package com.sun.dtf.exceptions;

import java.io.IOException;

/**
 * @author Rodney Gomes
 */
public class CorruptionException extends IOException {
    public CorruptionException(String message) { 
        super(message);
    }

    public CorruptionException(String message, Throwable t) { 
        //TODO: needs cleanup
        super(message + ", cause: " + t.getMessage());
    }
}
