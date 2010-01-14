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



package com.sun.honeycomb.client;

import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.ObjectLostException;
import com.sun.honeycomb.common.ThreadPropertyContainer;
import com.sun.honeycomb.common.UnsatisfiableReliabilityException;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InvalidOidException;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.nio.channels.WritableByteChannel;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpRecoverableException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MethodRetryHandler;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.codec.EncoderException;

class Connection {

    // API_CHOOSE_CELL must be < 0 to avoid range of valid cellids
    protected final static int API_CHOOSE_CELL = -1;

    private final boolean DEBUG_LOG = false;

    private static final int CONNECTIONS_PER_HOST = 32;
    private static final int TOTAL_CONNECTIONS = 32;

    // 64k buffer
    private static final int BUFFER_SIZE = 64 * 1024;

    private int connectionTimeout;
    private int socketTimeout;
    private int basePort;
    private MultiCell multiCell;
    private String clusterDomainName;

    private final static void suppressLoggingForClass(Class clazz) {
        Logger.getLogger(clazz.getName()).setLevel(Level.OFF);
    }

    private static void suppressClientLogging() {
        suppressLoggingForClass(HttpMethodBase.class);
        suppressLoggingForClass(HttpConnection.class);
    }

    static {
        suppressClientLogging();
    }

    //private final MethodRetryHandler RETRY_HANDLER = new NullRetryHandler();
    private HttpClient client;

    private static MultiThreadedHttpConnectionManager connectionManager =
        new MultiThreadedHttpConnectionManager();

    static {
        connectionManager.setMaxConnectionsPerHost(CONNECTIONS_PER_HOST);
        connectionManager.setMaxTotalConnections(TOTAL_CONNECTIONS);
    }

    Connection (String cld, int port) throws ArchiveException, IOException{
        this (cld, port, true);
    }

    Connection (String cld, int port, boolean reuseConnection) throws ArchiveException, IOException{
        basePort = port;
        clusterDomainName =cld;
        if (!reuseConnection)
            connectionManager = new MultiThreadedHttpConnectionManager();
        client = new HttpClient(connectionManager);
        multiCell = new MultiCell(cld, port);
        connectionTimeout = 0;
        socketTimeout = 0;
    }


    /**
     * Sets the value that determines how long the archive will wait
     * before failing when attempting to connect to the Honeycomb cluster.
     */
    void setConnectionTimeout(int milliseconds) {
        client.setConnectionTimeout(milliseconds);
        connectionTimeout = milliseconds;
    }

    /**
     * Returns the value that determines how long the archive will wait
     * before failing when attempting to connect to the Honeycomb cluster.
     */
    int getConnectionTimeout() {
        return connectionTimeout;
    }


    /**
     * Sets the value that determines how long the archive will wait
     * before failing when receiving data back from Honeycomb cluster.
     */
    void setSocketTimeout(int milliseconds) {
        client.setTimeout(milliseconds);
        socketTimeout = milliseconds;
    }

    /**
     * Returns the value that determines how long the archive will wait
     * before failing when receiving data back from Honeycomb cluster.
     */
    int getSocketTimeout() {
        return socketTimeout;
    }

    boolean zip = false;


