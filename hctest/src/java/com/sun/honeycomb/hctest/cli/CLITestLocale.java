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

//remove
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 *
 * @author jk142663
 */
public class CLITestLocale extends HoneycombCLISuite {
    
    private static String LOCALE_L_COMMAND = null;
    private static String LOCALE_LIST_COMMAND = null;
    private static String LOCALE_S_COMMAND = null;
    private static String LOCALE_SET_COMMAND = null;
    
    // will modify when the bug has been fixed
    private static final String [] LIST_OF_LOCALE = {"en", "fr", "gr", "ja", "sc"};
    private static final String EN_LOCALE = "en";
    private static final String INVALID_LOCALE = "uk";
    private static final String LOCALE_OUTPUT = "Language is currently set to:";
    
    /** Creates a new instance of CLITestLocale */
    public CLITestLocale() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: locale\n");
        sb.append("\t o positive test - change locale, verify all listed locale\n");
        sb.append("\t o negative test - invalid option/locale\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        LOCALE_L_COMMAND = HoneycombCLISuite.LOCALE_COMMAND + " -l";
        LOCALE_LIST_COMMAND = HoneycombCLISuite.LOCALE_COMMAND + " --list";
        LOCALE_S_COMMAND = HoneycombCLISuite.LOCALE_COMMAND + " -s ";
        LOCALE_SET_COMMAND = HoneycombCLISuite.LOCALE_COMMAND + " --set ";
    }
    
    public void testLocaleSyntax() {
        
        TestCase self = createTestCase("CLI_locale_syntax");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        try {
            String currentLocale = getCurrentLocale();
        
            boolean isCorrectLocale = verifyLocale(EN_LOCALE);
         
            if (isCorrectLocale) 
                self.testPassed();
            else
                self.testFailed();
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
    }
    
    public void testLocaleList() {
        
        String [] lsTestCase = {"CLI_locale_l", "CLI_locale_list"};
        String [] lsCommand = {LOCALE_L_COMMAND, LOCALE_LIST_COMMAND};
        
        ArrayList expLanguageList = new ArrayList();
        for (int i=0; i<LIST_OF_LOCALE.length; i++)
            expLanguageList.add(LIST_OF_LOCALE[i]);
            
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i]);

            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!

            if (self.excludeCase()) return;

            boolean isTestPass = false;        
            boolean foundCurrentLanguageStr = false;
            
            ArrayList actLanguageList = new ArrayList();
        
