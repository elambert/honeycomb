package com.sun.dtf.query;

import java.util.HashMap;

import com.sun.dtf.exception.QueryException;

public class Cursor {

    private QueryIntf _query = null;
    
    public Cursor(QueryIntf query) { 
        _query = query;
    }
    
    public String getCursorName() { return _query.getProperty(); } 

    public HashMap next(boolean recycle) throws QueryException { 
        return _query.next(recycle);
    }
    
    public void close() throws QueryException { _query.close(); } 
    public QueryIntf getQuery() { return _query; } 
}
