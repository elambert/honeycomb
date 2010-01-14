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
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import com.sun.honeycomb.common.NoSuchValueException;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.Header;
import org.apache.commons.codec.DecoderException;

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.DataInput;
import java.io.IOException;
import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;


/**
 * Instances of <code>QueryResultSet</code> provide access to 
 * <code>ObjectIdentifier</code>s and metadata returned by calls to
 * <a href="NameValueObjectArchive.html#query(java.lang.String, int)"><code>NameValueObjectArchive.query</code></a>. 
 * The results in a <code>QueryResultSet</code> are stepped through 
 * using the <code>next()</code> method (usage is similar to jdbc). 
 * The <code>ObjectIdentifier</code>s of metadata records matching the query
 * are accessed by calling <code>getObjectIdentifier()</code>.
 * E.g.
 * <pre>
 *
 *      QueryResultSet qrs = archive.query(q, n);
 *      while (qrs.next()) {
 *          ObjectIdentifier oid = qrs.getObjectIdentifier();
 *          ...
 *      }
 * </pre>
 * If selectKeys were specified in the original query, these metadata fields can be 
 * accessed using the typed "getter" methods with each field's name such as 
 * <code>QueryResultSet.getLong(fieldname)</code> or 
 * <code>QueryResultSet.getString(fieldname)</code> .
 */
public class QueryResultSet extends ResultSet {

    QueryResultSet(PreparedStatement query,
                   Connection connection,
                   MetadataObjectArchive mdoa,
                   String requestPath,
                   String queryPath,
                   int resultsPerFetch,
                   short[] cellList) throws IOException, ArchiveException{
        super(query,
              connection,
              mdoa,
              requestPath,
              queryPath,
              resultsPerFetch,
              cellList);
    }

    // Fetch batch of query results
    void read(HttpMethod method)
        throws IOException, ArchiveException {
        boolean binary = true;
        NameValueSchema schema = ((NameValueObjectArchive) mdoa).schema;

        InputStream in = method.getResponseBodyAsStream();

        if (!binary){
            // Parse XML
            throw new RuntimeException("Expected binary data");
        }
        else{

            DataInputStream dataIn = new DataInputStream(in);
            ArrayList results = new ArrayList(100);
            boolean eof = false;
            while (!eof) {
                HashMap values = new HashMap();
                if (isSelect) {

                    int numberOfFields = dataIn.readInt();

                    // check for EOF
                    // Since QueryPlus streams results directly to the 
                    // Response body, it doesn't have a count or a cookie 
                    // until it's too late to add it to the header. 
                    // Instead, they go to the end of the the response body.
                    if (numberOfFields == -1){
                        readFinal(dataIn);
                        eof = true;
                        break;
                    }
                    else{
                        for (int field=0; field < numberOfFields; field++){
                            Object value;
                            String name;
                            try {
                                name = schema.decodeName(dataIn.readUTF());
                                value = schema.decode(dataIn.readUTF());
                            } catch (DecoderException e) {
                                throw new ArchiveException(e);
                            }
                            values.put(name, value);
                        }
                    }
                }
                else {
                    ObjectIdentifier oi = ObjectIdentifier.deserialize(dataIn);
                    if (oi == ObjectIdentifier.ObjectIdentifierEOF) { 
                        readFinal(dataIn);
                        eof = true;
                        break;
                    }
                    else{
                        values.put("system.object_id", oi.toString());
                    }
                } // if/else (isSelect)
                results.add(values);
            } // while (!eof)
            setList(results);
            in.close();
        } // if/else (binary)
    }

    /**
     * Read the final part of a binary query results stream.  This is
     * the part that contains the cookie and the queryIntegrityTime.
     */
    void readFinal(DataInputStream dataIn) throws IOException {

        int cookieLength = dataIn.readInt();
        cookie = null;
        if (cookieLength != -1)
            cookie = dataIn.readUTF();

        long newQueryIntegrityTime;
        try {
            newQueryIntegrityTime = dataIn.readLong();
            if (newQueryIntegrityTime < 0)
                throw new IOException("Received "+
                                      "illegal queryIntegrityTime: "+
                                      newQueryIntegrityTime);
        } catch (EOFException e) {
            //talking to older version - no query
            //integrity time to report.
            //FIXME -- should we introduce better
            //version checking here?
            newQueryIntegrityTime = 0;
            //System.out.println("Server old version.  setting queryIntegrityTime to zero");

        }
        // Take the minimum query integrity time.
        if (newQueryIntegrityTime < queryIntegrityTime) {
            //System.out.println("Changing queryIntegrityTime from "+queryIntegrityTime+" to "+ newQueryIntegrityTime);
            queryIntegrityTime = newQueryIntegrityTime;
        } else {
            //System.out.println("not changing queryIntegrityTime: "+" old="+queryIntegrityTime+" new="+newQueryIntegrityTime);
        }
    }


