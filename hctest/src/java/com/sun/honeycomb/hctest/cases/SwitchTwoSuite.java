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

public class SwitchTwoSuite extends HoneycombRemoteSuite{
    private Hashtable hash = new Hashtable();
    private String NOHONEYCOMB = "/config/nohoneycomb";
    private String NOREBOOT = "/config/noreboot";
	private String SSH = "ssh -o StrictHostKeyChecking=no";
	private String SSH_ROOT = SSH + " root@";
    private String SSH_SWITCH = SSH + " -p 2222 -l nopasswd 10.123.45.1";
    private String SWITCH_CONF_FILE = "/etc/honeycomb/switch.conf";
    private String ENV_LOC = "/opt/test/bin/load/emi-load/";
    private String STRESS_LOG_LOCATION = "/mnt/test/emi-stresslogs/";
    private String START_LOAD = "start-master-stress.sh";
    private String STOP_LOAD = "kill-stress-test.sh";
    private String ANALYZE = "analyze-perf.sh";
    private String VRRP = "vrrpconfig -a";
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
    private long SLEEP_RECOVER = 300000; // milliseconds
    private long STORE_DURATION = 28800000; // milliseconds 8 hours
    private long SLEEP_NODE_REBOOT = 20000; //milliseconds
    private long SLEEP_LOAD_START = 300000; //milliseconds
    private long SLEEP_LOAD_STOP = 300000; //milliseconds
    private long BACKUP_DURATION = 25200000; // milliseconds 7 hours
    private long MASTER_DURATION = 3600000; // milliseconds 1 hour
    private long SLEEP_REBOOT_CLUSTER = 900000; //milliseconds
    private int MAX_ONLINE_ITERATIONS = 45; // iterations
    private int MAX_FLAG_ITERATIONS = 5; // iterations
    private int DISKS_PER_NODE = 4;
    private int NODE_NUM_OFFSET = 101;
    private int THREADS = 1;
    private int NODE_PORT_OFFSET = 2001;
    
    
    protected String originalHadbDate = null;
    protected ClusterNode node1 = null;
    protected CLI cli = null;
    protected BufferedReader stdout = null;
    protected long storeDuration = STORE_DURATION;
    protected long longevityDuration = STORE_DURATION;
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
    protected String cluster = null;
    protected String queryStatusString = QUERY_STATUS_STRING;
    protected boolean storeStatus = false;
    protected boolean isPowerPortDown = false;
    protected boolean isSetup = false;
    protected boolean isFlags = false;
    protected boolean isLoad = false;
    protected TestCase self;
    public SwitchTwoSuite() {
        super();
    }

