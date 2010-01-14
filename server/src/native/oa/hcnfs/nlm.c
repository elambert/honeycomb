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


#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>
#include <netdb.h>
#include <errno.h>
#include <thread.h>

#include "nlm.h"
#include "nlm_rpc.h"
#include "fhdb.h"
#include "globals.h"

static int
lm_callrpc(CLIENT *clnt, int action, xdrproc_t proc, caddr_t args)
{
    nlm4_res res;
    bzero(&res, sizeof(nlm4_res));
    int error;
    
    // TODO = an error 12 generic "other problem"
    // can be returned
    enum clnt_stat stat = clnt_call(clnt, action, proc, args,
                                    (xdrproc_t)xdr_nlm4_res, (caddr_t)&res,
                                    HCRPC_TIMEOUT);
    
    if (stat != RPC_SUCCESS) {
        log_error("RPC call failed [%d]", stat);
        return(stat);
    }

    if (res.stat.stat != NLM4_GRANTED) {
#if 0
        /* too verbose ? */
        log_error("The server failed to %s [%d]", 
                  ((action == NLMPROC4_LOCK) ? "lock" : "unlock"),
                  res.stat.stat);
#endif
        error = res.stat.stat;
    } else {
        error = 0;
    }
    
    xdr_free((xdrproc_t)xdr_nlm4_res, (caddr_t)&res);
    return(error);
}


int
nfs_trylock(CLIENT *clnt, fhandle3 *fh, int cookie)
{
    char owner[MAXPATHLEN];
    char hostname[MAXHOSTNAMELEN];
    nlm4_lockargs args;
    nlm4_lock alk;
    pid_t pid;

    bzero(&args, sizeof(nlm4_lockargs));
    
    if (gethostname(hostname, sizeof(hostname)) != 0) {
        log_error("Hostname not found(%d)", errno);
        return(-1);
    }
    if ((pid = getpid()) == -1) {
        log_error("Error getting pid(%d)", errno);
        return(-1);
    }
    
    snprintf(owner, sizeof(owner), "%s%d%d", hostname, pid, thr_self());
    
    alk.caller_name = hostname;
    alk.fh.n_len = fh->fhandle3_len;
    alk.fh.n_bytes = fh->fhandle3_val;
    alk.oh.n_len = strlen(owner) + 1;
    alk.oh.n_bytes = owner;
    alk.svid = pid;
    alk.l_offset = 0; 
    alk.l_len = 0;
    
    args.cookie.n_len = sizeof (cookie);
    args.cookie.n_bytes = (char *)&cookie;
    args.block = 0; /* non-blocking */
    args.exclusive = 1;
    args.alock = alk;
    args.reclaim = FALSE;
    args.state = 1;

    return lm_callrpc(clnt, NLMPROC4_LOCK, 
                      (xdrproc_t)xdr_nlm4_lockargs, 
                      (caddr_t)&args
                      );
}

int
nfs_unlock(CLIENT *clnt, fhandle3 *fh, int cookie)
{
    char owner[MAXPATHLEN];
    char hostname[MAXHOSTNAMELEN];
    nlm4_unlockargs args;
    nlm4_lock alk;
    pid_t pid;
    
    bzero(&args, sizeof(nlm4_unlockargs));

    if (gethostname(hostname, sizeof(hostname)) != 0) {
        log_error("Hostname not found(%d)", errno);
        return(-1);
    }
    if ((pid = getpid()) == -1) {
        log_error("Error getting pid(%d)", errno);
        return(-1);
    }
    
    snprintf(owner, sizeof(owner), "%s%d%d", hostname, pid, thr_self());
    
    alk.caller_name = hostname;
    alk.fh.n_len = fh->fhandle3_len;
    alk.fh.n_bytes = fh->fhandle3_val;
    alk.oh.n_len = strlen(owner) + 1;
    alk.oh.n_bytes = owner;
    alk.svid = pid;
    alk.l_offset = 0; 
    alk.l_len = 0;
    
    args.cookie.n_len = sizeof (cookie);
    args.cookie.n_bytes = (char *)&cookie;
    args.alock = alk;

    return lm_callrpc(clnt, NLMPROC4_UNLOCK,
                      (xdrproc_t)xdr_nlm4_unlockargs, 
                      (caddr_t)&args
                      );
}
