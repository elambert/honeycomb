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

public class CMMSlowServiceStartup extends CMMServiceUtil {
    
	private long pause = 0;
	private String repeat = null;

        private String[] tags = {HoneycombTag.CMM,HoneycombTag.SVC_MGMT};
	
    public CMMSlowServiceStartup() {
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

        sb.append("\tCreate a slow starting service in a given Honeycomb JVM with a specified runlevel\n");
        sb.append("\tProperties: \n");
        sb.append("\t            pause        - defines the amount of time to pause during the slow startup chosen. Default: 30s\n");
        sb.append("\t            repeat       - defines the number of consecutive times to repeat the slow startup of the service. Default: 1\n");
        
        sb.append(super.help());
        return sb.toString();
    }
    
    public void testSlowServiceStartup() throws HoneycombTestException {               
    	
    	super.initProperties();
        
    	String s = getProperty(HoneycombTestConstants.PROPERTY_CMM_REPEAT);    	
    	if (null != s){
    		try {
    			new Long(s);
    		} catch (NumberFormatException e ){
    			throw new HoneycombTestException("repeat must be a number not: " + s);
    		}
   			repeat = s;
    	} else {
    		repeat = "1"; // default: 1 repetition
    	}
    	
    	s = getProperty(HoneycombTestConstants.PROPERTY_CMM_PAUSE);    	
    	if (null != s){
    		pause  = HCUtil.parseTime(s)/1000; // convert to seconds
    	} else {
    		pause = 30; // default: runlevel 3
    	}
    	
    	if (pause < 30)
    		params.append(",AccpetablySlowServiceStartup");
    	else
    		params.append(",SlowServiceStartup");
        
    	if (pause < 30)
    		self = createTestCase("AcceptablySlowServiceStartup", params.toString());
    	else
    		self = createTestCase("SlowServiceStartup", params.toString());
        
        self.addTag(tags);
        addCommonTags();
     
        if (self.excludeCase()) // should I run?
            return;
        
        if (!packageInstalledOnAllNodes("SUNWhcwbcluster")){
    		throw new HoneycombTestException("Please install SUNWhcwbcluster package on all nodes, before running this testcase.");
    	}
    	    	  
        lapse = 0;
    	settleDown = 0;
    	
    	String[] jvms = jvmname.split(",");
    	String[] runlevels = runlevel.split(",");
    	ClusterNode n = null;	
      	
    	stopCluster();
    	HCUtil.doSleep(settleDown,"Letting cluster shutdown nicely...");
	       
    	int maxlevels = 0;
    	if (jvms.length > runlevels.length)
    		maxlevels = jvms.length;
    	else 
    		maxlevels = runlevels.length;
    	
    	Log.INFO("This test loops through the mininum amount of combinations between jvm names and runlevels, this does not do an exhaustive testrun.");
    	
		for(int i = 0; i < maxlevels; i++ ) { 
			String jvm_name = jvms[i%jvms.length];
			String runlevel_name = runlevels[i%runlevels.length];
			
	        try {  	
	        	// Infect all nodes.. with dummy TestService		            
	            createDummyTestServiceOnAllNodes(jvm_name, "cm.TestFaultyService", "TestFaultyService", runlevel_name);
	            addPropertyToAllNodes(jvm_name,"-Dstartuppause=" + pause);
	            
	            if (jvm_name.equalsIgnoreCase("MASTER-SERVERS"))
	            	// Infect all nodes
	            	createFileOnAllNodes("/tmp/TestFaultyService.properties","repeat=" + repeat);
	            else { 
	            	n = pickRandomPeerNode();
	            	Log.INFO("Picked node: " + n + " for slow service startup on jvm: " + jvm_name + " at runlevel: " + runlevel_name);
	            	n.createFile("/tmp/TestFaultyService.properties","repeat=" + repeat);
	            }
	            
	        	// Start the cluster
	        	startCluster();
	        	HCUtil.doSleep(60,"Letting cluster startup nicely...");
	        	
	        	cm.initClusterState();
	        	
	        	quorumSetup();
	        	
	            Log.INFO("Test in startup_pause mode, therefore service will probably not startup intime.");                    
	            Log.INFO("Attempting to verify service state");
	            
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
	            	removeFileFromAllNodes("/tmp/TestFaultyService.properties");
	            else
	            	n.removeFile("/tmp/TestFaultyService.properties");
		        
	        	// Clean up all nodes.. with dummy TestService		            
	            removeDummyTestServiceOnAllNodes(jvm_name,"TestFaultyService");
	            delPropertyToAllNodes(jvm_name,"-Dstartuppause=" + pause);
	            
		        stopCluster();
		        HCUtil.doSleep(settleDown,"Letting cluster shutdown nicely...");		        
	        } catch (HoneycombTestException hte) {
	            self.testFailed("Got test exception -> exiting: " + hte);
	            
	            if (jvm_name.equalsIgnoreCase("MASTER-SERVERS"))
	            	removeFileFromAllNodes("/tmp/TestFaultyService.properties");
	            else if (n != null)
	            	n.removeFile("/tmp/TestFaultyService.properties");
		        
	        	// Clean up all nodes.. with dummy TestService		            
	            removeDummyTestServiceOnAllNodes(jvm_name,"TestFaultyService");
	            delPropertyToAllNodes(jvm_name,"-Dstartuppause=" + pause);
	            
		        stopCluster();
		        HCUtil.doSleep(settleDown,"Letting cluster shutdown nicely...");		
	        }  
		}

		// Start the cluster
    	startCluster();
    	HCUtil.doSleep(300,"Letting cluster startup nicely...");
    }
}
