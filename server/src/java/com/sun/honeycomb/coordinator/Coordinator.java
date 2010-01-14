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



package com.sun.honeycomb.coordinator;

import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.protocol.server.ProtocolProxy;
import com.sun.honeycomb.protocol.server.ServiceRegistration.EventRegistrant;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;
import java.util.Locale;

public class Coordinator 
    implements WriteCoordinator, ReadCoordinator, EventRegistrant {

    public static final long UNKNOWN_SIZE = -1L;
    public static final long NO_CONTENT = 0L;
    public static final int EXPLICIT_CLOSE = -1;

    // 32 MB of data cache
    private static final int MAX_DATA_MEMORY = 32 * 1024 * 1024;
    // 2 MB of metadata cache
    private static final int MAX_METADATA_MEMORY = 2 * 1024 * 1024;

    // 5 seconds
    private static final long PURGE_PERIOD = 5 * 1000;

    // 5 seconds
    private static final long EJECT_READ_CONTEXT_TIMEOUT = 5 * 1000;

    // 2 hours - should be shorter?
    private static final long EJECT_WRITE_CONTEXT_TIMEOUT = 2 * 60 * 60 * 1000;

    // 30 seconds - should be shorter
    private static final long RELEASE_RESOURCES_TIMEOUT = 30 * 1000;

    private static final String COORDINATOR_KEY =
        Coordinator.class.getName();
    protected static final Logger LOGGER =
        Logger.getLogger(Coordinator.class.getName());

    private static BackingStore defaultBackingStore;
    private static Coordinator instance;

    private BackingStore backingStore;

    private Timer purgeContextsTimer;

    private Map writeContexts;
    private Map readDataContexts;
    private Map readMetadataContexts;

    private int writeBufferSize;
    private int readBufferSize;

    private BlockCache dataCache;
    private BlockCache metadataCache;
    private boolean cachingEnabled;

    static private ResourceBundle errorBundle = null;
    private static void init() {
        if (errorBundle != null)
            return;

	try {
	    errorBundle = ResourceBundle.getBundle("AdminResources",
                                                   Locale.getDefault());
	} catch (MissingResourceException ex){
	    LOGGER.log(Level.SEVERE, "cannot retrieve error bundle, exit..." + ex);
	    System.exit(1);
	}
    }

    public static Coordinator getInstance() {
        synchronized (Coordinator.class) {
            if (instance == null) {
                instance = new Coordinator();
            }
        }

        return instance;
    }

    public static void setDefaultBackingStore(BackingStore newStore) {
        defaultBackingStore = newStore;
    }

    public static BackingStore getDefaultBackingStore() {
        return defaultBackingStore;
    }

    protected Coordinator() {
        writeContexts = Collections.synchronizedMap(new HashMap());
        readDataContexts = Collections.synchronizedMap(new HashMap());
        readMetadataContexts = Collections.synchronizedMap(new HashMap());

        setCachingEnabled(true);

        setBackingStore((defaultBackingStore != null)
                        ? defaultBackingStore
                        : MetadataCoordinator.getInstance());

        startPurgingContexts();
    }

    public BackingStore getBackingStore() {
        return backingStore;
    }

    public void setBackingStore(BackingStore newStore) {
        if (backingStore != null) {
            throw new UnsupportedOperationException("can't replace " +
                                                    "existing backing store");
        }

        backingStore = newStore;

        writeBufferSize = backingStore.getWriteBufferSize();
        readBufferSize = backingStore.getReadBufferSize();

        if (writeBufferSize <= 0 || readBufferSize <= 0) {
            throw new IllegalArgumentException("buffer sizes must be positive");
        }

        if (cachingEnabled) {
            dataCache = new BlockCache(MAX_DATA_MEMORY / readBufferSize);
            metadataCache = new BlockCache(MAX_METADATA_MEMORY / readBufferSize);
        }
    }

    public void setCachingEnabled(boolean enabled) {
        cachingEnabled = enabled;
    }

    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    private void startPurgingContexts() {
        if (purgeContextsTimer == null) {
            purgeContextsTimer = new Timer();

            TimerTask task = new TimerTask() {
                public void run() {
                    purgeStaleContexts();
                }
            };

            purgeContextsTimer.scheduleAtFixedRate(task,
                                                   PURGE_PERIOD,
                                                   PURGE_PERIOD);
        }
    }

    private void stopPurgingContexts() {
        if (purgeContextsTimer != null) {
            purgeContextsTimer.cancel();
            purgeContextsTimer = null;
        }
    }

    private void purgeStaleContexts() {
        long start = 0;

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("purging contexts");
            start = System.currentTimeMillis();
        }

        purgeStaleContexts(writeContexts, false);
        purgeStaleContexts(readDataContexts, true);
        purgeStaleContexts(readMetadataContexts, true);

        if (LOGGER.isLoggable(Level.FINE)) {
            long end = System.currentTimeMillis();
            LOGGER.fine("done purging contexts - operation took " +
                        (end - start) +
                        " ms");
        }
    }

    private void purgeStaleContexts(Map contexts, boolean read) {
        if (contexts == null || contexts.size() == 0) {
            return;
        }

        // Create a list of contexts not backed by the map since
        // iterators don't support concurrent modification.
        ArrayList values = new ArrayList(contexts.entrySet());
        int size = values.size();
        long currentTime = System.currentTimeMillis();

        for (int i = 0; i < size; i++) {
            Map.Entry entry = (Map.Entry)values.get(i);
            if (entry == null) {
                continue;
            }

            String oid = (String)entry.getKey();
            if (read) {
                purgeReadContexts(contexts,
                                  oid, 
                                  (List)entry.getValue(),
                                  currentTime);
            } else {
                purgeWriteContext(contexts,
                                  oid,
                                  (Context)entry.getValue(),
                                  currentTime);
            }
        }
    }

    private void purgeReadContexts(Map contexts,
                                   String oid,
                                   List contextList,
                                   long currentTime) {

        if (contextList == null) {
            return;
        }

        List copyList = new ArrayList(contextList);
        int size = copyList.size();

        for (int i = 0; i < size; i++) {
            Context ctx = (Context)copyList.get(i);
            if (ctx == null) {
                continue;
            }

            long delta = currentTime - ctx.getAccessTime();

            if (delta >= EJECT_READ_CONTEXT_TIMEOUT) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("ejecting read context for oid " +
                                oid);
                }

                synchronized (contexts) {
                    if (contextList.remove(ctx)) {
                        ctx.dispose();
                    }
                }
            }
        }

        synchronized (contexts) {
            if (contextList.size() == 0) {
                contexts.remove(oid);
            }
        }
    }

    private void purgeWriteContext(Map contexts,
                                   String oid,
                                   Context ctx,
                                   long currentTime) {
        
        long delta = currentTime - ctx.getAccessTime();

        if (delta >= EJECT_WRITE_CONTEXT_TIMEOUT) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("ejecting write context for oid " +
                            oid);
            }

            if (contexts.remove(oid) != null) {
                ctx.dispose();
            }
        } else if (delta >= RELEASE_RESOURCES_TIMEOUT &&
                   ctx.hasResources()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("releasing resources for write " +
                            "context for oid " +
                            oid);
            }

            synchronized (contexts) {
                if (contexts.containsKey(oid)) {
                    ctx.releaseResources();
                }
            }
        }
    }

    private Context checkOutWriteContext(NewObjectIdentifier oid,
                                         boolean explicitClose)
        throws ArchiveException {

        Context result = (Context)writeContexts.remove(oid.toHexString());
        if (result != null) {
            if (!result.hasResources()) {
                acquireResourcesForStore(result);
            }

            result.updateAccessTime();
        } else {
            String str = errorBundle.getString("warn.coord.restore.wrcontext");

	    LOGGER.log(ExtLevel.EXT_WARNING, str);

            result = new Context();
            restoreContextForStore(oid, explicitClose, result);
        }

        return result;
    }

    private void checkInWriteContext(NewObjectIdentifier oid,
                                     Context ctx) {
        writeContexts.put(oid.toHexString(), ctx);
    }


    private void removeWriteContext(NewObjectIdentifier oid) {
	removeContext(writeContexts, oid);
    }
    
						 
    private Context checkOutReadContext(NewObjectIdentifier oid,
					long blockID,
                                        boolean isData)
        throws ArchiveException {

        Context result = null;
        Map contexts = (isData) ? readDataContexts : readMetadataContexts;

        synchronized (contexts) {
            List contextList = (List)contexts.get(oid.toHexString());

            if (contextList != null) {
                int size = contextList.size();

		// if there are >1, take the one that is closed to but
		// not greater than the blockID we are on now.  the fs
		// interface uses similar logic.  The reason is we
		// want the same user to get the one they used before
		// whenever possible.  If there are multiple w/ the
		// same block, they can be used interchangably (same
		// object, same position, read-only ops and attomic
		// ops are all we have, so who cares?).
                if (size > 0) {
		    long proximity = java.lang.Long.MAX_VALUE;
		    int closestNotHigher = -1;
		    Context candidate;
		    for(int c=0; c< size; c++) {
			candidate = (Context) contextList.get(c);
			if(candidate.getBlock() <= blockID) {
			    long newProximity = blockID - candidate.getBlock();
			    if(newProximity <= proximity) {
				closestNotHigher = c;
				proximity = newProximity;
			    }
			}
		    }
		    
		    // if the only contexts for this object are higher
		    // than we want, then don't return any (it's like
		    // there are no contexts for this object at all)
		    if(closestNotHigher == -1) {
			return null;
		    }
		    
                    result = (Context)contextList.remove(closestNotHigher);

                    if (!result.hasResources()) {
                        acquireResourcesForRetrieve(result);
                    }

                    result.updateAccessTime();
                }
            }
        }

        return result;
    }

    private void checkInReadContext(NewObjectIdentifier oid,
				    long blockID,
                                    boolean isData,
                                    Context ctx) {
        Map contexts = (isData) ? readDataContexts : readMetadataContexts;

	ctx.setBlock(blockID);

        synchronized (contexts) {
            List contextList = (List)contexts.get(oid.toHexString());

            if (contextList == null) {
                contextList = new ArrayList(4);
                contexts.put(oid.toHexString(), contextList);
            }

            contextList.add(ctx);
        }
    }
    
    /** Remove both data and metadata read contexts */
    public void removeReadContexts(NewObjectIdentifier oid) {
	removeReadContext(oid, true);
	removeReadContext(oid, false);
    }
    
    private void removeReadContext(NewObjectIdentifier oid,
                                   boolean isData) {
        Map contexts = (isData) ? readDataContexts : readMetadataContexts;
	removeContext(contexts, oid);
    }

    private void removeContext(Map contexts, NewObjectIdentifier oid) {
    
        synchronized (contexts) {
            List contextList = (List)contexts.get(oid.toHexString());

            if (contextList != null) {
                int size = contextList.size();

                if (size > 0) {
                    Context ctx = (Context)contextList.remove(size - 1);
                    ctx.dispose();

                    if (contextList.size() == 0) {
                        contexts.remove(oid.toHexString());
                    }
                }
            }
        }
    }


    private void restoreContextForStore(NewObjectIdentifier oid, 
                                       boolean explicitClose,
                                       Context ctx) 
        throws ArchiveException {

        backingStore.restoreContextForStore(oid, explicitClose, ctx);
    }

    private void restoreContextForRetrieve(NewObjectIdentifier oid, Context ctx)
        throws ArchiveException {
        backingStore.restoreContextForRetrieve(oid, ctx);
    }

    public void acquireResourcesForStore(Context ctx)
        throws ArchiveException {

        backingStore.acquireResourcesForStore(ctx);
        ctx.setHasResources(true);
    }

    public void acquireResourcesForRetrieve(Context ctx)
        throws ArchiveException {

        backingStore.acquireResourcesForRetrieve(ctx);
        ctx.setHasResources(true);
    }

    public NewObjectIdentifier createObject(long dataSize,
                                            long metadataSize,
                                            int autoCloseMillis,
                                            long retentionTime,
                                            long expirationTime,
                                            byte shred,
                                            String cacheID)
        throws ArchiveException {

        return create(null,
                      dataSize,
                      metadataSize,
                      null,
                      autoCloseMillis,
                      retentionTime,
                      expirationTime,
                      shred,
                      cacheID);
    }

    public NewObjectIdentifier createObject(long dataSize,
                                            CacheRecord metadataRecord,
                                            int autoCloseMillis,
                                            long retentionTime,
                                            long expirationTime,
                                            byte shred)
        throws ArchiveException {

        return create(null,
                      dataSize,
                      NO_CONTENT,
                      metadataRecord,
                      autoCloseMillis,
                      retentionTime,
                      expirationTime,
                      shred,
                      null);
    }

    public NewObjectIdentifier createMetadata(NewObjectIdentifier linkOID,
                                              long metadataSize,
                                              int autoCloseMillis,
                                              long retentionTime,
                                              long expirationTime,
                                              byte shred,
                                              String cacheID)
        throws ArchiveException {

        return create(linkOID,
                      NO_CONTENT,
                      metadataSize,
                      null,
                      autoCloseMillis,
                      retentionTime,
                      expirationTime,
                      shred,
                      cacheID);
    }

    public NewObjectIdentifier createMetadata(NewObjectIdentifier linkOID,
                                              CacheRecord metadataRecord,
                                              int autoCloseMillis,
                                              long retentionTime,
                                              long expirationTime,
                                              byte shred)
        throws ArchiveException {

        return create(linkOID,
                      NO_CONTENT,
                      NO_CONTENT,
                      metadataRecord,
                      autoCloseMillis,
                      retentionTime,
                      expirationTime,
                      shred,
                      null);
    }

    private NewObjectIdentifier create(NewObjectIdentifier linkOID,
                                       long dataSize,
                                       long metadataSize,
                                       CacheRecord metadataRecord,
                                       int autoCloseMillis,
                                       long retentionTime,
                                       long expirationTime,
                                       byte shred,
                                       String cacheID)
        throws ArchiveException {

        Context ctx = new Context();
        NewObjectIdentifier result;

        if (metadataRecord != null && metadataSize != NO_CONTENT) {
            throw new IllegalArgumentException("cannot specify metadata size" +
                                               " when a record is provided");
        }

        if (linkOID == null) {
            if (metadataRecord != null) {
                result = backingStore.createObject(dataSize,
                                                   metadataRecord,
                                                   autoCloseMillis,
                                                   retentionTime,
                                                   expirationTime,
                                                   shred,
                                                   ctx);
            } else {
                result = backingStore.createObject(dataSize,
                                                   metadataSize,
                                                   autoCloseMillis,
                                                   retentionTime,
                                                   expirationTime,
                                                   shred,
                                                   cacheID,
                                                   ctx);
            }
        } else if (metadataRecord != null) {
            result = backingStore.createMetadata(linkOID,
                                                 metadataRecord,
                                                 autoCloseMillis,
                                                 retentionTime,
                                                 expirationTime,
                                                 shred,
                                                 ctx);
        } else {
            result = backingStore.createMetadata(linkOID,
                                                 metadataSize,
                                                 autoCloseMillis,
                                                 retentionTime,
                                                 expirationTime,
                                                 shred,
                                                 cacheID,
                                                 ctx);
        }

        if (result == null) {
            ctx.dispose();
            throw new RuntimeException("failed to create object");
        }

        CoordinatorContext coctx;
        coctx = new CoordinatorContext((dataSize != NO_CONTENT),
                                       (metadataSize != NO_CONTENT ||
                                        metadataRecord != null));
        
        if (metadataRecord != null) {
            coctx.metadataRecord = metadataRecord;
        }

        ctx.registerPersistentObject(COORDINATOR_KEY, coctx);
        checkInWriteContext(result, ctx);

        return result;
    }

    public void writeData(NewObjectIdentifier oid,
                          ByteBuffer buffer,
                          long offset,
                          boolean explicitClose)
        throws ArchiveException {

        write(oid, buffer, offset, true, true, explicitClose);
    }

    public void writeMetadata(NewObjectIdentifier oid,
                              ByteBuffer buffer,
                              long offset,
                              boolean explicitClose) 
        throws ArchiveException {

        write(oid, buffer, offset, false, true, explicitClose);
    }

    private void appendMetadata(Context ctx,
                                ByteBufferList bufferList,
                                boolean explicitClose) 
        throws ArchiveException {

        ByteBuffer[] buffers = bufferList.getBuffers();

        for (int i = 0; i < buffers.length; i++) {
            write(ctx, buffers[i], UNKNOWN_SIZE, false, false, explicitClose);
        }
    }

    private void write(NewObjectIdentifier oid,
                       ByteBuffer buffer,
                       long offset,
                       boolean isData,
                       boolean validateOffset,
                       boolean explicitClose)
        throws ArchiveException {

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("writing buffer with length " +
                          buffer.remaining() +
                          " to " + ((isData) ? "data" : "metadata") +
                          " stream for oid " +
                          oid.toHexString() +
                          " at offset " + offset);
        }

        Context ctx = checkOutWriteContext(oid, explicitClose);
        try {
            write(ctx, buffer, offset, isData, validateOffset, explicitClose);
        } finally {
            checkInWriteContext(oid, ctx);
        }
    }

    private void write(Context ctx,
                       ByteBuffer buffer,
                       long offset,
                       boolean isData,
                       boolean validateOffset,
                       boolean explicitClose)
        throws ArchiveException {

        CoordinatorContext coctx;
        coctx = (CoordinatorContext)ctx.getPersistentObject(COORDINATOR_KEY);
        BufferState state = (isData) ? coctx.dataState : coctx.metadataState;
        if (isData && !state.enabled) {
            throw new IllegalStateException("not open for " +
                                            ((isData) ? "data" : "metadata") +
                                            " writes");
        }

        if (buffer.remaining() == 0) {
            return;
        }

        ByteBufferList bufferList = state.bufferList;
        if (bufferList == null) {
            bufferList = new ByteBufferList();
            state.bufferList = bufferList;
        }

        // pgates: Later this will reorder buffers. For now just make sure
        // they're in order.
        long stateOffset = state.offset;
        if (validateOffset && offset != stateOffset + bufferList.remaining()) {
            // pgates: update this exception
            throw new IllegalArgumentException("mismatched write at last " +
                                               " offset: actual = " +
                                               offset +
                                               " expected = " +
                                               stateOffset + bufferList.remaining());
        }

        bufferList.appendBuffer(buffer);
        int remaining = bufferList.remaining();
        int bufferSize = backingStore.getWriteBufferSize();
        int count = 0;

        while (remaining >= bufferSize) {
            ByteBufferList writeList;
            writeList = bufferList.slice(count++ * bufferSize, bufferSize);

            try {
		if (isData) {
		    backingStore.writeData(writeList, stateOffset, ctx);
		} else {
		    backingStore.writeMetadata(writeList, stateOffset, ctx);
		}
            } finally {
                writeList.clear();
            }

            stateOffset += bufferSize;
            remaining -= bufferSize;
        }

        if (remaining > 0) {
            state.bufferList = bufferList.slice(count * bufferSize, remaining);
        } else {
            state.bufferList = null;
        }

        state.offset = stateOffset;
        bufferList.clear();
    }

    // pgates: What do we need to do about locally buffered data and
    // metadata?
    public void commit(NewObjectIdentifier oid, 
                       boolean explicitClose)
        throws ArchiveException {

        Context ctx = checkOutWriteContext(oid, explicitClose);
        try {
            backingStore.commit(ctx);
        } finally {
            checkInWriteContext(oid, ctx);
        }
    }

    public long getCommittedDataSize(NewObjectIdentifier oid, 
                                     boolean explicitClose) 
        throws ArchiveException {

        return getCommittedSize(oid, true, explicitClose);
    }

    public long getCommittedMetadataSize(NewObjectIdentifier oid,
                                         boolean explicitClose) 
        throws ArchiveException {

        return getCommittedSize(oid, false, explicitClose);
    }

    private long getCommittedSize(NewObjectIdentifier oid, boolean isData,
                                  boolean explicitClose) 
        throws ArchiveException {

        Context ctx = checkOutWriteContext(oid, explicitClose);
        long result = 0;

        try {
            CoordinatorContext coctx;
            coctx = (CoordinatorContext)ctx.getPersistentObject(COORDINATOR_KEY);

            BufferState state = (isData) ? coctx.dataState : coctx.metadataState;
            if (!state.enabled) {
                throw new IllegalStateException("not open for " +
                                                ((isData) ? "data" : "metadata") +
                                                " writes");
            }

            result = state.offset;
            if (state.bufferList != null) {
                result += state.bufferList.remaining();
            }
        } finally {
            checkInWriteContext(oid, ctx);
        }

        return result;
    }

    public SystemMetadata close(NewObjectIdentifier oid)
        throws ArchiveException {

        Context ctx = checkOutWriteContext(oid, true);
        ByteBufferList extraMetadata = null;
        SystemMetadata smd = null;

        try {
            CoordinatorContext coctx =
            (CoordinatorContext)ctx.getPersistentObject(COORDINATOR_KEY);

            BufferState state = coctx.dataState;
            if (state.enabled) {
                if (state.bufferList != null && state.bufferList.remaining() > 0) {
                    backingStore.writeData(state.bufferList, state.offset, ctx);
                }
            }

            boolean hasMetadata = false;
            state = coctx.metadataState;
            if (state.enabled &&
                (state.offset > 0 ||
                 (state.bufferList != null &&
                  state.bufferList.remaining() > 0))) {
                hasMetadata = true;
            }

            // We have to call call closeData in all cases because the backing store
            // must be given a chance to append extra metadata to the metadata object
            extraMetadata = new ByteBufferList();
            smd = backingStore.closeData(extraMetadata, hasMetadata, ctx);

            if (extraMetadata.remaining() > 0) {
                appendMetadata(ctx, extraMetadata, true);
            }

            if (state.enabled) {
                if (state.bufferList != null && state.bufferList.remaining() > 0) {
                    backingStore.writeMetadata(state.bufferList, state.offset, ctx);
                }

                SystemMetadata mdsmd = backingStore.closeMetadata(ctx);

                // The update of the MD SystemMetadata is temporary fix.
                // smd can be null in the case where we have a MD only entry (e.g. FS directories)
                if (smd != null) {
                    mdsmd.setSize(smd.getSize());
		    mdsmd.setContentHash(smd.getContentHash());
		    mdsmd.setHashAlgorithm(smd.getHashAlgorithm());
                }

                smd = mdsmd;
            }

            ctx.dispose();
        } catch (ArchiveException e) {
            checkInWriteContext(oid, ctx);
            throw e;
        } finally {
            if (extraMetadata != null) {
                extraMetadata.clear();
            }
        }

        return smd;
    }

    public void abortWrite(NewObjectIdentifier oid)
        throws ArchiveException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("aborting write for OID " + oid.toHexString());
        }

        Context ctx = (Context)writeContexts.remove(oid.toHexString());
        if (ctx != null) {
            abortWrite(ctx);
        }
    }

    private void abortWrite(Context ctx)
        throws ArchiveException {
        if (ctx != null) {
            ctx.dispose();
        }
    }

    
    // Gets the proxy for each coordinator (including mine)
    // Calls a local delete() on each.
    public void delete(NewObjectIdentifier oid,
		       boolean immediate,
		       boolean shred) 
	throws ArchiveException {

        NewObjectIdentifier dataOid = null;
        try {
            dataOid = backingStore.resolveLink(oid);
        } catch (ArchiveException e) { 
            // ignore; data won't be cleared from cache
        }

        // First delete it from the backing store
        backingStore.delete(oid, immediate, shred);
	    
        // Now clear from all caches we can
        ProtocolProxy[] proxies = ProtocolProxy.getProxies();
        if(proxies == null) {
            throw new ArchiveException("Failed to get protocol proxies");
        } else {
            for(int p = 0; p < proxies.length; p++) {
                if(proxies[p] != null) {
                    proxies[p].apiCallback(EventRegistrant.API_DELETE, oid, dataOid);
                }
            }
        }
    }
    
    // called by delete() above on each node (including this one) via
    // proxy rpc (which is why it's public).
    // Clears resources for both MD OID and data OID.
    public void deleteFromLocalCache(NewObjectIdentifier oid, NewObjectIdentifier dataOid)
        throws ArchiveException {
	
	// Remove from block caches
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Removing from block cache oid " + oid.toHexString());
            if (dataOid != null && !dataOid.equals(oid)) {
                LOGGER.fine("Removing from block cache oid " + dataOid.toHexString());                
            }
        }

	synchronized (dataCache) {
	    dataCache.delete(oid);
            if (dataOid != null && !dataOid.equals(oid)) {
                dataCache.delete(dataOid);
            }
	}
	
	synchronized (metadataCache) {
	    metadataCache.delete(oid);
            // dataOid is never in MD cache
        }

	// Remove any read data or metadata or write contexts
	// Otherwise a quick read, delete, read sequence will return a
	// deleted object
	removeReadContexts(oid);
	removeWriteContext(oid);
        if (dataOid != null && !dataOid.equals(oid)) {
            removeReadContexts(dataOid);
            removeWriteContext(dataOid);
        }
    }
    
    public int readData(NewObjectIdentifier oid,
                        ByteBufferList bufferList,
                        long offset, 
                        int length,
                        boolean isLast)
        throws ArchiveException {

        return read(oid,
                    bufferList,
                    offset,
                    length,
                    isLast,
                    true);
    }

    public int readMetadata(NewObjectIdentifier oid,
                            ByteBufferList bufferList,
                            long offset,
                            int length,
                            boolean isLast) 
        throws ArchiveException {

        return read(oid,
                    bufferList,
                    offset,
                    length,
                    isLast,
                    false);
    }

    private int read(NewObjectIdentifier oid,
                     ByteBufferList bufferList,
                     long offset,
                     int length,
                     boolean isLast,
                     boolean isData)
        throws ArchiveException {
	
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("offset and length must be" +
                                               " non-negative");
        }

        long firstIndex = offset / readBufferSize;
        int firstPosition = (int)(offset % (long)readBufferSize);

        long lastIndex = (offset + length) / readBufferSize;
        int lastLimit = (int)((offset + (long)length) % (long)readBufferSize);
        if (lastLimit == 0 && lastIndex > 0) {
            lastIndex--;
            lastLimit = readBufferSize;
        }

        ByteBufferPool bufferPool = ByteBufferPool.getInstance();
        ByteBuffer firstBuffer = getBuffer(oid, firstIndex, isData);
        int result = 0;

        // Assume buffers are filled from the beginning
        // if the first buffer doesn't have enough data to get past
        // the position, we're done. This could be true if the buffer
        // is null or empty.
        if (firstBuffer == null) {
            return result;
        }

        if (firstBuffer.remaining() <= firstPosition) {
            bufferPool.checkInBuffer(firstBuffer);
            return result;
        }

        firstBuffer.position(firstPosition);
        if (firstIndex == lastIndex) {
            firstBuffer.limit(Math.min(firstBuffer.limit(), lastLimit));
        }

        bufferList.appendBuffer(firstBuffer);
        boolean done = false;

        if (firstIndex != lastIndex) {
            ByteBuffer lastBuffer = null;

            if (firstBuffer.limit() == readBufferSize) {
                for (firstIndex++; firstIndex < lastIndex; firstIndex++) {
                    lastBuffer = getBuffer(oid, firstIndex, isData);
                    int remaining = 0;

                    if (lastBuffer != null) {
                        remaining = lastBuffer.remaining();

                        if (remaining > 0) {
                            result += remaining;
                            bufferList.appendBuffer(lastBuffer);
                        }

                        bufferPool.checkInBuffer(lastBuffer);
                    }

                    // If the buffer is smaller than the "normal" read
                    // buffer size it must be the last one, otherwise
                    // getBuffer would have thrown.
                    if (lastBuffer == null || remaining != readBufferSize) {
                        done = true;
                        break;
                    }
                }
            }

            if (!done) {
                lastBuffer = getBuffer(oid, lastIndex, isData);

                if (lastBuffer != null) {
                    if (lastBuffer.hasRemaining()) {
                        lastBuffer.limit(Math.min(lastBuffer.limit(), lastLimit));
                        bufferList.appendBuffer(lastBuffer);

                        result += lastBuffer.remaining();
                    }

                    bufferPool.checkInBuffer(lastBuffer);
                }
            }
        }

        if (isLast) {
            removeReadContext(oid, isData);
        }

        result += firstBuffer.remaining();
        bufferPool.checkInBuffer(firstBuffer);

        return result;
    }

    private ByteBuffer getBuffer(NewObjectIdentifier oid,
                                 long blockID,
                                 boolean isData)
        throws ArchiveException {

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("get " +
                          ((isData) ? "data" : "metadata") +
                          " buffer: oid = " +
                          oid.toHexString() +
                          " block = " +
                          blockID);
        }

        ByteBufferPool bufferPool = ByteBufferPool.getInstance();
        ByteBuffer result = null;

        if (!cachingEnabled) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("caching disabled - looking up context");
            }
        } else {
            BlockCache cache = (isData) ? dataCache : metadataCache;
	  
            synchronized (cache) {
                result = cache.get(oid, blockID);
                if (result != null) {
                    result = bufferPool.checkOutReadOnlyBuffer(result);
                }
            }

            if (result != null) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("cache hit - returning block with " +
                                  result.remaining() +
                                  " bytes");
                }

                return result;
            }

            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("cache missed - looking up context");
            }
        }

        Context ctx = checkOutReadContext(oid, blockID, isData);

        long objectSize;

        if (ctx != null) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("found context");
            }

            CoordinatorContext coctx =
                (CoordinatorContext)ctx.getTransientObject(COORDINATOR_KEY);
            objectSize = coctx.objectSize;
        } else {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("no context - creating one");
            }

            SystemMetadata smd;
            ctx = new Context();

            smd = null;
            try {
                smd = (isData)
                    ? backingStore.openData(oid, ctx)
                    : backingStore.openMetadata(oid, ctx);
            } catch (ArchiveException e) {
                ctx.dispose();
                throw e;
            }

            objectSize = smd.getSize();
            CoordinatorContext coctx = new CoordinatorContext(isData, !isData);
            coctx.objectSize = objectSize;

            ctx.registerTransientObject(COORDINATOR_KEY, coctx);
        }

        int bufferSize = getReadBufferSize(blockID, objectSize);
        if (bufferSize <= 0) {
            ctx.dispose();
            return null;
        }

        long readOffset = blockID * readBufferSize;

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("reading from backing store " +
                          "oid = " + oid.toHexString() +
                          " object size = " + objectSize +
                          " buffer size = " + bufferSize +
                          " read offset = " + readOffset);
        }

        ByteBuffer buffer = bufferPool.checkOutBuffer(bufferSize);

        try {
            backingStore.read(buffer, readOffset, bufferSize, ctx);
        } catch (ArchiveException e) {
            bufferPool.checkInBuffer(buffer);
            ctx.dispose();

            throw e;
        }

        buffer.flip();

        if (!cachingEnabled) {
            result = buffer;
        } else {
            BlockCache cache = (isData) ? dataCache : metadataCache;
            synchronized (cache) {
                result = bufferPool.checkOutReadOnlyBuffer(buffer);
                cache.put(oid, blockID, buffer);
            }
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("caching context");
        }

        checkInReadContext(oid, blockID, isData, ctx);

        return result;
    }

    private int getReadBufferSize(long blockID, long objectSize) {
        long bytesRemaining = objectSize - (blockID * readBufferSize);

        if (bytesRemaining < 0) {
            return 0;
        }

        return (bytesRemaining >= (long)readBufferSize)
            ? readBufferSize
            : backingStore.getLastReadBufferSize((int)bytesRemaining);
    }

    private static class CoordinatorContext implements Codable, Disposable {

        BufferState dataState;
        BufferState metadataState;
        CacheRecord metadataRecord;
        long objectSize;

        CoordinatorContext(boolean dataEnabled, boolean metadataEnabled) {
            dataState = new BufferState(dataEnabled);
            metadataState = new BufferState(metadataEnabled);
        }

        public void encode(Encoder encoder) {
            encoder.encodeKnownClassCodable(dataState);
            encoder.encodeKnownClassCodable(metadataState);
        }

        public void decode(Decoder decoder) {
            dataState = new BufferState(false);
            decoder.decodeKnownClassCodable(dataState);

            metadataState = new BufferState(false);
            decoder.decodeKnownClassCodable(metadataState);
        }

        public void dispose() {
            if (dataState != null) {
                dataState.dispose();
            }

            if (metadataState != null) {
                metadataState.dispose();
            }
        }
    }

    private static class BufferState implements Codable, Disposable {
        boolean enabled;
        ByteBufferList bufferList;
        long offset;

        private BufferState(boolean newEnabled) {
            enabled = newEnabled;
            bufferList = null;
            offset = 0;
        }

        public void encode(Encoder encoder) {
        }

        public void decode(Decoder decoder) {
        }

        public void dispose() {
            if (bufferList != null) {
                bufferList.clear();
            }
        }
    }


    // Interface EventRegistrant
    public boolean apiCallback(int evt, NewObjectIdentifier oid, NewObjectIdentifier doid) {
        switch (evt) {
        case EventRegistrant.API_DELETE:
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("apiCallback, evt = API_DELETE:" + evt + ", oid " + oid + ", data oid " + doid);
            }
            try {
                deleteFromLocalCache(oid, doid);
            } catch (ArchiveException ae){
                LOGGER.severe("cannot delete from cache oid " + oid + ", data oid " + doid);
                return false;
            }
            return true;
        default:
            LOGGER.severe("evt " + evt + " not implemented");
            return false;
        }
    }

    /* Access to block cache statistics
     */
    public int getCacheHits(boolean data) {
        return (data ? dataCache.hits : metadataCache.hits);
    }
    public int getCacheMisses(boolean data) {
        return (data ? dataCache.misses : metadataCache.misses);
    }
    public int getCachePuts(boolean data) {
        return (data ? dataCache.puts : metadataCache.puts);
    }
    public int getCachePuthits(boolean data) {
        return (data ? dataCache.puthits : metadataCache.puthits);
    }
    public int getCacheDeletes(boolean data) {
        return (data ? dataCache.deletes : metadataCache.deletes);
    }
    public int getCacheEjects(boolean data) {
        return (data ? dataCache.ejects : metadataCache.ejects);
    }
    public int getCacheSize(boolean data) {
        return (data ? dataCache.getCacheSize() : metadataCache.getCacheSize());
    }
    public int getCacheMaxSize(boolean data) {
        return (data ? dataCache.getCacheMaxSize() : metadataCache.getCacheMaxSize());
    }
    // One-liner output
    public String getCacheStats(boolean data) {
        return (data ? dataCache.getCacheStats(false) : metadataCache.getCacheStats(false));
    }
    // Dump entire cache contents
    public String getCacheStats(boolean data, boolean verbose) {
        return (data ? dataCache.getCacheStats(verbose) : metadataCache.getCacheStats(verbose));
    }
    public void resetCacheStats(boolean data) {
        if (data) {
            dataCache.resetStats();
        } else { 
            metadataCache.resetStats();
        }
    }

    // Set the retention time
    public void setRetentionTime(NewObjectIdentifier oid, long retentionTime)
	throws ArchiveException {
        backingStore.setRetentionTime(oid, retentionTime);
    }

    // Get the retention time
    public long getRetentionTime(NewObjectIdentifier oid)
	throws ArchiveException {
        return backingStore.getRetentionTime(oid);
    }

    // Add a legal hold tag
    public void addLegalHold(NewObjectIdentifier oid, String legalHold)
	throws ArchiveException {
        backingStore.addLegalHold(oid, legalHold);
    }

    // Remove a legal hold tag
    public void removeLegalHold(NewObjectIdentifier oid, String legalHold)
	throws ArchiveException {
        backingStore.removeLegalHold(oid, legalHold);
    }

    public String[] getLegalHolds(NewObjectIdentifier oid)
        throws ArchiveException {
        return backingStore.getLegalHolds(oid);
    }

    /**
     * Replace the set of legal holds with the given set.
     *
     * Use with caution! This will delete anything not in the given
     * set!
    */
    public void setLegalHolds(NewObjectIdentifier oid, String[] newHolds)
        throws ArchiveException {
        backingStore.setLegalHolds(oid, newHolds);
    }

    public boolean isComplianceDeletable(NewObjectIdentifier oid)
        throws ArchiveException {
        return backingStore.isComplianceDeletable(oid);
    }

    public CacheRecord parseMetadata(String cacheId,
                                     InputStream in, 
                                     long mdLength,
                                     Encoding encoding) 
        throws ArchiveException {
        return backingStore.parseMetadata(cacheId, in, mdLength, encoding);
    }

    /**
     * force an OID to appear in the cache, if not there already
     * @returns -1 if already there, 0 if still missing, or 1 if added
    */
    public int checkIndexed(String cacheId, NewObjectIdentifier oid)
        throws ArchiveException {
        return backingStore.checkIndexed(cacheId,oid);
    }


}
