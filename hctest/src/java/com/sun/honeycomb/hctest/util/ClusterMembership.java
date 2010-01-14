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

import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.cli.NodeStat;
import com.sun.honeycomb.test.util.*;

import java.util.regex.Pattern;
import java.util.*;

/**
 * Monitor cluster membership (CMM state), incl. master node status.
 * 
 * Client is supposed to keep a ClusterMembership object to track
 * expected cluster state (number of nodes, who should be master).
 *
 * Another ClusterMembership object is used to collect actual state
 * from the cluster, using CLI (hwstat) and whitebox inspection.
 * Whitebox checks can be:
 * - using cmm_verifier to read state via CMM API from each node,
 * - reading NodeMgr mailbox on each node,
 * - pinging each node, 
 * - getting uptime from each node (should increase), 
 * - parsing logs for CMM errors/exceptions.
 * Not all of the above are implemented.
 *
 * A mismatch between expected and actual ClusterMembership
 * should be treated as a failure by the testcase. It is the client's
 * job to call ClusterMembership.compare() and post failed result.
 */

public class ClusterMembership {

    // poor man's enum to represent cluster run mode
    private static final int FULL_HC = 0;
    private static final int CMM_ONLY = 1;
    private static final int NODE_MGR = 2;
    private static final int CMM_SINGLE = 3;
    private static final int CMM_ONLY_WITH_SNIFFER = 4;

    // for methods that take number of nodes, 0 means all nodes
    private static final int ALL_NODES = 0;

    private int mode;        // run mode: see enum above
    private String adminVIP; // for CLI access to run 'hwstat'
    private int numNodes;    // number of nodes in the cluster
    private Map nodes;       // ClusterNode objects
    private RunCommand shell;
    private boolean masterFailover;
    private boolean viceFailover;
    private boolean quorum;  // should we have quorum or not?
    private int verfTimeout; // CMM Verifier timeout on retries
    private static final int verfInterval = 1; // 1 second between retries
    private CLI cli = null;
    CheatNode cheat = null;
    
    public ClusterMembership(int numNodes, String adminVIP) 
                            throws HoneycombTestException {
        this(numNodes, adminVIP, FULL_HC);
        
    }

    public ClusterMembership(int numNodes, String masterAdminVIP, int mode) 
                                      throws HoneycombTestException {
        this(numNodes, masterAdminVIP, mode, -1, masterAdminVIP, null); 
    }

