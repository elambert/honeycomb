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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


import com.sun.hadb.adminapi.HADBException;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.hadb.Utils;
import com.sun.honeycomb.hctest.util.ClusterMembership;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RunCommand;
import com.sun.honeycomb.wb.sp.hadb.scripts.BadScriptException;
import com.sun.honeycomb.wb.sp.hadb.scripts.Script;
import com.sun.honeycomb.wb.sp.hadb.utils.HADBDomainMember;
import com.sun.honeycomb.wb.sp.hadb.utils.Util;

/**
 * This class is used to test HADB's ability to 
 * be reliable in the presence of cluster reboots.
 * 
 * It is designed to be run from the cheat node. 
 * The purpose of running from the cheat is that 
 * it allows the test to access tha HADB management
 * API for the instance of HADB running on the cluster.
 * Also, hopefully at somepoint this test might be able 
 * to listen on HADB State machine events and take 
 * certain actions based on the presence of certain 
 * events.
 * 
 * @author elambert
 *
 */

/*
 * TODO update this to support nogreenline & nohoneycomb
 */

public class RebootTest {
    
    public static final int EXIT_CODE_SUCCESS=0;
    public static final int EXIT_CODE_FAILED=1;
    public static final int EXIT_CODE_ERROR=2;
    public static final int CLUSTER_OP_REBOOT = 0;
    public static final int CLUSTER_OP_HARDREBOOT = 1;
    public static final int CLUSTER_OP_NOTHING = 2;
    public static final int CLUSTER_OP_EXTEND = 3;
    public static final int NODE_ACTION_DISABLE = 0;
    public static final int NODE_ACTION_KILL  = 1;
    public static final int NODE_ACTION_REBOOT  = 2;
    public static final int NODE_ACTION_QREBOOT  = 3;
    public static final int NODE_ACTION_NOTHING  = 4;
    public static final int KILL_MODE_SOFT = 0;
    public static final int KILL_MODE_HARD = 1;
    public static final String CLUSTER_OP_REBOOT_STR="reboot";
    public static final String CLUSTER_OP_HRDREBOOT_STR="hardreboot";
    public static final String CLUSTER_OP_NOTHING_STR="nothing";
    public static final String CLUSTER_OP_EXTEND_STR="extend";
    public static final String [] CLUSTER_OPS_DESC = {CLUSTER_OP_REBOOT_STR,
	                                      	      CLUSTER_OP_HRDREBOOT_STR,
	                                      	      CLUSTER_OP_NOTHING_STR,
	                                      	      CLUSTER_OP_EXTEND_STR};
    public static final String NODE_ACT_DISABLE_STR="disable";
    public static final String NODE_ACT_REBOOT_STR="reboot";
    public static final String NODE_ACT_QREBOOT_STR="quick reboot";
    public static final String NODE_ACT_KILL_STR="kill";
    public static final String NODE_ACT_NOTHING_STR="nothing";
    public static final String [] NODE_ACT_DESC = {NODE_ACT_DISABLE_STR,
						   NODE_ACT_KILL_STR,
						   NODE_ACT_REBOOT_STR,
						   NODE_ACT_QREBOOT_STR,
						   NODE_ACT_NOTHING_STR};
    private static final String CLI_OPT_HELP = "--help";
    private static final String CLI_OPT_HELP_SHORT = "-H";
    private static final String CLI_OPT_WIPE = "--wipe";
    private static final String CLI_OPT_WIPE_SHORT = "-W";
    private static final String CLI_OPT_ITERS = "--iters";
    private static final String CLI_OPT_ITERS_SHORT = "-I";
    private static final String CLI_OPT_SLEEP = "--sleep";
    private static final String CLI_OPT_SLEEP_SHORT = "-S";
    private static final String CLI_OPT_SCRIPT = "--script";
    private static final String CLI_OPT_SCRIPT_SHORT = "-R";
    private static final String CLI_OPT_LOG = "--logdir";
    private static final String CLI_OPT_LOG_SHORT = "-L";
    private static final String CLI_OPT_NOGL = "--nogreenline";
    private static final String CLI_OPT_NOHC = "--nohoneycomb";
    private static final String CLI_OPT_KILL_MIRRORS = "--killmirror";
    private static final String CLI_OPT_KILL_MIRRORS_SHORT = "-KM";
    private static final String CLI_OPT_ALLOW_SPARES = "--allowspares";
    private static final String CLI_OPT_ALLOW_SPARES_SHORT = "-AS";
    private static final String CLI_OPT_ROLL_NODES = "--rollnodes";
    private static final String CLI_OPT_ROLL_NODES_SHORT = "-RN";
    private static final String CLI_OPT_WIPE_TIME = "--wipetimeout";
    private static final String CLI_OPT_WIPE_TIME_SHORT = "-WT";
    private static final String CLI_OPT_CLUSTER_OPT="--clusteroperation";
    private static final String CLI_OPT_CLUSTER_OPT_SHORT="-CO";
    private static final String CLI_OPT_NODE_ACTION="--nodeaction";
    private static final String CLI_OPT_NODE_ACTION_SHORT="-NA";
    private static final String CLI_OPT_NUMBER_NODES="--numberofnodes";
    private static final String CLI_OPT_NUMBER_NODES_SHORT="-NN";
    private static final String CLI_OPT_KILL_HARD = "--killhard";
    private static final String CLI_OPT_KILL_HARD_SHORT = "-KH";
    private static final String CLI_OPT_FOLLOW_MASTER = "--followmaster";
    private static final String CLI_OPT_FOLLOW_MASTER_SHORT = "-FM";
    private static final long STATE_MACHINE_MAX = 60* 70 * 1000; //70 min
    private static final long INITIAL_SANITY_WAIT = 120 * 60 *1000; // 2 hour
    
