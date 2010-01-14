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
#include"GFLinearAlgebra.h"
#include <stdio.h>

#define MASK 0xff

/**
 * Method to return the current position of a byte buffer.
 *
 * @param env the java runtime environment
 * @param jByteBuffer the byte buffer to use
 * @return jint the current position of the byte buffer
 */
static jint position(JNIEnv *env, jobject jByteBuffer) {
  jmethodID id = (*env)->GetMethodID(env,
                                     (*env)->GetObjectClass(env, jByteBuffer),
                                     "position",
                                     "()I");
  return (*env)->CallIntMethod(env, jByteBuffer, id);
}

/**
 * Method to return the remaining bytes in a byte buffer.
 *
 * @param env the java runtime environment
 * @param jByteBuffer the byte buffer to use
 * @return jint the remaining bytes in the byte buffer
 */
static jint remaining(JNIEnv *env, jobject jByteBuffer) {
  jmethodID id = (*env)->GetMethodID(env,
                                     (*env)->GetObjectClass(env, jByteBuffer),
                                     "remaining",
                                     "()I");
  return (*env)->CallIntMethod(env, jByteBuffer, id);
}

/**
 * Method to return the byte address and its length from an array of byte
 * buffers.
 *
 * @param env the java runtime environment
 * @param dataFragmentArray the array of data byte buffers
 * @param index the index of the byte buffer to process
 * @retval1 jint* the length of the valid byte address region
 * @retval2 jbyte* the address of the first valid byte or NULL on error
 */
static jbyte* getByteBufferAddress(JNIEnv *env,
                                   jobjectArray jbyteBufferArray,
                                   jint index,
                                   jint *length) {
  jobject jByteBuffer = (*env)->GetObjectArrayElement(env,
                                                      jbyteBufferArray,
                                                      index);
  // Get the data pointer
  jbyte *bytes = (jbyte *)(*env)->GetDirectBufferAddress(env,
                                                         jByteBuffer);
  if (bytes == NULL) {
    return NULL;
  }

  // Increment the byte pointer to the position in the buffer
  bytes += position(env, jByteBuffer);

  // The length is the remaining capacity in the buffer
  *length = remaining(env, jByteBuffer);

  return bytes;
}

