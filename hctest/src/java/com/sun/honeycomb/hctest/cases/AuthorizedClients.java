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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.InetAddress;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
 
public class AuthorizedClients extends HoneycombLocalSuite {
    private Hashtable hash = new Hashtable();
    private int SLEEP_WAKEUP_TIMEOUT = 60000; // milliseconds
    private int SLEEP_REBOOT_CLUSTER = 900000; // milliseconds
    private int SLEEP_SWITCH_FAILOVER = 600000; //milliseconds
    private int SLEEP_SWITCH_FAILBACK = 600000; //milliseconds
    private int SLEEP_HIVECFG = 300000; // milliseconds
    private int SLEEP_NODE_DOWN = 600000; // milliseconds
    private int SLEEP_NODE_UP = 600000; // milliseconds
    private int SLEEP_IRULES = 600000; // milliseconds
    private int SLEEP_TEST_RECOVER = 900000; // milliseconds
    private int MAX_ONLINE_ITERATIONS = 45; // iterations
    private int MAX_IO_ITERATIONS = 5; // iterations
    private int MAX_FLAG_ITERATIONS = 10;
    protected int NODE_NUM_OFFSET = 101;
    protected String apcCluster = null;
    protected String apcIP = null;
    protected String NOHONEYCOMB = "/config/nohoneycomb";
    protected String NOREBOOT = "/config/noreboot";
    protected String bogusIP = "111.111.111.111";
    protected String bogusSubnet = "111.111.111.0/24";
    protected String sshSwitch = "ssh -p 2222 -l nopasswd 10.123.45.1";
    protected String[] sixIPs =
        {"1.1.1.1","2.2.2.2","3.3.3.3","4.4.4.4","5.5.5.5","6.6.6.6"};
    protected String[] sixSubnets =
        {"1.1.1.0/24","2.2.2.0/24","3.3.3.0/24",
         "4.4.4.0/24","5.5.5.0/24","6.6.6.0/24"};
    protected String delimiter = ",";
    protected String minorVersionCommand = 
        " | awk {'print $3'} | cut -d'/' -f1,1 | egrep -v "
      + "'definition|junk' | grep -v ' ' | cut -d'-' -f2,2 | "
      + "sort -n | tail -1";
    protected String preVersionCommand = 
        " | awk {'print $3'} | cut -d'/' -f1,1 | egrep -v "
      + "'definition\\|junk' | grep -v ' ' | grep ";
    protected String postVersionCommand = " | tail -1";
    protected String getNTPProperty =
        "grep 'honeycomb.cell.ntp' /config/config.properties";
    protected String spOK = 
        "Correct irules for service processor access found";
    protected String adOK = 
        "Correct irules for admin access found";
    protected String daOK = 
        "Correct irules for data access found";
    protected String spNO = 
        "ERR: Incorrect irules for service processor access";
    protected String adNO =
        "ERR: Incorrect irules for admin access";
    protected String daNO = 
        "ERR: Incorrect irules for data access";
    
    protected String CLUSTER = null;
    protected String clientHostname = null;
    protected String clientIP  = null;
    protected String adminVIP = null;
    protected String dataVIP = null;
    protected String spIP = null;
    protected String irules = null;
    protected CLI cli = null;
    protected ClusterMembership cm = null;
    protected ClusterNode masterNode = null;
    protected CheatNode cheatNode = null;
    protected BufferedReader STDOUT = null;
    protected boolean switch2 = false;
    protected boolean isNodeDown = false;
    protected int NODES = 0;
    protected int masterCell = 0;
    protected TestCase self;
    
    // regexps
    protected String allPattern = "accpt\\s+\\|\\s+ip\\s+0\\s+";

    public AuthorizedClients() {
        super();
    }
   
