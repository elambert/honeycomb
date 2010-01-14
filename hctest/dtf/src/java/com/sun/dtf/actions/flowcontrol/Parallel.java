package com.sun.dtf.actions.flowcontrol;

import java.util.ArrayList;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;
import com.sun.dtf.util.ActionThread;


/**
 * @dtf.tag parallel
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc All of the direct children of this tag are executed in 
 *               parallel, this tag only terminates its execution once all of 
 *               the children have completed their executions.
 * 
 * @dtf.tag.example 
 * <parallel>
 *     <component id="DTFA1">
 *         <echo>Echo a</echo>
 *     </component>
 *     <component id="DTFA2">
 *         <echo>Echo b</echo>
 *     </component>
 * </parallel>
 *
 */
public class Parallel extends Action {

    public Parallel() { }

    public void execute() throws DTFException {
        ArrayList children = children();
        ActionThread[] actions = new ActionThread[children.size()];

        for (int index = 0; index < actions.length; index++) {
            actions[index] = new ActionThread((Action) children.get(index));
            DTFState state = getState().duplicate();
            ActionState.getInstance().setState(actions[index].getName(), state);
        }
        
        for (int index = 0; index < actions.length; index++) 
            actions[index].start();
       
        for (int index = 0; index < actions.length; index++) {
            try {
                actions[index].join();
            } catch (InterruptedException e) {
                getLogger().error("Error waiting for Action to complete.",e);
            }
        }
        
        for (int index = 0; index < actions.length; index++)
            actions[index].checkForException();
    }
}
