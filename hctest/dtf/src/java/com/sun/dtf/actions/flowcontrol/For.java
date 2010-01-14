package com.sun.dtf.actions.flowcontrol;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.range.Range;
import com.sun.dtf.range.RangeFactory;

/**
 * @dtf.tag for
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Simple for loop tag that allows you to do a for loop in XML.
 * 
 * @dtf.tag.example 
 * <for property="agent" range="1..10">
 *     <for property="loop" range="[1..1000]">
 *         <component id="${agent}">
 *             <echo>Echo on ${agent} of loop ${loop}.</echo>
 *         </component>
 *     </for>
 * </for>
 * 
 * @dtf.tag.example 
 * <for property="var1" range="[0..1][0..1][0..1]">
 *      <echo>${var1}</echo>
 * </for>
 * 
 * @dtf.tag.example 
 * <for property="var1" range="[0..10][a,b,c]">
 *      <echo>${var1}</echo>
 * </for>
 * 
 */
public class For extends Loop {

    public For() { }
   
    public void execute() throws DTFException {
        Range range = RangeFactory.getRange(getRange());
       
        while (range.hasMoreElements()) {
            getConfig().setProperty(getProperty(), range.nextElement());
            executeChildren();
        }
    }
    
}
