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



package com.sun.honeycomb.wb.sp.hadb.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.sun.hadb.adminapi.Database;
import com.sun.hadb.adminapi.DomainMember;
import com.sun.hadb.adminapi.HADBException;
import com.sun.hadb.adminapi.LostConnectionException;
import com.sun.hadb.adminapi.MAConnection;
import com.sun.hadb.adminapi.MAConnectionFactory;
import com.sun.hadb.adminapi.MANotReadyException;
import com.sun.hadb.adminapi.ManagementDomain;
import com.sun.hadb.adminapi.Node;
import com.sun.hadb.adminapi.OperationMonitor;
import com.sun.hadb.adminapi.OperationState;
import com.sun.hadb.mgt.NodeRole;
import com.sun.hadb.mgt.NodeState;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RunCommand;


public class Util {
    
    public static final String HADB_ADMIN_USER="admin";
    public static final String HADB_ADMIN_PASSWD="admin";
    public static final String HADB_DB_NAME="honeycomb";
    public static final String ADMIN_VIP="10.123.45.200";
    public static final int HADB_DEFAULT_PORT=1862;
    public static final String HADB_DEFAULT_ADMIN_IP= ADMIN_VIP;
    public static final String HADB_DEFAULT_ADMIN_URL=HADB_DEFAULT_ADMIN_IP + 
                               ":" + HADB_DEFAULT_PORT;
    public static final int MAX_CONNECTION_RETRIES=5;
    public static final int DATABASE_FILE_TYPE = 0;
    public static final int RELALG_FILE_TYPE = 1;
    
    private static final RunCommand m_rc = new RunCommand();
 
    public static boolean m_testMode = false;
    
    static {
	if (System.getProperty("testmode") != null) {
	    m_testMode = true;
	}
    }
    
    public static boolean getHADBHistory(String URL, String dest) 
    throws IllegalArgumentException, HADBException, IOException {
	return getHADBHistory(URL, null, null, null, dest, null);
    }
    
   
    public static boolean getHADBHistory(String URL, String database, 
	    				   String passwd, String user, 
	    				   String destination, PrintStream ps)
    throws IllegalArgumentException, HADBException, IOException {
	
	MAConnection mac = null;
	boolean hadbWorked = true;
	if (URL == null) {
	    throw new IllegalArgumentException ("You must specify a URL");
	}
	
	if (destination == null) {
	    throw new IllegalArgumentException ("You must specify a log file" +
	    				        " destination directory");
	}
	
	if (database == null) {
	    database = HADB_DB_NAME;
	}
	
	if (passwd == null) {
	    passwd = HADB_ADMIN_PASSWD;
	}
	
	if (user == null ) {
	    user = HADB_ADMIN_USER;
	}
	
	if (ps == null) {
	    ps = System.out;
	}
	
	try {
	    mac = MAConnectionFactory.connect(URL,user,passwd);
	    ManagementDomain md = mac.getDomain();
	    Database db = md.getDatabase(database);
	    ps.println("Retrieving Database History files");
	    OperationMonitor om = db.getHistoryFiles(destination);
	    while (om.getOperationState().equals(OperationState.active)) {
		ps.print("Progress: " + om.getProgressEstimate() + "%  ");
		sleeper(1000);
	    }
	    ps.println("");
	    if (om.getOperationState().equals(OperationState.failed)) {
		ps.println("Unable able to retrieve history files. " +
			   "Operation failed!");
		hadbWorked = false;
	    }
	    return hadbWorked;
	} finally {
	    if (mac != null) {
		mac.close();
	    }
	}
	
    }
    
    
    public static void copyHistoryFiles(int numNodes, String destDir) 
    throws IOException {
	
	for (int i = 0; i < numNodes; i++) {
	    String curNode = "hcb" + (101 + i);
	    ManagementConfig mc = new ManagementConfig(curNode);
	    System.err.println("Copying history log from " + curNode);
	    try {
		mc.load();
		m_rc.scpCmd(curNode+":"+mc.getDatabaseHistoryPath() +
			"/honeycomb.out.\\*", destDir);
	    } catch (IOException ioe) {
		System.err.println("Unable to access node " + curNode);
		System.err.println(ioe.getMessage());
		continue;
	    } catch (HoneycombTestException hte) {
		System.err.println("Unable to access node " + curNode);
		System.err.println(hte.getMessage());
		continue;
	    }
	}
	
    }
    

