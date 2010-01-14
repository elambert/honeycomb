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
import java.util.logging.Logger;
import java.util.Map;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.DeletedObjectException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.emd.SysCacheUtils;


/*
 * Override the server version to remove the event logic for use in the emulator.
 */
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
           throws SerializationException, IOException, OAException, ArchiveException{ 
        Context readCtx = null;
        ByteBuffer buffer = null;
        
        try {            
            boolean deleted = false;
            readCtx = new Context();
            try {
                _sm = _oaclient.open(_oid, readCtx);
            } catch (DeletedObjectException e) {
                // deleted object still needs to be backed up
                _sm = _oaclient.getSystemMetadata(_oid, true, false);
                deleted = true;
            }
            
            // write system record
            writeSystemMetadata(_sm, writer);
            
            // write out size to the stream in first place.
            long lengthToRead = (deleted ? 0 :_sm.getSize());
            long totalRead = 0;
            long read = 0;
            
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
                        LOG.finest("last read size = " + readSize);
	                }
	              
	                read = _oaclient.read(buffer, totalRead, readSize, readCtx);
	                buffer.flip();
	                
                    LOG.finest("read " + read + " bytes at offset " + totalRead);
	                
	                // accumulate the write time to subtract
	                long t2 = System.currentTimeMillis();
	                writer.write(buffer);
	                write_time += System.currentTimeMillis() - t2;
	                   
	                buffer.clear();
	                totalRead += read;
	            }
            }
	            
            if (totalRead != lengthToRead) 
                throw new SerializationException("Coulnd't read back the right amounts of bytes from disk for object " + _oid +
                                                 " expected " + lengthToRead + ", found " + totalRead);

            CallbackObject obj = new CallbackObject(_sm, CallbackObject.OBJECT_CALLBACK);
            return obj;
        } finally { 
            if (buffer != null)
                _bufferPool.checkInBuffer(buffer);
            
            if (readCtx != null)
                readCtx.dispose();
        }
    }

    public CallbackObject deserialize(StreamReader reader, Map headers) 
           throws SerializationException, ArchiveException, IOException{
        Context writeCtx = new Context();
        ByteBuffer buffer = null;
        try {        
            String oidStr = (String)headers.get(Constants.OID_HEADER);
            NewObjectIdentifier oid = NewObjectIdentifier.fromHexString(oidStr);

            _sm = readSystemMetadata(reader);
            long size = reader.readLineAsLongHex();
            
            reader.readSeparator();
            boolean deleted = false;
            boolean isMetadata = (oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE);
            
            LOG.finest("Restoring " + (isMetadata ? "metadata":"data") + 
                       " " + oid + " size: " + size);
         
            // by registering the SYSTEM_METADATA_KEY object OAClient
            // will then update the footers accordingly 
            if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) { 
                writeCtx.registerTransientObject(SYSTEM_METADATA_KEY, _sm);
            } else {
                writeCtx.registerTransientObject(SYSTEM_METADATA_KEY, null);
            }
            _oaclient.create(_sm.getSize(),
                             _sm.getLink(),
                             _sm.getOID(),
                             _sm.getLayoutMapId(),
                             isMetadata,
                             isMetadata, // 1.0 all metadata are refroots
                             Coordinator.EXPLICIT_CLOSE,
                             0,
                             0,
                             0, // retention time
                             0, // expiration time
                             (byte)0,
                             _sm.getChecksumAlg(),
                             OAClient.NOT_RECOVERY,
                             writeCtx,
                             null);
            
            // write out size to the stream in first place.
            long lengthToRead = size;
            long totalRead = 0;
            long read = 1;     
            int writeSize = _oaclient.getWriteBufferSize();
         
            if (writeSize > lengthToRead)
                writeSize = (int)lengthToRead;
            
            buffer = _bufferPool.checkOutBuffer(writeSize);
            
            // clear for safety
            buffer.clear();
           
            while ((read > 0) && (totalRead < lengthToRead)) {
                if ((lengthToRead - totalRead) < writeSize) {
                    writeSize = (int)(lengthToRead - totalRead);
                    buffer.limit(writeSize);
                    LOG.finest("last write size = " + writeSize);
                }
                read = reader.read(buffer);
                buffer.flip();

                LOG.finest("read " + read + " bytes at offset " + totalRead);
                _oaclient.write(buffer, totalRead, writeCtx);
                totalRead += read;
            }
                       
            if (totalRead != lengthToRead) 
                throw new SerializationException("Coulnd't read back the right amounts of bytes from disk for object " + oid +
                                                 " expected " + lengthToRead + 
                                                 " but got " + totalRead + " bytes.");
            
            if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) { 
                // lockout and replay session 
                if (isMetadata) {
                    // Its a metadata object that got added after we 
                    // updated the system cache so we must "fix" the
                    // data footer and system cache entry for this data
                    if (!deleted) { 
                        // incRefCount for link (data object)
                       
                        try {
                            _oaclient.incRefCount(_sm.getLink()); 
                            
                            // read the new SystemMetadata for the data 
                            // object and put that in the system cache
                            try {
                                SystemMetadata newSM = 
                                    _oaclient.getSystemMetadata(_sm.getLink(), 
                                                                true, 
                                                                false);
                                SysCacheUtils.insertRecord(newSM);
                            } catch (OAException e) {
                                throw new SerializationException(e);
                            } 
                        } catch (ArchiveException e) { 
                            // this can fail if the data object isn't
                            // here yet make sure to update the refCount
                            // in the system cache
                            //SystemMetadata newSM = SysCacheUtils.retrieveRecord(_sm.getLink());
                        }
                    }
                    // if deleted ignore we don't care if we error
                    // on the plus side of refCounts 
                } 
                
                LOG.finest("Replaying object: " + _sm.getOID());
                SysCacheUtils.insertRecord(_sm);
            } 
            
            _oaclient.close(writeCtx, new byte[FragmentFooter.METADATA_FIELD_LENGTH]);
            
            CallbackObject obj = new CallbackObject(_sm,CallbackObject.OBJECT_CALLBACK);
            return obj;
        } finally { 
            if (buffer != null)
                _bufferPool.checkInBuffer(buffer);
            
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
   
    public long getContentLength() throws SerializationException {
        long result = 0;
        result += getMetadataLength() + Constants.HEADER_TERMINATOR_LENGTH;
        result += (_sm.getDTime() != -1 ? 0 :_sm.getSize()) + Constants.HEADER_TERMINATOR_LENGTH; 
        return result;
    }
}

