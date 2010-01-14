package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class ActionException extends DTFException {

    public ActionException(String msg) {
        super(msg);
    }
    
    public ActionException(String msg, Throwable t) {
        super(msg,t);
    }

}
