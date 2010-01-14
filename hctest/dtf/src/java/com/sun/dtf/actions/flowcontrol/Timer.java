package com.sun.dtf.actions.flowcontrol;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.TimeUtil;

/**
 * @dtf.tag for
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc All of the direct children of this tag are executed in 
 *               parallel, this tag only terminates its execution once all of 
 *               the children have completed their executions.
 * 
 * @dtf.tag.example 
 * <timer property="loop">
 *     <component id="${agent}">
 *         <echo>Echo on ${agent} of loop ${loop}.</echo>
 *     </component>
 * </timer>
 */
public class Timer extends Action {

    /**
     * @dtf.attr interval
     * @dtf.attr.desc property is assigned the value of the element currently in 
     *                the range of this for loop.
     */
    private String interval = null;
    
    /**
     * @dtf.attr property 
     * @dtf.attr.desc property is assigned the value of the number of times the
     *                timer has looped.
     */
    private String property = null;
    
    public Timer() { }

    public void execute() throws DTFException { 
        long interval = TimeUtil.parseTime("interval",getInterval());
        long start = System.currentTimeMillis();
        long loop = 0;
        
        while (System.currentTimeMillis() - start < interval) {
            loop++;
            
            if (getProperty() != null) 
                getConfig().setProperty(getProperty(), ""+loop);
            
            executeChildren();
        }
        
        getLogger().info("Timer took " + (System.currentTimeMillis() - start) + 
                         "ms of " + interval + "ms.");
    }

    public String getInterval() throws ParseException { return replaceProperties(interval); }
    public void setInterval(String interval) { this.interval = interval; }

    public String getProperty() throws ParseException { return replaceProperties(property); }
    public void setProperty(String property) { this.property = property; }
}
