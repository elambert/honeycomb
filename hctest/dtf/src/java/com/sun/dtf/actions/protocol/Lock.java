package com.sun.dtf.actions.protocol;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.NodeInfo;
import com.sun.dtf.NodeState;
import com.sun.dtf.actions.Action;
import com.sun.dtf.comm.rpc.ActionResult;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.LockException;
import com.sun.dtf.util.ThreadUtil;

public class Lock extends Connect {

    private String owner = null;

    private String name = null;
    /*
     * Used for direct connection between executer and agents.
     */
    private String address = null;
    private int port = 0;
    
    private long timeout = 0;
    
    public Lock() { }
    
    public Lock(String id, String refid, long timeout) 
           throws DTFException{
        setId(id);
        setOwner(Action.getLocalID());
        setAddress(getConfig().getProperty(DTFProperties.DTF_LISTEN_ADDR));
        setPort(getConfig().getPropertyAsInt(DTFProperties.DTF_LISTEN_PORT));
        setName(refid);
        setTimeout(timeout);
    }
    
    public void execute() throws DTFException {
        if (getLogger().isDebugEnabled())
            getLogger().debug("Attemtping to lock: " + this);

        NodeState ns = NodeState.getInstance();
        NodeInfo ni = null;
       
        long start = System.currentTimeMillis();
        LockException excep = null;
        do { 
            try { 
                ni = ns.lockNode(this);
                break;
            } catch (LockException e) { 
                //if (getLogger().isDebugEnabled())
                getLogger().info("Retrying lock: " + this);
                excep = e; 
                ThreadUtil.pause(1000);
            }
        } while (System.currentTimeMillis() - start < getTimeout());
        
        if (excep != null && ni == null) 
            throw excep;
               
        /*
         * Communicate with locked component to register the owner information
         * that can be later used to identify who locked this component as well 
         * as allow communication between a locked component and the component
         * that holds this lock.
         */
        SetupAgent sa = new SetupAgent(this);
        ActionResult ar = ni.getClient().sendAction(ni.getId(), sa); 
        // must check that we did in fact succeed to send the action.
        ar.execute();
        
        /*
         * Now that we have a NodeInfo that means we've locked a 
         * component and we can return with this NodeInfo object
         * the direct connection information for the node requested.
         */
        setAddress(ni.getAddress());
        setPort(ni.getPort());
        
        copy(ni);
        getLogger().info("Locked node " + ni);
    }
    
    public void setName(String name) { this.name = name; } 
    public String getName() { return name; } 

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public void setTimeout(long timeout) { this.timeout = timeout; } 
    public long getTimeout() { return timeout; } 
}
