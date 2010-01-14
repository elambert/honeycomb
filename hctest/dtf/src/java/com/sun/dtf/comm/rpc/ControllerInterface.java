package com.sun.dtf.comm.rpc;

import com.sun.dtf.actions.protocol.Connect;
import com.sun.dtf.exception.DTFException;

public interface ControllerInterface {

    /**
     * 
     * @param connect
     * @return
     */
    public ActionResult register(Connect connect) throws DTFException;
    
   
    /**
     * 
     * @param connect
     * @return
     */
    public ActionResult unregister(Connect connect) throws DTFException;
}