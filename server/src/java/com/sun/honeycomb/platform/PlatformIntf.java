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

import java.util.List;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.util.sysdep.SystemInfo;
import com.sun.honeycomb.util.sysdep.MemoryInfo;
import com.sun.honeycomb.platform.BIOSInfo;
import com.sun.honeycomb.platform.IPMIInfo;
import com.sun.honeycomb.platform.DIMMInfo;
import com.sun.honeycomb.platform.CPUInfo;

/** Interface that defines all platform-specific operations.
 */
public interface PlatformIntf {
    /**
     * inialize the underlying platform implementation -
     * @param list The list of MonitoredService instances
     *             that need to be controlled by Platform.
     */
    public void initialize(List list);

    /** make the given disk accessible locally */
    public boolean nfsOpen(Disk disk);

    /** close the given disk when it is not used */
    public void nfsClose(DiskId disk);

    /** unmount all NFS mounts */
    public void nfsCloseAll();

    /** get the data vip for this node */
    public String getDataVip();

    /** get the network interface name for the platform */
    public String getInterfaceName();

    /** get the network interface name for the data vip */
    public String getDataVipInterface();

    /** get the network interface name for internal master vip */
    public String getInternalMasterVipInterface();

    /** get master multicell interface name */
    public String getMasterMulticellVipInterface();
    
    /** get the switch type for this node */
    public String getSwitchType();

    /** get the MAC address for this node */
    public String getMACAddress();

    /** re-init any cached network info */
    public void reInitialize();

    /** Reset network - Call if the dataVip changes */
    public void resetNetwork();

    /** power off the node */
    public boolean powerOff();

    /** power on the given node */
    public boolean powerOn(int nodeid);

    /** return system info for this node */
    public SystemInfo getSystemInfo();

    /** return memory info for this node */
    public MemoryInfo getMemoryInfo();

    /** return bios info for this node */
    public BIOSInfo getBIOSInfo();

    /** return ipmi info for this node */
    public IPMIInfo getIPMIInfo();

    /** return dimm info for this node */
    public DIMMInfo getDIMMInfo();

    /** return cpu info for this node */
    public CPUInfo getCPUInfo();

}
