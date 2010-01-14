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
import java.nio.ByteBuffer;

/**
 * Node change notification message (API)
 */
public class NodeChange extends Message {

    public static final int MASTER_ELECTED = 250;
    public static final int MASTER_DEMOTED = 251;
    public static final int VICEMASTER_ELECTED = 252;
    public static final int VICEMASTER_DEMOTED = 253;
    public static final int MEMBER_JOINED = 254;
    public static final int MEMBER_LEFT = 255;
    public static final int STALE_CLUSTER = 256;
    public static final int INVALID_CLUSTER = 257;
    public static final int VALID_CLUSTER = 258;
    public static final int NODE_ELIGIBLE = 259;
    public static final int NODE_INELIGIBLE = 260;
    
    public static final int DISK_CHANGE   = 800;

    public static final int GAINED_QUORUM = 900;
    public static final int LOST_QUORUM   = 901;
    
    public static final int CMD_OK = 0;
    public static final int CMD_ERROR = 1;

    private int nodeid;
    private int cause;

    public NodeChange() {
        nodeid = -1;
        cause = -1;
    }

    public NodeChange(Node node, int cause) {
        this.nodeid = node.nodeId();
        this.cause = cause;
    }

    public NodeChange(int nodeId, int cause) {
        this.nodeid = nodeId;
        this.cause = cause;        
    }

    public NodeChange(int cause) {
        this.nodeid = -1;
        this.cause = cause;
    }

    public int nodeId() {
        return nodeid;
    }

    public int getCause() {
        return cause;
    }

    public void setReply(int status) {
        cause = status;
    }

    public FrameType getType() {
        return FrameType.NODE_CHANGE;
    }

    public void copyInto(ByteBuffer buffer) throws CMMException {
        if (nodeid == -1) {
            throw new CMMException("NodeChange: node is not set");
        }
        buffer.putInt(cause);
        buffer.putInt(nodeid);
    }

    public void copyFrom(ByteBuffer buffer) throws CMMException {
        cause = buffer.getInt();
        nodeid = buffer.getInt();
        if (nodeid == -1) {
            throw new CMMException("NodeChange: unknown node");
        }
    }

    public String toString() {
	StringBuffer sb = new StringBuffer();
	exportString(sb);
	sb.append(" from ");
	sb.append(super.toString());
	return(sb.toString());
    }
    
    public void exportString(StringBuffer sb) {
	sb.append("NodeChange: ");
	sb.append(nodeid);
	sb.append(" cause ");
	sb.append(toStatus());
    }

    private String toStatus() {
        switch (cause) {
        case MASTER_ELECTED:
            return "elected master";
        case MASTER_DEMOTED:
            return "master demoted";
        case VICEMASTER_ELECTED:
            return "elected vice master";
        case VICEMASTER_DEMOTED:
            return "vice master demoted";
        case MEMBER_JOINED:
            return "node joined";
        case MEMBER_LEFT:
            return "node left";
        case NODE_ELIGIBLE:
            return "node eligible";
        case NODE_INELIGIBLE:
            return "node not eligible";
        case GAINED_QUORUM:
            return "achieved disk quorum";
        case LOST_QUORUM:
            return "lost disk quorum";
        default:
            return "unknown";
        }
    }
}
    
