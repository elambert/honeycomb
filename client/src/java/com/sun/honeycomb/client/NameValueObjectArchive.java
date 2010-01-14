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
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.StringTerminatedInputStream;

import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * This class is this main entry point into the @HoneycombProductName@.
 * Each instance of a <code>NameValueObjectArchive</code> provides access to 
 * a specific @HoneycombProductName@ server, functioning as a proxy object 
 * on which operations 
 * can be performed. Multiple simultaneous operations can be accomplished in 
 * separate threads on the same <code>NameValueObjectArchive</code>.
 * Communication with the @HoneycombProductName@ server is entirely by means 
 * of HTTP requests. A pool of HTTP connections is maintained for efficiency.
 * <p>
 * A <code>NameValueObjectArchive</code> allows you to store and retrieve 
 * object data and associated metadata records. Metadata is
 * associated with an object in a set of Name-Value pairs 
 * (see <a href="NameValueRecord.html">NameValueRecord</a>). 
 * Metadata records can
 * be used to associate application-specific information with the raw data, 
 * such as name, mime type, or purge date. Metadata records consist of 
 * structured data which can be queried. 
 * Object data is opaque to the @HoneycombProductName@.
 * <p>
 * A <code>NameValueObjectArchive</code> always ensures that a metadata record
 * is created on the @HoneycombProductName@ server for each newly-stored object,
 * even if no metadata is provided with the store. This enables a model of
 * programming where every stored data object is accessed by Name-Value
 * metadata records, for instance for examining results from queries or 
 * performing delete operations. Object data is never deleted directly; it is 
 * deleted when its last referencing metadata record is deleted.
 * <p>
 * Client applications should place <code>NameValueObjectArchive</code>
 * methods within retry loops to handle cases such as storage node failover. 
 * One immediate retry should be sufficient in the great majority of cases. 
 * In some cases of node failover, retries should be pursued for up to 30 
 * seconds.
 * <p>
 * Note that when the @HoneycombProductName@ server is sufficiently loaded, 
 * client timeouts may occur. To avoid this, maximum client threads should 
 * be on the order of 25 * number_of_storage_nodes. For example, on a 
 * full-cell with 16 storage nodes, maximum client threads should 
 * be <= 25 * 16, or 400.
 * <p>
 * @see <a href="NameValueRecord.html">NameValueRecord</a>
 */
public class NameValueObjectArchive extends ObjectArchive implements MetadataObjectArchive{


    protected static final String CACHE_ID = "extended";
    private static final String SYSTEM_CACHE_ID = "system";

    /**
     * Initialize a new <code>NameValueObjectArchive</code> with
     * the address or host name of a @HoneycombProductName@ server using the
     * default port.
     */
    public NameValueObjectArchive(String address) throws ArchiveException, IOException{
        this(address, ProtocolConstants.DEFAULT_PORT);
    }

    final NameValueSchema schema;

    /**
     * Initialize a new <code>NameValueObjectArchive</code> with
     * the address or host name of a @HoneycombProductName@ server using the
     * provided port.
     */
    public NameValueObjectArchive(String address, int port) throws ArchiveException, IOException {
        super(address, port);
        schema = (NameValueSchema) getSchema();
        //schema.setEncoding(Encoding.IDENTITY);
    }

    // This method is replicated here so that it will appear in the javadoc.

    /**
     * Sets the value that determines how long the archive will wait
     * before failing when receiving data back from Honeycomb cluster.
     */
    public void setSocketTimeout(int milliseconds) {
        super.setSocketTimeout(milliseconds);
    }

    // This method is replicated here so that it will appear in the javadoc.
                                                                                
    /**
     * Returns the value that determines how long the archive will wait
     * before failing when receiving data back from Honeycomb cluster.
     */
    public int getSocketTimeout() {
        return super.getSocketTimeout();
    }


    protected void setQuotedPrintable(){
      schema.setEncoding(Encoding.QUOTED_PRINTABLE);
    }

