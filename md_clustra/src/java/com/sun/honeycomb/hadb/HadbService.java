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



package com.sun.honeycomb.hadb;

import com.sun.hadb.adminapi.HADBException;
import com.sun.hadb.adminapi.MAConnection;
import com.sun.hadb.adminapi.MAConnectionFactory;
import com.sun.hadb.adminapi.ManagementDomain;
import com.sun.hadb.adminapi.MemberNotInThisDomainException;

import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.common.InternalException;

import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;

import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;

import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.common.StringUtil;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Set;
import java.util.HashSet;

import java.util.logging.Level;
import java.util.logging.Logger;


public class HadbService
    implements HADBServiceInterface {

    private static final Logger LOG =
        Logger.getLogger(HadbService.class.getName());

    private static final String HADB_INSTALL_DIR = "/config/hadb_install";
    private static final String HADB_RAMDISK_DIR = "/opt/SUNWhadb";
    private static final String HADB_45_CFG_PATH = HADB_INSTALL_DIR+"/4.5.0-11";
    private static final String HADB_45_OPT_PATH = HADB_RAMDISK_DIR+"/4.5.0-11";
    private static final String HADB_INSTALL_SYMLINK = HADB_INSTALL_DIR+"/4";
    private static final String HADB_RAMDISK_SYMLINK = HADB_RAMDISK_DIR+"/4";
    private static final String MA_PATH = HADB_INSTALL_SYMLINK+"/bin/ma";

    private static final String MA_CFG = "/config/SUNWhadb/mgt.cfg";
    private static final String MA_CFG_CONFIGPATH = "ma.server.dbconfigpath";
    private static final String MA_USER = "internal";
    private static final String MA_WAITS_PROPERTY = "honeycomb.hadb.ma_waits";
    private static final String MA_MOVING_DISKS_MARKER_PATH = 
    	                                      "/config/SUNWhadb/diskmove";

    // Poll delay waiting for the HADB disk to become available
    // should be longer than the bad disk timeout
    private static final long MA_START_TIMEOUT = 60000; // 1 minute

    // Delay between retries of connect to MA
    private static final long MA_START_RETRY_DELAY = 5000; // 5s

    // Poll delay in loop waiting for the DiskMonitor to get ready
    private static final long DISKMONITOR_POLL_DELAY = 15000; // 15s

    // Poll delay waiting for the HADB disk to become available
    private static final long DISKWAIT_POLL_DELAY = 15000; // 15s

    // If the HADB disk doesn't become available within this time of
    // the first disk coming online, give up.
    private static final long DISKWAIT_TIMEOUT = 5 * 60 * 1000; // 5 minutes

    // If the HADB disk stays bad for this long, declare an error.
    private static final long BADDISK_TIMEOUT =1 * 60 * 1000; // 1 minute

    // If MAs are being started sequentially, this is the poll loop delay.
    private static final long SEQ_START_POLL_INTERVAL = 1000;

    // If MA is running, sleep for this long and re-check.
    private static final long MA_CHECK_INTERVAL = 5000;
    
    // Once we have started MA for the first time, sleep for this long
    // in order to give MA to get settled
    private static final long MA_POST_START_WAIT = 20 * 1000; // 20 secs

    // After "kill SIGINT" of the MA process, wait for it to exit for this
    // much time before destroying the process.
    private static final long MA_EXIT_TIMEOUT = 10000; // 10s
    
    private boolean running;
    private boolean should_start;
    private Process maProcess;
    private String hadbDir;
    private int hadbDriveNum;
    private boolean isMaRunning;
    private int myId;
    private boolean detectedBadDisk;
    private File maMovingDiskMarkerFile = new File(MA_MOVING_DISKS_MARKER_PATH);

    private boolean should_notify;
    private long lastGoodDriveTime;

    private HashSet disksTried = null;

    private Thread svcThread = null;

    public HadbService() {
        running = false;
        should_start = true;
        maProcess = null;
        hadbDir = null;
        isMaRunning = false;
        detectedBadDisk = false;

        should_notify = false;

        myId = getNodeMgrProxy().nodeId();

        LOG.info("The HADB service has been instantiated.");
    }

    private void createDir(String suffix) {
        File dir = new File(hadbDir+suffix);
        String [] chownCmd = new String [4];
        if (!dir.exists()) {
            dir.mkdir();
        }

        chownCmd[0]="/usr/bin/chown";
        chownCmd[1]="-R";
        chownCmd[2]=MA_USER;
        chownCmd[3]=dir.getAbsolutePath();
        try {
            execAndWait(chownCmd);
        } catch (IOException ioe) {
            LOG.warning("An IOException was encountered while changing file " + dir.getAbsolutePath() +
                        " to be owned by " + MA_USER);
            LOG.warning(ioe.getMessage());
        }
    }

    private void createHadbDirectories() {
        createDir("");
        createDir("/dbdef");
        createDir("/repository");
        createDir("/history");
        createDir("/log");
    }

    /**
     * Wait for /data/<hadbDriveNum> to be available by querying the
     * DiskMonitor service.
     *
     * @throws NoDiskException if the HADB disk doesn't come up with
     *                      DISKWAIT_TIMEOUT of the first disk
     */
    private void ensureHadbDriveMounted(int drive) throws NoDiskException {
        long driveTimer = 0;
        boolean anyDrivesUp = false;
        
        while (true) {
            Disk[] nodeDisks = DiskProxy.getLocalDisks();

            if (nodeDisks == null) {
                // DiskMonitor not ready; sleep and re-try
                try {
                    Thread.currentThread().sleep(DISKMONITOR_POLL_DELAY);
                } catch (InterruptedException ignored) {}

                continue;
            }

            for (int i = 0; i < nodeDisks.length; i++) {
                if (nodeDisks[i] == null || !nodeDisks[i].isMounted())
                    // That disk is not available.
                    continue;

                if (!anyDrivesUp)
                    // Here's the first valid disk; start the disk timer.
                    driveTimer = System.currentTimeMillis();
                anyDrivesUp = true;

                if (nodeDisks[i].diskIndex() == drive) {
                    // The HADB drive is OK to use.
                    LOG.info("Using " + nodeDisks[i].getPath());
                    return;
                }
                
            }

            if (anyDrivesUp) {
                long curTime = System.currentTimeMillis();
                if (curTime - driveTimer > DISKWAIT_TIMEOUT)
                    // It's been 5 minutes since the first disk came
                    // up; assume the drive is not going to come up.
                    throw new NoDiskException("HADB drive /data/" + drive +
                                              " did not come online.");
            }

            // Wait and re-try.
            try {
                Thread.currentThread().sleep(DISKWAIT_POLL_DELAY);
            } catch (InterruptedException ignored) {}
        }
    }

    private boolean isMyDriveUp() {
        Disk[] nodeDisks = DiskProxy.getLocalDisks();
        if (nodeDisks == null)
            return false;

        for (int i = 0; i < nodeDisks.length; i++) {
            if (nodeDisks[i] != null &&
                nodeDisks[i].diskIndex() == hadbDriveNum &&
                nodeDisks[i].isMounted()) {

                return true;
            }
        }

        return false;
    }

    private int execAndWaitWithExitVal (String[] args)
        throws IOException {

        // If we're logging FINE, print stdout/stderr of the process
        // to the log
        Logger l = null;
        if (LOG.isLoggable(Level.FINE))
            l = LOG;

        int returnValue = Exec.exec(args, null, l);

        LOG.info("Exec " + echo(args) + " (rc: " + returnValue + ")");
        return returnValue;
    }

    private void execAndWait(String[] args)
        throws IOException {

        execAndWaitWithExitVal(args);
    }

    /** Quotes and concatenates the strings together separated by spaces */
    private String echo(String[] args) {
        StringBuffer sb = new StringBuffer();
        String delim = "";
        for (int i = 0; i < args.length; i++) {
            sb.append(delim).append(StringUtil.image(args[i]));
            delim = " ";
        }

        return sb.toString();
    }

    private boolean execPgrep(String pstring)
        throws IOException {
        String[] pgrepString = new String[3];
        pgrepString[0] = "/usr/bin/pgrep";
        pgrepString[1] = "-f";
        pgrepString[2] = pstring;
        return (execAndWaitWithExitVal(pgrepString) == 0);
    }

    private void execPkill(String pstring)
        throws IOException {
        String[] pkillString = new String[4];
        pkillString[0] = "/usr/bin/pkill";
        pkillString[1] = "-9";
        pkillString[2] = "-f";
        pkillString[3] = pstring;
        execAndWait(pkillString);
    }

    private void execPkillNice (String pstring)
        throws IOException {
        String[] pkillString = new String[4];
        pkillString[0] = "/usr/bin/pkill";
        pkillString[1] = "-2";
        pkillString[2] = "-f";
        pkillString[3] = pstring;
        execAndWait(pkillString);
    }


    private void killProcsCore(boolean killAll)
        throws IOException {
        boolean MAProcessAlive = true;
        boolean HADBProcessAlive = true;
        boolean HADBProcessAlive2 = true;
        boolean JDMKProcessAlive = true;
        boolean AnyAlive = true;
        while (AnyAlive) {
            execPkill(MA_PATH);
            if (killAll) {
                execPkill("SUNWhadb");
                execPkill("hadb_install");
                execPkill("SUNWjdmk");
            }
            try {
                Thread.currentThread().sleep(5000);
            } catch (InterruptedException ignored) {}
            MAProcessAlive=execPgrep(MA_PATH);
            if (MAProcessAlive)
                LOG.info("The HADB MA Process is still running");
            if (killAll) {
                HADBProcessAlive=execPgrep("SUNWhadb");
                if (HADBProcessAlive)
                    LOG.info("One of the HADB CL Processes are still running");
                HADBProcessAlive2=execPgrep("hadb_install");
                if (HADBProcessAlive2)
                    LOG.info("One of the HADB CL Processes are still running");
                JDMKProcessAlive=execPgrep("SUNWjdmk");
                if (JDMKProcessAlive)
                    LOG.info("The HADB JDMK Process is still running");
            }
            if (killAll) {
                AnyAlive = MAProcessAlive || HADBProcessAlive
                    || HADBProcessAlive2 || JDMKProcessAlive;
            } else {
                AnyAlive = MAProcessAlive;
            }
        }
        //LOG.info("It appears that all HADB Processes have been killed!");
    }

    private void killProcsAndWait()
        throws IOException {
        killProcsCore(true); //kill all procs
    }

    private void killMAAndWait()
        throws IOException {
        killProcsCore(false); //only kill MA
    }

    private void killIpcs()
        throws IOException {
        String[] killString = new String[3];
        killString[0] = "/usr/bin/ipcrm";
        killString[1] = "-M";
        killString[2] = "15001";
        execAndWait(killString);

        killString[2] = "15002";
        execAndWait(killString);

        killString[2] = "15003";
        execAndWait(killString);

        killString[2] = "15004";
        execAndWait(killString);

        killString[2] = "15005";
        execAndWait(killString);

        killString[1] = "-S";
        killString[2] = "15005";
        execAndWait(killString);
    }

    private void rmHadbDir()
        throws IOException {

        // Move the history and ma log files before blowing them away
        try {
            Date now = new Date(System.currentTimeMillis());
            String subDir = (new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss")).format(now);
            File backUpDir = new File(hadbDir,"../hadb-logs/"+subDir);
            backUpDir.mkdirs();

            // Move history file
            String[] mvString = new String[3];
            mvString[0] = "/usr/bin/mv";
            mvString[1] = hadbDir + "/history";
            mvString[2] = backUpDir.getAbsolutePath();
            execAndWait(mvString);
            if (!(new File(backUpDir,"history")).exists()) {
                LOG.log(Level.INFO, "Failed to move the hadb  history directory located at "
                        + hadbDir + " to " + backUpDir);
            } else {
                LOG.log(Level.INFO, "moved hadb history dir located at " + hadbDir + " to "
                        + backUpDir.getAbsolutePath());
            }

            //Move ma log file
            mvString[1] = hadbDir + "/log/ma.log";
            execAndWait(mvString);
            if (!(new File(backUpDir,"ma.log")).exists()) {
                LOG.log(Level.INFO, "Failed to move the hadb ma log file located at "
                        + hadbDir +"log/ma.log" + " to " + backUpDir);
            } else {
                LOG.log(Level.INFO, "moved hadb ma log file located at " + hadbDir + "/log to "
                        + backUpDir.getAbsolutePath());
            }
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Encountered an IOException while trying to move hadb " +
                    "log files to a safe location");
            LOG.log(Level.WARNING,ioe.getMessage());
        }

        String[] rmString = new String[3];
        rmString[0] = "/usr/bin/rm";
        rmString[1] = "-rf";
        rmString[2] = hadbDir;
        LOG.log(Level.INFO, "removing hadb dir");
        execAndWait(rmString);
        if ((new File(hadbDir)).exists()) {
            LOG.log(Level.INFO, "Failed to remove the hadb directory located at " + hadbDir);
        } else {
            LOG.log(Level.INFO, "Removed hadb dir located at " + hadbDir);
        }
        //LOG.log(Level.INFO, "Moving aside hadb dir instead of deleting it");
        //moveAsideDir();
    }


    //So we can backup the old hadb instead of deleting it.
    private void moveAsideDir()
        throws IOException {
        int suffix = 0;
        File backUpDir = null;
        File hadbDirF = new File(hadbDir);

        //Find a unique name for the back up dir
        while (true) {
            backUpDir = new File(hadbDir + "." + suffix);
            if (backUpDir.exists()) {
                LOG.log(Level.INFO, "Backup HADB dir " + backUpDir + " aleady exists. Will pick new suffix and try again");
                suffix++;
            } else {
                break;
            }
        }

        //move the directory
        LOG.log(Level.INFO, "Going to backup HADB dir to " + backUpDir);
        if (!hadbDirF.renameTo(backUpDir)) {
            LOG.log(Level.SEVERE, "Attempt to rename HADB Dir (" + hadbDirF + ") to " + backUpDir + " failed!");
        } else {
            if (hadbDirF.exists()) {
                LOG.log(Level.SEVERE, "HADB dir ("+hadbDirF+") still exists after move attempt.");
            }
        }
    }

    private void changeHadbDrive(int driveNum, boolean markerFileNeeded)
        throws IOException {
    	if (markerFileNeeded && !maMovingDiskMarkerFile.exists()) {
    		FileWriter fw = new FileWriter(maMovingDiskMarkerFile);
    		fw.write("[" + System.currentTimeMillis() + "] HADB files moving" 
    				 + " from " + hadbDir + " to drive " + driveNum );
    		fw.close();
    	}
        String[] mvString = new String[2];
        mvString[0] = "/opt/honeycomb/bin/hadb_cfg_rewrite.pl";
        mvString[1] = Integer.toString(driveNum);
        execAndWait(mvString);
    }

    private int pickNewDrive() throws NoDisksAvailableException {

        if (disksTried == null)
            disksTried = new HashSet();

        Disk[] theDisks = DiskProxy.getLocalDisks();
        if (theDisks == null)
            throw new NoDisksAvailableException("DiskMonitor not ready?");

        for (int i = 0; i < theDisks.length; i++) {
            // Ignore unavailable disks.
            if (theDisks[i] == null || !theDisks[i].isMounted())
                continue;

            // Have we already tried this disk?
            if (disksTried.contains(theDisks[i].getPath())) {
                LOG.info("Already tried to use " + theDisks[i].getPath());
                continue;
            }
            
            // We can use it.
            
            disksTried.add(theDisks[i].getPath());
            return i;
        }

        throw new NoDisksAvailableException("No more usable disks on " + myId);
    }

    /**
     * Search for MA_CFG_CONFIGPATH in MA_CFG, and extract the directory
     * that HADB files are in.
     */
    static String getPathFromConfig() throws IOException {
        BufferedReader cfg = new BufferedReader(new FileReader(MA_CFG));
        String line;

        try {
            while ((line = cfg.readLine()) != null) {
                if (!line.trim().startsWith(MA_CFG_CONFIGPATH))
                    continue;

                int pos = line.indexOf('=');
                if (pos < 0) {
                    LOG.warning("Ignoring line " + StringUtil.image(line));
                    continue;
                }

                String confPath = line.substring(pos + 1).trim();

                // Now talk to the DiskMonitor proxy on the local node and
                // get the mount-point of the disk that confPathPath is
                // on. The HADB directory is the first path component
                // after the mount-point.

                // XXX HACK!  For now, we *know* that the mount-point
                // is /data/n so just count off three components to
                // get the path. FIXME!!!
                String[] comps = confPath.split("/+");
                if (comps.length < 5)
                    throw new InternalException("Unexpectedly short path: " +
                                                StringUtil.image(confPath));
                String hadbPath = "/" + comps[1] + "/" + comps[2] + "/" + comps[3];
                LOG.info("Deduced HADB path: " + StringUtil.image(hadbPath));
                // End of lousy hack.

                return hadbPath;
            }
        }
        finally {
            cfg.close(); 
        }

        LOG.severe("Couldn't find " + StringUtil.image(MA_CFG_CONFIGPATH) +
                   " in " + StringUtil.image(MA_CFG));
        return null;
    }

    private int getDriveNumFromPath(String path) {
        // XXX HACK! The right thing to do here is to talk to the
        // diskmonitor proxy and figure out which disk the path
        // belongs on by looking at the mount-points. For now we do
        // this lousy hack. FIXME!!!

        String[] comps = path.split("/+");
        return Integer.parseInt(comps[2]);
    }

    private void wipeCore()
        throws IOException, NoDiskException {
        killProcsAndWait();
        killIpcs();
        hadbDir = getPathFromConfig();
        hadbDriveNum = Integer.parseInt(hadbDir.substring(6,7));
        ensureHadbDriveMounted(hadbDriveNum);
        rmHadbDir();
        createHadbDirectories();
    }

    /** This is the method called when moving to a new disk */
    private synchronized void wipeAndRestartForMove()
        throws IOException {
        LOG.info("Wiping and restarting MA on hcb" + myId);

        try {
            wipeCore();//XXX don't rm the old dir
        }
        catch (NoDiskException e) {
            LOG.severe("Wipe may fail: " + e.getMessage());
        }

        try {
            HADBMasterInterface masterAPI = getMasterApi();
            masterAPI.disableOneHost(getNodeMgrProxy().nodeId());

            should_notify = true;
            LOG.info("Wipe complete.");
        }
        catch (HADBMasterInterface.HADBServiceException e) {
            LOG.log(Level.WARNING, "Couldn't disable hcb" + myId);
        }
        catch (ManagedServiceException e) {
            LOG.log(Level.WARNING, "Couldn't invoke HADBMaster RMI", e);
        }
    }

    public synchronized void restartForAll()
        throws IOException, ManagedServiceException {
        LOG.info("Restarting MA on hcb" + myId + "(for all).");

        killProcsAndWait();
        killIpcs();
        LOG.info("Restart complete.");
    }

    private void copyForUpgrade(String newVersion) throws IOException {
        LOG.info("copy start");
        String[] cpString = new String[4];
        cpString[0] = "/usr/bin/cp";
        cpString[1] = "-rpP";
        cpString[2] = HADB_RAMDISK_DIR+"/"+newVersion;
        cpString[3] = HADB_INSTALL_DIR+"/"+newVersion;
        execAndWait(cpString);
        LOG.info("copy complete");
    }

    public void changeSymlinkForUpgrade(String newVersion) throws IOException {
        LOG.info("updating symlink");
        String[] rmString = new String[3];
        rmString[0] = "/usr/bin/rm";
        rmString[1] = "-f";
        rmString[2] = HADB_INSTALL_SYMLINK;
        execAndWait(rmString);
        String[] lnString = new String[4];
        lnString[0] = "/usr/bin/ln";
        lnString[1] = "-s";
        lnString[2] = HADB_INSTALL_DIR+"/"+newVersion;
        lnString[3] = HADB_INSTALL_SYMLINK;
        execAndWait(lnString);
        LOG.info("symlink update complete");
    }

    public synchronized void wipeAndRestartForAll()
        throws IOException {
        LOG.info("Wiping and restarting MA on this node (for all).");
        try {
            wipeCore();
            LOG.info("Wipe complete.");
        }
        catch (NoDiskException e) {
            LOG.severe("Wipe failed: " + e.getMessage());
        }
    }

    private NodeMgrService.Proxy getNodeMgrProxy() {
        return ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
    }

    private HADBMasterInterface getMasterApi() {

        Node masterNode =
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).getMasterNode();

        if (masterNode == null) {
            LOG.warning("No master node found.");
            return null;
        }

        ManagedService.ProxyObject proxy
            = ServiceManager.proxyFor(masterNode.nodeId(),
                                      "HADBMaster");
        if ((proxy == null) || (!proxy.isReady())) {
            LOG.warning("Cannot get the proxy for the HADBMaster.");
            return null;
        }

        Object obj = proxy.getAPI();
        if (!(obj instanceof HADBMasterInterface)) {
            LOG.warning("The HADBMaster API is not ready yet.");
            return null;
        }

        return (HADBMasterInterface)obj;
    }

    private void notifyMaster() {

        HADBMasterInterface masterAPI = getMasterApi();
        if(masterAPI == null) {
            /* XXX crappy try again later code */
            should_notify = true;
            return;
        }

        try {
            int thisId = getNodeMgrProxy().nodeId();
            LOG.info("Trying to move " + thisId + " to disk " + hadbDriveNum + "...");
            masterAPI.recoverHostForMove(thisId, hadbDriveNum);
        }
        catch (MemberNotInThisDomainException mde) {
        	should_notify = false;
        }
        catch (HADBMasterInterface.HADBServiceException e) {
            LOG.log(Level.WARNING, "Couldn't notify HADB MasterService");
            should_notify = true;
        }
        catch (ManagedServiceException e) {
            LOG.log(Level.WARNING, "Couldn't invoke HADBMaster RMI", e);
            should_notify = true;
        }
    }

    private synchronized Process startMA()
        throws IOException {

        // Check if ma is already running
        String[] pgrepCmd = new String[3];
        pgrepCmd[0] = "/usr/bin/pgrep";
        pgrepCmd[1] = "-f";
        pgrepCmd[2] = MA_PATH;
        
        int exitCode = execAndWaitWithExitVal(pgrepCmd);

        if (exitCode == 0) {
            LOG.info("The MA process is already running. Killing it first");
            pgrepCmd[0] = "/usr/bin/pkill";
            execAndWait(pgrepCmd);
            LOG.info("The MA process has been killed");
        }

        // be sure to start with a clean state
        /*
          killProcsAndWait();
          killIpcs();
        */

        String [] chownCmd = new String [3];
        chownCmd[0] = "/usr/bin/chown";
        chownCmd[1] = MA_USER;
        chownCmd[2] = MA_CFG;
        execAndWait(chownCmd);

        String[] cmd = new String[5];
        cmd[0] = "/usr/bin/su";
        cmd[1] = "-";
        cmd[2] = MA_USER;
        cmd[3] = "-c";
        cmd[4] = MA_PATH + " " + MA_CFG;

        LOG.info("Starting MA with command: " + echo(cmd));
        return Runtime.getRuntime().exec(cmd);
    }

    /*
     * Check if this MA server is allowed to start -
     * This MA server can start iff
     *  - the master service is not in initializing phase
     *  - all MA servers on nodes id lower than this one are started.
     * OR
     *  - honeycomb.hadb.ma_waits is set to 0 (the default for 1.1 and later)
     */
    private boolean isAllowedToStart() {

        ClusterProperties clusterConf =  ClusterProperties.getInstance();
        long should_wait = clusterConf.getPropertyAsLong(MA_WAITS_PROPERTY, 0);
        if (should_wait == 0) {
            return true;
        }

        NodeMgrService.Proxy nodemgr;
        nodemgr = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        Node master = nodemgr.getMasterNode();
        if (master == null) {
            return false;
        }

        ManagedService.ProxyObject proxy;
        proxy = ServiceManager.proxyFor(master.nodeId(), MasterService.class);
        if (!(proxy instanceof HADBMasterInterface.Proxy)) {
            return false;
        }

        if (!((HADBMasterInterface.Proxy) proxy).isInitializing()) {
            return true;
        }

        // Also check to see if we just wiped -- in which case, the
        // MAs can all be started together; return true.
        // TODO

        int nodeid = nodemgr.nodeId();
        Node[] hcnodes = nodemgr.getNodes();

        for (int i = 0; i < hcnodes.length; i++) {
            if (hcnodes[i].nodeId() >= nodeid) {
                continue;
            }
            if (!hcnodes[i].isAlive()) {
                continue;
            }
            proxy = ServiceManager.proxyFor(hcnodes[i].nodeId(), HadbService.class);
            if (!(proxy instanceof HADBServiceProxy)) {
                return false;
            }
            if (!((HADBServiceProxy) proxy).getRunning()) {
                return false;
            }
        }
        return true;
    }

    /*
     * Wait that MA is running by checking if we can connect locally
     */
    private void waitMAReady() {
        String addr = "localhost:" + Hadbm.HADB_PORT;
        String passwd = Hadbm.PWD_ADMIN;

        long endTime = System.currentTimeMillis() + MA_START_TIMEOUT;
        MAConnection conn = null;

        do {
            try {
                Thread.currentThread().sleep(MA_START_RETRY_DELAY);

                LOG.info("Trying to connect to MA....");
                conn = MAConnectionFactory.connect(addr, passwd, passwd);
                if (conn != null)
                    return;
            }
            catch (HADBException ignored) {}
            catch (InterruptedException ignored) {}
            finally {
                if (conn != null)
                    conn.close();
            }

        } while (endTime > System.currentTimeMillis());

        LOG.severe("Timeout; couldn't connect to MA.");
        throw new InternalException("Couldn't connect to local MA!");
    }

    /**
     * Needed for 1.1 Only
     * HADB 4.5 on 1.0 ran out of /opt, thus we need a symlink to it's new
     * home in /config
     */
    private void create45symlink() {

        String[] lnString = new String[4];
        lnString[0] = "/usr/bin/ln";
        lnString[1] = "-s";
        lnString[2] = HADB_45_CFG_PATH;
        lnString[3] = HADB_45_OPT_PATH;
        try {
            execAndWait(lnString);
        } catch (IOException e) {
            LOG.severe("couldn't create our hadb symlink");
            throw new RuntimeException(e);
        }
    }

    private void checkAndDoUpgrade() {
        LOG.info("Seeing if upgrade is required....");

        //FIXME - refactor this code
        try {
            File hadbRamdiskSymlink = new File(HADB_RAMDISK_SYMLINK);
            File hadbInstallSymlink = new File(HADB_INSTALL_SYMLINK);
            File configdir = new File(HADB_INSTALL_DIR);
            HadbVersion newVersion =
                new HadbVersion(hadbRamdiskSymlink.getCanonicalPath());
            if(!configdir.exists()) {
                configdir.mkdir();
            }
            if (!hadbInstallSymlink.exists()) {
                System.out.println("No config dir!");
                copyForUpgrade(newVersion.toPathString());
                changeSymlinkForUpgrade(newVersion.toPathString());
                return;
            }
            HadbVersion curVersion =
                new HadbVersion(hadbInstallSymlink.getCanonicalPath());
            if(curVersion.compareTo(newVersion)<0) {
                System.out.println("New HADB version is available");
                copyForUpgrade(newVersion.toPathString());
                changeSymlinkForUpgrade(newVersion.toPathString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class NoDiskException extends Exception {
        public NoDiskException(String msg) {
            super(msg);
        }
    }

    private static class NoDisksAvailableException extends Exception {
        public NoDisksAvailableException(String msg) {
            super(msg);
        }
    }

    /**
     * Execute initialization that needs to be done before we reach
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
        // If the previous instance of MA was not cleanly terminated
        // -- for instance, when there's a CM escalation at shutdown,
        // remaining services will NOT be shutdown gracefully but the
        // service JVMs are killed. When that happens, there could be
        // orphan HADB processes and IPCs hanging around -- kill them.
        cleanup();

        running = true;
        maProcess = null;
    }

    public void run() {
        svcThread = Thread.currentThread();

        try {
            hadbDir = getPathFromConfig();
            hadbDriveNum = getDriveNumFromPath(hadbDir);
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Failed to read HADB MA config \"" + MA_CFG + "\"", e);
            throw new RuntimeException(e);
        }

        create45symlink(); //remove this line + func when there are no more
                           //4.5 db's in the world.

        checkAndDoUpgrade();
        
        while (!diskIsReady());
        lastGoodDriveTime = System.currentTimeMillis();
        createHadbDirectories();

        while (!isAllowedToStart()) {
            try {
                Thread.currentThread().sleep(SEQ_START_POLL_INTERVAL);
            } catch (InterruptedException ignored) {}
        }


        // Start MA. We need to wait longer at initial startup: if we
        // start the MAs too quickly, we may fail to start the db.
        startLocalMA();
        try {
            Thread.currentThread().sleep(MA_POST_START_WAIT);
        } catch (InterruptedException ignore) {}
        isMaRunning = checkMARunning();

        // Note that the isMARunning flag is only set in this method,
        // and is only used by the proxy to send that info to the
        // MasterService. Its value stays true until checkMARunning()
        // fails; after that, it stays false until MA has been
        // re-started succesfully.

        while (running) {
            try {
                Thread.currentThread().sleep(MA_CHECK_INTERVAL);
            } catch (InterruptedException ignored) {}

            //
            // Check that the disk is OK.
            //

            if (isMyDriveUp()) {
                // If it's good, record the time so we can do the
                // timeout.
                lastGoodDriveTime = System.currentTimeMillis();
                detectedBadDisk = false;
            }
            else {
        	detectedBadDisk = true;
        	LOG.info("Disk failure: " + hadbDir + " is unusable.");

                if (!running)
                    // If we're not running, who cares -- we're done.
                    break;
                
                long now = System.currentTimeMillis();

                if (now - lastGoodDriveTime < BADDISK_TIMEOUT)
                    // Do nothing until the timeout expires, and skip
                    // the MA check. (If we don't, and MA is bouncing
                    // -- as it does when disks are down -- the
                    // MasterService may try to disable the disk.)
                    continue;

                // The time has come to switch to a new disk.
                switchToNewDisk();
            }

            //
            // Check that MA is running.
            //

            if (!running)
                // If the service has been told to terminate, who
                // cares -- we're done.
                break;

            if (!checkMARunning()) {
                isMaRunning = false;

                // This starts MA and makes sure that we can talk to
                // it -- otherwise it throws an exception.
                startLocalMA();
            }
            isMaRunning = true;
        }

        // Kill the MA process. Note that we'll be running the kill -2
        // whether or not maProcess is null.
        isMaRunning = false;
        String pName = nameOf(maProcess);
        try {
            LOG.info("Telling HADB MA process [" + pName + "] to exit...");
            execPkillNice(MA_PATH);
        }
        catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Couldn't stop MA [" + pName + "] cleanly!", e);
        }

        // Wait for the process to exit. Note that we're not checking
        // to see if maProcess is null -- to be able to do that
        // correctly we'd have to get synchronization exactly
        // right. (At this point maProcess can go from non-null to
        // null, but not the other way.) Just forge on, and if it gets
        // an NPE (or anything else), log and ignore it.
        try {
            maProcess.waitFor();
        }
        catch (Exception e) {
            // Not necessarily an error
            LOG.info("Waiting for MA: " + e);
        }

        LOG.info("MA monitor exit.");
    }

    private boolean diskIsReady ()  {
    	boolean diskMoved = false;
    	boolean verifiedDrive = false;
    	boolean isReady = true;
    	int d = hadbDriveNum;
    	
        
        if (maMovingDiskMarkerFile.exists()) {
        	LOG.info("Detected disk move marker file. " +
        			"Last move failed to complete.");
        	diskMoved = true;
        }
        
        // make sure the disk I am using is mounted, if not
        // pick a new disk and repeat
        while (!verifiedDrive) {
        	try {
        		LOG.info("Waiting for HADB directory " + d 
        				+ " to be mounted...");
        		ensureHadbDriveMounted(d);
        		verifiedDrive = true;
        		LOG.info("HADB Drive (" + d + ") is mounted.");
        	} catch (NoDiskException nde) {
        		LOG.info("HADB Drive (" + d + ") has not been mounted. " +
        				"Picking new drive");
        		diskMoved = true;
        		try {
        			d = pickNewDrive();
        		} catch (NoDisksAvailableException ndae) {
        			throw new InternalException("No disks available for HADB");
        		}
        	}
        }
        
        // If I am using a new disk than perform any work needed by HADB to use
        // the new disk.
        if (diskMoved) {
        	boolean dbReCfgNeeded = true; 
            ClusterProperties props = ClusterProperties.getInstance();
            long cTime = props.getPropertyAsLong(
                                ConfigPropertyNames.PROP_HADBM_LAST_CREATE_TIME, 
                                -1);
            if (cTime == 0) {
            	dbReCfgNeeded = false;
            }
            
            // write new config data
            try {
            	changeHadbDrive(d, dbReCfgNeeded);
            } catch (IOException ioe) {
            	LOG.log(Level.SEVERE, "Received an IO Exception while writing " +
            			"hadb configuration data to /config filesystem", ioe);
            	throw new InternalException(ioe);
            }
            
            //wipe disk and take appropriate HADB action
            try {	
                if (dbReCfgNeeded) {
            		wipeAndRestartForMove();
                } else {
            		wipeAndRestartForAll();
                }
            } catch (IOException ioe) {
            	LOG.log(Level.WARNING, "Received IO Exception while wiping " +
            			"hadb dirs from disk " + d, ioe);
            	isReady = false;
            }
        }
        
        return isReady;
        
    }
    

    private void startLocalMA() {
        try {
            LOG.info("Trying to start MA on node hcb" + myId + "...");
            maProcess = startMA();

            // Don't set isMaRunning to true yet.  We will set it below
            // if it stays up for at least 5 seconds.
            // this way bouncy MA == "not running".
            LOG.info("Waiting for MA on hcb" + myId +
                     " [" + nameOf(maProcess) + "] to settle down.");
            waitMAReady();

            // We only 'notify' the master when starting MA after the hadb files have 
            // moved to a new disk. 
            if (should_notify) {
                LOG.info("Notifying master that MA on hcb" + myId +
                         " [" + nameOf(maProcess) + "] has been started.");                
                notifyMaster();
                detectedBadDisk = false;
            }
            else {
                LOG.info("MA process on hcb" + myId +
                         " [" + nameOf(maProcess) + "] has been started" +
                         " (not notifying).");
            }
            if (maMovingDiskMarkerFile.exists()) {
                if (maMovingDiskMarkerFile.delete()) {
                	LOG.fine("HADB disk move marker file has been deleted");
                } else {
                	LOG.warning("Failed to delete HADB disk move " 
                			+ "marker file (" 
                			+ MA_MOVING_DISKS_MARKER_PATH + ")");
                }
            }
        }
        catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to start MA on node hcb" + myId, e);
            maProcess = null;
        }

    }

    private boolean checkMARunning() {
        try {
            int exitCode = maProcess.exitValue();

            // If we get here the process has exited.

            LOG.info("MA on hcb" + myId + " [" + nameOf(maProcess) +
                     "] exited (" + exitCode + ").");
            synchronized (this) {
                maProcess = null;
            }
            return false;
        }
        catch (IllegalThreadStateException ignored) {
            // This means we haven't exited yet
            return true;
        }
    }

    private void switchToNewDisk() {
        try {
            int d = pickNewDrive();

            LOG.info("Dir " + hadbDir + " has been unusable for >" +
                     (BADDISK_TIMEOUT/1000) +
                     "s, trying to move to /data/" + d + "...");

            changeHadbDrive(d,true);
            wipeAndRestartForMove();

            // If we found a usable disk, reset. If we ever need to
            // switch disks again, the picture of what's available and
            // what's not will probably have changed by then.
            disksTried = null;
        }
        catch (NoDisksAvailableException e) {
            throw new InternalException("No disks available for HADB");
        }
        catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't use " + hadbDir, e);
            // The calling loop (in run()) will re-try.
        }
    }

    public void shutdown() {
        String pName = nameOf(maProcess);
        LOG.info("Shutting down... (MA " + pName + ").");

        // Tell the run() loop to kill MA and exit
        running = false;
        svcThread.interrupt();

        try {
            svcThread.join(MA_EXIT_TIMEOUT);
        }
        catch (InterruptedException ignored) {}

        // If the process has not yet exited, we must have hit the
        // timeout; call maProcess.destroy().
        try {
            int rc = maProcess.exitValue();
            LOG.info("MA [" + pName + "] has exited (rc: " + rc + ").");
        }
        catch (Exception e) {
            // Process may still be running
            LOG.warning("Destroying MA [" + pName + "] (" + e + ").");
            maProcess.destroy();
        }

        // Make sure that all procs are killed and IPCs deleted
        cleanup();
    }

    /**
     * After MA has exited (or been killed), make sure all HADB
     * processes and IPCs are gone.
     */
    private void cleanup() {
        try {
            killProcsAndWait();
        }
        catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't kill processes", e);
        }

        try {
            killIpcs();
        }
        catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't delete IPCs", e);
        }
    }

    public ManagedService.ProxyObject getProxy() {
        return new HADBServiceProxy(isMaRunning,detectedBadDisk);
    }

    private String nameOf(Process proc) {
        if (proc == null)
            return "null";
        String s = proc.toString();
        int pos = s.indexOf('@');
        if (pos >= 0)
            return s.substring(pos + 1);
        else
            return s;
    }
}
