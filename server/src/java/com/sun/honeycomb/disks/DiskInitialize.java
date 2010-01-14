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



package com.sun.honeycomb.disks;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.lang.reflect.*;
import java.text.MessageFormat;

import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.AdminResourcesConstants;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.common.Getopt;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.oa.upgrade.Upgrader;
import com.sun.honeycomb.oa.upgrade.UpgraderException;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.util.ExtLevel;

import com.sun.honeycomb.diskmonitor.DiskMonitor;



public class DiskInitialize {

    private static final String LOCK_DIR="/tmp";
    private static final String FSCK_LOCK_PRE = LOCK_DIR + "/fsck";
    private static final String MKFS_LOCK_PRE = LOCK_DIR + "/mkfs";
    private static final String DISK_REPLACE_LOCK = LOCK_DIR + "/diskreplace";
    private static final String NEWFS_INDICATOR_PREFIX =
        "/config/newfs_data_slice";

    private static final long LOCK_TIMEOUT_SECS = 3600;

    private static final String PS_CMD = "/usr/bin/ps -elf";

    public static final long DEFAULT_NEWFS_TIMEOUT_SECONDS = (40*60); // 40 minutes
    public static final long DEFAULT_FSCK_TIMEOUT_SECONDS = (40*60);  // 40 minutes
    public static final long DISK_REPLACEMENT_TIMEOUT = 3600*1000; // 1 hour
    private static final long DISK_REPLACEMENT_WAIT_SLEEP = 10*1000; // ms

    private static final String myName = DiskInitialize.class.getName();
    private static final Logger logger = Logger.getLogger(myName);

    private enum IsRunning { CHECK, IGNORE };

    private static DiskOps ops = null;

    ClusterProperties config = null;

    // For each service to vet disks
    private static Method[] vetters = null;

    // If virtual disks are being used, these are the backing files' properties
    private static final String VDISK_PREFIX = "/hc_disks/disk";
    private static final int VDISK_SIZE_MB = 500;

    private Map currentMounts;
    private boolean initialized = false;
    private boolean eraseAll = false;
    private HardwareProfile profile;
    private String[] partitions;

    private boolean newfsOnFailedFsck = false;
    private boolean newfsOnFailedMount = false;

    private String newfsOptions;
    private String mountOptions;
    private long newfsTimeout;
    private long fsckTimeout;

    private DiskMonitor diskMonitor = null;

    // accessors synchronize access to this
    private boolean waitForDiskReplacementProcessing = false;

    /**
     * Erase and initialize all disks known to the system
     */
    public static void eraseAndInitAllDisks(HardwareProfile profile,
                                    DiskMonitor diskMonitor,
                                    int cellId, short siloId, short nodeId) {
        logger.info("Erasing and initializing all disks.");
        doInitAllDisks(profile, diskMonitor, cellId, siloId, nodeId, true);
    }

    /**
     * Initialize all disks known to the system
     */
    public static void initAllDisks(HardwareProfile profile,
                            DiskMonitor diskMonitor,
                            int cellId, short siloId, short nodeId) {
        logger.info("Starting to initialize all disks.");
        doInitAllDisks(profile, diskMonitor, cellId, siloId, nodeId, false);
    }

    public String toString() {
        return myName;
    }

    /**
     * Initialize all disks known to the system
     */
    private static void doInitAllDisks(HardwareProfile profile,
                               DiskMonitor monitor,
                               int cellId, short siloId, short nodeId,
                               boolean erase) {
        initStatics();

        try {
            if (ops == null)
                ops = DiskOps.getDiskOps();

            DiskInitialize di = new DiskInitialize(profile, monitor, erase);

            List paths;
            if (profile.useVirtualDisks())
                paths = di.getVirtualDisks();
            else
                paths = ops.getDiskPaths(profile.diskType());

            Map allDisks =
                DiskLabel.probeDisks(cellId, siloId, nodeId, paths, erase);
            di.initializeDisks(allDisks, nodeId);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't initialize disks", e);
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("err.disk.initialize.alldisks");
            Object [] args = {new String(e.getMessage())};
            logger.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

        }
    }

    /** Initialize statics */
    private static synchronized void initStatics() {
        if (ops != null)
            return;

        // These classes must have a "checkAndRepair" method:
        //     static boolean checkAndRepair(String dir)
        String[] checkerNames = new String[] {
            "oa.DirChecker",
        };

        List svcs = new LinkedList();
        String baseName = "com.sun.honeycomb.";

        for (int i = 0; i < checkerNames.length; i++) {
            if (checkerNames[i] == null)
                continue;

            String className = baseName + checkerNames[i];
            Class c = null;
            try {
                c = Class.forName(className);
            }
            catch (ClassNotFoundException ignored) {}

            if (c == null) {
                logger.severe("Class " + className + " not found.");
                continue;
            }
            Method m = null;
            try {
                m = c.getMethod("checkAndRepair", new Class[]{String.class});
            }
            catch (SecurityException e) {
                logger.log(Level.SEVERE,
                           "Couldn't get checkAndRepair for " + className, e);
            }
            catch (NoSuchMethodException ignored) {}

            if (m == null)
                logger.severe("Class " + className +
                              ": no suitable checkAndRepair method.");
            else
                svcs.add(m);

            ops = DiskOps.getDiskOps();
        }

        int j = 0;
        vetters = new Method[svcs.size()];
        for (Iterator i = svcs.iterator(); i.hasNext(); )
            vetters[j++] = (Method) i.next();
    }


