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



package com.sun.honeycomb.cm;

import com.sun.honeycomb.cm.NodeView;
import com.sun.honeycomb.cm.node_mgr.NodeConfigParser;
import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.util.CMMUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Vector;

public class NodeMgrVerifier
{
    private int numNodes;           // how many nodes in the cluster
    private static Map clusterView; // nodeName -> NodeView data structure
    private NodeView baseView;      // reference node table (known good state from CMM)
    private boolean consistent;     // did verify() reveal mailbox state consistent with CMM state?
    private boolean factory;
    private boolean factory_bomb = false; // setup error that disqualifies factory test
    protected int retries;          // count of refresh/verify retries
    private int maxRetries;
    private int runlevel;           // expected running mode: maintenance or data
    private boolean isMaintenance;  // actual running mode
    private boolean hasQuorum;      // actual quorum state (subtly different from maint/data mode)
    private NodeConfigParser nodeConfig; // nominal set of services from node_config.xml

    public NodeMgrVerifier(int _numNodes, NodeView _cmmView, int _doRetries, boolean _quorum, boolean _factory) {
        numNodes = _numNodes;
        baseView = _cmmView; // known consistent cluster state according to CMM
        maxRetries = _doRetries;
        factory = _factory;
        clusterView = new LinkedHashMap(numNodes);
        for (int i = 1; i <= numNodes; i++) {
            int nodeId = 100 + i;
            String nodeName = node(i);
            int maxId = 100 + numNodes;
            clusterView.put(nodeName, new NodeView(nodeId, nodeName, maxId));
        }
        consistent = false;
        retries = 0;
        
        // Copy node_config.xml from a node in the cluster
        copyNodeConfig();
        
        nodeConfig = NodeConfigParser.getInstance();
        if (nodeConfig == null) {
            if (_factory) {
                System.out.println("ERROR: Failed to parse node_config.xml, factory test failed");
                factory_bomb = true;
            } else
                System.out.println("WARNING: Failed to parse node_config.xml, will not check state of services");
        } else
            runlevel = _quorum ? nodeConfig.DATA_MODE : nodeConfig.MAINTENANCE_MODE;
    }
    
    private void copyNodeConfig(){
    	Iterator it = clusterView.keySet().iterator();
    	boolean copied = false;
    	while (it.hasNext()) {
    		String nodeName = (String)it.next();
    		if (CMMUtils.ping(nodeName)){
    			if (CMMUtils.execute("scp " + nodeName + ":/opt/honeycomb/share/node_config.xml /opt/test/config/")){
    				System.out.println("Copied node_config.xml from " + nodeName);
    				copied = true;
    				break;
    			}
    		}
    	}
    	
    	if (!copied)
    		System.out.println("WARNING: Failed to copy recent node_config.xml from a node on the cluster!! you mayb have problems!!");
    }
    
