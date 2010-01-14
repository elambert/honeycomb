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
 * Some convenience functions to call back to Java
 *
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

/*
 * Really want to do this:
 *     #define new_string(env, s) new_jstring(env, s)
 * but that doesn't work -- needs debugging
 */
#define new_string(env, s) ((*(env))->NewStringUTF((env), (s)))

int sendAttrs(JNIEnv* env, jclass cls,
                      jlong crTime, jint kid,
                      const char* module, jint instance, const char* name,
                      jint type, const char* clsName, jlong snapTime)
{
    const char* signature =
        "(JILjava/lang/String;ILjava/lang/String;ILjava/lang/String;J)V";

    jmethodID kstatAttrs =
        (*env)->GetStaticMethodID(env, cls, "kstatAttrs", signature);
    if (kstatAttrs == NULL)
        return -1;

    (*env)->CallStaticVoidMethod(env, cls, kstatAttrs,
                                 crTime, kid,
                                 new_string(env, module), instance,
                                 new_string(env, name), type,
                                 new_string(env, clsName), snapTime);
    return 0;
}

int sendRaw(JNIEnv * env, jclass cls,
                   const char* buffer, size_t size)
{
    const signed char* p = (const signed char*)buffer; /* Fucking ridiculous */

    jbyteArray byteArray = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, byteArray, 0, size, p);

    jmethodID setRawData =
      (*env)->GetStaticMethodID(env, cls, "setRawData", "([B)V");
    if (setRawData == NULL)
        return -1;
    (*env)->CallStaticVoidMethod(env, cls, setRawData, byteArray);

    return 0;
}

int sendNamedString(JNIEnv * env, jclass cls, const char* key,
                            const char* value, size_t vsize)
{
    jmethodID addNamedString =
      (*env)->GetStaticMethodID(env, cls, "addNamedString",
                                "(Ljava/lang/String;Ljava/lang/String;)V");
    if (addNamedString == NULL)
        return -1;
    (*env)->CallStaticVoidMethod(env, cls, addNamedString,
                                 new_string(env, key),
                                 new_string(env, value));                  
    return 0;
}

int sendNamedLong(JNIEnv * env, jclass cls, char* key, jlong val)
{
    jmethodID addNamedLong =
        (*env)->GetStaticMethodID(env, cls, "addNamedLong",
                                  "(Ljava/lang/String;J)V");
    if (addNamedLong == NULL)
        return -1;

    (*env)->CallStaticVoidMethod(env, cls, addNamedLong,
                                 new_string(env, key), val);
    return 0;
}

int sendIntr(JNIEnv * env, jclass cls, unsigned ctrs[])
{
    jmethodID setInterruptCounts =
        (*env)->GetStaticMethodID(env, cls, "setInterruptCounts", "(JJJJJ)V");
    if (setInterruptCounts == NULL)
        return -1;
    (*env)->CallStaticVoidMethod(env, cls, setInterruptCounts,
                                 (jlong)ctrs[0], (jlong)ctrs[1],
                                 (jlong)ctrs[2], (jlong)ctrs[3],
                                 (jlong)ctrs[4]);
    return 0;
}

int sendIOStat(JNIEnv * env, jclass cls,
                jlong l1, jlong l2, jlong l3, jlong l4, jlong l5, jlong l6,
                jlong l7, jlong l8, jlong l9, jlong l10, jlong l11, jlong l12)
{

    jmethodID setIOStat =
        (*env)->GetStaticMethodID(env, cls, "setIOStat", "(JJJJJJJJJJJJ)V");
    if (setIOStat == NULL)
        return -1;
    (*env)->CallStaticVoidMethod(env, cls, setIOStat,
                                 l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11,
                                 l12);
    return 0;
}

int sendTimer(JNIEnv * env, jclass cls, const char* name,
               jlong l1, jlong l2, jlong l3, jlong l4, jlong l5, jlong l6)
{
    
    jmethodID addTimer =
        (*env)->GetStaticMethodID(env, cls, "newTimer",
                                  "(Ljava/lang/String;JJJJJJ)V");
    if (addTimer == 0)
        return -1;
    (*env)->CallStaticVoidMethod(env, cls, addTimer, 
                                 new_string(env, name),
                                 l1, l2, l3, l4, l5, l6);
    return 0;
}
 

static jstring 
new_ljstring(JNIEnv *env, const char *str, size_t len)
{
    jstring result;
    jbyteArray bytes = 0;

    static jclass Class_java_lang_String = 0;
    static jmethodID MID_String_init = 0;

    if (!Class_java_lang_String)
        Class_java_lang_String = (*env)->FindClass(env, "Ljava/lang/String;");

    if (!MID_String_init)
        MID_String_init =
            (*env)->GetMethodID(env, Class_java_lang_String, "<init>", "[B");

    if (!str)
        return NULL;

    if ((*env)->EnsureLocalCapacity(env, 2) < 0) {
        /* out of memory error */
        return NULL;
    }

    bytes = (*env)->NewByteArray(env, len);
    if (bytes != NULL) {
        (*env)->SetByteArrayRegion(env, bytes, 0, len,
                                   (jbyte *)str);
        result = (*env)->NewObject(env, Class_java_lang_String,
                                   MID_String_init, bytes);
        (*env)->DeleteLocalRef(env, bytes);
        return result;
    }

    return NULL;
}

static jstring 
new_jstring(JNIEnv *env, const char *str)
{
    if (!str)
        return NULL;
    return new_ljstring(env, str, strlen(str));
}