    public static void copyMALogFiles(int numNodes, String destDir) 
    throws IOException {
	
	for (int i = 0; i < numNodes; i++) {
	    String curNode = "hcb" + (101 + i);
	    ManagementConfig mc = new ManagementConfig(curNode);
	    System.err.println("Copying ma log from " + curNode);
	    try {
		mc.load();
		m_rc.scpCmd(curNode+":"+mc.getLogFilePath(), 
		    destDir + "/ma.log." + i);
	    } catch (IOException ioe) {
		System.err.println("Unable to access node " + curNode);
		System.err.println(ioe.getMessage());
		continue;
	    } catch (HoneycombTestException hte) {
		System.err.println("Unable to access node " + curNode);
		System.err.println(hte.getMessage());
		continue;
	    }
	}
    }
    
    
    public static void copyRepositoryDirs(int numNodes, String destDir) 
    throws IOException {
        for (int i = 0; i < numNodes; i++) {
	    String curNode = "hcb" + (101 + i);
	    ManagementConfig mc = new ManagementConfig(curNode);
	    System.err.println("Copying HADB repository from " + curNode);
	    try {
		mc.load();
		m_rc.scpCmd(curNode+":"+mc.getRepositoryPath(), 
		    destDir + "/repository." + i, true);
	    } catch (IOException ioe) {
		System.err.println("Unable to access node " + curNode);
		System.err.println(ioe.getMessage());
		continue;
	    } catch (HoneycombTestException hte) {
		System.err.println("Unable to access node " + curNode);
		System.err.println(hte.getMessage());
		continue;
	    }
	}
    }
    
    public static void logDomainStatus()  
    throws IOException, HADBException {
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	printDomainStatus(new PrintStream(bos));
	bos.flush();
	ByteArrayInputStream bs = new ByteArrayInputStream(bos.toByteArray());
	BufferedReader br = new BufferedReader(new InputStreamReader(bs));
	for (String s = br.readLine(); s != null; s = br.readLine()) {
	    Log.INFO(s);
	}
    }
    
    public static void printDomainStatus(PrintStream ps) 
    throws HADBException, IOException {
	printDomainStatus(ADMIN_VIP,HADB_DEFAULT_PORT,ps);
    }
    
    
    public static void printDomainStatus(String adminvip, 
	    				 int port, PrintStream ps) 
    throws HADBException, IOException {
	MAConnection mac = null;
	try {
	    mac = MAConnectionFactory.connect(adminvip +":"+port, 
					HADB_ADMIN_USER, HADB_ADMIN_PASSWD);
	    HADBDomain hd = new HADBDomain();
	    hd.initialize(mac.getDomain());
	    Iterator iter = hd.getDomainMembers().iterator();
	    while (iter.hasNext()) {
		HADBDomainMember curMem = (HADBDomainMember)iter.next();
		ps.println(curMem.toString());
	    }
	} catch (IllegalStateException ise) {
	    Log.WARN("Unable to print domain status");
	    Log.WARN(ise.getMessage());
	} finally {
	    if (mac != null)
		mac.close();
	}
    }
    
    
    public static void logDatabaseStatus()  
    throws IOException, HADBException {
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	printDatabaseStatus(new PrintStream(bos));
	bos.flush();
	ByteArrayInputStream bs = new ByteArrayInputStream(bos.toByteArray());
	BufferedReader br = new BufferedReader(new InputStreamReader(bs));
	for (String s = br.readLine(); s != null; s=br.readLine()) {
	    Log.INFO(s);
	}
    }
    
