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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;



public class SwitchFailover extends HoneycombRemoteSuite {
    private Hashtable hash = new Hashtable();
    private String NOHONEYCOMB = "/config/nohoneycomb";
    private String NOREBOOT = "/config/noreboot";
    private String SSH_SWITCH = "ssh -p 2222 -l nopasswd 10.123.45.1";
    private String SSH_MASTER_INTERNAL = "ssh root@10.123.45.200";
    private String SWITCH_CONF_FILE = "/etc/honeycomb/switch.conf";
    private String ENV_LOC = "/opt/test/bin/load/emi-load/";
    private String STRESS_LOG_LOCATION = "/mnt/test/emi-stresslogs/";
    private String START_LOAD = "start-master-stress.sh";
    private String STOP_LOAD = "kill-stress-test.sh";
    private String ANALYZE = "analyze-perf.sh";
    private String VRRP = "vrrpconfig -a";
    private String LOG_LOCATION = "/var/adm/messages";
    private String OTHER_SWITCH = "/usr/bin/run_cmd_other.sh";
    private String ZLC_UP = "zlc zre22 up";
    private String ZLC_DOWN = "zlc zre22 down";
    private String SWITCH_MASTER_PAT = "SWITCH_ID=1";
    private String SWITCH_BACKUP_PAT = "SWITCH_ID=2";
    private String QUERY_STATUS_PAT = "Query Engine Status: (\\w+)";
    private String ANALYZE_PERF_OK = " (\\d+) OK";
    private String ANALYZE_PERF_ERR = " (\\d+) ERR";
    private String QUERY_STATUS_STRING = "HAFaultTolerant";
    private String CAT_MGT_FILE = "cat /config/SUNWhadb/mgt.cfg";
    private String LS_HADB_LOG_HEAD1 = "ls -t /data/";
    private String LS_HADB_LOG_HEAD2 = "/hadb-logs/ | head -1";
    private String HADB_PAT = "logfile.name=/data/(\\d+)/hadb/log/ma.log";
    private String[] APC_CLUSTERS = {"dev309", "dev308", "dev319"};
    private String[] APC_IPS = {"10.7.224.186", "10.7.224.166", "10.7.225.166"};
    private long CHECK_CLUSTER_INTERVAL = 300000; // milliseconds
    private long SLEEP_WAKEUP_TIMEOUT = 60000; // milliseconds
    private long SLEEP_SWITCH_FAILOVER = 300000; // milliseconds
    private long SLEEP_SWITCH_FAILBACK = 300000; // milliseconds
    private long SLEEP_PORT_UP = 60000; // milliseconds
    private long SLEEP_PORT_DOWN = 60000; // milliseconds
    private long SLEEP_RECOVER = 300000; // milliseconds
    private long STORE_DURATION = 28800000; // milliseconds 8 hours
    private long SLEEP_NODE_DOWN = 300000; // milliseconds
    private long BACKUP_DURATION = 25200000; // milliseconds 7 hours
    private long MASTER_DURATION = 3600000; // milliseconds 1 hour
    private int MAX_ONLINE_ITERATIONS = 45; // iterations
    private int DISKS_PER_NODE = 4;
    private int NODE_NUM_OFFSET = 101;
    private int THREADS = 1;
    
    protected ClusterNode node1 = null;
    protected Thread portThread = null;
    protected CLI cli = null;
    protected BufferedReader stdout = null;
    protected long storeDuration = STORE_DURATION;
    protected long runLoadBackupSwitch = BACKUP_DURATION;
    protected long runLoadMasterSwitch = MASTER_DURATION;
    protected long interval = CHECK_CLUSTER_INTERVAL;
    protected int nodes = 0;
    protected int masterCell = 0;
    protected int threads = THREADS;
    protected int delete = 0;
    protected String[] clients = null;
    protected String apcCluster = null;
    protected String apcIP = null;
    protected String line = null;
    protected String adminVIP = null;
    protected String dataVIP = null;
    protected String nodesOnlineString = null;
    protected String oneDownNodesOnlineString = null;
    protected String cluster = null;
    protected String node = null;
    protected String queryStatusString = QUERY_STATUS_STRING;
    protected String originalHadbDate = null;
    protected boolean storeStatus = false;
    protected boolean isZrePortDown = false;
    protected boolean isPowerPortDown = false;
    protected boolean isNodeDown = false;
    protected boolean isSetup = false;
    protected TestCase self;
    public SwitchFailover() {
        super();
    }

