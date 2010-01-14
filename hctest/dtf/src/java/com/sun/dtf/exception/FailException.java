package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class FailException extends DTFException {

    public FailException(String msg) {
        super(msg);
    }
    
    public FailException(String msg, Throwable t) {
        super(msg,t);
    }
}
