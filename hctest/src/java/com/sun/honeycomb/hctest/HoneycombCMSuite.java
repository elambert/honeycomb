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



package com.sun.honeycomb.hctest;

import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import java.util.*;

public class HoneycombCMSuite extends HoneycombSuite {

    // poor man's enum for HC configuration
    public final static int FULL_HC = 0;
    protected final int CMM_ONLY = 1;
    protected final int NODE_MGR = 2;
    protected final int CMM_SINGLE = 3;
    protected final int CMM_ONLY_WITH_SNIFFER = 4;
    
    // poor man's enum for startup order
    protected final int RAND_ORDER = 0;
    protected final int INCR_ORDER = 1;
    protected final int DECR_ORDER = 2;
    
    // poor man's enum for startup lapse time
    protected final int RAND_LAPSE = -1;
    
    protected StringBuffer params; // test parameters - string version of these settings:
    protected TestCase self;    // to file pass/fail test results
    
    protected String clusterIP; // IP to access cluster nodes from outside (adminVIP)
    protected ClusterMembership cm; // to track CMM state and manipulate nodes
    
    protected int iterations; // number of test iterations to execute (default = 1)
    protected int numNodes;   // how many nodes in the cluster
    protected int runMode;    // HC configuration: FULL_HC (default), CMM_ONLY or NODE_MGR
    protected int order;      // order of operations on nodes: RAND (default), INCR or DECR
    protected int lapse;      // sleep time between operations on nodes (default = random)
    protected int maxLapse;   // max sleep time (default = 1 minute)
    protected int failMode;   // what to fail: honeycomb process, network etc.
    protected int settleDown; // time to sleep (seconds) between test iterations
    protected int stopTimeout; // time to wait (seconds) after a node is stopped in some fashion
    protected int startTimeout; // time to wait (seconds) after a node is started in some fashion

    /** Common properties for CM tests.
     */
    public String help() {
        StringBuffer sb = new StringBuffer();

        sb.append("\nCommon properties of CM tests: \n");
        sb.append("\tDefault number of runs is 1, override with -ctx: " +
                  HoneycombTestConstants.PROPERTY_ITERATIONS + "=<num> (0 for forever) \n");
        sb.append("\tDefault HC mode is full stack HC, override with -ctx: " +
                  HoneycombTestConstants.PROPERTY_CMM_ONLY + "=yes \n");
        sb.append("\tDefault startup order of nodes is random, override with -ctx: " +
                  HoneycombTestConstants.STARTUP_ORDER + "=<incr|decr> \n");
        sb.append("\tDefault time lapse between node bringups is random, override with -ctx: " +
                  HoneycombTestConstants.STARTUP_SKEW + "=<seconds> (0 is valid)\n");
        sb.append("\tRandom lapse times are chosen up to max of 1 minute, override max with -ctx: " + 
                  HoneycombTestConstants.STARTUP_MAX_SKEW + "=<seconds> \n");
        sb.append("\tDefault failure mode is to stop honeycomb, override with -ctx: " +
                  HoneycombTestConstants.PROPERTY_FAIL_MODE + "=<mode> \n");

        return sb.toString();
    }

    public HoneycombCMSuite() {
        super();

        TestBed b = TestBed.getInstance();
        // TestBed assumes that the cluster is already online
        // In our case, it's not, it will be started by the test.
        if (b != null) {
            // check for nullness is needed for "help" mode
            b.gotCluster = false; 
            clusterIP = b.adminVIP;
        }

        iterations = 1;
        numNodes = 8;
        runMode = FULL_HC;
        failMode = HoneycombTestConstants.FAIL_HC;

        order = RAND_ORDER;
        lapse = RAND_LAPSE;
        maxLapse = 60; // seconds
        // Need to sleep between test iterations so sockets would release
        settleDown = 10; // seconds
        // Default CMM testing timeouts
        stopTimeout = 5; // max seconds between node-stop and node-left-cluster
        startTimeout = 10; // max seconds between node-start and node-joined-cluster

        // cm object is created in setUp() with correct params
    }

    public void tearDown() throws Throwable {
        super.tearDown();
    }

