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

import java.util.StringTokenizer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

/**
 *
 * @author jk142663
 */
public class HoneycombCLISuite extends HoneycombLocalSuite {
        
    // all commands 
    protected static String ALERTCFG_COMMAND = null;
    protected static String CELLADM_COMMAND = null;
    protected static String CELLCFG_COMMAND = null;
    protected static String COPYRIGHT_COMMAND = null;
    protected static String DATE_COMMAND = null;
    protected static String DF_COMMAND = null;
    protected static String HELP_COMMAND = null;
    protected static String HIVEADM_COMMAND = null;
    protected static String HIVECFG_COMMAND = null;
    protected static String HWCFG_COMMAND = null;
    protected static String HWSTAT_COMMAND = null;
    protected static String LICENSE_COMMAND = null;
    protected static String LOCALE_COMMAND = null;
    protected static String LOGOUT_COMMAND = null;
    protected static String LOGDUMP_COMMAND = null;
    protected static String MDCONFIG_COMMAND = null;
    protected static String PROTOCOL_PASSWD_COMMAND = null;
    protected static String PASSWD_COMMAND = null;
    protected static String PERFSTATS_COMMAND = null;
    protected static String REBOOT_COMMAND = null;
    protected static String SENSORS_COMMAND = null;
    protected static String SETUPCELL_COMMAND = null;
    protected static String SHUTDOWN_COMMAND = null;
    protected static String SYSSTAT_COMMAND = null;
    protected static String UPGRADE_COMMAND = null;
    protected static String VERSION_COMMAND = null;
    protected static String WIPE_COMMAND = null;
    
    // all helps
    protected static String [] ALERTCFG_HELP = null;    
    protected static String [] CELLADM_HELP = null;    
    protected static String [] CELLCFG_HELP = null;   
    protected static String [] COPYRIGHT_HELP = null;
    protected static String [] DATE_HELP = null;    
    protected static String [] DF_HELP = null;    
    protected static String [] HELP_HELP = null;    
    protected static String [] HIVEADM_HELP = null;    
    protected static String [] HIVECFG_HELP = null;    
    protected static String [] HWCFG_HELP = null;    
    protected static String [] HWSTAT_HELP = null;
    protected static String [] LICENSE_HELP = null;    
    protected static String [] LOCALE_HELP = null;    
    protected static String [] LOGOUT_HELP = null;    
    protected static String [] LOGDUMP_HELP = null;    
    protected static String [] MDCONFIG_HELP = null;    
    protected static String [] PROTOCOL_PASSWD_HELP = null;    
    protected static String [] PASSWD_HELP = null;    
    protected static String [] PERFSTATS_HELP = null;    
    protected static String [] REBOOT_HELP = null;    
    protected static String [] SENSORS_HELP = null;
    protected static String [] SETUPCELL_HELP = null;
    protected static String [] SHUTDOWN_HELP = null;    
    protected static String [] SYSSTAT_HELP = null;    
    protected static String [] UPGRADE_HELP = null;    
    protected static String [] VERSION_HELP = null;    
    protected static String [] WIPE_HELP = null;
    
    private static boolean isCommandNameDefined = false;
    private static boolean isCommandHelpDefined = false;
    
    protected static final String YES_COMMAND = "/usr/bin/yes "; 
    
    // for audit log verification
    protected static final String MESSAGE_LOG_FILE = "/var/adm/messages";
    protected static final String MESSAGE_LOG_FILE_0 = "/var/adm/messages.0";
    private static final String seesionIdStr = "session id";
    private static final String externalStr = "EXT_INFO";
    protected static boolean doAuditLogVerification;
    private static ArrayList seesionIdList = null;
    protected static ArrayList lsOfFailedAuditTest = null;
    private long logLineNumBeforeTest = 0;
                
    // for sysstat command
    protected static int SYSSTAT_CLUSTER_STATE_LINE = 0;
    protected static int SYSSTAT_NODE_DISK_STATE_LINE = 1;    
    protected static int SYSSTAT_ADMIN_DATA_IP_LINE = 2;
    protected static int SYSSTAT_SERVICES_STATE_LINE = 3;
    protected static String SYSSTAT_CLUSTER_ONLINE = "Online";
    protected static String SYSSTAT_CLUSTER_OFFLINE = "Offline";
    protected static String SYSSTAT_SERVICES_AVAILABLE = "Data services Online";
    protected static String SYSSTAT_SERVICES_OFFLINE = "Data services Offline";
     
    // option to force hidden commands without prompt
    protected static String HIDDEN_PROMPT_FORCE_OPTION = " -F ";
    protected static String CUSTOMER_WARNING_STRING = "***********";
    protected static int CUSTOMER_WARNING_LINE_COUNT = 3;
    
    // messages for invalid cellid/fru/node
    protected static String INVALID_CELLID_MESSAGE = "invalid cell id specified";
    protected static String INVALID_FRU_MESSAGE = 
            "unable to find a fru for the specified fruid";
    protected static String INVALID_NODE_MESSAGE = "invalid node name";
    protected static String INVALID_DISK_MESSAGE = "invalid disk name";
    
    // messages to do reboot --all after changing config param 
    protected static String REBOOT_MESSAGE = "You must reboot the cluster " +
            "with reboot --all for changes to take effect.";
    
    // usage message for multicell
    protected static String [] MULTICELL_USAGE_MESSAGE = 
            {"This is a multi cell hive, the cell ID must be specified."};
    protected static String [] MULTICELL_EXPANSION_MESSAGE = 
            {"A cell may not be expanded in a multicell configuration."};    
    
    // list of all commands
    private static ArrayList commandList = null;
    
    // cluster ip
    private String adminVIP, spVIP;
    
    // cellid(key) -> ip(value)
    private HashMap allAdminIp = null;
    private HashMap allDataIp = null;
    private HashMap allCheatIp = null;
    
    // total no of cells in hive
    private int totalNoOfCell = -1;
    
    // list of all cell ids that are part of hive
    private ArrayList listOfCellId = null;
    
    // cell id that is used to test
    private int CURRENT_TEST_CELL_ID = -1;
    
    protected static final String CELL_ID_ARG = " -c ";
    protected static final String CELL_ID_STDOUT_PATTERN = "^cell\\s+.*([^\\s]+):$";
    protected static final String CELL_ID_ERROR = " doesn't support \"-c\" option.";
    
    private int VALID_CELL_ID_MAX = 127;
    private int VALID_NODE_ID_MAX = 116;
    private int VALID_DISK_ID_MAX = 4;
    
    private TestProperties admin_resource_prop = null;
    private TestProperties admin_cli_prop = null;
    private TestProperties admin_audit_msg_prop = null;
    
    /** Creates a new instance of HoneycombCLISuite */
    public HoneycombCLISuite() {
         super();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG("com.sun.honeycomb.test.HoneycombCLISuite::setUp() called");
        super.setUp();
        
        TestBed b = TestBed.getInstance();
        if (b != null) {
            adminVIP  = b.adminVIP;
            spVIP = b.spIP;
        
        } else {
            throw new HoneycombTestException("Unable to get adminVIP.");
        }
        
        if (admin_resource_prop == null)
            admin_resource_prop = new TestProperties(
                    TestProperties.ADMIN_RESOURCE_PROPERTY_FILE);
        if (admin_cli_prop == null)
            admin_cli_prop = new TestProperties(
                    TestProperties.CLI_PROPERTY_FILE);
         
        // assign all command names
        if (!isCommandNameDefined) 
            setAllCommandName();
        
        // set all cell ids
        if (totalNoOfCell == -1)
            setAllCellid();
        
        // set all admin/data/cheat ips
        setAllIp(); 
        
        // assign all command helps
        if (!isCommandHelpDefined)
            setAllCommandHelp();   
        
        // for audit log verification
        if (seesionIdList == null) {
            seesionIdList = new ArrayList();
            
            String s = getProperty(HoneycombTestConstants.PROPERTY_SKIPPEDAUDITTEST);
            if (s != null) {
                Log.INFO("Property " + HoneycombTestConstants.PROPERTY_SKIPPEDAUDITTEST +
                    " is specified. Skipping audit log verification");
                doAuditLogVerification = false;
            }
            else {
                doAuditLogVerification = true;
            }
        }
    }
    
