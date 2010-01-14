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

import com.sun.honeycomb.cm.DiskfulCMMMain;

import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.Getopt;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.util.ExtLevel;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.lang.reflect.*;
import java.text.MessageFormat;

public class DiskInitialize2 {

    private static final String PROP_NEWFS_ON_FAILED_FSCK =
        "honeycomb.disks.newfs.on_failed_fsck";
    private static final String PROP_NEWFS_ON_FAILED_MOUNT =
        "honeycomb.disks.newfs.on_failed_mount";
    private static final String PROP_NEWFS_OPTIONS =
        "honeycomb.disks.newfs.options";
    private static final String PROP_NEWFS_TIMEOUT =
        "honeycomb.disks.newfs.timeout";
    private static final String PROP_MOUNT_OPTIONS =
        "honeycomb.disks.mount.options";

    public static final long DEFAULT_NEWFS_TIMEOUT = 2400; // 40 minutes

    private static final String myName = DiskInitialize2.class.getName();
    private static final Logger logger = Logger.getLogger(myName);
    private static DiskOps ops = null;

    ClusterProperties config = null;

    static private ResourceBundle errorBundle = null;

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

    private DiskfulCMMMain cmmMain = null;

    /**
     * Initialize all disks known to the system
     *
     * @return a map from device path to DiskId
     */
    public static void eraseAndInit(HardwareProfile profile,
                                    DiskfulCMMMain cmmMain,
                                    int cellId, short siloId, short nodeId) {
        logger.info("erasing and starting...");
        doInit(profile, cmmMain, cellId, siloId, nodeId, true);
    }

    /**
     * Initialize all disks known to the system
     *
     * @return a map from device path to DiskId
     */
    public static void init(HardwareProfile profile,
                            DiskfulCMMMain cmmMain,
                            int cellId, short siloId, short nodeId) {
        logger.info("starting...");
        doInit(profile, cmmMain, cellId, siloId, nodeId, false);
    }

    public String toString() {
        return myName;
    }

