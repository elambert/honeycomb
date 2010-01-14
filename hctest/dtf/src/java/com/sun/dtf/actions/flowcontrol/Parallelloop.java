package com.sun.dtf.actions.flowcontrol;

import com.sun.dtf.distribution.Worker;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.range.Range;
import com.sun.dtf.range.RangeFactory;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;

/**
 * @dtf.tag parallelloop
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The parallelloop tag will take the child tag and spawn as many 
 *               times as the number o items in the range expression and execute 
 *               those in parallel.
 * 
 * @dtf.tag.example
 * <parallelloop property="j" range="[1..6]">
 *     <parallelloop property="k" range="[1..6]">
 *         <local>
 *             <echo>Creating property property.${j}.${k}</echo>
 *             <property name="property.${j}.${k}" value="${j}-${k}"/>
 *         </local>
 *     </parallelloop>
 * </parallelloop>
 *   
 * @dtf.tag.example
 * <parallelloop property="var1" range="[1,2,3,4,5,6]">
 *     <local>
 *         <echo>Looping on ${var1}</echo>
 *     </local>
 * </parallelloop>
 */
public class Parallelloop extends Loop {

    public Parallelloop() { }
   
    public void execute() throws DTFException {
        Range range = RangeFactory.getRange(getRange());
        int workerCount = range.size();
        
        Sequence children = new Sequence();
        children.addActions(children());
        
        Worker[] workers =  new Worker[workerCount];
       
        int i = 0;
        while (range.hasMoreElements()) {
            String value = range.nextElement();
            DTFState state = getState().duplicate();
            state.getConfig().setProperty(getProperty(), value);
            workers[i++] = new Worker(children, null, state, i);
        }
       
        for(i = 0; i < workerCount; i++) 
            workers[i].start();
      
        /*
         * throw the exception at the end and make sure to wait for everyone
         * to terminate first.
         */
        DTFException excep = null;
        for(i = 0; i < workerCount; i++)  {
            try { 
                workers[i].waitFor();
                ActionState.getInstance().delState(workers[i].getName());
            } catch (DTFException e) { 
                excep = e;
            }
        }
        
        if (excep != null) 
            throw excep;
    }
}
