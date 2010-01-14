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
import java.util.ArrayList;

import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coordinator.Disposable;
import com.sun.honeycomb.resources.ByteBufferPool;
import java.util.logging.Logger;

/**
 * Class implementing the checksum context. This is used to store the state
 * of a checksum calculation. It stores all the checksum blocks and the
 * algoritm to use. In addition, it also stores fields that help in writing
 * the checksum blocks to a file.
 */
public class ChecksumContext implements Codable, Disposable {
    /** Checksum algorithm to use */
    public short algorithm = 0;

    /** The offset of the reserved checksum block */
    public long reservedChecksumBlockOffset = 0;

    /** The list of stored checksum blocks */
    public ArrayList checksumBlocks = new ArrayList();

    /** Read checksum block and its offset */
    public long dataStartOffset = 0;
    public long firstChecksumBlockOffset = 0;
    public ChecksumBlock readChecksumBlock = null;

    // The size of the data covered by one checksum block
    private long dataSizeCovered = 0;

    protected static final Logger LOG = 
        Logger.getLogger(ChecksumContext.class.getName());

    /**
     * Default constructor.
     */
    public ChecksumContext() {
    }

    /**
     * Constructor taking in the checksum algorithm to use.
     *
     * @param algorithm the checksum algorithm to use
     */
    public ChecksumContext(short algorithm) {
        this.algorithm = algorithm;
        dataSizeCovered = ChecksumAlgorithm.getDataSizeCovered(algorithm);
    }

    /**
     * Method to increment the reserved checksum block offset.
     */
    public void incrementReservedChecksumBlockOffset() {
        // The first checksum block is "dataSizeCovered" bytes into the
        // file. The next are offset by "dataSizeCovered+checksum block" size
        // bytes.
        if (reservedChecksumBlockOffset == 0) {
            reservedChecksumBlockOffset = dataSizeCovered;
        } else {
            reservedChecksumBlockOffset +=
                (dataSizeCovered + ChecksumBlock.checksumBlockSize) ;
        }
    }

    /**
     * Method to return the next checksum block offset.
     *
     * @return long the offset of the next checksum block
     */
    public long getNextChecksumBlockOffset() {
        if (reservedChecksumBlockOffset == 0) {
            return (reservedChecksumBlockOffset + dataSizeCovered);
        } else {
            return (reservedChecksumBlockOffset +
                dataSizeCovered +
                ChecksumBlock.checksumBlockSize);
        }
    }

    /**
     * Method to remap an offset with respect to the embedded checksum
     * blocks.
     *
     * @param logicalOffset the offset to map
     * @return long the remapped offset
     */
    public long remapOffset(long logicalOffset) {
        // (n*dataSizeCovered) -> (((n+1)*dataSizeCovered) - 1) =>
        // (logicalOffset + (n*checksumBlockSize)), (n*dataSizeCovered)
        long n = logicalOffset/dataSizeCovered;
        return (logicalOffset + (n*ChecksumBlock.checksumBlockSize));
    }

    /**
     * Method to indicate if an offset and length is covered by the cached
     * read checksum block.
     *
     * @param remappedOffset the remapped offset in the fragment file
     * @param length the length to check
     * @return true if the cached checksum block covers the data offset
     */
    public boolean hasChecksumBlock(long remappedOffset, long length) {
        return ((remappedOffset >= dataStartOffset) &&
                ((remappedOffset + length) <=
                 (dataStartOffset + dataSizeCovered)));
    }

    /**
     * Method to find out the checksum block offset for a particular
     * remapped offset in the fragment.
     *
     * @param remappedOffset the remapped offset in the fragment file
     * @return long the offset of the corresponding checksum block
     */
    public long getChecksumBlockOffset(long remappedOffset) {
        long checksumAndDataSize = ChecksumBlock.checksumBlockSize +
            dataSizeCovered;
        long n = remappedOffset / (long)checksumAndDataSize;
        return (checksumAndDataSize * n) - ChecksumBlock.checksumBlockSize;
    }

    /**
     * Method to set the read checksum block and the starting offset of the
     * data that it covers.
     *
     * @param checksumBlock the read checksum block null if the block is
     *                      invalid
     * @param offset the offset of the first data byte covered
     */
    public void setReadChecksumBlockAndOffset(ChecksumBlock checksumBlock,
                                              long offset) {
        if (readChecksumBlock != null) {
            readChecksumBlock.dispose();
        }
        readChecksumBlock = checksumBlock;
        dataStartOffset = offset;
    }