/**
 * Method to package an exception in the java runtime. This will make the
 * JVM throw and exception when the native call returns.
 *
 * @param env the java runtime environment
 * @param exceptionClass the fully qualified name of the exception class
 * @param errorString the error string to put in the exception
 */
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
 * Class:     com_sun_honeycomb_oa_erasure_GFLinearAlgebra
 * Method:    nativeCalculateParity
 * Signature: ([Ljava/nio/ByteBuffer;[Ljava/nio/ByteBuffer;III[B[BS)V
 */
JNIEXPORT void
JNICALL Java_com_sun_honeycomb_oa_erasure_GFLinearAlgebra_nativeCalculateParity
(JNIEnv *env,
 jobject object,
 jobjectArray dataFragmentArray,
 jobjectArray parityFragmentArray,
 jint nData,
 jint nParity,
 jint fragSize,
 jbyteArray linearForward,
 jbyteArray mulTable,
 jshort multTableRowWidth) {
  jint i;
  jsize arraySize;
  jint length;

  // Get arrays from Java (these will not move until we release)
  jbyte *fwd = (*env)->GetByteArrayElements(env, linearForward, 0);
  jbyte *mtab = (*env)->GetByteArrayElements(env, mulTable, 0);

  // Construct the parity buffer array
  arraySize = (*env)->GetArrayLength(env, parityFragmentArray);
  jbyte *parityFragments[nParity];
  for (i=0; i<nParity; i++) {
    parityFragments[i] = getByteBufferAddress(env,
                                              parityFragmentArray,
                                              i,
                                              &length);
    if (parityFragments[i] == NULL) {
      packageException(env,
                       "java/lang/IllegalArgumentException",
                       "Got null parity fragment address");
      return;
    }

    // Check to make sure that there are enough bytes in the buffer
    if (length < fragSize) {
      packageException(env,
                       "java/lang/IllegalArgumentException",
                       "Not enough bytes in the parity buffer");
      return;
    }
  }

  jint j;
  jint k;
  int fwdRowWidth = nData;
  jint indexInFragment = 0;
  jint currentDataFragment = 0;
  jbyte *fwdp;

  // Iterate through the array of data bytes
  arraySize = (*env)->GetArrayLength(env, dataFragmentArray);
  for (i=0; i<arraySize; i++) {
    jbyte* data = getByteBufferAddress(env,
                                       dataFragmentArray,
                                       i,
                                       &length);
    if (data == NULL) {
      packageException(env,
                       "java/lang/IllegalArgumentException",
                       "Got null data fragment address");
      return;
    }

    // Process the data from the buffer data[j]
    for (j=0; j<length; j++) {
      // Update all the parity blocks
      fwdp = fwd;
      for(k=0; k<nParity; k++) {
        // Initialize the parity index if this is the first time it is
        // being visited.
        if ((currentDataFragment == 0) && (indexInFragment < fragSize)) {
          (parityFragments[k])[indexInFragment] = 0;
        }

        (parityFragments[k])[indexInFragment] ^=
          *(mtab +
            ((*(fwdp + currentDataFragment))&MASK)*multTableRowWidth +
            ((data[j])&MASK));

        fwdp += fwdRowWidth;
      }

      // Increment the index in the fragment range taking care of the wrap
      // around. This also increments the current data fragment number as
      // fragSize bytes have been processed.
      if (++indexInFragment >= fragSize) {
        indexInFragment = indexInFragment % fragSize;
        currentDataFragment++;
      }
    }
  }

  // Tell VM we are done with these arrays so it can move them etc.
  (*env)->ReleaseByteArrayElements(env, linearForward, fwd, 0);
  (*env)->ReleaseByteArrayElements(env, mulTable, mtab, 0);
}

/*
 * Class:     com_sun_honeycomb_oa_erasure_GFLinearAlgebra
 * Method:    nativeGenerateParity
 * Signature: ([BIII[B[BS[B)V
 * This is a native implementation of the matrix multiply
 * used to generate parity fragments.
 * C allows true multi-dimentional arrays and real ptr arithmetic
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_oa_erasure_GFLinearAlgebra_nativeGenerateParity
(JNIEnv *env, jobject obj, jbyteArray block, jint nData, jint nChecks, 
 jint fragSize, jbyteArray linearForward, jbyteArray mulTable, 
 jshort mtabRowWidth, jbyteArray result) {
    int i=0, j=0, k=0;
    
    // Get arrays from Java (these will not move until we release)
    jbyte *blk = (*env)->GetByteArrayElements(env, block, 0);
    jbyte *fwd = (*env)->GetByteArrayElements(env, linearForward, 0);
    jbyte *mtab = (*env)->GetByteArrayElements(env, mulTable, 0);
    
    int resSize = nChecks*fragSize;
    jbyte localres[resSize];
    
    // Row width of x[y][z] is z
    int blkRowWidth = fragSize;
    int fwdRowWidth = nData;
    
    // a = linearForward
    // b = block
    // aRows = nChecks
    // aCols = bRows = nData
    // bCols = fragSize
    
    // These ptrs allow us to eliminate some array indexs + therefore 
    // we avoid some mults in the inner loop (which regularly runs
    // around 100,000 times per block).  One mult still remains.
    jbyte *resp = localres;
    jbyte *fwdp = 0;
    jbyte *blkp = 0;
    
    fwdp = fwd;
    for(i=0; i<nChecks; i++) {
      for(j=0; j<fragSize; j++) {
        blkp = blk + j;
        *resp = 0;
        for(k=0; k<nData; k++) {
          *resp = *resp ^ 
            *(mtab +
              ((*(fwdp + k))&MASK)*mtabRowWidth +
              ((*blkp)&MASK)
              );
          blkp += blkRowWidth;
        }
        resp++;
      }
      fwdp += fwdRowWidth;
    }
     
    // Copy our local work into the VM
    (*env)->SetByteArrayRegion(env, result, 0, resSize, localres);
  
    // Tell VM we are done with these arrays so it can move them etc.
    (*env)->ReleaseByteArrayElements(env, block, blk, 0);
    (*env)->ReleaseByteArrayElements(env, linearForward, fwd, 0);
    (*env)->ReleaseByteArrayElements(env, mulTable, mtab, 0);
}
