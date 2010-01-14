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



import SQLite.Database;
import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.parsers.QueryParser;
import com.sun.honeycomb.emd.parsers.ParseException;
import com.sun.honeycomb.emd.parsers.QueryNode;
import com.sun.honeycomb.emd.parsers.QueryAnd;
import com.sun.honeycomb.emd.parsers.QueryOr;
import com.sun.honeycomb.emd.parsers.QueryExpression;
import com.sun.honeycomb.emd.parsers.TokenMgrError;
import com.sun.honeycomb.emd.cache.CacheUtils;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;
import com.sun.honeycomb.emd.remote.StreamHead;
import com.sun.honeycomb.emd.remote.InMemoryMDStream;
import com.sun.honeycomb.emd.remote.ObjectBroker;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.config.FsView;

public class SQLiteExtended
    implements CacheClientInterface, CacheInterface {

    /**********************************************************************
     *
     * static fields
     *
     **********************************************************************/

    private static final Logger LOG = Logger.getLogger(SQLiteExtended.class.getName());
    public static final String SQLITE_MAIN_TABLE        = "main";

    /**********************************************************************
     *
     * CacheClientInterface implementation
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
            
        try {
            NameValueXML.createXML(attributes, output);
        } catch (IOException e) {
            EMDException newe = new EMDException("Couldn't generate extended cache metadata");
            newe.initCause(e);
            throw newe;
        }
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
    
    /**********************************************************************
     *
     * SQLiteContext class
     *
     **********************************************************************/

    private class SQLiteContext {
        private ArrayList openDatabases;
        private LinkedList availableDatabases;
        private Disk disk;
        private File dbFile;
        
        private SQLiteContext(String MDPath,
                              Disk nDisk)
            throws EMDException {
            openDatabases = new ArrayList();
            availableDatabases = new LinkedList();

            Database database = null;
            disk = nDisk;
            dbFile = new File(MDPath+"/sqlite_"+getCacheId()+"/"+SQLITE_MAIN_TABLE+".db");
            boolean hasToCreateTable = false;
            
            if (!dbFile.getParentFile().exists()) {
                LOG.info("The ["+dbFile.getParentFile().getAbsolutePath()+"] directory does not exist. Creating ...");
                dbFile.getParentFile().mkdir();
                hasToCreateTable = true;
            }
            
            if ((!hasToCreateTable) && (!dbFile.exists())) {
                hasToCreateTable = true;
            }

            try {
                database = new Database();
                database.open(dbFile.getAbsolutePath(), 0);
                if (hasToCreateTable) {
                    String command = SQLiteCommands.createSchemaCommand(SQLITE_MAIN_TABLE);

                    LOG.info("Creating the extended table in the SQLite database ["+
                             command+"]");
                    database.exec(command, null);

                    FsView[] views = RootNamespace.getInstance().getViews();

                    for (int i=0; i<views.length; i++) {
                        boolean viewExists = false;
                        int j = -1;
                        for (j=0; j<i; j++) {
                            if (views[i].sameAttributes(views[j])) {
                                viewExists = true;
                                break;
                            }
                        }

                        if (viewExists) {
                            LOG.info("Skipping the creation of an index for view "+
                                     views[i].getName()+
                                     " since the attributes are the same as the ones for view "+
                                     views[j].getName());
                        } else {
                            command = SQLiteCommands.createViewCommand(views[i]);
                            LOG.info("Creating an index on an SQLite database ["+
                                     command+"]");
                            database.exec(command, null);
                        }
                    }
                }

                openDatabases.add(database);
                availableDatabases.add(database);

            } catch (SQLite.Exception e) {
                if (database != null) {
                    try {
                        database.close();
                    } catch (SQLite.Exception ignored) {
                    }
                    database = null;
                }
                EMDException newe =  new EMDException("Failed to create the "+
                                                      dbFile.getAbsolutePath()+
                                                      " SQLite database ["+e.getMessage()+"]");
                newe.initCause(e);
                throw newe;
            }
        }

        private void close() {
            for (int i=openDatabases.size()-1; i>=0; i--) {
                Database database = (Database)openDatabases.remove(i);
                try {
                    database.close();
                } catch (SQLite.Exception ignored) {
                }
            }
            availableDatabases.clear();
        }

        private Database getDatabase() 
            throws SQLite.Exception {
            synchronized (availableDatabases) {
                if (availableDatabases.size() > 0) {
                    return((Database)availableDatabases.get(0));
                }

                LOG.warning("Creating a new SQLite Database handle [thread "+
                            Thread.currentThread().getName()+" - "+
                            openDatabases.size()+"]. You can safely ignore if you just performed a config update.");

                Database database = new Database();
                database.open(dbFile.getAbsolutePath(), 0);
                openDatabases.add(database);
                availableDatabases.add(database);
                
                return(database);
            }
        }

        private void returnDatabase(Database database) {
            //             synchronized (availableDatabases) {
            //                 availableDatabases.add(database);
            //             }
        }
    }
    
    /**********************************************************************
     *
     * SQLiteExtended class
     *
     **********************************************************************/

    private HashMap contextes;
    
    public SQLiteExtended() {
        contextes = new HashMap();
    }
    
    public String getCacheId() {
        return(CacheInterface.EXTENDED_CACHE);
    }
    
    public String getHTMLDescription() {
        return("A key/value cache implementation that relies on <b>SQLite</b>");
    }

    public void start()
        throws EMDException {
        // Nothing to do
    }
    
    public void stop()
        throws EMDException {
        Disk[] registeredDisks = new Disk[contextes.keySet().size()];
        contextes.keySet().toArray(registeredDisks);
        for (int i=0; i<registeredDisks.length; i++) {
            unregisterDisk(registeredDisks[i]);
        }
    }
    
    public void registerDisk(String MDPath,
                             Disk disk)
        throws EMDException {
        SQLiteContext context = new SQLiteContext(MDPath, disk);
        synchronized (contextes) {
            contextes.put(disk, context);
        }
        LOG.info("Disk ["+disk.getPath()+"] has been registered in the SQLite "+getCacheId()+" cache");
    }
    
    public void unregisterDisk(Disk disk)
        throws EMDException {
        LOG.info("Unregistering disk ["+disk.getPath()+"] from the SQLite "+getCacheId()+" cache");
        
        SQLiteContext context = null;
        synchronized (contextes) {
            context = (SQLiteContext)contextes.remove(disk);
        }
        if (context == null) {
            throw new EMDException("The disk ["+disk+"] was not registered");
        }
        context.close();
    }
    
    public boolean isRegistered(Disk disk) {
        boolean result = false;

        synchronized (contextes) {
            result = contextes.containsKey(disk);
        }

        return(result);
    }

    public static String normalizeString(String value) {
        StringBuffer buffer = new StringBuffer(value);
        for (int i=0; i<buffer.length(); i++) {
            if (buffer.charAt(i) == '\'') {
                buffer.insert(i, "\'");
                i += 1;
            }
        }
        return(buffer.toString());
    }
    
    public void setMetadata(NewObjectIdentifier oid,
                            Object argument,
                            Disk disk)
        throws EMDException {
        if (argument == null) {
            throw new IllegalArgumentException("Got a null argument in setMetadata");
        }

        Map attributes = (Map)argument;
        SQLiteContext context = null;

        synchronized (contextes) {
            context = (SQLiteContext)contextes.get(disk);
        }
        
        if (context == null) {
            throw new EMDException("Disk ["+disk+"] is not registered");
        }
        
        StringBuffer command = new StringBuffer();
        command.append("insert or ignore into "+SQLITE_MAIN_TABLE+" (");
        
        // Insert the attribute names
        Iterator names = attributes.keySet().iterator();
        boolean first = true;
        RootNamespace namespace = RootNamespace.getInstance();

        while (names.hasNext()) {
            if (first) {
                first = false;
            } else {
                command.append(", ");
            }

            String attributeName = (String)names.next();
            Field field = namespace.resolveField(attributeName);
            if (field == null) {
                throw new EMDException("Attribute ["+attributeName+"] does not exist");
            }
            
            field.getQualifiedName(command, "_");
        }
        
        command.append(") values (");
        
        names = attributes.keySet().iterator();
        first = true;
        while (names.hasNext()) {
            if (first) {
                first = false;
            } else {
                command.append(", ");
            }
            command.append("'"+normalizeString((String)attributes.get(names.next()))+"'");
        }
        
        command.append(")");
        
        Database database = null;

        try {
            database = context.getDatabase();
            database.exec(command.toString(), null);
        } catch (SQLite.Exception e) {
            EMDException newe = new EMDException("Failed to insert a record in the "+
                                                 getCacheId()+" SQLite database [disk "+
                                                 context.disk.getPath()+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (database != null) {
                context.returnDatabase(database);
            }
        }
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("A new record has been inserted in the "+
                     getCacheId()+" SQLite database [disk "+
                     context.disk.getPath()+" - oid ["+
                     oid+"]");
        }
    }

    public void removeMetadata(NewObjectIdentifier oid,
                               Disk disk)
        throws EMDException {
        SQLiteContext context = null;
        
        synchronized (contextes) {
            context = (SQLiteContext)contextes.get(disk);
        }
        if (context == null) {
            throw new EMDException("Disk ["+disk+"] is not registered");
        }
        
        StringBuffer command = new StringBuffer();
        command.append("delete from ");
        command.append(SQLITE_MAIN_TABLE);
        command.append(" where ");
        command.append(SystemMetadata.FIELD_NAMESPACE+"_"+SystemMetadata.FIELD_OBJECTID);
        command.append("=");
        command.append("'"+oid.toHexString()+"'");
        
        Database database = null;

        try {
            database = context.getDatabase();
            database.exec(command.toString(), null);
        } catch (SQLite.Exception e) {
            EMDException newe = new EMDException("Failed to delete a record in the "+
                                                 getCacheId()+" SQLite database [disk "+
                                                 context.disk.getPath()+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (database != null) {
                context.returnDatabase(database);
            }
        }
        
        LOG.info("A record has been deleted from the "+
                 getCacheId()+" SQLite database [disk "+
                 context.disk.getPath()+"] with the command ["+
                 command+"]");
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
            return("'"+input+"'");
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
	
        return("'"+new String(dst)+"'");
    }

    private void toSQLiteQuery(QueryNode node,
                               StringBuffer output) {

        if (node instanceof QueryAnd) {
            output.append("(");
            toSQLiteQuery(node.getLeftChild(), output);
            output.append(" and ");
            toSQLiteQuery(node.getRightChild(), output);
            output.append(")");
            return;
        } 
        
        if (node instanceof QueryOr) {
            output.append("(");
            toSQLiteQuery(node.getLeftChild(), output);
            output.append(" or ");
            toSQLiteQuery(node.getRightChild(), output);
            output.append(")");
            return;
        }
        
        if (node instanceof QueryExpression) {
            QueryExpression query = (QueryExpression)node;
            switch (query.getOperator()) {
            case QueryExpression.OPERATOR_EQUAL:
                if (query.getValue().equalsIgnoreCase("isnull")) {
                    query.getAttributeField().getQualifiedName(output, "_");
                    output.append(" isnull");
                } else {
                    if (query.getAttributeType() == Field.TYPE_DOUBLE) {
                        output.append("abs(");
                        query.getAttributeField().getQualifiedName(output, "_");
                        output.append("-"+
                                      Double.parseDouble(query.getValue())  +
                                      ") < "+QueryExpression.DOUBLE_PRECISION);
                    } else {
                        query.getAttributeField().getQualifiedName(output, "_");
                        output.append("="+
                                      normalizeValue(query.getValue()));
                    }
                }
                break;
                
            case QueryExpression.OPERATOR_NOTEQUAL:
                if (query.getAttributeType() == Field.TYPE_DOUBLE) {
                    output.append("abs(");
                    query.getAttributeField().getQualifiedName(output, "_");
                    output.append("-"+
                                  Double.parseDouble(query.getValue()) +
                                  ") > "+QueryExpression.DOUBLE_PRECISION);
                } else {
                    query.getAttributeField().getQualifiedName(output, "_");
                    output.append("!="+
                                  normalizeValue(query.getValue()));
                }
                break;
		    
            case QueryExpression.OPERATOR_LT:
                query.getAttributeField().getQualifiedName(output, "_");
                output.append("<"+
                              normalizeValue(query.getValue()));
                break;
                
            case QueryExpression.OPERATOR_GT:
                query.getAttributeField().getQualifiedName(output, "_");
                output.append(">"+
                              normalizeValue(query.getValue()));
                break;
            }
            return;
        }
        
        throw new IllegalArgumentException("Invalid node ["+node.getClass().getName()+"]");
    }

    public void toSQLiteQuery(String query,
                              StringBuffer output)
        throws EMDException {
        QueryNode node = null;

        try {
            node = QueryParser.parse(query);
        } catch (ParseException e) {
            EMDException newe = new EMDException("Failed to parse the query ["+
                                                 query+"] - ["
                                                 +e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (TokenMgrError e) {
            EMDException newe = new EMDException("Failed to parse the query ["+
                                                 query+"] - ["
                                                 +e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
        
        toSQLiteQuery(node, output);
    }

    private SQLiteContext[] getContextes(ArrayList disks) 
        throws EMDException {
        SQLiteContext[] result = null;

        synchronized (contextes) {
            if (disks ==  null) {
                // Return all contextes
                Collection values = contextes.values();
                result = new SQLiteContext[values.size()];
                values.toArray(result);
            } else {
                result = new SQLiteContext[disks.size()];
                for (int i=0; i<result.length; i++) {
                    result[i] = (SQLiteContext)contextes.get((Disk)disks.get(i));
                    if (result[i] == null) {
                        throw new EMDException("Disk ["+(Disk)disks.get(i)+
                                               "] is not registered");
                    }
                }
            }
        }

        return(result);
    }

    public void queryPlus(MDOutputStream output,
                          ArrayList disks,
                          String query,
                          ArrayList attributes,
                          NewObjectIdentifier cookie,
                          int maxResults, int timeout,
                          boolean forceResults)
        throws EMDException {
        
        // Build the SQL query

        StringBuffer command = new StringBuffer();
        String oidQualified = SystemMetadata.FIELD_NAMESPACE+"."+SystemMetadata.FIELD_OBJECTID;
        String oidAttributeName = SystemMetadata.FIELD_NAMESPACE+"_"+SystemMetadata.FIELD_OBJECTID;
        ArrayList columns = new ArrayList();
        
        command.append("select "+oidAttributeName);
        columns.add(oidQualified);

        if (attributes != null) {
            RootNamespace namespace = RootNamespace.getInstance();

            for (int i=0; i<attributes.size(); i++) {
                String attribute = (String)attributes.get(i);
                if (!attributes.equals(oidQualified)) {
                    Field field = namespace.resolveField(attribute);
                    if (field == null) {
                        throw new EMDException("Attribute ["+
                                               attribute+"] does not exist");
                    }
                    command.append(", ");
                    field.getQualifiedName(command, "_");
                    columns.add(field.getQualifiedName());
                }
            }
        }

        String[] cols = new String[columns.size()];
        columns.toArray(cols);

        command.append(" from "+SQLITE_MAIN_TABLE+" where (");
        
        toSQLiteQuery(query, command);
        if (cookie != null) {
            command.append(") and "+oidAttributeName+">'"+cookie.toHexString()+"'");
        } else {
            command.append(")");
        }
        command.append(" order by "+oidAttributeName+" collate binary");
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("The SQLiteExtended query is ["+command.toString()+"]");
        }

        SQLiteContext[] cxtes  =  getContextes(disks);
        Database[] databases = new Database[cxtes.length];
        SQLiteStream[] sqliteStreams = new SQLiteStream[cxtes.length];
        StreamHead[] streams = new StreamHead[cxtes.length];
        Thread[] dbThreads = new Thread[cxtes.length];
        
        byte type = (attributes == null)
            ? SQLiteCallback.CALLBACK_SIMPLE_QUERY
            : SQLiteCallback.CALLBACK_QUERY_PLUS;
        
        for (int i=0; i<streams.length; i++) {
            try {
                databases[i] = cxtes[i].getDatabase();
                sqliteStreams[i] = new SQLiteStream(databases[i],
                                                    command.toString(),
                                                    type, maxResults, cols, cxtes[i].disk.getPath());
                streams[i] = new StreamHead(sqliteStreams[i]);
                dbThreads[i] = new Thread(sqliteStreams[i]);
                dbThreads[i].start();
            } catch (SQLite.Exception e) {
                LOG.log(Level.WARNING,
                        "Failed to get a database handle for disk ["+
                        cxtes[i].disk.getPath()+"] - ["+
                        e.getMessage()+"]",
                        e);
            }
        }
        
        StreamHead.mergeStreams(streams, output, 0, maxResults);

        EMDException exception = null;

        for (int i=0; i<databases.length; i++) {
            if (exception == null) {
                exception = sqliteStreams[i].getException();
            }
            if (databases[i] != null) {
                cxtes[i].returnDatabase(databases[i]);
            }
        }

        if (exception != null) {
            throw exception;
        }
    }
    
    public void selectUnique(MDOutputStream output,
                             String query,
                             String attribute,
                             String cookie,
                             int maxResults, int timeout,
                             boolean forceResults)
        throws EMDException {

 throw new EMDException("update this to take cookie as object not string");

        String oidAttributeName = SystemMetadata.FIELD_NAMESPACE+"_"+SystemMetadata.FIELD_OBJECTID;

        // Find the type of the attribute
        Field field = RootNamespace.getInstance().resolveField(attribute);
        if (field == null) {
            throw new EMDException("Couldn't find attribute ["+
                                   attribute+"]");
        }
        int resultType = field.getType();

        // Build the SQL query
        StringBuffer command = new StringBuffer();
        command.append("select distinct ");
        field.getQualifiedName(command, "_");
        command.append(" from "+SQLITE_MAIN_TABLE);
        if ((query != null) || (cookie != null)) {
            command.append(" where ");
            
            if (query != null) {
                command.append("(");
                toSQLiteQuery(query, command);
                command.append(")");
            }
            
            if (cookie != null) {
                command.append(" and "+oidAttributeName+">'"+cookie+"'");
            }
        }
        command.append(" order by ");
        field.getQualifiedName(command, "_");
        command.append(" collate binary");
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("The SQLiteExtended select unique statement is ["+command.toString()+"]");
        }
        
        SQLiteContext[] cxtes  =  getContextes(null);
        Database[] databases = new Database[cxtes.length];
        SQLiteStream[] sqliteStreams = new SQLiteStream[cxtes.length];
        StreamHead[] streams = new StreamHead[cxtes.length];
        Thread[] dbThreads = new Thread[cxtes.length];

        byte type = SQLiteCallback.CALLBACK_SELECT_UNIQUE;
        
        for (int i=0; i<streams.length; i++) {
            try {
                databases[i] = cxtes[i].getDatabase();
                sqliteStreams[i] = new SQLiteStream(databases[i],
                                                    command.toString(),
                                                    type, resultType, maxResults, null,
                                                    cxtes[i].disk.getPath());
                streams[i] = new StreamHead(sqliteStreams[i]);
                dbThreads[i] = new Thread(sqliteStreams[i]);
                dbThreads[i].start();
            } catch (SQLite.Exception e) {
                LOG.log(Level.WARNING,
                        "Failed to get a database handle for disk ["+
                        cxtes[i].disk.getPath()+"] - ["+
                        e.getMessage()+"]",
                        e);
            }
        }
        
        StreamHead.mergeStreams(streams, output, 0, maxResults);

        EMDException exception = null;

        for (int i=0; i<databases.length; i++) {
            if (exception == null) {
                exception = sqliteStreams[i].getException();
            }
            if (databases[i] != null) {
                cxtes[i].returnDatabase(databases[i]);
            }
        }
	
        if (exception != null) {
            throw exception;
        }
    }
}
