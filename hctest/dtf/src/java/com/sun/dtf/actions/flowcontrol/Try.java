package com.sun.dtf.actions.flowcontrol;

import java.util.ArrayList;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag try
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The try tag allows you to catch exceptions being thrown from 
 *               other tags and take an action based on that exeption. This tag
 *               is useful within the context of failures scenarios or negative
 *               testcases.
 * 
 * @dtf.tag.example 
 * <try>
 *     <sequence>
 *         <local>
 *             <echo>This will naturally succeed.</echo>
 *         </local>
 *     </sequence>
 *     <catch exception="com.sun.dtf.exception.*">
 *         <local>
 *             <fail message="This part should never be executed."/>
 *         </local>
 *     </catch>
 * </try>
 *
 * @dtf.tag.example
 * <try>
 *     <sequence>
 *         <local>
 *             <echo>This should succeed: ${property}.</echo>
 *         </local>
 *     </sequence>
 *     <catch exception="com.sun.dtf.exception.ParseException">
 *         <local>
 *             <fail message="This part should never be executed."/>
 *         </local>
 *     </catch>
 *     <catch exception="com.sun.dtf.exception.*">
 *         <local>
 *             <fail message="This part should never be executed."/>
 *         </local>
 *     </catch>
 * </try>
 */
public class Try extends Action {
    
    public Try() {}
    
    public void execute() throws DTFException {
        try {
            ((Action)children().get(0)).execute();
        } catch (DTFException e) {
            ArrayList catches = findActions(Catch.class);
            
            if (getLogger().isDebugEnabled())
                getLogger().debug("Exception thrown " + e.getClass().getName());
           
            for(int i = 0; i < catches.size(); i++) {
                Catch catchElem = (Catch) catches.get(i);
                // Find the first match and execute non other... 
                if (getLogger().isDebugEnabled())
                    getLogger().debug("Exception caught " + 
                                      catchElem.getException());

                if (catchElem.matchAndExecute(e)) {
                    return;
                }
            }
            
            throw e;
        } finally { 
            Finally finallyAction = (Finally) findFirstAction(Finally.class);
            if (finallyAction != null) 
                finallyAction.execute();
        }
    }
}
