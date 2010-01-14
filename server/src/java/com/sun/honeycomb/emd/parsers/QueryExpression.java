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

import com.sun.honeycomb.common.ByteArrays;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

public class QueryExpression
    extends QueryNode {
    
    public static final int OPERATOR_EQUAL      =1;
    public static final int OPERATOR_NOTEQUAL   =2;
    public static final int OPERATOR_LT         =3;
    public static final int OPERATOR_GT         =4;

    public static final String DOUBLE_PRECISION = "0.0000000001";

    private String attribute;
    private Field attributeObject;
    private Object value;
    private int operator;

    private static String insertBackslash(String input) {
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

    public QueryExpression(String newAttribute,
                           Object newValue,
                           int newOperator) throws EMDException {
        super(TYPE_EXPRESSION, null, null);
        attribute = newAttribute;
        attributeObject = null;
        value = newValue;
        operator = newOperator;

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
        throws NoSuchElementException, IllegalArgumentException {

        throw new RuntimeException("toSQLString no longer should be used");
    }

    public String toString() {
        StringBuilder result = new StringBuilder();

        // Attribute
        result.append(attribute);

        // Operator
        switch (operator) {
        case OPERATOR_EQUAL:
            result.append("=");
            break;

        case OPERATOR_NOTEQUAL:
            result.append("!=");
            break;

        case OPERATOR_LT:
            result.append("<");
            break;

        case OPERATOR_GT:
            result.append(">");
            break;
        }

        // Value

        result.append(makeLiteral(value));

        return result.toString();
    }

    public String makeLiteral(Object o) {

        if (o instanceof String) {
            String str = (String) o;
            return "'"+sqlQuote(str)+"'";
        } else if (o instanceof Long) {
            return ((Long)o).toString();
        } else if (o instanceof Double) {
            return ((Double) o).toString();
        } else if (o instanceof Date) {
            Date d = (Date) o;
            return "{d '"+d.toString()+"'}";
        } else if (o instanceof Time) {
            Time t = (Time) o;
            return "{t '"+t.toString()+"'}";
        } else if (o instanceof Timestamp) {
            Timestamp ts = (Timestamp) o;
            return "{ts '"+ts.toString()+"'}";
        } else if (o instanceof byte []) {
            byte [] bytes = (byte[])o;
            return "x'"+ByteArrays.toHexString(bytes)+"'";
        } else throw new RuntimeException("illegal type "+o.getClass()+" encountered");
    }


    public String getAttribute() { return attribute;}
    public Field getAttributeField() { return(attributeObject); }
    public Object getValue() {return value;}
    public int getOperator() {return operator;}

    /** Replace single quotes with doubled single quotes */
    private static String sqlQuote(String s) {
        StringBuffer ret = new StringBuffer();

        int pos = 0;
        for (;;) {
            int c = s.indexOf('\'', pos);
            if (c < 0)
                break;

            c++;
            ret.append(s.substring(pos, c)).append("'");
            pos = c;
        }

        return ret.append(s.substring(pos)).toString();
    }
}