    SystemRecord store(String path,
                       ObjectIdentifier linkOID,
                       String cacheID,
                       long mdLength,
                       InputStream in,
                       int cellid)
        throws ArchiveException, IOException {

        StringBuffer queryBuffer = null;
        if (linkOID != null) {
            queryBuffer = new StringBuffer(ProtocolConstants.ID_PARAMETER);
            queryBuffer.append("=");
            queryBuffer.append(linkOID.toString());
        }

        if (cacheID != null) {
            if (queryBuffer == null) {
                queryBuffer = new StringBuffer();
            } else {
                queryBuffer.append("&");
            }

            queryBuffer.append(ProtocolConstants.CACHE_PARAMETER);
            queryBuffer.append("=");
            queryBuffer.append(cacheID);
        }

        if (mdLength > -1) {
            if (queryBuffer == null) {
                queryBuffer = new StringBuffer();
            } else {
                queryBuffer.append("&");
            }
            
            queryBuffer.append(ProtocolConstants.MDLENGTH_PARAMETER);
            queryBuffer.append("=");
            queryBuffer.append(mdLength);
        }

        //queryBuffer.append("&" + ProtocolConstants.BINARY_PARAMETER + "=" + "true");
        if (zip)
            queryBuffer.append("&" + ProtocolConstants.ZIP_PARAMETER + "=true");

        String query = (queryBuffer != null) ? queryBuffer.toString() : null;
        SystemRecord result = null;
        HttpMethod method = null;
        String node = "unknown";

        short cell;
        if (linkOID != null) {
            cell = linkOID.cellId();
        } else {
            if (cellid == API_CHOOSE_CELL)
                cell = multiCell.getPowerOfTwoCell(this);
            else
                cell = multiCell.checkCellId(this, cellid);
            //System.out.println("picked cell " + cell);
        }
        try {
            method = executeRequest(PostMethod.class,
                                    path,
                                    query,
                                    in,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    zip,
                                    cell);
            Header header = method.getResponseHeader(ProtocolConstants.HONEYCOMB_NODE_HEADER);
            if (header != null){
                node = header.getValue();
            }
            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {
                in = method.getResponseBodyAsStream();
                result = SystemRecord.readSystemRecord(in);
            } else {
                String reason = method.getStatusText();
                if (reason != null) {
                    try {
                        reason = URLDecoder.decode(reason, ProtocolConstants.UTF8_ENCODING);
                    } catch (UnsupportedEncodingException e) {
                        // this should never happen
                        throw new RuntimeException(e);
                    }

                    switch (status) {
                    case HttpStatus.SC_PRECONDITION_FAILED:
                        throw new UnsatisfiableReliabilityException(reason);
                    default:
                        throw new ArchiveException("Request failed on node " + node +
                                                   " with status " + status + ": " + 
                                                   reason + "\n" +
                                                   method.getResponseBodyAsString());
                    }
                }
            }
        } finally {
            if (method != null) {
                releaseConnection (method);
            }
        }

        return result;
    }

    long retrieve(String path,
                  ObjectIdentifier identifier,
                  WritableByteChannel channel,
                  long firstByte,
                  long lastByte)
        throws ArchiveException, IOException {

        StringBuffer query = new StringBuffer(ProtocolConstants.ID_PARAMETER + "=" + identifier);

        long size = 0;
        HttpMethod method = null;

        short cell = identifier.cellId();
        // System.out.println("cell id " + cell);
        try {
            method = executeRequest(GetMethod.class,
                                    path,
                                    query.toString(),
                                    null,
                                    firstByte,
                                    lastByte,
                                    cell);

            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {
                InputStream in = method.getResponseBodyAsStream();
                ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
                byte[] bytes = new byte[BUFFER_SIZE];
                int read = 0;

                while ((read = in.read(bytes)) >= 0) {
                    buffer.clear();
                    buffer.put(bytes, 0, read);
                    buffer.flip();

                    while (buffer.hasRemaining() && channel.write(buffer) >= 0);
                    if (buffer.hasRemaining()) {
                        throw new ArchiveException("failed to write entire buffer to channel");
                    }

                    size += read;
                }

                Header trailer = method.getResponseFooter(ProtocolConstants.TRAILER_STATUS);
                if (trailer == null) {
                    throw new ArchiveException("truncated data stream");
                } else {
                    int trailerStatus;
                    try {
                        trailerStatus = Integer.valueOf(trailer.getValue()).intValue();
                    } catch (NumberFormatException e) {
                        trailerStatus = 500;
                    }

                    if (trailerStatus != HttpStatus.SC_OK) {
                        trailer = method.getResponseFooter(ProtocolConstants.TRAILER_REASON);
                        String trailerReason = (trailer != null)
                            ? trailer.getValue()
                            : null;

                        handleErrorStatus(trailerStatus, trailerReason, method);
                    }
                }
            } else {
                String reason = "failed to retrieve " + identifier.toString() +"\n" + method.getStatusText();
                handleErrorStatus(status, reason, method);
            }
        } catch (IOException e) {
            throw new ArchiveException("Request failed with " + e, e);
        } finally {
            if (method != null) {
                releaseConnection (method);
            }
        }

        return size;
    }

    private static class RequestHeader{
        String name;
        String value;
        private RequestHeader(String name, String value){
            this.name = name; this.value = value;
        }
    }

    private short[] getCells() throws ArchiveException{
        return multiCell.getCellList();
    }

