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

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.coordinator.BackingStore;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.coordinator.Disposable;
import com.sun.honeycomb.coordinator.WriteCoordinator;
import com.sun.honeycomb.emd.EMDClient;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FileBackingStore implements BackingStore {

    private static final String FILE_KEY = FileBackingStore.class.getName();

    private static final int WRITE_BUFFER_SIZE = 64 * 1024;
    private static final int READ_BUFFER_SIZE = 64 * 1024;

    private File directory;

    public FileBackingStore(String path) {
        directory = new File(path);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalArgumentException("failed to create directory " +
                                               path);
        }
    }

    public void restoreContextForStore(NewObjectIdentifier oid,
                                       boolean explicitClose,
                                       Context cx)
        throws ArchiveException {
    }

    public void restoreContextForRetrieve(NewObjectIdentifier oid, Context cxt)
        throws ArchiveException {
    }

    public void acquireResourcesForStore(Context ctx)
        throws ArchiveException {
    }

    public void acquireResourcesForRetrieve(Context ctx)
        throws ArchiveException {
    }

    public int getWriteBufferSize() {
        return WRITE_BUFFER_SIZE;
    }

    public int getReadBufferSize() {
        return READ_BUFFER_SIZE;
    }

    public int getReadBufferSize(Context ctx) {
        return READ_BUFFER_SIZE;
    }

    public int getLastReadBufferSize(int length) {
        return length;
    }

    public NewObjectIdentifier createObject(long dataSize,
                                            long metadataSize,
                                            int autoCloseMillis,
                                            long retentionTime,
                                            long expirationTime,
                                            byte shred,
                                            String cacheID,
                                            Context ctx) {
        NewObjectIdentifier dataOID = null, metadataOID = null;
        String dataFileName, metadataFileName, linkFileName;
        File file;
        FileContext fileCtx = new FileContext();

        try {
            if (dataSize != 0) {
                dataOID = new NewObjectIdentifier(0, (byte)0, 0);
                file = new File(directory, dataOID.toHexString() + ".data");
                dataFileName = file.getCanonicalPath();

                if (!file.createNewFile()) {
                    throw new InternalException("failed to create file " +
                                                file);
                }

                fileCtx.dataOID = dataOID;
                fileCtx.dataFile = file;
                fileCtx.dataChannel = new FileOutputStream(file).getChannel();

                if (metadataSize != 0) {
                    metadataOID = new NewObjectIdentifier(0, (byte)0, 0);
                    file = new File(directory, metadataOID.toHexString() + ".metadata");

                    if (!file.createNewFile()) {
                        throw new InternalException("failed to create file " +
                                                    file);
                    }

                    fileCtx.metadataOID = metadataOID;
                    fileCtx.metadataFile = file;
                    fileCtx.metadataChannel = new FileOutputStream(file).getChannel();

                    file = new File(directory, metadataOID.toHexString() + ".data");
                    linkFileName = file.getCanonicalPath();
                    Runtime.getRuntime().exec("ln -s " +
                                              dataFileName +
                                              " " +
                                              linkFileName);
                }
            }
        } catch (IOException e) {
            throw new InternalException("failed to create object", e);
        }

        ctx.registerTransientObject(FILE_KEY, fileCtx);
        return (metadataOID != null) ? metadataOID : dataOID;
    }

    public NewObjectIdentifier createObject(long dataSize,
                                            CacheRecord metadataRecord,
                                            int autoCloseMillis,
                                            long retentionTime,
                                            long expirationTime,
                                            byte shred,
                                            Context ctx) {
        return null;
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

        NewObjectIdentifier metadataOID;
        String metadataFileName, dataFileName, linkFileName;
        File file;
        FileContext fileCtx = new FileContext();

        try {
            file = new File(directory, linkOID.toHexString() + ".data");
            if (!file.exists()) {
                throw new NoSuchObjectException("the data object " +
                                                linkOID.toHexString() +
                                                " does not exist");
            }

            dataFileName = file.getCanonicalPath();

            metadataOID = new NewObjectIdentifier(0, (byte)0, 0);
            file = new File(directory, metadataOID.toHexString() + ".metadata");
            if (!file.createNewFile()) {
                throw new InternalException("failed to create file " +
                                            file);
            }

            fileCtx.metadataOID = metadataOID;
            fileCtx.metadataFile = file;
            fileCtx.metadataChannel = new FileOutputStream(file).getChannel();

            file = new File(directory, metadataOID.toHexString() + ".data");
            linkFileName = file.getCanonicalPath();
            Runtime.getRuntime().exec("ln -s " +
                                      dataFileName +
                                      " " +
                                      linkFileName);

            file = new File(directory, linkOID.toHexString() + ".data");
            dataFileName = file.getCanonicalPath();

            file = new File(directory, metadataOID.toHexString() + ".data");
            linkFileName = file.getCanonicalPath();

            Runtime.getRuntime().exec("ln -s " +
                                      dataFileName +
                                      " " +
                                      linkFileName);
        } catch (IOException e) {
            throw new InternalException("failed to create metadata ", e);
        }

        ctx.registerTransientObject(FILE_KEY, fileCtx);
        return metadataOID;
    }

    public NewObjectIdentifier createMetadata(NewObjectIdentifier linkOID,
                                              CacheRecord metadataRecord,
                                              int autoCloseMillis,
                                              long retentionTime,
                                              long expirationTime,
                                              byte shred,
                                              Context ctx) {
        return null;
    }

    public void writeData(ByteBufferList bufferList, long offset, Context ctx) {
        FileContext fileCtx = (FileContext)ctx.getTransientObject(FILE_KEY);
        FileChannel channel = fileCtx.dataChannel;

        write(channel, bufferList, offset);
    }

    public void writeMetadata(ByteBufferList bufferList, long offset, Context ctx) {
        FileContext fileCtx = (FileContext)ctx.getTransientObject(FILE_KEY);
        FileChannel channel = fileCtx.metadataChannel;

        write(channel, bufferList, offset);
    }

    private void write(FileChannel channel,
                       ByteBufferList bufferList,
                       long offset) {
        ByteBuffer[] buffers = bufferList.getBuffers();
        long written;

        try {
            channel.position(offset);
            while (hasRemaining(buffers) &&
                   (written = channel.write(buffers)) >= 0) {
            }
        } catch (IOException e) {
            throw new InternalException("failed to write file", e);
        }
        
        if (hasRemaining(buffers)) {
            throw new InternalException("failed to completely write buffers");
        }
    }

    private boolean hasRemaining(ByteBuffer[] buffers) {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i].hasRemaining()) {
                return true;
            }
        }

        return false;
    }

    public void commit(Context ctx) throws IllegalArgumentException {
        FileContext fileCtx = (FileContext)ctx.getTransientObject(FILE_KEY);

        flushChannel(fileCtx.dataChannel);
        flushChannel(fileCtx.metadataChannel);
    }

    private void flushChannel(FileChannel channel) {
        if (channel != null) {
            try {
                channel.force(true);
            } catch (IOException e) {
                throw new InternalException("failed to flush file");
            }
        }
    }

    public SystemMetadata closeData(ByteBufferList bufferList,
                                    boolean hasMetadata,
                                    Context ctx)
        throws ArchiveException {
        FileContext fileCtx = (FileContext)ctx.getTransientObject(FILE_KEY);
        SystemMetadata result = null;

        if (fileCtx.dataOID != null) {
            result = closeChannel(fileCtx.dataOID,
                                  fileCtx.dataFile,
                                  fileCtx.dataChannel);
            fileCtx.dataChannel = null;
        } else if (fileCtx.metadataOID != null) {
            File file = new File(directory, fileCtx.metadataOID.toHexString() + ".data");

            try {
                file = file.getCanonicalFile();
                if (file.exists()) {
                    result = new SystemMetadata(fileCtx.metadataOID,
                                                file.length(),
                                                file.lastModified(),
                                                0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                (byte)0,
                                                (short)0,
                                                (byte)0,
                                                0,
                                                0,
                                                null,
                                                null,
                                                null);
                }
            } catch (IOException e) {
                throw new InternalException("failed to create metadata record: " + e);
            }

            if (result != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                HashMap map = new HashMap();
                
                Exception ex = null;
                try {
                    result.populateStrings(map, false);
                    NameValueXML.createXML(map, stream);
                } catch (EMDException e) {
                    ex = e;
                } catch (XMLException e) {
                    ex = e;
                } catch (IOException e) {
                    ex = e;
                }
                
                if (ex != null) {
                    throw new InternalException("failed to create XML stream: " + ex, ex);
                }
                
                ByteBufferPool pool = ByteBufferPool.getInstance();
                ByteBuffer byteBuffer = pool.checkOutBuffer(stream.size());
                
                byteBuffer.put(stream.toByteArray());
                byteBuffer.flip();
                bufferList.appendBuffer(byteBuffer);
                
                pool.checkInBuffer(byteBuffer);
            }
        }

        return result;
    }

    public SystemMetadata closeMetadata(Context ctx) {
        FileContext fileCtx = (FileContext)ctx.getTransientObject(FILE_KEY);
        SystemMetadata result = null;

        if (fileCtx.metadataOID != null) {
            result = closeChannel(fileCtx.metadataOID,
                                  fileCtx.metadataFile,
                                  fileCtx.metadataChannel);
            fileCtx.metadataChannel = null;
        }

        return result;
    }

    private SystemMetadata closeChannel(NewObjectIdentifier oid,
                                        File file,
                                        FileChannel channel) {
        SystemMetadata result = null;

        if (channel != null) {
            try {
                result = new SystemMetadata(oid,
                                            channel.size(),
                                            file.lastModified(),
                                            0,
                                            0,
                                            0,
                                            0,
                                            0,
                                            (byte)0,
                                            (short)0,
                                            (byte)0,
                                            0,
                                            0,
                                            null,
                                            null,
                                            null);
                channel.close();
            } catch (IOException e) {
                throw new InternalException("failed to close file");
            }
        }

        return result;
    }

    public SystemMetadata openData(NewObjectIdentifier oid, Context ctx)
        throws ArchiveException {

        return open(oid, true, ctx);
    }

    public SystemMetadata openMetadata(NewObjectIdentifier oid, Context ctx)
        throws ArchiveException {
        
        return open(oid, false, ctx);
    }

    private SystemMetadata open(NewObjectIdentifier oid, boolean isData, Context ctx)
        throws ArchiveException {

        FileContext fileCtx = new FileContext();
        File file = null;
        FileChannel channel = null;
        long size;

        try {
            String extension = (isData) ? ".data" : ".metadata";
            file = new File(directory, oid.toHexString() + extension);

            channel = new FileInputStream(file).getChannel();
            size = channel.size();
        } catch (FileNotFoundException e) {
            throw new NoSuchObjectException("the object " +
                                            oid.toHexString() +
                                            " does not exist");
        } catch (IOException e) {
            throw new InternalException("failed to open file: " + file);
        }

        if (isData) {
            fileCtx.dataChannel = channel;
        } else {
            fileCtx.metadataChannel = channel;
        }

        ctx.registerTransientObject(FILE_KEY, fileCtx);

        return new SystemMetadata(oid,
                                  size,
                                  file.lastModified(),
                                  0,
                                  0,
                                  0,
                                  0,
                                  0,
                                  (byte)0,
                                  (short)0,
                                  (byte)0,
                                  0,
                                  0,
                                  null,
                                  null,
                                  null);
    }

    public int read(ByteBuffer buffer,
                    long offset,
                    int length,
                    Context ctx) {
        FileContext fileCtx = (FileContext)ctx.getTransientObject(FILE_KEY);
        FileChannel channel = (fileCtx.dataChannel != null)
                                ? fileCtx.dataChannel
                                : fileCtx.metadataChannel;
        int result = 0;
        int read;

        try {
            channel.position(offset);

            while (result < length &&
                   buffer.hasRemaining() &&
                   ( (read = channel.read(buffer)) >= 0)) {
                result += read;
            }
        } catch (IOException e) {
            throw new InternalException("failed to read file");
        }

        return result;
    }

    public void delete(NewObjectIdentifier oid,
                       boolean immediate,
                       boolean shred) {

        new File(directory, oid.toHexString() + ".data").delete();
        new File(directory, oid.toHexString() + ".metadata").delete();
    }

    public EMDClient.EMDQueryResult queryPlus(String cacheId,
					      String query,
					      ArrayList attributes,
					      Cookie cookie,
					      int maxResults) 
        throws ArchiveException {
        return null;
    }

    public EMDClient.EMDQueryResult query(String cacheId,
                                          String query,
                                          Cookie cookie,
                                          int maxResults) 
        throws ArchiveException {
        return null;
    }

    public EMDClient.EMDQueryResult query(String cacheId,
                                          String query,
                                          int maxResults) 
        throws ArchiveException {
        return null;
    }

    public EMDClient.EMDQueryResult query(String cacheId,
                                          String query,
                                          int maxResults,
                                          int timeout)
        throws ArchiveException {
        return null;
    }

    public EMDClient.EMDSelectUniqueResult selectUnique(String cacheId,
                                                        String query,
                                                        String attribute,
                                                        int maxResults,
                                                        int timeout) 
        throws ArchiveException {
        return null;
    }

    public EMDClient.EMDSelectUniqueResult selectUnique(String cacheId,
                                                        String query,
                                                        String attribute,
                                                        int maxResults) 
        throws ArchiveException {
        return null;
    }

    private static class FileContext implements Disposable {
        NewObjectIdentifier dataOID;
        File dataFile;
        FileChannel dataChannel;

        NewObjectIdentifier metadataOID;
        File metadataFile;
        FileChannel metadataChannel;

        public void dispose() {
            if (dataChannel != null) {
                try {
                    dataChannel.close();
                } catch (IOException e) {
                    // do nothing
                }
                dataChannel = null;
                dataFile = null;
            }

            if (metadataChannel != null) {
                try {
                    metadataChannel.close();
                } catch (IOException e) {
                    // do nothing
                }
                metadataChannel = null;
                metadataFile = null;
            }
        }
    }
    public int checkIndexed(String CacheId, NewObjectIdentifier oid)
        throws ArchiveException {
        throw new ArchiveException("not implemented");
    }
}
