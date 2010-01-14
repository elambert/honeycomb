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



package com.sun.honeycomb.util.sysdep.linux;

import java.io.*;

import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.util.Exec;

/**
 * This is an implementation of interface DiskOps for Gentoo Linux R1 2.6.3.
 * This class does not know anything about Honeycomb.
 *
 * @author Shamim Mohamed
 * @version $Revision: 1.4 $ $Date: 2008-03-04 15:50:17 -0800 (Tue, 04 Mar 2008) $
 */
public class DiskOps extends com.sun.honeycomb.util.sysdep.DiskOps {

    // The various commands and options

    private static final String CMD_SFDISK = "/sbin/sfdisk -q";

    private static final String CMD_INSTALL = "/sbin/lilo -F -w -C";
    private static final String OPTION_INSTALL_CHROOT = "-r";

    private static final String CMD_MOUNT = "/bin/mount -t ";
    private static final String CMD_UMOUNT = "/bin/umount";
    private static final String PROC_MOUNT = "/proc/mounts";

    private static final String CMD_MKFS = "/sbin/mkfs";
    private static final String CMD_FSCK = "/sbin/fsck";

    private static final String CMD_DISK_REPLACE = 
	"/opt/honeycomb/bin/disk_replacement";

    private static final String CMD_LOSETUP = "/sbin/losetup";
    private static final String LOOPDEV_PREFIX = "/dev/loop";
    private static final String CMD_ZERO = "/bin/dd bs=1k if=/dev/zero";
    private static final String CMD_DF = "/bin/df";
    private static final String CMD_EXPORTFS = "/usr/sbin/exportfs";
    private static final String CMD_SHOWMOUNT = "/usr/sbin/showmount -a";

    private static final String CMD_UNTAR = "/bin/tar xzf";
    private static final String OPTION_UNTAR_ROOT = "-C";

    private static final String CMD_LSTAT = "/bin/ls -ld";
    private static final String CMD_SYMLINK = "/usr/bin/ln -s";
    private static final String CMD_REMOVE = "/usr/bin/rm -fr";

    private static final String PATH_PROC_CMDLINE = "/proc/cmdline";
    private static final String PATH_BOOT_CONF = "boot/lilo.conf";

    private static final String PATH_KERNEL_INITRD = "/boot/active/";
    private static final String NAME_KERNEL = "bzImage";
    private static final String NAME_INITRD = "initrd.gz";

    protected static final int SHORT_TIMEOUT  =  60000; // 1  minute
    protected static final int UMOUNT_TIMEOUT = 300000; // 5  minutes
    protected static final int LONG_TIMEOUT   = 600000; // 10 minutes

    private static final String DISKDEV_DIR = "/dev";
    private static final String DISKDEV_PATTERN = "[hs]d[a-z]";

    protected static final Logger logger =
        Logger.getLogger(DiskOps.class.getName());

    private static final Random random =
        new Random(System.currentTimeMillis());

