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

import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.ArchiveException;

import java.io.DataInput;
import java.io.InputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;

import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

/**
 * An instance of <code>NameValueSchema</code> represents information about
 * the Name-Value metadata the @HoneycombProductName@ uses to index data. 
 * It can be used to 
 * enumerate the fields available in the schema as <code>Attribute</code>s. 
 * Each <code>Attribute</code> has a name and a type.
 * <p>
 * See the Admin guide for information on how to define <code>Attribute</code>s.
 */
public class NameValueSchema extends Encoding implements CacheConfiguration, Comparable, Serializable {

    private Map map = new HashMap();

    /**
     * Representation of any of our documented types (<code>String</code>, 
     * <code>Char</code>, <code>Binary</code>, <code>Date</code>, 
     * <code>Time</code>, <code>Timestamp</code>, <code>Long</code>, 
     * <code>Double</code>) as well as <code>ObjectIdentifier</code>.
     */
    public static class ValueType{
        ValueType(Class javaType, int ordinalRank, String name) {
            this.javaType = javaType; 
            this.ordinalRank = ordinalRank;
            this.name = name;
        }
        public final Class javaType;
        public final int ordinalRank;
        public final String name;
        public String toString(){return name;}
    }

     /**
      * The type corresponding to a long.
      */
     public static final ValueType LONG_TYPE  = new ValueType(Long.class, 1, "long");

     /**
      * The type corresponding to a double.
      */
     public static final ValueType DOUBLE_TYPE = new ValueType(Double.class, 2, "double");

     /**
      * The type corresponding to a unicode string.
      */
     public static final ValueType STRING_TYPE = new ValueType(String.class, 3, "string");

     /**
      * The type corresponding to a Latin1 string.
      */
     public static final ValueType CHAR_TYPE = new ValueType(String.class, 4, "char");

     /**
      * The type corresponding to a date.
      */
     public static final ValueType DATE_TYPE = new ValueType(Date.class, 5, "date");

     /**
      * The type corresponding to a time.
      */
     public static final ValueType TIME_TYPE = new ValueType(Time.class, 6, "time");

     /**
      * The type corresponding to a timestamp.
      */
     public static final ValueType TIMESTAMP_TYPE = new ValueType(Timestamp.class, 7, "timestamp");

     /**
      * The type corresponding to a byte array.
      */
     public static final ValueType BINARY_TYPE = new ValueType(byte[].class, 8, "binary");

    /**
     * The type corresponding to system.object_id.
     */
    public static final ValueType OBJECTID_TYPE = new ValueType(byte[].class,9,"objectid");

    public final static ValueType[] TYPES = {LONG_TYPE, DOUBLE_TYPE, 
                                             CHAR_TYPE, STRING_TYPE, 
                                             DATE_TYPE, TIME_TYPE, TIMESTAMP_TYPE,
                                             BINARY_TYPE, OBJECTID_TYPE};

    private Attribute[] attributes;

    NameValueSchema(Attribute[] attributes) {
        super(BASE_64);        // Set up BASE64 encoding

        if (attributes == null) {
            attributes = new Attribute[0];
        }
        this.attributes = attributes;
        
        for (int i=0; i<this.attributes.length; i++)
            map.put(attributes[i].getName(), attributes[i]);

    }


    static NameValueSchema deserialize(DataInput dataIn) throws IOException {
        Attribute[] attrs = null;

        int count = dataIn.readInt();
        if (count > 0) {
            attrs = new Attribute[count];

            for (int i = 0; i < count; i++) {
                attrs[i] = Attribute.deserialize(dataIn);
            }
        }

        return new NameValueSchema(attrs);
    }

