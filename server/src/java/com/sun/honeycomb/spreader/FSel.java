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



package com.sun.honeycomb.spreader;

/**
 * An FSel represents an entry in (index into) the IMASK table,
 * i.e. it's a set of masks, and has associated with it a group of rules.
 *
 * @author Shamim Mohamed
 * @version $Id: FSel.java 10855 2007-05-19 02:54:08Z bberndt $
 */

class FSel {

    // These are the types of rules in the system
    static final int DELETED = 0;
    static final int DATA = 1;
    static final int DEFAULT = 2;
    static final int ARP = 3;
    static final int ADMIN = 4;
    static final int DROP = 5;
    static final int ACCEPT = 6;
    static final int OUTBOUND = 7;
    static final int INBOUND = 8;

    static final int NUM_TYPES = 9;

    private int value = -1;
    private int start = 0;
    private int size = 0;
    private int type = -1;

    FSel() {
    }

    FSel(int val, int ty, int st, int sz) {
        value = val;
        size = sz;
        start = st;
        type = ty;
    }

    int value() { return value; }
    int start() { return start; }
    int size()  { return size; }
    int last()  { return start + size - 1; }
    int type()  { return type; }

    public String toString() {
        String s = "";
        switch (type) {
        case DELETED: s += "DELETED"; break;
        case DATA: s += "DATA"; break;
        case DEFAULT: s += "DEFAULT"; break;
        case ARP: s += "ARP"; break;
        case ADMIN: s += "ADMIN"; break;
        case DROP: s += "DROP"; break;
        case ACCEPT: s += "ACCEPT"; break;
        case OUTBOUND: s += "OUTBOUND"; break;
        case INBOUND: s += "INBOUND"; break;
        }
        return s + ":" + value() + "(" + start() + ":" + last() + ")";
    }
}
