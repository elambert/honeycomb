package com.sun.dtf.recorder;

import java.net.URI;
import java.util.Iterator;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.event.Attribute;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.RecorderException;


public class ObjectRecorder extends RecorderBase {
    
    private String name = null;
    
    public ObjectRecorder(URI uri,boolean append)  {
        super(append);
        name = uri.getHost();
    }

    public void stop() throws RecorderException {
        
    }

    public void start() throws RecorderException {
        
    }

    public void record(Event event) throws RecorderException {
        Action.getConfig().setProperty(name + ".start", "" + event.getStart());
        Action.getConfig().setProperty(name + ".stop", "" + event.getStop());
      
        Iterator attributes = event.children().iterator();
        
        try { 
            while (attributes.hasNext()) { 
                Attribute attribute = (Attribute)attributes.next();
                Action.getConfig().setProperty(name + "." + attribute.getName(), 
                                               "" + attribute.getValue());
            }     
        } catch (ParseException e) {
            throw new RecorderException("Unable to parse attribute.",e);
        }
    }
}
