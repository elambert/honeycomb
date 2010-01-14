package com.sun.dtf.references;

import java.util.Hashtable;

import com.sun.dtf.actions.Action;


public class References {

    private Hashtable _references = null;
   
    public References() { 
        _references = new Hashtable();
    }
    
    public void addReference(String id, Action action) { 
        _references.put(id, action);
    }
    
    public Action getReference(String id) { 
        return (Action)_references.get(id);
    }
    
    public boolean hasReference(String id) { 
        return _references.containsKey(id);
    }
}
