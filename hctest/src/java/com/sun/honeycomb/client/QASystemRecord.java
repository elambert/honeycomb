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
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.ArchiveException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Map;

/** 
 * This is sub-class of SystemRecord whose sole purpose is to allow 
 * the C API to easily convert the data_structure hc_system_record_t
 * to a Java Class System Record. The only significant difference between the 
 * the two classes is the inclussion of setter methods in this class.
 * Most likely, YOU SHOULD NOT USE THIS CLASS. That being said, do what 
 * what must be done.
 */


public class QASystemRecord extends SystemRecord {



    private ObjectIdentifier oid;
    private ObjectIdentifier linkOID;

    private String digestAlgorithm;
    private byte[] dataContentDigest;
    private byte[] metadataContentDigest;

    private long size;
    private long ctime;
    private long dtime;
    private byte shredMode;
    private long httpResponseCode; 
    private String httpResponseMessage; 

    QASystemRecord (Map map) {
	super (map);
    }

    /**
     * Returns the identifer for this record.
     */
    public ObjectIdentifier getObjectIdentifier() {
        return oid;
    }
    
    public void setObjectIdentifier(ObjectIdentifier newoid) {
	if (newoid == null) {
	    System.out.println("Hmm... my oid is null, how sad!");
	}
        oid = newoid;
    }

    /**
     * Returns the link identifier for this record, if any. A link
     * identifier is the identifier of the data object "pointed" to
     * by this record's object, signifying that the object is a
     * metadata object.
     */
    public ObjectIdentifier getLinkIdentifier() {
        return linkOID;
    }

    public void setLinkIdentifier(ObjectIdentifier loid) {
        linkOID = loid;
    }

    /**
     * Returns the name of the algorithm used to compute the content
     * digest of this record's data and metadata objects.
     */
    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(String sda) {
        digestAlgorithm = sda;
    }

    public void setDataDigest(String dd) {
	    dataContentDigest = ByteArrays.toByteArray(dd);
    }

    /**
     * Returns a byte array representation of the content digest of
     * this record's data object.
     */
    public byte[] getDataDigest() {
        return ByteArrays.copy(dataContentDigest);
    }

    /**
     * Returns a byte array representation of the content digest of
     * this record's metadata object.
     */
    public byte[] getMetadataDigest() {
        return ByteArrays.copy(metadataContentDigest);
    }

    /**
     * Returns the size in bytes of this record's data object.
     */
    public long getSize() {
        return size;
    }

    public void setSize(long new_size) {
        size = new_size;
    }

    /**
     * Returns the creation time of this record as the number of
     * milliseconds since the epoch.
     */
    public long getCreationTime() {
        return ctime;
    }

    public void setCreationTime(long new_ctime) {
        ctime = new_ctime;
    }

    /**
     * Returns the deletion time of this record, if any, as the number of
     * milliseconds since the epoch.
     */
    public long getDeleteTime() {
        return dtime;
    }
    
    public void setDeleteTime(long new_dtime) {
        dtime = new_dtime;
    }

    /**
     * Returns the byte representing how this record's data and metadata
     * objects must be deleted.
     */
    public byte getShredMode() {
        return shredMode;
    }
    public void setShredMode(byte new_shredMode) {
        shredMode = new_shredMode;
    }

    public long getHTTPResponseCode() {
	return httpResponseCode;
    }
    public void setHTTPResponseCode(long rc) {
	httpResponseCode = rc;
    }

    public String getHTTPResponseMessage() {
	return httpResponseMessage;
    }
    public void setHTTPResponseMessage(String msg) {
	httpResponseMessage = msg;
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

        if (!(other instanceof QASystemRecord)) {
            return false;
        }

        return oid.equals(((QASystemRecord)other).oid);
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
        
        if (!(other instanceof QASystemRecord)) {
            return -1;
        }

        return oid.compareTo(((QASystemRecord)other).oid);
    }

    /**
     * Returns a string representation of this record.
     */
    public String toString() {
        StringBuffer result = new StringBuffer("SystemRecord: ");

        result.append("<oid=");
        result.append(oid.toHexString());

        result.append(", size=");
        result.append(size);

        result.append(", ctime=");
        result.append(ctime);

        result.append(">");

        return result.toString();
    }



    public static SystemRecord readSystemRecord(InputStream in)
        throws ArchiveException, IOException {
	return (SystemRecord) readRecord(in);
    }

    /**
     * Returns a newly-created <code>SystemRecord</code> initialized
     * with the contents of the input stream.
     *
     * @throws ArchiveException if the read fails due to an error in
     * the cluster
     * @throws IOException if the read fails due to a communication
     * problem
     */
    public static MetadataRecord readRecord(InputStream in)
        throws ArchiveException, IOException {
        try {
            return new SystemRecord(NameValueXML.parseXML(in));
        } catch (XMLException e) {
            throw new ArchiveException("failed to parse XML: " +
                                       e.getMessage(),
                                       e);
        }
    }

    /**
     * Writes a data stream interpretable by the Honeycomb cluster to
     * the output stream.
     * 
     * @throws ArchiveException if the write fails due to an error in
     * the cluster
     * @throws IOException if the write fails due to a communication
     * problem
     */
    public void writeRecord(MetadataRecord record, OutputStream out)
        throws ArchiveException, IOException {
    }
}
