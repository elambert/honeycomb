package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class ParseException extends DTFException {

    public ParseException(String msg) {
        super(msg);
    }
    
    public ParseException(String msg, Throwable t) {
        super(msg,t);
    }
}
