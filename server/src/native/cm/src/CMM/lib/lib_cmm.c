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



#include <pthread.h>
#include <stdlib.h>
#include <unistd.h>
#include <strings.h>

#include "cmm.h"
#include "comm.h"
#include "cmm_parameters.h"
#include "frame.h"

/********************************************************************************
 *
 * Global variables for the library
 *
 ********************************************************************************/

static int event_socket = -1;
static int api_socket = -1;

static int lib_initialized =0;
static pthread_mutex_t lib_mutex;

typedef struct {
    cmm_notify_t callback;
    void *cookie;
} callback_t;

static callback_t callback;
static unsigned int event_filter;

static char *cmm_error_strings[] = {
	"CMM_OK",
	"CMM_EBADF",
	"CMM_EBUSY",
	"CMM_ECANCELED",
	"CMM_EEXIST",
	"CMM_EINVAL",
	"CMM_ENOENT",
	"CMM_ENOMSG",
	"CMM_ENOTSUP",
	"CMM_EPERM",
	"CMM_ERANGE",
	"CMM_ESRCH",
	"CMM_ENOCLUSTER",
	"CMM_ECONN",
	"CMM_ETIMEDOUT",
	"CMM_EAGAIN",

    /* Internal errors */
    "CMM_ENOMEM",
    "CMM_EOTHER"
};

/********************************************************************************
 *
 * Private functions
 *
 ********************************************************************************/

static cmm_error_t
cmm_lib_init()
{
    if ( lib_initialized ) {
        return(CMM_OK);
    }

    if ( pthread_mutex_init( &lib_mutex, NULL ) ) {
        return( CMM_EOTHER );
    }
    
    callback.callback = NULL;
    callback.cookie = NULL;

    event_filter = 0xFFFFFFFF;

    lib_initialized = 1;

    return(CMM_OK);
}

static cmm_error_t
enter_api_routine()
{
    cmm_error_t err;
    timespec_t timeout = {0,0};

    if ( !lib_initialized ) {
        err = cmm_lib_init();
        if (err != CMM_OK ) {
            return(err);
        }
    }

    if (api_socket == -1) {
        err = cmm_connect(timeout);
        if (err != CMM_OK) {
            return(err);
        }
    }
    
    if ( pthread_mutex_lock( &lib_mutex ) ) {
        return(CMM_EOTHER);
    }

    return(CMM_OK);
}


static void
exit_api_routine()
{
    pthread_mutex_unlock( &lib_mutex );
}

static cmm_error_t
connect_socket( socket_type_t type )
{
    int *result = NULL;
    struct sockaddr_in address;
    cmm_error_t err;
    frame_t *frame;

    switch (type) {
    case SOCKET_API :
        result = &api_socket;
        break;

    case SOCKET_EVENT :
        result = &event_socket;
        break;
    }

    if ( *result != -1 ) {
        return(CMM_OK);
    }

    if ( GetHostAddress( NULL,
                         API_PORT,
                         &address ) != 1 ) {
        return(CMM_ECONN);
    }

    (*result) = ConnectToServer( &address );
    if ((*result) == -1) {
        return(CMM_ECONN);
    }

    /* Send the FRAME_REGISTER frame */
    
    frame = frame_allocate( FRAME_REGISTER, 0 );
    if (!frame) {
        close(*result);
        *result = -1;
        return(CMM_ENOMEM);
    }

    ((frame_arg_register_t*)frame->arg)->answer = (char)type;

    err = frame_send( *result, frame );
    if (err != CMM_OK) {
        close(*result);
        *result = -1;
        return(err);
    }
        
    frame_free( &frame );

    err = frame_receive( *result, &frame );
    if (err != CMM_OK) {
        close(*result);
        *result = -1;
        return(err);
    }

    frame_free( &frame );

    return(CMM_OK);
}

