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


import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;

import com.sun.honeycomb.hctest.suitcase.OutputReader;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;

import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

import com.sun.honeycomb.layout.DiskMask;


import java.lang.Runtime;
import java.nio.channels.ReadableByteChannel;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
/*
  There may b many tests that want to alter the state of cli vars.
  When we get around to running in parallel (Trash fragments + load, or 
  possibly trash fragments + performance) we want to ensure that we don't
  have several tests trying to tell the cli conflicting things.
  This will be the single point of access for altering the cli state.
*/

// Singleton me
// enum for state names
// "all" accessor. 
// Somehow grab/reinstate run times when we re-enable.


public class CLIState {
    private static CLIState instance=null;


    //
    //
    // FIXME:
    // This is somewhat lame
    /*  
        I've added a new hidden "convenience" command for setting the
        DataDoctor cycle targets, to use it then add this to your config,
        (or get the cluster_config.properties from the trunk)
        
        honeycomb.hcsh.cmd.name.CommandDataDocConfig = ddcfg
        honeycomb.hcsh.cmd.name.CommandDataDocConfig.isHidden = true
        
        here is the usage:
        ddcfg [-F|--force] < off | fullspeed | default >
        
*/


    // RFE: Data doctor commands (6353974)

    //
    private int populate_ext_cache_cycle_default = 43200;
    private int populate_sys_cache_cycle_default = 43200;
    private int recover_lost_frags_cycle_default = 43200;
    private int remove_dup_frags_cycle_default = 43200;
    private int remove_temp_frags_cycle_default = 86400;
    private int scan_frags_cycle_default = 604800;


    private int populate_ext_cache_cycle;
    private int populate_sys_cache_cycle;
    private int recover_lost_frags_cycle;
    private int remove_dup_frags_cycle;
    private int remove_temp_frags_cycle;
    private int scan_frags_cycle;

    private CLI cli;

    private int nodes; // num nodes in cluster (8 or 16)

    private static String data_vip = null;
    private static String admin_vip = null;
    private static String sp_ip = null;
    private static String smtp_server = null;
    private static String smtp_port = null;
    private static String ntp_server = null;
    private static String gateway = null;
    private static String subnet = null;
    private static String dns = null;
    private static String domain_name = null;
    private static String dns_search = null;
    private static String primary_dns_server = null;
    private static String secondary_dns_server = null;
    private static String log_host = null;
    private static String authorized_clients = null;
    private static String adminIP;

    public static final CLIState EXT_CACHE=new CLIState();
    public static final CLIState SYSTEM_CACHE=new CLIState();
    public static final CLIState LOST_FRAGS=new CLIState();
    public static final CLIState DUP_FRAGS=new CLIState();
    public static final CLIState TEMP_FRAGS=new CLIState();
    public static final CLIState SCAN_FRAGS=new CLIState();

    public static final CLIState DATA_VIP=new CLIState();
    public static final CLIState ADMIN_VIP=new CLIState();

    private static final int SSHRETRIES=20;
    private static int sshRetryCount=SSHRETRIES;


