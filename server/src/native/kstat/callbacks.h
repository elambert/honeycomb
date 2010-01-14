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



#include <jni.h>        /* Standard native method stuff */
#include "jkstat.h"     /* Generated earlier */
#include <sys/types.h>
#include <stdio.h>
#include <unistd.h>
#include <strings.h>
#include <errno.h>
#include <sys/vfs.h>
#include <sys/time.h>
#include <sys/statvfs.h>
#include <syslog.h>
#include <sys/sysinfo.h>
#include <sys/loadavg.h>
#include <kstat.h>
#include <sys/kstat.h>

extern int
sendAttrs(JNIEnv* env, jclass cls,
          jlong crTime, jint kid,
          const char* module, jint instance, const char* name,
          jint type, const char* clsName, jlong snapTime);


extern int
sendRaw(JNIEnv * env, jclass cls,
        const char* buffer, size_t size);


extern int
sendNamedString(JNIEnv * env, jclass cls, const char* key,
                const char* value, size_t vsize);


extern int
sendNamedLong(JNIEnv * env, jclass cls, char* key, jlong val);


extern int
sendIntr(JNIEnv * env, jclass cls, unsigned ctrs[]);


extern int
sendIOStat(JNIEnv * env, jclass cls,
           jlong l1, jlong l2, jlong l3, jlong l4, jlong l5, jlong l6,
           jlong l7, jlong l8, jlong l9, jlong l10, jlong l11, jlong l12);


extern int
sendTimer(JNIEnv * env, jclass cls, const char* name,
          jlong l1, jlong l2, jlong l3, jlong l4, jlong l5, jlong l6);

