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
#include <sys/cdefs.h>
#include <stddef.h>
#include <cmm.h>
#include <sys/time.h>

/*
 * Possible mailbox state
 */
typedef enum {
    SRV_INVALID = 0,
    SRV_INIT,
    SRV_READY,
    SRV_RUNNING,
    SRV_DISABLED, 
    SRV_DESTROY
} mb_state_t;

/*
 * Possible transition state
 */
typedef enum {
    ACT_VOID = 0,
    ACT_INIT,
    ACT_STOP,
    ACT_START,
    ACT_DESTROY
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

extern int mb_init_mailboxes __P((cmm_nodeid_t nodeid,
                                  int create_link));

/*
 * create a new mailbox 
 * A mailbox is created in SRV_INIT state
 * IN:
 *  name - tag name of the mailbox
 *  size - size of the mailbox
 */
extern mb_id_t mb_create __P((const char* tag,
                              size_t size));

/*
 * remove the given mailbox from the namespace
 * IN:
 *   name - tag name of the mailbox
 */
extern mb_error_t mb_unlink __P((const char* tag));

/*
 * initialize an existing mailbox 
 * IN:
 *  name     - tag name of the mailbox
 *  callback - user callback triggered when a state 
 *             change is requested
 */
extern mb_id_t mb_init __P((const char* tag, mb_callback_t chgState));

/*
 * mailbox heartbeat routine
 * IN:
 *  mb_id - handle for the mailbox 
 * OUT:
 *  pstate - if not null, contains the requested state 
 *           or 0 if not state change requested.
 */
extern mb_error_t mb_hbt __P((mb_id_t mb_id, mb_action_t* act));

/*
 * Open a mailbox for read access
 * IN:
 *  name - tag name of the mailbox
 */
extern mb_id_t mb_open __P((const char* tag));

/*
 * Close a mailbox
 * IN:
 *  mb_id - handle for the mailbox
 */
extern mb_error_t mb_close __P((mb_id_t mb_id));

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
    __P((mb_id_t mb_id, void* data, off_t offset, size_t size));

/*
 * write user data to a mailbox
 * IN:
 *  mb_id  - handle for the mailbox
 *  offset - offset within the mailbox from which to start writing
 *  size   - size of the data
 *  data   - data
 */
extern mb_error_t mb_write
    __P((mb_id_t mb_id, void* data, off_t offset, size_t size));

/*
 * get/set client current state of a mailbox 
 */
extern mb_error_t mb_getstate  __P((mb_id_t mb_id, mb_state_t* state));
extern mb_error_t mb_setstate  __P((mb_id_t mb_id, mb_state_t state));

/*
 * get/set server current state of a mailbox 
 */
extern mb_error_t mb_getexpectedstate  __P((mb_id_t mb_id, mb_state_t* state));
extern mb_error_t mb_setexpectedstate  __P((mb_id_t mb_id, mb_state_t state));

/*
 * get the last timestamp
 */

extern mb_error_t mb_gettimestamp __P((mb_id_t mb_id, struct timeval *timestamp));

/*
 * return the length of a mailbox
 */
extern size_t mb_len  __P((mb_id_t mb_id));

/*
 * multicast the mailbox
 */
extern mb_error_t mb_broadcast __P((mb_id_t mb_id));

#endif /* _HC_MAILBOX_H */
