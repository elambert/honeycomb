package com.sun.dtf.util;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.actions.Action;
import com.sun.dtf.comm.Comm;
import com.sun.dtf.exception.DTFException;

public class ActionThread extends Thread {
    
    private boolean _running = true;
  
    private Action _action = null;
    private DTFException _exception = null;
    private boolean _newComm = false;
    
    public ActionThread() { }
    public ActionThread(Action action) { _action = action; }
    
    public void shutdown() { _running = false; }
    public boolean running() { return _running; }
    public void setNewcomm() { _newComm = true; } 
    
    public void setAction(Action action) { _action = action; } 
    
    public void run() {
        try {
            if (_newComm) {
                _action.getConfig().setProperty(DTFProperties.DTF_NODE_NAME, 
                                               "dtfx-" + getName());
                _action.getState().setComm(new Comm(_action.getConfig()));
            }
            _action.execute();
        } catch (DTFException e) {
            _exception = e;
        } finally {
           if (_newComm)
               Action.getState().getComm().shutdown();
        }
    }
    
    public void checkForException() throws DTFException {
        if (_exception != null) 
            throw _exception;
    }
}
