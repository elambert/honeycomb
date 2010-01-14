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



package com.sun.honeycomb.emd.config;

import java.io.PrintStream;
import java.io.IOException;
import java.io.Writer;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;


import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.common.NewObjectIdentifier;

public class Field {
    
    public static final String TAG_FIELD = "field";
    public static final String ATT_NAME = "name";
    public static final String ATT_TYPE = "type";
    public static final String ATT_QUERYABLE = "queryable";
    public static final String ATT_LENGTH = "length";
    public static final String ATT_INDEXED = "indexed";
    // indexable is an old synonym for queryable.
    public static final String ATT_INDEXABLE = "indexable";
    
    public static final int TYPE_LONG         = 0;
    public static final int TYPE_DOUBLE       = 1;
    public static final int TYPE_STRING       = 2;
    public static final int TYPE_CHAR         = 3;
    public static final int TYPE_DATE         = 4;
    public static final int TYPE_TIME         = 5;
    public static final int TYPE_TIMESTAMP    = 6;
    public static final int TYPE_BINARY       = 7;
    public static final int TYPE_OBJECTID     = 8;

    // Only for System Metadata
    private static final int TYPE_INTERNAL    = 9;
    public static final int TYPE_BYTE         = TYPE_INTERNAL+0;

    //Number of bytes in an OBJECTID field
    public static final int OBJECTID_SIZE    = 30;

    static {
        assert(OBJECTID_SIZE == NewObjectIdentifier.OID_LENGTH);
    }

    public static int DEFAULT_STRING_LENGTH = 512;
    public static int MAX_STRING_LENGTH = 4000;
    public static int MAX_CHAR_LENGTH = 8000;

    private static final String[] typeStrings = {
        "long",
        "double",
        "string",
        "char",
        "date",
        "time",
        "timestamp",
        "binary",
        "objectid",
        "byte"
    };

    public static int parseType(String type)
    throws EMDConfigException {
        int result = -1;
        
        for (result=0; result<TYPE_INTERNAL; result++) {
            if (type.equals(typeStrings[result])) {
                return(result);
            }
        }
        throw new EMDConfigException("Unknown type ["+type+"]");
    }
    
    private Namespace namespace;
    private String name;
    private Column tableColumn;
    
    private int type;
    private int length;
    private boolean queryable;	//can be queried
    private boolean indexed;	//Create an index for this field

    /**
     * Constructor.
     */
    public Field(Namespace namespace,
                 String name,
                 int type,
                 int length,
                 boolean queryable,
                 boolean indexed) {
        this.namespace = namespace;
        this.name = name;
        this.type = type;
        this.queryable = queryable;
        this.indexed = indexed;
        if (length < 0 && needsLength(type))
            this.length = DEFAULT_STRING_LENGTH;
        else 
            this.length = length;
    }
    
    /**
     * Constructor.
     */
    public Field(Namespace namespace,
                 String name,
                 String type,
                 int length,
                 boolean queryable,
                 boolean indexed) {
        this(namespace, name, parseType(name, type, length), 
                length, queryable,indexed); 
    }

    /**
     * Constructor.
     */
    public Field(Namespace namespace,
                 String name,
                 int type,
                 boolean queryable) {
        this(namespace, name, type, -1, queryable, false);
    }

    public String getName() {
        return(name);
    }
    
    public int getLength() {
        return length;
    }

    public boolean isWritable() {
        return namespace.isWritable();
    }
    
    private static boolean needsLength(int type) {
        return (type == TYPE_STRING ||
                type == TYPE_CHAR ||
                type == TYPE_BINARY);
    }

    /**
       true IFF the field should be queryable.   
       Non-queryable fields may be retrieved with RetrieveMetadata
       but cannot be used in queries.
    */
    public boolean isQueryable() {
        return queryable;
    }

    public void setQueryable(boolean canQuery) {
        // For unassigned field, GUI can change this option freely.
        if (null == tableColumn) {
             queryable = canQuery;
             return;
        }
        
        if (queryable && !canQuery) {
            throw new IllegalArgumentException("Queryable field [" + 
                getQualifiedName() + "] cannot be made non-queryable");
        }
        // Namespace has to be extensible to change this field from
        // non-queryable to queryable.
        if (!queryable && canQuery) {
            if (!namespace.isExtensible()) {
                throw new IllegalArgumentException(
                    "Failed to change Field [" + getQualifiedName() + 
                    "] to be queryable.  The namespace [" + 
                    namespace.getQualifiedName() + "] is not extensible");
            }
            queryable = true;
        }
    }
    
