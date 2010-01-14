package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class StorageException extends DTFException {

    public StorageException(String msg) {
        super(msg);
    }
    
    public StorageException(String msg, Throwable t) {
        super(msg,t);
    }
}
