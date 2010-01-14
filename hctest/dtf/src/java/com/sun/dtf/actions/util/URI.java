package com.sun.dtf.actions.util;

import java.net.URISyntaxException;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;


public class URI extends Action{

    public String uri = null;
   
    public URI() {}
   
    public void execute() throws DTFException {     }

    public java.net.URI getUri() throws ParseException, ActionException {
        try {
            return new java.net.URI(replaceProperties(uri));
        } catch (URISyntaxException e) {
            throw new ActionException("Bad uri.",e);
        }
    }
    
    public void setUri(String uri) { this.uri = uri; }
}
