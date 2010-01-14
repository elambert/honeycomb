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



package com.sun.honeycomb.hadb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ResourceBundle;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.text.MessageFormat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.sql.SQLException;

import com.sun.hadb.adminapi.HADBException;
import com.sun.hadb.adminapi.MAException;
import com.sun.hadb.adminapi.MANotRunningException;
import com.sun.hadb.adminapi.MAInOtherDomainException;
import com.sun.hadb.adminapi.BundleAlreadyRegisteredException;
import com.sun.hadb.adminapi.MemberAlreadyDisabledException;
import com.sun.hadb.adminapi.MAConnection;
import com.sun.hadb.adminapi.MAConnectionFactory;
import com.sun.hadb.adminapi.MemberNotInThisDomainException;
import com.sun.hadb.adminapi.OperationState;
import com.sun.hadb.adminapi.ManagementDomain;
import com.sun.hadb.adminapi.MAUnreachableException;
import com.sun.hadb.adminapi.Database;
import com.sun.hadb.adminapi.DatabaseConfig;
import com.sun.hadb.adminapi.NodeConfig;
import com.sun.hadb.adminapi.OperationMonitor;
import com.sun.hadb.adminapi.MANotReadyException;
import com.sun.hadb.adminapi.LostConnectionException;
import com.sun.hadb.adminapi.DomainExistsException;
import com.sun.hadb.adminapi.SoftwareBundle;
import com.sun.hadb.adminapi.DomainMember;
import com.sun.hadb.mgt.NodeState;
import com.sun.hadb.mgt.DatabaseState;


import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;


import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;

import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.config.EMDConfigException;

/**
 * Monitor for HADB.
 *
 * This class is responsible to start and monitor HADB and tries as
 * much as possible to bring the database online without escalating
 * and wiping it.  It works around known and potentially unknown
 * issues with HADB, especially during initialization phase.
 *
 * In particular:
 *  - connect to different MA servers to be sure the state machine progress
 *  - heartbeat and recover and we get stuck talking to one MA server
 *
 * IMPORTANT: There can be only one HADB control operation active at
 * any time. This is an HADB design decision which lets them make their HA
 * guarantees.
 *
 * Note that the HADB MasterService exports some RMIs which call
 * methods here to perform certain HADB operations. (These are:
 * updateSchema, setPaths, recoverHost, and disableHost.) Since all
 * HADB operations are synchronized, it's possible that the state
 * machine might have to wait for these externally called methods to
 * finish.
 */
public class Hadbm extends Thread implements PropertyChangeListener {
    private static Logger LOG = Logger.getLogger(Hadbm.class.getName());
    
    static final int HADB_PORT = 1862;
    static final String PWD_ADMIN = "admin";
    
    static final int FIRST_NODE = 101;

    static final int MB = (1024 * 1024);

    // Re-try some operations this many times
    static final int NUM_RETRIES = 3;

    // If more than this many nodes refuse to join the domain, wipe
    // everything and re-start.
    static final int MAX_DOMAIN_OUTLIERS = 2;

    // When trying to create the domain, if we get more than this many
    // exceptions, wipe and re-start.
    static final int MAX_CREATEDOMAIN_EXCEPTIONS = 10;

    // When migrating over a 1.0 database to 1.1, print a progress
    // report log message every this many rows or this much time
    // (whichever comes first)
    static final int MIGRATE_SCHEMA_REPORT_FREQUENCY = 10000;
    static final int MIGRATE_SCHEMA_REPORT_INTERVAL = 300 * 1000; // 5 minutes

    // When trying an operation like "create database", if one MA
    // fails we try again with the next MA, wrapping around as
    // required. Don't want to do that endlessly, though; if we've
    // cycled around all MAs this many times, consider the operation a
    // failure. (0 means repeat until the operation timeout kicks in.)
    static int MAX_MA_LOOPS = 1; // Default: go around the MAs at most once.

    private static final String HC_DB_NAME = "honeycomb";    

    private static final String HADB_INSTALL_DIR = "/config/hadb_install";
    private static final String HADB_RAMDISK_DIR = "/opt/SUNWhadb";
    private static final String HADB_INSTALL_SYMLINK = HADB_INSTALL_DIR+"/4";
    private static final String HADB_RAMDISK_SYMLINK = HADB_RAMDISK_DIR+"/4";

    private static final int numFailuresTolerated = 3;

    private static Hadbm instance = null;
    
    private HadbState state;
    private Node hcNodes[];
    private long nodeDeathTimes[];
    private int nbNodes = 0; //So we can detect if this is the first time
    private MAConnection conn;
    private ManagementDomain domain;
    private String jdbcUrl;
    private boolean jdbcUrlIsReady;
    private Database database;
    private long heartbeatTime;
    private String hadbPasswdFile;
    private int nextMA;

    private volatile boolean isRunning;          // true ==> Hadbm can keep going, false ==> shut down
    private volatile boolean isForcedShutdown;   // true ==> bring down HADB forcefully

    // If we *know* that there is no domain (i.e. we're wiping and we
    // just wiped all nodes) then this is true.
    private boolean isDomainCleared = false;

    //system time when HADB was last created
    // or 0 if HADB is currently wiped/being created.
    private long lastCreateTime;

    private boolean schemaWorked = false;
    private int numFailures = 0;
    private long nonOperationalTime = 0;

    // If we're waiting, this is the current guard timeout value
    private long currentOpEndTime = 0;

    /*
     * HADB operation timeouts.
     * Modify with care.
     */
    static final long STARTUP_TIMEOUT         = (940 * 1000); // 15:40mn
    static final long MA_SETTLING_TIME        = (300 * 1000); // 5mn
    static final long CONNECT_TIMEOUT         = (180 * 1000) // 3mn
                                                + MA_SETTLING_TIME;
    static final long GET_DOMAIN_TIMEOUT      = (600 * 1000); // 5mn
    static final long CREATE_DOMAIN_TIMEOUT   = (600 * 1000); // 10mn
    static final long GET_DATABASE_TIMEOUT    = (180 * 1000); // 3mn
    static final long CREATE_DATABASE_TIMEOUT = (5400 * 1000);// 1h30mn (FIXME - slow disk?)
    static final long INIT_DATABASE_TIMEOUT   = (600 * 1000); // 10mn (FIXME - was 20mn)
    static final long START_DATABASE_TIMEOUT  = (900 * 1000); // 15mn
    static final long STOP_DATABASE_TIMEOUT   = (300 * 1000); // 5mn
    static final long GET_URL_TIMEOUT         = ( 60 * 1000); // 1mn
    static final long POLL_DELAY              = (  5 * 1000); // 5s
    static final long RETRY_SLEEP             = ( 15 * 1000); // 15s
    static final long HEARTBEAT_TIMEOUT       = (300 * 1000); // 5mn
    static final long NODE_DIED_TIMEOUT       = (120 * 1000); // 2mn   
    static final long RECONFIGURE_DATABASE_TIMEOUT = (120*60 * 1000); //120 min (FIXME - get real data on how long this takes)
    static final long NONOPERATIONAL_TIMEOUT  = (300 * 1000); // 5mn
    static final long UPGRADE_DATABASE_TIMEOUT = (120*60 * 1000); // 2 hours

    // This should be a few minutes... how long does it take to
    // convert DatabaseConverter.PROGRESS_TIMER_FREQUENCY rows?)
    static final long MIGRATE_SCHEMA_STEP_TIMEOUT  = (600 * 1000); // 10 min.

    static final long UPDATE_SCHEMA_TIMEOUT   = 120000;       // 2 minutes
    static final long SET_PATHS_TIMEOUT       = 60000;        // 1 minute
    static final long RECOVER_HOST_TIMEOUT    = 60000;        // 1 minute
    static final long DISABLE_HOST_TIMEOUT    = 60000;        // 1 minute
    
    // Defaults for properties that can be overridden
    // by setProps
    static final long DEFAULT_NODE_WIPE_TIMEOUT_SECS = 20 * 60; //20 min
    static final long DEFAULT_START_NODE_TIMEOUT_SECS = 20 * 60; // 20 min
    static final long DEFAULT_STOPPED_NODE_TIMEOUT_SECS = 2 * 60; //2 min
    
    // Properties that can overridden via set props
    static long nodeWipeTimeout=(DEFAULT_NODE_WIPE_TIMEOUT_SECS *1000); 
    static long nodeStoppedTimeout=(DEFAULT_STOPPED_NODE_TIMEOUT_SECS * 1000); 
    static long startNodeTimeout=(DEFAULT_START_NODE_TIMEOUT_SECS  *1000);
    
    /*
     * API
     */

    static synchronized Hadbm getInstance() {
        if (instance == null) {
            instance = new Hadbm();
        }
        return(instance);
    }

    static synchronized void resetInstance() {
        // This method should only be called when the active thread
        // has stopped. Anything else is a severe error and may cause
        // escalation and wipe.

        if (instance != null && instance.isAlive())
            LOG.severe("Hadbm thread is still alive, cannot reset!");
        else {
            instance = null;

            // Make sure no cached versions of the old run remain
            HADBJdbc.resetInstance();
            AttributeTable.resetSchema();
        }
    }
    
    synchronized void startup() {
        if (!isRunning) {
            isRunning = true;
            start();
        }
    }

    // shutdown shuts down HADB cleanly and then shuts down the
    // Hadbm thread instance.
    void shutdown() throws InterruptedException {
        if (!isAlive()) {
            LOG.warning("Shutdown requested but Hadbm is not alive!");
            return;
        }

        if (currentOpEndTime > 0) {
            long now = System.currentTimeMillis();
            String msg = "HADB shutdown: operation is in progress (" +
                (currentOpEndTime - now)/1000 + "s left).";
            LOG.warning(msg);
        }
        else
            LOG.info("Request for HADB shutdown.");

        isRunning = false;
        interrupt();
        join();
        LOG.info("HADB state machine shutdown complete.");
    }

    // forceShutdown shuts down the Hadbm thread without worrying about
    //  stopping HADB gracefully -- MasterService thread will call
    //  wipeAndRestartAll to reset to a known state.
    void forceShutdown() {
        while (isAlive()) {
            LOG.warning("Forced HADB shutdown.");

            isForcedShutdown = true;
            isRunning = false;
            interrupt();
            try {
                join();
            } catch (InterruptedException ignore) {
            }
        }
    }
    
    /**
     * Monitor the health of the HADB state machine. Interrupt the
     * current operation if the state machine appears to be stuck
     * waiting for an operation.
     *
     * @return true if the database is up and running.
     */
    boolean healthCheck() {
        if (!isAlive()) { 
            LOG.severe("Hadbm thread died unexpectedly!");
            return false; 
        } 

        long now = System.currentTimeMillis();
        long heartbeatElapsed = now - heartbeatTime;

        if (currentOpEndTime > 0) {
            if (now < currentOpEndTime) 
                // There's a long operation in progress. Say everything
                // is OK so the MasterService doesn't fire the doomsday
                // device and wipe the database.
                return true;

            LOG.warning("Operation timeout in state " + toString() +
                        ": interrupting controller thread.");
            interrupt();
        }

        else if (heartbeatElapsed >= HEARTBEAT_TIMEOUT) {
            LOG.warning("Heartbeat timeout in state " + toString() +
                        ": interrupting controller thread.");
            interrupt();
        }

        return isRunning();
    }
    
    String getJdbcUrl() {
        if (jdbcUrlIsReady)
            return jdbcUrl;
        else
            return null;
    }

    boolean isRunning() {
        return (state == HadbState.RUNNING);
    }

    boolean isInitializing() {
        // Do this to start MA servers sequentially (hcb101 then hcb102 10s 
        // later and so on) needed for HADB release 4.5.0-9 workaround.
        return (state.isNotPast(HadbState.WAIT_FOR_HADB));
    }
    
    boolean isDomainCleared() {
        return isDomainCleared;
    }

    long getLastCreateTime() {
        return lastCreateTime;
    }

    //Called when Hadbm starts up to read lastCreateTime from config
    //Should be called only from the Hadbm thread.
    void restoreLastCreateTime() {
        ClusterProperties props = ClusterProperties.getInstance();

        long lastTime =
            props.getPropertyAsLong(ConfigPropertyNames.PROP_HADBM_LAST_CREATE_TIME, 
                                    0);
        LOG.info("Restoring HADB lastCreateTime to "+lastTime+" from config.");
        lastCreateTime = lastTime;
    }

