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
#include <string.h>

#include "database.h"

static void
usage()
{
    printf("dump -f file_to_dump [-h]\n\
\t-f file_to_dump\tspecifies the file to be checked and dumped\n\
\t-h\t\tto get help\n\
");
}

static void
dump(db_handle_t *handle)
{
    db_record_t record[2];
    int cur = 0;
    int err = 0;
    int line = 0;

    err = db_get_next(handle, record+cur);
    if (err == 1) {
        line++;
        printf("%s\n", record[cur].oid);
    }

    while (err == 1) {
        cur = 1-cur;
        err = db_get_next(handle, record+cur);
        if (err == 1) {
            line++;
            printf("%s\n", record[cur].oid);
            if (strncmp(record[1-cur].oid,
                        record[cur].oid, 36) > 0) {
                fprintf(stderr, "Order mismatch l.%d [%s] [%s]\n",
                        line, record[1-cur].oid, record[cur].oid);
            }
        }
    }
}

int
main(int argc,
     char *argv[])
{
    char c;
    char *filename = NULL;
    int err;
    db_handle_t handle;

    while ((c=getopt(argc, argv, "hf:")) != EOF) {
        switch (c) {
        case 'h':
            usage();
            return(0);

        case 'f':
            filename = optarg;
            break;
            
        default:
            usage();
            return(1);
        }
    }

    if (!filename) {
        printf("You have to specify a file to dump\n");
        usage();
        return(1);
    }

    err = database_init(&handle, ".", filename, 0);
    if (err) {
        printf("Failed to open the database %s [%d]\n",
               filename, err);
        return(1);
    }

    dump(&handle);

    database_close(&handle);

    return(0);
}