    /** Get nodemgr_mailbox contents from given node, parse. 
     */
    public boolean refresh() {
        retries++;
        
        if (baseView == null) {
            raiseMailboxError("Base view unknown - all nodes down?");
            return false;
        }

        NODES: for (int i = 1; i <= numNodes; i++) {
            if (!baseView.isAlive(100 + i)) { 
                // ignore mailbox on dead nodes
                continue NODES;
            }

            NodeView mailboxView = (NodeView) clusterView.get(node(i));
            if (mailboxView == null) {
                raiseMailboxError("Node view not found: " + node(i));
                return false;
            }
            BufferedReader mailbox;
            try {
                mailbox = sshCmd(mailboxView.name, "/opt/honeycomb/bin/nodemgr_mailbox.sh");
            } catch (Exception e) {
                raiseMailboxError("ERROR: failed to read mailbox on node " + 
                                  mailboxView.name + "[ " + e.getMessage() + " ]");
                return false;
            }
            
            ArrayList nodes = new ArrayList(); // temp holder for parsed node table
            Vector services = new Vector();    // temp holder for running service set
            String line;
            Pattern p; // same regex is used to parse lines in all sections of output
            try { // eg: SOME [STUFF] AND [MORE][STUFF]
                p = Pattern.compile("[\\[ \\]]++"); // split on space and [ ]
            } catch (Exception e) { // invalid pattern
                raiseMailboxError("Pattern error in mailbox parsing on node " + i + ": " + e + " [" + mailbox + "]");
                return false;
            }

            int section = 0; // logical sections of mailbox output
            final int STATE_SECTION = 1;
            final int NODE_SECTION = 2;
            final int SERVICE_SECTION = 3;            

            LINES: while (true) {

                try {
                    line = mailbox.readLine();
                } catch (Exception e) {
                    raiseMailboxError("Mailbox reading error on node " + i + ": " + e.getMessage());
                    break LINES;
                }
                if (line == null) 
                    break LINES; // done

                if (line.trim().length() == 0)
                    continue LINES; // skip empty lines
                if (line.matches(".*STATE.*")) {
                    section = STATE_SECTION;
                    continue LINES; // skip heading
                } else if (line.matches(".*NODES.*")) {
                    section = NODE_SECTION;
                    continue LINES; // skip heading
                } else if (line.matches(".*SERVICES.*")) {
                    section = SERVICE_SECTION;
                    continue LINES; // skip heading
                } // otherwise go on to parse the line

                // if we are here, the line must have format of eg: STUFF [AND] [WHAT-NOT]
                String[] words = p.split(line);

                if (section == STATE_SECTION) {
                    if (words.length < 2) {
                        raiseMailboxError("Mailbox parsing error on node " + i + ": " + line + " [" + mailbox + "]");
                        continue NODES;
                    }
                    if (words[0].equalsIgnoreCase("Maintenance")) 
                        isMaintenance = Boolean.valueOf(words[1]).booleanValue();
                    if (words[0].equalsIgnoreCase("Quorum"))
                        hasQuorum = Boolean.valueOf(words[1]).booleanValue();
                    // other general info can be added to STATE section, ignored by this parser
                }

                if (section == SERVICE_SECTION) { 
                    // "MASTER-SERVERS[RUNNING]"
                    if (words.length < 3) {
                        raiseMailboxError("Mailbox parsing error on node " + i + ": " + line + " [" + mailbox + "]");
                        continue NODES;
                    }
                    if (nodeConfig.findJVM(words[0]) != null) {
                        // do nothing... don't know how to track JVMs just yet
                    } else if (nodeConfig.findSrv(words[0]) != null) {
                        Service srvDef = nodeConfig.findSrv(words[0]);
                        Service srv = new Service(srvDef);
                        if (!srv.setStatus(words[1]))
                            raiseMailboxError("Invalid service state on node " + i + ": " + line);
                        services.add(srv);
                    } else {
                        raiseMailboxError("Unknown service/JVM on node " + i + ": " + line + " [" + mailbox
                                          + "\nOur node_config.xml has [" + nodeConfig + "]");
                    }
                } 

                if (section == NODE_SECTION) {
                    // "101 [4] ALIVE MASTER 10.123.45.101"
                    if (words.length < 5) {
                        raiseMailboxError("Mailbox parsing error on node " + i + ": " + line + " [" + mailbox + "]");
                        continue NODES;
                    }
                    int id, numDisks;
                    try {
                        id = Integer.parseInt(words[0]);
                        numDisks = Integer.parseInt(words[1]);
                    } catch (Exception e) {
                        raiseMailboxError("Mailbox parsing error on node " + i + ": " + e + " [" + mailbox + "]");
                        continue NODES;
                    }
                    CMMApi.Node node = new CMMApi.Node();
                    node.nodeId = id;
                    node.activeDisks = numDisks;
                    if (words[2].equals("ALIVE")) node.isAlive = true;
                    if (words[3].equals("MASTER")) node.isMaster = true;
                    if (words[3].equals("VICEMASTER")) node.isViceMaster = true;
                    nodes.add(node);
                }
            }
            mailboxView.services = services;
            mailboxView.nodes = (CMMApi.Node[]) nodes.toArray(new CMMApi.Node[0]);
        }
        return true;
    }

    private void raiseMailboxError(String msg) {
        if (retries == maxRetries) {
            System.out.println(msg); // complain on last attempt
        }
        consistent = false;
    }

    private void raiseServiceError(String msg) {
        raiseMailboxError(msg + "\n" + "NODE_CONFIG SERVICES: " + nodeConfig);
    }

    /** Verify that mailboxes on all alive nodes match the known cluster state according to CMM.
     *
     *  Returns zero if mailbox state is consistent with CMM state, non-zero error count if there are discrepancies.
     */
    public int verify () {

        if (factory_bomb)
            return 100;

        if (baseView == null) {
            raiseMailboxError("Base view unknown - all nodes down?");
            return 100;
        }

        int errors = 0; // reset error counter

        NODES: for (int i = 1; i <= numNodes; i++) {
            if (!baseView.isAlive(100+i)) { 
                // ignore mailbox on dead nodes
                continue NODES;
            }
            NodeView mailboxView = (NodeView) clusterView.get(node(i));
            if (mailboxView == null) {
                raiseMailboxError("No mailbox data on node " + node(i));
                errors++;
                continue NODES;
            }
            if (!cmpNodes(mailboxView, baseView)) {
                errors++;
            }
            boolean isMaster = (mailboxView.id == baseView.getMaster());
            if (!cmpServices(mailboxView, isMaster)) {
                errors++;
            }
        }

        consistent = (errors == 0 ? true : false);
        return errors;
    }

