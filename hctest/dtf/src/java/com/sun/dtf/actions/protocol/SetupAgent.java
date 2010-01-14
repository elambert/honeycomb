package com.sun.dtf.actions.protocol;

import com.sun.dtf.DTFNode;
import com.sun.dtf.comm.Comm;
import com.sun.dtf.comm.CommClient;
import com.sun.dtf.exception.DTFException;

/**
 * Setups necessary information for the DTFA to be able to communicate with the 
 * DTFX in case of asynchronous events.
 * 
 */
public class SetupAgent extends Lock {
   
    public SetupAgent() { }
    
    public SetupAgent(Lock lock) { 
        setId(lock.getName());
        setOwner(lock.getOwner());
        setAddress(lock.getAddress());
        setPort(lock.getPort());
    }
    
    public void execute() throws DTFException {
        if (getLogger().isDebugEnabled())
            getLogger().debug("Setting id to [" + getId() + "] from owner [" +
                              getOwner() + "]");
        
        CommClient client = new CommClient(getAddress(), getPort());
        
        if (client.heartbeat().booleanValue()) { 
            if (getLogger().isDebugEnabled())
                getLogger().debug("Direction connection to component being used.");

            Comm.addClient(getOwner(), client);
        }
        
        DTFNode.setOwner(this);
    }
}
