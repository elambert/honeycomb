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
 * Parse a character string to get a date and convert it to a time_t
 *
 * Shamim Mohamed, Thu Sep 20 15:06:10 PDT 2001
 */

#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include "getdate.h"

#ifdef	_MSC_VER
#define snprintf _snprintf
#endif

static const char rcsid[] = "$Id: parsedate.c 10895 2007-05-23 04:19:24Z pc198268 $";

int main(int argc, char* argv[])
{
    int i;
    char datebuf[1024];
    char* p = datebuf;
    int sz = sizeof(datebuf)-1;

    time_t then, now = time(0);

    if (argc == 1) {
        fprintf(stderr, "Usage: %s date-string\n", argv[0]);
        exit(1);
    }

    for (i = 1; i < argc; i++) {
        int j = snprintf(p, sz, "%s ", argv[i]);
        if (j < 0) {
            fprintf(stderr, "%s: buffer not big enough!!!\n", argv[0]);
            exit(2);
        }
        p += j; sz -= j;
    }

    then = get_date(datebuf, &now);

    printf("%ld\n", then);
    
    exit(0);
}