    /* 
     * Tests must be run on a cluster that is already online.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        self = createTestCase("Switch Failover", "Runs multiple tests on second switch with and without load");
        self.addTag("switchFailover");
        if (self.excludeCase()) 
            return;
        
        super.setUp(); 
        
        for (int i = 0; i < APC_IPS.length; i++) {
            hash.put(APC_CLUSTERS[i], APC_IPS[i]);
        }
        
        cluster = 
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        String c =
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLIENTS);
        clients = c.split(",");
        
        if (!hash.containsKey(cluster)) {
            Log.ERROR("This test can only be run on:");
            
            for (int i = 0; i < APC_CLUSTERS.length; i++) {
                Log.ERROR("  " + APC_CLUSTERS[i]);
            }
            return;
        } else {
            apcCluster = cluster;
            apcIP = (String) hash.get(cluster);
        }
        
        
        if (!setupAPCScript()) {
            Log.ERROR("Unable to setup APC Script");
            return;
        }
        
        String loadTime = TestRunner.getProperty("time"); // hr
        String backupTime = TestRunner.getProperty("backup"); // hr
        String masterTime = TestRunner.getProperty("master"); // hr
        String intervalTime = TestRunner.getProperty("interval"); //minutes
        String threadsPerClient = TestRunner.getProperty("threads");
        String del = TestRunner.getProperty("delete");
        
        if (loadTime != null) {
            storeDuration = 
                (long) (Double.parseDouble(loadTime) * 60 * 60 * 1000);
        }
        if (backupTime != null) {   
            runLoadBackupSwitch =  
                (long) (Double.parseDouble(backupTime) * 60 * 60 * 1000);
        }
        if (intervalTime != null) { 
            interval = 
                (long) (Double.parseDouble(intervalTime) * 60 * 1000);
        }
        if (masterTime != null) {   
            runLoadMasterSwitch = 
                (long) (Double.parseDouble(masterTime) * 60 * 60 * 1000);
        } else {
            runLoadMasterSwitch = (storeDuration - runLoadBackupSwitch) / 2;
        }
        if (threadsPerClient != null) { 
            threads = Integer.parseInt(threadsPerClient);
        }
        if (del != null) {  
            delete = 1;
        }
        
        Log.INFO("Total store time (per load test): "
                + formatTime(storeDuration));
        Log.INFO("Total backup switch store time (per load test): "
                + formatTime(runLoadBackupSwitch));
        Log.INFO("Total master store time (per load test): "
                + formatTime(runLoadMasterSwitch));
        Log.INFO("Cluster checkup interval time: "
                + formatTime(interval));
        
        adminVIP = testBed.adminVIPaddr;
        dataVIP = testBed.dataVIP;
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        
        initAll(); 
        nodes = cli.hwstat(masterCell).getNodes().size();
        nodesOnlineString = nodes + " nodes online, " + 
            (DISKS_PER_NODE * nodes) + " disks online";
        oneDownNodesOnlineString = (nodes - 1) + " nodes online, " + 
            (DISKS_PER_NODE * (nodes -1)) + " disks online";
        node1 = new ClusterNode(adminVIP, 1);
        isSetup = true;
         
    }
    
    
    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        if (excludeCase()) return;
        super.tearDown();
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    //////////////////////////////////  TESTS /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // Passes!
    public boolean test1_SwitchFailover() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        Log.INFO("Runs a basic switch failover and failback");
        Log.INFO("Makes sure we are using switch 2 and switch 1, respectively");
        boolean ok_to_proceed = waitForClusterToStart();
        
        // verify that we are on switch 1
        if (ok_to_proceed)
        	ok_to_proceed = verifySwitch("master");
        
        // power off switch 1 through apc
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "Off");
        }

        // verify that we are on switch 2
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILOVER);
            ok_to_proceed = waitForClusterToStart(); 
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = verifySwitch("backup");
        }
        // power on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "On");
        }
   
        // verify that we are on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILBACK);
            ok_to_proceed = verifySwitch("master");
        }
        
        if (!ok_to_proceed) {
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        return ok_to_proceed;
    }
    
    public boolean test2_SwitchFailoverWithLoad() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        Log.INFO("Run a switch failover and failback while under heavy load");
        Log.INFO("Verifies correct switch use, and no change in cluster status");
        boolean ok_to_proceed = waitForClusterToStart();
    
        // Make sure all nodes are up, status is ok, etc...
        if (ok_to_proceed) {
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        // verify that we are on switch 1
        if (ok_to_proceed) {
            ok_to_proceed = verifySwitch("master");
        }
            
        // start load
        if (ok_to_proceed) {
            startLoad();
            
            Log.INFO("Continuously running load for " + formatTime(storeDuration));
            Log.INFO("    You can set this by using \"-ctx time=XXX\" (hours)");
            Log.INFO("    Default is " + formatTime(STORE_DURATION));
        
            // Wait while load runs on Master switch
            Log.INFO("  Running load on master switch for " + 
                    formatTime(runLoadMasterSwitch));
            Log.INFO("      You can explicitly set this by using \"-ctx master=XXX\" (hours)");
            Log.INFO("      Default is (time - backup) / 2");
            Log.INFO("    Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("        You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("        Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
            double endTime =
                System.currentTimeMillis() + runLoadMasterSwitch;
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(nodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }
            
            // power off switch 1 through apc
            ok_to_proceed = apcCommand(1, "Off");
        }
        
        
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILOVER);
            ok_to_proceed = waitForClusterToStart(); 
        }
        
        if (ok_to_proceed) {    
            ok_to_proceed = verifySwitch("backup");
            Log.INFO("Running load on backup switch for " +
                    formatTime(runLoadBackupSwitch));
            Log.INFO("    You can set this by using \"-ctx backup=XXX\" (hours)");
            Log.INFO("    Default is " + formatTime(BACKUP_DURATION));
            
            Log.INFO("  Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("      You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("      Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
            double endTime =
                System.currentTimeMillis() + runLoadBackupSwitch;
  
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(nodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }

        }
        
        // power on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "On");
        }
        
        // verify that we are on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILBACK);
            ok_to_proceed = verifySwitch("master");
            Log.INFO("Running load on master switch for " + 
                    formatTime(runLoadMasterSwitch));
            Log.INFO("    You can explicitly set this by using \"-ctx master=XXX\" (hours)");
            Log.INFO("    Default is (time - backup) / 2");
            Log.INFO("  Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("      You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("      Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
            double endTime =
                System.currentTimeMillis() + runLoadMasterSwitch;
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(nodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }
        }
        
  
        if (!ok_to_proceed) {
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        
        
        stopLoad();
        analyzeLoad();
        return ok_to_proceed;
    }
    
    // Passes
    public boolean test3_NetworkFailure() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        Log.INFO("Run a basic switch 1 network failure and failback");
        Log.INFO("Verifies correct switch is in use");
        boolean ok_to_proceed = waitForClusterToStart();
   
        // verify that we are on switch 1
        if (ok_to_proceed)
        	ok_to_proceed = verifySwitch("master");
   
        // shut down network port on switch 1
        if (ok_to_proceed) {
            portThread = new Thread(new PortUpDown("down"));
            portThread.start();
            pause(SLEEP_PORT_DOWN);
            ok_to_proceed = waitForClusterToStart(); 
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = verifySwitch("backup");
        }
        
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
 
        // bring up network port on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            portThread = new Thread(new PortUpDown("up"));
            portThread.start();
            pause(SLEEP_PORT_UP);
            ok_to_proceed = verifySwitch("master");
        }
        
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        if (!ok_to_proceed) {
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        
        return ok_to_proceed;
    }
    
    
    public boolean test4_NetworkFailureWithLoad() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        Log.INFO("Run a switch 1 network failure and failback while under heavy load");
        Log.INFO("Verifies correct switch use, and no change in cluster status");
        
        boolean ok_to_proceed = waitForClusterToStart();
            
        // Make sure all nodes are up, status is ok, etc...
        if (ok_to_proceed) {
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        // verify that we are on switch 1
        if (ok_to_proceed) {
            ok_to_proceed = verifySwitch("master");
        }
            
        // start load
        if (ok_to_proceed) {
            startLoad();
            
            Log.INFO("Continuously Running load for " + formatTime(storeDuration));
            Log.INFO("    You can set this by using \"-ctx time=XXX\" (hours)");
            Log.INFO("    Default is " + formatTime(STORE_DURATION));
            
            // Wait while load runs on Master switch
            Log.INFO("  Running load on master switch for " + 
                    formatTime(runLoadMasterSwitch));
            Log.INFO("      You can explicitly set this by using \"-ctx master=XXX\" (hours)");
            Log.INFO("      Default is (time - backup) / 2");
            Log.INFO("    Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("        You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("        Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
            double endTime =
                System.currentTimeMillis() + runLoadMasterSwitch;
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(nodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }
            
            // shut down network port on switch 1
            portThread = new Thread(new PortUpDown("down"));
            portThread.start();
            pause(SLEEP_PORT_DOWN);
            ok_to_proceed = waitForClusterToStart(); 
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = verifySwitch("backup");
            
        }
        
        
        // Load on switch 2
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            Log.INFO("Running load on backup switch for " +
                    formatTime(runLoadBackupSwitch));
            Log.INFO("    You can set this by using \"-ctx backup=XXX\" (hours)");
            Log.INFO("    Default is " + formatTime(BACKUP_DURATION));
            Log.INFO("  Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("      You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("      Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
            double endTime =
                System.currentTimeMillis() + runLoadBackupSwitch;
  
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(nodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }
        }
        
        // bring up network port on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            portThread = new Thread(new PortUpDown("up"));
            portThread.start();
            pause(SLEEP_PORT_UP);
            ok_to_proceed = verifySwitch("master");
        }
        
        // Load on master
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            Log.INFO("Running load on master switch for " + 
                    formatTime(runLoadMasterSwitch));
            Log.INFO("    You can explicitly set this by using \"-ctx master=XXX\" (hours)");
            Log.INFO("    Default is (time - backup) / 2");
            Log.INFO("  Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("      You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("      Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
            double endTime =
                System.currentTimeMillis() + runLoadMasterSwitch;
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(nodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }
        }
    
        if (!ok_to_proceed) {
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        
        stopLoad();
        analyzeLoad();
        return ok_to_proceed;
    }
    
    public boolean test5_SwitchFailoverNodeDownWithLoad() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        Log.INFO("Run a switch failover and failback while under heavy load");
        Log.INFO("While on switch 2, power a node down");
        Log.INFO("Verifies correct switch use, and no change in cluster status");
        
        boolean ok_to_proceed = waitForClusterToStart();
        
        if (ok_to_proceed) {
            // verify that we are on switch 1
            ok_to_proceed = verifySwitch("master");
        }
        
        // power off switch 1 through apc
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            //Have to start on sw1
            startLoad();
            
            Log.INFO("Continuously Running load for " + formatTime(storeDuration));
            Log.INFO("    You can set this by using \"-ctx time=XXX\" (hours)");
            Log.INFO("    Default is " + formatTime(STORE_DURATION));
            // Wait while load runs on Master switch
            Log.INFO("  Running load on master switch for " + 
                    formatTime(runLoadMasterSwitch));
            Log.INFO("      You can explicitly set this by using \"-ctx master=XXX\" (hours)");
            Log.INFO("      Default is (time - backup) / 2");
            Log.INFO("    Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("        You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("        Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
            double endTime =
                System.currentTimeMillis() + runLoadMasterSwitch;
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(nodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = apcCommand(1, "Off");
        }
        
        // verify that we are on switch 2
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILOVER);
            ok_to_proceed = waitForClusterToStart(); 
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = verifySwitch("backup");
            Log.INFO("Running load on backup switch for " +
                    formatTime(runLoadBackupSwitch));
            Log.INFO("    You can set this by using \"-ctx backup=XXX\" (hours)");
            Log.INFO("    Default is " + formatTime(BACKUP_DURATION));
            
            Log.INFO("  Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("      You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("      Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
        }
    
        if (ok_to_proceed) {
            // node down
            ok_to_proceed = nodeDown();
        }
        
        // check cluster continuously
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            storeStatus = true;
            double endTime =
                System.currentTimeMillis() + runLoadBackupSwitch;
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(oneDownNodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }
        }
                
        // power on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "On");
        }
   
        // verify that we are on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILBACK);
            ok_to_proceed = verifySwitch("master");
        }
        
        // Load on master
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            Log.INFO("Running load on master switch for " + 
                    formatTime(runLoadMasterSwitch));
            Log.INFO("    You can explicitly set this by using \"-ctx master=XXX\" (hours)");
            Log.INFO("    Default is (time - backup) / 2");
            Log.INFO("  Checking cluster status every " +
                    formatTime(interval));
            Log.INFO("      You can set this by using \"-ctx interval=XXX\" (minutes)");
            Log.INFO("      Default is " + formatTime(CHECK_CLUSTER_INTERVAL));
            double endTime =
                System.currentTimeMillis() + runLoadMasterSwitch;
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(oneDownNodesOnlineString);
                if (ok_to_proceed)
                    sleep(interval);
            }
        }
   
        // power on node
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = nodeUp();
        }
        
        if (!ok_to_proceed) {
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        
        stopLoad();
        analyzeLoad();
        return ok_to_proceed;
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////// HELPERS /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    protected boolean checkCluster(String grep) throws Throwable {
        boolean pass = true;

        ArrayList sysstat = readSysstat();
        // All nodes still up?
        if (!sysstat.toString().contains(grep)) {
            Log.ERROR(sysstat.toString());
            Log.ERROR("Detected a change in disks/nodes");
            ArrayList sysstatV = readLogSysstatV();
            pass = false;
        }
  
        if (storeStatus) {
            storeStatus = false;
            String pat = QUERY_STATUS_PAT;
            Pattern pattern = Pattern.compile(pat, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(sysstat.toString());
            if (matcher.find()) {
                queryStatusString = matcher.group(1);
                Log.INFO("Query Status: " + queryStatusString);
            } else {
                Log.ERROR(sysstat.toString());
                Log.ERROR("Could not determine HADB Status");
                pass = false;
            }
        }
        // HADB did not change
        if (!sysstat.toString().contains(queryStatusString)) {
            Log.ERROR(sysstat.toString());
            Log.ERROR("Detected a change in HADB status");
            pass = false;
        }

        // HADB did not wipe
        if (originalHadbDate == null)
            originalHadbDate = getHadbLogDate();
        String newHadbDate = getHadbLogDate();
        if (!originalHadbDate.equals(newHadbDate)) {
            Log.ERROR("Original HADB log date: " + originalHadbDate);
            Log.ERROR("Returned HADB log date: " + newHadbDate);
            Log.ERROR("Detected a HADB wipe");
            pass = false;
        }
        return pass;
    }
    
    protected boolean verifySwitch(String who) throws HoneycombTestException {
        Log.INFO("Verifying switch in use is the " + who + " switch..." );
        String out = node1.runCmd(SSH_SWITCH + " cat " + SWITCH_CONF_FILE);
        if (out.contains(getPattern(who))) {
            Log.INFO(getPattern(who));
            Log.INFO("Using " + who + " switch");
            out = node1.runCmd(SSH_SWITCH + " " + VRRP);
            if (out.contains(" M ")) {
                Log.INFO(out);
                return true;    
            }
            Log.ERROR(out);
        }
        Log.ERROR(out);
        Log.ERROR("Not using " + who + " switch");
        return false;
    }
    
    protected boolean apcCommand(int port, String action) throws Throwable {
        Log.INFO("Powering " + action + " Switch " + port);
        boolean ok = runSystemCommand(
            "/opt/abt/bin/abt Power" + action + "PduPort on=apc:apc@" +
            apcIP + ":" + port +" logDir=/mnt/test/ " +
            "2>&1 >> /dev/null 2>&1 >> /dev/null");
        if (ok)
            isPowerPortDown = (action.equals("Off")) ? true : false; 
        return ok;
    }
    
    protected boolean nodeDown() throws HoneycombTestException {
        // get non master node
        String m = node1.runCmd(SSH_MASTER_INTERNAL + " hostname");
        node = "hcb" + (((int)(Math.random() * nodes)) + 1 + NODE_NUM_OFFSET);
        while (node.equals(m)) {
            node = "hcb"+(((int) (Math.random() * nodes))+1+NODE_NUM_OFFSET);
        }
        
        try {
        	Log.INFO("Rebooting "+node+" with nohoneycomb/noreboot flags");
        	node1.runCmd("ssh " + node + " touch " + NOHONEYCOMB);
            node1.runCmd("ssh " + node + " touch " + NOREBOOT);
            isNodeDown = true;
            node1.runCmd("ssh " + node + " reboot");
            pause(SLEEP_NODE_DOWN);
            return true;
        } catch (HoneycombTestException e) {
            Log.INFO("Expected exception handled");
            pause(SLEEP_NODE_DOWN);
            return true; //DEBUG
        }
        //return false;
    }
    
    protected boolean nodeUp() throws HoneycombTestException {  
        try {
            node1.runCmd("ssh " + node + " rm " + NOHONEYCOMB);
            node1.runCmd("ssh " + node + " rm " + NOREBOOT);
            Log.INFO("Rebooting "+node+" without nohoneycomb/noreboot flags");
            isNodeDown = false;
            node1.runCmd("ssh " + node + " reboot");
            return true;
        } catch (HoneycombTestException e) {
            Log.INFO("Expected exception handled");
            return true; //DEBUG
        }
        //return false;
    }
        
    protected boolean waitForClusterToStart() throws HoneycombTestException {
        // Wait for cli to be accessible and sysstat returns Online

        Log.INFO("Waiting for cluster to come online and HAFaultTolerant...");
        boolean ready = false;
        int i = MAX_ONLINE_ITERATIONS;
        while (i > 0 && !ready) {
            try {
                i--;
                ArrayList lines = readSysstat();
                if (lines.toString().contains(nodesOnlineString)) {
                    if (lines.toString().contains(QUERY_STATUS_STRING)) {
                        ready = true;
                    }   
                }    
                if (!ready)
                    pause(SLEEP_WAKEUP_TIMEOUT);
            } catch (Throwable e) {
                pause(SLEEP_WAKEUP_TIMEOUT);
            }
        }
        if (i == 0) {
            Log.WARN("Cluster is not Online and HAFaultTolerant");
        }
        return ready;
    }
    
    protected void recover() throws HoneycombTestException{
        if (isZrePortDown) {
            Log.INFO("[RECOVERY] Turning on zre port...");
            portThread = new Thread(new PortUpDown("up"));
            portThread.start();
            pause(SLEEP_RECOVER);
        }
        
        if (isNodeDown) {
            Log.INFO("[RECOVERY] Bringing node back...");
            nodeUp();
            pause(SLEEP_RECOVER);
        }
        
        if (isPowerPortDown) {
            Log.INFO("[RECOVERY] Powering on switch 1...");
            boolean ok;
            try {
                ok = apcCommand(1, "On");
                if (ok) {
                    Log.INFO("[RECOVERY] Switch 1 is on");
                    pause(SLEEP_RECOVER);
                }
                else
                    Log.ERROR("[RECOVERY] Switch 1 could not be turned on, " +
                              "check manually");
            } catch (Throwable e) {
                Log.stackTrace(e);
                Log.ERROR("[RECOVERY] Switch 1 could not be turned on, " +
                  "check manually");
            }
        }
    }
    ///////////////////////////////////////////////////////////////////////////
    //////////////////////////////// UTILITIES ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    protected void rollLogs() throws HoneycombTestException {
        for (int i = 0; i < nodes; i++) {
            int node = i + NODE_NUM_OFFSET;
            CheatNode.runCmdOnDefaultCheat("ssh hcb" + node + " \"logadm " +
                    LOG_LOCATION + " -s 10b\"");
        }
    }
    
    protected String getPattern(String key) {
        String ret = null;
        if (key.equalsIgnoreCase("master"))
            ret = SWITCH_MASTER_PAT;
        if (key.equalsIgnoreCase("backup"))
            ret = SWITCH_BACKUP_PAT;    
        return ret;
    }
    protected boolean matchPattern(String pat, String str, String ok, String no) {
        Pattern pattern = Pattern.compile(pat, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(str);
        while (matcher.find()) {
            Log.INFO(ok);
            return true;
        }
        Log.ERROR(no);
        return false;
    }
    
    protected void initAll() throws Throwable {
        Log.INFO("initAll() called");
        initAdmincli();
        getMasterCell();
       
    }
    protected void initAdmincli() {
        cli = new CLI(cluster + "-admin");
    }
        
    protected void getMasterCell() throws Throwable {
        stdout = cli.runCommand("hiveadm");
        String regex = "Cell (\\d+): adminVIP = " + adminVIP;
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        String line;
        while ((line = stdout.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                masterCell = new Integer(matcher.group(1)).intValue();
                Log.INFO("Master Cell: " + masterCell);
                return;
            }
        } 
    }
    
    protected String getHadbLogDate() throws HoneycombTestException {
        String returned = null;
        String out = node1.runCmd(CAT_MGT_FILE);
        Pattern pattern = Pattern.compile(HADB_PAT, Pattern.MULTILINE);
        int num = -1;
        Matcher matcher = pattern.matcher(out);
        if (matcher.find()) {
            num = Integer.parseInt(matcher.group(1));
        }
        if (num == -1) {
            Log.ERROR("Could not determine hadb logfile date...");
        } else {
            returned = node1.runCmd(LS_HADB_LOG_HEAD1 + num + LS_HADB_LOG_HEAD2);
        }
        return returned;
    }
    
    protected void startLoad() {
        //Edit ENV File
        try {
            runSystemCommand("sed s/dev[0-9X][0-9X][0-9X]/" + cluster + "/ " + 
                ENV_LOC + "ENV" + " | sed s/NUMTHREADS=1/NUMTHREADS="+threads
                + "/ | sed s/STARTDELETES=0/STARTDELETES=" + delete +
                "/ > /tmp/ENV." + cluster);
            
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.ERROR("Could not setup ENV file");
            return;
        }
        
        
        //SCP into each client
        for (int i = 0; i < clients.length; i++) {
            try {
                // Delete the old stress logs
                runSystemCommand("ssh -o StrictHostKeyChecking=no root@" +
                        clients[i] + " rm -f " + STRESS_LOG_LOCATION + "*");
            } catch (Throwable e1) {
                Log.WARN("Could not erase old logs on " + clients[i]);
                Log.WARN("May produce incorrect analysis at the end of the test");
            }
            try {
                runSystemCommand("scp -o StrictHostKeyChecking=no /tmp/ENV." +
                        cluster + " root@" + clients[i] + ":" + ENV_LOC + 
                        "ENV" + " 2>&1 >> /dev/null");
                try {
                    //Start master stress on each
                    runSystemCommand("ssh -o StrictHostKeyChecking=no root@" +
                            clients[i] + " " + ENV_LOC + START_LOAD +
                            " 2>&1 >> /dev/null");
                    Log.INFO("Load started on " + clients[i]);
                } catch (Throwable e) {
                    Log.stackTrace(e);
                    Log.WARN(
                        "Could not start-master-stress on "+clients[i]);
                    Log.WARN("Not running load from " + clients[i]);
                }
            } catch (Throwable e) {
                Log.stackTrace(e);
                Log.WARN("Could not setup ENV file on " + clients[i]);
                Log.WARN("Not running load from " + clients[i]);
            }       
        }
    }
    
    protected void stopLoad() {
        //Edit the stop script
        try {
            runSystemCommand("sed s/java/\"-f " + dataVIP + "\"/ " + 
                    ENV_LOC + STOP_LOAD + " > /tmp/" + STOP_LOAD);
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.ERROR("Could not edit kill-stress-test file...");
            Log.ERROR("  File will run as is, killing all java,");
            Log.ERROR("  including the rmi server. Future tests will fail");
        }
        
        //SCP into each client
        for (int i = 0; i < clients.length; i++) {
            try {
                runSystemCommand(
                    "scp -o StrictHostKeyChecking=no /tmp/" + STOP_LOAD +
                    " root@" + clients[i] + ":" + ENV_LOC + STOP_LOAD +
                    " 2>&1 >> /dev/null");
            } catch (Throwable e) {
                Log.stackTrace(e);
                Log.WARN("Could not send " + STOP_LOAD + 
                        " file on " + clients[i]);
                Log.ERROR("Killing all java on " + clients[i]);
            }
            
            try {
                //Stop master stress on each
                runSystemCommand("ssh -o StrictHostKeyChecking=no root@"
                        + clients[i] + " " + ENV_LOC + STOP_LOAD +
                        " 2>&1 >> /dev/null");
                Log.INFO("Load stopped on " + clients[i]);
            } catch (Throwable e) {
                Log.stackTrace(e);
                Log.ERROR(
                    "Could not kill-stress-test on "+clients[i]);
                Log.ERROR("Still running load from " + clients[i]);
            }
        }
    }
    
    protected void analyzeLoad() {
        String cls = join(clients, " ");
        try {
            runSystemCommand("cp -f /tmp/ENV." + cluster + " " + 
                    ENV_LOC + "ENV" + " 2>&1 >> /dev/null");
            runSystemCommand(
                ENV_LOC + ANALYZE + " -m "+cls+" > /tmp/"+ANALYZE+".out");
            FileReader input = new FileReader("/tmp/"+ANALYZE+".out");
            BufferedReader bufRead = new BufferedReader(input);
            Matcher matcher = null;
            long okCount = 0;
            long errCount = 0;
            Pattern okPattern =
                Pattern.compile(ANALYZE_PERF_OK, Pattern.MULTILINE);
            Pattern errPattern =
                Pattern.compile(ANALYZE_PERF_ERR, Pattern.MULTILINE);
            while ((line = bufRead.readLine()) != null) {
                Log.INFO("---> " + line);
                matcher = okPattern.matcher(line);
                if (matcher.find()) {
                    okCount += Integer.parseInt(matcher.group(1));
                }
                matcher = errPattern.matcher(line);
                if (matcher.find()) {
                    errCount += Integer.parseInt(matcher.group(1));
                }
            }
            
            long total = okCount + errCount;
            Log.INFO("Total ops: " + total);
            Log.INFO(" - OK: " + okCount);
            Log.INFO(" - ERR: " + errCount);
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.ERROR("Unable to analyze peformance data");
        }
    }
        
    protected boolean setupAPCScript() {
        Log.INFO("Setting up the APC automation script...");
        try {
            runSystemCommand(
                    "/opt/test/bin/apc/setup_apc.sh 2>&1 >> /dev/null");            
            return true;
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.ERROR("Could not setup APC script");
        }
        return false;
    }
    
    protected ArrayList readSysstat() throws Throwable {
        return readSysstat (false);
    }
    
    
    protected ArrayList readSysstat(boolean extended) throws Throwable {
        ArrayList result = new ArrayList();
        String line = null;
        String cmd = null;
        if (extended)
            cmd = "sysstat -r";
        else 
            cmd = "sysstat";
        stdout = cli.runCommand(cmd + " -c " + masterCell);
        while ((line = stdout.readLine()) != null)
            result.add(line);
        return result;
    }

    protected ArrayList readLogSysstatV() throws Throwable {
        ArrayList result = new ArrayList();
        String line = null;
        String cmd = "sysstat -v";
        stdout = cli.runCommand(cmd + " -c " + masterCell);
        while ((line = stdout.readLine()) != null)
            result.add(line);
            Log.INFO(line);
        return result;
    }
    
    protected boolean runSystemCommand(String cmd) throws Throwable {
        Log.INFO("Running: " + cmd);
        String s = null;
        String[] command = {"sh", "-c", cmd};
        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();
        BufferedReader stdError = new BufferedReader(new 
                InputStreamReader(p.getErrorStream()));
        while ((s = stdError.readLine()) != null)
            Log.ERROR(s);
        stdout =  new BufferedReader(new
                InputStreamReader(p.getInputStream()));
        if (p.exitValue() != 0) {
            Log.ERROR("Command exited with value: " + p.exitValue());
            return false;
        }
        return true;
    }
    
    private static final Object [][] TIME_METRICS = new Object[][] {
        {new Double(1), "millisecond(s)"},
        {new Double(1000), "second(s)"},
        {new Double(60), "minute(s)"},
        {new Double(60), "hour(s)"}
    };
    
    public static String formatTime(long milli) {
        DecimalFormat df = new DecimalFormat( "##.##" );
        int metric = 0;
        while (milli >= 60 && metric < 3) {
            metric++;
            Double divisor = (Double) TIME_METRICS[metric][0];
            milli /= divisor.longValue();
        }
        return df.format(milli) + " " + TIME_METRICS[metric][1];
    }
    
    protected String join(String[] who, String delimiter) {
        String joined = "";
        for (int i = 0; i < (who.length - 1); i++) {
            joined += who[i];
            joined += delimiter;
        }
        // Last one
        joined += who[who.length - 1];
        return joined;
    }
    
    protected void pause(long milli) {
        Log.INFO("Sleeping for " + formatTime(milli));
        try {
            Thread.sleep(milli);
        } catch (InterruptedException e) { /* Ignore */ }
    }
        
    private class PortUpDown implements Runnable {
        private boolean up = false;
        
        public PortUpDown(String upDown) {
            super();
            if (upDown.equalsIgnoreCase("up"))
                up = true;
            Log.INFO("PortUpDown initialized...");
        }
        public void run() {
            if (up) {
                try {
                    Log.INFO("Enabling port zre22...");
                    node1.runCmd(
                        SSH_SWITCH + " " + OTHER_SWITCH + " " + ZLC_UP);
                    isZrePortDown = false;
                } catch (HoneycombTestException e) {
                    Log.stackTrace(e);
                    Log.ERROR("Unable to power up zre port");                   
                }
            }
            else {
                try {
                    Log.INFO("Disabling port zre22...");
                    isZrePortDown = true;
                    node1.runCmd(SSH_SWITCH + " " +  ZLC_DOWN);
                } catch (HoneycombTestException e) {
                    Log.INFO("Expected Exception Caught");
                }
            }
        }
    }
}


