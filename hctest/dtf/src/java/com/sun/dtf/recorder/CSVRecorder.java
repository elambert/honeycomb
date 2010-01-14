package com.sun.dtf.recorder;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.event.Attribute;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.RecorderException;
import com.sun.dtf.exception.StorageException;


import au.com.bytecode.opencsv.CSVWriter;

public class CSVRecorder extends RecorderBase  {
    
    private CSVWriter _writer = null;
    private URI _uri = null;

    public CSVRecorder(URI uri, boolean append) throws RecorderException {
        super(append);
        _uri = uri;
    }
  
    public void record(Event event) throws RecorderException {
        try { 
            // size = all attributes + 2 (start,stop and recorded)
            String[] line = new String[event.children().size() + 3];
            
            line[0] = ""+event.getStart();
            line[1] = ""+event.getStop();
    
            int count = 3;
            ArrayList props = event.findActions(Attribute.class);
            Iterator elems = props.iterator();
            
            while (elems.hasNext()) { 
                Attribute attribute = (Attribute)elems.next();
                line[count] = attribute.getValue();
                count++;
            }
            
            _writer.writeNext(line);
        } catch (ParseException e) { 
            throw new RecorderException("Unable to process properties.",e);
        }
    }

    public void stop() throws RecorderException {
        try {
            _writer.close();
        } catch (IOException e) {
            throw new RecorderException("Error closing CSVWriter.",e);
        }
    }

    public void start() throws RecorderException {
        String file;
        try {
            file = Action.getStorageFactory().getPath(_uri);
            _writer = new CSVWriter(new FileWriter(file,_append));
        } catch (StorageException e) {
            throw new RecorderException("Error opening csv file.");
        } catch (IOException e) {
            throw new RecorderException("Error opening csv file.");
        }
    }
}
