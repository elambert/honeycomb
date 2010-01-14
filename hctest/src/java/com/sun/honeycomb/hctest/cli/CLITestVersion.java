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
import java.util.ArrayList;
import java.util.HashMap;
import java.text.MessageFormat;
import java.io.BufferedReader;
import java.io.IOException;

/**
 *
 * @author jk142663
 */
public class CLITestVersion extends HoneycombCLISuite {
    
    private static final String VERSION_FILE = "/opt/honeycomb/version";
    private static final String VERSION_V_OPTION = "-v";
    private static String VERSION_V_COMMAND = null;
    
    HashMap allAdminIp = null;
    
    /** Creates a new instance of CLIVersion */
    public CLITestVersion() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: version\n");
        sb.append("\t o positive test - output syntax\n");
        sb.append("\t o negative test - invalid option\n");
        
        sb.append("\nnodes is a required argument for single " +
                "cell setup (-ctx nodes=<node_num>), default to 8\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        VERSION_V_COMMAND = HoneycombCLISuite.VERSION_COMMAND + " " + 
                VERSION_V_OPTION;
        
        if (allAdminIp == null) allAdminIp = getAllAdminIp();
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if (errorString.indexOf(INVALID_CELLID_MESSAGE) != -1)
	    return true;
	
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testVersionSyntax() {
        
        TestCase self = createTestCase("CLI_version_syntax");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;

        boolean isTestPass = false;

        try {
            if (isMultiCell()) {
                isTestPass = verifyCommandStdout(false, 
                        HoneycombCLISuite.VERSION_COMMAND,
                        HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);
            }                
            else {
                BufferedReader output = runCommandWithoutCellid(
                    HoneycombCLISuite.VERSION_COMMAND);
                isTestPass = checkVersionStdout(output.readLine());
                output.close();
            }                          
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }  
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testVersionVSyntax() {
        
        TestCase self = createTestCase("CLI_version_v_syntax");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;

        boolean isTestPass = false;

        try {
            if (isMultiCell()) {     
                isTestPass = verifyCommandStdout(false, VERSION_V_COMMAND,
                        HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);
            }
            else {
                ArrayList allCellid = getAllCellid();                   
                Integer cellid = (Integer) allCellid.get(0);
                
                BufferedReader output = runCommandWithoutCellid(
                    VERSION_V_COMMAND);
                String lsLine = HCUtil.readLines(output);
                output.close();
                isTestPass = checkVersionVStdout(cellid.intValue(), lsLine);
            }    
                      
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }  
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testVersionSyntaxWithCellid() {
        
        TestCase self = createTestCase("CLI_version_syntax_with_cellid");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        try {
            ArrayList allCellid = getAllCellid();
            
            for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
            
                int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                setCellid(cellid);
                    
                BufferedReader output = runCommandWithCellid(
                        HoneycombCLISuite.VERSION_COMMAND, cellid);
            
                String line = null;
                
                while ((line = output.readLine()) != null) {
                    String adminIp = (String) getAllAdminIp().get(new Integer(cellid));
                    isTestPass = checkVersionStdout(adminIp, line);    
                }           
                output.close();
            }
                     
            if (isTestPass) 
                self.testPassed();
            else
                self.testFailed();
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
    }    
    
    public void testVersionVSyntaxWithCellid() {
        
        TestCase self = createTestCase("CLI_version_v_syntax_with_cellid");

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
                    
                String adminIp = (String) getAllAdminIp().get(
                        new Integer(cellid));
                BufferedReader output = runCommandWithCellid(
                        VERSION_V_COMMAND, cellid);
                boolean isTestPassTmp = checkVersionVStdout(cellid, adminIp, 
                        HCUtil.readLines(output));  
                output.close();
                    
                if(!isTestPassTmp)
                    noOfFailure++;       
            }
                     
            if (noOfFailure == 0)
                isTestPass = true;
            
        } catch (Throwable t) {
            isTestPass = false;
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
        
        if (isTestPass) 
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testVersionNegativeTest() {
        
        TestCase self = createTestCase("CLI_version_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        String [] lsInvalidArgs = {
            HoneycombCLISuite.VERSION_COMMAND + " " + HoneycombCLISuite.VERSION_COMMAND,
            HoneycombCLISuite.VERSION_COMMAND + " -V"};
        
        // will test with invalid cellid for multicell
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.VERSION_COMMAND,
            VERSION_V_COMMAND
        };
        
        boolean isTestPass = false;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command
            boolean isTestPassTmp = verifyCommandStdout(
                    false, lsInvalidArgs[i], HoneycombCLISuite.VERSION_HELP);
            
            if ((i == 0) || (isTestPass))
                isTestPass = isTestPassTmp;
            
            // execute invalid command with cellid
            setCellid();
            isTestPassTmp = verifyCommandStdout(
                    true, lsInvalidArgs[i], HoneycombCLISuite.VERSION_HELP);
                
            if (isTestPass)
                isTestPass = isTestPassTmp;
        }
        
        int invalidCellId = getInvalidCellid();
                
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
               
            // execute command with invalid cellid
            String formattedCommand = formatCommandWithCellid(
                    lsInvalidMulticellArgs[i], invalidCellId);
            
            boolean isTestPassTmp = verifyCommandStdout(false, formattedCommand, null);
            
            if (isTestPass)
                isTestPass = isTestPassTmp;
        }
        
        if(isTestPass )
            self.testPassed();
        else
            self.testFailed();
    }
        
