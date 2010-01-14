package com.sun.dtf.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.dtf.exception.DBException;
import com.sun.dtf.logger.DTFLogger;

class DerbyConnMgr extends DBConnMgr {

    private static DTFLogger _logger = DTFLogger.getLogger(DerbyConnMgr.class);
    
    private static DerbyConnMgr _instance = null;
    
    private Hashtable _connections = null;
    
    private DerbyConnMgr() {
        _connections = new Hashtable();
    }
    
    public static synchronized DBConnMgr getInstance() { 
        if (_instance == null) 
            _instance = new DerbyConnMgr();
        
        return _instance;
    }

    private void loadDriver() throws DBException {
        // DEBUG purposes only!
        //System.setProperty("derby.language.logQueryPlan", "true");
        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        } catch (ClassNotFoundException e) {
            throw new DBException("Issue loading derby driver.",e);
        }
    }
   
    public synchronized Connection getConnection(String dbpath,
                                                 String dbuser,
                                                 String dbpass,
                                                 boolean append) throws DBException { 
        loadDriver();
        
        String connectString = "jdbc:derby:" + dbpath + ";create=true" +
                               ";user=" + dbuser + ";password=" + dbpass;
        
        if (!append) { 
            Connection connection = (Connection)_connections.remove(dbpath);
            if (connection != null) {
                try {
                    // necessary Derby shutdown sequence for this connection
                    DriverManager.getConnection(connectString + ";shutdown=true").close();
                    connection.close();
                } catch (SQLException ignore) { }
            }
        }

        if (_connections.containsKey(dbpath))
            return (Connection) _connections.get(dbpath);
        
        try {
            Connection connection = DriverManager.getConnection(connectString);
            _connections.put(dbpath, connection);
            return connection;
        } catch (SQLException e) {
            throw new DBException("Error opening DB.",e);
        }
    }
    
    public void close() {
        Enumeration connections = _connections.keys();
        
        while (connections.hasMoreElements()) {
            String key = (String)connections.nextElement();
            Connection connection = null;
            try {
                connection = ((Connection)_connections.get(key));
                connection.close();
            } catch (SQLException e) {
                _logger.info("Error closing DB connection. " + key,e);
            }
        }
    }
}
