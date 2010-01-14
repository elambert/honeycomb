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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.sun.hadb.adminapi.HADBException;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.util.ClusterMembership;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RandomUtil;
import com.sun.honeycomb.test.util.RunCommand;
import com.sun.honeycomb.wb.sp.hadb.scripts.Script;
import com.sun.honeycomb.wb.sp.hadb.utils.ManagementConfig;
import com.sun.honeycomb.wb.sp.hadb.utils.Util;

public class DisableHADBDisk implements Script {

    public static final String SCRIPT_NAME="DisableHADBDisk";
    public static final String ARG_NUM_NODES="-"+SCRIPT_NAME + ".numberOfNodes";
    public static final String ARG_NUM_DISKS="-"+SCRIPT_NAME + ".numberOfDisks";
    public static final String ARG_WAIT_TIME="-"+SCRIPT_NAME + ".waitTime";
    public static final String ARG_HADB_WAIT_TIME="-"+SCRIPT_NAME + 
    						".hadbWaitTime";
    public static final String ARG_NUM_NONHADB_NODES="-"+SCRIPT_NAME + 
    						".numberOfNonHadbNodes";
    public static final int DISKS_PER_NODE = 4;
    public static final long POST_DISABLE_SLEEP = 3 * 60 * 1000; // 1 min
    public static final long WAIT_FOR_HADB_MOVE_SLEEP = 1 * 60 * 1000; // 1 mn
    public static final long WAIT_FOR_NODE_IN_DB  = 4 * 60 * 60 * 1000; //4hr
    
