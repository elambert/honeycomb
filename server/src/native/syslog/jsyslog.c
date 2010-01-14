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
#include "jsyslog.h"     /* Generated earlier */
#include <syslog.h>

#define SYSLOG_PREFIX "java"
/* local_0 is used for traces that stays on local host */
#define SYSLOG_FACILITY LOG_LOCAL0
/* local_1 is used for traces that we forward to the loghost */
#define	SYSLOG_EXT_FALICITY LOG_LOCAL1

/*
 *  This logs to syslog
 */

/*
 * Class:     com_sun_honeycomb_util_SysLog
 * Method:    println
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_sun_honeycomb_util_SysLog_println
(JNIEnv * env, jclass cls, jint in_priority, jint jfacility,
jstring in_message) {

    int facility = 0;
    switch (jfacility) {
        case 1:
            facility = LOG_LOCAL1;
            break;
        case 2:
            facility = LOG_LOCAL2;
            break;
        default:
            facility = LOG_LOCAL0;
            ; // do nothing, facility = 0
    }

    // openlog shoud be called once and only once per process. Instead because
    // there are multiple people trying to log we're just going to use syslog
    // without calling open
    //openlog(SYSLOG_PREFIX, LOG_CONS | LOG_NOWAIT, facility);    
    const char *message = (*env)->GetStringUTFChars(env, in_message, 0);
    // Remember, there could be '%' characters inside message!
    syslog((int) in_priority|facility, "%s", message);
    (*env)->ReleaseStringUTFChars(env, in_message, message);
    //closelog();
} 