    private int m_numDownNodes=0;
    private boolean m_shouldWipe = false;
    private int m_clusterOp = CLUSTER_OP_REBOOT;
    private int m_nodeAction = NODE_ACTION_NOTHING;
    private int m_numberOfIterations = 1;
    private boolean m_onlyRebootHoneycomb = false;
    private boolean m_honeycombInsideGreenline = true;
    private boolean m_wipeClusterAtStart = false;
    private long m_rebootTimeOutValue = STATE_MACHINE_MAX; //70 min
    private long m_wipeTimeOutValue = 420; 
    private HashSet m_preRebootScripts = new LinkedHashSet();
    private int m_numberOfNodes=16;
    private ClusterMembership m_cluster = null;
    private RunCommand m_commandRunner = new RunCommand();
    private CLI m_cli = null;
    private File m_logDir = null;
    private static boolean m_testMode=false;
    private boolean m_reuseNodes = true;
    private boolean m_allowMirrors = false;
    private boolean m_allowSpares = false;
    private boolean m_verboseDataCollection = false;
    private int m_killMode = KILL_MODE_SOFT;
    private boolean m_alwaysNodeFaultMaster = false;
    //private HashSet m_disabledNodes = new HashSet();
    
    
    public RebootTest () {
	if (System.getProperty("testmode") != null) {
	    m_testMode = true;
	    Log.global.level=Log.DEBUG_LEVEL;
	    Log.INFO("enabling test mode");
	}
        if (System.getProperty("verbose.datacollection") != null) {
            m_verboseDataCollection = true;
        }
	TestBed.getInstance().spIP="localhost";
	TestBed.getInstance().spIPaddr="10.123.45.100";
    }
   
    
    public static void main (String [] args) {
	RebootTest me = new RebootTest();
	LinkedList argsList = new LinkedList();
	for (int i = 0; i < args.length; i++) {
	    argsList.add(args[i]);
	}                                                                       
	try {
	    me.parseArgs(argsList);
	    System.exit(me.testHADBState());
	} catch (InvalidArgException iae) {
	    System.err.println("Usage Error:");
	    System.err.println("            " + iae.getMessage());
	    System.exit(EXIT_CODE_ERROR);
	} catch (Throwable t) {
	    System.err.println("Unexpected exception:");
	    System.err.println("            " + t.getMessage());
	    t.printStackTrace(System.err);
	    System.exit(EXIT_CODE_ERROR);	    
	}

    }
 
    
    public static void usage() {
	String myClass = RebootTest.class.toString();
	System.out.println("Usage: ");
	System.out.println("       java -D" + 
		           RunCommand.IGNORE_COMMAND_VALIDATION +"=true "  + 
		           myClass);
	System.out.println("         " + CLI_OPT_LOG + 
	           " | " + CLI_OPT_LOG_SHORT +" <PATH_TO_LOG_DIR>  ");
	System.out.println("       [ " + CLI_OPT_ITERS + 
			   " | " + CLI_OPT_ITERS_SHORT +" <ITERATIONS> ] " ); 
	System.out.println("       [ " + CLI_OPT_CLUSTER_OPT + " | " + 
	          CLI_OPT_CLUSTER_OPT_SHORT + " <CLUSTER_OPERATION> ]");
	System.out.println("       [ " + CLI_OPT_NODE_ACTION + " | " + 
	           CLI_OPT_NODE_ACTION_SHORT + " <NODE_ACTION>]");
	System.out.println("       [ " + CLI_OPT_NUMBER_NODES + " | " + 
	           CLI_OPT_NUMBER_NODES_SHORT + " <NUMBER_OF_NODES> ]");
	System.out.println("       [ " + CLI_OPT_SCRIPT + " | " + 
	           CLI_OPT_SCRIPT_SHORT +" <SCRIPT> ] ");
	System.out.println("       [ " + CLI_OPT_SLEEP + " | " + 
		           CLI_OPT_SLEEP_SHORT +" <SLEEP_VALUE> ] " );
	System.out.println("       [ " + CLI_OPT_KILL_MIRRORS + " | " + 
	           CLI_OPT_KILL_MIRRORS_SHORT + " ]");
	System.out.println("       [ " + CLI_OPT_ALLOW_SPARES + " | " + 
	           CLI_OPT_ALLOW_SPARES_SHORT + " ]");
	System.out.println("       [ " + CLI_OPT_ROLL_NODES + " | " + 
	           CLI_OPT_ROLL_NODES_SHORT + " ]");
	System.out.println("       [ " + CLI_OPT_WIPE_TIME + " | " + 
		           CLI_OPT_WIPE_TIME_SHORT + "<TIME_IN_SECONDS> ]");
	System.out.println("       [ " + CLI_OPT_WIPE + " | " + 
	           CLI_OPT_WIPE_SHORT +" ] "); 
	System.out.println("       [ " + CLI_OPT_NOGL + " ] [ " + 
	           CLI_OPT_NOHC +" ]");
	System.out.println("       [ " + CLI_OPT_HELP + " | " + 
	           CLI_OPT_HELP_SHORT + " ]");
	System.out.println("");
	System.out.println("Options are: ");
	System.out.println("");
	System.out.println("             " +CLI_OPT_LOG + " | " 
		   			   + CLI_OPT_LOG_SHORT + ": " +
		   	  "The path to the directory where the " +
		   	  "HADB log files will be placed. This option is " +
			  "required.");
	System.out.println("");
	System.out.println("             " +CLI_OPT_ITERS + " | " 
					   + CLI_OPT_ITERS_SHORT + ": " +
		           "The number of reboot iterations to be executed. " +
		           "Defaults to 1");
	System.out.println("");
	System.out.println("             " +CLI_OPT_CLUSTER_OPT+ " | " 
			  		   + CLI_OPT_CLUSTER_OPT_SHORT + ": " +
			   "What action should be taken against the cluster " +
			   "at the end of each iteration Valid options are:");
	System.out.println("               -" + CLUSTER_OP_REBOOT_STR + ": " +
			   "Reboot the cluster via the CLI (reboot -f)" );
	System.out.println("               -" + CLUSTER_OP_HRDREBOOT_STR + ": " +
			   "Perform a hardreboot by rebooting each node in " +
			   "the cluster. THIS WILL CORRUPT THE DATABASE");
	System.out.println("               -" + CLUSTER_OP_NOTHING_STR + ": " +
			   "do nothing. This is the default value");
	System.out.println("");
	System.out.println("               -" + CLUSTER_OP_EXTEND_STR + ": " +
	   		   "Extend the number of nodes in the cluster" );
	System.out.println("");
	System.out.println("             " +CLI_OPT_NODE_ACTION+ " | " 
			  		   + CLI_OPT_NODE_ACTION_SHORT + ": " +
			   "What action should be taken against the set of " +
			   "selected nodes. Valid options are:");
	System.out.println("               -" + NODE_ACT_DISABLE_STR + ": " +
			   "let the node continue to run until the next " +
			   "reboot, after which it will not be in cluster");
	System.out.println("               -" + NODE_ACT_KILL_STR + ": " +
			   "kill all the Honeycomb and HADB processes and " +
			   "prevent them from restarting");
	System.out.println("               -" + NODE_ACT_REBOOT_STR + ": " +
			   "reboot the entire node. Node should restart " +
			   "honeycomb and HADB processes and rejoin " +
			   "the cluster");
	System.out.println("               -" + NODE_ACT_NOTHING_STR + ": " +
			   "do nothing. This is the default value.");	
	System.out.println("");
	System.out.println("             " +CLI_OPT_NUMBER_NODES+ " | " 
			  		   + CLI_OPT_NUMBER_NODES_SHORT + ": " +
			  "The number of nodes upon which action will be " +
			  "taken. ");
	System.out.println("              The actual set is determined " +
			   "randomly based on other command line options. " +
			   "Defaults to 0.");
	System.out.println("");
	System.out.println("             " +CLI_OPT_SLEEP + " | " 
					   + CLI_OPT_SLEEP_SHORT + ": " +
		           "The number of seconds we give the reboot to" +
		           " suceed in bring up HADB. Defaults to 1,500 " +
		           "[25 min]");
	System.out.println("");
	System.out.println("             " +CLI_OPT_SCRIPT + " | " 
					   + CLI_OPT_SCRIPT_SHORT + ": " +
		           "The name of the script to be executed before " +
		           "reboot.");
	System.out.println("              This option can appear more than " +
			"once on the command line. If more then one script is");
	System.out.println("              is specified then all scripts " +
			"will be in the order in which they appear on the " +
			"command line");
	System.out.println("");
	System.out.println("             " +CLI_OPT_KILL_MIRRORS+ " | " 
		   			  + CLI_OPT_KILL_MIRRORS_SHORT + ": " +
			   "If the number of nodes selected is greater than 1");
	System.out.println("              have the mechanism used to " +
			   "determine the set of nodes to act up always " +
			   "include mirrored pairs. ");
	System.out.println("              If this option is not specified, " +
			   "mirrored pairs will not be included in the set.");
	System.out.println("");
	System.out.println("             " +CLI_OPT_ALLOW_SPARES+ " | " 
                			   + CLI_OPT_ALLOW_SPARES_SHORT + ": " +
			   "Should spares be included in the set of nodes");
	System.out.println("");
	System.out.println("             " +CLI_OPT_WIPE + " | " 
		   			   + CLI_OPT_WIPE_SHORT + ": " +
		           "Wipe the cluster before starting this test. " +
		           "THIS OPTION IS CURRENTLY NOT AVAILABLE!");
	System.out.println("");	
	System.out.println("             " +CLI_OPT_ROLL_NODES+ " | " 
                			   + CLI_OPT_ROLL_NODES_SHORT + ": " +
                	   " Roll nodes. By specifying this ");
        System.out.println("              option, the test will select a new " +
        		   "set of nodes to down for each interation." );
	System.out.println("              If this option is not specified, " +
			   "then the set of nodes selected on the first " +
			   "iteration is used for all iterations.");
	System.out.println("");
	System.out.println("             " +CLI_OPT_WIPE_TIME+ " | " 
                			   + CLI_OPT_WIPE_TIME_SHORT + ": " +
                	   "If a wipe is expected, the number of seconds we " +
			   "will wait the happen. Defaults to 0");
	System.out.println("");
	System.out.println("             " +CLI_OPT_NOGL + ": " +
		           "Do not place honeycomb as greenline service. " +
		           "THIS OPTION IS CURRENTLY NOT AVAILABLE!");
	System.out.println("");
	System.out.println("             " +CLI_OPT_NOHC + ": " +
		           "Do not physically reboot the nodes, simply " +
		           "reboot the Honeycomb servers. " +
		           "THIS OPTION IS CURRENTLY NOT AVAILABLE!");
	System.out.println("");
	System.out.println("             " +CLI_OPT_KILL_HARD + " | " 
		   + CLI_OPT_KILL_HARD_SHORT + ": " +
	           "Use reboot -q (ungracefull reboot) to kill a node. ");
	System.out.println("");
	System.out.println("             " +CLI_OPT_FOLLOW_MASTER + " | " 
		   + CLI_OPT_FOLLOW_MASTER_SHORT + ": " +
	           "Always include the master node in the set of nodes" +
	           " to fault");
	System.out.println("");
	System.out.println("             " +CLI_OPT_HELP + " | " 
					   + CLI_OPT_HELP_SHORT + ": " +
		           "Print this usage");
	System.out.println("");
	


	
	System.exit(0);
	
    }
    

