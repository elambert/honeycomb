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

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.logging.Logger;

/** This is a cache that maps URL to a cursor */
public abstract class CursorCache {

    protected static final Logger LOG =
        Logger.getLogger(CursorCache.class.getName());
    
    private static final int DBC_CACHE_SIZE = 16;
    
    public static final int CURSOR_NONE = 0;
    public static final int CURSOR_MAIN = 1;
    public static final int CURSOR_CHILDREN = 2;
    public static final int CURSOR_ATIME = 3;
    public static final int CURSOR_INDEX = 4;
    public static final int CURSOR_OID = 5;
    
    private List cache = null;

    //////////////////////////////////////////////////////////////////////

    public static class CacheEntry {
	private String url;
	private Object cursor;
        private int type;
	
	private CacheEntry(String url, Object cursor, int type) {
	    this.url = url;
	    this.cursor = cursor;
            this.type = type;
	}

	public Object url() { return url; }
	public Object cursor() { return cursor; }
	public int type() { return type; }
    }

    public CursorCache() {
	cache = new LinkedList();
    }

    /**
     * The implementing class is the only one who knows how to get
     * a real cursor from CacheEntry
     */
    public abstract long getCursor(String url, int type);

    /** Look for elements with given URL, and remove them */
    public synchronized void remove(String url, int type) {
	CacheEntry entry = null;

        for (Iterator i = cache.iterator(); i.hasNext(); ) {
	    entry = (CacheEntry) i.next();
	    if (entry.type() != type)
                continue;

            if ((entry.url() == null && url == null) ||
                    entry.url().equals(url)) {

                freeCursor(entry.cursor());
                i.remove();
                return;
            }
	}
    }

    /**
     * If it's in the cache, return it; otherwise return a new one and
     * save it.
     */
    protected synchronized CacheEntry get(String url, int type) {
	CacheEntry entry = null;

        for (Iterator i = cache.iterator(); i.hasNext(); ) {
            entry = (CacheEntry) i.next();
	    if (entry.type() != type)
                continue;

            if ((entry.url() == null && url == null) ||
                    entry.url().equals(url))
                return entry;
	}

	Object cursor = createCursor(type);
	entry = new CacheEntry(url, cursor, type);
        add(entry);

	return entry;
    }
    
    private synchronized void add(CacheEntry entry) {
        if (cache.size() >= DBC_CACHE_SIZE) {
            CacheEntry e = (CacheEntry) cache.remove(0);
            freeCursor(e.cursor);
            e.cursor = null;
        }

        cache.add(entry);
    }

    protected abstract void freeCursor(Object cursor);
    protected abstract Object createCursor(int db);
    
}
