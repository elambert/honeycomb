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



package com.sun.honeycomb.util.sysdep.solaris;

import java.io.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.StringTokenizer;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.File;
import java.io.FilenameFilter;

import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.util.SolarisRuntime;
import com.sun.honeycomb.util.Exec;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.ConfigPropertyNames;

/**
 * This is an implementation of interface DiskOps for Solaris 10.
 * This class does not know anything about Honeycomb.
 *
 * @author Shamim Mohamed
 * @version $Revision: 1.4 $ $Date: 2004-09-21 13:57:55 -0700 (Tue, 21 Sep 2004) $
 */
public class DiskOps extends com.sun.honeycomb.util.sysdep.DiskOps {

    // The various commands and options

    private static final String CMD_FDISK = "/usr/sbin/fdisk";
    private static final String CMD_FORMAT = "/usr/sbin/format";
    private static final String CMD_PRTVTOC = "/usr/sbin/prtvtoc";

    private static final String CMD_MOUNT = "/usr/sbin/mount -F ";
    private static final String OPT_MOUNT_UFS = "-o noxattr,noatime,syncdir";
    private static final String CMD_CURR_MOUNT = "/usr/sbin/mount -p";
    private static final String CMD_UMOUNT = "/usr/sbin/umount";

    private static final String CMD_CFGADM_SATA =
	"/usr/sbin/cfgadm -l -s match=exact,select=class(sata),noheadings,cols=ap_id:r_state:o_state:condition";

    // incantation needed for printing of a particular access pt
    // cfgadm -s "match=partial,select=class(sata):ap_id(sata1/1),noheadings,cols=ap_id:r_state:o_state:condition"
    
    private static final String CMD_CFGADM_SATA_AP_PREFIX =
	"/usr/sbin/cfgadm -s match=partial,select=class(sata):ap_id(";
    private static final String CMD_CFGADM_SATA_AP_POSTFIX =
	"),noheadings,cols=ap_id:r_state:o_state:condition";

    private static final String CMD_NEWFS = "/usr/sbin/newfs";
    private static final String OPT_NEWFS_LARGE = "-T";
    private static final String CMD_MKFS = "/usr/sbin/mkfs";
    private static final String CMD_FSCK = "/usr/sbin/fsck";

    private static final String CMD_DISK_REPLACE = 
	"/opt/honeycomb/bin/disk_replacement";
    private static final String CMD_GET_DEVID =
        "/opt/honeycomb/bin/getdevid.pl";
    private static final String CMD_DISKMAP =
        "/opt/honeycomb/bin/diskmap";

    private static final String CMD_ZERO = "/bin/dd bs=512 if=/dev/zero";
    private static final String CMD_DF = "/bin/df -k";

    private static final String CMD_EXPORTFS = "/usr/sbin/exportfs";
    private static final String CMD_SHARE = "/usr/sbin/share";
    private static final String CMD_UNSHARE = "/usr/sbin/unshare";
    private static final String CMD_SHOWMOUNT = "/usr/sbin/showmount -a";

    private static final String CMD_LSTAT = "/bin/ls -ld";
    private static final String CMD_SYMLINK = "/bin/ln -s";
    private static final String CMD_REMOVE = "/bin/rm -fr";

    private static final String CMD_LUXPROBE = "/usr/sbin/luxadm probe";

    private static final String CMD_LO_SETUP = "/usr/sbin/lofiadm";
    private static final String LOOPDEV_PREFIX = "/dev/lofi/";

    private static final String CMD_MIRROR_OFFLINE =
        "/opt/honeycomb/bin/offline_mirrors.pl";
    private static final String CMD_MIRROR_ONLINE =
        "/opt/honeycomb/bin/online_mirrors.pl";
    private static final String CMD_PREPARE_ONLINE_OFFLINE =
        "/opt/honeycomb/bin/handle_online_offline.pl";

    private static final String DEFAULT_CMD_FSCK_CHECK =
        "/usr/sbin/fsck -F ufs -m";
    private static final String DEFAULT_CMD_FSCK_REPAIR =
        "/usr/sbin/fsck -F ufs -y";

    private static final int DEFAULT_FSCK_MIN_TRIES = 2;
    private static final int DEFAULT_FSCK_MAX_TRIES = 5;
    private static final int DEFAULT_FSCK_NUM_CONSEC = 2;

    private static final String formatProlog = "partition\n";
    private static final String formatPostlog = "label\nquit\nquit\n";

    private static final Pattern deviceKeyPattern =
        Pattern.compile(".*(c\\dt\\dd\\d?).*");


    // Solaris reserves "a few" cylinders for its own use, and we need
    // room for the disk label also; this is a generous estimate, to
    // be sure we don't overflow or collide in the fdisk partition
    // table and the VTOC slices
    protected static final int VTOC_SLOP = 10;

    protected static final int SHORT_TIMEOUT  =   60000; // 1  minute
    protected static final int UMOUNT_TIMEOUT =   10000; // 10 seconds
    protected static final int LONG_TIMEOUT   = 1200000; // 20 minutes

    // Partition tags
    protected static final int V_UNASSIGNED  = 0x00;
    protected static final int V_BOOT        = 0x01;
    protected static final int V_ROOT        = 0x02;
    protected static final int V_SWAP        = 0x03;
    protected static final int V_USR         = 0x04;
    protected static final int V_BACKUP      = 0x05;
    protected static final int V_STAND       = 0x06;
    protected static final int V_VAR         = 0x07;
    protected static final int V_HOME        = 0x08;
    protected static final int V_ALTSCTR     = 0x09;
    protected static final int V_CACHE       = 0x0a; 

    protected static final String[] tagNames = {
        "unsd", "boot", "root", "swap", "usr", "bkup",
        "stnd", "var", "home", "alt", "cach"
    };

