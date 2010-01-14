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



package com.sun.honeycomb.admingui.client;

import com.sun.honeycomb.common.CliConstants;

/**
 * encapsulets info about a honeycomb disk
 */
public class Disk extends Fru {

    private String diskid;
    private int mbytesCapacity;
    private int mbytesUsed;
    private Node node;

    // Disk status is usually shown as ENABLED or DISABLED -- if a disk has a
    // status equivalent to AVAILABLE, the CLI/GUI report is as ONLINE.  The
    // MISPLACED status is just a placeholder for when the disk is in the 
    // wrong spot during hot swap (TBD).
    public static final int DISABLED    = CliConstants.HCFRU_STATUS_DISABLED;
    public static final int ENABLED     = CliConstants.HCFRU_STATUS_ENABLED;
    public static final int AVAILABLE   = CliConstants.HCFRU_STATUS_AVAILABLE;
    public static final int OFFLINE     = CliConstants.HCFRU_STATUS_OFFLINE;
    public static final int MISPLACED   = CliConstants.HCFRU_STATUS_MISPLACED;
    public static final int ABSENT      = CliConstants.HCFRU_STATUS_ABSENT;
    
    Disk(AdminApi api, Node node, String diskid, int status, 
                        int mbytesCapacity, int mbytesUsed, String fru) {
        super(api, node.getCell(), diskid, 
                    CliConstants.HCFRU_TYPE_DISK, status, fru);
        this.node = node;       
        this.diskid = diskid;
        this.mbytesCapacity = mbytesCapacity;
        this.mbytesUsed = mbytesUsed;    
    }
    
    public String getDiskId() { return diskid; }
    /* capacities in megabytes (2^20 bytes) */
    public int getCapUsed() { return mbytesUsed; }
    public int getCapTotal() { return mbytesCapacity; }
    public Node getNode() { return node; }
    public int getStatus() { 
        if (!this.getNode().isAlive()) {
            status = Disk.OFFLINE;
        } 
        return status;
    }
      
    
    public void enable() throws ClientException, ServerException {
        api.enableDisk(this);
    }
    public void disable() throws ClientException, ServerException {
        api.disableDisk(this);
    }
    
    public String toString() {
        return "disk{" + diskid + ",status=" + status + "," + mbytesUsed + "/" +
                mbytesCapacity + ",n" +
                ((node == null) ? "-" : String.valueOf(node.getID())) +
                ",fru=" + fru + "}";
    }

}
