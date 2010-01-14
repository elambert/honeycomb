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

import java.util.ArrayList;
import java.io.BufferedReader;

/**
 *
 * @author jk142663
 */
public class CLITestSysstat extends HoneycombCLISuite {
    
    private static final String SYSSTAT_I_STRING = "-i";
    private static final String SYSSTAT_INTERVAL_STRING = "--interval";
    private static final String SYSSTAT_V_STRING = "-v";
    
    private static String SYSSTAT_I_COMMAND;
    private static String SYSSTAT_INTERVAL_COMMAND;
    private static String SYSSTAT_V_COMMAND;
    
    /*
     *  sysstat text strings for expected output lines
     */
    private static final String CELL_ID_TEXT = "Cell ";
    private static final String NODES_ONLINE_TEXT = "nodes online";
    private static final String DISKS_ONLINE_TEXT = "disks online";
    private static final String DATA_VIP_TEXT = "Data VIP ";
    private static final String ADMIN_VIP_TEXT = "Admin VIP ";
    private static final String DATA_SERVICES_TEXT = "Data services ";
    private static final String DATA_INTEGRITY_TEXT = "Data Integrity ";
    private static final String DATA_RELIABILITY_TEXT = "Data Reliability ";
    private static final String QUERY_INTEGRITY_TEXT = "Query Integrity ";
    private static final String NDMP_STATUS_TEXT = "NDMP status:";
    // Expected lines of sysstat output for a cell
    private static final int NO_OUTPUT_LINES = 8;
    
    // Size of date string output in lines.
    // For example: Tue Mar 18 07:03:46 UTC 2008
    private static int DATE_STRING_LEN = 29;
    
    private static String[] outputLines = {CELL_ID_TEXT,
    " ", DATA_VIP_TEXT, DATA_SERVICES_TEXT, DATA_INTEGRITY_TEXT,
    DATA_RELIABILITY_TEXT, QUERY_INTEGRITY_TEXT, NDMP_STATUS_TEXT};
    private static final String[] outputLinesTitle =
    {"Cell Status", "Nodes and Disk status", "Data and Admin VIP info",
     "Services Status", "Data Integrity Status", "Data Reliability Status",
     "Query Integrity Status", "NDMP Status"};
    
    private static final int CELL_ID_INDEX = 0;
    private static final int NODES_DISKS_INDEX = 1;
    private static final int VIP_INDEX = 2;
    private static final int DATA_SERVICES_INDEX = 3;
    private static final int DATA_INTEGRITY_INDEX = 4;
    private static final int DATA_RELIABLITY_INDEX = 5;
    private static final int QUERY_INTEGRITY_INDEX = 6;
    private static final int NDMP_INDEX = 7;
    
    /*
     *  Data structure to hold cell status information both expected
     *  and the actual status displayed by the sysstat command
     */
    class CellStatus {
        String strCellId;
        // Expected values - populated on instantiation.
        public int cellid;
        public int totalNodes;
        public int totalDisks;
        public int onlineNodes;
        public int onlineDisks;
        public int offlineNodes;
        public int offlineDisks;    // disks that are disabled or offline
        public int missingDisks;    // disks on offline nodes do not show up
        // in status commands
        
        // Test status or info found from test
        
        public boolean foundCellIdOutput;
        
        // true/false if found expected output line
        public boolean [] foundOutput;
        public boolean nodeCountCorrect;
        public boolean diskCountCorrect;
        public boolean dataServicesStatusCorrect;
        
        // Contains output using cell id (sysstat -c ###)
        // used to compare against output generated without -c option
        public ArrayList cellOutputLines = null;
        
        CellStatus(int id, int totalNodes, int totalDisks) {
            foundOutput = new boolean[CLITestSysstat.NO_OUTPUT_LINES];
            cellid = id;
            this.totalNodes = totalNodes;
            this.totalDisks = totalDisks;
            onlineNodes = totalNodes;
            onlineDisks = totalDisks;
            offlineNodes = 0;
            offlineDisks = 0;
            missingDisks = 0;
            cellOutputLines = new ArrayList();
        }
    }
    
    /** Creates a new instance of CLITestSysstat */
    public CLITestSysstat() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: sysstat\n");
        sb.append("\nnodes is a required argument for single " +
                "cell setup (-ctx nodes=<node_num>), default to 8\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        
        super.setUp();
        
        SYSSTAT_I_COMMAND = HoneycombCLISuite.SYSSTAT_COMMAND + " " +
                SYSSTAT_I_STRING;
        SYSSTAT_INTERVAL_COMMAND = HoneycombCLISuite.SYSSTAT_COMMAND + " " +
                SYSSTAT_INTERVAL_STRING;
        SYSSTAT_V_COMMAND = HoneycombCLISuite.SYSSTAT_COMMAND + " " +
                SYSSTAT_V_STRING;
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
        if (errorString == null)
            return false;
        errorString = errorString.toLowerCase();
        if ((errorString.indexOf(INVALID_CELLID_MESSAGE) != -1)
        || errorString.startsWith("invalid interval")
        || errorString.startsWith("specified interval"))
            return true;
        
        return super.isInErrorExclusionList(errorString);
    }
    
