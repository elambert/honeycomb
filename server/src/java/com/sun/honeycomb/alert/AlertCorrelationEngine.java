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

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ipc.Mboxd;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.LinkedList;
import java.util.Observer;
import java.util.Observable;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.lang.reflect.UndeclaredThrowableException;
//
//
// There is one main thread (main thread from AlerterServer),
// All the data structures are protected using the monitor
// from the class AlertCorrelationEngine.
//
public class AlertCorrelationEngine implements Observer {

    private static transient final Logger logger = 
        Logger.getLogger(AlertCorrelationEngine.class.getName());

    // Which branch to retrieve from each node (LocalEngine)
    static final public int    INIT_BRANCH = 0;
    static final public int    LATEST_BRANCH = 1;
        
    // List of properties (AlertTreeNodeLeaf) [1 per node]
    private List[]         propertiesNode;
    // List of (start/stop) Indexes for all the services [1 per Node]
    private HashMap[]           servicesNode;
    // Mapping property name / property (AlertTreeNodeLeaf)
    private HashMap             propertiesMap;
    private HashMap             propertiesInitMap;
    // Root of the tree
    private AlertTreeNode       root;
    // Root of the first tree
    private AlertTreeNode       initRoot;
    // Current thread
    private Thread              thr;
    // Map of Observer
    private HashMap             observers;
    // Linked list of CMM notification
    private LinkedList          cmmNotifications;
    // Retain log status per node-- to avoid logging same error every call.
    private Boolean             logStatus[];


    public AlertCorrelationEngine() {
        root = null;
        propertiesMap = new HashMap();
        propertiesInitMap = new HashMap();
        observers = new HashMap();
        propertiesNode = new ArrayList[CMM.MAX_NODES];
        servicesNode = new HashMap[CMM.MAX_NODES];
        logStatus = new Boolean[CMM.MAX_NODES];

        for (int i = 0; i < CMM.MAX_NODES; i++) {
            servicesNode[i] = new HashMap();
            propertiesNode[i] = new ArrayList();
            logStatus[i] = new Boolean(true);
        }
        
        // Main thread
        thr = Thread.currentThread();

        cmmNotifications = new LinkedList();

        try {
            ServiceManager.register(ServiceManager.CMM_EVENT, this);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("successfuly registered CMM notification");
            }
        } catch (CMAException e) {
            logger.severe("CMM registration failed.." + e);
        }