    private boolean checkVersionStdout(String adminIp, String line) {
        boolean isCorrectVersion = false;
            
        try { 
            Pattern regexp = Pattern.compile("^ST5800.*release.*$");
            Matcher matcher = regexp.matcher(line);
                    
            // verify the output pattern
            if (!matcher.matches()) {
                Log.ERROR("Unexpected output format: " + line);
                return false;
            }
            
            // verify the output content
            BufferedReader bufferRead = runCommandAsRoot(
                    adminIp, "cat " + VERSION_FILE);
            String versionLine = null;
            
            while ((versionLine = bufferRead.readLine()) != null) {
                if (line.equals(versionLine)) {
                    Log.INFO("version in " + VERSION_FILE + ": " +
                            versionLine);
                    isCorrectVersion = true;
                }
            }  
            bufferRead.close();
            
            if (!isCorrectVersion)
                Log.ERROR("Unexpected version: " + line);
            
        } catch (Throwable t) {
            Log.ERROR("Error while parsing CLI: " + t);
        }
        
        return isCorrectVersion;
    }
    
    private boolean checkVersionStdout(String line) {
        return checkVersionStdout(getAdminVIP(), line);
    }
    
    private boolean checkVersionVStdout(int cellid, String adminIp, 
            String lsStdout) {
        boolean isCorrectVersionSyntax = false;
                
        try {
            String serviceNodeLine = "Service Node:";
            String swithLine = "Switch:";
            String biosVersion = "BIOS Version:";
            String smdcVersion = "SMDC Version:";
            String overlayVersionSw1 = "Overlay Version (sw#1):";
            String overlayVersionSw2 = "Overlay Version (sw#2):";
        
            MessageFormat form = new MessageFormat("NODE-1{0}:");
            int nodeArg = 1;
        
            String [] lsLine =  tokenizeIt(lsStdout, "\n");
            int currentLine = 0;
        
            // honeycomb version
            boolean isCorrectHoneycombVersion = checkVersionStdout(
                    adminIp, lsLine[currentLine++]);
        
             // service node version
            boolean isCorrectServiceNodeSyntax = isCorrectSyntax(
                    lsLine[currentLine++], serviceNodeLine);
            boolean isCorrectBiosVersion = isCorrectSyntax(
                    lsLine[currentLine++], biosVersion);
            boolean isCorrectSmdcVersion = isCorrectSyntax(
                    lsLine[currentLine++], smdcVersion);
               
            // switch version
            boolean isCorrectSwitchSyntax = isCorrectSyntax(
                    lsLine[currentLine++], swithLine);
            boolean isCorrectOverlaySw1Version = isCorrectSyntax(
                    lsLine[currentLine++], overlayVersionSw1);
            boolean isCorrectOverlaySw2Version = isCorrectSyntax(
                    lsLine[currentLine++], overlayVersionSw2);
                
            // all node version
            boolean isCorrectNodeVersion = true;
            
            HwStat hwStat = null;            
            try {
                hwStat = new HwStat(runCommandWithCellid(
                    HoneycombCLISuite.HWSTAT_COMMAND, cellid));
            } catch (Throwable e) {
                throw new HoneycombTestException("Unable to get hwstat: ", e);
            }
            
            ArrayList nodes = hwStat.getNodes();
            NodeStat nodeStat = null;
        
            for (int i=0; i<nodes.size(); i++) {
                
                String nodeLineAct = lsLine[currentLine++]; 
                String nodeArgStr = new Integer(nodeArg).toString();
                if (nodeArg < 10)
                    nodeArgStr = "0" + nodeArgStr;
                Object [] formatArg = {nodeArgStr};
                String nodeLineExp = form.format(formatArg);
                nodeArg++;
            
                // determine whether the node is enable or disable
                boolean isEnabled = true;
                for(int nodeNum=0; nodeNum<nodes.size(); nodeNum++) {
                    nodeStat = (NodeStat) nodes.get(i);
                    
                    nodeArgStr = "1" + nodeArgStr;
                    int nodeId = new Integer(nodeArgStr).intValue();
                    
                    if (nodeId == nodeStat.nodeId) {
                        if (!nodeStat.isInCluster) 
                            isEnabled = false;
                        break;
                    }
                }
                
                if (!isEnabled) {
                    Log.INFO("Won't verify version of offline node " + nodeLineExp);
                    currentLine--;
                    continue;
                }
                
                boolean isCorrectNodeSyntax = isCorrectSyntax(
                        nodeLineAct, nodeLineExp);
                boolean isCorrectNodeBios = isCorrectSyntax(
                        lsLine[currentLine++], biosVersion);
                boolean isCorrectNodeSmdc = isCorrectSyntax(
                        lsLine[currentLine++], smdcVersion);
                
                if ((!isCorrectNodeSyntax) || (!isCorrectNodeBios) || 
                        (!isCorrectNodeSmdc))
                    isCorrectNodeVersion = false;
            }
    
           if ((isCorrectHoneycombVersion) && (isCorrectServiceNodeSyntax) && 
                    (isCorrectBiosVersion) && (isCorrectSmdcVersion) && 
                    (isCorrectSwitchSyntax) && (isCorrectOverlaySw1Version) && 
                    (isCorrectOverlaySw2Version) && (isCorrectNodeVersion))
               isCorrectVersionSyntax = true;          
        } catch (Exception e) {
            Log.ERROR("Error while parsing version stdout: " + e);
        }
        
        return isCorrectVersionSyntax;
    }
    
    private boolean checkVersionVStdout(int cellid, String lsStdout) {
        return checkVersionVStdout(cellid, getAdminVIP(), lsStdout);
    }
    
    private boolean isCorrectSyntax(String actLine, String expLine) {
        boolean isCorrectSyntax = false;
        
        actLine = actLine.toLowerCase().trim();
        expLine = expLine.toLowerCase().trim();
        
        if (actLine != null) {
           if (actLine.indexOf(expLine) == -1) {
               Log.ERROR("Unexpected output: " + actLine + "; Expected: " + expLine);
               isCorrectSyntax = false;
           }
           else {
               Log.INFO(actLine);
               isCorrectSyntax = true;
           }
        }
            
        return isCorrectSyntax;
    }
    
}
