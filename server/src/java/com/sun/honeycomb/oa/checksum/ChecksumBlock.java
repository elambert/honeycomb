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
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.coordinator.Disposable;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.common.ObjectCorruptedException;

/**
 * Class representing a checksum block on disk. The layout of the block is
 * shown in the figure below:

     Byte offset => 0-------------------------------  -
                    |         Block checksum        |  |
                    2-------------------------------   |
                    |   Number of valid checksums   |  |
                    4-------------------------------   |
                    |              C1               |  |
                     -------------------------------   |
  Checksum (Cs) -{  |              C2               |  |
     size            -------------------------------   |- Block size (Bs)
                    |                               |  |
                     -            ....             -   |
                    |                               |  |
                     -------------------------------   |
                    |              Cn               |  |
                     -------------------------------  -
*/
public class ChecksumBlock implements Disposable, Codable {
    //
    // PRIVATE STATIC CONSTANTS
    //

    private static final Logger logger = 
        Logger.getLogger(ChecksumBlock.class.getName());
    private static final int BLOCK_CHECKSUM_OFFSET = 0;
    private static final int NUM_CHECKSUMS_OFFSET = 2;
    private static final byte[] zeroArray = createZeroArray();

    //
    // PUBLIC MEMBERS
    //

    /** Size of the checksum block */
    public static final int checksumBlockSize = 4*1024;

    /**
     * Factor by which the data covered by a checksum block needs to be a
     * multiple of.
     */
    public static final int dataBlockFactor = 64*1024;

    /**
     * Number of reserved bytes in the checksum block. The breakup is :
     * 2 bytes for the checksum of the checksum block.
     * 2 bytes for the number of valid checksums in the block
     */
    public static final int reservedChecksumBlockBytes = 4;

    /** The byte order of the checksum block */
    public static final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;

    /** The maximum supported checksum size */
    public static final int maximumChecksumSize = 8;

    /** A blank readonly checksumBlock buffer */
    public static final ByteBuffer blankChecksumBlock =
        createInstance(maximumChecksumSize, checksumBlockSize).getBuffer();

    //
    // PRIVATE MEMBERS
    //

    private short blockChecksum = 0;
    private ByteBuffer bytes = null;
    private int checksumSize = 0;
    private int byteIndexLimit = 0;
    private int currentByteIndex = 0;

    //
    // PUBLIC METHODS
    //

    /**
     * Factory method to create a checksum block.
     *
     * @param int checksumSize the size of the checksums to store
     * @return ChecksumBlock a new instance of the checksum block
     */
    public static ChecksumBlock createInstance(int checksumSize,
                                               int dataBytesPerChecksum)
        throws IllegalArgumentException {
        if (checksumSize > maximumChecksumSize) {
            throw new IllegalArgumentException("Maximum checksum size is " +
                                               maximumChecksumSize + " bytes");
        } else {
            return new ChecksumBlock(checksumSize, dataBytesPerChecksum);
        }
    }

    /**
     * Factory method to create a checksum block.
     *
     * @param int checksumSize the size of the checksums to store
     * @param ByteBuffer the preallocated cheksum bytes
     * @return ChecksumBlock a new instance of the checksum block
     */
    public static ChecksumBlock createInstance(int checksumSize,
                                               ByteBuffer checksumBlockBytes,
                                               int dataBytesPerChecksum)
        throws IllegalArgumentException {
        if (checksumSize > maximumChecksumSize) {
            throw new IllegalArgumentException("Maximum checksum size is " +
                                               maximumChecksumSize + " bytes");
        } else if (checksumBlockBytes.capacity() > checksumBlockSize) {
            throw new IllegalArgumentException
                ("The checksum buffer size [" + checksumBlockBytes.capacity() +
                 "] is greater than the maximum block size [" +
                 checksumBlockSize + "]");
        } else {
            return new ChecksumBlock(checksumSize,
                                     checksumBlockBytes,
                                     dataBytesPerChecksum);
        }
    }

