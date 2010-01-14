package com.sun.dtf.actions.honeycomb.cluster;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag starthoneycomb
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Start the honeycomb nodes up by using the command line. 
 * 
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <starthoneycomb/>
 * </component>
 */
public class Starthoneycomb extends NodeAction {

    public final static String CMDLINE_MODE   = "commandline";

    private String mode = null;
    
    public void execute(NodeInterface node ) throws DTFException {
        if (getMode().equals(CMDLINE_MODE)) { 
            node.startHoneycomb();
        } else 
            throw new DTFException("Mode not supported [" + getMode() + "]");
    }

    public String getMode() throws ParseException { return replaceProperties(mode); }
    public void setMode(String mode) { this.mode = mode; }
}
