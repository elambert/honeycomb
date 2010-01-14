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


package com.sun.honeycomb.hctest.rmi.spsrv.srv;

import com.sun.honeycomb.hctest.rmi.spsrv.common.*;
import com.sun.honeycomb.hctest.rmi.nodesrv.common.*;
import com.sun.honeycomb.hctest.rmi.nodesrv.clnt.*;

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.test.util.*;

//import com.sun.honeycomb.config.ClusterConfig;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
//import com.sun.honeycomb.coordinator.Context;
//import com.sun.honeycomb.layout.LayoutClient;
//import com.sun.honeycomb.admin.ClusterManager;
//import com.sun.honeycomb.admin.DiskFRU;
//import com.sun.honeycomb.admin.MoboFRU;
//import com.sun.honeycomb.admin.FRU;
//import com.sun.honeycomb.admin.UnknownFRUException;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.io.*;
import java.util.*;

import java.util.logging.Logger;

public class SPSrvService extends UnicastRemoteObject implements SPSrvIF {

    private final String DB_ROOT = "/cluster_db";

    private final int IP_TIMEOUT = 3000; // msec, probably a config in kernel

    RunCommand shell = new RunCommand();

    public int num_nodes = HoneycombTestConstants.MAX_CLUSTER;

    public NodeSrvClnt[] nodeClients = 
                            new NodeSrvClnt[HoneycombTestConstants.MAX_CLUSTER];

    String nodeNames[] = new String[HoneycombTestConstants.MAX_CLUSTER];
    int node_status[] = new int[HoneycombTestConstants.MAX_CLUSTER];
    String node_uptime[] = new String[HoneycombTestConstants.MAX_CLUSTER];

    int master = -1;
    int vicemaster = -1;

    private long start_time = System.currentTimeMillis();

    private static final Logger LOG =
                                Logger.getLogger(SPSrvService.class.getName());

    public SPSrvService(int max_nodes) throws RemoteException {

        super();
        LOG.info("SPSrvService is initializing");

        if (max_nodes != -1)
            num_nodes = max_nodes;

        // build node IP list
        for (int i=0; i<num_nodes; i++) {
            nodeNames[i] = HoneycombTestConstants.BASE_IP + (100 + i + 1);
        }
        checkCluster("startup");
        labelNonExistentNodes();
        updateState();
        LOG.info("SPSrvService initialized");
    }

    private void labelNonExistentNodes() {
        //
        //  mark the hopefully nonexistent nodes as such 
        //  to avoid delays in repeated attempts to contact
        //  them.
        //
        int max_node = 0;
        for (max_node=num_nodes-1; max_node>-1; max_node--) {
            if (node_status[max_node] != SPSrvConstants.NODE_INACCESSIBLE)
                break;
        }

        //
        //  here we make the flying leap of faith that the
        //  cluster will have 4, 8 or 16 nodes.
        //
        if (max_node == 3  ||  max_node == 7) {
            LOG.warning("MARKING NODES > " + (max_node+1) + " NONEXISTENT");
            for (max_node++; max_node<num_nodes; max_node++)
                node_status[max_node] = SPSrvConstants.NODE_ASSUMED_NONEXISTENT;
        }
    }

    private void markNodeServerDown(int i) {
        node_status[i] = SPSrvConstants.NODE_NO_SERVER;
        nodeClients[i] = null;
    }

