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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;

/**
 *
 * @author jk142663
 */
public class CLITestHivecfg extends HoneycombCLISuite {
    
    private static final String HIVECFG_SMTP_SERVER_OUTPUT = "SMTP Server";
    private static final String HIVECFG_SMTP_PORT_OUTPUT = "SMTP Port";
    private static final String HIVECFG_NTP_SERVER_OUTPUT = "NTP Server";
    private static final String HIVECFG_LOGGER_OUTPUT = "External Logger";
    private static final String HIVECFG_AUTH_CLIENT_OUTPUT = "Authorized Clients";
    private static final String HIVECFG_DNS_OUTPUT = "DNS";
    private static final String HIVECFG_DOMAIN_NAME_OUTPUT = "Domain Name";
    private static final String HIVECFG_DNS_SEARCH_OUTPUT = "DNS Search";
    private static final String HIVECFG_PRIMARY_DNS_SERVER_OUTPUT = "Primary DNS Server";
    private static final String HIVECFG_SECONDARY_DNS_SERVER_OUTPUT = "Secondary DNS Server";
        
    private static final String HIVECFG_SMTP_SERVER_PARAM = "smtp_server";
    private static final String HIVECFG_SMTP_PORT_PARAM = "smtp_port";
    private static final String HIVECFG_NTP_SERVER_PARAM = "ntp_server";
    private static final String HIVECFG_LOGGER_PARAM = "external_logger";
    private static final String HIVECFG_AUTH_CLIENT_PARAM = "authorized_clients";
    private static final String HIVECFG_DNS_PARAM = "dns";
    private static final String HIVECFG_DOMAIN_NAME_PARAM = "domain_name";
    private static final String HIVECFG_DNS_SEARCH_PARAM = "dns_search";
    private static final String HIVECFG_PRIMARY_DNS_SERVER_PARAM = "primary_dns_server";
    private static final String HIVECFG_SECONDARY_DNS_SERVER_PARAM = "secondary_dns_server";
    
    private static final String HIVECFG_SMTP_SERVER_PARAM_SHORT = "s";
    private static final String HIVECFG_SMTP_PORT_PARAM_SHORT = "p";
    private static final String HIVECFG_NTP_SERVER_PARAM_SHORT = "n";
    private static final String HIVECFG_LOGGER_PARAM_SHORT = "x";
    private static final String HIVECFG_AUTH_CLIENT_PARAM_SHORT = "h";
    private static final String HIVECFG_DNS_PARAM_SHORT = "D";
    private static final String HIVECFG_DOMAIN_NAME_PARAM_SHORT = "m";
    private static final String HIVECFG_DNS_SEARCH_PARAM_SHORT = "e";
    private static final String HIVECFG_PRIMARY_DNS_SERVER_PARAM_SHORT = "1";
    private static final String HIVECFG_SECONDARY_DNS_SERVER_PARAM_SHORT = "2";
    
    private static final String HIVECFG_AUTH_CLIENT_ALL = "all"; 
            
    private static String HIVECFG_SMTP_SERVER_COMMAND = null;
    private static String HIVECFG_SMTP_PORT_COMMAND = null;
    private static String HIVECFG_NTP_SERVER_COMMAND = null;
    private static String HIVECFG_LOGGER_COMMAND = null;
    private static String HIVECFG_AUTH_CLIENT_COMMAND = null;
    private static String HIVECFG_DNS_COMMAND = null;
    private static String HIVECFG_DOMAIN_NAME_COMMAND = null;
    private static String HIVECFG_DNS_SEARCH_COMMAND = null;
    private static String HIVECFG_PRIMARY_DNS_SERVER_COMMAND = null;
    private static String HIVECFG_SECONDARY_DNS_SERVER_COMMAND = null;
    
    private static final String HIVECFG_NTP_SERVER_F_STRING = " -N ";
    
    
    /** Creates a new instance of CLITestHivecfg */
    public CLITestHivecfg() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: hivecfg\n");
        sb.append("\t o positive test - change NTP/SMTP/logger/auth client/DNS \n");
        sb.append("\t o negative test - invalid option\n");
        sb.append("\nnodes is a required argument for single " +
                "cell setup (-ctx nodes=<node_num>), default to 8\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        HIVECFG_SMTP_SERVER_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_SMTP_SERVER_PARAM;
        HIVECFG_SMTP_PORT_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_SMTP_PORT_PARAM;
        HIVECFG_NTP_SERVER_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND +  
                " " + HIVECFG_NTP_SERVER_F_STRING + " --" + 
                HIVECFG_NTP_SERVER_PARAM;
        HIVECFG_LOGGER_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_LOGGER_PARAM;
        HIVECFG_AUTH_CLIENT_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_AUTH_CLIENT_PARAM;
        HIVECFG_DNS_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_DNS_PARAM;
        HIVECFG_DOMAIN_NAME_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_DOMAIN_NAME_PARAM;
        HIVECFG_DNS_SEARCH_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_DNS_SEARCH_PARAM;
        HIVECFG_PRIMARY_DNS_SERVER_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_PRIMARY_DNS_SERVER_PARAM;
        HIVECFG_SECONDARY_DNS_SERVER_COMMAND = HoneycombCLISuite.HIVECFG_COMMAND + " --" + 
                HIVECFG_SECONDARY_DNS_SERVER_PARAM;
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
        
	if (errorString.startsWith("invalid")
	    || errorString.startsWith("an ip address")) {
	    return true;
	}
	return super.isInErrorExclusionList(errorString);
    }
  
