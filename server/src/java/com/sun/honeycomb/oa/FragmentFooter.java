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



package com.sun.honeycomb.oa;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;

import java.util.logging.Logger;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.CodingException;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.oa.checksum.AlgorithmState;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;
import com.sun.honeycomb.resources.ByteBufferPool;

/**
 * FragmentFooter layout
   TODO - Link is not a hash - it is an OID
   TODO - this diagram is now out of date - fix it

                      <--- 1 byte --->
       Byte offset => 0--------------1
                      |    version   |              
                      1-----------------------------
                      |          fragment           |
                      |         number (4)          |
                      5-----------------------------
                      |         layout map          |
                      |       identifier (4)        |
                      9-----------------------------
                      |           object            |
                      |                             |
                      |       reliability (8)       |
                      17----------------------------
                      |                             |
                      |       fragment hash         |
                      |                             |
                      |            (20)             |
                      |                             |
                      37----------------------------
                      |                             |
                      |          link hash          |
                      |                             |
                      |            (20)             |
                      |                             |
                      57----------------------------
                      |                             |
                      |        Fragment UID         |
                      |            (16)             |
                      |                             |
                      73----------------------------
                      |       creating time         |
                      |                             |
                      |            (8)              |
                      81----------------------------
                      |       retention time        |
                      |                             |
                      |            (8)              |
                      89----------------------------
                      |       expiration time       |
                      |                             |
                      |            (8)              |
                      99----------------------------
                      |        deletion time        |
                      |                             |
                      |            (8)              |
                      105-----------107-------------
                      |     shred    |     type     |
                      107---------------------------
                      |     checksum algorithm      |
                      109---------------------------
                      |   num preceeding checksums  |
                      111---------------------------
                      |       fragment size         |
                      |            (4)              |
                      115---------------------------
                      |         chunk size          |
                      |            (4)              |
                      119---------------------------
                      |         ref count           |
                      |            (4)              |
                      123---------------------------
                      |       max ref count         |
                      |            (4)              |
                      127---------------------------
                      |                             |
                      |        deleted refs         |
                      |        bloom filter         |
                      |                             |
                      |           (132)             |
                      |                             |
                      259---------------------------
                      |           footer            |
                      |         checksum (4)        |
                      263---------------------------
 */
public class FragmentFooter implements Codable {
    public static int METADATA_FIELD_LENGTH = 64;
    public static int OBJECT_CONTENT_HASH_LENGTH = 20;
    public static int DELETED_REFS_BLOOM_BITLENGTH = 1024; // 1024 bits, 128 bytes

    public static int REFCOUNT_OFFSET = 224; // 232 (all except bloom + checksum) - 4*2
    public static final byte version = 1;
    public int fragNum = 0;
    public NewObjectIdentifier oid = null;
    public NewObjectIdentifier linkoid = null;
    public ObjectReliability reliability = null;
    public long size = 0;
    public byte[] objectContentHash = new byte[OBJECT_CONTENT_HASH_LENGTH];
    public long creationTime = 0;
    public long retentionTime = 0;
    public long expirationTime = 0;
    public long autoCloseTime = -1;
    public long deletionTime = 0;
    public byte shred = 0;
    public byte[] metadataField = new byte[METADATA_FIELD_LENGTH];
    public int refCount = -1;
    public int maxRefCount = -1;
    public BitSet deletedRefs = new BitSet(DELETED_REFS_BLOOM_BITLENGTH);
    public short checksumAlg = 0;
    public short numPreceedingChecksums = 0;
    public int fragmentSize = 0;
    public int chunkSize = 0; // Stored as a factor of block size, not bytes!
    public int footerChecksum = 0;
    public static final int SIZE = 376; // 132 (bloom) + 4 (ftr. checksum) + 240 (all else)
    public static final int CHECKSUM_OFFSET = SIZE - 4;
    private boolean encodeChecksum = true;

    public FragmentFooter() {
    }

