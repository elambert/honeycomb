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

import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.net.URLEncoder;
import java.net.URLDecoder;

import java.util.List;
import java.util.LinkedList;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/** The cache used by the filesystem module */
public abstract class FSCache {
    public static final String PNAME_PREFIX = "honeycomb.fscache.";

    public static final String PNAME_CACHE_SIZE_MAX = "size.max";
    public static final String PNAME_CACHE_SIZE_LOW = "size.lo";
    public static final String PNAME_FORCE_REFRESH = "refresh_on_failure";
    public static final String PNAME_COHERENCY = "coherency.time";

    public static final int DEFAULT_CACHE_SIZE_MAX = 10000;
    public static final int DEFAULT_CACHE_SIZE_LOW =  8000;
    public static final long DEFAULT_COHERENCY = 10L; // seconds

    protected static final Logger logger =
        Logger.getLogger(FSCache.class.getName());

    private static boolean refreshOnFailure = false;
    private static long coherencyTime = 0;

    protected static Properties config = null;
    protected FSCacheObject root = null;

    protected int cacheSizeMax;
    protected int cacheSizeLow;

    private static FSCache theInstance = null;

    // Initialize the cache. Since an implementation of this interface
    // (a cache) will need to create new objects, it needs to know
    // their class (needs the root object)
    public void initialize(FSCacheObject root, Properties config)
            throws FSCacheException {
        if (theInstance != null)
            throw new FSCacheException(FSCacheException.FSERR_SERVERFAULT,
                                       "FS Cache object already instantiated!");
        theInstance = this;

        this.root = root;
        this.config = config;
        coherencyTime = 1000 * getProperty(PNAME_COHERENCY, DEFAULT_COHERENCY);
        refreshOnFailure = getProperty(PNAME_FORCE_REFRESH, true);
        cacheSizeMax = getProperty(PNAME_CACHE_SIZE_MAX,
                                   DEFAULT_CACHE_SIZE_MAX);
        cacheSizeLow = getProperty(PNAME_CACHE_SIZE_LOW,
                                   DEFAULT_CACHE_SIZE_LOW);
        logger.info("Cache coherency time limit " + coherencyTime/1000 +
                    "s; " + (refreshOnFailure? "" : "don't ") +
                    "refresh parent on lookup failures; hi-water " +
                    cacheSizeMax + ", lo-water " + cacheSizeLow);
    }

    static void reset() {
        logger.info("FS cache reset");
        theInstance = null;
    }

    public static FSCache getInstance() {
        return theInstance;
    }

    // Operations may be grouped to form a "transaction"
    public abstract Object startGroup()	throws FSCacheException;
    public abstract void endGroup(Object group)	throws FSCacheException;
    
    // Basic lookup methods
    public abstract FSCacheObject lookup(Object group, String path)
        throws FSCacheException;

    // Directory listing: returns list of FSCacheObject
    public abstract List listChildren(Object group, FSCacheObject parent)
        throws FSCacheException;

    // Add/remove/update entries
    public abstract boolean add(Object group, FSCacheObject obj)
        throws FSCacheException;
    public abstract boolean remove(Object group, FSCacheObject obj,
                                   boolean recursive)
        throws FSCacheException;

    public abstract boolean remove(Object group, byte[] oid)
        throws FSCacheException;

    public abstract String toString();

    public abstract void dump(OutputStream os) throws IOException;

    /** URL-quote and UTF-8 a string */
    public static String quote(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /** URL-unquote and un-UTF-8 a string */
    public static String unquote(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Split a path into its components, un-URL-quoting as required:
     */
    public static String[] split(String path) {
        if (path == null || path.length() == 0)
            return null;

        List dirs = new LinkedList();
        String[] comps = path.split("/+");

        for (int i = 0; i < comps.length; i++)
            if (comps[i].length() > 0)
                dirs.add(unquote(comps[i]));

        String[] retval = new String[dirs.size()];
        dirs.toArray(retval);

        if (logger.isLoggable(Level.FINE)) {
            String msg = "\"" + path + "\" -> {";
            for (int j = 0; j < retval.length; j++)
                msg += " \"" + retval[j] + "\"";
            logger.fine(msg + " }");
        }
        return retval;
    }

    /**
     * Convert an array of names into a path, quoting as required. A
     * leading slash will be present.
     */
    public static String combine(String[] comps) {
        if (comps == null || comps.length == 0)
            return "/";

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < comps.length; i++)
            sb.append('/').append(quote(comps[i]));
        return sb.toString();
    }

    public FSCacheObject getRoot() {
        return root;
    }

    public int getCacheSize() { return cacheSizeMax; }

    public static long getCoherencyTime() { return coherencyTime; }

    boolean getProperty(String name, boolean dfl) {
        String prop = getProperty(name, dfl? "true":"false");
        if (prop == null)
            return dfl;

        if (prop.equalsIgnoreCase("true") || prop.equalsIgnoreCase("yes") ||
                prop.equalsIgnoreCase("y") || prop.equals("1"))
            return true;
        if (prop.equalsIgnoreCase("false") || prop.equalsIgnoreCase("no") ||
                prop.equalsIgnoreCase("n") || prop.equals("0"))
            return false;

        return dfl;
    }

    String getProperty(String name) {
        return config.getProperty(PNAME_PREFIX + name);
    }

    String getProperty(String name, String dfl) {
        String s = getProperty(name);

        if (s != null)
            return s;

        return dfl;
    }

    int getProperty(String name, int dfl) {
        String s = getProperty(name);

        try {
            if (s != null)
                return Integer.parseInt(s);
        }
        catch (NumberFormatException e) {
            logger.warning("Property " + name + "=" + s + " non-numeric");
        }

        return dfl;
    }

    long getProperty(String name, long dfl) {
        String s = getProperty(name);

        try {
            if (s != null)
                return Long.parseLong(s);
        }
        catch (NumberFormatException e) {
            logger.warning("Property " + name + "=" + s + " non-numeric");
        }

        return dfl;
    }
}
