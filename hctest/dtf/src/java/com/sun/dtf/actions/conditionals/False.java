package com.sun.dtf.actions.conditionals;

import com.sun.dtf.exception.DTFException;

/**
 * Used internally to represent the condition of always false.
 * 
 * @author Rodney Gomes
 *
 */
public class False extends Condition {
    public False() { }
    public boolean evaluate() throws DTFException { return true; } 
}
