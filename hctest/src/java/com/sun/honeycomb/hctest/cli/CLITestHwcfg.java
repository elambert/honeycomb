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
public class CLITestHwcfg extends HoneycombCLISuite {
    
    private static final String NODE = "NODE-";
    private static final String DISK = "DISK-";
    
    private static String HWCFG_E_CHAR = null;
    private static String HWCFG_ENABLE_NAME = null;
    private static String HWCFG_D_CHAR = null;
    private static String HWCFG_DISABLE_NAME = null;
    private static String HWCFG_I_CHAR = "i";
    private static String HWCFG_IMPI_NAME = "ipmi";
    
    private static String HWCFG_E_COMMAND = null;
    private static String HWCFG_ENABLE_COMMAND = null;
    private static String HWCFG_D_COMMAND = null;
    private static String HWCFG_DISABLE_COMMAND = null;
    private static String HWCFG_I_COMMAND = null;
    private static String HWCFG_IMPI_COMMAND = null;
    
    private static final String outputMessage = "Operation successfully completed";
    
    private HwStat hwStat = null;    
    private ArrayList nodes = null;
    private DiskStat diskStat = null;
    
    /** Creates a new instance of CLITestHwcfg */
    public CLITestHwcfg() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: hwcfg\n");
        sb.append("\t o positive test - enable/disable node/disk\n");
        sb.append("\t o negative test - invalid option/node/disk\n");
        sb.append("\nnodes is a required argument for single " +
                "cell setup (-ctx nodes=<node_num>), default to 8\n");
        
        return sb.toString();
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if ((errorString.indexOf(INVALID_CELLID_MESSAGE) != -1) ||
            (errorString.indexOf(INVALID_FRU_MESSAGE) != -1) ||
	    (errorString.indexOf(INVALID_NODE_MESSAGE) != -1) ||
	    (errorString.indexOf(INVALID_DISK_MESSAGE) != -1) ||
	    (errorString.indexOf("invalid command sequence") != -1)) {
	    return true;
	}
	return super.isInErrorExclusionList(errorString);
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        HWCFG_E_CHAR = getAdminResourceProperties().getProperty("cli.hwcfg.enable_char") + " ";
        HWCFG_ENABLE_NAME = getAdminResourceProperties().getProperty("cli.hwcfg.enable_name") + " ";
        HWCFG_D_CHAR = getAdminResourceProperties().getProperty("cli.hwcfg.disable_char") + " ";
        HWCFG_DISABLE_NAME = getAdminResourceProperties().getProperty("cli.hwcfg.disable_name") + " ";
        
