/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



#include <trace.h>
#include <unistd.h>
#include <stdlib.h>

#include "scheduler.h"
#include "list.h"
#include "factory.h"
#include "server.h"
#include "forker.h"
#include "errors.h"
#include "service_monitor.h"
#include "mailbox.h"
#include "cmm_interface.h"

/*
 * Private types and globals variables
 */

typedef struct {
    int running;
    hc_tree_opaque_t *services;
    hc_service_filter_t allowed_groups;
    hc_list_opaque_t *sequences;
    hc_list_opaque_t *errors;
    int server_socket;
} global_datas_t;

static global_datas_t _global_datas;

/*
 * Private routines
 */

static void
decrease_services_walker(hc_tree_opaque_t *service_node,
                         void *cookie,
                         void *anchor,
                         void **brother_anchor,
                         void **child_anchor)
{
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(service_node);
    hc_sequence_t *sequence = (hc_sequence_t*)cookie;
    
    if (service_matches_mask(service->filter, sequence->filter)) {
        service->nb_ongoing_operations--;
/*         cm_trace(CM_TRACE_LEVEL_DEBUG, */
/*                  "Decreasing nb_ongoing_operations for %s [%d]", */
/*                  service->start_cmd, */
/*                  service->nb_ongoing_operations); */
    }
}

static int
schedule_sequences()
{
    int result = 0;
    hc_sequence_t *sequence = NULL;

    sequence = hc_list_get_first_element(_global_datas.sequences);
    if (!sequence) {
        return(0);
    }

    result += hc_sequence_run(sequence);
    if (sequence->status == SEQUENCE_STATUS_COMPLETED) {
        (hc_sequence_t*)hc_list_extract_first_element(_global_datas.sequences);
	
        /* Decrease the counter of the actions */
        hc_config_walker(sequence->service_node,
                         sequence->domain,
                         decrease_services_walker,
                         sequence);
	
        hc_sequence_simple_free(sequence);
    }

    return(result);
}

static void
restart_service(hc_tree_opaque_t *service_node)
{
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(service_node);
    hc_sequence_t *sequence = NULL;
    hc_service_filter_t filter;
    time_t now;

    service->restart_current++;
    if (service->restart_current == service->restart_nb) {
        service->restart_current = 0;
    }

    now = time(NULL);

    if ((service->restart_times[service->restart_current] == 0)
        || (now-service->restart_times[service->restart_current] > service->restart_window)) {
        /* We can restart */
        service->restart_times[service->restart_current] = now;

        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Service %s is being restarted",
                 service->start_cmd);

        filter = _global_datas.allowed_groups | HC_FILTER_NODE_ANYNODE;
        if (is_master()) {
            filter |= HC_FILTER_NODE_MASTER;
        }

        sequence = create_sequence_from_services(service_node,
                                                 HC_DOMAIN_PROCESS, filter,
                                                 stop_components_creator,
                                                 HC_SEQUENCE_EXECUTION_BOTTOMUP,
                                                 NULL, NULL, NULL);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the restart sequence");
            return;
        }
        hc_scheduler_add_sequence(sequence);

        sequence = create_sequence_from_services(service_node,
                                                 HC_DOMAIN_PROCESS, filter,
                                                 start_components_creator,
                                                 HC_SEQUENCE_EXECUTION_NORMAL,
                                                 NULL, NULL, NULL);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the restart sequence");
            return;
        }
        hc_scheduler_add_sequence(sequence);

        sequence = create_sequence_from_services(service_node,
                                                 HC_DOMAIN_PROCESS, filter,
                                                 state_change_creator,
                                                 HC_SEQUENCE_EXECUTION_NORMAL,
                                                 NULL, NULL,
                                                 (void*)SRV_RUNNING);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the restart sequence");
            return;
        }
        hc_scheduler_add_sequence(sequence);

        return;
    }

    /* We exhausted the restarts, simply stop the service */

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "***** The service %s cannot be restarted anymore (too many restarts). *****",
             service->start_cmd);

    filter = HC_FILTER_GROUP_ALL | HC_FILTER_NODE_ANYNODE;
    if (is_master()) {
        filter |= HC_FILTER_NODE_MASTER;
    }
    hc_config_lock(service_node, HC_DOMAIN_CHILDREN, filter, 1);

    sequence = create_sequence_from_services(service_node,
                                             HC_DOMAIN_CHILDREN, filter,
                                             stop_components_creator,
                                             HC_SEQUENCE_EXECUTION_BOTTOMUP,
                                             NULL, NULL, NULL);
    if (!sequence) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Couldn't create the stop sequence");
        return;
    }
    hc_scheduler_add_sequence(sequence);
}

/*
 * API implementation
 */

void
hc_scheduler_start(hc_tree_opaque_t *services,
                   int port,
                   hc_sequence_callback_t start_cb,
                   void *cookie)
{
    int operation_performed;
    hc_error_t *error = NULL;
    hc_service_t *service = NULL;
    hc_sequence_t *sequence = NULL;
    hc_service_filter_t filter;
    
    _global_datas.running = 1;
    _global_datas.services = services;
    _global_datas.server_socket = -1;

    _global_datas.sequences = hc_list_allocate(NULL);
    if (!_global_datas.sequences) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_list_allocate failed. Couldn't create the list of sequences");
        return;
    }

    _global_datas.errors = hc_list_allocate(NULL);
    if (!_global_datas.errors) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_list_allocate failed. Couldn't create a queue for errors");
        hc_list_free(_global_datas.sequences, NULL);
        return;
    }

    /* Create the first sequence (starting the SYSTEM services) */

