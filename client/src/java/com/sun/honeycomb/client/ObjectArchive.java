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

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InvalidOidException;
import com.sun.honeycomb.common.ProtocolConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;

import java.util.Date;
import java.util.Map;
import java.io.SequenceInputStream;

/**
 * The <code>ObjectArchive</code> class is the root of the Honeycomb client
 * API. An instance represents a connection to a single Honeycomb cluster
 * and can be shared across threads.
 * <p>
 * At its lowest level, <code>ObjectArchive</code> provides a way to stream
 * data and metadata to and from the Honeycomb cluster using
 * <code>ReadableByteChannels</code> and <code>WritableByteChannels</code>.
 * To provide a more convenient programming model, an
 * <code>ObjectArchive</code> can be specialized by a class implementing
 * <code>MetadataObjectArchive</code> which interpret metadata streams to
 * produce <code>MetadataRecords</code> and create metadata streams from
 * these records. This allows application code to manipulate metadata as
 * objects rather than as lower level streams. 
 * 
 * @see <a href="{@docRoot}/overview-summary.html">Honeycomb API Overview</a>
 * @see com.sun.honeycomb.client.NameValueObjectArchive
 */
public class ObjectArchive  {

    static final long UNKNOWN_SIZE = -1;

    private Map cachesByID;

    /**
     * Initializes a new <code>ObjectArchive</code> with the address or
     * host name of a Honeycomb cluster using the default port.
     */
    public ObjectArchive(String address)
        throws ArchiveException, IOException {
        this(address, 8080);
    }

    /**
     * Initializes a new <code>ObjectArchive</code> with the address or
     * host name of a Honeycomb cluster using the provided port.
     */
    public ObjectArchive(String address, int port)
        throws ArchiveException, IOException {
        connection = newConnection(address, port);

        // Create constant date objects
        //DATE_UNSET = new Date(0);
        //DATE_UNSPECIFIED = new Date(-1);
    }

    protected Connection newConnection(String address, int port)
        throws ArchiveException, IOException {
        return new Connection(address, port);
    }

    private Connection connection;

//     public void setZip(boolean zip){
// 	connection.zip = zip;
//     }


    /**
     * Creates a new object with no metadata record other than the
     * default system record. Returns a <code>SystemRecord</code>
     * instance containing the system metadata for the new object.
     *
     * @throws ArchiveException if the store fails due to an error in
     * the cluster
     * @throws IOException if the store fails due to a communication
     * problem
     */
    public SystemRecord storeObject(ReadableByteChannel dataChannel)
        throws ArchiveException, IOException {

        // Connection.API_CHOOSE_CELL:
        //  'advanced api' store to directed cell not impl
        return store(ProtocolConstants.STORE_PATH,
                     null,
                     null,
                     dataChannel, 
                     Connection.API_CHOOSE_CELL);  
    }

    /**
     * Creates a new object with a record in the cache specified by
     * <code>cacheID</code>. The metadata channel is assumed to contain
     * metadata valid for the specified cache. Returns a
     * <code>SystemRecord</code> instance containing the system
     * metadata for the new object.
     *
     * @throws ArchiveException if the store fails due to an error in
     * the cluster
     * @throws IOException if the store fails due to a communication
     * problem
     */
    SystemRecord storeObject(ReadableByteChannel dataChannel,
			     String cacheID,
			     ReadableByteChannel metadataChannel,
                             int cellid)
        throws ArchiveException, IOException {

        if (cacheID == null && metadataChannel != null) {
            throw new IllegalArgumentException("must specify a cache id to" +
                                               " store metadata");
        }

        SystemRecord record = store(ProtocolConstants.STORE_PATH,
                                    null,
                                    null,
                                    dataChannel,
                                    cellid);
        if (metadataChannel != null) {
            record = storeMetadata(record.getObjectIdentifier(),
                                   cacheID,
                                   metadataChannel);
        }

        return record;
    }

    private static class EmptyInputStream extends InputStream{
        public int read(){return -1;}
    }
    private final static EmptyInputStream emptyInputStream = new EmptyInputStream();

