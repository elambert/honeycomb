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

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;

import java.util.StringTokenizer;
import java.util.logging.Logger;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;

/*
 * This class manages the node power service
 */
public class RpcPowerController implements PowerController {

    private static final Logger logger =
        Logger.getLogger(RpcPowerController.class.getName());

    public static final String HC_MAC_POWER_PORT_STRING =
        "honeycomb.cell.mac_powerports";

    public static final String HC_CMM_NODES_STRING =
        "honeycomb.cm.cmm.nodes";

    private ClusterProperties config = null;

    /**
     * Method to configure the power manager. This does nothing.
     *
     * @throws Exception when the configuration cannot be done
     */
    protected RpcPowerController() {
        config = ClusterProperties.getInstance();
    }

    protected RpcPowerController(ClusterProperties config) {
        this.config = config;
    }

    public boolean powerOff (int nodeId) {
        String[] MACnRPC = getMACandRPCInfo(nodeId);
        PowerControllerClient rpc 
            = new BaytechPowerControllerClient("rpc" + MACnRPC[1]);
        logger.info ("non-graceful power-off of node " + nodeId
            + " rpc" + MACnRPC[1] + "-" + MACnRPC[2] + " commencing");
        return rpc.powerOff (Integer.parseInt (MACnRPC[2]));
    }

    public boolean powerOn (int nodeId) {
        String[] MACnRPC = getMACandRPCInfo(nodeId);
        PowerControllerClient rpc 
            = new BaytechPowerControllerClient("rpc" + MACnRPC[1]);
        logger.info ("power-in of node " + nodeId 
            + " rpc" + MACnRPC[1] + "-" + MACnRPC[2] + " commencing");
        return rpc.powerOn (Integer.parseInt (MACnRPC[2]));
    }

