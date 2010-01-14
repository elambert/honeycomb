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



package com.sun.honeycomb.oa.erasure;

import java.nio.ByteBuffer;

import com.sun.honeycomb.resources.ByteBufferList;

public abstract class ErasureAlgorithm {
    /** Supported erasure algorithms */
    public static final int REED_SOLOMON = 1;

    /**
     * Factory method to create a content hash processor which can be used
     * along with a context to process the content hash of data.
     *
     * @param algorithm the algorithm to use
     * @return ContentHashAlgorithm the instance of the algorithm
     */
    public static ErasureAlgorithm getInstance(int algorithm)
        throws IllegalArgumentException {
        if (algorithm == REED_SOLOMON) {
            return ReedSolomonAlgorithm.getInstance();
        } else {
            throw new IllegalArgumentException("Invalid algorithm [" +
                                               algorithm + "]");
        }
    }

    /**
     * Interface to generate parity buffers using the erasure algorithm. This
     * interface assumes that the 
     *
     * @param bufferList the list of data buffers to process
     * @param fragSize the size of the fragment that the algorithm should use
     * @param numData the number of data fragments to fragment to
     * @param numParities the number of parity fragments to generate
     * @return ByteBufferList[] the array of buffers containing the data and
     *                          parities
     */
    public abstract  ByteBufferList[]
        getDataAndParityBuffers(ByteBufferList bufferList,
                                int fragSize,
                                int numdata,
                                int numParities);

    /**
     * Method to calculate the parity buffers from data buffers.
     *
     * @param dataBuffers the array of data buffers to process
     * @param parityBuffers the array of parity buffers to fill
     * @param fragSize the fragment size in bytes
     * @param numData the number of data fragments
     * @param numParities the number of parity fragments
     */
    public abstract void calculateParityBuffers(ByteBuffer[] dataBuffers,
                                                ByteBuffer[] parityBuffers,
                                                int fragSize,
                                                int numData,
                                                int numParities);

    /**
     * Interface to reconstruct data from its parity.
     *
     * @param dataFragments the data fragment array.The length of the array
     *        denotes the number of possible data fragments.
     * @param parityFragments the parity fragments to use for reconstruction.
     *        The length of the array denotes the number of possible parity
     *        fragments.
     * @param validData the list of valid data fragments. false indicates that
     *        the corresponding data fragment needs to be reconstructed.
     * @param validParity the list of valid parity fragments. true indicates
     *        the corresponding parity fragment can be used for reconstruction.
     * @param fragSize the size of the data and parity fragments
     */
    public abstract void reconstructDataFragments(ByteBuffer[] dataFragments,
                                                  ByteBuffer[] parityFragments,
                                                  boolean[] validData,
                                                  boolean[] validParity,
                                                  int fragSize);
}