    /* 
     * Tests must be run on a cluster that is already online.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        self = createTestCase("Switch Two Suite", "Runs multiple tests on second switch with and without load");
        self.addTag("SwitchTwoSuite");
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
        String longevity = TestRunner.getProperty("longevity"); //hr
        String intervalTime = TestRunner.getProperty("interval"); //minutes
        String threadsPerClient = TestRunner.getProperty("threads");
        String del = TestRunner.getProperty("delete");
        
        if (loadTime != null) {
            storeDuration = 
                (long) (Double.parseDouble(loadTime) * 60 * 60 * 1000);
        }
        if (longevity != null) {
            longevityDuration = 
                (long) (Double.parseDouble(longevity) * 60 * 60 * 1000);
        }
        if (intervalTime != null) { 
            interval = 
                (long) (Double.parseDouble(intervalTime) * 60 * 1000);
        }
        if (threadsPerClient != null) { 
            threads = Integer.parseInt(threadsPerClient);
        }
        if (del != null) {  
            delete = 1;
        }
        
        Log.INFO("Total store time (per load test): "
                + formatTime(storeDuration));
        Log.INFO("Cluster checkup interval time: "
                + formatTime(interval));
        adminVIP = testBed.adminVIPaddr;
        dataVIP = testBed.dataVIP;
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        
        initAll(); 
        nodes = cli.hwstat(masterCell).getNodes().size();
        nodesOnlineString = nodes + " nodes online, " + 
            (DISKS_PER_NODE * nodes) + " disks online";
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

    // Has Successfully Passed
    public boolean test01_RebootNodesOnSwitchTwo() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        Log.INFO("  Reboots cluster while on switch 2");
        Log.INFO("  Verifies hadb did not wipe or nodes panic");
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        return runRebootTest("nodes", false);
    }
    
    // Makes hadb unavailable / known bug
    // Master node won't restart because it is waiting for sp to go down
    //  but sp is not accessible on sw2
    public boolean test02_RebootAllOnSwitchTwo() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        Log.INFO("  reboot -a while on switch 2");
        Log.INFO("  Verifies hadb did not wipe or nodes panic");
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        return runRebootTest("all", false);
    }
    
    // Has successfully passed
    public boolean test03_RebootNodesWithLoadOnSwitchTwo() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        Log.INFO("  Reboots nodes while on switch 2");
        Log.INFO("  Verifies hadb did not wipe or nodes panic");
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        return runRebootTest("nodes", true);
    }
    
    // Makes hadb unavailable / known bug see test2 above
    public boolean test04_RebootAllWithLoadOnSwitchTwo() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        Log.INFO("  reboot -a while on switch 2");
        Log.INFO("  Verifies hadb did not wipe or nodes panic");
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        return runRebootTest("all", true);
    }
    
    // Has successfully passed
    public boolean test05_MasterFailoverOnSwitchTwo() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        Log.INFO("  Master Failover while on switch 2");
        Log.INFO("  Verifies hadb did not wipe or nodes panic");
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        return runMasterFailoverTest(false);
    }
    
    public boolean test06_MasterFailoverWithLoadOnSwitchTwo() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        Log.INFO("  Master Failover with load while on switch 2");
        Log.INFO("  Verifies hadb did not wipe or nodes panic");
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        return runMasterFailoverTest(true);
    }
    
    public boolean test07_LongevityTestWithLoadOnSwitchTwo() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        Log.INFO("  Run load while on switch 2");
        Log.INFO("  Verifies hadb did not wipe or nodes panic");
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        return runLongevityTest();
    }
    
    public boolean test08_LoadSpreadingOnSwitchTwo() throws Throwable {
        if (excludeCase() || !isSetup) return false;
        Log.INFO("  Runs Load on SW2, verifies load spreading and");
        Log.INFO("  Verifies hadb did not wipe or nodes panic");
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        return runLoadSpreading();
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////// HELPERS /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    protected boolean runRebootTest(String who, boolean runLoad) throws Throwable {
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        
        boolean ok_to_proceed = waitForClusterToStart();
        
        if (ok_to_proceed) {
            storeStatus = true;
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        // verify that we are on switch 1
        if (ok_to_proceed) {
            ok_to_proceed = verifySwitch("master");
        }
        
        // power off switch 1 through apc
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "Off");
        }

        // verify that we are on switch 2
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILOVER);
            ok_to_proceed = verifySwitch("backup");
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = waitForClusterToStart();
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        if (ok_to_proceed && runLoad) {
            startLoad();
            pause(SLEEP_LOAD_START);
        }
        
        // reboot all nodes
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = rebootAndWait(who);
        }
        
        // check cluster
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = checkCluster(nodesOnlineString);
            if (ok_to_proceed && runLoad) {
                Log.INFO("Running load on backup switch for " + 
                        formatTime(storeDuration));
                double endTime =
                    System.currentTimeMillis() + storeDuration;
                while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                    ok_to_proceed = checkCluster(nodesOnlineString);
                    if (ok_to_proceed)
                        sleep(interval);
                }
            }
        }
        
        if (ok_to_proceed && runLoad) {
            stopLoad();
            pause(SLEEP_LOAD_STOP);
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
        
        if (ok_to_proceed) {
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        if (ok_to_proceed && runLoad) {
            analyzeLoad();
        }
        
        if (!ok_to_proceed) {
            checkCluster(nodesOnlineString);
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        return ok_to_proceed;
    }
    
    protected boolean runMasterFailoverTest(boolean runLoad) throws Throwable {
        if (!cluster.equalsIgnoreCase(apcCluster)) {
            return false;
        }
        boolean ok_to_proceed = waitForClusterToStart();
        
        if (ok_to_proceed) {
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        // verify that we are on switch 1
        if (ok_to_proceed) {
            ok_to_proceed = verifySwitch("master");
        }
        
        // power off switch 1 through apc
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "Off");
        }

        // verify that we are on switch 2
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILOVER);
            ok_to_proceed = verifySwitch("backup");
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = waitForClusterToStart();
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        if (ok_to_proceed && runLoad) {
            startLoad();
            pause(SLEEP_LOAD_START);
        }

        // Do a master failover
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            showMasterName();
            ok_to_proceed = masterFailover();
        }
        
        // check cluster for one node down
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = waitForClusterToStart();
            showMasterName();
        }
        
        // check cluster
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = checkCluster(nodesOnlineString);
            if (ok_to_proceed && runLoad) {
                Log.INFO("Running load on backup switch for " + 
                        formatTime(storeDuration));
                double endTime =
                    System.currentTimeMillis() + storeDuration;
                while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                    ok_to_proceed = checkCluster(nodesOnlineString);
                    if (ok_to_proceed)
                        sleep(interval);
                }
            }
        }
            
        if (ok_to_proceed && runLoad) {
            stopLoad();
            pause(SLEEP_LOAD_STOP);
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
        
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = waitForClusterToStart();
        }
        
        // check cluster for all nodes
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        if (ok_to_proceed && runLoad) {
            analyzeLoad();
        }
        
        if (!ok_to_proceed) {
            checkCluster(nodesOnlineString);
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        return ok_to_proceed;
    }
    
    protected boolean runLongevityTest() throws Throwable {
        boolean ok_to_proceed = waitForClusterToStart();
        
        if (ok_to_proceed) {
            storeStatus = true;
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        // Add Flags
        if (ok_to_proceed)
            ok_to_proceed = verifyFlags(true);
        
        // Kill Honeycomb
        if (ok_to_proceed)
            ok_to_proceed = reboot("nodes");
        
        // power off switch 1 through apc
        if (ok_to_proceed) {
            pause(SLEEP_REBOOT_CLUSTER);
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "Off");
        }

        // verify that we are on switch 2
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILOVER);
            ok_to_proceed = verifySwitch("backup");
        }
        
        // Remove Flags
        if (ok_to_proceed)
            ok_to_proceed = verifyFlags(false);
        
        // Start Honeycomb
        if (ok_to_proceed)
            ok_to_proceed = startHoneycomb();
        
        if (ok_to_proceed) {
            pause(SLEEP_REBOOT_CLUSTER);
            ok_to_proceed = waitForClusterToStart();
            checkCluster(nodesOnlineString);
        }
    
        if (ok_to_proceed) {
            startLoad();
            pause(SLEEP_LOAD_START);
        }
        
        // check cluster
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        if (ok_to_proceed) {
            Log.INFO("Running load on backup switch for " + 
                    formatTime(longevityDuration));
            double endTime =
                System.currentTimeMillis() + longevityDuration;
            while (ok_to_proceed && (System.currentTimeMillis() < endTime)) {
                ok_to_proceed = checkCluster(nodesOnlineString);
                if (ok_to_proceed)
                        sleep(interval);   
            }
        }
        
        if (ok_to_proceed) {
            stopLoad();
            pause(SLEEP_LOAD_STOP);
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
        

        if (ok_to_proceed) {
            analyzeLoad();
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        if (!ok_to_proceed) {
            checkCluster(nodesOnlineString);
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        return ok_to_proceed;
    }
    
    protected boolean runLoadSpreading() throws Throwable {
        DistributedLoadSpreading dls = new DistributedLoadSpreading();
        dls.setUp();
        boolean pass = false;
        boolean ok_to_proceed = waitForClusterToStart();
        
        if (ok_to_proceed) {
            storeStatus = true;
            ok_to_proceed = checkCluster(nodesOnlineString);
        }
        
        // Add Flags
        if (ok_to_proceed)
            ok_to_proceed = verifyFlags(true);
        
        // Kill Honeycomb
        if (ok_to_proceed)
            ok_to_proceed = reboot("nodes");
        
        // power off switch 1 through apc
        if (ok_to_proceed) {
            pause(SLEEP_REBOOT_CLUSTER);
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "Off");
        }

        // verify that we are on switch 2
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILOVER);
            ok_to_proceed = verifySwitch("backup");
        }
        
        // Remove Flags
        if (ok_to_proceed)
            ok_to_proceed = verifyFlags(false);
        
        // Start Honeycomb
        if (ok_to_proceed)
            ok_to_proceed = startHoneycomb();

        
        // Wait for startup
        if (ok_to_proceed) {
            pause(SLEEP_REBOOT_CLUSTER);
            ok_to_proceed = waitForClusterToStart();
            checkCluster(nodesOnlineString);
        }
        
        // Start the load spreading test (see DistributedLoadSpreading.java)
        if (ok_to_proceed) {
            pass = dls.test_LoadSpreading();
            Log.INFO("Ok to proceed...");
            ok_to_proceed = apcCommand(1, "On");
        }
   
        // verify that we are on switch 1
        if (ok_to_proceed) {
            Log.INFO("Ok to proceed...");
            pause(SLEEP_SWITCH_FAILBACK);
            ok_to_proceed = verifySwitch("master");
        }
        
        if (ok_to_proceed) {
            ok_to_proceed = waitForClusterToStart();
            checkCluster(nodesOnlineString);
        }
            
        if (!ok_to_proceed) {
            checkCluster(nodesOnlineString);
            Log.ERROR("Not ok to proceed, failing test");
            recover();
        }
        
        return pass && ok_to_proceed;
        
    }
    
    protected boolean checkCluster(String who) throws Throwable {
        boolean pass = true;

        ArrayList sysstat = readSysstat();
        // All nodes still up?
        if (!sysstat.toString().contains(who)) {
            Log.ERROR(sysstat.toString());
            Log.ERROR("Detected a node down");
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
            originalHadbDate = null;
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
    
    protected void showMasterName() {
    	try {
			runSystemCommand(SSH_ROOT + adminVIP + " hostname");
			Log.INFO("Master node: " + stdout.readLine());
		} catch (Throwable e) {
			Log.stackTrace(e);
			Log.ERROR("Could not get master hostname");
		}
    }
    protected boolean masterFailover() throws Throwable {
        boolean pass = false;
        try {
        	Log.INFO("Doing a Master Failover...");
            runSystemCommand(SSH_ROOT + adminVIP + " reboot");
            pass = true; //DEBUG
        } catch (HoneycombTestException e) {
            Log.INFO("Expected exception caught");
            pass = true;
        }
        return pass;
    }
    
    protected boolean startHoneycomb() throws Throwable {
        boolean ok = true;
        for (int n = 0; n < nodes; n++) {
            int port = n + NODE_PORT_OFFSET;
            try {
            	runSystemCommand(SSH + " -p " + port + " root@"+ adminVIP +
        			" \"/opt/honeycomb/etc/init.d/honeycomb start\"");
            } catch (Throwable e) {
                //Expected Exception caught
            }
        }
        return ok;
    }
    
    protected boolean reboot(String who) throws Throwable {
        addNoFsckFlags();
        Log.INFO("Rebooting Cluster...");
        String cmd = "reboot -c " + masterCell + " -F";
        if (who.equalsIgnoreCase("all"))
            cmd += " -A";
        cli.runCommand(cmd);
        return true; //exception will never get here
    }
    protected boolean rebootAndWait(String who) throws Throwable {
        reboot(who);
        pause(SLEEP_REBOOT_CLUSTER);
        return waitForClusterToStart();
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
    
    protected void recover() throws Throwable{      
        if (isLoad) {
            Log.INFO("[RECOVERY] Stopping Load");
            stopLoad();
            pause(SLEEP_LOAD_STOP);
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
        
        if (isFlags) {
            boolean ok = verifyFlags(false);
            if (ok){
                Log.INFO("[RECOVERY] NOHONEYCOMB/NOREBOOT flags removed");
            } else {
                Log.ERROR("[RECOVERY] NOHONEYCOMB/NOREBOOT" +
                        " flags could not be removed");
            }
            
        }
    }
    ///////////////////////////////////////////////////////////////////////////
    //////////////////////////////// UTILITIES ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
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
    
    protected String getPattern(String key) {
        String ret = null;
        if (key.equalsIgnoreCase("master"))
            ret = SWITCH_MASTER_PAT;
        if (key.equalsIgnoreCase("backup"))
            ret = SWITCH_BACKUP_PAT;    
        return ret;
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
                runSystemCommand(SSH_ROOT +
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
                    runSystemCommand(SSH_ROOT +
                            clients[i] + " " + ENV_LOC + START_LOAD +
                            " 2>&1 >> /dev/null");
                    Log.INFO("Load started on " + clients[i]);
                    isLoad = true;
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
        boolean allStopped = true;
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
                runSystemCommand(SSH_ROOT
                        + clients[i] + " " + ENV_LOC + STOP_LOAD +
                        " 2>&1 >> /dev/null");
                Log.INFO("Load stopped on " + clients[i]);
            } catch (Throwable e) {
                Log.stackTrace(e);
                Log.ERROR(
                    "Could not kill-stress-test on "+clients[i]);
                Log.ERROR("Still running load from " + clients[i]);
                allStopped = false;
            }
        }
        
        isLoad = !allStopped;
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
    
    protected void addNoFsckFlags() throws Throwable {
        Log.INFO("Adding no FSCK flags...");
        for (int i = 0; i < nodes; i++) {
            int node = i + NODE_NUM_OFFSET;
            int port = i + NODE_PORT_OFFSET;
            try {
                runSystemCommand(
                    SSH+" -p " + port +" root@" + adminVIP + 
                    " touch /config/clean_unmount__dev_dsk_c0t0d0s4");
                runSystemCommand(
                    SSH+" -p " + port +" root@" + adminVIP +  
                    " touch /config/clean_unmount__dev_dsk_c0t1d0s4");
                runSystemCommand(
                    SSH+" -p " + port +" root@" + adminVIP +  
                    " touch /config/clean_unmount__dev_dsk_c1t0d0s4");
                runSystemCommand(
                    SSH+" -p " + port +" root@" + adminVIP +  
                    " touch /config/clean_unmount__dev_dsk_c1t1d0s4");
                Log.INFO(" - node " + node + ": ok");
            } catch (HoneycombTestException e) {
                Log.stackTrace(e);
                Log.ERROR(" - node " + node + ": not ok");
            }
        }
    }
    
    protected boolean verifyFlags(boolean flagsExpected) throws Throwable {
        int i = MAX_FLAG_ITERATIONS;
        if (flagsExpected) {
            while (!isFlagsPresent() && i > 0) {
                Log.INFO("Adding nohoneycomb/noreboot flags on all nodes...");
                for (int n = 0; n < nodes; n++) {
                    int port = n + NODE_PORT_OFFSET;
                    runSystemCommand(
                       SSH+" -p "+port+" root@"+adminVIP+" touch "+NOHONEYCOMB);
                    runSystemCommand(
                       SSH+" -p "+port+" root@"+adminVIP+" touch "+NOREBOOT);
                }
                i--;
            }
        } else {
            while (isFlagsPresent() && i > 0) {
                Log.INFO("Removing nohoneycomb/noreboot flags on all nodes...");
                for (int n = 0; n < nodes; n++) {
                    int port = n + NODE_PORT_OFFSET;
                    runSystemCommand(
                       SSH+" -p "+port+" root@"+adminVIP+" rm "+NOHONEYCOMB);
                    runSystemCommand(
                       SSH+" -p "+port+" root@"+adminVIP+" rm "+NOREBOOT);
                }
                i--;
            }
        }
        
        if (i == 0) {
            String a = (flagsExpected) ? "add" : "remove";
            Log.ERROR("Could not " + a + "nohoneycomb/noreboot flags");
            return false;
        }
        return true;
    }
    
    protected boolean isFlagsPresent() throws Throwable {
        Log.INFO("Testing to see if nohoneycomb/noreboot flags are present...");
        boolean ok;
        int numPresent = 0;
        for (int n = 0; n < nodes; n++) {
            int port = n + NODE_PORT_OFFSET;
            ok = runSystemCommand(
               SSH+" -p "+port+" root@"+adminVIP+" ls "+NOHONEYCOMB);
            if (ok)
                numPresent += 1;
            ok = runSystemCommand(
                   SSH+" -p "+port+" root@"+adminVIP+" ls "+NOREBOOT);
            if (ok)
                numPresent += 1;
        }
        isFlags = (numPresent > 0) ? true : false;
        return (numPresent == (nodes * 2));
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
    
    protected boolean runSystemCommand(String cmd) throws Throwable {
        Log.INFO("Running: " + cmd);
        String s = null;
        String[] command = {"sh", "-c", cmd};
        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();
        BufferedReader stdError = new BufferedReader(new 
                InputStreamReader(p.getErrorStream()));
        while ((s = stdError.readLine()) != null)
            Log.WARN(s);
        stdout =  new BufferedReader(new
                InputStreamReader(p.getInputStream()));
        if (p.exitValue() != 0) {
            Log.WARN("Command exited with value: " + p.exitValue());
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
}
