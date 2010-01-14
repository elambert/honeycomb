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



/**
 * This class is the super class of all the nodes describing a EMD query in
 * the form of a parsed tree
 */

package com.sun.honeycomb.emd.parsers;

import com.sun.honeycomb.common.*;

import java.util.*;
import java.io.*;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.EMDConfigException;
import java.util.logging.Level;

public class QueryAttribute
    extends QueryNode {
    
    private String attribute;
    private Field attributeObject;

    public static String insertBackslash(String input) {
        int length = input.length();
        int nbQuotes = 0;
        int index = 0;
        char c;

        for (index=0; index<length; index++) {
            c = input.charAt(index);
            if ((c == '"') || (c == '\\')) {
                ++nbQuotes;
            }
        }

        if (nbQuotes == 0) {
            return(input);
        }

        char[] dst = new char[length+nbQuotes];
        input.getChars(0, length, dst, 0);

        int i,j;
        j = length-1;
        for (i=length+nbQuotes-1; i>=0; i--) {
            dst[i] = dst[j];
            j--;
            if ((dst[i] == '"') || (dst[i] == '\\')) {
                i--;
                dst[i] = '\\';
            }
        }
	
        return(new String(dst));
    }

    public QueryAttribute(String newAttribute) throws EMDException {
        super(TYPE_ATTRIBUTE, null, null);
        attribute = newAttribute;
        attributeObject = null;

        lookForAttribute();
    }

    private void lookForAttribute() 
        throws EMDException {
        if (attributeObject != null) {
            return;
        }

        attributeObject = RootNamespace.getInstance().resolveField(attribute);

        if (attributeObject == null) {
            throw new EMDException("No such attribute: '"+attribute+"'");
        }
    }

    public int getAttributeType() 
        throws NoSuchElementException {
        return(attributeObject.getType());
    }

    public String toSQLString() 
        throws NoSuchElementException {

        
        StringBuffer result = new StringBuffer();

        result.append(attribute);
        
        return(result.toString());
    }

    public String toString() {

        return("\"" + attribute + "\"" );
    }

    public String getAttribute() { return attribute;}
    public Field getAttributeField() { return(attributeObject); }
}
