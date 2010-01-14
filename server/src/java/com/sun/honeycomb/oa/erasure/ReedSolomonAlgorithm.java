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
import java.util.Hashtable;
import java.util.logging.Logger;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;

public class ReedSolomonAlgorithm extends ErasureAlgorithm {
    private static final Logger logger = 
        Logger.getLogger(ReedSolomonAlgorithm.class.getName());

    /** Instance of the hash algorithm */
    private static ErasureAlgorithm algorithmInstance = null;

    private static Hashtable gflaCache = new Hashtable();

    /**
     * Factory method to create a content hash processor which can be used
     * along with a context to process the content hash of data.
     *
     * @param algorithm the algorithm to use
     * @return ContentHashAlgorithm the instance of the algorithm
     */
    public static ErasureAlgorithm getInstance() {
        synchronized(logger) {
            if (algorithmInstance == null) {
                // This is the first time getInstance has been called
                algorithmInstance = new ReedSolomonAlgorithm();
            }
        }
        return algorithmInstance;
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
    public ByteBufferList[] getDataAndParityBuffers(ByteBufferList bufferList,
                                                    int fragSize,
                                                    int numData,
                                                    int numParities) {
        // Check to make sure that the data is a multiple of "fragSize" bytes
        if ((bufferList.remaining() % fragSize) != 0) {
            throw new IllegalArgumentException("data is not a multiple of " +
                                               "fragment size " + fragSize);
        }

        // Allocate the parity fragments
        ByteBuffer[] parityBuffers = new ByteBuffer[numParities];
        ByteBufferList parityList = new ByteBufferList();
        for(int i=0; i<numParities;i++) {
            parityBuffers[i] =
                ByteBufferPool.getInstance().checkOutBuffer(fragSize);
        }

        // Calculate the parity fragments
        calculateParityBuffers(bufferList.getBuffers(),
                               parityBuffers,
                               fragSize,
                               numData,
                               numParities);

        // Flip the parity buffers after setting the correct position
        for (int i=0; i<numParities; i++) {
            parityList.appendBuffer(parityBuffers[i]);
            ByteBufferPool.getInstance().checkInBuffer(parityBuffers[i]);
        }

        // Get the data and parity fragments as fragSize buffer list
        ByteBufferList[] dataFrags = bufferList.slice(fragSize);
        ByteBufferList[] parityFrags = parityList.slice(fragSize);
        // Check in all the buffers in the pool
        parityList.clear();

        // Construct the M+N array to return the result
        ByteBufferList[] frags = new ByteBufferList[numData + numParities];
        for(int i=0; i<numData;i++) {
            frags[i] = dataFrags[i];
        }
        for(int i=0; i<numParities;i++) {
            frags[i+numData] = parityFrags[i];
        }
        
        // NOTE: Caller is responsible for returning these to pool
        return frags;
    }

    /**
     * Method to calculate the parity buffers from data buffers.
     *
     * @param dataBuffers the array of data buffers to process
     * @param parityBuffers the array of parity buffers to fill
     * @param fragSize the fragment size in bytes
     * @param numData the number of data fragments
     * @param numParities the number of parity fragments
     */
    public void calculateParityBuffers(ByteBuffer[] dataBuffers,
                                       ByteBuffer[] parityBuffers,
                                       int fragSize,
                                       int numData,
                                       int numParities) {
        // The GF instance to use for generating parities
        GFLinearAlgebra gfla = getGFLA(numData, numParities);
        gfla.calculateParityFragments(dataBuffers,
                                      parityBuffers,
                                      fragSize);
    }

    /**
     * Interface to reconstruct data from its parity.
     * TODO:
     * The current implementation of the reconstruction makes a copy of the
     * data buffers. This is done because the current implementation of the
     * matrix inverstion and multiplication takes a byte array. These methods
     * need to operate on byte buffers instead.
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
    public void reconstructDataFragments(ByteBuffer[] dataFragments,
                                         ByteBuffer[] parityFragments,
                                         boolean[] validData,
                                         boolean[] validParity,
                                         int fragSize) {
        if (dataFragments.length != validData.length) {
            throw new IllegalArgumentException("dataFragments length [" +
                                               dataFragments.length + "] " +
                                               "and validData length [" +
                                               validData.length + "] do not " +
                                               "match");
        }

        if (parityFragments.length != validParity.length) {
            throw new IllegalArgumentException("parityFragments length [" +
                                               parityFragments.length + "] " +
                                               "and validParity length [" +
                                               validParity.length +
                                               "] do not match");
        }

        // Count the number of valid and missing data fragments
        int nDataFragsAvailable=0;
        int nMissing = 0;
        for(int i=0; i<validData.length; i++) { 
            if(validData[i]) {
                nDataFragsAvailable++;
            } else {
                nMissing++;
            }
        }

        // Nothing to do if there are no missing data fragments
        if (nDataFragsAvailable == dataFragments.length) {
            return;
        }

        // Count the number of valid parity fragments
        int nCheckFragsAvailable=0;
        for(int i=0; i<validParity.length; i++) {
            if(validParity[i]) {
                nCheckFragsAvailable++;
            }
        }

        // Check to see if there are enough parity fragments for reconstruction
        if (nCheckFragsAvailable < nMissing) {
            throw new IllegalArgumentException("Not enough parity fragments " +
                                               "to reconstruct data");
        }

        int nFragsAvailable = nDataFragsAvailable + nCheckFragsAvailable;

        int missingFrags[] = new int[nMissing];
        int replacedBy[] = new int[nMissing];
        byte[][] fragMatrix = new byte[dataFragments.length][];
        int fragMatrixIndex = 0;
        int tempIndex = 0;

        // Construct the fragment matrix and fill it with data fragments
        for(int i=0; i<dataFragments.length; i++) {
            if(validData[i]) {
                fragMatrix[fragMatrixIndex] = new byte[fragSize];
                int bufferPosition = dataFragments[i].position();
                dataFragments[i].rewind();
                dataFragments[i].get(fragMatrix[fragMatrixIndex]);
                fragMatrixIndex++;
                dataFragments[i].position(bufferPosition);
            } else  {
                missingFrags[tempIndex++] = i;
            }
        }

        // Fill the rest of the fragment matrix with available parity
        // fragments.
        tempIndex = 0;
        for(int i=0; i<parityFragments.length; i++) {
            if(validParity[i]) {
                fragMatrix[fragMatrixIndex] = new byte[fragSize];
                int bufferPosition = parityFragments[i].position();
                parityFragments[i].rewind();
                parityFragments[i].get(fragMatrix[fragMatrixIndex]);
                fragMatrixIndex++;
                parityFragments[i].position(bufferPosition);
                replacedBy[tempIndex++] = i;

                // Break if the fragment matrix is full
                if (fragMatrixIndex == dataFragments.length) {
                    break;
                }
            }
        }

        /*
        for(int i = 0; i < missingFrags.length; i++) {
            logger.info("Replacing " + missingFrags[i] + " with " +
                        replacedBy[i]);
        }
        */

        // Get the GF and reconstruct all the missing fragments
        GFLinearAlgebra gfla = getGFLA(dataFragments.length,
                                       parityFragments.length);
        byte[][] inverse = gfla.getInverse(missingFrags, replacedBy);
        for(int i=0; i<nMissing; i++) {
            byte[][] inverseRow = new byte[1][];
            inverseRow[0] = inverse[missingFrags[i]];
            byte[][] temp = gfla.matmul(inverseRow, fragMatrix);
//             System.err.println("Puting " + temp[0].length + " bytes into " + missingFrags[i] + 
//                                " at position " + dataFragments[missingFrags[i]].position() +
//                                " with remaining " + dataFragments[missingFrags[i]].remaining());
            dataFragments[missingFrags[i]].put(temp[0]);
            dataFragments[missingFrags[i]].flip();
        }
    }

    /**
     * Method to get a GFLA object instance for a certain "N", "M" and 
     */
    private synchronized GFLinearAlgebra getGFLA(int numData,
                                                 int numParities) {
        // Check to see if there is a GF object in the cache
        String key = Integer.toString(numData) +
            Integer.toString(numParities);

        GFLinearAlgebra gfla = (GFLinearAlgebra)gflaCache.get(key);
        if (gfla == null) {
            gfla = new GFLinearAlgebra(numParities,
                                       numData,
                                       GFLinearAlgebra.VANDERMONDE);
            gflaCache.put(key, gfla);
        }

        return gfla;
    }
}
