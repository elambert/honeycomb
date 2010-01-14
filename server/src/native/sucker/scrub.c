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



#include "scrub.h"
#include "database.h"
#include <sys/types.h>
#include <dirent.h>
#include <stdlib.h>
#include <strings.h>

#include "progress.h"

static void *
start_scrub(void *cookie)
{
    scrub_data_t *data = (scrub_data_t*)cookie;
    int i;
    int j;
    char buffer[256];
    db_record_t template;
    DIR *dir = NULL;
    struct dirent *dir_entry;
    int nb_inserted = 0;
    char *token;
    char *lasts;

    bzero(&template, sizeof(db_record_t));
    template.node = data->node;
    template.disk = data->disk;

    for (i=0; i<100; i++) {
        for (j=0; j<100; j++) {
            template.map = (i*100)+j;
            
            snprintf(buffer, 256, "/data/%d/%02d/%02d",
                     data->disk, i, j);
            dir = opendir(buffer);
            if (!dir) {
                perror(buffer);
            } else {
                // Do the scrub
                while ((dir_entry = readdir(dir)) != NULL) {
                    if (dir_entry->d_name[0] != '.') {
                        token = strtok_r(dir_entry->d_name, "_", &lasts);
                        strcpy(template.oid, token);

                        token = strtok_r(NULL, "_", &lasts);
                        template.fragment = atoi(token);
                        if (db_insert_record(data->handle, &template)) {
                            fprintf(stderr, "Failed to insert oid %s\n",
                                    template.oid);
                        } else {
                            nb_inserted++;
                        }
                    }
                }
                
                closedir(dir);
                dir = NULL;
            }
        }
        progress_step();
    }

    return((void*)nb_inserted);
}

int
scrub_start(scrub_job_t *job,
            db_handle_t *handle,
            char node,
            char disk)
{
    int err;

    job->started = 0;
    job->cookie.handle = handle;
    job->cookie.node = node;
    job->cookie.disk = disk;

    err = pthread_create(&job->thread,
                         NULL,
                         start_scrub, &job->cookie);
    if (err) {
        fprintf(stderr, "Failed to spawn a scrub thread [%d]\n",
                err);
        return(err);
    }

    job->started = 1;

    return(0);
}

int
scrub_join(scrub_job_t *job)
{
    int err;
    void *status;

    if (!job->started) {
        return(0);
    }
    
    err = pthread_join(job->thread, &status);
    if (err) {
        fprintf(stderr, "pthread_join failed [%d]\n",
                err);
        job->started = 0;
        return(0);
    }
    
    return((int)status);
}
