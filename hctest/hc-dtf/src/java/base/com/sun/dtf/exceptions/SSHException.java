package com.sun.dtf.exceptions;

import com.sun.dtf.exception.DTFException;

/**
 * @author Rodney Gomes
 */
public class SSHException extends DTFException {
    public SSHException(String message) { 
        super(message);
    }

    public SSHException(String message, Throwable t) { 
        super(message,t);
    }
}
