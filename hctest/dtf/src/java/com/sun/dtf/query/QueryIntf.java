package com.sun.dtf.query;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

import com.sun.dtf.actions.conditionals.Condition;
import com.sun.dtf.exception.QueryException;
import com.sun.dtf.exception.RecorderException;


public interface QueryIntf {
    
    /**
     * 
     * @param fields
     * @param constraints
     * @return
     * @throws RecorderException
     */
    public void open(URI uri,
                     ArrayList fields, 
                     Condition constraints, 
                     String event,
                     String property) throws QueryException;
   
    /**
     * 
     * @return
     */
    public String getProperty();
    
    /**
     * 
     *
     */ 
    public HashMap next(boolean recycle) throws QueryException;
   
    /**
     * 
     *
     */
    public void close() throws QueryException;
}
