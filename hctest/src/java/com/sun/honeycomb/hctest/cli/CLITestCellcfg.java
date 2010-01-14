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
public class CLITestCellcfg extends HoneycombCLISuite {
    
    private static final String CELLCFG_ADMIN_IP_OUTPUT = "Admin IP Address";
    private static final String CELLCFG_DATA_IP_OUTPUT = "Data IP Address";
    private static final String CELLCFG_SERVICE_IP_OUTPUT = "Service Node IP Address";
    private static final String CELLCFG_GATEWAY_OUTPUT = "Gateway";
    private static final String CELLCFG_SUBNET_OUTPUT = "Subnet";
    
    private static String CELLCFG_ADMIN_IP_COMMAND = null;
    private static String CELLCFG_DATA_IP_COMMAND = null;
    private static String CELLCFG_SERVICE_IP_COMMAND = null;
    private static String CELLCFG_GATEWAY_COMMAND = null;
    private static String CELLCFG_SUBNET_COMMAND = null;
    
    private static String CELLCFG_ADMIN_IP_COMMAND_STR = " --admin_ip ";
    private static String CELLCFG_DATA_IP_COMMAND_STR = " --data_ip ";
    private static String CELLCFG_SERVICE_IP_COMMAND_STR = " --service_node_ip ";
    private static String CELLCFG_GATEWAY_COMMAND_STR = " --gateway ";
    private static String CELLCFG_SUBNET_COMMAND_STR = " --subnet ";
    
    private static String newAdminIp = null, newDataIp = null, newSpIp = null;
    
    /** Creates a new instance of CLITestCellcfg */
    public CLITestCellcfg() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: cellcfg\n");
        sb.append("\t o positive test - syntax, change admin,data and SP IPs\n");
        sb.append("\t o negative test - invalid option\n");
        sb.append("\tUsage: cellcfg test runs in part of CLI test suite, but " +
                "user can run cellcfg test in standalone mode as follow:\n");
        sb.append("\t\t - /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestCellcfg " +
                "-ctx nodes=8 -ctx cellcfgips=<new_admin_ip>,<new_data_ip>,<new_SP_IP> " +
                "-ctx cluster=devxxx\n");
        sb.append("\t\t For Example: - /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestCellcfg " +
                "-ctx nodes=8 -ctx cellcfgips=10.7.227.101,10.7.227.102,10.7.227.100 " +
                "-ctx cluster=dev390\n");
        sb.append("\t\t o cellcfgips is a required argument - admin, data & SP ips " +
                "seperated by comma\n");
        sb.append("\t\t o nodes is a required argument for single " +
                "cell setup, default to 8\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        
        super.setUp();
        
        CELLCFG_ADMIN_IP_COMMAND = HoneycombCLISuite.CELLCFG_COMMAND +
                CELLCFG_ADMIN_IP_COMMAND_STR;
        CELLCFG_DATA_IP_COMMAND = HoneycombCLISuite.CELLCFG_COMMAND +
                CELLCFG_DATA_IP_COMMAND_STR;
        CELLCFG_SERVICE_IP_COMMAND = HoneycombCLISuite.CELLCFG_COMMAND +
                CELLCFG_SERVICE_IP_COMMAND_STR;
        CELLCFG_GATEWAY_COMMAND = HoneycombCLISuite.CELLCFG_COMMAND +
                CELLCFG_GATEWAY_COMMAND_STR;
        CELLCFG_SUBNET_COMMAND = HoneycombCLISuite.CELLCFG_COMMAND +
                CELLCFG_SUBNET_COMMAND_STR;
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
        if (errorString == null)
            return false;
        errorString = errorString.toLowerCase();
        
        if (errorString.startsWith("invalid")
        || errorString.startsWith("configuration error")) {
            return true;
        }
        return super.isInErrorExclusionList(errorString);
    }
    
