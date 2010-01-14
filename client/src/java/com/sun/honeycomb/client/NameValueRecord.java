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

import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.common.NoSuchValueException;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.commons.codec.binary.Base64;

import java.io.Serializable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import java.sql.Timestamp;
import java.sql.Time;
import java.sql.Date;

/**
 * Instances of <code>NameValueRecord</code> represent 
 * metadata used by the @HoneycombProductName@ 
 * to store and index user-extensible
 * lists of name-value pairs. For convenience, instances of
 * <code>NameValueRecord</code> also contain references to the
 * <code>SystemRecord</code>s of the objects they represent.
 * <p>
 * If <code>archive</code> is an instance of <code>NameValueObjectArchive</code>, 
 * <code>NameValueRecord nvr = archive.createRecord()</code> is equivalent to
 * <code>NameValueRecord nvr = new NameValueRecord(archive)</code>.
 * 
 * @see <a href="../NameValueObjectArchive.html">NameValueObjectArchive</a>
 * @see <a href="SystemRecord.html">SystemRecord</a>
 */
public class NameValueRecord implements MetadataRecord, Comparable, Serializable {

    private SystemRecord record;
    private Map map;
    private NameValueObjectArchive archive;

    /**
     * Initializes a new, empty <code>NameValueRecord</code>.
     */
    NameValueRecord(NameValueObjectArchive nvoa) {
        archive = nvoa;
        map = new HashMap();
    }

    NameValueRecord(SystemRecord newRecord, Map newMap, NameValueObjectArchive nvoa, boolean copy) {
        record = newRecord;
        archive = nvoa;
        map = (copy) ? new HashMap() : newMap;
        putAll(newMap);
    }

    Map getMap() {
        return map;
    }

    /**
     * Returns the <code>SystemRecord</code> corresponding to this record.
     */
    public SystemRecord getSystemRecord() {
        return record;
    }


    /**
     * Returns the type for the named metadata value.
     */
    public NameValueSchema.ValueType getAttributeType (String key){
        NameValueSchema.ValueType type = archive.schema.getType(key);
        if (type == null)
            throw new IllegalArgumentException ("No such metadata name in schema: " + key);
        else
            return type;
    }



    /**
     * Sets an <code>Object</code> value for the given key. The type of the 
     * object must match the type of the field.
     * This value is not uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, Object o) {

        if (o instanceof Long)
            put(key, (Long)o);
        else if (o instanceof Double)
            put(key, (Double)o);
        else if (o instanceof String)
            put(key, (String)o);
        else if (o instanceof Time)
            put(key, (Time)o);
        else if (o instanceof Timestamp)
            put(key, (Timestamp)o);
        else if (o instanceof Date)
            put(key, (Date)o);
        else if (o instanceof byte[])
            put(key, (byte[])o);
        else
            throw new IllegalArgumentException("Unsupported type " + o.getClass());
  }


    /**
     * Sets a value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     * <p>
     * The field named by the key may be of any type. Values are translated 
     * from string into object according to the "canonical string format" 
     * as described in the Client API Reference Manual.
     * <p>
     * In particular, this is the main method for assigning values to fields 
     * of type string and char.
     */
    public void put(String key, String value) {
        // It is OK to put a String value into any type. 
        put(key, value, true);
    }


    /**
     * Sets a <code>String</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called. If parameter 
     * <code>unicode</code> is false, the schema is validated before setting the 
     * key value.
     *
     * @deprecated Replaced by {@link #put(String, double)}
     */
    protected void put(String key, String value, boolean unicode) {
        // It is OK to put a String value into any type.
        if (!unicode) {
            archive.schema.validate(key, NameValueSchema.CHAR_TYPE);
        }
        map.put(key, value);
    }

