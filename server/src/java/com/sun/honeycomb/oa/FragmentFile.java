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

import java.util.logging.Logger;
import java.util.logging.Level;
import java.nio.ByteBuffer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coordinator.Disposable;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;
import com.sun.honeycomb.oa.checksum.ChecksumBlock;
import com.sun.honeycomb.oa.checksum.ChecksumContext;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.oa.daal.DAALException;

// Default implementation if the daal property is not set.
import com.sun.honeycomb.oa.daal.nfs.NfsDAAL;


/**
 * The FragmentFile class encapsulates an OA Fragemnt File.  It also
 * manages a temporary store context file on store.  TODO: finsh
 * checksum support (read/write from/to disk), read/write mode Also
 * track if open or not (so can't delete etc.) - basically real state
 * machine
 */
public class FragmentFile implements Codable, Disposable {

    // Create a fragment
    public FragmentFile(NewObjectIdentifier oid, int fragNum, Disk disk) {
        init(oid, fragNum, disk, null);
    }

    // Creates a fragment file with the given reliability
    FragmentFile(NewObjectIdentifier oid,
                 int fragNum,
                 Disk disk,
                 ObjectReliability rel) {
        init(oid, fragNum, disk, rel);
    }

    // Create a new empty fragment from a peer
    public FragmentFile(FragmentFile peer, int fragNum, Disk disk) {
        init(peer.getOID(), fragNum, disk, peer.getFooter().reliability);
    }


    // decode implies this (and our static NULL object uses it too)
    // also used by FragmentFileSet
    // FIXME - remove this constructor.
    protected FragmentFile() {
    }

    /**
     * create the fragment file.
     * If the fragment already exists, it is overwritten.
     *
     * FIXME - way too many parameters, some are not used
     **/
    public void create(NewObjectIdentifier link,
                       long size,
                       long create,
                       long retention,
                       long experation,
                       long autoClose,
                       long deletion,
                       byte shred,
                       short cAlgNum,
                       int fragmentSize,
                       int chunkSize,
                       int refCount,
                       int maxRefCount) throws OAException
    {
        assert(daal != null);

        if(getOp() == OP_NOOP) {
            setOp(OP_STORE);
        }

        if (cAlgNum != ChecksumAlgorithm.NONE) {
            ChecksumAlgorithm cAlg = ChecksumAlgorithm.getInstance(cAlgNum);
            checksumContext = cAlg.createContext();
        } else {
            checksumContext = null;
        }

        fragmentFooter = new FragmentFooter(daal.getOID(),
                                            link,
                                            size,
                                            daal.getFragNum(),
                                            create,
                                            retention,
                                            experation,
                                            autoClose,
                                            deletion,
                                            shred,
                                            cAlgNum,
                                            rel,
                                            fragmentSize,
                                            chunkSize,
                                            refCount,
                                            maxRefCount
                                            );

        try {
            daal.create();
        } catch (DAALException de) {
            close();
            bad = true;
            throw new OAException(de);
        }
    }


    /**
     * open the fragment file.
     * The fragment file is opened in read-only mode
     *
     * Returns creationTime from footer.
     */
    public long open() throws
        FragmentNotFoundException,
        DeletedFragmentException,
        ObjectCorruptedException,
        OAException
    {
        assert(daal != null);

        try {
            bad = true;
            daal.open();
            bad = false;
        } catch (DAALException de) {
            close();
            throw new OAException(de);
        }

        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer readBuffer = pool.checkOutBuffer(CACHE_READ_SIZE);
        try {
            readFooter(readBuffer);
        } finally {
            pool.checkInBuffer(readBuffer);
        }

        return fragmentFooter.creationTime;
    }


    FragmentFile recoverDeletedStubPeer(int fragId, Disk disk)
            throws FragmentNotFoundException, OAException
    {
        FragmentFile peer = new FragmentFile(this, fragId, disk);
        peer.create(this); // creates in tmp with same footer (except fragnum)
        peer.writeFooterAndClose(0);
        peer.completeCreate(); // rename from tmp to perm location
        return peer;
    }

    //TODO: Should we have a switck for ctx vs. fragment file target?

    public void commit(ByteBufferList ctxBuffers, boolean flush)
        throws OAException
    {
        try {
            daal.saveCtx(ctxBuffers, flush);
        } catch (DAALException de) {
            close();
            bad = true;
            throw new OAException(de);
        }
     }

    public ByteBuffer restoreContextForStore(ByteBuffer ctxarchive, boolean notused)
        throws OAException
    {
        try {
            return daal.restoreCtx(ctxarchive);
        } catch (DAALException de) {
            close();
            bad = true;
            throw new OAException(de);
        }
    }

    public void updateFooterWithMetadataField(byte[] metadataField) throws OAException
    {
        getFragmentFooter().metadataField = metadataField;
    }

    public void updateFooterWithSystemMetadata(SystemMetadata sm, boolean moreChunks) {
        fragmentFooter.deletionTime = sm.getDTime();
        fragmentFooter.refCount = sm.getRefcount();
        fragmentFooter.maxRefCount = sm.getMaxRefcount();
        // if this is a multi-chunk object, and not the final chunk, do not
        // update the size
        if (! moreChunks) {
            fragmentFooter.size = sm.getSize();
            fragmentFooter.objectContentHash = sm.getContentHash();
        }
        // objectHashAlgorithm is not being update because it currently
        // only has one type sha1 and it doesn't exist in the current
        // version of the footers, so whenever we support another hash
        // algorithm then we update the algorithm here.
        fragmentFooter.deletedRefs.clear();
        fragmentFooter.deletedRefs.or(sm.getDeletedRefs());
        fragmentFooter.metadataField = sm.getMetadataField();
        fragmentFooter.numPreceedingChecksums = sm.getNumPreceedingChksums();
        //System.out.println ("moreChunks=" + moreChunks);
        //System.out.println ("footer.numPreceedingChecksums=" + fragmentFooter.numPreceedingChecksums);
        //System.out.println ("sm.numPreceedingChecksums=" + sm.getNumPreceedingChksums());
    }

    public void updateFooterWithObjectHash(byte[] objectHash) throws OAException
    {
        getFragmentFooter().objectContentHash = objectHash;
    }

    /**
     * Method to finalize a fragment file. This will do the following:
     * 1. Write any remaining checksums to the end of the fragment.
     * 2. Set the object's size in the footer.
     * 3. Serialize and append the footer to the end of the fragment.
     * 4. Close the fragment file.
     *
     * @param actualSize the size of the object
     */
    public void writeFooterAndClose(long actualSize) throws OAException
    {
        ByteBufferPool pool = ByteBufferPool.getInstance();

        fragmentFooter.size = actualSize;
        if (fragmentFooter.checksumAlg != ChecksumAlgorithm.NONE) {
            // Write the checksum context to the end of the fragment. The
            // context should have a maximum of 2 checksum blocks in it as
            // the rest should have been processed during writes.
            ChecksumBlock checksumBlock = null;
            ByteBuffer checksumBlockBuffer = null;
            switch(checksumContext.checksumBlocks.size()) {
            case 2:
                // There are two checksum blocks in the context, one (0) for
                // the first data block and (1) for the last data block. Write
                // the checksum block for the last data block in its reserved
                // space and fall through to the next case which will append
                // the checksum block for the first data block to the end of
                // the fragment.

                // Get the checksum block and write it to its reserved space
                checksumBlock =
                    (ChecksumBlock)checksumContext.checksumBlocks.remove(1);

                // Get a buffer representation and write the checksum block
                // to its reserved space.
                checksumBlockBuffer = checksumBlock.getBuffer();
                try {
                    daal.write(checksumBlockBuffer,
                               checksumContext.reservedChecksumBlockOffset
                               );
                } catch (DAALException de) {
                    abortCreate();
                    bad = true;
                    throw new OAException(de);
                } finally {
                    pool.checkInBuffer(checksumBlockBuffer);
                    checksumBlock.dispose();
                }
                /*
                LOG.info("Fragment [" + daal.getFragNum() + "]: Wrote pending " +
                         "checksum block with " +
                         checksumBlock.numChecksums() +
                         " checksums at offset = " +
                         checksumContext.reservedChecksumBlockOffset);
                */
                // Fall through to write the checksum block for the first data
                // block...

            case 1:
                // There is only one checksum block. This can only be if this
                // is a small file and has lesser data than a checksum block
                // can cover.

                // Get the checksum block and set the number of preceeding
                // checksums in the fragment footer.
                checksumBlock =
                    (ChecksumBlock)checksumContext.checksumBlocks.remove(0);
                fragmentFooter.numPreceedingChecksums = (short)
                    checksumBlock.numChecksums();

                // Get a buffer representation and append the valid checksums
                // to the fragment.
                checksumBlockBuffer = checksumBlock.getChecksumBuffer();
                /*
                LOG.info("Fragment[" + daal.getFragNum() + "]: Writing first " +
                         "checksum block with " +
                         checksumBlock.numChecksums() +
                         " checksums at offset = " + currentOffset);
                */
                try {
                    currentOffset += daal.append(checksumBlockBuffer);
                } catch (DAALException de) {
                    abortCreate();
                    bad = true;
                    throw new OAException(de);
                } finally {
                    pool.checkInBuffer(checksumBlockBuffer);
                    checksumBlock.dispose();
                }

                // Clear all the checksums as they are not needed anymore
                checksumContext.checksumBlocks.clear();
                break;

            case 0:
                // Nothing to do as there are no checksums to write...
                //LOG.info("No checksums to write...");
                break;

            default:
                String err = "Context has " +
                    checksumContext.checksumBlocks.size() +
                    " checksum blocks. Expected 0, 1 or 2";
                LOG.warning(err + daal);
                throw new OAException(err);
            }
        }

        // Set the actual object size in footer (not fragment size)
        fragmentFooter.size = actualSize;

        // Write final footer to the end of the file
        ByteBuffer serialFooter = serializeFooter();

        try {
            currentOffset += daal.append(serialFooter);
        } catch (DAALException de) {
            abortCreate();
            bad = true;
            throw new OAException(de);
        } finally {
            pool.checkInBuffer(serialFooter);
        }

        // Close the file
        close();
    }