            try {
                BufferedReader output = runCommandWithoutCellid(lsCommand[i]);
                String [] lsLocaleOutput = excudeCustomerWarning(output);
                output.close();
            
                String line = null;
               
                for (int lineNo=0; lineNo<lsLocaleOutput.length; lineNo++) {
                    line = lsLocaleOutput[lineNo];               
                    isTestPass = true;
                    
                    if (line.trim().indexOf(LOCALE_OUTPUT) != -1) {
                        foundCurrentLanguageStr = true;
                        continue;
                    }
                
                    String [] lsLanguage = tokenizeIt(line, ":");
                    lsLanguage = tokenizeIt(lsLanguage[1], ",");
                    
                    for (int j=0; j<lsLanguage.length; j++) 
                        actLanguageList.add(lsLanguage[j].trim());
                }
                
                for (int j=0; j<actLanguageList.size(); j++) {
                    Log.DEBUG("language: " + actLanguageList.get(i));
                }
            
                if(!foundCurrentLanguageStr) {
                    Log.ERROR("Missing line <" + LOCALE_OUTPUT + 
                            "> from command <" + lsCommand[i] + "> stdout");
                    isTestPass = false;
                }

                // verify all languages are displayed in the locale list 
                boolean isLanguageMissing = false;
                String missingLanguage = "";
                for (int j=0; j<expLanguageList.size(); j++) {
                    String language = (String) expLanguageList.get(j);
                    if (!actLanguageList.contains(language)){
                        isLanguageMissing = true;
                        missingLanguage += language + " ";
                    }
                }
            
                if (isLanguageMissing) {
                    Log.ERROR("The supported language(s) <" + missingLanguage + 
                        "> is missing from <" + lsCommand[i] + "> stdout");
                    isTestPass = false;
                }
            
                // verify all languages are displayed in the locale list are unique
                boolean isNotLanguageUnique = false;
                String notUniqueLanguage = "";
                for (int j=0; j<expLanguageList.size(); j++) {
                    String language = (String) expLanguageList.get(j);
                    int firstIndex = actLanguageList.indexOf(language);
                    int lastIndex = actLanguageList.lastIndexOf(language);
                    
                    if (firstIndex != lastIndex){
                        isNotLanguageUnique = true;
                        notUniqueLanguage += language + " ";
                    }
                }
            
                if (isNotLanguageUnique) {
                    Log.ERROR("The supported language(s) <" + notUniqueLanguage + 
                        "> is displayed multiple times in <" + lsCommand[i] + "> stdout");
                    isTestPass = false;
                }
                
                // verify all languages that are displayed in locale list are supported
                boolean isNonSupportedLanguage = false;
                String nonSupportedLanguage = "";
                for (int j=0; j<actLanguageList.size(); j++) {
                    String language = (String) actLanguageList.get(j);
                    if (!expLanguageList.contains(language)){
                        isNonSupportedLanguage = true;
                        if (nonSupportedLanguage.indexOf(language) == -1)
                            nonSupportedLanguage += language + " ";
                    }
                }
            
                if (isNonSupportedLanguage) {
                    Log.ERROR("The non-suppoetd language(s) <" + nonSupportedLanguage + 
                        "> should not display in <" + lsCommand[i] + "> stdout");
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
    }
    
    public void testLocaleSet() throws HoneycombTestException {
        
        String [] lsTestCase = {"CLI_locale_s", "CLI_locale_set"};
        String [] lsCommand = {LOCALE_S_COMMAND, LOCALE_SET_COMMAND};
        
        String newLocale = getAdminCliProperties().getProperty("cli.locale");
        if ((newLocale == null) || (newLocale.equals("")))
            throw new HoneycombTestException("Unable to get locale " +
                    "from admin_cli properties file");

        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i], newLocale);

            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!
            
            if (self.excludeCase()) return;

            boolean isTestPass = false;
            
            try {
                // set locale to user specified value
                setLocale(lsCommand[i], newLocale);
                boolean isTestPass1 = verifyLocale(newLocale);  
                
                // set locale to "en""
                setLocale(lsCommand[i], EN_LOCALE);
                boolean isTestPass2 = verifyLocale(EN_LOCALE);  
                
                if ((isTestPass1) && (isTestPass2))
                    isTestPass = true;
                
            } catch (Throwable t) {
                Log.ERROR("Error while setting locale:" + t.toString());
            }
            
