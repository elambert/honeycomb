package com.sun.dtf.query;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.QueryException;
import com.sun.dtf.exception.RecorderException;
import com.sun.dtf.logger.DTFLogger;


public class QueryFactory {
    private static DTFLogger _logger = DTFLogger.getLogger(QueryFactory.class);
    private static HashMap _queries =  new HashMap();
    
    public static QueryIntf getQuery(String type) throws DTFException { 
        Class queryClass = (Class)_queries.get(type);
        
        if (queryClass == null)
            throw new RecorderException("Unsupported query type: '" + type + "'");
        
        Class[] parameters = new Class[] {};
        Object[] args = new Object[] {};
        
        try {
            return (QueryIntf) queryClass.getConstructor(parameters).newInstance(args);
        } catch (IllegalArgumentException e) {
            throw new QueryException("Unable to instantiate query [" + type + "].",e);
        } catch (SecurityException e) {
            throw new QueryException("Unable to instantiate query [" + type + "].",e);
        } catch (InstantiationException e) {
            throw new QueryException("Unable to instantiate query [" + type + "].",e);
        } catch (IllegalAccessException e) {
            throw new QueryException("Unable to instantiate query [" + type + "].",e);
        } catch (InvocationTargetException e) {
            throw new QueryException("Unable to instantiate query [" + type + "].",e);
        } catch (NoSuchMethodException e) {
            throw new QueryException("Unable to instantiate query [" + type + "].",e);
        }
    }
    
    public static void registerQuery(String name, Class queryClass) { 
        if (_queries.containsKey(name)) 
            _logger.warn("Overwriting query implementation for [" + name + "]");
        
        if (_logger.isDebugEnabled())
            _logger.debug("Registering query [" + name + "]");
        
        _queries.put(name, queryClass);
    } 
}
