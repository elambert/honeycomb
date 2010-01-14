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



package com.sun.honeycomb.admin.mgmt;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.MissingResourceException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import com.sun.honeycomb.alert.cli.AlertDefaultClient;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.diskmonitor.DiskControl;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.emd.server.SysCache;
import com.sun.honeycomb.oa.bulk.RestoreSession;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.AuditLevel;
import com.sun.honeycomb.util.Ipmi;
import com.sun.honeycomb.util.ServiceProcessor;
import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.cm.node_mgr.MgrException;

//
// The use of MgmtException is to describe at a higher level the cause
// of the error; the message from this exception is directly printed to
// the CLI. So, don't write error details but only pertinent information
// that can be used by the administrator
//

public class ClusterManagement {
    static public final int ACT_ENTER_MAINTENANCE = 1;
    static public final int ACT_EXIT_MAINTENANCE = 2;


    private static final int WAIT_QUORUM_TRANSITION = (5 * 1000); // 5 sec

    private final static String LOG_INFO_START_MAINTENANCE = 
	"info.admin.adminApi.startMaintenance";
    private final static String LOG_INFO_STOP_MAINTENANCE = 
	"info.admin.adminApi.stopMaintenance";

    private static final Logger logger =
        Logger.getLogger(ClusterManagement.class.getName());

    private static ClusterManagement instance = null;

    private  ClusterManagement() {
    }

    static public synchronized ClusterManagement getInstance() {
        if (instance == null) {
            instance = new ClusterManagement();
        }
        return instance;
    }

    public void powerNodeOff (int nodeid, boolean ipmiPower)
        throws MgmtException {

        logger.info ("power off requested for node " + nodeid);
        NodeMgrService.Proxy pxy =null;
        try {
            pxy = Utils.getNodeMgrProxy(nodeid);
        } catch (AdminException ae) {
            logger.severe("Can't retrieve NodeMgr proxy on NODE-" + nodeid);
            throw new MgmtException("Failed to power off NODE-" 
                                    + nodeid + ".");
        }
        Object o = pxy.getAPI();
        if (! (o instanceof NodeMgrService)) {
            logger.severe("Can't retrieve NodeMgr API on NODE-" + nodeid);
            throw new MgmtException("Failed to power off NODE-" 
                                    + nodeid + ".");
        }
        NodeMgrService api = (NodeMgrService) o;
        try {
            api.powerOff(ipmiPower);
        } catch (MgrException e) {
            logger.log(Level.SEVERE,
                       "Powering of NODE-" + nodeid + " down failed: ", e);
            throw new MgmtException("Failed to power off NODE-" 
                                    + nodeid + ".");
        } catch (ManagedServiceException e) {
            logger.log(Level.SEVERE,
                       "Powering of NODE-" + nodeid + " down  - RMI call failed: ", e);
            throw new MgmtException("Failed to power off NODE-" 
                                    + nodeid + ".");
        }

        String str = Utils.getLocalString("cli.alert.hwconfig");
        String op = Utils.getLocalString("common.disabled");
        String item="NODE-"+nodeid;
        String fruType=Utils.getLocalString("common.node");        
        Object [] args= {op,fruType,item};
        String msg = MessageFormat.format(str, args);
        logger.log (AuditLevel.AUDIT_INFO, msg);
        Utils.notifyChangeCli(msg);
    }

    public void powerNodeOn(int nodeid) throws MgmtException {
        try {
            Ipmi.powerOn(nodeid);
        } catch (IOException e) {

            logger.log(Level.SEVERE,
              "Ipmi call to power down NODE-" + nodeid + " failed", e);
            throw new MgmtException("Failed to power on NODE-" + nodeid + ".");
        }
    }
    public boolean getNodePowerStatus(int nodeid) throws MgmtException {
        boolean status=false;
        try {
            status=Ipmi.powerStatus(nodeid);
        } catch (IOException e) {
            logger.severe("ipmi call to check power status of NODE-" + nodeid + 
                          " failed");
            throw new MgmtException("Impi failed to check power status of NODE-" 
		+ nodeid + ".");
        }
        return status;
    }

