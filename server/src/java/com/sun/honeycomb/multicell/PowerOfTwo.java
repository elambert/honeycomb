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



package com.sun.honeycomb.multicell;

import java.util.List;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.multicell.mgmt.client.GetHCPowerOfTwo;
import com.sun.honeycomb.multicell.lib.MultiCellLib;


public class PowerOfTwo extends PowerOfTwoBase
{

    public PowerOfTwo() {
	super();
    }
    
    public PowerOfTwo(boolean dummy){
        super(dummy);
    }

    // Should be static but needs to be abstract for emulator
    // PowerOfTwo may not have been initialized yet.
    public void updateDiskCapacity(CellInfo curCell) {

        long totalCapacity = 0;
        long usedCapacity = 0;

        Node [] nodes = getNodeMgrProxy().getNodes();
        for (int i = 0; i < nodes.length; i++) {
            Node curNode = nodes[i];
            if (curNode == null || (!curNode.isAlive())) {
                continue;
            }
            DiskProxy diskProxy = getDiskMonitorProxy();
            Disk [] curDisks = diskProxy.getDisks(curNode.nodeId());
            if (curDisks != null) { 
                for (int j = 0; j < curDisks.length; j++) {
                    if (curDisks[j] != null) {
                        if (curDisks[j].isEnabled()) {
                            totalCapacity += curDisks[j].getDiskSize();
                            usedCapacity += curDisks[j].getUsedSize();
                        }
                    }
                }
            }
        }
        curCell.setCurUsedCapacity(usedCapacity);
        curCell.setCurTotalCapacity(totalCapacity);
    }

    private static NodeMgrService.Proxy getNodeMgrProxy() {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            throw new MultiCellError("can't get proxy for ServiceManager");
        }
        NodeMgrService.Proxy nodeMgr = (NodeMgrService.Proxy) obj;
        return nodeMgr;
    }

    private static DiskProxy getDiskMonitorProxy() {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE,
                                             "DiskMonitor");
        if (! (obj instanceof DiskProxy)) {
            throw new MultiCellError(
               "unable to acquire to disk monitor proxy");
        }
        return ((DiskProxy) obj);
    }
}
