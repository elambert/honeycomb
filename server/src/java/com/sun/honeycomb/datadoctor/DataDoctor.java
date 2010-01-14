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



package com.sun.honeycomb.datadoctor;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.layout.LayoutConfig;
import com.sun.honeycomb.layout.DiskIdList;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.lang.reflect.UndeclaredThrowableException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.StringTokenizer;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.text.MessageFormat;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.ExtLevel;


/** 
 * Service that starts the TaskSteppers, and updates them whenever a
 * relevant config property changes.  This service knows how many
 * tasksteppers to create, and the tasks with which they are associated
 * (which are in other HC packages), via the static array in TaskInfo.
 * Yes, !failureTolerance == possibleDataLoss, two words mean the same
 */
public class DataDoctor implements DataDocIntf, PropertyChangeListener {

    /** Default constructor called by CM. */
    public DataDoctor() {

        // register for notifications of property changes
        ClusterProperties.getInstance().addPropertyListener(this);

        // get handle to config and create our proxy
        LOG.info("DataDocConfig: "+DataDocConfig.getInstance().toString());
        myProxy = new DataDocProxy();
        myNodeId = DataDocConfig.getInstance().localNodeId();

        taskList = TaskList.getInstance(); 
    }

    /** 
     * Implementation of ManagedService interface
     */

    /** Return the current proxy for this service. */
    public ProxyObject getProxy() {
        myProxy.refresh();
        return myProxy;
    }