static cmm_error_t
cmm_single_get_info( cmm_member_t *member,
                     member_info_t type )
{
    cmm_error_t err;
    frame_t *frame;
    frame_arg_get_member_info_t *args;

    frame = frame_allocate( FRAME_GET_MEMBER_INFO, 0 );
    if (!frame) {
        return(CMM_ENOMEM);
    }

    args = (frame_arg_get_member_info_t*)frame->arg;
    args->type = type;
    args->node = 0;

    err = frame_send( api_socket, frame );
    if (err != CMM_OK) {
        frame_free( &frame );
        return(err);
    }

    frame_free( &frame );

    err = frame_receive( api_socket, &frame );
    if (err != CMM_OK) {
        return(err);
    }

    args = (frame_arg_get_member_info_t*)frame->arg;

    if (args->nb_elems != 1) {
        frame_free( &frame );
        return(CMM_ESRCH);
    }

    bcopy(args->elems, member, sizeof(cmm_member_t));
    frame_free( &frame );
    
    return(CMM_OK);
}

/********************************************************************************
 *
 * API implementation
 *
 ********************************************************************************/

/*
 * Connects to the CMM server
 * Does not use the timeout parameter for now
 */

cmm_error_t 
cmm_connect(timespec_t const P_timeout)
{
    cmm_error_t err;

    if ( !lib_initialized ) {
        err = cmm_lib_init();
        if (err != CMM_OK ) {
            return(err);
        }
    }

    if ( pthread_mutex_lock( &lib_mutex ) ) {
        return(CMM_EOTHER);
    }

    err = connect_socket( SOCKET_API );
    if (err != CMM_OK) {
        exit_api_routine();
        return(err);
    }

    exit_api_routine();

    return(CMM_OK);
}

cmm_error_t 
cmm_disconnect()
{
    cmm_error_t err;
    frame_t *frame;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    frame = frame_allocate( FRAME_DISCONNECT, 0 );
    if (!frame) {
        err = CMM_ENOMEM;
    }

    if (err == CMM_OK) {
        err = frame_send( api_socket, frame );
    }

    if (frame) {
        frame_free( &frame );
    }

    if (err == CMM_OK) {
        err = frame_receive( api_socket, &frame );
    }

    if (err == CMM_OK) {
        frame_free( &frame );
    }

    close(api_socket);
    api_socket = -1;

    exit_api_routine();
    
    return(CMM_OK);
}

cmm_error_t 
cmm_node_getid(cmm_nodeid_t * const me)
{
    cmm_error_t err;
    cmm_member_t member;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    err = cmm_single_get_info( &member,
                               MEMBER_ME );
    if (err == CMM_OK) {
        *me = member.nodeid;
    } else {
        *me = CMM_INVALID_NODE_ID;
    }

    exit_api_routine();
    
    return(err);
}

cmm_error_t 
cmm_member_getinfo(cmm_nodeid_t const nodeid,
                   cmm_member_t * const member)
{
    cmm_error_t err;
    frame_t *frame;
    frame_arg_get_member_info_t *args;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    frame = frame_allocate( FRAME_GET_MEMBER_INFO, 0 );
    if (!frame) {
        exit_api_routine();
        return(CMM_ENOMEM);
    }

    args = (frame_arg_get_member_info_t*)frame->arg;
    args->type = MEMBER_GIVEN_NODE;
    args->node = nodeid;

    err = frame_send( api_socket, frame );
    if (err != CMM_OK) {
        frame_free( &frame );
        exit_api_routine();
        return(err);
    }

    frame_free( &frame );

    err = frame_receive( api_socket, &frame );
    if (err != CMM_OK) {
        exit_api_routine();
        return(err);
    }

    args = (frame_arg_get_member_info_t*)frame->arg;

    if (args->nb_elems != 1) {
        frame_free( &frame );
        exit_api_routine();
        return(CMM_ESRCH);
    }

    bcopy(args->elems, member, sizeof(cmm_member_t));
    frame_free( &frame );
    exit_api_routine();
    
    return(CMM_OK);
}

cmm_error_t 
cmm_potential_getinfo(cmm_nodeid_t const nodeid,
                      cmm_member_t * const member)
{
    return( cmm_member_getinfo(nodeid, member) );
}

