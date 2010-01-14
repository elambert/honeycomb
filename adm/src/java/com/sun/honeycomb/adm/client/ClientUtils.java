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


package com.sun.honeycomb.adm.client;


import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.StringWriter;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.Date;
import java.math.BigInteger;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.time.Time;
import com.sun.honeycomb.admin.mgmt.client.HCSilo;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.PrecisionFormatter;
import com.sun.honeycomb.common.CliConstants;


/**
 * <p>
 * utils fuctions - what's not to love?
 * </p>
 *
 */
public class ClientUtils {
    
    /**
     * Create a comma seperated value (CSV) string form the passed in list
     * @param myList the list
     * @return String the coma seperated values string made from the passed
     * in list
     */
    public static  String getCSVFromList(List<String> myList) {
        String output=null;
        Iterator<String> iter = myList.iterator();
        while (iter.hasNext()) {

            String curVal=(String) iter.next();
            if(null==output) {
                output=curVal;
            } else {
                output=output+","+curVal;
            }
            
        }
        return output;
    }

    /**
     * Convert the command seperated values to a list
     * @param myCsv comma seperated values to convert to a list
     * @return List the values that taht where derived from the
     * the command seperated values
     */
    public static  List<String> getListFromCSV(String myCsv) {
        if (null==myCsv)
            return null;
        List<String> output = new ArrayList();
        String[] fields = myCsv.split(",");
        for(int i=0;i<fields.length;i++) {
            output.add(fields[i]);
        }
        return output;
    }


    public static final int KBYTE = 1024;
    public static final String[] UNITS
        = new String[] { " MB", " GB", " TB", " PB"};

    public static final Pattern ipPattern
        = Pattern.compile ("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    public static String padTo(String s, int sz) {
        return padTo (s, sz, true);
    }


    /**
     * Given a string and a field width, pads the string to fit within the
     * field. This may result in either the String being truncated, if it is
     * longer than the field or the String being padded (on the right or left,
     * depending on the left arg) with spaces.
     */
    public static String padTo(String s, int sz, boolean left) {
        int len = s.length();

        // handle the obvious
        if (len == sz) {
            return s;
        }
        // truncate those strings that are too long
        if (len > sz) {
            return s.substring (0, sz-1); // leave a blank space
        }

        StringWriter out = new StringWriter(sz);
        int padlen = sz - len;

        if (left) {
            out.write (s);
        }
        for (int i = 0; i < padlen; i++) {
            out.write (' ');
        }
        if (!left) {
            out.write (s);
        }
        return out.toString();
    }
    
    /**
     * Return a formatted storage unit value.  Converts the specified MB
     * value into the best appropriate value type
     * @param mbytes the MB value to format. 
     * @return String the formated storage value
     */
    public static String reduceMBStorageUnit(long mbytes) {
	double num = mbytes;
	 if (num < 1024) {
		return new StringBuffer(Long.toString(mbytes)).append(UNITS[0]).toString();
	 }
	 return reduceMBStorageUnit(num);
    }
    
    /**
     * Return a formatted storage unit value.  Converts the specified MB
     * value into the best appropriate value type
     * @param mbytes the MB value to format. 
     * @return String the formated storage value
     */
    public static String reduceMBStorageUnit(double mbytes) {
	double num = mbytes;
	if (num < 1024) {
	    return new StringBuffer(PrecisionFormatter.formatValue(num * 1024, 2)).toString();
	}
	
	for (int i = 1; i < UNITS.length; i++) {
	    num = num / 1024;
	    if (num < 1024) {
		return new StringBuffer(PrecisionFormatter.formatValue(num, 2))
		    .append(UNITS[i]).toString();
	    }
	}
	return new StringBuffer(PrecisionFormatter.formatValue(num * 1024, 2))
	    .append(UNITS[UNITS.length -1]).toString();
    }
    
    /**
     * Return a formatted percentage string for the specified value
     * @param value the value to format
     * @return String a percent string
     */
    public static String getPercentage(double value) {
	return new StringBuffer(PrecisionFormatter.formatValue(value, 1))
		.append("%").toString();
    }
}
