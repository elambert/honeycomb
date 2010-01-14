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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import java.io.StringWriter;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.CanonicalStrings;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.SoftwareVersion;

import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.Table;
import com.sun.honeycomb.emd.config.Column;
import com.sun.honeycomb.emd.config.EMDConfigException;

import com.sun.honeycomb.emd.common.EMDException;

import com.sun.honeycomb.hadb.convert.ConvertConstants;

public class DatabaseConverter {

    private static final Logger LOG = Logger.getLogger(DatabaseConverter.class.getName());

    // How often to update the lastOID in the pending attribute table
    // DEBUG: artifically low value!
    public static final int PROGRESS_TIMER_FREQUENCY=100;

    private static DatabaseConverter instance;

    private TableConverter currentConverter;

    private SchemaAccess schema;

    private RootNamespace preUpgradeNS;

    private AttributeTable attributeTable;
    private AttributeTable oldAttributeTable;

    private static String targetVersion = SoftwareVersion.getRunningVersion();

    private int rowsConvertedInStep = 0;

    DatabaseConverter() 
        throws EMDException {
        try {
            schema = SchemaAccess.getInstance(null);
        } catch (SQLException e) {
            throw new EMDException(e);
        } catch (NoConnectionException e) {
            throw new EMDException(e);
        }
    }

    public static synchronized DatabaseConverter getInstance() 
        throws EMDException {
        if (instance == null) {
            instance = new DatabaseConverter();
        }
        return instance;
    }

    SchemaAccess getSchema() {
        return schema;
    }


    RootNamespace getPreUpgradeNS() {
        return preUpgradeNS;
    }

    public static synchronized boolean isConvertInProgress() 
        throws EMDException {
        LOG.info("entering isConvertInProgress");
        if (RootNamespace.preUpgradeConfigExists()) {
            LOG.info("Database convert needed since pre-upgrade config exists");
            return true;
        }
        AttributeTable existingAttributeTable = 
            AttributeTable.getLoaded(ConvertConstants.ATTRIBUTE_TABLE_NAME);
        if (existingAttributeTable == null) {
            LOG.info("no database convert needed: fresh new database");
            return false;
        }
        String hcVersion = 
            existingAttributeTable.getHoneycombVersion();
        if (hcVersion == null) {
            LOG.info("Database convert needed: saved hcVersion is null, target version = "+targetVersion);
            return true;
        }
        if (! checkCompatibleSchema(hcVersion,targetVersion)) {
            return true;
        }
	    if (LOG.isLoggable(Level.INFO)) {
            LOG.info("No database convert needed,"+
                     " since versions are compatible:"+
                     "\n\t hadb database from version="+hcVersion+
                     "\n\t new install from version = "+targetVersion);
	    }

        return false;
    }

    public static boolean checkCompatibleSchema(String existingVersion, 
                                                String targetVersion) {
        // If some new target version is added which is not compatible
        // with the HADB shipped with 1.1-35 (the first version to store
        // the Honeycomb version in HADB) then return false to cause
        // a database upgrade to occur.
        return true;
    }

    // The following should be done before upgrade-reboot to set up the state
    // for the database transformation.
    // Note:  when we do this for real, we have to copy the config files
    // onto all nodes via the config file mechanism.
    public static synchronized void initConvert()
        throws EMDException {
        DatabaseConverter dbc = DatabaseConverter.getInstance();

        dbc.clearConvertState();

        RootNamespace.savePreUpgradeConfig();
    }

    public void clearConvertState() 
        throws EMDException {

        dropPendingTable(ConvertConstants.PENDING_ATTRIBUTE_TABLE_NAME);
        oldAttributeTable = null;

        AttributeTable.dropAttributeTable(ConvertConstants.OLD_ATTRIBUTE_TABLE_NAME,
                                          schema);
    }

    public static synchronized DatabaseConverter resumeConvert()
        throws EMDException {

        DatabaseConverter dbc = new DatabaseConverter();
        
        dbc.restartConvert();
        return dbc;

    }


