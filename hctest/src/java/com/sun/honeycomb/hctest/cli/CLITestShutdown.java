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

/**
 *
 * @author jk142663
 */
public class CLITestShutdown extends HoneycombCLISuite {
    
    private static final String SHUTDOWN_F_STRING = " -F ";
    private static final String SHUTDOWN_FORCE_STRING = " --force ";
    private static final String SHUTDOWN_A_STRING = " -A ";
    private static final String SHUTDOWN_ALL_STRING = " --all ";
    
    private static String SHUTDOWN_F_COMMAND = null;
    private static String SHUTDOWN_FORCE_COMMAND = null;
    private static String SHUTDOWN_A_COMMAND = null;
    private static String SHUTDOWN_ALL_COMMAND = null;
    
    /** Creates a new instance of CLITestShutdown */
    public CLITestShutdown() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: shutdown\n");
        sb.append("\t o positive test - shutdown cluster \n");
        sb.append("\t o negative test - invalid option\n");
        sb.append("\nnodes is a required argument for single " +
                "cell setup (-ctx nodes=<node_num>), default to 8\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        SHUTDOWN_F_COMMAND = HoneycombCLISuite.SHUTDOWN_COMMAND + SHUTDOWN_F_STRING;
        SHUTDOWN_FORCE_COMMAND = HoneycombCLISuite.SHUTDOWN_COMMAND + SHUTDOWN_FORCE_STRING; 
        SHUTDOWN_A_COMMAND = HoneycombCLISuite.SHUTDOWN_COMMAND + SHUTDOWN_A_STRING;
        SHUTDOWN_ALL_COMMAND = HoneycombCLISuite.SHUTDOWN_COMMAND + SHUTDOWN_ALL_STRING;   
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if (errorString.indexOf(INVALID_CELLID_MESSAGE) != -1)
	    return true;
	
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testShutdown() throws HoneycombTestException {
        
        String [] lsTestCase = {
            "CLI_F_shutdown", 
            "CLI_force_shutdown"
        };
        
        String [] lsCommand = {
            SHUTDOWN_F_COMMAND, 
            SHUTDOWN_FORCE_COMMAND};
        
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i]);
        
            self.addTag(Tag.NORUN); // remove me!
            self.addTag(Tag.HOURLONG);
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!

            if (self.excludeCase()) return;

            boolean isTestPass = shutDownSilo(lsCommand[i]);
            
            upSilo();
        
