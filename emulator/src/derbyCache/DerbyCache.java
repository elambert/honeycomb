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



import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;

import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.CanonicalStrings;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.Encoding;

import com.sun.honeycomb.emd.Derby;
import com.sun.honeycomb.emd.DerbyAttributes;
import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.CacheUtils;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.SessionEncoding;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.Table;
import com.sun.honeycomb.emd.config.Column;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import com.sun.honeycomb.emd.parsers.QueryParser;
import com.sun.honeycomb.emd.parsers.QueryNode;
import com.sun.honeycomb.emd.parsers.ParseException;
import com.sun.honeycomb.emd.parsers.TokenMgrError;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.QueryMap;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.disks.Disk;


public class DerbyCache
    implements CacheClientInterface, CacheInterface {

    public static final int DEFAULT_STRING_LENGTH = 512;

    public static final int DERBY_MAX_STRLEN = 7980;

    public static final String OID_ATTRIBUTE = "system.object_id";

    public static final String OID_SQL_TYPE = 
        "varchar("+Field.OBJECTID_SIZE+") for bit data";

    private static final Logger LOG = Logger.getLogger(DerbyCache.class.getName());

    // use a synchronized map.
    private static final Map tableMap = new Hashtable();

    private Disk registeredDisk;

    public DerbyCache() {
        registeredDisk = null;
    }

    /*
     * CacheClientInterface interface
     */

    public String getCacheId() {
        return(CacheClientInterface.EXTENDED_CACHE);
    }

    public String getHTMLDescription() {
        return("A key/value cache implementation that relies on <b>Derby</b>");
    }

    public boolean isRunning() {
        return true;
    }

    public void generateMetadataStream(CacheRecord mdObject,
                                       OutputStream output) 
        throws EMDException {
        ExtendedCacheEntry attributes = (ExtendedCacheEntry)mdObject;
        
        if (attributes == null) {
            // This an empty map to generate XML with an empty content
            attributes = new ExtendedCacheEntry();
        }
            
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

    /*
     * CacheInterface interface
     */

    public static final String DERBY_EXTENDED_DB = "extended";

    public static final String DERBY_STRING_TABLE = "stringTable";
    public static final String DERBY_LONG_TABLE = "longTable";
    public static final String DERBY_DOUBLE_TABLE = "doubleTable";
    
    public static final String FIELD_OBJECTID = "oid";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_VALUE = "value";


    public void registerDisk(String MDPath,
                             Disk disk)
        throws EMDException {
        if (registeredDisk != null) {
            LOG.warning("Being asked to register more than 1 disk");
            unregisterDisk(registeredDisk);
        }
        
        registeredDisk = disk;
        checkTables();
    }

    public void unregisterDisk(Disk disk)
        throws EMDException {
        if ((disk == null) || (disk != registeredDisk)) {
            throw new EMDException("Cannot unregister disk ["+
                                   disk+"]");
        }
        Derby.getInstance().stop(DERBY_EXTENDED_DB);
        registeredDisk = null;
    }

    public boolean isRegistered(Disk disk) {
        if ((disk == null) || (disk != registeredDisk)) {
            return(false);
        }
        return(true);
    }

    public void setMetadata(NewObjectIdentifier oid,
			    Object argument,
			    Disk disk)
        throws EMDException {

        Map attributes = (Map)argument;

        if (LOG.isLoggable(Level.INFO)) {
            StringBuffer msg = new StringBuffer();
            msg.append(oid.toHexString());
            msg.append(" <- {");
            for (Iterator i = attributes.keySet().iterator(); i.hasNext(); ) {
                String key = (String) i.next();
                msg.append(' ').append(key).append("='");
                msg.append(attributes.get(key).toString());
                msg.append("'");
            }
            LOG.info(msg.append(" }").toString());
        }
        

        // Get all attribute tables that will be involved
        Set tables = getTables(attributes);
	
        // No need to commit the system table last, because
        // all the inserts are within a transaction
        Iterator ite = tables.iterator();
        while (ite.hasNext()) {
            Table table = (Table)ite.next();
            addRow(oid, table, attributes);
        }

    }
    /**
     * Given a map of attr. names -> values, construct a table that
     * maps "partitioned attrs" table name to a list of values that
     * should be inserted into that table
     */
    private static Set getTables(Map attributes)
        throws EMDException{
        Set tables = new HashSet();

        Iterator ite = attributes.keySet().iterator();

        while (ite.hasNext()) {
            String key = (String)ite.next();

            // Skip OID which is special
            if (key.equals(OID_ATTRIBUTE))
                continue;

            Field field = RootNamespace.getInstance().resolveField(key);
            if (field == null || !field.isQueryable())
                continue;
	    Table table  = field.getTableColumn().getTable();

            tables.add(table);
        }

        return tables;
    }

    /** Add a row to table "tableName" using values from attributes */
    private static void addRow(NewObjectIdentifier oid,
			       Table table, 
			       Map attributes)
            throws EMDException {

        List literals = new ArrayList();
        String ins = makeStmt(table, oid, attributes,literals);
	LOG.info("insert row using sql: "+ins+ ": "+
                        CanonicalStrings.literalsToString(literals));

        Connection connection = null;
        PreparedStatement stmt = null;

        try {
            connection = Derby.getInstance().getConnection(DERBY_EXTENDED_DB);
            stmt = connection.prepareStatement(ins);

            RetryableCode.retryExecutePrepared(stmt,literals);
        } catch (SQLException sqle) {
                EMDException newe = 
		    new EMDException("addRow failed on statement: "+
				     ins+"] for oid ["+ oid+"] - ["+
				     sqle.getMessage()+"]");
		newe.initCause(sqle);
                throw newe;
        }
    }

    /**
     * Construct a SQL string to insert the row represented by oid and
     * attributes into the given table
     */
    private static String makeStmt(Table table,
                                   NewObjectIdentifier oid, 
                                   Map attributes,
                                   List literals)
        throws EMDException {
        String tableName = getTableName(table);
	    
        StringBuffer sb = new StringBuffer();
        ArrayList tableColumns = new ArrayList();
        table.getColumns(tableColumns);

        // For each column name, get the corresponding value from the Map
        // and construct the insert statement

        // OID always has to be first
        sb.append("insert into ").append(tableName);
        sb.append(" values (x'").append(oid.toHexString()).append("'");

        for (int i = 0; i < tableColumns.size(); i++) {
            Column col = (Column) tableColumns.get(i);
            String key = col.getFieldName();
            Object value = attributes.get(key);
            if (value instanceof String) {
                // When storing a string value into a non-string field,
                //  use the Canonical String decoding rather than the
                //  HADB decoding
                value = CanonicalStrings.decode((String)value, key);
            }

            sb.append(", ?");
            literals.add(value);
        }
        sb.append(")");

        return sb.toString();
    }

    public static String getTableName(Table table) {
        return assignUniqueTableName(table);
    }

    public static String getTableNameForField(Field field) {

        // FIXME:  I think we have to check for changes to the schema here
        Table table = field.getTableColumn().getTable();
        return getTableName(table);
    }

    private static String normalize(Object value, Field field) {
        if (value == null)
            return "null";

        // Needs more error checking here

        switch (field.getType()) {
        case Field.TYPE_DOUBLE:
        case Field.TYPE_BYTE:
	    return value.toString();
        case Field.TYPE_LONG:
            try {
                long v = Long.parseLong(value.toString());
                return Long.toString(v);
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Value " + value +
                                                   " not numeric");
            }

        case Field.TYPE_STRING:
            if (value instanceof String) {
		// We must right-truncate the string before passing it to Derby
		// FIXME: we don't have a way to let the application know
		//        that a metadata value was right-truncated.
		String strValue = (String) value;
                int len = field.getLength();
                if (len < 0)
                    len = DEFAULT_STRING_LENGTH;
		if (strValue.length() > len)
		    strValue = strValue.substring(0, len);
                return "'"+normalizeValue(strValue)+"'";
	    } else
                throw new IllegalArgumentException("Value " + value +
                                                   " is not a String");

        default:
            throw new RuntimeException("Unknown field type " + field.getType() + "for field " + field.toString());
        }
    }

    public void removeMetadata(NewObjectIdentifier _oid,
                               Disk disk)
        throws EMDException {
        
        byte[] oidBytes = _oid.getDataBytes();
        Connection connection = null;


        RootNamespace rootNS = RootNamespace.getInstance();
        Table[] tables = rootNS.getTables();

        try {
            connection = Derby.getInstance().getConnection(DERBY_EXTENDED_DB);
            for (int i=0; i<tables.length; i++) {
                String tableName = getTableName(tables[i]);
                deleteFromTable(connection, tableName, oidBytes);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE,
                    "Failed to remove oid ["+
                    ByteArrays.toHexString(oidBytes)+"] from the database ["+
                    e.getMessage()+"]",
                    e);
        } finally {
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }
    }

    private void deleteFromTable(Connection connection,
                                 String table,
                                 byte[] oidBytes) {
        List literals = new ArrayList();
        PreparedStatement statement = null;

        try {
            StringBuffer sb = new StringBuffer();
            sb.append("delete from ");
            sb.append(table);
            sb.append(" where objectid=?");
            literals.add(oidBytes);
            statement = connection.prepareStatement(sb.toString());
            
            RetryableCode.retryExecutePrepared(statement,literals);

        } catch (SQLException e) {
            LOG.log(Level.SEVERE,
                    "Failed to remove oid ["+
                    ByteArrays.toHexString(oidBytes)+"] from the database ["+
                    e.getMessage()+"] - ["+
                    e.getClass().getName()+"]",
                    e);
        } finally {
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
        }
    }

    public void queryPlus(MDOutputStream outputStream,
                          ArrayList disks,
                          String query,
                          ArrayList attributes,
                          EMDCookie cookie,
                          int maxResults, int timeout,
                          boolean forceResults,
                          Object[] boundParameters)
        throws EMDException {

        // Sanity checks
        if ((cookie != null)
            && (maxResults == -1)) {
            // Has to specify an offset AND a row count
            throw new EMDException("Invalid argument : when " +
                                   "using cookies, you have to " +
                                   "specify the number of " +
                                   "entries to return");
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

        LOG.log(Level.FINE, "Incoming query string: [{0}]",query);
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
                                                  (cookie == null ? null : cookie.getLastOid()),
                                                  attributes,
                                                  boundParameters);

        converter.convert(SQLQuery);
        List literals = converter.getLiterals();
        
        Connection conn = null;
        PreparedStatement statement = null;
        
        String[] atts = null;
        if (attributes != null) {
            atts = new String[attributes.size()];
            attributes.toArray(atts);
        }
        
        try {
            conn = Derby.getInstance().getConnection(DERBY_EXTENDED_DB);
            statement = conn.prepareStatement(SQLQuery.toString());
            if (maxResults != -1) {
                statement.setMaxRows(maxResults);
            }
            LOG.fine("query \"" + SQLQuery + "\" : " +
                     CanonicalStrings.literalsToString(literals));

            long startTime = System.currentTimeMillis();
            ResultSet results =
                RetryableCode.retryExecutePreparedQuery(statement,literals);
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.fine("instr " + elapsed + "ms for query \"" + SQLQuery + "\" : " +
                     CanonicalStrings.literalsToString(literals));

            int nbReturned = 0;
            int maxReturned = maxResults!=-1 ? maxResults : Integer.MAX_VALUE;
            NewObjectIdentifier oid = null;
            
            while (nbReturned<maxReturned) {
                if (! results.next()) {
                    LOG.fine("end of results for this query");
                    outputStream.clearLastObject();
                    break;
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
                    
                outputStream.sendObject(new MDHit(oid, attValues));
                nbReturned++;
            }

        } catch (SQLException e) {
            EMDException newe = new EMDException("SQL query failed [" + SQLQuery.toString() + "]:["+
                                                 e.getMessage()+"]:["+
                                                 CanonicalStrings.literalsToString(literals)+"]");
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
                try { conn.close(); } catch (SQLException e) { }
                conn = null;
            }
        }
    }
    
    public void selectUnique(MDOutputStream outputStream,
                             String query,
                             String attribute,
                             String lastAttribute,
                             int maxResults, int timeout,
                             boolean forceResults,
                             Object[] boundParameters)
        throws EMDException {
        if (attribute == null) {
            throw new EMDException("Attribute cannot be null");
        }

        // Parse the tree
        QueryNode parsedQuery = null;

        if (query != null) {
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

        Object singleResult = null;

        try {
            conn = Derby.getInstance().getConnection(DERBY_EXTENDED_DB);
            statement = conn.prepareStatement(SQLQuery.toString());
            if (maxResults != -1) {
                statement.setMaxRows(maxResults);
            }
            ResultSet results =
                RetryableCode.retryExecutePreparedQuery(statement,literals);

            int nbReturned = 0;
            int maxReturned = maxResults!=-1 ? maxResults : Integer.MAX_VALUE;
            
            while ((results.next()) && (nbReturned<maxReturned)) {
                Object value = results.getObject(1);
                singleResult = CanonicalStrings.encode(value,resultType);
                if (results.wasNull()) {
                    // Behave like INNER JOIN for now
                    //  and ignore objects with fields that are null!
                } else {
                    //OK to proceed.  Object has a value.
                    outputStream.sendObject(singleResult);
                    nbReturned++;
                }
            }

        } catch (SQLException e) {
            EMDException newe = new EMDException("SQL selectUnique failed [" + SQLQuery.toString() + "]:["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (statement != null) {
                try { statement.close(); } catch (SQLException e) { }
                statement = null;
            }
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { }
                conn = null;
            }
        }
    }

    private void checkTables()
        throws EMDException {

        Table [] tables = RootNamespace.getInstance().getTables();
        for (int i = 0; i < tables.length; i++) {
            createAttributeTable(tables[i]);
        }
    }
    /**
     * @param table the EMDConfig Table of interest
     */
    private void createAttributeTable(Table table)
        throws EMDException {

        DerbyAttributes attributes = new DerbyAttributes();
        attributes.add("objectid", OID_SQL_TYPE);

        ArrayList columns = new ArrayList();
        String tableName = assignUniqueTableName(table);

        table.getColumns(columns);
        for (int index = 0; index < columns.size(); index++){
            Column column = (Column)columns.get(index);

            // No column needed or allowed for object_id
            if (column.getFieldName().equals(OID_ATTRIBUTE)) {
                LOG.warning("system.object_id should NOT occur in any table definition!");
                continue;
            }
            attributes.add(column.getColumnName(),getSQLType(column));
        }
        Derby.getInstance().checkTable(DERBY_EXTENDED_DB,
                                       tableName,
                                       attributes);

    } // createAttributeTable

    static String assignUniqueTableName(Table table) {
        String name = table.getName();
        String tableName = (String)tableMap.get(table.getName());
        if (tableName != null) {
            LOG.info("Config table "+name+" already has Derby table name "+
                     tableName);
            return tableName;
        }

        String baseCandidate = null;
        //If table name contains any non-ASCII chars, 
        // just punt and use hashcode
        if (name.matches("\\p{Lower}(\\p{Lower}|\\p{Digit}|_)*")) {
            baseCandidate = "T_" + name;
        } else {
            baseCandidate = "T_" + Integer.toHexString(name.hashCode());
        }

        String candidate = baseCandidate.toUpperCase();
        LOG.info("Assigning config table "+name+" to new Derby table "+
                 candidate);
        tableMap.put(name, candidate);
    
        return(candidate);
    }

    private String getSQLType(Column column)
        throws EMDConfigException {
	return getSQLType(column.getField());
    }

    private String getSQLType(Field field)
        throws EMDConfigException {

        int type = field.getType();
        String typeString = null;
        int length;
        
        switch (type) {
        case Field.TYPE_LONG:
            typeString = "bigint";
            break;
                
        case Field.TYPE_DOUBLE:
            typeString = "double";
            break;

        case Field.TYPE_OBJECTID:
            typeString = OID_SQL_TYPE;
            break;

        case Field.TYPE_CHAR:
        case Field.TYPE_STRING:
            length = field.getLength();
            if (length <= 0) {
                length = DEFAULT_STRING_LENGTH;
            }
            typeString = "varchar(" + length + ")";
            break;
                
        case Field.TYPE_BINARY:
            length = field.getLength();
            if (length <= 0) {
                length = DEFAULT_STRING_LENGTH;
            }
            typeString = "varchar(" + length + ") for bit data";
            break;

        case Field.TYPE_DATE:
            typeString = "date";
            break;

        case Field.TYPE_TIME:
            typeString = "time";
            break;

        case Field.TYPE_TIMESTAMP:
            typeString = "timestamp";
            break;

        default:
            throw new EMDConfigException("HADB can't handle the type "+field.getTypeString());
        }
        return typeString;
    }

    
    
    private static String normalizeValue(String input) {
        int length = input.length();
        int nbQuotes = 0;
        int index = 0;
        char c;

        for (index=0; index<length; index++) {
            c = input.charAt(index);
            if (c == '\'') {
                ++nbQuotes;
            }
        }

        if (nbQuotes == 0) {
            return(input);
        }

        char[] dst = new char[length+nbQuotes];
        input.getChars(0, length, dst, 0);

        int i,j;
        j = length-1;
        for (i=length+nbQuotes-1; i>=0; i--) {
            dst[i] = dst[j];
            j--;
            if (dst[i] == '\'') {
                i--;
                dst[i] = '\'';
            }
        }
	
        return(new String(dst));
    }

    /*
     * start / stop methods
     */
    
    public void start()
        throws EMDException {
        // Nothing to do. We rely on the MetadataService to initialize Derby
    }

    public void stop()
        throws EMDException {
        // Nothing to do. We rely on the MetadataService to stop Derby
    }

    /**********************************************************************
     *
     * Compliance APIS. Needed to satisfy CacheInterface but not used.
     *
     **********************************************************************/

    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold, Disk disk)  {
	LOG.info("Legal holds are not stored in the emulator cache");
    }

    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold, Disk disk) {
    	LOG.info("Legal holds are not stored in the emulator cache");
    }
    
    public void sync(Disk disk) throws EMDException {
        // no implementation for now
    }
    
    public void wipe(Disk disk) throws EMDException {
        // no implementation for now
    }

    public void doPeriodicWork(Disk disk) throws EMDException {
        // no implementation for now
    }
}
