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



package com.sun.honeycomb.config;


import java.util.logging.Logger;
import java.util.logging.Level;

import java.nio.channels.SocketChannel;
import java.lang.Thread;
import java.lang.ThreadGroup;
import java.security.DigestOutputStream;
import java.security.NoSuchAlgorithmException;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;
import java.util.*;
import java.io.*;

import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChangeNotif;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.jvm_agent.CMAgent;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.common.SafeMessageDigest;


/**
 * This class is the primary interface for any service in the cluster to access
 * and potentially update cluster-wide, shared configuration data.
 * ClusterProperties is a singleton class; an instance should be retrieved
 * using {@link ClusterProperties.getInstance}.
 *
 * <p>
 * ClusterProperties implements the Observer interface, and registers itself
 * with the CMM layer to be notified when changes occur to the file on disk
 *
 * <p>
 * Additonally, services can, themselves, register with ClusterProperties,
 * as PropertyChangeListeners, which will cause these services to be called
 * back when changes occur.
 * </p>
 *
 */

public class ClusterProperties implements Observer {

    private static Logger logger
        = Logger.getLogger (ClusterProperties.class.getName());

    /** The directory where all configuration data is kept */
    private static String CONF_DIR;

    /** The default, immutable cluster configuration file */
    public static String DEFAULT_CLUSTER_CONF_FILE;

    /** The name of the symlink pointing to the current cluster configuration file */
    public static String CLUSTER_CONF_FILE;

    /** Properties file used as the default. */
    private static Properties DEFAULT_PROPERTIES;

    /** The instance */
    private static ClusterProperties instance = null;
    private static File configFile;
    private static volatile long lastModified;

    /** Name of the system which defines the config directory */
    public static final String PROP_CONFIG_DIR = "honeycomb.config.dir";

    /**
     * The getter for this singleton class
     */
    public static synchronized ClusterProperties getInstance() {
        if (instance == null) {
            instance = new ClusterProperties();
        }
        return(instance);
    }

