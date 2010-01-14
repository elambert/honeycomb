package com.sun.dtf.query;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.sun.dtf.actions.conditionals.And;
import com.sun.dtf.actions.conditionals.Condition;
import com.sun.dtf.actions.conditionals.Eq;
import com.sun.dtf.actions.conditionals.Gt;
import com.sun.dtf.actions.conditionals.Lt;
import com.sun.dtf.actions.conditionals.Neq;
import com.sun.dtf.actions.conditionals.Or;
import com.sun.dtf.actions.event.Attribute;
import com.sun.dtf.actions.event.Field;
import com.sun.dtf.actions.util.DTFProperty;
import com.sun.dtf.exception.DBException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.QueryException;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.recorder.DBRecorder;

public class DBQuery implements QueryIntf {

    private static DTFLogger _logger = DTFLogger.getLogger(DBQuery.class);
    
    private URI _uri = null;
    private String _event = null;
    private String _property = null;
    
    private ArrayList _fields = null;
    private ArrayList _fieldNames = null;
    private Condition _constraints = null;
    
    private Connection _connection = null;
    private ResultSet _resultSet = null;
   
    private String process(String tablename, 
                           Condition cond,
                           HashMap fieldMap) 
            throws QueryException, ParseException, SQLException { 
        StringBuffer result = new StringBuffer();
        ArrayList children = cond.children();
        
        if (cond instanceof And) { 
            StringBuffer aux = new StringBuffer("(");
            for (int i = 0; i < cond.children().size(); i++) {
                aux.append(process(tablename, (Condition)children.get(i), fieldMap));
                aux.append(" AND ");
            }
            result.append(aux.substring(0, aux.length() - " AND ".length()));
            result.append(")");
        } else if (cond instanceof Or) { 
            StringBuffer aux = new StringBuffer("(");
            for (int i = 0; i < cond.children().size(); i++) {
                aux.append(process(tablename, (Condition)children.get(i), fieldMap));
                aux.append(" OR ");
            }
            result.append(aux.substring(0, aux.length() - " OR ".length()));
            result.append(")");
        } else {  
            String compar = null;
            
            if (cond instanceof Eq)  
                compar =  "=";
            else if (cond instanceof Neq) 
                compar = "<>";
            else if (cond instanceof Lt)  
                compar = "<";
            else if (cond instanceof Gt)  
                compar = ">";
            else 
                throw new QueryException("Uknown conditional: " + cond.getClass());
           
            String op1 = "", op2 = "";
          
            String field = null;
            String value = null;
           
            /*
             * figure out which of the operands are the field and which is 
             * the value 
             */
            if (checkColumn(tablename, cond.getOp1(), _event, _connection)) {
                field = cond.getOp1();
                value = cond.getOp2();
            } else  {
                field = cond.getOp2();
                value = cond.getOp1();
            }
            
            String type = (String) fieldMap.get(field);
           
            /* TODO: need to abstract the type from here to some 
             *       more abstract way of handling these types.
             */
            if (type != null && type.equals("int")) {
                op1 = "INT(\"" + field + "\")";
                op2 = "INT(" + value + ")";
            } else {
                op1 = "\"" + field + "\"";
                op2 = "'" + value + "'";
            }
            
            checkAndCreateColumn(tablename, 
                                 field, 
                                 DTFProperty.DEFAULT_PROPERTY_LENGTH,
                                 _event,
                                 _connection);
            
            if (cond.getNullable())
                result.append("(" + op1 + compar + op2 + " OR  " + op1 + " is NULL)");
            else
                result.append("(" + op1 + compar + op2 + ")");
        }
        
        return result.toString();
    }
    