    public static final String NET_ADMIN_IP="admin_ip";
    public static final String NET_DATA_IP="data_ip";
    public static final String NET_SP_IP="service_node_ip";
    public static final String NET_SUBNET="subnet";
    public static final String NET_GATEWAY="gateway";
    public static final String NET_DNS="dns";
    public static final String NET_DOMAIN_NAME="domain_name";
    public static final String NET_DNS_SEARCH="dns_search";
    public static final String NET_PRIMARY_DNS_SERVER="primary_dns_server";
    public static final String NET_SECONDARY_DNS_SERVER="secondary_dns_server";
    public static final String NET_NTP_SERVER="ntp_server";
    public static final String NET_SMTP_SERVER="smtp_server";
    public static final String NET_SMTP_PORT="smtp_port";
    public static final String NET_SMTP_ALERT_FROM="smtp_alert_from";
    public static final String NET_SMTP_ALERT_TO="smtp_alert_to";
    public static final String NET_SMTP_ALERT_CC="smtp_alert_cc";
    public static final String DATA_PORT="data_port";
    public static final String DATA_CACHE="data_cache";
    public static final String AUTHORIZED_CLIENTS="authorized_clients";
    public static final String REMOVE_DUP_FRAGS_CYCLE="remove_dup_frags_cycle";
    public static final String REMOVE_TEMP_FRAGS_CYCLE="remove_temp_frags_cycle";
    public static final String POPULATE_SYS_CACHE_CYCLE="populate_sys_cache_cycle";
    public static final String POPULATE_EXT_CACHE_CYCLE="populate_ext_cache_cycle";
    public static final String RECOVER_LOST_FRAGS_CYCLE="recover_lost_frags_cycle";
    public static final String SLOSH_FRAGS_CYCLE="slosh_frags_cycle";
    public static final String SCAN_FRAGS_CYCLE="scan_frags_cycle";
    public static final String LICENSE="license";
    public static final String NUM_NODES="num_nodes";
    public static final String NET_LOGGER="logger";
    public static final String EXPANSION_STATUS="expansion_status";
    public static final String EXPANSION_NODEIDS="expansion_nodeids";
    public static final String LANGUAGE="language";
    public static final String MAX_DISKS_PERCENT="max_disks_percent";


