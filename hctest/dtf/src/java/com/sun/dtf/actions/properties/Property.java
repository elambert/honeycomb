package com.sun.dtf.actions.properties;

import com.sun.dtf.actions.util.DTFProperty;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag property
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc defines a new property within the current script.
 *
 * @dtf.tag.example 
 * <local>
 *     <property name="testvar1" value="values"/> 
 * </local>
 */
public class Property extends DTFProperty {

    /**
     * @dtf.attr overwrite
     * @dtf.attr.desc defaults to false, and this defines if the property being
     *                defined should  overwrite existing values of any property 
     *                or not. 
     */
    private String overwrite = "false";
    
    public Property() { }

    public void execute() throws DTFException {
        getConfig().setProperty(getName(), getValue(), getOverwrite());
    }

    public boolean getOverwrite() throws ParseException { return toBoolean("overwrite",overwrite); }
    public void setOverwrite(String overwrite) throws ParseException { this.overwrite = overwrite; }
}