    public synchronized void open(URI uri,
                                  ArrayList fields, 
                                  Condition constraints, 
                                  String event,
                                  String property)
           throws QueryException {
        _uri = uri;
        _event = event;
        _property = property;
        _fields = fields;
        _constraints = constraints;

        if (_fields != null) {
            _fieldNames = new ArrayList();

            for(int i = 0 ; i < _fields.size(); i++) { 
                try {
                    _fieldNames.add(((Field)_fields.get(i)).getName().toLowerCase());
                } catch (ParseException e) {
                    throw new QueryException("Unable to get field name.",e);
                }
            }
        
            if (!_fieldNames.contains("start")) 
                _fieldNames.add("start");
            
            if (!_fieldNames.contains("stop")) 
                _fieldNames.add("stop");
        }
       
        try {
            _connection = DBRecorder.getConnection(_uri,true);
        } catch (DBException e) {
            throw new QueryException("Issue connecting to db.", e);
        }
        
        // To the query here and retrieve the result set we need for later 
        // applying the next method to.
        StringBuffer query = null;
        HashMap fieldMap = new HashMap();
          
        if (_fields != null) { 
            query = new StringBuffer("select id,start,stop,");
            Iterator iter = _fields.iterator();
            try {
    	        while (iter.hasNext()) {
    	            Field field = (Field) iter.next();
    	            query.append("\"" + field.getName() + "\",");
                    fieldMap.put(field.getName(),field.getType());
    	        } 
            } catch (ParseException e) { 
                throw new QueryException("Error retrieving name from field.", e);
            }
        } else {
            query = new StringBuffer("select * ");
        }
       
        String tablename = DBRecorder.genTablename(event);
        query.replace(query.length()-1,query.length(), "");
        query.append(" from " + tablename);
      
        try { 
	        if (_constraints != null) {
	            query.append(" where (");
	            String where = process(tablename, _constraints, fieldMap);
	            query.append(where + ")");
	        }
           
        } catch (ParseException e) { 
            throw new QueryException("Error retrieving name from field.", e);
        } catch (SQLException e) {
            throw new QueryException("Error retrieving name from field.", e);
        }
        
        try {
            if (_logger.isDebugEnabled())
                _logger.debug("Query: " + query.toString()); 
    
            // Use internal holding place for results in order to speed up the 
            // retrieval of results plus free up locks on the DB.
            synchronized(_connection) { 
                _resultSet = new QueryResults(query.toString(), _connection);
            }
        } catch (SQLException e) {
            throw new QueryException("Error querying db.", e);
        } catch (ParseException e) {
            throw new QueryException("Error querying db.", e);
        }
    }
    
    public synchronized HashMap next(boolean recycle) throws QueryException {
        try {
            if (_resultSet.next()) {
                HashMap result = new HashMap();
                ResultSetMetaData md = _resultSet.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) { 
                    String key = md.getColumnName(i);
                    Attribute attrib = (Attribute)_resultSet.getObject(key);
                    String value = attrib.getValue() == null ? "null" : attrib.getValue();
                   
                    if (_fieldNames == null || _fieldNames.contains(key.toLowerCase())) {
                        /*
                         * All results fields are store in the result attribute
                         */
                        result.put(_property + "." + key.toLowerCase(),value);
                    }
                }
                
                return result;
            } else if (recycle) {
                // Lets try to rerun the query to find new results... 
                open(_uri, _fields, _constraints, _event, _property);
                return next(false);
            }
                
            return null;
        } catch (SQLException e) {
            throw new QueryException("Error retrievening results.", e);
        } catch (ParseException e) {
            throw new QueryException("Error setting property.", e);
        }
    }
    
    public static void checkAndCreateColumn(String tableName, 
                                            String columnName, 
                                            int length,
                                            String event, 
                                            Connection connection)
            throws SQLException {
        synchronized(connection) { 
            Statement statement = connection.createStatement();
            // Check if column is already in the db.
            String query = "SELECT table_name FROM COL_TABLE WHERE table_name = '"
                           + tableName + "' AND COLUMN_NAME = '" + columnName + "'";
    
            ResultSet res = statement.executeQuery(query);
            try { 
                if (!res.next()) {
                    // create column :) 
                    if (_logger.isDebugEnabled())
                        _logger.debug("Creating column: " + columnName + " on table "
                                + tableName);
                    
                    statement.executeUpdate(DBRecorder.
                                            createAddColumnStatement(columnName, 
                                                                     tableName,
                                                                     length));
                    statement.executeUpdate(DBRecorder.
                                       createAddColumnIndexStatement(columnName,
                                                                     tableName));
        
                    // update COL_TABLE
                    statement.execute("INSERT INTO COL_TABLE values('" + tableName +
                                      "','" + columnName + "','1')");
                }
            } finally { 
                res.close();
            }
        }
    }

    public static boolean checkColumn(String tableName,
            String columnName, String event, Connection connection)
            throws SQLException {
        Statement statement = connection.createStatement();
        // Check if column is already in the db.
        synchronized(connection) { 
            String query = "SELECT table_name FROM COL_TABLE WHERE table_name = '"
                    + tableName + "' AND COLUMN_NAME = '" + columnName + "'";
    
            ResultSet res = statement.executeQuery(query);
            try { 
                if (!res.next())
                    return false;
                else 
                    return true;
            } finally { 
                res.close();
            }
        } 
    }
   
    public String getProperty() { return _property; } 
    
    public void close() throws QueryException {
        if (_resultSet != null) {
            try {
                _resultSet.close();
            } catch (SQLException e) {
                throw new QueryException("Unable to close ResultSet correctly.",e);
            }
        }
    }
}
