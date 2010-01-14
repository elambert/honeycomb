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
 * This files defines the interface to write structured fields in a
 * mailbox.
 *
 * These fields will then be readable from some Java code.
 *
 * Today, int and strings are supported
 */

#ifndef _SERIALIZATION_H_
#define _SERIALIZATION_H_

#include "mbox.h"

typedef void *hc_serialization_t;

/*
 * This returns a pointer needed for futher calls.
 *
 * - mailbox_type is an integer which describes the service writing to that
 *   mailbox. On the reading side, a check can be made to ensure that the
 *   format of the mailbox is correctly interpreted.
 *
 * - version is the version number for mailbox format
 *
 * - first_write specifies if the sequence number should be set back to 1
 *
 * Returns NULL in case of error
 */

hc_serialization_t *
hc_serialization_open(mb_id_t mailbox,
                      short mailbox_type,
                      short version,
                      int first_write);

/*
 * The write operations return :
 *     - 0 in case of success
 *     - 1 in case of failure
 */
                      
int
hc_serialization_write_short(hc_serialization_t *handle,
                             short value);

int
hc_serialization_write_int(hc_serialization_t *handle,
                           int value);

int
hc_serialization_write_float(hc_serialization_t *handle,
                             float value);
int
hc_serialization_write_string(hc_serialization_t *handle,
                              char *string);

int
hc_serialization_write_uuid(hc_serialization_t *handle,
                            unsigned char uuid[16]);

/*
 * The commit routine effectively writes the object in the mailbox.
 *
 * The handle is not valid after it has been called.
 */

void
hc_serialization_commit(hc_serialization_t *handle);

void
hc_serialization_abort(hc_serialization_t *handle);

#endif /* _SERIALIZATION_H_ */
