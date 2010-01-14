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



package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.test.SolarisNode;
import com.sun.honeycomb.test.util.*;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class ClusterNode extends SolarisNode {

    // poor man's enum for HC configuration
    private final int FULL_HC = 0;
    private final int CMM_ONLY = 1;
    private final int NODE_MGR = 2;
    private final int CMM_SINGLE = 3; // CMM-only on single node
    private final int CMM_ONLY_WITH_SNIFFER = 4;

    // commands to start/stop honeycomb
    //private static final String START_FULL_HC = 
    //    "svcadm enable honeycomb-server";

    private static final String START_FULL_HC = 
        "/opt/honeycomb/etc/init.d/honeycomb start";
    private static final String STOP_HC = 
        "/opt/honeycomb/etc/init.d/honeycomb stop";
   
    private static final String STOP_FULL_HC =
        "svcadm disable honeycomb-server"; // non-blocking
    private static final String START_CMM_ONLY = 
        "\"/opt/honeycomb/sbin/start_cmm_only 0>&- 1>&- 2>&-\"";
    private static final String KILL_JVM_PREFIX = "\"kill -9 \\`ps -ef | grep ";
    private static final String KILL_JVM_SUFFIX = " | grep -v grep | awk '{print \\$2}'\\`\"";

    private static final String KILL_HADB_PROCS = "pkill -9 -f hadb_install";
    
    private static final String START_CMM_SINGLE =
        "./start_cmm_single.sh ";
    private static final String STOP_CMM_SINGLE =
        "./kill_cmm_single.sh ";
    
    private static final String CMM_DISK_CLIENT = 
        "/opt/test/bin/cmm_disk_client";

    private static final String IRULES = "/opt/honeycomb/bin/irules.sh ";
    
    private static final String UPDATE_SNIFER_BEHAVIOUR = "\"/opt/honeycomb/sbin/update_sniffer ";
    private static final String START_SNIFFER = 
        "\"/opt/honeycomb/sbin/start_sniffer 0>&- 1>&- 2>&-\"";
    private static final String STOP_SNIFFER = 
        "\"/opt/honeycomb/sbin/stop_sniffer 0>&- 1>&- 2>&-\"";
    
    private static final String START_CMM_ONLY_WITH_SNIFFER = 
        "\"/opt/honeycomb/sbin/start_cmm_only sniff-on 0>&- 1>&- 2>&-\"";
    
    private static final String ADD_SERVICE = "/opt/honeycomb/sbin/node_config_editor.sh add svc ";
    private static final String DEL_SERVICE = "/opt/honeycomb/sbin/node_config_editor.sh del svc ";
    private static final String SET_PROPERTY = "/opt/honeycomb/sbin/node_config_editor.sh props set ";
    private static final String UNSET_PROPERTY = "/opt/honeycomb/sbin/node_config_editor.sh props unset ";
    
    private static final String CHECK_SYS_CACHE = "/opt/honeycomb/sbin/check_sys_cache.sh ";
    
    public static final String CONFIG_NOHONEYCOMB = "/config/nohoneycomb";
    public static final String CONFIG_NOREBOOT = "/config/noreboot";
   
    protected int id; // node ID: 1,2,...8,...16
    String adminVIP; // for other ssh access
    CheatNode cheat = null; // for ssh from outside cluster
    int mode; // honeycomb mode

    // expected state
    protected boolean beMaster;
    protected boolean beVice;
    // protected boolean beDown; // comes from SolarisNode
    protected boolean beOut;  // in/out of the cluster
    
    // actual state
    protected boolean isMaster;
    protected boolean isVice;
    // protected boolean isDown; // comes from SolarisNode
    protected boolean isOut; // out of the cluster? based on NodeMgr mailbox
    
    // state of services - just FYI, not really used
    protected String services;
    protected String masterServices;

    // i = nodeId {1..16} (without the 100)
    //
    public ClusterNode(String clusterIP, int i) {
        this(clusterIP, null, i, 0); // the usual honeycomb mode FULL_HC
    }
    public ClusterNode(String clusterIP, CheatNode cheat, int i) {
        this(clusterIP, cheat, i, 0); // the usual honeycomb mode FULL_HC
    }
    
    
    // At this point this can only be used from inside 
    // the cluster.
    public ClusterNode(String nodeName) {
	super(nodeName,SSH_ARGS + "root@" + nodeName);
	id=name2id(nodeName);
	adminVIP="10.123.45.200";
	this.mode=0; //FULL_HC
	if (isDown) {
	    isOut = true;
	    beOut = true;
	}
    }

    // nodeId format is {1..16} (without the 100)
    //
    public ClusterNode(String clusterIP, CheatNode cheat, 
                       int nodeId, int mode) {
        super(id2hostname(nodeId), (clusterIP.equals("10.123.45.200") 
        	? SSH_ARGS + "root@" + id2hostname(nodeId) :  
            SSH_ARGS + "-p" + id2port(nodeId) + " root@" + clusterIP));

        id = nodeId;
        adminVIP = clusterIP;
        // set this.cheat b4 initStatus() since ping in SolarisNode 
        // uses ping here
        this.cheat = cheat; 
        this.mode = mode;

        initStatus();
        
        if (isDown) { // not pingable, must be out of the cluster
            isOut = true;
            beOut = true;
        }
        // all state variables are init'd to false
    }

    public String toString() {
        return "Node:" + name;
    }
    
    public boolean isMaster() { return isMaster; }

    public boolean isVice() { return isVice; }

    public boolean isAlive() { return !(isDown || isOut); }

    /** Set expected node state to match the actual state.
     *  To be used at init time, and after verification.
     */
    public void syncState() {
        beMaster = isMaster;
        beVice = isVice;
        beDown = isDown;
        beOut = isOut;
    }

    /** Set expected node state when node is set offline.
     *  This can be due to requesting the node stop HC, reboot, etc.  
     *  When this node comes back, the expectation is it shouldn't
     *  be master or vice, and the process of bringing it back
     *  should set Out flags to false.  The Down state is not
     *  changed, as this is modified at the SolarisNode level.
     */
    public void setOfflineState() {
        beMaster = false;
        beVice = false;
        beOut = true;
    }
    
    /** Check whether the actual node state matches expectations.
     *  This only applies to up/down, in/out state - mastership separate.
     */
    public boolean hasExpectedState() {
        if (beDown != isDown) return false;
        if (beOut != isOut) return false;
        return true;
    }
    
    /** Check whether the actual master/vice state matches expectations.
     */
    public boolean hasExpectedMastership(boolean masterFailover,
                                         boolean viceFailover) {
        boolean wasMaster = beMaster;
        boolean wasVice = beVice;

        if (masterFailover) { 
            if (wasMaster) { // if we were master, we shouldn't stay one
                beMaster = false; 
                if (isMaster) { 
                    Log.ERROR("Master failover didn't happen: " + getAllStateInfo());
                    return false; 
                }
            } else if (wasVice) { // if we were vice, we should have become master
                beMaster = true;
                beVice = false;
                if (!isOut && !isMaster) {
                    Log.ERROR("Former vice didn't become master: " + getAllStateInfo());
                    return false;
                }
            } else { // we may have become master if both ex-master and ex-vice died
                if (isMaster) {
                    Log.WARN("Non-vice became master: " + getAllStateInfo());
                }
            }
        } else { // should have expected state
            if (isMaster && !beMaster) {
                Log.WARN("Unexpectedly became master: " + getAllStateInfo());
            } // not an error, maybe we had no master before, and now we do
            if (!isMaster && beMaster) {
                Log.WARN("Unexpectedly lost master office: " + getAllStateInfo());
                return false;
            }
        }
        
        if (viceFailover) { // may happen without master failover!
            if (wasVice) { // if we were vicemaster, we shouldn't stay one
                beVice = false;
                if (isVice) { 
                    Log.ERROR("Vice failover didn't happen: " + getAllStateInfo());
                    return false; 
                }
            }
            // if we weren't a vice, we may have become one - no check
        } else { // should have expected state
            if (isVice && !beVice) {
                Log.WARN("Unexpectedly gained vice office: " + getAllStateInfo());
            } // not an error, maybe we had no vice before, and now we do
            if (!isVice && beVice) {
                Log.ERROR("Unexpectedly lost vice office: " + getAllStateInfo());
                return false;
            }
        }
        
        return true; // our state is probably correct
    }
        
    /** Return human-readable string with expected and actual node state.
     */
    public String getAllStateInfo() {
        return name + " Expected state: [" + 
            getState(beMaster, beVice, beDown, beOut) + "]" +
            " Actual state: [" + getState() + "]" +
            " Services: " + services + " " + masterServices;
    }
    
    /** Return human-readable string with actual node state.
     */
    public String getState() {
        return getState(isMaster, isVice, isDown, isOut);
    }
    
    private String getState(boolean master, boolean vice, 
                            boolean down, boolean out) {
        StringBuffer sb = new StringBuffer();
        sb.append(name);
        if (master) sb.append(".MASTER");
        if (vice) sb.append(".VICE");
        if (down) sb.append(".DOWN");
        if (out) sb.append(".OUT");
        if (!down && !out) sb.append(".OK");
        return sb.toString();
    }

    /** Starts HC in desired mode on this cluster node.
     *
     *  This is done by ssh'ing directly to the node and running the start script.
     */
    public void startHoneycomb() throws HoneycombTestException {
    	if (isDown)
    		return;
    	
        if (mode == CMM_SINGLE) {
            int nodeId = 100 + id;
            Log.INFO("Starting node " + id + " [ " + START_CMM_SINGLE + nodeId + " ]");
            shell.exec(START_CMM_SINGLE + nodeId + " 1>&- 2>&- <&-");
        } else {
            String startCmd;
            if (mode == CMM_ONLY) {
                startCmd = START_CMM_ONLY;
            } else if (mode == CMM_ONLY_WITH_SNIFFER){
            		startCmd = START_CMM_ONLY_WITH_SNIFFER;
            	}else { // NODE_MGR or FULL_HC
            		//startCmd = START_FULL_HC;
            		startCmd = START_FULL_HC;
            		// REMOVED temp hack around a bug: svcadm disable goes into maint mode
            		// to clear maintenance mode, must disable again, then ok to enable
            		// stopHoneycomb();
            }
            Log.INFO("Starting node " + id + " [ " + startCmd + " ] ");
            runCmd(startCmd);
        }
        beOut = false;
    }
    
    /**
	 * Update the sniffer behaviour in a CMM test environment.
	 * 
	 * This is done by ssh'ing directly to the node and running the update
	 * script. rules is a String with rules separated by spaces.
	 */
	public void updateSnifferBehaviour(String rules)
			throws HoneycombTestException {
		if (isDown)
			return;
		String cmd = UPDATE_SNIFER_BEHAVIOUR + " \'" + rules + "\'\"";
		runCmdVerify(cmd);
	}

	/**
	 * Start up the sniffer on CMM test environment.
	 * 
	 * This is done by ssh'ing directly to the node and running the update
	 * script. rules is a String with rules separated by spaces.
	 */
	public void startupSniffer() throws HoneycombTestException {
		if (isDown)
			return;
		Log.INFO("Starting sniffer on node " + id + " [ " + START_SNIFFER
				+ " ] ");
		runCmdVerify(START_SNIFFER);
	}

	/**
	 * Stop up the sniffer on CMM test environment.
	 * 
	 * This is done by ssh'ing directly to the node and running the update
	 * script. rules is a String with rules separated by spaces.
	 */
	public void stopSniffer() throws HoneycombTestException {
		if (isDown)
			return;
		Log.INFO("Stoping sniffer on node " + id + " [ " + START_SNIFFER
				+ " ] ");
                runCmdVerify(STOP_SNIFFER);
	}
	
	public void setProperty(String jvmname, String property) throws HoneycombTestException {
		if (isDown) return;
		Log.INFO("Setting property: " + property + " on JVM: " + jvmname );
                runCmdVerify(SET_PROPERTY + " " + jvmname + " " + property);
        }
	
	public void unsetProperty(String jvmname, String property) throws HoneycombTestException {
		if (isDown) return;
		Log.INFO("Unsetting property: " + property + " on JVM: " + jvmname );
		runCmdVerify(UNSET_PROPERTY + " " + jvmname + " " + property);
	}
	
	public void addService(String jvmname, String classname, String servicename, String runlevel) throws HoneycombTestException {
		if (isDown) return;
		Log.INFO("Adding service: " + servicename + " to JVM: " + jvmname + " at runlevel: " + runlevel );
		if (jvmname.equalsIgnoreCase("MASTER-SERVERS"))
                    runCmdVerify(ADD_SERVICE + " master " + jvmname + " " + servicename + " " + classname + " " + runlevel);
		else
                    runCmdVerify(ADD_SERVICE + jvmname + " " + servicename + " " + classname + " " + runlevel);
	}
	
	public void delService(String jvmname, String servicename) throws HoneycombTestException {
		if (isDown) return;
		Log.INFO("Removing service: " + servicename + " to JVM: " + jvmname );
		runCmdVerify(DEL_SERVICE + jvmname + " " + servicename);
	}
	
	/**
	 * Send Signal USR1 to the given honeycomb JVM name
	 * be sure to protect the right characters in the jvmname 
	 * because it is being used as a regular expression.
	 *
	 */
	public void sendSignalToJVM(String jvmname) throws HoneycombTestException{
		Log.INFO("Signalling JVM: " + jvmname );
		runCmdVerify("pkill -USR1 -f \"" + jvmname + "\"");
	}

    /**
	 * Stops HC in desired mode on this cluster node.
	 */
    public void stopHoneycomb() throws HoneycombTestException {
    	if (isDown)
    		return;
        if (mode == CMM_SINGLE) {
            int nodeId = 100 + id;
            Log.INFO("Stopping node " + id + " [ " + STOP_CMM_SINGLE + nodeId);
            shell.exec(STOP_CMM_SINGLE + nodeId); 
        } else { // NODE_MGR or FULL_HC
            Log.INFO("Stopping node " + id);
            pkillJava();
        }
        setOfflineState();
    }
    
    public void callHCStop () throws HoneycombTestException {
	runCmd(STOP_HC);
	setOfflineState();
	beDown = true;
    }

    /** Kills all JVMs on this node
     */
    public void pkillJava() throws HoneycombTestException {
        Log.INFO("Stopping node " + id + " [ " + PKILL_JAVA + " ] ");
        runCmd(PKILL_JAVA);
        setOfflineState();
    }
    
    /**
     * Kills all the HADB procs on the cluster
     */
    public void pkillHadb() throws HoneycombTestException {
	Log.INFO("Killing HADB Processes on node " + id + "[ " + KILL_HADB_PROCS + " ]");
	runCmd(KILL_HADB_PROCS);
    }
    
    /** Kills specific JVM on this node
     */
    public void killJVM(String jvm) throws HoneycombTestException {
        Log.INFO("Killing jvm: " + jvm + " on node: " + name);
        runCmd(KILL_JVM_PREFIX + jvm + KILL_JVM_SUFFIX);
    }

    /** Reboots this cluster node.
     */
    public void reboot() throws HoneycombTestException {
        super.reboot();
        setOfflineState();
    }
    
    public void rebootQuick () throws HoneycombTestException {
	super.rebootQuick();
	setOfflineState();
    }

    /** Run after a reasonable time period after reboot to set node-up expectations.
     */
    public void rebootDone() {
        super.rebootDone();
        beOut = false;
    }

    /** Disconnect this node from the network by downing its switch port.
     */
    public void stopNetwork() throws HoneycombTestException {
        String stopCmd = "zlc zre" + id + " down";
        String verifyCmd = "zlc zre" + id + " query";
        Log.INFO("Stopping network on node " + id + " [ " + stopCmd + " ] ");

        String status = ClusterSwitch.runCmdOnSwitch(verifyCmd).trim();
        if (status.indexOf("DOWN") != -1) {
            Log.WARN("Network is already stopped on node " + id + " [port status] " + status);
            setOfflineState(); // node should not be in the cluster
            return;
        }

        // port-down command is usually quick, but may take a few seconds to kick in
        for (int i = 0; i < 2 && !beOut; i++) { // retry downing twice
            ClusterSwitch.runCmdOnSwitch(stopCmd);
            for (int j = 0; j < 5 && !beOut; i++) { // check status for up to 5 sec
                HCUtil.doSleep(1, "port-check retry"); 
                status = ClusterSwitch.runCmdOnSwitch(verifyCmd).trim();
                if (status.indexOf("DOWN") != -1)
                    setOfflineState();
            }
        }
        if (!beOut)
            throw new HoneycombTestException("Failed to stop network on node " + 
                                             id + " [port status] " + status);
        else
            Log.INFO("Network down on node " + id + " [port status] " + status);
    }

    /**
     * kills snoop process that is capturing arp requests
     */
    public void pkillSnoop(String args) throws HoneycombTestException {
        runCmd("pkill -9 -f " + SNOOP);  
    }

    /** Reconnect this node to the network by upping its switch port.
     */
    public void startNetwork() throws HoneycombTestException {
        String startCmd = "zlc zre" + id + " up";
        String verifyCmd = "zlc zre" + id + " query";
        Log.INFO("Starting network on node " + id + " [ " + startCmd + " ] ");
 
        String status = ClusterSwitch.runCmdOnSwitch(verifyCmd).trim();
        if (status.indexOf("UP") != -1) {
            Log.WARN("Network is already started on node " + id + " [port status] " + status);
            beOut = false; // node should be in the cluster
            return;
        }

        // port-up command is usually slow, may take a few seconds to kick in
        for (int i = 0; i < 2 && beOut; i++) { // retry twice
            ClusterSwitch.runCmdOnSwitch(startCmd);
            for (int j = 0; j < 5 && beOut; j++) { // check status for up to 5 sec
                HCUtil.doSleep(1, "port-check retry");
                status = ClusterSwitch.runCmdOnSwitch(verifyCmd).trim();
                if (status.indexOf("UP") != -1)
                    beOut = false;
            }
        }
        if (beOut)
            throw new HoneycombTestException("Failed to start network on node " + 
                                             id + " [port status] " + status);
        else
            Log.INFO("Network up on node " + id + " [port status] " + status);
    }

    /** Stop this node using requested failure mode.
     */
    public void stopNode(int how) throws HoneycombTestException {

        if (beOut) {
            Log.WARN("Called stopNode() when node " + id + " is already expected to be out of cluster");
            beOut = false;
        }

        if (how == HoneycombTestConstants.FAIL_NETWORK) {
            stopNetwork();
        } else if (how == HoneycombTestConstants.PKILL_JAVA) {
            pkillJava();
        } else if (how == HoneycombTestConstants.FAIL_HC) { 
            stopHoneycomb(); 
        } else { // shouldn't happen... default: FAIL_HC
            stopHoneycomb();
        }
    }

    /** Restart a node from given failure mode.
     */
    public void startNode(int how) throws HoneycombTestException {

        if (!beOut) {
            Log.WARN("Called startNode() when node " + id + " is already expected to be in cluster");
            beOut = true;
        }

        if (how == HoneycombTestConstants.FAIL_NETWORK) {
            startNetwork();
        } else if (how == HoneycombTestConstants.FAIL_HC) {
            startHoneycomb();
        } else { // shouldn't happen... default: FAIL_HC
            startHoneycomb();
        }
    }

    /** Read node manager mailbox on this node, return output
     *
     *  Unused - verifier checks the mailboxes now. Keeping just in case for debug.
     */
    public String readNodeMgrMailbox() throws HoneycombTestException {
        String mailbox = "/opt/honeycomb/bin/nodemgr_mailbox.sh";
        return runCmd(mailbox);
    }

    /** Utility function to translate numeric node ID (1..16) to its hostname (hcb101..hcb116).
     */
    public static String id2hostname(int id) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(2);
        String name = "hcb1" + nf.format(id); // hcb101,..,hcb110,
        return name;
    }

    /** Translate numeric node ID (1..16) to its port number for direct ssh via adminVIP
     */
    public static int id2port(int id) {
        return (2000 + id);
    }

    /** Go from node ID 1,2,.. to 101,102,..
     */
    public static int nodeId(int id) {
        return (100 + id);
    }
    
    /** Go From hcbxxx to ID 1,2..
     * 
     */
    public static int name2id(String name) {
	return Integer.parseInt(name.substring(3)) - 100;
    }

    
    /** Returns total count of active disks in the cluster, according to CMM.
     *  Note that this is NOT local disk count, but clusterwide total.
     */
    public int getDisks() throws HoneycombTestException {
        String cmd = CMM_DISK_CLIENT + " " + nodeId(id);
        String disks = cheat.runCmd(cmd);
        int numDisks = -1;
        try {
            numDisks = Integer.parseInt(disks.trim().replace("Disks:", ""));
        } catch (NumberFormatException e) {
            throw new HoneycombTestException("getDisks failed on node " + id + ": " + e);
        }
        return numDisks;
    }

    /** Sets count of active disks on this node to COUNT.
     *  Returns new clusterwide total count of active disks.
     */
    public int setDisks(int count) throws HoneycombTestException {
        String cmd = CMM_DISK_CLIENT + " " + nodeId(id) + " " + count;
        String disks = cheat.runCmd(cmd);
        int numDisks = -1;
        try {
            numDisks = Integer.parseInt(disks.trim().replace("Disks:", ""));
        } catch (NumberFormatException e) {
            throw new HoneycombTestException("setDisks failed on node " + id + ": " + e);
        }
        return numDisks;
    }

    public List checkSysCache(String dataoid) throws HoneycombTestException {
        Log.DEBUG("Checking node " + id + " sys_cache for oid: " + dataoid);
        ExitStatus status = runCmdVerify(CHECK_SYS_CACHE + dataoid);
        return status.getOutStrings();
    }

    /**
     * Runs /opt/honeycomb/bin/irules.sh and returns the output
     */
    public String irules() throws HoneycombTestException {
        return runCmd(IRULES);
    }

    /**
     * Runs snoop and returns the output
     */
    public void snoop(String args) throws HoneycombTestException {
        runCmdPlus(SNOOP + args);
    }

    public String getSnoopOutput(String filename) throws HoneycombTestException {
        return runCmdPlus(SNOOP + "-i " + filename).getOutputString();
    }

    /** 
     * Redefines SolarisNode method to return success when node is down.
     * For cluster nodes that are down we don't care whether pkg is installed.
     */
    public boolean packageInstalled(String packageName) throws HoneycombTestException {
        if (isDown) return true;
        return super.packageInstalled(packageName);
    }

    /**
     * Redefines SolarisNode ping because we must ping cluster nodes from cheat,
     * not directly from outside, since they don't have public IPs. If cheat is
     * not set, we should be running from within the cluster so direct ping is
     * possible.
     */
    public boolean ping() {
        if (cheat == null) {
            Log.INFO("ping: cheat not set, pinging directly");
            return super.ping();
        }
        boolean alive = false;
        try {
            String result = cheat.runCmd("ping " + name + " 1");
            alive = (result.indexOf("alive") != -1);
            if (!alive) {
        	isDown = true;
                Log.INFO("Cluster node " + name + " is not pingable: " + result);
            } else {
        	isDown = false;
            }
            return alive;			
        } catch (Throwable e) {
            Log.INFO("Cluster node " + name + " is not pingable: " + e.getMessage());
            return false;
        } 
    }

    /**
     * This runs on the cluster to convert given layout map ID into list of DiskIds.
     *
     * To know where a given OID is stored on the cluster, do this:
     * 1. Parse out layout map ID from internal representation of OID
     * 2. printLayout(OID) -> get back array of diskIds where frags 0-6 are stored
     * 
     */
    public ArrayList printLayout(int mapId) throws HoneycombTestException {
        ArrayList locations = new ArrayList();
        String output = runCmd("/opt/honeycomb/sbin/print_layout.sh " + mapId);
        
        // TODO: parse the output into an ordered array of DiskIds
        
        return locations;
    }

    public void setupDevMode() throws HoneycombTestException {
        if (isOut) return;
        createFile(CONFIG_NOHONEYCOMB, "");
        createFile(CONFIG_NOREBOOT, "");
    }

    public void removeDevMode() throws HoneycombTestException {
        if (isDown) return;
        removeFile(CONFIG_NOHONEYCOMB);
        removeFile(CONFIG_NOREBOOT);        
    }
    
    public String getName() {
	return name;
    }

    public ArrayList readHadbmNodesStatus() throws HoneycombTestException {
        ArrayList nodeList = new ArrayList();
        String hadbm = runCmd("\'echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes\'");
        String[] nodes = hadbm.split("\\n");
        for(int i=0; i<nodes.length; i++) {
            nodeList.add(nodes[i]);        
        }
        return nodeList; 
    }

    public ArrayList readHadbObjectCount() throws HoneycombTestException {
        ArrayList objectList = new ArrayList();
        runCmd("echo \"select count\\(*\\) from t_system\\; > /tmp/hadbObjectCount\""); 
        String objects = runCmd("\'/opt/SUNWhadb/4/bin/clusql -noheader -notrace -nointeractive localhost:15005 system+superduper -command=/tmp/hadbObjectCount\'");
        String[] lines = objects.split("\\n");
        for(int i=0; i<lines.length; i++) {
            objectList.add(lines[i]);        
        }
        return objectList; 
   }

   public int getActiveSwitchID() throws HoneycombTestException {
       String switchID = 
           runCmd("ssh -p 2222 -l nopasswd 10.123.45.1 " +
                  "grep SWITCH_ID= /etc/honeycomb/switch.conf " +
                  "| awk -F= {'print $2'}").trim();
       Log.INFO("Switch ID -> " + switchID);
       return Integer.parseInt(switchID);
   } 
}