    public static void printDatabaseStatus(PrintStream ps) 
    throws HADBException, IOException {
	printDatabaseStatus(ADMIN_VIP,HADB_DEFAULT_PORT,ps);
    }
    
    
    public static void printDatabaseStatus(String adminvip, 
	    				   int port, PrintStream ps) 
    throws HADBException, IOException {
	MAConnection mac = null;
	try {
	    mac = MAConnectionFactory.connect(adminvip +":"+port, 
					HADB_ADMIN_USER, HADB_ADMIN_PASSWD);
	    HADBDatabase hd = new HADBDatabase();
	    hd.initialize(HADB_DB_NAME, mac.getDomain() );
	    ps.println(hd.toString());
	    Iterator iter = hd.getNodes().iterator();
	    while (iter.hasNext()) {
		HADBNode curNode = (HADBNode)iter.next();
		ps.println(curNode.toString());
	    }
	} catch (IllegalStateException ise) {
	    Log.WARN("Unable to  print database status.");
	    Log.WARN(ise.getMessage());
	} finally {
	    if (mac != null)
		mac.close();
	}
    }
    
    
    public static String getMirrorHostName(String nodeName) 
    throws HADBException {
	MAConnection mac = null;
	try {
	    mac = getDefaultMAConnection();
	    Node node = getNode(nodeName,mac);
	    if (node == null) {
		return null;
	    }
	    Node mirror = node.getMirrorNode();
	    if (mirror == null) {
		Log.WARN("Mirror node of  host " + nodeName + " is null.");
		return null;
	    }
	    DomainMember mb = mirror.getDomainMember(); 
	    if (mb == null) {
		Log.WARN("Unable to get handle on domain member of " 
			+ nodeName + "'s mirror. Operation returned null");
		return null;
	    }
	    return mb.getHostName();
	} finally {
	    if (mac != null)
		mac.close();
	}
    }
    
    
    public static boolean isSpare(String nodeName) 
    throws HADBException {
	MAConnection mac = null;
	try {
	    mac = getDefaultMAConnection();
	    Node node = getNode(nodeName,mac);
	    if (node == null) {
		return false;
	    }
	    return node.getNodeRole().equals(NodeRole.spare);
	} finally {
	    if (mac != null)
		mac.close();
	}
    }
    
    
    public static boolean nodeIsInDatabase(String name) 
    throws HADBException, HoneycombTestException {
	boolean status = true;
	MAConnection mac = null;
	int retries = 5;
	int retry = 1;
	while (retry <= retries) {
	    try {
	    	mac = getDefaultMAConnection();
	    	if (mac == null) {
			throw new HoneycombTestException("Was unable to " +
					"retreive connection to the database");
	    	}
	    	ManagementDomain md = mac.getDomain();
	    	if (md == null) {
			throw new HoneycombTestException("Was unable to " +
					"retreive management domain");
	    	}
	    	Database db = md.getDatabase(HADB_DB_NAME);
	    	if (db == null) {
			throw new HoneycombTestException("Was unable to " +
					"retreive database");
	    	}
	    	List nodes = db.getNodes();
	    	if (nodes == null) {
			throw new HoneycombTestException("Was unable to " +
					"retreive list of database nodes");
	    	}
	    	boolean foundNode = false;
	    	DomainMember dm = null;
	    	Node curNode = null;
	    	Iterator iter = nodes.iterator();
	    	while (iter.hasNext()) {
		     	curNode = (Node)iter.next();
			dm = curNode.getDomainMember();
			if (dm.getHostName().equals(name)) {
		    		foundNode = true;
		    		break;
			}
	    	}
	    	if (!foundNode || curNode == null) {
			Log.INFO("The node " + name + " doesnt appear in " +
					"the database");
			status = false;
	    	} else {
			NodeRole nr = curNode.getNodeRole();
			NodeState ns = curNode.getNodeState();
			if (!nr.equals(NodeRole.active) && 
			    !nr.equals(NodeRole.spare)) {
		    		Log.INFO("The node " + name + 
		    			" doesnt have an expected role. " +
		    			" Role is " + nr);
		    		status = false;
			} else {
		    		if (ns.equals(NodeState.stopped) ||
			    	ns.equals(NodeState.stopping) ||
			    	ns.equals(NodeState.halting) ||
			    	ns.equals(NodeState.unknown)) {
					Log.INFO("The node " + name + 
						" is an invalid state. " +
						"CurrentState is " + ns);
					status = false;
		    		} else {
					Log.INFO("The node " + name + 
					" is an active and valid participant " +
					"in the database");
					status = true;
		    		}
			}
	    	}
	    	break;
	    } catch (HADBException he) {
		String reason = "Unhandled exceptions";
	    	if ( he instanceof LostConnectionException ||
	    		he instanceof MANotReadyException) {
			if (++retry <= retries) {
		    		Log.WARN("Received an HADB Connection" +
		    			" exception " + he.getMessage() + 
		    			" while determining status of " 
			    		+ name);
		    		Log.WARN("Will establish a new connection " +
		    				"and try again.");
		    		sleeper(5000); //5 secs
		    		continue;
			}
			reason = "Exceed retries";
	    	}
	    	Log.WARN("Receieved an HADB Exception while attempting to " +
	    			"determine state of " + name);
	    	Log.WARN("Exception was: " + he.getMessage() + "(" + 
	    		he.getErrorCode() + ")");
	    	Log.WARN("Will not retry: " + reason);
		throw he;
	    } finally {
	    	if (mac != null) {
			mac.close();
	    	}
	    }
	}
	return status;
	
    }
    
