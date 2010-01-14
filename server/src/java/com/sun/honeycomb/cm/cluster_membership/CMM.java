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



package com.sun.honeycomb.cm.cluster_membership;

import java.util.Hashtable;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.InetAddress;
import java.util.Enumeration;
import java.net.Inet4Address;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.lang.reflect.Constructor;

import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.SoftwareVersion;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.common.InternalException;

/**
 * The CMM is the cluster membership monitor.
 * It provides the list of nodes currently active in the 
 * cell and elects a master and vicemaster nodes based on
 * eligibility status. The CMM is a building block of the
 * cluster management. If it fails, the node reboots.
 */
public final class CMM implements CMMConstants {

    private static final Logger logger = Logger.getLogger(CMM.class.getName());
    
    private static Hashtable apis;
    private static CMMEngine engine;
    private static String swVersion;
    private static boolean singleMode;
    private static boolean doQuorum;
    private static int nodeId;
    private static int disksPerNode;
    private static volatile int quorumThreshold;
    private static volatile int numNodes;

    static {
        apis = new Hashtable();
        engine = null;
        swVersion = null;
        nodeId = -1;
        disksPerNode = 0;
        numNodes = 0;
    }
    
    /**
     * Connect to the CMM on the given hostname and port.
     * If hostname is null, localhost is used.
     * @return the CMM api.
     * @throw an CMMError if the connection cannot be made.
     */
    public static CMMApi getAPI(String hostname, int port) 
    {
        ApiImpl api;
        synchronized (CMM.class) {
            String tag = hostname + ":" + port;
            api = (ApiImpl) apis.get(tag);
            if (api != null && api.isClosed()) {
                apis.remove(tag);
                api = null;
            }
            if (api == null) {
                try {
                    api = getApiImpl(hostname, new Integer(port));
                    apis.put(tag, api);
                } catch (CMMException e) {
                    throw new CMMError("CMM API failure " + e);
                }
            }
        }
        return api;
    }
    
    /**
     * Connect to CMM on the local node using the default port.
     */
    public static CMMApi getAPI() {
        return(getAPI(null, CMM.API_PORT));
    }

    /**
     * Connect to CMM on the given hostname using the default port
     */
    public static CMMApi getAPI(String hostname) {
        return(getAPI(hostname, CMM.API_PORT));
    }
    
    /**
     * Initialize and start CMM.
     * This method must be called once per node.
     * @return the hostname for this CMM
     */
    public static String start() {
        int nodeid;
        int numDisks;
        boolean single;
        boolean quorum;
        String version;
        String nodesConfig;

        /*
         * Check and recover the configurations
         */
        for (int i = 0; i < CMMApi.CFGFILES.size(); i++) {
            CMMApi.ConfigFile cfg = (CMMApi.ConfigFile) CMMApi.CFGFILES.get(i);
            CfgUpdUtil.getInstance().recoverConfig(cfg);
        }        
        ClusterProperties pr = ClusterProperties.getInstance();
        
        /*
         * Read the CMM tunables from the config
         */
        version = SoftwareVersion.getRunningVersion();        
        single = pr.getPropertyAsBoolean(ConfigPropertyNames.PROP_CM_SINGLEMODE);
        quorum = pr.getPropertyAsBoolean(ConfigPropertyNames.PROP_CM_DOQUORUM);
        nodesConfig = pr.getProperty(ConfigPropertyNames.PROP_CM_CFGNODES);
        if (nodesConfig == null) {
            throw new CMMError("cannot find nodes configuration");
        }

        /*
         * build a temporary table of CMM nodes IP addresses
         */
        String snodes[] = nodesConfig.split(",", CMM.MAX_NODES);
        String nodes[] = new String[snodes.length];
        int idx = 0;
        for (int i = 0; i < snodes.length; i++) {
            String cnode[] = snodes[i].trim().split("\\s++");
            nodes[idx++] = cnode[1];
        }
        
        /*
         * figure out the local node id based on the network interface
         * and the CMM nodes table.
         */
        HardwareProfile hwProfile = HardwareProfile.getProfile(pr);
        numDisks = hwProfile.getNumDisks();
        String[] interfaces = hwProfile.getNetworkInterfaces();
        nodeid = getIdFromInterface(interfaces, nodes);        
        
        /*
         * Start CMM
         */
        start(nodeid, nodesConfig, single, quorum, numDisks, version);
        
        /*
         * Return the hostname for this CMM
         */
        Node node = NodeTable.getNode(nodeid);
        if (node == null) {
            throw new CMMError("failed to get Node info for nodeid " + nodeid);
        }
        return node.getHost();
    }
        
