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



/* The lobby server listens to messages from the predecessor node, and routes 
 * the messages to the right recipient. Only the predecessor node can connect 
 * to the lobby server.
 */

#include <pthread.h>
#include <poll.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <fcntl.h>
#include <signal.h> /* alarm() */
#include <unistd.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>

#include "cmm_parameters.h"
#include "lobby.h"
#include "trace.h"
#include "comm.h"
#include "frame.h"
#include "cnt.h"
#include "utils.h"

static int stack_fd = -1;
static fifo_t *output_to_sender = NULL;

static pthread_t lobby_thread = 0;
static int keep_running;

static int connection_fd;
static int previous_node;

static void
processReceivedFrame( frame_t *frame )
{
    char send_to_stack, send_to_sender;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "[lobby] Received a frame of type %s",
             frame_type_strings[frame->type] );

    if ( (frame->type == FRAME_REGISTER) ) {
        frame_free( &frame );
        return;
    }

    /* Check where the frame has to be sent */
    send_to_sender = 1;
    send_to_stack = 1;

    if ( (cnt_distance(frame->sender) == 0)
         || (frame->dest == DEST_NEXT_ONLY) ) {
        send_to_sender = 0;
    }

    if ( (frame->dest != DEST_BROADCAST)
         && ( cnt_distance(frame->dest) != 0 )
         && ( frame->dest != DEST_NEXT_ONLY ) ) {
        send_to_stack = 0;
    }

    if (send_to_stack) {
        if ( frame_send( stack_fd, frame ) != CMM_OK ) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[lobby] frame_send failed" );
        }
    }

    if (send_to_sender) {
        if ( fifo_add_elem( output_to_sender, frame ) != CMM_OK ) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[lobby] fifo_add_elem failed (-> sender)" );
            frame_free( &frame );
        }
    } else {
        frame_free( &frame );
    }
}

/*
 * Accept a node registration request if I do not have any predecessor connected
 * that is closer than the new node, otherwise reject the registration.
 * If I am connected to the wrong node (not my predecessor), I will disconnect
 * the old connection and accept the new
 */

static void
acceptClient(int serverSocket )
{
    socklen_t           clientLength;
    struct sockaddr_in  clientAddr;
    frame_t             *frame, *disconnect;
    cmm_error_t         err;
    int                 client_fd;

    clientLength = sizeof(struct sockaddr_in);
    
    client_fd = accept( serverSocket,
                        (struct sockaddr*) &clientAddr,
                        &clientLength );

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "[lobby] A client connected (fd %d)",
             client_fd );

    /* Run the register algorithm */
    
    err = frame_receive( client_fd, &frame );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[lobby] frame_receive failed [%d]",
                 err );
        return;
    }
    
    if (frame->type != FRAME_REGISTER) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[lobby] Received a frame of type %s - Failure in the registration protocol",
                 frame_type_strings[frame->type] );
        frame_free( &frame );
        close(client_fd);
        return;
    }
    
    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "[lobby] Received a registration request from node %d",
             frame->sender );
    
    /*
     * TODO: I think the following check should be removed. It will cause a problem.
     * We should assume that the node requesting the connection tried my previous node
     * and failed. It may have a more updated view of my previous node if it was its
     * predecessor, because it receives its heartbeat. 
     * Another option is to implement a two way heartbeat.
     */

    /*    
          if ( (previous_node != -1)
          && ( previous_node > cnt_distance(frame->sender) ) ) {
          print_log( DEBUG_TRACE_LEVEL,
          "Rejecting registration from node %d since I am connected with %d",
          frame->sender,
          cnt_get_entry(previous_node)->member.nodeid );

          ((frame_arg_register_t*)frame->arg)->answer = 2;
          err = frame_send( client_fd, frame );
          if (err != CMM_OK) {
          print_error( "frame_send failed" );
          close(client_fd);
          }
          frame_free( &frame );
          return;
          }
    */
    /* I will accept the registration */    

    if ( previous_node != -1 ) {
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "[lobby] Closing connection with node %d",
                 cnt_get_entry(previous_node)->member.nodeid );

        disconnect = frame_allocate( FRAME_DISCONNECT, cnt_get_entry(0)->member.nodeid & 0xFF );
        if (!disconnect) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[lobby] frame_allocate failed" );
        } else {
            err = frame_send( connection_fd, disconnect );
            if (err != CMM_OK) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[lobby] frame_send failed to disconnect" );
            }
            frame_free( &disconnect );
            cm_trace(CM_TRACE_LEVEL_DEBUG,
                     "[lobby] Sent a close request to node %d", 
                     cnt_get_entry(previous_node)->member.nodeid );
            while ( frame_receive( connection_fd, &disconnect ) != CMM_ECONN ) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[lobby] DISCONNECT protocol is not complete yet [received %s]. Forwarding the frame",
                         frame_type_strings[disconnect->type] );
                processReceivedFrame(disconnect);
            }
            cm_trace(CM_TRACE_LEVEL_DEBUG, 
                     "[lobby] Node %d disconnected", 
                     cnt_get_entry(previous_node)->member.nodeid );
        }
        close(connection_fd);
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "[lobby] Connection with node %d is closed", 
                 cnt_get_entry(previous_node)->member.nodeid );
        connection_fd = -1;
        previous_node = -1;
    }

    ((frame_arg_register_t*)frame->arg)->answer = 1;
    err = frame_send( client_fd, frame );
    if (err != CMM_OK ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[lobby] frame_send failed" );
        frame_free( &frame );
        close(client_fd);
        return;
    }

    connection_fd = client_fd;
    previous_node = cnt_distance(frame->sender);
    frame_free( &frame );
    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "[lobby] I have a new previous node (%d)",
             cnt_get_entry(previous_node)->member.nodeid );
}

