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



#ifndef _FRAME_H_
#define _FRAME_H_

#include "cmm_internal_types.h"
#include "cmm.h"

#define DEST_NEXT_ONLY 0x00
#define DEST_BROADCAST 0xFF

typedef enum {
    /* These frames are for the ring control */
    FRAME_REGISTER =1,
    FRAME_DISCONNECT,
    FRAME_HEARTBEAT,

    /* These frames go to the stack */
    FRAME_NODE_CHANGE,
    FRAME_ELECTION,

    /* These frames are between the server and the libs */
    FRAME_NOTIFICATION,
    FRAME_GET_MEMBER_INFO,
    FRAME_MASTERSHIP_RELEASE,
    FRAME_QUALIF_CHANGE
} frame_type_t;

extern char *frame_type_strings[];

typedef struct {
    unsigned char sender;
    unsigned char dest;
    frame_type_t type;
    void *arg;
} frame_t;

cmm_error_t frame_send( int fd,
                        frame_t *frame );

/*
 * frame_receive allocates the arg parameter if needed !
 */

cmm_error_t frame_receive( int fd,
                           frame_t **frame );

frame_t *frame_allocate( frame_type_t type,
                         unsigned char sender );
void frame_free( frame_t **frame );

frame_t *frame_duplicate( frame_t *frame );

/**********************************************************************
 *
 * Frame arguments
 *
 **********************************************************************/

/*
 * Answer has the value : (ring semantic)
 * 0 : if not initialized
 * 1 : if accepted
 * 2 : if refused
 */

/*
 * Here are the possible values for the lib - server semantic
 * 1 : api socket
 * 2 : event socket
 */

typedef enum {
    SOCKET_API =1,
    SOCKET_EVENT =2
} socket_type_t;

extern char *socket_type_strings[];

typedef struct {
    char answer;
} frame_arg_register_t;

typedef struct {
    char left;
    unsigned char nodeid;
} frame_arg_node_change_t;

typedef struct {
    char office; /* 1 master - 2 vicemaster */
    char request; /* 1 if this to ask for an election - 0 otherwise */
    unsigned char elected_node;
} frame_arg_election_t;

typedef struct {
    cmm_cmc_notification_t notif;
} frame_arg_notification_t;

/*
 * Datas to retrieve the member info
 */

typedef enum {
    MEMBER_ME,
    MEMBER_MASTER,
    MEMBER_VICEMASTER,
    MEMBER_GIVEN_NODE,
    MEMBER_ALL_NB_ONLY,
    MEMBER_ALL
} member_info_t;

typedef struct {
    member_info_t type;
    cmm_nodeid_t  node;
    int           nb_elems;
    cmm_member_t  *elems;
} frame_arg_get_member_info_t;

/* FRAME_MASTERSHIP_RELEASE */

typedef struct {
    cmm_error_t res;
} frame_arg_mastership_release_t;

/* FRAME_QUALIF_CHANGE */

typedef struct {
    unsigned char nodeid;
    unsigned char new_qualif;
    char request;
    cmm_error_t res;            /* To be used only between the lib and the server */
} frame_arg_qualif_change_t;

#endif /* _FRAME_H_ */
