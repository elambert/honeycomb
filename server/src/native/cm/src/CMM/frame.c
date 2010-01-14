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



/* Implementation of methods for reading and writing different message types */

#include <stdlib.h>
#include <strings.h>
#include <poll.h>
#include <unistd.h>

#include "frame.h"
#include "trace.h"
#include "cmm_parameters.h"

char *frame_type_strings[] = {
    "INVALID !!!",
    "REGISTER",
    "DISCONNECT",
    "HEARTBEAT",
    "NODE_CHANGE",
    "ELECTION",
    "NOTIFICATION",
    "GET_MEMBER_INFO",
    "MASTERSHIP_RELEASE",
    "QUALIF_CHANGE"
};

char *socket_type_strings[] = {
    "INVALID",
    "SOCKET_API",
    "SOCKET_EVENT"
};

/********************************************************************/

static int
frame_buffer_write( int fd,
                    char *buf,
                    int size )
{
    int written_bytes, this_shot;
    int res;
    struct pollfd fds[1];

    /* Check that you can write */

    fds[0].fd = fd;
    fds[0].events = POLLOUT;
    fds[0].revents = 0;

    res = poll( fds, 1, 0);
    if (res < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "poll failed in frame" );
        return(-1);
    }

    if ( (fds[0].revents & POLLERR)
         || (fds[0].revents & POLLHUP) ) {

        /*
         * The following test has been removed because it appears to fail
         * on a pipe on Linux ...
         */
        /*          || !(fds[0].revents & POLLOUT) ) { */
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "Cannot write in the fd %d",
                 fd );

        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Flags : %x %x %x",
                 fds[0].revents & POLLERR,
                 fds[0].revents & POLLHUP,
                 fds[0].revents & POLLOUT );
                  
        return(-1);
    }

    written_bytes = 0;
    do {
        this_shot = write( fd, buf+written_bytes, size-written_bytes );
        if (this_shot <= 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "write failed" );
            return(-1);
        }
        written_bytes += this_shot;
    } while (written_bytes != size);

    return(0);
}

static int
frame_buffer_read( int fd,
                   char *buf,
                   int size )
{
    int read_bytes, this_shot, res;
    struct pollfd fds[1];

    fds[0].fd = fd;
    fds[0].events = POLLIN;
    fds[0].revents = 0;
    
    res = poll(fds, 1, HEARTBEAT_TIMEOUT);
    if (res < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "poll failed in frame_buffer_read" );
        return(-1);
    }

    if (res == 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Timedout waiting for datas in frame_buffer_read");
        return(-1);
    }

    if ( (fds[0].revents & POLLERR)
         || (fds[0].revents & POLLHUP) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "Cannot read in the fd %d",
                 fd);
        
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Flags : %x %x %x",
                 fds[0].revents & POLLERR,
                 fds[0].revents & POLLHUP,
                 fds[0].revents & POLLOUT );
        
        return(-1);
    }
    
    read_bytes = 0;
    do {
        this_shot = read( fd, buf+read_bytes, size-read_bytes );
        if (this_shot <= 0) {
            cm_trace(CM_TRACE_LEVEL_DEBUG,
                     "Warning ! read failed" );
            return(-1);
        }
        read_bytes += this_shot;
    } while (read_bytes != size);

    return(0);
}

static cmm_error_t
send_arguments( int fd,
                frame_t *frame )
{
    int result;

    switch (frame->type) {
    case FRAME_REGISTER :
        result = frame_buffer_write(fd, frame->arg, sizeof(frame_arg_register_t));
        break;

    case FRAME_NODE_CHANGE :
        result = frame_buffer_write(fd, frame->arg, sizeof(frame_arg_node_change_t));
        break;

    case FRAME_ELECTION :
        result = frame_buffer_write(fd, frame->arg, sizeof(frame_arg_election_t));
        break;

    case FRAME_NOTIFICATION :
        result = frame_buffer_write(fd, frame->arg, sizeof(frame_arg_notification_t));
        break;

    case FRAME_GET_MEMBER_INFO : {
        frame_arg_get_member_info_t *args = (frame_arg_get_member_info_t*)frame->arg;

        result = frame_buffer_write(fd, (char*)args, sizeof(args->type) + sizeof(args->node) + sizeof(args->nb_elems));
        if ( (result == 0)
             && (args->nb_elems > 0)
             && (args->type != MEMBER_ALL_NB_ONLY) ) {
            result = frame_buffer_write(fd, (char*)args->elems, args->nb_elems*sizeof(cmm_member_t));
        }
    }
        break;

    case FRAME_MASTERSHIP_RELEASE :
        result = frame_buffer_write(fd, frame->arg, sizeof(frame_arg_mastership_release_t));
        break;

    case FRAME_QUALIF_CHANGE :
        result = frame_buffer_write(fd, frame->arg, sizeof(frame_arg_qualif_change_t));
        break;

    default:
        return(CMM_OK);
    }

    if ( result == 0 ) {
        return(CMM_OK);
    }

    return(CMM_ECONN);
}

