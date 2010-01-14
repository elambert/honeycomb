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
 * To test with
 */

#include <sys/types.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <strings.h>
#include <errno.h>

#include "debug_callbacks.h"

extern int
Java_com_sun_honeycomb_util_Kstat_getKstat
(void* env, void* cls, const char* module, long instance, const char* kname);

int
debugSendAttrs(void* env, void* cls,
               jlong crTime, jint kid,
               const char* module, jint instance, const char* name,
               jint type, const char* clsName, jlong snapTime)
{
    static char* ctypes[] = { "raw", "named", "intr", "io", "timer" };
    printf("Kstat %s:%d:%s class \"%s\"\n",
           module, instance, name, clsName);
    printf("    %s kstat %d created %lld snaptime %lld\n",
           ctypes[type], kid, crTime, snapTime);
    return 0;
}

int
debugSendRaw(void * env, void* cls,
             const char* buffer, size_t size)
{
    char buf[51];
    int i = 0, j = 0;
    
    if (!buffer) {
        printf("    (null) [%ld]\n", size);
        return 0;
    }

    while (i < (sizeof(buf) - 1) && j < size) {
        sprintf(buf + i, "%02x", buffer[j++] & 0xff);
        i += 2;
    }
    buf[i] = 0;
    printf("    0x%s\n", buf);
    return 0;
}

int
debugSendNamedString(void * env, void* cls, const char* key,
                     const char* value, size_t vsize)
{
    printf("    %s = \"%s\"\n", key, value);
    return 0;
}

int
debugSendNamedLong(void * env, void* cls, char* key, jlong val)
{
    printf("    %s = %lld\n", key, val);
    return 0;
}

int
debugSendIntr(void * env, void* cls, unsigned ctrs[])
{
    printf("    [ %uld, %uld, %uld, %uld, %uld ]\n", ctrs[0], ctrs[1], ctrs[2],
           ctrs[3], ctrs[4]);
    return 0;
}

int
debugSendIOStat(void * env, void* cls,
                jlong l1, jlong l2, jlong l3, jlong l4, jlong l5, jlong l6,
                jlong l7, jlong l8, jlong l9, jlong l10, jlong l11, jlong l12)
{
    printf("    { %lld %lld %lld %lld %lld %lld %lld %lld %lld %lld %lld "
           "%lld }\n",
           l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12);
    return 0;
}

int
debugSendTimer(void * env, void* cls, const char* name,
               jlong l1, jlong l2, jlong l3, jlong l4, jlong l5, jlong l6)
{
    static char* ttypes[] = {
        "hard", "soft", "watchdog", "spurious", "multsvc"
    };
    printf("    %s [ %lld  %lld  %lld  %lld  %lld  %lld ]\n",
           l1, l2, l3, l4, l5, l6);
    return 0;
}

int main(int argc, char*argv[]) {
    if (argc != 2) {
        fprintf(stderr, "Usage: %s module:instance:name\n", argv[0]);
        exit(1);
    }

    char* module = argv[1];
    char* p = strchr(argv[1], ':');
    if (!p) {
        fprintf(stderr, "Need three components\n");
        exit(2);
    }
    *p++ = 0;
    char* inst = p;
    p = strchr(inst, ':');
    if (!p) {
        fprintf(stderr, "Need three components\n");
        exit(2);
    }
    *p++ = 0;
    char* name = p;

    long instance = atol(inst);

    Java_com_sun_honeycomb_util_Kstat_getKstat(0, 0, module, instance, name);

    exit(0);
}
