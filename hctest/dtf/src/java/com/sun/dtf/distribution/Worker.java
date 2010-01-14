package com.sun.dtf.distribution;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;

public class Worker extends Thread {

    private Action _action = null;
    private DTFState _state = null;
    private WorkState _dist = null;
    private DTFException _exception = null;
    private int _id = -1;
    
    /**
     * 
     * @param action
     * @param dist
     * @param state
     * @param id
     */
    public Worker(Action action,
                       WorkState dist,
                       DTFState state,
                       int id) {
        _action = action;
        _state = state;
        _dist = dist;
        _id = id;
       
        // set my state nicely
        ActionState.getInstance().setState(getName(), _state);
    }
    
    public int getWorkerId() { return _id; }
    
    public void run() {
        try {
            if (_dist == null) 
                _action.execute();
            else                 
                while (_dist.doWork(getWorkerId())) {
                    _action.execute();
                }
        } catch (Throwable t) {
            if (_dist == null)
                _exception = new DTFException("Error executing action.",t);
            else
                _dist.reportException(new DTFException("Error executing action.",t));
        }
        
        if (_dist != null)
            _dist.allDone();
    }

    public void waitFor() throws DTFException {
        try {
            join();
        } catch (InterruptedException e) {
            throw new DTFException("Interrupted.",e);
        }
        
        if (_exception != null)
            throw _exception;
    }
}
