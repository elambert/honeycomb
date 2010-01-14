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



package com.sun.honeycomb.mof;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Vector;

/**
 *
 * @author      Sun Microsystems, Inc.
 * @version     1.9, 05/18/01
 * @since       WBEM 1.0
 */
public class I18N {
   
    private static String resourceBundleName = "";

    /**
     * The default Locale 
     * The locale is read at initialization time.
     */
    public static Locale locale = Locale.getDefault();


    /**
     *
     */
    public static void setResourceName(String fileName) {
        resourceBundleName = fileName;
    }

    /**
     *
     */
    public static String getResourceName() {
        return resourceBundleName;
    }


    /**
     *
     */
    public static String loadString(String id) {
        return loadString(id, resourceBundleName);
    }

    /**
     * Checks if a string is in the resource bundle
     *
     * @param id    The string ID of the string you want to retrieve
     */
    public static boolean isStringAvailable(String id) {
        return isStringAvailable(id, resourceBundleName);
    }

    
    /**
     * Checks if a string is in the resource bundle
     *
     * @param id    The string ID of the string you want to retrieve
     * @param b	    A String that represents the name of the resource bundle.
     */
    public static boolean isStringAvailable(String id, String b) {
        ResourceBundle bundle = null;
    	try {
            bundle = ResourceBundle.getBundle(b, locale);
        } catch(MissingResourceException  e) {
            try {
                bundle = ResourceBundle.getBundle(b, Locale.ENGLISH);
            } catch(MissingResourceException ee) {
                return false;
            }
        }

        if (bundle == null) {
            return false;
        } else {
            try {
                bundle.getString(id);
            } catch (MissingResourceException e) {
                return false;
            }
            return true;
        }
    }


    /**
     * load a string from the resource bundle
     *
     * @param id    The string ID of the string you want to retrieve
     * @param b	    A String that represents the name of the resource bundle.
     */
    public static String loadString(String id, String b) {
        ResourceBundle bundle = null;
    	try {
            bundle = ResourceBundle.getBundle(b, locale);
        } catch(MissingResourceException  e) {
            try {
                bundle = ResourceBundle.getBundle(b, Locale.ENGLISH);
            } catch(MissingResourceException ee) {
                System.err.println("CRITICAL ERROR: Could not load resource bundle " + b);
                System.exit(-1);
            }
        }

        if (bundle == null) {
            //Since this is an error msg about not being able to locate
            //the resource bundle - Do not localize these strings
            System.err.println("CRITICAL ERROR: Could not load resource bundle " + b);
            System.exit(-1);
            return null;
        } else {
            try {
                return bundle.getString(id);
            } catch (MissingResourceException e) {
                //Since this is an error msg about not being able to locate
                //the resource bundle - Do not localize these strings
                System.err.println("CRITICAL ERROR: Could not load ID " +
                                   id + " resource bundle " + b);
                System.exit(-1);
            }
            return null;
        }		
    }


    /**
     * load a string from the resource bundle and applies a message format
     * using a vector of Objects
     *
     * @param id	The string ID of the string you want to retrieve
     * @param values	Vector containing Objects that will be inserted in
     *			message
     * @param b		A String that represents the name of the resource 
     *			bundle.
     */
    public static String loadStringFormat(String id, Vector values, String b) {
        String msgString = loadString(id, b);
        Object[] arguments;
        int numItems = values.size();
        if (msgString != null) {
            arguments = new Object[numItems];
            for (int i = 0; i < numItems; i++) {
                arguments[i] = values.elementAt(i);
            }
            return MessageFormat.format(msgString, arguments);
        } else {
            return null;
        }
    }

    /**
     * load a string from the resource bundle and applies a message format
     * using a vector of Objects
     *
     * @param id	The string ID of the string you want to retrieve
     * @param values	Vector containing Objects that will be inserted in
     *			message
     */
    public static String loadStringFormat(String id, Vector values) {
        return loadStringFormat(id, values, resourceBundleName);
    }

    /**
     * load a string from the resource bundle and applies a message format
     * using a array of Objects
     *
     * @param id	The string ID of the string you want to retrieve
     * @param values	Array containing Objects that will be inserted in
     *			message
     * @param b		A String that represents the name of the resource 
     *			bundle.
     */
    public static String loadStringFormat(String id, Object[] values,
                                          String b) {
        String msgString = loadString(id, b);
        if (msgString != null) {
            return MessageFormat.format(msgString, values);
        } else {
            return null;
        }
    }

    /**
     * load a string from the resource bundle and applies a message format
     * using a array of Objects
     *
     * @param id	The string ID of the string you want to retrieve
     * @param values	Array containing Objects that will be inserted in
     *			message
     */
    public static String loadStringFormat(String id, Object[] values) {
        return loadStringFormat(id, values, resourceBundleName);
    }

    /**
     * load a string from the resource bundle and applies a message format
     * using a single Object
     *
     * @param id	The string ID of the string you want to retrieve
     * @param arg1	Object to be inserted in message
     */
    public static String loadStringFormat(String id, Object arg1) {
        Vector v = new Vector();
        v.addElement(arg1);
        return loadStringFormat(id, v);
    }
	    
    /**
     * load a string from the resource bundle and applies a message format
     * using 2 Objects
     *
     * @param id	The string ID of the string you want to retrieve
     * @param arg1	First Object to be inserted in message
     * @param arg2	Second Object to be inserted in message
     */
    public static String loadStringFormat(String id, Object arg1, 
                                          Object arg2) {
        Vector v = new Vector();
        v.addElement(arg1);
        v.addElement(arg2);
        return loadStringFormat(id, v);
    }
	    
}
