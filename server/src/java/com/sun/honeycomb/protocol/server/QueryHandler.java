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
import com.sun.honeycomb.common.ObjectIdentifierList;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.StatsAccumulator;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.emd.MetadataClient;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Arrays;
import java.util.logging.Level;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.common.NameValueXML;

public class QueryHandler extends ProtocolHandler {

    private static final String emptyString = "* Empty *\n";

    public static StatsAccumulator queryStats = new StatsAccumulator();

    public QueryHandler(final ProtocolBase newService) {
        super(newService);
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
	
        // Find select clause, boundParameters, etc.
        QueryXML queryXML = (QueryXML) queryObject.get();
        Object[] boundParameters = null;
        if (queryXML != null)
            boundParameters = queryXML.getParameters();

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.fine("running query (or queryPlus)" +
                        query +
                        " using cache " +
                        cacheID + " and boundParameters=(" +
                        CanonicalEncoding.parametersToString(boundParameters)+
                        ")");
        }

        Cookie cookie;
        try {
            cookie = getRequestCookie(request, response, trailer);
        } catch (IllegalArgumentException e) {
            // already sent error reply in superclass
            return;
        }

        int maxResults;
        try {
            maxResults = getRequestMaxResults(request, response, trailer);
        } catch (IllegalArgumentException e) {
            // already sent error reply in superclass
            return;
        }

        boolean binary = getRequestBinary(request, response, trailer);

        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        try {            
            handleQuery(request,
                        response,
                        out,
                        cacheID,
                        query,
                        cookie,
                        maxResults, 
                        binary,
                        trailer,
                        boundParameters);
        } catch (ArchiveException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "query failed on ArchiveException: " +
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
                           "query failed on InternalException: " +
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

    protected void xmlTextResponse(HttpResponse response){
        response.setContentType(ProtocolConstants.XML_TYPE);
        response.setCharacterEncoding(ProtocolConstants.ASCII_ENCODING);
    }

    protected void handleQuery(final HttpRequest request,
                               final HttpResponse response,
                               final HttpOutputStream out,
                               final String cacheID,
                               final String query,
                               final Cookie cookie,
                               final int maxResults,
                               final boolean binary,
                               final HttpFields trailer,
                               final Object[] boundParameters)
        throws ArchiveException, IOException {

        long t1 = System.currentTimeMillis();
        MetadataClient.QueryResult result;
        result = MetadataClient.getInstance().query(cacheID, query, cookie, maxResults, boundParameters);

        t1 = System.currentTimeMillis() - t1;
        queryStats.add(t1);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("MEAS query __ time " + t1);
        }

        if (result.results == null || result.results.size() == 0 || maxResults <= 0) {
            result.cookie = null;
        }

        if (binary) {
            response.setContentType(ProtocolConstants.OCTET_STREAM_TYPE);
            // Client no longer depends on result count in header.
            writeMulticellConfig(out);
            DataOutputStream dataOut = new DataOutputStream(out);
            result.serializeOids(dataOut);
            // signal EOF because we can't put result-count in header 
            // after writing to response body 
            // (?? used to use serializeOids() to count results)
            dataOut.writeInt(-1);
            if (result.cookie == null){
                dataOut.writeInt(-1);
            } else {
                String cookieStr = ByteArrays.toHexString(result.cookie.getBytes());
                dataOut.writeInt(cookieStr.length());
                dataOut.writeUTF(cookieStr);
            }
            dataOut.writeLong(result.queryIntegrityTime);
            dataOut.flush();
        } else {
            if (result.results != null){
                response.addField (ProtocolConstants.RESULT_COUNT, Integer.toString(result.results.size()));
            }
            xmlTextResponse(response);

            writeMulticellConfig(out);
            PrintStream ps = new PrintStream(out);
            ps.println("<" + NameValueXML.QUERY_RESULTS_TAG + ">");

            if ((result.results != null) && (result.results.size() > 0)) {
                List list = result.results;
                int size = list.size();

                for (int i = 0; i < size; i++) {
                    ps.print("  <" + NameValueXML.QUERY_RESULT_TAG + " name=\"oid\" value=\"");
                    ps.print(((MDHit)list.get(i)).getExternalOid());
                    ps.println("\"/>");
                }
            }

            if (result.cookie != null){
                ps.print("  <" + NameValueXML.COOKIE_TAG + " value=\"");
                ps.print(ByteArrays.toHexString(result.cookie.getBytes()));
                ps.println("\"/>");
            }
            ps.println(" <" + NameValueXML.QUERY_INTEGRITY_TIME_TAG +
                     " value=\""+result.queryIntegrityTime+"\"/>");

            ps.println("</" + NameValueXML.QUERY_RESULTS_TAG + ">");
            ps.flush();
            out.flush();
            out.close();
        }

    } // handleQuery


}
