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



%#include "globals.h"

enum nfsstat3 { 
    NFS3_OK             = 0, 
    NFS3ERR_PERM        = 1, 
    NFS3ERR_NOENT       = 2, 
    NFS3ERR_IO          = 5, 
    NFS3ERR_NXIO        = 6, 
    NFS3ERR_ACCES       = 13, 
    NFS3ERR_EXIST       = 17, 
    NFS3ERR_XDEV        = 18, 
    NFS3ERR_NODEV       = 19, 
    NFS3ERR_NOTDIR      = 20, 
    NFS3ERR_ISDIR       = 21, 
    NFS3ERR_INVAL       = 22, 
    NFS3ERR_FBIG        = 27, 
    NFS3ERR_NOSPC       = 28, 
    NFS3ERR_ROFS        = 30, 
    NFS3ERR_MLINK       = 31, 
    NFS3ERR_NAMETOOLONG = 63, 
    NFS3ERR_NOTEMPTY    = 66, 
    NFS3ERR_DQUOT       = 69, 
    NFS3ERR_STALE       = 70, 
    NFS3ERR_REMOTE      = 71, 
    NFS3ERR_BADHANDLE   = 10001, 
    NFS3ERR_NOT_SYNC    = 10002, 
    NFS3ERR_BAD_COOKIE  = 10003, 
    NFS3ERR_NOTSUPP     = 10004, 
    NFS3ERR_TOOSMALL    = 10005, 
    NFS3ERR_SERVERFAULT = 10006, 
    NFS3ERR_BADTYPE     = 10007, 
    NFS3ERR_JUKEBOX     = 10008 
}; 

const NFS3_WRITEVERFSIZE=8;
const NFS3_COOKIEVERFSIZE=8;

/*
 * Basic types
 */

typedef unsigned long uint32; 
typedef unsigned hyper uint64; 
typedef uint32 mode3;
typedef uint32 uid3;
typedef uint32 gid3;
typedef uint64 size3;
typedef uint64 fileid3;
typedef uint64 offset3;
typedef uint32 count3; 
typedef opaque writeverf3[NFS3_WRITEVERFSIZE];
typedef uint64 cookie3; 
typedef opaque cookieverf3[NFS3_COOKIEVERFSIZE]; 

struct nfstime3 { 
    uint32   seconds; 
    uint32   nseconds; 
}; 

typedef string filename3<>;

struct diropargs3 { 
    fhandle3     dir; 
    filename3   name; 
}; 

enum createmode3 { 
    UNCHECKED = 0, 
    GUARDED   = 1, 
    EXCLUSIVE = 2 
}; 

/*
 * sattr3
 */

enum time_how { 
    DONT_CHANGE        = 0, 
    SET_TO_SERVER_TIME = 1, 
    SET_TO_CLIENT_TIME = 2 
}; 

union set_mode3 switch (bool set_it) { 
 case TRUE: 
     mode3    mode; 
 default: 
     void; 
}; 

union set_uid3 switch (bool set_it) { 
 case TRUE: 
     uid3     uid; 
 default: 
     void; 
}; 

union set_gid3 switch (bool set_it) { 
 case TRUE: 
     gid3     gid; 
 default: 
     void; 
}; 

union set_size3 switch (bool set_it) { 
 case TRUE: 
     size3    size; 
 default: 
     void; 
}; 

union set_atime switch (time_how set_it) { 
 case SET_TO_CLIENT_TIME: 
     nfstime3  atime; 
 default: 
     void; 
}; 

union set_mtime switch (time_how set_it) { 
 case SET_TO_CLIENT_TIME: 
     nfstime3  mtime; 
 default: 
     void; 
}; 

struct sattr3 { 
    set_mode3   mode; 
    set_uid3    uid; 
    set_gid3    gid; 
    set_size3   size; 
    set_atime   atime; 
    set_mtime   mtime;
}; 

/*
 * End of sattr3
 */

union createhow3 switch (createmode3 mode) { 
 case UNCHECKED: 
 case GUARDED: 
     sattr3       obj_attributes; 
}; 
struct CREATE3args { 
    diropargs3   where; 
    createhow3   how; 
}; 

union post_op_fh3 switch (bool handle_follows) { 
 case TRUE: 
     fhandle3 handle; 
 case FALSE: 
     void; 
}; 

enum ftype3 { 
    NF3REG    = 1, 
    NF3DIR    = 2, 
    NF3BLK    = 3, 
    NF3CHR    = 4, 
    NF3LNK    = 5, 
    NF3SOCK   = 6, 
    NF3FIFO   = 7 
}; 

struct specdata3 { 
    uint32     specdata1; 
    uint32     specdata2; 
}; 

struct fattr3 { 
    ftype3     type; 
    mode3      mode; 
    uint32     nlink; 
    uid3       uid; 
    gid3       gid; 
    size3      size; 
    size3      used; 
    specdata3  rdev; 
    uint64     fsid; 
    fileid3    fileid; 
    nfstime3   atime; 
    nfstime3   mtime; 
    nfstime3   ctime; 
}; 

union post_op_attr switch (bool attributes_follow) { 
 case TRUE: 
     fattr3   attributes; 
 case FALSE: 
     void; 
}; 

struct wcc_attr { 
    size3       size; 
    nfstime3    mtime; 
    nfstime3    ctime; 
}; 

union pre_op_attr switch (bool attributes_follow) { 
 case TRUE: 
     wcc_attr  attributes; 
 case FALSE: 
     void; 
}; 

