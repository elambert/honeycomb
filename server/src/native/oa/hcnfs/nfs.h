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



#ifndef _NFS_H_
#define _NFS_H_

#include "globals.h"
#include "nfs_rpc.h"
#include "fhdb.h"

extern int
nfs_create(int nodeid,
           int diskid,
           CLIENT **clnt,
           fhdb_data_t *db,
           int mapid,
           char *filename,
           fhandle3 *_filehandle);

extern int
nfs_rename(int nodeid,
           int diskid,
           CLIENT **clnt,
           fhdb_data_t *db,
           int from_mapid,
           char *from_filename,
           int to_mapid,
           char *to_filename);

extern int 
nfs_commit(int nodeid,
           int diskid,
           CLIENT **clnt, 
           fhandle3 *filehandle);

extern int
nfs_delete(int nodeid,
           int diskid,
           CLIENT **clnt,
           fhdb_data_t *db,
           int mapid,
           char *filename);

extern int
nfs_write(int nodeid,
          int diskid,
          CLIENT **clnt,
          fhandle3 *filehandle,
          offset3 offset,
          int data_length,
          char *data,
          int synchronous);

extern unsigned long
nfs_lookup(int nodeid,
           int diskid,
           CLIENT **clnt,
           fhandle3 *dirfilehandle,
           fhandle3 *filehandle,
           char *filename);

extern int
nfs_read(int nodeid,
         int diskid,
         CLIENT **clnt,
         fhandle3 filehandle,
         unsigned long offset,
         int length,
         char *buffer);

/* Return something else than 0 will stop the operation */
typedef int (*nfs_readdir_callback)(void *cookie, char *filename);

extern void
nfs_readdir(CLIENT *clnt,
            fhandle3 filehandle,
            nfs_readdir_callback callback,
            void *cookie);

#endif /* _NFS_H_ */
