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



package com.sun.honeycomb.oa.upgrade;

import com.sun.honeycomb.common.SoftwareVersion;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.oa.Common;

import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;

public class Upgrader {
    private static Logger log
        = Logger.getLogger(Upgrader.class.getName());

    public static final String VERSION_1_0 = "1.0";
    public static final String VERSION_1_1 = "1.1";
    public static final String CURRENT_VERSION = VERSION_1_1;
    private static final String VERSION_FILE = "VERSION";

    private static String hcVersion = SoftwareVersion.getConfigVersion();

    /**********************************************************************/
    private static File getVersionFile(Disk disk) {
        return new File(disk.getPath() + "/" + VERSION_FILE);
    }

    /**********************************************************************/
    private static String readMajorVersion(Disk disk) {
        File file = getVersionFile(disk);
        if (!file.exists()) {
            return VERSION_1_0;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String versionStr = br.readLine();
            if (versionStr == null) {
                return VERSION_1_0;
            }
            return versionStr.substring(0, 3);
        } catch (IOException e) {
            throw new InternalException(e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) { // ignore
                }
            }
        }
    }

    /**********************************************************************/
    public static boolean upgradeable(Disk disk) {
        log.info("Checking " + disk.getPath());
        if ((hcVersion == null)
            || (!hcVersion.substring(0, 3).equals(CURRENT_VERSION))) {
            log.info("Config version does not need upgrade");
            updateVersionFile(disk);
            return false;
        }
        String version = readMajorVersion(disk);
        if (version.equals(CURRENT_VERSION)) {
            log.info("Disk " + disk.getPath() + " is already upgraded to "
                     + CURRENT_VERSION);
            updateVersionFile(disk);
            return false;
        }
        if (!version.equals(VERSION_1_0)) {
            // Corrupted version file?
            String msg = "Unknown version " + version + " on Disk "
                + disk.getPath();
            log.severe(msg);
            if (!getVersionFile(disk).delete()) {
                log.severe("Failed to delete version file on "
                           + disk.getPath());
            }
        }
        log.info("Attempting to upgrade " + disk.getPath());
        return true;
    }

    /**********************************************************************/
    static void updateVersionFile(Disk disk) {
        try {
            File tmpFile = new File(Common.makeTmpDirName(disk) + "/"
                                    + VERSION_FILE);
            FileWriter out = new FileWriter(tmpFile);
            out.write(hcVersion);
            out.close();
            if (!tmpFile.renameTo(getVersionFile(disk))) {
                log.severe("Failed to rename " + tmpFile.getPath());
            }
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "Failed to write version file: ", ioe);
        }
    }

    /**********************************************************************/
    public void upgrade(Disk disk) {
        if (!upgradeable(disk)) {
            return;
        }
        DiskUpgrader du = new DiskUpgrader(disk);
        du.start();
        try {
            du.join();
        } catch (InterruptedException ie) {
            log.severe("Interrupted " + du + ", " + ie.getMessage());
        }
        try {
            du.checkException();
        } catch (UpgraderException ue) {
            log.log(Level.SEVERE, ue.getMessage(), ue);
        }
    }

    /**********************************************************************/
    public void upgrade(List disks) {
        List diskUpgraders = new LinkedList();
        for (Iterator it = disks.iterator(); it.hasNext(); ) {
            Disk disk = (Disk) it.next();
            if (upgradeable(disk)) {
                DiskUpgrader du = new DiskUpgrader(disk);
                diskUpgraders.add(du);
                du.start();
            }
        }
        for (Iterator it = diskUpgraders.iterator(); it.hasNext(); ) {
            DiskUpgrader du = (DiskUpgrader) it.next();
            try {
                du.join();
            } catch (InterruptedException ie) {
                log.severe("Interrupted " + du + ", " + ie.getMessage());
            }
        }
        for (Iterator it = diskUpgraders.iterator(); it.hasNext(); ) {
            DiskUpgrader du = (DiskUpgrader) it.next();
            try {
                du.checkException();
            } catch (UpgraderException ue) {
                log.log(Level.SEVERE, ue.getMessage(), ue);
            }
        }
    }

    /**********************************************************************/
    public static void main(String[] args) {
        Upgrader upgrader = new Upgrader();
        int nodeId = 0;
        try {
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            nodeId = Integer.parseInt(ipAddress.substring(10)); // nuke 10.123.45.
        } catch (UnknownHostException uhe) {
            log.log(Level.SEVERE, uhe.getMessage(), uhe);
            System.exit(1);
        }
        HardwareProfile hwProfile
            = HardwareProfile.getProfile("aquarius");
        List disks = new LinkedList();
        String path = hwProfile.getPathPrefix(hwProfile.dataPartitionIndex()) + "/";
        if ((args == null) || (args.length == 0)) {
            for (int i = 0; i < hwProfile.getMaxDisks(); i++) {
                disks.add(new Disk(new DiskId(nodeId, i), "foo", path + i, "bar",
                                   0, 0, 0, 0, 0, 0, 0, false));
            }
        } else {
            for (int i = 0; i < args.length; i++) {
                int diskId = 0;
                try {
                    diskId = Integer.parseInt(args[i]);
                } catch (NumberFormatException nfe) {
                    log.log(Level.SEVERE, nfe.getMessage(), nfe);
                    System.exit(2);
                }
                disks.add(new Disk(new DiskId(nodeId, diskId), "foo", path + diskId, "bar",
                                   0, 0, 0, 0, 0, 0, 0, false));
            }
        }
        upgrader.upgrade(disks);
    }
}
