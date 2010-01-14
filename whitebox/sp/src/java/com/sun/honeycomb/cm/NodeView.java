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

import com.sun.honeycomb.cm.node_mgr.NodeConfigParser;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.node_mgr.Service;
import java.util.Vector;

/** NodeView represents CMM node table in a form used by Verifier.
 * 
 *  Provides comparison method for cross-node verification.
 */

public class NodeView {
    protected String name;         // hcb101
    protected int id;              // 101
    protected int maxId;           // ignore nodes above this ID
    protected boolean okStatus;    // true if CMM is running
    protected String errMsg;       // if status is not OK
    protected CMMApi.Node[] nodes; // view of all nodes
    protected Vector services;       // view of local services
    protected int srvState;        // state of normal services
    protected int masterState;     // state of master services
    
    public NodeView(int _nodeId, String _host, int _maxId) {
        id = _nodeId;
        name = _host;
        maxId = _maxId;
        okStatus = true;
        srvState = 0;
        masterState = 0;
        services = new Vector();
    }
    
    public NodeView(NodeView other) {
        id = other.id;
        name = other.name;
        maxId = other.maxId;
        okStatus = other.okStatus;
        if (other.nodes != null) {
            nodes = new CMMApi.Node[other.nodes.length];
            for (int i = 0; i < other.nodes.length; i++) {
                CMMApi.Node clone = new CMMApi.Node();
                clone.init(other.nodes[i]);
                nodes[i] = clone;
            }
        }
        services = new Vector();
    }

    public int nodeId() {
        return(id);
    }

    public void setState(int srv, int master) {
        srvState = srv;
        masterState = master;
    }
    
    public String toString() {
        return name;
    }
    
    public String showOneLiner() {
        StringBuffer sb = new StringBuffer(name);
        if (!okStatus) {
            sb.append(" DEAD: " + errMsg);
            return sb.toString();
        } else {
            sb.append(" VIEW: ");
        }
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() > maxId) continue; // skip high node IDs
            sb.append(nodes[i].nodeId());
            if (nodes[i].isAlive()) {
                sb.append("A[" + nodes[i].getActiveDiskCount() + "]");
            } else {
                sb.append("D[_]");
            }
            if (nodes[i].isMaster()) {
                sb.append("M");
            } else {
                sb.append("_");
            }
            if (nodes[i].isViceMaster()) {
                sb.append("V");
            } else {
                sb.append("_");
            }
            sb.append(" ");
        }
        return sb.toString();
    }
    
    public String show() {
        StringBuffer sb = new StringBuffer(name);
        if (!okStatus) {
            sb.append(" DEAD: " + errMsg);
            return sb.toString();
        }
        sb.append(" VIEW: \n");
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() > maxId) continue; // skip high node IDs
            sb.append(nodes[i].nodeId());
            if (nodes[i].isAlive()) {
                sb.append(" [" + nodes[i].getActiveDiskCount() + "]");
                sb.append(" alive");
            } else {
                sb.append(" dead");
            }
            if (nodes[i].isMaster()) sb.append(" master");
            if (nodes[i].isViceMaster()) sb.append(" vicemaster");
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Returns CMM state of this node, and if alive, state of services on the node.
     */
    public String showMailbox() {
        StringBuffer sb = new StringBuffer();
        sb.append(nodeId());
        if (!okStatus) {
            sb.append(" dead");
            return sb.toString(); // done
        }
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() == nodeId()) { // this node
                sb.append(" [" + nodes[i].getActiveDiskCount() + "]");
                sb.append(" alive");
                if (nodes[i].isMaster()) 
                    sb.append(" master");
                else if (nodes[i].isViceMaster()) 
                    sb.append(" vicemaster");
                else
                    sb.append(" node"); // just-a-node
            }
        }
        sb.append(" srv:" + NodeConfigParser.printSrvState(srvState));
        sb.append(" msr:" + NodeConfigParser.printMasterState(masterState));
        return sb.toString();
    }

    /** Returns list of services and their state.
     */
    public String showSrv() {
        StringBuffer sb = new StringBuffer();
        sb.append("NODE " + id + " SERVICES: ");
        for (int i = 0; i < services.size(); i++) {
            Service srv = (Service) services.get(i);
            sb.append(srv.toString() + " ");
        }
        return sb.toString();
    }
    
    /** Compare node views from this and another node.
     *
     *  Returns true if state matches, false if differs, so you can call:
     *  if (!thisNode.cmp(other)) thisIsBad;
     */
    public boolean cmp(NodeView other) {
        
        if (!okStatus) { // this node is dead
            if (!other.okStatus) { // the other one is dead too, nothing to compare
                return true;
            } else {
                for (int j = 0; j < other.nodes.length; j++) {
                    if (id == other.nodes[j].nodeId()) { // other should think we are dead
                        return other.nodes[j].isAlive() ? false : true;
                    }
                    return false; // the other node doesn't know about us at all?
                }
            }
        }
        
        if (!other.okStatus) { // then we should think it's dead
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].nodeId() == other.id) {
                    if (nodes[i].isAlive()) {
                        //                            System.out.println("ERROR: Dead " + other.id + 
                        //                 " reported alive by " + this.id);
                        return false;
                    } else {
                        //                           System.out.println("WARNING: Dead " + other.id + 
                        //                 " reported dead by " + this.id);
                        return true;
                    }
                }
            }
            return false; // we don't know about the other node?
        }
        
        // let's check if we report the other node dead when it's not
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() == other.id) {
                if (!nodes[i].isAlive()) {
                    //                        System.out.println("ERROR: Alive " + other.id + 
                    //                   " reported dead by " + this.id);
                    return false;
                }
            }
        }
        
        // if both nodes have node view tables, let's compare
        int cmpErrors = 0;
        for (int i = 0; i < this.nodes.length; i++) {
            for (int j = 0; j < other.nodes.length; j++) {
                if (this.nodes[i].nodeId() == other.nodes[j].nodeId()) { // cmp state
                    if (this.nodes[i].isAlive() != other.nodes[j].isAlive() ||
                        this.nodes[i].isMaster() != other.nodes[j].isMaster() ||
                        this.nodes[i].isViceMaster() != other.nodes[j].isViceMaster()) {
                        //                            System.out.println("ERROR: Mismatch between " + this.id + "/" 
                        //                                               + other.id + " on " + this.nodes[i].nodeId());
                        cmpErrors++;
                    }
                }
            }
        }
        if (cmpErrors > 0) return false;
        return true;
    }
    
    /* Verify that this node is aware of who's master and vicemaster.
     */
    public boolean hasMaster() {
        return ((getMaster() != -1) &&
                (getViceMaster() != -1));
    }

    /* Who is the master? 
     */
    public int getMaster() {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isMaster())
                return nodes[i].nodeId();
        }
        return -1; // nobody is master
    }

    public int getViceMaster() {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isViceMaster())
                return nodes[i].nodeId();
        }
        return -1; // nobody is vicemaster
    }
    
    /* Am I a master node? 
     */
    public boolean isMaster() {
        if (getMaster() == id)
            return true;
        else
            return false;
    }

    /** Is a given node alive, according to our node table? 
     */
    public boolean isAlive(int id) {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() == id) {
                return nodes[i].isAlive();
            }
        }
        return false;
    }
}

