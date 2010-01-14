package com.sun.dtf.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.sun.dtf.exception.DTFException;


public class TimeUtil {

    private static final String TIME_REGEXP = "[0-9]*(s|m|h|d|ms)";

    private static Calendar _calendar = Calendar.getInstance();
    private static SimpleDateFormat _sdf = new SimpleDateFormat("yyyy-MM-dd-HH.mm.ss");

    /**
     * 
     * @param time
     * @return time in milliseconds for the timer interval expressed by the time
     *         argument.
     * @throws DTFException
     */
    public static long parseTime(String property, String time) throws com.sun.dtf.exception.ParseException {

        if (!time.matches(TIME_REGEXP)) {
            // at this point it can only contain a number and that is in ms
            try {
                return new Long(time).longValue();
            } catch (NumberFormatException e) {
                throw new com.sun.dtf.exception.ParseException("Unable to parse property [" + property + 
                                       "] as a long",e);
            }
        } else {
            if (time.endsWith("ms")) 
                return new Long(time.substring(0, time.length() - 2)).longValue();

            long longValue = new Long(time.substring(0, time.length() - 1)).longValue();
            
            if (time.endsWith("s")) 
                return longValue * 1000;
            
            if (time.endsWith("m")) 
                return longValue * 60000;
            
            if (time.endsWith("h")) 
                return longValue * 3600000;
            
            if (time.endsWith("d"))
                return longValue * 86400000;
            
            throw new com.sun.dtf.exception.ParseException("Property: " + 
                                 property + " does not respect the expression: "
                                 + TIME_REGEXP);
        }
    }

    /**
     * 
     * @return
     */
    public static String getDateStamp() {
        return _sdf.format(_calendar.getTime());
    }

    /**
     * 
     * @return
     */
    public static String getTimeStamp() {
        return "" + System.currentTimeMillis();
    }

    /**
     * 
     * @return
     */
    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }
    
    /**
     * 
     * @param datestamp
     * @return
     * @throws DTFException
     */
    public static Date dateStampToDate(String datestamp) throws ParseException {
        return _sdf.parse(datestamp);
    }

    /**
     * 
     * @param timestamp
     * @return
     * @throws DTFException
     */
    public static String dateStampToDateStamp(long timestamp)
            throws ParseException {
        Date date = new Date(timestamp);
        return _sdf.format(date);
    }
}