    public static Node getNode(String name, MAConnection mac) 
    throws HADBException {
	Node curNode = null;
	 ManagementDomain md = mac.getDomain();
	 Iterator iter = md.getDatabase(HADB_DB_NAME).getNodes().iterator();
	 while (iter.hasNext()) {
		    curNode = (Node) iter.next();
		    if (curNode.getDomainMember().getHostName().equals(name)) {
			break;
		    }
	 }
	 return curNode;
    }
    
    public static MAConnection getDefaultMAConnection() 
    throws HADBException {
	
	MAConnection conn = null;
	long initialSleep = 5 * 1000; // 5 seconds
	int attemptNumber = 0;
	String hostIPPrefix="10.123.45.";
	int hostIPSuffix=200; //Start trying to talk to master node
	while (conn == null) {
	    try {
		conn = MAConnectionFactory.connect(hostIPPrefix + hostIPSuffix 
					  + ":" + HADB_DEFAULT_PORT,
		 			  HADB_ADMIN_USER, HADB_ADMIN_PASSWD);
	    } catch (HADBException hae) {
		if (attemptNumber++ < MAX_CONNECTION_RETRIES) {
		    long sleep = initialSleep * attemptNumber;
		    hostIPSuffix = 100 + attemptNumber;
		    Log.WARN("Attempt " + attemptNumber + 
			" failed to establish a connection to MA ");
		    Log.WARN("Will sleep for " + sleep + " and try again.");
		    Log.WARN("Will try to connect to " + hostIPPrefix 
			    + hostIPSuffix);
		    sleeper(sleep);
		} else {
		    Log.WARN("After "+ MAX_CONNECTION_RETRIES + 
		    " retries failed to get a connection to MA. Giving up!");
		    throw hae;
		}
	    
	    }
	}
	return conn;
	
    }
    
    /**
     * 
     * @param hostName
     * @return A snap shot domain member running on the specified host. 
     * Will return null if the domain does not include this host.
     * @throws HADBException
     */
    public static HADBDomainMember getDomainMember(String hostName) 
    throws HADBException{	
	HADBDomainMember foundIt = null;
	MAConnection mac = getDefaultMAConnection();
	try {
	    ManagementDomain md = mac.getDomain();
	    if (md == null) {
		return foundIt;
	    }
	    Iterator iter = md.getDomainMembers().iterator();
	    while (iter.hasNext()) {
		DomainMember curMember = (DomainMember)iter.next();
		if (curMember.getHostName().equals(hostName)) {
		    foundIt=new HADBDomainMember(curMember);
		    break;
		}	
	    }
	    return foundIt;
	} finally {
	    mac.close();
	}
    }
    
    
    public static Set getAllDomainMembers() 
    throws HADBException {
	HashSet members = new HashSet();
	MAConnection mac = getDefaultMAConnection();
	try {
	    ManagementDomain md = mac.getDomain();
	    Iterator iter = md.getDomainMembers().iterator();
	    while (iter.hasNext()) {
		DomainMember curMember = (DomainMember)iter.next();
		members.add(curMember);	
	    }
	} finally {
	    mac.close();
	}
	return members;
    }
    
    public static List getAllDBNodes() throws HADBException {
	List nodes = null;
	MAConnection mac = getDefaultMAConnection();
	try {
	    ManagementDomain md = mac.getDomain();
	    Database db = md.getDatabase(HADB_DB_NAME);
	    nodes = db.getNodes();
	} finally {
	    mac.close();
	}
	return nodes;
    }
    