    public void powerOff(EventSender evt,
                         boolean useIpmi, 
                         boolean sp) throws MgmtException {                    
        String str = Utils.getLocalString("cli.alert.shutdown");
        logger.log(AuditLevel.AUDIT_INFO, str);       
        Utils.notifyChangeCli(str);

        sendAsynchronousEvent(evt, "Powering down cluster nodes now...");
        try {
            startMaintenance(evt);
        } catch (MgmtException e)  {
            sendAsynchronousEvent(evt, "Can't enter "+
                                     "maintenance mode, resuming regular operation");
            stopMaintenance();
            throw e;
        }

        if(sp){
            shutdownSp(evt,true);
        }

        NodeMgrService.Proxy pxy =null;
        try {
            pxy = Utils.getNodeMgrProxy();
        } catch (AdminException ae) {
            logger.severe("Can't retrieve NodeMgr proxy for master node.");
            throw new MgmtException("Failed to power off cluster");
        }
        Object o = pxy.getAPI();
        if (! (o instanceof NodeMgrService)) {
            logger.severe("Can't retrieve NodeMgr API on master node.");
            throw new MgmtException("Failed to power off cluster");
        }
        NodeMgrService api = (NodeMgrService) o;
        try {
            //
            // Switches don't have a notion of powering off,
            // let's at least reboot them.
            //
            api.shutdownCell(useIpmi,true);
        } catch (MgrException e) {
            logger.log(Level.SEVERE,"Failed to power off cluster", e);
            throw new MgmtException ("Failed to power off cluster. Please retry.");
        } catch (ManagedServiceException e) {
            logger.log(Level.SEVERE,"RMI failure - Failed to power off cluster", e);
            throw new MgmtException ("Failed to power off cluster. Please retry.");
        } 

    }

    public void rebootNode(int nodeid) throws MgmtException {
        logger.info ("reboot requested for NODE-" + nodeid);
        NodeMgrService.Proxy pxy = null;
        try {
            pxy = Utils.getNodeMgrProxy(nodeid);
        } catch (AdminException ae) {
            logger.severe("Failed to retrieve proxy for NodeMgr on NODE-" +
                nodeid);
            throw new MgmtException("Failed to reboot NODE-" + nodeid);
        }
        Object o = pxy.getAPI();
        if (! (o instanceof NodeMgrService)) {
            logger.severe("Failed to retrieve API for NodeMgr on NODE-" +
                nodeid);
            throw new MgmtException("Failed to reboot NODE-" + nodeid + ".");
        }
        NodeMgrService api = (NodeMgrService) o;        
        try {
            api.reboot();
        } catch (ManagedServiceException e) {
            logger.log(Level.SEVERE,"RMI failure - Failed to reboot cluster", e);
            throw new MgmtException ("Failed to reboot cluster. Please retry.");
        } 

    }


    public void rebootCell(EventSender evt,
                           boolean switches, 
                           boolean sp) throws MgmtException {

        String str = Utils.getLocalString("cli.alert.reboot");

        Utils.notifyChangeCli(str);
        logger.log (AuditLevel.AUDIT_INFO, str);
        //
        // Make sure the alert gets processed by the Alert clients
        // before we reboot the cluster. This period is currently
        // set to 1 second so this is acceptable.
        //
        int sleep = AlertDefaultClient.getAlertNotificationPeriod();
        try {
            Thread.currentThread().sleep(sleep);
        } catch (InterruptedException ignored) {
        }

        try {
            startMaintenance(evt);
        } catch (MgmtException e)  {
            sendAsynchronousEvent(evt, "Can't enter "+
                                     "maintenance mode, resuming regular operation");
            stopMaintenance();
            throw e;
        }

        if(sp) {
            shutdownSp(evt,false);
        }

        NodeMgrService.Proxy pxy =null;
        try {
            pxy = Utils.getNodeMgrProxy();
        } catch (AdminException ae) {
            logger.severe("Can't retrieve NodeMgr proxy for master node.");
            throw new MgmtException("Failed to power off cluster");
        }
        Object o = pxy.getAPI();
        if (! (o instanceof NodeMgrService)) {
            logger.severe("Can't retrieve NodeMgr API on master node.");
            throw new MgmtException("Failed to power off cluster");
        }
        NodeMgrService api = (NodeMgrService) o;
        try {
            api.rebootCell(switches);
        } catch (MgrException e) {
            logger.log(Level.SEVERE,"Failed to power off cluster", e);
            throw new MgmtException ("Failed to power off cluster. Please retry.");
        } catch (ManagedServiceException e) {
            logger.log(Level.SEVERE,"Failed to power off cluster in RMI call ", e);
            throw new MgmtException ("Failed to power off cluster. Please retry.");
        }

    }

