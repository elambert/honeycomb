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

import java.util.Map;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;

import com.sun.honeycomb.common.ByteArrays;

import java.util.regex.Pattern;

/**
 * This class encapsulates a few string util functions useful for
 * logging etc. The intent is that any value can be converted to a
 * string that is suitable for logging.
 *
 * @author Shamim Mohamed
 * @version $Version$ $Date: 2007-07-13 07:38:07 -0700 (Fri, 13 Jul 2007) $
 */
public class StringUtil {

    private static final String ellipsis = "...";

    // Bracket characters
    private static final String bra = "<";
    private static final String ket = ">";

    /**
     * Return a loggable string version of the value with
     * non-printable and special characters quoted and with type
     * information. Strings are quoted with double quotes, and other
     * objects have their class name and toString().
     */
    public static String image(Object value) {
        if (value == null)
            return "(null)";

        return addTags(value, quotedValue(value));
    }

    /**
     * The same as image(Object) but with a length restriction:
     * a trailing ellipsis (...) signifies that the value has been
     * truncated.
     */
    public static String image(Object value, int maxLength) {
        String s = image(value);
        if (s.length() <= maxLength)
            return s;

        // How much the string runs over by, including space for the ellipsis
        int ovflo = s.length() - maxLength + ellipsis.length();

        String qValue = quotedValue(value);
        int vLen = qValue.length() - ovflo;

        if (vLen <= 0)
            // Not enough space for the value
            return getTag(value) + bra + ellipsis + ket;
        else
            // Remove the final ovflo characters and add the ellipsis
            return addTags(value, qValue.substring(0, vLen) + ellipsis);
    }

    public static String imageLatin1(Object value) {
        if (value == null)
            return "(null)";

        return addTags(value, quotedLatin1Value(value));
    }

    public static String mapToString(Map q) {
        StringBuffer b = new StringBuffer();

        String delim = "";

        b.append("Map").append(bra);
        for (Iterator i = q.keySet().iterator(); i.hasNext(); ) {
            Object key = i.next();
            b.append(delim).append(image(key)).append("==>");
            b.append(image(q.get(key)));
            delim = ", ";
        }
        return b.append(ket).append("Map").toString();
    }

    ////////////////////////////////////////////////////////////////////////

    private static String quotedValue(Object value) {
        if (value instanceof byte[])
            return ByteArrays.toHexString((byte[])value);

        return escapeSpecial(value.toString());
    }

    private static String quotedLatin1Value(Object value) {
        if (value instanceof byte[])
            return ByteArrays.toHexString((byte[])value);

        return escapeNonLatin1(value.toString());
    }

    private static String addTags(Object value, String strValue) {
        if (value instanceof String)
            return "\"" + strValue + "\"";
        
        if (value instanceof Number)
            return strValue;

        if (value instanceof byte[])
            return "0x" + strValue;

        String className = getTag(value);
        return className + bra + strValue + ket + className;
    }

    private static String getTag(Object o) {
        return o.getClass().getName().replaceAll("^.*\\.", "");
    }

    /**
     * Replace non-printing and special characters with their
     * backslash equivalents.
     */
    private static String escapeSpecial(String s) {
        for (Iterator i = backslashSpecials.keySet().iterator(); i.hasNext(); ) {
            String spc = (String) i.next();
            String repl = (String) backslashSpecials.get(spc);
            s = s.replaceAll(spc, repl);
        }
        return s;
    }

    /**
     * Replace non-Latin1 characters with their unicode escaped equivalents.
     */
    private static String escapeNonLatin1(String s) {
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < s.length(); i++) {
            int c = s.charAt(i);

            if (c <= 255)
                sb.append(escapeSpecial(s.substring(i, i+1)));
            else {
                // Is there no short way of saying %08x in Java?
                String hex = "00000000" + Integer.toHexString(c);
                sb.append("\\u").append(hex.substring(hex.length() - 4, hex.length()));
            }
        }

        return sb.toString();
    }

    private static Map backslashSpecials = null;
    static {
        backslashSpecials = new HashMap();
        backslashSpecials.put(Pattern.quote("\""), "\\\\\"");
        backslashSpecials.put(Pattern.quote("\n"), "\\\\n");
        backslashSpecials.put(Pattern.quote("\r"), "\\\\r");
        backslashSpecials.put(Pattern.quote("\t"), "\\\\t");
        backslashSpecials.put(Pattern.quote("\f"), "\\\\f");
        backslashSpecials.put(Pattern.quote("\b"), "\\\\b");

        // Should the bracket characters be quoted if they occur
        // inside a value? Yes.
        backslashSpecials.put(Pattern.quote(bra), "\\\\" + bra);
        backslashSpecials.put(Pattern.quote(ket), "\\\\" + ket);

        backslashSpecials.put(Pattern.quote("\\"), "\\\\\\\\"); // !!!
    }

    /* Testing */

    public static void main(String[] argv) {
        Object[] objs = new Object[] {
            "Header: value\r\n\r\n<HTML/>\n<!--\n---\r+++\r\r000\n\n\"\\",
            "A very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "very very very very very very very very very very very very " +
            "long line",
            ByteArrays.toByteArray("689a390bc3e909f520f76e786a7606535a76c76e"),
            new Integer(42),
            new Double(3.14159265354),
            new Date(System.currentTimeMillis()),
            new ArchiveException("Unknown error <42> occurred!"),
            "\uc3a1\uc3a9\uc3ad\uc3b3\uc3ba\uc3a0\uc3a8\uc3ac\uc3b2\uc3b9\uc3a4\uc3ab\uc3af\uc3b6\uc3bc\uc384\uc38b\uc38f\uc396\uc39c",
            "\u043c\u043d\u0435 \u043d\u0435 \u0432\u0440\u0435\u0434\u0438\u0442",
            "\u0986\u09ae\u09be\u09b0 \u0995\u09cb\u09a8\u09cb",
            "\u305d\u308c\u306f\u79c1\u3092\u50b7\u3064\u3051\u307e\u305b\u3093"
        };

        System.err.println("\r\n");

        int lineLen = 30;
        for (int i = 0; i < objs.length; i++) {
            System.out.println(i + "\t" + image(objs[i]));

            String line = image(objs[i], lineLen);
            System.out.println("\t" + line + " (" + line.length() + ")");

            System.out.println("\t" + imageLatin1(objs[i]));
        }

        Map m = new HashMap();

        for (int i = 0; i < objs.length; i++)
            m.put(objs[i], new Integer(i));

        System.out.println("\n    " + mapToString(m));

    }
}
