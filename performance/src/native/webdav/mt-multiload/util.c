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

#define DUMP_LINELEN 60

static long start_time;

void util_init()
{
    start_time = now(); 
}

time_t now() {
    time_t t;
    time(&t);
    return t;
}

/* Program time -- program start is t=0 */
double ptime()
{
    struct timeval current_time;
    gettimeofday(&current_time, 0);
    return (current_time.tv_sec - start_time) + current_time.tv_usec/1000000.0;
}

long min(long a, long b)
{
    if (a < b)
        return a;
    return b;
}

int getint(char opt, const char* s)
{
    char* endp = 0;
    int v = strtoul(s, &endp, 10);
    if (!*endp)
        return v;

    fprintf(stderr,
            "Option -%c expects numeric; \"%s\" superfluous\n", opt, endp);
    usage();
    return 0xdeadbeef;
}

int get_rand_int(int range)
{
    static long maxval = (~0 & 0x7fffffff);

    /* Is this correct? */
    double f = range * (double)random() / maxval; 
    return (int)(f + 0.5);
}

FILE* open_write_file(const char* fname)
{
    FILE* f = fopen(fname, "w");
    if (f != 0)
        return  f;

    msg("Couldn't open \"%s\" for write\n", fname);
    usage();

    /*NOTREACHED*/
    return 0;
}

FILE* open_append_file(const char* fname)
{
    FILE* f = fopen(fname, "a");
    if (f != 0)
        return  f;

    msg("Couldn't open \"%s\" for append\n", fname);
    usage();

    /*NOTREACHED*/
    return 0;
}

char* newstr(const char* s)
{
    return newsubstr(s, strlen(s));
}

char* newsubstr(const char* s, int len)
{
    char* p = malloc(len + 1);
    if (p == 0) {
        fprintf(stderr, "Couldn't malloc %d bytes\n", len);
        exit(3);
    }
    strncpy(p, s, len);
    p[len] = 0;
    return p;
}

void dump_buffer(const char* s, int bufsize, const char* tag)
{
    int i;
    char buf[BUFSIZ];
    char* p = buf;
    char* end = buf + sizeof(buf);

    if (tag == NULL)
        tag = "";

    if (s == NULL) {
        msg("%s(null)", tag);
        return;
    }

    strncpy(p, "<<\"", end - p);
    p += 3;

    for (i = 0; i < bufsize; i++) {
        int c = (*s) & 0xff;
        switch (c) {
        case '\r': strncpy(p, "\\r", end - p); p += 2; break;
        case '\n': strncpy(p, "\\n", end - p); p += 2; break;
        case '\\': strncpy(p, "\\\\", end - p); p += 2; break;
        default:
            if (c >= ' ' && c < 0x7f) {
                *p++ = c;
            }
            else {
                snprintf(p, end - p, "\\x%02x", c);
                p += 4;
            }
            break;
        }
        s++;
        if (p - buf > DUMP_LINELEN) {
            *p++ = '\\';
            *p = 0;
            msg("%s%s", tag, buf);

            p = buf;
            strncpy(p, "   ", end - p);
            p += 3;
        }
    }

    
    snprintf(p, end - p, "\">> (%d)", bufsize);

    msg("%s%s", tag, buf);
}

void msg(char* fmt, ...)
{
    char buf[BUFSIZ];
    char* p = buf;
    char* end = buf + sizeof(buf);
    time_t t;
    struct tm utc;

    va_list args;

    time(&t);
    gmtime_r(&t, &utc);

    p += snprintf(p, end - p, "%04d/%02d/%02d %02d:%02d:%02d T.%03d ",
                  1900 + utc.tm_year, 1 + utc.tm_mon, utc.tm_mday,
                  utc.tm_hour, utc.tm_min, utc.tm_sec,
                  self());

    va_start(args, fmt);
    p += vsnprintf(p, end - p, fmt, args);
    va_end(args);

    *p++ = '\n';
    *p = 0;

    fputs(buf, stderr);
    fflush(stderr);
}

