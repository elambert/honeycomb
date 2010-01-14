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

import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.resources.ByteBufferPool;

public class ChecksumContextTest {
    private static final int CODING_OVERHEAD = 1024;
    private static final boolean DEBUG = false;

    private static boolean testContextEncodingAndDecoding
        (ChecksumContext context) {
        if (DEBUG) {
            System.out.println(context);
        }

        int size = context.size();
        if (DEBUG) {
            System.out.println("Context size = " + size);
        }
        ByteBuffer buffer = ByteBufferPool.getInstance().checkOutBuffer
            (CODING_OVERHEAD + size);
        ByteBufferCoder encoder = new ByteBufferCoder(buffer, false);

        if (DEBUG) {
            System.out.println("Encoding context...");
        }
        encoder.encodeKnownClassCodable(context);
        buffer.flip();

        ByteBufferCoder decoder = new ByteBufferCoder(buffer, false);
        ChecksumContext anotherContext = new ChecksumContext();
        decoder.decodeKnownClassCodable(anotherContext);

        if (DEBUG) {
            System.out.println(anotherContext);
        }

        boolean retval = true;
        if (context.equals(anotherContext)) {
            if (DEBUG) {
                System.out.println("Both contexts match...");
            }
        } else {
            if (DEBUG) {
                System.out.println("The contexts differ...");
            }
            retval = false;
        }

        ByteBufferPool.getInstance().checkInBuffer(buffer);

        return retval;
    }

    private static boolean testOffsetRemapping(ChecksumContext context) {
        long[] originalOffsets = {
            0,       // First offset of data block 0
            4128767, // Last offset of data block 0
            4128768, // First offset of data block 1
            8257535, // Last offset of data block 1
            8257536  // First offset of data block 2
        };

        long[] remappedOffsets = {
            originalOffsets[0], // No change
            originalOffsets[1], // No change
            // offset + checksum block size
            originalOffsets[2] + ChecksumBlock.checksumBlockSize,
            // offset + checksum block size
            originalOffsets[3] + ChecksumBlock.checksumBlockSize,
            // offset + 2*checksum block size
            originalOffsets[4] + (2*ChecksumBlock.checksumBlockSize)
        };

        boolean retval = true;
        for (int i=0; i<originalOffsets.length; i++) {
            System.out.print("offset [" + originalOffsets[i] +
                               "] remapped => " +
                               context.remapOffset(originalOffsets[i]));
            if (context.remapOffset(originalOffsets[i]) ==
                remappedOffsets[i]) {
                System.out.println(" [PASSED]");
            } else {
                System.out.println(" [FAILED] [Should be " +
                                   remappedOffsets[i] + "]");
                retval = false;
            }
        }
        return retval;
    }

    private static boolean testChecksumBlockOffset(ChecksumContext context) {
        long[] offsets = {
            0,
            1024,
            4128768,
            8257535,
            8257536
        };

        for (int i=0; i<offsets.length; i++) {
            System.out.println("Data offset [" + offsets[i] +
                               "] checksum block offset [" +
                               context.getChecksumBlockOffset(offsets[i]) +
                               "]");
        }

        return true;
    }

    private static boolean testFakeRead(ChecksumContext context) {
        context.setReadChecksumBlockAndOffset(null, 0);
        long readSize = ChecksumBlock.dataBlockFactor;
        long maxReadOffset = readSize * 1024;

        long currentOffset = 0;
        long remappedOffset = 0;
        long checksumBlockOffset = 0;
        while (currentOffset < maxReadOffset) {
            remappedOffset = context.remapOffset(currentOffset);
            if (!context.hasChecksumBlock(remappedOffset, readSize)) {
                checksumBlockOffset =
                    context.getChecksumBlockOffset(remappedOffset);
                if (checksumBlockOffset !=
                    remappedOffset - ChecksumBlock.checksumBlockSize) {
                    System.out.println
                        ("Error at real offset [ " + currentOffset +
                         "] remappedOffset = " + remappedOffset +
                         "] wrong checksum block offset. Expected [" +
                         (remappedOffset - ChecksumBlock.checksumBlockSize) +
                         "] got [" + checksumBlockOffset);
                    return false;
                }
                context.setReadChecksumBlockAndOffset
                    (null,
                     checksumBlockOffset + ChecksumBlock.checksumBlockSize);
                continue;
            }
            currentOffset += readSize;
        }
        return true;
    }

    private static boolean testOffsetInChecksumBlock(ChecksumContext context) {
        long[] dataOffsets = {
            0,
            4096,
            8192
        };

        for (int i=0; i<dataOffsets.length; i++) {
            System.out.println
                (dataOffsets[i] + " => " +
                 context.getOffsetInChecksumBlock(dataOffsets[i]));
        }

        return true;
    }

    private static void testBasicEncoding() {
        ByteBuffer context = ByteBuffer.allocateDirect(4096);
        ByteBuffer buffer = ByteBuffer.allocateDirect(CODING_OVERHEAD + 4096);
        ByteBufferCoder encoder = new ByteBufferCoder(buffer, false);
        encoder.encodeKnownLengthBuffer(context);
    }

    public static void main(String[] args) {
        ChecksumAlgorithm algorithm =
            ChecksumAlgorithm.getInstance(ChecksumAlgorithm.ADLER32);
        ChecksumContext context = algorithm.createContext();
        System.out.println(context);

        System.out.println("Testing offset remapping");
        if (!testOffsetRemapping(context)) {
            System.out.println("\t[FAILED]");
        } else {
            System.out.println("\t[PASSED]");
        }

        System.out.println("Testing checksum block offset mapping");
        if (!testChecksumBlockOffset(context)) {
            System.out.println("\t[FAILED]");
        } else {
            System.out.println("\t[PASSED]");
        }

        System.out.print("Testing checksum block index mapping");
        if (!testOffsetInChecksumBlock(context)) {
            System.out.println("\t[FAILED]");
        } else {
            System.out.println("\t[PASSED]");
        }

        System.out.print("Testing fake read");
        if (!testFakeRead(context)) {
            System.out.println("\t[FAILED]");
        } else {
            System.out.println("\t[PASSED]");
        }

        System.out.println("Encoding/decoding with empty checksum context");
        try {
            if (!testContextEncodingAndDecoding(context)) {
                System.out.println("Test failed...");
            }
        } catch (Exception e) {
            System.out.println("Test failed...");
            e.printStackTrace();
            System.exit(1);
        }

        // Fill in one checksum block in the context and try again
        System.out.println("Encoding/decoding with one checksum block");
        context.checksumBlocks.add(algorithm.createChecksumBlock());
        try {
            if (!testContextEncodingAndDecoding(context)) {
                System.out.println("Test failed...");
            }
        } catch (Exception e) {
            System.out.println("Test failed...");
            e.printStackTrace();
            System.exit(1);
        }

        // Fill in some checksum blocks in the context and try again
        System.out.println("Encoding/decoding with multiple checksum blocks");
        for (int i=0; i<10; i++) {
            context.checksumBlocks.add(algorithm.createChecksumBlock());
        }
        try {
            if (!testContextEncodingAndDecoding(context)) {
                System.out.println("Test failed...");
            }
        } catch (Exception e) {
            System.out.println("Test failed...");
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("All tests passed...");
    }
}