    public void restartConvert() 
        throws EMDException {

        attributeTable = AttributeTable.getInstance();

        if (schema.containsTable(ConvertConstants.OLD_ATTRIBUTE_TABLE_NAME)) {
            LOG.warning("Resuming database upgrade");
            oldAttributeTable = AttributeTable.getLoaded(ConvertConstants.OLD_ATTRIBUTE_TABLE_NAME);
        } else {
            LOG.warning("Starting database upgrade");

            // pending attribute table should not exist here, but just in case...
            dropPendingTable(ConvertConstants.PENDING_ATTRIBUTE_TABLE_NAME);

            oldAttributeTable = 
                attributeTable.copyAttributeTable(
                        ConvertConstants.OLD_ATTRIBUTE_TABLE_NAME,
                        schema);

            // Forget the old tables so we can create new ones 
            // that match the new metadata schema and new version
            attributeTable.forgetMetadataTables();

            // Have to completely remove the old table, so we 
            // can recreate it with new table layout.
            AttributeTable.dropAttributeTable(
                        ConvertConstants.ATTRIBUTE_TABLE_NAME,
                        schema);
            AttributeTable.createAttributeTable(
                        ConvertConstants.ATTRIBUTE_TABLE_NAME,
                        schema);

            RootNamespace rootNS;
            try {
                rootNS = RootNamespace.getInstance();
            } catch (EMDConfigException e) {
                throw new EMDException(e);
            }

            // Get the attribute table to create the new tables.
            attributeTable.reloadAll(schema, rootNS);
        }

        if (! schema.containsTable(ConvertConstants.PENDING_ATTRIBUTE_TABLE_NAME)) {
            createPendingTable(ConvertConstants.PENDING_ATTRIBUTE_TABLE_NAME, 
                               attributeTable);
        }

        LOG.info("Reading pre-upgrade config file");
        preUpgradeNS = RootNamespace.getPreUpgradeInstance();
        if (preUpgradeNS == null) {
            LOG.info("No pre-upgrade config file, so upgrading using current config file");
            preUpgradeNS = RootNamespace.getInstance();
        }

        //DEBUG:
        LOG.info("For Debugging: Pre-Upgrade config file as read");
        try {
            StringWriter sw = new StringWriter();
            preUpgradeNS.export(sw,true);
            sw.flush();
            LOG.info(sw.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void createPendingTable(String name,
                                          AttributeTable attrTable) 
        throws EMDException {
        try {
            schema.checkAndCreate(name,
                                  "( name nvarchar(64) primary key," +
                                  " lastoid "+
                                  ConvertConstants.OID_SQL_TYPE+")");
        } catch (SQLException e) {
            LOG.log(Level.WARNING,
                    "DatabaseConverter failed to create pending_tables table!"+
                    "\n\t["+e.getMessage()+"]\n",
                    e);
            throw new RuntimeException(e);
        }
        attributeTable.createTableFromQuery("select name from "+
                                            attrTable.getAttributeTableName(),
                                            "insert into "+ name + " values (?, null)",
                                            1);
                                            
    }

    public void dropPendingTable(String name) {
        try {
            schema.dropTable(name);
        } catch (SQLException e) {
            LOG.log(Level.WARNING,
                    "Failed to drop pending_tables table!"+
                    "\n\tWe can continue from this error, so ignoring it..."+
                    "\n\t["+e.getMessage()+"]",
                    e);
        }        
    }


    public TableConverter nextToConvert()
        throws EMDException {


        PreparedStatement queryStatement = null;
        PreparedStatement insertStatement = null;
        Connection connection = null;
        ResultSet rs = null;
        TableConverter tc = null;
    
        LOG.info("in nextToConvert");
        try {
            connection = HADBJdbc.getInstance().getConnection();
            queryStatement = 
                connection.prepareStatement("select name, lastoid from "+
                                            ConvertConstants.PENDING_ATTRIBUTE_TABLE_NAME);

            rs = RetryableCode.retryExecutePreparedQuery(queryStatement,null);

            if (rs.next()) {
                NewObjectIdentifier lastOid = null;

                String name = rs.getString(1);
                byte[] oidBytes = rs.getBytes(2);
                if (oidBytes != null) 
                    lastOid = NewObjectIdentifier.readFromBytes(oidBytes);
                tc = new TableConverter(this, name, lastOid);
            }


        } catch (SQLException e) {
            EMDException newe =
                new EMDException("Failed to get next table to convert: "+
                                 " message is [" + e.getMessage()+"]",
                                 e);
            throw newe;
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (SQLException e) {}
                rs = null;
            }
            if (queryStatement != null) {
                try { queryStatement.close();
                } catch (SQLException e) {
                    LOG.log(Level.WARNING,"Unable to close Prepared Statement." + e);
                }
                queryStatement = null;

            }
            if (connection != null) {
                HADBJdbc.getInstance().freeConnection(connection);
                connection = null;
            }
        } // finally
        
        LOG.info("nextToConvert returning "+tc);
        return tc;
    }

    public void tableConverterComplete(TableConverter tc) 
    throws EMDException {

        String configTableName = tc.getConfigTableName();
        String newDBTableName = tc.getNewDBTableName();
        LOG.info("table convert of table "+configTableName+
                 " to new table "+newDBTableName+
                 " is complete.");

        AttributeTable.executeSQL("delete from "+
                                  ConvertConstants.PENDING_ATTRIBUTE_TABLE_NAME+
                                  " where name=?",
                                  Collections.singletonList(configTableName));


    }

    public void markProgress(TableConverter tc, NewObjectIdentifier lastOid) 
    throws EMDException {

        String configTableName = tc.getConfigTableName();
        String newDBTableName = tc.getNewDBTableName();
        String oidString = lastOid.toHexString();
        LOG.info("mark progress: table "+configTableName+
                 " is up to lastOid="+oidString);
        tc.setLastOid(lastOid);
        AttributeTable.executeSQL("update "+
                                  ConvertConstants.PENDING_ATTRIBUTE_TABLE_NAME+
                                  " set lastoid = x'"+
                                  oidString+
                                  "' where name=?",
                                  Collections.singletonList(configTableName));


    }

    public void databaseConverterComplete()
        throws EMDException {

        LOG.info("database upgrade is complete... dropping old tables");

        Table [] tables = preUpgradeNS.getTables();
        for (int i = 0; i < tables.length; i++) {
            Table table = tables[i];
            oldAttributeTable.dropMetadataTable(table, schema);
        }

        // Now all the state is dropped.  Declare the database upgrade finished
        RootNamespace.removePreUpgradeConfig();
 
        try {
            schema.dropTable(ConvertConstants.PENDING_ATTRIBUTE_TABLE_NAME);
            schema.dropTable(ConvertConstants.OLD_ATTRIBUTE_TABLE_NAME);
        } catch (SQLException e) {
            throw new EMDException(e);
        }

        // By setting the version in the attribute table,
        //  we declare this conversion COMPLETE.
        attributeTable.setHoneycombVersion(targetVersion);

        LOG.warning("Database Upgrade Finished - entering main loop");
    }



    //returns true if should continue converting
    public boolean continueConvert(TableConverter tc)
        throws EMDException {

        assert(oldAttributeTable != null);
        assert(preUpgradeNS != null);


        String configTableName = tc.getConfigTableName();

        Table newTable = RootNamespace.getInstance().getTable(configTableName);

        // sqlQuery selects all rows including system.object_id
        List literals = new ArrayList();
        String sqlQuery = oldAttributeTable.createReadTableQuery(preUpgradeNS,
                                                                 newTable,
                                                                 tc.getLastOid(),
                                                                 literals);

        if (sqlQuery == null) {
            return false;       // Done with empty table that cannot be converted
        }

        int size = newTable.getColumnCount() + 1;       // + OBJECTID
        String ins = attributeTable.makeInsertStatement(newTable);     // throws EMDException

        boolean finishedCreate = 
            createTableFromQuery(sqlQuery,ins, size, tc, literals);

        return (! finishedCreate);
    }

    public boolean createTableFromQuery(String query, 
                                        String ins, 
                                        int size, 
                                        TableConverter tc,
                                        List literals)
        throws EMDException {

        PreparedStatement queryStatement = null;
        PreparedStatement insertStatement = null;
        Connection connection = null;
        ResultSet rs = null;
        Object[] rowValues = null;
        boolean resultCode = false;

        int numConverted = 0;
        LOG.info("createTableFromQuery: \n\tquery='"+query+"'\n"+
                 "\tins='"+ins+"'");

        byte[] lastOidBytes = null;
        byte[] lastGoodOidBytes = null;
    
        try {
            connection = HADBJdbc.getInstance().getConnection();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to get HADB connection during upgrade!",e);
            throw new RuntimeException(e);
        }
                
        try {
            // Need manual commit mode for two parallel cursors
            connection.setAutoCommit(false);
            queryStatement = connection.prepareStatement(query);
            insertStatement = connection.prepareStatement(ins);

            ResultSetMetaData rsmd = queryStatement.getMetaData();
            if (size != rsmd.getColumnCount())
                throw new InternalException("Unexpected result set size="+
                                            rsmd.getColumnCount()+
                                            " in copyTable query, expected="+size);

            queryStatement.setFetchSize(PROGRESS_TIMER_FREQUENCY);
            rs = RetryableCode.retryExecutePreparedQuery(queryStatement,literals);
            
            Table table = tc.getTable();

            for (int i = 0; i < PROGRESS_TIMER_FREQUENCY; i++) {
                if (! rs.next()) {
                    resultCode = true;
                    break;
                }
                rowValues = AttributeTable.readRow(rs,size);
                transformRow(rowValues, table); 
                AttributeTable.addRowPreparedStatement(insertStatement, 
                                                       Arrays.asList(rowValues),
                                                       ins);
                lastOidBytes = (byte[]) rowValues[0];
                numConverted++;
            } // for

            connection.commit();
            lastGoodOidBytes = lastOidBytes;
            if (LOG.isLoggable(Level.INFO)) {
                LOG.info("Committed up to "+numConverted+" rows at OID="+
                         CanonicalStrings.encode(lastGoodOidBytes));
            }

        } catch (SQLException e) {
            if (RetryableCode.isRetryable(e)) {
                LOG.log(Level.WARNING, "Retryable SQL error during upgrade - ["+e.getMessage()+"] -- "+
                        "retrying that portion of upgrade...",e);
                resultCode = false;
            } else {
                EMDException newe = new EMDException("Failed to convert row.  numConverted="+
                                                     numConverted+
                                                     (lastOidBytes != null ? 
                                                      (" OID="+ CanonicalStrings.encode(lastOidBytes)) :
                                                      "")+
                                                     " message is [" + e.getMessage()+"]",
                                                     e);
                throw newe;
            }
        } finally {
            try {
                connection.rollback();
            } catch (SQLException ignored) { 
                // If we get an exception on rollback then
                //   the connection's transaction
                //   should have already been rolled back by other means.
            }
            try {
                connection.setAutoCommit(false);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "failed to set auto commit: "+e.getMessage(),e);
            }
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

        if (lastGoodOidBytes != null) {
            NewObjectIdentifier lastOid =
                NewObjectIdentifier.readFromBytes(lastGoodOidBytes);
            markProgress(tc,lastOid);
        }

        rowsConvertedInStep += numConverted;
        LOG.info("converted "+numConverted+" rows");

        return resultCode;
        
    }

    public void transformRow(Object[] rowValues, Table table) {

        // The first transform we need is on the objectid field
        //  For now, we just key off the type of the data value.
        //  If we want to, we can determine the desired transform up front
        //  i.e. when the DatabaseConverter object gets created.
        //  but for now we just trust the data!
        Object oidObject = rowValues[0];

        if (oidObject instanceof String) {
            String oidString = (String)oidObject;
            NewObjectIdentifier oid =
                NewObjectIdentifier.fromLegacyHexString(oidString);
            rowValues[0] = oid.getDataBytes();
        } else if (oidObject instanceof byte[]) {
            // nothing to do
        } else {
            throw new RuntimeException("expected String or byte[] for OID");
        }

        Column columns[] = new Column[rowValues.length-1];
        columns = (Column[])table.getColumns().toArray(columns);
        LOG.info("columns="+Arrays.asList(columns));
        LOG.info("rowValues.length=="+rowValues.length+" columns.length=="+columns.length);

        // Convert all String values that should be a different type 
        // into the indicated type (binary, long, double)
        // compare to getLiteralsForInsert for this conversion
        // rowValues array starts at 2nd entry after OBJECTID
        // columns array does not include OBJECTID, so index off by one
        for (int index = 1; index < rowValues.length - 1; index++) {
            if (rowValues[index] instanceof String) {
                rowValues[index] =
                    CanonicalStrings.decode((String)rowValues[index],
                                           columns[index-1].getField());
            }
        }
    }

    /**
     * Run one step (PROGRESS_TIMER_FREQUENCY rows) of the current DB
     * converter. Returns the number of rows actually converted, 0
     * means the conversion is complete. -1 means there were errors
     * and no progress was made.
     */
    public int runConvertStep() throws EMDException {

        rowsConvertedInStep = 0;
        
        if (currentConverter == null) {
            currentConverter = nextToConvert();
            if (currentConverter == null) {
                databaseConverterComplete();
                // Database conversion is complete.
                return 0;
            }
            currentConverter.startConvert();
        }
        if (! continueConvert(currentConverter)) {
            tableConverterComplete(currentConverter);
            currentConverter = null;
        }

        if (rowsConvertedInStep == 0) 
            // If no rows were converted but upgrade is still not done,
            // report that no progress was made.
            return -1;
        else
            return rowsConvertedInStep;
    } // runConvertStep

    /** Returns true if the conversion is still going on */
    public boolean waitForConvert() throws EMDException {
        return runConvertStep() != 0;
    } // waitForConvert
}
