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
#include <mbox.h>
#include <malloc.h>
#include <sys/time.h>

#include "service_monitor.h"
#include "tree.h"
#include "config.h"
#include "sequence.h"
#include "scheduler.h"
#include "errors.h"

/*
 * Global datas
 */

static int _monitor_detection_enabled = 0;

/*
 * Private routines
 */

/* For now we only check the state transitions */

static void
check_service(hc_tree_opaque_t *service_node,
              hc_list_opaque_t *errors)
{
    hc_service_t *service = NULL;
    mb_state_t state, expected_state;
    hc_tree_opaque_t *node = NULL;
    int error = 0;
    hc_error_t *error_report = NULL;
    struct timeval now, last_heartbeat;

    service = (hc_service_t*)hc_tree_get_datas(service_node);

    if (!error) {
        if (service->mailbox_id == MB_INVALID_ID) {
            error = 1;
        }
    }

    /* Get the current state of the service */

    if (!error) {
        if (mb_getstate(service->mailbox_id, &state) != MB_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "Couldn't get the state of service %s",
                     service->start_cmd);
            error = 1;
        }
    }

    /* Check the heartbeat value and put the service DISABLED if needed */

    if (!error) {
        if ((state != SRV_READY)
            && (state != SRV_RUNNING)) {
            /* The service is not in a state to check its heartbeat */
            error = 1;
        }

        if (!error) {
            if (mb_gettimestamp(service->mailbox_id,
                                &last_heartbeat) != MB_OK) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "mb_gettimestamp failed. Cannot check the last heartbeat for %s",
                         service->start_cmd);
                error = 1;
            }
        }

        if (!error) {
            if (gettimeofday(&now, NULL)) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "gettimeofday failed. Cannot check the last heartbeat for %s",
                         service->start_cmd);
                error = 1;
            }
        }

        if (!error) {
            if (now.tv_sec - last_heartbeat.tv_sec > service->heartbeat) {
                cm_trace(CM_TRACE_LEVEL_NOTICE,
                         "***** The service %s missed its heartbeat !!! Moving to the disable state *****",
                         service->start_cmd);
                hc_sequence_disable_service(service);
            }
        }
        
        error = 0;
    }

    /* Get the expected state */
    
    if (!error) {
        if (mb_getexpectedstate(service->mailbox_id, &expected_state) != MB_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Failed to retrieve the states of %s",
                     service->start_cmd);
            error = 1;
        }
    }
    
    /* Check that the component is not DISABLED */

    if ((!error)
        && (service->locked == 0)) {
        if (state == SRV_DISABLED) {
            /* 	    cm_trace(CM_TRACE_LEVEL_NOTICE, */
            /* 		     "The service %s is in the DISABLED state", */
            /* 		     service->start_cmd); */

            error_report = (hc_error_t*)malloc(sizeof(hc_error_t));
            if (!error_report) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "Cannot allocate memory to report the error");
                error = 1;
            }

            if (!error) {
                error_report->type = HC_ERROR_DISABLED_SERVICE;
                error_report->service_node = service_node;

                if (hc_list_add_element(errors,
                                        error_report)) {
                    cm_trace(CM_TRACE_LEVEL_ERROR,
                             "Failed to add the error in the error queue");
                    hc_error_free(error_report);
                    error_report = NULL;
                    error = 1;
                }
            }
	
            error = 1;
        }
    }

    /* If the state change occured, release the waiting action */

    if (!error) {
        if (expected_state != SRV_INVALID) {
            if ((state == expected_state)
                && (service->waiting_action_node)) {
                /* The state transition occured */
                hc_sequence_stop_waiting(NULL,
                                         service->waiting_action_node,
                                         1);
                service->waiting_action_node = NULL;
            }
        }
    }

    /* Scan the children and brothers */

    node = hc_tree_get_first_child(service_node);
    if (node) {
        check_service(node, errors);
    }

    node = hc_tree_get_next_brother(service_node);
    if (node) {
        check_service(node, errors);
    }
}

/*
 * API implementation
 */

void
hc_monitor_services(hc_list_opaque_t *errors)
{
    hc_tree_opaque_t *services = NULL;

    services = hc_scheduler_get_services();
    check_service(services, errors);
}

void
hc_monitor_enable_detection(int value)
{
    _monitor_detection_enabled = value;
}
