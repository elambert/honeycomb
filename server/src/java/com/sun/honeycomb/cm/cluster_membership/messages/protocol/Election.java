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
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMError;
import com.sun.honeycomb.cm.cluster_membership.Node;
import com.sun.honeycomb.cm.cluster_membership.NodeTable;
import java.nio.ByteBuffer;

/**
 * The Election message is sent to trigger or
 * notify an election.
 */
public class Election extends Message {

    public static final int MASTER = 1;
    public static final int VICEMASTER = 2;

    private Node candidate;
    private boolean request;
    private boolean canceled;
    private int office;

    public Election() {
        candidate = null;
        office = -1;
        canceled = false;
        request = true;
    }
    
    /**
     * candidature
     */
    public Election(int office) {
        this.office= office;
        candidate = NodeTable.getLocalNode();
        request = true;
        canceled = false;
        setBroadcast();
        arm(CMM.latencyTimeout());
    }

    /**
     * notification
     */
    public Election(Node node, int office) {
        this.office = office;
        candidate = node;
        request = false;
        canceled = false;
        setBroadcast();
        arm(CMM.latencyTimeout());
    }

    public void removeCandidate() {
        candidate = null;
    }

    public Node getCandidate() {
        return candidate;
    }

    public void setCancel(boolean isCanceled) {
        canceled = isCanceled;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public boolean isRequested() {
        return request;
    }

    public boolean isNotification() {
        return (!request);
    }

    public boolean isMasterOffice() {
        return office == MASTER;
    }

    public void copyInto(ByteBuffer buffer) {

        if (request) {
            buffer.putInt(1);
        } else {
            buffer.putInt(0);
        }
        if (candidate != null) {
            buffer.putInt(candidate.nodeId());
        } else if (request) {
            buffer.putInt(-1);
        } else {
            throw new CMMError("candidate null");
        }
        buffer.putInt(office);
        if (canceled) {
            buffer.putInt(1);
        } else {
            buffer.putInt(0);
        }
    }

    public void copyFrom(ByteBuffer buffer) {

        if (buffer.getInt() == 0) {
            request = false;
        } else {
            request = true;
        }
        candidate = NodeTable.getNode(buffer.getInt());
        office = buffer.getInt();
        if (buffer.getInt() == 0) {
            canceled = false;
        } else {
            canceled = true;
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("Election");
        if (request) {
            sb.append(" request ");
        } else {
            sb.append(" notification "); 
        }
        if (canceled) {
            sb.append(" [CANCELED] ");
        }
        sb.append("from " + super.toString() + "candidate " + candidate);
        if (office == MASTER) {
            sb.append(" [MASTER]");
        } else if (office == VICEMASTER) {
            sb.append(" [VICEMASTER]");
        }
        return sb.toString();
    }

    public FrameType getType() {
        return FrameType.ELECTION;
    }
}
