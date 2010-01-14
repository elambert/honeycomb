package com.sun.honeycomb.hctest.cli;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import java.io.BufferedReader;
import java.util.HashMap;

/**
 *
 * @author jk142663
 */
public class CLITestLogdump extends HoneycombCLISuite {

    private static String LOGDUMP_GEO = null;
    private static String LOGDUMP_PROXY_SERVER = null;
    private static String LOGDUMP_PROXY_PORT = null;
    private static String LOGDUMP_CONTACT_FIRST = null;
    private static String LOGDUMP_CONTACT_LAST = null;
    private static String LOGDUMP_CONTACT = null;
    private static String LOGDUMP_PHONE = null;
    private static String LOGDUMP_EMAIL = null;
    
    private static final String LOGDUMP_GEO_OPTION = "geo";
    private static final String LOGDUMP_PROXY_SERVER_OPTION = "proxy_server";
    private static final String LOGDUMP_PROXY_PORT_OPTION = "proxy_port";
    private static final String LOGDUMP_CONTACT_OPTION = "contact";
    private static final String LOGDUMP_PHONE_OPTION = "phone_num";
    private static final String LOGDUMP_EMAIL_OPTION = "email";
     
    private static String LOGDUMP_RUN_EXPLORER_COMMAND = null;
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: logdump\n");
        sb.append("\t o positive test - extracts the log information\n");
        sb.append("\t o negative test - no/invalid option\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        LOGDUMP_GEO = getAdminCliProperties().getProperty("cli.logdump.geo");
        if ((LOGDUMP_GEO == null) || (LOGDUMP_GEO.equals("")))
            throw new HoneycombTestException("Unable to get geo from properties file");

        LOGDUMP_PROXY_SERVER = getAdminCliProperties().getProperty("cli.logdump.proxy.server");
        if ((LOGDUMP_PROXY_SERVER == null) || (LOGDUMP_PROXY_SERVER.equals("")))
            throw new HoneycombTestException("Unable to get proxy server from properties file");

        LOGDUMP_PROXY_PORT = getAdminCliProperties().getProperty("cli.logdump.proxy.port");
        if ((LOGDUMP_PROXY_PORT == null) || (LOGDUMP_PROXY_PORT.equals("")))
            throw new HoneycombTestException("Unable to get proxy port from properties file");

        LOGDUMP_CONTACT_FIRST = getAdminCliProperties().getProperty("cli.logdump.contact.first");
        if ((LOGDUMP_CONTACT_FIRST == null) || (LOGDUMP_CONTACT_FIRST.equals("")))
            throw new HoneycombTestException("Unable to get first name from properties file");

        LOGDUMP_CONTACT_LAST = getAdminCliProperties().getProperty("cli.logdump.contact.last");
        if ((LOGDUMP_CONTACT_LAST == null) || (LOGDUMP_CONTACT_LAST.equals("")))
            throw new HoneycombTestException("Unable to get last name from properties file");

        LOGDUMP_CONTACT = LOGDUMP_CONTACT_LAST + "," + LOGDUMP_CONTACT_FIRST;
        
        LOGDUMP_PHONE = getAdminCliProperties().getProperty("cli.logdump.phone");
        if ((LOGDUMP_PHONE == null) || (LOGDUMP_PHONE.equals("")))
            throw new HoneycombTestException("Unable to get phone no from properties file");

        LOGDUMP_EMAIL = getAdminCliProperties().getProperty("cli.logdump.email");
        if ((LOGDUMP_EMAIL == null) || (LOGDUMP_EMAIL.equals("")))
            throw new HoneycombTestException("Unable to get email from properties file"); 
        
        LOGDUMP_RUN_EXPLORER_COMMAND = HoneycombCLISuite.LOGDUMP_COMMAND +
                HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                " --" + LOGDUMP_GEO_OPTION + " " + LOGDUMP_GEO + 
                " --" + LOGDUMP_PROXY_SERVER_OPTION + " " + LOGDUMP_PROXY_SERVER +
                " --" + LOGDUMP_PROXY_PORT_OPTION + " " + LOGDUMP_PROXY_PORT +
                " --" + LOGDUMP_CONTACT_OPTION + " " + LOGDUMP_CONTACT +
                " --" + LOGDUMP_PHONE_OPTION + " " + LOGDUMP_PHONE +
                " --" + LOGDUMP_EMAIL_OPTION + " " + LOGDUMP_EMAIL;
    }
    
