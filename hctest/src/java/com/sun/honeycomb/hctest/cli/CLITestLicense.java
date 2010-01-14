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

/**
 *
 * @author jk142663
 */
public class CLITestLicense extends HoneycombCLISuite {
    
    private static String NO_LICENSE_OUTPUT = null;
    
    /** Creates a new instance of CLITestLicense */
    public CLITestLicense() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: license\n");
        sb.append("\t o positive test - set license\n");
        sb.append("\t o negative test - invalid option, " +
                "set license using invalid string\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        NO_LICENSE_OUTPUT = getAdminResourceProperties().
                getProperty("cli.license.not_set");
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if (errorString.indexOf(INVALID_CELLID_MESSAGE) != -1)
	    return true;
	
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testLicenseSyntax() {
        
        TestCase self = createTestCase("CLI_license_syntax");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        try {
            if (isMultiCell()) {
                isTestPass = verifyCommandStdout(false, 
                        HoneycombCLISuite.LICENSE_COMMAND,
                        HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);
                }                
                else {
                    BufferedReader output = runCommandWithoutCellid(
                        HoneycombCLISuite.LICENSE_COMMAND);
                    String [] lsOutput = excudeCustomerWarning(output);
                    output.close();
                    isTestPass = checkLicenseStdout(lsOutput[0]);
                }           
                                 
            if (isTestPass) 
                self.testPassed();
            else
                self.testFailed();
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
    }
    
    public void testLicenseSyntaxWithCellid() {
        
        TestCase self = createTestCase("CLI_license_syntax_with_cellid");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        int noOfFailure = 0;
        
        try {
            ArrayList allCellid = getAllCellid();
            
            for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
            
                int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                setCellid(cellid);
                    
                BufferedReader output = runCommandWithCellid(
                        HoneycombCLISuite.LICENSE_COMMAND, cellid);
                
                String [] lsOutput = excudeCustomerWarning(output);
                output.close();
                String line = lsOutput[0].trim();
                
                if (isMultiCell())
                    line = lsOutput[1].trim();
                
                isTestPass = checkLicenseStdout(line);  
                if (!isTestPass)
                    noOfFailure++;          
            }
              
            if (noOfFailure == 0)
                isTestPass = true;       
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
            isTestPass = false;
        }
        
        if (isTestPass) 
            self.testPassed();
        else
            self.testFailed();
            
    }
    
    public void testLicenseSetValue() throws HoneycombTestException {       
        TestCase self = createTestCase("CLI_license_set_value");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
         
        if (self.excludeCase()) return;
   
        boolean isTestPass = false;
        int noOfFailure = 0;   
        
        // license value will be test_dateTime
        String licenseStr = "license_" + getDateTime();
        String [] lsNewLicense = {"\"license\"", "'license'", "L", licenseStr};
         
        try {
            ArrayList allCellid = getAllCellid();
            
            // for each cell
            for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
            
                int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                setCellid(cellid);
                
                for (int i=0; i<lsNewLicense.length; i++) {
                
                    String newLicense = lsNewLicense[i];
                    
                    // set license value
                    setCurrentInternalLogFileNum();
                    setLicense(newLicense);
            
                    // verify audit log
                    verifyAuditInternalLog(HoneycombCLISuite.LICENSE_COMMAND,
                        "info.adm.setLicense", null, true);  
        
                    String currentLicense = getCurrentLicense();
            
                    if (currentLicense.equals(newLicense)) {
                        Log.INFO("License <" + currentLicense + 
                            "> is set proeprly");
                    }
                    else {
                        Log.ERROR("Actual license: " + currentLicense + 
                            ", Expected license: " + newLicense);
                        noOfFailure++;
                    }
                }
            }
            
            if (noOfFailure == 0)
                isTestPass = true;
            
        } catch (Throwable t) {
            Log.ERROR("Error while setting locale:" + t.toString());
            isTestPass = false;
        }
            
        if (isTestPass )
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testLicenseNegativeTest() {
        
        TestCase self = createTestCase("CLI_license_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        String [] lsInvalidArgs = {
            HoneycombCLISuite.LICENSE_COMMAND + " \" \"",
            HoneycombCLISuite.LICENSE_COMMAND + " -l",
            HoneycombCLISuite.LICENSE_COMMAND + " value1 value2"};
        
        // will test without cellid and with invalid cellid for multicell
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.LICENSE_COMMAND,
            HoneycombCLISuite.LICENSE_COMMAND + " value1"
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute invalid command
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], HoneycombCLISuite.LICENSE_HELP);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute invalid command with cellid
            setCellid();
            isTestPass = verifyCommandStdout(
                    true, lsInvalidArgs[i], HoneycombCLISuite.LICENSE_HELP);
                
