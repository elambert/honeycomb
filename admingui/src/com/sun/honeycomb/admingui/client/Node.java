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

import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.XmlRpcParamDefs;
import com.sun.honeycomb.common.CliConstants;
import java.lang.reflect.Array;

/**
 * encapsulates node information
 */
public class Node extends Fru{
    
    private int nodeid;
    private boolean alive;
    private Disk[] disks;

    public static final int OFFLINE = CliConstants.HCNODE_STATUS_OFFLINE;
    public static final int ONLINE  = CliConstants.HCNODE_STATUS_ONLINE;
    public static final int PWR_DOWN = CliConstants.HCNODE_STATUS_POWERED_DOWN;
    
    /** Creates a new instance of Node */
    Node(AdminApi api, Cell c, int id, boolean alive, int status, String fru) {
        super(api, c, String.valueOf(id), 
                        CliConstants.HCFRU_TYPE_NODE, status, fru);
        this.nodeid = id;
        this.alive = alive;
        this.disks = null;
    }

    public int getNodeID() { return nodeid; }
    public boolean isAlive() { return alive; }
    public Disk[] getDisks() { return disks; }
    void setDisks(Disk[] disks) { this.disks = disks; }
    public Disk getDisk(String diskId) {
        // Might want to optimize this, maybe.
        if (disks == null) {
            return null;
        }
        for (int i = 0; i < disks.length; i++) {
            if (disks[i].getDiskId().equals(diskId)) {
                return disks[i];
            }
        }
        return null;
    }
    
    public void refresh() throws ClientException, ServerException {
        ObjectFactory.clearCacheMethod(XmlRpcParamDefs.GETNODE);
        ObjectFactory.clearCacheMethod(XmlRpcParamDefs.GETDISKS);
        Node newInst = api.getNode(theCell, nodeid);
        Disk[] newDisksOnNode = api.getDisks(newInst, false);
        this.alive = newInst.isAlive();
        this.disks = newDisksOnNode;
        this.fru = newInst.getFRU();
    }

    public String toString() {
        String s = "node{" + nodeid + ",alive=" + alive + ",dsks[";
        if (disks == null)
                s += null;
        else
            for (int i = 0; i < disks.length; i++)
                    s += disks[i];
        s += ",fru=" + fru;
        s += "]}";
        return s;
    }
    
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Node)) {
            return false;
        }
        Node n = (Node)obj;
        return n.getNodeID() == this.getNodeID();

    }
    
    public int hashCode() {
        int hash = 10000;
        if (this.theCell == null) {
            return hash*this.getNodeID();
        }
        return hash*theCell.getID()*this.getNodeID();
    }
}
