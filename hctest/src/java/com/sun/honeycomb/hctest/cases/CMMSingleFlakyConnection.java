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

import java.util.ArrayList;

import com.sun.honeycomb.hctest.HoneycombTag;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class CMMSingleFlakyConnection extends ClusterStartup {
    
    protected String goodBehaviour = "100:NOTHING:.*:-1:REPEAT";
    private String badBehaviourN = null;
    private String badBehaviourNPlus1 = null;
    
    private ClusterNode nodeN = null;
    private ClusterNode nodeNPlus1 = null;
    
    protected long flakyinterval = 0;
    protected long restinterval = 0;

	ClusterNode master = null;
	ClusterNode vice = null;
	
        String[] tags = {Tag.EXPERIMENTAL, Tag.WHITEBOX, HoneycombTag.CMM};

    public CMMSingleFlakyConnection() {
        super();
    }

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
    }


    public String help() {
        StringBuffer sb = new StringBuffer();

        sb.append("\tTest flaky connection between nodes in a ring. \n");
        sb.append("\tEach it selects two random nodes (not master or vice) and ");
        sb.append("then proceeds to making all heartbeat and connects fail between ");
        sb.append("these nodes for the duration of an iteration and then proceeds to putting things back to normal. \n");
        sb.append("\tProperties: \n");
        sb.append("\t            flakyinterval - defines the amount of time during which a connection is maintained in the flaky state. can be any interval string of the type: 1h, 2m, etc. default 100s\n");
        sb.append("\t            restinterval  - defines the amoutn of time to rest between the creatoin of flaky connections. can be any interval string of the type: 1h, 2m, etc. default 30s \n");
        
        sb.append(super.help());
        return sb.toString();
    }

        
    /** overwriting temporarly for cmm-only setup...
     *  we need to clean up the cluster and be sure every service is turned off...
     */
    public void stopCluster() throws HoneycombTestException {
        Log.STEP("STOPPING CLUSTER");
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode n = cm.getNode(i);
            n.stopHoneycomb();
        }
    }
    
    /** overwriting in order to startup sniffer as needed ... :)
     */
    public void startCluster() throws HoneycombTestException {
        Log.STEP("STARTING CLUSTER");
        ArrayList nodes = new ArrayList(numNodes);
        for (int i = 1; i <= numNodes; i++) {
            nodes.add(new Integer(i)); // 1, 2, .., 10, 11, ..
        }
        for (int i = 0; i < numNodes; i++) {
            int nodeId = pickClusterNode(nodes);
            ClusterNode n = cm.getNode(nodeId);
            // Put good normal behaviour on sniffer
            n.updateSnifferBehaviour(goodBehaviour);
            n.startupSniffer();
            n.startHoneycomb();
        }
    }

    protected void pickNodes() throws HoneycombTestException{
    	
    	cm.initClusterState();
    	
    	master = cm.getMaster();
    	vice = cm.getVice();
    	
    	for (int i = 1; i <= numNodes; i++) {    		
            ClusterNode n = cm.getNode("hcb" + (100 + i));
            
            if (!n.isMaster() && !n.isVice() && n.isAlive()) {
            	// n is not master nor vice
            	// now check n + 1

            	// This a TEMPORARY little fix will jump over dead noes before deciding on n - 1
            	int j = i + 1;
            	while (j <= numNodes && !cm.getNode("hcb" + (100 + j)).isAlive())
            		j++;
            	
            	ClusterNode m = cm.getNode("hcb" + (100 + j));
            	if (!m.isMaster() && !m.isVice() && m.isAlive()) {
            		nodeN = n;
            		nodeNPlus1 = m;
            		badBehaviourN = "60:DROP:HEARTBEAT|CONNECT:" + "SRC" + (100 + j) + "|DST" + (100 + j)  + ":REPEAT";
            		badBehaviourNPlus1 = "60:DROP:HEARTBEAT|CONNECT:" + "SRC" + (100 + i) + "|DST" + (100 + i) + ":REPEAT";
            		Log.INFO("behaviour on " + nodeN + ": " + badBehaviourN);
            		Log.INFO("behaviour on " + nodeNPlus1 + ": " + badBehaviourNPlus1);
            		// We've found our candidates...
            		break;
            	}
            	n = null;
            	m = null;
            }
        }
    	
    	if (nodeN == null || nodeNPlus1 == null){
    		throw new HoneycombTestException("Candidate Nodes for flaky connection creation not found.");
    	} else {
    		Log.INFO("Flaky connection candidates are: " + nodeN + " and " + nodeNPlus1);
    	}
    }

    // overwrite inorder to avoid testcase repetition...
    public void testClusterStartup() {
	    return;
    }
    
    public void testFlakyConnection() throws HoneycombTestException {
    	params.append(",SingleFlakeyConection,PeerNodes");
    	
    	if (iterations == 1) 
            params.append(",SingleIteration");
        else if (iterations == 2)
            params.append(",DoubleIteration");
        else
            params.append(",MultipleIterations");
        
        self = createTestCase("ClusterFlakyConnection", params.toString());
	self.addTag(tags);
        addCommonTags();
        
        if (self.excludeCase()) // should I run?
            return;
        
    	if (cm.getMode() != CMM_ONLY_WITH_SNIFFER){
    		throw new HoneycombTestException("ClusterFlakeyConnection testcase can only be run in CMM_ONLY_WITH_SNIFFER mode.");
    	}
    	
    	String s = getProperty(HoneycombTestConstants.PROPERTY_CMM_FLAKY_CONNECTION_INTERVAL);    	
    	if (null != s){
   			flakyinterval = HCUtil.parseTime(s)/1000; // seconds
    	} else {
    		flakyinterval = 100; // 100s
    	}
    	
    	s = getProperty(HoneycombTestConstants.PROPERTY_CMM_REST_INTERVAL);    	
    	if (null != s){
   			restinterval = HCUtil.parseTime(s)/1000; // seconds
    	} else {
    		restinterval = 30; // 30s
    	}
    	
    	// Kill off everything on the cluster...
    	Log.INFO("Stopping all services on nodes.");
    	stopCluster();
    	
    	// Startup cluster...
    	Log.INFO("Starting up services on nodes.");
    	startCluster();	    
    	
    	HCUtil.doSleep(settleDown, "wait for cluster to settle down before picking candidates.");
    	    	
    	// Pick nodes to create flaky connection between.
    	pickNodes();
        
        for (int i = 1; i <= iterations; i++) {
        	
            if (iterations > 1)
                Log.STEP("ITERATION " + i);
                        
            try {
            	
            	// Put cluster in bad state
                putInBadState();
                
                // Sleep for a good amount of time
                HCUtil.doSleep(flakyinterval, "Wait in Bad state for a while.");
                
                // Return cluster to god state
                Log.STEP("Returning to good state.");
                putInGoodState();
                
                HCUtil.doSleep(restinterval, "settle down, on return to good state.");
                
                // in FULL_HC, wait for quorum up to 10 minutes; else verify once
                boolean ok = false;
                int tries = 0;
                int MAX_TRIES = (runMode == FULL_HC) ? 20 : 1; 
                while (!ok && tries < MAX_TRIES) {
                    tries++;
                    ok = verifyClusterMembership(startTimeout, "node-join timeout");
                    if (!ok)
                        Log.INFO("Verifier failed on attempt " + tries);
                }

                if (ok)
                    self.testPassed("CLUSTER STATE OK AFTER STARTUP");
                else
                    self.testFailed("CMM VERIFIER FAILED AFTER return to good state");
                
                cm.initClusterState();
                
                if (cm.getMaster() != master)
                	Log.ERROR("Master Differs! previous master: " + master + " current master: " + cm.getMaster());
                	
                if (cm.getVice() != vice)
                     Log.ERROR("Vice Master Differs! previous vice: " + vice + " current vice: " + cm.getVice());
            
            } catch (HoneycombTestException hte) {
                self.testFailed("Got test exception -> exiting: " + hte);
            }
        }
    }

    /** Put chosen nodes into flaky connection state between them.
     */
    protected void putInBadState() throws HoneycombTestException {
        Log.STEP("Placing Connection between node: " + nodeN + " and  node: " + nodeNPlus1 + " in bad state.");
        nodeN.updateSnifferBehaviour(badBehaviourN);
        nodeNPlus1.updateSnifferBehaviour(badBehaviourNPlus1);
    }
    
    /** Put chosen nodes into good state again.
     */
    protected void putInGoodState() throws HoneycombTestException {
    	Log.STEP("Placing Connection between node: " + nodeN + " and  node: " + nodeNPlus1 + " in good state.");
        nodeN.updateSnifferBehaviour(goodBehaviour);
        nodeNPlus1.updateSnifferBehaviour(goodBehaviour);
    }
}
