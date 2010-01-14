package com.sun.dtf.actions.component;

import com.sun.dtf.DTFNode;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag stopcomponent
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This will stop the component by sending a command to the 
 *               component telling it to turn itself off. This will in fact, 
 *               make the component disconnect from the DTF framework.
 * 
 * @dtf.tag.example 
 * <local>
 *     <stopcomponent id="DTFA1"/>
 * </local>
 */
public class Stopcomponent extends Unlockcomponent {

    public Stopcomponent() { }
   
    public void execute() throws DTFException {
        getLogger().info("Remote shutdown request has been received.");
        DTFNode.stop();
    }
}