    /**
     * Used by internal helper classes to identify the metadata object archive. 
     * This is part of the infrastructure and not useful to users.
     */
    public String getID(){
      return CACHE_ID;
    }

    //--> Poor design principal; would prefer always to stream data.
    protected byte[] createBytesForRecord(NameValueRecord record)
        throws ArchiveException, IOException {
        
        if (record == null) {
            return null;
        }

        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        record.writeRecord(bytesOut);

        bytesOut.flush();
        return(bytesOut.toByteArray());
    }

    /**
     * Upload a new data object with no user metadata.
     * Returns a <a href=SystemRecord.html>SystemRecord</a> instance containing
     * the system metadata for the new data object.
     *
     * @throws ArchiveException if the store fails due to an error on
     * the server
     * @throws IOException if the store fails due to a communication
     * problem
     */
    public SystemRecord storeObject(ReadableByteChannel dataChannel)
        throws ArchiveException, IOException {
        return storeObject(dataChannel, CACHE_ID, new byte[0], 
                           Connection.API_CHOOSE_CELL);
    }


    /**
     * Upload a new data object with a Name-Value metadata record
     * created from <code>record</code>. Returns a
     * <a href=SystemRecord.html>SystemRecord</a> instance containing the 
     * system metadata for the new object.
     *
     * @throws ArchiveException if the store fails due to an error on
     * the server
     * @throws IOException if the store fails due to a communication
     * problem
     */
    public SystemRecord storeObject(ReadableByteChannel dataChannel,
                                    NameValueRecord record)
        throws ArchiveException, IOException {

        return storeObject(dataChannel,
			   CACHE_ID,
			   createBytesForRecord(record), 
                           Connection.API_CHOOSE_CELL);
    }

    /**
     * Upload a new data object with a Name-Value metadata record
     * created from <code>metadataChannel</code>, which is assumed to
     * contain valid Honeycomb Name-Value XML. Returns a
     * <code>SystemRecord</code> instance containing the system
     * metadata for the new object.
     *
     * @throws ArchiveException if the store fails due to an error on
     * the server
     * @throws IOException if the store fails due to a communication
     * problem
     */
    protected SystemRecord storeObject(ReadableByteChannel dataChannel,
				       ReadableByteChannel metadataChannel)
        throws ArchiveException, IOException {

        return storeObject(dataChannel, CACHE_ID, metadataChannel, 
                           Connection.API_CHOOSE_CELL);
    }



    // This method is replicated here so that it will appear in the javadoc.

    /**
     * Writes all of the data for the specified object into the channel,
     * returning the amount of data actually retrieved.
     *
     * @throws ArchiveException if the retrieve fails due to an error on
     * the server
     * @throws IOException if the retrieve fails due to a communication
     * problem
     */
    public long retrieveObject(ObjectIdentifier oid,
                               WritableByteChannel dataChannel)
        throws ArchiveException, IOException {
      return super.retrieveObject(oid, dataChannel);
    }


    // This method is replicated here so that it will appear in the javadoc.

    /**
     * Writes the data for the specified object and range into the channel,
     * returning the amount of data actually retrieved.
     *
     * @throws ArchiveException if the retrieve fails due to an error on
     * the server
     * @throws IOException if the retrieve fails due to a communication
     * problem
     */
    public long retrieveObject(ObjectIdentifier oid,
                               WritableByteChannel dataChannel,
                               long firstByte,
                               long lastByte)
        throws ArchiveException, IOException {

        return super.retrieveObject(oid,
                                    dataChannel,
                                    firstByte,
                                    lastByte);
    }


    /**
     * Creates a new metadata record in the Name-Value Object Archive 
     * linked to the data object identified by <code>oid</code>. 
     * Returns a <a href=SystemRecord.html>SystemRecord</a> instance 
     * containing the system metadata for the new metadata record.
     *
     * @throws ArchiveException if the store fails due to an error on
     * the server
     * @throws IOException if the store fails due to a communication
     * problem
     */
    public SystemRecord storeMetadata(ObjectIdentifier oid,
                                      NameValueRecord record)
        throws ArchiveException, IOException {

        return storeMetadata(oid,
                             CACHE_ID,
                             Channels.newChannel
                             (new ByteArrayInputStream(createBytesForRecord(record))));
    }