    /**
     * Initialize all disks known to the system
     *
     * @return a map from device path to DiskId
     */
    private static void doInit(HardwareProfile profile,
                               DiskfulCMMMain cmmMain,
                               int cellId, short siloId, short nodeId,
                               boolean erase) {
        initStatics();

        try {
            if (ops == null)
                ops = DiskOps.getDiskOps();

            DiskInitialize2 di = new DiskInitialize2(profile, cmmMain, erase);

            List paths;
            if (profile.useVirtualDisks())
                paths = di.getVirtualDisks();
            else
                paths = ops.getDiskPaths(profile.diskType());

            Map allDisks =
                DiskLabel.probeDisks(cellId, siloId, nodeId, paths, erase);
            di.initialize(allDisks, nodeId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't initialize disks", e);
        }
    }

    /** Initialize statics */
    private static synchronized void initStatics() {
        if (errorBundle != null)
            return;

    try {
        errorBundle = ResourceBundle.getBundle("ExternalLogs",
          Locale.getDefault());
    } catch (MissingResourceException ex){
        logger.log(Level.SEVERE,
          "initStatics() cannot retrieve error bundle, exit..." + ex);
        System.exit(1);
    }

        // These classes must have a "checkAndRepair" method:
        //     static boolean checkAndRepair(String dir)
        String[] checkerNames = new String[] {
            "oa.DirChecker"
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
            } catch (ClassNotFoundException ignored) {}

            if (c == null) {
                logger.severe("Class " + className + " not found.");
                continue;
            }
            Method m = null;
            try {
                m = c.getMethod("checkAndRepair", new Class[]{String.class});
            } catch (SecurityException e) {
                logger.log(Level.SEVERE,
                           "Couldn't get checkAndRepair for " + className, e);
            } catch (NoSuchMethodException ignored) {}

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
    private DiskInitialize2(HardwareProfile profile,
                           DiskfulCMMMain cmmMain, boolean eraseAll)
            throws IOException {
        initStatics();
        this.profile = profile;
        this.eraseAll = eraseAll;
        this.cmmMain = cmmMain;

        config = ClusterProperties.getInstance();

        newfsOnFailedMount = getBooleanProperty(PROP_NEWFS_ON_FAILED_MOUNT,
                                                false);
        newfsOnFailedFsck = getBooleanProperty(PROP_NEWFS_ON_FAILED_FSCK,
                                               true);
        mountOptions = getStringProperty(PROP_MOUNT_OPTIONS, mountOptions);
        newfsOptions = getStringProperty(PROP_NEWFS_OPTIONS, "");
        newfsTimeout = 1000 * getLongProperty(PROP_NEWFS_TIMEOUT,
                                              DEFAULT_NEWFS_TIMEOUT);

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
     * Initialize disks in the Map
     *
     * @param disks a map from device path to DiskLabel
     */
    private void initialize(Map disks, int nodeId) {
        int i;
        Iterator l;

        Thread[] threads = new Thread[disks.size()];

        if (logger.isLoggable(Level.INFO)) {
            String msg = "Labels:";
            for (i = 0, l = disks.keySet().iterator(); l.hasNext(); i++) {
                String path = (String) l.next();
                DiskLabel label = (DiskLabel) disks.get(path);
                msg += " \"" + label + "\"";
            }
            logger.info(msg);
        }

        // Fire off the disk-checking threads
        for (i = 0, l = disks.keySet().iterator(); l.hasNext(); i++) {
            String path = (String) l.next();
            DiskLabel label = (DiskLabel) disks.get(path);

            DiskId id = new DiskId(label);
            if (label.isDisabled()) {
                // Don't check, mount etc. -- but we still want to
                // keep it in the list of labels so the disk can be
                // enabled via the CLI.
                if (logger.isLoggable(Level.INFO))
                    logger.info("Disk " + label + " won't be checked/mounted");

                //cmmMain.addDisk(id, label.getDevice());

                threads[i] = null;
                continue;
            }

            threads[i] =
                new Thread(new DiskInitializationThread(id, label,
                                                        profile, cmmMain));
            threads[i].start();
            // The threads call the appropriate DiskMonitor methods
        }
    }

    private static String[] getPartitions(HardwareProfile profile) {
        String[] partitions = new String[profile.getNumPartitions()];
        for (int i = 0; i < partitions.length; i++)
            partitions[i] = profile.getPartitionDesc(i);
        return partitions;
    }

    private class DiskInitializationThread implements Runnable {

        private DiskId disk;
        private DiskLabel label;
        private String device;
        private String[] partitions;
        private HardwareProfile profile;
        private DiskfulCMMMain cmmMain;

        private DiskInitializationThread(DiskId disk, DiskLabel label,
                                         HardwareProfile profile,
                                         DiskfulCMMMain cmmMain) {
            this.disk = disk;
            this.label = label;
            this.profile = profile;
            this.partitions = getPartitions(profile);
            this.device = label.getDevice();
            this.cmmMain = cmmMain;
        }

        public void run() {
            try {
                if (logger.isLoggable(Level.INFO))
                    logger.info(Thread.currentThread().getName() +
                                ": checking and trying to mount disk " +
                                disk);
                initDisk();
                cmmMain.addDisk();

                if (logger.isLoggable(Level.INFO))
                    logger.info(Thread.currentThread().getName() +
                                ": SUCCESS mounting disk " + disk);
                return;
            } catch (Exception e) {
        String str = errorBundle.getString("err.disk.init");
        Object [] args = {new Integer(disk.diskIndex()),
                  new Integer(disk.nodeId())};
        logger.log(ExtLevel.EXT_SEVERE,
                   MessageFormat.format(str, args));

                logger.log(Level.SEVERE, Thread.currentThread().getName() +
                           ": couldn't mount disk " + disk, e);
                System.exit(1);
            }

            // The DiskMonitor is informed about broken disks too?
            // diskMonitor.addDisk(disk, device);
        }

        private void initDisk() throws IOException {
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
             */

            // If both partitions are already mounted in the correct
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
                    String mountPoint = mountPrefix + "/" + disk.diskIndex();

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
                    String mountPoint = mountPrefix + "/" + disk.diskIndex();

                    if (currentMounts.get(dev) != null)
                        try {
                            ops.unexport(mountPoint);
                            ops.unmount(dev, profile.getPartitionType(part));
                        } catch (IOException ioe) {}
                }

                // Everything should be unmounted by now
                if (logger.isLoggable(Level.INFO))
                    Exec.exec("mount", logger);

                if (!checkPartitionTable()) {
                    String msg = "Disk " + disk + ": " +
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

                    String mountPoint = mountPrefix + "/" + disk.diskIndex();

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

            // Everything that was mountable has been mounted. Each
            // service must now check the data partition.

            // mount point for the data partition
            String dDir =
                profile.getPathPrefix(profile.dataPartitionIndex()) +
                "/" + disk.diskIndex();

            // Make sure there's actually a disk there
            File f = new File(dDir + "/" + ".disklabel");
            if (!f.exists())
                throw new IOException(dDir + " failed! Skipping.");

            for (int i = 0; i < vetters.length; i++) {

                if (!runCallback(vetters[i], dDir)) {
                    label.setDisabled(true);
                    label.writeLabel();
                    String msg = "Service " + vetters[i] + " marked \"" +
                        dDir + "\" (disk " + disk + ") unusable! Disabled.";
                    throw new IOException(msg);
                }
            }
        }

        private boolean checkPartitionTable() throws IOException {
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
         * @throws IOException on any non-recoverable error
         */
        private boolean checkAndMount(String dev, int fs, String mountPoint)
                throws IOException {

            if (eraseAll) {
                logger.severe(Thread.currentThread().getName() + ": " +
                              dev + ": Making new filesystem");
                ops.mkfs(dev, fs, newfsOptions, newfsTimeout);
            }
            else {
                // fsck(.., 0L) means infinite timeout (units=seconds)
                if (!ops.fsck(dev, fs, false, 0L) &&
                     !ops.fsck(dev, fs, true, 0L)) {
                    String msg = Thread.currentThread().getName() + ": " +
                        dev + " fsck failed";

                    // Heuristic: if we wrote a new disklabel we
                    // should be more willing to mkfs -- XXX TODO:

                    if (!newfsOnFailedFsck)
                        throw new IOException(msg);
                    else
                        logger.severe(msg);

                    logger.warning("Making new filesystem on " + dev);
                    ops.mkfs(dev, fs, newfsOptions, newfsTimeout);
                }
            }

            // Mount the filesystem.

            String errmsg = null;
            try {
                ops.mount(dev, fs, mountPoint, mountOptions);
                return true;
            } catch (IOException e) {
                errmsg = e.toString();
            }

            errmsg = Thread.currentThread().getName() +
                " couldn't mount disk " + disk +
                " (options=\"" + mountOptions + "\") " + errmsg;

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
            } catch (IllegalAccessException e) {
                logger.log(Level.SEVERE, "Method " + m + " is not accessible",
                           e);
            } catch (InvocationTargetException e) {
                logger.log(Level.SEVERE, "Can't invoke method " + m, e);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Service " + m +
                           ".checkAndRepair(" + dir + ") threw exception", e);
            }

            return retval != null && retval.booleanValue();
        }

    }

    private String getStringProperty(String pname, String defaultVal) {
        try {
            String s = config.getProperty(pname);
            if (s != null) return s;
        } catch (Exception e) {}

        return defaultVal;
    }

    private boolean getBooleanProperty(String pname, boolean defaultVal) {
        String s = null;
        try {
            if ((s = config.getProperty(pname)) == null)
                return defaultVal;

            return (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes"));
        } catch (Exception e) {}

        return defaultVal;
    }

    private int getIntProperty(String pname, int defaultVal) {
        String s = null;
        try {
            if ((s = config.getProperty(pname)) == null)
                return defaultVal;

            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
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
        } catch (NumberFormatException e) {
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
        } catch (Exception e) {
            //e.fillInStackTrace();
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace(System.err);
            rc = 1;
        } finally {
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
        String[] arguments = opts.remaining();

        if (outFileName != null && !outFileName.equals("-"))
            try {
                simLog = new PrintStream(new FileOutputStream(outFileName));
                outFileName = "\"" + outFileName + "\"";
            } catch (FileNotFoundException e) {
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
        init(HardwareProfile.getProfile(profileName), null,
             0, (short)0, (short)0);
    }
}
