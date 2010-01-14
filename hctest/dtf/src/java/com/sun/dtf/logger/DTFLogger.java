package com.sun.dtf.logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.actions.Action;
import com.sun.dtf.config.Config;

public class DTFLogger {
    private static Config _config = null;
    
    private Logger _logger = null;
    protected DTFLogger(Logger logger) { _logger = logger; } 
    
    public static void setConfig(Config config) { _config = config; } 
   
    private static HashMap _loggers = new HashMap();
    public synchronized static DTFLogger getLogger(String name) {
        if (!_loggers.containsKey(name)) { 
            Logger logger = Logger.getLogger(name);
            
            if (_config != null ) { 
                PropertyConfigurator.configure(_config.getProperties());
            } else  { 
                /*
                 * Creating default logger.
                 */
                Properties properties = new Properties();
                try {
                    String propfile = System.getProperty(DTFProperties.DTF_PROPERTIES);
                   
                    if (propfile == null)
                        propfile = "dtf.properties";
                    
                    properties.load(new FileInputStream(propfile));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException("Unable to find properties file.",e);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to find properties file.",e);
                }
                PropertyConfigurator.configure(properties);
            }
            
            _loggers.put(name,new DTFLogger(logger));
        }
        return (DTFLogger)_loggers.get(name);
    }
   
    public void info(Object message) { _logger.info(message); }
    public void info(Object message, Throwable t) { _logger.info(message,t); }
    
    public void error(Object message) { _logger.error(message); }
    public void error(Object message, Throwable t) { _logger.error(message,t); }
    
    public void warn(Object message) { _logger.warn(message); }
    public void warn(Object message, Throwable t) { _logger.warn(message,t); }
    
    public void debug(Object message) { _logger.debug(message); }
    public void debug(Object message, Throwable t) { _logger.debug(message,t); }
    
    public boolean isDebugEnabled() { return _logger.isDebugEnabled(); }
    
    public void addAppender(Appender appender) { _logger.addAppender(appender); } 
    public void removeAppender(Appender appender) { _logger.removeAppender(appender); } 
    
    public static DTFLogger getLogger(Class aClass) {
        return getLogger(Action.getClassName(aClass));
    }
}
