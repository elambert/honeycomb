package com.sun.dtf.actions.reference;

import java.util.ArrayList;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;


public class RefWrapper extends Action {

    private Referencable referencable = null;
    
    public RefWrapper(Referencable ref) { 
        this.referencable = ref;
    }
    
    public void execute() throws DTFException {
        if (referencable.isReference()) { 
            referencable.lookupReference().execute();
        } else if (referencable.isReferencable()) {
            throw new DTFException("Referencable elements are not to be executed.");
        } else
            referencable.execute();
    }
   
    /*
     * TODO: Adding children to refid can be caught here!
     */
    public void addAction(Action action) {
        referencable.addAction(action);
    }
    
    public void addActions(ArrayList actions) {
        referencable.addActions(actions);
    }
    
    public Referencable lookupReference() throws ParseException { 
        if (referencable.isReference()) { 
            Referencable ref = (Referencable) referencable.lookupReference();
            if (ref == null)
                throw new ParseException("Unable to find reference for " + referencable.getRefid());
            return ref;
        } else 
            return referencable;
    }

    public boolean anInstanceOf(Class type) {
        return referencable.anInstanceOf(type);
    }
}