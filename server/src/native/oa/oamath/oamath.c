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
    int resRowWidth =  fragSize;
    
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
                    *(mtab + ((*(fwdp + k))&MASK)*mtabRowWidth 
                      + ((*blkp)&MASK));
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