    private void setupAndValidate () 
    throws HoneycombTestException {
	
	if (m_numberOfIterations <= 0) {
	    throw new HoneycombTestException("You specified an invalid " +
	    		"number of reboots. Must be greater than 0");
	}
	
	if (m_logDir == null) {
	    throw new HoneycombTestException("You must specify a log dir.");
	}
	
	if (m_logDir.exists()) {
	    if (!m_logDir.canWrite()) {
		throw new HoneycombTestException("Do not have permission to " +
				"write to the log directory:" + 
				m_logDir.getAbsolutePath());
	    }
	} else {
	    if (!m_logDir.mkdirs()) {
		throw new HoneycombTestException("Log directory: " 
			+m_logDir.getAbsolutePath() + 
			" does not exist and I failed to make it");
	    }
	}
	
	
	TestRunner.setProperty(HoneycombTestConstants.PROPERTY_SP_IP,"localhost");
	m_cluster = new ClusterMembership(-1, HoneycombTestConstants.ADMIN_VIP);
	m_cluster.setQuorum(true);
	m_cluster.initClusterState();
	m_numberOfNodes = m_cluster.getNumNodes();
	
	// Before we start the test, make sure that the db is in a 
	// a sane state. We will wait for up to an hour for sanity.
	if (!Util.waitForDBSanity(INITIAL_SANITY_WAIT, 
		5* 60 * 1000, m_cluster.getNumNodes(), 0, 0,false)) {
	    throw new HoneycombTestException("Database never achieved sanity");
	}
	
	m_cli = new CLI(HoneycombTestConstants.ADMIN_VIP);
	
	
	if (m_cluster == null) {
	    throw new HoneycombTestException("Could not get a " +
	    		                     "handle on cluster object.");
	}
	
	if (!m_cluster.hasExpectedState()) {
	    throw new HoneycombTestException("Cluster is in an " +
	    		                     "unexpected state");
	}
	
	if (m_cli == null ) {
	    throw new HoneycombTestException("Could not get handle " +
	    				     "on CLI object");
	}

        String[] CLI_BANNER = HoneycombTestConstants.CLI_BANNER;
        String[] verifyString = new String[CLI_BANNER.length+1];
        for(int i=0;i<CLI_BANNER.length;i++) {
            verifyString[i] = CLI_BANNER[i];
        }
        verifyString[CLI_BANNER.length] = "HAFaultTolerant";
	if (!m_cli.verifyCommandStdout("hadb status -F", 
                                       verifyString)) {
	    throw new HoneycombTestException("HADB is not in running " +
	    				     "state on cluster");
	}
	
	if (m_onlyRebootHoneycomb) {
	    doServers("touch /config/noreboot");
	} else {
	    doServers("rm -f /config/noreboot");
	}
	
	if (m_honeycombInsideGreenline) {
	    doServers("rm -f /config/nohoneycomb");
	} else {
	    doServers("touch /config/nohoneycomb");
	}
	
	if (m_clusterOp == CLUSTER_OP_EXTEND) {
	    m_shouldWipe = false;
	} else  if (m_clusterOp == CLUSTER_OP_HARDREBOOT || 
		(m_allowMirrors && m_numberOfNodes > 1 && 
		 m_nodeAction != NODE_ACTION_NOTHING)) {
	    m_shouldWipe = true;
	}
    }
    
    // Future Cleanup: make sure we look on the right node for the state.
    // Occasionally, this output isn't right if we ask a node that has
    // been disabled, especially at end of test after failure.
    public void logHADBState() {
        try {
            Util.logDatabaseStatus();
            Util.logDomainStatus();
        } catch(IOException ioe) {
            Log.WARN("Unable to log domain and database status due to: ");
            Log.WARN(ioe.getMessage());
        } catch(HADBException he) {
            Log.WARN("Unable to log domain and database status due to: ");
            Log.WARN(he.getMessage());
        }
    }
    
    public ClusterMembership getCMMHandle() {
	return m_cluster;
    }
    
