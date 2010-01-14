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
 * The sender server establishes a connection with its successor node on the 
 * ring and communicates with it. The messages that the sender sends are stored
 * in two fifo structures, one containing frames sent from other nodes, and 
 * the second containing frames sent from this node. 
 */
#include <unistd.h>
#include <fcntl.h>
#include <netdb.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <tcp_abort.h>
#include <stdio.h>
#include <sys/ioctl.h>

#include "sender.h"
#include "trace.h"
#include "cmm_parameters.h"
#include "frame.h"
#include "cnt.h"
#include "utils.h"
#include "comm.h"

static fifo_t           *from_lobby;
static fifo_t           *from_stack;

static pthread_t        sender_thread =0;
static char             keep_running;

static int              next_node;
static struct timespec  next_timeout;
static int              connection_fd;
static struct timespec  last_heartbeat;

/* 
 * disconnect next node performs the following functions:
 * close the fd for the next node.
 * change the sender status to DISCONNECTED 
 * broadcast a FRAME_NODE_CHANGE message 
 */

static void
disconnect_next_node()
{
    if (next_node == -1) {
        return;
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "[sender] Disconnecting from node %d",
             cnt_get_entry(next_node)->member.nodeid);

    last_heartbeat.tv_sec = 0;
    last_heartbeat.tv_nsec = 0;

    close(connection_fd);
    connection_fd = -1;
    next_node = -1;
}

static void
forward_frames( fifo_t *input )
{
    frame_t *frame;
    cmm_error_t err;

    do {
        /*        frame = (frame_t*)fifo_extract_elem( input ); */

        frame = (frame_t*)fifo_get_first( input );

        if (frame) {
            err = frame_send( connection_fd,
                              frame );
            if (err != CMM_OK) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[sender] frame_send failed ! Couldn't forward a frame to the next node" );
                disconnect_next_node();
                return; /* the frame is left in the fifo */
            }
        }
        frame = (frame_t*)fifo_extract_elem( input ); /* remove the frame from the fifo */
    } while (frame);
}

static void
processReceivedFrame( frame_t *frame )
{
    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "[sender] Received a frame of type %s from node id %d",
             frame_type_strings[frame->type],
             frame->sender);

    switch (frame->type) {
    case FRAME_DISCONNECT :
        /* Disconnect the next node */
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "[sender] Received a DISCONNECT frame");
        disconnect_next_node();
        break;

    case FRAME_HEARTBEAT: {
        /* Received an heartbeat. Update the heartbeat value */
        clock_gettime(CLOCK_REALTIME,
                      &last_heartbeat);
/*         cm_trace(CM_TRACE_LEVEL_DEBUG, */
/*                  "[sender] Received an heartbeat from node %d", */
/*                  frame->sender); */
    } 
        break;
        

    default:
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "[sender] Unexpected frame type in processReceivedFrame [%d]",
                 frame->type);
    }

    frame_free( &frame );
}

static void
receiveNextNodeInfo()
{
    frame_t *frame;
    cmm_error_t err;

    while ( (connection_fd != -1)
            && (fdContainsInfo(connection_fd) == 1) ) {
        err = frame_receive( connection_fd,
                             &frame );
        switch (err) {
        case CMM_OK :
            /* Eveything is fine */
            break;

        case CMM_ECONN :
            /* Connection is broken */
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "[sender] Bad connection. Disconnecting from node %d",
                     cnt_get_entry(next_node)->member.nodeid );
            disconnect_next_node();
            break;

        default:
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[sender] frame_receive failed [%d]",
                     err );
        }

        if (err == CMM_OK) {
            processReceivedFrame( frame );
        }
    }
}

/*
 * The following method is called when the connections to the ring have
 * changed. It then has to check that :
 * - it is the first connection : a node join has to be sent
 * - the list of next nodes that left and send notifications
 */

