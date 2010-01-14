package com.sun.dtf.actions.reference;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

public class Referencable extends Action {
  
    /**
     * @dtf.attr refid
     * @dtf.attr.desc Reference ID should reference an existing element with an 
     *                ID set to this same value, otherwise an exception will be
     *                thrown.
     */
    private String refid = null;

    /**
     * @dtf.attr id
     * @dtf.attr.desc Unique ID by which this XML tag will be known to all other
     *                elements that which to reference it through the refid 
     *                attribute.
     */
    private String id = null;
    
    public void execute() throws DTFException { }
    
    public Action lookupReference() throws ParseException { 
        return getState().getReferences().getReference(getRefid());
    }
    
    public String getRefid() throws ParseException { return replaceProperties(refid); }
    public void setRefid(String refid) { this.refid = refid; }
    
    public String getId() throws ParseException { return replaceProperties(id); }
    public void setId(String id) { this.id = id; }
   
    /*
     * no resolution of the variables themselves...
     */
    public boolean isReference() throws ParseException { return (refid != null); }
    public boolean isReferencable() throws ParseException { return (id != null); }
}