    /**
     * Get a List of disks (full paths) in a Linux system
     *
     * @param diskType the type of disks the system has
     * @return a list of all disks on the system
     */
    public List getDiskPaths(int diskType) throws IOException {
        List paths = new LinkedList();

        switch (diskType) {

        case HardwareProfile.DISKS_IDE:
        case HardwareProfile.DISKS_SATA:
        case HardwareProfile.DISKS_SCSI:
            File[] devs =
                new File(DISKDEV_DIR).listFiles(new DiskDeviceFilter());
            Arrays.sort(devs);

            for(int i = 0; i < devs.length; i++)    
                paths.add(devs[i].getAbsolutePath());
            break;

        default:
            throw new InternalException("Don't know how to handle disk type " +
                                        diskType);
        }

        return paths;
    }
    private static class DiskDeviceFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return name.matches(DISKDEV_PATTERN);
        }
    }

    /**
     * Get the partition table for a device. 
     *
     * @param device the raw device to get the partition table of
     * @return array of strings, each representing a partition
     * @throws IOException on any error
     */
    public String[] getPartitionTable(String device)
        throws IOException {

        List lines = new LinkedList();
        String cmd = CMD_SFDISK + " -uM -l " + device;

        // Run sfdisk and get its stdout
        BufferedReader reader = Exec.execRead(cmd, logger);
        String line;
        while ((line = reader.readLine()) != null)
            if (line.startsWith("/dev/"))
                lines.add(line);

        // Why doesn't this work?
        // return (String[]) lines.toArray();

        String[] ret = new String[lines.size()];
        int j = 0;
        for (Iterator i = lines.iterator(); i.hasNext(); j++)
            ret[j] = (String)i.next();
        return ret;
    }

    /**
     * Writes a partition table to a disk.
     *
     * @param device the raw device to write the partition table to
     * @param partitionLines array of strings, each representing a partition
     * @throws IOException on any error
     */
    public void writePartitionTable(String device,
                                    String[] partitionLines)
        throws IOException {

        String cmd = CMD_SFDISK + " -D -uM " + device;

        // Run sfdisk and get its stdin
        PrintStream writer = Exec.execWrite(cmd, logger);

        for (int i = 0; i < partitionLines.length; i++) {
            String pair[] = partitionLines[i].split(":", 2);
            String id = pair[0];
            writer.println(pair[1]);
        }

        writer.close();
    }

    /**
     * Make a filesystem on a disk.
     *
     * @param device the device (full path, e.g. <tt>/dev/hde2</tt>)
     * @param type the type of filesystem to make
     * @throws IOException on any error
     */
    public void mkfs(String device, int type, String options, long timeout)
        throws IOException {

        String cmd = CMD_MKFS + " -t " + fsName(type);

        if (options != null)
            cmd += " " + options;

        if (type == DiskOps.FS_XFS)
            cmd += " -f";

        cmd += " " + device;

        int rc = Exec.exec(cmd, timeout, logger);
        if (rc != 0)
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

        // Damn XFS -- its fsck does nothing, it checks and repairs on
        // mount. We mount to a temp location and unmount.
        if (type == DiskOps.FS_XFS) {
            try {
                String tempDir = "/tmp/tmpMount." + random.nextInt();
                mount(device, type, tempDir, null);
                unmount(tempDir, type);
                remove(tempDir);
            }
            catch (IOException e) {
                // We call this an fsck failure
                return false;
            }
        }
        else {
            String cmd = CMD_FSCK + " -t " + fsName(type) +
                " -a " + device;
            int status = Exec.exec(cmd, timeout, logger);
            // A return of 1 from fsck means "File system errors
            // corrected", which is not an error
            if (status != 0 && status != 1)
                return false;
        }

        return true;
    }

    public void deleteSerialNumberMapEntry(String device)
        throws IOException {
        // not implemented for Linux
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
	
        int rc = Exec.exec(cmd, timeout);
        
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

        if (force)
            throw new RuntimeException("unimplemented");

        // make sure the mount point exists
        File fMountPoint = new File(mountPoint);
        fMountPoint.mkdirs();

        String cmd = CMD_MOUNT + fsName(type);
        if (type == DiskOps.FS_XFS)
            cmd += " -o wsync";

        if (opts != null && opts.length() > 0)
            cmd += " " + opts;

        cmd += " " + device + " " + mountPoint;
        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            throw new IOException("Command \"" + cmd + "\" returned " + rc);
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
        if (type == DiskOps.FS_XFS)
            // xfs handles a lazy unmount properly
            cmd += " -l";

        cmd += " " + deviceOrPath;
        int status = Exec.exec(cmd, UMOUNT_TIMEOUT, logger);
        if (status != 0 && status != 1) {
            // the device may not be mounted, so ignore status 1
            throw new IOException(cmd + " returned " + status);
        }

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

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(PROC_MOUNT));
        } catch (FileNotFoundException e) {
            throw new IOException("Special file \"" + PROC_MOUNT + 
                                  "\" not found");
        }
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.split(" ");
            if ((fsType == FS_ALL && line.startsWith("/dev/")) ||
                    fields[2].equals(fsName(fsType)))
                m.put(fields[0], fields[1]);
        }

        return m;
    }


    /**
     * Not implemented under Linux.
     */
    public boolean diskIsConfigured(String device) {
        return false;
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
        String line;
        while ((line = reader.readLine()) != null)
            s.add(line.split(" ")[0]);

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
        String line;
        while ((line = reader.readLine()) != null)
            s.add(line.split(":")[1]);

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
                String fields[] = line.split(" ");
                // Block size is 1K for df
                try {
                    return 1024 * Long.parseLong(fields[1]);
                } catch (NumberFormatException e) {
                    throw new InternalError("Can't parse output of df " + e);
                }
            }
        return 0;
    }

    /**
     * Take any mirrors associated with the device offline.
     * Stubbed out for Linux.
     *
     * @param device the disk which is going offline.
     * @throws IOException unconditionally.
     */
    public boolean mirrorOffline(String device) throws IOException {
        throw new IOException("mirrorOffline not supported under Linux");
    }

    public boolean mirrorOnline(String device) throws IOException {
        throw new IOException("mirrorOffline not supported under Linux");
    }

    public boolean deviceOffline(String device) throws IOException {
        throw new IOException("deviceOffline not supported under Linux");
    }

    public boolean deviceOnline(String device) throws IOException {
        throw new IOException("deviceOnline not supported under Linux");
    }

    public boolean dumpSwapMirrorOffline(String device) throws IOException {
        throw new
            IOException("dumpSwapMirrorOffline not supported under Linux");
    }

    public boolean dumpSwapMirrorOnline(String device) throws IOException {
        throw new
            IOException("dumpSwapMirrorOnline not supported under Linux");
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

        String kernel = PATH_KERNEL_INITRD + NAME_KERNEL;
        String initrd = PATH_KERNEL_INITRD + NAME_INITRD;

        // Get the "ip=<IP>:<BOOTSERVERIP>:<ROUTER>:<NETMASK>" from the
        // current kernel command line to add to the boot parameters of
        // the installed kernel. The "/proc/cmdline" file is of the format
        // ramdisk=<size> initrd=<file name> root=/dev/ram0
        // BOOT_IMAGE=<file name> ip=<IP>:<BOOTSERVERIP>:<ROUTER>:<NETMASK>

        File file = new File(PATH_PROC_CMDLINE);
        FileReader fr = new FileReader(file);
        BufferedReader inFile = new BufferedReader(fr);
        String line = inFile.readLine();
        String[] params = line.split(" ");
        String ip = null;
        for (int i = 0; i < params.length; i++) {
            if (params[i].startsWith("ip=")) {
                ip = params[i];
                break;
            }
        }
        fr.close();

        new File(deviceRoot + PATH_KERNEL_INITRD).mkdirs();

        if (! new File(kernel).exists())
            throw new IOException("Kernel file \"" + kernel + "\" not found.");
        Exec.exec("cp " + kernel + " " + deviceRoot +
                  PATH_KERNEL_INITRD + NAME_KERNEL, logger);

        if (! new File(initrd).exists())
            throw new IOException("Initrd file \"" + initrd + "\" not found.");
        Exec.exec("cp " + initrd + " " + deviceRoot +
                  PATH_KERNEL_INITRD + NAME_INITRD, logger);

        // Make sure that the ip string is valid
        if (ip == null)
            throw new IOException("Error getting the IP address from the" +
                                  " kernel commandline \"" + line + "\"");

        // Construct the contents of lilo.conf
        String conf = 
            "lba32\n" +
            "serial=0,115200n8\n" +
            "boot=" + device + "\n" +
            "map=/boot/System.map\n" +
            "install=/boot/boot.b\n" +
            "image=" + PATH_KERNEL_INITRD + NAME_KERNEL + "\n" +
            "   append=\"ramdisk=262144 root=/dev/ram0 console=ttyS0,115200n8 "
                + ip + " panic=10\"\n" +
            "   initrd=" + PATH_KERNEL_INITRD + NAME_INITRD + "\n";

        // Write out the config file
        String confFile = deviceRoot + "/" + PATH_BOOT_CONF;
        FileWriter out = new FileWriter(new File(confFile));
        out.write(conf);
        out.close();

        String cmd = CMD_INSTALL + " " + PATH_BOOT_CONF + " " +
            OPTION_INSTALL_CHROOT + " " + deviceRoot;
        PrintStream w = Exec.execWrite(cmd, logger);
        w.println("y"); w.close();
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

        String cmd = CMD_ZERO + " of=" + filename +
            " count=" + sizeMB * 1024;
        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            throw new IOException("Error: \"" + cmd + "\" returned " + rc);

        String device = LOOPDEV_PREFIX + index;
        cmd = CMD_LOSETUP + " " + device + " " + filename;
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
        String cmd = CMD_EXPORTFS +
            " -o async,rw,no_root_squash,no_wdelay " + "hcb*:" + path;
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
        String cmd = CMD_EXPORTFS + " -u " + "hcb*:" + path;
        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            logger.warning("exportfs -u \"" + path + "\" returned " + rc);
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

        // make sure the root exists
        File fRoot = new File(root);
        fRoot.mkdirs();

        String cmd = CMD_UNTAR + " " + archiveName + " " +
            OPTION_UNTAR_ROOT + " " + root;
        int rc = Exec.exec(cmd, LONG_TIMEOUT, logger);
        if (rc != 0)
            throw new IOException("Error: \"" + cmd + "\" returned " + rc);
    }

    public String getSerialNo(String device) throws IOException {
        return "serial number unimplemented";
    }

    public String getPartitionDevice(String device, int partitionIndex,
                                     String[] partitions) {
        if (partitionIndex >= partitions.length)
            return null;

        return device + partitions[partitionIndex].split(":", 2)[0];
    }

}
