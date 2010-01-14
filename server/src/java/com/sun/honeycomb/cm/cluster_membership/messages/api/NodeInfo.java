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



package com.sun.honeycomb.cm.cluster_membership.messages.api;

import com.sun.honeycomb.cm.cluster_membership.messages.*;
import com.sun.honeycomb.cm.cluster_membership.Node;
import com.sun.honeycomb.cm.cluster_membership.NodeTable;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import java.util.*;
import java.nio.ByteBuffer;


/**
 * The Node info message (API)
 */
public class NodeInfo extends Message {
    
    public static final int MEMBER_ME = 0;
    public static final int MEMBER_MASTER = 1;
    public static final int MEMBER_VICEMASTER = 2;
    public static final int MEMBER_ALL = 3;

    private int type;
    private CMMApi.Node[] members;

    public NodeInfo() {
        type    = -1;
        members = null;
    }

    public NodeInfo(int request) {
        type    = request;
        members = null;
    }

    public int getRqstType() {
        return type;
    }

    public CMMApi.Node[] getNodes() {
        return members;
    }

    public void updateInfo() throws CMMException {

        Node node;
        switch(type) {

            case MEMBER_ME:
                members = new CMMApi.Node[1];
                members[0] = new CMMApi.Node();
                copyNode(members[0], NodeTable.getLocalNode());
                break;

            case MEMBER_MASTER:
                node = NodeTable.getMasterNode();
                if (node != null) {
                    members = new CMMApi.Node[1];
                    members[0] = new CMMApi.Node();
                    copyNode(members[0], node);
                }
                break;

            case MEMBER_VICEMASTER:
                node = NodeTable.getViceMasterNode();
                if (node != null) {
                    members = new CMMApi.Node[1];
                    members[0] = new CMMApi.Node();
                    copyNode(members[0], node);
                }
                break;

            case MEMBER_ALL:
                members = new CMMApi.Node[NodeTable.getCount()];
                Iterator it = NodeTable.iterator();
                int index = 0;
                while (it.hasNext()) {
                    node = (Node) it.next();
                    CMMApi.Node member = new CMMApi.Node();
                    copyNode(member, node);
                    members[node.nodeIndex] = member;
                }
                break;

            default:
                throw new CMMException("NodeInfo unknown type " + type);
        }
    }
                
    public void copyInto(ByteBuffer buffer) {
        buffer.putInt(type);
        if (members == null) {
            buffer.putInt(0);
        } else {
            buffer.putInt(members.length);
            for (int i = 0; i < members.length; i++) {
                buffer.putInt(members[i].nodeId);
                byte[] name = members[i].hostName.getBytes();
                buffer.putInt(name.length);
                buffer.put(name);
                buffer.putInt((members[i].isAlive)? 1:0);
                buffer.putInt((members[i].isEligible)? 1:0);
                buffer.putInt((members[i].isMaster)? 1:0);
                buffer.putInt((members[i].isViceMaster)? 1:0);
                buffer.putInt((members[i].isOff)? 1:0);
                buffer.putInt(members[i].getActiveDiskCount());
            }
        } 
    }

    public void copyFrom(ByteBuffer buffer) {

        type = buffer.getInt();
        int size = buffer.getInt();
        if (size == 0) {
            members = null;
        } else {
            members = new CMMApi.Node[size];
            for (int i = 0; i < size; i++) {
                members[i] = new CMMApi.Node();
                members[i].nodeId = buffer.getInt();
                byte[] name = new byte[buffer.getInt()];
                buffer.get(name);
                members[i].hostName = new String(name);
                members[i].isAlive = (buffer.getInt() == 1)? true:false;
                members[i].isEligible = (buffer.getInt() == 1)? true:false;
                members[i].isMaster = (buffer.getInt() == 1)? true:false;
                members[i].isViceMaster = (buffer.getInt() == 1)? true:false;
                members[i].isOff = (buffer.getInt() == 1)? true:false;
                members[i].activeDisks = buffer.getInt();
            }
        }
    }

    public FrameType getType() {
        return FrameType.NODE_INFO;
    }

    public String toString() {
        return "NodeInfo:" + type + " " + super.toString();
    }

    private void copyNode(CMMApi.Node a, Node b) {
        a.hostName     = b.getInetAddr().getAddress().getHostAddress();
        a.activeDisks  = b.getActiveDiskCount();
        a.nodeId       = b.nodeId();
        a.isAlive      = b.isAlive();
        a.isEligible   = b.isEligible();
        a.isMaster     = b.isMaster();
        a.isViceMaster = b.isViceMaster();
        a.isOff        = b.isOff();
    }
}