    protected static NameValueSchema parse(InputStream input) 
        throws ArchiveException, IOException {

        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            SAXParser parser = parserFactory.newSAXParser();
            ParserHandler handler = new ParserHandler();
            parser.parse(input, handler);

            return(new NameValueSchema(handler.getAttributes()));

        } catch (SAXException e) {
            ArchiveException newe = new ArchiveException("Failed to parse the schema (SAX) ["+
                                                           e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (ParserConfigurationException e) {
            ArchiveException newe = new ArchiveException("Failed to parse the schema ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    /**
     * Returns the list of <code>Attributes</code> currently supported by
     * the schema previously downloaded.
     */
    public Attribute[] getAttributes() {
        Attribute[] result = new Attribute[attributes.length];
        System.arraycopy(attributes, 0, result, 0, attributes.length);
        return result;
    }

    protected String describe(){
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < attributes.length; i++)
            sb.append(attributes[i] + " ");
        return sb.toString();
    }



    /**
     * Returns the client-side type of specified attribute.
     */
    public ValueType getType (String name){
        Attribute attr = (Attribute)map.get(name);
        if (attr == null)
            return null;
        return attr.getType();
    }

    /**
     * Return this attribute's length according to the schema (meaningful
     * only for types String, Char, and Binary). Returns -1 if length is 
     * not meaningful for that attribute.
     */
    public int getLength (String name){
        Attribute attr = (Attribute)map.get(name);
        if (attr == null)
            return -1;
        return attr.getLength();
    }

    protected void validate (String key, ValueType argType){
        validate (key, argType, null);
    }

    protected void validate (String key, ValueType argType, 
                          ValueType argType2){
        validate(key, argType, argType2, null);
    }
    protected void validate (String key, 
                          ValueType argType, 
                          ValueType argType2,
                          ValueType argType3){
        Attribute attr = (Attribute)map.get(key);
        if (attr == null)
            throw new IllegalArgumentException ("No such metadata name in schema: " + key);

        ValueType type = attr.getType();
        if (type == argType3)
            return;
        if (type == argType2)
            return;
        else if (type != argType)
            throw new IllegalArgumentException ("Illegal type " + argType.javaType + " for " + key + 
                                                ", defined as " + type.javaType);
    }

    /**
     * Returns a hash code value for this schema.
     */
    public int hashCode() {
        return 0;
    }

    /**
     * Indicates whether the other schema is "equal to" this one.
     */
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        return (other instanceof NameValueSchema &&
                Arrays.equals(attributes,
                              ((NameValueSchema)other).attributes));
    }

    /**
     * Compares this schema with the one specified for order. Returns a
     * negative integer, zero, or a positive integer as this schema is less
     * than, equal to, or greater than the specified one.
     */
    public int compareTo(Object other) {
        if (other == null) {
            return 1;
        }

        if (!(other instanceof NameValueSchema)) {
            return -1;
        }

        NameValueSchema otherSchema = (NameValueSchema)other;
        if (attributes.length > otherSchema.attributes.length) {
            return 1;
        } else if (attributes.length < otherSchema.attributes.length) {
            return -1;
        }

        for (int i = 0; i < attributes.length; i++) {
            int comp = attributes[i].compareTo(otherSchema.attributes[i]);
            if (comp != 0) {
                return comp;
            }
        }

        return 0;
    }

    /**
     * Returns a string representation of this schema.
     */
    public String toString() {
        StringBuffer result = new StringBuffer("NameValueSchema [");

        for (int i = 0; i < attributes.length; i++) {
            if (i > 0) {
                result.append(", ");
            }

            result.append(attributes[i].toString());
        }

        result.append("]");
        return result.toString();
    }

    /**
     * Instances of <code>Attribute</code> represent the attributes
     * supported by the @HoneycombProductName@'s name-value metadata.
     */
    public static class Attribute implements Comparable, Serializable {

        private String name;
        private ValueType type;
        private int length;

        Attribute(String name, int type, int length) {
            this(name, TYPES[type-1], length);
        }
        
        Attribute(String name,
                  ValueType type,
                  int length) {
            this.name = name;
            this.type = type;
            this.length = length;
        }

        static Attribute deserialize(DataInput din) throws IOException {
            String name = din.readUTF();
            int type = din.readInt();
            int length = din.readInt();
            return new Attribute(name, type, length);
        }

        /**
         * Returns this attribute's name. The name includes the namespace, 
         * e.g. mynamespace.myname. For more information on namespaces, 
         * refer to the Administration Guide, 
         * Configuring Metadata and File System Views.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns this attribute's type.
         */
        public NameValueSchema.ValueType getType() {
            return type;
        }

        /**
         * Returns this attribute's length according to the schema 
         * (meaningful only for types String, Char, and Binary).
         */
        public int getLength() {
            return length;
        }



        /**
         * Returns a string representation of this attribute.
         */
        public String toString() {
            return "<" + name + ", " + type.toString() +
                ( (length > -1) ? (", length="+length):"") +
                ">";
        }

        /**
         * Indicates whether the other attribute is "equal to" this one.
         */
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }

            return (other instanceof Attribute &&
                    type == ((Attribute)other).type &&
                    name.equals(((Attribute)other).name) &&
                    length == ((Attribute)other).length);
        }

        /**
         * Compares this attribute with the one specified for order. Returns a
         * negative integer, zero, or a positive integer as this attribute is less
         * than, equal to, or greater than the specified one.
         */
        public int compareTo(Object other) {
            if (other == null) {
                return 1;
            }

            if (!(other instanceof Attribute)) {
                return -1;
            }

            Attribute otherAttribute = (Attribute)other;

            if (type != otherAttribute.type) {
                return type.ordinalRank - otherAttribute.type.ordinalRank;
            }

            int comp = name.compareTo(otherAttribute.name);
            if (comp != 0) return comp;

            return length - otherAttribute.length;
        }
    }

