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



/**
 * The class <code>BDBSystemCache</code> is the process unit
 * for the recovery table. It relies on the the MySqlProcessUnit
 * implementation.
 */

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.emd.cache.CacheInterface;
import java.util.logging.Logger;

import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sleepycat.db.Db;
import com.sleepycat.db.DbEnv;
import com.sleepycat.db.DbRunRecoveryException;
import com.sleepycat.db.DbTxn;
import com.sleepycat.db.DbException;
import java.util.logging.Level;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDHitByATime;
import com.sun.honeycomb.common.SystemMetadata;
import com.sleepycat.db.Dbt;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import java.util.ArrayList;
import com.sleepycat.db.Dbc;
import com.sleepycat.bdb.bind.tuple.TupleInput;
import com.sleepycat.bdb.bind.tuple.TupleOutput;
import java.io.IOException;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import java.lang.UnsupportedOperationException;
import java.nio.ByteBuffer;

import com.sun.honeycomb.common.CacheRecord;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.io.UnsupportedEncodingException;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.emd.common.MDHit;

public class BDBSystemCache
    extends BDBCache {

    private static final String INDEX_MAPID     = "perMapId";
    private static final String INDEX_MDID      = "perMDId";

    // ATime = (CTime > DTime ? CTime : DTime)
    private static final String INDEX_ATIMEID    = "perATime";

    private static final int QUERY_CODE_UNKNOWN         = 0;
    private static final int QUERY_CODE_GETOBJECTS      = 1;
    private static final int QUERY_CODE_GETMDSTHATPOINT = 2;
    private static final int QUERY_CODE_CHECKOID        = 3;
    private static final int QUERY_CODE_GETCHANGES      = 4;
    private static final int QUERY_CODE_HOLD            = 5;
    private static final int QUERY_CODE_ISRESTORED      = 6;
    private static final int QUERY_CODE_SETNOTRESTORED  = 7;

    /**********************************************************************
     *
     * The CacheClientInterface API
     *
     **********************************************************************/

    public void generateMetadataStream(CacheRecord mdObject,
                                       OutputStream output) 
        throws EMDException {
        if (mdObject == null) {
            throw new EMDException("The FS cache does not support empty MD descriptions");
        }

        HashMap attributes = new HashMap();
        try {
            ((SystemMetadata)mdObject).populateStrings(attributes, true);      //Strings only -- include systemcache fields
            NameValueXML.createXML(attributes, output);                 //Use Identity encoding
        } catch (IOException e) {
            EMDException newe = new EMDException("Couldn't generate some system cache metadata");
            newe.initCause(e);
            throw newe;
        }
    }
    
    public CacheRecord generateMetadataObject(NewObjectIdentifier oid) 
        throws EMDException {
        // This method should never be invoked.
        throw new UnsupportedOperationException("BDBSystemCache.generateMetadataObject shouldn't be used");
    }
    
    public CacheRecord parseMetadata(InputStream in, long mdLength, Encoding en)
        throws EMDException {
        // This method should never be invoked.
        throw new UnsupportedOperationException("BDBSystemCache.parseMetadata shouldn't be used");
    }
    


    public int getMetadataLayoutMapId(CacheRecord argument,
                                      int nbOfPartitions) {
        throw new UnsupportedOperationException("BDBSystemCache.getMetadataLayoutMapId shouldn't be used");
    }

    public int[] layoutMapIdsToQuery(String nQuery,
                                     int nbOfPartitions) {
        SystemCacheQuery query = null;

        try {
            query = new SystemCacheQuery(nQuery);
        } catch (EMDException e) {
            LOG.log(Level.SEVERE,
                    "Couldn't parse the system cache query",
                    e);
            return(null);
        }
        
        int[] result = null;

        switch (query.getType()) {
        case QUERY_CODE_GETOBJECTS:
            int layoutMapId = Integer.parseInt(query.getArguments()[1]);
            result = new int[1];
            result[0] = layoutMapId;
            break;
        case QUERY_CODE_GETMDSTHATPOINT:
            return null;
        case QUERY_CODE_CHECKOID:
            NewObjectIdentifier oid = 
                NewObjectIdentifier.fromExternalHexString(query.getArguments()[1]);
            result = new int[1];
            result[0] = oid.getLayoutMapId();
            break;
        case QUERY_CODE_GETCHANGES:
            return null;
        case QUERY_CODE_HOLD:
            return null;   
        case QUERY_CODE_ISRESTORED:
            return null;
        case QUERY_CODE_SETNOTRESTORED:
            return null;
        }
        
        return(result);
    }

    public void sanityCheck(CacheRecord argument)
        throws EMDException {
        // No sanity check implemented in this cache for now
    }
    
    /**********************************************************************
     *
     * Generic CacheInterface methods
     *
     **********************************************************************/

    public String getCacheId() {
        return(CacheInterface.SYSTEM_CACHE);
    }

    public String getHTMLDescription() {
        return("A system cache implementation that relies on <b>Berkeley DB</b>");
    }

    public boolean isRunning() {
        return true;
    }

    /**********************************************************************
     *
     * SystemCacheQuery class
     *
     **********************************************************************/
    
    private class SystemCacheQuery {
        private int type;
        private String[] arguments;

        private SystemCacheQuery(String query)
            throws EMDException {

            if (query == null) {
                throw new EMDException("Query cannot be null");
            }

            arguments = query.split(" ");
            type = QUERY_CODE_UNKNOWN;

            if (type == QUERY_CODE_UNKNOWN) {
                if(arguments[0].equals(SystemCacheConstants.SYSTEM_QUERY_GETOBJECTS)) {
                    type = QUERY_CODE_GETOBJECTS;
                } else if(arguments[0].equals(SystemCacheConstants.SYSTEM_QUERY_GETMDSTHATPOINT)) {
                    type = QUERY_CODE_GETMDSTHATPOINT;
                } else if(arguments[0].equals(SystemCacheConstants.SYSTEM_QUERY_CHECKOID)) {
                    type = QUERY_CODE_CHECKOID;
                } else if(arguments[0].equals(SystemCacheConstants.SYSTEM_QUERY_GETCHANGES)) {
                    type = QUERY_CODE_GETCHANGES;
                } else if(arguments[0].equals(SystemCacheConstants.SYSTEM_QUERY_HOLD)) {
                   type = QUERY_CODE_HOLD;
                } else if(arguments[0].equals(SystemCacheConstants.SYSTEM_QUERY_ISNOTRESTORED)) {
                    type = QUERY_CODE_ISRESTORED;
                } else if(arguments[0].equals(SystemCacheConstants.SYSTEM_QUERY_SETNOTRESTORED)) {
                    type = QUERY_CODE_SETNOTRESTORED;
                }
            }
            if (type == QUERY_CODE_UNKNOWN) {
                throw new EMDException("Unknown query type ["
                                       +arguments[0]+"]");
            }
        }
        
        private int getType() { return(type); }
        private String[] getArguments() { return(arguments); }
    }
    
    /**********************************************************************
     *
     * Static Fields
     *
     **********************************************************************/
    
    private static final Logger LOG = Logger.getLogger(BDBSystemCache.class.getName());
    
    /**********************************************************************
     *
     * Methods
     *
     **********************************************************************/

    public BDBSystemCache() {
        super();
    }
    
    protected void createIndexes(PerDiskRecord cookie)
        throws EMDException, FileNotFoundException, DbException {
        createIndex(INDEX_MAPID, MAINDB_NAME, 
                    new MapIdKeyCreate(), cookie, null);
        /*
         * WARNING!!! adding indexees can lead to severe performance
         *            degradation in setMetadata, for example the index
         *            below will degrade the performance by 30%
         * 
         */
        // createIndex(INDEX_MDID, MAINDB_NAME,
        //            new MDIdKeyCreate(), cookie, null);
        createIndex(INDEX_ATIMEID, MAINDB_NAME,
                    new ATimeKeyCreate(), cookie, new ATimeCompare());
    }
    
    protected void setMetadata(NewObjectIdentifier oid,
                               Object argument,
                               Db db)
        throws DbException, EMDException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("setMetadata has been called in the SystemCache");
        }

        if (argument == null) {
            throw new EMDException("The SystemMetadata argument has to be given to"
                                   +" populate the system cache");
        }
        
        SystemMetadata systemMetadata = (SystemMetadata)argument;
        Dbt key = null;
        Dbt data = null;

        key = encodeDbt(systemMetadata.getOID(),
                        SIZE_OBJECTID);
        data = encodeDbt(systemMetadata,
                         SIZE_SYSTEMMETADATA);
       
        db.put(null, key, data, Db.DB_AUTO_COMMIT);
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("system MD have been inserted");
        }
    }
    
    protected void removeMetadata(NewObjectIdentifier oid,
                                  Db db) 
        throws DbException {
        Dbt key = encodeDbt(oid, SIZE_OBJECTID);
        db.delete(null, key, Db.DB_AUTO_COMMIT);
    }

    protected void addLegalHold(NewObjectIdentifier oid,
                                String legalHold, Db db)
    throws DbException, EMDException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Adding (" + oid + ",[" + legalHold +
                     "]) to the hold system cache");
        }

        Dbt key = new Dbt();
        try {
            byte[] bytes = legalHold.getBytes("UTF8");
            key.setData(bytes);
            key.setSize(bytes.length);
	} catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }

        Dbt data = encodeDbt(oid, SIZE_OBJECTID);
        db.put(null, key, data, Db.DB_AUTO_COMMIT);
    }
    
    protected void removeLegalHold(NewObjectIdentifier oid,
                                   String legalHold, Db db) 
        throws DbException, EMDException {

        Dbt key = new Dbt();
        try {
            byte[] bytes = legalHold.getBytes("UTF8");
            key.setData(bytes);
            key.setSize(bytes.length);
	} catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }

        Dbt data = encodeDbt(oid, SIZE_OBJECTID);

        // First retrieve the pair we want to delete
        Dbc cursor = null;
        int errorCode;

        try {
            DbTxn txn = db.getDbEnv().txnBegin(null, 0);
            cursor = db.cursor(txn, 0);
            errorCode = cursor.get(key, data, Db.DB_GET_BOTH);

            // Delete the first instance we find of this key/data pair
            if (errorCode != Db.DB_NOTFOUND) {
                cursor.delete(0);
                cursor.close();
                txn.commit(0);

                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Removed (" + oid + ",[" + legalHold +
                             "]) from the hold system cache");
                }
            } else {
                cursor.close();
                txn.abort();
            }

            cursor = null;

        } catch (DbException e) {
            EMDException newe = new EMDException("Failed to delete the DB " +
                                                 "entry [" + e.getMessage() +
                                                 "]");
            newe.initCause(e);
            throw newe;
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (DbException ignored) {}
        }
    }

    private void getObjects(MDOutputStream output,
                            PerDiskRecord disk,
                            int layoutMapId,
                            ArrayList attributes,
                            NewObjectIdentifier cookie,
                            int maxResults, int timeout,
                            boolean forceResults)
        throws EMDException, DbException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Running the getObjects query for map "+layoutMapId);
        }

        Db db = (Db)disk.getDb(INDEX_MAPID);
        if (db == null) {
            throw new EMDException("The "+INDEX_MAPID+" index could not be found");
        }
        Dbt key = new Dbt();
        Dbt pkey = null;
        Dbt data = new Dbt();

        if (cookie == null) {
            pkey = new Dbt();
        } else {
            pkey = encodeDbt(cookie, SIZE_OBJECTID);
        }

        // Encode the mapid
        try {
            TupleOutput toutput = new TupleOutput();
            toutput.writeInt(layoutMapId);
            byte[] bytes = toutput.toByteArray();
            key.setData(bytes);
            key.setSize(bytes.length);
        } catch (IOException e) {
            EMDException newe = new EMDException("Failed to encode the entry to the index ["
                                                 +e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        Dbc cursor = null;
        int errorCode;

        try {
            cursor = db.cursor(null, 0);

            if (cookie == null) {
                errorCode = cursor.get(key, pkey, data, Db.DB_SET);
            } else {
                errorCode = cursor.get(key, pkey, data, Db.DB_GET_BOTH);
            }

	    int resultNum = 0;
            while (errorCode != Db.DB_NOTFOUND &&
                   (maxResults > 0 ? resultNum < maxResults : true)) {
                NewObjectIdentifier oid = (NewObjectIdentifier)decodeDbt(pkey);
                Object extraInfo = null;

                if ( (cookie == null)
                     || (oid.compareTo(cookie) > 0) ) {

                    if (attributes != null) {
                        // QueryPlus, also send the SystemMetadata
                        SystemMetadata systemMetadata = (SystemMetadata)decodeDbt(data);
                        extraInfo = systemMetadata;
                    }
                    output.sendObject(new MDHit(oid, extraInfo));
                    resultNum++;
                }
                
                errorCode = cursor.get(key, pkey, data, Db.DB_NEXT_DUP);
            }
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (DbException ignored) {
            }
        }
    }

    private void getMDsThatPoint(MDOutputStream output,
                                 PerDiskRecord disk,
                                 NewObjectIdentifier thatOid,
                                 ArrayList attributes,
                                 NewObjectIdentifier cookie,
                                 int maxResults, int timeout,
                                 boolean forceResults)
        throws EMDException, DbException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Running the getMDsThatPoint query for oid "+thatOid);
        }

        Db db = (Db)disk.getDb(INDEX_MDID);
        if (db == null) {
            throw new EMDException("The "+INDEX_MDID+" index could not be found");
        }
        Dbt key = new Dbt();
        Dbt pkey = null;
        Dbt data = new Dbt();

        if (cookie == null) {
            pkey = new Dbt();
        } else {
            pkey = encodeDbt(cookie, SIZE_OBJECTID);
        }
	
        encodeDbt(thatOid, SIZE_OBJECTID, key);

        Dbc cursor = null;
        int errorCode;

        try {
            cursor = db.cursor(null, 0);

            if (cookie == null) {
                errorCode = cursor.get(key, pkey, data, Db.DB_SET);
            } else {
                errorCode = cursor.get(key, pkey, data, Db.DB_GET_BOTH);
            }

            int resultNum=0;
            while (errorCode != Db.DB_NOTFOUND &&
                   (maxResults > 0 ? resultNum < maxResults : true)) {
                NewObjectIdentifier oid = (NewObjectIdentifier)decodeDbt(pkey);

                if ( (cookie == null)
                     || (oid.compareTo(cookie) > 0) ) {

                    output.sendObject(new MDHit(oid, null));
                    resultNum++;
                }
                
                errorCode = cursor.get(key, pkey, data, Db.DB_NEXT_DUP);
            }
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (DbException ignored) {
            }
        }
    }

    private void checkOid(MDOutputStream output,
                          PerDiskRecord disk,
                          NewObjectIdentifier thatOid,
                          boolean restoredOnly,
                          ArrayList attributes,
                          NewObjectIdentifier cookie,
                          int maxResults, int timeout,
                          boolean forceResults)
        throws EMDException, DbException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Running the checkOid query for oid "+thatOid);
        }

        Db db = (Db)disk.getDb(MAINDB_NAME);
        if (db == null) {
            throw new EMDException("The "+MAINDB_NAME+" index could not be found");
        }
        Dbt key = new Dbt();
        Dbt pkey = null;
        Dbt data = new Dbt();

        if (cookie == null) {
            pkey = new Dbt();
        } else {
            pkey = encodeDbt(cookie, SIZE_OBJECTID);
        }
	
        encodeDbt(thatOid, SIZE_OBJECTID, key);

        Dbc cursor = null;
        int errorCode;

        try {
            cursor = db.cursor(null, 0);

            if (cookie == null) {
                errorCode = cursor.get(key, data, Db.DB_SET);
            } else {
                errorCode = cursor.get(key, data, Db.DB_GET_BOTH);
            }

            int resultNum=0;
            while (errorCode != Db.DB_NOTFOUND &&
                   (maxResults > 0 ? resultNum < maxResults : true)) {
                NewObjectIdentifier oid = (NewObjectIdentifier)decodeDbt(key);
                Object extraInfo = null;
                
                if ( (cookie == null)
                     || (oid.compareTo(cookie) > 0) ) {
                    
                    SystemMetadata systemMetadata = (SystemMetadata)decodeDbt(data);

                    //
                    // Check if object has been restored, if not ignore it and
                    // Datadoc (pop syscache) will repopulate
                    //
                    if (!restoredOnly || systemMetadata.isRestored()) {
                        if (attributes != null) {
                            // QueryPlus, also send the SystemMetadata
                            extraInfo = systemMetadata;
                        }
                        
                        output.sendObject(new MDHit(oid, extraInfo));
                        resultNum++;
                    }
                }
                errorCode = cursor.get(key, data, Db.DB_NEXT_DUP);
            }
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (DbException ignored) {
            }
        }
    }

    private void isNotRestored(MDOutputStream output, PerDiskRecord disk) 
            throws EMDException, DbException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Running the isRestored query.");
        }

        Db db = (Db) disk.getDb(MAINDB_NAME);
        if (db == null) {
            throw new EMDException("The " + MAINDB_NAME
                    + " index could not be found");
        }
        Dbt key = new Dbt();
        Dbt data = new Dbt();

        Dbc cursor = null;
        int errorCode;
        
        try {
            cursor = db.cursor(null, 0);
            errorCode = cursor.get(key, data, Db.DB_SET_RANGE);

            while (errorCode != Db.DB_NOTFOUND) {
                NewObjectIdentifier oid = (NewObjectIdentifier)decodeDbt(key);
                SystemMetadata systemMetadata = (SystemMetadata)decodeDbt(data);

                if (!systemMetadata.isRestored()) {
                    output.sendObject(new MDHit(oid, systemMetadata));
                    // one false is enough!
                    return;
                }
                
                errorCode = cursor.get(key, data, Db.DB_NEXT_DUP);
            }
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (DbException ignored) {
            }
        }
    }
    
    private void setNotRestored(MDOutputStream output, PerDiskRecord disk)
            throws EMDException, DbException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Running the isRestored query.");
        }

        Db db = (Db) disk.getDb(MAINDB_NAME);
        
        if (db == null) {
            throw new EMDException("The " + MAINDB_NAME
                    + " index could not be found");
        }
        
        Dbt key = new Dbt();
        Dbt data = new Dbt();

        Dbc cursor = null;
        int errorCode;

        try {
            cursor = db.cursor(null, 0);
            errorCode = cursor.get(key, data, Db.DB_SET_RANGE);

            while (errorCode != Db.DB_NOTFOUND) {
                NewObjectIdentifier oid = (NewObjectIdentifier) decodeDbt(key);
                SystemMetadata systemMetadata = (SystemMetadata) decodeDbt(data);
               
                systemMetadata.setRestored(false);
                setMetadata(oid, systemMetadata, db);
                
                errorCode = cursor.get(key, data, Db.DB_NEXT);
            }
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (DbException ignored) {
            }
        }
    }
    
    private void getChanges(MDOutputStream output, 
                            PerDiskRecord disk, 
                            long t1,
                            long t2, 
                            ArrayList attributes,
                            EMDCookie cookie, 
                            int maxResults, 
                            int timeout,
                            boolean forceResults) 
            throws EMDException, DbException {
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Running the getChanges query for data between " + 
                     t1 + " and " + t2);
        }

        Db db = (Db)disk.getDb(INDEX_ATIMEID);
       
        if (db == null) {
            throw new EMDException("The " + INDEX_ATIMEID + 
                                   " index could not be found");
        }
        
        Dbt key = new Dbt();
        Dbt pkey = new Dbt();
        Dbt data = new Dbt();
      
        // Encode the t1
        long cookieAtime = t1;
        NewObjectIdentifier cookieOID = NewObjectIdentifier.NULL;
        
        if (cookie != null)  {
            cookieAtime = cookie.getLastATime();
            cookieOID = cookie.getLastOid(); 
        }
        ATimeKeyCreate.createDbt(key, cookieOID, cookieAtime);
            
        Dbc cursor = null;
        int errorCode;
        int resultNum = 0; 
        try {
            cursor = db.cursor(null, 0);
            errorCode = cursor.get(key, pkey, data, Db.DB_SET_RANGE);

            while (errorCode != Db.DB_NOTFOUND  &&
                   (maxResults > 0 ? resultNum < maxResults : true)) {
                NewObjectIdentifier oid = (NewObjectIdentifier)decodeDbt(pkey);
                byte[] bytes = key.getData();
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                new ByteBufferCoder(buffer).decodeCodable();
                long atime = buffer.getLong();
                Object extraInfo = null;

                if ((cookie == null) || 
                    ((atime >= cookieAtime) && !oid.equals(cookie.getLastOid())) ) {
                    if (atime > t2) {
                        break;
                    }
                   
                    if (attributes != null) {
                        // QueryPlus, also send the SystemMetadata
                        SystemMetadata systemMetadata = (SystemMetadata)decodeDbt(data);
                        extraInfo = systemMetadata;
                    }
                        
                    output.sendObject(new MDHitByATime(oid, extraInfo, atime));
                    resultNum++;
                } else if (LOG.isLoggable(Level.FINE))
                    LOG.fine("Skipping: " + oid);
                
                errorCode = cursor.get(key, pkey, data, Db.DB_NEXT);
            }
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (DbException ignored) {
            }
        }
    }

    private void queryHold(MDOutputStream output,
                           PerDiskRecord disk,
                           String hold,
                           ArrayList attributes,
                           NewObjectIdentifier cookie,
                           int maxResults, int timeout,
                           boolean forceResults)
        throws EMDException, DbException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Running the queryHold query for legal hold [" +
                     hold + "]");
        }

        Db db = (Db)disk.getDb(HOLDDB_NAME);

        if (db == null) {
            throw new EMDException("The "+ HOLDDB_NAME +
                                   " DB could not be found");
        }
        Dbt key = new Dbt();

        if (hold != null) {
            try {
                byte[] bytes = hold.getBytes("UTF8");
                key.setData(bytes);
                key.setSize(bytes.length);
            } catch (UnsupportedEncodingException uee) {
                throw new RuntimeException(uee);
            }
        }

        Dbt data = null;

        if (cookie == null) {
            data = new Dbt();
        } else {
            data = encodeDbt(cookie, SIZE_OBJECTID);
            if (LOG.isLoggable(Level.FINE)) {            
                LOG.fine("Querying legal holds with cookie " + cookie);
            }
        }

        Dbc cursor = null;
        int errorCode;

        try {
            cursor = db.cursor(null, 0);

            if (cookie == null) {
                errorCode = cursor.get(key, data, Db.DB_SET);
            } else {
                errorCode = cursor.get(key, data, Db.DB_GET_BOTH);
            }

            int resultNum = 0;
            while (errorCode != Db.DB_NOTFOUND &&
                   (maxResults > 0 ? resultNum < maxResults : true)) {
                byte[] bytes = key.getData();
                String legalHold = new String(bytes, "UTF8");
                NewObjectIdentifier oid = (NewObjectIdentifier)decodeDbt(data);
                Object extraInfo = null;

                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("System cache query found [" + oid + ", [" +
                         legalHold + "]");
                }

                if ( (cookie == null)
                     || (oid.compareTo(cookie) > 0) ) {

                    if (attributes != null) {
                        extraInfo = legalHold;
                    }
                    output.sendObject(new MDHit(oid, extraInfo));
                    resultNum++;
                }

                errorCode = cursor.get(key, data, Db.DB_NEXT_DUP);
            }
        } catch (IOException e) {
            EMDException newe = new EMDException("Failed to query the DB " +
                                                 "index [" + e.getMessage() +
                                                 "]");
            newe.initCause(e);
            throw newe;
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (DbException ignored) {}
        }
    }

    protected void queryPlus(MDOutputStream output,
                             PerDiskRecord disk,
                             String nQuery,
                             ArrayList attributes,
                             EMDCookie cookie,
                             int maxResults, int timeout,
                             boolean forceResults)
        throws EMDException {

        String dbName = null; 
        try {
	        SystemCacheQuery query = new SystemCacheQuery(nQuery);
	        switch (query.getType()) {
	        case QUERY_CODE_GETOBJECTS:
                dbName = MAINDB_NAME;
	            int layoutMapId = Integer.parseInt(query.getArguments()[1]);
	            getObjects(output, disk, layoutMapId, attributes,
                           (cookie != null ? cookie.getLastOid() : null),
                           maxResults, timeout, forceResults);
	            break;
	        case QUERY_CODE_GETMDSTHATPOINT:
                dbName = MAINDB_NAME;
	            NewObjectIdentifier thatOid = 
	                NewObjectIdentifier.fromExternalHexString(query.getArguments()[1]);
	            getMDsThatPoint(output, disk, thatOid, attributes,
                                (cookie != null ? cookie.getLastOid() : null),
	                             maxResults, timeout, forceResults);
	        case QUERY_CODE_CHECKOID:
                dbName = MAINDB_NAME;
	            NewObjectIdentifier thisOid = 
                NewObjectIdentifier.fromExternalHexString(query.getArguments()[1]);
                boolean restoredOnly =
                  Boolean.valueOf(query.getArguments()[2]).booleanValue();
                checkOid(output, disk, thisOid, restoredOnly, attributes,
                         (cookie != null ? cookie.getLastOid() : null), 
                         maxResults, timeout, forceResults);
                break;
	        case QUERY_CODE_GETCHANGES:
                dbName = MAINDB_NAME;
	            //TODO: check for number format exception
	            long t1 = new Long(query.getArguments()[1]).longValue();
	            long t2 = new Long(query.getArguments()[2]).longValue();
	            
	            getChanges(output,disk,t1,t2,attributes,cookie,
	                       maxResults,timeout,forceResults);
	            break;
	        case QUERY_CODE_HOLD:
                dbName = HOLDDB_NAME;
	            // Make a single legal hold string from all the arguments
	            String hold = null;
	            String[] args = query.getArguments();
	            if (args.length > 1) {
	                for (int i=1; i<args.length; i++) {
	                    if (i == 1) {
	                        hold = args[i];
	                    } else {
	                        hold = hold + " " + args[i];
	                    }
	                }
	            }
	            queryHold(output, disk, hold, attributes, 
                         (cookie != null ? cookie.getLastOid() : null), 
	                      maxResults, timeout, forceResults);
	            break;
	        case QUERY_CODE_ISRESTORED:
                dbName = MAINDB_NAME;
	            isNotRestored(output, disk);
	            break;
	        case QUERY_CODE_SETNOTRESTORED:
                dbName = MAINDB_NAME;
	            setNotRestored(output, disk);
	            break;
	        }
        } catch (DbException e) {
            handleException(e, disk, "query", dbName);
        }
    }
}