    ResultSet query(PreparedStatement query,
                    MetadataObjectArchive mdoa,
                    int maxResults) throws ArchiveException, IOException{
        String queryPath = buildQueryString(mdoa.getID(), maxResults, true);
        return new QueryResultSet(query, this, mdoa, ProtocolConstants.QUERY_PATH, queryPath, maxResults, getCells());
    }

    ResultSet query(PreparedStatement query,
                    MetadataObjectArchive mdoa,
                    String cacheID,
                    int maxResults) throws ArchiveException, IOException{
        String queryPath = buildQueryString(cacheID, maxResults, true);
        return new QueryResultSet(query, this, mdoa, ProtocolConstants.QUERY_PATH, queryPath, maxResults, getCells());
    }

    ResultSet query(PreparedStatement query,
                    String[] selectClause,
                    MetadataObjectArchive mdoa,
                    int maxResults) throws ArchiveException, IOException{
        for (int i = 0; i < selectClause.length; i++)
            query.addSelect(selectClause[i]);
        String queryPath = buildQueryString(mdoa.getID(), maxResults, true);
        return new QueryResultSet(query, this, mdoa, ProtocolConstants.QUERY_PLUS_PATH, queryPath, maxResults, getCells());
    }

    /** Construct the request path and query path
     */
    private String buildQueryString(String cacheID,
                                    int maxResults,
                                    boolean binary)
        throws ArchiveException, IOException {
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be positive.");
        }

        StringBuffer httpQuery = new StringBuffer();

        httpQuery.append(ProtocolConstants.CACHE_PARAMETER);
        httpQuery.append("=");
        httpQuery.append(URLEncoder.encode(cacheID, ProtocolConstants.UTF8_ENCODING));

        if (maxResults > 0) {
            httpQuery.append("&");
            httpQuery.append(ProtocolConstants.MAX_RESULTS_PARAMETER);
            httpQuery.append("=");
            httpQuery.append(maxResults);
        }
        httpQuery.append("&" + ProtocolConstants.BINARY_PARAMETER + "=" + binary);

