package com.sun.dtf.actions.honeycomb.cluster;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag unsetdevmode
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc this tag is used to unset the /config/nohoneycomb 
 *               /config/noreboot flags on the server side.
 *               
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <unsetdevmode/>
 * </component>
 *
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <unsetdevmode node="3"/>
 * </component>
 *
 */
public class Unsetdevmode extends NodeAction { 
    public void execute(NodeInterface node) throws DTFException {
        node.unSetDevMode();
    }
}