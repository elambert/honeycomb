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
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.oa.DeletedFragmentException;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.common.NewObjectIdentifier;

import java.util.ArrayList;
import java.util.logging.Level;

/** 
 * Checks if fragments on this disk are in correct location, according
 * to the given disk mask.  If not, AND if the fragment exists in the
 * correct location, remove the duplicate from this disk.
 */
public class RemoveDupFrags implements Steppable {

    private String taskName;
    private DiskMask diskMask = null;
    private DiskId myDiskId;
    private boolean abortStep = false;
    private TaskLogger log;
    private int errorCount;
    private static final LayoutClient lc = LayoutClient.getInstance();

    /**********************************************************************
     * constructor is no-args, real setup work done in init method
     **/
    public RemoveDupFrags() {
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    public void init(String taskName, DiskId diskId) {

        this.taskName = taskName;
        this.myDiskId = diskId;
        this.log = new TaskLogger(RemoveDupFrags.class.getName(), taskName);
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
    synchronized public void newDiskMask(DiskMask newMask) {
        diskMask = newMask;
    }

    /**********************************************************************
     * @inherit javadoc
     **/
    synchronized public void step(int stepNum) {

        if (diskMask == null) {
            log.severe("DiskMask is null, aborting step "+stepNum);
            return;
        }

        int mapId = stepNum;
        String[] fragList = TaskFragUtils.readMap(myDiskId, mapId);
        if (fragList == null) {
            log.severe("failed to read map " + mapId + " on disk " + myDiskId);
            return;
        }
        
        // walk frags in this map, checking frag number
        for (int i=0; i < fragList.length; i++) {

            // check if we're supposed to stop
            if (abortStep) {
                log.info("aborting step "+stepNum);
                abortStep = false;
                return;
            }

            String f = fragList[i];
            NewObjectIdentifier oid = null;
            int fragId = -1;
            try {
                oid = TaskFragUtils.fileNameToOid(f);
                fragId = TaskFragUtils.extractFragId(f);
            } catch (NotFragmentFileException e) {
                log.warning("Skipping file: " + f + ": " + e.getMessage());
                continue;
            }

            FragmentFile frag = null;
            try {
                // if fragment has wrong mapId, ignore but log error
                if (oid.getLayoutMapId() != mapId) {
                    log.severe("frag "+ f +" with mapId "+
                               oid.getLayoutMapId()+" but expected mapId "+mapId);
                    continue;
                }

                // if we expect this fragment on this disk, ignore it
                int expectFrag = lc.getFragmentId(mapId, myDiskId, diskMask);
                if (expectFrag == fragId) {
                    continue;
                }

                // fragment doesn't belong on this disk!  Where should it be?
                DiskId otherDisk = lc.diskIdForFrag(mapId, fragId, diskMask); 

                // if other disk is down, we cannot check if frag is there
                if (diskMask.isOffline(otherDisk)) {
                    log.fine(otherDisk.toStringShort()+" is offline"+ 
                             " so cannot check if "+f+" is recovered there");
                    continue;
                }

                // get same fragment file on other disk
                FragmentFile otherFrag = 
                    new FragmentFile(oid, fragId, DiskProxy.getDisk(otherDisk));

                // if frag not duplicated on other disk, DO NOT remove it
                if (!otherFrag.exists()) {
                    log.fine(f + " doesn't belong here, but "+
                             "not yet recovered to "+otherDisk.toStringShort());
                    continue;
                }
                
                // If we do not open the FragmentFile then we can never know 
                // if it was deleted lower in the code in order to know if 
                // we need to replay a delete
                Disk myDisk = DiskProxy.getDisk(myDiskId);
                frag = new FragmentFile(oid, fragId, myDisk);
                try {
                    frag.open();
                } catch (DeletedFragmentException e) { 
                    // if it's a DeletedFragmentException that is fine 
                    // any other exception and we skip this fragment. 
                    // If it's corrupted then RecoverLostFrags 
                    // should fix it before we do anything with it
                } 

                // if this frag is a deleted stub, must verify that other
                // frag is also marked deleted BEFORE we remove this one.
                if (frag.isDeleted()) {

                    // re-do delete (if frag is a data obeject, this method
                    // returns without doing anything, only re-do if md frag)
                    try {
                        frag.deleteRefFromReferee(System.currentTimeMillis());
                    } catch (OAException oae) {
                        // log warning, but still GC this frag
                        log.warning("Failed ref delete redo ");
                    }
               
                    // TODO: this error should be counted at the task level once we start
                    //       reporting errors at the task level 
                    
                    // if cannot delete other frag, don't delete this one
                    if (!deleteOtherFrag(otherFrag)) {
                        log.warning("Cannot delete " + otherFrag);
                        continue;
                    }
                }

                // safe to remove this fragment
                long timeStart = System.currentTimeMillis();
                boolean deleteOK = TaskFragUtils.removeFragment(frag);
                long timeEnd = System.currentTimeMillis();
                long timeInterval = timeEnd - timeStart;
              
                if (deleteOK) {
                    log.info("removed "+ frag +
                             " since it exists on "+ otherDisk.toStringShort() +
                             " in " + timeInterval + " msec");
                } else {
                    log.info("Failed to delete "+ frag + " from filesystem");
                }
            }  catch (Throwable e) {
                errorCount++;
                log.log(Level.SEVERE, "Severe internal error in DataDoctor - "+
                           "failed to remove duplicate frag  " + f, e);
            } finally {
                if (frag != null) {
                    frag.close();
                }
            }

            // "A file that big
            //  It might be very useful
            //  But now it is gone"
            //      --David J Liszewski (02/1998)
            //      http://www.loyalty.org/~schoen/haiku.html
            //      http://archive.salon.com/21st/chal/1998/02/10chal2.html

        } 

    }

    /**********************************************************************
     * delete the other fragment, return true if successful
     **/
    private boolean deleteOtherFrag(FragmentFile otherFrag) {
        try {
            otherFrag.delete(System.currentTimeMillis());
        } catch (FragmentNotFoundException fnfe) {
            // This is really wierd b/c exists() just passed above!
            // No problem though, just return that the delete failed
            return false;
        } catch (OAException oae) {
            // delete failed
            return false;
        }
        return true;
    }
}
