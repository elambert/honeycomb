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



package com.sun.honeycomb.hook;

import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.NoSuchElementException;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Types;
import java.sql.ParameterMetaData;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import java.io.File;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.CanonicalStrings;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.StringList;
import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.ArchiveException;

import com.sun.honeycomb.emd.MetadataInterface;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.EMDCookie;

import com.sun.honeycomb.emd.MetadataClient.QueryResult;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;
import com.sun.honeycomb.emd.cache.CacheUtils;

import com.sun.honeycomb.emd.parsers.QueryNode;
import com.sun.honeycomb.emd.parsers.QueryParser;
import com.sun.honeycomb.emd.parsers.ParseException;
import com.sun.honeycomb.emd.parsers.TokenMgrError;

import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.common.QueryMap;
import com.sun.honeycomb.emd.common.EMDException;

import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.Table;
import com.sun.honeycomb.emd.config.Column;
import com.sun.honeycomb.emd.config.SessionEncoding;

import com.sun.honeycomb.emd.remote.MDOutputStream;

import com.sun.honeycomb.admin.mgmt.server.MgmtServerIntf;
import com.sun.honeycomb.admin.mgmt.server.MgmtServer;

import com.sun.honeycomb.hadb.HADBJdbc;
import com.sun.honeycomb.hadb.RetryableCode;
import com.sun.honeycomb.hadb.HADBMasterInterface;
import com.sun.honeycomb.hadb.NoConnectionException;
import com.sun.honeycomb.hadb.AttributeTable;
import com.sun.honeycomb.hadb.DatabaseConverter;
import com.sun.honeycomb.hadb.MasterService;

import com.sun.honeycomb.hadb.convert.QueryConvert;
import com.sun.honeycomb.hadb.convert.ConvertConstants;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;

import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.disks.Disk;

