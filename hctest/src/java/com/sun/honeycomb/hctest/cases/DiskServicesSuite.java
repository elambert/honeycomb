package com.sun.honeycomb.hctest.cases;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class DiskServicesSuite extends HoneycombLocalSuite {

    protected int DISKS_PER_NODE = 4;
    protected String NEWLINE = System.getProperty("line.separator");
    protected String SSH = "ssh -o StrictHostKeyChecking=no";
    protected String SSH_ROOT = SSH + " root@";
    protected String DEFAULT_MAIL_SERVER = "10.7.224.10";
    protected String DEFAULT_TASKDONE_LOCATION = "/tmp/";
    protected String NOHONEYCOMB = "/config/nohoneycomb";
    protected String NOREBOOT = "/config/noreboot";
    protected String LOG_LOCATION = "/var/adm/messages";
    protected String[] CASE1 = {"0"};
    protected String[] CASE2 = {"1"};
    protected String[] CASE3 = {"0", "1"};
    protected String[] CASE4 = {"0", "1", "2"};
    protected String[] CASE5 = {"0", "1", "2", "3"};
    protected long SLEEP_REBOOT_CLUSTER = 900000; //milliseconds
    protected long SLEEP_WAKEUP_TIMEOUT = 60000; // milliseconds
    protected long SLEEP_DISK_DISABLE = 60000; // milliseconds
    protected long SLEEP_DISK_ENABLE = 60000; // milliseconds
    protected long SLEEP_NODE_DISABLE = 60000; // milliseconds
    protected long SLEEP_TASKDONE = 60000; //milliseconds
    protected int MAX_ONLINE_ITERATIONS = 60; // iterations
    protected int MAX_TASKDONE_ITERATIONS = 4320; // at 1 minutes each => 72 hrs
    protected int NODE_NUM_OFFSET = 101;
    protected int NODE_PORT_OFFSET = 2001;
    
    private TestCase self = null;
    private String cluster = null;
    private String adminVIP = null;
    private String dataVIP = null;
    private String spIP = null;
    private int nodes = 0;
    private int disks = 0;
    private String nodesOnlineString = null;
    private CLI cli = null;
    private BufferedReader stdout = null;
    private int masterCell = 0;
    private int activeNode = 0;
    private boolean okToProceed = false;
    private boolean isSetup = false;
    private String mailServer = DEFAULT_MAIL_SERVER;
    private String email = null;
    private ArrayList disabledDisks = new ArrayList();
    private ArrayList enabledDisks = new ArrayList();
    private String taskdoneLocation = DEFAULT_TASKDONE_LOCATION;
    private String subject = null;
    private String message = null;
    private String[] savedLogLines = null;


    public DiskServicesSuite() {
        super();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        self = createTestCase("Disk Services Suite",
                "Test Disk Serivces - semi automated test");
        self.addTag("DiskServicesSuite");
        if (self.excludeCase()) 
            return;
        super.setUp(); 
        cluster = 
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        email = TestRunner.getProperty("email");
        if (email == null) {
            Log.ERROR("Email must be provided");
            return;
        }
        Log.INFO("Email: " + email);
        String server = TestRunner.getProperty("mailserver");
        if (server != null) {
            mailServer = server;
        }
        Log.INFO("Mail Server: " + mailServer);
        String location= TestRunner.getProperty("location");
        if (location != null) {
            taskdoneLocation = location;
        }
        Log.INFO("Taskdone Location: " + taskdoneLocation);
        
        adminVIP = testBed.adminVIPaddr;
        dataVIP = testBed.dataVIPaddr;
        spIP = testBed.spIPaddr;
        initAll();
        nodes = cli.hwstat(masterCell).getNodes().size();
        disks = (DISKS_PER_NODE * nodes);
        nodesOnlineString = nodes + " nodes online, " + 
            disks + " disks online";
        
        okToProceed = sendTestMail();
        isSetup  = true;
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

    // 10/24/07 18:34:34 SUM: PASS RESULT_ID=1113920 RUNTIME=00:15:34
    public boolean test01_DiskInitializationRebootDisksOnline() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Reboot a system with all disks online via CLI 'reboot'");
        Log.INFO(" ===> Validate that all disks come back online after reboot");
        return rebootAndVerify();
    }
    // 10/24/07 18:57:04 SUM: PASS RESULT_ID=1113921 RUNTIME=00:22:29 
    public boolean test02_DiskInitializationDiskDisableEnable() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Disable disk via CLI \"hwcfg -D DISK-1XX:Y\"");
        Log.INFO(" ===> Validate that the disk is no longer used by Honeycomb");
        Log.INFO(" ===> Run this test against four disks from different slots:");
        Log.INFO(" ===> /data/0, /data/1, /data/2, /data/3");
        return disableDisksAndVerify();
    }
    // 10/24/07 19:32:39 SUM: PASS RESULT_ID=1113922 RUNTIME=00:35:34
    public boolean test03_DiskInitializationDiskDisabledReboot() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Reboot a system with a disabled disk");
        Log.INFO(" ===> Validate that disabled disk remains disabled after reboot.");
        Log.INFO(" ===> Run this test against four disks from different slots:");
        Log.INFO(" ===> /data/0, /data/1, /data/2, /data/3");
        return disableDisksRebootAndVerify();
    }
    // 10/25/07 15:49:49 SUM: PASS RESULT_ID=1113923
    public boolean test04_NodeStartupMissingDrives() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Start honeycomb with missing disk(s)");
        Log.INFO(" ===> Validate that CLI reports drives as missing.");
        Log.INFO(" ===> Does test for each of the following cases:");
        Log.INFO(" ===> /data/0");
        Log.INFO(" ===> /data/1");
        Log.INFO(" ===> /data/0 + /data/1");
        Log.INFO(" ===> /data/0 + /data/1 + /data/2");
        return startupWithDisksDown();
    }
    
    // Untested
    public boolean test05_NodeStartupDisabledDisks() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Start honeycomb with missing disk(s)");
        Log.INFO(" ===> Validate that CLI reports drives as missing.");
        Log.INFO(" ===> Does test for each of the following cases:");
        Log.INFO(" ===> /data/0");
        Log.INFO(" ===> /data/1");
        Log.INFO(" ===> /data/0 + /data/1");
        Log.INFO(" ===> /data/0 + /data/1 + /data/2");
        return startupWithDisksDisabled();
    }
    
    // Expected Fail, not supported yet per Satish
    public boolean test06_NodeStartupWithBadDrives() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Start honeycomb with bad disk(s)");
        Log.INFO(" ===> Validate that CLI reports drives as missing.");
        Log.INFO(" ===> Does test for each of the following cases:");
        Log.INFO(" ===> /data/0");
        Log.INFO(" ===> /data/1");
        Log.INFO(" ===> /data/0 + /data/1");
        Log.INFO(" ===> /data/0 + /data/1 + /data/2");
        return startupWithBadDisks();
    }
    
    // Expected Fail 6605537
    public boolean test07_DiskReplacementSimBlankDisk() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Replaces a disabled disk with a simulated blank disk");
        Log.INFO(" ===> Validate that new disk is properly formatted by ");
        Log.INFO(" ===> disk_replacement code, newfs'd, mounted, and is displayed");
        Log.INFO(" ===> as available on the CLI.");
        Log.INFO(" ===> Run this test against four disks from different slots:");
        Log.INFO(" ===> /data/0, /data/1, /data/2, /data/3");
        return replaceDiskWithSimBlankDisk();
    }
    
    // Expected Fail 6605537
    //11/02/07 17:39:02 SUM: PASS RESULT_ID=1117409 RUNTIME=01:23:11
    public boolean test08_DiskReplacementFactoryBlankDisk() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Replaces a disabled disk with a factory blank disk");
        Log.INFO(" ===> Validate that new disk is properly formatted by ");
        Log.INFO(" ===> disk_replacement code, newfs'd, mounted, and is displayed");
        Log.INFO(" ===> as available on the CLI.");
        Log.INFO(" ===> Run this test against four disks from different slots:");
        Log.INFO(" ===> /data/0, /data/1, /data/2, /data/3");
        return replaceDiskWithFactoryBlankDisk();
    }
    
    
    // 11/27/07 16:14:09 SUM: PASS RESULT_ID=1144863
    public boolean test09_DiskReplacementBadDisk() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Replaces a disabled disk with a known bad disk");
        Log.INFO(" ===> Validate that disk remains disabled ");
        Log.INFO(" ===> Run this test against four disks from different slots:");
        Log.INFO(" ===> /data/0, /data/1, /data/2, /data/3");
        return replaceDiskWithBadDisk();
    }
    
    // Expected Fail 6614197 and 6604577
    public boolean test10_DiskReplacementSwapDisksInNode() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Swap 2 disks within a node");
        Log.INFO(" ===> Validate that all disks come back online");
        return swapDisksInNode();
    }
    
    // Expected Fail 6614197 and 6604577
    public boolean test11_DiskReplacementSwapDisksAcrossNodes() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Swap 2 disks across 2 different nodes");
        Log.INFO(" ===> Validate that all disks come back online");
        return swapDisksAcrossNodes();
    }
    
    // Expected Fail 6605537
    public boolean test12_DisabledDiskLed() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Tests that the disabled disk led's work properly");
        Log.INFO(" ===> Greps logs for led enabled/disabled messages...");
        Log.INFO(" ===> but does not check hardware.");
        return ledTests();
    }
    
    // Untested
    public boolean test13_RebootAfterDiskReplacement() throws Throwable {
        if (excludeCase() || !okToProceed || !isSetup) return false;
        Log.INFO(" ===> Tests that a disk \"hotswap\" works properly");
        Log.INFO(" ===> Replaces a disabled disk with a simulated blank disk");
        Log.INFO(" ===> Validate that new disk is properly formatted by ");
        Log.INFO(" ===> disk_replacement code, newfs'd, mounted, and is displayed");
        Log.INFO(" ===> as available on the CLI.");
        Log.INFO(" ===> Then do a reboot to make sure changes persist");
        Log.INFO(" ===> Run this test against four disks from different slots:");
        Log.INFO(" ===> /data/0, /data/1, /data/2, /data/3");
        return disableDisksReplaceReboot();
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    /////////////////////////////// MAIN HELPERS //////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    protected boolean rebootAndVerify() throws Throwable {
        // verify all disks online
        // reboot
        // verify all disks online
        return (waitForClusterToStart() &&
                rebootAndWait("disks"));
    }
    
    protected boolean disableDisksAndVerify() throws Throwable {
        // verify all disks online
        // get random disks in each of the DISKS_PER_NODE spots
        // disable disks
        // verify disks offline
        // enable disks
        // verify all disks online
        
        return (waitForClusterToStart() &&
                chooseRandomDisks() &&
                disableDisks() &&
                verifySysstatV() &&
                enableDisks() &&
                waitForClusterToStart());
    }
    
    protected boolean disableDisksRebootAndVerify() throws Throwable {
        // verify all disks online
        // get random disks in each of the DISKS_PER_NODE spots
        // disable disks
        // verify disks offline
        // reboot and wait
        // verify disks offline
        // enable disks
        // verify all disks online
        return (waitForClusterToStart() &&
                chooseRandomDisks() &&
                disableDisks() &&
                verifySysstatV() &&
                rebootAndWait("disks") &&
                verifySysstatV() &&
                enableDisks() &&
                waitForClusterToStart());
    }
    
    protected boolean disableDisksReplaceReboot() throws Throwable {
        // verify all disks online
        // choose Random Disks
        // disable disks
        // send email and wait for flag
        // enable disks
        // verify all disks online
        // reboot and verify all online
        return (waitForClusterToStart() &&
                chooseRandomDisks() &&
                disableDisks() &&
                generateEmail("factory") &&
                waitForTaskdoneFlag(subject, message) &&
                enableDisks() &&
                waitForClusterToStart() &&
                rebootAndVerify());
    }
    
    protected boolean startupWithDisksDown() throws Throwable {
        boolean ok1 = startupWithDisksDown(CASE1);
        if (ok1)
            Log.INFO("CASE1 passed!");
        else
            Log.ERROR("CASE1 failed"); 
        boolean ok2 = startupWithDisksDown(CASE2);
        if (ok2)
            Log.INFO("CASE2 passed!");
        else
            Log.ERROR("CASE2 failed");
        boolean ok3 = startupWithDisksDown(CASE3);
        if (ok3)
            Log.INFO("CASE3 passed!");
        else
            Log.ERROR("CASE3 failed");
        boolean ok4 = startupWithDisksDown(CASE4);
        if (ok4)
            Log.INFO("CASE4 passed!");
        else
            Log.ERROR("CASE4 failed");
        return ok1 && ok2 && ok3 && ok4;
    }
    protected boolean startupWithDisksDown(String[] whichDisks) throws Throwable {
        // verify all disks online
        // get random disks to disable
        // add nohoneycomb/noreboot flags
        // reboot cluster
        // generate email message
        // send email and wait for flag.
        // remove no* flags
        // start up cluster
        // wait for cluster to start
        // verify sysstat
        // enable disks
        // verify all disks online
        return (waitForClusterToStart() &&
                chooseRandomDisks(whichDisks) &&
                powerDisabledDiskNode("off") &&
                generateEmail("remove") &&
                waitForTaskdoneFlag(subject, message) &&
                powerDisabledDiskNode("on") &&
                fixNodesOnlineString() &&
                waitForClusterToStart() &&
                verifySysstatV() &&
                powerDisabledDiskNode("off") &&
                generateEmail("insert") &&
                waitForTaskdoneFlag(subject, message) &&
                powerDisabledDiskNode("on") &&
                removeDisksFromDisabledDiskArray() &&
                waitForClusterToStart());
    }
    
    protected boolean startupWithDisksDisabled() throws Throwable {
        boolean ok1 = startupWithDisksDisabled(CASE1);
        if (ok1)
            Log.INFO("CASE1 passed!");
        else
            Log.ERROR("CASE1 failed"); 
        boolean ok2 = startupWithDisksDisabled(CASE2);
        if (ok2)
            Log.INFO("CASE2 passed!");
        else
            Log.ERROR("CASE2 failed");
        boolean ok3 = startupWithDisksDisabled(CASE3);
        if (ok3)
            Log.INFO("CASE3 passed!");
        else
            Log.ERROR("CASE3 failed");
        boolean ok4 = startupWithDisksDisabled(CASE4);
        if (ok4)
            Log.INFO("CASE4 passed!");
        else
            Log.ERROR("CASE4 failed");
        return ok1 && ok2 && ok3 && ok4;
    }
    protected boolean startupWithDisksDisabled(String[] whichDisks) throws Throwable {
        // verify all disks online
        // get random disks to disable
        // disable disks
    	// power down nodes
    	// power up nodes
    	// wait for cluster to start
        // verify sysstat
        // enable disks
        // verify all disks online
        return (waitForClusterToStart() &&
                chooseRandomDisks(whichDisks) &&
                disableDisks() &&
                verifySysstatV() &&
                powerDisabledDiskNode("off") &&
                pause(SLEEP_NODE_DISABLE) &&
                powerDisabledDiskNode("on") &&
                waitForClusterToStart() &&
                verifySysstatV() &&
                enableDisks() &&
                waitForClusterToStart());
    }
    
    protected boolean startupWithBadDisks() throws Throwable {
        boolean ok1 = startupWithBadDisks(CASE1);
        if (ok1)
            Log.INFO("CASE1 passed!");
        else
            Log.ERROR("CASE1 failed"); 
        boolean ok2 = startupWithBadDisks(CASE2);
        if (ok2)
            Log.INFO("CASE2 passed!");
        else
            Log.ERROR("CASE2 failed");
        boolean ok3 = startupWithBadDisks(CASE3);
        if (ok3)
            Log.INFO("CASE3 passed!");
        else
            Log.ERROR("CASE3 failed");
        boolean ok4 = startupWithBadDisks(CASE4);
        if (ok4)
            Log.INFO("CASE4 passed!");
        else
            Log.ERROR("CASE4 failed");
        
        return ok1 && ok2 && ok3 && ok4;
    }
    protected boolean startupWithBadDisks(String[] whichDisks) throws Throwable {
        // verify all disks online
        // get random disks to disable
        // power off node
        // generate email message
        // send email and wait for flag.
        // power on node
        // wait for cluster to start
        // verify sysstat
        // enable disks with a power off - email - power on
        // verify all disks online
        return (waitForClusterToStart() &&
                chooseRandomDisks(whichDisks) &&
                powerDisabledDiskNode("off") &&
                generateEmail("damaged") &&
                waitForTaskdoneFlag(subject, message) &&
                powerDisabledDiskNode("on") &&
                fixNodesOnlineString() &&
                waitForClusterToStart() &&
                verifySysstatV() &&
                powerDisabledDiskNode("off") &&
                generateEmail("fixDamaged") &&
                waitForTaskdoneFlag(subject, message) &&
                powerDisabledDiskNode("on") &&
                removeDisksFromDisabledDiskArray() &&
                waitForClusterToStart());
    }
    
    protected boolean replaceDiskWithSimBlankDisk() throws Throwable {
        // verify all disks online
        // choose Random Disks
        // disable disks
        // send email and wait for flag
        // enable disks
        // verify all disks online
        // do stores, verify some landed on new disk
        return (waitForClusterToStart() &&
                chooseRandomDisks() &&
                disableDisks() &&
                generateEmail("factory") &&
                waitForTaskdoneFlag(subject, message) &&
                enableDisks() &&
                waitForClusterToStart());
    }
    
    protected boolean replaceDiskWithFactoryBlankDisk() throws Throwable {
        // verify all disks online
        // choose Random Disks
        // disable disks
        // send email and wait for flag
        // enable disks
        // verify all disks online
        // do stores, verify some landed on new disk
        return (waitForClusterToStart() &&
                chooseRandomDisks() &&
                disableDisks() &&
                generateEmail("factory") &&
                waitForTaskdoneFlag(subject, message) &&
                enableDisks() &&
                waitForClusterToStart());
    }
    
    protected boolean replaceDiskWithBadDisk() throws Throwable {
        // verify all disks online
        // get random disks to disable
        // disable them
        // generate email message
        // send email and wait for flag.
        // wait for cluster to start
        // verify sysstat
        // enable disks with a power off - email - power on
        // verify all disks online
        return (waitForClusterToStart() &&
                chooseRandomDisks() &&
                disableDisks() &&
                generateEmail("damaged") &&
                waitForTaskdoneFlag(subject, message) &&
                !enableDisks() &&
                waitForClusterToStart() &&
                verifySysstatV() &&
                generateEmail("fixDamaged") &&
                waitForTaskdoneFlag(subject, message) &&
                enableDisks() &&
                waitForClusterToStart());
    }
    
    protected boolean swapDisksInNode() throws HoneycombTestException, Throwable {
        // verify all disks online
        // get random disks to disable
        // disable them
        // generate email
        // send email and wait for flag
        // enable disks
        // verify all disks online
        return (waitForClusterToStart() &&
                chooseRandomDisksOneNode(2) &&
                disableDisks() &&
                changeDiskIndicesAndEnable(false) &&
                enableDisks() &&
                waitForClusterToStart());
    }
    
    protected boolean swapDisksAcrossNodes() throws Throwable {
        // verify all disks online
        // get random disks to disable
        // disable them
        // generate email
        // send email and wait for flag
        // enable disks
        // verify all disks online
        return (waitForClusterToStart() &&
                chooseRandomDisksAcrossNodes(2) &&
                disableDisks() &&
                changeDiskIndicesAndEnable(false) &&
                enableDisks() &&
                waitForClusterToStart());
    }
    
    protected boolean ledTests() throws Throwable {
        // follows test cases 1-10 in the Disabled disk led section of
        // http://hc-web.sfbay.sun.com/svn/docs/Test/Divisadero/DiskServices_TestPlan.html
        return (rollLogs() &&
                waitForClusterToStart() &&
                chooseRandomDisks() &&
                disableAndVerifyLeds() &&
                rebootAndWait("disks") &&
                verifyLeds("on") &&
                enableAndVerifyLeds() && 
                rebootAndWait("disks") &&
                verifyLeds("none") &&
                chooseRandomDisks(CASE5) &&
                disableAndVerifyLeds() &&
                enableAndVerifyLeds() &&
                chooseRandomDisksOneNode(1) && 
                disableReplaceWithBadVerifyLeds() &&
                chooseRandomDisksAcrossNodes(1) &&
                disableDisks() &&
                generateEmail("immediatePush") &&
                waitTaskdoneStartVerifyLeds() &&
                enableDisks() &&
                chooseRandomDisksAcrossNodes(1) &&
                disableDisks() &&
                generateEmail("delayedPush") &&
                waitTaskdoneStartVerifyLeds() &&
                rebootAndWait("disks") && // Because of known bug: 6605537
                enableDisks());
    }
    protected boolean waitTaskdoneStartVerifyLeds() throws Throwable {
        return (waitForTaskdoneFlag(subject, message) &&
                waitForClusterToStart() &&
                verifyLeds("on"));
    }
    protected boolean disableAndVerifyLeds() throws Throwable {
        return (disableDisks() &&
                verifySysstatV() &&
                verifyLeds("on"));
    }
    protected boolean enableAndVerifyLeds() throws Throwable {
        return (enableDisks() &&
                waitForClusterToStart() &&
                verifySysstatV() &&
                verifyLeds("off"));
    }
    protected boolean disableReplaceWithBadVerifyLeds() throws Throwable {
        return (powerDisabledDiskNode("off") &&
                generateEmail("damaged") &&
                waitForTaskdoneFlag(subject, message) &&
                powerDisabledDiskNode("on") &&
                fixNodesOnlineString() &&
                waitForClusterToStart() &&
                verifyLeds("on") &&
                !enableDisks() &&
                verifyLeds("none") &&
                powerDisabledDiskNode("off") &&
                generateEmail("fixDamaged") &&
                waitForTaskdoneFlag(subject, message) &&
                powerDisabledDiskNode("on") &&
                removeDisksFromDisabledDiskArray() &&
                waitForClusterToStart() &&
                verifySysstatV() &&
                verifyLeds("off"));
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////// HELPERS /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    protected boolean changeDiskIndicesAndEnable(boolean expected) throws Throwable {
        Object[] dd = disabledDisks.toArray();
        String disk1 = (String) dd[0];
        String disk2 = (String) dd[1];
        Pattern pattern = 
            Pattern.compile("DISK-\\d+:(\\d+)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(disk1);
        String index1 = matcher.group(1);
        matcher = pattern.matcher(disk2);
        String index2 = matcher.group(1); 
        String line;
        
        boolean ok1 = false;
        boolean ok2 = false;
        // Change disk indices for the 2 disabled disks 
        cli.runCommand(
            "disklabel -F -f " + disk1 + " -d " + index2);
        while ((line = stdout.readLine()) != null) {
            Log.INFO(line);
            if (line.contains("disk Index: " + index2)) {
                Log.INFO(" - Correct Index Found: disk Index: " + index2);
                ok1 = true;
            }
        }
        
        cli.runCommand(
                "disklabel -F -f " + disk2 + " -d " + index1);
        while ((line = stdout.readLine()) != null) {
            Log.INFO(line);
            if (line.contains("disk Index: " + index1)) {
                Log.INFO(" - Correct Index Found: disk Index: " + index1);
                ok2 = true;
            }
        }
        boolean okToProceed = ok1 && ok2;
        
        if (okToProceed) {
            // Enable the disk, look for expected
            okToProceed = enableDisks() && expected;
        }               
        
        if (okToProceed) {
            // change one disk index back and wipe disk
            ok1 = false;
            ok2 = false;
            cli.runCommand(
                    "disklabel -F -f " + disk1 + " -d " + index1);
            while ((line = stdout.readLine()) != null) {
                Log.INFO(line);
                if (line.contains("disk Index: " + index1)) {
                    Log.INFO(" - Correct Index Found: disk Index: " + index1);
                    ok1 = true;
                }
            }
            if (ok1) {
                cli.runCommand("hwcfg -F -W " + disk1);
            }
            
            // change other disk index back and wipe disk
            cli.runCommand(
                    "disklabel -F -f " + disk2 + " -d " + index2);
            while ((line = stdout.readLine()) != null) {
                Log.INFO(line);
                if (line.contains("disk Index: " + index2)) {
                    Log.INFO(" - Correct Index Found: disk Index: " + index2);
                    ok2 = true;
                }
            }
            if (ok2) {
                cli.runCommand("hwcfg -F -W " + disk2);
            }
            okToProceed = ok1 && ok2;
        }           
        return okToProceed;
    }
    
    
    protected boolean verifyLeds(String key) throws Throwable {
        boolean ok = true;
        boolean isAny = false;
        Log.INFO("Grepping logs for led messages");
        
        if (key == "none")
            Log.INFO("Expecting to see an exit value of 1... no lines found");
        
        Object[] dd = disabledDisks.toArray();
        // To know if/what disks were enabled
        if (dd.length == 0)
            dd = enabledDisks.toArray();
        
        for (int i = 0; i < dd.length; i++) {
            String diskString = (String) dd[i];
            Pattern pattern = 
                Pattern.compile("DISK-(\\d+):", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(diskString);
            if (matcher.find()) {
                int port = Integer.parseInt(matcher.group(1))
                           - NODE_NUM_OFFSET + NODE_PORT_OFFSET;
                String cmd = SSH + " -p " + port + " root@" + adminVIP + 
                    " \"grep ledCtl " + LOG_LOCATION + " | grep '"
                    + diskString;
                if (key == "none")
                    cmd += " on' \"";
                else
                    cmd += " " + key + "'\"";
                
                runSystemCommand(cmd);
                String line;
                int count = 0;
                while ((line = stdout.readLine()) != null) {
                    count++;
                    isAny = true;
                    Log.INFO(" ---> " + line);
                }
                if (count == 0)
                    ok = false;
            }
        }
        rollLogs();
        if (key == "none") {
            return ((isAny) ? false : true);
        }
        
        if (ok && isAny) {
            Log.INFO("LEDs ok");
            return true;
        }
        else {
            Log.ERROR("LED's not ok");
            return false;
        }
    }
    
    protected boolean powerDisabledDiskNode(String action) throws Throwable {
        boolean ok;
        int node = activeNode;
        // get a node different than the node we are powering
        while (node == activeNode) {
            int r = ((int)(Math.random() * nodes));
            node = r + NODE_NUM_OFFSET;
        }
        Log.INFO("Attempting to power node " + activeNode + " " + action);
        int sshPort = node - NODE_NUM_OFFSET + NODE_PORT_OFFSET;
        runSystemCommand(SSH + " -p " + sshPort + " root@" + adminVIP +
            "  \"echo honeycomb > /opt/honeycomb/share/ipmi-pass\"");
        ok = runSystemCommand(SSH + " -p " + sshPort + " root@" + adminVIP +
            " \"ipmitool -I lan -f /opt/honeycomb/share/ipmi-pass -U Admin -H"+
            " hcb" + activeNode + "-sp chassis power " + action + "\"");
        if (ok)
            Log.INFO("Successfully powered node " + activeNode + " " + action);
        else
            Log.ERROR("Could not power node " + activeNode + " " + action);
        return ok;
    }
     
    protected boolean waitForTaskdoneFlag(String sub, String mes) {
        try {
            boolean ok;
            ok = sendmail(mailServer, email, "DiskServicesSuite", sub, mes);
            if (!ok) {
                Log.ERROR("Email could not be sent... failing test");
                recover();
                return false;
            }
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.ERROR("Email could not be sent... failing test");
            recover();
            return false;
        }
        
        int i = MAX_TASKDONE_ITERATIONS;
        while (i > 0 && !checkForTaskdoneFlag()) {
            i--;
            System.out.print(".");
            if (checkForSkiptestFlag()) {
                recover();
                return false;
            }
            sleep(SLEEP_TASKDONE);
        }
        if (i == 0) {
            // ran out of iterations;
            Log.ERROR("Maximum waittime reached: " + 
                    formatTime(MAX_TASKDONE_ITERATIONS * SLEEP_TASKDONE));
            recover();
            return false;
        }
        return true;
        
    }
    
    protected boolean checkForTaskdoneFlag() {
        File f = new File(taskdoneLocation + "taskdone");
        boolean e =  f.exists();
        if (e) {
            if (f.delete()) {
                System.out.print(NEWLINE);
                Log.INFO("Taskdone flag found and deleted... OK to proceed");
            } else {
                Log.ERROR("Problem deleting Taskdone flag");
                return false;
            }
        }
        return e;
    }
    
    protected boolean checkForSkiptestFlag() {
        File f = new File(taskdoneLocation + "skiptest");
        boolean e =  f.exists();
        if (e) {
            if (f.delete()) {
                System.out.print(NEWLINE);
                Log.INFO("Skiptest flag found and deleted... Skipping test");
            } else {
                Log.ERROR("Problem deleting Skiptest flag");
                return false;
            }
        }
        return e;
    }
    
    protected boolean verifySysstatV() throws Throwable {
        Log.INFO("Verifying correct amount off disks are online");
        stdout = cli.runCommand("sysstat -v");
        String line;
        String sysstatV = "";
        int offlineCount = 0;
        while ((line = stdout.readLine()) != null) {
            Log.INFO(" ---> " + line);
            sysstatV += (line + NEWLINE);
            if (line.contains("OFFLINE"))
                offlineCount++;
        }
        
        boolean ok1 = (offlineCount == disabledDisks.size());
        if (ok1)
            Log.INFO("Correct amount of OFFLINE disks detected");
        else
            Log.ERROR("Incorrect amount of OFFLINE disks detected");
        
        boolean ok2 = true;
        Object[] dd = disabledDisks.toArray();
        for (int i = 0; i < dd.length; i++) {
            String disk = (String) dd[i];
            String notfound = "Unable to detect " + disk + " as disabled";
            String found = disk + " detected as disabled";
            String pat = (disk + "\\s+\\[OFFLINE\\]");
            if (!matchPattern(pat, sysstatV, found, notfound))
                ok2 = false;
        }
        return ok1 && ok2;
    }
    protected boolean verifySysstatVBadDisks() throws Throwable {
        Log.INFO("Verifying correct amount off disks are online");
        stdout = cli.runCommand("sysstat -v");
        String line;
        String sysstatV = "";
        int offlineCount = 0;
        while ((line = stdout.readLine()) != null) {
            Log.INFO(" ---> " + line);
            sysstatV += (line + NEWLINE);
            if (line.contains("OFFLINE"))
                offlineCount++;
        }
        
        boolean ok1 = (offlineCount == disabledDisks.size());
        if (ok1)
            Log.INFO("Correct amount of OFFLINE disks detected");
        else
            Log.ERROR("Incorrect amount of OFFLINE disks detected");
        
        boolean ok2 = true;
        Object[] dd = disabledDisks.toArray();
        for (int i = 0; i < dd.length; i++) {
            String disk = (String) dd[i];
            String notfound = "Unable to detect " + disk + " as disabled";
            String found = disk + " detected as disabled";
            Pattern nodePat =Pattern.compile("(DISK-\\d+:)",Pattern.MULTILINE);
            Matcher matcher = nodePat.matcher(disk);
            if (matcher.find())
                disk = matcher.group(1) + ":-1";
            String pat = disk + "\\s+\\[OFFLINE\\]"; 
            if (!matchPattern(pat, sysstatV, found, notfound))
                ok2 = false;
        }
        return ok1 && ok2;
    }
    
    protected boolean chooseRandomDisks() {
        return chooseRandomDisks(DISKS_PER_NODE);
    }
    
    protected boolean chooseRandomDisks(int howMany) {
        Log.INFO("Choosing random disks");
        boolean ok = true;
        for (int i = 0; i < howMany; i++) {
            int r = ((int)(Math.random() * nodes));
            int node = r + NODE_NUM_OFFSET;
            boolean added = disabledDisks.add("DISK-" + node + ":" + i);
            if (added)
                Log.INFO("Set DISK-" + node + ":" + i + " to be disabled");
            else {
                Log.ERROR(
                    "Problem adding DISK-" + node + ":" + i + " to be disabled");
                ok = false;
            }
        }
        return ok;
    }
    
    protected boolean chooseRandomDisksOneNode(int howMany) {
        Log.INFO("Choosing random disks");
        boolean ok = true;
        int r = ((int)(Math.random() * nodes));
        int node = r + NODE_NUM_OFFSET;
        activeNode = node;
        for (int i = 0; i < howMany; i++) {
            do {
                r = ((int)(Math.random() * DISKS_PER_NODE));
            } while (disabledDisks.contains("DISK-"+node+":"+r));
            boolean added = disabledDisks.add("DISK-" + node + ":" + r);
            if (added)
                Log.INFO("Set DISK-" + node + ":" + r + " to be disabled");
            else {
                Log.ERROR(
                    "Problem adding DISK-" + node + ":" + r + " to be disabled");
                ok = false;
            }
        }
        return ok;
    }
    
    protected boolean chooseRandomDisksAcrossNodes(int howMany) {
        Log.INFO("Choosing random disks");
        boolean ok = true;
        for (int i = 0; i < howMany; i++) {
            int r = ((int)(Math.random() * nodes));
            int node = r + NODE_NUM_OFFSET;
            int d = ((int)(Math.random() * DISKS_PER_NODE));
            boolean added = disabledDisks.add("DISK-" + node + ":" + d);
            if (added)
                Log.INFO("Set DISK-" + node + ":" + d + " to be disabled");
            else {
                Log.ERROR(
                    "Problem adding DISK-" + node + ":" + d + " to be disabled");
                ok = false;
            }
        }
        return ok;
    }
    protected boolean chooseRandomDisks(String[] whichDisks) {
        boolean ok = true;
        int r = ((int)(Math.random() * nodes));
        int node = r + NODE_NUM_OFFSET;
        activeNode = node;
        for (int i = 0; i < whichDisks.length; i++) {
            boolean added =
                disabledDisks.add("DISK-" + node + ":" + whichDisks[i]);
            if (added)
                Log.INFO("Set DISK-" + node + ":" + i + " to be disabled");
            else {
                Log.ERROR(
                    "Problem adding DISK-" + node + ":" + i + " to be disabled");
                ok = false;
            }
        }
        return ok;
    }
   
    
    protected boolean disableDisks() throws Throwable {
        boolean ok = true;
        Object[] dd = disabledDisks.toArray();
        for (int i = 0; i < dd.length; i++) {
            String toDisable = (String) dd[i];
            boolean disabled = disableDisk(toDisable);
            if (!disabled) 
                ok = false;
        }
        enabledDisks = new ArrayList();
        return ok;
    }
    
    protected boolean disableDisk(String who) throws Throwable {
        Log.INFO("Disabling " + who);
        stdout = cli.runCommand("hwcfg -F -c " + masterCell + " -D " + who);
        String line;
        while ((line = stdout.readLine()) != null) {
            Log.INFO(line);
            if (line.contains("Successfully disabled " + who)) {
                disks--;
                nodesOnlineString = nodes + " nodes online, " + 
                    disks + " disks online";
                pause(SLEEP_DISK_DISABLE);
                return true;
            }
        }
        Log.ERROR("Unable to disable " + who);
        return false;
    }
    
    protected boolean enableDisks() throws Throwable {
        boolean ok = true;
        Object[] dd = disabledDisks.toArray();
        for (int i = 0; i < dd.length; i++) {
            String toDisable = (String) dd[i];
            boolean enabled = enableDisk(toDisable);
            if (!enabled) 
                ok = false;
        }
        return ok;
    }
    
    protected boolean enableDisk(String who) throws Throwable {
        Log.INFO("Enabling " + who);
        stdout = cli.runCommand("hwcfg -F -c " + masterCell + " -E " + who);
        String line;
        while ((line = stdout.readLine()) != null) {
            Log.INFO(line);
            if (line.contains("Successfully enabled " + who)) {
                disabledDisks.remove(who);
                enabledDisks.add(who);
                disks++;
                nodesOnlineString = nodes + " nodes online, " + 
                    disks + " disks online";
                pause(SLEEP_DISK_ENABLE);
                return true;
            }
        }
        Log.ERROR("Unable to enable " + who);
        return false;
    }
    
    protected boolean removeDisksFromDisabledDiskArray() {
        enabledDisks = new ArrayList();
        Object[] dd = disabledDisks.toArray();
        for (int i = 0; i < dd.length; i++) {
            disabledDisks.remove(dd[i]);
            enabledDisks.add(dd[i]);
            disks++;
        }
        nodesOnlineString = nodes + " nodes online, " + 
        disks + " disks online";
        return true;
    }
    
    protected boolean fixNodesOnlineString() {
        Object[] dd = disabledDisks.toArray();
        for (int i = 0; i < dd.length; i++) {
            disks--;
        }
        nodesOnlineString = nodes + " nodes online, " + 
        disks + " disks online";
        return true;
    }
    
    
    protected boolean reboot(String who) throws Throwable {
        Log.INFO("Rebooting cluster...");
        String cmd = "reboot -c " + masterCell + " -F";
        if (who.equalsIgnoreCase("all"))
            cmd += " -A";
        stdout = cli.runCommand(cmd);
        String line;
        pause(SLEEP_REBOOT_CLUSTER);
        return true; //exception will never get here
    }
    protected boolean rebootAndWait(String who) throws Throwable {
        reboot(who);
        return waitForClusterToStart();
    }
    
    protected boolean waitForClusterToStart() throws HoneycombTestException {
        // Wait for cli to be accessible and sysstat returns Online

        Log.INFO("Waiting for nodes/disks to come online...");
        Log.INFO("Grepping sysstat for \"" + nodesOnlineString + "\"");
        boolean ready = false;
        int i = MAX_ONLINE_ITERATIONS;
        while (i > 0 && !ready) {
            try {
                i--;
                ArrayList lines = readSysstat();
                if (lines.toString().contains(nodesOnlineString)) {
                    Log.INFO(" ---> " + nodesOnlineString);
                    ready = true;
                }    
                if (!ready)
                    pause(SLEEP_WAKEUP_TIMEOUT);
            } catch (Throwable e) {
                pause(SLEEP_WAKEUP_TIMEOUT);
            }
        }
        if (i == 0) {
            Log.ERROR("Cluster is not Online");
        }
        if (!ready) {
            okToProceed = false;
            Log.ERROR("Not ok to proceed");
        }
        return ready;
    }
    protected void recover() {
        Log.INFO("[RECOVERY] Trying to recover cluster...");
        // Enable disabled disks
        try {
            boolean ok = enableDisks();
            if (!ok)
                Log.ERROR("[RECOVERY] Disks could not be enabled..." +
                        " please do so manually");
            else
                Log.INFO("[RECOVERY] Disks enabled successfully");
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.ERROR("[RECOVERY] Disks could not be enabled... "+
                    "please do so manually");
        }
        
    }
    
    protected boolean sendTestMail() {
        return generateEmail("test") && waitForTaskdoneFlag(subject, message);
    }
    ///////////////////////////////////////////////////////////////////////////
    //////////////////////////////// UTILITIES ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    protected boolean generateEmail(String key) {
        subject = "The DiskServicesSuite test is waiting for you to " +
          "perform an action";
        message = "The DiskServiceSuite test is currently stalled and " +
          "waiting for you to perform the following action(s):" + NEWLINE;
        message += NEWLINE;
        if (key.equalsIgnoreCase("remove")) {
            Object[] dd = disabledDisks.toArray();
            for (int i = 0; i < dd.length; i++) {
                message += " ===> Remove " + dd[i] + 
                   " from " + cluster + NEWLINE;
            }
        }
        if (key.equalsIgnoreCase("insert")) {
            Object[] dd = disabledDisks.toArray();
            for (int i = 0; i < dd.length; i++) {
                message += " ===> Insert " + dd[i] + 
                  " into " + cluster + NEWLINE;
            }
        }
        if (key.equalsIgnoreCase("damaged")) {
            Object[] dd = disabledDisks.toArray();
            for (int i = 0; i < dd.length; i++) {
                message += " ===> Replace " + dd[i] + 
               " from " + cluster + " with a known bad disk" + NEWLINE;
            }
        }
        if (key.equalsIgnoreCase("fixDamaged")) {
            Object[] dd = disabledDisks.toArray();
            for (int i = 0; i < dd.length; i++) {
                message += " ===> Replace the known bad disk " + dd[i] + 
               " from " + cluster + " with the original (or a good) disk" + NEWLINE;
            }
        }
        if (key.equalsIgnoreCase("simBlank")) {
            Object[] dd = disabledDisks.toArray();
            for (int i = 0; i < dd.length; i++) {
                message += " ===> Replace the good disk " + dd[i] + 
               " from " + cluster + " with a simulated blank disk" + NEWLINE;
            }
            message += NEWLINE + "A simulated blank disk has 0\'s written ";
            message += "over the first 1MB." + NEWLINE;
        }
        if (key.equalsIgnoreCase("factory")) {
            Object[] dd = disabledDisks.toArray();
            for (int i = 0; i < dd.length; i++) {
                message += " ===> Replace the good disk " + dd[i] + 
               " from " + cluster + " with a factory blank disk" + NEWLINE;
            }
        }
        if (key.equalsIgnoreCase("test")) {
            Object[] dd = disabledDisks.toArray();
            message += " ===> Verify receipt of this email by adding the flag: ";
            message += taskdoneLocation + "taskdone"+ NEWLINE;
        }
        if (key.equalsIgnoreCase("immediatePush")) {
            Object[] dd = disabledDisks.toArray();
            for (int i = 0; i < dd.length; i++) {
                message += " ===> Pull out " + dd[i] + " from " + cluster +
                    " and immediately insert the same disk."+ NEWLINE;
            }
        }
        if (key.equalsIgnoreCase("delayedPush")) {
            Object[] dd = disabledDisks.toArray();
            for (int i = 0; i < dd.length; i++) {
                message += " ===> Pull out " + dd[i] + " from " + cluster +
                    " and after 5 minutes, insert the same disk."+ NEWLINE;
            }
        }
    
        message += NEWLINE;
        message += "The disks are numbered from left-to-right as you are facing the front ";
        message += "of the node, starting with 0. For example, DISK-XXX:2 will ";
        message += "be the 3rd from the left.";
        message += NEWLINE + NEWLINE;
        message += "After you have done the above tasks, please add a ";
        message += "special flag to let the test know to proceed. ";        
        message += "This flag can generated by creating the following file on the client:" + NEWLINE;
        message += "  " + taskdoneLocation + "taskdone" + NEWLINE;
        message += "If using a unix system, you can do this by using the command:" + NEWLINE;
        message += "  ssh [YOUR_CLIENT] touch " + taskdoneLocation + "taskdone" + NEWLINE;
        message += "The test will remove the flag once it is found and will ";
        message += "continue with the test. Please make sure you have performed the action ";
        message += "on the correct disk BEFORE creating the 'taskdone' flag.";
        message += NEWLINE + NEWLINE;
        message += "If you wish for the test to be omitted at this point, ";
        message += "please create the following file on the client:" + NEWLINE;
        message += "  " + taskdoneLocation + "skiptest" + NEWLINE;
        message += "If using a unix system, you can do this by using the command:" + NEWLINE;
        message += "  ssh [YOUR_CLIENT] touch " + taskdoneLocation + "skiptest" + NEWLINE;
        message += "*NOTE: this will skip the test and attempt to recover the cluster, but ";
        message += "will also fail the individual test. ";
        message += NEWLINE + NEWLINE;
        message += "The test will check for the file every "
            + formatTime(SLEEP_TASKDONE) + ", ";
        message += "and check a maximum of " + MAX_TASKDONE_ITERATIONS + " times, ";
        message += "giving a total wait time of " + 
        formatTime(MAX_TASKDONE_ITERATIONS * SLEEP_TASKDONE) + NEWLINE;
        message += "After that time, if one of the files is not found, ";
        message += "the test will assume something went wrong and fail.";
        return true;
    }
        
    protected boolean rollLogs() {
        boolean ok = true;
        Log.INFO("Rolling logs for easier greping");
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
    
    protected boolean startHoneycomb() throws Throwable {
        boolean ret = true;
        for (int i = 0; i < nodes; i++) {
            int port = i + NODE_PORT_OFFSET;
            boolean ok;
            ok = runSystemCommand(SSH + " -p " + port + " root@" + adminVIP +
                    " /opt/honeycomb/etc/init.d/honeycomb start");
            if (!ok)
                ret = false;
        }
        return ret;
    }
    protected boolean verifyFlags(boolean flagsExpected) throws Throwable {
        boolean ok1, ok2;
        boolean ok = true;
        if (flagsExpected) {
            Log.INFO("Adding nohoneycomb/noreboot flags...");
            for (int n = 0; n < nodes; n++) {
                int port = n + NODE_PORT_OFFSET;
                ok1 = runSystemCommand(SSH + " -p " + port + " root@" + 
                        adminVIP + " touch " + NOHONEYCOMB);
                ok2 = runSystemCommand(SSH + " -p " + port + " root@" + 
                        adminVIP + " touch " + NOREBOOT);
                if (!ok1 || !ok2)
                    ok = false;
            }
        } else {
            Log.INFO("Removing nohoneycomb/noreboot flags...");
            for (int n = 0; n < nodes; n++) {
                int port = n + NODE_PORT_OFFSET;
                ok1 = runSystemCommand(SSH + " -p " + port + " root@" + 
                        adminVIP + " rm -f " + NOHONEYCOMB);
                ok2 = runSystemCommand(SSH + " -p " + port + " root@" + 
                        adminVIP + " rm -f " + NOREBOOT);
                if (!ok1 || !ok2)
                    ok = false;
            }
        }
        
        return ok;
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
    
    protected boolean sendmail(String sendmailServer,
                               String to,
                               String from,
                               String subject,
                               String message) throws Throwable
    {
        
        String cmd = SSH_ROOT + sendmailServer;
        cmd += " \"echo 'TO: " + to + NEWLINE;
        cmd += "FROM: " + from + NEWLINE;
        cmd += "SUBJECT: " + subject + NEWLINE + NEWLINE;
        cmd += message;
        cmd += "' | sendmail -t\"";
        return runSystemCommand(cmd);
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
    
    protected ArrayList readSysstat() throws Throwable {
        ArrayList result = new ArrayList();
        String line = null;
        String cmd = "sysstat";
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
    
    protected boolean pause(long milli) {
        Log.INFO("Sleeping for " + formatTime(milli));
        try {
            Thread.sleep(milli);
        } catch (InterruptedException e) { return false; }
        return true;
    }
}
