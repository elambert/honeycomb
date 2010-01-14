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



package com.sun.honeycomb.oa;

import java.util.logging.Logger;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import com.sun.honeycomb.common.NewObjectIdentifier;

public class BlockCache {
    
    private class Key implements Comparable {
        public Key(NewObjectIdentifier oid, long blockId) {
            this.oid = oid;
            this.blockId = blockId;
        }
	
        public NewObjectIdentifier getOID() {
            return oid;
        }
	
        public long getBlockId() {
            return blockId;
        }
	
        public int hashCode() {
            return oid.hashCode() + (new Long(blockId)).hashCode();
        }

        public boolean equals(Object other) {
            return compareTo(other) == 0;
        }
	
        public int compareTo(Object other) {
            Key key = (Key) other;
	    
            int oidCompare = oid.compareTo(key.getOID());
            if(oidCompare != 0) {
                return oidCompare;
            }
	    
            if(blockId > key.getBlockId()) {
                return 1;
            } else if(blockId < key.getBlockId()) {
                return -1;
            }
	    
            return 0;
        }
	
        private NewObjectIdentifier oid = null;
        private long blockId = -1;
    }
    
    public static BlockCache getInstance() {
        synchronized(LOG) {
            if (blockCache == null) {
                // This is the first time getInstance has been called
                blockCache = new BlockCache();
            }
        }
        return blockCache;
    }  
    
    public void put(NewObjectIdentifier oid, long blockId, byte[][] block) {
        cache.put(new Key(oid, blockId), block);
    }
    
    public byte[][] get(NewObjectIdentifier oid, long blockId) {
        return (byte[][]) cache.get(new Key(oid, blockId));
    }

    // Singleton
    private static BlockCache blockCache = null;

    // LRU
    private Map cache = new LinkedHashMap(MAX_BLOCKS+1,
                                          LOAD_FACTOR,
                                          true) {
            public boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_BLOCKS;
            }
        };
    {
        cache = (Map)Collections.synchronizedMap(cache);
    }
    
    private static final Logger LOG = 
        Logger.getLogger(BlockCache.class.getName());
    private static final int MAX_BLOCKS = 20;
    private static final float LOAD_FACTOR = .75F;
}