    /** Called when stopping the service. */
    public void shutdown() {
        taskList.stopTasks();
        running = false; 
        LOG.info("DataDoctor now STOPPED");
    }

    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
    }
    
    /**
     * Return the list of fragments found in the given map id on the given
     * disk.
     */
    public String[] readMap(DiskId diskId, int mapId) {
        return TaskFragUtils.readMap(diskId, mapId);
    }
    
    /**
     * Returns the list of fragments committed on persistent 
     * storage for the given OID on the given disk.
     */
    public String[] getDataFrags(NewObjectIdentifier oid, DiskId diskId) {
        return TaskFragUtils.getDataFrags(oid, diskId);
    }
    
    /**
     * Returns the list of transient fragment for the given OID on 
     * the given list.
     */
    public String[] getTmpFrags(NewObjectIdentifier oid, DiskId diskId) {
        return TaskFragUtils.getTmpFrags(oid, diskId);
    }

    public String[] getAllTmpFrags() {
        return TaskFragUtils.getTmpFrags();
    }

    /** Service entry point. */
    public void run() {
        running = true;
        LOG.info("DataDoctor now RUNNING, waiting "+
                 (STARTUP_SLEEP/60000)+ " minutes to start tasks");
        try {
            Thread.sleep(STARTUP_SLEEP);
        } catch(InterruptedException e) {
            LOG.severe("sleep was interrupted: " + e);
        }

        // might have changed state while we were sleeping
        if (!running) {
            return;
        }

        diskMask = getDiskMask();
        taskList.updateTasks(diskMask);

        // Service thread event loop
        while (running) {

            // check if disk mask changed
            DiskMask oldMask = (DiskMask) diskMask.clone();
            diskMask = getDiskMask();
            if (!diskMask.equals(oldMask)) {
                //
                // If clause is nasty workaround for 6403345
                //
                //if(oldMask.isOffline(myNodeId,0) &&
                //   oldMask.isOffline(myNodeId,1) &&
                //   oldMask.isOffline(myNodeId,2) &&
                //   oldMask.isOffline(myNodeId,3)) {
                //    LOG.warning("Skipping spurrious mask change.");
                //} else {

                    LOG.info("Detected Disk Mask change, was: "+ oldMask +
                             "now: "+diskMask);
                    ResourceBundle rs = BundleAccess.getInstance().getBundle();
                    String str = rs.getString("info.datadoctor.diskmask");
                    Object [] args = { new String(oldMask.toString()),
                                       new String(diskMask.toString())
                                     };
                    LOG.log(ExtLevel.EXT_INFO, MessageFormat.format(str, args));
                    taskList.updateTasks(diskMask);
                //}
            }

            // handle property change
            if (isPropChange) {
                taskList.updateTasks(diskMask);
                isPropChange = false;
            }

            // check if any local tasks are newly done
            DiskMask[] newDoneMasks = taskList.computeTaskDoneMasks();
            for (int i=0; i < newDoneMasks.length; i++) {
                if (!newDoneMasks[i].equals(myProxy.doneMask(i))) {
                    myProxy.doneMask(i, newDoneMasks[i]);
                    proxyChanged = true;
                }
            } 

            // check if any tasks done cell-wide, and update proxy 
            computeCellDoneMasks();

            // update unhealed failure count, and update proxy
            computeUnhealedFailures();

            // publish our failure tolerance
            computeFailureTolerance();

            // check if we need to publish a proxy
            if (proxyChanged) {
                // envokes our getProxy method
                ServiceManager.publish(this); 
                proxyChanged = false;
                if (localNodeIsMaster()) {
                    if (LOG.isLoggable (Level. FINE)) {
                        LOG.fine("published new proxy: "+myProxy);
                    }
                }
            }

            /*
             * IMPORTANT !!!!!
             *
             * Going in maintenance mode has been disabled for Honeycomb
             * 1.0.
             *
             * This is due to the fact that the code in data doctor that
             * computes the number of unique unhealed disks is unreliable.
             *
             * Bug 6407787 has been postponed to 1.1
             */
            
            // master looks cell-wide to see if anyone detects
            // possible data loss, and if so, toggles the global config bit

//             if (localNodeIsMaster()) {
//                 int i = myProxy.cellWidePossibleDataLossCheck();
//                 if(i != -1) {
//                     LOG.severe("Node " + i + 
//                                " detected possible data loss -"+
//                                " setting cluster-wide state to " +
//                                " possible_data_loss = true");
//                     ClusterProperties.getInstance().
//                         setPendingProperty(POSSIBLE_DATA_LOSS, "true");
//                     try {
//                         ClusterProperties.getInstance().commitPendingChanges();
//                         LOG.info("cluster wide now possible_data_loss = true");
//                         running = false; // we are about to be shut down
//                     } catch (ServerConfigException e) {
//                         LOG.severe("Failed to set clusterWide possible dl: " +
//                                    e);
//                     }
//                 }
//             }
	    
            // sleep between iterations of event loop 
            try {
                Thread.sleep(TASK_UPDATE_INTERVAL);
            } catch(InterruptedException e) {
                LOG.severe("sleep was interrupted: " + e);
            }
	    
        }
    }
    

    /** 
     * Implementation of PropertyChangeListener interface
     */
    synchronized public void propertyChange (PropertyChangeEvent event) {

        // check if the event involved any properties we care about
        String oldToString = DataDocConfig.getInstance().toString();
        if (DataDocConfig.getInstance().processPropChange(event)) {
            isPropChange = true;
        }
    }



    /* PRIVATE API */

    /** get the latest disk mask */
    private DiskMask getDiskMask() {

        DiskMask mask = LayoutProxy.getCurrentDiskMask();

        // wait for diskMask to become available
        while (mask == null) {
            LOG.info("disk mask not available, will wait then retry");
            try {
                Thread.sleep(DISK_MASK_WAIT);
            } catch(InterruptedException e) {
                LOG.severe("sleep was interrupted: " + e);
            }
            mask = LayoutProxy.getCurrentDiskMask();
        }

        return mask;
    }

    /** check if a task just completed cell-wide */
    private void computeCellDoneMasks() {

        // refresh cell done mask for all tasks
        for (int i=0; i < TaskList.numTasks(); i++) {
            if (myProxy.refreshCellDone(i)) {
                proxyChanged = true;

                // if task done, record time and mask used
                if (myProxy.isTaskDone(i, diskMask)) {
                    long t = System.currentTimeMillis();
                    myProxy.timeFinished(i, t);
                    myProxy.maskUsed(i, diskMask);
                    LOG.info(TaskList.taskLabel(i)+
                             " finished cell-wide at "+timeToString(t)+
                             " using disk mask "+diskMask);
                }
            }
        } 
    }

    /**
     * Compute both the total number of unrecovered disk failures
     * and the number of failures on unique nodes, and update proxy.
     *
     * Example: for unrecovered disks 102:1 and 102:2, the
     * unhealedFailuresTotal is 2 but the unhealedFailuresUnique is 2
     */
    private void computeUnhealedFailures() {

        DiskIdList unrecovered = computeUnrecoveredDisks();
        int totalFailures = unrecovered.size();

        int uniqueNodes = 0;
        int maxNodeId = LayoutConfig.BASE_NODE_ID +
            LayoutConfig.NODES_PER_CELL - 1;

        for (int i=LayoutConfig.BASE_NODE_ID; i <= maxNodeId; i++) { 

            if (unrecovered.containsNodeCount(i) > 0) {
                uniqueNodes++;
            }
        }

        // if values changed, update our proxy
        if (myProxy.unhealedFailuresTotal() != totalFailures) {
            myProxy.unhealedFailuresTotal(totalFailures);
            if (LOG.isLoggable (Level.FINE)) {
                LOG.fine("total unhealed failures now "+totalFailures); 
            }
            proxyChanged = true;
        }
        if (myProxy.unhealedFailuresUnique() != uniqueNodes) {
            myProxy.unhealedFailuresUnique(uniqueNodes);
            if (LOG.isLoggable (Level.FINE)) {
                LOG.fine("unhealed failures on unique nodes now "+uniqueNodes); 
            }
            proxyChanged = true;
        }
    }

    /** failed disks for which recover not finished */
    private DiskIdList computeUnrecoveredDisks() {

        DiskMask diskMask = (DiskMask)LayoutProxy.getCurrentDiskMask();
        if (diskMask == null) {
            LOG.fine("computeUD: currentDiskMask = null!!");
            return new DiskIdList();
        }
        if (LOG.isLoggable (Level.FINE)) {
            LOG.fine("computeUD: currentDiskMask = " + diskMask);
        }
        diskMask = (DiskMask)diskMask.clone();
        DiskMask lastRecovery = (DiskMask)myProxy.maskUsed(
                                                           TaskList.RECOVER_TASK).clone();
        if (LOG.isLoggable (Level.FINE)) {
            LOG.fine("computeUD: currentDiskMask = " + diskMask);
        }
        DiskMask unrecovered;

        // if recovery not completed, base result on current disk mask
        if (lastRecovery.isEmpty()) {
            if (LOG.isLoggable (Level.FINE)) {
                LOG.fine("computeUD: last recovery is empty");
            }
            unrecovered = diskMask;
            if (LOG.isLoggable (Level.FINE)) {
                LOG.fine("computeUD: unrecovered = diskMask = " + unrecovered);
            }
            int num_nodes = getNumNodesProp();
            if (LOG.isLoggable (Level.FINE)) {
                LOG.fine("computeUD: num_nodes = " + num_nodes);
            }
            if (num_nodes == -1) {
                if (LOG.isLoggable (Level.FINE)) {
                    LOG.fine("computeUD: num_nodes = -1 !!");
                }
                return new DiskIdList();
            }
            // don't count nodes not in cluster as offline
            for (int i = num_nodes; 
                 i < LayoutConfig.NODES_PER_CELL; i++) {

                int nodeId = LayoutConfig.BASE_NODE_ID+i;
                unrecovered.setOnline(nodeId);
            }
            // flip to get offline disks and count them
            unrecovered.flip(0, unrecovered.size());

        } else {
            if (LOG.isLoggable (Level.FINE)) {
                LOG.fine("computeUD: lastRecovery is NOT empty");
            }
            // compute which disks failed since last recovery finished:
            // ON in last mask used for recovery, OFF in current mask
            unrecovered = lastRecovery;
            if (LOG.isLoggable (Level.FINE)) {
                LOG.fine("computeUD: unrecovered = lastRecovery = " 
                                                            + unrecovered);
            }
            diskMask.flip(0, diskMask.size());
	    
            unrecovered.and(diskMask);
        }
        if (LOG.isLoggable (Level.FINE)) {
            LOG.fine("computeUD: ** unrecovered = " + unrecovered + 
            	 " as lost " + unrecovered.onlineDiskIds());
        }
	
        return unrecovered.onlineDiskIds();
    }

    /*
     * Here is the scenario :
     *
     * Step 1. DataDoctor startup or restart
     * initialTolerance is initialized to true.
     * unhealedFailures will be set to -1
     * -- initialTolerance [true]
     * -- failureTolerance [false]
     *
     * Step 2. Data Doctor starts publishing its state
     * All disks are new and unhealed. unhealedFailures will jump to 16
     * -- initialTolerance [true]
     * -- failureTolerance [false]
     *
     * Step 3. All disks are OKed
     * unhealedFailures falls below 2 (parity). 
     * at this point, we exit initialTolerance mode.
     * -- initialTolerance [false]
     * -- failureTolerance [true]
     * From now on, dynamic quorum is activated.
     *
     * Step 4. Too many disks fail
     * unhealedFailures gets above 2
     * -- initialTolerance [false]
     * -- failureTolerance [false]
     * -> NodeMgr takes us into maintenance mode.
     *
     * We still have a problem: we will not detect the case of never
     * reaching fault tolerance on startup. Need to add some sort of
     * timeout (eg full healing pass?) after which we must unset
     * initialTolerance state.
     */
    private void computeFailureTolerance() {
        
        int unhealedFailures = myProxy.unhealedFailuresUnique();

        boolean currentTolerance = (unhealedFailures >= 0) && (unhealedFailures <= DataDocConfig.getInstance().parityFrags);

        // LOG.info("computeFT unhealedFailures = " + unhealedFailures +
        // 	 " computeFT parityFrags = " + DataDocConfig.getInstance().parityFrags +
        // 	 " computeFT currentTolerance = " + currentTolerance + 
        //	 " computeFT initialTolerance = " + initialTolerance);

        // initialTolerance is true iff we are currently in
        // initialTolerance mode and we have not yet reach faultTolerance.
        // Otherwise, initialTolerance forever remains false.
        initialTolerance = initialTolerance && !currentTolerance;

        // LOG.info("computeFT initialTolerance  = " + initialTolerance);
        // LOG.info("computeFT PUBLISHING tolerance = " 
        //	 + (initialTolerance || currentTolerance));

        // failureTolerancs is true if where are in initialTolerance mode
        // or our currentTolerance is ok.
        myProxy.failureTolerance(initialTolerance || currentTolerance);
    }


    /** get num_nodes property from config file */
    private int getNumNodesProp() {

        ClusterProperties config = ClusterProperties.getInstance();
        if (config == null) {
            LOG.warning("Cannot get cluster properties");
            return -1;
        }
        return config.getPropertyAsInt(ConfigPropertyNames.PROP_NUMNODES);

    }

    /** Determine if the local node is Master. */
    static boolean localNodeIsMaster() {

        NodeMgrService.Proxy proxy = DataDocConfig.getNodeMgrProxy();
        Node[] nodes = proxy.getNodes();
        for (int i=0; i < nodes.length; i++) {
            if (nodes[i].isMaster()) {
                return nodes[i].nodeId() == proxy.nodeId();
            }
        }
        return false;   // master not found
    }

    /** Converts given time to same format used in syslog */
    static String timeToString(long time) {

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
        return sdf.format(new Date(time));
    }

    
    /* PRIVATE ATTRIBUTES */

    // tasks we're managing
    private TaskList taskList = null;

    // used by service main loop
    private DataDocProxy myProxy = null;        // our current proxy obj
    private volatile boolean running = false;   // true until shutdown

    // config info

    int myNodeId = -1;                          // local nodeId

    DiskMask diskMask = null;                   // current disk mask

    boolean isPropChange = false;               // set by config callback
    boolean proxyChanged = false;               // set by task callback

    boolean initialTolerance = true; // have we determined current tolerance yet?
    

    /* PRIVATE CONSTANTS */

    // sleep durations
    private static final int STARTUP_SLEEP = 1000 * 60 * 3;     // 3 min
    private static final int TASK_UPDATE_INTERVAL = 1000;       // 1 second
    private static final int DISK_MASK_WAIT = 10000;            // 10 seconds

    // logger has same name as this class
    private static final Logger LOG =
        Logger.getLogger(DataDoctor.class.getName());

}

