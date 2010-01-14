package com.sun.dtf.actions.event;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag select
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This child tag of query allows for you to define what you want 
 *               to get back from the query.
 *               
 * @dtf.tag.example 
 * <select>
 *     <field name="myfield" type="int"/>
 * </select>
 */
public class Select extends Action {
    public Select() { }
    public void execute() throws DTFException { }
}
