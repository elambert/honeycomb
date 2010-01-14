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



package com.sun.honeycomb.emd;

import java.util.ArrayList;
import com.sun.honeycomb.emd.common.EMDException;
import java.util.Iterator;
import java.util.Map;

public class DerbyAttributes {

    public static final byte FLAG_PRIMARYKEY = 0x1;

    public interface Callback {
        public void step(Entry attribute)
            throws EMDException;
    }

    private ArrayList attributes;

    public DerbyAttributes() {
        attributes = new ArrayList();
    }

    public void add(String name,
                    String data) {
        attributes.add(new Entry(name, data));
    }

    public void add(String name,
                    String data,
                    byte flags) {
        attributes.add(new Entry(name, data, flags));
    }

    public void replace(String name,
                        String data) {
        Entry entry = null;
        int i;

        for (i=0; i<attributes.size(); i++) {
            entry = (Entry)attributes.get(i);
            if (entry.name.equals(name)) {
                break;
            }
        }

        if (i==attributes.size()) {
            add(name, data);
        } else {
            entry.data = data;
        }
    }

    public void addAll(Map map) {
        Iterator ite = map.keySet().iterator();
        while (ite.hasNext()) {
            String key = (String)ite.next();
            String value = (String) map.get(key);
            add(key, value);
        }
    }

    public void walk(Callback callback) 
        throws EMDException {
        Entry entry;

        for (int i=0; i<attributes.size(); i++) {
            entry = (Entry)attributes.get(i);
            callback.step(entry);
        }
    }
    
    public static class Entry {
        public String name;
        public String data;
        public byte flags;

        private Entry(String name,
                      String data) {
            this(name, data, (byte)0);
        }

        private Entry(String name,
                      String data,
                      byte flags) {
            this.name = name;
            this.data = data;
            this.flags = flags;
        }
    }
}
