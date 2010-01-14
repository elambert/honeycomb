package com.sun.dtf.state;

import java.util.Hashtable;

import com.sun.dtf.DTFConstants;

public class ActionState {

    private static ActionState _instance = new ActionState();
    
    private Hashtable _states = null;
   
    private ActionState() {
        _states = new Hashtable();
    }
    
    public static ActionState getInstance() {
        return _instance;
    }
   
    public String getCurrentID() { 
        return Thread.currentThread().getName();
    }
        
    public DTFState getState() {
       return getState(getCurrentID());
    }

    public DTFState getState(String id) {
       DTFState state = (DTFState)_states.get(id); 

       if (state == null) 
           return (DTFState)_states.get(DTFConstants.MAIN_THREAD_NAME);
       else 
           return state;
    }
    
    public void setState(String key, DTFState state) {
        _states.put(key, state);
    }

    public void setState(DTFState state) {
        _states.put(getCurrentID(), state);
    }
    
    public void delState() {
        ((DTFState)_states.remove(getCurrentID())).setDeleted();
    }
    
    public void delState(String key) {
        ((DTFState)_states.remove(key)).setDeleted();
    }
}
