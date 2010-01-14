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

final class Lofi extends HardwareProfile {

    Partition data = new Partition(0, "/data", ",", DiskOps.FS_UFS);

    Lofi(String name) {
        super(name);
    }

    public boolean useVirtualDisks() {
        return true;
    }

    public Partition getPartition(int partitionIndex) {
        if (partitionIndex >= getNumPartitions())
            return null;
        return data;
    }

    public int dataPartitionIndex() {
        // the entire disk is a data partition
        return 0;
    }

    public int getMaxDisks() {
        return 4;
    }

    public int diskType() {
        return DISKS_LOFI;
    }

    public int hostOS() {
        return OS_SOLARIS;
    }
	
    public String[] getNetworkInterfaces() {
        return new String[]{"e1000g0"};
    }

    public String[] getDataVipInterfaces() {
        // @ FIX ME - incorrect interfaces
        return new String[]{"e1000g0"};
    }
  
    public String[] getInternalMasterVipInterfaces() {
        // @ FIX ME - incorrect interfaces
        return new String[]{"e1000g0"};
    }
   
    public String[] getMasterMulticellVipInterfaces() {
        // @ FIX ME - incorrect interfaces
        return new String[]{"e1000g0"};
    } 
}
