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

import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.common.ByteArrays;

import java.util.Map;
import java.util.Random;
import java.util.HashMap;
import java.util.Iterator;

public class DavTestSchema {

    // The number of stringN attributes in the schema
    private static final int NSVARS = 4;

    // The no. of longN attributes
    private static final int NLVARS = 4;

    private static final int UNRESTRICTED = 0;

    private static Map nameMap = null;

    private static char[] chars = null;

    private static Random random = null;

    // The domain size for the PRNG
    private int domainSize = UNRESTRICTED;

    // Upper limit for length of random strings
    private int rStringLen = 10;

    private String[] rStrings = null;
    private Long[] rLongs = null;
    private Double[] rDoubles = null;

    /*
     * URLs are constructed using patterns: one pattern for the dir
     * path, and another for the filename. Each character in the
     * pattern represents an attribute in the schema. The digits
     * {1..9} represent attributes davtest.long{1..9}, and the letters
     * {A..Z} are davtest.string{A..Z}. The char 'd' is
     * davtest.double1, and 'l' is system.test.type_long.
     *
     * Example: {"12AB", "34CD"} =>
     *    /$long1/$long2/$string1/$string2/$long3_$long4_$string3_$string4
     *
     * The dir components are separated by '/' and the file components
     * are separated by '_'.
     */

    static class URLSpec {
        String name = null;
        String dPat = null;
        String fPat = null;
        URLSpec(String v, String d, String f) {
            name = v;
            dPat = d;
            fPat = f;
        }
    }

    static private URLSpec[] views = null;

    static {
        nameMap = new HashMap();
        int i;
        char c;

        for (i = 1, c = '1'; i <= NLVARS; i++, c++)
            nameMap.put(new Character(c).toString(), "davtest.long" + c);
        for (i = 1, c = 'A'; i <= NSVARS; i++, c++)
            nameMap.put(new Character(c).toString(), "davtest.string" + i);
        nameMap.put("d", "davtest.double1");
        nameMap.put("l", "system.test.type_long");

        i = 0;
        chars = new char[62];
        for (c = '0'; c <= '9'; c++)
            chars[i++] = c;
        for (c = 'a'; c <= 'z'; c++)
            chars[i++] = c;
        for (c = 'A'; c <= 'Z'; c++)
            chars[i++] = c;

        // Initialize the patterns of known views

        i = 0;
        views = new URLSpec[10];
        views[i++] = null;
        views[i++] = new URLSpec("davtest1", "1234", "ABCD");
        views[i++] = new URLSpec("davtest2", "3C4D", "1A2B");
        views[i++] = new URLSpec("davtest3", "34CD", "AB12");
        views[i++] = new URLSpec("davtest4", "C34D", "12AB");
        views[i++] = new URLSpec("davtest5", "1Ad", "1dA");
        views[i++] = new URLSpec("davtest6", "1234", "ABCD");
        views[i++] = new URLSpec("davtest7", "12", "ABCD");
        views[i++] = new URLSpec("davtest8", "1d", "A");
        views[i++] = new URLSpec("davtest9", "BC", "1A");

        init();
    }

    public static void init() {
        init(System.currentTimeMillis());
    }

    public static void init(long seed) {
        random = new Random(seed);
    }

    public DavTestSchema() {
        populateValues();
    }

    public void setPrngDomainSize(int size) {
        boolean valueChanged = (domainSize != size);
        domainSize = size;
        if (valueChanged)
            populateValues();
    }

    public void setRandomStringSize(int size) {
        boolean valueChanged = (rStringLen != size);
        rStringLen = size;
        if (valueChanged)
            populateValues();
    }

    /**
     * Add random values for each davtest attribute to the Map.
     */
    public void addDavMD(Map md) throws HoneycombTestException {

        for (int j = 1; j <= NSVARS; j++)
            md.put("davtest.string"+j, getString());
        for (int j = 1; j <= NLVARS; j++)
            md.put("davtest.long"+j, getLong());

        md.put("davtest.double1", getDouble());
    }

    private String getDavTestName(Map attrs,
                                  String name, String dPat, String fPat)
            throws HoneycombTestException {
        StringBuffer sb = new StringBuffer();

        sb.append("/webdav/").append(name);

        // Directories
        for (int i = 0; i < dPat.length(); i++) {
            String c = dPat.substring(i, i+1);
            String var = (String) nameMap.get(c);
            Object value = attrs.get(var);
            if (value == null)
                throw new HoneycombTestException("No attribute " + c + ":" +
                                                 image(var));
            sb.append('/').append(value.toString());
        }

        sb.append('/');

        // Filename
        String delim = "";
        for (int i = 0; i < fPat.length(); i++) {
            String c = fPat.substring(i, i+1);
            String var = (String) nameMap.get(c);
            Object value = attrs.get(var);
            if (value == null)
                throw new HoneycombTestException("No attribute " + c + ":" +
                                                 image(var));
            sb.append(delim).append(value.toString());
            delim = "_";
        }

        return sb.toString();
    }

    public String getDavTestName(Map md, int viewIndex)
            throws HoneycombTestException {
        return getDavTestName(md, views[viewIndex].name,
                              views[viewIndex].dPat, views[viewIndex].fPat);
    }

    public String getDavTestShortName(Map md, int viewIndex)
            throws HoneycombTestException {
        int dirLen = views[viewIndex].dPat.length()/2;
        return getDavTestName(md, views[viewIndex].name,
                              views[viewIndex].dPat.substring(0, dirLen),
                              views[viewIndex].fPat);
    }