    // this test is run last because it potentially leaves
    // things messed up and also takes forever
    public void test_Z_Sysstat() {
        
        TestCase self = createTestCase("CLI_sysstat");
        
        
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
        
        boolean isTestPass = false;
        
        try {
            int noOfFailure = 0;
            int totalNode = getNodeNum();
            
            ArrayList allCellid = getAllCellid();
            
            for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
                
                int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                setCellid(cellid);
                
                int totalOnlineNode = totalNode;
                int totalOnlineDisk = totalNode * 4;
                
                if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                        HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE,
                        HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE)) {
                    Log.WARN("Basic syntax checking is done. won't run " +
                            "advanced sysstat test due to unhealthy cluster");
                    self.testPassed();
                    return;
                }
                
                Log.SUM("\nDisable one node for 8-node cell & 2 nodes " +
                        "for 16-node cell and will verify sysstat (quorum)");
                
                totalOnlineNode --;
                totalOnlineDisk -= 4;
                
                String [] lsDisableComponent = new String [] {"NODE-108"};
                String [] lsDisks = new String [] {"DISK-106:0"};
                
                if (totalNode == 16) {
                    totalOnlineNode --;
                    totalOnlineDisk -= 4;
                    lsDisableComponent = new String [] {"NODE-108", "NODE-107"};
                }
                
