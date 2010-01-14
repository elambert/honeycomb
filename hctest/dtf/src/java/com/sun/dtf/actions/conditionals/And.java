package com.sun.dtf.actions.conditionals;

import java.util.ArrayList;

import com.sun.dtf.exception.DTFException;


/**
 * @dtf.tag and
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Evaluates if all of the underlying sub conditional tags 
 *               have a conditional evaluation of true.
 * 
 * @dtf.tag.example 
 * <and>
 *     <neq op1="value1" op2="${test.value}"/>
 *     <eq op1="value2" op2="${test.value2}"/>
 * </and>
 */
public class And extends AggCondition {
    public And() { }
    
    public boolean evaluate() throws DTFException {
        boolean result = true;
        ArrayList subconditions = findActions(Condition.class);
        
        for (int i = 0; i < subconditions.size(); i++) { 
            result &= ((Condition)subconditions.get(i)).evaluate();
           
            // lazy evaluation ;)
            if (!result) 
                return false;
        }
        
        return result;
    }
}
