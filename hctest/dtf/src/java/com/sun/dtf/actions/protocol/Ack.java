package com.sun.dtf.actions.protocol;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

public class Ack extends Action {
    public Ack() { }
   
    public void execute() throws DTFException {
        executeChildren();
    }
}
