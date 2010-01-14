package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class RecorderException extends DTFException {

    public RecorderException(String msg) {
        super(msg);
    }
    
    public RecorderException(String msg, Throwable t) {
        super(msg,t);
    }
}
