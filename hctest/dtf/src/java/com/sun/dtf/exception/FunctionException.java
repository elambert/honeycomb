package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class FunctionException extends DTFException {

    public FunctionException(String msg) {
        super(msg);
    }
    
    public FunctionException(String msg, Throwable t) {
        super(msg,t);
    }
}
