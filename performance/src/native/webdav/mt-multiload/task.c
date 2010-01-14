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



/*
 * This file contains functions that represent one thread of
 * execution. In its run loop (task_run()) a thread repeatedly opens a
 * connection to the server, generates a random URL, and executes the
 * requested operation (GET or PUT) on it.
 */

#include <multiload.h>

static url_t* url;
static int file_len;
static char* data_buf;
static const char* methodName;
static long duration;

typedef struct {
    char etag[MAX_HEADER_SIZE];
    int content_length;
} headers_t;

static int handle(int fd, double start_time);
static char* initBuf();
static int getConn(struct sockaddr *addr);

static int makeRequest(url_t* url, const char* path,
                       char* buffer, size_t bsize);

static void process_header(char* line, headers_t* doc);

static char* req_time(char* buf, size_t bufsize, double start_time);

void task_init(url_t* u, int file_length, const char* op, long t)
{
    url = u;
    file_len = file_length;
    methodName = op;
    duration = t;

    if (strcmp(methodName, "PUT") == 0)
        data_buf = initBuf();
    else
        data_buf = 0;
}

/* Repeatedly open a connection, perform the operation, close */
void* task_run(void* arg)
{
    long current_time, end_time;
    int rc, fd;
    double start_time;

    if (loglevel >= LOG_THREADS)
        msg("Thread starting");

    current_time = now();
    end_time = current_time + duration;

    while (current_time < end_time) {
        start_time = ptime();
        if (loglevel >= LOG_REQ_START_END)
            msg("@%.3f New connection...", start_time);

        if ((fd = getConn((struct sockaddr*)&url->addr)) > 0) {
            if ((rc = handle(fd, start_time)) != 0)
                msg("Error 0x%x (%d)", rc, rc);
            close(fd);
        }

        current_time = now();

        if (exit_on_failure && failure_encountered)
            break;
    }

    if (loglevel >= LOG_THREADS)
        msg("Thread exit.");

    thr_exit(0);
}

/*
 * The workhorse: generate the next random URL and send a request to
 * the server; then read the reply, separate into lines, parse headers
 * etc. Finally record the outcome -- success, error, or dup -- by
 * calling record_result in multiload.c.
 */
static int handle(int fd, double start_time)
{
    int nread, nwritten, num_headers, processed_headers, total_read;
    int req_size, prev_frag_size;
    int i, rc = -1, status = REQ_ERROR, body_offset, serial;
    char buffer[BUFSIZ + MAX_HEADER_SIZE], *read_buffer;
    char path[BUFSIZ];
    char* headers[MAX_HEADERS];
    char* body;
    headers_t doc_headers;

    /* Per-request ms-precision timestamps */
    char tag_buf[MAX_HEADER_SIZE];
#define req_ts() req_time(tag_buf, sizeof(tag_buf), start_time)

    if (loglevel >= LOG_FDS)
        msg("+%s Handling fd %d.", req_ts(), fd);

    /*
     * To handle PUTs: also get a random value at the same time the
     * URL is generated. The value is used as an offset into the data
     * buffer, which means that each thread doesn't always write the
     * same file contents with each request. By making sure the value
     * is obtained at the same time as the random URL, this process is
     * repeatable: a subsequent run of GETs will generate the same
     * sequence of URLs, each of which will have the same
     * pseudo-random body i.e. the file contents returned can also be
     * verified.
     */
    body_offset = pattern_nextpath(url, path, sizeof(path), &serial);

    if (loglevel >= LOG_URL)
        msg("+%s %d: %s", req_ts(), serial, path);

    if ((req_size = makeRequest(url, path, buffer, sizeof(buffer))) < 0)
        return 3;

    if (loglevel >= LOG_WRITES)
        dump_buffer(buffer, req_size, req_ts());

    nwritten = write(fd, buffer, req_size);
    if (method == OP_PUT)
        write(fd, data_buf + body_offset, file_len);

    num_headers = processed_headers = 0;
    memset(headers, sizeof(headers), 0);
    memset(&doc_headers, sizeof(doc_headers), 0);

    /*
     * Pay attention here: there's one header's worth of space
     * _before_ the read area, which is used for handling the partial
     * line at the end of the buffer. After the read, headers_parse
     * moves the fragment to the left, which means the next read will
     * automatically append to the previous fragment.
     */

    total_read = 0;
    read_buffer = buffer + MAX_HEADER_SIZE;
    prev_frag_size = 0;
    for (body = NULL; body == NULL; ) {
        if ((nread = read(fd, read_buffer, BUFSIZ-1)) < 0) {
            msg("+%s Error (after %ld bytes: \"%s\" (OID: <%s>)", req_ts(),
                    total_read, strerror(errno), doc_headers.etag);
            break;
        }

        read_buffer[nread] = 0;
        total_read += nread;

        if (loglevel >= LOG_READS)
            dump_buffer(read_buffer, nread, req_ts());

        /* collect headers, parse */
        rc = headers_parse(read_buffer, nread, headers, MAX_HEADERS,
                           &prev_frag_size, &body, &num_headers, &status);

        /*
         * Since headers are not malloc'ed but live in the buffer,
         * they must be processed here, not after the loop is
         * done.
         */

        if (status == REQ_OK) {
            /* Inspect new header values */
            for (i = processed_headers; i < num_headers; i++)
                process_header(headers[i], &doc_headers);
            processed_headers = num_headers;

        }

        /* Do NOT reset num_headers to 0! */
    }

    if (status == REQ_ERROR)
        failure_encountered = 1;

    if (loglevel >= LOG_DOCMD)
        msg("+%s Doc: length %d, OID = \"%s\"", req_ts(),
                doc_headers.content_length, doc_headers.etag);

    /* read data if GET */
    if (method == OP_GET) {
        int doc_read = nread - (body - read_buffer);
        while (doc_read < doc_headers.content_length) {
            if ((nread = read(fd, buffer, sizeof(buffer)-1)) < 0) {
                // Flag unexpected error
                if (loglevel >= LOG_ERRORS)
                    msg("+%s Error! %ld bytes (doc: %ld): \"%s\" (OID: <%s>)",
                        req_ts(), total_read, doc_read,
                        strerror(errno), doc_headers.etag);
                break;
            }
            if (nread == 0)
                /* EOF */
                break;

            buffer[nread] = 0;
            total_read += nread;
            doc_read += nread;

            if (loglevel >= LOG_READS)
                dump_buffer(read_buffer, nread, req_ts());
        }

        if (total_read < doc_headers.content_length && loglevel >= LOG_DOCMD)
            msg("+%s Only got %d bytes (expected %d)", req_ts(),
                doc_read, doc_headers.content_length);
    }

    if (loglevel >= LOG_SUMMARY)
        msg("@%.3f %d: DONE %s [%s] \"%s\"", ptime(), serial,
            req_ts(), doc_headers.etag, path);

    record_result(status, ptime() - start_time, doc_headers.etag, serial);
    return 0;
}

