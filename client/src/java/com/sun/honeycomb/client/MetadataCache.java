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



package com.sun.honeycomb.client;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.CacheConfiguration;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Each metadata cache in a Honeycomb cluster has a corresponding class
 * in the client that can interpret that cache's metadata streams to
 * create <code>MetadataRecord</code> instances and emit metadata streams
 * from <code>MetadataRecord</code>s. <code>MetadataCache</code>s are
 * also responsible for creating <code>CacheConfiguration</code> instances.
 * <p>
 * It should rarely necessary to interact directly with instances of
 * <code>MetadataCache</code> in application code. They are used primarily
 * by <code>ObjectArchive</code> and its subclasses.
 * 
 * @see <a href="caches/DirectoryCache.html">DirectoryCache</a>
 * @see <a href="caches/NameValueCache.html">NameValueCache</a>
 * @see <a href="caches/SystemCache.html">SystemCache</a>
 */
public interface MetadataCache {

    /**
     * Returns the unique identifier string for this cache.
     */
    public String getCacheID();

    /**
     * Returns a newly-created <code>MetadataRecord</code> initialized
     * with the contents of the input stream. The resulting object will
     * be an instance of the <code>MetadataRecord</code> class
     * corresponding to this cache.
     *
     * @throws ArchiveException if the read fails due to an error in
     * the cluster
     * @throws IOException if the read fails due to a communication
     * problem
     */
    public MetadataRecord readRecord(InputStream in)
        throws ArchiveException, IOException;

    /**
     * Writes a data stream interpretable by the Honeycomb cluster to
     * the output stream.
     * 
     * @throws ArchiveException if the write fails due to an error in
     * the cluster
     * @throws IOException if the write fails due to a communication
     * problem
     */
    public void writeRecord(MetadataRecord record, OutputStream out)
        throws ArchiveException, IOException;

    /**
     * Returns a newly-created <code>CacheConfiguration</code> instance
     * initialized with the contents of the input stream. The resulting
     * object will be an instance of the <code>CacheConfiguration</code>
     * class corresponding to this cache.
     * 
     * @throws ArchiveException if the read fails due to an error in
     * the cluster
     * @throws IOException if the read fails due to a communication
     * problem
     */
    public CacheConfiguration readConfiguration(InputStream in)
        throws ArchiveException, IOException;
}
