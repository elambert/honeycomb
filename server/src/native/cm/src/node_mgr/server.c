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



#include <strings.h>
#include <string.h>
#include <trace.h>
#include <netdb.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <malloc.h>
#include <ctype.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>
#include <unistd.h>
#include <sys/poll.h>
#include <stdlib.h>
#include <linux/reboot.h>
#include <sys/reboot.h>

#include "server.h"
#include "scheduler.h"
#include "sequence.h"
#include "action.h"
#include "factory.h"
#include "service_monitor.h"
#include "cmm_interface.h"

/*
 * Type definitions
 */

typedef struct {
    int client_fd;
    hc_server_operation_t operation;
    hc_sequence_t *sequence;
} server_callback_t;

/*
 * Private routines
 */

static int
get_host_address(char* host,
                 int port,
                 struct sockaddr_in* addr)
{
    bzero((char*)addr, sizeof (struct sockaddr_in));

    if (host == NULL) {
        addr->sin_addr.s_addr = INADDR_ANY;
        addr->sin_family = AF_INET;
    } else {
        struct hostent* hp;
        if ((hp = gethostbyname(host)) == NULL) {
            if (isdigit(host[0])) {
                addr->sin_addr.s_addr = inet_addr(host);
                addr->sin_family = AF_INET;
            } else {
                endhostent();
                return(1);
            }
        } else {
            addr->sin_family = hp->h_addrtype;
            (void) memcpy((char*) &addr->sin_addr,
                          hp->h_addr, hp->h_length);
        }
        endhostent();
    }
    addr->sin_port = htons(port);
    return(0);
}

static int
bind_server(int port,
            int nb_clients)
{
    struct sockaddr_in	addr;
    struct linger       linger;
    int                 server_socket;
    
    server_socket = socket(AF_INET, SOCK_STREAM, 0);
    if (server_socket == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "socket failed [%d]",
                 errno );
        return(-1);
    }
    
    linger.l_onoff = 1;
    linger.l_linger = 0;
    if (setsockopt(server_socket, SOL_SOCKET,
                   SO_LINGER, &linger, sizeof(linger)) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "setsockopt error [%d]", errno);
        close(server_socket);
        return(-1);
    }

    if (get_host_address(NULL, port, &addr)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "get_host_address failed");
        close(server_socket);
        return(-1);
    }
    
    if(bind(server_socket,
            (struct sockaddr*)&addr,
            sizeof(struct sockaddr_in))) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Cannot bind port [%d - %s]",
                 errno,
                 strerror(errno));
        close(server_socket);
        return(-1);
    }

    if (listen(server_socket, nb_clients)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Error in listen [%d]", errno);
        close(server_socket);
        return(-1);
    }

    return(server_socket);
}

static void
reply_to_client(hc_sequence_event_t event,
                void *data)
{
    server_callback_t *server_callback = (server_callback_t*)data;
    int result = 0;

    if (event != HC_SEQUENCE_EVENT_COMPLETED) {
        return;
    }

    switch (server_callback->operation) {
    case HC_OPERATION_NODE_START:
        if (server_callback->sequence->had_failures) {
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "\n****************************************\n*\n"
                     "* Some failures occured during the startup of the node.\n"
                     "* Node started\n"
                     "*\n****************************************");
        } else {
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "\n****************************************\n*\n"
                     "* The node has been started\n"
                     "*\n****************************************");
        }
        break;

    case HC_OPERATION_NODE_STOP:
        if (server_callback->sequence->had_failures) {
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "\n****************************************\n*\n"
                     "* Some failures occured during the shutdown of the node.\n"
                     "* Node stopped\n"
                     "*\n****************************************");
        } else {
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "\n****************************************\n*\n"
                     "* The node has been stopped\n"
                     "*\n****************************************");
        }
        break;

    default:
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Unexpected operation type [%d]",
                 server_callback->operation);
    }

    if ((server_callback->sequence->had_failures)
        && (server_callback->operation == HC_OPERATION_NODE_START)) {
        result = 1;
    }

    write(server_callback->client_fd, &result, 1);
    close(server_callback->client_fd);

    free(server_callback);
}

