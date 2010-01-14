package com.sun.dtf.recorder;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.event.Attribute;
import com.sun.dtf.actions.event.AttributeType;
import com.sun.dtf.database.DBConnMgr;
import com.sun.dtf.exception.DBException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.RecorderException;
import com.sun.dtf.exception.StorageException;
import com.sun.dtf.query.DBQuery;
import com.sun.dtf.util.StringUtil;


public class DBRecorder extends RecorderBase {

    private Connection _connection = null;
    private URI _uri = null;
    private boolean _append = true;
    
    public DBRecorder(URI uri, boolean append) {
        super(append);
       _uri = uri; 
       _append = append;
    }
   
    public static Connection getConnection(URI uri, boolean append) throws DBException { 
        try {
            String dbpath = Action.getStorageFactory().getPath(uri);
            String dbuser = Action.getConfig().getProperty(DTFProperties.DTF_DB_USERNAME);
            String dbpass = Action.getConfig().getProperty(DTFProperties.DTF_DB_PASSWORD);
            return DBConnMgr.getInstance().getConnection(dbpath,dbuser,dbpass,append);
        } catch (StorageException e) {
            throw new DBException("Error creating db.",e);
        }
    }
   
    public static String genTablename(String tablename) {
       String result = StringUtil.replace(tablename, ".","_");
       result = StringUtil.replace(result,"*",""); 
       
       if (result.endsWith("_"))
           result = result.substring(0,result.length()-1);
           
       return result;
    }
    
    public void start() throws RecorderException {
        try {
            _connection = getConnection(_uri,_append);
        } catch (DBException e) {
            throw new RecorderException("Issue connecting to db.", e);
        } 
       
        // need something better here...
        Statement statement = null;
        try {
            statement = _connection.createStatement();
            statement.execute(SYS_TABLE);
            statement.execute(COL_TABLE);
        } catch (SQLException ignore) {} 
    }
   
    private String SYS_TABLE = "CREATE TABLE SYS_TABLE ( " + 
                               " table_name VARCHAR(32)," +
                               " PRIMARY KEY (table_name) )";
    
    private String COL_TABLE = "CREATE TABLE COL_TABLE ( " + 
                               " table_name    VARCHAR(32), " + 
                               " column_name   VARCHAR(32), " + 
                               " index         CHAR(1), " + 
                               " FOREIGN KEY (table_name) REFERENCES SYS_TABLE )";
    
    private String INSERT_STATEMENT = "INSERT INTO SYS_TABLE (TABLE_NAME) VALUES (?)";

    private String createStatement(String tablename) { 
        return "CREATE TABLE " + tablename + " (" + 
               " id       BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL," +
			   " start    BIGINT," +
			   " stop     BIGINT)";
    }
    
    public static String createAddColumnStatement(String columnname, String tablename, int length) { 
        return "ALTER TABLE " + tablename + " ADD \"" + columnname + "\" VARCHAR(" + length + ")";
    }
    
    public static String createAddColumnIndexStatement(String columnname, String tablename) { 
        return "CREATE INDEX " + tablename + "_" + columnname + "_index ON " + tablename + "(\"" + columnname +  "\")";
    }
   
    private HashMap _tables = new HashMap();
    private synchronized void verifyTableExists(String tablename) throws SQLException, RecorderException { 
        /* Check for tables existence if it's not in the SYS_TABLES 
         * table then you can assume it wasn't created and create it.
         */
        if (_tables.containsKey(tablename)) { 
            return;
        } else  {
            synchronized (_connection) {
                Statement statement = _connection.createStatement();
                String query = "select table_name from SYS_TABLE where table_name = '" 
                               + genTablename(tablename) + "'";
                ResultSet res = statement.executeQuery(query);
                try { 
                    if (!res.next()) { 
                        PreparedStatement _insertTableStmt = 
                                             _connection.prepareStatement(INSERT_STATEMENT);
                        
                        // No Table so lets create it.
                        statement.execute(createStatement(genTablename(tablename)));
                        _insertTableStmt.clearParameters();
                        _insertTableStmt.setString(1, genTablename(tablename));
                        _insertTableStmt.executeUpdate();
                    }
                } finally { 
                    res.close();
                }
            }
            
            _tables.put(tablename, new Boolean(true));
        }
    }
   
    public void stop() throws RecorderException { }

