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

/**
 *
 * @author jk142663
 */
public class CLITestCelladm extends HoneycombCLISuite {
    
    private static final String [] CELLADM_STATUS = {"unknown", "not ready", "ready", "expanding", "complete"};
    private static final String CELLADM_EXPANSION_STATUS = "Expansion status is:";
    private static final String CELLADM_MULTICELL_ERROR = 
            "Expanding a cell isn't possible in a multicell configuration.";
                
    /** Creates a new instance of CLITestCelladm */
    public CLITestCelladm() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: celladm\n");
        sb.append("\t o positive test - syntax\n");
        sb.append("\t o negative test - invalid option\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	if (errorString.indexOf(
                HoneycombCLISuite.MULTICELL_EXPANSION_MESSAGE[0]) != -1) {
	    return true;
	}
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testCelladmSyntax() {        
        TestCase self = createTestCase("CLI_celladm_syntax");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;
        
        boolean isTestPass = false;
        
        try {
            boolean isMultiCell = isMultiCell();
            
            // multicell assumes full clusters, so this command is invalid 
            // in all cases except that of a single node cluster
            if (isMultiCell) {
                String line = null;
		isTestPass = verifyCommandStdout(false, 
		    HoneycombCLISuite.CELLADM_COMMAND,
		    HoneycombCLISuite.MULTICELL_EXPANSION_MESSAGE);
            }
            else {
                String currentStatus = getExpansionStatus();
            
                boolean isValidStatus = false;
                for (int i=0; i<CELLADM_STATUS.length; i++) {
                    if (currentStatus.equals(CELLADM_STATUS[i])) {
                        isValidStatus = true;
                        break;
                    }
                }
            
                if (isValidStatus) {
                    Log.INFO("Expansion status is vaild");
                    isTestPass = true;
                }
                else {
                    Log.ERROR("Invalid expansion status: " + currentStatus);
                    
                    String lsValidStatus = "";
                    for (int i=0; i<CELLADM_STATUS.length; i++)
                        lsValidStatus += CELLADM_STATUS[i] + " ";
                    Log.INFO("Valid Status: " + lsValidStatus);
                }
            }
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
        
        if(isTestPass )
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testCelladmNegativeTest() {
        
        TestCase self = createTestCase("CLI_celladm_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        setCellid();
        
        String [] lsInvalidArgs = {HoneycombCLISuite.CELLADM_COMMAND + " " + 
            HoneycombCLISuite.CELLADM_COMMAND,
            HoneycombCLISuite.CELLADM_COMMAND + " --expand",
            HoneycombCLISuite.CELLADM_COMMAND + " Expand",
            HoneycombCLISuite.CELLADM_COMMAND + " EXPAND",
            HoneycombCLISuite.CELLADM_COMMAND + " expand expand",
            HoneycombCLISuite.CELLADM_COMMAND + " test",
            formatCommandWithCellid(HoneycombCLISuite.CELLADM_COMMAND, getInvalidCellid()),
            formatCommandWithCellid(HoneycombCLISuite.CELLADM_COMMAND, getCellid()),
        };
        
        boolean isTestPass = false;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            String [] errorMessage = HoneycombCLISuite.CELLADM_HELP;
            
            boolean isTestPassTmp = verifyCommandStdout(
                    false, lsInvalidArgs[i], errorMessage);
            
            if ((i == 0) || (isTestPass))
                isTestPass = isTestPassTmp;
        }
        
        if(isTestPass )
            self.testPassed();
        else
            self.testFailed();
    }
    
    private String getExpansionStatus() throws HoneycombTestException {
        String currentStatus = "";
        
        try {
            BufferedReader output = runCommandWithoutCellid(HoneycombCLISuite.CELLADM_COMMAND);
            String [] lsOutput = excudeCustomerWarning(output);
            output.close();
            String line = lsOutput[0];
                
            if (line != null) {
                Log.INFO(HoneycombCLISuite.CELLADM_COMMAND + " output: " + line);
                
                // verify the output string
                if (line.indexOf(CELLADM_EXPANSION_STATUS) == -1) 
                    throw new HoneycombTestException("wrong output format: " + line);
                    
                else {
                    String [] lsOutputTmp = tokenizeIt(line, ":");
                    currentStatus = lsOutputTmp[lsOutputTmp.length-1].trim();
                    Log.DEBUG("Status: " + currentStatus);
                }
            }
            else 
                Log.ERROR("No output from comamnd " + 
                        HoneycombCLISuite.CELLADM_COMMAND);           
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }  
        
        return currentStatus;
    }
     
}
