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



package com.sun.honeycomb.spreader;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.HttpClient;

import com.sun.honeycomb.config.ClusterProperties;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

import java.util.Iterator;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.logging.Logger;

/**
 * Gets and parses the L2 table from the switch
 *
 * @author Shamim Mohamed
 * @version $Id: SwitchPorts.java 10855 2007-05-19 02:54:08Z bberndt $
 */
public class SwitchPorts {
    private static final Logger logger =
        Logger.getLogger(SwitchPorts.class.getName());

    String myAddress;

    private HashMap nodeIdToMac;
    private HashMap macToNodeId;

    private HashMap macToPort;
    private String[] portToMac;

    SwitchPorts(ClusterProperties config) throws MalformedOutputException {
        initMaps(config);
    }

    int getPort(int nodeId) {
        String mac = getMacFromNodeId(nodeId);
        if (mac == null) return -1;
        return getPort(mac);
    }

    int nodeId(int port) {
        if (port < 0)
            return -1;
        String mac = getMacFromPort(port);
        if (mac == null) return -1;
        return nodeId(mac);
    }

    int getPort(String mac) {
        Integer p = (Integer) macToPort.get(mac);
        if (p == null ) return -1;
        return p.intValue();
    }

    String getMacFromPort(int port) {
        return portToMac[port];
    }

    int nodeId(String mac) {
        Integer i = (Integer) macToNodeId.get(mac);
        if (i == null) return -1;
        return i.intValue();
    }

    String getMacFromNodeId(int nodeID) {
        return (String) nodeIdToMac.get(new Integer(nodeID));
    }

    String getMyAddress() {
        return myAddress;
    }

    int getNumNodes() {
        return nodeIdToMac.size();
    }

    Iterator nodeIdIterator() {
        return nodeIdToMac.keySet().iterator();
    }

    int getPortFromSwitch(String mac) throws MalformedOutputException {
        return getMacPortMapsFromSwitch(mac);
    }

    int getPortFromSwitch(int nodeId) throws MalformedOutputException {
        String mac = getMacFromNodeId(nodeId);
        return getMacPortMapsFromSwitch(mac);
    }

    private void initMaps(ClusterProperties config)
            throws MalformedOutputException {
        /**
         * Initialise the structures that maintain data about the network
         * and the switch
         */

        // To figure out myAddress, we need my node ID
        int myID = 0;
        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (proxy == null) {
            throw new RuntimeException("cannot get node manager proxy");
        }
        myID = proxy.nodeId();

        // First, we get the MAC Addr <-> node ID maps from the
        // cluster properties
        getMacNodeMaps(config, myID);

        // Now we get the L2 table from the switch and get the 
        // switch port <-> MAC address map, which allows us to
        // figure out port <-> ID map
        getMacPortMapsFromSwitch(null);
    }


    private void getMacNodeMaps(ClusterProperties config, int myID) {
        /**
         * Gets the MAC -> NodeID map by asking each node for its MAC address
         *
         * @param myID the ID of the node this service is running on
         * @return the map from MAC address to node ID
         */

        nodeIdToMac = new HashMap();
        macToNodeId = new HashMap();

        // like "101 10.123.45.101 true, 102 10.123.45.102 true" etc.
        String nodeData = config.getProperty("honeycomb.cm.cmm.nodes");
        if (nodeData == null || nodeData.length() == 0)
            logger.severe("Property honeycomb.cm.cmm.nodes not set!");
        StringTokenizer nodes = new StringTokenizer(nodeData, ",");

        while (nodes.hasMoreTokens()) {
            StringTokenizer node = new StringTokenizer(nodes.nextToken());
            assert(node.hasMoreTokens());
            int nodeId = Integer.parseInt(node.nextToken());

            // HACK -- this should be done somewhere else!
            if (nodeId == myID)
                myAddress = node.nextToken();

            String macAddr = getMAC(nodeId);
            if (macAddr == null)
                continue;
            macToNodeId.put(macAddr, new Integer(nodeId));
            nodeIdToMac.put(new Integer(nodeId), macAddr);
        }

        if (myAddress == null || myAddress.length() == 0)
            throw new RuntimeException("Couldn't get my address " +
                                       "(node " + myID + ")");
    }

    private int getMacPortMapsFromSwitch(String mac)
        throws MalformedOutputException {
        /**
         * Talk to the switch and get the L2 table. This method
         * actually does two things: if called with mac == null, it
         * initialises everything; otherwise it only finds the port
         * the given MAC address is on.
         *
         * @param mac MAC address to look for
         * @return port number if MAC was specified
         */

        int lineNo = 0;
        String url = "http://10.123.45.1/http/cgi-bin/zl2-sun";

        if (mac == null) {
            macToPort = new HashMap();
            portToMac = new String[32];
        }

        try {
            BufferedReader f = HttpClient.getHttp(url, logger);
            String line;
            while ((line = f.readLine()) != null) {
                lineNo++;
                if (line.startsWith("    Mac Address")) // it's a header
                    continue;

                // Parse line. The output looks like this:
		//
		//     Mac Address    VLAN   FLAGS  HIT   PORT/TGID
                // 00:10:18:03:66:57     2     0     1      zre02
                //
                // We're interested in the MAC address and the
                // PORT. The PORT numbers range from 1 to 16 for a
                // Znyx switch.

                StringTokenizer st = new StringTokenizer(line);
                String macAddr = st.nextToken().toLowerCase();
                if (macAddr == null)
                    logger.severe("Couldn't find MAC addr in switch output");
                int nodeId = nodeId(macAddr);
                if (nodeId < 0)
                    continue;
                String vlan = st.nextToken();
                if (!vlan.equals("2"))
                    continue;

                // Skip next two fields
                st.nextToken(); st.nextToken();

                String sp = st.nextToken();
                if (sp == null)
                    logger.severe("Field 5 in line \"" + line + "\" null?");
                if (!sp.startsWith("zre"))
                    logger.severe("Doesn't start with 'zre': \"" + sp + "\"");
                int switchPort;
                try {
                    switchPort = Integer.parseInt(sp.substring(3));
                }
                catch (NumberFormatException e) {
                    logger.severe("From switch zl2-sun unexpected PORT: " +
                                  line);
                    continue;
                }

                if (switchPort < 1 || switchPort > 16)
                    logger.warning("Port " + switchPort + " for " + macAddr +
                                   " outside expected range 1..16");

                // Golden!

                if (mac == null || mac.equals(macAddr)) {
                    macToPort.put(macAddr, new Integer(switchPort));
                    portToMac[switchPort] = macAddr;

                    if (mac == null)
                        logger.info("Node " +
                                    nodeId(macAddr) + " (" + macAddr +
                                    ") : p" + switchPort);
                    else
                        logger.info("Found " + mac + " on port " + switchPort);

                    if (mac != null)
                        return switchPort;
                }

                // next line
            }
        }
        catch (IOException e) {
            logger.severe("Reading http output: " + e);
        }
        logger.info("Read " + lineNo + " zl2-sun lines from the switch");

        if (mac != null)
            logger.severe("Couldn't find MAC " + mac + " in L2 table!");
        return -1;
    }

    /** Get a node's MAC address from its platform proxy */
    private String getMAC(int nodeId) {
        Object obj = ServiceManager.proxyFor(nodeId, "PlatformService");
        if (obj == null || !(obj instanceof PlatformService.Proxy))
            return null;

        PlatformService.Proxy proxy = (PlatformService.Proxy) obj;
        logger.info(nodeId + "@" + proxy.getMACAddress());
        return proxy.getMACAddress();
    }

}
