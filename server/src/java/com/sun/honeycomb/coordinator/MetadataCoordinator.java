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

import java.util.logging.Logger;
import java.util.Arrays;

import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.common.DeletedObjectException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.resources.ByteBufferList;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import com.sun.honeycomb.common.SystemMetadata;
import java.nio.ByteBuffer;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.emd.cache.CacheManager;
import java.util.logging.Level;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.fs.FSCacheEntry;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;
import java.util.HashMap;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.XMLException;
import java.io.IOException;
import com.sun.honeycomb.emd.cache.MDHeader;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.SysCacheUtils;


public class MetadataCoordinator
    implements BackingStore {

    /**********************************************************************
     *
     * Singleton
     *
     **********************************************************************/

    private static MetadataCoordinator instance = null;

    public static synchronized MetadataCoordinator getInstance() {
        if (instance == null) {
            instance = new MetadataCoordinator();
        }
        return(instance);
    }

    /**********************************************************************
     *
     * Static fields
     *
     **********************************************************************/

    private static Logger LOG = Logger.getLogger(MetadataCoordinator.class.getName());

    /**********************************************************************
     *
     * MetadataCoordinator methods
     *
     **********************************************************************/

    private OAClient oaClient;
    private MetadataClient mdClient;

    private MetadataCoordinator() {
        oaClient = OAClient.getInstance();
        mdClient = MetadataClient.getInstance();
    }
    
    /****************************************
     *
     * Persistent context class
     *
     ****************************************/

    public static class MDPersistentContext
        implements Codable, Disposable {

        private static final byte STORING_DATA          = 0x1;
        private static final byte STORING_METADATA      = 0x2;

        private byte mode;
        private String cacheId;
        private CacheRecord metadataObject;
        private NewObjectIdentifier linkValue;

        public MDPersistentContext() {
            mode = 0;
            cacheId = null;
            metadataObject = null;
            linkValue = null;
        }

        private MDPersistentContext(byte nMode,
                                    String nCacheId,
                                    NewObjectIdentifier nLinkValue) 
	    throws EMDException {
            mode = nMode;
            cacheId = nCacheId;
            metadataObject = null;
            linkValue = nLinkValue;
            
            if((mode & STORING_METADATA) != 0) {
                // The next call is only to check that the cache exists
                CacheManager.getInstance().getClientInterface(cacheId);
            }
        }
        
        private MDPersistentContext(byte nMode,
                                    CacheRecord nMetadataObject,
                                    NewObjectIdentifier nLinkValue) {
            mode = nMode;
            cacheId = nMetadataObject.getCacheID();
            metadataObject = nMetadataObject;
            linkValue = nLinkValue;
        }

        private boolean storingData() {
            return((mode & STORING_DATA) != 0);
        }

        private boolean storingMetadata() {
            return((mode & STORING_METADATA) != 0);
        }

        private String getCacheId() {
            return(cacheId);
        }

        private CacheRecord getMetadataObject() {
            return(metadataObject);
        }

        private NewObjectIdentifier getLink() {
            return(linkValue);
        }

        public void encode(Encoder encoder) {
            encoder.encodeByte(mode);

            boolean hasCacheId = (cacheId != null);
            encoder.encodeBoolean(hasCacheId);
            if (hasCacheId) {
                encoder.encodeString(cacheId);
            }

            boolean hasMetadataObject = (metadataObject != null);
            encoder.encodeBoolean(hasMetadataObject);
            if (hasMetadataObject) {
                encoder.encodeCodable(metadataObject);
            }

            boolean hasLink = (linkValue != null);
            encoder.encodeBoolean(hasLink);
            if (hasLink) {
                encoder.encodeCodable(linkValue);
            }
        }
        
        public void decode(Decoder decoder) {
            mode = decoder.decodeByte();

            boolean hasCacheId = decoder.decodeBoolean();
            cacheId = null;
            if (hasCacheId) {
                cacheId = decoder.decodeString();
            }

            boolean hasMetadataObject = decoder.decodeBoolean();
            metadataObject = null;
            if (hasMetadataObject) {
                metadataObject = (CacheRecord)decoder.decodeCodable();
            }

            boolean hasLink = decoder.decodeBoolean();
            linkValue = null;
            if (hasLink) {
                linkValue = (NewObjectIdentifier)decoder.decodeCodable();
            }
        }
        
        public void dispose() {
        }
    }
    
    /****************************************
     *
     * ContextConsumer API
     *
     ****************************************/

    public void restoreContextForStore(NewObjectIdentifier oid,
                                       boolean explicitClose,
                                       Context ctx)
        throws ArchiveException {
        oaClient.restoreContextForStore(oid, explicitClose, ctx);
    }

    public void restoreContextForRetrieve(NewObjectIdentifier oid,
                                          Context ctx)
        throws ArchiveException {
        oaClient.restoreContextForRetrieve(oid, ctx);
    }

    public void acquireResourcesForStore(Context ctx)
        throws ArchiveException {
            oaClient.acquireResourcesForStore(ctx);
    }

    public void acquireResourcesForRetrieve(Context ctx)
        throws ArchiveException {
            oaClient.acquireResourcesForRetrieve(ctx);
    }

    /****************************************
     *
     * BackingStore APIs
     *
     ****************************************/

    private static final String CONTEXT_PERSISTENT      = "MDPersistent";
    private static final String CONTEXT_OA_DATA         = "OAData";
    private static final String CONTEXT_MD_OID          = "MDOID";

    public int getWriteBufferSize() {
        return oaClient.getWriteBufferSize();
    }

    public int getReadBufferSize() {
        return oaClient.getReadBufferSize();
    }

    public int getReadBufferSize(Context ctx) {
        return oaClient.getReadBufferSize(ctx);
    }

    public int getLastReadBufferSize(int length) {
        return oaClient.getLastReadBufferSize(length);
    }

    /********** createbject Object APIs **********/

    private NewObjectIdentifier createObject(long dataSize,
                                             long metadataSize,
                                             int metadataLayoutMapId,
                                             int autoCloseMillis,
                                             long retentionTime,
                                             long expirationTime,
                                             byte shred,
                                             Context ctx,
                                             MDPersistentContext persistentContext) throws ArchiveException {
        Context                 dataCtx         = null;
        Context                 metadataCtx     = null;
        NewObjectIdentifier     dataOID         = NewObjectIdentifier.NULL;
        NewObjectIdentifier     returnValue     = null;

	
        // Create the appropriate contextes
        if (persistentContext.storingMetadata()) {
            metadataCtx = ctx;
            if (persistentContext.storingData()) {
                dataCtx = new Context();
                ctx.registerPersistentObject(CONTEXT_OA_DATA, dataCtx);
            }
        } else if (persistentContext.storingData()) {
            dataCtx = ctx;
        }
	
        if (persistentContext.storingData()) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Creating a data store context");
            }
	    
	    // Advanced API store of data object means data object isRefRoot
	    boolean dataIsRefRoot = !persistentContext.storingMetadata();
            
	    dataOID = oaClient.create(dataSize, NewObjectIdentifier.NULL, -1,
                                      false, dataIsRefRoot, autoCloseMillis,
                                      retentionTime, expirationTime,
                                      shred, dataCtx);
            returnValue = dataOID;
        }
        
        if (persistentContext.storingMetadata()) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Creating a metadata store context");
            }
            returnValue = oaClient.create(metadataSize, dataOID, metadataLayoutMapId,
                                          true, true, autoCloseMillis,
                                          retentionTime, expirationTime,
                                          shred, metadataCtx);
            ctx.registerTransientObject(CONTEXT_MD_OID, returnValue);
        }
        
        return(returnValue);
    }

    public NewObjectIdentifier createObject(long dataSize,
                                            long metadataSize,
                                            int autoCloseMillis,
                                            long retentionTime,
                                            long expirationTime,
                                            byte shred,
                                            String cacheID,
                                            Context ctx)
        throws ArchiveException{
        byte storeMode = 0;
        
        if (dataSize != Coordinator.NO_CONTENT) {
            storeMode |= MDPersistentContext.STORING_DATA;
        }
        if (metadataSize != Coordinator.NO_CONTENT) {
            storeMode |= MDPersistentContext.STORING_METADATA;
            if (cacheID == null) {
                cacheID = CacheClientInterface.EXTENDED_CACHE;
            }
        }

        MDPersistentContext persistentContext = new MDPersistentContext(storeMode,
                                                                        cacheID,
                                                                        null);
        ctx.registerPersistentObject(CONTEXT_PERSISTENT, persistentContext);
        return(createObject(dataSize, metadataSize, -1, autoCloseMillis, retentionTime,
                            expirationTime, shred, ctx, persistentContext));
    }

    public NewObjectIdentifier createObject(long dataSize,
                                            CacheRecord metadataObject,
                                            int autoCloseMillis,
                                            long retentionTime,
                                            long expirationTime,
                                            byte shred,
                                            Context ctx)
        throws ArchiveException{
        byte storeMode = MDPersistentContext.STORING_METADATA;
        


        if (dataSize != Coordinator.NO_CONTENT) {
            storeMode |= MDPersistentContext.STORING_DATA;
        }

        CacheClientInterface cache =
            CacheManager.getInstance().getClientInterface(metadataObject.getCacheID());
        cache.sanityCheck(metadataObject);

        MDPersistentContext persistentContext = new MDPersistentContext(storeMode,
                                                                        metadataObject,
                                                                        null);
        ctx.registerPersistentObject(CONTEXT_PERSISTENT, persistentContext);

        int metadataLayoutMapId = cache.getMetadataLayoutMapId(metadataObject,
                                                               LayoutClient.NUM_MAP_IDS);
        
        return(createObject(dataSize, Coordinator.UNKNOWN_SIZE, metadataLayoutMapId, autoCloseMillis,
                            retentionTime, expirationTime, shred, ctx, persistentContext));
    }
    
    /********** createMetadata APIs **********/

    /* Public access because Coordinator needs this on delete.
     */
    public NewObjectIdentifier resolveLink(NewObjectIdentifier linkOID)
        throws ArchiveException {
        NewObjectIdentifier result;

        if (linkOID.getObjectType() != NewObjectIdentifier.METADATA_TYPE) {
            result = linkOID;
        } else {
            // Resolve the link
            Context tmpctx = new Context();
            SystemMetadata systemMetadata = oaClient.open(linkOID, tmpctx);
            tmpctx.dispose();

            result = (NewObjectIdentifier)systemMetadata.get(SystemMetadata.FIELD_LINK);
        }

        return result;
    }

    public NewObjectIdentifier createMetadata(NewObjectIdentifier linkOID,
                                              long metadataSize,
                                              int autoCloseMillis,
                                              long retentionTime,
                                              long expirationTime,
                                              byte shred,
                                              String cacheID,
                                              Context ctx)
        throws ArchiveException {
        byte storeMode = 0;
        
        if (metadataSize == Coordinator.NO_CONTENT) {
            throw new IllegalArgumentException("createMetadata cannot accept a metadata size of "
                                               +Coordinator.NO_CONTENT);
        }

        storeMode |= MDPersistentContext.STORING_METADATA;
        if (cacheID == null) {
            cacheID = CacheClientInterface.EXTENDED_CACHE;
        }

        NewObjectIdentifier link = resolveLink(linkOID);

        MDPersistentContext persistentContext = new MDPersistentContext(storeMode,
                                                                        cacheID,
                                                                        link);
        ctx.registerPersistentObject(CONTEXT_PERSISTENT, persistentContext);

        Context metadataCtx = ctx;
        NewObjectIdentifier returnValue = null;
        
	// In 1.0 all metadata objects are refRoots, but later we may
	// have reference chains of metadata objects, and then only
	// the outermost metadata object would be a refRoot.  If we
	// make that change, we'll need a way of detecting it here. - jW
	boolean isRefRoot = true;
	
	// If the link is another object, then we need to first increment that object's refCount
	// If we don't succeed at that, then we need abort and not create the new referring object.
	// That will happen because incRefCount will throw an ArchiveException that the inc failed.
	// TODO: If this fails, do we need to unregisterPersistentObject?
	if(!link.equals(NewObjectIdentifier.NULL)) {
	    oaClient.incRefCount(link);
        SystemMetadata sm;
        try {
            sm = oaClient.getSystemMetadata(link, true, false);
            SysCacheUtils.updateRecord(link, sm);
        } catch (OAException e) {
            throw new ArchiveException(e);
        }
	}

        returnValue = oaClient.create(metadataSize, link, -1,
                                      true, isRefRoot, autoCloseMillis,
                                      retentionTime, expirationTime,
                                      shred, metadataCtx);
        ctx.registerTransientObject(CONTEXT_MD_OID, returnValue);
        
        return(returnValue);
    }

    public NewObjectIdentifier createMetadata(NewObjectIdentifier linkOID,
                                              CacheRecord metadataRecord,
                                              int autoCloseMillis,
                                              long retentionTime,
                                              long expirationTime,
                                              byte shred,
                                              Context ctx)
        throws ArchiveException{
        byte storeMode = 0;
        
        if (metadataRecord == null) {
            throw new IllegalArgumentException("createMetadata cannot accept a null metadataRecord");
        }

        NewObjectIdentifier link = resolveLink(linkOID);

        storeMode |= MDPersistentContext.STORING_METADATA;

        CacheClientInterface cache =
            CacheManager.getInstance().getClientInterface(metadataRecord.getCacheID());
        cache.sanityCheck(metadataRecord);

        MDPersistentContext persistentContext = new MDPersistentContext(storeMode,
                                                                        metadataRecord,
                                                                        link);
        ctx.registerPersistentObject(CONTEXT_PERSISTENT, persistentContext);

        Context metadataCtx = ctx;
        NewObjectIdentifier returnValue = null;
	
        // In 1.0 all metadata objects are refRoots, but later we may
        // have reference chains of metadata objects, and then only
        // the outermost metadata object would be a refRoot.  If we
        // make that change, we'll need a way of detecting it here. - jW
        boolean isRefRoot = true;
	
        // If the link is another object, then we need to first increment that object's refCount
        // If we don't succeed at that, then we need abort and not create the new referring object.
        // That will happen because incRefCount will throw an ArchiveException that the inc failed.
        // TODO: If this fails, do we need to unregisterPersistentObject?
        if(!link.equals(NewObjectIdentifier.NULL)) {
            oaClient.incRefCount(link);
            SystemMetadata sm;
            try {
                sm = oaClient.getSystemMetadata(link, true, false);
                SysCacheUtils.updateRecord(link, sm);
            } catch (OAException e) {
                throw new ArchiveException(e);
            }
        }

        returnValue = oaClient.create(Coordinator.UNKNOWN_SIZE, link, -1,
                                      true, isRefRoot, autoCloseMillis,
                                      retentionTime, expirationTime,
                                      shred, metadataCtx);
        ctx.registerTransientObject(CONTEXT_MD_OID, returnValue);
        
        return(returnValue);
    }

    /********** writeData **********/

    public void writeData(ByteBufferList newList,
                          long offset,
                          Context ctx) throws ArchiveException {
        MDPersistentContext persistentContext =
            (MDPersistentContext)ctx.getPersistentObject(CONTEXT_PERSISTENT);
        
        if (!persistentContext.storingData()) {
            throw new IllegalArgumentException("Cannot write data using the created context");
        }

        Context dataContext = null;

        if (persistentContext.storingMetadata()) {
            dataContext = (Context)ctx.getPersistentObject(CONTEXT_OA_DATA);
        } else {
            dataContext = ctx;
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Writing data ["+offset+" - "+newList.remaining()+"]");
        }

        oaClient.write(newList, offset, dataContext);
    }

    /********** writeMetadata **********/

    public void writeMetadata(ByteBufferList newList,
                              long offset,
                              Context ctx) throws ArchiveException {
        MDPersistentContext persistentContext =
            (MDPersistentContext)ctx.getPersistentObject(CONTEXT_PERSISTENT);
        
        if (!persistentContext.storingMetadata()) {
            throw new IllegalArgumentException("Cannot write metadata using the created context");
        }
        
        // The metadata context is always the top one
        Context metadataContext = ctx;
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Writing metadata ["+offset+" - "+newList.remaining()+"]");
        }
        
        oaClient.write(newList, offset, metadataContext);
    }
    
    /********** commit **********/

    public void commit(Context ctx) throws ArchiveException {
        oaClient.commit(ctx);
    }

    /********** closeData **********/
    
    private void writeMetadataToOA(ByteBufferList bufferList,
                                   ByteArrayOutputStream stream) {
        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer byteBuffer = pool.checkOutBuffer(stream.size());
        
        byteBuffer.put(stream.toByteArray());
        byteBuffer.flip();
        bufferList.appendBuffer(byteBuffer);
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Wrote "+byteBuffer.remaining()+" bytes to metadata");
        }
        pool.checkInBuffer(byteBuffer);
    }

    public SystemMetadata closeData(ByteBufferList bufferList,
                                    boolean hasMetadata,
                                    Context ctx)
        throws ArchiveException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Performing a closeData");
        }


        MDPersistentContext persistentContext =
            (MDPersistentContext)ctx.getPersistentObject(CONTEXT_PERSISTENT);

        SystemMetadata dataSystemMetadata = null;
        Context dataContext = null;
        Context metadataContext = null;

        if (persistentContext.storingMetadata()) {
            metadataContext = ctx;
            if (persistentContext.storingData()) {
                dataContext = (Context)ctx.getPersistentObject(CONTEXT_OA_DATA);
            }
        } else {
            if (!persistentContext.storingData()) {
                throw new IllegalArgumentException("closeData has been called on a context"
                                                   +" not storing data nor metadata");
            }
            dataContext = ctx;
        }

        if (persistentContext.storingData()) {
            // Close the data
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Closing the data context");
            }

            SystemMetadata[] dataSystemMetadatas = 
                oaClient.close(dataContext,
                               new byte[FragmentFooter.METADATA_FIELD_LENGTH]);

            // Insert MD into system cache(s)
            SysCacheUtils.insertRecord(dataSystemMetadatas);
            
            // got to return 1
            dataSystemMetadata = 
                oaClient.compressChunkSM(dataSystemMetadatas);
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer("dataSystemMetadata (case A)="+dataSystemMetadata);
            }
        } else {
            /*
             * We have to retrieve the SystemMetadata of the data
             * themselves
             */
            NewObjectIdentifier dataOID = (NewObjectIdentifier)persistentContext.getLink();
            
            if (dataOID != null) {
                Context tmpctx = new Context();
                try {
                    dataSystemMetadata = oaClient.open(dataOID, tmpctx);
                    if (LOG.isLoggable(Level.FINER)) {
                        LOG.finer("dataSystemMetadata (case B)="+dataSystemMetadata);
                    }
                } catch (ArchiveException nsoe) {
                    LOG.warning("Failed open dataSystemMetadata: " + nsoe);
                    return null;
                } finally {
                    tmpctx.dispose();
                }
            }
        }
        
        if ((persistentContext.storingMetadata() && !hasMetadata)
            || (persistentContext.getMetadataObject() != null)) {
            // We have to write the metadata themselves

            CacheRecord mdObject = persistentContext.getMetadataObject();

            // Hook for the filesystem cache to update some fields.
            if (persistentContext.getCacheId().equals(CacheClientInterface.FILESYSTEM_CACHE)) {
                if (dataSystemMetadata != null) {
                    ((FSCacheEntry)mdObject).setSize(dataSystemMetadata.getSize());
                }
                ((FSCacheEntry)mdObject).updateMTime();
            }

            // If .getMetadataObject is null, this is OK. That means that the MD
            // channel is empty, something describing the empty content has to be
            // generated by the cache
            CacheClientInterface cache = CacheManager.getInstance().getClientInterface(persistentContext.getCacheId());
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            
            try {
                cache.generateMetadataStream(mdObject, stream);
            } catch (EMDException e) {
                InternalException newe = new InternalException("generateMetadataStream failed ["+
                                                               e.getMessage()+"]", e);
                newe.initCause(e);
                throw newe;
            }
            writeMetadataToOA(bufferList, stream);
        }
        
        boolean hasToGenerateSystemMD = 
            (persistentContext.storingMetadata())
            && ( (persistentContext.getCacheId().equals(CacheClientInterface.EXTENDED_CACHE))
                 || (persistentContext.getCacheId().equals(CacheClientInterface.FILESYSTEM_CACHE)) );
        
        if (hasToGenerateSystemMD) {
            // Generate and store some system MD
            
            NewObjectIdentifier mdOID = (NewObjectIdentifier)ctx.getTransientObject(CONTEXT_MD_OID);
            long ctime = System.currentTimeMillis();
            SystemMetadata hybridSMD = null;
            
            if (dataSystemMetadata != null) {
                hybridSMD = new SystemMetadata(mdOID,
                                               dataSystemMetadata.getContentHash(), dataSystemMetadata.getHashAlgorithm(),
                                               ctime, mdOID.getLayoutMapId(),
                                               dataSystemMetadata.getSize());
            } else {
                hybridSMD = new SystemMetadata(mdOID, null, "Unknown",
                                               ctime, mdOID.getLayoutMapId(),
                                               0);
            }
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer("Using hybrid SystemMetadata="+hybridSMD);
            }

            // Hook for the extended cache to update the system fields.
            // These added fields can get stored to EMD for searches,
            // and are written to disk below by writeMetadataToOA
            if ( (persistentContext.getCacheId().equals(CacheClientInterface.EXTENDED_CACHE))
                 && (persistentContext.getMetadataObject() != null) ) {
                ExtendedCacheEntry entry = (ExtendedCacheEntry)persistentContext.getMetadataObject();
                hybridSMD.populateMD(entry,false);
            }
            
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            HashMap attributes = new HashMap();

            Exception ex = null;
            try {
                hybridSMD.populateStrings(attributes, false);
                attributes.remove(SystemMetadata.FIELD_NAMESPACE + 
                                  "." + SystemMetadata.FIELD_OBJECTID);

                NameValueXML.createXML(attributes, stream, NameValueXML.TAG_SYSTEM);
            } catch (EMDException e) {
                ex = e;
            } catch (IOException e) {
                ex = e;
            }

            if (ex != null) {
                InternalException newe = new InternalException("Failed to generate" +
                                                               " the metadata XML [" +
                                                               ex.getMessage() +
                                                               "]");
                newe.initCause(ex);
                throw newe;
            }
            writeMetadataToOA(bufferList, stream);
        }

        return(dataSystemMetadata);
    }
    
    /********** closeMetadata **********/

    public SystemMetadata closeMetadata(Context ctx) throws ArchiveException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Performing a closeMetadata");
        }

        MDPersistentContext persistentContext =
            (MDPersistentContext)ctx.getPersistentObject(CONTEXT_PERSISTENT);

        if (!persistentContext.storingMetadata()) {
            return(null);
        }
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Closing the metadata context");
        }
        
        // Close the OA object

        CacheRecord mdObject = persistentContext.getMetadataObject();
        String cacheId = persistentContext.getCacheId();

        SystemMetadata[] mdSystemMetadatas = 
            oaClient.close(ctx,
                           new MDHeader(cacheId).toByteArray());
        
        // Populate the system caches for the MD object
        SysCacheUtils.insertRecord(mdSystemMetadatas);
        
        SystemMetadata mdSystemMetadata = 
            oaClient.compressChunkSM(mdSystemMetadatas);
        
        // Populate the appropriate MD cache

        if (mdObject == null) {
            CacheClientInterface cache = CacheManager.getInstance().getClientInterface(cacheId);
            try {
                mdObject = cache.generateMetadataObject(mdSystemMetadata.getOID());
            } catch (EMDException e) {
                InternalException newe = new InternalException("generateMetadataObject failed ["+
                                                               e.getMessage()+"]");
                newe.initCause(e);
                throw newe;
            }
        }
        
        // Return to the client an indication of whether the metadata
        // is in the cache ready to query.
        boolean queryReady = 
            mdClient.setMetadata(persistentContext.getCacheId(),
                                 mdSystemMetadata.getOID(),
                                 mdObject);
        mdSystemMetadata.setQueryReady(queryReady);

        return(mdSystemMetadata);
    }

    /********** parseMetadata **********/

    public CacheRecord parseMetadata(String cacheId,
                                     InputStream in, 
                                     long mdLength,
                                     Encoding encoding) 
        throws EMDException{
        CacheClientInterface cache = 
            CacheManager.getInstance().getClientInterface(cacheId);

        return cache.parseMetadata(in, mdLength, encoding);

    }

    /********** checkIndexed **********/

    public int checkIndexed(String cacheId, NewObjectIdentifier oid) 
        throws ArchiveException{

        return mdClient.checkIndexed(cacheId, oid);

    }

    /********** openData **********/
    
    public SystemMetadata openData(NewObjectIdentifier oid,
                                   Context ctx) throws ArchiveException {
        NewObjectIdentifier link = resolveLink(oid);
        return oaClient.open(link, ctx);
    }

    /********** openMetadata **********/
    
    public SystemMetadata openMetadata(NewObjectIdentifier oid,
                                       Context ctx) 
        throws ArchiveException {
        if (oid.getObjectType() != NewObjectIdentifier.METADATA_TYPE) {
            throw new EMDException("Cannot give a data oid to openMetadata");
        }

        return oaClient.open(oid, ctx);
    }
    
    /********** read **********/
    
    public int read(ByteBuffer buffer,
                    long offset,
                    int length,
                    Context ctx) 
        throws ArchiveException {
        return((int)oaClient.read(buffer, offset, length, ctx));
    }

    public void delete(NewObjectIdentifier oid,
                       boolean immediate,
                       boolean shred)
        throws ArchiveException {

        LOG.info("Deleting the OID "+oid+" [immediate="+
                 immediate+" - shred="+shred+"]");

        // Remove the metadata from the caches
        String cacheId = null;
        if (oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE) {
            byte[] MDField = oaClient.getMetadataField(oid);
            MDHeader header = null;
            try {
                header = new MDHeader(MDField);
            } catch (IOException e) {
                EMDException newe = new EMDException("Failed to retrieve the cacheId value ["+
                                                     e.getMessage()+"]");
                newe.initCause(e);
                throw newe;
            }
            cacheId = header.getCacheId();
	    
        }


	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/

        // Check to see if the the object is deletable from a
        // compliance perspective, meaning it has an unset or past
        // retention time and no legal holds.
	/*
        if (!isComplianceDeletable(oid)) {
            throw new ArchiveException("Cannot delete an object with an " +
                                       "unexpired retention time or " +
                                       "possession of legal holds");
        }
	*/

        // Remove MD from the extended cache
        if (cacheId != null) {
            mdClient.removeMetadata(oid, cacheId);
        }

        // Call OA delete
        oaClient.delete(oid, immediate, shred ? 1 : 0);

        // Remove MD from the system cache
        SystemMetadata sm = getSystemMetadata(oid);
        SysCacheUtils.updateRecord(oid, sm);
      
        // If this was a MD object that was deleted (which should
        // always be the case) then we proceed to updating the system
        // cache for the data, this will make sure to update dtime
        // correctly for the object and also update all other system
        // metadata for compliance and backup needs.
        if (oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE) { 
            SystemMetadata dsm = getSystemMetadata(sm.getLink()); 
            SysCacheUtils.updateRecord(dsm.getOID(), dsm);
        }

        
        //If the item got repopulated into the external cache while we
        //were deleting it, then remove it again from the extended Cache.  
        if (cacheId != null &&
            mdClient.existsExtCache(cacheId,oid)) {
            LOG.info("deleted oid still queryable;"+
                     " probably a race with PopulateExtCache. "+
                     " Removing oid from extended cache again: "+oid);
            mdClient.removeMetadata(oid, cacheId);
        }
    }
    
    public SystemMetadata getSystemMetadata(NewObjectIdentifier oid) 
        throws ArchiveException {
        SystemMetadata result;
        try {
            result = oaClient.getSystemMetadata(oid, true, false);
        } catch (OAException e) {
            throw new ArchiveException(e);
        }
        return result;
    }

    // Set the retention time on the object
    public void setRetentionTime(NewObjectIdentifier oid, long date)
        throws ArchiveException {

        // Call OA setRetentionTime
        oaClient.setRetentionTime(oid, date);

        // Update the system cache
        updateCacheRetentionTime(oid, date);
    }

    // Set the retention time on the object
    public long getRetentionTime(NewObjectIdentifier oid)
        throws ArchiveException {
        return oaClient.getRetentionTime(oid);
    }

    // Add a legal hold tag
    public void addLegalHold(NewObjectIdentifier oid, String legalHold)
        throws ArchiveException {

        // Call OA addLegalHold
        oaClient.addLegalHold(oid, legalHold);

	// Insert into the system cache
	SysCacheUtils.addLegalHold(oid, legalHold);

        // Update the system cache
        updateExtensionModifiedTime(oid);
    }

    // Remove a legal hold tag
    public void removeLegalHold(NewObjectIdentifier oid, String legalHold)
        throws ArchiveException {

        // Call OA removeLegalHold
        oaClient.removeLegalHold(oid, legalHold);

	// Remove from the system cache
	SysCacheUtils.removeLegalHold(oid, legalHold);

        // Update the system cache
        updateExtensionModifiedTime(oid);
    }

    // Get all legal hold tags for an oid
    public String[] getLegalHolds(NewObjectIdentifier oid)
        throws ArchiveException {
        return oaClient.getLegalHolds(oid);
    }

    // Replace the current set of legal holds for the oid with the
    // given set. Use with caution! This will delete anything not in
    // the given set!
    public void setLegalHolds(NewObjectIdentifier oid, String newHolds[])
        throws ArchiveException {
        oaClient.setLegalHolds(oid, newHolds);

        // Update the system cache
        updateExtensionModifiedTime(oid);
    }

    /**
     *  Check to see whether an object is deletable by checking for a
     *  past retention time and no legal hold tags.
     */
    public boolean isComplianceDeletable(NewObjectIdentifier oid)
        throws ArchiveException {
        return oaClient.isComplianceDeletable(oid);
    }

    // Update the extensionModifiedTime in the syscache
    public void updateExtensionModifiedTime(NewObjectIdentifier oid)
        throws ArchiveException {
        SystemMetadata sm = SysCacheUtils.retrieveRecord(oid);

        // Handle null system metadata in the emulator
        if (sm != null) {
            long extensionModifiedTime = oaClient.getExtensionModifiedTime(oid);
            sm.setExtensionModifiedTime(extensionModifiedTime);
            SysCacheUtils.updateRecord(oid, sm);
        }
    }

    // Update the retention time & extensionModifiedTime in the syscache
    public void updateCacheRetentionTime(NewObjectIdentifier oid, long date)
        throws ArchiveException {
        SystemMetadata sm = SysCacheUtils.retrieveRecord(oid);

        // Handle null system metadata in the emulator
        if (sm != null) {
            sm.setRTime(date);
            long extensionModifiedTime = oaClient.getExtensionModifiedTime(oid);
            sm.setExtensionModifiedTime(extensionModifiedTime);
            SysCacheUtils.updateRecord(oid, sm);
        }
    }
}
