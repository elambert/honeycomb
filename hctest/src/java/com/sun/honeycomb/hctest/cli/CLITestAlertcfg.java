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
import java.io.BufferedReader;
import java.io.IOException;

/**
 *
 * @author jk142663
 */
public class CLITestAlertcfg extends HoneycombCLISuite {
    
    protected static final String ALERTCFG_ADD_STRING = "add";
    protected static final String ALERTCFG_DEL_STRING = "del";
    protected static final String ALERTCFG_TO_STRING = "to";
    protected static final String ALERTCFG_CC_STRING = "cc";
    
    private static final String ALERTCFG_COMMAND_TO_OUTPUT = "To:";
    private static final String ALERTCFG_COMMAND_CC_OUTPUT = "Cc:";
    
    private static String ALERTCFG_ADD_COMMAND = null;
    private static String ALERTCFG_ADD_TO_COMMAND = null;
    private static String ALERTCFG_ADD_CC_COMMAND = null;
    private static String ALERTCFG_DELETE_COMMAND = null;
    private static String ALERTCFG_DELETE_TO_COMMAND = null;
    private static String ALERTCFG_DELETE_CC_COMMAND = null;
   
    private String email;
    
    /** Creates a new instance of CLIAlertcfg */
    public CLITestAlertcfg() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: alertcfg\n");
        sb.append("\t o positive test - add/delete alert of type to/cc\n");
        sb.append("\t o negative test - add/delete non-existing alert, invalid option\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        ALERTCFG_ADD_COMMAND = 
            HoneycombCLISuite.ALERTCFG_COMMAND + " " + ALERTCFG_ADD_STRING;
        ALERTCFG_ADD_TO_COMMAND = 
            ALERTCFG_ADD_COMMAND + " " + ALERTCFG_TO_STRING + " ";
        ALERTCFG_ADD_CC_COMMAND = 
            ALERTCFG_ADD_COMMAND + " " + ALERTCFG_CC_STRING + " ";
        ALERTCFG_DELETE_COMMAND = 
            HoneycombCLISuite.ALERTCFG_COMMAND + " " + ALERTCFG_DEL_STRING;
        ALERTCFG_DELETE_TO_COMMAND = 
            ALERTCFG_DELETE_COMMAND + " " + ALERTCFG_TO_STRING + " ";
        ALERTCFG_DELETE_CC_COMMAND = 
            ALERTCFG_DELETE_COMMAND + " " + ALERTCFG_CC_STRING + " ";
        
        email = getAdminCliProperties().getProperty("cli.email");
        if ((email == null) || (email.equals("")))
            throw new HoneycombTestException("Unable to get email " +
                    "address from properties file");
    }
    
