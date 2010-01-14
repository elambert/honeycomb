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



package com.sun.honeycomb.protocol.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.emd.config.SessionEncoding;


public class StoreBothHandler extends StoreHandler {

    private static final Logger LOG = Logger.getLogger(StoreBothHandler.class.getName());

    public StoreBothHandler(final ProtocolBase newService) {
        super(newService);
        storeBoth = true;
    }

    protected NewObjectIdentifier create(HttpRequest request,
                                         HttpResponse response,
                                         HttpFields trailer,
                                         Coordinator coord,
                                         InputStream in)
        throws ArchiveException, IOException {

        String cacheID = getRequestCacheID(request, response, trailer);
        if (cacheID == null) {
            return null;
        }
        
        // Read the metadata object from the network
        long mdLength = getRequestMDLength(request, response, trailer);
        CacheRecord mdObject = null;
        if (mdLength >= 0) {
            mdObject = coord.parseMetadata(cacheID,
                                           in,
                                           mdLength,
                                           SessionEncoding.getEncoding());
        }

        // Create the object, with this metadata
        long t1 = System.currentTimeMillis();
        NewObjectIdentifier noo = coord.createObject(Coordinator.UNKNOWN_SIZE,
                                  mdObject,
                                  Coordinator.EXPLICIT_CLOSE,
                                  (long)0,
                                  (long)0,
                                  (byte)0);
        t1 = System.currentTimeMillis() - t1;
        storeMDSideStats.add(t1);
        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("MEAS store_md __ time " + t1);

        return noo;
    }



}
