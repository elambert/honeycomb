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
#include <strings.h>
#include <stdlib.h>
#include "trace.h"

#define SYSLOG_PREFIX "hc_native"
#define SYSLOG_FACILITY LOG_LOCAL1

static int initialized = 0;
static int use_syslog = 1;
static int log_level = LOG_ERR;
static FILE* log_file = stderr;

static void init()
{
    openlog(SYSLOG_PREFIX, 0, SYSLOG_FACILITY);
}

static void emit(int level, char* s) {
    if (use_syslog)
        syslog(level, s);

    else {
        fputs(s, log_file);
        fflush(log_file);
    }
}

void hc_log_msg(int level, const char* fmt,  ...)
{
    char buffer[MAX_TRACE_LEN];
    va_list ap;

    if (!initialized) init();

    if (level > log_level)
        return;

    bzero(buffer, sizeof(buffer));

    va_start(ap, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, ap);
    va_end(end);

    emit(level, buffer);
}

void set_logging_options(int l, FILE* f) {
    if (!initialized) init();

    log_level = l;
    log_file = f;

    use_syslog = log_file == NULL;
}

char* get_string(void* obj, size_t len)
{
    char* p;

    if (!initialized) init();

    if (obj == 0 || len < 0)
        return 0;

    if ((p = malloc(len + 1)) == 0)
        return p;

    memcpy(p, obj, len);
    p[len] = 0;

    return p;
}

/* For debug traces */
char* get_hex_string(void* obj, size_t len)
{
    int i;
    char* buffer;
    char* p;
    unsigned char* s = (unsigned char*) obj;

    if (!initialized) init();

    if (obj == 0 || len < 0)
        return 0;

    if ((buffer = malloc(2*len + 1)) == 0)
        return 0;

    for (i = 0, p = buffer; i < len; i++, p += 2)
        sprintf(p, "%02x", s[i]);

    return buffer;
}
