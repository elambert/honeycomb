package com.sun.dtf.exceptions;

import com.sun.dtf.exception.DTFException;

/**
 * @author Rodney Gomes
 */
public class MCConfigException extends DTFException {
    public MCConfigException(String message) { 
        super(message);
    }

    public MCConfigException(String message, Throwable t) { 
        super(message,t);
    }
}
