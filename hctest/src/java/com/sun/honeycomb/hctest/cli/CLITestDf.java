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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.ArrayList;

/**
 *
 * @author jk142663
 */
public class CLITestDf extends HoneycombCLISuite {
    
    private static String DF_H_STRING = null;
    private static String DF_HUMAN_READABLE_STRING = null;
    private static String DF_P_STRING = null;
    private static String DF_PHYSICAL_STRING = null;
    
    private static String DF_H_COMMAND = null;
    private static String DF_HUMAN_READABLE_COMMAND = null;
    private static String DF_P_COMMAND = null;
    private static String DF_PHYSICAL_COMMAND = null;
    private static String DF_P_H_COMMAND = null;
    private static String DF_H_P_COMMAND = null;
    private static String DF_P_HUMAN_READABLE_COMMAND = null;
    private static String DF_PHYSICAL_H_COMMAND = null;
    private static String DF_PHYSICAL_HUMAN_READABLE_COMMAND = null;
    
    private static final String blocksOutputStr = "All sizes expressed in 1K blocks";
    private static final String multicellContactStr = "Contacting all cells, please wait.";
    private static final String multicellAllCellStr = "All Cells:";
    private static final String multicellCellStr = "Cell ";
    private String clusterIP = null;
    private CLI cli = null;
    
    // gather data - ie: log into cluster node, do a "df" there,
    // and parse the output. we need this data to compare with cli "df""
    private HashMap list_of_Disk_df_h = null, list_of_Disk_df_k = null;
    private String total_disk_df_h = null, total_disk_df_k = null;
    
    
    public static String METRIC_TYPE = "(MB|GB|TB|PB)";
    private static final String DF_PHYS_PATTERN_STR =
            "Total:\\p{Zs}+([^\\s]+) " + METRIC_TYPE
            + "; Avail:\\p{Zs}+([^\\s]+) " + METRIC_TYPE
            + "; Used:\\p{Zs}+([^\\s]+) " + METRIC_TYPE
            + "; Usage:\\p{Zs}+([^\\s]+)%";
    
    private static final String DF_BLOCK_PATTERN_STR =
            "Total:\\p{Zs}+([^\\s]+)"
            + "; Avail:\\p{Zs}+([^\\s]+)"
            + "; Used:\\p{Zs}+([^\\s]+)"
            + "; Usage:\\p{Zs}+([^\\s]+)%";
    public static final Pattern DF_SYS_PATTERN =
            Pattern.compile("^" + DF_PHYS_PATTERN_STR + "$");
    
    public static final Pattern DF_SYS_BLOCK_PATTERN =
            Pattern.compile("^" + DF_BLOCK_PATTERN_STR + "$");
    public static final Pattern DF_DISK_PATTERN =
            Pattern.compile("^DISK-([^\\s]+)"
            + ": " + DF_PHYS_PATTERN_STR + "$");
    
    public static final Pattern DF_DISK_BLOCK_PATTERN =
            Pattern.compile("^DISK-([^\\s]+)"
            + ": " + DF_BLOCK_PATTERN_STR + "$");
    
    /** Creates a new instance of CLITestDf */
    public CLITestDf() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: df\n");
        sb.append("\t o positive test - output syntax\n");
        sb.append("\t o negative test - invalid option\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        
        super.setUp();
        
        DF_H_STRING = " --" + getAdminResourceProperties().
                getProperty("cli.df.human_readable_char") + " ";
        DF_HUMAN_READABLE_STRING = " --" + getAdminResourceProperties().
                getProperty("cli.df.human_readable_name") + " ";
        DF_P_STRING = " -" + getAdminResourceProperties().
                getProperty("cli.df.physical_char") + " ";
        DF_PHYSICAL_STRING = " --" + getAdminResourceProperties().
                getProperty("cli.df.physical_name") + " ";
        
        DF_H_COMMAND = HoneycombCLISuite.DF_COMMAND + DF_H_STRING;
        DF_HUMAN_READABLE_COMMAND =
                HoneycombCLISuite.DF_COMMAND + DF_HUMAN_READABLE_STRING;
        DF_P_COMMAND = HoneycombCLISuite.DF_COMMAND + DF_P_STRING;
        DF_PHYSICAL_COMMAND =
                HoneycombCLISuite.DF_COMMAND + DF_PHYSICAL_STRING;
        DF_P_H_COMMAND = HoneycombCLISuite.DF_COMMAND + DF_P_STRING + DF_H_STRING;
        DF_H_P_COMMAND = HoneycombCLISuite.DF_COMMAND + DF_H_STRING + DF_P_STRING;
        DF_P_HUMAN_READABLE_COMMAND =
                HoneycombCLISuite.DF_COMMAND + DF_P_STRING + DF_HUMAN_READABLE_STRING;
        DF_PHYSICAL_H_COMMAND =
                HoneycombCLISuite.DF_COMMAND + DF_PHYSICAL_STRING + DF_H_STRING;
        DF_PHYSICAL_HUMAN_READABLE_COMMAND =
                HoneycombCLISuite.DF_COMMAND + DF_PHYSICAL_STRING + DF_HUMAN_READABLE_STRING;
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
        if (errorString == null)
            return false;
        errorString = errorString.toLowerCase();
        if (errorString.indexOf(INVALID_CELLID_MESSAGE) != -1)
            return true;
        
        return super.isInErrorExclusionList(errorString);
    }
    
