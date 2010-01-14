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

public class ClusterStartup extends HoneycombCMSuite {

    private boolean clusterRunning;
    String[] tags = {Tag.EXPERIMENTAL, HoneycombTag.CMM};

    public ClusterStartup() {
        super();
        clusterRunning = false;
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

        sb.append("\tTests cluster membership convergence upon startup. \n");
        sb.append("\tEach run starts the cluster, verifies convergence, stops the cluster. \n");

        sb.append(super.help());
        return sb.toString();
    }

    public void testClusterStartup() throws HoneycombTestException {

        if (lapse == 0) // all nodes start up at once
            params.append(",Simultaneous");
        else // there is time lapse between nodes' startup
            params.append(",Staggered");

        self = createTestCase("ClusterStartup", params.toString());
        
	self.addTag(tags);
        addCommonTags();
        if (self.excludeCase()) // should I run?
            return;

        clusterRunning = true;
        // Stop the cluster before doing anything!
        stopCluster();
        
        for (int i = 1; i <= iterations; i++) {

            if (i > 1)
                HCUtil.doSleep(settleDown, "settle down between test iterations");
            if (iterations > 1)
                Log.STEP("ITERATION " + i);
                        
            try {
             
                startCluster();

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
                    self.testFailed("CMM VERIFIER FAILED AFTER STARTUP");
 
                if (i != iterations) // don't stop cluster at the end of the test
                    stopCluster();
            
            } catch (HoneycombTestException hte) {

                self.testFailed("Got test exception -> exiting: " + hte);
                doCleanup();
                return;
            }
        }
        // leave the cluster running!
        // doCleanup(); 
    }

    /** Final cleanup method, can be called from exception handler, so don't throw
     */
    private void doCleanup() {
        Log.INFO("Final cleanup");
        try { // return to no-cluster-running state
            stopCluster(); 
        } catch (HoneycombTestException e) {
            Log.ERROR("Final cleanup failed: " + e);
        }
    }

    /** Starts nodes one by one in 'order' with 'lapse' time interval
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
            n.startHoneycomb();
            HCUtil.doSleep(computeStartupSkew(), "node startup skew");
        }
        clusterRunning = true;
    }
    
    /** Stops all nodes in the cluster.
     */
    public void stopCluster() throws HoneycombTestException {
        if (!clusterRunning)
            return;
        Log.STEP("STOPPING CLUSTER");
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode n = cm.getNode(i);
            n.stopHoneycomb();
        }
        clusterRunning = false;
    }

    /** Startup skew between nodes, in seconds.
     */
    public int computeStartupSkew() {
        return computeSkew();
    }
}
