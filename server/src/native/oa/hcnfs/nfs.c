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

#include "nfs.h"
#include "nfs_rpc.h"
#include "fhdb.h"
#include "globals.h"
#include "handle_repos.h"

#include <sys/time.h>


static enum clnt_stat
myclnt_call(int nodeid, 
            int diskid,
            CLIENT **clnt, 
            rpcproc_t proc,
            xdrproc_t args, 
            caddr_t vargs, 
            xdrproc_t res, 
            caddr_t vres,
            struct timeval itimeout)
{
    enum clnt_stat stat;
    int retry;
    struct timeval timeout;
    
    for (retry = 0, timeout = itimeout; 
         retry < HCRPC_MAXRETRIES; 
         retry++, timeout.tv_sec *= 2) 
    {
        struct rpc_err err;
        
        stat = clnt_call(*clnt, proc, args, vargs, res, vres, timeout);                
        if (stat == RPC_SUCCESS) {
            break;
        }
        if (!hrep_check_disk(nodeid, diskid)) {
            break;
        }
        clnt_geterr(*clnt, &err);
        log_error("RPC failed(%d) [status %d, errno %d, terrno %d]", 
                  stat,
                  err.re_status, 
                  err.re_errno,
                  err.re_terrno);
        
        CLIENT *nclnt = create_nfs_connection(nodeid, "tcp");
        if (nclnt == NULL) {
            log_error("failed to create tcp connection for node %d", nodeid);
            stat = RPC_SYSTEMERROR;
            break;
        }
        destroy_connection(*clnt);
        *clnt = nclnt;
    }
    return stat;
}
             
int
nfs_create(int nodeid,
           int diskid,
           CLIENT **clnt,
           fhdb_data_t *db,
           int mapid,
           char *filename,
           fhandle3 *_filehandle)
{
    char fhdata[FHSIZE3];
    fhandle3 filehandle;
    int error = 0;
    fhdb_key_t key;
    CREATE3args args;
    CREATE3res res;

    _filehandle->fhandle3_len = 0;
    filehandle.fhandle3_val = fhdata;

    key.nodeid = (short)nodeid;
    key.diskid = (short)diskid;
    key.mapid = mapid;

    fhdb_get(db, &key, &filehandle); 
    if (filehandle.fhandle3_len == 0) {
        log_error("Failed to get the filehandle(%d,%d)", nodeid, diskid);
        return(1);
    }

    bzero(&args, sizeof(CREATE3args));
    bzero(&res, sizeof(CREATE3res));
    
    args.where.dir = filehandle;
    args.where.name = filename;
    /* args.how.mode = GUARDED; */
    args.how.mode = UNCHECKED;
    args.how.createhow3_u.obj_attributes.mode.set_it = 1;;
    args.how.createhow3_u.obj_attributes.mode.set_mode3_u.mode = S_IRUSR | S_IWUSR;
    args.how.createhow3_u.obj_attributes.uid.set_it = 1;
    args.how.createhow3_u.obj_attributes.uid.set_uid3_u.uid = 0;
    args.how.createhow3_u.obj_attributes.gid.set_it = 1;
    args.how.createhow3_u.obj_attributes.gid.set_gid3_u.gid = 0;

    enum clnt_stat stat = myclnt_call(nodeid, diskid, clnt, NFSPROC3_CREATE,
                                    (xdrproc_t) xdr_CREATE3args, (caddr_t)&args,
                                    (xdrproc_t) xdr_CREATE3res, (caddr_t)&res,
                                    HCRPC_TIMEOUT);

    if (stat != RPC_SUCCESS) {
        log_error("RPC call failed [%d]", stat);
        return(1);
    }

    if (res.status != NFS3_OK) {
        log_error("NFSPROC3_CREATE failed [%d]", res.status);
        error = res.status;
    } else {
        fhandle3 resfh = res.CREATE3res_u.resok.obj.post_op_fh3_u.handle;
        _filehandle->fhandle3_len = resfh.fhandle3_len;
        bcopy(resfh.fhandle3_val, _filehandle->fhandle3_val, resfh.fhandle3_len);
    }
    
    xdr_free((xdrproc_t)xdr_CREATE3res, (caddr_t)&res);
    
    return(error);
}

