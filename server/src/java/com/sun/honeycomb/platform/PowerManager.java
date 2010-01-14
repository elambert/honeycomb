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



package com.sun.honeycomb.platform;

import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.util.PowerControllerClient;
import com.sun.honeycomb.util.BaytechPowerControllerClient;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;

/*
 * This class manages the node power service
 */
public class PowerManager {

    private static final Logger logger =
	Logger.getLogger(PowerManager.class.getName());

    public static final String HC_MAC_POWER_PORT_STRING =
	"honeycomb.cell.mac_powerports";

    public static final String HC_CMM_NODES_STRING =
        "honeycomb.cm.cmm.nodes";


    static boolean powerOff () {
        String[] MACnRPC = getMACandRPCInfo(nodeId());
        PowerControllerClient rpc 
            = new BaytechPowerControllerClient("rpc" + MACnRPC[1]);
        if (logger.isLoggable(Level.INFO))
            logger.info("non-graceful power-off of node " + nodeId() 
                        + " rpc" + MACnRPC[1] + "-" + MACnRPC[2]
                        + " commencing");
        return rpc.powerOff (Integer.parseInt (MACnRPC[2]));
    }

    static boolean powerOn (int nodeid) {
        String[] MACnRPC = getMACandRPCInfo(nodeid);
        PowerControllerClient rpc 
            = new BaytechPowerControllerClient("rpc" + MACnRPC[1]);
        if (logger.isLoggable(Level.INFO))
            logger.info("power-in of node " + nodeid 
                        + " rpc" + MACnRPC[1] + "-" + MACnRPC[2] 
                        + " commencing");
        return rpc.powerOn (Integer.parseInt (MACnRPC[2]));
    }

    /**
     * Method to configure the power manager. This does nothing.
     *
     * @param cellConfig the properties file for the cell
     * @throws Exception when the configuration cannot be done
     */
    public static void configure(Properties cellConfig)
	throws Exception {
    }

    /**
     * Method to start the power service.
     *
     * @param cellConfig the properties file for the cell
     * @throws Exception when the service cannot be started
     */
    public static void start(Properties cellConfig)
	throws Exception {
	String configString =
	    cellConfig.getProperty(HC_MAC_POWER_PORT_STRING);
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
		throw new IllegalArgumentException("Rpc [" + rpc +
						   "] is invaild");
	    }

	    int port = Integer.parseInt(nodeline.nextToken());

	    if (logger.isLoggable(Level.INFO))
                logger.info("Switching on port [" + port +
                            "] on rpc [" + rpc + "]");
	    // Power on the port
	    PowerControllerClient rpcClient =
		new BaytechPowerControllerClient(rpc);

	    if (!rpcClient.powerOn(port)) {
		throw new Exception ("Failed to power on port [" + port +
				     "] on rpc [" + rpc + "]");
	    }
	}
    }

    /**
     * Method to stop the power service.
     *
     * @param cellConfig the properties file for the cell
     * @throws Exception when the service cannot be stopped
     */
    public static void stop(Properties cellConfig)
	throws Exception {
	String configString =
	    cellConfig.getProperty(HC_MAC_POWER_PORT_STRING);
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
		throw new IllegalArgumentException("Rpc [" + rpc +
						   "] is invaild");
	    }

	    int port = Integer.parseInt(nodeline.nextToken());

	    if (logger.isLoggable(Level.INFO))
                logger.info("Switching off port [" + port +
                            "] on rpc [" + rpc + "]");
	    // Power on the port
	    PowerControllerClient rpcClient =
		new BaytechPowerControllerClient(rpc);

	    if (!rpcClient.powerOff(port)) {
		throw new Exception ("Failed to power off port [" + port +
				     "] on rpc [" + rpc + "]");
	    }
	}
    }

    /**
     * Method to print the test result status.
     */
    private static void printStatus(boolean passed, String errorCase) {
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
    private static String[] getMACandRPCInfo (int nodeid) {
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

    private static int nodeId () {
        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor (ServiceManager.LOCAL_NODE);
        if (proxy == null) {
            throw new RuntimeException("Cannot access local node proxy");
        }
        return proxy.nodeId();
    }

    public static void main(String[] args) throws Exception {
	Properties cellConfig = new Properties();
	String nodePropertyValue = null;
	boolean passed = false;
	String errorCase = null;

	passed = true;
	errorCase = new String("[1] [No errors]");
	nodePropertyValue =
	    "11:11:11:11:11:11-1-1 " +
	    "22:22:22:22:22:22-2-2 ";
	cellConfig.setProperty(HC_MAC_POWER_PORT_STRING, nodePropertyValue);
	try {
	    PowerManager.start(cellConfig);
	    PowerManager.stop(cellConfig);
	} catch (Exception e) {
	    System.out.println(e);
	    passed = false;
	}
	printStatus(passed, errorCase);

	passed = false;
	errorCase = new String("[2] [start with invalid rpc 3 in entry 1]");
	nodePropertyValue = new String(
	    "11:11:11:11:11:11-3-1 " +
	    "22:22:22:22:22:22-2-2 "
	    );
	cellConfig.setProperty(HC_MAC_POWER_PORT_STRING, nodePropertyValue);
	try {
	    PowerManager.start(cellConfig);
	} catch (IllegalArgumentException e) {
	    System.out.println(e);
	    passed = true;
	}
	printStatus(passed, errorCase);

	passed = false;
	errorCase = new String("[3] [stop with invalid rpc 3 in entry 1]");
	try {
	    PowerManager.stop(cellConfig);
	} catch (IllegalArgumentException e) {
	    System.out.println(e);
	    passed = true;
	}
	printStatus(passed, errorCase);

	passed = false;
	errorCase = new String("[4] [start with invalid port 9 in entry 1]");
	nodePropertyValue = new String(
	    "11:11:11:11:11:11-1-9 " +
	    "22:22:22:22:22:22-2-2 "
	    );
	cellConfig.setProperty(HC_MAC_POWER_PORT_STRING, nodePropertyValue);
	try {
	    PowerManager.start(cellConfig);
	} catch (IllegalArgumentException e) {
	    System.out.println(e);
	    passed = true;
	}
	printStatus(passed, errorCase);

	passed = false;
	errorCase = new String("[5] [stop with invalid port 9 in entry 1]");
	try {
	    PowerManager.stop(cellConfig);
	} catch (IllegalArgumentException e) {
	    System.out.println(e);
	    passed = true;
	}
	printStatus(passed, errorCase);
    }
}
