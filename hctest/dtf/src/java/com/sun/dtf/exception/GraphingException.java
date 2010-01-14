package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class GraphingException extends DTFException {

    public GraphingException(String msg) {
        super(msg);
    }
    
    public GraphingException(String msg, Throwable t) {
        super(msg,t);
    }
}
