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

import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.config.ClusterProperties;

/**
 * This is a very simple implementation of FSCache that simply keeps
 * everything in a HashMap and never deletes anytyhing.
 */
public class SimpleFSCache extends FSCache {

    protected static final Logger logger =
        Logger.getLogger(SimpleFSCache.class.getName());
    
    private Map cache = null;
    private FSCacheObject root = null;

    public SimpleFSCache() {
        cache = new HashMap();
    }

    // Initialize the cache. Since an implementation of this interface
    // (a cache) will need to create new objects, it needs to know
    // their class
    public void initialize(FSCacheObject root, Properties config)
            throws FSCacheException {
        super.initialize(root, config);
        init();
    }

    private void init() throws FSCacheException {
        logger.info("SimpleFSCache initialized, root = " + root.fileName());
    }

    // Operations may be grouped to form a "transaction"
    public Object startGroup()	throws FSCacheException {
        return null;
    }

    public void endGroup(Object group) throws FSCacheException {
    }

    public String toString() {
        return "<SimpleFSCache>";
    }

    public void dump(OutputStream os) throws IOException {
    }

    //////////////////////////////////////////////////////////////////////

    // Basic lookup methods
    public FSCacheObject lookup(Object o, String path)
            throws FSCacheException {
        synchronized (cache) {
            return (FSCacheObject) cache.get(path);
        }
    }

    // Directory listing: returns list of FSCacheObject
    public List listChildren(Object o, FSCacheObject node)
            throws FSCacheException {
        List ret = new LinkedList();

        synchronized (cache) {
            for (Iterator i = cache.keySet().iterator(); i.hasNext(); ) {
                String name = (String) i.next();
                FSCacheObject obj = (FSCacheObject) cache.get(name);
                if (obj.parent() == node)
                    ret.add(obj);
            }
        }

        return ret;
    }


    // Add/remove/update entries
    public boolean add(Object o, FSCacheObject obj)
            throws FSCacheException {
        synchronized (cache) {
            return cache.put(obj.fileName(), obj) != null;
        }
    }

    public boolean remove(Object o, FSCacheObject obj, boolean r)
            throws FSCacheException {
        synchronized (cache) {
            return cache.remove(obj.fileName()) != null;
        }
    }

    public boolean remove(Object group, byte[] oid) throws FSCacheException {
        throw new RuntimeException("unimplemented");
    }

}
