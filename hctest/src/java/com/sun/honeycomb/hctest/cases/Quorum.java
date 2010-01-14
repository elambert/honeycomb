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

public class Quorum extends HoneycombCMSuite {

    private String node_config;
    
    private String[] tags = {Tag.EXPERIMENTAL,
                             Tag.WHITEBOX,
                             HoneycombTag.CMM};
    public Quorum() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();

        sb.append("\tTests gain/loss of quorum and resulting change in service set. \n");
        sb.append("\tRequires interesting node_config.xml in node-mgr configuration. \n");
        sb.append("\tProvide name of node_config.xml with -ctx: " + 
                  HoneycombTestConstants.NODE_CONFIG_XML + "=<filename> \n");

        sb.append(super.help());

        return sb.toString();
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
        super.setUp();

        String s = getProperty(HoneycombTestConstants.NODE_CONFIG_XML);
        if (null != s) {
            node_config = s;
            replaceNodeConfig();
        } else {
            node_config = "node_config.xml";
        }
        params.append(" ncfg=" + node_config);
        
    }

    private void replaceNodeConfig() {
        // XXX add code to "ln -s" node_config.xml on all cluster nodes to given filename
        Log.WARN("Assuming that node_config.xml on the cluster already points to: " + node_config);
    }

    public void testQuorum() {

        self = createTestCase("QuorumGainLoss", params.toString());
	self.addTag(tags);
        if (runMode == CMM_ONLY) self.addTag(HoneycombTag.CMM_ONLY);

        if (self.excludeCase())
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

            boolean gainOK, lossOK, cmOK;
            
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
                
                /* GAIN QUORUM */

                gainOK = cm.gainQuorumMax();
                if (!gainOK)
                    self.testFailed("FAILED TO INDUCE QUORUM GAIN");

                cmOK = verifyClusterMembership(startTimeout, "quorum-gain timeout");
                if (!cmOK) 
                    self.testFailed("VERIFIER FAILED AFTER QUORUM GAIN");

                if (gainOK && cmOK)
                    self.testPassed("CLUSTER STATE OK AFTER QUORUM GAIN");

                /* LOSE QUORUM */

                lossOK = cm.loseQuorumMax();
                if (!lossOK)
                    self.testFailed("FAILED TO INDUCE QUORUM LOSS");

                cmOK = verifyClusterMembership(stopTimeout, "quorum-loss timeout");
                if (!cmOK)
                    self.testFailed("VERIFIER FAILED AFTER QUORUM LOSS");

                if (lossOK && cmOK)
                    self.testPassed("CLUSTER STATE OK AFTER QUORUM LOSS");

                /* GAIN QUORUM AGAIN */

                gainOK = cm.gainQuorumMax();
                if (!gainOK)
                    self.testFailed("FAILED TO INDUCE QUORUM REGAIN");
                
                cmOK = verifyClusterMembership(startTimeout, "quorum-gain timeout");
                if (!cmOK)
                    self.testFailed("VERIFIER FAILED AFTER QUORUM REGAIN");

                if (gainOK && cmOK)
                    self.testPassed("CLUSTER STATE OK AFTER QUORUM REGAIN");

                /* LOSE QUORUM AGAIN */

                lossOK = cm.loseQuorumMax();
                if (!lossOK)
                    self.testFailed("FAILED TO INDUCE QUORUM LOSS");

                cmOK = verifyClusterMembership(stopTimeout, "quorum-loss timeout");
                if (!cmOK)
                    self.testFailed("VERIFIER FAILED AFTER QUORUM LOSS");

                if (lossOK && cmOK)
                    self.testPassed("CLUSTER STATE OK AFTER QUORUM LOSS");
            
            } catch (HoneycombTestException hte) {

                self.testFailed("Got test exception -> exiting: " + hte);
                return;
            }
        }                
    }
}
