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
import java.util.Arrays;
import java.util.Random;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;

public class ErasureAlgorithmTest {
    /** Static global random number generator */
    private static Random random = new Random();
    private static ByteBufferPool pool = ByteBufferPool.getInstance();

    private static ByteBufferList allocateDataBuffers(int numBuffers,
                                               int bufferSize) {
        ByteBufferList list = new ByteBufferList();
        for (int i=0; i<numBuffers; i++) {
            list.appendBuffer(pool.checkOutBuffer(bufferSize));
        }

        return list;
    }

    private static void prepareDataBuffers(ByteBufferList list) {
        ByteBuffer[] array = list.getBuffers();
        for (int i=0; i<array.length; i++) {
            // Fill the buffer with random bytes
            ByteBuffer buffer = array[i];
            buffer.clear();
            byte[] bytes = new byte[buffer.remaining()];
            random.nextBytes(bytes);
            buffer.put(bytes);
            buffer.flip();
        }
    }

    private static boolean verifySize(ByteBufferList[] buffers, int size) {
        boolean retval = true;
        for (int i=0; i<buffers.length; i++) {
            int bufferSize = buffers[i].remaining();
            if (bufferSize != size) {
                System.out.println("Fragment [" + i + "] is not of the " +
                                   "correct length. Expected [" + size +
                                   "] got [" + bufferSize + "]" );
                retval = false;
            }
        }
        return retval;
    }

    private static void
        getDataAndParityFragments(ByteBufferList[] fragmentList,
                                  ByteBuffer[] dataFragments,
                                  ByteBuffer[] parityFragments) {
        int numData = dataFragments.length;
        for (int i=0; i<numData; i++) {
            ByteBuffer[] buffers = fragmentList[i].getBuffers();
            for (int j=0; j<buffers.length; j++) {
                if (buffers[j].remaining() == 0) {
                    System.out.println("Skipping zero length buffer[" + i +
                                       "][" + j + "]");
                } else {
                    dataFragments[i] = buffers[j];
                }
            }
        }

        int numParity = parityFragments.length;
        for (int i=0; i<numParity; i++) {
            ByteBuffer[] buffers = fragmentList[numData + i].getBuffers();
            for (int j=0; j<buffers.length; j++) {
                if (buffers[j].remaining() == 0) {
                    System.out.println("Skipping zero length buffer[" +
                                       (numData + i) + "][" + j + "]");
                } else {
                    parityFragments[i] = buffers[j];
                }
            }
        }
    }

    private static boolean areEqual(ByteBuffer[] buffers1,
                                    ByteBuffer[] buffers2) {
        if (buffers1.length != buffers2.length) {
            System.out.println("Buffer arrays are of different lengths. " +
                               "buffers1.length = " + buffers1.length +
                               " buffers2.length = " + buffers2.length);
            return false;
        }

        boolean retval = true;
        for (int i=0; i<buffers1.length; i++) {
            if (buffers1[i].compareTo(buffers2[i]) != 0) {
                System.out.println("buffers1[" + i + "] is not equal to " +
                                   "buffers2[" + i + "]");
                retval = false;
            }
        }
        return retval;
    }

    private static void randomizeValidDataAndParity(boolean[] validData,
                                                    boolean[] validParity,
                                                    boolean trace) {
        int numInvalid = 0;
        boolean value = true;
        for (int i=0; i<(validData.length+validParity.length); i++) {
            if ((trace) && (i == 0)) {
                System.out.print("D[");
            }
            if ((trace) && (i == validData.length)) {
                System.out.print("] P[");
            }

            if ((numInvalid < validParity.length) && random.nextBoolean()) {
                value = false;
                numInvalid++;
            } else {
                value = true;
            }

            if (i < validData.length) {
                validData[i] = value;
            } else {
                validParity[i-validData.length] = value;
            }

            if (trace) {
                System.out.print((value?"1":"0"));
            }
        }
        if (trace) {
            System.out.println("]");
        }
    }

    private static ByteBuffer[] getCopyAsArray(ByteBufferList buffers) {
        ByteBuffer[] array = buffers.getBuffers();
        ByteBuffer[] resultArray = new ByteBuffer[array.length];
        for (int i=0; i<array.length; i++) {
            resultArray[i] = pool.checkOutBuffer(array[i].capacity());
            resultArray[i].put(array[i]);
            array[i].rewind();
            resultArray[i].flip();
        }
        return resultArray;
    }

    private static void prepareBuffers(ByteBuffer[] buffers,
                                       boolean[] isValid,
                                       boolean applyIsValid) {
        for (int i=0; i<buffers.length; i++) {
            if ((!isValid[i]) && (applyIsValid)) {
                buffers[i].clear();
                byte[] nullBytes = new byte[buffers[i].capacity()];
                Arrays.fill(nullBytes, (byte)0);
                buffers[i].put(nullBytes);
            }
            buffers[i].rewind();
        }
    }

    public static void main(String[] args) {
        int numData = 5;
        int numParity = 3;
        int fragSize = 4*1024;

        System.out.println("Allocating and preparing data buffers...");
        ByteBufferList dataBufferList = allocateDataBuffers(numData, fragSize);
        prepareDataBuffers(dataBufferList);

        ByteBuffer[] sourceDataBuffers = getCopyAsArray(dataBufferList);

        ErasureAlgorithm algorithm = 
            ErasureAlgorithm.getInstance(ErasureAlgorithm.REED_SOLOMON);

        System.out.println("Generating fragments...");
        ByteBufferList[] fragmentBuffers =
            algorithm.getDataAndParityBuffers(dataBufferList,
                                              fragSize,
                                              numData,
                                              numParity);

        // Make sure that all the buffer lists are of fragSize
        System.out.println("Verifying fragment sizes...");
        if (!verifySize(fragmentBuffers, fragSize)) {
            System.exit(1);
        }

        ByteBuffer[] dataFragments = new ByteBuffer[numData];
        ByteBuffer[] parityFragments = new ByteBuffer[numParity];
        getDataAndParityFragments(fragmentBuffers,
                                  dataFragments,
                                  parityFragments);

        // Compare the source data buffers with the generated data fragments
        System.out.println("Comparing source with generated fragments...");
        if (!areEqual(sourceDataBuffers, dataFragments)) {
            System.exit(1);
        }

        boolean[] validData = new boolean[numData];
        boolean[] validParity = new boolean[numParity];
        for (int i=0; i<100; i++) {
            randomizeValidDataAndParity(validData, validParity, true);

            // Prepare the buffers and apply the valid array by zeroing out
            // the corresponding buffers.
            prepareBuffers(dataFragments, validData, true);
            // TODO: Don't apply the valid array to the parity buffers now
            //       as it destroys it. Apply it later when there is code to
            //       reconstruct it.
            prepareBuffers(parityFragments, validParity, false);

            // Reconstruct the missing data buffer(s)
            algorithm.reconstructDataFragments(dataFragments,
                                               parityFragments,
                                               validData,
                                               validParity,
                                               fragSize);

            // Compare the data buffers with the original source buffers
            if (!areEqual(sourceDataBuffers, dataFragments)) {
                System.exit(1);
            }
        }
    }
}
