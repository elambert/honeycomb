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
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.StatsAccumulator;
import com.sun.honeycomb.emd.MetadataClient;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

public class GetValuesHandler extends ProtocolHandler {

    public static StatsAccumulator seluniqStats = new StatsAccumulator();

    public GetValuesHandler(final ProtocolBase newService) {
        super(newService);
    }


    private String getKey(HttpRequest request,
                          HttpResponse response,
                          HttpFields trailer)
        throws IOException {

        // Client version 1.1 and later transmit the query as an XML
        // document, perhaps with bound parameters and encoded select
        // fields. If the header told us to expect that, parse the
        // body of the request.
        QueryXML queryXML = (QueryXML) queryObject.get();

        if (queryXML != null){
            return queryXML.key;
        }
        else{
            return getRequestParameter(ProtocolConstants.KEY_PARAMETER,
                                       true,
                                       request,
                                       response,
                                       trailer);
        }
    }


    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

        String cacheID = getRequestCacheID(request, response, trailer);
        if (cacheID == null) {
            return;
        }

        String query = getRequestQuery(request, response, trailer);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.fine("running query " + query);
        }

        String key = null;
        Cookie cookie = null;
        try {
            cookie = getRequestCookie(request, response, trailer);
        } catch (IllegalArgumentException e) {
            // already sent error reply in superclass
            return;
        }
        if (cookie == null)
            key = getKey(request, response, trailer);

        int maxResults;
        try {
            maxResults = getRequestMaxResults(request, response, trailer);
        } catch (IllegalArgumentException e) {
            // already sent error reply in superclass
            return;
        }

        boolean binary = getRequestBinary(request, response, trailer);

        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        writeMulticellConfig(out);
        try {            
            handleGetValues(request,
                            response,
                            out,
                            cacheID,
                            query,
                            key,
                            cookie,
                            maxResults,
                            binary);
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
                      e);
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
                      e);
        } catch (IllegalArgumentException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "store failed on IllegalArgumentException: " +
                           e.getMessage(),
                           e);
            }       
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e);
            return;
        }
    }

    protected void handleGetValues(final HttpRequest request,
                                   final HttpResponse response,
                                   final OutputStream out,
                                   final String cacheID,
                                   final String query,
                                   final String key,
                                   final Cookie cookie,
                                   final int maxResults,
                                   final boolean binary)
        throws ArchiveException, IOException {

        long t1 = System.currentTimeMillis();

        MetadataClient.SelectUniqueResult result;

        result = MetadataClient.getInstance().selectUnique(cacheID,
							   query,
							   key,
							   cookie,
							   maxResults,
							   0, // ?
							   false); //?

        t1 = System.currentTimeMillis() - t1;
        seluniqStats.add(t1);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("MEAS seluniq __ time " + t1);
        }

        if (result.results == null || result.results.size() == 0) {
            result.cookie = null;
        }

        if (binary) {
            response.setContentType(ProtocolConstants.OCTET_STREAM_TYPE);

            DataOutputStream dataOut = new DataOutputStream(out);
            result.serialize(dataOut);
            dataOut.flush();
        } else { 
            response.setContentType(ProtocolConstants.PLAIN_TEXT_TYPE);
            response.setCharacterEncoding(ProtocolConstants.ASCII_ENCODING);

            if (result.cookie != null) {
                String hexString = ByteArrays.toHexString(result.cookie.getBytes());
                out.write(COOKIE_BYTES);
                out.write(hexString.getBytes(ProtocolConstants.ASCII_ENCODING));
                out.write(NEWLINE_BYTES);
            }

            if (result.results != null) {
                List list = result.results.getList();
                int size = list.size();

                for (int i = 0; i < size; i++) {
                    if (i > 0) {
                        out.write(NEWLINE_BYTES);
                    }

                    out.write(((String)list.get(i)).getBytes(ProtocolConstants.ASCII_ENCODING));
                }
            }
        }
    }
}
