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


package com.sun.honeycomb.cm.cluster_membership;

import java.net.InetSocketAddress;

/**
 * The Node class describes the local view of a node
 * in the ring with its current status.
 */
public class Node implements Comparable {

    public final int nodeIndex;

    private final InetSocketAddress addr;
    private boolean           isSniffed;
    private final String      hostname;
    private final int         nodeId;
    private volatile int      lastFrameId;
    private volatile boolean  alive;
    private volatile boolean  eligible;
    private volatile boolean  master;
    private volatile boolean  vicemaster;
    private volatile int      liveDisks;

    public Node(int nodeIndex, int nodeId,
                String hostname, int port,
                boolean eligible) 
        throws IllegalArgumentException {

        this.hostname = hostname;
        this.nodeId = nodeId;
        this.nodeIndex = nodeIndex;
        this.eligible = eligible;
        this.liveDisks = 0;

        alive = false;
        master = false;
        vicemaster = false;
        lastFrameId = 0;

        addr = new InetSocketAddress(hostname, port);
        if (addr.isUnresolved()) {
            String error = "host " + hostname + " unknown";
            throw new IllegalArgumentException(error);
        }
        isSniffed = false;
    }
    
    public void setSniffedFlag() {
        isSniffed = true;
    }

    public boolean isOff() {
        return false;
    }
    
    public boolean isAlive() {
        return alive;
    }

    void setAlive(boolean isAlive) {
        if (!isAlive) {
            master = false;
            vicemaster = false;
            lastFrameId = 0;
        }
        alive = isAlive;
    }

    public boolean isEligible() {
        return eligible;
    }

    void setEligible(boolean isEligible) {
        if (!isEligible) {
            master = false;
            vicemaster = false;
        }
        eligible = isEligible;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean isMaster) {
        if (isMaster) {
            eligible = true;
        }
        master = isMaster;
    }

    public boolean isViceMaster() {
        return vicemaster;
    }

    void setViceMaster(boolean isViceMaster) {
        if (isViceMaster) {
            eligible = true;
        }
        vicemaster = isViceMaster;
    }

    public int nodeId() {
        return nodeId;
    }

    public boolean isLocalNode() {
        return nodeId == NodeTable.getLocalNodeId();
    }

    int getLastFrameId() {
        return lastFrameId;
    }

    void setLastFrameId(int frameId) {
        lastFrameId = frameId;
    }

    public int getActiveDiskCount() {
        return this.liveDisks;
    }

    public void setActiveDiskCount (int count) {
        this.liveDisks = count;
    }

    public InetSocketAddress getInetAddr() {
        return addr;
    }

    public InetSocketAddress getAddrToBind() {
        if (isSniffed) {
            return(new InetSocketAddress(addr.getAddress(),
                                         addr.getPort()+CMM.SNIFF_OFFSET));
        } else {
            return(addr);
        }
    }

    public String getHost() {
        return addr.getHostName();
    }

    public int compareTo(Object object) {
        if (!(object instanceof Node)) {
            return -1;
        }
        int nodeid = ((Node) object).nodeId();
        if (nodeid < nodeId) {
            return 1;
        } else if (nodeid > nodeId) {
            return -1;
        }
        return 0;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("node " + nodeId);
        sb.append(" (").append (getHost()).append (") [").append (liveDisks)
            .append(" disks/");

        if (alive) {
            sb.append(" up "); 
        } else {
            sb.append(" down ");
        }
        if (master) {
            sb.append(" master ");
        }
        if (vicemaster) {
            sb.append(" vicemaster ");
        }
        if (eligible) {
            sb.append(" eligible ");
        }
        sb.append("] ");
        return sb.toString();
    }

    public boolean equals(Object o) {
        if (!(o instanceof Node)) {
            return(false);
        }
        Node node = (Node)o;
        return(nodeId == node.nodeId);
    }
}
