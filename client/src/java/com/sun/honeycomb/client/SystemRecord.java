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

import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.ArchiveException;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.DecoderException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Map;

/**
 * Instances of <code>SystemRecord</code> system information
 * about data and metadata records stored on the @HoneycombProductName@
 * server.
 * 
 * @see <a href="SystemCache.html">SystemCache</a>
 */
public class SystemRecord implements MetadataRecord, Comparable, Serializable {



    private ObjectIdentifier oid;
    private ObjectIdentifier linkOID;

    private String digestAlgorithm;
    private byte[] dataContentDigest;
    private byte[] metadataContentDigest;

    private long size;
    private long ctime;
    private long dtime;
    private byte shredMode;
    private boolean queryReady;

    SystemRecord(Map map, ObjectIdentifier oid) {
        this.oid = oid;
        initSystemRecord(map);
    }

    SystemRecord(Map map) {
        String value = (String)map.get(ProtocolConstants.IDENTIFIER_TAG);
        if (value != null) {
            oid = new ObjectIdentifier(value);
        }
        initSystemRecord(map);
    }

    private void initSystemRecord(Map map) {
        String value = (String)map.get(ProtocolConstants.LINK_TAG);
        if (value != null) {
            linkOID = new ObjectIdentifier(value);
        }

        String sizeString = (String)map.get(ProtocolConstants.SIZE_TAG);
        String ctimeString = (String)map.get(ProtocolConstants.CTIME_TAG);
        try {
            if (sizeString != null) {
                size = Long.parseLong(sizeString);
            }

            if (ctimeString != null) {
                ctime = Long.parseLong(ctimeString);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid value: " + e);
        }

        Object digestObj = map.get(ProtocolConstants.DATA_CONTENT_DIGEST_TAG);
        if (digestObj == null)
            dataContentDigest = null;
        else if (digestObj instanceof byte[])
            dataContentDigest = (byte[]) digestObj;
        else if (digestObj instanceof String) 
            dataContentDigest = ByteArrays.toByteArray((String)digestObj);
        else throw new RuntimeException("Unexpected Type");
        digestAlgorithm = (String)map.get(ProtocolConstants.HASH_ALGORITHM_TAG);

        String queryReadyString = (String) map.get(ProtocolConstants.QUERY_READY_TAG);
        if (queryReadyString != null) {
            try {
                long queryReadyValue = Long.parseLong(queryReadyString);
                queryReady = queryReadyValue == 1 ? true : false;
            } catch (NumberFormatException ignored) {
                //leave queryReady set to false
            }
        } // if
    }

    /**
     * Returns the identifier for this record.
     */
    public ObjectIdentifier getObjectIdentifier() {
        return oid;
    }

    /**
     * Returns the link identifier for this record, if any. A link
     * identifier is the identifier of the data object "pointed" to
     * by this record's object, signifying that the object is a
     * metadata object.
     */
    ObjectIdentifier getLinkIdentifier() {
        return linkOID;
    }

    /**
     * Returns the name of the algorithm used to compute the content
     * digest of this record's data and metadata objects.
     */
    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    /**
     * Returns a byte array representation of the content digest of
     * this record's data object.
     */
    public byte[] getDataDigest() {
        return ByteArrays.copy(dataContentDigest);
    }

    /**
     * Returns the size in bytes of this record's data object.
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the creation time of this record as the number of
     * milliseconds since the epoch.
     */
    public long getCreationTime() {
        return ctime;
    }

    /**
     * Returns the deletion time of this record, if any, as the number of
     * milliseconds since the epoch.
     */
    public long getDeleteTime() {
        return dtime;
    }

    /**
     * Returns <code>true</code> if this record was successfully
     * inserted in the query engine at the time of store, and is hence
     * available for query.  Objects that were not inserted into the
     * query engine at time of store are called <i>store index
     * exceptions</i>.  Until they are resolved, store index
     * exceptions may or may not show up in the result sets of queries
     * that match the store.  A store index exception is
     * <i>resolved</i> once the metadata for that object has been
     * successfully inserted into the query engine, after which the
     * object will definitely show up in the result sets of queries
     * that match the store.
     * <br> 
     * The <code>NameValueObjectArchive.checkIndexed</code> method may
     * be used to attempt to resolve a store index exception under
     * program control.  Store index exceptions will also be resolved
     * automatically (eventually) by ongoing system healing.  <br> The
     * <code>isIndexed</code> value is valid only for SystemRecords
     * that are returned from the <code>archive.storeObject</code> or
     * <code>archive.storeMetadata</code> operation.  The
     * <code>isIndexed</code> value is not meaningful for
     * SystemRecords that are returned from the
     * <code>archive.retrieveMetadata</code> operation.
     * @see NameValueObjectArchive#checkIndexed
     * @see ResultSet#isQueryComplete
     * @see ResultSet#getQueryIntegrityTime
     */
    public boolean isIndexed() {
        return queryReady;
    }

    /**
     * Returns the byte representing how this record's data and metadata
     * objects must be deleted.
     */
    protected byte getShredMode() {
        return shredMode;
    }

    /**
     * Returns a hash code value for this record.
     */
    public int hashCode() {
        return oid.hashCode();
    }

    /**
     * Indicates whether the other record is "equal to" this one.
     */
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null) {
            return false;
        }

        if (!(other instanceof SystemRecord)) {
            return false;
        }

        return oid.equals(((SystemRecord)other).oid);
    }

    /**
     * Compares this record with the one specified for order. Returns a
     * negative integer, zero, or a positive integer as this record is less
     * than, equal to, or greater than the specified one.
     */
    public int compareTo(Object other) {
        if (other == this) {
            return 0;
        }

        if (other == null) {
            return 1;
        }
        
        if (!(other instanceof SystemRecord)) {
            return -1;
        }

        return oid.compareTo(((SystemRecord)other).oid);
    }

    /**
     * Returns a string representation of this record.
     */
    public String toString() {
        StringBuffer result = new StringBuffer("SystemRecord: ");

        result.append("<oid=");
        result.append(oid.toString());

        result.append(", size=");
        result.append(size);

        result.append(", ctime=");
        result.append(ctime);

        result.append(", isIndexed=");
        result.append(queryReady);

        result.append(">");

        return result.toString();
    }


    private static class StringEncoding extends Encoding{
      private StringEncoding(){
          super(Encoding.IDENTITY);
      }
      public String encode (Object o) throws EncoderException{
          return o.toString();
      }

      public Object decode(String encoded) throws DecoderException{
          return encoded;
      }
    }
    private static StringEncoding se = new StringEncoding();

    static SystemRecord readSystemRecord(InputStream in)
        throws ArchiveException, IOException {
        try {
            return new SystemRecord(NameValueXML.parseXML(in, se));
        } catch (XMLException e) {
            throw new ArchiveException("failed to parse XML: " +
                                       e.getMessage(),
                                       e);
        }
    }

    static SystemRecord readSystemRecord(InputStream in, ObjectIdentifier oid)
        throws ArchiveException, IOException {
        try {
            return new SystemRecord(NameValueXML.parseXML(in), oid);
        } catch (XMLException e) {
            throw new ArchiveException("failed to parse XML: " +
                                       e.getMessage(),
                                       e);
        }
    }

    /**
     * Writes a data stream interpretable by the server to
     * the output stream.
     * <p>
     * This is an internal helper method.
     * 
     * @throws ArchiveException if the write fails due to an error in
     * the cluster
     * @throws IOException if the write fails due to a communication
     * problem
     */
    protected void writeRecord(MetadataRecord record, OutputStream out)
        throws ArchiveException, IOException {
    }

}