    // Partition flags
    protected static final int V_UNMNT       = 0x01;
    protected static final int V_RONLY       = 0x10;

    protected static final Logger logger =
        Logger.getLogger(DiskOps.class.getName());

    private static final Random random =
        new Random(System.currentTimeMillis());

    private static final Map deviceToAccessPoint = new HashMap();
    static {
        deviceToAccessPoint.put("c0t0d0", "sata0/0");
        deviceToAccessPoint.put("c0t1d0", "sata0/1");
        deviceToAccessPoint.put("c1t0d0", "sata1/0");
        deviceToAccessPoint.put("c1t1d0", "sata1/1");
    };

    // fsck commands
    private static final String fsckCheckCommand = getFsckCheckCommand();
    private static final String fsckRepairCommand = getFsckRepairCommand();

    // fsck retries
    private static final int minFsckTries = getMinFsckTries();
    private static final int maxFsckTries = getMaxFsckTries();

    /**
     * Get a List of disks (full paths) in a Solaris system
     */
    public List getDiskPaths(int diskType) throws IOException {
        List paths = new LinkedList();

        switch (diskType) {

        case HardwareProfile.DISKS_LUX:
            // The path to a fibrechannel disk on Solaris is something like
            //     /dev/rdsk/c2t22E4000A33003346d0s2
            // replace the trailing s2 with p0
            String probeCmd = CMD_LUXPROBE;
            
            BufferedReader reader = null;
            
            try {
	            reader = Exec.execRead(probeCmd, logger);
	            String line;
	            int pos;
	            while ((line = reader.readLine()) != null) {
	                if ((pos = line.indexOf("Logical Path:")) < 0)
	                    continue;
	
	                String device = line.substring(pos + 13).trim();
	                int len = device.length();
	                if (device.substring(len - 2).equals("s2"))
	                    device = device.substring(0, len - 2) + "p0";
	                paths.add(device);
	            }
            } finally {
            	if (reader != null)
            		try { 
            			reader.close();
            		} catch (IOException e ){}
            }
            break;

        case HardwareProfile.DISKS_SATA:
            File rootFile = new File("/dev/rdsk");
            File[] children = rootFile.listFiles(new p0FilenameFilter());
            for (int i = 0; i < children.length; i++) {
                paths.add(children[i].getAbsolutePath());
            }
            break;

        case HardwareProfile.DISKS_IDE:
        case HardwareProfile.DISKS_SCSI:

        default:
            throw new InternalException("Don't know how to handle disk type " +
                                        diskType);
        }

        return paths;
    }

    /**
     * Get the partition table (VTOC) for a device. 
     *
     * @param device the raw device to get the partition table of
     * @return array of strings, each representing a partition
     * @throws IOException on any error
     */
    public String[] getPartitionTable(String device)
        throws IOException {

        DiskGeometry geo = getGeometry(device);
        int cylinderSize = geo.sectorsPerTrack() * geo.tracksPerCylinder();

        List lines = new LinkedList();
        String cmd = CMD_PRTVTOC + " " + device;

        // Run prtvtoc and get its stdout
        BufferedReader reader = null;
        
        try {
	        reader = Exec.execRead(cmd, logger);
	        String line;
	        while ((line = reader.readLine()) != null) {
	            if (line.startsWith("*"))
	                // comment
	                continue;
	
	            line = line.replace('\t', ' ').trim();
	
	            String[] fields = line.split("\\s+");
	            int partitionID, tag, flags, pStart, pSize, pLast;
	            try {
	                partitionID = Integer.parseInt(fields[0]);
	                tag = Integer.parseInt(fields[1]);
	                flags = Integer.parseInt(fields[2], 2);
	                pStart = Integer.parseInt(fields[3]);
	                pSize = Integer.parseInt(fields[4]);
	                pLast = Integer.parseInt(fields[5]);
	            }
	            catch (NumberFormatException e) {
	                continue;
	            }
	
	            // Ignore read-only and unmountable slices
	            if (flags != 0)
	                continue;
	
	            String slice = partitionID + ":" + tagNames[tag] + "," +
	                pStart/cylinderSize + "," + pSize/cylinderSize;
	            
	            lines.add(slice);
	        }
        } finally {
        	try {
        		reader.close();
        	} catch (IOException e) {}
        }

        String[] ret = new String[lines.size()];
        int j = 0;
        for (Iterator i = lines.iterator(); i.hasNext(); j++)
            ret[j] = (String)i.next();
        Arrays.sort(ret);
        return ret;
    }

