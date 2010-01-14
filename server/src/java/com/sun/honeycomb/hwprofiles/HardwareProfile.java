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

import com.sun.honeycomb.config.ClusterProperties;
import java.util.Properties;

public abstract class HardwareProfile implements java.io.Serializable {

    static final String AQUARIUS = "aquarius";
    static final String TOPHAT = "tophat";
    static final String HON = "hon";
    static final String LOFI = "lofi";

    static final String PNAME_HWPROFILE = "honeycomb.hardware.profile";

    public static final int OS_LINUX   = 0;
    public static final int OS_SOLARIS = 1;

    public static final int DISKS_IDE  = 0;
    public static final int DISKS_SATA = 1;
    public static final int DISKS_SCSI = 2;
    public static final int DISKS_LUX  = 3;
    public static final int DISKS_LOFI = 4;

    private String profileName = null;

    private static String theProfileName = null;
    private static HardwareProfile theProfile = null;

    //////////////////////////////////////////////////////////////////
    // Factory method
    public static HardwareProfile getProfile(String profile) {

        if (theProfileName == null || !theProfileName.equals(profile) ||
                theProfile == null) {

            theProfile = getNewProfile(profile);
            theProfileName = profile;
        }

        return theProfile;
    }

    // Convenience functions
    public static HardwareProfile getProfile(Properties config) {
        return setProfileName(config.getProperty(PNAME_HWPROFILE));
    }

    public static HardwareProfile getProfile(ClusterProperties config) {
        return setProfileName(config.getProperty(PNAME_HWPROFILE));
    }

    private static HardwareProfile setProfileName(String profileName) {
        if (profileName == null)
            throw new Error("Cluster config doesn't have \"" +
                            PNAME_HWPROFILE + "\"");

        HardwareProfile profile = HardwareProfile.getProfile(profileName);
        if (profile == null)
            throw new Error("Hardware profile = \"" + profileName +
                            "\" unknown");
        return profile;
    }

    public static HardwareProfile getProfile() {
        if (theProfile == null) {
            ClusterProperties config = ClusterProperties.getInstance();
            getProfile(config);
        }
        return theProfile;
    }

    private static HardwareProfile getNewProfile(String profile) {
        String prof = profile.toLowerCase();

        // Wouldn't it be nice if instead of this sequence of string
        // comparisons we used the java class loader? The adding a new
        // profile just means copying a pre-existing profile and
        // modifying it -- no other changes required.

        if (prof.equalsIgnoreCase(LOFI))
            return new Lofi(prof);

        else if (prof.equalsIgnoreCase(HON))
            return new Hon(prof);

        else if (prof.equalsIgnoreCase(TOPHAT))
        return new Tophat(prof);

        else if (prof.equalsIgnoreCase(AQUARIUS))
        return new Aquarius(prof);

        else
            throw new IllegalArgumentException("Unknown profile \"" +
                                               profile + "\"");
    }

    //////////////////////////////////////////////////////////////////
    // Bookkeeping

    HardwareProfile(String name) {
    profileName = name;
    }

    public String name() {
    return profileName;
    }

    //////////////////////////////////////////////////////////////////
    // System stuff

    public abstract int hostOS();

    public abstract int diskType();

    public abstract String[] getNetworkInterfaces();

    public abstract String[] getDataVipInterfaces();

    public abstract String[] getInternalMasterVipInterfaces();

    public abstract String[] getMasterMulticellVipInterfaces();
 
    //////////////////////////////////////////////////////////////////
    // Disk related stuff

    public abstract int getMaxDisks();

    // Partitions on each disk

    public abstract int dataPartitionIndex();

    public String getPartitionId(int partitionIndex) {
    return Integer.toString(getPartition(partitionIndex).getNumber());
    }

    /**
     * A "partition descriptor" has all the information required to
     * write a partition to disk. The first value is the partition id,
     * followed by size/pos data. On Solaris, it consists of three
     * comma-separated values: the first is the "tag"; the second is
     * the start, and the third is the size. On Linux there is no
     * "tag" hence there are only two values. Defaults are allowed: a
     * missing "start" means whatever cylinder we're currently at
     * (while stepping through the partition table) and a missing size
     * means the rest of the disk. On Linux, the units are megabytes;
     * on Solaris they're cylinders. (All this because of differences
     * between sfdisk on Linux and format on Solaris.)
     */
    public String getPartitionDesc(int partitionIndex) {
        Partition p = getPartition(partitionIndex);
        if (p == null)
            return null;
    return p.getNumber() + ":" + p.getPartitionCommand();
    }

    public int getPartitionType(int partitionIndex) {
    return getPartition(partitionIndex).getType();
    }

    public String getPathPrefix(int partitionIndex) {
    return getPartition(partitionIndex).getPathPrefix();
    }

    //////////////////////////////////////////////////////////////////
    // Default implementations; profiles usually override these

    public int getNumPartitions() {
        return 1;
    }

    public int getNumDisks() {
        return 4;
    }

    public boolean useVirtualDisks() {
        return false;
    }

    public boolean useSMART() {
    return false;
    }

    public String diskDriverName() {
        return "sd";
    }

    public int getActiveInterface() {
        if (hostOS() == OS_SOLARIS)
            return -1;          // Must override
        else if (hostOS() == OS_LINUX)
            return 0;           // Linux systems only have one interface
        else
            return -1;
    }

    /**
     * Return the vlan id that the data vip lives on
     */
    public int getDataVlanId() {
        return 1;
    }

    /**
     * Some platforms -- e.g. Rig1U -- have non-sequential disk IDs.
     * This maps them to sequential.
     */
    public int diskIndex(int diskId) {
        return diskId;
    }


    ////////////////////////////////////////////////////////////////
    // Not for public consumption!

    abstract Partition getPartition(int partitionIndex);
}