    /** Rename the tmp file to perm. location */
    public void completeCreate() throws OAException
    {
        setOp(OP_STORE);

        try {
            daal.commit();
        } catch (DAALException de) {
            throw new OAException(de);
        }
    }


    /**
    * This function will do the opposite of completeCreate it will move a
     * fragment from the data slice back to the tmp directory on it's own disk
     *
     * @throws OAException
     */
    public void rollbackCreate() throws OAException
    {
        setOp(OP_STORE);
        try {
            daal.rollback();
        } catch (DAALException de) {
            throw new OAException(de);
        }
    }

    // Try to clean up after abort
    // remove frag from data disk (if already renamed) or tmp
    public void abortCreate()
    {
        close();
        remove();
        deleteContextFiles();
    }

    /**
    * remove is to bused by everyone who needs to physically remove the FragmentFile from
    * the disk. This way in the future when we want to add additional logic we dont' have
    * to hunt down all of the places where we would of done File(X).delete() and instead
    * just change the logic here.
    *
    * @return true if the fragment is successfully deleted. false otherwise.
    */
    public boolean remove()
    {
        boolean success = false;
        try {
            daal.delete();
            success = true;
        } catch (DAALException de) {
            LOG.warning("failed to delete fragment " + daal + de);
        }
        return success;
    }

    /**
     * Simple method to create a fragment from the contents of another fragment
     * FIXME - this method is dangerous as it does not check the data file
     * integrity. Only used by DataDoctor to heal back a fragment.
     *
     * @param frag
     * @throws OAException
     */
    public void createFrom(FragmentFile frag) throws OAException
    {
        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer buf = pool.checkOutBuffer(CACHE_READ_SIZE);
        try {
            long offset = 0;
            long n;

            daal.create();
            try {
                DAAL daalFrom = frag.getDAAL();
                long length = daalFrom.length();
                while ((n = daalFrom.read(buf, offset)) > 0) {
                    buf.flip();
                    daal.write(buf, offset);
                    offset += n;
                    if (offset == length) {
                        break;
                    }
                    buf.clear();
                }
                if (length != offset) {
                    throw new DAALException
                        ("short copy: expected size " + daalFrom.length() 
                         + " wrote size " + offset + " frag " + daalFrom);
                }
            } finally {
                daal.close();
            }

        } catch (DAALException de) {
            remove();
            throw new OAException(de);

        } finally {
            pool.checkInBuffer(buf);
        }
    }

    public void close()
    {
        boolean success = false;
        try {
            daal.close();
            success = true;
        } catch (DAALException de) {
            LOG.warning("failed to close " + de);
        } finally {
            if (!success) {
                bad = true;
            }
        }

        // Clean up the checksum context
        if (checksumContext != null) {
            checksumContext.dispose();
            checksumContext = null;
        }

        // Cleanup any cached data
        if (cachedData != null) {
            ByteBufferPool.getInstance().checkInBuffer(cachedData);
            cachedData = null;
        }

        setOp(OP_NOOP);
    }

    public void deleteContextFiles()
    {
        try {
            daal.deleteCtx();
        } catch (DAALException de) {
            LOG.warning("failed to delete context " + de);
        }
    }

    /**
     * Check whether this fragment exist in its temp location.
     */
    public boolean checkTmp()
    {
        return daal.isTransient();
    }

    /**
     * Check if this fragment exists on stable storage.
     */
    public boolean exists()
    {
        return daal.isCommitted();
    }

    /**
     * Method to append a list of data buffers to the fragment.
     *
     * @param bufList the list of data buffers to append
     */
    public void append(ByteBufferList bufList) throws OAException
    {
        ByteBufferList newList = null;
        boolean updateChecksumContext = false;

        if (getFragmentFooter().checksumAlg != ChecksumAlgorithm.NONE)
        {
            // If the data to be written is spanning a checksum block offset:
            // 1. Any pending checksum block must be written to its reserved
            //    location (available in the checksum context).
            // 2. The new checksum block space must be reserved.
            // 3. The new values of reserved and next checksum blocks must be
            //    calculated and stored in the checksum context.

            // Check to see if the data to be written is spanning a checksum
            // block offset.
            long nextChecksumBlockOffset = checksumContext.getNextChecksumBlockOffset();
            if ((currentOffset + bufList.remaining()) > nextChecksumBlockOffset)
            {
                /*
                  if (daal.getFragNum() == 0) {
                    LOG.info("Crossing checksum block boundary. " +
                             "Current offset = " + currentOffset);
                }
                */
                // Write any pending checksum blocks
                if (checksumContext.reservedChecksumBlockOffset > 0) {
                    // Get the checksum block and write it to its reserved
                    // space.
                    ChecksumBlock checksumBlock = (ChecksumBlock)
                        checksumContext.checksumBlocks.remove(1);

                    // Get a buffer representation and write the checksum block
                    // to its reserved space.
                    ByteBuffer checksumBlockBuffer = checksumBlock.getBuffer();
                    try {
                        daal.write(checksumBlockBuffer, 
                                   checksumContext.reservedChecksumBlockOffset);
                    } catch (DAALException de) {
                        abortCreate();
                        bad = true;
                        throw new OAException(de);
                    } finally {
                        ByteBufferPool.getInstance().checkInBuffer(checksumBlockBuffer);
                        checksumBlock.dispose();
                    }
                    /*
                    if (daal.getFragNum() == 4) {
                        LOG.info("Wrote pending checksum block with " +
                                 checksumBlock.numChecksums() +
                                 " checksums at offset = " +
                                 checksumContext.reservedChecksumBlockOffset);
                    }
                    */
                }

                int slice1Length = (int) (nextChecksumBlockOffset - currentOffset);

                // If the first slice length is zero the buffers can simply
                // be appended. As the writes are always aligned, the first
                // slice length will always be zero.
                if (slice1Length == 0) {
                    newList = new ByteBufferList();
                    newList.appendBuffer(ChecksumBlock.blankChecksumBlock);
                    newList.appendBufferList(bufList);
                } else {
                    int slice2Length = (int)(bufList.remaining() - slice1Length);
                    newList = bufList.slice(0, slice1Length);
                    newList.appendBuffer(ChecksumBlock.blankChecksumBlock);
                    ByteBufferList tempList = bufList.slice(slice1Length, slice2Length);
                    newList.appendBufferList(tempList);
                    tempList.checkInBuffers();

                    /*** COMMENTED REGION TO CATCH BUG WITH BYTE BUFFER LIST
                     *   SLICING. REMOVE WHEN FIXED.
                    if (daal.getFragNum() == 4) {
                        LOG.info("Inserting at " + slice1Length +
                                 " original buffer list size = " +
                                 bufList.remaining());
                    }

                    if (daal.getFragNum() == 4) {
                        LOG.info("Slicing " + bufList.toString() + " from " +
                                 0 + " to " + slice1Length);
                    }

                    newList = bufList.slice(0, slice1Length);

                    if (daal.getFragNum() == 4) {
                        LOG.info("new list = " + newList.toString());
                        LOG.info("Appending checksum block to new list");
                    }

                    newList.appendBuffer(ChecksumBlock.blankChecksumBlock);

                    if (daal.getFragNum() == 4) {
                        LOG.info("new list = " + newList.toString());
                        LOG.info("Slicing " + bufList.toString() + " from " +
                                 slice1Length + " to " + slice2Length);
                    }

                    ByteBufferList tempList =
                        bufList.slice(slice1Length, slice2Length);

                    if (daal.getFragNum() == 4) {
                        LOG.info("temp list = " + tempList.toString());
                    }

                    newList.appendBufferList(tempList);

                    if (daal.getFragNum() == 4) {
                        LOG.info("new list = " + newList.toString());
                    }

                    tempList.checkInBuffers();

                    if (daal.getFragNum() == 4) {
                        LOG.info("New buffer list size = " +
                                 newList.remaining());
                    }
                    */
                }
                updateChecksumContext = true;
            } else {
                newList = bufList;
            }
        } else {
            LOG.info (".... this should not happen!");
            newList = bufList;
        }

        // Append to the fragment file
        try {
            currentOffset += daal.append(newList);
        } catch (DAALException de) {
            abortCreate();
            bad = true;
            throw new OAException(de);
        } finally {
            // Clear the allocated buffer list if needed
            if (newList != bufList) {
                newList.checkInBuffers();
            }
        }

        // Update the checksum context if needed
        if ((fragmentFooter.checksumAlg != ChecksumAlgorithm.NONE) &&
            (updateChecksumContext)) {
            checksumContext.incrementReservedChecksumBlockOffset();
        }
    }

