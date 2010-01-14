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


package com.sun.honeycomb.adm.common;

import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.admin.mgmt.client.HCFru;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import com.sun.honeycomb.admin.mgmt.client.HCSP;
import com.sun.honeycomb.admin.mgmt.client.HCSwitch;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Given a HCFru array this object takes all the fru's and
 * breaks them up into there discrete fru components
 * <B>
 */
public class FruObjects {
    
    private HCDisk[] disks;
    private HCNode[] nodes;
    private HCSwitch[] switches;
    private HCSP service_node;
    private int cellId;
    private boolean aliveCell = true;
    
    private static final Logger LOG = 
                            Logger.getLogger(FruObjects.class.getName());
    
    /** 
     * Creates a new instance of FruObjects
     * @param fru All fru objects for a given cell
     * @param id Cell identifier
     * @param isCellAlive Flag indicating whether or not this cell is alive
     */
    public FruObjects(HCFru[] fru, int id, boolean isCellAlive) {
        cellId = id;
        aliveCell = isCellAlive;
        List<HCDisk> disks = new ArrayList();
        List<HCNode> nodes = new ArrayList();
        List<HCSwitch> switches = new ArrayList();
        
        if (fru != null) {
            for (int i=0; i < fru.length; i++) {
                assert(fru[i] != null);
                if (fru[i] instanceof HCDisk)
                    disks.add((HCDisk)fru[i]);
                else if (fru[i] instanceof HCNode)
                    nodes.add((HCNode)fru[i]);
                else if (fru[i] instanceof HCSwitch)
                    switches.add((HCSwitch)fru[i]);
                else if (fru[i] instanceof HCSP)
                    service_node = (HCSP)fru[i];
            }
        }
        this.disks = (HCDisk[])disks.toArray(new HCDisk[disks.size()]);
        this.nodes = (HCNode[])nodes.toArray(new HCNode[nodes.size()]);
        this.switches = (HCSwitch[])switches.toArray(new HCSwitch[switches.size()]);
    }
    
    /**
     * @return int the id of the cell that all fru's associated with this
     * object belong to
     */
    public int getCellId() {
        return cellId;
    }
    
    /**
     * @return boolean flag indicating whether or not the cell is alive that 
     * all fru's associated with this object belong to
     */
    public boolean isCellAlive() {
        return aliveCell;
    }
    
    /**
     * @return HCDisk[] the disk fru's for the cell
     */
    public HCDisk[] getDisks() {
        return this.disks;
    }
    
    /**
     * @return HCDisk[] the disk fru's for the specified node
     */
    public HCDisk[] getDisksOnNode(int nodeID) {
        ArrayList diskList = new ArrayList();
        for (int idx = 0; idx < disks.length; idx++) {
            HCDisk d = disks[idx];
            // inefficient...
            if(d.getNodeId().intValue()==nodeID) {
                diskList.add(d);
            }
        }

        HCDisk[] disks = (HCDisk [])diskList.
            toArray(new HCDisk[diskList.size()]);

        return disks;
    }
    
    /**
     * @return HCNode[] the node fru's for the cell
     */
    public HCNode[] getNodes() {
        return this.nodes;
    }
    
    /**
     * @return HCSP the service node fru for the cell 
     */
    public HCSP getServiceNode() {
        return this.service_node;
    }
    
    /**
     * @return HCSwitch the switch fru's for the cell
     */
    public HCSwitch[] getSwitches() {
        return this.switches;
    }
    
}