    private synchronized int initNodeClient(int i) {

        int stat = node_status[i];
        if (nodeClients[i] != null)
            return stat;

        //
        //  screen out nodes that appeared not to be present
        //  in the cluster at startup
        //
        if (stat == SPSrvConstants.NODE_ASSUMED_NONEXISTENT)
            return stat;

        long rmi_time = 0, ping_time = 0;
        long t1 = 0;

        //
        //  time is a concern here. when the node is up, rmi takes
        //  1-2 ms, while ping takes 6-8 ms. when the node is down,
        //  both can take 3 seconds, presumably due to the sp's IP 
        //  stack settings. hence we try rmi 1st here, and at startup 
        //  note the max accessible node to prevent unnecessary wait 
        //  time when screening.
        //

        String ip = nodeNames[i];
        try {
            t1 = System.currentTimeMillis();
            nodeClients[i] = new NodeSrvClnt(ip);
            //
            //  node rmi server is up
            //
            rmi_time = System.currentTimeMillis() - t1;
            stat = SPSrvConstants.NODE_HAS_SERVER;
        } catch (Exception e) {

            rmi_time = System.currentTimeMillis() - t1;

            //
            //  node rmi server is down - what about the host?
            //
            stat = SPSrvConstants.NODE_NO_SERVER;

            if (rmi_time >= IP_TIMEOUT) {

                //
                //  ping would take the same 3 sec+
                //
                stat = SPSrvConstants.NODE_INACCESSIBLE;
            } else {

                //
                //  try ping to be sure node is accessible
                //
                try {
                    t1 = System.currentTimeMillis();
                    shell.ping(ip);
                    ping_time = System.currentTimeMillis() - t1;
                    //
                    //  node is up
                    //
                    LOG.info("no server on " + (101 + i) + ": " + e);
                } catch (Exception e2) {
                    //
                    //  node is down/off-net
                    //
                    ping_time = System.currentTimeMillis() - t1;
                    stat = SPSrvConstants.NODE_INACCESSIBLE;
                }
            }
        }

        //
        //  skip logging node down repeatedly
        //
        if (node_status[i] == stat  &&  
                   stat == SPSrvConstants.NODE_INACCESSIBLE)
            return stat;

        node_status[i] = stat;
        LOG.info("node " + i + " rmi_time: " + rmi_time + 
                                "  ping: " + ping_time);
        return stat;
    }

    /**
     *  Check all nodes.
     */
    private void checkCluster(String msg) {

        LOG.info("checkCluster(" + msg + ")");

        int ok = 0;
        int inaccessible = 0;
        int no_server = 0;
        int nonexistent = 0;

        for (int i=0; i<num_nodes; i++) {
            switch(initNodeClient(i)) {
                case SPSrvConstants.NODE_HAS_SERVER:
                    ok++; break;
                case SPSrvConstants.NODE_NO_SERVER:
                    no_server++; break;
                case SPSrvConstants.NODE_INACCESSIBLE:
                    inaccessible++; break;
                case SPSrvConstants.NODE_ASSUMED_NONEXISTENT:
                    nonexistent++; break;
                default:
                    LOG.severe("unexpected status: " + node_status[i]);
                    System.exit(1);
            }
        }
        LOG.info("checkCluster(" + msg + ") ok_nodes " + ok + 
                                "  no_server " + no_server +
                                "  inaccessible " + inaccessible);
    }


    private int pickRandomNode(boolean fast, String msg) 
                                                        throws RemoteException {

        //
        //  be comprehensive or not
        //
        if (!fast)
           checkCluster(msg);

        //
        //  inventory available nodes
        //
        int[] existingNodes = new int[nodeClients.length];
        int ct = 0;
        for (int i=0; i<nodeClients.length; i++) {
            if (nodeClients[i] != null)
                existingNodes[ct++] = i;
        }
        if (ct == 0) {
            //
            //  if this was a quickie, try to get connections
            //
            if (fast)
                return pickRandomNode(false, msg);
            throw new RemoteException("no node servers accessible");
        }

        //
        //  pick an 'up' node at random
        //
        try {
            int ix = RandomUtil.randIndex(ct);
            return existingNodes[ix];
        } catch (Exception e) {
            throw new RemoteException("getting random index", e);
        }
    }

    private int checkNode(int node, String msg) throws RemoteException {
        if (node < 0) {
            //
            //  handle general cases
            //
            switch (node) {
                case SPSrvConstants.RANDOM_NODE:
                    return pickRandomNode(true, msg);
                case SPSrvConstants.RANDOM_NODE_FULLDECK:
                    return pickRandomNode(false, msg);
                default:
                    throw new RemoteException("unsupported node def < 0: " + 
                                                                      node);
            }
        } 
        if (node >= num_nodes) {
            throw new RemoteException("Bad node: " + node);
        }
        if (nodeClients[node] == null) {
            if (initNodeClient(node) != SPSrvConstants.NODE_HAS_SERVER) {
                throw new RemoteException("can't get server: " + node +
                                " status=" +
                                SPSrvConstants.nodeStatus(node_status[node]));
            }
        }
        return node;
    }

    public void updateState() {
        for (int i=0; i<num_nodes; i++) {
            if (nodeClients[i] == null)
                initNodeClient(i);
            if (nodeClients[i] == null) {
                node_uptime[i] = "no client/connection\n";
                continue;
            }
            try {
                node_uptime[i] = nodeClients[i].uptime();
            } catch (Exception e) {
                LOG.info("uptime try1: " + e);
                //
                //  try for fresh connection
                //
                markNodeServerDown(i);
                initNodeClient(i);
                if (nodeClients[i] == null) {
                    node_uptime[i] = "no client/connection\n";
                    continue;
                }
                try {
                    node_uptime[i] = nodeClients[i].uptime();
                } catch (Exception e2) {
                    markNodeServerDown(i);
                    LOG.info("uptime try2: " + e2);
                    node_uptime[i] = e2.toString() + "\n";
                    //e2.printStackTrace();
                }
            }
        }
    }


