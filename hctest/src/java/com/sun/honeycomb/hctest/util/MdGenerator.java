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



package com.sun.honeycomb.hctest.util;

import java.util.HashMap;

import com.sun.honeycomb.test.util.RandomUtil;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.client.NameValueSchema.Attribute;
import com.sun.honeycomb.client.NameValueSchema.ValueType;
import java.util.Iterator;
import com.sun.honeycomb.common.CanonicalStrings;

/**
 *
 * Class to generate Metadata
 *
 */
public class MdGenerator
{

    static public final int MAX_MD_PER_OBJECTS = 32;

    private int MAX_STRING = HoneycombTestConstants.HC_MAX_MD_STRLEN_CLUSTER;
    private int MAX_BINARY = HoneycombTestConstants.HC_MAX_MD_STRLEN_CLUSTER;
    private HashMap mdMap;
    private NameValueSchema schema;
    private int nbBinaryAttr;
    private int nbLongAttr;
    private int nbDoubleAttr;
    private int nbStringAttr;
    private int nbCharAttr;
    private int nbDateAttr;
    private int nbTimeAttr;
    private int nbTimestampAttr;

    public  MdGenerator(NameValueSchema schema)
        throws HoneycombTestException {
        this(schema, false);
    }

    public MdGenerator(NameValueSchema schema, boolean emulator)
        throws HoneycombTestException {
        this.schema = schema;

        if (emulator == true) {
            MAX_STRING = HoneycombTestConstants.HC_MAX_MD_STRLEN_EMULATOR;
        }

        mdMap = null;
        RandomUtil.initRandom();
        Attribute[] attribs = schema.getAttributes();

        nbBinaryAttr = 0;
        nbLongAttr = 0;
        nbDoubleAttr = 0;
        nbStringAttr = 0;
        nbDateAttr = 0;
        nbTimeAttr = 0;
        nbTimestampAttr = 0;
        nbCharAttr = 0;

        for (int i = 0; i < attribs.length; i++) {
            //
            // Don't use reserved system/filesystem/nonqueryable attrbutes
            //
            if (attribs[i].getName().startsWith("system.") ||
                attribs[i].getName().startsWith("filesystem.") ||
                attribs[i].getName().startsWith("nonqueryable.")) {
                continue;
            }

            ValueType type = attribs[i].getType();
            if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
                nbDoubleAttr++;
            } else if (type.equals(NameValueSchema.LONG_TYPE)) {
                nbLongAttr++;
            } else if (type.equals(NameValueSchema.STRING_TYPE)) {
                nbStringAttr++;
            } else if (type.equals(NameValueSchema.BINARY_TYPE)) {
                nbBinaryAttr++;
            } else if (type.equals(NameValueSchema.DATE_TYPE)) {
                nbDateAttr++;
            } else if (type.equals(NameValueSchema.TIME_TYPE)) {
                nbTimeAttr++;
            } else if (type.equals(NameValueSchema.TIMESTAMP_TYPE)) {
                nbTimestampAttr++;
            } else if (type.equals(NameValueSchema.CHAR_TYPE)) {
                nbCharAttr++;
            } 

        }
    }

    public HashMap getMdMap() {
        return mdMap;
    }

    public ValueType getType(String attr) {
        return schema.getType(attr);
    }
    public int getLength(String attr) {
        return schema.getLength(attr);
    }

    public int getNbAttributes(ValueType type) {
        if (type == null) {
            return nbBinaryAttr+nbDoubleAttr+nbLongAttr+nbStringAttr+
                nbCharAttr+nbDateAttr+nbTimeAttr+nbTimestampAttr;
        } else if (type.equals(NameValueSchema.BINARY_TYPE)) {
            return nbBinaryAttr;
        } else if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
            return nbDoubleAttr;
        } else if (type.equals(NameValueSchema.LONG_TYPE)) {
            return nbLongAttr;
        } else if (type.equals(NameValueSchema.STRING_TYPE)) {
            return nbStringAttr;
        } else if (type.equals(NameValueSchema.DATE_TYPE)) {
            return nbDateAttr;
        } else if (type.equals(NameValueSchema.CHAR_TYPE)) {
            return nbCharAttr;
        } else if (type.equals(NameValueSchema.TIME_TYPE)) {
            return nbTimeAttr;
        } else if (type.equals(NameValueSchema.TIMESTAMP_TYPE)) {
            return nbTimestampAttr;
        } else {
            return 0;
        }
    }

    /**
     * Generate a string query by parsing all the attr/value in the map
     * . If max = -1, go through the whole map
     * . If max > 0, stop when we reached max.
     */
    public String generateQueryFromMap(int max) 
        throws HoneycombTestException {

        StringBuffer query = null;
        int nbAttr = 0;
        
        if (mdMap == null) 
            return null;

        Iterator it = mdMap.keySet().iterator();
        while (it.hasNext()) {

            if (max != -1 && 
                nbAttr >= max) {
                break;
            }

            String attr = (String) it.next();
            ValueType type = getType(attr);
            if (type != null) {
                if (query == null) {
                    query = new StringBuffer();
                } else { 
                    query.append(" AND ");
                }
                nbAttr++;
                query.append("\"");
                query.append(attr);
                query.append("\"");
                query.append("=");

                if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
                    query.append(CanonicalStrings.encode(mdMap.get(attr)));
                } else if (type.equals(NameValueSchema.LONG_TYPE)) {
                    query.append(CanonicalStrings.encode(mdMap.get(attr)));
                } else if (type.equals(NameValueSchema.STRING_TYPE) ||
                           type.equals(NameValueSchema.CHAR_TYPE)) {
                    query.append("'");
                    query.append(mdMap.get(attr));
                    query.append("'");
                } else if (type.equals(NameValueSchema.BINARY_TYPE)) {
                    query.append("x'");
                    query.append(CanonicalStrings.encode(mdMap.get(attr)));
                    query.append("'");
                } else if (type.equals(NameValueSchema.DATE_TYPE)) {
                    query.append("{d '");
                    query.append(CanonicalStrings.encode(mdMap.get(attr)));
                    query.append( "'}");
                } else if (type.equals(NameValueSchema.TIME_TYPE)) {
                    query.append("{t '");
                    query.append(CanonicalStrings.encode(mdMap.get(attr)));
                    query.append( "'}");
                } else if (type.equals(NameValueSchema.TIMESTAMP_TYPE)) {
                    query.append("{ts '");
                    query.append(CanonicalStrings.encode(mdMap.get(attr)));
                    query.append( "'}");
                } else {
                    throw new
                        HoneycombTestException("Unsupported metadata type!");
                }
            }
        }
        return (query == null) ? null : query.toString();
    }


    public void createRandomFromSchema(int size) 
        throws HoneycombTestException {
        createRandomFromSchema(size, null);
    }

    public void createRandomStringFromSchema(int size) 
        throws HoneycombTestException {
        createRandomFromSchema(size, NameValueSchema.STRING_TYPE);
    }

    public void createRandomByteFromSchema(int size) 
        throws HoneycombTestException {
        createRandomFromSchema(size, NameValueSchema.CHAR_TYPE);
    }

    public void createRandomLongFromSchema(int size) 
        throws HoneycombTestException {
        createRandomFromSchema(size, NameValueSchema.LONG_TYPE);
    }

    public void createRandomDoubleFromSchema(int size) 
        throws HoneycombTestException {
        createRandomFromSchema(size, NameValueSchema.DOUBLE_TYPE);
    }

    public void createRandomBinaryFromSchema(int size) 
        throws HoneycombTestException {
        throw new HoneycombTestException("Unsupported Binary type!");
        // createRandomFromSchema(size, NameValueSchema.BINARY_TYPE);
    }

    public void createRandomFromListAttributes(Attribute[] attrs) 
        throws HoneycombTestException {
        mdMap = new HashMap();
        for (int i = 0; i < attrs.length; i++) {
            if (schema.getType(attrs[i].getName()) == null) {
                throw new
                    HoneycombTestException("Attribute is not part of schema");
            }
            mdMap.put(attrs[i].getName(),
                      generateData(attrs[i]));
        }
    }

    public void createFromListWithDefValues(Attribute[] attrs,
                                            HashMap defaults)
        throws HoneycombTestException {
        mdMap = new HashMap();
        for (int i = 0; i < attrs.length; i++) {
            if (schema.getType(attrs[i].getName()) == null) {
                throw new
                    HoneycombTestException("Attribute is not part of schema");
            }
            Object value = defaults.get(attrs[i].getType());
            if (value == null) {
                throw new
                    HoneycombTestException("Not default set for type " +
                                           attrs[i].getType());
            }
            checkType(attrs[i].getType(), value);
            mdMap.put(attrs[i].getName(), value);
        }
    }

    public String displayMap() {
        if (mdMap == null) {
            return "no attributes/values in the map";
        }

        StringBuffer buf = new StringBuffer();
        buf.append("Current " +  mdMap.size() + " attributes in the map:\n");
        Iterator it = mdMap.keySet().iterator();
        while (it.hasNext()) {
            String attrName = (String) it.next();
            ValueType type = schema.getType(attrName);
            int length = schema.getLength(attrName);
            String typeStr = null;
            byte b;
            double d;
            long l;
            String s;

            buf.append(". attribute name = " + attrName + 
                       ", type = "+ type.toString() +
                       ", length = " + length +
                       ", value = "+ CanonicalStrings.encode(mdMap.get(attrName))+
                       "\n");
        }
        return buf.toString();
    }


    private void checkType(ValueType type, Object value)
        throws HoneycombTestException {
        if (type.equals(NameValueSchema.BINARY_TYPE)) {
            throw new HoneycombTestException("Unsupported metadata type!");
            //        } else 
//             if (type.equals(NameValueSchema.BYTE_TYPE)) {
//             if (! (value instanceof Byte)) {
//                 throw new HoneycombTestException("Wrong type!");
//             }
        } else if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
            if (! (value instanceof Double)) {
                throw new HoneycombTestException("Wrong type!");
            }
        } else if (type.equals(NameValueSchema.LONG_TYPE)) {
            if (! (value instanceof Long)) {
                throw new HoneycombTestException("Wrong type!");
            }
        } else if (type.equals(NameValueSchema.STRING_TYPE)) {
            if (! (value instanceof String)) {
                throw new HoneycombTestException("Wrong type!");
            }
        }
    }

    private void createRandomFromSchema(int size, ValueType type)
        throws HoneycombTestException {

        int curSize = 0;
        mdMap = new HashMap();
        if (size > getNbAttributes(type)) {
            throw new HoneycombTestException("not enough attributes of type " +
                                         type + " in the schema to create " +
                                         "the map");
        }
        Attribute[] attribs = schema.getAttributes();
        // Not very efficient...
        do {

            int index = RandomUtil.randIndex(attribs.length);
            if (type != null && !type.equals(attribs[index].getType())) {
                continue;
            }
            //
            // Ignore system/filesystem/nonqueryable reserved attributes
            //
            if (attribs[index].getName().startsWith("system.") ||
                attribs[index].getName().startsWith("filesystem.") ||
                attribs[index].getName().startsWith("nonqueryable.")) {
                continue;
            }

            // Also we eliminate Binary since this is not supported yet.
            if (mdMap.containsKey(attribs[index].getName())) {
                continue;
            }

            mdMap.put(attribs[index].getName(),
                      generateData(attribs[index]));
            curSize++;
        } while (curSize < size);
    }


    private Object generateData(Attribute attr) throws HoneycombTestException {
        ValueType type = attr.getType();
        int length = attr.getLength();
        if (type.equals(NameValueSchema.BINARY_TYPE)) {
            return RandomUtil.getRandomBytes(length);
        } else if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
            return new Double(RandomUtil.getDouble());
        } else if (type.equals(NameValueSchema.LONG_TYPE)) {
            return new Long(RandomUtil.getLong());
        } else if (type.equals(NameValueSchema.STRING_TYPE)) {
            return RandomUtil.getRandomString(RandomUtil.randIndex(length));
        } else if (type.equals(NameValueSchema.CHAR_TYPE)) {
            return RandomUtil.getRandomString(RandomUtil.randIndex(length));
        } else if (type.equals(NameValueSchema.DATE_TYPE)) {
            return new java.sql.Date(RandomUtil.getLong());
        } else if (type.equals(NameValueSchema.TIME_TYPE)) {
            return new java.sql.Time(RandomUtil.getLong());
        } else if (type.equals(NameValueSchema.TIMESTAMP_TYPE)) {
            return new java.sql.Timestamp(RandomUtil.getLong());
        } else {
            throw new HoneycombTestException("Unsupported metadata type!");
        }
    } // generateData
} 
