package com.sun.dtf.actions.function;

import java.util.ArrayList;
import java.util.Iterator;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.properties.Property;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.FunctionException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.state.DTFState;


/**
 * @dtf.tag call
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Execute functions that were defined in this test case or
 *               imported using the {@dtf.link Import} tag.
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
public class Call extends Action {

    /**
     * @dtf.attr function
     * @dtf.attr.desc The unique name of the function to call from this point 
     *                in the XML testcase.
     */
    private String function = null;
    
    public void execute() throws DTFException {
        ArrayList properties = findActions(Property.class);

        DTFState state = (DTFState) getState();
        
        Action action = getFunctions().getFunction(getFunction());
        
        if (action == null) 
            throw new FunctionException("Unable to find function: " + getFunction());

        Iterator iterator = properties.iterator();
        
        while (iterator.hasNext()) { 
            Property property = (Property)iterator.next();
            state.getConfig().setProperty(property.getName(),property.getValue());
        }
        
        action.execute();
    }

    public String getFunction() throws ParseException { return replaceProperties(function); }
    public void setFunction(String function) { this.function = function; }
}
