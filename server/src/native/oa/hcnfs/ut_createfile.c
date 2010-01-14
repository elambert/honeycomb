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


#include <stdlib.h>

#include "nfs.h"
#include "fhdb.h"
#include "mount.h"
#include "globals.h"

int
main(int argc,
     char *argv[])
{
    CLIENT *clnt = NULL;

    if (argc != 5) {
        printf("Usage: nodeid diskid mapid filename\n");
        return(1);
    }

    int nodeid = atoi(argv[1]);
    int diskid = atoi(argv[2]);
    int mapid = atoi(argv[3]);
    fhdb_data_t *db = NULL;

    fhandle3 filehandle;
    char fhdata[FHSIZE3];
    filehandle.fhandle3_len = 0;
    filehandle.fhandle3_val = fhdata;

    clnt = create_nfs_connection(nodeid, "tcp");
    if (!clnt) {
        fprintf(stderr, "create_nfs_connection failed\n");
        return(1);
    }

    db = fhdb_init(NULL, 1);

    if (get_handle(nodeid, diskid, mapid, &filehandle)) {
        fprintf(stderr, "get_handle failed\n");
        return(1);
    }

    fhdb_key_t key;
    key.nodeid = nodeid;
    key.diskid = diskid;
    key.mapid = mapid;
    
    fhdb_set(db, &key, &filehandle);

    int error = nfs_create(nodeid, diskid, &clnt, db, mapid, argv[4], &filehandle);
    if (error) {
        fprintf(stderr, "Failed to create file [%d]\n", error);
    } else {
        printf("File has been created\n");
        print_fh(-1, 0, 0, &filehandle);
    }

    destroy_connection(clnt);
    clnt = NULL;

    fhdb_destroy(db);

    return(error);
}