                // 4 disks for 8-node & 8 disks for 16-node cluster
                // disable one node for 8-node cell & 2 nodes for 16-node cell
                if (disableComponent(lsDisableComponent)) {
                    
                    if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                            HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE,
                            HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE))
                        noOfFailure++;
                } else {
                    self.testFailed("problem disabling component - will abort test");
                    return;
                }
                
                Log.SUM("\nDisable one more disk and will verify sysstat (no quorum)");
                totalOnlineDisk --;
                
                if (disableComponent(lsDisks)) {
                    if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                            HoneycombCLISuite.SYSSTAT_CLUSTER_OFFLINE,
                            HoneycombCLISuite.SYSSTAT_SERVICES_OFFLINE))
                        noOfFailure++;
                } else {
                    self.testFailed("problem disabling component - will abort test");
                    return;
                }
                
                Log.SUM("\nEnable one disk and will verify sysstat (quorum)");
                totalOnlineDisk ++;
                
                if (enableComponent(lsDisks)) {
                    if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                            HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE,
                            HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE))
                        noOfFailure++;
                } else {
                    self.testFailed("problem enabling component - will abort test");
                    return;
                }
                
                Log.SUM("\nEnable all disable nodes and will verify sysstat (quorum)");
                totalOnlineNode = totalNode;
                totalOnlineDisk = totalNode * 4;
                
                if (enableComponent(lsDisableComponent)) {
                    if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                            HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE,
                            HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE))
                        noOfFailure++;
                } else {
                    self.testFailed("problem enabling component - will abort test");
                    return;
                }
                
                
                Log.SUM("\nDisable 4 disks for 8-node cell & 8 disks " +
                        "for 16-node cell and will verify sysstat (quorum)");
                lsDisableComponent = new String [] {"DISK-108:0", "DISK-107:1",
                "DISK-106:2","DISK-105:3"};
                totalOnlineDisk -= 4;
                
                if (totalNode == 16) {
                    totalOnlineDisk -= 4;
                    lsDisableComponent = new String [] {"DISK-116:0", "DISK-115:1",
                    "DISK-114:2", "DISK-113:3", "DISK-112:0",
                    "DISK-111:1", "DISK-110:2", "DISK-109:3"};
                }
                
                if (disableComponent(lsDisableComponent)) {
                    
                    if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                            HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE,
                            HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE))
                        noOfFailure++;
                } else {
                    self.testFailed("problem disabling component - will abort test");
                    return;
                }
                
                Log.SUM("\nDisable one more disk and will verify sysstat (no quorum)");
                totalOnlineDisk --;
                
                if (disableComponent(lsDisks)) {
                    if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                            HoneycombCLISuite.SYSSTAT_CLUSTER_OFFLINE,
                            HoneycombCLISuite.SYSSTAT_SERVICES_OFFLINE))
                        noOfFailure++;
                } else {
                    self.testFailed("problem disabling component - will abort test");
                    return;
                }
                
                Log.SUM("\nEnable one disk and will verify sysstat (quorum)");
                totalOnlineDisk ++;
                
                if (enableComponent(lsDisks)) {
                    if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                            HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE,
                            HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE))
                        noOfFailure++;
                } else {
                    self.testFailed("problem enabling component - will abort test");
                    return;
                }
                
                Log.SUM("\nEnable all disks and will verify sysstat (quorum)");
                totalOnlineNode = totalNode;
                totalOnlineDisk = totalNode * 4;
                
                if (enableComponent(lsDisableComponent)) {
                    if (!verifySysstat(cellid, totalOnlineNode, totalOnlineDisk,
                            HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE,
                            HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE))
                        noOfFailure++;
                } else {
                    self.testFailed("problem enabling component - will abort test");
                    return;
                }
            }
            
            if (noOfFailure == 0)
                isTestPass = true;
            else
                isTestPass = false;
            
        } catch (Throwable t) {
            isTestPass = false;
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void test_A_SysstatNegativeTest() {
        
        TestCase self = createTestCase("CLI_sysstat_Negative_Test");
        
        self.addTag(Tag.NEGATIVE);
        self.addTag(HoneycombTag.CLI);
        
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
        
        // invalid interval
        String [] invalidInterval = getAllInvalidInteger();
        
        // valid interval
        String validInterval = "10";
        
        // will test each command with invalid interval
        String [] lsParams = {
            SYSSTAT_I_COMMAND,
            SYSSTAT_INTERVAL_COMMAND
        };
        
        String [] lsInvalidArgs = {
            HoneycombCLISuite.SYSSTAT_COMMAND + " " + HoneycombCLISuite.SYSSTAT_COMMAND,
            HoneycombCLISuite.SYSSTAT_COMMAND + " --i " + validInterval,
            HoneycombCLISuite.SYSSTAT_COMMAND + " -I " + validInterval,
            HoneycombCLISuite.SYSSTAT_COMMAND + " --Interval " + validInterval,
            HoneycombCLISuite.SYSSTAT_COMMAND + " -interval " + validInterval,
        };
        
        // will test with invalid cellid
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.SYSSTAT_COMMAND,
            SYSSTAT_I_COMMAND + " " + validInterval,
            SYSSTAT_INTERVAL_COMMAND + " " + validInterval
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        // test with inavlid interval
        for (int i=0; i<lsParams.length; i++) {
            
            for (int j=0; j<invalidInterval.length; j++) {
                
                String invalidArg = lsParams[i] + " " + invalidInterval[j];
                
                // execute command with invalid interval (without cellid)
                isTestPass = verifyCommandStdout(false, invalidArg,
                        null);
                
                if (!isTestPass)
                    noOfFailure++;
                
                // execute command with invalid interval (with cellid)
                setCellid();
                isTestPass = verifyCommandStdout(true, invalidArg,
                        null);
                
                if (!isTestPass)
                    noOfFailure++;
            }
        }
        
        // test with invalid args
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            isTestPass = verifyCommandStdout(false, lsInvalidArgs[i],
                    null);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute command with invalid interval (with cellid)
            setCellid();
            isTestPass = verifyCommandStdout(true, lsInvalidArgs[i],
                    null);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        // execute command with invalid cellid
        int invalidCellId = getInvalidCellid();
        
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
            String formattedCommand = formatCommandWithCellid(
                    lsInvalidMulticellArgs[i], invalidCellId);
            
            isTestPass = verifyCommandStdout(false, formattedCommand, null);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    /**
     *  Test of sysstat command not specifying the cell id
     *  All commands should work for single cell. For a
     *  multicell hive only the basic sysstat command without
     *  command options will work correctly. Otherwise get the
     *  standard error message.
     */
    public void test_B_SysstatNoCellid() {
        
        TestCase self = null;
        
        try {
            int totalNode = getNodeNum();
            
            String [] lsTestCase = {"CLI_sysstat_nocellid", "CLI_sysstat_v"};
            int verboseTestIndex = 1; // -v flag without -c on multicell
            // should return an error
            
            String [] lsCommand = {HoneycombCLISuite.SYSSTAT_COMMAND,
            SYSSTAT_V_COMMAND};
            
            BufferedReader output = null;
            
            for (int i=0; i<lsTestCase.length; i++) {
                
                self = createTestCase(lsTestCase[i]);
                self.addTag(HoneycombTag.CLI);
                // self.addTag(Tag.REGRESSION); // add me!
                if (self.excludeCase()) continue;
                
                String line = null;
                
                // Re-get the current state of the cluster for each test
                ArrayList allCellid = getAllCellid();
                CellStatus[] cellStatus = new CellStatus[allCellid.size()];
                
                if (i != verboseTestIndex) {
                    Log.INFO("Setup: Executing <" +
                            HoneycombCLISuite.SYSSTAT_COMMAND +
                            " -c> and <" + HoneycombCLISuite.HWSTAT_COMMAND +
                            " -c> commands to get expected output");
                }
                
                for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
                    
                    int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                    
                    cellStatus[cellCount] = new CellStatus(cellid,
                            totalNode, totalNode*4);
                    
                    // Don't need sysstat -c output for verbose test
                    if (i == verboseTestIndex) {
                        continue;
                    }
                    
                    try {
                        // Get output from sysstat -c ### command
                        cellStatus[cellCount].cellOutputLines =
                                getCellSysstat(cellStatus[cellCount]);

                        // Get output from hwstat -c ### command
                        int nodeCount = getHwStatInfo(cellStatus[cellCount]);
                        
                        // Make sure have info on all nodes
                        if (nodeCount != totalNode) {
                            Log.ERROR("Test setup failed. Invalid number of " +
                                    "nodes detected: " + nodeCount +
                                    "\nExpected: " + totalNode);
                            self.testFailed();
                            return;
                            
                        }
                    } catch (Exception ex) {
                        Log.ERROR("IO Error accessing CLI:" +
                                Log.stackTrace(ex));
                        self.testFailed();
                        return;
                    }
                    
                }   // end for
                
                if (i != verboseTestIndex) {
                    Log.INFO("Setup: complete");
                }
                
                boolean isTestPass = false;
                
                ArrayList lines = new ArrayList();
                try {
                    output = runCommandWithoutCellid(
                            lsCommand[i]);
                    // read output
                    while ((line = output.readLine()) != null) {
                        lines.add(line);
                    }
                    output.close();
                    output = null;
                    
                    if (i == verboseTestIndex) {
                        // The sysstat -v command only works on single cell
                        isTestPass = verifySysstatVerboseNoCellid(
                                lsCommand[i], lines, cellStatus);
                    } else {
                        isTestPass = verifySysstatNoCellid(
                                lsCommand[i], lines, cellStatus,
                                HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE,
                                HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE);
                    }
                } catch (Throwable t) {
                    Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
                    isTestPass = false;
                } finally {
                    if (output != null) {
                        try {
                            output.close();
                        } catch (Exception ex) {
                            // ignore
                        }
                        output = null;
                    }
                }
                
                if (isTestPass)
                    self.testPassed();
                else {
                    self.testFailed();
                }
            }   // end for
            
        } catch (Throwable t) {
            if (self != null) {
                self.testFailed();
            }
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
        
    }
    
    /*
     * Get output from hwstat -c ### command
     * Information used to initialize node and disk counts and status
     */
    private int getHwStatInfo(CellStatus curCell) throws Throwable {
        int nodeCount = 0;
        String line = null;
        BufferedReader output = null;
        
        // Reset counters
        curCell.offlineNodes = 0;
        curCell.offlineDisks = 0;
        curCell.missingDisks = 0;
        curCell.onlineNodes = curCell.totalNodes;
        curCell.onlineDisks = curCell.totalDisks;
        
        try {
            output = runCommandWithCellid(
                    HoneycombCLISuite.HWSTAT_COMMAND, curCell.cellid);
            // read output
            while ((line = output.readLine()) != null) {
                if (line.trim() != "") {
                    if (line.contains("NODE-")) {
                        nodeCount++;
                        
                        if (!line.contains("ONLINE")) {
                            curCell.offlineNodes++;
                            curCell.onlineNodes--;
                            curCell.missingDisks += 4;
                            curCell.onlineDisks -= 4;
                        }
                    } else if (line.contains("DISK-")) {
                        
                        if (!line.contains("ENABLED")) {
                            curCell.offlineDisks++;
                            curCell.onlineDisks--;
                        }
                    }
                }
            }
        } finally {
            output.close();
            output = null;
        }
        return nodeCount;
        
    }
    /*
     *  Executes the sysstat -c ### command and returns the output lines
     */
    private ArrayList getCellSysstat(CellStatus cellStatus) throws Throwable {
        String line = null;
        BufferedReader output = null;
        ArrayList sysstatOutput = new ArrayList();
        
        // Get output from sysstat -c ### command
        try {
            output = runCommandWithCellid(
                    HoneycombCLISuite.SYSSTAT_COMMAND, cellStatus.cellid);
            // read output
            while ((line = output.readLine()) != null) {
                if (line.trim() != "") {
                    sysstatOutput.add(line);
                }
            }
        } finally {
            output.close();
            output = null;
        }
        
        return sysstatOutput;
        
    }
    private boolean verifySysstat(int cellid,
            int totalOnlineNode, int totalOnlineDisk,
            String actClusterState, String actDataServiceState) {
        
        int errors = 0;
        
        String actNodeDiskState = totalOnlineNode + " nodes online, " +
                totalOnlineDisk + " disks online";
        
        ArrayList lines = null;
        try {
            lines = readSysstat(cellid);
        } catch (Throwable t) {
            Log.ERROR("Error accessing sysstat:" + Log.stackTrace(t));
            return false;
        }
        
        String cluster_state = (String)lines.get(
                HoneycombCLISuite.SYSSTAT_CLUSTER_STATE_LINE);
        String nodeDiskState = (String)lines.get(
                HoneycombCLISuite.SYSSTAT_NODE_DISK_STATE_LINE);
        String services_state = (String)lines.get(
                HoneycombCLISuite.SYSSTAT_SERVICES_STATE_LINE);
        
        if (!cluster_state.contains(actClusterState)) {
            errors++;
            Log.ERROR("Unexpected cluster state: " + cluster_state +
                    "\nExpected: " + actClusterState);
        } else
            Log.INFO(cluster_state);
        
        if (!nodeDiskState.contains(actNodeDiskState)) {
            errors++;
            Log.ERROR("Unexpected node/disk state: " + nodeDiskState +
                    "\nExpected: " + actNodeDiskState);
        } else
            Log.INFO(nodeDiskState);
        
        if(!services_state.contains(actDataServiceState)) {
            errors++;
            Log.ERROR("Unexpected Data Service state: " + services_state +
                    "\nExpected: " + actDataServiceState);
        } else
            Log.INFO(services_state);
        
        if (errors == 0)
            return true;
        else
            return false;
    }
    
    /*
     *  Verify sysstat command output when command issued without a cellid
     *  This verifies all expected lines of output. For a multicell
     *  it verifies status information for all cells.
     *
     *  @param commandName sysstat command run which generated the output
     *  @param lines output lines generated from running the command
     *  @param cellStatus arry of cell status information
     *  @param actClusterState expected output string for cluster status info
     *  @param actDataServiceState expected output string for data services
     *
     *  @return true if output is valid for command and cell; false otherewise
     *
     *
     */
    private boolean verifySysstatNoCellid(String commandName,
            ArrayList lines, CellStatus[] cellStatus,
            String actClusterState, String actDataServiceState) {
        
        int numCells = cellStatus.length;
        int errors = 0;
        int curCellId = -1;
        int curCellIndex = -1;
        CellStatus curCellStatus = null;
        ArrayList curCellOutput = null;
        
        try {
            
            String[] lsLine = new String[lines.size()];
            lsLine =  (String[]) lines.toArray(lsLine);
            String line = null;
            
            for (int lineNo=0; lineNo<lsLine.length; lineNo++) {
                line = lsLine[lineNo];
                if (line.trim().equals(""))
                    continue;
                
                /* Cell status output line */
                if (line.startsWith(CELL_ID_TEXT)) {
                    // Check last cell of any missing lines
                    if (curCellStatus != null) {
                        errors += this.printMissingErrors(curCellStatus);
                        errors += this.compareOutput(curCellOutput,
                                curCellStatus);
                    }
                    
                    curCellId = -1;
                    curCellIndex = -1;
                    curCellStatus = null;
                    curCellOutput = new ArrayList();
                    String cellStr = line.substring(5);
                    int pos = cellStr.indexOf(":");
                    if (pos < 0) {
                        errors++;
                        Log.ERROR("Invalid Cell Status Output line: " + line);
                        Log.ERROR("Ignoring output until next cell " +
                                "status output line.");
                        continue;
                    }
                    try {
                        cellStr = cellStr.substring(0, pos);
                        curCellId = Integer.parseInt(cellStr);
                    } catch (NumberFormatException ex) {
                        errors++;
                        Log.ERROR("Unexpected or invalid cell id: : " +
                                line);
                        Log.ERROR("Ignoring output until next cell " +
                                "status output line.");
                        continue;
                    }
                    curCellIndex = findCellId(numCells,
                            curCellId, cellStatus);
                    if (curCellIndex < 0) {
                        errors++;
                        Log.ERROR("Unexpected or invalid cell id: " +
                                line);
                        Log.ERROR("Ignoring output until next cell " +
                                "status output line.");
                        continue;
                    }
                    
                    curCellStatus = cellStatus[curCellIndex];
                    curCellOutput.add(line);
                    if (false ==
                            curCellStatus.foundOutput[CELL_ID_INDEX]) {
                        curCellStatus.foundOutput[CELL_ID_INDEX]
                                = true;
                        if (!line.contains(
                               HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE)) {
                            Log.ERROR("Unexpected cluster state: " +
                                    line +
                                    "\nExpected: " +
                                    HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE);
                        }
                        Log.INFO(line);
                    } else {
                        Log.ERROR("Cell Status line displayed "
                                + "twice for cell id: "  +
                                cellStr);
                        errors++;
                        continue;
                    }
                    continue;
                }   // end processing Cell status output line
                
                // Save output line
                curCellOutput.add(line);
                // If don't have a valid cell id we can't verify the output
                // so skip
                if (curCellStatus == null) {
                    errors++;
                    Log.ERROR("No current cell id, ignoring line: " + line);
                    continue;
                }
                
                /* Nodes Disk Line */
                if (line.contains(NODES_ONLINE_TEXT) &&
                        line.contains(DISKS_ONLINE_TEXT)) {
                    if (!curCellStatus.foundOutput[NODES_DISKS_INDEX]) {
                        curCellStatus.foundOutput[NODES_DISKS_INDEX] = true;
                        Log.INFO(line);
                        if (!line.contains(outputLines[NODES_DISKS_INDEX])){
                            // Warning only; more nodes/disks may have come 
                            // online. Will be checked against sysstat -c later
                            Log.WARN(outputLinesTitle[NODES_DISKS_INDEX] +
                                    " line, number of disks or nodes online" +
                                    " does not match values obtained from" +
                                    " <hwstat> command.\nExpected output: " +
                                    outputLines[NODES_DISKS_INDEX]);
                        }
                    } else {
                        errors++;
                        Log.ERROR("Nodes and Disk status line displayed " +
                                "twice for this cell id: " + line);
                    }
                    continue;
                }
                
                /* VIP Line */
                boolean dataVIP = line.contains(DATA_VIP_TEXT);
                boolean adminVIP = line.contains(ADMIN_VIP_TEXT);
                if (dataVIP || adminVIP) {
                    if (!adminVIP) {
                        errors++;
                        Log.ERROR("Missing Admin VIP in output line: " +
                                line);
                    } else if (!dataVIP) {
                        errors++;
                        Log.ERROR("Missing Data VIP in output line: " +
                                line);
                    } else if (!curCellStatus.foundOutput[VIP_INDEX]) {
                        Log.INFO(line);
                        curCellStatus.foundOutput[VIP_INDEX] = true;
                    } else {
                        errors++;
                        Log.ERROR("Data/Admin VIP status line displayed " +
                                "twice for this cell id: " + line);
                    }
                    
                    continue;
                }
                
                /* Data Services Line */
                if (line.startsWith(outputLines[DATA_SERVICES_INDEX])) {
                    if(!line.contains(
                            HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE)) {
                        errors++;
                        Log.ERROR("Unexpected Data Service state: " + line +
                                "\nExpected: " +
                                HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE);
                    } else if (
                            curCellStatus.foundOutput[DATA_SERVICES_INDEX]) {
                        errors++;
                        Log.ERROR("Services status line displayed " +
                                "twice for this cell id: " + line);
                    } else {
                        curCellStatus.foundOutput[DATA_SERVICES_INDEX] = true;
                        Log.INFO(line);
                    }
                    continue;
                }
                
                /* Data Integrity */
                if (line.startsWith(outputLines[DATA_INTEGRITY_INDEX])) {
                    if (curCellStatus.foundOutput[DATA_INTEGRITY_INDEX]) {
                        errors++;
                        Log.ERROR("Data Integrity status line displayed " +
                                "twice for this cell id: " + line);
                    } else {
                        curCellStatus.foundOutput[DATA_INTEGRITY_INDEX] = true;
                        Log.INFO(line);
                    }
                    continue;
                }
                
                /* Data Reliability */
                if (line.startsWith(outputLines[DATA_RELIABLITY_INDEX])) {
                    if (curCellStatus.foundOutput[DATA_RELIABLITY_INDEX]) {
                        errors++;
                        Log.ERROR("Data Reliability status line displayed " +
                                "twice for this cell id: " + line);
                    } else {
                        curCellStatus.foundOutput[DATA_RELIABLITY_INDEX]
                                = true;
                        Log.INFO(line);
                    }
                    continue;
                }
                
                /* Query Integrity */
                if (line.startsWith(outputLines[QUERY_INTEGRITY_INDEX])) {
                    if (curCellStatus.foundOutput[QUERY_INTEGRITY_INDEX]) {
                        errors++;
                        Log.ERROR("Query Integrity status line displayed " +
                                "twice for this cell id: " + line);
                    } else {
                        curCellStatus.foundOutput[QUERY_INTEGRITY_INDEX] = true;
                        Log.INFO(line);
                    }
                    continue;
                }
                
                /* NDMP Status */
                if (line.startsWith(outputLines[NDMP_INDEX])) {
                    if (curCellStatus.foundOutput[NDMP_INDEX]) {
                        errors++;
                        Log.ERROR("NDMP status line displayed " +
                                "twice for this cell id: " + line);
                    } else {
                        curCellStatus.foundOutput[NDMP_INDEX] = true;
                        Log.INFO(line);
                    }
                    continue;
                }
                
                // Skip output lines
                Log.INFO("Unexpected output, ignoring line : " + line);
                
            }   // end for
            
            // Check last cell for any missing lines
            if (curCellStatus != null && curCellIndex != -1) {
                errors += this.printMissingErrors(curCellStatus);
                errors += this.compareOutput(curCellOutput,
                        curCellStatus);
            }
            
            // Check for missing cells
            for (int i = 0; i < numCells; i++) {
                if (!cellStatus[i].foundCellIdOutput) {
                    errors++;
                    Log.ERROR("Missing output for cell id: " +
                            cellStatus[i].cellid);
                }
            }
            if (errors == 0)
                return true;
            else
                return false;
            
        } catch (Exception e) {
            Log.ERROR("Error while parsing " + commandName + " stdout: "
                    + Log.stackTrace(e));
            return false;
        }
    }
    
    /*
     *  Prints out any missing lines from the expected sysstat output
     *  @param cellStatus cell status information for the current cell
     *
     *  @return int the number of errors (ie. lines missing)
     */
    private int printMissingErrors(CellStatus cellStatus) {
        int numMissing = 0;
        for (int i = 0; i < NO_OUTPUT_LINES; i++) {
            if (!cellStatus.foundOutput[i]) {
                Log.ERROR("Missing " + outputLinesTitle[i] +
                        " for cell id: " + cellStatus.cellid);
                numMissing++;
            }
        }
        return numMissing;
    }
    
    /*
     *  Compares the "sysstat" output received with the "sysstat -c" output
     *  for the same cell id
     *  @param noCellidOutput the output lines received from "sysstat" command
     *  @param cellStatus cell status information for the current cell
     *
     *  @return int the number of errors
     */
    private int compareOutput(ArrayList noCellidOutput, CellStatus cellStatus) {
        int numErrors = 0;
        int numLines = cellStatus.cellOutputLines.size();
        int linesFound = noCellidOutput.size();
        String cellidSysstatCmd = "<" + HoneycombCLISuite.SYSSTAT_COMMAND +
                " -c " + cellStatus.cellid + ">";
        String sysstatCmd = "<" + HoneycombCLISuite.SYSSTAT_COMMAND + ">";

        String[] expectedOutput = new String[numLines];
        expectedOutput = (String[]) cellStatus.
                cellOutputLines.toArray(expectedOutput);
        String[] foundOutput = new String[linesFound];
        foundOutput = (String[]) noCellidOutput.toArray(foundOutput);
        
        Log.INFO("Verifying " + sysstatCmd + " output matches the expected "
                + "output from " + cellidSysstatCmd);
        
        if (linesFound != numLines) {
            numErrors++;
            Log.ERROR("Mismatched output for cell id: " + cellStatus.cellid +
                    "\nNumber of output lines found: " + linesFound +
                    " Number of output lines expected: " + numLines);
            numLines = (numLines > linesFound) ? linesFound : numLines;
        }
        
        int i = 0;
        boolean retry = false;
        
        while (i < numLines) {
            try{
                if (expectedOutput[i].compareTo(foundOutput[i]) == 0) {
                    // exact match
                    retry = false;
                    continue;
                } else {
                    // check if just differs by date
                    String eOutput = expectedOutput[i];
                    String fOutput = foundOutput[i];
                    int fLen = fOutput.length();
                    int eLen = eOutput.length();
                    if (eLen > DATE_STRING_LEN && fLen > DATE_STRING_LEN) {
                        eOutput = eOutput.substring(0, eLen - DATE_STRING_LEN);
                        fOutput = fOutput.substring(0, fLen - DATE_STRING_LEN);
                        if ((eOutput.compareTo(fOutput) == 0) &&
                                expectedOutput[i].substring(
                                eLen - DATE_STRING_LEN).contains("UTC")) {
                            // OK just differs by date/timestamp
                            retry = false;
                            continue;
                        }
                    }
                    
                    // If this is the first time comparing this line of output
                    // then get the sysstat -c output and retry comparison
                    if (!retry) {
                        ArrayList updatedOutput = null;
                        try {
                            Log.WARN("Unexpected " + sysstatCmd + " output: " +
                                    foundOutput[i] + "\nExpected output: " + 
                                    expectedOutput[i] + 
                                    "\nGetting up-to-date expected output" +
                                    " using " + cellidSysstatCmd + " command");
                            updatedOutput = getCellSysstat(cellStatus);
                        } catch (Throwable t) {
                            numErrors++;
                            Log.ERROR("Error retrying "+ cellidSysstatCmd + 
                                    " command");
                        }
                        // Update the expected output with latest info
                        if (updatedOutput != null && 
                                updatedOutput.size() == numLines) {
                            expectedOutput = (String[]) 
                                updatedOutput.toArray(expectedOutput);
                            retry = true;
                            continue;
                        }
                    }
                    
                    // Error if reach here
                    Log.ERROR("Unexpected " + sysstatCmd + " output: " +
                            foundOutput[i] + "\nExpected output: "  +
                            expectedOutput[i]);
                    numErrors++;
                    retry = false;
                }
            } finally {
                if (!retry) {
                    // Check next output line
                    i++;
                }
            }
        }   // end while
        
        return numErrors;
    }
    
    /*
     *  Locates the cell id in the cell status array and sets the correct
     *  expected output text for the nodes/disks status line
     *
     *  @param numCells number of cells
     *  @param cellStatus array of cell status information
     *
     *  @return int the cell id's index in the cell status array
     *
     */
    private int findCellId(int numCells, int curCellId,
            CellStatus[] cellStatus) {
        int idIndex = -1;
        for (int i = 0; i < numCells; i++) {
            if (cellStatus[i].cellid == curCellId) {
                cellStatus[i].foundCellIdOutput = true;
                idIndex = i;
                outputLines[NODES_DISKS_INDEX] = "" +
                        cellStatus[i].onlineNodes +
                        " nodes online, " +
                        cellStatus[i].onlineDisks +
                        " disks online";
            }
        }   // end for
        return idIndex;
    }
    
    /*
     *  Verify sysstat -v command output when command issued without a cellid
     *
     *  @param commandName sysstat command run which generated the output
     *  @param lines output lines generated from running the command
     *  @param cellStatus arry of cell status information
     *
     *  @return true if output is valid for command and cell; false otherewise
     *
     */
    private boolean verifySysstatVerboseNoCellid(String commandName,
            ArrayList lines, CellStatus[] cellStatus) {
        
        int errors = 0;
        int outputLines = 0;
        boolean multiCell = isMultiCell();
        boolean foundMultiCellUsage = false;
        int nodeCount = 0;
        int diskCount = 0;
        int nodeOfflineCount = 0;
        
        try {
            
            String[] lsLine = new String[lines.size()];
            lsLine =  (String[]) lines.toArray(lsLine);
            String line = null;
            
            for (int lineNo=0; lineNo<lsLine.length; lineNo++) {
                line = lsLine[lineNo];
                if (line.trim().equals(""))
                    continue;
                
                outputLines++;
                if (multiCell) {
                    if (line.startsWith(
                            HoneycombCLISuite.MULTICELL_USAGE_MESSAGE[0])) {
                        Log.INFO(line);
                        foundMultiCellUsage = true;
                    } else {
                        Log.WARN("Unexpected output from command <" +
                                commandName + ">: \n" + line + "\n");
                    }
                    continue;
                }
                
                // Check for NODE and Disk output lines
                if (line.startsWith("NODE-")) {
                    nodeCount++;
                    if (!line.contains("ONLINE")) {
                        nodeOfflineCount++;
                    }
                } else if (line.startsWith("Disk")) {
                    diskCount++;
                } else {
                    Log.WARN("Unexpected output from command <" +
                            commandName + ">: \n" + line + "\n");
                }
                
            }   // end for
            
            // Verify some output was received
            if (multiCell) {
                if (!foundMultiCellUsage) {
                    errors++;
                    Log.ERROR("Missing expected output from command <" +
                            commandName + ">: \n" +
                            HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);
                }
            } else {
                if (nodeCount != cellStatus[0].totalNodes) {
                    errors++;
                    Log.ERROR("Incorrect number of nodes detected in output of "
                            + "command <" +  commandName + ">: \n" +
                            "Expected: " + cellStatus[0].totalNodes +
                            " Found: " + nodeCount);
                }
                if (diskCount + (nodeOfflineCount*4) != cellStatus[0].totalDisks) {
                    errors++;
                    Log.ERROR("Incorrect number of disks detected in output of "
                            + "command <" + commandName + ">: \n" +
                            "Expected: " + cellStatus[0].totalDisks +
                            " Found: " + diskCount);
                }
            }
        } catch (Exception ex) {
            Log.ERROR("Error while parsing " + commandName + " stdout: "
                    + Log.stackTrace(ex));
            return false;
            
        }
        
        if (outputLines == 0 && errors == 0) {
            errors++;
            Log.ERROR("No output for command " + commandName);
        }
        
        if (errors == 0) {
            Log.INFO("Output of <" + commandName +
                    "> command is displayed properly");
            return true;
        } else {
            return false;
        }
        
    }
    
    private boolean disableComponent(String [] lsComponent) {
        try {
            for (int i=0; i<lsComponent.length; i++) {
                String component = lsComponent[i];
                Log.INFO("Disable component: " + component);
                
                // disable the component
                String command = HoneycombCLISuite.HWCFG_COMMAND + " -D ";
                BufferedReader output = runCommand(command + component);
                
                String line = null;
                while ((line = output.readLine()) != null) {
                    Log.DEBUG(command + " output: " + line);
                }
                output.close();
                
                HCUtil.doSleep(60, "wait to complete disable node/disk operation");
                
                // verify the disable state of component
                String hwstatline = getCommandStdout(isMultiCell(),
                        HoneycombCLISuite.HWSTAT_COMMAND, component);
                if (hwstatline == null) {
                    Log.ERROR("No line has '" + component +
                            "', retrying hwstat");
                    hwstatline = getCommandStdout(isMultiCell(),
                            HoneycombCLISuite.HWSTAT_COMMAND, component);
                    if (hwstatline == null) {
                        Log.ERROR("No line has '" + component +
                                "', failing test");
                        return false;
                    }
                }
                Log.INFO(hwstatline);
                
                if ((!hwstatline.contains("OFFLINE")) && (!hwstatline.contains("DISABLED"))) {
                    Log.ERROR("Unable to disable the component: " + component);
                    Log.WARN("Won't verify sysstat output");
                    return false;
                } else
                    Log.INFO("Component " + component + " is disabled successfully");
            }
        } catch (Throwable t) {
            Log.ERROR("Error while disabling component:" + Log.stackTrace(t));
            return false;
        }
        
        return true;
    }
    
    private boolean enableComponent(String [] lsComponent) {
        try {
            for (int i=0; i<lsComponent.length; i++) {
                String component = lsComponent[i];
                Log.INFO("Enable component: " + component);
                
                // disable the component
                String command = HoneycombCLISuite.HWCFG_COMMAND + " -E ";
                BufferedReader output = runCommand(command + component);
                
                String line = null;
                while ((line = output.readLine()) != null) {
                    Log.DEBUG(command + " output: " + line);
                }
                output.close();
                
                String [] lsDisk = null;
                
                if (lsComponent[i].indexOf("NODE") != -1) {
                    String [] tokeinizedNode = tokenizeIt(lsComponent[i],"-");
                    String nodeid = tokeinizedNode[1];
                    
                    lsDisk = new String [5];
                    lsDisk[0] = "DISK-" + nodeid + ":-1";
                    for (int disknum=0; disknum<4; disknum++)
                        lsDisk[disknum+1] = "DISK-" + nodeid + ":" + disknum;
                } else {
                    lsDisk = new String [] {lsComponent[i]};
                }
                
                int sleep = 3600;  // 1 hour
                int sleepInterval = 120;  // 2 minutes
                int sleepCurrent = 0;
                boolean isDiskDisabled = true;
                
                while ((sleepCurrent != sleep) && (isDiskDisabled)){
                    HCUtil.doSleep(sleepInterval,
                            "wait to complete enable node/disk operation");
                    sleepCurrent += sleepInterval;
                    isDiskDisabled = false;
                    
                    for (int count=0; count<lsDisk.length; count++) {
                        String diskStatline = getCommandStdout(isMultiCell(),
                                HoneycombCLISuite.HWSTAT_COMMAND, lsDisk[count]);
                        
                        if ((lsComponent[i].indexOf("NODE") != -1) &&
                                (count == 0) && (diskStatline == null))
                            continue;
                        if (diskStatline == null) {
                            Log.WARN("Got null disk status: node is down?");
                            continue;
                        }
                        Log.INFO(diskStatline);
                        if (!diskStatline.contains("ENABLED")) {
                            isDiskDisabled = true;
                            break;
                        }
                    }
                }
                
                if (isDiskDisabled) {
                    Log.ERROR("Unable to enable the component: " + component);
                    Log.WARN("Won't verify sysstat output");
                    return false;
                } else
                    Log.INFO("Component " + component + " is enabled successfully");
            }
        } catch (Throwable t) {
            Log.ERROR("Error while enabling component:" + Log.stackTrace(t));
            return false;
        }
        
        return true;
    }
}