    /**
     * Start CMM with the given parameters.
     */
    public static void start(int nodeid, 
                             String nodesConfig, 
                             boolean single,
                             boolean quorum, 
                             int numDisks, 
                             String version) 
    {
        synchronized (CMM.class) {
            if (engine == null) {
                updateConfig();
                NodeTable.init(nodeid, nodesConfig);
                nodeId = nodeid;
                singleMode = single;
                doQuorum = quorum;
                disksPerNode = numDisks;
                swVersion = version;
                engine = new CMMEngine().start();
            }
        }
    }
    
    /**
     * Return true if CMM is running in a one node configuration.
     * This is used to allow or prevent a node to establish
     * a ring with only itself.
     * CMM must have been started prior to use this API
     */
    public static boolean isSingleMode() {
        checkApiAccess();
        return singleMode;
    }

    /**
     * Return the node id for this CMM
     * CMM must have been started prior to use this API
     */
    public static int nodeId() {
        checkApiAccess();
        return nodeId;
    }
    
    /** 
     * Return the software version.
     * This method can be called from both the client and server
     */
    public static synchronized String getSWVersion() {
        if (swVersion == null) {
            swVersion = SoftwareVersion.getRunningVersion();
        }
        return swVersion;
    }
    
    /**
     * Return max latency of a message in the ring.
     * used for message retransmission
     * This method can be called from both the client and server
     */
    public static int latencyTimeout() {
        return HEARTBEAT_INTERVAL * MAX_NODES;
    }

    /**
     * Return the number of disks that we need to have quorum
     * CMM must have been started prior to use this API
     */
    public static int getMinDiskNum() {
        checkApiAccess();
        return ((((numNodes * disksPerNode) * quorumThreshold) / 100) + 1);
    }
    
    /*****************
     * Package access
     *****************/
    
    
    /**
     * Return the number of nodes currently configured in the cell.
     */
    static int getNumNodes() {
        return numNodes;
    }
    
    /**
     * Called when the default config file is changed.
     * Update the current number of nodes and the quorum threshold.
     */
    static void updateConfig() {
        ClusterProperties pr = ClusterProperties.getInstance();
        numNodes = pr.getPropertyAsInt
            (ConfigPropertyNames.PROP_NUM_NODES, MAX_NODES);

        quorumThreshold = pr.getPropertyAsInt
            (ConfigPropertyNames.PROP_CM_QUORUM_THRESHOLD, DEFAULT_THRESHOLD);
    }
    
    /**
     * Returns true if we should check for quorum, false if we should just
     * keep on truckin' regardless of the # of disks
     */
    static boolean doQuorumCheck() {
        return doQuorum;
    }
    
    /**
     * update the value of the latency timeout
     * base the current ring configuration.
     * TBD - update with care.
     */
    static void updateLatencyTimeout(int timeout) {
    }
    
    /**
     * Return the current Lobby Task
     */
    static LobbyTask lobbyTask() {
        if (engine != null) {
            return engine.lobbyTask();
        }
        return null;
    }
    
    /**
     * Return the current Sender Task
     */
    static SenderTask senderTask() {
        if (engine != null) {
            return engine.senderTask();
        }
        return null;
    }

    /**
     * Return the current Receiver Task
     */
    static ReceiverTask receiverTask() {
        if (engine != null) {
            return engine.receiverTask();
        }
        return null;
    }
    
    /**
     * Local heartbeat
     */
    static void heartbeat() {
        if (engine != null) {
            engine.heartbeat();
        }
    }
    
    /***********************************************************
     * TEST environment 
     * Instanciate the appropriate debug/test classes of CMM
     * Those are used to instantiate the right set of class when 
     * running the config/update stress tests
     ************************************************************/
        
    private static final String PROP_CFGUPD_TEST = 
        "com.sun.honeycomb.cm.cluster_membership.CfgUpdUtilStressTest";
    
    private static final String PROP_LOBBY_TEST =
        "com.sun.honeycomb.cm.cluster_membership.LobbyTaskStressTest";
    
    private static final String PROP_API_IMPL_TEST =
        "com.sun.honeycomb.cm.cluster_membership.ApiImplStressTest";

