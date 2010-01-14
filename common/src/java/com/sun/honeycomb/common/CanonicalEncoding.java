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

import java.util.TimeZone;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharsetDecoder;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;

import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import com.sun.honeycomb.common.Encoding;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class CanonicalEncoding {

    public static final String CANONICAL_TIMESTAMP_FORMAT = 
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static final String CANONICAL_DATE_FORMAT = "yyyy-MM-dd";

    public static final String CANONICAL_TIME_FORMAT = "HH:mm:ss";

    public static final String ISO8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    // We need a Latin1 encoder to call in validateChar, so do it once only.
    // All Charset operations are thread-safe.
    public static Charset latin1Set = Charset.forName("ISO-8859-1");

    public static Pattern nullPattern = Pattern.compile(".*\0.*");
    // Pattern check for singleton unicode supplemental code points 
    // as well as unicode surrogate pairs in range [10000-10FFFF].  
    public static Pattern uniSuppPattern = Pattern.compile(".*([\0]|" +
                              "[\uD800\uDC00-\uDBFF\uDFFF]|[\uD800-\uDFFF]).*");

    private static final Logger LOG = Logger.getLogger(CanonicalEncoding.class.getName());

    protected CanonicalEncoding() {
        //Static methods only.   Never instantiate this class.
    }


    public static String encode (Object obj, String name) {
        
        //Do some type checking?
        return encode(obj);
    }

    public static String encode (Object obj) {
        if (obj instanceof String) {
            return encode((String) obj);
        } else if (obj instanceof Long) {
            return encode((Long) obj);
        } else if (obj instanceof Double) {
            return encode((Double) obj);
        } else if (obj instanceof Date) {
            return encode((Date) obj);
        } else if (obj instanceof Time) {
            return encode((Time) obj);
        } else if (obj instanceof Timestamp) {
            return encode((Timestamp) obj);
        } else if (obj instanceof byte[]) {
            return encode((byte[]) obj);
        } else if (obj instanceof ExternalObjectIdentifier) {
            return encode((ExternalObjectIdentifier) obj);
        } else if (obj == null) {
            return "";
        } else {
            throw new IllegalArgumentException("object of unhandled type: "+
                                               obj.getClass().getCanonicalName());
        }
    }
  
    public static byte[] strToUTF8(String s) {
        String string = s;
        byte[] strBytes;
        ByteBuffer bbuf;
        int actBufSize;
   
        try {
          CharsetEncoder enc = Charset.forName("UTF-8").newEncoder();
          bbuf = enc.encode(CharBuffer.wrap(string));
          bbuf.rewind();
          actBufSize = bbuf.remaining(); 
          strBytes = new byte[actBufSize];
          bbuf.get(strBytes, 0,  actBufSize);  
        } catch (CharacterCodingException e) {
          throw new IllegalArgumentException("UTF-8 encoding failed due to"
             + " malformed or unmappable string input");
        }
        return (strBytes);
    }

    public static String utf8ToString (byte[] b) {
        String string;
        byte[] strBytes = b;

        try {
          CharsetDecoder dec = Charset.forName("UTF-8").newDecoder();
          string = dec.decode(ByteBuffer.wrap(strBytes)).toString();
        } catch (CharacterCodingException e) {
          throw new IllegalArgumentException("UTF-8 decoding failed due to"
             + " malformed or unmappable string input");
        }
        return (string);
    }


    public static String encode (Long l) {
        return l.toString();
    }

    public static String encode (Double d) {
        return d.toString();
    }

    public static String encode (String s) {
        return s;
    }


    ///Encode time value as hh:mm:ss.fff
    public static String encode (Time t) {
        return t.toString();
    }

    public static String encode (Date d) {
        return d.toString();

    }

    public static String encode (Timestamp ts) {
        SimpleDateFormat formatter = 
            new SimpleDateFormat(CANONICAL_TIMESTAMP_FORMAT);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(ts);
    }

    public static String encode (byte[] binary) {
        return ByteArrays.toHexString(binary);
    }

    public static Long decodeLong(String encoded){
        return new Long(encoded);
    }

    public static Double decodeDouble(String encoded){
        return new Double(encoded);
    }

    public static String decodeChar(String encoded) {
        return encoded;
    }

    public static String decodeString(String encoded) {
        return encoded;
    }

    public static String encode (ExternalObjectIdentifier oid) {
        return oid.toString();
    }

    public static java.util.Date decodeJavaDate(String encoded, 
                                                String timePattern,
                                                String timeZoneName) {
        SimpleDateFormat formatter = new SimpleDateFormat(timePattern);
        formatter.setTimeZone(TimeZone.getTimeZone(timeZoneName));

        java.util.Date d = null;

        try {
            //throws NPE if encoded is null
            d = formatter.parse(encoded, new ParsePosition(0));
            if (d == null)
                throw new IllegalArgumentException("Cannot parse date/time value '"+
                                                   encoded+"' with pattern '"+
                                                   timePattern+"'");
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(e);
        }

        return d;
    }

    public static java.util.Date decodeISO8601Date(String s) {
        // There is some doubt about whether this should always be UTC,
        // or if the timezone should be extracted from the string itself
        return decodeJavaDate(s, ISO8601_FORMAT, "UTC");
    }

    public static Timestamp decodeTimestamp(String encoded){
        java.util.Date dd = decodeJavaDate(encoded, 
                                           CANONICAL_TIMESTAMP_FORMAT,
                                           "UTC");
        return new Timestamp(dd.getTime());

    }

    public static Date decodeDate(String encoded){

        return Date.valueOf(encoded);
    }

    public static Time decodeTime(String encoded){

        return Time.valueOf(encoded);
    }

    public static byte[] decodeBinary(String encoded){
        return ByteArrays.toByteArrayLeftJustified(encoded);
    }

    public static ExternalObjectIdentifier decodeObjectID(String encoded){
        return new ExternalObjectIdentifier(encoded);
    }

    public static void validateString(String strValue)  {
        Matcher m = uniSuppPattern.matcher(strValue);
        if (m.matches()) {
            throw new IllegalArgumentException(image(strValue)+" is not a valid string value");
        }
    }

    public static void validateChar(String charValue)  {
        CharsetEncoder latin1Encoder = latin1Set.newEncoder();
        Matcher m = nullPattern.matcher(charValue);
        if (m.matches())
            throw new IllegalArgumentException(image(charValue)+" is not a valid char value");
        if (!latin1Encoder.canEncode(charValue))
            throw new IllegalArgumentException(image(charValue)+" is not a valid char value");
    }

    public static void validateBinary(String binaryValue) {
        if (! binaryValue.matches("[A-Fa-f0-9]*")) 
            throw new IllegalArgumentException(image(binaryValue)+" is not a valid binary value");
    }

    public static void validateDate(String dateValue) {
        SimpleDateFormat formatter = new SimpleDateFormat(CANONICAL_DATE_FORMAT);
        if (! dateValue.matches(formatter.toPattern()))
            throw new IllegalArgumentException(image(dateValue)+" is not a valid date value");
    }

    public static void validateTime(String timeValue) {
        SimpleDateFormat formatter = new SimpleDateFormat(CANONICAL_TIME_FORMAT);
        if (! timeValue.matches(formatter.toPattern()))
            throw new IllegalArgumentException(image(timeValue)+" is not a valid time value");
    }

    public static void validateTimestamp(String timestampValue) {
        SimpleDateFormat formatter = new SimpleDateFormat(CANONICAL_TIMESTAMP_FORMAT);
        if (! timestampValue.matches(formatter.toPattern()))
            throw new IllegalArgumentException(image(timestampValue)+" is not a valid timestamp value");
    }



    static class Test{
        void test (Date d) {
            System.err.println("Date test:");
            System.err.println(d + "("+d.getTime()+"): " +
                CanonicalEncoding.encode(d) + " " +
                CanonicalEncoding.decodeDate(CanonicalEncoding.encode(d)) + " " +
                d.equals((CanonicalEncoding.decodeDate(CanonicalEncoding.encode(d)))));
        }

        void test (Time t) {
            System.err.println("Time test:");
            System.err.println(t + "("+t.getTime()+"): " + 
                CanonicalEncoding.encode(t) + " " +
                CanonicalEncoding.decodeTime(CanonicalEncoding.encode(t)) + " " +
                t.equals(CanonicalEncoding.decodeTime(CanonicalEncoding.encode(t))));
        }

        void test (Timestamp t) {
            System.err.println("Timestamp test:");
            System.err.println(t + "("+t.getTime()+"): " +
                CanonicalEncoding.encode(t) + " " + 
                CanonicalEncoding.decodeTimestamp(CanonicalEncoding.encode(t)) + " " +
                t.equals(CanonicalEncoding.decodeTimestamp(CanonicalEncoding.encode(t))));
        }

        void test (byte[] bytes) {
            System.err.println("Binary test:");
            System.err.println(Arrays.toString(bytes) + ": " + CanonicalEncoding.encode(bytes) + " " + 
                               Arrays.toString(CanonicalEncoding.decodeBinary(CanonicalEncoding.encode(bytes))) + " " +
                               Arrays.equals(bytes, CanonicalEncoding.decodeBinary(CanonicalEncoding.encode(bytes))));
        }

        private void test() {
            test (Date.valueOf("2006-01-01"));
            test (Time.valueOf("12:34:56"));
            test (Timestamp.valueOf("2006-10-04 12:34:56.789"));
            test (Timestamp.valueOf("1970-01-01 00:00:00.000"));
            test (new Timestamp(0));
            test (new Timestamp(System.currentTimeMillis()));

            //See what happens across the magic 1583 boundary
            Timestamp bigYear = Timestamp.valueOf("1583-12-31 19:00:00.000");
            test (bigYear);
            test (new Timestamp(bigYear.getTime() - (365 *24 * 60 * 60 * 1000L)));
            test (new Timestamp(bigYear.getTime() - (2 * 365 * 24 * 60 * 60 * 1000L)));

            byte [] bytes = new byte[10];
            Arrays.fill(bytes,0,5,(byte)0xab);
            Arrays.fill(bytes,5,10,(byte)0x01);
            test (bytes);

            System.err.println(CanonicalEncoding.encode(new Double(1d)));

        }
    }

    public static String literalsToString(List literals) {
        if (literals == null)
            return "";
        StringBuilder sb = new StringBuilder();
        String separator="";
        for (int i = 0; i < literals.size(); i++) {
            sb.append(separator);
            separator = ", ";
            Object value = literals.get(i);
            sb.append(Encoding.getTagChar(value));
            sb.append("'");
            sb.append(encode(value));
            sb.append("'");
        }
        return sb.toString();
    }

    public static String parametersToString(Object[] boundParameters) {
        if (boundParameters == null)
            return "";
        return literalsToString(Arrays.asList(boundParameters));
    }

    /**
     * Return a printable version of the value. Non-printable
     * characters are converted to the appropriate Java String
     * literal syntax (\\uXXXX sequences)
     */
    public static String image(Object o) {
       
        if (o == null)
            return "(null)";

        if (o instanceof byte[])
            return "0x" + ByteArrays.toHexString((byte[])o);

        String s = o.toString();
        StringBuffer sb = new StringBuffer();

        if (o instanceof String)
            sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int ic = (int) c;

            if (c == '\\' || c == '\"' || c == '\'')
                sb.append('\\').append(c);
            else if (c == '\r')
                sb.append("\\r");
            else if (c == '\n')
                sb.append("\\n");
            else if (ic >= 32 && ic <= 126)
                sb.append(c);
            else {
                String hex = "000" + Integer.toHexString(ic & 0xffff);
                hex = hex.substring(hex.length() - 4);
                sb.append("\\u").append(hex);
            }
        }
        if (o instanceof String)
            sb.append('"');

        return sb.toString();
    }
    public static void main (String[] argv) {

        new Test().test();
    }
}