    public FragmentFooter getFooter() {
        return fragmentFooter;
    }

    public byte[] getObjectMetadataField() {
        return fragmentFooter.metadataField;
    }

    public long getObjectCreateTime() {
        return fragmentFooter.creationTime;
    }

    public long getObjectDeleteTime() {
        return fragmentFooter.deletionTime;
    }

    public byte[] getObjectContentHash() {
        return fragmentFooter.objectContentHash;
    }

    public int getFragmentSize() {
        return fragmentFooter.fragmentSize;
    }

    /* WARNING: chunkSize is in blocks, not bytes or kbytes */
    public int getChunkSize() {
        return fragmentFooter.chunkSize;
    }

    public int getRefCount() {
        return fragmentFooter.refCount;
    }

    public int getMaxRefCount() {
        return fragmentFooter.maxRefCount;
    }

    public long getObjectSize() {
        return fragmentFooter.size;
    }

    public NewObjectIdentifier getOID() {
        return daal.getOID();
    }

    public int getFragNum() {
        return daal.getFragNum();
    }

    // Builds a new system md object from relevant fragment fileds
    public SystemMetadata getSystemMetadata()  {
        return fragmentFooter.getSystemMetadata();
    }

    // Opens, reads in footer, closes, and returns SystemMetadata
    public SystemMetadata readSystemMetadata()
        throws FragmentNotFoundException,
               DeletedFragmentException,
               ObjectCorruptedException,
               OAException
    {
        open();
        close();
        return getSystemMetadata();
    }

    // Returns true of the fragment file is readable and has the right
    // fragNum in it
    public boolean checkFragment()
    {
        boolean good = false;
        try {
            open();
            close();
            good = fragmentFooter.fragNum == daal.getFragNum();
        } catch (FragmentNotFoundException fnfe) {
            return false;
        } catch (DeletedFragmentException dfe) {
            return false;
        } catch (ObjectCorruptedException oce) {
            return false;
        } catch (OAException oae) {
            return false;
        } finally {
            dispose();
        }
        return good;
    }

    /**
     * Method to get the absolute object offset from a given fragment offset.
     * The given offset must be fragment size aligned. For single chunk
     * fragments, the offset will be "n" times the fragment offset. For
     * multiple chunk fragments, the offset will be adjusted to include the
     * previous chunks.
     *
     * @param fragmentOffset the fragment size aligned offset to map
     * @return long the starting offset of the block in the object
     */
    public long mapFragmentOffsetToObjectBlockOffset(long fragmentOffset)
    {
        // Make sure that the offset is fragment size aligned
        int fragmentSize = getFragmentSize();

        if ((fragmentOffset % fragmentSize) != 0) {
            throw new IllegalArgumentException
                ("Fragment offset [" + fragmentOffset + "] is not fragment " +
                 "size aligned [" + fragmentSize + "]");
        }

        int n = fragmentFooter.reliability.getDataFragCount();
        long chunkNumber = fragmentFooter.oid.getChunkNumber();
        int chunkSize = getChunkSize();
        return n * ((chunkNumber * fragmentSize * chunkSize) + fragmentOffset);
    }

    /**
     * Method to get the size of the data contained in the fragment.
     *
     * @return long the size of the data in the fragment
     */
    public long getDataSize()
    {
        // If the size is set to a special value (MORE_CHUNKS) it indicates
        // that this is a non-last fragment of a multiple chunk file. As all
        // non-last fragments have a fixed size, it can be calculated from
        // the footer values and returned.
        if (fragmentFooter.size == OAClient.MORE_CHUNKS) {
            return (fragmentFooter.fragmentSize * fragmentFooter.chunkSize);
        }

        // Data reliability of the object
        int n = fragmentFooter.reliability.getDataFragCount();

        // If this is the last fragment of a multiple chunk file, then it has
        // (size - size of previous chunks) bytes of data
        long chunkSize = fragmentFooter.size;
        long chunkNumber = fragmentFooter.oid.getChunkNumber();

        if (chunkNumber > 0) {
            chunkSize = chunkSize - (chunkNumber *
                                     n *
                                     fragmentFooter.fragmentSize *
                                     fragmentFooter.chunkSize);
        }

        // First align the chunk size to "N"
        long alignedChunkSize = chunkSize;
        if ((chunkSize % n) != 0) {
            long alignment = n - (chunkSize % n);
            alignedChunkSize = chunkSize + alignment;
        }

        // Size is mostly aligned object size / n unless it is the first
        // fragment and is not block aligned by less than
        // Fragmenter.MAX_BOTROS_BLOCK_SIZE.
        long size = alignedChunkSize/n;
        if (fragmentFooter.fragNum == 0)
        {
            int blockSize = n * fragmentFooter.fragmentSize;
            long blockUnalignedBytes = chunkSize % blockSize;
            if ((blockUnalignedBytes > 0) &&
                (blockUnalignedBytes < Fragmenter.MAX_BOTROS_BLOCK_SIZE))
            {
                long numBlocks = alignedChunkSize / blockSize;
                size = (numBlocks * fragmentFooter.fragmentSize) +
                    (alignedChunkSize % blockSize);
            }
        }
        return size;
    }


    /**
     * Method to read data from the fragment. This read will do an integrity
     * check on the data and return an error if it is corrupted. The read
     * expects the offsets to be FRAGMENT_SIZE aligned.
     *
     * @param buf the buffer to read the data in
     * @param offset the offset to read the data from
     */
    public long read(ByteBuffer buf, long offset)
        throws OAException, ObjectCorruptedException
    {
        //
        // Check to see if the data is cached. It will be cached if:
        // 1. This is the 0th fragment
        // 2. The object size is < Fragmenter.MAX_BOTROS_BLOCK_SIZE
        // 3. cachedData != null
        //
        // NOTE: The cached data is already verified so it can be returned
        //       without checksumming.
        //

        if ((fragmentFooter.fragNum == 0) &&
            (fragmentFooter.size < Fragmenter.MAX_BOTROS_BLOCK_SIZE) &&
            (cachedData != null))
        {
            int start = buf.position();
            buf.put(cachedData);
            cachedData.rewind();
            return (int)(buf.position() - start);
        }

        // Reset the last read state as it might not be valid anymore
        lastReadBad = false;
        long newOffset = offset;

        // If checksums are enabled, remap the offset and read the checksums
        // if required.
        if (fragmentFooter.checksumAlg != ChecksumAlgorithm.NONE) {
            // Remap the read offset in the fragment file. This will take care
            // of adjusting for any checksum blocks embedded in the file.
            newOffset = checksumContext.remapOffset(offset);

            // Check if the context has a cached checksum block
            if (!checksumContext.hasChecksumBlock(newOffset, buf.remaining()))
            {
                // There is no cached checksum block, read and cache it
                long checksumBlockOffset =
                    checksumContext.getChecksumBlockOffset(newOffset);

                ByteBuffer checksumBuffer =
                    ByteBufferPool.getInstance().checkOutBuffer(ChecksumBlock.checksumBlockSize);

                /*
                if (fragmentFooter.fragNum == 0) {
                    LOG.info("Fragment [" + fragmentFooter.fragNum + "]: " +
                             " Reading checksum block from offset " +
                             checksumBlockOffset);
                }
                */

                // Seek and read the checksums from the daal,
                // create the checksum block and set it in the context.
                try {
                    daal.read(checksumBuffer, checksumBlockOffset);

                    ChecksumAlgorithm algorithm =
                        ChecksumAlgorithm.getInstance(fragmentFooter.checksumAlg);
                    ChecksumBlock block = algorithm.createChecksumBlock(checksumBuffer);

                    checksumContext.setReadChecksumBlockAndOffset
                        (block, checksumBlockOffset + ChecksumBlock.checksumBlockSize);

                } catch (DAALException de) {
                    close();
                    bad = true;
                    throw new OAException(de);

                } catch (IllegalArgumentException e) {
                    close();
                    bad = true;
                    throw new ObjectCorruptedException
                        ("Error in creating a checksum block. Exception = " + e);

                } finally {
                    ByteBufferPool.getInstance().checkInBuffer(checksumBuffer);
                }

                //
                // TODO: [Performance]
                // If the checksum block and the offset to read are
                // consecutive then they can be read in one IO.
                //
            }
        }

        // Read the data
        long readBytes = 0;
        try {
            readBytes = daal.read(buf, newOffset);
        } catch (DAALException de) {
            close();
            bad = true;
            throw new OAException(de);
        }

        /*
        if (fragmentFooter.fragNum == 0) {
            LOG.info("Fragment [" + fragmentFooter.fragNum + "]: Read " +
                     readBytes + " bytes from offset " + offset +
                     " remapped offset " + newOffset);
        }
        */

        // If the data has checksums, verify them here
        if (fragmentFooter.checksumAlg != ChecksumAlgorithm.NONE)
        {
            boolean consistent = false;

            // Verify the data only if the checksum block is valid
            if (checksumContext.readChecksumBlock != null) {
                ByteBuffer toCheck = buf.duplicate();
                toCheck.rewind();
                ChecksumAlgorithm algorithm =
                    ChecksumAlgorithm.getInstance(fragmentFooter.checksumAlg);
                try {
                    algorithm.verify
                        (toCheck,
                         checksumContext.getOffsetInChecksumBlock(newOffset),
                         checksumContext.readChecksumBlock
                         );
                    consistent = true;
                } catch (ObjectCorruptedException e) {
                    LOG.log(Level.WARNING, "OID: [" + fragmentFooter.oid + "]: ", e);
                }
            }

            if (!consistent) {
                String err = new String
                    ("OID: [" + fragmentFooter.oid + "] Fragment [" +
                     fragmentFooter.fragNum + "]: " +
                     "Checksum verification failed at offset " + offset +
                     " remapped offset " + newOffset +
                     " checksumIndex = " +
                     checksumContext.getOffsetInChecksumBlock(newOffset)
                     );
                //LOG.warning(err);
                lastReadBad = true;
                buf.rewind();
                throw new ObjectCorruptedException(err);
            }
        }

        return readBytes;
    }

