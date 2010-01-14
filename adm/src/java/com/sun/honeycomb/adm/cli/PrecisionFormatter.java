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


package com.sun.honeycomb.adm.cli;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility methods for formatting numerical values for a given 
 * precision specification.
 */
public class PrecisionFormatter
{
    
    private PrecisionFormatter()
    {
	
    }
    
    /**
     * The default decimal precision.
     */
    public static final int DEFAULT_PRECISION = 3;
    
    /**
     * The default precision floor value
     * @see getPrecisionFloor().
     */
    private static double DEFAULT_PRECISION_FLOOR = 
                                getPrecisionFloor(DEFAULT_PRECISION);
    
    
    /**
     * Get the default locale (English since the cli doesn't localize) 
     * @return locale
     */
    protected static Locale getLocale() {
        return Locale.ENGLISH;
    }

    
    /**
     * Get the precision floor for a given precision specification.
     * The returned number represents the smallest positive value for
     * which the standard formatting rules will apply; a positive number
     * smaller than this value will result in specialized formatting
     * of the number to ensure at least one significant (ie, non-zero)
     * digit is rendered as part of the formatted result.
     * <p/>
     * For example, if the specified precision is 3, and the number
     * to be formatted is 0.000005394, then the formatted number would
     * be 0.000006.  In this case, the returned precision floor value
     * would be 0.001
     * @param precision The integer number of decimal places.
     * @return The precision floor value.
     */
    public static double getPrecisionFloor(int precision) {
        double precisionFloor = 0.0;
        
        if ((precision == DEFAULT_PRECISION) &&
            (DEFAULT_PRECISION_FLOOR > 0.0)) {
                return DEFAULT_PRECISION_FLOOR;
        }
        
        for (int i = precision; i > 0; i--) {
            if (precisionFloor == 0.0) {
                precisionFloor = 0.1;
            } else {
                precisionFloor /= 10.0;
            }
        }
        
        return precisionFloor;
    }

    /**
     * Get a displayable rendition of a value properly
     * formatted to conform to presentation rules,
     * using the specified locale.
     * @param value The <code>long</code> value to formate.
     * @param locale The locale to use for formatting; if null, the current
     * locale is used.
     * @return A formatted value suitable for presentation
     */
    public static String formatValue(long value, Locale locale) {
        if (locale == null) {
            locale = getLocale();
        }
        return NumberFormat.getInstance(locale).format(value);
    }

    /**
     * Get a displayable rendition of a value properly
     * formatted to conform to presentation rules,
     * using the current locale.
     * @param capacity The <code>long</code> storage capacity value.
     * @return A formatted value suitable for presentation
     */
    public static String formatValue(long value) {
        return formatValue(value, null);
    }

    /**
     * Get the displayable rendition of a value properly
     * formatted to conform to presentation rules,
     * using the specified locale and decimal precision.
     * <p/>
     * This method performs special handling of very small numbers such that the
     * formatted value will always return a string that does not include only
     * "0" digits; the value will always indicate the most significant non-zero
     * digit for values less than 1.0 for the defined precision.  For
     * example, if the defined precision is 3, then the value 0.00005836 will be
     * formatted as "0.00006".
     *
     * @param value The <code>double</code> value to format;
     * this value should never (by definition) be a negative value, but if a
     * negative value is specified, it will be formatted using the normal rules.
     * @param precision The number of decimal places of the formatted number.
     * @param locale The locale to use for formatting; if null, the current
     * locale is used.
     *
     * @return A formatted value suitable for presentation within the portlet.
     */
    public static String formatValue(double value, int precision,
                                               Locale locale) {
        
        String rv = null;   // Constructed return value
        
        if (locale == null) {
            locale = getLocale();
        }
        
        NumberFormat nf = NumberFormat.getInstance(locale);
        
        // For 0.0 <= value >= the precision_ceiling, then apply the simple
        // formatting rules governed by the defined standard precision
        // and the specified locale.
        //
        // Otherwise, for values greater than 0.0 and less than 1.0, format the
        // value so that the most significant digit in the fractional part is
        // visible in the formatted string, regardless of the defined precision,
        // but using the specified locale.
        //
        if ((value > 0.0) && (value < getPrecisionFloor(precision))) {
            double characteristic = 0.0;
            precision = 0;
            
            // Find the first non-zero fractional digit
            while (characteristic == 0.0) {
                value *= 10.0;
                precision++;
                characteristic = Math.floor(value);
            }
            
            // Add the rounding factor
            value += 0.5;
            
            // Get only the integer portion
            value = Math.floor(value);

            // If the rounding has caused the value to exceed 10,
            // then adjust it downward; otherwise, an extra zero will
            // show up after the 1.
            if (value == 10.0) {
                value /= 10.0;
                precision--;
            }
            
            // Scale the value back down
            for (int i = precision; i > 0; i--) {
                value /= 10.0;
            }
        }
        
        nf.setMinimumFractionDigits(precision);
        nf.setMaximumFractionDigits(precision);
        rv = nf.format(value);

        return rv;
    }
    
    /**
     * Get the displayable rendition of a value properly
     * formatted to conform to presentation rules,
     * using the specified locale.
     * <p/>
     * This method performs special handling of very small numbers such that the
     * formatted value will always return a string that does not include only
     * "0" digits; the value will always indicate the most significant non-zero
     * digit for values less than 1.0 for the defined standard precision.  For
     * example, if the defined precision is 3, then the value 0.00005836 will be
     * formatted as "0.00006".
     *
     * @param value The <code>double</code> value to format;
     * this value should never (by definition) be a negative value, but if a
     * negative value is specified, it will be formatted using the normal rules.
     * @param locale The locale to use for formatting; if null, the current
     * locale is used.
     *
     * @return A formatted value suitable for presentation within the portlet.
     */
    public static String formatValue(double value, Locale locale) {
        return formatValue(value, DEFAULT_PRECISION, locale);
    }
        
    /**
     * Get the displayable rendition of a value properly
     * formatted to conform to presentation rules,
     * using the specified precision and default locale.
     * <p/>
     * This method performs special handling of very small numbers such that the
     * formatted value will always return a string that does not include only
     * "0" digits; the value will always indicate the most significant non-zero
     * digit for values less than 1.0 for the defined standard precision.  For
     * example, if the defined precision is 3, then the value 0.00005836 will be
     * formatted as "0.00006".
     *
     * @param value The <code>double</code> value to format;
     * this value should never (by definition) be a negative value, but if a
     * negative value is specified, it will be formatted using the normal rules.
     * @param precision The number of decimal places of the formatted number.
     *
     * @return A formatted value suitable for presentation within the portlet.
     */
    public static String formatValue(double value, int precision) {
        return formatValue(value, precision, null);
    }

    /**
     * Get the displayable rendition of a value properly
     * formatted to conform to presentation rules,
     * using the current locale.
     *
     * @see formatValue(double capacity, Locale locale)
     *
     * @param value The value as a <code>double</code> value;
     * this value should never (by definition) be a negative value, but if a
     * negative value is specified, it will be formatted using the normal rules.
     *
     * @return A formatted value suitable for presentation within the portlet.
     */
    public static String formatValue(double value) {
        return formatValue(value, DEFAULT_PRECISION, null);
    }
}

