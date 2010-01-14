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



#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <malloc.h>

#include "globals.h"
#include "mount.h"
#include "mount_rpc.h"
#include "fhdb.h"
#include "nfs.h"

static void
encode_path(int diskid, int mapid, char *buf, int buflen)
{
    if (mapid == TMPMAP) {
        snprintf(buf, buflen, "/%s/%d/%s", DATADIR, diskid, TMPNAME);

    } else if (mapid == ROOTMAP) {
        snprintf(buf, buflen, "/%s/%d", DATADIR, diskid);

    } else {
        snprintf(buf, buflen, "/%s/%d/%02d/%02d",
                 DATADIR,
                 diskid,
                 mapid / NUMDIR,
                 mapid % NUMDIR);
    }
}

int
single_mount(CLIENT *clnt, char *diskPath, fhandle3 *filehandle)
{
    int error = 0;

    struct mountres3 res;
    bzero(&res, sizeof(res));

    res.mountres3_u.mountinfo.fhandle.fhandle3_val = filehandle->fhandle3_val;

    enum clnt_stat stat = clnt_call(clnt, MOUNTPROC3_MNT,
                                    (xdrproc_t) xdr_dirpath, (caddr_t)&diskPath,
                                    (xdrproc_t) xdr_mountres3, (caddr_t)&res,
                                    HCRPC_TIMEOUT);
    if (stat != RPC_SUCCESS) {
        log_error("RPC called failed(%d)", stat);
        error = 1;
    } else {            
        if (res.fhs_status != MNT3_OK) {
            log_error("Server didn't return OK [%d]", res.fhs_status);
            error = 1;
        } else {
            filehandle->fhandle3_len = res.mountres3_u.mountinfo.fhandle.fhandle3_len;
            res.mountres3_u.mountinfo.fhandle.fhandle3_val = NULL;
        }            
        xdr_free((xdrproc_t)xdr_mountres3, (caddr_t)&res);
    }        

    return(error);
}

int
get_handle(int nodeid, int diskid, int mapid, fhandle3 *filehandle)
{
    CLIENT *clnt = NULL;
    int error = 0;
    char nodeAddr[32];
    char *diskPath = NULL;

    diskPath = (char*)malloc(MNTPATHLEN);
    if (!diskPath) {
        return(1);
    }

    snprintf(nodeAddr, sizeof(nodeAddr), "%s.%d", INTERNAL_SUBNET, nodeid + NODEBASEID);
    encode_path(diskid, mapid, diskPath, MNTPATHLEN);
    
    clnt = clnt_create_timed(nodeAddr, MOUNT_PROGRAM, MOUNT_V3, "udp", &HCRPC_TIMEOUT);
    if (!clnt) {
        free(diskPath);
        log_error("clnt_create failed(%d, %d)", nodeid, diskid);
        return(1);
    }

    error = single_mount(clnt, diskPath, filehandle);

    if (clnt) {
        clnt_destroy(clnt);
        clnt = NULL;
    }
    if (diskPath) {
        free(diskPath);
        diskPath = NULL;
    }
    
    return(error);
}

int
mount_disk(fhdb_data_t *db, int nodeid, int diskid)
{
    CLIENT *clnt = NULL;
    int error = 0;
    fhdb_key_t key;
    char filename[16];
    long lookup_res;
    
    char rootfilehandle_data[FHSIZE3];
    fhandle3 rootfilehandle;
    rootfilehandle.fhandle3_val = rootfilehandle_data;

    char level1filehandle_data[FHSIZE3];
    fhandle3 level1filehandle;
    level1filehandle.fhandle3_val = level1filehandle_data;

    char level2filehandle_data[FHSIZE3];
    fhandle3 level2filehandle;
    level2filehandle.fhandle3_val = level2filehandle_data;

    // Get the filehandle of the root directory
    error = get_handle(nodeid, diskid, ROOTMAP, &rootfilehandle);
    if (error) {
        return(1);
    }

    // Build the connection
    clnt = create_nfs_connection(nodeid, "tcp");
    if (clnt == NULL) {
        return (1);
    }

    // Iterate through the directory structure

    key.nodeid = (short)nodeid;
    key.diskid = (short)diskid;

    for (int i=0; i<NUMDIR; i++) {
        snprintf(filename, sizeof(filename), "%02d", i);
        lookup_res = nfs_lookup(nodeid, diskid, &clnt, &rootfilehandle, 
                                &level1filehandle, filename);
        if (lookup_res < 0) {
            log_error("Failed to get handle for level 1 %d:%d %d",
                      NODEBASEID+nodeid, 
                      diskid, 
                      NUMDIR*i
                      );
            error = 1;
            break;
        } else {
            for (int j=0; j<NUMDIR; j++) {
                snprintf(filename, sizeof(filename), "%02d", j);
                lookup_res = nfs_lookup(nodeid, diskid, &clnt, 
                                        &level1filehandle, &level2filehandle, 
                                        filename);
                if (lookup_res < 0) {
                    log_error("Failed to get handle for level 2 %d:%d-%d",
                              NODEBASEID+nodeid, 
                              diskid, 
                              NUMDIR*i+j
                              );
                    error = 1;
                    break;
                } else {
                    key.mapid = NUMDIR*i+j;
                    fhdb_set(db, &key, &level2filehandle);
                }
            }
            if (error != 0) {
                break;
            }
        }
    }

    /* Look for the temporary directory */
    if (error == 0) {
        lookup_res = nfs_lookup(nodeid, diskid, &clnt, &rootfilehandle, 
                                &level1filehandle, TMPNAME);
        if (lookup_res < 0) {
            log_error("Failed to get handle for temporary directory on %d:%d",
                      NODEBASEID+nodeid, 
                      diskid);
            error = 1;
        } else {
            key.mapid = TMPMAP;
            fhdb_set(db, &key, &level1filehandle);
        }
    }
    
    clnt_destroy(clnt);
    return(error);
}