    /**
     * Method to find out the maximum number of checksums that a checksum
     * block of a specified algorithm can store. This is the maximum number of
     * entries that can cover data blocks that are a multiple of the data block
     * factor.
     *
     * @param algorithm the specified checksum algorithm to use
     * @param dataBytesPerChecksum the number of data bytes covered by each
     *                             checksum entry
     * @return int the size in bytes of the data covered by one checksum block
     */
    public static int getMaxChecksums(int size, int dataBytesPerChecksum) {
        // The maximum number of entries supported by the fixed size checksum
        // block is the available size of the checksum block
        // (block size - reserved) divided by the size of each checksum entry.
        int maxEntries = (checksumBlockSize - reservedChecksumBlockBytes)/size;

        // The number of checksum entries that will cover one data block
        // factor.
        int entriesPerDataBlockFactor = dataBlockFactor/dataBytesPerChecksum;

        // The required max entries will be less than or equal to the real max
        // entries.
        return maxEntries - (maxEntries%entriesPerDataBlockFactor);
    }

    /**
     * Method to get the length of a checksum block. This covers the reserved
     * bytes and the valid checksums in the block.
     *
     * @param checksumSize the size of each checksum entry
     * @param numChecksums the number of valid checksums
     * @return int the number of valid bytes in the checksum block
     */
    public static int getChecksumBlockLength(int checksumSize,
                                             int numChecksums) {
        return (reservedChecksumBlockBytes + (checksumSize * numChecksums));
    }

    /**
     * Method to get the length of a checksum block for a given data size.
     * This covers the reserved bytes and the valid checksums in the block.
     *
     * @param checksumSize the size of each checksum entry
     * @param dataSize the size of the data
     * @param dataSizePerChecksum the max size of the data covered by each
     *                            checksum entry
     * @return int the number of valid bytes in the checksum block
     */
    public static int getChecksumBlockLengthForData(int checksumSize,
                                                    int dataSize,
                                                    int dataSizePerChecksum) {
        int numChecksums = dataSize / dataSizePerChecksum;
        if ((dataSize % dataSizePerChecksum) != 0) {
            numChecksums++;
        }
        return (reservedChecksumBlockBytes + (checksumSize * numChecksums));
    }

    public static void initializeChecksumBuffer(ByteBuffer buffer) {
        buffer.put(zeroArray);
        buffer.rewind();
    }

    /**
     * Method to return the number of valid checksums in the checksum block.
     *
     * @return short the number of valid checksums
     */
    public short numChecksums() {
        return bytes.getShort(NUM_CHECKSUMS_OFFSET);
    }

    /**
     * Method to find out if the checksum block is full.
     *
     * @return boolean true if there is no more space for more checksums
     */
    public boolean isFull() {
        return ((currentByteIndex + checksumSize) > byteIndexLimit);
    }

    /**
     * Method to insert a checksum in the block at a given offset. This method
     * ensures that the number of valid checksums are correctly incremented if
     * the offset is beyond the current valid offset but within the limits of
     * the checksum block.
     *
     * @param algorithm the checksum algorithm to use
     * @param internalState the algorithm's internal state
     * @param checksumIndex the index to insert the checksum at
     */
    public void insertChecksum(ChecksumAlgorithm algorithm,
                               AlgorithmState internalState,
                               int checksumIndex) {
        // Check to make sure that there is enough space in the checksum
        // block to insert a checksum
        int insertOffset =
            reservedChecksumBlockBytes + (checksumIndex * checksumSize);
        if (insertOffset> byteIndexLimit) {
            throw new IndexOutOfBoundsException
                ("Cannot insert checksum beyond index = " + byteIndexLimit);
        }

        switch(checksumSize) {
        case 1:
            bytes.put(insertOffset, algorithm.getByteValue(internalState));
            break;
        case 2:
            bytes.putShort(insertOffset,
                           algorithm.getShortValue(internalState));
            break;
        case 4:
            bytes.putInt(insertOffset, algorithm.getIntValue(internalState));
            break;
        case 8:
            bytes.putLong(insertOffset, algorithm.getLongValue(internalState));
            break;
        default:
            throw new IllegalArgumentException("Invalid checksum size " +
                                               checksumSize);
        }

        // Set the num valid checksums if it is lesser
        if (numChecksums() <= checksumIndex) {
            setNumChecksums(checksumIndex + 1);
        }
    }