    /** Parse cmd-line properties specific to HoneycombCMSuite.
     */
    public void setUp() throws Throwable {
        super.setUp(); // call after setting requiredProps
        
        params = new StringBuffer();
        String s;
        
        s = getProperty(HoneycombTestConstants.PROPERTY_ITERATIONS);
        if (null != s) {
            try {
                iterations = Integer.parseInt(s, 10);
                if (iterations < 0) throw new NumberFormatException("negative");
            } catch (NumberFormatException nfe) {
                throw new HoneycombTestException("Invalid number of iterations: " + s + 
                                                 " (should be non-negative int, 0 for forever)");
            }
        }
        if (iterations == 0) {
            iterations = Integer.MAX_VALUE;
        }
        
        s = getProperty(HoneycombTestConstants.PROPERTY_NODES);
        if (null != s) {
            try {
                numNodes = Integer.parseInt(s, 10);
                if (numNodes <= 0) throw new NumberFormatException("no nodes");
            } catch (NumberFormatException nfe) {
                throw new HoneycombTestException("Invalid number of cluster nodes: " + s +
                                                 " (should be at least 1, default 8)");
            }
        }
        params.append(numNodes + "-Nodes");

        s = getProperty(HoneycombTestConstants.PROPERTY_CMM_ONLY);
        if (null != s) {
            runMode = CMM_ONLY;
            params.append(",CMM-Only");
        } 
        s = getProperty(HoneycombTestConstants.PROPERTY_NODE_MGR);
        if (null != s) {
            runMode = NODE_MGR;
            params.append(",NodeMgr,");
        }
        s = getProperty(HoneycombTestConstants.PROPERTY_CMM_SINGLE);
        if (null != s) {
            runMode = CMM_SINGLE;
            params.append(",CMM-Single");
            
            /* Archive all local CMM logs from multiple JVMs */
            LogArchive archiver = LogArchive.getInstance();
            archiver.addLogSourceDir("../logs", "cmm_logs"); 
        }
        
        s = getProperty(HoneycombTestConstants.PROPERTY_CMM_ONLY_WITH_SNIFFER);
        if (null != s) {
        	runMode = CMM_ONLY_WITH_SNIFFER;
            params.append(",CMM-Only-With-Sniffer");
        }
        
        if (runMode == FULL_HC) {
            // this is default, don't include in testcase name
            // params.append(" runMode=full-hc");
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_FAIL_MODE);
        if (null != s) {
            if (s.equals("network")) {
                failMode = HoneycombTestConstants.FAIL_NETWORK;
            } else if (s.equals("process")) {
                failMode = HoneycombTestConstants.PKILL_JAVA;
            }
        } // don't include failMode in testcase parameters,
        // it isn't relevant for all CM tests
        
        s = getProperty(HoneycombTestConstants.CMM_START_TIMEOUT);
        if (null != s) {
            try {
                startTimeout = Integer.parseInt(s, 10);
                if (startTimeout < 0) throw new NumberFormatException("negative");
            } catch (NumberFormatException nfe) {
                throw new HoneycombTestException("Invalid start timeout value: " + s +
                                                 " (should be non-negative int)");
            }            
        } else if (runMode == FULL_HC) {
            startTimeout = 120; // 2 minutes
        }

        s = getProperty(HoneycombTestConstants.CMM_STOP_TIMEOUT);
        if (null != s) {
            try {
                stopTimeout = Integer.parseInt(s, 10);
                if (stopTimeout < 0) throw new NumberFormatException("negative");
            } catch (NumberFormatException nfe) {
                throw new HoneycombTestException("Invalid stop timeout value: " + s +
                                                 " (should be non-negative int)");
            }            
        } else if (runMode == FULL_HC) {
            stopTimeout = 90; // minute and a half
        }

        /* timeouts are not included in testcase name as params,
           but they are settable from command line for fine tuning,
           and logged
        */
        Log.INFO("CMM timeouts: start=" + startTimeout + ", stop=" + stopTimeout + " seconds");
        
        s = getProperty(HoneycombTestConstants.STARTUP_ORDER);
        if (null != s) {
            if (s.equals("incr")) {
                order = INCR_ORDER;
            } else if (s.equals("decr")) {
                order = DECR_ORDER;
            } else {
                throw new HoneycombTestException("Invalid startup order: " + s +
                                                 " (should be <incr|decr>)");
            } 
        } else {
            order = RAND_ORDER;
        }

        s = getProperty(HoneycombTestConstants.STARTUP_SKEW);
        if (null != s) {
            try {
                lapse = Integer.parseInt(s, 10);
                if (lapse < 0) throw new NumberFormatException("negative");
            } catch (NumberFormatException nfe) {
                throw new HoneycombTestException("Invalid time lapse value: " + s +
                                                 " (should be non-negative int)");
            }
        }

        s = getProperty(HoneycombTestConstants.STARTUP_MAX_SKEW);
        if (null != s) {
            if (lapse != RAND_LAPSE) {
                throw new HoneycombTestException("Property does not apply: " +
                                                 HoneycombTestConstants.STARTUP_MAX_SKEW +
                                                 " because time lapse is non-random");
            }
            try {
                maxLapse = Integer.parseInt(s, 10);
                if (maxLapse < 0) throw new NumberFormatException("negative");
            } catch (NumberFormatException nfe) {
                throw new HoneycombTestException("Invalid max time lapse value: " + s +
                                                 " (should be non-negative int)");
            }
        }

        cm = new ClusterMembership(numNodes, clusterIP, runMode);
        if (runMode == FULL_HC) { // expect to have quorum - test can override this
            cm.setQuorum(true);
        }
    }

