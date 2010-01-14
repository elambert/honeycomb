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



/* The stack deals with the different types of messages coming into a node */

#include <sys/errno.h>
#include <stdlib.h>

#include "stack.h"
#include "frame.h"
#include "cmm.h"
#include "trace.h"
#include "cnt.h"
#include "election.h"
#include "utils.h"
#include "cmm_parameters.h"
#include "api.h"

static int lobby_fd =-1;
static fifo_t *to_sender;

static int keep_running;

static void
node_joined( frame_t *frame )
{
    frame_t *reply;

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "[stack] Node %d has joined the cluster",
             ((frame_arg_node_change_t*)frame->arg)->nodeid );

    api_dispatch_event(CMM_MEMBER_JOINED, ((frame_arg_node_change_t*)frame->arg)->nodeid);

    /* Send my own status if the joined node is not me */

    if ( cnt_distance(frame->sender) == 0 ) {
        if (frame->dest == DEST_BROADCAST) {
            start_new_election( to_sender, 1 );
        }
        return;
    }
    
    reply = frame_allocate( FRAME_NODE_CHANGE, frame->sender );
    if (!reply) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "frame_allocate failed" );
        return;
    }

    reply->dest = frame->sender;
    ((frame_arg_node_change_t*)reply->arg)->left = 0;
    ((frame_arg_node_change_t*)reply->arg)->nodeid = cnt_get_entry(0)->member.nodeid & 0XFF;

    if ( fifo_add_elem( to_sender, reply ) != CMM_OK ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "fifo_add_elem failed" );
        frame_free( &reply );
    }

    if ( (cnt_get_entry(0)->member.sflag & CMM_MASTER)
         || (cnt_get_entry(0)->member.sflag & CMM_VICEMASTER) ) {
        frame_arg_election_t *args;

        reply = frame_allocate( FRAME_ELECTION, frame->sender );
        reply->dest = frame->sender;
        
        args = (frame_arg_election_t*)reply->arg;
        
        if ( cnt_get_entry(0)->member.sflag & CMM_MASTER ) {
            args->office = 1;
        } else {
            args->office = 2;
        }

        args->request = 0;
        args->elected_node = cnt_get_entry(0)->member.nodeid;
        
        if ( fifo_add_elem( to_sender, reply ) != CMM_OK ) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "fifo_add_elem failed" );
            frame_free( &reply );
        }
    }

    if ( cnt_get_entry(0)->member.sflag & CMM_FLAG_DISQUALIFIED ) {
        frame_arg_qualif_change_t *args;

        reply = frame_allocate( FRAME_QUALIF_CHANGE, frame->sender );
        reply->dest = frame->sender;
        
        args = (frame_arg_qualif_change_t*)reply->arg;
        
        args->nodeid = cnt_get_entry(0)->member.nodeid & 0xFF;
        args->new_qualif = CMM_DISQUALIFIED_MEMBER + 150;
        args->request = 0;
        args->res = CMM_OK;
        
        if ( fifo_add_elem( to_sender, reply ) != CMM_OK ) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "fifo_add_elem failed" );
            frame_free( &reply );
        }
    }
}

static void
dealWithFrame( frame_t *frame )
{
    char have_to_free_frame = 1;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "[stack] Received a frame of type %s (sender is %d)",
             frame_type_strings[frame->type],
             frame->sender );
    
    switch ( frame->type ) {
    case FRAME_NODE_CHANGE : {
        frame_arg_node_change_t *args = (frame_arg_node_change_t*)frame->arg;
        int node_index;

        node_index = cnt_distance(args->nodeid);

        if ( args->left ) {
            if (!(cnt_get_entry(node_index)->member.sflag & CMM_OUT_OF_CLUSTER)) {
                cnt_get_entry(node_index)->member.sflag |= CMM_OUT_OF_CLUSTER;
                if ( cnt_get_entry(node_index)->member.sflag & CMM_MASTER ) {
                    node_elected(to_sender, args->nodeid, -1);
                }
                if ( cnt_get_entry(node_index)->member.sflag & CMM_VICEMASTER ) {
                    node_elected(to_sender, args->nodeid, -2);
                }

                api_dispatch_event( CMM_MEMBER_LEFT, ((frame_arg_node_change_t*)frame->arg)->nodeid );

                cm_trace(CM_TRACE_LEVEL_NOTICE,
                         "[stack] Node %d has left the cluster",
                         args->nodeid );
            }
        } else {
            if (cnt_get_entry(node_index)->member.sflag & CMM_OUT_OF_CLUSTER) {
                cnt_get_entry(node_index)->member.sflag &= ~CMM_OUT_OF_CLUSTER;
                node_joined( frame );
            }
        }
    }
        break;

    case FRAME_ELECTION :
        election_deal_with_frame( to_sender, frame );
        have_to_free_frame = 0;
        break;

    case FRAME_QUALIF_CHANGE :
        election_qualif_change(to_sender, frame);
        break;

    default:
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Default handler in dealWithFrame");
    }

    if (have_to_free_frame) {
        frame_free( &frame );
    }
}

void
stack_start( int _lobby_fd,
             fifo_t *_to_sender )
{
    frame_t *frame;
    cmm_error_t err;
    struct pollfd *fds;
    int nb_fds, nb_busy;

    lobby_fd = _lobby_fd;
    to_sender = _to_sender;

    keep_running = 1;
    while (keep_running) {
        err = CMM_OK;

        api_fill_fds( lobby_fd, &fds, &nb_fds );
        if (nb_fds == 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "api_fill_fds failed" );
            err = CMM_EOTHER;
        }

        if ( err == CMM_OK ) {
            nb_busy = poll( fds, nb_fds, -1 );
            if (nb_busy < 0) {
                if (errno == EINTR) continue; /* ignore interrupts */
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "stack: poll failed in stack [%d]", nb_busy);
                err = CMM_EOTHER;
            }
        }

        if ( (err == CMM_OK)
             && (fds[0].revents & POLLIN) ) {
            /* There is something in the pipe */
            err = frame_receive( lobby_fd, &frame );
            if ( err != CMM_OK ) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[stack] frame_receive failed [%d]",
                         err );
            }

            if ( err == CMM_OK ) {
                dealWithFrame( frame );
            }
        }

        api_process_incoming( fds, nb_fds, to_sender );
        if (fds) {
            free(fds);
            fds = NULL;
            nb_fds = 0;
        }
    }
}
