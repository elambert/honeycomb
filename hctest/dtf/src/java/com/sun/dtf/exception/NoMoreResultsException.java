package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class NoMoreResultsException extends QueryException {

    public NoMoreResultsException(String msg) {
        super(msg);
    }
    
    public NoMoreResultsException(String msg, Throwable t) {
        super(msg,t);
    }
}
