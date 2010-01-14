package com.sun.dtf.actions.honeycomb.cli;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag cli
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc cli tag is used to obvioulsy execute certain commands on the 
 *               honeycomb cli that can be used to change or check the state of
 *               the clutser.
 *               
 * @dtf.tag.example 
 * <cli>
 *      <reboot/>
 * </cli>
 * 
 */
public class Cli extends Action {
    public void execute() throws DTFException {
        executeChildren();
    }
}