    public void testHivecfgSyntax() {
        TestCase self = createTestCase("CLI_hivecfg_syntax");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;

        boolean isTestPass = false;

        boolean isValidSmtpServer = false, isValidSmtpPort = false, 
                isValidNtpServer = false, isValidLogger = false,
                isValidAuthClient = false, isValidDns = false,
                isValidDomain = false, isValidDnsSearch = false, 
                isValidPrimaryDns = false, isValidSecondaryDns = false, 
                isValidStdout = true, isDnsEnabled = false;
        
        try {	    
            BufferedReader output = runCommandWithoutCellid(
                    HoneycombCLISuite.HIVECFG_COMMAND);
            String line = null;
            
            while ((line = output.readLine()) != null) {
                
                int equalsIndex = line.indexOf('=');
		
                if(-1 != equalsIndex) {
                    String valueString = line.substring(equalsIndex + 2);

                    if (line.startsWith(HIVECFG_SMTP_SERVER_OUTPUT)) {
                        String smtpServer = valueString;
                        isValidSmtpServer = validateHivecfgParam(
                                HIVECFG_SMTP_SERVER_OUTPUT, 
                                smtpServer, false);    
                    }
                    else if (line.startsWith(HIVECFG_SMTP_PORT_OUTPUT)) {
                        String port = valueString;
                        isValidSmtpPort = validateHivecfgParam(
                                HIVECFG_SMTP_PORT_OUTPUT, 
                                port, false);    
                    }
                    else if (line.startsWith(HIVECFG_NTP_SERVER_OUTPUT)) {
                        String ntpServer = valueString;
                        isValidNtpServer = validateNTPServer(ntpServer);   
                    }
                    else if (line.startsWith(HIVECFG_LOGGER_OUTPUT)) {
                        String logger = valueString;
                        isValidLogger = validateHivecfgParam(
                                HIVECFG_LOGGER_OUTPUT, 
                                logger, false);    
                    }
                    else if (line.startsWith(HIVECFG_AUTH_CLIENT_OUTPUT)) {
                        String authClient = valueString;
                        isValidAuthClient = validateAuthClient(authClient);
                    }
                    else if (line.startsWith(HIVECFG_DOMAIN_NAME_OUTPUT)) {
                        String domain = valueString;
                        isValidDomain = validateHivecfgParam(
                                HIVECFG_DOMAIN_NAME_OUTPUT, 
                                domain, true);    
                    }
                    else if (line.startsWith(HIVECFG_DNS_SEARCH_OUTPUT)) {
                        String dnsSearch = valueString;
                        isValidDnsSearch = validateHivecfgParam(
                                HIVECFG_DNS_SEARCH_OUTPUT,
                                dnsSearch, true);
                    }
                    else if (line.startsWith(HIVECFG_DNS_OUTPUT)) {
                        String dns = valueString;
			if (dns.equals("y"))
			    isValidDns = isDnsEnabled = true;
			else if (dns.equals("n")) {
                            isValidDns = true;
			    break;		
			} else
			    Log.ERROR("Invalid " + HIVECFG_DNS_OUTPUT + ": " 
				+ dns);
                    }
                    else if (line.startsWith(HIVECFG_PRIMARY_DNS_SERVER_OUTPUT)) {
                        String primaryDns = valueString;
                        isValidPrimaryDns = validateIp(HIVECFG_PRIMARY_DNS_SERVER_OUTPUT, 
                                primaryDns, true);
                    }
                    else if (line.startsWith(HIVECFG_SECONDARY_DNS_SERVER_OUTPUT)) {
                        String secondaryDns = valueString;
                        isValidSecondaryDns = validateIp(
                                HIVECFG_SECONDARY_DNS_SERVER_OUTPUT, secondaryDns, true);
                    }
                    else {
                        Log.ERROR("Unexpected string: " +  line);
                        isValidStdout = false;
                    }
                }
                else {
                    Log.ERROR("Unexpected line: " + line); 
                    isValidStdout = false;
                }
            }
            output.close();
            
            if ((isValidSmtpServer) && (isValidSmtpPort) && (isValidNtpServer)
                && (isValidLogger) && (isValidAuthClient) && (isValidDns)
		&& (isValidStdout)) {
		    // The DNS properties are only available when DNS is enabled
		    if (isDnsEnabled == false)
			isTestPass = true;
		    else
			if ((isValidDomain) && (isValidDnsSearch) 
			    && (isValidPrimaryDns) && (isValidSecondaryDns))
				isTestPass = true;  
	    }
            
        } catch (Throwable t) {
            isTestPass = false;
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
        
    public void testHivecfgNegativeTest() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hivecfg_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        // will use admin ip as valid ip 
        Integer cellid = (Integer) getAllCellid().get(0);
        String validIp = (String) getAllAdminIp().get(cellid);
        String domainName = getAdminVIP();
        if (domainName.indexOf(".") == -1) {
            // add domain
            String domain = System.getProperty(HCLocale.PROPERTY_DOMAIN);
            if (domain == null) {
                throw new HoneycombTestException("System properties: " +
                                                 HCLocale.PROPERTY_DOMAIN);
            }
            domainName = domainName + "." + domain;
        }
        
        // get all invalid ip
        String [] lsInvalidIp = getAllInvalidIp();
        String [] lsInvalidHost = getAllInvalidHost();
        String [] lsInvalidNumber = getAllInvalidInteger();
        
        String [] lsInvalidPort = new String[lsInvalidNumber.length +1];
        lsInvalidPort[0] = "O";    // The letter O
        for (int i=0; i<lsInvalidNumber.length; i++)
            lsInvalidPort[i+1] = lsInvalidNumber[i];
            
        // will test with each command with invalid values 
        String [] lsHivecfgParam = {  
            HoneycombCLISuite.HIVECFG_COMMAND,
            HIVECFG_SMTP_SERVER_COMMAND,
            HIVECFG_SMTP_PORT_COMMAND,
            HIVECFG_NTP_SERVER_COMMAND,
            HIVECFG_LOGGER_COMMAND,
            HIVECFG_AUTH_CLIENT_COMMAND,
            HIVECFG_DNS_COMMAND,
            HIVECFG_DOMAIN_NAME_COMMAND,   
            HIVECFG_PRIMARY_DNS_SERVER_COMMAND,
            HIVECFG_SECONDARY_DNS_SERVER_COMMAND
        };
        
         String [][] lsInvalidArgs = {
            {" -h"},
            lsInvalidHost,
            lsInvalidPort,
            lsInvalidHost,
            lsInvalidHost,
            lsInvalidHost,
            {"yes", "no", "YES", "NO"},
            lsInvalidHost,
            lsInvalidIp,
            lsInvalidIp         
         };
        
        // will test with cellid for multicell
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.HIVECFG_COMMAND,
            HIVECFG_SMTP_SERVER_COMMAND + " " + validIp,
            HIVECFG_SMTP_PORT_COMMAND + " 25",
            HIVECFG_NTP_SERVER_COMMAND + " " + validIp,
            HIVECFG_LOGGER_COMMAND + " " + validIp,
            HIVECFG_AUTH_CLIENT_COMMAND + " " + validIp,
            HIVECFG_DNS_COMMAND + " y", 
            HIVECFG_DOMAIN_NAME_COMMAND + " " + domainName,   
            HIVECFG_DNS_SEARCH_COMMAND + " " + domainName,
            HIVECFG_PRIMARY_DNS_SERVER_COMMAND + " " + validIp,
            HIVECFG_SECONDARY_DNS_SERVER_COMMAND + " " + validIp,
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsHivecfgParam.length; i++) {
            
            String [] lsInvalidVal = lsInvalidArgs[i];
            for (int j=0; j<lsInvalidVal.length; j++) {
            
                String invalidArg = lsHivecfgParam[i] + " " + lsInvalidVal[j];
                
                // execute command with invalid ip (without cellid)
                isTestPass = verifyCommandStdout(
                    false, invalidArg, null);
            
                if (!isTestPass)
                    noOfFailure++;
            }
        }
        
        // execute command with valid cellid
        setCellid();            
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
            isTestPass = verifyCommandStdout(
                    true, lsInvalidMulticellArgs[i], null);
        
            if (!isTestPass)
                noOfFailure++;       
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
      
    public void test1HivecfgSetDnsYes() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hivecfg_set_dns_yes");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        String domainName = getAdminCliProperties().getProperty("cli.domain.name");
        if ((domainName == null) || (domainName.equals("")))
            throw new HoneycombTestException("Unable to get Domain Name from properties file");

        String newDnsSearch = getAdminCliProperties().getProperty("cli.dns.search");
        if ((newDnsSearch == null) || (newDnsSearch.equals("")))
            throw new HoneycombTestException("Unable to get DNS Search from properties file");

        String newPrimaryServer = getAdminCliProperties().getProperty("cli.primary.dns.server");
        if ((newPrimaryServer == null) || (newPrimaryServer.equals("")))
            throw new HoneycombTestException("Unable to get primary server from properties file");

        String newSecondaryServer = getAdminCliProperties().getProperty("cli.secondary.dns.server");
        if ((newSecondaryServer == null) || (newSecondaryServer.equals("")))
            throw new HoneycombTestException("Unable to get secondary server from properties file");

        String [] lsParam = {HIVECFG_DNS_PARAM, HIVECFG_DOMAIN_NAME_PARAM, 
                HIVECFG_DNS_SEARCH_PARAM, HIVECFG_PRIMARY_DNS_SERVER_PARAM, 
                HIVECFG_SECONDARY_DNS_SERVER_PARAM};
        String [] lsParamShort = {HIVECFG_DNS_PARAM_SHORT, HIVECFG_DOMAIN_NAME_PARAM_SHORT, 
                HIVECFG_DNS_SEARCH_PARAM_SHORT, HIVECFG_PRIMARY_DNS_SERVER_PARAM_SHORT, 
                HIVECFG_SECONDARY_DNS_SERVER_PARAM_SHORT};
        String [] lsParamName = {HIVECFG_DNS_OUTPUT, HIVECFG_DOMAIN_NAME_OUTPUT,
                HIVECFG_DNS_SEARCH_OUTPUT, HIVECFG_PRIMARY_DNS_SERVER_OUTPUT, 
                HIVECFG_SECONDARY_DNS_SERVER_OUTPUT};
        String [] lsConfigParam = {ConfigProperties.CONFIG_PROPERTIES_DNS,
               ConfigProperties.CONFIG_PROPERTIES_DOMAIN_NAME,
               ConfigProperties.CONFIG_PROPERTIES_DNS_SEARCH,
               ConfigProperties.CONFIG_PROPERTIES_PRIMARY_DNS_SERVER,
               ConfigProperties.CONFIG_PROPERTIES_SECONDARY_DNS_SERVER};
        String [] lsParamValue = {"y", domainName, newDnsSearch, 
                newPrimaryServer, newSecondaryServer};
            
        Log.DEBUG("domain:" + domainName + ",se:" + newDnsSearch + ", p:" +
                newPrimaryServer + ",s:" + newSecondaryServer);
        
        isTestPass = changeHivecfgParam(lsParam, lsParamShort, 
                lsParamName, lsConfigParam, lsParamValue);
        
        // verify ntp server, smtp server, auth clients and external logger 
        // can accept hostname when dns is set to yes
        if (isTestPass) {
            
            Log.INFO("Verify NTP Server, SMTP Server, Auth clients and " +
                    "External logger can accept hostname when dns is set to yes");
            
            String newNtpServer = getAdminCliProperties().getProperty("cli.ntp.server");
            newNtpServer = getHostname(newNtpServer);
            if ((newNtpServer == null) || (newNtpServer.equals("")))
                throw new HoneycombTestException(
                        "Unable to get NTP Server from properties file");

            String newLogger = getAdminCliProperties().getProperty("cli.logger");
            newLogger = getHostname(newLogger);
            if ((newLogger == null) || (newLogger.equals("")))
                throw new HoneycombTestException(
                        "Unable to get External logger from properties file");
            
            String newSmtpServer = getAdminCliProperties().getProperty("cli.smtp.server");
            newSmtpServer = getHostname(newSmtpServer);
            if ((newSmtpServer == null) || (newSmtpServer.equals("")))
                throw new HoneycombTestException(
                        "Unable to get SMTP Server from properties file");
            
            String newAuthClient = getAdminCliProperties().getProperty("cli.authorized.clients");
            newAuthClient = getHostname(newAuthClient);
            if ((newAuthClient == null) || (newAuthClient.equals("")))
                throw new HoneycombTestException(
                        "Unable to get Auth clients from properties file");
                    
            String [] lsHostNameParam = {
                HIVECFG_NTP_SERVER_PARAM, 
                HIVECFG_LOGGER_PARAM,
                HIVECFG_SMTP_SERVER_PARAM, 
                HIVECFG_AUTH_CLIENT_PARAM
            };
            
            String [] lsHostNameParamShort = {
                HIVECFG_NTP_SERVER_PARAM_SHORT,
                HIVECFG_LOGGER_PARAM_SHORT,
                HIVECFG_SMTP_SERVER_PARAM_SHORT,
                HIVECFG_AUTH_CLIENT_PARAM_SHORT
            };
            
            String [] lsHostNameParamName = {
                HIVECFG_NTP_SERVER_OUTPUT,
                HIVECFG_LOGGER_OUTPUT, 
                HIVECFG_SMTP_SERVER_OUTPUT,
                HIVECFG_AUTH_CLIENT_OUTPUT
            };
            
            String [] lsHostNameConfigParam = {
                ConfigProperties.CONFIG_PROPERTIES_NTP_SERVER,
                ConfigProperties.CONFIG_PROPERTIES_LOGGER,
                ConfigProperties.CONFIG_PROPERTIES_SMTP_SERVER,
                ConfigProperties.CONFIG_PROPERTIES_AUTH_CLIENT
            };
            
            String [] lsHostNameParamValue = {
                newNtpServer,
                newLogger,
                newSmtpServer,
                newAuthClient
            };
        
            isTestPass = changeHivecfgParam(lsHostNameParam, lsHostNameParamShort,
                lsHostNameParamName, lsHostNameConfigParam, lsHostNameParamValue);        
        }
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void test2HivecfgSetDnsNo() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hivecfg_set_dns_no");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        String [] lsParam = {HIVECFG_DNS_PARAM};
        String [] lsParamShort = {HIVECFG_DNS_PARAM_SHORT};
        String [] lsParamName = {HIVECFG_DNS_OUTPUT};
        String [] lsConfigParam = {ConfigProperties.CONFIG_PROPERTIES_DNS};
        String [] lsParamValue = {"n"};            
        
        isTestPass = changeHivecfgParam(lsParam, lsParamShort,
                lsParamName, lsConfigParam, lsParamValue);
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
   
    public void test3HivecfgSetNtpServer() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hivecfg_set_ntp_server");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        String newNtpServer = getAdminCliProperties().getProperty("cli.ntp.server");
        if ((newNtpServer == null) || (newNtpServer.equals("")))
            throw new HoneycombTestException("Unable to get NTP Server from properties file");

        String [] lsParam = {HIVECFG_NTP_SERVER_PARAM};
        String [] lsParamShort = {HIVECFG_NTP_SERVER_PARAM_SHORT};
        String [] lsParamName = {HIVECFG_NTP_SERVER_OUTPUT};
        String [] lsConfigParam = {ConfigProperties.CONFIG_PROPERTIES_NTP_SERVER};
        String [] lsParamValue = {newNtpServer};
            
        isTestPass = changeHivecfgParam(lsParam, lsParamShort,
                lsParamName, lsConfigParam, lsParamValue);
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void test4HivecfgSetExternalLogger() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hivecfg_set_external_logger");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        String newLogger = getAdminCliProperties().getProperty("cli.logger");
        if ((newLogger == null) || (newLogger.equals("")))
            throw new HoneycombTestException("Unable to get logger from properties file");

        String [] lsParam = {HIVECFG_LOGGER_PARAM};
        String [] lsParamShort = {HIVECFG_LOGGER_PARAM_SHORT};
        String [] lsParamName = {HIVECFG_LOGGER_OUTPUT};
        String [] lsConfigParam = {ConfigProperties.CONFIG_PROPERTIES_LOGGER};
        String [] lsParamValue = {newLogger};
            
        isTestPass = changeHivecfgParam(lsParam, lsParamShort,
                lsParamName, lsConfigParam, lsParamValue);
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void test5HivecfgSetAuthClient() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hivecfg_set_auth_client");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        String newAuthClient = getAdminCliProperties().getProperty("cli.authorized.clients");
        if ((newAuthClient == null) || (newAuthClient.equals("")))
            throw new HoneycombTestException("Unable to get auth clients from properties file");

        String [] lsParam = {HIVECFG_AUTH_CLIENT_PARAM};
        String [] lsParamShort = {HIVECFG_AUTH_CLIENT_PARAM_SHORT};
        String [] lsParamName = {HIVECFG_AUTH_CLIENT_OUTPUT};
        String [] lsConfigParam = {ConfigProperties.CONFIG_PROPERTIES_AUTH_CLIENT};
        String [] lsParamValue = {newAuthClient};
            
        isTestPass = changeHivecfgParam(lsParam, lsParamShort,
                lsParamName, lsConfigParam, lsParamValue);
           
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void test6HivecfgSetSmtpServerPort() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hivecfg_set_smtp_server_port");

        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        boolean isTestPass = false;
        
        String newSmtpServer = getAdminCliProperties().getProperty("cli.smtp.server");
        if ((newSmtpServer == null) || (newSmtpServer.equals("")))
            throw new HoneycombTestException("Unable to get SMTP server from properties file");

        String newSmtpPort = getAdminCliProperties().getProperty("cli.smtp.port");
        if ((newSmtpPort == null) || (newSmtpPort.equals("")))
            throw new HoneycombTestException("Unable to get SMTP port from properties file");

        String [] lsParam = {HIVECFG_SMTP_SERVER_PARAM, HIVECFG_SMTP_PORT_PARAM};
        String [] lsParamShort = {HIVECFG_SMTP_SERVER_PARAM_SHORT, 
                                  HIVECFG_SMTP_PORT_PARAM_SHORT};
        String [] lsParamName = {HIVECFG_SMTP_SERVER_OUTPUT, HIVECFG_SMTP_PORT_OUTPUT};
        String [] lsConfigParam = {ConfigProperties.CONFIG_PROPERTIES_SMTP_SERVER,
                                   ConfigProperties.CONFIG_PROPERTIES_SMTP_PORT};
        String [] lsParamValue = {newSmtpServer, newSmtpPort};
            
        isTestPass = changeHivecfgParam(lsParam, lsParamShort,
                lsParamName, lsConfigParam, lsParamValue);
         
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    } 
    