    /**
     * Interface to insert the checksum value into a checksum block.
     *
     * @param algorithm the checksum algorithm to use
     * @param internalState the algorithm's internal state
     */
    public void insertChecksum(ChecksumAlgorithm algorithm,
                               AlgorithmState internalState) {
        // Check to make sure that there is enough space in the checksum
        // block to insert a checksum
        if ((currentByteIndex + checksumSize) > byteIndexLimit) {
            throw new IndexOutOfBoundsException("Checksum block full");
        }

        switch(checksumSize) {
        case 1:
            bytes.put(currentByteIndex,
                      algorithm.getByteValue(internalState));
            break;
        case 2:
            bytes.putShort(currentByteIndex,
                           algorithm.getShortValue(internalState));
            break;
        case 4:
            bytes.putInt(currentByteIndex,
                         algorithm.getIntValue(internalState));
            break;
        case 8:
            bytes.putLong(currentByteIndex,
                          algorithm.getLongValue(internalState));
            break;
        default:
            throw new IllegalArgumentException("Invalid checksum size " +
                                               checksumSize);
        }

        // Increment the current byte index and set the new valid checksum
        currentByteIndex += checksumSize;
        setNumChecksums();
    }

    /**
     * Method to compare the value of a checksum in the checksum block
     *
     * @param algorithm the checksum algorithm to use
     * @param internalState the algorithm's internal state
     * @return boolean
     */
    public boolean equals(ChecksumAlgorithm algorithm,
                          AlgorithmState internalState,
                          int checksumIndex) throws ObjectCorruptedException {
        // Check to make sure that the checksum index is valid
        if ((checksumIndex < 0) ||
            (checksumIndex > (int)numChecksums())) {
            String err = "CHECKSUM MISMATCH. Checksum index [" +
                checksumIndex + "] is not valid in block with [" +
                bytes.getShort(NUM_CHECKSUMS_OFFSET) +
                "] valid checksums";
            throw new ObjectCorruptedException(err);
        }

        boolean retval = true;
        int checksumOffset =
            reservedChecksumBlockBytes + (checksumSize*checksumIndex);

        switch(checksumSize) {
        case 1:
            retval = check(algorithm.getByteValue(internalState),
                           checksumOffset);
            break;
        case 2:
            retval = check(algorithm.getShortValue(internalState),
                           checksumOffset);
            break;
        case 4:
            retval = check(algorithm.getIntValue(internalState),
                           checksumOffset);
            break;
        case 8:
            retval = check(algorithm.getLongValue(internalState),
                           checksumOffset);
            break;
        default:
            retval = false;
        }

        return retval;
    }

    /**
     * Method to get a buffer representation of the checksum block.
     *
     * @return ByteBuffer the readonly buffer representation
     */
    public ByteBuffer getBuffer() {
        setBlockChecksum(calculateBlockChecksum());
        ByteBuffer buf = 
            ByteBufferPool.getInstance().checkOutReadOnlyBuffer(bytes);
        buf.position(0);
        buf.limit(checksumBlockSize);
        return buf;
    }

    /**
     * Method to get a buffer representation of the checksum portion of the
     * block. This includes the block checksum and the number of valid
     * checksums. It excludes any unused space in the block.
     *
     * @return ByteBuffer the readonly buffer representation of the valid
     *                    checksums
     */
    public ByteBuffer getChecksumBuffer() {
        ByteBuffer buf = getBuffer();
        buf.limit(reservedChecksumBlockBytes + (numChecksums()*checksumSize));
        return buf;
    }