            if (isTestPass )
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testLocaleNegativeTest() {
        
        TestCase self = createTestCase("CLI_locale_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;
        
        String [] lsInvalidArgs = {HoneycombCLISuite.LOCALE_COMMAND + " " + 
            HoneycombCLISuite.LOCALE_COMMAND,
            HoneycombCLISuite.LOCALE_COMMAND + " -S " + EN_LOCALE,
            HoneycombCLISuite.LOCALE_COMMAND + " --Set " + EN_LOCALE,
            HoneycombCLISuite.LOCALE_COMMAND + " --SET " + EN_LOCALE,
            LOCALE_S_COMMAND, 
            LOCALE_SET_COMMAND,
            LOCALE_S_COMMAND + EN_LOCALE + " " + EN_LOCALE,
            LOCALE_SET_COMMAND + EN_LOCALE + " " + EN_LOCALE,            
            LOCALE_L_COMMAND + EN_LOCALE,
            LOCALE_LIST_COMMAND + EN_LOCALE,            
            HoneycombCLISuite.LOCALE_COMMAND + " -l " + EN_LOCALE,
            HoneycombCLISuite.LOCALE_COMMAND + " --list " + EN_LOCALE,
            HoneycombCLISuite.LOCALE_COMMAND + " -s --set " + EN_LOCALE,
            HoneycombCLISuite.LOCALE_COMMAND + " --set -s " + EN_LOCALE,
        };
        
        String [] [] lsInvalidLocaleArgs = {
            {LOCALE_S_COMMAND, "--set"},
            {LOCALE_SET_COMMAND, "-s"},
            {LOCALE_S_COMMAND, INVALID_LOCALE},
            {LOCALE_SET_COMMAND, INVALID_LOCALE}
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            String [] invalidMessage = HoneycombCLISuite.LOCALE_HELP;
            
            isTestPass = verifyCommandStdout(
                    false, lsInvalidArgs[i], invalidMessage);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        for (int i=0; i<lsInvalidLocaleArgs.length; i++) {
            String [] invalidMessage = getInvalidLocaleMessage(lsInvalidLocaleArgs[i][1]);
            
            isTestPass = verifyCommandStdout(
                    false, lsInvalidLocaleArgs[i][0] + lsInvalidLocaleArgs[i][1], invalidMessage);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    private String [] getInvalidLocaleMessage(String invalidLocale) {
        String [] LOCALE_INVALID_MESSAGE = 
            {"Language " + invalidLocale + " not installed on this system.",
             "Supported languages: en,fr,gr,ja,sc"};
        
        return LOCALE_INVALID_MESSAGE;
    }
    
    private void setLocale(String command, String locale) {
        setCurrentInternalLogFileNum();
                
        try {
            Log.INFO("Setting locale to: " + locale);
            
            BufferedReader output = runCommandWithoutCellid(command + locale);
            String [] lsOutput = excudeCustomerWarning(output);
            output.close();
                
            String line = lsOutput[0].trim();
            Log.INFO(command + " output: " + line);
                
            if (!line.equals("Hive reboot is required " +
                    "for command to take effect."))
                Log.WARN("Unexpected output: " + line);
                
          } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
    }
    
    private String getCurrentLocale() throws HoneycombTestException {
        String currentLocale = "";
        
        try {
            BufferedReader output = runCommandWithoutCellid(HoneycombCLISuite.LOCALE_COMMAND);
            String [] lsLocaleOutput = excudeCustomerWarning(output);
            output.close();
                
            for (int lineNo=0; lineNo<lsLocaleOutput.length; lineNo++) {
                String line = lsLocaleOutput[lineNo];               
                Log.INFO(HoneycombCLISuite.LOCALE_COMMAND + " output: " + line);
                
                // verify the output string
                if (line.indexOf(LOCALE_OUTPUT) == -1) 
                    throw new HoneycombTestException("wrong output format: " + line);
                    
                else {
                    String [] lsOutput = tokenizeIt(line, ":");
                    currentLocale = lsOutput[lsOutput.length-1].trim();
                    Log.INFO("Locale: " + currentLocale);
                }
            }
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }  
        
        return currentLocale;
    }
    
    private boolean verifyLocale(String expLocale) {
        verifyAuditLocale(expLocale, "info.adm.setLocale");
        
        try {
            // get locale from locale stdout
            String actLocale = getCurrentLocale();
                
            if (actLocale.equals(expLocale)) {
                Log.INFO("Current locale <" + actLocale + "> is displayed proeprly in <" +
                        HoneycombCLISuite.LOCALE_COMMAND + "> stdout");
                    
                // verify that the locale list also displyed current locale properly
                String output = getCommandStdout(false, LOCALE_L_COMMAND, LOCALE_OUTPUT);
                String [] lsOutput = tokenizeIt(output, ":");
                actLocale = lsOutput[lsOutput.length-1].trim();
                       
                if (actLocale.equals(expLocale)) {
                    Log.INFO("Current locale <" + actLocale + "> is displayed proeprly in <" +
                        LOCALE_L_COMMAND + "> stdout");
                    return true;
                }
                else {
                    Log.ERROR("Actual locale: " + actLocale + ", Expected locale: " +
                        expLocale);
                    return false;
                }
            }
            else {
                Log.ERROR("Actual locale: " + actLocale + ", Expected locale: " +
                        expLocale);
                return false;
            }
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }  
        
        return false;
    }
    
    private void verifyAuditLocale(String paramValue, String msg) {
        ArrayList paramValueList = new ArrayList();
        paramValueList.add(paramValue);
        verifyAuditInternalLog(HoneycombCLISuite.LOCALE_COMMAND, 
                msg, paramValueList, true);         
    }
}
