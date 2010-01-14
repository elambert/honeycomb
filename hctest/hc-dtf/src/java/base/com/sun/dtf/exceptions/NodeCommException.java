package com.sun.dtf.exceptions;

import com.sun.dtf.exception.DTFException;

/**
 * @author Rodney Gomes
 */
public class NodeCommException extends DTFException {
    public NodeCommException(String message) { 
        super(message);
    }

    public NodeCommException(String message, Throwable t) { 
        super(message,t);
    }
}