        HWCFG_E_COMMAND = HoneycombCLISuite.HWCFG_COMMAND + " -" + 
                HWCFG_E_CHAR;
        HWCFG_ENABLE_COMMAND = HoneycombCLISuite.HWCFG_COMMAND + " --" + 
                HWCFG_ENABLE_NAME;
        HWCFG_D_COMMAND = HoneycombCLISuite.HWCFG_COMMAND + " -" + 
                HWCFG_D_CHAR;
        HWCFG_DISABLE_COMMAND = HoneycombCLISuite.HWCFG_COMMAND + " --" + 
                HWCFG_DISABLE_NAME;  
        HWCFG_I_COMMAND = HoneycombCLISuite.HWCFG_COMMAND + " -" + 
                HWCFG_I_CHAR;
        HWCFG_IMPI_COMMAND = HoneycombCLISuite.HWCFG_COMMAND + " --" + 
                HWCFG_IMPI_NAME;        
	
    }
    
    public void testEnableDisableDisk() {
        
        String [] lsTestCase = {"CLI_hwcfg_D_disk", "CLI_hwcfg_E_disk", 
                "CLI_hwcfg_disable_disk", "CLI_hwcfg_enable_disk"};
        String [] lsCommand = {HWCFG_D_COMMAND, HWCFG_E_COMMAND, 
                HWCFG_DISABLE_COMMAND, HWCFG_ENABLE_COMMAND};
        boolean [] lsEnabled = {false, true, false, true};
        String [] lsAuditMsg = {
            "info.adm.disableDisk",
            "info.adm.enableDisk",
            "info.adm.disableDisk",
            "info.adm.enableDisk",            
        };
        
        ArrayList allCellid = getAllCellid();
                    
        for (int i=0; i<lsTestCase.length; i++) {
        
            TestCase self = createTestCase(lsTestCase[i]);            
            boolean isTestPass = false;
         
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!
            
            if (self.excludeCase()) return;

            int noOfFailure = 0;
            
            try {
                for(int cellCount=0; cellCount < allCellid.size(); cellCount++) {  
                        
                    int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                    setCellid(cellid);
                    
                    setCurrentInternalLogFileNum();
            
                    // change disk state to enable
                    if (lsEnabled[i]) {
                    
                        setDiskStat(false);
                
                        if (diskStat != null)  {
                            isTestPass = enableComponent(lsCommand[i], 
                                diskStat.nodeId, diskStat.diskId);
                            
                            if (!isTestPass)
                                noOfFailure++;
                        }                        
                    }
                
                    // change disk state to disable
                    else {
                    
                        setDiskStat(true);
                
                        if (diskStat != null) {
                            isTestPass = disableComponent(lsCommand[i], 
                                diskStat.nodeId, diskStat.diskId);
                            
                            if (!isTestPass)
                                noOfFailure++;
                        }
                    }
                    
                    verifyAuditHwcfg(lsCommand[i], lsAuditMsg[i], 
                            DISK + diskStat.nodeId + ":"  + diskStat.diskId, 
                            String.valueOf(diskStat.nodeId));
                }
            } 
	    catch (Throwable t) {
                Log.ERROR("Error while enabled/disabled a disk: " 
		    + Log.stackTrace(t));
            }
        
            if(noOfFailure == 0)
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    /**
     * Test the enable disable options of hwcfg.  Terminate on any failure
     */
    public void testEnableDisableNode() {
        
        String [] lsCommand = {HWCFG_D_COMMAND, HWCFG_E_COMMAND, 
                HWCFG_DISABLE_COMMAND, HWCFG_ENABLE_COMMAND};
        boolean [] lsEnabled = {false, true, false, true};
	String [] lsAuditMsg = {
            "info.adm.powerOffNode",
            "info.adm.powerOnNode",
            "info.adm.powerOffNode",
            "info.adm.powerOnNode",
        };
        
	TestCase self = createTestCase("CLI_hwcfg_EnableDiskable");   
        self.addTag(HoneycombTag.CLI);
	// self.addTag(Tag.REGRESSION); // add me!
	if (self.excludeCase()) return;
	
	boolean isTestPass = false;
	try {
	    ArrayList allCellid = getAllCellid();
	    for(int cellCount=0; cellCount < allCellid.size(); cellCount++) {  
		int cellid = ((Integer) allCellid.get(cellCount)).intValue();
		setCellid(cellid);
		
                // Get an online node.  We'll bounce this node up and down
		// for the enable/disable tests 
		NodeStat nodeStat = getEnabledNodeStat();
		if (nodeStat == null) {
		    self.addTag(Tag.NORUN, "No testable nodes found on cell " 
			+ cellid + ".");
		    self.excludeCase();
		    return;
		}
		for (int i=0; i < lsCommand.length; i++) {
                    setCurrentInternalLogFileNum();
            
                    if (lsEnabled[i]) {
			// change node state to enable
                        isTestPass = enableComponent(lsCommand[i], nodeStat.nodeId, -1);
                    } else {
			// change node state to disable
			isTestPass = disableComponent(lsCommand[i], nodeStat.nodeId, -1);
                    }
		
                    verifyAuditHwcfg(lsCommand[i], lsAuditMsg[i], 
                            String.valueOf(nodeStat.nodeId), String.valueOf(cellid));
                    
		    if (!isTestPass) {
			break;
		    }
                }
            } 
	}
	catch (HoneycombTestException he) {
	    Log.ERROR(he.getMessage());
	    isTestPass = false;
	}
	catch (Throwable t) {
	    Log.ERROR("Error while disabling a node: " 
		+ Log.stackTrace(t));
	    isTestPass = false;
	}

	if(isTestPass)
	    self.testPassed();
	else
	    self.testFailed();
    }
    
    public void testEnableDisableBoundaryNode() {
        
        TestCase self = createTestCase("CLI_hwcfg_enable_disable_boundary_node");  
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        int noOfFailure = 0;

        try {
            ArrayList allCellid = getAllCellid();
        
            for(int cellCount=0; cellCount<allCellid.size(); cellCount++) {  
    
		int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                setCellid(cellid);
                Log.INFO("******* Cell id # " + cellid + " ******");
		
                NodeStat nodeStat = getBoundaryNodeStat();
		if (nodeStat == null) {
		    self.addTag(Tag.NORUN, "Boundary node is not testable.");
		    self.excludeCase();
		    return;
		}
            
                // change boundary node state to offline followed by online
                if (nodeStat.isInCluster) {
                    setCurrentInternalLogFileNum();            
                    isTestPass = disableComponent(HWCFG_D_COMMAND, 
                            nodeStat.nodeId, -1);
                
                    verifyAuditHwcfg(HWCFG_D_COMMAND, "info.adm.powerOffNode", 
                            String.valueOf(nodeStat.nodeId), String.valueOf(cellid));
                    
                    if (!isTestPass) {
                        noOfFailure++;
			continue;
		    }
                    
                    setCurrentInternalLogFileNum();            
                    isTestPass = enableComponent(HWCFG_E_COMMAND, 
                            nodeStat.nodeId, -1);                
                    verifyAuditHwcfg(HWCFG_E_COMMAND, "info.adm.powerOnNode", 
                            String.valueOf(nodeStat.nodeId), String.valueOf(cellid));
                    
                    if (!isTestPass)
                        noOfFailure++;   
                }
             
                // change boundary node state to online followed by offline
                else {
                    setCurrentInternalLogFileNum();            
                    isTestPass = enableComponent(HWCFG_E_COMMAND, 
                            nodeStat.nodeId, -1);                
                    verifyAuditHwcfg(HWCFG_E_COMMAND, "info.adm.powerOnNode", 
                            String.valueOf(nodeStat.nodeId), String.valueOf(cellid));
                    
                    if (!isTestPass) {
                        noOfFailure++;
			continue;
		    }
                    
                    setCurrentInternalLogFileNum();            
                    isTestPass = disableComponent(HWCFG_D_COMMAND, 
                            nodeStat.nodeId, -1);
                    verifyAuditHwcfg(HWCFG_D_COMMAND, "info.adm.powerOffNode", 
                            String.valueOf(nodeStat.nodeId), String.valueOf(cellid));
                    
                    if (!isTestPass) {
			// Disable failed.  Not already enabled.
                        noOfFailure++;   
			continue;
		    }
		    
		    // Re-enable disabled node
		    setCurrentInternalLogFileNum();            
                    isTestPass = enableComponent(HWCFG_E_COMMAND, 
                            nodeStat.nodeId, -1);                
                    verifyAuditHwcfg(HWCFG_E_COMMAND, "info.adm.powerOnNode", 
                            String.valueOf(nodeStat.nodeId), String.valueOf(cellid));
                    
                    if (!isTestPass)
                        noOfFailure++;  
                }
            }
        } 
	catch (Exception e) {
            Log.ERROR("Error while enabling/disabling boundary node " 
		+ Log.stackTrace(e));
            noOfFailure++;
        }
    
        if(noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testImpiPowerOn() {     
        
        String [] lsCommand = {
                HWCFG_I_COMMAND + " -" + HWCFG_D_CHAR,
                HWCFG_IMPI_COMMAND + " -" + HWCFG_D_CHAR,
                HWCFG_I_COMMAND + " --" + HWCFG_DISABLE_NAME,
                HWCFG_IMPI_COMMAND + " --" + HWCFG_DISABLE_NAME};
        
	ArrayList allCellid = getAllCellid();
	
	TestCase self = createTestCase("CLI_hwcfg_impi_powerOn");
	self.addTag(HoneycombTag.CLI);

	// self.addTag(Tag.REGRESSION); // add me!
	if (self.excludeCase()) return;

	// We don't want to keep disabling nodes if we get a failure.
	// Abort on the 1st failure.
	boolean isTestPass = true;

	try {
	    for(int cellCount=0; isTestPass && cellCount < allCellid.size(); cellCount++) {

		int cellid = ((Integer) allCellid.get(cellCount)).intValue();
		setCellid(cellid);


		NodeStat nodeStat = getEnabledNodeStat();
		if (nodeStat == null) {
		    self.addTag(Tag.NORUN, "No testable nodes found on cell "
                        + cellid + ".");
		    self.excludeCase();
		    break;
		}
		
		for (int i=0; i < lsCommand.length; i++) {
		    // disable the node
		    setCurrentInternalLogFileNum();            
                    isTestPass = disableComponent(lsCommand[i], nodeStat.nodeId, -1);                       
		    verifyAuditHwcfg(lsCommand[i], "info.adm.powerOffNodeIpmi", 
                            String.valueOf(nodeStat.nodeId), String.valueOf(cellid));
                    if (!isTestPass)
			break;

		    // If passed, re-enable the node
		    setCurrentInternalLogFileNum();            
                    isTestPass = enableComponent(HWCFG_E_COMMAND, nodeStat.nodeId, -1);
		    verifyAuditHwcfg(lsCommand[i], "info.adm.powerOnNode", 
                            String.valueOf(nodeStat.nodeId), String.valueOf(cellid));
                    if (!isTestPass) 
			break;
		}
	    }
	} 
	catch (Exception e) {
	    Log.ERROR("Error while disabling/enabling node using impi: " 
		+ Log.stackTrace(e));
	    isTestPass = false;
	}
	if(isTestPass)
	    self.testPassed();
	else
	    self.testFailed();
    }
        
    public void testHwcfgNegativeTest() {
        
        TestCase self = createTestCase("CLI_hwcfg_Negative_Test");
	self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
	if (self.excludeCase()) return;
        // self.addTag(Tag.REGRESSION); // add me!
        
        int noOfFailure = 0;
        
        int nodeId = 108, diskId = 0;        
        String nodeIdStr = NODE + nodeId;
        String diskIdStr = DISK + nodeId + ":" + diskId;
        String invalidNodeId = NODE + getInvalidNodeId();
        String invalidDiskId = DISK + nodeId + ":" + getInvalidDiskId();
        
        String badFruError = getAdminResourceProperties().
                getProperty("common.badfru") + ": ";
        String [] invalidDiskError = {badFruError + invalidDiskId + 
                ": no such disk: " + invalidDiskId};
        String [] invalidNodeError = {badFruError + invalidNodeId + ": " + 
                getAdminResourceProperties().getProperty("cli.internal.bad_nodeid") + 
                " " + getInvalidNodeId()};
	
        
        String [] lsInvalidArgs = {HoneycombCLISuite.HWCFG_COMMAND,
            HoneycombCLISuite.HWCFG_COMMAND + " -h",
            HWCFG_E_COMMAND, HWCFG_ENABLE_COMMAND, 
            HWCFG_D_COMMAND, HWCFG_DISABLE_COMMAND,
            HoneycombCLISuite.HWCFG_COMMAND + " -e " + diskIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " -d " + diskIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " -I -E " + nodeIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " --E " + diskIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " --D " + diskIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " -enable " + diskIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " -disable " + diskIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " -impi -E " + nodeIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " --Enable " + diskIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " --Disable " + diskIdStr,
            HoneycombCLISuite.HWCFG_COMMAND + " --Impi -E " + nodeIdStr,
            HWCFG_E_COMMAND + " --" + HWCFG_ENABLE_NAME + " " + diskIdStr,
            HWCFG_ENABLE_COMMAND + " -" + HWCFG_E_CHAR + " " + diskIdStr, 
            HWCFG_D_COMMAND + " --" + HWCFG_DISABLE_NAME + " " + diskIdStr,
            HWCFG_DISABLE_COMMAND + " -" + HWCFG_D_CHAR + " " + diskIdStr
	};
	
	String [] invalidDiskNodes = new String[] {
            HWCFG_E_COMMAND + " " + invalidDiskId,
            HWCFG_ENABLE_COMMAND + " " + invalidDiskId, 
            HWCFG_D_COMMAND + " "  + invalidDiskId, 
            HWCFG_DISABLE_COMMAND + " " + invalidDiskId,
            HWCFG_E_COMMAND + " "  + invalidNodeId,
            HWCFG_ENABLE_COMMAND + " " + invalidNodeId, 
            HWCFG_D_COMMAND + " "  + invalidNodeId, 
            HWCFG_DISABLE_COMMAND + " " + invalidNodeId,
            HWCFG_IMPI_COMMAND + " -E " + invalidNodeId,            
        };
        
        String [] lsInvalidMulticellArgs = {
            HWCFG_D_COMMAND + " " + nodeIdStr, 
            HWCFG_E_COMMAND + " " + nodeIdStr, 
            HWCFG_DISABLE_COMMAND + " " + nodeIdStr, 
            HWCFG_ENABLE_COMMAND + " " + nodeIdStr,
            HWCFG_D_COMMAND + " " + diskIdStr, 
            HWCFG_E_COMMAND + " " + diskIdStr, 
            HWCFG_DISABLE_COMMAND + " " + diskIdStr, 
            HWCFG_ENABLE_COMMAND + " " + diskIdStr 
        };
        boolean isTestPass = false;
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], HoneycombCLISuite.HWCFG_HELP);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute invalid command with cellid
            setCellid();
            isTestPass = verifyCommandStdout(
                    true, lsInvalidArgs[i], HoneycombCLISuite.HWCFG_HELP);
                
            if (!isTestPass)
                noOfFailure++;          
        }
	
        for (int i=0; i<invalidDiskNodes.length; i++) {
            // execute invalid command
            isTestPass = verifyCommandStdout(
                    false, invalidDiskNodes[i], null);
            
            if (!isTestPass)
                noOfFailure++;
            // execute invalid command with cellid
            setCellid();
            isTestPass = verifyCommandStdout(
                    true, invalidDiskNodes[i], null);
                
            if (!isTestPass)
                noOfFailure++;  
        }
	
        int invalidCellId = getInvalidCellid();
                
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
                
            // execute command without cellid
            if (isMultiCell()) {
                isTestPass = verifyCommandStdout(
                        false, lsInvalidMulticellArgs[i], HoneycombCLISuite.HWCFG_HELP);
        
                if (!isTestPass)
                    noOfFailure++;
            }
            
            // execute command with invalid cellid
            String formattedCommand = formatCommandWithCellid(
                    lsInvalidMulticellArgs[i], invalidCellId);
        
            isTestPass = verifyCommandStdout(
                    false, formattedCommand, HoneycombCLISuite.HWCFG_HELP);
        
            if (!isTestPass)
                noOfFailure++;
        }
        // for 8-node setup only
        int nodeNum = -1;
        try {
            nodeNum = getNodeNum();
        } catch(Throwable t) {
            Log.WARN("Error to retrieve the total node in cluster: " 
		+ Log.stackTrace(t));
        }
        
        if (nodeNum == 8) {
            String invalidFruId_8nodes = "DISK:114-1";
            String [] lsInvalidArgs_8nodes = {
                HWCFG_D_COMMAND + invalidFruId_8nodes,
                HWCFG_E_COMMAND + invalidFruId_8nodes,
                HWCFG_DISABLE_COMMAND + invalidFruId_8nodes,
                HWCFG_ENABLE_COMMAND + invalidFruId_8nodes
            };      
            for (int i=0; i<lsInvalidArgs_8nodes.length; i++) {
                
                // execute invalid command without cellid
                isTestPass = verifyCommandStdout(
                        false, lsInvalidArgs_8nodes[i], null);
		    // invalidIdNameError);
        
                if (!isTestPass)
                    noOfFailure++;
	    }
            for (int i=0; i<lsInvalidArgs_8nodes.length; i++) {
		// execute invalid command with cellid
		setCellid();
                isTestPass = verifyCommandStdout(
                    true, lsInvalidArgs_8nodes[i], null);
                
		if (!isTestPass)
		    noOfFailure++; 
            }
        
        }
        if(noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }

    // enable a disk or node
    // command: hwcfg [-E | --enable}
    // nodeId: Node id
    // diskId: Disk Id when component is disk, -1 when component is node
    private boolean enableComponent(String command, int nodeId, 
            int diskId) throws HoneycombTestException {
        
        boolean isEnabled = false;
        String componentName = null;
        String expectedStatus = null;
	
        // enable disk
        if (diskId != -1) {
            componentName = DISK + nodeId + ":" + diskId;
	    expectedStatus = "enabled";
	} else { // enable node
            if (nodeId == -1) {
                throw new HoneycombTestException(
                        "enableCComponent: nodeId and diskId can't both be -1");
            }
            componentName = NODE + nodeId;    
	    expectedStatus = "online";
	}
        
        command = command + " " + componentName;
        
        try {
            String line = null;
            
            // check output message
            BufferedReader output = runCommand(command);
            
            while ((line = output.readLine()) != null) {
                Log.INFO(command + " output: " + line);
            }
            output.close();
            
            // vefiry the status
            // for disk
            if (diskId != -1) {
                DiskStat diskStatTmp = getDiskStat(nodeId, diskId);
                Log.INFO("Disk Status: " + diskStatTmp.toString());
                if (diskStatTmp.isEnabled)
                    isEnabled = true;
            }
            // for node
            else {
                NodeStat nodeStatTmp = getNodeStat(nodeId);
		if (nodeStatTmp == null) {
		   throw new HoneycombTestException("Lookup of " + nodeId + " failed.");
		}
                Log.INFO("Node Status: " + nodeStatTmp.toString());
                if(nodeStatTmp.isInCluster)
                    isEnabled = true;
                else {
                    for (int diskNum=0; diskNum<nodeStatTmp.disks.length; diskNum++) {
                        DiskStat diskStatTmp = (DiskStat) nodeStatTmp.disks[diskNum];
			if (diskStatTmp != null) {
                            Log.INFO("Disk Status: " + diskStatTmp.toString());
                            if(!diskStatTmp.isEnabled)
                                isEnabled = false;
			}
                    }
                }
            }
        } 
	catch (HoneycombTestException hte) {
	    throw hte;
        }
        catch (Throwable e) {
            throw new HoneycombTestException("Unable to enable component " + 
                    componentName + ": ", e);
        }
        
        if (isEnabled) {
            Log.INFO(componentName + " is enabled successfully");
            Log.INFO("\nVerify that the command <" + command + 
                    "> will display proper error message as the component <" + 
                    componentName + "> is already enabled");

            isEnabled = invalidCommand(command, componentName + " is already "
		+ expectedStatus + ".");
        }
        else
            Log.ERROR("Failed to enable " + componentName);
        
        return isEnabled;
    }
    
    // disable a disk or node
    // command: hwcfg [-D | --disable}
    // nodeId: Node id
    // diskId: Disk Id when component is disk, -1 when component is node
    private boolean disableComponent(String command, int nodeId, 
            int diskId) throws HoneycombTestException {
        
        boolean isDisabled = false;
        String componentName = null;
	String expectedStatus=null;
        // disable disk
        if (diskId != -1) {
            componentName = DISK + nodeId + ":" + diskId;
	    expectedStatus = "disabled";
        // disable node
	} else {
            if (nodeId == -1) {
                throw new HoneycombTestException("disableComponent: nodeId and diskId can't both be -1");
            }

            componentName = NODE + nodeId;
	    expectedStatus = "offline";
	}
        command = command + " " + componentName;
        
        try {
            String line = null;
            
            // check output message
            BufferedReader output = runCommand(command);
            
            while ((line = output.readLine()) != null) {
                Log.INFO(command + " output: " + line);
            }
            output.close();
            
            // vefiry the status
            // for disk
            if (diskId != -1) {
                HCUtil.doSleep(15, "CR6573089 hwcfg -D DISK-x:xx can show disk as ENABLE if hwstat called immediately afterwords");
                DiskStat diskStatTmp = getDiskStat(nodeId, diskId);
                Log.INFO("Disk Status: " + diskStatTmp.toString());
                if (!diskStatTmp.isEnabled)
                    isDisabled = true;
            }
            // for node
            else {  	
	        NodeStat nodeStatTmp = getNodeStat(nodeId);	
                Log.INFO("Node Status: " + nodeStatTmp.toString());
                if(!nodeStatTmp.isInCluster)
                    isDisabled = true;
                else {
                    for (int diskNum=0; diskNum<nodeStatTmp.disks.length; diskNum++) {
                        DiskStat diskStatTmp = (DiskStat) nodeStatTmp.disks[diskNum];
                        Log.INFO("Disk Status: " + diskStatTmp.toString());
                        if(diskStatTmp.isEnabled)
                            isDisabled = false;
                    }
                }
            }
            
        } 
        catch (HoneycombTestException hte) {
            throw hte;
        }
	catch (Throwable e) {
            throw new HoneycombTestException("Unable to disable component " + 
                    componentName + ": ", e);
        }
        
        if (isDisabled) {
            Log.INFO(componentName + " is disabled successfully");
            Log.INFO("\nVerify that the command <" + command + 
                    "> will display proper error message as the component <" + 
                    componentName + "> is already disabled");

            isDisabled = invalidCommand(command, componentName + " is already " 
		+ expectedStatus + ".");
        }
        else
            Log.ERROR("Failed to disable " + componentName);
        
        return isDisabled;
    }
    
    private boolean invalidCommand(String command, String message) {
        try {
            // check output message
            BufferedReader output = runCommand(command);
            String [] lsOutput = excudeCustomerWarning(output); 
            output.close();
	    String line = null;
	    if (lsOutput != null) {
            	line = lsOutput[0];
	    }
            String errMsg = "";
            if (line != null) {
                errMsg = line;
            }
                
            if (!message.equals(errMsg.trim())) {
                Log.ERROR("Actual message: " + errMsg + 
                        "\nExpected message: " + message);
                return false;
            }
     
        } catch (Throwable t) {
                Log.ERROR("Error while trying to enable/disable component: " 
		    + Log.stackTrace(t));
                return false;
        }
        
        return true;
    }
    
    private NodeStat getNodeStat(int nodeId) throws HoneycombTestException {
        NodeStat nodeStatTmp = null;
        
        try {
            hwStat = new HwStat(runCommand(HoneycombCLISuite.HWSTAT_COMMAND));
            nodes = hwStat.getNodes();
        
            for(int nodeNum=0; nodeNum<nodes.size(); nodeNum++) {
                nodeStatTmp = (NodeStat) nodes.get(nodeNum);
                if (nodeId == nodeStatTmp.nodeId) 
                    return nodeStatTmp;
            }
        } 
        catch (HoneycombTestException hte) {
	    throw hte;
        }
        catch (Throwable e) {
            throw new HoneycombTestException("Unable to get node " + nodeId + ": ",e);
        }
        
        return null;
    }
    
    private DiskStat getDiskStat(int nodeId, int diskId) throws HoneycombTestException {
        DiskStat diskStatTmp = null;
        NodeStat nodeStatTmp = null;
        
        try {
            nodeStatTmp = getNodeStat(nodeId);
        } 
	catch (HoneycombTestException hte) {
	    throw hte;
	}
	catch (Throwable t) {
            throw new HoneycombTestException("Unable to get node " + 
                    nodeId + ": " + t);
        }
        
        if (nodeStatTmp != null) {
            for (int diskNum=0; diskNum<nodeStatTmp.disks.length; diskNum++) {
                diskStatTmp = nodeStatTmp.disks[diskNum];
                if (diskId == diskStatTmp.diskId) 
                    return diskStatTmp;
            }
        }
        
        return null;
    }
    
    private NodeStat getEnabledNodeStat()
    throws HoneycombTestException {

	int noIpmiNodes = 0;
	NodeStat nodeStat = null; 
        try {
            hwStat = new HwStat(runCommand(HoneycombCLISuite.HWSTAT_COMMAND));
            nodes = hwStat.getNodes();

	    IpmiTestAvailability ipmi = ipmi = getIpmiAvailability();
           
            for (int nodeNum=0; nodeNum<nodes.size(); nodeNum++) {
                nodeStat = (NodeStat) nodes.get(nodeNum);
                Log.DEBUG("Node: " + nodeStat.toString());
                    
                if (nodeStat.nodeId == 101) {
		   // ignore NODE-101
		   nodeStat = null;
		   continue;
		}
		if (ipmi != null && ipmi.isIpmiAvailable(nodeStat.nodeId) == false) {
		    // No ipmi available for node
		    noIpmiNodes++;
		    nodeStat = null;
		    continue;
		} 
		if (nodeStat.isInCluster)        
                    break;  
		nodeStat = null;
            }
        } 
	catch (HoneycombTestException hte) {
	    throw hte;
	}
	catch (Throwable e) {
            throw new HoneycombTestException("Unable to get node: " + e.getMessage(), e);
        }
        
        if (nodeStat == null) {
	    if (noIpmiNodes != 0) {
		Log.ERROR("Won't test. No online nodes that support ipmi were found.");
	    } else {
		Log.ERROR("Won't test  as all nodes are offline");
	    }
        }
        else
            Log.INFO("Node Status: " + nodeStat.toString());               
	return nodeStat;
    }
    
    private NodeStat getBoundaryNodeStat() throws HoneycombTestException {
        try {
            hwStat = new HwStat(runCommand(HoneycombCLISuite.HWSTAT_COMMAND));
            nodes = hwStat.getNodes();
            NodeStat nodeStat = (NodeStat) nodes.get(nodes.size()-1);  
            Log.INFO("Node Status: " + nodeStat.toString());
	    
	    
	    IpmiTestAvailability ipmi = ipmi = getIpmiAvailability();
            if (ipmi != null && ipmi.isIpmiAvailable(nodeStat.nodeId) == false) {
		// No ipmi available for node
		Log.ERROR("Won't test.  Boundary node " + nodeStat.nodeId + " does not support ipmi.");
		return null;
	    }
            
	    return nodeStat;
                                 
        } catch (Throwable e) {
            throw new HoneycombTestException("Unable to get boundary node: ", e);
        }   
    }
    
    private void setDiskStat(boolean isDiskEnabled) throws HoneycombTestException {   
	NodeStat nodeStat = null;
        try {
            hwStat = new HwStat(runCommand(HoneycombCLISuite.HWSTAT_COMMAND));
            nodes = hwStat.getNodes();
            
            diskStat = null;
            
            for (int nodeNum=0; nodeNum<nodes.size(); nodeNum++) {
                if (diskStat != null)
                    break;
                
                nodeStat = (NodeStat) nodes.get(nodeNum);
                if (nodeStat.nodeId == 101) // ignore NODE-101
                    continue;
                
                if (nodeStat.isInCluster) {
                    Log.DEBUG("Node: " + nodeStat.toString() + 
                            "No of disks: " + nodeStat.disks.length);
                    
                    for (int diskNum=0; diskNum<nodeStat.disks.length; diskNum++) {
                        diskStat = nodeStat.disks[diskNum];
                        Log.DEBUG("Disk: " + diskStat.toString() + ", enabled: " 
                                + diskStat.isEnabled);
                        if (((isDiskEnabled == true) && (diskStat.isEnabled)) ||
                            ((isDiskEnabled == false) && (!diskStat.isEnabled))) 
                            break;
                        else 
                            diskStat = null;
                    }
                }
                else
                    nodeStat = null;
            }
        } 
	catch (HoneycombTestException hte) {
	    throw hte;
	}
	catch (Throwable e) {
            throw new HoneycombTestException("Unable to set disk id: ",e);
        }
        
        if(nodeStat == null)
            Log.ERROR("Won't test " + 
                    HoneycombCLISuite.HWCFG_COMMAND + " as all nodes are offline");
        
        if(diskStat == null) {
            if (isDiskEnabled)
                Log.ERROR("Won't test " + 
                    HoneycombCLISuite.HWCFG_COMMAND + " as all disks are disabled");
            else
                Log.ERROR("Won't test " + 
                    HoneycombCLISuite.HWCFG_COMMAND + " as all disks are enabled");
        }
        else
            Log.INFO("Disk Status: " + diskStat.toString());    
    }
    
    
    /**
     * Determine whether ipmi exists on the various nodes on the cluster.
     * <P>
     * NOTE: IpmiTestAvailability assumes all nodes are up
     * @return IpmiTestAvailability
     */
    public IpmiTestAvailability getIpmiAvailability()
    throws HoneycombTestException {
	String [] result = null;
	try {
	    BufferedReader stdout = runCommandWithCellid(HoneycombCLISuite.VERSION_COMMAND + " -v");
            String stdoutLine = HCUtil.readLines(stdout);
            stdout.close();
            result = tokenizeIt(stdoutLine, "\n");
	}
	catch (Throwable t) {
	    throw new HoneycombTestException("Version command execution failed. ", t);
	}
	return new IpmiTestAvailability(result);
    }
    
    private void verifyAuditHwcfg(String command, String msg, 
            String value1, String value2) {
        ArrayList paramValueList = new ArrayList();
        paramValueList.add(value1);
        paramValueList.add(value2);
        verifyAuditInternalLog(command, msg,
                paramValueList, true); 
    }
}