    ///true IFF the Field should have its own index
    public boolean isIndexed() {
        return indexed;
    }
    public void setIndexed() {
        indexed = true;
    }
    
    public String getQualifiedName() {
        String namespaceString = namespace.getQualifiedName();
        return( namespaceString == null ? name
                : namespaceString+"."+name );
    }
    
    public void getQualifiedName(StringBuffer buffer,
            String separator) {
        int before = buffer.length();
        if (namespace != null) {
            namespace.getQualifiedName(buffer, separator);
        }
        if (buffer.length() > before) {
            buffer.append(separator);
        }
        buffer.append(name);
    }
    
    public Namespace getNamespace() {
        return namespace;
    }
    
    public Column getTableColumn() {
        return tableColumn;
    }
    
    public void setTableColumn(Column tableColumn) {
        this.tableColumn = tableColumn;
    }
    
    public int getType() {
        return type;
    }
    
    public String getTypeString() {
        return typeStrings[type];
    }
    
    public static String[] getTypeStrings() {
        return typeStrings;
    }

    public void export(Writer out,
                       String prefix)
        throws IOException{
        out.write(prefix+"<"+TAG_FIELD+
                  " "+ATT_NAME+"=\""+name+
                  "\" "+ATT_TYPE+"=\""+typeStrings[type]+ "\" "+
                  (length > 0 ? (ATT_LENGTH +"=\""+length + "\" ") : "")+
                  ATT_QUERYABLE+"=\""+queryable+"\" "+
                  (indexed ? (ATT_INDEXED +"=\""+indexed+"\" ") : "")+
                  "/>\n");
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("<Field \"");
        getQualifiedName(sb, ".");
        sb.append("\" ");
        sb.append(typeStrings[type]);
        if (queryable)
            sb.append(" queryable");
	if (tableColumn != null) {
	    sb.append(" => ");
	    sb.append(tableColumn.toString());
	}
        sb.append(">");

        return sb.toString();
    }
    /**
     * Validate this field's type and length.
     * @param name the name the this field
     * @param type the type of this field.
     * @param length the length of this field.
     * @throws IllegalArgumentException on error.
     */
    public static int parseType(String name, String type, int length) {
        int intType;
        if (type == null || "".equals(type)) {
            throw new IllegalArgumentException("a field tag ["+
                      name+"] misses the type attribute");
        }

        try {
            intType = parseType(type);
        } catch (EMDConfigException e) {
            throw new IllegalArgumentException("Bad type [" +
                      type+"] for field [" + name+"]");
        }

        // Validate the field length
        if (Field.TYPE_STRING == intType) {
            if (length > Field.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException(
                        "Field [" + name+"] of type [" + 
                        type + "] exceeded the max length of " + 
                        Field.MAX_STRING_LENGTH + ".");
            }
        } else if (Field.TYPE_CHAR == intType || Field.TYPE_BINARY == intType) {
            if (length > Field.MAX_CHAR_LENGTH) {
                throw new IllegalArgumentException(
                        "Field [" + name+"] of type [" + 
                        type + "] exceeded the max length of " +
                        Field.MAX_CHAR_LENGTH + ".");
            }
        }
        return intType;
    }
    
    /**
     * Validate a value for assignment to this Field. Make sure the
     * types are compatible, and if they're different convert and
     * return the right value. If no conversion is required return
     * null.
     *
     * @param value the value to convert
     * @return the new value of the correct type; null if no
     * conversion is required
     * @throws EMDConfigException if the value is incompatible
     */
    public Object validate(Object value) throws EMDConfigException {

        // The conversions throw NumberFormatException or
        // IllegalArgumentException; they're converted to
        // EMDConfigException

        try {
            return getTypedValue(value);
        } catch (NumberFormatException e) {
            ;
        } catch (IllegalArgumentException e) {
            ;
        }

        String msg = "Illegal type " + value.getClass().getCanonicalName() +
            "(value=" + CanonicalEncoding.image(value) + ") for " +
            typeStrings[type] + " field " + getQualifiedName();
        throw new EMDConfigException(msg);
    }