static void
send_nodechange_notifications()
{
    frame_t *frame = NULL;
    int i, next_real;

    if (connection_fd == -1) {
        /* I am not connected - What can I do ? */
        return;
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "[sender] ***** NODE %d has joined the cluster *****",
             cnt_get_entry(0)->member.nodeid );

    /* broadcast a FRAME_NODE_CHANGE message */
    frame = frame_allocate( FRAME_NODE_CHANGE, cnt_get_entry(0)->member.nodeid & 0xFF );
    if ( !frame ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[sender] frame_allocate failed" );
    } else {
        ((frame_arg_node_change_t*)frame->arg)->left = 0;
        ((frame_arg_node_change_t*)frame->arg)->nodeid = cnt_get_entry(0)->member.nodeid & 0xFF;
        fifo_add_elem( from_lobby, frame );
    }

    /* Check that the following missing nodes are already marked as OUT OF CLUSTER */

    next_real = next_node;
    if (next_real == 0) {
        next_real = cnt_get_nb_nodes();
    }

    for (i=1; i<next_real; i++) {
        if (!(cnt_get_entry(i)->member.sflag & CMM_OUT_OF_CLUSTER)) {
            /* The node is still marked to be part of the cluster - Send a frame */

            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "[sender] ***** NODE %d has left the cluster *****",
                     cnt_get_entry(i)->member.nodeid );

            frame = frame_allocate( FRAME_NODE_CHANGE, cnt_get_entry(0)->member.nodeid & 0xFF );
            if ( !frame ) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[sender] frame_allocate failed" );
            } else {
                ((frame_arg_node_change_t*)frame->arg)->left = 1;
                ((frame_arg_node_change_t*)frame->arg)->nodeid = cnt_get_entry(i)->member.nodeid & 0xFF;
                fifo_add_elem( from_lobby, frame );
            }
        }
    }
}

static void 
connect_to_ring()
{
    frame_t *frame = NULL;
    frame_t *request_frame = NULL;
    cmm_error_t err;
    frame_type_t type;

    /* Allocations */
    request_frame = frame_allocate( FRAME_REGISTER, cnt_get_entry(0)->member.nodeid & 0xFF );
    if (!request_frame) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[sender] frame_allocate failed" );
        return;
    }
    ((frame_arg_register_t*)request_frame->arg)->answer = 0;
    
    /* Loop until a node is found */

    next_node = 1;

    do {
        if ( next_node == cnt_get_nb_nodes() ) {
            cm_trace(CM_TRACE_LEVEL_DEBUG,
                     "[sender] Failed to connect to any node in the ring. Connect to myself" );
            next_node =  0;
        }

        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "[sender] Trying to connect to node %d",
                 cnt_get_entry(next_node)->member.nodeid );
        
        connection_fd = ConnectToServer( &cnt_get_entry(next_node)->net_addr );
        if ( connection_fd == -1 ) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "[sender] Failed to connect to node %d",
                     cnt_get_entry(next_node)->member.nodeid );
        }
        
        if (connection_fd != -1) {
            /* Play the connection protocol */
            
            err = frame_send(connection_fd, request_frame);
            if (err != CMM_OK) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[sender] frame_send failed (was sending the FRAME_REGISTER frame)" );
                close(connection_fd);
                connection_fd = -1;
            }
        }

        if (connection_fd == -1) {
            /* Failed to connect in this round */
            next_node++;
        }

    } while ((connection_fd == -1) && (next_node != 0));

    /* I can free the request frame */

    frame_free(&request_frame);
    request_frame = NULL;

    if (connection_fd == -1) {
        /* Couldn't find any node */
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "[sender] Couldn't connect to any node in the ring, no even myself ...");
        next_node = -1;
        return;
    }

    do {
        err = frame_receive(connection_fd, &frame);
        if (err != CMM_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[sender] frame receive failed [%d]",
                     err );
            close(connection_fd);
            connection_fd = -1;
            next_node = -1;
            return;
        }

        type = frame->type;

        if ( type != FRAME_REGISTER ) {
            processReceivedFrame( frame );
        } else {
            switch ( ((frame_arg_register_t*)frame->arg)->answer ) {
            case 0 :
                /* Still uninitialized ??? */
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[sender] Received unexpected answer in FRAME_REGISTER (0)" );
                /* No break */

            case 2 :
                cm_trace(CM_TRACE_LEVEL_NOTICE,
                         "[sender] Registration to node %d has been refused",
                         cnt_get_entry(next_node)->member.nodeid );
                next_node = -1;
                close(connection_fd);
                connection_fd = -1;
                break;

            case 1 :
                cm_trace(CM_TRACE_LEVEL_DEBUG,
                         "[sender] Connection with node %d has been accepted",
                         cnt_get_entry(next_node)->member.nodeid);
                break;
            }
            frame_free(&frame);
        }
    } while (type != FRAME_REGISTER);   

    if (next_node == -1) {
        return;
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "[sender] I have a new next node (%d)",
             cnt_get_entry(next_node)->member.nodeid );

    send_nodechange_notifications();
}

