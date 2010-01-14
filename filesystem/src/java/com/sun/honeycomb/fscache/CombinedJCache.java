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
 * A set of objects that together constitute a cache

 * @author Shamim Mohamed <shamim@sun.com>
 */
public class CombinedJCache {

    protected static final Logger logger =
        Logger.getLogger(CombinedJCache.class.getName());

    private static boolean paranoid = true; // conservative

    // Total count of stale objects kicked out
    private long objectsSpilled = 0;

    // maps path to FSCacheObject
    private Map cache = null;

    // maps OIDs to a list of FSCacheObject. Since an object may
    // appear in multiple places in the filesystem, it needs to be a
    // list and not just a single path or link to an FSCacheObject.
    private Map oidMap = null;

    public CombinedJCache() {
        cache = new HashMap();
        oidMap = new HashMap();
    }

    public long objectsSpilled() {
        return objectsSpilled;
    }

    public void setParanoia(boolean value) {
        paranoid = value;
    }

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

    public synchronized FSCacheObject lookup(String path) {
        return (FSCacheObject) cache.get(path);
    }


    public synchronized boolean add(FSCacheObject obj) {

        if (logger.isLoggable(Level.FINE))
            logger.fine("ADD  " + obj.fileName());

        // Add to all structures
        cache.put(obj.fileName(), obj);
        getObjList(obj.getOidBytesExternal()).add(obj);

        FSCacheObject parent =
            (FSCacheObject) cache.get(obj.parentName());

        // Make sure "/" entry does not appear as a child of "/" itself.
        if (obj.fileType() != FSCacheObject.ROOTFILETYPE) {
            obj.setParent(parent);
            parent.addChild(obj);
            if (paranoid)
                return checkAfterAdd(obj, parent);
        }

        return true;
    }

    public synchronized boolean remove(FSCacheObject obj) {
        if (obj == null)
            return true;

        if (logger.isLoggable(Level.FINE))
            logger.fine("RM   " + obj.fileName());

        OID oid = new OID(obj.getOidBytesExternal());

        // Remove from all structures
        cache.remove(obj.fileName());

        List objects = getObjList(oid.value());
        objects.remove(obj);
        if (objects.size() == 0)
            // All instances of this OID are gone
            oidMap.remove(oid);

        FSCacheObject parent =
            (FSCacheObject) cache.get(obj.parentName());
        parent.removeChild(obj);

        if (paranoid)
            return checkAfterRemove(obj, parent);
        return true;

    }

    public synchronized List findAllChildren(FSCacheObject node) {
        LinkedList ret = new LinkedList();
        for (Iterator i = cache.keySet().iterator(); i.hasNext(); ) {
            String name = (String) i.next();
            FSCacheObject obj = (FSCacheObject) cache.get(name);
            if (obj.parent() == node)
                ret.add(obj);
        }
        return ret;
    }

    public synchronized String toString() {
        return "FS cache " + cache.size() + " 0x" + hashCode();
    }

