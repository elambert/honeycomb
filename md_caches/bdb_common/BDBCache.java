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



import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.sleepycat.db.DbBtreeCompare;
import com.sleepycat.db.DbEnv;
import com.sleepycat.db.Db;
import com.sleepycat.db.DbException;
import com.sleepycat.db.DbRunRecoveryException;
import com.sleepycat.db.Dbc;
import com.sleepycat.db.Dbt;
import com.sleepycat.db.DbSecondaryKeyCreate;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.remote.StreamHead;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import com.sun.honeycomb.emd.remote.InMemoryMDStream;
import com.sun.honeycomb.emd.server.MDService;
import com.sun.honeycomb.emd.server.ProcessingCenter;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;

public abstract class BDBCache 
    implements CacheInterface {
    
    /**********************************************************************
     *
     * Private fields and methods
     *
     **********************************************************************/

    private static final Logger LOG = Logger.getLogger(BDBCache.class.getName());
    protected static final String MAINDB_NAME   = "main";
    protected static final String HOLDDB_NAME   = "hold";

    private static final String DB_CONFIG_FILENAME="DB_CONFIG"; 
    private static final String DB_CONFIG_CONTENTS="#set cache size to 16MB\n" +
                                                   "set_cachesize 0 16777216 1\n";
    
    private final int BDB_CREATE_RETRIES = 2;
    
    /*
     * Why 5 logs you may ask ? because through testing I saw the least
     * number of caches being lost after an abrupt powerfailure and it's a
     * reasonable amount of space to waste per cache:
     * 
     * 5 logs * 10MB each log = 50MB per disk * 64 disks = 3.2 GB for 
     * all of the logging necessary by BDB
     * 
     */
    private final int MAX_LOGS = 5; 

    /**********************************************************************
     *
     * IndexEntry class
     *
     **********************************************************************/

    private static class IndexEntry {
        private String name;
        private Db db;
        private ArrayList children;
        
        private IndexEntry(String nName,
                           Db nDb) {
            name = nName;
            db = nDb;
            children = new ArrayList();
        }
    }
    
    /**********************************************************************
     *
     * PerDiskRecord class
     *
     **********************************************************************/
  
    private void createDirectory(String path) { 
        File localPath = new File(path);
        if (!localPath.isDirectory()) {
            localPath.delete();
        }
        if (!localPath.exists()) {
            localPath.mkdir();
        }
    }
   
    /** 
     * This method will traverse all the elements by reading them back, We must
     * justify the performance of this to not take too long for whenthe BDB maybe
     * completely full and be certain that this overhead is justified by the fact
     * that would catch corruption at startup and not during runtime of this 
     * cache.
     * 
     * @param dbEnv
     * @throws DbRunRecoveryException
     */
    private void checkDBIsGood(Db db) throws DbException { 
        Dbt key = new Dbt(); 
        Dbt data = new Dbt();
        Dbc cursor = null;
        int res;

        /*
         * After being able to iterate through all values and retrieve the data
         * we know all the entries are fine, costly but effective.
         */
        long counter = 0;
        long start = System.currentTimeMillis();
        try {
            cursor = db.cursor(null, 0);
	        res = cursor.get(key, data, Db.DB_LAST);
            
	        while (res != Db.DB_NOTFOUND) {
	            res = cursor.get(key, data, Db.DB_PREV);
                counter++;
                
                if (counter > 5000) 
                    break;
	        }
            long stop = System.currentTimeMillis();
            
            if (LOG.isLoggable(Level.INFO))
                LOG.info(db.getFileName() + " passed DB check in " + 
                         (stop - start) + "ms.");
            
        } catch (DbException e) { 
            long stop = System.currentTimeMillis();
            LOG.log(Level.WARNING,"Bad BDB detected in " + (stop-start) +
                                  "ms, will proceed to recovery.",e);
            throw e;
        } finally { 
            if (cursor != null)
                cursor.close();
        }
    }
   
   
    /**
     * Private class used by BDB to log all errors to the java Logger
     */
    private static class LoggerOutputStream extends OutputStream {
        private static int BUFFER_LENGTH = 1024;
        private byte[] buffer = null;
        private int count = 0;

        private static LoggerOutputStream _instance = null;

        public static LoggerOutputStream getInstance() {
            if (_instance == null) {
                _instance = new LoggerOutputStream();
            }
            return _instance;
        }

        private LoggerOutputStream() {
            buffer = new byte[BUFFER_LENGTH];
        }

        public void write(int b) throws IOException {
            if (b == '\n' || count > BUFFER_LENGTH) {
                LOG.warning(new String(buffer,0,count));
                count = 0;
            } else {
                buffer[count++] = (byte) b;
            }
        }
    }

    /**
     * Attempt to open the cache and if it doens't open run recovery to see if
     * we can still rescue this cache.
     * 
     * @param dbEnv
     * @param path
     * @param flags
     * @throws DbException
     * @throws FileNotFoundException
     */
    private IndexEntry openCache(DbEnv dbEnv, 
                                 String path, 
                                 String dbName, 
                                 String filename,
                                 int flags,
                                 int envFlags,
                                 boolean doDB_DUPSORT) throws FileNotFoundException, DbException { 
        
        IndexEntry db = null;
        long start = 0, stop = 0;
        start = System.currentTimeMillis();
        // create the path for this cache if it's not already there...
        createDirectory(path);

        // make sure to create DB_CONFIG file with necessary BDB config options.
        File db_config = new File(path + File.separator + DB_CONFIG_FILENAME);
        try { 
            FileOutputStream fos = new FileOutputStream(db_config);
            fos.write(DB_CONFIG_CONTENTS.getBytes());
            fos.close();
        } catch (IOException e) { 
            db_config.delete();
            LOG.log(Level.SEVERE,"Unable to create DB_CONFIG file for cache: " + path,e);
        }

        dbEnv.open(path, envFlags, 0);
        dbEnv.setErrorStream(LoggerOutputStream.getInstance());
        
        db = new IndexEntry(dbName, new Db(dbEnv, 0)); 
          
        if (doDB_DUPSORT)
            db.db.setFlags(Db.DB_DUPSORT); // allow duplicates
           
        db.db.open(null, filename, null, Db.DB_BTREE, flags, 0);
        stop = System.currentTimeMillis();
        LOG.info("BDB took " + (stop-start) + "ms to open database: " + dbName);
       
        // BDB opened now lets make sure it is really open 
        checkDBIsGood(db.db);
        return db;
    }
    
    protected class PerDiskRecord {
        private DbEnv dbEnv;
        private DbEnv holdDbEnv;
        private IndexEntry mainDb;
        private IndexEntry holdDb;
        private HashMap indexLookup;
        public RWLock rwLock;
        public RWLock holdRwLock;
       
        private Disk _disk = null;
        private String _mdpath = null;
        
        public void flagForRecovery(DbException e, Disk disk) throws EMDException {
            LOG.info("Corrupted cache detected at runtime :(, flagging cache.");
            MDService.setCacheCorrupted(disk, true);
            
            EMDException newe  = null;
            newe = new EMDException("BDB Operation failed on " 
                                    + disk.getPath()+" for cache "
                                    + getCacheId()+" ["
                                    + e.getMessage()+"]");
            
            newe.initCause(e);
            throw newe;
        }
        
        private PerDiskRecord(String MDPath, Disk disk) throws FileNotFoundException, DbException {
            indexLookup = new HashMap();
            rwLock = new RWLock();
            
            _mdpath = MDPath;
            _disk = disk;

            // create the db environment
            try {
                dbEnv = new DbEnv(0);
            } catch (DbException e) {
                /* DbException can only come from the new DbEnv()... if that 
                 * happens something is really fishy, the BDB documentation says 
                 * the following:
                 * 
                 * public DbEnv(int flags)
                 *    throws DbException
                 *     
                 *  The constructor creates the DbEnv object. The constructor 
                 *  allocates memory internally; calling the DbEnv.close or 
                 *  DbEnv.remove methods will free that memory.
                 *   
                 *  {... crap I don't care for}
                 * 
                 *  Throws:
                 *      DbException - Signals that an exception of some sort 
                 *                    has occurred.
                 *                    
                 *  So my only conclusion from the explanation of what is done 
                 *  in the constructor is that we BDB may not be able to allocate
                 *  memory and therefore would throw an exception.     
                 *  
                 *  So I'll throw a RuntimeException making the MDService 
                 *  restart in the hopes that this fixes any memory issue we 
                 *  must be experiencing...
                 */
                throw new RuntimeException("Failure to init BDB",e);
            }
            
            String cachePath = MDPath + File.separatorChar + MAINDB_NAME;
            mainDb = openCache(dbEnv, 
                               cachePath, 
                               MAINDB_NAME, 
                               getCacheId() + ".bdb",
                               dbFlags,
                               dbEnvFlags,
                               false);
        
            indexLookup.put(MAINDB_NAME, mainDb);


	    /********************************************************
	     *
	     * Bug 6554027 - hide retention features
	     *
	     *******************************************************/
	    /*
            // create the hold db environment
            try {
                holdDbEnv = new DbEnv(0);
            } catch (DbException e) {
                // same as above situation with failure to do a new DbEnv(0)
                throw new RuntimeException("Failure to init BDB",e);
            }
           
            cachePath = MDPath + File.separatorChar + HOLDDB_NAME;
            holdDb = openCache(holdDbEnv, 
                               cachePath, 
                               HOLDDB_NAME, 
                               HOLDDB_NAME + ".bdb",
                               holdDbFlags,
                               holdDbEnvFlags,
                               true);
            
            indexLookup.put(HOLDDB_NAME, holdDb);
	    */
        }

        protected void addDb(IndexEntry parent,
                             IndexEntry child) 
            throws EMDException {
            parent.children.add(child);
            indexLookup.put(child.name, child);
        }

        protected IndexEntry getEntry(String name) 
            throws EMDException {
            IndexEntry entry = (IndexEntry)indexLookup.get(name);
            if (entry == null) {
                String home = "Unknown location";
                if (dbEnv != null) {
                    try {
                        home = dbEnv.getDbEnvHome();
                    } catch (DbException ignored) {
                    }
                }
                throw new EMDException("Db "+name+" does not exist ["+
                                       home+"]");
            }

            return(entry);
        }

        public Db getDb(String name) 
            throws EMDException {
            IndexEntry entry = (IndexEntry)indexLookup.get(name);
            if (entry == null) {
                String home = "Unknown location";
                if (dbEnv != null) {
                    try {
                        home = dbEnv.getDbEnvHome();
                    } catch (DbException ignored) {
                    }
                }
                throw new EMDException("Db "+name+" does not exist ["+
                                       home+"]");
            }
            return(entry.db);
        }
        
        public Disk getDisk() {
            return _disk;
        }
        
        public String getPath() { 
            return _mdpath;
        }
    }

    /**********************************************************************
     *
     * Main class
     *
     **********************************************************************/
    
    private int dbEnvFlags;
    private int dbFlags;
    private int holdDbEnvFlags;
    private int holdDbFlags;
    private HashMap disks;

    private static void wipeDb(String MDPath) {
        File top = new File(MDPath);
        if (top.isDirectory())
            LOG.warning("***** The BDB database on [" +
                        MDPath + "] is being wiped *****");
        wipe(top);
    }
        
    private static void wipe(File top) { 
        File[] children = top.listFiles();
        /*
         * need to check for null because listFiles will return null if the 
         * path is not a directory and if there are I/O errors. (read the docs)
         */
        if (children != null) {
	        for (int i=0; i<children.length; i++) {
                LOG.info("Handling " + children[i].getAbsoluteFile());
                if (children[i].isFile()) {
                    children[i].delete();
                    LOG.info("[" + children[i].getAbsolutePath() + "] has been deleted");
                } else if (children[i].isDirectory()) { 
                    wipe(children[i]);
                    children[i].delete(); 
                }
	        }
        } else
            LOG.info("Nothing to wipe: " + top.getAbsoluteFile());
    }

    public PerDiskRecord getDiskRecord(Disk disk)
        throws EMDException {
        PerDiskRecord diskRecord = null;
        
        synchronized (disks) {
            diskRecord = (PerDiskRecord)disks.get(disk);
        }

        if (diskRecord == null) {
            if (LOG.isLoggable(Level.FINE)) {
                StringBuffer log = new StringBuffer();
                log.append("Available disks are :");
                synchronized (disks) {
                    Iterator ite = disks.keySet().iterator();
                    while (ite.hasNext()) {
                        Disk d = (Disk)ite.next();
                        log.append(" ["+d.getPath()+"-"+d.hashCode()+"]");
                    }
                }
                LOG.fine(log.toString());
            }
            throw new EMDException("Disk "+disk.getPath()+" ["+
                                   disk.hashCode()+"] has not been registered / initialized");
        }
        
        return(diskRecord);
    }

    private PerDiskRecord[] getAllDisks() 
        throws EMDException {
        PerDiskRecord[] result = null;
         
        synchronized (disks) {
            result = new PerDiskRecord[disks.keySet().size()];
            Iterator keys = disks.keySet().iterator();
            for (int i=0; keys.hasNext(); i++) {
                result[i] = (PerDiskRecord)disks.get(keys.next());
            }
        }

        return(result);
    }
    
    private PerDiskRecord[] getDisks(ArrayList inputDisks) 
        throws EMDException {

        if (inputDisks == null) {
            return(getAllDisks());
        }
        
        ArrayList result = new ArrayList();

        synchronized (disks) {
            for (int i=0; i<inputDisks.size(); i++) {
                PerDiskRecord record = (PerDiskRecord)disks.get(inputDisks.get(i));
                if (record != null) {
                    result.add(record);
                }
            }
        }

        PerDiskRecord[] res = new PerDiskRecord[result.size()];
        result.toArray(res);
        
        return(res);
    }
   
    public void doPeriodicWork(Disk disk) throws EMDException {
        PerDiskRecord record = getDiskRecord(disk);
        try {
            if (LOG.isLoggable(Level.FINE))
                LOG.fine("Checkpointing BDB on disk " + 
                         disk.getId().toStringShort());
            
            record.dbEnv.txnCheckpoint(0,0,Db.DB_FORCE);
            cleanLogs(record.dbEnv);

	    /********************************************************
	     *
	     * Bug 6554027 - hide retention features
	     *
	     *******************************************************/
            // record.holdDbEnv.txnCheckpoint(0,0,Db.DB_FORCE);
            // cleanLogs(record.holdDbEnv);

        } catch (DbException e) {
            throw new EMDException("checkpointing failed.",e);
        }
    }
    
    private void cleanLogs(DbEnv dbEnv) throws DbException { 
        String[] logs = dbEnv.logArchive(Db.DB_ARCH_ABS | Db.DB_ARCH_LOG);
       
        if (logs.length > MAX_LOGS) {
	        for(int i = 0; i < logs.length-5; i++){
	            LOG.info("Purging log: " + logs[i]);
	            new File(logs[i]).delete();
	        }
        }
    }
    
    private void closeDb(Db db) {
        boolean closed = false;
        
        try {
            LOG.info("The database ["+db.getFileName()+"] is being closed");
        } catch (DbException ignored) {
        }
        
        try {
            // Try to flush and close first
            db.close(0);
            closed = true;
        } catch (DbException e) {
            LOG.info("Failed to close the database. Trying without flushing data ["+
                     e.getMessage()+"]");
        }
        
        if (!closed) {
            try {
                db.close(Db.DB_NOSYNC);
            } catch (DbException e) {
                LOG.log(Level.SEVERE,
                        "Failed to close database for cache "
                        + this.getCacheId() 
                        + " ["+ e.getMessage()+ "]",
                        e);
            } catch (IllegalArgumentException e){ 
                /*
                 * BDB throws this sometimes on corrupted caches so we should
                 * just ignore like we do above.
                 */
                LOG.log(Level.SEVERE,
                        "Failed to close database for cache "
                        + this.getCacheId() 
                        + " ["+ e.getMessage()+ "]",
                        e);
            }
        }
    }

    private void closeDbRec(IndexEntry entry) {
        for (int i=0; i<entry.children.size(); i++) {
            IndexEntry child = (IndexEntry)entry.children.get(i);
            closeDbRec(child);
        }
        closeDb(entry.db);
    }

    /**********************************************************************
     *
     * Implemented cacheInterface API
     *
     **********************************************************************/

    public void start() throws EMDException {
        // Nothing to do
    }
    
    public void stop() throws EMDException {
        // Close all the databases and environments
        synchronized (disks) {
            Disk[] diskSet = new Disk[disks.keySet().size()];
            disks.keySet().toArray(diskSet);
            for (int i=0; i<diskSet.length; i++) {
                Disk disk = diskSet[i];
                unregisterDisk(disk);
            }
        }
    }

    public void registerDisk(String MDPath,
                             Disk disk)
        throws EMDException {
        if (disk == null) {
            throw new EMDException("Invalid disk [null]");
        }

        String localPath = MDPath+"/"+getCacheId();
        PerDiskRecord diskRecord = null;
       
        long retries = 0;
        while (retries < BDB_CREATE_RETRIES) {
            createDirectory(localPath);
            try {
                diskRecord = new PerDiskRecord(localPath, disk);
                createIndexes(diskRecord);
                    
                // register the disk once we've verified main db and the indexes
				synchronized (disks) {
				    disks.put(disk, diskRecord);
				}
                
				LOG.info("Disk "+disk.getPath()+" has been registered for cache "+getCacheId());
				MDService.setCacheRunning(disk, true);
                return;
            } catch (EMDException e) {
                // if there's a failure to create the indexes then lets wipe the
                // whole db
                LOG.log(Level.WARNING,
                        "Errors recreating cache, lets wipe and retry");
                wipe(disk);
            } catch (DbException e) {
                // if there's a failure to create the indexes then lets wipe the
                // whole db
                LOG.log(Level.WARNING,
                        "Errors recreating cache, lets wipe and retry");
                wipe(disk);
            } catch (FileNotFoundException e) {
                // if there's a failure to create the indexes then lets wipe the
                // whole db
                LOG.log(Level.WARNING,
                        "Errors recreating cache, lets wipe and retry");
                wipe(disk);
            }
            retries++;
        }
        
        throw new RuntimeException("Unable to create caches correctly on " + 
                                   disk + " after retrying twice.");
    }

    public void unregisterDisk(Disk disk) throws EMDException {
        PerDiskRecord diskRecord = null;

        synchronized (disks) {
            diskRecord = (PerDiskRecord)disks.remove(disk);
        }
        
        if (diskRecord == null) {
            throw new EMDException("Disk "+disk.getPath()+" is not registered");
        }
        
        diskRecord.rwLock.getWriteLock();

        closeDbRec(diskRecord.mainDb);

	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/
        // closeDbRec(diskRecord.holdDb);

        try {
            diskRecord.dbEnv.logFlush(null);
            diskRecord.dbEnv.close(0);
        } catch (Exception e) {
	    EMDException newe = new EMDException("Failed to close the BDB environment on disk "+disk.getPath()
						 +" ["+e.getMessage()+"]");
	    newe.initCause(e);
	    throw newe;
        } finally {
            diskRecord.rwLock.releaseLock();
        }

        MDService.setCacheRunning(disk, false);
        LOG.info("The BDB environment for disk "+disk.getPath()+" has been freed");
    }
    
    public boolean isRegistered(Disk disk) {
        boolean result = false;
        
        synchronized (disks) {
            result = disks.containsKey(disk);
        }

        return(result);
    }

    protected void handleException(DbException e, 
                                 PerDiskRecord record,
                                 String nameOfMethod,
                                 String dbName) 
            throws EMDException { 
        Disk disk = record._disk;
        if (e instanceof DbRunRecoveryException) { 
            record.flagForRecovery(e, disk);
        } else {
            // Special cases of errors that need to lead to cleaning up of the
            // BDB on this disk.
            if (e.getErrno() == Db.DB_SECONDARY_BAD) {
                record.flagForRecovery(e, disk);
            }
            
            EMDException newe = new EMDException(nameOfMethod + 
                                                 " failed on disk " + 
                                                 disk.getPath() + " for cache " +
                                                 dbName + " [" + 
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }
    
    public void setMetadata(NewObjectIdentifier oid,
                            Object argument,
                            Disk disk)
        throws EMDException {
        PerDiskRecord record = getDiskRecord(disk);
        Db db = record.getDb(MAINDB_NAME);

        record.rwLock.getWriteLock();

        try {
            setMetadata(oid, argument, db);
        } catch (DbException e) {
            handleException(e, record, "setMetadata", getCacheId());
        } finally {
            record.rwLock.releaseLock();
        }
    }
    
    public void removeMetadata(NewObjectIdentifier oid,
                               Disk disk) 
        throws EMDException {
        PerDiskRecord record = getDiskRecord(disk);
        Db db = record.getDb(MAINDB_NAME);
        
        record.rwLock.getWriteLock();

        try {
            removeMetadata(oid, db);
        } catch (DbException e) {
            handleException(e, record, "removeMetadata", getCacheId());
        } finally {
            record.rwLock.releaseLock();
        }
    }

    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold,
                             Disk disk)
    throws EMDException {
        PerDiskRecord record = getDiskRecord(disk);
        Db db = record.getDb(HOLDDB_NAME);

        record.rwLock.getWriteLock();

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Add legal hold (" + oid + ", [" + legalHold +
                     "] into cache " + HOLDDB_NAME);
        }

        try {
            addLegalHold(oid, legalHold, db);
        } catch (DbException e) {
            handleException(e, record, "addLegalHold", HOLDDB_NAME);
        } finally {
            record.rwLock.releaseLock();
        }

        // Debug
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Added (" + oid + ",[" + legalHold +
                     "]) to the hold system cache " +
                     HOLDDB_NAME + " on disk " +
                     disk.getPath());
        }
    }
    
    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold,
                                Disk disk) 
                                throws EMDException {
        PerDiskRecord record = getDiskRecord(disk);
        Db db = record.getDb(HOLDDB_NAME);

        record.rwLock.getWriteLock();

        try {
            removeLegalHold(oid, legalHold, db);
        } catch (DbException e) {
            handleException(e, record, "removeLegalHold", HOLDDB_NAME);
        } finally {
            record.rwLock.releaseLock();
        }

	// Debug
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Removed (" + oid + ",[" + legalHold +
                     "]) from the hold system cache " +
                     HOLDDB_NAME + " on disk " +
                     disk.getPath());
        }
    }

    public void queryPlus(MDOutputStream output,
                          ArrayList inputDisks,
                          String query,
                          ArrayList attributes,
                          EMDCookie cookie,
                          int maxResults, int timeout,
                          boolean forceResults,
                          Object[] boundParameters)
        throws EMDException {
        
        PerDiskRecord[] disks = getDisks(inputDisks);
        StreamHead[] streams = new StreamHead[disks.length];
        for (int i=0; i<streams.length; i++) {
            InMemoryMDStream pipe = new InMemoryMDStream();
            streams[i] = new StreamHead(pipe);
            
            disks[i].rwLock.getReadLock();
            try {
                queryPlus(pipe, disks[i], query, attributes,
                          cookie, maxResults, timeout, forceResults);
            } finally {
                disks[i].rwLock.releaseLock();
            }
        }
        
        StreamHead.mergeStreams(streams, output, 0, maxResults);
    }
    
    public void selectUnique(MDOutputStream output,
                             String query,
                             String attribute,
                             String cookie,
                             int maxResults, int timeout,
                             boolean forceResults,
                             Object[] boundParameters)
        throws EMDException {
        throw new EMDException("Berkeley DB backends do not implement select unique operations");
    }
    
    /**********************************************************************
     *
     * Protected fields and methods
     * Abstract methods to be implemented by subclasses
     *
     **********************************************************************/
    
    protected static final int SIZE_UNKNOWN          = -1;
    // real size are indicated in comments; we just have to ensure this is
    // larger so that we allocate enough bytes to backup the ByteBuffer
    // Then we use the 'position()' so the real size is written in BDB
    //
    protected static final int SIZE_SYSTEMMETADATA   = 500; // 449 
    public static final    int SIZE_OBJECTID         = 100;  // 82;
    public static final    int SIZE_LEGALHOLD        = 100;

    public static class StringCodable
        implements Codable {
        private String value;
        
        public StringCodable() {
            value = null;
        }
        public StringCodable(String nValue) {
            value = nValue;
        }
        public String getValue() {
            return(value);
        }
        public void encode(Encoder encoder) {
            encoder.encodeString(value);
        }
        public void decode(Decoder decoder) {
            value = decoder.decodeString();
        }
    }
    
    public static Dbt encodeDbt(Codable codable,
                                int size) {
        Dbt result = new Dbt();
        encodeDbt(codable,size,result);
        return result;
    }

    public static void encodeDbt(Codable codable,
                                int size,
				Dbt result) {
        if (size == SIZE_UNKNOWN) {
            size = 0x1000; // 4k
        }

        byte[] bytes = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        new ByteBufferCoder(buffer).encodeCodable(codable);
        result.setData(bytes);
        result.setSize(buffer.position());
    }


    public static Dbt encodeDbts(Codable[] codable,
                                 int totalSize) {
        if (totalSize == SIZE_UNKNOWN) {
            totalSize = 0x1000; // 4k
        }
        
        byte[] bytes = new byte[totalSize];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        ByteBufferCoder coder = new ByteBufferCoder(buffer);
        coder.encodeInt(codable.length);
        for (int i=0; i<codable.length; i++) {
            coder.encodeCodable(codable[i]);
        }
        Dbt result = new Dbt();
        result.setData(bytes);
        result.setSize(buffer.position());
        return(result);
    }

    public static Codable decodeDbt(Dbt dbt) {
        ByteBuffer buffer = ByteBuffer.wrap(dbt.getData());
        Codable result = new ByteBufferCoder(buffer).decodeCodable();
        return(result);
    }
    
    public static Codable[] decodeDbts(Dbt dbt) {
        ByteBuffer buffer = ByteBuffer.wrap(dbt.getData());
        ByteBufferCoder coder = new ByteBufferCoder(buffer);
        int length = coder.decodeInt();
        Codable[] result = new Codable[length];
        for (int i=0; i<length; i++) {
            result[i] = coder.decodeCodable();
        }
        return(result);
    }
    
    protected BDBCache() {
        dbEnvFlags = Db.DB_INIT_MPOOL | Db.DB_PRIVATE | Db.DB_THREAD | 
                     Db.DB_CREATE | Db.DB_INIT_TXN | Db.DB_RECOVER ;
        dbFlags = Db.DB_CREATE | Db.DB_DIRTY_READ | Db.DB_THREAD | Db.DB_AUTO_COMMIT;

        holdDbEnvFlags = Db.DB_INIT_MPOOL | Db.DB_PRIVATE | Db.DB_THREAD | 
                         Db.DB_CREATE | Db.DB_INIT_TXN | Db.DB_INIT_LOCK | 
                         Db.DB_INIT_LOG | Db.DB_RECOVER ;
        holdDbFlags = Db.DB_CREATE | Db.DB_DIRTY_READ | Db.DB_THREAD | Db.DB_AUTO_COMMIT;

        disks = new HashMap();
    }

    protected void createIndex(String indexName,
                               String parentName,
                               DbSecondaryKeyCreate callback,
                               PerDiskRecord cookie,
                               DbBtreeCompare btreeCompare) 
        throws EMDException, DbException, FileNotFoundException {
        
        Db index = new Db(cookie.dbEnv, 0);
        index.setFlags(Db.DB_DUPSORT);
        
        if (btreeCompare != null)
            index.setBtreeCompare(btreeCompare);
        
        index.open(null, getCacheId()+"-"+indexName+".bdb",
                   null, Db.DB_BTREE, dbFlags, 0);
           
        IndexEntry parent = cookie.getEntry(parentName);
        parent.db.associate(null, index, callback, Db.DB_CREATE | Db.DB_AUTO_COMMIT);

        checkDBIsGood(index);
        
        cookie.addDb(parent, new IndexEntry(indexName, index));
    }

    public void sync(Disk disk) throws EMDException {
        Db db = getIndex(disk, MAINDB_NAME);
        try {
            db.sync(0);
        } catch (DbException e) {
            throw new EMDException("Error syncing db: " + MAINDB_NAME,e);
        } 
    }
    
    public void wipe(Disk disk) throws EMDException {
        wipeDb(ProcessingCenter.getMDPath(disk));
        MDService.setCacheComplete(disk, false);
        MDService.setCacheCorrupted(disk, false);
    }
   
    protected Db getIndex(Disk disk,
                          String indexName) 
        throws EMDException {
        PerDiskRecord diskRecord = null;

        synchronized (disks) {
            diskRecord = (PerDiskRecord)disks.get(disk);
        }

        if (diskRecord == null) {
            throw new EMDException("Invalid disk ["+disk+"]");
        }

        if (indexName == null) {
            indexName = MAINDB_NAME;
        }
        return(diskRecord.getDb(indexName));
    }

    protected abstract void createIndexes(PerDiskRecord cookie) 
                       throws EMDException, FileNotFoundException, DbException;

    protected abstract void setMetadata(NewObjectIdentifier oid,
                                        Object argument,
                                        Db db)
        throws DbException, EMDException;
    
    protected abstract void removeMetadata(NewObjectIdentifier oid,
                                           Db db) 
        throws DbException;
    
    protected abstract void addLegalHold(NewObjectIdentifier oid,
                                         String legalHold, Db db)
        throws DbException, EMDException;

    protected abstract void removeLegalHold(NewObjectIdentifier oid,
                                            String legalHold, Db db) 
        throws DbException, EMDException;

    protected abstract void queryPlus(MDOutputStream output,
                                      PerDiskRecord disk,
                                      String query,
                                      ArrayList attributes,
                                      EMDCookie cookie,
                                      int maxResults, int timeout,
                                      boolean forceResults)
        throws EMDException;
}
