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
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLI wipe test can be run in a loop, default loop 
 * number is set to 1. use "-ctx iterations=iteration_num"
 * to specify the number of times to run wipe test.
 *
 * if "nohadb" flag is set, then test will take ~7 mins 
 * to run a single iteartion as it will skip hadb verification.
 * Otherwise test will take max ~37 mins to run a single iteration 
 * as it will wait max 30 mins for hadb to come up.
 *   
 */
public class CLITestWipe extends HoneycombCLISuite {
    
    /** total no of loops the test will run */
    private static int noOfLoop = 1; 
    private static boolean doHadbVerification = true;
    
    private static final String WIPE_F_STRING = " -F ";
    private static final String WIPE_FORCE_STRING = " --force ";
    
    private static String WIPE_F_COMMAND = null;
    private static String WIPE_FORCE_COMMAND = null;
    
    private static final double FREE_SPACE_AFTER_WIPE = 2;
    
    private static GregorianCalendar beforeWipeTimeStamp = null;
    private static int currentYear = 0;
    
    /** Creates a new instance of CLITestWipe */
    public CLITestWipe() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: wipe\n");
        sb.append("\t - positive test: wipe the cluster in loop, default is set to 1 \n");
        sb.append("\t - negative test: invalid option\n");
        sb.append("\tUsage: wipe test runs in part of CLI test suite, but " +
                "user can run wipe test in standalone mode as follow:\n");
        sb.append("\t\t - /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestWipe " +
                "-ctx nodes=8 -ctx iterations=10 -ctx cluster=dev501:nohadb\n");
        sb.append("\t\t - /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestWipe " +
                "-ctx nodes=8 -ctx cluster=dev501\n");
        sb.append("\t\t - /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestWipe " +
                "-ctx nodes=8 -ctx iterations=10 -ctx cluster=dev501\n");
        sb.append("\t\t o nodes is a required argument for single " +
                "cell setup, default to 8\n");
        sb.append("\t\t o iterations is the number of times to run wipe test, " +
                "default to 1\n");
        sb.append("\t\t o if nohadb flag is specified, then wipe test will " +
                "skip the hadb verification. if nohadb flag is not set, then test " +
                "will verify hadb state after wiped cluster\n");
         
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        WIPE_F_COMMAND = HoneycombCLISuite.WIPE_COMMAND + WIPE_F_STRING;
        WIPE_FORCE_COMMAND = HoneycombCLISuite.WIPE_COMMAND + WIPE_FORCE_STRING;    
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if (errorString.indexOf(INVALID_CELLID_MESSAGE) != -1)
	    return true;
	
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testWipe() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_wipe");
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
            // verify hadb state before running wipe test
            ArrayList allCellid = getAllCellid();
            for (int i = 0; i < allCellid.size(); i++) {
                Integer cellid = (Integer) allCellid.get(i);
                if (!verifyHadb(cellid.intValue())) {
                    self.testFailed("Unexpected hadb state - abort wipe test");
                    return;
                }
            }
        }
        
        // run wipe in loop
        int noOfFailure = 0;
        for (int count = 0; count < noOfLoop; count++) {
            Log.INFO("\n******  Start Iteration # " + count + " ******\n");
            
            setCurrentInternalLogFileNum();
            boolean isTestpass = wipeHive(WIPE_FORCE_COMMAND);
            verifyAuditInternalLog(HoneycombCLISuite.WIPE_COMMAND, 
                "info.adm.wipe", null, true);  
            
            if (!isTestpass) {
                noOfFailure ++;
                break;
            }
        }
         
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testWipeNegativeTest() {
        
        TestCase self = createTestCase("CLI_wipe_Negative_Test");
        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        Integer cellid = (Integer) getAllCellid().get(0);
        
        String [] lsInvalidArgs = {
            HoneycombCLISuite.WIPE_COMMAND + HoneycombCLISuite.CELL_ID_ARG + 
                    ((Integer) getAllCellid().get(0)).intValue(),
            HoneycombCLISuite.WIPE_COMMAND + " " + HoneycombCLISuite.WIPE_COMMAND,
            HoneycombCLISuite.WIPE_COMMAND + " -f",
            HoneycombCLISuite.WIPE_COMMAND + " --F",
            HoneycombCLISuite.WIPE_COMMAND + " --Force",
            HoneycombCLISuite.WIPE_COMMAND + " -force"
        };
        
        // will test with invalid cellid 
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.WIPE_COMMAND,
            WIPE_F_COMMAND, 
            WIPE_FORCE_COMMAND
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command without cellid
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], HoneycombCLISuite.WIPE_HELP);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute invalid command with cellid
            setCellid();
            isTestPass = verifyCommandStdout(
                    true, lsInvalidArgs[i], HoneycombCLISuite.WIPE_HELP);
                
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
    
    private boolean wipeHive(String command) {
        int noOfFailure = 0;
        
        try {
            beforeWipeTimeStamp = getTimeStamp();
           
            BufferedReader output = runCommandWithoutCellid(command);
            String line = null;
                
            while ((line = output.readLine()) != null) {                
                Log.INFO(line);                
            }
            output.close();
            
            ArrayList allCellid = getAllCellid();
            for (int i = 0; i < allCellid.size(); i++) {
                Integer cellid = (Integer) allCellid.get(i);
                
                if (!verifyWipe(cellid.intValue()))
                    noOfFailure++;
            }            
        } catch (Throwable t) {
            Log.ERROR("Error while wiped hive:" + Log.stackTrace(t));
        } 
        
        if (noOfFailure != 0) {
            Log.ERROR("Hive is not wiped properly");
            return false;
        }
        else {
            Log.INFO("Hive is wiped properly");
            return true;
        }
    }
    
    private boolean verifyWipe(int cellid) {
        int noOfFailure = 0;
        
        Log.SUM("Verify CELL:" + cellid);
        Integer cellidInt = new Integer(cellid);
        
        // verify hadb state
        if (doHadbVerification) {
            if (!verifyHadb(cellid))
                noOfFailure++;
        }
        
        // verify free space using df command
        if (!verifyFreeSpaceInDf(cellid))
            noOfFailure++;
        
        // verify timestamp 
        if (!verifyTimestamp(cellid))
            noOfFailure++;
                
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    private boolean verifyFreeSpaceInDf(int cellid) {
        int noOfFailure = 0;
        
        try {
            Log.INFO("*** verify free space");
            BufferedReader output = runCommandWithCellid(
                        HoneycombCLISuite.DF_COMMAND + " -h", cellid);
            String lines = HCUtil.readLines(output);
            output.close();
            Pattern regexp = Pattern.compile(
                "^Total:(.*)" +
		"; Usage:\\p{Zs}+([^\\s]+)%$");
          
            String [] lsLine =  tokenizeIt(lines, "\n");
            String line = null;
                        
            for (int lineNo=0; lineNo<lsLine.length; lineNo++) {                
                line = lsLine[lineNo];
                 Log.INFO(line);   
                if (line.trim().equals("")) 
                    continue;
                        
                Matcher matcher = regexp.matcher(line);
                        
                // verify the output pattern
                if (!matcher.matches()) {
                    noOfFailure++;
                    Log.ERROR("Unexpected output format: " + line + 
                            ", Expected: " + matcher.toString());
                }  
                else {
                   String sPerUsed = matcher.group(2);
		   double perUsed = Double.valueOf(sPerUsed).doubleValue(); 
                   if (perUsed < FREE_SPACE_AFTER_WIPE)
                       Log.INFO("Free space of " + perUsed 
                           + " is displayed properly");
                   else {
                       Log.ERROR("Unexpected free space: " + perUsed + "; Expected value less than " +
                               FREE_SPACE_AFTER_WIPE);
                        noOfFailure++;
                   }
                }
            }
        } catch (Throwable t) {
            noOfFailure++;
            Log.ERROR("Error while verify free space using df command:" + 
                    Log.stackTrace(t));
        }
        
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }    
    
    private boolean verifyTimestamp(int cellid) {
        Log.INFO("*** Verify timestamp");
            
        String adminIp = (String) getAllAdminIp().get(new Integer(cellid));
        String cheatIp = (String) getAllCheatIp().get(new Integer(cellid));
        CheatNode cheat = new CheatNode(cheatIp);

        int noOfFailure = 0;
        
        try {
            int totalNode = getNodeNum();
            Log.INFO("Total Node:" + totalNode);
            
            for (int nodeNum = 1; nodeNum <= totalNode; nodeNum++) {
                ClusterNode node = new ClusterNode(adminIp, cheat, nodeNum);
                Log.INFO("********** Node: " + node.toString() + " **********");
                
                // verify timestamp only if node is up
                if (!node.ping()) {
                    Log.ERROR("Node " + node.toString() + " is not pingable after wipe.");
                    noOfFailure ++;
                    continue;
                }
               
                String timestampOutput = node.runCmd("ls -ld /data/?/00");
                String [] lsDataDiskTimestamp = tokenizeIt(timestampOutput, "\n");
            
                for (int i=0; i<lsDataDiskTimestamp.length; i++) {
                    Log.INFO(lsDataDiskTimestamp[i]);
                    String [] dataDiskTokenize = 
                            tokenizeIt(lsDataDiskTimestamp[i], " ");
                    
                    int month = getMonthNum(dataDiskTokenize[5]);
                    int date = new Integer(dataDiskTokenize[6]).intValue();
                    
                    // drwxr-xr-x 102 root     root        1536 Aug 11 01:10 /data/2/00
                    // or
                    // drwxr-xr-x 102 root     root        1536 Aug 11 2006 /data/2/00
                    String [] time = null;
                    int hour = 0; int minute = 0; int year = 0;
                    
                    try {
                        time = tokenizeIt(dataDiskTokenize[7], ":");
                    
                        hour = new Integer(time[0]).intValue();
                        minute = new Integer(time[1]).intValue();
                        year = currentYear;
                        
                    } catch (Exception e) {
                        year = new Integer(dataDiskTokenize[7]).intValue();
                    }
                    
                    GregorianCalendar afterWipeTimeStamp = 
                            new GregorianCalendar(year, month, date, hour, minute);
                    
                    if (!afterWipeTimeStamp.after(beforeWipeTimeStamp)) {
                        Log.ERROR("Unexpected timestamp");                       
                        noOfFailure++;
                    }
                }
            }
        } catch (Throwable t) {
            noOfFailure++;
            Log.ERROR("Error while verifying timestamp of all data disk:" + 
                    Log.stackTrace(t));
        }
        
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    private GregorianCalendar getTimeStamp() {
        ClusterNode node = new ClusterNode(getAdminVIP(), null, 1);
        GregorianCalendar calender = null;
        
        try {
            String timeStamp = node.runCmd("date");
            Log.INFO("Timestamp before wiping cluster: " + timeStamp);
            
            String [] timeStampTokenize = tokenizeIt(timeStamp, " ");
         
            int year = new Integer(timeStampTokenize[5]).intValue();
            int month = getMonthNum(timeStampTokenize[1]);
            int date = new Integer(timeStampTokenize[2]).intValue();
    
            String [] time = tokenizeIt(timeStampTokenize[3], ":");
            int hour = new Integer(time[0]).intValue();
            int minute = new Integer(time[1]).intValue();
            
            calender = new GregorianCalendar(year, month, date, hour, minute);  
            currentYear = year;
        } catch (Throwable t) {
            Log.ERROR("Error while tokenized date:" + Log.stackTrace(t));
        }
        
        return calender;
    }
    
    private int getMonthNum(String month) {
        if (month.equalsIgnoreCase("Jan"))
            return 0;
        else if (month.equalsIgnoreCase("Feb"))
            return 1;
        else if (month.equalsIgnoreCase("Mar"))
            return 2;
        else if (month.equalsIgnoreCase("Apr"))
            return 3;
        else if (month.equalsIgnoreCase("May"))
            return 4;
        else if (month.equalsIgnoreCase("Jun"))
            return 5;
        else if (month.equalsIgnoreCase("Jul"))
            return 6;
        else if (month.equalsIgnoreCase("Aug"))
            return 7;
        else if (month.equalsIgnoreCase("Sep"))
            return 8;
        else if (month.equalsIgnoreCase("Oct"))
            return 9;
        else if (month.equalsIgnoreCase("Nov"))
            return 10;
        else
            return 11;
    }
    
}
