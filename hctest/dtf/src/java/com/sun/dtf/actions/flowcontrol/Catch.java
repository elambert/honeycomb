package com.sun.dtf.actions.flowcontrol;

import java.util.ArrayList;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag catch
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Used within the try tag. This tag is what allows us to define 
 *               what to catch and even specify different behavior for different 
 *               types of errors.
 * 
 * @dtf.tag.example 
 * <catch exception="com.sun.dtf.exception.ParseException">
 *     <local>
 *         <fail message="This part should never be executed."/>
 *     </local>
 * </catch>
 *
 * @dtf.tag.example 
 * <catch exception="com.sun.dtf.exception.*">
 *     <local>
 *         <fail message="This part should never be executed."/>
 *     </local>
 * </catch>
 */
public class Catch extends Action {

    /**
     * @dtf.attr exception 
     * @dtf.attr.desc Default to nothing which matches any and all exceptions. 
     *                Otherwise defines the regular expression that will match 
     *                with the expected exception name. This name is the full 
     *                package of the exception like so: java.io.IOException
     */
    private String exception = null;
    
    /**
     * @dtf.attr property
     * @dtf.attr.desc Default to nothing. Otherwise if specified this property 
     *                will contain the message from the exception caught.
     */
    private String property = null;
  
    public Catch() {}
    
    public void execute() throws DTFException { }
    
    public boolean matchAndExecute(DTFException e) throws DTFException { 
        /*
         * Trick done here is to be able to figure out what was the 
         * underlying causes of certain exceptions since at certain points
         * of execution you may have a DTFException that wraps an 
         * underlying exception that is meant to be caught.
         */
        ArrayList classes = new ArrayList();
        Throwable aux = e;
        while (aux != null) { 
            classes.add(aux.getClass().getName());
            aux = aux.getCause();
        }
        
        if (getException() == null || classes.contains(getException())) {
            if (getProperty() != null)
                getConfig().setProperty(getProperty(), e.getMessage());
            executeChildren();
            return true;
        }
        
        return false;
    }

    public String getException() throws ParseException { return replaceProperties(exception); }
    public void setException(String exception) { this.exception = exception; }

    public String getProperty() throws ParseException { return replaceProperties(property); }
    public void setProperty(String property) { this.property = property; }
}
