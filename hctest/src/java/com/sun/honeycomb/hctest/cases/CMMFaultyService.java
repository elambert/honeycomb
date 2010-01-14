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
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class CMMFaultyService extends CMMServiceUtil {
    
	private String faulttype = null;
        private String[] tags = {HoneycombTag.CMM,
                                 Tag.WHITEBOX};
	
    public CMMFaultyService() {
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

        sb.append("\tCreate a faulty service in a given Honeycomb JVM with a specified runlevel\n");
        sb.append("\tProperties: \n");
        sb.append("\t            faulttype    - defines the fault type to execute, currently dothrow,doexit and dojvmexit are supported. Default: dothrow\n");
        
        sb.append(super.help());
        return sb.toString();
    }    
    
    public void testFaultyService() throws HoneycombTestException {
    	
    	super.initProperties();
    	
    	String s = getProperty(HoneycombTestConstants.PROPERTY_CMM_FAULT_TYPE);    	
    	if (null != s){
    		if (s.equalsIgnoreCase("dothrow") || s.equalsIgnoreCase("doexit") || s.equalsIgnoreCase("dojvmexit"))
    			faulttype = s;
    		else
    			throw new HoneycombTestException("Fault type not supported: " + s + ". select from dothrow, doexit");
    	} else {
    		faulttype = "dothrow"; // default: dothrow
    	}
    	
    	params.append(",FaultyService");
    	params.append("," + faulttype);
        
        self = createTestCase("FaultyService", params.toString());
        self.addTag(tags);
        addCommonTags();
        
        if (self.excludeCase()) // should I run?
            return;        
        
        if (!packageInstalledOnAllNodes("SUNWhcwbcluster")){
    		throw new HoneycombTestException("Please install SUNWhcwbcluster package on all nodes, before running this testcase.");
    	}
        
        lapse = 0;
    	settleDown = 0;
    	startTimeout = 60; // 200 seconds
    	
    	String[] jvms = jvmname.split(",");
    	String[] runlevels = runlevel.split(",");
    	
    	ClusterNode n = null;
    	
        try { 
	        setupClusterInDevMode();
	    	stopCluster();
	    	HCUtil.doSleep(settleDown,"Letting cluster shutdown nicely...");
	    	
	    	// by cycling throught jvms and runlevels at the same time we test all
	    	// runlevels, but only one runlevel per jvm since this covers the 
	    	// approriate area of testing..
	    	
	    	int maxlevels = 0;
	    	if (jvms.length > runlevels.length)
	    		maxlevels = jvms.length;
	    	else 
	    		maxlevels = runlevels.length;
	    	
	    	Log.INFO("This test loops through the mininum amount of combinations between jvm names and runlevels, this does not do an exhaustive testrun.");

			for(int i  = 0; i < maxlevels; i++ ) { 
				String jvm_name = jvms[i%jvms.length];
				String runlevel_name = runlevels[i%runlevels.length];
				
	            try {
	            	// Infect all nodes with TestService	            
		            createDummyTestServiceOnAllNodes(jvm_name, "cm.DynTestService", "DynTestService", runlevel_name);
		            // Add property for faulttype definition 
		            addPropertyToAllNodes(jvm_name, "-D" + faulttype + "=true");
		        	
		            // Start the cluster
		        	startCluster();
		        	HCUtil.doSleep(startTimeout,"Letting cluster startup nicely...");
		        	
		        	cm.initClusterState();
		        	
		        	quorumSetup();			        
		        		            	
		        	if (jvm_name.equalsIgnoreCase("MASTER-SERVERS"))
		        		n = cm.getMaster();
		        	else
		        		n = pickRandomPeerNode();
		        	
			    	Log.INFO("Choose node: " + n + " will have a faulty service on " + jvm_name + " jvm at runlevel " + runlevel_name);
	            	
	            	// Send signal to DynTestService in order to make the service throw an exception
		            Log.INFO("Sending signal to faulty service, this will produce the following fault: " + faulttype);
	            	n.sendSignalToJVM(jvm_name);
	
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
	                    self.testPassed("CLUSTER STATE OK " + jvm_name + " WAS RESTARTED");
	                else
	                    self.testFailed("CMM VERIFIER FAILED AFTER " + jvm_name + " WAS RESTARTED");    
	
	                if (jvm_name.equalsIgnoreCase("MASTER-SERVERS"))
	                	delPropertyToAllNodes(jvm_name, "-D" + faulttype + "=true");
	                else
	                	n.unsetProperty(jvm_name,"-D" + faulttype + "=true");
	                
	                // Clean up all nodes.. with dummy TestService		            
		            removeDummyTestServiceOnAllNodes(jvm_name,"DynTestService");
	                
	                stopCluster();
			        HCUtil.doSleep(settleDown,"Letting cluster shutdown nicely...");
			        
	            } catch (HoneycombTestException hte) {
	                self.testFailed("Got test exception -> exiting: " + hte);
	                
	                if (jvms[i].equalsIgnoreCase("MASTER-SERVERS"))
	                	delPropertyToAllNodes(jvm_name, "-D" + faulttype + "=true");
	                else if (n != null)
	                	n.unsetProperty(jvm_name,"-D" + faulttype + "=true");
	                
	                // Clean up all nodes.. with dummy TestService		            
		            removeDummyTestServiceOnAllNodes(jvm_name,"DynTestService");
	                
	                stopCluster();
			        HCUtil.doSleep(settleDown,"Letting cluster shutdown nicely...");
	            }
			}	
        } finally { 
            /* 
             * try to stop the cluster correclty remove DevMode and leave it 
             * up and running...
             */
	    	stopCluster();
            removeClusterFromDevMode();
            startCluster();
            HCUtil.doSleep(startTimeout,"Letting cluster startup nicely...");
        }
    }
}
