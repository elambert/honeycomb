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



/* api implementation for the CMM */

#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <fcntl.h>
#include <unistd.h>
#include <strings.h>

#include "api.h"
#include "trace.h"
#include "cmm_parameters.h"
#include "fifo_mt.h"
#include "frame.h"
#include "cnt.h"
#include "comm.h"
#include "election.h"

static int apiSocket;

static fifo_t *notif_clients;
static fifo_t *api_clients;

typedef struct {
    int clientfd;
} client_t;

cmm_error_t
api_init()
{
    apiSocket = BindServer( API_PORT, 10 );
    if (apiSocket == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] BindServer failed");
        return( CMM_EOTHER );
    }

    notif_clients = fifo_init( NULL );
    if (!notif_clients) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] fifo_init failed");
        close(apiSocket);
        apiSocket = -1;
        return( CMM_EOTHER );
    }

    api_clients = fifo_init( NULL );
    if (!api_clients) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] fifo_init failed" );
        fifo_destroy( notif_clients );
        notif_clients = NULL;
        close(apiSocket);
        apiSocket = -1;
        return( CMM_EOTHER );
    }

    return(CMM_OK);
}

static void
accept_client()
{
    frame_t             *frame;
    cmm_error_t         err;
    client_t            *client;
    struct sockaddr_in  clientAddr;
    socklen_t           clientLength;
    socket_type_t       socket_type;

    client = (client_t*)malloc(sizeof(client_t));
    if (!client) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] malloc failed" );
        return;
    }

    clientLength = sizeof(struct sockaddr_in);
    
    client->clientfd = accept( apiSocket,
                               (struct sockaddr*) &clientAddr,
                               &clientLength );
    if ( client->clientfd == -1 ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] accept failed" );
        free(client);
        return;
    }

    err = frame_receive( client->clientfd,
                         &frame );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] frame_receive failed [%d]",
                 err );
        close(client->clientfd);
        free(client);
        return;
    }

    if (frame->type != FRAME_REGISTER) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] Error in the regitration protocol (received a frame of type %d)",
                 frame->type );
        close(client->clientfd);
        free(client);
        return;
    }

    socket_type = (socket_type_t)((frame_arg_register_t*)frame->arg)->answer;
    
    err = CMM_EINVAL;

    switch (socket_type) {
    case SOCKET_API :
        err = fifo_add_elem( api_clients, client );
        break;

    case SOCKET_EVENT :
        err = fifo_add_elem( notif_clients, client );
        break;
    }

    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] fifo_add_elem failed [%d]",
                 err );
        close(client->clientfd);
        free(client);
        return;
    }

    err = frame_send( client->clientfd,
                      frame );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] frame_send failed [%d]",
                 err );
    }
    
    frame_free( &frame );

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "[api] A client connected to the API (fd %d) with a socket of type %s",
             client->clientfd,
             socket_type_strings[socket_type] );
}

static void
send_all_nodes( int fd,
                frame_t *frame )
{
    int index, pos;
    frame_arg_get_member_info_t *args = (frame_arg_get_member_info_t*)frame->arg;
    cmm_error_t err;
    
    args->nb_elems = 0;
    for (index=0; index<cnt_get_nb_nodes(); index++) {
        if ( !(cnt_get_entry(index)->member.sflag & CMM_OUT_OF_CLUSTER) ) {
            args->nb_elems++;
        }
    }

    args->elems = (cmm_member_t*)malloc(args->nb_elems*sizeof(cmm_member_t));
    if (!args->elems) {
        args->nb_elems = 0;
        err = frame_send( fd, frame );
        if (err != CMM_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[api] frame_send failed [%d]",
                     err );
        }
        return;
    }

    pos = 0;
    for (index=0; index<cnt_get_nb_nodes(); index++) {
        if ( !(cnt_get_entry(index)->member.sflag & CMM_OUT_OF_CLUSTER) ) {
            bcopy( &cnt_get_entry(index)->member,
                   args->elems+pos,
                   sizeof(cmm_member_t) );
            ++pos;
        }
    }

    err = frame_send( fd, frame );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] frame_send failed [%d]",
                 err );
    }
}

