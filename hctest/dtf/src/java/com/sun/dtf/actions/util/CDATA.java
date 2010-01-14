package com.sun.dtf.actions.util;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

public class CDATA extends Action {
   
    private String CDATA = null;
   
    public CDATA() { }
    
    public void setCDATA(String CDATA) { this.CDATA = CDATA; }
    public String getCDATA() throws ParseException { return replaceProperties(CDATA); } 

    public void execute() throws DTFException { }
}