    public FragmentFooter(NewObjectIdentifier oid, NewObjectIdentifier link,
                          long size, int fragNum, long create, long retention,
                          long experation, long autoclose, long deletion,
                          byte shred, short checksumAlg,
                          ObjectReliability rel, int fragmentSize,
                          int chunkSize, int refCount, int maxRefCount) {
        this.oid = oid;
        this.linkoid = link;
        this.size = size;
        this.fragNum = fragNum;
        this.creationTime = create;
        this.retentionTime= retention;
        this.expirationTime = experation;
        this.autoCloseTime = autoclose;
        this.deletionTime = deletion;
        this.shred = shred;
        this.checksumAlg = checksumAlg;
        this.reliability = rel;
        this.fragmentSize = fragmentSize;
        this.chunkSize = chunkSize;
        Arrays.fill(objectContentHash, (byte)0);
        Arrays.fill(metadataField, (byte)0);
        this.refCount = refCount;
        this.maxRefCount = maxRefCount;
        deletedRefs.clear();
    }

    public void encode(Encoder encoder) {
        encoder.encodeByte(version);                       // 1 byte
        encoder.encodeInt(fragNum);                        // 4 bytes
        encoder.encodeKnownClassCodable(oid);              // 30 bytes
        encoder.encodeKnownClassCodable(linkoid);          // 30 bytes
        encoder.encodeLong(size);                          // 8 bytes
        encoder.encodeKnownClassCodable(reliability);      // 8 bytes
        encoder.encodeBytes(objectContentHash);            // 20 bytes
        encoder.encodeLong(creationTime);                  // 8 bytes
        encoder.encodeLong(retentionTime);                 // 8 bytes
        encoder.encodeLong(expirationTime);                // 8 bytes
        encoder.encodeLong(autoCloseTime);                 // 8 bytes
        encoder.encodeLong(deletionTime);                  // 8 bytes
        encoder.encodeByte(shred);                         // 1 bytes
        encoder.encodeBytes(metadataField);                // 64 bytes
        encoder.encodeShort(checksumAlg);                  // 2 bytes
        encoder.encodeShort(numPreceedingChecksums);       // 2 bytes
        encoder.encodeInt(fragmentSize);                   // 4 bytes
        encoder.encodeInt(chunkSize);                      // 4 bytes
        encoder.encodeInt(refCount);                       // 4 bytes
        encoder.encodeInt(maxRefCount);                    // 4 bytes
        // (DELETED_REFS_BLOOM_BITLENGTH/8)+4 = 132
        encoder.encodePackedBitSet(deletedRefs); 

    if (encodeChecksum) {
            footerChecksum = 0;
            footerChecksum = calculateChecksum();
        }
        encoder.encodeInt(footerChecksum);                 // 4
    }

    public void decode(Decoder decoder) {
        byte checkVersion = decoder.decodeByte();
        if(checkVersion != version) {
            LOG.warning("Excepted version " + version + " but got " +
                        checkVersion);
            // TODO: Throw exception - bad version!
        }
        fragNum = decoder.decodeInt();
        try {
            oid = decodeNewObjectIdentifier(decoder);
            linkoid = decodeNewObjectIdentifier(decoder);
            size = decoder.decodeLong();
            reliability = new ObjectReliability();
            decoder.decodeKnownClassCodable(reliability);
        } catch (Exception e) {
            throw new CodingException(e.toString());
        }

        objectContentHash = decoder.decodeBytes();
        creationTime = decoder.decodeLong();
        retentionTime = decoder.decodeLong();
        expirationTime = decoder.decodeLong();
        autoCloseTime = decoder.decodeLong();
        deletionTime = decoder.decodeLong();
        shred = decoder.decodeByte();
        metadataField = decoder.decodeBytes();
        checksumAlg = decoder.decodeShort();
        numPreceedingChecksums = decoder.decodeShort();
        fragmentSize = decoder.decodeInt();
        chunkSize = decoder.decodeInt();
        refCount = decoder.decodeInt();
        maxRefCount = decoder.decodeInt();
        deletedRefs = decoder.decodePackedBitSet();
        footerChecksum = decoder.decodeInt();
    }

