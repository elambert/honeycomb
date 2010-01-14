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



package com.sun.honeycomb.oa.bulk.stream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Map;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.DeletedObjectException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.datadoctor.TaskFragUtils;
import com.sun.honeycomb.oa.BloomFilter;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAContext;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.FooterExtension;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.emd.SysCacheUtils;
import com.sun.honeycomb.emd.common.EMDException;

public class OAObjectSerializer extends ObjectSerializer {
    
    public static final String SYSTEM_METADATA_KEY = "restoreSystemMD"; 

    protected static Logger LOG = Logger.getLogger(OAObjectSerializer.class.getName());
 
    private ByteBufferPool _bufferPool = null;
    private NewObjectIdentifier _oid = null;
    private SystemMetadata _sm = null; 

    public OAObjectSerializer(Session session) {
        super(session);
        _bufferPool = ByteBufferPool.getInstance();
    }

    public void init(Object obj) throws SerializationException {
        // read the system metadata record
        _oid = (NewObjectIdentifier) obj;
        _sm = readSystemMetadata(_oid);
    }
   
    public CallbackObject serialize(StreamWriter writer) 
           throws SerializationException { 
        
        Context readCtx = null;
        ByteBuffer buffer = null;
        
        try { 
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("OID being serialized: " + _oid);
            
            boolean deleted = false;
            readCtx = new Context();
            try {
                _oaclient.open(_oid, readCtx);
            } catch (DeletedObjectException e) {
                // deleted object.
                deleted = true;
            }

            /*
             * Get the system record from the system cache and not using OA logic
             * which does not actually read back all the system records and figure
             * out which one is that has the latest correct values for refCount,
             * maxRefCount,etc.
             */
            try {
                _sm = SysCacheUtils.retrieveRecord(_oid);
            } catch(EMDException e) {
                throw new SerializationException(
                             "Error retrievieving system record for " + _oid,e);
            }
            
            // write system record
            writeSystemMetadata(_sm, writer);
            // Write the footer extension. If one does not currently
            // exist for this object, we will get back an empty one
            // with a last modified time of 0. Only write out the
            // FooterExtension byte stream if the last modified time
            // is greater than 0. Oterwise, just write a length of 0.
//             FooterExtension fe = _oaclient.getFooterExtension(_oid);
//             if (fe.getLastModified() > 0) {
//                 int feSize = fe.size();
//                 ByteBuffer feBuffer = _bufferPool.checkOutBuffer(feSize);
//                 try{
//                     feBuffer.put(fe.asByteBuffer());
//                     feBuffer.flip();
//                     if (LOG.isLoggable(Level.FINEST))
//                         LOG.finest("Writing chunk size " + feSize);
//                     writer.writeIntAsHex(feSize);
//                     writer.writeSeparator();
//                     writer.write(feBuffer, feSize);
//                 } finally { 
//                     if (feBuffer != null)
//                         _bufferPool.checkInBuffer(feBuffer);
//                 }
//             } else {
//                 writer.writeIntAsHex(0);
//             }

            writer.writeIntAsHex(0);
            writer.writeSeparator();
            
            // write out size to the stream in first place.
            long lengthToRead = (deleted ? 0 :_sm.getSize());
            long totalRead = 0;
            long read = 0;
            
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("Writing chunk size " + lengthToRead);
            writer.writeLongAsHex(lengthToRead);
            writer.writeSeparator();

            if (lengthToRead != 0) {
	            int readSize = _oaclient.getReadBufferSize(readCtx);
	            buffer = _bufferPool.checkOutBuffer(readSize);
	            long write_time = 0;
	            // clear for safety
	            buffer.clear();
	            
	            while (totalRead < lengthToRead) {
	                
	                if ((lengthToRead - totalRead) < readSize) {
	                    readSize = _oaclient.getLastReadBufferSize((int)(lengthToRead - totalRead));
                        buffer.limit(readSize);
                        if (LOG.isLoggable(Level.FINEST))
                            LOG.finest("last read size = " + readSize);
	                }
	              
	                read = _oaclient.read(buffer, totalRead, readSize, readCtx);
	                buffer.flip();
	                
	                if (LOG.isLoggable(Level.FINEST))
	                    LOG.finest("read " + read + " bytes at offset " + totalRead);
	                
	                // accumulate the write time to subtract
	                long t2 = System.currentTimeMillis();
	                writer.write(buffer, (int)read);
	                write_time += System.currentTimeMillis() - t2;
	                   
	                buffer.clear();
	                totalRead += read;
	            }
            }
	            
            if (totalRead != lengthToRead) 
                throw new SerializationException("Coulnd't read back the right amounts of bytes from disk for object " + _oid);

            CallbackObject obj = new CallbackObject(_sm,CallbackObject.OBJECT_CALLBACK);
            return obj;
        } catch (IOException e) {
            throw new SerializationException(e);
        } catch (ArchiveException e) {
            throw new SerializationException(e);
        } finally { 
            if (buffer != null)
                _bufferPool.checkInBuffer(buffer);
            
            if (readCtx != null)
                readCtx.dispose();
        }
    }

