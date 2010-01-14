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

import java.util.logging.Level;

import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.oa.DeletedFragmentException;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.config.ClusterProperties;

/** 
 * Populates the local system cache for this disk by inserting all
 * fragments that are not aready there.
 */
public class PopulateExtCache implements Steppable {

    private String taskName;
    private DiskMask diskMask = null;
    private DiskId myDiskId;
    private boolean abortStep = false;
    private int startingMap;
    private TaskLogger log;
    private int errorCount;
    private static MetadataClient mdClient = MetadataClient.getInstance(); 

    private String cacheId = CacheClientInterface.EXTENDED_CACHE;

    /**********************************************************************
     * constructor is no-args, real setup work done in init method
     **/
    public PopulateExtCache() {
        
        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);        
        int localNodeId = (proxy.nodeId() - 100);
        assert(localNodeId >= 0 && localNodeId < CMM.MAX_NODES);        
        startingMap =  (localNodeId * (getNumSteps() - 1)) / CMM.MAX_NODES;
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public void init(String taskName, DiskId diskId) {

        this.taskName = taskName;
        this.myDiskId = diskId;
        this.log = new TaskLogger(PopulateExtCache.class.getName(), taskName);
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public String getName() {
        return taskName;
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public int getNumSteps() { return Steppable.NUM_STEPS; }


    /**********************************************************************
     * @inherit javadoc
     **/
    public void abortStep() { abortStep = true; }

    /**********************************************************************
     * @inherit javadoc
     **/
    public int getErrorCount() { return errorCount;}


    /**********************************************************************
     * @inherit javadoc
     **/
    public void resetErrorCount() { errorCount = 0; }
    
    /**********************************************************************
     * @inherit javadoc
     **/
    synchronized public void newDiskMask(DiskMask newMask) {
        diskMask = newMask;
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    synchronized public void step(int stepNum) {

        if (diskMask == null) {
            log.severe("DiskMask is null, aborting step "+stepNum);
            errorCount++;
            return;
        }

        Disk myDisk = DiskProxy.getDisk(myDiskId);
        if (myDisk == null) {
            log.severe("myDisk is null, aborting step "+stepNum);
            errorCount++;
            return;
        }

        // Make sure the external cache is available
        CacheClientInterface cache = null;
        try {
            cache = CacheManager.getInstance().getClientInterface(cacheId);
        } catch (EMDException e) {
            log.warning("Failed to get a cache instance ["+
                        e.getMessage()+"]. Aborting step " +
                        stepNum);
            errorCount++;
            return;
        }

        if (cache == null) {
            log.severe("Did not get a cache object back, " +
                       "aborting step " + stepNum);
            errorCount++;
            return;
        }

        // Sleep and retry until the cache comes up
        int cacheWaitSteps = 0;
        int cacheWaitSleep = 10;
        int cacheMessageInterval = 60;
        while (!cache.isRunning()) {

            // Print the wait message periodically
            if (((cacheWaitSleep * cacheWaitSteps) %
                 cacheMessageInterval) == 0) {
                log.info("Extended metadata database is not yet running");
            }
            cacheWaitSteps++;

            if (abortStep) {
                log.info("aborting step "+stepNum);
                abortStep = false;
                errorCount++;
                return;
            }
 
            try {
                Thread.sleep(cacheWaitSleep * 1000);
            } catch (InterruptedException ignored) {}
        }

        int mapId = (stepNum + startingMap) % getNumSteps();        
        String[] fragList = TaskFragUtils.readMap(myDiskId, mapId);
        if (fragList == null) {
            log.severe("failed to read map " + mapId + " on disk " + myDiskId);
            errorCount++;
            return;
        }

        // walk frags in this directory, checking frag number
        for (int i=0; i < fragList.length; i++) {

            // check if we're supposed to stop
            if (abortStep) {
                log.info("aborting step "+stepNum);
                abortStep = false;
                errorCount++;
                return;
            }

            String f = fragList[i];
            NewObjectIdentifier oid = null;
            int fragid = -1;
            try {
                oid = TaskFragUtils.fileNameToOid(f);
                fragid = TaskFragUtils.extractFragId(f);
            } catch (NotFragmentFileException e) {
                log.warning("Skipping file: " + f + ": " + e.getMessage());
                continue;
            }

            // only insert for frags 0-2.  This guarantees that each
            // object in OA will be inserted into the Extended Cache.
            if (fragid > 2) {
                continue;
            }
	       
            FragmentFile frag = null;
            try {

                // if this is a data object ignore it
                if (oid.getObjectType() == NewObjectIdentifier.DATA_TYPE) {
                    continue;
                }
                
                frag = new FragmentFile(oid, fragid, myDisk);
                frag.open();

                if (!frag.isDeleted() && 
                    !mdClient.existsExtCache(cacheId, oid)) {
                    // This is a metadata fragment. If it does not exist in the 
                    // extended cache then insert it. Note that this is
                    // a cell-wide query, not local to this disk, because in
                    // general we don't know how the underlying DB distributes
                    // data among disks, as we do for our system cache.

                    log.info("metadata oid "+oid+" not found in "+
                             cacheId+" cache, inserting");

                    long timeStart = System.currentTimeMillis();
                    CacheRecord mdObject;
                    mdObject = cache.generateMetadataObject(oid);
                    long timeEnd = System.currentTimeMillis();
                    long timeInterval = timeEnd - timeStart;
                    
                    // If store into MD is not successful, then count
                    // that as a fatal DD error to this step.
                    if (!mdClient.setMetadata(cacheId, oid, mdObject)) {
                        log.info("setMetadata failed - fatal to this step");
                        errorCount++;
                    }
                    else
                        log.info("metadata oid " + oid + " inserted in "+
                                 + timeInterval + " msec");


                } else if (frag.isDeleted() && 
                           mdClient.existsExtCache(cacheId, oid)) {
                    log.info("deleted metadata oid "+oid+" found in "+
                             cacheId+" cache, removing");
                    // deleted metadata frag, remove it from ext cache.
                    mdClient.removeMetadata(oid, cacheId);
                }
            } catch (EMDException e) {
                //Can fail on query, store, or delete.   Each of them
                //affects just this one object.
                log.warning("Metadata operation failed ["+
                            e.getMessage()+"]. Fatal to this step ...");
                errorCount++;
            } catch (DeletedFragmentException e) { 
                log.fine("Deleted fragment for object " + oid + 
                            " will not be inserted into the extended cache."); 
            } catch (OAException oe) {
                log.warning("OA operation failed [" + oe.getMessage() + "]. Step failed.");
                errorCount++;
            } catch (Throwable e) {
                // FIXME - NEVER DO THIS - DON'T CATCH THROWABLE
                // CHANGED 10/03/06 TO AT LEAST LOG THE REASON - WHY ?
                errorCount++;
                log.log(Level.SEVERE,"Severe internal error in DataDoctor " + 
                           "failed to populate External cache with oid "+oid,e);
            } finally {
                if (frag != null) {
                    frag.close();
                }
            }
        }
    }
}
