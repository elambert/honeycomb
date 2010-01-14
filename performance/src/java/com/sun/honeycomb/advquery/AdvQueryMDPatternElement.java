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



import java.io.*;
import java.util.ArrayList;
import java.util.IllegalFormatException;

import com.sun.honeycomb.test.util.HoneycombTestException;
/**
 * Manges a single metadata pattern element. Used to create randomly generated
 * metadata based on the user specified pattern.
 */
public class AdvQueryMDPatternElement {
    
    public static final int DATA_TYPE_NONE = 0;
    public static final int DATA_TYPE_INTEGER =  1;
    public static final int DATA_TYPE_HEX = 2;
    public static final int DATA_TYPE_CHAR = 3;
    public static final int DATA_TYPE_STRING = 4;
    public static final int DATA_TYPE_DOUBLES = 5;
    
    public static final int VALUES_NONE = 0;
    public static final int VALUES_LIST = 1;
    public static final int VALUES_RANGE = 2;
    
    private String formatSpecifier = null;
    private int dataType;
    private int rangeType;
    private int minValue = 0;
    private int maxValue = 10;
    private int numValues = 0;
    private int valueList[];
    private int value = 0;
    private static String namespace = "";
    private String metadataFieldName = "";
    private int patternSize = 0;
    private String generatedPattern = "";
    
    AdvQueryMDPatternElement() {
        // set defaults
        dataType = DATA_TYPE_INTEGER;
        rangeType = VALUES_RANGE;
        minValue = 1;
        maxValue = 10;
        numValues = maxValue - minValue + 1;
        formatSpecifier = "$2x0";
    }
    
    /**
     * Create metadata pattern element based on the user specified
     * criteria.
     */
    AdvQueryMDPatternElement(String name, String format,
            int dt, int vt, int [] values, int repeatSize) {

        metadataFieldName = name;
        formatSpecifier = format;
        dataType = dt;
        rangeType = vt;
        maxValue = 0;
        minValue = 0;
        numValues = 0;
        if (dt == this.DATA_TYPE_STRING) {
            patternSize = repeatSize;
        }
        
        if (vt != VALUES_NONE) {
            maxValue = values[values.length-1];
            minValue = values[0];
            if (vt == VALUES_RANGE) {
                numValues = values[1] - values[0] + 1;
            } else if (vt == VALUES_LIST) {
                if (values != null) {
                    valueList = values.clone();
                    numValues = valueList.length;
                }
            }
        }
        
    }
    
    /**
     * Create metadata pattern element based on the user specified
     * criteria.
     */
    AdvQueryMDPatternElement(AdvQueryMDPatternElement element) {
        dataType = element.dataType;
        rangeType = element.rangeType;
        maxValue = element.maxValue;
        minValue = element.minValue;
        numValues = element.numValues;
        patternSize = element.patternSize;
        metadataFieldName = element.metadataFieldName;
        formatSpecifier = element.formatSpecifier;
        if (element.valueList != null && element.valueList.length > 0) {
            valueList = element.valueList.clone();
        }
        generatedPattern = element.generatedPattern;
        value = element.value;
        
    }
    
    /**
     *  Accepts an input string of user specified metadata fields and
     *  data generation patterns and returns an ArrayList of the metadata
     *  field/pattern objecs.
     */
    static ArrayList parseUserSpecifiedMetadataPatterns(String patterns) {
        ArrayList<AdvQueryMDPatternElement> metadataFields =
                new ArrayList<AdvQueryMDPatternElement>();
        
        String[] fields = patterns.split("/");
        if (fields == null || fields.length == 0) {
            System.err.println("Invalid metadata patterns specified");
            return null;
        }
        String name = "";
        for(int i = 0; i < fields.length; i++) {
            String[] tokens = fields[i].split("[\\[\\]]");
            if (tokens == null || tokens.length < 3) {
                System.err.println("Invalid metadata patterns specified: " +
                        fields[i]);
                return null;
            }
            
            name = tokens[1];
             if (namespace.length() == 0) {
                String[] tokens2 = name.split("[.]");
                // Save the namespace
                if (tokens2 != null && tokens2.length > 1) {
                    namespace = tokens2[0];
                }  
            }
            
            AdvQueryMDPatternElement mdField =
                    createPatternElement(name, tokens[2]);
            if (mdField != null) {
                metadataFields.add(mdField);
            }
        }
        return metadataFields;
    }
    
