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
public class CLITestMdconfig extends HoneycombCLISuite {
    
    private static String MDCONFIG_COMMIT_CHAR = null;
    private static String MDCONFIG_COMMIT_NAME = null;
    private static String MDCONFIG_DUMP_CHAR = null;
    private static String MDCONFIG_DUMP_NAME = null;
    private static String MDCONFIG_RETRY_CHAR = null;
    private static String MDCONFIG_RETRY_NAME = null;
    private static String MDCONFIG_TEMPLATE_CHAR = null;
    private static String MDCONFIG_TEMPLATE_NAME = null;
    
            
    /** Creates a new instance of CLITestMdconfig */
    public CLITestMdconfig() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: mdconfig\n");
        sb.append("\t o negative test - invalid option\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        MDCONFIG_COMMIT_CHAR = getAdminResourceProperties().
                getProperty("cli.mdconfig.commit_char");
        MDCONFIG_COMMIT_NAME = getAdminResourceProperties().
                getProperty("cli.mdconfig.commit_name");
        MDCONFIG_DUMP_CHAR = getAdminResourceProperties().
                getProperty("cli.mdconfig.dump_char");
        MDCONFIG_DUMP_NAME = getAdminResourceProperties().
                getProperty("cli.mdconfig.dump_name");
        MDCONFIG_RETRY_CHAR = getAdminResourceProperties().
                getProperty("cli.mdconfig.retry_char");
        MDCONFIG_RETRY_NAME = getAdminResourceProperties().
                getProperty("cli.mdconfig.retry_name");
        MDCONFIG_TEMPLATE_CHAR = getAdminResourceProperties().
                getProperty("cli.mdconfig.template_char");
        MDCONFIG_TEMPLATE_NAME = getAdminResourceProperties().
                getProperty("cli.mdconfig.template_name");        
    }
    
    public void testMdconfigNegativeTest() {
        
        TestCase self = createTestCase("CLI_mdconfig_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        // will test with each command with invalid option 
        String [] lsInvalidArgs = {  
            HoneycombCLISuite.MDCONFIG_COMMAND,
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_COMMIT_NAME,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_COMMIT_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_DUMP_CHAR +
                    " -" + MDCONFIG_TEMPLATE_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_DUMP_NAME +
                    " -" + MDCONFIG_TEMPLATE_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_TEMPLATE_CHAR +
                    " -" + MDCONFIG_DUMP_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_TEMPLATE_NAME +
                    " -" + MDCONFIG_DUMP_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_RETRY_CHAR +
                    " -" + MDCONFIG_COMMIT_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_RETRY_NAME +
                    " -" + MDCONFIG_COMMIT_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_COMMIT_CHAR +
                    " -" + MDCONFIG_RETRY_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_COMMIT_NAME +
                    " -" + MDCONFIG_RETRY_CHAR            
        };
        
        // will test with valid cellid
        String [] lsMulticellArgs = {
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_DUMP_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_DUMP_NAME,
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_TEMPLATE_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_TEMPLATE_NAME,
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_RETRY_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_RETRY_NAME,
            HoneycombCLISuite.MDCONFIG_COMMAND + " -" + MDCONFIG_COMMIT_CHAR,
            HoneycombCLISuite.MDCONFIG_COMMAND + " --" + MDCONFIG_COMMIT_NAME
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        // will test with invalid option 
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            // execute command without cellid
            isTestPass = verifyCommandStdout(
                false, lsInvalidArgs[i], HoneycombCLISuite.MDCONFIG_HELP);
        
            if (!isTestPass)
                noOfFailure++;
        }
        
        // execute command with cellid
        for (int i=0; i<lsMulticellArgs.length; i++) {
            setCellid();
            isTestPass = verifyCommandStdout(
                true, lsMulticellArgs[i], HoneycombCLISuite.MDCONFIG_HELP);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
}
