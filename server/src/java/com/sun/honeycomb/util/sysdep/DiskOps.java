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



package com.sun.honeycomb.util.sysdep;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This interface encapsulated system-dependent disk stuff: partition
 * tables, mkfs/fsck, mount/unmount, NFS export/unexport, un-tar etc.
 *
 * Important note: we only deal with partition indices into an array
 * of partitions, and each partition has a system-dependent ID.
 * (Partition IDs start at 0 or 1, and may skip some values like 2 and
 * 8 on Solaris).  This class will handle any translations required;
 * getPartitionDevice() is an externally-visible entry point to this
 * function.
 *
 * @author Shamim Mohamed
 * @version $Revision: 1.3 $ $Date: 2008-03-04 15:50:17 -0800 (Tue, 04 Mar 2008) $
 */
public abstract class DiskOps {

    // Filesystem types
    static public final int FS_ALL = -1;
    static public final int FS_NONE = 0;
    static public final int FS_XFS = 1;
    static public final int FS_UFS = 2;
    static public final int FS_ZFS = 3;
    static public final int FS_NFS = 4;
    static public final int FS_EXT2 = 5;

    public static String fsName(int fsType) {
        switch (fsType) {
        case FS_EXT2: return "ext2";
        case FS_XFS:  return "xfs";
        case FS_UFS:  return "ufs";
        case FS_ZFS:  return "zfs";
        case FS_NFS:  return "nfs";
        default: return "";
        }
    }

    // System-dependent methods

    public abstract List getDiskPaths(int diskType)
        throws IOException;

    public abstract String getSerialNo(String device) throws IOException;

    public abstract String getPartitionDevice(String device, int index,
                                              String[] partitions);

    public abstract String[] getPartitionTable(String device)
        throws IOException;
    public abstract void writePartitionTable(String device, String[] partitions)
        throws IOException;

    public abstract void mkfs(String device, int fsType, String extraOptions,
                              long timeout) throws IOException;
    public abstract boolean fsck(String device, int fsType, boolean fixit,
                                 long timeout) throws IOException;

    public abstract void deleteSerialNumberMapEntry(String device)
        throws IOException;

    public abstract void processReplacedDisks(long timeout) 
	throws IOException;

    public abstract void mount(String device, int fsType, String mountPoint,
                               String options, boolean force)
        throws IOException;
    public abstract void umount(String deviceOrPath, int fsType, boolean force)
        throws IOException;

    public abstract void export(String path)
        throws IOException;
    public abstract void unexport(String path)
        throws IOException;

    public abstract Map getCurrentMounts(int fsType)
        throws IOException;
    public abstract Set getCurrentExports()
        throws IOException;
    public abstract Set getCurrentMountedExports()
        throws IOException;

    public abstract long df(String path)
        throws IOException;

    public abstract void makeBootable(String device, String deviceRoot)
        throws IOException;

    public abstract String makeVirtualDisk(int index, String filename, 
                                           long sizeMB)
        throws IOException;

    public abstract void remove(String filename)
        throws IOException;

    public abstract void link(String filename, String linkname)
        throws IOException;

    public abstract boolean isLink(String path)
        throws IOException;

    public abstract void unpack(String archiveName, String root)
        throws IOException;

    public Map getCurrentMounts() throws IOException {
        return getCurrentMounts(FS_ALL);
    }

    /**
     * Is the disk configured so it can be used for IO?
     */
    public abstract boolean diskIsConfigured(String device);

    public void unmount(String deviceOrPath, int fsType) throws IOException {
        umount(deviceOrPath, fsType, false);
    }

    public void unmount(String deviceOrPath, int fsType,
			boolean force) throws IOException {
        umount(deviceOrPath, fsType, force);
    }

    public void mount(String device, int fsType, String mountPoint,
                      String options)
            throws IOException {
        mount(device, fsType, mountPoint, options, false);
    }

    public abstract boolean mirrorOffline(String device)
        throws IOException;

    public abstract boolean mirrorOnline(String device)
        throws IOException;

    public abstract boolean deviceOffline(String device)
        throws IOException;

    public abstract boolean deviceOnline(String device)
        throws IOException;

    public abstract boolean dumpSwapMirrorOffline(String device)
        throws IOException;

    public abstract boolean dumpSwapMirrorOnline(String device)
        throws IOException;

    public boolean rmdirP(String target) {
        boolean ok = true;
        File f = new File(target);
        if (f.isDirectory()) {
            String l[] = f.list();
            for (int i = 0; i < l.length; i++) {
                ok |= rmdirP(target + "/" + l[i]);
            }
        }
        ok |= f.delete();
        return ok;
    }
    
    // Factory
    private static DiskOps ops = null;
    public static synchronized DiskOps getDiskOps() {
        if (ops == null) {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.equals("sunos") || os.equals("solaris"))
                ops = new com.sun.honeycomb.util.sysdep.solaris.DiskOps();
            else if (os.equals("linux"))
                ops = new com.sun.honeycomb.util.sysdep.linux.DiskOps();
            else
                ops = null;
        }

        return ops;
    }
}