int
nfs_delete(int nodeid,
           int diskid,
           CLIENT **clnt,
           fhdb_data_t *db,
           int mapid,
           char *filename)
{
    char fh[FHSIZE3];
    REMOVE3args args;
    REMOVE3res res;
    fhdb_key_t key;
    
    bzero(&args, sizeof(REMOVE3args));
    bzero(&res, sizeof(REMOVE3res));
    
    key.nodeid = nodeid;
    key.diskid = diskid;
    key.mapid = mapid;
    
    args.object.dir.fhandle3_val = fh;
    args.object.name = filename;
    
    fhdb_get(db, &key, &args.object.dir); 
    if (args.object.dir.fhandle3_len == 0) {
        log_error("Couldn't get the filehandler(%d, %d)", nodeid, diskid);
        return(1);
    }
    
    enum clnt_stat stat = myclnt_call(nodeid, diskid, clnt, NFSPROC3_REMOVE,
                                    (xdrproc_t)xdr_REMOVE3args, (caddr_t)&args,
                                    (xdrproc_t)xdr_REMOVE3res, (caddr_t)&res,
                                    HCRPC_TIMEOUT);
    
    if (stat != RPC_SUCCESS) {
        log_error("RPC call failed(%d)", stat);
        return(1);
    }
    
    int error = 0;
    if (res.status != NFS3_OK) {
        if (res.status != NFS3ERR_NOENT) {
            log_error("The server failed to remove [%d]", res.status);
            error = res.status;
        } else {
            log_error("Race condition during delete - %s", filename);
        }
    }
    
    xdr_free((xdrproc_t)xdr_REMOVE3res, (caddr_t)&res);
    return(error);
}

int
nfs_rename(int nodeid,
           int diskid,
           CLIENT **clnt,
           fhdb_data_t *db,
           int from_mapid,
           char *from_filename,
           int to_mapid,
           char *to_filename)
{
    char fromfh[FHSIZE3];
    char tofh[FHSIZE3];
    RENAME3args args;
    RENAME3res res;
    fhdb_key_t key;

    bzero(&args, sizeof(RENAME3args));
    bzero(&res, sizeof(RENAME3res));

    key.nodeid = nodeid;
    key.diskid = diskid;
    key.mapid = from_mapid;

    args.from.dir.fhandle3_val = fromfh;
    args.from.name = from_filename;

    fhdb_get(db, &key, &args.from.dir); 
    if (args.from.dir.fhandle3_len == 0) {
        log_error("Couldn't get the from filehandler(%d, %d)", nodeid, diskid);
        return(1);
    }

    key.mapid = to_mapid;
    args.to.dir.fhandle3_val = tofh;
    args.to.name = to_filename;

    fhdb_get(db, &key, &args.to.dir); 
    if (args.to.dir.fhandle3_len == 0) {
        log_error("Couldn't get the to filehandler(%d,%d)", nodeid, diskid);
        return(1);
    }

    enum clnt_stat stat = myclnt_call(nodeid, diskid, clnt, NFSPROC3_RENAME,
                                      (xdrproc_t)xdr_RENAME3args, (caddr_t)&args,
                                      (xdrproc_t)xdr_RENAME3res, (caddr_t)&res,
                                      HCRPC_LONG_TIMEOUT);

    if (stat != RPC_SUCCESS) {
        log_error("RPC call failed(%d)", stat);
        return(1);
    }

    int error = 0;
    if (res.status == NFS3ERR_NOENT) {
        /*
         * The source file does not exist anymore -
         * It can happen that the rpc client lib timed out but
         * the server operation actually suceeded.
         * Check if the dest file exist before returning an error.
         */
        char fh[FHSIZE3];
        fhandle3 hdl;
        unsigned long sz;
        
        hdl.fhandle3_len = sizeof(fh);
        hdl.fhandle3_val = fh;
        
        sz = nfs_lookup(nodeid, diskid, clnt, &args.to.dir, &hdl, to_filename);
        if (sz == -1 || sz == -2) {
            /* the dest file does not exist or an error occured */
            error = res.status;
        } else {
            log_error("Race condition during rename for %s", to_filename);
        }
    } else if (res.status != NFS3_OK) {
        error = res.status;
    }

    xdr_free((xdrproc_t)xdr_RENAME3res, (caddr_t)&res);
    
    if (error != 0) {
        log_error("The server failed to rename [%d]", error);
    }
    return(error);
}

