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

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.MessageFormat;
import java.text.DecimalFormat;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.DiskHealth;
import com.sun.honeycomb.disks.DiskLabel;
import com.sun.honeycomb.disks.DiskInitialize;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.Kstat;
import com.sun.honeycomb.util.posix.StatFS;
import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.Ipmi;
import com.sun.honeycomb.util.BundleAccess;

/**
 * The DiskMonitor is a {@link ManagedService} that monitors disks and
 * also handles requests from other services to manipulate them [the
 * disks].
 *
 * @author Shamim Mohamed
 * @version $Revision: 1.5 $ $Date: 2008-03-04 15:50:17 -0800 (Tue, 04 Mar 2008) $
 */

public class DiskMonitor implements DiskControl {

    // The alert string when we find a new disk
    private static final String ALERT_NEW_DISK = "info.disk.monitor.newdisk";

    // The alert string when we find a foreign or mismatched disk
    private static final String ALERT_FOREIGN_DISK = "err.disk.mismatch";

    private static final float DEFAULT_AVAIL_LIMIT = 0.1f; // 90%
    private static final int DEFAULT_USAGE_CAP = 85; // 85%
    private static final long DEFAULT_POLL_INTERVAL = 600L; // 10 minutes

    private static final String DEFAULT_KERNEL_LOG = "/var/adm/kernel";

    private static final String CONFIG_DIR = "/config";
    private static final String CLEAN_UNMOUNT_PREFIX =
        CONFIG_DIR + "/clean_unmount_";

    // If a disk has more than this many errors it is disabled
    private static final int DEFAULT_ERROR_THRESHOLD = 10;

    // Shutdown delay between disable and dismount
    private static final long DEFAULT_DISMOUNT_DELAY = 10; // sec.

    private static final int DEFAULT_RETRY_LIMIT = 5;

    private static final boolean OFF = false;
    private static final boolean ON = true;

    // disable() flag: should we write the label indicating
    // that the disk is disabled?  This really indicates
    // whether we're doing a "soft disable" (no label update,
    // no swap/mirror/dump disassociation) or a "hard disable"
    // (label update, diassociation swap/mirror/dump from disk).
    private enum LabelMode { WRITE, DONT_WRITE };

    // Flag to disable indicating why we are disabling.
    //
    private enum DisableCause {
    	INTENTIONAL,		// disabling due to operator instruction
            			// or clean shutdown
	PULL,			// disabled due to pull
	ERROR };		// disabling because saw errors on drive

    // Flag to control whether we enable the disk when "onlining" it.
    //
    private enum OnlineEnableAction {
    	ENABLE,		// enable disk, even if label indicates disabled state

	OBEY_LABEL };	// obey enabled/disabled state recorded in the label

    // pattern for extracting partition information.
    // This will dig the cXtXdX part out of disk names
    // like these: /dev/dsk/c0t0d0s4 /dev/rdsk/c1t1d0p0.
    private static final Pattern partitionPattern =
        Pattern.compile("/dev/r?dsk/(.*)[sp]\\d$");

    private HardwareProfile profile = null;

    private Map diskMap = null; // diskId -> Disk
    private Disk[] diskArray;
    private int numDisks;

    private int cellId = -1;
    private int siloId = -1;
    private int nodeId = -1;
    private String nodeIpAddress = null;

    private Map diskLabels = null;

    private boolean useSMART = true;
    private Map smartCache = null; // Disk -> DiskHealth

    private Map currentMounts = null; // (cache) device -> mount-point
    private Set currentExports = null; // (cache) currently exported dirs

    private DiskOps diskOps = null;
    private String newfsOptions;
    private long newfsTimeout;
    private long fsckTimeout;

    private boolean terminate = false;
    private float availLimit;
    private long pollInterval;

    private DiskProxy proxy = null;
    private CheckerThread diskChecker = null;
    private QuorumThread quorumThread = null;
    private Thread diskmonitorThread = null;

    private String kernelLog;
    private KernelListener kernelListener = null;

    private int errorThreshold;
    private int[] errorCounts = null;
    private long[] proxyUsageUpdates = null;

    private Random rand = null;
    private float errorSimulationRate = 0.0f;
    private int cmRetryLimit;

    private boolean unmountOnExit = false;
    private long exitUnmountDelay;

    private static final Logger logger =
        Logger.getLogger(DiskMonitor.class.getName());

    static private ClusterProperties config = null;
    
    public DiskMonitor() {
        initStatics();

        profile = HardwareProfile.getProfile();

        unmountOnExit = getBooleanProperty(
            ConfigPropertyNames.PROP_DISK_UNMOUNT_ON_EXIT, false);
        exitUnmountDelay = 1000 * getLongProperty(
            ConfigPropertyNames.PROP_DISK_UNMOUNT_ON_EXIT_DELAY,
            DEFAULT_DISMOUNT_DELAY);

        availLimit = 1.0f - (0.01f * getIntProperty(
                                 ConfigPropertyNames.PROP_DISK_USAGE_CAP,
                                 DEFAULT_USAGE_CAP));
        int usageCap = (int)(100 * (1.0f - availLimit));

        errorThreshold = getIntProperty(
            ConfigPropertyNames.PROP_DISK_THRESHOLD,
            DEFAULT_ERROR_THRESHOLD);

        pollInterval = 1000 * getLongProperty(
            ConfigPropertyNames.PROP_DISK_POLL_INTERVAL,
            DEFAULT_POLL_INTERVAL);

        kernelLog = getStringProperty(
            ConfigPropertyNames.PROP_DISK_KERNEL_LOG,
            DEFAULT_KERNEL_LOG);

        newfsOptions = getStringProperty(
            ConfigPropertyNames.PROP_DISK_NEWFS_OPTIONS, "");
        newfsTimeout =
            1000 * getLongProperty(ConfigPropertyNames.PROP_DISK_NEWFS_TIMEOUT,
                DiskInitialize.DEFAULT_NEWFS_TIMEOUT_SECONDS);
        fsckTimeout =
            1000 * getLongProperty(ConfigPropertyNames.PROP_DISK_FSCK_TIMEOUT,
                DiskInitialize.DEFAULT_FSCK_TIMEOUT_SECONDS);

        errorSimulationRate = getFloatProperty(
            ConfigPropertyNames.PROP_DISK_DEBUG_ERROR_RATE,
            0.0f);

        cmRetryLimit = getIntProperty(
            ConfigPropertyNames.PROP_DISK_CM_NOTIFY_LIMIT,
            DEFAULT_RETRY_LIMIT);

        initDiskMonitorSubsystem(profile);
    }

    ////////////////////////////////////////////////////////////////

    /** Initialize statics: config, localized error stuff */
    private synchronized void initStatics() {
        if (config == null)
            config = ClusterProperties.getInstance();


    }

