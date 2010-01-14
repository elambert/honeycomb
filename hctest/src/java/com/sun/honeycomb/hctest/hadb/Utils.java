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

import com.sun.honeycomb.wb.sp.hadb.utils.HADBDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.sun.hadb.adminapi.HADBException;
import com.sun.honeycomb.hadb.convert.QueryConvert;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.util.ClusterMembership;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.wb.sp.hadb.utils.HADBDomainMember;
import com.sun.honeycomb.wb.sp.hadb.utils.Util;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.parsers.ParseException;

public class Utils {
    	private static boolean m_testMode = false;
    	private static final String ADMIN_VIP="10.123.45.200";
    	static { 
    	    if (System.getProperty("testmode") != null) {
    		m_testMode = true;
    		Log.global.level=Log.DEBUG_LEVEL;
    		Log.INFO("enabling test mode");
    	    }
    	}
    	
    	/**
    	 * This is a static helper class. No one should 
    	 * ever invoke new on it.
    	 *
    	 */
	private Utils() {
		
	}

	public static String generateAttributeValue(String attribute) {
		if (attribute.equals(Utils.STRING_TYPE)) {
		    return STRING_VALUES[getRandomInt(STRING_VALUES.length)];
		} else if (attribute.equals(DOUBLE_TYPE)) {
		    return DOUBLE_VALUES[getRandomInt(DOUBLE_VALUES.length)];
		} else if (attribute.equals(LONG_TYPE)) {
		    return LONG_VALUES[getRandomInt(LONG_VALUES.length)];
		} else {
			return null; 
			//TODO I should throw an exception
		}
	}

	public static ArrayList generateQueryTokensMasterList() {
		QueryToken qt = null;
		ArrayList al = new ArrayList();

	
		for (int i = 0; i < Utils.STRING_ATTRS.length; i++) {
			qt = new QueryToken(Utils.STRING_ATTRS[i], 
					    Utils.STRING_TABLE,
					    Utils.STRING_TYPE);
			al.add(qt);
		}

		// process Double Tokens
		for (int i = 0; i < Utils.DOUBLE_ATTRS.length; i++) {
			qt = new QueryToken(Utils.DOUBLE_ATTRS[i], 
					    Utils.DOUBLE_TABLE,
					    Utils.DOUBLE_TYPE);
			al.add(qt);
		}

		// process Long Tokens
		for (int i = 0; i < Utils.LONG_ATTRS.length; i++) {
			qt = new QueryToken(Utils.LONG_ATTRS[i], 
					    Utils.LONG_TABLE,
					    Utils.LONG_TYPE);
			al.add(qt);
		}

		return al;
	}

	public static int getRandomInt(int ceiling) {
		return (int) Math.round(java.lang.Math.random()*(ceiling - 1));
	}
	
	public static String generateQry(HashSet tokenSet) 
	throws ParseException,EMDException {
		return generateQry(tokenSet,false);
	}
	
	public static String generateQry(HashSet tokenSet, boolean verbose) 
	throws ParseException,
			EMDException {

		Iterator iter = tokenSet.iterator();
		StringBuffer selectClause = new StringBuffer();
		StringBuffer whereClause = new StringBuffer();
		StringBuffer qry = new StringBuffer();
		String selectAttr = null;
		String rawQry = null;
		QueryConvert converter = null;
		while (iter.hasNext()) {
			QueryToken qt = (QueryToken) iter.next();
			String quote = "'";
			if (qt.isSelectToken) {
			    selectClause=new StringBuffer(qt.getAttributeName());
			    selectAttr = qt.getAttributeName();
			} else {
			    if (whereClause.length() != 0) {
				whereClause.append(" and ");
			    }
			    if (!qt.getType().equals(STRING_TYPE)) {
				quote = "";
			    }
			    whereClause.append(qt.getAttributeName() + "="
				    	       + quote + qt.getValue() + quote);
			}

		}
		if (whereClause.length() == 0 && selectAttr != null) {
			whereClause.append(selectAttr + " is not null");
		}

		converter = new QueryConvert(selectClause.toString(),
					     whereClause.toString(), 
					     (String) null);
		rawQry = "select " + selectClause + " where " + whereClause;

		
		converter.convert(qry);
		if (verbose) {
			System.out.println("Raw query: " + rawQry);
			System.out.println("Converted Qry: " + qry);
		}
		
		return qry.toString();

	}
	
