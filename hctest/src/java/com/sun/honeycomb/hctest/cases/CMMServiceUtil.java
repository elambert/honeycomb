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

import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.Tag;

public class CMMServiceUtil extends ClusterStartup {
    
	protected String jvmname = null;
	protected String runlevel = null;
	protected boolean quorum = true;
	
    public CMMServiceUtil() {
        super();
    }

    public void setUp() throws Throwable {

        String[] tags = {Tag.EXPERIMENTAL,
                             Tag.WHITEBOX};

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

        sb.append("\tCommon Service Utils\n");
        sb.append("\tProperties: \n");
        sb.append("\t            jvmname      - a coma separted list of JVMs to polute with the faulty service. Default: NODE-SERVERS,PLATFORM-SERVERS,LAYOUT-SERVERS,API-SERVERS,IO-SERVERS\n");
        sb.append("\t            runlevel     - a coma separated list of runlevels to create the fault service at. Default: 1,2,3,4,5 (for quorum=true) 3,4,5 (for quorum=false) \n");
        sb.append("\t            quorum       - by setting this value to false we can force loss of quorum during test run. Default: quorum=true\n");
        
        sb.append(super.help());
        return sb.toString();
    }    

    // overwrite inorder to avoid testcase repetition...
    public void testClusterStartup() {
	    return;
    }
    
    public void createDummyTestServiceOnAllNodes(String jvmname, String classname, String servicename,String runlevel) throws HoneycombTestException{
    	// Infect all nodes.. with dummy TestService		            
        for (int x = 1; x <= numNodes; x++) {
            ClusterNode m = cm.getNode(x);
            // Put DynTestService in node_config.xml and set necessary properties
            m.addService(jvmname, classname, servicename, runlevel);            
        }
    }
    
    public void addPropertyToAllNodes(String jvmname, String property) throws HoneycombTestException{
    	// Infect all nodes.. with dummy TestService		            
        for (int x = 1; x <= numNodes; x++) {
            ClusterNode m = cm.getNode(x);
            // Put DynTestService in node_config.xml and set necessary properties
            m.setProperty(jvmname, property);       
        }
    }
    
    public boolean packageInstalledOnAllNodes(String packageName) throws HoneycombTestException{
    	// Check all nodes for installed package...
    	boolean result = true;
    	
        for (int x = 1; x <= numNodes; x++) {
            ClusterNode m = cm.getNode(x);
            // Put DynTestService in node_config.xml and set necessary properties
            result = result & m.packageInstalled(packageName);       
        } 
        
        return result;
    }
    
    public void delPropertyToAllNodes(String jvmname, String property) throws HoneycombTestException{
    	// Infect all nodes.. with dummy TestService		            
        for (int x = 1; x <= numNodes; x++) {
            ClusterNode m = cm.getNode(x);
            // Put DynTestService in node_config.xml and set necessary properties
            m.unsetProperty(jvmname, property);       
        }
    }
    
    public void removeDummyTestServiceOnAllNodes(String jvmname, String servicename) throws HoneycombTestException{
    	// Infect all nodes.. with dummy TestService		            
        for (int x = 1; x <= numNodes; x++) {
            ClusterNode m = cm.getNode(x);
            // Put DynTestService in node_config.xml and set necessary properties
            m.delService(jvmname, servicename);            
        }
    }
    
    public void createFileOnAllNodes(String filename, String contents) throws HoneycombTestException{
    	// Infect all nodes.. with dummy TestService		            
        for (int x = 1; x <= numNodes; x++) {
            ClusterNode m = cm.getNode(x);
            // Put DynTestService in node_config.xml and set necessary properties
            m.createFile(filename, contents);            
        }
    }
    
    public void removeFileFromAllNodes(String filename) throws HoneycombTestException{
    	// Infect all nodes.. with dummy TestService		            
        for (int x = 1; x <= numNodes; x++) {
            ClusterNode m = cm.getNode(x);
            // Put DynTestService in node_config.xml and set necessary properties
            m.removeFile(filename);            
        }
    }
    
