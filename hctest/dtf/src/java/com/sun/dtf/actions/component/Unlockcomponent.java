package com.sun.dtf.actions.component;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.protocol.Lock;
import com.sun.dtf.actions.protocol.Unlock;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag unlockcomponent
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is used to identify and lock the components required 
 *               for the execution of the current test case. When you lock a 
 *               {@dtf.link Component} you can then define a global alias 
 *               by which it will be known for use by other tags such as the 
 *               {@dtf.link Component} tag.
 * 
 * @dtf.tag.example 
 * <local>
 *     <echo>Remote counter retrieval</echo>
 *     <lockcomponent id="DTFA1">
 *         <attrib name="type" value="DTFA"/>
 *     </lockcomponent>
 * </local>
 */
public class Unlockcomponent extends Action {

    /**
     * @dtf.attr id
     * @dtf.attr.desc Identifies the component to be unlocked by this testcase.
     *                When a testcase finishing executing it will also issue
     *                unlockcomponent request for each of the components that 
     *                were locked and never unlocked by the testcase.
     */
    private String id = null;
    
    public Unlockcomponent() { }
    
    public void execute() throws DTFException { 
        Lock lock = getComponents().getComponent(getId());
       
        getLogger().info("Unlocking " + lock.getId());
        Unlock unlock = new Unlock(lock.getId(), lock.getOwner());
        Action result = getComm().sendAction("dtfc", unlock);
        
        //TODO: best effort no executing the return child ? 
       
        getComponents().unregisterComponent(getId());
        getLogger().info("Component unlocked " + getId());
    }
    
    public String getId() throws ParseException { return replaceProperties(id); }
    public void setId(String id) { this.id = id; }
}
