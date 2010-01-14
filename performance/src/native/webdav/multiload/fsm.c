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

static int file_len;
static char* data_buf;
static const char* method;

static int writeConn(url_t* url, conn_t* conn);
static int readConn(conn_t* conn, char* buffer, size_t buf_size);
static int initBuf();
static int makeRequest(url_t*, const char* path, char* buffer, size_t bsize);

static char* get_lines(conn_t* conn, char* lines[],
                       char* buffer, int buf_size);
static int handle_buffer(conn_t* conn, char* buffer, int buf_size);
static void handle_header(conn_t* conn, char* header);

static void error(conn_t* conn, const char* msg);
static void dump(char* s);

static char* state_names[] = {
    "closed", "ready", "sending request", "sent request",
    "sending data", "sent data", "reading headers", "receiving data"
};

static char* rc_names[] = {
    "-", "OK", "dup", "error", "notfound"
};

int fsm_init(int file_length, int op)
{
    file_len = file_length;
    switch (op) {
    case OP_PUT: method = "PUT"; break;
    case OP_GET: method = "GET"; break;
    default:
        fprintf(stderr, "Unknown op %d\n", op);
        return 4;
    }
    return initBuf();
}

/* Run a transition of the FSM */
int fsm_step(url_t* url, pfd_t* pfd)
{
    int rc1 = 0, rc2 = 0;
    char buffer[BUFSIZ];        /* To pass in to readConn */

    conn_t* conn = conn_get(pfd_getfd(pfd));

    if (pfd->revents & POLLOUT)
        rc1 = writeConn(url, conn);

    if (pfd->revents & POLLIN)
        rc2 = readConn(conn, buffer, sizeof(buffer));

    if (verbose > 2)
        fprintf(stderr, "Stepped conn #%d -> %s\n", conn->index,
                state_names[conn->state]);

    if (rc2 == ST_OK && out_file != 0)
        fprintf(out_file, "%s %s\n", conn->last_etag, conn->last_path);

    if (rc2 == ST_OK || rc2 == ST_DUP) {
        conn_reset(conn, (struct sockaddr*) &url->addr);
    }

    if (rc1 == ST_ERROR || rc2 == ST_ERROR) {
        if (verbose) {
            char buf[100];
            snprintf(buf, sizeof(buf),
                     "FSM error {r:%s w:%s}", rc_names[rc2], rc_names[rc1]);
            error(conn, buf);
        }
        conn_reset(conn, (struct sockaddr*) &url->addr);
    }

    if (rc1 != ST_OK && rc1 != ST_CONTINUE)
        return rc1;
    return rc2;
}

static void error(conn_t* conn, const char* msg)
{
    char time_buf[30];
    char err_buf[100];
    time_t now = time(0);

    *err_buf = 0;
    if (errno != 0)
        strerror_r(errno, err_buf, sizeof(err_buf));
    cftime(time_buf, "%F %T %Z", &now);

    fprintf(stderr, "%s C.%d: [%s] \"%s\" (%ld) -- %s\n",
            time_buf, conn->index, err_buf,
            state_names[conn->state], conn->n_written,
            msg);

    errno = 0;
}