    private boolean changeHivecfgParam(String [] lsParam, String [] lsParamShort, 
            String [] lsParamName, String [] lsConfigParam, 
            String [] lsNewParamValue) throws HoneycombTestException {
       
        int noOfFailure = 0;
        
        // get the old value
        String [] lsOldParamValue = new String[lsParamName.length];
       
	boolean dnsCheckEnabled = true; 
	int verifyParamCount = lsParamName.length;
        for (int i=0; i<lsParamName.length; i++) {
            lsOldParamValue[i] = getHivecfgParamValue(lsParamName[i]);
	    if (HIVECFG_DNS_OUTPUT.equals(lsParamName[i])
		&& "n".equals(lsOldParamValue[i])) {
		// If DNS is n then there are no more parameter values
		// to retrieve.
		break;
	    }
        }
            
        // set the new value
        String output = setHivecfgParamValue(false, lsParam, lsParamName, 
                lsNewParamValue);
        
        if ("n".equals(lsNewParamValue[0])) {
            Log.INFO("output:" + output);
            if (!output.equals(HoneycombCLISuite.REBOOT_MESSAGE)) {
                // If DNS lookups for hostnames works on the system then 
                // the DNS names will get converted to IP addresses and 
                // the command would succeed.
                //
                // if there is any problem to change the dns 
                // setup from yes to no, then try to convert all 
                // hostname to ip and then retry to set DNS to no
                
                String smtpServer = getHivecfgParamValue(HIVECFG_SMTP_SERVER_OUTPUT);
                String ntpServer = getHivecfgParamValue(HIVECFG_NTP_SERVER_OUTPUT);
                String logger = getHivecfgParamValue(HIVECFG_LOGGER_OUTPUT);
                String authClient = getHivecfgParamValue(HIVECFG_AUTH_CLIENT_OUTPUT);
                
                if ((!convertParamToIP(HIVECFG_SMTP_SERVER_PARAM, smtpServer)) ||
                    (!convertParamToIP(HIVECFG_NTP_SERVER_PARAM, ntpServer)) ||
                    (!convertParamToIP(HIVECFG_LOGGER_PARAM, logger)) ||
                    (!convertParamToIP(HIVECFG_AUTH_CLIENT_PARAM, authClient)))
                    
                    return false;
               
                // retry to set DNS to no after converting all hostname to ip
                output = setHivecfgParamValue(false, lsParam, lsParamName, 
                    lsNewParamValue);    
            }
        }
                       
        // verify hivecfg output
	for (int i=0; i<lsParamName.length; i++) {
            if (!verifyHivecfgOutput(lsParamName[i], lsNewParamValue[i]))
                noOfFailure++;
        }
        // verify config properties file 
        if (!verifyConfigPropertiesFile(lsConfigParam, lsNewParamValue))
            noOfFailure++;
        
        if (!lsParam[0].equals(HIVECFG_DNS_PARAM)) {            
            // set back to the original value - using short option
            Log.INFO("Set back to the original value");
            setHivecfgParamValue(true, lsParamShort, lsParamName, lsOldParamValue);
        }
        else {
            // set the dns value - using short option
            setHivecfgParamValue(true, lsParamShort, lsParamName, lsNewParamValue);
        }
        
        // verify hivecfg output
        for (int i=0; i<lsParamName.length; i++) {
            if (lsParam[0].equals(HIVECFG_DNS_PARAM)) { 
                if (!verifyHivecfgOutput(lsParamName[i], lsNewParamValue[i]))
                    noOfFailure++;  
            }
            else {
                if (!verifyHivecfgOutput(lsParamName[i], lsOldParamValue[i])) {
                    noOfFailure++;
                    Log.ERROR("Unable to set back to the original " + 
                        lsParamName[i] + " value");
                }
            }
        }
        
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    // will return param value of command hivecfg
    private String getHivecfgParamValue(String paramName) {
        String paramValue = null, line = null;

        try {
            line = getCommandStdout(false,
                    HoneycombCLISuite.HIVECFG_COMMAND, paramName);
        } catch(Throwable t) {
            Log.ERROR("Unable to parse hivecfg stdout: " + t.toString());
        }
        
        if (line == null) {
            Log.ERROR("Missing " + paramName + " in " +
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
    
    // set hivecfg param value 
    private String setHivecfgParamValue(boolean isShortOption,
            String [] lsParam, String [] lsParamName,
            String [] lsParamValue) {
        
        setCurrentInternalLogFileNum();
        String command = HoneycombCLISuite.HIVECFG_COMMAND;
        String allParam = "", allValue = "";
        
        String optionStr = " --";
        if (isShortOption)
            optionStr = " -";
        
        for (int i=0; i<lsParamName.length; i++) {
            
            if ((lsParam[i].equals(HIVECFG_NTP_SERVER_PARAM)) ||
                    (lsParam[i].equals(HIVECFG_NTP_SERVER_PARAM_SHORT)))
                command += " " + HIVECFG_NTP_SERVER_F_STRING;
            
            command += optionStr + lsParam[i] + " " + lsParamValue[i];
            
            allParam += lsParamName[i] + " ";
            allValue += lsParamValue[i] + " ";
        }
        
        Log.INFO("Set the <" + allParam + "> to the value <" + allValue + ">");
        String outputMsg = null;
        
        try {
            BufferedReader output = runCommandWithoutCellid(command);
            String line = null;        
                
            while ((line = output.readLine()) != null) {                
                Log.INFO(command + " output: " + line); 
                outputMsg = line;
            }
            output.close();
            
        } catch (Throwable t) {
            Log.ERROR("Error to set value <" + allValue + "> for param <" + 
                    allParam + "> :" + Log.stackTrace(t));
        }
        
        for (int i=0; i<lsParamName.length; i++) {
            String paramName = lsParamName[i];
            String paramValue = lsParamValue[i];
            String msg = "";
            if (paramName.equals(HIVECFG_SMTP_SERVER_OUTPUT))
                msg = "info.adm.setSMTPServer";
            else if (paramName.equals(HIVECFG_SMTP_PORT_OUTPUT))
                msg = "info.adm.setSMTPPort";
            else if (paramName.equals(HIVECFG_AUTH_CLIENT_OUTPUT))
                msg = "info.adm.setDataClients";
            else if (paramName.equals(HIVECFG_LOGGER_OUTPUT))
                msg = "info.adm.setExternalLogger";
            else if (paramName.equals(HIVECFG_NTP_SERVER_OUTPUT))
                msg = "info.adm.setNTPServer";
            else if (paramName.equals(HIVECFG_DNS_OUTPUT))
                msg = "info.adm.setDNS";
            else if (paramName.equals(HIVECFG_DOMAIN_NAME_OUTPUT))
                msg = "info.adm.setDomainName";
            else if (paramName.equals(HIVECFG_DNS_SEARCH_OUTPUT))
                msg = "info.adm.setDNSSearchPath";
            else if (paramName.equals(HIVECFG_PRIMARY_DNS_SERVER_OUTPUT))
                msg = "info.adm.setPrimaryDNSServer";
            else if (paramName.equals(HIVECFG_SECONDARY_DNS_SERVER_OUTPUT))
                msg = "info.adm.setSecondaryDNSServer";
                    
            if (i == 0)      
                verifyAuditHivecfg(paramName, paramValue, msg, true);
            else
                verifyAuditHivecfg(paramName, paramValue, msg, false);
        }
        
        return outputMsg;
    }
    
    private boolean convertParamToIP(String paramName, String paramValue) {
        paramValue = paramValue.trim();
        
        // if auth client sets to all, then return true
        if ((paramName.equals(HIVECFG_AUTH_CLIENT_PARAM)) && 
                (paramValue.equals(HIVECFG_AUTH_CLIENT_ALL)))
            return true;
        
        // auth client & NTP Server could have more than one value
        String [] lsIp = null;            
        if (paramValue.indexOf(",") != -1) {
            try {
                lsIp = tokenizeIt(paramValue, ",");
            } catch(Exception e) {
                Log.ERROR("Error to tokenize the string " + 
                        paramValue + ": " + e);
                return false;
            }
        }
        else
            lsIp = new String [] {paramValue};
        
        boolean isFoundHostName = false;
        paramValue = "";
        
        for (int i=0; i<lsIp.length; i++) {
            if (i != 0)
                paramValue += ",";
            
            // if param value is set to hostname, then change it to ip
            String ipVal = lsIp[i];
            if (!isIp(ipVal)) {
                isFoundHostName = true;
                ipVal = getIpAddress(ipVal);
                
                if (ipVal == null)
                    return false;
                
            }            
            paramValue += ipVal;            
        }
        
        // if param value is already in IP format, then don't do anything
        if (!isFoundHostName) 
            return true;
        
        String command = HoneycombCLISuite.HIVECFG_COMMAND;
               
        if (paramName.equals(HIVECFG_NTP_SERVER_PARAM))
            command += " " + HIVECFG_NTP_SERVER_F_STRING;
        
        command += " --" + paramName + " " + paramValue;
        
        Log.INFO("Set the <" + paramName + "> to the value <" + paramValue + ">");
         
        try {
            BufferedReader output = runCommandWithoutCellid(command);
            String line = null;
                
            while ((line = output.readLine()) != null) {                
                Log.INFO(command + " output: " + line);                
            }
            output.close();
            
        } catch (Throwable t) {
            Log.ERROR("Error to set value <" + paramValue + "> for param <" + 
                    paramName + "> :" + Log.stackTrace(t));
            
            return false;
        }    
        
        return true;
    }
    
    private boolean verifyConfigPropertiesFile(String [] lsConfigParamName,
            String [] lsNewParamValue) throws HoneycombTestException {
        int error = 0;
        
        Log.SUM("Verify " + ConfigProperties.CONFIG_PROPERTIES_FILE +
                        " file for each cell node");        
        
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
                        cm, i, ConfigProperties.lsHivecfgConfigPropName);
                HashMap configFile = configFileNode.getAllConfigPropNameValue();
                
                for (int j=0; j<lsConfigParamName.length; j++) {
                    String newExpValue = lsNewParamValue[j];
                    String paramName = lsConfigParamName[j];
                    
                    if ((paramName.equals(ConfigProperties.CONFIG_PROPERTIES_SMTP_PORT)) &&
                            (newExpValue.equals("25")))
                            continue;
                    
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
    
    private boolean verifyHivecfgOutput(String paramName, String expParamValue) {
        String actParamValue = getHivecfgParamValue(paramName);
        if (actParamValue != null && actParamValue.equals(expParamValue)) {
            Log.INFO(paramName + " : " + actParamValue);
            return true;
        }
        else {
            Log.ERROR("Actual " + paramName + " : " + actParamValue + 
                    "; Expected: " + expParamValue);        
            return false;
        }
    }
    
    private boolean validateHivecfgParam(String paramName, String paramValue, 
            boolean isOptional) {
        
        if ((paramValue == null) || (paramValue.equalsIgnoreCase("null"))) {
            Log.ERROR("Invalid " + paramName + ": null");
            return false;
        }
        
        paramValue = paramValue.trim();
        
        if (paramValue.equals("")) {
            if (isOptional) {
                Log.INFO(paramName + ": " + paramValue);        
                return true;
            }
            else {
                Log.ERROR("Invalid " + paramName + ": " + paramValue);
                return false;
            }   
        }
        
        Log.INFO(paramName + ": " + paramValue);        
        return true;
    }

    private boolean validateNTPServer(String ntpServer) {
        boolean isValidNtpServer = false;
        
        if (ntpServer.indexOf(",") != -1) {
            String [] lsNtpServer = null;
            
            try {
                lsNtpServer = tokenizeIt(ntpServer, ",");
            } catch(Exception e) {
                Log.ERROR("Error to tokenize the string " + 
                        ntpServer + ": " + e);
                return false;
            }
            
            isValidNtpServer = true;
                            
            for (int num=0; num<lsNtpServer.length; num++) {
                                
                boolean isValidNtpServerTmp = validateHivecfgParam(
                        HIVECFG_NTP_SERVER_OUTPUT, lsNtpServer[num], false); 
                                
                if (!isValidNtpServerTmp)
                    isValidNtpServer = false;
            }
        }
        else                            
            isValidNtpServer = validateHivecfgParam(HIVECFG_NTP_SERVER_OUTPUT, 
                    ntpServer, false); 
        
        return isValidNtpServer;
    }
    
    private boolean validateAuthClient(String authClient) {
        boolean isValidAuthClient = false;
        
        if (authClient.equals(HIVECFG_AUTH_CLIENT_ALL)) {
            isValidAuthClient = true;
            Log.INFO(HIVECFG_AUTH_CLIENT_OUTPUT + ": " + authClient);    
        }
        else if (authClient.indexOf(",") != -1) {
            String [] lsAuthClient = null;
            
            try {
                lsAuthClient = tokenizeIt(authClient, ",");
            } catch(Exception e) {
                Log.ERROR("Error to tokenize the string " + 
                        authClient + ": " + e);
                return false;
            }
            
            isValidAuthClient = true;
        
            for (int num=0; num<lsAuthClient.length; num++) {

                boolean isValidAuthClientTmp = validateHivecfgParam(
                        HIVECFG_AUTH_CLIENT_OUTPUT, lsAuthClient[num], false); 
                                
                if (!isValidAuthClientTmp)
                    isValidAuthClient = false;
            }
        }
        else   
            isValidAuthClient = validateHivecfgParam(HIVECFG_AUTH_CLIENT_OUTPUT, 
                    authClient, false);   
        
        return isValidAuthClient;
    }
    
    private void verifyAuditHivecfg(String paramName, String paramValue, String msg, 
            boolean isNewSessionId) {
        ArrayList paramValueList = new ArrayList();
        paramValueList.add(paramValue);
        verifyAuditInternalLog(HoneycombCLISuite.HIVECFG_COMMAND + ":" + paramName,
                msg, paramValueList, isNewSessionId);         
    }
}
