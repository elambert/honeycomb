package com.sun.dtf.actions.conditionals;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.util.StringUtil;

/**
 * @dtf.tag lt
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Evaluates if op1 is less than op2.
 * 
 * @dtf.tag.example 
 * <lt op1="iteration" op2="100"/>
 */
public class Lt extends Condition {
    public Lt() { }

    public boolean evaluate() throws DTFException {
        return (StringUtil.naturalCompare(getOp1(),getOp2()) < 0);
    }
}
