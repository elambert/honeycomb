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



package com.sun.honeycomb.util;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.util.Ipmi;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;

/*
 * This class manages the node power service
 */
public class IpmiPowerController implements PowerController {

    private Map nodeAddrs = null;

    private static final Logger logger =
        Logger.getLogger(IpmiPowerController.class.getName());

    protected IpmiPowerController() {
        getNodeAddrs();
    }
    
    public boolean powerOff (int nodeId) {
        logger.info("powering off node " + nodeId);
        try {
            return Ipmi.powerOff(nodeId);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't power off hcb"+nodeId+": ", e);
        }
        return false;
    }

    public boolean powerOn (int nodeId) {
        logger.info("Powering ON node " + nodeId);
        try {
            return Ipmi.powerOn(nodeId);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't power off hcb"+nodeId+": ", e);
        }
        return false;
    }

    public boolean powerCycle (int nodeId) {
        logger.info("Powering cycling node " + nodeId);
        try {
            return Ipmi.powerCycle(nodeId);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't power cycle hcb"+nodeId+": ", e);
        }
        return false;
    }

    public boolean reset (int nodeId) {
        logger.info("RESET " + nodeId);
        try {
            return Ipmi.powerReset(nodeId);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't reset hcb" +nodeId+ ": ", e);
        }
        return false;
    }

    public void start() {
    }

    public void stop() {
    }

    /** Lookup addresses for all nodes */
    private void getNodeAddrs() {
        nodeAddrs = new HashMap();
        NodeMgrService.Proxy proxy = 
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        Node[] nodes = proxy.getNodes();
        if (nodes == null || nodes.length == 0) {
            // If we couldn't get the nodes, it's serious
            logger.severe("Null node list from NodemgrMailbox!");
            throw new InternalException("Couldn't get node list");
        }

        for (int i = 0; i < nodes.length; i++) {
            InetAddress addr;
            String nodeAddress = nodes[i].getAddress();
            try {
                addr = InetAddress.getByName(nodeAddress);
            } catch(UnknownHostException e) {
                throw new InternalException("Couldn't lookup address: " + 
                                            nodeAddress);
            }
            nodeAddrs.put(new Integer(nodes[i].nodeId()), addr);
        }
    }

    private InetAddress getAddr(int nodeId) {
        return (InetAddress) nodeAddrs.get(new Integer(nodeId));
    }

}
