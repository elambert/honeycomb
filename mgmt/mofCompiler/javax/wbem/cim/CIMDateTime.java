/*
 *EXHIBIT A - Sun Industry Standards Source License
 *
 *"The contents of this file are subject to the Sun Industry
 *Standards Source License Version 1.2 (the "License");
 *You may not use this file except in compliance with the
 *License. You may obtain a copy of the 
 *License at http://wbemservices.sourceforge.net/license.html
 *
 *Software distributed under the License is distributed on
 *an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either
 *express or implied. See the License for the specific
 *language governing rights and limitations under the License.
 *
 *The Original Code is WBEM Services.
 *
 *The Initial Developer of the Original Code is:
 *Sun Microsystems, Inc.
 *
 *Portions created by: Sun Microsystems, Inc.
 *are Copyright (c) 2001 Sun Microsystems, Inc.
 *
 *All Rights Reserved.
 *
 *Contributor(s): WBEM Solutions, Inc.
*/

package javax.wbem.cim;


import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * The CIMDateTime class is modeled on the datetime data type as specified
 * in the CIM specification. It is in the format: yyyyMMddHHmmss.SSSSSSsutc
 * where
 *
 * yyyy	- is a 4 digit year
 * MM 	- is the  month
 * dd 	- is the day of the month
 * HH 	- is the hour (24 hour clock)
 * mm 	- is the minute
 * ss 	- is the second
 * mmmmmm - is the number of microseconds
 * s - is "+" or "-", indicating the sign of the UTC (Universal Coordinated
 * Time; for all intents and purposes the sane as Greenwhich Mean Time)
 * correction field, or a ":". In the case of a ":" the value is
 * interpreted as a time interval, and yyyyMM are interreted as days.
 * utc - is the offset from UTC in minutes (using the sign indicated by s).
 * It is ignored for a time interval.
 * 
 * For example Monday, May 25, 1998, at 1:30 PM EST would be represented as:
 * 
 * 	19980525133015.000000-300
 *
 * Values must be zero-padded so that the entire string is always the same
 * 25-character length.  Fields which are not significant must be replaced
 * with asterisk characters.
 *
 * Similarly, intervals use the same format, except that the interpretation
 * of the fileds is based on elapsed time.  For example, an elapsed time of
 * 1 day, 13 hours, 23 minutes, 12 seconds would be:
 *	00000001132312.000000:000 
 * A UTC offset of zero is always used for interval properties.
 *
 * @author	Sun Microsystems, Inc.
 * @version 	1.8 10/15/01
 * @since	WBEM 1.0 
 */
public class CIMDateTime implements Serializable {

    final static long serialVersionUID = 200;

    private boolean isInterval = false;
    private String dateString = null;

    /**
     * Create a CIMDateTime object using the 
     * current Time/Date of the system
     */
    public CIMDateTime() {
	this(new Date());
    }

    /**
     * Creates a CIMDateTime object using a Date
     * @param d The date
     */
    public CIMDateTime(Date d) {
	Calendar cal = Calendar.getInstance();
	cal.setTime(d);
	dateString = getCalendarString(cal);
    }

    /**
     * Creates a CIMDateTime object using a Calendar
     * @param c The calendar 
     */
    public CIMDateTime(Calendar c) {
	dateString = getCalendarString(c);
    }

    /**
     * Creates a CIMDateTime object using a string
     * 
     * @param dt A string in the format of yyyyMMddHHmmss.SSSSSSsutc
     * @throws IllegalArgumentException if string is not in the correct format
     */
    public CIMDateTime(String dt) throws IllegalArgumentException {
	// if valid becomes false, we have an invalid string
	boolean valid = true;
        //strip the quotes if sent in with them....
        if (dt.startsWith("\"") && dt.endsWith("\"")) {
            dt = dt.substring(1,dt.length()-1);
        }
	// all DateTime strings must be 25 char and 14 char must be a '.'
	if ((dt.length() != 25) || (dt.charAt(14) != '.')) {
	    valid = false;
	} else if (dt.charAt(21) == ':') {
	    isInterval = true;
	    // if interval string, make sure it has valid values
	    // validate days string
	    int i = Integer.valueOf(dt.substring(0, 8)).intValue();
	    // validate hours string
	    i = Integer.valueOf(dt.substring(8, 10)).intValue();
	    valid = valid && (i >= 0) && (i <= 24);
	    // validate min string
	    i = Integer.valueOf(dt.substring(10, 12)).intValue();
	    valid = valid && (i >= 0) && (i <= 60);
	    // validate sec string
	    i = Integer.valueOf(dt.substring(12, 14)).intValue();
	    valid = valid && (i >= 0) && (i <= 60);
	    // validate microsec string
	    i = Integer.valueOf(dt.substring(15, 21)).intValue();
	    valid = valid && (i >= 0) && (i <= 999999);
	    // UTC is always 000 on interval
	    dateString = dt.substring(0, 22);
	    dateString = dateString.concat("000");
	} else {
	    // if date string, get calendar value to validate
	    dateString = dt;
	}
	// if we have invalid string, throw exception
	if (!valid) {
	    throw new IllegalArgumentException(dt);
	}
    }

