package com.sun.dtf.exception;

public class RangeException extends DTFException {

    public RangeException(String msg) {
        super(msg);
    }
    
    public RangeException(String msg, Throwable t) {
        super(msg,t);
    }

}
