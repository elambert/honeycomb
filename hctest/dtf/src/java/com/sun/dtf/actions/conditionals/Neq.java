package com.sun.dtf.actions.conditionals;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.util.StringUtil;

/**
 * @dtf.tag gt
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Evaluates if op1 and op2 are different.
 * 
 * @dtf.tag.example 
 * <neq op1="value1" op2="${test.value}"/>
 */
public class Neq extends Condition {
    public Neq() { }
    
    public boolean evaluate() throws DTFException {
        return (StringUtil.naturalCompare(getOp1(), getOp2()) != 0);
    }
}