    private static class ParserHandler
        extends DefaultHandler {

        private ArrayList attributes;

        public ParserHandler() {
            attributes = new ArrayList();
        }

        public Attribute[] getAttributes() {
            Attribute[] a = new Attribute[attributes.size()];
            attributes.toArray(a);
            return a;
        }

        public void startElement(String namespaceURI,
                                 String localName,
                                 String qName,
                                 Attributes atts) 
            throws SAXException {
            if (qName.equals("attribute")) {
                String name = atts.getValue("name");
                if (name == null) {
                    throw new SAXException("Attribute tag missing name");
                }

                String typeString = atts.getValue("type");
                if (typeString == null) {
                    throw new SAXException("Attribute type missing for " + name);
                }

                int length = -1;
                String lengthString = atts.getValue("length");
                if (lengthString != null) {
                    try {
                        length = Integer.parseInt(lengthString);
                    } catch (NumberFormatException e) {
                        throw new SAXException("Attribute "+name+" has an illegal length value: '"+
                                               lengthString+"'", e);
                    }
                }

                ValueType type = null;

                if (typeString.equals("long")) {
                    type = LONG_TYPE;
                } else if (typeString.equals("double")) {
                    type = DOUBLE_TYPE;
                } else if (typeString.equals("binary")) {
                    type = BINARY_TYPE;
                } else if (typeString.equals("char")) {
                    type = CHAR_TYPE;
                } else if (typeString.equals("string")) {
                    type = STRING_TYPE;
                } else if (typeString.equals("time")) {
                    type = TIME_TYPE;
                } else if (typeString.equals("timestamp")) {
                    type = TIMESTAMP_TYPE;
                } else if (typeString.equals("date")) {
                    type = DATE_TYPE;
                } else if (typeString.equals("objectid")) {
                    type = OBJECTID_TYPE;
                } else {
                    throw new SAXException("Attribute "+name+" has an unsupported type ["+
                                           typeString+"]");
                }

                attributes.add(new Attribute(name, type, length));
            }
        }
    }








    ///////////////
    // test code //
    ///////////////



