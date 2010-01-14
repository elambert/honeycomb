package com.sun.dtf.query;

import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.dtf.exception.QueryException;
import com.sun.dtf.logger.DTFLogger;


public class Cursors {
    
    private DTFLogger _logger = DTFLogger.getLogger(Cursors.class);

    private Hashtable _cursors = null;
  
    /**
     * 
     * @param recorder RecorderIntf to use.
     * @param event set to null if you don't want to filter on events.
     */
    public Cursors() {
        _cursors = new Hashtable();
    }
   
    public void addCursor(String name, Cursor cursor) { 
        _cursors.put(name, cursor);
    }
   
    public Cursor getCursor(String name) { 
        return (Cursor)_cursors.get(name);
    }
    
    public void close() { 
        Enumeration keys = _cursors.keys();
        
        while (keys.hasMoreElements())  {
            String key = (String)keys.nextElement();
            Cursor cursor = (Cursor)_cursors.get(key);
            try {
                cursor.close();
            } catch (QueryException e) {
                _logger.warn("Error closing query.", e);
            }
        }
    }
}
