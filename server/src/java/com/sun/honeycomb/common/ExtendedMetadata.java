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



package com.sun.honeycomb.common;

import com.sun.honeycomb.emd.*;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.EMDConfigException;

public class ExtendedMetadata {
    
    public ExtendedMetadata(Map map) {
        if(map == null) {
            emd = new HashMap();
        } else {
            emd = map;
        }
    }
    
    /* copy constructor */
    public ExtendedMetadata(ExtendedMetadata em) {
        emd = new HashMap(em.emd);
    }
    
    public void populate(Map map) {
        map.putAll(emd);
    }

    public Map getMap() {
        return emd;
    }
    
    public boolean equals(Object anObject) {
        if(anObject == null || (anObject.getClass() != this.getClass())) {
            return false;
        }
        
        return(emd.equals(((ExtendedMetadata)anObject).emd));
    }
    
    public void serialize(DataOutput dout) throws IOException {
        if(dout == null) throw new IOException ("No DataOutput!");
        
        Set emdEntries = emd.entrySet();
        int size = emdEntries.size();
        
        // Count the non-null entries
        Iterator iter = emdEntries.iterator();
        while(iter.hasNext()) {
            Entry entry = (Entry) iter.next();
            if(entry.getKey() == null || entry.getValue() == null) {
                size--;
            }
        }
        
        iter = emdEntries.iterator();
        
        dout.writeInt(size);
        
        while(iter.hasNext()) {
            Entry entry = (Entry) iter.next();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if(key != null && value != null) {
                dout.writeUTF(key);
                dout.writeUTF(value);
            }
        }
    }
    
    public static ExtendedMetadata deserialize(DataInput din) 
        throws IOException {
        if(din == null) throw new IOException ("No DataInput!");
        
        Map map = new HashMap();
        int size = din.readInt();
        while(size-- > 0) {
            String key = din.readUTF();
            String value = din.readUTF();
            map.put(key, value);
        }
        return new ExtendedMetadata(map);
    }
    
    // Constructs a new emd object from a comma-seperated string:
    // "name1=value1,name2=value2,..."
    public static ExtendedMetadata parseCSVString(String csv)
        throws IOException, IllegalArgumentException {
        if(csv == null) {
            throw new IOException ("csv string is null");
        }  
        
        Map map = new HashMap();
        StringTokenizer st = new StringTokenizer(csv, CSV_SEP, true);
        String name = null;
        String value = null;

        while(st.hasMoreElements()) {
            name = CSVNextToken(st, CSV_SEP);
            if(!st.hasMoreTokens()) {
                throw new 
                    IllegalArgumentException("Found odd number of arguments" +
                                             " in [" + csv + "]");
            }
            value = CSVNextToken(st, CSV_SEP);
            map.put(CSVUnescape(name), CSVUnescape(value));
        }
        
        // Check the validity of the arguments
        try {
            RootNamespace.getInstance().validate(map);
        } catch (EMDConfigException e) {
            IllegalArgumentException newe = new IllegalArgumentException(e.getMessage());
            newe.initCause(e);
            throw newe;
        }
        
        return new ExtendedMetadata(map);
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer();
        Set emdEntries = emd.entrySet();
        Iterator iter = emdEntries.iterator();
        
        while(iter.hasNext()) {
            Entry entry = (Entry) iter.next();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if(key != null && value != null) {
                buf.append(key + " = " + value + "\n");
            }
        }
        
        return buf.toString();
    }
    
    // Returns a comma seperated string "name1,value1,name2,value2..."
    public String toCSVString() throws IOException {
        StringBuffer sbuf = new StringBuffer();
        boolean firstEntry = true;
        
        Iterator iter = emd.entrySet().iterator();
        while(iter.hasNext()) {
            Entry entry = (Entry) iter.next();
            Object key = entry.getKey();
            if(key == null) {
                throw new 
                    IllegalArgumentException("Field names must not be null");
            }
            String keyStr = key.toString();
            Object value = entry.getValue();
            if(value == null) {
                throw new IllegalArgumentException(keyStr + 
                                                   " must not be null");
            }
            String valueStr = value.toString();
            if (!firstEntry) {
                sbuf.append(CSV_SEP);
            } else {
                firstEntry = false;
            }
            sbuf.append(keyStr);
            sbuf.append(CSV_SEP);
            String escValStr = CSVEscape(valueStr);
            sbuf.append(CSVEscape(valueStr));
        }
        
        return sbuf.toString();
    }
    
    // replaces "," with "\,"
    private static String CSVEscape(String s) {
        return s.replaceAll(",", "\\\\,");
    }
    
    // replaces "\," with ","
    private static String CSVUnescape(String s) {
        return s.replaceAll("\\\\,", ",");
    }
    
    // Repects the esc char '\' while tokenizing (treats x\<sep>y as 1 token)
    private static String CSVNextToken(StringTokenizer st, String sep) {
        StringBuffer result = new StringBuffer("");
        String next = "";
        boolean esc = false;
        
        if(!st.hasMoreTokens()) {
            return null;
        }
        
        while(st.hasMoreTokens()) {
            next = st.nextToken().replaceAll("\\\\\\\\", "\\\\");
            if(next.equals(sep)) {
                if(esc) {
                    result.append(next);
                    esc = false;
                } else {
                    return result.toString();
                }
            } else if(next.endsWith("\\")) {
                if(!next.endsWith("\\\\")) {
                    esc = true;
                    result.append(next.substring(0,next.length()-1));
                } else {
                    result.append(next);
                }
            } else {
                result.append(next);
                
            }
        }
        return result.toString();
    }
    
    
    private Map emd;
    private static final String CSV_SEP = ",";
}