    private void prepareDisks(boolean erase) {
        /*
         * Initialize the disks
         */
        try {
            if (erase)
                DiskInitialize.eraseAndInitAllDisks(profile, this, cellId,
                                            (short) siloId, (short) nodeId);
            else
                DiskInitialize.initAllDisks(profile, this, cellId,
                                    (short) siloId, (short) nodeId);
        }
        catch (Exception e) {
            String os = System.getProperty("os.name");
            logger.log(Level.SEVERE, "DiskInitialize.init: ", e);

            Error error
                = new Error("Disk initialization failed (" + os + "): " + e);
            error.initCause(e);
            throw error;
        }
    }

    /**
     * Initialize the system: populate the DiskId -> Disk cache
     *
     * @param profile the system's profile
     */
    private void initDiskMonitorSubsystem(HardwareProfile profile) {
        this.profile = profile;

        NodeMgrService.Proxy nodeProxy =
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (nodeProxy == null) {
            throw new InternalException("cannot access node proxy");
        }
        nodeIpAddress = nodeProxy.getHostname();
        nodeId = nodeProxy.nodeId();
        cellId = DiskId.CELL_ID;
        siloId = DiskId.SILO_ID;

        diskOps = DiskOps.getDiskOps();

        if (errorSimulationRate > 0.0) {
            // To simulate disk failure
            logger.info("Simulating disk errors: rate " + errorSimulationRate);
            rand = new Random();
            rand.setSeed(System.currentTimeMillis());
        }

        disksInit(false);
    }

    private void disksInit(boolean erase) {

        diskMap = new TreeMap();
        proxyUsageUpdates = new long[profile.getMaxDisks()];
        numDisks = 0;

        diskArray = new Disk[profile.getMaxDisks()];
        for (int i = 0; i < diskArray.length; i++) {
            diskArray[i] = Disk.getNullDisk(cellId, siloId, nodeId, i, -1);
            diskArray[i].setMode(Disk.CHECKING);
        }

        smartCache =
            Collections.synchronizedMap(new HashMap(profile.getMaxDisks()));

        useSMART = profile.useSMART();

        for (int i = 0; i < proxyUsageUpdates.length; i++)
            proxyUsageUpdates[i] = 0L;

        String drv = profile.diskDriverName();
        kernelListener = new KernelListener(this, kernelLog, drv, diskArray);

        prepareDisks(erase);

        errorCounts = new int[profile.getMaxDisks()];
        for (int i = 0; i < errorCounts.length; i++)
            errorCounts[i] = 0;
        diskChecker  = new CheckerThread(config, diskArray, this);
        quorumThread = new QuorumThread(diskArray, this, cmRetryLimit);

        // Publish the proxy
        proxy = null;
        ServiceManager.publish(this);

        if (logger.isLoggable(Level.INFO))
            logger.info("DiskMonitor " +
                        cellId + "," + siloId + "," + nodeId +
                        " (" + profile.name() + ") monitoring " +
                        numDisks + " disks.");
    }

    /**
     * Execute initialization that needs to be done before we reach
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
    }


    public void run() {
        logger.info("Disk monitoring thread starting...");

        diskmonitorThread = Thread.currentThread();

        // Start the background checker threads
        diskChecker.start();
        quorumThread.start();
        kernelListener.start();

        // About once an hour, log statistics
        int numIter = (int) (3600000/pollInterval);
        int i = 0;

        if (logger.isLoggable(Level.INFO))
            logger.info("DiskMonitor " +
                        cellId + "," + siloId + "," + nodeId + " is running");

        // About once every 12 minutes, log disk fullness
        int count = 0;

        StringBuffer sb = new StringBuffer();

        while (!terminate) {

            // Simulate disk errors?
            if (rand != null)
                for (int j = 0; j < diskArray.length; j++)
                    if (rand.nextFloat() < errorSimulationRate) {
                        logger.warning("Simulated disk error on " +
                                       diskArray[j].getId());
                        reportError(diskArray[j]);
                    }

            if (count > 3) {
                sb.setLength(0);
                sb.append("diskUsage: ");
                checkUsage(sb); // JNI call to statvfs(2)
                logger.info(sb.toString());
                count = 0;
            } else
                checkUsage(null); // JNI call to statvfs(2)
            count++;

            updateIOStats();    // JNI call to kstat_open(3kstat)/kstat_read

            if (++i > numIter) {
                i = 0;
                logStats();
            }

            try {
                checkExports(); // Runs external program
            }
            catch(IOException e) {
                logger.warning("While trying to check exports:" + e);
            }

            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException ie) {
                logger.fine("Sleep interrupted");
            }
        }

        diskmonitorThread = null;

        if (unmountOnExit)
            unmountAll();

        if (logger.isLoggable(Level.INFO))
            logger.info("Exit.");
    }

    public void shutdown() {
        if (logger.isLoggable(Level.INFO))
            logger.info("Stopping all diskmonitor threads...");

        diskChecker.stop();
        quorumThread.stop();
        kernelListener.stop();

        terminate = true;
        if (diskmonitorThread != null) {
            diskmonitorThread.interrupt();

            try {
                diskmonitorThread.join();
            }
            catch (InterruptedException ie) {
                logger.fine("Sleep interrupted");
            }
            catch (NullPointerException e) {
                // This means that the main thread has already exited
                // and diskmonitorThread is no longer valid
                logger.fine("Shutdown NPE");
            }
            catch (Exception e) {
                logger.warning("Exception " + e + " on shutdown");
            }
        }
    }

    private void unmountAll() {
        logger.info("Disabling all disks");
        for (int i = 0; i < diskArray.length; i++)
            try {
                if (diskArray[i] != null)
                    disable(diskArray[i], LabelMode.DONT_WRITE,
                        DisableCause.INTENTIONAL);
            }
            catch(Exception e) {
                logger.log(Level.WARNING,
                           "Couldn't disable disk " + diskArray[i].getId(), e);
            }
    }

    public ManagedService.ProxyObject getProxy() {
        if (proxy == null)
            proxy = new DiskProxy(diskArray, nodeId);

        return proxy;
    }

    /**
     * When a disk is added to a running system, it is added to the
     * internal list and the ServiceManager is notified that a new
     * DiskProxy should be created.
     *
     * @param disk the disk being added
     * @param device the device
     */
    public void addDisk(DiskId disk, String device) {
        int status = Disk.OFFLINE;

        DiskLabel label = disk.label();
        if (label != null) {
            if (label.isDisabled())
                status = Disk.DISABLED;
            else if (label.isForeign())
                status = Disk.FOREIGN;
            else
                status = Disk.ENABLED;
        }

        addDisk(disk, device, status);
    }

