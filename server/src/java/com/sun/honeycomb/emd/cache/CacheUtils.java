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

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.StringTerminatedInputStream;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferListInputStream;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.config.SessionEncoding;

public class CacheUtils {
    
    private static final Logger LOG = Logger.getLogger(CacheUtils.class.getName());
    private static final String XML_TERMINATION = "</relationalMD>";
    
    private CacheUtils() {
    }

    private static Map retrieveMetadata(NewObjectIdentifier mdOID,
                                       int length) 
        throws ArchiveException, EMDException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Retrieving the MD content for oid "+mdOID);
        }

        Coordinator coordinator = Coordinator.getInstance();
        ByteBufferList buffers = new ByteBufferList();

        Exception ex = null;
        Map emd = null, systemMD = null;
        
        try {
            coordinator.readMetadata(mdOID, buffers, 0, length, true);
            ByteBufferListInputStream stream = new ByteBufferListInputStream(buffers);

            try {
                emd = NameValueXML.parseXML(
                     new StringTerminatedInputStream(stream, XML_TERMINATION),
                     new SessionEncoding(Encoding.IDENTITY));
                systemMD = NameValueXML.parseXML(
                     new StringTerminatedInputStream(stream, XML_TERMINATION));
            } catch (XMLException e) {
                ex = e;
            }
        } finally {
            ByteBufferPool.getInstance().checkInBufferList(buffers);
        }

        if (ex != null) {
            EMDException newe = new EMDException("Failed to parse" +
                                                 " the metadata XML [" +
                                                 ex.getMessage() +
                                                 "]");
            newe.initCause(ex);
            throw newe;
        }

        emd.putAll(systemMD);
        buffers.clear();
        return(emd);
    }

    public static Map retrieveMetadata(OAClient oaClient,
                                       NewObjectIdentifier mdOID) 
        throws ArchiveException, EMDException {

        if (oaClient == null) {
            oaClient = OAClient.getInstance();
        }

        // Retrieve the file length
        Context ctx = new Context();
        SystemMetadata systemMD = null;
        try {
            systemMD = oaClient.open(mdOID, ctx);
        } finally {
            ctx.dispose();
        }
        return(retrieveMetadata(mdOID, (int)systemMD.getSize()));
    }


    public static Map parseMetadata(InputStream in, long mdLength, Encoding encoding)
        throws EMDException {
        InputStream inputStream;

        if (mdLength == Coordinator.UNKNOWN_SIZE) {
            // The whole input stream is metadata.
            inputStream = in;
        } else if (mdLength > 0) {
            // Just the first mdLength bytes of the stream are metadata
            //  FIXME:  create a new InputStream that just
            //  reads mdLength bytes and then returns EOF.
            byte [] bytes = readFromStream(in, mdLength);
            inputStream = new ByteArrayInputStream(bytes);
        } else if (mdLength == 0) {
            // Special case for null MD in StoreBoth operation
            return null;
        } else {
            throw new EMDException("metadata claims illegal length");
        }

        Map emd = null;
        try {
            emd = NameValueXML.parseXML(inputStream, encoding);
        } catch (XMLException ex) {
            EMDException newe = new EMDException("Failed to parse" +
                                                 " the metadata XML [" +
                                                 ex.getMessage() +
                                                 "]");
            newe.initCause(ex);
            throw newe;
        }
        return emd;
    }
    public static byte[] readFromStream(InputStream in, long mdLength) 
        throws EMDException{

        assert(mdLength >= 0 && mdLength <= Integer.MAX_VALUE);

        byte[] bytes = new byte[(int)mdLength];

        int totalRead = 0;
        int lastRead = 0;

        try {
            if (mdLength > 0) {
                do {
                    lastRead = in.read(bytes, totalRead, bytes.length-totalRead);
                    if (lastRead >= 0) {
                        totalRead += lastRead;
                    }
                } while ( (lastRead >= 0) && (totalRead < bytes.length) );
                
                if (lastRead < 0) {
                    throw new IOException("Failed to read the MD object");
                }
            }
        } catch (IOException e) {
            throw new EMDException("failed to read from stream", e);
        }
        return bytes;
    } // readFromStream
}
