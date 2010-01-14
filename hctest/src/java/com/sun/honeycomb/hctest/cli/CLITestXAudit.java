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
public class CLITestXAudit extends HoneycombCLISuite {

    private static final String MAIL_SERVER = "hclog301.sfbay.sun.com";
    private static final String MAILBOX_LOCATION = "/var/log/honeycmb/";
    private static String MAILBOX = null;
    private long extLogLineNumBeforeTest = 0;
    private long logLineNumBeforeTest = 0;
    
    private static final String HIVECFG_LOGGER_OUTPUT = "External Logger";
    private static final String HIVECFG_LOGGER_PARAM = " --external_logger ";
    private static String ALERTCFG_ADD_COMMAND = null;
    private static String ALERTCFG_DELETE_COMMAND = null;
    private static String email = null;
    
    public CLITestXAudit() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tThis test will verify whether audit log feature " +
                "is working properly or not. If external logger is not set to " +
                MAIL_SERVER + " then test will change external logger and then " +
                "reset back to original logger at the end of test\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        ALERTCFG_ADD_COMMAND = 
            HoneycombCLISuite.ALERTCFG_COMMAND + " " + 
            CLITestAlertcfg.ALERTCFG_ADD_STRING + " " +
            CLITestAlertcfg.ALERTCFG_TO_STRING + " ";
        ALERTCFG_DELETE_COMMAND = 
            HoneycombCLISuite.ALERTCFG_COMMAND + " " + 
            CLITestAlertcfg.ALERTCFG_DEL_STRING + " " + 
            CLITestAlertcfg.ALERTCFG_TO_STRING + " ";
        
