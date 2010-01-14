package com.sun.dtf.actions.flowcontrol;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag sequence
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag simply aggregates a group of tags and executes them in 
 *               the sequential order by which they appear.
 * 
 * @dtf.tag.example 
 * <sequence>
 *     <local>
 *     <echo> Value from db: ${element.iteration}</echo> 
 *     </local> 
 *     <nextresult property="element"/>
 * </sequence>
 */
public class Sequence extends Action {
   
    private String threadID = null;
    
    public Sequence() { }
    public void execute() throws DTFException { 
        executeChildren(); 
    }
    
    public void setThreadID(String id) { threadID = id; } 
    public String getThreadID() { return threadID; } 
}