    public static void sleeper(long sleepvalue) {
        try {
            Log.INFO("Begin sleep " + sleepvalue);
            Thread.currentThread().sleep(sleepvalue);
            Log.INFO("End sleep " + sleepvalue);
        } catch (InterruptedException ignored) {
        }
    }
    
    
    public static void wipeCluster (int size) 
    throws HoneycombTestException { 
	for (int i = 0; i < size; i++) {
	    String nodeName = "hcb" + (101 + i);
	    ManagementConfig mc = new ManagementConfig(nodeName);
	    try {
		mc.load();
	    } catch (IOException ioe) {
		throw new HoneycombTestException("IOexception while loading" +
				" management config for " + nodeName + 
				ioe.getMessage());
	    }
	    String dataDir = mc.getDatabaseDevicePath();
	    Log.INFO("Wiping HADB from " + nodeName + ":" + dataDir);
	    ExitStatus es = m_rc.sshCmdStructuredOutput(nodeName, 
		          				"rm -r " + dataDir);
	    if (es.getReturnCode() != 0) {
		Log.WARN("Attempt to remove " + dataDir + " on " + 
			nodeName + " returned non-zero exit code");
		Log.WARN(es.getOutputString(true));
	    }
	}
    }
    

   public static boolean waitForHadbWipe(long startTime, long waitTime) 
   throws HoneycombTestException{
       boolean detectedWipe = false;
       long timeoutValue = System.currentTimeMillis() + waitTime;
       HADBDatabase db = new HADBDatabase();
       try {
	   ManagementDomain md = getDefaultMAConnection().getDomain();
	   db.initialize(HADB_DB_NAME, md);
       } catch (HADBException he) {
	   HoneycombTestException hte = new HoneycombTestException(
		   "HADB Exception while connecting to database.:" + 
		   he.getMessage());
	   hte.initCause(he);
       }
       do {
	   Log.INFO("Checking for wipe: " + 
		   ((timeoutValue - System.currentTimeMillis())/1000) + 
		   " seconds left.");
	   if (m_testMode) {
	       sleeper(2 * 1000); //2 sec
	   } else {
	       sleeper(2 * 60 * 1000); //2 min
	   }
	   try {
	       detectedWipe = db.hasBeenWiped(startTime); 
	   } catch (IllegalStateException ise) {
	       throw new HoneycombTestException("Encountered an error " +
	       		"while checking for wipe" + ise.getMessage());
	   }
       } while (System.currentTimeMillis() <= timeoutValue && !detectedWipe); 
      
       return detectedWipe;
   }
  
   
   class SyslogFilter implements FilenameFilter {
       public boolean accept(File dir, String file) {
	   if (!dir.getAbsolutePath().equals("/var/adm")) {
	       return false;
	   }
	   
	   if (!file.startsWith("messages")) {
	       return false;
	   }
	   return true;
       }
   }
   
   private static void simulateNodeWipe(String dirName, int nodes) 
   throws IOException, HoneycombTestException {
       for (int i = 0; i < nodes; i++) {
	   String nodeId = "hcb" + (101 + i);
	   // get the path to hadb-logs dir
	   ManagementConfig mc = new ManagementConfig(nodeId);
	   mc.load();
	   String path = mc.getDatabaseDevicePath() + "/../hadb-logs/"+dirName;
	   
	   // mkdir
	   //SolarisNode sn = new SolarisNode(nodeId,"");
	   RunCommand shell = new RunCommand();
	   ExitStatus stat = shell.sshCmdStructuredOutput(nodeId,"mkdir -p " + path);
	   if (stat.getReturnCode() != 0) {
	       Log.ERROR(("Unable to create " + path + " on " + nodeId));
	       Log.ERROR(stat.getOutputString());
	   }
       }
   }
   
