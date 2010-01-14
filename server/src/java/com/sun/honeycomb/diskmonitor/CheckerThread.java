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
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import java.util.Random;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.common.ConfigPropertyNames;

/**
 * CheckerThread: background thread that keeps testing disk read/write
 *
 * @author Shamim Mohamed
 */

class CheckerThread implements Runnable {
    private static final long DEFAULT_SLEEP_INTERVAL = 60000; // 1 min.

    private static final int BUFFER_SIZE = 8192;

    private static final Logger logger =
        Logger.getLogger(CheckerThread.class.getName());

    private ClusterProperties config = null;
    private boolean terminate = false;
    private DiskMonitor diskMonitor = null;
    private Disk[] disks = null;
    private long sleepInterval = DEFAULT_SLEEP_INTERVAL;
    private Random rand;

    private Thread theThread = null;

    CheckerThread(ClusterProperties conf, Disk[] diskArray,
                  DiskMonitor parent) {
        config = conf;
        disks = diskArray;
        diskMonitor = parent;

        String p = config.getProperty(
            ConfigPropertyNames.PROP_DISK_CHECK_INTERVAL);
        if (p != null)
            try {
                sleepInterval = Long.parseLong(p) * 1000;
            }
            catch (NumberFormatException e) {
                long l = sleepInterval/1000;
                logger.warning("Couldn't parse " +
                    ConfigPropertyNames.PROP_DISK_CHECK_INTERVAL +
                    " = " + p + "; using default = " + l + " sec.");
            }

        rand = new Random(System.currentTimeMillis());
    }

    public void start() {
        theThread = new Thread(this);
        theThread.start();
    }

    public void run() {
        while (!terminate) {
            for (int i = 0; i < disks.length; i++) {
                Disk disk = disks[i];
                boolean ok = false;
                try {
                    ok = check(disk);
                }
                catch (IOException e) {
                    logger.log(Level.WARNING,
                               "Checking disk " + disk.getId(), e);
                }
                if (!ok)
                    diskMonitor.reportError(disk);
            }

            try {
                Thread.sleep(sleepInterval);
            } catch (InterruptedException ie) {
                logger.fine("Sleep interrupted");
            }
        }
        theThread = null;
        logger.info("Terminating.");
    }

    void stop() {
        terminate = true;
        if (theThread != null)
            theThread.interrupt();
    }

    private boolean check(Disk disk) throws IOException {
        if (disk == null)
            return true;

        // Write a file, and read it back

        String baseDir = disk.getPath();

        // Safeguard: make sure it's a disk there
        File f = new File(baseDir, ".disklabel");
        if (!f.exists()) {
            logger.fine("Not checking " + disk + ": disabled or broken");
            return true;
        }

        // Generate some data to write
        byte[] data = getRandomData(BUFFER_SIZE);

        // This is the file
        f = new File(baseDir, "check-file");

        DataOutputStream dos = null;
        DataInputStream dis = null;
        try {
            dos = new DataOutputStream(new FileOutputStream(f));
            dos.write(data);
            dos.close();
            dos = null;

            dis = new DataInputStream(new FileInputStream(f));

            int len = (int) f.length();
            byte[] buffer = new byte[len];
            dis.readFully(buffer);

            // Delete it
            if (!f.delete())
                throw new IOException("Couldn't delete " + f.getPath());

            // Check the read-in data
            if (len != data.length)
                return false;
            for (int i = 0; i < len; i++)
                if (data[i] != buffer[i])
                    return false;

            return true;
        }
        finally {
            if (dos != null)
                dos.close();
            if (dis != null)
                dis.close();
        }
    }

    private byte[] getRandomData(int size) {
        byte[] buffer = new byte[size];
        rand.nextBytes(buffer);
        return buffer;
    }
}