    protected NewObjectIdentifier decodeNewObjectIdentifier(Decoder decoder) {
        NewObjectIdentifier noi = new NewObjectIdentifier();
        decoder.decodeKnownClassCodable(noi);
        return noi;
    }

    /**
     * Method to check the integrity of the footer.
     *
     * @return boolean true if the footer is not corrupted false otherwise
     */
    public boolean isConsistent() {
        // Check the integrity of the footer
        int storedChecksum = footerChecksum;
        footerChecksum = 0;
        int checksum = calculateChecksum();
        footerChecksum = storedChecksum;
        if (checksum != footerChecksum) {
            LOG.warning("Footer checksum [" +
                        Integer.toHexString(footerChecksum) +
                        "] does not match the stored value [" +
                        Integer.toHexString(checksum) + "]");
            return false;
        }
        return true;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();

        buffer.append("Version: [" + version + "]\n");
        buffer.append("Fragment number: [" + fragNum + "]\n");
        buffer.append("OID: [" + oid + "]\n");
        buffer.append("Link OID: [" + linkoid + "]\n");
        buffer.append("Reliability: [" + reliability + "]\n");
        buffer.append("Size: [" + size + "]\n");

        // Not printed because it messes up other stuff
        //buffer.append("Hash: [" + ByteArrays.toHexString(objectContentHash) + "]\n");
        buffer.append("Hash: [ NOT PRINTED ]\n");

        buffer.append("Creation Time: [" + creationTime + "]\n");
        buffer.append("Retention Time: [" + retentionTime + "]\n");
        buffer.append("Expiration Time: [" + expirationTime + "]\n");
        buffer.append("Auto Close Time: [" + autoCloseTime + "]\n");
        buffer.append("Deletion Time: [" + deletionTime + "]\n");
        buffer.append("Shred: [" + shred + "]\n");
        buffer.append("MetadataField: [" + new String(metadataField) + "]\n");
        buffer.append("Checksum Algorithm: [" +
                      ChecksumAlgorithm.getName(checksumAlg) + "]\n");
        buffer.append("Number of preceeding checksums: [" +
                      numPreceedingChecksums + "]\n");
        buffer.append("Fragment Size: [" + fragmentSize + "]\n");
        buffer.append("Chunk Size: [" + chunkSize + "]\n");
        buffer.append("Reference Count: [" + refCount + "]\n");
        buffer.append("Max Reference Count: [" + maxRefCount + "]\n");
        buffer.append("Footer Checksum: [" +
                      Integer.toHexString(footerChecksum) + "]\n");
        buffer.append("Footer size: [" + SIZE + "]");

        return buffer.toString();
    }

