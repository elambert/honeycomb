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


import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;

import com.sun.honeycomb.hctest.suitcase.OutputReader;
import com.sun.honeycomb.hctest.cases.interfaces.HCFileSizeCases;
import com.sun.honeycomb.hctest.cli.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.TestNVOA;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.protocol.server.ProtocolService;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutConfig;

import java.lang.Runtime;
import java.nio.channels.ReadableByteChannel;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

/*
 * This test triggers a config update via the CLI:
 *   syscfg recover_lost_frags_cycle 70000
 * It reads back the value via CLI, then verifies that all config files
 * on cluster nodes have the new value, and also greps the log
 * for property change callback messages.
 *
 * TODO: right now this depends on error message parsing. that's lame.
 *       We need a module ala daria's stuff that actually plugs in 
 *       and can report configuration changes in a format that we're
 *       expecting. See ConfigurationUpdateTest twiki page.
 */

public class ConfigurationUpdate extends HoneycombLocalSuite{
    
    int numNodes;
    int iterations;
    TestCase testCase = null;
    String clusterIP = null;

    String[] tags = {Tag.POSITIVE,
                     HoneycombTag.CONFIG,
                     HoneycombTag.POSITIVE_CONFIG,
                     Tag.REGRESSION};

    public ConfigurationUpdate() {
        // DO NOT DO ACTUAL WORK HERE!
        // Constructor runs even if the test will be skipped.
        // Therefore, any real actions must be taken in setUp()
        // after the check for isTagSetActive, or in the test methods
        // after the check for excludeCase.
    }

    public void setUp() throws Throwable {
            
        if (!Run.isTagSetActive(tags)) {
            // Test will be skipped, don't do any setup
            return;
        }

        super.setUp();

        // Get the admin VIP
        TestBed b = TestBed.getInstance();
        if (b != null) {
            clusterIP = b.adminVIP;
        } else {
            throw new HoneycombTestException("Unable to get adminVIP.");
        }

        /* Non-obvious: this call talks to HC CLI to get syscfg output.
         */
        Log.INFO("setUp: getting DataDoctorState");
        DataDoctorState state=DataDoctorState.getInstance();
        Log.INFO("got DataDoctorState");

        /* Figure out how many nodes in the cluster
         */

        ClusterMembership cm = new ClusterMembership(-1, clusterIP);
        cm.setQuorum(true);
        /*
        try {
            // check that cluster is online and CMM is consistent
            cm.setQuorum(true);
            cm.initClusterState();
        } catch (HoneycombTestException e) {
            Log.ERROR("Failed to intialize ClusterMembership: " + e.getMessage());
            testCase.testFailed("Cluster must be in bad state, not continuing");
            return;
        } 
        */       
        numNodes = cm.getNumNodes();

        /* Default number of test iterations is 2: if this test runs on a just-rebooted cluster,
         * we want to validate that first-ever update works, and that subsequent update works.
         * Override from command line for more iterations.
         */
        String iterationString = getProperty(HoneycombTestConstants.CLI_CHANGE_ITERATIONS);
        if (null==iterationString) {
            iterations=2;
        } else {
            try {
                iterations = Integer.parseInt(iterationString, 10);
                if (iterations <= 0) 
                    throw new NumberFormatException(
                                              "specify number of iterations!");
            } catch (NumberFormatException nfe) {
                throw new HoneycombTestException(
                                              "Invalid number of iterations: " +
                                              iterationString +
                                              " (should be at least 1)");
            }
        }

    }

    public void runTests() {    

        testCase = createTestCase("ConfigurationUpdate","syscfg");
        
        testCase.addTag(tags);
        if (testCase.excludeCase()) {
            return;
        }

        Log.INFO("Starting config update test for " + iterations + 
                 " iterations against cluster with " + numNodes + " nodes");

        int errors = 0;
        DataDoctorState state=DataDoctorState.getInstance();
        
        // Will reset to original value at the end of test
        int originalValue = -1;
        try {
            originalValue = state.getValue(CLIState.LOST_FRAGS);
        } catch (HoneycombTestException he) {
            Log.ERROR(he.getMessage());
        }
        
        for (int i = 1; i <=iterations; i++ ) {

            int val = 70000 + i;

            boolean actionSucceeded = true;

            // Trigger config update
            Log.SUM("ITERATION " + i + ": setting lost frags value to "+ val);
            state.setValue(CLIState.LOST_FRAGS,val);
            
            try {
                state.syncState();
            } catch (HoneycombTestException he) {
                Log.ERROR(he.getMessage());
            }
            
            /* Sleep 8 additional seconds - we're seeing some spurious errors
             * that are just because this can be a little slow.            
             */
            try {
                Thread.sleep(8*1000);
            } catch (InterruptedException e) {
                Log.ERROR ("Sleep interrupted:" +e.toString());
             }
            
            // Validate that CLI shows new value
            int gotV = -1;
            try {
                gotV = state.getValue(CLIState.LOST_FRAGS);
            } catch (HoneycombTestException he) {
                Log.ERROR(he.getMessage());
            }
            
            if(gotV != val) {
                Log.ERROR("CLI isn't reflecting the value we set. Set:" + val +" got:"+gotV);
                actionSucceeded = false;
            }

            /* Check on each live node that 
             * /opt/honecyomb/config/config.properties has the line
             * honeycomb.datadoctor.recover_lost_frags_cycle=i
             */
            for(int j=101;j<=(100+numNodes);j++) {

                String res = 
                    state.runCommand(j,
                                     "ls -lath /config/config.properties");
                Log.INFO("Config properties date: " + res);

                res = 
                    state.runCommand(j,
                                     "grep  recover_lost_frags_cycle /config/config.properties");
                if(null == res) {
                    Log.ERROR("Mismatch. Got no match for recover_lost_frags_cycle and expected : " + val);
                } else if (!res.endsWith(""+val)) {
                    Log.ERROR("Mismatch in config.properties. Got: " + res + " and expected it to end with: " + val);
                    actionSucceeded = false;
                } else {
                    Log.INFO("Matched setting of " + val);
                }

                /* Check that cluster node's log contains message about property change;
                 * this validates that config update callbacks work.
                 */
                //
                // Temporarily disabled - we no longer log on client nodes?
                // also, since we check that the actual property is changed, above, it's pretty
                // redundant to check and depend on a log message.
                //
                /*
                res = 
                    state.runCommand(j,
                                     "tail -7000 /var/adm/messages \\| grep  \\'honeycomb.datadoctor.recover_lost_frags_cycle="+val+" \\'");

                System.out.println("Got grep result::" + res);
                if( (null==res) || (res.length() < 20 )) {
                    Log.ERROR("No notification on node "+j+" of property change to " + val + " from " + (val-1));
                    actionSucceeded = false;
                }
                */
            }
            
            if (!actionSucceeded) {
                errors++;
                Log.ERROR("TEST ITERATION " + i + " FAILED");
            } else {
                Log.INFO("TEST ITERATION " + i + " PASSED");
            }
            
        }

        // reset
        state.setValue(CLIState.LOST_FRAGS,originalValue);


        if (errors == 0) {
            testCase.testPassed("Config update succeeded " + iterations + " times");
        } else {
            testCase.testFailed("Config update failed " + errors + " times out of " + iterations + " attempts");
        }
    }
}
