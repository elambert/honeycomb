package com.sun.dtf.actions.event;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag where
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This child tag of query allows us to define the exact 
 *               conditions of the query that we want to run against the 
 *               collection of events stored by a previous recorder.
 *               
 * @dtf.tag.example 
 * <where>
 *     <lt field="iteration" value="5"/>
 * </where>
 * 
 * @dtf.tag.example 
 * <where>
 *     <or>
 *         <lt field="iteration" value="5"/>
 *         <gt field="runid" value="1000"/>
 *     </or>
 * </where>
 */
public class Where extends Action {
    public Where() { }
    public void execute() throws DTFException { }
}
