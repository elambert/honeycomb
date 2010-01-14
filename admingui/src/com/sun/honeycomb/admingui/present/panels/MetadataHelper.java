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



package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.emd.config.Field;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import javax.swing.table.DefaultTableModel;

public class MetadataHelper {
    
    public static String ROOT_NAME = GuiResources.getGuiString(
            "config.metadata.namespace.rootNamespaceName");
    
    // data type length values and sizes
    public static final int MAX_NAMESPACE_NAME_LENGTH = 63;
    public static final int MAX_FIELD_NAME_LENGTH = 63;
    public static final int MIN_FIELD_LENGTH = 1;
    public static final int MAX_FIELD_LENGTH = 1024;
    public static final int FIELD_NAME_LENGTH_ZERO = 0;
    public static final int FIELD_NAME_LENGTH_EXCEEDED = 1;
    public static final int FIELD_NAME_LENGTH_OK = 3;
    public static final int DEFAULT_CHAR_SIZE = 256;
    public static final int DEFAULT_BINARY_SIZE = 256;
    public static final int DEFAULT_STRING_SIZE = 128;
    public static final int STRING_CONSTANT = 1;
    public static final int CHAR_CONSTANT = 2;
    public static final int BINARY_CONSTANT = 3;
    
    // Namespace type groupings (all, reserved, and nonreserved)
    public static final int NONRESERVED_NS = 0;
    public static final int RESERVED_NS = 1;
    public static final int ALL_NS = 2;
    
    // archival types for views
    public static final String ARCH_TYPE_TAR = "tar";
    public static final String ARCH_TYPE_ZIP = "zip";
    public static final String ARCH_TYPE_CPIO = "cpio";
    public static final String ARCH_TYPE_ISO = "iso9660";

    // Data type mappings
    public static final String NOT_APPLICABLE = GuiResources.getGuiString(
                                    "config.metadata.namespace.notApplicable");
    public static final String STRING_N = GuiResources.getGuiString(
                                    "config.metadata.namespace.stringN");
    public static final String CHAR_N = GuiResources.getGuiString(
                                    "config.metadata.namespace.charN");
    public static final String BINARY_N = GuiResources.getGuiString(
                                    "config.metadata.namespace.binaryN");
    public static final String LONG = GuiResources
                            .getGuiString("config.metadata.namespace.long");
    public static final String DOUBLE = GuiResources
                            .getGuiString("config.metadata.namespace.double");
    public static final String DATE = GuiResources
                            .getGuiString("config.metadata.namespace.date");
    public static final String TIME = GuiResources
                            .getGuiString("config.metadata.namespace.time");
    public static final String TIMESTAMP = GuiResources
                        .getGuiString("config.metadata.namespace.timeStamp");

    static HashMap dataTypeStringToInt = new HashMap();
    static HashMap dataTypeIntToString = new HashMap();
    static {
        // integer to string data type mapping
        dataTypeIntToString.put(new Integer(Field.TYPE_STRING), STRING_N);
        dataTypeIntToString.put(new Integer(Field.TYPE_CHAR), CHAR_N);
        dataTypeIntToString.put(new Integer(Field.TYPE_BINARY), BINARY_N);
        dataTypeIntToString.put(new Integer(Field.TYPE_LONG), LONG);
        dataTypeIntToString.put(new Integer(Field.TYPE_DOUBLE), DOUBLE);
        dataTypeIntToString.put(new Integer(Field.TYPE_DATE), DATE);
        dataTypeIntToString.put(new Integer(Field.TYPE_TIME), TIME);
        dataTypeIntToString.put(new Integer(Field.TYPE_TIMESTAMP), TIMESTAMP);
        
        // string to integer data type mapping
        dataTypeStringToInt.put(STRING_N, new Integer(Field.TYPE_STRING));
        dataTypeStringToInt.put(CHAR_N, new Integer(Field.TYPE_CHAR));
        dataTypeStringToInt.put(BINARY_N, new Integer(Field.TYPE_BINARY));
        dataTypeStringToInt.put(LONG, new Integer(Field.TYPE_LONG));
        dataTypeStringToInt.put(DOUBLE, new Integer(Field.TYPE_DOUBLE));
        dataTypeStringToInt.put(DATE, new Integer(Field.TYPE_DATE));
        dataTypeStringToInt.put(TIME, new Integer(Field.TYPE_TIME));
        dataTypeStringToInt.put(TIMESTAMP, new Integer(Field.TYPE_TIMESTAMP));
        
// Byte not currently supported... ???
//        dataTypeIntToString.put(new Integer(Field.TYPE_BYTE),
//                GuiResources.getGuiString("config.metadata.namespace.byte"));
//        dataTypeStringToInt.put(
//                GuiResources.getGuiString("config.metadata.namespace.byte"),
//                                            new Integer(Field.TYPE_BYTE));
        
    }
    
