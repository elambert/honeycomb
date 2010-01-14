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



package com.sun.honeycomb.coordinator;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.resources.ByteBufferPool;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlockCache {

    private static final float LOAD_FACTOR = .75F;

    private static final Logger LOGGER =
        Logger.getLogger(BlockCache.class.getName());

    private int maxBlocks;
    private Map cache;
    private Key probe;

    public int hits;
    public int misses;
    public int puts;
    public int puthits;
    public int deletes;
    public int ejects;

    public BlockCache(int newMaxBlocks) {
        maxBlocks = newMaxBlocks;

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("max blocks = " + maxBlocks);
        }

        // anonymous class construct extends LinkedHashMap
        // and overrides its removeEldestEntry method
        // to let us manage return of buffers to the pool.
        //
        // This map is ACCESS ordered, therefore cache.get()
        // is a structural modification and must be synchronized!
        // cache.put() as well, of course.
        //
        cache = new LinkedHashMap(maxBlocks, LOAD_FACTOR, true) {

            public boolean removeEldestEntry(Map.Entry eldest) {

                boolean result = (size() >= maxBlocks);
                if (result) {
                    // Stale block will be retired from the cache
                    // by the put() once we return 'true' from here; 
                    // check in its buffer back to the buffer pool.
                    ByteBuffer block = (ByteBuffer)eldest.getValue();
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest("cache full: size " + size() +
                                      " (max size " + maxBlocks + 
                                      "), ejecting block " +
                                      getIdentityString(block) + " " +
                                      (Key)eldest.getKey());
                    }
                    ByteBufferPool.getInstance().checkInBuffer(block);
                    ejects++;
                }

                return result;
            }
        };

        probe = new Key();

        resetStats();
    }

    private static String getIdentityString(Object object) {
        return Integer.toHexString(System.identityHashCode(object));
    }

    public void put(NewObjectIdentifier oid, long blockId, ByteBuffer block) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("inserting block " + getIdentityString(block) +
                          " oid = " + oid +
                          " blockId = " + blockId);
        }

        ByteBuffer other = (ByteBuffer)cache.put(new Key(oid, blockId), block);
        if (other != null) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("replaced buffer " + getIdentityString(other) + 
                              " with buffer " + getIdentityString(block) + 
                              " oid = " + oid + " blockId = " + blockId);
            }
            ByteBufferPool.getInstance().checkInBuffer(other);
            puthits++;
        } else {
            puts++;
        }
    }

    public ByteBuffer get(NewObjectIdentifier oid, long blockId) {
        probe.oid = oid;
        probe.blockId = blockId;
        ByteBuffer result = (ByteBuffer)cache.get(probe);
        if (result != null) {
            hits++;
        } else { 
            misses++;
        }
        return result;
    }

    // pgates: NOTE - This implementation traverses the entire hash
    // structure to find objects to delete. This is OK as long as the
    // map is small. Our servers currently cache at most 512 blocks so
    // there's nothing to worry about, but we'll probably need to
    // revisit this later. Fortunately LinkedHashMap has a very
    // efficient entry iterator since it's also a linked list.
    public void delete(NewObjectIdentifier oid) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("deleting all buffers for oid: " + oid);
        }
        Iterator entries = cache.entrySet().iterator();

        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry)entries.next();
            Key key = (Key)entry.getKey();
            ByteBuffer buffer = (ByteBuffer)entry.getValue();

            if (key.oid.equals(oid)) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("deleting block " + 
                                  getIdentityString(buffer) + 
                                  " " + key);
                }
                entries.remove();
                ByteBufferPool.getInstance().checkInBuffer(buffer);
                deletes++;
            } 
        }
    }

    private String showCache() {
        StringBuffer contents = new StringBuffer("\n\n");
        Iterator entries = cache.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry)entries.next();
            Key key = (Key)entry.getKey();
            ByteBuffer buffer = (ByteBuffer)entry.getValue();
            contents.append("buffer " + getIdentityString(buffer) +
                            " " + key + "\n");
        }
        contents.append("\n");
        return contents.toString();
    }

    public int getCacheSize() { return cache.size(); }
    public int getCacheMaxSize() { return maxBlocks; }
   
    public String getCacheStats(boolean verbose) {
        String stats =
            "Cache size=" + cache.size() + " maxsize=" + maxBlocks +
            " hits=" + hits + " misses=" + misses + 
            " puts=" + puts + " puthits=" + puthits +
            " deletes=" + deletes + " ejects=" + ejects;
        if (verbose) {
            return stats + "\n" + showCache();
        } else {
            return stats;
        }
    }

    public void resetStats() {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("zeroing out cache stats counters, " +
                          " old values: " + getCacheStats(false));
        }
        hits = misses = puts = puthits = deletes = ejects = 0;
    }

    private static class Key implements Comparable {

        private NewObjectIdentifier oid = null;
        private long blockId = -1;

        public Key() {
        }

        public Key(NewObjectIdentifier newOid, long newBlockId) {
            // Fix for bug 6398274: clone the OID, do not keep a reference!
            // OID contents can be modified for multichunk objects.
            oid = new NewObjectIdentifier(newOid);
            blockId = newBlockId;
        }

        public int hashCode() {
            return oid.hashCode() + (int)(blockId ^ (blockId >>> 32));
        }

        public boolean equals(Object other) {
            // safest to implement in terms of compareTo()
            return (compareTo(other) == 0);
        }

        public int compareTo(Object other) {
            Key key = (Key)other;

            int oidCompare = oid.compareTo(key.oid);
            if (oidCompare != 0) {
                return oidCompare;
            }

            if (blockId > key.blockId) {
                return 1;
            } else if (blockId < key.blockId) {
                return -1;
            }

            return 0;
        }

        public String toString() {
            return "oid = " + oid + " blockId = " + blockId;
        }
    }
}