    // This method does not throw exception -
    // it logs the error and return.
    void rewriteBlock(ByteBuffer buffer, long offset)
    {
        // Get a new daal instance of the fragment and open it for write mode.
        DAAL toRepair = instantiateDAAL(daal.getDisk(),
                                        daal.getOID(),
                                        daal.getFragNum());
        try {
            toRepair.rwopen();

        } catch (DAALException de) {
            LOG.warning("failed to open daal " + daal);
            return;

        } catch (FragmentNotFoundException ne) {
            LOG.warning("daal " + daal + "does not exist");
            return;
        }

        // Remap the offset in the fragment file. This will take care
        // of adjusting for any checksum blocks embedded in the file.
        long newOffset = checksumContext.remapOffset(offset);

        //LOG.info("Remapped offset [" + offset + "] to [" + newOffset + "]");

        // Make sure that the context has the cached checksum block. This
        // should be there due to the previous read that failed.
        if (!checksumContext.hasChecksumBlock(newOffset, buffer.remaining())) {
            LOG.warning("OID [" + fragmentFooter.oid +
                        "] Cached checksum block not found " +
                        "for offset " + offset + ". Returning..."
                        );
            //
            // TODO: This should not happen but we can still read it from disk
            //
            try {
                toRepair.close();
            } catch (DAALException de) {
                LOG.warning("Failed to close the fragment file" + daal);
            }
            return;
        }

        //LOG.info("Found cached checksum block for " + newOffset);

        // Calculate the new checksum for the repair data and insert into
        // the checksum block.
        int checksumBlockIndex = checksumContext.getOffsetInChecksumBlock(newOffset);
        //LOG.info("Checksum block index = " + checksumBlockIndex);
        ChecksumAlgorithm algorithm = ChecksumAlgorithm.getInstance(fragmentFooter.checksumAlg);

        // The checksum block can be null if it was corrupted on read. Create
        // a blank one and insert the checksums in it.
        if (checksumContext.readChecksumBlock == null) {
            checksumContext.readChecksumBlock = algorithm.createChecksumBlock();
        }
        algorithm.insert(buffer.duplicate(),
                         checksumBlockIndex,
                         checksumContext.readChecksumBlock
                         );

        // Rewrite the data
        LOG.info("OID [" + fragmentFooter.oid + "] Rewriting " + buffer.remaining() +
                 " bytes to logical offset " + offset + " remapped offset " +
                 newOffset
                 );
        try {
            toRepair.write(buffer, newOffset);
        } catch (DAALException de) {
            LOG.warning("OID [" + fragmentFooter.oid + "] Failed rewriting " +
                        buffer.remaining() + " bytes to logical offset " +
                        offset + " remapped offset " + newOffset +
                        ". Trying to write the checksum block"
                        );
        }

        // Get a buffer representation and write the checksum block to its
        // location.
        long checksumBlockOffset = checksumContext.getChecksumBlockOffset(newOffset);
        ByteBuffer checksumBlockBuffer;
        // Check to see if this is the checksum block for the first data block
        if (checksumBlockOffset < 0) {
            checksumBlockOffset = checksumContext.firstChecksumBlockOffset;
            checksumBlockBuffer = checksumContext.readChecksumBlock.getChecksumBuffer();
        } else {
            checksumBlockBuffer = checksumContext.readChecksumBlock.getBuffer();
        }

        LOG.info("OID [" + fragmentFooter.oid + "] Rewriting " +
                 checksumBlockBuffer.remaining() + " bytes of updated " +
                 "checksum block at offset " + checksumBlockOffset
                 );
        try {
            toRepair.write(checksumBlockBuffer, checksumBlockOffset);
        } catch (DAALException de) {
            LOG.warning("OID [" + fragmentFooter.oid + "] Failed rewriting " +
                        checksumBlockBuffer.remaining() +
                        " bytes of updated checksum block at offset " +
                        checksumBlockOffset
                        );
        }

        ByteBufferPool.getInstance().checkInBuffer(checksumBlockBuffer);

        // Close the file and return
        try {
            toRepair.close();
        } catch(DAALException de) {
            LOG.warning("OID [" + fragmentFooter.oid +
                        "] Exception while closing fragment " +
                        "to repair. Exception = " + de
                        );
        }
    }

    public long delete(long deletionTime)
        throws FragmentNotFoundException, OAException
    {
        return delete(deletionTime, -1);
    }

    /** Opens, reads in footer, closes, writes deleted version of
     * fragment (truncated data, footer w/ dtime set), and moves
     * deleted fragment over good one
     * TODO: Need some retries here!
     * now that DataDoctor in separate package, make PUBLIC
     **/
    public long delete(long deletionTime, long objectSize)
        throws FragmentNotFoundException, OAException
    {
        long linkSize = -1;

        // Just open to read in footer then close
        try {
            //LOG.info("XXX first opening " + fragmentFooter.oid + " frag " +
            // fragmentFooter.fragNum);
            open();

            // TODO: Move to finest after delete has been well-tested
            LOG.info("Attempting to delete fragment " + fragmentFooter.fragNum +
                     " of " + fragmentFooter.oid +
                     " from disk " + printDisk() +
                     " in dir " + Common.getStoreDir(fragmentFooter.oid) +
                     " with link " + fragmentFooter.linkoid +
                     " and refCount " + fragmentFooter.refCount +
                     " and maxRefCount " + fragmentFooter.maxRefCount +
                     " and bloom filter " + fragmentFooter.deletedRefs);

        } catch (DeletedFragmentException dfe) {
            // This fragment has previously been deleted - return happy
            // delete should be idempotent
            return -1;
        } catch(ObjectCorruptedException oce) {
            String err = new String("Fragment [" + fragmentFooter.fragNum +
                                    "] is corrupted");
            LOG.warning(err + daal);
            throw new OAException(err);

        } finally {
            close();
        }

        setOp(OP_DELETE);

        // Set the Dtime
        fragmentFooter.deletionTime = deletionTime;


        ByteBuffer serialFooter = serializeFooter();
        try {
            daal.replace(serialFooter);
        } catch (DAALException de) {
            throw new OAException(de);
        } finally {
            ByteBufferPool.getInstance().checkInBuffer(serialFooter);
        }

        // Does nothing if we are a referee, else decrements and maybe
        // deletes in our referee fragment
        objectSize = deleteRefFromReferee(deletionTime, objectSize);

        // TODO: Move to finest after delete as been well-tested
        LOG.info("Deleted fragment " + fragmentFooter.fragNum +
                 " of " + fragmentFooter.oid +
                 " from disk " + printDisk() +
                 " in dir " + Common.getStoreDir(fragmentFooter.oid) +
                 " with link " + fragmentFooter.linkoid +
                 " and refCount " + fragmentFooter.refCount +
                 " and maxRefCount " + fragmentFooter.maxRefCount +
                 " and bloom filter " + fragmentFooter.deletedRefs
                 );

        return objectSize;
    }

    /** delete my ref to all chunks of my referee */
    public long deleteRefFromReferee(long deletionTime)
        throws OAException
    {
        return deleteRefFromReferee(fragmentFooter.linkoid, deletionTime, -1);
    }

    /** delete my ref to all chunks of my referee */
    public long deleteRefFromReferee(long deletionTime, long objectSize)
        throws OAException
    {
        return deleteRefFromReferee(fragmentFooter.linkoid, deletionTime, objectSize);
    }

    /** delete my ref to all chunks of my referee */
    public long deleteRefFromReferee(NewObjectIdentifier initialoid,
                                     long deletionTime,
                                     long objectSize) throws OAException
    {
        int chunk = 0;
        int numChunks = 1;

        NewObjectIdentifier linkoid = new NewObjectIdentifier(initialoid.toString());

        OAClient oa = OAClient.getInstance();

        if(objectSize == -1) {
            if(fragmentFooter.oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE)
            {
                SystemMetadata sm = null;
                try {
                    sm = oa.getLastSystemMetadata(linkoid, true, false, true);

                } catch (NoSuchObjectException nsoe) {
                    throw new OAException("Can't get link system info - no obj: "
                                          + nsoe
                                          );

                } catch (OAException oae) {
                    LOG.warning(fragmentFooter.oid +
                                " is fully deleted so there is no way to follow links"
                                );
                }
                if(sm != null) {
                    objectSize = sm.getSize();
                }
            }
        }

        if(objectSize != -1) {
            numChunks = (int) oa.calcNumChunks(objectSize);
        }

        while(numChunks-- > 0) {
            // first delete the 1st chunk, & get the size of the referee object
            try {
                deleteRefFromRefereeChunk(linkoid, deletionTime);
            } catch (OAException oae) {
                LOG.info("Failed to deleteRefFromRefereeChunk oid " +
                         fragmentFooter.oid + " frag " +
                         fragmentFooter.fragNum + " chunk " + chunk
                         );
            }

            // inc chunk# and layout number of layoutoid
            linkoid.setChunkNumber(linkoid.getChunkNumber()+1);
            int nextLayoutId = LayoutClient.getInstance().
                getConsecutiveLayoutMapId(linkoid.getLayoutMapId());
            linkoid.setLayoutMapId(nextLayoutId);
            chunk++;
        }
        return objectSize;
    }

