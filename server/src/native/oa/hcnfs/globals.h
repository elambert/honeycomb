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



#ifndef _GLOBALS_H_
#define _GLOBALS_H_

#include <rpc/rpc.h>
#include "HcNfsDAAL.h"


#define	MNTPATHLEN 1024
#define	FHSIZE3 64
#define BUFFER_SIZE 0x1000 // 4k write cache (small file)

#define NODEBASEID  com_sun_honeycomb_oa_daal_hcnfs_NfsAccess_NODE_BASE_ID
#define TMPMAP      com_sun_honeycomb_oa_daal_hcnfs_NfsAccess_TMPMAP
#define ROOTMAP     com_sun_honeycomb_oa_daal_hcnfs_NfsAccess_ROOTMAP
#define NUMDIR      com_sun_honeycomb_oa_daal_hcnfs_NfsAccess_NUMDIR

/*
 * Define the following to cache tcp connections
 * at the rpc library level
 #define USR_RPC_TLI_CACHE   1
 */

/*
 * FIXME - we should not hardcode values this way.
 * Instead setup the constants dynamically when initializing the 
 * library.
 */
#define TMPNAME                 "tmp-close"
#define INTERNAL_SUBNET         "10.123.45"
#define DATADIR                 "data"
#define DB_FILENAME_PATTERN     "/nfsdisks/%d_%d.db"
#define MAX_NUM_NODES           16
#define MAX_NUM_DISKS_PER_NODE  4

/*
 * RPC operation timeout
 * Disk is checked for validity after this timeout.
 * The operation is retried max times with a doubled 
 * timeout every time. 
 */
static const struct timeval HCRPC_TIMEOUT = { 12, 0 };
static const struct timeval HCRPC_LONG_TIMEOUT = { 30, 0 };

/*
 * Max number of retries before giving up
 * Must account for transient software failure.
 * total nfs operation timeout: 2x8s=16s
 */
static const int HCRPC_MAXRETRIES = 2;

/*
 * A NFS3 handle
 */
typedef struct {
	u_int fhandle3_len;
	char *fhandle3_val;
} fhandle3;


/*
 * native part of the HcNfsDAAL.
 */
typedef struct {
    int nodeid;
    int diskid;
    int mapid;
    int read_only;
    CLIENT *clnt;
    char *filename;
    
    fhandle3 filehandle;
    u_longlong_t offset;
    unsigned long size;
    
    char buffer[BUFFER_SIZE];
    int buffer_length;
} native_data_t;


extern bool_t xdr_fhandle3(register XDR *xdrs, fhandle3 *objp);
extern void print_fh(int nodeid, int diskid, int mapid, fhandle3 *filehandle);

extern CLIENT *create_nfs_connection(int nodeid, char *protocol);
extern CLIENT *create_nlm_connection(int nodeid, char *protocol);
extern void destroy_connection(CLIENT *clnt);


#define log_error(format, args...) \
    _log_error(__FILE__, __LINE__, format, ##args)

extern void _log_error(const char *file, int line, const char* format, ...);

#endif /* _GLOBALS_H_ */
