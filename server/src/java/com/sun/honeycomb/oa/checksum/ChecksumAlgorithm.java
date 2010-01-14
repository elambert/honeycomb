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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.resources.ByteBufferList;

/**
 * Abstract class for a checksum algorithm. This class provides the methods
 * to create specific checksum algorithm instances which can be used to
 * calculate checksums for data blocks.
 */
public abstract class ChecksumAlgorithm {
    /** The data block size of which the checksum will be calculated */
    public static final int DATA_BLOCK_SIZE = 4*1024;

    /** Predefined algorithms */
    public static final short NONE = 0;
    public static final short ADLER32 = 1;
    private static final short MAX_ALGORITHM_INDEX = 1; // Increment this 

    /** String names of the algorithms */
    private static String[] algorithmNames = {
        "NONE",
        "ADLER32"
    };
    private static String unknownAlgorithmString = "UNKNOWN ALGORITHM";

    /** Logger */
    private static final Logger logger = 
        Logger.getLogger(ChecksumAlgorithm.class.getName());

    /**
     * Method to get the name for an algorithm.
     *
     * @param algorithm the algorithm number
     * @return String the name of the algorithm
     */
    public static String getName(short algorithm) {
        if (algorithm >= algorithmNames.length) {
            return unknownAlgorithmString;
        }
        return algorithmNames[algorithm];
    }

    /**
     * Method to get the algorithm index from a string name. The string
     * comaprison ignores the case.
     *
     * @param algorithmName the string name of the algorithm
     * @return short the algorithm index
     */
    public static short getAlgorithm(String algorithmName) {
        if (algorithmName.equalsIgnoreCase(algorithmNames[NONE])) {
            return NONE;
        } else if (algorithmName.equalsIgnoreCase(algorithmNames[ADLER32])) {
            return ADLER32;
        } else {
            throw new IllegalArgumentException("Unknown algorithm type " +
                                               algorithmName);
        }
    }

    /**
     * Factory method to create a checksum algorithm processor.
     *
     * @param algorithm the algorithm to use
     * @return ChecksumAlgorithm the instance of the algorithm
     */
    public static ChecksumAlgorithm getInstance(int algorithm)
        throws IllegalArgumentException {
        if (algorithm == ChecksumAlgorithm.ADLER32) {
            return Adler32Algorithm.getInstance();
        } else {
            throw new IllegalArgumentException("Unknown algorithm type " +
                                               algorithm);
        }
    }

    /**
     * Factory method to create a checksum algorithm processor.
     *
     * @param context a previously created checksum context
     * @return ChecksumAlgorithm the instance of the algorithm
     */
    public static ChecksumAlgorithm getInstance(ChecksumContext context)
        throws IllegalArgumentException {
        if (context.algorithm == ChecksumAlgorithm.ADLER32) {
            return Adler32Algorithm.getInstance();
        } else {
            throw new IllegalArgumentException("Unknown algorithm type " +
                                               context.algorithm);
        }
    }

    /**
     * Method to find out the size of the data covered by a checksum block of
     * a given algorithm.
     *
     * @param algorithm the checksum algorithm to use
     * @param dataBytesPerChecksum the number of data bytes covered by each
     *                             checksum entry
     * @return int the size in bytes of the data covered by one checksum block
     */
    public static int getDataSizeCovered(short algorithm) {
        int checksumSize = getInstance(algorithm).getChecksumSize();
        return (DATA_BLOCK_SIZE *
                ChecksumBlock.getMaxChecksums(checksumSize, DATA_BLOCK_SIZE));
    }

    /**
     * Method to get the length of a checksum block for a given data size.
     *
     * @param algorithm the checksum algorithm to use
     * @param dataSize the size of the data
     * @return int the number of valid bytes in the checksum block
     */
    public static int getChecksumBlockLengthForData(short algorithm,
                                                    int dataSize) {
        if (dataSize > getDataSizeCovered(algorithm)) {
            throw new IllegalArgumentException
                ("Size of data [" + dataSize + "] is more than the maximum " +
                 "size covered [" + getDataSizeCovered(algorithm) +
                 "] by a checksum block for the checksum algorithm " +
                 getName(algorithm));
        }
        int checksumSize = getInstance(algorithm).getChecksumSize();
        return ChecksumBlock.getChecksumBlockLengthForData(checksumSize,
                                                           dataSize,
                                                           DATA_BLOCK_SIZE);
    }

