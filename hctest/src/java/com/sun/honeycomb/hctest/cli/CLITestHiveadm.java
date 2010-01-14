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
import java.util.HashMap;
import java.util.Random;

/**
 *
 * @author jk142663
 */
public class CLITestHiveadm extends HoneycombCLISuite {
    
    private static final String HIVEADM_NEW_CHAR = HIDDEN_PROMPT_FORCE_OPTION + "-n";
    private static final String HIVEADM_REMOVE_CHAR = HIDDEN_PROMPT_FORCE_OPTION + "-r";
    private static final String HIVEADM_STATUS_CHAR = "-s";
    private static final String HIVEADM_ADMIN_IP_CHAR = "-a";
    private static final String HIVEADM_DATA_IP_CHAR = "-d";
    private static final String HIVEADM_CELLID_CHAR = "-c";
   
    /** total no of loops the test will run */
    int noOfLoop = 1; 
    /** list of all new admin & data ips, seperated by "*" */
    ArrayList lsAdminDataIp = null;
    
    /** Creates a new instance of CLITestHiveadm */
    public CLITestHiveadm() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCLI Test: hivadm command\n");
        sb.append("\nnewcells is a required argument \n" +
          "Syntax: -ctx newcells=adminip*dataip(for cell 1)," +
          "adminip*dataip(for cell 2)\n" +
          "For example: -ctx newcells=xxx.xxx.xxx.xxx*" +
          "xxx.xxx.xxx.xxx,xxx.xxx.xxx.xxx*xxx.xxx.xxx.xxx\n");
        sb.append("\nloop is the number of times to run test, 1 by default" +
          "Syntax: -ctx loop=<loop_num>)\n" +
          "For example: -ctx loop=5");
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();
    }
    
    public void testHiveadmAddRemoveCell() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hiveadm_add_remove_cell");

        self.addTag(Tag.NORUN); // remove me!
        self.addTag(HoneycombTag.MULTICELL);
        
        // self.addTag(Tag.REGRESSION); // add me!
        if (self.excludeCase()) return;
        
        // verify prerequisite before running test
        verifyPreReqs();
        
        // add all new cells
        if (!addAllNewCell()) {
            Log.ERROR("Unable add all the cells in the hive ");
            self.testFailed();
        }
        
        // each loop will remove a cell followed by adding that cell randomly
        for (int i=0; i<noOfLoop; i++) {
            // get a cell id
            int cellid = getRandomCellid();
            if (cellid == -1) {
                self.testFailed("Unable to get a valid cell id to remove a" +
                  " cell from hive");
                return;
            }
            
            // get admin & data ips
            String adminIp = (String) getAllAdminIp().get(new Integer(cellid));
            String dataIp = (String) getAllDataIp().get(new Integer(cellid));
            
            // remove a cell
            setCurrentInternalLogFileNum();
            if (!removeCell(cellid)) {
                Log.ERROR("Unable to remove cell with id " + cellid +
                  " to remove a cell from hive");
                self.testFailed("removeCell failed cellid = " + cellid);
            }
            
            // verify audit log after removing a cell
            verifyAuditHiveadm(HIVEADM_REMOVE_CHAR, String.valueOf(cellid), null,
                "info.adm.removeCell");  
            
            // add a cell
            setCurrentInternalLogFileNum();
            int newCellid = addCell(adminIp, dataIp);
            if (newCellid == -1) {
                Log.ERROR("Unable to add cell with admin ip " + adminIp +
                  " and data ip " + dataIp + " to the hive");
                self.testFailed("addCell failed " + cellid);
            }
            else {
                if (!verifyAddCell(newCellid, adminIp, dataIp)) {
                    Log.ERROR("Failed to verify cell " + cellid +
                      "has been correctly added");
                    self.testFailed("verifyAddCell failed " + cellid);
                }
            }  
            
            // verify audit log after adding a new cell
            verifyAuditHiveadm(HIVEADM_NEW_CHAR, adminIp, dataIp,
                "info.adm.addCell");            
        }        
        self.testPassed();  
    }
    
    public void testHiveadmNegativeTest() throws HoneycombTestException {
        
        TestCase self = createTestCase("CLI_hiveadm_negative_test");
            
        self.addTag(Tag.NORUN); // remove me!
        self.addTag(Tag.NEGATIVE);
        self.addTag(HoneycombTag.MULTICELL);
        // self.addTag(Tag.REGRESSION); // add me!
        
        if (self.excludeCase()) return;
        
        String [] lsTestCaseScenario = {
            "add an already added cell", 
            "add non-existent admin ip, but existent data ip",
            "add non-existent data ip, but existent admin ip",
            "add non-existent admin & data ip",
            "same existent admin ip for both admin & data ip",
            "same existent data ip for both admin & data ip",
            "execute celladm on non-master cell"
        };
        
        String invalid_adminIp = "101.102.103.104";
        String invalid_dataIp = "201.202.203.204";
        
        int noOfFailure = 0;
            
        for (int i = 0; i<lsTestCaseScenario.length; i++) {
            Log.SUM("hiveadm - negative test: " + lsTestCaseScenario[i]);
            
            // get an existing cell id
            int cellid = getRandomCellid();
            if (cellid == -1) {
                self.testFailed("Unable to get a valid cell id to perform " +
                  "negative test");
                return;
            }
            
            // get admin & data ips
            String valid_adminIp =
              (String) getAllAdminIp().get(new Integer(cellid));
            String valid_dataIp =
              (String) getAllDataIp().get(new Integer(cellid));
            
            if (i == 0) {                
                int newCellid = addCell(valid_adminIp, valid_dataIp);
                if (newCellid != -1) {
                    Log.ERROR("cell id <" + newCellid + "> is returned " +
                      "while adding an already added cell to hive");
                    noOfFailure++;
                }
                else {
                    if (!verifyAddCell(cellid, valid_adminIp, valid_dataIp))
                        noOfFailure ++;
                }
                
                if (!removeCell(cellid)) {
                    Log.ERROR("Unable to remove cell with cellid <" + 
                      cellid + ">, abort the test");
                    self.testFailed();
                    return;
                }
            }
            else if (i == 1) {
                int newCellid = addCell(invalid_adminIp, valid_dataIp);
                if (newCellid != -1) {
                    Log.ERROR("cell id <" + newCellid + "> is returned " +
                      "while adding a cell with non-existent admin " +
                      "ip, but existent data ip");
                    noOfFailure++;
                }
            }
            else if (i == 2) {
                int newCellid = addCell(valid_adminIp, invalid_dataIp);
                if (newCellid != -1) {
                    Log.ERROR("cell id <" + newCellid + "> is returned " +
                      "while adding a cell with non-existent data " +
                      "ip, but existent admin ip");
                    noOfFailure++;
                }
            }
            else if (i == 3) {
                int newCellid = addCell(invalid_adminIp, invalid_dataIp);
                if (newCellid != -1) {
                    Log.ERROR("cell id <" + newCellid + "> is returned " +
                      "while adding a cell with non-existent admin & data ip");
                    noOfFailure++;
                }
            }
            else if (i == 4) {
                int newCellid = addCell(valid_adminIp, valid_adminIp);
                if (newCellid != -1) {
                    Log.ERROR("cell id <" + newCellid + "> is returned " +
                      "while adding a cell with same existent " +
                      "admin ip for both admin & data ip");
                    noOfFailure++;
                }
            }
            else if (i == 5) {
                int newCellid = addCell(valid_dataIp, valid_dataIp);
                if (newCellid != -1) {
                    Log.ERROR("cell id <" + newCellid + "> is returned " +
                      "while adding a cell with same existent data " +
                      "ip for both admin & data ip");
                    noOfFailure++;
                }
            }
            else if (i == 6) {
                verifyCelladm(valid_adminIp);
            }
        }
        if (noOfFailure == 0)
            self.testPassed();  
        else
            self.testFailed("failed with " + noOfFailure + "failures");
    }
    
    private void verifyPreReqs() throws HoneycombTestException {
        Log.INFO("Verify the syntax of \"newcells\" argument");
        String newCellsArg = 
          getProperty(HoneycombTestConstants.PROPERTY_MULTICELl_NEW_CELLS);
        lsAdminDataIp = new ArrayList();
        
        try {            
            if (newCellsArg.indexOf(",") == -1) {  // in case of one cell            
                lsAdminDataIp.add(newCellsArg);
            }
            else { // more than one cell
                String [] lsCellAdminDataIp = tokenizeIt(newCellsArg, ",");
                
                for (int i=0; i<lsCellAdminDataIp.length; i++) {
                    lsAdminDataIp.add(lsCellAdminDataIp[i]);
                }
            }
        }
        catch (Exception e) {
            throw new HoneycombTestException("Syntax error in " +
              "\"newcells\" argument, verify help" + e);
        }
        
        Log.INFO("Verify all admin/data ips are pingable " +
          "and sysstat for each new cell");
        
        for (int i=0; i<lsAdminDataIp.size(); i++) {
            String [] tmp = null;
            
            Log.INFO("STEPH cluster = " + (String) lsAdminDataIp.get(i));
            try {
                tmp = tokenizeIt((String) lsAdminDataIp.get(i), "*");
            }
            catch (Exception e) {
                throw new HoneycombTestException("Syntax error in " +
                  "\"newcells\" argument, verify help" + e);
            }
            
            String adminIp = tmp[0];
            String dataIp = tmp[1];
            
            boolean isAdminIpUp = isPing(getSpVIP(), adminIp);
            boolean isDataIpUp = isPing(getSpVIP(), dataIp);
            
            if ((!isAdminIpUp) || (!isDataIpUp))
                throw new HoneycombTestException("Admin(" + adminIp + 
                  ")/Data(" + dataIp + ") is not up.");
            
            // verify sysstat
            // STEPH this is a bit too strong...
//             if (!verifySysStat(adminIp))
//                 throw new HoneycombTestException("Unexpected sysstat " +
//                  "of new cell with admin ip: " + adminIp + ".");
        }
        
        // determine the no of loops to run the test
        String loopArg = null;
        try {
            loopArg = getProperty(HoneycombTestConstants.PROPERTY_MULTICELl_LOOP_NO); 
            if (null != loopArg)
                noOfLoop = Integer.parseInt(loopArg);
            else
                noOfLoop = 1;      
        } catch (NumberFormatException nfe) {
            Log.WARN("Invalid loop number " + loopArg + " - verify help, " +
              "default to 1)");
            noOfLoop = 1;
        }
    }
    
    // return cell id if successful, otherwise return -1
    private int addCell(String adminIp, String dataIp) 
        throws HoneycombTestException {

        Log.SUM("Add a cell - Admin Ip:" + adminIp + "; Data Ip:" + dataIp);
        
        int cellid = -1;                
        
        String command = HoneycombCLISuite.HIVEADM_COMMAND + 
          " " + HIVEADM_NEW_CHAR + " " + HIVEADM_ADMIN_IP_CHAR + 
          " " + adminIp + " " + HIVEADM_DATA_IP_CHAR + " " + dataIp;
            
        BufferedReader output = null;
            
        try {                                    
            output = runCommandWithoutCellid(command);
            
            String line = null;         
            while ((line = output.readLine()) != null) {
                Log.INFO(line);
        
                // get the cellid of newly added cell
                if (line.indexOf("New cell was added successfully, cellid =") != -1) { 
                    try {                                    
                        String [] lsTmp = tokenizeIt(line, "=");    
                        cellid = Integer.parseInt(lsTmp[1].trim());                
                    } catch (Throwable t) {
                        throw new HoneycombTestException("Error while parsing line <" +
                          line + "> to get the cellid: "+ t.toString());
                    } 
                }
            }
            output.close();
        } catch (Throwable t) {
            throw new HoneycombTestException("Error while executing command <" +
              command + ">: "+ t.toString());
        } 
        
        setTotalNoOfCell(-1);
        setAllCellid(); // set all cell id values
        
        initializeAllIp();
        setAllIp(); // set all admin/data ips
        
        return cellid;       
    }
    
    // NOT COMPLETE - verify silo_info.xml file   
    private boolean verifyAddCell(int cellid, String adminIp, String dataIp) {

        int noOfFailure = 0;

        // verify the hiveadm status to make sure that the 
        // newly added cell is listed proeprly and unique
        if (!verifyHiveadmStatus(cellid, true)) {
            Log.ERROR("cellid " + cellid + "does not appear in the status");
            noOfFailure++;
        }
        
        // verify silo_config.xml file

        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    private boolean removeCell(int cellid) throws HoneycombTestException{
        Log.SUM("Remove a cell with cell id: " + cellid);
        
        int noOfFailure = 0;
        String command = HoneycombCLISuite.HIVEADM_COMMAND + 
          " " + HIVEADM_REMOVE_CHAR + " " + HIVEADM_CELLID_CHAR + 
          " " + cellid;
            
        BufferedReader output = null;
            
        try {                                    
            output = runCommandWithoutCellid(command);
        
            String line = null;        
            while ((line = output.readLine()) != null) {                
                Log.INFO(line);
            }
            output.close();
        } catch (Throwable t) {
            throw new HoneycombTestException("Error while executing command <" +
              command + ">: "+ t.toString());
        } 
            
        setTotalNoOfCell(-1);
        setAllCellid(); // set all cell id values
        
        initializeAllIp();
        setAllIp(); // set all admin/data ips
            
        // verify the hiveadm status to make sure that the 
        // removed cell is not listed 
        if (!verifyHiveadmStatus(cellid, false)) {
            Log.ERROR("cellid " + cellid + " is still listed in the status");
            noOfFailure++;
        }
        
        // verify silo_info.xml file - NO IMPLEMENTATION YET
       
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    private boolean verifyHiveadmStatus(int newCellid, boolean isPresent) {

        String command = command = HoneycombCLISuite.HIVEADM_COMMAND + 
          " " + HIVEADM_STATUS_CHAR;
        Log.SUM("Execute command <" + command + 
          "> to determine single or multi-cell and will get all cellid(s)");

        BufferedReader output = null;
        try {
            output = runCommandWithoutCellid(command);
        } catch (Throwable t) {
            Log.ERROR("Error while executing command <" +
              command + ">: "+ t.toString());
            return false;
        } 
            
        try {
            boolean isFound = false;
            boolean isUniqueId = true;
            
            String outputStr = HCUtil.readLines(output);
            output.close();
            String [] lsLine = tokenizeIt(outputStr, "\n");
            String [] line = null;
            
            ArrayList lsCellId = new ArrayList();
            for (int i=1; i<lsLine.length; i++) {
                line = tokenizeIt(lsLine[i], " ");
                
                if (line[2].indexOf(":") != -1) {
                    String [] lsTmp = tokenizeIt(line[2], ":");
                    line[2] = lsTmp[0];
                }
                
                Integer cellid = Integer.valueOf(line[2]);
                int cellidInt = cellid.intValue();
                
                if (cellidInt == newCellid) {
                    Log.INFO("hiveadm status is displayed the cell: " +
                      lsLine[i]);
                    isFound = true;
                }
                
                if (lsCellId.contains(cellid)) {
                    Log.ERROR("Cell id <" + cellidInt + "> is not unique.");
                    isUniqueId = false;
                } else {
                    lsCellId.add(cellid);
                }
            } 
            
            if (isPresent) {
                if (!isFound) {
                    Log.ERROR("Unable to find the newly added cell info with id <" + 
                      newCellid + "> from hiveadm status output");
                    return false;
                }
                else if (!isUniqueId)
                    return false;
                else
                    return true;                    
            }
            else {
                if (isFound) {
                    Log.ERROR("Removed cell with id <" + newCellid + "> should not display " +
                      "in the hiveadm status");
                    return false;
                }
                else 
                    return true;
            }
        } catch(Throwable e) {
            Log.ERROR("Error while parsing hiveadm status output: " + e);
        }
        return false;
    }
    
    private boolean addAllNewCell() throws HoneycombTestException {
        int noOfFailure = 0;
        
        for (int i= 0; i<lsAdminDataIp.size(); i++) {
            String [] tmp = null;
            
            try {
                tmp = tokenizeIt((String) lsAdminDataIp.get(i), "*");
            }
            catch (Exception e) {
                throw new HoneycombTestException("Error in parsing: " + e);
            }
            
            String adminIp = tmp[0];
            String dataIp = tmp[1];
            
            int newCellid = addCell(adminIp, dataIp);
            if (newCellid == -1) {
                Log.ERROR("failed to add cell : adminIP = " + adminIp + 
                    ", dataIP = " + dataIp);
                noOfFailure ++; 
            }
            else {
                if (!verifyAddCell(newCellid, adminIp, dataIp)) 
                    noOfFailure ++;
            }
        }  
        
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    private int getRandomCellid() {
        if (getTotalNoOfCell() <= 1)
            return -1;
        
        // ignore master cell id 
        while (true) {
            int randNum = rand(getTotalNoOfCell());
            int cellId = ((Integer) getAllCellid().get(randNum)).intValue();
            
            if (cellId != getMasterCellid())
                return cellId;
        }
    }
    
    public static int rand(int high) {
        Random rn = new Random();
        int num = rn.nextInt(high);
        
        return num;
    }
    
    private boolean verifySysStat(String adminIp) {
        int noOfFailure = 0;
        ArrayList sysstatStdout = new ArrayList();
        
        try {
            BufferedReader br = runCommandWithoutCellid(adminIp, 
              HoneycombCLISuite.SYSSTAT_COMMAND);
            String line = null;
        
            while ((line = br.readLine()) != null) {
                sysstatStdout.add(line);
            }
            br.close();
        } catch (Throwable t) {
            Log.ERROR("Error while executing sysstat command: "+ t.toString());
            return false;
        } 
        
        String actNodeDisk_state = "16 nodes online, " + 
          "64 disks online, " + 
          "0 disks unrecovered";
        
        String cluster_state = (String)sysstatStdout.get(
            HoneycombCLISuite.SYSSTAT_CLUSTER_STATE_LINE);
        String nodeDisk_state = (String)sysstatStdout.get(
            HoneycombCLISuite.SYSSTAT_NODE_DISK_STATE_LINE);
        String services_state = (String)sysstatStdout.get(
            HoneycombCLISuite.SYSSTAT_SERVICES_STATE_LINE);
                
        if (cluster_state.indexOf(HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE) ==
          -1) {
            noOfFailure++;
            Log.ERROR("Unexpected cluster state: " + cluster_state +
              "\nExpected: " + HoneycombCLISuite.SYSSTAT_CLUSTER_ONLINE);
        }
            
        if (nodeDisk_state.indexOf(actNodeDisk_state) == -1) {
            noOfFailure++;                            
            Log.ERROR("Unexpected node/disk state: " + nodeDisk_state +
              "\nExpected: " + actNodeDisk_state);
        }
        
        if(services_state.indexOf(HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE) == -1) {
            noOfFailure++;
            Log.ERROR("Unexpected Data Service state: " + services_state +
              "\nExpected: " + HoneycombCLISuite.SYSSTAT_SERVICES_AVAILABLE);
        }
        
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    private boolean verifyCelladm(String adminIp) {
        String expLine = "Expanding a cell isn't possible " +
          "in a multicell configuration.";
        boolean isExpLine = false;
                        
        try {
            BufferedReader br = runCommandWithoutCellid(adminIp, 
              HoneycombCLISuite.CELLADM_COMMAND);
            
            String line = null; 
            while ((line = br.readLine()) != null) {
                Log.INFO("celladm output: " + line);
                
                if (line.equals(expLine))
                    isExpLine = true;
            }
            br.close();
            
        } catch (Throwable t) {
            Log.ERROR("Error while executing celladm command: "+ t.toString());
            return false;
        } 
        
        return isExpLine;
    }
    
    private void verifyAuditHiveadm(String paramName, String paramValue1, 
            String paramValue2, String msg) {
        ArrayList paramValueList = new ArrayList();
        paramValueList.add(paramValue1);
        if (paramValue2 != null)
            paramValueList.add(paramValue2);        
        verifyAuditInternalLog(HoneycombCLISuite.HIVEADM_COMMAND + " " + paramName,
                msg, paramValueList, true);         
    }
}