static char* req_time(char* buf, size_t bufsize, double start_time)
{
    snprintf(buf, bufsize, "%.3f", ptime() - start_time);
    return buf;
}

/*
 * Split a line into name+value and handle it: if it's Etag or
 * Content-length, parse and save the value. Note that this code
 * doesn't handle headers that are split into multiple lines.
 *
 * If the line begins with whitespace, it's a continuation of the
 * previous value. Since headers are processed in batches (one of the
 * design goals here is to minimize mallocs and buffer copies) it
 * probably means there needs to be a buffer for partial headers
 * etc. That gets complex, and is inappropriate -- this is a
 * high-performance Honeycomb test tool, not a general-purpose HTTP
 * client.
 */
static void process_header(char* line, headers_t* doc)
{
    char* p;
    char* name;
    char* value;

    if (line == NULL || *line == 0)
        return;

    name = line;

    /*
     * Note: if the line begins with whitespace, it's a continuation
     * of the previous header.
     */

    if ((value = strchr(line, ':')) == NULL) {
        msg("Bad line \"%s\"", line);
        return;
    }
    *value++ = 0;

    while (isspace(*value))
        value++;

    p = strchr(name, 0);
    p--; while(isspace(*p)) p--;

    if (loglevel >= LOG_HEADERS)
        msg("Header \"%s\" = \"%s\"", name, value);

    if (strcasecmp(name, "etag") == 0)
        strncpy(doc->etag, value, sizeof(doc->etag));
    else if (strcasecmp(name, "content-length") == 0)
        doc->content_length = strtoul(value, 0, 10);
}

static int getConn(struct sockaddr *addr)
{
    int fd;

    if ((fd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("socket");
        return -1;
    }

    if (connect(fd, addr, sizeof(struct sockaddr_in)) < 0) {
        perror("connect");
        close(fd);
        return -1;
    }

    return fd;
}

static char* initBuf()
{
    unsigned char* buf;
    int i, bufsize = file_len + BODY_STATESPACE_SIZE;

    if ((buf = malloc(bufsize)) == NULL) {
        perror("Couldn't allocate data buffer");
        return 0;
    }

    /* Get enough random values to fill the buffer */

    mutex_lock(&prng_lock);
    for (i = 0; i < bufsize; i++)
        buf[i] = get_rand_int(255);
    mutex_unlock(&prng_lock);

    return (char*) buf;
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
                    methodName, path, url->server, file_len);
}
