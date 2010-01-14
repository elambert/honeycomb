package com.sun.dtf.recorder;

import java.util.Vector;

import com.sun.dtf.actions.event.Attribute;
import com.sun.dtf.exception.RecorderException;

public class Recorder {
    
    private RecorderBase _recorder = null;
    private Recorder _parent = null;
    private String _eventPattern = null;
    
    private Vector _defaultAttributes = null;

    /**
     * 
     * @param recorder RecorderIntf to use.
     * @param event set to null if you don't want to filter on events.
     */
    public Recorder(RecorderBase recorder, String eventPattern) {
        _recorder = recorder;
        _eventPattern = eventPattern;
        _defaultAttributes = new Vector();
    }

    public void addDefaultAttribute(String name, String value) { 
        _defaultAttributes.add(new Attribute(name,value,false));
        
        if (_parent != null)
            _parent.addDefaultAttribute(name,value);
    }
    
    public void addDefaultAttribute(String name, String value, int length) { 
        _defaultAttributes.add(new Attribute(name,value,length,false));
        
        if (_parent != null)
            _parent.addDefaultAttribute(name,value,length);
    }
    
    public void record(Event event) throws RecorderException {
        if (_eventPattern == null || event.getName().startsWith(_eventPattern)) {
            /*
             * We need to record the recorded time on the client side of 
             * recording because time from client side can not be used for
             * calculating the exact time an event occurred.
             */
            event.addAttributesAndOverwrite(_defaultAttributes);
             
            _recorder.record(event);
        }
        
        if (_parent != null) 
            _parent.record(event);
    }
    
    public void setParent(Recorder recorder) { _parent = recorder; } 
    public Recorder getParent() { return _parent; } 
   
    public void start() throws RecorderException { _recorder.start(); }
    public void stop() throws RecorderException {  _recorder.stop();  }
}
