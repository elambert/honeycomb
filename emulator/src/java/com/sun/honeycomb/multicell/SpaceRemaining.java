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



package com.sun.honeycomb.multicell;

import com.sun.honeycomb.config.ClusterProperties;
import java.io.File;
import com.sun.honeycomb.cm.NodeMgr;

/**
 * Tracks simulated used space in the emulator.
 * emulator uses one jvm, so this works dandy
 */
public class SpaceRemaining {

    private static SpaceRemaining _instance = null;
    private long dataBytes;
    private boolean calcMD;
    private long mdBytes;
    private long totalCapacityBytes;

    /**
     * The getter for this singleton class
     */
    public static synchronized SpaceRemaining getInstance() {
        if (_instance == null) {
            _instance = new SpaceRemaining();
        }
        return(_instance);

    }

    private SpaceRemaining() {

        calculateTotalCapacityBytes();

        calcDataBytes();
        calcMD = true;
        calcMDBytes();
    }

    /**
     * Total simulated capacity, in bytes
     * @return long Total simulated capacity, in bytes
     */
    public long getTotalCapacityBytes() {
        return totalCapacityBytes;
    }

    /**
     * Used simulated capacity, in bytes
     * @return long Used simulated capacity, in bytes
     */
    public long getUsedCapacityBytes() {
        calcMDBytes();
        return dataBytes + mdBytes;
    }

    /**
     *  Invoke on store - increments internal data byte count and sets flag to
     *  recalc md bytes.
     */
    public void addBytes(long byteCount) {
        dataBytes += byteCount;
        calcMD = true;
    }

    /**
     * Returns total capacity as specified in the emulator 
     * config file honeycomb.cell.capacity.megs.
     * Defaults to 500 megs if undefined.
     * @returns long total simulated cell capacity in bytes
     */
    private void calculateTotalCapacityBytes() {
        totalCapacityBytes = ClusterProperties.getInstance().
            getPropertyAsLong("honeycomb.cell.capacity.megs", 500);
        totalCapacityBytes *= 1024 * 1024;
    }

    /**
     * Explores the data directory to return
     * used capacity in bytes. Platform independent
     * @returns long total used data in bytes
     */
    private void calcDataBytes() {
        String root = NodeMgr.getInstance().getEmulatorRoot();

        String path = root + File.separatorChar + "var" +
                             File.separatorChar + "data";
        File dir = new File(path);
        dataBytes = sumFileSizes(dir);
    }

    /**
     * Explores the metadata directory to return
     * used capacity in bytes. Platform independent
     * @returns long total used metadata in bytes
     */
    private void calcMDBytes() {

        if (!calcMD)
            return;

        String root = NodeMgr.getInstance().getEmulatorRoot();

        String path = root + File.separatorChar + "var" +
                             File.separatorChar + "metadata";
        File dir = new File(path);
        mdBytes = sumFileSizes(dir);

        calcMD = false;
    }

    /**
     * Sums all the files and directories recursively.
     * Exits if null directory is passed.
     * Returns 0 if there aren't any contents.
     * @returns long total bytes used by files and directories.
     */
    private long sumFileSizes(File dir) {

       if (dir == null) {
           System.err.println("sumFileSizes: null dir passed");
           System.exit(1);
       }

       long usedCapacity = dir.length();

       File[] files = dir.listFiles();
       if (files == null  ||  files.length == 0) {
           return 0;
       }
       for (int i = 0; i < files.length; i++) {
           usedCapacity += files[i].length(); 
           if (files[i].isDirectory())
               usedCapacity += sumFileSizes(files[i]);
       }
       return usedCapacity;
    }

}