    /** 
     * Convert Object type to String - used for Length Field
     */
    public static String objToString(Object obj) {
         String str = "";
         if (obj instanceof String) {
             str = (String)obj;            
         } else if (obj instanceof Integer) {
             int size = ((Integer)obj).intValue();
             if (size > 0) {
                str = Integer.toString(size);  
             } else {
                str = MetadataHelper.NOT_APPLICABLE;
             }
         }
         return str;
    }
    
    /** 
     * Combine the length field with the data type
     */
    public static String toTypeWithLength(Integer dataType, String length) {
        String strType = "";
        if (dataType.intValue() == Field.TYPE_STRING) {
            strType = GuiResources.getGuiString(
                   "config.metadata.namespace.stringLength", length);
        } else if (dataType.intValue() == Field.TYPE_CHAR) {
            strType = GuiResources.getGuiString(
                   "config.metadata.namespace.charLength", length);
        } else if (dataType.intValue() == Field.TYPE_BINARY) {
            strType = GuiResources.getGuiString(
                   "config.metadata.namespace.binaryLength", length);
        } else {
            strType = (String)MetadataHelper.dataTypeIntToString.get(dataType);  
        }
        
        return strType;
    }
    
    public static String getNSNameFromField(String qualifiedFieldName) {
        String namespaceName = null;
        // The qualified field name consists of <qualified ns name>.<field name>
        // If the namespace name is null, then the qualified field name is just
        // the name, but one would assume that a null namespace indicates that
        // it is a field belonging to <root>
        int separator = qualifiedFieldName.lastIndexOf(".");
        if (0 < separator) {
            namespaceName = qualifiedFieldName.substring(0, separator);
        } else {
            namespaceName = MetadataHelper.ROOT_NAME;
        }
        return namespaceName;
    }
    public static String getNSNameFromField(Field field) {
        
        String qualifiedFieldName = field.getQualifiedName();
        return getNSNameFromField(qualifiedFieldName);
    }
 
    public static Map getUniqueFields(DefaultTableModel model, int column) {
        Vector modelData = model.getDataVector();
        int numOfRows = modelData.size();
        Map validRows = new HashMap(numOfRows);
        for (int i = 0; i < numOfRows; i++) {
            boolean valid = true;
            String name = (String)((Vector)modelData.elementAt(i))
                                                        .elementAt(column);
            name = name.trim();
            if (name.length() == 0) {
                continue;
            }          
                        
            if (numOfRows > 1) {
                for (int j = i + 1; j < numOfRows; j++) {
                    String nextName = (String)((Vector)modelData
                                            .elementAt(j)).elementAt(column);
                    if (name.equalsIgnoreCase(nextName.trim())) {
                        Log.logInfoMessage(GuiResources.getGuiString(
                                "config.metadata.namespace.field.duplicate",
                                name));
                        valid = false;
                        break;
                    }
                }
            }
            
            if (valid) {
                validRows.put(new Integer(i), name);
            }
        } // end of iterating through all fields entered by user
        return validRows;
    }

    /**
     * To check and set default FieldLength 
     * based on selected DataType from Combo in AddRemoveList
     */
    public static boolean isValidInteger(Object num, String dataType)  {
        boolean result = true;
        Integer val = null;
        int strCon = 0;
        int charCon = 0;
        int binaryCon = 0;
        int constant = 0;
        if (dataType.equalsIgnoreCase(STRING_N)) {
            strCon = STRING_CONSTANT;
            constant = strCon;
        }
        if (dataType.equalsIgnoreCase(CHAR_N)) {
            charCon = CHAR_CONSTANT;
            constant = charCon;
        }
        if (dataType.equalsIgnoreCase(BINARY_N)) {
            binaryCon = BINARY_CONSTANT;
            constant = binaryCon;
        }
        
        if (num instanceof Integer) {
           val = (Integer)num;
           int i = val.intValue();
             
               switch (constant) {
                   case  STRING_CONSTANT :                        
                       if (dataType.equalsIgnoreCase(STRING_N) &&
                                                    i == DEFAULT_STRING_SIZE) {
                           result = true;
                       } else {
                           result = false;
                       }
                       break;
                   case CHAR_CONSTANT :
                        if (dataType.equalsIgnoreCase(CHAR_N)  &&  
                                                    i == DEFAULT_CHAR_SIZE) {
                            result = true;
                        } else {
                            result = false;
                        }
                        break;
                   case BINARY_CONSTANT :                                    
                        if (dataType.equalsIgnoreCase(BINARY_N) && 
                                                    i == DEFAULT_BINARY_SIZE) {
                            result = true;
                        } else {
                            result = false;
                        }
                        break;
               }
        }
        return result;
    }
}