	public static synchronized Connection getConnection () 
	throws SQLException, ClassNotFoundException {
		return getConnection(DEFAULT_HADB_HOST);
	}
	
	public static synchronized Connection getConnection (String HadbHostURL) 
	throws SQLException, ClassNotFoundException {
	    Connection conn = null;
	    if (!m_driverLoaded) {
		Class.forName(Utils.JDBC_DRIVER_CLASS);
	    }
			
	    conn = DriverManager.getConnection(JDBC_DRIVER_URL_PREFIX + 
		    				HADB_USER + "@" + HadbHostURL );
	    conn.setAutoCommit(true);
	    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
	    return conn;
	}
	

    public static synchronized String generateStanfordOID() {
		String value=Long.toString(System.currentTimeMillis()) + "-" + 
				  Integer.toString(Utils.getRandomInt(32000));
		return value;
    }
    
    public static synchronized ArrayList getOIDs (long maxNum, Connection conn) 
    throws SQLException{
    		ArrayList results = new ArrayList();
    		long i = 0;
    		Statement stmt = conn.createStatement();
    		ResultSet rs = stmt.executeQuery("select objectid from " +
    						 "stringattribute;");
    		while (rs.next() && i++ < maxNum ){
    			results.add(rs.getString(1));
    		}
    		return results;
    }
    
    public static String generateOID() {
        int layout = Utils.getRandomInt(10000);
        int chunkNumber = 1;
        byte type = (byte)0x01;
        return (new NewObjectIdentifier(layout,type,
        				chunkNumber,null)).toString();
    }
    
    public static Set getNodesToDisable(Set priorSet, boolean useSpares, 
	    boolean useMirrors, boolean canReuseNodes, int numberOfNodes,
	    int clusterSize)
    throws HoneycombTestException, HADBException{
	ClusterMembership cluster =  new ClusterMembership(-1, ADMIN_VIP);
	cluster.setQuorum(true);
	cluster.initClusterState();
	return getNodesToDisable(priorSet, useSpares, useMirrors, canReuseNodes, 
		numberOfNodes, clusterSize, false, cluster);
    }
    
