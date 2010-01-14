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



package com.sun.honeycomb.hctest.cli;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.ArrayList;

public class CLITestReboot extends HoneycombCLISuite {
    
    /** total no of loops the test will run */
    private static int noOfLoop = 1; 
    private static boolean doHadbVerification = true;
    private static HashMap intial_node_disk_state = null;
    
    private static final String REBOOT_F_STRING = " -F ";
    private static final String REBOOT_FORCE_STRING = " --force ";
    private static final String REBOOT_A_STRING = " -A ";
    private static final String REBOOT_ALL_STRING = " --all ";

    private static String REBOOT_F_COMMAND = null;
    private static String REBOOT_FORCE_COMMAND = null;
    private static String REBOOT_A_COMMAND = null;
    private static String REBOOT_ALL_COMMAND = null;

    
    /** Creates a new instance of CLITestReboot */
    public CLITestReboot() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: reboot\n");
        sb.append("\t o positive test - reboot --all \n");
        sb.append("\t o negative test - invalid option\n");
        sb.append("\tUsage: reboot test runs in part of CLI test suite, but " +
                "user can run reboot test in standalone mode as follow:\n");
        sb.append("\t - /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestReboot " +
                "-ctx nodes=8 -ctx iterations=10 -ctx cluster=devxxx:nohadb\n");
        sb.append("\t\t o nodes is a required argument for single " +
                "cell setup, default to 8\n");
        sb.append("\t\t o iterations is the number of times to run reboot test, " +
                "default to 1\n");
        sb.append("\t\t o if nohadb flag is specified, then reboot test will " +
                "skip the hadb verification. if nohadb flag is not set, then test " +
                "will verify hadb state after rebooting cluster\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        REBOOT_F_COMMAND = HoneycombCLISuite.REBOOT_COMMAND + REBOOT_F_STRING;
        REBOOT_FORCE_COMMAND = HoneycombCLISuite.REBOOT_COMMAND + REBOOT_FORCE_STRING;
        REBOOT_A_COMMAND = HoneycombCLISuite.REBOOT_COMMAND + REBOOT_A_STRING;
        REBOOT_ALL_COMMAND = HoneycombCLISuite.REBOOT_COMMAND + REBOOT_ALL_STRING;    
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if (errorString.indexOf(INVALID_CELLID_MESSAGE) != -1)
	    return true;
	
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testrebootAll() throws HoneycombTestException {
       
        TestCase self = createTestCase("CLI_Reboot_All");
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
            
        if (self.excludeCase()) return;

        // determine the no of loops to run the test
        String loopArg = null;
        try {
            loopArg = getProperty(HoneycombTestConstants.PROPERTY_ITERATIONS); 
            if (null != loopArg)
                noOfLoop = Integer.parseInt(loopArg);
            else
                noOfLoop = 1;      
        } catch (NumberFormatException nfe) {
            Log.WARN("Invalid loop number " + loopArg + " - verify help, " +
              "default to 1)");
            noOfLoop = 1;
        }
        
        String s = getProperty(HoneycombTestConstants.PROPERTY_NO_HADB);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_HADB +
                " was specified. Skipping hadb verification");
            doHadbVerification = false;
        }
        else {
            // verify hadb state before running reboot test
            ArrayList allCellid = getAllCellid();
            for (int i = 0; i < allCellid.size(); i++) {
                Integer cellid = (Integer) allCellid.get(i);
                if (!verifyHadb(cellid.intValue())) {  
                    self.testFailed("Unexpected hadb state - abort reboot test");
                    return;
                }
            }
        }
        
        // get the cluster state before running reboot --all
        try {
            intial_node_disk_state = new HashMap();
            ArrayList allCellid = getAllCellid();
            for (int i = 0; i < allCellid.size(); i++) {
                int cellid = ((Integer) allCellid.get(i)).intValue();
                ArrayList lines = readSysstat(cellid);
                String node_disk_state = (String)lines.get(
                            HoneycombCLISuite.SYSSTAT_NODE_DISK_STATE_LINE);  
                Log.INFO("The state of cell " + cellid + " before start " +
                        "running reboot test:\n" +  node_disk_state);
                intial_node_disk_state.put(new Integer(cellid), node_disk_state);
            }
        } catch (Throwable t) {
            Log.ERROR("Error while running sysstat:" + Log.stackTrace(t));
        }
        
        // run reboot in a loop
        
        ArrayList allCellid = getAllCellid();
        