    SystemRecord storeObject(ReadableByteChannel dataChannel,
			     String cacheID,
			     byte[] metadata,
                             int cellid)
        throws ArchiveException, IOException {

        if (cacheID == null && (metadata != null || metadata.length > 0) ) {
            throw new IllegalArgumentException("must specify a cache id to" +
                                               " store metadata");
        }

        InputStream input = (dataChannel == null) ? emptyInputStream : Channels.newInputStream(dataChannel);
        long mdLength = 0;

        if ((metadata == null) || (metadata.length == 0)) {
            mdLength = 0;
        } else {
            mdLength = metadata.length;
            input = new SequenceInputStream(new ByteArrayInputStream(metadata),
                                            input);
        }
        
        SystemRecord record = connection.store(ProtocolConstants.STORE_BOTH_PATH,
					       null,
					       cacheID,
					       mdLength,
					       input,
                                               cellid);
        
        return record;
    }

    /**
     * Creates a new metadata record in the cache specified by
     * <code>cacheID</code> with a link to the data object identified by
     * <code>linkOID</code>. If <code>linkOID</code> represents a metadata
     * object, then the link is resolved and the new metadata record will
     * have a link to the resolved data object. The metadata channel is
     * assumed to contain metadata valid for the specified cache. Returns
     * a <code>SystemRecord</code> instance containing the system metadata
     * for the new object.
     *
     * @throws ArchiveException if the store fails due to an error in
     * the cluster
     * @throws IOException if the store fails due to a communication
     * problem
     */
    SystemRecord storeMetadata(ObjectIdentifier linkOID,
			       String cacheID,
			       ReadableByteChannel metadataChannel)
        throws ArchiveException, IOException {

        if (linkOID == null || cacheID == null || metadataChannel == null) {
            throw new IllegalArgumentException("must specify link oid, " +
                                               "cache id, and " +
                                               "metadata channel");
        }

        // Connection.API_CHOOSE_CELL: metadata goes to cell data is in
        return store(ProtocolConstants.STORE_METADATA_PATH,
                     linkOID,
                     cacheID,
                     metadataChannel,
                     Connection.API_CHOOSE_CELL); 
    }

    protected SystemRecord store(String path,
                                 ObjectIdentifier linkOID,
                                 String cacheID,
                                 ReadableByteChannel channel,
                                 int cellid)
        throws ArchiveException, IOException {

        InputStream in = null;
        if (channel != null) {
            in = Channels.newInputStream(channel);
        }

        return connection.store(path, linkOID, cacheID, -1, in, cellid);
    }

    

    /**
     * Writes all of the data for the specified object into the channel,
     * returning the amount of data actually retrieved.
     *
     * @throws ArchiveException if the retrieve fails due to an error in
     * the cluster
     * @throws IOException if the retrieve fails due to a communication
     * problem
     */
    public long retrieveObject(ObjectIdentifier oid,
                               WritableByteChannel dataChannel)
        throws ArchiveException, IOException {

        return connection.retrieve(ProtocolConstants.RETRIEVE_PATH,
				   oid,
				   dataChannel,
				   UNKNOWN_SIZE,
				   UNKNOWN_SIZE);
    }

    /**
     * Writes the data for the specified object and range into the channel,
     * returning the amount of data actually retrieved.
     *
     * @throws ArchiveException if the retrieve fails due to an error in
     * the cluster
     * @throws IOException if the retrieve fails due to a communication
     * problem
     */
    public long retrieveObject(ObjectIdentifier oid,
                               WritableByteChannel dataChannel,
                               long firstByte,
                               long lastByte)
        throws ArchiveException, IOException {

        return connection.retrieve(ProtocolConstants.RETRIEVE_PATH,
				   oid,
				   dataChannel,
				   firstByte,
				   lastByte);
    }

    /**
     * Writes the metadata for the specified object to the channel,
     * returning the amount of metadata actually retrieved.
     *
     * @throws ArchiveException if the retrieve fails due to an error in
     * the cluster
     * @throws IOException if the retrieve fails due to a communication
     * problem
     */
    long retrieveMetadata(ObjectIdentifier oid,
			  WritableByteChannel metadataChannel)
        throws ArchiveException, IOException {

        return connection.retrieve(ProtocolConstants.RETRIEVE_METADATA_PATH,
				   oid,
				   metadataChannel,
				   UNKNOWN_SIZE,
				   UNKNOWN_SIZE);
    }