    //Change the config file version of the last HADBM create time.
    //Should be called only from the Hadbm thread.
    void setLastCreateTime(long timeValue) {
        long oldValue = lastCreateTime;
        if (oldValue == timeValue) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("skipping update of HADB lastCreateTime "+
                         "since value is already "+timeValue);
            }
        } else {
            LOG.info("Changing HADB lastCreateTime from "+
                     oldValue+" to "+timeValue);
            try {
                ClusterProperties.getInstance().put(ConfigPropertyNames.PROP_HADBM_LAST_CREATE_TIME, 
                                                    Long.toString(timeValue));
            } 
            catch (ServerConfigException e) {
                LOG.log(Level.SEVERE,
                        "Failed to update HADB lastCreateTime from "+
                        oldValue+" to "+timeValue, e);
                throw new RuntimeException("Unable to update "+
                                           ConfigPropertyNames.PROP_HADBM_LAST_CREATE_TIME+
                                           " to "+timeValue+" in config.",e);
            } // try/catch
            lastCreateTime = timeValue;
            MasterService.resetProxy();
        } // if
    }

    public String toString() {
        return state.toString();
    }
    
    // Can be called from the Hadbm thread or the MasterService thread
    synchronized void wipeAndRestartAll() throws StateMachineException {

        HADBJdbc.resetInstance();
        AttributeTable.resetSchema();

        setLastCreateTime(0);           //Note DB is currently wiped
        Node[] allNodes =
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).getNodes();
        
        for (int i = 0; i < allNodes.length; i++) {
            if (!allNodes[i].isAlive()) {
                continue;
            }

            int nodeID = allNodes[i].nodeId();
            HADBServiceInterface api = getRemoteService(nodeID);
            if (api == null) {
                // Can't reset that one, but continue wiping other nodes
                LOG.severe("Can't get HadbService RMI pointer for " + nodeID);
                continue;
            }

            try {
                api.wipeAndRestartForAll();
                heartbeat();
                LOG.info("Wipe and restart of node " + nodeID + " succeeded.");
                
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Exception, can't wipe node " + nodeID, e);
                // Continue with resetting other nodes
            }
        }

        isDomainCleared = true;
    }

    public void run() {
        LOG.info("HADB State Machine is starting up.");
        
        while (isRunning) {
            try {
                heartbeat();

                runStateMachine();

                long delay;
                if (state == HadbState.RUNNING) {
                    delay = RETRY_SLEEP;
                } else {
                    delay = POLL_DELAY;
                }
                Thread.currentThread().sleep(delay);

            } catch (InterruptedException ie) {
                LOG.info("Hadbm monitor thread interrupted.");
            }
            catch (Throwable e) {
                // (Bug 6551349) If the state machine throws any
                // exception, do not allow it to escalate to CM; log
                // it, and go to FAILURE, which will allow the state
                // machine to try to recover. (If recovery fails, it
                // will cause HADB to be wiped and restarted.)
                LOG.log(Level.SEVERE, "Exception in state machine", e);
                state = HadbState.FAILURE;
            }
        }

        // If forced shutdown, no need to shut down HADB gracefully
        if (isForcedShutdown) {
            LOG.warning("Forced shutdown -- exit without stopping HADB.");
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("warn.hadb.hadbm.forceshutdown");
            LOG.log(ExtLevel.EXT_WARNING, str); 
            return;
        }

        // Stop the database if this node is the master and if the db
        // is running.

        if (!isNodeMaster()) {
            LOG.info("Local node not master; exit without stopping HADB.");
            return;
        }

        if (state != HadbState.RUNNING) {
            LOG.severe("Cannot stop database in state " + state);
            return;
        }

        // Stop the database!

        // This should probably depend on the current state: if the
        // database hasn't been started yet, what happens if we try
        // to stop it? The important thing is that if there is a
        // database, we _must_ shut it down correctly.

        if (stopDb() != 0) {
            LOG.severe("Failed to shutdown database.");
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("err.hadb.hadbm.shutdownfail");
            LOG.log(ExtLevel.EXT_SEVERE, str);
        } else {
            LOG.info("Database stopped.");
        }

        // Do not allow this instance to be used again
        instance = null;
    }

    /** Whether or not the present node is the master. */
    private boolean isNodeMaster() {
        NodeMgrService.Proxy nodemgr =
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        Node master = nodemgr.getMasterNode();
        int localId = nodemgr.nodeId();

        if (master == null) {
            LOG.warning("Couldn't figure out which the master node is.");
            return false;
        }

        if (master.nodeId() != localId) {
            LOG.warning("Node " + localId + " is no longer the master (" + 
                        master.nodeId() + ").");
            return false;
        }

        return true;
    }

    // FIXME 
    // there is no guarantee that we hit the running state within
    // the time period.
    synchronized void updateSchema()
        throws StateMachineException {

        waitUntilState(HadbState.RUNNING, UPDATE_SCHEMA_TIMEOUT);
        schemaWorked = false;

        LOG.info("First phase done.");

        state = HadbState.CREATE_SCHEMA_2;
        waitUntilState(HadbState.RUNNING, UPDATE_SCHEMA_TIMEOUT);

        LOG.info("Second phase done.");

        if (!schemaWorked) {
            String err = "mdconfig error, re-try later with mdconfig -r";
            LOG.warning(err);
            throw new StateMachineException(err);
        }
    }

    /**
     * Convenience method, placed here so MasterService doesn't have
     * to worry about synchronization; all of it is in Hadbm.
     */
    synchronized void recoverHostForMove(int nodeId, int newDrive)
        throws StateMachineException, MemberNotInThisDomainException {

        recoverHost(nodeId);
        setPaths(nodeId, newDrive);
    }

    // FIXME -
    // there is no guarantee that we hit the running state within
    // the time period.
    synchronized void setPaths(int nodeId, int newDrive) 
        throws StateMachineException {
	
	String hostName = "hcb" + nodeId;

        // This _is_ dubious: the right thing to do is to get the
        // diskmonitor proxy and get the mount-point for disk
        // "newDrive". FIXME
        String devPath = "/data/" + newDrive + "/hadb";
        String histFile = devPath + "/history";
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE,"About to set device and history paths for " 
        	    + hostName + " to " + devPath + " and " + histFile);
        }
        waitUntilState(HadbState.RUNNING, SET_PATHS_TIMEOUT);

        int retries = NUM_RETRIES;
        while (retries-- > 0 && isRunning) {
            try {
                DatabaseConfig config = database.getDatabaseConfig();
                NodeConfig nodeConfig = getNodeConfigForHost(hostName,config);

                nodeConfig.setAttributeValue("DevicePath", devPath);
                nodeConfig.setAttributeValue("HistoryPath", histFile);

                LOG.info("Issuing path set change for " + hostName);

                if (setDatabaseConfig(config, startNodeTimeout)) {
                    LOG.info("Path set change for hcb" + nodeId + " succesful.");
                    return;
                } else {
                    LOG.log(Level.WARNING,"Failed to set new path for " 
                	    + hostName + ". " + retries + " retries left.");
                }
            }
            catch (HADBException e) {
                String msg = "Couldn't set new path for hcb" + nodeId +
                    " (retries left: " + retries + ").";
                LOG.log(Level.WARNING, msg, e);
                if (e instanceof LostConnectionException) {
                    LOG.log(Level.WARNING,"Connection to MA was lost during " +
                                "operation. Will attmept to gain new " +
                                "connection and try again.");
                    connectToNextMA();
                }
            }

            sleeper();
        }

        throw new StateMachineException("Failed to reset path for hcb" + nodeId);
    }
    
    /*
     * Get the NodeConfig for a particular node in the database.
     * 
     * Due to bug 6611477 in the HADB admin API, there currently does not 
     * exist a definitive way to get a handle on the NodeConfig for a
     * particular node. This method takes advantage of the current behavior that
     * database.getNodes() method and the config.getNodeConfigs() method return
     * elements in the same order (The first element of the list returned by
     * getNodeConfigs will be the config for the first element returned by 
     * getNodes() ). Since this is not specified behavior, we should not 
     * rely on this behavior working in future releases of hadb. 
     */
    private NodeConfig getNodeConfigForHost(String hostname, DatabaseConfig cfg) 
    throws HADBException, StateMachineException {
	int index = 0;
	NodeConfig config = null;
	if (cfg == null) {
	    throw new StateMachineException("Unable to retrieve handle on " +
	    		"database configuration.");
	}
	List dbNodes = database.getNodes();
	if (dbNodes == null) {
	    throw new StateMachineException("Failed to retrieve list of " +
	    		"database nodes. List is null.");
	}
	
	// Find where in the db nodes list is this host
	Iterator iter = dbNodes.iterator();
	while (iter.hasNext()) {
	    com.sun.hadb.adminapi.Node curNode = (com.sun.hadb.adminapi.Node)
	                                         iter.next();
	    String curHostName = curNode.getDomainMember().getHostName();
	    if (curHostName.equals(hostname)) {
		break;
	    }
	    index++;
	}
	
	List nodeConfigList = cfg.getNodeConfigs();
	if (nodeConfigList == null) {
	    throw new StateMachineException("Unable to retrieve node config " +
	    		"list from database config.");
	}
	
	if ( index < nodeConfigList.size()) {
	    config = (NodeConfig) nodeConfigList.get(index);
	} else {
	    throw new StateMachineException ("Can not find Node Config for " +
	    		"host " + hostname + ". Index value for host (" + 
	    		index + ") exceeds size of nodeConfig list size (" +
	    		nodeConfigList.size() + ").");
	}
	
	return config;
	
    }
    

    /** Set the database config and wait for it to complete */
    private boolean setDatabaseConfig(DatabaseConfig config, long timeout)
        throws HADBException, StateMachineException {

        assert Thread.holdsLock(this);

        OperationState curState = null;
        OperationMonitor monitor = database.reconfigureDatabase(config);

        // Now wait till the operation has completed

        long endTime = System.currentTimeMillis() + timeout;
        currentOpEndTime = endTime;

        while (endTime > System.currentTimeMillis() && isRunning) {
            logProgress(monitor, endTime);
            curState = monitor.getOperationState();

            if (curState.equals(OperationState.completed)) {
                currentOpEndTime = 0;
                return true;
            }

            if (!curState.equals(OperationState.active))
                // Operation no longer in progress
                break;

            sleeper();
        }

        // No longer waiting on a monitor
        currentOpEndTime = 0;

        // Timed out, or shutting down.
        if (curState.equals(OperationState.active)) {
            String reason = isRunning ? "operation timed out" :
                "StateMachine is stopping";
            LOG.info("Operation cancelled: " + reason);

            try {
                monitor.cancel();
            }
            catch (HADBException e) {
                LOG.log(Level.WARNING,"Couldn't cancel operation", e);
            }

            return false;
        }

        // Fatal error: no re-try possible, throw an exception.
        if (curState.equals(OperationState.obsolete)) {
            currentOpEndTime = 0;
            String msg = "Cancelled -- another HADB operation in progress?";
            throw new StateMachineException(msg);
        }

        // Re-tryable errors
        if (curState.equals(OperationState.failed)) {
            String failedReason = "(unknown)";
            Exception ex = monitor.getException();
            if (ex != null) {
                failedReason = ex.getMessage();
            }
            LOG.warning("Operation failed because " + failedReason);
        }
        else {
            LOG.warning("Operation is in an unknown state: " + curState);

            try {
                monitor.cancel();
            }
            catch (HADBException e) {
                LOG.log(Level.WARNING,"Couldn't cancel operation", e);
            }
        }

        return false;
    }

    // FIXME - 
    // there is no guarantee that we hit the running state within
    // the time period.
    synchronized void recoverHost(int nodeId)
        throws StateMachineException, MemberNotInThisDomainException {

        waitUntilState(HadbState.RUNNING, RECOVER_HOST_TIMEOUT);

        String[] hostname = new String[1];
        hostname[0] = "hcb" + nodeId;

        int retries = NUM_RETRIES;
        while (true) {
            currentOpEndTime = System.currentTimeMillis() + startNodeTimeout;
            try {
                LOG.info("Trying to recover " + hostname[0] + "...");
                domain.recoverMembers(hostname);
                currentOpEndTime = 0;
                return;
            } 
            catch (HADBException e) {
                LOG.log(Level.WARNING, "Couldn't recover " + hostname[0], e);
                if (e instanceof LostConnectionException ) {
                    LOG.info("MAConnection was lost while recovering host." +
                                " Will attempt to establish a new one");
                    connectToNextMA();
                }
                if (e instanceof MemberNotInThisDomainException) {
                	LOG.warning("Attempted to recover host (" + nodeId 
                			+ ") that is not in the HADB Domain");
                	throw (MemberNotInThisDomainException)e;
                }
                if (retries-- <= 0) {
                    currentOpEndTime = 0;
                    throw new StateMachineException(e);  
                }
            }

            sleeper();
        }
        
    }
    
    // FIXME - 
    // there is no guarantee that we hit the running state within
    // the time period.
    synchronized void disableHost(int nodeId)
        throws StateMachineException {

        // This method is called on disk failover -- after HadbService
        // has copied over files to the new disk, it stops the node
        // using this method. (After that its regular run() loop will
        // restart MA, which will use the new disk.)

        waitUntilState(HadbState.RUNNING, DISABLE_HOST_TIMEOUT);

        String hostName = "hcb" + nodeId;

        Set badHosts = new HashSet();
        badHosts.add(hostName);

        if (disableHosts(badHosts))
            // Yay!
            return;

        LOG.warning("Fallback: disable nodes HADB thinks are dead...");

        if (!disableHosts(getDeadNodes()))
            LOG.severe("Couldn't disable dead nodes!");
    }
    
    /*
     * Private
     */

        
    /*****************************************************************
     * State Machine implementation.
     *****************************************************************/
    
    /*
     * Possible states of HADB
     * comparable typesafe enums.  Mostly for easy printing
     */
    private static class HadbState implements Comparable {
        
        private final String name;
        private final int status;
        private static int nextOrdinal = 0;
        private final int ordinal = nextOrdinal++;
        
        private HadbState(String name, int status) {
            this.name = name;
            this.status = status;
        }
        
        public String toString() {
            return name;
        }
        
        public int compareTo(Object o) {
            if (o instanceof HadbState)
                return ordinal - ((HadbState)o).ordinal;

            throw new InternalException("Can't handle " + StringUtil.image(o));
        }

        public boolean isNotPast(HadbState s) {
            return ordinal <= s.ordinal;
        }

        public int getStatus() {
            return status;
        }
        
        /*
         * Pay attention to how the state values are assigned.
         *
         * The initialize method won't exit until we reach a state >= RUNNING
         * All init stage states should therefore have a value < RUNNING.
         */
        static final HadbState FAILURE =
            new HadbState("FAILURE", CliConstants.HCHADB_STATUS_FAILED);
        static final HadbState START =
            new HadbState("START", CliConstants.HCHADB_STATUS_CONNECTING);
        static final HadbState WAIT_FOR_HADB =
            new HadbState("WAIT MAs", CliConstants.HCHADB_STATUS_CONNECTING);
        static final HadbState SETUP_PASSWD_FILE =
            new HadbState("PASSWORD SETUP", CliConstants.HCHADB_STATUS_CONNECTING);
        static final HadbState ESTABLISH_CONNECTION =
            new HadbState("CONNECTING", CliConstants.HCHADB_STATUS_CONNECTING);
        static final HadbState CREATE_DOMAIN =
            new HadbState("CREATE DOMAIN", CliConstants.HCHADB_STATUS_INITIALIZING);
        static final HadbState GET_DOMAIN =
            new HadbState("GET DOMAIN", CliConstants.HCHADB_STATUS_INITIALIZING);
        static final HadbState CREATE_DB =
            new HadbState("CREATE DB", CliConstants.HCHADB_STATUS_INITIALIZING);
        static final HadbState GET_DB =
            new HadbState("GET DB", CliConstants.HCHADB_STATUS_INITIALIZING);
        static final HadbState INIT_DB =
            new HadbState("INIT DB", CliConstants.HCHADB_STATUS_INITIALIZING);
        static final HadbState CHECK_DB_STATE =
            new HadbState("CHECK DB", CliConstants.HCHADB_STATUS_INITIALIZING);
        static final HadbState GET_URL =
            new HadbState("GET URL", CliConstants.HCHADB_STATUS_INITIALIZING);
        static final HadbState MIGRATE_SCHEMA =
            new HadbState("MIGRATE SCHEMA", CliConstants.HCHADB_STATUS_UPGRADING);
        static final HadbState DO_UPGRADE =
            new HadbState("UPGRADE", CliConstants.HCHADB_STATUS_UPGRADING);
        static final HadbState CREATE_SCHEMA_1 =
            new HadbState("CREATE SCHEMA", CliConstants.HCHADB_STATUS_SETTING_UP);
        static final HadbState CREATE_SCHEMA_2 =
            new HadbState("CHECK SCHEMA", CliConstants.HCHADB_STATUS_SETTING_UP);
        static final HadbState PUBLISH_URL =
            new HadbState("PUBLISH URL", CliConstants.HCHADB_STATUS_SETTING_UP);
        static final HadbState ABOUT_TO_RUN =
            new HadbState("PRE-RUN", CliConstants.HCHADB_STATUS_SETTING_UP);
        static final HadbState RUNNING =
            new HadbState("RUNNING", CliConstants.HCHADB_STATUS_RUNNING);
    }
    

    private void runStateMachine() {
        int failure;
        
        if (state == HadbState.START) {
            try {
                getHadbPath();
                state = HadbState.WAIT_FOR_HADB;
            } catch (IOException e) {
                LOG.severe("SUNhadb path corrupt or missing");
                ResourceBundle rs = BundleAccess.getInstance().getBundle();
                String str = rs.getString("err.hadb.hadbm.pathcorrupt");
                LOG.log(ExtLevel.EXT_SEVERE, str);
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.WAIT_FOR_HADB) {
            waitForHadb();
            state = HadbState.SETUP_PASSWD_FILE;
            
        } else if (state == HadbState.SETUP_PASSWD_FILE) {
            failure = setupPasswdFile();
            switch(failure) {
            case 0:
                state = HadbState.ESTABLISH_CONNECTION;
                break;
            default:
                state = HadbState.FAILURE;
                break;
            }
            
        } else if (state == HadbState.ESTABLISH_CONNECTION) {
            failure = establishConnection();
            switch (failure) {
            case 0:
                state = HadbState.GET_DOMAIN;
                break;
            default:
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.GET_DOMAIN) {
            failure = getDomain();
            switch(failure) {
            case 0:
                if (domain != null) {
                    state = HadbState.GET_DB;
                } else {
                    state = HadbState.CREATE_DOMAIN;
                }
                break;
            default:
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.CREATE_DOMAIN) {
            failure = createDomain();
            switch(failure) {
            case 0:
                state = HadbState.GET_DB;
                break;
            default:
                // If we can't create a domain, bail out and start all over
                numFailures = numFailuresTolerated;
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.GET_DB) {
            failure = getDb();
            switch(failure) {
            case 0:
                if (database != null) {
                    state = HadbState.CHECK_DB_STATE;
                } else {
                    state = HadbState.CREATE_DB;
                }
                break;
            default:
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.CREATE_DB) {
            failure = createDb();
            switch (failure) {
            case 0:
                state = HadbState.INIT_DB;
                break;
            default:
                //If we can't create the database, that's it, wipe.
                numFailures = numFailuresTolerated;
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.INIT_DB) {
            failure = initDb();
            switch (failure) {
            case 0:
                state = HadbState.CHECK_DB_STATE;
                break;
            default:
                state = HadbState.FAILURE;
                break;
            }
            
        } else if (state == HadbState.CHECK_DB_STATE) {
            failure = checkDbState();
            switch(failure) {
            case 0:
                state = HadbState.GET_URL;
                break;
            default:
                //If we can't start the database, that's it, wipe.
                numFailures = numFailuresTolerated;
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.GET_URL) {
            failure = getUrl();
            switch (failure) {
            case 0:
                state = HadbState.DO_UPGRADE;
                break;
            default:
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.DO_UPGRADE) {
            try {
                failure = doUpgrade();
                switch (failure) {
                case 0:
                    state = HadbState.CREATE_SCHEMA_1;
                    try {
                        HADBJdbc.getInstanceWithUrl(jdbcUrl);
                        if (DatabaseConverter.isConvertInProgress()) {
                            state = HadbState.MIGRATE_SCHEMA;
                        } else {
                            state = HadbState.CREATE_SCHEMA_1;
                        }
                    } catch (EMDException e) {
                        LOG.log(Level.WARNING, "Couldn't convert database", e);
                        ResourceBundle rs = BundleAccess.getInstance().getBundle();
                        Object [] args = { new String(e.getMessage()) };
                        String str = rs.getString("warn.hadb.hadbm.convdb");
                        LOG.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));
                        numFailures = numFailuresTolerated;
                        state = HadbState.FAILURE;
                    }
                    break;
                default:
                    // If upgrade fails, it will never succeed -- don't re-try
                    numFailures = numFailuresTolerated;
                    state = HadbState.FAILURE;
                }
            } catch (Exception e) {
                // If this failed, it means the DB is hosed, and we should
                // wipe and re-start; make the state machine go to state
                // FAILURE.

                // EDL 07/29/07 
                // I think the assumption that any exception ecountered here
                // means the db is 'hosed' is not correct, for example, if the 
                // MAConnection is down, we'll encounter a LostConnectionException, 
                // in which case we should try and re-establish the connection
                // before wiping
                numFailures = numFailuresTolerated;
                state = HadbState.FAILURE;
            }
        } else if (state == HadbState.MIGRATE_SCHEMA) {
            try {
                HADBJdbc.getInstanceWithUrl(jdbcUrl);
                failure = migrateSchemaInPlace();
                switch (failure) {
                case 0:
                    // When Upgrade Schema completes, we are ready to run
                    state = HadbState.ABOUT_TO_RUN;
                    // Publicize the JDBC Url so other jobs can get it.
                    jdbcUrlIsReady = true;
                    MasterService.resetProxy();
                    break;
                default:
                    //If we can't upgrade the schema, that's it, wipe.
                    numFailures = numFailuresTolerated;
                    state = HadbState.FAILURE;
                }
            } catch (EMDException e) {
                LOG.log(Level.WARNING ,"Upgrade schema", e);
                //If we can't upgrade the schema, that's it, wipe.
                numFailures = numFailuresTolerated;
                state = HadbState.FAILURE;
            }

        } else if (state == HadbState.CREATE_SCHEMA_1) {
            try {
                HADBJdbc.getInstanceWithUrl(jdbcUrl);
                AttributeTable.activateSchema();
                state = HadbState.ABOUT_TO_RUN;

                // Publicize the JDBC Url so other jobs can get it.
                jdbcUrlIsReady = true;
                MasterService.resetProxy();

                // Get rid of the old "this JVM only" instance of HADBJdbc
                HADBJdbc.resetInstance();

            } catch (SQLException e) {
                LOG.log(Level.WARNING ,"Activate schema", e);
                state = HadbState.FAILURE;
            } catch (EMDConfigException e) {
                LOG.log(Level.WARNING ,"Activate schema", e);
                state = HadbState.FAILURE;
            } catch (EMDException e) {
                LOG.log(Level.WARNING ,"Activate schema", e);
                state = HadbState.FAILURE;
            }
        } else if (state == HadbState.CREATE_SCHEMA_2) {
            try {
                assert jdbcUrlIsReady : jdbcUrlIsReady;
                AttributeTable.activateSchema();
                schemaWorked=true;
                state = HadbState.ABOUT_TO_RUN;
            } catch (SQLException e) {
                LOG.log(Level.WARNING ,"Activate schema", e);
                state = HadbState.ABOUT_TO_RUN;
            } catch (EMDConfigException e) {
                LOG.log(Level.WARNING ,"Activate schema", e);
                state = HadbState.ABOUT_TO_RUN;
            } catch (EMDException e) {
                LOG.log(Level.WARNING ,"Activate schema", e);
                state = HadbState.FAILURE;
            }
            
        } else if (state == HadbState.ABOUT_TO_RUN) {
            numFailures = 0;
            // If DB is coming up and was wiped, then now becomes
            // the new lastCreateTime
            if (lastCreateTime == 0) {
                setLastCreateTime(System.currentTimeMillis());
            }
            state = HadbState.RUNNING;
            synchronized (this) {
                notify();
            }
            
        } else if (state == HadbState.RUNNING) {
            /* do all our periodic running tasks here */
            failure = periodicCheck();
            switch(failure) {
            case 0:
                break;
            default:
                state = HadbState.FAILURE;
            }
                        
        } else if (state == HadbState.FAILURE) {
            numFailures++;
            if (numFailures >= numFailuresTolerated) {
                LOG.warning("HADB is still not up: resetting and wiping it.");
                ResourceBundle rs = BundleAccess.getInstance().getBundle();
                String str = rs.getString("warn.hadb.hadbm.notup");
                LOG.log(ExtLevel.EXT_WARNING, str);
                try {
                    wipeAndRestartAll();
                } catch (StateMachineException e) {
                    LOG.log(Level.WARNING, "Couldn't reset all nodes", e);
                }
            }
            else
                LOG.info("Failed to start HADB; starting anew (" +
                         numFailures + " failures).");

            state = HadbState.START;            
        }
    }

    /*
     * Wait for MA servers to be running.
     * Note that this phase can run in lock step. 
     * See isAllowedToStart() in HadbService and isInitializing() in this
     * class.
     * Keep trying until it succeed or timeout
     * return 0 in case of success, -1 otherwise.
     */    
    private synchronized int waitForHadb() {
        // Make sure that after this method finishes, isDomainCleared
        // will be reset to false, no matter what.
        try {
            long timeout = System.currentTimeMillis() + STARTUP_TIMEOUT;
            currentOpEndTime = timeout;

            do {
                ManagedService.ProxyObject[] masrv =
                    ServiceManager.proxyFor(HadbService.class);

                int count = 0;
                int minCount = (nbNodes < masrv.length)? nbNodes : masrv.length;

                for (int i = 0; i < minCount; i++) {
                    if (!(masrv[i] instanceof HADBServiceProxy)) {
                        continue;
                    }
                    if (((HADBServiceProxy) masrv[i]).getRunning())
                        count++;
                }

                if (count == minCount) {
                    // Success!
                    LOG.info("Success: " + minCount + " MA servers running.");
                    return 0;
                }

                long rem = (timeout -  System.currentTimeMillis()) / 1000;
                LOG.info("Have " + count + " MA servers, waiting " + rem +
                         "s more ...");

                sleeper();

            } while (timeout > System.currentTimeMillis() && isRunning);

            LOG.severe("Timeout expired: couldn't start enough MA servers!");
            return -1;
        }
        finally {
            // After MAs have been started, we no longer want to
            // do any wipe-targeted optimizations (like parallel
            // MA start).
            isDomainCleared = false;
            currentOpEndTime = 0;     // not waiting any more
        }
    }

    /*
     * Try to connect to nextMA server.
     * We want to try to connect to all MA servers before progressing
     * to the next phase. We don't fail if we connected to at least one
     * MA server within the timeout.
     * return 0 in case of success, -1 otherwise.
     */
    private synchronized int establishConnection() {
        long timeout = System.currentTimeMillis() + CONNECT_TIMEOUT;
        currentOpEndTime = timeout;

        boolean foundAnMA = false;

        do {
            int numMAs = 0;
            for (int i = 0; i < nbNodes; i++)
                if (connectToNextMA())
                    numMAs++;

            if (numMAs > 0)
                foundAnMA = true;

            if (numMAs == nbNodes) {
                // The HADB team tells us that after we've started all
                // the MAs, we should wait "3-5 minutes" to make sure
                // they're all talking to each other and forming a
                // domain and all that.
                waitMASettlingTime();
                return 0;
            }

            long now = System.currentTimeMillis();

            LOG.info("Connected to " + numMAs + " MAs; re-trying (" +
                     (timeout - now)/1000 + "s left).");
                
        }  while (timeout > System.currentTimeMillis() && isRunning);
        currentOpEndTime = 0;

        if (foundAnMA) {
            // No point in waiting if we're shutting down
            if (isRunning)
                waitMASettlingTime();
            return 0;
        }
        else
            return -1;
    }

    private void waitMASettlingTime() {
        // Remember that MA_SETTLING_TIME is longer than a heartbeat

        LOG.info("Waiting " + MA_SETTLING_TIME/1000 + "s for MAs to settle.");

        try {
            heartbeat();
            Thread.sleep(MA_SETTLING_TIME/2);
            heartbeat();
            Thread.sleep(MA_SETTLING_TIME/2);
        }
        catch (InterruptedException e) {
            LOG.info("MA settling wait interrupted.");
        }
    }

    /*
     * create the database domain. Keep trying until it succeed or timeout
     * return 0 in case of success, -1 otherwise.
     */
    private synchronized int createDomain() {
        HashSet badHosts = new HashSet();
        int badHostCount = 0;
        int badCreateCount = 0;

        long timeout = System.currentTimeMillis() + CREATE_DOMAIN_TIMEOUT;
        currentOpEndTime = timeout;

        do {
            try {
                if (conn == null) {
                    // Need to wait till we have a connection
                    LOG.info("No HADB connection, waiting....");
                    continue;
                }

                LOG.info("Attempting to create domain...");
                domain = conn.createDomain(PWD_ADMIN, getDomainConfig(badHosts));
                if (domain != null) {
                    LOG.info("Domain created.");
                    currentOpEndTime = 0;
                    return 0;
                }
            }

            catch (DomainExistsException ee) {
                // This is bad!
                //
                // the domain exists on some nodes but not on the majority.
                // We have a choice between dealing with the problem or
                // letting the master service timeout and wipe everything.

                String thishost = ee.getHostName();
                LOG.warning("Domain already exists for " + thishost + "!");

                badHosts.add(Integer.toString(getNodeIdFromName(thishost)));
                badHostCount++;
                badCreateCount++;
            }

            catch (MAUnreachableException un) {
                String thishost = un.getHostName();
                LOG.warning("MA on " + thishost + " not reachable!");

                // Add to the list of unreachable nodes
                badHosts.add(Integer.toString(getNodeIdFromName(thishost)));
                badHostCount++;
                badCreateCount++;
            }

            catch (HADBException e) {
                LOG.log(Level.WARNING, "Failed to create domain", e);
                badCreateCount++;
            }
            
            if (badHostCount > MAX_DOMAIN_OUTLIERS) {
                LOG.warning("More than " + MAX_DOMAIN_OUTLIERS +
                            " nodes weren't willing to create the domain.");
                break;
            }

            if (badCreateCount > MAX_CREATEDOMAIN_EXCEPTIONS) {
                LOG.warning("Failed to create the domain after " +
                            MAX_CREATEDOMAIN_EXCEPTIONS);
                break;
            }

            // Log progress report
            logTimeLeft(timeout);

            sleeper();
            connectToNextMA();
            
        } while (timeout > System.currentTimeMillis() && isRunning);
        currentOpEndTime = 0;
        
        LOG.severe("Couldn't create domain; wipe and re-start.");
        ResourceBundle rs = BundleAccess.getInstance().getBundle();
        String str = rs.getString("err.hadb.hadbm.domainfail");
        LOG.log(ExtLevel.EXT_SEVERE, str);
        numFailures = numFailuresTolerated;
        return -1;
    }
    
    /**
     * Get the domain.
     * Try to determine if there is a valid hadb domain.
     * This function waits until all but 2 nodes agree that there is a domain
     * or until all nodes agree that there is no domain. The function fails
     * if most of the nodes but not all agree there is no domain.
     * @return 0 in case of success, -1 otherwise.
     */
    private synchronized int getDomain() {        
        long now = System.currentTimeMillis();
        long timeout = now + GET_DOMAIN_TIMEOUT;

        currentOpEndTime = timeout;

        do {
            int domainDoesNotExists = 0;
            int domainExists = 0;

            // Holy obfuscated code!

            int nbRetry = nbNodes;
            do {
                try {
                    if (conn != null) {
                        LOG.info("Attempting to get domain...");

                        domain = conn.getDomain();
                        if (domain != null) {
                            if ((++domainExists) >= (nbNodes - 2)) {
                                currentOpEndTime = 0;
                                LOG.info("Get domain succeeded.");
                                currentOpEndTime = 0;
                                return 0;
                            }
                        }
                        else if ((++domainDoesNotExists) == nbNodes) {
                            currentOpEndTime = 0;
                            LOG.info("Get domain succeeded.");
                            currentOpEndTime = 0;
                            return 0;
                        }
                    }
                }
                catch (HADBException e) {
                    LOG.log(Level.WARNING, "getDomain failed! Re-trying",  e);
                }
            } while (connectToNextMA() == true && (--nbRetry) > 0);

            if (domainDoesNotExists > (nbNodes / 2)) {
                break;
            }

            logTimeLeft(timeout);
            sleeper();
            connectToNextMA();

        } while (timeout > System.currentTimeMillis() && isRunning);
        currentOpEndTime = 0;

        LOG.severe("Couldn't get domain!");
        domain = null;
        return -1;
    }

    /*
     * create the database. Keep trying until it succeed or timeout
     * return 0 in case of success, -1 otherwise.
     */
    private synchronized int createDb() {
        setLastCreateTime(0);           //Note DB is currently wiped
        long timeout = System.currentTimeMillis() + CREATE_DATABASE_TIMEOUT;
        currentOpEndTime = timeout;

        // When we go around MAs, if all MAs report failure then we
        // can give up. Actually it's slightly more conservative: the
        // number of times around is defined by a config parameter;
        // the default is 1.
        int startingMA = nextMA;
        int numMALoops = 0;

        OperationMonitor monitor = null;
        do {
            try {
                if (monitor != null) {
                    logProgress(monitor, timeout);

                    // Note: waitUntilCompleted will return only if
                    // the timeout period passed with no progress!
                    // Instead, check monitor.getOperationState() to
                    // see if it's finished.
                    if (monitor.getOperationState() == OperationState.completed) {
                        currentOpEndTime = 0;
                        LOG.info("Created database.");
                        return 0;
                    }

                    sleeper();
                }
                else if (domain != null) {
                    DatabaseConfig config = getDbConfig();
                    LOG.info("Attempting to create database....");
                    monitor = domain.createDatabase(config);
                }
                else {
                    sleeper();
                    connectToNextMA();

                    if (nextMA == startingMA) {
                        numMALoops++;

                        if (MAX_MA_LOOPS > 0 && numMALoops >= MAX_MA_LOOPS) {
                            // Give up.
                            LOG.warning("Too many times (" + numMALoops +
                                        ") around all MAs; abort.");
                            break;
                        }
                        else
                            LOG.info("Back at starting MA (" + nextMA + ") " +
                                     numMALoops + " time(s).");
                    }
                }
            }
            catch (HADBException e) {
                LOG.log(Level.SEVERE, "Failed to create database", e);

                if (monitor != null) {
                    try {
                        monitor.cancel();
                    }
                    catch (HADBException ignore) {}
                    monitor = null;
                }
                sleeper();
                connectToNextMA();
 
                if (nextMA == startingMA) {
                    numMALoops++;

                    if (MAX_MA_LOOPS > 0 && numMALoops >= MAX_MA_LOOPS) {
                        // Give up.
                        LOG.warning("Too many times (" + numMALoops +
                                    ") around all MAs; abort");
                        break;
                    }
                    else
                        LOG.info("Back at starting MA (" + nextMA + ") " +
                                 numMALoops + " time(s).");
                }

            }

        } while (timeout > System.currentTimeMillis() && isRunning);
        currentOpEndTime = 0;
        
        if (monitor != null) {
            try {
                logProgress(monitor, timeout);

                if (monitor.getOperationState() == OperationState.completed) {
                    LOG.info("Created database.");
                    return 0;
                }
                monitor.cancel();
            }
            catch (HADBException e) {
                LOG.log(Level.INFO, "Ignoring exception", e);
            }
        }

        LOG.warning("Couldn't create database.");
        return -1;
    }
        
    /*
     * get the database
     * We want to check that a majority of MA servers say the same thing
     * return 0 in case of success, -1 otherwise.
     */
    private synchronized int getDb() {
        long timeout = System.currentTimeMillis() + GET_DATABASE_TIMEOUT;
        currentOpEndTime = timeout;

        do {
            int databaseDoesNotExists = 0;
            int databaseExists = 0;
            int nbRetry = nbNodes;

            do {
                try {
                    if (domain != null) {
                        LOG.info("Trying to get database....");

                        database = domain.getDatabase(HC_DB_NAME);
                        if (database != null) {
                            if ((++databaseExists) >= (nbNodes/2)) {
                                currentOpEndTime = 0;
                                LOG.info("GetDB succeeded.");
                                return 0;
                            }
                        }
                        else {
                            if ((++databaseDoesNotExists) >= (nbNodes/2)) {
                                currentOpEndTime = 0;
                                LOG.info("GetDB succeeded.");
                                return 0;
                            }
                        }
                    }
                }
                catch (HADBException e) {
                    LOG.log(Level.WARNING, "Failed to get database", e);
                    break;
                    
                }
            } while (connectToNextMA() == true && (--nbRetry) > 0);
            
            logTimeLeft(timeout);
            sleeper();
            connectToNextMA();
            
        } while (timeout > System.currentTimeMillis() && isRunning);
        currentOpEndTime = 0;

        LOG.severe("Couldn't get database!");
        database = null;
        return -1;
    }
    
    /*
     * initialize the database. Keep trying until it succeed or timeout
     * return 0 in case of success, -1 otherwise.
     */
    private synchronized int initDb() {
        long timeout = System.currentTimeMillis() + INIT_DATABASE_TIMEOUT;
        currentOpEndTime = timeout;

        OperationMonitor monitor = null;
        do {            
            try {
                if (monitor != null) {
                    logProgress(monitor, timeout);

                    if (monitor.getOperationState() == OperationState.completed) {
                        currentOpEndTime = 0;
                        LOG.info("DB initialized.");
                        return 0;
                    }

                    sleeper();
                }
                else if (database != null) {
                    LOG.info("Attempting to initialize database...");
                    monitor = database.initialize(false);

                }
                else {
                    sleeper();
                    connectToNextMA();
                }
            }
            catch (HADBException e) {
                LOG.log(Level.WARNING, "Failed to initialize database", e);
                if (monitor != null) {
                    try {
                        monitor.cancel();
                    }
                    catch (HADBException ignore) {}
                    monitor = null;
                }
                sleeper();
                connectToNextMA();                
            }

        } while (timeout > System.currentTimeMillis() && isRunning);
        currentOpEndTime = 0;
            
        if (monitor != null) {
            try {
                if (monitor.getOperationState() == OperationState.completed) {
                    LOG.info("DB initialized.");
                    return 0;
                }                
                monitor.cancel();
            }
            catch (HADBException ignore) {}
        }    

        LOG.warning("Couldn't initialize database!");        
        return -1;
    }
    
    /*
     * check and start the database. Keep trying until it succeed or timeout
     * return 0 in case of success, -1 otherwise.
     */    
    private synchronized int checkDbState() {
        long timeout = System.currentTimeMillis() + START_DATABASE_TIMEOUT;
        currentOpEndTime = timeout;

        OperationMonitor monitor = null;
        do {
            try {
                if (monitor != null) {
                    logProgress(monitor, timeout);

                    if (monitor.getOperationState() == OperationState.completed) {
                        currentOpEndTime = 0;
                        LOG.info("DB state OK.");
                        return 0;
                    }

                    sleeper();
                }
                else if (database != null) {
                    DatabaseState theState = database.getDatabaseState();

                    if (databaseStateOK(theState)) {
                        LOG.info("Database already started (" + theState + ")");
                        currentOpEndTime = 0;
                        return 0;
                    }

                    // Try to start the database

                    if (theState == DatabaseState.stopped) {
                        LOG.info("Trying to start the database...");
                        monitor = database.start();
                    }
                    else if (theState == DatabaseState.unknown) {
                        LOG.warning("Some nodes missing; try to start anyway.");
                        try {
                            monitor = database.start();                        
                        }
                        catch (MANotRunningException e) {
                            // This means one (or more) of the nodes
                            // isn't running, so disable it/them.
                            LOG.info("DB start failed; trying to disable dead nodes...");
                            if (!disableHosts(getDeadNodes())) {
                                LOG.info("Failed; try with new MA.");
                                //FIXME - do the same as below
                                sleeper();
                                connectToNextMA();
                            }
                        }
                    }
                    else {
                        // DatabaseState.nonOperational
                        LOG.warning("Database is non-operational.");
                        sleeper();
                        connectToNextMA();
                    }                        
                }
                else {
                    // database == null
                    sleeper();
                    connectToNextMA();
                }
            // Possibly catch InvalidDatabaseStateException and
            // DatabaseDeletedException here, which would mean the DB
            // is hosed, and we should wipe. (Maybe only after we've
            // tried all MAs MAX_MA_LOOPS times.)
            } catch (HADBException e) {
                LOG.log(Level.WARNING, "Failed to start database; re-trying", e);
                monitor = null;
                sleeper();
                connectToNextMA();
            }
        
        } while (timeout > System.currentTimeMillis() && isRunning);
        currentOpEndTime = 0;
        
        if (monitor != null) {
            logProgress(monitor, timeout);

            try {
                if (monitor.getOperationState() == OperationState.completed) {
                    LOG.info("DB state OK.");
                    return 0;
                }                
                monitor.cancel();
            }
            catch (HADBException ignore) {}

            // Well, we tried to start, and it didn't work.  time to wipe.
            numFailures = numFailuresTolerated;
        }

        LOG.warning("Couldn't start database!");
        return -1;        
    }

    /*
     * Get the JDBC URL - Keep trying until it succeed or timeout
     * return 0 in case of success, -1 otherwise.
     */
    private synchronized int getUrl() {
        long timeout = System.currentTimeMillis() + GET_URL_TIMEOUT;
        currentOpEndTime = timeout;

        do {
            try {
                if (database != null) {
                    jdbcUrl = database.getJdbcUrl();
                    if (jdbcUrl != null) {
                        LOG.info("Returning URL \"" + jdbcUrl + "\"");
                        currentOpEndTime = 0;
                        return 0;
                    }
                }
            } catch (HADBException e) {
                LOG.log(Level.WARNING, "Failed to get JDBC URL", e);
            }

            logTimeLeft(timeout);
            sleeper();
            connectToNextMA();
            
        } while (timeout > System.currentTimeMillis() && isRunning);
        currentOpEndTime = 0;

        LOG.severe("Couldn't get JDBC URL.");
        return -1;
    }

    /*
     * See if we need to upgrade the HADB data - 
     * keep doing the upgrade as long as progress is being made.
     */
    private synchronized int migrateSchemaInPlace()
        throws EMDException {
        DatabaseConverter dbc = null;       // Used if MigrateSchema 

        // This is how long we're willing to let the converter spin
        // without making progress.
        long timeout = MIGRATE_SCHEMA_STEP_TIMEOUT;

        // The DatabaseConverter converts a fixed number of rows each
        // time around. Keep calling it in a loop; each such call is
        // protected by the MasterService timeout "currentOpEndTime".
        // (In the future we may want to let the converter run to
        // completion in a separate thread, with the state machine
        // just waiting on an overall timeout. However the present
        // approach does have the advantage that if HADB access hangs,
        // the state machine will realise it much earlier.)

        dbc = DatabaseConverter.resumeConvert();

        try {
            currentOpEndTime = System.currentTimeMillis() + timeout;

            // A progress report shouldn't be printed each time
            // through this loop (or log rotation will probably wipe
            // out all other logs!), so keep track of rows and log a
            // report every MIGRATE_SCHEMA_REPORT_FREQUENCY rows or
            // MIGRATE_SCHEMA_REPORT_INTERVAL time.
            int reportRowCount = 0;
            long lastReportTime = 0;

            for (;;) {
                int nrows = dbc.runConvertStep();

                if (nrows == 0) {
                    // All done!
                    LOG.info("Schema upgraded in place.");
                    return 0;
                }

                if (nrows > 0) {
                    // Progress is being made, so extend the timeout.
                    long now = System.currentTimeMillis();
                    currentOpEndTime = now + timeout;

                    // Log progress report if required
                    reportRowCount += nrows;
                    long reportDuration = now - lastReportTime;
                    if (reportRowCount > MIGRATE_SCHEMA_REPORT_FREQUENCY ||
                            reportDuration > MIGRATE_SCHEMA_REPORT_INTERVAL) {
                        logTimeLeft(currentOpEndTime);
                        LOG.info("Converted " + reportRowCount + " rows.");
                        reportRowCount = 0;
                        lastReportTime = now;
                    }

                    if (LOG.isLoggable(Level.FINE))
                        LOG.fine("Converted " + nrows + " rows, continue....");
                }
                else
                    LOG.warning("No progress was made by the DB converter!");

                heartbeat();
            }
        }
        finally {
            currentOpEndTime = 0;
        }
    }

    /**
     * Detect if we need to do an upgrade, and if so, do one.
     *
     * Detection Mechanism:
     *   We compare the version of hadb that the database is running to the
     *   version pointed to by the symlink /opt/SUNWhadb/4.  If the ramdisk
     *   version is newer, we upgrade to it and unregister the old version.
     *
     * Version comparison and encoding is done in HadbVersion.java
     */
    private synchronized int doUpgrade() throws HADBException, IOException {

        File hadbRamdiskSymlink = new File(HADB_RAMDISK_SYMLINK);
        SoftwareBundle oldBundle =
            database.getDatabaseConfig().getSoftwareBundle();

        String oldBundleName = oldBundle.getName();
        if (oldBundleName.charAt(0)=='V') {
            oldBundleName = oldBundleName.substring(1);
        }
        HadbVersion curVersion = new HadbVersion(oldBundleName);
        HadbVersion newVersion = new HadbVersion(hadbRamdiskSymlink.getCanonicalPath());

        if (curVersion.compareTo(newVersion) >= 0) {
            LOG.info("Database upgrade done.");
            return 0;
        }

        // Register the new package

        SoftwareBundle newBundle;
        String pkgName = newVersion.toPackageNameString();
        String version = HADB_INSTALL_DIR + "/" +
            newVersion.toPathString();
        LOG.info("Upgrade: registering " + pkgName + " " + version);
        try {
            newBundle = domain.registerSoftwareBundle(pkgName, version);
        } catch (BundleAlreadyRegisteredException e) {
            newBundle = domain.getSoftwareBundle(pkgName);
        }

        // Start the upgrade of the database
        OperationMonitor monitor = database.upgradeDatabase(newBundle);

        // Wait for it to complete

        long now = System.currentTimeMillis();
        long timeout = now + UPGRADE_DATABASE_TIMEOUT;
        currentOpEndTime = timeout;

        while (now < timeout) {
            // Wait, and see if the monitor reports any progress
            boolean madeProgress = waitAndReportProgress(monitor, RETRY_SLEEP);
            now = System.currentTimeMillis();

            if (madeProgress) {
                // Extend the timeout
                timeout = now + UPGRADE_DATABASE_TIMEOUT;
                currentOpEndTime = timeout;
            }

            logProgress(monitor, timeout);

            // Check the monitor state
            OperationState curState = monitor.getOperationState();
            if (curState != OperationState.active) {
                // Not waiting any more
                currentOpEndTime = 0;

                if (curState == OperationState.completed) {
                    LOG.info("Database upgrade complete.");
                    return 0;
                } else {
                    LOG.severe("Database upgrade failed!");
                    ResourceBundle rs = BundleAccess.getInstance().getBundle();
                    String str = rs.getString("err.hadb.hadbm.dbupgradefail");
                    LOG.log(ExtLevel.EXT_SEVERE, str);
                    return -1;
                }
            }
        }

        // Timed out -- cancel the operation
        monitor.cancel();
        currentOpEndTime = 0;
      
        LOG.severe("Database upgrade timed out.");
        ResourceBundle rs = BundleAccess.getInstance().getBundle();
        String str =  rs.getString("err.hadb.hadbm.timeoutexpire");
        LOG.log(ExtLevel.EXT_SEVERE, str);
        return -1;
    }

    /**
     * Wait for the specified interval and return whether or not the
     * monitor made any progress. Note that it insists on waiting (at
     * least) the full interval, because progress should not be logged
     * more often than that.
     */
    private boolean waitAndReportProgress(OperationMonitor monitor,
                                          long interval)
            throws HADBException {
        long startTime = System.currentTimeMillis();

        boolean madeProgress = monitor.waitNewStatus(interval);

        if (madeProgress) {
            // It didn't wait for the whole interval, so wait
            // for the remainder
            long duration = System.currentTimeMillis() - startTime;
            if (duration < interval)
                try {
                    Thread.sleep(interval - duration);
                } catch (InterruptedException ignored) {}
        }

        heartbeat();
        return madeProgress;
    }

    /*
     * Periodically monitor the state of the database and try to 
     * recover node in the domain.
     * return 0 if database is ok, -1 in case of error.
     */
    private int periodicCheck() {

        // Maybe check isRunning between each phase here....

        List nodes = checkDBStateGetNodes();
        if (nodes == null)
            return -1;

        checkIfURLChanged();

        List newNodes = checkNodesGetNewNodes(nodes);

        return addNewNodes(nodes, newNodes);
    }

    private List checkDBStateGetNodes() {
        /*
         * get the list of nodes from the database.
         */
        List nodes = null;

        DatabaseState theState = DatabaseState.nonOperational;

        // Try all MAs until we get a database pointer
        for (int i = 0; i < nbNodes; i++) {
            if (database != null) {
                try {
                    LOG.fine("Trying to get the nodes in the domain...");
                    nodes = database.getNodes();
                    theState = database.getDatabaseState();
                    break;
                }
                catch (HADBException e) {
                    LOG.log(Level.WARNING, "Failed to get database nodes", e);
                }
            }
            connectToNextMA();
        }

        if (nodes == null) {
            LOG.severe("No nodes in the domain!");
            return null;
        }

        if (!databaseStateOK(theState)) {
            long now = System.currentTimeMillis();

            // If this is the first time, start the timer but return OK
            if (nonOperationalTime == 0) {
                LOG.warning("HADB is non-operational; starting timer...");
                nonOperationalTime = now;
                return nodes;
            }

            // Timeout: wipe immediately
            if (now - nonOperationalTime > NONOPERATIONAL_TIMEOUT) {
                LOG.severe("HADB has been non-operational for " +
                           NONOPERATIONAL_TIMEOUT / 1000 + "s; wiping HADB.");
                ResourceBundle rs = BundleAccess.getInstance().getBundle();
                String str = rs.getString("err.hadb.hadbm.hadbnonoperational");
                Object [] args = {new Long(NONOPERATIONAL_TIMEOUT / 1000)};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));
                numFailures = numFailuresTolerated;
                return null;
            }
 
            LOG.finer("HADB is still non-operational.");
            return nodes;
        }

        // HADB's OK. If it used to be bad, reset and log an info message.

        if (nonOperationalTime > 0) {
            LOG.info("Database was non-operational, but now it's OK.");
            nonOperationalTime = 0;
        }

        return nodes;
    }

    private void checkIfURLChanged() {
        try {
            String theNewUrl = database.getJdbcUrl();
            if (theNewUrl != null && theNewUrl.compareTo(jdbcUrl) !=0 ) {
                LOG.info("Found new JDBC URL " + theNewUrl);
                jdbcUrl = theNewUrl;
            }
        }
        catch (HADBException e) {
            LOG.info("Not updating JDBC URL (" + e + ")");
        }
    }

    private List checkNodesGetNewNodes(List nodes) {
        // See if the cluster size has changed
        checkIfNodesAdded();

        /*
         * Check if some new nodes need to be wiped and integrated
         * into the domain.
         *
         * FIXME - we need to be smarter here and recover reliability
         * of HADB by removing dead nodes from the domain.
         *
         * WARNING: nodes.get(i) isn't necessarily 101+i
         */

        boolean isNodeInDomain[] = new boolean[nbNodes];
        for (int i = 0; i < nbNodes; i++) {
            isNodeInDomain[i] = false;
        }

        Set deadNodes = new HashSet();

        for (int i = 0; i < nodes.size(); i++) {
            heartbeat();        // We're still alive

            com.sun.hadb.adminapi.Node cur =
                (com.sun.hadb.adminapi.Node) nodes.get(i);

            String nodeName = cur.getDomainMember().getHostName();

            // WARNING: the array "nodeDeathTimes" is indexed by the
            // ordering imposed by HADB -- in this case, "i" (cf.
            // the array "isNodeInDomain" indexed by Honeycomb order)

            try {
                int nodeID = getNodeIdFromName(nodeName);
                isNodeInDomain[nodeID - FIRST_NODE] = true;
                NodeState nodeState = cur.getNodeState();

                long now = System.currentTimeMillis();

                // Start checks on this node
                
                if (nodeDetectedBadDisk(nodeID)) {
                    // when disk problems have been detected, it is the
                    // repsonsbility of the HadbService to take corrective
                    // action, best thing we can do is to stay out of the way
                    LOG.log(Level.INFO, "Node " + nodeID + " is moving " +
                                "HADB to a new disk.");
                    continue;
                }

                // If there's no MA on the node, we can't get its
                // status; if it doesn't come back before a timer
                // expires, assume it's dead and disable it.
                if (!isRemoteMARunning(nodeID)) {

                    if (LOG.isLoggable(Level.FINE))
                        LOG.fine("Node " + nodeName + " is down.");

                    if (nodeDeathTimes[i] == 0) {
                        LOG.warning("Couldn't contact " + nodeName +
                                    "; starting timer.");
                        nodeDeathTimes[i] = now;
                    }
                    else if (nodeDeathTimes[i] > 0 &&
                             (now - nodeDeathTimes[i]) > NODE_DIED_TIMEOUT) {
                        LOG.severe("No MA; declaring " + nodeName + " dead.");
                        // Add this node to the toBeDisabled list
                        deadNodes.add(nodeName);

                        // This makes sure we won't try to keep disabling it
                        // -- since even though the node has been disabled,
                        // HADB still reports it as part of the domain.
                        nodeDeathTimes[i] = -1;
                    }

                    continue;
                }

                if (nodeState == NodeState.running) {
                    Level l = Level.FINE;
                    if (nodeDeathTimes[i] > 0)
                        l = Level.INFO;
                    LOG.log(l, "Node " + nodeName + " OK (" + nodeState + ")");

                    // Paranoia: ensure that the time of death is not set
                    nodeDeathTimes[i] = 0;
                    continue;
                }

                // If we got here, the node is either stopped or in
                // some other non-running state. Start a timer; if the
                // node doesn't come back before the timer expires, we
                // have to reset it.
                //
                // If a node stays "stopped", we start it; but if it
                // stays anything else, we wipe and re-start it.
                //
                // In past versions of HADB, a stopped node was always
                // good and could be cleanly re-started. This is no
                // longer the case. If the re-start of a stopped node
                // fails, give up: wipe and restart it.

                // Should this be a more intelligent test? What about
                // node states backingup recovering repairing
                // restoring waiting ...
                boolean stopped = nodeState == NodeState.stopped;

                long timeoutValue =
                    stopped? nodeStoppedTimeout : nodeWipeTimeout;

                if (nodeDeathTimes[i] <= 0) {
                    StringBuffer msg = new StringBuffer();
                    msg.append("Node ").append(nodeName).append(" state ");
                    msg.append("\"").append(nodeState.toString());
                    msg.append("\" but MA is running; starting ");
                    msg.append(timeoutValue/1000).append("s timer...");

                    LOG.warning(msg.toString());
                    nodeDeathTimes[i] = now;
                }
                else if ((now - nodeDeathTimes[i]) > timeoutValue) {
                    // It's time to try to re-start this node

                    if (stopped) {
                        //XXX this blocks until the node is started.
                        //In the future, make it async and keep doing checks.

                        LOG.warning("Timer expired; starting " + nodeName);

                        if (startOneNode(cur)) {
                            // Success! Reset the timer and move on to
                            // the next node.
                            nodeDeathTimes[i] = 0;
                            continue;
                        }
                        // If startOneNode failed, then we fall
                        // through to wipeAndRestartNode. Optionally,
                        // we could allow one or two re-tries of
                        // startOneNode before wiping the node. (TBD.)
                        LOG.warning("Couldn't start " + nodeName +
                                    "; wiping the node.");
                    }
                    else
                        LOG.warning("Timer expired; wiping " + nodeName);

                    nodeDeathTimes[i] = 0;
                    wipeAndRestartNode(nodeID);
                    recoverHost(nodeID);
                }
                else if (LOG.isLoggable(Level.FINE)) {
                    StringBuffer msg = new StringBuffer();
                    msg.append("Node ").append(nodeName);
                    msg.append(" is in state ");
                    msg.append("\"").append(nodeState.toString());
                    msg.append("\"");
                    LOG.fine(msg.toString());
                }
            }
            catch (LostConnectionException lce) {
                LOG.log(Level.WARNING,
                        "MAConnection was lost while monitoring node " +
                        nodeName + ". Attempting to get new connection.");
                connectToNextMA();
            }
            catch (HADBException e) {
                LOG.log(Level.WARNING,
                        "Failed to monitor \"" + nodeName + "\"", e);
            }
            catch (StateMachineException e) {
                LOG.log(Level.WARNING,
                        "Failed to restart \"" + nodeName + "\"", e);
                // Now we should get rid of this node (or maybe try to
                // wipe it) since we can't re-start. XXX TODO
            }
        }

        heartbeat();

        // Disable dead nodes
        if (deadNodes.size() > 0) {
            if (!disableHosts(deadNodes)) {
                LOG.warning("Fallback: disable nodes HADB thinks are dead");
                if (!disableHosts(getDeadNodes()))
                    LOG.severe("Couldn't disable dead nodes!");
            }
        }

        //XXX todo: what if the nodes need to be wiped?
        //XXX todo: what if the directory of the db on the nodes to be
        //added isn't the default one?

        ArrayList list = new ArrayList();

        for(int i = 0; i < nbNodes; i++) {
            int nodeID = FIRST_NODE + i;

            if (!isNodeInDomain[i] && isRemoteMARunning(nodeID)) {
                // This node is ready to be added.
                try {
                    list.add(InetAddress.getByName("hcb" + nodeID));
                }
                catch (UnknownHostException e) {
                    // Eh? Log error and ignore this one.
                    String error = "Unknown host \"hcb" + nodeID +
                        "\" defined by CM!";
                    LOG.log(Level.SEVERE, error, e);
                }
            }
        }

        int count = list.size();
        if ((count % 2) != 0) {
            // We need an even number of nodes to add to the domain.
            // If we have an odd number of nodes, remove the last one.
            list.remove(count - 1);
            count -= 1;
        }

        return list;
    }

    /**
     * Hack: parse node ID from hostname, assuming the last three
     * characters are the ID.
     */
    private int getNodeIdFromName(String name) {
        try {
            int l = name.length();
            return Integer.parseInt(name.substring(l-3, l));
        }
        catch (Throwable e) {
            LOG.log(Level.WARNING, "Couldn't parse " + StringUtil.image(name),
                    e);
            return -1;
        }
    }

    private synchronized int addNewNodes(List nodes, List list) {
        int count = list.size();
        if (count == 0)
            return 0;

        StringBuffer nodeList = new StringBuffer();
        String delim = "";
        InetAddress[] addrs = new InetAddress[count];
        for (int i = 0; i < addrs.length; i++) {
            addrs[i] = (InetAddress) list.get(i);
            nodeList.append(delim).append(addrs[i]);
            delim = ", ";
        }
        LOG.info("Adding " + count + " nodes {" + nodeList + "} to domain.");

        try {
            try {
                domain.addMembers(addrs);
                heartbeat();
            }
            catch (MAInOtherDomainException e) {
                String badHost = e.getHostName();
                LOG.info("Node(s) " + badHost +
                         " used to be in another domain, wipe and re-start.");

                String[] failedHosts = badHost.split(",");

                int nodeID = 0;
                int h = 0;
                // For some reason, if 108 is bad then e.getHostName()
                // will be of the form "10.123.45.108, 10.123.45.101"
                // -- I don't know why. I'm assuming that everything
                // up to that first comma is the host that failed.
                // Should we really be looping over all entries
                // instead?  [SPM]
                // for (h = 0; h < failedHosts.length; h++)
                    try {
                        nodeID = getNodeIdFromName(failedHosts[h].trim());
                        wipeAndRestartNode(nodeID);
                        LOG.info("Node " + nodeID + " wiped.");
                    }
                    catch (StateMachineException e2) {
                        LOG.log(Level.WARNING, "Couldn't wipe " + nodeID, e2);
                    }

                return 0;
            }

            LOG.info("All nodes added to the domain.");

            DatabaseConfig config = database.getDatabaseConfig();
            for(int i = 0; i < count; i++) {
                InetAddress myaddr[] = new InetAddress[1];
                myaddr[0] = addrs[i];
                config.addNode(myaddr);
            }

            Integer numSparesToUse = null;
            if (nodes.size() + count < 8) {
                numSparesToUse = new Integer(0); 
            }
            else if (nodes.size() + count < 16) {
                numSparesToUse = new Integer(2);
            }
            else {
                numSparesToUse = new Integer(4);
            }
            config.setAttributeValue("NumberOfSpares", numSparesToUse);
            LOG.info("Now using " + numSparesToUse + " spares.");

            LOG.info("Trying to reconfigure database....");
            setDatabaseConfig(config, RECONFIGURE_DATABASE_TIMEOUT);

            //XXX do something here? fail? check db status?

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Couldn't add nodes to the domain", e);
        }

        // What should the return code be? This is the addition of
        // nodes to the domain; if it failed now, it could succeed in
        // the future. In the meantime, the database is functional so
        // return success.
        return 0;
    }
    
    private synchronized boolean startOneNode(com.sun.hadb.adminapi.Node node) {
        String nodeName = node.getDomainMember().getHostName();
        int retries = nbNodes;
        while (retries-- > 0) {
            try {
                LOG.info("Trying to start node " + nodeName);

                OperationMonitor monitor = node.start();
                long timeout = System.currentTimeMillis() + startNodeTimeout;
                currentOpEndTime = timeout;

                while (timeout > System.currentTimeMillis() && isRunning) {
                    logProgress(monitor, timeout);

                    if (monitor.getOperationState() == OperationState.completed) {
                        currentOpEndTime = 0;
                        return true;
                    }

                    sleeper();
                }

                // Timed out
                monitor.cancel();
                currentOpEndTime = 0;
                break;
            } 
            catch (LostConnectionException lce) {
                LOG.log(Level.WARNING,"MA Connection was lost during start " +
                                "operation for " + nodeName, lce);
                if (retries > 0) {
                    LOG.log(Level.WARNING, "Attempting to establish new " +
                                "connection and retry start operation. " + 
                                retries + " retries left.");
                    connectToNextMA();
                } else {
                    LOG.log(Level.WARNING, "No more connection retries left.");
                }
            }
            catch (HADBException e) {
                LOG.log(Level.WARNING, "Failed to start node " + nodeName, e);
                break;
            }
        }

        currentOpEndTime = 0;
        return false;
    }

    /** Get the HADB dir and set hadbPasswdFile from it */
    private void getHadbPath() throws IOException {
        hadbPasswdFile = HadbService.getPathFromConfig() + "/hadb_passwd";
    }

    /*
     * FIXME - Is this phase still needed ?
     */
    private int setupPasswdFile() {
        String passwdLine = "HADBM_ADMINPASSWORD=" + PWD_ADMIN + "\n";

        try {
            File passwdFile = new File(hadbPasswdFile);
            if (passwdFile.exists()) {
                passwdFile.delete();
            }

            FileOutputStream output = new FileOutputStream(passwdFile);
            output.write(passwdLine.getBytes());
            output.close();

            LOG.info("Password file " + StringUtil.image(passwdFile) +
                     " created.");

            return 0;
        }
        catch (IOException e) {
            LOG.log(Level.SEVERE, "Couldn't setup password file!", e);
            return -1;
        }
    }

    /*
     * TODO: This method is never called. RM it
     * Try to recover the Database -
     * If we reach this phase we are in trouble. The database was probably 
     * not shutdown correctly or we hit bugs in HADB startup and there is 
     * very little chance that we can recover anything.
     * The trade off is between clearing the db now (and loosing all data) or
     * letting the master service timeout to wipe and restart everything.
     *
     * TBD - ask the HADB guys how to recover a consistent state.
     * In our case, it is better to restart from a old and valid state 
     * than restart from scratch. Restarting specific MA servers can be
     * a valid option.
     */
    private int recoverDatabase() {
        // we don't have HADB anymore.
        jdbcUrl = null;
        
        if (database != null) {
            // We have the database handle. Let's see if we can restart
            // stuck MA servers
            LOG.info("Trying to recover database...");

            try {
                List nodes = database.getNodes();
                for (int i = 0; i < nodes.size(); i++) {
                    com.sun.hadb.adminapi.Node cur;
                    cur = (com.sun.hadb.adminapi.Node) nodes.get(i);
                    if (cur.getNodeState() != NodeState.stopped &&
                        cur.getNodeState() != NodeState.running) {

                        LOG.info("Trying to re-start node " + (FIRST_NODE + i));
                        restartMA(FIRST_NODE + i);
                    }
                }
            }
            catch (HADBException e) {
                LOG.log(Level.WARNING, "Failed to recover DB", e);
            }

            return 0;
        }
        
        // We don't have the domain yet.  Let's see if we can restart
        // MA servers that are stuck in the "not ready" state.
        LOG.info("Trying to recover domain...");

        for (int i = 0; i < nbNodes; i++) {
            try {
                if (conn != null) {
                    domain = conn.getDomain();
                }
            }
            catch (MANotReadyException me) {
                LOG.info("Restarting MA " + nextMA);
                restartMA(FIRST_NODE + nextMA);
                break; // one at a time.
            }
            catch (HADBException e) {
                LOG.log(Level.WARNING, "Failed to recover domain", e);
            }

            connectToNextMA();
        }

        LOG.info("Database recovery complete."); // But did it really succeed?
        return 0;
    }
    
    /*****************************************************
     * private methods
     *****************************************************/

    private Hadbm() {

        state = HadbState.START;
        isRunning = false;
        nextMA = 0;

        // Initialize number of nodes in the cluster. NOTE: If a
        // global depends on nbnodes to be inited, please put it in
        // initNumNodes().
        initNumNodes();
        
        loadHadbProperties();

        NodeMgrService.Proxy nodeMgr =
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        hcNodes = nodeMgr.getNodes();
        int nodeid = nodeMgr.nodeId();

        assert(nbNodes <= hcNodes.length);
        
        StringBuffer sb = new StringBuffer();
        String delim = "";

        for (int i = 0; i < nbNodes; i++) {

            // The first MA we contact is the one on the local node
            if (hcNodes[i].nodeId() == nodeid)
                nextMA = i;

            sb.append(delim).append(hcNodes[i].getName());
            delim = ", ";
        }

        heartbeat();

        LOG.info("HADB nodes: {" + sb + "} (" + nbNodes +
                 "); MAX_MA_LOOPS = " + MAX_MA_LOOPS);

        restoreLastCreateTime();        // Read lastCreateTime from config
    }
    
    /**
     * Initialize the number of nodes in the cell and updates nbNodes.
     * NOTE: Put all initialization of globals that depend on nbnodes here.
     */
    private void initNumNodes() {
        int localNumNodes = getConfigNumNodes();

        nodeDeathTimes = new long[localNumNodes];
        for(int i = 0; i < localNumNodes; i++) {
            nodeDeathTimes[i] = 0;
        }
        nbNodes = localNumNodes;
    }

    /**
     * Checks to see if the number of nodes in the cell has been changed
     * since the last time we checked (and updates nbNodes if so).
     *
     * @return true if any nodes were added to the domain.
     */
    private boolean checkIfNodesAdded() {
        int newNumNodes = getConfigNumNodes();

        if (newNumNodes == nbNodes)
            // No change
            return false;

        LOG.info("HADB state machine: cluster expansion to " + newNumNodes);

        // Re-size the nodeDeathTimes array

        long tempDeathTimes[] = new long[newNumNodes];
        for (int i = 0; i < newNumNodes; i++) {
            if (i < nodeDeathTimes.length)
                tempDeathTimes[i] = nodeDeathTimes[i];
            else
                tempDeathTimes[i] = 0;
        }

        nodeDeathTimes = tempDeathTimes;
        nbNodes = newNumNodes;

        return true;
    }

    private int getConfigNumNodes() {
        ClusterProperties clusterConf =  ClusterProperties.getInstance();

        String value = clusterConf.getProperty(ConfigPropertyNames.PROP_NUMNODES);
        if (value == null) {
            String err = "Failed to get the number of configured nodes.";
            throw new RuntimeException(err);
        }
        
        int numNodes = Integer.parseInt(value);
        assert ((numNodes > 0) && (numNodes <= 16));

        if (numNodes != 8 && numNodes != 16) {
            LOG.warning("Config says number of nodes is " + numNodes +
                        ", but we only know how to handle 8 and 16.");
        }

        //TODO: [EDL] This should be moved into the loadHadbProperties
        //method. 
        String prop =
            clusterConf.getProperty(ConfigPropertyNames.HADBM_MAX_MA_LOOPS);
        try {
            int oldValue = MAX_MA_LOOPS;
            if (prop != null)
                MAX_MA_LOOPS = Integer.parseInt(prop);
            if (MAX_MA_LOOPS != oldValue)
                LOG.info("Set MAX_MA_LOOPS to " + MAX_MA_LOOPS);
        }
        catch (NumberFormatException e) {
            LOG.warning("Couldn't parse " + StringUtil.image(prop) +
                        "; value of MAX_MA_LOOPS is still " + MAX_MA_LOOPS);
        }

        return numNodes;
    }
    
    private void loadHadbProperties () {
	ClusterProperties clusterConf =  ClusterProperties.getInstance();
	
	nodeWipeTimeout = clusterConf.getPropertyAsLong(
		ConfigPropertyNames.PROP_HADBM_WIPE_NODE_TIMEOUT,
		DEFAULT_NODE_WIPE_TIMEOUT_SECS) * 1000;

	nodeStoppedTimeout = clusterConf.getPropertyAsLong(
		ConfigPropertyNames.PROP_HADBM_NODE_IS_STOPPED_TIMEOUT,
		DEFAULT_STOPPED_NODE_TIMEOUT_SECS) * 1000;
	
	startNodeTimeout = clusterConf.getPropertyAsLong(
		ConfigPropertyNames.PROP_HADBM_NODE_START_TIMEOUT,
		DEFAULT_START_NODE_TIMEOUT_SECS) * 1000;
	
	if (LOG.isLoggable(Level.FINE)) {
	    LOG.fine(ConfigPropertyNames.PROP_HADBM_WIPE_NODE_TIMEOUT + 
		    " = " + nodeWipeTimeout);
	    LOG.fine(ConfigPropertyNames.PROP_HADBM_NODE_IS_STOPPED_TIMEOUT + 
		    " = " + nodeStoppedTimeout);
	    LOG.fine(ConfigPropertyNames.PROP_HADBM_NODE_START_TIMEOUT + " = " +
		    startNodeTimeout);
		
	}
	clusterConf.addPropertyListener(this);
	
    }
    
    public void propertyChange(PropertyChangeEvent event) {
        String propName = event.getPropertyName();
        ClusterProperties clusterConf =  ClusterProperties.getInstance();
        if (propName.equals(ConfigPropertyNames.PROP_HADBM_WIPE_NODE_TIMEOUT)) {
            nodeWipeTimeout = clusterConf.getPropertyAsLong(
    		ConfigPropertyNames.PROP_HADBM_WIPE_NODE_TIMEOUT) * 1000;
            if (LOG.isLoggable(Level.FINE)) {
        	LOG.fine(ConfigPropertyNames.PROP_HADBM_WIPE_NODE_TIMEOUT + 
    		    " = " + nodeWipeTimeout);
            }
        } else if (propName.equals
        	(ConfigPropertyNames.PROP_HADBM_NODE_IS_STOPPED_TIMEOUT)) {
            nodeStoppedTimeout = clusterConf.getPropertyAsLong(
        		ConfigPropertyNames.PROP_HADBM_NODE_IS_STOPPED_TIMEOUT)
        		* 1000;
            if (LOG.isLoggable(Level.FINE)) {
            	LOG.fine(ConfigPropertyNames.PROP_HADBM_NODE_IS_STOPPED_TIMEOUT + 
        		    " = " + nodeStoppedTimeout);
            }            
        } else if (propName.equals
        	(ConfigPropertyNames.PROP_HADBM_NODE_START_TIMEOUT)) {
            startNodeTimeout = clusterConf.getPropertyAsLong(
        	    ConfigPropertyNames.PROP_HADBM_NODE_START_TIMEOUT) * 1000;
             if (LOG.isLoggable(Level.FINE)) {
        	LOG.fine(ConfigPropertyNames.PROP_HADBM_NODE_START_TIMEOUT + 
    		    " = " + startNodeTimeout);
             }
	}
        
    } 

    /*
     * Connect to a different MA server - 
     * This method is called when the current operation fails or does not
     * progress with the connected MA server. This workaround fixes isssues in 
     * HADB where a given MA server get stuck (in unknown state most of the
     * time) or keeps failing. It is usually possible to find a node on which
     * the state machine can progress.
     * Return true if hadbm is connected to a new server, false otherwise.
     */
    private boolean connectToNextMA() {
        hcNodes = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).getNodes();

        // First, get a live node to try. Do not try any nodes that CM
        // says are dead.
        do {
            nextMA = (nextMA + 1) % nbNodes;
        } while (!hcNodes[nextMA].isAlive());

        heartbeat();

        String host = hcNodes[nextMA].getName() + ":" + HADB_PORT;
        MAConnection nextConn = null;

        LOG.info("Trying to connect to MA " + nextMA + " (" + host  + ")");


        try {
            nextConn = MAConnectionFactory.connect(host, PWD_ADMIN, PWD_ADMIN);
            if (nextConn == null) {
                LOG.warning("Failed to connect to MA server at " + host);
                return false;
            }

            if (state.isNotPast(HadbState.GET_DOMAIN)) {
                // Nothing's been accomplished yet, so this connection
                // is fine.
                domain = null;
                database = null;
                return startUsingConnection(nextConn, host);
            }

            if (state.isNotPast(HadbState.GET_DB)) {
                // We can use this connection if we can get the domain
                // from it.
                ManagementDomain nextDomain = nextConn.getDomain();
                if (nextDomain != null) {
                    domain = nextDomain;
                    database = null;
                    return startUsingConnection(nextConn, host);
                }
            }
            else {
                // Apparently we already have a DB. If we can get the
                // domain and the db, we can use the connection.
                ManagementDomain nextDomain = nextConn.getDomain();
                if (nextDomain != null) {
                    Database nextDb = nextDomain.getDatabase(HC_DB_NAME);
                    if (nextDb != null) {
                        domain = nextDomain;
                        database = nextDb;
                        return startUsingConnection(nextConn, host);
                    }
                }
            }

            // That MA is no good: close the connection, we're done here.
            nextConn.close();   // (bug 6550417)
            
        } catch (HADBException e) {
            LOG.log(Level.INFO, "Trying to connect to MA on " + host, e);
            if (nextConn != null) {
                nextConn.close();
            }
        }
        
        LOG.warning("Failed to connect to MA server at " + host);
        return false;
    }

    /** Make newConn the new connection to use; host is for logging only */
    private boolean startUsingConnection(MAConnection newConn, String host) {
        if (conn != null)
            conn.close();

        conn = newConn;

        LOG.info("Connected to MA " + nextMA + " (" + host  + ")");
        return true;
    }
    
    /*
     * return appropriate database configuration.
     */
    private DatabaseConfig getDbConfig() throws HADBException {
        assert(domain != null);
        int numHosts = 0;
        
        SoftwareBundle bundle = domain.getDefaultSoftwareBundle();
        DatabaseConfig config = bundle.createDatabaseModel(HC_DB_NAME);
        
        InetAddress hosts[] = new InetAddress[1];
        Iterator it = domain.getDomainMembers().iterator();
        while (it.hasNext()) {
            DomainMember member = (DomainMember) it.next();
            String name = member.getHostName();

            try {
                hosts[0] = InetAddress.getByName(name);
                config.addNode(hosts);
                numHosts++;
            }
            catch (UnknownHostException e) {
                String error = "Unknown host defined by the domain: " + name;
                LOG.severe(error);
                throw new RuntimeException(error);
            }
        }
        
        ClusterProperties clusterConf =  ClusterProperties.getInstance();

        // Default size: 8 GB
        long size =
            clusterConf.getPropertyAsLong(ConfigPropertyNames.HADB_DATABASE_SIZE_PROPERTY,
                                          8192);
        config.setAttributeValue("DataDeviceSize", new Long(size * MB));

        // Default relalg: 2 GB.
        size =
            clusterConf.getPropertyAsLong(ConfigPropertyNames.HADB_DATABASE_RELALG_PROPERTY,
                                          2048);
        config.setAttributeValue("RelalgDeviceSize", new Long(size * MB));

        Integer numSparesToUse = null;
        if (numHosts < 8) {
            numSparesToUse = new Integer(0);
        }
        else if (numHosts < 16) {
            numSparesToUse = new Integer(2);
        }
        else {
            numSparesToUse = new Integer(4);
        }
        config.setAttributeValue("NumberOfSpares", numSparesToUse);

        if (LOG.isLoggable(Level.INFO)) {
            StringBuffer sb = new StringBuffer("HADB config:");

            sb.append(" DataDeviceSize=");
            sb.append(config.getAttributeValue("DataDeviceSize").toString());

            sb.append(" RelalgDeviceSize=");
            sb.append(config.getAttributeValue("RelalgDeviceSize").toString());

            sb.append(" NumberOfSpares=");
            sb.append(config.getAttributeValue("NumberOfSpares").toString());

            LOG.info(sb.toString());
        }

        return config;
    }
    
    /*
     * return appropriate domain configuration
     */
    private InetAddress[] getDomainConfig(HashSet badHosts) {
        hcNodes = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).getNodes();

        ArrayList list = new ArrayList();
        for (int i = 0; i < nbNodes; i++) {
            String nodeName = hcNodes[i].getName();
            String nodeID = Integer.toString(getNodeIdFromName(nodeName));

            if (!hcNodes[i].isAlive() || badHosts.contains(nodeID)) {
                LOG.warning("Not using " + nodeName);
                continue;
            }

            try {
                list.add(InetAddress.getByName(nodeName));
            }
            catch (UnknownHostException e) {
                LOG.severe("Unknown cluster node: " + StringUtil.image(nodeName));
            }
        }

        int count = list.size();
        if ((count % 2) != 0) {
            // we don't have an even number of nodes and
            // need to create a reduced domain.
            count -= 1;
        }

        StringBuffer msg = new StringBuffer();
        String delim = "";
        InetAddress[] addrs = new InetAddress[count];
        for (int i = 0; i < addrs.length; i++) {
            addrs[i]  = (InetAddress) list.get(i);
            msg.append(delim).append(addrs[i]);
            delim = ",";
        }

        if (LOG.isLoggable(Level.INFO)) {
            StringBuffer sb = new StringBuffer();
            delim = "";
            for (Iterator hIter = badHosts.iterator(); hIter.hasNext(); ) {
                sb.append(hIter.next().toString()).append(delim);
                delim = ",";
            }
            LOG.info("Domain: {" + msg + "} (badHosts: {" + sb + "})");
        }

        return addrs;
    }
    
    /*
     * Log progress on given operation, including the time remaining
     * before the timeout expires.
     */
    private void logProgress(OperationMonitor mon, long endTime) {

        heartbeat();

        long remaining = (endTime - System.currentTimeMillis()) / 1000;

        StringBuffer msg = new StringBuffer("In ");

        try {
            // Attach the name of the calling method. Element 0 in the
            // stack trace is this method, so element 1 is the caller.
            // If this voodoo fails for any reason, do nothing; log as
            // much as is reasonable.
            String caller =
                (new Throwable()).getStackTrace()[1].getMethodName();

            if (caller != null)
                msg.append(caller).append(", ");
        } catch (Throwable ignored) {}

        msg.append("state ").append(toString()).append(": ");

        if (mon != null)
            try {
                msg.append(mon.getProgressMessage());
                msg.append(".. (").append(mon.getProgressEstimate());
                msg.append("% complete, ");
                msg.append(mon.getOperationState().toString());
                msg.append(")");

                if (mon.getOperationState() == OperationState.completed)
                    msg.append(".");
                else
                    msg.append("; ").append(remaining).append("s left.");
            }
            catch (Throwable e) {
                LOG.log(Level.SEVERE, "Unexpected exception", e);
                msg.append("Unexpected exception \"" + e + "\"");
            }
        else
            msg.append(remaining).append("s left.");

        LOG.info(msg.toString());
    }

    /*
     * Log the time left for the current phase.
     */
    private void logTimeLeft(long endTime) {
        logProgress(null, endTime);
    }

    /*
     * sleep before retrying.
     */
    private void sleeper() {
        heartbeat();
        try {
            Thread.currentThread().sleep(RETRY_SLEEP);
        } catch (InterruptedException ignored) {
        }
    }

    /*
     * heartbeat -
     * The state machine thread must heartbeat within the HEARTBEAT_TIMEOUT
     * window or it is interrupted.
     */
    private void heartbeat() {
        heartbeatTime = System.currentTimeMillis();
    }

    /**
     * Get HADB's idea of what nodes are dead.
     *
     * @return a Set with the names of the dead hosts.
     */
    private synchronized Set getDeadNodes() {
        StringBuffer sb = new StringBuffer();
        String delim = "";

        Set members = null;
        try {
            members = domain.getDomainMembers();
        }
        // Possibly catch MANotRunningException and MAException separately?
        catch (HADBException e) {
            LOG.log(Level.SEVERE, "Couldn't get dead hosts", e);
        }

        Set badHosts = new HashSet();

        for (Iterator m = members.iterator(); m.hasNext(); ) {
            DomainMember host = (DomainMember) m.next();
            if (!host.isRunning()) {
                badHosts.add(host.getHostName());

                sb.append(delim).append(StringUtil.image(host.toString()));
                delim = ", ";
            }
        }

        LOG.info("Dead nodes: {" + sb + "}");

        if (badHosts.size() > 0)
            return badHosts;

        return null;
    }

    /**
     * Disable all the specified hosts.
     *
     * @param badHosts a Set of String hostnames
     * @return whether or not the operation succeeded.
     */
    private synchronized boolean disableHosts(Set badHosts) {
        if (badHosts == null || badHosts.size() == 0) {
            LOG.info("No bad hosts, nothing to disable!");
            return true;
        }

        String hostName = null;

        StringBuffer sb = new StringBuffer();
        String delim = "";

        Set addrs = new HashSet();

        // Do name lookups on all hosts
        for (Iterator h = badHosts.iterator(); h.hasNext(); )
            try {
                hostName = (String) h.next();
                addrs.add(InetAddress.getByName(hostName));

                sb.append(delim).append(StringUtil.image(hostName));
                delim = ", ";
            }
            catch (UnknownHostException e) {
                LOG.severe("Unknown host " + StringUtil.image(hostName));
            }

        // Re-try the disableMembers() call if necessary

        for (int i = 0; i <= NUM_RETRIES; i++) {
            try {
                LOG.info("Trying to disable {" + sb + "}....");
                conn.disableMembers(addrs);
                LOG.info("Nodes {" + sb + "} disabled.");
                return true;
            }
            catch (LostConnectionException lce) {
                LOG.log(Level.WARNING,"MA Connection lost while disabling " +
                                "host(s).",lce);
                if (i <= NUM_RETRIES){
                    LOG.log(Level.INFO,"Will attempt to obtain new connection " +
                                "and retry disable operation");
                    connectToNextMA();
                }
            }
            catch (MemberAlreadyDisabledException ignored) {
                LOG.info("Node(s) already disabled; assuming all is well.");
                return true;
            }
            catch (HADBException e) {
                String msg = "Couldn't disable hosts {" + sb + "}, re-try...";
                LOG.log(Level.WARNING, msg, e);
            }

            sleeper();
        }

        LOG.severe("Failed to disable even after re-tries: {" + sb + "}");
        return false;
    }

    /*
     * stop the database. 
     * We try very hard to stop the database b/c if we fail there is no
     * way to recover (double failure).
     * Keep trying until it succeed or timeout
     * return 0 in case of success, -1 otherwise.
     */
    private synchronized int stopDb() {
        long timeout = System.currentTimeMillis() + STOP_DATABASE_TIMEOUT;
        currentOpEndTime = timeout;

        state = HadbState.RUNNING; // assume we were running
        // In the name of all that's holy! Why is that a good assumption?

        OperationMonitor monitor = null;
        do {
            try {
                if (monitor != null) {
                    logProgress(monitor, timeout);

                    // Note: waitUntilCompleted will return only if
                    // the timeout period passed with no progress!
                    // Instead, check monitor.getOperationState() to
                    // see if it's finished. This way _we_ can report
                    // progress.
                    if (monitor.getOperationState() == OperationState.completed) {
                        LOG.info("HADB stopped normally. (MA " + nextMA + ")");
                        currentOpEndTime = 0;
                        return 0;
                    }

                    sleeper();

                } else if (database != null) {
                    DatabaseState theState = database.getDatabaseState();
                    if (theState != DatabaseState.stopped) {
                        LOG.info("Attempting to stop database...");
                        monitor = database.stop();
                        
                    } else {
                        LOG.info("HADB stopped normally (MA " + nextMA + ")");
                        currentOpEndTime = 0;
                        return 0;
                    }
                } else {
                    sleeper();
                    connectToNextMA();
                }
                
            } catch (MANotRunningException e) {
                // This means one (or more) of the nodes isn't running,
                // so disable it.
                monitor = null;

                if (!disableHosts(getDeadNodes())) {
                    LOG.info("Trying to stop DB with a new host...");
                    //FIXME - do the same as below
                    sleeper();
                    connectToNextMA();
                }
            } catch (HADBException e) {
                LOG.log(Level.WARNING, "Failed to stop database!", e);
                // FIXME -
                // HADB does not want to stop the database if the domain
                // is not valid (.i.e some nodes are missing).
                // We need to handle this case here and recover the domain
                // in order to stop correctly HADB
                monitor = null;
                sleeper();
                connectToNextMA();
            }

        } while (timeout > System.currentTimeMillis());
        currentOpEndTime = 0;
        
        if (monitor != null) {
            logProgress(monitor, timeout);

            try {
                if (monitor.getOperationState() == OperationState.completed) {
                    LOG.info("HADB stopped normally. (MA " + nextMA + ")");
                    return 0;
                }                
                monitor.cancel();
            } catch (HADBException ignore) {}
        }      

        LOG.severe("Failed to stop database!");
        ResourceBundle rs = BundleAccess.getInstance().getBundle();
        String str = rs.getString("err.hadb.hadbm.failedstop");
        LOG.log(ExtLevel.EXT_SEVERE, str);
        return -1;
    }

    private void wipeAndRestartNode(int nodeid)
            throws StateMachineException {

        HADBJdbc.resetInstance();
        AttributeTable.resetSchema();

        // Get the API to the HadbService on the node
        HADBServiceInterface api = getRemoteService(nodeid);
        if (api == null) {
            LOG.warning("Cannot wipe and restart node " + nodeid);
            return;
        }

        // Wipe the node

        try {
            api.wipeAndRestartForAll();
            LOG.info("Wipe succeeded for node " + nodeid);
        }
        catch (ManagedServiceException e) {
            LOG.log(Level.SEVERE, "RMI HadbService.wipeAndRestartForAll", e);
            throw new StateMachineException(e);
        }
        catch (Exception e) {
            LOG.log(Level.WARNING, "Unexpected exception", e);
            throw new StateMachineException(e);
        }
    }

    private boolean isRemoteMARunning(int nodeid) {
        ManagedService.ProxyObject proxy;
        proxy = ServiceManager.proxyFor(nodeid, HadbService.class);
        if (proxy != null && proxy.isReady()) {
            HADBServiceProxy p = (HADBServiceProxy)proxy;
            return p.getRunning();
        }
        return false;
    }
    
    private boolean nodeDetectedBadDisk(int nodeid) {
        ManagedService.ProxyObject proxy;
        proxy = ServiceManager.proxyFor(nodeid, HadbService.class);
        if (proxy != null && proxy.isReady()) {
            HADBServiceProxy p = (HADBServiceProxy)proxy;
            return p.hasDetectedBadDisk();
        }
        return false;
    }

    private void restartMA(int nodeid) {
        // Get the API to the HadbService on the node
        HADBServiceInterface api = getRemoteService(nodeid);
        if (api == null) {
            LOG.warning("Cannot restart node " + nodeid);
            return;
        }

        try {
            api.restartForAll();
            LOG.info("Restart of node " + nodeid + " succeeded.");

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Cannot restart node " + nodeid, e);
        }
    }

    private HADBServiceInterface getRemoteService(int nodeId) {
        ManagedService.ProxyObject proxy =
             ServiceManager.proxyFor(nodeId, HadbService.class);

        if (proxy == null) {
            LOG.warning("No HadbService proxy on node " + nodeId);
            return null;
        }

        if (!proxy.isReady()) {
            LOG.warning("HadbService proxy on node " + nodeId + " not ready.");
            return null;
        }

        Object obj = proxy.getAPI();
        if (!(obj instanceof HADBServiceInterface)) {
            LOG.warning("The HADB API on " + nodeId + " is not ready.");
            return null;
        }

        return (HADBServiceInterface) obj;
    }

    /**
     * Wait for a particular state. 
     *
     * This MUST only be called when the lock is held (e.g. from a
     * synchronized method). This way when this method returns you're
     * guaranteed that the state really is "s"; nothing else could
     * have sneaked in and changed things.
     *
     * @param s the state to wait for
     * @param endTime if positive, the time when we time out and bail;
     * otherwise wait forever.
     *
     * @throws StateMachineException if it times out
     */
    private void waitUntilState(HadbState s, long timeout)
        throws StateMachineException {

        assert Thread.holdsLock(this);

        try {
            long startTime = System.currentTimeMillis();
        
            if (timeout > 0)
                currentOpEndTime = startTime + timeout;
            else
                // Infinite wait
                currentOpEndTime = Long.MAX_VALUE;

            for (;;) {
                if (state == s)
                    // Success!
                    return;

                long now = System.currentTimeMillis();

                if (now > currentOpEndTime) {
                    // Timeout has expired.
                    String msg = "Timeout waiting for state " + s;
                    throw new StateMachineException(msg);
                }

                try {
                    if (timeout > 0) {
                        wait(currentOpEndTime - now);
                    } else {
                        wait();
                    }
                } catch (InterruptedException ignored) {}
            }
        }
        finally {
            currentOpEndTime = 0;
        }
    }

    /*
     * returns state machine status as an integer value from
     * common.CliConstants
     */
    int getCacheStatus() {

        if (LOG.isLoggable(Level.INFO)) {
            LOG.info("State: " + state + "; cacheStatus=" + state.getStatus());
        }
        return state.getStatus();
    }

    /*
     * returns state machine status as a readable string.
     */
    String getEMDCacheStatus() {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("getEMDCacheStatus returning status " + state);
        }

        return state.toString();
    }

    /*
     * reset HADB to a known state (i.e. empty)
     */
    void clearFailure() {
        LOG.warning("Ignoring clearFailure request - not yet implemented.");
    }

    /** Check if the database state is one that we consider functional */
    private boolean databaseStateOK(DatabaseState s) {
        return
            s == DatabaseState.operational ||
            s == DatabaseState.faultTolerant ||
            s == DatabaseState.haFaultTolerant;
    }

}