    /**
     * Method to get the start offset of the first checksum in a block for a
     * given data offset.
     *
     * @param remappedOffset the start offset of the data
     * @return int the start offset of the first checksum in the block
     */
    public int getOffsetInChecksumBlock(long remappedOffset) {
        return (int)
            (remappedOffset-dataStartOffset)/ChecksumAlgorithm.DATA_BLOCK_SIZE;
    }

    /**
     * Method to return the size of the data in the checksum context
     *
     * @return int the size of the data
     */
    public int size() {
        int size = 0;
        size += 2; // For 2 byte algorithm size (short)
        size += 8; // For 8 byte reservedChecksumBlock Offset (long)
        size += 4; // For 4 byte array list size (int)
        for (int i=0; i<checksumBlocks.size(); i++) {
            size += ChecksumBlock.checksumBlockSize;
        }
        return size;
    }

    /**
     * Method to encode the checksum context in to the provided encoder.
     *
     * @param encoder the encoder to use
     */
    public void encode(Encoder encoder) {
        // Encode the primitive members
        encoder.encodeShort(algorithm);
        encoder.encodeLong(reservedChecksumBlockOffset);
        encoder.encodeLong(dataStartOffset);

        // Encode the number of checksum blocks
        encoder.encodeInt(checksumBlocks.size());

        // Encode all the checksum blocks
        for (int i=0; i<checksumBlocks.size(); i++) {
            ChecksumBlock block = (ChecksumBlock)checksumBlocks.get(i);
            encoder.encodeKnownClassCodable(block);
        }
    }

    /**
     * Method to decode the checksum context from the provided decoder
     *
     * @param decode the decoder to use
     */
    public void decode(Decoder decoder) {
        // Decode the primitive numbers
        algorithm = decoder.decodeShort();
        reservedChecksumBlockOffset = decoder.decodeLong();
        dataStartOffset = decoder.decodeLong();
        dataSizeCovered = ChecksumAlgorithm.getDataSizeCovered(algorithm);

        // Decode the number of checksum blocks
        int numBlocks = decoder.decodeInt();

        ChecksumAlgorithm algo = ChecksumAlgorithm.getInstance(algorithm);
        ByteBuffer buffer;
        for (int i=0; i<numBlocks; i++) {
            ChecksumBlock block = new ChecksumBlock();
            decoder.decodeKnownClassCodable(block);
            checksumBlocks.add(block);
        }
    }

    public void dispose() {
        for (int i=0; i<checksumBlocks.size(); i++) {
            ChecksumBlock block = (ChecksumBlock)checksumBlocks.get(i);
            block.dispose();
        }
        if (readChecksumBlock != null) {
            readChecksumBlock.dispose();
        }
    }

    /**
     * Method to return the string representation of the checksum context.
     *
     * @param String the string representation of the internal context
     */
    public String toString() {
        StringBuffer buffer = new StringBuffer();

        buffer.append("Algorithm = " + algorithm + "\n");
        buffer.append("Reserved Checksum Block Offset = " + 
                      reservedChecksumBlockOffset + "\n");
        buffer.append("Data size covered by a block = " +
                      dataSizeCovered + "\n");

        buffer.append("Checksum blocks = " + checksumBlocks.size() + "\n");
        for (int i=0; i<checksumBlocks.size(); i++) {
            ChecksumBlock block = (ChecksumBlock)checksumBlocks.get(i);
            buffer.append("Block [" + i + "] checksums = " +
                          block.numChecksums() + "\n");
        }
        return buffer.toString();
    }

    /**
     * Method to compare this checksum block with another.
     *
     * @param ob the other checksum block to compare with
     * @return boolean true if all the fields are equal false otherwise
     */
    public boolean equals(Object ob) {
        ChecksumContext toCompare = (ChecksumContext)ob;
        if (this.algorithm != toCompare.algorithm) {
            return false;
        }
        if (this.reservedChecksumBlockOffset !=
            toCompare.reservedChecksumBlockOffset) {
            return false;
        }

        return this.checksumBlocks.equals(toCompare.checksumBlocks);
    }
}
