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
 * Synopsis  = disk structure and constant
 */

#ifndef _HC_DISK_H
#define _HC_DISK_H

#include <sys/param.h>
#include <sys/cdefs.h>
#include <uuid/uuid.h>
#include <stddef.h>
#include <fcntl.h>


#define HC_XFS_PARTITION 2
#define DISK_OMODE (S_IRWXU|S_IRWXG|S_IRWXO)
#define HC_DIR_SETUP ".dirsetup"

/*
 * Status 
 */
#define DISK_BAD  0
#define DISK_OK   1
#define DISK_INIT 2
#define DISK_STOP 3

/*
 * Flags 
 */
#define DISK_ERROR     1
#define DISK_FAKE      2
#define DISK_CORRUPTED 4

typedef struct {
    unsigned int   rd_sects;
    unsigned int   wr_sects;
    unsigned int   tt_nios;
    unsigned int   tt_ticks;
    struct timeval last_update;
    unsigned int   total_size;
    unsigned int   avail_size;
    unsigned int   temperature;
    unsigned int   sectors_reallocated;
    unsigned int   pending_reallocated;
    unsigned int   offline_unrecoverable;
    float          write_kBs;
    float          read_kBs;
    float          io_wait;
} disk_stat_t;

typedef struct {
    char         *dev_name;
    int          flags;
    dev_t        dev_id;
    uuid_t       disk_id;
    int          status;
    char         *exportfs;
    char         *partition;
    disk_stat_t  stats;
} disk_t;


extern int  disk_setup     __P((const char*, disk_t*));
extern int  disk_init      __P((disk_t*));
extern int  disk_start     __P((disk_t*));
extern int  disk_stop      __P((disk_t*));
extern int  disk_stat      __P((disk_t*));
extern int  disk_smart     __P((disk_t*));
extern void disk_heartbeat __P((void));

#endif /* _HC_DISK_H */