    public static Set getNodesToDisable(Set priorSet, boolean useSpares, 
	    boolean useMirrors, boolean canReuseNodes, int numberOfNodes,
	    int clusterSize, ClusterMembership cluster)
    throws HoneycombTestException, HADBException{
	return getNodesToDisable(priorSet, useSpares, useMirrors, canReuseNodes, 
		numberOfNodes, clusterSize, false, cluster);
    }
    /**
     * This is a utility method used by HADB StateMachine tests to determine
     * a set nodes which can disable or killed for the purposes of the test.
     * The method randomly picks nodes and checks to see if the node meets
     * the selection criteria.  If so, the node is included in the set 
     * returned by the method.
     * 
     * @param priorSet 
     * @param useSpares Can spare nodes be included in the set.
     * @param useMirrors Should mirror pairs be included in the set.
     * @param canReuseNodes Can nodes that are a part of the prior set
     * be included in the result set returned by this method.
     * @param numberOfNodes The number nodes to be included in the set.
     * @param clusterSize The number of nodes in the cluster.
     * @return The set of nodes
     * @throws HoneycombTestException
     * @throws HADBException
     */
    public static Set getNodesToDisable (Set priorSet, boolean useSpares, 
	   		 	        boolean useMirrors, 
	   		 	        boolean canReuseNodes, 
	   		 	        int numberOfNodes,int clusterSize,
	   		 	        boolean alwaysIncludeMaster, 
	   		 	        ClusterMembership cluster) 
    throws HoneycombTestException, HADBException{
	
	int numAttempts = 32;
	Random randGen = new Random(System.currentTimeMillis());
	HashSet resultSet = new HashSet();
	cluster.setQuorum(true);
	cluster.initClusterState();
	
	if (priorSet == null) {
	    throw new HoneycombTestException("The prior set of nodes is null.");
	}
	
	if (numberOfNodes > clusterSize) {
	    throw new HoneycombTestException("The number of nodes you want to" +
	    				    " disable (" + numberOfNodes + 
	    				    ") is greater then the number of " +
	    				    "nodes in the cluster (" + 
	    				    clusterSize +")");
	}
	
	if (numberOfNodes < 0) {
	    throw new HoneycombTestException("The number of nodes you want to "+
	    		"disable ("+ numberOfNodes + ") is less than 0.");
	}
	
	if (alwaysIncludeMaster) {
	    ClusterNode master = cluster.getMaster();
	    if (master == null) {
		throw new HoneycombTestException("Set of disabled nodes must " +
				"include master, but can not detemine who the " +
				"master is.");
	    }
	    resultSet.add(master);
	    
	}
	
	while (resultSet.size() < numberOfNodes && numAttempts-- > 0) {
	    String name=ClusterNode.id2hostname(randGen.nextInt(clusterSize)+1);
	    Log.DEBUG("gonna try node " + name);
	    if (nodeMeetsCriteria(name,useMirrors,
			    	  useSpares,canReuseNodes, 
			    	  priorSet,resultSet)){
		numAttempts=32;
		resultSet.add(cluster.getNode(name));
		if (useMirrors && resultSet.size() != numberOfNodes) {
		    resultSet.add(cluster.getNode(Util.getMirrorHostName(name)));
		}
	    } else {
		Log.DEBUG("Node " + name + " was not " +
			 "qualified. "+ numAttempts + " attempts left");
		}
	}
	if (numAttempts == 0) {
		throw new HoneycombTestException("Was unable to generate " 
				+ numberOfNodes +" unique nodes to down.");
	}

	return resultSet;
   }
    
    
   private static boolean nodeMeetsCriteria (String nodeName, 
	   				     boolean checkMirror,
	   			             boolean useSpares,
	   			             boolean reuseNodes,
	   			             Set priorSet,Set currentSet) 
   throws HADBException, HoneycombTestException {
       if (priorSet == null) {
	   throw new HoneycombTestException("The prior set of nodes is null.");
       }
       
       if (currentSet == null) {
	   throw new HoneycombTestException("The prior set of nodes is null.");
       }
       
   	if (nodeInSet(nodeName,currentSet)) {
   	    Log.DEBUG("rejecting node " + nodeName + 
   		      " since it already has been selected");
   	    return false;
   	}
   	if (!reuseNodes && nodeInSet(nodeName,priorSet)) {
   	    Log.DEBUG("rejecting node " + nodeName + 
   		      " since it was in the last set of selected nodes");
   	    return false;
   	}
   	if ( !useSpares && Util.isSpare(nodeName)) {
   	    Log.DEBUG("rejecting node " + nodeName + 
   		      " because it is a spare and I am not using spares.");
   	    return false;
   	}
   	if (checkMirror) {
   	    if (!nodeMeetsCriteria(Util.getMirrorHostName(nodeName),
   		    		   false,useSpares,reuseNodes,priorSet,
   		    		   currentSet)) {
   		Log.DEBUG("rejecting node "+ nodeName + 
   			  " because its' mirror did not meet the critiria.");   
   		return false;
   	    }
   	} else {
   	    if (nodeInSet(Util.getMirrorHostName(nodeName),currentSet)) {
   		Log.DEBUG("rejecting node "+ nodeName + 
   			  " because its' mirror has already been selected.");  
   		return false;
   	    }
   	}
   	Log.DEBUG("I am accepting the node " + nodeName);
   	return true;
   }
	
   
   private static boolean nodeInSet (String name, Set set) {
	if (set == null) {
	    return false;
	}
	Iterator iter = set.iterator();
	while (iter.hasNext()) {
	    ClusterNode cn = (ClusterNode) iter.next();
	    if(cn.getName().equals(name)) {
		return true;
	    }
	}
	return false;
   }
   
   
   public static boolean restartNodes(Set nodes, boolean useStop ) 
   throws HoneycombTestException {
       Log.INFO("Restarting disabled and killed nodes. " +
		"This can take upto 30 minutes");
       Iterator iter = nodes.iterator();
       int maxTries=3;
       boolean unableToRestartNode=false;
       if (nodes.isEmpty()) {
	   return true;
       }	
       while (iter.hasNext()) {
	    ClusterNode cn = (ClusterNode)iter.next();
	    Log.INFO("About to restart " + cn.getName());
	    if (m_testMode) {
		continue;
	    }
	    cn.removeDevMode();
	    try {
		if (useStop)
			cn.callHCStop();
		else
		    	cn.reboot();
	    }catch (HoneycombTestException hte) {};//the close causes an hte
       }
	
       if (m_testMode) {
	    return true;
       }
	
       Util.sleeper(3 * 60 * 1000);
	
       iter = nodes.iterator();
       while (iter.hasNext()) {
	    int tries=0;
	    ClusterNode cn = (ClusterNode)iter.next();
	    boolean pingable = false;
	    while (!(pingable = cn.ping()) && tries < maxTries) {
		Util.sleeper(30 *1000);
		tries++;
	    }
	    if (pingable) {
		cn.rebootDone();
	    } else {
		Log.ERROR(cn.getName() + " cant be brought back. Not pingable");
		unableToRestartNode = true;
	    }
	}	
	
	// now wait a bit more for the node to rejoin hadb
	// we will wait upto 20 minutes for the 
	// node to rejoin the domain
	if (!unableToRestartNode) {
	    
	    long timeout = System.currentTimeMillis() + (20 * 60 * 1000);
	    boolean allRunning = true;
	    while (System.currentTimeMillis() < timeout) {
		iter = nodes.iterator();
		ClusterNode cur = null;
		allRunning = true;
		while (iter.hasNext()) {
		    cur = (ClusterNode)iter.next();
                    String nodeName = cur.getName();
		    HADBDomainMember dm = null;
		    try {
			dm=Util.getDomainMember(nodeName);
		    } catch (HADBException he) {
			Log.WARN("Received an exception while querying domain" +
					" state of node " + nodeName);
			Log.WARN(he.getMessage());
		    }
		    if (dm == null || !dm.isRunning()) {
			Log.INFO(nodeName + 
				" still not a running member of the domain");
			allRunning=false;
		    } else {
                        try {
                            if (!Util.nodeIsInDatabase(nodeName)) {
                                Log.INFO(nodeName + " is not in the database");
                                allRunning = false;
                            }
                        } catch (HADBException he) {
                            Log.WARN("Encountered an HADB Exception while " +
                                    "attempting to determing if " + nodeName + 
                                    " is in the database");
                            Log.WARN(he.getMessage());
                            allRunning = false;
                            
                        }
                    }
		}
		if (allRunning) {
		    break;
		} else {
		    Util.sleeper(30 * 1000); //sleep for 30 secs
		}
	    }
	    if (!allRunning) {
		unableToRestartNode=true;
	    }
	}
	
	return !unableToRestartNode;
   }
   
