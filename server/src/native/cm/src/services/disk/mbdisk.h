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
 * Component = disk server  
 * Synopsis  = disk mailbox structure
 */

#ifndef _HC_MBDISK_H
#define _HC_MBDISK_H

#include <sys/cdefs.h>
#include <uuid/uuid.h>
#include <mbox.h>

#define DISKSERVER_MAILBOX_TAG     "DiskServer"
#define DISKSERVER_MAILBOX_TYPE    2
#define DISKSERVER_MAILBOX_VERSION 1
#define DISKSERVER_MAXDISKS        8
#define DISKSERVER_MAXPATHLEN      64
#define DISKSERVER_MAXDEVLEN       16

#define MBDISK_BAD 0
#define MBDISK_OK  1

typedef char mbpath_t[DISKSERVER_MAXPATHLEN];
typedef char mbdev_t[DISKSERVER_MAXDEVLEN];

typedef struct {
    short nb_entries;
    struct {
         uuid_t      disk_id;
         int         disk_index;
         mbdev_t     disk_devname;
         int         disk_status;
         int         disk_size;
         int         avail_size;
         int         temperature;
         int         bad_sectors;
         int         pending_sectors;
         float       readkbs;
         float       writekbs;
         float       iotime;
         mbpath_t    mount_point;
    } dsks[DISKSERVER_MAXDISKS];
} mbdisk_t;
    
extern int mbdisk_write __P((mb_id_t, mbdisk_t*));

#endif /* _HC_MBDISK_H */
