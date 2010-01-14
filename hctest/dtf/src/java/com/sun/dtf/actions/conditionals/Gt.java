package com.sun.dtf.actions.conditionals;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.util.StringUtil;

/**
 * @dtf.tag gt
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Evaluates if op1 is greater than op2.
 * 
 * @dtf.tag.example 
 * <gt op1="iteration" op2="100"/>
 */
public class Gt extends Condition {
    public Gt() { }
    
    public boolean evaluate() throws DTFException {
        return (StringUtil.naturalCompare(getOp1(),getOp2()) > 0);
    }
}
