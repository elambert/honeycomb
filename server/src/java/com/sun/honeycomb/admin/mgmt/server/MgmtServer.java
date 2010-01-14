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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

import com.sun.honeycomb.admin.DataDocTaskTracker;
import com.sun.honeycomb.admin.mgmt.AdminException;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlerterServerIntf;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.datadoctor.DataDocProxy;
import com.sun.honeycomb.datadoctor.TaskList;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.server.SysCache;
import com.sun.honeycomb.multicell.MultiCellConfig;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.ExtLevel;

public class MgmtServer extends MgmtServerBase implements MgmtServerIntf {

    static private MgmtServer   mgmtServer;

    private Map                 _ddTasks; 
    private long _sessionId=-1;
    private Date _sessionTime;
    private String cuser;
    private String cpasswd;
    private String cpubkey;
    
    // flag to stop spurious logging while waiting for quorum
    private boolean _monitorDDfirstRun = true;
                       
    
    /** The default location of honeycomb */
    private final static String DEFAULT_PREFIX = "/opt/honeycomb";

    /** The default and base uid for honeycomb admin */
    private final static int DEFAULT_ADMIN_UID = 1000;

    /** The default gid of group honeycomb */
    private final static int DEFAULT_ADMIN_GID = 1000;

    /** The default admin user */
    private final static String DEFAULT_ADMIN_USER = "admin";

    /** The default admin password */
    private final static String DEFAULT_ADMIN_PASSWD = "admin";


    /** The default admin public key */
    private final static String DEFAULT_ADMIN_PUBKEY = null;

    private static final long LOGIN_TIMEOUT=1000*60*5; // five minute timeout

    //Query Integrity parameters indicate if the 
    // PopulateExtCache task is up to date

    private MetadataClient      mdClient;
    private long                queryIntegrityTime;
    private long                superRunStart;
    private long                superCycleStart;
    private long                hadbCreateTime;
    private long                savedQueryIntegrityTime;

    //
    // Accessed by Adaptors
    //
    private AlertApi.AlertViewProperty alertView = null;
    private long alertViewTimestamp = -1;

    AlertApi.AlertViewProperty getAlertView(){
        if (alertView == null || System.currentTimeMillis() - alertViewTimestamp > 5000){
            try {
                AlerterServerIntf alertApi = AlerterServerIntf.Proxy.getServiceAPI();
                if (alertApi != null) {
                    alertView = alertApi.getViewProperty();
                    alertViewTimestamp = System.currentTimeMillis();
                } else {
                    logger.warning("cannot get alert server API");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE,
                           "Can't get the current view of the alert tree", e);
            }
        }
        return alertView;
    }

    public MgmtServer() {
        super();

        alertView = null;
        try {
            mgmtPort = 
                Integer.parseInt(config.getProperty(MultiCellConfig.PROP_MGMT_PORT));
        } catch (Exception exc) {
            throw new RuntimeException("MgmtServer cannot start because " +
                                       "the port number is invalid");
        }
        mgmtServer = this;
        init();
    }


    private void init() {

        // DataDocTaskTracker needs the maximum number nodes that will EVER
        // possibly be running. Getitng num_nodes from config doesn't
        // work, as expansion causes num_nodes to go from 8->16 which will 
        // result in an ArrayIndexOutOfBounds
        int maxClusterSize = CMM.MAX_NODES; //Utils.getNumNodes();
        _ddTasks = new HashMap();
        for (int i = 0; i < TaskList.numTasks(); i++) {
            String name = TaskList.taskLabel(i);
            _ddTasks.put(name,
                        new DataDocTaskTracker(i, name,
                                               maxClusterSize, 
                                               Utils.getDisksPerNodes()));
        }

        // Always make sure admin account is properly setup on the 
        // master node by calling changeAdmin().  This will ensure
        // public keys are correctly installed and the admin
        // user account is present.
        ClusterProperties props = ClusterProperties.getInstance();
        cuser   = props.getProperty (ConfigPropertyNames.PROP_ADMIN_USER,
                                            DEFAULT_ADMIN_USER);
        cpasswd = props.getProperty (ConfigPropertyNames.PROP_ADMIN_PASSWD,
                                            DEFAULT_ADMIN_PASSWD);
        cpubkey = props.getProperty (ConfigPropertyNames.PROP_ADMIN_PUBKEY,
                                            DEFAULT_ADMIN_PUBKEY);
        changeAdmin(cuser, cpasswd, cpubkey);

        mdClient = MetadataClient.getInstance();
    }

