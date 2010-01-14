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



package com.sun.honeycomb.fscache;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.Random;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import com.sleepycat.db.DbException;

public class BDBNativeFileCache extends FSCache
        implements PropertyChangeListener {

    private static final String PNAME_CACHE_TEMPDIR =
        "honeycomb.fscache.tempdir";
    private static final String PNAME_CACHE_CLEANUP_INTERVAL =
        "honeycomb.fscache.poll_interval";

    // These are for testing only
    private static final String DEFAULT_CACHE_TEMPDIR = "/tmp/fscache";
    private static final int DEFAULT_CACHE_CLEANUP_INTERVAL = 0;

    private static Logger LOG = 
        Logger.getLogger(BDBNativeFileCache.class.getName());

    private FSCacheObject root = null;

    private static Properties config = null;

    private HashMap volatileCache = null;
    private CursorCache cursorCache = null;

    private String cacheDir = DEFAULT_CACHE_TEMPDIR;

    private long pollInterval;
    private SweeperThread sweeperThread = null;

    public BDBNativeFileCache() {

    }

    public String toString() { return "BDB"; }
    public void dump(OutputStream os) throws IOException {
    }

    /** Directory listing: returns list of FSCacheObject */
    public List listChildren(Object group, FSCacheObject parent)
            throws FSCacheException {

        List children = new LinkedList();
        boolean foundChildren = false;
        byte[] parentBytes = null;
        try {
            parentBytes = parent.fileName().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        // Make sure object's children are all filled in
        parent.addChildren(group);

	try {
	    long cursor = cursorCache.getCursor(parent.fileName(),
                                                CursorCache.CURSOR_CHILDREN);

            for (;;) {
                byte[] bytes =
                    doCursorGet(parentBytes, cursor, !foundChildren);
                if (bytes == null)
                    break;
                foundChildren = true;

                FSCacheObject file = root.newObject();

                ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
                try {
                    file.readIn(new DataInputStream(bs));
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }

                children.add(file);
	    }
	}
        catch (DbException e) {
	    LOG.log(Level.WARNING, "FS cache doCursorGet failed", e);
	}
        finally {
            updateAncestorAtimes(group, parent);
	    if (!foundChildren)
                // We won't be using the cursor again
                cursorCache.remove(parent.fileName(),
                                   CursorCache.CURSOR_CHILDREN);
	}

        return children;
    }

    public boolean update(Object group, FSCacheObject file,
                          boolean modified)
            throws FSCacheException {
        boolean result = false;

        long txn = (group == null) ? 0 : ((Long)group).longValue();

        byte[] path = null;
        try {
            path = file.fileName().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            file.writeOut(new DataOutputStream(stream));
        }
        catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't serialize out", e);
        }
        byte[] data = stream.toByteArray();

        try {
            result = doUpdateEntry(txn, path, data);
        }
        catch (DbException e) {
            LOG.log(Level.WARNING, "Couldn't update", e);
            return false;
        }

	if (modified)
	    cursorCache.remove(file.fileName(), CursorCache.CURSOR_CHILDREN);

        return result;
    }

    public synchronized boolean remove(Object group, FSCacheObject file,
                                            boolean recursive)
            throws FSCacheException {

        return rm(group, file, recursive);
    }

    public boolean remove(Object group, byte[] oid) throws FSCacheException {
        throw new RuntimeException("unimplemented");
    }

    public boolean add(Object group, FSCacheObject file)
        throws FSCacheException {
	
        byte[] path = null;
        try {
            path = file.fileName().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            file.writeOut(new DataOutputStream(stream));
        }
        catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't serialize out", e);
        }
        byte[] hcfileBytes = stream.toByteArray();

	long txn = (group == null) ? 0
	    : ((Long)group).longValue();
	boolean result = true;

        LOG.info("FS cache adding \"" + file + "\"");

	try {
	    result = doAddEntry(txn, path, hcfileBytes);
	} catch (DbException e) {
	    LOG.log(Level.WARNING,
		    "FS cache failed to insert \"" + file.fileName() +
                    "\" in the filesystem cache: " + e.getMessage(),
		    e);
	}
	return(result);
    }
    
    public Object startGroup() throws FSCacheException {
        Object txn = null;

        if (true)
            return null;        // Don't know why transactions don't work

 	try {
 	    txn = new Long(createTransaction());
 	} catch (DbException e) {
 	    LOG.log(Level.WARNING, "Failed to create a transaction", e);
            throw new FSCacheException(e);
 	}

        return txn;
    }

    public void endGroup(Object group) throws FSCacheException {
	if (group == null)
	    return;

	try {
	    commitTransaction(((Long)group).longValue());
	}
        catch (DbException e) {
	    LOG.log(Level.WARNING, "FS cache failed to commit transaction", e);
            throw new FSCacheException(e);
	}
    }
    
    public FSCacheObject lookup(Object group, String path)
            throws FSCacheException {
        try {
            return resolve(group, pathSanityCheck(path));
        }
        catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't lookup", e);
            throw new FSCacheException(e);
        }
    }

    public void propertyChange(PropertyChangeEvent event) {
        String propName = event.getPropertyName();
        if (propName.equals(PNAME_CACHE_SIZE_MAX) ||
            propName.equals(PNAME_CACHE_SIZE_LOW))
            getCacheSizes();
    }

    public void initialize(FSCacheObject root, Properties config)
            throws FSCacheException {
        super.initialize(root, config);
        init();
    }

    private void init() throws FSCacheException {

        getCacheSizes();

        cacheDir = getProperty(PNAME_CACHE_TEMPDIR, DEFAULT_CACHE_TEMPDIR);
        pollInterval = getProperty(PNAME_CACHE_CLEANUP_INTERVAL,
                                      DEFAULT_CACHE_CLEANUP_INTERVAL) * 1000;

	volatileCache = new HashMap();
	cursorCache = new DbcNativeCache();

	File fsDir = setUpFSDir(cacheDir);
	int err = doInit(cacheDir, !(root instanceof TestCacheObject));
	
	if (err != 0) {
            String m = "FS cache Sleepycat DB init failed (" + err + ")";
	    LOG.severe(m);
            throw new FSCacheException(FSCacheException.FSERR_SERVERFAULT, m);
	}

        add(null, root);

        startSweeperThread();

        if (LOG.isLoggable(Level.INFO))
            LOG.info("FS cache temp. dir. \"" + cacheDir + "\", " +
                     "hi-water " + cacheSizeMax + ", " +
                     "lo-water " + cacheSizeLow);
    }

    /**********************************************************************
     *
     * Temporary cache API
     *
     **********************************************************************/
    
    public FSCacheObject volatileResolve(String path) throws FSCacheException {
	path = pathSanityCheck(path);
	FSCacheObject result = (FSCacheObject) volatileCache.get(path);

	if (result == null) {
	    throw new FSCacheException(FSCacheException.FSERR_NOENT,
				       "No such file ["+
				       path+"] in the volatile cache");
	}
	
	return(result);
    }
    
    public void volatileAddEntry(FSCacheObject entry) {
	volatileCache.put(entry.fileName(), entry);
    }
    
    public ArrayList volatileReaddir(FSCacheObject parent) {
	ArrayList result = new ArrayList();
	Iterator files = volatileCache.values().iterator();
	while (files.hasNext()) {
	    FSCacheObject file = (FSCacheObject) files.next();
	    if (parentOf(file.fileName()).equals(parent.fileName()))
		result.add(file);
	}
	
	return(result);
    }
    
    public void volatileCleanEntry(FSCacheObject file) {
	volatileCache.remove(file.fileName());
    }


    //////////////////////////////////////////////////////////////////////
    // Debugging support
    
    void dumpCache(int type) {
        boolean firstTime = true;

        long cursor = cursorCache.getCursor(null, type);

        try {
            for (long i = 1; ; i++) {
                try {
                    byte[] bytes = doCursorGet(null, cursor, firstTime);
                    firstTime = false;

                    if (bytes == null)
                        break;

                    FSCacheObject file = root.newObject();
                    ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
                    file.readIn(new DataInputStream(bs));

                    System.out.println(i + ". " + file.toString());
                }
                catch (Exception e) {
                    LOG.log(Level.SEVERE, "reading from cursor", e);
                }
            }
        }
        finally {
            cursorCache.remove(null, CursorCache.CURSOR_ATIME);
        }
    }

    //////////////////////////////////////////////////////////////////////

    private String pathSanityCheck(String path)
	throws FSCacheException {
	if (path == null) {
	    throw new FSCacheException(FSCacheException.FSERR_PERM,
				       "No such file [null]");
	}	    

	if ((path.endsWith("/") && !path.equals("/"))) {
	    path = path.substring(0, path.length()-1);
	}

	return(path);
    }

    private File setUpFSDir(String fsCacheDir) {
	File fsDir = new File(fsCacheDir);
	
	if (!fsDir.exists()) {
	    fsDir.mkdir();
	}
        else {
	    File[] children = fsDir.listFiles();
	    for (int i = 0; i < children.length; i++)
		children[i].delete();
	}

	return fsDir;
    }

    private FSCacheObject resolve(Object group, String path)
            throws FSCacheException {
	
        FSCacheObject result = null;

        byte[] bytes = null;
        try {
            bytes = doResolvePath(path.getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        if (bytes == null) {
            if (LOG.isLoggable(Level.FINE))
                LOG.fine("FS cache BDB lookup(\"" + path + "\") failed");

            try {
                result = volatileResolve(path);
            }
            catch (FSCacheException ignored) {}

            if (result == null && LOG.isLoggable(Level.INFO))
                LOG.info("FS cache failed to resolve \"" + path + "\"");
        }
        else {
            result = root.newObject();
            ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
            try {
                result.readIn(new DataInputStream(bs));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (result != null)
            updateAncestorAtimes(group, result);

	return result;
    }

    /**
     * Update atime, and also update parent's atime by calling resolve
     * on it. This way all ancestors are guaranteed to have atimes
     * later than the child's, and will stay in the cache at least as
     * long.
     */
    private void updateAncestorAtimes(Object group, FSCacheObject node)
            throws FSCacheException {

        updateAtime(group, node);
            
        if (node.fileType() == ROOTFILETYPE)
            return;

        resolve(group, parentOf(node.fileName()));
    }

    private boolean rm(Object group, FSCacheObject file, boolean recursive)
            throws FSCacheException {
	boolean ok = true;

        if (recursive) {
            List children = listChildren(group, file);

            for (Iterator i = children.iterator(); i.hasNext(); ) {
                FSCacheObject child = (FSCacheObject) i.next();
                if (!rm(group, child, true))
                    ok = false;
            }
        }

        if (!ok) {
            String msg = "Couldn't delete all children for " + file.fileName();
            throw new RuntimeException(msg);
        }

        try {
            long txn = (group == null) ? 0 : ((Long)group).longValue();
            return doDelEntry(txn, file.fileName().getBytes("UTF-8"));
        }
        catch (DbException e) {
            throw new FSCacheException(e);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    void checkSize() {
        LOG.info("Size now is " + getSize() + " (max: " + cacheSizeMax + ")");
        if (getSize() > cacheSizeMax) {
            int toDelete = (int) (getSize() - cacheSizeLow);
            if (LOG.isLoggable(Level.INFO))
                LOG.info("FS cache cleanup: removing " + toDelete +
                         " entries to try to get to " + cacheSizeLow);

            boolean rc = false;
            try {
                rc = deleteMustiestEntries(toDelete);
            }
            catch (Exception e) {
                LOG.log(Level.WARNING, "cache cleanup error", e);
            }

            if (!rc)
                LOG.warning("Cache cleanup failed!");
            else
                if (LOG.isLoggable(Level.INFO))
                    LOG.info("FS cache cleanup: now size = " + getSize());
        }
    }

    private void updateAtime(Object group, FSCacheObject file)
            throws FSCacheException {
        long now = System.currentTimeMillis();

        LOG.info("Updating atime for \"" + file.fileName() + "\": " + now);

        file.setAtime(now);

        update(group, file, false);    // don't flush cursor cache entry
    }

    /**
     * Gargabge collection: Start reading from the atime index, which
     * will give us oldest entries first
     */
    private synchronized boolean deleteMustiestEntries(int num)
            throws DbException {
        boolean firstTime = true;

        LOG.info("Trying to delete " + num + " entries.");

        long cursor = cursorCache.getCursor(null, CursorCache.CURSOR_ATIME);
        long txn = createTransaction();

        try {
            for (int i = 0; i < num; i++) {
                try {
                    byte[] bytes = doCursorGet(null, cursor, firstTime);
                    firstTime = false;

                    if (bytes == null)
                        return false;

                    FSCacheObject file = root.newObject();
                    ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
                    file.readIn(new DataInputStream(bs));
                
                    LOG.info("Deleting " + file.fileName() +
                             " (atime " + file.atime() + ")");

                    doDelEntry(txn, file.fileName().getBytes());
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        finally {
            commitTransaction(txn);
            cursorCache.remove(null, CursorCache.CURSOR_ATIME);
        }

        return true;
    }
    
    private static String parentOf(String path) {
        int pos = path.lastIndexOf('/');
        if (pos < 0)
            return ".";
        return path.substring(0, pos);
    }

    private void getCacheSizes() {
        cacheSizeMax = getProperty(PNAME_CACHE_SIZE_MAX,
                                      DEFAULT_CACHE_SIZE_MAX);
        cacheSizeLow = getProperty(PNAME_CACHE_SIZE_LOW,
                                      DEFAULT_CACHE_SIZE_LOW);
        if (cacheSizeLow >= cacheSizeMax) {
            LOG.warning("FS cache lo-water " + cacheSizeLow +
                        " MUST be below hi-water " + cacheSizeMax +
                        "; resetting to (hi-water - 1)");
            cacheSizeLow = cacheSizeMax - 1;
        }
    }

    private void startSweeperThread() {
        if (pollInterval <= 0)
            return;

        sweeperThread = new SweeperThread(this);
        sweeperThread.start();
    }

    /** At regular intervals call checkSize() which does the work */
    class SweeperThread extends Thread {
        BDBNativeFileCache parent = null;
        private boolean terminate = false;
        SweeperThread(BDBNativeFileCache parent) {
            this.parent = parent;
        }
        public void run() {
            LOG.info("FS cache sweeper starting");
            while (!terminate) {
                try {
                    Thread.sleep(pollInterval);
                }
                catch (InterruptedException ie) {
                    LOG.info("Sleep interrupted");
                }
                parent.checkSize();
            }
        }
        public void terminate() { terminate = true; }
    }

    String getProperty(String name, String dfl) {
        String s = config.getProperty(name);
        if (s == null)
            return dfl;
        return s;
    }

    int getProperty(String name, int dfl) {
        int i = dfl;

        String s = config.getProperty(name);

        if (s == null)
            return dfl;

        try {
            i = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new Error("Property " + name + "=" + s + " non-numeric");
        }

        return i;
    }

    /**********************************************************************
     *
     * Native calls
     *
     **********************************************************************/

    static {
        System.loadLibrary("fscache");
    }

    private native int doInit(String home, boolean useSyslog);

    private native boolean doAddEntry(long txn, byte[] path, byte[] data)
	throws DbException;
    private native boolean doUpdateEntry(long txn, byte[] path, byte[] data)
	throws DbException;

    private native boolean doDelEntry(long txn, byte[] path)
        throws DbException;

    private native byte[] doResolvePath(byte[] path);

    private native byte[] doCursorGet(byte[] path, long cursor,
				      boolean firstEntry)
	throws DbException;

    // getSize is cheap (cached or heuristic); calculateSize is
    // expensive and may have to scan the entire DB.
    private native long getSize();
    private native long calculateSize() throws DbException;

    private native long createTransaction()
	throws DbException;
    private native void commitTransaction(long txn)
	throws DbException;

    // These are for the convenience of the JNI
    public static final byte ROOTFILETYPE = FSCacheObject.ROOTFILETYPE;
    public static final byte DIRECTORYTYPE = FSCacheObject.DIRECTORYTYPE;
    public static final byte FILELEAFTYPE = FSCacheObject.FILELEAFTYPE;
    private static final int FTYPE_OFFSET = FSCacheObject.FTYPE_OFFSET;
    private static final int ATIME_OFFSET = FSCacheObject.ATIME_OFFSET;
    private static final int ATIME_LEN = FSCacheObject.ATIME_LEN;
    private static final int HC_INDEX_OFFSET = FSCacheObject.HC_INDEX_OFFSET;
    private static final int HC_INDEX_LEN = FSCacheObject.HC_INDEX_LEN;
    private static final int OID_OFFSET = FSCacheObject.OID_OFFSET;
    private static final int OID_LEN = FSCacheObject.OID_LEN;
    private static final int NAMELEN_OFFSET = FSCacheObject.NAMELEN_OFFSET;
    private static final int NAME_OFFSET = FSCacheObject.NAME_OFFSET;
    public static final int CURSOR_NONE = CursorCache.CURSOR_NONE;
    public static final int CURSOR_MAIN = CursorCache.CURSOR_MAIN;
    public static final int CURSOR_CHILDREN = CursorCache.CURSOR_CHILDREN;
    public static final int CURSOR_ATIME = CursorCache.CURSOR_ATIME;
    public static final int CURSOR_INDEX = CursorCache.CURSOR_INDEX;
    public static final int CURSOR_OID = CursorCache.CURSOR_OID;

    //////////////////////////////////////////////////////////////////////
    // Unit tests
    //////////////////////////////////////////////////////////////////////

    private static final int OID_SIZE = 28;

    private static BDBNativeFileCache theCache = null;
    private static Random r;
    private static int dirIndex = 1;
    static {
        r = new Random(System.currentTimeMillis());
    }
    static synchronized int nextIndex() { return dirIndex++; }

    public class TestCacheObject extends FSCacheObject {

        private byte[] oid = null;

        public TestCacheObject() {
            oid = new byte[OID_SIZE];
            r.nextBytes(oid);
        }

        public FSCacheObject newObject() {
            return new TestCacheObject();
        }

        public void readIn(DataInputStream input) throws IOException {
            super.readIn(input);

        }

        public void writeOut(DataOutputStream os) throws IOException {
            super.writeOut(os);

        }

        public void readInOID(DataInputStream input) throws IOException {
            input.read(oid);
        }

        public void writeOutOID(DataOutputStream os) throws IOException {
            os.write(oid);
        }

        public byte[] getOidBytesExternal() {
            return oid;
        }

        public int addChildren(Object group) throws FSCacheException {
            File f = new File(fileName());
            int nEntries = 0;

            String fp = fileName();
            if (fp.equals("/"))
                fp = "";
	    File[] children = f.listFiles();
            if (children == null)
                return 0;

            try {
                for (int i = 0; i < children.length; i++) {
                    File child = children[i];
                    TestCacheObject o = new TestCacheObject();
                    o.setFileName(fp + "/" + child.getName());

                    o.setIndex(nextIndex());
                    if (child.isDirectory())
                        o.setFileType(DIRECTORYTYPE);
                    else
                        o.setFileType(FILELEAFTYPE);
                    o.setAtime(System.currentTimeMillis());
                    theCache.add(group, o);
                    nEntries++;
                }
            }
            finally {
                theCache.endGroup(group);
            }
            return nEntries;
        }
    }

    private FSCacheObject testRoot() {
        FSCacheObject o = new TestCacheObject();
        o.setFileName("/usr/local");
        return o;
    }

    public static void main(String[] args) {
        Properties conf = new Properties();
        conf.setProperty(PNAME_CACHE_TEMPDIR,
                         String.valueOf(DEFAULT_CACHE_TEMPDIR));
        conf.setProperty(PNAME_CACHE_SIZE_MAX,
                         String.valueOf(DEFAULT_CACHE_SIZE_MAX));
        conf.setProperty(PNAME_CACHE_SIZE_LOW,
                         String.valueOf(DEFAULT_CACHE_SIZE_LOW));
        conf.setProperty(PNAME_CACHE_CLEANUP_INTERVAL,
                         String.valueOf(DEFAULT_CACHE_CLEANUP_INTERVAL));

        try {
            theCache = new BDBNativeFileCache();
            FSCacheObject tRoot = theCache.testRoot();
            theCache.initialize(tRoot, conf);

            // Tell the root to add all its children (recursively).
            Object group =  theCache.startGroup();
            addAllChildren(tRoot, group);
            theCache.endGroup(group);

            // dump the current contents
            System.out.println("Complete DB:");
            theCache.dumpCache(CursorCache.CURSOR_MAIN);

            theCache.deleteMustiestEntries(1000);

            System.out.println("\nSorting by mustiest first:");
            theCache.dumpCache(CursorCache.CURSOR_ATIME);

            System.out.println("Good bye.");
        }
        catch (Exception e) {
            System.err.println("Exception: " + e);
            e.printStackTrace();
        }
    }

    private static void addAllChildren(FSCacheObject node, Object group)
            throws FSCacheException {
        if (node == null || node.fileType() == FSCacheObject.FILELEAFTYPE)
            return;

        List children = theCache.listChildren(group, node);
        if (children == null)
            return;

        for (Iterator i = children.iterator(); i.hasNext(); ) {
            FSCacheObject c = (FSCacheObject) i.next();
            addAllChildren(c, group);
        }
    }
}