   public static final String STRING_TYPE = "String";

   public static final String DOUBLE_TYPE = "Double";

   public static final String LONG_TYPE = "Long";

   public static final String[] STRING_VALUES = { "empty", "YES", 
						  "No", "MaybeSo",
						  "AVALUE","Sun", "Apple", 
						  "Honeycomb", "San Francisco", 
						  "Paris" };
 

	public static final String[] LONG_VALUES = { "7", "0","42","-59", 
	    					  "89", 
	    					  Long.toString(Long.MIN_VALUE),
	    					  Long.toString(Long.MAX_VALUE),
	    					  "-7","1016", "197701" };

	public static final String[] DOUBLE_VALUES = { "3.14", "0","-16.03",
	    						"98.6", "71", "2.1", 
	    						"451.0","0.333333",
	    						"212.415", "1.3" };

	public static final String[] STRING_ATTRS = { "test_id", "stringnull",
						      "system_filepath", 
						      "initchar", "sha1", 
						      "word","archive",
						      "stringlarge","sixth", 
						      "fifth","client", 
						      "stringorigargs",
						      "prevSHA1", 
						      "stringspaces",
						      "view_filepath", 
						      "User_Comment",
						      "stringweirdchars", 
						      "storedate","filesize", 
						      "user"};

	public static final String[] LONG_ATTRS = { "longsmall","fileorigsize",
	    					    "longnull", "filecurrsize", 
	    					    "longlarge", "timenow",
	    					    "date","wordlength",
	    					    "timestart", "iteration"};

