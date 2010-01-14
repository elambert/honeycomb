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



/* The main Cluster Membership Monitor */

#include <unistd.h>
#include <stdio.h>
#include <libgen.h>
#include <string.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>

#include "trace.h"
#include "cnt.h"
#include "fifo_mt.h"
#include "lobby.h"
#include "comm.h"
#include "cmm_parameters.h"
#include "stack.h"
#include "api.h"
#include "sender.h"

static void
usage( char *progname,
       char *msg )
{
    fprintf( stderr, "\n"
             "Usage : %s -n <node id> -f <config file> [-d]\n"
             "\t<node id> is the is of the node on which the CMM is executed\n"
             "\t<config file> is the file containing the cluster configuration\n"
             "\t-d enables DEBUG traces\n"
             "\n",
             basename(progname) );
    if ( msg ) {
        fprintf( stderr, "Error : %s\n\n",
                 msg );
    }
    exit(1);
}

static void
daemonize()
{
    pid_t pid;

    pid = fork();
    switch (pid) {
    case 0:
        /* Child */
        close(0);
        close(1);
        close(2);
        return;

    case -1:
        /* Error */
        fprintf(stdout, "CMM failed to daemonized\n");
        exit(1);

    default:
        /* Father */
        exit(0);
    }
}

/* 
 * The main method of the Cluster Membership Monitor 
 */
int
main( int argc,
      char *argv[] )
{
    int c;
    cmm_nodeid_t node_id = CMM_INVALID_NODE_ID;
    char *filename = NULL;
    cmm_error_t err;
    cm_trace_level_t trace_level = CM_TRACE_LEVEL_NOTICE;
    fifo_t *lobby_to_sender, *stack_to_sender;
    int pipe_stack[2];
    struct sigaction action;

    /* Read and verify the input arguments */

    while ( (c=getopt(argc, argv, "n:f:d")) != EOF ) {
        switch (c) {
        case 'n' :
            node_id = atoi( argv[optind-1] );
            break;
            
        case 'f' :
            filename = strdup(optarg);
            if (!filename) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "strdup failed" );
                exit(1);
            }
            break;
            
        case 'd' :
            trace_level = CM_TRACE_LEVEL_DEBUG;
            break;
        }
    }

    if ( node_id == CMM_INVALID_NODE_ID ) {
        usage( argv[0],
               "The node id paramater has to be specified" );
    }

    if ( filename == NULL ) {
        usage( argv[0],
               "The configuration file parameter has to be specified" );
    }

    daemonize();
    
    cm_openlog("CMM", trace_level);

    /* Block the SIGPIPE signals */
    
    action.sa_handler = SIG_IGN;
    if (sigaction(SIGPIPE, &action, NULL)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "sigaction failed");
        cm_closelog();
        return(1);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "The local node id is %d",
             node_id );

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "The configuration file is %s",
             filename );


    err = cnt_init( filename, node_id );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "cnt_init failed [%d]",
                 err );
        return(1);
    }

    cnt_print();

    if ( pipe(pipe_stack) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pipe failed" );
        return(1);
    }
    
    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "stack pipe has been created (%d %d)",
             pipe_stack[0], pipe_stack[1] );
    
    stack_to_sender = fifo_init(NULL);
    if (!stack_to_sender) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "fifo_init failed" );
        return(1);
    }

    lobby_to_sender = fifo_init(stack_to_sender);
    if (!lobby_to_sender) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "fifo_init failed" );
        return(1);
    }

    /* start the lobby server thread to listen to incoming messages from 
     * the predecessor node 
     */

    err = lobby_start( RING_PORT,
                       pipe_stack[1],
                       lobby_to_sender );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "lobby_start failed [%d]",
                 err );
        return(1);
    }

    /* start the sender server thread to establish connection with the 
     * successor node and send messages to it from the lobby_to_sender and 
     * the stack_to_sender fifos
     */
    err = sender_start( lobby_to_sender,
                        stack_to_sender );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "sender_start failed [%d]",
                 err );
        return(1);
    }

    /* Start the api server */

    err = api_init();
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "api_init failed [%d]",
                 err );
        return(1);
    }
/*     err = heartbeat_start(); */
/*     if (err != CMM_OK) { */
/*         cm_trace(CM_TRACE_LEVEL_ERROR,  */
/*                  "heartbeat init failed [%d]", err); */
/*         return (1); */
/*     } */
    stack_start( pipe_stack[0], stack_to_sender );

    cm_trace(CM_TRACE_LEVEL_ERROR, 
             "The stack exited !");
    
    cm_closelog();

    return(0);
}