    /* 
     * Tests must be run on a cluster that is already online.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        self = createTestCase("AuthorizedClients","Automated authorized clients test.");
        self.addTag("authorizedClients");
        if (self.excludeCase()) // should I run?
            return;
        super.setUp();
        dataVIP = testBed.dataVIPaddr;
        adminVIP = testBed.adminVIPaddr;
        spIP = testBed.spIPaddr;
        
        String apc_switch_clusters = System.getProperty(
                                        HCLocale.PROPERTY_APC_SWITCH_CLUSTERS);
        if (apc_switch_clusters != null) {
            String fields[] = apc_switch_clusters.split(",");
            if (fields.length % 2 != 0) {
                throw new HoneycombTestException("System property " +
                                HCLocale.PROPERTY_APC_SWITCH_CLUSTERS +
                                " must be comma-delimited list of " +
                                " cluster1,apc1_ip,cluster2,apc2_ip,..");
            }
            for (int i=0; i<fields.length; i+=2) {
                hash.put(fields[i], fields[i+1]);
            }
        }
        
        CLUSTER = 
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        
        InetAddress addr = InetAddress.getLocalHost();
        clientIP = addr.getHostAddress();
        clientHostname = addr.getHostName();
        
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        initAll();
        NODES = cli.hwstat(masterCell).getNodes().size();
    }
    
    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        if (excludeCase()) return;
        changeAuthorizedClients("all");
        rebootAllAndWait();
        super.tearDown();
    }
    
    
    
    ///////////////////////////////////////////////////////////////////////////
    //////////////////////////////////  TESTS /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    // Works
    public boolean test1_HappyClusterAll() {
        if (excludeCase()) return false;
        Log.INFO("Changes auth clients to all");
        Log.INFO("Verifies correct hivecfg, irules and data access");
        try {
            return changeAuthRebootVerify("all", true);
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        }
    }

    // Needs more testing
    public boolean test2_UnhappyClusterAll() {
        if (excludeCase()) return false;
        Log.INFO("Changes auth clients to all");
        Log.INFO("Triggers master-failover, cluster reboot, node down");
        Log.INFO(" and a switch failover if cluster is connected to an apc");
        Log.INFO("Verifies correct hivecfg, irules and data access");
        try {
            return runUnhappyTests("all", true);
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        }
    }

    // Works
    public boolean test3_HappyClusterIP() {
        if (excludeCase()) return false;
        Log.INFO("Changes auth clients to the client ip");
        Log.INFO("Verifies correct hivecfg, irules and data access");
        try {
            boolean passed1 = changeAuthRebootVerify(clientIP, true);
            boolean passed2 = changeAuthRebootVerify(bogusIP, false);
            return passed1 && passed2;
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        }
    }
    
    // Works
    public boolean test4_UnhappyClusterIP() {

        if (excludeCase()) return false;

        Log.INFO("Changes auth clients to client ip");
        Log.INFO("Triggers master-failover, cluster reboot, node down");
        Log.INFO(" and a switch failover if cluster is connected to an apc");
        Log.INFO("Verifies correct hivecfg, irules and data access");
        try {
            boolean passed1 = runUnhappyTests(clientIP, true);
            boolean passed2 = runUnhappyTests(bogusIP, false);
            return passed1 && passed2;
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        }
    }
    
    // Works
    public boolean test5_HappyClusterSubnet() throws Throwable {
        if (excludeCase()) return false;
        Log.INFO("Changes auth clients to correct subnet");
        Log.INFO("Verifies correct hivecfg, irules and data access");
        String clientSubnet = System.getProperty(
                                        HCLocale.PROPERTY_CLIENT_SUBNET);
        if (clientSubnet == null) {
            throw new HoneycombTestException("System property not found: " +
                                        HCLocale.PROPERTY_CLIENT_SUBNET);
        }
        try {
            boolean passed1 = changeAuthRebootVerify(clientSubnet, true);
            boolean passed2 = changeAuthRebootVerify(bogusSubnet, false);
            return passed1 && passed2;
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        }
    }
   
    // Works
    public boolean test6_UnhappyClusterSubnet() throws Throwable {
        if (excludeCase()) return false;
        Log.INFO("Changes auth clients to incorrect subnet");
        Log.INFO("Triggers master-failover, cluster reboot, node down");
        Log.INFO(" and a switch failover if cluster is connected to an apc");
        Log.INFO("Verifies correct hivecfg, irules and data access");
        String clientSubnet = System.getProperty(
                                        HCLocale.PROPERTY_CLIENT_SUBNET);
        if (clientSubnet == null) {
            throw new HoneycombTestException("System property not found: " +
                                        HCLocale.PROPERTY_CLIENT_SUBNET);
        }
        try {
            boolean passed1 = runUnhappyTests(clientSubnet, true);
            boolean passed2 = runUnhappyTests(bogusSubnet, false);
            return passed1 && passed2;
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        }
    }
    
    // Works
    public boolean test7_hivecfgTests() {
        if (excludeCase()) return false;
        Log.INFO("Tests hivecfg for syntax correct verifications");
        try {
            // test that 6 ips will fail in hivecfg
            boolean ok1 = !changeAuthorizedClients(sixIPs);
            if (!ok1) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok2 = !changeAuthorizedClients(sixSubnets);
            if (!ok2) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok3 =
                !changeAuthorizedClients(randomMix(sixIPs, sixSubnets, 6));
            if (!ok3) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            // verify bad syntax is caught
            String[] a = sixIPs;
            String[] b = sixSubnets;
            boolean ok4 = !changeAuthorizedClients(nonNumericIP(a,b,5));
            if (!ok4) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok5 = !changeAuthorizedClients(nonNumericSubnet(a,b,5));
            if (!ok5) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok6 = !changeAuthorizedClients(invalidRangeIP(a,b,5));
            if (!ok6) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok7 = !changeAuthorizedClients(invalidRangeSubnet(a,b,5));
            if (!ok7) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok8 = !changeAuthorizedClients(invalidNumberIP(a,b,5));
            if (!ok8) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok9 = !changeAuthorizedClients(invalidNumberSubnet(a,b,5));
            if (!ok9) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok10 = !changeAuthorizedClients(shortSyntaxIP(a,b,5));
            if (!ok10) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok11 = !changeAuthorizedClients(shortSyntaxSubnet(a,b,5));
            if (!ok11) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok12 = !changeAuthorizedClients(longSyntaxIP(a,b,5));
            if (!ok12) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            boolean ok13 = !changeAuthorizedClients(longSyntaxSubnet(a,b,5));
            if (!ok13) {
                Log.ERROR("Incorrect syntax was not caught by hivecfg");
            }
            
            //verify a hostname is not accepted with dns
            boolean ok14 = hostnameNoDNS(clientHostname);
            if (!ok14) {
                Log.ERROR("Hostname accepted when DNS is not enabled");
            }
            
            // verify dns is ok
            boolean ok15 = hostnameDNS(clientHostname, true);
            if (!ok15) {
                Log.ERROR("Hostname not accepted when DNS is enabled");
            }
            
            
            return (ok1 && ok2 && ok3 && ok4 && ok5 && ok6 && ok7 && ok8 && ok9 &&
                    ok10 && ok11 && ok12 && ok13 && ok14 && ok15);
    
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        } 
    }
    
    // Works
    public boolean test8_changeAuthWhileNodeDown() {
        if (excludeCase()) return false;
        Log.INFO("Changes auth clients setting while a node is down");
        Log.INFO("Verifies correct hivecfg, irules and data access");
        try {
            // power off a node, verify load spreading
            boolean ok1 = changeAuthRebootVerify("all", true);
            String name = masterNode.getName();
            while (name == masterNode.getName()) {
                int node = (int) (Math.random() * NODES) + 1;
                name = cm.getNode(node).getName();
            }
            addNoFsckFlags();
            Log.INFO("Powering off node " + name);
            verifyFlags(name, true);
            try {
                cm.getNode(name).reboot();
                isNodeDown = true;
            } catch (HoneycombTestException e) {
                // Expect to get here
                Log.INFO("Expected reboot exception caught");
            }
            pause(SLEEP_NODE_DOWN);
            waitForClusterToStart();
            boolean ok2 = changeAuthRebootVerify(clientIP, true);
            boolean ok3 = verifyLoadSpreading(clientIP, true);
    
            Log.INFO("Powering on node " + name);
            verifyFlags(name, false);
            try {
                cm.getNode(name).reboot();
                isNodeDown = false;
            } catch (HoneycombTestException e) {
                // Expect to get here
                Log.INFO("Expected reboot exception caught");
            }
            pause(SLEEP_NODE_UP);
            pause(SLEEP_IRULES);
            waitForClusterToStart();
            boolean ok4 = verifyLoadSpreading(clientIP, false);
    
            return ok1 && ok2 && ok3 && ok4;
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        }
    }
    
    // Works
    public boolean test9_upgrade() throws Throwable {
        if (excludeCase()) return false;
        Log.INFO("Changes auth clients setting before an upgrade");
        Log.INFO("Verifies correct hivecfg, irules and data access");
        String extraClient = System.getProperty(
                                HCLocale.PROPERTY_EXTRA_CLIENT_IP);
        if (extraClient == null) {
                throw new HoneycombTestException(
                                "System property not defined: " +
                                HCLocale.PROPERTY_EXTRA_CLIENT_IP);
        }
        try {
            String[] who = {clientIP, extraClient};
            boolean ok1 = changeAuthRebootVerify(who, true);
            boolean ok2 = upgradeCluster();
            initAll();
            boolean ok3 = verifyHivecfgChange(who);
            boolean ok4 = verifyIrules(who);
            boolean ok5 = doIO(true);
            return ok1 && ok2 && ok3 && ok4 && ok5;
        } catch (Throwable e) {
            Log.stackTrace(e);
            Log.WARN("Exception thrown... sleeping to let cluster recover");
            pause(SLEEP_TEST_RECOVER);
            return false;
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    /////////////////////////////////  HELPERS ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    protected boolean changeAuthRebootVerify(String who, boolean expected) throws Throwable {
        initAll();
        if (!isAuthorizedClients(who)) {
            changeAuthorizedClients(who);
            
            rebootAllAndWait();
        } else {
            waitForClusterToStart();
        }
        return verifyAllAndIO(who, expected);
    }
    
    protected boolean changeAuthRebootVerify(String[] who, boolean expected) throws Throwable {
        initAll();
        if (!isAuthorizedClients(who)) {
            changeAuthorizedClients(who);
            
            rebootAllAndWait();
        } else {
            waitForClusterToStart();
        }
        return verifyAllAndIO(who, expected);
    }
    
    protected boolean runUnhappyTests(String who, boolean expected) throws Throwable {
        boolean result = true;
        boolean ok_to_proceed = changeAuthRebootVerify(who, expected);
    
        // Do a switch failover
        if (ok_to_proceed && hash.containsKey(CLUSTER)) {
            Log.INFO("OK to proceed...");
            apcCluster = CLUSTER;
            apcIP = (String) hash.get(CLUSTER);
            boolean ok = setupAPCScript();
            if (ok) {
                Log.INFO("Powering off the switch...");
                runSystemCommand(
                    "/opt/abt/bin/abt PowerOffPduPort on=apc:apc@" + apcIP +
                    ":1 logDir=/mnt/test/  2>&1 >> /dev/null 2>&1 >> /dev/null"
                );
                switch2 = true;
                pause(SLEEP_SWITCH_FAILOVER);
                waitForClusterToStart();
                //verify switch 2?
                boolean ok1 = verifyAllAndIO(who, expected);
                Log.INFO("Powering on the switch...");
                runSystemCommand(
                    "/opt/abt/bin/abt PowerOnPduPort on=apc:apc@" + apcIP +
                    ":1 logDir=/mnt/test/  2>&1 >> /dev/null 2>&1 >> /dev/null"
                );
                switch2 = false;
                pause(SLEEP_SWITCH_FAILBACK);
                waitForClusterToStart();
                // verify switch 1?
                boolean ok2 = verifyAllAndIO(who, expected);
                
                ok_to_proceed = ok1 && ok2;
            }
        }
        
        // Do a master failover - Works
        if (ok_to_proceed) {
            Log.INFO("OK to proceed...");
            initAll();
            Log.INFO("");
            addNoFsckFlags();
            Log.INFO("Doing a master failover...");
            try {
                masterNode.reboot();
            } catch (HoneycombTestException e) {
                Log.stackTrace(e);
            }
            pause(SLEEP_REBOOT_CLUSTER);
            waitForClusterToStart();

            ok_to_proceed = verifyAllAndIO(who, expected);
        }
        
        // Do a cluster reboot - Works
        if (ok_to_proceed) {
            Log.INFO("OK to proceed...");
            initAll();
            Log.INFO("");
            
            Log.INFO("Rebooting the cluster...");
            rebootAllAndWait();
            
            ok_to_proceed = verifyAllAndIO(who, expected);
        }
        
        // power off a node, verify load spreading - Works
        if (ok_to_proceed) {
            Log.INFO("OK to proceed...");
            initAll();
            String name = masterNode.getName();
            while (name == masterNode.getName()) {
                int node = (int) (Math.random() * NODES) + 1;
                name = cm.getNode(node).getName();
            }
            Log.INFO("");
            addNoFsckFlags();
            Log.INFO("Powering off node " + name);
            verifyFlags(name, true);
            try {
                cm.getNode(name).reboot();
            } catch (HoneycombTestException e) {
                // Expect to get here
                Log.INFO("Expected reboot exception caught");
            }
            pause(SLEEP_NODE_DOWN);
            boolean ok1 = verifyLoadSpreading(who, true);
            
            Log.INFO("Powering on node " + name);
            verifyFlags(name, false);
            try {
                cm.getNode(name).reboot();
            } catch (HoneycombTestException e) {
                // Expect to get here
                Log.INFO("Expected reboot exception caught");
            }
            pause(SLEEP_NODE_UP);
            boolean ok2 = verifyLoadSpreading(who, false);
            
            ok_to_proceed = ok1 && ok2;
        }
        
        // Make sure ntp is good
        if (ok_to_proceed) {
            Log.INFO("OK to proceed...");
            Log.INFO("Testing NTP is ok");
            String ntpServer = null;
            String ntpUsed = null;
            String ntp = masterNode.runCmd(getNTPProperty);
            String pat = "(\\d+\\.\\d+\\.\\d+\\.\\d+)";
            Pattern pattern = Pattern.compile(pat, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(ntp);
            if (matcher.find()) {
                ntpServer = matcher.group(1).toString();
            }
            String ntpq = masterNode.runCmd("'ntpq -p | grep *'");
            Log.INFO(ntpq);
            pat = "\\*(\\S+) ";
           
            pattern = Pattern.compile(pat, Pattern.MULTILINE);
            matcher = pattern.matcher(ntpq);
            if (matcher.find()) {
                ntpUsed = matcher.group(1).toString();
            }
            if (ntpUsed.equals(ntpServer))
                ok_to_proceed = true;
            else {
                String s;
                runSystemCommand("nslookup " + ntpUsed);
                while ((s = STDOUT.readLine()) != null) {
                    if (s.contains(ntpServer))
                        Log.INFO("NTP Server from /config/config.properties used");
                        ok_to_proceed = true;
                        break;
                }
                if (!ok_to_proceed) {
                    Log.WARN("NTP Server from /config/config.properties not used");
                    ok_to_proceed = false;
                }
            }  
        }
        
        // If anything failed...
        if (!ok_to_proceed) {
            Log.ERROR("Not ok to proceed...");
            result = false;
        }
        
        return result;
    }
    
    protected boolean verifyAllAndIO(String who, boolean expected) throws Throwable {
        initAll();
        boolean ok1 = verifyHivecfgChange(who);
        boolean ok2 = verifyIrules(who);
        boolean ok3 = doIO(expected);
        return ok1 && ok2 && ok3;
    }
    protected boolean verifyAllAndIO(String[] who, boolean expected) throws Throwable {
        initAll();
        boolean ok1 = verifyHivecfgChange(who);
        boolean ok2 = verifyIrules(who);
        boolean ok3 = doIO(expected);
        return ok1 && ok2 && ok3;
    }
    
    protected boolean verifyHivecfgChange(String who) throws Throwable {
        Log.INFO("Verifying hivecfg status...");
        STDOUT = cli.runCommand("hivecfg");
        String line;
        while ((line = STDOUT.readLine()) != null) {
            if (line.contains(who)) {
                Log.INFO("Correct hivecfg setting detected");
                return true;
            }
        }
        Log.ERROR("ERR: Incorrect hivecfg setting detected");
        return false;
    }
    
    protected boolean verifyHivecfgChange(String[] who) throws Throwable {
            return verifyHivecfgChange( join(who, delimiter) );
    }
    
    protected boolean verifyIrules(String who) throws Throwable {
        //pause(SLEEP_IRULES_PROPAGATE);
        try {
            irules = masterNode.irules();
        } catch (HoneycombTestException e) {
            throw new HoneycombTestException("Failed to retrieve switch rules "
                                             + Log.stackTrace(e));
        }
        if (irules == null ||
            irules.equals("")) {
            throw new HoneycombTestException("Failed to retrieve switch rules");
        }
        Log.INFO(irules);
        
        if (who.equals("all")) {
            String cheat = allPattern + spIP;
            String admin = allPattern + adminVIP;
            String data  = allPattern + dataVIP;
        
            boolean ok1 = matchPattern(cheat, irules, spOK, spNO);
            boolean ok2 = matchPattern(admin, irules, adOK, adNO);
            boolean ok3 = matchPattern(data, irules, daOK, daNO);
            boolean ok4 = true;
            for (int i = 0; i < NODES; i++) {
                String node = 
                    "forwd\\s+\\|\\s+tcp\\s+"+i+"\\s+8080\\s+0\\s+"+dataVIP;        
                if (!matchPattern(node,
                        irules,
                        "Correct irules for node "+(i+1)+" access found",
                        "ERR: Incorrect irules for node "+(i+1)+" access"))
                    ok4 = false;
            }
            return ok1 && ok2 && ok3 && ok4;
        }
        
        if(!who.contains("/")) { // IP Address
            String cheat = allPattern + spIP;
            String admin = allPattern + adminVIP;
            String data = "accpt\\s+\\|\\s+ip\\s+"+who+"\\s+"+dataVIP;
            
            boolean ok1 = matchPattern(cheat, irules, spOK, spNO);
            boolean ok2 = matchPattern(admin, irules, adOK, adNO);
            boolean ok3 = matchPattern(data, irules, daOK, daNO);
            boolean ok4 = true;
            for (int i = 0; i < NODES; i++) {
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
        else { // Subnet
            String cheat = allPattern + spIP;
            String admin = allPattern + adminVIP;
            String subnet = who.split("/")[0];
            String data = "accpt\\s+\\|\\s+ip\\s+"+subnet+"\\s+"+dataVIP;
            
            boolean ok1 = matchPattern(cheat, irules, spOK, spNO);
            boolean ok2 = matchPattern(admin, irules, adOK, adNO);
            boolean ok3 = matchPattern(data, irules, daOK, daNO);
            boolean ok4 = true;
            for (int i = 0; i < NODES; i++) {
                String node =
                    "forwd\\s+\\|\\s+tcp\\s+" + i + "\\s+8080\\s+" + subnet
                  + "\\s+" + dataVIP;
                if (!matchPattern(node,
                        irules,
                        "Correct irules for node "+(i+1)+" access found",
                        "ERR: Incorrect irules for node "+(i+1)+" access"))
                    ok4 = false;
            }
            return ok1 && ok2 && ok3 && ok4;
        }
    }

    
    protected boolean verifyIrules(String[] who) throws Throwable {
        boolean result = true;
        for (int i = 0; i < who.length; i++) {
            if (!verifyIrules(who[i]))
                result = false;
        }
        return result;
    }
    
    protected boolean doIO(boolean expected) {
        boolean success = false;
        int i = MAX_IO_ITERATIONS;
        while (!success && i > 0) {
            try {
                Log.INFO("Attempting a store IO...");
                store(getFilesize());
                success = (true == expected);
                Log.INFO("Store Successful");
            } catch (HoneycombTestException e) {
                Log.INFO("Store Unsuccessful");
                success = (false == expected);
            }
            i--;
        }
        
        return success;
    }
    
    protected boolean verifyLoadSpreading(String who, boolean dupExpected) throws Throwable {
        ArrayList OPorts = new ArrayList();
        boolean dupFound = false;
        String pat;
        try {
            irules = masterNode.irules();
        } catch (HoneycombTestException e) {
            throw new HoneycombTestException("Failed to retrieve switch rules "
                                             + Log.stackTrace(e));
        }
        if (irules == null ||
            irules.equals("")) {
            throw new HoneycombTestException("Failed to retrieve switch rules");
        }
        Log.INFO(irules);
        
        if (who.equals("all")) {
            pat = "forwd\\s+\\|\\s+tcp\\s+\\d+\\s+8080\\s+0\\s+" + dataVIP + 
                  "\\s+\\S+\\:\\S+\\s+\\|\\s+(\\d+)";
        } else {
            if(!who.contains("/")) { // IP Address
                pat = "forwd\\s+\\|\\s+tcp\\s+\\d+\\s+8080\\s+" + who + "\\s+" + 
                       dataVIP + "\\s+\\S+\\:\\S+\\s+\\|\\s+(\\d+)";
            } 
            else {
                String subnet = who.split("/")[0];
                pat = "forwd\\s+\\|\\s+tcp\\s+\\d+\\s+8080\\s+" + subnet + "\\s+" + 
                       dataVIP + "\\s+\\S+\\:\\S+\\s+\\|\\s+(\\d+)";
            
            }
        }
        Log.INFO("Verifying Load Spreading...");
        Pattern pattern = Pattern.compile(pat, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(irules);
        while (matcher.find()) {
            Log.INFO("Found: " + matcher.group(1));
            String match = matcher.group(1);
            if (OPorts.contains(match)) {
                if (dupExpected)
                    Log.INFO("Expected duplicate load spread detected");
                else
                    Log.ERROR("Unexpected duplicate load spread detected");
                dupFound = true;
            }
            OPorts.add(match);
        }
        return ((dupFound == dupExpected) && (OPorts.size() == NODES));
    }
    
    protected boolean verifyLoadSpreading(String[] who, boolean dupExpected) throws Throwable {
        boolean result = true;
        for (int i = 0; i < who.length; i++) {
            if (!verifyLoadSpreading(who[i], dupExpected))
                result = false;
        }
        return result;
    }
    
    protected void waitForClusterToStart() throws HoneycombTestException {
        // Wait for cli to be accessible and sysstat returns Online

        Log.INFO("Waiting for cluster to come online");
        boolean ready = false;
        int i = MAX_ONLINE_ITERATIONS;
        while (i > 0 && !ready) {
            try {
                i--;
                ArrayList lines = readSysstat();
                if (lines.toString().contains("Data services Online")) {
                    if (isNodeDown && 
                        lines.toString().contains("FaultTolerant")) {
                            ready = true;
                    } else {
                        if (lines.toString().contains("HAFaultTolerant")) {
                            ready = true;
                        }
                    }
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
        initAdminCLI();
        getMasterCell();
        initAdminCM();
        initMaster();
    }
    protected void initAdminCLI() {
        cli = new CLI(CLUSTER + "-admin");
    }
    
    protected void initAdminCM() throws HoneycombTestException{
        if (switch2)
            return;
        cm = new ClusterMembership(-1, CLUSTER + "-admin", 0, masterCell, 
                CLUSTER + "-admin", null);
        cm.setQuorum(true);
        cm.initClusterState(masterCell);
    }
    
    protected void initMaster() {
        masterNode = cm.getMaster();
    }
    
    protected void getMasterCell() throws Throwable {
        STDOUT = cli.runCommand("hiveadm");
        String regex = "Cell (\\d+): adminVIP = " + adminVIP;
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        String line;
        while ((line = STDOUT.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                masterCell = new Integer(matcher.group(1)).intValue();
                Log.INFO("Master Cell: " + masterCell);
                return;
            }
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
    
    protected boolean isAuthorizedClients(String who) throws Throwable {
        STDOUT = cli.runCommand("hivecfg");
        String line;
        while ((line = STDOUT.readLine()) != null) {
            if (line.contains("Authorized Clients")) {
                if (line.contains(who)) {
                    Log.INFO(line);
                    Log.INFO("Authorized Clients is already set corectly");
                    return true;
                }
            }
        } 
        return false;
    }
    
    protected boolean isAuthorizedClients(String[] who) throws Throwable {
        boolean result = true;
        for (int i = 0; i < who.length; i++) {
            if (!isAuthorizedClients(who[i]))
                result = false;
        }
        return result;
    }
    
    protected boolean changeAuthorizedClients(String who) throws Throwable {
        Log.INFO("Changing Authorized Clients...");
            try {
                STDOUT = cli.runCommand(
                        "hivecfg --authorized_clients " + who);
                
                String line;
                boolean ok = true;
                while ((line = STDOUT.readLine()) != null) {
                    Log.INFO(line);
                    if (line.contains("Invalid authorized client") ||
                        line.contains("Invalid authorizied client") ||
                        line.contains("An IP address must be specified") ||
                        line.contains("Invalid mask bit") ||
                        line.contains("Too many authorized clients") ||
                        line.contains("Invalid IP/mask combination"))
                    {
                        ok = false;
                    }
                }
                if (!ok)
                    return false;
            } catch (Throwable e) {
                return false;
            }
//      pause(SLEEP_HIVECFG);
        printHivecfg();
        return true;
    }
    
    protected boolean changeAuthorizedClients(String[] who) throws Throwable {
        String joined = join(who, delimiter);
        return changeAuthorizedClients(joined);
    }
    protected void rebootAllAndWait() throws Throwable {
        addNoFsckFlags();
        Log.INFO("Rebooting Cluster...");
        STDOUT = cli.runCommand("reboot -c " + masterCell + " -A -F");
        pause(SLEEP_REBOOT_CLUSTER);
        waitForClusterToStart();
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
        STDOUT = cli.runCommand(cmd + " -c " + masterCell);
        while ((line = STDOUT.readLine()) != null)
            result.add(line);
        return result;
    }
    
    protected boolean hostnameDNS(String hostname, boolean expected) throws Throwable {
        STDOUT = cli.runCommand("hivecfg");
        String line;
        while ((line = STDOUT.readLine()) != null) {
            if (line.contains("DNS = n")) {
                Log.INFO(line);
                String dns1 = System.getProperty(HCLocale.PROPERTY_DNS1);
                if (dns1 == null) {
                    throw new HoneycombTestException(
                                        "System property not defined: " +
                                        HCLocale.PROPERTY_DNS1);
                }
                String dns2 = System.getProperty(HCLocale.PROPERTY_DNS2);
                if (dns2 == null) {
                    throw new HoneycombTestException(
                                        "System property not defined: " +
                                        HCLocale.PROPERTY_DNS2);
                }
                String domain = System.getProperty(
                                               HCLocale.PROPERTY_DOMAIN_NAME);
                if (domain == null) {
                    throw new HoneycombTestException(
                                        "System property not defined: " +
                                        HCLocale.PROPERTY_DOMAIN_NAME);
                }
                String search_dns = System.getProperty(
                                                   HCLocale.PROPERTY_DOMAIN);
                if (search_dns == null) {
                    throw new HoneycombTestException(
                                        "System property not defined: " +
                                        HCLocale.PROPERTY_DOMAIN);
                }
                cli.runCommand(
                           "hivecfg -D y -1 " + dns1 + " -2 " + dns2 + " -m " +
                           domain + " -e " + search_dns);
                pause(SLEEP_HIVECFG);
            }
        }
        boolean ok1 = changeAuthorizedClients(hostname);
        return ok1 && expected;
    }
    
    protected boolean hostnameNoDNS(String hostname) throws Throwable {
        STDOUT = cli.runCommand("hivecfg");
        String line;
        while ((line = STDOUT.readLine()) != null) {
            Log.INFO(line);
            if (line.contains("DNS = y")) {
                cli.runCommand("hivecfg -D n -h all");
                pause(SLEEP_HIVECFG);
            }
        }
        return !changeAuthorizedClients(hostname);
    }
    
    protected boolean upgradeCluster() throws Throwable {
        boolean ok = false;
        String minorVersion = null;
        String currentVersion = null;
        String s = null;
        Log.INFO("Getting Minor Version...");
        String releases_url = System.getProperty(
                                        HCLocale.PROPERTY_RELEASES_URL);
        if (releases_url == null) {
            throw new HoneycombTestException("System property not found: " +
                                        HCLocale.PROPERTY_RELEASES_URL);
        }
        String iso_path = System.getProperty(
                                        HCLocale.PROPERTY_RELEASES_ISO_PATH);
        if (iso_path == null) {
            throw new HoneycombTestException("System property not found: " +
                                        HCLocale.PROPERTY_RELEASES_ISO_PATH);
        }
        runSystemCommand("curl -s " + releases_url + minorVersionCommand);
        while ((s = STDOUT.readLine()) != null) {
            minorVersion = s.trim();
            Log.INFO(s);
        }
        Log.INFO("Getting Major Version...");
        runSystemCommand("curl  -s -q " + releases_url + 
                         preVersionCommand + minorVersion + postVersionCommand);
        while ((s = STDOUT.readLine()) != null) {
            currentVersion = s.trim();
            Log.INFO("Current Version: " + currentVersion);
        }
        String url = releases_url + currentVersion + iso_path + 
                                    currentVersion + ".iso";
        Log.INFO("Validating URL...");
        runSystemCommand("wget -O - -q " + url + " 2>&1 >> /dev/null");
        while ((s = STDOUT.readLine()) != null)
            Log.INFO(s);
        
        if (currentVersion != null) {
            STDOUT = cli.runCommand("upgrade -c " + masterCell + " -F " + url);
            String line;
            while ((line = STDOUT.readLine()) != null) {
                if (line.contains("Upgrade succeeded"))
                    ok = true;
            }
            //pause(SLEEP_UPGRADE);
            pause(SLEEP_REBOOT_CLUSTER);
            return ok;
        }
        
        return false;
    }
    
    protected void printHivecfg() throws Throwable {
        printToLog(cli.runCommand("hivecfg"));
    }
    
    protected void addNoFsckFlags() {
        Log.INFO("Adding no FSCK flags...");
        for (int i = 0; i < NODES; i++) {
            int node = i + NODE_NUM_OFFSET;
            try {
                CheatNode.runCmdOnDefaultCheat(
                    "ssh hcb" + node + 
                    " touch /config/clean_unmount__dev_dsk_c0t0d0s4");
                CheatNode.runCmdOnDefaultCheat(
                    "ssh hcb" + node + 
                    " touch /config/clean_unmount__dev_dsk_c0t1d0s4");
                CheatNode.runCmdOnDefaultCheat(
                    "ssh hcb" +node + 
                    " touch /config/clean_unmount__dev_dsk_c1t0d0s4");
                CheatNode.runCmdOnDefaultCheat(
                    "ssh hcb" + node + 
                    " touch /config/clean_unmount__dev_dsk_c1t1d0s4");
                Log.INFO(" - node " + node + ": ok");
            } catch (HoneycombTestException e) {
                Log.stackTrace(e);
                Log.ERROR(" - node " + node + ": not ok");
            }
        }
    }
    
    protected void verifyFlags(String name, boolean flagsExpected) throws HoneycombTestException {
        int i = MAX_FLAG_ITERATIONS;
        if (flagsExpected) {
            while (!isFlagsPresent(name) && i > 0) {
                Log.INFO("Adding nohoneycomb/noreboot flags...");
                CheatNode.runCmdOnDefaultCheat("ssh " + name + " touch " + NOHONEYCOMB);
                CheatNode.runCmdOnDefaultCheat("ssh " + name + " touch " + NOREBOOT);
                i--;
            }
        } else {
            while (isFlagsPresent(name) && i > 0) {
                Log.INFO("Removing nohoneycomb/noreboot flags...");
                CheatNode.runCmdOnDefaultCheat("ssh " + name + " rm " + NOHONEYCOMB);
                CheatNode.runCmdOnDefaultCheat("ssh " + name + " rm " + NOREBOOT);
                i--;
            }
        }
        
        if (i == 0) {
            String a = (flagsExpected) ? "add" : "remove";
            Log.ERROR("Could not " + a + "nohoneycomb/noreboot flags");
        }
    }
    
    protected boolean isFlagsPresent(String name) throws HoneycombTestException {
        ExitStatus e = cm.getNode(name).runCmdPlus("ls " + NOHONEYCOMB);
        boolean ok1 = (e.getReturnCode() == 0);
        e = cm.getNode(name).runCmdPlus("ls " + NOREBOOT);
        boolean ok2 = (e.getReturnCode() == 0);
        return ok1 && ok2;
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
        STDOUT =  new BufferedReader(new
                InputStreamReader(p.getInputStream()));
        if (p.exitValue() != 0) {
            Log.ERROR("Command exited with value: " + p.exitValue());
            return false;
        }
        return true;
    }
    
    protected void printToLog(BufferedReader stdout) throws Throwable {
        String line = null;
        while ((line = stdout.readLine()) != null)
                Log.INFO("  " + line);
    }
    
    protected String join(String[] who, String delimiter) {
        String joined = "";
        for (int i = 0; i < (who.length - 1); i++) {
            joined += who[i];
            joined += ",";
        }
        // Last one
        joined += who[who.length - 1];
        return joined;
    }
    
    protected void pause(long milliseconds) {
        long time = milliseconds / 1000;
        Log.INFO("Sleeping for " + time + " seconds...");
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) { /* Ignore */ }
    }
    
    protected String[] randomMix(String[] one, String[] two, int count) {
        String[] randomized = new String[count];
        for (int i = 0; i < count; i++ ) {
            if (Math.random() < .5)
                randomized[i] = one[i];
            else
                randomized[i] = two[i];
        }
        return randomized;
    }
    
    protected String[] nonNumericIP(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        a[(int) (Math.random() * count)] = "A.A.A.A";
        return a;
    }
    protected String[] nonNumericSubnet(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        a[(int) (Math.random() * count)] += "/A";
        return a;
    }
    protected String[] invalidRangeIP(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        int b = 0;
        while (b <= 255)
            b = ((int) (Math.random() * Integer.MAX_VALUE));
        a[(int) (Math.random() * count)] = "b.b.b.b";
        return a;
    }
    protected String[] invalidRangeSubnet(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        int b = 0;
        while (b <= 32)
            b = ((int) (Math.random() * Integer.MAX_VALUE));
        a[(int) (Math.random() * count)] += "/" + b;
        return a;
    }
    
    protected String[] invalidNumberIP(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        a[(int) (Math.random() * count)] = "-1.-1.-1.-1";
        return a;
    }
    protected String[] invalidNumberSubnet(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        a[(int) (Math.random() * count)] += "/-24";
        return a;
    }

    protected String[] shortSyntaxIP(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        a[(int) (Math.random() * count)] = "111.111.111";
        return a;
    }
    protected String[] shortSyntaxSubnet(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        a[(int) (Math.random() * count)] += "/";
        return a;
    }
    
    protected String[] longSyntaxIP(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        a[(int) (Math.random() * count)] = "111.111.111.111.111";
        return a;
    }
    protected String[] longSyntaxSubnet(String[] one, String[] two, int count) {
        String[] a = randomMix(one, two, count);
        a[(int) (Math.random() * count)] += "/24/";
        return a;
    }    
}
