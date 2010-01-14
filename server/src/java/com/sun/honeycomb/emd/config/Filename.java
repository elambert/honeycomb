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

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import java.beans.XMLEncoder;

import com.sun.honeycomb.emd.common.QueryMap;

public class Filename {
    private static final Logger LOG = Logger.getLogger(Filename.class.getName());

    public static final int REPRESENTATION_STRING =1;
    public static final int REPRESENTATION_VARIABLE =2;

    public static class Element {
        public int type;
        public String value;
        public Field field;

        public Element(int newType,
                       String newValue) {
            type = newType;
            value = newValue;
        }
    }
    
    private FsView fsView;
    private Element[] elements;
    

    public Filename(FsView _fsView,
                    Element[] _elements,
                    RootNamespace rootNamespace)
        throws EMDConfigException {
        fsView = _fsView;
        elements = _elements;
        lookForFields(rootNamespace);
    }

    private void lookForFields(RootNamespace rootNamespace)
        throws EMDConfigException {
        for (int i=0; i<elements.length; i++) {
            if (elements[i].type == REPRESENTATION_VARIABLE) {
                elements[i].field = fsView.resolveField(elements[i].value,
                                                        rootNamespace);
            }
        }
    }
    
    public String toString() {
        String result = new String();

        for (int i=0; i<elements.length; i++) {
            switch (elements[i].type) {
            case REPRESENTATION_STRING:
                result += elements[i].value;
                break;

            case REPRESENTATION_VARIABLE:
                result += "${"+fsView.dequalifyField(elements[i].field)+"}";
                break;
            }
        }
            
        return(result);
    }

    public String toEncodedString() {
        String result = new String();

        for (int i=0; i<elements.length; i++) {
            switch (elements[i].type) {
            case REPRESENTATION_STRING:
                result += XMLEncode(elements[i].value);
                break;

            case REPRESENTATION_VARIABLE:
                result += "${"+fsView.dequalifyField(elements[i].field)+"}";
                break;
            }
        }
            
        return(result);
    }

    private String XMLEncode(String s) {
        // The five pre-defined characters that are special in XML,
        // along with their entity representations. (Don't forget that
        // & must be the first one to be substituted!)
        String[] special = {"&", "<", ">", "\"", "'" };
        String[] quoted = { "&amp;", "&lt;", "&gt;", "&quot;", "&apos;" };

        for(int i = 0; i < special.length; i++)
            s = s.replaceAll(special[i], quoted[i]);

        return s; 
    }

    public ArrayList getNeededAttributes() {
        ArrayList result = new ArrayList();
        
        for (int i=0; i<elements.length; i++) {
            if (elements[i].type == REPRESENTATION_VARIABLE) {
                result.add(elements[i].field.getQualifiedName());
            }
        }

        return(result);
    }

    public boolean isWritable() {
        for (int i=0; i<elements.length; i++) {
            if (elements[i].type == REPRESENTATION_VARIABLE) {
                if (!elements[i].field.isWritable())
                    return(false);
            }
        }
        return(true);
    }

    public String convert(QueryMap extendedMetadata) {
        String result = new String();
	
        for (int i=0; i<elements.length; i++) {
            switch (elements[i].type) {
            case REPRESENTATION_STRING:
                result += elements[i].value;
                break;

            case REPRESENTATION_VARIABLE:
                Object value = null;
                Object obj;
		
                if (value == null) {
                    value = extendedMetadata.get(elements[i].field.getQualifiedName());
                }
		
                if (value == null) {
                    // Attribute not found
                    value = "???";
                }

                //FIXME:  Use "canonical value encoding".  For now, just use the toString encoding!
                result += value.toString();
                break;
            }
        }

        return(result);
    }

    public String convert(Map md) {
        String result = new String();
	
        for (int i=0; i<elements.length; i++) {
            switch (elements[i].type) {
            case REPRESENTATION_STRING:
                result += elements[i].value;
                break;

            case REPRESENTATION_VARIABLE:
                String value = null;
                Object obj;
		
                value = (String) md.get(elements[i].field.getQualifiedName());
		
                if (value == null) {
                    // Attribute not found
                    value = "???";
                }

                result += value;
                break;
            }
        }

        return(result);
    }

    public Map parseFilename(String filename) 
        throws EMDConfigException {
	
        HashMap result = new HashMap();
        int currentIndex = 0;
        int elementIndex;

        for (elementIndex=0; elementIndex<elements.length; elementIndex++) {
            if (currentIndex == filename.length()) {
                throw new EMDConfigException("The filename string is too short ["+
                                             filename+"]");
            }
	    
            Element currentElement = elements[elementIndex];

            if (currentElement.type == REPRESENTATION_STRING) {
                if (!filename.substring(currentIndex, currentIndex+currentElement.value.length())
                    .equals(currentElement.value)) {
                    throw new EMDConfigException("Failed to parse the filename ["+filename+"]. Was expecting "+
                                                 currentElement.value+" at offset "+currentIndex);
                }

                currentIndex += currentElement.value.length();
                continue;
            }

            if (elementIndex+1 == elements.length) {
                // This is the last string.
                result.put(currentElement.value,
                           filename.substring(currentIndex));
                currentIndex = filename.length();
                continue;
            }

            if ( (elements[elementIndex].type == REPRESENTATION_VARIABLE)
                 && (elements[elementIndex+1].type == REPRESENTATION_VARIABLE) ) {
                throw new EMDConfigException("Cannot parse a representation that has 2 consecutive variables");
            }
	    
            String nextString = elements[elementIndex+1].value;
            int newIndex = filename.indexOf(nextString, currentIndex);

            if (newIndex == -1) {
                throw new EMDConfigException("Couldn't find the expected ["+
                                             nextString+"] token, after index "+currentIndex);
            }

            result.put(currentElement.value,
                       filename.substring(currentIndex, newIndex));
            currentIndex = newIndex;
        }
	
        return(result);
    }
}
