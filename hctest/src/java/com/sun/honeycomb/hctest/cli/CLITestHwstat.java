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
import java.util.HashMap;
import java.io.BufferedReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author jk142663
 */
public class CLITestHwstat extends HoneycombCLISuite {
    
    public static final String ONLINE_NODE = FruInfo.ONLINE;
    public static final String OFFLINE_NODE = FruInfo.OFFLINE;
    public static final String ENABLED_DISK = FruInfo.ENABLED;
    public static final String DISABLED_DISK = FruInfo.DISABLED;

    private static String HWSTAT_F_STRING = null;
    private static String HWSTAT_FRUID_STRING = null;
    
    private static String HWSTAT_V_COMMAND = null;
    private static String HWSTAT_VERBOSE_COMMAND = null;
    private static String HWSTAT_F_COMMAND = null;
    private static String HWSTAT_FRUID_COMMAND = null;
    
    /** Creates a new instance of CLITestHwstat */
    public CLITestHwstat() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: hwstat\n");
        sb.append("\t o positive test - output syntax\n");
        sb.append("\t o negative test - invalid option/fru\n");
        sb.append("\nnodes is a required argument for single " +
                "cell setup (-ctx nodes=<node_num>), default to 8\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        HWSTAT_F_STRING = getAdminResourceProperties().getProperty("cli.hwstat.fruid_char");
        HWSTAT_FRUID_STRING = getAdminResourceProperties().getProperty("cli.hwstat.fruid_name");
        
        HWSTAT_F_COMMAND = HoneycombCLISuite.HWSTAT_COMMAND + " -" +
                HWSTAT_F_STRING;
        HWSTAT_FRUID_COMMAND = HoneycombCLISuite.HWSTAT_COMMAND + " --" +
                HWSTAT_FRUID_STRING;
    }
  
