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

import java.util.logging.Logger;
import com.sun.honeycomb.emd.common.EMDException;
import java.io.OutputStream;
import java.io.InputStream;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.NewObjectIdentifier;
import java.util.logging.Level;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.Encoding;
import java.io.IOException;

public class CacheManager {

    private static final Logger LOG = Logger.getLogger(CacheManager.class.getName());
    private static CacheManager singleton = null;

    public static synchronized CacheManager getInstance() {
        if (singleton != null) {
            return(singleton);
        }
        
        singleton = new CacheManager();
        return(singleton);
    }

    private CacheClientInterface clientInterface;

    private CacheManager() {
        clientInterface = new ClientInterface();
    }

    public CacheClientInterface getClientInterface(String name) 
        throws EMDException {
        if (name.equals(CacheClientInterface.EXTENDED_CACHE)) {
            return(clientInterface);
        }
        return(null);
    }

    private static class ClientInterface
        implements CacheClientInterface {
        private ClientInterface() {
        }

        public String getCacheId() {
            return("fake");
        }

        public String getHTMLDescription() {
            return("fake");
        }

        public boolean isRunning() {
            return true;
        }

        public void generateMetadataStream(CacheRecord mdObject,
                                           OutputStream output)
            throws EMDException {
            ExtendedCacheEntry attributes = (ExtendedCacheEntry)mdObject;
        
            if (attributes == null) {
                // This an empty map to generate XML with an empty content
                attributes = new ExtendedCacheEntry();
            }
            
            try {
                NameValueXML.createXML(attributes, output);
            } catch (IOException e) {
                EMDException newe = new EMDException("Couldn't generate extended cache metadata");
                newe.initCause(e);
                throw newe;
            }
        }

        public CacheRecord generateMetadataObject(NewObjectIdentifier oid)
            throws EMDException {
            throw new RuntimeException("Not implemented");
        }

        public int getMetadataLayoutMapId(CacheRecord argument,
                                          int nbOfPartitions) {
            return(-1);
        }

        public int[] layoutMapIdsToQuery(String query,
                                         int nbOfPartitions) {
            throw new RuntimeException("Not implemented");
        }

        public CacheRecord parseMetadata(InputStream in, 
                                         long mdLength, 
                                         Encoding encoder)
            throws EMDException {
            throw new RuntimeException("Not implemented");
        }

        public void sanityCheck(CacheRecord argument)
            throws EMDException {
        }
    }
}