    /**
     * Constructor is private
     */
    private DiskInitialize(HardwareProfile profile,
                           DiskMonitor diskMonitor, boolean eraseAll)
        throws IOException {
        initStatics();
        this.profile = profile;
        this.eraseAll = eraseAll;
        this.diskMonitor = diskMonitor;

        config = ClusterProperties.getInstance();

        newfsOnFailedMount = getBooleanProperty(
            ConfigPropertyNames.PROP_DISK_NEWFS_ON_FAILED_MOUNT,
                                                false);
        newfsOnFailedFsck = getBooleanProperty(
            ConfigPropertyNames.PROP_DISK_NEWFS_ON_FAILED_FSCK,
                                               false);
        mountOptions = getStringProperty(
            ConfigPropertyNames.PROP_DISK_MOUNT_OPTIONS, mountOptions);
        newfsOptions = getStringProperty(
            ConfigPropertyNames.PROP_DISK_NEWFS_OPTIONS, "");
        newfsTimeout = 1000 * getLongProperty(
            ConfigPropertyNames.PROP_DISK_NEWFS_TIMEOUT,
                                              DEFAULT_NEWFS_TIMEOUT_SECONDS);
        fsckTimeout = 1000 * getLongProperty(
            ConfigPropertyNames.PROP_DISK_FSCK_TIMEOUT,
                                             DEFAULT_FSCK_TIMEOUT_SECONDS);

        logger.info("newfsOnFailedMount: " + newfsOnFailedMount + 
            " newfsOnFailedFsck: " + newfsOnFailedFsck);
        logger.info("fsckTimeout: " + fsckTimeout);

        partitions = getPartitions(profile);
        currentMounts = ops.getCurrentMounts();
    }

    /**
     * Create virtual disks using loop devices
     *
     * @param nDisks number of disks to create
     * @return list of paths to disks
     */
    private List getVirtualDisks() throws IOException {

        int nDisks = profile.getMaxDisks();
        List allDisks = new LinkedList();

        for (int i = 0; i < nDisks; i++) {
            String filename = VDISK_PREFIX + i;
            String device = ops.makeVirtualDisk(i, filename, VDISK_SIZE_MB);
            allDisks.add(device);
        }

        return allDisks;
    }

    /**
     * Initialize just one disk
     */
    public static void initializeDisk(HardwareProfile profile,
                                  int nodeId, String path,
                                  DiskLabel label,
                                  DiskMonitor monitor)
            throws ConcurrencyException {

        if (logger.isLoggable(Level.INFO))
            logger.info("Initializing disk " + label + " -> " + path);

        if (label.isForeign()) {
            if (logger.isLoggable(Level.INFO))
                logger.info("Won't touch foreign disk " + label);
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("info.disk.initialize.foreign");
            Object [] args = {label.toString()};
            logger.log(ExtLevel.EXT_INFO, MessageFormat.format(str, args));
        }

        if (label.isDisabled()) {
            logger.warning("Disk " + label + " still disabled!");
        }

        DiskInitialize di = null;
        try {
            di = new DiskInitialize(profile, monitor, false);
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't init " + label, e);
            return;
        }

        di.waitForDiskReplacementProcessing(false);

        Runnable r = di.getDiskRunnable(nodeId, path, label);
        if (r == null) {
            // Don't try to monitor an inactive disk
            return;
        }

        Thread t = new Thread(r);
        t.start();
        try {
            t.join();
        }
        catch (InterruptedException e) {
            logger.log(Level.WARNING, "Interrupted", e);
        }
        DiskInitializationThread dit = (DiskInitializationThread) r;
        if(dit.concurrencyException != null) {
            logger.log(Level.WARNING, "Concurrency Exception for " + label);
            throw dit.concurrencyException;
        }
    }

    /**
     * Initialize disks in the Map
     *
     * @param disks a map from device path to DiskLabel
     */
    private void initializeDisks(Map disks, int nodeId) {
        int i;
        Iterator l;

        if (logger.isLoggable(Level.INFO)) {
            String msg = "Labels {";
            for (i = 0, l = disks.keySet().iterator(); l.hasNext(); i++) {
                String path = (String) l.next();
                DiskLabel label = (DiskLabel) disks.get(path);
                msg += " \"" + label + "\"";
            }
            logger.info(msg + " }");
        }

        // Make disk threads wait till replacement processing is done.
        // We don't join the thread because initializeDisks() can't block,
        // or the node manager will restart us (and eventually the
        // node); instead the disk threads spin for a shared
        // semaphore.  But if we're wiping, no need to do all that:
        // the disk threads can start their work immediately.
        if (eraseAll)
            waitForDiskReplacementProcessing(false);
        else {
            waitForDiskReplacementProcessing(true);

            // Fire off the disk replacement processing thread
            Runnable r = getReplacementProcessingRunnable();
            Thread t = new Thread(r);
            t.start();
        }

        // Fire off the disk-checking threads
        for (i = 0, l = disks.keySet().iterator(); l.hasNext(); i++) {
            String path = (String) l.next();
            DiskLabel label = (DiskLabel) disks.get(path);

            Runnable r = getDiskRunnable(nodeId, path, label);
            Thread t = new Thread(r);
            t.start();
            // The threads call the appropriate DiskMonitor methods
        }
    }

