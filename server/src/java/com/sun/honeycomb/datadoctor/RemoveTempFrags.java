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

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutConfig;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAException;


/**
 * Checks if old fragments exist in tmp directory,
 * and removes them (and sometimes corresponding data frags)
 * to clean up after a partial/failed store or recovery.
 *
 */
public class RemoveTempFrags implements Steppable {

    private static final long MILLIS_PER_HOUR = 1000 * 60 * 60;
    private static final long WAIT_WINDOW = 120000; // 2m

    private static long OK_TO_REMOVE_MS_SINCE_MODIFIED
        = MILLIS_PER_HOUR * 12;  // 12 hrs before we delete frags in tmp

    private String taskName;
    private DiskMask diskMask = null;
    private DiskId myDiskId;
    private boolean abortStep = false;
    private TaskLogger log;
    private int errorCount;
    private int NUM_DATA_FRAGS;
    private int NUM_PARITY_FRAGS;
    private int NUM_ALL_FRAGS;

    /**********************************************************************
     * constructor is no-args, real setup work done in init method
     **/
    public RemoveTempFrags() {
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public void init(String taskName, DiskId diskId) {

        this.taskName = taskName;
        this.myDiskId = diskId;
        this.log = new TaskLogger(RemoveTempFrags.class.getName(), taskName);
        this.NUM_DATA_FRAGS = OAClient.getInstance().getReliability().getDataFragCount();
        this.NUM_PARITY_FRAGS = OAClient.getInstance().getReliability().getRedundantFragCount();
        this.NUM_ALL_FRAGS = OAClient.getInstance().getReliability().getTotalFragCount();
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public String getName() { return taskName; }

    /**********************************************************************
     * @inherit javadoc
     **/
    public int getNumSteps() {
        return 1;       // only one step, all file in same directory
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public int getErrorCount() {
        return errorCount;
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public void resetErrorCount() {
        errorCount = 0;
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public void abortStep() { abortStep = true; }


    /**********************************************************************
     * @inherit javadoc
     **/
    synchronized public void newDiskMask(DiskMask newMask) {
        diskMask = newMask;
    }

    /**********************************************************************
     * Check whether all disks are online.
     * Chooses the lesser of CMM count and DiskProxy count (used by Layout)
     * because CMM gets updated first when disks leave and join.
     **/
    private boolean allDisksOnline() {
        // Get number of nodes in system from config.
        ClusterProperties cp = ClusterProperties.getInstance();
        String num_nodes = cp.getProperty("honeycomb.cell.num_nodes");

        if (num_nodes == null)
            throw new RuntimeException("honeycomb.cell.num_nodes not set in config.properties!");

        int totalDisks = (new Integer(num_nodes)).intValue() * LayoutConfig.DISKS_PER_NODE;

        Disk[] allDisks = DiskProxy.getClusterDisks();
        if (allDisks == null) {
            log.warning("DiskProxy returned null online disks array");
            return false;
        }

        int diskCount = 0;
        for (int i = 0; i < allDisks.length; i++) {
            if (allDisks[i] != null && allDisks[i].isEnabled()) {
                diskCount++;
            }
        }

        return (diskCount == totalDisks);
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    synchronized public void step(int stepNum) {
        ClusterProperties cp = ClusterProperties.getInstance();
        /*
         * RemoveTempFrags can decide to remove a partially stored object and 
         * this will lead to the deletion of all of the tmp fragments, this 
         * currently leads to a race between all of the tasks trying to delete
         * the same fragments. To solve this we will make all the tasks start
         * 2m after each other and that way we most certainly won't collide 
         * when cleaning up the same temporary object.
         */
        
        try {
            long waitWindow = cp.getPropertyAsLong(ConfigPropertyNames.PROP_REMOVE_TEMP_WAIT_WINDOW, WAIT_WINDOW);
            long sleepTime = (myDiskId.nodeId()-101)*waitWindow;
            log.info("Waiting " + sleepTime + "ms before commencing work on " +
                     myDiskId.toStringShort());
            Thread.sleep(sleepTime);
        } catch (InterruptedException ignore) {
        }
        
        log.info("Starting work on " + myDiskId.toStringShort());
        
        if (diskMask == null) {
            log.severe("DiskMask is null, aborting step " + stepNum);
            return;
        }

        File[] fragFiles = TaskFragUtils.listTmpFrags(myDiskId);
        if (fragFiles == null) {
            return;
        }

        // Walk files in this directory, and check if we should delete any.
        for (int i = 0; i < fragFiles.length; i++) {

            // check if we're supposed to stop
            if (abortStep) {
                log.info("aborting step "+stepNum);
                abortStep = false;
                return;
            }

            File fragFile = fragFiles[i];

            // Get last modification time and current time.
            long timeNow = System.currentTimeMillis();
            long lastModified = fragFile.lastModified();
            if (lastModified <= 0) {
                // Maybe an IO error.
                errorCount++;
                log.info("Invalid lastModified time for "
                         + fragFile.getAbsolutePath()
                         + ", skipping, errorCount: " + errorCount);
                continue;
            }

            // Delete object based on last modification time.
            if (OK_TO_REMOVE_MS_SINCE_MODIFIED != 0 &&
                (timeNow - lastModified) <= OK_TO_REMOVE_MS_SINCE_MODIFIED) {
                log.fine("Too early to delete " + fragFile.getAbsolutePath()
                         + ", skipping.");
                continue;
            }
            log.info("Attempting to delete tmp fragment file " + fragFile
                     + ", last modified on " + DataDoctor.timeToString(lastModified)
                     + ", current time is " + DataDoctor.timeToString(timeNow));
            NewObjectIdentifier oid = null;
            try {
                oid = TaskFragUtils.fileNameToOid(fragFile.getName());
            } catch (NotFragmentFileException e) {
                // Do not attempt to remove, this may be an NFS temp file
                errorCount++;
                log.warning("Skipping file " + fragFile.getAbsolutePath() + ": "
                            + e.getMessage() + ", errorCount: " + errorCount);
                continue;
            }
            try {
                processObject(oid);
            } catch (ArchiveException e) {
                errorCount++;
                log.severe(e.getMessage() + " errorCount: " + errorCount);
            }
        }
    }

    public void processObject(NewObjectIdentifier oid) throws ArchiveException
        {
            // Start by gathering data fragments and tmp fragments on current
            // layout
            ArrayList tmpFrags = new ArrayList();
            ArrayList dataFrags = new ArrayList();

            collectFrags(oid, dataFrags, tmpFrags);

            boolean finished = false;
            boolean crawlTmp = false;
            boolean crawlData = false;

            while (!finished) {

                long timeStart = System.currentTimeMillis();
                // All tmp frags found
                if (tmpFrags.size() == NUM_ALL_FRAGS) {
                    log.info("All tmp frags found for object: " + oid 
                           + " cleaning up tmp frags.");
                    deleteAll(oid, tmpFrags, true);
                    long timeEnd = System.currentTimeMillis();
                    long timeInterval = timeEnd - timeStart;
                    log.info("Cleaned up tmp frags of " + oid + " in " 
                            + timeInterval + " msec");
                    finished = true;
                    continue;
                }

                // Enough tmp frags to decide the object is incomplete.
                if (tmpFrags.size() > NUM_PARITY_FRAGS) {

                    if (dataFrags.size() >= NUM_DATA_FRAGS) {
                        // Object should not be complete
                        throw new ArchiveException(oid + " has " + tmpFrags.size()
                                                   + " tmp frags and " + dataFrags.size()
                                                   + " data frags, something is really wrong.");
                    }

                    // Rollback data fragments
                    if (dataFrags.size() > 0) {
                        // dataFrags somewhere below NUM_DATA_FRAGS and above 0
                        log.info(oid + " data fragments rolling back to tmp.");
                        tmpFrags.addAll(rollBackToTmp(oid, dataFrags));
                        continue;
                    }

                    // No data frags lets try to crawl to find them
                    if (dataFrags.size() == 0) {
                        if (!crawlData) {
                            if (allDisksOnline()) {
                                log.info(oid + "Crawling to find additional data fragments.");
                                dataFrags = crawlAndGetAllDataFrags(oid);
                                if (dataFrags == null) {
                                    log.info("failed to crawl for data, skipping: " + oid);
                                    return;
                                }
                                crawlData = true;
                                continue;
                            } else {
                                log.info("Not all disks are online, skipping: " + oid);
                                return;
                            }
                        } else {
                            log.info(oid + " has no more data frags and only: "
                                     + tmpFrags.size() + " tmp frags, cleaning up.");
                            deleteAll(oid, tmpFrags, true);
                            long timeEnd = System.currentTimeMillis();
                            long timeInterval = timeEnd - timeStart;
                            log.info("Cleaned up tmp frags of " + oid + " in " 
                                    + timeInterval + " msec");
                            finished = true;
                            continue;
                        }
                    }
                }

                if (tmpFrags.size() <= NUM_PARITY_FRAGS) {

                    if (dataFrags.size() >= NUM_DATA_FRAGS) {
                        log.info(oid + " is complete let's clean up these tmp frags.");
                        deleteAll(oid, tmpFrags, false);
                        long timeEnd = System.currentTimeMillis();
                        long timeInterval = timeEnd - timeStart;
                        log.info("Cleaned up tmp frags of " + oid + " in "
                                + timeInterval + " msec");
                        finished = true;
                        continue;
                    }

                    // Not enough data fragments to conclude the object is complete
                    // take easy route and try to find enough tmp frags to support
                    // decision, that object is in fact incomplete, and only then
                    // will I crawl for data to complete the object.
                    if (!crawlTmp) {
                        if (allDisksOnline()) {
                            log.info(oid + " seems incomplete, let's crawl to find tmp frags.");
                            tmpFrags = crawlAndGetAllTmpFrags(oid);
                            if (tmpFrags == null) {
                                log.info(oid+ " cannot be processed - fail to crawl");
                                return;
                            }
                            crawlTmp = true;
                            continue;
                        } else {
                            log.info(oid+ " can not be processed since not all disks are online");
                            return;
                        }
                    } else {

                        if (!crawlData) {
                            if (allDisksOnline()) {
                                log.info("Crawling to find additional data fragments for " + oid);
                                dataFrags = crawlAndGetAllDataFrags(oid);
                                if (dataFrags == null) {
                                    log.info(oid + " cannot be processed, failed to crawl");
                                    return;
                                }
                                crawlData = true;
                                continue;
                            } else {
                                log.info(oid + " can not be processed since not all disks are online");
                                return;
                            }
                        }

                        if (dataFrags.size() != 0){
                            // Object is incomplete and we've crawled tmp and
                            // were not capable of finding enough tmp files so this
                            // could be a data loss scenario:
                            throw new ArchiveException(oid  + " seems to be incomplete, but there are "
                                                       + "not enough tmp frags to support that decision."
                                                       + "Possible data loss detected.");
                        } else {
                            // No signs of the object found therefore this object
                            // was probably lost during the creation of tmp frags.
                            log.info(oid + " only resides on tmp directory, will eliminate tmp frags.");
                            deleteAll(oid, tmpFrags, true);
                            long timeEnd = System.currentTimeMillis();
                            long timeInterval = timeEnd - timeStart;
                            log.info("Cleaned up tmp frags of " + oid + " in "
                                    + timeInterval + " msec");
                            finished = true;
                            continue;
                        }
                    }
                }
            }
        }

    /**
     * Take an ArrayList of data fragments and roll them back into the tmp
     * directories on those same disks.
     *
     * @param files
     * @throws ArchiveException
     */
    private ArrayList rollBackToTmp(NewObjectIdentifier oid, ArrayList dataFiles)
        throws ArchiveException
        {
            Iterator iterator = dataFiles.iterator();
            ArrayList tempFiles = new ArrayList();
            while (iterator.hasNext()) {
                FragmentFile frag = (FragmentFile) iterator.next();
                try {
                    frag.rollbackCreate();
                    tempFiles.add(frag);
                } catch (OAException e) {
                    throw new ArchiveException("Unable to rollback: " +  oid,e);
                }
            }
            return tempFiles;
        }

    /**
     * Delete all File objects passed in the ArrayList and do necessary refCount
     * updates and mult-chunk operations if the objectIncomplete is true.
     *
     * @param files
     * @throws ArchiveException
     */
    private void deleteAll(NewObjectIdentifier oid, ArrayList files,
                           boolean objectIncomplete)
        throws ArchiveException {

        log.info("Deleting " + files.size() + " tmp frags for oid: " + oid );

        if (objectIncomplete && oid.getChunkNumber() > 0) {
            log.info("Current object is a non zero chunk of a multichunk object,"
                     + "we must first rollback all data frags for the previous"
                     + "chunk before deleting.");

            int prevMapID = LayoutClient.getPreviousLayoutMapId(oid.getLayoutMapId());
            NewObjectIdentifier prevOID = new NewObjectIdentifier(oid.getUID(),
                                                                  prevMapID,
                                                                  oid.getObjectType(),
                                                                  oid.getChunkNumber() - 1,
                                                                  oid.getRuleId(),
                                                                  oid.getSilolocation());

            log.info("Searching for data frags of : " + prevOID);
            ArrayList dataFiles = new ArrayList();
            collectFrags(prevOID, dataFiles, null);

            if (dataFiles.size() == 0) {
                log.warning("Can't find data frags for previous chunk of oid "
                            + prevOID + ", Skipping rollback.");
                if (!allDisksOnline()) {
                    log.warning(oid + " not all disks are online - skip the delete");
                    return;
                }
            } else {
                ArrayList tmpFiles = rollBackToTmp(prevOID, dataFiles);
                if (tmpFiles.size() == 0) {
                    log.warning("Couldn't rollback previous chunk for oid: " + oid
                                + " can't delete current tmp frags, Skipping rollback.");
                    return;
                }
            }
        }

        if (objectIncomplete && oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE) {
            // Since it is a metadata object we must decrement the refCount on
            // data object.
            log.info("Metadata object found must decrement refCount for linkoid.");

            // Try to find a tmp frag that can be used to read back fragment
            // file information on linkoid and others.
            Iterator iterator = files.iterator();
            int errors = 0;

            while (iterator.hasNext()) {
                FragmentFile frag = (FragmentFile) iterator.next();

                try {
                    frag.readSystemMetadata();
                    frag.deleteRefFromReferee(System.currentTimeMillis());
                } catch (Throwable t) {
                    errors++;
                }
            }

            if (errors > OAClient.getInstance().getReliability().getRedundantFragCount()) {
                log.warning("Unable to dec refCount for object: " + oid);
            }
        }

        Iterator iterator = files.iterator();
        while (iterator.hasNext()) {
            FragmentFile frag = (FragmentFile) iterator.next();
            log.info("Deleting tmp fragment " + frag);
            if (!frag.remove())
                throw new ArchiveException("Problem deleting file: " + frag);
        }

        if (objectIncomplete && oid.getChunkNumber() > 0) {
            log.info("Current object is a non zero chunk of a multichunk object,"
                     + "removing tmp frags for previous chunk.");
            int prevMapID = LayoutClient.getInstance().getPreviousLayoutMapId(oid.getLayoutMapId());
            NewObjectIdentifier prevOID = new NewObjectIdentifier(oid.getUID(),
                                                                  prevMapID,
                                                                  oid.getObjectType(),
                                                                  oid.getChunkNumber() - 1,
                                                                  oid.getRuleId(),
                                                                  oid.getSilolocation());
            processObject(prevOID);
        }
    }

    /*
     * Collect all available frags for the given oid.
     * The fragment is added to the correct array list depending if it is
     * committed on stable storage or not.
     */
    private void collectFrags(NewObjectIdentifier oid,
                              ArrayList dataFrags,
                              ArrayList tmpFrags)
        {
            LayoutClient lc = LayoutClient.getInstance();
            int mapId = oid.getLayoutMapId();
            Layout layout = lc.getCurrentLayout(mapId);

            for (int i = 0; i < layout.size(); i++) {
                Disk disk = layout.getDisk(i);

                // disk is null during grace period
                if (disk == null) {
                    continue;
                }
                FragmentFile frag = new FragmentFile(oid, i, disk);
                if (frag.exists()) {
                    if (dataFrags != null) {
                        dataFrags.add(frag);
                    }
                } else if (frag.checkTmp()) {
                    if (tmpFrags != null) {
                        tmpFrags.add(frag);
                    }
                }
            }
        }

    /*
     * Crawl all available disks and find all of the tmp fragments that can be
     * found for the specified oid.
     * @param oid
     * @return ArrayList of all available data fragments for this oid.
     */
    private ArrayList crawlAndGetAllTmpFrags(NewObjectIdentifier oid) {

        ArrayList res = new ArrayList();
        Disk[] allDisks = DiskProxy.getClusterDisks();

        for (int d = 0; d < allDisks.length; d++) {
            Disk disk = allDisks[d];
            if (disk == null) {
                // all disk must be online
                return null;
            }
            if (!disk.isEnabled()) {
                // disk must be enabled
                return null;
            }
            String[] frags = TaskFragUtils.getTmpFrags(oid, disk.getId());
            if (frags == null) {
                return null;
            }
            for (int i = 0; i < frags.length; i++) {
                int fragid = -1;
                try {
                    fragid = TaskFragUtils.extractFragId(frags[i]);
                } catch (NotFragmentFileException e) {
                    log.warning("Skipping file: " + frags[i] + ": " + e.getMessage());
                    continue;
                }
                res.add(new FragmentFile(oid, fragid, disk));
            }
        }
        return res;
    }

    /*
     * Crawl all available disks and find all of the data fragments that can be
     * found for the specified oid.
     * @param oid
     * @return ArrayList of all available data fragments for this oid.
     */
    private ArrayList crawlAndGetAllDataFrags(NewObjectIdentifier oid) {

        ArrayList res = new ArrayList();
        Disk[] allDisks = DiskProxy.getClusterDisks();

        for (int d = 0; d < allDisks.length; d++) {
            Disk disk = allDisks[d];
            if (disk == null) {
                // all disk must be online
                return null;
            }
            if (!disk.isEnabled()) {
                // disk must be enabled
                return null;
            }
            String[] frags = TaskFragUtils.getDataFrags(oid, disk.getId());
            if (frags == null) {
                return null;
            }
            for (int i = 0; i < frags.length; i++) {
                int fragid = -1;
                try {
                    fragid = TaskFragUtils.extractFragId(frags[i]);
                } catch (NotFragmentFileException e) {
                    log.warning("Skipping file: " + frags[i] + ": " + e.getMessage());
                    continue;
                }
                res.add(new FragmentFile(oid, fragid, disk));
            }
        }
        return res;
    }

    public static long getRemovalTime() {
        return OK_TO_REMOVE_MS_SINCE_MODIFIED;
    }

    public static void setRemovalTime(long removalTime) {
        OK_TO_REMOVE_MS_SINCE_MODIFIED = removalTime;
    }
}
