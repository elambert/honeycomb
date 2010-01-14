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
#include <pthread.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <semaphore.h>

#include "nfs.h"
#include "nlm.h"
#include "mount.h"
#include "fhdb.h"
#include "globals.h"


static sem_t sem;
static CLIENT *clnt = NULL;
static fhandle3 filehandle;
static int volatile try_locked = 0;
static int volatile locked = 0;

static void P() {
    int result = sem_wait(&sem);
    if (result != 0) {
	fprintf(stderr, "failed to lock semaphore errno = %d\n", errno);
	exit(-1);
    }
}

static void V() {
    int result = sem_post(&sem);
    if (result != 0) {
	fprintf(stderr, "failed to unlock semaphore errno = %d\n", errno);
	exit(-1);
    }
}

static void*
thread_start(void* arg)
{
    int result;

    printf("Thread %d starts\n",  pthread_self());

    P();

    printf("Thread %d will attempt to lock the file\n",  pthread_self());

    result = nfs_trylock(clnt, &filehandle, 1);
    // First thread should succeed
    if ((result == 0) &&
      (try_locked == 0)){
	printf("-> Thread %d locked the file [success]\n", pthread_self());
	try_locked++;
	locked = 1;
	// First thread should not fail   
    } else if ((result != 0) 
      && (try_locked == 0)) {
        fprintf(stderr, "Failed to lock file [%d] [failure]\n", result);
        destroy_connection(clnt);
	exit(1);
	// Second thread should fail with NLM4_DENIED
    } else if ((result == NLM4_DENIED) &&
      (try_locked == 1)) {
	printf("-> Thread %d got denied the lock [success]\n", pthread_self());
	try_locked++;
    } else if ((result == 0) &&
      (try_locked == 1)) {
        fprintf(stderr, "Succeeded to lock file [failure]\n");
        destroy_connection(clnt);
	exit(1);    
    } else {
        fprintf(stderr, "Thread %d got unexpected result %d, try_lock = %d\n",
	  pthread_self(), result, try_locked);
        destroy_connection(clnt);
	exit(1);	
    }


    printf("Thread %d will post and yield the processor\n",  pthread_self());

    V();

    while (try_locked < 2) {
	yield();
	printf("Thread %d yields the processor \n",  pthread_self());
    }

    printf("Thread %d resumes\n",  pthread_self());

    P();


    if (locked) {

	printf("Thread %d will attempt to unlock the file\n",  pthread_self());

	result = nfs_unlock(clnt, &filehandle, 1);
	if (result) {
	    fprintf(stderr, "Error unlocking the file [failure]\n");
	    destroy_connection(clnt);
	    exit(1);
	}
	locked = 0;
	printf("Thread %d unlocked the file [success]\n",  pthread_self());
    }

    printf("Thread %d will post and exit\n",  pthread_self());

    V();

    printf("Thread %d exits \n",  pthread_self());

}
 

static pthread_t
create_thread()
{
    pthread_attr_t attributes;
    pthread_t tid;
    int result;

    (void) pthread_attr_init(&attributes);

    result = pthread_attr_setscope(&attributes, PTHREAD_SCOPE_SYSTEM);
    if (result != 0) {
	fprintf(stderr, "failed to set thread attributes errno = %d\n",
	  errno);
	return -1;
    }

    result = pthread_create(&tid,
      &attributes, thread_start, (void *) NULL);
    if (result != 0) {
	fprintf(stderr, "failed to create thread  errno = %d\n",
	  errno);
	return -1;
    }
    printf("Created thread %d\n", tid);
    return tid;
}




static int
create_file(int nodeid,
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

//
// Simple test which creates two threads, each of which
// will sync up with its part to successively lock/unlock
// a fragment file.
//
int main(int argc, char *argv[])
{

    int result;
    pthread_t t1;
    pthread_t t2;
    int nodeid;
    int diskid;
    int mapid;
    char *filename = NULL;
    char fhdata[FHSIZE3];

    if (argc != 5) {
        printf("Usage: nodeid diskid mapid filename\n");
        return(1);
    }

    nodeid = atoi(argv[1]);
    diskid = atoi(argv[2]);
    mapid = atoi(argv[3]);
    filename = argv[4];

    filehandle.fhandle3_len = 0;
    filehandle.fhandle3_val = fhdata;

    result = create_file(nodeid, diskid, mapid, filename, &filehandle);
    if (result) {
        return result;
    }

    clnt = create_nlm_connection(nodeid, "tcp");
    if (clnt == NULL) {
        fprintf(stderr, "create_lm_connection failed\n");
        return 1;
    }

    result = sem_init(&sem, 0, 1);
    if (result) {
	fprintf(stderr, "failed to initialize semaphore errno = %d\n", errno);
	return 1;
    }
    
    printf("Start test pid = %d\n", getpid());

    t1 = create_thread();
    t2 = create_thread();
    if (t1 == -1 || t2 == -1) {
	fprintf(stderr, 
	  "failed to createthe threads...");
	return -1;
    }

    result = pthread_join(t1, NULL);
    if (result != 0) {
	fprintf(stderr,
	  "failed to wait for thread %d, error = %d\n", t1, result);
	return 1;
    }

    result = pthread_join(t2, NULL);
    if (result != 0) {
	fprintf(stderr,
	  "failed to wait for thread %d, error = %d\n", t2, result);
	return 1;
    }

    printf("Main thread returns...");
    return 0;
}