    /**
     * @see com.sun.honeycomb.hctest.cli.HoneycombCLISuite#isInErrorExclusionList(String)
     */
    protected boolean isInErrorExclusionList(String errorString) {

        if (errorString == null)
            return false;
        errorString = errorString.toLowerCase();
	if (errorString.indexOf("unable to delete") != -1)
	    return true;
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testAlertcfgSyntax() {
        
        TestCase self = createTestCase("CLI_alertcfg_syntax");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        if (self.excludeCase()) return;
        
        try {
            BufferedReader output = runCommandWithoutCellid(
                    HoneycombCLISuite.ALERTCFG_COMMAND);
            String line = null, tmpEmail = null;
            int lineNo = 0;
            ArrayList lsEmail = new ArrayList();
            boolean isToEmail = false, isCcEmail = false;
            
            while ((line = output.readLine()) != null) {
                
                lineNo ++;
                Log.INFO(HoneycombCLISuite.ALERTCFG_COMMAND + " output: " + line);
                
                if (lineNo == 1) {
                    isToEmail = true;
                    isCcEmail = false;
                    
                    Pattern regexp = Pattern.compile("^To:\\s+.*$");
                    Matcher matcher = regexp.matcher(line);
                    
                    // verify the output pattern
                    if (!matcher.matches()) {
                        throw new RuntimeException("missing <" + ALERTCFG_COMMAND_TO_OUTPUT + 
                                ">, wrong output format: " + line);                                                   
                    } 
                }
                else if (line.indexOf(ALERTCFG_COMMAND_CC_OUTPUT) != -1) {
                    isToEmail = false;
                    isCcEmail = true;
                }    
                else {
                    Log.DEBUG("No To/Cc at the begining of the line");
                } 
                
                // get the actual email address for the current line
                if (line.indexOf(":") != -1) {
                    String [] lsLine = tokenizeIt(line, ":");
                    if (lsLine.length >= 2) {
                        tmpEmail = (lsLine[1]);
                        lsEmail.add(tmpEmail);
                    }
                }
                else {                  
                    tmpEmail = line;
                    lsEmail.add(tmpEmail);
                }
            }
            output.close();
            
            if (lineNo == 0) {
                self.testFailed("No output from " + HoneycombCLISuite.ALERTCFG_COMMAND);
                return;
            }
        
            if(!isCcEmail) {
                self.testFailed("missing <" + ALERTCFG_COMMAND_CC_OUTPUT + 
                                ">, wrong output format");
                return;
            }
            
            for (int i=0; i<lsEmail.size(); i++) {
                Log.DEBUG("email:" + lsEmail.get(i));
            }
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
            return;
        }  
        
        self.testPassed();
    }
    
    public void testAlertcfgNegativeTest() {
        
        TestCase self = createTestCase("CLI_alertcfg_Negative_Test");
        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        if (self.excludeCase()) return;
        
        String nonExistingAlert = " nonExistingAlert";
        String [] lsInvalidArgs = {HoneycombCLISuite.ALERTCFG_COMMAND + " " + 
            HoneycombCLISuite.ALERTCFG_COMMAND,
            HoneycombCLISuite.ALERTCFG_COMMAND + " --add " + 
                ALERTCFG_TO_STRING + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " --del " + 
                ALERTCFG_TO_STRING + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " Add " + 
                ALERTCFG_TO_STRING + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " Del " + 
                ALERTCFG_TO_STRING + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " Add " + 
                ALERTCFG_CC_STRING + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " Del " + 
                ALERTCFG_CC_STRING + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " " + 
                ALERTCFG_ADD_STRING + " To " + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " " + 
                ALERTCFG_ADD_STRING + " Cc " + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " " + 
                ALERTCFG_DEL_STRING + " To " + nonExistingAlert,
            HoneycombCLISuite.ALERTCFG_COMMAND + " " + 
                ALERTCFG_DEL_STRING + " Cc " + nonExistingAlert,
            ALERTCFG_ADD_COMMAND, ALERTCFG_ADD_COMMAND + " tocc",
            ALERTCFG_ADD_TO_COMMAND, ALERTCFG_ADD_CC_COMMAND,
            ALERTCFG_DELETE_COMMAND, ALERTCFG_DELETE_COMMAND + " tocc",
            ALERTCFG_DELETE_TO_COMMAND, ALERTCFG_DELETE_CC_COMMAND};
        
        boolean isTestPass = true;
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            String [] expectedMessage = CLITestHelp.ALERTCFG_HELP;
            boolean isTestPassTmp = verifyCommandStdout(false, 
                    lsInvalidArgs[i], expectedMessage);
            
            if (isTestPassTmp == false)
                isTestPass = false;
        }
        
        String msg = "The specified email address is not a member of the list.";
	if (verifyCommandStdout(false, 
            ALERTCFG_DELETE_TO_COMMAND + nonExistingAlert, 
	    new String[] {msg}) == false)
		isTestPass = false;
        if (verifyCommandStdout(false,     
            ALERTCFG_DELETE_CC_COMMAND + nonExistingAlert,
	    new String[] {msg}) == false)
		isTestPass = false;
	    
        if(isTestPass )
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testAlertcfgAddDelete() {
        
        String [] lsTestCaseName = {
            "CLI_alertcfg_add_to_NonExistingEmail",
            "CLI_alertcfg_add_to_ExistingToEmail",
            "CLI_alertcfg_add_cc_ExistingToEmail",
            "CLI_alertcfg_del_to_ExistingToEmail",
            "CLI_alertcfg_add_cc_NonExistingEmail",
            "CLI_alertcfg_add_cc_ExistingCcEmail",
            "CLI_alertcfg_add_to_ExistingCcEmail",
            "CLI_alertcfg_del_cc_ExistingCcEmail",
	    "CLI_alertcfg_del_to_Cleanup"
        };
        
        String [] lsEmailType = {
            ALERTCFG_TO_STRING,
            ALERTCFG_TO_STRING,
            ALERTCFG_CC_STRING,
            ALERTCFG_TO_STRING,
            ALERTCFG_CC_STRING,
            ALERTCFG_CC_STRING,
            ALERTCFG_TO_STRING,
            ALERTCFG_CC_STRING,
	    ALERTCFG_TO_STRING
        };
        
        String [] lsCommand = {
            ALERTCFG_ADD_TO_COMMAND, 
            ALERTCFG_ADD_TO_COMMAND,
            ALERTCFG_ADD_CC_COMMAND,
            ALERTCFG_DELETE_TO_COMMAND,
            ALERTCFG_ADD_CC_COMMAND,
            ALERTCFG_ADD_CC_COMMAND,
            ALERTCFG_ADD_TO_COMMAND,
            ALERTCFG_DELETE_CC_COMMAND,
	    ALERTCFG_DELETE_TO_COMMAND
        };
        
        String [] lsTestType = {
            "add_NonExistingEmail",
            "add_ExistingEmail_sameType",
            "add_ExistingEmail_notSameType",
            "delete_Email",
            "add_NonExistingEmail",
            "add_ExistingEmail_sameType",
            "add_ExistingEmail_notSameType",
            "delete_Email",
	    "delete_Email"
        };
        
        String [] lsAuditMsg = {
            "info.adm.addToEmail",
            "",
            "info.adm.addCCEmail",
            "info.adm.delToEmail",
            "info.adm.addCCEmail",
            "",
            "info.adm.addToEmail",
            "info.adm.delCCEmail",
            "info.adm.delToEmail",            
        };
        
        for (int i=0; i<lsTestCaseName.length; i++) {
        
            TestCase self = createTestCase(lsTestCaseName[i], email);

            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!

            if (self.excludeCase()) return;
            
            boolean isTestPass = false;
            
            setCurrentInternalLogFileNum();
        
            try {
                String testType = lsTestType[i];
                
                if (testType.equals("add_NonExistingEmail"))
                    isTestPass = addNonExistingEmail(lsCommand[i], email, lsEmailType[i]);
                else if(testType.equals("add_ExistingEmail_sameType"))
                    isTestPass = addExistingEmailSameType(lsCommand[i], email, lsEmailType[i]);
                else if(testType.equals("add_ExistingEmail_notSameType"))
                    isTestPass = addExistingEmailNotSameType(lsCommand[i], email, lsEmailType[i]);
                else if(testType.equals("delete_Email"))
                    isTestPass = deleteEmail(lsCommand[i], email, lsEmailType[i]);
                else
                    // do nothing
                    ;
                
                // verify audit log
                if (!lsAuditMsg[i].equals("")) 
                    verifyAuditAlertcfg(lsCommand[i], lsAuditMsg[i]);    
                
            } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
            }
            
            if (isTestPass) 
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testAlertcfgMultipleAdd() {
        
        String [] lsTestCaseName = {
            "CLI_alertcfg_add_multiple_to_Email",
            "CLI_alertcfg_add_multiple_cc_Email"};
        
        String [] lsEmailType = {
            ALERTCFG_TO_STRING,
            ALERTCFG_CC_STRING};
        
        String [] lsAddCommand = {
            ALERTCFG_ADD_TO_COMMAND, 
            ALERTCFG_ADD_CC_COMMAND};
        
        String [] lsDeleteCommand = {
            ALERTCFG_DELETE_TO_COMMAND,
            ALERTCFG_DELETE_CC_COMMAND};
        
        // the following email addresses created using netadmin
        String [] lsEmail = {"hc_test1@sun.com", "hc_test2@sun.com"};
        
        for (int i=0; i<lsTestCaseName.length; i++) {
        
            TestCase self = createTestCase(lsTestCaseName[i], email);

            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!

            if (self.excludeCase()) return;
            
            boolean isTestPass = false;
            
            try {
                boolean isAdd1 = addEmail(lsAddCommand[i], 
                        lsEmail[0], lsEmailType[i]);
                boolean isAdd2 = addEmail(lsAddCommand[i], 
                        lsEmail[1], lsEmailType[i]);
                
                boolean isDelete1 = deleteEmail(lsDeleteCommand[i], 
                        lsEmail[0], lsEmailType[i]);
                boolean isDelete2 = deleteEmail(lsDeleteCommand[i], 
                        lsEmail[1], lsEmailType[i]);
                
                if ((isAdd1) && (isAdd2) && (isDelete1) && (isDelete2))
                    isTestPass = true;
                
            } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
            }
            
            if (isTestPass) 
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    private boolean isEmailPresent(String emailAddress) {
        String alertcfgOutput = null;
                
        try {
            alertcfgOutput = getCommandStdout(
                false, HoneycombCLISuite.ALERTCFG_COMMAND, emailAddress);
        } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
        
        if (alertcfgOutput != null) 
            return true;
        else
            return false;
    }
    
    // type could be To/Cc
    private boolean isEmailPresent(String emailAddress, String emailType) {
        try {        
             BufferedReader output = runCommandWithoutCellid(
                     HoneycombCLISuite.ALERTCFG_COMMAND);
             
             String lines = HCUtil.readLines(output);
             output.close();
             String [] lsLine = tokenizeIt(lines, "\n");
             
             boolean isCCEmail = false;
                                        
             for (int i=0; i<lsLine.length; i++) {
                String line = lsLine[i].trim();
                Log.INFO(HoneycombCLISuite.ALERTCFG_COMMAND + " output: " + line);
                
                if (emailType.equalsIgnoreCase(ALERTCFG_TO_STRING)) {
                    if (line.indexOf(ALERTCFG_COMMAND_CC_OUTPUT) != -1) 
                        return false;
                    
                    if (line.indexOf(emailAddress) != -1)
                        return true;                    
                }
                else if (emailType.equalsIgnoreCase(ALERTCFG_CC_STRING)) {
                    if (!isCCEmail) {
                        if (line.indexOf(ALERTCFG_COMMAND_CC_OUTPUT) == -1) 
                            continue;
                        else
                            isCCEmail = true;
                    }
                            
                    if (line.indexOf(emailAddress) != -1)
                            return true;
                }
                else
                    // do nothing
                    ;                 
             }             
        } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
        
        return false;
    }
    
    // add an email address using command alertcfg add [to|cc]
    private boolean addEmail(String command, String emailAddress, 
            String emailType) {
        Log.INFO("Test Synopsis: Add email address <" + emailAddress + 
                "> of type <" + emailType + ">");
        
        boolean isAddTestPass = false;
        command += " " + emailAddress;
               
        try {
            String line = null;
            BufferedReader output = runCommandWithoutCellid(command);
                
            while ((line = output.readLine()) != null) {
                Log.INFO(line);
            }
            output.close();
            
            boolean isEmailPresent = isEmailPresent(emailAddress, emailType);
            
            if (!isEmailPresent)
                Log.ERROR("Unable to find the email address <" + emailAddress + 
                        "> of type <" + emailType + "> from the output of <" + 
                        HoneycombCLISuite.ALERTCFG_COMMAND + "> command");
            else {
                Log.INFO("Email Address <" + emailAddress + "> of type <" +
                        emailType + "> is added successfully");
                
                // Generate an alert
                generateAlert();
                
                isAddTestPass = true;
            }
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }      
        
        return isAddTestPass;
    }
    
    // add an email address using command alertcfg add [to|cc]
    // make sure the email is not present in to/cc initially
    private boolean addNonExistingEmail(String command, String emailAddress, 
            String emailType) {
        boolean isAddTestPass = false;
        
        try {
            // make sure that the email is deleted before testing add option
            boolean isPresent = isEmailPresent(emailAddress);
            
            // if the email is already present, delete it first
            if(isPresent) {
               // try with to option
                BufferedReader output = runCommandWithoutCellid(
                        ALERTCFG_DELETE_TO_COMMAND + " " + emailAddress);
                String line = null;
                while ((line = output.readLine()) != null) {
                    Log.INFO(line);
                }
                output.close();
                
                isPresent = isEmailPresent(emailAddress);
                    
                // try with cc option
                if (isPresent) {
                    output = runCommandWithoutCellid(
                            ALERTCFG_DELETE_CC_COMMAND + " " + emailAddress);
                     
                    while ((line = output.readLine()) != null) {
                        Log.DEBUG(ALERTCFG_DELETE_TO_COMMAND + " output: " + line);
                    }
                    output.close();
                    
                    isPresent = isEmailPresent(emailAddress);
                    if (isPresent) {
                        Log.WARN("unable to delete email <" + emailAddress +
                                ">, Won't run add email test");
                        return false;                       
                    }                    
                }                
            }
            
            isAddTestPass = addEmail(command, emailAddress, emailType);
            
        } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
        }   
        
        return isAddTestPass;
    }
   
    // add an email address using command alertcfg add [to|cc]
    // make sure the email with specified type is present initially
    private boolean addExistingEmailSameType(String command, String emailAddress, 
            String emailType) {
        boolean isAddTestPass = false;
        
        try {
            boolean isPresent = isEmailPresent(emailAddress, emailType);
            if (isPresent) 
                Log.INFO("Email Address <" + emailAddress + "> of type <" +
                            emailType + "> is already present");
            else {
                Log.ERROR("No email <" + emailAddress + "> of type <" +
                        emailType +"> is present to test <add existing email> option");
                return false;
            } 
            
            isAddTestPass = addEmail(command, emailAddress, emailType);
  
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }      
        
        return isAddTestPass;
    }
        
    // add an email address using command alertcfg add [to|cc]
    // make sure the email with specified type (e.g. to) is not present initially,
    // but present with other type (e.g. cc)
    private boolean addExistingEmailNotSameType(String command, String emailAddress,
            String emailType) {
        boolean isAddTestPass = false;
        
        try {
            // make sure that the email with same type is deleted 
            // before testing add option
            boolean isPresent = isEmailPresent(emailAddress, emailType);
            
            // if the email of same type is already present, delete it first
            if(isPresent) {
                
               String deleteCommand = ALERTCFG_DELETE_TO_COMMAND + " " + emailAddress;
               if (emailType.equalsIgnoreCase(ALERTCFG_CC_STRING))
                   deleteCommand = ALERTCFG_DELETE_CC_COMMAND + " " + emailAddress;
               
                String line = null;
                BufferedReader output = runCommandWithoutCellid(deleteCommand);
                while ((line = output.readLine()) != null) {
                    Log.INFO(line);
                }
                output.close();
                
                isPresent = isEmailPresent(emailAddress, emailType);
                if (isPresent) {
                    Log.WARN("unable to delete email <" + emailAddress +
                            ">, Won't run add email test");
                    return false;                                        
                }                
            }
            
            // make sure that the email with different type is present 
            // before testing add option
            String emailTypeTmp = "";
            if (emailType.equalsIgnoreCase(ALERTCFG_CC_STRING))
                emailTypeTmp = ALERTCFG_TO_STRING;
            else
                emailTypeTmp = ALERTCFG_CC_STRING;
                
            isPresent = isEmailPresent(emailAddress, emailTypeTmp);
            
            if (isPresent) 
                Log.INFO("Email Address <" + emailAddress + "> of type <" +
                            emailTypeTmp + "> is already present");
            else {
                Log.ERROR("No email <" + emailAddress + "> of type <" +
                        emailType +"> is present to test <add existing email> option");
                return false;
            } 
            
            isAddTestPass = addEmail(command, emailAddress, emailType);
            
        } catch (Throwable t) {
                Log.ERROR("IO Error accessing CLI:" + t.toString());
        }   
        
        return isAddTestPass;
    }
    
    // delete an email address using command alertcfg del [to|cc]
    private boolean deleteEmail(String command, String emailAddress, 
            String emailType) {
        Log.INFO("Test Synopsis: Delete email address <" + emailAddress + 
                "> of type <" + emailType + ">");
        
        boolean isDeleteTestPass = false;
        command += " " + emailAddress;
                
        try {
            // make sure that the email is present before testing delete option
            boolean isPresent = isEmailPresent(emailAddress, emailType);
            if(!isPresent) {
                Log.ERROR("Unable to test command <" + command + 
                        "> as no email address <" + emailAddress + "> is present");
                return false;
            }
            
            String line = null;
            BufferedReader output = runCommandWithoutCellid(command);
                
            while ((line = output.readLine()) != null) {
                Log.INFO(line);
            }
            output.close();
            
            boolean isEmailPresent = isEmailPresent(emailAddress, emailType);
            
            if (isEmailPresent)
                Log.ERROR("Email Address <" + emailAddress + 
                        "> of type <" + emailType + 
                        "> is displayed in the output of <" 
                        + HoneycombCLISuite.ALERTCFG_COMMAND + "> command");
            else {
                Log.INFO("Email Address <" + emailAddress + 
                        "> of type <" + emailType + "> is deleted successfully");
                isDeleteTestPass = true;
            }
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }      
        
        return isDeleteTestPass;
    }
  
    // generate an alert using syscfg
    private void generateAlert() {
        try {
            String syscfgCommand = getAdminResourceProperties().
                    getProperty("cli.commandname.ddcfg") + " -F";
            String syscfgParam = "recover_lost_frags_cycle";
            
            Log.INFO("Will generete an alert using " + syscfgCommand);
            
            String syscfgOutput = getCommandStdout(
                false, syscfgCommand, syscfgParam);
            
            if (syscfgOutput == null)
                Log.ERROR("Unable to find parameter <" + syscfgParam +
                        "> from <" + syscfgCommand + "> output");
            else {
                Log.INFO("Current value: " + syscfgOutput);
                String [] syscfgNameValue = tokenizeIt(syscfgOutput, "=");
                int value = new Integer(syscfgNameValue[1]).intValue();
                
                if (value == 0)
                    value += 1;
                else
                    value -= 1;
                
                Log.INFO("Set the <" + syscfgParam + "> to a new value <" +
                        value + ">");
                String line = null;
                BufferedReader output = runCommandWithoutCellid(
                        syscfgCommand + " " + syscfgParam + " " + value);
                
                while ((line = output.readLine()) != null) {
                    Log.DEBUG(syscfgCommand + " Output: " + line);
                }            
                output.close();
            }
            
        } catch (Throwable t) {
            Log.ERROR("Error while generating alert:" + t.toString());
        } 
    }
    
    private void verifyAuditAlertcfg(String command, String msg) {
        ArrayList paramValueList = new ArrayList();
        paramValueList.add(email);
        verifyAuditInternalLog(command, msg, 
                paramValueList, true); 
    }
}
