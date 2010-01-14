package com.sun.dtf.comm.rpc;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

public interface NodeInterface {
   
    /**
     * 
     * @return
     */
    public Boolean heartbeat();
   
    /**
     * 
     * @param action
     * @return
     */
    public ActionResult execute(String id, Action action) throws DTFException;
}
