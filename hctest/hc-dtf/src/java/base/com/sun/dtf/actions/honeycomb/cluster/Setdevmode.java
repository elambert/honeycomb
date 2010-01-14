package com.sun.dtf.actions.honeycomb.cluster;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag setdevmode
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc this tag is used to set the /config/nohoneycomb 
 *               /config/noreboot flags on the server side.
 *               
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <setdevmode/>
 * </component>
 *
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <setdevmode node="3"/>
 * </component>
 */
public class Setdevmode extends NodeAction {
    public void execute(NodeInterface node) throws DTFException {
        node.setDevMode();
    }
}