static void
shutdown_scheduler(hc_sequence_event_t event,
                   void *data)
{
    server_callback_t *server_callback = (server_callback_t*)data;

    if (event != HC_SEQUENCE_EVENT_COMPLETED) {
        return;
    }

    if (server_callback->sequence->had_failures) {
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "\n****************************************\n*\n"
                 "* Some failures occured during the shutdown of the node.\n"
                 "* Node stopped\n"
                 "* Node manager is now exiting\n"
                 "*\n****************************************");
    } else {
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "\n****************************************\n*\n"
                 "* The node has been stopped\n"
                 "* Node manager is now exiting\n"
                 "*\n****************************************");
    }
    free(server_callback);
    hc_scheduler_stop();
}

static void
reboot_node(hc_sequence_event_t event,
            void *data)
{
    if (event != HC_SEQUENCE_EVENT_COMPLETED) {
        return;
    }

    if (data != NULL) {
        shutdown_scheduler(event, data);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "\n****************************************\n*\n"
             "* The node has been stopped\n"
             "* REBOOTING !!! \n"
             "*\n****************************************");

    sync();

    if (system("/sbin/reboot") == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Failed to execute the reboot command. Performing the system call");
        reboot(LINUX_REBOOT_CMD_RESTART);
    }
    exit(0);
}