    public void wipeDisks(EventSender evt, Reassure reassureThread) 
        throws MgmtException {

        Node [] nodes = null;
        try {
            nodes = Utils.getNodes();
        } catch (AdminException ae) {
            logger.severe("Failed to retrieve the \"Nodes\"");
            throw new MgmtException("Failed to wipe the disks" +
              " [cell " + Utils.getCellid() + "].");
        }
        String str = Utils.getLocalString("cli.alert.aboutToWipe");
        Utils.notifyChangeCli(str);
        //
        //  Enter maintenance mode-- use Reasure thread to send 
        //  asynchronous events to the CLI-- so it does not timeout
        //  and also to make sure admin knows what is happening
        // 
        reassureThread.setMessage("Start maintenance mode..." +
          " [cell " + Utils.getCellid() + "]");
        reassureThread.start();
        startMaintenance(evt);

        String message="Maintenance mode reached: Wiping the disks...";
        logger.info(message);
        reassureThread.setMessage(message + 
          " [cell " + Utils.getCellid() + "]");
        reassureThread.pause();

        // This is now a minty-fresh cluster. Remove any properties which retain state 
        // (just restore/Syscache state for the moment) while we are still in maintenance 
        // mode, but leave the configuration properties.
        message = "Clearing state properties.";
        logger.info(message);
        clearStatefulProperties();

        //
        // Start the asynchronous wipe. The wipe on each node returns
        // right after the disks have been disabled, so we know
        // quorum is/will be lost (soon).
        //
        int numNodes = nodes.length;
        try {
	    // Read in the number of nodes since Utils.getNodes() will
	    // return 16 nodes on a 8 node cluster
            numNodes = ClusterProperties.getInstance()
		.getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
        } catch (Exception e) {
            logger.warning ("Config num nodes parse error: " 
		+ ConfigPropertyNames.PROP_NUM_NODES);
        }
       
        for (int i = 0; i < numNodes; i++) {
            if (nodes[i].isAlive()) { 
                DiskProxy obj = null;
                try {
                    obj = Utils.getDiskMonitorProxy(nodes[i].nodeId());
                } catch (AdminException ae) {
                    logger.severe("Failed to get disk proxy for NODE-" +
                      nodes[i].nodeId());
                    throw new MgmtException("Failed to wipe disks on NODE-"+
                      nodes[i].nodeId() + ", aborting" +
                        " [cell " + Utils.getCellid() + "].");
                }
                if (!(obj.getAPI() instanceof DiskControl)) {
                    logger.severe("Failed to get disk API for NODE-" +
                      nodes[i].nodeId());
                    throw new MgmtException("Failed to wipe disks on NODE-"+
                      nodes[i].nodeId() + ", aborting" +
                        " [cell " + Utils.getCellid() + "].");
                }
                try {
                    DiskControl api = (DiskControl) obj.getAPI();
                    api.wipeAll();
                    sendAsynchronousEvent(evt, "Started wiping on NODE-"+
                        nodes[i].nodeId() + " [cell " + Utils.getCellid() + "]");
                } catch (IOException ioe) {
                    logger.log(Level.SEVERE,
                      "received IOException when wiping node " +
                      nodes[i].nodeId() + " ", ioe);
                    throw new MgmtException("Failed to wipe disks on NODE-"+
                      nodes[i].nodeId() + ", aborting" +
                      " [cell " + Utils.getCellid() + "].");
                } catch (Throwable thw) {
                    logger.severe("Unexpected exception from WipeAll");
                    throw new MgmtException("Unexpected exception on NODE-" +
                      nodes[i].nodeId() + ", aborting" +
                      " [cell " + Utils.getCellid() + "].");
                }
            } else {
                sendAsynchronousEvent(evt, "Skipped dead node: NODE-"+
                    nodes[i].nodeId() + " [cell " + Utils.getCellid() + "]");
            }
        }

        NodeMgrService.Proxy proxy =
          ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        boolean hasQuorum = true;

        //
        // At that point we should have lost quorum. Wait for NodeMgr
        // to notice the transition quorum -> no quorum
        //
        do {
            try {
                Thread.sleep(WAIT_QUORUM_TRANSITION);
            } catch (InterruptedException ie) {
            }
            proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            hasQuorum = proxy.hasQuorum();

            logger.info("Waiting for NodeMgr to detect loss of quorum...");

        } while (hasQuorum);
        
        logger.info("NodeMgr detected loss of quorum...");
        ResourceBundle rs = BundleAccess.getInstance().getBundle();
        String string =  rs.getString("warn.admin.mgmt.ClusterManagement.lostquorum");
        logger.log(ExtLevel.EXT_INFO, string);

        //
        // Wait for the transtion no quorum -> quorum to happen
        // before we ask to leave maintenance mode.
        //
        message = "Waiting for quorum...";
        logger.info(message);
        reassureThread.setMessage(message + 
          " [cell " + Utils.getCellid() + "]");
        reassureThread.unpause();          
        do {
            try {
                Thread.sleep(WAIT_QUORUM_TRANSITION);
            } catch (InterruptedException ie) {
            }
            proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            hasQuorum = proxy.hasQuorum();
        } while (!hasQuorum);


        //
        // Exit maintenance mode
        //
        message = "Reached quorum, exit maintenance mode...";
        logger.info(message);
        reassureThread.setMessage(message + 
          " [cell " + Utils.getCellid() + "]");
        stopMaintenance();

        //
        // Stop Reassure thread.
        //
        reassureThread.safeStop();



        sendAsynchronousEvent(evt,"Maintenance mode exited.. [cell " + 
          Utils.getCellid() + "]");


        //
        // Clear schema and send alert
        //

        clearSchema(System.currentTimeMillis());
        sendAsynchronousEvent(evt,"Schema cleared..[cell " + 
          Utils.getCellid() + "]");

        str = Utils.getLocalString("cli.alert.wipe");
        Utils.notifyChangeCli(str);

        logger.log (AuditLevel.AUDIT_INFO, str);
    }

