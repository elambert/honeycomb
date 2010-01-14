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
 *The Initial Developer of the Original Code is:
 *IBM.
 *
 *Portions created by: Sun Microsystems, Inc.
 *are Copyright (c) 2002 Sun Microsystems, Inc.
 *
 *All Rights Reserved.
 *
 *Contributor(s): Brian Schlosser
*/
package javax.wbem.client;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import javax.wbem.cim.CIMException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 */
public class ClientProperties {

    private static final Logger LOG = Logger.getLogger(ClientProperties.class.getName());

    /**
     * Client transport default configuration property resource bundle name.
     * This property file is in the open source client jar file, wbem.jar.
     */
    private final static String PROP_DEFAULT_FILE =
        "javax.wbem.client.WbemDefaults";

    /**
     * Client transport extended configuration property resource bundle name.
     * This property file must be on the client class path along with the
     * vendor extension client jar files pointed to by its properties.
     */
    private final static String PROP_EXTEND_FILE = "WbemClient";

    private static Properties props = null;

    /**
     * Only static methods in this class
     */
    private ClientProperties() {}

    /**
     * Method to return a configuration property.
     */
    public static synchronized String getProperty(String name)
        throws CIMException {

        if (props == null) {
            initProperties();
        }
        return props.getProperty(name);
    }

    /**
     * Method to return a list of property values with the same
     * property name prefix. Returns an empty listif no such properties.
     */
    public static List getPropertyList(String name) throws CIMException {

        ArrayList list = new ArrayList();

        initProperties();

        String prefix = name.concat(".");
        Enumeration en = props.propertyNames();
        while (en.hasMoreElements()) {
            String key = (String) en.nextElement();
            if (!key.startsWith(prefix)) {
                continue;
            }
            String val = props.getProperty(key);
            if (val != null) {
                list.add(val);
            }
        }
        return list;
    }

    /**
     * Method to load the configuration property resource bundles.
     * Loads WbemDefaults first, then WbemClient if it exists.
     * Then searches for properties with the "extension.properties."
     * prefix. Each such property defines a vendor extension
     * property file which is also read.
     */
    private static void initProperties() throws CIMException {

        if (props == null) {
            props = new Properties();
            try {
                loadProperties(PROP_DEFAULT_FILE);
            } catch (Exception ex) {
                LOG.info("Error loading WbemDefaults properties.");
                throw new CIMException(
                    CIMException.CIM_ERR_FAILED,
                    PROP_DEFAULT_FILE);
            }
            try {
                loadProperties(PROP_EXTEND_FILE);
            } catch (MissingResourceException ex) {
                LOG.info("Couldn't loading extended properties: " + PROP_EXTEND_FILE);
                // OK if the extended properties file is missing
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Client configuration properties:");
                Enumeration en = props.propertyNames();
                while (en.hasMoreElements()) {
                    String key = (String) en.nextElement();
                    String val = String.valueOf(props.getProperty(key));
                    LOG.fine("    " + key + " \t" + val);
                }
            }
        }
    }

    /**
     * Method to load the configuration properties from the specified
     * resource bundle property file.
     */
    private static void loadProperties(String bundle) throws CIMException {

        PropertyResourceBundle prb;

        prb = (PropertyResourceBundle) ResourceBundle.getBundle(bundle,
                (new Locale("", "")));
        if (prb != null) {
            Enumeration en = prb.getKeys();
            while (en.hasMoreElements()) {
                String key = (String) en.nextElement();
                if (key != null) {
                    try {
                        String val = prb.getString(key);
                        props.setProperty(key, val);
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
            }
        }
    }
}