        for (int count=0; count<noOfLoop; count++) {
            Log.INFO("\n******  Starting Iteration # " + count + " ******\n");
                       
            for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {

                int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                setCellid(cellid);
                String cellAdminIp = (String) 
                                     getAllAdminIp().get(new Integer(cellid));
                String cellCheatIp = (String) 
                                     getAllCheatIp().get(new Integer(cellid));
            
                Log.SUM("Preparing to reboot cell " + cellid + ":");
                boolean isTestPass = checkCell(cellid, cellAdminIp, cellCheatIp);
                if (!isTestPass) {
                    self.testFailed("Problem with initial state of cell: " + 
                            cellid + ", will abort the test");
                    return;
                }
                setCurrentInternalLogFileNum();
                isTestPass = rebootCell(cellid, 
                        HoneycombCLISuite.REBOOT_COMMAND + 
                        REBOOT_F_STRING + REBOOT_ALL_STRING);
                verifyAuditReboot(String.valueOf(cellid), "info.adm.rebootAll");
                
                if (!isTestPass) {
                    self.testFailed("Problem rebooting cell: " + 
                            cellid + ", will abort the test");
                    return;
                }
            }
        }
         
        self.testPassed();
    }
       
    public void testRebootNegativeTest() {
        TestCase self = createTestCase("CLI_reboot_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        String [] lsInvalidArgs = {
            HoneycombCLISuite.REBOOT_COMMAND + " " + HoneycombCLISuite.REBOOT_COMMAND,
            HoneycombCLISuite.REBOOT_COMMAND + " -f",
            HoneycombCLISuite.REBOOT_COMMAND + " --F",
            HoneycombCLISuite.REBOOT_COMMAND + " --Force",
            HoneycombCLISuite.REBOOT_COMMAND + " -force",
            HoneycombCLISuite.REBOOT_COMMAND + " -a",
            HoneycombCLISuite.REBOOT_COMMAND + " --A",
            HoneycombCLISuite.REBOOT_COMMAND + " --All",
            HoneycombCLISuite.REBOOT_COMMAND + " -all",
            REBOOT_A_COMMAND + " -f",
            REBOOT_ALL_COMMAND + " -f",
            REBOOT_A_COMMAND + " --F",
            REBOOT_ALL_COMMAND + " --F",
            REBOOT_A_COMMAND + " --Force",
            REBOOT_ALL_COMMAND + " --Force",
            REBOOT_A_COMMAND + " -force",
            REBOOT_ALL_COMMAND + " -force",            
            REBOOT_F_COMMAND + " -a",
            REBOOT_FORCE_COMMAND + " -a",
            REBOOT_F_COMMAND + " --A",
            REBOOT_FORCE_COMMAND + " --A",
            REBOOT_F_COMMAND + " --All",
            REBOOT_FORCE_COMMAND + " --All",
            REBOOT_F_COMMAND + " -all",
            REBOOT_FORCE_COMMAND + " -all" 
        };
        
        // will test with invalid cellid for multicell
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.REBOOT_COMMAND,
            REBOOT_F_COMMAND, 
            REBOOT_FORCE_COMMAND
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command without cellid
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], HoneycombCLISuite.REBOOT_HELP);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute invalid command with cellid
            setCellid();
            isTestPass = verifyCommandStdout(
                    true, lsInvalidArgs[i], HoneycombCLISuite.REBOOT_HELP);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        int invalidCellId = getInvalidCellid();
                
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
               
            // execute command with invalid cellid
            String formattedCommand = formatCommandWithCellid(
                    lsInvalidMulticellArgs[i], invalidCellId);
            
            isTestPass = verifyCommandStdout(false, formattedCommand, null);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        if(noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }

    private boolean checkCell(int cellid, String cellAdminIp, String cellCheatIp) {
        try {
            ClusterMembership cm = 
               new ClusterMembership(-1, getAdminVIP(), 0, cellid, cellAdminIp,
                                     cellCheatIp);
            if (!verifyCMMState(cellid, cm)) {
                 Log.ERROR("Cluster is in bad state before reboot, not continuing");
                 return false;
            }
        } catch(Exception e) {
            Log.ERROR("Exception from CMM: " + e);
            return false;
        }
        return true;
    }
         
    private boolean rebootCell(int cellid, String command) {
        int noOfFailure = 0;
        try {           
            BufferedReader output = runCommandWithCellid(command, cellid);
            String line = null;
            String matchStr = "Entered maintenance mode";
            boolean isFound = false;
            
            while ((line = output.readLine()) != null) {                
                Log.INFO(line);  
                if (line.indexOf(matchStr) != -1)
                    isFound = true;
            }
            output.close();
             
            if (!isFound) {
                Log.ERROR("Expected output <" + matchStr + "> is missing");
                Log.ERROR("problem with reboot --all, aborting the test");
                System.exit(1);
            }
            
            HCUtil.doSleep(500, "Wait to reboot completely...");
            
        } catch (Throwable t) {
            noOfFailure++;
            Log.ERROR("Error while rebooting silo:" + t.toString());
        }
           
        if (!verifyRebootCell(cellid))
            noOfFailure++;        
        
        if (noOfFailure != 0) {
            Log.ERROR("Cell " + cellid + " is not rebooted properly");
            return false;
        }
        else {
            Log.INFO("Cell " + cellid + " is rebooted properly");
            return true;
        }
    }
   
    
    private boolean verifyRebootCell(int cellid) {
        
        int errors = 0;
        Log.INFO("Verify whether cell " + cellid + " is rebooted properly or not");
        
        Integer cellidInt = new Integer(cellid);
        String adminIp = (String) getAllAdminIp().get(cellidInt);
        String dataIp = (String) getAllDataIp().get(cellidInt);
        String cheatIp = (String) getAllCheatIp().get(cellidInt);
        
        try {
            int sleepTime = 900; // wait max 15 mins for cluster to come up
            int sleepInterval = 150;
            int totalSleep = 0;

            while (totalSleep < sleepTime) { 
                HCUtil.doSleep(sleepInterval, "Begin sleep " + sleepInterval);
                totalSleep += sleepInterval;
                
                // ping admin & data ip
                boolean isPingAdminIp = isPing(cheatIp, adminIp);
                boolean isPingDataIp = isPing(cheatIp, dataIp);

                if ((isPingAdminIp) && (isPingDataIp)) {
                    Log.INFO("admin/data ips are pingable");
                    break;
                }
                else {
                    if (totalSleep >= sleepTime) {
                        Log.ERROR("Unable to ping admin/data ip");
                        return false;
                    }
                }
            }
             
            // verfiy sysstat
            String node_disk_state_before_reboot = 
                      (String) intial_node_disk_state.get(new Integer(cellid));
            String node_disk_state_after_reboot = null;
            String cluster_state = null;
            String services_state = null;
                
            totalSleep = 0;
            while (totalSleep < sleepTime) {
                HCUtil.doSleep(sleepInterval, "Begin sleep " + sleepInterval);

                ArrayList lines = readSysstat(cellid);
                cluster_state = (String)lines.get(
                        HoneycombCLISuite.SYSSTAT_CLUSTER_STATE_LINE);
                services_state = (String)lines.get(
                        HoneycombCLISuite.SYSSTAT_SERVICES_STATE_LINE);
                node_disk_state_after_reboot = (String)lines.get(
                        HoneycombCLISuite.SYSSTAT_NODE_DISK_STATE_LINE);  
                
                if ((cluster_state.contains(HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE)) &&
                        (services_state.contains(HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE)) &&
                        node_disk_state_before_reboot.equals(node_disk_state_after_reboot)) {
                    Log.INFO(HoneycombCLISuite.SYSSTAT_COMMAND + ": " +
                            cluster_state + "; \n" +
                            node_disk_state_before_reboot + "; \n" +
                            services_state);
                    break;
                } else {
                    Log.INFO("Unexpected " + 
                            HoneycombCLISuite.SYSSTAT_COMMAND + ": " +
                            cluster_state + "; \n" + 
                            node_disk_state_after_reboot + "; \n" +
                            services_state);
                }
                totalSleep += sleepInterval;
            }
            if (totalSleep >= sleepTime) {
                // timed out
                if (!(cluster_state.contains(
                              HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE) &&
                      services_state.contains(
                              HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE))) {

                    // error in cluster/services, maybe in node/disk too
                    errors++;
                    Log.ERROR("Unexpected " + 
                            HoneycombCLISuite.SYSSTAT_COMMAND + ": " +
                            cluster_state + "; \n" + 
                            node_disk_state_after_reboot + "; \n" +
                            services_state);
                } else {
                    // cluster/services ok, examine node/disk state
                    // e.g. "16 nodes online, 63 disks online."
                    int i = node_disk_state_before_reboot.indexOf(",");
                    int j = node_disk_state_after_reboot.indexOf(",");
                    if (i == -1  ||  j == -1) {
                        Log.ERROR("unexpected node/disk state:\n" +
                                  node_disk_state_before_reboot + "\n" +
                                  node_disk_state_after_reboot + "\n" +
                                  "exiting");
                        System.exit(1);
                    }
                    String nodesBefore = 
                                 node_disk_state_before_reboot.substring(0, i);
                    String nodesAfter =
                                 node_disk_state_after_reboot.substring(0, j);
                    if (!nodesBefore.equals(nodesAfter)) {
                        Log.ERROR("Lost node(s):\n" +
                               "  before reboot: " + 
                               node_disk_state_before_reboot + "\n" +
                               "  after reboot: " +
                               node_disk_state_after_reboot);
                        errors++;
                    } else if (!node_disk_state_before_reboot.equals(
                                               node_disk_state_after_reboot)) {
                        Log.ERROR("Lost drive(s):\n" +
                               "  before reboot: " + 
                               node_disk_state_before_reboot + "\n" +
                               "  after reboot: " +
                               node_disk_state_after_reboot);
                        errors++;
                    }
                }
            }
            
            // verify hadb state
            if (doHadbVerification) {
                if (!verifyHadb(cellid))
                    errors++;
            }
           
            // verify uptime 
            int maxUpTimeDelta = maxUpTimeDelta(adminIp);
            if (maxUpTimeDelta > 4) {
                Log.ERROR("Max difference in uptime between nodes: " + 
                        maxUpTimeDelta +
                        " minutes - exceeds max expected of 4");
                errors++;
            }
           
        } catch (Throwable t) {
            Log.ERROR("Error while verifying reboot cell:" + Log.stackTrace(t));
        } 
        
        if (errors == 0)
            return true;
        else
            return false;
    }

    private boolean verifyCMMState(int cellid, ClusterMembership cm) 
            throws HoneycombTestException {
        int totalNode = -1;
        try {
            totalNode = getNodeNum();
        } catch(Throwable t) {
            Log.ERROR("Error while getting total node number: " + t.toString());
            return false;
        }
        
        cm.setQuorum(true);
        cm.initClusterState(cellid); 
                    
        for (int i=1; i<=totalNode; i++) {
            //Log.INFO("**** Node Number: " + i + " ****");

            ClusterNode node = cm.getNode(i); 
            if (!node.isAlive()) {
                Log.INFO("Cluster node " + node.getName() + " is offline");
                return false;
            }
        }
        return true;
    } 
    
    private int maxUpTimeDelta(String adminIp) {
        int mostDelta = 0;
        try {
            int low = -1;
            int high = -1;
            for(int i=101; i<=100+getNodeNum(); i++) {
                BufferedReader output = runCommandAsRoot(adminIp, 
                        "ssh hcb" + i + " uptime");
                String line=output.readLine();
                output.close();
                int minutes = -1;
                String [] tokenizeStr = tokenizeIt(line, " ");
               
                if(tokenizeStr[3].startsWith("min")) {
                    minutes = Integer.valueOf(tokenizeStr[2]).intValue();
                } else if(tokenizeStr[3].startsWith("hr")) {
                    minutes= Integer.valueOf(tokenizeStr[2]).intValue()*60;
		} else if(tokenizeStr[2].contains(":")) {
                    tokenizeStr = tokenizeIt(tokenizeStr[2], ":");
                    minutes = (Integer.valueOf(tokenizeStr[0]).intValue()*60) +
                              (Integer.valueOf(tokenizeStr[1].replace(",", "")).
                                                                   intValue());
                } else if(tokenizeStr[3].startsWith("day")) {
                    minutes = Integer.valueOf(tokenizeStr[2]).intValue()*60*24;
                } else {
                    Log.WARN("Didn't get a time- bad output: " + line);
                }

                Log.INFO("Node " + i + " has been up for " + minutes + " minutes.");
                if (low == -1 || minutes < low) 
                    low = minutes;
                if (high == -1 || minutes > high) 
                    high = minutes;
            }
            
            mostDelta = high - low;
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        } 
        Log.INFO("Largest delta, in minutes: " + mostDelta);        
        return mostDelta;
    }
    
    private void verifyAuditReboot(String cellid, String msg) {
        ArrayList paramValueList = new ArrayList();
        paramValueList.add(cellid);
        verifyAuditInternalLog(HoneycombCLISuite.REBOOT_COMMAND, 
                msg, paramValueList, true);         
    }
}