    public void record(Event event) throws RecorderException {
        String updateQueryStr  = null;
        try {
            // make sure event table exists otherwise create it!
            verifyTableExists(event.getName());
            String tablename = genTablename(event.getName());
            Iterator e = event.children().iterator();
            
            StringBuffer updateQuery = new StringBuffer("INSERT INTO ");
            updateQuery.append(tablename);
            updateQuery.append(" (START,STOP,");
                
            StringBuffer values = new StringBuffer(") VALUES (?,?,");
              
            StringBuffer update = new StringBuffer("UPDATE ");
            update.append(tablename);
            update.append(" SET ");
                
            StringBuffer updateWhere = new StringBuffer("WHERE ");
            boolean oneIndex = false;
                
            while (e.hasNext()) { 
                Attribute attribute = (Attribute) e.next();
                updateQuery.append("\"");
                updateQuery.append(attribute.getName());
                updateQuery.append("\",");

                values.append("?,");
                checkAndCreateColumn(tablename,
                                     attribute.getName(),
                                     attribute.getLength(),
                                     event,
                                     _connection);

                if (!attribute.isIndex()) {
                    update.append("\"");
                    update.append(attribute.getName());

                    if (AttributeType.getType(attribute.getType()) == AttributeType.STRING_TYPE) {
                        update.append("\"='");
                        update.append(attribute.getValue());
                        update.append("',");
                    } else {
                        update.append("\"=");
                        update.append(attribute.getValue());
                        update.append(",");
                    }
                } else {
                   oneIndex = true;
                   updateWhere.append("\"");
                   updateWhere.append(attribute.getName());
                  
                   if (AttributeType.getType(attribute.getType()) == AttributeType.STRING_TYPE) {
                       updateWhere.append("\"='");
                       updateWhere.append(attribute.getValue());
                       updateWhere.append("' AND");
                   } else {
                       updateWhere.append("\"=");
                       updateWhere.append(attribute.getValue());
                       updateWhere.append(" AND");
                   }
                }
            }
             
            String updateWhereStr = updateWhere.substring(0,updateWhere.length()-3);
            
            ResultSet res = null;
            boolean isIndexed = false;
            synchronized(_connection) { 
                try {    
                    if (oneIndex) { 
                        try { 
                            Statement statement = _connection.createStatement();
                            res = statement.executeQuery("select * from " + tablename + 
                                                         " "  + updateWhereStr);
                            isIndexed = oneIndex && res != null && res.next();
                        } catch (SQLException ignore) { }
                    }
                } finally { 
                    if (res != null) 
                        res.close();
                }
                
                if (isIndexed) { 
                    // Update current row.
                    Statement statement = _connection.createStatement();
                    String updateStr = update.substring(0,update.length()-1);
                    updateStr = updateStr + " " + updateWhereStr;
                    statement.executeUpdate(updateStr);
                } else {
                    // Now insert the full event with all of the values filled in 
                    updateQueryStr = updateQuery.substring(0,updateQuery.length()-1);
                    updateQueryStr += values.substring(0,values.length()-1) + ")";
                       
                    PreparedStatement prep = _connection.prepareStatement(updateQueryStr);
                    e = event.children().iterator();
                    prep.clearParameters();
                        
                    prep.setLong(1, event.getStart());
                    prep.setLong(2, event.getStop());
                    int index = 3;
                    while (e.hasNext()) {
                        Attribute attribute = (Attribute)e.next();
                        prep.setString(index++, attribute.getValue());
                    }
                    prep.execute();
                }
            }
        } catch(SQLException e) {
            Action.getLogger().info("children: " + event.children().size());
            Action.getLogger().info("Query: " + updateQueryStr);
            throw new RecorderException("Error recording.",e);
        } catch (ParseException e) {
            throw new RecorderException("Error recording.",e);
        }
    }
   
    private HashMap _columns = new HashMap();
    public synchronized void checkAndCreateColumn(String tableName, 
                                                  String columnName,  
                                                  int length,
                                                  Event event, 
                                                  Connection connection) 
           throws SQLException, ParseException { 
        String key = tableName + "." + columnName;
        if (_columns.containsKey(key)) {
            return;
        } else { 
            synchronized (connection) { 
                Statement statement = connection.createStatement();
                // Check if column is already in the db.
                String query = "SELECT table_name FROM COL_TABLE WHERE table_name = '" + 
                               tableName + "' AND COLUMN_NAME = '" + columnName  + "'";
                
                ResultSet res = statement.executeQuery(query);
                try { 
                    if (!res.next()) { 
                        // create column :) 
                        statement.executeUpdate(
                                            createAddColumnStatement(columnName,
                                                                     tableName,
                                                                     length));
            
                        if (event.isIndex(columnName))
                            statement.executeUpdate(
                                       createAddColumnIndexStatement(columnName,
                                                                     tableName));
                        
                        // update COL_TABLE
                        statement.execute("INSERT INTO COL_TABLE values('" + 
                                          tableName + "','" + columnName + 
                                          "','" + (event.isIndex(columnName) ? 1 : 0) +
                                          "')");
                    }
                } finally { 
                    res.close();
                }
            }
            
            _columns.put(key,new Boolean(true));
        }
    }
}
