package com.sun.dtf.recorder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.dtf.actions.event.Attribute;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.RecorderException;
import com.sun.dtf.logger.DTFLogger;


public class ConsoleRecorder extends RecorderBase  {
    private DTFLogger _logger =  DTFLogger.getLogger(ConsoleRecorder.class);

    public ConsoleRecorder(URI uri, boolean append) {
        super(append);
    }
    
    public void record(Event event) throws RecorderException {
        StringBuffer result = new StringBuffer("{");
        result.append(event.getName());
        result.append(".start=");
        result.append(event.getStart());
        result.append(", ");
      
        result.append(event.getName());
        result.append(".stop=");
        result.append(event.getStop());
        result.append(", ");

        try { 
            ArrayList props = event.findActions(Attribute.class);
            Iterator elems = props.iterator();
            
            while (elems.hasNext()) { 
                Attribute attribute = (Attribute)elems.next();
                result.append(event.getName());
                result.append(".");
                result.append(attribute.getName());
                result.append("=");
                result.append(attribute.getValue());
                result.append(", ");
            }
        } catch (ParseException e) { 
            throw new RecorderException("Unable to process properties.",e);
        }
       
        _logger.info(result.substring(0,result.length()-2) + "}");
    }

    public void stop() throws RecorderException { }
    public void start() throws RecorderException { }
}
