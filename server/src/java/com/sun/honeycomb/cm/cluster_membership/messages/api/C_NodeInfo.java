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
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import java.util.*;
import java.nio.ByteBuffer;


/**
 * The Node info message (C API)
 */
public class C_NodeInfo extends Message {
    
    static final int MEMBER_ME = 0;
    static final int MEMBER_MASTER = 1;
    static final int MEMBER_VICEMASTER = 2;
    static final int MEMBER_GIVEN_NODE = 3;
    static final int MEMBER_ALL_NB_ONLY = 4;
    static final int MEMBER_ALL = 5;

    private int type;
    private int nodeId;
    private MemberInfo[] members;

    public C_NodeInfo() {
        type    = -1;
        nodeId  = -1;
        members = null;
    }

    public void updateInfo() throws CMMException {

        Node node;
        switch(type) {

            case MEMBER_ME:
                members = new MemberInfo[1];
                members[0] = new MemberInfo(NodeTable.getLocalNode());
                break;

            case MEMBER_MASTER:
                node = NodeTable.getMasterNode();
                if (node != null) {
                    members = new MemberInfo[1];
                    members[0] = new MemberInfo(node);
                }
                break;

            case MEMBER_VICEMASTER:
                node = NodeTable.getViceMasterNode();
                if (node != null) {
                    members = new MemberInfo[1];
                    members[0] = new MemberInfo(node);
                }
                break;

            case MEMBER_GIVEN_NODE:
                node = NodeTable.getNode(nodeId);
                if (node != null) {
                    members = new MemberInfo[1];
                    members[0] = new MemberInfo(node);
                }
                break;

            case MEMBER_ALL_NB_ONLY:
                // this is done in copyInto
                break;

            case MEMBER_ALL:
                members = new MemberInfo[NodeTable.getCount()];
                Iterator it = NodeTable.iterator();
                int index = 0;
                while (it.hasNext()) {
                    node = (Node) it.next();
                    MemberInfo member = new MemberInfo(node);
                    members[node.nodeIndex] = member;
                }
                break;

            default:
                throw new CMMException("NodeInfo unknown type " + type);
        }
    }
                
    public void copyInto(ByteBuffer buffer) {

        buffer.putInt(type);
        buffer.putInt(nodeId);

        if (type == MEMBER_ALL_NB_ONLY) {
            buffer.putInt(NodeTable.getCount());
        } else if (members == null) {
            buffer.putInt(0);
        } else {
            buffer.putInt(members.length);
            for (int i = 0; i < members.length; i++) {
                members[i].copyInto(buffer);
            }
        } 
    }

    public void copyFrom(ByteBuffer buffer) {
        type = buffer.getInt();
        nodeId = buffer.getInt();
    }

    public FrameType getType() {
        return FrameType.C_NODE_INFO;
    }

    public String toString() {
        return "[" + "NodeInfo:" + type + "]";
    } 

    /*
     * ABI compatibility with current CMM.
     */

    private static class MemberInfo {

        private static final int MAX_NAME_SIZE = 256;
        private static final int MAX_ADDR_SIZE = 64;
        private static final int MAX_SWLOAD_SIZE = 32;

        private static final int CMM_MASTER = 0x0001;
        private static final int CMM_VICEMASTER = 0x0002;
        private static final int CMM_OUT_OF_CLUSTER = 0x0004;
        private static final int CMM_ELIGIBLE = 0x0400;

        int nodeid;
        byte[] name;
        byte[] addr;
        int domain;
        int sflags;
        int incarnation;
        byte[] swload;

        MemberInfo(Node node) {
            nodeid = node.nodeId();
            name = new byte[MAX_NAME_SIZE];
            addr = new byte[MAX_ADDR_SIZE];
            swload = new byte[MAX_SWLOAD_SIZE];
            incarnation = 0;
            sflags = 0;
            domain = 0;

            String src;
            src = node.getInetAddr().getAddress().getHostAddress().toString();
            System.arraycopy(src.getBytes(), 0, name, 0, src.length());
            System.arraycopy(src.getBytes(), 0, addr, 0, src.length());

            if (node.isMaster()) {
                sflags |= CMM_MASTER;
            }
            if (node.isViceMaster()) {
                sflags |= CMM_VICEMASTER;
            }
            if (node.isEligible()) {
                sflags |= CMM_ELIGIBLE;
            }
            if (!node.isAlive()) {
                sflags |= CMM_OUT_OF_CLUSTER;
            }
        }

        void copyInto(ByteBuffer buffer) {
            buffer.putInt(nodeid);
            buffer.put(name);
            buffer.put(addr);
            buffer.putInt(domain);
            buffer.putInt(sflags);
            buffer.putInt(incarnation);
            buffer.put(swload);
        }

        public String toString() {
            return name.toString();
        }
    }
}
