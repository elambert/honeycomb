package com.sun.dtf.database;

import java.sql.Connection;

import com.sun.dtf.exception.DBException;


public abstract class DBConnMgr {
  
    private static DBConnMgr _instance = null;
    
    public static synchronized DBConnMgr getInstance() { 
        if (_instance == null) 
            _instance = DerbyConnMgr.getInstance();
        
        return _instance;
    }
        
    public abstract Connection getConnection(String dbpath,
                                             String dbuser,
                                             String dbpass,
                                             boolean append) throws DBException;  
    
    public abstract void close() throws DBException;
}