    public void testDfSyntax() {
        String [] lsTestCase = {"CLI_df", "CLI_df_h", "CLI_df_human-readable"};
        
        String [] lsCommand = {HoneycombCLISuite.DF_COMMAND, DF_H_COMMAND,
        DF_HUMAN_READABLE_COMMAND};
        
        boolean [] lsDfInBlock = {true, false, false};
        
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i]);
            
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!
            
            if (self.excludeCase()) return;
            
            boolean isTestPass = false;
            
            try {
                BufferedReader output = runCommandWithoutCellid(lsCommand[i]);
                String lsLine = HCUtil.readLines(output);
                output.close();
                if (isMultiCell()) {
                    isTestPass = checkDfStdoutMulticell(lsCommand[i], lsLine,
                            lsDfInBlock[i]);
                } else {
                    isTestPass = checkDfStdout(lsCommand[i], lsLine, 
                                               lsDfInBlock[i]);
                }
                
            } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
            }
            
            if (isTestPass)
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testDfSyntaxWithCellid() {
        String [] lsTestCase = {"CLI_df_with_cellid", "CLI_df_h_with_cellid",
        "CLI_df_human-readable_with_cellid"};
        
        String [] lsCommand = {HoneycombCLISuite.DF_COMMAND, DF_H_COMMAND,
        DF_HUMAN_READABLE_COMMAND};
        
        boolean [] lsDfInBlock = {true, false, false};
        
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i]);
            
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!
            
            
            if (self.excludeCase()) return;
            
            boolean isTestPass = false;
            
            try {
                ArrayList allCellid = getAllCellid();
                int noOfFailure = 0;
                
                for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
                    
                    int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                    setCellid(cellid);
                    
                    BufferedReader output = runCommandWithCellid(
                            lsCommand[i], cellid);
                    
                    boolean isTestPassTmp = checkDfStdout(lsCommand[i],
                            HCUtil.readLines(output), lsDfInBlock[i]);
                    output.close();
                    
                    if(!isTestPassTmp)
                        noOfFailure++;
                }
                
                if (noOfFailure == 0)
                    isTestPass = true;
                
            } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
            }
            
            if (isTestPass)
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testDfPhysicalSyntax() {
        String [] lsTestCase = {"CLI_df_p", "CLI_df_physical", "CLI_df_p_h",
        "CLI_df_h_p", "CLI_df_p_human-readable", "CLI_df_physical_h",
        "CLI_df_physical_human-readable"};
        
        String [] lsCommand = {DF_P_COMMAND, DF_PHYSICAL_COMMAND, DF_P_H_COMMAND,
        DF_H_P_COMMAND, DF_P_HUMAN_READABLE_COMMAND, DF_PHYSICAL_H_COMMAND,
        DF_PHYSICAL_HUMAN_READABLE_COMMAND};
        
        boolean [] lsDfInBlock = {true, true, false, false, false, false, false};
        
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i]);
            
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!
            
            if (self.excludeCase()) return;
            
            boolean isTestPass = false;
            
            try {
                BufferedReader output = runCommandWithoutCellid(lsCommand[i]);
                
                if (isMultiCell()) {
                    isTestPass = verifyCommandStdout(false,
                            lsCommand[i],
                            HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);
                } else {
                    String lsLine = HCUtil.readLines(output);
                    isTestPass = checkDfPhysicalStdout(lsCommand[i],
                            lsLine, lsDfInBlock[i]);
                }
                output.close();
                
            } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
            }
            
            if (isTestPass)
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testDfPhysicalSyntaxWithCellid() {
        String [] lsTestCase = {"CLI_df_p_with_cellid", "CLI_df_physical_with_cellid",
        "CLI_df_p_h_with_cellid", "CLI_df_h_p_with_cellid",
        "CLI_df_p_human-readable_with_cellid", "CLI_df_physical_h_with_cellid",
        "CLI_df_physical_human-readable_with_cellid"};
        
        String [] lsCommand = {DF_P_COMMAND, DF_PHYSICAL_COMMAND, DF_P_H_COMMAND,
        DF_H_P_COMMAND, DF_P_HUMAN_READABLE_COMMAND, DF_PHYSICAL_H_COMMAND,
        DF_PHYSICAL_HUMAN_READABLE_COMMAND};
        
        boolean [] lsDfInBlock = {true, true, false, false, false, false, false};
        
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i]);
            
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!
            
            if (self.excludeCase()) return;
            
            boolean isTestPass = false;
            
            try {
                ArrayList allCellid = getAllCellid();
                int noOfFailure = 0;
                
                for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
                    
                    int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                    setCellid(cellid);
                    
                    BufferedReader output = runCommandWithCellid(
                            lsCommand[i], cellid);
                    
                    boolean isTestPassTmp = checkDfPhysicalStdout(
                            lsCommand[i], HCUtil.readLines(output), 
                            lsDfInBlock[i]);
                    output.close();
                    
                    if(!isTestPassTmp)
                        noOfFailure++;
                }
                
                if (noOfFailure == 0)
                    isTestPass = true;
                
            } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
            }
            
            if (isTestPass)
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testDfNegativeTest() {
        
        TestCase self = createTestCase("CLI_df_Negative_Test");
        
        self.addTag(Tag.NEGATIVE);
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
        
        String [] lsInvalidArgs = {
            HoneycombCLISuite.DF_COMMAND + " " + HoneycombCLISuite.DF_COMMAND,
            HoneycombCLISuite.DF_COMMAND + " -H",
            HoneycombCLISuite.DF_COMMAND + " --h",
            HoneycombCLISuite.DF_COMMAND + " -P",
            HoneycombCLISuite.DF_COMMAND + " --p",
            HoneycombCLISuite.DF_COMMAND + " --Physical",
            HoneycombCLISuite.DF_COMMAND + " -physical",
            HoneycombCLISuite.DF_COMMAND + " --Human-readable",
            HoneycombCLISuite.DF_COMMAND + " -human-readable",
            DF_H_COMMAND + " -P",
            DF_H_COMMAND + " --p",
            DF_H_COMMAND + " --Physical",
            DF_H_COMMAND + " -physical",
            DF_P_COMMAND + " -H",
            DF_P_COMMAND + " --h",
            DF_P_COMMAND + " --Human-readable",
            DF_P_COMMAND + " -human-readable",
            DF_HUMAN_READABLE_COMMAND + " -P",
            DF_HUMAN_READABLE_COMMAND + " --p",
            DF_HUMAN_READABLE_COMMAND + " --Physical",
            DF_HUMAN_READABLE_COMMAND + " -physical",
            DF_PHYSICAL_COMMAND + " -H",
            DF_PHYSICAL_COMMAND + " --h",
            DF_PHYSICAL_COMMAND + " --Human-readable",
            DF_PHYSICAL_COMMAND + " -human-readable"
        };
        
        // will test with invalid cellid for multicell
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.DF_COMMAND,
            DF_H_COMMAND,
            DF_HUMAN_READABLE_COMMAND,
            DF_P_COMMAND,
            DF_PHYSICAL_COMMAND,
            DF_P_H_COMMAND,
            DF_H_P_COMMAND,
            DF_P_HUMAN_READABLE_COMMAND,
            DF_PHYSICAL_H_COMMAND,
            DF_PHYSICAL_HUMAN_READABLE_COMMAND
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command without cellid
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], null);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute invalid command with cellid
            setCellid();
            isTestPass = verifyCommandStdout(
                    true, lsInvalidArgs[i], null);
            
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
    
    private HashMap parseDfFromClusterNode(String command) {
        HashMap dfHashMap = null;
        
        try {
            BufferedReader output = runCommandAsRoot(clusterIP, "hostname");
            
            // hcb10x
            String masterNode = null;
            
            if ((masterNode = output.readLine()) == null) {
                Log.WARN("Unable to get the master node");
                output.close();
            } else {
                output.close();
                output = runCommandAsRoot(clusterIP, command);
                String line = null;
                dfHashMap = new HashMap();
                
                while ((line = output.readLine()) != null) {
                    Log.DEBUG(command + " output: " + line);
                    
                    String [] tokenizeStr = tokenizeIt(line, " ");
                    String mountedOnColumn = tokenizeStr[tokenizeStr.length-1].trim();
                    
                    // /data/x or /netdisks/10.123.45.10x/data/x
                    if ((mountedOnColumn.startsWith("/data/")) ||
                            (mountedOnColumn.startsWith("/netdisks/"))) {
                        
                        String [] tokenizeTmp = tokenizeIt(mountedOnColumn, "/");
                        
                        // hashKey -> 10x:x:
                        // hashValue -> total avail used perUsed
                        String hashKey = null, hashValue = null;
                        
                        if (mountedOnColumn.startsWith("/netdisks/")) {
                            String [] tokenizeIp = tokenizeIt(tokenizeTmp[1], ".");
                            hashKey = tokenizeIp[tokenizeIp.length-1] + ":" +
                                    tokenizeTmp[tokenizeTmp.length-1] + ":";
                        } else
                            hashKey = masterNode.substring(3, masterNode.length()) +
                                    ":" + tokenizeTmp[tokenizeTmp.length-1] + ":";
                        
                        String total = tokenizeStr[1].trim();
                        String avail = tokenizeStr[3].trim();
                        String used = tokenizeStr[2].trim();
                        String perUsed = tokenizeStr[4].trim();
                        
                        hashValue = total + " " + avail + " " +
                                used + " " + perUsed;
                        
                        Log.DEBUG(hashKey + " " + hashValue);
                        
                        dfHashMap.put(hashKey, hashValue);
                    } else
                        Log.DEBUG("do nothing");
                }
                output.close();
            }
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
        
        return dfHashMap;
    }
    
    // this method will remove the ; from the end of the string
    private String getActualValue(String value) {
        if (value.indexOf(";") != -1) {
            String[] strings = value.split(";");
            value = strings[0];
        }
        
        return value;
    }
    
    // return capacity without ','
    // 124,234,222 -> 124234222
    private String formatCapacity(String value) throws Exception {
        return value.replaceAll(",","");
    }
    
    private boolean checkDfStdout(String commandName,
            String lsStdout, boolean isBlock) {
        boolean isCorrectDf = false, isBlockStdout = false;
        
        try {
            
            String [] lsLine =  tokenizeIt(lsStdout, "\n");
            String line = null;
            
            int noOfFailure = 0;
            
            for (int lineNo=0; lineNo<lsLine.length; lineNo++) {
                line = lsLine[lineNo];
                
                if (line.trim().equals(""))
                    continue;
                
                Log.DEBUG(commandName + " output: " + line);
                
                if (line.equals(blocksOutputStr)) {
                    isBlockStdout = true;
                    continue;
                }

                Matcher matcher;
                if (isBlock)
                    matcher = DF_SYS_BLOCK_PATTERN.matcher(line);
                else
                    matcher = DF_SYS_PATTERN.matcher(line);
                
                // verify the output pattern
                if (!matcher.matches()) {
                    noOfFailure++;
                    Log.ERROR("Unexpected output format: " + line +
                            ", Expected: " + matcher.toString());
                } 
                else {
                    // verify the output content
                    int group = 1;
                    String total = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String avail = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String used = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String perUsed = matcher.group(group);
                    
                    // remove , from the capacity string
                    total = formatCapacity(total);
                    
                    Log.INFO("Total: " + total + " Avail: " + avail +
                            " Used: " + used + " Usage: " + perUsed);
                    
                    // verify total capacity > 0
                    double totalCap = new Double(total).doubleValue();
                    if (totalCap <= 0) {
                        noOfFailure++;
                        Log.ERROR("Unexpected total capacity: " + totalCap);
                    }
                }
            }
            
            if (isBlock) {
                if (!isBlockStdout) {
                    noOfFailure++;
                    Log.ERROR("Missing <" + blocksOutputStr + "> from stdout");
                }
            }
            
            if (noOfFailure == 0)
                isCorrectDf = true;
            
        } catch (Exception e) {
            Log.ERROR("Error while parsing " + commandName + " stdout: "
                    + Log.stackTrace(e));
        }
        
        return isCorrectDf;
    }
    
    private boolean checkDfStdoutMulticell(String commandName,
            String lsStdout, boolean isBlock) {
        boolean isCorrectDf = false, isBlockStdout = false;
        boolean multicell = false;
        boolean isMulticellPleaseWait = false;
        boolean isMulticellAllCells = false;
        boolean isMultiAllCellsUsage = false;
        ArrayList cellIds = getAllCellid();
        int numCells = cellIds.size();
        int multiCellIds[] = new int[numCells];
        boolean multiCellIdFound[] = new boolean[numCells];
        boolean multiCellIdUsage[] = new boolean[numCells];
        int numCellIdsFound = 0;
        int numCellUsageInfo = 0;
        int curCellIndex = -1;
        
        try {
            
            for (int i= 0; i < numCells; i++) {
                multiCellIds[i] = ((Integer) cellIds.get(i)).intValue();
                multiCellIdFound[i] = false;
                multiCellIdUsage[i] = false;
            }
            
            String [] lsLine =  tokenizeIt(lsStdout, "\n");
            String line = null;
            
            int noOfFailure = 0;
            
            for (int lineNo=0; lineNo<lsLine.length; lineNo++) {
                line = lsLine[lineNo];
                
                if (line.trim().equals(""))
                    continue;
                
                Log.DEBUG(commandName + " output: " + line);
                
                if (line.equals(this.multicellContactStr)) {
                    isMulticellPleaseWait = true;
                    continue;
                }

                if (line.equals(blocksOutputStr)) {
                    isBlockStdout = true;
                    continue;
                }
                
                if (line.equals(multicellAllCellStr)) {
                    curCellIndex = -1;
                    if (!isMulticellAllCells) {
                        isMulticellAllCells = true;
                        Log.INFO(multicellAllCellStr);
                    } else {
                        noOfFailure++;
                        Log.ERROR(">" + multicellAllCellStr + "< label " +
                                "displayed twice.");
                    }
                    continue;
                }
                
                if (line.startsWith(this.multicellCellStr)) {
                    String cellStr = line.substring(
                            multicellCellStr.length() -1);
                    cellStr = cellStr.trim();
                    if (cellStr.endsWith(":")) {
                        cellStr = cellStr.substring(0, cellStr.length() -1);
                        try {
                            int testCellId = -2;
                            testCellId = Integer.parseInt(cellStr);
                            for (int i = 0; i < numCells; i++) {
                                if (multiCellIds[i] == testCellId) {
                                    if (multiCellIdFound[i] == false) {
                                        multiCellIdFound[i] = true;
                                        numCellIdsFound++;
                                        curCellIndex = i;
                                        Log.INFO(line);
                                    } else {
                                        Log.ERROR("Cell id label displayed " +
                                                "twice: "  + cellStr);
                                        noOfFailure++;
                                    }
                                    break;
                                }
                            }
                        } catch (Exception ex) {
                            Log.ERROR("Invalid cell id: " + cellStr); 
                            noOfFailure++;
                        }
                    }
                    continue;
                }
                
                Matcher matcher;
                if (isBlock)
                    matcher = DF_SYS_BLOCK_PATTERN.matcher(line);
                else
                    matcher = DF_SYS_PATTERN.matcher(line);
                
                // verify the output pattern
                if (!matcher.matches()) {
                    noOfFailure++;
                    Log.ERROR("Unexpected output format: " + line +
                            ", Expected: " + matcher.toString());
                } else {
                    // verify the output content
                    int group = 1;
                    String total = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String avail = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String used = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String perUsed = matcher.group(group);
                    
                    // remove , from the capacity string
                    total = formatCapacity(total);
                    
                    Log.INFO("Total: " + total + " Avail: " + avail +
                            " Used: " + used + " Usage: " + perUsed);
                    
                    // verify total capacity > 0
                    double totalCap = new Double(total).doubleValue();
                    if (totalCap <= 0) {
                        noOfFailure++;
                        Log.ERROR("Unexpected total capacity: " + totalCap);
                    }
                    
                    if (curCellIndex == -1) {
                        isMultiAllCellsUsage = true;
                    } else if (curCellIndex >= 0) {
                        if (!multiCellIdUsage[curCellIndex]) {
                            numCellUsageInfo++;
                            multiCellIdUsage[curCellIndex] = true;
                        }
                    } else {
                        Log.ERROR("Ignoring disk usage information for " +
                                "invalid cell id.");
                        noOfFailure++;
                    }                    
                }
            }
            
            if (isBlock) {
                if (!isBlockStdout) {
                    noOfFailure++;
                    Log.ERROR("Missing <" + blocksOutputStr + "> from stdout");
                }
            }
            
            if (numCells != numCellIdsFound) {
                StringBuffer missingList = new StringBuffer();
                for (int i = 0; i < numCells; i++) {
                    if (!multiCellIdFound[i]) {
                        if (missingList.length() > 0) {
                            missingList.append(", ");
                        }
                        missingList.append(Integer.toString(multiCellIds[i]));
                    }
                }
                noOfFailure++;
                Log.ERROR("Missing information for some cell ids: " + 
                        missingList.toString());
            } else if (numCellUsageInfo < numCells) {
                noOfFailure++;
                Log.ERROR("Missing disk usage information for some cells.");
            } else if (!isMultiAllCellsUsage) {
                noOfFailure++;
                Log.ERROR("Missing all cells total disk usage information.");
            }
            if (noOfFailure == 0)
                isCorrectDf = true;
            
        } catch (Exception e) {
            Log.ERROR("Error while parsing " + commandName + " stdout: "
                    + Log.stackTrace(e));
        }
        
        return isCorrectDf;
    }

    private boolean checkDfPhysicalStdout(String commandName,
            String lsStdout, boolean isBlock) {
        boolean isCorrectDf = false, isBlockStdout = false;
        
        try {
            
            String [] lsLine =  tokenizeIt(lsStdout, "\n");
            String line = null;
            
            int noOfFailure = 0;
            
            for (int lineNo=0; lineNo<lsLine.length; lineNo++) {
                line = lsLine[lineNo];
                
                if (line.trim().equals(""))
                    continue;
                
                Log.DEBUG(commandName + " output: " + line);
                
                if (line.equals(blocksOutputStr)) {
                    isBlockStdout = true;
                    continue;
                }
                
                Matcher matcher;
                if (isBlock)
                    matcher = DF_DISK_BLOCK_PATTERN.matcher(line);
                else
                    matcher = DF_DISK_PATTERN.matcher(line);
                
                // verify the output pattern
                if (!matcher.matches()) {
                    noOfFailure++;
                    Log.ERROR("Unexpected output format: " + line +
                            ", Expected: " + matcher.toString());
                } else {
                    // verify the output content
                    int group = 1;
                    int nodeId = -1, diskId = -1;
                    String nodeDiskString = matcher.group(group++);
                    
                    if (nodeDiskString.indexOf(":") != -1) {
                        String[] strings = nodeDiskString.split(":");
                        try {
                            nodeId = Integer.parseInt(strings[0]);
                            diskId = Integer.parseInt(strings[1]);
                        } catch (NumberFormatException nfe) {
                            Log.ERROR("Unexpected output format: " + nodeDiskString +
                                    ", Expected: " + matcher.toString());
                            noOfFailure++;;
                        }
                    }
                    
                    String total = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String avail = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String used = getActualValue(matcher.group(group++));
                    if (isBlock == false)
                        group++;   // Skip over MB,GB,TB,PB value
                    String perUsed = matcher.group(group);
                    
                    // remove , from the capacity string
                    // applicable only for non-human-readable df command
                    if (isBlock)
                        total = formatCapacity(total);
                    // remove unit
                    else
                        total = total.substring(0, total.length()-1);
                    
                    Log.INFO("NODE-" + nodeDiskString
                            + ": Total: " + total + " Avail: " + avail +
                            " Used: " + used + " Usage: " + perUsed);
                    
                    // verify total capacity > 0
                    double totalCap = new Double(total).doubleValue();
                    if (totalCap <= 0) {
                        noOfFailure++;
                        Log.ERROR("Unexpected total capacity: " + totalCap);
                    }
                }
            }
            
            if (isBlock) {
                if (!isBlockStdout) {
                    noOfFailure++;
                    Log.ERROR("Missing <" + blocksOutputStr + "> from stdout");
                }
            }
            
            if (noOfFailure == 0)
                isCorrectDf = true;
            
        } catch (Exception e) {
            Log.ERROR("Error while parsing " + commandName + " stdout: "
                    + Log.stackTrace(e));
        }
        
        return isCorrectDf;
    }
    
}
