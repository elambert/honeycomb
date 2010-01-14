package com.sun.dtf.actions.function;

import com.sun.dtf.actions.util.DTFProperty;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag param
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Define a parameter for a function.
 *
 * @dtf.tag.example 
 * <function name="func3">
 *      <local><echo>In func3</echo></local>
 *      <param name="nomore" type="optional"/>  
 * </function>
 * 
 * @dtf.tag.example 
 * <function name="func2">
 *      <param name="nomore" type="required"/>  
 * </function>
 * 
 */
public class Param extends DTFProperty {
  
    public static String REQUIRED_PARAM = "required";
    public static String OPTIONAL_PARAM = "optional";
   
    /**
     * @dtf.attr type
     * @dtf.attr.desc There are only two types of parameters: required, 
     *                optional.
     */
    private String type = null;
    
    public void execute() throws DTFException { }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }
    
    public boolean isRequired() { return type.equalsIgnoreCase(REQUIRED_PARAM); }
    public boolean isOptional() { return type.equalsIgnoreCase(OPTIONAL_PARAM); }
}