    static private String[] parseMDFields(String patterns) {
        String[] fields = patterns.split("/");
        return fields;
    }
    
    /**
     *  Creates a new AdvQueryMDPatternElement
     */
    static private AdvQueryMDPatternElement createPatternElement(String name,
            String formatPattern) {
        AdvQueryMDPatternElement pElement = null;
        StringBuffer format = new StringBuffer();
        boolean foundRandomPattern = false;
        String[] tokens = formatPattern.split("[{}]");
        if (tokens == null || tokens.length == 0) {
            System.err.println("Invalid metadata patterns specified: " +
                    formatPattern);
            return null;
        }
        
        int[] values = null;
        int dt = DATA_TYPE_NONE;
        int vt = VALUES_NONE;
        int repeatSize = 1;
        for(int j = 0; j < tokens.length; j++) {
            int pos = tokens[j].indexOf(":");
            if (pos == -1) {
                format.append(tokens[j]);
            } else {
                if (foundRandomPattern) {
                    System.err.println("Invalid pattern specified for " +
                            "field: " + name);
                    return pElement;
                }
                foundRandomPattern = true;
                String[] tokens2 = tokens[j].split("[:]");
                
                tokens2[0] = tokens2[0].trim();
                format.append("%1$");
                format.append(tokens2[0]);
                char dataType = tokens2[0].charAt(tokens2[0].length()-1);
                if (dataType == 'x') {
                    dt = DATA_TYPE_HEX;
                } else if (dataType == 'd') {
                    dt = DATA_TYPE_INTEGER;
                } else if (dataType == 'c') {
                    dt = DATA_TYPE_CHAR;
                } else if (dataType == 's') {
                    dt = DATA_TYPE_STRING;
                }
                
                int min = 0;
                int max = 0;
                if (tokens2.length > 1) {
                    values = getValues(tokens2[1].trim());
                    if (tokens2[1].indexOf(",") != -1) {
                        vt = VALUES_LIST;
                        min = 1;
                        max = values.length;
                        
                    } else if (tokens2[1].indexOf('-') != -1) {
                        vt = VALUES_RANGE;
                        min = values[0];
                        max = values[1];
                    } else {
                        vt = VALUES_LIST;
                        min = 1;
                        max = 1;
                    }
                }
                if (dt == DATA_TYPE_STRING) {
                    repeatSize = min;
                    if (tokens2.length > 2) {
                        repeatSize = Integer.parseInt(tokens2[2].trim());
                    }
                }
            }
            
            
        }   // end for
        
        pElement = new AdvQueryMDPatternElement(name,
                format.toString(), dt,  vt, values, repeatSize );
        return pElement;
        
    }
    
    /**
     * Parse values list or range. Can only be one or the other can not
     * be both.
     */
    static private int[] getValues(String valueList) {
        String [] valueStrings;
        if (valueList.indexOf(',') != -1) {
            valueStrings = valueList.split("[,]");
            // set list type
        } else {
            valueStrings = valueList.split("[-]");
            // set range type and min and max value
        }
        int[] values = new int[valueStrings.length];
        
        for (int i = 0; i < valueStrings.length; i++) {
            values[i] = Integer.parseInt(valueStrings[i].trim());
         }
        
        return values;
    }
    