/* Write to connection */
static int writeConn(url_t* url, conn_t* conn) 
{
    char buffer[BUFSIZ];
    char request[BUFSIZ];
    int to_write, nwritten;
    int req_len, rc;

    if (verbose)
        fprintf(stderr, "can write conn #%d [%s]\n", conn->index,
                state_names[conn->state]);

    switch (conn->state) {
    case CLOSED:
        fprintf(stderr, "Can't run FSM on a closed connection!\n");
        return ST_ERROR;

    case CONN_READY:
        /* Send the next file */

        if ((rc = pattern_nextpath(url, conn, buffer, sizeof(buffer))) != 0) {
            fprintf(stderr, "Couldn't get next path!\n");
            return ST_ERROR;
        }
        conn->last_path = newstr(buffer);
        conn->start_time = now();

        req_len = makeRequest(url, conn->last_path, request, sizeof(request));

        if (verbose > 2)
            fprintf(stderr, "Sending request:\n%s<<< end request >>>\n\n",
                    request);

        nwritten = write(conn->pfd->fd, request, req_len);
        if (nwritten < 0) {
            error(conn, "write request");
            return ST_ERROR;
        }

        conn->n_written = nwritten;

        if (nwritten >= req_len) {
            conn->state = SENT_REQUEST;
            if (verbose > 1)
                fprintf(stderr, "Wrote %ld byte request\n", conn->n_written);
        }
        else
            conn->state = SENDING_REQUEST;

        break;
 
    case SENDING_REQUEST:
        /*
          A partial write of the request is not likely, so this code
          (to re-generate the request) is not executed very
          often. Better to keep it simple and error-free than to save
          partial requests etc.
         */
        req_len = makeRequest(url, conn->last_path, request, sizeof(request));
        to_write = req_len - conn->n_written;

        nwritten = write(conn->pfd->fd, request + conn->n_written, to_write);
        if (nwritten < 0) {
            error(conn, "write request");
            return ST_ERROR;
        }

        conn->n_written += nwritten;

        if (conn->n_written >= req_len) {
            conn->state = SENT_REQUEST;
            if (verbose > 1)
                fprintf(stderr, "cWrote %ld byte request\n", conn->n_written);
        }
        /* Otherwise it continues in state SENDING_REQUEST */

        break;

    case SENT_REQUEST:
        /* Send data */

        nwritten = write(conn->pfd->fd, data_buf, file_len);
        if (nwritten < 0) {
            error(conn, "write");
            return ST_ERROR;
        }

        conn->n_written = nwritten;

        if (nwritten >= file_len) {
            if (verbose > 1)
                fprintf(stderr, "Conn %d wrote %ld bytes of data to %d\n",
                        conn->index, conn->n_written, conn->pfd->fd);
            conn->state = SENT_DATA;
        }
        else
            conn->state = SENDING_DATA;

        break;

    case SENDING_DATA:
        to_write = file_len - conn->n_written;
        nwritten = write(conn->pfd->fd, data_buf + conn->n_written, to_write);
        if (nwritten < 0) {
            error(conn, "write");
            return ST_ERROR;
        }

        conn->n_written += nwritten;

        if (conn->n_written >= file_len) {
            if (verbose > 1)
                fprintf(stderr, "cConn %d wrote %ld bytes of data to %d\n",
                        conn->index, conn->n_written, conn->pfd->fd);
            conn->state = SENT_DATA;
        }
        /* Otherwise it continues in state SENDING_DATA */

        break;

    case SENT_DATA:
        /* We're not interested in writing to this any more */
        shutdown(conn->pfd->fd, SHUT_WR);
        conn_readonly(conn);
        break;

    default:
        error(conn, "spurious state");

    }

    return ST_CONTINUE;
} 

/* Read from a connection */
static int readConn(conn_t* conn, char* buffer, size_t buf_size) 
{
    int nread;

    if (verbose)
        fprintf(stderr, "Reading conn #%d [%s]\n", conn->index,
                state_names[conn->state]);

    nread = read(conn->pfd->fd, buffer, buf_size-1);
    if (nread < 0) {
        if (errno == EWOULDBLOCK)
            return ST_CONTINUE;

        error(conn, "first read");
        return ST_ERROR;
    }
    buffer[nread] = '\0';

    if (nread == 0)
        /* EOF: reset everything. (Should conn->local_buffer be processed?) */
        return ST_OK;

    if (conn->state == SENT_DATA) {
        char* first_line = buffer;

        conn->state = READING_HEADERS;
        conn->local_buffer[0] = '\0';

        if ((buffer = strstr(buffer, "\r\n")) == NULL) {
            fprintf(stderr, "Couldn't find status line!");
            return ST_ERROR;
        }
        buffer += 2;
        nread -= buffer - first_line;
        
        if (strstr(first_line, "HTTP/1.1 200 OK") == NULL) {
            if (strstr(first_line, "HTTP/1.1 404 ") != NULL)
                return ST_404;

            if (strstr(first_line, "already+exists") != NULL)
                return ST_DUP;

            fputs("\nGot bad reply! ", stderr);
            dump(buffer);
            fputs("\n", stderr);
            return ST_ERROR;
        }
    }

    return handle_buffer(conn, buffer, nread);
}

/* Handle input by separating into lines and calling handle_header for each */
static int handle_buffer(conn_t* conn, char* buffer, int buf_size)
{
    int i;
    char* lines[MAX_HEADERS];
    char* body;

    char first_line[MAX_HEADER_SIZE];

    switch (conn->state) {

    case READING_HEADERS:

        /* Set up lines[0] with the remnant from the previous read */
        strncpy(first_line, conn->local_buffer, sizeof(first_line));
        lines[0] = first_line;

        /* Split into lines (saving fragment in conn->local_buffer) */
        body = get_lines(conn, lines, buffer, buf_size);

        for (i = 0; i < MAX_HEADERS; i++)
            handle_header(conn, lines[i]);
        
        if (body == NULL)
            /* Done for now; we're still reading headers */
            return ST_CONTINUE;

        /* Otherwise, body points to the body of the reply */

        if (verbose > 1)
            fputs("End Of Headers\n", stderr);

        conn->state = RECEIVING_DATA;
        buf_size = body - buffer;
        buffer = body;
        /* FALLTHROUGH */

    case RECEIVING_DATA:
        /*
         * In the future, we'll actually be testing the returned
         * object to make sure the data is correct. (And the code
         * needs to handle the "chunked" transfer encoding.) For now,
         * we just discard it.
         */
        if (verbose > 3)
            fprintf(stderr, "%s", buffer);
        break;

    default:
        fprintf(stderr, "Fatal error -- bad state %s\n",
                state_names[conn->state]);
        break;
    }

    return ST_CONTINUE;
}

