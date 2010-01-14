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

import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.IncompleteObjectException;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.admin.mgmt.server.HCCellAdapter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;


/** 
 * Querys system Metadata to see which fragments should be on this disk.
 * If any are missing, reconstruct via Object Archive Reed-Solomon. 
 */
public class RecoverLostFrags implements Steppable {


    private static final long READDIR_TIMEOUT = (10 * 60); // 10mn
    private static final LayoutClient lc = LayoutClient.getInstance();
    private static final OAClient oaClient = OAClient.getInstance();
    
    /*
     * Threads pool to get the directory lists asynchronously.
     * This pool is shared between all lost fragment recovery threads.
     */
    private static ExecutorService executor = Executors.newCachedThreadPool();
  
    private int errorCount;
    
    private String taskName;
    private DiskMask diskMask;
    private Object diskMaskMonitor = new Object();
    private DiskId myDiskId;
    private boolean abortStep;
    private TaskLogger log;
    private String[][] dirsList;
    private Semaphore syncTasks;
    
    /* only used if log level = FINE */
    long phase0 = 0;
    long phase1 = 0;
    long phase2 = 0;
    long phase3 = 0;

    
    /**********************************************************************
     * constructor is no-args, real setup work done in init method
     **/
    public RecoverLostFrags() {
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public void init(String taskName, DiskId diskId) {

        this.taskName = taskName;
        this.myDiskId = diskId;
        this.dirsList = null;
        this.log = new TaskLogger(RecoverLostFrags.class.getName(), taskName);
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public String getName() { return taskName; }

    /**********************************************************************
     * @inherit javadoc
     **/
    public int getNumSteps() { return Steppable.NUM_STEPS; }

    /**********************************************************************
     * @inherit javadoc
     **/
    public int getErrorCount() { return errorCount; }

    /**********************************************************************
     * @inherit javadoc
     **/
    public void resetErrorCount() { errorCount = 0; }

    
    /**********************************************************************
     * @inherit javadoc
     **/
    public void abortStep() { abortStep = true; }

    /**********************************************************************
     * @inherit javadoc
     *
     * synchronized to prevent mask change in middle of step
     **/
    public void newDiskMask(DiskMask newMask) {
        synchronized (diskMaskMonitor) {
            diskMask = newMask;
        }
    }


    /**********************************************************************
     * @inherit javadoc
     **/
    public void step(int stepNum) {

        int mapId;
        int fragId;
        Disk myDisk;
        Disk healedDisk;
        Layout layout;

        if (log.isLoggable (Level.FINE)) {
            phase0 = System.currentTimeMillis();
        }
        
        synchronized (diskMaskMonitor) {
            if (diskMask == null) {
                log.severe("DiskMask is null, aborting step "+stepNum);
                return;
            }

            myDisk = DiskProxy.getDisk(myDiskId);
            if (myDisk == null) {
                log.severe("myDisk is null, aborting step "+stepNum);
                return;
            }
            
            // get the current layout for this mapId
            mapId = stepNum;
            layout = lc.getLayoutForRecover(mapId, diskMask);

            // if mapId is not using this disk, no fragments to recover for it
            if (!layout.contains(myDiskId)) {
                return;
            }

            // remember which fragment should be here
            fragId = layout.indexOf(myDiskId);        

            // This could be a heal back from failure. Check if the file
            // exists in the disk that is one row below in the same column.
            // Move file if file exists.
            healedDisk = lc.getNextDiskInColumn(mapId, diskMask, myDiskId, fragId);
        }
        
        if (log.isLoggable (Level.FINE)) {
            phase1 = System.currentTimeMillis();
        }

        handleLayout(mapId, fragId, myDisk, healedDisk, layout);

    }


    public void handleLayout(int mapId, int fragId, Disk myDisk, Disk healedDisk, Layout layout)
    {
        /*
         * Get a listing of all fragment files in these diskIds asynchronously.
         * This is where we get the list of OIDs to recover 
         * (not from system MD cache)
         */
        int numDirs = layout.size();
        syncTasks = new Semaphore(0);
        int numTasks = 0;
        int myIndex = -1;
        
        boolean isSloshing = 
        (HCCellAdapter.EXPAN_STR_EXPAND.compareTo(ClusterProperties.getInstance()
                            .getProperty(ConfigPropertyNames.PROP_EXP_STATUS)
                             ) == 0)? true:false;
        
        if (isSloshing) {
            // add the disks from the pre-sloshing layout
            numDirs += lc.getLayoutForSloshing(mapId).size();
        }

        dirsList = new String[numDirs][];
        readFragsTask[] tasks = new readFragsTask[numDirs];

        for (int i = 0; i < numDirs; i++) {
            DiskId diskId;
            if (i >= layout.size()) {
                assert(isSloshing);
                // add the disks from the pre-sloshing layout
                Layout preSloshLayout = lc.getLayoutForSloshing(mapId);
                diskId = (DiskId) preSloshLayout.get((i - layout.size()));
            } else {
                diskId = (DiskId) layout.get(i);
            }
            if (diskId == null) {
                dirsList[i] = null;
                continue;
            }
            if (diskId.equals(myDisk.getId())) {
                assert(myIndex == -1);
                myIndex = i;
            }
            // build an async thread to read the fragments and fire it up
            tasks[i] = new readFragsTask(diskId, mapId, i);
            numTasks++;
            executor.execute(tasks[i]);
        }
        if (myIndex == -1) {
            log.severe("failed to get my recovery disk");
            errorCount++;
            return;
        }

        /*
         * Wait for all threads to complete.
         * The current step is aborted if the operation timed out.
         */
        try {
            boolean isDone;
            isDone = syncTasks.tryAcquire(numTasks, READDIR_TIMEOUT, TimeUnit.SECONDS);
            if (!isDone) {
                log.severe("timeout waiting for readdir tasks to complete for " + 
                           mapId +
                           ", aborting step"
                           );
                errorCount++;
                return;
            }
        } catch (InterruptedException ie) {
            log.severe("thread interrupted waiting for readdir, aborting step");
            errorCount++;
            return;
        }
        
        if (log.isLoggable (Level.FINE)) {
            phase2 = System.currentTimeMillis();
        }
        
        /*
         * Check and build the results
         */
        int maxOids = 0;
        int maxLen = 0;
        boolean dirsMissing = false;

        for (int i = 0; i < numDirs; i++) {      
            if (dirsList[i] == null) {
                dirsMissing = true;
                break;
            }
            maxOids += dirsList[i].length;
            maxLen = Math.max(maxLen, dirsList[i].length);
        }
                
        if (dirsMissing) {
            if (log.isLoggable (Level.FINE)) {
                log.fine("map " + mapId +  " no dir phase1 " + (phase1 - phase0) 
                         + " msec "+ "phase2 " + (phase2 - phase1) + " msec"
                         );
            }
            log.warning("failed to collect list for mapId "+mapId+", aborting step");
            errorCount++;
            return;
        }
      
        // no oids to recover
        if (maxLen == 0) {
            if (log.isLoggable (Level.FINE)) {
                log.fine("map " + mapId +  " empty map phase1 " + 
                         (phase1 - phase0) + " msec "+ "phase2 " + 
                         (phase2 - phase1) + " msec"
                         );
            }
            return;
        }

        // make an array of unique OIDs found in these directories
        // FIXME: this assumes how an oid filename is built (oid_fragnum)
        String [][] oidsList = MergeSorted.truncateAt(Common.fragNumSep, dirsList);
        List localMap = Arrays.asList(oidsList[myIndex]);
        MergeSorted.Result[] oids = MergeSorted.mergeSortedLists(oidsList);
        
        if (log.isLoggable (Level.FINE)) {
            phase3 = System.currentTimeMillis();
        }
        
        /*
         * Recover each OID in the list if it does not exist.
         */
        for (int i=0; i < oids.length; i++) {
            
            if (localMap.contains(oids[i].val)) {
                if (log.isLoggable (Level.FINE)) {
                    log.fine("oid " + oids[i].val + " exists in map "
                             + mapId + " on disk " + myDisk.getId());
                }
                continue;
            }
                    
            try {
                NewObjectIdentifier oid = null;
                try {
                    oid = new NewObjectIdentifier(oids[i].val);
                } catch (IllegalArgumentException e) {
                    log.warning("Skipping file: " + oids[i].val + ": " + e.getMessage());
                    continue;
                }
                                
                boolean success = false;

                // First, check the current layout. This is generally the
                // ONLY layout.
                long timeStart = System.currentTimeMillis();
                success = recoverViaCopy(oid, fragId, myDisk, healedDisk);

                if (!success && isSloshing) {
                    if (log.isLoggable (Level.FINE))
                        log.fine ("failed to recover " + oid 
                            + ". attempting to recover from pre-slosh layout.");
                    // If we're sloshing, and the previous call failed, we need
                    // to check the pre-slosh layout for the fragments we're 
                    // looking for.
                    Layout preSloshLayout = lc.getLayoutForSloshing(mapId);
                    Disk preSloshDisk = preSloshLayout.getDisk (fragId);
                    success = recoverViaCopy(oid, fragId, myDisk, preSloshDisk, false);
                }

                // Finally, if we've not bene successful in trying to copy 
                // the missing fragment, we should attempt to recover it.
                if (!success) {
                    if (log.isLoggable (Level.FINE))
                        log.fine ("failed to recover " + oid 
                            + ". attempting final recovery.");
                    success = recoverViaOA (oid, fragId, myDisk);
                }

                long timeEnd = System.currentTimeMillis();
                long timeInterval = timeEnd - timeStart;
                
                if (success) {
                    FragmentFile frag = new FragmentFile(oid, fragId, myDisk);
                    if (frag.exists()) {
                      frag.open();
                      long fragSize = frag.getDataSize();
                      frag.close();
                      log.info("recovered " + oid + " to " +
                             myDiskId.toStringShort() + " in " + timeInterval 
                             + " msec " + fragSize + " bytes of data");
                    }
                    else
                      log.log(Level.SEVERE,
                        "oid " + oid + " frag " + fragId + 
                        " does not exist on disk " + myDiskId.toStringShort());

                } else {
                    errorCount++;
                    log.severe("Failed to recover(errorCount: " + errorCount 
                               + ") " +  oids[i].val + " (" + fragId + ") to "
                               + myDiskId.toStringShort()
                               );                    
                }
            } catch (Throwable e) {
                // FIXME - This means software bugs that we don't catch.
                // NOTE: No need to synchronize for errorCount. If more than
                // one task increments this variable simulataneously, it is still OK.
                errorCount++;
                log.log(Level.SEVERE, 
                        "Failed to recover (errorCount: " + errorCount + ") "
                           +  oids[i].val + " (" + fragId + ") to "
                           + myDiskId.toStringShort(), e);
            }
        }
        
        if (log.isLoggable (Level.FINE)) {
            long phase4 = System.currentTimeMillis();
            log.info("map " + mapId + " map length " + oids.length + 
                     " phase1 " + (phase1 - phase0) + " msec"+ 
                     " phase2 " + (phase2 - phase1) + " msec"+
                     " phase3 " + (phase3 - phase2) + " msec"+
                     " phase4 " + (phase4 - phase3) + " msec"
                     );
        }
    }
   
    /**********************************************************************
     * Attempt to perform recovery of this fragment by copying from
     * either the 16 node or 8 node layour depending on whether the
     * cluster is expanding or not.
     **/
    private boolean recoverViaCopy(NewObjectIdentifier oid, 
                               int fragId, 
                               Disk myDisk,
                               Disk healedDisk) {
        return recoverViaCopy (oid, fragId, myDisk, healedDisk, true);
    }

    private boolean recoverViaCopy(NewObjectIdentifier oid, 
                               int fragId, 
                               Disk myDisk,
                               Disk healedDisk,
                               boolean failOnDeleted) {

        boolean copySuccess = false;

        if (oid == null) {
            // supposed to be checked by the caller!
            log.severe("recoverViaCopy received null oid");
            return copySuccess;
        }

        // get fragment file for this oid
        FragmentFile frag = new FragmentFile(oid, fragId, myDisk);
        if (frag.exists()) {
            // fragment is there, no need to recover.
            // should not happen at this point.
            log.warning("frag "+ fragId + " for oid " + oid 
                        + " already exists on disk " + myDisk
                        );
            return copySuccess;
        }

        // get (possible) previous fragment file for this oid
        try {
            if ((healedDisk == null) || (healedDisk == myDisk)) {
                log.info("healed disk cannot be found (rows exhausted?) for "
                         + oid + "(" + fragId + ") " + myDiskId.toStringShort());
            } else {
                FragmentFile healedFrag = new FragmentFile(oid, fragId, healedDisk);
                if (healedFrag.exists()) {
                    // move contents
                    log.info("Copying file during recovery of " + oid + "("
                             + fragId + ") from " + healedDisk.getId()
                             );
                    copySuccess = TaskFragUtils.moveFragment(healedFrag, myDisk, failOnDeleted);
                }
            }
        } catch (Exception e) {
            // No need to panic here
            log.log(Level.WARNING,"copy failed for oid " + oid + ", fragId " + 
                                  fragId + " disk " + healedDisk.getId(),e);
        }

        return copySuccess;
    }

    /**********************************************************************
     * Tries to perform an expensive, non-copy recovery via OA
     * @return true if recovery suceeded
     **/
    private boolean recoverViaOA (NewObjectIdentifier oid, 
                                  int fragId, 
                                  Disk myDisk) 
    {
        try {
            TaskFragUtils.recoverFragment(oid, fragId, myDisk);
        } catch (IncompleteObjectException ioe) {
            log.warning("Skipping: " + ioe.getMessage());
            return false;
        } catch (OAException oae) {
            // check to see if this object has frags in tmp directory
            // XXX this should no longer be necessary - done at lower level
            if (isEphemeral(oid)) {
                log.info(oid + " has frags in tmp directory");
                return false;
            } else {
                // check to see if local frag now exists
                // possible if recovery is racing with store
                FragmentFile frag = new FragmentFile(oid, fragId, myDisk);
                if (frag.exists()) {
                    log.info(oid + " already has frag on disk " +
                             myDiskId.toStringShort());
                } else {
                    log.severe("failed to recover " + oid + ") to "
                               + myDiskId.toStringShort() + " : " + oae);
                    return false;
                }
            }
        }
        return true;
    }

   /**********************************************************************
    * Check if this object has frags in tmp directory. This means
    * either the object is being written or that it is not yet removed
    * because of failed write.
    * TODO: crawl all disks instead of just current layout.
    **/
    private boolean isEphemeral(NewObjectIdentifier oid) {
        Layout layout;
        synchronized(diskMaskMonitor) {
            layout = lc.getLayoutForRecover(oid.getLayoutMapId(), diskMask);
        }
        for (int i = 0; i < layout.size(); i++) {
            DiskId diskId = (DiskId) layout.get(i);
            String[] res = TaskFragUtils.getTmpFrags(oid, diskId);
            if (res != null) {
                if (res.length > 0) {
                    // found at least one on this diskId
                    return true;
                }
            }
        }
        return false;
    }

    /**********************************************************************/
    public class readFragsTask implements Runnable {
        
        private final DiskId diskId;
        private final int mapId;
        private final int index;

        readFragsTask(DiskId _diskId, int _mapId, int _index) {
            diskId = _diskId;
            mapId = _mapId;
            index = _index;
        }
        
        public void run() {
            try {
                String[] output = TaskFragUtils.readMap(diskId, mapId);
                if (output != null) {
                    Arrays.sort(output);
                }
                dirsList[index] = output;
            } finally {
                syncTasks.release();
            }
        }
    }
}
