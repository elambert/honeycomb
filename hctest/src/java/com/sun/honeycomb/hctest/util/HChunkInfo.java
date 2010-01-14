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
 *  Holder for whitebox info about a chunk, for use within HOidInfo.
 */
public class HChunkInfo implements Serializable {

    private int chunk;
    private int layoutMapId;
    private String hashPath;

    private ArrayList frags = null;


    public boolean missingFrags = false;
    public boolean extraFrags = false;

    public HChunkInfo(int chunk, int layoutMapId, String hashPath) {
        this.chunk = chunk;
        this.layoutMapId = layoutMapId;
        this.hashPath = hashPath;
    }

    /**
     *  Add fragment.
     */
    public void addFrag(HFragmentInfo frag) {
        if (frags == null)
            frags = new ArrayList();
        frags.add(frag);
    }

    /**
     *  Get chunk id.
     */
    public int getChunkId() {
        return chunk;
    }

    /**
     *  Get layout map id.
     */
    public int getLayoutMapId() {
        return layoutMapId;
    }

    /**
     *  Get hashpath.
     */
    public String getHashPath() {
        return hashPath;
    }

    /**
     *  Get fragments.
     */
    public ArrayList getFragments() {
        return frags;
    }

    public String toString() {

        StringBuffer sb = new StringBuffer();

        sb.append("chunk: ").append(Integer.toString(chunk));
        sb.append("  layout ").append(Integer.toString(layoutMapId));
        sb.append("\n");

        if (frags == null) {
            sb.append("\tfrags null\n");
        } else if (frags.size() == 0) {
            sb.append("\tno frags\n");
        } else {
            for (int i=0; i<frags.size(); i++) {
                HFragmentInfo fi = (HFragmentInfo) frags.get(i);
                sb.append(fi.list(hashPath));
            }
        }
        return sb.toString();
     }       
}
