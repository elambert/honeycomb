package com.sun.dtf.cluster;

import java.util.ArrayList;
import java.util.Iterator;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

public abstract class NodeAction extends Action {

    /**
     * @dtf.attr node
     * @dtf.attr.desc specifies the node id to run the current action on.
     */
    private String node = null;
   
    /**
     * Sets that this sub Action of NodeAction has a PreCondition that must 
     * succeed before we can proceed with the execution of this action. 
     */
    private boolean hasPreCondition = false;
    protected void setPreConditionChecing() { hasPreCondition = true; } 
    
    /**
     * Execute method for NodeAction actions. Your execute method should only 
     * operate on the single NodeInterface passed as an argument.
     *  
     * @param node
     * @throws DTFException
     */
    public abstract void execute(NodeInterface node) throws DTFException;
    
    public void preCondition(NodeInterface node) throws DTFException { }
       
    public void execute() throws DTFException {
        Cluster cluster = Cluster.getInstance();
        NodeInterface node = getClusterNode();
        
        if (node == null) {
            if (hasPreCondition) { 
                getLogger().info("Checking preconditions for " + this + 
                                 ", on all nodes.");
                int nodeCount = cluster.getNumNodes();
                NodeActionThread nat = null;
                ArrayList actions = new ArrayList();
                
                for (int i = 1; i <= nodeCount; i++) { 
                    node = cluster.getNode(i);
                    nat = new NodeActionThread(this,
                                               node,
                                               NodeActionThread.PRECONDITION_MODE);
                    nat.start();
                    actions.add(nat);
                }
               
                Iterator nats = actions.iterator();
                while (nats.hasNext()) { 
                    nat = (NodeActionThread) nats.next();
                    try {
                        nat.join();
                    } catch (InterruptedException e) {
                        getLogger().warn("Interrupted NodeActionThread.",e);
                    }
                    nat.checkForException();
                }
            }
            
            getLogger().info("Executing " + this + ", on all nodes.");
            int nodeCount = cluster.getNumNodes();
            NodeActionThread nat = null;
            ArrayList actions = new ArrayList();
            
            for (int i = 1; i <= nodeCount; i++) { 
                node = cluster.getNode(i);
                nat = new NodeActionThread(this, node, NodeActionThread.EXECUTION_MODE);
                nat.start();
                actions.add(nat);
            }
           
            Iterator nats = actions.iterator();
            while (nats.hasNext()) { 
                nat = (NodeActionThread) nats.next();
                try {
                    nat.join();
                } catch (InterruptedException e) {
                    getLogger().warn("Interrupted NodeActionThread.",e);
                }
                nat.checkForException();
            }
            
        } else {
            getLogger().info("Executing " + this + ", on node " + getNode());
            execute(node);
        }
    }

    protected NodeInterface getClusterNode() throws DTFException { 
        if (getNode() == null || getNode().equals("all") ) { 
            return null;
        } else {
            if (getNode().matches("[0-9]*")) { 
                int nodeId = new Integer(getNode()).intValue();
                return Cluster.getInstance().getNode(nodeId); 
            }
        }
       
        throw new ParseException("Unkown node [" + getNode() + "]");
    }

    public String getNode() throws ParseException { return replaceProperties(node); }
    public void setNode(String node) { this.node = node; }
}
