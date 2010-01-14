package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class StatsException extends DTFException {

    public StatsException(String msg) {
        super(msg);
    }
    
    public StatsException(String msg, Throwable t) {
        super(msg,t);
    }
}
