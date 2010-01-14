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



package com.sun.honeycomb.platform;

import com.sun.honeycomb.platform.MonitoredService;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.util.sysdep.Commands;
import com.sun.honeycomb.disks.Disk;

import java.io.FileWriter;
import java.io.File;
import java.util.Map;
import java.util.Random;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;

/*
 * This class manages the nfs OS services used in honeycomb.
 */
public class NfsManager implements MonitoredService {

    static final Logger logger = Logger.getLogger(NfsManager.class.getName());

    // TODO - should be from config
    private static final String prefix = "/opt/honeycomb";
    private static final String nfsPrefix = "netdisks";
    private static final String dirSep = "/";

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY = 1000;

    private static DiskOps diskOps = null;
    private static Commands commands = null;

    private static int nbRetries = 0;
    private static boolean needToManageNfs;

    private static String nfs_process;
    private static String nfs_svc;

    // For error simulation
    private Random rand = null;
    private float errorSimulationRate = 0.0f;

    static {
        diskOps = DiskOps.getDiskOps();
        commands = Commands.getCommands();
        nfs_svc = commands.nfsSvcName();
        nfs_process = commands.nfsProcessName();
    }

    NfsManager(float errorRate) {
        this.errorSimulationRate = errorRate;

        if (errorSimulationRate > 0.0) {
            rand = new Random();
            rand.setSeed(System.currentTimeMillis());
        }

        if (logger.isLoggable(Level.INFO))
            logger.info("NfsManager created, error rate = " + errorRate);
    }
    
    // Monitored Service implementation 

    public void start() {
	if (pgrep(nfs_process)) {
	    if (logger.isLoggable(Level.INFO))
                logger.info("The NFS service is already up");
	    needToManageNfs = false;
	} else {
	    if (logger.isLoggable(Level.INFO))
                logger.info("The NFS service is not up. Starting it ...");
	    needToManageNfs = true;
	    startSvc(nfs_svc);
	}

        if (isRunning()) {
            logger.info ("Nfs service already running");
            return;
        }
        reset();
        if (logger.isLoggable(Level.INFO))
            logger.info("NfsManager started");
    }

    synchronized public void stop() {
        if (!isRunning()) {
            logger.info ("Nfs service already stopped");
            return;
        }
        shutdown();
        if (logger.isLoggable(Level.INFO))
            logger.info("NfsManager stopped");
    }

    synchronized public void restart() {
        reset();
        logger.warning("NfsManager restarted");
    }

    synchronized public boolean isRunning() {
        return pgrep (nfs_process);
    }

    public boolean doShutdownOnExit () {
        return true;
    }

    public String toString() {
        return NfsManager.class.getName();
    }
    
    // Package access
    
    synchronized boolean mountDisk(String host, String path, 
                                   String mountPoint, String options) {

        if (rand != null && rand.nextFloat() < errorSimulationRate) {
            logger.info("Simulated NFS mount error for " +
                        host + ":" + path + " -> " + mountPoint);
            return false;
        }

	try {
	    diskOps.mount(host + ":" + path, DiskOps.FS_NFS, mountPoint,
                          options);
	}
	catch (IOException e) {
	    logger.warning("Couldn't NFS-mount " + mountPoint);
	    return false;
	}
	return true;
    }
    
    synchronized void umountDisk(String mountPoint) {
	try {
	    diskOps.umount(mountPoint, DiskOps.FS_NFS, true);
	}
	catch (IOException e) {
	    logger.warning("Couldn't un-NFS-mount " + mountPoint);
	}
    }

    synchronized static void reset() {
	restartSvc(nfs_svc);
    }

    synchronized static private void shutdown() {
	if (needToManageNfs) {
	    stopSvc(nfs_svc);
	}
    }

    static private boolean pgrep(String pid) {
	try {
	    return Exec.exec(commands.pgrep() + pid + "$") == 0;
	}
	catch (IOException e) {
	    throw new InternalException("Couldn't run pgrep: " + e);
	}
    }

    private static void restartSvc(String pattern) {
	try {
	    Exec.exec(commands.svcRestart(pattern));
	}
	catch (IOException e) {
	    throw new InternalException("Couldn't restart service: " + e);
	}
    }

    private static void startSvc(String pattern) {
	try {
	    Exec.exec(commands.svcStart(pattern));
	}
	catch (IOException e) {
	    throw new InternalException("Couldn't start service: " + e);
	}
    }
    
    private static void stopSvc(String pattern) {
	try {
	    Exec.exec(commands.svcStop(pattern));
	}
	catch (IOException e) {
	    throw new InternalException("Couldn't stop service: " + e);
	}
    }

    static void unmountAll() {
        // Called on shutdown
        try {
            Map mounts = diskOps.getCurrentMounts(DiskOps.FS_NFS);
            for (Iterator i = mounts.keySet().iterator(); i.hasNext(); ) {
                String dev = (String) i.next();
                try {
                    diskOps.umount(dev, DiskOps.FS_NFS, true);
                }
                catch (IOException e) {
                    logger.warning("Couldn't un-NFS-mount " + dev);
                }
            }
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Unmounting all NFS mounts", e);
        }
        logger.info("All NFS filesystems unmounted.");
    }
    
    static String getNFSMountPoint(Disk disk) {
        return dirSep + nfsPrefix + dirSep + disk.getNodeIpAddr() + disk.getPath();
    }
    
    static String getNFSMountPoint(int nodeId, String path) {
        String addr = "10.123.45." + nodeId;
        return dirSep + nfsPrefix + dirSep + addr + path;
    }
    
    public static String getNfsPath(Disk disk, String localPath) {
        return  dirSep + nfsPrefix + dirSep + disk.getNodeIpAddr() + localPath;
    }
}
