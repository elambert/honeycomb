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
import java.util.Collections;
import java.util.Random;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import java.util.SortedSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.common.ByteArrays;

/**
 * This is a LinkedHashMap implementation of FSCache. It's a vanilla
 * LRU cache with support for the OID Map: all the locations in the
 * cache that a particular OID is in.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class LinkedHashMapCache extends FSCache {

    protected static final Logger logger =
        Logger.getLogger(LinkedHashMapCache.class.getName());

    //////////////////////////////////////////////////////////////////////
    // The real cache class
    // We need to override removeEldestEntry to remove from oidMap
    static final float loadFactor = 0.75f;
    private class LRUCache extends LinkedHashMap {
        LinkedHashMapCache parent = null;
        LRUCache(int maxSize, LinkedHashMapCache parent) {
            super(1 + (int)Math.ceil(maxSize/loadFactor), loadFactor, true);
            this.parent = parent;
        }
        protected boolean removeEldestEntry(Map.Entry obj) {
            if (size() <= cacheSizeMax)
                return false;

            if (logger.isLoggable(Level.FINE))
                logger.fine("Spilling " + obj.getKey());

            return parent.spill((FSCacheObject) obj.getValue());
        }
    }

    private Map cache = null;
    private Map oidMap = null;

    //////////////////////////////////////////////////////////////////////

    public void LinkedHashMapCache() {}

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

    //////////////////////////////////////////////////////////////////////

    // Basic lookup methods
    public FSCacheObject lookup(Object o, String path)
            throws FSCacheException {
        long startTime = System.currentTimeMillis();
        FSCacheObject result = cacheLookup(path);

        if (result == null) {
            int err = FSCacheException.FSERR_NOENT;
            String msg = "File \"" + path + "\" not found";
            throw new FSCacheException(err, msg);
        }

        if (logger.isLoggable(Level.FINE))
            logger.fine("instr " + (System.currentTimeMillis() - startTime) +
                        " lookup \"" + path + "\"");
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
        cacheLookup(node.fileName());

        if (logger.isLoggable(Level.FINE)) {
            String msg = "LS   " + node.fileName() + " -> {";

            for (Iterator i = children.iterator(); i.hasNext(); ) {
                FSCacheObject obj = (FSCacheObject) i.next();
                msg += " \"" + obj.fileName() + "\"";
            }
            msg +=  " } ";

            logger.fine(msg +
                        (System.currentTimeMillis() - startTime) + "ms");
        }

        return children;
    }

    // Add/remove/update entries
    public boolean add(Object o, FSCacheObject obj) throws FSCacheException {
        long startTime = System.currentTimeMillis();

        FSCacheObject preExisting = cacheLookup(obj.fileName());
        if (preExisting != null) {
            if (logger.isLoggable(Level.FINE))
                logger.fine("Object " + obj.fileName() + " already exists");
            return true;
        }

        cache.put(obj.fileName(), obj);
        getObjList(obj.getOidBytesExternal()).add(obj);

        FSCacheObject parent = cacheLookup(parentName(obj.fileName()));

        if (parent == null) {
            logger.warning("Cache invariant violation: no parent for " +
                           obj.fileName());
            parent = addParentDirs(obj);
        }

        // Make sure "/" entry does not appear as a child of "/" itself.
        if (obj.fileType() != FSCacheObject.ROOTFILETYPE) {
            obj.setParent(parent);
            parent.addChild(obj);
        }

        if (logger.isLoggable(Level.FINE))
            logger.fine("instr " + (System.currentTimeMillis() - startTime) +
                        " add \"" + obj.fileName() + "\"");
        return true;
    }

    public boolean remove(Object o, FSCacheObject obj, boolean r)
            throws FSCacheException {
        long startTime = System.currentTimeMillis();

        String objName = obj.fileName();

        if (cacheLookup(objName) == null) {
            logger.warning("Object " + objName + " not in the cache");
            return true;
        }

        oidMapRemove(obj);

        boolean ok = cache.remove(objName) != null;
        if (!ok)
            logger.warning("Couldn't remove " + objName);

        FSCacheObject parent = cacheLookup(parentName(objName));
        if (parent != null)
            parent.removeChild(obj);

        if (logger.isLoggable(Level.FINE))
            logger.fine("instr " + (System.currentTimeMillis() - startTime) +
                        " rm \"" +  objName + "\"");

        return ok;
    }

    public boolean remove(Object group, byte[] oid) throws FSCacheException {
        long startTime = System.currentTimeMillis();

        boolean ok = true;
        String[] locations = getLocations(oid);

        if (logger.isLoggable(Level.INFO)) {
            String msg = "Removing OID " + StringUtil.image(oid) + ":";
            for (int i = 0; i < locations.length; i++)
                msg += " " + StringUtil.image(locations[i]);
            logger.info(msg);
        }

        for (int i = 0; i < locations.length; i++)
            if (!remove(group, cacheLookup(locations[i]), false))
                ok = false;

        if (logger.isLoggable(Level.FINE))
            logger.fine("instr " + (System.currentTimeMillis() - startTime) +
                        " rm " + ByteArrays.toHexString(oid));

        return ok;
    }

    ///////////////////////////////////////////////////////////////////////

    private void init() throws FSCacheException {

        cache = Collections.synchronizedMap(new LRUCache(cacheSizeMax, this));
        cache.put(root.fileName(), root);

        oidMap = new HashMap();

        if (logger.isLoggable(Level.INFO))
            logger.info("LinkedHashMapCache initialized, root = " +
                        root.fileName());
    }

    public String toString() {
        return "LinkedHashMapCache " + cache.size() + " 0x" + hashCode();
    }

    public synchronized void dump(OutputStream os) throws IOException {
        OutputStreamWriter out = new OutputStreamWriter(os);

        out.write(toString());
        out.write('\n');

        int j = 0;
        for (Iterator i = cache.keySet().iterator(); i.hasNext(); ) {
            FSCacheObject obj = (FSCacheObject) cache.get(i.next());
            out.write(Integer.toString(++j));
            out.write(':');
            out.write(obj.toString());
            out.write('\n');
            if (cache.get(obj.fileName()) == null)
                logger.warning("Object " + obj.fileName() + " not in!");
        }
        out.flush();

        j = 0;
        out.write("\nObject map (" + oidMap.size() + " elements):\n");
        for (Iterator i = oidMap.keySet().iterator(); i.hasNext(); ) {
            try {
                OID oid = (OID) i.next();
                out.write(Integer.toString(++j));
                out.write(':');
                out.write(ByteArrays.toHexString(oid.value()));

                String[] paths = getLocations(oid.value());
                for (int k = 0; k < paths.length; k++) {
                    out.write(" \"");
                    out.write(paths[k]);
                    out.write("\"");
                }
                out.write('\n');
            }
            catch (Exception e) {
                out.write("WTF?");
                logger.log(Level.SEVERE, "AAaaaaa.....!", e);
            }
        }
        out.write("\nDONE!\n");

    }

    /**
     * Lookup a path in the cache. Take care of the cache invariant:
     * get parent also, so parent is always accessed after any of its
     * children so will not be reclaimed while it still has children.
     */
    private FSCacheObject cacheLookup(String path) {
        FSCacheObject result = (FSCacheObject) cache.get(path);

        // Recursively go up through ancestors
        if (result != null && !path.equals("/"))
            cacheLookup(parentName(result.fileName()));

        return result;
    }

    ////////////////////////////////////////////////////////////////////////
    // Support for delete by OID

    /** Finds all the locations that an object occurs in the filesystem */
    public synchronized String[] getLocations(byte[] oidBytes) {
        List paths = new LinkedList();

        List objects = getObjList(oidBytes);
        for (Iterator i = objects.iterator(); i.hasNext(); ) {
            FSCacheObject f = (FSCacheObject) i.next();
            paths.add(f.fileName());
        }

        String[] retval = new String[paths.size()];
        return (String[]) paths.toArray(retval);
    }

    private List getObjList(byte[] oidBytes) {
        OID oid = new OID(oidBytes);

        List objects = (List) oidMap.get(oid);
        if (objects == null) {
            objects = new LinkedList();
            oidMap.put(oid, objects);
        }
        return objects;
    }

    ////////////////////////////////////////////////////////////////////////
    // Cache reclamation

    private boolean spill(FSCacheObject obj) {
        if (obj == null) {
            logger.warning("Can't spill null!");
            return true;
        }

        // If this node has any children, spilling this node would
        // result in an invariant violation
        if (obj.numChildren() > 0) {
            logger.info("Spilling " + StringUtil.image(obj.fileName()) +
                        " would result in a violation of the invariant");
            return false;
        }

        FSCacheObject parent = cacheLookup(parentName(obj.fileName()));
        if (parent == null) {
            logger.warning("Cache invariant violation: no parent for " +
                           StringUtil.image(obj.fileName()));
            parent = addParentDirs(obj);
        }

        if (obj.parent() == null)
            obj.setParent(parent);

        // Don't reclaim the root or any view objects. If someone does
        // try to, issue a warning and update the atime for the object.
        if (obj.fileType() == FSCacheObject.ROOTFILETYPE ||
                obj.parent().fileType() == FSCacheObject.ROOTFILETYPE) {
            if (logger.isLoggable(Level.FINE))
                logger.fine("Foiling attempt to reclaim " +
                            StringUtil.image(obj.fileName()));
            cacheLookup(obj.fileName());
            return false;
        }

        parent.removeChild(obj);
        oidMapRemove(obj);

        return true;
    }

    private void oidMapRemove(FSCacheObject obj) {
        OID oid = new OID(obj.getOidBytesExternal());

        List objects = getObjList(oid.value());
        objects.remove(obj);
        if (objects.size() == 0)
            // All instances of this OID are gone
            oidMap.remove(oid);
    }

    ////////////////////////////////////////////////////////////////////////
    // Misc

    /**
     * A fixer-upper: when the cache invariant is violated (i.e. a
     * node's parent had an earlier timestamp so it got spilled before
     * the node) create a parent node and add it to the cache.
     */
    private FSCacheObject addParentDirs(FSCacheObject obj) {
        if (obj.fileType() == FSCacheObject.ROOTFILETYPE)
            return obj;

        HCFile f = (HCFile) obj;
        String name = f.fileName();
        String pName = parentName(name);
        FSCacheObject parent = cacheLookup(pName);
        if (parent != null)
            return parent;

        parent = new HCFile(f.getViewIndex(), f.getAttributes());
        addParentDirs(parent);

        f.setParent(parent);
        parent.addChild(f);

        cache.put(pName, parent);
        return parent;
    }

    /** This is just like basename(1)/dirname(1) */
    private static String parentName(String path) {
        String name = path.replaceAll("/[^/]*/?$", "");
        if (name.length() == 0 || path.equals(name))
            // A bare name "foo" is considered the same as "/foo"
            return "/";
        return name;
    }

    /** A wrapper to let us use byte arrays as keys in a Map */
    private static class OID {
        private byte[] value = null;
        OID(byte[] v) { this.value = v; }
        byte[] value() { return value; }
        public int hashCode() { return ByteArrays.hashCode(value); }
        public boolean equals(Object o) {
            if (!(o instanceof OID)) return false;
            return ByteArrays.equals(this.value, ((OID) o).value);
        }
    }

    //////////////////////////////////////////////////////////////////////
    // Unit tests
    //////////////////////////////////////////////////////////////////////

    private static final int OID_SIZE = 28;

    private static LinkedHashMapCache theCache = null;
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

        try {
            theCache = new LinkedHashMapCache();
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
                        oidsToDelete.add(new OID(node.getOidBytesExternal()));

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
                OID oid = (OID) i.next();
                theCache.remove(null, oid.value());
            }

            System.out.println("After OID deletes, DB is:");
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