   public static void main (String [] args) {
       long startTime = System.currentTimeMillis();
       SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
       try {
	   
	   // test waitForWipe immediate (should not detect)
	   if (waitForHadbWipe(startTime, 0)) {
	       System.err.println("WipeDetection TestCase 1: FAILED");
	       System.err.println("-Detected a wipe when I should not have");
	   } else {
	       System.err.println("WipeDetection TestCase 1: PASSED");
	   }
	   
	   // test waitForWipe delayed (should not detect)
	   if (waitForHadbWipe(startTime,10000)) {
	       System.err.println("WipeDetection TestCase 2: FAILED");
	       System.err.println("-Detected a wipe when I should not have");
	   } else {
	       System.err.println("WipeDetection TestCase 2: PASSED");
	   }
	   
	   // test waitForWipe wipe dirs before startTime (should not detect)
	   // create a wipedir on all nodes before start time
	   Date preStartTime = new Date(startTime - 1000);
	   simulateNodeWipe(formatter.format(preStartTime), 16);
	   if (waitForHadbWipe(startTime,10000)) {
	       System.err.println("WipeDetection TestCase 3: FAILED");
	       System.err.println("-Detected a wipe when I should not have");
	   } else {
	       System.err.println("WipeDetection TestCase 3: PASSED");
	   }
	   
	   // test waitForWipe wipe lacks quorom (should not detect)
	   // create wipedir after start time on half of the nodes
	   Date postStartTime = new Date(startTime + 1000);
	   
	   //only 1 node
	   simulateNodeWipe(formatter.format(postStartTime), 1);
	   if (waitForHadbWipe(startTime,10000)) {
	       System.err.println("WipeDetection TestCase 4: FAILED");
	       System.err.println("-Detected a wipe when I should not have. " +
	       		"Only one node had wiped");
	       
	   } else {
	       System.err.println("WipeDetection TestCase 4: PASSED");
	   }
	   
	   //	 only 8 nodes
	   simulateNodeWipe(formatter.format(postStartTime), 8);
	   if (waitForHadbWipe(startTime,10000)) {
	       System.err.println("WipeDetection TestCase 5: FAILED");
	       System.err.println("-Detected a wipe when I should not have. " +
	       		"Only eight node had wiped");
	       
	   } else {
	       System.err.println("WipeDetection TestCase 5: PASSED");
	   } 
	   
	   // test waitForWipe has quorum (should detect)
	   // create wipedir after starttime on all nodes
	   simulateNodeWipe(formatter.format(postStartTime), 16);
	   if (!waitForHadbWipe(startTime,10000)) {
	       System.err.println("WipeDetection TestCase 6: FAILED");
	       System.err.println("-Wipe not detected when I should have. " +
	       		"All nodes had wiped");
	       
	   } else {
	       System.err.println("WipeDetection TestCase 6: PASSED");
	   }
	   
	   //no up the start time and do it again (should not detect)
	   if (waitForHadbWipe(startTime + 2000,10000)) {
	       System.err.println("WipeDetection TestCase 7: FAILED");
	       System.err.println("-Wipe not detected when I should have. " +
	       		"All nodes had wiped were wiped before start time");
	       
	   } else {
	       System.err.println("WipeDetection TestCase 7: PASSED");
	   }
	  
	   
       } catch (Throwable t) {
	   System.err.println(t.getMessage());
	   t.printStackTrace();
       }
   }
   /**
    * Wait for a given period of time for the cluster to reach a sane state. 
    * 
    * @param wait The amount of  time in milliseconds will wait for the 
    * database to be sane.
    * 
    * @param sleeptime If we detect the database is not sane, how much time 
    * in milliseconds, should we sleep before trying again.
    * 
    * @param clusterSize ClusterSize, 16 or 8
    * 
    * @param downedNodes Number of nodes currently not participating in the 
    * database but are in the domain 
    * 
    * @param missingNodes number of nodes that are not in the domain
    * 
    * @param checkWipe check to see if wipe occured while we were waiting 
    * for the database becoming sane, if so return false
    * 
    * @return true if the database achieved a sane state with in the
    * specified time, else false.
    */
   public static boolean waitForDBSanity(long wait, long sleeptime, 
	   int clusterSize, int downedNodes, 
	   int missingNodes, boolean checkWipe) {
       
       long wipeCheckPoint = System.currentTimeMillis();
       long timeOut = System.currentTimeMillis() + wait;
       boolean timedOut = false;
       while  (!timedOut) {
	   try {
	       if (isDBSane(clusterSize,downedNodes, missingNodes))
		   break;
	   } catch (HADBException he) {
	       Log.INFO("Having trouble checking sanity of db.");
	       Log.INFO("Received the following exception " + he.getMessage());
	       Log.INFO("Will try again later");
	   }
	   if (System.currentTimeMillis() > timeOut) {
	       timedOut = true;
	   } else {
	       Log.INFO("Database is not sane. " +
	       		"Will sleep for a bit and check again");
	       sleeper(sleeptime);
	   }
	   
       }
       if (checkWipe && !timedOut) {
	   try {
	       if (waitForHadbWipe(wipeCheckPoint, 0)) {
		   Log.INFO("Wipe was detected while we waited for database " +
	       		"to achieve sane state.");
		   timedOut = true;
	       }
	   } catch (HoneycombTestException hte) {
	       Log.WARN("Encountered an Exception while checking for wipes:");
	       Log.WARN(hte.getMessage());
	   }
       }
       
       
       return !timedOut;
   }
   