/* 
 * Split the buffer into headers (line terminator is "\r\n") and
 * body. Headers are returned in the array; pointer to the body is the
 * return value. If we're still in the middle of reading headers, it
 * returns NULL.
 */
static char* get_lines(conn_t* conn, char* lines[],
                       char* buffer, int buf_size)
{
    char* retval = NULL;
    int header_index;

    /*
     * The first header has to be treated specially: it starts with a
     * fragment from the previous read; this fragment is already in
     * lines[0].
     */
    char* p = strchr(lines[0], 0);

    char* q = strstr(buffer, "\r\n");
    if (q == 0 || ((p - lines[0]) + (q - buffer)) > MAX_HEADER_SIZE) {
        /* Crap! Header too long? */
        fprintf(stderr, "Header too long! \"%s\"\n", buffer);
        fflush(stderr);
        return (char*) 0xdeadbeef; /* Make it crash! */
    }

    memcpy(p, buffer, q - buffer);
    p[q - buffer] = 0;
    q += 2;                     /* Skip the \r\n */

    buf_size -= q - buffer;
    buffer = q;

    header_index = 1;
    while (header_index < MAX_HEADERS) {
        lines[header_index++] = buffer;
        q = strstr(buffer, "\r\n");

        /* Don't accept a result beyond the extent of the buffer */
        if ((q - buffer) > buf_size)
            q = 0;

        if (q == 0) {
            // This is a partial line, don't use it
            lines[header_index-1] = 0;

            /* Save leftovers into conn->local_buffer */
            if (buf_size + 1 > sizeof(conn->local_buffer)) {
                fprintf(stderr, "(partial) header too long (%d)\n",
                                sizeof(conn->local_buffer));
                fflush(stderr);
                buf_size = sizeof(conn->local_buffer) - 1;
            }
            memcpy(conn->local_buffer, buffer, buf_size);
            conn->local_buffer[buf_size] = 0;
            break;              /* retval is NULL */
        }

        if ((q - buffer) > MAX_HEADER_SIZE) {
            fprintf(stderr, "Header too long! \"%s\"\n", buffer);
            fflush(stderr);
            retval = buffer;
            break;
        }

        *q = 0;

        if (q == buffer) {
            /* End of headers */
            retval = q + 2;
            break;
        }

        *q = 0;
        q += 2;

        buf_size -= q - buffer;
        buffer = q;
    }

    for (; header_index < MAX_HEADERS; header_index++)
        lines[header_index] = NULL;

    return retval;
}

/* Split a line into name+value and handle it */
static void handle_header(conn_t* conn, char* header)
{
    char* p;
    char* name;
    char* value;

    if (header == NULL || *header == 0)
        return;

    name = header;
    while (isspace(*name))
        name++;

    if ((value = strchr(header, ':')) == NULL) {
        fprintf(stderr, "Bad header \"%s\"\n", header);
        return;
    }
    *value++ = 0;

    while (isspace(*value))
        value++;

    p = strchr(name, 0);
    p--; while(isspace(*p)) p--;

    if (verbose)
        fprintf(stderr, "Header \"%s\" = \"%s\"\n", name, value);

    /* The only header currently handled is ETag */
    if (strcasecmp(name, "etag") == 0)
        conn->last_etag = value;
}

static int initBuf()
{
    if ((data_buf = malloc(file_len)) == NULL) {
        perror("Couldn't allocate data buffer");
        return 3;
    }
    memset(data_buf, 'a', file_len);
    return 0;
}

static int makeRequest(url_t* url, const char* path,
                        char* buffer, size_t bsize)
{
    return snprintf(buffer, bsize,
                    "%s %s HTTP/1.0\r\n"
                    "Host: %s\r\n"
                    "Content-length: %d\r\n"
                    "Content-type: application/octet-stream\r\n"
                    "\r\n",
                    method, path, url->server, file_len);
}

static void dump(char* s)
{
    if (s == NULL) {
        fputs("(null)\n", stderr);
        return;
    }

    fputs("\"", stderr);

    while (*s != 0) {
        switch (*s) {
        case '\r': fputs("\\r", stderr); break;
        case '\n': fputs("\\n", stderr); break;
        default: 
            if (*s >= ' ' || *s < 0x7f)
                fputc(*s, stderr);
            else
                fprintf(stderr, "\\x%02x", *s);
            break;
        }
        s++;
    }
    fputs("\"\n", stderr);
}