    /**
// -->
     * Creates a new metadata record in the Name-Value Archive with a link to
     * the data object identified by <code>oid</code>. If
     * <code>oid</code> represents a metadata object, then the link is
     * resolved and the new metadata record will have a link to the resolved
     * data object. The metadata channel is assumed to contain a valid @HoneycombProductName@
     * Name-Value XML. Returns a <a href=SystemRecord.html>SystemRecord</a>
     * instance containing the system metadata for the new object.
     *
     * @throws ArchiveException if the store fails due to an error on
     * the server
     * @throws IOException if the store fails due to a communication
     * problem
     */
    protected SystemRecord storeMetadata(ObjectIdentifier oid,
                                         ReadableByteChannel metadataChannel)
        throws ArchiveException, IOException {

        return storeMetadata(oid, CACHE_ID, metadataChannel);
    }

    /**
     * Returns a <code>NameValueRecord</code> instance containing the System
     * and Name-Value metadata for the metadata record identified by <code>oid</code>.
     *
     * @throws ArchiveException if the retrieve fails due to an error on
     * the server
     * @throws IOException if the retrieve fails due to a communication
     * problem
     */
    public NameValueRecord retrieveMetadata(ObjectIdentifier oid)
        throws ArchiveException, IOException {

        return (NameValueRecord)retrieveMetadata(oid, this);
    }



    // This method is replicated here so that it will appear in the javadoc.

    /**
     * Deletes the metadata record. If it is the last metadata record 
     * referencing it, the underlying object data will also be deleted.
     *
     * @throws ArchiveException if the delete fails due to an error on
     * the server
     * @throws IOException if the delete fails due to a communication
     * problem
     */
    public void delete(ObjectIdentifier identifier)
        throws ArchiveException, IOException {

        if (identifier == null) {
            throw new InvalidOidException("ObjectIdentifier cannot be null.");
        }
	super.delete(identifier);
    }

    /**
     * Checks if the metadata for an object is present in the query
     * engine, and inserts it if not.  <code>checkIndexed</code> is
     * intended as way to resolve a <i>store index exception</i> (see 
     * <code>SystemRecord.isIndexed</code> for definition) under program control.
     * Once a store index exception occurs (as indicated by a 
     * <code>SystemRecord.isIndexed</code> value of <code>false</code> after a store
     * operation) then <code>archive.checkIndexed(oid)</code> can be
     * called repeatedly until it returns any non-zero value.  This
     * will ensure that the metadata for the object has been inserted
     * into the query engine; the object should then start to show up in
     * matching queries.
     * @return An <code>int</code> value that indicates if the metadata for this
     * object has been inserted into the query engine.  The value is
     * -1 if the metadata was already inserted before this operation
     * was called, 0 if the metadata has still not been inserted, or 1
     * if the metadata was just now inserted.
     * @throws ArchiveException if checkIndexed fails due to an error in
     * the cluster
     * @throws IOException if checkIndexed fails due to a communication
     * problem
     * @see SystemRecord#isIndexed
     */
 public int checkIndexed(ObjectIdentifier identifier)
        throws ArchiveException, IOException {
        return super.checkIndexed(this, identifier);
    }


