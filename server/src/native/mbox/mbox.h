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
 * Component = Mailbox service - API
 * Synopsis  = Public Mailbox interfaces, structures, constants and definitions
 */

#ifndef _HC_MAILBOX_H
#define _HC_MAILBOX_H

#include <sys/param.h>
//#include <sys/cdefs.h>
#include <stddef.h>
#include <sys/time.h>

#include "jmbox.h"


/*
 * Possible mailbox state
 */
typedef enum {
    SRV_INVALID = 
    com_sun_honeycomb_cm_ipc_Mailbox_SRV_INVALID,
    SRV_INIT = 
    com_sun_honeycomb_cm_ipc_Mailbox_SRV_INIT,
    SRV_READY =
    com_sun_honeycomb_cm_ipc_Mailbox_SRV_READY,
    SRV_RUNNING =
    com_sun_honeycomb_cm_ipc_Mailbox_SRV_RUNNING,
    SRV_DISABLED = 
    com_sun_honeycomb_cm_ipc_Mailbox_SRV_DISABLED,
    SRV_DESTROY =
    com_sun_honeycomb_cm_ipc_Mailbox_SRV_DESTROY
} mb_state_t;

/*
 * Possible transition state
 */
typedef enum {
    ACT_VOID = 
    com_sun_honeycomb_cm_ipc_Mailbox_ACT_VOID,
    ACT_INIT =
    com_sun_honeycomb_cm_ipc_Mailbox_ACT_INIT,
    ACT_STOP =
    com_sun_honeycomb_cm_ipc_Mailbox_ACT_STOP,
    ACT_START =
    com_sun_honeycomb_cm_ipc_Mailbox_ACT_START,
    ACT_DESTROY =
    com_sun_honeycomb_cm_ipc_Mailbox_ACT_DESTROY
} mb_action_t;

typedef enum {
    MB_OK,
    MB_ERROR
} mb_error_t;

typedef void* mb_id_t;
#define MB_INVALID_ID (void*)0

/*
 * Callback triggered when a mailbox state change
 * is requested
 */
typedef void (*mb_callback_t)    (mb_id_t, mb_action_t);

/*
 * Setup the mailboxes directory.
 *
 * This will create the symbolic link from 0 to the actual local node
 * mailboxes if create_link is set to 1
 *
 * IN:
 *   the local node id
 *   if it has to create the sym link
 *
 * OUT:
 *   0 in case of success
 */

extern int mb_init_mailboxes (int nodeid, int create_link);

/*
 * create a new mailbox 
 * A mailbox is created in SRV_INIT state
 * IN:
 *  name - tag name of the mailbox
 *  size - size of the mailbox
 */
extern mb_id_t mb_create (const char* tag,
                          size_t size);

/*
 * remove the given mailbox from the namespace
 * IN:
 *   name - tag name of the mailbox
 */
extern mb_error_t mb_unlink (const char* tag);

/*
 * initialize an existing mailbox 
 * IN:
 *  name     - tag name of the mailbox
 *  callback - user callback triggered when a state 
 *             change is requested
 */
extern mb_id_t mb_init (const char* tag, mb_callback_t clb);

/*
 * mailbox heartbeat routine
 * IN:
 *  mb_id - handle for the mailbox 
 * OUT:
 *  pstate - if not null, contains the requested state 
 *           or 0 if not state change requested.
 */
extern mb_error_t mb_hbt (mb_id_t mb_id, mb_action_t* act);

/*
 * Open a mailbox for read access
 * IN:
 *  name - tag name of the mailbox
 */
extern mb_id_t mb_open (const char* tag);

/*
 * Close a mailbox
 * IN:
 *  mb_id - handle for the mailbox
 */
extern mb_error_t mb_close (mb_id_t mb_id);

/*
 * read user data from a mailbox 
 * IN:
 *  mb_id  - handle for the mailbox 
 *  offset - offset within the mailbox from which to start reading
 *  size   - size of the data
 * OUT:
 *  data   - user data
 */ 
extern mb_error_t mb_read
    (mb_id_t mb_id, void* data, off_t offset, size_t size, int* uid);

/*
 * write user data to a mailbox
 * IN:
 *  mb_id  - handle for the mailbox
 *  offset - offset within the mailbox from which to start writing
 *  size   - size of the data
 *  data   - data
 */
extern mb_error_t mb_write
    (mb_id_t mb_id, void* data, off_t offset, size_t size);

/*
 * get/set client current state of a mailbox 
 */
extern mb_error_t mb_getstate  (mb_id_t mb_id, mb_state_t* state);
extern mb_error_t mb_setstate  (mb_id_t mb_id, mb_state_t state);

/*
 * get/set server current state of a mailbox 
 */
extern mb_error_t mb_getexpectedstate  
    (mb_id_t mb_id, mb_state_t* state);
extern mb_error_t mb_setexpectedstate  
    (mb_id_t mb_id, mb_state_t state);

/*
 * get the last timestamp
 */

extern mb_error_t mb_gettimestamp 
    (mb_id_t mb_id, struct timeval *timestamp);

/*
 * return the length of a mailbox
 */
extern size_t mb_len  (mb_id_t mb_id);

/*
 * return current version (write counter)
 */
extern mb_error_t mb_getversion (mb_id_t mb_id, int *version); 

/*
 * multicast the mailbox
 */
extern mb_error_t mb_broadcast (mb_id_t mb_id);

/*
 * multicast all the mailboxes
 */
extern mb_error_t mb_net_publish ();

/*
 * process an incoming multicast and
 * update the local view of a remote mailbox
 */
extern size_t mb_net_len (mb_id_t mb_id);
extern mb_error_t mb_net_copyout (unsigned char buf[], int nbytes);
extern mb_error_t mb_net_copyin  (mb_id_t mbid, unsigned char buf[],
                                      int nbytes);

/*
 * mark all the mailboxes for the given node as disabled.
 */
extern mb_error_t mb_disable_node (int nodeid);

/*
 * check if this mailbox exists on this node
 */
extern mb_error_t mb_check (const char* name);

#endif /* _HC_MAILBOX_H */
