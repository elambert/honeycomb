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

import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.cm.ServiceManager;


//
// One such object per cluster node.
// These objects are created at init time by the 'AlertRoot'
//
public class AlertNode extends AlertComponentDefaultImpl
{
    private static final Logger logger =
        Logger.getLogger(AlertNode.class.getName());

    private NodeMgrService.Proxy nodeMgrProxy;
    private int nodeId;
    private boolean valid;

    public AlertNode(AlertProperty prp, NodeMgrService.Proxy proxy) {
        super(prp);
        nodeMgrProxy = proxy;

        nodeId = nodeMgrProxy.nodeId();

        nbChildren = 0;
        valid = true;
        updateChildren();
    }


    public AlertNode(AlertProperty prp, int id) {
        super(prp);
        nodeId = id;
        nodeMgrProxy = null;
        valid = true;
        nbChildren = 0;
    }

    public int nodeId() {
        return nodeId;
    }

    public void setValid() {
        valid = true;        
    }

    public void setInvalid() {
        valid = false;        
    }

    public boolean isValid() {
        return valid;
    }

    //
    // Called at initilization time when creating the tree
    // and by the correlation engine to refresh the proxys
    // and check for new running services-- since sercive creation
    // is asynchronous.
    //
    // Look all the services for this node.
    // - Skip unmanaged, and non running services
    // - Retrieve/refresh current proxy (AlertComponent) for the service
    //   from the local mailbox,
    // - Returns the number of new services or -1 if node proxy
    //
    public void updateChildren() {

        Service[] svc = nodeMgrProxy.getServices();

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("nb services on node" + nodeId +
                        ": " + svc.length);
        }


        for (int i = 0; i < svc.length; i++) {

            if (!svc[i].isManaged()) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("svc " + svc[i].getName() +
                                "on node " + nodeId() +
                                " not managed, skip...");
                }
                continue;
            }

            if (!svc[i].isRunning()) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("svc " + svc[i].getName() +
                            "on node " + nodeId() + " not running,...");
                }
                continue;
            }

            AlertComponent proxy = null;
            AlertProperty prop =
                new AlertProperty(svc[i].getName(),
                                           AlertType.COMPOSITE);
                
            Object obj = ServiceManager.proxyFor(nodeId,
                                                 svc[i].getName());
            if (! (obj instanceof AlertComponent)) { 
                logger.warning("cannot retrieve proxy " +
                              " for svc " +
                              svc[i].getName() + " on node " + nodeId);
                continue;
            }

            proxy = (AlertComponent) obj;

            if (!childrenMap.containsKey(svc[i].getName())) {

                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("add service " +
                                svc[i].getName()
                                + " on node " +  nodeId);
                }
                addService(prop, proxy);
            } else {
                // Update the proxy
                childrenMap.remove(svc[i].getName());
                childrenMap.put(svc[i].getName(), proxy);
            }
        }
    }

    public void addService(AlertProperty prop, AlertComponent comp) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("add service " +
                        prop.getName()
                        + " on node " +  nodeId());
        }
        childrenMap.put(prop.getName(), comp);
        childrenVector.add(prop);
        nbChildren++;
    }

    //
    // API for AlertComponent
    //
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

    public AlertComponent getPropertyValueComponent(String prop)
        throws AlertException {        
        AlertComponent proxy = (AlertComponent) childrenMap.get(prop);
        if (proxy != null) {
            return proxy;
        } else {
            throw (new AlertException("component " + prop + 
                                      " does not exist"));            
        }
    }

}