    /** Convert the value to one of the correct (JDBC) type */
    private Object getTypedValue(Object value) throws EMDConfigException {
        String strValue = value.toString();
        String fieldName = getQualifiedName();

        // Use CanonicalEncoding.decode*() to convert "value" into the
        // right type. If the value is bad, an IllegalArgumentException
        // is thrown.

        switch (type) {

        case TYPE_LONG:
            if (value instanceof Long)
                // Nothing to do
                return null;
           
            if (value instanceof String)
                // Convert the String to a Long
                return CanonicalEncoding.decodeLong(strValue);

            break;

        case TYPE_DOUBLE:
            if (value instanceof Double)
                return null;

            if (value instanceof String)
                return CanonicalEncoding.decodeDouble(strValue);

            break;

        case TYPE_DATE:
            if (value instanceof Date)
                return null;

            if (value instanceof String)
                return CanonicalEncoding.decodeDate(strValue);

            break;

        case TYPE_TIME:
            if (value instanceof Time)
                return null;

            if (value instanceof String)
                return CanonicalEncoding.decodeTime(strValue);

            break;

        case TYPE_TIMESTAMP:
            if (value instanceof Timestamp)
                return null;

            if (value instanceof String)
                return CanonicalEncoding.decodeTimestamp(strValue);

            break;

        // Variable-size fields: check length too

        case TYPE_STRING:
            if (value instanceof String) {
                if (strValue.length() > length)
		    throw new EMDConfigException("Value too long for field "+
		                                 fieldName);
	        CanonicalEncoding.validateString(strValue);

                return null;	
            }

            break;

        case TYPE_CHAR:
            if (value instanceof String) {
                if (strValue.length() > length)
                    throw new EMDConfigException("Value too long for field "+
                                                 fieldName);
                CanonicalEncoding.validateChar(strValue);

                // And nothing needs to be done
                return null;
            }

            break;

        case Field.TYPE_OBJECTID:
            length = Field.OBJECTID_SIZE;
            // Fall into BINARY case!

        case TYPE_BINARY:
            if (value instanceof String) {
                int arrayLength = strValue.length()/2;
                if (arrayLength > length)
                    throw new EMDConfigException("Value too long for field "+
                                                 fieldName);
                CanonicalEncoding.validateBinary(strValue);
                return CanonicalEncoding.decodeBinary(strValue);
            }

            if (value instanceof byte[]) {
                byte [] bytesValue = (byte []) value;
                if (bytesValue.length > length) 
                    throw new EMDConfigException("Value too long for field "+
                                                 fieldName);
                return null;
            }

            break;

        default:
            throw new InternalException("Unsupported type code " + type +
                                        "in field " + fieldName);
        }

        // Let the caller throw a nice EMDConfigException
        throw new IllegalArgumentException("bad type");
    }

    public void checkCompatibility(Field fd)
        throws EMDConfigException {
        if (!name.equals(fd.getName())) {
            throw new EMDConfigException(
                "Field name differs for field [" + name + "].");
        }

        if (type != fd.getType()) {
             throw new EMDConfigException(
                 "Field [" + name + "] of type [" + fd.getTypeString() +
                 "] conflicts with the new type [" + getTypeString() +"].");
        }

        if (length != fd.getLength()) {
             throw new EMDConfigException(
                 "Field [" + name + "] of length [" + fd.getLength() +
                 "] conflicts with the new length [" + length + "].");
        }
    }

    public void compareDirect(Field fd)
        throws EMDConfigException {

        if (type != fd.getType()) {
            throw new EMDConfigException("The 'type' attribute for  " +
              "field " + name + 
              " differ from the hive to the remote new cell"); 
        }

        if (queryable != fd.isQueryable()) {
            throw new EMDConfigException("The 'queryable' attribute for  " +
              "field " + name + 
              " differ from the hive to the remote new cell"); 
        }

        if (indexed != fd.isIndexed()) {
            throw new EMDConfigException("The 'indexed' attribute for  " +
              "field " + name + 
              " differ from the hive to the remote new cell"); 
        }

        if (length != fd.getLength()) {
            throw new EMDConfigException("The 'length' attribute for  " +
              "field " + name + 
              " differ from the hive to the remote new cell"); 
        }
    }
}
