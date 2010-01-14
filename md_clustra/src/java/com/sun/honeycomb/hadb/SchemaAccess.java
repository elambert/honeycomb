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



package com.sun.honeycomb.hadb;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.sql.Statement;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.EMDConfigException;

public class SchemaAccess {

    private static final Logger LOG = Logger.getLogger(SchemaAccess.class.getName());

    private Connection connection;
    private ArrayList tableArray;	// tables that already exist
    private ArrayList indexArray;	// indexes that already exist

    private static SchemaAccess instance;

    private SchemaAccess() {
        connection = null;
    }
    public static synchronized SchemaAccess getInstance(Connection connection) 
        throws SQLException, NoConnectionException {
        if (instance == null) {
            // Create a new instance and initialize it
            SchemaAccess sa = new SchemaAccess();
            sa.init(connection);

            // If initialization succeeded, the instance is usable
            instance = sa;
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
        }
        instance = null;
    }

    private void init(Connection _connection)
        throws SQLException, NoConnectionException {

        connection = _connection;
        if (connection == null) {
            connection = HADBJdbc.getInstance().getConnection();
        }

        DatabaseMetaData metadata = connection.getMetaData();
        ResultSet resultSet = metadata.getTables(null, "sysroot", null, null);

        tableArray = new ArrayList();
        indexArray = new ArrayList();
        
        StringBuilder sb = new StringBuilder();
        try {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME").trim();
                sb.append(tableName).append(", ");
                tableArray.add(tableName.toUpperCase());
                if (tableName != null) {
                    Statement statement = null;
                    ResultSet indexResultSet = null;
                    try {
                        statement = connection.createStatement();
                        indexResultSet = RetryableCode.retryExecuteQuery(statement, 
                                                                         "select indexname from sysroot.allindexes where tablename='" +
                                                                         tableName + "'");
                        if (indexResultSet != null) {
                            while (indexResultSet.next()) {
                                String indexName = indexResultSet.getString("indexname");
                                if (indexName != null) {
                                    addIndex(tableName,indexName);
                                }
                            }
                        }
                    } finally {
                        if (indexResultSet != null)
                            try { indexResultSet.close(); } catch (SQLException e) {}
                        if (statement != null)
                            try { statement.close(); } catch (SQLException e) {}
                    }
                }
            }
        } finally {
            if (resultSet != null) {
                try { resultSet.close(); } catch (SQLException e) {}
            }

        }
        LOG.info("Tables Present in HADB: "+sb.toString());
    }    

    public Connection getConnection() {
        return(connection);
    }
    
    public void close() {
        if (connection != null) {
            LOG.info("Instance is being reset.");
            HADBJdbc.getInstance().freeConnection(connection);
            connection = null;
            tableArray = null;
            indexArray = null;
        }
    }
    
    public void addIndex(String tableName, String indexName) {
        String fullIndexName = tableName.trim() + "." + indexName.trim();
        LOG.info ("I see index ["+fullIndexName+"] in HADB");
        indexArray.add(fullIndexName);
    }


    public boolean containsIndex(String tableName, String indexName) {
        boolean found = false;
        String fullIndexName = tableName.trim() + "." + indexName.trim();
        for (int i=0; i<indexArray.size(); i++) {
            if (fullIndexName.equalsIgnoreCase((String)(indexArray.get(i)))) {
                found = true;
                break;
            }
        }
        LOG.info ("containsIndex returns "+found+" for ["+
                          fullIndexName+"] in HADB");
        return(found);
    }

    public boolean containsTable(String tableName) {
        boolean found = false;
        
        for (int i=0; i<tableArray.size(); i++) {
            if (tableName.equalsIgnoreCase((String)(tableArray.get(i)))) {
                found = true;
                break;
            }
        }
        LOG.info ("containsTable returns "+found+" for ["+
                          tableName+"] in HADB");

        return(found);
    }

    public void executeSQL(String sql) 
        throws SQLException {
        Statement statement = null;

        if (LOG.isLoggable(Level.INFO))
            LOG.info("Executing SQL \"" + sql + "\"");

        try {
            statement = connection.createStatement();
            RetryableCode.retryExecute(statement, sql);
        } finally { 
            if (statement != null) {
                try { statement.close(); } catch (SQLException e) {}
                statement = null;
            }
        }
    }

    public void dropTable(String tableName) 
        throws SQLException {
        if (! containsTable(tableName)) {
            LOG.info("dropTable sees "+tableName+" is already dropped!");
            return;
        }

        try {
            executeSQL("drop table "+tableName);
            tableArray.remove(tableName.toUpperCase());
        } catch (SQLException e) {
            LOG.log(Level.INFO,
                    "Failed to drop table "+tableName+
                    " ["+e.getMessage()+"]",e);
            throw e;
        }
    }

    public boolean checkAndCreate(String tableName,
                               String definition)
        throws SQLException {
        boolean result = false;
        if (containsTable(tableName)) {
            LOG.info("HADB already contains the table ["+
                     tableName+"]");
        } else {
            LOG.info("Table ["+tableName+"] does not exist. Creating ...");
            StringBuffer sql = new StringBuffer();
            sql.append("create table ");
            sql.append(tableName);
            sql.append(definition);
            try {
                executeSQL(sql.toString());
                result = true;
            } catch (SQLException e) {
                if (e.getErrorCode() == 11711) {
                    LOG.log(Level.INFO, 
                            "Tried to create table "+tableName+" but it is already there.",
                            e);
                } else {
                    throw(e);
                }
            }//try

            LOG.info ("Table ["+tableName+"] has been created in HADB");
            tableArray.add(tableName.toUpperCase());
        }
        return result;
    }

    public void checkAndCreateIndex(String tableName, 
                                    String indexName,
                                    String fields)
        throws SQLException {
        if (containsIndex(tableName,indexName)) {
            LOG.info("HADB already contains the index ["+
                     tableName+"."+indexName + "]");
        } else {
            LOG.info ("Creating index "+indexName+" on table "+tableName);
            StringBuffer sql = new StringBuffer();
            sql.append("create index ");
            sql.append(indexName);
            sql.append(" on " + tableName + " " + fields);
            try {
                executeSQL(sql.toString());
                addIndex(tableName,indexName);
                LOG.info ("Index ["+tableName+"."+indexName +
                          "] has been created in HADB");
            } catch (SQLException e) {
                if (e.getErrorCode() == 11762) {
                    if (LOG.isLoggable(Level.INFO)) {
                        LOG.info("Index "+indexName+" is already there, so skip creating it");
                    }
                } else {
                    LOG.log(Level.WARNING, "FAILED to create index "+indexName+
                            " on table "+ tableName+
                            ".  This is a non-fatal error, but it"+
                            " may impact performance. Error=",
                            e);
                } // if/else (e.getErrorCode()...)
            } // try/catch
        } // if/else (containsIndex(tableName,indexName))
    }//checkAndCreateIndex
}
