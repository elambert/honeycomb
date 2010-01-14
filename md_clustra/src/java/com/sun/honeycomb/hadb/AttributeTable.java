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

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Collections;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSetMetaData;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;

import com.sun.hadb.jdbc.NChar;

import com.sun.honeycomb.common.CanonicalStrings;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SoftwareVersion;

import com.sun.honeycomb.emd.common.EMDException;

import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.Table;
import com.sun.honeycomb.emd.config.Column;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.FsAttribute;

import com.sun.honeycomb.emd.parsers.QueryNode;
import com.sun.honeycomb.emd.parsers.QueryAttribute;
import com.sun.honeycomb.emd.parsers.QueryParser;
import com.sun.honeycomb.emd.parsers.ParseException;
import com.sun.honeycomb.emd.parsers.TokenMgrError;

import com.sun.honeycomb.hadb.convert.ConvertConstants;
import com.sun.honeycomb.hadb.convert.QueryConvert;

public class AttributeTable
    implements ConvertConstants {
    
    private static final Logger LOG = Logger.getLogger(AttributeTable.class.getName());

    private static AttributeTable instance = null;

    private String attrTableName;
    private Map tableMap;

    /// Index can include up to 15 columns
    public static final int HADB_MAX_INDEX_COLUMNS = 15;


    public static synchronized AttributeTable getInstance()
        throws EMDException {
        if (instance == null) {
            // Construct all local data structures
            instance = new AttributeTable();
            instance.load(ATTRIBUTE_TABLE_NAME);
        }
        
        return(instance);
    }
    public static synchronized AttributeTable getLoaded(String _attrTableName) 
        throws EMDException {
        AttributeTable at;

	    at = new AttributeTable();

        int nbLoaded = at.load(_attrTableName);
        if (nbLoaded < 0) 
            return null;
        return at;
    }


    public static synchronized AttributeTable getDebugInstance(File dir) {
        if (instance == null) {
            AttributeTable t = new AttributeTable();

            try {
                File f = new File(dir, "attrtable");
                if (f.exists())
                    t.loadFile(f);

                t.checkAndCreateAllTables(null, RootNamespace.getInstance());
                t.dump();

                instance = t;
                LOG.info("Debug instance created");
            }
            catch (Exception e) {
                LOG.log(Level.WARNING, "Couldn't init instance", e);
            }
        }
        
        return(instance);
    }

    public static synchronized void reset() {
        instance = null;
    }

    private AttributeTable() {
        tableMap = new HashMap();
    }

    
    public static void resetSchema() {
        LOG.info("Clearing cached AttributeTable.");
        reset();
        SchemaAccess.resetInstance();
    }

    // Only to be called from the master service !!!
    public static AttributeTable  activateSchema()
        throws SQLException, NoConnectionException, 
               EMDException, EMDConfigException {

        AttributeTable at = new AttributeTable();
        at.activateAll();
        return at;
    }
    public void activateAll() 
        throws SQLException, NoConnectionException, 
               EMDException, EMDConfigException {

        SchemaAccess schema = null;
        Connection connection = null;
        RootNamespace rootNS;        
        try {
            rootNS = RootNamespace.getInstance();

            schema = SchemaAccess.getInstance(null);
            
            boolean created = createAttributeTable(ATTRIBUTE_TABLE_NAME,schema);
            load(ATTRIBUTE_TABLE_NAME);
            
            if (created) {
                String version = SoftwareVersion.getRunningVersion();
                if (version != null) 
                    setHoneycombVersion(version);
            }
            
            checkAndCreateAllTables(schema, rootNS);
        }
        catch (SQLException e) {
            LOG.log(Level.WARNING, "Oops!", e);
            throw e;
        }
        catch (EMDConfigException e) {
            LOG.log(Level.WARNING, "Oops!", e);
            throw e;
        }
        catch (NoConnectionException e) {
            LOG.log(Level.WARNING, "Oops!", e);
            throw e;
        }
    }
    

    public String getAttributeTableName() {
        return attrTableName;
    }

    public Map getTableMap() {
        return tableMap;
    }

    public String[] getTableNames() {
        String[] tableNames = new String[tableMap.size()];
        tableMap.values().toArray(tableNames);
        return tableNames;
    }


    public String getHoneycombVersion() {
        return (String) tableMap.get(HONEYCOMB_VERSION_KEY);
    }

    public void setHoneycombVersion(String hcVersion) 
        throws EMDException {

        // assert hcVersion != null : hcVersion;

        if (hcVersion != null) 
            updateAttributeTableEntry(HONEYCOMB_VERSION_KEY, hcVersion);
    }
    public void updateAttributeTableEntry(String name, String tableName)
        throws EMDException{
        String keyName = NChar.literal(name);
        String insertSql =
            "insert into " + attrTableName + " values ("
            + keyName + ", '" + tableName + "')";
    
        SchemaAccess schema = null;
        try {
            schema = SchemaAccess.getInstance(null);
            schema.executeSQL(insertSql);
        } catch (SQLException e) {
            if (e.getErrorCode() == 11939) {
                // a row already exists
                try {
                    String updateSql=
                        "update "+attrTableName+
                        " set tablename='"+tableName+
                        "' where name = "+keyName;
                    schema.executeSQL(updateSql);
                } catch (SQLException sqle) {
                    LOG.log(Level.WARNING, "Oops!", sqle);
                    throw new EMDException(sqle);
                }
            } else {
                LOG.log(Level.WARNING, "Oops!", e);
                throw(new EMDException(e));
            }
        } catch (NoConnectionException e) {
            LOG.log(Level.WARNING, "Oops!", e);
            throw new EMDException(e);
        } // catch
        tableMap.put(name, tableName);
    }

    void reloadAll(SchemaAccess schema, RootNamespace rootNS)
        throws EMDException {
        try {
            reload();
            checkAndCreateAllTables(schema, rootNS);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE,
                    "Failed to create the proper Metadata tables from the attributes table",
                    e);
            throw new RuntimeException(e);
        }
    }

    synchronized void reload() 
        throws EMDException {
        try {
            int nbLoaded;

            nbLoaded= load(attrTableName);
            LOG.info("Loaded "+nbLoaded+" attributes in the attribute table");
        } catch (NoConnectionException e) {
            LOG.log(Level.SEVERE,
                    "Failed to load the attributes from the attributes table",
                    e);
            throw new RuntimeException(e);
        }
    }

    private void loadFile(File f)
        throws IOException {

        // load objects into "map" from text file
        LOG.info("Loading AttributeTable from text file "+f);
        BufferedReader inp = new BufferedReader(new FileReader(f));

        String line;
        while ((line = inp.readLine()) != null) {
            String[] fields = line.split(":", 2);

            if (LOG.isLoggable(Level.FINE))
                LOG.fine("Attribute \"" + fields[0] + "\" -> " +
                         fields[1]);
            
        tableMap.put(fields[0].trim(), fields[1].trim());
        } // while

        inp.close();
    } // loadFile


    public static boolean createAttributeTable(String name, SchemaAccess schema) {
        boolean created;
        try {
            created = schema.checkAndCreate(name,
                                            "( name nvarchar(64) primary key," +
                                            " tablename varchar(32) not null)");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return created;
    }

    public static void dropAttributeTable(String name, SchemaAccess schema) {
        try {
            schema.dropTable(name);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }        
    }



    public int load(String _attrTableName)
        throws EMDException {
        // Read the attributes from the attribute table
        Connection connection = null;
        Statement statement = null;
        ResultSet results = null;
        int nbLoaded = 0;
        LOG.info("Checking attribute table " + _attrTableName);

        assert(attrTableName == null ||
               attrTableName == _attrTableName);
        attrTableName = _attrTableName;

        try {
            connection = HADBJdbc.getInstance().getConnection();
            statement = connection.createStatement();
            results = RetryableCode.retryExecuteQuery(statement, 
                           "select name, tablename from "+
                           attrTableName);
            while (results.next()) {
                String configTableName = results.getString(1);
                String hadbTableName = results.getString(2);
                tableMap.put(configTableName,hadbTableName);
                nbLoaded++;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 11701) {
                LOG.info("table "+attrTableName+" is not there");
                // HADB-E-11701: Table attributetable not found
                return -1;
            }
            throw new EMDException(e);
        } catch (NoConnectionException e) {
            throw new EMDException(e);
        } finally {
            if (results != null)
                try { results.close(); } catch (SQLException e) {}
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null) {
                HADBJdbc.getInstance().freeConnection(connection);
                connection = null;
            }
        }
        
        if (LOG.isLoggable(Level.INFO)) {
            LOG.info(tableMap.toString());
        }

        return(nbLoaded);
    }
    
    private String assignUniqueTableName(Table table, SchemaAccess schema) {
        String fullName = table.getName();
        String name = fullName;
        if (name.length() > 21) {
            name = name.substring(0,21);
        }

        
        String baseCandidate = null;
        //If table name contains any non-ASCII chars, 
        //  just punt and use hashcode
        if (name.matches("\\p{Alpha}(\\p{Alnum}|_)*")) {
            baseCandidate = "T_" + name;
        } else {
            baseCandidate = "T_" + Integer.toHexString(name.hashCode());
        }
        baseCandidate = baseCandidate.toUpperCase();

        String candidate = baseCandidate;
            
        int base = 0;
        while (schema.containsTable(candidate)) {
            base++;
            if (base == Integer.MAX_VALUE)
                base = 0;
            candidate = baseCandidate + "_"+
                Integer.toHexString(base).toUpperCase();
            LOG.fine("Trying next candidate: "+candidate);
        } // while
        LOG.info("Assigning config table "+name+" to new HADB table "+candidate);
        assert(candidate.length() <= 32);

        //cf loadTable
        tableMap.put(fullName,candidate);

        return(candidate);
    }

    public String getTableName(Table table) 
        throws EMDException {
        return getTableName(table.getName());
    }
    public String getTableName(String name) 
        throws EMDException {
        String tableName = (String) tableMap.get(name);
        if (tableName == null) {
            LOG.info("Couldn't find the HADB table for ["+
                     name+
                     "] so reloading AttributeTable...");
            reload();		// reload from HADB
            tableName = (String) tableMap.get(name);
            if (tableName == null) {
                throw new EMDException("Couldn't find the HADB table for ["+
                                       name+"]");
            } // if
        } // if
        return tableName;
    } // getTableName

    public String getTableNameForField(Field field) 
        throws EMDException {
        Table table = null;
        Column col = field.getTableColumn();
        if (col != null) {
            table = col.getTable();
        }
        if (table  == null)
            throw new EMDException("field "+field+" has no assigned config table?!");

        String tableName = (String) tableMap.get(table.getName());
        if (tableName == null) {
            LOG.info("Couldn't find the HADB table for ["+
                     field.getQualifiedName()+
                     "] so reloading list of fields...");
            reload();
            tableName = (String) tableMap.get(table.getName());
            if (tableName == null) {
                throw new EMDException("Couldn't find the table for ["+
                                       field.getQualifiedName()+"]");
            } // if
        }
        return tableName;
    } // getTableNameForField


    private synchronized void createAllIndexes(SchemaAccess schema,
                                               RootNamespace rootNamespace) {

        Map tableVars = new HashMap(); // Table -> Set(Column)

        try {

            // Each fsView imputes an index on an initial subset of
            // attributes in the view that are from the same table.

            FsView[] views = rootNamespace.getViews();
            for (int i = 0; i < views.length; i++) {
                createIndex(views[i], schema, rootNamespace);
            } // for

            // Each indexed field imputes an index on itself.
            ArrayList fields = new ArrayList();
            rootNamespace.getFields(fields, true);
            for (int i = 0; i < fields.size(); i++) {
                Field field = (Field)fields.get(i);
                if (field.isIndexed()) {
                    createIndex(field, schema);
                }
            } // for
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Checking indexes", e);
            throw new InternalException(e);
        }
    }
    

    /** Create all the tables needed for the current schema */
     synchronized void checkAndCreateAllTables(SchemaAccess schema, 
                                               RootNamespace rootNS) 
        throws SQLException, EMDConfigException {
        
        Table [] tables = rootNS.getTables();
        for (int i = 0; i < tables.length; i++) {
            createMetadataTable(tables[i], schema);
        }

        createAllIndexes(schema, rootNS);
    }



    /**
     * @param table the EMDConfig Table of interest
     * @param schema access to HADB
     */
    void createMetadataTable(Table table,
                             SchemaAccess schema)
        throws SQLException, EMDConfigException {

        StringBuffer definition = new StringBuffer();

        ArrayList columns = new ArrayList();
        String tableName = (String) tableMap.get(table.getName());
        if (tableName == null)
            tableName = assignUniqueTableName(table, schema);

        table.getColumns(columns);
        for (int index = 0; index < columns.size(); index++){
            Column column = (Column)columns.get(index);
        
            // No column needed or allowed for object_id
            if (column.getFieldName().equals(OID_ATTRIBUTE)) {
                LOG.warning(OID_ATTRIBUTE+" should NOT occur in any table definition!");
                continue;
            }
            definition.append(column.getColumnName());
            definition.append(" ");
            definition.append(getSQLType(column));
            // even the last field needs a comma.
            definition.append(", ");
        }
        
        if (LOG.isLoggable(Level.INFO)) {
            String msg = "Creating HADB table " + tableName +
                ": {";
            for (int i = 0; i < columns.size(); i++)
                msg += " " + columns.get(i).toString();
            msg += " } ==> \"" + definition;
            LOG.info(msg + "\"");
        }

        String insertSql =
            "insert into " + attrTableName + " values ("
            + NChar.literal(table.getName()) + ", '" + tableName + "')";
    
        try {
            schema.executeSQL(insertSql);
        } catch (SQLException e) {
            if (e.getErrorCode() == 11939) {
                LOG.info("Attribute table insert skipped: "+
                         attrTableName+
                         " already contains a row for '"+
                         table.getName());
            } else {
                throw(e);
            }
        } // catch
        String attrTableSql =
            "( " + FIELD_OBJECTID + " " + OID_SQL_TYPE + ", " + definition +
            " primary key(" + FIELD_OBJECTID + "))" +
            " partitionkeys 1";

        schema.checkAndCreate(tableName, attrTableSql);
    }


    public void dropMetadataTable(Table table,SchemaAccess schema)
        throws EMDException {
        String name = table.getName();
        String tableName = (String)tableMap.get(name);

        assert(tableName != null);

        try {
            schema.dropTable(tableName);
        } catch (SQLException e) {
            throw new EMDException(e);
        }
        forgetMetadataTable(name);
    }


    public void forgetMetadataTables() 
        throws EMDException {

        LOG.info("Forgetting previous list of tables in "+attrTableName);
        String[] names = new String[tableMap.size()];
        tableMap.keySet().toArray(names);
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            forgetMetadataTable(name);
        }
    }

    public void forgetMetadataTable(String name)
        throws EMDException {

        StringBuffer sb = new StringBuffer();

        sb.append("delete from ");
        sb.append(attrTableName);
        sb.append(" where name = ?");
        String deleteSql = sb.toString();

        executeSQL(deleteSql, 
                   Collections.singletonList(name));
        tableMap.remove(name);
    }

    /**
     * Get all fields in the fsview (including fields used in the
     * filename) and collect the fields for the first table. Then
     * create an index with those fields.
     */
    private void createIndex(FsView view,
                             SchemaAccess schema,
                             RootNamespace rootNS) 
        throws SQLException {

        Table table = null;

        ArrayList attrs = view.getAllVariables();

        if (LOG.isLoggable(Level.INFO)) {
            String msg = "Create index for view " + view + " {";
            for (int i = 0; i < attrs.size(); i++)
                msg += " \"" + attrs.get(i).toString() + "\"";
            LOG.info(msg + " }");
        }

        Set s = new LinkedHashSet(); //preserve order from fsView!

        for (int i = 0; i < attrs.size(); i++) {
            String fieldName = (String) attrs.get(i);
            if (fieldName.equals(OID_ATTRIBUTE))
                continue;	// do not include this in index

            Field field = rootNS.resolveField(fieldName);
            if (field == null) {
                LOG.severe("Couldn't find attribute \"" + fieldName + "\"!");
                continue;
            }

            Column col = field.getTableColumn();
            if (col == null) {
                LOG.severe("Attribute " + field + " has no Column!");
                continue;
            }
            Table ctable = col.getTable();

            if (table == null) {
                // first time: just get the name of the table
                table = ctable;
            } else {
                if (!table.getName().equals(ctable.getName()))
                    break;
            }

            s.add(col);
        } // for

        // Only call createIndex if the view has a non-empty (ignoring
        // OID) set of attributes, i.e only if an index needs to be
        // created.
        if (table == null) {
            if (LOG.isLoggable(Level.INFO))
                LOG.info("No index required for view " + view +
                         " -- no non-OID attributes.");
        }
        else
            createIndex(table, s, view.getName(), schema);
    }

    private void createIndex(Field field,
                             SchemaAccess schema) 
        throws SQLException {
        LOG.info("Create index for field " + field);

        Column col = field.getTableColumn();
        Table table = col.getTable();
        Set s = new LinkedHashSet();
        s.add(col);
        createIndex(table, s, field.getName(), schema);
    }

    /**
     * Create an index for a list of fields.
     *
     * It is important that this routine can be called several times 
     * with the same list of fields. It only creates the index once.
     * Set cols should normally be a LinkedHashSet or other ordered set,
     * since the order of columns in the index is important.
     */
    private void createIndex(Table t, Set cols, String name, SchemaAccess schema)
        throws SQLException {
        StringBuffer sb = new StringBuffer();
        String separator = "";
        StringBuffer indexSb = new StringBuffer();
        int indexSize = 30 + 2 + 28; // general overhead + OID size 
        String tableName = (String) tableMap.get(t.getName());

        sb.append('(');
        indexSb.append('_');

        int ncols = 0;
        for (Iterator j = cols. iterator(); j.hasNext(); ) {
            Column c = (Column) j.next();
            int fieldSize = c.getFieldSize();
            String fieldIndexStr = String.valueOf(c.getTableIndex());
            if (indexSize + fieldSize > 1024 ||
                ++ncols > HADB_MAX_INDEX_COLUMNS) {
                LOG.log(Level.WARNING,"Excceeded the maximum number of "
                        + "index columns on table " + t + ", "
                        + c + " is truncated.");
                break;
            }
            sb.append(separator).append(c.getColumnName());
            separator = ", ";
            indexSb.append('_').append(fieldIndexStr);
            indexSize += fieldSize;
        }//for

        if (ncols == 0) {
            LOG.info("Index on table "+t+" has NO columns!");
            return;
        }

        sb.append(')');

        String indexName = "I" + tableName.substring(1) + indexSb;

        if (LOG.isLoggable(Level.INFO)) {
            LOG.info("Creating index " + indexName +
                     " on table " + t + " -> \"" + sb + "\"");
        }

        schema.checkAndCreateIndex(tableName, indexName, sb.toString());
    }

    private static String getSQLType(Column column)
        throws EMDConfigException {
        return getSQLType(column.getField());
    }

    private static String getSQLType(Field field)
        throws EMDConfigException {

        int type = field.getType();
        String typeString = null;
        int length;
        switch (type) {
        case Field.TYPE_LONG:
            typeString = "double integer";
            break;
                                
        case Field.TYPE_DOUBLE:
            typeString = "double precision";
            break;

        case Field.TYPE_STRING:
            length = field.getLength();
            typeString = "nvarchar(" + length + ")";
            break;
                
        case Field.TYPE_CHAR:
            length = field.getLength();
            typeString = "varchar(" + length + ")";
            break;
                
        case Field.TYPE_OBJECTID:
            typeString = OID_SQL_TYPE;
            break;

        case Field.TYPE_BINARY:
            length = field.getLength();
            typeString = "varbinary(" + length + ")";
            break;

        case Field.TYPE_DATE:
            typeString = "date";
            break;

        case Field.TYPE_TIME:
            typeString = "time(3)";
            break;

        case Field.TYPE_TIMESTAMP:
            typeString = "timestamp(3)";
            break;
                
        default:
            throw new EMDConfigException("HADB can't handle the type "+field.getTypeString());
        }
        return typeString;
    }


    public AttributeTable copyAttributeTable(String toTableName,
                                             SchemaAccess schema)
        throws EMDException {
        String query = "select name,tablename from "+ attrTableName;
        String ins = "insert into " + toTableName + " values(?,?)";

        AttributeTable.createAttributeTable(toTableName,schema);
        createTableFromQuery(query, ins, 2);

        return getLoaded(toTableName);
    }

    public void dump() {
        try {
            System.err.println("\nAttributes:");
            System.err.println(tableMap.toString());

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Construct a SQL string to insert the row represented by oid and
     * attributes into the given table.
     */
    public String makeInsertStatement(Table table) 
        throws EMDException {
        String tableName = getTableName(table);
            
        StringBuffer sb = new StringBuffer();
        int numIndexColumns = table.getColumnCount();

        // For each column name, get the corresponding value from the Map
        // and construct the insert statement

        // OID always has to be first
        sb.append("insert into ").append(tableName);
        sb.append(" values (?");

        for (int i = 0; i < numIndexColumns; i++) {
            sb.append(", ?");
        }
        sb.append(");");

        return sb.toString();
    }


    public static void addRowPreparedStatement(PreparedStatement stmt,
                                               List literals,
                                               String ins)
        throws SQLException {

        try {
            long startTime = System.currentTimeMillis();
            RetryableCode.retryExecutePrepared(stmt, literals);
            if (LOG.isLoggable(Level.INFO)) {
                long elapsed = System.currentTimeMillis() - startTime;
                LOG.info("instr " + elapsed + "ms for \"" + ins + "\" : " +
                         CanonicalStrings.literalsToString(literals));
            }
        } catch (SQLException e) {
            int errorCode = e.getErrorCode();
            if (errorCode == 11939) {
                //Handle non-fatal error conditions
                //    HADB-E-11939: Primary key constraint violation
                LOG.info("Insert stmt \"" + ins + "\" : " +
                         CanonicalStrings.literalsToString(literals) +
                         " got non-fatal HADB error: " + e);
            } else {
                LOG.log(Level.WARNING,
                        "Couldn't execute insert stmt \"" + ins + "\" : " +
                        CanonicalStrings.literalsToString(literals), 
                        e);
                throw(e);
            }
        } // try/catch
    } // addRowPreparedStatement


    public static List getFieldNamesForTable(Table table) {
        List columns = table.getColumns();
        List names = new ArrayList(columns.size());

        Iterator ite = columns.iterator();
        while (ite.hasNext()) {
            Column col = (Column) ite.next();
            names.add(col.getFieldName());
        }
        return names;
    }


    public static String getQueryForFields(List fieldNames) {
        assert(fieldNames.size() > 0);

        StringBuffer sb = new StringBuffer();
        String separator = "";
        Iterator ite = fieldNames.iterator();
        while (ite.hasNext()) {
            String name = (String) ite.next();
            sb.append(separator);
            sb.append("(\"");
            sb.append(QueryAttribute.insertBackslash(name));
            sb.append("\" is not null)");
            separator = " OR ";
        }
        return sb.toString();
    }

    // We currently cannot handle fields that did not exist in the
    // 1.0.1 schema, so we just avoid them.  HACK HACK.
    public static boolean skipConvert(List fieldNames) {
        assert(fieldNames.size() > 0);

        Iterator ite = fieldNames.iterator();
        while (ite.hasNext()) {
            String name = (String) ite.next();
            if (name.equals("system.test.type_binary") ||
                name.equals("system.test.type_char") ||
                name.equals("system.test.type_date") ||
                name.equals("system.test.type_time") ||
                name.equals("system.test.type_timestamp"))
                
                return true;
        }
        return false;
    }

    /**
     * create a query to read rows from several tables to match
     * an insert into a new fresh table.
     */
    public String createReadTableQuery(RootNamespace preUpgradeNS,
                                       Table newTable,
                                       NewObjectIdentifier lastOid,
                                       List retLiterals) 
        throws EMDException {

        List fieldNames = getFieldNamesForTable(newTable);
        LOG.info("createReadTableQuery: "+
                 " lastOid="+(lastOid == null ? "null" : lastOid.toHexString())+
                 " fieldNames: "+fieldNames);

        // A limitation of the current database converter is that it doesn't
        //  know how to skip over new fields (like system.test.type_binary)
        //  that did not exist in the old schema.
        if (skipConvert(fieldNames)) {
            LOG.info("Skipping createReadTableQuery for table "+newTable);
            return null;
        }

        String tableQuery = getQueryForFields(fieldNames);
        LOG.info("Honeycomb query for table "+newTable+
                 ": "+tableQuery);
        
        // Make sure we include system.object_id as a selected value
        //   (not otherwise mentioned in each table)
        fieldNames.add(0, OID_ATTRIBUTE);

        // Parse the tree
        QueryNode parsedQuery = null;

        try {
            parsedQuery = QueryParser.parse(tableQuery);
        } catch (ParseException e) {
            EMDException newe = new EMDException("Failed to parse the query ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (TokenMgrError e) {
            EMDException newe = new EMDException("Failed to parse the query ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        // Construct the SQL query, using this AttributeTable to resolve names,
        //   and using OUTER JOIN between the tables
        QueryConvert converter = 
            new QueryConvert(parsedQuery,       // honeycomb query
                             lastOid,           // allow restart in the middle
                             fieldNames,        // select all attributes
                             null,              // no boundParameters
                             this,              // get Table Names from here
                             preUpgradeNS,      // get Column Names from here
                             true);             // use OUTER JOIN

        StringBuffer sb = new StringBuffer();
        converter.convert(sb);
        List literals = converter.getLiterals();
        retLiterals.addAll(literals);
        String hadbQuery = sb.toString();
        LOG.info("HADB query for table "+newTable+": "+hadbQuery+ " : "+
                 CanonicalStrings.literalsToString(literals));
        return hadbQuery;
    }

    public static Object[] readRow(ResultSet rs, int size)
        throws SQLException {
        Object[] rowValues = new Object[size];

        for (int i=0; i<size; i++) {
            rowValues[i] = rs.getObject(i+1);
        }

        return rowValues;
    }


    public static void executeSQL(String sql, List literals) 
        throws EMDException {
        PreparedStatement myStatement = null;
        Connection connection = null;
        LOG.info("Executing SQL: '"+sql+"'"+
                 " : "+CanonicalStrings.literalsToString(literals));
        try {
            connection = HADBJdbc.getInstance().getConnection();
            myStatement = connection.prepareStatement(sql);

            RetryableCode.retryExecutePrepared(myStatement,literals);
            

        } catch (SQLException e) {
            EMDException newe = new EMDException("Failed to: "+sql+
                                                 " message is [" + e.getMessage()+"]",
                                                 e);
            throw newe;
        } finally {
            if (myStatement != null) {
                try { myStatement.close(); } 
                catch (SQLException e) {
                    LOG.log(Level.WARNING,"Unable to close Prepared Statement." + e);
                }
                myStatement = null;
            }
            if (connection != null) {
                HADBJdbc.getInstance().freeConnection(connection);
                connection = null;
            }
        } // finally
        
    } // executeSQL

    public void copyTable(String oldTableName, Table newTable)
        throws EMDException {
        createTableFromQuery("select * from "+oldTableName,
                                    newTable);
    }


    public void createTableFromQuery(String query, Table newTable)
        throws EMDException {
        int size = newTable.getColumnCount() + 1;       // + OBJECTID
        String ins = makeInsertStatement(newTable);     // throws EMDException
        createTableFromQuery(query,ins,size);
    }

    public void createTableFromQuery(String query, String ins, int size)
        throws EMDException {

        PreparedStatement queryStatement = null;
        PreparedStatement insertStatement = null;
        Connection connection = null;
        ResultSet rs = null;
        Object[] rowValues = null;

        int numConverted = 0;
        LOG.info("createTableFromQuery: \n\tquery='"+query+"'\n"+
                 "\tins='"+ins+"'");

    
        try {
            connection = HADBJdbc.getInstance().getConnection();
            queryStatement = connection.prepareStatement(query);
            insertStatement = connection.prepareStatement(ins);

            ResultSetMetaData rsmd = queryStatement.getMetaData();
            if (size != rsmd.getColumnCount())
                throw new InternalException("Unexpected result set size="+
                                            rsmd.getColumnCount()+
                                            " in copyTable query, expected="+size);

            rs = RetryableCode.retryExecutePreparedQuery(queryStatement,null);
            

            while (rs.next()) {
                rowValues = readRow(rs,size);
                //transformRow(rowValues); (Call a transformer object?)
                addRowPreparedStatement(insertStatement, 
                                        Arrays.asList(rowValues),
                                        ins);
                numConverted++;
            }

            LOG.info("converted "+numConverted+" rows");

        } catch (SQLException e) {
            EMDException newe = new EMDException("Failed to convert row.  numConverted="+
                                                 numConverted+
                                                 (rowValues != null && rowValues.length > 0 ? 
                                                  (" OID="+CanonicalStrings.encode(rowValues[0])) :
                                                  "")+
                                                 " message is [" + e.getMessage()+"]",
                                                 e);
            throw newe;
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (SQLException e) {}
                rs = null;
            }
            if (queryStatement != null) {
                try { queryStatement.close(); } 
                catch (SQLException e) {
                    LOG.log(Level.WARNING,"Unable to close Prepared Statement." + e);
                }
                queryStatement = null;
            }
            if (insertStatement != null) {
                try { insertStatement.close(); } 
                catch (SQLException e) {
                    LOG.log(Level.WARNING,"Unable to close Prepared Statement." + e);
                }
                insertStatement = null;
            }
            if (connection != null) {
                HADBJdbc.getInstance().freeConnection(connection);
                connection = null;
            }
        } // finally

    }


    public static void main(String[] args)
        throws EMDException {

        if (args.length < 1) {
            System.err.println("Expected one argument: <dir-name>");
            System.exit(1);
        }

        String dirName = args[0];
        File dir = new File(dirName);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("No dir \"" + dir.getAbsolutePath() + "\"");
            System.exit(2);
        }

        System.setProperty("emulator.root", dir.getAbsolutePath());
        System.setProperty("uid.lib.path", "emulator");

        String jdbcurl = null;
        if (args.length > 1) {
            if (!args[1].equalsIgnoreCase("HADB")) {
                jdbcurl = "jdbc:sun:hadb:"+args[1]+":15005";
                System.setProperty(HADBJdbc.MD_HADB_JDBC_URL_PROPERTY, jdbcurl);
            } else {
                jdbcurl = System.getProperty(HADBJdbc.MD_HADB_JDBC_URL_PROPERTY);
            }
        }

        AttributeTable attrtable = null;

        try {
            //Initialize HADBJdbc within this JVM
            HADBJdbc.getInstanceWithUrl(jdbcurl);
            AttributeTable.activateSchema();
        } catch (Exception e) {
            System.out.println("Operation failed " + e);
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("AttributeTable activateSchema succeeded.");


        HashMap map = new HashMap();
        
        try {
            File f = new File(dir, "attrtable");
            if (f.exists()) {
                attrtable.loadFile(f);
            }
            attrtable.checkAndCreateAllTables(null,RootNamespace.getInstance());
            attrtable.dump();
        }
        catch (Exception e) {
            System.err.println("Aaaaa!");
            e.printStackTrace();
        }
    }
}

// 456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789
