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



package com.sun.honeycomb.alert;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.common.InternalException;

//
// 'root' AlertComponent.
//  The AlertServer creates on such object at init time.
//  and populates all its children with the 'node' AlertComponent.
//
public class AlertRoot extends AlertComponentDefaultImpl
{

    private static final Logger logger =
        Logger.getLogger(AlertRoot.class.getName());
    
    private NodeMgrService.Proxy nodeMgrProxy;

    public AlertRoot(AlertProperty initProp) {
        super(initProp);

        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            throw new InternalException("AlertRoot : cannot retrieve " +
                                        "NodeManager Proxy");
        }
        nodeMgrProxy = (NodeMgrService.Proxy) obj;

        Node[] nodes = nodeMgrProxy.getNodes();
        nbChildren = nodes.length;


        for (int i = 0; i < nbChildren; i++) {
            String nodeid = (new Integer(nodes[i].nodeId())).toString();
            AlertProperty prop = new AlertProperty(nodeid,
                                                   AlertType.COMPOSITE);
            AlertNode node = new AlertNode(prop, nodes[i].nodeId());
            childrenMap.put(nodeid, node);
            childrenVector.add(prop);
        }
    }

    public AlertProperty getPropertyChild(int index)
        throws AlertException {
        AlertProperty prop = (AlertProperty) childrenVector.get(index);
        if (prop != null) {
            return prop;
        } else {
            throw (new AlertException("property for index " + index + 
                                      " does not exist"));
        }
    }

    public AlertComponent getPropertyValueComponent(String name)
        throws AlertException {        
        AlertNode node = (AlertNode) childrenMap.get(name);
        if (node != null) {
            return node;
        } else {
            throw (new AlertException("component " + name + " does not exist"));
        }
    }
}
