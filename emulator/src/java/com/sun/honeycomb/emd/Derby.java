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



package com.sun.honeycomb.emd;

import java.io.File;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.honeycomb.emd.common.EMDException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.sql.DatabaseMetaData;

public class Derby {
    
    private static final Logger LOG = Logger.getLogger(Derby.class.getName());

    private static Derby instance;

    public static synchronized Derby getInstance() {
        if (instance == null) {
            instance = new Derby();
            instance.init();
        }

        return(instance);
    }

    private static final String DERBY_URL_PREFIX = "jdbc:derby:";
    private static final File systemDir = new File(System.getProperty("emulator.root")+
                                                   File.separator+"var"+
                                                   File.separator+"metadata");

    private HashMap dbs;

    private Derby() {
        dbs = new HashMap();
    }

    private void init() {
        // Check that the system directory exists
        if (!systemDir.exists()) {
            systemDir.mkdir();
        }
        
        System.setProperty("derby.system.home", systemDir.getAbsolutePath());
        
        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        } catch (ClassNotFoundException e) {
            LOG.log(Level.SEVERE,
                    "Failed to load the derby JDBC driver ["+
                    e.getMessage()+"]",
                    e);
            System.exit(1);
        }
    }

    public synchronized void stop() {
        if (instance == null) {
            return;
        }

        try {
            DriverManager.getConnection(DERBY_URL_PREFIX+";shutdown=true");
        } catch (SQLException e) {
        }

        dbs.clear();
        instance = null;
    }

    public void stop(String db) 
        throws EMDException {
        
        if (dbs.get(db) == null) {
            return;
        }

        try {
            DriverManager.getConnection(DERBY_URL_PREFIX+
                                        db+
                                        ";shutdown=true");
        } catch (SQLException e) {
        } finally {
            dbs.remove(db);
        }
    }

    public Connection getConnection(String db)
        throws SQLException {
        boolean create = false;

        if (dbs.get(db) == null) {
            File dbFile = new File(systemDir.getAbsolutePath()
                                   +File.separator+db);
            if (!dbFile.exists()) {
                create = true;
            }
            dbs.put(db, dbFile);
        }

        return(DriverManager.getConnection(DERBY_URL_PREFIX+db+
                                           (create ? ";create=true" : "")));
    }

    public void checkTable(String db,
                           String tableName,
                           DerbyAttributes attributes)
        throws EMDException {

        boolean foundTable = false;
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            connection = getConnection(db);
            if (!Derby.containsTable(connection,tableName)) {
                try {
                    StringBuffer sb = new StringBuffer();
                    sb.append("create table ");
                    sb.append(tableName);
                    sb.append(" (");
                    attributes.walk(new CreateTable(sb));
                    sb.append(")");

                    statement = connection.createStatement();
                    
                    LOG.info("Creating table ["+tableName+"] in database ["+
                             db+"] with command ["+
                             sb+"]");
                    
                    statement.execute(sb.toString());
                } finally {
                    if (statement != null)
                        try { statement.close(); } catch (SQLException e) {}
                }
            } else {
                LOG.info("Table ["+tableName+"] already exists in database ["+db+"]");
            }

        } catch (SQLException e) {
            EMDException newe = new EMDException("Failed to check the extended cache ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (connection != null) 
                try { connection.close(); } catch (SQLException e) {}
        }
        
    }

    public static boolean containsTable(Connection connection,
                                        String tableName)
        throws EMDException {

        ResultSet resultSet = null;


        try {
            DatabaseMetaData metadata = connection.getMetaData();
            resultSet = metadata.getTables(null, "APP", null, null);
            while (resultSet.next()) {
                if (resultSet.getString("TABLE_NAME").equalsIgnoreCase(tableName)) {
                    return true;
                }
                
            }
        } catch (SQLException e) {
            EMDException newe = new EMDException("containsTable failed");
            newe.initCause(e);
            throw newe;
        } finally {
            if (resultSet != null) 
                try { resultSet.close(); } catch (SQLException e) {}
            resultSet = null;
        }
        return false;
    }

    private static class CreateTable 
        implements DerbyAttributes.Callback {
        private StringBuffer buffer;
        private boolean first;
        
        public CreateTable(StringBuffer _buffer) {
            buffer = _buffer;
            first = true;
        }

        public void step(DerbyAttributes.Entry attribute)
            throws EMDException {
            if (!first) {
                buffer.append(", ");
            } else {
                first = false;
            }
            buffer.append(attribute.name);
            buffer.append(" ");
            buffer.append(attribute.data);
            if ((attribute.flags & DerbyAttributes.FLAG_PRIMARYKEY) != 0) {
                buffer.append(" primary key");
            }
        }
    }
}