    /**
     * Sets a <code>double</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, double value) {
        archive.schema.validate(key, NameValueSchema.DOUBLE_TYPE);
        // Doubles are represented in IEEE 754 floating-point "double format" bit layout
        map.put(key, new Double(value));
    }

    /**
     * Sets a <code>double</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, Double value) {
        archive.schema.validate(key, NameValueSchema.DOUBLE_TYPE);
        // Doubles are represented in IEEE 754 floating-point "double format" bit layout
        map.put(key, value);
    }


    /**
     * Sets a <code>long</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, long value) {
        archive.schema.validate(key, NameValueSchema.LONG_TYPE);
        map.put(key, new Long(value));
    }
    /**
     * Sets a <code>long</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, Long value) {
        archive.schema.validate(key, NameValueSchema.LONG_TYPE);
        map.put(key, value);
    }

    /**
     * Sets a <code>Date</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, Date value) {
        archive.schema.validate(key, NameValueSchema.DATE_TYPE);
        map.put(key, value);
    }

    /**
     * Sets a <code>Time</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, Time value) {
        archive.schema.validate(key, NameValueSchema.TIME_TYPE);
        map.put(key, value);
    }

    /**
     * Sets a <code>Timestamp</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, Timestamp value) {
        archive.schema.validate(key, NameValueSchema.TIMESTAMP_TYPE);
        map.put(key, value);
    }

    /**
     * Sets a <code>binary</code> value for the given key. This value is not 
     * uploaded to the server until
     * {@link
     * NameValueObjectArchive#storeMetadata(ObjectIdentifier, NameValueRecord)
     * NameValueObjectArchive.storeMetadata} or
     * {@link
     * NameValueObjectArchive#storeObject(ReadableByteChannel, NameValueRecord)
     * NameValueObjectArchive.storeObject} is called.
     */
    public void put(String key, byte[] value) {
        archive.schema.validate(key, 
                                NameValueSchema.BINARY_TYPE, 
                                NameValueSchema.OBJECTID_TYPE);
        map.put(key, value);
    }


    private final static void barf(String name, String type){
        throw new IllegalArgumentException("Value of '" + name + 
                                           "' must be of class " + type);
    }

    /**
     * Convenience method to set multiple values. 
     * <p> 
     * Map entries are {name, value} where the name is 
     * always of type String. The value can be of type String,
     * in which case a conversion is made to the appropriate
     * type if necessary, or of any of the types in the schema.
     *
     * @see NameValueRecord#put(String, String)
     * @see NameValueRecord#put(String, double)
     * @see NameValueRecord#put(String, long)
     * @see NameValueRecord#put(String, byte[])
     * @see NameValueRecord#put(String, Date)
     * @see NameValueRecord#put(String, Time)
     * @see NameValueRecord#put(String, Timestamp)
     */
    public void putAll(Map map) throws IllegalArgumentException {

        Iterator i = map.entrySet().iterator();

        while (i.hasNext()){
            Map.Entry e = (Map.Entry) i.next();
            String key = (String) e.getKey();
            Object val = e.getValue();

            NameValueSchema.ValueType type = getAttributeType(key);

            if (type == null)
                throw new IllegalArgumentException("Unknown type " + key);

            if (type == NameValueSchema.STRING_TYPE ||
                       type == NameValueSchema.CHAR_TYPE){
                // FIXME: enforce prohibition of embedded null?
                // FIXME: enforce Char includes only Latin-1?
                if (val instanceof String)
                    put(key, (String)val);
                else 
                    barf(key, "String");
            }
            else if (type == NameValueSchema.LONG_TYPE){
                if (val instanceof Long)
                    put(key, (Long)val);
                else if (val instanceof String)
                    put(key, Long.parseLong((String)val));
                else 
                    barf(key, "Long");
            }
            else if (type == NameValueSchema.DOUBLE_TYPE){
                if (val instanceof Double)
                    put(key, (Double)val);
                else if (val instanceof String)
                    put(key, Double.parseDouble((String)val));
                else 
                    barf(key, "Double");
            }
            else if (type == NameValueSchema.TIME_TYPE){
                if (val instanceof Time)
                    put(key, (Time)val);
                else if (val instanceof String)
                    put(key, Time.valueOf((String)val));
                else 
                    barf(key, "Time");
            }
            else if (type == NameValueSchema.TIMESTAMP_TYPE){
                if (val instanceof Timestamp)
                    put(key, (Timestamp)val);
                else if (val instanceof String)
//                    put(key, Timestamp.valueOf((String)val));
                    put(key, CanonicalEncoding.decodeTimestamp((String)val));
                else 
                    barf(key, "Timestamp");
            }
            else if (type == NameValueSchema.DATE_TYPE){
                if (val instanceof Date)
                    put(key, (Date)val);
                else if (val instanceof String)
                    put(key, Date.valueOf((String)val));
                else 
                    barf(key, "Date");
            }
            else if (type == NameValueSchema.BINARY_TYPE ||
                     type == NameValueSchema.OBJECTID_TYPE){
                if (val instanceof byte[])
                    put(key, (byte[])val);
                else if (val instanceof String)
                    put(key, ByteArrays.toByteArrayLeftJustified((String)val));
                else 
                    barf(key, "Binary");
            }
        }
    }
    


//     * Long -- L followed by a signed decimal value
//     * Double -- D followed by exactly 16 hex digits   
//     * Char - C followed by quoted-printable encoded 8-bit character values
//     * String - S followed by quoted-printable encoded character values that may
//       include Unicode characters
//     * Timestamp - T followed by a signed decimal value representing microseconds
//       since the Epoch.
//     * Date - d followed by a signed decimal value representing days since the Epoch
//     * Time - t followed by an unsigned decimal value representing the number of
//       microseconds (e.g. past midnight)
//     * Binary - B followed by a BASE64-encoded string