    /**
     * Returns <code>ObjectIdentifier</code>s of metadata records matching the query as a
     * <code>QueryResultSet</code> instance. The 
     * <code>query</code> parameter is an expression that identifies the
     * objects to return. The <code>query</code> language is similar 
     * to the where-clause of an SQL statement. Each <code>query</code> 
     * expression contains a combination of field names, logical operators, 
     * typed literal values, and some simple SQL operators and functions.
     * The <code>resultsPerFetch</code> parameter specifies the number of  
     * results fetched by each underlying call to the server. For simple  
     * queries a <code>resultsPerFetch</code> of 2000 usually works, but for 
     * complex queries a much lower value than that is suggested.
     * <p>
     * The results are stepped through by calling the <code>next</code>
     * method and using the typed getXXX accessor methods.
     *
     * @throws ArchiveException if the query fails due to an error on
     * the server
     * @throws IOException if the query fails due to a communication
     * problem
     *
     */
    public QueryResultSet query(String query,
                                int resultsPerFetch)
        throws ArchiveException, IOException {
        return query(new PreparedStatement(query), this, resultsPerFetch);
    }


    /**
     * Returns <code>ObjectIdentifier</code>s and specified fields from metadata records matching the query 
     * as a <code>QueryResultSet</code> instance. 
     * The <code>query</code> language is similar
     * to the where-clause of an SQL statement. Each <code>query</code>
     * expression contains a combination of field names, logical operators,
     * typed literal values, and some simple SQL operators and functions.
     * The <code>resultsPerFetch</code> parameter specifies the number of
     * results fetched by each underlying call to the server. For simple
     * queries a <code>resultsPerFetch</code> of 2000 usually works, but for
     * complex queries a much lower value than that is suggested.
     * <p>
     * The results are stepped through by calling the <code>next</code>
     * method and using the getObjectIdentifier accessor.
     * <br>
     * <code>selectKeys</code> identifies the values to be returned, 
     * functioning as an SQL select-clause. 
     * <b>Only metadata records containing all of the specified select
     * keys will be returned.</b>
     *
     * @throws ArchiveException if the query fails due to an error on
     * the server
     * @throws IOException if the query fails due to a communication
     * problem
     *
     */
    public QueryResultSet query(String query,
                                String[] selectKeys,
                                int resultsPerFetch)
        throws ArchiveException, IOException {

        return query(new PreparedStatement(query), this, selectKeys, resultsPerFetch);
    }


    /**
     * Returns <code>ObjectIdentifier</code>s of metadata records matching the query as a
     * <code>QueryResultSet</code> instance. 
     * The <code>query</code> language is similar
     * to the where-clause of an SQL statement. Each <code>query</code>
     * expression contains a combination of field names, logical operators,
     * typed literal values, and some simple SQL operators and functions.
     * The <code>resultsPerFetch</code> parameter specifies the number of
     * results fetched by each underlying call to the server. For simple
     * queries a <code>resultsPerFetch</code> of 2000 usually works, but for
     * complex queries a much lower value than that is suggested.
     * <p>
     * The results are stepped through by calling the <code>next</code>
     * method and using the typed getXXX accessor methods.
     *
     * @throws ArchiveException if the query fails due to an error on
     * the server
     * @throws IOException if the query fails due to a communication
     * problem
     *
     */
    public QueryResultSet query(PreparedStatement query,
                                int resultsPerFetch)
        throws ArchiveException, IOException {

        return query(query, this, resultsPerFetch);
    }


    /**
     * Returns <code>ObjectIdentifier</code>s and specified fields from metadata records matching the query 
     * as a <code>QueryResultSet</code> instance. 
     * The <code>query</code> language is similar
     * to the where-clause of an SQL statement. Each <code>query</code>
     * expression contains a combination of field names, logical operators,
     * typed literal values, and some simple SQL operators and functions.
     * The <code>resultsPerFetch</code> parameter specifies the number of
     * results fetched by each underlying call to the server. For simple
     * queries a <code>resultsPerFetch</code> of 2000 usually works, but for
     * complex queries a much lower value than that is suggested.
     * <p>
     * The results are stepped through by calling the <code>next</code>
     * method and using the getObjectIdentifier accessor.
     * <br>
     * <code>selectKeys</code> identifies the values to be returned, 
     * functioning as an SQL select-clause. 
     * <b>Only metadata records containing all of the specified select
     * keys will be returned.</b>
     *
     * @throws ArchiveException if the query fails due to an error on
     * the server
     * @throws IOException if the query fails due to a communication
     * problem
     *
     */
    
