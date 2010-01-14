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
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.UnsupportedEncodingException;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.DeletedObjectException;
import com.sun.honeycomb.common.IncompleteObjectException;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.CapacityLimitException;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.coordinator.ContextConsumer;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.oa.bulk.stream.OAObjectSerializer;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;
import com.sun.honeycomb.oa.checksum.ChecksumBlock;
import com.sun.honeycomb.oa.hash.ContentHashAlgorithm;
import com.sun.honeycomb.oa.hash.ContentHashContext;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.multicell.lib.MultiCellLib;

/**
 * The ObjectArchive (OA) provides Honeycomb with scalable, reliable,
 * write-once, read-many (WORM) object storage. Objects are blocks of
 * user data, each of which has an associated identifier which is
 * issued by OA when stored, and which is later used to retrieve or
 * delete the object.  This OAClient class provides the interface for
 * storing, retrieving, and deleting.
 * TODO - maybe restoreCtxForRetrieve should take boolean for whether
 * or not to  read in footer
 * TODO - fewer if any ops should close or remove things, we should
 * ALSO: Make proper variables configurable (not just m+n)
 * ALSO ALSO: through all the right exceptions,like NoSuchObjectException
 * ALSO ALSO ALSO: The design calls for open deleting a partially deleted obj.
 * ALSO TODO: add "calc_hash" boolean to create, and respect it
 */
public class OAClient implements ContextConsumer {

    // SINGLETON METHODS //

    /**
     * OAClient is a singleton.  The first time this method is called,
     * an instance is created and returned.  Subsequent calls
     * return a reference to the same object.
     */
    public static OAClient getInstance() {
        synchronized(LOG) {
            if (oaclient == null) {
                // This is the first time getInstance has been called
                oaclient = new OAClient(false);
            }
        }
        return oaclient;
    }

    /**
     * OAClient for testing is a singleton. The first time this method is
     * called, an instance is created and returned. Subsequent calls return
     * a reference to the same object.
     */
    public static OAClient getTestInstance() {
        synchronized(LOG) {
            if (oaTestClient == null) {
                // This is the first time getInstance has been called
                oaTestClient = new OAClient(true);
            }
        }
        return oaTestClient;
    }

    /**
     * This static method can be used to see whether the OAClient
     * singleton has been instantiated yet or not (in other words,
     * whether getInstance has ever been called before or not).
     */
    public static boolean isInstantiated() {
        return !(oaclient == null);
    }

    // STORE METHODS //
    public NewObjectIdentifier create(long size, NewObjectIdentifier link,
                                      int layoutMapId, boolean isMetadata,
                                      boolean isRefRoot, int autoCloseMillis,
                                      long retentionTime, long expirationTime,
                                      byte shred, Context ctx)
        throws ArchiveException {
        return create(size,
                      link,
                      null,
                      layoutMapId,
                      isMetadata,
                      isRefRoot,
                      autoCloseMillis,
                      CTIME_NOW,
                      NOT_DELETED,
                      retentionTime,
                      expirationTime,
                      shred,
                      configuredChecksumAlgorithm,
                      NOT_RECOVERY,
                      ctx,
                      null);
    }

    /**
     * Create with these defaults: always retain, never expire, do not shred
     */
    public NewObjectIdentifier create(long size, NewObjectIdentifier link,
                                      int layoutMapId, boolean isMetadata,
                                      boolean isRefRoot,
                                      int autoCloseMillis, Context ctx)
        throws ArchiveException {
        return create(size,
                      link,
                      layoutMapId,
                      isMetadata,
                      isRefRoot,
                      autoCloseMillis,
                      ALWAYS_RETAIN,
                      NEVER_EXPIRE,
                      DO_NOT_SHRED,
                      ctx);
    }

    /**
     * Create with even defaults: explicit close
     */
    public NewObjectIdentifier create(long size, NewObjectIdentifier link,
                                      int layoutMapId, boolean isMetadata,
                                      boolean isRefRoot, Context ctx)
        throws ArchiveException {
        return create(size,
                      link,
                      layoutMapId,
                      isMetadata,
                      isRefRoot,
                      Coordinator.EXPLICIT_CLOSE,
                      ctx);
    }

    /**
     * If you know you are creating a data object, you can even do this
     */
    public NewObjectIdentifier createData(long size, Context ctx)
        throws ArchiveException {
        return create(size,
                      NewObjectIdentifier.NULL,
                      CHOOSE_LAYOUT,
                      false,
                      false,
                      ctx);
    }

    /** Syncs ctx and any data cached in the kernel to disk to
     * establish a recovery point that store can be resumed from in
     * case of a fail-over.  In future versions, if we add buffering
     * to OA (see Future Work) commit will sync those buffers to disk.
     */
    public void commit(Context ctx) throws ArchiveException {
        try {
            commit(true, ctx);
        } catch (OAException e) {
            throw new ArchiveException("Error in commit", e);
        }
    }

    /**
     * Writes data to a not-yet-committed object.  Because of the
     * kernel buffer cache on the client side of OA's NFS connection,
     * writes are _not_ guaranteed to hit disk.  Data is only
     * guaranteed to hit disk after a commit (see below).  On all but
     * the last write to an object, OA enforces that writes be OA
     * block-aligned. An OA block is N*64k, where N is a cluster
     * configuration value.  Once a non-aligned write occurs, all
     * future writes will throw an exception.  In the future OA may be
     * extended to buffer writes, in which case these restrictions
     * will be relaxed (see Future Work below). If the final write
     * comes in not aligned to an N-byte boundary, OA pads it with
     * zeros to an N-byte boundary.  Because we own the clients of OA,
     * we can write them to respect handle OA's block interface,
     * buffering writes if necessary
     */
    /**
     * ATTENTION: When passing ByteBuffer(s) to this method remember to check them
     *            back in on the caller side because read-only copies are maintained
     *            internally by the OAClient and if you write back to those 
     *            data will be corrupted in a very unpredictable manner and it 
     *            will be extremely hard to understand why. 
     *            
     * Use the following pattern:
     * 
     * ByteBuffer buff = ByteBufferPool.checkOutBuffer(size);
     * try { 
     *      OAClient.write(buff,ctx);
     * } catch {
     *      ByteBufferPool.checkInBuffer(size);
     * }
     */
    public void write(ByteBufferList newList, long offset, Context ctx)
        throws ArchiveException {
        // TODO - check offset
        append(newList, ctx);
    }

    /**
     * ATTENTION: When passing ByteBuffer(s) to this method remember to check them
     *            back in on the caller side because read-only copies are maintained
     *            internally by the OAClient and if you write back to those 
     *            data will be corrupted in a very unpredictable manner and it 
     *            will be extremely hard to understand why. 
     *            
     * Use the following pattern:
     * 
     * ByteBuffer buff = ByteBufferPool.checkOutBuffer(size);
     * try { 
     *      OAClient.write(buff,ctx);
     * } catch {
     *      ByteBufferPool.checkInBuffer(size);
     * }
     */
    public void write(ByteBuffer newBuf, long offset, Context ctx)
        throws ArchiveException {
        // TODO - check offset
        append(newBuf, ctx);
    }

    public void append(ByteBufferList newList, Context ctx)
        throws ArchiveException {
        append(newList, true, ctx);
    }

    public void append(ByteBuffer newBuf, Context ctx)
        throws ArchiveException {
        append(newBuf, false, ctx);
    }

    /*
     * Set the retention time on the object. The new date can be
     * either -1 (unspecified), a future date from now, or a future
     * date that's later than the currently set retention date on the
     * object.
     */
    public void setRetentionTime(NewObjectIdentifier initialOid,
                                 long newRetentionTime)
        throws ArchiveException {
        NewObjectIdentifier oid =
            new NewObjectIdentifier(initialOid.toString());

        // Get the current date
        long currentTime = (new Date()).getTime();

        // Date cannot be less than -1
        if (newRetentionTime < RETENTION_UNSPECIFIED) {
            throw new
                ArchiveException("Cannot set retention time for oid " +
                                 oid + " because the specified date " +
                                 newRetentionTime + " is invalid");
        }

        // Date cannot be in the past
        if ((newRetentionTime) != RETENTION_UNSPECIFIED &&
            (newRetentionTime < currentTime)) {
            throw new
                ArchiveException("Cannot set retention time for oid " +
                                 oid + " because the specified date " +
                                 newRetentionTime + " is in the past");
        }

        // Get the old date from the footer
        long oldRetentionTime = getRetentionTime(oid);

        // Cannot set to -1 if we already have a real date
        if ((newRetentionTime == RETENTION_UNSPECIFIED) &&
            (oldRetentionTime > RETENTION_UNSET)) {
            throw new
                ArchiveException("Cannot set retention time for oid " +
                                 oid + " because an uspecific time " +
                                 "cannot be set when a specific one" +
                                 "is already set");
        }

        // Cannot set to an earlier date than already set
        if ((oldRetentionTime > RETENTION_UNSET) &&
            (newRetentionTime < oldRetentionTime)) {
            throw new
                ArchiveException("Cannot set retention time for oid " + oid +
                                 " because an ealier time " +
                                 newRetentionTime +
                                 " cannot be set when the later time" +
                                 oldRetentionTime + " is already set");
        }

        // Now loop over the chunks and set the retention time. Do we
        // really need to do this for all chunks of an object? Or just
        // set it for the first (or last one)?
        boolean moreChunks = true;
        int chunkIndex = 0;
        long numChunks = 0;
        long objectSize = -1;
        while(moreChunks) {
            objectSize = setChunkRetentionTime(oid, newRetentionTime);
            numChunks = calcNumChunks(objectSize);
            if(++chunkIndex == numChunks) {
                moreChunks = false;
            } else {
                setChunkAndLayout(oid, chunkIndex);
            }
        }

        // Debug
        LOG.info("Updated retention time from " + oldRetentionTime +
                 " to " + newRetentionTime + " for " + initialOid);
    }

    // Set compliance retention time on a single chunk
    public long setChunkRetentionTime(NewObjectIdentifier oid,
                                      long retentionTime)
        throws ArchiveException {
        long objectSize = -1;

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        // Create the class which encapsulates and manages frag. files
        FragmentFileSet fset;
        try {
            fset = new FragmentFileSet(oid,
                                       layout,
                                       reliability,
                                       NOT_RECOVERY);
        } catch (OAException oae) {
            throw new ArchiveException("Failed to create fragment set " +
                                       "object for oid [" + oid + "]", oae);
        }

        try {
            fset.setRetentionTime(retentionTime);
            objectSize = getObjectSize(fset);
        } catch (OAException oae) {
            throw new ArchiveException("Failed to set retention time " +
                                       "for oid [ " + oid + "]", oae);
        }

        // Update the mtime for this chunk's oid in its footer extension
        updateExtensionModifiedTime(oid, System.currentTimeMillis());

        return objectSize;
    }

    // Update the Footer Extension modified time for an object
    public void updateExtensionModifiedTime(NewObjectIdentifier oid,
                                            long mtime)
        throws ArchiveException {

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        FooterExtensionFileSet fefSet = new FooterExtensionFileSet(oid,
                                            layout, reliability);
        try {
            fefSet.setLastModified(mtime);
        } catch (OAException oae) {
            throw new ArchiveException(oae);
        }
    }