    static public MgmtServer getInstance() {
        return mgmtServer;
    }

    //Update the savedQueryIntegrityTime
    void setSavedQueryIntegrityTime()  {
        long oldValue = savedQueryIntegrityTime;
        if (oldValue == queryIntegrityTime) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Skipping update of savedQueryIntegrityTime "+
                         "since value is already "+queryIntegrityTime);
            }
        } else {
            logger.info("Changing savedQueryIntegrityTime from "+
                     oldValue+" to "+queryIntegrityTime);
            savedQueryIntegrityTime = queryIntegrityTime;
        } // if
    }

    void updateQueryIntegrity() {

        long oldQueryIntegrityTime = queryIntegrityTime;
        long oldSavedQueryIntegrityTime = savedQueryIntegrityTime;

        //Get HADB create time from metadata client
        hadbCreateTime = mdClient.getLastCreateTime();

        if (superCycleStart != 0 && 
            superCycleStart > superRunStart &&
            hadbCreateTime > 0 &&
            superCycleStart > hadbCreateTime) {
            //We have a new value for queryIntegrityTime
            queryIntegrityTime = superCycleStart;

            setSavedQueryIntegrityTime();

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Setting new queryIntegrityTime="+
                            queryIntegrityTime+
                            " tsuperRunStart="+superRunStart+
                            " tsuperCycleStart="+superCycleStart+
                            " hadbCreateTime="+hadbCreateTime);
            }
        } else if (savedQueryIntegrityTime != 0 &&
                   hadbCreateTime > 0 &&
                   savedQueryIntegrityTime > hadbCreateTime) {
            queryIntegrityTime = savedQueryIntegrityTime;
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Using savedQueryIntegrityTime="+
                            savedQueryIntegrityTime+":"+
                            " superRunStart="+superRunStart+
                            " superCycleStart="+superCycleStart+
                            " hadbCreateTime="+hadbCreateTime);
            }
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Resetting queryIntegrityTime to 0!"+
                            " savedQueryIntegrityTime="+
                            savedQueryIntegrityTime+
                            " superRunStart="+superRunStart+
                            " superCycleStart="+superCycleStart+
                            " hadbCreateTime="+hadbCreateTime);
            }

            queryIntegrityTime = 0;
            setSavedQueryIntegrityTime();
        }

        if (queryIntegrityTime != oldQueryIntegrityTime ||
            savedQueryIntegrityTime != oldSavedQueryIntegrityTime) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("query integrity time changed from "+
                            oldQueryIntegrityTime+" to "+
                            queryIntegrityTime+
                            " and savedQueryIntegrityTime from "+
                            oldSavedQueryIntegrityTime+" to "+
                            savedQueryIntegrityTime+".");
            } // if
        } // if
    }

    public ManagedService.ProxyObject getProxy () {
        return new Proxy(queryIntegrityTime);
    }


    protected void executeInRunLoop() {
        //
        // FIXME - not enabled yet
        //
        work();
        checkExpansion();
        
        ServiceManager.publish(this);
    }

    private void changeAdmin(String user, String passwd, String pubkey) {
        
        String home = DEFAULT_PREFIX + "/home/" + user;
        String ssh  = home + "/.ssh";
        
        try {
            Exec.exec (DEFAULT_PREFIX + "/sbin/setup_admin_account.pl "
                       + user + " " + passwd);
        } catch (IOException e) {
            logger.log(Level.SEVERE,
                       "Couldn't update the admin account ["+e.getMessage()+"]",
                       e);
        }
        
        // create the ssh dir and file structure
        if (pubkey != null) {
            mkdir (ssh);
            String authkeys = ssh + "/authorized_keys";
            FileOutputStream out = null;
            
            try {
                out = new FileOutputStream (authkeys, false); // do not append
                out.write (pubkey.getBytes());
            }
            catch (IOException ioe) {
                logger.severe ("Unable to write " + authkeys);
            }
            finally {
                if (out != null) {
                    try { out.close(); } catch (Exception e) {}
                }
            }
        }
        
        // massage directory ownership
        try {
            Exec.exec ("/usr/bin/chown -R " + user + " " + home);
        } catch (IOException ioe) {
            logger.log (Level.WARNING,
                        "unable to adjust ownership on home directory for "
                        + user, ioe);
        }
        
        // massage directory group ownership
        try {
            Exec.exec ("/usr/bin/chgrp -R " + DEFAULT_ADMIN_GID + " " + home);
        } catch (IOException ioe) {
            logger.log (Level.WARNING,
                        "unable to adjust group on home directory for "
                        + user, ioe);
        }
    }        
        

    /**
     * Called every loop of the run() method, this function reads the
     * various cluster config from ClusterProperties and updates the required
     * system stuff
     */

    private void work () {
        ClusterProperties props = ClusterProperties.getInstance();
        String user   = props.getProperty (ConfigPropertyNames.PROP_ADMIN_USER,
                                            DEFAULT_ADMIN_USER);
        String passwd = props.getProperty (ConfigPropertyNames.PROP_ADMIN_PASSWD,
                                            DEFAULT_ADMIN_PASSWD);
        String pubkey = props.getProperty (ConfigPropertyNames.PROP_ADMIN_PUBKEY,
                                            DEFAULT_ADMIN_PUBKEY);

        if ((user != null && !user.equalsIgnoreCase(cuser)) || 
            (passwd != null && !passwd.equals(cpasswd)) ||
            (pubkey != null && !pubkey.equals(cpubkey)))
        {
            cuser = user;
            cpasswd = passwd;
            cpubkey = pubkey;
            changeAdmin(cuser, cpasswd, cpubkey);
        }
        
        // calculate some data doctor statistics

        monitorDataDoctor();

        // if we are in possible data loss mode, run a check
        // if it passes, take us back out of this mode
        if(props.getPropertyAsBoolean(ConfigPropertyNames.PROP_DATA_LOSS)) {
            logger.log(Level.WARNING, "POSSIBLE DATA LOSS: Running sucker");

            int numNodes = props.getPropertyAsInt ("honeycomb.cell.num_nodes");
            int lostObjects = 0;
            mkdir ("/var/adm/sucker");
            String suckerDir = "/var/adm/sucker";
            String suckerOutput = suckerDir + "/OUTPUT";
            mkdir(suckerDir);
            String runSucker = "/opt/honeycomb/sbin/run_sucker.sh " + numNodes + " " + suckerDir;
            String analyzeSucker = "/opt/honeycomb/sbin/analyze -d " +
                suckerDir + " -n " + numNodes + " > " + suckerOutput;
            boolean passed = false;
            try {
                logger.log(Level.INFO, "Running " + runSucker + "...");
                Exec.exec(runSucker);
                logger.log(Level.INFO, "Returned.");
            } catch (IOException ioe) {
                logger.log (Level.WARNING, "Failed to run sucker cmd " +
                            runSucker + ": " + ioe);
                return;
            }
            try {
                logger.log(Level.INFO, "Running " + analyzeSucker + "...");
                lostObjects = Exec.exec(analyzeSucker);
                logger.log(Level.INFO, "Returned " + lostObjects + " lost.");
            } catch (IOException ioe) {
                logger.log (Level.WARNING, "Failed to analyze suck cmd " +
                            analyzeSucker + ": " + ioe);
                return;
            }

            if(lostObjects == 0) {
                logger.log(Level.WARNING,
                           "SUCKER PASSED - LEAVING POSSIBLE DATA LOSS MODE");
                try {
                    props.put(ConfigPropertyNames.PROP_DATA_LOSS, "false");
                } catch (ServerConfigException e) {
                    logger.log(Level.SEVERE,
                               "Failed to unset clusterWide possible dl: " + e);
                }
                // TODO: should we wait to confirm updated?
            } else {
                logger.log(Level.SEVERE,
                           "SUCKER FAILED - " + lostObjects +
                           " OBJECTS ARE LOST");
            }
        }
    }

    private void monitorDataDoctor() {
        // Do not start monitoring DataDoctor cycles until we have quorum. 
        // This should correct the issue of seeing super-fast, invalid cycles
        // completing as the cluster comes up.
        try {
            NodeMgrService.Proxy nodeproxy =
                ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            nodeproxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            if (nodeproxy == null || !nodeproxy.hasQuorum()) {
                if (_monitorDDfirstRun && logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, 
                            "Delayed datadoctor task monitoring: "
                            + "cluster does not have quorum");
                    _monitorDDfirstRun = false;
                }
                return;
            }
        } catch (Exception e) {
            if (_monitorDDfirstRun && logger.isLoggable(Level.INFO))
                logger.log(Level.INFO, 
                        "Delayed datadoctor task monitoring: " 
                        + "quorum state cannot be determined");
            _monitorDDfirstRun = false;
            return;
        }

        Node nodes[] = null;
        try {
            nodes = Utils.getNodes();
        } catch (AdminException ae) {
            logger.severe(
                    "Unable to get the \"Node\" objects");
            return;
        }
        
        int numNodes = ClusterProperties.getInstance()
            .getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
        
        for (int i = 0; i < numNodes; i++) {
            int nodeid = nodes[i].nodeId();                
            DataDocProxy ddProxy = DataDocProxy.getDataDocProxy(nodes[i].nodeId());
            if (null==ddProxy) {
                logger.info("DataDocProxy not yet available on node " + nodes[i].nodeId());
                //continue;
            }
            DiskProxy proxy = null;
            try {
                proxy = Utils.getDiskMonitorProxy();
            } catch (AdminException ae) {
                logger.severe(
                        "Unable to get DiskProxy for node id " + nodeid);
                return;
            }
            com.sun.honeycomb.disks.Disk[] disks = proxy.getDisks(nodeid);

            // This logs spuriously on 8-node clusters
            //if (disks == null) {
            // Probably node offline; deal with null disks later.
            //  logger.warning(
            //      "Unable to get disk info from DiskProxy for node id " 
            //      + nodeid);
            //}

            for (int j = 0; j < Utils.getDisksPerNodes(); j++) {
                // for each task, when disk completes write in the 
                // timestamp of the completion.
                // when the last disk in a task completes, the cycle is 
                // complete.
                // calculate a diff for each disk from start time, 
                // noting the min, avg and max times, and note these. 
                // note the task completion time.
                for (int k = 0; k < TaskList.numTasks(); k++) {
                    String taskName = TaskList.taskLabel(k);
                    DataDocTaskTracker task 
                        = (DataDocTaskTracker) _ddTasks.get(taskName);
                    // if we think the node/disk is dead, set some known
                    // bad values for each disk that we'll check for
                    if (!nodes[i].isAlive()) {
                        if (logger.isLoggable (Level.FINE)) {
                            logger.fine ("found offline node id " + nodeid);
                        }
                        task.updateCompletion (nodeid, j, -1, -1, -1, -1, -1, -1);
                        continue;
                    } 
                    if (disks == null || disks[j] == null) {
                        if (logger.isLoggable (Level.FINE)) {
                            logger.fine ("found null disk for node id " 
                                    + nodeid + " disk index " + j);
                        }
                        task.updateCompletion (nodeid, j, -1, -1, -1, -1, -1, -1);
                        continue;
                    }
                    if (!disks[j].isEnabled()) {
                        if (logger.isLoggable (Level.FINE)) {
                            logger.fine ("found offline disk for node id "
                                    + nodeid + " disk index " + j);
                        }
                        task.updateCompletion (nodeid, j, -1, -1, -1, -1, -1, -1);
                        continue;
                    }

                    if (ddProxy == null) {
                        // If we got here, something's wrong. The node is alive, but we can't get it's DDProxy.
                        // There's really nothing we can do but carry on and hope it fixes itself. If this happens
                        // for an extended period of time, this will lead to task completion in the CLI/AdminGui
                        // getting "stuck."
                        logger.severe ("DataDoc proxy not available");
                        continue;
                    }
                    
                    // cyclesDone, numFaults, completion, startTime
                    int numFaults = 0;
                    int completion = 0;
                    int cyclesDone = 0;
                    long cycleStart = 0;
                    long currentRunStart = 0;
                    long currentCycleStart = 0;

                    numFaults=ddProxy.numFaults(k,j);
                    completion=ddProxy.completion(k,j);
                    cyclesDone=ddProxy.cyclesDone(k,j);
                    cycleStart=ddProxy.cycleStart(k,j);
                    currentRunStart=ddProxy.currentRunStart(k,j);
                    currentCycleStart=ddProxy.currentCycleStart(k,j);

                    task.updateCompletion(nodeid, j, cycleStart,
                            completion, 
                            cyclesDone,
                            numFaults,
                            currentRunStart,
                            currentCycleStart);
                }
            }
        }

        // we've now updated everything, check for completion.
        // for each task, if it's complete, call reset() which will store
        // away the completion timestamp and zero out all completion times.
        for (Iterator i = _ddTasks.values().iterator(); i.hasNext();) {
            DataDocTaskTracker task = (DataDocTaskTracker) i.next();
            if (task.getName().equals(TaskList.POP_EXT_TASK)) {
                superRunStart = task.getSuperRunStart();
                superCycleStart = task.getSuperCycleStart();
            }

            if (task.isComplete()) {
                if (task.getErrorCount() > 0 ) {
                    String str = BundleAccess.getInstance().getBundle().
                    getString("warn.datadoctor.errors_in_cycle");
                    Object [] args = { task.getName(), task.getErrorCount() };
                    logger.log (ExtLevel.EXT_WARNING, MessageFormat.format (str, args));
                    logger.log (Level.WARNING, MessageFormat.format (str, args));
                }
                task.reset();
            }
        }
        
        //Update queryIntegrityTime
        updateQueryIntegrity();
    }

    /**
     * Create a directory if it does not already exist
     */
    private static void mkdir (String dirname) {
        File dir = new File (dirname);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                logger.warning ("failed to make directory: " + dirname);
            }
        }
    }

    private void checkExpansion () {
        String status = config.getProperty(ConfigPropertyNames.PROP_EXP_STATUS);
        if (logger.isLoggable (Level.FINE))
            logger.fine ("expansion status=" + status);
        
        if (! status.equals(HCCellAdapter.EXPAN_STR_EXPAND)) {
            if (logger.isLoggable (Level.FINE))
                logger.fine ("skipping expansion check");
            return;
        }

        long expansionStarted 
            = config.getPropertyAsLong(ConfigPropertyNames.PROP_EXP_START);
        long recoverLastCompletion 
            = getPreviousCompletedCycleTime(TaskList.taskId("RecoverLostFrags"));
        logger.info ("expansion started: " + expansionStarted);
        logger.info ("recoverLastCompletion: " + recoverLastCompletion);

        if (recoverLastCompletion > expansionStarted) {
            logger.info("expansion completed");
            // this means we've completed a full recovery cycle since
            // expansion began. We're fully expanded...
            try {
                config.put(ConfigPropertyNames.PROP_EXP_STATUS,
                           HCCellAdapter.EXPAN_STR_DONE);
            } catch (Exception e) {
                logger.warning ("unable to set expansion status to done");
            }
        }
    }
    public String getTaskName (int task) {
        return TaskList.taskLabel (task);
    }

    public int getNumTasks() {
        return TaskList.numTasks();
    }

    public long getTaskCompletionTime (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getCompletionTime();
    }

    public long getPreviousCompletedCycleTime (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getPreviousCompletionTime();
    }
    public long getTaskSlowestDisk (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getSlowestDiskTime();
    }

    public long getTaskFastestDisk (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getFastestDiskTime();
    }

    public long getTaskAverageDisk (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getAverageDiskTime();
    }
    public long getTaskNumFaults (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getErrorCount();
        
    }
    public long getTaskCompletionPercent (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getCompletionPercent();
    }
    public long getTaskSuperRunStart (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getSuperRunStart();
    }
    public long getTaskSuperCycleStart (int task) {
        String taskName = TaskList.taskLabel (task);
        DataDocTaskTracker t = (DataDocTaskTracker) _ddTasks.get(taskName);
        return t.getSuperCycleStart();
    }
    public int getTaskId(String name) {
        return TaskList.taskId (name);
    }
    public void resetSysCache () {
        DataDocTaskTracker t 
            = (DataDocTaskTracker) _ddTasks.get(TaskList.POP_SYS_TASK);
        t.resetPreviousCompleted();
    }
    //
    // Login/logout support - logic lives is HCSiloAdapter.java
    // 
    public long getSessionId() {
        return _sessionId;
    }

    public void setSessionId(long sessionId) {
        _sessionId=sessionId;
    }

    public void updateSessionTime() {
        Date now=new Date();
        _sessionTime=now;
    }
    public void clearSessionTime() {
        _sessionTime=null;
    }
    public boolean isTimedOut() {
        Date now=new Date();
        if(_sessionTime==null) {
            return true;
        }
        if( (_sessionTime.getTime()+LOGIN_TIMEOUT) > now.getTime()) {
            return false;
        }else {
            return true;
        }
           
    }
    
    /** Get the proxy for the MgmtServer Service */
    public static MgmtServerIntf.Proxy getMgmtServerProxy() {

        NodeMgrService.Proxy nodemgrProxy = 
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        if (nodemgrProxy == null) {
            logger.warning("Failed to get the local node manager.");
            return null;
        }

        Node masterNode = nodemgrProxy.getMasterNode();
        if (masterNode == null) {
            logger.warning("Failed to get the master node proxy.");
            return null;
        }

        ManagedService.ProxyObject proxy =
            ServiceManager.proxyFor(masterNode.nodeId(), MgmtServer.class);

        if (proxy == null) {
            logger.warning("Couldn't get MgmtServerIntf proxy object.");
            return null;
        }

        if (!(proxy instanceof MgmtServerIntf.Proxy)) {
            logger.warning("Bad MasterService proxy: " + StringUtil.image(proxy));
            return null;
        }

        return (MgmtServerIntf.Proxy) proxy;
    }

    public static long getQueryIntegrityTime() {
        long queryTime;
        try {
            MgmtServerIntf.Proxy msProxy = MgmtServer.getMgmtServerProxy();
            if (msProxy == null)
                queryTime = 0;
            else
                queryTime = msProxy.getQueryIntegrityTime();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception in getting queryIntegrityTime",e);
            queryTime = 0;
        }
        return queryTime;
    }
    
    public void setSyscacheInsertFailureTime (long timestamp) 
        throws ManagedServiceException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("SysCache insert failure being inserted at "
                + timestamp + " (" + new Date (timestamp) + ")");
        }
        try {
            SysCache.getInstance().setInsertFailureTime(timestamp);
        } catch (ServerConfigException sce) {
            throw new ManagedServiceException (
                    "Unable to update config with syscache insert failure "
                    + timestamp + " (" + new Date (timestamp) + ")", sce);
        }
    }
}
