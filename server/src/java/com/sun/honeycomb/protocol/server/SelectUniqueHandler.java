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

import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectIdentifierList;
import com.sun.honeycomb.common.ObjectLostException;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.StringList;
import com.sun.honeycomb.common.StatsAccumulator;
import com.sun.honeycomb.emd.cache.CacheInterface;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.util.logging.Level;
import java.util.List;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

public class SelectUniqueHandler extends ProtocolHandler {

    public static StatsAccumulator selUniqStats = new StatsAccumulator();

    private static final String QUERY_PARAMETER = "query";
    private static final String ATTRIBUTE_PARAMETER = "attr";
    private static final String MAXRESULTS_PARAMETER = "maxresults";
    private static final String BINARY_PARAMETER = "binary";
    
    public SelectUniqueHandler(final ProtocolBase service) {
        super(service);
    }
	
    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {
        int maxResults = -1;
        
        boolean binary = false;
        String queryString = request.getParameter(QUERY_PARAMETER);
        String attrString = request.getParameter(ATTRIBUTE_PARAMETER);
        String maxResultsString = request.getParameter(MAXRESULTS_PARAMETER);
        String binaryString = request.getParameter(BINARY_PARAMETER);
        
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.fine("running query " + queryString);
        }
        
        if (queryString.equals("null")) {
            queryString = null;
        }

        if (attrString == null) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("attribute was null");
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      "attribute was null");
            return;
        }
        
        if (maxResultsString != null) {
            try {
                maxResults = Integer.parseInt(maxResultsString);
            } catch (NumberFormatException nfe) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("invald maxResults: " + maxResultsString);
                }
                sendError(response,
                          trailer,
                          HttpResponse.__400_Bad_Request,
                          "invalid maxResults: " + maxResultsString);
                return;
            }
        }
        
        binary = ((binaryString != null) &&
                  (binaryString.equals("1") ||
                   binaryString.equalsIgnoreCase("true")));
        
        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        writeMulticellConfig(out);
        
        if (binary) {
            response.setContentType(ProtocolConstants.OCTET_STREAM_TYPE);
        } else { 
            response.setContentType(ProtocolConstants.PLAIN_TEXT_TYPE);
            response.setCharacterEncoding(ProtocolConstants.ASCII_ENCODING);
        }
        
        try {            
            handleSelectUnique(request, response, out, queryString, attrString,
                               maxResults,  binary);
            //response.setContentLength(responseData.length); optional?
        } catch (ArchiveException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "select unique failed on ArchiveException: " +
                           e.getMessage(),
                           e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e.getMessage());
        } catch (InternalException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "select unique failed on InternalException: " +
                           e.getMessage(),
                           e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__500_Internal_Server_Error,
                      e.getMessage());
        }
    }
    
    protected void handleSelectUnique(final HttpRequest request,
                                      final HttpResponse response,
                                      final OutputStream out,
                                      final String query,
                                      final String attr,
                                      final int maxResults,
                                      final boolean binary)
        throws ArchiveException, IOException {
        
        long t1 = System.currentTimeMillis();

        StringList results = MetadataClient.getInstance().selectUnique(CacheInterface.EXTENDED_CACHE,
                                                                       query, 
                                                                       attr,
                                                                       maxResults).results;
        selUniqStats.add(System.currentTimeMillis() - t1);
        
        if (binary) {
            results.serialize(new DataOutputStream(out));
        } else {
            out.write(results.toString().getBytes(ProtocolConstants.ASCII_ENCODING));
        }
    }
}
