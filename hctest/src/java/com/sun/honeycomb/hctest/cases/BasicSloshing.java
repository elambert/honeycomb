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

import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.hctest.cli.DataDoctorState;
import com.sun.honeycomb.hctest.util.CheatNode;
import com.sun.honeycomb.hctest.util.ClusterMembership;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.SnapshotTool;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import java.util.Iterator;


public class BasicSloshing extends HoneycombLocalSuite {

    protected static final String SUNWHCWBCLUSTER_PACKAGE = "SUNWhcwbcluster";
    
    private long SLOSH_CHECK_MINUTES = 1; // Always in minutes
    private long CLUSTER_WAKEUP_TIMEOUT = 60000; // milliseconds
   
    private String CELL_EXP_CMD = "celladm expand -F";
    private String CELLADM_CMD = "celladm -F";
    
    protected String cluster = null;
    private String snapshot_name = null;
    protected boolean delete_data = false;
    protected boolean create_data = false;
    protected boolean setup_cluster = false;
    private boolean already_clean = false;
    
    protected CLI cli = null;
    protected SnapshotTool snapshot_tool = null;
    
    private String safe_snapshot = "sloshing_safe_snapshot_" + System.currentTimeMillis();
    private String createdata_snapshot = "sloshing_createdata_snapshot_" + System.currentTimeMillis();

    protected TestCase self;    // to file pass/fail test results
    protected ClusterMembership cm; // to track CMM state and manipulate nodes
 
    // wipes master boot record from all 4 data slices 
    private String wipe_mbr = 
        "dd if=/dev/zero of=/dev/rdsk/c0t0d0p0 bs=512 count=2 ; " +
        "dd if=/dev/zero of=/dev/rdsk/c0t1d0p0 bs=512 count=2 ; " +
        "dd if=/dev/zero of=/dev/rdsk/c1t0d0p0 bs=512 count=2 ; " +
        "dd if=/dev/zero of=/dev/rdsk/c1t1d0p0 bs=512 count=2 ; ";

    private String adminVip; 
    private String dataVip;
    private String spIp;
    private String gateway;
    private String subnet;
    private String ntpServers;
    private String smtpServer;
    private String smtpPort;
    private String logServer;
    private String authClients;
    private String dns;
    private String primaryDns = "";
    private String secondaryDns = "";
    private String domainName = "";
    private String dnsSearch = "";
    private String alertEmail = "";
 
