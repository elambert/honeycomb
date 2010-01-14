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



package com.sun.honeycomb.hwprofiles;

import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.util.Kstat;
import java.text.MessageFormat;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ResourceBundle;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;

final class Aquarius extends HardwareProfile {
    
    private Partition partitions[];

    private static final Logger logger =
        Logger.getLogger(Aquarius.class.getName());

    // The "name" value passed to kstat.
    private  final String KSTAT_IF_KSNAME = "mii";

    Aquarius(String name) {
		super(name);

        partitions = new Partition[getNumPartitions()];

        // Partition command:
        //     tag, start cylinder, size in cylinders (1c ~ 8MB)

	// EXPECTED from prtvtoc:
	// 0:unsd,69,1270 2:bkup,0,60560 3:unsd,1339,127 4:unsd,1466,56552 5:unsd,58018,2540 7:unsd,60558,2
	
	// we ignore 6(swap), and 8(boot) and 9(alternates) b/c prtvtoc flags them 01, and 
	// our DiskOps getParitionTable code ignores read-only and unmountable slices
	// we also ignore 2 backup 
	
	partitions[0] = new Partition // unassigned-broot
            (0,   null, "root,69,1270",   DiskOps.FS_NONE);
	partitions[1] = new Partition // bkup
	    (2,   null, "unsd,0,60560",   DiskOps.FS_UFS);
        partitions[2] = new Partition // unassigned-config
            (3,   null, "unsd,1339,127",   DiskOps.FS_UFS);
        partitions[3] = new Partition // unassigned-data
            (4,   "/data","home,1466,56552",   DiskOps.FS_UFS);
        partitions[4] = new Partition // unassigned-log
            (5,   null, "unsd,58018,2540",      DiskOps.FS_UFS);
	partitions[5] = new Partition // unassigned---???
            (7,   null, "unsd,60558,2",      DiskOps.FS_NONE); // don't know real size
	// we ignore 6(swap), and 8(boot) and 9(alternates b/c prtvtoc flags them 01, and 
	// our DiskOps getParitionTable code ignores read-only and unmountable slices
	// we also ignore 2 backup, 

    }

    public int getNumDisks() {
        return 4;
    }

    public int getNumPartitions() {
        return 6;
    }

    public Partition getPartition(int partitionIndex) {
        if (partitionIndex >= getNumPartitions())
            return null;

        return partitions[partitionIndex];
    }

    public int dataPartitionIndex() {
        return 3;
    }

    public int diskType() {
        return DISKS_SATA;
    }

    public int hostOS() {
        return OS_SOLARIS;
    }

    public int getMaxDisks() {
        return 4;
    }

    public String diskDriverName() {
        return "sd";
    }

    public String[] getNetworkInterfaces() {
        return new String[]{"bge0", "nge0"};
    }

    public String[] getDataVipInterfaces() {
        return new String[]{"bge1000:3", "nge1000:3"}; 
    }
    
    public String[] getInternalMasterVipInterfaces() {
        return new String[]{"bge0:2", "nge0:2"}; 
    }

    public String[] getMasterMulticellVipInterfaces() {
        return new String[]{"bge3000:2", "nge3000:2"};
    }

    public int getActiveInterface() {
        // It looks like we can have more than one apparently active
        // interface when this is called in the failover case.
        // This may be due to the state changing as we're calling
        // interfaceFailed() on each.  If we see that there's more than
        // interface active, we try again.
        
        int active = -1;

        for (int i = 0; i < 5; i++) {
            try {
                active = getActiveInterfaceMeat();
            } catch (RuntimeException e) {
                logger.info("getActiveInterface iteration " + i +
                    " exception " + e);
            }
        }

        return active;
    }

    private int getActiveInterfaceMeat() {
        String[] interfaceNames = getNetworkInterfaces();
        int sw = -1;

        for (int i = 0; i < interfaceNames.length; i++) {
            if (interfaceFailed(interfaceNames[i]))
                continue;
            if (sw != -1) {
                // There can't be more than one non-failed interface!
                ResourceBundle rs = BundleAccess.getInstance().getBundle();
                String str = rs.getString("err.hwProfiles.Aquarius.bothintfactive");
                Object [] args = { new String(interfaceNames[sw]),
                                   new String(interfaceNames[i])
                                 };
                logger.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));
                throw new RuntimeException(MessageFormat.format(str, args));
            }
            sw = i;
        }
        return sw;
    }

    private boolean interfaceFailed(String ifname) {
        // First, find trailing numeric part to separate ifname into
        // module name and instance

        int i = ifname.length();
        while (i > 0 && Character.isDigit(ifname.charAt(i - 1)))
            i--;

        String moduleName = ifname.substring(0, i);
        int instance = Integer.parseInt(ifname.substring(i));

        // Get the link_up value for the interface to determine
        // if the link on the interface is up. The "name"
        // parameter to get link_up is "mii"
        Kstat ks = Kstat.get(moduleName, instance, KSTAT_IF_KSNAME);
	if (ks == null) {
	    // Interface does not exist
            return(true);
	}

        Object link_up = ks.getStat("link_up");
        if (link_up == null || !(link_up instanceof Long))
            throw new RuntimeException("kstat " + ifname + " failed");

        long link = ((Long)link_up).longValue();
        return link == 0;
    }
}