    // masterAdminVIP & adminIP are same in case of master cell 
    public ClusterMembership(int numNodes, String masterAdminVIP, int mode, 
            int cellId, String adminIP, String cheatIP) 
        throws HoneycombTestException {

        cli = new CLI(masterAdminVIP);

        try {
            if (cheatIP == null) {
                cheat = CheatNode.getDefaultCheat();
            } else {
                cheat = new CheatNode(cheatIP);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new HoneycombTestException("Getting cheat node: " + t, t);
        }
        if (numNodes == -1) {
            if (mode == FULL_HC ){
                // figure out how many nodes in ring through CLI
                Log.INFO("Discovering how many nodes in ring using the CLI.");
                try {
                    ArrayList nodes = null; 
                    
                    if (cellId == -1) {
                        // for single-cell setup
                        nodes = cli.hwstat().getNodes();
                    } else {
                        // for multi-cell setup
                        nodes = cli.hwstat(cellId).getNodes();
                    } 
                    
                    numNodes = nodes.size();
		    if (numNodes == 0) {
			throw new HoneycombTestException("Got node count of 0 nodes from hwstat.\n"
			    + "This may indicate that the Admin IP is not reachable.  Check to\n"
			    + "ensure that command like 'ssh admin@"+masterAdminVIP+"' work.");
		    }
                    Log.INFO("Discovered " + numNodes + " nodes through the CLI.");
                } 
		catch (HoneycombTestException hte) {
		    throw hte;
		}
		catch (Throwable e) {
                    throw new HoneycombTestException("Error trying " +
                            "to get node count from CLI.",e);
                }
            } else 
                throw new HoneycombTestException("numNodes can only " +
                        "be -1 when in FULL_HC mode.");
        }
        this.adminVIP = adminIP;
        this.numNodes = numNodes;
        this.mode = mode;

        nodes = new LinkedHashMap(numNodes);
        for (int i = 1; i <= numNodes; i++) {
            String name = ClusterNode.id2hostname(i);
            nodes.put(name, new ClusterNode(adminIP, cheat, i, mode));
        }

        masterFailover = false;
        viceFailover = false;
        quorum = false;
        verfTimeout = 60; // seconds = 1 minute
        shell = new RunCommand();
    }

    /** TEST
     */
    public static void main(String[] args) {

        int numNodes = 0;
        int mode = 0;
        try {
            numNodes = (new Integer(args[0])).intValue();
        } catch (Exception e) {
            Log.ERROR("Usage: ClusterMembership <numNodes> <adminVIP> [mode]");
            System.exit(42);
        }
        String adminVIP = args[1];
        try {
            mode = (new Integer(args[2])).intValue();
        } catch (Exception e) {
            mode = FULL_HC; // default
        }

        ClusterMembership cm = null;
        try {
            cm = new ClusterMembership(numNodes, adminVIP, mode);
        } catch (HoneycombTestException e1) {
            Log.ERROR("FAIL: " + e1);
            System.exit(-1);
        }

        try {
            cm.initClusterState();
        } catch (HoneycombTestException e) {
            Log.ERROR("FAIL: " + e);
            System.exit(43);
        }

        ClusterNode n = cm.getNode("hcb102");
        try {
            n.stopHoneycomb();
        } catch (HoneycombTestException e) {
            Log.ERROR("FAIL: Couldn't stop node: " + e);
            System.exit(44);
        }

        boolean ok = false;
        try {
            cm.updateClusterState();
            ok = cm.hasExpectedState();
        } catch (HoneycombTestException e) {
            Log.ERROR("FAIL: " + e);
        }

        if (ok) {
            Log.INFO("PASS: Node Stop");
        } else {
            Log.INFO("FAIL: Node Stop");
        }

        try {
            n.startHoneycomb();
        } catch (HoneycombTestException e) {
            Log.ERROR("FAIL: Couldn't start node: " + e);
            System.exit(44);
        }

        try {
            cm.updateClusterState();
            ok = cm.hasExpectedState();
        } catch (HoneycombTestException e) {
            Log.ERROR("FAIL: " + e);
        }

        if (ok) {
            Log.INFO("PASS: Node Start");
        } else {
            Log.INFO("FAIL: Node Start");
        }


    }

    /** Read in initial cluster state.
     */
    public void initClusterState() throws HoneycombTestException {
        initClusterState(-1);
    }
    
    public void initClusterState(int cellId) throws HoneycombTestException {
        if (cheat.getIsDown()) {
            throw new HoneycombTestException("Cheat node " + cheat +
                                     " is down, unable to run CMM verifier");
        }
        updateClusterState(cellId);
        syncState();
        Log.INFO("INITIAL CLUSTER STATE: " + this);
    }

    /** Set expected state to be identical to actual cluster state.
     *
     *  Use on startup to set initial expectations, and
     *  after verifying that state change happened correctly.
     */
    public void syncState() {
        Iterator nodeList = nodes.values().iterator();
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = (ClusterNode)nodeList.next();
            node.syncState();
        }
        masterFailover = false;
        viceFailover = false;
    }

    /** Set our state to be the actual state of the cluster.
     *
     *  This involves running commands on the cluster nodes, plus CLI.
     *  If commands time out or produce unexpected output, we throw.
     */
    public void updateClusterState() throws HoneycombTestException {
        updateClusterState(-1);
    }
    
    public void updateClusterState(int cellId) throws HoneycombTestException {
        pingNodes();       // not implemented        

        try {
            parseVerifier(); // CMM only, or CMM + NodeMgr mailbox verification
        } catch (HoneycombTestException e) {
            /* Retry once to get around bug 6298871:
               CMM API closes connection for unknown reason, verifier fails,
               but works fine on the very next retry
             */
            parseVerifier(); 
        }
        if (mode == FULL_HC) {
            verifyHWstat(cellId); // untested
        }
    }

