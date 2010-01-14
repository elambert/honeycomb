package com.sun.dtf.exceptions;

/**
 * @author Rodney Gomes
 */
public class SnapshotException extends Exception {
    public SnapshotException(String message) { 
        super(message);
    }

    public SnapshotException(String message, Throwable t) { 
        super(message,t);
    }
}
