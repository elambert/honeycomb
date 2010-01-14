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

/**
 *
 * @author jk142663
 */
public class CLITestUpgrade extends HoneycombCLISuite {
    
    private static final String iso_image = "st5800_1.1-91.iso";
    private static final String upgrade_jar = "st5800-upgrade.jar";
    private static final String SRC = 
            "http://10.7.228.10/~hcbuild/repository/releases/1.1/1.1-91/AUTOBUILT/pkgdir/";
    private static final String SRC_URL = SRC + iso_image;
    private static final String INVALID_URL = SRC + "test/";
    private static final String INVALID_SRC_URL = INVALID_URL + iso_image;
    private static final String INVALID_SRC_JAR = INVALID_URL + upgrade_jar;
    
    private static final String UPGRADE_DOWNLOAD_STRING = "download";
    private static final String UPGRADE_DOWNLOADED_STRING = "downloaded";
    
    private static String UPGRADE_DOWNLOAD_COMMAND = null;
    private static String UPGRADE_DOWNLOADED_COMMAND = null;
    
    /** Creates a new instance of CLITestUpgrade */
    public CLITestUpgrade() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: upgrade\n");
        sb.append("\t o negative test - invalid option/src url\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        UPGRADE_DOWNLOAD_COMMAND = HoneycombCLISuite.UPGRADE_COMMAND +
                " " + UPGRADE_DOWNLOAD_STRING;
        UPGRADE_DOWNLOADED_COMMAND = HoneycombCLISuite.UPGRADE_COMMAND +
                " " + UPGRADE_DOWNLOADED_STRING;
    }
  
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if ((errorString.indexOf(INVALID_CELLID_MESSAGE) != -1) ||
	    (errorString.indexOf("url required") != -1) ||
	    (errorString.indexOf("insufficient arguments") != -1) ||
	    (errorString.indexOf("unknown argument") != -1) ||
	    (errorString.indexOf("incorrect arguments") != -1))
	    return true;
	
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testUpgradeNegativeTest() {
        
        TestCase self = createTestCase("CLI_upgrade_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        String [] lsInvalidArgs = {
            HoneycombCLISuite.UPGRADE_COMMAND,
            HoneycombCLISuite.UPGRADE_COMMAND + " -u",
            UPGRADE_DOWNLOAD_COMMAND,
            HoneycombCLISuite.UPGRADE_COMMAND + " " + 
                    UPGRADE_DOWNLOADED_COMMAND.toUpperCase() +  " " + SRC_URL,
            HoneycombCLISuite.UPGRADE_COMMAND + " " + 
                    UPGRADE_DOWNLOAD_COMMAND.toUpperCase() +  " " + SRC_URL,            
            UPGRADE_DOWNLOADED_COMMAND + " " + INVALID_SRC_URL,
            UPGRADE_DOWNLOAD_COMMAND + " " + INVALID_SRC_URL,
        };
        
        // will test without cellid and with invalid cellid for multicell
        String [] lsInvalidMulticellArgs = {
            UPGRADE_DOWNLOAD_COMMAND + " " + SRC_URL,
            UPGRADE_DOWNLOADED_COMMAND,
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            String [] errorMsg = HoneycombCLISuite.UPGRADE_HELP;
        
            if (i==(lsInvalidArgs.length-1))
                errorMsg = new String [] {
                    "Cannot open stream to url '" + INVALID_SRC_JAR + 
                        "': " + INVALID_SRC_JAR,
                    "Exiting upgrade due to errors getting new upgrade code"};
            
            // execute invalid command
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], errorMsg);
            
            if (!isTestPass)
                noOfFailure++;
            
            // for multi-cell only
            if (isMultiCell()) {
                
                // execute invalid command with cellid
                setCellid();
                isTestPass = verifyCommandStdout(
                        true, lsInvalidArgs[i], errorMsg);
                
                if (!isTestPass)
                    noOfFailure++;
            }
        }
        
        // for multi-cell only
        if (isMultiCell()) {
             
            // execute command without cellid
            isTestPass = verifyCommandStdout(
                    false, lsInvalidMulticellArgs[1], 
                    HoneycombCLISuite.UPGRADE_HELP);
            
             if (!isTestPass)
                noOfFailure++;
        }  
        
        // execute command with invalid cellid
        int invalidCellId = getInvalidCellid();
    
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
            String formattedCommand = formatCommandWithCellid(
                    lsInvalidMulticellArgs[i], invalidCellId);
        
            isTestPass = verifyCommandStdout(false, formattedCommand, 
                    HoneycombCLISuite.UPGRADE_HELP);
        
            if (!isTestPass)
                noOfFailure++;
        }        
        
        if(noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
}
