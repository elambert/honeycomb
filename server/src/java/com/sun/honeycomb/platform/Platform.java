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

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskProxy;

import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.oa.Common;

import com.sun.honeycomb.hwprofiles.HardwareProfile;

import com.sun.honeycomb.common.InternalException;

import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.util.sysdep.SystemInfo;
import com.sun.honeycomb.util.sysdep.MemoryInfo;
import com.sun.honeycomb.platform.BIOSInfo;
import com.sun.honeycomb.platform.IPMIInfo;
import com.sun.honeycomb.platform.DIMMInfo;
import com.sun.honeycomb.platform.CPUInfo;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Concrete Factory that created platform-specific information objects.
 */

class Platform implements PlatformIntf {

    private static final Logger logger =
        Logger.getLogger (Platform.class.getName());

    private static final String HC_PORTMAPPER =
        "/opt/honeycomb/bin/external_portmap.sh";
    private static final String HC_EXTERNAL_LOGGER =
        "honeycomb.cell.external_logger";
    private static final String PNAME_SWITCH_TYPE =
        "honeycomb.cell.switch_type";
    private static final String PNAME_NFS_OPTIONS =
        "honeycomb.cell.nfs.options";
    private static final String PNAME_NFS_ERR_RATE =
        "honeycomb.cell.nfs.simulate.error.rate";

    /** Cluster properties */
    private ClusterProperties config;

    /** Our hardware profile */
    private HardwareProfile profile;

    /** Object that initializes the VIPs */
    private VIPManager vipManager;

    /** Options to be used for intra-cluster NFS */
    private String nfsOptions = null;

    /** This node's ID */
    private int nodeId = -1;

    /** The NFS manager -- need a ref to satisfy RMI requests */
    NfsManager nfsmgmt = null;

    // For error simulation
    private float errorSimulationRate = 0.0f;

    static private boolean initialized = false;

    /** Hidden constructor */
    Platform() {
        if (!initialized) {
            synchronized (Platform.class) {
                config = ClusterProperties.getInstance();
            }
        }

        profile = HardwareProfile.getProfile();
        nfsOptions = config.getProperty(PNAME_NFS_OPTIONS);
        try {
            errorSimulationRate =
                Float.parseFloat(config.getProperty(PNAME_NFS_ERR_RATE));
        }
        catch (Exception ignored) {}

        nfsmgmt = new NfsManager(errorSimulationRate);
    }

    /** initialize the OS dependent part of the Platform service */
    public void initialize(List services) {

        // Make sure the VIP is setup before we start anything else
        vipManager = new VIPManager(profile);

        // The logger we want to use is set up early. If an
        // external log host is defined in the cluster config, use
        // ksyslog and forward logs to the log host; otherwise
        // configure syslog-ng.

        String logHost = config.getProperty(HC_EXTERNAL_LOGGER);


        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor (ServiceManager.LOCAL_NODE);
        if (proxy == null) {
            throw new RuntimeException("Cannot access local node proxy");
        }
        nodeId = proxy.nodeId();

        // set up all other services

        if (!System.getProperty("os.name").equals("SunOS")) {
            // On Solaris, SMF manages all services
            services.add (nfsmgmt);
            nfsmgmt.start();
        }
    }

    /**
     * Re-initialize things that change, like networking stuff
     */
    public void reInitialize() {
        vipManager.reInit();
    }

    /*
     * Reset network - Call if the dataVip changes
     */
    public void resetNetwork() {
        vipManager.resetNetwork();
    }
    

    /** Get a concrete SystemInfo implementation */
    public SystemInfo getSystemInfo() {
        return SystemInfo.getInfo();
    }

    /** Get a concrete MemoryInfo implementation */
    public MemoryInfo getMemoryInfo() {
        return MemoryInfo.getInfo();
    }

    /** Get a concrete BIOSInfo implementation */
    public BIOSInfo getBIOSInfo() {
        return BIOSInfo.getInfo();
    }

    /** Get a concrete IPMIInfo implementation */
    public IPMIInfo getIPMIInfo() {
        return IPMIInfo.getInfo();
    }

    /** Get a concrete DIMMInfo implementation */
    public DIMMInfo getDIMMInfo() {
        return DIMMInfo.getInfo();
    }

    /** Get a concrete CPUInfo implementation */
    public CPUInfo getCPUInfo() {
        return CPUInfo.getInfo();
    }

    /** Get a concrete getDataVip implementation */
    public String getDataVip() {
        return vipManager.getDataVip();
    }

    /** Get the node's network interface */
    public String getInterfaceName() {
        return vipManager.getNetworkInterface(); 
    }
  
