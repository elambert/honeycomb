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



package com.sun.honeycomb.admin.mgmt.server;

import java.math.BigInteger;

import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.admin.mgmt.AdminException;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.admin.mgmt.ClusterManagement;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.common.CliConstants;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCNodeAdapter 
    extends HCFruAdapter implements HCNodeAdapterInterface {

    protected int nodeId;
    private Node node = null;
    private MgmtServer           mgmtServer;
    private ClusterManagement    cltMgmt;

     private static transient final Logger logger = 
         Logger.getLogger(HCNodeAdapter.class.getName());


    public HCNodeAdapter() {
        super();
    }

    public void loadHCFru()
        throws InstantiationException {
    }


    public void loadHCNode(BigInteger _nodeId)
        throws InstantiationException {
        nodeId =  _nodeId.intValue();
        node=null;
        try {
            NodeMgrService.Proxy nodeMgr = Utils.getNodeMgrProxy(nodeId);
            node = nodeMgr.getNode();
        } catch (AdminException ae) {
            logger.warning("Node unavailable - creating dummy for node:" + _nodeId);

        }

        mgmtServer = MgmtServer.getInstance();
        cltMgmt = ClusterManagement.getInstance();
    } 
    /*
    * This is the list of accessors to the object
    */


    public BigInteger getNodeId() throws MgmtException {
        return BigInteger.valueOf(nodeId);
    }

    public String getFruName() throws MgmtException {
        return nodeId+"";
    }


    public String getHostname() throws MgmtException {
        if(null!=node) {
            return node.getName();
        } else {
            return "unavailable";
        }
    }

    public String getFruId() throws MgmtException {
        try {
            String prop = "root." + nodeId + 
                ".PlatformService.biosInfo.UUID";
            AlertApi.AlertViewProperty alertView = mgmtServer.getAlertView();
            if (alertView != null){
                AlertApi.AlertObject o = alertView.getAlertProperty(prop);
                return o.getPropertyValueString();
            }
        } catch (AlertException e) {
            mgmtServer.logger.log(Level.SEVERE,
                                  "Error while retrieving the firmware versions" +
                                  e.getMessage(), e);
        }
        return "unavailable";

    }

    public BigInteger getFruType() throws MgmtException {
        return BigInteger.valueOf(CliConstants.HCFRU_TYPE_NODE);
    }

    public BigInteger getStatus() throws MgmtException {
        if(null==node || node.isOff()) {
            return(BigInteger.valueOf(CliConstants.HCNODE_STATUS_POWERED_DOWN));
        }else if(node.isAlive()) {
            return(BigInteger.valueOf(CliConstants.HCNODE_STATUS_ONLINE));
        } else {
            return(BigInteger.valueOf(CliConstants.HCNODE_STATUS_OFFLINE));
        }
    }

    public Boolean getIsAlive() throws MgmtException {
        if(null==node) {
            return new Boolean(false);
        }
        return new Boolean(node.isAlive());
    }

    public Boolean getIsEligible() throws MgmtException {
        if(null==node) {
            return new Boolean(false);
        }
        return new Boolean(node.isEligible());

    }

    public Boolean getIsMaster() throws MgmtException {
        if(null==node) {
            return new Boolean(false);
        }
        return new Boolean(node.isMaster());

    }

    public Boolean getIsViceMaster() throws MgmtException {
        if(null==node) {
            return new Boolean(false);
        }
        return new Boolean(node.isViceMaster());

    }

    public BigInteger getDiskCount() throws MgmtException {
        try {
            return BigInteger.valueOf(Utils.getDisks().length);
        } catch (AdminException ae) {
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to retrieve the number of disks");
        }
    }

    public BigInteger reboot(BigInteger dummy) throws MgmtException {
        if(null!=node) {
            cltMgmt.rebootNode(nodeId);
            return BigInteger.valueOf(0);
        } else {          
            //
            // Internationalize here
            //  
            throw new MgmtException("Cannot unavailable node.");
        }
    }

}
