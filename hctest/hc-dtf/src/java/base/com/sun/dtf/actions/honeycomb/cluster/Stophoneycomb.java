package com.sun.dtf.actions.honeycomb.cluster;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag stophoneycomb
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Stop the honeycomb nodes up by using the command line to reboot
 *               or pkill all the jvms.
 * 
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <stophoneycomb mode="reboot"/>
 * </component>
 *
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <stophoneycomb mode="pkill"/>
 * </component>
 */
public class Stophoneycomb extends NodeAction {
    
    public final static String PKILL_MODE   = "pkill";
    public final static String REBOOT_MODE  = "reboot";

    /**
     * @dtf.attr mode
     * @dtf.attr.desc There are 2 available modes for stopping honeycomb:
     *           <b>Stop Modes</b>
     *           <table border="1">
     *               <tr>
     *                   <th>Mode</th> 
     *                   <th>Description</th> 
     *               </tr>
     *               <tr>
     *                   <td>pkill</td> 
     *                   <td>Stops the honeycomb JVMs with a "pkill -9 java"</td>
     *               </tr>
     *               <tr>
     *                   <td>reboot</td> 
     *                   <td>Stops the honeycomb nodes with a reboot from the 
     *                       OS command line.</td>
     *               </tr>
     *          </table> 
     */
    private String mode = null;
    
    public void execute(NodeInterface node) throws DTFException {
        if (getMode().equals(PKILL_MODE)) { 
            node.pkillHoneycomb();
        } else  if (getMode().equals(REBOOT_MODE)) { 
            node.rebootOS();
        } else 
            throw new DTFException("Mode not supported [" + getMode() + "]");
    }

    public String getMode() throws ParseException { return replaceProperties(mode); }
    public void setMode(String mode) { this.mode = mode; }
}
