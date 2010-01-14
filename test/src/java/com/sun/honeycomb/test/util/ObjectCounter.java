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



package com.sun.honeycomb.test.util;

import java.util.*;

/**
 *  Count types of Objects, like unix 'sort file | uniq -c'.
 */
public class ObjectCounter {
    HashMap map = new HashMap();

    private class Count implements Comparable {
        String name;
        int value = 1;
        public Count(String name) {
            this.name = name;
        }
        public void increment() {
            value++;
        }
        public int compareTo(Object o) {
            if (! (o instanceof Count))
                return -1;
            Count c = (Count) o;
            if (c.value < this.value)
                return -1;
            if (c.value > this.value)
                return 1;
            return this.name.compareTo(c.name);
        }
    }

    public void count(Object o) {
        Count c = (Count) map.get(o);
        if (c == null) {
            map.put(o, new Count(o.toString()));
            return;
        }
        c.increment();
    }

    public StringBuffer sort() {
        StringBuffer sb = new StringBuffer();
        Object oo[] = map.values().toArray();
        Arrays.sort(oo);   
        for (int i=0; i<oo.length; i++) {
            Count c = (Count) oo[i];
            sb.append(c.value).append('\t').append(c.name).append('\n');
        }
        return sb;
    }
}
