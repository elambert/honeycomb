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



package com.sun.honeycomb.layout;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.Iterator;

/** Implements methods to querry the online status of disks in the list. */
public class DiskIdList extends ArrayList {

    public DiskIdList() {
        super();
    }

    /** construct list based on input string, eg: "102:3 105:1  103:0" */
    DiskIdList(String s) {

        super();
        StringTokenizer st = new StringTokenizer(s);
        int fragId = 0;
        while (st.hasMoreTokens()) {
            add(fragId++, new DiskId(st.nextToken()));
        }
    }

    /** convert Disk[] into DiskIdList */
    DiskIdList(Disk[] disks) {
        super();
        for (int i=0; i < disks.length; i++) {
            add((DiskId) disks[i].getId());
        }
    }

    public String toString() {

        String s = "";
        s += "[";
        Iterator iter = iterator();
        while (iter.hasNext()) {
            DiskId diskId = (DiskId)iter.next();
            if (diskId != null) {
                s += diskId.toStringShort();
            } else {
                s += " null";
            }
            if (iter.hasNext()) {
                s += " ";
            }
        }
        s += "]";
        return s;
    }

    /** used when computing layout maps */
    public int containsNodeCount(int nodeId) {

        int n = 0;
        for (int i=0; i < size(); i++) {

            Object obj = get(i);
            if (! (obj instanceof DiskId)) {
                continue;
            }
            if (((DiskId) obj).nodeId() == nodeId) {
                n++;
            }
        }
        return n;
    }

    /** convert to an array of Disk objects */
    Disk[] toDisks() {

        if (size() == 0) {
            return null;
        }
        Disk[] disks = new Disk[size()];
        for (int i=0; i < disks.length; i++) {
            DiskId id = (DiskId)get(i);
            disks[i] = DiskProxy.getDisk(id);
        }
        return disks;
    }

    /** compute Hamming Distance between two layouts */
    int hammingDistance(Layout other) {

        // only defined for layouts of same length
        if (other == null) {
            throw new NullPointerException("other Layout is null");
        }
        if (size() != other.size()) {
            throw new IllegalArgumentException("layouts differ in length");
        }

        // count number of disks that differ 
        int distance = 0;
        for (int i=0; i < size(); i++) {

            DiskId d1 = (DiskId)get(i);
            DiskId d2 = (DiskId)other.get(i);
            if ((d1 == null && d2 != null) || !d1.equals(d2)) {
                distance++;
            } 
        }
        return distance;
    }
    
}


