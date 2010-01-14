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



package com.sun.honeycomb.layout;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.oa.OAServerIntf;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.config.ClusterProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.Iterator;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.StringTokenizer;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;


/** 
 * Generates DiskMasks by reading DiskProxy.  Responsible for
 * deciding when disks on a missing node should be treated as offline.
 */

public class LayoutService implements LayoutInterface, PropertyChangeListener {

    /** Default constructor called by CM. */
    public LayoutService() {
        ClusterProperties.getInstance().addPropertyListener (this);

        // read config and create our proxy
        LayoutConfig.getClusterConfig();
        NODES = LayoutConfig.NODES_PER_CELL;
        DISKS = LayoutConfig.DISKS_PER_NODE;
        gracePeriodMillis = LayoutConfig.getGracePeriod();
        LOG.info("LayoutConfig: "+LayoutConfig.configToString());
        myProxy = new LayoutProxy();
    }

    /** Return the current proxy for this service. */
    public ProxyObject getProxy() {

        return myProxy;
    }

    /** Called when stopping the service. */
    public void shutdown() {

        running = false; 
        thr.interrupt();
        boolean stopped = false;
        while (!stopped) {
            try {
                thr.join();
                stopped = true;
            } catch (InterruptedException ignored) {
            }
        }
        LOG.info("LayoutService now STOPPED");
    }

    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
        thr = Thread.currentThread();
    }


    /** Service entry point. */
    public void run() {

        LOG.info("LayoutService now RUNNING");

        running = true;

        long timeAtStartup = System.currentTimeMillis();
        boolean proxyChanged = false;
        boolean maskChange = false;
        DiskMask newMask = null;

        // clear proxy, in case we just restarted this service
        myProxy.currentDiskMask(new DiskMask());
        pendingFailures.clear();
        boolean firstIter = true;

        // Service thread event loop
        while (running) {

            // compute new disk mask 
            DiskMask oldMask = myProxy.currentDiskMask();
            newMask = computeDiskMask();

            // compare old and new masks to account for the grace period
            gracePeriod(oldMask, newMask, firstIter);
            firstIter = false;

            // must be done before a new proxy is published
            // this method may modify newMask
            manageDisks(oldMask, newMask);

            // check for disk mask change
            long timeNow = System.currentTimeMillis();
            if (!newMask.equals(oldMask)) {

                // update proxy and remember time
                myProxy.currentDiskMask(newMask);
                proxyChanged = true;
                maskChange = true;
                lastChange = timeNow;
            }

            // log if timer expired or mask changed
            if (timeNow - lastLogDiffs >= LOG_DIFFS_MILLIS || maskChange) {
                logDiffs(oldMask, newMask);
                lastLogDiffs = timeNow;
                maskChange = false;
            } 

            // check if we need to block or unblock stores
            if (myProxy.blockStores() != isUsageCapReached) { 
                
                myProxy.blockStores(isUsageCapReached);
                proxyChanged = true;
                LOG.info("blockStores flag changed to " + isUsageCapReached);
            }

            // check if we need to publish a new proxy
            if (proxyChanged) {

                // envokes our getProxy method
                ServiceManager.publish(this); 
                proxyChanged = false;
            }

            // sleep between iterations of event loop 
            try {
                Thread.sleep(MASK_UPDATE_INTERVAL);
            } catch(InterruptedException ignore) {
            }
        }
        closeDisks(newMask);
    }
    
    /*
     * Unmount all the remote disks to avaoid having stale NFS handle.
     */
    private void closeDisks(DiskMask curDiskMask) {

        OAServerIntf oa = OAServerIntf.Proxy.getLocalAPI();
        if (oa == null) {
            String error = "ERROR - cannot access local OA proxy/api";
            LOG.severe(error);
            throw new RuntimeException(error);
        }
        
        int num_nodes = ClusterProperties.getInstance()
	    .getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
        
        for (int i = 0; i < num_nodes; i++) {
            int n = LayoutConfig.BASE_NODE_ID + i;
            // skip current node.
            if (n == LayoutConfig.getLocalNodeId()) {
                continue;
            }
            for (int d = 0; d < DISKS; d++) {
                if (curDiskMask.isOnline(n, d)) {
                    DiskId diskId = new DiskId(n, d);
                    try {
                        oa.closeDisk(diskId);
                    } catch (ManagedServiceException me) {
                        LOG.warning("failed to close disk " + diskId);
                    }
                }
            }
        }
    }


    /* PRIVATE API */

    /** Recompute the current disk mask.  Check per-disk usage flag. */
    private synchronized DiskMask computeDiskMask() {

        boolean capReached = false; // has the disk cap. limit been reached?

        // create new diskmask to hold result
        DiskMask newMask = new DiskMask(); 

        int num_nodes = ClusterProperties.getInstance()
	    .getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
        
        // walk nodes, look for online disks
        for (int i=0; i < num_nodes; i++) {
            int n = LayoutConfig.BASE_NODE_ID + i;

            // If the node doesn't exist, skip it
            Disk[] nodeDisks = DiskProxy.getDisks(n);
            if (nodeDisks == null) {
                continue;
            }

            // should have an entry for every disk, even if offline
            if (nodeDisks.length != DISKS) {
                throw new InternalException(
                    "DiskProxy returned "+nodeDisks.length+
                    " disks for node "+n+", expected "+DISKS);

            }

            // walk disks, check status, if online then add to mask.
            // check to see if we've hit the disk usage limit too.
            for (int d=0; d < DISKS; d++) {

                Disk disk = nodeDisks[d];
                if (disk != null && disk.isEnabled()) {
                    /*
                     * Set online sets both enabled and available.
                     *
                     * Later this code will have to be integrated with 
                     * an administrative layer for making disks unavailable
                     * even though they are enabled. So they can be used 
                     * by upgrade or maybe other features like evacuation of 
                     * disk contents.
                     */
                    newMask.setOnline(n, d);

                    if (disk.getUsageCapReached()) {
                        capReached = true;
                    }
                } 
            }
        }

        // did we change the state of isUsageCapReached?

        if (capReached != isUsageCapReached) {
            isUsageCapReached = capReached;
        }

        return newMask;
    }

    /** update the newMask to account for pending failures  */
    synchronized private void gracePeriod(DiskMask oldMask, DiskMask newMask, 
                             boolean firstIter) {

        // if grace period is zero, nothing to do
        if (gracePeriodMillis <= 0) {
            return;
        }

        long now = System.currentTimeMillis();

        int num_nodes = ClusterProperties.getInstance()
	    .getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
        
        // compare the old and new masks
        for (int i=0; i < num_nodes; i++) {
            int n = LayoutConfig.BASE_NODE_ID + i;
            for (int d=0; d < DISKS; d++) {

                // went offline: keep online but add to pending failures
                // no grace period if disk down on first iteration
                if (oldMask.isOnline(n, d) && 
                    newMask.isOffline(n, d) &&
                    !firstIter) {  

                    newMask.setOnline(n, d);
                    DiskId diskId = new DiskId(n ,d);
                    if (!pendingFailures.containsKey(diskId)) {
                        pendingFailures.put(diskId, new Long(now)); 
                        LOG.info("adding "+diskId.toStringShort()+
                                 " to pending failures");    
                    }

                // is online: check and remove it from pending failures.
                } else if (newMask.isOnline(n, d)) {

                    DiskId diskId = new DiskId(n ,d);
                    if (pendingFailures.containsKey(diskId)) {
                        pendingFailures.remove(diskId);
                        LOG.info("removing "+diskId.toStringShort()+
                                 " from pending failures");    
                    }
                } 
            }
        } 

        // clone because we may modify pendingFailures in the loop,
        // leading to a ConcurrentModificationException 
        Iterator diskIter = ((HashMap)pendingFailures.clone()).
                                keySet().iterator();
        // check if any pending failures hit the grace period
        while (diskIter.hasNext()) {

            DiskId diskId = (DiskId)diskIter.next();
            Long timeFailed = (Long)pendingFailures.get(diskId);

            // grace period elapsed: set offline and remove from pending
            if (now - timeFailed.longValue() > gracePeriodMillis) {
                newMask.setOffline(diskId);
                pendingFailures.remove(diskId);
                LOG.info("grace period expired for  "+
                         diskId.toStringShort()+ 
                         ", removing from disk mask");    
            }
        }
    }

    /** Diffs btwn old and new mask, and time since last mask change. */
    private void logDiffs(DiskMask oldMask, DiskMask newMask) {

        // create array lists
        DiskIdList wentOffline = new DiskIdList();
        DiskIdList cameOnline = new DiskIdList();
        DiskIdList stillOffline = new DiskIdList();
        DiskIdList stillOnline = new DiskIdList();

        int num_nodes = ClusterProperties.getInstance()
	    .getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
        // walk nodes
        for (int i=0; i < num_nodes; i++) {
        
            // walk disks and classify
            int n = LayoutConfig.BASE_NODE_ID + i;
            for (int j=0; j < DISKS; j++) {

                int d = j;
                DiskId diskId = new DiskId(n, d);
                if (newMask.isOnline(n, d)) {
                    if (!oldMask.isOnline(n, d)) {
                        cameOnline.add(diskId);
                    } else {
                        stillOnline.add(diskId);
                    }
                } else {
                    if (oldMask.isOnline(n, d)) {
                        wentOffline.add(diskId);
                    } else {
                        stillOffline.add(diskId);
                    }
                }
            }
        }

        // print lists
        StringBuffer sb = new StringBuffer();
        sb.append("Disk Mask "); 
        if (!newMask.equals(oldMask)) {
            sb.append("Changed --");
            sb.append("  went offline: " + wentOffline);
            sb.append("  came online: " + cameOnline);
        } else {
           sb.append("(last changed " + 
                   timeToString(lastChange) + ") -- ");
        }
        sb.append("  still online: " + stillOnline);
        sb.append("  still offline: " + stillOffline);

        LOG.info(sb.toString());
    }

    /** 
     * Update datapath for online/offline disks.
     * If cannot open a new disk, set it back to offline.
     */
    private void manageDisks(DiskMask oldMask, DiskMask newMask) {

        OAServerIntf oa = OAServerIntf.Proxy.getLocalAPI();
        if (oa == null) {
            String error = "ERROR - cannot access local OA proxy/api";
            LOG.severe(error);
            return;
        }
        
        int num_nodes = ClusterProperties.getInstance()
	    .getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
        for (int i = 0; i < num_nodes; i++) {
            int n = LayoutConfig.BASE_NODE_ID + i;

            for (int d = 0; d < DISKS; d++) {
                
                DiskId diskId = new DiskId(n, d);
                if (newMask.isOnline(n, d)) {
                    if (!oldMask.isOnline(n, d)) {

                        boolean succeed;
                        try {
                            succeed = oa.openDisk(diskId);
                        } catch (ManagedServiceException me) {
                            succeed = false;
                        }
                        if (!succeed) {
                            
                            // maybe we should disable the disk if 
                            // none of the nodes can mount it?
                            LOG.warning("cannot open disk "+ diskId.toStringShort());
                            
                            // for now just remove from disk mask, so
                            // we'll retry openDisk next time around
                            newMask.setOffline(n, d);
                        }
                    }
                } else if (oldMask.isOnline(n, d)) {

                    // new disk offline - close it
                    try {
                        oa.closeDisk(diskId);
                    } catch (ManagedServiceException me) {
                        LOG.warning("failed to close disk " + diskId.toStringShort());
                    }
                }
            }
        }
    }

    /** Converts given time to same format used in syslog */
    private String timeToString(long time) {

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
        return sdf.format(new Date(time));
    }

    /**
     * handle property change events
     */
    public void propertyChange (PropertyChangeEvent event) {
        //LOG.info ("recieved property change event: " + event);
        //String prop = event.getPropertyName();
    }

       /* PRIVATE ATTRIBUTES */

    // set in constructor
    private int NODES;                  // max nodes per cell
    private int DISKS;                  // number of disks per node

    // used by service main loop
    private LayoutProxy myProxy = null;         // our current proxy obj
    private volatile boolean running = false;   // true until shutdown
    private long lastLogDiffs = 0;              // time of last log msg
    private long lastChange = 0;                // last mask change

    private boolean isUsageCapReached = false;  // set by computeDM

    private HashMap pendingFailures =  new HashMap(); // in grace period
    private long gracePeriodMillis;             // from config file

    private Thread thr = null;

    /* PRIVATE CONSTANTS */

    // How long to wait between logging current disk mask
    public static final int LOG_DIFFS_MINS = 10;   
    public static final int LOG_DIFFS_MILLIS = 60000 * LOG_DIFFS_MINS;

    // how much to sleep between iterations of our main loop
    private static final int MASK_UPDATE_INTERVAL = 1000; // 1 second

    // logger has same name as this class
    private static final Logger LOG =
        Logger.getLogger(LayoutService.class.getName());

}

