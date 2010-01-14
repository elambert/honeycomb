package com.sun.dtf.actions.honeycomb.cluster;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag rebootnode
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Reboot a honeycomb node from the Solaris command line.
 * 
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <rebootnode node="3"/>
 * </component>
 *
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <rebootnode/>
 * </component>
 */
public class Rebootnode extends NodeAction {
    public void execute(NodeInterface node) throws DTFException {
        node.rebootOS();
    }
}