int
nfs_commit(int nodeid, int diskid, CLIENT **clnt, fhandle3 *filehandle)
{
    COMMIT3args args;
    COMMIT3res res;
    
    bzero(&args, sizeof(COMMIT3args));
    bzero(&res, sizeof(COMMIT3res));
           
    args.file.fhandle3_len = filehandle->fhandle3_len;
    args.file.fhandle3_val = filehandle->fhandle3_val;
    args.offset = 0;
    args.count = 0;
    
    enum clnt_stat stat = myclnt_call(nodeid, diskid, clnt, NFSPROC3_COMMIT,
                                    (xdrproc_t)xdr_COMMIT3args, (caddr_t)&args,
                                    (xdrproc_t)xdr_COMMIT3res, (caddr_t)&res,
                                    HCRPC_TIMEOUT);
    if (stat != RPC_SUCCESS) {
        log_error("RPC call failed(%d)", stat);
        return(1);
    }
    
    int error = 0;
    if (res.status != NFS3_OK) {
        log_error("The server failed to commit file [%d]", res.status);
        error = res.status;
    }
    
    xdr_free((xdrproc_t)xdr_COMMIT3res, (caddr_t)&res);
    return(error);
}

int
nfs_write(int nodeid,
          int diskid,
          CLIENT **clnt,
          fhandle3 *filehandle,
          offset3 offset,
          int data_length,
          char *data,
          int synchronous)
{
    WRITE3args args;
    WRITE3res res;
    
    enum clnt_stat stat;
    
    bzero(&args, sizeof(WRITE3args));
    bzero(&res, sizeof(WRITE3res));
    
    args.file.fhandle3_len = filehandle->fhandle3_len;
    args.file.fhandle3_val = filehandle->fhandle3_val;
    args.offset = offset;
    args.count = data_length;
    args.stable = (synchronous)? DATA_SYNC : UNSTABLE;
    args.data.data_len = data_length;
    args.data.data_val = data;
    
    stat = myclnt_call(nodeid, diskid, clnt, NFSPROC3_WRITE,
                       (xdrproc_t)xdr_WRITE3args, (caddr_t)&args,
                       (xdrproc_t)xdr_WRITE3res, (caddr_t)&res,
                       ((synchronous)? HCRPC_LONG_TIMEOUT : HCRPC_TIMEOUT));
    
    
    if (stat != RPC_SUCCESS) {
        log_error("RPC call failed [%d]", stat);
        return(-1);
    }

    int result;
    if (res.status != NFS3_OK) {
        log_error("The server failed to write [%d]", res.status);
        result = -2;
    } else {
        result = res.WRITE3res_u.resok.count;
    }

    xdr_free((xdrproc_t)xdr_WRITE3res, (caddr_t)&res);
    return(result);
}

unsigned long
nfs_lookup(int nodeid,
           int diskid,
           CLIENT **clnt,
           fhandle3 *dirfilehandle,
           fhandle3 *filehandle,
           char *filename)
{
    LOOKUP3args args;
    LOOKUP3res res;

    bzero(&args, sizeof(LOOKUP3args));
    bzero(&res, sizeof(LOOKUP3res));

    args.what.dir = *dirfilehandle;
    args.what.name = filename;

    enum clnt_stat stat = myclnt_call(nodeid, diskid, clnt, NFSPROC3_LOOKUP,
                                      (xdrproc_t)xdr_LOOKUP3args, (caddr_t)&args,
                                      (xdrproc_t)xdr_LOOKUP3res, (caddr_t)&res,
                                      HCRPC_TIMEOUT);

    if (stat != RPC_SUCCESS) {
        log_error("RPC call failed [%d]", stat);
        return(1);
    }

    unsigned long result;
    if (res.status != NFS3_OK) {
        if (res.status != NFS3ERR_NOENT) {
            log_error("The server failed to lookup [%d]", res.status);
            result = -2;
        } else {
            result = -1;
        }
    } else {
        fhandle3 *fh = &res.LOOKUP3res_u.resok.object;
        filehandle->fhandle3_len = fh->fhandle3_len;
        bcopy(fh->fhandle3_val, filehandle->fhandle3_val, fh->fhandle3_len);
        result = res.LOOKUP3res_u.resok.obj_attributes.post_op_attr_u.attributes.size;
    }
    
    xdr_free((xdrproc_t)xdr_LOOKUP3res, (caddr_t)&res);
    return(result);
}

