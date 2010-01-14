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

public class NodeBounce extends HoneycombCMSuite {

    private int numBounce;      // how many nodes to stop/start simultaneously
    private ArrayList nodes;    // used to randomly pick nodes... weird implementation
    private ArrayList bouncing; // keeps track of currently bouncing nodes

    private String[] tags = {Tag.WHITEBOX, HoneycombTag.CMM};

    public NodeBounce() {
        super();

        numBounce = 1;
        nodes = new ArrayList();
        bouncing = new ArrayList();
    }

    public String help() {
        StringBuffer sb = new StringBuffer();
        
        sb.append("\tTests cluster membership when nodes leave and rejoin. \n");
        sb.append("\tDefault number of nodes to bounce simultaneously is 1, ");
        sb.append("override with -ctx: " +
            HoneycombTestConstants.PROPERTY_BOUNCE + "=<num> \n");

        sb.append(super.help());

        return sb.toString();
    }

    /** Parses additional properties specific to NodeBounce test.
     *  Parsed props are added to the params string to form testcase name.
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

        String s = getProperty(HoneycombTestConstants.PROPERTY_BOUNCE);
        if (null != s) {
            try {
                numBounce = Integer.parseInt(s, 10);
                if (numBounce < 1) throw new NumberFormatException("less than 1");
            } catch (NumberFormatException nfe) {
                throw new HoneycombTestException("Invalid number of nodes to bounce: " + s);
            }
        }
        params.append(" bounce=" + numBounce);

        /* Populate the nodes array to pick bouncing nodes from
         */
        if (nodes.size() == 0) { 
            for (int i = 1; i <= numNodes; i++) {
                nodes.add(new Integer(i)); // 1, 2, .., 10, 11, ..
            }
        }
    }

    public void testNodeBounce() {
        
        self = createTestCase("NodeBounce", params.toString());

	self.addTag(tags);
        if (runMode == CMM_ONLY) self.addTag(HoneycombTag.CMM_ONLY);

        if (self.excludeCase()) // should I run?
            return;
        
        try {
            cm.initClusterState();
        } catch (HoneycombTestException e) {
            self.testFailed("CMM VERIFIER FAILED ON INITIAL STATE: " + e);
            return;
        }

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

                stopNode();
                
                if (numNodes == 8 && numBounce > 1) {
                    cm.setQuorum(false);
                }
                
                boolean leftOK = verifyClusterMembership(stopTimeout, "node-left timeout");
                if (!leftOK)
                    self.testFailed("CMM VERIFIER FAILED AFTER NODE LEFT");

                restartNode();
                cm.setQuorum(true);

                boolean joinOK = verifyClusterMembership(startTimeout, "node-join timeout");
                if (!joinOK) 
                    self.testFailed("CMM VERIFIER FAILED AFTER NODE REJOINED");
                
                if (leftOK && joinOK) 
                    self.testPassed("CLUSTER STATE OK AFTER NODE BOUNCE");

            } catch (HoneycombTestException hte) {
                
                self.testFailed("Got test exception -> exiting: " + hte);
                doCleanup();
                return;
            }
        }
        doCleanup(); // after last iteration
    }

    /** Final cleanup method, can be called from exception handler, so don't throw
     */
    private void doCleanup() {
        Log.INFO("Final cleanup");
        try { // return to full-cluster-running state
            restartNode(); 
        } catch (HoneycombTestException e) {
            Log.ERROR("Final cleanup failed: " + e);
        }
    }

    /** Kicks desired node(s) out of the cluster, using requested "kick" method.
     *
     *  In practice we either stop Honeycomb by "svcadm disable honeycomb-server",
     *  or kill JVMs by "pkill java", or disconnect network by turning off switch port.
     */
    public void stopNode() throws HoneycombTestException {

        assert nodes.size() >= numBounce;

        /* Pick numBounce nodes, remember their IDs, stop each node 
         */
        for (int i = 0; i < numBounce; i++) {
            int nodeId = pickClusterNode(nodes);
            Log.INFO("BOUNCE NODE " + nodeId);
            bouncing.add(new Integer(nodeId));
            
            ClusterNode n = cm.getNode(nodeId);
            if (n.isMaster())
                cm.setMasterFailover();
            if (n.isVice())
                cm.setViceFailover();
            n.stopNode(failMode);

            HCUtil.doSleep(computeSkew(), "node bounce skew");
        }
    }

    /** Allows kicked-out node(s) to rejoin the cluster, using correct restart method.
     *
     *  The restart method must match what we did in stopNode().
     *  In practice we restart Honeycomb by "svcadm enable honeycomb-server",
     *  or "start_cmm_only", or reconnect network by turning on the switch port.
     *
     *  Nodes are restarted in the same order as they were stopped,
     *  but we can easily change that to rand/incr/decr order if desired.
     */
    public void restartNode() throws HoneycombTestException {

        int bounced = bouncing.size();
        for (int i = 0; i < bounced; i++) {
            Integer nodeId = (Integer) bouncing.remove( 0 );
            nodes.add(nodeId); // add back to the set of alive nodes
            
            ClusterNode n = cm.getNode(nodeId.intValue());
            n.startNode(failMode);

            HCUtil.doSleep(computeSkew(), "node bounce skew");
        }
    }
}