    public QueryResultSet query(PreparedStatement query,
                                String[] selectKeys,
                                int resultsPerFetch)
        throws ArchiveException, IOException {

        return query(query, this, selectKeys, resultsPerFetch);
    }

    /********************************************************
     *
     * Bug 6554027 - hide retention features
     *
     *******************************************************/

    /**
     * Get all the objects which have this legal hold applied to them
     */
    /*
    public QueryResultSet queryLegalHold(String legalHold, int resultsPerFetch)
        throws ArchiveException, IOException {
        String query = "queryHold " + legalHold;
        return query(new PreparedStatement(query), this,
                     SYSTEM_CACHE_ID, resultsPerFetch);
    }
    */

    /**
     * Returns the runtime configuration of the Name-Value Object Archive
     * as a <code>NameValueSchema</code> instance.
     *
     * @throws ArchiveException if the operation fails due to an error on
     * the server
     * @throws IOException if the operation fails due to a communication
     * problem
     */
    public NameValueSchema getSchema()
        throws ArchiveException, IOException {
        return (NameValueSchema)getCacheConfiguration(this);
    }


    /**
     * Used by internal helper classes to implement the metadata object archive. 
     * This is part of the infrastructure and not useful to users.
     * <p>
     * Returns a newly-created <code>NameValueSchema</code> instance
     * initialized with the contents of the input stream.
     * 
     * @throws ArchiveException if the read fails due to an error on
     * the server
     * @throws IOException if the read fails due to a communication
     * problem
     */
    public CacheConfiguration readConfiguration(InputStream in)
        throws ArchiveException, IOException {

        return NameValueSchema.parse(in);
    }

    CacheConfiguration getConfiguration() throws ArchiveException, IOException{
        return getCacheConfiguration(this);
    }


    private static final String XML_TERMINATION = "</" + NameValueXML.TAG_RELATIONAL + ">";

    /**
     * Used by internal helper classes to implement the metadata object archive. 
     * This is part of the infrastructure and not useful to users.
     * <p>
     * Returns a newly-created <code>NameValueRecord</code> initialized
     * with the contents of the input stream.
     *
     * @throws ArchiveException if the read fails due to an error on
     * the server
     * @throws IOException if the read fails due to a communication
     * problem
     */
    public MetadataRecord readRecord(InputStream in, ObjectIdentifier oid)
        throws ArchiveException, IOException {
        NameValueRecord result = null;

        if (in.available() > 0) {
            SystemRecord sr = null;
            Map extendedMD = null;

            try {
                StringTerminatedInputStream stringIn;
                stringIn = new StringTerminatedInputStream(in, XML_TERMINATION);
                extendedMD = NameValueXML.parseXML(stringIn, schema);

                // This is a grody way of telling whether there's a second XML
                // block to be read - our server code leaves two spaces at the
                // end. It would be better to scan past any trailing whitespace
                // and then do the check.

                stringIn = new StringTerminatedInputStream(in, XML_TERMINATION);
                sr = SystemRecord.readSystemRecord(stringIn, oid);
            } catch (XMLException e) {
                throw new ArchiveException("failed to parse XML: " +
                                           e.getMessage(),
                                           e);
            }
            result = new NameValueRecord(sr,
                                         extendedMD,
                                         this, 
                                         false);
        }

        return result;
    }

    /** 
     * Used by internal helper classes to implement the metadata object archive. 
     * This is part of the infrastructure and not useful to users.
     * <p>
     */
    public void writeRecord(MetadataRecord record, OutputStream out)
        throws ArchiveException, IOException {
	((NameValueRecord)record).writeRecord(out);
    }


    /** 
     * Create a NameValueRecord to populate and then store on a @HoneycombProductName@ server. 
     * The content of the record should be populated with the <code>put</code>
     * methods. The NameValueRecord is then uploaded using the storeObject or
     * storeMetadata method.
     */
    public NameValueRecord createRecord(){
	return new NameValueRecord(this);
    }

}