    public void testLogdump() {
        TestCase self = createTestCase("CLI_logdump_run_explorer");
        self.addTag(HoneycombTag.CLI);
        self.addTag(Tag.HOURLONG);
        if (self.excludeCase()) return;

        int noOfFailure = 0;
        
        String explorerLogFileName = null;
        String matchStr = "explorer ID:";
        boolean isFound = false;
        
        // run the positive test twice in a row to validate that 
        // it generates output file properly if there's already 
        // an output file
        for (int iterationNum=0; iterationNum<2; iterationNum++) {
            try {           
                BufferedReader output = runCommandWithoutCellid(
                        LOGDUMP_RUN_EXPLORER_COMMAND);
                String line = null; 
                while ((line = output.readLine()) != null) {
                    Log.INFO(line);  
                    if (line.indexOf(matchStr) != -1) {
                        isFound = true;
                        String [] tokenizedLine = tokenizeIt(line.trim(), ":");

                        // file format: explorer.00000000.hcb101-2008.02.26.01.19.tar.gz
                        explorerLogFileName = "/var/adm/" +
                                tokenizedLine[tokenizedLine.length-1].trim() +
                                ".tar.gz";
                    }
                }
                output.close();

                // verify config.properties file
                if (!verifyConfigPropertiesFile())
                    noOfFailure ++;

                // verify the existence of log file generated by 
                // explorer on master node
                if (isFound) {
                    if (!verifyExplorerLogFile(explorerLogFileName))
                        noOfFailure ++;
                } 
                else {
                    noOfFailure ++;
                    Log.ERROR("Unable to get the log file name generated by explorer script");
                }

            } catch (Throwable t) {
                noOfFailure++;
                Log.ERROR("Error while running explorer:" + t.toString());
            }
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testLogdumpNegativeTest() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_logdump_Negative_Test");
        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        if (self.excludeCase()) return;

        String [] lsInvalidArgs = {
            HoneycombCLISuite.LOGDUMP_COMMAND + 
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION,
            HoneycombCLISuite.LOGDUMP_COMMAND + 
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " -" + LOGDUMP_GEO_OPTION + " " + LOGDUMP_GEO,
            HoneycombCLISuite.LOGDUMP_COMMAND + 
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION + 
                    " --" + LOGDUMP_GEO_OPTION.toUpperCase() + " " + LOGDUMP_GEO,
            /*HoneycombCLISuite.LOGDUMP_COMMAND +
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_PROXY_SERVER_OPTION + " " + LOGDUMP_PROXY_SERVER +
                    " --" + LOGDUMP_PROXY_PORT_OPTION + " " + LOGDUMP_PROXY_PORT +
                    " --" + LOGDUMP_CONTACT_OPTION + " " + LOGDUMP_CONTACT +
                    " --" + LOGDUMP_PHONE_OPTION + " " + LOGDUMP_PHONE +
                    " --" + LOGDUMP_EMAIL_OPTION + " " + LOGDUMP_EMAIL,*/
            HoneycombCLISuite.LOGDUMP_COMMAND +
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_GEO_OPTION + " " +  LOGDUMP_GEO +  LOGDUMP_GEO,      
            HoneycombCLISuite.LOGDUMP_COMMAND + 
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_GEO_OPTION, 
            HoneycombCLISuite.LOGDUMP_COMMAND +
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_GEO_OPTION + " " + LOGDUMP_GEO + 
                    " --" + LOGDUMP_PROXY_SERVER_OPTION +
                    " --" + LOGDUMP_PROXY_PORT_OPTION + " " + LOGDUMP_PROXY_PORT,
            HoneycombCLISuite.LOGDUMP_COMMAND +
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_GEO_OPTION + " " + LOGDUMP_GEO + 
                    " --" + LOGDUMP_PROXY_SERVER_OPTION + " " + LOGDUMP_PROXY_SERVER +
                    " --" + LOGDUMP_PROXY_PORT_OPTION,
            HoneycombCLISuite.LOGDUMP_COMMAND +
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_GEO_OPTION + " " + LOGDUMP_GEO + 
                    " --" + LOGDUMP_CONTACT_OPTION,        
            HoneycombCLISuite.LOGDUMP_COMMAND +
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_GEO_OPTION + " " + LOGDUMP_GEO + 
                    " --" + LOGDUMP_CONTACT_OPTION + " last_name first_name",
            HoneycombCLISuite.LOGDUMP_COMMAND +
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_GEO_OPTION + " " + LOGDUMP_GEO + 
                    " --" + LOGDUMP_PHONE_OPTION +
                    " --" + LOGDUMP_EMAIL_OPTION + " " + LOGDUMP_EMAIL,
            HoneycombCLISuite.LOGDUMP_COMMAND +
                    HoneycombCLISuite.HIDDEN_PROMPT_FORCE_OPTION +
                    " --" + LOGDUMP_GEO_OPTION + " " + LOGDUMP_GEO + 
                    " --" + LOGDUMP_PHONE_OPTION + " " + LOGDUMP_PHONE +
                    " --" + LOGDUMP_EMAIL_OPTION    
        };
        
        int noOfFailure = 0;
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            String [] expectedMessage = CLITestHelp.LOGDUMP_HELP;
            if (i == 3)
                expectedMessage = new String [] {
                    "Invalid geographic region specified.",
                    "Specify either AMERICAS, EMEA or APAC for the -g/--geo option.",
                    "Invalid parameter value(s), please try again."                    
                };
            
            boolean isTestPass = verifyCommandStdout(false, 
                    lsInvalidArgs[i], expectedMessage);
            
            if (!isTestPass)
                noOfFailure ++;
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    private boolean verifyConfigPropertiesFile() throws HoneycombTestException {
        int error = 0;
        
        Log.SUM("Verify " + ConfigProperties.CONFIG_PROPERTIES_FILE +
                        " file for each cell node");        
        
        String [] lsConfigParamName = {
            ConfigProperties.CONFIG_PROPERTIES_LOGDUMP_GEO,
            ConfigProperties.CONFIG_PROPERTIES_LOGDUMP_SERVER,
            ConfigProperties.CONFIG_PROPERTIES_LOGDUMP_PORT,
            ConfigProperties.CONFIG_PROPERTIES_LOGDUMP_CONTACT,
            ConfigProperties.CONFIG_PROPERTIES_LOGDUMP_PHONE,
            ConfigProperties.CONFIG_PROPERTIES_LOGDUMP_EMAIL
        };
        
        String [] lsConfigParamValue = {
            LOGDUMP_GEO,
            LOGDUMP_PROXY_SERVER,
            LOGDUMP_PROXY_PORT,
            LOGDUMP_CONTACT_FIRST + " " + LOGDUMP_CONTACT_LAST,
            LOGDUMP_PHONE,
            LOGDUMP_EMAIL
        };
        
        int totalNode = -1;
        try {
            totalNode = getNodeNum();
        } catch(Throwable t) {
            Log.ERROR("Error while getting total node number: " + t.toString());
            return false;
        }
        
        for (int cellCount = 0;  cellCount<getAllCellid().size(); cellCount++) {
            Integer cellid = (Integer) getAllCellid().get(cellCount);                
            String adminIp = (String) getAllAdminIp().get(cellid);
            String cheatIp = (String) getAllCheatIp().get(cellid);
            
            Log.SUM("Cell " + cellid + ": adminip=" + adminIp);
            ClusterMembership cm = 
                    new ClusterMembership(-1, getAdminVIP(), 0, 
                    cellid.intValue(), adminIp, cheatIp);
        
            cm.setQuorum(true);
            cm.initClusterState(cellid.intValue()); 
                    
            for (int i=1; i<=totalNode; i++) {
                Log.INFO("**** Node Number: " + i + " ****");
                
                ClusterNode node = cm.getNode(i); 
                if (!node.isAlive()) {
                    Log.INFO("Cluster node " + node.getName() +
                            " is offline");
                    continue;
                }
                
                ConfigProperties configFileNode = new ConfigProperties(
                        cm, i, ConfigProperties.lsLogdumpConfigPropName);
                HashMap configFile = configFileNode.getAllConfigPropNameValue();
        
                for (int j=0; j<lsConfigParamName.length; j++) {
                    String newExpValue = lsConfigParamValue[j];
                    String paramName = lsConfigParamName[j];                    
                    String newValueInConfig = (String) configFile.get(paramName);
                    
                    if (newValueInConfig.equals(newExpValue))
                        Log.INFO(paramName + " = " + newExpValue +
                            " is displayed properly in config file");
                    else {
                        error++;
                        Log.ERROR(paramName + "- Expected: " + newExpValue +
                            ", Actual: " + newValueInConfig);
                    }
                }
            }
        }
        
        if (error == 0)
            return true;
        else
            return false;        
    }
    
    private boolean verifyExplorerLogFile(String explorerLogFileName) {
        int noOfFailure = 0;  
        try {
            BufferedReader output = runCommandAsRoot(getAdminVIP(), 
                    "ls " + explorerLogFileName);
            String line = output.readLine();
            if (line == null) {
                noOfFailure ++;
                Log.ERROR("Unable to find the " + explorerLogFileName);
            }
            else {
                Log.INFO("LINE:" + line);
                if (line.trim().equals(explorerLogFileName))
                    Log.INFO("Found log file <" + explorerLogFileName +
                            "> generated by explorer");
                else {
                    noOfFailure ++;
                    Log.ERROR("Unable to find the " + explorerLogFileName);
                }
            }
            output.close();
        } catch (Throwable t) {
            noOfFailure++;
            Log.ERROR("Error while looking for log file " + 
                    explorerLogFileName + ":" + t.toString());
        }
        
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
}