    /**
     * Returns current <code>ObjectIdentifier</code> for the specified 
     * field from metadata records.
     * <p>
     * The <code>name</code> parameter specifies the name of the 
     * metadata attribute as defined in the schema and as requested 
     * in the query <code>selectKeys</code> parameter.
     */
    public ObjectIdentifier getObjectIdentifier() {
        Object o = get("system.object_id");
        String oidString;

        if (o instanceof String) {
            oidString = (String) o;
        } else  if (o instanceof ExternalObjectIdentifier) {
            oidString = ((ExternalObjectIdentifier)o).toString();
        } else throw new RuntimeException("don't know how to get objectid from object: "+o);

        return new ObjectIdentifier(oidString);
    }

    /**
     * Convenience method which returns the value for a given attribute translated to a <code>String</code>.
     */
    public String getAsString(String key) {
        NameValueSchema.ValueType type = 
            (NameValueSchema.ValueType)
               (((NameValueObjectArchive)mdoa).schema).getType(key);
        if (type == null)
            throw new IllegalArgumentException ("No such metadata name in schema: " + key);
        else if (type == NameValueSchema.STRING_TYPE ||
                 type == NameValueSchema.CHAR_TYPE) 
            return getString(key);
        else if (type == NameValueSchema.LONG_TYPE)
            return CanonicalEncoding.encode(new Long(getLong(key)));
        else if (type == NameValueSchema.DOUBLE_TYPE)
            return CanonicalEncoding.encode(new Double(getDouble(key)));
        else if (type == NameValueSchema.BINARY_TYPE)
            return CanonicalEncoding.encode(getBinary(key));
        else if (type == NameValueSchema.DATE_TYPE)
            return CanonicalEncoding.encode(getDate(key));
        else if (type == NameValueSchema.TIME_TYPE)
            return CanonicalEncoding.encode(getTime(key));
        else if (type == NameValueSchema.TIMESTAMP_TYPE)
            return CanonicalEncoding.encode(getTimestamp(key));
        else if (type == NameValueSchema.OBJECTID_TYPE)
            return getObjectIdentifier().toString();
        else
            throw new IllegalArgumentException("Unknown key: " + key);

    }

    /**
     * Returns current <code>String</code> for the specified field from 
     * metadata records. 
     * <p>
     * The <code>name</code> parameter specifies the name of the 
     * metadata attribute as defined in the schema and as requested 
     * in the query <code>selectKeys</code> parameter.
     *
     * @throws NoSuchValueException if the specified field is not found or is
     * the wrong type.
     */    
    private Object get(String name) {
        if (!currentObject.containsKey(name)) {
            throw new NoSuchValueException(name + " not in result set");
        }
        Object o = currentObject.get(name);
        if (o == null) {
            throw new NoSuchValueException(name + " not in result set.");
        }
        return o;
    }

    /**
     * Returns current <code>String</code> for the specified field from
     * metadata records.  Used for <code>String</code>, <code>Char</code>, 
     * and <code>ObjectIdentifier</code> types.
     * <p>
     * The <code>name</code> parameter specifies the name of the
     * metadata attribute as defined in the schema and as requested
     * in the query <code>selectKeys</code> parameter.
     */
    public String getString(String name) {
        ((NameValueObjectArchive)mdoa).schema.validate(name, NameValueSchema.CHAR_TYPE,
                                                       NameValueSchema.STRING_TYPE,
                                                       NameValueSchema.OBJECTID_TYPE);
        Object o = get(name);
        if (o instanceof String) {
            String s = (String) o;
            return s;
        } else if (o instanceof byte[]) {
            ((NameValueObjectArchive)mdoa).schema.validate(name, NameValueSchema.OBJECTID_TYPE);
            byte[] bytes = (byte[]) o;
            return CanonicalEncoding.encode(bytes);
        } else  if (o instanceof ExternalObjectIdentifier) {
            return getObjectIdentifier().toString();
        } else
            throw new NoSuchValueException(name + " has incorrect type (expected string)");
    }
    
