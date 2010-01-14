package com.sun.dtf.exceptions;

import com.sun.dtf.exception.DTFException;

/**
 * @author Rodney Gomes
 */
public class CLIException extends DTFException {
    public CLIException(String message) { 
        super(message);
    }

    public CLIException(String message, Throwable t) { 
        super(message,t);
    }
}