    // Get the Footer Extension modified time for an object
    public long getExtensionModifiedTime(NewObjectIdentifier oid)
        throws ArchiveException {

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        FooterExtensionFileSet fefSet = new FooterExtensionFileSet(oid,
                                            layout, reliability);
        try {
            return fefSet.getLastModified();
        } catch (OAException oae) {
            throw new ArchiveException(oae);
        }
    }

    // Get the FooterExtension for an object. Used for backup.
    public FooterExtension getFooterExtension(NewObjectIdentifier oid)
        throws ArchiveException {

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        FooterExtensionFileSet fefSet = new FooterExtensionFileSet(oid,
                                            layout, reliability);
        try {
            return fefSet.getFooterExtension();
        } catch (OAException oae) {
            throw new ArchiveException(oae);
        }
    }

    // Put out the given FooterExtension to an object. Used for backup.
    public void putFooterExtension(NewObjectIdentifier oid,
                                   FooterExtension fe)
        throws ArchiveException {

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        FooterExtensionFileSet fefSet = new FooterExtensionFileSet(oid,
                                            layout, reliability);
        try {
            fefSet.putFooterExtension(fe);
        } catch (OAException oae) {
            throw new ArchiveException(oae);
        }
    }

    // Add a legal hold tag
    public void addLegalHold(NewObjectIdentifier oid, String legalHold)
        throws ArchiveException {

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        FooterExtensionFileSet fefSet = new FooterExtensionFileSet(oid,
                                            layout, reliability);

        // Get the UTF-8 bytes for the data
        try {
            byte[] data = legalHold.getBytes("UTF8");
            fefSet.add(fefSet.LEGAL_HOLD, data);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        } catch (OAException e) {
            throw new ArchiveException(e);
        }

        // Debug
        LOG.info("Added legal hold [" + legalHold + "] to " + oid);
    }

    // Remove a legal hold tag
    public void removeLegalHold(NewObjectIdentifier oid, String legalHold)
        throws ArchiveException {

    // Check to see if the legal hold exists first
    String holds[] = getLegalHolds(oid);
        if (holds == null) {
            throw new ArchiveException("Specified legal hold does not exist");
        }

    ArrayList holdsArray = new ArrayList(Arrays.asList(holds));

        if (!holdsArray.contains(legalHold)) {
            throw new ArchiveException("Specified legal hold does not exist");
        }

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        FooterExtensionFileSet fefSet = new FooterExtensionFileSet(oid,
                                            layout, reliability);

        // Get the UTF-8 bytes for the data
        try {
            byte[] data = legalHold.getBytes("UTF8");
            fefSet.remove(fefSet.LEGAL_HOLD, data);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        } catch (OAException e) {
            throw new ArchiveException(e);
        }

        // Debug
        LOG.info("Removed legal hold [" + legalHold + "] from " + oid);
    }

    /** To be called by the MD coordinator before addMetadata */
    public void incRefCount(NewObjectIdentifier initialoid)
        throws ArchiveException {
        NewObjectIdentifier oid =
            new NewObjectIdentifier(initialoid.toString());

        boolean moreChunks = true;
        int chunkIndex = 0;
        long numChunks = 0;
        long objectSize = -1;
        while(moreChunks) {
            objectSize = incChunkRefCount(oid);
            numChunks = calcNumChunks(objectSize);
            if (++chunkIndex == numChunks) {
                moreChunks = false;
            } else {
                setChunkAndLayout(oid, chunkIndex);
            }
        }
    }


    public void decRefFromReferee(NewObjectIdentifier initialoid)
        throws ArchiveException {

        NewObjectIdentifier oid =
            new NewObjectIdentifier(initialoid.toString());
        Layout layout =
          layoutClient.getLayoutForRetrieve(oid.getLayoutMapId()); 

        try {
             FragmentFileSet fset = new FragmentFileSet(oid,
              layout, reliability);
             int deleted = 
               fset.deleteRefFromReferee(System.currentTimeMillis());
            if (deleted < (2*reliability.getRedundantFragCount())+1) {
                throw new ArchiveException("Failed to decrement ref count " +
                  "from referee - only deleted " +
                  deleted + " fragments sucessfully for " + oid);
            }
        } catch (OAException oae) {
            throw new ArchiveException("Failed to create fragment set " +
                                       "object for oid [" + oid + "]", oae);
        }
    }

    /** Do an incRefCount on a single chunk */
    public long incChunkRefCount(NewObjectIdentifier oid)
        throws ArchiveException {
        // See FragmentFileSet.incRefCount for detailed comments on
        // how the system ensures that no ref count increments are
        // missed.

        // For multi-chunk objects, this code is called sequentially
        // for each chunk. So the refcount consistency checking is
        // done per chunk; this implies different chunks of an object
        // may have different ideas of the ref count but this is OK as
        // long as none of them are low.

        long objectSize = -1;

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        // Create the class which encapsulates and manages frag. files
        FragmentFileSet fset;
        try {
            fset = new FragmentFileSet(oid,
                                       layout,
                                       reliability,
                                       NOT_RECOVERY);
        } catch (OAException oae) {
            throw new ArchiveException("Failed to create fragment set " +
                                       "object for oid [" + oid + "]", oae);
        }

        // Tell the frag. file set to increment the refcount, retrying
        // as appropriate
        for (int i = 0; i < NUM_LOCK_RETRIES; i++) {
            try {
                fset.incRefCount();
                return getObjectSize(fset);
            }
            catch (FragmentFileSetLockFailedException e) {
                LOG.info("FragmentFileSet incRefCount failed (try " + i +
                         "), OID " + oid.toExternalHexString());
            }
            catch (OAException oae) {
                LOG.log(Level.WARNING,
                        "FragmentFileSet incRefCount failed (try " + i +
                        "), OID " + oid.toExternalHexString(),
                        oae);
            }

            // A random wait before we re-try to acquire the lock
            try {
                Thread.sleep(prng.nextInt(LOCK_RETRY_DELAY));
            } catch (InterruptedException e) {}
        }

        throw new ArchiveException("Failed to inc refCount for oid [ "
                                   + oid + "]");
    }

    // RETRIEVE METHODS //
    public SystemMetadata open(NewObjectIdentifier oid, Context ctx)
        throws ArchiveException {
        return open(oid, UNKNOWN_SIZE, null, ctx);
    }

    public SystemMetadata[] close(Context ctx, byte[] metadataField)
        throws ArchiveException {
        return close(ctx, metadataField, null, false);
    }

    public SystemMetadata[] close(Context ctx,
                                  byte[] metadataField,
                                  boolean moreChunks)
        throws ArchiveException {
        return close(ctx, metadataField, null, moreChunks);
    }

    public SystemMetadata[] close(Context ctx,
                                  byte[] metadataField,
                                  byte[] objectHash)
        throws ArchiveException {
        return close(ctx, metadataField, objectHash, false);
    }

    /**
     * A close for folks that don't have a metadataField
     */
    public SystemMetadata[] closeData(Context ctx) throws ArchiveException {
        return close(ctx,
                     new byte[FragmentFooter.METADATA_FIELD_LENGTH],
                     null);
    }

    public long read(ByteBuffer buf, long offset,  long length, Context ctx) throws ArchiveException {
        return read(buf, offset, length, false, ctx);
    }

