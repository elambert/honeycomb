package com.sun.dtf;

import java.util.ArrayList;
import java.util.Hashtable;

import com.sun.dtf.actions.protocol.Connect;
import com.sun.dtf.actions.protocol.Lock;
import com.sun.dtf.comm.CommClient;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.LockException;
import com.sun.dtf.logger.DTFLogger;


public class NodeState {
    private static DTFLogger _logger = DTFLogger.getLogger(NodeState.class);
    private static NodeState instance = null;

    public static NodeState getInstance() {
       if (instance == null) {
           instance = new NodeState();
       }
       return instance;
    }
   
    private ArrayList connectedNodes = null;
    private Hashtable lockedNodes = null;
    
    private NodeState() { 
        connectedNodes = new ArrayList();
        lockedNodes = new Hashtable();
    }
    
    public ArrayList getRegisteredNodes() { 
        return connectedNodes;
    }
    
    public synchronized NodeInfo addNode(Connect connect, CommClient client) throws DTFException{
        NodeInfo ni = new NodeInfo(connect, client);
        
        _logger.info("Registering " + ni);
        
        if (connectedNodes.contains(ni)) 
            throw new DTFException("Node already registered with id: " + 
                                   ni.getId());
        
        connectedNodes.add(ni);
        return ni;
    }
    
    public synchronized NodeInfo getNodeInfo(String id) throws DTFException { 
        for (int i = 0; i < connectedNodes.size(); i++) {
            NodeInfo ni = (NodeInfo)connectedNodes.get(i); 
            if (ni.getId().equals(id))
                return ni;
        }
        throw new DTFException("Unable to find node: " + id);
    }
    
    public synchronized void removeNode(Connect conn) throws DTFException{
        NodeInfo ni = new NodeInfo(conn,null);
       
        _logger.info("Unregistering " + conn);
        
        if (!connectedNodes.contains(ni)) 
            throw new DTFException("Node not registered with id: " + 
                                   conn.getId());
       
        connectedNodes.remove(ni);
        
        // unlock all components held by this agent
        ArrayList locks = (ArrayList)lockedNodes.get(conn.getId());

        if (locks != null && locks.size() != 0) {
            _logger.info("Unlocking all compoennts for " + conn.getId());
            String ids = "";
            for(int i = 0; i < locks.size(); i++) {
                NodeInfo locked = (NodeInfo) locks.get(i);
                ids += locked.getId() + ", ";
                locked.unlock();
            }
            _logger.info("Unlocked components {" + ids + "}");
        } else 
            _logger.info("No locked components.");
        
        lockedNodes.remove(conn.getId());
    }
    
    private void addNode(String id, NodeInfo node) {
        ArrayList locks = (ArrayList)lockedNodes.get(id);
        if (locks == null)  {
            locks = new ArrayList();
            lockedNodes.put(id, locks);
        }
        locks.add(node);
    }
  
    private void removeNode(String id, NodeInfo node) throws ActionException {
        ArrayList locks = (ArrayList)lockedNodes.get(id);
       
        if (locks == null) 
            throw new ActionException("Agent never locked by this agent.");
        
        locks.remove(node);
    }
    
    public synchronized NodeInfo lockNode(Lock lock) throws DTFException { 
        NodeInfo niLock = new NodeInfo(lock);
        for(int i = 0; i < connectedNodes.size(); i++) {
            NodeInfo ni = (NodeInfo) connectedNodes.get(i);
               
            if (_logger.isDebugEnabled());
                _logger.debug("Trying to match " + ni + " with " + niLock);
                
            if (!ni.isLocked() && niLock.matches(ni)) { 
                if (_logger.isDebugEnabled());
                    _logger.debug("Locked " + ni + " for " + lock.getOwner());
                    
                ni.lock();
                addNode(lock.getOwner(),ni);
                return ni;
            } else 
                if (_logger.isDebugEnabled())
                    _logger.debug("Didn't find match " + ni);
        }
        throw new LockException("No agent found to match: " + niLock);
    }
    
    public synchronized NodeInfo unlockNode(Lock lock) throws DTFException {
        NodeInfo niLock = new NodeInfo(lock);
        for(int i = 0; i < connectedNodes.size(); i++) {
            NodeInfo ni = (NodeInfo) connectedNodes.get(i);
            if (_logger.isDebugEnabled())
                _logger.debug("Trying to match " + ni + " with " + niLock);
            if (ni.isLocked() && niLock.equals(ni)) { 
                ni.unlock();
                removeNode(lock.getOwner(), ni);
                if (_logger.isDebugEnabled())
                    _logger.debug("Unlocked " + ni + " for " + lock.getOwner());
                return ni;
            }
        }
        throw new ActionException("Agent: " + niLock + " never locked.");
    }
}