    /**
     * When a disk is added to a running system, it is added to the
     * internal list and the ServiceManager is notified that a new
     * DiskProxy should be created.
     *
     * @param disk the disk being added
     * @param device the device
     * @param status the state of the disk
     */
    public void addDisk(DiskId disk, String device, int status) {

        try {
            logger.log(Level.INFO, "Adding disk " + disk +
                "(dev " + device + ") label: " + disk.label());

            newDisk(disk, device, status);
            kernelListener.addDisk(getDisk(disk));
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't add disk " + disk +
                       " to kernelListener", e);
        }
        ServiceManager.publish(this);
    }

    // Get the DiskLabel for a given disk.
    // For Disk Service testing purpose only.
    public DiskLabel getDiskLabel(DiskId disk_id) {
        Disk d;
        try {
            d = getDisk(disk_id);
        } catch (InternalException exp) {
            logger.log(Level.WARNING, "Unknown disk " + disk_id +
                       " passed ", exp);
            return null;
        }
        String device = getRawPartZero(d.getDevice());
        DiskLabel label = DiskLabel.checkAndGetLabel(device, (short)nodeId);
        if (label == null) {
            logger.log(Level.SEVERE, "Failed to get the label for " + disk_id);
            return null;
        }
        return label;
    }

    // Set the DiskLabel for a given disk.
    // For testing and diagnostic purpose only.
    public int setDiskLabel(DiskId disk_id, String labelstring) {
        Disk d;
        boolean toCommit = true;
        try {
            d = getDisk(disk_id);
        } catch (InternalException exp) {
            logger.log(Level.WARNING, "unknown disk" + disk_id +
                       "to setDiskLabel", exp);
            return -1;
        }
        try {
            DiskLabel l = d.getId().label();
            l.updateDiskLabel(labelstring, toCommit);
        } catch (IOException e) {
            logger.log(Level.WARNING, "failed to set label" + disk_id +
                       "setDiskLabel", e);
            return -1;
        }
        return 0;
    }

    public DiskHealth getHealth(DiskId diskId) throws IOException {
        String device = getDisk(diskId).getDevice();
        DiskHealth health = (DiskHealth) smartCache.get(device);
        if (health == null)
            health = getHealthFromDisk(device, diskId);

        return health;
    }

    /**
     * Lookup a disk ID in the disk map
     * Private because outside callers use DiskProxy.getDisk()
     *
     * @param diskId the ID of the disk
     * @return the disk device
     * @throws IOException on any error
     */
    public Disk getDisk(DiskId diskId) {
        try {
            return diskArray[diskId.diskIndex()];
        }
        catch (IndexOutOfBoundsException e) {
            throw new InternalException("Unknown disk " + diskId);
        }
    }

    // Enable/disable

    /**
     * Disable and dismount and write disklabel so it stays disabled
     * on reboot
     */
    public void disable(DiskId diskId) throws IOException {
        disable(getDisk(diskId), LabelMode.WRITE, DisableCause.INTENTIONAL);
    }

    /**
     * Disable and dismount and write disklabel so it stays disabled
     * on reboot.  Since we disabled due to a disk error, don't
     * set the clean unmount flag.
     */
    public void disableDueToError(DiskId diskId) throws IOException {
        disable(getDisk(diskId), LabelMode.WRITE, DisableCause.ERROR);
    }

    private void disableDueToPull(Disk disk) throws IOException {
        disable(disk, LabelMode.DONT_WRITE, DisableCause.PULL);
    }

    /**
     * Disable and dismount but don't write disklabel
     */
    public void dismount(DiskId diskId) throws IOException {
        disable(getDisk(diskId), LabelMode.DONT_WRITE, DisableCause.INTENTIONAL);
    }

    /**
     * Determine if the disk is configured appropriately for use for
     * data storage. If the disk is marked not valid for data storage
     * we take that at face value. However, if it is marked as valid, 
     * we double-check by probing for disk presence.
     * Return true if the disk is accessible, false otherwise.
     */
    public boolean isDiskConfigured(DiskId diskId) {
        Disk disk;
        try {
            logger.log(Level.INFO, "isDiskConfigured " + diskId);

            disk = getDisk(diskId);
        } catch (Exception e) {
            // should never happen
            logger.log(Level.WARNING, "Can't find disk for diskId " + diskId);
            return false;
        }

        switch (disk.getStatus()) {

        case Disk.DISABLED:
        case Disk.OFFLINE:
        case Disk.FOREIGN:
            logger.log(Level.INFO,
                "isDiskConfigured: DISABLED/OFFLINE/FOREIGN " +
                disk.getStatus());
            return false;

        case Disk.MOUNTED:
        case Disk.ENABLED:
            boolean r = diskOps.diskIsConfigured(disk.getDevice());
            logger.log(Level.INFO,
                "isDiskConfigured: MOUNTED/ENABLED, r " + r);
            return r;
         
        default:
            logger.log(Level.INFO, "isDiskConfigured: unknown status " +
                disk.getStatus());
            return false;
        }
    }

    private void disable(Disk disk, LabelMode labelWriteMode,
        DisableCause reason) throws IOException {

        if (disk == null) {
            logger.log(Level.WARNING, "disabled called with null disk");
            return;
        }
        if (!disk.isEnabled()) {
            if (logger.isLoggable(Level.INFO))
                logger.info("Disk already disabled: " + disk.getId());
            return;
        }

        disk.setStatus(Disk.DISABLED);

        try {
            forceUnmount(disk.getPath());
        } catch (IOException e) {
            // Do nothing: it's OK if the data partition wasn't
            // exported or mounted.
        }

        // If we are disabling due to errors w/the disk,
        // don't write out a clean unmount indication; that
        // indication is used to tell us that we don't need
        // do do a full fsck, but the contents of the disk
        // are suspect if are disabling it due to error.

        if (reason != DisableCause.ERROR && reason != DisableCause.PULL)
            recordCleanUnmount(disk.getDevice());

        if (labelWriteMode == LabelMode.WRITE) {
            DiskLabel label = disk.getId().label();
            label.setDisabled(true);
            label.writeLabel();
            // the "disabled" LED only gets turned on when the
            // disk is marked disabled (permanent, not temporary)
            ledCtl(disk.getId(), ON);

            // Ensure that the disk isn't used in any way.
            // We only do this in the persistent (write the label)
            // disable case.
            try {
                if (!diskOps.dumpSwapMirrorOffline(disk.getDevice())) {
                    if (logger.isLoggable(Level.INFO))
                        logger.log(Level.WARNING,
                            "Couldn't disable dump, swap, and mirrors on disk "
                            + disk.getId());
                }
            } catch (IOException e) {
                // nothing to do here
            }
        }

        // Wake up the quorum thread so it can refresh CM
        quorumThread.refresh();

        if (logger.isLoggable(Level.INFO)) {
            logger.info("Disk " + disk.getId() + " has been disabled due to " +
			reason);
        }
    }

    public boolean enable(DiskId diskId) throws IOException {
        if (enable(getDisk(diskId))) {
            proxy = null;
            ServiceManager.publish(this);
            return true;
        }
        return false;
    }

    private boolean enable(Disk disk) throws IOException {

        if (disk.isEnabled())
            return true;

        // Enable and online are converging; 
        // enable is now essentially an alias for online,
        // which we will be deprecating.

        return processOnline(disk.getId(), OnlineEnableAction.ENABLE);
    }

    /** Process a disk insertion */
    private boolean processOnline(DiskId diskId, OnlineEnableAction enableAction)
        throws IOException 
    {
        Disk disk = getDisk(diskId);
        return processOnline(disk, enableAction);
    }

    /**
     * Process a disk push (insertion)
     *
     * Note that is cribbed from processInsertion() and common code
     * should be refactored.
     */
    private boolean processOnline(Disk disk, OnlineEnableAction enableAction)
	throws IOException {

        String dataPartDev = disk.getDevice();
        String partZeroDev = getRawPartZero(dataPartDev);

        if (logger.isLoggable(Level.INFO))
            logger.info("processOnline: " + disk.getId() + " " + partZeroDev);

        // Need to bring the device online first, so
        // we can do IO to it
        diskOps.deviceOnline(dataPartDev);

        // If the disk doesn't have a label, it's a candidate for
        // replacement processing.

        if (!DiskLabel.hasValidLabel(partZeroDev)) {
            if (logger.isLoggable(Level.INFO))
                logger.log(Level.INFO, "processOnline: disk " + partZeroDev +
                    " doesn't have a valid label ");
            
            // Force the disk replacement script
            // to consider it to be a new disk.
            String baseDevice = getBaseDevName(partZeroDev);
            deleteSerialNumFromMap(baseDevice);

            DiskLabel.deleteDisk(partZeroDev);

            // A disabled disk may have been replaced by this
            // blank disk; we should clear any stale
            // clean unmount indication.
            clearStaleCleanUnmount(dataPartDev);
            
            try {
                diskOps.processReplacedDisks(
                    DiskInitialize.DISK_REPLACEMENT_TIMEOUT);
            } catch (IOException ioe) {
                logger.log(Level.WARNING,
                        "Can't do replacement processing on wiped disk");
            }

            // Assign a label to the disk.
            DiskLabel.newDisk(cellId, (short)siloId, (short)nodeId, partZeroDev);
        }

        DiskLabel label = DiskLabel.checkAndGetLabel(partZeroDev, (short)nodeId);
        if (label == null) {
            logger.log(Level.WARNING,
                "processOnline: no disk label after replacement processing: ",
                partZeroDev);
            return false;
        }

        disk.getId().setLabel(label);

        boolean doSetEnabled = false;

        if (label.isForeign()) {
            logger.info("Disk " + disk.getId() + " is misplaced");
	    ledCtl(disk.getId(), ON);
            disk.setStatus(Disk.FOREIGN);

        } else if (enableAction == OnlineEnableAction.OBEY_LABEL &&
            label.isDisabled()) {
            // disk is disabled
	    logger.info("Disk " + disk.getId() + " is disabled");
	    ledCtl(disk.getId(), ON);
	    disk.setStatus(Disk.DISABLED);

        } else if ((enableAction == OnlineEnableAction.ENABLE)  ||
            (enableAction == OnlineEnableAction.OBEY_LABEL &&
                !label.isDisabled())) {
            // enable
            ledCtl(disk.getId(), OFF);
            label.setDisabled(false);
            label.writeLabel();
            doSetEnabled = true;

        } else {
            logger.log(Level.WARNING,
                "processOnline: unanticipated condition: action " +
                enableAction + " label.isDisabled() " + label.isDisabled());
        }
        
        String dev = label.getDevice();
        try {
            DiskInitialize.initializeDisk(profile, nodeId, 
                dev, label, this);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't initialize", e);
            return false;
        }

        if (doSetEnabled)
            disk.setStatus(Disk.ENABLED);

        // Tell CM about the (possible) new disk
        quorumThread.refresh();

        if (disk.isEnabled() && logger.isLoggable(Level.INFO))
            logger.info("Disk " + disk.getId() + " has been brought online");

        return true;
    }

    /**
     * Process a disk pull request.
     *
     * This should only be called on non-enabled disk (ie a disk that
     * is not being used in any way).  Takes the attachment point
     * offline and sets the status of the Disk object to ABSENT.
     */ 
    boolean processOffline(DiskId diskId) throws IOException {

        Disk disk = getDisk(diskId);

        if (disk == null) {
            logger.warning("processOffline: couldn't getDisk(" + diskId + ")");
            return false;
        }

        // debug
        if (logger.isLoggable(Level.INFO))
            logger.info("processOffline (diskId): " + disk.getId() + " " +
			disk.getDevice());

        if (disk.isEnabled()) {
            logger.log(Level.WARNING, "processOffline: " + disk.getId() +
		       " " + disk.getDevice() + " should not be enabled!");
            return false;
        }

        return processOffline(disk);
    }

    /**
     * Process a disk pull request.
     *
     * Assumes that the disk is disabled or has been taken offline.
     * Takes the attachment point offline and sets the status of the
     * Disk object to ABSENT.
     */ 
    private boolean processOffline(Disk disk) throws IOException {

        if (disk == null) {
            logger.warning("processOffline: called with null disk");
            return false;
        }

        String device = disk.getDevice();

        // debug
        if (logger.isLoggable(Level.INFO))
            logger.info("processOffline (disk): " + disk.getId() + " " +
                device);

        disableDueToPull(disk);

        boolean deviceOfflineWorked = false;

        try {
            deviceOfflineWorked =
                diskOps.deviceOffline(device);
        } catch (IOException e) {
            // nothing to do
        }

        if (deviceOfflineWorked == false) {
            logger.log(Level.WARNING, "Couldn't prepare disk " +
                disk.getId() + " for removal");
            // nothing more to do
            return false;
        }

        DiskLabel.deleteDisk(getRawPartZero(device));
        disk.setStatus(Disk.ABSENT);
        quorumThread.refresh();
        return true;
    }

    /**
     * Process a disk insertion event, sent to us by the
     * {@link KernelListener} thread.
     */
    public boolean processInsertion(String device) {
        
        // debug
        if (logger.isLoggable(Level.INFO))
            logger.info("processInsertion(" + device + ")");
        
        Disk disk = findDisk(device);
        
        if (disk == null) {
            logger.info("Can't find disk for device " + device);
            return false;
        }
        
        boolean retval = false;
        
        try {
            retval = processOnline(disk, OnlineEnableAction.OBEY_LABEL);
        } catch (IOException ioe) {
            logger.warning("Couldn't bring disk " + device + " online " + ioe);
        } finally {
            return retval;
        }
    }
    

    /**
     * Process a disk deletion event, sent to us by the
     * {@link KernelListener} thread.
     *
     * This method is called in response to a deletion event.
     */
    public boolean processDeletion(String device) {
        // debug
        if (logger.isLoggable(Level.INFO))
            logger.info("processDeletion(" + device + ")");
        
        Disk disk = findDisk(device);
        
        if (disk == null) {
            logger.info("Can't find disk for device " + device);
            return false;
        }
        
        boolean retval = false;
        
        try {
            retval = processOffline(disk);
        } catch (IOException ioe) {
            logger.warning("Couldn't bring disk " + device + " online " + ioe);
        } finally {
            return retval;
        }
    }

    // Find a disk, given the device.
    // Device must be the data partition device, e.g. /dev/dsk/c0t0d0s4
    private Disk findDisk(String device) {
        for (int i = 0; i < diskArray.length; i++) {
            if (device.indexOf(diskArray[i].getDevice()) >= 0) {
                return diskArray[i];
            }
        }
        return null;
    }

    void markMissingAndAbsent(Disk disk) throws IOException {
        // debug
        logger.log(Level.WARNING, "markMissingAndAbsent(" +
            disk.getDevice() + ")");

        disk.setStatus(Disk.ABSENT);
    }

    //
    // WipeAll() is called from admin (CLI request to wipe the cluster)
    // The contract is that cluster has been brought into maintenance mode
    // before we start that operation.
    //
    public void wipeAll() throws IOException {

        logger.info("**** CLUSTER WIPE ****");

        // Check we entered maintenance mode
        NodeMgrService.Proxy proxy = 
          ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (!proxy.isMaintenance()) {
            throw new RuntimeException("Need maintenance mode before we can " +
              "wipe all disks");
        }
        //
        // The operation will first disable the disks and then
        // proceed asynchronously.
        //
        int fsType = profile.getPartitionType(profile.dataPartitionIndex());
        WipeThread wiper = new WipeThread(diskArray, this, diskOps, fsType);
        wiper.start();
    }

    public void wipe(DiskId diskId) throws IOException {
        wipe(getDisk(diskId));
    }

    private void wipe(Disk disk) throws IOException {
        if (disk == null)
            return;

        if (disk.isEnabled())
            throw new IOException("Cowardly refusal to wipe enabled disk" +
                                  disk.getId());

        // The disk should be unmounted when it's disabled. Verify
        if (currentMounts.get(disk.getDevice()) != null)
            throw new IOException("Couldn't unmount " + disk.getDevice());

        // make a new filesystem on the disk and mount it

        int fsType = profile.getPartitionType(profile.dataPartitionIndex());
        if(!DiskInitialize.getMKFSLock(disk.getDevice())) {
            logger.warning("skipping mkfs for " + disk +
                " b/c someone else is already doing it.");
        } else {
            try {
                diskOps.mkfs(disk.getDevice(), fsType, newfsOptions,
                    newfsTimeout);
            } finally {
                DiskInitialize.releaseMKFSLock(disk.getDevice());
            }
            diskOps.mount(disk.getDevice(), fsType, disk.getPath(), null);

            clearCleanUnmount(disk.getDevice());

            if(!enable(disk)) {
                logger.severe("Wipe failed to enable disk " + disk);
            }
        }
    }

    public void fullWipe(DiskId diskId) throws IOException {
        fullWipe(getDisk(diskId));
    }


    /**
     * Completely wipe a disk.
     */
    private void fullWipe(Disk disk) throws IOException {
        assert(disk != null);

        if (disk.isEnabled()) {
            logger.warning(
                "Cowardly refusal to wipe enabled disk" + disk.getId());
            throw new IOException("Cowardly refusal to wipe enabled disk" +
                disk.getId());
        }

        logger.log(Level.INFO, "Wiping disk " + disk.getId());

        String device = disk.getDevice();
        String baseDevice = getBaseDevName(device);
        String rawPartZeroDevice = getRawPartZero(device);
        DiskLabel label = disk.getId().label();
        String labelDevice = label.getDevice();

        // Clear the label and remove the disk serial number from
        // the list of known disks so the disk is subjected to full
        // disk replacement processing.

        label.smash();
        deleteSerialNumFromMap(baseDevice);

        // Make sure the disk isn't used in any way.
        boolean dsmoWorked = false;
        try {
            dsmoWorked = diskOps.dumpSwapMirrorOffline(device);
        } catch (IOException e) {
            // nothing to do
        }
        if (dsmoWorked == false) {
            logger.log(Level.WARNING,
                "Couldn't disable dump, swap, and mirrors on disk "
                + disk.getId());
            return;
        }

        DiskLabel.deleteDisk(labelDevice);

        // Force disk replacement processing inline.
        // Note that Disk replacement detaches and attaches zpool slices.
        try {
            diskOps.processReplacedDisks(
                DiskInitialize.DISK_REPLACEMENT_TIMEOUT);
        } catch (IOException ioe) {
            logger.log(Level.WARNING,
                "Can't do replacement processing on wiped disk " +
                disk.getId());
            return;
        }

        // Assign a label to the disk.
        DiskLabel.newDisk(cellId, (short)siloId, (short)nodeId, labelDevice);

        label = DiskLabel.checkAndGetLabel(labelDevice, (short)nodeId);
        if (label == null) {
            logger.log(Level.WARNING, "couldn't read new label: ",
                labelDevice);
            return;
        }

        try {
            DiskInitialize.initializeDisk(profile, nodeId,
                labelDevice, label, this);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't initialize " + labelDevice, e);
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("warn.disk.monitor.initialize");
            Object [] args = {new String(labelDevice),
                              new String(e.getMessage())};
            logger.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));
            return;
        }

        ledCtl(disk.getId(), OFF);
        label.setDisabled(false);
        label.writeLabel();

        // Tell CM about the (possible) new disk
        quorumThread.refresh();

        logger.info("Wiped disk " + disk.getId() + " has been brought online");
    }

    /**
     * Delete the disk's serial number from /config/diskmap
     */
    private void deleteSerialNumFromMap(String device) throws IOException {
        try {
            diskOps.deleteSerialNumberMapEntry(device);
        } catch (IOException e) {
            logger.severe("Couldn't delete serial number for " + device +
                " " + e);
        }
    }

    public boolean reportError(DiskId diskId) {
        return reportError(getDisk(diskId));
    }

    public synchronized boolean reportError(Disk disk) {
        int d = disk.diskIndex();
        if (d < 0 || !disk.isEnabled())
            return false;

        logger.warning("Found error on disk " + disk.getId());

        if (++errorCounts[d] >= errorThreshold) {
            String msg = "More than " + errorThreshold + " errors on disk" +
                disk.getId() + " (" + disk.getPath() + "): disabling";
            logger.severe(msg);
            try {
                disable(disk, LabelMode.WRITE, DisableCause.ERROR);
                return true;
            }
            catch (Exception e) {
                logger.log(Level.INFO, "Couldn't disable " + disk.getId(), e);
            }
        }
        return false;
    }

    // Evacuation will not be implemented in 1.0, so these will just
    // throw an "unimplemented" exception
    public void evacuate(DiskId diskId) throws IOException {
        logger.severe("Implement me!");
        throw new InternalException("Evacuation is unimplemented.");
    }
    public void stopEvacuate(DiskId diskId) throws IOException {
        logger.severe("Implement me!");
        throw new InternalException("Evacuation is unimplemented.");
    }

    public boolean onlineDisk(DiskId diskId) throws IOException {
        logger.warning("DiskMonitor.onlineDisk");

        Disk disk = getDisk(diskId);

        if (disk == null) {
            logger.warning("Couldn't find disk" + diskId);
            return false;
        }

	boolean onlineWorked = processOnline(diskId,
					     OnlineEnableAction.OBEY_LABEL);
	logger.warning("online of disk " + disk.getDevice() +
		       (onlineWorked ? " worked" : " failed"));
	return onlineWorked;
    }

    public void offlineDisk(DiskId diskId) throws IOException {
        logger.warning("DiskMonitor.offlineDisk");

        Disk disk = getDisk(diskId);

        if (disk == null) {
            logger.warning("Couldn't find disk" + diskId);
            return;
        }

        if (processOffline(diskId)) {
            logger.warning("offline of disk " + disk.getDevice() + " worked");
        } else {
            logger.warning("offline of disk " + disk.getDevice() + " failed");
        }
    }

    public void pullDisk(DiskId diskId) throws IOException {
        logger.warning("DiskMonitor.diskPull");

        Disk disk = getDisk(diskId);

        if (disk == null) {
            logger.warning("Couldn't find disk" + diskId);
            return;
        }

        if (processDeletion(disk.getDevice())) {
            logger.warning("pull of disk " + disk.getDevice() + " worked");
        } else {
            logger.warning("pull of disk " + disk.getDevice() + " failed");
        }
    }

    /**
     * Convert a data slice name to the "base" c?t?d? device name.
     */
    static String getBaseDevName(String dataDev) {
        Matcher partitionMatcher = partitionPattern.matcher(dataDev);
        boolean found = partitionMatcher.find();

        assert(found);
        assert(partitionMatcher.groupCount() == 1);

        return partitionMatcher.group(1);
    }

    /**
     * Convert a data slice name to the partition 0 name.
     */
    static String getRawPartZero(String dataDev) {
        return ("/dev/rdsk/" + getBaseDevName(dataDev) + "p0");
    }

    public void pushDisk(DiskId diskId) throws IOException {
        logger.warning("DiskMonitor.diskPush");

        Disk disk = getDisk(diskId);

        if (disk == null) {
            logger.warning("Couldn't find disk" + diskId);
            return;
        }

        // the disk device is actually the data slice (/dev/dsk/cXdXs4)
        // need to mung that into /dev/rdsk/cXdXpX
        String partitionZeroDev = getRawPartZero(disk.getDevice());

        if (processInsertion(partitionZeroDev)) {
            logger.warning("push of disk " + partitionZeroDev + " worked");
        } else {
            logger.warning("push of disk " + partitionZeroDev + " failed");
        }
    }


    // Misc accessors
    // XXX used?  Needed?
    public int nodeId() { return nodeId; }
    public HardwareProfile profile() { return profile; }


    // The wipe thread calls back here to re-initialize all disks
    void reInitDisks() {

        kernelListener.stop();
        diskChecker.stop();
        quorumThread.stop();

        disksInit(true);

        // Start the background checker threads
        kernelListener.start();
        diskChecker.start();
        quorumThread.start();
    }

    /**
     * When a disk is added to the system -- both at hot-swap and
     * system startup -- this method adds it to its internal list of
     * disks.
     */
    private void newDisk(DiskId diskId, String device, int status) {
        if (logger.isLoggable(Level.INFO))
            logger.info("Adding disk " + diskId + " " +
                        StringUtil.image(device) +
                        " status: " + Disk.getStatusString(status));

        // The array of partitions on a disk
        String[] partitions = new String[profile.getNumPartitions()];
        for (int i = 0; i < partitions.length; i++)
            partitions[i] = profile.getPartitionDesc(i);

        String partitionDevice =
            diskOps.getPartitionDevice(device, profile.dataPartitionIndex(),
                                       partitions);
        String serial = "???";
        try {
            serial = diskOps.getSerialNo(device);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "No serial number for " + device, e);
        }

        Disk disk;
        try {
            disk = makeDiskObj(diskId, partitionDevice, status, Disk.NORMAL,
                               serial);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't create Disk ", e);
            disk = Disk.getNullDisk(diskId);
        }

        if (status == Disk.FOREIGN) {
            // Send the alert that we can't use this disk
            Object[] args = {
                new Integer(DiskLabel.getSlotIndex(device)),
                new Integer(nodeId)
            };
            sendAlertWarning(ALERT_FOREIGN_DISK, args);

            // A disabled disk may have been replaced by this
            // foreign disk; we should clear any stale
            // clean unmount indication.
            clearStaleCleanUnmount(disk.getDevice());
        }
        else {
            Object [] args = {new Integer (diskId.nodeId())};
            sendAlertInfo(ALERT_NEW_DISK, args);
        }

        diskMap.put(diskId, disk);
        diskArray[diskId.diskIndex()] = disk;
        numDisks++;

        if (disk.isMounted()) {
            try {
                diskOps.export(disk.getPath());
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        ledCtl(disk.getId(), disk.isMounted() ? OFF : ON);

        if (logger.isLoggable(Level.INFO))
            logger.info("Added disk " + disk.getId() + " status: "
                        + disk.getStatusString());

        proxy = null;           // New one needs to be created
    }

    /**
     * Get information about a disk and put it in the cache
     *
     * @param diskId the ID of the disk
     * @param device the disk device name
     * @param status is the disk enabled or disabled
     * @param mode disk mode
     * @param serial disk serial number
     * @return information about the disk
     * @throws IOException on any error
     */
    private Disk makeDiskObj(DiskId diskId, String device,
                             int status, int mode, String serial) {

        String mountPoint =
            profile.getPathPrefix(profile.dataPartitionIndex()) + "/" +
            diskId.diskIndex();

        // Get filesystem's total size and free size
        long pSize  = 0, pAvail = 0;
        StatFS.Struct statfs = null;
        if (status != Disk.DISABLED && status != Disk.FOREIGN) {
            try {
                statfs = StatFS.statfs64(mountPoint);
            }
            catch (ClassNotFoundException e) {
                String msg = "JNI couldn't find statfs class";
                logger.log(Level.SEVERE, msg, e);
                throw new RuntimeException(msg);
            }
            catch (IllegalArgumentException e) {
                logger.warning("failed to get device stats: " + e);
            }
        }
        if (statfs != null) {
            pSize = statfs.totalMBytes();
            pAvail = statfs.availMBytes();
        }

        // we don't want to retrieve DiskHealth at every poll - that is
        // too expensive and far too disruptive to a disk's normal
        // operation. So we do it once initially and cache the
        // result. The cache is updated when getDiskHealth is invoked.

        DiskHealth smart = null;
        int badSectors = 0;
        int pendingBadSectors = 0;
        int temperature = 0;
        boolean smart_error = false;

        if (useSMART) {
            if ((smart = (DiskHealth) smartCache.get(device)) == null)
                try {
                    // get current DiskHealth and insert it in the cache
                    smart = getHealthFromDisk(device, diskId);
                }
                catch (IOException e) {
                    String msg = "Couldn't get DiskHealth from " + diskId +
                        " (\"" + device + "\")";
                    logger.log(Level.WARNING, msg, e);
                }

            if (smart != null) {
                badSectors = smart.getBadSectors();
                pendingBadSectors =  smart.getPendingBadSectors();
                temperature = smart.getTemperature();
                smart_error = smart.someThresholdExceeded();
            }
        }

        Disk disk = new Disk(diskId, device, mountPoint, nodeIpAddress,
                             status, mode,
                             pSize, pAvail, badSectors, pendingBadSectors,
                             temperature, smart_error);

        disk.setAltDevice(readlink(device));
        disk.setSerialNo(serial);

        logger.info("Sizes: disk " + disk.getPath() + " " +
                    StringUtil.image(serial) + ": " +
                    pSize + "MB with " + pAvail + "MB free");

        return disk;
    }

    ////////////////////////////////////////////////////////////////////////
    // Monitoring
    private DecimalFormat d = new DecimalFormat ("#######0.0#");
    private void checkUsage(StringBuffer sb) {
        Disk disk;

        for (int i = 0; i < diskArray.length; i++) {
            if ((disk = diskArray[i]) == null || !disk.isEnabled())
                continue;

            StatFS.Struct statfs = null;
            try {
                statfs = StatFS.statfs64(disk.getPath());
            }
            catch (ClassNotFoundException e) {
                String msg = "Couldn't load the StatFS class!";
                logger.severe(msg);
                throw new InternalException(msg);
            }
            catch (IllegalArgumentException e) {
                logger.log(Level.SEVERE, "Bad path \"" + disk.getPath() + "\"",
                           e);
                continue;
            }

            if (statfs == null) {
                logger.warning("Couldn't get disk usage for " +
                               disk.getPath());
                continue;
            }

            long avail = statfs.availMBytes();
            long total = statfs.totalMBytes();

            disk.setAvailableSize(avail);

            // If avail. changed more than 0.1% update the proxy
            if (Math.abs(proxyUsageUpdates[i] - avail) > total * 0.001) {
                proxyUsageUpdates[i] = avail;
                proxy = null;
            }

            float fracFree = avail/(float)total;
            if (sb != null) {
                sb.append(disk.getPath()).append(": ").append(
                          d.format((1.0-fracFree) * 100.0)).append("% ");
            }

            if (fracFree < availLimit) {

                // set flag in disk object (read by LayoutService)
                if (!disk.getUsageCapReached()) {
                    disk.setUsageCapReached(true);
                    proxy = null;
                    logger.warning("usage now above cap for " + disk);
                }

                // log warning and send alert.
                // Should we move this into the above if-block, so that
                // we only log/alert once instead of on each checkUsage?
                logger.warning("Freespace < " + availLimit + " for " + disk);

            }
            else {

                // check if we just dropped below the usage cap
                if (disk.getUsageCapReached()) {
                    disk.setUsageCapReached(false);
                    proxy = null;
                    logger.info("usage dropped below cap for " + disk);
                }

            }
        }

        // Need to update the proxy?
        if (proxy == null)
            ServiceManager.publish(this);

    // XXX TODO: write the logs for when a disk is disabled/removed

    }

    private void checkExports() throws IOException {
        boolean tookAction = false;
        Set currentExports = diskOps.getCurrentExports();

        for (int i = 0; i < profile.getMaxDisks(); i++) {
            Disk disk = diskArray[i];
            if (disk == null || !disk.isEnabled())
                continue;

            String mountPoint = disk.getPath();
            if (!currentExports.contains(mountPoint)) {
                if (logger.isLoggable(Level.INFO))
                    logger.info("Disk " + mountPoint +
                                " was not exported: re-exporting");

                diskOps.export(mountPoint);
                tookAction = true;
            }
        }

        if (tookAction && logger.isLoggable(Level.INFO)) {
            String msg = "Current exports were";
            for (Iterator i = currentExports.iterator(); i.hasNext(); )
                msg += " " + (String) i.next();
            logger.info(msg + ".");
        }
    }


    private void forceUnmount(String mountPoint) throws IOException {
        int fsType = profile.getPartitionType(profile.dataPartitionIndex());

        try {
            diskOps.unexport(mountPoint);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Unexporting \"" + mountPoint + "\"", e);
        }

        try {
            diskOps.umount(mountPoint, fsType, true);
        } catch (Exception e) {
            if (!isMounted(mountPoint)) {
                // If the umount failed due to the mountpoint not
                // being mounted, emit a non-scary warning.
                logger.log(Level.WARNING, "forceUnmount(\"" + mountPoint +
                    "\") was already unmounted");
            } else {
                logger.log(Level.WARNING, "Unmounting \""
                    + mountPoint + "\"", e);
            }
        }
    }


    /**
     * Is the device mounted?
     */
    private boolean isMounted(String mountPoint) throws IOException {
        Map currentMounts = diskOps.getCurrentMounts(); // dev -> mountPt
        boolean mounted = currentMounts.containsValue(mountPoint);

        return (mounted);
    }

    /**
     * A note regarding various indicator files in /config:
     * 
     * We use several different indicator files in /config
     * to record information which should persist across boot.
     *
     * clean umount indicator:
     *	Indicates that a data partition was unmounted cleanly.
     *  The presence of this flag tells us that we don't need
     *  to do an full fsck on the data partition.
     *
     *  example: /config/clean_unmount__dev_dsk_c0t0d0s4
     *
     * need to newfs indicator:
     *	Indicates that we need to newfs the data partition.  This
     *  file is used by the disk_replacement script to communicate
     *  with the DiskInitialize code.
     *
     *  example: /config/newfs_data_slice_dev_dsk_c1t1d0s4
     * 
     */

    /**
     * Return the file name for the clean unmount indicator.
     */
    private static String unmountFileName(String mountDev) {
        return CLEAN_UNMOUNT_PREFIX + mountDev.replace("/", "_");
    }

    /**
     * Record the fact that we cleanly umounted the file system.
     */
    public static void recordCleanUnmount(String mountDev) {
        String fname = unmountFileName(mountDev);

        if (logger.isLoggable(Level.INFO))
            logger.info("Recording clean unmount of " + fname);

        try {
            File file = new File(fname);
            if (!file.createNewFile()) {
                // This is a programming error.
                logger.log(Level.WARNING, "recordCleanUnmount: " + fname +
                    " already exists");
            }
        } catch (IOException e) {
            // Shouldn't happen
            logger.log(Level.WARNING, "recordCleanUnmount: " + fname +
                " couldn't create " + e);
        }
    }

    /**
     * Clear a stale "cleanly unmounted partition" indication.  There may not
     * be such an indication; that's OK.
     */
    public static void clearStaleCleanUnmount(String mountDev) {
        if (didCleanUnmount(mountDev)) {

            if (logger.isLoggable(Level.INFO))
                logger.info("Removing stale clean unmount indication for " +
                    mountDev);

            clearCleanUnmount(mountDev);
        }
    }

    /**
     * Clear the "cleanly unmounted partition" indication for the partition.
     */
    public static void clearCleanUnmount(String mountDev) {
        String fname = unmountFileName(mountDev);

        if (logger.isLoggable(Level.INFO))
            logger.info("Clearing clean unmount indication for " + fname);

        boolean success = (new File(fname)).delete();
        
        if (!success) {
            // This is a programming error.
            logger.log(Level.WARNING, "clearCleanUnmount: " + fname +
                " doesn't exist");
        }
    }

    /**
     * Did we cleanly umount the file system?
     */
    public static boolean didCleanUnmount(String mountDev) {
        String fname = unmountFileName(mountDev);

        return ((new File(fname)).exists());
    }

    private void updateIOStats() {
        if (profile.hostOS() != HardwareProfile.OS_SOLARIS)
            return;

        for (int i = 0; i < diskArray.length; i++) {
            Disk disk = diskArray[i];
            if (disk == null || !disk.isEnabled())
                continue;

            // Find the instance number of the driver.  (Instances
            // start from 1.)
            int instance = 1 + DiskLabel.getSlotIndex(disk.getDevice());

            // Get the kstat

            Kstat kstat = null;
            try {
                kstat = Kstat.get(profile.diskDriverName(), instance);
            }
            catch (Exception e) {
                logger.log(Level.WARNING,
                           "Couldn't get kstat for " + disk.getId(), e);
            }
            if (kstat == null) {
                logger.warning("No IO stats for disk " + disk.getId() +
                               " (no kstat found)");
                continue;
            }

            Kstat.IOStat iostat = null;
            try {
                iostat = kstat.getIOStat();
            }
            catch (Exception e) {
                logger.log(Level.WARNING,
                           "Couldn't get iostat for " + disk.getId(), e);
            }
            if (iostat == null) {
                logger.warning("No IO stats for disk " + disk.getId() +
                               " (couldn't get iostat from kstat)");
                continue;
            }

            if (logger.isLoggable(Level.FINE))
                logger.fine(disk.getId() + ": @" + kstat.snapshotTime() + " " +
                            iostat.bytesRead() + " read, " +
                            iostat.bytesWritten() + " written");

            // Update the disk object
            disk.updateIOStats(kstat.snapshotTime(),
                               iostat.bytesRead(), iostat.bytesWritten());

        }
    }

    private static void sendAlertInfo(String msgName, Object [] args) {
        String str = BundleAccess.getInstance().getBundle().getString(msgName);
        logger.log(ExtLevel.EXT_INFO, MessageFormat.format(str, args));
    }

    private static void sendAlertWarning(String msgName, Object [] args) {
        String str = BundleAccess.getInstance().getBundle().getString(msgName);
        logger.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));
    }

    private void logStats() {
        if (!logger.isLoggable(Level.INFO))
            return;

        for (int i = 0; i < diskArray.length; i++)
            if (diskArray[i] != null)
                logger.info("Status: " + diskArray[i]);
    }

    private DiskHealth getHealthFromDisk(String device, DiskId diskId)
            throws IOException {
        if (!useSMART)
            return null;

        try {
            DiskHealth smartInfo = new DiskHealth(device, diskId);
            smartCache.put(device, smartInfo);
            return smartInfo;
        }
        catch (Exception e) {
            logger.warning("Couldn't get SMART data for " + device);
            return null;
        }
    }

    /**
     * If path is a symlink, use readlink(2) to find its dest
     *
     * @param path the symlink to read
     * @return the file it points to (null if not a symlink)
     */
    private static String readlink(String path) {
        // Need a JNI method to call readlink(2). For now just use ls -l
        BufferedReader reader = null;
        try {
            String cmd = "/usr/bin/ls -l " + path;

            reader = Exec.execRead(cmd, logger);
            String line = reader.readLine();
            if (line == null)
                return null;

            int pos = line.lastIndexOf(" -> ");
            if (pos < 0)
                return null;

            String dest = line.substring(pos + 4);

            // If it's a relative path we need to fix it
            if (dest.startsWith("/"))
                return dest;

            String dir = new File(path).getParentFile().getCanonicalPath();
            return new File(dir + "/" + dest).getCanonicalPath();
        }
        catch (IOException e) {
            logger.log(Level.WARNING,
                       "readlink " + path + ": can't monitor kernel logs", e);
            return null;
        }
        finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {}
        }
    }

    private static void ledCtl(DiskId disk, boolean isOn) {
        if (logger.isLoggable(Level.INFO))
            logger.info("LED " + "hcb" + disk.nodeId() +
                        " DISK-" + disk.nodeId() + ":" + disk.diskIndex() +
                        (isOn? " on" : " off"));

        NodeMgrService.Proxy nodeProxy =
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        if (nodeProxy == null) {
            throw new InternalException("cannot access node proxy");
        }

        nodeProxy.remoteExec(Ipmi.localDiskLedStr(disk, isOn), logger);

    }

    /**
     * Get this node's UUID.
     */
    public static String getNodeUUID() {
        // XXX this looks a lot like getSpUUID(); common code should
        // be factored out.

        logger.info("Getting node UUID");
        String uuid = "UNKNOWN";

        /*
         * Create the command to retrieve the UUID
         */
        String cmd = "dmidecode|grep -i uuid|cut -f 2 -d :";

        /*
         * Exec the command
         */
        try {
            BufferedReader out = Exec.execRead(cmd, logger);
            uuid = out.readLine().trim();
            out.close();
        } catch (IOException e) {
            logger.severe("node uuid retrieve failed: " + e);
        } catch (Exception e) {
            // XXX: non-IOException: need to figure out the source of these
            // in a future release.
            logger.log(Level.SEVERE, "node uuid retrieve failed due to non-IOexception", e);
        }

        return uuid;
    }

    private String getStringProperty(String pname, String defaultVal) {
        try {
            String s = config.getProperty(pname);
            if (s != null) return s;
        }
        catch (Exception e) {}

        return defaultVal;
    }

    private boolean getBooleanProperty(String pname, boolean defaultVal) {
        String s;
        try {
            if ((s = config.getProperty(pname)) == null)
                return defaultVal;

            return (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes"));
        }
        catch (Exception e) {}

        return defaultVal;
    }

    private int getIntProperty(String pname, int defaultVal) {
        String s = null;
        try {
            if ((s = config.getProperty(pname)) == null)
                return defaultVal;

            return Integer.parseInt(s);
        }
        catch (NumberFormatException e) {
            logger.warning("Couldn't parse " + pname + " = " +
                           StringUtil.image(s) +
                           "; using default " + defaultVal);

        }

        return defaultVal;
    }

    private float getFloatProperty(String pname, float defaultVal) {
        String s = null;
        try {
            if ((s = config.getProperty(pname)) == null)
                return defaultVal;

            return Float.parseFloat(s);
        }
        catch (NumberFormatException e) {
            logger.warning("Couldn't parse " + pname + " = " +
                           StringUtil.image(s) +
                           "; using default " + defaultVal);

        }

        return defaultVal;
    }

    private long getLongProperty(String pname, long defaultVal) {
        String s = null;
        try {
            if ((s = config.getProperty(pname)) == null)
                return defaultVal;

            return Long.parseLong(s);
        }
        catch (NumberFormatException e) {
            logger.warning("Couldn't parse " + pname + " = " +
                           StringUtil.image(s) +
                           "; using default " + defaultVal);

        }

        return defaultVal;
    }
}