    public synchronized void dump(OutputStream os) throws IOException {
        OutputStreamWriter out = new OutputStreamWriter(os);

        out.write(toString());
        out.write('\n');

        Set atimes = makeAtimes();

        int j = 0;
        for (Iterator i = atimes.iterator(); i.hasNext(); ) {
            FSCacheObject obj = (FSCacheObject) i.next();
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
     * Complete consistency check. The master is the
     * path -> FSCacheObject map.
     */
    public synchronized void checkAndRepair() {

        logger.info("Starting cache consistency check");

        // Go through cache. For each node, make sure there are
        // entries in oidMap and they're consistent.  Check it's in
        // its parent's child list; check if each child has the node
        // as its parent.
        for (Iterator i = cache.keySet().iterator(); i.hasNext(); ) {
            String path = (String) i.next();
            FSCacheObject node = (FSCacheObject) cache.get(path);

            checkOidMap(node);
            checkRelations(node);
        }

        checkOidMap();

        logger.info("Cache consistency check complete.");
    }

    public synchronized void deleteMustiestEntries(int num) {

        logger.info("Cache cleanup: deleting " + num + " old entries.");

        Set atimes = makeAtimes();

        int c = 0;
        for (Iterator i = atimes.iterator(); i.hasNext(); ) {
            FSCacheObject obj = (FSCacheObject) i.next();
            OID oid = new OID(obj.getOidBytesExternal());

            if (logger.isLoggable(Level.INFO))
                logger.info("... removing " + obj.fileName());

            i.remove();
            cache.remove(obj.fileName());
            List objects = getObjList(oid.value());
            objects.remove(obj);
            if (objects.size() == 0)
                // All instances of this OID are gone
                oidMap.remove(oid);

            objectsSpilled++;

            FSCacheObject parent =
                (FSCacheObject) cache.get(obj.parentName());
            parent.removeChild(obj);

            if (++c >= num)
                break;
        }

        logger.info("Cache cleanup done: size = " + getSize() +
                    "; #objects reclaimed = " + objectsSpilled);
    }

    public synchronized int getSize() {
        return cache.size();
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

    /**
     * Go through all objects and return a sorted set of them, sorted
     * in ascending atimes order
     *
     * If the atimes invariant is false, we try to fix atimes of all
     * ancestors to conform and re-try. If the fix fails, i.e.
     * the 2nd try still encounters problems, give up.
     */
    private Set makeAtimes() {
        for (int j = 0; j < 2; j++) {
            boolean botched = false;

            Set s = new TreeSet();
            for (Iterator i = cache.keySet().iterator(); i.hasNext(); ) {
                FSCacheObject obj = (FSCacheObject) cache.get(i.next());
                if (obj.fileType() == FSCacheObject.ROOTFILETYPE)
                    continue;
                if (obj.atime() > obj.parent().atime()) {
                    logger.warning("[!] botch " + obj.fileName() +
                                   " is newer than parent!");
                    fixAtimes(obj.parent(), obj.atime());
                    botched = true;
                }
                s.add(obj);
            }
            if (!botched)
                return s;

            if (j == 0)
                logger.warning("Re-starting scan to fix botch");
        }

        throw new RuntimeException("Couldn't recover from botched invariant");
    }

    private void fixAtimes(FSCacheObject obj, long t) {
        while (obj != null) {
            obj.setAtime(t);
            obj = obj.parent();
        }
    }

    /**
     * Object has just been added; see if it's in all structures
     *
     */
    private boolean checkAfterAdd(FSCacheObject obj, FSCacheObject parent) {

        OID oid = new OID(obj.getOidBytesExternal());
        boolean inCache = cache.containsKey(obj.fileName());
        boolean inOidMap = oidMap.containsKey(oid);
        boolean inParentsList = parent.inChildren(obj);

        if (inCache && inOidMap && inParentsList)
            return true;

        logger.warning("Check failed after adding " + obj.fileName());

        if (inCache)
            cache.remove(obj.fileName());
        if (inOidMap)
            oidMap.remove(oid);
        if (inParentsList)
            parent.removeChild(obj);

        return false;

    }

    /**
     * Check the node is in its parent's child list; check if each
     * child has node as its parent; make sure parent always has a
     * later access-time.
     */
    private void checkRelations(FSCacheObject node) {
        if (node.fileType() != FSCacheObject.ROOTFILETYPE) {
            FSCacheObject parent = (FSCacheObject) cache.get(node.parentName());
            if (node.parent() != parent) {
                logger.warning("Setting parent pointer of " + node.fileName());
                node.setParent(parent);
            }

            List childList = parent.children();
            if (!childList.contains(node)) {
                logger.warning("Adding " + node.fileName() + 
                               " to parents child list");

                if (!parent.addChild(node))
                    logger.severe("Couldn't add " + node.fileName() + 
                                  " to parents child list");

                if (parent.atime() < node.atime()) {
                    logger.severe("Node " + node.fileName() +
                                  " has atime later than its parent");
                    parent.setAtime(node.atime());
                }
            }
        }

        for (Iterator i = node.children().iterator(); i.hasNext(); ) {
            FSCacheObject obj = (FSCacheObject) i.next();
            if (!node.fileName().equals(obj.parentName())) {
                logger.warning("Removing " + obj.fileName() + 
                               " from child list");
                i.remove();
            }
        }
    }

    private void checkOidMap(FSCacheObject node) {
        OID oid = new OID(node.getOidBytesExternal());

        if (!oidMap.containsKey(oid)) {
            logger.warning("Adding \"" + node.fileName() +
                           "\" to oidMap");
            oidMap.put(oid, node);
        }

        List objects = getObjList(oid.value());
        if (objects.contains(node))
            return;

        logger.warning("oidMap: no \"" + node.fileName() + "\"");
        if (!objects.add(node))
            logger.severe("oidMap add failed: \"" + node.fileName() + "\"");
    }

    private void checkOidMap() {
        for (Iterator i = oidMap.keySet().iterator(); i.hasNext(); ) {
            OID oid = (OID) i.next();
            List objects = (List) oidMap.get(oid);

            for (Iterator j = objects.iterator(); j.hasNext(); ) {
                FSCacheObject obj = (FSCacheObject) j.next();

                if (!ByteArrays.equals(oid.value(), obj.getOidBytesExternal())) {
                    logger.warning("Bad entry: " + obj.fileName());
                    j.remove();
                    continue;
                }

                if (!cache.containsKey(obj.fileName())) {
                    logger.warning("Spurious oidMap entry: " + obj.fileName());
                    j.remove();
                    continue;
                }
            }

            if (objects.size() == 0)
                i.remove();
        }
    }

    /**
     * Object has just been removed; ensure it's not in all structures
     */
    private boolean checkAfterRemove(FSCacheObject obj, FSCacheObject parent) {

        OID oid = new OID(obj.getOidBytesExternal());
        boolean inCache = cache.containsKey(obj.fileName());
        boolean inOidMap = oidMap.containsKey(oid);
        boolean inParentsList = parent.inChildren(obj);

        if (!inCache && !inOidMap && !inParentsList)
            return true;

        logger.warning("Check failed after removing " + obj.fileName());

        if (inCache)
            cache.remove(obj.fileName());
        if (inOidMap)
            oidMap.remove(oid);
        if (inParentsList)
            parent.removeChild(obj);

        return false;

    }

    /** A wrapper to let us use byte arrays as keys in a Map */
    public static class OID {
        private byte[] value = null;
        OID(byte[] v) { this.value = v; }
        byte[] value() { return value; }
        public int hashCode() { return ByteArrays.hashCode(value); }
        public boolean equals(Object o) {
            if (!(o instanceof OID)) return false;
            return ByteArrays.equals(this.value, ((OID) o).value);
        }
    }

}
