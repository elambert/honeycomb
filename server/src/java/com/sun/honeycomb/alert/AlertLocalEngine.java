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
import java.io.IOException;


import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ManagedService;

//
// Create the branch of properties for the local node.
//
public class AlertLocalEngine {


    private static transient final Logger logger = 
        Logger.getLogger(AlertCorrelationEngine.class.getName());

    // Current tree or properties for this node
    private AlertTreeNode  curBranch;

    // Latest tree of properties returned to the master.
    private AlertTreeNode  latestBranch;

    // Latest tree of properties processed by the master
    private AlertTreeNode  masterBranch;

    // Node Manager Proxy for local node.
    private NodeMgrService.Proxy nodeMgrProxy;

    // Current nodeid
    private int nodeId;

    public AlertLocalEngine() {
        curBranch = null;
        masterBranch = null;
        latestBranch = null;
        nodeMgrProxy = getProxy();
        nodeId = nodeMgrProxy.nodeId();
    }


    //
    // Called by AlerterClient (RMI call implementation)
    //
    synchronized public AlertTreeNode getBranch(int whichBranch) {
        
        switch(whichBranch) {

        case AlertCorrelationEngine.INIT_BRANCH:
            return masterBranch;
            
        case AlertCorrelationEngine.LATEST_BRANCH:
            masterBranch = latestBranch;
            latestBranch = curBranch;
            curBranch = null;
            notify();
            return latestBranch;
            
        default:
            logger.severe("unknown request from correlation engine, " + 
                          "ignore...");
            break;
        }
        return null;
    }



    //
    // Wait on the monitor until the Master node fetches the current branch.
    //
    synchronized public void waitForMaster()
        throws InterruptedException {
        wait();
    }

    //
    // Probe the node and fetch the next branch of properties.
    //
    public void update() {
        AlertTreeNode newBranch = null;
        try {
            newBranch = probeNode();
        } catch (AlertException ae) {
            logger.log(Level.SEVERE,
                       "cannot fetch next branch of properties " +
                       AlertCorrelationEngine.formatThrowable(ae),
                       ae);
        }
        curBranch = newBranch;
    }


    //
    //
    // PRIVATE METHODS
    //

    //
    //
    // Returns Node Manager proxy for the local node
    //
    private NodeMgrService.Proxy getProxy() {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            throw new InternalException("cannot fetch NodeManager Proxy" +
                                        " for local node");
        }
        return (NodeMgrService.Proxy) obj;
    }


    //
    // Probe the node to create the branch of properties
    //
    private AlertTreeNode probeNode()
        throws AlertException {

        // Retrieve latest proxy.
        nodeMgrProxy = getProxy();

        String nodeStr = (new Integer(nodeId)).toString();

        AlertComponent.AlertProperty prop =
            new AlertComponent.AlertProperty(nodeStr, AlertType.COMPOSITE);
        AlertNode node = new AlertNode(prop, nodeMgrProxy);           
        AlertTreeNode newBranch = new AlertTreeNode(node, null);

        StringBuffer str = new StringBuffer();
        str.append("root.");
        str.append(nodeStr);

        createSubTree(newBranch, str);
        return newBranch;
    }


    //
    // Called by createTree() and updateTree()
    // Create recursively the subtree (starts at the node level)
    //
    private void createSubTree(AlertTreeNode parent, StringBuffer path)  
        throws AlertException {

        AlertComponent    parentComp = parent.getComponent();
        AlertTreeNode     child = null;
        AlertTreeNode     prevChild = null;
        AlertComponent    childComp = null;
        boolean           valBool;
        int               valInt;
        long              valLong;
        float             valFloat;
        double            valDouble;
        String            valString;
        AlertTreeNodeLeaf nodeLeaf = null;

        if (parentComp == null) {
            logger.severe("parent component is nll, " +
                          "skip subtree creation");
            return;
        }
        
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("createSubTree(), nbChildren = " +
                        parentComp.getNbChildren());
        }
        
        for (int i = 0; i < parentComp.getNbChildren(); i++) {
            
            AlertComponent.AlertProperty prop = parentComp.getPropertyChild(i);
            
            StringBuffer newPath = new StringBuffer();
            newPath.append(path);
            newPath.append(".");
            newPath.append(prop.getName());

            switch (prop.getType()) {
            case AlertType.COMPOSITE:
                childComp =
                    parentComp.getPropertyValueComponent(prop.getName());
                child = new AlertTreeNode(childComp, parent);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("createSubTree(), new node = " +
                                prop.getName());
                }
                if (childComp != null) {
                    createSubTree(child, newPath);
                }
                break;

            case AlertType.BOOLEAN:
                valBool = parentComp.getPropertyValueBoolean(prop.getName());
                nodeLeaf = new AlertTreeNodeLeaf(parent,
                                                 prop,
                                                 newPath.toString(),
                                                 new Boolean(valBool));
                break;

            case AlertType.INT:
                valInt = parentComp.getPropertyValueInt(prop.getName());
                nodeLeaf = new AlertTreeNodeLeaf(parent,
                                                 prop,
                                                 newPath.toString(),
                                                 new Integer(valInt));
                break;
                
            case AlertType.LONG:
                valLong = parentComp.getPropertyValueLong(prop.getName());
                nodeLeaf = new AlertTreeNodeLeaf(parent,
                                                 prop,
                                                 newPath.toString(),
                                                 new Long(valLong));
                break;
                
            case AlertType.FLOAT:
                valFloat = parentComp.getPropertyValueFloat(prop.getName());
                nodeLeaf = new AlertTreeNodeLeaf(parent,
                                                 prop,
                                                 newPath.toString(),
                                                 new Float(valFloat));
                break;
                
            case AlertType.DOUBLE:
                valDouble = parentComp.getPropertyValueDouble(prop.getName());
                nodeLeaf = new AlertTreeNodeLeaf(parent,
                                                 prop,
                                                 newPath.toString(),
                                                 new Double(valDouble));
                break;
                
            case AlertType.STRING:
                valString = parentComp.getPropertyValueString(prop.getName());
                nodeLeaf = new AlertTreeNodeLeaf(parent,
                                                 prop,
                                                 newPath.toString(),
                                                 valString);
                break;
                
            default:
                throw new AlertException("property type not supported");
            }
            
            if (prop.getType() != AlertType.COMPOSITE) {
                child = new AlertTreeNode(parent);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("createSubTree() found leaf = " +
                                prop.getName());
                }
                child.setLeaf(nodeLeaf);
            }
            
            if (prevChild == null) {
                parent.setChild(child);
            } else {
                prevChild.setSibling(child);
            }
            prevChild = child;
        }
    }
}