cmm_error_t 
cmm_master_getinfo(cmm_member_t * const member)
{
    cmm_error_t err;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    err = cmm_single_get_info(member, MEMBER_MASTER);

    exit_api_routine();
    
    return(err);
}

cmm_error_t 
cmm_vicemaster_getinfo(cmm_member_t * const member)
{
    cmm_error_t err;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    err = cmm_single_get_info(member, MEMBER_VICEMASTER);

    exit_api_routine();
    
    return(err);
}

cmm_error_t 
cmm_member_getcount(uint32_t * const member_count)
{
    cmm_error_t err;
    frame_t *frame;
    frame_arg_get_member_info_t *args;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    frame = frame_allocate( FRAME_GET_MEMBER_INFO, 0 );
    if (!frame) {
        exit_api_routine();
        return(CMM_ENOMEM);
    }

    args = (frame_arg_get_member_info_t*)frame->arg;
    args->type = MEMBER_ALL_NB_ONLY;
    args->node = 0;

    err = frame_send( api_socket, frame );
    if (err != CMM_OK) {
        frame_free( &frame );
        exit_api_routine();
        return(err);
    }

    frame_free( &frame );

    err = frame_receive( api_socket, &frame );
    if (err != CMM_OK) {
        exit_api_routine();
        return(err);
    }

    args = (frame_arg_get_member_info_t*)frame->arg;

    *member_count = args->nb_elems;

    frame_free( &frame );
    
    exit_api_routine();
    
    return(CMM_OK);
}

cmm_error_t 
cmm_member_getall(uint32_t       const table_size,
                  cmm_member_t * const member_table,
                  uint32_t     * const member_count)
{
    cmm_error_t err;
    frame_t *frame;
    frame_arg_get_member_info_t *args;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    frame = frame_allocate( FRAME_GET_MEMBER_INFO, 0 );
    if (!frame) {
        exit_api_routine();
        return(CMM_ENOMEM);
    }

    args = (frame_arg_get_member_info_t*)frame->arg;
    args->type = MEMBER_ALL;
    args->node = 0;

    err = frame_send( api_socket, frame );
    if (err != CMM_OK) {
        frame_free( &frame );
        exit_api_routine();
        return(err);
    }

    frame_free( &frame );

    err = frame_receive( api_socket, &frame );
    if (err != CMM_OK) {
        exit_api_routine();
        return(err);
    }

    args = (frame_arg_get_member_info_t*)frame->arg;

    *member_count = args->nb_elems<=table_size ? args->nb_elems : table_size;
    bcopy( args->elems,
           member_table,
           (*member_count)*sizeof(cmm_member_t) );

    frame_free( &frame );
    
    exit_api_routine();
    
    return(CMM_OK);
}

cmm_error_t 
cmm_mastership_release()
{
    cmm_error_t err;
    frame_t *frame;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    frame = frame_allocate( FRAME_MASTERSHIP_RELEASE, 0 );
    if (!frame) {
        exit_api_routine();
        return(CMM_ENOMEM);
    }

    if ( frame_send( api_socket, frame ) != CMM_OK ) {
        frame_free( &frame );
        exit_api_routine();
        return(CMM_ECONN);
    }

    frame_free( &frame );

    if ( frame_receive( api_socket, &frame ) != CMM_OK ) {
        exit_api_routine();
        return(CMM_ECONN);
    }

    err = ((frame_arg_mastership_release_t*)frame->arg)->res;

    frame_free( &frame );

    exit_api_routine();
    
    return( err );
}

/*
 * TEMPORARY API -
 */