    private CLIState() {
        // DD
        initDDDefaults();
        adminIP = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_ADMIN_VIP_ADDR);
        cli = new CLI(adminIP);
    }

    /** for testing */
    private CLIState(String vip) {
        initDDDefaults();
        adminIP = vip;
        cli = new CLI(adminIP);
    }
    static public void main(String[] args) throws Exception {
        CLIState c = new CLIState(args[0]);
        c.syncState();
        c.dumpState();
        if (args.length == 2) {
            Log.INFO("setting datavip to " + args[1]);
            c.setStringValue(CLIState.DATA_VIP, args[1]);
            c.syncState();
            c.dumpState();
        }
    }

    /** Admin VIP is not a secret... caller may need it
     */
    public String getAdminVIP() {
        return adminIP;
    }

    /** Tell caller how many nodes in the cluster (8 or 16)
     */
    public int getNumNodes() throws HoneycombTestException {
        
        // OK to cache the value of nodes, unless you're sloshing! If sloshing, refresh me.
        
        while (nodes == 0) { // retry...
            try {
                nodes = cli.hwstat().getNodes().size(); 
            } catch (Throwable e) {
                sshRetryCount--;
                Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(e));
                if (sshRetryCount == 0) {
                    throw new HoneycombTestException(
                            "retried " + SSHRETRIES + " times. Exiting.");
                }
            }
        }
        return nodes;
    }
    
    private void initDDDefaults() {
        populate_ext_cache_cycle = populate_ext_cache_cycle_default;
        populate_sys_cache_cycle = populate_sys_cache_cycle_default;
        recover_lost_frags_cycle = recover_lost_frags_cycle_default;
        remove_dup_frags_cycle = remove_dup_frags_cycle_default;
        remove_temp_frags_cycle =remove_temp_frags_cycle_default;
        scan_frags_cycle =scan_frags_cycle_default;
    }

    public void startDDCycle(CLIState state) throws HoneycombTestException {
        try {
            setIntValue(state,0);
            setIntValue(state,1);
        } catch (Throwable e) {
            sshRetryCount--;
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(e));
            if (sshRetryCount == 0) {
                throw new HoneycombTestException(
                        "retried " + SSHRETRIES + " times. Exiting.");
            }
            startDDCycle(state);
        }
    }
    public void stopDDCycle(CLIState state) throws HoneycombTestException {
        try {
            setIntValue(state,0);       
        } catch (Throwable e) {
            sshRetryCount--;
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(e));
            if (sshRetryCount==0) {
                throw new HoneycombTestException(
                        "retried " + SSHRETRIES + " times. Exiting.");
            }
            stopDDCycle(state);
        }
    }

    
    public void setIntValue(CLIState state,int value) {
        try {
            if(state==EXT_CACHE) {
                if(populate_ext_cache_cycle != value) {
                    populate_ext_cache_cycle=value;
                    Log.INFO(HCUtil.readLines(cli.runCommand("ddcfg -F populate_ext_cache_cycle " + 
                                                             Integer.toString(value))));
                }
            } else if (state== SYSTEM_CACHE) {
                if (populate_sys_cache_cycle!=value) {
                    populate_sys_cache_cycle=value;
                    Log.INFO(HCUtil.readLines(cli.runCommand("ddcfg -F populate_sys_cache_cycle " + 
                                                             Integer.toString(value))));            
                }
            } else if(state==LOST_FRAGS) {
                if(recover_lost_frags_cycle!=value) {
                    recover_lost_frags_cycle=value;
                    
                    Log.INFO(HCUtil.readLines(cli.runCommand("ddcfg -F recover_lost_frags_cycle " + 
                                                             Integer.toString(value))));
                }
            } else if(state==DUP_FRAGS) {
                if(remove_dup_frags_cycle!=value) {
                    remove_dup_frags_cycle=value;
                    Log.INFO(HCUtil.readLines(cli.runCommand("ddcfg -F remove_dup_frags_cycle " + 
                                                             Integer.toString(value))));
                }
            } else if(state==TEMP_FRAGS) {
                if(remove_temp_frags_cycle!=value ) {
                Log.INFO(HCUtil.readLines(cli.runCommand("ddcfg -F remove_temp_frags_cycle " + 
                                                         Integer.toString(value))));
                remove_temp_frags_cycle=value;
                }
            } else if(state==SCAN_FRAGS) {
                if(scan_frags_cycle!=value) {
                    Log.INFO(HCUtil.readLines(cli.runCommand("ddcfg -F scan_frags_cycle " + 
                                                             Integer.toString(value))));
                    scan_frags_cycle=value;
                }
            } else {
                throw new HoneycombTestException(
                        "Unknown state passed to CLIState.setIntValue().");         
            }
            
            //
            // No reason to sleep here for FragmentLevelTests, might
            // mess with command line tests. 
            //
            //            Thread.sleep(1000*60);
        } catch (Throwable e) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(e));
        }
    }
    public void setStringValue(CLIState state, String value) {
        try {
            if(state==DATA_VIP) {
                if (data_vip == null  ||  !data_vip.equals(value)) {
                    Log.INFO(HCUtil.readLines(cli.runCommand("cellcfg --data_ip " +
                                                             value)));
                    data_vip=value;
                }
            } else if(state==ADMIN_VIP) {
                if (admin_vip == null  ||  !admin_vip.equals(value)) {
                    Log.INFO(HCUtil.readLines(cli.runCommand("cellcfg --admin_ip "+
                                                             value)));
                    admin_vip=value;
                }
            } else {
                throw new HoneycombTestException(
                        "Unknown state passed to CLIState.setStringValue().");          
            }
        } catch (Throwable e) {
            Log.ERROR("IO Error accessing CLI:" + Log.stackTrace(e));
        }
    }

    //
    // Note - this does NOT interrogate the cluster; it's
    // tracked internally. That's da point - if two close to
    // simultaneous processes wanna do the same thing, we only
    // take action if the requested state differs from what we
    // believe the state to be.
    //
    public int getIntValue(CLIState state) throws HoneycombTestException {
        int retval=-1;
        if(state==EXT_CACHE) {
            retval =  populate_ext_cache_cycle;
        } else if(state==SYSTEM_CACHE) {
            retval = populate_sys_cache_cycle;
        } else if(state==LOST_FRAGS) {
            retval = recover_lost_frags_cycle;
        } else if(state==DUP_FRAGS) {
            retval = remove_dup_frags_cycle;
        } else if(state==TEMP_FRAGS) {
            retval = remove_temp_frags_cycle;           
        } else if(state==SCAN_FRAGS) {
            retval = scan_frags_cycle;            
        } else {
            throw new HoneycombTestException(
                    "Unknown state passed to CLIState.getIntValue().");
        }
        return retval;
    }
    public String getStringValue(CLIState state) throws HoneycombTestException {
        if (state!=DATA_VIP)
            throw new HoneycombTestException(
                "Unknown state passed to CLIState.getStringValue()");
        
        return data_vip;
    }
    

    //
    // Testing
    //
    public void dumpState() {

        System.out.println(POPULATE_EXT_CACHE_CYCLE + "= " + 
                                                populate_ext_cache_cycle);
        System.out.println(POPULATE_SYS_CACHE_CYCLE + "= " + 
                                                populate_sys_cache_cycle);
        System.out.println(RECOVER_LOST_FRAGS_CYCLE + "= " + 
                                                recover_lost_frags_cycle);
        System.out.println(REMOVE_DUP_FRAGS_CYCLE + "= " +
                                                remove_dup_frags_cycle);
        System.out.println(REMOVE_TEMP_FRAGS_CYCLE + "= " + 
                                                remove_temp_frags_cycle);
        System.out.println(SCAN_FRAGS_CYCLE + "= " +
                                                scan_frags_cycle );

        System.out.println(NET_DATA_IP + "= " + data_vip);
        System.out.println(NET_ADMIN_IP + "= " + admin_vip);
        System.out.println(NET_SP_IP + "= " + sp_ip);
        System.out.println(NET_SMTP_SERVER + "= " + smtp_server);
        System.out.println(NET_SMTP_PORT + "= " + smtp_port);
        System.out.println(NET_NTP_SERVER + "= " + ntp_server);
        System.out.println(NET_GATEWAY + "= " + gateway);
        System.out.println(NET_SUBNET + "= " + subnet);
        System.out.println(NET_DNS + "= " + (dns.equals("n")?"Disabled":"Enabled"));
        System.out.println(NET_DOMAIN_NAME + "= " + domain_name);
        System.out.println(NET_DNS_SEARCH + "= " + dns_search);
        System.out.println(NET_PRIMARY_DNS_SERVER + "= " + primary_dns_server);
        System.out.println(NET_SECONDARY_DNS_SERVER + "= " + secondary_dns_server);
        //System.out.println("log_host= " + log_host);
        System.out.println(AUTHORIZED_CLIENTS + "= " + 
                                                authorized_clients);
    }
    public void syncState() throws HoneycombTestException {
        BufferedReader output=null;

        try {
            output = cli.runCommand("ddcfg -F");
       
            String line = null;
            int node_i = -1;
            while ((line = output.readLine()) != null) {

                int equalsIndex=line.indexOf('=');

                if(-1 != equalsIndex) {
                    String valueString=line.substring(equalsIndex+2);

                    int value=-1;
                    try {
                        value=Integer.parseInt(valueString);
                    } catch (NumberFormatException e){

                    }
                    if(value!= -1) {

                        if (line.startsWith("populate_ext_cache_cycle")) {
                            populate_ext_cache_cycle=value;
                        } else if (line.startsWith("populate_sys_cache_cycle")) {
                            populate_sys_cache_cycle=value;
                        } else if (line.startsWith("recover_lost_frags_cycle")) {
                            recover_lost_frags_cycle=value;
                        } else if (line.startsWith("remove_dup_frags_cycle")) {
                            remove_dup_frags_cycle=value;
                        } else if (line.startsWith("remove_temp_frags_cycle")) {
                            remove_temp_frags_cycle=value;
                        } else if (line.startsWith("scan_frags_cycle")) {
                            scan_frags_cycle=value;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw new HoneycombTestException(
                    "Error running ddcfg command on cli; exiting: " + 
                    Log.stackTrace(e));
        }


        try {
            output = cli.runCommand("cellcfg");
       
            String line = null;

            while ((line = output.readLine()) != null) {
                //System.out.println("netcfg: " + line);
                int equalsIndex=line.indexOf('=');

                if(-1 != equalsIndex) {
                    String valueString=line.substring(equalsIndex+2);

                    if (line.startsWith("Data IP Address")) {
                        data_vip=valueString;
                    } else if (line.startsWith("Admin IP Address")) {
                        admin_vip=valueString;
                    } else if (line.startsWith("Service Node IP Address")) {
                        sp_ip=valueString;
                    } else if (line.startsWith("Gateway")) {
                        gateway=valueString;
                    } else if (line.startsWith("Subnet")) {
                        subnet=valueString;
                    } else
                        Log.INFO("Unexpected string: " + line);
                }
            }
            
            output = cli.runCommand("hivecfg");
            
            line = null;

            
            while ((line = output.readLine()) != null) {
                //System.out.println("netcfg: " + line);
                int equalsIndex=line.indexOf('=');

                if(-1 != equalsIndex) {
                    String valueString=line.substring(equalsIndex+2);
                    if (line.startsWith("SMTP Server")) {
                        smtp_server=valueString;

                    } else if (line.startsWith("SMTP Port")) {
                        smtp_port=valueString;
                    } else if (line.startsWith("NTP Server")) {
                        ntp_server=valueString;

                    } else if (line.startsWith("Domain Name")) {
                        if (valueString == null) {
                            Log.INFO("Unexpected string: " + line);
                        }
                        domain_name=valueString;
                    } else if (line.startsWith("DNS Search")) {
                        if (valueString == null) {
                            Log.INFO("Unexpected string: " + line);
                        }
                        dns_search=valueString;
                    } else if (line.startsWith("Primary DNS Server")) {
                        primary_dns_server=valueString;
                    } else if (line.startsWith("Secondary DNS Server")) {
                        secondary_dns_server=valueString;
                    } else if (line.startsWith("DNS")) {
                        dns=valueString;

                    } else if (line.startsWith("External Logger")) {
                        log_host=valueString;
                    } else if (line.startsWith("Authorized Clients")) {
                        authorized_clients=valueString;

                        
                    } else
                        Log.INFO("Unexpected string: " + line);
                }
            }
            
            // Do some dns validation
	    if (dns.equals("n")) {
                domain_name = null;
                dns_search = null;
	      primary_dns_server = null;
	      secondary_dns_server = null;
            } else if (dns.equals("y")) {
	        if ((domain_name == null) ||
	  	    (dns_search == null) ||
	  	    (primary_dns_server == null) ||
		    (secondary_dns_server == null)) {
	  	  Log.INFO("Invalid values for dns: " + line);
	        }
	    } else {
	       Log.INFO("Unexpected string: " + line);
	    }
        } catch (Throwable e) {
            throw new HoneycombTestException(
                    "Error gathering ddcfg, cellcfg, hivecfg command " +
                    "on cli; exiting: " + Log.stackTrace(e));
        }
    }


    public void disableDD() {
        setIntValue(EXT_CACHE, 0);
        setIntValue(SYSTEM_CACHE, 0);
        setIntValue(LOST_FRAGS, 0);
        setIntValue(DUP_FRAGS, 0);
        setIntValue(TEMP_FRAGS, 0);
        setIntValue(SCAN_FRAGS, 0);
    }

    public void fastestDD() {
        setIntValue(EXT_CACHE, 1);
        setIntValue(SYSTEM_CACHE, 1);
        setIntValue(LOST_FRAGS, 1);
        setIntValue(DUP_FRAGS, 1);
        setIntValue(TEMP_FRAGS, 1);
        setIntValue(SCAN_FRAGS, 1);
    }
  
    public void enableDD() throws HoneycombTestException {
        setDefaultDD(EXT_CACHE);
        setDefaultDD(SYSTEM_CACHE);
        setDefaultDD(LOST_FRAGS);
        setDefaultDD(DUP_FRAGS);
        setDefaultDD(TEMP_FRAGS);
        setDefaultDD(SCAN_FRAGS);
        
    }
    private String nodeNumber(int node) {
        String nodeNumber=""+node;
        return nodeNumber;
        /*
        String nodeNumber;
        if(node<=9) {
            nodeNumber="10"+node;
        } else {
            nodeNumber="1"+node;
        }
        return nodeNumber;
        */
    }

    public DiskMask getDiskMask() throws Throwable {
        HwStat hwstat = new HwStat(cli.runCommand("hwstat"));
        return hwstat.getDiskMask();
    }

    public int getDDValue(CLIState state) throws HoneycombTestException {
        if (state==EXT_CACHE) {
            return populate_ext_cache_cycle;
        } else  
        if (state==SYSTEM_CACHE) {
            return populate_sys_cache_cycle;
        } else 
        if (state== LOST_FRAGS ){
            return recover_lost_frags_cycle;
        } else 
        if (state==DUP_FRAGS ){
            return remove_dup_frags_cycle;
        } else 
        if (state==TEMP_FRAGS){
            return remove_temp_frags_cycle;
        } else 
        if (state==SCAN_FRAGS){
            return scan_frags_cycle;
        } else {            
            throw new HoneycombTestException(
                    "bad value passed in getDDValue");
        }
    }

    public void stopItDD(String propName, CLIState state) 
                            throws HoneycombTestException {

        if(0 != getDDValue(state)) {
            boolean zeroed=false;
            for (int i=0;i<10 && (zeroed==false);i++) {
                setIntValue(state,0);

                syncState();
                if(0 != getDDValue(state)) {
                    Log.WARN("Failed to disable "+propName+" state, retrying.");
                } else {
                    zeroed=true;
                }
                try{
                    Thread.sleep(30000);
                } catch (InterruptedException ie) {
                    Log.ERROR("Interrupted exception: "+ie.toString());
                }
                
            }
            if(zeroed==false) {
                throw new HoneycombTestException(
                        "Cannot trigger manual data doctor if the scan_frags " +
                        "component of DD is active.\n" + 
                        "It is currently set for: " +  getDDValue(state));
            }
        } 


    }

    public void setDefaultDD(CLIState state) throws HoneycombTestException {
        if(state==EXT_CACHE) {
            setIntValue(EXT_CACHE,populate_ext_cache_cycle_default);
        } else if (state==SYSTEM_CACHE) {
            setIntValue(SYSTEM_CACHE,populate_sys_cache_cycle_default);
        }else if (state==LOST_FRAGS) {
            setIntValue(LOST_FRAGS,recover_lost_frags_cycle_default);        
        }else if (state==DUP_FRAGS) {
            setIntValue(DUP_FRAGS,remove_dup_frags_cycle_default);        
        }else if (state==TEMP_FRAGS) {
            setIntValue(TEMP_FRAGS,remove_temp_frags_cycle_default);        
        }else if (state==SCAN_FRAGS) {
            setIntValue(SCAN_FRAGS,scan_frags_cycle_default);        
        } else {
            throw new HoneycombTestException(
                    "Unknown state passed to CLIState.setIntValue().");
        }

    }
    
    public void verifyNodeAndDisk(int nodeID, int diskID) throws HoneycombTestException {
        if (nodeID < 101 || nodeID > 116) 
            throw new HoneycombTestException("nodeID must be between 101 and 116.");
        
        if (diskID < 0 || diskID > 3) 
            throw new HoneycombTestException("diskID must be between 0 and 3.");
    }
    
    public void disableDisk(int nodeID, int diskID) throws HoneycombTestException {
        
        if (nodeID < 100)
            nodeID = nodeID + 100;
        
        verifyNodeAndDisk(nodeID, diskID);
        
        try {
            cli.runCommand("hwcfg -F -D DISK-" + nodeID + ":" + diskID);
        } catch (Throwable e) {
            throw new HoneycombTestException("Unable to disable disk: ",e);
        }
    }
    
    public void enableDisk(int nodeID, int diskID) throws HoneycombTestException {
        
        if (nodeID < 100)
            nodeID = nodeID + 100;
        
        verifyNodeAndDisk(nodeID, diskID);
        
        try {
            cli.runCommand("hwcfg -F -E DISK-" + nodeID + ":" + diskID);
        } catch (Throwable e) {
            throw new HoneycombTestException("Unable to disable disk: ",e);
        }
    }
    
    public static CLIState getInstance() {
        if(null==instance) {
            instance = new CLIState();
            try {
                instance.syncState();
            } catch (HoneycombTestException he) {
                Log.ERROR(he.getMessage());
            }
        }
        return instance;
    }

}