    private Runnable getDiskRunnable(int nodeId, String path,
                                     DiskLabel label) {

        // Construct the diskId using the location of the disk,
        // not the name in the label.  This allows us to refer a misplaced
        // disk from the UI using a DISK-101:3 style name.

        DiskId id = new DiskId(DiskId.CELL_ID, DiskId.SILO_ID, nodeId,
            DiskLabel.getSlotIndex(path), label);

        if (label.isDisabled() || label.isForeign()) {
            // Don't check, mount etc. -- but we still want to
            // keep it in the list of labels so the disk can be
            // enabled via the CLI (if disabled), and the disk
            // information shows up in hwstat output.

            // DiskLabel.toString() appends (disabled) or (foreign)
            logger.warning(
                "Disk " + label +
                " is being taken offline; won't be checked/mounted");

             ResourceBundle rs = BundleAccess.getInstance().getBundle();
             String str = rs.getString("info.disk.initialize.notMounted");
             Object [] args = {label.toString()};
             logger.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));

             
            diskMonitor.addDisk(id, label.getDevice());
            return null;
        }
        return new DiskInitializationThread(id, profile, diskMonitor);
    }

    private Runnable getReplacementProcessingRunnable() {
        return new DiskReplacementThread();
    }

    private static String[] getPartitions(HardwareProfile profile) {
        String[] partitions = new String[profile.getNumPartitions()];
        for (int i = 0; i < partitions.length; i++)
            partitions[i] = profile.getPartitionDesc(i);
        return partitions;
    }

    private class DiskReplacementThread implements Runnable {
        public void run() {
            // First find and proccess any replaced disks Compares
            // serial# to old table For any that are new, mkfses,
            // formats, Throws IOException if it fails log SEVERE,
            // but still try to bring disks online: MikeG: would you
            // rather not bring disks on if disk_replacement fails?

            logger.info("Starting DiskReplacementThread");

            try {
                if(!getDiskReplacementLock()) {
                    logger.severe("Won't process replaced disks: found lock");
                } else {
                    ops.processReplacedDisks(DISK_REPLACEMENT_TIMEOUT);
                }
            } catch(IOException ioe) {
                logger.severe("Processing replaced disks failed, but moving on: " + ioe);
            } finally {
                releaseDiskReplacementLock();
                // No matter what, allow disks to proceed initting
                waitForDiskReplacementProcessing(false);
            }

            logger.info("DiskReplacementThread exiting");
        }
    }

    private class DiskInitializationThread implements Runnable {

        private DiskId diskId;
        private DiskLabel label;
        private String device;
        private String[] partitions;
        private HardwareProfile profile;
        private DiskMonitor diskMonitor;
        public  ConcurrencyException concurrencyException;

        private DiskInitializationThread(DiskId diskId,
                                         HardwareProfile profile,
                                         DiskMonitor diskMonitor) {
            this.diskId = diskId;
            this.label = diskId.label();
            this.profile = profile;
            this.partitions = getPartitions(profile);
            this.device = label.getDevice();
            this.diskMonitor = diskMonitor;
            this.concurrencyException = null;
        }

        public void run() {
            logger.info("Starting DiskInitializationThread for " + label);

            if (waitForDiskReplacementProcessing()) {
                logger.info("Disk initialization of " + diskId +
                            " waiting until DiskReplacementProcessing completes");
                while (waitForDiskReplacementProcessing()) {
                    try {
                        Thread.sleep(DISK_REPLACEMENT_WAIT_SLEEP);
                    } catch (InterruptedException ie) {
                        logger.warning("Disk replacement sleep interrupted: "
                                       + ie);
                    }
                }

                logger.info("Disk initialization of disk " + diskId +
                            " beginning now that DiskReplacementProcessing has completed.");
            }

            if (label.isForeign()) {
                // Don't touch a foreign disk in any way
                if (logger.isLoggable(Level.INFO))
                    logger.info(Thread.currentThread().getName() +
                                ": skipping check/mount of foreign disk " +
                                label);

                diskMonitor.addDisk(diskId, device);
                return;
            }

            if (label.isDelayedWrite())
                label.commitLabel();

            if (label.isDisabled()) {
                // We don't try to check and mount a disabled disk
                if (logger.isLoggable(Level.INFO))
                    logger.info(Thread.currentThread().getName() +
                                ": skipping check/mount of disabled disk " +
                                label);

                diskMonitor.addDisk(diskId, device);
                return;
            }

            try {
                if (logger.isLoggable(Level.INFO))
                    logger.info(Thread.currentThread().getName() +
                                ": checking and trying to mount disk " +
                                diskId);

                initDisk();

                if (logger.isLoggable(Level.INFO))
                    logger.info(Thread.currentThread().getName() +
                                ": SUCCESS mounting disk " + diskId);
            }
            catch (ConcurrencyException ce) {
                logger.log(Level.SEVERE, Thread.currentThread().getName() +
                           ": disk op concurrency exception. " + diskId, ce);
                concurrencyException = ce;
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, Thread.currentThread().getName() +
                           ": couldn't mount disk. " + diskId, e);

                String str = null;
                String msgKey = AdminResourcesConstants
                        .MSG_KEY_ALERT_DISK_INIT_ERR_DISABLED;
                Object [] args = {new Integer(diskId.diskIndex()),
                                  new Integer(diskId.nodeId())};
                try {
                    str = BundleAccess.getInstance().getBundle()
                            .getString(msgKey);
                    str = MessageFormat.format(str, args);
                } catch (Exception ex) {
                    if (str == null) {
                        str = msgKey;
                    }                    
                    logger.log(Level.WARNING, 
                            "Unable to find or translate message key " + 
                            msgKey +  "  Message string: " + str);                     
                }

                // disable the disk
                if(concurrencyException == null) {
                    label.setDisabled(true);
                    
                    logger.log (ExtLevel.EXT_SEVERE, str);
                    try {
                        /*
                         * Note: When system is booting up the Alert Service is
                         * probably not running yet. In that case the attempt to
                         * send an alert will fail.
                         */
                        Utils.notifyChangeCli(str);
                    } catch (Exception ex) {
                        // Should not get here but trap any exceptions
                        // just in case
                        logger.log(Level.WARNING,
                                "Unabled to deliver alert " +
                                " notification for disabled disk");
                    }
                    
                    try {
                        label.writeLabel();
                    } catch (IOException ioe) {
                        logger.log(Level.SEVERE,
                                   Thread.currentThread().getName() +
                                   ": cannot write disk label " + diskId, ioe);
                    }
                }
                ResourceBundle rs = BundleAccess.getInstance().getBundle();
                String lstr = rs.getString("err.disk.init");
                Object [] largs = {new Integer(diskId.diskIndex()),
                                  new Integer(diskId.nodeId())};
                logger.log(ExtLevel.EXT_SEVERE,
                           MessageFormat.format(lstr, largs));

                logger.log(Level.SEVERE, Thread.currentThread().getName() +
                           ": couldn't mount disk. Disabled. " + diskId, e);
            }

            // In the case of concurrency - somone else is
            // doing all this already
            if (concurrencyException != null)
                return;

            if (!label.isDisabled()) {
                diskMonitor.addDisk(diskId, device, Disk.MOUNTED);
                Disk disk = diskMonitor.getDisk(diskId);

                logger.info("Disk " +  disk.getPath() + " is "
                            + disk.getStatusString() + ", running Upgrader.");

                // Upgrade OA
                try {
                    new Upgrader().upgrade(disk);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "OA Upgrader failed", e);
                    return;
                }

                disk.setStatus(Disk.ENABLED);
                ServiceManager.publish(diskMonitor);
                logger.info("Disk " +  disk.getPath() + " status set to "
                            + disk.getStatusString());
            } else {
                // DiskMonitor still needs to know about disabled disks so
                // they can be enabled.
                diskMonitor.addDisk(diskId, device);
            }
        }

        private void initDisk() throws IOException, ConcurrencyException {
            /*
             * if ! all partitions already mounted
             *     umount all
             *     check partition table
             *         write, if reqd.
             *     for each managed partition
             *         fsck
             *             mkfs if bad
             *         mount
             * enable
             *
             * if mkfs or fsck are already being done elsewhere, throw
             * ConcurrencyException
             */

            // If all partitions are already mounted in the correct
            // places, we try to not do anything drastic
            int numPartitions = partitions.length;
            boolean allAlreadyMounted = true;

            if (eraseAll)
                allAlreadyMounted = false;
            else
                for (int part = 0; part < numPartitions; part++) {
                    String mountPrefix = profile.getPathPrefix(part);
                    if (mountPrefix == null || mountPrefix.length() == 0)
                        continue;

                    String dev =
                        ops.getPartitionDevice(device, part, partitions);
                    String mountPoint = mountPrefix + "/" + diskId.diskIndex();

                    String m = (String) currentMounts.get(dev);
                    if (m == null || !m.equals(mountPoint)) {
                        allAlreadyMounted = false;
                        break;
                    }
                }

            if (allAlreadyMounted && logger.isLoggable(Level.INFO))
                logger.info("All filesystems are already mounted; " +
                            "not checking any of them");

            if (!allAlreadyMounted) {

                // Unmount the managed partition(s) before starting

                for (int part = 0; part < numPartitions; part++) {
                    // If there's no mount prefix, HC does not manage
                    // the slice so don't unmount
                    String mountPrefix = profile.getPathPrefix(part);
                    if (mountPrefix == null || mountPrefix.length() == 0)
                        continue;

                    String dev =
                        ops.getPartitionDevice(device, part, partitions);
                    String mountPoint =
                        mountPrefix + "/" + diskId.diskIndex();

                    if (currentMounts.get(dev) != null) {
                        // A failed unshare means a forced unmount
                        // will be necessary.  Luckily, do a
                        // forced unmount
                        try {
                            ops.unexport(mountPoint);
                        } catch (IOException oae) {
                            logger.warning("Failed to unshare: " + oae);
                        }

                        // If we fail a forced unmount, we will
                        // throw out of here and stop trying to
                        // use this disk as nothing below can work
                        // and so is only asking for trouble.
                        try {
                            ops.unmount(dev,
                                profile.getPartitionType(part),
                                true); // forced unmount
                        } catch (IOException oae) {
                            // It's possible that the unmount failed
                            // because the device was already unmounted.
                            // If that's the case, we should just drive on.
                            Map mounts = ops.getCurrentMounts();
                            
                            if (mounts.get(dev) == null) {
                                // not mounted, so we're OK
                                logger.warning("Mount point " + mountPoint +
                                    " was already unmounted");
                            } else {
                                logger.warning("Failed to unmount: " + oae);
                                throw (oae);
                            }
                        }
                        
                       DiskMonitor.recordCleanUnmount(dev);
                    }
                }

                // Everything should be unmounted by now
                if (logger.isLoggable(Level.INFO))
                    Exec.exec("/usr/sbin/mount", logger);

                // Currently, checkPartitionTable() always returns
                // true because it is out of sync with the new disk
                // replacement stuff.
                if (!checkPartitionTable()) {
                    String msg = "Disk " + diskId + ": " +
                        (eraseAll? "" : "partition check failed; ") +
                        "writing new partition table";
                    logger.warning(msg);
                    writePartitionTable();
                }

                // Check and mount all filesystems
                for (int part = 0; part < numPartitions; part++) {
                    String dev =
                        ops.getPartitionDevice(device, part, partitions);
                    int fsType = profile.getPartitionType(part);

                    // If there's no mount prefix, HC does not manage
                    // the slice so don't fsck, mkfs, or mount
                    String mountPrefix = profile.getPathPrefix(part);
                    if (mountPrefix == null || mountPrefix.length() == 0)
                        continue;

                    String mountPoint =
                        mountPrefix + "/" + diskId.diskIndex();

                    // checkAndMount will fsck; mkfs if it fails; then mount
                    // If the mount fails, it will mkfs and try mount again.
                    // If that fails, it throws an exception -- nothing we
                    // can do to recover.

                    if (checkAndMount(dev, fsType, mountPoint)) {
                        // The disklabel is saved in /.disklabel for
                        // mounted disks
                        File f = new File(mountPoint, ".disklabel");
                        PrintWriter wr = new PrintWriter(new FileWriter(f));
                        wr.println(label.toString());
                        wr.close();
                    }
                }
            }

            logger.info(
                "Configuring dump, swap and mirrors while initializing disk "
                + diskId);

            // Enable swap and dump on the disk if not already enabled
            if (!ops.dumpSwapMirrorOnline(device)) {
                // This is unexpected, but we drive on; the system
                // should tolerate a reduced amount of swap and
                // dump is not essential.
                logger.warning(
                    "Couldn't enable dump, swap, and mirrors for disk " +
                    diskId);
            }

            // Everything that was mountable has been mounted. Each
            // service must now check the data partition.

            // mount point for the data partition
            String dDir =
                profile.getPathPrefix(profile.dataPartitionIndex()) +
                "/" + diskId.diskIndex();

            // Make sure there's actually a disk there
            File f = new File(dDir + "/" + ".disklabel");
            if (!f.exists())
                throw new IOException(dDir + " failed! Skipping.");

            for (int i = 0; i < vetters.length; i++) {

                if (!runCallback(vetters[i], dDir)) {
                    String msg = "Service " + vetters[i] + " marked \"" +
                        dDir + "\" (disk " + diskId + ") unusable! Disabled.";
                    throw new IOException(msg);
                }
            }
        }

        private boolean checkPartitionTable() throws IOException {

            // XXX TODO THIS CHECK NEEDS WORK - NOT IN SYNC WITH NEW
            // DISK REPLACEMENT STUFF
            if (true)
                return true;

            if (profile.useVirtualDisks())
                // no partitions on virtual disks
                return true;

            /*
             * Here we have a tendency to be unduly aggressive -- be
             * careful! On Solaris, re-writing the partition table and
             * VTOC -- even with the same values -- seems to screw up
             * the RAID boot/root/conf partitions.
             */

            String[] parts = ops.getPartitionTable(device);

            // The return value is like
            // { "0:root,10,128", "1:root,138,128", "3:root,266,128",
            //   "4:home,522,17283", "5:root,394,128", }
            // Unmountable partitions (e.g. 2 and 8) are not included.

            if (logger.isLoggable(Level.INFO)) {
                String msg = device + ": ";
                if (parts.length == 0)
                    msg += "no partitions";
                else {
                    msg += parts.length + " partitions:";
                    for (int i = 0; i < parts.length; i++)
                        msg += " " + parts[i];
                }
                logger.info(msg);
            }

            // Compare current partitions against required partitions
            boolean retval = true;
            for (int i = 0; i < partitions.length; i++) {
                String pId = partitions[i].split(":")[0];

                // Is there partition pId in parts[]?
                String p = null;
                String s = pId + ":";
                for (int j = 0; j < parts.length; j++)
                    if (parts[j].startsWith(s)) {
                        p = parts[j];
                        break;
                    }
                if (p == null) {
                    logger.warning("Partition " + partitions[i] +
                                   " absent on disk " + device);
                    // Don't return yet -- I want warnings printed
                    // for all missing partitions
                    retval = false;
                }
            }

            return retval;
        }

        private void writePartitionTable() throws IOException {
            if (profile.useVirtualDisks())
                // no partitions on virtual disks
                return;

            ops.writePartitionTable(device, partitions);
        }

        /**
         * Check and mount a filesystem
         *
         * @param dev device
         * @param fs filesystem type
         * @param mountPoint mount point
         * @return whether the operation succeeded
         * @throws ConcurrencyException someone else is doing a diskop
         * @throws IOException on any non-recoverable error
         */
        private boolean checkAndMount(String dev, int fs, String mountPoint)
            throws IOException, ConcurrencyException {

            boolean shouldNewfs = shouldNewfsDataPartition(dev);

            if (eraseAll || shouldNewfs) {
                logger.severe(Thread.currentThread().getName() + ": " +
                              dev + ": Making new filesystem");
                ResourceBundle rs = BundleAccess.getInstance().getBundle();
                String str = rs.getString("warn.disk.initialize.mkfs");
                Object [] args = {new String(dev)};
                logger.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));
             
                ops.mkfs(dev, fs, newfsOptions, newfsTimeout);

                // If we saw the "should newfs" indicator, clear it now
                // that we've done the newfs.
                if (shouldNewfs)
                    clearNewfsDataPartitionIndicator(dev);
            } else {
                
                // If we see that the file system was cleanly unmounted,
                // we only do a cursory fsck and then go on to mount the
                // file system.
                //
                // If the file system was not cleanly unmounted, we 
                // go directly to a full-force fsck.

                boolean cleanUnmount = DiskMonitor.didCleanUnmount(dev);

                boolean checkGood = false;

                if (!getFSCKLock(dev)) {
                    logger.severe("Failed to get FSCKLock for " + dev);
                    throw new ConcurrencyException(
                        "someone else already fscking " +
                        dev + " aborting checkAndMount");
                }

                try {
                    if (cleanUnmount) {
                        logger.info("Had clean unmount of " + dev +
                            ", doing fast fsck");

                        checkGood = ops.fsck(dev, fs, false, fsckTimeout);

                        if (!checkGood) {
                            logger.info("fast fsck of " + dev +
                                " unexpectedly failed, doing full fsck");
                            
                            checkGood = ops.fsck(dev, fs, true, fsckTimeout);

                            logger.info("result of full fsck of " + dev +
                                ":  " + checkGood);
                        }
                    } else {
                        // Need to do full bore fsck
                        logger.info(
                            "Doing full fsck due to unclean unmount of " +
                            dev);

                        checkGood = ops.fsck(dev, fs, true, fsckTimeout);
                    }
                } catch (IOException e) {
                    logger.severe("Got fsck exception " + e);
                } finally {
                    releaseFSCKLock(dev);
                }

                if (!checkGood) {
                    // check version of fsck failed, need to try
                    // the repair version

		    String msg = Thread.currentThread().getName() + ": " +
			dev + " fsck failed";
		    
		    if (!newfsOnFailedFsck)
			throw new IOException(msg);
		    else
			logger.severe(msg);
		    
                    logger.warning("Making new filesystem on " + dev);
		    
		    if (!getMKFSLock(dev)) {
			logger.severe("Failed to get FSCKLock for " + dev);
			throw new ConcurrencyException(
                            "someone else already mkfsing " +
                            dev + " aborting checkAndMount");
		    }
		    try {
			ops.mkfs(dev, fs, newfsOptions, newfsTimeout);
		    } finally {
			releaseMKFSLock(dev);
		    }
                }
            }

            // Mount the filesystem.

            String errmsg = null;
            try {
                ops.mount(dev, fs, mountPoint, mountOptions);
                return true;
            }
            catch (IOException e) {
                errmsg = e.toString();
                try {
                    ops.mount(dev, fs, mountPoint, mountOptions, true);
                    logger.warning("Mount failure (" + e + ") overridden");
                    return true;
                }
                catch (IOException e2) {
                    errmsg = "initial error: \"" + errmsg + "\"; force: \"" +
                        e2 + "\"";
                }
            } finally {
                // If we tried to mount (successful or not),
                // clear clean unmount indication
                DiskMonitor.clearCleanUnmount(dev);
            }

            errmsg = Thread.currentThread().getName() +
                " couldn't mount disk " + diskId +
                " (options=\"" + mountOptions + "\") -- " + errmsg;

            // If the first attempt fails, maybe make a new filesystem
            // and try again. (We need this because sometimes Solaris'
            // fsck lies and says the filesystem is ok even though I
            // just dropped a chunk on zeros on it.)
            if (!newfsOnFailedMount)
                throw new IOException(errmsg);
            else
                logger.severe(errmsg);

            logger.severe("Making new filesystem on " + dev +
                          " (options=" + newfsOptions + ")");

            ops.mkfs(dev, fs, newfsOptions, newfsTimeout);
            ops.mount(dev, fs, mountPoint, mountOptions);

            return true;
        }

        private boolean runCallback(Method m, String dir) {
            Boolean retval = null;
            try {
                retval = (Boolean) m.invoke(null, new Object[]{dir});
            }
            catch (IllegalAccessException e) {
                logger.log(Level.SEVERE, "Method " + m + " is not accessible",
                           e);
            }
            catch (InvocationTargetException e) {
                logger.log(Level.SEVERE, "Can't invoke method " + m, e);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Service " + m +
                           ".checkAndRepair(" + dir + ") threw exception", e);
            }

            return retval != null && retval.booleanValue();
        }

    }

    /**
     * Return the file name for the 'newfs the data partition' indicator.
     * Disk must be of the form "/dev/dsk/cXtXdX".
     */
    private static String newfsIndicatorFileName(String disk) {
        return NEWFS_INDICATOR_PREFIX + disk.replace("/", "_");
    }


    /**
     * Clear "need to newfs the data partition" indication.
     * Disk must be of the form "cXtXdX".
     */
    private static void clearNewfsDataPartitionIndicator(String disk) {
        String fname = newfsIndicatorFileName(disk);

        if (logger.isLoggable(Level.INFO))
            logger.info("Clearing 'newfs data partition' indicator for " +
                fname);

        boolean success = (new File(fname)).delete();
        
        if (!success) {
            // This is a programming error.
            if (logger.isLoggable(Level.INFO))
                logger.info("clearNewfsDataPartition: " + fname +
                    " doesn't exist");
        }
    }

    /**
     * Do we need to newfs the data partition of the disk?
     * Disk must be of the form /dev/dsk/cXtXdXsX
     */
    private static boolean shouldNewfsDataPartition(String disk) {
        String fname = newfsIndicatorFileName(disk);

        boolean file_exists = (new File(fname)).exists();

        // debug
        logger.info("shouldNewfsDataPartition: " + fname + " " + file_exists);
        
        return file_exists;
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
        String s = null;
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
            logger.warning("Couldn't parse " + pname + " = \"" + s +
                           "\"; using default " + defaultVal);

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
            logger.warning("Couldn't parse " + pname + " = \"" + s +
                           "\"; using default " + defaultVal);

        }

        return defaultVal;
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // The Simulator
    //
    // The simulator is a mode of the Exec class where instead of exec'ing
    // the command, it's just printed to a log file. Output to be read from a
    // command is taken from an input file.

    private static PrintStream simLog = System.out;

    public static void main(String[] args) {
        initStatics();

        int rc = 0;
        String profileName = null;
        try {
            if ((profileName = setupSim(args)) != null)
                runSim(profileName);
        }
        catch (Exception e) {
            //e.fillInStackTrace();
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace(System.err);
            rc = 1;
        }
        finally {
            if (simLog != System.out)
                simLog.close();
        }

        System.out.println("done");
        System.exit(rc);

    }

    private static String setupSim(String[] args) {
        Date now = new Date(System.currentTimeMillis());
        int rc = 0;
        String inputScriptDir = ".";
        int verbosity = 0;
        String outFileName = null;
        String profileName = "hon";

        Getopt opts = new Getopt(args, "o:d:p:v");
        while (opts.hasMore()) {
            Getopt.Option option = opts.next();
            switch (option.name()) {

            case 'o':
                outFileName = option.value();
                break;

            case 'p':
                profileName = option.value();
                break;

            case 'd':
                inputScriptDir = option.value();
                break;

            case 'v':
                verbosity++;

            }
        }
        if (outFileName != null && !outFileName.equals("-"))
            try {
                simLog = new PrintStream(new FileOutputStream(outFileName));
                outFileName = "\"" + outFileName + "\"";
            }
            catch (FileNotFoundException e) {
                System.err.println("Couldn't find \"" + outFileName + "\"!");
                return null;
            }
        else
            outFileName = "<stdout>";


        System.out.println("Started at " + now.toString() +
                           ", trace is in " + outFileName);
        simLog.print(now.toString() + "\n\n");
        Exec.simulatorMode(simLog, inputScriptDir);
        return profileName;
    }

    private static void runSim(String profileName) {
        System.out.println("Using profile \"" + profileName + "\"");
        initAllDisks(HardwareProfile.getProfile(profileName), null,
             0, (short)0, (short)0);
    }

    // Locking - keeps some fileops from running concurrently

    public static boolean getFSCKLock(String device) {
        logger.info(device);
        return getLockFile("fsck", device, IsRunning.CHECK);
    }

    public static void releaseFSCKLock(String device) {
        logger.info(device);
        releaseLockFile("fsck", device);
    }

    public static boolean getMKFSLock(String device) {
        logger.info(device);
        return getLockFile("mkfs", device, IsRunning.CHECK);
    }

    public static void releaseMKFSLock(String device) {
        logger.info(device);
        releaseLockFile("mkfs", device);
    }

    public static boolean getDiskReplacementLock() {
        logger.info("acquire");
        return getLockFile("disk_replacement", null, IsRunning.IGNORE);
    }

    public static void releaseDiskReplacementLock() {
        logger.info("release");
        releaseLockFile("disk_replacement", null);
    }

    /**
     * Tries to acquire the lock file.
     *
     * @param op operation for which we're trying to get the lock, e.g. "mkfs"
     *                          -- must be present in output of ps
     * @param device device on which op operates (may be null)
     * @param checkRunning if true, check to see if the operation is
     *              currently running.
     * @return whether we got the lock.
     */

    private static boolean getLockFile(String op, String device,
                                       IsRunning checkRunning) {

        String path = LOCK_DIR + "/" + op;

        if (device != null) {
            String cleanDevice = device.replaceAll("/", "_");
            path = path + cleanDevice;
        }

        boolean result = false;

        File file = new File(path);

        // First clear out any stale lock file
        if (file.exists()) {
            boolean tryDelete = false;
            if (checkRunning == IsRunning.CHECK) {
                //
                // Is the command still running? run ps -elf and find out
                //
                if (isRunning(op, device)) {
                    logger.info("failed to get lock of " + path +
                                "; command " + op + " is running");
                    return false;
                } else {
                    logger.info("command '" + op + "' for lock " + path +
                                " is no longer running, deleting lock file");
                    tryDelete = true;
                }
            } else if (((System.currentTimeMillis() - file.lastModified()) / 1000) >
                       LOCK_TIMEOUT_SECS) {
                tryDelete = true;
            }

            if (tryDelete) {
                if (file.delete()) {
                    logger.warning("Removing expired lock: " + path);
                } else {
                    logger.severe("Failed to remove expired lock: " + path);
                    return false;
                }
            }
        }

        // Then try atomically to create the file (fails if exists)
        try {
            result = file.createNewFile();
        } catch(IOException ioe) {
            logger.severe("IOE grabbing lock file " + path + ": " + ioe);
            return false;
        } catch(SecurityException se) {
            logger.severe("SecEx grabbing lock file " + path + ": " + se);
            return false;
        }
        if(result == true) {
            logger.info("got lock of " + path);
        } else {
            logger.info("failed to get lock of " + path);
        }
        return result;
    }

    private static void releaseLockFile(String op, String device) {

        String path = LOCK_DIR + "/" + op;

        if (device != null) {
            String cleanDevice = device.replaceAll("/", "_");
            path = path + cleanDevice;
        }

        File file = new File(path);
        if (!file.exists()) {
            logger.warning("Tried to release a lock we didn't have: " + path);
            return;
        }
        try {
            if (!file.delete()) {
                logger.severe("Failed to release lock file " + path);
            } else {
                logger.info("released lock of " + path);
            }
        } catch(SecurityException se) {
            logger.severe("SecEx releasing lock file " + path + ": " + se);
        }
    }

    private void waitForDiskReplacementProcessing(boolean wait) {
        synchronized(this) {
            waitForDiskReplacementProcessing = wait;
        }
    }

    private boolean waitForDiskReplacementProcessing() {
        synchronized(this) {
            return waitForDiskReplacementProcessing;
        }
    }


    /**
     * Is the process running?  Device argument, if non-null,
     * asks if the process is running with the device as an argument.
     */
    private static boolean isRunning(String processName, String device) {
        boolean running = false;

        BufferedReader reader = null;
        try {
            boolean terminate = false;

            reader = Exec.execRead(PS_CMD, logger);

            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;

                if (line.indexOf(processName) == -1)
                    continue;

                if (device != null && line.indexOf(device) == -1)
                    continue;

                // have the process name we're looking for;
                // is it a zombie?

                String [] tokens = line.split("\\s");
                if (tokens.length < 2) {
                    // error; this is really weird, but
                    // we assume it is not a zombie in this case.
                    logger.info("not enough tokens in line: " + line +
                                "length: " + tokens.length);
                    running = true;
                    break;
                }

                // A zombie process has Z in the 2nd column of ps output
                boolean isZombie = (tokens[2].indexOf("Z") != -1);
                running = (!isZombie);
                break;
            }
        }
        catch (IOException e) {
            logger.log(Level.WARNING,
                       "isRunning " + processName + ": can't run ps ", e);
        }
        finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {}
            return running;
        }
    }
}
