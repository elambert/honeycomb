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
 * This file contains functions that parse and separate lines
 * (headers) from the buffer just read from the server. It handles the
 * case of headers that span multiple buffers. After the last header
 * is read (blank line) a pointer is returned to the request body.
 */

#include <multiload.h>

static char* get_lines(char* lines[], int maxlines, int* num_headers,
                       char* buffer, int nchars, int pos,
                       int* prev_frag_size);
static int handle_first_line(char* buffer, int bufsize, int* status);

/* This is the entry point. */
int headers_parse(char* buffer, size_t bufsize,
                  char* headerbuf[], int max_headers,
                  int* prev_frag_size, char** body,
                  int* num_headers, /* no. of headers already in headerbuf */
                  int* status)
{
    int pos = 0;

    if (*num_headers == 0) {
        /*
         * This is the first time through, so look for the HTTP status
         * line and set *status to one of REQ_OK, REQ_DUP, or
         * REQ_ERROR
         */
        int first_line_size = handle_first_line(buffer, bufsize, status);
        if (first_line_size < 0)
            return -first_line_size;

        pos = first_line_size;

        /* Make sure that get_lines will not try to incorporate all that */
        *prev_frag_size = 0;
    }

    *body = get_lines(headerbuf, max_headers, num_headers,
                      buffer, bufsize-pos, pos, prev_frag_size);

    return 0;
}

/* Returns the length of the first line, and sets the status */
static int handle_first_line(char* buffer, int bufsize, int* status)
{
    char* p;
    int http_status;

    char* q = strstr(buffer, "\r\n");
    if (q == 0 || (q - buffer) > bufsize) {
        msg("First line too long!");
        dump_buffer(buffer, bufsize, 0);
        return -3;
    }

    *q = 0; q += 2;
    if (loglevel >= LOG_HTTPSTAT)
        msg("First line: <%s>", buffer);

    if (strncasecmp(buffer, "http/", 5) != 0) {
        msg("No status line received!");
        dump_buffer(buffer, bufsize, 0);
        return -3;
    }

    p = strchr(buffer, ' ');
    if (p == 0) {
        msg("Bad status line!");
        dump_buffer(buffer, bufsize, 0);
        return -3;
    }

    http_status = strtoul(p, 0, 10);
    if (*status == 0) {
        msg("Couldn't find status code");
        dump_buffer(buffer, bufsize, 0);
        return -3;
    }

    if (loglevel >= LOG_HTTPSTAT)
        msg("Status code = %d", http_status);

    switch (http_status) {
    case 200: *status = REQ_OK; break;
    case 404: *status = REQ_NOTFOUND; break;
    case 409: *status = REQ_DUP; break;
    default:  *status = REQ_ERROR; break;
    }

    return q - buffer;
}

/* 
 * Split the buffer into headers (line terminator is "\r\n") and
 * body. Headers are returned in the array; pointer to the body is the
 * return value. If we're still in the middle of reading headers, it
 * returns NULL. N.B. the previous fragment is to the left of buffer.
 */
static char* get_lines(char* lines[], int maxlines, int* num_headers,
                       char* buffer, int nchars, int pos, int* prev_frag_size)
{
    char* q;
    char* buf = buffer + pos;

    /* Incorporate the previous fragment, but only if we're starting
       at the beginning of the line */
    if (pos == 0) {
        buf = buffer - *prev_frag_size;
        nchars += *prev_frag_size;

        if (loglevel >= LOG_BUFFERS) {
            msg("After including prev fragment (%d):", *prev_frag_size);
            dump_buffer(buf, nchars, 0);
        }
    }

    while (*num_headers < maxlines) {
        q = strstr(buf, "\r\n");

        if (q == 0) {
            /*
             * This is a partial line, don't use it. Move the
             * leftover characters to just left of buffer
             */
            if (loglevel >= LOG_BUFFERS) {
                msg("Unprocessed fragment (%d chars):", nchars);
                dump_buffer(buf, nchars, 0);
            }
            memcpy(buffer - nchars, buf, nchars);
            *prev_frag_size = nchars;
            return NULL;
        }

        if ((q - buf) > MAX_HEADER_SIZE) {
            msg("Header too long!");
            dump_buffer(buf, nchars, 0);
            fflush(stderr);

            /* Leave that string for the caller to handle */
            return buf;
        }

        *q = 0;
        lines[*num_headers] = buf;

        if (loglevel >= LOG_LINES)
            msg("Line <%s>", buf);

        if (q == buf)
            /* Empty header, i.e. end of headers */
            return q + 2;

        (*num_headers)++;
        q += 2;

        nchars -= q - buf;
        buf = q;
    }

    /* Too many headers, so return the remaining un-processed chars */
    return buf;
}
