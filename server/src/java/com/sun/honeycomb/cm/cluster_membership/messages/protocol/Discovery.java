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



package com.sun.honeycomb.cm.cluster_membership.messages.protocol;

import com.sun.honeycomb.cm.cluster_membership.messages.*;
import com.sun.honeycomb.cm.cluster_membership.Node;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.NodeTable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.Iterator;

/**
 * A Discovery message is a reliable notification sent
 * everytime the ring configuration changes.
 */
public class Discovery extends Message {

    private List deadNodes;
    private List aliveNodes;
    private Node masterNode;
    private Node viceMasterNode;
    private NodeStatus nodeStatus[];

    private class NodeStatus {
        final int nodeid;
        final int disksCount;
        
        NodeStatus(int _nodeid, int _disksCount) {
            nodeid = _nodeid;
            disksCount = _disksCount;
        }
    }
    
    public Discovery() {
        setBroadcast();
        arm(CMM.latencyTimeout());
        deadNodes  = new ArrayList(CMM.MAX_NODES);
        aliveNodes = new ArrayList(CMM.MAX_NODES);
        masterNode = null;
        viceMasterNode = null;
        nodeStatus = null;
    }
    
    public Iterator getAliveNodes() {
        return aliveNodes.iterator();
    }

    public Iterator getDeadNodes() {
        return deadNodes.iterator();
    }

    public Node getMasterNode() {
        return masterNode;
    }

    public void setMasterNode(Node node) {
        masterNode = node;
    }

    public Node getViceMasterNode() {
        return viceMasterNode;
    }

    public void setViceMasterNode(Node node) {
        viceMasterNode = node;
    }
    
    public int getDisksCount(Node node) {
        if (!NodeTable.isLocalNode(node) && nodeStatus != null) {
            for (int i = 0; i < nodeStatus.length; i++) {
                if (nodeStatus[i].nodeid == node.nodeId()) {
                    return nodeStatus[i].disksCount;
                }
            }
        }
        return node.getActiveDiskCount();
    }

    public void updateLocalNodeStatus() {
        Node node = NodeTable.getLocalNode();
        synchronized (aliveNodes) {
            if (!aliveNodes.contains(node)) {
                aliveNodes.add(node);
            } 
        }
        synchronized (deadNodes) {
            deadNodes.remove(node);
        }
        if (node.isMaster()) {
            masterNode = node;
        }
        if (node.isViceMaster()) {
            viceMasterNode = node;
        }
    }
    
    public void updateAllNodesStatus() {
        Node node = null;
        Iterator it = NodeTable.iterator();
        while (it.hasNext()) { 
            node = (Node) it.next();
            if (node.isAlive()) {
                synchronized (aliveNodes) {
                    if (!aliveNodes.contains(node)) {
                        aliveNodes.add(node);
                    } 
                }
                synchronized (deadNodes) {
                    deadNodes.remove(node);
                }
                if (node.isMaster()) {
                    masterNode = node;
                }
                if (node.isViceMaster()) {
                    viceMasterNode = node;
                }
            } else {
                synchronized(deadNodes) {
                    if (!deadNodes.contains(node)) {
                        deadNodes.add(node);
                    }
                }
                synchronized (aliveNodes) {
                    aliveNodes.remove(node);
                }
                
            }                
        }
    }        
    
    public void updateNodesInfo(Node connected) {
        Node node = null;
        Iterator it = NodeTable.iterator();
        while (it.hasNext()) { 
            node = (Node) it.next();
            if (NodeTable.compare(node, connected) < 0 ||
                (NodeTable.isLocalNode(connected) && node != connected)) {
                synchronized(deadNodes) {
                    if (!deadNodes.contains(node)) {
                        deadNodes.add(node);
                    }
                }
                synchronized (aliveNodes) {
                    aliveNodes.remove(node);
                }
            }
        }
    }

    public void copyInto(ByteBuffer buffer) throws CMMException {
        if (masterNode != null) {
            buffer.putInt(masterNode.nodeId());
        } else {
            buffer.putInt(-1);
        }
        if (viceMasterNode != null) {
            buffer.putInt(viceMasterNode.nodeId());
        } else {
            buffer.putInt(-1);
        }

        buffer.putInt(deadNodes.size());
        for (int i = 0; i < deadNodes.size(); i++) {
            Node node = (Node) deadNodes.get(i);
            buffer.putInt(node.nodeId());
        }

        buffer.putInt(aliveNodes.size());
        for (int i = 0; i < aliveNodes.size(); i++) {
            Node node = (Node) aliveNodes.get(i);
            buffer.putInt(node.nodeId());
            buffer.putInt(getDisksCount(node));
        }
    }

    public void copyFrom(ByteBuffer buffer) throws CMMException {
        masterNode = NodeTable.getNode(buffer.getInt());
        viceMasterNode = NodeTable.getNode(buffer.getInt());

        int size = buffer.getInt();
        for (int i = 0; i < size; i++) {
            int nodeid = buffer.getInt();
            Node node = NodeTable.getNode(nodeid);
            if (node == null) {
                throw new CMMException("unknown node in dead list:" + nodeid);
            }
            deadNodes.add(node);
        }
        
        size = buffer.getInt();
        assert (size > 0 && size <= CMM.MAX_NODES);
        nodeStatus = new NodeStatus[size];
        
        for (int i = 0; i < size; i++) {
            int nodeid = buffer.getInt();
            Node node = NodeTable.getNode(nodeid);
            if (node == null) {
                throw new CMMException("unknown node in alive list: " + nodeid);
            }
            aliveNodes.add(node);
            nodeStatus[i] = new NodeStatus(nodeid, buffer.getInt());
        }
    }

    public String toString() {

        StringBuffer sb = new StringBuffer("Discovery: ");
        sb.append(super.toString());
        sb.append("dead nodes: [ ");
        for (int i = 0; i < deadNodes.size(); i++) {
            sb.append(+ ((Node) deadNodes.get(i)).nodeId() + " ");
        }
        sb.append("] alive nodes [ ");
        for (int i = 0; i < aliveNodes.size(); i++) {
            sb.append(((Node) aliveNodes.get(i)).nodeId() + ":");
            sb.append(getDisksCount((Node) aliveNodes.get(i)) + " ");
        }
        sb.append("] master: " + masterNode);
        sb.append(" vicemaster: " + viceMasterNode);
        return sb.toString();
    }
        
    public FrameType getType() {
        return FrameType.DISCOVERY;
    }
}