int
nfs_read(int nodeid,
         int diskid,
         CLIENT **clnt,
         fhandle3 filehandle,
         unsigned long offset,
         int length,
         char *buffer)
{
    READ3args args;
    READ3res res;

    int eof = 0;
    int error = 0;
    int result = 0;
    enum clnt_stat stat;
    
    while ((!eof) && (!error) && (result<length)) {

        bzero(&args, sizeof(READ3args));
        bzero(&res, sizeof(READ3res));

        args.file = filehandle;
        args.offset = offset+result;
        args.count = length-result;

        res.READ3res_u.resok.data.data_val = buffer+result;

        stat = myclnt_call(nodeid, diskid, clnt, NFSPROC3_READ,
                         (xdrproc_t)xdr_READ3args, (caddr_t)&args,
                         (xdrproc_t)xdr_READ3res, (caddr_t)&res,
                         HCRPC_TIMEOUT);
        
        if (stat != RPC_SUCCESS) {
            log_error("RPC call failed(%d)", stat);
            error = 1;
        } else {
            if (res.status != NFS3_OK) {
                log_error("The server failed to read [%d]", res.status);
                error = 1;
            } else {
                eof = res.READ3res_u.resok.eof ? 1 : 0;
                result += res.READ3res_u.resok.data.data_len;
            }
            
            res.READ3res_u.resok.data.data_val = NULL;
            xdr_free((xdrproc_t)xdr_READ3res, (caddr_t)&res);
        }
    }
    
    if (error)
        return(-2);

    if ((eof) && (result == 0))
        return(-1);

    return(result);
}

void
nfs_readdir(CLIENT *clnt,
            fhandle3 filehandle,
            nfs_readdir_callback callback,
            void *cookie)
{
    int eof = 0;
    enum clnt_stat stat;
    READDIR3args args;
    READDIR3res res;
    cookie3 nfs_cookie = 0;
    int has_to_free_res = 0;

    bzero(&args, sizeof(READDIR3args));

    while (!eof) {
        bzero(&res, sizeof(READDIR3res));

        args.dir = filehandle;
        args.cookie = nfs_cookie;
        args.count = 0x1000; // 4k
        has_to_free_res = 0;

        stat = clnt_call(clnt, NFSPROC3_READDIR,
                         (xdrproc_t)xdr_READDIR3args, (caddr_t)&args,
                         (xdrproc_t)xdr_READDIR3res, (caddr_t)&res,
                         HCRPC_TIMEOUT);

        bzero(&args, sizeof(READDIR3args));

        if (stat != RPC_SUCCESS) {
            log_error("RPC call failed(%d)", stat);
            eof = 1;
        } else {
            has_to_free_res = 1;
            bcopy(res.READDIR3res_u.resok.cookieverf, args.cookieverf, sizeof(cookieverf3));
            if (res.status != NFS3_OK) {
                log_error("The server failed to readdir [%d]", res.status);
                eof = 1;
            }
        }

        struct entry3 *entry = res.READDIR3res_u.resok.reply.entries;

        while ((!eof) && (entry)) {
            if (!eof) {
                nfs_cookie = entry->cookie;
                eof = callback(cookie, entry->name);
                entry = entry->nextentry;
            }
        }

        if (has_to_free_res) {
            if (!eof)
                eof = res.READDIR3res_u.resok.reply.eof;
            xdr_free((xdrproc_t)xdr_READDIR3res, (caddr_t)&res);
        }
    }
}