    private final void clearIfSet(ClusterProperties props, HashMap newProps, String name, String def){
        String val = props.getProperty(name);
        if (val != null && !val.equals(def))
            newProps.put(name, def);
    }

    public void clearStatefulProperties()
        throws MgmtException{
        HashMap map = new HashMap(4);
        ClusterProperties props = ClusterProperties.getInstance();
        clearIfSet(props, map, SysCache.SYSTEM_CACHE_STATE, SysCache.STOPPED);
        clearIfSet(props, map, RestoreSession.PROP_RESTORE_SESSION_IN_PROGRESS, Boolean.FALSE.toString());
        clearIfSet(props, map, SysCache.LAST_ERROR_TIME, Long.toString(Long.MIN_VALUE));
        clearIfSet(props, map, SysCache.FIRST_ERROR_TIME, Long.toString(Long.MIN_VALUE));
        //map.put(SysCache.RESTOR_FT_DATE);
        try{
            props.putAll(map);
        } catch (ServerConfigException sce) {
            logger.log(Level.SEVERE,
                       "Failed to reset state information properties ["+
                       sce.getMessage()+"]", sce);
            throw new MgmtException("Failed to reset state information properties.");
        }
    }

    public void clearSchema(long version)
        throws MgmtException {
        try {
            CMM.getAPI().wipeConfig(CMMApi.UPDATE_METADATA_FILE, version);
        } catch (Exception cmme /* CMMEXCeption or ServerConfigException */) {
            logger.log(Level.SEVERE,
                       "Failed to clear the schema ["+
                       cmme.getMessage()+"]", cmme);
            throw new MgmtException("Failed to clear the schema.");
        }
    }

