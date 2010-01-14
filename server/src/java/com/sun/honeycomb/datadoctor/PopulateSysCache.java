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

import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.oa.DeletedFragmentException;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.emd.EMDClient;
import com.sun.honeycomb.emd.MDManagedService;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.MetadataClient.QueryResult;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDDiskAbstraction;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.common.Cookie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;


/** 
 * Populates the local system cache for this disk by inserting all
 * fragments that are not aready there.
 */
public class PopulateSysCache implements Steppable {

    private String taskName;
    private DiskMask diskMask = null;
    private DiskId myDiskId;
    private boolean abortStep = false;
    private int startingMap;
    private TaskLogger log;

    private static int MAX_FETCH_SIZE = 128;

    private static MetadataClient mdClient = MetadataClient.getInstance(); 
    private static OAClient oaClient = OAClient.getInstance(); 
    private static final LayoutClient lc = LayoutClient.getInstance();
    private int errorCount;

    protected static final Logger LOG = 
        Logger.getLogger(PopulateSysCache.class.getName());

    /**********************************************************************
     * constructor is no-args, real setup work done in init method
     **/
    public PopulateSysCache() {
        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);        
        int localNodeId = (proxy.nodeId() - 100);
        assert(localNodeId >= 0 && localNodeId < CMM.MAX_NODES);        
        startingMap =  (localNodeId * (getNumSteps() - 1)) / CMM.MAX_NODES;
    }

    /** initialize this instance */
    public void init(String taskName, DiskId diskId) {

        this.taskName = taskName;
        this.myDiskId = diskId;
        this.log = new TaskLogger(PopulateSysCache.class.getName(), 
                                  taskName);
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
    public void abortStep() { abortStep = true; }

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
     *
     * synchronized to prevent mask change in middle of step
     **/
    synchronized public void newDiskMask(DiskMask newMask) {
        diskMask = newMask;
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    synchronized public void step(int stepNum) {
        long start, stop;
        
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

        // We need to make sure the cache is up before we start doing anything...
        // Sleep and retry until the cache comes up
        int cacheWaitSteps = 0;
        int cacheWaitSleep = 10;
        int cacheMessageInterval = 60;
        MDManagedService.Proxy proxy = 
                     MDManagedService.Proxy.getProxy(ServiceManager.LOCAL_NODE);

        if (proxy == null) { 
            log.warning("MDManagedService proxy is null, aborting step.");
            errorCount++;
            return;
        }
        
        while (!proxy.isCacheRunning(myDisk)) {
            
            proxy = MDManagedService.Proxy.getProxy(ServiceManager.LOCAL_NODE);

            if (proxy == null) {
                log.warning("MDManagedService proxy is null, aborting step.");
                errorCount++;
                return;
            }
            
            // Print the wait message periodically
            if (((cacheWaitSleep * cacheWaitSteps) % cacheMessageInterval) == 0) {
                log.info("System metadata cache is not yet running on " + myDisk.diskIndex());
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

        start = System.currentTimeMillis();
        /*
         * Create list of fragments in a given mapID
         */
        int mapId = (stepNum + startingMap) % getNumSteps();
        String[] fragList = TaskFragUtils.readMap(myDiskId, mapId);
        if (fragList == null) {
            log.severe("failed to read map " + mapId + " on disk " + myDiskId);
            errorCount++;
            return;
        }
        stop = System.currentTimeMillis();
        
        if (log.isLoggable(Level.FINE))
            log.fine("Time to list all files on " + myDiskId.toStringShort() + 
                     " of mapID " + mapId + " is " + (stop-start) + "ms.");

        
        
        /*
         * Query the system cache for all of the entires in the current mapID.
         */
        start = System.currentTimeMillis();
        String query = SystemCacheConstants.SYSTEM_QUERY_GETOBJECTS + " " + mapId;
        ArrayList results = new ArrayList();
      
	QueryResult result = null;
        Cookie cookie = null;

    /*
     * XXX:
     *     We're only doing multiple queries instead of getting all of the results
     *     at once because we don't want to lock out all of the setMetadata 
     *     operations till we've collected the results. So by doing smaller queries
     *     and using the cookies we're able to not affect the performance as greatly
     *     as we would we if did one big query for all of the objects in this mapID.
     */
	do { 
		try {
		    /*
		     * We have to be able to hold all of the results in memory. For a 
		     * cluster with 100 Million objects we would have about 10,000 
		     * entries in any give mapId. Now with that in mind and knowing that
		     * we need about 64bytes per OID (string size) and we have two lists
		     * one from the filesystem and another from the cache that is
		     * 
		     * 10,000*64bytes = 640KB round up to 1MB * 2 (two lists) = 2MB
		     * 
		     * 1 PopulateSysCache Thread per disk so 2MB * 4 = 8MB of heap would
		     * be necessary. 
		     * 
		     */
		    result = mdClient.query(CacheClientInterface.SYSTEM_CACHE, 
					                query, 
                                    cookie,
                                    MAX_FETCH_SIZE,
                                    myDisk);
            results.addAll(result.results);
		    cookie = result.cookie;
		} catch (ArchiveException e) {
		    errorCount++;
		    LOG.log(Level.SEVERE,
			    "Unable to query system cache for map: " + mapId,
			    e);
		    return;
		}
	} while (result.results.size() != 0);
        stop = System.currentTimeMillis();

        if (log.isLoggable(Level.FINE))
            LOG.fine("Time to query cache on " + myDiskId.toStringShort() + 
                     " for mapID " + mapId + " is " + (stop-start) + "ms.");
        
        /* 
         * Check that everything that is on disk is also in the cache.
         */
       
        ArrayList partial = null;
        
        // walk frags in this directory, checking frag number
        for (int i=0; i < fragList.length; i++) {
            
            // check if we're supposed to stop
            if (abortStep) {
                log.info("aborting step "+stepNum);
                abortStep = false;
                errorCount++;
                return;
            }
            
            if (!proxy.isCacheRunning(myDisk))  {
                log.info("aborting step " + stepNum + 
                         " because cache went away :(.");
                abortStep = false;
                errorCount++;
                return;
            }
            
            String f = fragList[i];
            int fragId = -1;
            NewObjectIdentifier oid = null;
            try {            
                oid = TaskFragUtils.fileNameToOid(f);
                fragId = TaskFragUtils.extractFragId(f);
            } catch (NotFragmentFileException e) {
                log.warning("Skipping file: " + f + ": " + e.getMessage());
                continue;
            }

            // only handle chunk 0 of any object
            if (oid.getChunkNumber() != 0)
                continue;
            
            // if fragment has wrong mapId, ignore but log error
            if (oid.getLayoutMapId() != mapId) {
                log.severe("frag " + f + " with mapId " + oid.getLayoutMapId()
                           + " but expected mapId " + mapId);
                continue;
            }
  
            boolean usesDisk = mdClient.usesDisk(oid.getLayoutMapId(), 
                                                 CacheClientInterface.SYSTEM_CACHE, 
                                                 myDisk);
            if (!usesDisk)
                continue;
        
            /*
             * If it is not in the list of oids that came back from the system
             * cache query then insert it.
             */
            MDHit hit = new MDHit(oid,null);
            boolean wasthere = results.remove(hit);
            if (!wasthere) {
                try {
                    /*
                     * Before inserting we must make sure this fragment does not
                     * belong to a partial store. So we check that it does not
                     * belong to the partial ArrayList previously calculated.
                     * 
                     * Now how does this work ? well the fragments on disk are 
                     * first listed and only then we list the ones in tmp. So 
                     * lets say there's a store in progress and that we see the
                     * freshly renamed data fragment but the rest of the store 
                     * fails. Then there will be 3 tmp fragments that will allow
                     * us to not make the right decision. Now what if the store
                     * succeeds ? well then it would have been populated into 
                     * the system cache by the protocol layer during the store.
                     */
                    if (partial == null)  {
                        partial = getAllPartialTmps(); 
                         
                        if (partial == null) { 
				            log.severe("unable to get listing of all tmp fragments in the " + 
				                       "cluster and can't make a clear decision on which " + 
				                       "objects are partial stores or not.");
                            errorCount++;
                            return;
                        }
                    }
        
                    if (!objectIsPartial(oid,partial))
                        cacheInsert(myDisk,oid,fragId);
                    else 
                        log.info("oid " + oid + " systemMD not found in " + 
                                 myDisk.getId().toStringShort() + 
                                 " system cache, NOT inserting because it" +
                                 " is a partial store or a store in course.");
                    
                } catch (Throwable e) {
                    errorCount++;
                    log.log(Level.SEVERE,"Severe internal error in DataDoctor - "
                            + "failed to populate System cache with file " + f,e);
                }
            } 
        }
        
        /*
         * Now all of the entries still in the ArrayList are the ones that we
         * need to remove from the system cache because they are stale entries
         */
        if (results.size() != 0 && LOG.isLoggable(Level.FINE))
            LOG.fine("Removing stale entries: " + results.size());
        
        Iterator iter = results.iterator();
        String cacheId = CacheClientInterface.SYSTEM_CACHE;
        
        while (iter.hasNext()) { 

            if (abortStep) {
                log.info("aborting step "+stepNum);
                abortStep = false;
                errorCount++;
                return;
            }
            
            MDHit hit = (MDHit) iter.next();
            NewObjectIdentifier oid = NewObjectIdentifier.fromHexString(hit.getOid());
              
            /*
             * If this is not the right disk for this entry then verify that the
             * disk that should have this entry has it already otherwise wait
             * till that has happened before removing this entry. This logic is 
             * absolutely necessary for restore while PopulateSysCache is doing
             * it's work.
             */
            if (!mdClient.usesDisk(mapId, cacheId, myDisk)) {
                ArrayList disks = MDDiskAbstraction.getInstance((byte)0)
                                         .getUsedDisksFromMapId(mapId, cacheId);
              
                boolean foundOnEveryCache = true;
                for (int d = 0; d < disks.size(); d++) {
	                Disk shouldBe = (Disk) disks.get(d);

	                query = SystemCacheConstants.SYSTEM_QUERY_CHECKOID  + 
                            " " + oid.toExternalHexString() + " false";
	                QueryResult qResult = null;
	                try {
	                    qResult = mdClient.query(cacheId, query, -1, shouldBe);
	                } catch (ArchiveException e) {
                        foundOnEveryCache = false;
	                    errorCount++;
			            log.log(Level.SEVERE,
			                    "Unable to remove metadata from cache for " + 
	                            hit.getOid(),
			                    e);
	                }
                    
                    /*
                     * if it wasn't found on one of the caches then wait before
                     * removing this stale entry.
                     */
	                if (qResult == null || qResult.results.size() == 0) { 
	                    foundOnEveryCache = false;
                    }
                }
	               
                if (foundOnEveryCache) {
		            LOG.info("Removing stale entry for object " + hit.getOid() + 
		                     " from cache " + myDisk.getId().toStringShort());
		            try {
		                mdClient.removeMetadata(oid, 
	                                            CacheClientInterface.SYSTEM_CACHE, 
	                                            myDisk);
		            } catch (EMDException e) {
		                errorCount++;
		                log.log(Level.SEVERE,
		                        "Unable to remove metadata from cache for " + hit.getOid(),
		                        e);
		            }
                } else if (LOG.isLoggable(Level.FINE))
                    LOG.fine("Stale entry not found on all other disks, so we won't remove this stale entry just yet.");
            }
        }
    } 
    
    /**
     * Check if an object is partial and be certain to apply the correct logic 
     * in the case of multichunk objects. Multichunk objects can have a few of 
     * the first chunks in data slices and only the last chunk(s) in tmp and 
     * therefore comparing the OIDs directly is not sufficient.
     * 
     * @param oid
     * @param partial
     * @return
     */
    private boolean objectIsPartial(NewObjectIdentifier oid, ArrayList partial) {
        Iterator iter = partial.iterator();
        
        while (iter.hasNext()) { 
            NewObjectIdentifier aux = (NewObjectIdentifier)iter.next();
           
            if (aux.equals(oid)) { 
                return true;
            } else if (aux.getChunkNumber() != 0) { 
                /*
                 * if it's a multichunk then we have to compare the UIDs of the
                 * objects to figure out that it is in fact a different chunk
                 * of this Object that is incomplete and therefore that renders
                 * the whole object incomplete.
                 * 
                 */
                 if (aux.getUID().equals(oid.getUID()))
                     return true;
            }
        }
        
        return false;
    }

    /**********************************************************************
     *
     * 
     * @throws OAException 
     * @throws DeletedFragmentException 
     * @throws FragmentNotFoundException 
     * @throws ArchiveException 
     **/
    void cacheInsert(Disk targetDisk, NewObjectIdentifier oid, int fragId) 
         throws FragmentNotFoundException, DeletedFragmentException, 
                OAException, ArchiveException {

        log.info("oid " + oid + " systemMD not found in " + 
                 targetDisk.getId().toStringShort() + 
                 " system cache, inserting");

        long timeStart = System.currentTimeMillis(); 
        FragmentFile frag = null;
        try {
            frag = new FragmentFile(oid, fragId, targetDisk);
            SystemMetadata sm = null;
            try {
                sm = frag.readSystemMetadata();
            } catch (DeletedFragmentException e) { 
                /*
                 * Deleted fragments still contain a system metadata record and
                 * should be populated into the system cache so they can be 
                 * correctly backed up.
                 */
                sm = frag.getSystemMetadata();
            }
            
            if (sm.getSize() == OAClient.MORE_CHUNKS) { 
                /*
                 * multichunk objects we need to read the system record of the
                 * object. This has a higher cost but currently no easy way 
                 * to get the system record that contains the right information
                 * from 
                 */
                sm = oaClient.getSystemMetadata(oid, true, false);
            }

            mdClient.setSysMetadata(sm, targetDisk);
            long timeEnd = System.currentTimeMillis();
            long timeInterval = timeEnd - timeStart;
            
            log.info("oid " + oid + " systemMD inserted in " +
                     + timeInterval + " msec");

            /*
             * No reason to handle fef code here for the time being because of 
             * bug 6554027 - hide retention feature
             *  
             */
            // For syscache repop we always lookup the compliance
            // mtime when calculating the atime in the maindb.
            /*
            long extensionModifiedTime = oaClient.getExtensionModifiedTime(oid);
            sm.setExtensionModifiedTime(extensionModifiedTime);


            String legalHolds[] = oaClient.getLegalHolds(oid);
            if (legalHolds != null) {
                for (int i = 0; i < legalHolds.length; i++) {
                    mdClient.addLegalHold(oid, legalHolds[i], targetDisk);
                }
            }
            */
        } finally { 
            if (frag != null) 
                frag.close();
        }
    }
    
    
    private ArrayList getAllPartialTmps() { 
        long start = System.currentTimeMillis();
        /*
         * Create list of all tmp fragments in the cluster. If we were unable
         * to get a complete view then return null because that is an error.
         *
         */
        ArrayList alltmps = TaskFragUtils.getClusterTmpFrags();
        if (alltmps == null) { 
            return null;
        }
        
        Iterator iter = alltmps.iterator();
        HashMap aux = new HashMap();
        /*
         * partial list will be used to make sure that the current oid about 
         * to be repopulated is of a completed store or not.
         */
        ArrayList partial = new ArrayList();
      
        /* 
         * Count the number of tmps for each oid and if it's >= 3 then it's a 
         * partial object and we shouldn't populate it into the system cache.
         */
        while(iter.hasNext()) { 
            String frag = (String)iter.next();
            NewObjectIdentifier oid = null;
            try {
                oid = TaskFragUtils.fileNameToOid(frag);
            } catch (NotFragmentFileException ignore) {
                // skip the ones that are not fragments.
                continue;
            }
            
            Long reps = (Long)aux.get(oid);
            
            if (reps != null) {
                reps++;
                aux.put(oid, reps);
                
                /* 
                 * if we have 3 tmps then this is a partial store.
                 */
                if (reps == 3) 
                    partial.add(oid);
            } else { 
                reps = new Long(1);
                aux.put(oid,reps);
            }
        }
        long stop = System.currentTimeMillis();
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("Time to list full tmp listing: " + (stop-start) + "ms."); 
        
        return partial;
    }
}