    /**
     *  Reads a range from an object, first opening the object and
     *  filling the ctx if necessary.  NOTE: we might read some into
     *  the buffer, then hit a problem For this reason, we will only
     *  throw an exception if we have not yet written anything to
     *  buffer.  Once we have written a byte or more, the only
     *  indication of a problem will be a result that is shorter than
     *  the requested read.
     *
     *  The contract is:
     *  - offset must be block-aligned (64k*n today)
     *  - the length must be n-aligned (5 today)
     *     NOTE: this means offset+length may be > objectSize
     *  - buf.remaining() must be >= length
     *  - Once we have written >= 1 byte to buf, we won't throw on error
     *  - We return the number of user bytes written (excluding pad)
     *    NOTE: This means a happy read may return result < length
     *          So there is no good way of knowing the difference between
     *          that happeneing, and an error after some reads happened
     * - We may write padding to the buffer
     *
     * TODO:
     *  - Fix bug w/ return value - wrong (negative) when length really big
     *  - fix the issue with pasing a length that is bigger than the original
     *    object and make sure to fix all of the references to any read method
     *    in the oa client that do the "magic" on the length of the object.
     */
    public long read(ByteBuffer buf, long offset,  long length, boolean singleChunkRead,
                     Context ctx) throws ArchiveException {

        // TODO check for null stuff

        // Check some args (more checks later)

        if(offset < -1) {
            throw new IllegalArgumentException("offset " + offset +
                                               " is < -1");
        }

        if(length < -1) {
            throw new IllegalArgumentException("length " + offset +
                                               " is < -1");
        }

        if(length > buf.remaining()) {
            throw new IllegalArgumentException("length of " + length +
                                               "buf.remaining() of " +
                                               buf.remaining());
        }

        if(length % reliability.getDataFragCount() != 0) {
            throw new IllegalArgumentException("length of " + length +
                                               " is not aligned to required " +
                                               reliability.getDataFragCount());
        }

        // Get what we need out of ctx
        OAContext oactx =
            (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

        // The caller must have already called open to populate context
        if(oactx == null) {
            throw new IllegalArgumentException("Context is missing OA state");
        }

        if(offset % oactx.getFragmentSize() != 0) {
            throw new IllegalArgumentException("offset of " + offset +
                                               " is not aligned to required " +
                                               oactx.getFragmentSize());
        }

        if(length == 0) {
            return 0;
        }

        FragmentFileSet fset = oactx.getFragmentFileSet();

        // Remember the object size, we will need it
        long objectSize = oactx.getObjectSize();


        if(objectSize == 0) {
            buf.position(0);
            buf.limit(0);
            return 0;
        }

        /* Usually a read is done using the OID of the 1st chunk even
         * if the range desired is in a later chunk.  On recovery
         * however, we are reading and writing exactly 1 chunk which
         * is indicated in the fset being read, so we need to be sure
         * to use that chunk */

        int chunk = fset.getOID().getChunkNumber();

        if(!singleChunkRead) {
            /* Figure out what chunk we actually need to begin to read from */
            if(offset >= oactx.getChunkSize()) {
                chunk = (int) (offset/oactx.getChunkSize());
            }
        }

        // Adjust for padding if necessary (do this before messing w/ offset)
        long dataToRead = length;
        long pad = 0;

        /* padding calculations work differently if we are doing a
         * read using chunk 0 oid (reading from whole object), or not
         * (reading from a single chunk for recovery */

        if(!singleChunkRead) {
            if(offset + length > objectSize) {
                pad = (offset + length) - objectSize;
                dataToRead -= pad;
            }
        } else if(length < blockSize){ /* recovery is reading remainder from last (small) chunk */
            dataToRead = objectSize % blockSize;
            pad = length - dataToRead;
        }

        long prevChunkBytes = oactx.getChunkSize() * chunk;

        /* Again, we don't have to do this if recovery is already
         * giving is the oid w/ right chunk */

        if(!singleChunkRead) {

            // Update offset to be relative to the chunk
            offset -= prevChunkBytes;

        }

        // If the oa context is for the wrong chuunk, change oa contexts

        NewObjectIdentifier oid = fset.getOID();

        if(oid.getChunkNumber() != chunk) {

            // Save the content hash
            byte[] objectContentHash = oactx.getContentHash();

            oactx.dispose(); // Closes , etc

            // remove old OA context from ctx
            ctx.removePersistentObject(OAContext.CTXTAG);

            // Make the right oid
            setChunkAndLayout(oid, chunk);

            /** Open that OID - use the version that takes a size so we
             // don't have to re-lookup the size */
            open(oid, objectSize, objectContentHash, ctx);

            // Get what we need out of ctx
            oactx = (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

            if(oactx == null) {
                throw new IllegalStateException("Context is missing OA state");
            }

            fset = oactx.getFragmentFileSet();
        }

        long actuallyRead = 0;

        // We do reads from start
        long start = offset;

        // First, pull off as many normal, full OA blocks as possible

        while(length-actuallyRead > oactx.getBlockSize()) {
            // Since we know the range left to read is inside the
            // object (else we would have thrown an exeption right
            // away), and since the range is at least blockSize large,
            // and since offests are blockAligned, we must be reading
            // a normal, faull block
            long read;
            try {
                read = Fragmenter.getInstance().readAndDefragment
                    (buf,
                     start,
                     oactx.getBlockSize(),
                     fset,
                     false,
                     oactx.getBlockSize(),
                     oactx);
            } catch (OAException oae) {
                throw new ArchiveException(oae);
            }

            actuallyRead += read;

            if(read != oactx.getBlockSize() || length-actuallyRead  == 0) {
                if(length-actuallyRead != 0) {
                    LOG.warning("Tried to read " + oactx.getBlockSize() +
                                " but read " + read + " last read, " +
                                actuallyRead + " total.");
                }
                buf.position((int)(buf.position()-pad));
                buf.limit((int)(buf.limit()-pad));
                return actuallyRead-pad;
            }

            start += oactx.getBlockSize();
        }

        // Time for a smaller read, maybe from a normal block, maybe
        // from the small block
        long read;
        try {
            read = Fragmenter.getInstance().readAndDefragment
                (buf,
                 start,
                 length-actuallyRead,
                 fset,
                 (objectSize - (start+prevChunkBytes)) < oactx.getBlockSize(),
                 oactx.getBlockSize(),
                 oactx);
        } catch (OAException oae) {
            throw new ArchiveException(oae);
        }

        actuallyRead += read;

        if(length-actuallyRead != 0) {
            LOG.warning("last read was short " + (length-actuallyRead) +
                        " bytes (w/ pad)");
        }

        buf.position((int)(buf.position()-pad));
        buf.limit((int)(buf.limit()-pad));
        return actuallyRead-pad;
    }

    /**
     * Opens an object, reads in OA headers.  If keepOpen, then leaves
     * the object open and populates ctx with state for reads, else
     * closes object.  Throws an exception if oid cannot be found, or
     * if unable to read full header.
     */
    public SystemMetadata readMetadata(NewObjectIdentifier oid,
                                       Context ctx) throws ArchiveException {
        // Get what we need out of ctx
        OAContext oactx =
            (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

        // The caller must have already called open to populate context
        if(oactx == null) {
            throw new IllegalArgumentException("Context is missing OA state");
        }

        FragmentFileSet fset = oactx.getFragmentFileSet();
        SystemMetadata sm = null;

        try {
            sm = fset.getSystemMetadata();
        } catch (OAException e) {
            throw new ArchiveException("Failed to read system metadata from " +
                                       " the fragments", e);
        }

        sm.setSize(oactx.getObjectSize());
        sm.setContentHash(oactx.getContentHash());

        return sm;
    }


    // MODIFY METHODS //

    /**
     * Deletes all chunks of the data or metadata object identified by oid.
     * Performs either a soft or a hard delete, optionally also wiping
     * the data using the DOD erasure algorithm.
     * TODO - what to do w/ hard and wipe - those were passed on create!
     * TODO - design says to retry delete if < 2P+1 now we just throw
     */
    public void delete(NewObjectIdentifier initialoid, boolean hard,
                       int wipe) throws ArchiveException {
        NewObjectIdentifier oid = new NewObjectIdentifier(initialoid.
                                                          toString());

        boolean moreChunks = true;
        int chunkIndex = 0;
        long numChunks = 0;
        long objectSize = -1;

        // Try to delete enough fragments to register the object as deleted
        // If I can't get to the data then I give up on this delete
        // right here and right now, this helps us on restore activity
        // where the data object might not exist for a while on the
        // system and we don't want to messup the refCounts by not
        // updating on the data object.
        if (oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE) {
            Context ctx = new Context();

            try {
                SystemMetadata sm =  open(initialoid,ctx);
                ctx.dispose();
                ctx = new Context();
                // if this open succeds then the object is there and
                // we can delete
                open(sm.getLink(),ctx);
            } finally {
              if (ctx != null)
                  ctx.dispose();
            }

        }

        while(moreChunks) {

            numChunks = calcNumChunks(deleteChunk(oid, hard, wipe));

            if(++chunkIndex == numChunks) {
                moreChunks = false;
            } else {
                setChunkAndLayout(oid, chunkIndex);
            }

        }
    }

    /** Do a delete of a single chunk */
    public long deleteChunk(NewObjectIdentifier oid, boolean hard, int wipe)
        throws ArchiveException {

        long objectSize = -1;

        // Build a disk array from the layout (should this be a sperate fn?)
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        // Create the class which encapsulates and manages frag. files
        FragmentFileSet fset;
        try {
            fset = new FragmentFileSet(oid,
                                       layout,
                                       reliability,
                                       NOT_RECOVERY);
        } catch (OAException oae) {
            throw new ArchiveException("Failed to create fragment set " +
                                       "object for oid [" + oid + "]", oae);
        }

        // Try to delete enough fragments to register the object as deleted
        int deleted = fset.delete(System.currentTimeMillis());
        if(deleted < (2*reliability.getRedundantFragCount())+1) {
            throw new ArchiveException("Failed to delete - only deleted " +
                                       deleted + " fragments sucessfully for " + oid);
        }

        try {
            objectSize = getObjectSize(fset);
        } catch (OAException oae) {
            throw new ArchiveException("Failed to get object size at end.");
        }

        return objectSize;
    }

    /**
     *  Check to see whether an object is deletable by checking for a
     *  past retention time and no legal hold tags.
     */
    public boolean isComplianceDeletable(NewObjectIdentifier oid)
        throws ArchiveException {

        // Read the retention time from the footer
        long retentionTime = getRetentionTime(oid);

        // Cannot delete if the time is unspecified
        if (retentionTime == RETENTION_UNSPECIFIED) {
            return false;
        }

        // Get the current time
        long currentDate = System.currentTimeMillis();

        // Cannot delete if the time is in the future
        if (retentionTime > currentDate) {
            return false;
        }

        // Cannot delete if there are legal holds present.
        if (getNumLegalHolds(oid) > 0) {
            return false;
        }

        // Return true if the retention time is unset or is in the
        // past, or there are no legal holds.
        return true;
    }

    /**
     *  Count the number of legal holds by retrieving all legal holds
     *  and then counting them up.
     */
    public int getNumLegalHolds(NewObjectIdentifier oid)
        throws ArchiveException {
        String[] holds = getLegalHolds(oid);
        if (holds == null) {
            return 0;
        }
        return holds.length;
    }

    /**
     * Get all legal hold strings
     */
    public String[] getLegalHolds(NewObjectIdentifier oid)
        throws ArchiveException {
        ArrayList data = null;
        String[] holds = null;

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        FooterExtensionFileSet fefSet = new FooterExtensionFileSet(oid,
                                            layout, reliability);

        // Get all objects of type legal hold
        try {
            data = fefSet.getType(fefSet.LEGAL_HOLD);
        } catch (Exception e) {
            throw new ArchiveException(e);
        }

        // Extract the strings
        if (data != null) {
            try {
                holds = new String[data.size()];
                for (int i=0; i<data.size(); i++) {
                    holds[i] = new String((byte[])data.get(i), "UTF8");
                }
            } catch (Exception e) {
                throw new ArchiveException(e);
            }
        }

        if (LOG.isLoggable(Level.FINE)) {
            if (holds != null) {
                for (int i=0; i<holds.length; i++) {
                    LOG.info("LEGAL HOLD: " + holds[i]);
                }
            } else {
                LOG.info("No legal holds found for OID " + oid);
            }
        }

        return holds;
    }

    // Replace the current set of legal holds for the oid with the
    // given set. Use with caution! This will delete anything not in
    // the given set! This implementation could be optimized to work
    // on the tree object directly.
    public void setLegalHolds(NewObjectIdentifier oid, String newHolds[])
        throws ArchiveException {

        // Check args
        if (oid == null || newHolds == null) {
            throw new ArchiveException("invalid arguments");
        }

        // Get the current holds
    String currentHolds[] = getLegalHolds(oid);

        if (currentHolds == null) {
            currentHolds = new String[]{};
        }

        // Iterate over and compare ArrayLists
        ArrayList addArray = new ArrayList();
        ArrayList removeArray = new ArrayList();
        ArrayList newArray = new ArrayList(Arrays.asList(newHolds));
        ArrayList currentArray = new ArrayList(Arrays.asList(currentHolds));

        // Find the legal holds to add. For each new legal hold, if
        // the existing set does not contain it, add it.
        for (int i=0; i<newArray.size(); i++) {
            if (!currentArray.contains(newArray.get(i))) {
                addArray.add(newArray.get(i));
            }
        }

        // Find the legal holds to remove. For each existing legal
        // hold, if the new set does not contain it, remove it.
        for (int i=0; i<currentArray.size(); i++) {
            if (!newArray.contains(currentArray.get(i))) {
                removeArray.add(currentArray.get(i));
            }
        }

        // Debug
        LOG.info("Current holds:  " + currentArray.toString());
        LOG.info("New holds:      " + newArray.toString());
        LOG.info("Removing holds: " + removeArray.toString());
        LOG.info("Adding holds:   " + addArray.toString());

        // Remove holds
        for (int i=0; i<removeArray.size(); i++) {
            removeLegalHold(oid, (String)removeArray.get(i));
        }

        // Add holds
        for (int i=0; i<addArray.size(); i++) {
            addLegalHold(oid, (String)addArray.get(i));
        }
    }

    // Context Consumer //

    /**
     *  Opens temp. files for an in-progress store, reads in enough to
     *  populate ctx with things like open file handles, content hash
     *  state, etc.  Importantly, restoreContextForStore is smart
     *  enough to be able to figure out which fragment files if any
     *  were marked bad previously.  Since Commit won't return success
     *  until enough contexts get serialized to disk, FillContext will
     *  have a way of reverting to the oldest context that was sync-ed
     *  enough places.  Note this means that we always save two
     *  contexts to fragment files.
     *  TODO: This is still called * fillStoreContext in the design doc.
     */
    public void restoreContextForStore(NewObjectIdentifier oid,
                                       boolean explicitClose,
                                       Context ctx)
        throws ArchiveException {

        // Find fileset for ctx and temp files, and read in ctx

        // TODO make this lookup as good as the one for open

        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            try {
                layout = layoutClient.getLayoutForStore(oid.getLayoutMapId());
            } catch (CapacityLimitException cle) {
                throw cle;
            }
        }

        // Build an fset for reading in context files
        // NOTE: _NOT_ the same as fset for object being restored
        FragmentFileSet fset;
        try {
            fset = new FragmentFileSet(oid, layout, reliability, NOT_RECOVERY);
        } catch (OAException oae) {
            throw new ArchiveException("Failed to create fragment set for " +
                                       "oid [" + oid + "]", oae);
        }

        ByteBuffer ctxarchive = ByteBufferPool.getInstance().
            checkOutBuffer(CTX_ARCHIVE_SIZE_BYTES);

        // Combine the archive and the buffered data list into one buffer array
        ByteBuffer bufferedData = null;

        try {
            bufferedData = fset.restoreContextForStore(ctxarchive,
                                                       explicitClose);
        } catch (OAException e) {
            throw new ArchiveException("Failed to restore store context", e);
        }

        ctxarchive.flip();
        bufferedData.flip();

        // De-serialize the context - TODO get whole context

        ByteBufferCoder decoder = new ByteBufferCoder(ctxarchive);

        decoder.decodeKnownClassCodable(ctx);

        ByteBufferPool.getInstance().checkInBuffer(ctxarchive);

        // Get what we need out of ctx
        OAContext oactx =
            (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

        // The caller must have already called create to populate context
        if(oactx == null) {
            throw new IllegalArgumentException("Context is missing OA state");
        }

        // Set buffered data
        oactx.getBufferedData().appendBuffer(bufferedData);
        ByteBufferPool.getInstance().checkInBuffer(bufferedData);
    }

    public void restoreContextForRetrieve(NewObjectIdentifier oid,
                                          Context ctx)
        throws ArchiveException {
        restoreContextForRetrieve(oid, UNKNOWN_SIZE, null, ctx);
    }

    public void acquireResourcesForStore(Context ctx)
        throws ArchiveException {
    }

    public void acquireResourcesForRetrieve(Context ctx)
        throws ArchiveException {
    }

    public int getWriteBufferSize() {
        return OA_WRITE_UNIT; // fragSize aligned
    }

    public int getReadBufferSize() {
        return OA_FRAGMENT_SIZE * reliability.getDataFragCount();
    }

    public int getReadBufferSize(Context ctx) {
        // Get what we need out of ctx
        OAContext oactx = (OAContext)ctx.getPersistentObject(OAContext.CTXTAG);
        if(oactx == null) {
            throw new IllegalArgumentException("Context is missing OA state");
        }
        return oactx.getFragmentSize() * reliability.getDataFragCount();
    }

    public int getLastReadBufferSize(int length) {
        int off = length % reliability.getDataFragCount();
        if(off > 0) {
            length += (reliability.getDataFragCount() - off);
        }
        return length;
    }

    public SystemMetadata compressChunkSM(SystemMetadata[] sms) {
        SystemMetadata sm = new SystemMetadata(sms[0]);
        sm.setSize(sms[sms.length-1].getSize());
        sm.setContentHash(sms[sms.length-1].getContentHash());
        sm.setHashAlgorithm(sms[sms.length-1].getHashAlgorithm());
        return sm;
    }

    /**
     * Method to set a set of layout disks used for testing only.
     *
     * @param testLayout the array of disks to use
     */
    public void setTestLayout(Layout testLayout) {
        if (isTestInstance) {
            LOG.info("Setting the test layout");
            this.testLayout = testLayout;
        }
    }

    /** Used by the OA Server to recover a specific fragment to a
        specific node

        TODO: use the sm we get back from open() and stop taking sm
        arg, and therefore stop doing queryPlus in OA Server

        TODO: How often should we commit during write?

        TODO: Need a way to get metadata field - put in system md?
    */
    public void recover(NewObjectIdentifier oid,
                        int fragNum,
                        Disk disk)  throws
                            OAException, IncompleteObjectException{

        if (disk == null) {
            LOG.warning("recovering " + oid + " frag " + fragNum +
                        " to null disk!");
        } else {
            LOG.info("recovering " + oid + "frag " + fragNum + " to disk " + disk.getPath());
        }

        Context storeCtx = new Context();
        Context retrieveCtx = new Context();

        try {

            // Open the object to read from
            SystemMetadata sm = null;
            try {
                sm = open(oid, retrieveCtx);
            } catch(DeletedObjectException doe) {
                // We are recovering a deleted stub
                recoverDeletedStub(oid, fragNum, disk);
                return;
            } catch(IncompleteObjectException ioe) {
                throw new IncompleteObjectException("Will not recover: " + ioe.getMessage());
            } catch(ArchiveException ae) {
                retrieveCtx.dispose();
                throw new OAException("Can't open object to read: " +
                                      ae, ae);
            }

            // Get fset (so we can get metadata field)
            OAContext retrieveoactx =
                (OAContext) retrieveCtx.getPersistentObject(OAContext.CTXTAG);

            // create must have already called create to populate context
            if(retrieveoactx == null) {
                retrieveCtx.dispose();
                throw new IllegalArgumentException("Context missing OA state");
            }

            FragmentFileSet retrievefset = retrieveoactx.getFragmentFileSet();


            // TODO: Name these functions better to show this distinction!
            long size = retrievefset.getObjectSize(); // frag header only
            long readLeft = retrieveoactx.getObjectSize(); // whole object

            /* If we are not on the last chunk, or we are last and we
             * are maxChunkSize, we will be reading in a whole
             * chunk */
            if(readLeft > 0) {
                if(size == MORE_CHUNKS || (readLeft % maxChunkSize == 0)) {
                    readLeft = maxChunkSize;
                } else {
                    /* The last chunk is always the remainder, so (whole object size) % (chunk size) */
                    readLeft = readLeft % maxChunkSize;

                    //size = readLeft; /* also, read will want to know how much we are reading */
                }
            }

            long createTime = retrievefset.getObjectCreateTime();

            long deleteTime = retrievefset.getObjectDeleteTime();

            boolean isMetadata = oid.getObjectType() == OBJECT_TYPE_METADATA;

            boolean isRefRoot =
                retrievefset.getSomeRefCount() == REFROOT_REFCOUNT;

            int maxMaxRefCount = REFROOT_REFCOUNT;
            if(!isRefRoot) {
                maxMaxRefCount = retrievefset.getMaxMaxRefCount();
            }

            // Create the new object to write to
            NewObjectIdentifier newoid = null;
            try {
                newoid = create(size,
                                sm.getLink(),
                                oid,
                                oid.getLayoutMapId(),
                                isMetadata,
                                maxMaxRefCount,
                                maxMaxRefCount,
                                Coordinator.EXPLICIT_CLOSE,
                                createTime,
                                deleteTime,
                                sm.getRTime(),
                                sm.getETime(),
                                sm.getShred(),
                                sm.getChecksumAlg(),
                                fragNum,
                                storeCtx,
                                null);
            } catch (ArchiveException e) {
                throw new OAException(e);
            }

            // Read in file and write it to the new one
            long readoffset = 0;

            while(readLeft > 0) {

                // Read in <= OA blockSize

                int readSize = retrieveoactx.getBlockSize();
                if(readLeft < retrieveoactx.getBlockSize()) {
                    readSize = getLastReadBufferSize((int)readLeft);
                }

                ByteBuffer buf =
                    ByteBufferPool.getInstance().checkOutBuffer(readSize);

                long read = 0;
                try {
                    read = read(buf, readoffset, readSize, true, retrieveCtx); // true == single chunk read
                } catch (ArchiveException e) {
                    throw new OAException("failed to read fragment: " + e, e);
                }

                // read() actually always returns block-alligned
                // answer b/c we always read full blocks (otherwise RS
                // couldn't work.  However, on the last block of an
                // object, some of the data we read in was pad.  We
                // could change the read() contract to answer the
                // number of _user_ bytes we read (in other words,
                // minus pad), but read() is just returing what
                // readAndDefragment is returning, so we'd have to
                // change what that returns too.  I'm not sure what
                // the effect of such a contract change would be
                // through the whole system, so here we just correct
                // for the last block case, as we know (from readLeft)
                // how much _user_ data we acually read.  It's
                // important to get this right b/c we use this value
                // to decide how much _user_ data to write out.
                // Before we did this, we were always writing out even
                // block multiples, which put the right data down (it
                // wrote out pad right) but set the size wrong.
                //
                // A simpler explination is this: coordinator
                // understands that OA reads and writes in blocks and
                // knows enough about the actual user data sizes to
                // clip appropriately.  Recovery has to be similarly
                // as smart.
                if(readLeft < retrieveoactx.getBlockSize()) {
                    read = readLeft;
                }

                buf.flip();

                readoffset += read;
                readLeft -= read;

                long writeLeft = read;

                // Write out full blocks if there are any...

                ByteBufferList list = new ByteBufferList();

                list.appendBuffer(buf);

                int writeoffset = 0;

                while(writeLeft >= retrieveoactx.getFragmentSize()) {
                    ByteBufferList writelist =
                        list.slice(writeoffset,
                                   retrieveoactx.getFragmentSize());

                    try {
                        append(writelist, storeCtx);
                    } catch (ArchiveException e) {
                        throw new OAException(e);
                    }

                    writelist.clear();

                    writeoffset += retrieveoactx.getFragmentSize();
                    writeLeft -= retrieveoactx.getFragmentSize();
                }

                // Then write out small block if there is one

                if(writeLeft > 0) {

                    ByteBufferList writelist = list.slice(writeoffset,
                                                          (int)writeLeft);

                    try {
                        append(writelist, storeCtx);
                    } catch (ArchiveException e) {
                        throw new OAException(e);
                    }

                    writelist.clear();
                }

                // Get buffer ready for the next read
                list.clear();

                ByteBufferPool.getInstance().checkInBuffer(buf);
            }

            // Clean up
            try {
                close(storeCtx,
                      retrievefset.getObjectMetadataField(),
                      retrievefset.getObjectContentHash(),
                      size == MORE_CHUNKS);
            } catch (ArchiveException e) {
                throw new OAException(e);
            }
        } finally {
            storeCtx.dispose();
            retrieveCtx.dispose();
        }
    }
    public void recoverDeletedStub(NewObjectIdentifier oid,
                                   int fragNum,
                                   Disk disk)  throws OAException {
        // NOTE: This doesn't do crawl like findAndOpenFset
        // Assumes everything we need is in place
        // That should be okay as recovery shouldn't be crawling

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.
                getLayoutForRetrieve(oid.getLayoutMapId());
        }

        // Make sure we use 'disk' to recover stub to Don't be
        // confused by the NOT_RECOVERY argment.  All it means is that
        // we are going to need to use all fragments.  RECOVERY is
        // used for the write part of a normal recovery.  In a normal
        // recovery, we read using NOT_RECOVERY since we need all
        // fragments.  Here we need _some_ peer fragment of the
        // deleted stub so we can get the footer fields.  Any will do,
        // but since we need >1 frags, we use the NOT_RECOVERY arg
        FragmentFileSet fset = new FragmentFileSet(oid,
                                                   layout,
                                                   reliability,
                                                   OAClient.NOT_RECOVERY);
        fset.recoverDeletedStub(fragNum, disk);
    }

    public byte[] getMetadataField(NewObjectIdentifier oid)
        throws ArchiveException{
        Context ctx = new Context();
        byte[] result = null;
        try {
            try {
                open(oid, ctx);
            } catch(DeletedObjectException doe) {
                LOG.info("get md from deleted object " + oid +
                         " - that's no problem!");
            }

            OAContext oactx =
                (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

            // If the context already has oa state, throw an exception
            if(oactx == null) {
                // XXX this msg must change to not include internal oids
                // to user
                throw new DeletedObjectException("No oa ctx for " + oid + ".");
            }

            FragmentFileSet fset = oactx.getFragmentFileSet();
            if(fset == null) {
                throw new IllegalArgumentException("no fragment file set.");
            }

            try {
                result = fset.getObjectMetadataField();
            } catch (OAException e) {
                throw new ArchiveException("Failed to get metadata field " +
                                           "from the fragments", e);
            }

        } finally {
            ctx.dispose();
        }

        return result;
    }

    /**
     * Method to return the default reliability that the OA client uses. This
     * can return a "null" value if it cannot read the configuration.
     *
     * @return ObjectReliability the reliability that OA client uses
     */
    public ObjectReliability getReliability() {
        if (reliability == null) {
            try {
                getConfig(isTestInstance);
            } catch (OAException e) {
                LOG.warning("Cannot get the configuration");
            }
        }
        return reliability;
    }

    //
    // PRIVATE METHODS
    //

    /** create helper function that knows how to initialize refCount fields
     * NOT USED FOR RECOVERY*/
    public  NewObjectIdentifier create(long size,
                                       NewObjectIdentifier link,
                                       NewObjectIdentifier inoid,
                                       int layoutMapId,
                                       boolean isMetadata,
                                       boolean isRefRoot,
                                       long autoCloseMillis,
                                       long createTime,
                                       long deleteTime,
                                       long retentionTime,
                                       long expirationTime,
                                       byte shred,
                                       short checksumAlgorithm,
                                       int recoverFrag,
                                       Context ctx,
                                       ContentHashContext hashContext)
        throws ArchiveException {

        if(recoverFrag != NOT_RECOVERY) {
            // should never happen - recover path never calls this verison
            throw new ArchiveException("recovery should not call this");
        }

        int refCount = 1;
        int maxRefCount = 1;
        if(isRefRoot) {
            refCount = maxRefCount = REFROOT_REFCOUNT;
        }

        return(create(size, link, inoid, layoutMapId, isMetadata,
                      refCount, maxRefCount, autoCloseMillis, createTime,
                      deleteTime, retentionTime, expirationTime, shred,
                      checksumAlgorithm, recoverFrag, ctx, hashContext));
    }

    /**
     * This begins the store procedure by creating a temporary object
     * on disk that can be stored to.
     */
    private NewObjectIdentifier create(long size,
                                       NewObjectIdentifier link,
                                       NewObjectIdentifier inoid,
                                       int layoutMapId,
                                       boolean isMetadata,
                                       int refCount,
                                       int maxRefCount,
                                       long autoCloseMillis,
                                       long createTime,
                                       long deleteTime,
                                       long retentionTime,
                                       long expirationTime,
                                       byte shred,
                                       short checksumAlgorithm,
                                       int recoverFrag,
                                       Context ctx,
                                       ContentHashContext hashContext)
        throws ArchiveException {

        // Check arguments
        if(size < -2) {
            throw new IllegalArgumentException("size < -2 not allowed: was "
                                               + size);
        }
        if(link == null) {
            throw new IllegalArgumentException("link may not be null");
        }
        if(layoutMapId < -1) {
            throw new IllegalArgumentException("layoutMapId is " +
                                               layoutMapId + " must be > -1");
        }
        if(autoCloseMillis < -1) {
            throw new IllegalArgumentException("autoCloseMillis " +
                                               autoCloseMillis +
                                               " must be > -1");
        }
        if(ctx == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        //
        // TODO: Check times, shred
        //

        // If layout is not passed in, ask Layout to pick one
        if(layoutMapId == PICK_LAYOUT) {
            layoutMapId = layoutClient.getLayoutMapId();
        }

        // The caller tells us the objectType
        byte objectType = OBJECT_TYPE_DATA;
        if(isMetadata) {
            objectType = OBJECT_TYPE_METADATA;
        }

        NewObjectIdentifier oid = inoid;

        // Generate a chunk 0 ObjectIdentifier for this store, if not
        // passed in
        if(oid == null || oid == NewObjectIdentifier.NULL) {
            if (objectType == OBJECT_TYPE_DATA) {
                oid = new NewObjectIdentifier(layoutMapId, objectType, 0);
            } else {
                oid = new NewObjectIdentifier(layoutMapId, objectType, 0, link);
            }
        }

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            if(recoverFrag == NOT_RECOVERY) {
                try {
                    layout = layoutClient.getLayoutForStore(layoutMapId);
                } catch (CapacityLimitException cle) {
                    throw cle;
                }
            } else {
                // cannot hit the capacity limit if we're recovering
                layout = layoutClient.getLayoutForRecover(layoutMapId);
            }
        }

        // Create the class which encapsulates and manages frag. files
        FragmentFileSet fset;
        try {
            fset = new FragmentFileSet(oid, layout, reliability, recoverFrag);
        } catch (OAException oae) {
            throw new ArchiveException("Failed to create fragment file " +
                                       "set for oid [" + oid + "]", oae);
        }

        if(createTime == CTIME_NOW) {
            createTime = System.currentTimeMillis();
        }

        /* XXX Until we actually use autoClose time, let's force it to -1 */
        autoCloseMillis = -1;

        // Open fragment files and count errors
        try {
            fset.create(link,
                        size,
                        createTime,
                        retentionTime,
                        expirationTime,
                        autoCloseMillis,
                        deleteTime,
                        shred,
                        checksumAlgorithm,
                        OA_FRAGMENT_SIZE,
                        (int)(maxChunkSize/blockSize),
                        refCount,
                        maxRefCount);
        } catch (OAException e) {
            throw new ArchiveException("Failed to create the fragments for" +
                                       "oid [" + oid + "]", e);
        }

        // Make new oa ctx and populate it
        OAContext oactx = new OAContext();
        oactx.setFragmentFileSet(fset);
        oactx.setFragmentSize(OA_FRAGMENT_SIZE);
        oactx.setBlockSize(blockSize);
        oactx.setChunkSize(maxChunkSize);

        // Create a new hash context if it is null
        if (hashContext == null) {
            // Make new hash context and put it in OA context
            hashContext =
                ContentHashAlgorithm.createContext(ContentHashAlgorithm.SHA1);
        }
        oactx.setContentHashContext(hashContext);

        // Popluate coordinator ctx with oa ctx
        ctx.removePersistentObject(OAContext.CTXTAG);
        ctx.registerPersistentObject(OAContext.CTXTAG, (Codable) oactx);

        /*
         * The following commit has been commented out part of the
         * optimization work for charter.
         *
         * The idea is that since no data have been written by the user at
         * that time, it is not worth spending time to commit the OA
         * headers.
         *
         * If needed, the user can always commit itself.
         */

        // Do an initial commit - no data sync necessary
        //         try {
        //             commit(false, ctx);
        //         } catch (OAException oae) {
        //             throw new ArchiveException("Failed in initial commit for" +
        //                                        "oid [" + oid + "]", oae);
        //         }

        return(oid);
    }

    private void append(Object buf, boolean list, Context ctx)
        throws ArchiveException {
        if(buf == null) {
            throw new IllegalArgumentException("buf must not be null");
        }

        if(ctx == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        ByteBufferList newList = null;
        ByteBuffer newBuf = null;

        if(list) {
            newList = (ByteBufferList) buf;
        } else {
            newBuf = (ByteBuffer) buf;
        }

        long newBytes = -1;

        if(list) {
            newBytes = newList.remaining();
        } else {
            newBytes = newBuf.remaining();
        }

        // Return if there is nothing to write
        if(newBytes == 0) {
            return;
        }

        if(newBytes > OA_WRITE_UNIT) {
            throw new IllegalArgumentException("Write may not be over " +
                                               OA_WRITE_UNIT + " bytes, got:" +
                                               newBytes);
        }

        // Get what we need out of ctx
        OAContext oactx =
            (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

        // The caller must have already called create to populate context
        if(oactx == null) {
            throw new IllegalArgumentException("Context is missing OA state");
        }

        FragmentFileSet fset = oactx.getFragmentFileSet();

        if(fset == null) {
            throw new IllegalArgumentException("Failed to get fset");
        }

        long n = fset.getReliability().getDataFragCount();

        // See if this is the start of a new chunk
        int chunk = fset.getOID().getChunkNumber();

        if(oactx.getActualDataBytesWritten() == oactx.getChunkSize()) {
            /*
              LOG.info("Object append [" + oactx.getActualDataBytesWritten() +
              "] is crossing the maximum chunk size [" +
              oactx.getChunkSize() + "]");
            */
            // Close the previous chunk and open a new one
            // No MD field in any but the last chunk

            SystemMetadata[] sms =
                closeFragments(ctx, new byte[FragmentFooter.METADATA_FIELD_LENGTH],
                           null,true);

            // Get what we need out of ctx
            OAContext oactx_chunk =
                (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);
            FragmentFileSet fset_chunk = oactx_chunk.getFragmentFileSet();

            createNextChunk(sms[sms.length-1], ctx);

            renameFragments(fset_chunk, sms, new byte[FragmentFooter.METADATA_FIELD_LENGTH], true);

            // Get what we need out of ctx
            oactx = (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

            // The caller must have already called create to populate context
            if(oactx == null) {
                throw new IllegalArgumentException("Missing new ctx chunk");
            }

            fset = oactx.getFragmentFileSet();
        }

        // Get ready to work with any buffered data
        ByteBufferList bufList = oactx.getBufferedData();
        long bufSizeBytes = bufList.remaining();
        long totalBytes = bufSizeBytes + newBytes;

        // See if this write is short or not
        if(newBytes != OA_WRITE_UNIT) {
            // This write was short - it better be the last
            if(oactx.noMoreWrites()) {
                LOG.warning("Only one short write allowed per object");
                // That is illegal because the last one was short too
                throw new
                    ArchiveException("Only 1 short write allowed per object");
            } else {
                // It's the first write like that, so this the object's end

                // A short write can never make the cache large enough
                // so it is safe to append this to the buffered data
                // w/o fear of overflow
                if(list) {
                    bufList.appendBufferList(newList);
                } else {
                    bufList.appendBuffer(newBuf);
                }

                // pad buffered data to an  N-byte boundry, if necessary
                long misalignment = totalBytes % n; // how far over?

                long pad = 0;
                if(misalignment != 0) {
                    pad = n-misalignment;
                    bufList.pad((int)pad, (byte)0);
                }

                long writingSize = bufList.remaining();

                // and write N-aligned buffer list
                try {
                    Fragmenter.getInstance().fragmentAndAppend
                        (bufList,
                         fset,
                         oactx.getBlockSize(),
                         pad,
                         oactx);
                } catch (OAException oae) {
                    LOG.warning("Error fragmenting data: " + oae);
                    throw new
                        ArchiveException("Error fragmenting data", oae);
                }

                oactx.incActualDataBytesWritten(writingSize - pad);

                // Clear the buffer so close doesn't try to write this
                // data again
                oactx.clearBufferedData();

                // Make sure we get no more small writes
                oactx.AllowNoMoreWrites();
            }
        } else {
            // Write is exactly fragment size

            // Append the new write to the cached data buffer
            if(list) {
                bufList.appendBufferList(newList);
            } else {
                bufList.appendBuffer(newBuf);
            }

            // If adding this still makes the buffered data <
            // blocksize, just buffer and return
            if(totalBytes < oactx.getBlockSize()) {
                return;
            }

            long writingSize = bufList.remaining();

            // Otherwise, we have buffered a block - let's write it
            try {
                Fragmenter.getInstance().fragmentAndAppend
                    (bufList,
                     fset,
                     oactx.getBlockSize(),
                     0, // 0 pad
                     oactx);
            } catch (OAException oae) {
                LOG.warning("Error fragmenting data: " + oae);
                throw new ArchiveException("Error fragmenting data", oae);
            }

            oactx.incActualDataBytesWritten(writingSize);

            // Clear the buffer to accept the beginning of the next block
            oactx.clearBufferedData();
        }
    }

    public void createMultiChunkStubs(Context ctx, long size)
           throws ArchiveException {
        OAContext oactx = (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);
        long chunk_size = oactx.getChunkSize();
        long offset = 0;

        // don't do anything
        if (size < chunk_size) {
            return;
        }

        int bytesPerChecksum = 102400;

        while (offset < size) {
            boolean isLast = ((offset+chunk_size) >= size) ? true : false;

            SystemMetadata sm = (SystemMetadata)ctx
                .getTransientObject(OAObjectSerializer.SYSTEM_METADATA_KEY);

            if (isLast) {
                // The last chunk is closed outside of this function, so 
                // just set the metadata we need set, for the last chunk
                // and return
                sm.setSize (size);
                sm.setNumPreceedingChksums ((short)1);
                return;
            } 

            // Create the next chunk!
            sm.setNumPreceedingChksums ((short)(size/bytesPerChecksum));
            SystemMetadata[] sms =
                closeFragments(ctx,
                               new byte[FragmentFooter.METADATA_FIELD_LENGTH],
                               null,
                               true);
                // Get what we need out of ctx
            OAContext oactx_chunk = (OAContext)
                        ctx.getPersistentObject(OAContext.CTXTAG);
            FragmentFileSet fset_chunk = oactx_chunk.getFragmentFileSet();
            createNextChunk(sms[sms.length-1], ctx);
            renameFragments(fset_chunk, 
                            sms, 
                            new byte[FragmentFooter.METADATA_FIELD_LENGTH], 
                            true);

            offset += chunk_size;
        }
    }

    /** Used by close to write remaining buffered data to fragment
     * files before footer gets written */
    private void appendBufferedData(Context ctx) throws OAException {
        // Check arguments
        if(ctx == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        // Get what we need out of ctx
        OAContext oactx =
            (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

        // The caller must have already called create to populate context
        if(oactx == null) {
            throw new IllegalArgumentException("Context is missing OA state");
        }
        FragmentFileSet fset = oactx.getFragmentFileSet();
        ByteBufferList bufList = oactx.getBufferedData();
        long n = fset.getReliability().getDataFragCount();

        long bufSizeBytes = bufList.remaining();

        // Do nothing if there is no buffered data
        if(bufSizeBytes == 0) {
            return;
        }

        // Do a short write

        // This write was short - it better be the last
        if(oactx.noMoreWrites()) {
            LOG.warning("Only one short write allowed per object");
            // That is illegal because the last one was short too
            throw new
                OAException("Only 1 short write allowed per object");
        } else {

            // It's the first write like that, so this the object's end

            // pad buffered data to an  N-byte boundry, if necessary
            long misalignment = bufSizeBytes % n; // how far over?

            long pad = 0;
            if(misalignment != 0) {
                pad = n-misalignment;
                bufList.pad((int)pad, (byte)0);
            }

            long writingSize = bufList.remaining();

            // and write N-aligned buffer list
            Fragmenter.getInstance().fragmentAndAppend(bufList,
                                                       fset,
                                                       oactx.getBlockSize(),
                                                       pad,
                                                       oactx);

            oactx.incActualDataBytesWritten(writingSize - pad);

            // Make sure we get no more small writes
            oactx.AllowNoMoreWrites();
        }
    }

    /**
     * Opens fragment files and builds up oa context.  Must be called
     * prior to calling cread()
     */
    private SystemMetadata open(NewObjectIdentifier oid,
                                long objectSize,
                                byte[] contentHash,
                                Context ctx) throws ArchiveException {
        OAContext oactx = (OAContext)ctx.getPersistentObject(OAContext.CTXTAG);

        // If the context already has oa state, throw an exception
        if(oactx != null) {
            throw new IllegalArgumentException("context already has OA state");
        }

        restoreContextForRetrieve(oid, objectSize, contentHash, ctx);

        oactx = (OAContext)ctx.getPersistentObject(OAContext.CTXTAG);

        FragmentFileSet fset =  oactx.getFragmentFileSet();
        SystemMetadata sm;
        try {
            sm =  fset.getSystemMetadata();
        } catch (OAException oae) {
            throw new ArchiveException("Failed to get system metadata from " +
                                       "the fragments for oid [" + oid + "]",
                                       oae);
        }

        sm.setSize(oactx.getObjectSize());
        sm.setContentHash(oactx.getContentHash());

        String link = "";
        if(sm.getLink().compareTo(NewObjectIdentifier.NULL) != 0) {
            link = " and link " + sm.getLink();
        }

        LOG.info("OA opened " + oid +
                 " size " + sm.getSize() +
                 " layout " +  fset.getLayout() +
                 " in directory " +  Common.getStoreDir(fset.getOID()) +
                 " with hash " +
                 ByteArrays.toHexString(sm.getContentHash()) +
                 " link " + sm.getLink() +
                 " and ref fields " + fset.getRefFieldsString());

        return sm;
    }

    /**
     * Finishes the store and commits the object to permanent storage.
     *
     * @param ctx the context of the object
     * @param metadataField the metadata field to update the footer with
     * @return SystemMetadata the object's system metadata after close
     */
    private SystemMetadata[] close(Context ctx,
                                   byte[] metadataField,
                                   byte[] objectHash,
                                   boolean moreChunks)
        throws ArchiveException {

        SystemMetadata[] sms =  closeFragments(ctx, metadataField, objectHash, moreChunks);
        OAContext oactx_chunk =
            (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

        FragmentFileSet fset_chunk = oactx_chunk.getFragmentFileSet();
        renameFragments(fset_chunk,sms,objectHash,moreChunks);
        return sms;
    }

    private SystemMetadata[] closeFragments(Context ctx,
                                   byte[] metadataField,
                                   byte[] objectHash,
                                   boolean moreChunks)
        throws ArchiveException {
        if(ctx == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        // Get what we need out of ctx
        OAContext oactx =
            (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

        // The caller must have already called create to populate context
        if(oactx == null) {
            throw new IllegalArgumentException("Context is missing OA state");
        }

        FragmentFileSet fset = oactx.getFragmentFileSet();

        // append any buffered data before writing the footer
        try {
            appendBufferedData(ctx);
        } catch (OAException e) {
            throw new ArchiveException("Cannot append more data as the " +
                                       "previous write was short");
        }
        try {
            fset.updateFooterWithMetadataField(metadataField);
        } catch (OAException e) {
            throw new ArchiveException("Cannot update footer with metadata: "
                                       + e.getMessage());
        }

        // Update all the fragment's footers with the object's content
        // hash value.

        ContentHashContext hashContext = oactx.getContentHashContext();
        ContentHashAlgorithm hashAlgorithm =
            ContentHashAlgorithm.getInstance(hashContext);

        if(objectHash == null) {
            if (moreChunks) {
                objectHash =
                    new byte[hashAlgorithm.getContentHashLength(hashContext)];
                Arrays.fill(objectHash, (byte)0);
            } else {
                objectHash = hashAlgorithm.digest(hashContext);
            }
        }

        int chunk = fset.getOID().getChunkNumber();

        // Write footer to fragment files and close them (flushing data)
        try {
            fset.updateFooterWithObjectHash(objectHash);

            // When we're in a restore context, the file size has to come
            // from the stream, and not from OA, since OA cannot determine
            // the old size of the object
            SystemMetadata sm = (SystemMetadata)ctx
                .getTransientObject(OAObjectSerializer.SYSTEM_METADATA_KEY);
            // This is used by restore to be able to update all footers
            // on all chunks.
            if (sm != null) {
                fset.updateFooterWithSystemMetadata(sm, moreChunks);
                if (moreChunks) {
                    fset.writeFooterAndClose(MORE_CHUNKS);
                } else {
                    fset.writeFooterAndClose(sm.getSize());
                }
            } else if (moreChunks) {
                fset.writeFooterAndClose(MORE_CHUNKS);
            } else {
                fset.writeFooterAndClose((chunk * oactx.getChunkSize()) +
                                         oactx.getActualDataBytesWritten());
            }
        } catch (OAException e) {
            throw new ArchiveException("Failed in writing the fragment " +
                                       "footer and closing", e);
        }

        // Build system md for all chunks
        SystemMetadata[] sms = null;
        SystemMetadata lastsm = null;

        try {
            lastsm = fset.getSystemMetadata();
            lastsm.setHashAlgorithm(hashAlgorithm.getHashAlgorithmName());
        } catch (OAException e) {
            throw new ArchiveException("Failed to get system metadata from " +
                                       " the fragments", e);
        }

        if(moreChunks) {
            // Internally, only need latest sm
            sms = new SystemMetadata[1];
            sms[0] = lastsm;
        } else {
            sms = new SystemMetadata[chunk+1];
            sms[chunk] = lastsm;
            for(int c=chunk-1; c>=0; c--) {
                // TODO - make the other ones
                // start with the last one
                sms[c] = new SystemMetadata(sms[c+1]);

                // Update chunk-specific fields

                int layoutMapId =
                    layoutClient.getPreviousLayoutMapId(sms[c+1].
                                                        getLayoutMapId());

                byte objectType = sms[c+1].getOID().getObjectType();

                NewObjectIdentifier oid =
                    new NewObjectIdentifier(sms[c+1].getOID().getUID(),
                                            layoutMapId,
                                            objectType,
                                            c,
                                            sms[c+1].getOID().getRuleId(),
                                            sms[c+1].getOID().getSilolocation());

                sms[c].setOID(oid);
                sms[c].setLayoutMapId(layoutMapId);
                sms[c].setSize(MORE_CHUNKS);
            }
        }

        return sms;
    }

    public void renameFragments(FragmentFileSet fset,
                       SystemMetadata[] sms,
                       byte[] objectHash,
                       boolean moreChunks) throws ArchiveException {


        // If we made it this far, we have enough tmp files, so rename
        // from tmp to perm
        try {
            fset.completeCreate();
        } catch (OAException e) {
            throw new ArchiveException("Failed to complete the fragment " +
                                       "creation", e);
        }

        // Clean up any ctx and tmp files
        fset.deleteContextFiles();

        // Log what we did
        String action = "stored";
        if(fset.recovery()) {
            action = "recovered";
        }
        String moreChunksStr = "";
        if(moreChunks) {
            moreChunksStr = "(Not Last Chunk)";
        }
        String link = " and link null";
        if(sms[0].getLink().compareTo(NewObjectIdentifier.NULL) != 0) {
            link = " and link " + sms[0].getLink();
        }

        try {
            LOG.info("OA " + action + " "  + fset.getOID() +
                     " size " + fset.getObjectSize() + // multii don't know whole thing yet
                     " layout " + fset.getLayout() +
                     " in directory " + Common.getStoreDir(fset.getOID()) +
                     " with hash " + ByteArrays.toHexString(objectHash) +
                     link + moreChunksStr);
        } catch (OAException oae) {
            LOG.warning("Got exception while getting information from " +
                        "fragment for oid [" + fset.getOID() + "] " +
                        "Exception: " + oae);
        }
    }

    private void restoreContextForRetrieve(NewObjectIdentifier oid,
                                           long size,
                                           byte[] contentHash,
                                           Context ctx)
        throws ArchiveException {
        // Find fileset, open the files, read in footers
        FragmentFileSet fset;
        try {
            fset = findAndOpenFset(oid, reliability);
        } catch (OAException oae) {
            throw new ArchiveException("Error opening fragments for oid [" +
                                       oid + "]", oae);
        }

        // Make new oa ctx and populate it
        OAContext oactx = new OAContext();

        // First set fileset
        oactx.setFragmentFileSet(fset);

        // Set the fragment, block and chunk size
        try {
            int fragmentSize = fset.getFragmentSize();
            int tempBlockSize = fragmentSize * reliability.getDataFragCount();
            oactx.setFragmentSize(fragmentSize);
            oactx.setBlockSize(tempBlockSize);
            oactx.setChunkSize((long)fset.getChunkSize()*(long)tempBlockSize);

            // Set objectSize. If size was not given as a shortcut, get it,
            // maybe walking chunks until we find the right one.
            if(size == UNKNOWN_SIZE) {
                size = fset.getObjectSize();
                contentHash = fset.getObjectContentHash();

                // Check to see if this is a multi chunk file.
                if(size == MORE_CHUNKS) {
                    SystemMetadata sm = getLastSystemMetadata(oid);
                    size = sm.getSize();
                    contentHash = sm.getContentHash();
                }
            }
            oactx.setObjectSize(size);
            oactx.setContentHash(contentHash);
        } catch (OAException oae) {
            throw new ArchiveException("Error getting information from " +
                                       " fragments for oid [" + oid + "]",
                                       oae);
        }

        // Popluate coordinator ctx with oa ctx
        ctx.registerPersistentObject(OAContext.CTXTAG, (Codable) oactx);
    }

    protected OAClient(boolean isTest) {
        try {
            getConfig(isTest);
        } catch (OAException ie) {
            LOG.severe("OA Failed to get configuration");
        }

        isTestInstance = isTest;
        layoutClient = layoutClient.getInstance();
        prng = new Random(System.currentTimeMillis());
    }

    /**
     * Method to read in any config values if missing - right now M + N are
     * the only ones. This also takes a parameter to either fetch the
     * runtime configuratio or generate it for testing purposes.
     *
     * @param isTest indicates if this is a test setup. false if the runtime
     *               configuration needs to be fetched. true if it needs to be
     *               generated
     */
    private void getConfig(boolean isTest) throws OAException {
        int n=-1;
        int m=-1;
        if (!isTest) {
            ClusterProperties confs = ClusterProperties.getInstance();
            if(reliability == null) {
                // TODO - Either we just keep trying until we succed, or use
                // the new Charter way, though I don't yet know what that is
                try {
                    String nStr = confs.getProperty(N_CONFIG_PARAM);
                    if(nStr == null) {
                        throw new OAException("Missing N confi param");
                    }
                    String mStr = confs.getProperty(M_CONFIG_PARAM);
                    if(mStr == null) {
                        throw new OAException("Missing M confi param");
                    }
                    n = Integer.parseInt(nStr);
                    m = Integer.parseInt(mStr);
                } catch(NumberFormatException nfe) {
                    throw new OAException("reliability conf is not " +
                                          "number: " + nfe.toString());
                }
            }
            // Get the checksum algorithm
            String configValue =
                confs.getProperty(CHECKSUM_CONFIG_PARAM);
            if(configValue == null) {
                LOG.fine("Config does not have value for the checksum " +
                         "algorithm. Using default = " +
                         ChecksumAlgorithm.getName
                         (configuredChecksumAlgorithm));
            } else {
                try {
                    configuredChecksumAlgorithm =
                        ChecksumAlgorithm.getAlgorithm(configValue);
                    LOG.fine("Got checksum algorithm = " +
                             ChecksumAlgorithm.getName
                             (configuredChecksumAlgorithm));
                } catch (IllegalArgumentException e) {
                    LOG.warning("Unsupported algorithm specified in " +
                                "configuration. Using defualt = " +
                                ChecksumAlgorithm.getName
                                (configuredChecksumAlgorithm));
                }
            }
        } else {
            n = TEST_N;
            m = TEST_M;
            LOG.info("Generated n = " + n + " and m = " + m);
        }

        reliability = new ObjectReliability(n, m);
        blockSize = n*OA_FRAGMENT_SIZE;
        // just over 1 GB, or 1,073,872,896 bytes for n=6
        // makes a max fragment data section of 178,978,816 bytes
        maxChunkSize = OA_MAX_CHUNK_SIZE;
        LOG.info("Using reliability = " + reliability + " blockSize = " +
                 blockSize + " maxChunkSize = " + maxChunkSize +
                 " configuredChecksumAlgorithm = " +
                 ChecksumAlgorithm.getName(configuredChecksumAlgorithm));
    }


    /** This does the real work of commit.  See public method for
     * description */
    protected void commit(boolean flush, Context ctx) throws OAException {
        if(ctx == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        OAContext oactx =
            (OAContext) ctx.getPersistentObject(OAContext.CTXTAG);

        // The caller must have already called create to populate context
        if(oactx == null) {
            throw new IllegalArgumentException("Context is missing OA state");
        }

        // Get what we need out of ctx
        FragmentFileSet fset = oactx.getFragmentFileSet();

        // Archive everything in the ctx except the oactx buffered data list
        ByteBuffer ctxarchive = ByteBufferPool.getInstance().
            checkOutBuffer(CTX_ARCHIVE_SIZE_BYTES);

        ByteBufferCoder coder = new ByteBufferCoder(ctxarchive);

        coder.encodeKnownClassCodable(ctx);

        // Done filling the ctxarchive, so prep. it for later disk write
        ctxarchive.flip();

        // ctx is variable sized, so calculate and store size
        int archiveSize = ctxarchive.remaining();
        ByteBuffer archiveSizeBuffer =
            ByteBufferPool.getInstance().checkOutBuffer(4);
        archiveSizeBuffer.putInt(ctxarchive.remaining());
        archiveSizeBuffer.flip();

        // Combine the archive and the buffered data list into one buffer array
        ByteBufferList ctxBuffers = new ByteBufferList();

        ctxBuffers.appendBuffer(archiveSizeBuffer);

        ctxBuffers.appendBuffer(ctxarchive);

        ctxBuffers.appendBufferList(oactx.getBufferedData());

        ByteBufferPool.getInstance().checkInBuffer(ctxarchive);

        ByteBufferPool.getInstance().checkInBuffer(archiveSizeBuffer);

        // Now actually commit the fragment and ctx files
        fset.commit(ctxBuffers, flush);

        ctxBuffers.clear();
    }

    private NewObjectIdentifier createNextChunk(SystemMetadata sm,
                                                Context ctx)
        throws ArchiveException {
        // Get the OA context and extract the content hash context from it
        OAContext oactx =
            (OAContext) ctx.removePersistentObject(OAContext.CTXTAG);
        ContentHashContext hashContext = oactx.getContentHashContext();

        // Get next layout id
        int lid = layoutClient.getConsecutiveLayoutMapId(sm.getLayoutMapId());

        // Use the new OID to create the new chunk
        // TODO - Pass real object type - right now always data
        // Do do this, we need to add object type to SystemMetadata
        // or figure out how to pass it as another argument
        // TODO - each chunk has different ctime - is that okay?
        return create(sm.getSize(),
                      sm.getLink(),
                      new NewObjectIdentifier (sm.getOID().getUID(),
                                               lid,
                                               NewObjectIdentifier.DATA_TYPE,
                                               sm.getOID().getChunkNumber()+1,
                                               sm.getOID().getRuleId(),
                                               sm.getOID().getSilolocation()),
                      lid,
                      false,
                      false, // TODO: BUG data can be refRoot!
                      Coordinator.EXPLICIT_CLOSE,
                      sm.getCTime(),
                      NOT_DELETED,
                      sm.getRTime(),
                      sm.getETime(),
                      sm.getShred(),
                      sm.getChecksumAlg(),
                      NOT_RECOVERY,
                      ctx,
                      hashContext);
    }

    public SystemMetadata getLastSystemMetadata(NewObjectIdentifier oid)
        throws NoSuchObjectException, OAException {
        // special handle deleted and incomplete objects
        return getLastSystemMetadata(oid, false, true, false);
    }

    /**
     * Method to get the system metadata for the last chunk of a large
     * object.
     *
     * @param oid the object identifier of the first chunk
     * @param tolerateDeleted if we should ignore _some_ deleted fragments
     * @return SystemMetadata the system metadata of the last chunk
     */
    public SystemMetadata getLastSystemMetadata(NewObjectIdentifier oid,
                                                boolean ignoreDeleted,
                                                boolean knownMoreChunks,
                                                boolean ignoreIncomplete)
        throws NoSuchObjectException, OAException {
        SystemMetadata sm = null;
        // object size is set to "MORE_CHUNKS" for non-last chunks
        long objectSize = MORE_CHUNKS;

        if(!knownMoreChunks) {
            FragmentFileSet fset =
                new FragmentFileSet(oid,
                                    layoutClient.
                                    getLayoutForRetrieve(oid.getLayoutMapId()),
                                    reliability,
                                    NOT_RECOVERY);

            long creationTime = -1;

            fset.open(ignoreDeleted, ignoreIncomplete);

            if (creationTime == 0) {
                // If failed to open any fragments in the set, give up.
                fset.close();
                String err = "OID [" + oid + "] Failed to open chunk [" +
                    oid + "]";
                LOG.warning(err);
                throw new NoSuchObjectException(err);
            }

            sm = fset.getSystemMetadata();
            objectSize = sm.getSize();
            fset.close();
            if(objectSize != MORE_CHUNKS) {
                return sm;
            }
        }

        NewObjectIdentifier prevoid = oid;

        while(objectSize == MORE_CHUNKS) {
            // Get next layout id
            int lid = layoutClient.getConsecutiveLayoutMapId
                (prevoid.getLayoutMapId());

            // Construct next OID
            NewObjectIdentifier nextoid =
                new NewObjectIdentifier(prevoid.getUID(),
                                        lid,
                                        prevoid.getObjectType(),
                                        prevoid.getChunkNumber()+1,
                                        prevoid.getRuleId(),
                                        prevoid.getSilolocation());

            Layout layout = null;
            if ((isTestInstance) && (testLayout != null)) {
                layout = testLayout;
            } else {
                layout = layoutClient.getLayoutForRetrieve(lid);
            }

            FragmentFileSet fset =
                new FragmentFileSet(nextoid, layout, reliability, NOT_RECOVERY);

            long creationTime = fset.open(ignoreDeleted, ignoreIncomplete);

            if (creationTime == 0) {
                // If failed to open any fragments in the set, give up.
                fset.close();
                String err = "OID [" + oid + "] Failed to open chunk [" +
                    nextoid + "]";
                LOG.warning(err);
                throw new NoSuchObjectException(err);
            }

            sm = fset.getSystemMetadata();
            objectSize = sm.getSize();
            fset.close();
            prevoid = nextoid; // May have to walk to the next chunk
        }

        return sm;
    }



    /**
     * Returns the system record for the specified object, by system record this is
     * generated on the following rules:
     *
     * 1. each object in the system only has one system record and that record for a multi
     *    chunk object is based on the oid of the first chunk.
     *
     * 2. it must also have the layout correctly from the first chunk
     *
     * 3.
     */
    public SystemMetadata getSystemMetadata(NewObjectIdentifier oid,
                                            boolean ignoreDeleted,
                                            boolean ignoreIncomplete)
           throws NoSuchObjectException, OAException {

        SystemMetadata sm = null;
        long objectSize = MORE_CHUNKS;
        NewObjectIdentifier prevoid = oid;
        do {
            FragmentFileSet fset = findAndOpenFset (prevoid, 
                                                    reliability, 
                                                    ignoreDeleted, 
                                                    ignoreIncomplete);
            
            // Construct next OID
            NewObjectIdentifier nextoid = new NewObjectIdentifier(prevoid.getUID(),
                          LayoutClient.getConsecutiveLayoutMapId(prevoid.getLayoutMapId()),
                          prevoid.getObjectType(),
                          prevoid.getChunkNumber() + 1,
                          prevoid.getRuleId(),
                          prevoid.getSilolocation());

            sm = fset.getSystemMetadata();
            objectSize = sm.getSize();
            fset.close();
            prevoid = nextoid; // May have to walk to the next chunk
        } while (objectSize == MORE_CHUNKS);

        // set the oid to the object oid
        sm.setOID(oid);
        sm.setLayoutMapId(oid.getLayoutMapId());

        return sm;
    }

    public long calcNumChunks(long objectSize) {
        long numChunks = objectSize / maxChunkSize;
        if((objectSize % maxChunkSize != 0) || objectSize == 0) {
            numChunks++;
        }
        return numChunks;
    }

    
    /** Open fragment fileset, encapsulates layout retry algorithm.  */
    private FragmentFileSet findAndOpenFset(NewObjectIdentifier oid,
                                            ObjectReliability reliability)
        throws NoSuchObjectException, OAException {
        return findAndOpenFset(oid, reliability, false, true);
    }

    private FragmentFileSet findAndOpenFset(NewObjectIdentifier oid,
                                            ObjectReliability reliability,
                                            boolean ignoreDeleted, 
                                            boolean ignoreIncomplete)
        throws NoSuchObjectException, OAException {

        // if fails, catch our own exception and crawl for object
        Layout layout = null;

        try {
            // FIRST ATTEMPT
            // Build a disk array from the layout
            if ((isTestInstance) && (testLayout != null)) {
                layout = testLayout;
            } else {
                layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
            }

            FragmentFileSet fset = new FragmentFileSet(oid,
                                                       layout,
                                                       reliability,
                                                       OAClient.NOT_RECOVERY);

            // Open files, read footers, and cons up in-memory context
            long creationTime = fset.open(ignoreDeleted, ignoreIncomplete);
            if (creationTime == -1) {
                return fset;
            }
            
            throw new NoSuchObjectException("Too few fragments");
        } catch (DeletedObjectException ex) {
            throw ex;
        } catch (NoSuchObjectException ex) { 
            LOG.info ("failed to find oid " + oid + " in current layout: " 
                  + ex.getMessage());
        }
    
        if(!crawlAllLastResort) {
            String errorMsg="crawling disabled, giving up on oid " + oid;
            LOG.info (errorMsg);
            throw new NoSuchObjectException(errorMsg);
        }

        // FINAL ATTEMPT
        LOG.info("Crawling because oid "+oid+" not found in any layout");
        return crawlForAndOpenFset(oid, reliability);    
    }

    private FragmentFileSet crawlForAndOpenFset(NewObjectIdentifier oid,
                                                ObjectReliability reliability)
        throws NoSuchObjectException, OAException {

        LOG.info("Last chance lookup: crawling all disks for "+oid);

        Disk[] allDisks = DiskProxy.getClusterDisks();

        if(allDisks == null) {
            throw new
                NoSuchObjectException("null online disks array");
        }

        // Use special crawl - will throw exception if doesn't find enough
        FragmentFileSet fset = new FragmentFileSet(oid, allDisks, reliability);

        if(fset.open() != -1) {
            throw new NoSuchObjectException("Crawl failed");
        }

        return fset;
    }


    private long getObjectSize(FragmentFileSet fset) throws OAException,
                                                            NoSuchObjectException {
        // get size, maybe walking chunks until we find the right one
        long size = fset.getObjectSize();
        if(size == MORE_CHUNKS) {
            SystemMetadata sm = getLastSystemMetadata(fset.getOID());
            size = sm.getSize();
        }

        return size;
    }

    /** set oid's chunk to chunkIndex and incs/decs it's layout id by diff between current and new chunk id */
    private void setChunkAndLayout(NewObjectIdentifier oid, int chunkIndex) {
        int chunkDelta = chunkIndex - oid.getChunkNumber();
        oid.setChunkNumber(chunkIndex);
        if(chunkDelta < 0) {
            while(chunkDelta++ < 0) {
                oid.setLayoutMapId(layoutClient.getPreviousLayoutMapId(oid.getLayoutMapId()));
            }
        } else {
            while(chunkDelta-- > 0) {
                oid.setLayoutMapId(layoutClient.getConsecutiveLayoutMapId(oid.getLayoutMapId()));
            }
        }
    }

    // COMPLIANCE METHODS

     // Get the existing date from the footer
    public long getRetentionTime(NewObjectIdentifier oid)
        throws ArchiveException {
        long date = 0;

        // Build a disk array from the layout
        Layout layout = null;
        if ((isTestInstance) && (testLayout != null)) {
            layout = testLayout;
        } else {
            layout = layoutClient.getLayoutForRetrieve(oid.getLayoutMapId());
        }

        FragmentFileSet fset;
        try {
            fset = new FragmentFileSet(oid,
                                       layout,
                                       reliability,
                                       OAClient.NOT_RECOVERY);
        } catch (OAException oae) {
            throw new ArchiveException("Failed to create fragment set " +
                                       "object for oid [" + oid + "]", oae);
        }

        try {
            date = fset.getRetentionTime();
        } catch (OAException e) {
            throw new ArchiveException("Cannot get retention time: "
                                       + e.getMessage());
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Retrieved retention time " + date + " for " + oid);
        }

        return date;
    }

    // TEST METHODS -- should not be called except from tests //
    public void setMaxChunkSize(long size) {
        LOG.warning("WARNING: changing maxChunkSize size from " + maxChunkSize +
                    " to " + size + " ; only tests should do this!");
        maxChunkSize = size;
    }

    public long getMaxChunkSize() {
        return maxChunkSize;
    }

    // STATIC CONSTANTS //
    public static final int PICK_LAYOUT = -1;
    public static final int UNKNOWN_SIZE = -1;
    public static final int NOT_RECOVERY = -1;
    public static final int CTIME_NOW = -1;
    public static final byte LAYOUT_STYLE_SMALL = (byte)1;
    public static final byte LAYOUT_STYLE_LARGE = (byte)2;
    public static final byte OBJECT_TYPE_DATA = (byte)1;
    public static final byte OBJECT_TYPE_METADATA = (byte)2;
    public static final int REFROOT_REFCOUNT = -1;
    public static final int ALWAYS_RETAIN = -1; // keep forever
    public static final int NEVER_EXPIRE = -1; // keep forever
    public static final int NOT_DELETED = -1; // object not yet deleted
    public static final byte DO_NOT_SHRED = (byte)0; // of deleted, don't shred
    public static final int CHOOSE_LAYOUT = -1;
    //private static final int CTX_ARCHIVE_SIZE_BYTES = 131072; // TODO: ???
    private static final int CTX_ARCHIVE_SIZE_BYTES = 300072;
    public static final int MORE_CHUNKS = -2;

    // CONFIG FIELD NAMES //
    private static final String N_CONFIG_PARAM = "honeycomb.layout.datafrags";
    private static final String M_CONFIG_PARAM =
        "honeycomb.layout.parityfrags";
    private static final String CHECKSUM_CONFIG_PARAM =
        "honeycomb.oa.client.checksumalgorithm";

    // RUN-TIME CONFIGURABLE PARAMS //
    private ObjectReliability reliability = null;
    public int blockSize = -1; // A function of N, which is runtime config
    private long maxChunkSize = -1; // A function of N, which is runtime config
    private short configuredChecksumAlgorithm = ChecksumAlgorithm.ADLER32;
    //private short configuredChecksumAlgorithm = ChecksumAlgorithm.NONE;

    // THESE ARE STATIC BUT NEED TO BECOME CONFIGURABLE
    public static final int OA_FRAGMENT_SIZE = 64 * 1024;
    public static final int OA_WRITE_UNIT = OA_FRAGMENT_SIZE;
    // 5 for 5+2, pick 3200 to get 1 GB max chunk
    public static final int OA_MAX_CHUNK_SIZE = OA_FRAGMENT_SIZE * 5 * 3200;

    // OTHER PRIVATE MEMBERS //
    protected static final Logger LOG =
        Logger.getLogger(OAClient.class.getName());

    /** Real OA client */
    private static OAClient oaclient = null;

    private static LayoutClient layoutClient = null;

    // COMPLIANCE MEMBERS
    private static final int RETENTION_UNSET = 0;
    private static final int RETENTION_UNSPECIFIED = -1;

    // Locking for inc ref count
    private Random prng = null;
    // How many times to re-try getting a lock on *all* fragments when
    // incrementing ref count
    private static final int NUM_LOCK_RETRIES = 6;
    // Max time (ms) to wait before re-trying to acquire the lock
    private static final int LOCK_RETRY_DELAY = 100; // ms

    //
    // TEST MEMBERS
    //

    public static final int TEST_N = 5;
    public static final int TEST_M = 3;

    /** Test OA client. This does not read configuration, etc */
    private static OAClient oaTestClient = null;

    /** The list of test layout disks. */
    private static Layout testLayout = null;

    /** Boolean for indicating if this is a test instance of the OA client */
    private static boolean isTestInstance = false;

    private static final boolean crawlAllLastResort = true;
}
