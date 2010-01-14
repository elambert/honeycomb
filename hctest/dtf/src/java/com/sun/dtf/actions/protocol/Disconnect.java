package com.sun.dtf.actions.protocol;

import com.sun.dtf.NodeState;
import com.sun.dtf.exception.DTFException;

/**
 * This action is used internally for the protocol connect action for 
 * establishing a connection to the DTFC
 * 
 * @author Rodney Gomes
 */
public class Disconnect extends Connect {
    public Disconnect()  { }

    public void execute() throws DTFException {
        NodeState ni = NodeState.getInstance(); 
        ni.removeNode(this); 
    }
}