    public void encode(Encoder encoder) {
        encoder.encodeShort(blockChecksum);
        encoder.encodeInt(checksumSize);
        encoder.encodeInt(byteIndexLimit);
        encoder.encodeInt(currentByteIndex);
        ByteBuffer buf = getBuffer();
        encoder.encodeKnownLengthBuffer(buf);
        ByteBufferPool.getInstance().checkInBuffer(buf);
    }

    public void decode(Decoder decoder) {
        blockChecksum = decoder.decodeShort();
        checksumSize = decoder.decodeInt();
        byteIndexLimit = decoder.decodeInt();
        currentByteIndex = decoder.decodeInt();
        bytes = decoder.decodeKnownLengthBuffer(checksumBlockSize, 
                                                true);
        bytes.order(byteOrder);
    }
    
    /**
     * Method to dispose the checksum block's byte buffer
     */
    public void dispose() {
        ByteBufferPool.getInstance().checkInBuffer(this.bytes);
    }

    /**
     * Method to compare a checksum block with this instance.
     *
     * @return boolean true if the blocks are equal false otherwise
     */
    public boolean equals(Object ob) {
        ChecksumBlock toCompare = (ChecksumBlock)ob;
        ByteBuffer thisBuffer = this.getBuffer();
        ByteBuffer bufferToCompare = toCompare.getBuffer();
        boolean res = thisBuffer.equals(bufferToCompare);
        ByteBufferPool.getInstance().checkInBuffer(thisBuffer);
        ByteBufferPool.getInstance().checkInBuffer(bufferToCompare);
        return res;
    }

    public String toString() {
        return new String("[checksumSize = " + checksumSize +
                          "] [numChecksums = " + numChecksums() +
                          "] [byteIndexLimit = " + byteIndexLimit +
                          "] [currentByteIndex = " + currentByteIndex + "]");
    }

    // Public for encode/decode for context write/restore
    public ChecksumBlock() {
    }
    

    //
    // PRIVATE METHODS
    //

    private static byte[] createZeroArray() {
        byte[] array = new byte[checksumBlockSize];
        Arrays.fill(array, (byte)0);
        return array;
    }

    private ChecksumBlock(int checksumSize, int dataBytesPerChecksum) {
        bytes = ByteBufferPool.getInstance().checkOutBuffer(checksumBlockSize);
        try {
            initialize(checksumSize, true, dataBytesPerChecksum);
        } catch (IllegalArgumentException e) {
            ByteBufferPool.getInstance().checkInBuffer(bytes);
            throw e;
        }
    }

    private ChecksumBlock(int checksumSize,
                          ByteBuffer checksumBlockBytes,
                          int dataBytesPerChecksum) {
        bytes =
            ByteBufferPool.getInstance().checkOutDuplicate(checksumBlockBytes);
        try {
            initialize(checksumSize, false, dataBytesPerChecksum);
        } catch (IllegalArgumentException e) {
            ByteBufferPool.getInstance().checkInBuffer(bytes);
            throw e;
        }
    }

    /**
     * Method to check a checksum (byte) with one in the block.
     *
     * @param checksum the checksum value to check
     * @param checksumOffset the offset of the checksum in the block
     * @return boolean true if the checksums match false otherwise
     */
    private boolean check(byte checksum, int checksumOffset)
        throws ObjectCorruptedException {
        if(checksum != bytes.get(checksumOffset)) {
            String err = "CHECKSUM MISMATCH. Expected [" +
                Long.toHexString(checksum) + "] got [" +
                Long.toHexString(bytes.get(checksumOffset)) + "]";
            throw new ObjectCorruptedException(err);
        }
        return true;
    }