    private void populateValues() {
        rStrings = new String[domainSize];
        rLongs = new Long[domainSize];
        rDoubles = new Double[domainSize];

        for (int i = 0; i < domainSize; i++) {
            rStrings[i] = randomString();
            rLongs[i] = randomLong();
            rDoubles[i] = randomDouble();
        }
    }

    ////////////////////////////////////////////////////////////////////
    // statics

    /** See if the davtest schema is loaded on the server "dav" */
    public static boolean isSchemaLoaded(WebDAVer dav) {
        String msg = "Schema \"davtest\" not loaded";

        try {
            if (dav != null && dav.getFile("/webdav/davtest1/", false).pass)
                return true;
        }
        catch (HoneycombTestException e) {
            msg += " (" + e.getMessage() + ")";
        }

        Log.ERROR(msg);
        return false;
    }

    public static void compareMaps(Map md1, Map md2)
            throws HoneycombTestException {
        String err = null;

        // Check OID and the variables of the davtest schema

        String oid1 = (String) md1.get("system.object_id");
        String oid2 = (String) md2.get("system.object_id");

        if (oid1 == null || !oid1.equals(oid2))
            throw new HoneycombTestException("OIDs differ: [" + oid1 +
                                             "] =/= [" + oid2 + "]");

        for (Iterator i = nameMap.keySet().iterator(); i.hasNext(); ) {
            String var = (String) nameMap.get(i.next());

            if (var.startsWith("system.test."))
                continue;

            if (!typesEqual(var, md1, md2))
                throw new HoneycombTestException("\"" + var + "\" types: " +
                                                 fmtTypes(var, md1, md2));

            if (!valuesEqual(var, md1, md2))
                throw new HoneycombTestException("\"" + var + "\" values: " +
                                                 fmtValues(var, md1, md2));
        }

    }

    /** Compare a key value in two Maps */
    private static boolean valuesEqual(String key, Map md1, Map md2) {
        Object v1 = md1.get(key);
        Object v2 = md2.get(key);

        if (v1 == null && v2 == null)
            return true;

        if (v1 != null && v2 != null)
            return v1.toString().equals(v2.toString());

        return false;
    }

    /** Compare the types of a key value in two Maps */
    private static boolean typesEqual(String key, Map md1, Map md2) {
        Object v1 = md1.get(key);
        Object v2 = md2.get(key);

        if (v1 == null && v2 == null)
            return true;

        if (v1 != null && v2 != null)
            return v1.getClass().getName().equals(v2.getClass().getName());

        return false;
    }

    /** Format the values from two maps for the same key */
    private static String fmtValues(String key, Map md1, Map md2) {
        return image(md1.get(key)) + " =/= " + image(md2.get(key));
    }

    /** Format the values from two maps for the same key */
    private static String fmtTypes(String key, Map md1, Map md2) {
        return md1.get(key).getClass() + " =/= " + md2.get(key).getClass();
    }

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

    public static String toString(Map md) {
        String delim = "";
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        for (Iterator i = md.keySet().iterator(); i.hasNext(); ) {
            sb.append(delim); delim = " ";

            String key = (String) i.next();
            sb.append(key).append("=");
            sb.append(image(md.get(key)));
        }
        sb.append("]");

        return sb.toString();
    }

    ////////////////////////////////////////////////////////////////////
    // Private

    /** Return a random positive Long value */
    private Long getLong() {
        if (domainSize == UNRESTRICTED)
            return randomLong();
        else
            return (Long) randomElement(rLongs);
    }

    /** Return a random ASCII string with length upto len (inclusive) */
    private String getString() {
        if (domainSize == UNRESTRICTED)
            return randomString();
        else
            return (String) randomElement(rStrings);
    }

    /** Select a random positive Double value */
    private Double getDouble() {
        if (domainSize == UNRESTRICTED)
            return randomDouble();
        else
            return (Double) randomElement(rDoubles);
    }

    /** Return a random positive Long value */
    private Long randomLong() {
        long l = random.nextLong();
        if (l < 0)
            l = -l;
        return new Long(l);
    }

    /** Return a random positive Double value */
    private Double randomDouble() {
        double d = random.nextDouble();
        if (d < 0)
            d = -d;
        return new Double(d);
    }

    /** Return a random ASCII string */
    private String randomString() {
        return newRandomString(rStringLen);
    }

    /** Return a random ASCII string with length upto len (inclusive) */
    public static String newRandomString(int maxlen) {

        // The size of the alphabet is N. There are N^n strings of
        // length n (or, there are N times as many strings of length n
        // as there are of length n - 1). The length of the string to
        // generate should have the same distribution: k+1 should be
        // n times as likely as k. If the max length is m,
        //     strlen = 1 + log(rand(exp(m)))

        double r = random.nextDouble() * Math.exp((double)maxlen);
        int strlen = 1 + (int) Math.log(r);

        return newRandomFixedLengthString(strlen);
    }


    /** Return a random ASCII string with length upto len (inclusive) */
    public static String newRandomFixedLengthString(int strlen) {
        StringBuffer s = new StringBuffer();

        for (int i = 0; i < strlen; i++)
            s.append(chars[random.nextInt(chars.length)]);

        return s.toString();
    }

    /** Return a random element of an array */
    public static Object randomElement(Object[] obj) {
        if (obj == null || obj.length == 0)
            return null;

        int i = random.nextInt(obj.length);
        if (i < 0)
            i = -i;

        return obj[i];
    }

}

