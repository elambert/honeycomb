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



#include <util.h>

time_t now() {
    time_t t;
    time(&t);
    return t;
}

int getint(char opt, const char* s) {
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

FILE* open_append_file(const char* fname)
{
    FILE* f = fopen(fname, "a");
    if (f != 0)
        return  f;

    fprintf(stderr, "Couldn't open \"%s\" for append\n", fname);
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
