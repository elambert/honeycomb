
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

import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.ThreadPropertyContainer;
import com.sun.honeycomb.common.LogEscapeFormatter;
import com.sun.honeycomb.emd.config.SessionEncoding;
import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.multicell.lib.MultiCellLibException;
import com.sun.honeycomb.multicell.lib.MultiCellLibBase.MultiCellVersionHeader;

import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.InetAddress;
import java.net.UnknownHostException;


import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.handler.AbstractHttpHandler;
import com.ccg.net.ethernet.EthernetAddress;

public abstract class ProtocolHandler extends AbstractHttpHandler {

    protected static final byte[] COOKIE_BYTES;
    protected static final byte[] NEWLINE_BYTES;

    protected static final Logger LOGGER =
        Logger.getLogger(ProtocolHandler.class.getName());
    private static String HOST_ID;

    // Are these safe, or is this object supposed to be reentrant?
    private boolean sendStackTrace = false;

    // FIXME: this class is over using threadLocal. We should be able
    // to use one threadLocal (one context).
    // When a request arrives, we inspect the header to derive the
    // client version, then populate a ThreadLocal with an encoding
    // that corresponds to what that client expects for
    // metadata. Downstream services (such as the query engine)
    // discover and use this encoding
    static ThreadLocal queryObject = new ThreadLocal();

    // When a request arrives, we inspect the header to derive the 
    // "host" specifier that the client supplied and save this string
    // in a ThreadLocal.   If we are running on the emulator,
    // this value is later used in MulticellLib to create a dummy
    // multicell config.
    // FIXME: this class is over using threadLocal. We should be able
    // to use one threadLocal (one context).
    static ThreadLocal requestedIP = new ThreadLocal();
    
    // FIXME - only one ThreadLocal
    private static ThreadLocal mcellConfig = new ThreadLocal();
    
    static {
        try {
            COOKIE_BYTES = "cookie:".getBytes(ProtocolConstants.ASCII_ENCODING);
            NEWLINE_BYTES = "\n".getBytes(ProtocolConstants.ASCII_ENCODING);
        } 
        catch (UnsupportedEncodingException e) {
            // this should never happen
            throw new IllegalStateException("unsupported encoding: " +
                                            ProtocolConstants.ASCII_ENCODING);
        }
        try {
            InetAddress me = InetAddress.getLocalHost();
            HOST_ID = me.getHostName() + " " + me.getHostAddress();
            //org.doomdark.uuid.EthernetAddress eaddr = org.doomdark.uuid.NativeInterfaces.getPrimaryInterface();
            //HOST_ID += " " + EthernetAddress.getPrimaryAdapter();
        }
        catch (UnsatisfiedLinkError ule) {
            LOGGER.info("Could not read ethernet address: " + ule);
            HOST_ID += " " + ule;
        }
        catch (UnknownHostException unhe) {
            // this should never happen
            throw new IllegalStateException("Could not find local host: " +
                                            unhe);
        }
        catch (Throwable ule) {
            LOGGER.info("Could not read ethernet address: " + ule);
            HOST_ID += " " + ule;
        }
    }

    public ProtocolHandler(final ProtocolBase newService) {
        super();
        setName(getClass().getName());
    }