cmm_error_t 
cmm_node_eligible()
{
    cmm_error_t err;
    frame_t *frame;
    cmm_nodeid_t local;
    frame_arg_notification_t *args;

    err = cmm_node_getid(&local);
    if (err != CMM_OK) {
        return err;
    }

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    frame = frame_allocate( FRAME_NOTIFICATION, 0 );
    if (!frame) {
        exit_api_routine();
        return(CMM_ENOMEM);
    }

    args = (frame_arg_notification_t*)frame->arg;
    args->notif.cmchange = CMM_NODE_ELIGIBLE;
    args->notif.nodeid   = local;

    if ( frame_send( api_socket, frame ) != CMM_OK ) {
        frame_free( &frame );
        exit_api_routine();
        return(CMM_ECONN);
    }

    frame_free( &frame );

    if ( frame_receive( api_socket, &frame ) != CMM_OK ) {
        exit_api_routine();
        return(CMM_ECONN);
    }

    args = (frame_arg_notification_t*)frame->arg;
    err = args->notif.cmchange;

    frame_free( &frame );

    exit_api_routine();
    
    return( err );
}

/*
 * TEMPORARY API -
 */
cmm_error_t 
cmm_node_ineligible()
{
    cmm_error_t err;
    frame_t *frame;
    cmm_nodeid_t local;
    frame_arg_notification_t *args;

    err = cmm_node_getid(&local);
    if (err != CMM_OK) {
        return err;
    }

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    frame = frame_allocate( FRAME_NOTIFICATION, 0 );
    if (!frame) {
        exit_api_routine();
        return(CMM_ENOMEM);
    }

    args = (frame_arg_notification_t*)frame->arg;
    args->notif.cmchange = CMM_NODE_INELIGIBLE;
    args->notif.nodeid   = local;

    if ( frame_send( api_socket, frame ) != CMM_OK ) {
        frame_free( &frame );
        exit_api_routine();
        return(CMM_ECONN);
    }

    frame_free( &frame );

    if ( frame_receive( api_socket, &frame ) != CMM_OK ) {
        exit_api_routine();
        return(CMM_ECONN);
    }

    args = (frame_arg_notification_t*)frame->arg;
    err = args->notif.cmchange;

    frame_free( &frame );

    exit_api_routine();
    
    return( err );
}

/* That API routine is not implemented */

cmm_error_t 
cmm_membership_remove()
{
    return(CMM_EBADF);
}

cmm_error_t 
cmm_member_setqualif(cmm_nodeid_t const nodeid,
                     cmm_qualif_t const new_qualif)
{
    cmm_error_t err;
    frame_t *frame;
    frame_arg_qualif_change_t *args;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    frame = frame_allocate( FRAME_QUALIF_CHANGE, 0 );
    if (!frame) {
        return(CMM_ENOTSUP);
    }

    args = (frame_arg_qualif_change_t*)frame->arg;
    
    args->nodeid = nodeid & 0xFF;
    args->new_qualif = new_qualif + 150;
    args->request = 1;
    args->res = CMM_OK;

    if ( frame_send( api_socket, frame ) != CMM_OK ) {
        frame_free(&frame);
        exit_api_routine();
        return(CMM_ECONN);
    }

    frame_free( &frame );

    if ( frame_receive(api_socket, &frame) != CMM_OK ) {
        exit_api_routine();
        return(CMM_ECONN);
    }

    err = ((frame_arg_qualif_change_t*)frame->arg)->res;
    frame_free(&frame);

    exit_api_routine();
    
    return(err);
}

cmm_error_t 
cmm_member_seizequalif()
{
    return( cmm_member_setqualif(0, CMM_QUALIFIED_MEMBER) );
}

cmm_error_t 
cmm_cmc_register(cmm_notify_t cl_callback, void *client_data)
{
    cmm_error_t err;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    err = connect_socket( SOCKET_EVENT );
    if (err != CMM_OK) {
        exit_api_routine();
        return(err);
    }
   
    callback.callback = cl_callback;
    callback.cookie = client_data;

    exit_api_routine();
    
    return(CMM_OK);
}

cmm_error_t 
cmm_cmc_unregister()
{
    cmm_error_t err;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    if ( event_socket == -1 ) {
        exit_api_routine();
        return(CMM_ECONN);
    }

    callback.callback = NULL;
    callback.cookie = NULL;

    close(event_socket);
    event_socket = -1;

    exit_api_routine();
    
    return(CMM_OK);
}

