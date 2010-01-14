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



/*
 * This is the client to send requests to the local node manager
 */

#include <unistd.h>
#include <trace.h>
#include <string.h>
#include <stdlib.h>
#include <libgen.h>
#include <stdio.h>
#include <netdb.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <ctype.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>

#include "server.h"

int get_host_address(char* host,
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

static char
client_request(hc_server_operation_t operation,
	       int port)
{
    int server_socket = -1;
    struct sockaddr_in	addr;
    char result;
    char operation_char;

    operation_char = operation;

    server_socket = socket(AF_INET, SOCK_STREAM, 0);
    if (server_socket == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
		 "socket failed [%d]",
		 errno );
        return(1);
    }

    if (get_host_address("localhost", port, &addr)) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "get_host_address failed");
	close(server_socket);
	return(1);
    }

    if (connect(server_socket, (struct sockaddr*)&addr,
		sizeof(struct sockaddr_in))) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "connect failed [%d]",
		 errno);
	close(server_socket);
	return(1);
    }

    write(server_socket, &operation_char, 1);
    read(server_socket, &result, 1);

    if (result) {
	cm_trace(CM_TRACE_LEVEL_DEBUG,
		 "The request failed [%d]",
		 result);
    } else {
	cm_trace(CM_TRACE_LEVEL_DEBUG,
		 "The request succeeded");
    }

    return(result);
}

static void
usage(char *progname)
{
    fprintf(stderr, "Usage : %s [-p port] -c command\n\n"
	    "Where :\n"
	    "\tport is the port number to contact the local node manager (default %d)\n"
	    "\tcommand is the command to execute (start_node | stop_node | exit | node_eligible | node_ineligible)\n",
	    progname,
	    DEFAULT_PORT);
}

int
main(int argc,
     char *argv[])
{
    char c;
    int port = DEFAULT_PORT;
    hc_server_operation_t operation = HC_OPERATION_UNKNOWN;
    char *request_string = NULL;
    char err;
    
#ifdef DEBUG
    cm_openlog("NodeMgr client", CM_TRACE_LEVEL_DEBUG);
#else
    cm_openlog("NodeMgr client", CM_TRACE_LEVEL_NOTICE);
#endif

    while ((c=getopt(argc, argv, "c:p:")) != -1) {
	switch (c) {
	case 'c':
	    if (!strcmp(argv[optind-1], "start_node")) {
		operation = HC_OPERATION_NODE_START;
		request_string = "start node";
	    }
	    if (!strcmp(argv[optind-1], "stop_node")) {
		operation = HC_OPERATION_NODE_STOP;
		request_string = "stop node";
	    }
	    if (!strcmp(argv[optind-1], "exit")) {
		operation = HC_OPERATION_EXIT;
		request_string = "stop node manager";
	    }
	    if (!strcmp(argv[optind-1], "node_eligible")) {
		operation = HC_OPERATION_NODE_ELIGIBLE;
		request_string = "node eligible";
	    }
	    if (!strcmp(argv[optind-1], "node_ineligible")) {
		operation = HC_OPERATION_NODE_INELIGIBLE;
		request_string = "node ineligible";
	    }
	    break;

	case 'p':
	    port = atoi(argv[optind-1]);
	    break;

	default:
	    usage(basename(argv[0]));
	    return(1);
	}
    }

    if (!request_string) {
	usage(basename(argv[0]));
	return(1);
    }

    cm_trace(CM_TRACE_LEVEL_DEBUG,
	     "Port is %d", port);

    cm_trace(CM_TRACE_LEVEL_NOTICE,
	     "Executing the %s request",
	     request_string);

    err = client_request(operation, port);
    if (err) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "The request failed [%d]",
		 err);
    } else {
	cm_trace(CM_TRACE_LEVEL_NOTICE,
		 "The request has been successfully executed");
    }

    cm_closelog();

    return(err);
}
