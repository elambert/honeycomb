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

/**
 *
 * @author jk142663
 */
public class CLITestSensors extends HoneycombCLISuite {
    
    /** Creates a new instance of CLITestSensors */
    public CLITestSensors() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: sensors\n");
        sb.append("\t o positive test - output syntax\n");
        sb.append("\t o negative test - invalid option\n");
        sb.append("\nnodes is a required argument for single " +
                "cell setup (-ctx nodes=<node_num>), default to 8\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if (errorString.indexOf(INVALID_CELLID_MESSAGE) != -1)
	    return true;
	
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testSensorsSyntax() {
        
        TestCase self = createTestCase("CLI_sensors_syntax");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;

        boolean isTestPass = false;

        try {
            if (isMultiCell()) {     
                isTestPass = verifyCommandStdout(false, 
                        HoneycombCLISuite.SENSORS_COMMAND,
                        HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);
            }
            else {
                BufferedReader output = runCommandWithoutCellid(
                    HoneycombCLISuite.SENSORS_COMMAND);
                
                String lsLine = HCUtil.readLines(output);
                output.close();
                isTestPass = checkSensorsStdout(lsLine);
            }    
                      
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }  
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testSensorsSyntaxWithCellid() {
        
        TestCase self = createTestCase("CLI_sensors_syntax_with_cellid");

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
                        HoneycombCLISuite.SENSORS_COMMAND, cellid);
            
                boolean isTestPassTmp = checkSensorsStdout(
                            HCUtil.readLines(output));  
                output.close();
                    
                if(!isTestPassTmp)
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
    
    public void testSensorsNegativeTest() {
        
        TestCase self = createTestCase("CLI_sensors_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        String [] lsInvalidArgs = {
            HoneycombCLISuite.SENSORS_COMMAND + " -s",
            HoneycombCLISuite.SENSORS_COMMAND + " " + HoneycombCLISuite.SENSORS_COMMAND};
        
        // will test with invalid cellid for multicell
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.SENSORS_COMMAND
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], HoneycombCLISuite.SENSORS_HELP);
            
            if (!isTestPass)
                noOfFailure++;
            
            // for multi-cell only
            if (isMultiCell()) {
                
                // execute invalid command with cellid
                setCellid();
                isTestPass = verifyCommandStdout(
                        true, lsInvalidArgs[i], HoneycombCLISuite.SENSORS_HELP);
                
                if (!isTestPass)
                    noOfFailure++;
            }
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
        
        
        if(noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }

    private final String UNAVAILABLE_PATTERN = "Unavailable";
    private final String VOLTS_PATTERN = "(([^\\s]+\\s+Volts)|" + UNAVAILABLE_PATTERN + ")";
    private final String DEGRESS_PATTERN = "((\\d+\\s+degrees C)|" + UNAVAILABLE_PATTERN + ")";
    private final String RPM_PATTERN = "((\\d+\\s+RPM)|" + UNAVAILABLE_PATTERN + ")";
    
    private boolean checkSensorsStdout(String lsStdout) {
        boolean isCorrectSensors = false;
                
        try {
            Pattern regexpDDR = Pattern.compile("^DDR Voltage\\s+" 
		+ VOLTS_PATTERN + "$");
            Pattern regexpCPU = Pattern.compile("^CPU Voltage\\s+" 
		+ VOLTS_PATTERN + "$");
            Pattern regexpVCC_3_3_V = Pattern.compile("^VCC 3.3V\\s+" 
		+ VOLTS_PATTERN + "$");
            Pattern regexpVCC_5_V = Pattern.compile("^VCC 5V\\s+" 
		+ VOLTS_PATTERN + "$");
            Pattern regexpVCC_12_V = Pattern.compile("^VCC 12V\\s+" 
		+ VOLTS_PATTERN + "$");
            Pattern regexpBattery = Pattern.compile("^Battery Voltage\\s+" 
		+ VOLTS_PATTERN + "$");
            Pattern regexpCPUTemp = Pattern.compile("^CPU Temperature\\s+"
		+ DEGRESS_PATTERN + "$");
            Pattern regexpSystemTemp = Pattern.compile("^System Temperature\\s+"
		+ DEGRESS_PATTERN + "$");
            Pattern regexpSystemFan1 = Pattern.compile("^System Fan 1 speed\\s+"
		+ RPM_PATTERN + "$");
            Pattern regexpSystemFan2 = Pattern.compile("^System Fan 2 speed\\s+"
		+ RPM_PATTERN + "$");
            Pattern regexpSystemFan3 = Pattern.compile("^System Fan 3 speed\\s+"
		+ RPM_PATTERN + "$");
            Pattern regexpSystemFan4 = Pattern.compile("^System Fan 4 speed\\s+"
		+ RPM_PATTERN + "$");
            Pattern regexpSystemFan5 = Pattern.compile("^System Fan 5 speed\\s+"
		+ RPM_PATTERN + "$");
            
            MessageFormat form = new MessageFormat("NODE-1{0}:");
            int nodeArg = 01;
        
            String [] lsLine =  tokenizeIt(lsStdout, "\n");
            int currentLine = 0;
            int noOfFailure = 0;
        
            // for all nodes
            for (int i=0; i<16; i++) {
                
                if ((i == 8) && (lsLine.length <= currentLine))
                    break;           
                
                String nodeLineAct = lsLine[currentLine++];           
                String nodeArgStr = new Integer(nodeArg).toString();
                if (nodeArg < 10)
                    nodeArgStr = "0" + nodeArgStr;
                Object [] formatArg = {nodeArgStr};
                String nodeLineExp = form.format(formatArg);
                nodeArg++;
            
                if (!nodeLineAct.equals(nodeLineExp)) 
                    noOfFailure++;
                
                if (!isCorrectPattern(lsLine[currentLine++], regexpDDR)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpCPU)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpVCC_3_3_V)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpVCC_5_V)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpVCC_12_V)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpBattery)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpCPUTemp)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpSystemTemp)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpSystemFan1)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpSystemFan2))
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpSystemFan3)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpSystemFan4)) 
                    noOfFailure++;
                if (!isCorrectPattern(lsLine[currentLine++], regexpSystemFan5)) 
                    noOfFailure++;
            }
            
            if (noOfFailure == 0)
                isCorrectSensors = true;
            
        } catch (Exception e) {
            Log.ERROR("Error while parsing sensors stdout: " + e);
        }
        
        return isCorrectSensors;
    }
    
    private boolean isCorrectPattern(String line, Pattern expPattern) {
        Matcher matcher = expPattern.matcher(line);
                    
        // verify the output pattern
        if (!matcher.matches()) {
            Log.ERROR("Unexpected output: " + line + "; Expected: " + 
                    expPattern.toString());
            return false;
        }
        else {
           Log.DEBUG(line);
           return true;
        }
    }
}
