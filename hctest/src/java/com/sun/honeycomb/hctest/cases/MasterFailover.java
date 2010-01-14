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
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import java.util.*;

public class MasterFailover extends HoneycombCMSuite {

    // by default, fail over master; override with cmd-line arg "vice"
    private static final String FAILOVER_MODE = "vice";
    private static final int MASTER_FAILOVER = 0;
    private static final int VICE_FAILOVER = 1;
    private static final int MASTER_AND_VICE_FAILOVER = 3;

    private int failover; // mode: master or vice
    private boolean stopped; // state variable, determines whether cleanup is needed
    private ClusterNode n; // which node is under failover
    private ClusterNode n2; // which node is under failover
    private String[] tags = {Tag.WHITEBOX, HoneycombTag.CMM};


    public String help() {
        StringBuffer sb = new StringBuffer();

        sb.append("\tTests cluster membership when master/vicemaster failover happens. \n");
        sb.append("\tBy default, fails over master node; override with -ctx: " + FAILOVER_MODE + "\n");

        sb.append(super.help());

        return sb.toString();
    }

    /** Parses additional properties specific to MasterFailover test.
     */
    public void setUp() throws Throwable {
       	//The super.setUp() method attemps to talk to nodes
	//in the cluster, we should only do this if want to run the test.
	//The super.setUp also sets params. If we don't set it, we'll get an 
	//NPE later
        if (!Run.isTagSetActive(tags)) {
            Log.WARN("Tag set is not active skipping set up.");
            params = new StringBuffer("");
            return;
        }
       	Log.WARN("Tag is active, executing set up.");
        super.setUp();

        String s = getProperty(FAILOVER_MODE);
        if (null != s) {
        	if (s.equalsIgnoreCase("aswell")){
        		failover = MASTER_AND_VICE_FAILOVER;
        	} else {
        		failover = VICE_FAILOVER;
        	}
        } else {
        	failover = MASTER_FAILOVER; // default
        }
    }

    public void testFailover() {

        // how many failovers the test performs, in sequence
        if (iterations == 1) 
            params.append(",Single");
        else if (iterations == 2)
            params.append(",Double");
        else
            params.append(",Multiple");

        if (failover == MASTER_AND_VICE_FAILOVER)
        	self = createTestCase("MasterAndViceFailover", params.toString());
        else if (failover == MASTER_FAILOVER)
            self = createTestCase("MasterFailover", params.toString());
        else
            self = createTestCase("ViceMasterFailover", params.toString());

	self.addTag(tags);
        addCommonTags();
        if (self.excludeCase()) // should I run?
            return;

        try {
            cm.initClusterState();
        } catch (HoneycombTestException e) {
            self.testFailed("CMM VERIFIER FAILED ON INITIAL STATE: " + e);
            return;
        }

        n2 = null;
        n = null;
        stopped = false;

        /** Execute desired number of test iterations.
         *  The test does not stop if there is a failure during any iteration,
         *  unless it's a test exception, or initial cluster state is wrong.
         */
        for (int i = 1; i <= iterations; i++) {
            
            if (i > 1)
                HCUtil.doSleep(settleDown, "settle down between test iterations");
            if (iterations > 1)
                Log.STEP("ITERATION " + i);
            
            try {
                /* Reset expectations (if previous test iteration failed,
                   the cluster may not have the expected master/vice, so
                   we should go on the actual cluster state
                */
                cm.initClusterState();

                /* Set expectations for failover
                 */
                if (failover == MASTER_AND_VICE_FAILOVER){
                	cm.setMasterFailover();
                	cm.setViceFailover();
                } else if (failover == MASTER_FAILOVER)
                    cm.setMasterFailover();
                else
                    cm.setViceFailover();

                if (failover == MASTER_AND_VICE_FAILOVER){
                	n = cm.getMaster();
                    Log.INFO("MASTER: " + n);
                    
                    n2 = cm.getVice();
                    Log.INFO("VICEMASTER: " + n2);
                    
                    assert (n != null); // verify should have failed if no master/vice
                    assert (n2 != null); // verify should have failed if no master/vice
                    
                    n.stopNode(failMode);
                    n2.stopNode(failMode);
	                stopped = true;
	               
	                if (failover == MASTER_AND_VICE_FAILOVER && numNodes == 8) 
	                    cm.setQuorum(false);
	                
	                boolean leftOK = verifyClusterMembership(stopTimeout, "node-left timeout");
	                if (!leftOK) {
	                    self.testFailed("CMM VERIFIER FAILED AFTER " + n + " and " + n2 +  " LEFT");
	                }
	                
	                HCUtil.doSleep(computeSkew(), "failover skew");
	            	
	                n.startNode(failMode);
	                stopped = false;	                
	                
	                n2.startNode(failMode);
	                stopped = false;

	                cm.setQuorum(true);
	                
	                // No failover happens now - just a node rejoining the cluster
	                boolean joinOK = verifyClusterMembership(startTimeout, "node-join timeout");
	                if (!joinOK) 
	                    self.testFailed("CMM VERIFIER FAILED AFTER " + n + " and " + n2 + " REJOINED");
	                
	                if (leftOK && joinOK)
	                    self.testPassed("CLUSTER STATE OK AFTER MASTER AND VICE FAILOVER");
                                        
                } else { 
                
	                if (failover == MASTER_FAILOVER) {
	                    n = cm.getMaster();
	                    Log.INFO("MASTER: " + n);
	                } else {
	                    n = cm.getVice();
	                    Log.INFO("VICEMASTER: " + n);
	                }
	                
	                assert (n != null); // verify should have failed if no master/vice
	
	                n.stopNode(failMode);
	                stopped = true;
                
	                boolean leftOK = verifyClusterMembership(stopTimeout, "node-left timeout");
	                if (!leftOK) 
	                    self.testFailed("CMM VERIFIER FAILED AFTER " + n + " LEFT");
	
	                HCUtil.doSleep(computeSkew(), "failover skew");
	
	                n.startNode(failMode);
	                stopped = false;

	                // No failover happens now - just a node rejoining the cluster
	                boolean joinOK = verifyClusterMembership(startTimeout, "node-join timeout");
	                if (!joinOK) 
	                    self.testFailed("CMM VERIFIER FAILED AFTER " + n + " REJOINED");
	                
	                if (leftOK && joinOK)
	                    self.testPassed("CLUSTER STATE OK AFTER FAILOVER");
                }

            } catch (HoneycombTestException hte) {
                
                self.testFailed("Got test exception -> exiting: " + hte);
                doCleanup();
                return;
                
            }
        }  
        doCleanup();
    }
    
    /** Final cleanup method, can be called from exception handler, so don't throw
     */
    private void doCleanup() {
        Log.INFO("Final cleanup");
        if (stopped && n != null) {
            try { // return to full-cluster-running state
                n.startNode(failMode); 
            } catch (HoneycombTestException e) {
                Log.ERROR("Final cleanup failed: " + e);
            }
        }
        if (stopped && n2 != null) {
            try { // return to full-cluster-running state
                n2.startNode(failMode); 
            } catch (HoneycombTestException e) {
                Log.ERROR("Final cleanup failed: " + e);
            }
        }
    }
}
