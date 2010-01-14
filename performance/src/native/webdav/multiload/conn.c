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



#include <multiload.h>

static int n_conns;
static pfd_t* poll_array;
static conn_t* conn_array;

static conn_t* conn_map[TMPSIZE];

static char u_prng_state[RAND_STATE_SIZE];

static int initPfd(pfd_t* pfd, struct sockaddr *addr);

/* Initialize */
int conn_init(int num, unsigned int seed, struct sockaddr *addr)
{
    unsigned int i;

    n_conns = num;
    poll_array = (pfd_t*) malloc(num * sizeof(pfd_t));
    conn_array = (conn_t*) malloc(num * sizeof(conn_t));

    if (poll_array == 0 || conn_array == 0) {
        fprintf(stderr, "Couldn't allocate!\n");
        return 3;
    }

    memset(poll_array, 0, num * sizeof(pfd_t));
    memset(conn_array, 0, num * sizeof(conn_t));

    for (i = 0; i < num; i++) {
        if (initPfd(&poll_array[i], addr) != 0)
            return 0;
        conn_array[i].index = i;
        conn_array[i].pfd = &poll_array[i];
        conn_array[i].state = CONN_READY;
        initstate(seed ^ i, (char*) conn_array[i].rand_state,
                             sizeof(conn_array[i].rand_state));
        conn_set(pfd_getfd(conn_array[i].pfd), &conn_array[i]);
    }

    /* Initialise the unique PRNG and make it the default */
    initstate(seed, u_prng_state, sizeof(u_prng_state));
    setstate(u_prng_state);

    return 0;
}

/* Close fd and open a new one */
int conn_reset(conn_t* conn, struct sockaddr *addr)
{
    int rc;
    if (verbose > 1)
        fprintf(stderr, "Reset connection %d\n", conn->index);

    close(pfd_getfd(conn->pfd));
    conn_set(pfd_getfd(conn->pfd), 0);

    /* Start new connection */
    rc = initPfd(conn->pfd, addr);

    conn_set(pfd_getfd(conn->pfd), conn);
    conn->state = CONN_READY;
    conn->n_written = 0;

    return rc;
}

/* Set a connection to readonly */
int conn_readonly(conn_t* conn)
{
    if (verbose)
        fprintf(stderr, "Connection #%d going read-only\n", conn->index);
    conn->pfd->events = POLLIN;
    return 0;
}

/* Write all fds to /dev/poll and get the ready files */
int conn_getReadyFiles(pfd_t* buffer, long timeout)
{
    return pfd_getReadyFiles(poll_array, buffer, n_conns, timeout);
}

/* Fill in the sockaddr struct in the url with DNS lookup */
int conn_fillAddress(url_t* url)
{
    struct hostent* host;

    if ((host = gethostbyname(url->server)) == 0) {
        fprintf(stderr, "%s: no such host\n", url->server);
        return 2;
    }

    /* Initialize the destination address.*/
    memset((char *)&url->addr, 0, sizeof(url->addr));
    url->addr.sin_family = AF_INET;
    url->addr.sin_port = htons((u_short) url->port);
    memcpy(&url->addr.sin_addr, host->h_addr, host->h_length);

    return 0;
}

/* Return the connection that has this fd */
conn_t* conn_get(int fd)
{
    if (fd < 0 || fd >= TMPSIZE)
        return 0;

    if (verbose > 1)
        fprintf(stderr, "fd %d -> conn. #%d\n", fd, conn_map[fd]->index);

    return conn_map[fd];
}

/* Associate the connection with an fd */
void conn_set(int fd, conn_t* conn)
{
    if (fd < 0 || fd >= TMPSIZE)
        return;

    conn_map[fd] = conn;

    if (verbose > 1)
        fprintf(stderr, "fd %d <-> conn. #%d\n", fd, conn_map[fd]->index);
}

/* Return a number between 0 and range (inclusive) */
int conn_getrand(conn_t* conn, int range)
{
    /* Save the default PRNG's state */
    char* old_state = setstate(conn->rand_state);

    int i = get_rand_int(range);

    /* Restore the default PRNG */
    setstate(old_state);

    return i;
}

void conn_terminate()
{
    int i;
    if (verbose)
        fprintf(stderr, "Closing all connections\n");
    for (i = 0; i < n_conns; i++)
        (void) close(conn_array[i].pfd->fd);
}

/***********************************************************************/

static int initPfd(pfd_t* pfd, struct sockaddr *addr)
{
    int fd;

    if ((fd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("socket");
        return 3;
    }

    /* Set socket to non-blocking. */
    if (fcntl(fd, F_SETFL, O_NONBLOCK) < 0) {
        perror("fcntl");
        return 2;
    }

    if (connect(fd, addr, sizeof(struct sockaddr_in)) < 0 &&
            errno != EINPROGRESS) {
        perror("connect");
        return 2;
    }

    if (verbose > 1)
        fprintf(stderr, "New socket fd %d\n", fd);

    pfd_setfd(pfd, fd);

    return 0;
}

