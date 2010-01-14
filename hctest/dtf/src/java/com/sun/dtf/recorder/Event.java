package com.sun.dtf.recorder;

import java.util.ArrayList;
import java.util.Vector;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.event.Attribute;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.RecorderException;
import com.sun.dtf.util.ByteArrayUtil;

public class Event extends Action {
    
    private String _name = null;
    
    private long start = -1;
    private long stop = -1;
    
    public Event() { }
    public Event(String name) {
        this();
        _name = name;
    }
    
    public void execute() throws RecorderException { 
        getRecorder().record(this);
    }
    
    public void start() { start = System.currentTimeMillis(); }
    public void stop() { stop = System.currentTimeMillis(); }
    
    public String getName() { return _name; }
    public void setName(String name) { this._name = name; } 
 
    private Attribute findAttribute(String name) {
        ArrayList attributes = children();
        for (int i = 0; i < attributes.size(); i++) { 
            Attribute attrib = (Attribute) attributes.get(i);
            if (attrib.retName().equals(name)) { 
                return attrib;
            }
        }
        return null;
    }
    
    public Attribute retAttribute(String name) {
        return (Attribute)findAttribute(name);
    }
    
    public boolean isIndex(String key) throws ParseException {
        Attribute attribute = retAttribute(key);
        return (attribute != null ? attribute.isIndex() : false);
    }

    public synchronized void addAttribute(String name, String value, boolean index) {
        Attribute attribute = new Attribute(name,value,index);
        addAction(attribute);
    }
    
    public synchronized void addAttribute(String name, String value, int length, boolean index) {
        Attribute attribute = new Attribute(name,value,length,index);
        addAction(attribute);
    }
    
    public synchronized void addAttributesAndOverwrite(Vector attributes) {
        for (int i = 0; i < attributes.size(); i++) { 
            Attribute attrib = (Attribute)attributes.get(i);
            Attribute fAttrib = findAttribute(attrib.retName());
                  
            if (fAttrib != null) { 
                fAttrib.setValue(attrib.retValue());
            } else 
                super.addAction(attrib);
        }
    }
    
    public void addAttribute(String name, long value) throws ParseException {
        addAttribute(name,""+value,false);
    }

    public void addAttribute(String name, double value) throws ParseException {
        addAttribute(name,""+value,false);
    }
    
    public void addAttribute(String name, String value) throws ParseException {
        addAttribute(name,value,false);
    }
    
    public void addAttribute(String key, byte[] bytes) throws ParseException {
        addAttribute(key, ByteArrayUtil.byteArrayToHexString(bytes));
    }
  
    public long getStart() { return start; }
    public void setStart(long start) { this.start = start; }
    
    public long getStop() { return stop;}
    public void setStop(long stop) { this.stop = stop;}
}