    public String spStatus() throws RemoteException {
        StringBuffer sb = new StringBuffer();
        sb.append("SP Server status:  uptime ");
        sb.append(Long.toString(
            (System.currentTimeMillis() - start_time) /60000)).append(" min\n");
        sb.append("max nodes assumed: " + num_nodes).append("\n");
        sb.append("\tnode\tstatus\t\tuptime\tcmds\n");
        for (int i=0; i<num_nodes; i++) {
            sb.append("\t").append(Integer.toString(i+1)).append("\t");
            sb.append(SPSrvConstants.nodeStatus(node_status[i]));
            if (nodeClients[i] != null) {
                sb.append("\t").append(Long.toString(
                       System.currentTimeMillis() - nodeClients[i].start_time));
                sb.append("\t").append(Integer.toString(nodeClients[i].cmds));
            }
            sb.append("\n");
        }
        sb.append("uptime:\n");
        for (int i=0; i<num_nodes; i++) {
            sb.append(Integer.toString(i+1)).append("\t");
            if (node_uptime[i] == null)
                sb.append("not polled\n");
            else
                sb.append(node_uptime[i]);
        }
        return sb.toString();
    }

    public String nodeStatus(int node) throws RemoteException {
        if (node < 0) {
            node *= -1;
            markNodeServerDown(node);
        }
        checkNode(node, "nodeStatus");
        return nodeClients[node].uptime();
    }

    /**
     *  Allow tests to make sure that nodes are connected.
     */
    public CmdResult checkNodes(boolean comprehensive) throws RemoteException {
        if (comprehensive) {
            for (int i=0; i<num_nodes; i++) {
                if (node_status[i] == SPSrvConstants.NODE_ASSUMED_NONEXISTENT)
                    node_status[i] = SPSrvConstants.NODE_INACCESSIBLE;
            }
        }
        CmdResult cr = new CmdResult();
        long t1 = System.currentTimeMillis();
        checkCluster("checkNodes");
        cr.time = System.currentTimeMillis() - t1;

        if (comprehensive)
            labelNonExistentNodes();

        cr.count = 0;
        for (int i=0; i<num_nodes; i++) {
            if (node_status[i] == SPSrvConstants.NODE_HAS_SERVER)
                cr.count++;
        }
        return cr;
    }

    /**
     *  Test harness admin cmd to stop node rmi servers.
     *  Since we can't just pkill java w/out killing HC.
     */
    public void shutdownNodeServers() throws RemoteException {
        for (int i=0; i<num_nodes; i++) {

            //
            //  try to connect to any node servers that were down
            //
            if (nodeClients[i] == null) {
                if (initNodeClient(i) != SPSrvConstants.NODE_HAS_SERVER) {
                    LOG.info("shutdownNodeServers: not up: " + i);
                    continue;
                }
            }

            try {
                nodeClients[i].shutdown();
                LOG.info("shutdownNodeServers: ok: " + i);
            } catch (Exception e) {
                // skip for now
                LOG.warning("caught " + e);
            }
            markNodeServerDown(i);
        }
    }

    /**
     *  Log msg to syslog.
     */
    public void logMsg(String msg) throws RemoteException {
        // System.err.println("logMsg: " + msg);
        LOG.info(msg);
    }
    static int remotelog = 0;
    public void logMsg(String msg, int node) throws RemoteException {
        checkNode(node, "logMsg"); // throws if no access
        try {
            nodeClients[node].logMsg(msg);
        } catch (Exception e) {
            // retry
            markNodeServerDown(node);
            checkNode(node, "logMsg"); // throws if no access
            nodeClients[node].logMsg(msg);
        }
    }

    /**
     *  Get whitebox cluster config. XXX from which node(s)?
     */
    public HClusterConfig getConfig() throws RemoteException {
        return null;
    }