        email = getAdminCliProperties().getProperty("cli.email");
        if ((email == null) || (email.equals("")))
            throw new HoneycombTestException("Unable to get email " +
                    "address from properties file");
    }
    
    public void testAuditInternalExternalLogs() {
        TestCase self = createTestCase("CLI_Audit_Test_internal_external_log_file"); 
        self.addTag(HoneycombTag.CLI);
        
        if (self.excludeCase()) return;
        if (!doAuditLogVerification) return;
            
        String currentExtLog = getExternalLogger();
        String extLoggerIp = getIpAddress(MAIL_SERVER);
        boolean isExtLoggerWrong = false;
        
        if ((currentExtLog.equals(extLoggerIp)) || 
                (currentExtLog.equals(MAIL_SERVER))) {
            Log.INFO("External log server is configured properly");
        }
        else {
            isExtLoggerWrong = true;
            boolean isSetExtLogger = setExternalLogger(extLoggerIp);
            if (!isSetExtLogger) {
                self.testFailed("Unable to set external logger to " + 
                        extLoggerIp + ", will abort audit test");
                return;
            }
        }
        
        Integer cellid = (Integer) getAllCellid().get(0);
        String ip = (String) getAllAdminIp().get(cellid);
        MAILBOX = MAILBOX_LOCATION + ip + ".log";
       
        // add/delete alert and then verify both internal and external 
        // log file for audit log message
        String [] lsCommand = {
            ALERTCFG_ADD_COMMAND, 
            ALERTCFG_DELETE_COMMAND
        };
        
        String [] lsAuditMsg = {
            "info.adm.addToEmail",
            "info.adm.delToEmail"
        }; 
        
        int noOfFailure = 0;
        for (int i=0; i<lsCommand.length; i++) {
            setCurrentLogFileNum();
            runAlertcfg(lsCommand[i]);
            boolean isVerifyAudit = verifyAuditInternalExternalLog(lsAuditMsg[i]);
            if (!isVerifyAudit)
                noOfFailure ++;
        }
        
        // reset to original external logger
        if (isExtLoggerWrong) {
            boolean isSetExtLogger = setExternalLogger(currentExtLog);
            if (!isSetExtLogger) {
                Log.WARN("Unable to reset original external logger to " + 
                        currentExtLog);
            }
        }
        
        if (noOfFailure != 0)
            self.testFailed();
        self.testPassed();
    }
    
    public void testAuditInternalLog() {
        TestCase self = createTestCase("CLI_Audit_Test_internal_log_file"); 
        self.addTag(HoneycombTag.CLI);
        
        if (self.excludeCase()) return;
        if (!doAuditLogVerification) return;
         
        if(lsOfFailedAuditTest == null)
            self.testPassed("No error in Audit test after running CLI test suite");
        else {
            Log.INFO("The following messages are failed to audit to log files " +
                    "while running CLI test suite");
            for (int i=0; i<lsOfFailedAuditTest.size(); i++)
                Log.WARN("<" + lsOfFailedAuditTest.get(i) + ">");
            Log.ERROR("AUDIT FAILED");
            self.testFailed();
        }
    }
        
    // will return external logger value using command hivecfg
    private String getExternalLogger() {
        String paramValue = null, line = null;

        try {
            line = getCommandStdout(false,
                    HoneycombCLISuite.HIVECFG_COMMAND, HIVECFG_LOGGER_OUTPUT);
        } catch(Throwable t) {
            Log.ERROR("Unable to parse hivecfg stdout: " + t.toString());
        }
        
        if (line == null) {
            Log.ERROR("Missing " + HIVECFG_LOGGER_OUTPUT + " in " +
                    HoneycombCLISuite.HIVECFG_COMMAND + " output");
        }
        else {
            int equalsIndex = line.indexOf('=');
            if(-1 != equalsIndex) {
                paramValue = line.substring(equalsIndex + 2);
            }
        }
    
        return paramValue;
    }

    private boolean setExternalLogger(String ip) {
        Log.INFO("WIll configure External log server to " + ip);
        String command = HoneycombCLISuite.HIVECFG_COMMAND + 
                  HIVECFG_LOGGER_PARAM + ip;
        try {
            BufferedReader output = runCommandWithoutCellid(command);
            String line = null;        
                
            while ((line = output.readLine()) != null) {                
                Log.INFO(command + " output: " + line); 
            }
            output.close();
            
        } catch (Throwable t) {
            Log.ERROR("Error to set external logger:" + Log.stackTrace(t));
            return false;
        }  
        
        Log.INFO("WIll reboot cluster to make External log server effective");
        boolean isReboot = rebootCluster();
        if (!isReboot)
            return false;
        
        return true;
    }
    
    private boolean rebootCluster() {
        String command = HoneycombCLISuite.REBOOT_COMMAND;
         
        try {
            BufferedReader output = runCommandWithoutCellid(command);
            String line = null;        
                
            while ((line = output.readLine()) != null) {                
                Log.INFO(command + " output: " + line); 
            }
            output.close();
            HCUtil.doSleep(1800, "sleeping 30 mins after reboot");            
        } catch (Throwable t) {
            Log.ERROR("Error to set external logger:" + Log.stackTrace(t));
            return false;
        }
        return true;   
    }
    
    private void runAlertcfg(String command) {
        command += " " + email;
        
        try {
            BufferedReader output = runCommandWithoutCellid(command);
            String line = null;        
                
            while ((line = output.readLine()) != null) {                
                Log.INFO(command + " output: " + line); 
            }          
            output.close();
        } catch (Throwable t) {
            Log.ERROR("Error to set external logger:" + Log.stackTrace(t));
        }
    }
    
    // save current log file line number
    private void setCurrentLogFileNum() {
        logLineNumBeforeTest = getCurrentLogFileLine(
                        getSpVIP(), MESSAGE_LOG_FILE); 
        extLogLineNumBeforeTest = getCurrentLogFileLine(
                        MAIL_SERVER, MAILBOX);        
    }
    
    // verify both internal & external log files 
    private boolean verifyAuditInternalExternalLog(String msg) {
        int noOfFailure = 0;
        
        TestProperties admin_audit_msg_prop = new TestProperties(
                    TestProperties.CLI_AUDIT_MESSAGE_PROPERTY_FILE);
        String msgExp = admin_audit_msg_prop.getProperty(msg);
        
        ArrayList lsParamValueExp = new ArrayList();
        lsParamValueExp.add(email);
        
        // verify server log file
        if(!isMessageInLogFile(getSpVIP(), MESSAGE_LOG_FILE, logLineNumBeforeTest, 
                msgExp, lsParamValueExp, true))
            noOfFailure ++;
        
        // verify external log file
        if(!isMessageInLogFile(MAIL_SERVER, MAILBOX, extLogLineNumBeforeTest, 
                msgExp, lsParamValueExp, false))
            noOfFailure ++;
        
        if (noOfFailure != 0) {
            Log.ERROR("AUDIT FAILED");
            return false;
        }
        return true;
    }
}