    /** delete my ref to a chunk of my referee, return more chunks or not */
    private boolean deleteRefFromRefereeChunk(NewObjectIdentifier linkoid,
                                             long deletionTime)
        throws OAException
    {
        boolean moreChunks = false;

        // If we are only a referee and not a referer, just return

        // XXX: This check doesn't work for data-only objects,
        // stored using the unsupported advanced API.
        // They also have refCount of -1, but don't refer to anything.
        // See BUG 6276284.

        if(fragmentFooter.refCount != -1 || fragmentFooter.maxRefCount != -1) {
            return false; // TODO: maybe a special exception is more appropriate?
        }

        // If we are a referrer, decrement refCount and maxRefCount
        // and add our OID to the deletedRefs field of the fragment we
        // refer to.  If the counts go to 0 and the safety check
        // passes, delete the fragment we refer to.

        // First ask layout what disk to go to
        Layout layout = LayoutClient.getInstance().
            getLayoutForRetrieve(linkoid.getLayoutMapId());

        Disk disk = layout.getDisk(fragmentFooter.fragNum);

        // TODO?: do we need to check the state of this disk before using it?

        // Next create a new FragmentFile for our referred-to fragment
        FragmentFile refereeFrag = new FragmentFile(linkoid,
                                                    fragmentFooter.fragNum,
                                                    disk,
                                                    fragmentFooter.reliability);

        refereeFrag.setOp(OP_REFDEC);

        // open the fragment file if we can
        // if we can't healing will take care of this later

        FragmentFooter refereeFooter = null;
        try { // block for close finally

            try {
                //LOG.info("XXX deleteRefFromReferee: Opening referee " +
                //       linkoid +
                //     " frag " + fragmentFooter.fragNum);

                // Updating ref header fields; make sure we get the
                // lock before reading
                refereeFrag.openReadWriteLocked();
                moreChunks = (refereeFrag.getObjectSize() == OAClient.MORE_CHUNKS);

            } catch (DeletedFragmentException dfe) {
                // this is not an error - it's actually the common case
                // this delete has already been done so we don't have to re-do it
                //LOG.fine("XXX referee already deleted");
                return moreChunks;
 
            } catch (FragmentNotFoundException fnfe) {
                throw new OAException("Can't find referred-to object " +
                                      linkoid + " fragment " +
                                      fragmentFooter.fragNum + " on disk " +
                                      printDisk() + " to delete: " + fnfe
                                      );

            } catch (ObjectCorruptedException oce) {
                // TODO?: what really is the right behaviour here?
                throw new OAException("Can't refDec corrupted referred-to fragment: " +
                                      oce);
            }

            // get the footer
            refereeFooter = refereeFrag.getFooter();

            // Make a bloom filter object out of the bitset
            BloomFilter bloom = new BloomFilter(refereeFooter.deletedRefs);

            // if my oid is already in the filter, then we don't have to
            // do this again, so just return
            if(bloom.hasKey(fragmentFooter.oid)) {
                refereeFrag.unlock();
                refereeFrag.close();
                LOG.info("XXX referee already has us in its bloom filter");
                return moreChunks;
            }

            String oldRefs = null;
            int oldCount = refereeFooter.refCount;
            if (LOG.isLoggable(Level.INFO))
                oldRefs = refereeFrag.getRefFieldsString();

            // decrement the refcount and add our OID to the hash
            refereeFooter.refCount--;
            bloom.put(fragmentFooter.oid);
            refereeFooter.deletedRefs = bloom.getKeys();

            // write out our changes to the referee header
            ByteBuffer newFieldsBuffer = serializeFooter(refereeFooter);
            try {
                DAAL refereeDAAL = refereeFrag.getDAAL();
                refereeDAAL.write(newFieldsBuffer,
                                  refereeDAAL.length() - refereeFooter.SIZE
                                  );

                if (LOG.isLoggable(Level.INFO)) {
                    String msg = "Refcount " +
                        refereeFrag.getOID().toExternalHexString() + " " +
                        refereeFooter.fragNum + ":" +
                        oldCount + "->" + refereeFooter.refCount + "," +
                        refereeFooter.maxRefCount + " " +

                        // Print the new ref string first, since that
                        // often grows so big it gets truncated and
                        // we'd like to see the new info if we have to
                        // choose.
                        refereeFrag.getRefFieldsString() + " <- " + oldRefs;
                    LOG.info(msg);
                }

            } catch (DAALException de) {
                throw new OAException("Failed to update footer for refereeFrag " +
                                      fragmentFooter.fragNum + " oid " +
                                      linkoid + ": " + de
                                      );
            } finally {
                if(newFieldsBuffer != null) {
                    ByteBufferPool.getInstance().checkInBuffer(newFieldsBuffer);
                }
            }

            // if that referee fragment still has other references, then return
            if(refereeFooter.refCount > 0) {
                LOG.info("decremented refcount of " + refereeFooter.oid +
                           " but it is still " + refereeFooter.refCount +
                           " so we are keeping it around for now");
                return moreChunks;
            }

        } finally {
            refereeFrag.unlock();
            refereeFrag.close();
        }


        // if refCount has dropped to 0, do a safety check to see if
        // we can delete the fragemnt
        // LOG.info("refCount is 0 (maxRefCount is " +
        // refereeFooter.maxRefCount + ") for " +
        //  refereeFooter.oid + " so running safety check...");
        int realMaxRefCount = safetyCheck(refereeFooter.maxRefCount,
                                          false,
                                          refereeFrag,
                                          layout,
                                          linkoid
                                          );
        boolean safe = (realMaxRefCount == -1);
        // LOG.info("safe is " + safe + " and realMaxRefCount is " +
        // realMaxRefCount);

        // if the safety check failed, correct  things, and return
        if(!safe) {
            // LOG.info("running safety check correct + return w/o delete...");
            // first correct any fragments that had missed increments
            safetyCheck(realMaxRefCount, true, refereeFrag, layout, linkoid);

            // and then just return because refCount > 0 still
            return moreChunks;
        }

        // if we found enough fragments and they all pass the safety
        // check, then delete that fragment as we were the last to
        // reference it and we are now deleted
        try {
            //LOG.finest("refcount == 0 so deleting data object " +
            //refereeFooter.oid);
            refereeFrag.delete(deletionTime);
            //LOG.finest("delete of " + refereeFooter.oid + " was successful");

        } catch (FragmentNotFoundException fnfe) {
            throw new DeleteSafetyCheckFailedException("can't delete: " + fnfe);

        } catch (OAException oe) {
            throw new DeleteSafetyCheckFailedException("can't delete: " + oe);
        }
        return moreChunks;
    }

