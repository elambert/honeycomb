package com.sun.dtf.functions;

import java.util.Hashtable;

import com.sun.dtf.actions.Action;


public class Functions {

    private Hashtable _functions = null;
   
    public Functions() { 
        _functions = new Hashtable();
    }
    
    public void addFunction(String name, Action action) { 
        _functions.put(name, action);
    }
    
    public Action getFunction(String name) { 
        return (Action)_functions.get(name);
    }
    
    public boolean hasFunction(String name) { 
        return _functions.containsKey(name);
    }
}