    /**
     * Convenience method which returns the value for a given attribute translated to a <code>String</code>.
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     *<p>
     * Values are translated from string into object according to the "canonical 
     * string format" as described in the Client API Reference 
     * Manual.
     */
    public String getAsString(String key) {
        NameValueSchema.ValueType type = (NameValueSchema.ValueType)archive.schema.getType(key);

        if (type == null)
            throw new IllegalArgumentException ("No such metadata name in schema: " + key);
        else if (type == NameValueSchema.LONG_TYPE)
            return Long.toString(getLong(key));
        else if (type == NameValueSchema.DOUBLE_TYPE)
            return Double.toString(getDouble(key));
        else if (type == NameValueSchema.STRING_TYPE ||
                 type == NameValueSchema.CHAR_TYPE)
            return getString(key);
        else if (type == NameValueSchema.BINARY_TYPE ||
                 type == NameValueSchema.OBJECTID_TYPE)
            return ByteArrays.toHexString(getBinary(key));
        else if (type == NameValueSchema.DATE_TYPE)
            return getDate(key).toString();
        else if (type == NameValueSchema.TIME_TYPE)
            return getTime(key).toString();
        else if (type == NameValueSchema.TIMESTAMP_TYPE)
            return getTimestamp(key).toString();
        else
            throw new IllegalArgumentException("Unknown key: " + key);

    }

    /**
     * Returns the underlying object value for the named attribute.
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     */
    public Object getObject(String key) {
        return map.get(key);
    }

    private Object get(String key) {
        if (!map.containsKey(key)) {
            throw new NoSuchValueException(key + " not in NameValueRecord map");
        }
        Object o = map.get(key);
        if (null == o) {
            throw new NoSuchValueException(key + 
                    " has a null value in the NameValueRecord map"); 
        }
        return o;
    }

    /**
     * Returns the value for the named attribute. The attribute may be of 
     * type string, char, or objectid. 
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     */
    public String getString(String key) {
        archive.schema.validate(key, NameValueSchema.STRING_TYPE,
                                NameValueSchema.CHAR_TYPE,
                                NameValueSchema.OBJECTID_TYPE);
        Object o = get(key);
        if (o instanceof String) {
            return (String) o;
        } else if (o instanceof byte[]) {
            archive.schema.validate(key, NameValueSchema.OBJECTID_TYPE);
            byte[] bytes = (byte[]) o;
            return CanonicalEncoding.encode(bytes);
        } else throw new RuntimeException(key + " has an unexpected type");
    }

    /**
     * Returns the value for the named <code>long</code> attribute.
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     */
    public long getLong(String key) {
        archive.schema.validate(key, NameValueSchema.LONG_TYPE);
        return ((Long) get(key)).longValue();
    }

    /**
     * Returns the value for the named <code>double</code> attribute.
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     */
    public double getDouble(String key) {
        archive.schema.validate(key, NameValueSchema.DOUBLE_TYPE);
        // Doubles are represented in IEEE 754 floating-point "double format" bit layout
        return ((Double) get(key)).doubleValue();
    }


    /**
     * Returns the value for the named <code>Date</code> attribute.
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     */
    public Date getDate(String key) {
        archive.schema.validate(key, NameValueSchema.DATE_TYPE);
        return (Date) get(key);
    }