    /**
     * 
     * @return  Calendar object for the date/time.
     * @throws IllegalArgumentException if this object refers to an interval
     *         instead of a date/time
     */
    public Calendar getCalendar() throws IllegalArgumentException {
	Calendar retCal = null;
	// date string must have a '+' or a '-' as 21 char
	if ((dateString.charAt(21) != '+') && (dateString.charAt(21) != '-')) {
	    throw new IllegalArgumentException(dateString);
	}

	try {
            // milliseconds is not validated by SimpleDateFormatter
	    // parse it ourselves, which will throw an exception if 
            // it is not a valid number.
	    Integer.parseInt(dateString.substring(15, 21));
        
	    retCal = Calendar.getInstance();
	    SimpleDateFormat formatter = 
		new SimpleDateFormat("yyyyMMddHHmmss.SSS");
	    formatter.setLenient(false);
	    Date date;
	    date = formatter.parse(dateString.substring(0, 18));

	    retCal.setTime(date);
	    Integer offset = Integer.valueOf(dateString.substring(22));
	    if (dateString.charAt(21) == '-') {
		offset = new Integer((offset.intValue() * -1));
	    }
	    TimeZone tz = TimeZone.getTimeZone("GMT");
	    tz.setRawOffset(offset.intValue() * 60 * 1000);
	    retCal.setTimeZone(tz);
	} catch (Exception e) {
	    throw new IllegalArgumentException(dateString + " " + e);
	}
	return retCal;
    }

    /**
     * Tests if this is an interval
     *
     * @return boolean	true if this is an interal value; false otherwise 
     */
    public boolean isInterval() {
	return isInterval;
    }

    /**
     * Returns a String representation of the CIMDateTime.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return string representation of this datetime
     */
    public String toString() {
	return getDateTimeString();
    }

    /**
     * Returns a MOF representation of the CIMDateTime 
     *
     * @return String   Managed Object Format (MOF) representation of
     *                  this datetime 
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }

    /**
     * Compares the CIMDateTime with this one
     *
     * @param	when	The CIMDateTime to be compared with this object
     * @return 	boolean	true if the current CIMDateTime is after the 
     * 			CIMDateTime of when; false otherwise 
     *                  If comparing interval values, returns true if current
     *                  interval is greater then when, false otherwise 
     * @throws IllegalArgumentException if one object refers to an interval
     *         and the other does not.
     */
    public boolean after(CIMDateTime when) throws IllegalArgumentException {
        if (isInterval()) {
            return compareInterval(when) > 0;
        } else {
	    Calendar cal = getCalendar();
	    return cal.after(when.getCalendar());
	}
    }

    /**
     * Compares the CIMDateTime with this one
     *
     * @param	when	The CIMDateTime to be compared with this object
     * @return 	boolean	true if the current CIMDateTime is before the 
     * 			CIMDateTime of when; false otherwise 
     *                  If comparing interval values, returns true if current
     *                  interval is less then when, false otherwise 
     * @throws IllegalArgumentException if one object refers to an interval
     *         and the other does not.
     */
    public boolean before(CIMDateTime when) throws IllegalArgumentException {
	if (isInterval()) {
	    return compareInterval(when) < 0;
	} else {
	    Calendar cal = getCalendar();
	    return cal.before(when.getCalendar());
	}
    }