    private int m_nodesToDisableHADBDisk=0;
    private int m_nodesToDisableNonHADBDisk=0;
    private int m_disksToDisable=1;
    private boolean m_useSpares = false;
    private boolean m_useMirrors = false;
    private boolean m_reuseNode = false;
    private int m_clusterSize = 16;
    private Set m_nodeSet = new HashSet();
    private long m_waitTime = 25 * 60 * 1000; //20 min
    private int m_result = Script.RETURN_CODE_INPROGRESS;
    private static final RunCommand m_rc = new RunCommand();
    private ClusterMembership m_cluster = null;
    private long m_inDbTimeout = WAIT_FOR_NODE_IN_DB;
    
    
    public void executeScript(Object caller) throws Throwable {
	m_cluster = new ClusterMembership(-1, HoneycombTestConstants.ADMIN_VIP);
	m_cluster.setQuorum(true);
	m_cluster.initClusterState();
	m_clusterSize = m_cluster.getNumNodes();
	Set targetedNodes = new HashSet ();
	Iterator iter = null;
	int totalNodes = m_nodesToDisableHADBDisk + m_nodesToDisableNonHADBDisk;
	// pick a node 
	m_nodeSet = Utils.getNodesToDisable(m_nodeSet, m_useSpares,
					    m_useMirrors, m_reuseNode, 
					    totalNodes, m_clusterSize,
					    m_cluster);
	
	// create the sets of targeted nodes
	iter = m_nodeSet.iterator();
	while (iter.hasNext()) {
	    ClusterNode curNode = (ClusterNode)iter.next();
	    targetedNode tn = new targetedNode (curNode, m_disksToDisable);
	    targetedNodes.add(tn);
	}
	
	// disable the nodes
	int hadbNodes = 0;
	iter = targetedNodes.iterator();
	while (iter.hasNext()) {
	    targetedNode curtn = (targetedNode)iter.next();
	    curtn.disableDisks(hadbNodes++ < m_nodesToDisableHADBDisk);
	}

	
	// sleep
	Util.sleeper(POST_DISABLE_SLEEP);
	
	// verify that nodes are disabled
	iter = targetedNodes.iterator();
	while (iter.hasNext()) {
	    targetedNode curtn = (targetedNode)iter.next();
	    boolean disabled = curtn.AllDisksDisabled();
	    if (!disabled) {
		Log.ERROR("Failed to disable one or more disks on " 
			+ curtn._nodeName + ". Failing test." );
		m_result = Script.RETURN_CODE_FAILED;
		return;
	    }
	}
	
	// verify that hadb nodes have moved to new disk and are in db	
	boolean allNodesMoved = false;
	long timeOut = System.currentTimeMillis() + m_waitTime;
	while (System.currentTimeMillis() < timeOut &&
		m_nodesToDisableHADBDisk > 0) {
	    allNodesMoved = true;
	    iter = targetedNodes.iterator();
	    while (iter.hasNext()) {
		targetedNode ctn = (targetedNode)iter.next();
		if (!ctn._targetHadbDisk) {
		    continue;
		}
		if (!ctn.hasHadbMoved()) {
		    Log.INFO(ctn._nodeName+" has not moved hadb to a new disk");
		    allNodesMoved = false;
		}
	    }
	    if (allNodesMoved) {
		Log.INFO("All nodes with hadb targeted disks have moved");
		break;
	    }
	    Log.INFO("Sleeping on hadb disk move");
	    Util.sleeper(WAIT_FOR_HADB_MOVE_SLEEP);
	}
	if (m_nodesToDisableHADBDisk > 0 && !allNodesMoved) {
	    Log.WARN("One or more nodes did not move hadb to a new disk");
	    m_result = Script.RETURN_CODE_FAILED;
	}
	
	if (m_nodesToDisableHADBDisk > 0) {
		long nodesInDBTimeOut = System.currentTimeMillis()+m_inDbTimeout;
		boolean allInDb = false;
		while (System.currentTimeMillis() < nodesInDBTimeOut && 
			!allInDb) {
	    		iter = targetedNodes.iterator();
	    		allInDb = true;
	    		while (iter.hasNext()) {
				targetedNode ctn = (targetedNode)iter.next();
				if (ctn._targetHadbDisk) {
		    			if (!ctn.isNodeInDatabase()) {
						Log.WARN(ctn._nodeName + " is" +
						" still not in the database");
						allInDb = false;
		    			}
				}
	    		}
	    		Util.sleeper(60 * 1000);
		}
		if (allInDb) {
		    Log.INFO("All nodes with disabled hadb disks are in " +
		    		"the database");
		} else {
		    Log.ERROR("Some nodes with disabled hadb disks are not " +
	    		"the database");
		    m_result = Script.RETURN_CODE_FAILED;
		}
	}
	
	// enable disk
	iter = targetedNodes.iterator();
	while (iter.hasNext()) {
	    targetedNode curtn = (targetedNode)iter.next();
	    curtn.enableDisk();
	}
	
	
	//sleep
	Log.INFO("Sleeping will disks come back on line");
	Util.sleeper(POST_DISABLE_SLEEP);
	
	// verify disks are enabled
	iter = targetedNodes.iterator();
	while (iter.hasNext()) {
	    targetedNode curtn = (targetedNode)iter.next();
	    if (!curtn.AllDisksEnabled()) {
		Log.ERROR("Failed to enable one or more disks on " 
			+ curtn._nodeName + ". Failing test." );
		m_result = Script.RETURN_CODE_FAILED;
		return;
	    }
	}
	if (m_result != RETURN_CODE_FAILED) {
		m_result = RETURN_CODE_PASSED;
	}
	
    }
    
   
    public String getDescription() {
	return "";
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
	return m_result;
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
	    if (curArg.equals(ARG_NUM_NODES)) {
		if (l.isEmpty() || ((String)l.peek()).startsWith("-")) {
		    throw new IllegalArgumentException("You must specify the " +
		              "number of nodes upon which you want to disable "+
		              "the hadb disk with the argument " + curArg);
		} else {
		    String num = (String)l.remove();
		    try {
		    	m_nodesToDisableHADBDisk = Integer.parseInt(num);
		    } catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(num + 
				" Is not a number. You must specify a number " +
				"of nodes with the argument " + curArg);
		    }
		}
	    } else if (curArg.equals(ARG_NUM_DISKS)) {
		if (l.isEmpty() || ((String)l.peek()).startsWith("-")) {
		    throw new IllegalArgumentException("You must specify the " +
		              "number of disks you want to disable on each "+
		              "node with the argument " + curArg);
		} else {
		    String num = (String)l.remove();
		    try {
		    	m_disksToDisable = Integer.parseInt(num);
		    } catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(num + 
				" Is not a number. You must specify a number " +
				"of disks with the argument " + curArg);
		    }
		}
	    } else if (curArg.equals(ARG_WAIT_TIME)) {
		if (l.isEmpty() || ((String)l.peek()).startsWith("-")) {
		    throw new IllegalArgumentException("You must specify the " +
		    		"wait time with the argument " + curArg);
		} else {
		    String num = (String)l.remove();
		    try {
		    	m_waitTime = Integer.parseInt(num) * 60 * 1000;
		    } catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(num + " Is not a " +
					"number. You must specify the number " +
					"of minutes with argument " + curArg);
		    }
		}
	    } else if (curArg.equals(ARG_HADB_WAIT_TIME)) {
		if (l.isEmpty() || ((String)l.peek()).startsWith("-")) {
		    throw new IllegalArgumentException("You must specify the " +
		    		"hadb wait time with the argument " + curArg);
		} else {
		    String num = (String)l.remove();
		    try {
		    	m_inDbTimeout = Integer.parseInt(num) * 60 * 1000;
		    } catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(num + " Is not a " +
					"number. You must specify the number " +
					"of minutes with argument " + curArg);
		    }
		}
	    } else if (curArg.equals(ARG_NUM_NONHADB_NODES)) {
		if (l.isEmpty() || ((String)l.peek()).startsWith("-")) {
		    throw new IllegalArgumentException("You must specify the " +
			    "number of nodes upon which you want to disable "+
		              "non hadb disks with the argument " + curArg);
		} else {
		    String num = (String)l.remove();
		    try {
		    	m_nodesToDisableNonHADBDisk = Integer.parseInt(num);
		    } catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(num + " Is not a " +
					"number. You must specify a number " +
					"of nodes with " + curArg);
		    }
		}
	    } else {
		throw new IllegalArgumentException("The Script " + SCRIPT_NAME + 
			" does not recognize the argument "+ curArg);
	    }
	}

    }

    public boolean stop() throws Throwable {
	return true;
    }
    
    private int getDiskNumber (String path) {
	String disk = "";
	if (!path.startsWith("/data/")) {
	    throw new IllegalStateException("Do not understand path " +
	    		"to db device:" + path);
	} else {
	    try {
		disk = path.substring(6,7);
		return Integer.parseInt(disk);
	    } catch (NumberFormatException nfe) {
		throw new IllegalStateException("Disk number " + 
			disk + " is not a number");
	    }
	}
    }
    
    private String getNodeNum(String name) {
	return name.substring(3);
    }
    
    
    class  targetedNode {
	String _nodeName ="";
	boolean _targetHadbDisk = false;
	int [] _disabledDisks = null;
	boolean _hasHadbMoved = false;
	ClusterNode _cn = null;
	String _originalHadbPath = "";
	String _nodeNum = "";
	int _numDiskToDisable = 0;
	
	targetedNode (ClusterNode cn, int numDiskToDisable) {
	    _cn = cn;
	    _nodeName = _cn.getName();
	    _nodeNum = getNodeNum(_nodeName);
	    _numDiskToDisable = numDiskToDisable;
	    _disabledDisks = new int [_numDiskToDisable];
	}
	
	void disableDisks(boolean targetHadbDisk) throws HoneycombTestException{
	    _targetHadbDisk = targetHadbDisk;
	    // find out where hadb is 
	    ManagementConfig mc = new ManagementConfig(_nodeName);
	    try {
	    	mc.load();
	    } catch (IOException ioe) {
		throw new HoneycombTestException("Unable to retrieve HADB" +
				" management file. Receieved IO Exception: " 
				+ ioe.getMessage());
	    }
	    _originalHadbPath = mc.getDatabaseDevicePath();
	    
	    
	    // get a disks to disable
	    int hadbDisk = getDiskNumber(_originalHadbPath);
	    for (int curDisk = 0; curDisk < _numDiskToDisable; curDisk++) {
		int disk = hadbDisk;
		if (! _targetHadbDisk || curDisk != 0) {
		    disk=getUniqueNonHadbDisk(hadbDisk);
		}
		_disabledDisks[curDisk] = disk;
	    }
	    
	    // disable it
	    for (int i=0; i < _numDiskToDisable; i++) {
	    	Log.INFO("disabling disk "+_nodeNum+":" + _disabledDisks[i]);
	    	m_rc.sshCmd("admin@" + HoneycombTestConstants.ADMIN_VIP, 
		     	"hwcfg -F -D DISK-"+_nodeNum+":"+_disabledDisks[i]);
	    }
	    
	}
	
	
	boolean hasHadbMoved () throws HoneycombTestException {
	    ManagementConfig mc = new ManagementConfig(_nodeName);
	    try {
	    	mc.load();
	    } catch (IOException ioe) {
		throw new HoneycombTestException("Unable to retrieve HADB" +
				" management file. Receieved IO Exception: " 
				+ ioe.getMessage());
	    }
	    if (mc.getDatabaseDevicePath().equals(_originalHadbPath)) {
		return false;
	    }
	    return true;
	}
	
	
	boolean isNodeInDatabase() throws HoneycombTestException {
	    try {
	    	return Util.nodeIsInDatabase(_nodeName);
	    } catch (HADBException he) {
		throw new HoneycombTestException("Received and HADBException" +
				" while checking if " + _nodeName + 
				" is in the database: " + he.getMessage());
	    }
	}
	
	void enableDisk()  throws HoneycombTestException {
	    for (int i=0; i < _numDiskToDisable; i++) {
	    	Log.INFO("enabling disk " + _nodeNum + ":" + _disabledDisks[i]);
	    	m_rc.sshCmd("admin@" + HoneycombTestConstants.ADMIN_VIP, 
		     	"hwcfg -F -E DISK-"+_nodeNum+":"+_disabledDisks[i]);
	    }
	}
	
	int getUniqueNonHadbDisk(int hadbDisk) throws HoneycombTestException {
	    int val = -1;
	    while (true) {
	    	val = RandomUtil.randIndex(DISKS_PER_NODE);
	    	if (val == hadbDisk ) {
	    	    val = ++val % DISKS_PER_NODE;
	    	}
	    	boolean unique = true;
	    	for (int i =0; i < _disabledDisks.length;i++) {
	    	    if (val == _disabledDisks[i]) {
	    		unique = false;
	    		break;
	    	    } 
	    	}
	    	if (unique) {
	    	    return val;
	    	} 
	    	continue;
	    }
	    
	}
	
	boolean AllDisksDisabled() throws HoneycombTestException {
	    boolean allDisabled = true;
	    for (int i = 0; i < _numDiskToDisable; i++) {
		if (!checkDiskState(_disabledDisks[i], "DISABLED")) {
		    Log.INFO("DISK-" + _nodeNum +":" + _disabledDisks[i] + 
			    " is not disabled");
		    allDisabled = false;
		}
	    }
	    return allDisabled;
	}
	
	boolean AllDisksEnabled() throws HoneycombTestException {
	    boolean allEnabled = true;
	    for (int i = 0; i < _numDiskToDisable; i++) {
		if (!checkDiskState(_disabledDisks[i], "ENABLED")) {
		    Log.INFO("DISK-" + _nodeNum +":" + _disabledDisks[i] + 
			    " is not enabled");
		    allEnabled = false;
		}
	    }
	    return allEnabled;
	}
	
	boolean checkDiskState(int diskNum, String state) 
	throws HoneycombTestException{
	    String diskID = "DISK-"+_nodeNum+":" + diskNum;
	    Log.DEBUG("Checking disk " + diskID + " for " + state);
	    ExitStatus ex = null;
	    int retries = 0;
	    while (ex == null) {
		try {
	    		ex = m_rc.sshCmdStructuredOutput("admin@" 
	    			+ HoneycombTestConstants.ADMIN_VIP, "hwstat");
		}
	    	catch (HoneycombTestException he) {
	    	    Log.WARN("Caught an exception while attempting to get " +
	    	    		"status of " + diskID);
	    	    Log.WARN(he.getMessage());
	    	    if (++retries == 3) {
	    		Log.ERROR("GIVING UP!");
	    		return false;
	    	    } else {
	    		Log.WARN("Will try again!");
	    	    }
	    	}
	    }
	    List hwstat = ex.getOutStrings();
	    Iterator iter = hwstat.iterator();
	    while (iter.hasNext()) {
		String curLine = (String)iter.next();
		curLine = curLine.trim();
		if (curLine.startsWith(diskID)) {
		    if (curLine.endsWith(state)) {
			Log.DEBUG("Disk " + diskID + " is in state " + state);
			Log.DEBUG(curLine + ".");
			return true;
		    } else {
			Log.ERROR(diskID + " is not " + state);
			Log.ERROR(curLine);
			return false;
		    }
		}
	    }
	    Log.ERROR("Was unable to find status for " + diskID);
	    return false;
	    
	}
    }
    
    private static void runTestCases (String tests [] )  throws Throwable {
	DisableHADBDisk me = null;
	for (int i = 0; i < tests.length; i++) {
	    String currentTest = tests[0];
	    if (currentTest.equalsIgnoreCase("testcase1")) {
		me = new DisableHADBDisk();
		Log.INFO("Starting Testcase1: disable 1 non-hadb disk on 1 node");
		me.m_disksToDisable = 1;
		me.m_nodesToDisableNonHADBDisk = 1;
		me.executeScript(null);
		if (me.getResult() != Script.RETURN_CODE_PASSED) {
		    Log.ERROR("TEST CASE #1: FAILED");
		    System.exit(1);
		} else {
		    Log.INFO("TEST CASE #1: PASSED");
		}
		
	    } else if (currentTest.equalsIgnoreCase("testcase2")) {
		me = new DisableHADBDisk();
		Log.INFO("Starting Testcase2: disable 1 non-hadb disk on 2 nodes");
		me.m_disksToDisable = 1;
		me.m_nodesToDisableNonHADBDisk = 2;
		me.executeScript(null);
		if (me.getResult() != Script.RETURN_CODE_PASSED) {
		    Log.ERROR("TEST CASE #2: FAILED");
		    System.exit(1);
		} else {
		    Log.INFO("TEST CASE #2: PASSED");
		}
	    } else if (currentTest.equalsIgnoreCase("testcase3")) {
		me = new DisableHADBDisk();
		Log.INFO("Starting Testcase3: disable 2 non-hadb disks on 1 node");
		me.m_disksToDisable = 2;
		me.m_nodesToDisableNonHADBDisk = 1;
		me.executeScript(null);
		if (me.getResult() != Script.RETURN_CODE_PASSED) {
		    Log.ERROR("TEST CASE #3: FAILED");
		    System.exit(1);
		} else {
		    Log.INFO("TEST CASE #3: PASSED");
		}
	    } else if (currentTest.equalsIgnoreCase("testcase4")) {
		me = new DisableHADBDisk();
		Log.INFO("Starting Testcase4: disable 2 disks (hadn and non) on 1 node");
		me.m_disksToDisable = 2;
		me.m_nodesToDisableHADBDisk = 1;
		me.executeScript(null);
		if (me.getResult() != Script.RETURN_CODE_PASSED) {
		    Log.ERROR("TEST CASE #4: FAILED");
		    System.exit(1);
		} else {
		    Log.INFO("TEST CASE #4: PASSED");
		}
	    } else if (currentTest.equalsIgnoreCase("testcase5")) {
		me = new DisableHADBDisk();
		Log.INFO("Starting Testcase5: 1 disks on 2 nodes (one hadb, one not)");
		me.m_disksToDisable = 1;
		me.m_nodesToDisableHADBDisk = 1;
		me.m_nodesToDisableNonHADBDisk = 1;
		me.executeScript(null);
		if (me.getResult() != Script.RETURN_CODE_PASSED) {
		    Log.ERROR("TEST CASE #5: FAILED");
		    System.exit(1);
		} else {
		    Log.INFO("TEST CASE #5: PASSED");
		}
	    } else {
		Log.ERROR("Unknown testcase: " + currentTest);
	    }
	    
	}
    }
    
    public static void main (String [] args) 
    throws Throwable {
	TestBed.getInstance().spIP="localhost";
	TestBed.getInstance().spIPaddr="10.123.45.100";
	Log.global.level=Log.DEBUG_LEVEL;
	String [] defaultTestcases = {"testcase1","testcase2","testcase3",
		"testcase4","testcase5"};
	if (args.length == 0) {
	    runTestCases(defaultTestcases);
	} else {
	    runTestCases(args);
	}

	
	
    }
    
}
