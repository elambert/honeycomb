package com.sun.dtf.actions.conditionals;

import java.util.ArrayList;

import com.sun.dtf.exception.DTFException;


/**
 * @dtf.tag or
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Evaluates that at least one of the sub conditional tags 
 *               evaluates to a true value.
 * 
 * @dtf.tag.example 
 * <or>
 *     <neq op1="value1" op2="${test.value}"/>
 *     <eq op1="value2" op2="${test.value2}"/>
 * </or>
 */
public class Or extends AggCondition {
    
    public Or() { }

    public boolean evaluate() throws DTFException {
        boolean result = false;
        ArrayList subconditions = findActions(Condition.class);
        
        for (int i = 0; i < subconditions.size(); i++) { 
            result |= ((Condition)subconditions.get(i)).evaluate();
           
            // lazy evaluation ;)
            if (result) 
                return true;
        }
        
        return result;
    }
}
