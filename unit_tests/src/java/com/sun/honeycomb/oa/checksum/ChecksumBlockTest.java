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



package com.sun.honeycomb.oa.checksum;

import java.nio.ByteBuffer;

import com.sun.honeycomb.resources.ByteBufferPool;

public class ChecksumBlockTest {
    public static void main(String[] args) {
        ChecksumAlgorithm algorithm =
            ChecksumAlgorithm.getInstance(ChecksumAlgorithm.ADLER32);
        ChecksumBlock cb = algorithm.createChecksumBlock();

        int max = cb.getMaxChecksums(algorithm.getChecksumSize(),
                                     ChecksumAlgorithm.DATA_BLOCK_SIZE);
        System.out.println("Max checksums = " + max);

        //
        // Test checksum population
        //
        if (cb.numChecksums() != 0) {
            System.out.println("FAILED: New checksum block has " +
                               cb.numChecksums() + " instead of zero");
            System.out.println(cb);
            System.exit(1);
        }
        AlgorithmState state = algorithm.createAlgorithmState();
        for (int i=0; i<max; i++) {
            if (cb.isFull()) {
                System.out.println("FAILED: Cannot be full before max " +
                                   "checksums are inserted. max = " + max +
                                   " inserted = " + i);
                System.out.println(cb);
                System.exit(1);
            }
            cb.insertChecksum(algorithm, state);
        }
        if (!cb.isFull()) {
            System.out.println("FAILED: Checksum block should be full");
            System.out.println(cb);
            System.exit(1);
        }
        cb.dispose();
        System.out.println("Checksum population...[PASSED]");


        //
        // Test checksum insert at an offset
        //

        cb = algorithm.createChecksumBlock();
        cb.insertChecksum(algorithm, state, 0);
        // Insert at offset 0 and make sure that there is only one checksum
        if (cb.numChecksums() != 1) {
            System.out.println("FAILED: New checksum block has " +
                               cb.numChecksums() + " instead of 1");
            System.out.println(cb);
            System.exit(1);
        }
        // Insert at offset 1 and make sure that there are two checksums
        cb.insertChecksum(algorithm, state, 1);
        if (cb.numChecksums() != 2) {
            System.out.println("FAILED: New checksum block has " +
                               cb.numChecksums() + " instead of 2");
            System.out.println(cb);
            System.exit(1);
        }
        // Insert at offset 0 again and make sure that there are still only
        // two checksums.
        cb.insertChecksum(algorithm, state, 0);
        if (cb.numChecksums() != 2) {
            System.out.println("FAILED: New checksum block has " +
                               cb.numChecksums() + " instead of 2");
            System.out.println(cb);
            System.exit(1);
        }
        cb.dispose();
        System.out.println("Checksum insert...[PASSED]");

        //
        // Test checksum calculation and update into block
        //
        cb = algorithm.createChecksumBlock();
        ByteBufferPool pool = ByteBufferPool.getInstance();
        int numChecksums = 64;
        ByteBuffer buffer = pool.checkOutBuffer
            (ChecksumAlgorithm.DATA_BLOCK_SIZE*numChecksums);
        algorithm.insert(buffer, 0, cb);
        if (cb.numChecksums() != numChecksums) {
            System.out.println("FAILED: New checksum block has " +
                               cb.numChecksums() + " instead of " +
                               numChecksums);
            System.out.println(cb);
            System.exit(1);
        }
        System.out.println(cb);
        cb.dispose();
        pool.checkInBuffer(buffer);
        System.out.println("Checksum algorithm insert...[PASSED]");

        //
        // Test checksum block lengths
        //
        int[][] inputOutputArray = {
            {0, 4},
            {1, (4+4)},
            {4096, (4+4)},
            {4097, (4+4+4)},
            {4128768, (4+(4*1008))}
        };

        int input;
        int output;
        for (int i=0; i<inputOutputArray.length; i++) {
            input = inputOutputArray[i][0];
            output = inputOutputArray[i][1];
            if (ChecksumAlgorithm.getChecksumBlockLengthForData
                (ChecksumAlgorithm.ADLER32, input) != output) {
                System.out.println
                    ("FAILED: Expected " + output + " got " +
                     ChecksumAlgorithm.getChecksumBlockLengthForData
                     (ChecksumAlgorithm.ADLER32, input));
                System.exit(1);
            }
        }

        boolean exception = false;
        try {
            ChecksumAlgorithm.
                getChecksumBlockLengthForData(ChecksumAlgorithm.ADLER32,
                                              4128769);
        } catch (IllegalArgumentException e) {
            exception = true;
        }
        if (!exception) {
            System.out.println("FAILED: Didn't encounter expected exception");
            System.exit(1);
        }
        System.out.println("Checksum block length...[PASSED]");

        //
        // Test encoding decoding
        //
    }
}
