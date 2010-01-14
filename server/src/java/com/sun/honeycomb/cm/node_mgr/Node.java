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



package com.sun.honeycomb.cm.node_mgr;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;

/**
 * Definition of a node in the node manager
 * proxy object.
 *
 */
public class Node extends CMMApi.Node implements java.io.Serializable {

    private final String tag;

    /**
     * return the corresponding node manager proxy of this
     * node.
     */
    public NodeMgrService.Proxy getProxy() {
        Object obj;
        obj = ServiceManager.proxyFor(nodeId);
        if (obj instanceof NodeMgrService.Proxy) {
            assert (isAlive());
            return (NodeMgrService.Proxy) obj;
        }
        return null;
    }

    public String toStatus() {
        String status = " [" + getActiveDiskCount() + "]";
        if (isAlive()) {
            status += " ALIVE";
        } else {
            status += " DEAD";
        }
        if (isMaster()) {
            status += " MASTER ";
        } else if (isViceMaster()) {
            status += " VICEMASTER ";
        } else if (isEligible()) {
            status += " ELIGIBLE ";
        }
        if (isOff()) {
            status += " OFF ";
        }
        return status;
    }

    public String toString() {
        return nodeId + " " + toStatus() + " " + hostName;
    }

    Node(String tag, CMMApi.Node node) {
        super.init(node);
        this.tag = tag;
    }

    void update(CMMApi.Node node) {
        super.init(node);
    }
}
