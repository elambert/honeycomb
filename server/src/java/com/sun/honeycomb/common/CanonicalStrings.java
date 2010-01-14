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

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import java.text.SimpleDateFormat;
import java.text.ParsePosition;

import java.util.Arrays;

import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.CanonicalEncoding;

import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.EMDConfigException;

public class CanonicalStrings extends CanonicalEncoding{

    protected CanonicalStrings() {
        //Static methods only.   Never instantiate this class.
    }


    public static String encode(Object o, String name) {
        Field field;
        try{
            field = RootNamespace.getInstance().resolveField(name);
        } catch (EMDConfigException ece){
            throw new RuntimeException(ece);
        }
        if (field == null)
            throw new IllegalArgumentException("Unknown field " + name);
        return encode(o, field);
    }
    public static String encode(Object o, Field field) {
        int type = field.getType();

        return encode(o,type);
    }

    public static String encode(Object o, int type) {
        if (type == Field.TYPE_OBJECTID) {
            // Convert OID to external form if not already
            if (o instanceof ExternalObjectIdentifier) {
                // Already in proper format
            } else if (o instanceof byte[]) {
                NewObjectIdentifier oid = NewObjectIdentifier.readFromBytes((byte[])o);
                o = oid.toExternalObjectID();
            } else if (o instanceof String) {
                NewObjectIdentifier oid = NewObjectIdentifier.fromHexString((String)o);
                o = oid.toExternalObjectID();
            } else throw new RuntimeException("illegal type for oid field");
        }
        return encode(o);
    }


    public static Object decode(String encoded, String name) {
        Field field;
        try{
            field = RootNamespace.getInstance().resolveField(name);
        } catch (EMDConfigException ece){
            throw new RuntimeException(ece);
        }
        if (field == null)
            throw new IllegalArgumentException("Unknown field " + name);
        return decode(encoded,field);
    }

    public static Object decode(String encoded, Field field) {
        int type = field.getType();

        return decode(encoded,type);
    }

    public static Object decode(String encoded, int type) {
        switch (type){
        case Field.TYPE_LONG:
            return decodeLong(encoded);
        case Field.TYPE_DOUBLE:
            return decodeDouble(encoded);
        case Field.TYPE_STRING:
            return decodeString(encoded);
        case Field.TYPE_CHAR:
            return decodeChar(encoded);
        case Field.TYPE_DATE:
            return decodeDate(encoded);
        case Field.TYPE_TIME:
            return decodeTime(encoded);
        case Field.TYPE_TIMESTAMP:
            return decodeTimestamp(encoded);
        case Field.TYPE_BINARY:
            return decodeBinary(encoded);
        case Field.TYPE_OBJECTID:
            return decodeObjectID(encoded);
        default:
            throw new IllegalArgumentException("Unknown type " + type);
        }
    }


}
