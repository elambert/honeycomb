package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class UnsupportedFeatureException extends DTFException {

    public UnsupportedFeatureException(String msg) {
        super(msg);
    }
    
    public UnsupportedFeatureException(String msg, Throwable t) {
        super(msg,t);
    }
}
