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

import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMMException;

/**
 * QuorumThread: background thread that monitors which disks are active and
 * alerts CM for the purposes of Quorum
 *
 * @author Rob Wygand
 */

class QuorumThread implements Runnable {
    private static final long SLEEP_INTERVAL = 1000 * 5; // 5 sec

    private boolean terminate = false;
    private DiskMonitor diskMonitor = null;
    private Disk[] disks = null;
    private Thread myThread = null;
    private volatile int lastActiveCount = -1;
    private int retryLimit;

    private static final Logger logger =
        Logger.getLogger(QuorumThread.class.getName());

    QuorumThread (Disk[] diskArray, DiskMonitor parent, int tries) {
        disks = diskArray;
        diskMonitor = parent;
        lastActiveCount = -1;
        retryLimit = tries;
    }

    public void start() {
        myThread = new Thread(this);
        myThread.start();
    }

    public void run() {
        while (! terminate) {
            int activeCount = getActiveCount();

            if (activeCount != lastActiveCount)
                notifyCM(activeCount);

            lastActiveCount = activeCount;

            try {
                Thread.sleep(SLEEP_INTERVAL);
            } catch (InterruptedException ie) {
                if (logger.isLoggable (Level.FINE)) {
                    logger.fine("Sleep interrupted");
                }
            }
        }
        logger.info("Terminating.");
    }

    public void refresh() {
        myThread.interrupt();
    }

    private int getActiveCount() {
        int numDisks = 0;
        for (int i = 0; i < disks.length; i++) {
            Disk disk = disks[i];
            if (disk != null && disk.isEnabled())
                numDisks++;
        }
        return numDisks;
    }

    void stop() {
        terminate = true;
        myThread.interrupt();
    }

    private void notifyCM (int activeCount) {
        for (int i = 1; i <= retryLimit; i++) {
            try {
                CMM.getAPI().setActiveDiskCount (activeCount);
                logger.info("Notified CM disk count = " + activeCount);
                return;
            } catch (CMMException cme) {
                logger.log (Level.WARNING, "Try #" + i +
                            " notifying CMM of disk change:", cme);
            }
        }
        logger.severe("Disk notification failed!");
    }
}