    /**
     * Reads the metadata for the specified object and range to the
     * channel, returning the amount of metadata actually retrieved.
     *
     * @throws ArchiveException if the retrieve fails due to an error in
     * the cluster
     * @throws IOException if the retrieve fails due to a communication
     * problem
     */
    long retrieveMetadata(ObjectIdentifier oid,
                                 WritableByteChannel metadataChannel,
                                 long firstByte,
                                 long lastByte)
        throws ArchiveException, IOException {

        return connection.retrieve(ProtocolConstants.RETRIEVE_METADATA_PATH,
				   oid,
				   metadataChannel,
				   firstByte,
				   lastByte);
    }

    /**
     * Returns the metadata record for the specified object and cache
     * as a <code>MetadataRecord</code> instance.
     *
     * @throws ArchiveException if the retrieve fails due to an error in
     * the cluster
     * @throws IOException if the retrieve fails due to a communication
     * problem
     */
    MetadataRecord retrieveMetadata(ObjectIdentifier oid,
				    MetadataObjectArchive mdoa)
        throws ArchiveException, IOException {

        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(bytesOut);
        retrieveMetadata(oid, channel);

        byte[] bytes = bytesOut.toByteArray();
        ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
        return mdoa.readRecord(bytesIn, oid);
    }

    
    /**
     * Deletes the specified object. If the object is a metadata
     * object with a link to a data object, only the metadata object is
     * deleted. If the object is a data object, it is deleted without
     * checking for the existence of metadata objects with links to it.
     * THIS MUST BE USED WITH EXTREME CAUTION! A future version of
     * Honeycomb will add the link check.
     *
     * @throws ArchiveException if the delete fails due to an error in
     * the cluster
     * @throws IOException if the delete fails due to a communication
     * problem
     */
    public void delete(ObjectIdentifier identifier)
        throws ArchiveException, IOException {

        if (identifier == null) {
            throw new InvalidOidException("OID cannot be null.");
        }
	connection.delete(identifier);
    }
    
    /**
     * Checks if the metadata for an object is present in the query
     * engine, and inserts it if not.  If
     * <code>SystemRecord.isIndexed</code> returns false after a store
     * operation, indicating that the metadata for the object was not
     * inserted into the query engine as part of the store operation
     * (i.e. the object was a <i>store index exception</i>), 
     * and if the application wishes to complete the insert of the
     * metadata into the query engine without waiting for system
     * healing, then <code>archive.checkIndexed(oid)</code> can be
     * called repeatedly until it returns any non-zero value.
     * @return An <code>int</code> value that indicates if the metadata for this
     * object has been inserted into the query engine.  The value is
     * -1 if the metadata was already inserted before this operation
     * was called, 0 if the metadata has still not been inserted, or 1
     * if the metadata was just now inserted.
     * @throws ArchiveException if the delete fails due to an error in
     * the cluster
     * @throws IOException if the delete fails due to a communication
     * problem
     * @see SystemRecord#isIndexed
     */
    public int checkIndexed(MetadataObjectArchive mdoa,
                            ObjectIdentifier identifier)
        throws ArchiveException, IOException {

        if (identifier == null) {
            throw new InvalidOidException("OID cannot be null.");
        }
        return connection.checkIndexed(mdoa, identifier);
    }

    /**
     * Returns the first batch of results from issuing the query as a
     * <code>QueryResultSet</code> instance. <code>query</code> is
     * assumed to be a string that can be interpreted by the metadata
     * cache corresponding to <code>cacheID</code>. The number of
     * results returned is the lesser of <code>maxResults</code> and
     * the maximum number of results the cluster is configured to return
     * in a single batch. If <code>maxResults</code> is non-positive it
     * is ignored.
     *
     * @throws ArchiveException if the query fails due to an error in
     * the cluster
     * @throws IOException if the query fails due to a communication
     * problem
     *
     */
    QueryResultSet query(PreparedStatement query,
                         MetadataObjectArchive mdoa,
                         int maxResults)
        throws ArchiveException, IOException {
        return (QueryResultSet) connection.query(query, mdoa, maxResults);
    }

