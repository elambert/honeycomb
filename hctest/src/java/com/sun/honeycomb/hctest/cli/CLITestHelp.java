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
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 * @author jk142663
 */
public class CLITestHelp extends HoneycombCLISuite {
    
    /** Creates a new instance of CLITestHelp */
    public CLITestHelp() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        
        //positive tests
        sb.append("\tCommand: help\n");
        sb.append("\t o positive test - verifiy help of each command\n");
        sb.append("\t\t - verifiy all public commands are listed in help\n");
        sb.append("\t o negative test - invalid commandname/option\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        
        super.setUp();
    }
 
    public void testHelp() {
        TestCase self = createTestCase("CLI_help");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
    
        boolean isTestPass = false;
        
        ArrayList allExpCommand = getAllCommandList();
        ArrayList allActCommand = new ArrayList();
        
        String helpAssistStr = getAdminResourceProperties().
                getProperty("cli.help.assist");
        boolean foundHelpAssistStr = false;
        
        try {
            BufferedReader output = runCommandWithoutCellid(
                    HoneycombCLISuite.HELP_COMMAND);
            String line = null;
            
            while ((line = output.readLine()) != null) {
                isTestPass = true;
                
                if (line.trim().equals(helpAssistStr)) {
                    foundHelpAssistStr = true;
                    continue;
                }
                
                String [] lsCommand = tokenizeIt(line, " ");
                for (int j=0; j<lsCommand.length; j++)
                    allActCommand.add(lsCommand[j].trim());
            }
            output.close();
            
            for (int i=0; i<allActCommand.size(); i++) {
                Log.DEBUG("command: " + allActCommand.get(i));
            }
            
            if(!foundHelpAssistStr) {
                Log.ERROR("Missing help Assist <" + helpAssistStr + "> from help stdout");
                isTestPass = false;
            }

            // verify all commands are displayed in help stdout
            boolean isCommandMissing = false;
            String missingCommand = "";
            for (int i=0; i<allExpCommand.size(); i++) {
                String commandName = (String) allExpCommand.get(i);
                if (!allActCommand.contains(commandName)){
                    isCommandMissing = true;
                    missingCommand += commandName + " ";
                }
            }
            
            if (isCommandMissing) {
                Log.ERROR("The command(s) <" + missingCommand + 
                        "> is missing from help stdout");
                isTestPass = false;
            }
            
            // verify all commands that are displayed in help stdout are public
            boolean isPrivateCommand = false;
            String privateCommand = "";
            for (int i=0; i<allActCommand.size(); i++) {
                String commandName = (String) allActCommand.get(i);
                if (!allExpCommand.contains(commandName)){
                    isPrivateCommand = true;
                    privateCommand += commandName + " ";
                }
            }
            
            if (isPrivateCommand) {
                Log.ERROR("The private command(s) <" + privateCommand + 
                        "> should not display in help stdout");
                isTestPass = false;
            }            
        } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
            
        if (!isTestPass)
            self.testFailed();
        else
            self.testPassed();
        
    }    
    
