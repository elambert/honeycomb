package com.sun.dtf.actions.basic;

import com.sun.dtf.actions.util.CDATA;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag echo
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The echo tag is used to print out testcase information during 
 *               the execution of the testcase.
 * 
 * @dtf.tag.example 
 * <local>
 *     <echo message="Hello World"/>
 * </local>
 * 
 * @dtf.tag.example
 * <local>
 *     <echo>Hello World</echo>
 * </local>
 */
public class Echo extends CDATA {
   
    /**
     * @dtf.attr message
     * @dtf.attr.desc The message attribute contains the message to be logged to
     *                the test execution log.
     */
    private String message = null;

    public Echo() { }
    
    public void execute() throws DTFException {
        String msg = getMessage();
        String cdata = getCDATA();
        if (msg != null)
            getLogger().info(msg);
        else if (cdata != null)
            getLogger().info(cdata);
        else
            throw new DTFException("Echo does not contain a message to be printed.");
    }

    public String getMessage() throws ParseException { return replaceProperties(message); }
    public void setMessage(String message) { this.message = message; }
}
