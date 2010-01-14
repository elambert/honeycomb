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



package com.sun.honeycomb.hctest.cases;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.cli.DataDoctorState;
import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import java.util.*;

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.common.OIDFormat;
import com.sun.honeycomb.layout.Layout;

public class HealingBasic extends HoneycombLocalSuite {

    private ClusterMembership cm = null;
    private String clusterIP = null;
    private boolean verbose = false;
    private DataDoctorState dd = null;

    // supported test modes
    private static int RS_RECOVERY = 1;
    private static int HEALBACK_COPY = 2;
    private static boolean ALL_SIZES = false;

    // Test data object sizes.  We do these tests on different sizes to do basic
    // validatation of healing. 
    // See also bug 6538878.
    // TODO: multichunk
    //
    private static long SMALL_SIZE = 100;
    private static long MEDIUM_SIZE = 1024*1024; // 1MB

    // These sizes come from FragmentLevelTests (boundary sizes),
    // plus trigger size for bug 6655693 (~3MB) and 6538878 (~14MB),
    // plus sizes around 7MB (7 frags x 1MB),
    // plus some additional power-of-two sizes,
    // incl. boundary of CACHE_READ_SIZE in FragmentFile.java (64k) 
    // This set ranges from zero bytes to about 14MB.
    //
    private long[] sizes = {0, 1, 2, 7, 8, 32, 64, 100, 
                            254, 255, 256, 257, 500, 510, 511, 512, 513, 
                            1000, 1022, 1023, 1024, 1025, 1026, 1500,
                            7167, 7168, 7169, 7170, 8192,
                            8800, 8801, 8869, 8870, 8871, 8872,
                            10498, 10499, 10500, 10501, 10502,
                            32768, 65535, 65536, 65537, 98304, 
                            131071, 131072, 131073, 163840, 
                            196607, 196608, 196609, 
                            327679, 327680, 327681, 491520, 
                            655359, 655360, 655361, 819200, 
                            983039, 983040, 983041,
                            1048574, 1048575, 1048576, 1048577, 1048578,
                            3103118, 3103119, 3103120, 3103121, 3103122,
                            7340030, 7340031, 7340032, 7340033, 7340034,
                            15278717, 15278718, 15278719
    };

    private String[] tags = {Tag.REGRESSION,
                             HoneycombTag.DATA_DOCTOR,
                             HoneycombTag.HEALING};

    public HealingBasic() {
        super();
    }

    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tTests basic healing by removing fragments and forcing datadoctor to run recovery on given layout map \n");
        sb.append("\tAcceptable for regression suite because it's fast and doesn't offline any disks/nodes \n");
        return sb.toString();
    }

    public void setUp() throws Throwable {
        super.setUp();

        if (!Run.isTagSetActive(tags)) {
            return;
        }

        String allFilesizesProperty = getProperty(HoneycombTestConstants.PROPERTY_ALLFILESIZES);
        if (null != allFilesizesProperty) {
            ALL_SIZES = true;
            Log.INFO("Will test complete set of object sizes");
        }

        TestBed b = TestBed.getInstance();
        if (b != null) {
            clusterIP = b.adminVIP;
        } else {
            throw new HoneycombTestException("Unable to get adminVIP.");
        }

        dd = DataDoctorState.getInstance();
        cm = new ClusterMembership(-1, clusterIP);
        try {
            // check that cluster is online and CMM is consistent
            cm.setQuorum(true);
            cm.initClusterState();
        } catch (HoneycombTestException e) {
            Log.ERROR("Failed to intialized ClusterMembership: " + e.getMessage());
            throw new HoneycombTestException("Cluster must be in bad state, not continuing");
        }
    }

    public void testSingleMapRecovery() throws HoneycombTestException {
        if (ALL_SIZES) {
            for (int i = 0; i < sizes.length; i++) {
                runHealingTest(RS_RECOVERY, sizes[i]);
            }
        } else {
            runHealingTest(RS_RECOVERY, SMALL_SIZE);
            runHealingTest(RS_RECOVERY, MEDIUM_SIZE);
        }
    }

    public void testSingleFragHealBack() throws HoneycombTestException {
        if (ALL_SIZES) {
            for (int i = 0; i < sizes.length; i++) {
                runHealingTest(HEALBACK_COPY, sizes[i]);
            }
        } else {
            runHealingTest(HEALBACK_COPY, SMALL_SIZE);
            runHealingTest(HEALBACK_COPY, MEDIUM_SIZE);
        }
    }

    public void runHealingTest(int healMode, long size) 
                        throws HoneycombTestException {
        
        TestCase self; 
        if (healMode == RS_RECOVERY) {
            self = createTestCase("Healing", "SingleMapFragRecovery-" + size);
        } else if (healMode == HEALBACK_COPY) {
            self = createTestCase("Healing", "SingleFragHealBack-" + size);
        } else {
            throw new RuntimeException("Unsupported test mode: " + healMode);
        }
        self.addTag(tags);
        if (self.excludeCase()) return;

        // store object of given size (TODO multichunk)
        CmdResult stored = null;
        try {
            stored = store(size);
        } catch (HoneycombTestException e) {
            self.testFailed("Store failed: " + e.getMessage());
            return;
        }

        // on each disk where this OID has a fragment:
        // remove (hide) the fragment, (copy elsewhere if testing healback),
        // run targeted recovery,
        // verify that healed fragment matches the original.

        int FRAGS = 7;
        int chunkId = 0; // XXX TODO multichunk

        for (int fragId = 0; fragId < FRAGS; fragId++) {

            Log.SUM("Iteration " + fragId);

            if (healMode == HEALBACK_COPY) {
                Log.INFO("Healback mode: copy frag to backup disks before removing it");
                dd.makeOneRemoteFragBackup(stored.dataoid, fragId, chunkId);
            }

            Log.INFO("Deleting (hiding) frag " + fragId + " of oid " + stored.dataoid);
            dd.delete(stored.dataoid, fragId, chunkId);

            Log.INFO("Running recovery");
            boolean healOK = false;
            try {
                // after recovery, verify that healed fragment matches original
                boolean verify = true; 
                healOK = dd.dataDoctorTargetedHeal(stored.dataoid, fragId, chunkId, 
                                          CLIState.LOST_FRAGS, verbose, verify);
            } catch (Throwable t) {
                self.testFailed("Failed to do DD targeted heal on oid " + 
                                stored.dataoid + " frag " + fragId + " chunk " + chunkId);
                return;
            }
            if (!healOK) {
                self.testFailed("Verification after healing failed on oid " +
                                stored.dataoid + " frag " + fragId + " chunk " + chunkId);
                return;
            }

            if (healMode == HEALBACK_COPY) {
                Log.INFO("Healback mode: cleanup extra copied frags");
                dd.deleteOneRemoteFragBackup(stored.dataoid, fragId, chunkId);
            }

            fragId++; // moving on to next frag on next node:disk
        }

        // retrieve data, verify
        CmdResult retrieved = null;
        try {
            retrieved = retrieve(stored.mdoid);
        } catch (HoneycombTestException e) {
            self.testFailed("Retrieve failed on oid " + stored.mdoid + ": " + e.getMessage());
            return;
        }

        self.testPassed();
    }
}