    public void tearDown() throws Throwable {
        super.tearDown();
    }
    
    protected void setAllCommandName() {
        isCommandNameDefined = true;
        
        try {
            if (admin_resource_prop == null)
                admin_resource_prop = new TestProperties(
                        TestProperties.ADMIN_RESOURCE_PROPERTY_FILE);
            
            commandList = new ArrayList();
            
            ALERTCFG_COMMAND = admin_resource_prop.getProperty("cli.commandname.alertcfg");
            commandList.add(ALERTCFG_COMMAND);
            
            CELLADM_COMMAND =  admin_resource_prop.getProperty("cli.commandname.celladm") +
                    HIDDEN_PROMPT_FORCE_OPTION;
            
            CELLCFG_COMMAND =  admin_resource_prop.getProperty("cli.commandname.cellcfg");
            commandList.add(CELLCFG_COMMAND);
	    
	    COPYRIGHT_COMMAND =  admin_resource_prop.getProperty("cli.commandname.copyright");
            commandList.add(COPYRIGHT_COMMAND);
            
            DATE_COMMAND =  admin_resource_prop.getProperty("cli.commandname.date");
            commandList.add(DATE_COMMAND);
            
            DF_COMMAND =  admin_resource_prop.getProperty("cli.commandname.df");
            commandList.add(DF_COMMAND);
            
            HELP_COMMAND =  admin_resource_prop.getProperty("cli.commandname.help");
            commandList.add(HELP_COMMAND);
            
            HIVEADM_COMMAND =  admin_resource_prop.getProperty("cli.commandname.hiveadm");
            commandList.add(HIVEADM_COMMAND);
            
            HIVECFG_COMMAND =  admin_resource_prop.getProperty("cli.commandname.hivecfg");
            commandList.add(HIVECFG_COMMAND);
            
            HWCFG_COMMAND =  admin_resource_prop.getProperty("cli.commandname.hwcfg") +
                    HIDDEN_PROMPT_FORCE_OPTION;
            
            HWSTAT_COMMAND =  admin_resource_prop.getProperty("cli.commandname.hwstat");
            commandList.add(HWSTAT_COMMAND);
            
            LICENSE_COMMAND =  admin_resource_prop.getProperty("cli.commandname.license") +
                    HIDDEN_PROMPT_FORCE_OPTION;
            
            LOCALE_COMMAND =  admin_resource_prop.getProperty("cli.commandname.locale") +
                    HIDDEN_PROMPT_FORCE_OPTION;
            
            LOGOUT_COMMAND =  admin_resource_prop.getProperty("cli.commandname.logout");
            commandList.add(LOGOUT_COMMAND);
            
            LOGDUMP_COMMAND =  admin_resource_prop.getProperty("cli.commandname.logdump");
            commandList.add(LOGDUMP_COMMAND);
            
            MDCONFIG_COMMAND =  admin_resource_prop.getProperty("cli.commandname.mdconfig");
            commandList.add(MDCONFIG_COMMAND);
            
            PROTOCOL_PASSWD_COMMAND =  admin_resource_prop.getProperty("cli.commandname.protocolpasswd") +
		    HIDDEN_PROMPT_FORCE_OPTION;            
            
            PASSWD_COMMAND =  admin_resource_prop.getProperty("cli.commandname.passwd");
            commandList.add(PASSWD_COMMAND);
            
            PERFSTATS_COMMAND =  admin_resource_prop.getProperty("cli.commandname.perfstats");
            commandList.add(PERFSTATS_COMMAND);
            
            REBOOT_COMMAND =  admin_resource_prop.getProperty("cli.commandname.reboot");
            commandList.add(REBOOT_COMMAND);
            
            SENSORS_COMMAND =  admin_resource_prop.getProperty("cli.commandname.sensors");
            commandList.add(SENSORS_COMMAND);
             
            SETUPCELL_COMMAND =  admin_resource_prop.getProperty("cli.commandname.setupcell")+
                    HIDDEN_PROMPT_FORCE_OPTION;
            
            SHUTDOWN_COMMAND =  admin_resource_prop.getProperty("cli.commandname.shutdown");
            commandList.add(SHUTDOWN_COMMAND);
            
            SYSSTAT_COMMAND =  admin_resource_prop.getProperty("cli.commandname.sysstat");
            commandList.add(SYSSTAT_COMMAND);
            
            UPGRADE_COMMAND =  admin_resource_prop.getProperty("cli.commandname.upgrade")+
                    HIDDEN_PROMPT_FORCE_OPTION;
            
            VERSION_COMMAND =  admin_resource_prop.getProperty("cli.commandname.version");
            commandList.add(VERSION_COMMAND);
            
            WIPE_COMMAND =  admin_resource_prop.getProperty("cli.commandname.wipe");
            commandList.add(WIPE_COMMAND);            
        } catch(Exception e){
            Log.ERROR("Problem while getiing values from Admin Resource " +
                    "properties file: " + Log.stackTrace(e));
        }
    }    
    
