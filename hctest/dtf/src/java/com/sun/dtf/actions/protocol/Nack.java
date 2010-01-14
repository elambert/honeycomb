package com.sun.dtf.actions.protocol;

import com.sun.dtf.exception.DTFException;

public class Nack extends Ack {
    private String message = null;
   
    public Nack() {}
    public Nack(DTFException exception) { this.message = exception.getLocalizedMessage(); }
    
    public void execute() throws DTFException { throw new DTFException(message); }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
