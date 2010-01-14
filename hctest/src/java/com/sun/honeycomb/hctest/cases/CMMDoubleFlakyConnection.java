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

import com.sun.honeycomb.hctest.HoneycombTag;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class CMMDoubleFlakyConnection extends CMMSingleFlakyConnection {
    
	private String badBehaviourM = null;
	private String badBehaviourMPlus1 = null;
	
    private ClusterNode nodeM = null;
    private ClusterNode nodeMPlus1 = null;
    	
    public CMMDoubleFlakyConnection() {
        super();
    }

    public String help() {
        StringBuffer sb = new StringBuffer();

        sb.append(super.help());
        return sb.toString();
    }
            
    private void pickSecondPair() throws HoneycombTestException { 
    	cm.initClusterState();
    	
    	master = cm.getMaster();
    	vice = cm.getVice();
    	
    	for (int i = numNodes; i >= 1; i--) {    		
            ClusterNode n = cm.getNode("hcb" + (100 + i));
            
            if (!n.isMaster() && !n.isVice() && n.isAlive()) {
            	// n is not master nor vice
            	// now check n - 1
            	
            	
            	// This a TEMPORARY little fix will jump over dead noes before deciding on n - 1
            	int j = i - 1;
            	while (j >= 1 && !cm.getNode("hcb" + (100 + j)).isAlive())
            		j--;
            		
            	ClusterNode m = cm.getNode("hcb" + (100 + j));
            	if (!m.isMaster() && !m.isVice() && m.isAlive()) {
            		nodeM = n;
            		nodeMPlus1 = m;
            		badBehaviourM = "60:DROP:HEARTBEAT|CONNECT:" + "SRC" + (100 + j) + "|DST" + (100 + j) + ":REPEAT";
            		badBehaviourMPlus1 = "60:DROP:HEARTBEAT|CONNECT:" + "SRC" + (100 + i) + "|DST" + (100 + i) + ":REPEAT";
            		Log.INFO("behaviour on " + nodeM + ": " + badBehaviourM);
            		Log.INFO("behaviour on " + nodeMPlus1 + ": " + badBehaviourMPlus1);
            		// We've found our candidates...
            		break;
            	}
            	n = null;
            	m = null;
            }
        }
    	
    	if (nodeM == null || nodeMPlus1 == null){
    		throw new HoneycombTestException("Candidate Nodes for flaky connection creation not found.");
    	} else {
    		Log.INFO("Flaky connection candidates are: " + nodeM + " and " + nodeMPlus1);
    	}
    }
    
    public void testFlakyConnection() throws HoneycombTestException {
    	
    	params.append(",DoubleFlakeyConection,PeerNodes");
    	
    	if (iterations == 1) 
            params.append(",SingleIteration");
        else if (iterations == 2)
            params.append(",DoubleIteration");
        else
            params.append(",MultipleIterations");
        
        self = createTestCase("ClusterFlakyConnection", params.toString());
        
        self.addTag(Tag.EXPERIMENTAL); // nobody but me should run this just yet
        self.addTag(HoneycombTag.CMM);
        addCommonTags();
        
        
        if (self.excludeCase()) // should I run?
            return;
        
    	if (cm.getMode() != CMM_ONLY_WITH_SNIFFER){
    		throw new HoneycombTestException("ClusterFlakeyConnection testcase can only be run in CMM_ONLY_WITH_SNIFFER mode.");
    	}
    	
    	String s = getProperty(HoneycombTestConstants.PROPERTY_CMM_FLAKY_CONNECTION_INTERVAL);    	
    	if (null != s){
   			flakyinterval = HCUtil.parseTime(s)/1000;
    	} else {
    		flakyinterval = 100; // 100s
    	}
    	
    	s = getProperty(HoneycombTestConstants.PROPERTY_CMM_REST_INTERVAL);    	
    	if (null != s){
   			restinterval = HCUtil.parseTime(s)/1000;
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
  
    	pickSecondPair();
    	
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
    	super.putInBadState();
        Log.STEP("Placing Connection between node: " + nodeM + " and  node: " + nodeMPlus1 + " in bad state.");
        nodeM.updateSnifferBehaviour(badBehaviourM);
        nodeMPlus1.updateSnifferBehaviour(badBehaviourMPlus1);
    }
    
    /** Put chosen nodes into good state again.
     */
    protected void putInGoodState() throws HoneycombTestException {
    	super.putInGoodState();
    	Log.STEP("Placing Connection between node: " + nodeM + " and  node: " + nodeMPlus1 + " in good state.");
    	nodeM.updateSnifferBehaviour(goodBehaviour);
        nodeMPlus1.updateSnifferBehaviour(goodBehaviour);
    }
}