    protected void setAllCommandHelp() {
        isCommandHelpDefined = true;
        
        ALERTCFG_HELP = getHelp(ALERTCFG_COMMAND, "cli.alertcfg.usage", 
                "cli.alertcfg.opts", false);         
        CELLADM_HELP = getHelp(CELLADM_COMMAND, "cli.celladm.usage",                 
                "cli.celladm.opts", false);         
        CELLCFG_HELP = getHelp(CELLCFG_COMMAND, "cli.cellcfg.usage", 
                "cli.cellcfg.opts", true);  
        COPYRIGHT_HELP = getHelp(CELLCFG_COMMAND, "cli.copyright.usage", 
                "cli.copyright.opts", true);    
        DATE_HELP = getHelp(DATE_COMMAND, "cli.date.usage", 
                "cli.date.opts", true);    
        DF_HELP = getHelp(DF_COMMAND, "cli.df.usage", 
                "cli.df.opts", false);    
        HELP_HELP = getHelp(HELP_COMMAND, "cli.help.usage", 
                "cli.help.opts", false);            
        HIVECFG_HELP = getHelp(HIVECFG_COMMAND, "cli.hivecfg.usage",
                "cli.hivecfg.opts", false);    
        HIVEADM_HELP = getHelp(HIVEADM_COMMAND, "cli.hiveadm.usage", 
                "cli.hiveadm.opts", false);             
        HWCFG_HELP = getHelp(HWCFG_COMMAND, "cli.hwcfg.usage", 
                "cli.hwcfg.opts", true);    
        HWSTAT_HELP = getHelp(HWSTAT_COMMAND, "cli.hwstat.usage", 
                "cli.hwstat.opts", true);
        LICENSE_HELP = getHelp(LICENSE_COMMAND, "cli.license.usage", 
                "cli.license.opts", true);    
        LOCALE_HELP = getHelp(LOCALE_COMMAND, "cli.locale.usage", 
                "cli.locale.opts", false);    
        LOGOUT_HELP = getHelp(LOGOUT_COMMAND, "cli.logout.usage", 
                "cli.logout.opts", false);    
        LOGDUMP_HELP = getHelp(LOGDUMP_COMMAND, "cli.logdump.usage", 
                "cli.logdump.opts", false);    
        MDCONFIG_HELP = getHelp(MDCONFIG_COMMAND, "cli.mdconfig.usage", 
                "cli.mdconfig.opts", false);    
        PROTOCOL_PASSWD_HELP = getHelp(PROTOCOL_PASSWD_COMMAND, 
                "cli.protocolpasswd.usage", "cli.protocolpasswd.opts", false);    
        PASSWD_HELP = getHelp(PASSWD_COMMAND, "cli.passwd.usage", 
                "cli.passwd.opts", false);    
        PERFSTATS_HELP = getHelp(PERFSTATS_COMMAND, "cli.perfstats.usage", 
                "cli.perfstats.opts", true);    
        REBOOT_HELP = getHelp(REBOOT_COMMAND, "cli.reboot.usage", 
                "cli.reboot.opts", true);    
        SENSORS_HELP = getHelp(SENSORS_COMMAND, "cli.sensors.usage", 
                "cli.sensors.opts", true);    
        SETUPCELL_HELP = getHelp(SETUPCELL_COMMAND, "cli.setupcell.usage", 
                "cli.setupcell.opts", false);    
        SHUTDOWN_HELP = getHelp(SHUTDOWN_COMMAND, "cli.shutdown.usage", 
                "cli.shutdown.opts", true);    
        SYSSTAT_HELP = getHelp(SYSSTAT_COMMAND, "cli.sysstat.usage", 
                "cli.sysstat.opts", false);    
        UPGRADE_HELP = getHelp(UPGRADE_COMMAND, "cli.upgrade.usage", 
                "cli.upgrade.opts", true);    
        VERSION_HELP = getHelp(VERSION_COMMAND, "cli.version.usage", 
                "cli.version.opts", true);    
        WIPE_HELP = getHelp(WIPE_COMMAND, "cli.wipe.usage", 
                "cli.wipe.opts", false);
    }
    
    // will use Admin Resource properties file to assign help varables for each command
    protected String [] getHelp(String commandName, String usageName, 
            String optsName, boolean isMulticellUsage) {
        String [] lsHelp = null;
        String [] lsUsage = null;
        String [] lsOption = null;
        
        if (admin_resource_prop == null)
            admin_resource_prop = new TestProperties(
                    TestProperties.ADMIN_RESOURCE_PROPERTY_FILE);
                       
        try {
            String usageString = admin_resource_prop.getProperty(usageName);
            lsUsage = tokenizeIt(usageString, "\n");
            
            
            // remove -F option from hidden command name
            if (commandName.indexOf(HIDDEN_PROMPT_FORCE_OPTION) != -1) {
                String [] tmp = tokenizeIt(commandName, HIDDEN_PROMPT_FORCE_OPTION);
                commandName = tmp[0];
            }
            
            // multi-cell: Usage: command_name -c <cellid> [options]
            // single cell: Usage: command_name [options]
            String optionsString = "Usage: " + commandName + " ";
            if ((isMultiCell()) && (isMulticellUsage)) {
                if ((UPGRADE_COMMAND.indexOf(commandName) == -1) &&
                        (LICENSE_COMMAND.indexOf(commandName) == -1))
                    optionsString += CELL_ID_ARG.trim() + " <cellid> ";
            }
            
            String optsNameTmp = admin_resource_prop.getProperty(optsName);
            
            if (isMultiCell()) {
                if ((commandName.equals(SENSORS_COMMAND)) ||
                    (commandName.equals(DATE_COMMAND)))
                    optsNameTmp = "";                
                else if (UPGRADE_COMMAND.indexOf(commandName) != -1) {
                    optsNameTmp = admin_resource_prop.getProperty(
                            "cli.upgrade.opts.multicell");
                }
                else if (LICENSE_COMMAND.indexOf(commandName) != -1) {
                    optsNameTmp = admin_resource_prop.getProperty(
                            "cli.license.opts.multicell");
                }
                else
                    ;
            }
            
            optionsString += optsNameTmp;
            
            lsOption = tokenizeIt(optionsString, "\n");
                
            lsHelp = new String[lsOption.length + lsUsage.length];
                
            for (int i=0; i<lsOption.length; i++) {
                lsHelp[i] = lsOption[i];
            } 
                
            for (int i=0; i<lsUsage.length; i++) {
                lsHelp[lsOption.length + i] = lsUsage[i];
            }                         
            
        } catch(NullPointerException e){
            Log.WARN("Missing <" + usageName + "/" + optsName +
                    "> from Admin Resource properties file: " 
		+ Log.stackTrace(e));
        } catch(Exception e){
            Log.WARN("Problem while getiing help for " + usageName + "" +
                    " from Admin Resource properties file: " 
		+ Log.stackTrace(e));
        }
        
        return lsHelp;
    }
    
    // Pass the delimiter as parameter
    public static String [] tokenizeIt(String string, String delimiter) 
                                                     throws Exception {
        StringTokenizer st = new StringTokenizer(string, delimiter);
        String [] tokenized;
        tokenized = new String[ st.countTokens()];
        int mctr = 0;
    
        while (st.hasMoreTokens()) {
            tokenized[mctr] = st.nextToken().trim();
            mctr++;
        }
         
        return tokenized ;
    } 
    
    protected TestProperties getAdminResourceProperties() {
        if (admin_resource_prop == null)
            admin_resource_prop = new TestProperties(
                    TestProperties.ADMIN_RESOURCE_PROPERTY_FILE);
        
        return admin_resource_prop;        
    }
    
    protected TestProperties getAdminCliProperties() {
        if (admin_cli_prop == null)
            admin_cli_prop = new TestProperties(
                    TestProperties.CLI_PROPERTY_FILE);
        
        return admin_cli_prop;    
    }
    
    protected String getAdminVIP() {
        return this.adminVIP;
    }
    
    protected String getSpVIP() {
        return this.spVIP;
    }
    
    protected void setAdminVIP(String ip) {
        this.adminVIP =  ip;;
    }
    
    protected void setSpVIP(String ip) {
        this.spVIP = ip;
    }
    
    protected int getNodeNum() throws Throwable {
        int totalNode = 16;
        
        if (!isMultiCell()) {
            String nodeStr = getProperty(HoneycombTestConstants.PROPERTY_NODES);
                        
            if (null != nodeStr) {
                try {
                    totalNode = Integer.parseInt(nodeStr);
		    
                } catch (NumberFormatException nfe) {
		    totalNode = -1;
		}
		if (totalNode <= 0) {
		    // If the node argument isn't correct don't
		    // continue since this is the basic
		    // argument needed by the tests and it
		    // needs to be correct in order for a
		    // successful run to occur.
                    Log.ERROR(
			"Invalid number of cluster nodes." 
			   + "nodes property set to=" + nodeStr 
                           +"\n This should be at least 1, default 8.  "
			   + "Make sure -ctx node= argument is correct.");
		    System.exit(-1);
                }
            }
            else
                totalNode = 8; //default to 8
        }
        
        return totalNode;
    }
    
    protected int getCellid() {
        if (validateCellId(CURRENT_TEST_CELL_ID))
            return CURRENT_TEST_CELL_ID;
        else {
            Log.ERROR("Invalid Cell Id <" + CURRENT_TEST_CELL_ID + ">");
            System.exit(1);
        }
        
        return -1;
    }
    
