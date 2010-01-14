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

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.HttpClient;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.util.ServiceProcessor;
import com.sun.honeycomb.spreader.SpreaderManagedService.Proxy;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlerterServerIntf;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.admin.mgmt.Utils;

import java.net.UnknownHostException;

import java.util.Locale;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ResourceBundle;
import java.text.MessageFormat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Observer;
import java.util.Observable;
import java.util.StringTokenizer; 
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.sun.honeycomb.util.BundleAccess;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

/**
 * The SpreaderService is a {@link ManagedService} that takes care of
 * distributing incoming packets to the cluster nodes. It takes care
 * of switch failover. This class invokes {@link SwitchStatusManager}
 * to keep track of nodes entering or leaving the cluster.
 *
 * @author Shamim Mohamed
 * @version $Id: SpreaderService.java 11859 2008-02-28 17:28:51Z ktripp $
 */
public class SpreaderService implements SpreaderManagedService, Observer,
                                        PropertyChangeListener {

    private static final String PNAME_INSTALL_DIR =
        "honeycomb.prefixPath";
    private static final String PNAME_SWITCH_TYPE = 
        "honeycomb.cell.switch_type";
    private static final String PNAME_VIP_INTERNAL_ADMIN =
        "honeycomb.cell.vip_internal_admin";
    private final static String PNAME_NUM_NODES = 
        "honeycomb.cell.num_nodes";
    private final static String PNAME_MIND_QUORUM = 
        "honeycomb.switch_mgr.quorum.mind";

    private static final String PNAME_POLL_INTERVAL =
        "honeycomb.switch_mgr.poll_interval";
    private static final String PNAME_VERSION_CHECK =
        "honeycomb.switch_mgr.versions.check";
    private static final String PNAME_INITRD_VERSIONS =
       "honeycomb.switch_mgr.versions";
    private static final String PNAME_VERIFY_PERIOD =
        "honeycomb.switch_mgr.verification.period";

    private static final String SWITCH_VERSION_URL =
        "http://10.123.45.1/http/cgi-bin/version";

    private static int CONF_NUM_RETRIES = 5;
    private static int CONF_RETRY_DELAY = 1000;

    // Default delay between checking states of nodes
    private static final int DEFAULT_POLL_INTERVAL = 10000; // 10 s

    // Default delay between sending gratuitous data VIP arps
    private static final int DEFAULT_ARP_INTERVAL = 60000; // 60 s

    // Default poll period to check switch
    private static final int DEFAULT_VERIFY_PERIOD = 60; // 10 min

    // Default poll period to check if Service processor and 
    // backup switch are alive
    private static final int DEFAULT_ISALIVE_VERIFY_PERIOD = 30; // 5 min

    // If disabled, check whether to terminate this often
    private static final int LONG_DELAY = 30000; // 30 s

    // No. of poll ticks we expect services to start by. The time
    // taken for all services to start on a node after it joins the
    // ring should be less than DEFAULT_POLL_INTERVAL*NODE_CHECK_COUNT
    private static final int NODE_CHECK_COUNT = 12;

    private static final Logger logger =
        Logger.getLogger(SpreaderService.class.getName());

    private int switchType;
    private int numNodes;

    private ClusterProperties clusterConf = null;
    private HardwareProfile profile = null;

    volatile boolean terminate = false;

    private SwitchStatusManager statusManager = null;

    private long pollInterval = DEFAULT_POLL_INTERVAL;
    private int verifyPeriod = DEFAULT_VERIFY_PERIOD;
    private int verifyIsAlivePeriod = DEFAULT_ISALIVE_VERIFY_PERIOD;
    private boolean disabled = false;

    private static int activeSwitch = -1;
    private static boolean isBackupSwitchAlive = true;
    private static boolean isSPAlive = true;
    private static String[] interfaceNames = null;

    private int nodeCheckCount = NODE_CHECK_COUNT;

    private boolean mindQuorum;
    private volatile boolean haveQuorum = false;

    private String adminVIP = null;
    private String dataVIP = null;
    private Proxy proxy = null;


    public SpreaderService() {
        logger.info("SpreaderService creation...");

        clusterConf = ClusterProperties.getInstance();
        clusterConf.addPropertyListener(this);

        try {
            String switchName = clusterConf.getProperty(PNAME_SWITCH_TYPE);
            if (switchName != null && switchName.equalsIgnoreCase("znyx")) {
                switchType = SWITCH_ZNYX;

                String checkFirmware =
                    clusterConf.getProperty(PNAME_VERSION_CHECK);
                if (checkFirmware != null &&
                    (checkFirmware.equalsIgnoreCase("yes") ||
                     checkFirmware.equalsIgnoreCase("true")))
                    checkSwitchVersion();

                String mq =
                    clusterConf.getProperty(PNAME_MIND_QUORUM);
                mindQuorum = (mq != null &&
                              (mq.equalsIgnoreCase("yes") ||
                               mq.equalsIgnoreCase("true")));
                logger.info((mindQuorum?"Minding":"Ignoring") + " quorum.");
            }
            else {
                // We don't try to program the switch
                switchType = SWITCH_OTHER;
                if (switchName == null)
                    logger.info("Property " + PNAME_SWITCH_TYPE + " not set.");
                else if (!switchName.equalsIgnoreCase("other"))
                    logger.warning("What's a \"" + switchName + "\" switch?");
            }

            dataVIP = MultiCellLib.getInstance().getDataVIP();
            if (switchType == SWITCH_ZNYX && 
                (dataVIP == null || dataVIP.length() == 0)) {
                logger.severe("Property " + MultiCellLib.PROP_DATA_VIP + " not set!");
                disable();
                return;
            }

            /* We need the admin VIP for SWITCH_OTHER. For Znyx switches
             * the admin VIP is configured on the switch */
            adminVIP = MultiCellLib.getInstance().getAdminVIP();
            if (switchType == SWITCH_OTHER && 
                (adminVIP == null || adminVIP.length() == 0)) {
                logger.severe("Property " + MultiCellLib.PROP_ADMIN_VIP + " not set!");
                disable();
                return;
            }

            String pollInt = null;
            try {
                pollInt = clusterConf.getProperty(PNAME_POLL_INTERVAL);
                if (pollInt != null)
                    pollInterval = Long.parseLong(pollInt);
            }
            catch (NumberFormatException e) {
                logger.warning("Couldn't parse poll interval \"" +
                               pollInt + "\"; using " + pollInterval);
            }

            String period = null;
            try {
                period = clusterConf.getProperty(PNAME_VERIFY_PERIOD);
                if (period != null)
                    verifyPeriod = Integer.parseInt(period);
            }
            catch (NumberFormatException e) {
                logger.warning("Couldn't parse verification period \"" +
                               period + "\"; using " + verifyPeriod);
            }

            getNumNodes(clusterConf);

            // HardwareProfile
            profile = HardwareProfile.getProfile();

            interfaceNames = profile.getNetworkInterfaces();

            // Paranoia: is this the master node?
            NodeMgrService.Proxy nodeProxy =
                ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            Node master = nodeProxy.getMasterNode();
            if (master == null || master.nodeId() != nodeProxy.nodeId()) {
                logger.warning("I (hcb" + nodeProxy.nodeId() + ") am not " +
                               "the master; SpreaderService will be disabled");
                disable();
                return;
            }

            // Everything's OK

            statusManager =
                new SwitchStatusManager(dataVIP, adminVIP, numNodes, 
                                        clusterConf, switchType == SWITCH_ZNYX);
            disabled = false;
            logger.info("... Spreader init complete.");
        }
        catch(MalformedOutputException e) {
            logger.severe("Couldn't create a SwitchStatusManager: " + e);
        }
        catch (Exception e) {
            // Want the stack trace here
            logger.log(Level.SEVERE, "Unexpected error: ", e);
        }

        if (statusManager == null) {
            logger.severe("StatusManager is null!");
            disable();
        }
    }


    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {

        try {
            if (!isDisabled())
                statusManager.start();

            String intrface = interfaceNames[profile.getActiveInterface()];
            AdminVIP.configureAdminVIPs(intrface, switchType, true);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Failure", e);
        }
    }



    /**
     * At regular intervals and when notified by CMM this calls the
     * {@link SwitchStatusManager} (which does all the real work) to
     * check switch status. (When we're sure CMM callbacks work all
     * the time, we can turn the STATUS_ALL "regular interval" call to
     * statusManager into STATUS_SWITCH -- i.e. we check the switch at
     * regular intervals and the nodes on callback.)
     *
     * @see SwitchStatusManager
     */
    public void run() {

        /* Paranoia: is this the master node? */
        NodeMgrService.Proxy nodeProxy =
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        Node master = nodeProxy.getMasterNode();
        if (master == null || master.nodeId() != nodeProxy.nodeId()) {
            logger.warning("hcb" + nodeProxy.nodeId() + " isn't the master; " +
                           "SpreaderService disabled");
            disable();
        }
        else
            logger.info("SpreaderService running.");

        if (!isDisabled()) {
            try {
                ServiceManager.register(ServiceManager.CMM_EVENT, this);
                logger.info("CMM registration succesful.");
            }
            catch(CMAException e) {
                logger.log(Level.SEVERE, "Register for callbacks", e);
            }
            getQuorumState();
            if (!haveQuorum && !statusManager.dropAll())
                logger.warning("StatusManager.dropAll failed");
        }

        /**
         * Counter for the loop for sending out gratuitous arp
         * replies. We only want to run every 60 seconds, so we won't
         * be running every time the node status loop wakes up.
         */
        int loopCount = 0;

        int verifyCounter = 0;
        // counter for verifying the state of the backup switch
        int verifyIsAliveCounter = 0;
        while (!terminate) {

            /**
             * Send out the gratuitous arp every 60 seconds. We're
             * depending on the node poll interval. If that is greater
             * than 60 seconds we just send the arp everytime we get
             * woken up. Otherwise, count up the number of loop
             * iterations until we hit 60 seconds. Then reset the
             * counter.
             */
            if ((DEFAULT_POLL_INTERVAL >= DEFAULT_ARP_INTERVAL) ||
                (loopCount ==
                 ((DEFAULT_ARP_INTERVAL / DEFAULT_POLL_INTERVAL) - 1))) {
                sendarp();
                loopCount=0;
            } else {
                loopCount++;
            }

            if (statusManager != null) {
                int statusCheck = 0;

                int currentSwitch = profile.getActiveInterface();

                /**
                 * Check Backup Switch State i.e. is it pingable and 
                 * ssh'able?
                 */
                if (currentSwitch >= 0) {
                    if(verifyIsAliveCounter > verifyIsAlivePeriod) {
                        isSPAlive = ServiceProcessor.isSPAlive();
                        isBackupSwitchAlive = Switch.isBackupSwitchAlive();

                        // refresh proxy 
                        proxy = null;
                        ServiceManager.publish(this);
                        verifyIsAliveCounter = 0;
                    } else {
                        verifyIsAliveCounter++; 
                    }
                }

                if (currentSwitch >= 0 && currentSwitch != activeSwitch) {
                    String ifname = interfaceNames[currentSwitch];
                    String msg; 
                    // The switch ID's are one based so bump the count for display
                    if (activeSwitch >= 0)
                        logger.warning("Switch failover " + (activeSwitch + 1)  +
                                       " -> " + (currentSwitch + 1));
                    else
                        logger.info("Init: switch " + (currentSwitch + 1) +
                                    " active.");

                    activeSwitch = currentSwitch;

                    // The proxy needs to be refreshed
                    proxy = null;
                    ServiceManager.publish(this);

                    // Notify other services
                    notifyFailover();

                    AdminVIP.configureAdminVIPs(ifname, switchType, true);


                    statusCheck |= SwitchStatusManager.STATUS_SWITCH;
                }

                if (doNodeCheck())
                    statusCheck |= SwitchStatusManager.STATUS_NODES;

                if (currentSwitch >= 0 && verifyPeriod > 0) {
                    if (verifyCounter >= verifyPeriod) {
                        verifyCounter = 0;
                        statusCheck |= SwitchStatusManager.STATUS_SWITCH;
                    }
                    else
                        verifyCounter++;
                }

                try {
                    // We're not going to risk missing the quorum notification
                    if (!haveQuorum && getQuorumState())
                        logger.warning("Missed a quorum notification!");

                    if (haveQuorum &&
                            !statusManager.update(statusCheck, numNodes)) {
                        logger.warning("StatusManager.update failed");
                        ResourceBundle rs = BundleAccess.getInstance().getBundle();
                        String str = rs.getString("warn.StatusManager.update");
                        logger.log(ExtLevel.EXT_WARNING, str);


                    }
                        
                }
                catch (Exception e) {
                    logger.log(Level.SEVERE, "StatusManager.update", e);
                }
            }

            try {
                Thread.sleep(pollInterval);
            }
            catch (InterruptedException ie) {
                logger.info("Sleep interrupted");
            }

        }

        // Drop all traffic
        if (mindQuorum && !statusManager.dropAll())
            logger.warning("StatusManager.dropAll failed");

        logger.info("Exit.");
    }


    /**
     * Notify the PlatformService on all nodes that they need to re-init
     * any networking config they have
     */
    private void notifyFailover() {
        if (statusManager == null) {
            logger.warning("Can't send notifications without a manager!");
            return;
        }

        // The switch ID's are one based so bump the count for display
        if (activeSwitch < 0 || activeSwitch > 1) {
            logger.warning("Notify that switch = " + (activeSwitch + 1) + "?");
            return;
        }

        if (logger.isLoggable(Level.INFO))
            logger.info("Notifying all that active switch = " + (activeSwitch + 1));
            
        try {
            // If we can't get a proxy, we'll hit an exception and log it
            NodeMgrService.Proxy nodeMgr = (NodeMgrService.Proxy)
                ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            Node[] nodes = nodeMgr.getNodes();

            for (int i = 0; i < nodes.length; i++) {
                int nId = nodes[i].nodeId();
                statusManager.reinitAddress(nId);
            }
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Sending failover notifications", e);
        }
    }

    public void shutdown() {
        logger.info("Sending shutdown...");
        terminate = true;
    }

    public ManagedService.ProxyObject getProxy() {
        try {
            if (proxy == null) {
                proxy = new Proxy(getDataVIP(), getAdminVIP(), getMyAddress(),
                                  getActiveSwitch(), getBackupSwitchStatus(),
                                  getSPStatus()); 
                if (logger.isLoggable(Level.INFO))
                    logger.info(proxy.toString());
            }
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Exception:", e);
        }

        return proxy;
    }

    /** Called when node status changes */
    public void update(Observable obj, Object typ) {
        NodeChange nodechange = (NodeChange) typ;
        int nodeId = nodechange.nodeId();
        int cause = nodechange.getCause();

        if (logger.isLoggable(Level.INFO))
            logger.info("Handling node change: " + cause + " from " + nodeId);

        switch (cause) {

        case NodeChange.MEMBER_JOINED:
        case NodeChange.MEMBER_LEFT:
            // Node status changed: include nodes in our checks
            if (logger.isLoggable(Level.FINE)) {
                String msg = " joined the ring.";
                if (cause == NodeChange.MEMBER_LEFT) 
                    msg = " left the ring.";
                logger.fine("Node " + nodeId + msg);
            }
            try {
                if (haveQuorum)
                    startNodeChecks();
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, 
                           "StatusManager.update(Node) failed", e);
            }
            break;

        case NodeChange.MASTER_ELECTED:
        case NodeChange.MASTER_DEMOTED:
            // if we're the master, configure the vip. if we're not
            // the master, de-configure the vip. this could be
            // optimized
            NodeMgrService.Proxy proxy = 
                ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            if (proxy != null) {
                if (nodeId == proxy.nodeId()) {
                    String iface = interfaceNames[activeSwitch];
                    boolean master = (cause == NodeChange.MASTER_ELECTED);
                    AdminVIP.configureAdminVIPs(iface, switchType, master);
                }
            }
            break;

        case NodeChange.GAINED_QUORUM:
        case NodeChange.LOST_QUORUM:
            if (!mindQuorum)
                break;

            // Track quorum state. When we don't have quorum, the switch
            // should drop all traffic to the data VIP.
            haveQuorum = (cause == NodeChange.GAINED_QUORUM);

            try {
                if (haveQuorum) {
                    logger.info("Gained quorum.");
                    if (!statusManager.update(numNodes))
                        logger.warning("StatusManager.update failed");
                        ResourceBundle rs = BundleAccess.getInstance().getBundle();
                        String str = rs.getString("warn.StatusManager.update");
                        logger.log(ExtLevel.EXT_WARNING, str);
                }
                else {
                    logger.info("Lost quorum.");
                    if (!statusManager.dropAll())
                        logger.warning("StatusManager.dropAll failed.");
                }
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "Quorum " +
                           (haveQuorum? "gained" : "lost") +
                           " but statusManager choked", e);
            }
            break;
        }
    }

    /** Get quorum state from the node manager */
    private boolean getQuorumState() {
        if (!mindQuorum)
            haveQuorum = true;
        else {
            NodeMgrService.Proxy proxy;
            proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            haveQuorum = proxy.hasQuorum();
        }
        return haveQuorum;
    }

    /**
     * Set the status to DISABLED
     */
    private void disable() {
        logger.warning("disabling myself");
        disabled = true;
        statusManager = null;
        pollInterval = LONG_DELAY; // Check this often on whether to terminate
    }

    SwitchStatusManager getStatusManager() {
        return statusManager;
    }

    /*
     * Public API
     */


    /**
     * Whether the service is enabled or disabled.
     *
     * @return if the service managed to get running correctly
     */
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * The adminVIP for the cluster
     */
    public String getAdminVIP() {
        return adminVIP;
    }

    /**
     * The dataVIP for the cluster
     */
    public String getDataVIP() {
        return dataVIP;
    }

    /**
     * The type of switch in the cluster.
     *
     * @return SpreaderManagedService.SWITCH_ZNYX etc.
     */
    public int getSwitchType() {
        return switchType;
    }

    /**
     * Whether the node is "up" i.e. will have traffic directed to it
     * (this needs to have finer granularity: by service, not just
     * the whole node.)
     *
     * @param nodeId the ID of the node
     * @return whether the node is running
     */
    public boolean isNodeUp(int nodeId) {
        if (!isDisabled())
            return statusManager.isNodeUp(nodeId);
        return true;
    }

    /**
     * The number of bits (lsb) of the source IP address that are
     * used in the routing decision
     *
     * @return the mask size
     */
    public int getSrcAddrMaskSize() {
        if (!isDisabled())
            return statusManager.getSrcAddrMaskSize();
        return 0;
    }

    /**
     * The number of bits (lsb) of the source TCP/UDP port that are
     * used in the routing decision
     *
     * @return the mask size
     */
    public int getSrcPortMaskSize() {
        if (!isDisabled())
            return statusManager.getSrcPortMaskSize();
        return 0;
    }

    /**
     * Get the switch port that is currently receiving traffic
     * from a set of source hosts for a given TCP/UDP port
     *
     * @param srcHost the value of the {@link getSrcAddrMaskSize}
     *                rightmost bits of the source address
     * @param srcPort the value of the rightmost bits of the source port
     * @param destIpPort the TCP/UDP port number the packet is for
     * @return the switch port the packet will be sent to
     */
    public int getSwitchPort(int srcHost, int srcPort, int destIpPort) {
        if (!isDisabled())
            return statusManager.getPort(srcHost, srcPort, destIpPort);
        return -1;
    }

    /**
     * Gets the port on the switch that the admin (master node)
     * is connected to.
     *
     * @return switch port master node is connected to
     */
    public int getMasterPort() {
        if (!isDisabled())
            return statusManager.getMasterNodePort();
        return -1;
    }
        
    /**
     * Get the switch port a given node is connected to
     * Returns -1 if the information is not known.
     *
     * @param nodeId the ID of the node in question
     * @return the port the node is connected to
     */
    public int getPortFromId(int nodeId) {
        if (!isDisabled())
            return statusManager.getSwitchPorts().getPort(nodeId);
        return -1;
    }

    /**
     * Get the node ID of the node connected to a switch port
     * Returns -1 if the information is not known.
     *
     * @param port the port in question
     * @return the ID of the node connected to the port
     */
    public int nodeIdFromPort(int port) {
        if (!isDisabled())
            return statusManager.getSwitchPorts().nodeId(port);
        return -1;
    }

    /**
     * Get the active switch
     */
    public int getActiveSwitch() {
        return activeSwitch;
    }

    /**
     * Get Backup Switch Status 
     */
    public boolean getBackupSwitchStatus() {
        return isBackupSwitchAlive; 
    }
    
    /**
     * Get Service Processor Status 
     */
    public boolean getSPStatus() {
        return isSPAlive; 
    }

    /**
     * Get the port that a node identified by its MAC address
     * is connected to. Returns -1 if the MAC address is unknown.
     *
     * @param mac the MAC address in question
     * @return the port the node is connected to
     */
    public int getPortFromMAC(String mac) {
        if (!isDisabled())
            return statusManager.getSwitchPorts().getPort(mac);
        return -1;
    }

    /**
     * Get the MAC address of the node at a particular port.
     * Returns null if unknown.
     *
     * @param port the switch port to consider
     * @return the MAC address
     */
    public String getMacFromPort(int port) {
        if (!isDisabled())
            return statusManager.getSwitchPorts().getMacFromPort(port);
        return "";
    }

    /**
     * Get the node ID of the node with the given MAC address.
     * Returns -1 for unknown MAC addresses.
     *
     * @param mac the MAV address
     * @return  the node ID
     */
    public int nodeIdFromMAC(String mac) {
        if (!isDisabled())
            return statusManager.getSwitchPorts().nodeId(mac);
        return -1;
    }

    /**
     * Get the MAC address of a give node. Returns null if unknown.
     *
     * @param nodeId the node ID
     * @return the MAC address
     */
    public String getMacFromNodeId(int nodeId) {
        if (!isDisabled())
            return statusManager.getSwitchPorts().getMacFromNodeId(nodeId);
        return "";
    }

    /**
     * Get the IP address of the node the service is running on
     * This is the address used when talking to the switch.
     *
     * @return the IP address
     */
    public String getMyAddress() {
        if (!isDisabled() && statusManager != null)
            return statusManager.getSwitchPorts().getMyAddress();

        return adminVIP;
    }

    /**
     * Get the number of nodes in this cluster according to the
     * cluster config.
     *
     * @return the number of nodes
     */
    public int getNumNodes() {
        if (!isDisabled())
            return statusManager.getSwitchPorts().getNumNodes();
        else
            return 0;
    }

    // Private: managing the nodeCheckCount

    private synchronized boolean doNodeCheck() {
        if (true)
            return true;        // XXX TODO: HACK, workaround for 6273452

        if (nodeCheckCount <= 0)
            return false;
        nodeCheckCount--;
        return true;
    }

    private synchronized void startNodeChecks() {
        nodeCheckCount = NODE_CHECK_COUNT;
    }

    private void checkSwitchVersion() {
        BufferedReader reader = null;
        String firmWareVersions = null;
        int linesRead = 0;
        try {
            reader = HttpClient.getHttp(SWITCH_VERSION_URL, logger);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (reader == null)
            throw new RuntimeException("Couldn't GET " + SWITCH_VERSION_URL);

        boolean versionOK = false;

        try {
            String line;
            firmWareVersions =
                clusterConf.getProperty(PNAME_INITRD_VERSIONS);
           
            if (firmWareVersions != null && 
                !firmWareVersions.equals("")) {

                while ((line = reader.readLine()) != null) {
                    linesRead++;
                    String pattern = " *, *"; // match spaces around commas
                    logger.info("Switch version \"" + line + "\"");
                    String[] okVersions =
                        firmWareVersions.split(pattern);
                    for (int i=0; i < okVersions.length; i++) {
                        if (line.equals(okVersions[i])) {
                            versionOK = true;
                            break;
                        }
                    }
                    if (versionOK)
                        break;
                }
            }
            reader.close();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (linesRead == 0)
            throw new RuntimeException("Couldn't get switch version info: " +
                                       "check cgi-bin/version");

        if (!versionOK)
            throw new RuntimeException("Switch firmware mismatch: expected " +
                                        firmWareVersions);
    }

    public void propertyChange(PropertyChangeEvent event) {
        String propName = event.getPropertyName();
        if (propName.equals(PNAME_NUM_NODES))
            getNumNodes(clusterConf);
    }

    private void getNumNodes(ClusterProperties config) {
        synchronized (config) {
            String val = config.getProperty(PNAME_NUM_NODES);

            if (val == null)
                throw new Error("Property " + PNAME_NUM_NODES + 
                              " does not exist");

            try {
                numNodes = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                throw new Error("Property " + PNAME_NUM_NODES + "=" + val +
                                " is non-numeric.");
            }
        }
    }

    /**
     * Send a gratuitous arp for the data VIP
     */
    private void sendarp() {
        /**
         * We know that when either of the two switch boot, both switches
         * negotiate for mastership. During this period both switches are 
         * masters. do not send arp in this situation, as we dont know which 
         * interface to choose.
         */
        int currentSwitch = profile.getActiveInterface();
        if(currentSwitch < 0) {
            return; 
        }
        String intrface = interfaceNames[currentSwitch];

        int vlanId = profile.getDataVlanId();

        // Get the device
        Matcher m = Pattern.compile("[a-zA-Z]+").matcher(intrface);
        m.find();
        String device = intrface.substring(m.start(), m.end());
                
        // Get the PPA
        m = Pattern.compile("[0-9]+").matcher(intrface);
        m.find();
        String devPPA = intrface.substring(m.start(), m.end());

        /**
         * Create the proper adapter instance for the data vip
         * vlan. The VLAN's PPA is given by:
         *
         * VLAN logical PPA = 1000 * VID + Device_PPA 
         *
         * See http://docs.sun.com/app/docs/doc/816-1664-10/6m82lv9lt?a=view
         */
        String vlanPPA = Integer.toString(vlanId * 1000 +
                                          Integer.parseInt(devPPA));
        String vlanIntrface = device + vlanPPA;
        String arpcmd = "/opt/honeycomb/bin/sendarp " +
            vlanIntrface + " " + getDataVIP();

        try {
            int ret = Exec.exec(arpcmd, logger);
        } catch (IOException e) {
            logger.severe("Gratuitous sendarp failed: " + e);
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("err.spreader.sendarp");
            Object [] args = {new String (e.getMessage())};
            logger.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));
        }
    }
}