    /**
     * Method to create an instance of the internal checksum state.
     *
     * @return AlgorithmState a new instance of the internal state
     */
    public AlgorithmState createAlgorithmState() {
        AlgorithmState state = new AlgorithmState();
        initialize(state);
        return state;
    }

    /**
     * Factory method to create a algorithm specific checksum block to be
     * used to store the checksums of data.
     *
     * @return the algorithm specific checksum block
     */
    public ChecksumBlock createChecksumBlock() {
        return ChecksumBlock.createInstance(getChecksumSize(),
                                            DATA_BLOCK_SIZE);
    }

    /**
     * Factory method to create a algorithm specific checksum block to be
     * used to store the checksums of data.
     *
     * @param 
     * @return the algorithm specific checksum block
     */
    public ChecksumBlock createChecksumBlock(ByteBuffer blockBuffer) {
        return ChecksumBlock.createInstance(getChecksumSize(),
                                            blockBuffer,
                                            DATA_BLOCK_SIZE);
    }

    /**
     * Method to get the length of a checksum block. This covers the reserved
     * bytes and the valid checksums in the block.
     *
     * @param numChecksums the number of valid checksums
     * @return int the number of valid bytes in the checksum block
     */
    public int getChecksumBlockLength(int numChecksums) {
        return ChecksumBlock.getChecksumBlockLength(getChecksumSize(),
                                                    numChecksums);
    }

    /**
     * Method to calculate the checksum of a set of data buffers. This method
     * also creates and fills the checksum blocks for the data in the given
     * context.
     *
     * @param buffers the list of data buffers to process
     * @param context the context that contains the checksum blocks
     */
    public void update(ByteBufferList buffers, ChecksumContext context) {
        // Return if the length is zero or less
        if (buffers.remaining() == 0) {
            return;
        }

        ByteBuffer[] array = buffers.getBuffers();

        // Call the update method with single ByteBuffers
        //
        // TBD: The native method should take an array of byte buffers and the
        //      checksum block to fill. This will reduce the number of native
        //      calls.
        int bytesProcessed = 0;
        int toProcess = 0;
        AlgorithmState internalState = createAlgorithmState();
        ChecksumBlock checksumBlock = null;
        for (int i=0; i<array.length; i++) {
            while (array[i].hasRemaining()) {
                // Calculate the amount of data to process. This the lesser
                // of the DATA_BLOCK_SIZE or the number of remaining bytes
                // in the current buffer.
                if ((bytesProcessed + array[i].remaining()) >
                    DATA_BLOCK_SIZE) {
                    toProcess = DATA_BLOCK_SIZE - bytesProcessed;
                } else {
                    toProcess = array[i].remaining();
                }

                // Process the current buffer and increment the number of
                // processed bytes.
                update(array[i], toProcess, internalState);
                bytesProcessed += toProcess;

                // For each block size of data
                // 1. If the checksum block is full, create a new block and
                //    store it in the context.
                // 2. Store the checksum in the checksum block
                if (bytesProcessed == DATA_BLOCK_SIZE) {
                    // Get the checksum block if it is null
                    if (checksumBlock == null) {
                        if (context.checksumBlocks.size() != 0) {
                            // Get the last checksum block
                            checksumBlock =
                                (ChecksumBlock)context.checksumBlocks.get
                                (context.checksumBlocks.size() - 1);
                        } else {
                            checksumBlock = createChecksumBlock();
                            context.checksumBlocks.add(checksumBlock);
                        }
                    }

                    // Create a new checksum block if the current one is
                    // full.
                    if (checksumBlock.isFull()) {
                        checksumBlock = createChecksumBlock();
                        context.checksumBlocks.add(checksumBlock);
                    }

                    // Insert the checksum in the checksum block
                    checksumBlock.insertChecksum(this, internalState);

                    // Reset the internal checksum state and the number of
                    // processed bytes.
                    initialize(internalState);
                    bytesProcessed = 0;
                }
            }
        }

        // Insert the last checksum if the bytes processed is not zero. This is
        // if the buffer is not a multiple of DATA_BLOCK_SIZE bytes.
        if (bytesProcessed != 0) {
            // Get the checksum block if it is null
            if (checksumBlock == null) {
                if (context.checksumBlocks.size() != 0) {
                    // Get the last checksum block
                    checksumBlock =
                        (ChecksumBlock)context.checksumBlocks.get
                        (context.checksumBlocks.size() - 1);
                } else {
                    checksumBlock = createChecksumBlock();
                    context.checksumBlocks.add(checksumBlock);
                }
            }

            // Create a new checksum block if the current one is full
            if (checksumBlock.isFull()) {
                checksumBlock = createChecksumBlock();
                context.checksumBlocks.add(checksumBlock);
            }

            // Insert the checksum in the checksum block
            checksumBlock.insertChecksum(this, internalState);
        }

        // TBD: [This should never happen]
        // What about data blocks that are not OA block size aligned but are
        // "N" byte aligned, e.g. If the writes are 65K each, then the
        // checksum for the extra 1K for the first write should be used to
        // compute the checksum for the next 3K in the next write so that it
        // is collectively 4K again. Need to check if writes can come this
        // way. This can be done by keeping the non-4K checksums and the
        // "bytesProcessed" in the context instead of the checksum block and
        // using it for the next set of calculations.
    }