    protected int getMasterCellid() {
        int masterCellid = ((Integer)listOfCellId.get(0)).intValue();
        
        for (int num=1; num<listOfCellId.size(); num++) {
            int cellid = ((Integer)listOfCellId.get(num)).intValue();
            if (cellid < masterCellid)
                masterCellid = cellid;
        }
        
        return masterCellid;
    }
    
    protected void setCellid(int cellid) {
        if (validateCellId(cellid))
            CURRENT_TEST_CELL_ID = cellid;
        else {
            Log.ERROR("Invalid Cell Id <" + cellid + ">");
            System.exit(1);
        }
    }
    
    protected void setCellid() {
        int cellid = ((Integer) getAllCellid().get(0)).intValue();
        if (validateCellId(cellid))
            CURRENT_TEST_CELL_ID = cellid;
        else {
            Log.ERROR("Invalid Cell Id <" + CURRENT_TEST_CELL_ID + ">");
            System.exit(1);
        }
    }
    
    protected void setAllCellid() {
        if (getTotalNoOfCell() != -1) return;
        
        String command = null;
        
        try {
            command = getAdminResourceProperties().
                    getProperty("cli.commandname.hiveadm") + " -s";
            Log.SUM("Execute command <" + command + 
                    "> to determine single or multi-cell and will get all cellid(s)");
            BufferedReader output = runCommandWithoutCellid(command);
            
            String outputStr = HCUtil.readLines(output);
            output.close();
	    if (outputStr == null || outputStr.length() == 0) {
		Log.INFO("Command execution failed.  No output from hiveadm.  "
		    + "Make sure your test client can access your cluster.");
		System.exit(1);
	    }
            String [] lsLine = tokenizeIt(outputStr, "\n");
            
            String [] line = tokenizeIt(lsLine[0], " ");
            totalNoOfCell = new Integer(line[2]).intValue();
            
            listOfCellId = new ArrayList();
            for (int i=1; i<lsLine.length; i++) {
                line = tokenizeIt(lsLine[i], " ");
                
                if (line[2].indexOf(":") != -1) {
                    String [] lsTmp = tokenizeIt(line[2], ":");
                    line[2] = lsTmp[0];
                }
                
                Integer cellid = new Integer(line[2]);
                listOfCellId.add(cellid);
            }
        } catch(Throwable e) {
            Log.ERROR("Error while executing <" + command + 
                    "> command to get cellid(s): " 
		+ Log.stackTrace(e));
            Log.INFO("aborting the cli test");
            Log.WARN("IF MULTICELL: " +
                     "ARE YOU USING CELL W/ LOWEST CELLID FOR ADMIN VIP??");
            System.exit(1);
        }
    }
    
    protected int getTotalNoOfCell() {
        return totalNoOfCell;
    }
    
    protected void setTotalNoOfCell(int noOfCell) {
        totalNoOfCell = noOfCell;
    }
    
    protected ArrayList getAllCellid() {
        return listOfCellId;
    }
    
    protected int getInvalidCellid() {
        return (VALID_CELL_ID_MAX + 1);
    }
    
    protected int getInvalidNodeId() {
        return (VALID_NODE_ID_MAX + 1);
    }
    
    protected int getInvalidDiskId() {
        return (VALID_DISK_ID_MAX + 1);
    }
    
    protected ArrayList getAllCommandList() {
        return commandList;
    }
    
    // verify whether the cell id is int or not
    protected boolean validateCellId(int cellid) {
        if ((cellid < 0) || (cellid > VALID_CELL_ID_MAX))
            return false;
        
        return true;
    }
    
    protected boolean isMultiCell() {
        boolean isMultiCell = false;
        
        if (getTotalNoOfCell() == -1) 
            setAllCellid();
        
        // if silo has more than one cell
        if (getTotalNoOfCell() > 1) 
            isMultiCell = true;
        
        return isMultiCell;        
    }
    
    // set all admin ips for each cell
    protected void setAllAdminIp() {
        if (allAdminIp == null)
            allAdminIp = getAllIp("admin");
    }
    
    // return all admin ips for each cell
    protected HashMap getAllAdminIp() {
        return allAdminIp;
    }
    
    // set all cheat ips for each cell
    protected void setAllCheatIp() {
        if (allCheatIp == null)
            allCheatIp = getAllIp("cheat");
    }
    
    // return all cheat ips for each cell
    protected HashMap getAllCheatIp() {
        return allCheatIp;
    }
    
    // set all data ips for each cell
    protected void setAllDataIp() {
        if (allDataIp == null)
            allDataIp = getAllIp("data");
    }
    
    // return all data ips for each cell
    protected HashMap getAllDataIp() {
        return allDataIp;
    }
    
    // return all IPs of type ipType for each cell
    // valid ipType "admin", "data", "cheat"
    // cellid(key) -> ip(value)
    protected HashMap getAllIp(String ipType) {
        HashMap ipMap = null;
        String command = null;
        
        try {
            command = CELLCFG_COMMAND;
            Log.INFO("Execute command " + command + 
                    " to get all " + ipType + " ips");
            
            String ipName = null;
            if (ipType.equalsIgnoreCase("admin"))
                ipName = "Admin ip";
            else if (ipType.equalsIgnoreCase("data"))
                ipName = "Data ip";
            else if (ipType.equalsIgnoreCase("cheat"))
                ipName = "Service node ip";
            else {
                Log.ERROR("Invalid ip type: " + ipType);
                return null;
            }
                
            ipMap = new HashMap();
            
            for (int i= 0; i<listOfCellId.size(); i++) {
                int cellid = ((Integer) listOfCellId.get(i)).intValue();
                setCellid(cellid);
                    
                BufferedReader output = runCommand(command);
                String line = null;
                    
                while ((line = output.readLine()) != null) {  
                    if (line.indexOf(ipName) != -1) {
                        String [] lsString = tokenizeIt(line, "=");
                        ipMap.put(new Integer(cellid), lsString[1]);
                    }
                }
                output.close();
            }
        } catch(Throwable e) {
            Log.ERROR("Error while executing <" + command + 
                    "> command to get " + ipType + " ip(s): " 
		+ Log.stackTrace(e));
            System.exit(1);
        }
        
        return ipMap;
    }
    
    // set all IPs of type ipType for each cell
    // valid ipType "admin", "data", "cheat"
    // cellid(key) -> ip(value)
    protected void setAllIp() {
        if ((allAdminIp != null) && (allDataIp != null) && (allCheatIp != null))
            ; // do nothing
        else {
            allAdminIp = null;
            allDataIp = null;
            allCheatIp = null;
        
            String adminIpName = "Admin IP Address";
            String dataIpName = "Data IP Address";
            String cheatIpName = "Service Node IP Address";
                    
            String command = null;
        
            try {
                command = CELLCFG_COMMAND;
                Log.SUM("Execute command <" + command + 
                    "> to get all admin/data/cheat ips");
                        
                allAdminIp = new HashMap();
                allDataIp = new HashMap();
                allCheatIp = new HashMap();
            
                for (int i= 0; i<listOfCellId.size(); i++) {
                    int cellid = ((Integer) listOfCellId.get(i)).intValue();
                    setCellid(cellid);
                    
                    BufferedReader output = runCommand(command);
                    String line = null;
                    
                    while ((line = output.readLine()) != null) {
                        if (line.indexOf(adminIpName) != -1) {
                            String [] lsString = tokenizeIt(line, "=");
                            allAdminIp.put(new Integer(cellid), lsString[1]);
                        }
                        else if (line.indexOf(dataIpName) != -1) {
                            String [] lsString = tokenizeIt(line, "=");
                            allDataIp.put(new Integer(cellid), lsString[1]);
                        }
                        else if (line.indexOf(cheatIpName) != -1) {
                            String [] lsString = tokenizeIt(line, "=");
                            allCheatIp.put(new Integer(cellid), lsString[1]);
                        }
                        else
                            ; // do nothing
                    }
                    output.close();
                }
            } catch(Throwable e) {
                Log.ERROR("Error while executing <" + command + 
                    "> command to get admin/data/cheat ip(s): " 
		    + Log.stackTrace(e));
                System.exit(1);
            }
        }
    }
    