cmm_error_t
frame_send( int fd,
            frame_t *frame )
{
    char type;
    cmm_error_t err;

    type = (char)frame->type;

    if ( frame_buffer_write(fd, (char*)&frame->sender, sizeof(frame->sender))
         || frame_buffer_write(fd, (char*)&frame->dest, sizeof(frame->dest))
         || frame_buffer_write(fd, (char*)&type, sizeof(type)) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "frame_buffer_write failed" );
        return(CMM_EOTHER);
    }

    err = send_arguments( fd, frame );
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "send_arguments failed");
        return(err);
    }

    return(CMM_OK);
}

static cmm_error_t
receive_arguments( int fd,
                   frame_t *frame )
{
    int result;

    switch (frame->type) {
    case FRAME_REGISTER :
        result = frame_buffer_read(fd, frame->arg, sizeof(frame_arg_register_t));
        break;

    case FRAME_NODE_CHANGE :
        result = frame_buffer_read(fd, frame->arg, sizeof(frame_arg_node_change_t));
        break;

    case FRAME_ELECTION :
        result = frame_buffer_read(fd, frame->arg, sizeof(frame_arg_election_t));
        break;

    case FRAME_NOTIFICATION :
        result = frame_buffer_read(fd, frame->arg, sizeof(frame_arg_notification_t));
        break;

    case FRAME_GET_MEMBER_INFO : {
        frame_arg_get_member_info_t *args = (frame_arg_get_member_info_t*)frame->arg;

        result = frame_buffer_read(fd, (char*)args, sizeof(args->type) + sizeof(args->node) + sizeof(args->nb_elems));

        if ( (result == 0)
             && (args->nb_elems > 0) 
             && (args->type != MEMBER_ALL_NB_ONLY) ) {
            args->elems = (cmm_member_t*)malloc(args->nb_elems*sizeof(cmm_member_t));
            if (!args->elems) {
                result = -1;
                args->nb_elems = 0;
            }

            if (result == 0) {
                result = frame_buffer_read(fd, (char*)args->elems, args->nb_elems*sizeof(cmm_member_t));
            }
        } else {
            args->elems = NULL;
        }
    }
        break;

    case FRAME_MASTERSHIP_RELEASE :
        result = frame_buffer_read(fd, frame->arg, sizeof(frame_arg_mastership_release_t));
        break;

    case FRAME_QUALIF_CHANGE :
        result = frame_buffer_read(fd, frame->arg, sizeof(frame_arg_qualif_change_t));
        break;

    default:
        frame->arg = NULL;
        return(CMM_OK);
    }

    if (result == 0) {
        return(CMM_OK);
    }
    return(CMM_ECONN);
}

cmm_error_t
frame_receive( int fd,
               frame_t **frame )
{
    unsigned char sender, dest;
    char type = -1;
    cmm_error_t err;

    if ( frame_buffer_read(fd, (char*)&sender, sizeof(sender))
         || frame_buffer_read(fd, (char*)&dest, sizeof(dest))
         || frame_buffer_read(fd, (char*)&type, sizeof(type)) ) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Warning ! frame_buffer_read failed" );
        return(CMM_ECONN);
    }

    *frame = frame_allocate( (frame_type_t)type, sender );
    if (!*frame) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "frame_allocate failed" );
        return(CMM_ENOMEM);
    }

    (*frame)->dest = dest;
    (*frame)->type = (frame_type_t)type;

    err = receive_arguments(fd, *frame);
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "receive_arguments failed");
        frame_free( frame );
        return(err);
    }

    if (type != FRAME_HEARTBEAT) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Received a frame of type %s from nodeid %d",
                 frame_type_strings[(int)type],
                 sender);
    }

    return(CMM_OK);
}