    /**
     * Writes a partition table to a disk. When we say "partition
     * table" we mean both the fdisk partition table as well as the
     * VTOC with slices.
     *
     * @param device the raw device to write the partition table to
     * @param partitionLines description of each slice
     * @throws IOException on any error
     */
    public void writePartitionTable(String device,
                                    String[] partitionLines)
        throws IOException {

        // This is what the disk looks like (slop units are cylinders):
        //
        //  <------- 1 cyl. ------->
        //                           <- VTOC_SLOP -->  ... (HC slices) ...
        // +-+-----+--+---------+---+----------------------------------------+
        // |M|Part.|  |         |   |p0                                      |
        // |B|table|  |DiskLabel|   +------+----+----+-----+-----+-----+-----+
        // |R|     |  | 1 block |   | VTOC | s8 |    | s0  | s1  | s3  | ... |
        // +-+-----+--+---------+---+------+----+----+-----+-----+-----+-----+
        //  <-- 64 blocks ---->
        // 
        // The default partition table created by "fdisk -B" reserves
        // the first cylinder for the MBR and the partition table. The
        // only partition is p0 which starts at the second cylinder
        // and goes to the end of the disk.  (The DiskLabel is after
        // the partition table in the first cylinder.) The p0 VTOC
        // takes the first "few" cylinders (don't know exactly how
        // many -- around 3 or 4, we make it 10 [VTOC_SLOP] for
        // safety) and it also creates s8 of size 1 cylinder. The
        // first HC slice starts after s8.

        String deviceName = device.substring(device.lastIndexOf("/") + 1);
        if (deviceName.endsWith("p0") || deviceName.endsWith("s2"))
            deviceName = deviceName.substring(0, deviceName.length() - 2);

        // First, get the disk geometry.
        DiskGeometry geo = getGeometry(device);
        int labelBlockSize = com.sun.honeycomb.disks.DiskLabel.BLOCK_SIZE;
        if (labelBlockSize != geo.bytesPerSector()) {
            String msg = "DiskLabel's assumed block size (" +
                labelBlockSize + ") != actual (" + geo.bytesPerSector() + ")";
            throw new InternalException(msg);
        }

        /*
         * Make the "MSDOS" partition table have one Solaris partition
         * that begins at cylinder 2 (leave cylinder no. 1 alone for
         * disk label etc.) and goes to the end of the disk
         *
         * Partition IDs: UNIXOS = 99, SUNIXOS = 130, SUNIXOS2 = 191
         */

        int sectorsPerCylinder =
            geo.sectorsPerTrack() * geo.tracksPerCylinder();

        // Create the default Solaris partition table
        String cmd = CMD_FDISK + " -B " + device;
        Exec.exec(cmd, logger);

        /*
         * Slices: s2 is the whole partition, and s8 is the mysterious
         * Solaris boot slice of size 1 cylinder (some sources suggest
         * that this can be removed, but hey, I'm not willing to risk
         * it to gain 0.0056% of the disk). The Honeycomb broot
         * partition is slice 0, size 128 cylinders (1005 MB); spare
         * broot partition, slice 1, same size. The data partition
         * is the rest of the disk (actually, fdisk partition).
         *
         * This is the input file of commands to format(1m) for three slices:
         *     Slice     Tag   Flags  Start Cyl.   Size (cylinders)
         *      0       V_ROOT  wm        1             128
         *      1       V_ROOT  wm      129             128
         *      3       V_HOME  wm      257           17550
         * wm == writeable and mountable.
         *
         * partition    enter partition menu
         * 0            slice number to modify
         * root         tag: V_ROOT
         * wm           writable, mountable
         * 1            start cylinder
         * 128c         size: 128 cylinders
         * 1            next: slice 1
         * root         tag: V_ROOT
         * wm           writeable, mountable
         * 129          start cylinder
         * 128c         size of slice
         * 3            next: slice 3
         * home         tag: V_HOME
         * wm           writeable, mountable
         * 257          start cylinder
         * 17550c       size of slice
         * label        write everything out
         * quit         the partition menu
         * quit         the main menu
         *
         * The file is built up from formatProlog, then a string for
         * each partition, then the formatPostlog. A "partition line"
         * is the partition ID followed by three comma-separated
         * values: the first is the tag, the second the start
         * cylinder, and the third the size in cylinders. A missing
         * value is substituted for if possible.
         */

        // Write the commands to a temp. file
        int usableCylinders = geo.cylinders() - (VTOC_SLOP + 1);
        int currCyl = VTOC_SLOP;
        int remainingCyls = usableCylinders;
        String contents = formatProlog;
        String dbg = device + " partitions:";

        for (int i = 0; i < partitionLines.length; i++) {
            String pLine = partitionLines[i];
            if (pLine == null)
                continue;
            String pair[] = pLine.split(":", 2);
            String id = pair[0];
            String fields[] = pair[1].split(",", -1);
            if (fields.length != 3)
                throw new RuntimeException("Bad partition command \"" + pLine +
                                           "\" for partition \"" + id +
                                           "\": expected three fields, got " +
                                           fields.length);
            String tag = fields[0];
            String st = fields[1].trim();
            String sz = fields[2].trim();

            if (currCyl >= usableCylinders || remainingCyls <= 0)
                throw new RuntimeException("For partition \"" + id +
                    "\": curent cylinder = " + currCyl +
                    " and remaining cylinders = " + remainingCyls +
                    "! Can't continue.");

            int pStart = currCyl, pSize = remainingCyls;
            try {
                if (st.length() > 0)
                    pStart = Integer.parseInt(st);
                if (sz.length() > 0)
                    pSize = Integer.parseInt(sz);
            }
            catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }

            if (pSize > remainingCyls)
                throw new RuntimeException("Partition " + id +
                                           " too big: size = " + pSize +
                                           ", remaining = " + remainingCyls);

            currCyl = pStart + pSize;
            remainingCyls -= pSize;

            contents += id + "\n" + tag + "\nwm\n" +
                pStart + "\n" + pSize + "c\n";

            if (logger.isLoggable(Level.INFO))
                dbg +=
                    " [" + id + ": " + tag + "," + pStart + "," + pSize + "]";
        }

        contents += formatPostlog;

        if (logger.isLoggable(Level.FINE))
            logger.fine(contents);

        File f = File.createTempFile("HCdiskformat", ".txt");
        f.deleteOnExit();
        BufferedWriter fd = new BufferedWriter(new FileWriter(f));
        fd.write(contents);
        fd.close();

        // Run format(1m) with the file just created
        cmd = CMD_FORMAT + " -d " + deviceName + " -msf " + f.getAbsolutePath();
        if (logger.isLoggable(Level.INFO))
            logger.info(dbg);

        Exec.exec(cmd, logger);

