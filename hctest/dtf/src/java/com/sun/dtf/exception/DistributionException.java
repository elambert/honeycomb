package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class DistributionException extends DTFException {

    public DistributionException(String msg) {
        super(msg);
    }
    
    public DistributionException(String msg, Throwable t) {
        super(msg,t);
    }
}