    public boolean equals(Object obj) {
        FragmentFooter compareTo = (FragmentFooter)obj;
        if (version != compareTo.version) {
            LOG.warning("version does not match");
            return false;
        }
        if (fragNum != compareTo.fragNum) {
            LOG.warning("fragment number does not match");
            return false;
        }
        if (!oid.equals(compareTo.oid)) {
            LOG.warning("oid does not match");
            return false;
        }
        if (!linkoid.equals(compareTo.linkoid)) {
            LOG.warning("linkoid does not match");
            return false;
        }
        if (!reliability.equals(compareTo.reliability)) {
            LOG.warning("reliability does not match");
            return false;
        }
        if (size != compareTo.size) {
            LOG.warning("size does not match");
            return false;
        }
        if (!Arrays.equals(objectContentHash, objectContentHash)) {
            LOG.warning("content has does not match");
            return false;
        }
        if (creationTime != compareTo.creationTime) {
            LOG.warning("creation time does not match");
            return false;
        }
        if (retentionTime != compareTo.retentionTime) {
            LOG.warning("retention time does not match");
            return false;
        }
        if (expirationTime != compareTo.expirationTime) {
            LOG.warning("expiration time does not match");
            return false;
        }
        if (autoCloseTime != compareTo.autoCloseTime) {
            LOG.warning("auto close does not match");
            return false;
        }
        if (deletionTime != compareTo.deletionTime) {
            LOG.warning("deletion time does not match");
            return false;
        }
        if (shred != compareTo.shred) {
            LOG.warning("shred does not match");
            return false;
        }
        if (!Arrays.equals(metadataField, metadataField)) {
            LOG.warning("metadata field does not match");
            return false;
        }
        if (checksumAlg != compareTo.checksumAlg) {
            LOG.warning("checksum algorithm does not match");
            return false;
        }
        if (numPreceedingChecksums != compareTo.numPreceedingChecksums) {
            LOG.warning("numPreceeding checksums does not match");
            return false;
        }
        if (fragmentSize != compareTo.fragmentSize) {
            LOG.warning("fragment size does not match");
            return false;
        }
        if (chunkSize != compareTo.chunkSize) {
            LOG.warning("chunk size does not match");
            return false;
        }
    if(refCount != compareTo.refCount) {
        LOG.warning("reference count does not match");
        return false;
    }
    if(maxRefCount != compareTo.maxRefCount) {
        LOG.warning("maximum reference count does not match");
        return false;
    }
    if(!deletedRefs.equals(compareTo.deletedRefs)) {
        LOG.warning("reference count does not match");
        return false;
    }
        if (footerChecksum != compareTo.footerChecksum) {
            LOG.warning("footer checksum does not match");
            return false;
        }
        return true;
    }

    public boolean isDeleted() {
        return(deletionTime != OAClient.NOT_DELETED);
    }

    public SystemMetadata getSystemMetadata() {
        return new SystemMetadata(oid,
                                  size,
                                  creationTime,
                                  retentionTime,
                                  expirationTime,
                                  autoCloseTime,
                                  deletionTime,
                                  shred,
                                  checksumAlg,
                                  numPreceedingChecksums,
                                  metadataField,
                                  version,
                                  oid.getLayoutMapId(),
                                  reliability,
                                  linkoid,
                                  objectContentHash,
                                  refCount,
                                  maxRefCount,
                                  deletedRefs);
    }

    public static int refAndMaxRefCountFieldSize() {
    return 4*2; // 2 Int fields
    }

    public static void updateRefAndMaxRefCountFields(int newRefCount,
                             int newMaxRefCount,
                             ByteBuffer buffer) {
    ByteBufferCoder encoder = new ByteBufferCoder(buffer, false);
    encoder.encodeInt(newRefCount);
    encoder.encodeInt(newMaxRefCount);
    buffer.flip();
    }

     public static int allRefFieldsSize() {
    return 4*2 + DELETED_REFS_BLOOM_BITLENGTH; // 2 Int fields * bloom
    }

    public static void updateAllRefFields(int newRefCount,
                      int newMaxRefCount,
                      BitSet newDeletedRefs,
                      ByteBuffer buffer) {
    ByteBufferCoder encoder = new ByteBufferCoder(buffer, false);
    encoder.encodeInt(newRefCount);
    encoder.encodeInt(newMaxRefCount);
    encoder.encodePackedBitSet(newDeletedRefs);
    buffer.flip();
    }

    protected int calculateChecksum() {
        // Calculate the footer checksum
        ByteBuffer buffer = ByteBufferPool.getInstance().checkOutBuffer(SIZE);
        Encoder coder = new ByteBufferCoder(buffer, false);
        encodeChecksum = false;
        coder.encodeKnownClassCodable(this);
        encodeChecksum = true;
        buffer.flip();

        ChecksumAlgorithm algorithm =
            ChecksumAlgorithm.getInstance(ChecksumAlgorithm.ADLER32);
        AlgorithmState internalState = algorithm.createAlgorithmState();
        algorithm.update(buffer, buffer.remaining(), internalState);
        ByteBufferPool.getInstance().checkInBuffer(buffer);
        return algorithm.getIntValue(internalState);
    }

    protected static final Logger LOG =
        Logger.getLogger(FragmentFooter.class.getName());

};
