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



package com.sun.honeycomb.emd.cache;

import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.common.EMDException;
import java.io.OutputStream;
import java.io.InputStream;

/**
 * The <code>CacheClientInterface</code> interface can be implemented by
 * cache implementations that want to choose the mapId where to store their
 * Metadata.
 * <br><br>
 * In parallel, these caches can also tell the query engine where to look
 * for results.
 */

public interface CacheClientInterface {
    
    /*
     * Well known caches
     */
    
    String SYSTEM_CACHE         = "system";
    String EXTENDED_CACHE       = "extended";
    String FILESYSTEM_CACHE     = "fs";
    
    /*
     * Registration methods
     */

    String getCacheId();

    String getHTMLDescription();

    boolean isRunning();

    /**
     * The <code>generateMetadataStream</code> method builds a data stream
     * that will be written in the metadata OA object.
     *
     * @param The mdObject parameter (a <code>CacheRecord</code>) is the
     * object to be converted
     * @param The output parameter (an <code>OutputStream</code>) is a
     * stream where to write the representation to
     */
    
    void generateMetadataStream(CacheRecord mdObject,
                                OutputStream output)
        throws EMDException;
    
    /**
     * The <code>generateMetadataObject</code> method generates a
     * CacheRecord object based on the content read in the InputStream
     *
     * @param The input parameter (an <code>InputStream</code>) is the
     * stream containing the object representation
     * @return a <code>CacheRecord</code> value
     */

    CacheRecord generateMetadataObject(NewObjectIdentifier oid)
        throws EMDException;

    /**
     * <code>getMetadataLayoutMapId</code> allows caches to specify where
     * to store metadata
     *
     * @param argument  is the same argument as the one given in setMetadata
     * @return the layout mapId.
     */

    int getMetadataLayoutMapId(CacheRecord argument,
                               int nbOfPartitions);
    
    /**
     * <code>layoutMapIdsToQuery</code> gives individual caches an
     * opportunity to give the query engine only a subset of map ids where
     * to look for results.
     *
     * @param the query being run
     * @return the list of maps to query (null for all)
     */
    
    int[] layoutMapIdsToQuery(String query,
                              int nbOfPartitions);
    
    /**
     * <code>sanityCheck</code> is implemented by the caches to do some basic
     * sanity checks at store time
     *
     * @param argument a <code>CacheRecord</code> value that contains the 
     * metadata about to be stored
     * @exception EMDException if the check failed
     */
    
    void sanityCheck(CacheRecord argument)
        throws EMDException;


    public CacheRecord parseMetadata(InputStream in, 
                                     long mdLength, 
                                     Encoding encoding)
        throws EMDException;

}
