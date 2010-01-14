package com.sun.dtf.actions.flowcontrol;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.FailException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag fail
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Fail tag allows us to throw a failure at any point in the test 
 *               case where we feel that by reaching this point in the test we 
 *               have in fact hit an issue that should be noted as a failure of 
 *               the test case.
 * 
 * @dtf.tag.example 
 * <local>
 *     <fail message="There was failure."/>
 * </local>
 */
public class Fail extends Action {

    /**
     * @dtf.attr message
     * @dtf.attr.desc The message to be used when throwing the FailException.
     */
    private String message = null;
    
    public Fail() {}
    
    public void execute() throws FailException, ParseException {
        throw new FailException(getMessage());
    }

    public String getMessage() throws ParseException { return replaceProperties(message); }
    public void setMessage(String message) { this.message = message; }
}
