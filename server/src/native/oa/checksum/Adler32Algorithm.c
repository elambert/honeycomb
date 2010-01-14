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



#include <jni.h>
#include <zlib.h>

#include "Adler32Algorithm.h"

static void packageException(JNIEnv *env,
                             const char *exceptionClass,
                             const char *errorString) {
  // Get the exception class
  jclass newExcCls = (*env)->FindClass(env, exceptionClass);
  if(newExcCls == 0) {
    return;
  }
 
  // Indicate an exception with the error string
  char exp[128];
  sprintf(exp, "%s", errorString);
  (*env)->ThrowNew(env, newExcCls, exp);
}

/*
 * Class:     com_sun_honeycomb_oa_checksum_Adler32Algorithm
 * Method:    nativeInitialize
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_sun_honeycomb_oa_checksum_Adler32Algorithm_nativeInitialize
(JNIEnv *env,
 jclass class) {
  return adler32(0L, Z_NULL, 0);
}

/*
 * Class:     com_sun_honeycomb_oa_checksum_Adler32Algorithm
 * Method:    nativeUpdate
 * Signature: (Ljava/nio/ByteBuffer;IIJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_sun_honeycomb_oa_checksum_Adler32Algorithm_nativeUpdate
(JNIEnv *env,
 jclass class,
 jobject jByteBuffer,
 jint offset,
 jint length,
 jlong state) {
  jbyte *bytes = (jbyte *)(*env)->GetDirectBufferAddress(env, jByteBuffer);
  if (bytes == NULL) {
    packageException(env,
                     "java/lang/IllegalArgumentException",
                     "Got null address for the data buffer");
    return -1;
  }

  const unsigned char* foo = (const unsigned char*) (bytes+offset);
  return adler32(state, foo, length);
}
