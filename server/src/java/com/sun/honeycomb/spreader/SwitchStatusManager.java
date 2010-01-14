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

import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.protocol.server.ProtocolBase;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.spreader.SpreaderManagedService;
import com.sun.honeycomb.spreader.SpreaderService;
import com.sun.honeycomb.protocol.server.ProtocolProxy;
import com.sun.honeycomb.time.Time;
import com.sun.honeycomb.common.ConfigPropertyNames;

import java.io.*;
import java.util.*;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.text.MessageFormat;

/**
 * Monitors nodes going up or down and programs the switch to
 * compensate. This is the worker class for the SpreaderService.
 * It has no public methods.
 *
 * @author Shamim Mohamed
 * @version $Id: SwitchStatusManager.java 11713 2007-12-21 18:30:14Z jr152025 $
 */
public class SwitchStatusManager {
    private static final int FIRST_NODE = 101;
    private static final int MAX_NUM_NODES = 16;

    private static final int ADD = 1;
    private static final int DELETE = 2;
    private static final int RESET = 3;

    private static final String SVC_FS = "Webdav";

    ClusterProperties config = null;

    private static final int DEFAULT_RESET_FAILURE_LIMIT = 3;

    public static final int STATUS_SWITCH = 1;
    public static final int STATUS_NODES = 2;
    public static final int STATUS_ALL = STATUS_SWITCH | STATUS_NODES;

    String authorizedClients = null;

    //private CommSocket sock;

    Random random;

    // These are used to call zrule with the right values
    private List nodeList;
    private int srcMaskSize;
    private int portMaskSize;

    private int addrBits = -1;

    private int numAuthClientRules = 0;

    private int maxHVal;
    private int maxPVal;
    private int maxHashVal;

    private String dataVIP;
    private String adminVIP;
    private String myAddress;
    private int myID;

    private SwitchPorts switchPorts = null;
    private boolean previousUpdateSucceeded = false;
    private boolean previousVerifySucceeded = false;
    private int failureCount = 1; // To make sure update() runs
    private int failureLimit = DEFAULT_RESET_FAILURE_LIMIT;


    // Known states of nodes: to start, nothing is up
    private HashSet upNodes;

    // The list of switch rules (cached)
    private RuleSet rules;

    // The clients allowed to connect (empty => no restrictions)
    private String[] allowedClients;

    // The TCP ports that load is spread for
    private int[] loadSpreadPorts;

    // The switch port for ARP. 0 means the master node's port.
    private int arpPort = 0;
    // The switch port for ICMP. 0 means the master node's port.
    private int icmpPort = 0;
    // The switch port for NTP. 0 means the master node's port.
    private int ntpPort = 0;
    // The switch port for NDMP. 0 means the master node's port.
    private int ndmpPort = 0;

    // ndmp inbound and outbound data port values
    private int ndmpInboundDataPort = 10001;
    private int ndmpOutboundDataPort = 10002;

    // Should rules be verified after being sent to the switch?
    private boolean verifyRules = true;

    private boolean droppingAll = true;

    private NodeMonitor nodeMonitor;
    private boolean programSwitch; // whether it's a Znyx and should
                                   // be programmed

    private static final Logger logger =
        Logger.getLogger(SwitchStatusManager.class.getName());


    /**********************************************************************
     * Initialise with network configuration
     *
     * @param dataVIP the VIP used for data access
     * @param adminVIP the VIP for admin access
     * @param config the properties of the cluster
     * @param programSwitch whether it's a Znyx and should be programmed
     */
    SwitchStatusManager(String dataVIP, String adminVIP, int numNodes,
                        ClusterProperties config, boolean programSwitch)
		throws MalformedOutputException {
        initialize(dataVIP, adminVIP, numNodes, config, programSwitch);
    }

