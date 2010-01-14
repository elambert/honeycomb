package com.sun.dtf.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.PropertyException;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class Config {
    // Static Properties
    private Properties _properties = null;
    private HashMap _dynamicProperties = null;
  
    public Config() throws DTFException { 
        this(true);
    }
    
    private Config(boolean init) throws DTFException {
        _properties = new Properties();
        _dynamicProperties = new HashMap();
        
        /*
         * Dynamic properties that are used internally in the DTF framework.
         */ 
        put(DTFTimeStamp.DTF_TIMESTAMP, new DTFTimeStamp());
        put(DTFDateStamp.DTF_DATESTAMP, new DTFDateStamp());
        put(DTFRandomInt.DTF_RANDOMINT, new DTFRandomInt());
        put(DTFRandomLong.DTF_RANDOMLONG, new DTFRandomLong());
        put(DTFRandomDouble.DTF_RANDOMDOUBLE, new DTFRandomDouble());
        
        if (init) 
            init();
    }
    
    private void init() throws DTFException {
        File config = new File(System.getProperty("dtf.properties.filename", 
                                                  "dtf.properties"));
        if (config.exists()) {
            FileInputStream fis;
            try {
                fis = new FileInputStream(config);
                loadProperties(fis,false);
            } catch (FileNotFoundException e) {
                throw new DTFException("Bad config.",e);
            }
        }
        
        // add default properties
        if (System.getProperty(DTFProperties.DTF_XML_FILENAME) != null) { 
	        String pathToXML = new File(System.getProperty(DTFProperties.DTF_XML_FILENAME)).getParent();
	        if (pathToXML == null)
	            pathToXML = "";
	        _properties.put(DTFProperties.DTF_XML_PATH, pathToXML);
        }
    }
    
    public void put(String key, DynamicProperty property) { 
        _dynamicProperties.put(key, property);
    }
    
    public String getProperty(String key) {
        String result = (String)_properties.get(key);
        if (result == null) result = System.getProperty(key);
        return result;
    }

    public String getProperty(String key, String defaultValue) {
        String result = getProperty(key);
        if (result == null) 
            return defaultValue;
        else 
            return result;
    }

    public int getPropertyAsInt(String key, int defaultValue) throws PropertyException {
        String result = getProperty(key);
       
        try { 
            if (result == null) 
                return defaultValue;
            else
                return new Integer(result).intValue(); 
        } catch (NumberFormatException e) { 
            throw new PropertyException("Invalid int value for property '" + 
                                        key + "'",e);
        }
    }
   
    public int getPropertyAsInt(String key) throws PropertyException {
        String result = getProperty(key);
       
        try { 
            if (result == null) 
                return -1;
            else
                return new Integer(result).intValue(); 
        } catch (NumberFormatException e) { 
            throw new PropertyException("Invalid int value for property '" + 
                                        key + "'",e);
        }
    }
    
    public String getInternalProperty(String key) throws ParseException {
        String result = _properties.getProperty(key);
       
        if (result == null) {
            // Dynamic Properties
            DynamicProperty prop = (DynamicProperty)_dynamicProperties.get(key);
            if (prop != null)
                return prop.getValue();
        }
        
        return result;
    } 
    
    public synchronized boolean contains(Object value) {
        return _properties.contains(value) || System.getProperties().contains(value)
               || _dynamicProperties.containsValue(value);
    }
    
    public synchronized boolean containsKey(Object key) {
        return _properties.containsKey(key) || System.getProperties().containsKey(key)
               || _dynamicProperties.containsKey(key);
    }
   
    public void loadProperties(InputStream is, boolean overwrite) throws DTFException {
        try {
            if (overwrite) { 
                _properties.load(is);
            } else {
                Properties properties = new Properties();
                properties.load(is);
                
                Enumeration keys = properties.keys();
                
                while (keys.hasMoreElements()) { 
                    Object key = keys.nextElement();
                    if (!containsKey(key)) 
                        _properties.put(key, properties.get(key));
                }
            }
        } catch (IOException e) {
            throw new DTFException("Unable to load properties from specified stream.",e);
        }
    }
    
    public Properties getProperties() { return _properties; }
    
    public Object setProperty(String key, String value) {
        return _properties.setProperty(key, value);
    }

    public Object setProperty(String key, String value, boolean overwrite) {
        if (overwrite)
           return _properties.setProperty(key, value);
        else if (!containsKey(key)) {
           return _properties.setProperty(key, value);
        }
        
        return _properties.get(key);
    }
    
    public synchronized void putAll(Map t) {
        _properties.putAll(t);
    }
   
    public Object clone() {
        Config config;
        try {
            config = new Config(false);
        } catch (DTFException e) {
            throw new RuntimeException("Unable to clone.",e);
        }
        config._properties = (Properties)this._properties.clone();
        config._dynamicProperties = (HashMap)this._dynamicProperties.clone();
        return config;
    }
    
    public synchronized Object remove(Object key) {
        Object obj1 = _properties.remove(key);
        Object obj2 = System.getProperties().remove(key);
        return (obj1 != null ? obj1 : obj2);
    }
}