        return httpQuery.toString();
    }
        
    /** This is where the real work happens.
     */
    void query(ResultSet resultSet,
               String requestPath,
               String queryPath,
               short cell)
        throws ArchiveException, IOException {

        HttpMethod method = null;
        try {
            RequestHeader[] header;
            ByteArrayInputStream bodyStream;

            if (resultSet.cookie == null){
                bodyStream = new ByteArrayInputStream(resultSet.body);
                header = new RequestHeader[1];
                header[0] = new RequestHeader(ProtocolConstants.QUERY_BODY_CONTENT, ProtocolConstants.XML_IN_BODY);
            } else{
                // Send a null query.   All the information is in the cookie
                //Note:  Assume that cookie is already in ASCII encoding
                bodyStream = new ByteArrayInputStream(resultSet.cookie.getBytes());
                header = new RequestHeader[2];
                header[0] = new RequestHeader(ProtocolConstants.SELECT_CLAUSE_HEADER, "");
                header[1] = new RequestHeader(ProtocolConstants.QUERY_BODY_CONTENT, ProtocolConstants.COOKIE_IN_BODY);
            }
            method = executeRequest(PostMethod.class,
                                    requestPath,
                                    queryPath,
                                    bodyStream,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    false,
                                    cell,
                                    header);
                
            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {
                // handle results
                resultSet.read(method);
     
                Header trailer = method.getResponseFooter(ProtocolConstants.TRAILER_STATUS);
                if (trailer != null) {
                    int trailerStatus;
                    try {
                        trailerStatus = Integer.
                            valueOf(trailer.getValue()).intValue();
                    } catch (NumberFormatException e) {
                        trailerStatus = 500;
                    }

                    trailer = method.getResponseFooter(ProtocolConstants.TRAILER_REASON);
                    String trailerReason = (trailer != null)
                        ? trailer.getValue()
                        : null;

                    handleErrorStatus(trailerStatus, trailerReason, method);
                }
            } else {
                String reason = method.getStatusText();
                handleErrorStatus(status, reason, method);
            }
        } catch (IOException e) {
            throw new ArchiveException("Request failed with " + e, e);
        } finally {
            if (method != null) {
                releaseConnection (method);
            }
        }
    }

    void delete(ObjectIdentifier identifier)
        throws ArchiveException, IOException {
        HttpMethod method = null;
        try {
            String requestPath = ProtocolConstants.ID_PARAMETER + "=" + identifier;
            short cell = identifier.cellId();
            //System.out.println("delete oid cell " + cell);
            method = executeRequest(PostMethod.class,
                                    ProtocolConstants.DELETE_PATH,
                                    requestPath,
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    cell);

            int status = method.getStatusCode();
            if (status < 200 || status >= 300) {
                String reason = method.getStatusText();
                handleErrorStatus(status, reason, method);
            }
        } finally {
            if (method != null) {
                releaseConnection (method);
            }
        }
    }


    ///Force an ObjectIdentifier to become queryable on the server
    public int checkIndexed(MetadataObjectArchive metadataArchive, 
                            ObjectIdentifier identifier)
        throws ArchiveException, IOException {

        String requestPath = 
            ProtocolConstants.CACHE_PARAMETER + "="+
            URLEncoder.encode(metadataArchive.getID(), 
                              ProtocolConstants.UTF8_ENCODING)+"&"+
            ProtocolConstants.ID_PARAMETER + "=" + identifier;

        HttpMethod method = null;
        int isIndexed = 0;

        try {
            short cell = identifier.cellId();
            method = executeRequest(GetMethod.class,
                                    ProtocolConstants.CHECK_INDEXED_PATH,
                                    requestPath,
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    cell);

            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {

                // Binary based response
                InputStream in = method.getResponseBodyAsStream();
                DataInputStream dIn = new DataInputStream(in);
                isIndexed = dIn.readInt();

            } else {
                String reason = "failed to check isIndexed status " +
                    identifier.toString() +"\n" + method.getStatusText();
                handleErrorStatus(status, reason, method);
            }

        } finally {
            if (method != null) {
                releaseConnection(method);
            }
        }

        return isIndexed;
    }

    // Set the retention time for the oid with the date
    public void setRetentionTime(ObjectIdentifier identifier, long date)
        throws ArchiveException, IOException {
        HttpMethod method = null;
        try {
            String requestPath = ProtocolConstants.ID_PARAMETER + "=" + identifier
                + "&" + ProtocolConstants.DATE_PARAMETER + "=" + date;
            short cell = identifier.cellId();
            method = executeRequest(PostMethod.class,
                                    ProtocolConstants.SET_RETENTION_PATH,
                                    requestPath,
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    cell);

            int status = method.getStatusCode();
            if (status < 200 || status >= 300) {
                String reason = method.getStatusText();
                handleErrorStatus(status, reason, method);
            }
        } finally {
            if (method != null) {
                releaseConnection(method);
            }
        }
    }

    // Set the relative retention time for the oid with the date
    public long setRetentionTimeRelative(ObjectIdentifier identifier,
                                         long retentionLength)
        throws ArchiveException, IOException {
        HttpMethod method = null;
        long retentionTime = -2;
        try {
            String requestPath = ProtocolConstants.ID_PARAMETER + "=" + identifier
                + "&" + ProtocolConstants.RETENTION_LENGTH_PARAMETER + "=" + retentionLength;
            short cell = identifier.cellId();
            method = executeRequest(PostMethod.class,
                                    ProtocolConstants.SET_RETENTION_RELATIVE_PATH,
                                    requestPath,
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    cell);

            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {

                // Binary based response
                InputStream in = method.getResponseBodyAsStream();
                DataInputStream dIn = new DataInputStream(in);
                retentionTime = dIn.readLong();

            } else {
                String reason = "failed to set a relative retention time " +
                    identifier.toString() +"\n" + method.getStatusText();
                handleErrorStatus(status, reason, method);
            }

        } finally {
            if (method != null) {
                releaseConnection(method);
            }
        }

        return retentionTime;
    }

    // Get the retention time for the oid
    public long getRetentionTime(ObjectIdentifier identifier)
        throws ArchiveException, IOException {

        // Set defaults
        long size = 0;
        long retentionTime = -2;

        HttpMethod method = null;
        try {
            String requestPath = ProtocolConstants.ID_PARAMETER + "=" + identifier;
            short cell = identifier.cellId();
            method = executeRequest(GetMethod.class,
                                    ProtocolConstants.GET_RETENTION_PATH,
                                    requestPath,
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    cell);

            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {

                // Binary based response
                InputStream in = method.getResponseBodyAsStream();
                DataInputStream dIn = new DataInputStream(in);
                retentionTime = dIn.readLong();

            } else {
                String reason = "failed to retrieve retention time " +
                    identifier.toString() +"\n" + method.getStatusText();
                handleErrorStatus(status, reason, method);
            }

        } finally {
            if (method != null) {
                releaseConnection(method);
            }
        }

        return retentionTime;
    }

    // Add a legal hold for the given object
    public void addLegalHold(ObjectIdentifier identifier, String legalHold)
        throws ArchiveException, IOException {
        HttpMethod method = null;
        try {
            String requestPath = ProtocolConstants.ID_PARAMETER + "=" + identifier
                + "&" + ProtocolConstants.HOLD_TAG_PARAMETER + "=" +
                URLEncoder.encode(legalHold, ProtocolConstants.UTF8_ENCODING);
            short cell = identifier.cellId();
            method = executeRequest(PostMethod.class,
                                    ProtocolConstants.ADD_HOLD_PATH,
                                    requestPath,
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    cell);

            int status = method.getStatusCode();
            if (status < 200 || status >= 300) {
                String reason = method.getStatusText();
                handleErrorStatus(status, reason, method);
            }
        } finally {
            if (method != null) {
                releaseConnection(method);
            }
        }
    }

    // Remove a legal hold from the given object
    public void removeLegalHold(ObjectIdentifier identifier, String legalHold)
        throws ArchiveException, IOException {
        HttpMethod method = null;
        try {
            String requestPath = ProtocolConstants.ID_PARAMETER + "=" + identifier
                + "&" + ProtocolConstants.HOLD_TAG_PARAMETER + "=" +
                URLEncoder.encode(legalHold, ProtocolConstants.UTF8_ENCODING);
            short cell = identifier.cellId();
            method = executeRequest(PostMethod.class,
                                    ProtocolConstants.REMOVE_HOLD_PATH,
                                    requestPath,
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    cell);

            int status = method.getStatusCode();
            if (status < 200 || status >= 300) {
                String reason = method.getStatusText();
                handleErrorStatus(status, reason, method);
            }
        } finally {
            if (method != null) {
                releaseConnection(method);
            }
        }
    }

    /**
     * Get the time from the cell. Use default cell for multicell.
     * Assumes that all cells have the same time.
     */
    public long getDate() throws ArchiveException, IOException {
        long date = -1;

        HttpMethod method = null;
        try {
            method = executeRequest(GetMethod.class,
                                    ProtocolConstants.GET_DATE_PATH,
                                    "dummy",
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    (short)-2); // 'default' cell

            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {

                // Binary based response
                InputStream in = method.getResponseBodyAsStream();
                DataInputStream dIn = new DataInputStream(in);
                date = dIn.readLong();

            } else {
                String reason = "failed to retrieve system time " +
                    "from the cell" + "\n" + method.getStatusText();
                handleErrorStatus(status, reason, method);
            }

        } finally {
            if (method != null) {
                releaseConnection(method);
            }
        }

        return date;
    }

    
    CacheConfiguration getCacheConfiguration(MetadataObjectArchive metadataArchive)
        throws ArchiveException, IOException {


        StringBuffer httpQuery = new StringBuffer();
        httpQuery.append(ProtocolConstants.CACHE_PARAMETER);
        httpQuery.append("=");
        httpQuery.append(URLEncoder.encode(metadataArchive.getID(), ProtocolConstants.UTF8_ENCODING));

        HttpMethod method = null;
        CacheConfiguration result = null;
        //System.out.println("get cache config");
        try {
            // Fetch configuration on random/default cell.
            method = executeRequest(GetMethod.class,
                                    ProtocolConstants.GET_CACHE_CONFIG_PATH,
                                    httpQuery.toString(),
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    (short)-2); // default cell

            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {
                InputStream in = method.getResponseBodyAsStream();
                result = metadataArchive.readConfiguration(in);

                Header trailer = method.getResponseFooter(ProtocolConstants.TRAILER_STATUS);
                if (trailer != null) {
                    int trailerStatus;
                    try {
                        trailerStatus = Integer.
                            valueOf(trailer.getValue()).intValue();
                    } catch (NumberFormatException e) {
                        trailerStatus = 500;
                    }

                    trailer = method.getResponseFooter(ProtocolConstants.TRAILER_REASON);
                    String trailerReason = (trailer != null)
                        ? trailer.getValue()
                        : null;

                    handleErrorStatus(trailerStatus, trailerReason, method);
                }
            } else {
                String reason = method.getStatusText();
                handleErrorStatus(status, reason, method);
            }
        } catch (IOException e) {
            throw new ArchiveException("request failed with " + e, e);
        } finally {
            if (method != null) {
                releaseConnection (method);
            }
        }

        return result;
    }

    /** 
     *  For now, get/discard schema to induce load of multicell config.
     *  Expected to be used only on corner case when the 1st op on an 
     *  ObjectArchive is a retrieve, to get the config to look up the cell
     *  spec'd in the oid. In all other cases a previous cmd should have 
     *  already gotten the config. This depends on the server handling
     *  requests to the extended cache. If we disable this for a no-md
     *  config, this will need to be revisited. Test case that exercises
     *  this is com.sun.honeycomb.hctest.cases.StoreRetrieveStream
     *
     *  Is a clone of getCacheConfiguration().
     */
    void getMulticellConfiguration()
        throws ArchiveException, IOException {

        StringBuffer httpQuery = new StringBuffer();
        httpQuery.append(ProtocolConstants.CACHE_PARAMETER);
        httpQuery.append("=");
        httpQuery.append(URLEncoder.encode("extended", ProtocolConstants.UTF8_ENCODING));

        HttpMethod method = null;

        try {
            // Fetch configuration on random/default cell.
            method = executeRequest(GetMethod.class,
                                    ProtocolConstants.GET_CACHE_CONFIG_PATH,
                                    httpQuery.toString(),
                                    null,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    ObjectArchive.UNKNOWN_SIZE,
                                    (short)-2); // must be default cell
                                                // to avoid recursion

            int status = method.getStatusCode();
            if (status >= 200 && status < 300) {
                // throw away the bytes
                InputStream in = method.getResponseBodyAsStream();
                byte b[] = new byte[1024];
                while (in.read(b) != -1); // consume bytes til -1==EOF

                Header trailer = method.getResponseFooter(ProtocolConstants.TRAILER_STATUS);
                if (trailer != null) {
                    int trailerStatus;
                    try {
                        trailerStatus = Integer.
                            valueOf(trailer.getValue()).intValue();
                    } catch (NumberFormatException e) {
                        trailerStatus = 500;
                    }

                    trailer = method.getResponseFooter(ProtocolConstants.TRAILER_REASON);
                    String trailerReason = (trailer != null)
                        ? trailer.getValue()
                        : null;

                    handleErrorStatus(trailerStatus, trailerReason, method);
                }
            } else {
                String reason = method.getStatusText();
                handleErrorStatus(status, reason, method);
            }
        } catch (IOException e) {
            throw new ArchiveException("request failed with " + e, e);
        } finally {
            if (method != null) {
                releaseConnection (method);
            }
        }
    }

    private int retryNtimes = 1;

    void setRetryCount(int times){
        retryNtimes = times;
    }
    int getRetryCount(){
        return retryNtimes;
    }

    private void parseMulticellConfig(InputStream in, int size) 
        throws IOException {

        //System.out.println("parseMulticellConfig " + size);
        byte[] buf = new byte[size];

        for (int i=0; i<size; i++) {
            int b = in.read();
            if (b == -1) {
                buf = null;
                break;
            }
            buf[i] = (byte) b;
        }
        if (buf != null) {
            //System.out.println("parse mcell\n" + sb);
            ByteArrayInputStream bs = new ByteArrayInputStream(buf);
            multiCell.readConfig(bs);
            //System.out.println(multiCell.toString());
        }
    }

    private HttpMethod executeRequest(final Class methodClass,
                                      final String path,
                                      final String query,
                                      final InputStream in,
                                      final long firstByte,
                                      final long lastByte,
                                      short cell)
        throws ArchiveException, IOException {

        return executeRequest(methodClass,
                              path,
                              query,
                              in,
                              firstByte,
                              lastByte, 
                              false,
                              cell);
    }


    private HttpMethod executeRequest(final Class methodClass,
                                      final String path,
                                      String query,
                                      final InputStream in,
                                      final long firstByte,
                                      final long lastByte,
                                      boolean zip,
                                      short cell) 
        throws ArchiveException, IOException {
        return executeRequest(methodClass,
                              path,
                              query,
                              in,
                              firstByte,
                              lastByte,
                              zip,
                              cell,
                              null);
    }

    private HttpMethod executeRequest(final Class methodClass,
                                      final String path,
                                      String query,
                                      final InputStream in,
                                      final long firstByte,
                                      final long lastByte,
                                      boolean zip,
                                      short cellId,
                                      RequestHeader[] requestHeaders)
        throws ArchiveException, IOException {

        HttpMethodBase method = null;
        IOException exception = null;
        int status = -1;
        String node = "";
        NullRetryHandler retryHandler = new NullRetryHandler();

        exception = null;
        try {
            method = (HttpMethodBase)methodClass.newInstance();
            method.setRequestHeader(ProtocolConstants.HONEYCOMB_VERSION_HEADER, ProtocolConstants.HONEYCOMB_VERSION);
            method.setRequestHeader(ProtocolConstants.HONEYCOMB_MINOR_VERSION_HEADER, ProtocolConstants.HONEYCOMB_MINOR_VERSION);
            method.setRequestHeader(ProtocolConstants.RETRY_COUNT, Integer.toString(retryHandler.count));
	    
            method.setRequestHeader(ProtocolConstants.MULTICELL_CONFIG_VERSION_HEADER, multiCell.getVersion());
            //method.setRequestHeader(ProtocolConstants.SEND_STACK_TRACE, "true");
            if (requestHeaders != null){
                for (int i = 0; i < requestHeaders.length; i++)
                    method.setRequestHeader(requestHeaders[i].name, requestHeaders[i].value);
            }
            //method.setRequestHeader("Count", "Test " + ID + ":" + count++);
            //method.setRequestHeader(ProtocolConstants.TEST_DELAY_SECS, "2");
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("failed to instantiate " +
                                               "class " +
                                               methodClass.getName());
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("failed to instantiate " +
                                               "class " +
                                               methodClass.getName());
        }
        Cell cel = multiCell.getCell(cellId, this);
        HostConfiguration host = new HostConfiguration();
        //System.out.println("cel.getDataVIP " + cel.getDataVIP());
        host.setHost(cel.getDataVIP(), cel.getPort());
        method.setHostConfiguration(host);
        method.setPath(path);


        // This bit of code allows a subclass to add query parameters
        // to the HTTP request URL.  This is used by testware, for example,
        // to pass additional information down into the cluster.
        // The "getExtraQueryParameters()" method is *NOT* intended to be
        // a documented feature available for customers.
        Map extraParameters = getExtraQueryParameters();
        if (extraParameters != null) {
            Iterator keys = extraParameters.keySet().iterator();
            while (keys.hasNext()) {
                Object name = keys.next();
                Object value = extraParameters.get(name);
                if (query == null) {
                    query = "";
                }
                try {
                    query += "&";
                    query += URLEncoder.encode(name.toString(), ProtocolConstants.UTF8_ENCODING);
                    query += "=";
                    query += URLEncoder.encode(value == null ? "null" : value.toString(), ProtocolConstants.UTF8_ENCODING);
                }
                catch (java.io.UnsupportedEncodingException e) {}
            }
        }

        if (query != null) {
            method.setQueryString(query);
        }

        if (firstByte != ObjectArchive.UNKNOWN_SIZE || lastByte != ObjectArchive.UNKNOWN_SIZE) {
            StringBuffer range = new StringBuffer();

            if (firstByte != ObjectArchive.UNKNOWN_SIZE) {
                if (firstByte < 0) {
                    throw new IllegalArgumentException("firstByte cannot be negative");
                }

                range.append(firstByte);
            }

            range.append(ProtocolConstants.RANGE_SEPARATOR);

            if (lastByte != ObjectArchive.UNKNOWN_SIZE) {
                if (lastByte < firstByte) {
                    throw new IllegalArgumentException("lastByte cannot be less than firstByte.");
                }

                range.append (lastByte);
            }

            method.setRequestHeader(ProtocolConstants.RANGE_HEADER, range.toString());
        }

        if (method instanceof EntityEnclosingMethod) {
            EntityEnclosingMethod eeMethod = (EntityEnclosingMethod)method;
            //eeMethod.setUseZip(zip);
            eeMethod.setRequestContentLength(EntityEnclosingMethod.CONTENT_LENGTH_CHUNKED);
            if (in != null) {
                if (zip){
                    //new RequestEntity(){};
                }
                eeMethod.setRequestBody(in);
            }
        }

        method.setMethodRetryHandler(retryHandler);

        try {
            status = client.executeMethod(method);

            // System.err.println("client: " + client + " " + client.getHostConfiguration().getHost() + " " +
            //                  connectionManager /*.getConnection(client.getHostConfiguration()) */);

            Header header = method.getResponseHeader(ProtocolConstants.HONEYCOMB_NODE_HEADER);
            if (header != null){
                node = header.getValue();
            }
            header = method.getResponseHeader(ProtocolConstants.HONEYCOMB_MULTICELL_HEADER);
            if (header != null) {
                String val = header.getValue();
                int size;
                try {
                    size = Integer.parseInt(val);
                } catch (NumberFormatException nfe) {
                    throw new ArchiveException("Parsing multicell response size [" + val + "]: " + nfe);
                }
                parseMulticellConfig(method.getResponseBodyAsStream(), size);
            }

            if (isSuccessStatus(status)) {
                //host.clearEnableTime();
            } else {

                if (DEBUG_LOG) {
                    String reason = method.getStatusText();
                    if (reason != null) {
                        reason = URLDecoder.decode(reason, ProtocolConstants.UTF8_ENCODING);
                    }

                    System.out.println("request failed with status " +
                                       status +
                                       ((reason != null)
                                        ? (", reason: " + reason)
                                        : ""));
                }
            }
        } catch (IOException e) {
            exception = e;
            if (DEBUG_LOG) {
                System.out.println("request failed with " + e);
            }
        }
        

        if (exception != null) {
            if (DEBUG_LOG)
                System.out.println(this + " failed " + exception + " on " + path);
            //handleErrorStatus(status, exception.toString(), method);

            ArchiveException ae = new ArchiveException("Request failed after " +
                         retryHandler.count + 
                         ((retryHandler.count == 1) ? " retry " : " retries ") +
                         "[" + cel + "]",
                         exception);
            throw ae;
        }

        if (!isSuccessStatus(status)) {
            handleErrorStatus(status, null, method);
        }

        return method;
    }



    protected Map getExtraQueryParameters() {
        return null;
    }


    private void handleErrorStatus(int status, String reason, HttpMethod method)
        throws ArchiveException {
        StringBuffer data;
        try {
            Header header = method.getResponseHeader(ProtocolConstants.HONEYCOMB_NODE_HEADER);
            String node = (header == null) ? "unknown server node" : ("Server " + header.getValue());
            data = new StringBuffer(node);
            if (reason != null) {
                reason = URLDecoder.decode(reason, ProtocolConstants.UTF8_ENCODING);
                data.append(" ").append(reason);
            }
            data.append(": ").append(method.getResponseBodyAsString());
        }
        // these should never happen
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } 
        catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        switch (status) {
        case HttpStatus.SC_NOT_FOUND:
            throw new NoSuchObjectException(data.toString());
        case HttpStatus.SC_FAILED_DEPENDENCY:
            throw new ObjectCorruptedException(data.toString());
        case HttpStatus.SC_GONE:
            throw new ObjectLostException(data.toString());
        case HttpStatus.SC_SERVICE_UNAVAILABLE:
            throw new ArchiveException(data.toString());
        default:
            throw new ArchiveException("Request failed " +
                                       "with status " +
                                       status +
                                       (status==500 ? " (Internal Server Error)" : "") +
                                       ": " +
                                       data.toString());
        } 
    }


    private boolean isSuccessStatus(int status) {
        return !(status < 0 || (status >= 500 && status < 507));
    }


    private final class NullRetryHandler implements MethodRetryHandler {
        int count = 0;
        String message = "";
        public boolean retryMethod(HttpMethod method,
                                   HttpConnection connection,
                                   HttpRecoverableException recoverableException,
                                   int executionCount,
                                   boolean requestSent) {
            //  --> save earlier info & report
            if (DEBUG_LOG) {
                if (ThreadPropertyContainer.getLogTag() != null)
                    System.out.println(ThreadPropertyContainer.getLogTag() + " Failure " + executionCount);
                else
                    System.out.println("[NOLOGTAG] Failure " + executionCount);
            }
            message += " tried " + executionCount + " of " + retryNtimes;

            //System.err.println("Retry count " + executionCount);
            if (executionCount <= retryNtimes){
                //System.err.println("retrying");
                count ++;
            }
            return executionCount <= retryNtimes;
        }
    }


    private static void releaseConnection(HttpMethod method){
        method.releaseConnection();
        //method.recycle();
    }
}