    // Two modes - safety check (returns max) or correct.  safety
    // check returns -1 if safe, else the max maxRefCount found.
    // correct returns nothing useful.
    int safetyCheck(int maxRefCount,
                    boolean correct,
                    FragmentFile refereeFrag,
                    Layout layout,
                    NewObjectIdentifier linkoid) throws OAException
    {
        int checked = 0;
        boolean safe = true;
        boolean alreadyDeleted = false;
        int realMaxRefCount = maxRefCount;

        for(int safetyFrag = 0;
            safetyFrag < fragmentFooter.reliability.getTotalFragCount();
            safetyFrag++)
        {
            if(correct || (safetyFrag != fragmentFooter.fragNum)) {
                Disk safetyDisk = layout.getDisk(safetyFrag);

                // TODO?: do we need to check the state of this disk
                // before using it?

                // Next create a new FragmentFile for our referred-to
                // fragment
                FragmentFile safetyRefereeFrag;
                safetyRefereeFrag = new FragmentFile(linkoid,
                                                     safetyFrag,
                                                     safetyDisk,
                                                     fragmentFooter.reliability);

                // open the fragment file if we can
                // if we can't healing will take care of this later
                ///LOG.info("XXX safety check [ " + correct + " ]
                ///opening " + linkoid +
                //   " frag " + fragmentFooter.fragNum);

                boolean didCheck = false;
                try { // this is just so we can close safetyReferee in a finally block

                    try {
                        if(correct) {
                            // maybe updated ref fields so make sure
                            // it's locked
                            safetyRefereeFrag.openReadWriteLocked();
                        } else {
                            safetyRefereeFrag.open();
                        }
                        didCheck = true;

                    } catch (DeletedFragmentException dfe) {
                        LOG.finest("Another thread has already deleted fragment " +
                                   safetyFrag);
                        alreadyDeleted = true;
                        didCheck = true;

                    } catch (FragmentNotFoundException fnfe) {
                        LOG.warning("SAFETY CHECK [ " + correct +
                                    " ]: Can't find referred-to object "+
                                    linkoid +
                                    " fragment " +
                                    fragmentFooter.fragNum + " on disk "+
                                    printDisk(safetyDisk) +  " to delete: " + fnfe);
                        didCheck = false;

                    } catch (ObjectCorruptedException oce) {
                        // TODO?: what really is the right behaviour here?
                        LOG.warning("SAFETY CHECK [" + correct +
                                    "]: Can't refDec corrupted referred-to fragment" +
                                    daal + oce);
                        didCheck = false;
                    }

                    // get the footer out
                    FragmentFooter safetyRefereeFooter = safetyRefereeFrag.getFooter();
                    if(didCheck) {
                        // only actually used for check mode,
                        // not in correct mode
                        checked++;
                    }

                    if (!alreadyDeleted)
                    {
                        if (correct) // we are in correction mode, so correct
                        {
                            int failedIncCount = maxRefCount - safetyRefereeFooter.maxRefCount;
                            if (failedIncCount > 0)
                            {
                                // this frag missed failedIncCount increments
                                // so increment both inc fields by that much to make them correct
                                LOG.info("old " + safetyRefereeFrag.getRefFieldsString() +
                                         " in " + safetyRefereeFrag.getOID());
                                safetyRefereeFooter.refCount += failedIncCount;
                                safetyRefereeFooter.maxRefCount += failedIncCount;

                                //  write out the correction
                                ByteBuffer fieldsBuffer = serializeFooter(safetyRefereeFooter);
                                try {
                                    DAAL target = safetyRefereeFrag.getDAAL();
                                    target.write(fieldsBuffer,
                                                 target.length() - FragmentFooter.SIZE
                                                 );
                                    LOG.info("new " + safetyRefereeFrag.getRefFieldsString() +
                                             " in " + safetyRefereeFrag.getOID());

                                } catch (DAALException de) {
                                    throw new OAException("SAFETY CHECK [ " + correct + " ]: " +
                                                          "Failed correcting  incs in frag " +
                                                          safetyFrag + " of referee " +
                                                          safetyRefereeFooter.oid);
                                } finally {
                                    if(fieldsBuffer != null) {
                                        ByteBufferPool.getInstance().checkInBuffer(fieldsBuffer);
                                    }
                                }
                            }
                        } else { // we are in safety check mode, so just check that max is right

                            // if any disagree, the safety test fails
                            // track the highest max field, and we'll
                            // use it to correct in a 2nd pass
                            if((safetyRefereeFooter != null) &&
                               (safetyRefereeFooter.maxRefCount != maxRefCount))
                            {
                                LOG.warning(safetyRefereeFooter.oid + " frag " + safetyFrag +
                                            " max " + safetyRefereeFooter.maxRefCount +
                                            " does not match current max " + maxRefCount +
                                            " so not safe - correcting.");
                                safe = false;
                                if(safetyRefereeFooter.maxRefCount > maxRefCount) {
                                    realMaxRefCount = safetyRefereeFooter.maxRefCount;
                                }
                            }
                        }
                    }
                } finally {
                    if(safetyRefereeFrag != null) {
                        safetyRefereeFrag.unlock();
                        safetyRefereeFrag.close();
                    }
                }
            }
        }

        if(correct) {
            // correction mode always returns -1
            return -1;
        }

        // if we can't safety check enough fragments, throw an error
        // TODO - it is safe as long as we check this many right?
        // We don't have to check all - better be b/c then we can't delete
        // w/ even one disk down!
        // the -1 is b/c we always pass our own check
        if(checked < 2*fragmentFooter.reliability.getRedundantFragCount() + 1 - 1) {
            throw new DeleteSafetyCheckFailedException
                ("Did not check enough frags in safety test " + checked + " not " +
                 (2*fragmentFooter.reliability.getRedundantFragCount() + 1 - 1)
                 );
        }

        // safety check mode returns -1 if safe, else the max maxRefCount found
        if(safe) {
            return -1;
        } else {
            return realMaxRefCount;
        }
    }

    /**
     * Increments refCount and maxRefCount fields, accounting for any
     * missed updates. (Remember that "maxRefCount" means the number
     * of times the refCount has been incremented.) By comparing the
     * fragment's maxRefCount to the parameter (which is the number of
     * times that the other fragments have been incremented) we know
     * exactly how many increments were missed by this fragment. This
     * ensures that refCount can be higher than the real refcount (if
     * we missed any decrements), but never lower.
     *
     * Note that the fragment is assumed to already be locked by this
     * thread (since all fragments need to be locked before any of
     * them can be updated; the lock must not be given up while the
     * totalIncrements value is live).
     *
     * Throws if the field is -1, meaning a top-of-ref-chain object.
     * Also throws if the object is already deleted.
     */
    void incRefCount(int totalIncrements) throws
        FragmentNotFoundException,
        NotRefereeObjectException,
        IncRefFailedException,
        OAException
    {
        setOp(OP_REFINC);

        // The fragment has already been opened for read/write, and locked.

        if(fragmentFooter.refCount == -1 || fragmentFooter.maxRefCount == -1) {
            throw new NotRefereeObjectException("Can't inc a referrer in 1.0");
        }

        if (totalIncrements < fragmentFooter.maxRefCount)
            // How can this footer's refcount have been incremented
            // *more* times than the largest?
            LOG.severe("Hey! Total object increments = " + totalIncrements + 
                       " but footer's maxRefCount = " +
                       fragmentFooter.maxRefCount + "?!! for " +
                       getOID().toExternalHexString());

        ByteBuffer newFieldsBuffer = null;
        try {
            int numMissedIncs = totalIncrements - fragmentFooter.maxRefCount;
            String oldRefs = null;
            int oldCount = fragmentFooter.refCount;
            if (LOG.isLoggable(Level.INFO))
                oldRefs = getRefFieldsString();

            fragmentFooter.refCount += numMissedIncs + 1;
            fragmentFooter.maxRefCount = totalIncrements + 1;

            // Write out the updated footer
            newFieldsBuffer = serializeFooter();
            daal.write(newFieldsBuffer, daal.length() - fragmentFooter.SIZE);

            if (LOG.isLoggable(Level.INFO)) {
                String msg = "Refcount " +
                    getOID().toExternalHexString() + " " +
                    fragmentFooter.fragNum + ":" +
                    oldCount + "->" + fragmentFooter.refCount + "," +
                    fragmentFooter.maxRefCount + " ";
                if (numMissedIncs == 0)
                    msg += "--";
                else if (numMissedIncs > 0)
                    msg += "+" + numMissedIncs;
                else
                    msg += numMissedIncs;

                // Print the new ref string first, since that often
                // grows so big it gets truncated and we'd like to see
                // the new info if we have to choose.
                msg += " " +  getRefFieldsString() + " <- " + oldRefs;

                LOG.info(msg);
            }

        } catch (DAALException de) {
            throw new OAException("Failed to increment footer fields: " + de);

        } finally {
            if(newFieldsBuffer != null) {
                ByteBufferPool.getInstance().checkInBuffer(newFieldsBuffer);
            }
        }
    }

    public void openAndLock() throws FragmentNotFoundException, OAException {
        try {
            openReadWriteLocked();
        } catch (DeletedFragmentException dfe) {
            String err = "OID " + daal.getOID().toExternalHexString() +
                "frag " + fragmentFooter.fragNum + " has been deleted: ";
            LOG.warning(err + daal);
            throw new OAException(err);

        } catch(ObjectCorruptedException oce) {
            String err = "OID " + daal.getOID().toExternalHexString() +
                "frag " + fragmentFooter.fragNum + " is corrupted! ";
            LOG.warning(err + daal);
            throw new OAException(err);
        }

        if (fragmentFooter == null)
            throw new InternalException("Open OK but FragmentFooter null! " +
                                        daal);
    }

    public void unlockAndClose()
            throws FragmentNotFoundException, OAException {
        unlock();
        close();
    }

    // Get the compliance retention time for the object
    public long getRetentionTime()
        throws FragmentNotFoundException, OAException
    {
        // Open the file if it's not already open
        if (fragmentFooter == null) {
            try {
                open();
                close();
            } catch (DeletedFragmentException dfe) {
                String err = "Fragment [" + daal.getFragNum() + "] is deleted";
                LOG.warning(err + daal);
                throw new OAException(err);

            } catch(ObjectCorruptedException oce) {
                String err = "Fragment [" + daal.getFragNum() + "] is corrupted";
                LOG.warning(err + daal);
                throw new OAException(err);
            }
        }

        return fragmentFooter.retentionTime;
    }

    // Open and set the retention time
    public void setRetentionTime(long date)
        throws FragmentNotFoundException, OAException
    {
        // Open the file if it's not already open
        if (fragmentFooter == null) {
            try { // unlock and close block

                try {
                    openReadWriteLocked();

                } catch (FragmentNotFoundException fnfe) {
                    bad = true;
                    throw fnfe;

                } catch (DeletedFragmentException dfe) {
                    String err = "Fragment [" + daal.getFragNum() + "] is deleted";
                    LOG.warning(err + daal);
                    throw new OAException(err);

                } catch(ObjectCorruptedException oce) {
                    String err = "Fragment [" + daal.getFragNum() + "] is corrupted";
                    LOG.warning(err + daal);
                    throw new OAException(err);
                }

                ByteBuffer newFieldsBuffer = null;
                try {
                    fragmentFooter.retentionTime = date;
                    newFieldsBuffer = serializeFooter();
                    daal.write(newFieldsBuffer, daal.length() - fragmentFooter.SIZE);
                } catch (DAALException de) {
                    throw new OAException("Failed to set retention time: " + de);

                } finally {
                    if(newFieldsBuffer != null) {
                        ByteBufferPool.getInstance().
                        checkInBuffer(newFieldsBuffer);
                    }
                }

            } finally {
                unlock();
                close();
            }

            // Just set the retention time if the file is already open
        } else {
            fragmentFooter.retentionTime = date;
        }
    }