    public CallbackObject deserialize(StreamReader reader, Map headers)
        throws SerializationException {
        Context writeCtx = new Context();
        ByteBuffer buffer = null;
        FooterExtension fe = null;
        try {
            String oidStr = (String)headers.get(Constants.OID_HEADER);
            NewObjectIdentifier oid = NewObjectIdentifier.fromHexString(oidStr);

            SystemMetadata diskMD = null;
            if (_session.checkDiskForObject()) {
                try {
                    diskMD = _oaclient.getSystemMetadata(oid, true, false);
                } catch (ArchiveException e) {
                    // Can't find valid object so we won't rely on the
                    // disk but instead restore from the current stream.
                } catch (OAException e) {
                    // Can't find valid object so we won't rely on the
                    // disk but instead restore from the current stream.
                }
                // if the object is not found then no other subsequent
                // objects will be found on disk either...
                if (diskMD == null)
                    _session.setCheckDiskForObject(false);
            }

            _sm = readSystemMetadata(reader);

            // Read the Footer Extension
            int feSize = reader.readLineAsIntHex();
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("Read chunk size " + feSize);
            if (feSize > 0) {
                reader.readSeparator();
                ByteBuffer feBuffer = _bufferPool.checkOutBuffer(feSize);
                try{
                    feBuffer.clear();
                    reader.read(feBuffer, feSize);
                    feBuffer.rewind();
                    fe = new FooterExtension();
                    fe.read(feBuffer);
                } finally {
                    if (feBuffer != null)
                        _bufferPool.checkInBuffer(feBuffer);
                }
            }

            reader.readSeparator();
            long size = reader.readLineAsLongHex();
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("Read chunk size " + size);
            reader.readSeparator();
            boolean isMetadata
                = (oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE);
            SystemMetadata sys_sm = null;
            try {
                sys_sm = SysCacheUtils.retrieveRecord(oid);
            } catch(EMDException ignore) {
                LOG.info("failed to retrieve oid " + oid +
                         " from systeme cache");
            }

            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("Restoring " + (isMetadata ? "metadata":"data") +
                           " " + oid + " size: " + size);

            // by registering the SYSTEM_METADATA_KEY object OAClient
            // will then update the footers accordingly
            boolean deleted = false;
            if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) {
                deleted = (_sm.getDTime() != -1) ? true : false;
                writeCtx.registerTransientObject(SYSTEM_METADATA_KEY, _sm);
            } else {
                deleted = ((sys_sm != null) && (sys_sm.getDTime() != -1)) ?
                    true : false;
                sys_sm.setChecksumAlg (_sm.getChecksumAlg());
                sys_sm.setMetadataField (_sm.getMetadataField());
                writeCtx.registerTransientObject(SYSTEM_METADATA_KEY, sys_sm);
            }

            // If the object is on disk don't recreate it
            if (!_session.checkDiskForObject()) {
                long ctime =
                    (sys_sm != null) ? sys_sm.getCTime() : _sm.getCTime();
                long dtime =
                    (sys_sm != null) ? sys_sm.getDTime() : _sm.getDTime();
                long rtime =
                    (sys_sm != null) ? sys_sm.getRTime() : _sm.getRTime();
                long etime =
                    (sys_sm != null) ? sys_sm.getETime() : _sm.getETime();

                _oaclient.create(_sm.getSize(),
                                 _sm.getLink(),
                                 _sm.getOID(),
                                 _sm.getLayoutMapId(),
                                 isMetadata,
                                 isMetadata, // 1.0 all metadata are refroots
                                 Coordinator.EXPLICIT_CLOSE,
                                 ctime,
                                 dtime,
                                 rtime,
                                 etime,
                                 (byte)0,
                                 _sm.getChecksumAlg(),
                                 OAClient.NOT_RECOVERY,
                                 writeCtx,
                                 null);
            }
            // write out size to the stream in first place.
            long lengthToRead = size;
            long totalRead = 0;
            int writeSize = _oaclient.getWriteBufferSize();
            if (size < writeSize)
                writeSize = (int) size;

            if (LOG.isLoggable(Level.FINEST)) {
                LOG.finest ("writeSize="+writeSize);
            }
            while (totalRead < size){
                buffer = _bufferPool.checkOutBuffer(writeSize);
                try{
                    buffer.clear();
                    reader.read(buffer, writeSize);
                    if (LOG.isLoggable(Level.FINEST)) {
                        LOG.finest ("post read buffer position="+buffer.position());
                    }
                    // deleted object don't write out any contents but make sure to
                    // read the data from the stream.
                    if (!deleted && !_session.checkDiskForObject()){
                        buffer.flip();
                        _oaclient.write(buffer, totalRead, writeCtx);
                        if (LOG.isLoggable(Level.FINEST)) {
                            LOG.finest ("post write buffer position="+buffer.position());
                        }
                    }
                    else{
                        buffer.clear();
                        if (LOG.isLoggable(Level.FINEST)) {
                            LOG.finest ("deleted object, skipping write");
                        }
                    }
                    totalRead += writeSize;
                    if (size - totalRead < writeSize){
                        writeSize = (int) (size - totalRead);
                        if (LOG.isLoggable(Level.FINEST)) {
                            LOG.finest ("last buffer. expecting " + writeSize + " bytes");
                        }
                    }
                }
                finally{
                    // Is this check necessary?
                    if (buffer != null)
                        _bufferPool.checkInBuffer(buffer);
                }
            }

            if (deleted && !_session.checkDiskForObject()) {
                OAContext oactx = (OAContext) writeCtx.getPersistentObject(OAContext.CTXTAG);
                if (_sm.getSize() > oactx.getChunkSize()) {
                    // Multi-Chunk deleted object we need to make sure
                    // to recreate all the chunks as deleted stubs
                    SystemMetadata tmp =
                        (SystemMetadata) writeCtx.getTransientObject(SYSTEM_METADATA_KEY);
                    long safe = tmp.getSize();
                    tmp.setSize(OAClient.MORE_CHUNKS);
                    writeCtx.registerTransientObject(SYSTEM_METADATA_KEY, tmp);
                    _oaclient.createMultiChunkStubs(writeCtx, _sm.getSize());
                    tmp.setSize(safe);
                    writeCtx.registerTransientObject(SYSTEM_METADATA_KEY, tmp);
                }
            }
            if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) {
                //
                // No entries in system cache and object is not on disk,
                // (First pass of the first session)
                //
                if (sys_sm == null && !_session.checkDiskForObject()) {

                    //
                    // Insert all objects in system cache.
                    // (restored flag is already set, but for paranoia,
                    // reset it)
                    _sm.setRestored(true);
                    SysCacheUtils.insertRecord(_sm);
                    //
                    // For metadata, we also need to increment/decrement the
                    // ref count on the linkoid. That operation may fail
                    // if the referee (linkoid) is not there, and in that case
                    // we still need to patch the system cache
                    //
                    if (isMetadata) {
                        try {
                            if (!deleted) {
                                _oaclient.incRefCount(_sm.getLink());
                            } else {
                                _oaclient.decRefFromReferee(_sm.getOID());
                            }
                            SystemMetadata newSM = _oaclient.getSystemMetadata(
                                                                               _sm.getLink(), true, false);
                            SysCacheUtils.insertRecord(newSM);
                        } catch (Exception e
                                 /* ArchiveException,
                                    OAException,
                                    NoSuchObjectException */) {
                            //
                            // Data has not been restored yet; extract md
                            // from system cache, increment/decrement its
                            // ref count and insert it back into the
                            // the system cache
                            //
                            SystemMetadata newSM =
                                SysCacheUtils.retrieveRecord(_sm.getLink());
                            int refCount = newSM.getRefcount();
                            int maxRefCount = newSM.getMaxRefcount();
                            if (!deleted) {
                                //
                                // Note that here we may do the wrong thing
                                // and increment too much; we don't have bloom
                                // filter to tell us if this MD has already
                                // been counted. That's part of the things
                                // that should be fixed on DD when such task
                                // exists.
                                //
                                refCount++;
                                maxRefCount++;
                            } else {
                                BloomFilter filter =
                                    new BloomFilter(_sm.getDeletedRefs());
                                if (!filter.hasKey(_sm.getOID())) {
                                    refCount--;
                                    maxRefCount--;
                                }
                            }
                            newSM.setRefcount(refCount);
                            newSM.setMaxRefcount(maxRefCount);
                            SysCacheUtils.insertRecord(newSM);
                        }
                    }
                } else if (sys_sm == null && _session.checkDiskForObject()) {
                    //
                    // (Possible additional pass of the first session, if we
                    //  crash later and have to restart from first session)
                    //
                    diskMD.setRestored(true);
                    SysCacheUtils.insertRecord(diskMD);
                } else if (sys_sm != null) {
                    //
                    // Those objects match what comes from the stream,
                    // nothing to do.
                    // (First pass for the objects which were already in the
                    //  system cache at the time we snapshoted the cache)
                    //
                    sys_sm.setRestored(true);
                    SysCacheUtils.insertRecord(sys_sm);
                }
            } else {
                //
                // Updates the system cache of this object to restored...
                // (Non first session)
                //
                if (sys_sm != null) {
                    sys_sm.setRestored(true);
                    SysCacheUtils.insertRecord(sys_sm);
                } else {
                    LOG.severe("OID  " + oid +
                               "  does not exists in system cache, can't be restored");
                    throw new SerializationException("OID " + oid +
                                                     " is missing from system cache, aborting");
                }
            }
            if (!_session.checkDiskForObject()) {
                if (fe != null) {
                    _oaclient.putFooterExtension(oid, fe);
                }
                _oaclient.close(writeCtx,
                                new byte[FragmentFooter.METADATA_FIELD_LENGTH]);
            }