    static ApiImpl getApiImpl(String hostname, Integer port) 
        throws CMMException {
            
            ApiImpl api = null;
            Class [] signatures = { String.class, Integer.class };
            Object [] params = { hostname, port };            
            Constructor ctor = getCTOR(PROP_API_IMPL_TEST, signatures);
            if (ctor != null) {
                try {
                    api = (ApiImpl) ctor.newInstance(params);
                    logger.info("Instanciated stress test of ApiImpl ");
                }  catch (Exception e) {
                    logger.log(Level.SEVERE,
                               "can't instantiate object ApiImpl", e
                               );
                }
            }
            if (api == null) {
                api = new ApiImpl(hostname, port);
            }
            return api;
        }
    
    static LobbyTask getLobbyImpl(SenderTask sender) {
        
        LobbyTask lobby = null;
        Class [] signatures = { SenderTask.class};
        Object [] params = { sender };
        Constructor ctor = getCTOR(PROP_LOBBY_TEST, signatures);
        if (ctor != null) {
            try {
                lobby = (LobbyTask) ctor.newInstance(params);
                logger.info("Instanciated stress test of LobbyTask ");
            }  catch (Exception e) {
                logger.log(Level.SEVERE,
                           "can't instantiate object LobbyTask", e
                           );
            }
        }
        if (lobby == null) {
            lobby = new LobbyTask(sender);
        } 
        return lobby;
    }
    
    static CfgUpdUtil getCfgUpdUtilImpl() {
        
        CfgUpdUtil cfgUtil = null;
        Constructor ctor = getCTOR(PROP_CFGUPD_TEST, null);
        if (ctor != null) {
            try {
                cfgUtil = (CfgUpdUtil) ctor.newInstance();
                logger.info("Instanciated stress test of CfgUpdUtil ");
            } catch (Exception e) {
                logger.log(Level.SEVERE,
                           "can't instantiate object CfgUpdUtil", e
                           );
            }
        }
        if (cfgUtil == null) {
            cfgUtil = new CfgUpdUtil();
        }
        return cfgUtil;
    }        
    
    /*****************
     * Private
     *****************/
    
    /**
     * Look at the IPv4 addresses of all the interfaces. If any of them
     * match one of the addresses in "nodes", return the last octet of
     * the address -- that's our node ID
     */
    private static int getIdFromInterface(String[] interfaces, String nodes[])
    {
        for (int i = 0; i < interfaces.length; i++) {
            String ifName = interfaces[i];
            
            NetworkInterface nic = null;
            try {
                nic = NetworkInterface.getByName(ifName);
            }
            catch (SocketException e) {
                throw new CMMError(e);
            }
            
            if (nic == null) {
                throw new CMMError("illegal network interface " + ifName);
            }
            
            Enumeration addrs = nic.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress inetAddr = (InetAddress) addrs.nextElement();
                
                if (!(inetAddr instanceof Inet4Address)) {
                    logger.warning("ClusterMgmt - Not an IPV4 addr, skipping "
                                   + inetAddr);
                    continue;
                }
                String addr = inetAddr.getHostAddress();
                
                String myAddr = null;
                for (int j = 0; j < nodes.length; j++) {
                    if (addr.equals(nodes[j])) {
                        myAddr = addr;
                        break;
                    }
                }
                if (myAddr == null) {
                    logger.warning("ClusterMgmt - Not a CMM node, skipping "
                                   + addr);
                    continue;
                }
                
                String octets[] = myAddr.split("\\.");
                if (octets.length > 0) {
                    int id = Integer.parseInt(octets[octets.length - 1]);
                    logger.info("ClusterMgmt - node " + id + " using " +
                                ifName + ":" + myAddr + " as internal network");
                    return id;
                }
                
            }
        }        
        // Couldn't figure it out
        throw new CMMError("cannot find local node id");
    }
    
    private static void checkApiAccess() {
        if (engine == null) {
            throw new InternalException("CMM is not started in this JVM");
        }
    } 

    private static Constructor getCTOR(String name, Class [] signatures) 
    {        
        Constructor ctor = null;
        if (name != null) {
            try {
                Class cls = Class.forName(name);
                ctor = cls.getConstructor(signatures);
            } catch (Exception e) {
                logger.log(Level.FINE, "test class "+name
                           +" not found - will use default CMM class ",e
                           );
                return null;
            }
        }
        return ctor;
    }
}