static void
deal_with_client(int socket)
{
    socklen_t           client_length;
    struct sockaddr_in  client_addr;
    int client;
    char operation_char;
    hc_sequence_t *sequence = NULL;
    char result = 1;
    server_callback_t *server_callback = NULL;
    hc_service_filter_t filter;

    server_callback = (server_callback_t*)malloc(sizeof(server_callback_t));
    if (!server_callback) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "malloc failed in deal_with_client");
        return;
    }
    
    client_length = sizeof(struct sockaddr_in);
    
    client = accept(socket,
                    (struct sockaddr*)&client_addr,
                    &client_length );

    read(client, &operation_char, 1);

    server_callback->client_fd = client;
    server_callback->operation = operation_char;

    filter = HC_FILTER_GROUP_USER | HC_FILTER_NODE_ANYNODE;
    if (is_master()) {
        filter |= HC_FILTER_NODE_MASTER;
    }
    
    switch (server_callback->operation) {
    case HC_OPERATION_NODE_START:
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Received a request to start the node");

        /*         hc_monitor_enable_detection(1); */
        hc_config_lock(NULL, HC_DOMAIN_ALL, filter, 0);
        hc_scheduler_change_groups(1, HC_FILTER_GROUP_USER);
	
        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 start_components_creator,
                                                 HC_SEQUENCE_EXECUTION_NORMAL,
                                                 NULL, NULL,
                                                 NULL);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the start sequence");
            free(server_callback);
            result = 1;
            write(client, &result, 1);
            close(client);
            return;
        }
        hc_scheduler_add_sequence(sequence);

        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 state_change_creator,
                                                 HC_SEQUENCE_EXECUTION_NORMAL,
                                                 reply_to_client, (void*)server_callback,
                                                 (void*)SRV_RUNNING);
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the start sequence");
            free(server_callback);
            result = 1;
            write(client, &result, 1);
            close(client);
            return;
        }
        hc_scheduler_add_sequence(sequence);

        server_callback->sequence = sequence;
        break;

    case HC_OPERATION_NODE_STOP:
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Received a request to stop the node");
	
        /*         hc_monitor_enable_detection(0); */

        hc_config_lock(NULL, HC_DOMAIN_ALL, filter, 1);
        hc_scheduler_change_groups(0, HC_FILTER_GROUP_USER);

        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 stop_components_creator,
                                                 HC_SEQUENCE_EXECUTION_BOTTOMUP,
                                                 reply_to_client, (void*)server_callback,
                                                 NULL);
        server_callback->sequence = sequence;
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the stop sequence");
            free(server_callback);
            result = 1;
            write(client, &result, 1);
            close(client);
            return;
        }
        hc_scheduler_add_sequence(sequence);
        break;
	
    case HC_OPERATION_EXIT:
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Received a request to exit");

        /*         hc_monitor_enable_detection(0); */

        filter |= HC_FILTER_GROUP_ALL;

        hc_config_lock(NULL, HC_DOMAIN_ALL, filter, 1);
        hc_scheduler_change_groups(0, HC_FILTER_GROUP_USER);
        hc_scheduler_change_groups(0, HC_FILTER_GROUP_SYSTEM);

        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 stop_components_creator,
                                                 HC_SEQUENCE_EXECUTION_BOTTOMUP,
                                                 shutdown_scheduler, server_callback,
                                                 NULL);
        server_callback->sequence = sequence;
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the exit sequence");
            free(server_callback);
            result = 1;
            write(client, &result, 1);
            close(client);
            return;
        }
        hc_scheduler_add_sequence(sequence);

        result = 0;
        write(client, &result, 1);
        close(client);
        break;

    case HC_OPERATION_NODE_REBOOT:
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Received a request to reboot");

        /*         hc_monitor_enable_detection(0); */

        filter |= HC_FILTER_GROUP_ALL;

        hc_config_lock(NULL, HC_DOMAIN_ALL, filter, 1);
        hc_scheduler_change_groups(0, HC_FILTER_GROUP_USER);
        hc_scheduler_change_groups(0, HC_FILTER_GROUP_SYSTEM);

        result = 0;
        write(client, &result, 1);
        close(client);

        sequence = create_sequence_from_services(hc_scheduler_get_services(),
                                                 HC_DOMAIN_ALL, filter,
                                                 stop_components_creator,
                                                 HC_SEQUENCE_EXECUTION_BOTTOMUP,
                                                 reboot_node, server_callback,
                                                 NULL);
        server_callback->sequence = sequence;
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't create the reboot sequence");
            free(server_callback);
            result = 1;
            write(client, &result, 1);
            close(client);
            reboot_node(HC_SEQUENCE_EVENT_COMPLETED, NULL);
            return;
        }
        hc_scheduler_add_sequence(sequence);

        break;

    case HC_OPERATION_NODE_ELIGIBLE:
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                "Received a request to become eligible");

        result = 0;
        if (cmm_node_eligible() != CMM_OK) {
            result = 1;
        }
        free(server_callback);
        write(client, &result, 1);
        close(client);
        break;

    case HC_OPERATION_NODE_INELIGIBLE:
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                "Received a request to become ineligible");

        result = 0;
        if (cmm_node_ineligible() != CMM_OK) {
            result = 1;
        }
        free(server_callback);
        write(client, &result, 1);
        close(client);
        break;

    default:
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Received an unknown request [%d]",
                 server_callback->operation);
        result = 1;
        write(client, &result, 1);
        close(client);
        free(server_callback);
        break;
    }
}

/*
 * API implementation
 */

int
hc_server_start(int port)
{
    int res;

    res = bind_server(port, 1);
    if (res == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "bind_server failed. Couldn't bind the server socket");
        return(-1);
    }
    
    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "The node manager daemon is waiting for requests on port %d",
             port);
    
    return(res);
}

void
hc_server_wait(int socket,
               int milliseconds)
{
    struct pollfd fds[2];
    int nb_fds;
    int res;
    
    fds[0].fd = socket;
    fds[0].events = POLLIN;
    fds[0].revents = 0;

    fds[1].fd = get_cmm_fd();
    fds[1].events = POLLIN;
    fds[1].revents = 0;

    if (fds[1].fd != -1) {
        nb_fds = 2;
    } else {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The CMM fd is not correct. Not polling the CMM");
        nb_fds = 1;
    }
    
    res = poll(fds, nb_fds, milliseconds);
    switch (res) {
    case 0 :
        /* Timed out */
        break;

    case -1 :
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "poll failed [%d - %s] in server",
                 errno, strerror(errno));
        break;

    default:
        if (fds[0].revents & POLLIN) {
            deal_with_client(socket);
        }
        if ((nb_fds == 2)
            && (fds[1].revents & POLLIN)) {
            (cmm_error_t)cmm_notify_dispatch();
        }
    }
}