    public void testCommandHelp() {
        
        String [] lsCommand = {HoneycombCLISuite.ALERTCFG_COMMAND, 
            HoneycombCLISuite.CELLADM_COMMAND, HoneycombCLISuite.CELLCFG_COMMAND,
            HoneycombCLISuite.DATE_COMMAND, HoneycombCLISuite.DF_COMMAND, 
            HoneycombCLISuite.HELP_COMMAND, HoneycombCLISuite.HIVEADM_COMMAND, 
            HoneycombCLISuite.HWCFG_COMMAND, HoneycombCLISuite.HIVECFG_COMMAND,
            HoneycombCLISuite.HWSTAT_COMMAND, HoneycombCLISuite.LOCALE_COMMAND, 
            HoneycombCLISuite.LOGOUT_COMMAND, HoneycombCLISuite.LOGDUMP_COMMAND, 
            HoneycombCLISuite.MDCONFIG_COMMAND, HoneycombCLISuite.PERFSTATS_COMMAND,
            HoneycombCLISuite.PASSWD_COMMAND, HoneycombCLISuite.REBOOT_COMMAND, 
            HoneycombCLISuite.SENSORS_COMMAND, HoneycombCLISuite.SETUPCELL_COMMAND,
            HoneycombCLISuite.SHUTDOWN_COMMAND, HoneycombCLISuite.SYSSTAT_COMMAND, 
            HoneycombCLISuite.UPGRADE_COMMAND, HoneycombCLISuite.VERSION_COMMAND, 
            HoneycombCLISuite.WIPE_COMMAND, HoneycombCLISuite.PROTOCOL_PASSWD_COMMAND,
        };
       
        String [][] lsHelp = {HoneycombCLISuite.ALERTCFG_HELP, 
            HoneycombCLISuite.CELLADM_HELP, HoneycombCLISuite.CELLCFG_HELP, 
            HoneycombCLISuite.DATE_HELP, HoneycombCLISuite.DF_HELP, 
            HoneycombCLISuite.HELP_HELP, HoneycombCLISuite.HIVEADM_HELP, 
            HoneycombCLISuite.HWCFG_HELP, HoneycombCLISuite.HIVECFG_HELP, 
            HoneycombCLISuite.HWSTAT_HELP, HoneycombCLISuite.LOCALE_HELP, 
            HoneycombCLISuite.LOGOUT_HELP, HoneycombCLISuite.LOGDUMP_HELP, 
            HoneycombCLISuite.MDCONFIG_HELP, HoneycombCLISuite.PERFSTATS_HELP,
            HoneycombCLISuite.PASSWD_HELP, HoneycombCLISuite.REBOOT_HELP, 
            HoneycombCLISuite.SENSORS_HELP, HoneycombCLISuite.SETUPCELL_HELP,
            HoneycombCLISuite.SHUTDOWN_HELP, HoneycombCLISuite.SYSSTAT_HELP, 
            HoneycombCLISuite.UPGRADE_HELP, HoneycombCLISuite.VERSION_HELP, 
            HoneycombCLISuite.WIPE_HELP, HoneycombCLISuite.PROTOCOL_PASSWD_HELP,
        };
        
        for (int i=0; i<lsCommand.length; i++) {
            
            try {
                // remove -F option from hidden command name
                if (lsCommand[i].indexOf(HIDDEN_PROMPT_FORCE_OPTION) != -1) {
                    String [] tmp = tokenizeIt(lsCommand[i], HIDDEN_PROMPT_FORCE_OPTION);
                    lsCommand[i] = tmp[0];
                }
            } catch (Exception e) {
                Log.WARN("Unable to tokenize string: " + lsCommand[i]);
            }
            
            TestCase self = createTestCase("CLI_" + lsCommand[i] + "_help");

            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!
            
            if (self.excludeCase()) return;

            boolean isTestPass = false;
            
            String command1 = HELP_COMMAND + " " + lsCommand[i];
            String command2 = lsCommand[i] + " --" + HELP_COMMAND;
            
            try {
                if (lsHelp[i] == null) {
                    Log.ERROR("No help is defined in Admin Resource " +
                            "properties file for commmand " + lsCommand[i]);
                    isTestPass = false;
                }
                else {
                    boolean isTestPass1 = verifyCommandStdout(false, command1, lsHelp[i]);
                    boolean isTestPass2 = verifyCommandStdout(false, command2, lsHelp[i]);
                
                    if ((isTestPass1) && (isTestPass2))
                        isTestPass = true;
                }
            } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
            }
            
            if (!isTestPass)
                self.testFailed();
            else
                self.testPassed();
        }
    }    
    
    public void testHelpNegativeTest() {
        TestCase self = createTestCase("CLI_help_Negative_Test");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
            
        if (self.excludeCase()) return;

        boolean isTestPass = true;
        
        String helpCommand = HoneycombCLISuite.HELP_COMMAND;
        String validCommand = HoneycombCLISuite.DF_COMMAND;
        String invalidCommand = HoneycombCLISuite.DF_COMMAND + 
                HoneycombCLISuite.DF_COMMAND;
        
        //<invalid command>, help <invalid_command>, <invalid_command> --help, 
        // help <valid_command> <valid_command>, <valid_command> <valid_command> --help
        String [] lsInvalidCommand = {invalidCommand, 
                helpCommand + " -h",
                helpCommand + " " + invalidCommand,
                invalidCommand + " --" + helpCommand,
                helpCommand + " " + validCommand + " " + validCommand};
        
        String [] helpStdout = null;
        try {
            helpStdout = getCommandStdoutWithoutCellid(HoneycombCLISuite.HELP_COMMAND);
        } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
        
        String [][] lsInvalidCommandStdout = {
                helpStdout, 
                HoneycombCLISuite.HELP_HELP, 
                helpStdout,
                helpStdout,
                HoneycombCLISuite.HELP_HELP
        };
        
        for (int i=0; i<lsInvalidCommand.length; i++) {
            Log.INFO("Invalid command: <" + lsInvalidCommand[i] + ">");
            
            try {
                boolean isTestPassTmp = verifyCommandStdout(
                        false, lsInvalidCommand[i], lsInvalidCommandStdout[i]);
                
                if (!isTestPassTmp)
                    isTestPass = false;
                
            } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
            }            
        }
        
        if (!isTestPass)
            self.testFailed();
        else
            self.testPassed();
        
    }    
    
}