static void *
sender_start_routine( void *arg )
{
    frame_t *frame = NULL;

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "[sender] The sender thread has been started" );

    while ( keep_running ) {
        update_timer( &next_timeout, HEARTBEAT_INTERVAL );
        
        /* Check if there is something in the socket from the next node */
        if (connection_fd != -1) {
            switch ( fdContainsInfo(connection_fd) ) {
            case -1 :
                /* fdContainsInfo error */
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[sender] fdContainsInfo failed" );
                break;

            case 0 :
                /* No info available */
                break;

            case 1 :
                /* There is some info available */
                receiveNextNodeInfo();
                break;

            case 2 :
                /* The connection is broken */
                cm_trace(CM_TRACE_LEVEL_NOTICE,
                         "[sender] The connection with node %d has been broken. Disconnecting ...",
                         cnt_get_entry(next_node)->member.nodeid );
                disconnect_next_node();
                break;
            }
        }

        /* Deal with the connection */
        if (connection_fd == -1) {
            connect_to_ring();
            /* enable the reception of SIGURG */
            fcntl(connection_fd, F_SETOWN, getpid());
	    
        }

        /*
         * Check that the heartbeat from the next node has been received
         */

        if (connection_fd != -1) {
            struct timespec now, elapsed_time;
            int err = 0;
            
            if (clock_gettime(CLOCK_REALTIME, &now)) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "[sender] clock_gettime failed. Cannot check heartbeat");
                err = 1;
            }

            if ((last_heartbeat.tv_sec == 0)
                && (last_heartbeat.tv_nsec == 0)) {
                /* Heartbeats not initialized yet */
                err = 1;
            }

            if (!err) {
                elapsed_time = substract_timespec(now, last_heartbeat);
                
                if (((elapsed_time.tv_sec*1000)+(elapsed_time.tv_nsec/1000000)) > HEARTBEAT_TIMEOUT) {
                    cm_trace(CM_TRACE_LEVEL_ERROR,
                             "[sender] ***** Failed to receive an heartbeat from the next node *****");
                    
                    // Send the node leave frame notification
                    frame = frame_allocate(FRAME_NODE_CHANGE, cnt_get_entry(0)->member.nodeid & 0xFF );
                    if ( !frame ) {
                        cm_trace(CM_TRACE_LEVEL_ERROR, 
                                 "[sender] frame_allocate failed" );
                    } else {
                        ((frame_arg_node_change_t*)frame->arg)->left = 1;
                        ((frame_arg_node_change_t*)frame->arg)->nodeid = cnt_get_entry(next_node)->member.nodeid & 0xFF;
                        fifo_add_elem(from_lobby, frame);
                    }

                    /* Disconnect the node  */
                    disconnect_next_node();
                    
                }
            }
        }

        if (connection_fd != -1) {
            /* Check if there are frames to forward from the lobby 
               (ring propagation) */
            if ( !fifo_is_empty( from_lobby ) ) {
                forward_frames( from_lobby );
            }
        }

        if (connection_fd != -1) {
            /* Check if there are frames from the stack */
            if ( !fifo_is_empty( from_stack ) ) {
                forward_frames( from_stack );
            }
        }

        /* Go to sleep for the remaining time, or until elements are added 
           to the coupled fifos  */
        fifo_timed_block( from_lobby,  
                          remaining_time(next_timeout) );
    }

    return(NULL);
}

/* 
 * Start the sender server thread. Establish a connection with successor,
 * send the frames in the lobby and stack fifos 
 */
 
cmm_error_t
sender_start( fifo_t *_from_lobby,
              fifo_t *_from_stack )
{
    int err;
    from_lobby = _from_lobby;
    from_stack = _from_stack;

    keep_running = 1;
    next_node = -1;

    next_timeout.tv_sec = 0;
    next_timeout.tv_nsec = 0;

    last_heartbeat.tv_sec = 0;
    last_heartbeat.tv_nsec = 0;
    
    connection_fd = -1;

    err = pthread_create( &sender_thread,
                          NULL,
                          sender_start_routine,
                          NULL );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[sender] pthread_create failed [%d]",
                 err );
        return(CMM_EOTHER);
    }

    return(CMM_OK);
}

void
sender_stop()
{
    int err;

    keep_running = 0;
    
    do {
        err = pthread_join( sender_thread, NULL );
        if (err) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[sender] pthread_join failed" );
        }
    } while (err);
}