    protected void initializeAllIp() {
        allAdminIp = null;
        allDataIp = null;
        allCheatIp = null;
    }

    // execute command without cell id for single-cell
    // execute command with cell id for multi-cell
    public BufferedReader runCommand(String command)
        throws Throwable {
        
        if (isMultiCell())
            return runCommandWithCellid(command);
        
        else 
            return runCommandWithoutCellid(command);
    }
    
    // execute command without cell id
    // applicable for single-cell
    public BufferedReader runCommandWithoutCellid(String adminIp, String command)
        throws Throwable {
        Log.INFO("CLI::runCommand(" + command + ")"); 
        String [] args = new String [] {
            "/usr/bin/ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "admin@" + adminIp,
            command
        };
        
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);
        InputStream stdout = process.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout));
    }
    
    // execute command without cell id
    // applicable for single-cell
    public BufferedReader runCommandWithoutCellid(String command)
        throws Throwable {
       
        return runCommandWithoutCellid(adminVIP, command);
    }
   
    // required cell id to execute command 
    // applicable for multi-cell
    public BufferedReader runCommandWithCellid(String command, int cellid)
        throws Throwable {
        command = formatCommandWithCellid(command, cellid);
        Log.INFO("CLI::runCommand(" + command + ")");
        
        String [] args = new String [] {
            "/usr/bin/ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "admin@" + adminVIP,
            command
        };

        
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);
        InputStream stdout = process.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout));
    }
        
    // will use CURRENT_TEST_CELL_ID as a cellid
    // applicable for multi-cell
    public BufferedReader runCommandWithCellid(String command)
        throws Throwable {
            
        return runCommandWithCellid(command, getCellid());
    }
    
    protected String formatCommandWithCellid(String command, int cellid) {
        String [] lsCommand = null;
        
        try {
            lsCommand = tokenizeIt(command, " ");
        } catch (Exception e) {
            Log.ERROR("Error while tokenize the command <" + command + ">:" 
		+ Log.stackTrace(e));
        }
        
        String commandName = lsCommand[0];
        
        String option = "";
        for (int i=1; i<lsCommand.length; i++)
            option += lsCommand[i] + " ";
        
        String formattedCommand = commandName + CELL_ID_ARG + cellid + " " + option;
        
        return formattedCommand.trim();
    }
    
    // ip could be devxxx-admin, devxxx-cheat
    public BufferedReader runCommandAsRoot(String ip, String command)
        throws Throwable
    {
        Log.INFO("CLI::runCommand(" + command + ")");
        String [] args = new String [] {
            "/usr/bin/ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "root@" + ip,
            command
        };
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);
        InputStream stdout = process.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout));
    }
    
    // if isMultiCellCmd is true, will run multicell command, 
    // will use CURRENT_TEST_CELL_ID as a cellid;
    // otherwise run single-cell command and will return the 
    // line that will match with the compareStr
    public String getCommandStdout(boolean isMultiCellCmd, 
            String command, String compareStr) throws Throwable {
        
        String line = null, compareLine = null;
        BufferedReader bufferRead = null;
        
        if (isMultiCellCmd)
            bufferRead = runCommandWithCellid(command);
        else
            bufferRead = runCommandWithoutCellid(command);
        
        while ((line = bufferRead.readLine()) != null) {
            Log.DEBUG(command + " output: " + line);
            if (line.indexOf(compareStr) != -1) {
                compareLine = line;
		break;
	    }	
        }         
        bufferRead.close();
          
        return compareLine;
    }
    
    // required cell id to execute command 
    // applicable for multi-cell
    public String getCommandStdoutWithCellid(String command, 
            int cellid, String compareStr) throws Throwable {
        
        String line = null, compareLine = null;
        BufferedReader bufferRead = runCommandWithCellid(command, cellid);
        
        while ((line = bufferRead.readLine()) != null) {
            if (line.indexOf(compareStr) != -1) {
                compareLine = line;
		break;
	    }
        }         
        bufferRead.close();
          
        return compareLine;
    }
    
    // execute command without cell id
    // applicable for single-cell
    public String [] getCommandStdoutWithoutCellid(String command)
        throws Throwable {
        BufferedReader stdout = runCommandWithoutCellid(command);
        String stdoutLine = HCUtil.readLines(stdout);
        String [] lines = tokenizeIt(stdoutLine, "\n");
        
        return lines;
    }
    
    /**
     * Check to see if the error output string is in the error exclusion list.
     * Individual test classes should override this method to add exclussions
     * that are unique to the command being tested
     * 
     * @param errorString the string to check
     * @return boolean true if found, false otherwise
     */
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
        
	if ((errorString.indexOf("unknown argument") != -1) || 
		(errorString.indexOf("command not found") != -1) ||                    
		(errorString.indexOf("unknown option") != -1) ||
		(errorString.indexOf("illegal value") != -1) ||
		(errorString.indexOf("invalid value") != -1) ||
		(errorString.indexOf("invalid cell id specified") != -1) ||
		(errorString.indexOf("incorrect nodeid") != -1) ||
		(errorString.indexOf("invalid fruid") != -1) ||
		(errorString.indexOf("invalid command sequence") != -1) ||
		(errorString.indexOf("negative how long") != -1) ||
		(errorString.indexOf("negative interval") != -1) ||
		(errorString.indexOf("incorrect node id") != -1) ||
		(errorString.indexOf("at least one option is required.") != -1) ||
		(errorString.indexOf("specify a fru to enable or disable.") != -1) ||
                (errorString.indexOf("this is a mutli cell hive") != -1) ||
                (errorString.indexOf("must specify a cell id - this is a " +
                                     "multicell hive.") != -1)) {
	    return true;
	}
	return false;
    }
    
    
    protected boolean isCustomerWarning(String [] lsOutput) {
        if (lsOutput == null)
            return false;
        
        for (int i=0; i<lsOutput.length; i++) {
            if (lsOutput[i]. trim().startsWith(CUSTOMER_WARNING_STRING))
                return true;
        }
        
        return false;
    }
    
    protected String [] excudeCustomerWarning(String [] lsOutput) {
        if (!isCustomerWarning(lsOutput))
            return lsOutput;
        
        int totalWarningLine = 0;
        boolean beginWarningMsg = false;        
        for (int i=0; i<lsOutput.length; i++) {
            totalWarningLine++;
            if (lsOutput[i]. trim().startsWith(CUSTOMER_WARNING_STRING)) {
                if (beginWarningMsg) 
                    break;  // end of customer warning 
                else
                    beginWarningMsg = true;                    
            }
        }
        
        String [] lsExcludeWarningOutput = 
                new String[lsOutput.length - totalWarningLine];
        
        for (int i=0; i<lsExcludeWarningOutput.length; i++) 
            lsExcludeWarningOutput[i] = lsOutput[i+totalWarningLine];     
           
        return lsExcludeWarningOutput;
    }
    
    protected String [] excudeCustomerWarning(BufferedReader buf) {
        String [] lsExcludeWarningOutput = null;
        
        try {
            String outputStr = HCUtil.readLines(buf);
            lsExcludeWarningOutput = tokenizeIt(outputStr, "\n");
            lsExcludeWarningOutput = excudeCustomerWarning(lsExcludeWarningOutput);
        } catch (Exception e) {
            Log.WARN("Unable to parse command output: " + e);
        }
        
        return lsExcludeWarningOutput;
    }
    
    // to verify command output    
    public boolean verifyCommandStdout(boolean isMultiCellCmd, 
            String command, String [] lsExpOutput) {        
    
        boolean isCorrectStdout = false;
            
        try {
            String line = null;
            BufferedReader output = null;
                      
            if (isMultiCellCmd)
                output = runCommandWithCellid(command);
            else
                output = runCommandWithoutCellid(command);
            String outputStr = HCUtil.readLines(output);
            output.close();
	    if (outputStr == null || outputStr.trim().length() == 0) {
		Log.ERROR("No output returned from command.");
		return false;
	    }
            String [] lsActOutput = tokenizeIt(outputStr, "\n");
           
	    if (lsActOutput.length == 0) {
		Log.ERROR("Incomplete output of <" + outputStr 
		    + "> returned from command execution.  Missing \\n.");
		return false;
	    }
            
            lsActOutput = excudeCustomerWarning(lsActOutput);
            int start = 0;

            // for multicell
            if (isMultiCell()) {
                if ((lsActOutput[start].equals(MULTICELL_USAGE_MESSAGE[0])) ||
                        (lsActOutput[start].equals(MULTICELL_EXPANSION_MESSAGE[0])))
                    return true;
            }
            
	    // all error messages are followed by a usage message
            if (isInErrorExclusionList(lsActOutput[start])) {
                isCorrectStdout=true;
                Log.DEBUG(lsActOutput[start]);
                start++; 
            }
            	   
	    if (lsExpOutput != null) {
		if ((lsActOutput.length-start) != lsExpOutput.length) {
		    String actOutput = "";
		    for (int i=start; i<lsActOutput.length; i++) {
			if (!lsActOutput[i].trim().equals(""))
			    actOutput += lsActOutput[i] + "\n";
		    }
		    Log.WARN("Unexpected output from command <" + 
			    command + ">: \n" + actOutput + "\n");

		    String expOutput = "";
		    for (int i=0; i<lsExpOutput.length; i++) {
			if (!lsExpOutput[i].trim().equals(""))
			    expOutput += lsExpOutput[i] + "\n";
		    }
		    Log.INFO("Expected output: \n" + expOutput);

		    return false;                    
		}

		for (int lineNo=0; lineNo<lsExpOutput.length; lineNo++) { 
		    line = lsActOutput[lineNo+start].trim();
		    Log.DEBUG(command + " output : " + line);              
		    if (!line.trim().equals(lsExpOutput[lineNo].trim())) {
			Log.WARN("<" + command + "> command: " + "\n# Actual output: \n" + 
				line + "\n#Expected output: \n" + lsExpOutput[lineNo] + "");
			isCorrectStdout = false;  
		    }
		    else {
			Log.DEBUG("Correct output: " + line);
			if ((lineNo == 0) || (isCorrectStdout)) 
			    isCorrectStdout = true;   
		    }             
		}
	    } else {
		if (isCorrectStdout == false)
		    Log.WARN("<" + command + "> command: " + "\n# Actual output: \n" + 
				line);
	    }
            if (!isCorrectStdout)
                Log.ERROR("Unexpected output from <" + command + "> command");
            else
                Log.INFO("Output of <" + command + "> command is displayed properly");  
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }  
                
        return isCorrectStdout;
    }
        
    // to verify command output    
    public boolean verifyCommandStdout(String command, String [] lsExpOutput) {
        
        boolean isCorrectStdout = false;
            
        try {
            if (isMultiCell())
                isCorrectStdout = verifyCommandStdout(true, command, lsExpOutput);
            else
                isCorrectStdout = verifyCommandStdout(false, command, lsExpOutput);
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }  
                
        return isCorrectStdout;
    }
    
        
    protected String getDateTime() {
        // to get the current date and time. Used in license value
        Calendar cal = new GregorianCalendar();
        
        String year = new Integer(cal.get(Calendar.YEAR)).toString(); // 2002
        String month = new Integer(cal.get(Calendar.MONTH) + 1).toString(); // 0=Jan, 1=Feb, ...
        String day = new Integer(cal.get(Calendar.DAY_OF_MONTH)).toString(); // 1...

        String hour = new Integer(cal.get(Calendar.HOUR_OF_DAY)).toString(); // 0..23
        String min = new Integer(cal.get(Calendar.MINUTE)).toString(); // 0..59
        String sec = new Integer(cal.get(Calendar.SECOND)).toString(); // 0..59    
        
        return year + month + day + "_" + hour + min + sec;
    }
    
    protected long getSleepTime() {
        // default is 15 minutes
        long sleepTime = 900;
        
        String sleepTimeStr = getAdminCliProperties().getProperty("sleep.time").trim();
        
        if ((sleepTimeStr != null) && (!sleepTimeStr.equals(""))) 
            sleepTime = Long.parseLong(sleepTimeStr) * 60;  
        
        return sleepTime;
    }
    
    // correct format: cell <id>:
    protected boolean checkCellidStdout(String line) {
        Pattern regexp = Pattern.compile(CELL_ID_STDOUT_PATTERN);
        Matcher matcher = regexp.matcher(line);
                    
        // verify the output pattern
        if (!matcher.matches()) {
            Log.ERROR("Unexcpected output format: " + line);  
            return false;
        }
        else {
            String cellidStr = matcher.group(1);
            
            try {
                Integer cellid = new Integer(cellidStr);  
                
                if (!listOfCellId.contains(cellid)) {
                    Log.ERROR("Invalid cellid: " + cellid.intValue());
                    return false;
                }
               
            } catch (Exception e) {
                Log.ERROR("Error while parsing stdout <" + line + 
                        "> to get cellid: " 
		    + Log.stackTrace(e));
                return false;
            }
             
            return true;
        }
    }
    
    // Line: cell <id>:
    // will return id
    protected int getCellidFromStdout(String line) {
        int cellidInt = -1;
        
        Pattern regexp = Pattern.compile(CELL_ID_STDOUT_PATTERN);
        Matcher matcher = regexp.matcher(line);
                    
        // verify the output pattern
        if (!matcher.matches()) {
            Log.ERROR("Unexcpected output format: " + line);  
            return -1;
        }
        else {
            String cellidStr = matcher.group(1);
            
            try {
                Integer cellid = new Integer(cellidStr);  
                cellidInt = cellid.intValue();
                
                if (!listOfCellId.contains(cellid)) {
                    Log.ERROR("Invalid cellid: " + cellid.intValue());
                    return -1;
                }
               
            } catch (Exception e) {
                Log.ERROR("Error while parsing stdout <" + line + 
                        "> to get cellid: " + Log.stackTrace(e));
                return -1;
            }
             
            return cellidInt;
        }
    }
    
    protected boolean isPing(String spIP, String hostip) {
        try {
            String command = "ping " + hostip;
            BufferedReader output = runCommandAsRoot(spIP, command);
            String line = null;
            
            String expectedMsg = hostip + " is alive";
            String actualMsg = "";
                
            while ((line = output.readLine()) != null) {                
                actualMsg += line;             
            }
            output.close();
            
            if (actualMsg.trim().equals(expectedMsg)) {
                Log.INFO(actualMsg);
                return true;
            }
            else 
                Log.ERROR("Actual: " + actualMsg + ", Expected: " + expectedMsg);
            
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        }
        
        return false;
    }
    
    // applicable for multicell setup
    // will return a HashMap containg cellid is key and the 
    // stdout of that cell is the value for each cell
    protected HashMap formatMulticellStdout(BufferedReader stdout) {
        HashMap stdoutMap = new HashMap();
        
        try {
            Pattern regexp = Pattern.compile(CELL_ID_STDOUT_PATTERN);
            int key = -1;
            String value = "";
            String line = null;
         
            while ((line = stdout.readLine()) != null) {
                Matcher matcher = regexp.matcher(line);
                    
                // verify the output pattern
                if (matcher.matches()) {
                
                    if ((key != -1) && (!value.trim().equals("")))
                        stdoutMap.put(new Integer(key), value);
                
                    // set key/value for next cell
                    key = getCellidFromStdout(line);
                    value = "";
                }
                else {
                    if (!line.trim().equals(""))
                        value += line + "\n";  
                }
            }
        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(t));
        } 
        
        return stdoutMap;
    }
    
    protected boolean validateIp(String ipName, String ipValue, 
            boolean isOptional) {
        if ((ipValue == null) || (ipValue.equalsIgnoreCase("null"))) {
            Log.ERROR("Invalid " + ipName + ": null");
            return false;
        }
        
        ipValue = ipValue.trim();
        
        if (ipValue.equals("")) {
            if (isOptional) {
                Log.INFO(ipName + ": " + ipValue);
                return true;
            }
            else {
                Log.ERROR("Invalid " + ipName + ": " + ipValue);
                return false;
            }   
        }
        
        Pattern regexp = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
        Matcher matcher = regexp.matcher(ipValue);
        
        if (matcher.matches()) {
            Log.INFO(ipName + ": " + ipValue);        
            return true;
        }
        else {
            Log.ERROR("Invalid " + ipName + ": " + ipValue);
            return false;
        }
    }
    
    public String [] getAllInvalidIp() {
        // will use admin ip as valid ip 
        Integer cellid = (Integer) getAllCellid().get(0);
        String validIp = (String) getAllAdminIp().get(cellid);
        
        String [] lsInvalidIp = {"10.0.235", "345.0.0.1", "10.1.0.0.12", 
                "." + validIp, validIp + ".", "ip", "10", " ", "' '", "\" \""};
        
        return lsInvalidIp;
    }
    
    public String [] getAllInvalidHost() {
        // will use admin ip as valid ip 
        Integer cellid = (Integer) getAllCellid().get(0);
        String validIp = (String) getAllAdminIp().get(cellid);
        
        String [] lsInvalidIp = {"10.0.235", "345.0.0.1", "10.1.0.0.12", 
                "." + validIp, validIp + ".", "ip@", "10%", "sun. com", "-sun.com",
                "sun.com-", ".sun.com", "sun.com.", "!@#$%^&*:;", " ", 
                "' '", "\" \""};        
            
        return lsInvalidIp;
    }
    
    public String [] getAllInvalidInteger() {
        
        String [] lsInvalidNumber = {"-25", "25.0", "3.422", "-2.434", "!@#$%^&*:;", 
            "3.1e3", "-1.3e-21", "string", " ", "' '", "\" \""};
        
        return lsInvalidNumber;
    }
    
    protected ArrayList readSysstat(int cellid) throws Throwable {
        setCellid(cellid);
        
        ArrayList result = new ArrayList();
        String line = null;
        BufferedReader br = runCommand(SYSSTAT_COMMAND);
        while ((line = br.readLine()) != null) {
            result.add(line);
        }
        br.close();
        return result;
    }
    
    /**
     * Perform a lookup of server returning the associated ip address.
     * DNS must be enabled for this call to succeed.
     *
     * @param server the hostname to convert to an ip address.
     * @return String the ipAddress for the speceified server.  null if
     * the lookup of the server failed and the ip address can not be determined.
     */
    public static String getIpAddress(String server)
    {
	if (server == null)
	    return null;
	try
	{
	    InetAddress addr = InetAddress.getByName(server);
	    return addr.getHostAddress();
	}
	catch (UnknownHostException uhe) {
	    return null;
	}
    }
    
    /**
     * Perform a lookup of ip Address returning the associated hostname.
     * DNS must be enabled for this call to succeed.
     *
     * @param ipAddress the ipAddress to convert to a hostname
     * @return String the hostname for the speceified ip Address.  null if
     * the lookup of the ip Address failed and the hostname can not be determined.
     */
    public static String getHostname(String ipAddress)
    {
	if (ipAddress == null)
	    return null;
	try
	{
	    InetAddress addr = InetAddress.getByName(ipAddress);
	    return addr.getHostName();
	}
	catch (UnknownHostException uhe) {
	    return null;
	}
    }
    
    protected boolean isIp(String ipAddress) {
        String NUM_255 = "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
        Pattern regexpIP = Pattern.compile( "^(?:" + NUM_255 + "\\.){3}" + 
                 NUM_255 + "$");
        Matcher matcher = regexpIP.matcher(ipAddress);
                    
        if (matcher.matches()) 
            return true;
        
        return false;
    }
    
    protected boolean verifyHadb(int cellid) {
        Log.INFO("*** Verify hadb state");
        int sleepTime = 3600; // wait max 1 hour for hadb to come up
        int sleepInterval = 300;
        int totalSleep = 0;
        String stateSought = "FaultTolerant";
        
        try {
            while (totalSleep < sleepTime) { 

                if (totalSleep > 0)
                    HCUtil.doSleep(sleepInterval, 
                                   "Begin sleep " + sleepInterval);
                totalSleep += sleepInterval;                

                BufferedReader output = runCommandWithCellid("hadb -F status", cellid);
                String lines = HCUtil.readLines(output);
                output.close();
                String [] lsLine = tokenizeIt(lines, "\n");   
                lsLine = excudeCustomerWarning(lsLine);

                for (int i=0; i<lsLine.length; i++) {
                    String line = lsLine[i];
                    if (line.trim().equals("")) 
                        continue;
                    
                    Log.INFO("hadb state: " + line);   
                    if (line.indexOf(stateSought) != -1) 
                        return true;                
                }
            }
            Log.ERROR("Timed out waiting for " + stateSought + " hadb state");
        } catch (Throwable t) {
            Log.ERROR("Error while verifying hadb state using hadb command:" + 
                    Log.stackTrace(t));
        }
        
        return false;
    }
   
    // save current log file line number
    protected void setCurrentInternalLogFileNum() {
        logLineNumBeforeTest = getCurrentLogFileLine(
                        getSpVIP(), MESSAGE_LOG_FILE);      
    }
    
    // verify internal log file
    protected void verifyAuditInternalLog(String command, String msg, 
            ArrayList lsParamValueExp, boolean isNewSessionId) {
       
        if (!doAuditLogVerification) {
            Log.INFO("Skipping audit log verification");
            return;
        }
        
        int noOfFailure = 0;
        
        if (admin_audit_msg_prop == null)
            admin_audit_msg_prop = new TestProperties(
                    TestProperties.CLI_AUDIT_MESSAGE_PROPERTY_FILE);
        String msgExp = admin_audit_msg_prop.getProperty(msg);
        
        if (seesionIdList.isEmpty())
            isNewSessionId = true;
        
        // verify server log file
        if(!isMessageInLogFile(getSpVIP(), MESSAGE_LOG_FILE, logLineNumBeforeTest, 
                msgExp, lsParamValueExp, isNewSessionId))
            noOfFailure ++;
        
        if (noOfFailure != 0) {
            if (lsOfFailedAuditTest == null)
                lsOfFailedAuditTest = new ArrayList();
            lsOfFailedAuditTest.add("Command:" + command + "; Message format:" + msgExp);
        }
    }
    
    protected long getCurrentLogFileLine(String hostIp, String filename) {
        if (!doAuditLogVerification) return 0;
        
        long lineNum = 0;
        try {
            BufferedReader buf = runCommandAsRoot(hostIp, "wc -l " + filename);
            String line = buf.readLine();
            buf.close();
            String [] tokenizeStr = tokenizeIt(line, " ");
            lineNum = Long.parseLong(tokenizeStr[0]);
            Log.INFO(filename + ":Line number:" + lineNum);
        } catch (Throwable t) {
            Log.WARN("Unable to determine total no of lines of file " +
                    filename + ": " + Log.stackTrace(t));
        }
        return lineNum;
    }
    
    protected boolean isMessageInLogFile(String hostIp, String filename,
                  long prevLineNum, String msgExp, ArrayList lsParamValueExp,
                  boolean isNewSessionId) {
        int noOfFailure = 0;
        
        String matchStr = "";
        try {
            matchStr = tokenizeIt(msgExp, ":")[0];
        } catch (Throwable t) {
            Log.WARN("Unable to tokenize string <" + msgExp + ">: " +
                    Log.stackTrace(t));
            return false;            
        }
           
        try {
            boolean isLogRotate = false;
            long curLineNum = getCurrentLogFileLine(hostIp, filename);
            if (curLineNum < prevLineNum) {
                Log.INFO("The logs have rotated during the test");
                isLogRotate = true;
            }
            
            String grepMsg = null;
            int sleepInterval = 10;
            int sleepTime = 60;
            int totalSleep = 0;
            while (totalSleep < sleepTime) { 
                if (totalSleep > 0) 
                    HCUtil.doSleep(sleepInterval, "wait to message come up in log file");
                totalSleep += sleepInterval;
                
                BufferedReader buf = runCommandAsRoot(hostIp, 
                        "grep -n -i " + externalStr + " " + filename + 
                        " | grep \"" + matchStr + 
                        "\" | tail -1");
                grepMsg = buf.readLine();
                buf.close();
                if ((grepMsg != null) && (!grepMsg.trim().equals("")))
                    break;
            }
            
            if ((isLogRotate) && (filename.equals(MESSAGE_LOG_FILE))){
                if ((grepMsg == null) || (grepMsg.trim().equals(""))) {
                    BufferedReader buf = runCommandAsRoot(hostIp, 
                            "grep -n -i " + externalStr + " " + MESSAGE_LOG_FILE_0 + 
                            " | grep \"" + matchStr + 
                            "\" | tail -1");
                    grepMsg = buf.readLine(); 
                    buf.close();
                }
                else
                   prevLineNum = 0; 
            }
            
            if ((grepMsg == null) || (grepMsg.trim().equals(""))) {
                Log.WARN("Unable to find string " + matchStr + " in file " +
                        filename);
                return false;            
            }
            else {
                long foundLineNum = Long.parseLong(tokenizeIt(grepMsg, ":")[0]);
                if (foundLineNum < prevLineNum) {
                    Log.WARN("Audit failed: last audit message was " +
                            grepMsg);
                    return false;
                }
                else {
                    Log.INFO("Found message: " + grepMsg);
                    
                    ArrayList lsParamNameExp = getAllParamNameFromMsg(
                            msgExp.toLowerCase());
                    HashMap paramNameValueMapAct = getAllParamNameValueFromMsg(
                            grepMsg.toLowerCase());
                    
                    // verify whether session id is unique or not
                    String sessionId = (String)paramNameValueMapAct.get(seesionIdStr);
                    if (isNewSessionId) {
                        if (seesionIdList.contains(sessionId)) {
                            noOfFailure ++;            
                            Log.WARN("Audit failed: Session id <" + sessionId + 
                                    "> is not unique");
                        }                    
                        else
                            seesionIdList.add(sessionId);
                    }
                    else {
                        if (!seesionIdList.contains(sessionId)) {
                            noOfFailure ++;            
                            Log.WARN("Audit failed: Invalid session id: " + sessionId);
                        }  
                    }
                    
                    // verify command parameters
                    if (lsParamValueExp != null) {
                        for (int i=0; i<lsParamNameExp.size(); i++) {
                            String paramName = (String) lsParamNameExp.get(i);
                            String paramValueExp = 
                                    ((String)lsParamValueExp.get(i)).toLowerCase();
                            String paramValueAct = 
                                    (String)paramNameValueMapAct.get(paramName);
                            if (paramValueAct == null) {
                                noOfFailure ++;            
                                Log.WARN("Audit failed: Missing param name <" + paramName +
                                        "> from message");
                                Log.WARN("Expected message format <" + msgExp +
                                        ">");
                                continue;
                            }
                            if (!paramValueExp.trim().equals(paramValueAct.trim())) {
                                noOfFailure ++;            
                                Log.WARN("Audit failed: Expected " + paramName +
                                        ": " + paramValueExp + ", found: " + 
                                        paramValueAct);
                            }
                        }                        
                    }                        
                }            
            }
        } catch (Throwable t) {
            noOfFailure ++;            
            Log.WARN("Audit failed: Unable to grep message <" + msgExp + "> from  file " +
                    filename + ": " + Log.stackTrace(t));
        }
        
        if (noOfFailure != 0)
            return false;
        return true;
    }
    
    private ArrayList getAllParamNameFromMsg(String msg) {
        ArrayList paramList = new ArrayList();         
        String [] lsTokenizeIt = getTokeinizedMsg(msg);
        if (lsTokenizeIt == null) 
            return null;
        
        String paramName = "";
        for (int i=0; i<lsTokenizeIt.length; i++) {
            String str = lsTokenizeIt[i];
            if (str.endsWith(":")) {
                paramName += str.substring(0, str.length()-1);
                if (!paramName.trim().equals(seesionIdStr))
                    paramList.add(paramName.trim());
                i++;
                paramName = "";
            }  
            else {
                paramName += str + " ";
            }
        }
        
        return paramList;
    }
    
    private HashMap getAllParamNameValueFromMsg(String msg) {
        HashMap paramNameValueMap = new HashMap();         
        String [] lsTokenizeIt = getTokeinizedMsg(msg);
        if (lsTokenizeIt == null) 
            return null;
         
        String paramName = "", paramValue = "";
        for (int i=0; i<lsTokenizeIt.length; i++) {
            String str = lsTokenizeIt[i];
            if (str.endsWith(":")) {
                paramName += str.substring(0, str.length()-1);
                paramValue = lsTokenizeIt[++i];
                
                if (paramValue.endsWith(".")) {
                    paramValue = paramValue.substring(0, paramValue.length()-1);
                }
                
                if ((paramValue != null) || (paramValue.trim().equals(""))) 
                    paramNameValueMap.put(paramName.trim(), paramValue.trim());
                
                paramName = "";
            }  
            else {
                paramName += str + " ";
            }
        }
        
        return paramNameValueMap;
    }
    
    private String [] getTokeinizedMsg(String msg) {
        int index = msg.indexOf(seesionIdStr);
        msg = msg.substring(index);
        
        String [] lsTokenizeMsg = null;
        try {
            lsTokenizeMsg = tokenizeIt(msg, " ");
        } catch (Throwable t) {
            Log.WARN("Unable to tokenize string <" + msg + ">: " +
            Log.stackTrace(t));
            return null;
        }
        
        return lsTokenizeMsg;
    }
    
}