    /**
     *  Sets the next randomly generated value
     */
    private int getNextValue(AdvQueryRandomUtil prng) 
        throws HoneycombTestException {
        
        int value = 0;
        if (dataType == DATA_TYPE_NONE ||
                numValues <= 0) {
            return value;
        }
        
        int ranValue = 0;
        try {
              
            ranValue = prng.getInteger(numValues);
            if (rangeType == VALUES_RANGE) {
                value = minValue + ranValue;                
            } else {
                if (valueList != null && ranValue < valueList.length) {
                    value = valueList[ranValue];
                } else {
                    HoneycombTestException testEx = new HoneycombTestException(
                            "Index out of range: " + ranValue +
                            "    Format String: " + formatSpecifier);
                    throw testEx;
                }
            }
            
        } catch (IllegalArgumentException e) {
            // should never get here cause numValues is
            // always positive.
            HoneycombTestException testEx = new HoneycombTestException(
                    "IllegalArgumentException: " + e.getMessage() +
                    "    Number of Values: " + numValues +
                    "    Format String: " + formatSpecifier, e);
            throw testEx;
        }
        
        return value;
    }
    
    /**
     *  Creates and sets the next randomly generated string
     */
    private String getNextString(int size, AdvQueryRandomUtil prng)
    throws HoneycombTestException {
        
        StringBuffer generatedString = new StringBuffer();
        int repeatSize = patternSize;
        if (patternSize > size) {
            repeatSize = size;
        }
        try {
            String randomString = prng.getRandomString2(repeatSize);
            
            int i = 0;
            while (generatedString.length() < size) {
                generatedString.append(randomString);
            }
            if (generatedString.length() > size) {
                generatedString.delete(size, generatedString.length());
            }
            
        } catch (StringIndexOutOfBoundsException e) {
            // should never get here
            HoneycombTestException testEx = new HoneycombTestException(
                    "StringOutOfBounds: " + e.getMessage() +
                    "    Format String: " + formatSpecifier, e);
            throw testEx;
        }
        
        return generatedString.toString();
    }
    
    /**
     *  Generates the next formatted output pattern using
     *  the random number generator that is passed in.
     */
    public String getNextGeneratedPattern(AdvQueryRandomUtil prng)
    throws HoneycombTestException {
        
        synchronized (this) {
            int value = 0;
            try {
                // get the next random generated value
                value = getNextValue(prng);
                StringWriter writerString = new StringWriter();
                PrintWriter pw = new PrintWriter(writerString);
                
                switch (dataType) {
                    
                    case DATA_TYPE_INTEGER :
                    case DATA_TYPE_HEX :
                        pw.format(formatSpecifier, value);
                        break;
                    case DATA_TYPE_CHAR :
                        char c = (char) value;
                        pw.format(formatSpecifier, c);
                        break;
                    case DATA_TYPE_STRING :
                        String tmpString = this.getNextString(value, prng);
                        pw.format(formatSpecifier, tmpString);
                        break;
                    default :
                        pw.format(formatSpecifier, value);
                        break;
                        
                }
                generatedPattern = writerString.toString();
                return generatedPattern;
                //return writerString.toString();
                
            } catch (IllegalFormatException ex){
                Class exClass = ex.getClass();
                StringBuilder msg = new
                        StringBuilder("IllegalFormatException: ");
                msg.append(exClass.getName());
                msg.append("   Format: ");
                msg.append(formatSpecifier);
                msg.append("   Value: ");
                msg.append(value);
                
                HoneycombTestException exTest =
                        new HoneycombTestException(msg.toString(), ex);
                throw exTest;
                
            } catch (Throwable th) {
                String msg = th.getMessage();
                if (msg == null || msg.length() == 0) {
                    msg = "Unexpected exception fomating random metadata";
                }
                HoneycombTestException exTest =
                        new HoneycombTestException(msg, th);
                throw exTest;
            }
        }
        
    }
    
    /**
     * Gets the metadata field name
     */
    public String getMetadataFieldName() {
        return metadataFieldName;
    }
    
    /**
     * Gets the format string used to generate the metadata field's value
     */
    public String getFormatString() {
        return this.formatSpecifier;
    }
    
    /**
     * Gets the last generated value
     */
    public String getGeneratedPattern() {
        return this.generatedPattern;
    }
    
    /*
     *  Gets the namespace
     */
    public static String getNamespace() {
        return namespace;
    }
    
    public AdvQueryMDPatternElement copy(AdvQueryMDPatternElement pattern) {
        AdvQueryMDPatternElement clone = new AdvQueryMDPatternElement(pattern);
        return clone;
    }
}