    private void forceMaintenanceNode(int nodeid, boolean maintenance)
        throws MgmtException {

        String mgmtExceptionMsg = null;
        if (maintenance) {
            logger.info("Start maintenance mode for NODE-" + nodeid);
            mgmtExceptionMsg = "Failed to enter maintenance node on NODE-" + 
              nodeid + ".";
        } else {
            logger.info("Start exit of maintenance for NODE-" + nodeid);
            mgmtExceptionMsg = "Failed to exit maintenance node on NODE-" + 
              nodeid + ".";
        }

        NodeMgrService.Proxy proxy = null;
        try {
            proxy = Utils.getNodeMgrProxy(nodeid);
        } catch (AdminException ae) {
            logger.severe("Failed to get NodeMgrProxy for NODE-" + nodeid);
            throw new MgmtException(mgmtExceptionMsg);
        }
        Object obj = proxy.getAPI();
        if (!(obj instanceof NodeMgrService)) {
            logger.severe("Failed to get NodeMgrProxy API for NODE-" + nodeid);
            throw new MgmtException(mgmtExceptionMsg);            
        }
        try {
            boolean res = ((NodeMgrService)obj).forceMaintenanceMode(maintenance);
            if (!res) {
                logger.severe(mgmtExceptionMsg);
                throw new MgmtException(mgmtExceptionMsg);            
            }
        } catch (ManagedServiceException e) {
            logger.log(Level.SEVERE,"Failed to get node to enter maintenance mode at RMI level ", e);
            throw new MgmtException ("Failed to tell mode to enter mainenance mode. Please retry.");
        }

        
    }
    
    private void sendAsynchronousEvent(EventSender evt, String message) {
        logger.info(message);
        if (evt == null) {
            return;
        }
        try {
            evt.sendAsynchronousEvent(message);
        } catch (Exception e) {
            logger.log(Level.SEVERE,
              "failed to send Asynchronous event :", e);
        }
    }

    private void shutdownSp(EventSender evt,boolean powerOff) {
        
        if (powerOff) {
            ServiceProcessor.shutdownServiceProcessor();
            sendAsynchronousEvent(evt,
                                  "Powered off service processor [cell " + 
                                  Utils.getCellid() + "]");
        } else {
            ServiceProcessor.rebootServiceProcessor();
            sendAsynchronousEvent(evt,"Rebooted service processor [cell " + 
                                  Utils.getCellid() + "]");
        }

    }