    /** get the network interface name for the data vip */
    public String getDataVipInterface() {
        return vipManager.getDataVipInterface(); 
    }
 
    /** get the network interface name for internal master vip */
    public String getInternalMasterVipInterface() {
        return vipManager.getInternalMasterVipInterface();   
    } 

    /** get master multicell interface name */
    public String getMasterMulticellVipInterface() {
        return vipManager.getMasterMulticellVipInterface();  
    }

    /** Get the node's MAC address */
    public String getMACAddress() {
        return vipManager.getMACAddress();
    }

    /** Get a concrete getSwitchType implementation */
    public String getSwitchType() {
        String type = config.getProperty(PNAME_SWITCH_TYPE);
        if (type == null) {
            type = "other";
        }
        return type;
    }

    public boolean powerOn(int nodeid) {
        return PowerManager.powerOn(nodeid);
    }

    public boolean powerOff() {
        return PowerManager.powerOff();
    }

    /** API: NFS mount the disk */
    synchronized public boolean nfsOpen(Disk disk) {
        String mountPoint = nfsmgmt.getNFSMountPoint(disk);
        File mountPointFile = new File(mountPoint);

        // Filesystem already mounted?
        if (isDiskRoot(mountPoint)) {
            logger.info(mountPoint + " (disk " + disk.getId() +
                        ") already mounted");
            return true;
        }

        // Do not NFS mount our own disks. In other words, if "host"
        // is me, just create a symlink
        if (disk.nodeId() == nodeId) {
            if (logger.isLoggable(Level.INFO))
                logger.info("Creating symlink instead of NFS-mounting " +
                            disk.getPath());

            File f = new File(mountPoint);
            if (f.exists()) {
                if (f.isDirectory())
                    throw new InternalException("Self mount-point " + f +
                                                " is a directory");
                try {
                    DiskOps.getDiskOps().remove(mountPoint);
                } catch (Exception e) {
                    throw new InternalException("Couldn't delete " + f);
                }
            }
            else
                f.getParentFile().mkdirs();

            try {
                DiskOps.getDiskOps().link(disk.getPath(), mountPoint);
            }
            catch (IOException e) {
                logger.log(Level.WARNING,
                           "Symlink local disk " + disk.getPath(), e);
                return false;
            }

            return true;
        }

        if (!mountPointFile.exists() && !mountPointFile.mkdirs())
            throw new RuntimeException("Couldn't create " + mountPoint);

        if (nfsmgmt.mountDisk(disk.getNodeIpAddr(), disk.getPath(),
                                 mountPoint, nfsOptions)) {
            if (isDiskRoot(mountPoint)) {
                if (logger.isLoggable(Level.INFO))
                    logger.info("Disk " + disk.getId() + " NFS-mounted");
                return true;
            }

            // It's not really a disk -- unmount it quick!
            nfsmgmt.umountDisk(mountPoint);
        }

        logger.warning("Failed to NFS-mount " + disk.getId());
        return false;
    }

    /** API umount the nfs filesystem corresponding to the given disk */
    synchronized public void nfsClose(DiskId disk) {
        DiskOps ops = DiskOps.getDiskOps();

        String path = profile.getPathPrefix(profile.dataPartitionIndex()) +
            "/" + disk.diskIndex();
        String mountPoint = nfsmgmt.getNFSMountPoint(disk.nodeId(), path);

        // We don't NFS mount our own disks -- it's just a symlink.
        if (disk.nodeId() == nodeId) {
            if (logger.isLoggable(Level.INFO))
                logger.info("Deleting symlink instead of un-NFS-mounting " +
                            disk);

            try {
                if (!ops.isLink(mountPoint))
                    // This is unexpected -- don't call DiskOps.remove() on it!
                    throw new InternalException("Disk path " + mountPoint +
                                                " not a symlink!");
                ops.remove(mountPoint);
            }
            catch (IOException e) {
                logger.log(Level.WARNING,
                           "Symlink local disk " + mountPoint, e);
            }
            return;
        }

        nfsmgmt.umountDisk(mountPoint);

        if (logger.isLoggable(Level.INFO))
            logger.info("Disk " + disk + " un-NFS-mounted");
    }

    synchronized public void nfsCloseAll() {
        nfsmgmt.unmountAll();
    }

    /**
     * This is a bit of a hack: the disk initialisation code leaves a
     * human-readable version of the disk's label in its [the disk's]
     * root directory, so if we find that file, it probably really is
     * a mounted disk.
     *
     * @param dirName the name of the suspected mount-point
     */
    private boolean isDiskRoot(String dirName) {
        File f = new File(dirName + "/.disklabel");
        return f.exists();
    }

}