        // Create initial tree of properties
        try {
            initializeEngine();
        } catch (AlertException ae ) {
            logger.log(Level.SEVERE,
                       "cannot initialize correlation engine",
                       ae);
        }
    }
    


    //
    // PUBLIC METHOD CALLED BY CMAgent-- RMI call from AlerterLocal
    // - Update/Create the branch for the node 'nodeid'
    //
    public void updateTree(boolean init, int nodeIndex) 
        throws AlertException {

        boolean found = false;
        int i = -1;

        if (!init) {
            checkPendingCMMNotif();
        }

        int nodeId = getNodeIdFromIndex(nodeIndex);
        if (nodeId == -1) {
            logger.severe("skip fetching branch for index = " + nodeIndex);
            return;
        }

        //
        // Fetch the latest/current branch from each node
        //
        NodeMgrService.Proxy nodeMgrProxy =
            getProxy(ServiceManager.LOCAL_NODE);
        Node[] nodes = nodeMgrProxy.getNodes();
        for (i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() != nodeId) {
                continue;
            }
            if (!nodes[i].isAlive()) {
                return;
            } else {
                found = true;
                break;
            }
        }
        if (found == false) {
            logger.severe("cannot find node for index" + nodeIndex);
            return;
        }


        ManagedService.ProxyObject proxy =
            ServiceManager.proxyFor(nodes[i].nodeId(), "AlerterClient");
        //
        // If the remote node is still initializing it does not have
        // any branch to give us so we can safely skip it.
        //
        if (! (proxy instanceof AlerterClientIntf.Proxy)) {
            return;
        }

        Object obj = proxy.getAPI(); 
        if (!(obj instanceof  AlerterClientIntf)) {
            if (logStatus[nodeIndex].booleanValue() == true) {
                logStatus[nodeIndex] = new Boolean(false);
                throw (new AlertException("cannot retrieve API from " +
                                          "AlerterClientIntf running on node " +
                                          nodes[i].nodeId()));
            } else {
                return;
            }
        }
        AlerterClientIntf api = (AlerterClientIntf) obj;

            
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("initialize branch tree from node " + 
                        nodes[i].nodeId());
        }

        AlertNode compNode = (AlertNode)
            root.getChild(nodeIndex).getComponent();

        try {
            if (init) {
                AlertTreeNode initBranch = api.getBranch(INIT_BRANCH);
                if (initBranch != null) {
                    addInitBranch(initBranch, nodeIndex);
                }
            }

            AlertTreeNode latestBranch = api.getBranch(LATEST_BRANCH);
            if (latestBranch != null) {
                mergeNewBranch(latestBranch, nodeIndex);
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("master got branches from node " +
                            nodes[i].nodeId());
            }
            logStatus[nodeIndex] = new Boolean(true);
            compNode.setValid();
        } catch (IOException ioe) {
            if (logStatus[nodeIndex].booleanValue() == true) {
                logger.warning("IO Exception from AlerterClientIntf RMI call " +
                               "to node " + nodes[i].nodeId() + " " + ioe);
                logStatus[nodeIndex] = new Boolean(false);
                compNode.setInvalid();
            }
        } catch (UndeclaredThrowableException ue) {
            if (logStatus[nodeIndex].booleanValue() == true) {
                Throwable thr = ue.getCause();
                logger.log(Level.SEVERE,
                           "UndeclaredThrowableException from " +
                           "AlerterClientIntf RMI call to node " +
                           nodes[i].nodeId() + " :", thr);
                logStatus[nodeIndex] = new Boolean(false);
                compNode.setInvalid();
            }
        } catch (Throwable th) {
            if (logStatus[nodeIndex].booleanValue() == true) {
                logger.severe("Unexpected exception from AlerterClientIntf " +
                           "RMI call to node " + nodes[i].nodeId() + " " + 
                           formatThrowable(th));
                logStatus[nodeIndex] = new Boolean(false);
                compNode.setInvalid();             
            }
        }
    }

    static public String formatThrowable(Throwable t) {
        String s = t.toString();
        StackTraceElement[] st = t.getStackTrace();
        if (st != null) {
            for (int i=0; i<st.length; i++) {
                String ss = st[0].toString();
                s += "[at " + ss + "]\n";
                if (ss.indexOf("honeycomb.alert") != -1)
                    break;
            }
        }
        return s;
    }

    //
    // PUBLIC METHOD CALLED BY CMAgent for CMM Notifications
    // Forward CMM notification to registered clients.
    //
    public void update(Observable o, Object arg) {

        if (!(arg instanceof NodeChange)) {
            logger.severe("received unknown notification, ignore...");
            return;
        }

        NodeChange nodeChange = (NodeChange) arg;
        if ((nodeChange.getCause() != NodeChange.MEMBER_JOINED) &&
            (nodeChange.getCause() != NodeChange.MEMBER_LEFT)) {
            // Ignore.
            return;
        }

        synchronized(cmmNotifications) {
            cmmNotifications.add(nodeChange);
        }
    }

    //
    //
    // PUBLIC METHODS CALLED BY AlertApiImpl
    //
    //
    //
    public void getCurrentView(HashMap map, List list) {

        synchronized(AlertCorrelationEngine.class) {
            for (int j = 0; j < CMM.MAX_NODES; j++) {
                AlertNode curNode = getNodeFromIndex(j);
                if (!curNode.isValid()) {
                    continue;
                }
                for (int i = 0; i < propertiesNode[j].size(); i++) {
                    
                    AlertTreeNodeLeaf leaf =
                        (AlertTreeNodeLeaf) propertiesNode[j].get(i);
                    AlertApi.AlertObject obj = leaf.getAlertObject();
                    map.put(obj.getPropertyName(), new Integer(list.size()));
                    list.add(obj);
                }
            }        
        }
    }

    //
    // Retrieve a 'single property' using its name.
    //
    public AlertApi.AlertObject getProperty(String prop) 
        throws AlertException {
        AlertApi.AlertObject obj = null;
        AlertTreeNodeLeaf leaf = null;
        synchronized(AlertCorrelationEngine.class) {
            leaf = (AlertTreeNodeLeaf) propertiesMap.get(prop);
        }
        if (leaf != null) {
            String [] pathElements = leaf.parsePath();
            try {
                int nodeid = Integer.parseInt(pathElements[0]);
                int index = getIndexFromNodeId(nodeid);
                AlertNode curNode = getNodeFromIndex(index);
                if (curNode != null && curNode.isValid()) {
                    obj = leaf.getAlertObject();
                    return obj;
                }
            } catch (NumberFormatException nb) {
                logger.severe("invalid leaf, nodeid = " + pathElements[0]);
            }
        }
        if (obj == null) {
            throw (new AlertException("property " + prop + " does not exist"));
        }
        // To please compiler.
        return null;
    }
    
    //
    // Register 'property' notification
    //
    public Object register(String name, String node, String svc, String prop,
                        Observer obs, AlertApi.AlertEvent ev) 
        throws AlertException {

        Object res = null;
        AlertObserver curObs = null;
        List leaves = null;

        synchronized(AlertCorrelationEngine.class) {
            curObs = (AlertObserver) observers.get(obs);
            if (curObs == null) {
                curObs = new AlertObserver(obs, name);
                observers.put(obs, curObs);
            }

            leaves = getLeavesMatchingRule(node, svc, prop);
            for (int i = 0; i < leaves.size(); i++) {
                AlertTreeNodeLeaf leaf = (AlertTreeNodeLeaf) leaves.get(i);
                leaf.addObserver(curObs);
            }
            res  = curObs.addNotification(node, svc, prop, ev);
        }
        triggerMissedNotifications(obs, leaves, ev.getType());
        return res;
    }

    //
    // Register for CMM notification
    //
    public Object registerCMM(String name, Observer obs) {

        Object res =  null;
        synchronized(AlertCorrelationEngine.class) {
            AlertObserver curObs = (AlertObserver) observers.get(obs);
            if (curObs == null) {
                curObs = new AlertObserver(obs, name);
                observers.put(obs, curObs);
            }
            
            res = curObs.addCMMNotification();
        }
        triggerMissedCMMNotifications(obs);
        return res;
    }

    //
    // Unregister the notification
    //
    public boolean unregister(Observer obs, Object obj) {
        boolean res;
        synchronized(AlertCorrelationEngine.class) {
                AlertObserver curObs = (AlertObserver) observers.get(obs);
                if (curObs == null) {
                    return false;
                }
                res = curObs.delNotification(obj);
        }
        return res;
    }

    //
    // Notify the clients who have registered for the property 'prop'
    //
    public void notifyClients(String prop, String msg)
        throws AlertException {
        AlertTreeNodeLeaf leaf = null;
        synchronized(AlertCorrelationEngine.class) {
            leaf = (AlertTreeNodeLeaf) propertiesMap.get(prop);
            if (leaf == null) {
                throw new AlertException("property " + prop + "does not exist");
            }
        }
        leaf.notifyClients(msg);
    }


    //
    //
    // PRIVATE METHODS.
    //
    //
    private void initializeEngine()
        throws AlertException {
        synchronized(AlertCorrelationEngine.class) {      
            root = createTree();
            initRoot = createTree();
            for (int i = 0; i < CMM.MAX_NODES; i++) {
                updateTree(true, i); 
            }
            populateInitMap(initRoot);
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.info("engine has been initialized succesfully");
        }
    }


    //
    // Called once at initialization time to create the top of the trees.
    //
    private AlertTreeNode createTree() 
        throws AlertException {

        AlertTreeNode res = null;

        AlertComponent.AlertProperty prop = null;
        AlertComponent compRoot = null;
        AlertComponent compNode = null;
        AlertTreeNode node = null;
        AlertTreeNode prevNode = null;

        //
        // 'root' node
        //
        prop = new AlertComponent.AlertProperty("root", AlertType.COMPOSITE);
        compRoot = new AlertRoot(prop);
        res = new AlertTreeNode(compRoot, null);

        //
        // 'node' Node.
        // 
        for (int i = 0; i < compRoot.getNbChildren(); i++) {
            
            prop = compRoot.getPropertyChild(i);
            compNode = compRoot.getPropertyValueComponent(prop.getName());
            node = new AlertTreeNode(compNode, res);
            if (prevNode == null) {
                res.setChild(node);
            } else {
                prevNode.setSibling(node);
            }
            prevNode = node;
        }
        return res;
    }

    //
    // Called at init time to populate initRoot
    //
    private void addInitBranch(AlertTreeNode branch, int nodeIndex)
        throws AlertException {

        if (logger.isLoggable(Level.INFO)) {
            logger.info("add init branch from node (index = " +
                        nodeIndex + ")");
        }

        synchronized(AlertCorrelationEngine.class) {
            if (nodeIndex == 0) {
                AlertTreeNode child = initRoot.getChild();
                branch.setSibling(child.getSibling());
                initRoot.setChild(branch);
            } else {
                AlertTreeNode prevChild = initRoot.getChild(nodeIndex - 1);
                AlertTreeNode child = prevChild.getSibling();
                branch.setSibling(child.getSibling());
                prevChild.setSibling(branch);
            }
            branch.setParent(initRoot);
        }
    }

    //
    // Called from the AlertLocalEngine RMI call to update the
    // branch of the tree for the specific node 'nodeid'
    //
    private void mergeNewBranch(AlertTreeNode branch, int nodeIndex)
        throws AlertException {

        AlertNode node = null;
        AlertComponent.AlertProperty prop = null;

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("merge new branch, node = " + nodeIndex + ")");
        }

        synchronized(AlertCorrelationEngine.class) {

            // Existing 'node' in the tree.
            AlertTreeNode parentNode = root.getChild(nodeIndex);
            AlertNode compNode = (AlertNode) parentNode.getComponent();
            AlertTreeNode  lastChild =
                parentNode.getChild(compNode.getNbChildren() - 1);

            //
            // Check each service:
            // - if service exists already, update the value
            //   and generate alerts
            // - if the service does not exist, create new branch and
            //   add new properties
            //
            AlertComponent bCompNode = branch.getComponent();
            if (bCompNode == null) {
                throw(new AlertException("new branch from node " + nodeIndex +
                                          " is corrupted, component is null," +
                                         " skip.."));
            }
            AlertTreeNode bCurChild = branch.getChild();
            for (int i = 0; i < bCompNode.getNbChildren(); i++) {

                if (bCurChild == null) {
                    throw(new AlertException("new branch from node" +
                                             nodeIndex +
                                             " is corrupted, child " + i +
                                             " is null, stop processing " +
                                             "branch.."));
                    //deleteNodeProperties(nodeIndex);
                    //parentNode.setChild(null);
                }

                prop = bCompNode.getPropertyChild(i);
                if (prop == null) {
                    throw(new AlertException("new branch from node" +
                                             nodeIndex + 
                                             " is corrupted, prop is null," +
                                             " stop processing branch..."));
                    //deleteNodeProperties(nodeIndex);
                    //parentNode.setChild(null);
                }

                //
                // Create or update the current service for this node.
                //
                boolean newSvc = mergeNewService(bCurChild, bCompNode,
                                                 compNode, nodeIndex, prop);
                if (newSvc) {
                    // Add new service branch to the tree
                    if (lastChild == null) {
                        parentNode.setChild(bCurChild);
                    } else {
                        lastChild.setSibling(bCurChild);
                    }
                    lastChild = bCurChild;
                    bCurChild.setParent(parentNode);
                }


                bCurChild = bCurChild.getSibling();
            }
        }
    }

    //
    // Called from mergeNewBranch to update/create the service
    // The code becomes more complex because some services such
    // as DiskMonitor initialize asynchronously so we may discover
    // new properties when new branch arrive.
    // Since we don't know in advance, we may start updating a service
    // and discover some new leaves and as a result we generate an exception,
    // delete the current entries for the service and recreate the service.
    // 
    // Returns true if we added a new service, false otherwise.
    //
    private boolean mergeNewService(AlertTreeNode bNodeChild,
                                    AlertComponent bCompNode,
                                    AlertNode compNode,
                                    int nodeIndex,
                                    AlertComponent.AlertProperty svcProp)
        throws AlertException {

        boolean success = false;

        while (!success) {
            if (servicesNode[nodeIndex].containsKey(svcProp.getName())) {

                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("update svc, node = " +
                                nodeIndex + " svc = " + svcProp.getName());
                }

                try {
                    updateService(bNodeChild, nodeIndex, svcProp.getName());
                } catch (AlertException ae) {
                    logger.warning("" + ae); 
                    
                    // Delete the current service and its properties before
                    // we recreate it.
                    deleteNodeSvcProperties(nodeIndex, svcProp.getName());
                    continue;
                }

                return false;

            } else {

                AlertComponent curComp =                     
                    bCompNode.getPropertyValueComponent(svcProp.getName());
                if (curComp == null) {
                    throw(new AlertException("new branch from node" +
                                             svcProp.getName() + " on node" +
                                             nodeIndex +
                                             " is corrupted. component" +
                                             " is null, stop" +
                                             " processing branch..."));
                }

                // Add new svc to the current AlertNode
                compNode.addService(svcProp, curComp);


                int startSvcIndex = propertiesNode[nodeIndex].size();

                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("create svc, node = " +
                                nodeIndex + " svc = " + svcProp.getName());
                }

                createService(bNodeChild, nodeIndex, svcProp.getName());
                    
                int endSvcIndex = propertiesNode[nodeIndex].size() - 1;
                IndexArray index =
                    new IndexArray(startSvcIndex, endSvcIndex);
                servicesNode[nodeIndex].put(svcProp.getName(), index);

                return true;
            }
        }
        // For compiler, to avoid complaining about missing return...
        throw (new InternalException("unexpected path..."));
    }


    private int getIndexFromNodeId(int nodeid) {
        int index = -1;
        AlertComponent compRoot = root.getComponent();
        for (int i = 0; i < compRoot.getNbChildren(); i++) {
            try {
                AlertComponent.AlertProperty prop =
                    compRoot.getPropertyChild(i);
                AlertNode node = (AlertNode)
                    compRoot.getPropertyValueComponent(prop.getName());
                if (node.nodeId() == nodeid) {
                    index = i;
                    break;
                }
            } catch (AlertException ae) {
                logger.severe("AlertNode for index " + i +
                              " does not exist");
            }
        }
        return index;
    }


    private AlertNode getNodeFromIndex(int nodeIndex) {

        AlertComponent compRoot = root.getComponent();
        try {
            AlertComponent.AlertProperty prop =
                compRoot.getPropertyChild(nodeIndex);
            AlertNode node = (AlertNode)
                compRoot.getPropertyValueComponent(prop.getName());
            return node;
        } catch (AlertException ae) {
            logger.severe("AlertNode for index " + nodeIndex +
                          " does not exist");
            return null;
        }
    }


    private int getNodeIdFromIndex(int nodeIndex) {

        AlertNode node = getNodeFromIndex(nodeIndex);
        if (node != null) {
            return node.nodeId();
        } else {
            return -1;
        }
    }


    //
    // The service represented by the node 'parent' already exists
    // Update values for all the leaves and trigger alerts if necessary
    //
    private void  updateService(AlertTreeNode parent, 
                                  int nodeIndex, String svcName)
        throws AlertException {

        AlertComponent parentComp = parent.getComponent();
        if (parentComp == null) {
            return;
        }

        synchronized(AlertCorrelationEngine.class) {

            AlertTreeNode child = parent.getChild();
            for (int i = 0; i < parentComp.getNbChildren(); i++) {

                if (child == null) {
                    logger.severe("new branch for service " + svcName +
                                  " on node nodeIndex = " + nodeIndex +
                                  " is corrupted. child is null, " +
                                  "stop processing subBranch...");
                    return;
                }

                AlertComponent.AlertProperty prop =
                    parentComp.getPropertyChild(i);
                if (prop == null) {
                    logger.severe("new branch for service " + svcName +
                                  " on node nodeIndex = " + nodeIndex +
                                  " is corrupted. property is null, " +
                                  "stop processing subBranch...");
                    return;
                }

                if (prop.getType() == AlertType.COMPOSITE) {
                    updateService(child, nodeIndex, svcName);
                } else {

                    AlertTreeNodeLeaf leaf = child.getLeaf();
                    if (leaf == null) {
                        logger.severe("new branch for service " + svcName +
                                      " on node nodeIndex = " + nodeIndex +
                                      " is corrupted. leaf is null, " +
                                      "stop processing subBranch...");
                        return;
                    }

                    AlertTreeNodeLeaf existingLeaf =
                        (AlertTreeNodeLeaf) propertiesMap.get(leaf.getPath());
                    if (existingLeaf == null) {
                        throw (new AlertException("property " + leaf.getPath() +
                                      " does not exist in current tree, " +
                                                  "recreate the service..."));

                    }
                    if (existingLeaf.getType() != leaf.getType()) {
                        logger.severe("property type for " + leaf.getPath() +
                                      "does not match: existing type = " +
                                      existingLeaf.getType() +
                                      " new type = " +
                                      leaf.getType() + ", skip leaf...");
                        child = child.getSibling();
                        continue;
                    }
                    existingLeaf.setValue(leaf.getValue());
                }
                child = child.getSibling();
            }
        }
    }

    //
    // The service represeneted by 'parent' does not exist yet. 
    //
    // - add entry in the propertyMap hash table
    // - add the 'leaf' in the array of properties.
    // - set the Observers for each leaf.
    //
    private void createService(AlertTreeNode parent,
                               int nodeIndex, String svcName)  
        throws AlertException {

        AlertComponent parentComp = parent.getComponent();
        if (parentComp == null) {
            return;
        }

        synchronized(AlertCorrelationEngine.class) {

            AlertTreeNode child = parent.getChild();
            for (int i = 0; i < parentComp.getNbChildren(); i++) {

                if (child == null) {
                    logger.severe("new branch for service " + svcName +
                                  " on node nodeIndex = " + nodeIndex +
                                  " is corrupted. child is null, " +
                                  "stop processing subBranch...");
                    return;
                }

                AlertComponent.AlertProperty prop =
                    parentComp.getPropertyChild(i);
                if (prop == null) {
                    logger.severe("new branch for service " + svcName +
                                  " on node nodeIndex = " + nodeIndex +
                                  " is corrupted. property is null, " +
                                  "stop processing subBranch...");
                    return;
                }


                if (prop.getType() == AlertType.COMPOSITE) {
                    createService(child, nodeIndex, svcName);                
                } else {

                    AlertTreeNodeLeaf nodeLeaf = child.getLeaf();
                    if (nodeLeaf == null) {
                        logger.severe("new branch for service " + svcName +
                                      " on node nodeIndex = " + nodeIndex +
                                      " is corrupted. leaf is null, " +
                                      "stop processing subBranch...");
                        return;
                    }
                    nodeLeaf.initObservers();

                    // Add index of the property in the Hash
                    propertiesMap.put(nodeLeaf.getPath(), nodeLeaf);

                    // Add property in the array
                    propertiesNode[nodeIndex].add(nodeLeaf);

                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("add new leaf " + nodeLeaf.getPath());
                    }
                    addObserversToLeaf(nodeLeaf);
                }
                child = child.getSibling();
            }
        }
    }

    //
    // Recursive method used to add all the existing leaves
    // in the initRoot tree and in the propertiesInitMap
    // Hash table.
    //
    private void populateInitMap(AlertTreeNode parent) 
        throws AlertException {

        AlertComponent parentComp = parent.getComponent();
        if (parentComp == null) {
            return;
        }

        AlertTreeNode child = parent.getChild();
        for (int i = 0; i < parentComp.getNbChildren(); i++) {
            
            AlertComponent.AlertProperty prop =
                parentComp.getPropertyChild(i);
            
            if (prop.getType() == AlertType.COMPOSITE) {
                populateInitMap(child);                
            } else {
                
                AlertTreeNodeLeaf nodeLeaf = child.getLeaf();
                
                // Add index of the property in the Hash
                propertiesInitMap.put(nodeLeaf.getPath(), nodeLeaf);
                
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("add new leaf " + nodeLeaf.getPath());
                }
            }
            child = child.getSibling();
        }
    }

    //
    // Delete the properties when this node leaves the cluster,
    // or if the NodeMgr proxy for the node is not available.
    //
    private void deleteNodeProperties(int nodeIndex) {
        synchronized(AlertCorrelationEngine.class) {
            for (int i = 0; i < propertiesNode[nodeIndex].size(); i++) {
                
                AlertTreeNodeLeaf leaf =
                    (AlertTreeNodeLeaf) propertiesNode[nodeIndex].get(i);
                if (leaf != null) {
                    propertiesMap.remove(leaf.getPath());
                }
            }
            servicesNode[nodeIndex].clear();
            propertiesNode[nodeIndex].clear();
        }
    }


    //
    // Delete the properties for a given service on a given node.
    // This happens when we discover that a service was incomplete
    // (due to asynchronous initialization) and we need to recreate
    // the service.
    // 
    private void deleteNodeSvcProperties(int nodeIndex, String svc) {
        synchronized(AlertCorrelationEngine.class) {
            IndexArray index = (IndexArray) 
                servicesNode[nodeIndex].remove(svc);

            for (int j = index.getStartIndex();
                 j <= index.getStopIndex();
                 j++) {

                AlertTreeNodeLeaf leaf = null;
                try {
                    //
                    // Remove a range : always remove the first element since
                    // this operation shifts the whole indexes for the array.
                    //
                    leaf = (AlertTreeNodeLeaf) 
                        propertiesNode[nodeIndex].remove(index.getStartIndex());
                } catch (IndexOutOfBoundsException oob) {
                    logger.severe("Index Out of Bound : svc = " + svc + 
                                  ", startIndex = " + index.getStartIndex() +
                                  ", stopIndex = " + index.getStopIndex() +
                                  ", index =  " + j +  oob);
                }
                if (leaf != null) {
                    propertiesMap.remove(leaf.getPath());
                }
            }
        }
    }


    //
    // Check if there are any pending CMM notif in the
    // queue and process them
    //
    private void checkPendingCMMNotif()
        throws AlertException {

        NodeChange curNotif = null;
        while (true) {
            synchronized(cmmNotifications) {
                try {
                    curNotif = (NodeChange) cmmNotifications.removeFirst();
                } catch (NoSuchElementException no) {
                    return;
                }
            }      
            processCMMNotif(curNotif);
        }
    }

    //
    // Process the current CMM notification
    //
    private void processCMMNotif(NodeChange arg)
        throws AlertException {

        CMMObservable observable = new CMMObservable();
        synchronized(AlertCorrelationEngine.class) {
            Iterator it = observers.values().iterator();
            while (it.hasNext()) {
                AlertObserver obs = (AlertObserver) it.next();
                if (obs.isRegisteredCMM()) {
                    observable.addObserver(obs.getObserver());
                }
            }
        }
        observable.notifyAllObservers(arg);
        synchronized(AlertCorrelationEngine.class) {
            if (arg.getCause() == NodeChange.MEMBER_LEFT) {
                int index = getIndexFromNodeId(arg.nodeId());
                if (index != -1) {
                    // Removes all properties for this node.
                    deleteNodeProperties(index);
                    // Free memory for subtree
                    AlertTreeNode node = root.getChild(index);
                    node.setChild(null);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("CMM notification node " +
                                    arg.nodeId() + 
                                    "left the cluster");
                    }
                } 
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("CMM notification node " + arg.nodeId() + 
                                "joins the cluster");
                }
            }
        }
    }

    // 
    // Called when registering a new CMM notifications
    // If the master node just failed over, the new master comes in
    // and check using this method to see if any clients missed
    // some CMM notifications.
    //
    private void triggerMissedCMMNotifications(Observer obs) {

        if (initRoot == null) {
            return;
        }

        List notifList = new ArrayList();

        synchronized(AlertCorrelationEngine.class) {
            AlertTreeNode initNode = initRoot.getChild();
            AlertTreeNode curNode = root.getChild();

            for (int i = 0; i < CMM.MAX_NODES; i++) {

                NodeChange cmmNotif = null;
                AlertNode node = (AlertNode) curNode.getComponent();
            
                AlertTreeNode initChild = initNode.getChild();
                AlertTreeNode curChild = curNode.getChild();
             
                if ((initChild == null)  &&
                  (curChild != null)) {
                    cmmNotif = new NodeChange(node.nodeId(),
                      NodeChange.MEMBER_JOINED);
                }
                // (should be caught by CMM notification)
                //if  ((initChild != null)  &&
                //     (curChild == null)) {
                //    cmmNotif = new NodeChange(node.nodeId(),
                //                              NodeChange.MEMBER_LEFT);
                //}
                if (cmmNotif != null) {
                    notifList.add(cmmNotif);
                }
             
                initNode = initNode.getSibling();
                curNode = curNode.getSibling();
            }
        }

        CMMObservable observable = new CMMObservable();
        observable.addObserver(obs);
        for (int i = 0; i < notifList.size(); i++) {
            NodeChange cmmNotif = (NodeChange) notifList.get(i);
            observable.notifyAllObservers(cmmNotif);
        }
    }
    
    //
    // Called when registering some notifications for properties
    // . If this is a registartion for a 'change' event, check
    //   from the set of current leaves matching the rule,
    //   their previous value in the initRoot tree and trigger the
    //   the notification if this has changed
    // . If this is any other event, we don't bother checking; we 'll do
    //   that next time we have a new branch-- we may miss some but this is
    //   ok since this means the value went back below/above the threshold.
    //   
    private void triggerMissedNotifications(Observer obs,
                                            List leaves, int ev) {
        if (initRoot == null) {
            return;
        }
        if (ev !=  AlertApi.AlertEvent.EVT_CHANGE) {
            return;
        }

        for (int i = 0; i < leaves.size(); i++) {
            AlertTreeNodeLeaf leaf = (AlertTreeNodeLeaf) leaves.get(i);
            AlertTreeNodeLeaf oldLeaf = 
                (AlertTreeNodeLeaf) propertiesInitMap.get(leaf.getPath());
            if (oldLeaf == null) {
                continue;
            }
            oldLeaf.notifyObserverIfChange(obs, leaf.getValue());
        }
    }


    //
    // Called by register() to retrieve the array of properties
    // matching the rule.
    //
    private List getLeavesMatchingRule(String node,
                                            String svc,
                                            String prop)
        throws AlertException {

        List res = new ArrayList();
        // All the leaves...
        if (node.equals("*") &&
            svc.equals("*") &&
            prop.equals("*")) {
            for (int i = 0; i < propertiesMap.size(); i++) {
                Iterator it = propertiesMap.values().iterator();
                while (it.hasNext()) {
                    res.add(it.next());
                }
            }
            return res;
            
         // All the nodes. 
        } else if (node.equals("*")) {
            for (int i = 0; i < CMM.MAX_NODES; i++) {
                if (propertiesNode[i].size() == 0) {
                    continue;
                }
                String curNode =
                    ((AlertComponent.AlertProperty) root.getComponent().
                     getPropertyChild(i)).getName();
                getLeavesMatchingRule(i, curNode, svc, prop, res);
            }
            return res;
        } else {

            // One specific node
            AlertComponent compRoot = root.getComponent();
            for (int i = 0; i < compRoot.getNbChildren(); i++) {
                String curNode =
                    ((AlertComponent.AlertProperty) root.getComponent().
                     getPropertyChild(i)).getName();
                if (node.equals(curNode)) {
                    if (propertiesNode[i].size() != 0) {
                        getLeavesMatchingRule(i, node, svc, prop, res);
                    }
                }
            }
            return res;
        }
    }
           
    //
    // Called by previous method getLeavesMatchingRule
    //
    private void getLeavesMatchingRule(int idx,
                                       String node,
                                       String svc,
                                       String prop,
                                       List res)
        throws AlertException {

        String rule = "root." + node + "." + svc + "." + prop;

        // All the leaves for this node
        if (svc.equals("*")) {
            for (int i = 0; i < propertiesNode[idx].size(); i++) {

                AlertTreeNodeLeaf leaf = (AlertTreeNodeLeaf) 
                    propertiesNode[idx].get(i);                
                res.add(leaf);
            }
        } else {
            IndexArray index = (IndexArray) servicesNode[idx].get(svc);
            if (index == null) {
                throw(new AlertException("Invalid rule: service " +
                                         svc + " does not exist" ));
            }
            // All the leaves for the service 'svc'
            if (prop.equals("*")) {
                for (int j = index.getStartIndex();
                     j <= index.getStopIndex();
                     j++) {

                    AlertTreeNodeLeaf leaf = (AlertTreeNodeLeaf) 
                        propertiesNode[idx].get(j);                    
                    res.add(leaf);
                }
                // specific property for the service 'svc'
            } else {
                AlertTreeNodeLeaf leaf =
                    (AlertTreeNodeLeaf) propertiesMap.get(rule);
                if (leaf != null) {
                    res.add(leaf);
                }
            }
        }
    }

    //
    // When we discover a new leaf we need to check if some
    // registration of notitfication were set for this leaf,
    // and add the corresponding observer(s).
    //
    private void addObserversToLeaf(AlertTreeNodeLeaf leaf) {
        Iterator it = observers.values().iterator();
        while (it.hasNext()) {
            AlertObserver obs = (AlertObserver) it.next();
            if (obs.isLeafMatchingNotification(leaf)) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("add observer to leaf = " +
                                leaf.getPath());
                }
                leaf.addObserver(obs);
            }
         }
    }

    //
    // Returns Node Manager proxy for node 'nodeid'
    //
    private NodeMgrService.Proxy getProxy(int node) { 
        Object obj = ServiceManager.proxyFor(node);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            if (node == ServiceManager.LOCAL_NODE) {
                throw new InternalException("cannot fetch NodeManager Proxy" +
                                            " for local node");
            } else {
                logger.warning("cannot retrieve proxy for node " + node);
            }
        }
        return (NodeMgrService.Proxy) obj;
   }


    private class CMMObservable extends Observable {
        public  CMMObservable() {
            super();
        }
        public void notifyAllObservers(Object arg) {
            setChanged();
            notifyObservers(arg);
        }
    }

                                               
    //
    // Used to keep the indexes of each service properties
    // in the per node property Array
    //
    private class IndexArray {
        private int startIndex;
        private int stopIndex;

        public IndexArray(int start, int stop) {
            startIndex = start;
            stopIndex = stop;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getStopIndex() {
            return stopIndex;
        }
    }
}