    public int testHADBState () 
    throws HoneycombTestException, HADBException {

	int currentIteration=1;
	Set nodesToDisable=new HashSet();

	//	Set up 
	setupAndValidate();
	printConfig();
	//do i wipe?
	if (m_wipeClusterAtStart) {
	    Log.INFO("Wiping cluster....");
	    Util.wipeCluster(m_numberOfNodes);
	}


	//do it 
	while (currentIteration <= m_numberOfIterations) {
	    long iterStartTime = System.currentTimeMillis();

	    Log.INFO("Starting iteration number: " + currentIteration);

	    logHADBState(); 

	    Log.INFO("Master node is now " + m_cluster.getMaster().getName());
	    //run scripts
	    runScripts(m_preRebootScripts);

	    //Disable nodes
	    if (m_numDownNodes > 0) {
		try {
		    boolean killedTheMaster = false;
		    if (!m_reuseNodes || nodesToDisable.isEmpty()) {
			nodesToDisable = Utils.getNodesToDisable(nodesToDisable, 
				m_allowSpares,m_allowMirrors, 
				m_reuseNodes, m_numDownNodes, 
				m_numberOfNodes, m_alwaysNodeFaultMaster, 
				m_cluster);
		    } 
		    Iterator iter = nodesToDisable.iterator();
		    String theMasterId = m_cluster.getMaster().getName();
		    String theViceMasterId = m_cluster.getVice().getName();
		    while (iter.hasNext()) {
			ClusterNode cn = (ClusterNode)iter.next();
			String name = cn.getName();
			if(name.equals(theMasterId)) {
			    Log.INFO("Disabling node " + cn.getName() + 
				    ".  Node " + theMasterId + 
				    " is master--master is disabled node!");
			    killedTheMaster = true;
			    m_cluster.setMasterFailover();
			} else {
			    if (name.equals(theViceMasterId)) {
				m_cluster.setViceFailover();
			    }
			    Log.INFO("Disabling node " + cn.getName() + 
				    ".  Node " + theMasterId + 
				    " is master--master isn't disabled node");
			}
			disableNode(cn, m_nodeAction, m_killMode);
		    }
		    if(killedTheMaster) {
			// XXX Need to determine what the right thing for the test
			// to do is.  We gloss over the problem described in bug
			//
			// 6525579 Rebooting quickly after master failover can 
			// result in losing HADB 
			//
			// by adding this extra sleep.  This was a change Fred made
			// to the testware, so we need to determine if it should stay.
			//
			// Future Cleanup:
			// - avoid this sleep if we just disable the node since
			// no failover occurs in that case.  Same for the 'else'
			// clause as well...no need to wait if we don't change the
			// state of the system.
			// - maybe a blind sleep isn't good and maybe it is better to
			// check the state of the database returns to something
			// operational as the proper way to determine master failover
			// completed
			Log.INFO("We killed the master, so sleep some extra time.");
			Util.sleeper(1000 * 60 * 20);
		    } else {
			// XXX Need to determine what the right thing for the test
			// to do is.  We sometimes have problems doing the node disable
			// and shutting down under load, and this test has been seen
			// to fail if we use a 1 minute timeout here.  A re-run of this
			// test (reboot_003) with a 5 minute sleep here allowed that 
			// test to pass.
			// 
			// Log.INFO("XXX Sleep longer than 1 minute and maybe test " +
			//    "will pass!!!");
			// Util.sleeper(1000 * 60 * 5);
			Util.sleeper(1000 * 60);
		    }
		} catch (HADBException he) {
		    Log.ERROR("HADBException while attempting to disable node");
		    throw new HoneycombTestException(he.getMessage());
		}
	    }

	    //Reboot cluster
	    if (m_clusterOp == CLUSTER_OP_REBOOT || 
		    m_clusterOp == CLUSTER_OP_HARDREBOOT ||
		    m_clusterOp == CLUSTER_OP_EXTEND) {
		if (m_clusterOp == CLUSTER_OP_EXTEND) {
		    Util.wipeCluster(m_numberOfNodes);
		}
		rebootCluster();
	    }
	    
	    Log.INFO("Sleeping for 3 minutes to let cluster settle down.");
	    if (!m_testMode) {
		Util.sleeper(1000 * 60 * 3);
	    }
	    printStateOfDisableNodes(nodesToDisable, "Post Reboot");
	    
	    //wait for HADB
	    if (!waitForHADB()) {
		Log.INFO("TIMED OUT WAITING FOR HADB!");
		break;
	    }
	    
	    printStateOfDisableNodes(nodesToDisable, "Post DB State check");
	    
		

	    //check for wipe
	    Log.INFO("Checking for wipe.  Expect to find wipe: " + m_shouldWipe);
	    if (Util.waitForHadbWipe(iterStartTime, m_wipeTimeOutValue) 
		    != m_shouldWipe) {
		if (m_shouldWipe) {
		    Log.INFO("HADB failed to get wiped.");
		    break;
		} else {
		    Log.INFO("Unexpected HADB wipe detected.");
		    break;
		}
	    } else  {
		if (m_shouldWipe) {
		    Log.INFO("I detected the wipe I was looking for!");
		} else {
		    Log.INFO("HADB has not been wiped.");
		}
	    }

	    if (!stopScripts(m_preRebootScripts)) {
		Log.WARN("one or more scripts failed.");
		break;
	    }

	    printStateOfDisableNodes(nodesToDisable, "Post Wipe check");
	    Log.INFO("Prior to verification, domain and db state is");
	    logHADBState();

	    // verify state of nodes
	    // check to make sure that down nodes are in the state
	    // we expect them to be, give db 10 mins to reach a 
	    // a sane state
	    boolean nodesVerified = true;
	    Iterator iter = nodesToDisable.iterator();
	    while(iter.hasNext()) {
		if (!verifyNodeState((ClusterNode)iter.next(), m_nodeAction, 
			m_clusterOp, m_shouldWipe)) {
		    nodesVerified = false;
		}
	    }

	    printStateOfDisableNodes(nodesToDisable, "Post hadb node verify");
	    
	    int missingNodes = getNumMissingNodes(m_nodeAction, m_clusterOp, 
		    m_shouldWipe, nodesToDisable.size());
	    int disNodes = getNumDisabledNodes(m_nodeAction, m_clusterOp, 
		    m_shouldWipe, nodesToDisable.size());
	    if (nodesVerified && 
		    Util.waitForDBSanity(m_rebootTimeOutValue, 
			    1000 * 60 * 2,m_numberOfNodes,disNodes,
			    missingNodes, true)) {
		Log.INFO("Domain verification passed!");
	    } else {
		Log.INFO("Domain verification failed!");
		break;
	    }
	    
	    printStateOfDisableNodes(nodesToDisable, "Pre CMM verify");
	  
	    // verify membership
	    if (!m_cluster.verifiedAndExpectedState(m_clusterOp == 
		CLUSTER_OP_NOTHING)) {
		Log.ERROR("Failed cmm related checks");
		break;
	    }
	    
	    // the main reason we sync state here is to 
	    // reset the master failover flag
	    m_cluster.syncState();
	    
	    // Turn back on the nodes I did not bring up
	    // The extend domain turns them back on for me
	    if (m_clusterOp != CLUSTER_OP_EXTEND && 
		    (m_nodeAction == NODE_ACTION_DISABLE || 
			    m_nodeAction == NODE_ACTION_KILL )) {
		boolean useHCStop = true;
		if (m_nodeAction == NODE_ACTION_KILL 
			&& m_killMode == KILL_MODE_HARD) {
		    useHCStop = false;
		} 
		Utils.restartNodes(nodesToDisable,useHCStop);
		if (!Util.waitForDBSanity(m_rebootTimeOutValue, 
			10 * 60 * 1000, m_cluster.getNumNodes(), 0, 0, true)) {
		    Log.WARN("DB never achieved sane state " +
		    "after renabling nodes");
		    break;
		}
	    
	    	// verify membership
	    	if (!m_cluster.verifiedAndExpectedState(true)) {
			Log.ERROR("After re-enabling downed nodes, " +
					"failed cmm related checks");
			break;
	    	}
	    }
	    
	    m_cluster.syncState();

	    if (m_verboseDataCollection) {
		File repFilesCopy = new File(m_logDir,"repositories_"
			+ currentIteration);
		if (repFilesCopy.mkdir()) {
		    try { 
			Util.copyRepositoryDirs(m_numberOfNodes,
				repFilesCopy.getAbsolutePath());
		    } catch (IOException ioe) {
			Log.WARN("Encoutered an IOException while attempting " +
				"to retrieve repository files");
			Log.WARN("Repository files will not be retrieved.");
			Log.WARN(ioe.getMessage());
		    }
		} else {
		    Log.WARN("Was unable to create directory " + 
			    repFilesCopy.getAbsolutePath());
		    Log.WARN("Repository directories will not copied.");
		}
	    }

	    Log.INFO("Finished iteration number: " + currentIteration);
	    currentIteration++;

	}

	//shutdown
	shutdown();

	//how did I do
	if (currentIteration > m_numberOfIterations) {
	    Log.INFO("Test Passed!");
	    return EXIT_CODE_SUCCESS;
	} else {
	    Log.INFO("Test Failed! Was only able to run " + 
		    (currentIteration -1 ) + " iterations");
	    return EXIT_CODE_FAILED;
	}

    }

    
    private void parseArgs(LinkedList args) 
    throws InvalidArgException {
	
	while (!args.isEmpty()) {
	
	    String curArg=(String)args.removeFirst();
	    
	    if (curArg.equals(CLI_OPT_HELP) || 
		curArg.equals(CLI_OPT_HELP_SHORT)) {
		usage();
	    } else if (curArg.equals(CLI_OPT_WIPE) || 
		       curArg.equals(CLI_OPT_WIPE_SHORT)) {
		m_wipeClusterAtStart=true;
	    } else if (curArg.equals(CLI_OPT_ITERS) || 
		       curArg.equals(CLI_OPT_ITERS_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify " +
		    		"the number of reboot iterations"+
		    		"with the " + curArg + " option");
		}
		try {
		    m_numberOfIterations=Integer.parseInt((String)args.peek());
		    args.remove();
	    	} catch (NumberFormatException nfe) {
	    	    throw new InvalidArgException("You must specify " +
	    	    		"the number of reboot iterations"+
	    	    		"with the " + curArg + " option. " + 
	    	    		"The value you specified '"+args.remove() +
			    	"' is not a number");
	    	}
	    } else if (curArg.equals(CLI_OPT_SLEEP) || 
		       curArg.equals(CLI_OPT_SLEEP_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify the " +
		    		"number of seconds to sleep " +
		    		"with the " + curArg + " option");
		}
		try {
		    m_rebootTimeOutValue=Integer.parseInt((String)args.peek()) * 1000;
		    args.remove();
	    	} catch (NumberFormatException nfe) {
	    	    throw new InvalidArgException("You must specify the " + 
	    		    "number a number of seconds with the " + curArg + 
	    		    " option. The value you specified '"+args.remove() + 
	    		    "' is not a number");
	    	}
	    } else if (curArg.equals(CLI_OPT_SCRIPT) || 
		       curArg.equals(CLI_OPT_SCRIPT_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify the name" + 
			    "of a script with the " + curArg + " option");
		}
		String scriptname = (String)args.remove();
		try {
		    m_preRebootScripts.add(initScript(scriptname, args));
		} catch (BadScriptException bs) {
		    throw new InvalidArgException("The script you specified " + 
			    scriptname + " is not valid." + bs.getMessage());
		}
	    } else if (curArg.equals(CLI_OPT_LOG) || 
		       curArg.equals(CLI_OPT_LOG_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify a path to " +
		    		"directory where log files will be placed with " 
			        + CLI_OPT_LOG + " option.");
		}
		m_logDir = new File((String)args.remove());
	    } else if (curArg.equals(CLI_OPT_NOGL)) {
		m_honeycombInsideGreenline=false;
	    } else if (curArg.equals(CLI_OPT_KILL_MIRRORS) || 
		       curArg.equals(CLI_OPT_KILL_MIRRORS_SHORT)) {
		m_allowMirrors = true;
	    } else if (curArg.equals(CLI_OPT_ALLOW_SPARES) || 
		       curArg.equals(CLI_OPT_ALLOW_SPARES_SHORT)) {
		m_allowSpares = true;
	    } else if (curArg.equals(CLI_OPT_ROLL_NODES) || 
		       curArg.equals(CLI_OPT_ROLL_NODES_SHORT)) {
		m_reuseNodes = false;
	    } else if (curArg.equals(CLI_OPT_NOHC)) {
		m_onlyRebootHoneycomb=true;
	    } else if (curArg.equals(CLI_OPT_NUMBER_NODES) || 
		       curArg.equals(CLI_OPT_NUMBER_NODES_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify the number " +
		    		"of nodes you want to disable with the " 
			        + curArg + " option");
		}
		m_numDownNodes=Integer.parseInt((String)args.remove());
	    } else if (curArg.equals(CLI_OPT_WIPE_TIME) || 
		       curArg.equals(CLI_OPT_WIPE_TIME_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify the number " +
		    		"of seconds you wish to wait for a wipe " +
		    		"with the " + curArg + " option");
		}
		m_wipeTimeOutValue=Integer.parseInt((String)args.remove()) * 1000;
	    }  else if (curArg.equals(CLI_OPT_CLUSTER_OPT) || 
		        curArg.equals(CLI_OPT_CLUSTER_OPT_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify a cluster" +
		    		" operation with the " + curArg + " option");
		}
		String operation = (String)args.remove();
		if (operation.equalsIgnoreCase(CLUSTER_OP_REBOOT_STR)) {
		    m_clusterOp = CLUSTER_OP_REBOOT;
		} else if (operation.equalsIgnoreCase(CLUSTER_OP_HRDREBOOT_STR)) {
		    m_clusterOp = CLUSTER_OP_HARDREBOOT;
		} else if (operation.equalsIgnoreCase(CLUSTER_OP_NOTHING_STR)) {
		    m_clusterOp = CLUSTER_OP_NOTHING;
		} else if (operation.equalsIgnoreCase(CLUSTER_OP_EXTEND_STR)) {
		    m_clusterOp = CLUSTER_OP_EXTEND;
		    m_allowSpares = true;
		    m_allowMirrors = true;
		} else {
		    throw new InvalidArgException("Unrecognized cluster " +
		    		"operation: " + operation); 
		}
	    } else if (curArg.equals(CLI_OPT_NODE_ACTION) || 
		       curArg.equals(CLI_OPT_NODE_ACTION_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify a node " +
		    		"action with the " + curArg + " option");
		}
		String action = (String)args.remove();
		if (action.equalsIgnoreCase(NODE_ACT_REBOOT_STR)) {
		    m_nodeAction = NODE_ACTION_REBOOT;
		} else if (action.equalsIgnoreCase(NODE_ACT_DISABLE_STR)) {
		    m_nodeAction = NODE_ACTION_DISABLE;
		} else if (action.equalsIgnoreCase(NODE_ACT_KILL_STR)) {
		    m_nodeAction = NODE_ACTION_KILL;
		} else if (action.equalsIgnoreCase(NODE_ACT_NOTHING_STR)) {
		    m_nodeAction = NODE_ACTION_NOTHING;
		} else {
		    throw new InvalidArgException("Unrecognized cluster " +
		    				 "operation: " + action); 
		}
	    } else if (curArg.equals(CLI_OPT_NUMBER_NODES) 
		    || curArg.equals(CLI_OPT_NUMBER_NODES_SHORT)) {
		if (args.isEmpty() || ((String)args.peek()).startsWith("-")) {
		    throw new InvalidArgException("You must specify " +
		    	"the number of nodes you want to act on with the " 
			+curArg + " option");
		}
		m_numDownNodes = Integer.parseInt((String)args.remove());
	    } else if (curArg.equals(CLI_OPT_KILL_HARD) 
		    || curArg.equals(CLI_OPT_KILL_HARD_SHORT)) {
		m_killMode = KILL_MODE_HARD;
	    } else if (curArg.equals(CLI_OPT_FOLLOW_MASTER) 
		    || curArg.equals(CLI_OPT_FOLLOW_MASTER_SHORT)) {
		m_alwaysNodeFaultMaster = true;
		m_reuseNodes = false;
	    } else {
		throw new InvalidArgException("Unknown option: " + curArg);
	    }
	}
    }
    
    
    private Script initScript (String script, LinkedList args) 
    throws BadScriptException {
	
	Class c = null;
	Object o = null;
	Script s = null;
	try {
	    c=Class.forName(script);
	} catch (ClassNotFoundException cnfe) {
	    throw new BadScriptException ("Can not find " + script + 
		    			" on class path");
	}
	
	try {
	    o=c.newInstance();
	} catch (IllegalAccessException iae) {
	    throw new BadScriptException ("Can not load script: " 
		    			  + iae.getMessage());
	} catch (InstantiationException ie) {
	    throw new BadScriptException ("Can not create instance of script: " 
		    			  + ie.getMessage());
	}
	
	if (o instanceof Script) {
	    s = (Script)o;
	    try {
		s.setUp(args);
	    } catch (IllegalArgumentException iae) {
		throw new BadScriptException("The arguments you specified " +
			"for the script " + Script.class.getCanonicalName() 
			+ " were not valid." + iae.getMessage());
	    }
	} else {
	    throw new BadScriptException("The script you specified " 
		    + Script.class.getCanonicalName() 
		    + " not implement the script interface");
	}
	return s;
    }
    
    
    private void doServers(String command) 
    throws HoneycombTestException {
	
	ExitStatus exitCode = null;
	for (int i = 1; i <= m_numberOfNodes; i++) {
	    String nodeName = ClusterNode.id2hostname(i);
	    ClusterNode curNode = m_cluster.getNode(nodeName);
	    if (curNode.isAlive()) {
		try {
		    exitCode=m_commandRunner.sshCmdStructuredOutput(nodeName,command);
		}catch (HoneycombTestException hte) {
		    throw new HoneycombTestException(
			    "Encountered a Honeycomb Test Exception while " +
			    "executing '" + command + "' on " + nodeName,hte );
		}
		if (exitCode.getReturnCode() != 0) {
		    throw new HoneycombTestException("Executing '" + command +
			    "' on " + nodeName + "resulted in non-zero " +
			    "exit code" );
		}
	    } else {
		Log.WARN("Node " + nodeName + " is not alive. Skipping. ");
	    }
	}
	
    }
    
    
    private void shutdown() {
	
	File domainSummary = new File(m_logDir,"domain_summary.out");
	File dbSummary = new File(m_logDir,"db_summary.out");
	try { 
	    Util.printDomainStatus(HoneycombTestConstants.ADMIN_VIP, 
		    Util.HADB_DEFAULT_PORT, new PrintStream(domainSummary));
	} catch (IOException ioe) {
	    Log.WARN("Problems encountered while getting domain summary");
	    Log.WARN(ioe.getMessage());
	} catch (HADBException he) {
	    Log.WARN("Problems encountered while getting domain summary");
	    Log.WARN(he.getMessage());
	}
	
	try { 
	    Util.printDatabaseStatus(HoneycombTestConstants.ADMIN_VIP, 
		    Util.HADB_DEFAULT_PORT, new PrintStream(dbSummary));
	} catch (IOException ioe) {
	    Log.WARN("Problems encountered while getting database summary");
	    Log.WARN(ioe.getMessage());
	} catch (HADBException he) {
	    Log.WARN("Problems encountered while getting database summary");
	    Log.WARN(he.getMessage());
	}
	
    // Future Cleanup: can we combine this with the above to only do this once?
    Log.INFO("At end of test, HADB state is:");
    logHADBState();
    }
    
    
    private void rebootCluster () 
    throws HoneycombTestException {
	
	if (m_testMode) {
	    Log.INFO("I am in test mode, skipping reboot");
	    return;
	}
	
	BufferedReader mybr = null; 
	
	// issue the reboot command
	if  (m_clusterOp == CLUSTER_OP_HARDREBOOT) {
	    ClusterMembership cm = new ClusterMembership(-1,
		    HoneycombTestConstants.ADMIN_VIP);
	    cm.setQuorum(true);
	    cm.initClusterState();
	    Iterator iter = cm.getLiveNodes().iterator();
	    while (iter.hasNext()) {
		ClusterNode cn = (ClusterNode)iter.next();
		try {
		    cn.reboot();
		} catch (HoneycombTestException hte) {}
		//we ignore the HTE above because it caused by the reboot
		//closing the ssh connection
	    }
	} else {
	    try {
            // CLI reboot, second arg means to exit the test if reboot fails
            mybr = m_cli.runCommandLogErrors("reboot -F", true);
	    } catch (Throwable t) {
            HoneycombTestException hte = new HoneycombTestException("Reboot " +
                "command resulted in exception.");
            try {
                hte.initCause(t);
            } catch (Throwable ignored) {};
            try {
                if (mybr != null) 
                    mybr.close();
            } catch (Throwable ignore) {}
            throw hte;
        }
	
        if (mybr == null ) {
            Log.WARN("Can not read output from reboot command. " +
                "The buffered reader returned is null");
        } else {
            try {
                String s = mybr.readLine();
                Log.INFO("Begin stdout for cmd reboot");
                while (s != null) {
                    Log.INFO(s);
                    s = mybr.readLine();
                }
                Log.INFO("End stdout for cmd reboot");
            }catch (IOException ioe) {
                Log.WARN("Unable to retrieve output from reboot command." +
                    " IOException during read" +
                    ioe.getMessage());
            } finally {
                try {
                    if (mybr != null)
                        mybr.close();
                } catch (Throwable ignore) {}
            }
        }
    }
		
    }
    
    private boolean waitForHADB () {
	
	//	 start the timer
	long timeout =System.currentTimeMillis() + m_rebootTimeOutValue;
	String hadbStatus = "unset/initialStatus";
	String hadbStatusLast = "unset/initialStatusLast";
	boolean success = false;
	String expectedState="HAFaultTolerant";
	if (m_numDownNodes == 2 || m_numberOfNodes <= 8) {
	    expectedState = "FaultTolerant";
	} 
	Log.INFO("waiting on HADB to be " + expectedState);
	Log.INFO("will allow " + m_rebootTimeOutValue + " msecs " +
        " to reach that state");
	
	while (System.currentTimeMillis() < timeout) {
	    BufferedReader br = null;

	    // check to see if the cluster is back
	    try {
            // second arg means don't exit if status command fails
		br = m_cli.runCommandLogErrors("hadb status -F", false);
	    } catch (Throwable t) {
		Log.DEBUG("Encountered a throwable while attempting to" +
                " get HADB Status from CLI");
		Log.DEBUG(t.getMessage());
		try {
		    if (br != null)
			br.close();
		} catch (Throwable ignore) {}
	    }


	    // if so, return
	    if (br != null) {
		try {
		    Log.INFO("Begin stdout for cmd hadb status looking for state "
                    + expectedState);
		    hadbStatus = br.readLine();
		    while (hadbStatus != null) {
			Log.INFO("stdout: " + hadbStatus);
			if (inExpectedState(expectedState, hadbStatus)) {
			    Log.INFO("HADB has reached: " + expectedState);
			    success=true;
			    break;
			}

			// remember the last state for printing below.
			// the state is currently the last thing printed.
			// this is a bit fragile, but works for now.
			// otherwise, we always end up with a null.
			hadbStatusLast = hadbStatus;
			hadbStatus = br.readLine();
		    }
		    Log.INFO("End stdout for cmd hadb status");
		    if(success == true) {
			try {
			    if (br != null)
				br.close();
			} catch (Throwable ignore) {}

			//exit the loop and return success.
			break;
		    }
		} catch (IOException ioe) {
		    Log.WARN("Encountered an IOException while reading " +
                    "output from cli.");
		} finally {
		    try {
			if (br != null)
			    br.close();
		    } catch (Throwable ignore) {}
		}
        }
	    
	    // sleep
	    
	    // print device sizes
	    List liveNodes = m_cluster.getLiveNodes();
	    if (liveNodes != null) {
		Iterator iter = liveNodes.iterator();
		ClusterNode curNode = null;
		while (iter.hasNext()) {
		    curNode = (ClusterNode)iter.next();
		    String name = curNode.getName();
		    if (!curNode.ping()) {
			Log.INFO("Node " + name + 
				" is not up. Skipping device size printout");
		    } else {
			Log.INFO(name +": DB Device Size=" + 
				Util.getHADBFileSize(name, Util.DATABASE_FILE_TYPE) + 
				" RelAlg File Size=" + 
				Util.getHADBFileSize(name, Util.RELALG_FILE_TYPE));
		    }
		}
	    } else {
		Log.INFO("No live nodes at the moment. " +
				"Will skip device size printout");
	    }
	    Log.INFO("HADB Current Status: " + 
		    (hadbStatus == null ? hadbStatusLast : hadbStatus) + 
		    "; Expected Status: " + expectedState +
            "; Time Remaining: " +
		    (timeout - System.currentTimeMillis() ) / 1000 );
	    Util.sleeper(1000 * 30); // 30 seconds
	}
	
	return success;
    }
    
   
    private boolean verifyNodeState(ClusterNode cn, int nodeAction, 
	                             int clusterOp, boolean wiped) 
    throws HADBException, IllegalArgumentException, HoneycombTestException {
	boolean verified = true;
	if (nodeAction == NODE_ACTION_DISABLE) {
	    verified =  verifyDisabledNode(cn.getName(), clusterOp, wiped);
	} else if (nodeAction == NODE_ACTION_KILL) {
	    verified = verifyKilledNode(cn.getName(), clusterOp, wiped);
	} else if (nodeAction == NODE_ACTION_REBOOT) {
	    verified = verifyRebootedNode(cn.getName(), clusterOp);
	} else if ( nodeAction == NODE_ACTION_NOTHING) {
	    verified = isNodeEnabledAndRunning(cn.getName());
	} else {
	    throw new IllegalArgumentException("Unknown node action " 
		      + nodeAction);
	}
	return verified;
    }
    
    /*
     * If cluster was rebooted, domainmember should !Enabled 
     * If cluster was wiped domainmember should not be present 
     *    && 1 more should be missing 
     * If nothing, domainmember should be enabled and running
     * 
     */
    private boolean verifyDisabledNode(String hostName, int clusterOp, 
	              		       boolean wipe) 
    throws HADBException, IllegalArgumentException, HoneycombTestException {
	HADBDomainMember dm = Util.getDomainMember(hostName);
	boolean returnCode = true;
	if (clusterOp == CLUSTER_OP_REBOOT || 
	    clusterOp == CLUSTER_OP_HARDREBOOT ) {
	    if (!wipe) {
		if (dm == null ) {
		    Log.INFO("The node " + hostName + " should be present but " +
			"can not be found in the domain.");
		    returnCode = false;
		} else if (dm.isEnabled()) {
		    Log.INFO("The node " + hostName + " should be disabled " +
		    "but is currenlty enabled.");
		    returnCode=false;
		}
	    } else {
		if (dm != null ) {
		    Log.INFO("The node " + hostName + " should not be present " +
			 "but can be found in the domain.");
		    returnCode = false;
		}
	    }
	} else if (clusterOp == CLUSTER_OP_NOTHING || 
		   clusterOp == CLUSTER_OP_EXTEND) {
		returnCode = isNodeEnabledAndRunning(hostName);
	} else {
	    throw new IllegalArgumentException("Unknown cluster operation " + 
		      				clusterOp);
	}
	return returnCode;
    }
    
    /* If cluster was wiped, domainmember should be missing
     * otherwise, we should be in the domain but not enabled or running
     */
    private boolean verifyKilledNode(String hostName, int clusterOp, 
	                     	     boolean wipe) 
    throws HADBException {
	boolean returnCode = true;
	HADBDomainMember dm = Util.getDomainMember(hostName);
	
	if (wipe) {
	    if ( dm != null) {
		Log.INFO("The killed node " + hostName + " is in " +
    		"the domain but should not be.");
    		returnCode = false;
	    }
	} else {
	    if (dm.isEnabled()) {
		Log.INFO("The killed node " + hostName + " is enabled " +
				"but should not be");
		returnCode = false;
	    } else if (dm.isRunning()) {
		Log.INFO("The killed node " + hostName + " is running " +
		"but should not be");
		returnCode = false;
	    }
	}
	return returnCode;
    }
    
    /* If cluster was rebooted, domainmember should Enabled && Running
     * If cluster was wiped, domainmember should Enabled && Running
     * If nothing, domainmember should Enabled && Running
     */
    private boolean verifyRebootedNode(String hostName, int clusterOp) 
    throws HADBException, HoneycombTestException, IllegalArgumentException {
	boolean returnCode = true;
	if (clusterOp == CLUSTER_OP_REBOOT || clusterOp == CLUSTER_OP_HARDREBOOT
		|| clusterOp == CLUSTER_OP_NOTHING ) {
	    returnCode = isNodeEnabledAndRunning(hostName);
	} else {
	    throw new IllegalArgumentException("Unknown cluster operation " 
		    				+ clusterOp);
	}
	return returnCode;
    }
    
    
    private boolean isNodeEnabledAndRunning(String hostName) 
    throws HADBException, HoneycombTestException {
	boolean returnCode = true;
	HADBDomainMember dm = Util.getDomainMember(hostName);
	if (dm == null ) {
		Log.INFO("The rebooted node " + hostName 
			 + " is not in the domain but should be.");
		returnCode = false;
	} else if (!dm.isEnabled()) {
		Log.INFO("The rebooted node " + hostName + " is not enabled " +
		 "but should be.");
		returnCode = false;
	} else if (!dm.isRunning()) {
		Log.INFO("The rebooted node " + hostName + " is not running " +
		 "but should be.");
		returnCode = false;
	} else {
	    returnCode = Util.nodeIsInDatabase(hostName);
	}
	
	return returnCode;
    }
    
    
    private void runScripts(HashSet scripts) 
    throws HoneycombTestException{
	Iterator iter = scripts.iterator();
	Script currentScript = null;
	while (iter.hasNext()) {
	    try {
		currentScript = (Script) iter.next();
		Log.INFO("About to run script " + currentScript.getName());
		currentScript.executeScript(this);
	    } catch(Throwable t) {
		Log.WARN("An exception was encountered while executing script " 
			 + currentScript.getName());
		Log.WARN(t + ": " + t.getMessage());
		Log.stackTrace(t);
		throw (new HoneycombTestException("Failed executing script: " + 
			currentScript.getName()));
	    }
	}
    }
   
    private boolean stopScripts(HashSet scripts) {
	Iterator iter = scripts.iterator();
	Script currentScript = null;
	boolean scriptsOK = true;
	while (iter.hasNext()) {
	    try {
		currentScript = (Script) iter.next();
		Log.INFO("About to stop script " + currentScript.getName());
		currentScript.stop();
		if (currentScript.getResult() == Script.RETURN_CODE_FAILED) {
		      Log.WARN("The script " + currentScript.getName() + 
			       " failed.");
		      scriptsOK = false;
		}
	    } catch(Throwable t) {
		Log.WARN("An exception was encountered while stopping script " 
			 + currentScript.getName());
		Log.WARN(t + ": " + t.getMessage());
		Log.stackTrace(t);
	    }
	}
	return scriptsOK;
    }
    

    
   public void disableNode(ClusterNode node,int nodeAction, int killMode) 
   throws HoneycombTestException, HADBException {
       if (nodeAction == NODE_ACTION_NOTHING) {
	   return;
       }
	Log.DEBUG("About to disable node " + node.getName());
	if (!node.ping()) {
	    throw new HoneycombTestException("Node " + node.getName() + 
		    			     " appears to be down (cant ping)");
	}
	if (!m_testMode) {
	    if (nodeAction == NODE_ACTION_DISABLE) {
		node.setupDevMode();
		node.setOfflineState();
		Log.INFO(node.getName() +" disabled! State: " 
			+ node.getAllStateInfo());
	    } else if (nodeAction == NODE_ACTION_KILL) {
		node.setupDevMode();
		if (killMode == KILL_MODE_SOFT) {
		    Log.INFO("About to kill node " + node.getName() + 
			    " by pkilling all procs");
		    node.pkillJava();
		    node.pkillHadb();
		} else {
		    try {
			Log.INFO("About to kill node " + node.getName() + 
				" with reboot -q");
			node.rebootQuick();
		    } catch (HoneycombTestException hte){} 
		    //Ignore the hte, its due the reboot closing the connection
		}
		node.setOfflineState();
		Log.INFO(node.getName() +" killed! State:" 
			+ node.getAllStateInfo());
	    } else if (nodeAction == NODE_ACTION_REBOOT) {
		node.removeDevMode();
		try {
		    if (killMode == KILL_MODE_SOFT) {
			node.reboot();
		    } else {
			node.rebootQuick();
		    }
		} catch (HoneycombTestException hte){} 
		//Ignore the hte, it is due the reboot closing the connection
		Log.INFO(node.getName() +" rebooted! State: "+
			node.getAllStateInfo());
	    } else {
		throw new HoneycombTestException("Unrecognized node action.");
	    }
	} else {
	    Log.INFO("Would have disabled " + node.getName() + 
		     " but I am in test mode.");
	}
   }
   

   
   private boolean inExpectedState (String expectedState, String curState) {
       if (expectedState.equals(curState)) {
	   return true;
       } else {
	   if (expectedState.equals("FaultTolerant") && 
	   	curState.equals("HAFaultTolerant")) {
	       Log.WARN("I was expecting FaultTolerant, got HAFaultTolerant. Good enough..");
	       return true;
	   } else {
	       return false;
	   }
       }
   }
   
   
   
    /*
     * Here's a chart that explains when things are disabled or missing.
     * It describes the logic for getNumDisabledNodes and 
     * getNumMissingNodes.   The Cluster Operation and the Node Action
     * are the indices to the table.  These values are the arguments 
     * passed to the reboot test when invoked.  
     *
     * The trickiest case is disable, which only takes effect if the
     * node has been rebooted.  
     *
     * If a wipe is expected, then the Wipe column must be used 
     * since the domain will have been recreated and nodes might be 
     * noticed as missing at that time depending on their state.

     R = running
     D = disabled 
     M = missing from domain
     
                        Cluster Operation
                        -----------------
               | Reboot      Nothing     Extend    Wiped 
     ----------
N A   Nothing       R           R           R         R
o c   Kill          D           D           D         M
d t   Disable       D           R           R         R (M if rebooted)
e i   Reboot        R           R           R         R
  o
  n

     */

   private int getNumDisabledNodes (int nodeAction, int clusterOp, 
	   boolean wiped, int numNodes) {
       
       switch (nodeAction) {
       case NODE_ACTION_NOTHING:
	   return 0;
       case NODE_ACTION_REBOOT:
	   return 0;
       case NODE_ACTION_KILL:
	   if (!wiped) {
	       return numNodes;
	   } else {
	       return 0;
	   }
       case NODE_ACTION_DISABLE:
	   if (clusterOp == CLUSTER_OP_REBOOT && !wiped) {
	       return numNodes;
	   } else {
	       return 0;
	   } 
       }
       return 0;
       
   }
   
   
  private int getNumMissingNodes (int nodeAction, int clusterOp, 
	  boolean wiped, int numNodes) {
       
       if (wiped) {
	   if (nodeAction == NODE_ACTION_KILL || 
		   (nodeAction == NODE_ACTION_DISABLE && 
		   clusterOp == CLUSTER_OP_REBOOT)) {
	       return numNodes;
	   }
       }
       return 0;
       
   }
  
  private void printStateOfDisableNodes (Collection nodes, String message) {
      Log.INFO("Checking state of disabled nodes: " + message);
      Iterator iter = nodes.iterator();
      while (iter.hasNext()) {
	  Log.INFO(((ClusterNode)iter.next()).getAllStateInfo());
      }
  }
   
   private void printConfig() {
       Log.INFO("Iterations: " + m_numberOfIterations);
       Log.INFO("Cluster Operation: "+ CLUSTER_OPS_DESC[m_clusterOp]);
       Log.INFO("Node Action: " + NODE_ACT_DESC[m_nodeAction]);
       Log.INFO("Number of Affected Nodes: " + m_numDownNodes);
       Log.INFO("Reuse Nodes: " + m_reuseNodes);
       Log.INFO("Kill Mirrors: " + m_allowMirrors);
       Log.INFO("Allow Spares: " + m_allowSpares);
       Log.INFO("Wipe TimeOut (secs): " + (m_wipeTimeOutValue/1000));
       Log.INFO("Wait on HADB Timeout (secs): " + m_rebootTimeOutValue/1000);
       Log.INFO("Should wipe: " + m_shouldWipe);
       
       File domainSummary = new File(m_logDir,"domain_summary.start");
       File dbSummary = new File(m_logDir,"db_summary.start");
       try { 
	   Util.printDomainStatus(HoneycombTestConstants.ADMIN_VIP, 
		   Util.HADB_DEFAULT_PORT,new PrintStream(domainSummary));
       } catch (IOException ioe) {
	   Log.WARN("Problems encountered while getting domain summary");
	   Log.WARN(ioe.getMessage());
	} catch (HADBException he) {
	    Log.WARN("Problems encountered while getting domain summary");
	    Log.WARN(he.getMessage());
	}
	
	try { 
	    Util.printDatabaseStatus(HoneycombTestConstants.ADMIN_VIP, 
		    Util.HADB_DEFAULT_PORT,new PrintStream(dbSummary));
	} catch (IOException ioe) {
	    Log.WARN("Problems encountered while getting database summary");
	    Log.WARN(ioe.getMessage());
	} catch (HADBException he) {
	    Log.WARN("Problems encountered while getting database summary");
	    Log.WARN(he.getMessage());
	}
   }
    
    
    class InvalidArgException extends Exception {
	
	InvalidArgException(String desc) {
	    super (desc);
	}
	
    }
    
   
    
}
