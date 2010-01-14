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
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.Node;
import com.sun.honeycomb.util.CMMUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Verify that CMM state is consistent across the cluster.
 *
 *  CMMVerifier uses CMM API to find out the view of nodes' state
 *  from each node in the cluster, then compares for consistency.
 *
 */

public class CMMVerifier {
    
    private int numNodes; // how many nodes in the cluster
    protected static Map clusterView; // nodeName -> NodeView data structure
    protected static Map lastView; // clusterView at last refresh time
    protected NodeView baseView; // reference state (if cluster is consistent)
    private int errors; // cumulative error count
    protected int retries; // count of refresh calls (we retry until maxRetries or consistent state)
    private int maxRetries;
    private boolean consistent; // verify() revealed consistent cluster state?
       
    public CMMVerifier(int nodeCount, int doRetries) {
        numNodes = nodeCount;
        maxRetries = doRetries;
        retries = 0;
        consistent = false;
        clusterView = new LinkedHashMap(numNodes);
        for (int i = 1; i <= numNodes; i++) {
            int nodeId = 100 + i;
            String nodeName = node(i);
            int  maxId = 100 + numNodes;
            clusterView.put(nodeName, new NodeView(nodeId, nodeName, maxId));
        }
        baseView = null; // don't have any data yet
    }

    public void refresh() {
        retries++;

        //        System.out.println("\n*** GETTING CMM STATE *** \n");

        lastView = new LinkedHashMap(numNodes);
        for (int i = 1; i <= numNodes; i++) { // remember previous state
            NodeView lastNode = (NodeView) clusterView.get(node(i));
            lastView.put(lastNode.name, new NodeView(lastNode));
        }

        for (int i = 1; i <= numNodes; i++) {
            NodeView cmmNode = (NodeView)clusterView.get(node(i));
            CMMApi api = null;

            try {
            	if (CMMUtils.ping(cmmNode.name)){
            		api = connectToApi(cmmNode);
            	} else {
            		cmmNode.okStatus = false;
            		cmmNode.errMsg = "Node not pingable: " + cmmNode.name;
            		continue;
            	}
            } catch (Error err) { // because in CMM.java failure to getAPI is an Error
                // Yes, you're not supposed to catch Errors in Java, but here I must.
                cmmNode.okStatus = false;
                cmmNode.errMsg = err.getMessage();
                //                System.out.println("WARNING: Found dead node " + cmmNode.id);
                continue;
            }
            
            if( null == api ) { // should never happen, but just in case
                cmmNode.okStatus = false;
                cmmNode.errMsg = "Got null CMMAPi object";
                //                System.out.println("WARNING: Found dead node " + cmmNode.id);
                continue;
            }

            try {
                cmmNode.nodes = api.getNodes();
                //                System.out.println("INFO: Refreshed state for node " + cmmNode.name);
            } catch (CMMException cme) { // getNodes throws CMMException
                cmmNode.okStatus = false;
                cmmNode.errMsg = cme.getMessage();
                //                System.out.println("WARNING: Found dead node " + cmmNode.id);
                continue;
            } 
        }
    }

    /** Checks node tables across nodes for consistency.
     *
     *  Returns zero on success (consistent, good cluster state), 
     *  -1 if state is consistent, but incorrect (no master/vice),
     *  positive error count if state is inconsistent.
     */
    public int verify() {
        
        //        System.out.println("\n*** CHECKING STATE CONSISTENCY *** \n");
        errors = 0; // reset error counter
        baseView = null; // will compare its view to everyone else's
        for (int i = 1; i <= numNodes; i++) {
            NodeView cmmNode = (NodeView)clusterView.get(node(i));
            if (cmmNode.okStatus) {
                baseView = cmmNode;
                break;
            }
        }
        if (baseView == null) {
            System.out.println("CMM: all nodes down");
            consistent = false;
            return 1;
        }
        /* Check that all nodes report the same cluster state. 
         */
        for (int i = 1; i <= numNodes; i++) {
            NodeView cmmNode = (NodeView)clusterView.get(node(i));
            if (cmmNode.id == baseView.id) continue; // don't compare with self
            if(!baseView.cmp(cmmNode)) {
                raiseCMMError("State mismatch between nodes: " + baseView + "/" + cmmNode);
                errors++;
            } 
        }

        /* Check that master and vicemaster are present.
           This check is required because during cluster startup, there is a time when 
           all nodes report the same state (they are all up), but elections haven't happened yet. 
           The verifier should not exit too early, it should wait for master/vice to be elected.
        */
        if (!baseView.hasMaster())
            if (errors == 0) errors = -1; // signifies state error

        consistent = (errors == 0 ? true : false);
        return errors;
    }

    private void raiseCMMError(String errMsg) {
        if (retries == maxRetries) // last attempt
            System.out.println(errMsg);
    }

    public void report() {
        if (consistent)
            reportOK();
        else
            reportErr();
    }

    /** This is the output when nodes report different states.
     *  Since cluster state is inconsistent, we print each node's view. 
     */
    private void reportErr() {
        System.out.println();
        // prints each node's state in one-liner output format
        for (int i = 1; i <= numNodes; i++) {
            NodeView cmmNode = (NodeView)clusterView.get(node(i));
            System.out.println(cmmNode.showOneLiner());
        }
        System.out.println();
    }

    /** This is the output when all nodes report the same state.
     *  This consistent state may be right or wrong.
     *  If master or vicemaster is missing, the state is certainly wrong.
     *  Otherwise the caller needs to verify whether this is the expected state.
     */
    private void reportOK() {
        for (int i = 1; i <= numNodes; i++) {
            NodeView cmmNode = (NodeView)clusterView.get(node(i));
            if (cmmNode.okStatus) { // print report once from alive node
                System.out.println(cmmNode.show() + "\n");
                return;
            }
        }
    }

    private String node(int num) {
        int nodeNum = 100 + num;
        return "hcb" + nodeNum;
    }

    protected CMMApi connectToApi(NodeView node) {
        return(CMM.getAPI(node.name));
    }
}