            if(isTestPass)
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testShutdownWithCellid() throws HoneycombTestException {
        
        String [] lsTestCase = {
            "CLI_F_shutdown_with_cellid", 
            "CLI_force_shutdown_with_cellid"
        };
        
        String [] lsCommand = {
            SHUTDOWN_F_COMMAND, 
            SHUTDOWN_FORCE_COMMAND};
        
        ArrayList allCellid = getAllCellid();
        
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i]);
        
            self.addTag(Tag.NORUN); // remove me!
            self.addTag(Tag.HOURLONG);
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!

            if (self.excludeCase()) return;

            boolean isTestPass = false;
            int noOfFailure = 0;
            
            for (int cellCount = 0; cellCount<allCellid.size(); cellCount++) {
                Integer cellid = (Integer) allCellid.get(cellCount);
            
                String adminIp = (String) getAllAdminIp().get(cellid);
                String dataIp = (String) getAllDataIp().get(cellid);
                String cheatIp = (String) getAllCheatIp().get(cellid);
                
                isTestPass = shutDownCell(lsCommand[i], cellid.intValue());
            
                if (!isTestPass)
                    noOfFailure++;
                
                upCell(adminIp, dataIp, cheatIp);
            }
            
            if (noOfFailure == 0)
                self.testPassed();
            else
                self.testFailed();
        }
    }
        
    public void testShutdownNegativeTest() {
        
        TestCase self = createTestCase("CLI_shutdown_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        String [] lsInvalidArgs = {
            HoneycombCLISuite.SHUTDOWN_COMMAND + " " + HoneycombCLISuite.SHUTDOWN_COMMAND,
            HoneycombCLISuite.SHUTDOWN_COMMAND + " -f",
            HoneycombCLISuite.SHUTDOWN_COMMAND + " --F",
            HoneycombCLISuite.SHUTDOWN_COMMAND + " --Force",
            HoneycombCLISuite.SHUTDOWN_COMMAND + " -force",
            HoneycombCLISuite.SHUTDOWN_COMMAND + " -a",
            HoneycombCLISuite.SHUTDOWN_COMMAND + " --A",
            HoneycombCLISuite.SHUTDOWN_COMMAND + " --All",
            HoneycombCLISuite.SHUTDOWN_COMMAND + " -all",
            SHUTDOWN_A_COMMAND + " -f",
            SHUTDOWN_ALL_COMMAND + " -f",
            SHUTDOWN_A_COMMAND + " --F",
            SHUTDOWN_ALL_COMMAND + " --F",
            SHUTDOWN_A_COMMAND + " --Force",
            SHUTDOWN_ALL_COMMAND + " --Force",
            SHUTDOWN_A_COMMAND + " -force",
            SHUTDOWN_ALL_COMMAND + " -force",            
            SHUTDOWN_F_COMMAND + " -a",
            SHUTDOWN_FORCE_COMMAND + " -a",
            SHUTDOWN_F_COMMAND + " --A",
            SHUTDOWN_FORCE_COMMAND + " --A",
            SHUTDOWN_F_COMMAND + " --All",
            SHUTDOWN_FORCE_COMMAND + " --All",
            SHUTDOWN_F_COMMAND + " -all",
            SHUTDOWN_FORCE_COMMAND + " -all" 
        };
        
        // will test with invalid cellid 
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.SHUTDOWN_COMMAND,
            SHUTDOWN_F_COMMAND, 
            SHUTDOWN_FORCE_COMMAND
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command without cellid
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], HoneycombCLISuite.SHUTDOWN_HELP);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute invalid command with cellid
            setCellid();
            isTestPass = verifyCommandStdout(
                    true, lsInvalidArgs[i], HoneycombCLISuite.SHUTDOWN_HELP);
                
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
        
    private boolean shutDownSilo(String command) {
        int noOfFailure = 0;
        
        try {
            BufferedReader output = runCommandWithoutCellid(command);
            String line = null;
                
            while ((line = output.readLine()) != null) {                
                Log.INFO(command + " output: " + line);                
            }
            output.close();
           
            HCUtil.doSleep(getSleepTime(), "Wait to shutdown completely...");
            
            ArrayList allCellid = getAllCellid();
            for (int i = 0; i < allCellid.size(); i++) {
                Integer cellid = (Integer) allCellid.get(i);
                String adminIp = (String) getAllAdminIp().get(cellid);
                String dataIp = (String) getAllDataIp().get(cellid);
                String cheatIp = (String) getAllCheatIp().get(cellid);
                
                boolean isPingAdminIp = isPing(cheatIp, adminIp);
                boolean isPingDataIp = isPing(cheatIp, dataIp);
                
                if ((isPingAdminIp) || (isPingDataIp))
                    noOfFailure++;
            }            
        } catch (Throwable t) {
            Log.ERROR("Error while shutdown silo:" + t.toString());
        } 
        
        if (noOfFailure != 0) {
            Log.ERROR("Silo is not shutdown properly");
            return false;
        }
        else {
            Log.INFO("Silo is shutdown properly");
            return true;
        }
    }
    
    private boolean shutDownCell(String command, int cellid) {
        boolean isPing = false;
        
        Integer cellidInt = new Integer(cellid);
        String adminIp = (String) getAllAdminIp().get(cellidInt);
        String dataIp = (String) getAllDataIp().get(cellidInt);
        String cheatIp = (String) getAllCheatIp().get(cellidInt);
        
        try {
            BufferedReader output = runCommandWithCellid(command, cellid);
            String line = null;
                
            while ((line = output.readLine()) != null) {                
                Log.INFO(command + " output: " + line);                
            }
            output.close();
           
            HCUtil.doSleep(getSleepTime(), "Wait to shutdown completely...");
            
            boolean isPingAdminIp = isPing(cheatIp, adminIp);
            boolean isPingDataIp = isPing(cheatIp, dataIp);
            
            if ((isPingAdminIp) || (isPingDataIp))
                isPing = true;       
            
        } catch (Throwable t) {
            Log.ERROR("Error while shutdown cell:" + t.toString());
        } 
        
        if (isPing) {
            Log.ERROR("Cell with id " + cellid + " is not shutdown properly");
            return false;
        }
        else {
            Log.INFO("Cell with id " + cellid + " shuts down properly");            
            return true;
        }
    }
    
    private void upSilo() {
        ArrayList allCellid = getAllCellid();
        
        for (int i = 0; i < allCellid.size(); i++) {
            Integer cellid = (Integer) allCellid.get(i);
            
            String adminIp = (String) getAllAdminIp().get(cellid);
            String dataIp = (String) getAllDataIp().get(cellid);
            String cheatIp = (String) getAllCheatIp().get(cellid);                
             
            upCell(adminIp, dataIp, cheatIp);
        }                
    }
    
    private void upCell(String adminIp, String dataIp, String chatIp) {
        try {
            Log.DEBUG("Copy powerUp.sh script from client to cheat node");
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("scp /opt/test/etc/powerUp.sh root@" +
                    chatIp + ":/export");
        
            Log.INFO("Power up the cluster from cheat node using ipmitool");            
            BufferedReader output = runCommandAsRoot(chatIp, 
                    "/usr/bin/sh /export/powerUp.sh");
            String line = null;
                
            while ((line = output.readLine()) != null) {                
                Log.INFO("ipmitool output: " + line);                
            }
            output.close();
           
            HCUtil.doSleep(getSleepTime(), 
                    "Wait to power up completely using ipmi tool...");
            boolean isPingAdminIp = isPing(chatIp, adminIp);  
            boolean isPingDataIp = isPing(chatIp, dataIp);  
            if ((!isPingAdminIp) || (!isPingDataIp)) {
                Log.ERROR("Abort the rest of the tests as cluster is down");
                System.exit(-1);
            }
            else
                Log.INFO("Cluster is up");
            
        } catch (Throwable t) {
            Log.ERROR("error while power up cell using ipmi tool:" + t.toString());
        } 
    }
    
}
