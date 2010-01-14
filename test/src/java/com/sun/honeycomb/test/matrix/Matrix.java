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



package com.sun.honeycomb.test.matrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

public class Matrix {

    private HashMap lookup;
    private Object [] order;
    private HashMap permutation;

    public Matrix() {
        this.lookup = new HashMap();
        this.order = null;
        this.permutation = null;
    }

    public void add(String name, Domain domain) {
        this.lookup.put(name, domain);
    }

    public void reset() {
        //System.err.println("reset");
        this.order = (new TreeSet(lookup.keySet())).toArray();
        this.permutation = new HashMap();
        for (int i = 0; i < this.order.length; i++) {
            Object name = this.order[i];
            Domain domain = (Domain) this.lookup.get(name);
            //System.out.println(name.toString() + ": " + domain);
            //System.out.println(name.toString() + ": reset");
            domain.reset();
            if (domain.hasNext()) {
                Object value = domain.next();
                //System.out.println(name.toString() + ": " + value);
                this.permutation.put(name, value);
            }
        }
    }

    public boolean hasNext() {
        return permutation != null;
    }

    public HashMap next() {
        HashMap nextPerm = this.permutation;
        this.permutation = new HashMap();

        int i = 0;
        //System.out.println("new values");
        while (i < order.length) {
            //System.out.println("i: " + i);
            Object name = this.order[i++];
            Domain domain = (Domain) this.lookup.get(name);
            //System.out.println(name.toString() + ": " + domain);
            if (domain.hasNext()) {
                //System.out.println(name + ": hasNext");
                Object value = domain.next();
                //System.out.println(name + ": " + value);
                this.permutation.put(name, value);
                break;
            } else {
                //System.out.println(name + ": !hasNext");
                if (i == order.length) {
                    //System.out.println(name + ": last");
                    this.permutation = null;
                }
                else {
                    //System.out.println(name + ": reset");
                    domain.reset();
                    if (domain.hasNext()) {
                        //System.out.println(name + ": hasNext");
                        Object value = domain.next();
                        //System.out.println(name + ": " + value);
                        this.permutation.put(name, value);
                    }
                }
            }
        }
        //System.out.println("old values");
        while (i < order.length) {
            //System.out.println("i: " + i);
            Object name = this.order[i++];
            Object value = nextPerm.get(name);
            //System.out.println(name + ": " + value);
            this.permutation.put(name, nextPerm.get(name));
        }
        return nextPerm;
    }

    public static void main(String [] argv)
        throws Throwable
    {
        System.out.println("...");
        Matrix m = new Matrix();
        m.add("a", new Booleans());
        m.add("b", new Booleans());
        m.reset();
        while (m.hasNext()) {
            HashMap perm = m.next();
            System.out.print(perm.get("a").toString() + ",");
            System.out.println(perm.get("b").toString());
        }

        System.out.println("...");
        m = new Matrix();
        m.add("a", new Booleans());
        m.add("b", new Booleans());
        m.add("c", new Booleans());
        m.reset();
        while (m.hasNext()) {
            HashMap perm = m.next();
            System.out.print(perm.get("a").toString() + ",");
            System.out.print(perm.get("b").toString() + ",");
            System.out.println(perm.get("c").toString());
        }

        System.out.println("...");
        m = new Matrix();
        m.add("a", new SimpleDomain(new Object [] {"one", "two", "three"}));
        m.add("b", new Booleans());
        m.add("c", new Booleans());
        m.reset();
        while (m.hasNext()) {
            HashMap perm = m.next();
            System.out.print(perm.get("a").toString() + ",");
            System.out.print(perm.get("b").toString() + ",");
            System.out.println(perm.get("c").toString());
        }

        System.out.println("...");
        m = new Matrix();
        m.add("a", new SimpleDomain(new Object [] {"one", "two", "three", null}));
        m.add("b", new Booleans());
        m.add("c", new Booleans());
        m.reset();
        while (m.hasNext()) {
            HashMap perm = m.next();
            System.out.print(perm.get("a") + ",");
            System.out.print(perm.get("b") + ",");
            System.out.println(perm.get("c"));
        }

    }

}
