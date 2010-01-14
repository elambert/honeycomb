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

import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

import com.sun.honeycomb.client.NameValueSchema;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *  Misc static methods for HC test.
 */
public class HCUtil {

    // Pathnames for access to Honeycomb disks
    public static final char dirSep = '/'; // matches oa/common/Common.java
    public static final String localData = dirSep + "data" + dirSep; 

    /**
     *  Handle size notation like '1k' '12m' '6g', with honeycomb-
     *  specific values of 'b' for block size, 'f' for fragment size,
     *  and 'e' for chunk/extent size.
     */
    public static long parseSize(String s) throws NumberFormatException {

        String size = s.trim();
        long mult = 1;
        if (size.endsWith("k")  ||  size.endsWith("K")) {
            mult = 1024;
        } else if (size.endsWith("m")  ||  size.endsWith("M")) {
            mult = 1024 * 1024;
        } else if (size.endsWith("g")  ||  size.endsWith("G")) {
            mult = 1024 * 1024 * 1024;
        } else if (size.endsWith("f")  ||  size.endsWith("F")) {
            mult = HoneycombTestConstants.OA_FRAGMENT_SIZE;
        } else if (size.endsWith("b")  ||  size.endsWith("B")) {
            mult = HoneycombTestConstants.OA_BLOCK_SIZE;
        } else if (size.endsWith("e")  ||  size.endsWith("E")) {
            mult = HoneycombTestConstants.OA_MAX_CHUNK_SIZE;
        }
        String size2 = size;
        if (mult != 1)
            size2 = size.substring(0, size.length()-1);
        long lsize = Long.parseLong(size2);
        lsize *= mult;
        return lsize;
    }

    /**
     *  Handle time notation like '1s' '12m' '6h' '2d' '3w'.
     */
    public static long parseTime(String s) throws NumberFormatException {

        String time = s.trim();

        if (time.length() == 0)
            throw new NumberFormatException("empty string");

        char c = time.charAt(time.length()-1);

        long mult = 1;
        if (Character.getType(c) != Character.DECIMAL_DIGIT_NUMBER) {

            switch (c) {
                case 'm': // minute
                    mult = 60000; break;
                case 's': // second
                    mult = 1000; break;
                case 'h': // hour
                    mult = 60 * 60000; break;
                case 'd': // day
                    mult = 24 * 60 * 60000; break;
                case 'w': // week
                    mult = 7 * 24 * 60 * 60000; break;
                default:
                    throw new NumberFormatException("unexpected symbol: " + c);
            }

            if (time.length() == 1)
                return mult;

            time = time.substring(0, time.length()-1);
        }

        long t = Long.parseLong(time);
        t *= mult;
        return t;
    }

    /**
     *   Return a string that represents megabytes per second.
     */
    public static String megabytesPerSecond(long msecs, long bytes) {
        // multiply in constants while converting to double
        double time = (new Long(msecs)).doubleValue() * 1024.0 * 1024.0;
        double size = (new Long(bytes)).doubleValue() * 1000.0;

        //
        //  In the absence of an obvious number formatting ability,
        //  hack around Double.toString()'s switch to exponential
        //  notation for numbers < .001
        //
        double mbytesPerSecond = 0.0;
        if (time != 0.0) {
            mbytesPerSecond = size / time;
            if (mbytesPerSecond < 0.0005) {
                mbytesPerSecond = 0.0;
            } else if (mbytesPerSecond < 0.001) {
                mbytesPerSecond = 0.001;
            }
        }
        // truncate & round
        String s = Double.toString(mbytesPerSecond);
        int i = s.indexOf(".");
        if (s.length() > i+4  &&  s.charAt(i+4) > '4') {
            mbytesPerSecond = Double.parseDouble(s.substring(0, i+4));
            mbytesPerSecond += .001;
            s = Double.toString(mbytesPerSecond);
        } 
        if (s.length() > i+4)
            s = s.substring(0, i+4);

        return (s + " MB/sec");
    }