	public static final String[] DOUBLE_ATTRS = { "doublenull",
	    					      "doublefixed",
	    					      "doublechanged",
	    					      "doublelarge", 
	    					      "doublenegative", 
	    					      "doublechunked", 
	    					      "doublesmall" };

	public static final String [] STANFORD_STRING_ATTRS = { "oid" };
	
	public static final String JDBC_DRIVER_CLASS="com.sun.hadb.jdbc.Driver";

	public static final String METADATA_TABLE = "metadataschema";

	public static final String STRING_TABLE = "stringattribute";

	public static final String LONG_TABLE = "longattribute";

	public static final String DOUBLE_TABLE = "doubleattribute";
	
	public static final String JDBC_DRIVER_URL_PREFIX = "jdbc:sun:hadb:";
	
	public static final String HADB_USER = "system+superduper";
	
	public static final String DEFAULT_HADB_HOST="10.123.45.101:15005," +
				  "10.123.45.102:15005,10.123.45.103:15005," +
				  "10.123.45.104:15005,10.123.45.105:15005," +
				  "10.123.45.106:15005,10.123.45.107:15005," +
				  "10.123.45.108:15005";
	
	private static boolean m_driverLoaded = false;
	
	public static void main (String [] args) throws Throwable {
	    TestBed.getInstance().spIP="localhost";
	    TestBed.getInstance().spIPaddr="10.123.45.100";
	    
	    ClusterNode spare = null;
	    ClusterNode nonspare = null;
	    ClusterNode mirror = null;
	    
	    for (int i = 1; i < 17; i++) {
		String name = ClusterNode.id2hostname(i);
		if (!name.equals("hcb" + (100 +i))) {
		    System.err.println("Name format is wrong " + name);
		} else {
		    System.err.println(name);
		    ClusterNode cn = new ClusterNode(name);
		    if (Util.isSpare(name) && spare == null) {
			spare = new ClusterNode(name);
		    } else {
			if (nonspare == null) {
			    nonspare = new ClusterNode(name);
			    mirror=new ClusterNode(Util.getMirrorHostName(name));
			}
		    }
		}
	    }
	    
	    System.err.println("Spare is " + spare.getName());
	    System.err.println("nonSpare is " + nonspare.getName());
	    System.err.println("mirror is " + mirror.getName());
	    
	    HashSet testSet = new HashSet();
	    testSet.add(nonspare);
	    
	    // Test node in Set: null name
	    if (nodeInSet(null, new HashSet())) {
		System.err.println("Node In Set (NullName): FAILED");
	    } else {
		System.err.println("Node In Set (NullName): PASSED");
	    }
	    
	    // Test node in Set: null set
	    if (nodeInSet("TEST", null)) {
		System.err.println("Node In Set (NullSet): FAILED");
	    } else {
		System.err.println("Node In Set (NullSet): PASSED");
	    }
	    
	    // Test node in Set: empty set
	    if (nodeInSet("TEST", new HashSet())) {
		System.err.println("Node In Set (NullSet): FAILED");
	    } else {
		System.err.println("Node In Set (NullSet): PASSED");
	    }
	    
	    // Test node in Set: hit
	    if (!nodeInSet(nonspare.getName(), testSet)) {
		System.err.println("Node In Set (Hit): FAILED");
	    } else {
		System.err.println("Node In Set (Hit): PASSED");
	    }
	    // Test node in Set: miss
	    if (nodeInSet(spare.getName(), testSet)) {
		System.err.println("Node In Set (miss): FAILED");
	    } else {
		System.err.println("Node In Set (miss): PASSED");
	    }
	    
	    
	    // Test will accept non spare node with empty set
	    if (nodeMeetsCriteria(nonspare.getName(), false, false, false, 
		    		  new HashSet(), new HashSet())) {
		System.err.println("nodeMeetsCriteria (acceptNonSpare):PASSED");
	    } else {
		System.err.println("nodeMeetsCriteria (acceptNonSpare):FAILED");
	    }
	    
	    // Test will reject node already in use
	    if (!nodeMeetsCriteria(nonspare.getName(), false, false, false, 
		      		   new HashSet(), testSet)) {
		System.err.println("nodeMeetsCriteria(rejAlreadyUsed):PASSED");
	    } else {
		System.err.println("nodeMeetsCriteria(rejAlreadyUsed):FAILED");
	    }
	    
	    // Test wil reject node if mirror is already in use and no-mirrors
	    if (!nodeMeetsCriteria(mirror.getName(), true, false, false, 
		    		   new HashSet(), testSet)) {
		System.err.println("nodeMeetsCriteria(rejectMirInUse):PASSED");
	    } else {
		System.err.println("nodeMeetsCriteria(rejectMirInUse):FAILED");
	    }
	    
	    // Test rejects node if node was selected in last round & no reuses
	    if (!nodeMeetsCriteria(nonspare.getName(), false, false, false, 
		    		   testSet, new HashSet())) {
		System.err.println("nodeMeetsCriteria (rejectPriorUse):PASSED");
	    } else {
		System.err.println("nodeMeetsCriteria (rejectPriorUse):FAILED");
	    }
	    
	   
	    // Test accept nodes if node was selected in last round & can resuse
	    if (nodeMeetsCriteria(nonspare.getName(), false, false, true, 
		    		  testSet, new HashSet())) {
		System.err.println("nodeMeetsCriteria (acceptPrioruse):PASSED");
	    } else {
		System.err.println("nodeMeetsCriteria (acceptPrioruse):FAILED");
	    }
	    
	    //Test will reject a spare when we ask it to
	    if (!nodeMeetsCriteria(spare.getName(), false, false, false, 
		    		   testSet, new HashSet())) {
		System.err.println("nodeMeetsCriteria (rejectSpare):PASSED");
	    } else {
		System.err.println("nodeMeetsCriteria (rejectSpare):FAILED");
	    }
	    
	    //Test will accept a spare when we ask it to
	    if (nodeMeetsCriteria(spare.getName(), false, true, false, 
		    		  testSet, new HashSet())) {
		System.err.println("nodeMeetsCriteria (acceptSpare):PASSED");
	    } else {
		System.err.println("nodeMeetsCriteria (acceptSpare):FAILED");
	    }
	    
	    // Test getNodesToDisable 
	    Set t2 = null;
	    boolean thrown = false;
	    // Test negative number of nodes
	    try {
		t2 = getNodesToDisable(new HashSet(), true, true, true,-1, 16); 
	    } catch (Throwable t) {
		thrown = true;
	    }
	    
	    if (thrown) {
		System.err.println("getNodesToDisable(negativenum):PASSED");
	    } else {
		System.err.println("getNodesToDisable(negativenum):FAILED");
	    }
	    
	    thrown = false;
	    // Test to many
	    try {
		t2 = getNodesToDisable(new HashSet(), true, true, true,17, 16); 
	    } catch (Throwable t) {
		thrown = true;
	    }
	    
	    if (thrown) {
		System.err.println("getNodesToDisable(too many):PASSED");
	    } else {
		System.err.println("getNodesToDisable(too many):FAILED");
	    }
	    
	    // Test 0 nodes
	    t2 = getNodesToDisable(new HashSet(), true, true, true,0, 16);
	    if (t2 != null && t2.isEmpty()) {
		System.err.println("getNodesToDisable(0 nodes):PASSED");
	    } else {
		System.err.println("getNodesToDisable(0 nodes):FAILED");
	    }
	    
	    // Test 1 node
	    t2 = getNodesToDisable(new HashSet(), true, true, true,1, 16);
	    if (t2 != null && t2.size() == 1 ) {
		System.err.println("getNodesToDisable(1 nodes):PASSED");
	    } else {
		System.err.println("getNodesToDisable(1 nodes):FAILED");   
		if (t2 != null) {
		    System.err.println("Return " + t2.size());
		}else  {
		    System.err.println("Return null");
		}
	    }
	    
	    // Test 8 nodes
	    t2 = getNodesToDisable(new HashSet(), true, true, true,8, 16);
	    if (t2 != null && t2.size() == 8) {
		System.err.println("getNodesToDisable(8 nodes):PASSED");
	    } else {
		System.err.println("getNodesToDisable(8 nodes):FAILED");
		if (t2 != null) {
		    System.err.println("Return " + t2.size());
		} else  {
		    System.err.println("Return null");
		}
		    
	    }
	    
	    
	  t2 = getNodesToDisable (new HashSet(), false, true,false,2,16);
	  if (t2.size() != 2) {
	      System.err.println("getNodesToDisable(2 mirrors):FAILED");
		if (t2 != null) {
		    System.err.println("Return " + t2.size());
		} else  {
		    System.err.println("Return null");
		}
	  } else {
	      Iterator iter = t2.iterator();
	  	ClusterNode c1 = (ClusterNode)iter.next();
	  	ClusterNode c2 = (ClusterNode)iter.next();
	  	System.err.println(c1.getName());
	  	System.err.println(c2.getName());
	  	if (!Util.getMirrorHostName(c1.getName()).equals(c2.getName())) {
	  	    System.err.println("getNodesToDisable(2 mirrors):" +
	  	    		       "FAILED [not mirrors]");
	  	} else {
	  	  System.err.println("getNodesToDisable(2 mirrors):PASSED");
	  	}
	  }
	  
	  t2 = getNodesToDisable (new HashSet(), false, true,false,3,16);
	  if (t2.size() != 3) {
	      System.err.println("getNodesToDisable(3 mirrors):FAILED");
		if (t2 != null) {
		    System.err.println("Return " + t2.size());
		} else  {
		    System.err.println("Return null");
		}
	  } else {
	      Iterator iter = t2.iterator();
	  	ClusterNode c1 = (ClusterNode)iter.next();
	  	ClusterNode c2 = (ClusterNode)iter.next();
	  	if (!Util.getMirrorHostName(c1.getName()).equals(c2.getName())) {
	  	    System.err.println("getNodesToDisable(3 mirrors):" +
	  	    		       "FAILED [not mirrors]");
	  	} else {
	  	  System.err.println("getNodesToDisable(3 mirrors):PASSED");
	  	}
	  }
	   
	}
	


}