    /**
     *  Get admin cluster state.
     */
    public HClusterState getClusterState(ClusterTestContext c) 
                                                        throws RemoteException {
        if (c.adminVIP == null)
            throw new RemoteException("adminVIP is null");
        HClusterState hc = new HClusterState();

        //
        //  get admin info for whole cluster
        //
        try {
            String admin = "admin@" + c.adminVIP;
            hc.version = shell.sshCmd(admin, "version");
            hc.df = shell.sshCmd(admin, "df");
            hc.sysstat = shell.sshCmd(admin, "sysstat");
            hc.hwstat = shell.sshCmd(admin, "hwstat");
        } catch (Exception e) {
            throw new RemoteException("getting admin state", e);
        }

        //
        //  TODO - when # of nodes known, get per-node info
        //
        return hc;
    }

    /**
     *  Get whitebox & API info on oid.
     */
    public HOidInfo getOidInfo(String oid, boolean thisOnly)
                                                        throws RemoteException {
 
        // Make sure we have an oid
        if (oid == null) {
            throw new RemoteException("null oid");
        }
        int node = pickRandomNode(true, "getOidInfo"); // any node will do

        return nodeClients[node].getOidInfo(oid, thisOnly);
    }


    /**
     *  Delete the given number of fragments (renames them actually).
     */
    public void deleteFragments(List fragments) throws RemoteException {
        // Make sure we have a list
        if (fragments == null) {
            throw new RemoteException("null fragments list");
        }
        LOG.info("(delete " + fragments.size() + ")");

        int node = pickRandomNode(true, "deleteFragments"); // any node will do

        nodeClients[node].deleteFragments(fragments);

        // before returning, make sure all fragments have disappeared from the
        // perspective of each node.
        for (int i = 0; i < num_nodes; i++) {
            if (nodeClients[i] != null) {
                nodeClients[i].waitForFragments(fragments, false);
            }
        }
    }

    /**
     *  Restore moved fragments, given a list of originals.
     */
    public void restoreFragments(List fragments) throws RemoteException {
        // Make sure we have a list
        if (fragments == null) {
            throw new RemoteException("null fragments list");
        }
        LOG.info("restore " + fragments.size());

        int node = pickRandomNode(true, "restoreFragments"); // any node will do
        nodeClients[node].restoreFragments(fragments);

        // before returning, make sure all fragments have re-appeared from the
        // perspective of each node.
        for (int i = 0; i < num_nodes; i++) {
            if (nodeClients[i] != null) {
                nodeClients[i].waitForFragments(fragments, true);
            }
        }
     }

/*
    public void corruptFragments(List fragments, int nbytes, byte mask)
                                                        throws RemoteException {
    }
*/

    public void disableNode(int nodeid) throws RemoteException {
        throw new RemoteException("disableNode - not impl");
    }

    public void disableDisk(int nodeid, String disk) throws RemoteException {
        throw new RemoteException("disableDisk- not impl");
    }
/*
    public void disableFRU (FRU fru) throws RemoteException {
        throw new RemoteException("disableFRU - not impl");
    }
*/
    public void injectServiceProblem(int node, String svc_name, String action)
                                                        throws RemoteException {
        node = checkNode(node, "injectServiceProblem"); // throws if no access
        nodeClients[node].injectServiceProblem(svc_name, action);
    }

    public CmdResult rebootNode(int node, boolean fast) throws RemoteException {

        CmdResult cr = new CmdResult();

        cr.node = checkNode(node, "rebootNode"); // throws if no access

        //
        //  it is hard to tell if a reboot succeeded,
        //  for now the fact that the node was accessible
        //  will suffice
        //
        cr.pass = true; 

        LOG.info("rebootNode " + cr.node + " fast=" + fast);
        /*
        // this would work if node server was down; slower
        // to ssh, but would it matter?
        String n = "hcb" + (100 + cr.node + 1);
        try {
            shell.sshCmd(n, "reboot -f");
        } catch (Exception e) {
            throw new RemoteException("ssh reboot", e);
        }
        */
        //
        //  a thread is needed since the reboot won't return
        //  until maybe some timeout
        //
        RebootThread t = new RebootThread(nodeClients[cr.node], cr.node, fast);
        t.start();

        return cr;
    }

    class RebootThread extends Thread {

        NodeSrvClnt np;
        int node;
        boolean fast;

        public RebootThread(NodeSrvClnt np, int node, boolean fast) {
            this.np = np;
            this.node = node;
            this.fast = fast;
        }
        public void run() {
            try {
                np.reboot(fast);
                LOG.info("REBOOT thread on " + node + " completed");
            } catch (Exception e) {
                LOG.severe("REBOOT " + node + ": " + e);
                //e.printStackTrace();
            }
            markNodeServerDown(node);
        }
    }

}