   /**
    * Check to see if the database is in a sane state. 
    * 
    * @param clusterSize ClusterSize, 16 or 8
    * 
    * @param downedNodes Number of nodes currently not participating in the 
    * cluster 
    * 
    * @param missingNodes number of nodes that are not in the domain
    * 
    * @return true if the database is currently in a sane state, else false.
    * 
    * @throws HADBException
    */
   public static boolean isDBSane(int clusterSize,int downedNodes, 
	   int missingNodes) 
   throws HADBException {
       
       int expectedDomainSize = clusterSize;
       int expectedRunningNodes = 0;
       int runningDBNodes = 0;
       int activeDBNodes = 0;
       int dru0SpareDBNodes = 0; 
       int dru1SpareDBNodes = 0;
       MAConnection conn = null;
       
       Log.INFO("Checking sanity of db:");
       Log.INFO("Sanity Check thinks cluster size is " + clusterSize);
       Log.INFO("Sanity Check thinks number of downedNodes is " + downedNodes);
       Log.INFO("Sanity Check thinks number of missingNodes is " +missingNodes);
       //Log.INFO("Sanity Check thinks we have wiped with missing nodes: "
	 //      + wiped);
       
       try {

	   conn = getDefaultMAConnection();
       if (conn == null) {
           Log.INFO("Couldn't get default MA connection");
           return false;
       }

	   ManagementDomain md = conn.getDomain();
       if (md == null) {
           Log.INFO("Couldn't get domain");
           return false;
       }

	   Database db = md.getDatabase(HADB_DB_NAME);
       if (db == null) {
           Log.INFO("Couldn't get db " + HADB_DB_NAME);
           return false;
       }

	   int domainSize = md.getDomainMembers().size();
	   expectedDomainSize = clusterSize-(missingNodes+(missingNodes%2));
	   expectedRunningNodes = expectedDomainSize - downedNodes;
	   
	   // check domain size
	   if (expectedDomainSize != domainSize) {
	       Log.INFO("Unexpected Domain Size! Expected: " + 
		       expectedDomainSize + " Reported Size: " +domainSize);
	       return false;
	   }

       Log.INFO("Domain size was " + domainSize + " as expected");
       
	   // If I am a 16 node cluster, I should always have 12 active nodes
	   // If I am an 8 node cluster, I should always have 6  active nodes
	   int expectedNumActive = (clusterSize == 16 ? 12 : 6);
	   
	   int expectedNumRunningSpares = domainSize - expectedNumActive - downedNodes;
	   
	   
	   
	   // iterate through the database nodes, taking note of  
	   // each nodes state and role. 
	   // If the node is a spare, make a note of which Disaster Recovery
	   // Unit (DRU) it is in. In the future, we can use this information
	   // to determine what state the database should be in 
	   Iterator iter = db.getNodes().iterator();
	   while (iter.hasNext()) {
	       Node curNode = (Node)iter.next();
	       if (curNode.getNodeState().equals(NodeState.running)) {
		   runningDBNodes++;
		   if (curNode.getNodeRole().equals(NodeRole.active)) {
		       activeDBNodes++;
		   } else {
		       if (curNode.getDruNumber() == 0) {
			   dru0SpareDBNodes++;
		       } else {
			   dru1SpareDBNodes++;
		       }
		   }
	       } else {
               Log.INFO("Found node in non-running state=" +
                   curNode.getNodeState() + " and role=" +
                   curNode.getNodeRole());
           }
	   }

	   // do I have the correct number of running nodes
	   if (runningDBNodes != expectedRunningNodes) {
	       Log.INFO("Unexpected number of running nodes. Expected: " +
		       expectedRunningNodes + " Reported: " + runningDBNodes);
           return false;
	   }

       Log.INFO("Number of running nodes was " + runningDBNodes +
           " as expected");
       
	   // do I have the correct number of active nodes?
	   if(activeDBNodes != expectedNumActive) {
	       Log.INFO("Unexpected number of active db nodes! Expected: " 
		   + expectedNumActive + " Reported Num: " + activeDBNodes);
	       return false;
	   }

       Log.INFO("Number of active db nodes was " + activeDBNodes +
           " as expected");
       
	   // do I have the correct number of spare nodes?
	   int totalNumOfSpares = dru0SpareDBNodes + dru1SpareDBNodes;
	   if(totalNumOfSpares != expectedNumRunningSpares) {
	       Log.INFO("Unexpected number of spare db nodes! Expected: " 
		   + expectedNumRunningSpares + 
		   " Reported Num: " + totalNumOfSpares);
	       return false;
	   }  
	   
       Log.INFO("Number of spares was " + totalNumOfSpares + 
           " as expected");

       } finally {
	   if (conn != null) {
	       try {
		   conn.close();
	       } catch (Exception ignored) {}
	   }
       }
     
       return true;
   }
   
