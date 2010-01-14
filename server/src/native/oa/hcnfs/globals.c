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



#include <string.h>
#include <strings.h>
#include <syslog.h>
#include <stdarg.h>
#include <errno.h>
#include <time.h>

#include "globals.h"
#include "nfs_rpc.h"
#include "nlm_rpc.h"


bool_t
xdr_fhandle3(register XDR *xdrs, fhandle3 *objp)
{
#if defined(_LP64) || defined(_KERNEL)
	register int *buf;
#else
	register long *buf;
#endif

	if (!xdr_bytes(xdrs, (char **)&objp->fhandle3_val, 
                   (u_int *) &objp->fhandle3_len, 
                   FHSIZE3))
    {
		return (FALSE);
    }
	return (TRUE);
}

void
print_fh(int nodeid, int diskid, int mapid, fhandle3 *filehandle)
{
    char buf[1024];
    
    if (nodeid > 0) {
        if (mapid == TMPMAP) {
            snprintf(buf, sizeof(buf), "%s.%d:/%s/%d/%s ",
                     INTERNAL_SUBNET,
                     nodeid,
                     DATADIR,
                     diskid,
                     TMPNAME);
        } else {
            snprintf(buf, sizeof(buf), "%s.%d:/%s/%d/%02d/%02d ",
                     INTERNAL_SUBNET,
                     nodeid,
                     DATADIR,
                     diskid,
                     mapid / NUMDIR,
                     mapid % NUMDIR);
        }
    } else {
        buf[0] = '\0';
    }
    
    int index = strlen(buf);
    for (int i=0; i<filehandle->fhandle3_len; i++) {
        sprintf(buf+index, "%02x", filehandle->fhandle3_val[i]);
        index += 2;
    }
    printf("%s\n", buf);
}

void
_log_error(const char* file, int line, const char* format, ...)
{
    char fmt[1024];
    va_list ap;
    
    snprintf(fmt, sizeof(fmt), "file(%s) line(%d): %s", file, line, format);
    
    //openlog("hcnfs", LOG_CONS | LOG_NDELAY, LOG_USER );
    va_start(ap, format);
    vsyslog(LOG_WARNING|LOG_USER, fmt, ap);
    va_end(ap);
    //closelog();
}

CLIENT *
create_nfs_connection(int nodeid, char *protocol)
{
    char buf[32];
    CLIENT *clnt = NULL;

    snprintf(buf, sizeof(buf), "%s.%d", INTERNAL_SUBNET, nodeid + NODEBASEID);

    int retry;
    for (retry = 0; retry < HCRPC_MAXRETRIES; retry++) {
        const struct timespec throttle = { 0, 100000 };
        clnt = clnt_create_timed(buf, NFS_PROGRAM, NFS_V3, protocol, &HCRPC_TIMEOUT);
        if (clnt != NULL) {
            break;
        }
        nanosleep(&throttle, NULL);
    }
    if (clnt) {
        clnt->cl_auth = authsys_create_default();
    } else {
        log_error("failed to create nfs connection %s [errno %d]", buf, errno);
    }

    return(clnt);
}

CLIENT *
create_nlm_connection(int nodeid, char *protocol)
{
    char buf[32];
    CLIENT *clnt = NULL;

    snprintf(buf, sizeof(buf), "%s.%d", INTERNAL_SUBNET, nodeid + NODEBASEID);
    
    int retry;
    for (retry = 0; retry < HCRPC_MAXRETRIES; retry++) {
        const struct timespec throttle = { 0, 100000 };
        clnt = clnt_create_timed(buf, NLM_PROGRAM, NLM_V4, protocol, &HCRPC_TIMEOUT);
        if (clnt != NULL) {
            break;
        }
        nanosleep(&throttle, NULL);        
    }
    if (clnt) {
        clnt->cl_auth = authsys_create_default();
    } else {
        log_error("failed to create nlm connection %s [errno %d]", buf, errno);
    }

    return(clnt);
}

void
destroy_connection(CLIENT *clnt)
{
#ifdef USR_RPC_TLI_CACHE
    int clfd;
    if (clnt_control(clnt, CLGET_FD, (char *) &clfd) == TRUE) {
        /*
         * avoid to let the tcp connection in time_wait state
         * note: use a private api to set the socket option
         * through the stream (tli) api
         */
        if (__rpc_tli_set_options(clfd, SOL_SOCKET, SO_LINGER, 0) < 0) {
            /*
             * Not all rpc connection uses tcp -
             * in turn this ioctl can fail for udp connection with
             * bad filedesc.
             */
#ifdef DEBUG
            log_error("failed to set SO_LINGER(%d)", errno);
#endif
        }
        if (__rpc_tli_set_options(clfd, SOL_SOCKET, SO_REUSEADDR, 1) < 0) {
            log_error("failed to set SO_REUSEADDR(%d)", errno);
        }
    } else {
        log_error("failed to get client fd(%p)", clnt);
    }
#else
    if (clnt_control(clnt, CLSET_FD_CLOSE, NULL) == FALSE) {
        log_error("failed to close client fd(%p)", clnt);
    }
#endif
    if (clnt->cl_auth) {
        auth_destroy(clnt->cl_auth);
        clnt->cl_auth = NULL;
    }
    clnt_destroy(clnt);
}
