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
#include <pthread.h>
#include <semaphore.h>
#include <thread.h>
#include <synch.h>

#include "mount.h"
#include "fhdb.h"

#define NUM_THREADS 8

typedef void (*callback_t)(int nodeid,
                           int diskid,
                           int error);

static struct thread_data_t {
    enum {
        SLEEPING, WORKING, STOPPED, FAILED
    } state;
    pthread_t thread;
    fhdb_data_t *db;
    int nodeid;
    int diskid;
    callback_t callback;
    sem_t lock;
} threads[NUM_THREADS];

static sem_t free_threads;

static int pending;
static mutex_t main_lock;
static cond_t main_cond;

static void*
thread_main(void *arg) {
    struct thread_data_t *data = (struct thread_data_t*)arg;

    while (data->state != STOPPED) {
        data->state = SLEEPING;
        sem_post(&free_threads);

        if (sem_wait(&data->lock)) {
            fprintf(stderr, "sem_wait failed");
            data->callback = NULL;
            data->state = FAILED;
            return(NULL);
        }
        
        if (data->state == WORKING) {
            int error = 0;
            error = mount_disk(data->db, data->nodeid, data->diskid);
            if (data->callback) {
                data->callback(data->nodeid, data->diskid, error);
            }
        } else if (data->state != STOPPED) {
            printf("Was not in the WORKING state ???\n");
        }
    }

    return(NULL);
}

static void
print_result(int nodeid,
             int diskid,
             int error)
{
    if (error) {
        fprintf(stderr, "Failed to mount %d:%d [%d]\n",
                nodeid, diskid, error);
    } else {
        fprintf(stderr, "Disk %d:%d has been mounted\n",
                nodeid, diskid);
    }
    
    mutex_lock(&main_lock);
    pending--;
    cond_signal(&main_cond);
    mutex_unlock(&main_lock);
}

static void
add_to_queue(int nodeid,
             int diskid, 
             callback_t callback)
{
    int t = -1;
    sem_wait(&free_threads);
    for (t=0; t<NUM_THREADS; t++) {
        if (threads[t].state == SLEEPING) {
            break;
        }
    }
    if (t==NUM_THREADS) {
        printf("No free threads ...\n");
    } else {
        threads[t].state = WORKING;
        threads[t].nodeid = nodeid;
        threads[t].diskid = diskid;
        threads[t].callback = callback;
        sem_post(&threads[t].lock);
    }
}

static int
mount_all_disks(fhdb_data_t *db,
                int nb_nodes,
                int nb_disks)
{
    /*
     * Initialization
     */

    pending = 0;
    mutex_init(&main_lock, USYNC_THREAD, NULL);
    cond_init(&main_cond, USYNC_THREAD, NULL);

    sem_init(&free_threads, 0, 0);

    for (int i=0; i<NUM_THREADS; i++) {
        threads[i].state = SLEEPING;
        threads[i].callback = NULL;
        threads[i].db = db;
        sem_init(&threads[i].lock, 0, 0);
        pthread_create(&threads[i].thread, NULL,
                       thread_main, &threads[i]);
    }

    /*
     * Main loop
     */
    
    for (int j=0; j<nb_disks; j++) {
        for (int i=1; i<=nb_nodes; i++) {
            mutex_lock(&main_lock);
            pending++;
            mutex_unlock(&main_lock);
            add_to_queue(i, j, print_result);
        }
    }

    /*
     * Wait for completion
     */

    mutex_lock(&main_lock);
    while (pending>0) {
        cond_wait(&main_cond, &main_lock);
    }
    mutex_unlock(&main_lock);

    mutex_destroy(&main_lock);
    cond_destroy(&main_cond);
    sem_destroy(&free_threads);

    for (int i=0; i<NUM_THREADS; i++) {
        threads[i].state = STOPPED;
        sem_post(&threads[i].lock);
        pthread_join(threads[i].thread, NULL);
        
        sem_destroy(&threads[i].lock);
    }
    
    return(0);
}

int
main(int argc,
     char *argv[])
{
    fhdb_data_t *db = NULL;
    db = fhdb_init(NULL, 1);

    if (mount_all_disks(db, 8, 4)) {
        fprintf(stderr, "mount_all_disks failed\n");
        return(1);
    }
    
    fhdb_key_t key;
    char fhdata[FHSIZE3];
    fhandle3 filehandle;
    filehandle.fhandle3_val = fhdata;

    key.nodeid = 2;
    key.diskid = 2;
    key.mapid = 500;

    fhdb_get(db, &key, &filehandle);
    printf("%d\n", filehandle.fhandle3_len);

    fhdb_destroy(db);

    return(0);
}
