package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class DBException extends DTFException {

    public DBException(String msg) {
        super(msg);
    }
    
    public DBException(String msg, Throwable t) {
        super(msg,t);
    }
}
