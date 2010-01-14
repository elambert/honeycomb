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



package com.sun.honeycomb.hctest.util;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *  Holder for whitebox info about a fragment, for use within HOidInfo.
 */
public class HFragmentInfo implements Serializable {

    int frag_num;

    private ArrayList disks = null;

    public HFragmentInfo(int frag_num) {
        this.frag_num = frag_num;
    }

    /**
     *  Add disk prefix.
     */
    public void addDisk(String disk, long fsize, String refs) {
        if (disks == null)
            disks = new ArrayList();
        disks.add(new HFragmentInstance(frag_num, fsize, disk, refs));
    }

    /**
     *  Get the disk array.
     */
    public ArrayList getDisks() {
        return disks;
    }

    /**
     * Get frag num.
     */
    public int getFragNum() {
        return (frag_num);
    }

    /**
     *  Get path.
     */
    public String getPath(String hashPath) {
        // XXX assumes one frag?
        return disks.get(0).toString() + hashPath + Integer.toString(frag_num);
    }

    /**
     *  For HChunkInfo's toString().
     */
    public String list(String hashPath) {

        StringBuffer sb = new StringBuffer();

        sb.append("\tfragment ").append(Integer.toString(frag_num));

        if (disks == null) {
            sb.append("  disks null\n");
        } else if (disks.size() == 0) {
            sb.append("  no disks\n");
        } else {
            if (disks.size() > 1)
                sb.append("  duplicates:\n");
            else
                sb.append("\n");
            for (int i=0; i<disks.size(); i++) {
                sb.append("\t\t").append(disks.get(i).toString());
                if (hashPath != null)
                    sb.append(hashPath).append(Integer.toString(frag_num));
                sb.append("\n");
            }
        }
        return sb.toString();
     }       
}