    ByteBuffer serializeFooter()
    {
        return serializeFooter(fragmentFooter);
    }

    /** Should this be in fragmentFooter?) */
    static ByteBuffer serializeFooter(FragmentFooter footer)
    {
        // NOTE: Caller is responsible for returning this
        ByteBuffer footerarchive =
            ByteBufferPool.getInstance().checkOutBuffer(FragmentFooter.SIZE);

        ByteBufferCoder coder = new ByteBufferCoder(footerarchive);
        coder.encodeKnownClassCodable(footer);

        // Done filling buf., so prep. it for being written
        footerarchive.flip();
        return footerarchive;
    }

    public boolean isNull()
    {
        return (this == FragmentFile.NULL);
    }

    public boolean isDeleted()
    {
        return(fragmentFooter != null && fragmentFooter.isDeleted());
    }

    public String toString()
    {
        if (daal != null) {
            return daal.toString();
        } else {
            return null;
        }
    }

    // Codable //

    /**
     * Used when writing context files during store operation
     */
    public void encode(Encoder encoder)
    {
        encoder.encodeKnownClassCodable(daal.getOID());
        encoder.encodeInt(daal.getFragNum());
        encoder.encodeKnownClassCodable(rel);
        encoder.encodeKnownClassCodable(daal.getDisk());
        encoder.encodeBoolean(bad);
        encoder.encodeLong(currentOffset);
        encoder.encodeKnownClassCodable(fragmentFooter);
        if (fragmentFooter.checksumAlg != ChecksumAlgorithm.NONE) {
            encoder.encodeKnownClassCodable(checksumContext);
        }
    }

    /**
     * Used when reeading context files during store operation for
     * failure recovery
     */
    public void decode(Decoder decoder)
    {
        NewObjectIdentifier oid = new NewObjectIdentifier();
        decoder.decodeKnownClassCodable(oid);

        int fragNum = decoder.decodeInt();
        rel = new ObjectReliability();
        decoder.decodeKnownClassCodable(rel);

        Disk disk = Disk.getNullDisk(-1, -1, -1, -1, -1);
        decoder.decodeKnownClassCodable(disk);

        bad = decoder.decodeBoolean();
        currentOffset = decoder.decodeLong();

        fragmentFooter = new FragmentFooter();
        decoder.decodeKnownClassCodable(fragmentFooter);
        if (fragmentFooter.checksumAlg != ChecksumAlgorithm.NONE) {
            checksumContext = new ChecksumContext();
            decoder.decodeKnownClassCodable(checksumContext);
        } else {
            checksumContext = null;
        }
        daal = instantiateDAAL(disk, oid, fragNum);
        if (!bad) {
            try {
                daal.rwopen();
                daal.truncate(currentOffset);

            } catch (FragmentNotFoundException fnfe) {
                LOG.warning("Can't find ctx file on " + daal);
                close();
                bad = true;

            } catch (DAALException e) {
                LOG.warning("Error opening ctx file on " + daal);
                close();
                bad = true;
            }
        }
    }

    public void dispose()
    {
        close();

        // Release references to all members
        rel = null;
        fragmentFooter = null;
    }

    public Disk getDisk() {
        return daal.getDisk();
    }

    protected void init(NewObjectIdentifier oid,
                        int fragNum,
                        Disk disk,
                        ObjectReliability rel)
    {
        checkOID(oid);

        this.daal = instantiateDAAL(disk, oid, fragNum);
        this.rel = rel;
        this.bad = false;
    }

    private String printDisk(Disk disk) {
        return (disk != null) ? disk.getId().toStringShort() : "NULL";
    }

    private String printDisk() {
        return printDisk(daal.getDisk());
    }

    private void lock() throws OAException
    {
        try {
            daal.lock();
        } catch (DAALException de) {
            throw new OAException(de);
        }
    }

    /**
     * unlocks this fragment file.  doesn't throw anything, just logs
     */
    private void unlock()
    {
        try {
            daal.unlock();
        } catch (DAALException de) {
            LOG.warning("failed to unlock fragment " + daal + de);
        }
    }

    protected void checkOID(NewObjectIdentifier oid) throws IllegalArgumentException
    {
        if (oid == null || oid == NewObjectIdentifier.NULL) {
            throw new IllegalArgumentException("NULL oid");
        }
    }

    protected FragmentFooter getFragmentFooter() throws OAException
    {
        if (fragmentFooter == null) {
            throw new OAException("Null fragment footer, interrupted write perhaps?");
        }
        return fragmentFooter;
    }

    protected FooterExtension readFooterExtension() throws OAException {
        try {
            return getDAAL().readFooterExtension();
        } catch (DAALException de) {
            throw new OAException(de);
        }
    }

    protected void writeFooterExtension(FooterExtension fe) throws OAException {
        try {
            getDAAL().writeFooterExtension(fe);
        } catch (DAALException de) {
            throw new OAException(de);
        }
    }

    ObjectReliability getObjectRel() {
            return rel;
    }

    public boolean bad() {
        return bad;
    }

    public void setLastReadBad() {
        lastReadBad = true;
    }

    boolean lastReadBad() {
        return lastReadBad;
    }

    void resetLastReadBad() {
        lastReadBad = false;
    }

    ChecksumContext getChecksumContext() {
        return checksumContext;
    }

    String getRefFieldsString() {
        return "(" +
        fragmentFooter.fragNum + ":" +
        fragmentFooter.refCount + ":" +
        fragmentFooter.maxRefCount + ":" +
        fragmentFooter.deletedRefs + ":" +
        fragmentFooter.deletionTime + ":" +
        fragmentFooter.size +
        ")";
    }

    protected void setOp(int op) {
      this.op = op;
    }

    protected int getOp() {
      return op;
    }

    //
    // STATS measurements - NOT IMPLEMENTED
    //
    public long getCreateTime() {
        return -1;
    }
    public long getWriteTime() {
        return 0;
    }
    public long getWriteBytes() {
        return 0;
    }
    public void resetWriteMeasurements() {
    }
    public long getReadTime() {
        return 0;
    }
    public long getReadBytes() {
        return 0;
    }
    public void resetReadMeasurements() {
    }
    public long getCloseTime() {
        return -1;
    }
    public long getRenameTime() {
        return -1;
    }


    //
    // PRIVATE
    //

    /*
     * create a fragment file from a peer.
     * If the fragment already exists, it is overwritten.
     *
     */
     private void create(FragmentFile peer) throws OAException
     {
         if(getOp() == OP_NOOP) {
             setOp(OP_STORE);
         }

         short cAlgNum = peer.getFooter().checksumAlg;

         if (cAlgNum != ChecksumAlgorithm.NONE) {
             ChecksumAlgorithm cAlg = ChecksumAlgorithm.getInstance(cAlgNum);
             checksumContext = cAlg.createContext();
         } else {
             checksumContext = null;
         }

         fragmentFooter = peer.getFooter();
         getFragmentFooter().fragNum = daal.getFragNum();

         try {
             daal.create();
         } catch (DAALException de) {
             close();
             bad = true;
             throw new OAException(de);
         }
     }

    /* 
     * Open the fragment file in r/w mode.
     * If the fragment does not exist, FragmentNotFoundException is thrown
     *
     * Note that this method does no locking; it is understood that
     * the caller will take care of synchronization.
     */
    private long openReadWriteIfExists() throws
        FragmentNotFoundException,
        DeletedFragmentException,
        ObjectCorruptedException,
        OAException
    {
        return openReadWrite(false); // don't lock
    }

    /*
     * Open the fragment file in r/w mode, but lock it before reading it.
     * If the fragment does not exist, FragmentNotFoundException is thrown
     */
    private long openReadWriteLocked() throws
        FragmentNotFoundException,
        DeletedFragmentException,
        ObjectCorruptedException,
        OAException
    {
        return openReadWrite(true); // lock it
    }

    private long openReadWrite(boolean lockIt) throws
        FragmentNotFoundException,
        DeletedFragmentException,
        ObjectCorruptedException,
        OAException
    {
        assert(daal != null);
        try {
            bad = true;
            daal.rwopen();
            if (lockIt)
                lock();
        } catch (DAALException de) {
            if (lockIt)
                unlock();
            close();
            throw new OAException(de);
        }

        // If we got here, we opened the fragment OK, so we can reset
        // "bad". If readFooter has problems reading the footer, it
        // will set bad to true again.
        bad = false;

        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer readBuffer = pool.checkOutBuffer(CACHE_READ_SIZE);
        try {
            readFooter(readBuffer);
        } finally {
            pool.checkInBuffer(readBuffer);
        }

        if (LOG.isLoggable(Level.FINEST)) {
            if (lockIt) {
                LOG.finest("Locked fragment " + fragmentFooter.oid +
                  ", frag " + fragmentFooter.fragNum);
            } else {
                LOG.finest("Unlocked fragment " + fragmentFooter.oid +
                  ", frag " + fragmentFooter.fragNum);
            }
        }
        return fragmentFooter.creationTime;
    }