    /**
     *   Return a string that represents gigs per day.
     */
    public static String gigsPerDay(long msecs, long bytes) {
        double gigsPerHour = 0.0;
        double gigsPerDay = 0.0;
        double gigs = ((double)bytes) / (1024 * 1024 * 1024);
        double hours = ((double)msecs) / (1000 * 60 * 60);

        if (hours != 0.0) {
            gigsPerHour = gigs / hours;
            gigsPerDay = gigsPerHour * 24;
        }
            
        return (gigsPerDay + "GB/day");
    }

    /**
     *  Calculate the SHA1 id of the given string.
     */
    private static final int HEX_RADIX = 16;
    private static final int BITS_PER_NIBBLE = 4;
    public static String getSHA1(String message) throws HoneycombTestException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
            byte b[] = md.digest(message.getBytes());

            StringBuffer sbHash = new StringBuffer();
            for (int i=0; i<b.length; i++) {
                sbHash.append(Character.forDigit(
                                              (b[i] & 0xF0) >> BITS_PER_NIBBLE,
                                                         HEX_RADIX));
                sbHash.append(Character.forDigit(b[i] & 0x0F, HEX_RADIX));
            }
            return sbHash.toString();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    /**
     *  Get hash dirs as in an earlier version HC itself, 
     *  by hashing an oid's uid (any string would do). HC now
     *  uses layout which is part of the internal oid.
     */
    public static String getHashDirs(String oid) throws HoneycombTestException {
        String uidHash = getSHA1(oid);
        String dirA = uidHash.substring(0, 2);
        String dirB = uidHash.substring(2, 4);
        return "/" + dirA + "/" + dirB + "/";
    }
    public static String getHashPath(String oid) throws HoneycombTestException {
        return getHashDirs(oid) + oid;
    }

    /**
     *  Convert raw hash bytes to a string.
     */
    public static String convertHashBytesToString(byte[] rawHash) {
        // XXX This code was copied from OA.
        StringBuffer sbHash = new StringBuffer();
        for(int i = 0; i < rawHash.length; i++) {
            sbHash.append(Character.forDigit((rawHash[i] & 0xF0) >> 4, 16));
            sbHash.append(Character.forDigit(rawHash[i] & 0x0F, 16));
        }
        return (sbHash.toString());
    }

    /**
     *  Calc hash on file using java (rather than shell to sha1sum).
     */
    public static String computeHash(String filename) 
                                                throws HoneycombTestException {
        String hashAlg = HoneycombTestConstants.CURRENT_HASH_ALG;
        String computedHash = null;

        try {

            MessageDigest md = MessageDigest.getInstance(hashAlg);

            File f = new File(filename);

            long len = f.length();
            
            // 0 byte file...
            if (len == 0)
            	return convertHashBytesToString(md.digest());
            
            byte[] buf;
            if (f.length() > HoneycombTestConstants.MAX_ALLOCATE)
                buf = new byte[HoneycombTestConstants.MAX_ALLOCATE];
            else
                buf = new byte[(int)len];

            FileInputStream in = new FileInputStream(f);

            int bytesRead;

            while ((bytesRead = in.read(buf)) != -1) {
                md.update(buf, 0, bytesRead);
            }
            in.close();

            computedHash = convertHashBytesToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new HoneycombTestException("Couldn't compute " + hashAlg +
                ":  No such algorithm");
        } catch (IOException e) {
            throw new HoneycombTestException("Couldn't compute " + hashAlg +
                ": " + e.getMessage());
        }

        return computedHash;
    }

    // This is code from
    //   ByteArrays.java 3458 2005-02-07 20:03:55Z rw151951
    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuffer result = new StringBuffer(bytes.length * 2);

        for (int i = 0; i < bytes.length; i++) {
            result.append(Character.forDigit((bytes[i] & 0xF0) >> BITS_PER_NIBBLE,
                                          HEX_RADIX));
            result.append(Character.forDigit(bytes[i] & 0x0F, HEX_RADIX));
        }