    /**
     * Method to verify a data block's integrity. The provided checksum block
     * has the corresponding checksums to verify with.
     *
     * @param buffer the data buffer to verify
     * @param startIndex the offset of the first checksum in the checksum
     *                   block
     * @param checksumBlock the checksum block with all the relevant checksums
     * @return boolean true if the data is correct false otherwise
     */
    public boolean verify(ByteBuffer buffer,
                          int startIndex,
                          ChecksumBlock checksumBlock)
        throws ObjectCorruptedException {
        boolean consistent = true;
        AlgorithmState internalState = createAlgorithmState();
        int toProcess = DATA_BLOCK_SIZE;
        while(buffer.hasRemaining()) {
            // Calculate the amount of data to process. This the lesser
            // of the DATA_BLOCK_SIZE or the number of remaining bytes
            // in the current buffer.
            if (buffer.remaining() < DATA_BLOCK_SIZE) {
                toProcess = buffer.remaining();
            }

            // Find the checksum for the current buffer and compare it with
            // the value stored in the checksum block.
            update(buffer, toProcess, internalState);
            checksumBlock.equals(this, internalState, startIndex);

            // Reset the internal checksum state
            initialize(internalState);
            startIndex++;
        }

        return consistent;
    }

    /**
     * Method to calculate a data block's checksums and insert it into the
     * given checksum block.
     *
     * @param buffer the data buffer to process
     * @param startIndex the offset of the first checksum to be inserted in
     *                   the checksum block
     * @param checksumBlock the checksum block to insert in
     */
    public void insert(ByteBuffer buffer,
                       int startIndex,
                       ChecksumBlock checksumBlock) {
        AlgorithmState internalState = createAlgorithmState();
        int toProcess = DATA_BLOCK_SIZE;
        while(buffer.hasRemaining()) {
            // Calculate the amount of data to process. This the lesser
            // of the DATA_BLOCK_SIZE or the number of remaining bytes
            // in the current buffer.
            if (buffer.remaining() < DATA_BLOCK_SIZE) {
                toProcess = buffer.remaining();
            }

            // Find the checksum for the current buffer and compare it with
            // the value stored in the checksum block.
            update(buffer, toProcess, internalState);
            checksumBlock.insertChecksum(this, internalState, startIndex);

            // Update the variables for the next iteration
            startIndex++;
            initialize(internalState);
        }
    }

    /**
     * Interface to create a context for the checksum algorithm which
     * represents its internal state.
     *
     * @return ChecksumContext the codable internal state
     */
    public abstract ChecksumContext createContext();

    public abstract long getLongValue(AlgorithmState internalState);

    public abstract int getIntValue(AlgorithmState internalState);

    public abstract short getShortValue(AlgorithmState internalState);

    public abstract byte getByteValue(AlgorithmState internalState);

    public abstract int getChecksumSize();

    public abstract void initialize(AlgorithmState internalState);

    public abstract void update(ByteBuffer buffer,
                                int length,
                                AlgorithmState internalState);
}