   public static String getHADBFileSize(String host, int fileType) {
       String size = "unknown";
       String fileName="";
       String filePath = "";
       ManagementConfig mc = new ManagementConfig(host);
       if (fileType == DATABASE_FILE_TYPE) {
	   fileName = "honeycomb.data-";
       } else if (fileType == RELALG_FILE_TYPE) {
	   fileName = "honeycomb.relalg";
       } else {
	   Log.WARN("Unkown file type: " + fileType);
	   return size;
       }
       
      
       
       try {
	   mc.load();
	   filePath = mc.getDatabaseDevicePath();
	   ExitStatus ex = m_rc.sshCmdStructuredOutput(host, "ls -ld "+filePath + 
		   "/" + fileName + "\\*");
	   if (ex.getReturnCode() == 0) {
	       String lsout = ex.getOutputString();
	       lsout.trim();
	       String [] lstokens = lsout.split("\\s+");
	       size = lstokens[4];
	   } else {
	       Log.WARN("The attempt to ls the file " + fileName + " on node " +
		       host + " failed!");
	       Log.WARN(ex.getOutputString(true));
	   }
	   
       } catch (IOException ioe) {
	   Log.WARN("Recieved an IOException while attempting to ls the file "+
	   		fileName + " on " + host);
	   Log.WARN(ioe.getMessage());
	   
       } catch (HoneycombTestException hte) {
	   Log.WARN("Recieved a TestException while attempting to ls the file "+
	   		fileName + " on " + host);
	   Log.WARN(hte.getMessage());
       }
       
       return size;
   }
   
   public static void testConnection () {
       try {
	   Log.INFO("Establishing connection");
	   MAConnection myconn = null;
	   myconn = MAConnectionFactory.connect("10.123.45.101:"+HADB_DEFAULT_PORT, HADB_ADMIN_USER,HADB_ADMIN_PASSWD);
	   Log.INFO("Getting handle on db structures");
	   ManagementDomain md = myconn.getDomain();
	   Database db = md.getDatabase(HADB_DB_NAME);
	   Node node = (Node)db.getNodes().get(0);
	   
	   //do work
	   Log.INFO("Printing DB State:");
	   printDomainMembers(md);
	   printDataBase(db);
	   printNodeState(node);
	   
	   //re-establish new connection
	   Log.INFO("Closing connection and establishing new one");
	   myconn.close();
	   myconn = MAConnectionFactory.connect("10.123.45.103:"+HADB_DEFAULT_PORT, HADB_ADMIN_USER,HADB_ADMIN_PASSWD);
	   
	   //do work again
	   Log.INFO("Printing DB State:");
	   printDomainMembers(md);
	   printDataBase(db);
	   printNodeState(node);
	   
	   Log.INFO("Closing connection for good");
	   myconn.close();
	   
       } catch (HADBException he) {
	   Log.INFO("Caught hadb exception: " + he.getMessage());
	   he.printStackTrace();   
       }
       
   }
   
   private static void printDomainMembers(ManagementDomain md) throws HADBException {
       Set members = md.getDomainMembers();
       Iterator iter = members.iterator();
       Log.INFO("DOMAIN MEMBERS ARE:");
       while (iter.hasNext()) {
	   Log.INFO(((DomainMember)iter.next()).getHostName());
       }
   }
   
   private static void printDataBase(Database db) throws HADBException {
       Log.INFO("State of Database is: " + db.getDatabaseState().toString());
       List list = db.getNodes();
       Iterator iter = list.iterator();
       Log.INFO("NODE LIST:");
       while (iter.hasNext()) {
	   Log.INFO(((Node)iter.next()).toString());
       }
   }
   
   private static void printNodeState(Node n) throws HADBException {
       StringBuffer message = new StringBuffer();
       message.append("Node: " + n.getDomainMember().getHostName());
       message.append(" Role: " + n.getNodeRole().toString());
       message.append(" State: " + n.getNodeState().toString());
       Log.INFO(message.toString());
   }
}
