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



package com.sun.honeycomb.diskmonitor;

import java.io.File;
import java.io.IOException;

import java.util.Map;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.oa.OAServerIntf;
import com.sun.honeycomb.util.sysdep.DiskOps;

/**
 * WipeThread: background thread for wipe
 *
 * @author Shamim Mohamed
 */

class WipeThread implements Runnable {
    private static final Logger logger =
        Logger.getLogger(WipeThread.class.getName());

    private DiskMonitor diskMonitor = null;
    private Disk[] disks = null;
    private DiskOps diskOps = null;
    private int fsType;

    private Map currentMounts = null;

    // For operations we can re-try (e.g. umount)
    private static final long SHORT_DELAY = 2000L; // 2 sec.
    private static final int NUM_WAITS = 5;

    WipeThread(Disk[] diskArray, DiskMonitor parent, DiskOps ops, int fsType) {
        disks = diskArray;
        diskMonitor = parent;
        diskOps = ops;
        this.fsType = fsType;

        // Tell platform service to shut down all NFS mounts	 
        try {	 
            OAServerIntf oa = OAServerIntf.Proxy.getLocalAPI();	 
            if (oa != null) {	 
                oa.closeAllDisks();	 
            }	 
        } catch (Exception e) {	 
            logger.log(Level.WARNING,	 
              "Problem getting platform to terminate NFS", e);	 
        }

        //
        // Start by disabling all disks. 
        //
        for (int i = 0; i < disks.length; i++) {
            Disk disk = disks[i];
            if (disk != null && disk.isEnabled()) {
                try {
                    diskMonitor.dismount(disk.getId());
                } catch (Exception e) {
                    logger.log(Level.FINE, "Couldn't unmount " + disk, e);
                }
            }
        }
    }

    public void start() {
        new Thread(this).start();
    }

    public void run() {

        // Force unmount of any disks still mounted
        if (!unmountAll())
            throw new RuntimeException("Couldn't unmount all disks!");

        try {
            currentMounts = diskOps.getCurrentMounts();
            String msg = "Re-initializing; current mounts:";
            for (Iterator i = currentMounts.keySet().iterator(); i.hasNext();){
                String dev = (String) i.next();
                String mountPoint = (String) currentMounts.get(dev);
                msg += " \"" + dev + "\"->\"" + mountPoint + "\"";
            }
            logger.info(msg);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't get current mounts", e);
        }
        diskMonitor.reInitDisks();
    }

    private boolean unmountAll() {
        // We need to make sure nothing's still mounted

        boolean rc = true;

        try {
            currentMounts = diskOps.getCurrentMounts();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't get current mounts", e);
        }

        for (int i = 0; i < disks.length; i++) {
            if (!unmount(disks[i])) {
                logger.severe("Forced unmount of " + disks[i].getPath() +
                              " failed");
                rc = false;
            }
        }
        return rc;
    }

    private boolean unmount(Disk disk) {
        if (disk == null)
            return true;

        String dev = disk.getDevice();
        String mountPoint = disk.getPath();

        if (currentMounts.get(dev) == null)
            return true;

        if (logger.isLoggable(Level.INFO))
            logger.info("Forcibly unmounting " + disk.getId());

        for (int j = 0; j < NUM_WAITS; j++) {
            try {
                diskOps.umount(mountPoint, fsType, true);
                return true;
            } catch (Exception e) {
                logger.log(Level.INFO,
                           "unmount " + mountPoint + " failed (try " + j + ")",
                           e);
            }
            try {
                Thread.sleep(SHORT_DELAY);
            } catch (Exception ignored) { }
        }
        return false;
    }

}
