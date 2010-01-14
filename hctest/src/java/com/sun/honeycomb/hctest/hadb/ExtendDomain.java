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



package com.sun.honeycomb.hctest.hadb;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;


import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.util.ClusterMembership;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.wb.sp.hadb.scripts.Script;
import com.sun.honeycomb.wb.sp.hadb.utils.HADBDomainMember;
import com.sun.honeycomb.wb.sp.hadb.utils.Util;

public class ExtendDomain implements Script {
    public static final String SCRIPT_NAME="ExtendDomain";
    public static final String ARG_INCRMNT_SIZE="-"+SCRIPT_NAME + ".increment";
    public static final long NODE_JOIN_TIMEOUT = 60 * 60 * 1000; // 1 hour
    
    private int m_resultCode = Script.RETURN_CODE_INPROGRESS;
    private int m_clusterSize = 16;
    private int m_incrSize = 1;
    private ClusterMembership m_cluster = null;
    

    public void executeScript(Object caller) throws Throwable {
	// All my work is to be done post reboot, 
	// so the executeScript method does nothing 
	// the mains code is in the stop method
	
	// the only thing we need to do here is get a handle 
	// on the cmm object used by the test
	
	if (caller instanceof com.sun.honeycomb.hctest.cases.RebootTest) {
	  com.sun.honeycomb.hctest.cases.RebootTest test = 
	      (com.sun.honeycomb.hctest.cases.RebootTest) caller;
	  m_cluster = test.getCMMHandle();
	  
	} 
	
	return;
    }

    public String getDescription() {
	return null;
    }

    public OutputStream getErrorStream() {
	return null;
    }

    public String getName() {
	return SCRIPT_NAME;
    }

    public OutputStream getOutputStream() {
	return null;
    }

    public int getResult() {
	return m_resultCode;
    }

    public void setInputStream(InputStream is) {

    }

