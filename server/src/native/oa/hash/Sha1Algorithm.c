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
#include <openssl/sha.h>

#include "Sha1Algorithm.h"

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
 * Class:     com_sun_honeycomb_oa_hash_Sha1Algorithm
 * Method:    getNativeContextSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_com_sun_honeycomb_oa_hash_Sha1Algorithm_getNativeContextSize
(JNIEnv *env,
 jclass class) {
  return sizeof(SHA_CTX);
}

/*
 * Class:     com_sun_honeycomb_oa_hash_Sha1Algorithm
 * Method:    initializeContext
 * Signature: (Ljava/nio/ByteBuffer;)V
 */
JNIEXPORT void JNICALL
Java_com_sun_honeycomb_oa_hash_Sha1Algorithm_initializeContext
(JNIEnv *env,
 jclass class,
 jobject jNativeContext) {
  jbyte *bytes = (jbyte *)(*env)->GetDirectBufferAddress(env, jNativeContext);
  if (bytes == NULL) {
    packageException(env,
                     "java/lang/IllegalArgumentException",
                     "Got null address for the native context");
    return;
  }

  SHA_CTX *native_sha_context = (SHA_CTX *)bytes;
  SHA1_Init(native_sha_context);
}


/*
 * Class:     com_sun_honeycomb_oa_hash_Sha1Algorithm
 * Method:    update
 * Signature: (Ljava/nio/ByteBuffer;JJLjava/nio/ByteBuffer;)V
 */
JNIEXPORT void JNICALL Java_com_sun_honeycomb_oa_hash_Sha1Algorithm_update
(JNIEnv *env,
 jclass class,
 jobject jByteBuffer,
 jlong offset,
 jlong length,
 jobject jNativeContext) {
  SHA_CTX *native_sha_context;
  jbyte *bytes;

  native_sha_context =
    (SHA_CTX *)(*env)->GetDirectBufferAddress(env, jNativeContext);

  if (native_sha_context == NULL) {
    packageException(env,
                     "java/lang/IllegalArgumentException",
                     "Got null address for the native context");
    return;
  }

  bytes = (jbyte *)(*env)->GetDirectBufferAddress(env, jByteBuffer);

  if (bytes == NULL) {
    packageException(env,
                     "java/lang/IllegalArgumentException",
                     "Got null address for the data buffer");
    return;
  }

  SHA1_Update(native_sha_context, (bytes+offset), length);
}

/*
 * Class:     com_sun_honeycomb_oa_hash_Sha1Algorithm
 * Method:    digest
 * Signature: ([BLjava/nio/ByteBuffer;)V
 */
JNIEXPORT void JNICALL Java_com_sun_honeycomb_oa_hash_Sha1Algorithm_digest
(JNIEnv *env,
 jclass class,
 jbyteArray jDigest,
 jobject jNativeContext) {
  SHA_CTX *native_sha_context =
    (SHA_CTX *)(*env)->GetDirectBufferAddress(env, jNativeContext);

  if (native_sha_context == NULL) {
    packageException(env,
                     "java/lang/IllegalArgumentException",
                     "Got null address for the native context");
    return;
  }

  jbyte *digestBytes = (*env)->GetByteArrayElements(env, jDigest, 0);
  unsigned char* foo = (unsigned char*) digestBytes;
  SHA1_Final(foo, native_sha_context);
  (*env)->ReleaseByteArrayElements(env, jDigest, digestBytes, 0);
}
