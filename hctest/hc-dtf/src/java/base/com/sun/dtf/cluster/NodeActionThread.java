package com.sun.dtf.cluster;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;

public class NodeActionThread extends Thread {

    public static final int EXECUTION_MODE    = 0;
    public static final int PRECONDITION_MODE = 1;
   
    private int _mode = 0;
    private NodeAction _naction = null;
    private NodeInterface _node = null;
    
    private DTFException _exception = null;
    
    public NodeActionThread(NodeAction naction,
                            NodeInterface node,
                            int mode) { 
        /*
         * Creating a new thread requires care with what we do with the current
         * state that needs to be shared across threads. This needs to be made
         * simpler for most actions but for now I can keep track of this.
         */
        ActionState as = ActionState.getInstance();
        DTFState state = as.getState().duplicate();
        ActionState.getInstance().setState(getName(),state);
        _naction = naction;
        _node = node;
        _mode = mode;
    }
    
    public void run() { 
        try {
            
            if (_mode == EXECUTION_MODE) 
                _naction.execute(_node);
            
            if (_mode == PRECONDITION_MODE) 
                _naction.preCondition(_node);
                
        } catch (DTFException e) {
            _exception = e;
        }
    }
    
    public void checkForException() throws DTFException { 
        if (_exception != null) 
            throw _exception;
    } 
    
    public NodeInterface getNode() { return _node; } 
    
}