  private static byte[] snarf (String path) throws Exception{
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    InputStream fis = new FileInputStream(path);
    int c = fis.read();
    while (c != -1){
      baos.write(c);
      c = fis.read();
    }
    return baos.toByteArray();
  }

/*  
 * Commenting out main so this does not show up in the javadocs.
 *   public static void main (String[] argv) throws Exception{
 *       FileInputStream fis = new FileInputStream (argv[0]);
 *       parse(fis);
 *   }
 */

//   private static void perf (String path, NameValueSchema schema) throws Exception{
//     byte[] b = snarf(path);
//     ByteArrayInputStream bais;

//     long start = System.currentTimeMillis();
//     for (int i = 0; i < 1000; i++){
//       bais = new ByteArrayInputStream(b);
//       Map map = NameValueXML.parseXML(bais, schema);
//     }
//     System.err.println("SAX: " + (System.currentTimeMillis() - start));
    
//     start = System.currentTimeMillis();
//     for (int i = 0; i < 1000; i++){
//       bais = new ByteArrayInputStream(b);
//       Map map = NameValueXML.oldParseXML(bais);
//     }
//     System.err.println("DOM: " + (System.currentTimeMillis() - start));

    
//     start = System.currentTimeMillis();
//     for (int i = 0; i < 1000; i++){
//       bais = new ByteArrayInputStream(b);
//       Map map = NameValueXML.parseXML(bais, schema);
//     }
//     System.err.println("SAX: " + (System.currentTimeMillis() - start));
    
//     start = System.currentTimeMillis();
//     for (int i = 0; i < 1000; i++){
//       bais = new ByteArrayInputStream(b);
//       Map map = NameValueXML.oldParseXML(bais);
//     }
//     System.err.println("DOM: " + (System.currentTimeMillis() - start));
//   }




//   public static void main (String[] argv) throws Exception{
//     byte b[] = snarf("/export/home/dave/Me.jpg");
//     //System.out.println(encode(b));
// //     FileOutputStream fos = new FileOutputStream ("/tmp/test.jpg");
// //     fos.write(decodeBinary(encode(b, null, null)));
// //     fos.close();

    
//     Attribute[] a = 
//       {new Attribute("uString", STRING_TYPE),
//        new Attribute("cString", CHAR_TYPE),
//        new Attribute("date", DATE_TYPE),
//        new Attribute("time", TIME_TYPE),
//        new Attribute("timestamp", TIMESTAMP_TYPE),
//        new Attribute("long", LONG_TYPE),
//        new Attribute("double", DOUBLE_TYPE),
//        new Attribute("binart", BINARY_TYPE)
//       };

//     NameValueSchema schema = new NameValueSchema(a);

//     Map map = new HashMap();
//     map.put("uString", "Unicode String");
//     map.put("cString", "Octet String");
//     map.put("date", new Date(System.currentTimeMillis()));
//     map.put("time", new Time(System.currentTimeMillis()));
//     map.put("timestamp", new Timestamp(System.currentTimeMillis()));
//     map.put("long", new Long(1000000000000l));
//     map.put("double", new Double (Math.PI));
//     //    map.put("binary", b);


//     ByteArrayOutputStream baos = new ByteArrayOutputStream();
//     NameValueXML.createXML(map, baos, schema);
//     System.out.println(new String(baos.toByteArray()));
//     map = NameValueXML.parseXML(new ByteArrayInputStream(baos.toByteArray()), schema);

//     Iterator i = map.entrySet().iterator();
    
//     while (i.hasNext()){
//       Map.Entry e = (Map.Entry) i.next();
//       System.out.println(e.getKey() + " " + e.getValue());
//       if (e.getKey().equals("double"))
//         System.out.println("PI: " + (((Double)(e.getValue())).doubleValue() == Math.PI));
//     }
//     //perf("/tmp/SRs", schema);
//   }
}


//   <attributes>
//     <attribute name="double" value="D400921fb54442d18"/>
//     <attribute name="cString" value="CY1N0cmluZw=="/>
//     <attribute name="uString" value="SdVN0cmluZw=="/>
//     <attribute name="time" value="d1154652674275"/>
//     <attribute name="timestamp" value="T1154652674275"/>
//     <attribute name="date" value="d1154652674275"/>
//     <attribute name="long" value="L1000000000000"/>
//   </attributes>