    /** Selects a node from given array in random|incr|decr order.
     *
     *  Warning: node is removed from the original array! Smaller pool remains.
     */
    public int pickClusterNode(ArrayList from) throws HoneycombTestException {
        Integer nodeId;
        if (order == INCR_ORDER) {
            nodeId = (Integer)from.remove( 0 );
        } else if (order == DECR_ORDER) {
            nodeId = (Integer)from.remove( from.size()-1 );
        } else { // RAND_ORDER
            int index = RandomUtil.randIndex(from.size());
            nodeId = (Integer)from.remove( index );
        }
        return nodeId.intValue();
    }

    /** Verifies that cluster membership is consistent across all nodes.
     *
     *  The verifier is run from the cheat node, so ssh'ing to the cheat.
     */
    public boolean verifyClusterMembership(int timeout, String note) {

        /* give cluster time to settle before verifying state;
         * the timeout will be different depending on preceding action
         * (eg starting node, stopping node; timeout of zero is also fine)
         */
        HCUtil.doSleep(timeout, note); 

        boolean ok = false;
        try {
            cm.updateClusterState();
            if (cm.hasExpectedState()) {
                Log.INFO("OK CLUSTER STATE: " + cm);
                ok = true;
            } else {
                Log.ERROR("WRONG CLUSTER STATE: " + cm);
                ok = false;
            }
            cm.syncState(); // expected := actual even if wrong
        } catch (HoneycombTestException e) {
            Log.ERROR("INCONSISTENT CLUSTER STATE: " + e);
            ok = false;
            // do not sync to inconsistent state!
        }
        return ok;
    }

    /** Skew between operations on cluster nodes (in seconds) is configurable.
     *
     *  If set by the user explicitly, that's what we use (zero is valid).
     *  If the user specified random skew with a max limit, we get random value.
     */
    public int computeSkew() {
        if (lapse == RAND_LAPSE) {
            int rlapse = 0;
            try {
                rlapse = RandomUtil.randIndex(maxLapse);   
            } catch (HoneycombTestException hte) {
                Log.WARN("Failed to get random lapse value, using zero: " + hte);
            }
            return rlapse;
        } else {
            return lapse; // seconds
        }
    }

    // to be called by testcases when adding tags... this one is common
    protected void addCommonTags() {
        self.addTag(Tag.WHITEBOX);
        if (runMode == CMM_ONLY) 
            self.addTag(HoneycombTag.CMM_ONLY);
        if (runMode == CMM_ONLY_WITH_SNIFFER) 
            self.addTag(HoneycombTag.CMM_ONLY_WITH_SNIFFER);
        else if (runMode == NODE_MGR) 
            self.addTag(HoneycombTag.NODE_MGR);
        else
            self.addTag(HoneycombTag.FULL_HC);
    }
}