    public void testCellcfgSyntax() {
        TestCase self = createTestCase("CLI_cellcfg_syntax");
        
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
        
        boolean isTestPass = false;
        
        boolean isValidAdminIp = false, isValidDataIp = false,
                isValidServiceIp = false, isValidGateway = false,
                isValidSubnet = false, isValidStdout = true;
        
        int currentLineNo = 0;
        
        try {
            if (isMultiCell()) {
                isTestPass = verifyCommandStdout(false,
                        HoneycombCLISuite.CELLCFG_COMMAND,
                        HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);
            } else {
                BufferedReader output = runCommandWithoutCellid(
                        HoneycombCLISuite.CELLCFG_COMMAND);
                String line = null;
                
                while ((line = output.readLine()) != null) {
                    
                    currentLineNo ++;
                    
                    int equalsIndex = line.indexOf('=');
                    
                    if(-1 != equalsIndex) {
                        String valueString = line.substring(equalsIndex + 2);
                        
                        if (line.startsWith(CELLCFG_DATA_IP_OUTPUT)) {
                            String dataIp = valueString;
                            isValidDataIp = validateIp(CELLCFG_DATA_IP_OUTPUT, dataIp, false);
                        } else if (line.startsWith(CELLCFG_ADMIN_IP_OUTPUT)) {
                            String adminIp = valueString;
                            isValidAdminIp = validateIp(CELLCFG_ADMIN_IP_OUTPUT, adminIp, false);
                        } else if (line.startsWith(CELLCFG_SERVICE_IP_OUTPUT)) {
                            String serviceIp = valueString;
                            isValidServiceIp = validateIp(CELLCFG_SERVICE_IP_OUTPUT, serviceIp, false);
                        } else if (line.startsWith(CELLCFG_GATEWAY_OUTPUT)) {
                            String gateway = valueString;
                            isValidGateway = validateIp(CELLCFG_GATEWAY_OUTPUT, gateway, false);
                        } else if (line.startsWith(CELLCFG_SUBNET_OUTPUT)) {
                            String subnet = valueString;
                            isValidSubnet = validateIp(CELLCFG_SUBNET_OUTPUT, subnet, false);
                        } else {
                            Log.ERROR("Unexpected string: " +  line);
                            isValidStdout = false;
                        }
                    } else {
                        Log.ERROR("Unexpected line: " + line);
                        isValidStdout = false;
                    }
                }
                output.close();
                
                if ((isValidAdminIp) && (isValidDataIp) && (isValidServiceIp) &&
                        (isValidGateway) && (isValidSubnet) && (isValidStdout))
                    isTestPass = true;
            }
        } catch (Throwable t) {
            isTestPass = false;
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testCellcfgSyntaxWithCellid() {
        TestCase self = createTestCase("CLI_cellcfg_syntax_with_cellid");
        
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
        
        boolean isTestPass = false;
        
        boolean isValidAdminIp = false, isValidDataIp = false,
                isValidServiceIp = false, isValidGateway = false,
                isValidSubnet = false, isValidStdout = true;
        
        try {
            ArrayList allCellid = getAllCellid();
            int noOfFailure = 0;
            
            for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
                
                int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                setCellid(cellid);
                
                Log.INFO("Cell " + cellid + ":");
                
                BufferedReader output = runCommandWithCellid(
                        HoneycombCLISuite.CELLCFG_COMMAND, cellid);
                String line = null;
                
                while ((line = output.readLine()) != null) {
                    int equalsIndex = line.indexOf('=');
                    
                    if(-1 != equalsIndex) {
                        String valueString = line.substring(equalsIndex + 2);
                        
                        if (line.startsWith(CELLCFG_DATA_IP_OUTPUT)) {
                            String dataIp = valueString;
                            isValidDataIp = validateIp(
                                    CELLCFG_DATA_IP_OUTPUT, dataIp, false);
                        } else if (line.startsWith(CELLCFG_ADMIN_IP_OUTPUT)) {
                            String adminIp = valueString;
                            isValidAdminIp = validateIp(
                                    CELLCFG_ADMIN_IP_OUTPUT, adminIp, false);
                        } else if (line.startsWith(CELLCFG_SERVICE_IP_OUTPUT)) {
                            String serviceIp = valueString;
                            isValidServiceIp = validateIp(
                                    CELLCFG_SERVICE_IP_OUTPUT, serviceIp, false);
                        } else if (line.startsWith(CELLCFG_GATEWAY_OUTPUT)) {
                            String gateway = valueString;
                            isValidGateway = validateIp(
                                    CELLCFG_GATEWAY_OUTPUT, gateway, false);
                        } else if (line.startsWith(CELLCFG_SUBNET_OUTPUT)) {
                            String subnet = valueString;
                            isValidSubnet = validateIp(
                                    CELLCFG_SUBNET_OUTPUT, subnet, false);
                        } else {
                            Log.ERROR("Unexpected string: " +  line);
                            isValidStdout = false;
                        }
                    } else {
                        Log.ERROR("Unexpected line: " + line);
                        isValidStdout = false;
                    }
                }
                output.close();
                
                if ((!isValidAdminIp) || (!isValidDataIp) || (!isValidServiceIp) ||
                        (!isValidGateway) || (!isValidSubnet) || (!isValidStdout))
                    noOfFailure++;
            }
            
            if (noOfFailure == 0)
                isTestPass = true;
            
        } catch (Throwable t) {
            isTestPass = false;
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
        
        if (isTestPass)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testCellcfgNegativeTest() {
        
        TestCase self = createTestCase("CLI_cellcfg_Negative_Test");
        
        self.addTag(Tag.NEGATIVE);
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
        
        // will use admin ip as valid ip
        Integer cellid = (Integer) getAllCellid().get(0);
        String validIp = (String) getAllAdminIp().get(cellid);
        
        // get all invalid ip
        String [] lsInvalidIp = getAllInvalidIp();
        
        // will test each command with invalid ips
        String [] lsInvalidArgs = {
            CELLCFG_ADMIN_IP_COMMAND,
            CELLCFG_DATA_IP_COMMAND,
            CELLCFG_SERVICE_IP_COMMAND,
            CELLCFG_GATEWAY_COMMAND,
            CELLCFG_SUBNET_COMMAND,
        };
        
        // will test without cellid and with invalid cellid
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.CELLCFG_COMMAND,
            HoneycombCLISuite.CELLCFG_COMMAND + " -c",
            CELLCFG_ADMIN_IP_COMMAND + " " + validIp,
            CELLCFG_DATA_IP_COMMAND + " " + validIp,
            CELLCFG_SERVICE_IP_COMMAND + " " + validIp,
            CELLCFG_GATEWAY_COMMAND + " " + validIp,
            CELLCFG_SUBNET_COMMAND + " " + validIp,
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            for (int j=0; j<lsInvalidIp.length; j++) {
                
                String invalidArg = lsInvalidArgs[i] + " " + lsInvalidIp[j];
                
                // execute command with invalid ip (without cellid)
                isTestPass = verifyCommandStdout(
                        false, invalidArg, null);
                
                if (!isTestPass)
                    noOfFailure++;
                
                // execute command with invalid ip (with cellid)
                setCellid();
                isTestPass = verifyCommandStdout(
                        true, invalidArg, null);
                
                if (!isTestPass)
                    noOfFailure++;
            }
        }
        
        // execute command without cellid
        if (isMultiCell()) {
            isTestPass = verifyCommandStdout(
                    false, lsInvalidMulticellArgs[1],
                    null);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        // execute command with invalid cellid
        int invalidCellId = getInvalidCellid();
        
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
            String formattedCommand = formatCommandWithCellid(
                    lsInvalidMulticellArgs[i], invalidCellId);
            
            isTestPass = verifyCommandStdout(
                    false, formattedCommand, null);
            
            if (!isTestPass)
                noOfFailure++;
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    public void testCellcfgSetIps() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_cellcfg_set_admin_data_sp_ips");
        
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
        
        // make sure three new ips are provided as runtest args, otherwise test won't run
        String newCellIps =
                getProperty(HoneycombTestConstants.PROPERTY_CLI_CELLCFG_IPS);
        
        if (newCellIps == null) {
            Log.WARN("missing required runtest argument \"cellcfgips\" - will abort the test....");
            Log.INFO("use -ctx cellcfgips=<new_admin_ip>,<new_data_ip>,<new_SP_IP> as runtest argument");
            self.testFailed();
            return;
        }
        
        try {
            if (newCellIps.indexOf(",") == -1) {
                throw new HoneycombTestException("Syntax error in " +
                        "\"cellcfgips\" argument, verify help\n");
            } else {
                String [] lsNewCellIps = tokenizeIt(newCellIps, ",");
                newAdminIp = lsNewCellIps[0];
                newDataIp = lsNewCellIps[1];
                newSpIp = lsNewCellIps[2];
            }
        } catch (Exception e) {
            throw new HoneycombTestException("Syntax error in " +
                    "\"newcells\" argument, verify help\n" + e);
        }
        
        boolean isTestPass = false;
        
        ArrayList allCellid = getAllCellid();
        int noOfFailure = 0;
        
        for (int cellCount=0; cellCount<allCellid.size(); cellCount++) {
            
            int cellid = ((Integer) allCellid.get(cellCount)).intValue();
            setCellid(cellid);
            
            Integer cellidInt = new Integer(cellid);
            String currentAdminIp = (String) getAllAdminIp().get(cellidInt);
            String currentDataIp = (String) getAllDataIp().get(cellidInt);
            String currentSpIp = (String) getAllCheatIp().get(cellidInt);
            
            Log.SUM("Cell " + cellid + ":");
            
            isTestPass = setCellIps(cellidInt.intValue(), currentAdminIp,
                    currentDataIp, currentSpIp);
            if (!isTestPass)
                noOfFailure++;
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    private boolean setCellIps(int cellid, String adminIp,
            String dataIp, String spIp)
            throws HoneycombTestException {
        
        int noOfFailure = 0;
        
        boolean isMasterCell = false;
        if (cellid == getMasterCellid())
            isMasterCell = true;
        
        String masterCellAdminIp = getAdminVIP();
        String masterCellSpIp = getSpVIP();
        
        // set cell ips to new values
        setCurrentInternalLogFileNum();        
        runCellcfg(cellid, newAdminIp, newDataIp, newSpIp);
        
        if (isMasterCell) {
            setAdminVIP(newAdminIp);
            setSpVIP(newSpIp);            
            TestBed.getInstance().spIP = newSpIp;
        }
        
        initializeAllIp();
        setAllIp();
        
        // verify cell ips
        if (!verifyCellcfg(cellid, newAdminIp, newDataIp, newSpIp)) {
            noOfFailure++;
            Log.ERROR("Unable to change cell ips to new values");
        }
        
        // verify audit log
        verifyAuditCellcfg(cellid, newAdminIp, newDataIp, newSpIp);
                
        Log.INFO("***** Reset to original ips *****");
        setCurrentInternalLogFileNum();        
        runCellcfg(cellid, adminIp, dataIp, spIp);
        
        if (isMasterCell) {
            setAdminVIP(masterCellAdminIp);
            setSpVIP(masterCellSpIp);
            TestBed.getInstance().spIP = masterCellSpIp;
        }
        
        initializeAllIp();
        setAllIp();
        
        if (!verifyCellcfg(cellid, adminIp, dataIp, spIp)) {
            noOfFailure++;
            Log.ERROR("Unable to set back to original cell ips");
        }
        
        // verify audit log
        verifyAuditCellcfg(cellid, adminIp, dataIp, spIp);
        
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    private void runCellcfg(int cellid, String adminIp,
            String dataIp, String spIp)
            throws HoneycombTestException {
        
        Log.INFO("Set Admin Ip=" + adminIp + "; Data Ip=" + dataIp + "; SP Ip=" + spIp);
        try {
            String command = HoneycombCLISuite.CELLCFG_COMMAND + " -F " +
                    CELLCFG_ADMIN_IP_COMMAND_STR + adminIp +
                    CELLCFG_DATA_IP_COMMAND_STR + dataIp +
                    CELLCFG_SERVICE_IP_COMMAND_STR + spIp;
            
            BufferedReader output = runCommandWithCellid(command, cellid);
            String line = null;
            
            while ((line = output.readLine()) != null) {
                Log.INFO(line);
            }
            output.close();
            
        } catch (Throwable t) {
            Log.ERROR("Error to set cell ips:" + Log.stackTrace(t));
        }
        
        HCUtil.doSleep(1800, "Wait to reboot completely...");
    }
    
    private boolean verifyCellcfg(int cellid, String expAdminIp,
            String expDataIp, String expSpIp) {
        String actAdminIp = "", actDataIp = "", actSpIp = "";
        int noOfFailure = 0;
        
        try {
            String [] line = getCommandStdoutWithoutCellid(HoneycombCLISuite.CELLCFG_COMMAND +
                    HoneycombCLISuite.CELL_ID_ARG + cellid);
            for (int lineNum=0; lineNum<line.length; lineNum++) {
                String [] nameValue = tokenizeIt(line[lineNum], "=");
                if (nameValue[0].trim().equals(CELLCFG_ADMIN_IP_OUTPUT))
                    actAdminIp = nameValue[1].trim();
                else if (nameValue[0].trim().equals(CELLCFG_DATA_IP_OUTPUT))
                    actDataIp = nameValue[1].trim();
                else if (nameValue[0].trim().equals(CELLCFG_SERVICE_IP_OUTPUT))
                    actSpIp = nameValue[1].trim();
                else
                    ; //do nothing
            }
        } catch(Throwable t) {
            Log.ERROR("Unable to parse cellcfg stdout: " + Log.stackTrace(t));
            noOfFailure ++;
        }
        
        // comapare actual ips displayed by cli cellcfg with expected value
        if (expAdminIp.equals(actAdminIp)) {
            Log.INFO(CELLCFG_ADMIN_IP_OUTPUT + " : " + actAdminIp);
        } else {
            Log.ERROR("Actual " + CELLCFG_ADMIN_IP_OUTPUT + " : " + actAdminIp +
                    "; Expected: " + expAdminIp);
            noOfFailure ++;
        }
        
        if (expDataIp.equals(actDataIp)) {
            Log.INFO(CELLCFG_DATA_IP_OUTPUT + " : " + actDataIp);
        } else {
            Log.ERROR("Actual " + CELLCFG_DATA_IP_OUTPUT + " : " + actDataIp +
                    "; Expected: " + expDataIp);
            noOfFailure ++;
        }
        
        if (expSpIp.equals(actSpIp)) {
            Log.INFO(CELLCFG_SERVICE_IP_OUTPUT + " : " + actSpIp);
        } else {
            Log.ERROR("Actual " + CELLCFG_SERVICE_IP_OUTPUT + " : " + actSpIp +
                    "; Expected: " + expSpIp);
            noOfFailure ++;
        }
        
        // verify whether sysstat is displayed correct admin and data ips
        if (!verifySysstatOutput(cellid, "Admin VIP " + expAdminIp))
            noOfFailure++;
        if (!verifySysstatOutput(cellid, "Data VIP " + expDataIp))
            noOfFailure++;
        
        
        // verify silo_info.xml file
        try {
            if (!verifySiloInfoXmlFile(cellid, CELLCFG_ADMIN_IP_OUTPUT,  expAdminIp))
                noOfFailure++;
            if (!verifySiloInfoXmlFile(cellid, CELLCFG_DATA_IP_OUTPUT, expDataIp ))
                noOfFailure++;
            if (!verifySiloInfoXmlFile(cellid, CELLCFG_SERVICE_IP_OUTPUT,  expSpIp))
                noOfFailure++;
        } catch(Throwable t) {
            Log.ERROR("Unable to verify silo_info.xml file: " + Log.stackTrace(t));
            noOfFailure ++;
        }
        
        if (noOfFailure > 0)
            return false;
        else
            return true;
    }
    
    private boolean verifySysstatOutput(int cellid, String expectedStr) {
        String line = "";
        
        try {
            ArrayList lines = readSysstat(cellid);
            line = (String)lines.get(
                    HoneycombCLISuite.SYSSTAT_ADMIN_DATA_IP_LINE);
        } catch(Throwable t) {
            Log.ERROR("Unable to parse sysstat stdout: " + Log.stackTrace(t));
        }
        
        if (line.contains(expectedStr)) {
            Log.INFO(HoneycombCLISuite.SYSSTAT_COMMAND + " output: " +
                    line);
            return true;
        } else {
            Log.ERROR("Actual: " + line + ", Expected: " + expectedStr);
            return false;
        }
    }
    
    private boolean verifySiloInfoXmlFile(int cellid, String paramName,
            String paramValue) throws HoneycombTestException {
        int error = 0;
        
        Log.SUM("Verify " + SiloInfoXml.SILO_INFO_XML_FILE +
                " file for each cell node");
        
        int totalNode = -1;
        try {
            totalNode = getNodeNum();
        } catch(Throwable t) {
            Log.ERROR("Error while getting total node number: " + Log.stackTrace(t));
            return false;
        }
        
        for (int cellCount = 0;  cellCount<getAllCellid().size(); cellCount++) {
            Integer cellidInt = (Integer) getAllCellid().get(cellCount);
            String adminIp = (String) getAllAdminIp().get(cellidInt);
            String cheatIp = (String) getAllCheatIp().get(cellidInt);
            
            Log.INFO("Cell: adminip=" + adminIp);
            ClusterMembership cm =
                    new ClusterMembership(-1, getAdminVIP(), 0, 
                    cellidInt.intValue(), adminIp, cheatIp);
            cm.setQuorum(true);
            
            for (int i=1; i<=totalNode; i++) {
                Log.INFO("**** Node Number: " + i + " ****");
                
                ClusterNode node = cm.getNode(i);
                if (!node.isAlive()) {
                    Log.INFO("Cluster node " + node.getName() +
                            " is offline");
                    continue;
                }
                
                SiloInfoXml siloInfoXmlFileNode = new SiloInfoXml(node, cellid);
                String paramValueInSilo = null;
                
                if (paramName.equals(CELLCFG_ADMIN_IP_OUTPUT))
                    paramValueInSilo = siloInfoXmlFileNode.getSiloAdminIp();
                else if (paramName.equals(CELLCFG_DATA_IP_OUTPUT))
                    paramValueInSilo = siloInfoXmlFileNode.getSiloDataIp();
                else if (paramName.equals(CELLCFG_SERVICE_IP_OUTPUT))
                    paramValueInSilo = siloInfoXmlFileNode.getSiloSpIp();
                else if (paramName.equals(CELLCFG_GATEWAY_OUTPUT))
                    paramValueInSilo = siloInfoXmlFileNode.getSiloGateway();
                else
                    paramValueInSilo = siloInfoXmlFileNode.getSiloSubnet();
                
                if (paramValueInSilo.equals(paramValue))
                    Log.INFO(paramName + " = " + paramValue +
                            " is displayed properly in config file");
                else {
                    error++;
                    Log.ERROR(paramName + "- Expected: " + paramValue +
                            ", Actual: " + paramValueInSilo);
                }
            }
        }
        
        if (error == 0)
            return true;
        else
            return false;
    }
    
    private void verifyAuditCellcfg(int cellid, String adminIp,
            String dataIp, String spIp) {
        ArrayList paramValueList = new ArrayList();
        paramValueList.add(String.valueOf(cellid));
        
        // verify admin ip
        paramValueList.add(adminIp);
        verifyAuditInternalLog(HoneycombCLISuite.CELLCFG_COMMAND,
                "info.adm.setAdminIP", paramValueList, true);  
        
        // verify data ip
        paramValueList.remove(adminIp);
        paramValueList.add(dataIp);
        verifyAuditInternalLog(HoneycombCLISuite.CELLCFG_COMMAND,
                "info.adm.setDataIP", paramValueList, false);  
        
        // verify SP ip
        paramValueList.remove(dataIp);
        paramValueList.add(spIp);
        verifyAuditInternalLog(HoneycombCLISuite.CELLCFG_COMMAND,
                "info.adm.setServiceNodeIP", paramValueList, false);          
    }
}