/*     hc_monitor_enable_detection(1); */
    filter = HC_FILTER_GROUP_SYSTEM | HC_FILTER_NODE_ANYNODE;
    if (is_master()) {
        filter |= HC_FILTER_NODE_MASTER;
    }

    hc_config_lock(NULL, HC_DOMAIN_ALL, filter, 0);
    _global_datas.allowed_groups = HC_FILTER_GROUP_SYSTEM;
	
    sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                             HC_DOMAIN_ALL, filter,
                                             start_components_creator,
                                             HC_SEQUENCE_EXECUTION_NORMAL,
                                             start_cb, cookie,
                                             NULL);
    if (!sequence) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Couldn't create the sequence to start the SYSTEM components");
    }

    if (sequence) {
        hc_scheduler_add_sequence(sequence);

        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 state_change_creator,
                                                 HC_SEQUENCE_EXECUTION_NORMAL,
                                                 NULL, NULL,
                                                 (void*)SRV_RUNNING);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the sequence to put the SYSTEM components RUNNING");
        } else {
            hc_scheduler_add_sequence(sequence);
        }
    }

    /* Starting the request server */

    _global_datas.server_socket = hc_server_start(port);
    if (_global_datas.server_socket == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Failed to start the server");
        hc_list_free(_global_datas.sequences, hc_sequence_simple_free);
        hc_list_free(_global_datas.errors, NULL);
        return;
    }
    
    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "The scheduler has been started");

    while (_global_datas.running) {
        operation_performed = 0;
	
        operation_performed += schedule_sequences();
        if (operation_performed) {
            cm_trace(CM_TRACE_LEVEL_DEBUG,
                     "Some action have been executed");
        }

        hc_forker_check(_global_datas.errors);
        hc_monitor_services(_global_datas.errors);

        error = (hc_error_t*)hc_list_extract_first_element(_global_datas.errors);
        while (error) {
            service = (hc_service_t*)hc_tree_get_datas(error->service_node);

            switch (error->type) {
            case HC_ERROR_DISABLED_SERVICE:
                cm_trace(CM_TRACE_LEVEL_DEBUG,
                         "A HC_ERROR_DISABLED_SERVICE has been received for service %s",
                         service->start_cmd);
                
                if (service->nb_ongoing_operations == 0) {
                    /* We increase operation_performed not to block at the
                     * scheduler loop and to execute the RESTART sequence
                     * as fast as possible */
                    operation_performed++;

                    restart_service(error->service_node);
                } else {
                    cm_trace(CM_TRACE_LEVEL_DEBUG,
                             "Does not restart %s, since there is already an operation affecting it",
                             service->start_cmd);
                }

                (int)update_mailbox();
                break;
                
            default:
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "Got an unexpected error report %d",
                         error->type);
            }
	    
            hc_error_free(error);
            error = (hc_error_t*)hc_list_extract_first_element(_global_datas.errors);
        }

        if (operation_performed) {
            cm_trace(CM_TRACE_LEVEL_DEBUG,
                     "Polling server without waiting");
            (int)update_mailbox();
            hc_server_wait(_global_datas.server_socket, 0);
        } else {
            hc_server_wait(_global_datas.server_socket, 500);
        }
    }

    close(_global_datas.server_socket);
    _global_datas.server_socket = -1;

    hc_list_free(_global_datas.sequences, hc_sequence_simple_free);
    _global_datas.sequences = NULL;
    
    hc_list_free(_global_datas.errors, NULL);
    _global_datas.errors = NULL;

    cmm_dispose();

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "The scheduler is exiting");
}

void
hc_scheduler_add_sequence(hc_sequence_t *sequence)
{
    hc_list_add_element(_global_datas.sequences,
                        sequence);
}

hc_tree_opaque_t *
hc_scheduler_get_services()
{
    return(_global_datas.services);
}

void
hc_scheduler_stop()
{
    _global_datas.running = 0;
}

/*
 * This error routines are implemented here
 */

void
hc_error_free(hc_error_t *error)
{
    free(error);
}

hc_sequence_t *
hc_scheduler_get_running_sequence()
{
    return((hc_sequence_t*)hc_list_get_first_element(_global_datas.sequences));
}

void
hc_scheduler_change_groups(int addition,
                           hc_service_filter_t filter)
{
    if (addition) {
        _global_datas.allowed_groups |= (filter & HC_FILTER_GROUP_ALL);
    } else {
        _global_datas.allowed_groups &= ~(filter & HC_FILTER_GROUP_ALL);
    }
}

void
hc_scheduler_change_master_services(int start)
{
    hc_service_filter_t filter;
    hc_sequence_t *sequence;

    filter = _global_datas.allowed_groups | HC_FILTER_NODE_MASTER;
    
    if (start) {
        /* Starting the MASTER services */

        hc_config_lock(NULL, HC_DOMAIN_ALL, filter, 0);
	
        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 start_components_creator,
                                                 HC_SEQUENCE_EXECUTION_NORMAL,
                                                 NULL, NULL,
                                                 NULL);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the start sequence for MASTER services");
            return;
        }
        hc_scheduler_add_sequence(sequence);

        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 state_change_creator,
                                                 HC_SEQUENCE_EXECUTION_NORMAL,
                                                 NULL, NULL,
                                                 (void*)SRV_RUNNING);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the start sequence for MASTER services");
            return;
        }
        hc_scheduler_add_sequence(sequence);
    } else {
        /* Stopping the MASTER services */

        hc_config_lock(NULL, HC_DOMAIN_ALL, filter, 1);

        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 stop_components_creator,
                                                 HC_SEQUENCE_EXECUTION_BOTTOMUP,
                                                 NULL, NULL,
                                                 NULL);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the stop sequence for MASTER services");
            return;
        }
        hc_scheduler_add_sequence(sequence);
    }
}