    /** Verify that nodes are physically online by pinging them.
     *  Maybe also check that Honeycomb JVMs are running?
     */
    public void pingNodes() throws HoneycombTestException {
        Iterator nodeList = nodes.values().iterator();
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = (ClusterNode)nodeList.next();
            node.ping();
        }
    }

    /** Override default timeout: how long CMM Verifier should retry
     *  before giving up, if the cluster state is inconsistent
     */
    public void setVerifierTimeout(int timeout) {
        verfTimeout = timeout;
    }

    /** Use cmm_verifier app to speak CMM API to cluster nodes.
     *  Unless the mode is CMM_ONLY, the verifier also check nodemgr_mailboxes.
     *
     *  If all nodes report the same cluster view, parse output to set nodes' state.
     *  Otherwise throw an exception on inconsistent cluster view.
     */
    public void parseVerifier() throws HoneycombTestException {

        String script = (mode == CMM_SINGLE) ? "./cmm_ut_verifier " : "/opt/test/bin/cmm_verifier ";
        String cmmOnly = (mode == CMM_ONLY || mode == CMM_ONLY_WITH_SNIFFER ? " cmm-only" : " node-mgr");
        String doQuorum = (quorum ? " quorum" : " no-quorum");
        String cmd = script + numNodes + " " + verfTimeout + " " + verfInterval + cmmOnly + doQuorum + " 2>/dev/null";
        String cmmver;
        if (runningOnCheat()) {
            cmmver = shell.execWithOutput(cmd); // runs locally
        } else {
            cmmver = shell.sshCmd("-o StrictHostKeyChecking=no -p 2000 root@" + adminVIP, 
                                  "\"" + cmd + "\""); 
        }
        StringTokenizer lines = new StringTokenizer(cmmver, "\n");
        boolean consistent = false;
        boolean wrong = false; // is mailbox wrong?
        while (lines.hasMoreTokens()) {
            String line = lines.nextToken();
            if (!consistent) {
                consistent = line.matches(".*CLUSTER STATE: CONSISTENT.*");
                if (consistent)
                    wrong = line.matches(".*MAILBOX WRONG.*");
                continue; // skip all preceding lines, and CONSISTENT heading
            }
            if (line.trim().length() == 0 || 
                line.matches(".*VIEW.*")) continue; // heading before node table
            
            Pattern p;
            try { // 102 [0] alive vicemaster
                p = Pattern.compile("[\\[ \\]]++"); // split on space and [ ]
            } catch (Exception e) { // invalid pattern
                throw new HoneycombTestException(e);
            }
            // Sample output:
            // hcb101 [0] alive master
            // hcb102 [0] alive vicemaster
            // hcb103 [0] alive
            // hcb104 dead
            String[] keywords = p.split(line); // nodeId, numDisks, state, mastership
            if (keywords.length < 2) { // didn't match patterns
                throw new HoneycombTestException("Invalid line format in cmm_verifier output: [" + line + "]\n" + cmmver);
            }
            ClusterNode node = (ClusterNode) nodes.get("hcb" + keywords[0]);
            if (node == null) {
                if (!keywords[1].equals("dead")) {
                    throw new HoneycombTestException("Unexpected online node: " + line + "\n" + cmmver);
                } else { // should be OK... eg nodes 9-16 in 8-node cluster
                    Log.DEBUG("Ignoring node in hwstat output: " + line);
                    continue;
                }
            }
            // set actual node state
            node.isOut = (keywords[1].equals("dead")) ? true : false; 
            if (keywords.length >= 4) {
                node.isMaster = (keywords[3].equals("master")) ? true : false;
                node.isVice = (keywords[3].equals("vicemaster")) ? true : false;
            } else {
                node.isMaster = node.isVice = false;
            }
            if (keywords.length == 6) {
                node.services = keywords[4];
                node.masterServices = keywords[5];
            }
        } 
        if (!consistent) {
            throw new HoneycombTestException("CMM Verifier got inconsistent cluster view \n" 
                                             + cmmver);
        }
        if (wrong && (mode == NODE_MGR || mode == FULL_HC)) {
            throw new HoneycombTestException("NodeMgr mailbox is wrong \n" + cmmver);
        }
    }
    
    /** Compare current cluster view with what HWStat tell us
     */
    public void verifyHWstat() throws HoneycombTestException {
        verifyHWstat(-1);
    }
    
    public void verifyHWstat(int cellId) throws HoneycombTestException {
        ArrayList hwStatNodes = null;
        
        try {
            if (cellId == -1) {
                // for single-cell setup
                hwStatNodes = cli.hwstat().getNodes();
            } else {
                // for multi-cell setup
                hwStatNodes = cli.hwstat(cellId).getNodes();
            }
        } catch (Throwable e) {
            throw new HoneycombTestException(e);
        }
        
        Iterator hwStatIterator = hwStatNodes.iterator();
        
        while (hwStatIterator.hasNext()){
            NodeStat nodeStat = (NodeStat)hwStatIterator.next();
            ClusterNode clusterNode = (ClusterNode)
                       nodes.get(ClusterNode.id2hostname(nodeStat.nodeId-100));
            
            if (nodeStat.isInCluster != clusterNode.isAlive()) {
                throw new HoneycombTestException(
                                "HWStat does not verify with " +
                		"state obtained with CMM Verifier: " +
                                "node " + nodeStat.nodeId + ": hwstat.in=" +
                                nodeStat.isInCluster + " cmm.alive=" +
                                clusterNode.isAlive());
            }
        }
        Log.INFO("CMMVerifier and HWStat have consistent views.");
    }
    
    public boolean hasExpectedState() {
	return hasExpectedState(true);
    }

    /** Compare our state with target (actual) state, return true/false.
     */
    public boolean hasExpectedState(boolean checkMaster) {
        int errors = 0;
        
        if (checkMaster && !hasExpectedMastership()) {
            errors++;
        }

        Iterator nodeList = nodes.values().iterator();
        for (int i = 1; i <= numNodes; i++) {
            // Only looks at up/down, in/out state (not mastership).
            ClusterNode node = (ClusterNode)nodeList.next();
            if (!node.hasExpectedState()) {
                Log.ERROR("State mismatch: " + node.getAllStateInfo());
                errors++;
            }
        }
        if (errors > 0) 
            return false;
        else
            return true;
    }

    /** Compare expected mastership with actual.
     */
    public boolean hasExpectedMastership() {
        int masters = 0; // we should have exactly one master
        int vicemrs = 0;   // and exactly one vicemaster
        int errors = 0;

        Iterator nodeList = nodes.values().iterator();
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = (ClusterNode)nodeList.next();
            if (node.isMaster) masters++;
            if (node.isVice) vicemrs++;
            if (!node.hasExpectedMastership(masterFailover, viceFailover)) {
                errors++;
            }
        }
        
        if (masters == 0) {
            Log.ERROR("No master! Cluster state: " + this.toString());
            errors++;
        }
        if (masters > 1) {
            Log.ERROR("Multiple masters! Cluster state: " + this.toString());
            errors++;
        }
        if (vicemrs == 0) {
            Log.ERROR("No vicemaster! Cluster state: " + this.toString());
            errors++;
        }
        if (vicemrs > 1) {
            Log.ERROR("Multiple vicemasters! Cluster state: " + this.toString());
            errors++;
        }
        
        if (errors > 0) 
            return false;
        else
            return true;
    }

    /* Get access to a ClusterNode object by node ID
     */
    public ClusterNode getNode (int id) {
        String name = ClusterNode.id2hostname(id);
        return getNode(name);
    }

    /* Get access to the current master node
     */
    public ClusterNode getMaster () {
        Iterator nodeList = nodes.values().iterator();
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = (ClusterNode)nodeList.next();
            if (node.isMaster) return node;
        }
        return null; // never happens if hasExpectedState OK
    }

    /* Get access to the current vicemaster node
     */
    public ClusterNode getVice () {
        Iterator nodeList = nodes.values().iterator();
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = (ClusterNode)nodeList.next();
            if (node.isVice) return node;
        }
        return null; // never happens if hasExpectedState OK
    }

    /* Get access to a ClusterNode object by hostname
     */
    public ClusterNode getNode (String name) {
        return (ClusterNode)nodes.get(name);
    }
    
    public Collection getAllNodes() {
	return (Collection)nodes.values();
    }

    /** Set expectations for a given node to be "down" (not in cluster).
     */
    public void setNodeDown(String nodeName) {
        ClusterNode node = (ClusterNode)nodes.get(nodeName);
        node.setBeDown(true);
    }

    /** Set expectations for a given node to be "up" (in cluster).
     */
    public void setNodeUp(String nodeName) {
        ClusterNode node = (ClusterNode)nodes.get(nodeName);
        node.setBeDown(false);
    }

    /** Set expectations for master failover.
     *
     *  The current master node is expected to leave the master role,
     *  and the current vicemaster node is expected to become master.
     *  Some other node is supposed to become vicemaster.
     *
     *  Note: no node is expected to go down. Use setNodeDown() if needed.
     */
    public void setMasterFailover() {
        masterFailover = true;
        setViceFailover();
    }

    /** Set expectations for vicemaster failover.
     *
     *  The current vicemaster node is expected to leave the vice role,
     *  and some other node is expected to become vicemaster.
     *
     *  Note: no node is expected to go down. Use setNodeDown() if needed.
     *  Note: setViceFailover() is always called by setMasterFailover().
     */
    public void setViceFailover() {
        viceFailover = true;
    }

    /** Return human-readable string with cluster membership.
     */
    public String toString() {
        // [ 101 102MASTER 103VICE 104 105DOWN 106 107DOWN 108 ]
        StringBuffer printState = new StringBuffer();
        printState.append("[ ");
        Iterator nodeList = nodes.values().iterator();
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = (ClusterNode)nodeList.next();
            printState.append(node.getState());
            printState.append(" ");
        }
        printState.append("]");
        return printState.toString();
    }

    /** Return a subset of the node table - all alive nodes.
     */
    public List getLiveNodes() {
        List liveNodes = new ArrayList();
        Iterator nodeList = nodes.values().iterator();
        for (int i = 1; i <= numNodes; i++) {
            ClusterNode node = (ClusterNode)nodeList.next();
            if (node.isAlive()) {
                liveNodes.add(node);
            }
        }
        return liveNodes; 
    }

    /** Set expectations for quorum.
     */
    public void setQuorum (boolean setTo) {
        quorum = setTo; 
    }

    /** Set all disks online on all live nodes to gain quorum.
     */
    public boolean gainQuorumMax() {
        boolean q = setDiskCount(ALL_NODES, HoneycombTestConstants.DISKS_PER_NODE);
        if (q)
            setQuorum(true);
        return q;
    }

    /** Set just enough disks online on one node to gain quorum.
     */
    public boolean gainQuorumMin() {
        int disks = (int) Math.ceil( HoneycombTestConstants.DISKS_PER_NODE * numNodes *
                                     HoneycombTestConstants.QUORUM_PERCENT );
        boolean q = setDiskCount(1, disks);
        if (q)
            setQuorum(true);
        return q;
    }

    /** Set all disks offline on all live nodes to lose quorum.
     */
    public boolean loseQuorumMax() {
        boolean q = setDiskCount(ALL_NODES, 0);
        if (q)
            setQuorum(false);
        return q;
    }

    /** Disable just enough disks on one node to lose quorum.
     *  Assumes that gainQuorumMin() has been called - may not work otherwise.
     */
    public boolean loseQuorumMin() {
        int disks = (int) Math.ceil( HoneycombTestConstants.DISKS_PER_NODE * numNodes *
                                     HoneycombTestConstants.QUORUM_PERCENT ) - 1;
        boolean q = setDiskCount(1, disks);
        if (q)
            setQuorum(false);
        return q;
    }

    /** Make the cluster gain/lose quorum by setting DISKS online on NODES.
     *  Returns true on success, false on failure, errors are logged.
     */
    public boolean setDiskCount(int nodes, int disks) {
        List todo = getLiveNodes();
        if (nodes > todo.size()) {
            Log.ERROR("Too many nodes requested: " + nodes + " - count of live nodes: " + todo.size());
            return false;
        } 
        if (nodes == ALL_NODES)
            nodes = todo.size();

        int targetDisks = nodes * disks; 
        int activeDisks = -1;

        Iterator nodeList = todo.iterator();
        for (int i = 0; i < todo.size(); i++) {
            ClusterNode node = (ClusterNode)nodeList.next();
            if (node.isAlive()) {
                try {
                    activeDisks = node.setDisks(disks);
                } catch (HoneycombTestException e) {
                    Log.ERROR("Failed to set disk count on node: " + node + " - error: " + e);
                    return false;
                }
            }
        }

        if (activeDisks < targetDisks) {
            Log.ERROR("Not enough disks online: " + activeDisks + " - requested: " + targetDisks);
            return false;
        } 
        if (activeDisks > targetDisks) {
            Log.ERROR("Too many disks online: " + activeDisks + " - requested: " + targetDisks);
            return false;
        }
        return true; // success
    }
    
    public int getMode(){
    	return mode;
    }

    public int getNumNodes() {
        return numNodes;
    }
    
    /*
     * If the mode is CMM_SINGLE or ADMINVIP is set
     * to the internal IP, then I must be running on 
     * the cheat 
     */
    private boolean runningOnCheat() {
	if (mode == CMM_SINGLE || adminVIP.equals("10.123.45.200")) {
	    return true;
	} else {
	    return false;
	}
    }
    
    public boolean verifiedAndExpectedState (boolean checkMastership) {
	Iterator iter = getAllNodes().iterator();
	Log.INFO("About to list cluster node state before CMM check");
	while (iter.hasNext()) {
	    Log.INFO(((ClusterNode)iter.next()).getAllStateInfo());
	}
	try {
	    updateClusterState();
	} catch (HoneycombTestException hte) {
	    Log.ERROR("Failed CMM verification");
	    Log.ERROR(hte.getMessage());
	    return false;
	}
	
	iter = getAllNodes().iterator();
	Log.INFO("About to list cluster node state before state check");
	while (iter.hasNext()) {
	    Log.INFO(((ClusterNode)iter.next()).getAllStateInfo());
	}
	
	if (!hasExpectedState(checkMastership)) {
	    Log.ERROR("Cluster does not have expected state.");
	    return false;
	}
	return true;
	   
    }
    
}