public class HADBHook
    implements MetadataInterface, CacheClientInterface, ConvertConstants {

    private static final Logger LOG = Logger.getLogger(HADBHook.class.getName());

    private static final int HADB_MAX_TABLE_ROW = 8080;
    private static final int HADB_MAX_INDEX_KEY = 1024;
    private static final int HADB_ROW_OVERHEAD = 100;
    private static final int HADB_MAX_STRLEN = (HADB_MAX_TABLE_ROW - 
                                                HADB_ROW_OVERHEAD - 2);

    // When running via main(), it does everything but talk to HADB by
    // setting this flag to false
    private static boolean useHADB = true;

    static final int QUERY_INTEGRITY_CHECK_INTERVAL = 1000*5; // 5 seconds
    static volatile long lastCheckTime;
    static volatile long queryIntegrityTime;

    public HADBHook() {
    }
    
    public String getCacheId() {
        return(CacheClientInterface.EXTENDED_CACHE);
    }

    public String getHTMLDescription() {
        return("A key/value cache implementation that relies on <b>Java High Availability Database</b>");
    }

    public boolean isRunning() {
        return HADBJdbc.getInstance().isRunning();
    }

    ///Emulator hook to initialize the extended cache database
        public void inithook() 
            throws EMDException {
            //The following is the magic that makes the HADB Emulator
            // create all the tables that it needs.  It makes the emulator
            // call the "MasterServices" version of load().
            String jdbcURL = System.getProperty(HADBJdbc.MD_HADB_JDBC_URL_PROPERTY);
            if (jdbcURL != null) {
                try {
                    HADBJdbc.getInstanceWithUrl(jdbcURL);
                    AttributeTable.activateSchema();

                    // Test the database converter by running it in the Emulator
                    if (DatabaseConverter.isConvertInProgress()) {
                        DatabaseConverter dbc = DatabaseConverter.resumeConvert();
                        while (dbc.waitForConvert()) {
                            // do nothing
                        }
                        dbc = null;
                    }
                


                } catch (Exception e) {
                    LOG.log(Level.SEVERE,
                            "Emulator creation of AttributeTable failed",
                            e);
                    throw new EMDException("Emulator creation of"+
                                           " attribute table failed",
                                           e);
                } // try
            } // if
        } // inithook

    public long getQueryIntegrityTime() {

        long now = System.currentTimeMillis();

        if (now - lastCheckTime > QUERY_INTEGRITY_CHECK_INTERVAL) {
            synchronized (this) {
                if (now - lastCheckTime > QUERY_INTEGRITY_CHECK_INTERVAL) {
                    lastCheckTime = now;
                    queryIntegrityTime = MgmtServer.getQueryIntegrityTime();
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("See new queryIntegrityTime="+queryIntegrityTime);
                    }
                } // if
            } // synchronized
        } // if
        long queryTime = queryIntegrityTime;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Using queryIntegrityTime="+queryIntegrityTime);
        }
        return queryTime;
    }

    public MetadataClient.QueryResult queryPlus(String cacheId,
                                                String query,
                                                ArrayList attributes,
                                                Cookie _cookie,
                                                int maxResults,
                                                int timeout,
                                                boolean forceResults,
                                                boolean abortOnFailure,
                                                Object[] boundParameters,
                                                MDOutputStream outputStream) 
        throws EMDException {

        // Sanity checks
        if ((_cookie != null)
            && (maxResults == -1)) {
            // Has to specify an offset AND a row count
            throw new EMDException("Invalid argument : when " +
                                   "using cookies, you have to " +
                                   "specify the number of " +
                                   "entries to return");
        }

        // Cookie management
        EMDCookie cookie = (EMDCookie)_cookie;
        NewObjectIdentifier lastOid = null;
        int toBeSkipped = 0;
        if (cookie != null) {
            query = cookie.getQuery();
            lastOid = cookie.getLastOid();
            toBeSkipped = cookie.getToBeSkipped();
            boundParameters = cookie.getBoundParameters();
            attributes = cookie.getAttributes();
        }

        if (query == null) {
            throw new EMDException("Query cannot be null");
        }

        //Make sure the selected attributes list, if it exists,
        // always contains system.object_id
        if (attributes != null && ! attributes.contains(OID_ATTRIBUTE)) 
            attributes.add(OID_ATTRIBUTE);

        // Parse the tree
        QueryNode parsedQuery = null;

        try {
            parsedQuery = QueryParser.parse(query);
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


        // Construct the SQL query
        StringBuffer SQLQuery = new StringBuffer();
        QueryConvert converter = new QueryConvert(parsedQuery,
                                                  lastOid,
                                                  attributes,
                                                  boundParameters,
                                                  forceResults);

        converter.convert(SQLQuery);
        List literals = converter.getLiterals();
        
        Connection conn = null;
        PreparedStatement statement = null;
        MetadataClient.QueryResult result = new MetadataClient.QueryResult();

        //Record the query integrity time for this query chunk
        // "query integrity time" is defined relative to the HADB db
        // create time, so only HADBHook sets it.  Other kinds of
        // queries (e.g. emulator, syscache) would have to get their
        // own version of query integrity time. 
        result.queryIntegrityTime = getQueryIntegrityTime();

        if (outputStream == null) {
            result.results = new ArrayList();
        }
        
        String[] atts = null;
        if (attributes != null) {
            atts = new String[attributes.size()];
            attributes.toArray(atts);
        }
        
        try {
            conn = HADBJdbc.getInstance().getConnection();
            statement = conn.prepareStatement(SQLQuery.toString());
            if (maxResults != -1) {
                statement.setMaxRows(maxResults+toBeSkipped);
            }
            LOG.fine("query \"" + SQLQuery + "\" : " +
                     CanonicalStrings.literalsToString(literals));

            long startTime = System.currentTimeMillis();
            ResultSet results =
                RetryableCode.retryExecutePreparedQuery(statement,literals);
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.fine("instr " + elapsed + "ms for query \"" + SQLQuery + "\" : " +
                     CanonicalStrings.literalsToString(literals));

            int nbReturned = -toBeSkipped;
            int maxReturned = maxResults!=-1 ? maxResults : Integer.MAX_VALUE;
            NewObjectIdentifier oid = null;
            
            while (nbReturned<maxReturned) {
                if (!results.next()) {
                    // force us to not send cookie if out of results.
                    LOG.fine("no more results for this query -- no cookie");
                    oid = null;
                    if (outputStream != null) {
                        // this is overkill, but doesn't hurt anything.
                        outputStream.clearLastObject();
                    }
                    break;
                }
                    
                if (nbReturned < 0) {
                    nbReturned++;
                    continue;
                }

                oid = NewObjectIdentifier.readFromBytes(results.getBytes(1));
                QueryMap attValues = null;
                    
                if (attributes != null) {
                    Object[] values = new Object[attributes.size()];
                    int resultSetIndex = 2;
                    int i;
                    for (i=0; i<values.length; i++) {
                        String attrName = (String)attributes.get(i);
                        // We know here that the attributes are in order
                        //  in the select clause
                        if (attrName.equals(OID_ATTRIBUTE)) {
                            // queryPlus returns the external OID
                            values[i] = oid.toExternalObjectID();
                        } else {
                            values[i] = results.getObject(resultSetIndex);
                            if (!forceResults && results.wasNull()) {
                                //Behave like INNER JOIN for now and ignore
                                // this object!
                                break;
                            }
                            resultSetIndex++;
                        }
                    }
                    if (!forceResults && i < values.length) {
                        //Behave like INNER JOIN for now and ignore
                        // this object!
                        continue;
                    }
                    attValues = new QueryMap(atts, values);
                }
                    
                if (outputStream != null) {
                    outputStream.sendObject(new MDHit(oid, attValues));
                } else {
                    result.results.add(new MDHit(oid, attValues));
                }
                nbReturned++;
            }

            if ((result != null) && (oid != null)) {
                result.cookie = new EMDCookie(oid,
                                              query,
                                              0,
                                              boundParameters,
                                              attributes);
            }
            
        } catch (SQLException e) {
            String msg;
            String queryString=SQLQuery.toString();
            if (e.getErrorCode() == 11701) {
                // HADB-E-11701: Table XXX not found
                if (LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO,
                            "The following query ["+queryString+"]\n\t"+
                            "referenced a table that is not there.\n\t"+
                            "This is a rare case that can happen if the "+
                            "HADB database is wiped\n\t"+
                            "without resetting all "+
                            "JVMs to get the new schema.\n\t"+
                            "We will generate an error for this request "+
                            "but then automatically recover.");
                }
                AttributeTable.resetSchema();
                msg = "transient failure";
            } else {
                msg = "failed";
            }

            EMDException newe = 
                new EMDException("SQL query "+msg+" ["+
                                 queryString + "]:["+
                                 e.getMessage()+"]:["+
                                 CanonicalStrings.literalsToString(literals)+
                                 "]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (statement != null) {
                try { statement.close(); } catch (SQLException e) {
                    LOG.log(Level.WARNING,"Unable to close Query Plus Prepared Statement." + e);
                }
                statement = null;
            }
            if (conn != null) {
                HADBJdbc.getInstance().freeConnection(conn);
                conn = null;
            }
        }

        return(result);
    }
    

    public Cookie querySeek(String query,
                            int index,
                            Object[] boundParameters,
                            ArrayList attributes) {
        return new EMDCookie(null, query, index, boundParameters, 
                             attributes);
    }
    
    public MetadataClient.SelectUniqueResult selectUnique(String cacheId,
                                                          String query,
                                                          String attribute,
                                                          Cookie _cookie,
                                                          int maxResults,
                                                          int timeout,
                                                          boolean forceResults,
                                                          Object[] boundParameters,
                                                          MDOutputStream outputStream) 
        throws EMDException {

        // Sanity checks
        if ((_cookie != null)
            && (maxResults == -1)) {
            // Has to specify an offset AND a row count
            throw new EMDException("Invalid argument : when " +
                                   "using cookies, you have to " +
                                   "specify the number of " +
                                   "entries to return");
        }

        // Cookie management
        EMDCookie cookie = (EMDCookie)_cookie;
        String lastAttribute = null;
        int toBeSkipped = 0;

        if (cookie != null) {
            query = cookie.getQuery();
            lastAttribute = cookie.getLastAttribute();
            toBeSkipped = cookie.getToBeSkipped();
            attribute = cookie.getAttribute();
            boundParameters = cookie.getBoundParameters();
        }

        if (attribute == null) {
            throw new EMDException("Attribute cannot be null");
        }

        MetadataClient.SelectUniqueResult result = null;

        // Parse the tree
        QueryNode parsedQuery = null;

        if (query != null) {
            LOG.log(Level.FINE, "Incoming query string: [{0}]",query);
            try {
                parsedQuery = QueryParser.parse(query);
            } catch (ParseException e) {
                EMDException newe = new EMDException("Failed to parse the query ["+
                                                     e.getMessage()+"]");
                newe.initCause(e);
                throw newe;
            }
        }

        int resultType = 0;

        try {
            Field f = RootNamespace.getInstance().resolveField(attribute);
            if (f == null) {
                throw new EMDException("Couldn't resolve field ["+attribute+"]");
            }
            resultType = f.getType();
        } catch (NoSuchElementException e) {
            throw new EMDException(e.getMessage());
        }
        
        // Construct the SQL query
        StringBuffer SQLQuery = new StringBuffer();
        QueryConvert converter = new QueryConvert(attribute, 
                                                  parsedQuery, 
                                                  lastAttribute,
                                                  boundParameters);
        converter.convert(SQLQuery);
        List literals = converter.getLiterals();

        Connection conn = null;
        PreparedStatement statement = null;
        ArrayList resultList = null;
        if (outputStream == null) {
            resultList = new ArrayList();
        }

        String singleResult = null;

        try {
            conn = HADBJdbc.getInstance().getConnection();
            statement = conn.prepareStatement(SQLQuery.toString());
            if (maxResults != -1) {
                statement.setMaxRows(maxResults+toBeSkipped);
            }

            long startTime = System.currentTimeMillis();
            ResultSet results =
                RetryableCode.retryExecutePreparedQuery(statement,literals);
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.fine("instr " + elapsed + "ms for query \"" + SQLQuery + "\" : " +
                     CanonicalStrings.literalsToString(literals));

            int nbReturned = -toBeSkipped;
            int maxReturned = maxResults!=-1 ? maxResults : Integer.MAX_VALUE;
            
            while ((results.next()) && (nbReturned<maxReturned)) {
                Object value = results.getObject(1);
                singleResult = CanonicalStrings.encode(value,resultType);
                if (results.wasNull()) {
                    // Behave like INNER JOIN for now
                    //  and ignore objects with fields that are null!
                } else {
                    //OK to proceed.  Object has a value.
                    if (outputStream != null) {
                        outputStream.sendObject(singleResult);
                    } else {
                        resultList.add(singleResult);
                    }
                    nbReturned++;
                }
            }

        } catch (SQLException e) {
            EMDException newe = new EMDException("SQL selectUnique failed [" + SQLQuery.toString() + "]:["+
                                                 e.getMessage()+"]:[" +
                                                 CanonicalStrings.literalsToString(literals)+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (statement != null) {
                try { statement.close(); } catch (SQLException e) {
                    LOG.log(Level.WARNING, "Unable to close Select Unique Plus Prepared Statement." + e);
	       	}
                statement = null;
            }
            if (conn != null) {
                HADBJdbc.getInstance().freeConnection(conn);
                conn = null;
            }
        }

        if (outputStream == null) {
            result = new MetadataClient.SelectUniqueResult();
            result.results = new StringList(resultList);
            if (singleResult != null) {
                result.cookie = new EMDCookie(singleResult,
                                              query,
                                              attribute,
                                              0,
                                              boundParameters);
            }
            return(result);
        } else {
            return(null);
        }
    }
    
    public Cookie selectUniqueSeek(String query,
                                   String attribute,
                                   int index,
                                   Object[] boundParameters) {
        return new EMDCookie(null, query, attribute, index, boundParameters);
    }


    /**
     * Construct a SQL string to insert the row represented by oid and
     * attributes into the given table.
     * Returns a list of literal values that can be passed to
     * RetryableCode.
     */
    private static List getLiteralsForInsert(Table table,
                                             NewObjectIdentifier oid, 
                                             Map attributes) 
        throws EMDException {

        ArrayList literals = new ArrayList();

        ArrayList tableColumns = new ArrayList();
        table.getColumns(tableColumns);

        // OID always has to be first
        literals.add(oid.getDataBytes());       //internal form
        
        for (int i = 0; i < tableColumns.size(); i++) {
            Column col = (Column) tableColumns.get(i);
            Field field = col.getField();
            String key = col.getFieldName();
            Object value = attributes.get(key);
            if (value instanceof String) {
                // When storing a string value into a non-string field,
                //  use the Canonical String decoding rather than the
                //  HADB decoding
                value = CanonicalStrings.decode((String)value, field);
            }
            literals.add(value);
        }

        return literals;
    }

    /**
     * Given a map of attr. names -> values, construct a table that
     * maps "partitioned attrs" table name to a list of values that
     * should be inserted into that table
     */
    private Set getTables(Map attributes, RootNamespace rootNS)
        throws EMDException{
        Set tables = new HashSet();
        AttributeTable attrTable = AttributeTable.getInstance();

        Iterator ite = attributes.keySet().iterator();

        while (ite.hasNext()) {
            String key = (String)ite.next();

            // Skip OID which is special
            if (key.equals(OID_ATTRIBUTE))
                continue;

            Field field = rootNS.resolveField(key);
            if (field == null || !field.isQueryable())
                continue;
            Table table  = field.getTableColumn().getTable();

            tables.add(table);
        }

        return tables;
    }

    /** Add a row to table "tableName" using values from attributes */
    private static void addRow(Connection conn, NewObjectIdentifier oid,
                               Table table, Map attributes)
        throws SQLException, EMDException {

        if (!useHADB)
            return;

        addRowUsingSQL(conn, oid, table, attributes);
    }

    // WARNING, clients should not use the boolean return value
    // to determine whether the op has succeeded, instead, it is
    // used by some clients to determine whether HABD is up or not.
    public boolean setMetadata(String cacheId,
                               Map items) {

        if (items == null) {
            LOG.severe("Invalid argument in setMetadata [null]");
            return false;
        }
        SortedMap sortedItems = new TreeMap(items);
        for (Iterator i = sortedItems.keySet().iterator(); i.hasNext(); ) {
            NewObjectIdentifier oid = (NewObjectIdentifier) i.next();
            Map attributes = (Map)sortedItems.get(oid);
            if (setMetadata(cacheId,oid,attributes))
                return true;
        }
        return false;
    }

    public boolean setMetadata(String cacheId,
                               NewObjectIdentifier oid,
                               Object argument) {
        if (argument == null) {
            LOG.severe("Invalid argument in setMetadata [null]");
            return false;
        }
        
        Map attributes = (Map)argument;

        if (LOG.isLoggable(Level.INFO)) {
            StringBuffer msg = new StringBuffer();
            msg.append(oid.toHexString());
            msg.append(" <- {");
            for (Iterator i = attributes.keySet().iterator(); i.hasNext(); ) {
                String key = (String) i.next();
                msg.append(' ').append(key).append("=");
                msg.append(StringUtil.image(attributes.get(key)));
            }
            LOG.info(msg.append(" }").toString());
        }
        
        Connection conn = null;
        try {

            // Get all attribute tables that will be involved
            Set tables = getTables(attributes, RootNamespace.getInstance());
            
            if (useHADB)
                conn = HADBJdbc.getInstance().getConnection();
            
            // We need to commit the system table last, because
            // we use autocommit
            Table systemTable = null;

            RootNamespace rootNS = RootNamespace.getInstance();
            Field field = rootNS.resolveField(INSERT_COMMIT_PROPERTY);
            Table commitTable = field.getTableColumn().getTable();
            String commitTableName =
                AttributeTable.getInstance().getTableName(commitTable);
         
            Iterator ite = tables.iterator();
            while (ite.hasNext()) {
                Table table = (Table)ite.next();
                String tableName =
                    AttributeTable.getInstance().getTableName(table);
         
                if (tableName.equalsIgnoreCase(commitTableName))
                    systemTable = table;
                else
                    addRow(conn, oid, table, attributes);
                
            }

            if (systemTable != null)
                addRow(conn, oid, systemTable, attributes);
            
        } catch (SQLException e) {
        
            LOG.warning("setMetadata failed for oid ["+
                        oid.toHexString()+"] - ["+
                        e.getMessage()+"]");
            return false;
        } catch (NoConnectionException e) {
            LOG.warning("setMetadata failed for oid ["+
                        oid.toHexString()+"] - ["+
                        e.getMessage()+"]");
            return false;
        } catch (EMDException e) {
            LOG.warning("setMetadata failed for oid ["+
                        oid.toHexString()+"] - ["+
                        e.getMessage()+"]");
            return false;
        } finally {
            if (conn != null) {
                HADBJdbc.getInstance().freeConnection(conn);
                conn = null;
            }
        }
        return true;
    }

    /** Construct SQL and execute it */
    private static void addRowUsingSQL(Connection conn,
                                       NewObjectIdentifier oid,
                                       Table table,
                                       Map attributes)
        throws SQLException, EMDException {

        long startTime = System.currentTimeMillis();
        List literals = getLiteralsForInsert(table, oid, attributes);
        String ins = AttributeTable.getInstance().makeInsertStatement(table);
        PreparedStatement stmt = conn.prepareStatement(ins);
        if (LOG.isLoggable(Level.FINE)) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.fine("instr " + elapsed + "ms to prepare \"" + ins + "\"");
        }

        try {
            AttributeTable.addRowPreparedStatement(stmt,literals,ins);
        } finally {
            try { stmt.close(); } catch (SQLException e) { 
                LOG.log(Level.WARNING,"Unable to close Add Row Prepared Statement." + e);
            }
            stmt = null;
        }
    }


    private boolean cacheContains(NewObjectIdentifier oid) 
        throws EMDException {
        
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet results = null;
        
        Field systemField = RootNamespace.getInstance().resolveField(INSERT_COMMIT_PROPERTY);
        String tableName = AttributeTable.getInstance().getTableNameForField(systemField);
        List literals = new ArrayList();

        StringBuffer sb = new StringBuffer();
        sb.append("select ");
        sb.append(FIELD_OBJECTID);
        sb.append(" from ");
        sb.append(tableName);
        sb.append(" where ");
        sb.append(FIELD_OBJECTID);
        sb.append("=?");
        literals.add(oid.getDataBytes());

        boolean result = false;
        
        try {
            connection = HADBJdbc.getInstance().getConnection();
            statement = connection.prepareStatement(sb.toString());

            results = RetryableCode.retryExecutePreparedQuery(statement,literals);
            result = results.next();

        } catch (SQLException e) {
            EMDException newe = new EMDException("Failed to check if cache contains OID ["+
                                                 oid.toHexString()+"]" + "message is [" + e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (results != null)
                try { results.close(); } catch (SQLException e) {}
            if (statement != null) {
                try { statement.close(); } catch (SQLException e) {
                    LOG.log(Level.WARNING,"Unable to close cache contains Prepared Statement." + e);
	       	}
                statement = null;
            }
            if (connection != null) {
                HADBJdbc.getInstance().freeConnection(connection);
                connection = null;
            }
        }
        
        return(result);
    }
    
    public void setMetadata(SystemMetadata systemMD,
                            byte[] MDField,
                            Disk disk) {

        LOG.severe("OBSOLETE method"+
                   " setMetadata (part of recovery) has been called for oid "+systemMD.getOID()
                   +" on disk ["+disk+"]");
        throw new RuntimeException("disk-style setMetadata called in HADBHook?!");
    }
    
    private void deleteFromTable(Connection conn,
                                 String table,
                                 byte[] oidBytes)
        throws SQLException {
        
        List literals = new ArrayList();
        PreparedStatement statement = null;

        StringBuffer sql = new StringBuffer();
        sql.append("delete from ");
        sql.append(table);
        sql.append(" where objectid =?");
        literals.add(oidBytes);
        
        try {
            statement = conn.prepareStatement(sql.toString());
            
            RetryableCode.retryExecutePrepared(statement,literals);
        } finally {
            if (statement != null) {
                try { statement.close(); } catch (SQLException e) {
                    LOG.log(Level.WARNING,"Unable to close cache contains Prepared Statement." + e);
                }
            }
        }
    }
    
    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId) 
        throws EMDException {
        
        Connection connection = null;

        try {

            RootNamespace rootNS = RootNamespace.getInstance();
            Field field = rootNS.resolveField(INSERT_COMMIT_PROPERTY);
            Table commitTable = field.getTableColumn().getTable();
            String commitTableName = 
                AttributeTable.getInstance().getTableName(commitTable);

            // Do a remove op for each active table in the system
            Table[] tables = rootNS.getTables();

            connection = HADBJdbc.getInstance().getConnection();

            byte[] oidBytes = oid.getDataBytes();

            // Remove OID from the system table first.   That way if
            // the delete operation fails later but before doing the
            // complete removal, PopulateExtCache will be able to
            // repopulate the data item or finish deleting the data
            // item, as the case may be.

            deleteFromTable(connection,
                            commitTableName, 
                            oidBytes);
            for (int i=0; i<tables.length; i++) {
                String tableName = 
                    AttributeTable.getInstance().getTableName(tables[i]);
                if (! tableName.equalsIgnoreCase(commitTableName)) {
                    deleteFromTable(connection, 
                                    tableName, 
                                    oidBytes);
                } // if
            } // for

        } catch (SQLException e) {
            EMDException newe = new EMDException("Removal of ["+
                                                 oid.toHexString()+"] failed. ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (connection != null) {
                HADBJdbc.getInstance().freeConnection(connection);
            }
        }
    }

    private HADBMasterInterface getAPI()
        throws EMDException {

        ManagedService.ProxyObject proxy 
            = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE,
                                      "HADBMaster");
        if ((proxy == null) || (!proxy.isReady())) {
            throw new EMDException("Failed to contact the database to perform the configuration update ["+proxy+"]");
        }
        
        Object obj = proxy.getAPI();
        if (!(obj instanceof HADBMasterInterface)) {
            String s = (obj==null) ? "null" : obj.getClass().getName();
            throw new EMDException("Failed to contact the database to perform the configuration update [Invalid connection handle] ["+s+"]");
        }
        
        return( (HADBMasterInterface)proxy.getAPI() );
    }

    public void clearFailure()
        throws EMDException  {

        HADBMasterInterface HADBmaster = getAPI();

        try {
            HADBmaster.clearFailure();
        } catch (ManagedServiceException e) {
            EMDException newe = new EMDException(e.getMessage());
            newe.initCause(e);
            throw newe;
        }
    }

    public void updateSchema()
        throws EMDException {

        HADBMasterInterface HADBmaster = getAPI();

        try {
            HADBmaster.updateSchema();
        } catch (HADBMasterInterface.HADBServiceException e) {
            LOG.log(Level.WARNING, "Couldn't update schema", e);
            throw new EMDException(e);
        } catch (ManagedServiceException e) {
            LOG.log(Level.WARNING, "Couldn't invoke HADBMaster RMI", e);
            throw new EMDException(e);
        }
    }
    public int getCacheStatus()
        throws EMDException {

        HADBMasterInterface HADBmaster = getAPI();

        int result = -1;
        try {
            result = HADBmaster.getCacheStatus();
        } catch (ManagedServiceException e) {
            EMDException newe = new EMDException(e.getMessage());
            newe.initCause(e);
            throw newe;
        }
        return result;
    }

    public String getEMDCacheStatus()
        throws EMDException {

        HADBMasterInterface HADBmaster = getAPI();

        String result = null;
        try {
            result = HADBmaster.getEMDCacheStatus();
        } catch (ManagedServiceException e) {
            EMDException newe = new EMDException(e.getMessage());
            newe.initCause(e);
            throw newe;
        }
        return result;
    }

    public long getLastCreateTime() {

        HADBMasterInterface.Proxy proxy = MasterService.getMasterProxy();

        if (proxy == null) {
            LOG.fine("Couldn't get MasterService proxy to get last HADB create time");
            return 0;
        }
        long lastCreateTime = proxy.getLastCreateTime();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("getLastCreateTime returning "+lastCreateTime);
        }
        return lastCreateTime;
    }

    /**********************************************************************
     *
     * CacheClientInterface APIS
     *
     **********************************************************************/

    public void generateMetadataStream(CacheRecord mdObject,
                                       OutputStream output) 
        throws EMDException {
        ExtendedCacheEntry attributes = (ExtendedCacheEntry)mdObject;
        
        if (attributes == null) {
            // This an empty map to generate XML with an empty content
            attributes = new ExtendedCacheEntry();
        }

        attributes.remove(SystemMetadata.FIELD_NAMESPACE + 
                          "." + SystemMetadata.FIELD_OBJECTID);
        try {
            NameValueXML.createXML(attributes, output, SessionEncoding.getEncoding());
        } catch (IOException e) {
            EMDException newe = new EMDException("Couldn't generate extended cache metadata");
            newe.initCause(e);
            throw newe;
        }
    }

    public CacheRecord parseMetadata(InputStream in, 
                                     long mdLength, 
                                     Encoding encoder)
        throws EMDException {

        ExtendedCacheEntry result = new ExtendedCacheEntry();
        Map emd = CacheUtils.parseMetadata(in, mdLength, encoder);
        if (emd != null) {
            result.putAll(emd);
        }

        return result;
    }

    public CacheRecord generateMetadataObject(NewObjectIdentifier oid) 
        throws EMDException {
        Map attributes = null;
        
        try {
            attributes = 
                CacheUtils.retrieveMetadata(null, oid);
        } catch (ArchiveException e) {
            EMDException newe = new EMDException("Failed to retrieve the metadata ["+e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
        if (!attributes.containsKey(SystemMetadata.FIELD_NAMESPACE + 
                                    "." + SystemMetadata.FIELD_OBJECTID)) {
            attributes.put(SystemMetadata.FIELD_NAMESPACE + 
                           "." + SystemMetadata.FIELD_OBJECTID,
                           oid.toHexString());
        }
        
        ExtendedCacheEntry result = new ExtendedCacheEntry();
        result.putAll(attributes);
        
        return(result);
    }

    public int getMetadataLayoutMapId(CacheRecord argument,
                                      int nbOfPartitions) {
        return(-1);
    }
    
    public int[] layoutMapIdsToQuery(String query,
                                     int nbOfPartitions) {
        return(null);
    }
    
    public void sanityCheck(CacheRecord argument)
        throws EMDException {
        ExtendedCacheEntry attributes = (ExtendedCacheEntry)argument;
        RootNamespace.getInstance().validate(attributes);
    }

    /**********************************************************************
     *
     * Compliance APIS. Needed to satisfy CacheInterface &
     * MetadataInterface but not used.
     *
     **********************************************************************/

    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold) {
	LOG.info("Legal holds are not stored in the extended cache");	
    }

    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold, Disk disk)  {
	LOG.info("Legal holds are not stored in the extended cache");
    }

    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold) {
	LOG.info("Legal holds are not stored in the extended cache");
    }

    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold, Disk disk) {
    	LOG.info("Legal holds are not stored in the extended cache");
    }


    public static void main(String[] args) 
    throws EMDException {
        useHADB = false;

        if (args.length < 1) {
            System.err.println("Expected at least one argument: <dir-name>");
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
            useHADB = true;
            if (!args[1].equalsIgnoreCase("HADB")) {
                jdbcurl = "jdbc:sun:hadb:"+args[1]+":15005";
                System.setProperty(HADBJdbc.MD_HADB_JDBC_URL_PROPERTY, jdbcurl);
            } else {
                jdbcurl = System.getProperty(HADBJdbc.MD_HADB_JDBC_URL_PROPERTY);
            }
        }

        AttributeTable attrtable = null;

        if (useHADB) {
            try {
                HADBJdbc.getInstanceWithUrl(jdbcurl);
                attrtable = AttributeTable.activateSchema();
            } catch (Exception e) {
                System.out.println("Operation failed " + e);
                e.printStackTrace();
                System.exit(1);
            }
            System.out.println("new AttributeTable succeeded.");
        }
        else
            attrtable = AttributeTable.getDebugInstance(dir);


        HashMap map = new HashMap();
        
        HADBHook me = new HADBHook();
          
        map.put("system.object_size", "42");
        
        NewObjectIdentifier oid = new NewObjectIdentifier(0, (byte)1, 0, null);
        map.put("system.object_id", oid.toHexString());
        map.put("system.object_ctime", Long.toString(System.currentTimeMillis()));
        map.put("ofoto.dir1", "1");
        map.put("ofoto.dir4", "2");
        map.put("ofoto.dir5", "42");
        map.put("ofoto.fname", "My Name");

        map.put("filesystem.mimetype","text/html");

        me.setMetadata("extended", oid, map);
        map.put("ofoto.dir2", "2000001");
        NewObjectIdentifier oid2 = new NewObjectIdentifier(0, (byte)1, 0, null);
        me.setMetadata("extended", oid2, map);
        try {
            if (! me.cacheContains(oid)) {
                LOG.severe("Failed to insert the oid: "+oid);
            }
            ArrayList selectAttributes = new ArrayList();
            selectAttributes.add("ofoto.dir4");
            selectAttributes.add("ofoto.dir3");
            selectAttributes.add("ofoto.fname");

            MetadataClient.QueryResult result = 
                me.queryPlus("extended", "ofoto.dir4='2'", selectAttributes,
                             null, 100,-1,false,false,null,null);
            LOG.info("Query succeeded with "+result.results.size()+" results");

            me.removeMetadata(oid,"extended");
            LOG.info("Remove metadata "+oid.toHexString()+" succeeded.");

            result =
                me.queryPlus("extended", "ofoto.dir4='2'", selectAttributes,
                             null, 100,-1,false,false,null,null);
            LOG.info("Query succeeded with "+result.results.size()+" results");

        } catch (EMDException e) {
            LOG.log(Level.INFO,"Received EMDConfigException: ",e);
            throw new RuntimeException(e);
        }//catch
    }//main

    public QueryResult queryPlus(String cacheId, String query,
            ArrayList attributes, Cookie _cookie, int maxResults, int timeout,
            boolean forceResults, Object[] boundParameters,
            MDOutputStream outputStream, Disk disk) throws EMDException {
        throw new RuntimeException("Unsupported method on extended metadata.");
    }
}
