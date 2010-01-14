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
#include <unistd.h>
#include <assert.h>
#include <string.h>
#include <sys/wait.h>

#include "nfs.h"
#include "nlm.h"
#include "mount.h"
#include "fhdb.h"
#include "globals.h"

static int
_test_write(int nodeid, int diskid,
            fhandle3 *fh)
{
    CLIENT *clnt = create_nfs_connection(nodeid, "tcp");
    if (!clnt) {
        fprintf(stderr, "create_nfs_connection failed\n");
        return(1);
    }
    int nbytes = nfs_write(nodeid, diskid, &clnt, fh, 0, 4, "foo", 0);
    if (nbytes != 4) {
        fprintf(stderr, "write failed\n");
        destroy_connection(clnt);
        return(-1);
    }
    destroy_connection(clnt);
    return 0;
}

static int
_test_read(int nodeid, int diskid,
           fhandle3 *fh)
{
    CLIENT *clnt = create_nfs_connection(nodeid, "tcp");
    if (!clnt) {
        fprintf(stderr, "create_nfs_connection failed\n");
        return(1);
    }
    char result[4];
    int nbytes = nfs_read(nodeid, diskid, &clnt, *fh, 0, 4, result);
    if ((nbytes != 4) || (strncmp("foo", result, 3) != 0)) {
        fprintf(stderr, "read failed\n");
        destroy_connection(clnt);
        return(-1);
    }
    destroy_connection(clnt);
    return 0;
}

static int
_create_file(int nodeid,
             int diskid,
             int mapid,
             char *filename,
             fhandle3 *fh)
{
    CLIENT *clnt = NULL;
    fhdb_data_t *db = NULL;
    
    db = fhdb_init(NULL, 1);
    
    clnt = create_nfs_connection(nodeid, "tcp");
    if (!clnt) {
        fprintf(stderr, "create_nfs_connection failed\n");
        return(1);
    }
    
    if (get_handle(nodeid, diskid, mapid, fh)) {
        fprintf(stderr, "get_handle failed\n");
        return(1);
    }
    
    fhdb_key_t key;
    key.nodeid = nodeid;
    key.diskid = diskid;
    key.mapid = mapid;
    
    fhdb_set(db, &key, fh);
    
    int error = nfs_create(nodeid, diskid, &clnt, db, mapid, filename, fh);
    if (error) {
        fprintf(stderr, "Failed to create file [%d]\n",
                error);
    } else {
        printf("File has been created\n");
        print_fh(-1, 0, 0, fh);
    }
    destroy_connection(clnt);
    clnt = NULL;
    fhdb_destroy(db);
    
    return(error);
}

int
main(int argc, char *argv[])
{
    fhandle3 filehandle;
    char fhdata[FHSIZE3];
    if (argc != 5) {
        printf("Usage: nodeid diskid mapid filename\n");
        return(1);
    }
    int nodeid = atoi(argv[1]);
    int diskid = atoi(argv[2]);
    int mapid = atoi(argv[3]);
    char *filename = argv[4];
    filehandle.fhandle3_len = 0;
    filehandle.fhandle3_val = fhdata;
    CLIENT *clnt = NULL;

    int error = _create_file(nodeid, diskid, mapid, filename, &filehandle);
    if (error) {
        return(error);
    }
    clnt = create_nlm_connection(nodeid, "tcp");
    if (!clnt) {
        fprintf(stderr, "create_lm_connection failed\n");
        return(1);
    }
    error = nfs_trylock(clnt, &filehandle, 1);
    if (error) {
        fprintf(stderr, "Failed to lock file [%d]\n", error);
        destroy_connection(clnt);
        return(error);
    }
    printf("File has been locked\n");
    error = _test_write(nodeid, diskid, &filehandle);
    if (!error) {
        error = _test_read(nodeid, diskid, &filehandle);
    }
    if (error) {
        destroy_connection(clnt);
        return(error);
    }

    /* should be able to lock file again, 
       from same host, process etc. */
    error = nfs_trylock(clnt, &filehandle, 1);
    if (error) {
        fprintf(stderr, "Unable to lock file (2) [%d]\n", error);
        destroy_connection(clnt);
        return(error);
    }
    /* try to lock file again from a different process */
    pid_t pid = vfork();
    int child_retval;
    if (pid < 0) {
        fprintf(stderr, "failed to fork\n");
        destroy_connection(clnt);
        return(1);
    } else if (pid == 0) {
        /* lock from child process */
        int expected_error = nfs_trylock(clnt, &filehandle, 1);
        if (!expected_error) {
            fprintf(stderr, "Error: locked file from child\n");
            _exit(1);
        }
        assert(expected_error == NLM4_DENIED);
        fprintf(stderr, "failed to lock file from child (expected)\n");
        /* try to write */
        expected_error = _test_write(nodeid, diskid, &filehandle);
        /* NFS3 provides advisory lock only. Other
           processes can write to a file that is already
           locked. */
        if (expected_error) {
            fprintf(stderr, "Error: Failed to write\n");
            _exit(2);
        }
        fprintf(stderr, "Wrote to file from child (as expected)\n");
        /* try to unlock */
        error = nfs_unlock(clnt, &filehandle, 1);
        if (error) {
            fprintf(stderr, "Error: failed to unlocked file from child\n");
            _exit(1);
        }
        /* this looks counter-intuitive, but nfs server allows
           unlocking from a different process */
        fprintf(stderr, "Unlocked file from child (expected)\n");
        _exit(0);
    }
    wait(&child_retval);
    if (WEXITSTATUS(child_retval) != 0) {
        return WEXITSTATUS(child_retval);
    }

    /* try to unlock file (already unlocked from child) */
    error = nfs_unlock(clnt, &filehandle, 1);
    if (error) {
        fprintf(stderr, "Error: failed to unlocked file\n");
        destroy_connection(clnt);
        return error;
    }

    /* lock and unlock from a child */
    pid = vfork();
    if (pid < 0) {
        fprintf(stderr, "failed to fork\n");
        destroy_connection(clnt);
        return(1);
    } else if (pid == 0) {
        /* lock from child process */
        error = nfs_trylock(clnt, &filehandle, 1);
        if (!error) {
            error = nfs_unlock(clnt, &filehandle, 1);
        }
        if (error) {
            fprintf(stderr, "Error: lock or unlock failed from child\n");
            _exit(1);
        }
        fprintf(stderr, "lock and unlock from child succeeded\n");
        _exit(0);
    }
    wait(&child_retval);
    if (WEXITSTATUS(child_retval) != 0) {
        return WEXITSTATUS(child_retval);
    }

    /* lock again */
    error = nfs_trylock(clnt, &filehandle, 1);
    if (!error) {
        error = nfs_unlock(clnt, &filehandle, 1);
    }
    if (error) {
        fprintf(stderr, "Error: lock or unlock (2nd time) failed\n");
        _exit(1);
    }
    fprintf(stderr, "lock and unlock (2nd time) succeeded\n");
    destroy_connection(clnt);
    return error;
}