    /**
     * Method to check a checksum (short) with one in the block.
     *
     * @param checksum the checksum value to check
     * @param checksumOffset the offset of the checksum in the block
     * @return boolean true if the checksums match false otherwise
     */
    private boolean check(short checksum, int checksumOffset)
        throws ObjectCorruptedException {
        if(checksum != bytes.getShort(checksumOffset)) {
            String err = "CHECKSUM MISMATCH. Expected [" +
                Long.toHexString(checksum) + "] got [" +
                Long.toHexString(bytes.getShort(checksumOffset)) + "]";
            throw new ObjectCorruptedException(err);
        }
        return true;
    }

    /**
     * Method to check a checksum (int) with one in the block.
     *
     * @param checksum the checksum value to check
     * @param checksumOffset the offset of the checksum in the block
     * @return boolean true if the checksums match false otherwise
     */
    private boolean check(int checksum, int checksumOffset)
        throws ObjectCorruptedException {
        if(checksum != bytes.getInt(checksumOffset)) {
            String err = "CHECKSUM MISMATCH. Expected [" +
                Long.toHexString(checksum) + "] got [" +
                Long.toHexString(bytes.getInt(checksumOffset)) + "]";
            throw new ObjectCorruptedException(err);
        }
        return true;
    }

    /**
     * Method to check a checksum (long) with one in the block.
     *
     * @param checksum the checksum value to check
     * @param checksumOffset the offset of the checksum in the block
     * @return boolean true if the checksums match false otherwise
     */
    private boolean check(long checksum, int checksumOffset)
        throws ObjectCorruptedException {
        if(checksum != bytes.getLong(checksumOffset)) {
            String err = "CHECKSUM MISMATCH. Expected [" +
                Long.toHexString(checksum) + "] got [" +
                Long.toHexString(bytes.getLong(checksumOffset)) + "]";
            throw new ObjectCorruptedException(err);
        }
        return true;
    }

    private short getBlockChecksum() {
        return bytes.getShort(BLOCK_CHECKSUM_OFFSET);
    }

    private void setBlockChecksum(short checksum) {
        bytes.putShort(BLOCK_CHECKSUM_OFFSET, calculateBlockChecksum());
    }

    private short calculateBlockChecksum() {
        ByteBuffer duplicate = 
            ByteBufferPool.getInstance().checkOutDuplicate(bytes);
        duplicate.position(NUM_CHECKSUMS_OFFSET);

        ChecksumAlgorithm algorithm =
            ChecksumAlgorithm.getInstance(ChecksumAlgorithm.ADLER32);
        AlgorithmState internalState = algorithm.createAlgorithmState();
        algorithm.update(duplicate,
                         (checksumBlockSize - NUM_CHECKSUMS_OFFSET),
                         internalState);
        ByteBufferPool.getInstance().checkInBuffer(duplicate);
        return algorithm.getShortValue(internalState);
    }

    private void initialize(int checksumSize,
                            boolean initBytes,
                            int dataBytesPerChecksum) {
        this.checksumSize = checksumSize;
        byteIndexLimit = reservedChecksumBlockBytes +
            (getMaxChecksums(checksumSize,
                             dataBytesPerChecksum) * checksumSize);
        blockChecksum = 0;
        currentByteIndex = reservedChecksumBlockBytes;
        bytes.order(byteOrder);

        // Initialize the byte array if required
        if (initBytes) {
            bytes.put(zeroArray);
            setNumChecksums();
        } else {
            // Verify that the checksum matches the buffer's
            if (calculateBlockChecksum() != getBlockChecksum()) {
                throw new IllegalArgumentException
                    ("Checksum of the initializing buffer [" +
                     Integer.toHexString(calculateBlockChecksum()) +
                     "] does not match the stored value [" +
                     Integer.toHexString(getBlockChecksum()) + "]");
            }
        }
    }

    private void setNumChecksums() {
        int numChecksums =
            (currentByteIndex - reservedChecksumBlockBytes)/checksumSize;
        bytes.putShort(NUM_CHECKSUMS_OFFSET, (short)numChecksums);
    }

    private void setNumChecksums(int num) {
        bytes.putShort(NUM_CHECKSUMS_OFFSET, (short)num);
    }
}
