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



#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <stdio.h>
#include <strings.h>
#include <stdio.h>

#include "handle_repos.h"

/* must be at least the max honeycomb configuration */
static fhdb_data_t *fhdbs[MAX_NUM_NODES][MAX_NUM_DISKS_PER_NODE];

void
hrep_encode_db_name(char *buf, int size, int nodeid, int diskid)
{
    snprintf(buf, size, DB_FILENAME_PATTERN, nodeid + NODEBASEID, diskid);
}

static void
hrep_open_all()
{
    struct stat statdata;
    char buf[MAXPATHLEN];

    for (int i=0; i<MAX_NUM_NODES; i++) {
        for (int j=0; j<MAX_NUM_DISKS_PER_NODE; j++) {
            if (!fhdbs[i][j]) {
                hrep_encode_db_name(buf, sizeof(buf), (i+1), j);
                if (!stat(buf, &statdata)) {
                    // The file is there but not loaded yet ...
                    fhdbs[i][j] = fhdb_init(buf, 0);
                }
            }
        }
    }
}

int
hrep_check_disk(int nodeid, int diskid)
{
    int idx = nodeid - 1;
    if (idx < 0 || idx >= MAX_NUM_NODES) {
        return(0);
    }
    return (fhdbs[idx][diskid] != NULL &&
            fhdb_isvalid(fhdbs[idx][diskid]));
}

fhdb_data_t *
hrep_get_db(int nodeid, int diskid)
{
    int idx = nodeid - 1;
    if (idx < 0 || idx >= MAX_NUM_NODES) {
        return(NULL);
    }
    
    if (!fhdbs[idx][diskid]) {
        /*
         * first time - initialize db cache for all disks
         */
        hrep_open_all();
        if (!fhdbs[idx][diskid]) {
            log_error("failed to initialize db cache for node %d disk %d",
                      idx, diskid);
            return(NULL);
        }
    }

    if (!fhdb_isvalid(fhdbs[idx][diskid])) {
        /*
         * the corresponding db does not exists or the cache is stalled.
         */
        char buf[MAXPATHLEN];
        hrep_encode_db_name(buf, sizeof(buf), nodeid, diskid);
        fhdb_destroy(fhdbs[idx][diskid]);
        fhdbs[idx][diskid] = fhdb_init(buf, 0);
        if (!fhdbs[idx][diskid]) {
            log_error("failed to open db cache(%d, %d)", nodeid, diskid);
            return(NULL);
        }
    }
    
    return(fhdbs[idx][diskid]);
}