            CallbackObject obj =
                new CallbackObject(_sm,CallbackObject.OBJECT_CALLBACK);
            return obj;
        } catch (IOException e) {
            throw new SerializationException(e);
        } catch (ArchiveException e) {
            throw new SerializationException(e);
        } finally {
            if (writeCtx != null)
                writeCtx.dispose();
        }
    }



    public long getMetadataLength() {
        // oa object is already complete in the data section of the object
        // no need for metadata 
        return 0;
    }

    public long getNDataBlocks() {
        return 1;
    }
    
    public String getOID() {
        return _oid.toHexString();
    }
   
    public long getContentLength() throws SerializationException, EMDException {
        long result = 0;
       
        if (_sm == null)  {
            /*
             * By returning SKIP_BLOCK we are in fact skipping this object. So 
             * lets make sure to spam the external log server about this 
             * incident.
             *
             */
            String str = BundleAccess.getInstance().getBundle().
                                      getString("err.oa.bulk.skippedObject");
            Object [] args = {_oid.toHexString()};
            LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));
            
            return SKIP_BLOCK;
        }
        
        result += smToString(_sm).length() + Constants.HEADER_TERMINATOR_LENGTH;
        result += getMetadataLength() + Constants.HEADER_TERMINATOR_LENGTH;
        result += (_sm.getDTime() != -1 ? 0 :_sm.getSize()) + Constants.HEADER_TERMINATOR_LENGTH; 
        return result;
    }
}