frame_t *
frame_allocate( frame_type_t type,
                unsigned char sender )
{
    frame_t *result;

    result = (frame_t*)malloc(sizeof(frame_t));
    if (!result) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "malloc failed" );
        return(NULL);
    }

    result->sender = sender;
    /*     cnt_get_entry(0)->member.nodeid & 0xFF; */
    result->dest = DEST_BROADCAST;
    result->type = type;
    result->arg = NULL;

    switch ( type ) {
    case FRAME_REGISTER :
        result->arg = (frame_arg_register_t*)malloc(sizeof(frame_arg_register_t));
        break;

    case FRAME_NODE_CHANGE :
        result->arg = (frame_arg_node_change_t*)malloc(sizeof(frame_arg_node_change_t));
        break;

    case FRAME_ELECTION :
        result->arg = (frame_arg_node_change_t*)malloc(sizeof(frame_arg_election_t));
        break;

    case FRAME_NOTIFICATION :
        result->arg = (frame_arg_notification_t*)malloc(sizeof(frame_arg_notification_t));
        break;        

    case FRAME_GET_MEMBER_INFO :
        result->arg = (frame_arg_get_member_info_t*)malloc(sizeof(frame_arg_get_member_info_t));
        ((frame_arg_get_member_info_t*)result->arg)->nb_elems = 0;
        ((frame_arg_get_member_info_t*)result->arg)->elems = NULL;
        break;        

    case FRAME_MASTERSHIP_RELEASE :
        result->arg = (frame_arg_mastership_release_t*)malloc(sizeof(frame_arg_mastership_release_t));
        break;                

    case FRAME_QUALIF_CHANGE :
        result->arg = (frame_arg_mastership_release_t*)malloc(sizeof(frame_arg_qualif_change_t));
        break;                

    default:
        /* No argument needed */
        return(result);
    }

    if (!result->arg) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "malloc failed" );
        free(result);
        return(NULL);
    }

    return(result);
}

void
frame_free( frame_t **frame )
{
    switch ( (*frame)->type ) {
    case FRAME_GET_MEMBER_INFO : {
        frame_arg_get_member_info_t *args = (frame_arg_get_member_info_t*)(*frame)->arg;
        
        if ( args->elems ) {
            free(args->elems);
            args->elems = NULL;
        }
    }
        break;
	
    default:
        break;
    }

    if ( (*frame)->arg ) {
        free( (*frame)->arg );
    }
    free(*frame);

    *frame = NULL;
}

frame_t *
frame_duplicate( frame_t *frame )
{
    frame_t *result;
    int     arg_size;

    result = frame_allocate( frame->type, frame->sender );
    if (!result) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "frame_allocate failed" );
        return(NULL);
    }

    result->dest = frame->dest;

    arg_size = 0;
    switch ( frame->type ) {
    case FRAME_REGISTER :
        arg_size = sizeof( frame_arg_register_t );
        break;

    case FRAME_NODE_CHANGE :
        arg_size = sizeof( frame_arg_node_change_t );
        break;

    case FRAME_ELECTION :
        arg_size = sizeof( frame_arg_election_t );
        break;

    case FRAME_NOTIFICATION :
        arg_size = sizeof( frame_arg_notification_t );
        break;

    case FRAME_GET_MEMBER_INFO : {
        frame_arg_get_member_info_t *args = (frame_arg_get_member_info_t*)frame->arg;
        frame_arg_get_member_info_t *res_args = (frame_arg_get_member_info_t*)result->arg;

        bcopy( frame->arg, result->arg, sizeof(frame_arg_get_member_info_t) );
        if (args->nb_elems == 0) {
            return(result);
        }
        
        if ( (args->nb_elems > 0)
             && (args->type != MEMBER_ALL_NB_ONLY) ) {
            res_args->elems = (cmm_member_t*)malloc(args->nb_elems*sizeof(cmm_member_t));
            if (!res_args->elems) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "malloc failed" );
                frame_free( &result );
                return(NULL);
            }
            bcopy( args->elems, res_args->elems, args->nb_elems*sizeof(cmm_member_t));
        } else {
            res_args = NULL;
        }
                   
        return(result);
    }
        break;

    case FRAME_MASTERSHIP_RELEASE :
        arg_size = sizeof( frame_arg_mastership_release_t );
        break;        

    case FRAME_QUALIF_CHANGE :
        arg_size = sizeof( frame_arg_qualif_change_t );
        break;        

    default:
        /* Nothing to be done */
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Default handler in frame_duplicate");
    }
    
    if ( arg_size ) {
        bcopy( frame->arg, result->arg, arg_size );
    }

    return(result);
}
