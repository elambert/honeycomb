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



package com.sun.honeycomb.cm.node_mgr;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.jvm_agent.CMAgent;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.cm.ipc.Mailbox;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.config.ClusterPropertiesInterpreter;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.SystemInfo;
import com.sun.honeycomb.util.sysdep.MemoryInfo;
import com.sun.honeycomb.util.sysdep.Commands;
import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.util.sysdep.MemoryInfo;
import com.sun.honeycomb.util.sysdep.SystemInfo;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.Ipmi;
import com.sun.honeycomb.util.Switch;


import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.text.DecimalFormat;
import java.util.ResourceBundle;
import java.text.MessageFormat;


public final class NodeMgr extends CMAgent
    implements NodeMgrService, Observer {

    private static final Logger logger = Logger.getLogger(NodeMgr.class.getName());

    /*
     * Node configuration tunables.
     * FIXME - should not be hardcoded.
     */
    static final int NODEMGR_STARTUP_TIMEOUT =  20000; // 20s
    static final int STARTUP_TIMEOUT  = (10 * 60000);  // 10 min
    static final int SHUTDOWN_TIMEOUT = (7  * 60000);  // 7 mn
    static final int RUNTIME_TIMEOUT  = (3  * 60000);  // 3 mn
    static final int MAINTENANCE_GRACE_PERIOD = (1 * 60000); // 1mn
    static final int MAX_ERRORS = 3; // 3 errors in a row before quitting
    static final int MIN_NODES  = 2; // a cluster is at least 2 nodes.
    static final int POLL_DELAY = 500;          // 500ms
    static final int MONITOR_DELAY = 1000;      // 1s
    static final int MONITOR_GRACE_PERIOD = 40; // 40*500ms || 40*1s

    /*
     * Node paths
     * FIXME - should be define in NodeConfig.java or ConfigPropertyNames.java
     */
    static final String HC_ROOT_DIR       = "/opt/honeycomb";
    static final public String HC_SHARE_DIR =  HC_ROOT_DIR + "/share/";
    static final public String HC_NODE_CONFIG =  HC_SHARE_DIR + "node_config.xml";

    /*
     * initialized at startup by main()
     */
    private static int localnode;
    private static boolean doFailureCheck;
    private static boolean failureTolerance;

    private NodeMgrService.Proxy mgrproxy;
    private NodeConfig config;
    private CMMApi.Node[] cmmNodes;
    private Shutdown shutdownThr;
    private Thread maintenanceThr;
    private boolean maintenanceNotified;
    private final Timeout maintenanceTimeout; 
    private boolean cleanShutdownCompleted;

    private volatile boolean isMaster           = false;
    private volatile boolean isViceMaster       = false;
    private volatile boolean hasQuorum          = false;
    private volatile boolean isRebooting        = false;
    private volatile boolean isInMaintenance    = true;
    private volatile Boolean forceMaintenance   = Boolean.FALSE;
    private volatile boolean stopRequested      = false;
    private volatile boolean isAcceptingRequests = true;


    public NodeMgr() throws MgrException {
        super();
        config = NodeConfig.getInstance();
        shutdownThr = new Shutdown();
        maintenanceThr = null;
        maintenanceNotified = false;
        maintenanceTimeout = new Timeout(MAINTENANCE_GRACE_PERIOD);
        maintenanceTimeout.disable();

        /*
         * add all the services running in this JVM
         * into the agent control.
         */
        ServiceMailbox nodemgr = config.getService(this.getClass());
        List lst = config.getJVM(nodemgr).getServices();
        for (int i = 0; i < lst.size(); i++) {
            ServiceMailbox svc = (ServiceMailbox) lst.get(i);
            if (svc != nodemgr) {
                try {
                    addService(svc.getServiceClass(), svc.getTag());
                } catch (Exception e) {
                    throw new MgrException(e);
                }
            }
        }

        /*
         * Initialize all services to their managed initial state
         */
        ServiceMailbox[] svc = config.getServices();
        for (int i = 0; i < svc.length; i++) {
            if (svc[i].isPartOf(IO_SERVICES) ||
                svc[i].isPartOf(ALL_MASTER_SERVICES))
            {
                svc[i].setManaged(false);
            } else {
                svc[i].setManaged(true);
            }
        }

        /*
         * build the node manager proxy
         */
        try {
            mgrproxy = new NodeMgrService.Proxy(localnode);
            mgrproxy.isMaint = forceMaintenance.booleanValue();
            mgrproxy.hasQuorum = hasQuorum;
            mgrproxy.isRebooting = isRebooting;
            cmmNodes = CMM.getAPI().getNodes();
            mgrproxy.nodes = new Node[cmmNodes.length];
            for (int i = 0; i < cmmNodes.length; i++) {
                mgrproxy.nodes[i] = new Node(nodemgr.getTag(), cmmNodes[i]);
            }
            mgrproxy.services = new Service[svc.length];
            for (int i = 0; i < svc.length; i++) {
                mgrproxy.services[i] = new Service(svc[i].getService());
            }
        } catch (Exception e) {
            throw new MgrException(e);
        }

        cleanShutdownCompleted = false;
        logger.info("ClusterMgmt - NodeMgr is READY");
    }


    /******************************************************************
     *
     *    ManagedService INTERFACE
     *
     *******************************************************************/

    public void run() {
        logger.info("ClusterMgmt - NodeMgr is RUNNING");
        /*
         * Register for CMM notifications.
         */
        try {
            ServiceManager.register(ServiceManager.CMM_EVENT, this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        /*
         * Verify that the distributed IPC is in place and working,
         * Start initial services, wait for the master and disk quorum
         */
        try {
            publish();
            NodeMgrService.Proxy myself;
            myself = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            if (myself == null) {
                throw new MgrException("ClusterMgmt - Distributed IPC error");
            }
            startServices(INIT_SERVICES);
            waitForMaster();
            startServices(PLATFORM_SERVICES);
            // We have to wait for the disk quorum notification before
            // we can start more services

        } catch (Exception e) {
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("err.cm.nodemgr.startup");
            Object [] args = {new Integer (localnode)};
            logger.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));
            logger.log(Level.SEVERE,
                       "ClusterMgmt - ERROR startup [" + e + "]", e
                       );
            shutdown_sequence();
        }

        /*
         * This is the main loop of the node manager.
         */
        try {
            idle();

        } catch (Exception e) {
            logger.severe("ClusterMgmt - EXITING exception " + e);
            shutdown_sequence();
        }
        assert (false);
    }

    public void shutdown() {
        logger.severe("ClusterMgmt - Service shutdown");
        System.exit(1);
    }

    public void syncRun() {
    }

    public ProxyObject getProxy() {
        updateProxy();
        return mgrproxy;
    }

    


    /******************************************************************
     *
     *   PUBLIC METHODS
     *
     ******************************************************************/

    /**
     * Process CMM notification.
     * NOTE: this callback function just logs state transition from CMM.
     * This is not a reliable way to detect state change in the cell.
     * The right way is to extend the node mgr monitor() method.
     */
    public void update(Observable obj, Object arg) {

        NodeChange nodechange = (NodeChange) arg;
        int cause = nodechange.getCause();
        int node = nodechange.nodeId();
        logger.info("ClusterMgmt - CMM callback triggered " + nodechange);

        switch (cause) {

        case NodeChange.MASTER_ELECTED:
            if (node == localnode) {
                logger.info("ClusterMgmt - NODE ELECTED MASTER ");
            }
            break;

        case NodeChange.VICEMASTER_ELECTED:
            if (node == localnode) {
                logger.info("ClusterMgmt - NODE ELECTED VICEMASTER ");
            }
            break;

        case NodeChange.MASTER_DEMOTED:
            if (node == localnode) {
                logger.warning("ClusterMgmt - NODE DEMOTED MASTER ");
            }
            break;
        case NodeChange.MEMBER_JOINED:
            logger.info("ClusterMgmt - NODE " + node + " JOINED CLUSTER");
            break;

        case NodeChange.MEMBER_LEFT:
            logger.warning("ClusterMgmt - NODE " + node + " LEFT CLUSTER");
            break;

        case NodeChange.GAINED_QUORUM:
            logger.info ("ClusterMgmt - DISK QUORUM GAINED");
            break;

        case NodeChange.LOST_QUORUM:
            logger.info ("ClusterMgmt - DISK QUORUM LOST");
            break;
        }
    }

    /**
     * Return the local node id.
     */
    public static int nodeId() {
        return localnode;
    }



    /******************************************************************
     *  REMOTE API exported by NodeMgr as defined by NodeMgrService.java:
     *  All these calls  are executed in the context of the CMAgent thread
     *
     *  - start();
     *  - stopAllServices();
     *  - forceMaintenanceMode();
     *  - reboot();
     *  - powerOff();
     *  - setLogLevel(String);
     ******************************************************************/


    /**
     * Start all the services on this node.
     * [Called by NodeMgrClient when enabling honeycomb-server]
     */
    public void start() {
        try {
            startServices(ALL_SERVICES);
            logger.info("ClusterMgmt - ************ NODE STARTED *******");
        } catch (ManagedServiceException e) {
            logger.log(Level.WARNING, "Couldn't start all services", e);
        }
    }

    /**
     * Force the main thread to exit the idle() loop and initiate
     * a shutdown_sequence()
     * [Called by NodeMgrClient when disabling honeycomb-server]
     */
    public boolean stopAllServices() {
        if (stopRequested) {
            logger.warning("Stop() called when shutdown already in progress");
        }
        stopRequested = true; // will make the idle loop exit

        // FIXME - don't sleep but get notified by mode mgr
        try {
            Thread.sleep(SHUTDOWN_TIMEOUT);
        } catch (InterruptedException e) {
            logger.info("Shutdown must be finishing - caller exits");
            return true;
        }
        logger.info("Shutdown sequence timed out - caller exits");
        return false;
    }

    /**
     * Reboot --  stopping services first.
     * [Called by AdminServer to gracefully reboot the node]
     */
    public void reboot() throws ManagedServiceException {
        shutdownThr.reboot();
    }

    /**
     * Hard power down --  stopping services first if not ipmi.
     * [Called by AdminServer to powerOff the node]
     */
    public void powerOff(boolean useIpmi) 
        throws ManagedServiceException, MgrException {
        if(useIpmi) {
            try {
                Ipmi.powerOff();
            } catch (IOException e) {
                logger.log(Level.SEVERE,"Failed to power off node." + e);
                throw new MgrException(e);
            }
        } else {
            shutdownThr.powerOff();
        }
    }


    /**
     * reboot the cluster - can be forced if the impi flag is high.
     * switches will be rebooted if the reboot switches flag is high.
     */
    public void rebootCell(boolean rebootSwitches) 
        throws MgrException,ManagedServiceException {
        die(true, false,rebootSwitches);
    }


    /**
     * shut down the cluster - can be forced if the impi flag is high.
     * switches will be rebooted if the reboot switches flag is high.
     */
    public void shutdownCell(boolean useIpmi, boolean rebootSwitches) 
        throws MgrException, ManagedServiceException {
        die(false, useIpmi,rebootSwitches);
    }



    /**
     * Adjust log level for node manager.
     */
    public void setLogLevel(String levelp) {
        super.setLogLevel(levelp, null);
    }

    /**
     * force the node into maintenance mode. Calling this function with a
     * true value will cause the node to enter maintenance mode and remain in
     * this state, regardless of quorum. The node will stay in this state
     * until either (1) it is removed from this mode by calling this function
     * again with false, or (2) a reboot.
     * Note that calling this function with false will not always trigger
     * restoration of data services if the node is not ready to start them.
     */
    public boolean forceMaintenanceMode (boolean ask_to_enter) 
        throws ManagedServiceException 
    {
        if (ask_to_enter == forceMaintenance.booleanValue()) {
            logger.warning("ClusterMgmt - Asked to  " +
              (ask_to_enter ? "enter " : "exit " ) +
              "maintenance mode but forceMaintenanceMode = " +
              forceMaintenance.booleanValue() + ", return");
            return true;
        }

        boolean success = true;
        int timeoutValue;
        if (ask_to_enter) {
            timeoutValue = SHUTDOWN_TIMEOUT;
        } else {
            timeoutValue = STARTUP_TIMEOUT;
        }
        // always account for the grace period
        timeoutValue += MAINTENANCE_GRACE_PERIOD;
        Timeout timeout = new Timeout(timeoutValue);
        timeout.arm();

        // FIXME - we should use a nodeMgrRequest object to post the
        // request and wait for its completion instead of dealing with
        // synchronization this way.
        forceMaintenance = (ask_to_enter) ? Boolean.TRUE : Boolean.FALSE;
        synchronized (forceMaintenance) {
	    if (isInMaintenance != forceMaintenance) { 
                maintenanceNotified = false;
                maintenanceThr = Thread.currentThread();
                // Guard against spurious interrupt-- see javadoc for wait().
                do {
                    try {
                        logger.info("CMAgent thread wait for NodeMgr...");
                        forceMaintenance.wait(timeoutValue);
                    } catch (InterruptedException ie) {
                        success = false;
                        logger.info("Maintenance thread has been interrupted");
                        break;
                    }
                } while (!maintenanceNotified);
                maintenanceThr = null;
	   }
        }

        if (timeout.hasExpired()) {
            success = false;
            forceMaintenance = (ask_to_enter) ? Boolean.FALSE : Boolean.TRUE;
        }
        return success;
     }
    


    /******************************************************************
     *
     * PRIVATE METHODS
     *
     ******************************************************************/
    /*
     * Shut down cell.
     * Prerequsite: Cell must be in maintenance mode before entering this
     * routine. There's no safe way to exit this process, and it must
     * be run in a JVM which is still preent even when we're in 
     * maintenance mode AND we've lost master status. This is because
     * solo nodes are demoted from master status.
     */
    private void die(boolean reboot,
                     boolean useIpmi,
                     boolean rebootSwitches)
        throws ManagedServiceException,MgrException {
        String operationName=null;
        if(reboot) {
            operationName="reboot";
        } else {
            operationName="shutdown";
        }

        
        //
        // Check the prereq; 
        //

        //
        // There's no such thing as rebooting using ipmi
        //
        if(reboot && useIpmi) {
            throw new MgrException
                ("No such thing as rebooting an entire cell using ipmi");
            
        }
        
        if(!isInMaintenance) {
            throw new MgrException
                ("Attempted to shutdown cluster without "+
                 " first placing cell in maintenance mode.");
        }
        
        //
        // Get the node manager proxies
        //
        CMMApi.Node[] rebootNodes = null;
        try {
            rebootNodes = CMM.getAPI().getNodes();
        } catch (CMMException e) {
            logger.severe("Die - cannot reach CMM: " + e);
            throw new MgrException
                ("Cannot reach CMM - die failed");           
        }

        //
        // Prerequsites met; start shutdown/reboot. 
        //
        logger.info ("Starting cluster "+operationName);
        try {
            isRebooting=true;
            for (int i = 0; i < rebootNodes.length; i++) {
                CMMApi.Node cur = rebootNodes[i];
                if (cur.nodeId() == localnode || !cur.isAlive()) {
                    // this is this node or the node is not alive - skip it
                    continue;
                }
                NodeMgrService api = NodeMgrService.Proxy.getAPI(cur.nodeId());
                if (api == null) {
                    throw new MgrException(
                        "Failed to get node API for NODE-" + cur.nodeId()
                        + " aborting " + operationName + " operation.");
            }  
            if(reboot)
                api.reboot();
            else 
                api.powerOff(useIpmi);
            }
            logger.info("Done issuing " + operationName + " on remote nodes");
            // FIXME: solaris bug - CR 6665082
            // Wait until the nodes have a chance to reboot fully and call
            // stop script. This prevents the race issues with switch reboot.
            // The master node is still vulnerable to the above bug.
            logger.info("Waiting a few secs for " + operationName);
	    try {
	        Thread.currentThread().sleep(20000);
             } catch (InterruptedException e) {
                logger.log(Level.WARNING, "ClusterMgmt - wait on reboot interrupted ");
             }

            //
            // reboot dem switches if we were asked to
            //
            if (rebootSwitches) {
                Switch.rebootSwitches();
                logger.info("Rebooted switches");
            }

            logger.info("Will now " + operationName + " on locale node");

            //
            // Finally, reboot/shutdown ourselves
            //
            if (reboot) {
                reboot();
            } else {
                powerOff(useIpmi);
            }    
        } finally {
            isRebooting=false;
        } 
    }
    
    /**
     * Shutdown all JVMs except for the one we're running in.
     */
    private void stopJVMs() {

        // Shutdown all JVMs except for ours
        List jvms = config.getJVMs();
        for (int i = 0; i < jvms.size(); i++) {
            JVMProcess jvm = (JVMProcess) jvms.get(i);
            try {
                jvm.shutdown();
            } catch (IOException ioe) {
                logger.warning("ClusterMgmt - IO error on JVM shutdown: " +ioe);
            }
        }
        logger.info("ClusterMgmt - service JVMs are stopped");
    }
    
    /**
     * Start services of given mask: ALL_SERVICES, IO_SERVICES etc.
     */
    synchronized private void startServices(int what)
        throws ManagedServiceException 
    {
        if (!isMaster &&
            (what == MASTER_SERVICES || what == ALL_MASTER_SERVICES))
        {
            logger.warning(
                           "ClusterMgmt - cannot start master services - not master");
            return;
        }
        
        logger.fine("ClusterMgmt - Starting level " + what + " services.");
        
        List jvms = config.getJVMs();
        for (int i = 0; i < jvms.size(); i++) {
            JVMProcess jvm = (JVMProcess) jvms.get(i);
            try {
                jvm.start();
            } catch (Exception e) {
                throw new ManagedServiceException(e);
            }
        }
        
        // This list is sorted by runlevel
        ServiceMailbox[] services = config.getServices();
        int nbErrors = 0;
        
        Timeout timeout = new Timeout(STARTUP_TIMEOUT);
        
        // Keep trying to start services until either
        // . Everything is up, yeahhh...
        // . Too many services fail to start and we shutdown
        // . We reach the STARTUP_TIMEOUT
        //
        do {
            
            // Try to start all services. If any service fails to
            // start, we still try to start everything else that has
            // the same runlevel. In other words, do not attempt to
            // start a service if a lower runlevel service has already
            // failed.
            int failLevel = Integer.MAX_VALUE;
            boolean allOK = true;
            for (int count = 0; count < services.length; count++) {
                ServiceMailbox svc = services[count];
                int level = svc.getLevel();
                
                if (level > failLevel)
                    // A lower-level service has already failed
                    break;
                
                if (!svc.isPartOf(what) || !svc.isManaged()) {
                    continue;
                }
                logger.fine("ClusterMgmt - STARTING " + svc);
                try {
                    
                    if (!svc.start()) {
                        failLevel = level;
                        allOK = false;
                    } else {
                        nbErrors = 0;
                    }
                }
                catch (MgrException e) {
                    logger.log (Level.SEVERE, "ClusterMgmt - "+e.getMessage());
                    if (++nbErrors >= MAX_ERRORS) {
                        String error = "*** TOO MANY ERRORS ***";
                        logger.log(Level.SEVERE, "ClusterMgmt - " + error, e);
                        shutdownThr.shutdown();
                        throw new ManagedServiceException(error, e);
                    }
                    JVMProcess jvm = config.getJVM(svc);
                    jvm.restart();
                    break;
                }
            } // service loop
            
            publish();
            
            if (allOK) {
                String str = BundleAccess.getInstance()
                    .getBundle().getString("info.cm.services.startup");
                
                Object [] args = {new Integer (localnode)};
                logger.log(ExtLevel.EXT_INFO, MessageFormat.format(str, args));
                return;
            }
            
            try {
                cmmNodes = CMM.getAPI().getNodes();
                Thread.currentThread().sleep(POLL_DELAY);
                
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
                
            } catch (CMMException e) {
                logger.severe("ClusterMgmt - ERROR lost connection with CMM" + e);
                if (++nbErrors >= MAX_ERRORS) {
                    String error = "ClusterMgmt - *** TOO MANY ERRORS *** ";
                    logger.log(Level.SEVERE, error, e);
                    shutdownThr.shutdown();
                    throw new ManagedServiceException(error, e);
                }
            }
        } while (!timeout.hasExpired());
        
        if (timeout.hasExpired()) {
            String error =  "ClusterMgmt - services could not start" +
            " after " + STARTUP_TIMEOUT + " mSeconds -- escalation- ";
            shutdownThr.shutdown();
            throw new ManagedServiceException(error);
        }
    }
    
    /**
     * Stop services of given mask: ALL_SERVICES, IO_SERVICES etc.
     */
    private void stopServices(int what) {
        
        switch (what) {
            case MASTER_SERVICES:
                break;
            default:
                logger.warning("stopService only supported for MASTER_SERVICES");
                return;
        }
        
        // FIXME - should be done asynchronously (node mgr does the job).
        try {
            stopServices(what, 1);
        } catch (ManagedServiceException e) {
            logger.warning("ClusterMgmt - Couldn't stop services of mask "
                           + what + " " + e
                           );
        }
    }
    
    /**
     * Stop services of "what" mask at runlevel "stopLevel" and higher.
     */
    private void stopServices(int what, int stopLevel)
        throws ManagedServiceException {
        stopServices( getStoppableServices( what, stopLevel ));
    }

    /**
     * Which services should be stopped.
     * Selects services that should be stopped by applying "what" mask
     * to services of runlevel "stopLevel" and higher.
     * Returns a list of services, sorted by runlevel in decreasing order,
     * so service[0] should be stopped first, and service[N] last.
     * FIXME - we mixed runlevel with service group.
     */
    private ServiceMailbox[] getStoppableServices(int what, int stopLevel) {
        ServiceMailbox[] services = config.getServices();
        ArrayList stopSet = new ArrayList();

        for (int i = services.length - 1; i >= 0; i--) {
                ServiceMailbox svc = services[i];
                int level = svc.getLevel();

                // this check will exclude services which are not managed
                if (!svc.isPartOf(what) || !svc.isManaged()) {
                    logger.fine("service is not part of requested stop set: "
                                + svc.getName());
                    continue;
                }

                // level-zero services are stopped separately in
                // NodeMgr.shutdown()
                if (level == 0 || level < stopLevel) {
                    logger.fine("not a stoppable runlevel: "
                                + level
                                + " service: " + svc.getName());
                    continue;
                }

                // FIXME - keepAlive is a hack - get rid of it.
                if (svc.keepAlive() && what != ALL_MASTER_SERVICES) {
                    logger.fine("not stopping service " + svc.getName() +
                                " (keepAlive)");
                    continue;
                }

                // JVM services are stopped separately in stopJVMs()
                if (svc.isJVM()) {
                    logger.fine("skipping JVM service: " + svc.getName());
                    continue;
                }

                logger.info("Service should be stopped: " + svc.getName());
                stopSet.add(svc);
            }
            return (ServiceMailbox[]) stopSet.toArray(new ServiceMailbox[0]);
    }

    /**
     * Stop services in given set, sorted by runlevel in decreasing order.
     * FIXME - this is basically hacked with many side effects.
     */
    private synchronized void stopServices(ServiceMailbox[] stopSet)
        throws ManagedServiceException {

        int failLevel = 0; // runlevel of still-running service
        boolean waitForService = false;
        int count = stopSet.length - 1;

        logger.info("ClusterMgmt - STOP SERVICES: count = " + count);

        Timeout timeout = new Timeout(SHUTDOWN_TIMEOUT);
        boolean[] stopAcknowledged = new boolean[stopSet.length];

        for (int i=0; i<stopAcknowledged.length; i++) {
            stopAcknowledged[i] = false;
        }

        do { // keep trying until all services have exited, or reached timeout

            waitForService = false; // starting over

            for (count = 0; count < stopSet.length; count++) {

                ServiceMailbox svc = stopSet[count];
                int level = svc.getLevel();

                // do not stop service at runlevel X if runlevel X+1 is still
                // running
                if (level < failLevel) {
                    waitForService = true;
                    logger.info("ClusterMgmt - Service still running at " +
                                "higher level " + failLevel +
                                " - skipping " + svc.getName());
                    break;
                }

                //
                // If a service fails to shutdown after
                // Service.ESCALATION_TIMEOUT (10s), the JVM is killed
                // Skip any services belonging to this JVM...
                JVMProcess curJvm = config.getJVM(svc);
                if (!curJvm.healthCheck()) {
                    logger.info("skip service " + svc.getName() +
                                ", because its JVM has been killed " +
                                "during escalation");
                    failLevel = 0;
                    continue;
                }

                try {
                    if (!svc.stop()) {
                        failLevel = level;
                        waitForService = true;
                    } else {
                        failLevel = 0;
                        if (!stopAcknowledged[count]) {
                            logger.info("ClusterMgmt - Service has stopped: "
                                        + svc.getName() + " level=" + level
                                        );
                            stopAcknowledged[count] = true;
                        }
                    }
                }
                catch (MgrException e) {
                    logger.log(Level.SEVERE,
                               "ClusterMgmt - " + e.getMessage(), e
                               );
                    JVMProcess jvm = config.getJVM(svc);
                    // FIXME: If we shutdown the master jvm during a cli reboot
                    // operation, the cli command will fail. See keepAlive hack
                    try {
                        // if service stop() fails badly, kill the JVM
                        jvm.shutdown();
                    } catch (IOException ioe) {
                        logger.severe("ClusterMgmt - IO error " + ioe);
                        throw new RuntimeException(ioe);
                    }
                    break;
                }
            }

            if (count < stopSet.length) {
                waitForService = true; // some services remain
            }

            publish();
            try {
                Thread.currentThread().sleep(POLL_DELAY);
            } catch (InterruptedException ie) {
                logger.fine("sleep interrupted");
                // do not rethrow, continue normal shutdown
            }

        } while (waitForService && !timeout.hasExpired());

        if (waitForService) {
            logger.severe("ClusterMgmt - STOP TIMEOUT - some services still running");
            for (int i=0; i < stopAcknowledged.length; i++) {
                if (stopAcknowledged[i] == false) {
                    JVMProcess jvm = config.getJVM(stopSet[i]);
                    // FIXME: If we shutdown the master jvm during a cli reboot
                    // operation, the cli command will fail. See keepAlive hack.
                    // on the other hand we *definitly* want to shutdown the
                    // jvm if we cannot gracefully shutdown the services.
                    try {
                        jvm.shutdown();
                    } catch (IOException ioe) {
                        logger.severe("ClusterMgmt - IO error " + ioe);
                        throw new RuntimeException(ioe);
                    }
                }
            }
        } else {
            logger.info("All requested services are stopped: count=" + count);
        }
    }

    /**
     * get a fresh copy of the node manager proxy.
     */
    private void updateProxy() {

        synchronized (mgrproxy) {
            mgrproxy.hasQuorum = hasQuorum;
            mgrproxy.isRebooting = isRebooting;

            mgrproxy.isMaint  = forceMaintenance.booleanValue();
            for (int i = 0; i < cmmNodes.length; i++) {
                mgrproxy.nodes[i].update(cmmNodes[i]);
            }
            ServiceMailbox[] svc = config.getServices();
            for (int i = 0; i < svc.length; i++) {
                mgrproxy.services[i].update(svc[i].getService());
            }
        }
    }

    /**
     * Publish the node manager proxy in the cell.
     */
    private void publish() {
        ServiceManager.publish(this);
    }


    /**
     * Idle (Main) loop of the node manager.
     * Monitor the node for fault detection and recovery and detect cluster
     * membership change or client request.
     * We give up if there is more than MAX_ERROR to recover a problem or
     * if the node cannot be running after a RUNTIME_TIMEOUT period of time.
     */
    private void idle() throws ManagedServiceException {

        Timeout timeout = new Timeout(RUNTIME_TIMEOUT);
        Timeout logTimeout = new Timeout(CMM.LOGGING_INTERVAL);
        int decay = 0;
        int nbErrors = 0;

        while (nbErrors < MAX_ERRORS && !stopRequested) {
            if (decay >= MONITOR_GRACE_PERIOD) {
                // we reach the end of the monitoring alert period.
                if (nbErrors != 0) {
                    logger.warning("ClusterMgmt - LEAVING ALERT PERIOD");
                }
                decay = 0;
                nbErrors = 0;
            }
            int delay = MONITOR_DELAY;
            try {
                if (monitor()) {
                    // the node needs attention -
                    // enter monitoring alert period.
                    delay = POLL_DELAY;
                } else {
                    decay++;
                    timeout.arm();
                }
            } catch (ManagedServiceException e) {
                if (nbErrors == 0) {
                    logger.warning("ClusterMgmt - ENTERING ALERT PERIOD");
                    timeout.arm();
                }
                delay = POLL_DELAY;
                nbErrors++;
            }

            /*
             * Publish the node mgr proxy and sleep for the requested delay.
             */
            try {
                publish();
                Thread.currentThread().sleep(delay);
            } catch (Exception e) {
                logger.severe("failed to publish proxy " + e.getMessage());
                nbErrors++;
            }

            /*
             * Detect failure (include our own latency thread issue) and
             * give up if the node has been unhealthy for too long.
             * FIXME - we should not care if we are in maintenance or not.
             * node restarting issue ?
             */
            if (!isInMaintenance && timeout.hasExpired()) {
                logger.warning("ClusterMgmt - FAILURE  DETECTED ??");
                try {
                    if (!monitor()) {
                        threadLatencyWarning();
                    } else {
                        break;
                    }
                } catch (ManagedServiceException e) {
                    break;
                }
                timeout.arm();
            }

            /*<
             * Flush the output of the processes -
             * if a process died, we will report the error
             * during the next round.
             */
            List jvms = config.getJVMs();
            for (int i = 0; i < jvms.size(); i++) {
                JVMProcess jvm = (JVMProcess) jvms.get(i);
                try {
                    jvm.flushOutput();
                } catch (Exception e) {
                    if (logger.isLoggable (Level.INFO)) {
                        logger.info("ClusterMgmt - process " + jvm + " died");
                        StackTraceElement[] lines = e.getStackTrace();
                        for (int j = 0; j < lines.length; j++) {
                            logger.info ("TRACE: " + lines[j]);
                        }
                    }
                }
            }

            /*
             * Log current state of the node.
             */
            if (logTimeout.hasExpired()) {
                StringBuffer msg = new StringBuffer ("ClusterMgmt - Quorum: [");
                msg.append (hasQuorum);
                msg.append ("] FailureTolerance: [");
                msg.append (failureTolerance);
                msg.append ("] ForceMaintenance: [");
                msg.append (forceMaintenance.booleanValue());
                msg.append ("] Mode: [");
                msg.append ((isInMaintenance ? "MAINTENANCE]" : "DATA]"));
                logger.info(msg.toString());
                logTimeout.arm();
            }
        }

        logger.info("ClusterMgmt - NodeMgr is exiting from idle loop");
        if (nbErrors >= MAX_ERRORS) {
            logger.severe("ClusterMgmt - **** NODE HAS TOO MANY FAILURES ****");
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("err.cm.node_mgr.NodeMgr.toomanyfailures");
            logger.log(ExtLevel.EXT_SEVERE, str);
        }

        shutdown_sequence();
    }


    /**
     * Monitor the node/cell and update the state of the node accordingly.
     * Returns true if the node needs attention due to recoverable
     * error conditions. Generates a ManagedServiceException in case of
     * unexpected error.
     */
    synchronized private boolean monitor() throws ManagedServiceException {

        /*
         * Remember the current state, it's about to get updated
         */
        boolean wasMaster = isMaster;
        boolean hadQuorum = hasQuorum;
        boolean wasInMaintenance = isInMaintenance;
        boolean wasAcceptingRequests = isAcceptingRequests;


        /*
         * Check and update CMM view and update maintenanceTimeout if necessary
         */
        int aliveNodes = updateCMM();
        if (hasQuorum) {
            maintenanceTimeout.disable();
        } else {
            if (maintenanceTimeout.isDisabled()) {
                maintenanceTimeout.arm();                        
            }
        }

        /*
         * Check number of unrecovered disk failures
         */
        checkUnhealedFailures();

        /*
         * Do we need to start/stop master services on this node ?
         */
        if (wasMaster != isMaster) {
            ServiceMailbox[] svc = config.getServices();
            if (isMaster) {
                for (int i = 0; i < svc.length; i++) {
                    if (svc[i].isPartOf(ALL_MASTER_SERVICES)) {
                        /*
                         * a service can be part of multiple services groups
                         * (master and i/o) - only start the right ones.
                         */
                        if (!isInMaintenance || !svc[i].isPartOf(IO_SERVICES)) {
                            svc[i].setManaged(true);
                        }
                    }
                }
                logger.info("ClusterMgmt - *** STARTING MASTER SERVICES ***");
                startServices(ALL_MASTER_SERVICES);
            } else {
                logger.info("ClusterMgmt - *** STOPPING MASTER SERVICES ***");
                stopServices(ALL_MASTER_SERVICES, 1);
                for (int i = 0; i < svc.length; i++) {
                    if (svc[i].isPartOf(ALL_MASTER_SERVICES)) {
                        svc[i].setManaged(false);
                    }
                }
            }
        }


        /*
         * Do we need to enter/leave maintenance mode (stop/start data-path)?
         */
        if (!failureTolerance || forceMaintenance.booleanValue()) {
            isInMaintenance = true;
        } else if (!hasQuorum) {
            if (!maintenanceTimeout.hasExpired()) {
                if (!isInMaintenance) {
                    logger.info("ClusterMgmt - Lost quorum, wait for grace " +
                      " period before entering maintenance mode");
                }
            } else {
                isInMaintenance = true;
            }
        } else {
            isInMaintenance = false;
        }

        if (wasInMaintenance != isInMaintenance) {
            ServiceMailbox[] svc = config.getServices();
            if (isInMaintenance) {
                logger.info("ClusterMgmt - *** STOPPING DATA SERVICES ***");
                stopServices(IO_SERVICES, 1);
                for (int i = 0; i < svc.length; i++) {
                    if (svc[i].isPartOf(IO_SERVICES)) {
                        svc[i].setManaged(false);
                    }
                }
            } else {
                logger.info("ClusterMgmt - *** STARTING DATA SERVICES ***");
                for (int i = 0; i < svc.length; i++) {
                    if (svc[i].isPartOf(IO_SERVICES)) {
                        if (isMaster || !svc[i].masterOnly()) {
                            svc[i].setManaged(true);
                        }
                    }
                }
                startServices(IO_SERVICES);

                // This will make sure that the following code will
                // detect that we are accepting requests and if so
                // do something about it...
                wasAcceptingRequests = true;
            }
        }

        /*
         * Do we need to stop/start API services
         */
        if (!isInMaintenance) {
            isAcceptingRequests = ClusterPropertiesInterpreter.acceptRequests();
            if (wasAcceptingRequests != isAcceptingRequests) {
                ServiceMailbox[] svc = config.getServices();
                if (!isAcceptingRequests) {
                    logger.info("ClusterMgmt - *** STOPPING API ACCESS SERVICES ***");
                    stopServices(API_SERVICES, 1);
                    for (int i = 0; i < svc.length; i++) {
                        if (svc[i].isPartOf(API_SERVICES)) {
                            svc[i].setManaged(false);
                        }
                    }
                } else {
                    logger.info("ClusterMgmt - *** STARTING API ACCESS SERVICES ***");
                    for (int i = 0; i < svc.length; i++) {
                        if (svc[i].isPartOf(API_SERVICES)) {
                            if (isMaster || !svc[i].masterOnly()) {
                                svc[i].setManaged(true);
                            }
                        }
                    }
                    startServices(API_SERVICES);
                }
            }
        }

        /*
         * FIXME - this is not the right way to do it.
         * Use an nodeMgrRequest object to synchronize client request
         * with the node manager. A client posts a request and the node
         * manager does the actual action and notify the client upon
         * completion.
         */
        if (wasInMaintenance != isInMaintenance) {
            synchronized (forceMaintenance) {
                if (maintenanceThr != null) {
                    logger.info("ClusterMgmt - notify maintenance thread");
                    maintenanceNotified = true;
                    forceMaintenance.notify();
                }
            }
        }

        /*
         * Monitor and recover HC services.
         */
        ServiceMailbox[] svc = config.getServices();
        boolean failed = false;
        for (int i = 0; i < svc.length; i++) {
            try {
                if (svc[i].isManaged()) {
                    // FIXME - we should not have to use a restart flag -
                    // This is useless as it tries to cover a corner case
                    // that can happen anyway.
                    boolean restart = !stopRequested;
                    if (!svc[i].monitor(restart)) {
                        failed = true;
                        break;
                    }
                } else if (svc[i].isRunning()) {
                    logger.warning("ClusterMgmt - found service " +
                                   svc[i].getName() +
                                   " running - should be disabled"
                                   );
                    if (svc[i].isJVM()) {
                        JVMProcess jvm = config.getJVM(svc[i]);
                        if (jvm.safeToShutdown()) {
                            jvm.shutdown();
                        }
                    } else {                    
                        svc[i].stop();
                    }
                }
            } catch (Exception e) {
                logger.severe("ClusterMgmt - " + e.getMessage());
                if (svc[i].getLevel() == 0) {
                    logger.severe("ClusterMgmt - ESCALATION - EXITING");
                    shutdown_sequence();
                }
                JVMProcess jvm = config.getJVM(svc[i]);
                // FIXME - shutdown services that are still running.
                jvm.restart();
                startServices(ALL_SERVICES);
                throw new ManagedServiceException(e);
            }
        }

        /*
         * It is an error to have less than the required number of nodes.
         * In this case we want to wait here for a master or timeout and
         * trigger an exception.
         */
        if (!CMM.isSingleMode() && aliveNodes < MIN_NODES) {
            logger.severe("*** CLUSTER CONFIG and NODE ALONE ***");
            waitForMaster();
        }

        return failed;
    }


    /**
     * Update the node manager view of CMM and trigger the appropriate
     * actions according to the new state.
     * We lost quorum after a grace period to account for transient software 
     * failures; Node can leave/join CMM ring due to missed heartbeat, 
     * PLATFORM jvm can get killed and restarted.     
     * @return the number of active nodes.
     */
    synchronized private int updateCMM() throws ManagedServiceException {
        try {
            cmmNodes = CMM.getAPI().getNodes();
            if (CMM.getAPI().hasQuorum()) {
                hasQuorum = true;
            } else {
                hasQuorum = false;
            }
        } catch (CMMException e) {
            logger.log(Level.SEVERE,
                       "ClusterMgmt - ERROR lost connection with CMM", e
                       );
            throw new ManagedServiceException(e);
        }

        /*
         * Check if this node is elected/demoted master and
         * count the number of nodes alive in the ring.
         */
        isMaster = false;
        int aliveCount = 0;
        for (int i = 0; i < cmmNodes.length; i++) {
            if (cmmNodes[i].isAlive()) {
                aliveCount++;
            }
            if (cmmNodes[i].isMaster() &&
                localnode == cmmNodes[i].nodeId()) {
                isMaster = true;
            }
        }
        return aliveCount;
    }


    /**
     * check if Unhealed Failure Count is within our tolerance
     */
    private void checkUnhealedFailures() {

        ClusterProperties config = ClusterProperties.getInstance();

        // do not change the current value if
        // - config unavailable
        // - we have configured this check off (dev only!)
        // - we don't have the quorum
        if (config == null || !doFailureCheck || !hasQuorum) {
            return;
        }

        boolean oldFailureTolerance = failureTolerance;
        failureTolerance = !config.getPropertyAsBoolean
            (ConfigPropertyNames.PROP_DATA_LOSS);

        if (oldFailureTolerance != failureTolerance) {
            if (!failureTolerance) {
                logger.severe("failureTolerance above limit, too many "+
                              "unhealed disk failures, possible data loss!"
                              );
            } else {
                logger.info("failureTolerance now within limit - " +
                "no possible lost data");
            }
        }
    }

    /**
     * Wait for a master to show up.
     * A master needs to be elected within a limited period of time
     * otherwise this method triggers an error (java exception)
     */
    private void waitForMaster() throws ManagedServiceException {
        Timeout timeout = new Timeout(RUNTIME_TIMEOUT);
        while (true) {
            try {
                cmmNodes = CMM.getAPI().getNodes();
            } catch (CMMException e) {
                logger.log(Level.SEVERE,
                           "ClusterMgmt - ERROR lost connection with CMM",
                           e);
                throw new RuntimeException (e);
            }
            
            int aliveCount = 0;
            boolean hasMaster = false;
            for (int i = 0; i < cmmNodes.length; i++) {
                if (cmmNodes[i].isAlive()) {
                    aliveCount++;
                }
                if (cmmNodes[i].isMaster()) {
                    hasMaster = true;
                }
            }
            if (hasMaster && (CMM.isSingleMode() || aliveCount >= MIN_NODES)) {
                return;
            }

            logger.warning("ClusterMgmt - *** NO MASTER FOUND ***");
            if (timeout.hasExpired()) {
                throw new ManagedServiceException("- timeout escalation -");
            }
            try {
                Thread.currentThread().sleep(CMM.HEARTBEAT_TIMEOUT);
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        }
    }


    private void threadLatencyWarning() {
        PlatformService.Proxy platform = PlatformService.Proxy.getProxy();
        if (platform == null) {
            logger.warning("ClusterMgmt - platform proxy not available");
            return;
        }
        SystemInfo sysinfo = platform.getSystemInfo();
        MemoryInfo meminfo = platform.getMemoryInfo();
        DecimalFormat formatter = new DecimalFormat("###.##");
        logger.warning("ClusterMgmt - High Thread Latency - Load average: " +
                       formatter.format(sysinfo.get1MinLoad()) + ", " +
                       formatter.format(sysinfo.get5MinLoad()) + ", " +
                       formatter.format(sysinfo.get15MinLoad()));
    }


    /**
     * Shutdown sequence: stop services, JVMs, services in this JVM
     */
    private void shutdown_sequence() {
        try {
            if (isMaster) {
                stopServices(MASTER_SERVICES, 1);
            }
            stopServices(ALL_SERVICES, 1);

            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("info.cm.services.shutdown");
            Object [] args = {new Integer (localnode)};
            logger.log(ExtLevel.EXT_INFO, MessageFormat.format(str, args));

        } catch (ManagedServiceException e) {
            logger.warning("ClusterMgmt - failed to stop services cleanly: " + e);
        }

        stopJVMs(); // other JVMs, not us

        cleanShutdownCompleted = true;
        synchronized(shutdownThr) {
            /*
             * A reboot / powerOff has been initiated, let the shutdown thread
             * powerOff/reboot the node, and simply return.
             */
            if (shutdownThr.isWaiting()) {
                shutdownThr.notify();
                logger.info("ClusterMgmt - ***** NODE SHUTDOWN COMPLETED " +
                            "SUCCESSFULLY ****");
                return;
            }
        }
        super.shutdown(); // stop level-zero services in our JVM
        logger.info("ClusterMgmt - ***** NODE SHUTDOWN COMPLETED SUCCESSFULLY ****");
        System.exit(1);
    }


    /**
     * Shutdown hook - reboot the system by default, or drop to the shell
     * if we are in debug mode or explicit stop was requested.
     *
     * The default behavior on NodeMgr's exit is to reboot the node to clear
     * potential bad state. It's not necessary to kill child JVMs in this case
     * because reboot will destroy them all.
     * If an external entity (like greenline) is programmed to restart NodeMgr,
     * it may try to do so in a race with reboot, but reboot will of course win.
     * Note: shutdown hooks are NOT guaranteed to execute if the JVM is
     * exiting on crash, so for full confidence we need an external reboot
     * mechanism to clear bad state.
     * In debug mode, NodeMgr will exit, the node will not reboot. This is
     * consistent with intentional shutdown of our process (greenline's
     * "svcadm disable" or user's "pkill java").
     * In case of crash, only NodeMgr may exit, but child JVMs will remain
     * running.
     * NodeMgr may be restarted by an external entity (greenline), and will
     * create new child JVMs.
     * For debug mode's sake, we may want to add code to kill child PIDs on
     * shutdown.
     *
     */
    private class Shutdown extends Thread {

        final int SHUTDOWN = 1;
        final int REBOOT   = 2;
        final int POWEROFF = 3;

        private int doAction;
        private boolean isWaiting;

        public Shutdown() {
            super();
            doAction = SHUTDOWN;
            isWaiting = false;
        }

        public void run() {
            switch (doAction) {

            case SHUTDOWN:
                if (stopRequested) {
                    logger.info(
                       "ClusterMgmt - EXPLICIT STOP REQUEST - WILL NOT REBOOT");
                    return;
                }
                System.exit(0);
                return;

            case REBOOT:
                logger.info("ClusterMgmt - **** NODE REBOOT REQUESTED ****");
                stopRequested = true;
                if (!waitForShutdownSequence()) {
                    logger.warning("ClusterMgmt - *** CLEAN SHUTDOWN TIMED OUT... ");
                }
                // The JVM exits at this point and let GL take any appropriate
                // action, such as rebooting the node.
                System.exit(0);
                return;

            case POWEROFF:
                logger.info("ClusterMgmt - **** NODE POWEROFF REQUESTED ****");
                stopRequested = true;
                if (!waitForShutdownSequence()) {
                    logger.warning("ClusterMgmt - *** CLEAN SHUTDOWN TIMED OUT... ");
                }
                _powerOff();
                return;

            default:
                logger.severe("ClusterMgmt - internal error");
                return;
            }
        }

        boolean isWaiting() {
            return isWaiting;
        }

        synchronized void shutdown() {
            doAction = SHUTDOWN;
            super.start();
        }

        synchronized void reboot() {
            doAction = REBOOT;
            super.start();
        }

        synchronized void powerOff() {
            doAction = POWEROFF;
            super.start();
        }

        private boolean waitForShutdownSequence() {
            synchronized(this) {
                try {
                    isWaiting = true;
                    wait(SHUTDOWN_TIMEOUT);
                } catch (InterruptedException ignore) {
                    logger.warning("ClusterMgmt - Shutdown thread has been " +
                                   "interrupted...");
                }
                isWaiting = false;
            }
            return cleanShutdownCompleted;
        }

        private void _reboot() {
            String str = BundleAccess.getInstance().getBundle().getString("info.cm.node.reboot");
            Object [] args = {new Integer (localnode)};
            logger.log(ExtLevel.EXT_INFO, MessageFormat.format(str, args));

            logger.info("\n\nClusterMgmt - *** NODE IS REBOOTING ***");

            try {
                Commands cmds = Commands.getCommands();
                Exec.exec(cmds.reboot() + "-n", logger);
            } catch (Exception e) {
                logger.severe("ClusterMgmt - ERROR failed to reboot node");
            }
        }

        private void _powerOff() {

            String str = BundleAccess.getInstance().getBundle().getString("info.cm.node.shutdown");
            Object [] args = {new Integer (localnode)};
            logger.log(ExtLevel.EXT_INFO, MessageFormat.format(str, args));

            logger.info ("\n\nClusterMgmt - *** NODE IS POWERING DOWN ***");
            //FIXME: Platform needs to be moved to runlevel 0 and
            //      this code changed to invoke Platform.powerOff()
            //      instead of relying on exec'ing shutdown
            try {
                Commands cmds = Commands.getCommands();
                Exec.exec(cmds.poweroff(), logger);
            } catch (Exception e) {
                logger.severe("ClusterMgmt - ERROR failed to reboot node");
            }
        }
    }


    /**
     * Main entry point of honeycomb
     */
    static public void main(String args[]) {

        if (ServiceManager.initNodeMgr() == false) {
            logger.severe("ClusterMgmt - ERROR failed to init contract template");
        }

        logger.info("***** HONEYCOMB IS STARTING UP *****");
        try {            
            /*
             * initialize and start CMM
             */
            String hostName = CMM.start();
            
            /*
             * setup node manager configuration tunables.
             */
            ClusterProperties properties = ClusterProperties.getInstance();
            
            doFailureCheck = properties.getPropertyAsBoolean
                (ConfigPropertyNames.PROP_CM_FAILURE_CHECK);
            
            if (!doFailureCheck) {
                failureTolerance = true;
                logger.info ("ClusterMgmt - unhealed disk failure "+
                             "checking disabled");
            } else {
                failureTolerance = !properties.getPropertyAsBoolean
                    (ConfigPropertyNames.PROP_DATA_LOSS);
                
                if (!failureTolerance) {
                    logger.warning ("ClusterMgmt - too many unhealed failures"+
                                    " starting checker + maintenance mode");
                }
            }
            
            /*
             * Iinitialize distributed IPC for this node.
             */
            localnode = CMM.nodeId();
            if (Mailbox.initIPC(localnode, 1) != 0) {
                throw new Error("Cannot initialize IPC for local node");
            }
            
            /*
             * parse the node configuration and build
             * the list of JVMs and services running on that node.
             */
            NodeConfig config = NodeConfig.getInstance();
            
            /*
             * fire up the node manager agent
             */
            ServiceMailbox nodemgr = config.getService(NodeMgr.class);
            if (nodemgr == null) {
                throw new Error("ClusterMgmt class is missing from config");
            }

            logger.info("ClusterMgmt - Starting NodeMgr Agent...");
            createAgent(hostName, NodeMgr.class, nodemgr.getTag());
            
            /*
             * be sure the node manager is running before
             * the main thread exits.
             */
            Timeout timeout = new Timeout(NODEMGR_STARTUP_TIMEOUT);
            nodemgr.setInitRqstReady();
            do {
                if (timeout.hasExpired()) {
                    logger.severe("ClusterMgmt - Cannot start node manager");
                    System.exit(1);
                }
                if (nodemgr.isReady()) {
                    timeout.arm();
                    nodemgr.rqstRunning();
                }
                try {
                    Thread.currentThread().sleep(POLL_DELAY);
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            } while (!nodemgr.isRunning());

        } catch (Exception e) {
            logger.log(Level.SEVERE,
                       "ClusterMgmt - ERROR failed to start node manager", e
                       );
            throw new Error(e);
        }
    }
}