    private int compareInterval(CIMDateTime when) {
        if (isInterval() != when.isInterval()) { 
            throw new IllegalArgumentException(
                    "Can't compare an interval with an absolute date: " + 
                    when.toString());
        }
        return toString().compareTo(when.toString());
    }
    
    /**
     * Compares the CIMDateTime with this one
     *
     * @param	obj	The CIMDateTime to be compared with this one
     * @return 	boolean	true if the objects are the same; false otherwise 
     * @throws IllegalArgumentException if one object refers to an interval
     *         and the other does not.
     */
    public boolean equals(Object obj) throws IllegalArgumentException {
        if(obj instanceof CIMDateTime) {
            CIMDateTime date = (CIMDateTime)obj;
            if (isInterval() != date.isInterval()) { 
                throw new IllegalArgumentException(date.toString());
            }
            return dateString.equals(date.getDateTimeString());
        } else {
            return false;
        }
    }

    /**
     * Gets the internal string representation of the date/time object
     *
     * @return 	String	the internal representation of the date/time object 
     */
    protected String getDateTimeString() {
	return dateString;
    }

    /**
     * Gets the CIM specification string of a date/time object given a
     * Calendar object
     *
     * @param c       the Calendar object to convert to a string
     * @return String the CIM specification string of a date/time object 
     */
    private String getCalendarString(Calendar c) {
	StringBuffer buf = new StringBuffer();
	buf.append(padLeadingZeros(c.get(Calendar.YEAR), 4));
	// add 1 to month because calendar month is 0 based and CIMDateTime
	// month is 1 based
	buf.append(padLeadingZeros((c.get(Calendar.MONTH) + 1), 2));
	buf.append(padLeadingZeros(c.get(Calendar.DAY_OF_MONTH), 2));
	buf.append(padLeadingZeros(c.get(Calendar.HOUR_OF_DAY), 2));
	buf.append(padLeadingZeros(c.get(Calendar.MINUTE), 2));
	buf.append(padLeadingZeros(c.get(Calendar.SECOND), 2));
	buf.append(".");
	// milliseconds to microseconds needs trailing zeros
	buf.append(padTrailingZeros(padLeadingZeros(
            c.get(Calendar.MILLISECOND), 3), 6));
	int offset = c.get(Calendar.ZONE_OFFSET);
	if (offset < 0) {
	    buf.append("-");
	} else {
	    buf.append("+");
	}
	buf.append(padLeadingZeros(Math.abs(offset/60000), 3));
	return buf.toString();
    }

    /**
     * Converts a number into a string of a specific size.  Will pad the number
     * with leading zeros if the given number is not the specified size
     * 
     *
     * @param number  the number to convert to string
     * @param numSize the desired length of the resulting number string
     * @return String representing the desired number with the specified 
     *         length.
     */
    private String padLeadingZeros(int number, int numSize) {
	return padLeadingZeros(String.valueOf(number), numSize);
    }    

    /**
     * Takes a number string and adds leading zeros to make it a specifed
     * length
     * 
     *
     * @param number  the number string
     * @param numSize the desired length of the number string
     * @return String representing the desired number with the 
     *         specified length.
     */
    private String padLeadingZeros(String number, int numSize) {
	StringBuffer buf = new StringBuffer(number);
	for (int i = number.length(); i < numSize; i++) {
	    buf.insert(0, 0);
	}
	return buf.toString();
    }

    /**
     * Converts a number into a string of a specific size.  Will pad the number
     * with trailing zeros if the given number is not the specified size
     * 
     *
     * @param number  the number to convert to string
     * @param numSize the desired length of the resulting number string
     * @return String representing the desired number with the specified 
     *         length.
     
    private String padTrailingZeros(int number, int numSize) {
	return padTrailingZeros(String.valueOf(number), numSize);
    }    
    */
    
    /**
     * Takes a number string and adds trailing zeros to make it a specifed
     * length
     * 
     *
     * @param number  the number string
     * @param numSize the desired length of the number string
     * @return String representing the desired number with the 
     *         specified length.
     */
    private String padTrailingZeros(String number, int numSize) {
	StringBuffer buf = new StringBuffer(number);
	for (int i = number.length(); i < numSize; i++) {
	    buf.append(0);
	}
	return buf.toString();
    }

}
