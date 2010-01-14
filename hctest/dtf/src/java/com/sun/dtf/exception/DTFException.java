package com.sun.dtf.exception;

import com.sun.dtf.actions.Action;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class DTFException extends Exception {
    
    private boolean alreadyReported = false;

    public DTFException() { }

    public DTFException(String msg) {
        super(msg + Action.getXMLLocation());
    }
    
    public DTFException(String msg, Throwable t) { 
        super(msg + Action.getXMLLocation(),t); 
    }
    
    public boolean wasLogged() { return alreadyReported; }
    public void logged() { alreadyReported = true; }
}