    public boolean powerCycle(int nodeId) {
        if (!powerOff(nodeId)) {
            logger.severe("Couldn't power off node " + nodeId);
            return false;
        }
        try {
            // HACK -- fix me!
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
        return powerOn(nodeId);
    }

    public boolean reset (int nodeId) {
        return true;
    }

    /**
     * Method to start the power service.
     *
     * @throws Exception when the service cannot be started
     */
    public void start() {
        String configString =
            config.getProperty(HC_MAC_POWER_PORT_STRING);
        StringTokenizer nodes = new StringTokenizer(configString);

        // Parse through tokens of the form <mac>-<rpc>-<port>
        int nodeNumber = 1;
        while (nodes.hasMoreTokens()) {
            // We need the second(rpc) and third(port) tokens to switch
            // on the node.
            StringTokenizer nodeline =
                new StringTokenizer(nodes.nextToken(), "-");
            nodeline.nextToken();
            String rpc = "rpc" + nodeline.nextToken();
            if ((!rpc.equals("rpc1")) && (!rpc.equals("rpc2"))) {
                logger.severe("Invalid rpc value [" + rpc + "]");
                throw new RuntimeException("Rpc [" + rpc +
                                           "] is invaild");
            }

            int port = Integer.parseInt(nodeline.nextToken());

            logger.info("Switching on port [" + port +
                        "] on rpc [" + rpc + "]");
            // Power on the port
            PowerControllerClient rpcClient =
                new BaytechPowerControllerClient(rpc);

            if (!rpcClient.powerOn(port)) {
                throw new RuntimeException ("Failed to power on port [" +
                                            port +
                                            "] on rpc [" + rpc + "]");
            }
        }
    }

    /**
     * Method to stop the power service.
     *
     * @param config the properties file for the cell
     * @throws Exception when the service cannot be stopped
     */
    public void stop() {
        String configString =
            config.getProperty(HC_MAC_POWER_PORT_STRING);
        StringTokenizer nodes = new StringTokenizer(configString);

        // Parse through tokens of the form <mac>-<rpc>-<port>
        int nodeNumber = 1;
        while (nodes.hasMoreTokens()) {
            // We need the second(rpc) and third(port) tokens to switch
            // on the node.
            StringTokenizer nodeline =
                new StringTokenizer(nodes.nextToken(), "-");
            nodeline.nextToken();
            String rpc = "rpc" + nodeline.nextToken();
            if ((!rpc.equals("rpc1")) && (!rpc.equals("rpc2"))) {
                logger.severe("Invalid rpc value [" + rpc + "]");
                throw new RuntimeException("Rpc [" + rpc +
                                           "] is invaild");
            }

            int port = Integer.parseInt(nodeline.nextToken());

            logger.info("Switching off port [" + port +
                        "] on rpc [" + rpc + "]");
            // Power on the port
            PowerControllerClient rpcClient =
                new BaytechPowerControllerClient(rpc);

            if (!rpcClient.powerOff(port)) {
                throw new RuntimeException ("Failed to power off port [" +
                                            port +
                                            "] on rpc [" + rpc + "]");
            }
        }
    }

    /**
     * Method to print the test result status.
     */
    private void printStatus(boolean passed, String errorCase) {
        System.out.print("Case " + errorCase);
        if (passed) {
            System.out.println("\t[Passed] ");
        } else {
            System.out.println("\t[Failed] ");
        }
    }

    // private 

    /**
     * Looks up this nodes MAC address by mapping the position of it's nodeid
     * in the cluster config to the MAC address/power port line. Returns that
     * same line parsed into a String array where:
     * <br>String[0] = MAC
     * <br>String[1] = rpc
     * <br>String[2] = port
     */
    private String[] getMACandRPCInfo (int nodeid) {
        String[] mac = null;

        ClusterProperties config = ClusterProperties.getInstance();

        String[] nodes;
        String[] macs;
        nodes = config.getProperty (HC_CMM_NODES_STRING).split ("\\s*,\\s*");
        macs  = config.getProperty (HC_MAC_POWER_PORT_STRING).split("\\s+");

        for (int i = 0; i < nodes.length; i++) {
            String[] node = nodes[i].split("\\s+", 3);
            if (node[0].equals (String.valueOf (nodeid))) {
                mac = macs[i].split("-");
                break;
            }
        }

        return mac;
    }

    private int nodeId () {
        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor (ServiceManager.LOCAL_NODE);
        if (proxy == null) {
            throw new RuntimeException("Cannot access local node proxy");
        }
        return proxy.nodeId();
    }

    public static void main(String[] args) throws Exception {
        ClusterProperties config = ClusterProperties.getInstance();
        String nodePropertyValue = null;
        boolean passed = false;
        String errorCase = null;

        RpcPowerController rpc = new RpcPowerController(config);

        passed = true;
        errorCase = new String("[1] [No errors]");
        nodePropertyValue =
            "11:11:11:11:11:11-1-1 " +
            "22:22:22:22:22:22-2-2 ";
        try {
            config.put(HC_MAC_POWER_PORT_STRING, nodePropertyValue);
        } catch (ServerConfigException e) {
            System.out.println("Internal server error setting: " + HC_MAC_POWER_PORT_STRING);
            passed=false;
        }
        try {
            rpc.start();
            rpc.stop();
        } catch (Exception e) {
            System.out.println(e);
            passed = false;
        }
        rpc.printStatus(passed, errorCase);

        passed = false;
        errorCase = new String("[2] [start with invalid rpc 3 in entry 1]");
        nodePropertyValue = new String(
            "11:11:11:11:11:11-3-1 " +
            "22:22:22:22:22:22-2-2 "
            );
        try {
            config.put(HC_MAC_POWER_PORT_STRING, nodePropertyValue);
        } catch (ServerConfigException e) {
            System.out.println("Internal server error setting: " + HC_MAC_POWER_PORT_STRING);
            passed=false;
        }
        try {
            rpc.start();
        } catch (IllegalArgumentException e) {
            System.out.println(e);
            passed = true;
        }
        rpc.printStatus(passed, errorCase);

        passed = false;
        errorCase = new String("[3] [stop with invalid rpc 3 in entry 1]");
        try {
            rpc.stop();
        } catch (IllegalArgumentException e) {
            System.out.println(e);
            passed = true;
        }
        rpc.printStatus(passed, errorCase);

        passed = false;
        errorCase = new String("[4] [start with invalid port 9 in entry 1]");
        nodePropertyValue = new String(
            "11:11:11:11:11:11-1-9 " +
            "22:22:22:22:22:22-2-2 "
            );
        try {
            config.put(HC_MAC_POWER_PORT_STRING, nodePropertyValue);
        } catch (ServerConfigException e) {
            System.out.println("Internal server error setting: " + HC_MAC_POWER_PORT_STRING);
            passed=false;
        }
        try {
            rpc.start();
        } catch (IllegalArgumentException e) {
            System.out.println(e);
            passed = true;
        }
        rpc.printStatus(passed, errorCase);

        passed = false;
        errorCase = new String("[5] [stop with invalid port 9 in entry 1]");
        try {
            rpc.stop();
        } catch (IllegalArgumentException e) {
            System.out.println(e);
            passed = true;
        }
        rpc.printStatus(passed, errorCase);
    }
}