    /**
     * Returns current <code>double</code> for the specified field from 
     * metadata records.
     * <p>
     * The <code>name</code> parameter specifies the name of the 
     * metadata attribute as defined in the schema and as requested 
     * in the query <code>selectKeys</code> parameter.
     *
     * @throws NoSuchValueException if the specified field is not found or is
     * the wrong type.
     */
    public double getDouble(String name) {
        ((NameValueObjectArchive)mdoa).schema.validate(name, NameValueSchema.DOUBLE_TYPE);
        Object o = get(name);
        if (o instanceof Double) {
            return ((Double)o).doubleValue();
        } else if (o instanceof String) {
            String s = (String) o;
            return Double.parseDouble(s);
        } else
            throw new NoSuchValueException(name + " has incorrect type (expected Double)");
    }
    
    /**
     * Returns current <code>long</code> for the specified field from 
     * metadata records.
     * <p>
     * The <code>name</code> parameter specifies the name of the 
     * metadata attribute as defined in the schema and as requested 
     * in the query <code>selectKeys</code> parameter.
     *
     * @throws NoSuchValueException if the specified field is not found or is
     * the wrong type.
     */    
    public long getLong(String name) {
        ((NameValueObjectArchive)mdoa).schema.validate(name, NameValueSchema.LONG_TYPE);
        Object o = get(name);
        if (o instanceof Long) {
            return ((Long)o).longValue();
        } else if (o instanceof String) {
            String s = (String) o;
            return Long.parseLong(s);
        } else
             throw new NoSuchValueException(name + " has incorrect type (expected long)");
    }
    
    /**
     * Returns current <code>Timestamp</code> for the specified field from 
     * metadata records.
     * <p>
     * The <code>name</code> parameter specifies the name of the 
     * metadata attribute as defined in the schema and as requested 
     * in the query <code>selectKeys</code> parameter.
     *
     * @throws NoSuchValueException if the specified field is not found or is
     * the wrong type.
     */    
    public Timestamp getTimestamp(String name) {
        ((NameValueObjectArchive)mdoa).schema.validate(name, NameValueSchema.TIMESTAMP_TYPE);
        Object o = get(name);
        if (o instanceof Timestamp) {
            return (Timestamp)o;
        } else if (o instanceof String) {
            String s = (String) o;
            return CanonicalEncoding.decodeTimestamp(s);
        } else
            throw new NoSuchValueException(name + " has incorrect type (expected timestamp)");
    }
    
    /**
     * Returns current <code>Time</code> for the specified field from 
     * metadata records.
     * <p>
     * The <code>name</code> parameter specifies the name of the 
     * metadata attribute as defined in the schema and as requested 
     * in the query <code>selectKeys</code> parameter.
     *
     * @throws NoSuchValueException if the specified field is not found or is
     * the wrong type.
     */     
    public Time getTime(String name) {
        ((NameValueObjectArchive)mdoa).schema.validate(name, NameValueSchema.TIME_TYPE);
        Object o = get(name);
        if (o instanceof Time) {
            return (Time) o;
        } else if (o instanceof String) {
            String s = (String) o;
            return CanonicalEncoding.decodeTime(s);
        } else
            throw new NoSuchValueException(name + " has incorrect type (expected time)");
    }
    /**
     * Returns current <code>Date</code> for the specified field from 
     * metadata records.
     * <p>
     * The <code>name</code> parameter specifies the name of the 
     * metadata attribute as defined in the schema and as requested 
     * in the query <code>selectKeys</code> parameter.
     *
     * @throws NoSuchValueException if the specified field is not found or is
     * the wrong type.
     */
    public Date getDate(String name) {
        ((NameValueObjectArchive)mdoa).schema.validate(name, NameValueSchema.DATE_TYPE);
        Object o = get(name);
        if (o instanceof Date) {
            return (Date)o;
        } else if (o instanceof String) {
            String s = (String) o;
            return CanonicalEncoding.decodeDate(s);
        } else
            throw new NoSuchValueException(name + " has incorrect type (expected date)");
    }

    /**
     * Returns current byte array for the specified field from metadata records.
     * <p>
     * The <code>name</code> parameter specifies the name of the 
     * metadata attribute as defined in the schema and as requested 
     * in the query <code>selectKeys</code> parameter.
     *
     * @throws NoSuchValueException if the specified field is not found or is
     * the wrong type.
     */
    public byte[] getBinary(String name) {
        ((NameValueObjectArchive)mdoa).schema.validate(name, NameValueSchema.BINARY_TYPE, NameValueSchema.OBJECTID_TYPE);
        Object o = get(name);
        if (o instanceof byte[]) {
            return (byte[])o;
        } else if (o instanceof String) {
            String s = (String) o;
            return CanonicalEncoding.decodeBinary(s);
        } else  if (o instanceof ExternalObjectIdentifier) {
            return getObjectIdentifier().getBytes();
        } else
            throw new NoSuchValueException(name + " has incorrect type (expected binary)");
    }

}