static void
receiveFrame()
{
    frame_t *frame;
    cmm_error_t err;

    while ( fdContainsInfo(connection_fd) == 1 ) {
        err = frame_receive( connection_fd,
                             &frame );
        switch (err) {
        case CMM_OK :
            /* Eveything is fine */
            break;

        case CMM_ECONN :
            /* Connection is broken */
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "[lobby] Bad connection. Disconnecting from node %d",
                     cnt_get_entry(previous_node)->member.nodeid );
            if (connection_fd != -1) {
                close(connection_fd);
                connection_fd = -1;
            }
            previous_node = -1;
            break;

        default:
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[lobby] frame_receive failed [%d]",
                     err );
        }
            
        if (err == CMM_OK) {
            processReceivedFrame( frame );
        }
    }
}    

/*
 * send_heartbeat sends an heartbeat from a lobby to the sender module of
 * the previous CMM node
 */

static void
send_heartbeat() 
{
    frame_t *frame;
    cmm_error_t err = CMM_OK;

    if (connection_fd != -1) {
        /* Send the heartbeat to the previous node */

/*         cm_trace(CM_TRACE_LEVEL_DEBUG,  */
/*                  "[lobby] Sending the heartbeat message to my predecessor: %d",  */
/*                  cnt_get_entry(previous_node)->member.nodeid); */

        if (err == CMM_OK) {
            frame = frame_allocate(FRAME_HEARTBEAT,
                                   cnt_get_entry(0)->member.nodeid & 0xFF);
            if (!frame) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "[lobby] frame_allocate failed. Cannot send the heartbeat");
                err = CMM_EOTHER;
            }
        }

        if (err == CMM_OK) {
            err = frame_send(connection_fd, frame);
            if (err != CMM_OK) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "[lobby] Failed to send the heartbeat frame");
            }
        }

        if (frame) {
            frame_free(&frame);
            frame = NULL;
        }
    }
}

static void *
lobby_start_routine( void *arg )
{
    int port = (int)arg;
    int serverSocket;
    struct pollfd fds[2];
    int nb_fds, res;
    struct timespec next_timeout;
    struct timespec remaining;
    int time_to_wait;

    serverSocket = BindServer( port,
                               2 );
    if (serverSocket == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[lobby] BindServer failed. Couldn't start the lobby server. Exiting ..." );
        exit(1);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "[lobby] The lobby server has been started" );

    update_timer(&next_timeout, HEARTBEAT_INTERVAL);

    while ( keep_running ) {
        fds[0].fd = serverSocket;
        fds[0].events = POLLIN;
        fds[0].revents = 0;

        if ( connection_fd == -1 ) {
            nb_fds = 1;
        } else {
            nb_fds = 2;
            fds[1].fd = connection_fd;
            fds[1].events = POLLIN;
            fds[1].revents = 0;
        }

        remaining = remaining_time(next_timeout);

        time_to_wait = remaining.tv_sec*1000 + remaining.tv_nsec / 1000000;

        res = poll(fds, nb_fds, time_to_wait);

        update_timer(&next_timeout, HEARTBEAT_INTERVAL);

        if (res > 0) {
            if (fds[0].revents & POLLIN) {
                acceptClient(serverSocket);
                res = 0;
            }
        }
        
        if ( (res > 0)
             && (nb_fds == 2) ) {
            if ( (fds[1].revents & POLLERR)
                 || (fds[1].revents & POLLHUP) ) {
                cm_trace(CM_TRACE_LEVEL_NOTICE,
                         "[lobby] Connection with previous node (%d) lost",
                         cnt_get_entry(previous_node)->member.nodeid );
                previous_node = CMM_INVALID_NODE_ID;
                close(connection_fd);
                connection_fd = -1;
            }
            
            if (fds[1].revents & POLLIN) {
                receiveFrame();
            }
        }

        /* Send a heartbeat to the previous node */
        send_heartbeat();
        
        if (res < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[lobby] poll failed in lobby [%s]",
                     strerror(errno) );
        }
    }

    return(NULL);
}

/* 
 * Start the lobby server thread
 */

cmm_error_t 
lobby_start( int port,
             int _stack_fd,
             fifo_t *_output_to_sender )
{
    int err;

    stack_fd = _stack_fd;
    output_to_sender = _output_to_sender;

    keep_running = 1;

    connection_fd = -1;
    previous_node = CMM_INVALID_NODE_ID;

    err = pthread_create( &lobby_thread,
                          NULL,
                          lobby_start_routine,
                          (void*)port );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[lobby] pthread_create failed [%d]",
                 err );
        return(CMM_EOTHER);
    }
    return(CMM_OK);
}