    protected boolean isInErrorExclusionList(String errorString) {
        if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	
	if ((errorString.indexOf(INVALID_CELLID_MESSAGE) != -1) ||
                (errorString.indexOf(INVALID_FRU_MESSAGE) != -1)) {
	    return true;
        }
        
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testHwstatSyntax() {
        
        TestCase self = createTestCase("CLI_hwstat_syntax");
        self.addTag(HoneycombTag.CLI);
        self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        try {
            BufferedReader output = runCommandWithoutCellid(
                    HoneycombCLISuite.HWSTAT_COMMAND);
            
            if (isMultiCell()) {
                isTestPass = verifyCommandStdout(false, 
                        HoneycombCLISuite.HWSTAT_COMMAND,
                        HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);
            }
            else {
                String stdout = HCUtil.readLines(output);            
                isTestPass = isValidHwstatSyntax(stdout);
            }
            output.close();
        } catch (Throwable t) {
            isTestPass = false;
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
                         
        if (isTestPass) 
            self.testPassed();
        else
            self.testFailed();            
    }
    
    public void testHwstatSyntaxWithCellid() {
        
        TestCase self = createTestCase("CLI_hwstat_syntax_with_cellid");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        try {
            ArrayList allCellid = getAllCellid();
            int noOfFailure = 0;
                
            for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
            
                int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                    
                BufferedReader output = runCommandWithCellid(
                    HoneycombCLISuite.HWSTAT_COMMAND, cellid);
                
                String stdout = HCUtil.readLines(output);    
                output.close();
                boolean isTestPassTmp = isValidHwstatSyntax(stdout);
                if (!isTestPassTmp)
                    noOfFailure++;
            }
                
            if (noOfFailure == 0)
                isTestPass = true;
            
        } catch (Throwable t) {
            isTestPass = false;
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
                         
        if (isTestPass) 
            self.testPassed();
        else
            self.testFailed();            
    }    
    
    public void testHwstatNegativeTest() {
        
        TestCase self = createTestCase("CLI_hwstat_Negative_Test"); 
	self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        String validFruId = getFruid("NODE");
        String invalidFruId = validFruId + "_INVALID";
        
        String [] lsInvalidArgs = {
            HoneycombCLISuite.HWSTAT_COMMAND + " " + HoneycombCLISuite.HWSTAT_COMMAND,
            HoneycombCLISuite.HWSTAT_COMMAND + " --" + HWSTAT_F_STRING + " " + validFruId,
            HoneycombCLISuite.HWSTAT_COMMAND + " --" + HWSTAT_FRUID_STRING.toLowerCase() + 
                    " " + validFruId,
            HoneycombCLISuite.HWSTAT_COMMAND + " -" + HWSTAT_FRUID_STRING + " " + validFruId,
            HWSTAT_FRUID_COMMAND  + " " + invalidFruId,
            HWSTAT_F_COMMAND  + " " + invalidFruId,
        };
        
        // will test with invalid cellid 
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.HWSTAT_COMMAND,
            HWSTAT_F_COMMAND + " " + validFruId, 
            HWSTAT_FRUID_COMMAND + " " + validFruId, 
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command without cellid
            if (i >= lsInvalidArgs.length-2)
                isTestPass = verifyCommandStdout(false, lsInvalidArgs[i], null);
            else                
                isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], HoneycombCLISuite.HWSTAT_HELP);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute invalid command with cellid
            setCellid();
            if (i >= lsInvalidArgs.length-2)
                isTestPass = verifyCommandStdout(true, lsInvalidArgs[i], null);
            else                
                isTestPass = verifyCommandStdout(
                    true, lsInvalidArgs[i], HoneycombCLISuite.HWSTAT_HELP);
                
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
    
    private boolean isValidHwstatSyntax(String stdout) {
        boolean isValidSyntax = true;
        
        String [] lsLine = null;
        int totalNode = -1;
        
        try {
            lsLine = tokenizeIt(stdout, "\n");
        }
        catch (Exception e) {
            Log.ERROR("Unable to tokenize hwstat stdout: " + e);
            return false;
        }
        
        try {
            totalNode = getNodeNum();            
        }
        catch (Throwable t) {
            Log.ERROR("Unable to get total number of nodes: " + t);
            return false;
        }
        
        Pattern regexpNode = NodeStat.NODE_HWSTAT_PATTERN;
                    
        ArrayList lsFruId = new ArrayList();
        String nodeid = null, diskid = null, nodeStatus = null, diskStatus = null;
        String componentFruid = null;
        
        SwitchFru switchOneFrunInfo = null;
        SwitchFru switchTwoFrunInfo = null;
        
        String line = null;
        int nodeNum = 0;
        
        for (int i=0; i<lsLine.length; i++) {
            line = lsLine[i];
            
            // Fru Type always matches the start of the fru name so use that
            // as our initial match point.
            if (line.startsWith(FruInfo.FRU_TYPE_NODE)) {
                nodeNum++;
                Matcher matcher = regexpNode.matcher(line);
                if (!matcher.matches()) {
                    isValidSyntax = false;
                    Log.ERROR("Unable to parse node stat output: " + line);
                    Log.INFO("Expected pattern: " + matcher.toString());
                }
                else {
                    nodeid = matcher.group(2);
                    componentFruid = matcher.group(4);
                    nodeStatus = matcher.group(6);
                    Log.DEBUG(nodeid + " " + componentFruid + " " + nodeStatus);                   
                
                    if (lsFruId.contains(componentFruid)) {
                        isValidSyntax = false;
                        Log.ERROR("Fru Id of node " + nodeid + 
                                " <" + componentFruid + "> is not unique.");
                    }
                 
                if ((!nodeStatus.equals(ONLINE_NODE)) && 
                            (!nodeStatus.equals(OFFLINE_NODE))) {
                        isValidSyntax = false;
                        Log.ERROR("Invalid status of node " + nodeid + 
                                ": <" + nodeStatus + ">");
                    }
                 
                    if (!componentFruid.equals("unavailable") 
			&& (!componentFruid.equals("UNKNOWN")) 
			&& (!componentFruid.equals("?")))
                        lsFruId.add(componentFruid);
                }
            }
            else if (line.startsWith(FruInfo.FRU_TYPE_DISK)) {
                for (int diskNum=0; diskNum<4; diskNum++) {
            
                    if (diskNum !=0) {
                        i++;
                        line = lsLine[i];
                    }                    
		    
		    DiskStat stat = null;
		    try
		    {
			stat = new DiskStat(line);
		    }
		    catch (Throwable t) {
			isValidSyntax = false;
			Log.ERROR(t.getMessage());
                        Log.ERROR("Unable to parse disk stat output: " + line);
			continue;
		    }
		    diskid = stat.getDiskId();
		    componentFruid = stat.getFruId();
		    diskStatus = stat.getStatus();
		    Log.DEBUG(diskid + " " + componentFruid + " " + diskStatus);

		    if (lsFruId.contains(componentFruid)) {
			isValidSyntax = false;
			Log.ERROR("Fru Id of disk " + diskid + 
			    " <" + componentFruid + "> is not unique.");
		    }

		    if ((!diskStatus.equals(ENABLED_DISK)) && 
			(!diskStatus.equals(DISABLED_DISK))) {
			isValidSyntax = false;
			Log.ERROR("Invalid status of disk " + diskid + 
			    ": <" + diskStatus + ">");
		    }

		    if ((nodeStatus).equals(OFFLINE_NODE)) {
			if (diskStatus.equals(ENABLED_DISK)) {
			    isValidSyntax = false;
			    Log.ERROR("Invalid status of disk " + diskid + 
			    ": <" + diskStatus + "> of " + nodeStatus +
				    " node " + nodeid);
			}
		    }

		    if ((componentFruid.length() != 0)
			&& (!componentFruid.equals("UNKNOWN")) 
			&& (!componentFruid.equals("?")))
			lsFruId.add(componentFruid);
                }
            } else if (line.startsWith(FruInfo.FRU_TYPE_SWITCH)) {
                try {
                    SwitchFru fru = new SwitchFru(line);
                    lsFruId.add(fru.getFruId());
                    
                    if (fru.getName().equals(FruInfo.FRU_SWITCH_1))
                        switchOneFrunInfo = fru;
                    else if (fru.getName().equals(FruInfo.FRU_SWITCH_2))
                        switchTwoFrunInfo = fru;
                    else {
                        isValidSyntax = false;
                        Log.ERROR("Invalid " + FruInfo.FRU_TYPE_SWITCH +
                                ": " + fru.getName());
                    }
                }
                catch (RuntimeException re) {
                    isValidSyntax = false;
                    Log.ERROR(re.getMessage());
                }
            } else if (line.startsWith(FruInfo.FRU_TYPE_SP)) {
                try {
                    SPFru fru = new SPFru(line);
                    lsFruId.add(fru.getFruId());
                }
                catch (RuntimeException re) {
                    isValidSyntax = false;
                    Log.ERROR(re.getMessage());
                }
            }
            else if(line.startsWith("Component") || line.startsWith("---------")) {
                // no-op
            } 
            else {                
                isValidSyntax = false;
                Log.ERROR("Unexpected output from hwstat: " + line);
            }
        }
        
        if (nodeNum != totalNode) {
            isValidSyntax = false;
            Log.ERROR("Expected node number:" + totalNode +
                    "; Actual node number: " + nodeNum);
        }
        
        // verify switch state
        if ((switchOneFrunInfo == null) || (switchTwoFrunInfo == null)) {
            Log.ERROR("Missing " + FruInfo.FRU_TYPE_SWITCH + " component");
            isValidSyntax = false;            
        }
        else {
            String switch1FruName = switchOneFrunInfo.getName();
            String switch1FruStatus = switchOneFrunInfo.getStatus();
            String switch2FruStatus = switchTwoFrunInfo.getStatus();
            
            if (switch1FruStatus.equals(SwitchFru.ACTIVE)) {
                if (switch2FruStatus.equals(SwitchFru.ACTIVE)) {
                    Log.ERROR("Both switches are in " + SwitchFru.ACTIVE + " state");
                    isValidSyntax = false;  
                }
            }
            else if (switch1FruStatus.equals(SwitchFru.STANDBY)) {
                Log.ERROR(switch1FruName + " is in " + SwitchFru.STANDBY + " state");
                isValidSyntax = false;  
            }
            else
                ; // do nothing
        }
        
        return isValidSyntax;
    }
    
    // componetType -> DISK or NODE
    private String getFruid(String componentType) {
        try {
            setCellid();
            BufferedReader output = runCommand(HoneycombCLISuite.HWSTAT_COMMAND);
                
            Pattern regexpNode = Pattern.compile(
                    "^NODE-(\\d{3})\\s+NODE\\s+([^\\s]+)\\s+\\[([^\\s]+)\\s*\\]$");
            Pattern regexpDisk = Pattern.compile(
                    "^DISK-(\\d{3}:[0-3\\?])\\s+DISK\\s+([^\\s]+)\\s+\\[([^\\s]+)\\]$");
                 
            String stdout = HCUtil.readLines(output);   
            output.close();
            String [] lsLine = tokenizeIt(stdout, "\n");
            
            Matcher matcher = null;
            String componentFruid = null;
                    
            for (int i=0; i<lsLine.length; i++) {
                String line = lsLine[i].trim();
                
                if (line.startsWith(componentType)) {
                    
                    if (line.startsWith("NODE")) {
                        matcher = regexpNode.matcher(line);
                    }
                    else {
                        matcher = regexpDisk.matcher(line);
                    }
                 
                    if (!matcher.matches())
                        continue;
                    
                    componentFruid = matcher.group(2);
                    
                    if ((!componentFruid.equals("UNKNOWN")) && (!componentFruid.equals("?")))
                        return componentFruid;
                    
                }                           
            }
            
        } catch (Throwable t) {
            Log.ERROR("Unable to parse hwstat stdout: " + Log.stackTrace(t));
        }
        
        return null;
    }
    
}