    public void setUp(LinkedList l) throws IllegalArgumentException {
	if (l == null) {
	    Log.INFO("The args object passsed to setup is null.");
	    return;
	}
	
	while (!l.isEmpty()) {
	    if (!((String)l.peek()).startsWith("-"+SCRIPT_NAME + ".") ) {
		return;
	    }
	    String curArg=(String)l.remove();
	    if (curArg.equals(ARG_INCRMNT_SIZE)) {
		if (l.isEmpty() || ((String)l.peek()).startsWith("-")) {
		    throw new IllegalArgumentException("You must specify the " +
		    		"domain extension increment size  with " +
		    		"the argument " + curArg);
		} else {
		    String num = (String)l.remove();
		    try {
		    	m_incrSize = Integer.parseInt(num);
		    } catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(num + " Is not a " +
					"number. You must specify a " +
					"size with " + curArg);
		    }
		}
	    } else {
		throw new IllegalArgumentException("The Script " + SCRIPT_NAME + 
			" does not recognize the argument "+ curArg);
	    }
	}
	
	
	if (m_incrSize <= 0 || m_incrSize > m_clusterSize) {
	    throw new IllegalArgumentException("You specified an invalid " +
		    "increment size. The value must be between 1 and " 
		    + m_clusterSize);
	}

    }

    
    public boolean stop() throws Throwable {
	if (m_cluster == null) {
		m_cluster = new ClusterMembership(-1, 
		HoneycombTestConstants.ADMIN_VIP);
		m_cluster.setQuorum(true);
		m_cluster.initClusterState();
	} else {
	    m_cluster.updateClusterState();
	}
	m_clusterSize = m_cluster.getNumNodes();
	HashSet nodesWaitingToJoin= new HashSet();
	HashSet disabledNodes = new HashSet();
	boolean succeeded = true;
	Iterator disabledIter = null;
	
	
	// generate the set of nodes who are down or
	// waiting to be added to the domain
	for (int i = 1; i <= m_clusterSize; i++) {
	    String curNode = "hcb" + (100 + i);
	    HADBDomainMember curMember = Util.getDomainMember(curNode);
	    if (curMember == null) {
		ClusterNode clstrNode = m_cluster.getNode(curNode);
		if (!clstrNode.isAlive()) {
		    disabledNodes.add(clstrNode);
		    Log.INFO("Adding " + clstrNode.getName() + 
			    " to list of disabled nodes");
		} else {
		    nodesWaitingToJoin.add(clstrNode);
		    Log.INFO("Adding " + clstrNode.getName() + 
		    " to list of nodes waiting to join domain");
		}
	    }
	}
	
	
	if (disabledNodes.isEmpty() && nodesWaitingToJoin.isEmpty()) {
	    throw new IllegalStateException ("All nodes currently in the " +
	    		"domain. Extend domain has nothing to do.");
	}
	
	disabledIter = disabledNodes.iterator();
	
	
	while (true) {
	    int numWaiting = nodesWaitingToJoin.size();
	    int exptdJoin = numWaiting - (numWaiting % 2);
	    
	    Log.INFO("There are " + numWaiting + " nodes waiting to join.");
	    Log.INFO("Of the available " + numWaiting + 
		    " I expect " + exptdJoin + " to join.");
	    
	    // am I waiting for some nodes to be added
	    if (numWaiting > 0) {
		int nodesJoined = 0;
		long timeout = System.currentTimeMillis() + NODE_JOIN_TIMEOUT;
		while (System.currentTimeMillis() < timeout && 
			exptdJoin != nodesJoined ) {
		    Iterator joinIter = nodesWaitingToJoin.iterator();
		    while (joinIter.hasNext()) {
		    	ClusterNode curNode = (ClusterNode)joinIter.next();
		    	HADBDomainMember dm=Util.getDomainMember(curNode.getName());
		    	if (dm != null && dm.isEnabled() && dm.isRunning() &&
			    Util.nodeIsInDatabase(curNode.getName())) {
				Log.INFO("Node " + dm.getHostName() + 
					" has rejoined");
				nodesJoined++;
				joinIter.remove();
				curNode.rebootDone(); 
		    	} else {
				Log.INFO("Node " + curNode.getName() + 
				 " has not rejoined");
		    	}
		    }
		    Util.sleeper(30 * 1000);
		}
		if (exptdJoin != nodesJoined) {
		    Log.ERROR("Expected " + exptdJoin + " nodes to join the " +
		    		"domain. But only " + nodesJoined + " joined.");
		    succeeded = false;
		    break;
		} else {
		    Log.INFO("As Expected " + exptdJoin + " nodes joined.");
		}
	    }
	    
	    
	    // do I have nodes to reboot, the reboot upto increment # nodes
	   Log.INFO("About to restart " + m_incrSize + " nodes.");
	   int index=0;
	   while (disabledIter.hasNext() && index < m_incrSize) {
	       ClusterNode cn = (ClusterNode) disabledIter.next();
	       Log.INFO("about to restart " + cn.getName());
	       if (!cn.ping()) {
		    throw new IllegalStateException ("Can't ping " + 
			    cn.getName() + " will not able to restart it");
		}
	       cn.removeDevMode();
		try {
		    cn.callHCStop();
		} catch (HoneycombTestException hte) {};
		disabledIter.remove();
	       nodesWaitingToJoin.add(cn);
	       index++;
	   }
	   
	    // if I have no nodes to reboot and all nodes are added
	    // exit
	    if (nodesWaitingToJoin.isEmpty() && disabledNodes.isEmpty()) {
		break;
	    }
	}

	return succeeded;
    }
    
    public static void main (String [] args) 
    throws Throwable {
	TestBed.getInstance().spIP="localhost";
	TestBed.getInstance().spIPaddr="10.123.45.100";
	ExtendDomain me = new ExtendDomain();
	me.m_incrSize = 1;
	Log.global.level=Log.DEBUG_LEVEL;
	try {
	me.executeScript(null);
	if (!me.stop()) {
	    Log.INFO("FAILED");
	} else {
	    Log.INFO("PASSED");
	}
	} catch (Throwable t) {
	    System.err.println(t.getMessage());
	    t.printStackTrace();
	}
    }

}