static void
send_single_member_info( int fd,
                         frame_t *frame,
                         int cnt_index )
{
    frame_arg_get_member_info_t *args = (frame_arg_get_member_info_t*)frame->arg;
    int res;
    cnt_entry_t *entry;
    cmm_error_t err;

    res = 1;

    if (cnt_index == -1) {
        /* Looking for an entry that does not exist */
        args->nb_elems = 0;
        res = 0;
        args->elems = NULL;
    }

    if (res) {
        args->elems = (cmm_member_t*)malloc(sizeof(cmm_member_t));
        if (!args->elems) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[api] malloc failed" );
            res = 0;
        }
    }

    if (res) {
        args->nb_elems = 1;
        entry = cnt_get_entry(cnt_index);
        bcopy(&entry->member, args->elems, sizeof(cmm_member_t));
    }

    /* Send the frame back */
    err = frame_send(fd, frame);
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] frame_send failed [%d]",
                 err );
    }
}

static cmm_error_t
deal_with_client( client_t *client,
                  fifo_t *to_sender )
{
    frame_t *frame;
    cmm_error_t err;

    err = frame_receive( client->clientfd, &frame );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] frame_receive failed [%d]",
                 err );
        return(err);
    }
    
    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "[api] A call has been made on fd %d (frame %s)",
             client->clientfd,
             frame_type_strings[frame->type] );

    switch ( frame->type ) {
    case FRAME_GET_MEMBER_INFO : {
        frame_arg_get_member_info_t *args = (frame_arg_get_member_info_t*)frame->arg;

        switch ( args->type ) {
        case MEMBER_GIVEN_NODE :
            send_single_member_info( client->clientfd,
                                     frame,
                                     cnt_distance( args->node ) );
            break;

        case MEMBER_MASTER :
        case MEMBER_VICEMASTER : {
            int index;
            member_info_t info = args->type;

            /* Find the master node */
            for (index=0; index<cnt_get_nb_nodes(); index++) {
                if ( (info == MEMBER_MASTER)
                     && (cnt_get_entry(index)->member.sflag & CMM_MASTER) ) {
                    break;
                }
                if ( (info == MEMBER_VICEMASTER)
                     && (cnt_get_entry(index)->member.sflag & CMM_VICEMASTER) ) {
                    break;
                }
            }
            
            if ( index == cnt_get_nb_nodes() ) {
                /* Master not found */
                err = frame_send( client->clientfd, frame );
                if (err != CMM_OK) {
                    cm_trace(CM_TRACE_LEVEL_ERROR, 
                             "[api] frame_send failed [%d]",
                             err );
                }
            } else {
                send_single_member_info( client->clientfd,
                                         frame,
                                         index );
            }
        }
            break;

        case MEMBER_ME :
            send_single_member_info( client->clientfd,
                                     frame,
                                     0 );
            break;
            
        case MEMBER_ALL_NB_ONLY : {
            int index;

            args->nb_elems = 0;
            for (index=0; index<cnt_get_nb_nodes(); index++) {
                if ( !(cnt_get_entry(index)->member.sflag & CMM_OUT_OF_CLUSTER) ) {
                    args->nb_elems++;
                }
            }
            
            if ( frame_send( client->clientfd, frame ) != CMM_OK ) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "[api] frame_send failed" );
            }
        }
            break;

        case MEMBER_ALL :
            send_all_nodes( client->clientfd, frame );
            break;
        }
    }
        break;

    case FRAME_DISCONNECT :
        /* client is disconnecting */
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "[api] Disconnecting client from fd %d",
                 client->clientfd );
        close(client->clientfd);
        free(client);
        fifo_remove_current( api_clients );
        break;

    case FRAME_MASTERSHIP_RELEASE :
        /* Release the mastership */
        ((frame_arg_mastership_release_t*)frame->arg)->res = election_mastership_release( to_sender );
        if ( frame_send( client->clientfd, frame ) != CMM_OK ) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[api] frame_send failed" );
        }
        break;

    case FRAME_QUALIF_CHANGE :
        /* For cmm_member_seizequalif */
        if ( ((frame_arg_qualif_change_t*)frame->arg)->nodeid == 0 ) {
            ((frame_arg_qualif_change_t*)frame->arg)->nodeid = cnt_get_entry(0)->member.nodeid & 0xFF;
        }

        election_request_qualif_change(to_sender, frame);
        if ( frame_send(client->clientfd, frame) != CMM_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[api] frame_send failed" );
        }
        break;

    default:
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "[api] Default handler in deal_with_client");
    }

    frame_free( &frame );
    return(CMM_OK);
}

            
void api_fill_fds( int myfd,
                   struct pollfd **fds,
                   int *nb_fds )
{
    int i;
    client_t *client;

    *fds = NULL;
    *nb_fds = 0;
    
    if (apiSocket == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] the API server has not been started" );
        return;
    }

    *nb_fds = fifo_get_nb_elems( api_clients ) +2;
    *fds = (struct pollfd*)malloc((*nb_fds)*sizeof(struct pollfd));
    if (!(*fds)) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] malloc failed" );
        *nb_fds = 0;
        return;
    }

    (*fds)[0].fd = myfd;
    (*fds)[0].events = POLLIN;
    (*fds)[0].revents = 0;
    
    (*fds)[1].fd = apiSocket;
    (*fds)[1].events = POLLIN;
    (*fds)[1].revents = 0;

    client = fifo_get_first( api_clients );

    for (i=2; i<(*nb_fds); i++) {
        (*fds)[i].fd = client->clientfd;
        (*fds)[i].events = POLLIN;
        (*fds)[i].revents = 0;
        client = fifo_get_next( api_clients );
    }
}