    protected NewObjectIdentifier getRequestIdentifier(HttpRequest request,
                                                       HttpResponse response,
                                                       final HttpFields trailer)
        throws IOException, IllegalArgumentException {

        String identifierString = getRequestParameter(ProtocolConstants.ID_PARAMETER,
                                                      true,
                                                      request,
                                                      response,
                                                      trailer);
        NewObjectIdentifier result = null;
        if (identifierString != null) {
            try {
                // We don't keep the session honeycomb-version around
                //  but we can trigger off the session encoding
                SessionEncoding encoding = 
                    SessionEncoding.getSessionEncoding();
                result = encoding.decodeObjectIdentifierFromString(identifierString);
                LOGGER.info("Incoming OID="+identifierString+
                            "\n ==>\tInternal OID="+result.toHexString());
            } catch (IllegalArgumentException e) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("invalid identifer: " + identifierString);
                }
                sendError(response,
                          trailer,
                          HttpResponse.__400_Bad_Request,
                          "invalid identifier: " + identifierString);
                throw e;
            }
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("got identifier from request: " + identifierString + 
                        "--> oid="+result.toString());
        }

        return result;
    }

    private void checkMCVersion(HttpRequest request, HttpResponse response)
        throws IOException {

        mcellConfig.set(null);

        String clntVersion = request.getField(ProtocolConstants.MULTICELL_CONFIG_VERSION_HEADER);
        if (clntVersion == null)
            return;

        String svcVersion = "0.0";
        try {
            svcVersion = MultiCellLib.getInstance().getVersion();
        } catch (MultiCellLibException mce) {
            LOGGER.log(Level.SEVERE, "Can't retrieve the current MC version", 
		       mce);
            return;
        }

        MultiCellVersionHeader clntHeader = 
          new MultiCellVersionHeader(clntVersion);
        MultiCellVersionHeader svcHeader = 
          new MultiCellVersionHeader(svcVersion);
        if (clntHeader.compareTo(svcHeader) >= 0) {
            return;
        }

        LOGGER.info("client runs " + clntHeader + 
          ", will be updated to " + svcHeader);

        byte[] config = null;
        try {
            config = MultiCellLib.getInstance().getXMLConfig();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "CAN'T GET MCELL CONFIG: ",e);
            return;
        }
        if (config == null) {
            LOGGER.log(Level.SEVERE, "MCELL CONFIG IS NULL");
            return;
        }
        response.addField(ProtocolConstants.HONEYCOMB_MULTICELL_HEADER, 
                      Integer.toString(config.length));
        mcellConfig.set(config);
    }

    protected void writeMulticellConfig(OutputStream out) 
        throws IOException {

        byte[] config = (byte[]) mcellConfig.get();
        if (config == null)
            return;
        out.write(config);
        mcellConfig.set(null);
    }

    protected String getRequestCacheID(HttpRequest request,
                                       HttpResponse response,
                                       HttpFields trailer)
        throws IOException {

        return getRequestParameter(ProtocolConstants.CACHE_PARAMETER,
                                   true,
                                   request,
                                   response,
                                   trailer);
    }

    protected long getRequestMDLength(HttpRequest request,
                                      HttpResponse response,
                                      HttpFields trailer)
        throws IOException {
        
        String value = getRequestParameter(ProtocolConstants.MDLENGTH_PARAMETER,
                                           true,
                                           request,
                                           response,
                                           trailer);
        return(Long.parseLong(value));
    }

    private static String readBody(HttpRequest request)
        throws IOException {

        StringBuffer sb = new StringBuffer();
        InputStream is = request.getInputStream();
        if (! (is instanceof BufferedInputStream))
            is = new BufferedInputStream(is);
        int i = is.read();
        while (i != -1){
            sb.append ((char)i);
            i = is.read();
        }
        return sb.toString();
    }

    protected Cookie getRequestCookie(HttpRequest request,
                                      HttpResponse response,
                                      HttpFields trailer)
        throws IOException, IllegalArgumentException {

        Cookie result = null;
        String cookieString = request.getField(ProtocolConstants.COOKIE_HEADER);

        String bodyContent = request.getField(ProtocolConstants.QUERY_BODY_CONTENT);
        if (cookieString == null){
            if (ProtocolConstants.COOKIE_IN_BODY.equals(bodyContent)){
                cookieString = readBody(request);
            }
            else {
                cookieString = request.getParameter(ProtocolConstants.COOKIE_PARAMETER);
            }
        }
        if (cookieString != null) {
            try {
                byte[] bytes = ByteArrays.toByteArray(cookieString);
                result = new EMDCookie(bytes);
            } catch (IllegalArgumentException e) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("invald cookie: " + cookieString);
                }
                sendError(response,
                          trailer,
                          HttpResponse.__400_Bad_Request,
                          "invalid cookie: " + cookieString);
                throw e;
            }
        }

        return result;
    }

    protected int getRequestMaxResults(HttpRequest request,
                                       HttpResponse response,
                                       HttpFields trailer)
        throws IOException, IllegalArgumentException {

        String intString = getRequestParameter(ProtocolConstants.MAX_RESULTS_PARAMETER,
                                               false,
                                               request,
                                               response,
                                               trailer);
        int result = -1;
        if (intString != null) {
            try {
                result = Integer.parseInt(intString);
            } catch (NumberFormatException nfe) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("invald maxResults: " + intString);
                }
                sendError(response,
                          trailer,
                          HttpResponse.__400_Bad_Request,
                          "invalid maxResults: " + intString);
                throw new IllegalArgumentException("invalid maxResults: " +
                                                   intString);
            }
        }

        return result;
    }

    private static boolean fieldIsTrue (HttpRequest request, String parameter){
        String booleanString = request.getField(parameter);
        return isTrue (booleanString);
    }

    private static boolean parameterIsTrue (HttpRequest request, String parameter){
        String booleanString = request.getParameter(parameter);
        return isTrue (booleanString);
    }

    private static boolean isTrue (String booleanString){
        return (booleanString != null &&
                (booleanString.equals("1") ||
                 booleanString.equalsIgnoreCase("yes") ||
                 booleanString.equalsIgnoreCase("true")));
    }

    protected boolean getRequestBinary(HttpRequest request,
                                       HttpResponse response,
                                       HttpFields trailer)
        throws IOException {
        return parameterIsTrue(request, ProtocolConstants.BINARY_PARAMETER);
    }

    protected boolean getBooleanRequestParameter(String parameter,
						boolean required,
						HttpRequest request,
						HttpResponse response,
						HttpFields trailer)
    throws IOException{
	
        String booleanString = getRequestParameter(parameter,
						   required,
						   request,
						   response,
						   trailer);
        return ((booleanString != null) &&
                (booleanString.equals("1") ||
                (booleanString.equals("yes")) ||
                 booleanString.equalsIgnoreCase("true")));
    }

    protected String getRequestParameter(String parameter,
                                         boolean required,
                                         HttpRequest request,
                                         HttpResponse response,
                                         HttpFields trailer)
        throws IOException {

        String result = request.getParameter(parameter);
        if (result == null && required) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("request failed due to null " + parameter);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      new Exception("missing " + parameter));
        }

        return result;
    }


    String getRequestQuery(HttpRequest request,
                                     HttpResponse response,
                                     HttpFields trailer)
        throws IOException {

        // Client version 1.1 and later transmit the query as an XML
        // document, perhaps with bound parameters and encoded select
        // fields. If the header told us to expect that, parse the
        // body of the request.
        QueryXML queryXML = (QueryXML) queryObject.get();

        if (queryXML != null)
            return queryXML.query;

        // Accept query string in header
        String result = request.getField(ProtocolConstants.WHERE_CLAUSE_HEADER);
        if (result != null)
            return result;
        String bodyContent = request.getField(ProtocolConstants.QUERY_BODY_CONTENT);
        if (ProtocolConstants.QUERY_CLAUSE_IN_BODY.equals(bodyContent)){
            return readBody(request);
        }
        else{
            result = getRequestParameter(ProtocolConstants.QUERY_PARAMETER,
                                         false,
                                         request,
                                         response,
                                         trailer);

            if (result != null && result.equalsIgnoreCase("null")) {
                result = null;
            }

            return result;
        }
    }


    private static float parseHttpVersion(String httpVersion){
        return Float.parseFloat(httpVersion.substring("HTTP/".length()));
    }

    private static float requiredHttpVersion = parseHttpVersion(ProtocolConstants.REQUIRED_HTTP_VERSION);

    boolean validateHttpVersion(String clientVersion){
        return parseHttpVersion(clientVersion) >= requiredHttpVersion;
    }


    /** Hook for testing: allwow client to specify servicing delay for this request */
    private static final void handleTestHeaders(HttpRequest request, HttpResponse response){

        String delay = request.getField(ProtocolConstants.TEST_DELAY_SECS);

        if (delay != null){
            try{
                int millis = (int) (Float.parseFloat(delay) * 1000);
                Thread.currentThread().sleep(millis);
                response.addField(ProtocolConstants.TEST_DELAY_SECS, Integer.toString(millis));
            }
            catch (InterruptedException ie){
                response.addField(ProtocolConstants.TEST_DELAY_SECS, "Interrupted during sleep '" + delay +"'");
            }
            catch (NumberFormatException nfe){
                response.addField(ProtocolConstants.TEST_DELAY_SECS, "Could not parse '" + delay +"'");
            }
        }
    }

    private final void setEncoding(HttpRequest request){
        String major = request.getField(ProtocolConstants.HONEYCOMB_VERSION_HEADER);
        String minor = request.getField(ProtocolConstants.HONEYCOMB_MINOR_VERSION_HEADER);
        float version = -1;
        if (major != null){
            version = Float.parseFloat(major);
        }
        if (minor != null){
            version += Float.parseFloat(minor) / 10;
        }
        if (version >= 1.1){
            // This may be overriden by parser
            SessionEncoding.setSessionEncoding(Encoding.BASE_64);
        } else {
            SessionEncoding.setSessionEncoding(Encoding.LEGACY_1_0_1);
        }
    }


    public void handle(final String pathInContext,
                       final String pathParams,
                       final HttpRequest request,
                       final HttpResponse response)
        throws IOException {

        handleTestHeaders(request, response);

        String job = request.getParameter(ProtocolConstants.LOG_TAG_PARAMETER);
        long startTime = System.currentTimeMillis();
        ThreadPropertyContainer.setLogTag(job);
        setEncoding(request);

        //Host is always present in HTTP 1.1 headers
        setSessionRequestedIP(request.getField("Host"));
    
        String bodyContent = request.getField(ProtocolConstants.QUERY_BODY_CONTENT);
        if (ProtocolConstants.XML_IN_BODY.equals(bodyContent))
            queryObject.set(new QueryXML(request.getInputStream()));

        sendStackTrace = ProtocolService.reportStackTrace ||
            fieldIsTrue(request, ProtocolConstants.SEND_STACK_TRACE);
            
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Request received from " + request.getRemoteHost() + ":" + ((java.net.Socket) request.getHttpConnection().getConnection()).getPort());
            if (ThreadPropertyContainer.getLogTag() != null)
                LOGGER.fine(ThreadPropertyContainer.getLogTag() + " received request with headers:");
            else 
                LOGGER.fine("[NOLOGTAG] received request with headers:");
            
            Enumeration names = request.getFieldNames();
            while (names.hasMoreElements()) {
                String name = (String)names.nextElement();
                LOGGER.fine("    " + name + ": " + request.getField(name));
            }
        }

        // pgates: The following order of operations is important. Jetty
        // has a bug where the following things are true:
        //
        // 1. You can only ask for the trailer before data is written to
        //    the output stream.
        // 2. If you ask for the trailer one is created for you.
        // 3. If a trailer is created and no fields are set then a NPE is
        //    thrown when the output stream is closed.
        //
        // Together these things mean that if you think you'll ever want
        // to write to the trailer then you have to ask for it in advance,
        // but if you wind up not using it you're hosed.
        //
        // We work around this by asking for the trailer and stashing it,
        // then setting the trailer to null in the underlying stream, and
        // finally re-setting it in the stream if we wind up needing it
        // (see sendError below and handleRetrieve in XMLRetrieveHandler).
        response.setAcceptTrailer(true);
        HttpFields trailer = response.getTrailer();

        response.addField(ProtocolConstants.HONEYCOMB_VERSION_HEADER, ProtocolConstants.HONEYCOMB_VERSION);
        response.addField(ProtocolConstants.HONEYCOMB_MINOR_VERSION_HEADER, ProtocolConstants.HONEYCOMB_MINOR_VERSION);
        response.addField(ProtocolConstants.HONEYCOMB_NODE_HEADER, HOST_ID);
        response.addField("ProtocolService dispatched to ", this.getClass().getName());
        response.addField("Client", request.getHttpConnection().getConnection().toString());

        String clientVersion = request.getVersion();
        if (! validateHttpVersion(clientVersion)){
            sendError(response,
                      trailer,
                      HttpResponse.__505_HTTP_Version_Not_Supported,
                      "'" + clientVersion + "' not supported. Version " + 
                       ProtocolConstants.REQUIRED_HTTP_VERSION + " required");
            return;
        }


        if (!ProtocolService.isReady()) {
            sendError(response,
                      trailer,
                      HttpResponse.__503_Service_Unavailable,
                      "server request handling disabled");
            return;
        }

        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        out.setChunking();
        out.setTrailer(null);

        checkMCVersion(request, response);

        try {
            handle(pathInContext,
                   pathParams,
                   request,
                   response,
                   trailer);
        } catch (Throwable t) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                while (t.getCause() != null){
                    t = t.getCause();
                }
                LOGGER.log(Level.SEVERE,
                           "[" + 
                           job + " " +
                           getName() +
                           "] " +
                           "uncaught Throwable while handling request",
                           t);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__500_Internal_Server_Error,
                      t);

            if (t instanceof Error) {
                throw (Error)t;
            }
        } finally {
            SessionEncoding.clearEncoding();
            queryObject.set(null);
            out.close();
            request.setHandled(true);
            setSessionRequestedIP(null);
        }

        // 	System.err.println(new Date() + " Finished " + job + " " +
        // 			    ((System.currentTimeMillis() - startTime) / 1000.0));

        if (LOGGER.isLoggable(Level.FINE)) {
            Runtime runtime = Runtime.getRuntime();
            if (ThreadPropertyContainer.getLogTag() != null)
            	LOGGER.fine("[" +
            		getName() + " " + ThreadPropertyContainer.getLogTag() +
                        "] done handling request - memory statistics:");
            else
            	LOGGER.fine("[" +
            		getName() + " NOLOGTAG] done handling request - memory statistics:");
            LOGGER.fine(" free memory: " + runtime.freeMemory());
            LOGGER.fine("  max memory: " + runtime.maxMemory());
            LOGGER.fine("total memory: " + runtime.totalMemory());
        }
    }

    protected abstract void handle(final String pathInContext,
                                   final String pathParams,
                                   final HttpRequest request,
                                   final HttpResponse response,
                                   final HttpFields trailer)
        throws IOException;



    protected void sendError(final HttpResponse response,
                             final HttpFields trailer,
                             final int status,
                             Throwable e)
        throws IOException {

        String reason = e.toString();
        sendError(response,
                  trailer,
                  status,
                  reason);

        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();

        String errMsg;
        // Do not set if some data has already been sent to client -
        // Otherwise the stack trace will be seen as part of payload data
        if (sendStackTrace && !response.isCommitted()){
            response.addField(ProtocolConstants.BODY_CONTAINS_STACK_TRACE_PROPERTY, ProtocolConstants.TRUE);
            Throwable t = e;
            while (t.getCause() != null){
                errMsg  = 
                    LogEscapeFormatter.native2ascii("Exception: " + t+"\n\t");
                out.write(errMsg.getBytes());
                t = t.getCause();
            }
            StringWriter ss = new StringWriter();
            PrintWriter ps = new PrintWriter(ss);
            t.printStackTrace(ps);
            ps.flush();
            errMsg = LogEscapeFormatter.native2ascii(ss.toString());
            out.write(errMsg.getBytes());
        }
        out.flush();
    }


    protected void sendError(final HttpResponse response,
                             final HttpFields trailer,
                             final int status,
                             final String reason) throws IOException 
    {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "API error " + reason);
        }
        //
        // Check if the server is still running and abort the request
        // if it is not. Trying to process cleanly the error while the
        // server is shutting down can end up corrupting the data stream.
        //
        if (!ProtocolService.isRunning()) {
            String msg = "Server is not running - aborting client request. "
            + "Status: " + status + "  Reason: " + reason;
            LOGGER.log(Level.SEVERE, msg);
            throw new IOException(msg);
        }
         
        //
        // Handle possibility of sendError being called twice for the same
        // underlying error and the reponse has already been destroyed.
        //
        if (response == null) {
            String msg = "Response is null. Cannot send error to client. "
            + "Status: " + status + "  Reason: " + reason;
            LOGGER.log(Level.SEVERE, msg);            
            throw new IOException(msg);
        }
        
        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();

        // No data sent to client yet
        if (out != null && !response.isCommitted()) {
            
            response.addField(ProtocolConstants.BODY_CONTAINS_ERROR_STRING_PROPERTY, ProtocolConstants.TRUE);
            response.setStatus(status);
            response.setReason(reason);
            
            // MultiCell config is expected right at the begining so only
            // write it when nothing has already been written to
            // the output stream
            if (!out.isWritten()) {
                writeMulticellConfig(out);
            }
            
            // Set error text in message body
            out.write(LogEscapeFormatter.native2ascii(reason+"\n").getBytes());
            
        } else {    // Partial data already sent to client via chunking 
                    // or previous sendError reset output handle
            
            // Determine if client supports trailers.
            // As of 1.1 the C client does NOT support trailers because 
            // current version of CURL does NOT support trailers.
            HttpRequest request = response.getHttpRequest();
            boolean trailerSupport = false; // default to trailers not supported
            if (request != null) {
                String clientType = request.getField(ProtocolConstants.USER_AGENT_HEADER);
                // Note: When trailer support is added to CURL then need to
                //       check for C Client API version as well. See hcoa.c
                //       for exact string.
                //       If version field has value then this is a standard
                //       HC client (i.e. don't set trailer support for other
                //       types of generic clients).
                if (clientType != null &&
                    request.getField(ProtocolConstants.HONEYCOMB_VERSION_HEADER) != null) {
                    trailerSupport = 
                        (clientType.indexOf("Honeycomb C API Client") == -1);
                }
            }
            
            // If client does not support trailers then have to destroy the response
            // to terminate connection so client will detect some sort of error
            if (!trailerSupport) {
                LOGGER.log(Level.SEVERE,
                    "Cannot set error status in the HTTP header (some data " +
                    "already sent to client). Client does not support " +
                    "trailers, must destroy response to terminate the " +
                    "connection and send an error to the client. Underlying " +
                    "error is - Status: " + status + "   Reason: " + reason);

                // Destroy response. Will also close output stream. An error 
                // (NullPointerException) will be generated when next trying to
                // set the response or send something to output stream. 
                if (out != null && !out.isClosed()) {
                   response.destroy();
                }
                throw new IOException(
                    "The response was destroyed while handling a previous error.");
                    
            } else {    // Client supports trailers
                     
                // It is possible this is the second time sendError is called  
                if (out == null || out.isClosed()) {
                    LOGGER.log(Level.INFO,
                        "Cannot send error to client because the output " +
                        "stream is already closed. Status: " + status + 
                        "   Reason: " + reason);
                } else {
                    LOGGER.log(Level.INFO,
                        "Cannot set error status in the HTTP header (some " +
                        "data already sent to client). Sending error " +
                        "information in trailer. Status: " + status + 
                        "   Reason: " + reason);

                    // Add the trailer
                    trailer.add(ProtocolConstants.TRAILER_STATUS, String.valueOf(status));
                    if (reason == null) 
                        trailer.add(ProtocolConstants.TRAILER_REASON, HOST_ID);
                    else
                        trailer.add(ProtocolConstants.TRAILER_REASON, HOST_ID + " " + reason);

                    out.setTrailer(trailer);
                    // Do NOT set error text in message body because client will
                    // end up reading it as part of the payload data.
                }
            }   // end else (client supports trailers)
        }   // end else (partial data sent to client via http chunking)
    }

    protected static void setSessionRequestedIP(String host)
    {
        requestedIP.set(host);
    }
    public static String getSessionRequestedIP()
    {
        return (String) requestedIP.get();
    }

}
