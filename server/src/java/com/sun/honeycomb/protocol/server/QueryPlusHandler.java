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

import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.common.QueryMap;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.remote.MDOutputStream;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpOutputStream;
import org.apache.commons.codec.EncoderException;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class QueryPlusHandler extends QueryHandler {

    private static final String emptyString = "* Empty *\n";

    public QueryPlusHandler(final ProtocolBase newService) {
        super(newService);
    }
    private static String selects(ArrayList selectClause){
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < selectClause.size(); i++)
            sb.append(selectClause.get(i) + " " );
        return sb.toString();
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

	int count = 0;
	ResultsStream resultStream = null;
	if (binary){
	    response.setContentType(ProtocolConstants.OCTET_STREAM_TYPE);
        writeMulticellConfig(out);
	    resultStream = new BinaryAdapter(out);
	}
	else{
	    response.setContentType(ProtocolConstants.PLAIN_TEXT_TYPE);
	    response.setCharacterEncoding(ProtocolConstants.ASCII_ENCODING);
        writeMulticellConfig(out);
	    resultStream = new XMLAdapter(out);
	}

        MetadataClient mdc = MetadataClient.getInstance();
        MetadataClient.QueryResult result;

        // Find select clause, boundParameters, etc.
        QueryXML queryXML = (QueryXML) queryObject.get();
        ArrayList keys;
        if (queryXML != null) {
            keys = queryXML.selects;
        } else {
            keys =  parseKeys(request, response, trailer, (cookie==null));
        }

        if (cookie == null){
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.fine("running queryPlus " +
                            " select " + selects(keys) + 
                            " where " + query +
                            " using cache " +
                            cacheID + " (and boundParameters=" +
                            CanonicalEncoding.parametersToString(boundParameters)+
                            ")");
            }

            // Stream results to the adapter
            result = mdc.queryPlus(cacheID, query, keys, null, maxResults, 
                                   0, false, boundParameters, resultStream);
        }
        else{
            // Stream results to the adapter
            result = mdc.queryPlus(cacheID, null, null, cookie, maxResults, 0, false,
                                   null, resultStream);
        }


        // We don't know how many results we will get until we have already 
        // streamed them to the response body, by which time it's too late 
        // to add the Result-Count, so write an EOF, then write the cookie
        if (result.cookie == null || maxResults <= 0) {
            result.cookie = null;
            resultStream.finish(null, result.queryIntegrityTime);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("queryPlus returned last result.");
            }
        }
        else{
            String cookieString = ByteArrays.toHexString(result.cookie.getBytes());
            resultStream.finish(cookieString, result.queryIntegrityTime);

            // This would be better, but it doesn't seem to work...
            // 	    trailer.add(COOKIE_PARAMETER, cookieString);
            // 	    out.setTrailer(trailer);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("queryPlus returned partial result and cookie.");
            }
        }
    }


    private static final String separator = ",";

    private ArrayList parseKeys(final HttpRequest request,
                                final HttpResponse response,
                                final HttpFields trailer,
                                boolean required) throws IOException{
        // Look in header, then URL query string
        String selectClause = request.getField(ProtocolConstants.SELECT_CLAUSE_HEADER);
        if (selectClause == null){
            selectClause = getRequestParameter (ProtocolConstants.SELECT_PARAMETER,
                                                required,
                                                request,
                                                response,
                                                trailer);
        }

        ArrayList al = new ArrayList();
        boolean foundOid = false;
        if (selectClause != null) {
            String curParam = null;
            int start = 0;
            int end = selectClause.indexOf(separator, start);
            while (end != -1){
                curParam = selectClause.substring(start, end);
                if (curParam.equals("system.object_id")) {
                    foundOid = true;
                }
                al.add(curParam);
                start = end + 1;
                end = selectClause.indexOf(separator, start);
            }
            curParam = selectClause.substring(start);
            if (curParam.equals("system.object_id")) {
                foundOid = true;
            }
            al.add(curParam);
        }
        if (foundOid == false) {
            al.add("system.object_id");
        }
        return al;
    }

    private abstract class ResultsStream implements MDOutputStream{
	Object last = null;
    public void clearLastObject() {
        last = null;
    }
	public Object getLastObject(){
	    return last;
	}
	public void sendObject(Object o)
	    throws EMDException{
	    last = o;
	    MDHit hit = (MDHit)o;
	    QueryMap qm = null;

        if (hit.getExtraInfo() instanceof SystemMetadata) 
            qm = ((SystemMetadata)hit.getExtraInfo()).toQueryMap();
        else
            qm =(QueryMap) hit.getExtraInfo();
        
	    try{
            sendObject(qm);
	    }
	    catch (IOException ioe){
            throw new EMDException(ioe);
	    }
	    catch (EncoderException ee){
            throw new EMDException(ee);
	    }
	}

	abstract void sendObject(QueryMap qm) throws IOException, EncoderException;
	abstract void finish(String cookie, long queryTime) throws IOException;
    }


    private class BinaryAdapter extends ResultsStream{
        final DataOutputStream dos;

        BinaryAdapter(OutputStream os){
            dos = new DataOutputStream(os);
        }

        public void sendObject(QueryMap qm) throws IOException, EncoderException {
            qm.serialize(dos);
        }

        void finish(String cookie, long queryTime) throws IOException{
            // signal end of results
            dos.writeInt(-1);
            if (cookie == null){
                dos.writeInt(-1);
            }
            else{
                dos.writeInt(cookie.length());
                dos.writeUTF(cookie);
            }
            // Insert the queryIntegrityTime
            dos.writeLong(queryTime);
            dos.flush();
        } // finish
    } // BinaryAdapter

    private class XMLAdapter extends ResultsStream{
        PrintStream ps;
        boolean firstResult = true;
        XMLAdapter(OutputStream os){
            ps = new PrintStream(os);
        }
        public void sendObject(QueryMap qm) throws IOException, EncoderException{
            if (firstResult){
                // Delay writing anything until we get the first result so that if
                // the query gets an error we can return a non "200 OK" response code
                // (it's too late to do this once we start writing content to the body)
                ps.println("<" + NameValueXML.QUERY_PLUS_RESULTS_TAG + ">");
                firstResult = false;
            }
            ps.print("  <" + NameValueXML.QUERY_RESULT_TAG + ">\n");
            qm.toXML(ps);
            //ps.print("  <attribute name=\"count\" value=\"" + (count++) + "\"/>\n");
            ps.print("  </" + NameValueXML.QUERY_RESULT_TAG + ">\n");
            //NameValueXML.createXML(qm, out, cacheID);
        }
        void finish(String cookie, long queryTime) throws IOException{
            if (firstResult){
                ps.println("<" + NameValueXML.QUERY_PLUS_RESULTS_TAG + ">");
                firstResult = false;
            } else if (cookie != null){
                ps.print("  <" + NameValueXML.COOKIE_TAG + " value=\"");
                ps.print(cookie);
                ps.println("\"/>");
            }

            //Insert the queryIntegrityTime
            ps.println(" <" + NameValueXML.QUERY_INTEGRITY_TIME_TAG +
                       " value=\""+queryTime+"\"/>");

            ps.println("</" + NameValueXML.QUERY_PLUS_RESULTS_TAG + ">");
            ps.flush();
            ps.close();
        }
    }

//     public static void main(String[] argv){
// 	ArrayList keys = parseKeys(argv[0]);
// 	int n = keys.size();
// 	for (int i = 0; i < n; i++)
// 	    System.err.println(keys.get(i));
//     }

}
