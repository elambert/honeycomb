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
 * Synopsis  = mailbox management
 */

#include <sys/types.h>
#include <stdio.h>
#include "mbdisk.h"
#include "serialization.h"
#include "trace.h"

/*
 * Write the mbdisk object to the mailbox
 */
int
mbdisk_write(mb_id_t mbid, mbdisk_t* mbdisk)
{
    static int _first_write = 1;
    hc_serialization_t *hdl;
    int i;
    int ret;

    hdl = hc_serialization_open(mbid, DISKSERVER_MAILBOX_TYPE,
                                DISKSERVER_MAILBOX_VERSION, _first_write);
    if (hdl == NULL) {
        return (-1);
    }
    ret = hc_serialization_write_short(hdl, mbdisk->nb_entries);
    if (ret == 0) {
        for (i = 0; i < mbdisk->nb_entries; i++) {

            ret = hc_serialization_write_uuid(hdl, 
                      mbdisk->dsks[i].disk_id);
            if (ret != 0) break;

            ret = hc_serialization_write_int(hdl,
                    mbdisk->dsks[i].disk_index);
            if (ret != 0) break;

            ret = hc_serialization_write_int(hdl, 
                      mbdisk->dsks[i].disk_status);
            if (ret != 0) break;

            ret = hc_serialization_write_int(hdl, 
                      mbdisk->dsks[i].disk_size);
            if (ret != 0) break;

            ret = hc_serialization_write_int(hdl, 
                      mbdisk->dsks[i].avail_size);
            if (ret != 0) break;

            ret = hc_serialization_write_int(hdl, 
                      mbdisk->dsks[i].bad_sectors);
            if (ret != 0) break;

            ret = hc_serialization_write_int(hdl, 
                      mbdisk->dsks[i].pending_sectors);
            if (ret != 0) break;

            ret = hc_serialization_write_int(hdl, 
                      mbdisk->dsks[i].temperature);
            if (ret != 0) break;

            ret = hc_serialization_write_float(hdl, 
                      mbdisk->dsks[i].readkbs);
            if (ret != 0) break;

            ret = hc_serialization_write_float(hdl, 
                      mbdisk->dsks[i].writekbs);
            if (ret != 0) break;

            ret = hc_serialization_write_float(hdl, 
                      mbdisk->dsks[i].iotime);
            if (ret != 0) break;

            ret = hc_serialization_write_string(hdl, 
                      mbdisk->dsks[i].disk_devname);
            if (ret != 0) break;

            ret = hc_serialization_write_string(hdl, 
                      mbdisk->dsks[i].mount_point);
            if (ret != 0) break;
        }
    }
    if (ret != 0) { 
        hc_serialization_abort(hdl);
        return (-1);
    }
    hc_serialization_commit(hdl);
    if (_first_write) {
        _first_write = 0;
    }
    return (0);
}

