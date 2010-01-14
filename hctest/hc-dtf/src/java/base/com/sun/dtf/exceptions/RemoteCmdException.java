package com.sun.dtf.exceptions;

import com.sun.dtf.exception.DTFException;

/**
 * @author Rodney Gomes
 */
public class RemoteCmdException extends DTFException {
    public RemoteCmdException(String message) { 
        super(message);
    }

    public RemoteCmdException(String message, Throwable t) { 
        super(message,t);
    }
}