    /*
     * Read and initialize the footer and the read cache from the fragment file
     */
    protected void readFooter(ByteBuffer readBuffer) throws
        FragmentNotFoundException,
        DeletedFragmentException,
        ObjectCorruptedException,
        OAException
    {
        if(getOp() == OP_NOOP) {
            setOp(OP_RETRIEVE);
        }

        // Byte buffer pool to use
        ByteBufferPool pool = ByteBufferPool.getInstance();

        // read the footer -
        // Figure out how big the fragment file is, and calculate
        // size of data block
        // throw FragmentNotFoundException if fails
        long fileLength = 0;
        try {
            fileLength = daal.length();
            if(fileLength < FragmentFooter.SIZE) {
                String err = "File too small - no footer: " + daal + ": " + fileLength;
                LOG.warning(err);
                bad = true;
                close();
                throw new FragmentNotFoundException(err);
            }

            long readOffset = 0;
            if (fileLength > readBuffer.capacity()) {
                readOffset = fileLength - readBuffer.capacity();
            }
            daal.read(readBuffer, readOffset);

        } catch (DAALException de) {
            String err = "Failed to read footer " + daal;
            LOG.warning(err);
            bad = true;
            close();
            throw new FragmentNotFoundException(err);
        }

        //
        // The buffer should be <...DATA>[CHECKSUM BLOCK]<FOOTER>
        //

        // Extract the fragment's footer
        readBuffer.flip();
        ByteBuffer footerSlice = readBuffer.slice();
        footerSlice.position(footerSlice.limit() - FragmentFooter.SIZE);
        ByteBufferCoder decoder = new ByteBufferCoder(footerSlice);
        fragmentFooter = new FragmentFooter();
        decoder.decodeKnownClassCodable(fragmentFooter);
        if (!fragmentFooter.isConsistent()) {
            bad = true;
            close();
            throw new ObjectCorruptedException("Fragment footer is corrupted");
        }
        // Set the limit to before the footer data for further processing
        readBuffer.limit(readBuffer.limit() - FragmentFooter.SIZE);

        /*
         // Temporary debugging
         if(fragmentFooter.fragNum == 0) {
             LOG.info("fragFooter oid is " + fragmentFooter.oid);
         }
         */

        if(fragmentFooter.isDeleted()) {
            close();
            throw new DeletedFragmentException(fragmentFooter.fragNum + " has been deleted.");
        }

        // Process the checksums if enabled
        if(fragmentFooter.checksumAlg != ChecksumAlgorithm.NONE &&
           fragmentFooter.numPreceedingChecksums > 0)
        {
            // Read the first checksum block. The first checksum block is
            // always written before the fragment footer. The footer has the
            // number of checksums before it.

            ChecksumAlgorithm algorithm =
            ChecksumAlgorithm.getInstance(fragmentFooter.checksumAlg);
            // Get the number of bytes occupied by the checksums
            long numbytes = algorithm.getChecksumBlockLength
                (fragmentFooter.numPreceedingChecksums);

            ByteBuffer checksumBuffer =
                pool.checkOutBuffer(ChecksumBlock.checksumBlockSize);
            ChecksumBlock.initializeChecksumBuffer(checksumBuffer);
            checksumBuffer.limit((int)numbytes);

            // Position the buffer to the checksum block and copy it
            ByteBuffer checksumSlice = readBuffer.slice();
            checksumSlice.position(checksumSlice.limit() - (int)numbytes);
            checksumBuffer.put(checksumSlice);

            // Set the buffer limit to before the checksums for further
            // processing
            readBuffer.limit(readBuffer.limit() - (int)numbytes);

            checksumBuffer.position(0);
            checksumBuffer.limit(ChecksumBlock.checksumBlockSize);
            // Create the checksum block and set it in the context
            ChecksumBlock block = null;
            try {
                block = algorithm.createChecksumBlock(checksumBuffer);
            } catch (IllegalArgumentException e) {
                bad = true;
                close();
                throw new ObjectCorruptedException("Fragment footer is corrupted," +
                                                   " can't create checksum block." +
                                                   " Exception = " + e);
            }

            // Initialize the checksum context
            checksumContext = algorithm.createContext();
            checksumContext.setReadChecksumBlockAndOffset(block, 0);
            checksumContext.firstChecksumBlockOffset =
                fileLength - FragmentFooter.SIZE - numbytes;

            pool.checkInBuffer(checksumBuffer);

            /*
             if (fragmentFooter.fragNum == 0) {
                 LOG.info(fragmentFooter.oid + " has checksums. Read " +
                          "checksum block zero of " + numbytes +
                          " bytes from offset " + (fileLength - numbytes));
             }
             */
        } else {
            checksumContext = null;
        }

        this.rel = fragmentFooter.reliability;
        this.bad = false;

        // Cache the object if it is the first fragment and its length is
        // smaller than Fragmenter.MAX_BOTROS_BLOCK_SIZE
        if ((fragmentFooter.fragNum == 0) &&
            (fragmentFooter.size != OAClient.MORE_CHUNKS) &&
            (fragmentFooter.size < Fragmenter.MAX_BOTROS_BLOCK_SIZE) &&
            readBuffer.hasRemaining())
        {
            cachedData = pool.checkOutBuffer(readBuffer.remaining());
            cachedData.put(readBuffer);
            cachedData.rewind();

            // Checksum the data to make sure that it is consistent
            if (fragmentFooter.checksumAlg != ChecksumAlgorithm.NONE) {
                if(checksumContext.readChecksumBlock == null) {
                    throw new
                    ObjectCorruptedException("OID [" + fragmentFooter.oid +
                                             "]: Can't read footer chceksum block");
                }
                ByteBuffer toCheck = cachedData.duplicate();
                toCheck.rewind();
                ChecksumAlgorithm algorithm =
                    ChecksumAlgorithm.getInstance(fragmentFooter.checksumAlg);
                try {
                    algorithm.verify(toCheck,
                                     0,
                                     checksumContext.readChecksumBlock);
                } catch (ObjectCorruptedException e) {
                    LOG.warning("OID [" + fragmentFooter.oid +
                                "]: Can't verify frooter checksum block.");
                    pool.checkInBuffer(cachedData);
                    cachedData = null;
                    throw new
                        ObjectCorruptedException("OID [" + fragmentFooter.oid +
                                                 "]: Data block is " +
                                                 "corrupted. Not caching...");
                }
            }
        }
    }

    private DAAL getDAAL() throws OAException {
        if (daal == null) {
            throw new OAException("daal object not initialized");
        }
        return daal;
    }

    protected DAAL instantiateDAAL(Disk disk, NewObjectIdentifier oid, int fragNum)
    {
        Exception error = null;
        try {
            Object[] params = { disk, oid, new Integer(fragNum) };
            return (DAAL) DAALFactory.newInstance(params);

        } catch (InstantiationException ie) {
            error = ie;
        } catch (IllegalAccessException ae) {
            error = ae;
        } catch (InvocationTargetException te) {
            error = te;
        }
        LOG.severe("failed to instantiate daal " + error);
        throw new IllegalArgumentException(error);
    }

    // FIXME need to be cleanup
    public static final String FRAGFILE_PROP = "fragfile.Class";

    // PROTECTED MEMBERS //

    // Constants
    protected static final Logger LOG = Logger.getLogger(FragmentFile.class.getName());
    protected static final int CACHE_READ_SIZE = 64*1024;

    // Encodeable/Decodable state
    protected ObjectReliability rel = null;
    protected boolean bad = false;
    protected boolean lastReadBad = false;
    protected long currentOffset = 0; // cur offset during writes
    protected FragmentFooter fragmentFooter = null;
    protected ChecksumContext checksumContext = null;

    // Reconstrucable state
    protected ByteBuffer cachedData = null;
    protected DAAL daal;

    // unit test - fault injection framework
    // FIXME if possible - don't add anymore to this list.
    protected int op = OP_NOOP;

    public static final int OP_NOOP = 0;
    public static final int OP_STORE = 1;
    public static final int OP_RETRIEVE = 2;
    public static final int OP_DELETE = 3;
    public static final int OP_REFINC = 4;
    public static final int OP_REFDEC = 5;
    public static final int OP_CNT = 6;
    public static final int OP_RETAIN = 7;
    public static final int OP_HOLD = 8;
    public static final int OP_UNHOLD = 9;

    // STATIC MEMBERS //
    private static final Constructor DAALFactory;
    protected static final FragmentFile NULL;

    static {

        NULL = new FragmentFile();
        NULL.daal = null;
        NULL.bad = true;
        NULL.checksumContext = null;
        NULL.fragmentFooter = new FragmentFooter();

        // get DAAL implementaton - default to NFS if we don't find one
        ClusterProperties config = ClusterProperties.getInstance();
        String className = config.getProperty(DAAL.DAAL_PROPERTY);
        try {
            Class cl;
            if (className == null) {
                LOG.info("Cannot find property " + DAAL.DAAL_PROPERTY +
                         " defaulting to NFS protocol");
                cl = NfsDAAL.class;
            } else {
                cl = Class.forName(className);
            }
            Class[] signature = { Disk.class, NewObjectIdentifier.class, Integer.class };
            DAALFactory = cl.getConstructor(signature);

        } catch (ClassNotFoundException ce) {
            LOG.severe("FATAL failed to find DAAL class " + className);
            throw new RuntimeException(ce);

        } catch (NoSuchMethodException me) {
            LOG.severe("FATAL cannot access DAAL class " + className);
            throw new RuntimeException(me);
        }
    }
}
