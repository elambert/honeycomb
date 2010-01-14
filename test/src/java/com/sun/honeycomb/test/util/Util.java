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



package com.sun.honeycomb.test.util;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.sql.Time;

public class Util
{
    public static String join(String expr, String [] tokens) {
        StringBuffer sb = new StringBuffer();
        if (tokens.length > 0)
        {
            sb.append(tokens[0]);
            for (int i = 1; i < tokens.length; i++)
            {
                sb.append(expr);
                sb.append(tokens[i]);
            }
        }
        return sb.toString();
    }

    /** 
     * Given milliseconds since epoch, return a date string.
     */
    public static String msecDateString(long msSinceEpoch) {
        Date d = new Date(msSinceEpoch);
        return (d.toString());
    }

    /** 
     * Given milliseconds since epoch, return a date string and the
     * original msec string.
     */
    public static String msecDateStringVerbose(long msSinceEpoch) {
        return (msecDateString(msSinceEpoch) + " (" + msSinceEpoch + ")");
    }

    /** Given elapsed time in milliseconds,
        return a string formatted HH:MM:SS
    */
    public static String formatTime(long ms) {
        long h, m, s; 
        s = ms / 1000;      // ms -> seconds
        h = s / 3600;       // hours
        s = s - (h * 3600);
        m = s / 60;         // minutes
        s = s - (m * 60);
        s = (s > 0 ? s : 1);    // round up to 1 second
        java.text.NumberFormat time = new java.text.DecimalFormat("00");
        return time.format(h) + ":" + time.format(m) + ":" + time.format(s);
    }

    /** Return current date and time in terse format 
     */
    public static String currentTimeStamp() {
        java.text.SimpleDateFormat df = 
            new java.text.SimpleDateFormat("MM/dd/yy HH:mm:ss");
        return df.format(new java.util.Date(System.currentTimeMillis())) + " ";
    }

    /**
     *  See if regular expression is matched by Throwable.getMessage().
     *  Using "contains" logic, spanning multiple lines.
     *  Returns true if message contains the regex.
     *  
     *  If you want a stricter matching logic, write another matcher.
    **/
    public static boolean exceptionMatches(String regexp, Throwable t) {
        String msg = t.getMessage();

        // Some throwables don't have messages
        if (msg == null) {
            msg = "";
        }

        Log.DEBUG("regexp: " + regexp + "; exception msg: " + msg);
        return (msg.matches("(?s).*" + regexp + ".*"));
    }

    private static String localHostname = null;
    public static String localHostName() {
        if (localHostname == null) {
            try {
                localHostname = java.net.InetAddress.getLocalHost().getHostName().split("\\.")[0];
            } catch (java.net.UnknownHostException e) {
                localHostname = "unknown";
            }
        }
        return localHostname;
    }

    /** Convert numeric hours, minutes, seconds into a Time object
     *  I have to do this myself because the constructor I want
     *  Time(int h, int m, int s) - has been deprecated.
     *  Also Time is on GMT, so this method performs conversion.
     */
    public static Time duration(int hr, int min, int sec) {
        int off = Calendar.getInstance().get(Calendar.ZONE_OFFSET);
        // because Time is counted in GMT, and we may be local, subtract offset
        int ms = sec * 1000 + min * 60 * 1000 + hr * 60 * 60 * 1000 - off;
        return new Time(ms);
    }

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

    public static boolean equalityNullTest(Object obj) {
	return equalityDifferentInstanceTest(obj, null);
    }


    public static boolean equalityDifferentInstanceTest(Object thisObj, 
                                                        Object thatObj) {
	if (isEqual(thisObj, thatObj))
	    return false;
	else
	    return true;
    }

    public static boolean equalityTransitiveTest(Object obj1, Object obj2, 
                                                              Object obj3) {
	return (isEqual(obj1,obj2) && isEqual(obj2,obj3) && isEqual(obj1,obj3));
    }

    public static boolean equalitySymmetryTest(Object thisObj, Object thatObj) {
	return (isEqual(thisObj,thatObj) && isEqual(thatObj,thisObj)); 
    }

    public static boolean equalityReflexiveTest(Object thisObj) {
	return (isEqual(thisObj,thisObj)); 
    }

    public static boolean equalityInequalityTest(Object thisObj, 
                                                 Object thatObj) {
	return (!isEqual(thisObj,thatObj));
    }

    /**
     * Simple wrapper around equals() method that swallows an 
     * exceptions.
     */
    private static boolean isEqual(Object thisObj, Object thatObj) {
	try {
	    return thisObj.equals(thatObj);
	}
	catch (Throwable t) {
	    return false;
	}
    }
}