void
api_process_incoming( struct pollfd *fds,
                      int nb_fds,
                      fifo_t *to_sender )
{
    int i;
    client_t *client;
    cmm_error_t err;

    if ( fds[1].revents & POLLIN ) {
        /* A client tries to connect */
        accept_client();
    }
    
    client = fifo_get_first( api_clients );
    for (i=2; i<nb_fds; i++) {
        if ( (fds[i].revents & POLLERR)
             || (fds[i].revents & POLLHUP) ) {
            /* The client disconnected */
            fifo_remove_current( api_clients );
            cm_trace(CM_TRACE_LEVEL_DEBUG,
                     "[api] Client on fd %d disconnected",
                     client->clientfd );
            close(client->clientfd);
            free(client);
            client = NULL;
        } else {
            if ( fds[i].revents & POLLIN ) {
                /* The client is making a call */
                err = deal_with_client(client, to_sender);
                if (err != CMM_OK) {
                    cm_trace(CM_TRACE_LEVEL_ERROR, 
                             "[api] Error occured with fd %d. Removing the client",
                             client->clientfd );
                    fifo_remove_current( api_clients );
                    close(client->clientfd);
                    free(client);
                    client = NULL;
                }
            }
        }
        client = fifo_get_next( api_clients );
    }
}

void
api_dispatch_event( cmm_cmchanges_t cmchange,
                    cmm_nodeid_t nodeid )
{
    frame_t *frame;
    client_t *client;
    cmm_error_t err;
    frame_arg_notification_t *args;

    frame = frame_allocate( FRAME_NOTIFICATION, 0 );
    if (!frame) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "[api] frame_allocate failed" );
        return;
    }

    args = (frame_arg_notification_t*)frame->arg;
    args->notif.cmchange = cmchange;
    args->notif.nodeid = nodeid;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "[api] Dispatching event %d %d",
             cmchange,
             nodeid );

    client = fifo_get_first( notif_clients );
    while (client) {
        err = frame_send( client->clientfd, frame );
        if (err != CMM_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "[api] frame_send failed [%d] - removing the client",
                     err );
            fifo_remove_current( notif_clients );
            close(client->clientfd);
            free(client);
            client = NULL;
        }
        client = fifo_get_next( notif_clients );
    }

    frame_free( &frame );
}
