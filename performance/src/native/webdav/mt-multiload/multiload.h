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
#include <stdarg.h>
#include <string.h>
#include <strings.h>
#include <ctype.h>
#include <time.h>
#include <assert.h>
#include <netdb.h>
#include <unistd.h>
#include <fcntl.h>
#include <strings.h>
#include <limits.h>
#include <signal.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <sys/errno.h>
#include <netinet/in.h>

#ifdef SunOS
#include <sys/ksynch.h>
#endif

#include <util.h>

#define TMPSIZE            5000 /* for temp arrays etc. */
#define RAND_STATE_SIZE       8 /* see random(3C) */

#define HTTP_PORT          8080

#define OP_GET                1
#define OP_PUT                2
#define DFL_OP           OP_PUT

#define MAX_THREADS         500

#define MAX_HEADERS          20 /* Max. no. of headers returned in a reply */
#define MAX_HEADER_SIZE     512 /* Largest header that we might receive */


#define MAX_SEED_SIZE       500

#ifdef TESTING
/* Set the limits low to test if the "buffering" actually works */
#undef BUFSIZ
#define BUFSIZ              150
#undef MAX_HEADER_SIZE
#define MAX_HEADER_SIZE     100
#endif

/* The size of the OID (external format) as a hex string */
#define OID_LENGTH           60

/* pattern types */
#define ROSTER                1
#define RANGE                 2

/* Possible outcomes from a connection attempt */
#define REQ_OK                1
#define REQ_DUP               2
#define REQ_NOTFOUND          3
#define REQ_ERROR             4
#define REQ_OID_MISMATCH      5

/* Logging: how many "-v"s are required for a given kind of message */
#define LOG_ERRORS            0 /* always logged */
#define LOG_SUMMARY           1 /* summary line with OID, URL, and svc time */
#define LOG_PARAMS            1 /* parameters to the program */
#define LOG_THREADS           1 /* thread start/stop */
#define LOG_URL               2
#define LOG_DOCMD             2 /* doc metadata */
#define LOG_REQ_START_END     2 /* start/end of each request */
#define LOG_HEADERS           2
#define LOG_HTTPSTAT          2 /* HTTP status */
#define LOG_LINES             3 /* lines extracted from read buffers */
#define LOG_MISMATCH          3 /* OID mismatch */
#define LOG_FDS               4 /* file descriptors */
#define LOG_READS             4 /* everything read from the socket */
#define LOG_WRITES            4 /* everything written to the socket */
#define LOG_BUFFERS           5 /* partial buffers */
#define LOG_PRNS              5 /* PRN = pseudo-random number */
#define LOG_BODY              6

/* Exit status */
#define ES_OK                 0
#define ES_USAGE              1
#define ES_NETWORK            2
#define ES_RESOURCE           3
#define ES_REMOTE             4
#define ES_INTERNAL           5

/* State space size for the random variable controlling the file contents */
#define BODY_STATESPACE_SIZE  1000

#ifndef MAXHOSTNAMELEN
#define MAXHOSTNAMELEN 100
#endif

#ifndef PTHREADS
#include <thread.h>
#else
/* Redefine all Solaris thr_* functions with pthread_* functions */
#include <pthread.h>
#define thread_t pthread_t

#define thr_create(stack, stksize, startfunc, arg, flags, id) \
    pthread_create(id, NULL, startfunc, arg)
#define thr_join(id, d, stat)   pthread_join(id, stat)
#define thr_kill(t, sig)        pthread_kill(t, sig)
#define thr_self()              pthread_self()
#define thr_exit(s)             pthread_exit(s)

#define mutex_t pthread_mutex_t

#define mutex_lock pthread_mutex_lock
#define mutex_unlock pthread_mutex_unlock
#define mutex_init pthread_mutex_init
#endif /* PTHREADS */

typedef struct _patternElem {
    const char* fmt;
    int type;                   /* ROSTER or RANGE */
    int num_values;
    int next_val;
    union _v {
        int *values;            /* ROSTER */
        int start;              /* RANGE */
    } vals;
} patt_t;

typedef struct _url {
    const char* server;
    int port;
    struct sockaddr_in addr;
    const char* fmt;
    int num_patterns;
    patt_t* patterns;
    const char* raw_string;
} url_t;

/* Globals */
extern const char* progname;
extern int method;
extern int errno;
extern int loglevel;
extern int sequential;
extern FILE* oid_file;

extern int exit_on_failure;
extern int failure_encountered;

extern mutex_t prng_lock;

/* Public functions */

extern url_t* pattern_parse(const char* s);
extern int pattern_nextpath(url_t* url, char* buffer, size_t buf_size, int* i);
extern void task_init(url_t* url, int file_length, const char* op, long t);
extern void* task_run(void*);
extern void record_result(int result_type, double elapsed_time,
                          const char* oid, int serial);

extern
int headers_parse(char* buffer, size_t bufsize,
                  char* headerbuf[], int headerbuf_size,
                  int* prev_frag_size, char** body, int* num_headers,
                  int* status);

extern unsigned int self();

