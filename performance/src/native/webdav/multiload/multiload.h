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
#include <strings.h>
#include <ctype.h>
#include <time.h>
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
#include <sys/devpoll.h>

#include <util.h>

#define TMPSIZE            5000 /* for temp arrays etc. */
#define RAND_STATE_SIZE       8 /* see random(3c) */

#define HTTP_PORT          8080

#define OP_GET                1
#define OP_PUT                2
#define DFL_OP                OP_PUT

/* Max. no. of headers returned in a reply */
#define MAX_HEADERS          20
/* Largest header that we might receive */
#define MAX_HEADER_SIZE     512

/* connection states */
#define CLOSED                0
#define CONN_READY            1
#define SENDING_REQUEST       2
#define SENT_REQUEST          3
#define SENDING_DATA          4
#define SENT_DATA             5
#define READING_HEADERS       6
#define RECEIVING_DATA        7

#ifdef __sun__
typedef struct pollfd pfd_t;
#endif
#ifdef __linux__
typedef struct epoll_event pfd_t;
#endif

typedef struct _ConnectionInfo {
    int state;
    int index;
    pfd_t* pfd;
    long start_time;
    long n_written;
    const char* last_path;
    const char* last_etag;
    const char rand_state[RAND_STATE_SIZE];
    char local_buffer[80];      /* per-connection scratch space */
} conn_t;

/* pattern types */
#define ROSTER 1
#define RANGE  2

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
} url_t;

/* Return codes when a step of the FSM is run */
#define ST_CONTINUE 0
#define ST_OK       1
#define ST_DUP      2
#define ST_ERROR    3
#define ST_404      4

/* Globals */
extern const char* progname;
extern int errno;
extern int verbose;
extern int sequential;
extern int multi_PRNG;
extern FILE* out_file;

/* Public functions */

extern url_t* pattern_parse(const char* s);

extern int     conn_fillAddress(url_t* url);
extern int     conn_getReadyFiles(pfd_t* buf, long timeout);
extern int     conn_reset(conn_t* conn, struct sockaddr *addr);
extern conn_t* conn_get(int fd);
extern void    conn_set(int fd, conn_t* conn);
extern int     conn_init(int num, unsigned int seed, struct sockaddr *addr);
extern int     conn_readonly(conn_t* conn);
extern void    conn_terminate();
extern int     conn_getrand(conn_t* conn, int maxval);

extern int pattern_nextpath(url_t* url, conn_t* conn,
                            char* buffer, size_t buf_size);

extern int fsm_init(int file_length, int op);
extern int fsm_step(url_t* url, pfd_t*);

extern int  pfd_init(int nconns);
extern int  pfd_getfd(pfd_t* pfd);
extern void pfd_setfd(pfd_t* pfd, int fd);
extern int  pfd_getReadyFiles(pfd_t* fds, pfd_t* buf, int nconn, long timeout);

