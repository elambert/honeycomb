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

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.delete.Constants;

public class LayoutClient {
    
    private static LayoutClient instance = null;
    public static final int NUM_MAP_IDS = 1024;

    private static Layout[] layouts = null;
    public static Disk[] disks = null;
    static DiskMask currentDiskMask = null;
    
    synchronized public static LayoutClient getInstance() {
        if (instance == null) {
            instance = new LayoutClient();
            initLayouts();
        }
        return instance;
    }

    private LayoutClient() {
    }
    
    public static void disableDisk(int num) throws ArchiveException {
        if (disks.length > num)
            disks[num].setDisabled();
        else
            throw new ArchiveException("Invalid disk number: " + num);
    }
    
    public static void enableDisk(int num) throws ArchiveException {
        if (disks.length > num)
            disks[num].setEnabled();
        else
            throw new ArchiveException("Invalid disk number: " + num);
    }

    public static int getLayoutMapId() {
        return (int)(Math.random() * NUM_MAP_IDS);
    }

    public Layout getLayoutForStore(int mapId) {
        return(layouts[mapId]);
    }

    public Layout getLayoutForRetrieve(int mapId) {
        return(layouts[mapId]);
    }
    
    /** Get current layout for a mapId */
    public Layout getCurrentLayout(int mapId) {
        return (layouts[mapId]);
    }

    public Layout getLayoutForSloshing (int mapId) {
        return (layouts[mapId]);
    }

    public Layout getLayoutForRecover(int mapId) {
        return getLayoutForRecover(mapId,
            // LayoutProxy.getCurrentDiskMask());
            null);
    }

    public Layout getLayoutForRecover(int mapId,
                                      DiskMask mask) {
        return(layouts[mapId]);
    }
    
    private static void initLayouts() {
        // Init disks first
        disks = new Disk[Constants.NB_DISKS];
        for (int i=0; i<Constants.NB_DISKS; i++) {
            disks[i] = new Disk(i);
        }

        // Init the disk mask
        currentDiskMask = new DiskMask();

        // Init the layouts
        layouts = new Layout[NUM_MAP_IDS];
        for (int i=0; i<NUM_MAP_IDS; i++) {
            layouts[i] = new Layout(i);
        }
    }

    public static boolean isMapIdValid(int layoutMapId) {
        if (layoutMapId < 0 || layoutMapId >= NUM_MAP_IDS) {
            return false;
        } else {
            return true;
        }
    }

    /* If we take available capacity into account when selecting
     * layouts, using consecutive mapids for extents of the same object 
     * might not be the best way. TBD in Capacity Planning design.
     */
    public static int getConsecutiveLayoutMapId(int mapId) {
        return (mapId + 1) % NUM_MAP_IDS;
    }
    public static int getPreviousLayoutMapId(int mapId) {
        int result = mapId-1;
        if(result < 0) {
            result = NUM_MAP_IDS-1;
        }
        return result;

    }
}
