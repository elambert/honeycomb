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

#include "globals.h"
#include "nfs.h"
#include "mount.h"

static void
usage()
{
    printf("Usage: nodeid diskid [mapid]\n\n");
}

static int
readdir_callback(void *cookie, char *filename)
{
    int *nb_nulls = (int*)cookie;

    if (filename) {
        printf("%s\n", filename);
    } else {
        printf("<NULL> [%d]\n", *nb_nulls);
        (*nb_nulls)++;
        if ((*nb_nulls) == 10) {
            return(1);
        }
    }
    return(0);
}

int
main(int argc, char *argv[])
{
    int nodeid = -1;
    int diskid = -1;
    int mapid = ROOTMAP;
    char filehandle_data[FHSIZE3];
    fhandle3 filehandle;
    filehandle.fhandle3_val = filehandle_data;
    int nb_nulls = 0;
    

    if ((argc<3) || (argc>4)) {
        usage();
        return(1);
    }

    nodeid = atoi(argv[1]);
    diskid = atoi(argv[2]);
    if (argc >= 4) {
        mapid = atoi(argv[3]);
    }

    printf("Listing from node %d, disk %d, map %d\n",
           nodeid, diskid, mapid);

    int err = get_handle(nodeid, diskid, mapid, &filehandle);
    if (err) {
        fprintf(stderr, "get_handle failed\n");
        return(1);
    }
    
    CLIENT *clnt = create_nfs_connection(nodeid, "tcp");
    if (!clnt) {
        fprintf(stderr, "create_nfs_connection failed\n");
        return(1);
    }
    
    nfs_readdir(clnt, filehandle, readdir_callback, &nb_nulls);

    clnt_destroy(clnt);
    clnt = NULL;

    return(0);
}