struct wcc_data { 
    pre_op_attr    before; 
    post_op_attr   after; 
}; 

struct CREATE3resok { 
    post_op_fh3   obj; 
    post_op_attr  obj_attributes; 
    wcc_data      dir_wcc; 
}; 
struct CREATE3resfail { 
    wcc_data      dir_wcc; 
}; 
union CREATE3res switch (nfsstat3 status) { 
 case NFS3_OK: 
     CREATE3resok    resok; 
 default: 
     CREATE3resfail  resfail; 
};

struct COMMIT3args { 
    fhandle3   file; 
    offset3    offset; 
    count3     count; 
};
struct COMMIT3resok { 
    wcc_data   file_wcc; 
    writeverf3 verf; 
};
struct COMMIT3resfail { 
    wcc_data   file_wcc; 
}; 
union COMMIT3res switch (nfsstat3 status) { 
  case NFS3_OK: 
       COMMIT3resok   resok; 
  default: 
       COMMIT3resfail resfail; 
}; 

struct RENAME3args { 
    diropargs3   from; 
    diropargs3   to; 
}; 
struct RENAME3resok { 
    wcc_data     fromdir_wcc; 
    wcc_data     todir_wcc; 
}; 
struct RENAME3resfail { 
    wcc_data     fromdir_wcc; 
    wcc_data     todir_wcc; 
}; 
union RENAME3res switch (nfsstat3 status) { 
 case NFS3_OK: 
     RENAME3resok   resok; 
 default: 
     RENAME3resfail resfail; 
}; 

enum stable_how { 
    UNSTABLE  = 0, 
    DATA_SYNC = 1, 
    FILE_SYNC = 2 
}; 
struct WRITE3args { 
    fhandle3    file; 
    offset3     offset; 
    count3      count; 
    stable_how  stable; 
    opaque      data<>; 
}; 
struct WRITE3resok { 
    wcc_data    file_wcc; 
    count3      count; 
    stable_how  committed; 
    writeverf3  verf; 
}; 
struct WRITE3resfail { 
    wcc_data    file_wcc; 
}; 
union WRITE3res switch (nfsstat3 status) { 
 case NFS3_OK: 
     WRITE3resok    resok; 
 default: 
     WRITE3resfail  resfail; 
}; 

struct LOOKUP3args { 
    diropargs3  what; 
}; 

struct LOOKUP3resok { 
    fhandle3      object; 
    post_op_attr obj_attributes; 
    post_op_attr dir_attributes; 
}; 

struct LOOKUP3resfail { 
    post_op_attr dir_attributes; 
}; 

union LOOKUP3res switch (nfsstat3 status) { 
 case NFS3_OK: 
     LOOKUP3resok    resok; 
 default: 
     LOOKUP3resfail  resfail; 
}; 

struct READ3args { 
    fhandle3  file; 
    offset3  offset; 
    count3   count; 
}; 

struct READ3resok { 
    post_op_attr   file_attributes; 
    count3         count; 
    bool           eof; 
    opaque         data<>; 
}; 

struct READ3resfail { 
    post_op_attr   file_attributes; 
}; 

union READ3res switch (nfsstat3 status) { 
 case NFS3_OK: 
     READ3resok   resok; 
 default: 
     READ3resfail resfail; 
}; 

struct REMOVE3args {
	diropargs3 object;
};
typedef struct REMOVE3args REMOVE3args;

struct REMOVE3resok {
	wcc_data dir_wcc;
};
typedef struct REMOVE3resok REMOVE3resok;

struct REMOVE3resfail {
	wcc_data dir_wcc;
};
typedef struct REMOVE3resfail REMOVE3resfail;

union REMOVE3res switch (nfsstat3 status) { 
 case NFS3_OK: 
     REMOVE3resok   resok; 
 default: 
     REMOVE3resfail resfail; 
}; 

struct READDIR3args { 
    fhandle3     dir; 
    cookie3      cookie; 
    cookieverf3  cookieverf; 
    count3       count; 
}; 

struct entry3 { 
    fileid3      fileid; 
    filename3    name; 
    cookie3      cookie; 
    entry3       *nextentry; 
}; 
struct dirlist3 { 
    entry3       *entries; 
    bool         eof; 
}; 
struct READDIR3resok { 
    post_op_attr dir_attributes; 
    cookieverf3  cookieverf; 
    dirlist3     reply; 
}; 
struct READDIR3resfail { 
    post_op_attr dir_attributes; 
}; 
union READDIR3res switch (nfsstat3 status) { 
 case NFS3_OK: 
     READDIR3resok   resok; 
 default: 
     READDIR3resfail resfail; 
}; 
 
program NFS_PROGRAM { 
    version NFS_V3 { 
        LOOKUP3res NFSPROC3_LOOKUP(LOOKUP3args) = 3;
        READ3res NFSPROC3_READ(READ3args) = 6; 
        WRITE3res NFSPROC3_WRITE(WRITE3args) = 7; 
        CREATE3res NFSPROC3_CREATE(CREATE3args) = 8; 
        RENAME3res NFSPROC3_RENAME(RENAME3args) = 14; 
        REMOVE3res NFSPROC3_REMOVE(REMOVE3args) = 12;
        READDIR3res NFSPROC3_READDIR(READDIR3args) = 16;
        COMMIT3res NFSPROC3_COMMIT(COMMIT3args) = 21;
    } = 3;

} = 100003;

