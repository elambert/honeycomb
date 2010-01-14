package com.sun.honeycomb.hctest.cases;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class DNSTestSuite extends HoneycombLocalSuite {
	
	protected Hashtable hash = new Hashtable();
    protected String MINOR_VERSION_COMMAND = 
        "curl -s http://10.7.228.10/~hcbuild/repository/releases/1.1/"
        + " | awk {'print $3'} | cut -d'/' -f1,1 | egrep -v "
        + "'definition|junk' | grep -v ' ' | cut -d'-' -f2,2 | "
        + "sort -n | tail -1";
    protected String PRE_VERSION_COMMAND = 
        "curl -s -q http://10.7.228.10/~hcbuild/repository/releases/1.1/"
        + " | awk {'print $3'} | cut -d'/' -f1,1 | egrep -v "
        + "'definition\\|junk' | grep -v ' ' | grep ";
    protected String POST_VERSION_COMMAND = " | tail -1";
    protected String UPGRADE_DOMAIN = "hc-dev.sfbay.sun.com";
    protected String DNS1 = "10.7.224.10";
    protected String DNS2 = "129.146.11.21";
    protected String DOMAIN_NAME = "sun.com";
    protected String SEARCH_DNS = "sfbay.sun.com";
    protected String SSH = "ssh -o StrictHostKeyChecking=no";
    protected String SSH_ROOT = SSH + " root@";
    protected String PING = "ping -c 1 -t 10";
    protected String DEFAULT_HOSTNAME = "hc-dev.sfbay.sun.com";
    protected String DEFAULT_SMTPNAME = "hclog301.SFBay.Sun.COM";
    protected String SVCS = "svcs honeycomb_config_network:default";
    protected String SVCS_DISABLE = 
        "svcadm disable honeycomb_config_network:default";
    protected String SVCS_ENABLE = 
        "svcadm enable honeycomb_config_network:default";
    protected String spOK = 
        "Correct irules for service processor access found";
    protected String adOK = "Correct irules for admin access found";
    protected String daOK = "Correct irules for data access found";
    protected String spNO =
        "ERR: Incorrect irules for service processor access";
    protected String adNO = "ERR: Incorrect irules for admin access";
    protected String daNO = "ERR: Incorrect irules for data access";
    protected String allPattern = "accpt\\s+\\|\\s+ip\\s+0\\s+";
    protected String LOG_LOCATION = "/var/adm/messages";
    protected String QUERY_STATUS_STRING = "HAFaultTolerant";
    private String[] APC_CLUSTERS = {"dev309", "dev308", "dev319"};
    private String[] APC_IPS = {"10.7.224.186", "10.7.224.166", "10.7.225.166"};
    protected long CHECK_CLUSTER_INTERVAL = 300000; // milliseconds
    protected long SLEEP_MASTER_FAILOVER = 60000; // milliseconds
    protected long SLEEP_WAKEUP_TIMEOUT = 60000; // milliseconds
    protected long STORE_DURATION = 28800000; // milliseconds 8 hours
    protected long SLEEP_DISK_DOWN = 300000; //milliseconds
    protected long SLEEP_HIVECFG = 300000; //milliseconds
    protected long SLEEP_REBOOT_CLUSTER = 900000; //milliseconds
    protected long SLEEP_SWITCH_DOWN_UP = 300000; //milliseconds
    protected long SLEEP_RECOVER = 300000; //milliseconds
    protected int MAX_ONLINE_ITERATIONS = 45; // iterations
    protected int DISKS_PER_NODE = 4;
    protected int NODE_NUM_OFFSET = 101;
    protected int THREADS = 1;
    protected int NODE_PORT_OFFSET = 2001;
    
    protected CLI cli = null;
    protected BufferedReader stdout = null;
    protected int nodes = 0;
    protected int masterCell = 0;
    protected String adminVIP = null;
    protected String dataVIP = null;
    protected String spIP = null;
    protected String nodesOnlineString = null;
    protected String cluster = null;
    protected String hostname = null;
    protected String smtpName = null;
    protected String smtpNameOriginal = null;
    protected String diskString = "";
    protected String portString = "";
    protected String apcIP = "";
    protected boolean okToProceed = false;
    protected boolean onAPCCluster = false;
    protected boolean isPowerPortDown = false;
    protected boolean isDNSDisabled = false;
    protected boolean isSMTPChanged = false;
    protected TestCase self;
    
    public DNSTestSuite() {
        super();
    }

    
    /* 
     * Tests must be run on a cluster that is already online.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        self = createTestCase("DNS Test Suite",
                "Test DNS functionality of Honeycomb");
        self.addTag("DNSTestSuite");
        if (self.excludeCase()) 
            return;
        super.setUp(); 
        cluster = 
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        hostname = TestRunner.getProperty("host");
        hostname = (hostname != null) ? hostname : DEFAULT_HOSTNAME;
        smtpName = TestRunner.getProperty("smtp");
        smtpName = (smtpName != null) ? smtpName : DEFAULT_SMTPNAME;
        adminVIP = testBed.adminVIPaddr;
        dataVIP = testBed.dataVIPaddr;
        spIP = testBed.spIPaddr;
        initAll();
        nodes = cli.hwstat(masterCell).getNodes().size();
        nodesOnlineString = nodes + " nodes online, " + 
            (DISKS_PER_NODE * nodes) + " disks online";
        
        
        for (int i = 0; i < APC_IPS.length; i++) {
            hash.put(APC_CLUSTERS[i], APC_IPS[i]);
        }
        if (hash.containsKey(cluster)) {
            Log.INFO("APC cluster detected: " + cluster);
            apcIP = (String) hash.get(cluster);
            onAPCCluster = true;
        }  
      
        okToProceed = setupEnableDNS();
    }


    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        if (excludeCase()) return;
        recover();
        super.tearDown();
        
    }


    ///////////////////////////////////////////////////////////////////////////
    //////////////////////////////////  TESTS /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public boolean setupEnableDNS() throws Throwable {
        okToProceed = false;
        Log.INFO("  Enables DNS on the cluster and reboots");
        Log.INFO("  Verifies hivecfg settings are correct");
        return enableDNSAndTestConfigUpdate();
    }
    
    public boolean test1_SMTPServer() throws Throwable {
        if (excludeCase() || !okToProceed) return false;
        Log.INFO("  Changes SMTP to a domain name, triggers an alert");
        Log.INFO("  Verifies alert was sent");
        return changeSMTPandTriggerAlert();
    }
    
    public boolean test2_MasterFailover() throws Throwable {
        if (excludeCase() || !okToProceed) return false;
        Log.INFO("  Does a master failover");
        Log.INFO("  Verifies DNS functionality persists");
        return masterFailoverWithDNS();
    }
    
    public boolean test3_RebootAll() throws Throwable {
        if (excludeCase() || !okToProceed) return false;
        Log.INFO("  Does a reboot all");
        Log.INFO("  Verifies DNS functionality persists");
        return rebootAllWithDNS();
    }
    
    public boolean test4_SwitchFailover() throws Throwable {
        if (excludeCase() || !okToProceed) return false;
        Log.INFO("  Does a switch failover");
        Log.INFO("  Verifies DNS functionality persists");
        if (!onAPCCluster) {
        	Log.INFO(
        		"The switch failover test can only be run on a cluster with an APC");
        	Log.INFO("Passing test... (would it be right to fail?)");
        	return true;
        }
        boolean ok = switchFailoverWithDNS();
        if (!ok)
        	recover();
        return ok;
    }

	public boolean test5_AuthClients() throws Throwable {
        if (excludeCase() || !okToProceed) return false;
        Log.INFO("  Changes auth clients to a hostname");
        Log.INFO("  Verifies rules are programmed corectly");
        return authClientsWithDNS();
    }
        
    public boolean test6_Svcadm() throws Throwable {
        if (excludeCase() || !okToProceed) return false;
        Log.INFO("  Restarts greenline service honeycomb_congig_network:default");
        Log.INFO("  Verifies service is disabled/enabled properly");
        return disableEnableSvcs();
    }
    
    public boolean test7_ValidateProperties() throws Throwable {
        if (excludeCase() || !okToProceed) return false;
        Log.INFO("  Sets DNS to \"no\", attempts to change properties using a hostname");
        Log.INFO("  Verifies all hostname changes are rejected");
        return validateProperties();
    }
    
    public boolean test8_DownloadUpgrade() throws Throwable {
        if (excludeCase() || !okToProceed) return false;
        Log.INFO("  Uses a hostname to download the ISO");
        Log.INFO("  Verifies download succeeded");
        return upgradeCluster();
    }
    

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////// HELPERS /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    protected boolean upgradeCluster() throws Throwable {
        
        // need to ping hcdev from master before and after
        if (!pingFromMaster(hostname))
            return false;
        
        String minorVersion = null;
        String currentVersion = null;
        String s = null;
        Log.INFO("Getting Minor Version...");
        runSystemCommand(MINOR_VERSION_COMMAND);
        while ((s = stdout.readLine()) != null) {
            minorVersion = s.trim();
            Log.INFO(s);
        }
        Log.INFO("Getting Major Version...");
        runSystemCommand(
                PRE_VERSION_COMMAND + minorVersion + POST_VERSION_COMMAND);
        while ((s = stdout.readLine()) != null) {
            currentVersion = s.trim();
            Log.INFO("Current Version: " + currentVersion);
        }
        String url =
            "http://" + UPGRADE_DOMAIN + "/~hcbuild/repository/releases/1.1/"
            + currentVersion + "/AUTOBUILT/pkgdir/st5800_"
            + currentVersion + ".iso";
        Log.INFO("Validating URL...");
        runSystemCommand("wget -O - -q " + url + " 2>&1 >> /dev/null");
        while ((s = stdout.readLine()) != null)
            Log.INFO(s);

        if (currentVersion != null) {
            stdout = cli.runCommand(
            		"upgrade -c " + masterCell + " -F download " + url);
            String line;
            while ((line = stdout.readLine()) != null) {
                if (line.contains("bytes successfully")) {
                    Log.INFO(" ---> " + line);
                    return pingFromMaster(hostname);
                }
            }
            Log.ERROR("Problem with upgrade detected... failing test");
        }
        return false;
    }
    
    protected boolean changeSMTPandTriggerAlert() throws Throwable {
        // save original smtp
        // change smtp to SMTP_NAME 
        // reboot all and wait
        // roll logs
        // disable/enable a disk and wait
        // grep logs
        // change back to saved smtp
        // reboot all?
        return (saveSMTP() &&
                changeSMTP(smtpName) &&
                rebootAndWait("all") &&
                rollLogs() &&
                triggerAlert() &&
                scrapeLogs() &&
                changeSMTP(smtpNameOriginal) &&
                rebootAndWait("all"));              
    }
    
    protected boolean masterFailoverWithDNS() throws Throwable {
        // ping from master
        // master failover
        // ping from master 
        return (pingFromMaster(hostname) &&
                masterFailover() &&
                pingFromMaster(hostname));
    }
    
    protected boolean rebootAllWithDNS() throws Throwable {
        // ping from master
        // reboot all
        // ping from master     
        return (pingFromMaster(hostname) &&
                rebootAndWait("all") &&
                pingFromMaster(hostname));
    }
    
    private boolean switchFailoverWithDNS() throws Throwable {
		// setup apc script
    	// ping from master
    	// switch failover
    	// ping from master
    	// switch failback
    	// ping from master
    	return (setupAPCScript() &&
    			pingFromMaster(hostname) &&
    			apcCommand(1, "Off") &&
    			pingFromMaster(hostname) &&
    			apcCommand(1, "On") &&
    			pingFromMaster(hostname));
	}
    
    protected boolean authClientsWithDNS() throws Throwable {
        // change authclients to host
        // reboot all and wait
        // verify irules
        // change authclients to all
        // reboot all and wait
        return (changeAuthorizedClients(hostname) &&
                rebootAndWait("all") &&
                verifyIrules(getIP(hostname)) &&
                pingFromMaster(hostname) &&
                changeAuthorizedClients("all") &&
                rebootAndWait("all"));
    }
    
    protected boolean enableDNSAndTestConfigUpdate() throws Throwable {
        // change dns to yes with setting
        // reboot all and wait
        // verify hivecfg settings
        // run upgrade checker
        if (enableDNS() &&
             rebootAndWait("all") &&
             verifyHivecfg() //&&
                //upgradeChecker()
        ) {
            okToProceed = true;
            return true;
        }
        return false;
    }
    
    protected boolean disableEnableSvcs() throws Throwable {
        //check svsc is online
        //disable svc
        //check svc is offline
        //enable svc
        //check svc is online
        int i = ((int)(Math.random() * nodes));
        portString = "" + (i + NODE_PORT_OFFSET);
        
        return (verifySvcs("online") &&
                changeSvcs("disable") &&
                verifySvcs("disabled") &&
                changeSvcs("enable") &&
                verifySvcs("online"));
    }

    protected boolean validateProperties() throws IOException, Throwable {
        // disable dns on cluster
        // reboot all and wait
        // try to change props with hostnames
        // validate failure
        // enable dns again
        return (disableDNS() &&
                rebootAndWait("all") &&
                changeProps() &&
                enableDNS() &&
                rebootAndWait("all"));
    }
    ///////////////////////////////////////////////////////////////////////////
    //////////////////////////////// UTILITIES ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    protected boolean pingFromMaster(String who) throws Throwable {
        Log.INFO("Attempting to ping " + who + " from the master node");
        if (runSystemCommand(
            SSH_ROOT + adminVIP +" \""+ PING + " " + who + "\"" ))
        {
            Log.INFO(who + " is pingable");
            return true;
        } else {
            Log.ERROR(who + " is not pingable");
            return false;
        }
    }
    
    protected boolean enableDNS() throws IOException {
        Log.INFO("Enabling DNS on " + cluster);
        try {
            cli.runCommand(
                    "hivecfg -D y -1 " + DNS1 + " -2 " + DNS2 + " -m " +
                    DOMAIN_NAME + " -e " + SEARCH_DNS
            );
            Log.INFO("DNS enabled on " + cluster);
            pause(SLEEP_HIVECFG);
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.ERROR("Could not enable DNS on " + cluster);
            Log.ERROR("Failing test...");
            return false;
        }

        try {
            stdout = cli.runCommand("hivecfg");
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.ERROR("Could not verify DNS on " + cluster);
            Log.ERROR("Failing test...");
            return false;
        }
        String line;
        while ((line = stdout.readLine()) != null) {
            if (line.contains("DNS = y")) {
                Log.INFO(line);
                Log.INFO("DNS verified on " + cluster);
                isDNSDisabled = false;
                return true; //OK
            }
        }
        Log.ERROR("Could not verify DNS on " + cluster);
        Log.ERROR("Failing test...");
        return false;
    }

    protected boolean disableDNS() throws Throwable {
        Log.INFO("Disabling DNS on cluster " + cluster);
        stdout = cli.runCommand("hivecfg -D n");
        String line;
        while ((line = stdout.readLine()) != null) {
            Log.INFO(line);
            if (line.contains("You must reboot the hive")) {
                Log.INFO(line);
                Log.INFO("DNS disabled");
                pause(SLEEP_HIVECFG);
                isDNSDisabled = true;
                return true;
            }
        }
        Log.ERROR("DNS was not detected as disabled");
        return false;
    }
    
    protected boolean changeProps() throws Throwable {
        Log.INFO("Changing DNS properties to " + hostname);
        Log.INFO("All are expected to fail with DNS = n");
        stdout = cli.runCommand("hivecfg -n " + hostname);
        boolean ok1 = verifyDNSRejected();
        stdout = cli.runCommand("hivecfg -s " + hostname);
        boolean ok2 = verifyDNSRejected();
        stdout = cli.runCommand("hivecfg -h " + hostname);
        boolean ok3 = verifyDNSRejected();
        stdout = cli.runCommand("hivecfg -x " + hostname);
        boolean ok4 = verifyDNSRejected();
        stdout = cli.runCommand(
            "hivecfg -x " + hostname + " -h " + hostname + 
            " -s " + hostname + " -n " + hostname);
        boolean ok5 = verifyDNSRejected();
        return ok1 && ok2 && ok3 && ok4 && ok5;
        
    }
    
    protected boolean verifyDNSRejected() throws IOException {
        String line;
        boolean ok = false;
        while ((line = stdout.readLine()) != null) {
            if (line.contains("An IP address must be specified"))
                Log.INFO(" ---> " + line);
                ok = true;
        }
        if (ok) {
            Log.INFO("Hostname successfully rejected: " + hostname);
        } else {
            Log.ERROR("Hostname was accepted: " + hostname);
        }
        return ok;
    }
    
    protected boolean masterFailover() throws Throwable {
        addNoFsckFlags();
        showMasterName();
        Log.INFO("Failing over the master node...");
        try {
            runSystemCommand(SSH_ROOT + adminVIP + " reboot");
            pause(SLEEP_MASTER_FAILOVER);
            showMasterName();
            return true;
        } catch (HoneycombTestException e) {
            Log.stackTrace(e);
            Log.ERROR("Could not failover the master node");
            return false;
        }
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
    
    protected boolean apcCommand(int port, String action) throws Throwable {
        Log.INFO("Powering " + action + " Switch " + port);
        boolean ok = runSystemCommand(
            "/opt/abt/bin/abt Power" + action + "PduPort on=apc:apc@" +
            apcIP + ":" + port +" logDir=/mnt/test/ " +
            "2>&1 >> /dev/null 2>&1 >> /dev/null");
        if (ok) {
            isPowerPortDown = (action.equals("Off")) ? true : false;
            Log.INFO("Power " + action + " successful");
            pause(SLEEP_SWITCH_DOWN_UP);
            ok = waitForClusterToStart();
        }
        return ok;
    }
    
    protected boolean reboot(String who) throws Throwable {
        addNoFsckFlags();
        Log.INFO("Rebooting cluster...");
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

    protected boolean changeSvcs(String action) throws Throwable {
        if (action.equals("enable")) {
            return runSystemCommand(
                    SSH + " -p " + portString + " root@" + adminVIP +
                    " " + SVCS_ENABLE);
        } else {
            return runSystemCommand(
                    SSH + " -p " + portString + " root@" + adminVIP +
                    " " + SVCS_DISABLE);
        }
    }
    
    
    protected boolean changeAuthorizedClients(String who) throws Throwable {
        Log.INFO("Changing Authorized Clients...");
            try {
                stdout = cli.runCommand(
                        "hivecfg --authorized_clients " + who);
                pause(SLEEP_HIVECFG);
                String line;
                boolean ok = true;
                while ((line = stdout.readLine()) != null) {
                    Log.INFO(line);
                    if (line.contains("Invalid authorized client") ||
                        line.contains("Invalid authorizied client") ||
                        line.contains("An IP address must be specified") ||
                        line.contains("Invalid mask bit") ||
                        line.contains("Too many authorized clients") ||
                        line.contains("Invalid IP/mask combination"))
                    {
                        Log.ERROR(line);
                        ok = false;
                    }
                }
                if (!ok)
                    return false;
            } catch (Throwable e) {
                Log.stackTrace(e);
                return false;
            }
        return true;
    }
    
    protected boolean verifySvcs(String expected) throws Throwable {
        runSystemCommand(SSH + " -p " + portString + " root@" + adminVIP +
                " " + SVCS);
        String line;
        while ((line = stdout.readLine()) !=null) {
            if (line.contains(expected)) {
                Log.INFO("SVCS status same as expected:" + expected);
                return true;
            }
        }
        Log.ERROR("SVCS status not expected:" + expected);
        return false;
    }
    
    protected boolean verifyHivecfg() throws Throwable {
        boolean ok = true;
        String hivecfg = "";
        stdout = cli.runCommand("hivecfg");
        String line;
        while ((line = stdout.readLine()) != null) {
            Log.INFO(line);
            hivecfg += line;
        }
        if (!hivecfg.contains("DNS = y")) {
            ok = false;
            Log.ERROR("\"DNS = y\" not found");
        }
        if (!hivecfg.contains("Domain Name = " + DOMAIN_NAME)) {
            ok = false;
            Log.ERROR("\"Domain Name = " + DOMAIN_NAME + "\" not found");
        }
        if (!hivecfg.contains("DNS Search = " + SEARCH_DNS)) {
            ok = false;
            Log.ERROR("\"DNS Search = " + SEARCH_DNS + "\" not found");
        }
        if (!hivecfg.contains("Primary DNS Server = " + DNS1)) {
            ok = false;
            Log.ERROR("\"Primary DNS Server = " + DNS1 + "\" not found");
        }
        if (!hivecfg.contains("Secondary DNS Server = " + DNS2)) {
            ok = false;
            Log.ERROR("\"Secondary DNS Server = " + DNS2 + "\" not found");
        }
        if (!ok) {
            Log.ERROR("Hivecfg settings not updated correctly");
            return false;
        } else {
            Log.INFO("Correct hivecfg settings detected");
            return true;
        }
    }
    
    protected boolean verifyIrules(String who) throws Throwable {
        Log.INFO("Verifying irules for " + who);
        String irules = "";
        try {
            runSystemCommand(
                SSH_ROOT + adminVIP + " /opt/honeycomb/bin/irules.sh" +
                " > /tmp/irules.out");
            FileReader input = new FileReader("/tmp/irules.out");
            BufferedReader bufRead = new BufferedReader(input);
            String line;
            while ((line = bufRead.readLine()) != null) {
                Log.INFO(line);
                irules += line;
            }
        } catch (HoneycombTestException e) {
            throw new HoneycombTestException("Failed to retrieve switch rules "
                                             + Log.stackTrace(e));
        }
        if (irules == null ||
            irules.equals("")) {
            throw new HoneycombTestException("Failed to retrieve switch rules");
        }
        
        Log.INFO("spIP: " + spIP);
        Log.INFO("adminVIP: " + adminVIP);
        Log.INFO("dataVIP: " + dataVIP);
        String cheat = allPattern + spIP;
        String admin = allPattern + adminVIP;
        String data = "accpt\\s+\\|\\s+ip\\s+"+who+"\\s+"+dataVIP;
        boolean ok1 = matchPattern(cheat, irules, spOK, spNO);
        boolean ok2 = matchPattern(admin, irules, adOK, adNO);
        boolean ok3 = matchPattern(data, irules, daOK, daNO);
        boolean ok4 = true;
        for (int i = 0; i < nodes; i++) {
            String node =
                "forwd\\s+\\|\\s+tcp\\s+" + i + "\\s+8080\\s+" + who +
                "\\s+" + dataVIP;      
            if (!matchPattern(node,
                    irules,
                    "Correct irules for node "+(i+1)+" access found",
                    "ERR: Incorrect irules for node "+(i+1)+" access"))
                ok4 = false;
        }
        return ok1 && ok2 && ok3 && ok4;
    }
    
    protected boolean waitForClusterToStart() throws HoneycombTestException {
        // Wait for cli to be accessible and sysstat returns Online

        Log.INFO("Waiting for cluster to come Online...");
        boolean ready = false;
        int i = MAX_ONLINE_ITERATIONS;
        while (i > 0 && !ready) {
            try {
                i--;
                ArrayList lines = readSysstat();
                if (lines.toString().contains(nodesOnlineString)) {
                    ready = true;
                }    
                if (!ready)
                    pause(SLEEP_WAKEUP_TIMEOUT);
            } catch (Throwable e) {
                pause(SLEEP_WAKEUP_TIMEOUT);
            }
        }
        if (i == 0) {
            Log.WARN("Cluster is not Online");
        }
        if (!ready)
            okToProceed = false;
        return ready;
    }

    protected void recover() throws Throwable {
    	if (isPowerPortDown) {
    		Log.INFO(" [recovery] - Trying to turn on power port...");
    		if (apcCommand(1, "On"))
    			Log.INFO(" [recovery] - Power port successfully turned on");
    		pause(SLEEP_RECOVER);
    	}
    	
    	if (isDNSDisabled) {
    		Log.INFO(" [recovery] - Trying to enable DNS...");
    		if (enableDNS())
    			Log.INFO(" [recovery] - DNS successfully enabled");
    		pause(SLEEP_RECOVER);
    	}
    	if (isSMTPChanged) {
    		Log.INFO(" [recovery] - Trying to restore SMTP...");
    		if (changeSMTP(smtpNameOriginal))
    			Log.INFO(" [recovery] - SMTP successfully restored");
    		pause(SLEEP_RECOVER);
    	}
    }
    protected boolean saveSMTP() throws Throwable {
        smtpNameOriginal = "";
        stdout = cli.runCommand("hivecfg");
        String pat = "SMTP Server = (.*)";
        Pattern pattern = Pattern.compile(pat, Pattern.MULTILINE);
        String line;
        while ((line = stdout.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                Log.INFO(line);
                smtpNameOriginal = matcher.group(1).toString();
                Log.INFO(
                    "Saving " + smtpNameOriginal + " as original smtp server");
                return true;
            }
        }
        return false;
    }
    
    protected boolean changeSMTP(String who) throws Throwable {
        Log.INFO("Changing SMTP server to " + who);
        cli.runCommand("hivecfg -s " + who);
        pause(SLEEP_HIVECFG);
        Log.INFO("Verifying change");
        stdout = cli.runCommand("hivecfg");
        String pat = "SMTP Server = " + who;
        Pattern pattern = Pattern.compile(pat, Pattern.MULTILINE);
        String line;
        while ((line = stdout.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                Log.INFO(line);
                Log.INFO("SMTP server changed successfully to " + who);
                isSMTPChanged =  (who == smtpNameOriginal) ? false : true;
                return true;
            }
        }
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

    protected String getIP(String who) {
        String ip = "";
        try {
            java.net.InetAddress inetAdd =
                java.net.InetAddress.getByName(who);
            ip = inetAdd.getHostAddress();
        } catch(java.net.UnknownHostException uhe) {
            Log.stackTrace(uhe);
            Log.ERROR("Unknown Host Exception");
        }
        return ip;
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
    
    protected boolean rollLogs() {
        boolean ok = true;
        for (int i = 0; i < nodes; i++) {
            int node = i + NODE_PORT_OFFSET;
            try {
                runSystemCommand(
                    SSH + " -p " + node + " root@" +
                    adminVIP + " logadm " + LOG_LOCATION + " -s 10b");
            } catch (Throwable e) {
                Log.stackTrace(e);
                Log.ERROR("Could not roll logs on " + (i + NODE_NUM_OFFSET));
                ok = false;
            }
        }
        return ok;
    }
    
    protected boolean scrapeLogs() throws Throwable {
        runSystemCommand(
            SSH_ROOT + adminVIP + " grep AlertMail.email " + LOG_LOCATION + 
            " | grep " + diskString);
        String line;
        int alerts = 0;
        while ((line = stdout.readLine()) != null) {
            Log.INFO(line);
            alerts += 1;
        }
        if (alerts >= 2) {
            Log.INFO("Alerts: " + alerts);
            return true;
        }
        Log.ERROR("Alerts: " + alerts);
        Log.ERROR("Did not detect that the alerts attempted to sent");
        return false;
    }

    protected boolean triggerAlert() throws Throwable {
        // get non master node
        runSystemCommand(SSH_ROOT + adminVIP + " hostname");
        String master = stdout.readLine();
        int i = ((int)(Math.random() * nodes));
        String node = "hcb" + (i + 1 + NODE_NUM_OFFSET);
        while (node.equals(master)) {
            i = ((int)(Math.random() * nodes));
            node = "hcb"+(i + 1 + NODE_NUM_OFFSET);
        }
        int n = i + 1 + NODE_NUM_OFFSET;
        int d = (int)(Math.random() * DISKS_PER_NODE);
        diskString = "DISK-" + n + ":" + d;
        cli.runCommand("hwcfg -F -D " + diskString);
        pause(SLEEP_DISK_DOWN);
        cli.runCommand("hwcfg -F -E " + diskString);
        pause(SLEEP_DISK_DOWN);
        waitForClusterToStart();
        return true;
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

    protected ArrayList readSysstat() throws Throwable {
        ArrayList result = new ArrayList();
        String line = null;
        String cmd = "sysstat";
        stdout = cli.runCommand(cmd + " -c " + masterCell);
        while ((line = stdout.readLine()) != null)
            result.add(line);
        return result;
    }

    protected void addNoFsckFlags() throws Throwable {
        Log.INFO("Adding no FSCK flags...");
        for (int i = 0; i < nodes; i++) {
            int node = i + NODE_NUM_OFFSET;
            int port = i + NODE_PORT_OFFSET;
            try {
                runSystemCommand(
                        SSH + " -p " + port +" root@" + adminVIP + 
                " touch /config/clean_unmount__dev_dsk_c0t0d0s4");
                runSystemCommand(
                        SSH + " -p " + port +" root@" + adminVIP +  
                " touch /config/clean_unmount__dev_dsk_c0t1d0s4");
                runSystemCommand(
                        SSH + " -p " + port +" root@" + adminVIP +  
                " touch /config/clean_unmount__dev_dsk_c1t0d0s4");
                runSystemCommand(
                        SSH + " -p " + port +" root@" + adminVIP +  
                " touch /config/clean_unmount__dev_dsk_c1t1d0s4");
                Log.INFO(" - node " + node + ": ok");
            } catch (HoneycombTestException e) {
                Log.stackTrace(e);
                Log.ERROR(" - node " + node + ": not ok");
            }
        }
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
    
    protected void pause(long milli) {
        Log.INFO("Sleeping for " + formatTime(milli));
        try {
            Thread.sleep(milli);
        } catch (InterruptedException e) { /* Ignore */ }
    }
}