    // setup the defaults
    static {
        String confpath = System.getProperty (PROP_CONFIG_DIR);
        if (confpath == null) {
            confpath = "/config";
        }

        File confdir = new File (confpath);

        CONF_DIR = confdir.getAbsolutePath();
        DEFAULT_CLUSTER_CONF_FILE = CONF_DIR + "/config_defaults.properties";
        CLUSTER_CONF_FILE         = CONF_DIR + "/config.properties";
        DEFAULT_PROPERTIES = new Properties();
        File defaults_file = new File (DEFAULT_CLUSTER_CONF_FILE);
        if (logger.isLoggable (Level.FINE)) {
            logger.fine ("CONF_DIR=" + CONF_DIR);
        }

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(defaults_file);
            DEFAULT_PROPERTIES.load (fis);
        } catch (IOException ioe) {
            if (logger.isLoggable (Level. WARNING)) {
                logger.log (Level.WARNING,
                           "unable to load: " + DEFAULT_CLUSTER_CONF_FILE, ioe);
            }
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    protected ArrayList  listeners;
    protected Properties properties;
    protected boolean    hasRegisteredCmm;


    /**
     * getProperty differs from the Properties.getProperty method only in the
     * fact that is trims leading and trailing whitespace from the value
     * returned.
     */
    public String getProperty(String name) {
        checkAndReload();
        String value = properties.getProperty(name);
        if (value != null) {
            return value.trim();
        }
        return null;
    }

    public String getProperty(String key, String defaultValue) {
        checkAndReload();
        return properties.getProperty(key, defaultValue);
    }

    public void list(PrintWriter out) {
        properties.list(out);
    }

    public Enumeration propertyNames() {
        checkAndReload();
        return properties.propertyNames();
    }

    public void put(String name, String value) throws ServerConfigException {
        HashMap map = new HashMap();
        map.put(name, value);
        putAll(map);
    }
    
    public synchronized void putAll(Map newProperties)
        throws ServerConfigException 
    {
        checkAndReload();
        Properties pending = (Properties) properties.clone();
        pending.putAll(newProperties);
        store(newProperties);
        properties = pending;
    }

    /*
     *
     * Observer/Listener methods
     *
     */

    /**
     * Register a listener for property change events. Registering listeners
     * with this method results in your listener being called back when the
     * specificed property changes.
     */
    public void addPropertyListener(PropertyChangeListener l) {
        
        synchronized(listeners) {
            if (!hasRegisteredCmm) {
                try {
                    ServiceManager.register(ServiceManager.CONFIG_EVENT,
                      instance);
                    hasRegisteredCmm = true;
                } catch(Exception e) {
                    logger.severe("Unable to register for CMM notifications");
                    return;
                }
            }
            if (!listeners.contains(l)) {
                listeners.add(l);
            }
        }
    }

    /**
     * Unregister a listener
     */
    public void removePropertyListener(PropertyChangeListener l) {
        synchronized(listeners) {
            if (listeners.contains(l)) {
                listeners.remove(l);
            }
        }
    }

    /**
     * This method is called by CMM when properties change
     */
    public synchronized void update(Observable o, Object arg) {

        if (arg instanceof ConfigChangeNotif) {
            ConfigChangeNotif notif = (ConfigChangeNotif) arg;
            if (notif.getCause() != ConfigChangeNotif.CONFIG_UPDATED) {
                // not the final config update, ignore
                return;
            }
        } else {
            // invalid case, we should only be called with ConfigChangeNotif
            logger.warning("Update arg not of ConfigChangeNotif type");
        }
        reload();
    }

    /*
     *
     * Existence
     *
     */

    /**
     * Checks the properties list for the given name, the checks the default
     * properties list for the same, returning true if the property is
     * defined in either
     */
    public boolean isDefined(String name) {
        checkAndReload();

        boolean retVal = false;
        if (properties.containsKey (name)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("property " + name + " found in current config");
            }
            retVal = true;

        } else if (DEFAULT_PROPERTIES.containsKey(name)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("property " + name + " found in default config");
            }
            retVal = true;
        }
        return retVal;
    }

    /**
     * Checks the properties list for the given name. If the property is
     * only defined in the default property list, this method returns
     * true.
     */
    public boolean isDefaulted(String name) {
        checkAndReload();

        boolean retVal = false;
        if ((!properties.contains(name)) && DEFAULT_PROPERTIES.contains(name)) {
            retVal = true;
        }
        return retVal;
    }

    /**
     * Returns the given property name, if found, as an array of Strings. If
     * the property is not found, null will be returned. Lists are comma
     * seperated. Whitespace will be trimmed from the beginning and end of
     * each value
     */
    public String[] getPropertyAsList(String name) {
        String value = getProperty(name);
        if (value == null) return null;
        return value.split("\\s*,\\s*");
    }

    /*
     *
     * Type Coersion
     *
     */

    /**
     * Returns the given property name, if found, as an int base type. If
     * the property name is not found Integer.MIN_VALUE is returned
     */
    public int getPropertyAsInt(String name) throws NumberFormatException {
        return getPropertyAsInt(name, Integer.MIN_VALUE);
    }

    /**
     * As {@link getPropertyAsInt} except if the property is not found, the
     * specified defaultValue will be returned
     */
    public int getPropertyAsInt(String name, int defaultValue)
        throws NumberFormatException 
    {
        int num = defaultValue;
        String prop = getProperty(name);
        if (prop == null)
            return num;
        else return Integer.parseInt(prop);
    }

    /**
     * Returns the given property name, if found, as an array of ints. If
     * the property is not found, null will be returned. Lists are comma
     * seperated.
     */
    public int[] getPropertyAsIntList(String name)
        throws NumberFormatException 
    {
        String[] values = getPropertyAsList(name);
        if (values == null) {
            return null;
        }
        int[] int_values = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            int_values[i] = Integer.parseInt(values[i]);
        }
        return int_values;
    }

    /**
     * Returns the given property name, if found, as a long base type. If
     * the property name is not found Long.MIN_VALUE is returned
     */
    public long getPropertyAsLong(String name) throws NumberFormatException {
        return getPropertyAsLong(name, Long.MIN_VALUE);
    }

    /**
     * As {@link getPropertyAsLong} except if the property is not found, the
     * specified defaultValue will be returned
     */
    public long getPropertyAsLong(String name, long defaultValue)
        throws NumberFormatException 
    {
        long num = defaultValue;
        String prop = getProperty(name);
        if (prop == null)
            return num;
        else return Long.parseLong(prop);
    }

    /**
     * Returns the given property name, if found, as an array of longs. If
     * the property is not found, null will be returned. Lists are comma
     * seperated.
     */
    public long[] getPropertyAsLongList(String name)
        throws NumberFormatException 
    {
        String[] values = getPropertyAsList(name);
        if (values == null) {
            return null;
        }
        long[] long_values = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            long_values[i] = Long.parseLong(values[i]);
        }
        return long_values;
    }

    /**
     * Returns the given properties value as a boolean. If the value is the
     * string "true" (ignoring case) then Boolean.TRUE will be returned, else
     * Boolean.FALSE will be returned
     */
    public boolean getPropertyAsBoolean(String name) {
        return getPropertyAsBoolean(name, false);
    }

    /**
     * As {@link getPropertyAsBoolean} except if the property specificed by
     * the name parameter is not defined, the defaultValue parameter is
     * returned
     */
    public boolean getPropertyAsBoolean(String name, boolean defaultValue) {
        
        boolean bool = defaultValue;
        String value = getProperty(name);        
        if (value == null) {
            bool = defaultValue;
        } else {
            bool = Boolean.valueOf(value).booleanValue();
        }
        return bool;
    }

    /**
     * Returns the given properties value as a double. If the property
     * is not found Double.MIN_VALUE is returned.
     */
    public double getPropertyAsDouble(String name) {
        return getPropertyAsDouble(name, Double.MIN_VALUE);
    }

    /**
     * As {@link getPropertyAsDouble} except if the property is not
     * found, the specified defaultValue will be returned
     */
    public double getPropertyAsDouble(String name, double defaultValue)
        throws NumberFormatException 
    {
        double d = defaultValue;
        String prop = getProperty(name);
        if (prop == null)
            return d;
        else return Double.parseDouble(prop);
    }

    
    /*
     *
     * Protected & private methods
     *
     */

    /**
     * Protected constructor called when the singleton class is initialized.
     */
    protected ClusterProperties() {
        hasRegisteredCmm  = false;
        properties=new Properties(DEFAULT_PROPERTIES);
        listeners = new ArrayList();
        configFile = new File(CLUSTER_CONF_FILE);

        // load the current config
        load(properties);
        lastModified = configFile.lastModified();
    }

    /**
     * Check and reload the properties
     */
    private void checkAndReload() {
        if (configFile.lastModified() != lastModified) {
            reload();
        }
    }
    
    /**
     * atomically check and reload the cluster config properties.
     * called each time the config properties potentially changed
     * due to a config update.
     */
    private synchronized void reload() {
        
        long curModified = configFile.lastModified();
        if (curModified == lastModified) {
            return;
        }
        lastModified = curModified;
        
        // save the old properties into an another properties instance (old)
        // for the comparison later to trigger the appropriate notification
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        try {
            properties.store(bos, null);
        } catch(IOException ioe) {
            logger.warning("Unable to store old config");
        }
        
        // load those stored properties into another properties instance
        Properties old = new Properties();
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(os.toByteArray());
            old.load(new BufferedInputStream(in));
        } catch(IOException ioe) {
            logger.warning("Unable to reload old config");
        }
        
        // load the updated new properties into memory
        load(properties);
        
        // walk the property keys and generate an event for each change.
        for (Enumeration e = properties.keys(); e.hasMoreElements(); ) {
            String key = (String) e.nextElement();
            String old_value = old.getProperty(key);
            String new_value = this.getProperty(key);
            boolean doAlert = false;
            
            if (new_value == null) {
                if (old_value != null) {
                    doAlert = true;
                }
            } else if (!new_value.equals(old_value)) {
                doAlert = true;
                
            }
            if (doAlert) {
                logger.info("Property change: prop="+key+
                            " old="+old_value+" new="+new_value
                            );
                PropertyChangeEvent event
                    = new PropertyChangeEvent(this, key, old_value, new_value);
                synchronized(listeners) {
                    PropertyChangeListener l;
                    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
                        l = (PropertyChangeListener) i.next();
                        l.propertyChange(event);
                    }
                }
            }
        }            
    }
    
    /**
     * Load the most current properties file, including the header containing
     * embedded checksum and version information
     * FIXME - this method should be private.
     * Used by CMM to create a new Config file.
     */
    public static synchronized void load(Properties curProps) {

        File config_symlink = new File(CLUSTER_CONF_FILE);

        if (config_symlink.exists()) {

            // on unix, this will resolve the symlimk to the real file name
            File config_real = null;
            try {
                config_real = config_symlink.getCanonicalFile();

            } catch(IOException ioe) {
                throw new ConfigException("unable to resolve symlink: "
                    + config_symlink.getPath());
            }

            try {
                //
                // don't checksum the file; that's done at copy time
                // from lobbytask
                //
                ByteArrayInputStream bis
                    = new ByteArrayInputStream(parseConfigFile(config_real,false));

                curProps.load(bis);
            } catch(IOException ioe) {
                throw new ConfigException(ioe.getMessage());
            } catch(ConfigChecksumException cce) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE,
                    "checksum error reading " + config_real,
                    cce);
                }
            }
        }
    }

    /**
     * Preparses the specified config file, removing any non-"standard"
     * bits added by us for file integreity or other purposes, and then
     * returns a byte[] arrary suitable for loading into a a Properties object
     * via Properties.load()
     * <p>
     * This method will throw ...
     * <p>
     * WARNING - This is messy and could be less wasteful
     * Note use all the time.
     */
    public static byte[] parseConfigFile(File config, boolean validate)
        throws IOException {

        // Read the file into as StringBuffer
        BufferedReader in = null;
        StringBuffer raw_props = new StringBuffer();
        char[] buf = new char[4096];
        int len;
        try {
            in = new BufferedReader(new FileReader(config));
            while ((len = in.read(buf)) != -1) {
                raw_props.append(buf, 0, len);
            }
        } finally {
            try {
                in.close();
            } catch(Exception e) {
                // ignore
            }
        }

        // The first line, a comment, was added by us during store and
        // contains the embedded MD5 and version number.

        int eol = raw_props.indexOf("\n#");
        String validation_line = raw_props.substring(0, eol);
        raw_props.delete(0, eol+1); // delete the validaiton info from sb

	int b1 = validation_line.indexOf('[');
	int b2 = validation_line.indexOf(']');
	
	String validation_string = validation_line.substring(b1+1,b2-1).trim();
        String[] parts = validation_string.split(" ");
        String embedded_md5 = parts[0];

        byte[] md5Bytes= SafeMessageDigest.digest(raw_props.toString().getBytes(),"MD5");
        String file_md5 = new BASE64Encoder().encode(md5Bytes);

//         Disable validation -- See bug #6529266
//         if (validate) {
//             if (! embedded_md5.equals(file_md5)) {
//                 throw new ConfigChecksumException(embedded_md5 + " != " + file_md5);
//             }
//         }
        return raw_props.toString().getBytes();
    }

    /**
     * Save the current properties to disk, creating a new config version.
     * This method checksums the data, then embeds the checksum and version
     * into the data as a properties comment.
     *
     * Blocks until operation complete
     */
    private void store(Map newProps) throws ServerConfigException {

        try {
            CMM.getAPI().updateConfig(CMMApi.UPDATE_DEFAULT_FILE, newProps);
        } catch(CMMException cmme) {
            throw new ConfigException(cmme);
        }
    }
    
    
    /*
     *
     * Obsoleted methods
     *
     */
    
    /**
        * This method does nothing in this implementation.
     */
    public void load(InputStream in) {
        return;
    }
    
    /**
        * This method does nothing in this implementation.
     */
    public void loadFromXML(InputStream in) {
        return;
    }
    
    /**
        * This method does nothing in this implementation.
     */
    public void store(OutputStream out, String comments) {
        return;
    }
    
    /**
        * This method does nothing in this implementation.
     * @deprecated
     */
    public void save(OutputStream out, String comments) {
        return;
    }
    
    /**
        * This method does nothing in this implementation.
     */
    public void storeToXML(OutputStream out, String comments) {
        return;
    }
    
    /**
        * This method does nothing in this implementation.
     */
    public void storeToXML(OutputStream out, String comments, String enc) {
        return;
    }
}