    public BasicSloshing() {
        super();
    }

    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("Sloshing Testing\n");
        sb.append("\t" + HoneycombTestConstants.PROPERTY_SNAPSHOT + 
                  " - if a snapshot is specified then the testcase will use this dataset to do all sloshing testing.");
        return sb.toString();
    }

    public void setUp() throws Throwable {
        super.setUp();
        
        self = createTestCase("Sloshing","Simple sloshing testcase.");
        self.addTag("sloshing");
        
        if (self.excludeCase()) // should I run?
            return;

        init();
    }
    
    public void init() throws HoneycombTestException {
        snapshot_name = getProperty(HoneycombTestConstants.PROPERTY_SNAPSHOT);
        
        safe_snapshot = "sloshing_safe_snapshot_" + System.currentTimeMillis();
        createdata_snapshot = "sloshing_createdata_snapshot_" + System.currentTimeMillis();

        delete_data = (getProperty(HoneycombTestConstants.PROPERTY_DELETEDATA) != null ? true : false);
        create_data = (getProperty(HoneycombTestConstants.PROPERTY_CREATEDATA) != null ? true : false);
        setup_cluster = (getProperty(HoneycombTestConstants.PROPERTY_SETUPCLUSTER) != null ? true : false);
                
        if (snapshot_name != null && create_data) 
            throw new HoneycombTestException("User error, you can not specify createdata and snapshot_name in the same execution.");
        
        cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        cli = new CLI(cluster + "-admin");
        cm = new ClusterMembership(-1,cluster + "-admin");
        snapshot_tool = SnapshotTool.getInstance();
    }
    
    protected boolean finished = false;
    public void tearDown() throws Throwable {
        super.tearDown();

        if (self.excludeCase()) // should I run?
            return;
        
        if (snapshot_name != null && !delete_data && finished == false) {
            Log.INFO("Testcase has ended with a failure... the current state " +
                     "the cluster is bad, your previous dataset was " +
                     "saved as the snapshot: " + safe_snapshot);
        }
        
        if (snapshot_name != null && !delete_data) { 
            Log.INFO("data that was on the old 8 node cluster can be found on the snapshot: " + safe_snapshot);
        }
        
        if (finished && (setup_cluster || create_data)) {
            // If we successfully finished and data/snapshot was created for
            // this test, then lets clean up
            snapshot_tool.deleteSnapshot(createdata_snapshot);       
        }
        DataDoctorState.getInstance().setDefault(CLIState.LOST_FRAGS);
    }
    
    protected void verifyPreReqs() throws HoneycombTestException {
        Log.INFO("Making sure all 16 nodes are up and running...");
        for (int i = 101; i <= 116; i++) {
            try {
                int result = runOnCheat("ping hcb" + i + " 1","",true);
                if (result != 0) {
                    throw new HoneycombTestException("Node " + i + " is not powered on, please power on before proceeding.");
                } else {
                    
                }
            } catch (HoneycombTestException e) {/* ignore */}
        }
        
        Log.INFO("Verifying all 16 nodes have same HC version...");
        ClusterNode master = cm.getMaster();
        String version_on_master = master.runCmd("cat /opt/honeycomb/version");
        for (int i = 1; i <= 16; i++) {
            ClusterNode node = cm.getNode(i);
            String version = node.runCmd("cat /opt/honeycomb/version");
            
            if (!version_on_master.equals(version))
                throw new HoneycombTestException("HC version on node " + i + " differs from master verison");
        }

        verifyPackage(SUNWHCWBCLUSTER_PACKAGE);
        
        if (setup_cluster) {
            setupSloshingCluster();
        }
    }
   
    protected void verifyPackage(String packageName) throws HoneycombTestException {
        Log.INFO("Verifying cluster nodes have SUNWhcwbcluster package.");
        for (int i = 1; i <= 16; i++) {
            ClusterNode node = cm.getNode(i);
            if (node != null) 
                if (node.packageInstalled(packageName)==false)
                    throw new HoneycombTestException (
                        packageName + " is not installed on " + node.toString());
        }
    }
    
    protected void initCluster() throws HoneycombTestException {
        setupInstallServerOnCheat();
        stopExpansionNodes();
        setupExistingCluster();
        startExpansionNodes();
        waitForExpansionReady();
    }
 
    public void doLoad() throws HoneycombTestException {
        /*
         * Default Stores are done with current client and using 10 processes
         * with 100% Stores with max data size of 1M and for 5m
         * 
         */
        TestRunner.setProperty("factory","ContinuousMixFactory");
        
        if (TestRunner.getProperty("clients") == null) {
            String hostname = com.sun.honeycomb.test.util.Util.localHostName();
            TestRunner.setProperty("clients",hostname);
        }
        
        if (TestRunner.getProperty("processes") == null)
            TestRunner.setProperty("processes","5");
        
        if (TestRunner.getProperty("operations") == null)
            TestRunner.setProperty("operations","100%StoreOpGenerator");
        
        if (TestRunner.getProperty("maxsize") == null)
            TestRunner.setProperty("maxsize","1M");
        
        TestRunner.setProperty("nodes", new Integer(cm.getNumNodes()).toString());
        
        if (TestRunner.getProperty("time") == null)
            TestRunner.setProperty("time","1m");
        
        // TestBed must be re-initiazlied in order to make sure that the new
        // settings above take effect.
        TestBed.getInstance().init();
        
        Suite suite;
        try {
            suite = Suite.newInstance("com.sun.honeycomb.hctest.cases.storepatterns.ContinuousStore");
        } catch (Throwable e) {
            throw new HoneycombTestException(e);
        }
        
        try {
            suite.setUp();
            suite.run();
            suite.tearDown();        
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }
    }
    
    public void testSloshing() throws HoneycombTestException {
        
        if (self.excludeCase()) // should I run?
            return;
        
        cm.setQuorum(true);
        cm.initClusterState();

        verifyPreReqs();
        initCluster();        
        startSloshing();        
        waitForSloshingCompletion();   
        verifyPackage(SUNWHCWBCLUSTER_PACKAGE);
        verifyCluster();
                
        self.postResult(true, "Sloshing completed successfully.");
        finished = true;
    }
   
    protected void parseConfig() throws Throwable {
            Pattern p1 = Pattern.compile("Admin IP Address\\s*=\\s*(.*)");
            Pattern p2 = Pattern.compile("Data IP Address\\s*=\\s*(.*)");
            Pattern p3 = Pattern.compile("Service Node IP Address\\s*=(.*)");
            Pattern p4 = Pattern.compile("Subnet\\s*=\\s*(.*)");
            Pattern p5 = Pattern.compile("Gateway\\s*=\\s*(.*)");
            String line;
            String cellcfgStr = "";
            BufferedReader br = cli.runCommand("cellcfg");
            while((line = (String)br.readLine()) != null) {
                Matcher m1 = p1.matcher(line);
                if(m1.find()) {
                    adminVip = m1.group(1); 
                    continue;
                }     
                Matcher m2 = p2.matcher(line);    
                if(m2.find()) {
                    dataVip = m2.group(1); 
                    continue;
                }
                Matcher m3 = p3.matcher(line);
                if(m3.find()) {
                    spIp = m3.group(1); 
                    continue;
                }    
                Matcher m4 = p4.matcher(line);
                if(m4.find()) {
                    subnet = m4.group(1);  
                    continue;
                }    
                Matcher m5 = p5.matcher(line);   
                if(m5.find()) {
                    gateway = m5.group(1); 
                    continue;
                } 
            }

	    Pattern p6 = Pattern.compile("NTP Server\\s*=\\s*(.*)");
            Pattern p7 = Pattern.compile("SMTP Server\\s*=\\s*(.*)");
            Pattern p8 = Pattern.compile("SMTP Port\\s*=\\s*(.*)");
            Pattern p9 = Pattern.compile("Authorized Clients\\s*=\\s*(.*)");
            Pattern p10 = Pattern.compile("External Logger\\s*=\\s*(.*)");
            Pattern p11 = Pattern.compile("DNS\\s*=\\s*(.*)");
            Pattern p12 = Pattern.compile("Domain Name\\s*=\\s*(.*)");
            Pattern p13 = Pattern.compile("DNS Search\\s*=\\s*(.*)");
            Pattern p14 = Pattern.compile("Primary DNS Server\\s*=\\s*(.*)");
            Pattern p15 = Pattern.compile("Secondary DNS Server\\s*=\\s*(.*)");

            String hivecfgStr = "";
            br = cli.runCommand("hivecfg");
            while((line = (String)br.readLine()) != null) {
                Matcher m6 = p6.matcher(line);
                if(m6.find()) {
                    ntpServers = m6.group(1); 
                    continue;
                }     
                Matcher m7 = p7.matcher(line);    
                if(m7.find()) {
                    smtpServer = m7.group(1); 
                    continue;
                }
                Matcher m8 = p8.matcher(line);
                if(m8.find()) {
                    smtpPort = m8.group(1); 
                    continue;
                }    
                Matcher m9 = p9.matcher(line);
                if(m9.find()) {
                    authClients = m9.group(1); 
                    continue;
                }    
                Matcher m10 = p10.matcher(line);   
                if(m10.find()) {
                    logServer = m10.group(1); 
                    continue;
                } 
                Matcher m11 = p11.matcher(line);   
                if(m11.find()) {
                    dns = m11.group(1);
                    continue;
                } 
                Matcher m12 = p12.matcher(line);   
                if(m12.find()) {
                    domainName = m12.group(1);
                    continue;
                } 
                Matcher m13 = p13.matcher(line);   
                if(m13.find()) {
                    dnsSearch = m13.group(1);
                    continue;
                } 
                Matcher m14 = p14.matcher(line);   
                if(m14.find()) {
                    primaryDns = m14.group(1);
                    continue;
                } 
                Matcher m15 = p15.matcher(line);   
                if(m15.find()) {
                    secondaryDns = m15.group(1);
                    continue;
                } 
            }

            String alertcfgStr = "";
            br = cli.runCommand("alertcfg");
            Pattern p16 = Pattern.compile("To:\\s*(.*)");
            while((line = (String)br.readLine()) != null) {
                Matcher m16 = p16.matcher(line);
                if(m16.find()) {
                    alertEmail = m16.group(1); 
                    break; 
                }   
            } 
    }
 
    protected void setupInstallServerOnCheat() {
        try { 
            parseConfig();
            Log.INFO("Create cluster.conf file for setup_install_server");
            runOnCheat("rm /tmp/cluster.conf","", true);
            runOnCheat("touch /tmp/cluster.conf","", true);
            runOnCheat("echo CLUSTERNAME=" + cluster + " >> /tmp/cluster.conf","", true);

            /*
             *  On purpose hardcoded as 8, as cluster is still an 8 node cluster. 
             */   
            runOnCheat("echo CLUSTERSIZE=8 >> /tmp/cluster.conf","", true);
            runOnCheat("echo HARDWAREPROFILE=aquarius >> /tmp/cluster.conf","", true);
            runOnCheat("echo DISKSPERNODE=4  >> /tmp/cluster.conf","", true);
            runOnCheat("echo DATAVIP=" + dataVip + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo ADMINVIP=" + adminVip + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo SPIP=" + spIp + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo SWITCHTYPE=znyx >> /tmp/cluster.conf", "", true);
            runOnCheat("echo NODEVIPS=\\'\\' >> /tmp/cluster.conf", "", true);
            runOnCheat("echo LOGSERVER=" + logServer + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo NTPSERVER=\\'" + ntpServers + "\\'" + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo SMTPSERVER=" + smtpServer + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo AUTHCLIENTS=\\'" + authClients + "\\'" + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo ALERTEMAIL=" + alertEmail + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo GATEWAY=" + gateway + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo SUBNET=" + subnet + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo DNS=" + dns + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo DOMAINNAME=" + domainName + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo DNSSEARCH=" + dnsSearch + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo PRIMARYDNSSERVER=" + primaryDns + " >> /tmp/cluster.conf", "", true);
            runOnCheat("echo SECONDARYDNSSERVER=" + secondaryDns + " >> /tmp/cluster.conf", "", true);
            Log.INFO("Setup DHCP server on cheat for node jumpstart");
            runOnCheat("/opt/honeycomb/bin/setup_install_server -f /tmp/cluster.conf -n 8", "", true);
        } catch(Throwable e) { /* ignore exception */ } 
    }

    protected void stopExpansionNodes(){
        Log.INFO("Making sure nodes 8 through 16 are cleaned and then powered off");
        for (int i = 109; i <= 116; i++) {
            try {
                int result = runOnCheat("ping hcb" + i + " 1","",true);
                if (result == 0) {
                    Log.INFO("Wipe MBR for node hcb" + i);
                    runOnCheat("ssh hcb" + i + " '" + wipe_mbr + " '","",true);
                    Log.INFO("Power Off node hcb" + i);
                    runOnCheat("ssh hcb" + i + " 'rm -fr /config/nohoneycomb /config/noreboot ; poweroff'","",true);
                } else {
                    Log.INFO("Unable to ping hcb" + i);
                }
            } catch (HoneycombTestException e) {/* ignore */}
        }   
        Log.INFO("Letting these nodes settle for a minute");
        pause(60000);
    }

    protected void stopCluster(int numNodes) throws HoneycombTestException {
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = cm.getNode(i);
            node.setupDevMode();
        }
      
        try {
            cli.runCommand("reboot --force");
        } catch (Throwable e) {
            throw new HoneycombTestException(e);
        }        
    }
    
    protected void waitForHCToDie(int numNodes) throws HoneycombTestException {
        Log.INFO("Wait for java processes to die on cluster.");
        boolean done = false;
        while (!done) {
            done = true;
            for (int i = 1; i <= numNodes; i++) {
                ClusterNode node = cm.getNode(i);
               
                // HACK: just because of some stupid jvms left behind CLI_PROCESS
                //ExitStatus status = node.runCmdPlus("ps -ef | grep java | grep -v grep");
                if (node.isAlive()) {
	                ExitStatus status = node.runCmdPlus("ps -ef | grep 'NODE-SERVERS' | grep -v grep");
	                if (status.getReturnCode() == 0) {
	                    done = false;
	                    Log.INFO("Node " + i + " still running java processes.");
	                    break;
	                } 
                }
            }
            pause(10000);
        }
      
       
        // HACK: because of the above reason 
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = cm.getNode(i);
            if (node.isAlive()) 
                node.runCmdPlus("pkill java");
        } 
    }
    
    protected void removeDevMode(int numNodes) throws HoneycombTestException {
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = cm.getNode(i);
            node.removeDevMode();
        }
    }
    
    protected void startCluster(int numNodes) throws HoneycombTestException {
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = cm.getNode(i);
            if (node.isAlive())
                node.runCmd("/opt/honeycomb/etc/init.d/honeycomb start");
        }
    }
    
    protected void waitForClusterToStart(int numNodes) throws HoneycombTestException {
        // Wait for CLI to be acessible and sysstat returns Online
        boolean ready = false;
        boolean isClusterOnline = false;
        boolean isHadbFaultTolerant = false;
        while (!ready) {
            try {
                pause(CLUSTER_WAKEUP_TIMEOUT);
                ArrayList lines = readSysstat();
                for (int i=0; i<lines.size(); i++) {
                    if ((((String)lines.get(i)).contains("Online"))) {
                        isClusterOnline = true;
                    }
                    if ((((String)lines.get(i)).contains("HAFaultTolerant"))) {
                        isHadbFaultTolerant = true;
                    }
                }
                if (isClusterOnline && isHadbFaultTolerant) {
                    ready = true;
                }
            } catch (Throwable e) {
                // ignore for now
            }
        }
    }
    
    protected void handleCurrentData() throws HoneycombTestException {
        if (delete_data) {
            if (!already_clean)
                deleteData();
        } else {
            Log.INFO("Moving current data to a safe snapshot: " + safe_snapshot);
            snapshot_tool.saveSnapshot(safe_snapshot, SnapshotTool.DO_MOVE);
        }
    }
    
    protected void setupSloshingCluster() throws HoneycombTestException {
        Log.INFO("Setting up cluster for sloshing activity...");
        Log.INFO("Making an 8 node config on the cheat.");
      
        String tmpfilename = "/tmp/cluster.conf." + safe_snapshot;
        Log.INFO ("Temp config filename: " + tmpfilename);
        ClusterNode master = cm.getMaster();
        master.runCmd("scp /config/config.properties hcb100:/tmp/config.properties");
        ExitStatus status = cheat.runCmdPlus("\"cat /tmp/config.properties" + 
         " | sed 's/honeycomb.cell.num_nodes\\s*=\\s*[0-9]*/honeycomb.cell.num_nodes=8/g' > " 
         + tmpfilename + " \"");
        
        if (status.getReturnCode() != 0)
            throw new HoneycombTestException("error copying config.properties on cheat: " + status.getOutputString());
        
        status = cheat.runCmdPlus("mv " + tmpfilename + " /tmp/config.properties");
        if (status.getReturnCode() != 0)
            throw new HoneycombTestException("error moving config.properties on cheat: " + status.getOutputString());

        status = cheat.runCmdPlus("\"cat /tmp/config.properties" + 
            " | sed 's/honeycomb.layout.expansion_status\\s*=\\S*//g' > " 
            + tmpfilename + " \"");

        if (status.getReturnCode() != 0)
            throw new HoneycombTestException("error copying config.properties on cheat: " + status.getOutputString());
        
        Log.INFO("Copying new config to all nodes.");
        for (int i = 101; i <= 116; i++) {
            cheat.runCmd("scp " + tmpfilename + " hcb" + i + ":/config/config.properties");
        }
        
        //cheat.runCmd("rm -f " + tmpfilename);
        
        stopCluster(16);
        waitForHCToDie(16);
        
        deleteData();
        already_clean = true;
        
        removeDevMode(16);
        rebootCluster(16);        
        
        Log.INFO("Sleeping for 3m for the cluster to reboot and startup again...");
        pause(180000);
        
        Log.INFO("Waiting for HC to be Online with 16 nodes...");
        waitForClusterToStart(16);        
    }
    
    protected void rebootCluster(int numNodes) throws HoneycombTestException {
        Log.INFO("Rebooting all nodes in the clutser.");
        for (int i = 1; i <= numNodes; i++) {
            cheat.runCmdPlus("ssh hcb" + (100 + i) + " 'reboot'");
        }
    }
    
    protected void deleteData() throws HoneycombTestException {
        Log.INFO("Deleting current data on cluster");
        snapshot_tool.deletedata();
    }
    
    protected void setupExistingCluster() throws HoneycombTestException {
        // if createdata option specified then the testcase will create some 
        // random data and then use that for testing of sloshing.
        
        if (create_data) {
            // Shutdown current cluster in dev mode
            if (!already_clean) {
                Log.INFO("Shutting down cluster...");
                stopCluster(8);
                waitForHCToDie(8);
                handleCurrentData();
                
                Log.INFO("Bringing cluster back online, waiting for CLI to report Cluster Online.");
                removeDevMode(8);
                startCluster(8);
                waitForClusterToStart(8);
            }
        
            Log.INFO("Creating some data on cluster...");
            doLoad();
            
            Log.INFO("Shutting down cluster...");
            stopCluster(8);
            waitForHCToDie(8);
            
            Log.INFO("Saving current snapshot of new data to " + createdata_snapshot);
            snapshot_tool.saveSnapshot(createdata_snapshot, "copy");
            
            Log.INFO("Bringing cluster back online, waiting for CLI to report Cluster Online.");
            removeDevMode(8);
            startCluster(8);
            waitForClusterToStart(8);            
        } else {
            // If snapshot specified then save current cluster state and use the 
            // snapshot given by the user.
            if (snapshot_name != null) {
                // Shutdown current cluster in dev mode
                Log.INFO("Shutting down cluster to restore from snapshot: " + snapshot_name);
                stopCluster(8);
         
                handleCurrentData();
                
                Log.INFO("Restoring cluster to specified snapshot: " + snapshot_name);
                snapshot_tool.restoreSnapshot(snapshot_name, SnapshotTool.DO_COPY);
                
                Log.INFO("Bringing cluster back online, waiting for CLI to report Cluster Online.");
                waitForClusterToStart(8);
            }
        }
    }
    
    protected void startExpansionNodes() throws HoneycombTestException {

        runOnCheat("echo honeycomb > /tmp/ipmi.pass",
                   "Error trying to setup ipmi password file on cheat.",true);
 
        Log.INFO("Using IPMI to power up nodes 8 through 16.");        
        for (int i = 109; i <= 116; i++) {
         Log.INFO("Jumpstarting node "+ i);        
         runOnCheat("ipmitool -I lan -H hcb" + i + 
                    "-sp -U Admin -f /tmp/ipmi.pass chassis power on",
                    "Ipmi did not work on node " + i, 
                    true);
         // wait for 2 mins, before jumpstarting next node
         pause(120000);
        }
        
        Log.INFO("Waiting on CLI to report that the cluster is online and 16 nodes are available.");
        // Wait for CLI to be acessible and sysstat returns Online
        boolean ready = false;
        boolean isClusterOnline = false; 
        boolean isHadbFaultTolerant = false;

        while (!ready) {
            try {
                pause(CLUSTER_WAKEUP_TIMEOUT);
                ArrayList lines = readSysstat();
                for (int i=0; i<lines.size(); i++) {
                    if ((((String)lines.get(i)).contains("Online"))) {
                        isClusterOnline = true;
                    }
                    if ((((String)lines.get(i)).contains("HAFaultTolerant"))) {
                        isHadbFaultTolerant = true;
                    }
                }
                if (isClusterOnline && isHadbFaultTolerant) {
                    ready = true;
                }
            } catch (Throwable e) {
                // ignore for now
            }
        }
    }
   
    protected void waitForExpansionReady() {
        Log.INFO("Verifying expansion status.");
           
        boolean ready = false;
        while (!ready) {
            try {
                pause(SLOSH_CHECK_MINUTES*60000);
                ArrayList lines = readCellAdm();
                for (int i=0; i<lines.size(); i++) {
                    if ((((String)lines.get(i)).contains("Expansion status is: ready"))) {
                        ready = true;
                    }
                }
            } catch (Throwable e) {
                // ignore for now
            }
        }
        // Log.INFO("Verifying recovery has completed.");
        // waitRecoveryCompletion();  
    }
   
    protected void setLostFragsCycleSpeed() throws HoneycombTestException {
        Log.INFO("set ddcfg recover_lost_frags to 1");
        DataDoctorState.getInstance().setValue(CLIState.LOST_FRAGS, 1);
    }
 
    protected void startSloshing() throws HoneycombTestException {
        try {
            // print cluster fullness and hadb object before expansio
            Log.INFO("Hadb Object Count and Cluster Fullness, Before Expansion");
            printStats();
        } catch(Throwable e) {
            throw new HoneycombTestException("Unable to print Cluster Fullness Stats: " + e.getMessage());
        }

        Log.INFO("Time to Slosh!!!");
        setLostFragsCycleSpeed(); 
        try {
            cli.runCommand(CELL_EXP_CMD);
        } catch (Throwable e) {
           throw new HoneycombTestException("Problem running cell expansion command: " + CELL_EXP_CMD);
        }
    }
    
   
    protected ArrayList readSysstat() throws Throwable {
        return readSysstat (false);
    }

    protected ArrayList readSysstat(boolean extended) throws Throwable {
        ArrayList result = new ArrayList();
        String line = null;
        String cmd = null;
        if (extended)
            cmd = "sysstat -r -F";
        else 
            cmd = "sysstat";

        BufferedReader br = cli.runCommand(cmd);
        while ((line = br.readLine()) != null) {
            result.add(line);
        }
        return result;
    }
    
    protected ArrayList readCellAdm() throws Throwable {
        ArrayList result = new ArrayList();
        String line = null;
        String cmd = null;
        BufferedReader br = cli.runCommand(CELLADM_CMD);
        while ((line = br.readLine()) != null) {
            result.add(line);
        }
        return result;
    }

    private String lastTimeStamp = null;
    protected void waitRecoveryCompletion() {
        DataDoctorState.getInstance().setValue(CLIState.LOST_FRAGS, 1);
                
        boolean recoveryComplete = false;
        String dataReliability = "";
        
        while (!recoveryComplete) {
            Log.INFO("Waiting for recovery to finish.");
            pause(SLOSH_CHECK_MINUTES*60000);
            try {
                ArrayList lines = readSysstat(true);
		 
		if (lines != null) { 
                    for (int i=0; i<lines.size(); i++) {
                        if(((String)lines.get(i)).contains("RecoverLostFrags")) {
               	            dataReliability = (String)lines.get(i);
                            break;
                        } 
                    } 
                    Log.INFO ("RLFL: " + dataReliability);
                }
               
                if (dataReliability != null && 
                    dataReliability.indexOf("RecoverLostFrags last completed at") == 0) {
                    Log.INFO ("lasTimeStamp=" + lastTimeStamp);
                    if ((lastTimeStamp != null) && (lastTimeStamp.equals(dataReliability)))
                            continue;

                    lastTimeStamp = dataReliability;
                    recoveryComplete = true;
                }
            } catch (Throwable e) {
              Log.ERROR("CLI access error:" + Log.stackTrace(e));
            }
        }
        Log.INFO(dataReliability);
    }
    
    protected void waitForSloshingCompletion(){
        Log.INFO("Checking for sloshing completion every " + SLOSH_CHECK_MINUTES + "m.");
        boolean ready = false;
        while (!ready) {
            try {
                pause(SLOSH_CHECK_MINUTES*60000);
                ArrayList lines = readCellAdm();
                for (int i=0; i<lines.size(); i++) {
                    if ((((String)lines.get(i)).contains("Expansion status is: complete"))) {
                        ready = true;
                    }
                }
            } catch (Throwable e) {
                // ignore for now
            }
        }
        waitRecoveryCompletion();        
    }
  
   /*
    *  Print Hadb count and cluster fullness     
    */   
    protected void printStats() throws Throwable {

        String line;
        BufferedReader br = cli.runCommand("df -h");
        while((line = (String)br.readLine()) != null) {
            Log.INFO(line);
        }
        
        Log.INFO("HADB object count: -->");
        for(int n=1; n<=16; n++) {
            ClusterNode node = cm.getNode(n);
            if(node.ping()) {
                ArrayList lines = node.readHadbObjectCount();
                for(int i=0; i<lines.size(); i++) {
                    Log.INFO((String)lines.get(i));
                }
                break;
            } 
        }
    }
 
    protected void verifyCluster() throws HoneycombTestException {     
        /**
         * wait for HADB to be HAFaultTolerant 
         */ 
        Log.INFO("wait for HADB to be HAFaultTolerant");  
        waitForClusterToStart(16);
 
        /**
         *  verify that hadb now has 12 active nodes
         *  and 4 spare nodes 
         */     
        ClusterNode master = cm.getMaster();
        ArrayList nodeList = master.readHadbmNodesStatus();
        int activeNodes = 0;
        int spareNodes = 0;
        for(int i=0; i<nodeList.size(); i++) {
            if(((String)nodeList.get(i)).contains("active")) {
                activeNodes++;
            } else if(((String)nodeList.get(i)).contains("spare")) {
                spareNodes++;
            }
        }
        if(activeNodes == 12 && spareNodes == 4) {
            Log.INFO("HADB Expansion Completed");
        } else {
            Log.ERROR("HADB excepts 12 active nodes and 4 spare nodes"); 
        }

        try {
            // print cluster fullness and hadb object after expansion
            Log.INFO("Hadb Object Count and Cluster Fullness, After Expansion");
            printStats();
        } catch(Throwable e) {
            throw new HoneycombTestException("Unable to print Cluster Fullness Stats: " + e.getMessage());
        }
 
        // If this run was from a snapshot then we can verify the data placement
        // with the snapshot
        String snapshot_to_verify_against = null;
        
        if (snapshot_name != null) {
            snapshot_to_verify_against = snapshot_name;
        } else if (createdata_snapshot != null) {
            snapshot_to_verify_against = createdata_snapshot;
        }
        
        if (snapshot_to_verify_against != null) {
            Log.INFO("Now that sloshing has completed, need to verify snapshost: " 
                     + snapshot_to_verify_against + " against current data.");
            snapshot_tool.verifyDataAgainstSnapshot(snapshot_to_verify_against);
        }
        
        // Other verification
        Log.INFO("Still missing other verification.");
    }
    
    protected void pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) { /* Ignore */ }
    }
        
    private static CheatNode cheat = CheatNode.getDefaultCheat();    
    public static int runOnCheat(String command, String onFailureMsg, boolean quiet) throws HoneycombTestException {
        int result = cheat.runCmdAndLog(command, quiet);
        if (result != 0) throw new HoneycombTestException(onFailureMsg);
        return result;
    }    
    
    class SloshThread extends Thread {
        public void run() {
            try {
                 setLostFragsCycleSpeed(); 
                 startSloshing();
            } catch (HoneycombTestException e) {
              Log.ERROR("Starting of sloshing failed...");
            }                   
            waitForSloshingCompletion();    
        }
    }
}
