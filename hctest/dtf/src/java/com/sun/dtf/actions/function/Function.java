package com.sun.dtf.actions.function;

import java.util.ArrayList;
import java.util.Iterator;

import com.sun.dtf.actions.Action;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;


/**
 * @dtf.tag function
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Define a function that  can be called from anywhere in the 
 *               subsequent XML script. This function can also be imported using 
 *               the import tag defined up ahead.
 *
 * @dtf.tag.example 
 * <function name="func3">
 *      <local><echo>In func3</echo></local>
 *      <param name="nomore" type="optional"/>  
 * </function>
 */
public class Function extends Action {

    /**
     * @dtf.attr name
     * @dtf.attr.desc Specifies a unique name for the function in this script 
     *                file.
     */
    private String name = null;

    public void execute() throws DTFException {
        /*
         * Verify parameters before executing
         */
        ArrayList params = findActions(Param.class);
        Iterator iter = params.iterator();
       
        Config config = getConfig();
        while (iter.hasNext()) { 
            Param param = (Param)iter.next();
            if (param.isRequired()) { 
                if (config.getProperty(param.getName()) == null) { 
                    throw new DTFException("Missing required parameter " + param);
                }
            }
        }
        
        executeChildren();
    }
    
    public String getName() throws ParseException { return replaceProperties(name); }
    public void setName(String name) { this.name = name; }
}
