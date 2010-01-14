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

const MNTNAMLEN  = 255;   /* Maximum bytes in a name */ 

typedef string dirpath<MNTPATHLEN>; 
typedef string name<MNTNAMLEN>;

enum mountstat3 { 
    MNT3_OK = 0,                 /* no error */ 
    MNT3ERR_PERM = 1,            /* Not owner */ 
    MNT3ERR_NOENT = 2,           /* No such file or directory */ 
    MNT3ERR_IO = 5,              /* I/O error */ 
    MNT3ERR_ACCES = 13,          /* Permission denied */ 
    MNT3ERR_NOTDIR = 20,         /* Not a directory */ 
    MNT3ERR_INVAL = 22,          /* Invalid argument */ 
    MNT3ERR_NAMETOOLONG = 63,    /* Filename too long */ 
    MNT3ERR_NOTSUPP = 10004,     /* Operation not supported */ 
    MNT3ERR_SERVERFAULT = 10006  /* A failure on the server */ 
}; 

struct mountres3_ok { 
    fhandle3   fhandle; 
    int        auth_flavors<>; 
}; 
union mountres3 switch (mountstat3 fhs_status) { 
 case MNT3_OK: 
     mountres3_ok  mountinfo; 
 default: 
     void; 
}; 

program MOUNT_PROGRAM { 
    version MOUNT_V3 { 
        void      MOUNTPROC3_NULL(void)    = 0; 
        mountres3 MOUNTPROC3_MNT(dirpath)  = 1; 
    } = 3; 
} = 100005;