    QueryResultSet query(PreparedStatement query,
                         MetadataObjectArchive mdoa,
                         String cacheID,
                         int maxResults)
        throws ArchiveException, IOException {
        return (QueryResultSet) connection.query(query, mdoa, cacheID,
                                                 maxResults);
    }

    QueryResultSet query(PreparedStatement query,
                         MetadataObjectArchive mdoa,
                         String[] selectKeys,
                         int maxResults)
        throws ArchiveException, IOException {
        return (QueryResultSet) connection.query(query, selectKeys, mdoa, maxResults);
    }


    /**
     * Returns the runtime configuration of the specified server-side cache
     * as a <code>CacheConfiguration</code> instance. The actual class of
     * the result corresponds to the class of the cache identified by
     * <code>cacheID</code>. For example, if <code>cacheID</code> the id
     * of the Name-Value cache, the result will be an instance of
     * <code><a href="NameValueSchema.html">NameValueSchema</a></code>.
     *
     * @throws ArchiveException if the delete fails due to an error in
     * the cluster
     * @throws IOException if the delete fails due to a communication
     * problem
     */

    CacheConfiguration getCacheConfiguration(MetadataObjectArchive cache) throws ArchiveException, IOException{
	return connection.getCacheConfiguration(cache);
    }



    /**
     * Sets the value that determines how long the archive will wait
     * before failing when attempting to connect to the Honeycomb cluster.
     */
    public void setConnectionTimeout(int milliseconds) {
        connection.setConnectionTimeout(milliseconds);
    }

    /**
     * Returns the value that determines how long the archive will wait
     * before failing when attempting to connect to the Honeycomb cluster.
     */
    public int getConnectionTimeout() {
        return connection.getConnectionTimeout();
    }

    /**
     * Sets the value that determines how long the archive will wait
     * before failing when receiving data back from Honeycomb cluster.
     */
    public void setSocketTimeout(int milliseconds) {
        connection.setSocketTimeout(milliseconds);
    }

    /**
     * Returns the value that determines how long the archive will wait
     * before failing when receiving data back from Honeycomb cluster.
     */
    public int getSocketTimeout() {
        return connection.getSocketTimeout();
    }

    /********************************************************
     *
     * Bug 6554027 - hide retention features
     *
     *******************************************************/

    /**
     * Set the retention time for an object
     */
    /*
    public void setRetentionTime(ObjectIdentifier identifier,
                                 Date retentionTime)
        throws ArchiveException, IOException {

        if (identifier == null) {
            throw new InvalidOidException("OID cannot be null.");
        }

	connection.setRetentionTime(identifier, retentionTime.getTime());
    }
    */

    /**
     * Set the retention time for an object relative to the current time
     */
    /*
    public Date setRetentionTimeRelative(ObjectIdentifier identifier,
                                         long retentionLength)
        throws ArchiveException, IOException {

        if (identifier == null) {
            throw new InvalidOidException("OID cannot be null.");
        }

	return new Date(connection.setRetentionTimeRelative(identifier,
                                                            retentionLength));
    }
    */

    /**
     * Get the retention time for an object
     */
    /*
    public Date getRetentionTime(ObjectIdentifier identifier)
        throws ArchiveException, IOException {

        if (identifier == null) {
            throw new InvalidOidException("OID cannot be null.");
        }

	return new Date(connection.getRetentionTime(identifier));
    }
    */

    /**
     * Add a legal hold
     */
    /*
    public void addLegalHold(ObjectIdentifier identifier, String legalHold)
        throws ArchiveException, IOException {

        if (identifier == null) {
            throw new InvalidOidException("OID cannot be null.");
        }

	connection.addLegalHold(identifier, legalHold);
    }
    */

    /**
     * Remove a legal hold. The legal hold name must be known.
     */
    /*
    public void removeLegalHold(ObjectIdentifier identifier, String legalHold)
        throws ArchiveException, IOException {

        if (identifier == null) {
            throw new InvalidOidException("OID cannot be null.");
        }

	connection.removeLegalHold(identifier, legalHold);
    }
    */

    /**
     * Get the time from Honeycomb. May be used by clients to resolve
     * time differences for setting accurate retention times. Returns
     * milliseconds since the epoch in UTC time.
     */
    public Date getDate()
        throws ArchiveException, IOException {
        return new Date(connection.getDate());
    }
}