    /** For a given node, compare state according to NodeMgr mailbox, to state according to CMM.
     * 
     *  Returns true if state is identical (incl. mastership), false if there are discrepancies.
     */
    public boolean cmpNodes(NodeView mailbox, NodeView cmm) {
        if (!mailbox.cmp(cmm)) {
            raiseMailboxError("State mismatch for node " + mailbox + ": NodeMgr Mailbox " + 
                              mailbox.showOneLiner() + " / CMM " + cmm.showOneLiner());
            return false;
        }
        return true;
    }

    public boolean cmpServices (NodeView mailbox, boolean isMaster) {
        boolean ok_srv = false;
        boolean ok_master = false;
        int srvState, masterState;

        srvState = nodeConfig.compareNormal(mailbox.services, runlevel);
        if (srvState == nodeConfig.MAINTENANCE_MODE) {
            if (isMaintenance || !hasQuorum)
                ok_srv = true; // valid state: enforced maintenance, or no quorum
            else 
                raiseServiceError("Node " + mailbox + " is in maintenance mode, but says: maint=" + 
                                  isMaintenance + " quorum=" + hasQuorum + "\n" + mailbox.showSrv());
        }
        if (srvState == nodeConfig.DATA_MODE) {
            if (!isMaintenance && hasQuorum)
                ok_srv = true; // valid state: data
            else
                raiseServiceError("Node " + mailbox + " is in data mode, but says: maint=" +
                                  isMaintenance + " quorum=" + hasQuorum + "\n" + mailbox.showSrv());
        }
        if (srvState == nodeConfig.SUBZERO_MODE)
            raiseServiceError("Node " + mailbox + " is missing services for maintenance mode: \n" + mailbox.showSrv());
        if (srvState == nodeConfig.HALFWAY_MODE && runlevel == nodeConfig.MAINTENANCE_MODE)
            raiseServiceError("Node " + mailbox + " is running too many services for maintenance mode: \n" + mailbox.showSrv());
        if (srvState == nodeConfig.HALFWAY_MODE && runlevel == nodeConfig.DATA_MODE)
            raiseServiceError("Node " + mailbox + " is missing services for data mode: \n" + mailbox.showSrv());
        if (srvState == nodeConfig.OVERBOARD_MODE)
            raiseServiceError("Node " + mailbox + " is running too many services for data mode: \n" + mailbox.showSrv());

        masterState = nodeConfig.compareMaster(mailbox.services, runlevel, isMaster); 
        if (masterState == nodeConfig.MASTER || masterState == nodeConfig.NON_MASTER)
            ok_master = true; // valid state
        else {
            if (masterState == nodeConfig.USURPER_MASTER)
                raiseServiceError("Node " + mailbox + " runs master services, but is not master: \n" + mailbox.showSrv());
            if (masterState == nodeConfig.MISSING_MASTER)
                raiseServiceError("Node " + mailbox + " is missing master services, but is master: \n" + mailbox.showSrv());
            if (masterState == nodeConfig.OVERBOARD_MASTER)
                raiseServiceError("Node " + mailbox + " is master but runs too many master services: \n" + mailbox.showSrv());
        }
        mailbox.setState(srvState, masterState); // remember

        return (ok_srv ? ok_master : false);
    }

    private BufferedReader sshCmd(String host, String cmd) {
        BufferedReader out;
        try {
        	Process proc = Runtime.getRuntime().exec("/usr/bin/ssh -o StrictHostKeyChecking=no -q " + host + " " + cmd);
        	int rc = proc.waitFor();
            if ( rc != 0)
             	out = new BufferedReader(new StringReader("Failed to run cmd [ " + cmd + " ] on host " + host+ " failed with return code: " + rc));
            else  
            	out = new BufferedReader(new InputStreamReader(proc.getInputStream()));           
        } catch (Exception e) {
            out = new BufferedReader(new StringReader("Failed to run cmd [ " + cmd + " ] on host " + host));
        }
        return out;
    }

    private String node(int num) {
        int nodeNum = 100 + num;
        return "hcb" + nodeNum;
    }

    /** Output a list of nodes with their CMM state (from baseView), and state of services.
     *  If service state is wrong, output a full list of services for that node upfront.
     */
    public void report() {
        System.out.println("NODEMGR MAILBOX VIEW:");
        for (int i = 1; i <= numNodes; i++) {
            int nodeId = 100 + i;
            if (!baseView.isAlive(nodeId)) {
                System.out.println(nodeId + " dead");
            } else {
                NodeView node = (NodeView) clusterView.get(node(i));
                System.out.println(node.showMailbox());
            }
        }
    }
}
