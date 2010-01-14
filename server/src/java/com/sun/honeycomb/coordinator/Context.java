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

import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.common.NewObjectIdentifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Context implements Codable, Disposable, Releasable {

    private long accessTime;
    private boolean hasResources;
    private HashMap transientObjectMap;
    private HashMap persistentObjectMap;
    private long block; // used by coord when multiple w/ same oid

    public Context() {
        updateAccessTime();
        hasResources = true;
	block = -1;
    }

    void updateAccessTime() {
        accessTime = System.currentTimeMillis();
    }

    long getAccessTime() {
        return accessTime;
    }

    void setHasResources(boolean newHasResources) {
        hasResources = newHasResources;
    }

    boolean hasResources() {
        return hasResources;
    }

    public void registerTransientObject(String key, Object value) {
        if (transientObjectMap == null) {
            transientObjectMap = new HashMap();
        }

        transientObjectMap.put(key, value);
    }

    public Object removeTransientObject(String key) {
        return (transientObjectMap != null)
            ? transientObjectMap.remove(key)
            : null;
    }

    public Object getTransientObject(String key) {
        return (transientObjectMap != null)
            ? transientObjectMap.get(key)
            : null;
    }

    public void registerPersistentObject(String key, Codable value) {
        if (persistentObjectMap == null) {
            persistentObjectMap = new HashMap();
        }

        persistentObjectMap.put(key, value);
    }

    public Codable removePersistentObject(String key) {
        return (persistentObjectMap != null)
            ? (Codable)persistentObjectMap.remove(key)
            : null;
    }

    public Object getPersistentObject(String key) {
        return (persistentObjectMap != null)
            ? (Codable)persistentObjectMap.get(key)
            : null;
    }

    public void setBlock(long block) {
	this.block = block;
    }
    
    public long getBlock() {
	return block;
    }

    public void encode(Encoder encoder) {
        int size = (persistentObjectMap != null)
            ? persistentObjectMap.size()
            : 0;

        encoder.encodeInt(size);
        if (size > 0) {
            Iterator entries = persistentObjectMap.entrySet().iterator();

            while (entries.hasNext()) {
                Map.Entry entry = (Map.Entry)entries.next();

                encoder.encodeString((String)entry.getKey());
                encoder.encodeCodable((Codable)entry.getValue());
            }
        }
    }

    public void decode(Decoder decoder) {
        int size = decoder.decodeInt();

        if (size > 0) {
            persistentObjectMap = new HashMap(size);

            for (int i = 0; i < size; i++) {
                String key = decoder.decodeString();
                Object value = decoder.decodeCodable();

                persistentObjectMap.put(key, value);
            }
        }
    }

    public void dispose() {
        performAction(transientObjectMap, true);
        performAction(persistentObjectMap, true);
    }

    public void releaseResources() {
        performAction(transientObjectMap, false);
        performAction(persistentObjectMap, false);
    }

    private void performAction(Map map, boolean dispose) {
        hasResources = false;

        if (map != null) {
            Iterator entries = map.entrySet().iterator();

            while (entries.hasNext()) {
                Object value = ((Map.Entry)entries.next()).getValue();
                if (dispose) {
                    if (value instanceof Disposable) {
                        ((Disposable)value).dispose();
                    }
                } else if (value instanceof Releasable) {
                    ((Releasable)value).releaseResources();
                }
            }
        }
    }
}