        f.delete();
    }

    /**
     * Make a filesystem on a disk.
     *
     * @param device the device (full path, e.g. <tt>/dev/hde2</tt>)
     * @param type the type of filesystem to make
     * @param timeout time to wait for completion (ms)
     * @throws IOException on any error
     */
    public void mkfs(String dev, int type, String extraOptions, long timeout)
        throws IOException {

        String cmd;

        if (type == DiskOps.FS_UFS)
            cmd = CMD_NEWFS;
        else
            cmd = CMD_MKFS + " -F " + fsName(type);

        if (extraOptions != null)
            cmd += " " + extraOptions;

        cmd += " " + dev;

        logger.info(cmd);

        long begin = System.currentTimeMillis();
        //int rc = Exec.exec(cmd, timeout);
        int rc = Exec.exec(new String[]{"/usr/bin/sh", "-c", "/bin/yes | " + cmd},timeout);
        
        long duration = (System.currentTimeMillis() - begin + 500)/1000;

        if (rc == Exec.TIME_EXPIRED)
            throw new IOException("Command \"" + cmd + "\" running >" +
                                  timeout/1000 + "s; terminated");

        if (logger.isLoggable(Level.INFO))
            logger.info("Command \"" + cmd + "\" took " + duration + 
			"s returned " + rc);

        else if (rc != 0)
            throw new IOException("Command \"" + cmd + "\" returned " + rc);
    }

    /**
     * Check a filesystem
     *
     * @param device the full path to the device (e.g. <tt>/dev/hde2</tt>)
     * @param type the type of filesystem on the device
     * @return whether the device contained uncorrectable errors
     * @throws IOException on any error
     */
    public boolean fsck(String device, int type, boolean fixit, long timeout)
        throws IOException {

        Logger l = null;
        if (fixit)
            l = logger;
        
        logger.info("fsck of " + device + " fixit " + fixit);

        if (fixit)
            return exhaustiveFsck(device, type, timeout);

        // Check only.

        String cmd = fsckCheckCommand + " " + device;

        long begin = System.currentTimeMillis();
        int rc = Exec.exec(cmd, timeout, l);
        long duration = (System.currentTimeMillis() - begin + 500)/1000;

        if (rc == Exec.TIME_EXPIRED)
            throw new IOException("Fsck command \"" + cmd + "\" running >" +
                                  timeout/1000 + "s; terminated");

        if (logger.isLoggable(Level.INFO))
            logger.info("Fsck command \"" + cmd + "\" took " + duration + 
			"s returned " + rc);

	if (rc != 0) {
	    logger.warning("Fsck command \"" + cmd + "\" returned " + rc);
	}

        return (rc == 0);
    }

    /**
     * Repeatedly run the fsck command.  Return true if things look OK,
     * false otherwise.
     */
    public boolean exhaustiveFsck(String device, int type, long timeout)
        throws IOException {

        // min times we will fsck, errno & cmd output be damned
        int minTries = getMinFsckTries();

        // max times we will fsck
        int maxTries = getMaxFsckTries();
 
        // number of consecutive successful fsck runs we need
        // to consider the fs clean
        int numConsecFsckRequired = getNumConsecFsckRequired();

        // clean up any obviously bad values
        if (minTries < 1)
            minTries = 1;
        if (maxTries < minTries)
            maxTries = minTries;

        logger.info("exhaustive fsck of " + device + "; minTries " +
            minTries + " maxTries " + maxTries);

        String cmd = fsckRepairCommand + " " + device;
        boolean fsckGoodResult = true;
        int numConsecFsck = 0;

        for (int i = 0; i < maxTries; ) {

            long begin = System.currentTimeMillis();

            fsckGoodResult = fsckMeat(cmd, timeout);

            // record a consecutive good fsck run; if bad,
            // reset the count.
            if (fsckGoodResult)
                numConsecFsck++;
            else
                numConsecFsck = 0;

            long duration = (System.currentTimeMillis() - begin + 500)/1000;

            logger.info("Iteration " + i +
                " of running fsckMeat: fsckGoodResult " + fsckGoodResult);

            if (logger.isLoggable(Level.INFO))
                logger.info("Fsck command \"" + cmd + "\" took " + duration);
            
            // We always run min tries no matter whether we
            // think the fsck ran OK or not.
            if (++i < minTries)
                continue;

            // We want to see consecutive good fsck runs before we
            // declare the fs clean.
            if (numConsecFsck >= numConsecFsckRequired)
                return true;
        }

        return fsckGoodResult;
    }

    /**
     * Guts of the fsck running code.  Runs the command, looking
     * for bad status or output lines indicating that the command
     * needs to be re-run.  Returns true if everything looks good,
     * false otherwise.
     */
    private boolean fsckMeat(String cmd, long timeout) {
    	
        if (logger.isLoggable(Level.INFO))
            logger.info("fsckMeat: running: " + cmd);
    	
        SolarisRuntime sr = new SolarisRuntime();
        BufferedReader reader = null;
        boolean runAgain = false;
        int rc = 0;

        try { 
            sr.exec(cmd);
            reader = new BufferedReader(
                new InputStreamReader(sr.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                // debug
                if (logger.isLoggable(Level.INFO))
                    logger.info("fsckMeat: line is " + line);
                if ((line.indexOf("FILE SYSTEM WAS MODIFIED") != -1) ||
                    (line.indexOf("PLEASE RERUN FSCK") != -1)) {
                    if (logger.isLoggable(Level.INFO))
                        logger.info("fsck: saw '" + line +
                            "' while running '" + cmd + "'");
                    runAgain = true;
                }
            }

            try {
                rc = sr.waitFor();
                if (logger.isLoggable(Level.INFO))
                    logger.info("fsck: rc is " + rc);
             } catch (Throwable iex) {
                if (logger.isLoggable(Level.INFO))
                    logger.info("cmd " + cmd + " was interrupted");
                rc = 1; // interrupted
            }

        } catch (Throwable iex) {
                if (logger.isLoggable(Level.INFO))
                    logger.info("cmd " + cmd + " had problems " + iex);
                rc = 1; // interrupted
        } finally {  
            if (reader != null) {
                try { 
                    reader.close();
                } catch (IOException e ){}
            }
            sr.cleanUp();
        }
 
        if (logger.isLoggable(Level.INFO))
            logger.info("fsckMeat at end rc " + rc + " runAgain " + runAgain);
 
        return (rc == 0 && !runAgain);
    }

    public void deleteSerialNumberMapEntry(String device)
        throws IOException {
        // this could be done purely from Java, but calling
        // a shell script is probably easier and there's already
        // shell logic which deals with this stuff

        String cmd = CMD_DISKMAP + " " + device;

        int rc = Exec.exec(cmd, logger);

        if (logger.isLoggable(Level.INFO))
            logger.info("Command \"" + cmd + " returned " + rc);

        if (rc != 0) {
	    logger.warning("Command \"" + cmd + "\" returned " + rc);
        }
    }

    /**
     * Mount a device on a mount point
     *
     * @param device the name of the device to mount
     * @param type the type of filesystem on the device
     * @param mountPoint the directory to mount to (created if non-existent)
     * @throws IOException on any error
     */
    public void mount(String device, int type, String mountPoint,
                      String opts, boolean force)
        throws IOException {

        // make sure the mount point exists
        File fMountPoint = new File(mountPoint);
        fMountPoint.mkdirs();

        String cmd = CMD_MOUNT + fsName(type);

        if (type == FS_UFS) {
            cmd += " " + OPT_MOUNT_UFS;

            // Note: code assumes that OPT_MOUNT_UFS is not
            // empty; in this case we need a comma before adding
            // the rest of the opts. 

            if (opts != null && opts.length() > 0)
                cmd += "," + opts;
        } else {
            if (opts != null && opts.length() > 0)
            cmd += opts;
        }

        if (force)
            cmd += " -O";

        cmd += " " + device + " " + mountPoint;
        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            throw new IOException("Command \"" + cmd + "\" returned " + rc);

	// make mount points world read/write/exec
	cmd = "/usr/bin/chmod a+rwx " + mountPoint;
	rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
    }

    /**
     * Unmount a device or filesystem
     *
     * @param the device or filesystem to unmount
     * @param type the type of the filesystem
     * @throws IOException on any error
     */
    public void umount(String deviceOrPath, int type, boolean force)
        throws IOException {

        String cmd = CMD_UMOUNT;

        if (force)
            cmd += " -f";

        cmd += " " + deviceOrPath;
        int status = Exec.exec(cmd, UMOUNT_TIMEOUT, logger);
        if (status != 0)
            throw new IOException(cmd + " returned " + status);
    }

    /**
     * Get the list of currently mounted disks (ignores any NFS mounts)
     *
     * @return a table mapping device names to mount points
     * @throws IOException on any error
     */
    public Map getCurrentMounts(int fsType)
        throws IOException {

        HashMap m = new HashMap();

        BufferedReader reader = Exec.execRead(CMD_CURR_MOUNT);

        try {
	        String line;
	        while ((line = reader.readLine()) != null) {
	            String[] fields = line.trim().split("\\s+");
	            if ((fsType == FS_ALL && line.startsWith("/dev/")) ||
	                    fields[3].equals(fsName(fsType)))
	                m.put(fields[0], fields[2]);
	        }
        } finally {
        	try {
        		reader.close();
        	} catch (IOException e){}
        }
        return m;
    }

    public boolean diskIsConfigured(String device) {
        
        Matcher deviceKeyMatcher = deviceKeyPattern.matcher(device);
        
        if (!deviceKeyMatcher.find()) {
            // This should never happen
            if (logger.isLoggable(Level.INFO))
                logger.info("Can't find pattern in " + device);
            return false;
        }
        
        String deviceKey = deviceKeyMatcher.group(1);
        String accessPt = (String)deviceToAccessPoint.get(deviceKey);
        
        if (accessPt == null) {
            if (logger.isLoggable(Level.INFO))
                logger.info("diskIsConfigured: can't get accessPt for " +
                            deviceKey);
            return false;
        }
        
        // splice access point into the middle of the cfgadm command
        String cmd = CMD_CFGADM_SATA_AP_PREFIX + accessPt +
            CMD_CFGADM_SATA_AP_POSTFIX;
        
        BufferedReader reader = null;
        try {
            reader = Exec.execRead(cmd, logger);
        } catch (Exception e) {
            logger.severe("Unable to execRead cfgadm " + e);
            return false;
        }
        
        try {
            String line = reader.readLine();
            
            if (line == null) {
                logger.severe("No output from '" + cmd + "'");
                return false;
            }
            
            String[] fields = line.trim().split("\\s+");
            String apId = fields[0];		// access point ID
            String rState = fields[1];		// receptacle state
            String oState = fields[2];		// occupant state
            String condition = fields[3];
            
            if (logger.isLoggable(Level.INFO))
                logger.info("diskIsConfigured: apId " + apId + 
                            " rState " + rState + " oState " + oState +
                            " condition " + condition);
            
            if (!rState.equals("connected"))
                return false;
            
            if (!oState.equals("configured"))
                return false;
            
            if (!condition.equals("ok"))
                return false;
        } catch (IOException e) {
            logger.severe("Unable to readLine " + e);
        } finally {
            try {
                reader.close();
            } catch (IOException e){}
        }
        
        return true;
    }

    /**
     * Get the list of currently exported filesystems (the mount points)
     *
     * @return a Set of mount points
     * @throws IOException on any error
     */
    public Set getCurrentExports()
        throws IOException {

        Set s = new HashSet();

        BufferedReader reader = Exec.execRead(CMD_EXPORTFS);
        
        try {
	        String line;
	        while ((line = reader.readLine()) != null)
	            s.add(line.trim().split("\\s+")[1]);
        } finally {
        	try {
        		reader.close();
        	} catch (IOException e) {}
        }

        return s;
    }

    /**
     * Get the list of disks (mount points) that some remote host is
     * NFS-mounting
     *
     * @return a Set of disk names
     * @throws IOException on any error
     */
    public Set getCurrentMountedExports()
        throws IOException {

        Set s = new HashSet();

        BufferedReader reader = Exec.execRead(CMD_SHOWMOUNT);
        
        try{
	        String line;
	        while ((line = reader.readLine()) != null)
	            s.add(line.split(":")[1]);
        } finally {
        	try { 
        		reader.close();
        	} catch (IOException e) {}
        }

        return s;
    }

    /**
     * Get the size of the free space (in bytes) on a filesystem
     *
     * @param path the filesystem to return info for
     * @throws IOException on exec error
     */
    public long df(String path) throws IOException {
        BufferedReader reader = Exec.execRead(CMD_DF + " " + path);
        String line;
        while ((line = reader.readLine()) != null)
            if (line.startsWith("/dev/")) {
                String fields[] = line.trim().split("\\s+");
                // Block size is 1K for df
                try {
                    return 1024 * Long.parseLong(fields[1]);
                } catch (NumberFormatException e) {
                    throw new InternalError("Can't parse output of df " + e);
                } finally {
                	try {
                		reader.close();
                	} catch (IOException e){}
                }
            }
        return 0;
    }

    /**
     * Take any mirrors associated with the device offline.
     *
     * @param device the disk which is going offline.
     * @throws IOException on any error
     */
    public boolean mirrorOffline(String device) {
        int rc = 1;

        try {
            rc = Exec.exec(new String[]{CMD_MIRROR_OFFLINE, device},
                LONG_TIMEOUT, logger);
        } catch (IOException e) {
            logger.warning("Command \"" + CMD_MIRROR_OFFLINE + " " +
                device + "\" got exception " + e);
        }

	if (rc != 0) {
	    logger.warning("Command \"" + CMD_MIRROR_OFFLINE + " " +
                device + "\" returned " + rc);
	}

        return (rc == 0);
    }

    /**
     * Put any mirrors associated with the device online.
     *
     * @param device the disk which has come online.
     * @throws IOException on any error
     */
    public boolean mirrorOnline(String device) {
        int rc = 1;

        try {
            rc = Exec.exec(new String[]{CMD_MIRROR_ONLINE, device},
                LONG_TIMEOUT, logger);
        } catch (IOException e) {
            logger.warning("Command \"" + CMD_MIRROR_ONLINE + " " +
                device + "\" got exception " + e);
        }

	if (rc != 0) {
	    logger.warning("Command \"" + CMD_MIRROR_ONLINE + " " +
                device + "\" returned " + rc);
	}

        return (rc == 0);
    }

    /**
     * Disassociate any dump, swap, and mirror devices associated with
     * the disk.
     *
     * @param device the disk which is going offline.
     * @throws IOException on any error
     */
    public boolean dumpSwapMirrorOffline(String device) throws IOException {
        return (deviceOfflineOnline(device,
                true /* isOffline */,
                false /* doCfg */));
    }

    /**
     * Set up any dump, swap, and mirror devices associated with
     * the device since it has come online.
     *
     * @param device the disk which is coming online.
     * @throws IOException on any error
     */
    public boolean dumpSwapMirrorOnline(String device) throws IOException {
        return (deviceOfflineOnline(device,
                false /* isOffline */,
                false /* doCfg */));
    }

    /**
     * Prepare the disk for physical removal.
     * Involves configuring the SATA access point so that
     * the disk may be safely removed.
     *
     * @param device the disk which is going to be removed.
     * @throws IOException on any error
     */
    public boolean deviceOffline(String device) throws IOException {
        return (deviceOfflineOnline(device,
                true /* isOffline */,
                true /* doCfg */));
    }

    /**
     * Prepare the disk for physical removal.
     * Involves configuring the SATA access point so that
     * the disk may be safely removed.
     *
     * @param device the disk which is going to be removed.
     * @throws IOException on any error
     */
    public boolean deviceOnline(String device) throws IOException {
        return (deviceOfflineOnline(device,
                false /* isOffline */,
                true /* doCfg */));
    }

    /**
     * Prepare the disk prior to removal or after insertion.
     * Add/remove dump device, zfs mirror, and swap association, optionally
     * config/unconfig SATA attachment point.
     */
    private boolean deviceOfflineOnline(String device,
        boolean isOffline,
        boolean doCfg)
        throws IOException {

        String op = "";

        if (isOffline && doCfg) {
            op = "offline";
        } else if (isOffline && !doCfg) {
            op = "dumpswap_offline";
        } else if (!isOffline && doCfg) {
            op = "online";
        } else if (!isOffline && !doCfg) {
            op = "dumpswap_online";
        }
        
        logger.info("deviceOfflineOnline: preparing " + device + " for " + op);
        String result = "error: ";
        String cmd = CMD_PREPARE_ONLINE_OFFLINE + " " + op + " " + device;
        int pos = -1;
        BufferedReader out = null;

        /*
         * Exec the command
         */
        try {
            out = Exec.execRead(cmd, logger);

            while ((result = out.readLine()) != null) {
                // look for indications of success or failure
                pos = result.indexOf("ok");
                if (pos == 0) {
                    if (logger.isLoggable(Level.INFO))
                        logger.info("deviceOfflineOnline: " + op +
                            " success; result: " + result);
                    return true;
                }

                pos = result.indexOf("error: ");
                if (pos == 0) {
                    if (logger.isLoggable(Level.INFO))
                        logger.info("deviceOfflineOnline: " + op +
                            " failure; result: " + result);
                    return false;
                }
            }
        } catch (IOException e) {
            logger.severe("deviceOfflineOnline: " + op + " " + cmd +
                "failed: " + e);
        } catch (Exception e) {
            // XXX: non-IOException: need to figure out the source of these
            // in a future release.
            logger.severe("deviceOfflineOnline: " + op + " " + cmd +
                "failed: " + e);
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException e ){};
            }
        }

        if (logger.isLoggable(Level.INFO))
            logger.info("deviceOfflineOnline: " + op +
                " no 'ok' or 'error'; result: " + result);

        // we failed to find an indication of success or failure
        return false;
    }


    /**
     * Do all the bootloader magic to make the device bootable
     *
     * @param device the disk to make bootable
     * @param deviceRoot the mount-point for the device
     * @throws IOException on any error
     */
    public void makeBootable(String device, String deviceRoot)
        throws IOException {

        throw new RuntimeException("Unimplemented");
    }

    /**
     * Create a virtual device (loop device) on a file
     *
     * @param index the number of the loop device to use
     * @param filename the name of the file to use for storage
     * @param sizeMB size (in megabytes) of the device
     * @return the name of the virtual device created
     * @throws IOException on any error
     */
    public String makeVirtualDisk(int index, String filename,
                                  long sizeMB)
        throws IOException {
                
        String cmd;
        int rc;

        if (!new File(filename).exists()) {
            cmd = CMD_ZERO + " of=" + filename +
                " count=" + sizeMB * 1024 * 2;
            rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
            if (rc != 0)
                throw new IOException("Error: \"" + cmd + "\" returned " + rc);
        }

        // And don't forget that a loop device is a block device.

        String device = LOOPDEV_PREFIX + (1 + index);
        cmd = CMD_LO_SETUP + " -a " + filename;
        rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            throw new IOException("Error: \"" + cmd + "\" returned " + rc);

        return device;
    }

    /**
     * Allow a directory to be exported to other HC nodes
     *
     * @param path the directory to export
     * @throws IOException on any error
     */
    public void export(String path) throws IOException {
        if (path == null || path.length() == 0)
            return;

        String cmd = CMD_SHARE + " -F nfs -o rw=@10.123.45.0," +
	    "root=@10.123.45.0 " + path;
        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            logger.warning("exportfs \"" + path + "\" returned " + rc);
    }

    /**
     * Stop a directory from being exported to other HC nodes
     *
     * @param path the directory to unexport
     * @throws IOException on any error
     */
    public void unexport(String path) throws IOException {
        if (path == null || path.length() == 0)
            return;

        String cmd = CMD_UNSHARE + " -F nfs " + path;
        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            throw new IOException("unshare \"" + path + "\" returned " + rc);
    }

    /**
     * Remove a file (and all files below, if it's a directory)
     *
     * @param filename the name of the file to remove
     * @throws IOException on any error
     */
    public void remove(String filename) throws IOException {
        String cmd = CMD_REMOVE + " " + filename;

        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            throw new IOException("Error: \"" + cmd + "\" returned " + rc);
    }

    /**
     * Create a symbolic link
     *
     * @param filename the target of the link
     * @param linkname the name of the link
     * @throws IOException on any error
     */
    public void link(String filename, String linkname)
        throws IOException {

        String cmd = CMD_SYMLINK + " " + filename + " " + linkname;
        
        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            throw new IOException("Error: \"" + cmd + "\" returned " + rc);
    }

    public boolean isLink(String path) throws IOException {
        String cmd = CMD_LSTAT + " " + path;
        BufferedReader ls = Exec.execRead(cmd, logger);
        String line = ls.readLine();
        ls.close();
        return line.substring(0, 1).equals("l");
    }

    /**
     * Unpack an archive (<tt>.tar.gz</tt>)
     *
     * @param archiveName the archive file to unpack
     * @param root the directory to unpack to
     * @throws IOException on any error
     */
    public void unpack(String archiveName, String root)
        throws IOException {

        throw new RuntimeException("Unimplemented");
    }

    public String getSerialNo(String device) throws IOException {
        String cmd = CMD_GET_DEVID + " -d " + device;
        BufferedReader ls = Exec.execRead(cmd, logger);
        String line = ls.readLine();
        ls.close();
        if (logger.isLoggable(Level.INFO))
            logger.info("Command \"" + cmd + "returned: " + line); 
        if (line != null)
            return line;
        else
            return "MISSINGDISK";
    }

    public String getPartitionDevice(String device, int partitionIndex,
                                     String[] partitions) {
        if (partitionIndex >= partitions.length)
            return null;

        // If the device begins with /dev/rdsk/ replace it with /dev/dsk/
        String rawdevPrefix = "/dev/rdsk/";
        String cookedPrefix = "/dev/dsk/";
        if (device.startsWith(rawdevPrefix))
            device = cookedPrefix + device.substring(rawdevPrefix.length());

        // If device ends in p0 or s2, replace with "s" + partitionId
        int pPos = device.lastIndexOf("p");
        int sPos = device.lastIndexOf("s");
        int pos = (pPos > sPos)? pPos : sPos; // max()
        if (pos < 0)
            // Loop devices don't have this scheme
            return device;

        String partitionId = partitions[partitionIndex].split(":", 2)[0];
        String prefix = device.substring(0, pos);
        String suffix = device.substring(pos);

        String slice = "s" + partitionId;

        // This test may not be required, but....
        if (suffix.length() < 2 || suffix.length() > 3) {
            String msg = "Path \"" + device + "\" bogus: too many ";
            if (suffix.startsWith("p"))
                msg += "fdisk partitions";
            else
                msg += "slices";
            throw new InternalException(msg);
        }

        return prefix + slice;
    }

    /**
     * Prepare any replaced disks
     *
     * @param timeout how long to wait before timing out this command
     * @throws IOException on any error
     */
    public void processReplacedDisks(long timeout)
        throws IOException {
	
	String cmd;
	
	cmd = CMD_DISK_REPLACE;
	
	logger.info("Checking for and preparing any replaced disks...");
	
	long begin = System.currentTimeMillis();
	
        int rc = Exec.exec(cmd, timeout, logger);
        
        long duration = (System.currentTimeMillis() - begin + 500)/1000;
	
        if (rc == Exec.TIME_EXPIRED)
            throw new IOException("Command \"" + cmd + "\" running >" +
                                  timeout/1000 + "s; terminated");
	
        if (logger.isLoggable(Level.INFO))
            logger.info("Command \"" + cmd + "\" took " + duration + 
			"s returned " + rc);
	
        else if (rc != 0)
            throw new IOException("Command \"" + cmd + "\" returned " + rc);
    }
    
    protected class DiskGeometry implements Serializable {
    
        private int numCylinders;
        private int numTracksPerCylinder;
        private int numSectorsPerTrack;
        private int numBytesPerSector;

        protected DiskGeometry(int cyl, int tracks, int sectors, int sectSize) {
            numCylinders = cyl;
            numTracksPerCylinder = tracks;
            numSectorsPerTrack = sectors;
            numBytesPerSector = sectSize;
        }

        public int cylinders() { return numCylinders; }
        public int tracksPerCylinder() { return numTracksPerCylinder; }
        public int sectorsPerTrack() { return numSectorsPerTrack; }
        public int bytesPerSector() { return numBytesPerSector; }
    }

    /**
     * Get the geometry for a disk.
     *
     * @param disk the disk to get the geometry for
     * @return the geometry
     * @throws IOException on any error
     */
    private DiskGeometry getGeometry(String disk) throws IOException {
        String cmd = CMD_FDISK + " -G " + disk;
        
        BufferedReader reader = null;
        
        try { 
	        reader = Exec.execRead(cmd, logger);
	        String line;
	        while ((line = reader.readLine()) != null) {
	            if (line.startsWith("*"))
	                continue;
	            reader.close();     // There should be only one line
	
	            String fields[] = line.trim().split("\\s+");
	            try {
	                int pcyl = Integer.parseInt(fields[0]);
	                // fdisk -G returns a cylinder count that's too high
	                int ncyl = Integer.parseInt(fields[1]) - 10;
	                int nhead = Integer.parseInt(fields[4]);
	                int nsect = Integer.parseInt(fields[5]);
	                int sectSize = Integer.parseInt(fields[6]);
	
	                if (logger.isLoggable(Level.INFO))
	                    logger.info("Disk \"" + disk + "\": CHS (" + ncyl + "," +
	                            nhead + "," + nsect + ") total blocks = " +
	                            ncyl * nhead * nsect);
	
	                return new DiskGeometry(ncyl, nhead, nsect, sectSize);
	            }
	            catch (NumberFormatException e) {
	                throw new RuntimeException("Couldn't parse \"" + cmd +
	                                           "\" output -- \"" + line + "\" " + e);
	            }
	        }
        } finally { 
        	if (reader != null)
        		try {
        			reader.close();
        		} catch (IOException e){}
        }
        throw new RuntimeException("EOF trying to read \"" + cmd + "\"");
    }

    private static String getFsckCheckCommand() {
        ClusterProperties config = ClusterProperties.getInstance();

        String fsckCommand = getFsckCommandMeat(config,
            ConfigPropertyNames.PROP_DISK_FSCK_CHECK_COMMAND,
            DEFAULT_CMD_FSCK_CHECK);

        logger.info("Fsck check command is '" + fsckCommand + "'");

        return fsckCommand;
    }

    private static String getFsckRepairCommand() {
        ClusterProperties config = ClusterProperties.getInstance();

        String fsckCommand = getFsckCommandMeat(config,
            ConfigPropertyNames.PROP_DISK_FSCK_REPAIR_COMMAND,
            DEFAULT_CMD_FSCK_REPAIR);

        logger.info("Fsck repair command is '" + fsckCommand + "'");

        return fsckCommand;
    }

    private static String getFsckCommandMeat(ClusterProperties config,
        String pname, String defaultVal) {

        String s = config.getProperty(pname);
        if (s != null) return s;

        return defaultVal;
    }

    private static int getMinFsckTries() {
        ClusterProperties config = ClusterProperties.getInstance();

        int tries = getIntProperty(config,
            ConfigPropertyNames.PROP_DISK_FSCK_MIN_TRIES,
            DEFAULT_FSCK_MIN_TRIES);

        logger.info("Fsck min tries is " + tries);

        return tries;
    }

    private static int getMaxFsckTries() {
        ClusterProperties config = ClusterProperties.getInstance();

        int tries = getIntProperty(config,
            ConfigPropertyNames.PROP_DISK_FSCK_MAX_TRIES,
            DEFAULT_FSCK_MAX_TRIES);

        logger.info("Fsck max tries is " + tries);

        return tries;
    }

    private static int getNumConsecFsckRequired() {
        ClusterProperties config = ClusterProperties.getInstance();

        int tries = getIntProperty(config,
            ConfigPropertyNames.PROP_DISK_FSCK_NUM_CONSEC,
            DEFAULT_FSCK_NUM_CONSEC);

        logger.info("Fsck number of consecutive good runs is " + tries);

        return tries;
    }

    private static int getIntProperty(ClusterProperties config,
        String pname, int defaultVal) {
        
        int tries = defaultVal;

        try {
            String s = config.getProperty(pname);
            if (s != null) {
                tries = Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
            logger.warning("failed to parse " + pname + ": " + e);
        }

        return tries;
    }

    private static class p0FilenameFilter
        implements FilenameFilter {

        private p0FilenameFilter() {
        }
        
        public boolean accept(File dir, String filename) {
            return (filename.endsWith("p0"));
        }
    }

}

// 456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789
