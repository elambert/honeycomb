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



package com.sun.honeycomb.disks;

import com.sun.honeycomb.delete.Constants;

public class Disk {

    private int nb;
    private boolean enabled;
    
    private static int NODE101 = 101;

    public Disk(int nNb) {
        nb = nNb;
        enabled = true;
    }

    public String getPath() {
        return(Constants.getDiskRoot()+"/"+nb);
    }
    
    public boolean isEnabled() {
        return(enabled);
    }

    public int nodeId() {
        /*
         * emulate always beening node 101
         */
        return(NODE101);
    }
    
    public void setDisabled() {
        enabled = false;
    }
    
    public void setEnabled() {
        enabled = true;
    }

    public int diskIndex() { 
        return(nb);
    }

    public DiskId getId() {
        return(new DiskId(0, 0, nodeId(), nb, 0));
    }

    public String toString() {
        return(getPath());
    }

    public boolean isNullDisk() {
        // XXX maybe change later for spontaneous fault injection
        return(false);
    }
}
