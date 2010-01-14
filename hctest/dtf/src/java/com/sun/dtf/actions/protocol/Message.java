package com.sun.dtf.actions.protocol;

import java.util.ArrayList;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.recorder.Event;


/**
 * This action is used internally for connecting to the DTFC
 * 
 * @author Rodney Gomes
 */
public class Message extends Action {

    private String dst = null; 
    private String src = null;
    private String queue = null;
    private Action action = null;
    
    public Message() { }
    
    public Message(String dst,
                   String src,
                   String queue,
                   Action action)  {
        this.dst = dst;
        this.src = src;
        this.queue = queue;
        this.action = action;
    }
  
    public void execute() throws DTFException {
        executeChildren();
    }
    
    public ArrayList counters() throws ParseException { return findActions(Event.class); } 

    public String getQueue() { return queue; }
    public void setQueue(String queue) { this.queue = queue; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public String getDst() { return dst; }
    public void setDst(String dst) { this.dst = dst; }

    public String getSrc() { return src; }
    public void setSrc(String src) { this.src = src; }
}
