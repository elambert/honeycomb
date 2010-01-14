package com.sun.dtf.recorder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Iterator;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.event.Attribute;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.RecorderException;
import com.sun.dtf.exception.StorageException;
import com.sun.dtf.storage.StorageFactory;


public class TextRecorder extends RecorderBase {
 
    private OutputStream _os = null;
    
    public TextRecorder(URI uri, boolean append) throws StorageException { 
        super(append);
        //_os = Action.getStorageFactory().getOutputStream(uri,append);
        StorageFactory sf = Action.getStorageFactory();
        _os = new BufferedOutputStream(sf.getOutputStream(uri,append));
    }
    
    public void record(Event event) throws RecorderException {
        StringBuffer result = new StringBuffer();
        
        result.append(event.getName());
        result.append(".start=");
        result.append(event.getStart());
        result.append("\n");
        
        result.append(event.getName());
        result.append(".stop=");
        result.append(event.getStop());
        result.append("\n");

        Iterator attributes = event.children().iterator();
        
        while (attributes.hasNext()) { 
            Attribute attribute = (Attribute)attributes.next();
            try {
                result.append(event.getName());
                result.append(".");
                result.append(attribute.getName());
                result.append("=");
                result.append(attribute.getValue());
                result.append("\n");
            } catch (ParseException e) {
                throw new RecorderException("Error writing to recorder.", e);
            }
        }
        
        result.append("\n");
      
        try {
            _os.write(result.toString().getBytes());
        } catch (IOException e) {
            throw new RecorderException("Error writing to recorder.", e);
        }
    }

    public void stop() throws RecorderException {
        try {
            _os.close();
        } catch (IOException e) {
            throw new RecorderException("Error closing the TextAppender.",e);
        }
    }

    public void start() throws RecorderException { }
}