    /**********************************************************************
     * Initialise with network configuration
     *
     * @param dataVIP the VIP used for data access
     * @param adminVIP the VIP for admin access
     * @param config the properties of the cluster
     * @param programSwitch whether it's a Znyx and should be programmed
     */
    private void initialize(String dataVIP, String adminVIP, int numNodes,
                            ClusterProperties config, boolean programSwitch)
        throws MalformedOutputException {
		
        logger.info("Spreader StatusManager creation...");
		
        // Initialise multicast socket
        //sock = new CommSocket(myAddress);

        random = new Random(System.currentTimeMillis());
        upNodes = new HashSet(MAX_NUM_NODES*13/10);
        nodeList = new LinkedList();

        this.config = config;
        this.programSwitch = programSwitch;
        this.adminVIP = adminVIP;
        this.dataVIP = dataVIP;

        if (programSwitch)
            getInfoFromSwitch(config);

        // Get NDMP Inbound and OutBound Data Ports
        // FIXME: This should actually be done via NDMP Proxy
        // but since it does not exists, reading the value from
        // config properties file
        String port = null;
        try {
            port = config.getProperty(ConfigPropertyNames.NDMP_INBOUND_DATA_PORT);
            ndmpInboundDataPort = Integer.parseInt(port);

            port = config.getProperty(ConfigPropertyNames.NDMP_OUTBOUND_DATA_PORT);
            ndmpOutboundDataPort = Integer.parseInt(port);
        } catch(NumberFormatException e) {
            logger.warning("Could'nt parse NDMP inbound/outbound ports");
        }

        String prop;
        if ((prop = 
            config.getProperty(ConfigPropertyNames.PROP_SWITCH_ARP_DEST)) != null) {
            if (prop.equalsIgnoreCase("switch"))
                arpPort = ZNetlink2Message.CPUPORT;
            else if (prop.equalsIgnoreCase("master"))
                arpPort = 0;
            else
                logger.warning("ARP destination \"" + prop +
                               "\" unknown; using " + arpPort);
        }

        if ((prop = 
            config.getProperty(ConfigPropertyNames.PROP_SWITCH_ICMP_DEST)) != null) {
            if (prop.equalsIgnoreCase("switch"))
                icmpPort = ZNetlink2Message.CPUPORT;
            else if (prop.equalsIgnoreCase("master"))
                icmpPort = 0;
            else
                logger.warning("ICMP destination \"" + prop +
                               "\" unknown; using " + icmpPort);
        }


        if ((prop = 
            config.getProperty(ConfigPropertyNames.PROP_SWITCH_VERIFY)) != null) {
            String v = prop.trim();
            if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("no"))
                verifyRules = false;
            else if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"))
                verifyRules = true;
        }

        if ((prop = 
            config.getProperty(ConfigPropertyNames.PROP_RESET_FAILURE_LIMIT)) != null) {
            String v = prop.trim();
            try {
                failureLimit = Integer.parseInt(prop);
            }
            catch (NumberFormatException e) {
                logger.warning("Couldn't parse " +
                               ConfigPropertyNames.PROP_RESET_FAILURE_LIMIT +
                               " = \"" + prop + "\"; using default " +
                               failureLimit);
                
            }
        }

	int defaultPort = 
	  config.getPropertyAsInt(ProtocolConstants.API_SERVER_PORT_PROPERTY,
	    ProtocolConstants.DEFAULT_PORT);
        loadSpreadPorts = new int[]{
            defaultPort,
        };

        if (fsSvcActive()) {
            int[] p = new int[loadSpreadPorts.length + 1];
            for (int i = 0; i < loadSpreadPorts.length; i++)
                p[i] = loadSpreadPorts[i];
            p[loadSpreadPorts.length] = ProtocolConstants.WEBDAV_PORT;
            loadSpreadPorts = p;
            logger.info("FS service \"" + SVC_FS + ":" +
                        ProtocolConstants.WEBDAV_PORT + "\" is active.");
        }
        else
            logger.info("Not including service \"" + SVC_FS + "\".");

        authorizedClients = 
            config.getProperty(ConfigPropertyNames.PNAME_AUTH_CLIENTS);
        if ((authorizedClients != null) && (!authorizedClients.equals("")))
            allowedClients = parseClients(authorizedClients);
        

        // Calculate srcMaskSize, portMaskSize, maxPVal, maxHVal and maxHashVal
        if ((prop = 
            config.getProperty(ConfigPropertyNames.PROP_SPREADER_ADDRBITS)) != null) {
            try {
                addrBits = Integer.parseInt(prop);
            }
            catch (NumberFormatException e) {
                logger.warning("Couldn't parse " +
                               ConfigPropertyNames.PROP_SPREADER_ADDRBITS +
                               " = \"" + prop + "\"");
            }
        }

        computeHashes(numNodes);

        myID = getProxy().nodeId();

        //  Get the number of rules for authorized clients
        numAuthClientRules = 
            config.getPropertyAsInt(ConfigPropertyNames.PROP_SPREADER_AUTHRULES);
        if (numAuthClientRules < 4 || 
            numAuthClientRules > (maxPVal + 1)) {
            logger.warning("Invalid number of auth rules specified " +
                           "Should be >= 4 and  <= " + (maxPVal + 1) +
                           "Using the default " + (maxPVal + 1) +
                           "rules");
            
            // maxPVal is 3 for 16 nodes, if load spread on all nodes it is 15.
            numAuthClientRules = maxPVal + 1; // Same as number of nodes.
        }

