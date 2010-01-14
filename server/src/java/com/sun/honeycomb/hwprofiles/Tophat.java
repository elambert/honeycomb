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


//import java.util.logging.Logger;
//import java.util.logging.Level;

final class Tophat extends HardwareProfile {

    private Partition partitions[];

    //private static final Logger logger =
    //    Logger.getLogger(Tophat.class.getName());

    Tophat(String name) {
	super(name);

        partitions = new Partition[getNumPartitions()];

        // Partition command:
        //     tag, start cylinder, size in cylinders (1c ~ 8MB)

        partitions[0] = new Partition
            (0,  null,    "root,,128",   DiskOps.FS_NONE);
        partitions[1] = new Partition
            (1,  null,    "root,,128",   DiskOps.FS_NONE);
        partitions[2] = new Partition
            (3,  null,    "root,,128",   DiskOps.FS_NONE);
        partitions[3] = new Partition
            (5,  null,    "root,,128",   DiskOps.FS_NONE);
        partitions[4] = new Partition
            (4,  "/data", "home,,",      DiskOps.FS_UFS);
    }

    public int getNumPartitions() {
        return 5;
    }

    public Partition getPartition(int partitionIndex) {
        if (partitionIndex >= getNumPartitions())
            return null;

        return partitions[partitionIndex];
    }

    public int dataPartitionIndex() {
        return 4;
    }

    public int getMaxDisks() {
        return 4;
    }

    public int hostOS() {
        return OS_SOLARIS;
    }

    public int diskType() {
        return DISKS_LUX;
    }

    public String[] getNetworkInterfaces() {
        return new String[]{"bge0", "bge1"};
    }

    public String[] getDataVipInterfaces() {
        // @ FIX ME - incorrect interfaces
        return new String[]{"bge0", "bge1"};
    }

    public String[] getInternalMasterVipInterfaces() {
        // @ FIX ME - incorrect interfaces
        return new String[]{"bge0", "bge1"};
    }

    public String[] getMasterMulticellVipInterfaces() {
        // @ FIX ME - incorrect interfaces
        return new String[]{"bge0", "bge1"};
    }

    public int getActiveInterface() {
        String[] interfaceNames = getNetworkInterfaces();
        int sw = -1;
        
        for (int i = 0; i < interfaceNames.length; i++) {
            if (interfaceFailed(interfaceNames[i]))
                continue;
            if (sw != -1) {
                // There can't be more than one non-failed interface!
                String msg = "Both interfaces \"" + interfaceNames[sw] +
                    "\" and \"" + interfaceNames[i] + "\" active?";
                throw new RuntimeException(msg);
            }
            sw = i;
        }
        if (sw < 0)
            throw new RuntimeException("No active interfaces!");
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

        // The kstat for an interface has its current speed, which for
        // an inactive interface is 0.

        Kstat ks = Kstat.get(moduleName, instance, ifname);
        Object ifspeed = ks.getStat("ifspeed");
        if (ifspeed == null || !(ifspeed instanceof Long))
            throw new RuntimeException("kstat " + ifname + " failed");

        long speed = ((Long)ifspeed).longValue();
        return speed == 0;
    }

}