    /**
     * Returns the value for the named <code>Time</code> attribute.
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     */
    public Time getTime(String key) {
        archive.schema.validate(key, NameValueSchema.TIME_TYPE);
        return (Time) get(key);
    }

    /**
     * Returns the value for the named <code>Timestamp</code> attribute.
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     */
    public Timestamp getTimestamp(String key) {
        archive.schema.validate(key, NameValueSchema.TIMESTAMP_TYPE);
        return (Timestamp) get(key);
    }

    /**
     * Returns the value for the named attribute. The attribute may be of 
     * type binary or objectid. 
     * <p>
     * 'key' is the name of the metadata attribute as defined in the schema. 
     */
    public byte[] getBinary(String key) {
        archive.schema.validate(key, NameValueSchema.BINARY_TYPE,
                                NameValueSchema.OBJECTID_TYPE);
        return (byte[]) get(key);
    }


    /**
     * Returns the list of keys present in this record. These are the valid key parameters to 
     * {@link #getString}, {@link #getLong}, {@link #getDate}, {@link #getObject}, 
     * {@link #getTime}, {@link #getTimestamp}, {@link #getBinary}, or {@link #getDouble}.
     */
    public String[] getKeys() {
        int size = (map != null) ? map.size() : 0;
        String[] result = new String[size];

        if (size > 0) {
            Iterator keys = map.keySet().iterator();
            for (int i = 0; keys.hasNext(); i++) {
                result[i] = (String)keys.next();
            }
        }

        return result;
    }

    /**
     * Returns a hash code value for this record.
     */
    public int hashCode() {
        return (record != null) ? record.hashCode() : System.identityHashCode(this);
    }

    /**
     * Indicates whether the other record is "equal to" this one.
     */
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (this == other) {
            return true;
        }

        if (!(other instanceof NameValueRecord)) {
            return false;
        }

        NameValueRecord otherRecord = (NameValueRecord)other;

        if ((record != null && otherRecord.record == null) ||
            (record == null && otherRecord.record != null) ||
            (record != null && otherRecord.record != null &&
             !record.equals(otherRecord.record))) {
            return false;
        }

        if ((map != null && otherRecord.map == null) ||
            (map == null && otherRecord.map != null) ||
            (map != null && otherRecord.map != null &&
             !map.equals(otherRecord.map))) {
            return false;
        }

        return true;
    }

    /**
     * Compares this record with the one specified for order. Returns a
     * negative integer, zero, or a positive integer as this record is less
     * than, equal to, or greater than the specified one.
     */
    public int compareTo(Object other) {
        if (other == null) {
            return 1;
        }

        if (this == other) {
            return 0;
        }

        if (!(other instanceof NameValueRecord)) {
            return -1;
        }

        NameValueRecord otherRecord = (NameValueRecord)other;

        if (record != null) {
            if (otherRecord.record != null) {
                return record.compareTo(otherRecord.record);
            } else {
                return 1;
            }
        } else if (otherRecord.record != null) {
            return -1;
        }

        return 0;
    }

    /**
     * Returns a <code>String</code> representation of this record.
     */
    public String toString() {
        StringBuffer result = new StringBuffer("NameValueRecord: ");

        if (record != null) {
            result.append("<");
            result.append(record.toString());
            result.append(">");
        }

        if (map != null) {
            result.append("<map: ");
            result.append(map.toString());
            result.append(">");
        }

        return result.toString();
    }

    


    /**
     * Write a data stream interpretable by the @HoneycombProductName@ server to
     * the output stream. This is the client component for uploading metadata.
     * 
     * @throws ArchiveException if the write fails due to an export PS1='$PWD# ';unalias ls
     * the server
     * @throws IOException if the write fails due to a communication
     * problem
     */
    void writeRecord(OutputStream out)
        throws ArchiveException, IOException{
        writeRecord(out, false);
    }

    void writeRecord(OutputStream out, boolean binary)
        throws ArchiveException, IOException {

        if (map != null) {
            if (binary){
                ObjectOutputStream oos = new ObjectOutputStream (out);
                oos.writeObject(map);
                oos.flush();
                oos.close();
            }
            else{
                NameValueXML.createXML(map, out, archive.schema);
            }
        }
    }

}
