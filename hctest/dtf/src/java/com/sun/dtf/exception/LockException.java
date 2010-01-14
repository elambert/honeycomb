package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class LockException extends DTFException {

    public LockException(String msg) {
        super(msg);
    }
    
    public LockException(String msg, Throwable t) {
        super(msg,t);
    }

}