            if (!isTestPass)
                noOfFailure++;
        }
        
        // execute command without cellid
        if (isMultiCell()) {
            isTestPass = verifyCommandStdout(
                    false, lsInvalidMulticellArgs[1], 
                    HoneycombCLISuite.LICENSE_HELP);
        
            if (!isTestPass)
                noOfFailure++;
        }
    
        // execute command with invalid cellid
        int invalidCellId = getInvalidCellid();
    
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
            String formattedCommand = formatCommandWithCellid(
                    lsInvalidMulticellArgs[i], invalidCellId);
        
            isTestPass = verifyCommandStdout(false, formattedCommand, 
                    HoneycombCLISuite.LICENSE_HELP);
        
            if (!isTestPass)
                noOfFailure++;
        }
        
        if(noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    private void setLicense(String license) {
        try {
            Log.INFO("Setting license to: " + license);
            
            BufferedReader output = runCommand(
                    HoneycombCLISuite.LICENSE_COMMAND + " " + license);
            String line = null;
               
            while ((line = output.readLine()) != null) {
                Log.DEBUG(HoneycombCLISuite.LICENSE_COMMAND + " output: " + line);
            }
            output.close();
                
          } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
    }
    
    private String getCurrentLicense() throws HoneycombTestException {
        String currentLicense = NO_LICENSE_OUTPUT;
        
        try {
            BufferedReader output = runCommand(HoneycombCLISuite.LICENSE_COMMAND);
            String [] lsOutput = excudeCustomerWarning(output);                
            output.close();
            
            String line = lsOutput[0];                
            if (isMultiCell())
                line = lsOutput[1].trim();
                
            if (line != null) {                
                Log.INFO(HoneycombCLISuite.LICENSE_COMMAND + " output: " + line);
                
                // verify the output string
                // the output could be the "License Not Yet Entered" or
                // the value of license
                if (!line.equals(NO_LICENSE_OUTPUT)) {
                    String [] lsLicenseOutput = tokenizeIt(line, ":");
                        
                    if (lsLicenseOutput.length > 2)
                        throw new HoneycombTestException(
                                "Unexpected license output format: " + line);
                        
                    else {
                        currentLicense = lsLicenseOutput[1].trim();
                        Log.DEBUG("License: " + currentLicense);                        
                    }
                }
            }
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing license CLI:" + t.toString());
        }  
        
        return currentLicense;
    }
    
    private boolean checkLicenseStdout(String line) {
        if (line == null)
            return false;
        
        line = line.trim();
        
        try { 
            if (line.equals(NO_LICENSE_OUTPUT)) 
                return true;
            else {
                String [] lsOutput = tokenizeIt(line, ":");
                        
                if (lsOutput.length > 2) {
                    Log.ERROR("Unexpected output format: " + line);
                    return false;
                }        
                else {
                    String currentLicense = lsOutput[1].trim();
                    Log.DEBUG("License: " + currentLicense);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.ERROR("Error while parsing CLI: " + e);
        }
        
        return false;
    }
    
}
