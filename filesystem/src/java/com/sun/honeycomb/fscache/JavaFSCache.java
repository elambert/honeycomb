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

import java.io.IOException;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.DataOutputStream;
import java.io.DataInputStream;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import java.util.Random;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.SortedSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.ByteArrays;

/**
 * This is a pure-Java implementation of FSCache. It's not meant to be
 * fast, just simple and <em>correct</em>.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class JavaFSCache extends FSCache {

    private static final String PNAME_CACHE_CLEANUP_INTERVAL = "gc_interval";
    private static final String PNAME_PARANOID = "paranoid";

    private static final int DEFAULT_CACHE_CLEANUP_INTERVAL = 600;
    private static final boolean DEFAULT_PARANOIA = true;

    private static boolean paranoid = true; // conservative

    protected static final Logger logger =
        Logger.getLogger(JavaFSCache.class.getName());

    private long pollInterval;
    private SweeperThread sweeperThread = null;

    private CombinedJCache cache = null;

    public void JavaFSCache() {}

    public void initialize(FSCacheObject root, Properties config)
            throws FSCacheException {
        super.initialize(root, config);
        init();
    }

    // Operations may be grouped to form a "transaction"
    public Object startGroup()	throws FSCacheException {
        return null;
    }

    public void endGroup(Object group) throws FSCacheException {
    }

    public long objectsSpilled() {
        return cache.objectsSpilled();
    }

    // If a directory has ~N entries (where N is the size of the
    // cache), while trying to add them all a GC will result, and that
    // could cause this node (or its parent) to be deleted.


    //////////////////////////////////////////////////////////////////////

    // Basic lookup methods
    public FSCacheObject lookup(Object o, String path)
            throws FSCacheException {
        FSCacheObject result = cache.lookup(path);

        if (result == null) {
            int err = FSCacheException.FSERR_NOENT;
            String msg = "File \"" + path + "\" not found";
            throw new FSCacheException(err, msg);
        }

        if (logger.isLoggable(Level.FINE))
            logger.fine("STAT " + path + " -> " + result);

        updateAncestorAtimes(o, result);

        return result;
    }

    // Directory listing: returns list of FSCacheObject
    public List listChildren(Object o, FSCacheObject node)
            throws FSCacheException {
        long startTime = System.currentTimeMillis();

        if (node.isOld())
            // refresh it
            try {
                node.addChildren(o);
            }
            catch (FSCacheException e) {
                logger.warning("Failed to add children: " + node.fileName());
                return null;
            }

        List children = node.children();

        // We want the directory to have an atime later than any of its kids
        updateAncestorAtimes(o, node);

        if (logger.isLoggable(Level.FINE)) {
            String msg = "LS   " + node.fileName() + " -> {";

            for (Iterator i = children.iterator(); i.hasNext(); ) {
                FSCacheObject obj = (FSCacheObject) i.next();
                msg += " \"" + obj.fileName() + "\"";
            }
            logger.fine(msg + " }");
        }

        return children;
    }

    // Add/remove/update entries
    public boolean add(Object o, FSCacheObject obj)
            throws FSCacheException {
        long startTime = System.currentTimeMillis();

        FSCacheObject preExisting = cache.lookup(obj.fileName());
        if (preExisting != null) {
            updateAncestorAtimes(o, preExisting);
            if (logger.isLoggable(Level.FINE))
                logger.fine("Object " + obj.fileName() + " already exists");
            return true;
        }

        boolean rc;
        if (!(rc = cache.add(obj))) {
            String msg = "Couldn't add " + obj.fileName() + 
                " -- did you call refreshDirectory() first?";
            logger.warning(msg);
            dumpCache(Level.FINE);
        }

        updateAncestorAtimes(o, obj);

        if (cache.getSize() > cacheSizeMax && sweeperThread != null)
            // Wake up the cache cleaner
            sweeperThread.interrupt();

        if (logger.isLoggable(Level.FINE))
            logger.fine("INSTR ADD \"" + obj.fileName() + "\" " +
                        (System.currentTimeMillis() - startTime) + "ms");
        return rc;
    }

    public boolean remove(Object o, FSCacheObject obj, boolean r)
            throws FSCacheException {
        long startTime = System.currentTimeMillis();

        if (cache.lookup(obj.fileName()) == null) {
            logger.warning("Object " + obj.fileName() + " not in the cache");
            return true;
        }

        boolean rc;
        if (!(rc = cache.remove(obj))) {
            logger.warning("Couldn't remove " + obj.fileName());
            dumpCache(Level.INFO);
        }

        if (logger.isLoggable(Level.FINE))
            logger.fine("INSTR RM \"" +  obj.fileName() + "\" " +
                        (System.currentTimeMillis() - startTime) + "ms");

        return rc;
    }

    public boolean remove(Object group, byte[] oid) throws FSCacheException {
        long startTime = System.currentTimeMillis();

        boolean ok = true;
        if (logger.isLoggable(Level.INFO))
            logger.info("Removing object " + ByteArrays.toHexString(oid));

        String[] locations = cache.getLocations(oid);

        if (logger.isLoggable(Level.INFO)) {
            String msg = "OID " + ByteArrays.toHexString(oid) + ":";
            for (int i = 0; i < locations.length; i++)
                msg += " \"" + locations[i] + "\"";
            logger.info(msg);
        }

        for (int i = 0; i < locations.length; i++)
            if (!cache.remove(cache.lookup(locations[i])))
                ok = false;

        if (logger.isLoggable(Level.FINE))
            logger.fine("INSTR RM " + ByteArrays.toHexString(oid) + " " +
                        (System.currentTimeMillis() - startTime) + "ms");

        return ok;
    }

    public void dumpCache(Level l) {
        if (logger.isLoggable(l))
            logger.log(l, cache.toString());
    }

    ///////////////////////////////////////////////////////////////////////

    private void init() throws FSCacheException {

        if (config == null)
            logger.info("No config!");

        pollInterval = getProperty(PNAME_CACHE_CLEANUP_INTERVAL,
                                   DEFAULT_CACHE_CLEANUP_INTERVAL) * 1000;

        cache = new CombinedJCache();
        paranoid = getProperty(PNAME_PARANOID, DEFAULT_PARANOIA);
        cache.setParanoia(paranoid);
        cache.add(root);

        if (cacheSizeLow >= cacheSizeMax) {
            logger.warning("FS cache lo-water " + cacheSizeLow +
                           (paranoid? "(paranoid) ":"") +
                           " MUST be below hi-water " + cacheSizeMax +
                           "; resetting to (hi-water - 1)");
            cacheSizeLow = cacheSizeMax - 1;
        }

        startSweeperThread();

        if (logger.isLoggable(Level.INFO)) {
            logger.info("FS cache sweeps @ " + pollInterval + "ms");
            logger.info("JavaFSCache initialized, root = " + root.fileName());
        }
    }

    private void updateAncestorAtimes(Object o, FSCacheObject obj)
            throws FSCacheException {
        long now = System.currentTimeMillis();

        FSCacheObject node = obj;
        while (node != null) {
            node.setAtime(now);
            if (node.fileType() == FSCacheObject.ROOTFILETYPE)
                return;
            node = node.parent();
        }
        logger.warning("Doesn't go all the way up? " + obj);
    }

    private void startSweeperThread() {
        if (pollInterval <= 0)
            return;

        sweeperThread = new SweeperThread(this);
        sweeperThread.start();
    }

    private synchronized void stopSweeperThread() {
        if (sweeperThread != null) {
            sweeperThread.terminate();
            sweeperThread.interrupt();
            try {
                sweeperThread.join();
            } catch (InterruptedException e) {}
            sweeperThread = null;
        }
    }

    private void checkSize() {
        int sz = cache.getSize();

        if (sz > cacheSizeMax)
            cache.deleteMustiestEntries(sz - cacheSizeLow);
    }

    public String toString() {
        return cache.toString();
    }

    public void dump(OutputStream os) throws IOException {
        logger.info("Size " + cache.getSize());
        cache.dump(os);
    }

    /** At regular intervals call checkSize() which does the work */
    class SweeperThread extends Thread {
        JavaFSCache parent = null;
        private boolean terminate = false;
        SweeperThread(JavaFSCache parent) {
            this.parent = parent;
        }
        public void run() {
            logger.info("FS cache sweeper starting");
            while (!terminate) {
                try {
                    Thread.sleep(pollInterval);
                }
                catch (InterruptedException ie) {
                    logger.info("Sleep interrupted");
                }
                parent.checkSize();
            }
        }
        public void terminate() { terminate = true; }
    }


    //////////////////////////////////////////////////////////////////////
    // Unit tests
    //////////////////////////////////////////////////////////////////////

    private static final int OID_SIZE = 28;

    private static JavaFSCache theCache = null;
    private static Random r;
    private static int dirIndex = 1;
    static {
        r = new Random(System.currentTimeMillis());
    }
    static synchronized int nextIndex() { return dirIndex++; }

    /** A cache object that loads children from the filesystem */
    public static class TestCacheObject extends FSCacheObject {

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
                    if (fileType() != ROOTFILETYPE)
                      //theCache.loadPath(group,
                      //                  FSCache.split(o.parent().fileName()));
                    theCache.add(group, o);
                    nEntries++;
                }
            }
            finally {
                theCache.endGroup(group);
            }

            setComplete(true);  // All children are present
            return nEntries;
        }
    }

    private static void addAllChildren(FSCacheObject node, Object o, int depth)
            throws FSCacheException {
        if (depth-- <= 0 || node == null ||
                node.fileType() == FSCacheObject.FILELEAFTYPE)
            return;

        List children = theCache.listChildren(o, node);
        if (children == null || depth <= 0)
            return;

        for (Iterator i = children.iterator(); i.hasNext(); )
            addAllChildren((FSCacheObject) i.next(), o, depth);
    }

    private static FSCacheObject randomElement(List l) {
        if (l == null || l.size() == 0)
            return null;

        int j = 0, elt = r.nextInt(l.size());
        for (Iterator i = l.iterator(); i.hasNext(); )
            if (j++ == elt)
                return (FSCacheObject) i.next();
            else
                i.next();
        return null;
    }

    public static void main(String[] args) {
        Properties conf = new Properties();
        conf.setProperty(PNAME_CACHE_SIZE_MAX,
                         String.valueOf(DEFAULT_CACHE_SIZE_MAX));
        conf.setProperty(PNAME_CACHE_SIZE_LOW,
                         String.valueOf(DEFAULT_CACHE_SIZE_LOW));
        conf.setProperty(PNAME_CACHE_CLEANUP_INTERVAL,
                         String.valueOf(DEFAULT_CACHE_CLEANUP_INTERVAL));

        try {
            theCache = new JavaFSCache();
            FSCacheObject node = null;
            FSCacheObject tRoot = new TestCacheObject();
            tRoot.setFileName("/var");

            theCache.initialize(tRoot, conf);

            try { Thread.sleep(1000); } catch (Exception e) {}
            System.out.println("");

            List oidsToDelete = new LinkedList();

            // Start going down the tree at random
            for (int i = 0; i < 1000; i++)
                try {
                    Object o =  theCache.startGroup();
                    String prev = null;

                    node = tRoot;
                    while (!node.isFile()) {
                        node = randomElement(theCache.listChildren(o, node));
                        if (node == null)
                            break;
                        prev = node.fileName();
                    }

                    // 1% of the nodes will be deleted
                    if (node != null && r.nextInt(1000) < 10)
                        oidsToDelete.add(new CombinedJCache.OID(node.getOidBytesExternal()));

                    theCache.endGroup(o);
                    logger.info("done " + i + ".");
                }
                catch (Exception e) {
                    System.out.println(i + ": ERROR");
                    logger.log(Level.SEVERE, "error: " + node, e);
                }

            try { Thread.sleep(1000); } catch (Exception e) {}

            System.out.println("After loading tree, DB is:");
            theCache.dump(System.out);

            // Go through oidsToDelete and delete them all
            for (Iterator i = oidsToDelete.iterator(); i.hasNext(); ) {
                CombinedJCache.OID oid = (CombinedJCache.OID) i.next();
                theCache.remove(null, oid.value());
            }

            theCache.stopSweeperThread();

            System.out.println("Spilled " + theCache.objectsSpilled() +
                               " objects.");
            System.out.flush();

            System.out.println("After stopping sweeper, DB is:");
            theCache.dump(System.out);

            System.out.println("\nGoodbye.");
        }
        catch (Exception e) {
            System.err.println("Exception: " + e);
            e.printStackTrace();
            try { theCache.dump(System.err); } catch (Exception ign) {}
        }
    }

}
