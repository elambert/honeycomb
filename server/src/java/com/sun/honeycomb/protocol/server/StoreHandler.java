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


import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.UnsatisfiableReliabilityException;
import com.sun.honeycomb.common.BandwidthStatsAccumulator;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.StatsAccumulator;
import com.sun.honeycomb.emd.config.SessionEncoding;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.resources.ByteBufferPool;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpInputStream;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpMessage;
import org.mortbay.http.HttpResponse;

public class StoreHandler extends ProtocolHandler {

    protected boolean mdOnly = false;
    protected boolean storeBoth = false;

    /*
     * Store Throughput Stats, Ops/sec, # Ops
     */ 
    public static BandwidthStatsAccumulator storeStats =
                                              new BandwidthStatsAccumulator();
    public static BandwidthStatsAccumulator storeBothStats =
                                              new BandwidthStatsAccumulator();
    public static BandwidthStatsAccumulator storeMDStats =  
                                              new BandwidthStatsAccumulator();
    
    public static StatsAccumulator storeMDSideStats = new StatsAccumulator();

        
    public StoreHandler(final ProtocolBase newService) {
        super(newService);
    }

    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.fine("storing object with unknown size");
        }

        HttpInputStream in = (HttpInputStream)response.getInputStream();
        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();

        SystemMetadata smd = null;

        try {
            response.setContentType(ProtocolConstants.XML_TYPE);
            response.setCharacterEncoding(ProtocolConstants.UTF8_ENCODING);
            smd = handleStore(request, response, trailer, in, out);
        } catch (UnsatisfiableReliabilityException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "store failed on " +
                           "UnsatisfiableReliabilityException: " +
                           e.getMessage(), e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__412_Precondition_Failed,
                      e.getMessage());
            return;
        } catch (NoSuchObjectException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,"store failed on NoSuchObjectException.",e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__404_Not_Found,
                      e.getMessage());
        } catch (ArchiveException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "store failed on ArchiveException: " +
                           e.getMessage(), e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e);
            return;
        } catch (InternalException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, 
                           "store failed on InternalException: " +
                           e.getMessage(), e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__500_Internal_Server_Error,
                      e);
            return;
        } catch (IllegalArgumentException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.FINE,
                           "store failed on IllegalArgumentException: " +
                           e.getMessage(),
                           e);
            }       
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e);
            return;
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.INFO,
                           "Store failed server-side IOException: " +
                           e.getMessage(),
                           e);
            }       

            try {
                sendError(response,
                          trailer,
                          HttpResponse.__400_Bad_Request,
                          e);
            } catch (IOException ex) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO,
                               "failed to send response: " +
                               ex.getMessage(),
                               e);
                }
            }

            return;
        }

        if (smd != null) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("stored object with id " + smd.getOID());
            }
            // Normally we use the identity encoding to encode
            // the system metadata.  But for Legacy 1.0.1 clients,
            // we have to use the legacy encoding, so that
            // object ids will be correctly shortened.
            Encoding encoding = SessionEncoding.getEncoding();
            if (! encoding.isLegacyEncoding())
                encoding = Encoding.identityEncoding;

            try {
                NameValueXML.createXML(makeSMDMap(smd), 
                                       out, 
                                       encoding,
                                       NameValueXML.TAG_SYSTEM,
                                       false);
            } catch (IOException e) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, 
                               "store failed on XML generation: " +
                               e.getMessage(), e);
                }
                sendError(response,
                          trailer,
                          HttpResponse.__500_Internal_Server_Error,
                          e.getMessage());
            }
        }
    }


    private static Map makeSMDMap(SystemMetadata smd){
        HashMap map = new HashMap();
        //
        // Transform the OID to its externl form before we return
        // it to the client. 
        //
        map.put(ProtocolConstants.IDENTIFIER_TAG, 
                smd.getOID().toExternalObjectID());
        if (smd.getLink() != null) {
            map.put(ProtocolConstants.LINK_TAG, 
                smd.getLink().toExternalObjectID());
        }

        map.put(ProtocolConstants.SIZE_TAG, new Long(smd.getSize()));
        map.put(ProtocolConstants.CTIME_TAG, new Long(smd.getCTime()));

        byte[] digest = smd.getContentHash();
        if (digest != null) {
            map.put(ProtocolConstants.HASH_ALGORITHM_TAG, smd.getHashAlgorithmString());
            map.put(ProtocolConstants.DATA_CONTENT_DIGEST_TAG, digest);
        }

        //on-the-wire format doesn't handle boolean or int, so send a long
        long queryReadyValue = smd.isQueryReady() ? 1 : 0;
        map.put(ProtocolConstants.QUERY_READY_TAG, new Long(queryReadyValue));
        return map;
    }


    protected SystemMetadata handleStore(final HttpRequest request,
                                         final HttpResponse response,
                                         final HttpFields trailer,
                                         InputStream in,
                                         final HttpOutputStream out)
        throws ArchiveException, IOException {
        
        boolean failsafe = false;
        String chunkSizeHeaderStr = request.getField(ProtocolConstants.CHUNKSIZE_HEADER);
        long chunkSizeBytes = -1;
        if(chunkSizeHeaderStr != null) {
            chunkSizeBytes = Long.parseLong(chunkSizeHeaderStr);
            if(chunkSizeBytes > 0) {
                failsafe = true;
            }
        }
        
        if(!failsafe) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, 
        		   "store is not fail-safe.");
            }
        } else {
            if (LOGGER.isLoggable(Level.INFO)) {
        	LOGGER.log(Level.INFO, 
        		   "store is fail-safe with chunk size " + 
        		   chunkSizeBytes);
            }
        }
        
        long t1 = System.currentTimeMillis();
        Coordinator coord = Coordinator.getInstance();

        if (getBooleanRequestParameter(ProtocolConstants.ZIP_PARAMETER, false, request, response, trailer) ){
            in = new ZipInputStream(in);
        }
        NewObjectIdentifier oid = create(request,
                                         response,
                                         trailer,
                                         coord,
                                         in);

        if (oid == null) {
            return null;
        }
        ByteBufferPool bufferPool = ByteBufferPool.getInstance();
        ByteBuffer buffer = null;
        int bufferSize = coord.getWriteBufferSize();
        byte[] bytes = new byte[bufferSize];
        int read = 0;
        long bytesWritten = 0;
        long totalBytesWritten = 0;
        long read_time = 0;
        long chunksWritten = 0;
        long chunksCommitted = 0;

        while (read >= 0) {
            buffer = bufferPool.checkOutBuffer(bufferSize);
            int bufferRead = 0;
            
            try {
        	// read until we get some data or reach the end of the
        	// byte array
        	
            long t2 = System.currentTimeMillis();
            while (bufferRead < bufferSize &&
                   (read = in.read(bytes, bufferRead, 
                                   bufferSize - bufferRead)) >= 0) {
                bufferRead += read;
            }
            read_time += System.currentTimeMillis() - t2;

            if (bufferRead > 0) {
        	    
                // copy the bytes to the direct buffer
                buffer.put(bytes, 0, bufferRead);
                totalBytesWritten += bufferRead;
                buffer.flip();

                write(coord, oid, buffer, bytesWritten);

                bytesWritten += bufferRead;

                if(failsafe) {

                    chunksWritten = totalBytesWritten / chunkSizeBytes;


                    System.out.flush();
                    if(chunksWritten > chunksCommitted) {
                        coord.commit(oid, true); // XXX TODO: xplicit close?
                        for(long c = chunksCommitted+1; 
                            c<=chunksWritten; 
                            c++) {
                            String s = Long.toHexString(c) + "\n";
                            out.write(s.getBytes());
                        }
                        chunksCommitted = chunksWritten;
                    }
                }
            }
            } finally {
                bufferPool.checkInBuffer(buffer);
            }
        }

        SystemMetadata smd = coord.close(oid);
        
        //
        // Multicell configuration needs to be written after 
        // we proceed with the store because of the error path-- 
        // see sendError()
        //
        writeMulticellConfig(out);

        if (smd != null) {
            long store_time = System.currentTimeMillis() - t1;
            store_time -= read_time;

            if (mdOnly) {
                storeMDStats.add(smd.getSize(), store_time);
            }
            else if (storeBoth) {
                storeBothStats.add(smd.getSize(), store_time);
            } else {
                storeStats.add(smd.getSize(), store_time);
            }

            if (LOGGER.isLoggable(Level.INFO)) {
                if (mdOnly) {
                    LOGGER.info("MEAS addmd __ size " + smd.getSize() +
                                " time " + store_time);
                } else if (storeBoth) {
                    LOGGER.info("MEAS store_b __ size " + smd.getSize() +
                                " time " + store_time);
                } else {
                    LOGGER.info("MEAS store __ size " + smd.getSize() +
                                " time " + store_time);
                }
            }
        }

        return smd;
    }
  

    protected NewObjectIdentifier create(HttpRequest request,
                                         HttpResponse response,
                                         HttpFields trailer,
                                         Coordinator coord,
                                         InputStream in)
        throws ArchiveException, IOException {
        return coord.createObject(Coordinator.UNKNOWN_SIZE,
                                  (long)0,
                                  Coordinator.EXPLICIT_CLOSE,
                                  (long)0,
                                  (long)0,
                                  (byte)0,
                                  null);
    }

    protected void write(Coordinator coord,
                         NewObjectIdentifier oid,
                         ByteBuffer buffer,
                         long offset)
        throws ArchiveException {
        coord.writeData(oid, buffer, offset, true);
    }
}
