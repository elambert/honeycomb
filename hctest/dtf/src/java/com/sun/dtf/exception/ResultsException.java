package com.sun.dtf.exception;

public class ResultsException extends DTFException {

    public ResultsException(String msg) {
        super(msg);
    }
    
    public ResultsException(String msg, Throwable t) {
        super(msg,t);
    }

}
