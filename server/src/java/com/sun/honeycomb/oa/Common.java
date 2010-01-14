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



package com.sun.honeycomb.oa;

import java.util.logging.Logger;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;


/**
 * This class encapsulates operations to manipulate FragmentFile names on local
 * disks. It knows how to translate a OID, fragId and Disk to a pathname
 * on the local filesystem and vice versa.
 * NOTE: this is the only class in honeycomb that knows an object is stored
 * on a filesystem and hence has a path. This eventually needs to be merged
 * within the DAAL interface.
 */
public class Common {

    /*
     * Convert mapId to a directory path string,
     * for example /data/0/01/23
     */
    public static String mapIdToDir(int mapId, DiskId diskId) {

        String diskPath = diskIdToDir(diskId);
        String firstLevel = String.valueOf(mapId / NUM_DIRS);
        if (firstLevel.length() == 1) {
            firstLevel = "0" + firstLevel;
        }

        String secondLevel = String.valueOf(mapId % NUM_DIRS);
        if (secondLevel.length() == 1) {
            secondLevel = "0" + secondLevel;
        }

        String d = diskPath + dirSep + firstLevel + dirSep + secondLevel;
        return d;
    }

    /*
     * convert mapId to a tmp-close directory path string,
     * for example /data/0/tmp-close
     */
    public static String mapIdToTmpDir(DiskId diskId) {
        String d = diskIdToDir(diskId) + dirSep + closeDir;
        return d;
    }

    /*
     * Convert an <OID, Disk, FragNum> to a fragment filename.
     */
    public static String makeFragmentName(NewObjectIdentifier oid, int fragNum)
    {
        return oid.toString() + fragNumSep + fragNum;
    }

    /*
     * Convert an <OID, Disk, Fragnum> to a filename path on the local
     * filesystem in persistent storage.
     */
    public static String makeFilename(NewObjectIdentifier oid, Disk disk, int fragNum)
    {
        return makeDir(oid, disk) + dirSep +  makeFragmentName(oid, fragNum);
    }

    /*
     * Convert an <OID, Disk, FragNum> to a filename path on the local
     * filesystem in temporary storage.
     */
    public static String makeTmpFilename(NewObjectIdentifier oid, Disk disk, int fragNum)
    {
        return makeTmpDirName(disk) + dirSep + makeFragmentName(oid, fragNum);
    }

    /*
     * Convert the OID to a directory path of where it should be stored.
     */
    public static String getStoreDir(NewObjectIdentifier oid) {
        String layoutMapId = new Integer(oid.getLayoutMapId()).toString();
        while (layoutMapId.length() < 4) {
            layoutMapId = '0' + layoutMapId;
        }
        return dirSep + layoutMapId.substring(0,2) +
               dirSep + layoutMapId.substring(2,4);
    }

    /*
     * Extract the OID encoded in the filename
     */
    public static NewObjectIdentifier extractOIDFromFilename(String filename) {
        String[] splitStrs = filename.split("_", 2);
        if(splitStrs.length < 2) {
            return NewObjectIdentifier.NULL;
        }
        NewObjectIdentifier result = NewObjectIdentifier.NULL;
        try {
            result = new NewObjectIdentifier(splitStrs[0]);
        } catch (IllegalArgumentException iae) {
            return NewObjectIdentifier.NULL;
        }
        return result;
    }

    /*
     * Extract the FragNum encoded in the filename
     */
    public static int extractFragNumFromFilename(String filename) {
        String[] splitStrs = filename.split("_", 2);
        if(splitStrs.length < 2) {
            return -1;
        }
        int result = -1;
        try {
            result = Integer.parseInt(splitStrs[1]);
        } catch(NumberFormatException nfe) {
            result = -1;
        }
        return result;
    }

    /*
     * Convert an <OID, Disk> to a directory path
     */
    public static String makeDir(NewObjectIdentifier oid, Disk disk) {
        return makeDir(new Integer(oid.getLayoutMapId()).toString(),
                       disk.getPath());
    }

    /*
     * Convert an <LayoutID, Disk path> to a directory path
     */
    public static String makeDir(String layoutMapId, String diskPath) {
        while (layoutMapId.length() < 4) {
            layoutMapId = '0' + layoutMapId;
        }
        String path =  diskPath + dirSep +
            layoutMapId.substring(0,2) + dirSep +
            layoutMapId.substring(2,4);
        return path;
    }

    /*
     * Return the dir path to store temporary fragments on the given disk
     */
    public static String makeTmpDirName(Disk disk) {
        return disk.getPath() + dirSep + closeDir;
    }


    // PRIVATE MEMBERS //

    /*
     * convert diskIndex to a local directory path string, example
     *          /data/0
     */
    private static String diskIdToDir(DiskId diskId) {
        return dirSep + DATA_DIR + dirSep + diskId.diskIndex();
    }


    // CONSTANTS //

    private static final Logger LOG = Logger.getLogger(Common.class.getName());

    private static final String DATA_DIR = "data"; // FIXME - should be from Disk.getPath()
    public static final int NUM_DIRS = 100;
    public static final int NUM_MAPS = (NUM_DIRS * NUM_DIRS);

    public static final String dirSep = "/";
    public static final String fragNumSep = "_";
    public static final String closeDir = "tmp-close";
}
