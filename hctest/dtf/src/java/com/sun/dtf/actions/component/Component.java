package com.sun.dtf.actions.component;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.flowcontrol.Sequence;
import com.sun.dtf.actions.protocol.Lock;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.state.DTFState;

/**
 * @dtf.tag component
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag encapsulates the remote tags that are to be executed 
 *               on components that have been locked and identified by the 
 *               {@dtf.link Lockcomponent} tag. The children tags specified 
 *               within this tag are executed on the component identified by the
 *               id attribute.
 * 
 * @dtf.tag.example 
 * <component id="DTFA1">
 *     <echo>***********************************</echo>
 *     <echo>This is being printed from the dtfx</echo>
 *     <echo>***********************************</echo>
 * </component>
 * 
 * @dtf.tag.example 
 * <component id="DTFA2">
 *     <sleep time="3s"/>
 *     <echo>This is being printed from the dtfx</echo>
 * </component>
 */
public class Component extends Action {

    /**
     * @dtf.attr id
     * @dtf.attr.desc The unique identifier of a component already locked with 
     *                the {@dtf.link Lockcomponent} tag.
     */
    private String id = null;
   
    public Component() { }
    
    public void execute() throws DTFException {
        DTFState state = getState();
        Lock lock = state.getComponents().getComponent(getId());
       
        Sequence sequence = new Sequence();
        sequence.setThreadID(Thread.currentThread().getName());
        sequence.addActions(children());

        state.disableReplace();
        try { 
            Action result = getComm().sendAction(lock.getId(), sequence);
                
            if (result != null) 
                result.execute();
        } finally { 
            state.enableReplace();
        }
    }
    
    public String getId() throws ParseException { return replaceProperties(id); }
    public void setId(String id) { this.id = id; }
}