    private void startMaintenance(EventSender evt)
        throws MgmtException {
        sendAsynchronousEvent(evt, "Starting maintenance mode now.....");
        logger.info("Start startMaintenance mode");
        Node [] nodes = null;
        try {
            nodes = Utils.getNodes();
        } catch (AdminException ae) {
            logger.log(Level.SEVERE, "Failed to retrieve the \"Nodes\"", ae);
            throw new MgmtException("Failed to enter maintenance mode.");
        }

        int master = -1;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isMaster()) {
                assert master == -1 : master;
                master = i;
            }
        }
        if (master < 0) {
            logger.severe("Can not find master node!");
            throw new MgmtException(
                "Can not find master node, failed to enter maintenance mode.");
        } 
        AdminThread masterThread = 
            new AdminThread (nodes[master],
                             ACT_ENTER_MAINTENANCE);

        masterThread.start();
        boolean stopped = false;
        while (!stopped) {
            try {
                masterThread.join();
                stopped = true;
            } catch (InterruptedException ie) {
                logger.severe("Main thread got interrupted, retrying...");
            }
        }
        if (masterThread.mgmtException != null) {
            logger.log(Level.WARNING,
                            "Failed to start maintence mode on Master NODE-" 
                            + nodes[master].nodeId(), masterThread.mgmtException);
            throw masterThread.mgmtException;
        }
        logger.info("Ended succesfully started maintenance mode on Master NODE-" +
                      nodes[master].nodeId());

        //
        // nodes.length will always be 16 in current configuration, even
        // for 8 node systems. Check that the node isn't alive, and figure 
        // out how many nodes we have.
        //
        AdminThread maintenanceThreads[] = new AdminThread [nodes.length];
        int nodeCount = 0;
        for (int i = 0; i < nodes.length; i++) {

            if (nodes[i].isAlive() && i != master) {
                maintenanceThreads[i] =
                    new AdminThread (nodes[i],
                                     ACT_ENTER_MAINTENANCE);
                maintenanceThreads[i].start();
                nodeCount++;
            } else {
                maintenanceThreads[i] = null;
            }
        }
        
        MgmtException mgmtException = null;
        for (int i = 0; i < maintenanceThreads.length; i++) {
            if (maintenanceThreads[i] != null) {
                stopped = false;
                while (!stopped) {
                    try {
                        maintenanceThreads[i].join();
                        stopped = true;
                    } catch (InterruptedException ie) {
                        logger.severe("Main thread got interrupted, retrying...");
                    }
                }
                if (maintenanceThreads[i].mgmtException != null) {
                    logger.log(Level.WARNING,
                            "Failed to start maintence mode on NODE-" 
                            + nodes[i].nodeId(), 
                            maintenanceThreads[i].mgmtException);
                    if (mgmtException == null) {
                        mgmtException = maintenanceThreads[i].mgmtException;
                    }
                    
                } else {
                    logger.info(
                      "Ended succesfully started maintenance mode on NODE-" +
                      nodes[i].nodeId());
                }
            } 
        }
        
        if (mgmtException != null) {
            // Try to revert operation since that failed on at least one node
            // not sure that really makes sense??
            logger.severe("Failed to start maintenance mode," +
                          " run stopMaintenance");
            sendAsynchronousEvent(evt,
                                  "Failed to start maintenance mode,"+
                                  " on [cell " + 
                                  Utils.getCellid() +
                                  "] stopping maintenance mode now.");
            int MAX_RETRY = 3;
            int curRetry = 0;
            boolean success = false;
            do {
                try {
                    stopMaintenance();
                    success = true;
                } catch (MgmtException ignore) {
                    logger.severe("stopMaintenanceMode failed, retry = " + 
                      curRetry);
                    success = false;
                }
            } while(++curRetry < MAX_RETRY && success == false);

            logger.info("Ended stopMaintenance attempt: success="+success);
            throw mgmtException;
        } 

        /* Maintenance mode entered successfully */
        String str = Utils.getLocalString(LOG_INFO_START_MAINTENANCE);        
        logger.log(ExtLevel.EXT_INFO,str);
        sendAsynchronousEvent(evt, str);
        logger.info("Ended succesfully started Maintenance mode");
    }

    public void stopMaintenance() throws MgmtException {

        logger.info("Start stopMaintenance mode... ");

        Node [] nodes = null;
        try {
            nodes = Utils.getNodes();
        } catch (AdminException ae) {
            logger.severe("failed to retrieve the \"Nodes\"");
            throw new MgmtException("Failed to exit maintenance mode.");
        }

        AdminThread maintenanceThreads[] = new AdminThread [nodes.length];
        // re-enter data mode
        maintenanceThreads = new AdminThread [nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isAlive()) {
                maintenanceThreads[i] =
                  new AdminThread(nodes[i], ACT_EXIT_MAINTENANCE);
                maintenanceThreads[i].start();
                logger.info("Ending maint mode on node: " + i);
            } else {
                maintenanceThreads[i] = null;
            }
        }
        for (int i = 0; i < maintenanceThreads.length; i++) {
            if (maintenanceThreads[i] != null) {
                boolean stopped = false;
                while (!stopped) {
                    try {
                        maintenanceThreads[i].join();
                        stopped = true;
                    } catch (InterruptedException ie) {
                        logger.info("exit maintenance already in progress" +
                          ", retrying...");
                    }
                }
                if (maintenanceThreads[i].mgmtException != null) {
                    throw maintenanceThreads[i].mgmtException;
                }
                logger.info("Ended succesfully stopMaintenance mode on NODE-" +
                  nodes[i].nodeId());
            }
        }
        logger.info("Ended succesfully stopMaintenance mode... ");

        String str = Utils.getLocalString(LOG_INFO_STOP_MAINTENANCE);
        logger.log(ExtLevel.EXT_INFO, str);
    }

    public class AdminThread extends Thread {

        Node n;
        int action;
        MgmtException mgmtException;

        AdminThread(Node n, int action) {
            this.n = n;
            this.action = action;
            this.mgmtException = null;
        }
        
        private String getActionString() {
            String res = null;
            switch (action) {
            case ACT_ENTER_MAINTENANCE:
                res = "enter_maintenance";
                break;
            case ACT_EXIT_MAINTENANCE:
                res = "exit_maintenance";
                break;
            default:
                res = "unknown";
                break;
            }
            return res;
        }

        public void run() {
            if (n != null) {
                try {
                    switch (action) {
                    case ACT_ENTER_MAINTENANCE:
                        forceMaintenanceNode(n.nodeId(), true);
                        break;
                    case ACT_EXIT_MAINTENANCE:
                        forceMaintenanceNode(n.nodeId(), false);
                        break;
                    default:
                        throw new RuntimeException("Invalid action " + action);
                    }
                } catch (MgmtException me) {
                    mgmtException = me;
                }
            } else {
                logger.severe("Invalid \"Node\" for operation " +
                  getActionString());
            }
        }
    }

}