    public void stopCluster() throws HoneycombTestException {
        Log.STEP("STOPPING CLUSTER");
         for (int i = 1; i <= numNodes; i++) {
             ClusterNode n = cm.getNode(i);
             n.pkillJava();
         }
     }
    
    public void setupClusterInDevMode() throws HoneycombTestException { 
        Log.STEP("PLACING CLUSTER IN DEV MODE.");
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode n = cm.getNode(i);
            n.setupDevMode();
        }
    }
    
    public void removeClusterFromDevMode() throws HoneycombTestException { 
        Log.STEP("REMOVING CLUSTER IN DEV MODE.");
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode n = cm.getNode(i);
            n.removeDevMode();
        }
    }
    
    protected String DEFAULT_SERVERS = "PLATFORM-SERVERS,LAYOUT-SERVERS,API-SERVERS,IO-SERVERS";
    
    public void initProperties() throws HoneycombTestException{
    	
    	String s = getProperty(HoneycombTestConstants.PROPERTY_CMM_QUORUM);    	
    	if (null != s){
   			quorum = Boolean.getBoolean(s);
    	} else {
    		quorum = true;
    	}
    	
    	s = getProperty(HoneycombTestConstants.PROPERTY_CMM_JVM_NAME);    	
    	if (null != s){
   			jvmname = s;
    	} else {
    		jvmname = DEFAULT_SERVERS;
    	}
    	
    	if (jvmname.indexOf("NODE-SERVERS") != -1){
    		throw new HoneycombTestException("NODE-SERVERS jvm is not supported in this testcase.");
    	}
    	
    	s = getProperty(HoneycombTestConstants.PROPERTY_CMM_RUN_LEVEL);    	
    	if (null != s){
    		runlevel = s;
    	} else {
            /*
             * TODO: 
             * need to do runlevel 0 as well but should be a separate test
             * where we take into account that messing with a runlevel 0 service
             * results in the node reboot.
             */
    		if (quorum)
    			runlevel = "1,2,3,4,5";
    		else 
    			runlevel = "1";
    	}
    }
    
    public void quorumSetup(){
    	if (!quorum) {
	        /* LOSE QUORUM */
	        boolean lossOK = cm.loseQuorumMax();
	        if (!lossOK)
	            self.testFailed("FAILED TO INDUCE QUORUM LOSS");
	
	        boolean cmOK = verifyClusterMembership(stopTimeout, "quorum-loss timeout");
	        if (!cmOK)
	            self.testFailed("VERIFIER FAILED AFTER QUORUM LOSS");
	
	        if (lossOK && cmOK)
	            self.testPassed("CLUSTER STATE OK AFTER QUORUM LOSS");
    	}
    }
    
    public void quorumShutdown() {
    	if (!quorum) {
	    	/* GAIN QUORUM AGAIN */
	        boolean gainOK = cm.gainQuorumMax();
	        if (!gainOK)
	            self.testFailed("FAILED TO INDUCE QUORUM REGAIN");
	        
	        boolean cmOK = verifyClusterMembership(startTimeout, "quorum-gain timeout");
	        if (!cmOK)
	            self.testFailed("VERIFIER FAILED AFTER QUORUM REGAIN");
	
	        if (gainOK && cmOK)
	            self.testPassed("CLUSTER STATE OK AFTER QUORUM REGAIN");
    	}
    }
    
    public ClusterNode pickRandomPeerNode() throws HoneycombTestException{
    	ArrayList nodes = new ArrayList(numNodes);
        for (int i = 1; i <= numNodes; i++) {
            nodes.add(new Integer(i)); // 1, 2, .., 10, 11, ..
        } 
        
        int nodeId = pickClusterNode(nodes);
        ClusterNode n = cm.getNode(nodeId);
        
        while ((!n.isAlive() || n.isMaster() || n.isVice()) && (nodes.size() >0)) {
        	nodeId = pickClusterNode(nodes);	
        	n = cm.getNode(nodeId);
        }
        	
        if (n == null)
        	throw new HoneycombTestException("Not able to find a valid peer node.");
        
        return n;
    }
}
