package com.sun.dtf.recorder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashMap;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.RecorderException;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.util.SystemUtil;


public class RecorderFactory {
    private static DTFLogger _logger = DTFLogger.getLogger(RecorderFactory.class);
    private static HashMap _recorders = new HashMap();
    
    public static RecorderBase getRecorder(String type, 
                                           URI uri, 
                                           boolean append) throws DTFException { 
        Class recorderClass = (Class)_recorders.get(type);
        
        if (recorderClass == null)
            throw new RecorderException("Unsupported recorder type [" + type + "]");
        
        Class[] parameters = new Class[] {URI.class, boolean.class};
        Object[] args = new Object[] {uri, new Boolean(append)};

       
        if (!append) { 
            String path = Action.getStorageFactory().getPath(uri);
            File dir = new File(path);
            if (dir.exists()) { 
                SystemUtil.deleteDirectory(dir);
                _logger.info("Wiped [" + path + "]");
            }
        }
        
        try {
            return (RecorderBase)
                     recorderClass.getConstructor(parameters).newInstance(args);
        } catch (IllegalArgumentException e) {
            throw new RecorderException("Unable to instantiate recoder [" + type + "].",e);
        } catch (SecurityException e) {
            throw new RecorderException("Unable to instantiate recoder [" + type + "].",e);
        } catch (InstantiationException e) {
            throw new RecorderException("Unable to instantiate recoder [" + type + "].",e);
        } catch (IllegalAccessException e) {
            throw new RecorderException("Unable to instantiate recoder [" + type + "].",e);
        } catch (InvocationTargetException e) {
            throw new RecorderException("Unable to instantiate recoder [" + type + "].",e);
        } catch (NoSuchMethodException e) {
            throw new RecorderException("Unable to instantiate recoder [" + type + "].",e);
        }
    }
    
    public static void registerRecorder(String name, Class recorderClass) { 
        if (_recorders.containsKey(name)) 
            _logger.warn("Overwriting recorder implementation for [" + name + "]");
        
        if (_logger.isDebugEnabled())
            _logger.debug("Registering recorder [" + name + "]");
        
        _recorders.put(name, recorderClass);
    }
}