        return result.toString();
    }

    //TODO: pull this method out since it now rightly exists in test/util/Util (elambert)
    public static boolean equalityNullTest(Object obj) {
	return equalityDifferentInstanceTest(obj, null);
    }


    //TODO: pull this method out since it now rightly exists in test/util/Util (elambert)
    public static boolean equalityDifferentInstanceTest(Object thisObj, Object thatObj) {
	if (isEqual(thisObj, thatObj))
	    return false;
	else
	    return true;
    }

    //TODO: pull this method out since it now rightly exists in test/util/Util (elambert)
    public static boolean equalityTransitiveTest(Object obj1, Object obj2, Object obj3) {
	return (isEqual(obj1,obj2) && isEqual(obj2,obj3) && isEqual(obj1,obj3)) ;
    }

    //TODO: pull this method out since it now rightly exists in test/util/Util (elambert)
    public static boolean equalitySymmetryTest(Object thisObj, Object thatObj) {
	return (isEqual(thisObj,thatObj) && isEqual(thatObj,thisObj)); 
    }

    //TODO: pull this method out since it now rightly exists in test/util/Util (elambert)
    public static boolean equalityReflexiveTest(Object thisObj) {
	return (isEqual(thisObj,thisObj)); 
    }

    //TODO: pull this method out since it now rightly exists in test/util/Util (elambert)
    public static boolean equalityInequalityTest(Object thisObj, Object thatObj) {
	return (!isEqual(thisObj,thatObj));
    }

    /**
     * Simple wrapper around equals() method that swallows an 
     * exceptions.
     */
    //TODO: pull this method out since it now rightly exists in test/util/Util (elambert)
    private static boolean isEqual(Object thisObj, Object thatObj) {
	try {
	    return thisObj.equals(thatObj);
	}
	catch (Throwable t) {
	    return false;
	}
    }

    /** Convenience method to sleep for given number of seconds, reason is logged.
     */
    public static void doSleep(long seconds, String note) {
        if (seconds < 1) return;
        try {
            if (seconds > 1) // don't clutter log for short sleeps
                Log.INFO("Sleeping " + seconds + " seconds [ " + note + " ]");
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException ie) { 
            Log.INFO("Sleep interrupted"); 
        }
    }

    public static String readLines(BufferedReader reader)
        throws IOException
    {
        StringBuffer lines = new StringBuffer();
        String line = null;
        while ((line = reader.readLine()) != null) {
            lines.append(line);
            lines.append("\n");
        }
        return lines.toString();
    }
    
    public static String getHostname(){
    	try {
            InetAddress addr = InetAddress.getLocalHost();
        
            // Get hostname
            String hostname = addr.getHostName();
            return hostname;
        } catch (UnknownHostException e) {
        	return "unresolved hostname";
        }
    }

    public static String getIPFromHostname(String hostname) {
        String ipaddr = null;
        try {
            InetAddress ia = InetAddress.getByName(hostname);
            if (ia != null) {
                ipaddr = ia.getHostAddress();
            }
        } catch (Throwable t) {
            Log.WARN("Failed to lookup ip for " + hostname + ": " + t);
        }
        
        return (ipaddr);
    }

    public static boolean schemaHasAttribute(NameValueSchema nvs, String att) {
        NameValueSchema.Attribute[] attributes = nvs.getAttributes();
        for (int i=0; i<attributes.length; i++) {
            if (attributes[i].getName().equals(att))
                return true;
        }
        return false;
    }

    /** Convert numeric layout map ID "1234" to 2-level dirname "12/34"
     */
    public static String layoutMapToDir(int mapId) {
        String layoutMapId = new Integer(mapId).toString();
        while (layoutMapId.length() < 4) {
            layoutMapId = '0' + layoutMapId;
        }
        String dir = layoutMapId.substring(0,2) + 
            dirSep + layoutMapId.substring(2,4);
        return dir;
    }
}