        nodeMonitor = NodeMonitor.getInstance();

        // XXX: need to figure out the right way of doing this
        RuleSet r = new RuleSet(config, maxHVal, maxPVal);

        // Some debug output
        if (logger.isLoggable(Level.INFO)) {

            if (programSwitch) {
                logger.info("Spreader: admin VIP = " + this.adminVIP +
                            ", data VIP = " + this.dataVIP + 
                            ", my node ID = " + myID +
                            ", my address = " + myAddress);
                logger.info("ARP sent to " + arpPort +
                            ", ICMP to " + icmpPort);
                logger.info("MaxHashes/masksize: H = " +
                            maxHVal + "!" + srcMaskSize +
                            ", P = " + maxPVal + "!" + portMaskSize);

                if (failureLimit > 0)
                    logger.info("Reset switch if >" + failureLimit +
                                " failures.");

                if (allowedClients != null) {
                    String c = "";
                    for (int i = 0; i < allowedClients.length; i++)
                        c += allowedClients[i] + " ";
                    logger.info("Only accepting requests from " + c);
                }
            }
            else {
                logger.info("Spreader: not a Znyx switch; ID = " + this.myID +
                            ", admin VIP = " + this.adminVIP);
            }
        }
    }


    public String getDataVIP() {
        return dataVIP;
    }

    public String getAdminVIP() {
        return adminVIP;
    }

    public String getAddress() {
        return myAddress;
    }

    int getSrcAddrMaskSize() {
        return srcMaskSize;
    }

    int getSrcPortMaskSize() {
        return portMaskSize;
    }

    SwitchPorts getSwitchPorts() {
        return switchPorts;
    }

    /** This is the arbiter of which node is up and which isn't */
    boolean isNodeUp(int nodeId) {
        ProtocolProxy proxy = ProtocolProxy.getProxy(nodeId);
        if (proxy == null) {
            logger.warning("ProtocolProxy not ready on node " + nodeId);
            return false;
        }
        return proxy.isAPIReady();
    }

    /** If all the nodes in assignment array is marked as down */
    boolean areAllNodesDown(int [] assignments) {
        int numNodes = config.getPropertyAsInt(ConfigPropertyNames.PROP_NUMNODES);
        for (int i=0; i < numNodes; i++ ) {
            if (assignments[i] >= 0) 
                return false;
        } 
        return true;
    }

    /**
     * May be used in the remote API of the service
     *
     * @param srcHost source IP address
     * @param srcIpPort source TCP/UDP port
     * @param destIpPort destination TCP/UDP port
     * @return switch port that traffic will be sent to
     */
    int getPort(int srcHost, int srcIpPort, int destIpPort) {
        // Look through rules
        for (Iterator i = rules.iterator(); i.hasNext(); ) {
            Rule r = (Rule) i.next();
            if (r.getVIP().equals(dataVIP) && r.getSrcAddr() == srcHost &&
                r.getRuleType() == Rule.BY_PORT &&
                r.getDestPort() == destIpPort &&
                r.getSrcIpPort() == srcIpPort)

                return r.getSwitchPort();
        }

        return -1;
    }

    int getMasterNodePort() {
        if (programSwitch)
            return switchPorts.getPort(myID);
        else
            return -1;
    }

    /**
     * Do any initialization tht needs to be done after we're in the
     * RUNNING state. Start all the timers; when update is called,
     * running nodes will get their timers cancelled.
     */
    void start() {
        if (programSwitch) {
            logger.info("Reset switch rules");
            RuleSet.reset(getAddress());
        }
    }

    /**
     * Checks current status of nodes against the program in the
     * switch. It invokes zrule to send rules to the switch if any
     * changes need to be made -- this can be either because nodes
     * came up or went down, or a previous send by zrule failed, or
     * because the switch failed and the new one needs to be
     * programmed with all rules.
     */
    boolean update(int numNodes) {
        return update(SwitchStatusManager.STATUS_ALL, numNodes);
    }
    boolean update(int what, int numNodes) {
        boolean switchFailover = false;
        boolean configChanged = false;

        programSwitch = !isRebooting();
        if(programSwitch==false) {
            logger.info("Cluster is rebooting - disabling spreader switch programming");
        }
        // Update number of nodes
        computeHashes(numNodes);

        // No longer dropping everything
        if (droppingAll) {
            logger.info("No longer dropping all data traffic");
            RuleSet.reset(getAddress());
        }
        droppingAll = false;

        // Check to see if the config changed
        String authClients =
            config.getProperty(ConfigPropertyNames.PNAME_AUTH_CLIENTS);

        boolean mergeRules = true;
        if  ((authClients != null) && 
             (authClients.equalsIgnoreCase("all") ||
              authClients.equals(""))) {
            authClients = null;
        }
        if ((authClients == null) && (authorizedClients != null)) {
            mergeRules = false;
            configChanged = true;
            authorizedClients = null;
            allowedClients = null;
            logger.info("Config change: accepting connections from all clients");
        }
        if ((authorizedClients == null && authClients != null) ||
            ((authorizedClients != null) && (authClients != null)
             && (!authorizedClients.equals(authClients)))) {
            String msg = "Config change: now only accepting connections from "
                + authClients;
            logger.info(msg);
            configChanged = true;
            authorizedClients = authClients;
            allowedClients = parseClients(authorizedClients);
            mergeRules = false;
        }

        if (programSwitch && (what & STATUS_SWITCH) != 0) {
            // The port assignments may be different on the other switch
            getInfoFromSwitch(config);
            switchFailover = true;
            logger.info("Resetting switch rules on Failover");
            RuleSet.reset(getAddress());
        }

        if (!configChanged && !switchFailover && (what & STATUS_NODES) == 0)
            return true;

        int[] assignments = new int[maxHashVal + 1];   // actions to perform
        int[] load = new int[maxHashVal + 1];          // load on each port

        // Check all the nodes' statuses. The arrays are modified by
        // the function.
        boolean dirty = false;
        try {
            dirty = getNodeStatus(assignments, load);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't get load", e);
        }

        if (logger.isLoggable(Level.FINE)) {
            String msg = "";
            msg += (configChanged?"":"!") + "configChanged ";
            msg += (dirty?"":"!") + "dirty ";
            msg += (switchFailover?"":"!") + "switchFailover ";
            msg += (previousUpdateSucceeded?"":"!")+"previousUpdateSucceeded ";
            msg += (previousVerifySucceeded?"":"!")+"previousVerifySucceeded ";
            msg += "falures = " + failureCount;
            logger.fine(msg);
        }

        if (!configChanged && !dirty && !switchFailover && failureCount == 0)
            return previousUpdateSucceeded = true;

        if (!dirty && failureCount > 0)
            logger.info("Previous update(s) failed, will retry");

        if (failureLimit > 0 && failureCount >= failureLimit) {
            // After that many errors we don't want to try to clean up
            // after whatever mess was left behind
            logger.info(">=" + failureLimit + " failures; clearing all rules");
            RuleSet.reset(getAddress());
            failureCount = 0;
        }

        previousUpdateSucceeded = true;
        if (programSwitch)
            programSwitch(assignments, load, mergeRules);

        return previousUpdateSucceeded;
    }

    /**
     * Examine all the nodes and figure out which ones are down; their
     * traffic should be redirected to the up nodes. It tries to spread
     * this redistribution evenly across the up nodes.
     *
     * @param assignments int array that will represent the assignments
     *                    of dead nodes to live ones (live nodes' will have
     *                    themselves as the assignee)
     * @param load the number of nodes' traffic handled by each node
     * @return whether any node status changed
     */
    private boolean getNodeStatus(int[] assignments, int[] load) {

        for (int i = 0; i <= maxHashVal; i++) {
            assignments[i] = -1;                       // implies no-op
            // Why 32? Why not! It just needs to be somewhat larger
            // than the number of TCP/UDP ports we consider.
            load[i] = Integer.MAX_VALUE/32;
        }

        // If a node is up, find the port it's connected to, then we
        // need to "reset" that port i.e. assignments[h] = h. If a
        // node is down, we need to send its traffic to a
        // replacement port i.e. assignments[h] = replacement.
        //
        // At the same time, we see whether or not any nodes changed
        // state; if no nodes changed state -- the usual case -- we
        // don't need to talk to the switch.

        // We use two arrays indexed 0 .. maxHashVal for what we're
        // doing per-port. "assignments" is the set of commands we're
        // going to send to the switch; "load" is the number of hashVals
        // mapped to this switch port.

        // When looking for a replacement port for p, we start at p and
        // count up, looking for the port that has the fewest hash values
        // assigned to it.

        // Caveat: if p and p+1 are down, then p -> p+2 and p+1 -> p+3.
        // Now if p+1 comes up, the p redirection becomes p -> p+1 --
        // which means connections going to p break even though p itself
        // didn't change state. (As dmr might say, you are not expected
        // to understand this.) The code gets really ugly though, and
        // there's no point in introducing new bugs to handle a very
        // infrequent situation.

        String l = "";          // For debugging
        HashSet nodesUpNow = new HashSet(MAX_NUM_NODES*13/10);
        boolean dirty = false;            // whether anything needs to be done

        int numNodes = config.getPropertyAsInt(ConfigPropertyNames.PROP_NUMNODES);
        for (int i = 0; i < numNodes; i++) {
            int nodeId = FIRST_NODE + i;
            boolean up = isNodeUp(nodeId);

            if (logger.isLoggable(Level.INFO))
                // Human-readable summary of up-ness
                l += (up? " ":" !") + nodeId;

            if (up && programSwitch) {
                // Get the port this node is on; ensure we're up to date
                int port = switchPorts.getPort(nodeId);
                if (port < 0) {
                    // Refresh the port map
                    try {
                        getInfoFromSwitch(config);
                        port = switchPorts.getPortFromSwitch(nodeId);
                    }
                    catch (MalformedOutputException e) {
                        logger.severe("Couldn't talk to the switch!");
                        previousUpdateSucceeded = false;
                        return previousUpdateSucceeded;
                    }
                }
                
                // If a node is up, but we cannot find its port number on the switch
                // using known MAC address, maybe the MAC has changed after switch failover.
                // Ask the node what its current MAC is, and use it on the next call of
                // getNodeStatus().

                if (port < 0) {
                    logger.warning("Node " + nodeId + 
                                   " is up, but the switch doesn't know? Will try to reinit MAC address");
                    reinitAddress(nodeId);
                    continue;
                }
                else {
                    try {
                        load[port - 1] = 1;
                        assignments[port - 1] = port;
                    }
                    catch (ArrayIndexOutOfBoundsException e) {
                        String msg = "Array bounds: length = " + load.length ;
                        msg += ", index " + port + " - 1";
                        logger.severe(msg);
                        //
                        // FIXME - internationalize
                        //
                        throw new RuntimeException(msg);
                    }
                }
            }
            
            if (up) {
                nodesUpNow.add(new Integer(nodeId));
            }
            
            if (!dirty) {
                dirty = (upNodes.contains(new Integer(nodeId)) != up);
            }

        } // for

        upNodes = nodesUpNow;

        if (dirty && logger.isLoggable(Level.INFO))
            logger.info("Nodes' status:" + l);

        return dirty;
    }

    private boolean isRebooting(){
        Object obj = ServiceManager.proxyFor (ServiceManager.LOCAL_NODE);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            logger.severe ("NodeMgrService.Proxy not available");
            return false;
        }

        return ((NodeMgrService.Proxy)obj).isRebooting();
    }

    /**
     * Reinitialize our view of the MAC address of given node.
     */
    public void reinitAddress(int nodeId) {
        
        try {
            Object o = ServiceManager.proxyFor(nodeId, "PlatformService");
            if (o == null ||
                !(o instanceof PlatformService.Proxy)) {
                // No such node
                logger.finer("Couldn't get platform proxy for " + nodeId);
                return;
            }
            
            PlatformService.Proxy platform = (PlatformService.Proxy) o;
            PlatformService api = (PlatformService) platform.getAPI();

            api.reInitAddresses();
        }
        catch (ManagedServiceException e) {
            logger.log(Level.WARNING, "RMI exception hcb" + nodeId, e);
        }
        catch (Exception e) {
            logger.warning("PlatformService exception");
        }
    }
    

    /** Program the switch to drop all traffic */
    boolean dropAll() {
        logger.info("Dropping all data traffic");
        try {
            droppingAll = true;
            RuleSet rs = new RuleSet(config);
            addDefaultRules(rs, switchPorts.getPort(myID));
            rs.add(new Rule(dataVIP, ZNetlink2Message.NULLPORT));
            RuleSet.reset(getAddress());
            return rs.send(getAddress());
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Error", e);
        }
        return false;
    }

    /**
     * Given a list of src addr -> switch port assignments, and the
     * load on each port, program the switch to reflect the desired
     * state. An assignment of -1 means the corresponding node is dead
     * and a substitute must be found.
     *
     * @param assignments int array that will represent the assignments
     *                    of dead nodes to live ones (live nodes' will have
     *                    themselves as the assignee)
     * @param load the number of nodes' traffic handled by each node
     * @param mergeRules  To merge rules or reset rules.
     * @return whether the switch was successfully programmed
     */
    private boolean programSwitch(int[] assignments, int[] load, boolean mergeRules) {

        rules = new RuleSet(config, maxHVal, maxPVal);
        addDefaultRules(rules, switchPorts.getPort(myID));


        int portMask = maxPVal;
        int hostMask = maxHVal << portMaskSize;

        String st = "live nodes:";         // Human-readable summary

        if (!areAllNodesDown(assignments)) {

            // Handle allowed clients, if set.
            if (allowedClients != null) {
                // Atleast one node should be up for addAllowedClients.
                // If not it could get stuck in a infinite loop.
                addAllowedClients(rules, assignments);
                return sendRules(rules, false, false);
            } 

            // First, take care of all the nodes that are up
            for (int i = 0; i <= maxHashVal; i++) {
                int port = i + 1;
                if (assignments[i] >= 0) {
                    int h = (i & hostMask) >> portMaskSize;
                    int p = i & portMask;
                    for (int j = 0; j < loadSpreadPorts.length; j++) {
                        int tcpPort = loadSpreadPorts[j];
                        if (tcpPort <= 0)
                            continue;
                        rules.add(new Rule(dataVIP, tcpPort, h, maxHVal, p, maxPVal,
                                                               assignments[i]));
                    }

                    if (logger.isLoggable(Level.INFO))
                        st += " h" + i + ",p" + port + "(hcb" + 
                            switchPorts.nodeId(port) + 
                            ")->p" + assignments[i] + 
                            "(" + switchPorts.nodeId(assignments[i]) + ")";
                }
            }
        } else {
            logger.warning("No nodes are ready yet.");
            failureCount++;
            return previousUpdateSucceeded = false;
        }

        if (logger.isLoggable(Level.INFO))
            logger.info(st);

        // Each of the above ports is getting ZNetlink2Message.NB_PORTS
        // rules; adjust assignments
        for (int i = 0; i <= maxHashVal; i++)
            load[i] *= loadSpreadPorts.length;

        st = "substitutions:";

        // Now for each unassigned hValue, for each IP port, send the
        // traffic to a healthy node
        for (int i = 0; i <= maxHashVal; i++) {
            int h = (i & hostMask) >> portMaskSize;
            int p = i & portMask;
            int port = i + 1;
            if (assignments[i] < 0)
                for (int j = 0; j < loadSpreadPorts.length; j++) {
                    int ipPort = loadSpreadPorts[j];
                    if (ipPort <= 0)
                        continue;
                    int replacement = getNewPort(load, i);
                    try {
                        load[replacement - 1]++;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // We have nothing to send any traffic to.
                        logger.info("Nothing to send traffic to; defer (" +
                                    replacement + "/" + load.length + ")");
                        failureCount++;
                        return previousUpdateSucceeded = false;
                    }

                    rules.add(new Rule(dataVIP, ipPort, h, maxHVal, p, maxPVal,
                                       replacement));

                    if (logger.isLoggable(Level.INFO)) {
                        String id = "?";
                        if (switchPorts.nodeId(port) >= 0)
                            id = "hcb" + switchPorts.nodeId(port);
                        st += " h" + i + ",p" + port + "(" + id + "):" +
                            ipPort + "->p" + replacement;
                    }
                }
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.info(st);
        }

        return sendRules(rules, verifyRules, mergeRules);
    }

    /**********************************************************************/
    private boolean sendRules(RuleSet rules, boolean verify, boolean merge) {
        // Sync; if any rule updates fail, we'll try again the next time
        previousUpdateSucceeded = previousVerifySucceeded = true;
        if (!rules.send(myAddress, merge)) {
            previousUpdateSucceeded = false;
            failureCount++;
        } else if (verify && !rules.verify(myAddress)) {
            previousVerifySucceeded = false;
            failureCount++;
        }
        else
            failureCount = 0;

        if (logger.isLoggable(Level.INFO)) {
            String msg = "Switch programming ";
            if (previousUpdateSucceeded)
                msg += "succeeded";
            else
                msg += "failed";
            if (verify) {
                msg += "; verification ";
                if (previousVerifySucceeded)
                    msg += "succeeded";
                else
                    msg += "failed";
            }
            if (failureCount > 0)
                msg += "; failure count now " + failureCount;
            logger.info(msg);
        }
        return previousUpdateSucceeded;
    }

    /**********************************************************************
     * Add rules to allow authorized clients. These rules cannot be
     * mixed with general rules based on wildcard, since the rule to
     * drop all traffic from a host always gets the lowest FSEL value
     * (through NetLink), and hence takes the least priority, which
     * results in the rule (to drop traffic) getting ignored if any
     * other rule also matches the mask. For each allowed client,
     * add a separate rule to allow access after picking a port
     * assigned to a live node.
     */
    private void addAllowedClients(RuleSet rules, int[] assignments) {
        // Drop all packets
        rules.add(new Rule(dataVIP, ZNetlink2Message.NULLPORT));
       
        // Accept from each host in allowedClients
        int nodeIdx = -1;
        for (int clientIdx = 0; clientIdx < allowedClients.length; clientIdx++) {

           logger.info("Creating rules for authorized client " + allowedClients[clientIdx]);
            // If a subnet is allowed access then 4 nodes are
            // allocated to it. If a node within this subnet is found
            // in allowedClients then no new rule needs to be
            // added. But, to just keep it simple (since finding bugs
            // in this codepath is hairy), add superfluous clients (if
            // any) in the same subnet anyway. This situation should
            // be rare.
            
            if (allowedClients[clientIdx] == null) {
                logger.severe("Client is null");
                return;
            }

            // Add rules per allowed client per load port (8080 and 8079).
            // The rule is uniformly spread on all nodes or 'numAuthClientRules' nodes if 
            // configured to do so.
            for (int portValue = 0; portValue <= (numAuthClientRules - 1); portValue++) {
                int nodePort = 0;
                // get a port associated with a live node
                try {
                    nodePort = assignments[++nodeIdx];
                } catch (ArrayIndexOutOfBoundsException e) {
                        String msg = "Array bounds in addAllowedClients: nodeIdx = " 
                                      + nodeIdx + " port = " + nodePort + ","  ;
                        msg += " Switch rules not programmed properly";
                        logger.severe(msg);
                        return;
                }
                while (nodePort < 0) {
                    if (nodeIdx < maxHashVal) {  // maxHashVal is 15 for 16 nodes
                        nodeIdx++;
                    } else {
                        nodeIdx = 0;
                    }
                    nodePort = assignments[nodeIdx];
                }

                for (int loadPort = 0; loadPort < loadSpreadPorts.length; loadPort++) {
                    int tcpPort = loadSpreadPorts[loadPort];
                    if (tcpPort <= 0) {
                        continue;
                    }
                    Rule rule = new Rule(dataVIP, tcpPort, allowedClients[clientIdx],
                                         portValue, maxPVal, nodePort);
                    rules.add(rule);
                }
                if (nodeIdx == maxHashVal) {
                    nodeIdx = -1; //Start from the beginning  
                }
            }
        }
    }

    /**
     * Sets up the initial switch rules: ICMP and ARP requests for the
     * data vip are sent to the port specified in the cluster config;
     * traffic from unauthorized hosts is silently dropped.
     *
     * @return success/failure
     */
    private RuleSet addDefaultRules(RuleSet rules, int masterPort) {

        int port = arpPort;
        if (port == 0)
            port = masterPort;
        rules.add(new Rule(dataVIP, "arp_request", port));

        port = icmpPort;
        if (port == 0)
            port = masterPort;
        rules.add(new Rule(dataVIP, "icmp", port));
      
        port = ntpPort;
        if (port == 0)
            port = masterPort;
        rules.add(new Rule(dataVIP, Time.NTP_PORT, "udp", port));

        // NDMP Inbound and Outbound Traffic is redirected
        // at the master node.
        port = ndmpPort;
        if (port == 0)
            port = masterPort;

        // NDMP INBOUND COONECTION
        rules.add(new Rule(dataVIP, ndmpInboundDataPort, port));

        // NDMP OUTBOUND CONNECTION
        rules.add(new Rule(dataVIP, ndmpOutboundDataPort, "tcp", port)); 
 
        return rules;
    }

    /**
     * Finds the node with the lowest "load" where load is defined 
     * as the number of hash values mapped to the node; start counting
     * from n+1 so we're not biased towards the lower numbered nodes
     *
     * @param currentLoad array of load values
     * @param n the index a replacement is being found for
     */
    private int getNewPort(int[] currentLoad, int n) {
        assert(currentLoad.length >= 2 && n < currentLoad.length);

        int[] mins = new int[currentLoad.length];
        int nMins = 0;
        int min = currentLoad[0];
        mins[nMins++] = 0;
        for (int i = 1; i < currentLoad.length; i++) {
            if (currentLoad[i] == min)
                mins[nMins++] = i;
            else if (currentLoad[i] < min) {
                nMins = 0;      // reset
                min = currentLoad[i];
                mins[nMins++] = i;
            }
        }

        if (min > 100) {
            logger.info("All ports unsuitable -- still starting?");
            return -1;
        }

        // Now pick a random element from mins
        int choice = mins[(int)(random.nextFloat() * (nMins - 1) + 0.5)];

        if (logger.isLoggable(Level.INFO)) {
            String st = "{ ";
            for (int i = 0; i < currentLoad.length; i++) {
                if (currentLoad[i] > 100)
                    st += "* ";
                else
                    st += currentLoad[i] + " ";
            }
            logger.info(st + "}  " + n + " -> " + choice);
        }

        return choice + 1;      // Ports on the switch are 1-based
    }

    private void getInfoFromSwitch(ClusterProperties config) {
        try {
            switchPorts = new SwitchPorts(config);
        }
        catch (MalformedOutputException e) {
            logger.severe("couldn't talk to switch: " + e);
            return;
        }

        myAddress = switchPorts.getMyAddress();

        if (logger.isLoggable(Level.INFO)) {
            String pm = "Switch ports";
            for (Iterator i = switchPorts.nodeIdIterator(); i.hasNext(); ) {
                Integer nodeId = (Integer) i.next();
                int port = switchPorts.getPort(nodeId.intValue());
                pm += " " + nodeId;
                pm += "," + switchPorts.getMacFromNodeId(nodeId.intValue());
                pm += ",p" + port;
            }
            logger.info(pm);
        }
    }

    private void computeHashes(int numNodes) {
        int masksize = 1;
        if (numNodes > 2) masksize = 2;
        if (numNodes > 4) masksize = 3;
        if (numNodes > 8) masksize = 4;
        if (numNodes > 16) masksize = 5;

        // If addrBits has been specified, use it; otherwise stay with
        // the old behaviour of half the total bits.
        if (addrBits >= 0 && addrBits <= masksize) {
            srcMaskSize = addrBits;
            portMaskSize =  masksize - srcMaskSize;
        }
        else {
            portMaskSize = masksize/2;
            srcMaskSize = masksize - portMaskSize;
        }

        maxPVal = (1 << portMaskSize) - 1;
        maxHVal = (1 << srcMaskSize) - 1;
        maxHashVal = (1 << masksize) - 1;
    }

    private String[] parseClients(String s) {
        // Split on whitespace into an array, check each element is well-formed
        try {
            if (s == null || s.equals(""))
                return null;

            String[] hosts = s.split("[ \t,]+");

            for (int i = 0; i < hosts.length; i++) {
                String[] components = hosts[i].split("/");
                if (components.length > 2) {
                    logger.severe("Too many slashes in " + hosts[i]);
                    return null;
                }
                if (components.length == 2) {
                    int nBits = Integer.parseInt(components[1]);
                    if (nBits < 0 || nBits > 32) {
                        logger.severe("Mask in " + hosts[i] + " out of range");
                        return null;
                    }
                }
            }
            return hosts;
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't parse " + s, e);
            return null;
        }
    }

    /** Talk to CM and find out if there's an FS service */
    private boolean fsSvcActive() {
        // WebDAV now listens on the API port
        return false;           /* return null != getProxy(SVC_FS); */
    }

    ////////////////////////////////////////////////////////////////////////
    // CM interfaces

    /** Get node proxy for local node */
    private NodeMgrService.Proxy getProxy() {
        return getProxy(ServiceManager.LOCAL_NODE);
    }

    private NodeMgrService.Proxy getProxy(int nodeId) {
        Object o = ServiceManager.proxyFor(nodeId);
        if (o == null || !(o instanceof NodeMgrService.Proxy)) {
            logger.finest("Couldn't get node proxy for " + nodeId);
            return null;
        }
        return (NodeMgrService.Proxy) o;
    }

    /** Get service proxy for local node  */
    private ManagedService.ProxyObject getProxy(String tag) {
        return getProxy(ServiceManager.LOCAL_NODE, tag);
    }

    private ManagedService.ProxyObject getProxy(int nodeId, String tag) {
        Object o = ServiceManager.proxyFor(nodeId, tag);
        if (o == null || !(o instanceof ManagedService.ProxyObject)) {
            logger.finest("Couldn't get service proxy for " + tag +
                          " on node " + nodeId);
            return null;
        }
        return (ManagedService.ProxyObject) o;
    }

}
