package com.sun.dtf.components;

import java.util.HashMap;

import com.sun.dtf.actions.protocol.Lock;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;


public class Components {

    public HashMap _elems = null;
   
    public Components() { _elems = new HashMap(); }
    
    public void registerComponent(String key, Lock lock) { _elems.put(key, lock); }
    public void unregisterComponent(String key) { _elems.remove(key); }
    
    public Lock getComponent(String key) throws DTFException {
        Object obj = _elems.get(key);
        
        if (obj == null) {
           
            if (key.equals("DTF-DTFC")) 
                return new Lock("dtfc",null,0);
            
            throw new ActionException("Component with id: " + key + " not registered.");
        }
        
        return (Lock)obj;
    }
    
    public boolean hasComponents() { return _elems.size() != 0; } 
}