cmm_error_t 
cmm_cmc_filter(	int P_action,
                cmm_cmchanges_t *P_event_list,
                int P_event_list_count)
{
    cmm_error_t err;
    int i;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    switch ( P_action ) {
    case CMM_CMC_NOTIFY_ADD :
        for (i=0; i<P_event_list_count; i++) {
            event_filter |= 1 << (P_event_list[i]-250);
        }
        break;

    case CMM_CMC_NOTIFY_REM :
        for (i=0; i<P_event_list_count; i++) {
            event_filter &= ~(1 << (P_event_list[i]-250));
        }
        break;

    case CMM_CMC_NOTIFY_SET :
        event_filter = 0;
        for (i=0; i<P_event_list_count; i++) {
            event_filter |= 1 << (P_event_list[i]-250);
        }
        break;

    case CMM_CMC_NOTIFY_ALL :
        event_filter = 0xFFFFFFFF;
        break;

    case CMM_CMC_NOTIFY_NONE :
        event_filter = 0;
        break;
    }

    exit_api_routine();
    
    return(CMM_OK);
}

cmm_error_t 
cmm_notify_getfd(int *fd)
{
    cmm_error_t err;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    err = connect_socket( SOCKET_EVENT );
    if (err != CMM_OK) {
        exit_api_routine();
        return(err);
    }
   
    *fd = event_socket;

    exit_api_routine();
    
    return(CMM_OK);
}

cmm_error_t 
cmm_notify_dispatch()
{
    cmm_error_t err;
    frame_t *frame;
    frame_arg_notification_t *args;

    err = enter_api_routine();
    if (err != CMM_OK) {
        return(err);
    }

    if ( event_socket == -1 ) {
        exit_api_routine();
        return(CMM_ECONN);
    }

    if (!callback.callback) {
        exit_api_routine();
        return(CMM_OK);
    }

    err = frame_receive( event_socket, &frame );
    if (err != CMM_OK) {
        exit_api_routine();
        return(err);
    }

    args = (frame_arg_notification_t*)frame->arg;

    exit_api_routine();
    
    /* Check if the notification has to be filtered out */
    if ( event_filter & ( 1 << (args->notif.cmchange-250)) ) {
        callback.callback( &args->notif,
                           callback.cookie );
    }

    frame_free( &frame );

    return(CMM_OK);
}

/*
 * This routine is not implemented
 */

cmm_error_t 
cmm_config_reload()
{
    return(CMM_OK);
}

char *
cmm_strerror(cmm_error_t errnum)
{
    if (errnum == CMM_OK) {
        return(cmm_error_strings[0]);
    }

    return( cmm_error_strings[errnum+101] );
}

int 
cmm_member_isoutofcluster(cmm_member_t const * member)
{
    return( member->sflag & CMM_OUT_OF_CLUSTER ? 1 : 0 );
}

int 
cmm_member_isfrozen(cmm_member_t const * member)
{
    return( member->sflag & CMM_FROZEN_MEMBER ? 1 : 0 );
}

int 
cmm_member_isexcluded(cmm_member_t const * member)
{
    return( member->sflag & CMM_EXCLUDED_MEMBER ? 1 : 0 );
}

int 
cmm_member_iseligible(cmm_member_t const * member)
{
    return( member->sflag & CMM_ELIGIBLE_MEMBER ? 1 : 0 );
}

int 
cmm_member_ismaster(cmm_member_t const * member)
{
    return( member->sflag & CMM_MASTER ? 1 : 0 );
}

int 
cmm_member_isvicemaster(cmm_member_t const * member)
{
    return( member->sflag & CMM_VICEMASTER ? 1 : 0 );
}

int 
cmm_member_isqualified(cmm_member_t const * member)
{
    return( !cmm_member_isdisqualified(member) );
}

int 
cmm_member_isdisqualified(cmm_member_t const * member)
{
    return( member->sflag & CMM_FLAG_DISQUALIFIED ? 1 : 0 );
}

/*
 * That call is a dummy one (synchronisation not implemented)
 */

int 
cmm_member_isdesynchronized(cmm_member_t const * member)
{
    return(0);
}
