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
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.Encoding;
import java.nio.ByteBuffer;
import java.io.InputStream;

public interface WriteCoordinator {

    public NewObjectIdentifier createObject(long dataSize,
                                            long metadataSize,
                                            int autoCloseMillis,
                                            long retentionTime,
                                            long expirationTime,
                                            byte shred,
                                            String cacheID)
        throws ArchiveException;

    public NewObjectIdentifier createObject(long dataSize,
                                            CacheRecord metadataRecord,
                                            int autoCloseMillis,
                                            long retentionTime,
                                            long expirationTime,
                                            byte shred)
        throws ArchiveException;

    public NewObjectIdentifier createMetadata(NewObjectIdentifier linkOID,
                                              long metadataSize,
                                              int autoCloseMillis,
                                              long retentionTime,
                                              long expirationTime,
                                              byte shred,
                                              String cacheID)
        throws ArchiveException;

    public NewObjectIdentifier createMetadata(NewObjectIdentifier linkOID,
                                              CacheRecord metadataRecord,
                                              int autoCloseMillis,
                                              long retentionTime,
                                              long expirationTime,
                                              byte shred)
        throws ArchiveException;

    public void writeData(NewObjectIdentifier oid,
                          ByteBuffer buffer,
                          long offset,
                          boolean explicitClose)
        throws ArchiveException;

    public void writeMetadata(NewObjectIdentifier oid,
                              ByteBuffer buffer,
                              long offset,
                              boolean explicitClose) 
        throws ArchiveException;

    public CacheRecord parseMetadata(String cacheId,
                                     InputStream in,
                                     long mdLength,
                                     Encoding encoding)
        throws ArchiveException;

    public void commit(NewObjectIdentifier oid, boolean explicitClose)
        throws ArchiveException;

    public long getCommittedDataSize(NewObjectIdentifier oid, 
                                     boolean explicitSize) 
        throws ArchiveException;

    public long getCommittedMetadataSize(NewObjectIdentifier oid,
                                         boolean explicitSize)
        throws ArchiveException;

    public SystemMetadata close(NewObjectIdentifier oid) 
        throws ArchiveException;

    public void abortWrite(NewObjectIdentifier oid)
        throws ArchiveException;

    public void delete(NewObjectIdentifier oid,
		       boolean immediate,
		       boolean shred)
        throws ArchiveException;

    public void setRetentionTime(NewObjectIdentifier oid, long retentionTime)
        throws ArchiveException;

    public int checkIndexed(String CacheId, NewObjectIdentifier oid)
        throws ArchiveException;
}
