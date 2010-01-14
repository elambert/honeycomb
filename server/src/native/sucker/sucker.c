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
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <strings.h>

#include "database.h"
#include "scrub.h"
#include "progress.h"

static void
usage(char *prog)
{
    printf("Usage: %s [-n nodeid] [-d output_dir] [-q] [-h]\n\
\t-n nodeid\tspecified the node id where the program is running\n\
\t-d output_dir\tspecifies an output directory\n\
\t-q\t\tfor a quiet output\n\
\t-h\t\tfor help\n\
", prog);
}

static char
get_node_id()
{
    int fd = -1;
    char buf[32];
    int nbread;

    fd = open("/etc/nodename", O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "Cannot guess the nodeid [/etc/nodename does not exist\n");
        return(-1);
    }
    
    bzero(buf, sizeof(buf));
    nbread = read(fd, buf, sizeof(buf));
    close(fd);

    if (nbread < 6) {
        fprintf(stderr, "Cannot guess the nodeid from [%s]\n",
                buf);
        return(-1);
    }

    return(atoi(buf+3));
}

int
main(int argc,
     char *argv[])
{
    char output_dir[1024];
    char db_filename[32];
    int output_dir_set = 0;
    int quiet = 0;
    char nodeid = -1;
    char c;
    int err, i;
    db_handle_t db_handle;
    scrub_job_t jobs[4];
    int nb_inserted;

    while ((c=getopt(argc, argv, "n:d:qh")) != EOF) {
        switch (c) {
        case 'n':
            nodeid = atoi(optarg);
            break;

        case 'd':
            strcpy(output_dir, optarg);
            output_dir_set = 1;
            break;

        case 'q':
            quiet = 1;
            break;

        case 'h':
            usage(argv[0]);
            return(0);

        default:
            usage(argv[0]);
            return(1);
        }
    }

    if (nodeid == -1) {
        nodeid = get_node_id();
        if ((nodeid != -1) && (!quiet)) {
            printf("I guess that the local node id is %d\n",
                   nodeid);
        }
    }

    if (nodeid == -1) {
        fprintf(stderr, "You have to specify the nodeid\n");
        usage(argv[0]);
        return(1);
    }

    snprintf(db_filename, sizeof(db_filename), "hcb%d.db", nodeid);

    if (!output_dir_set) {
        getcwd(output_dir, sizeof(output_dir));
    }

    if (!quiet) {
        printf("The output directory is %s\n",
               output_dir);
    }

    err = database_init(&db_handle, output_dir, db_filename, 1);
    if (err) {
        fprintf(stderr, "database_init failed [%d]\n", err);
        return(1);
    }

    if (!quiet) {
        printf("The BDB database has been initialized\n");
    }

    if (!quiet) {
        progress_init(4*100);
    } else {
        progress_init(-1);
    }

    for (i=0; i<4; i++) {
        err = scrub_start(jobs+i, &db_handle, 
                          nodeid, i);
        if (err) {
            fprintf(stderr, "Failed to start the scrub job on disk %d\n",
                    i);
        }
    }
    
    nb_inserted = 0;
    for (i=0; i<4; i++) {
        nb_inserted += scrub_join(jobs+i);
    }

    if (!quiet) {
        progress_destroy();
    }

    if (!quiet) {
        printf("All the disks have been scrubbed. Syncing the DB to disk\n");
    }

    database_close(&db_handle);
    
    if (!quiet){
        printf("%d fragments have been inserted.\n",
               nb_inserted);
    }
    
    return(0);
}
