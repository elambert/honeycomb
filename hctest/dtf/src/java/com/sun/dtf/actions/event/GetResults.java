package com.sun.dtf.actions.event;

import java.util.HashMap;
import java.util.Iterator;

import com.sun.dtf.actions.Action;
import com.sun.dtf.comm.rpc.ActionResult;
import com.sun.dtf.comm.rpc.Node;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.NoMoreResultsException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.QueryException;
import com.sun.dtf.query.Cursor;

public class GetResults extends Action {

    /**
     * @dtf.attr cursor
     * @dtf.attr.desc
     */
    private String cursor = null;
    
    /**
     * @dtf.attr recycle
     * @dtf.attr.desc 
     */
    private String recycle = null;
   
    /**
     * @dtf.attr results
     * @dtf.attr.desc 
     */
    private String prefetch = "50";
    
    public GetResults() { }

    public void execute() throws DTFException {
        ActionResult ar = (ActionResult) getContext(Node.ACTION_RESULT_CONTEXT);
     
        if (getLogger().isDebugEnabled()) 
            getLogger().debug("DTFA requesting results from cursor [" + 
                              getCursor() + "]");
        
        Cursor cursor = getState().getCursors().getCursor(getCursor());
       
        if (cursor == null)
            throw new QueryException("Unknown cursor [" + getCursor() + "]");
           
        int results = 0;
        int prefetch = getPreFetch();
        String cursorname = getCursor();
        
        for (int i = 0; i < prefetch; i++) { 
            Event event = null;
            HashMap attribs = null;
            if ((attribs = cursor.next(isRecycle())) != null) { 
                Iterator keys = attribs.keySet().iterator();
                
                event = new Event();
                event.setName(cursorname);
                while ( keys.hasNext() ) { 
                    String key = (String) keys.next();
                    String value = (String) attribs.get(key);
                    event.addAction(new Attribute(key,value));
                }
               
                results++;
                ar.addAction(event);
            } else 
                break;
        }
        
        if (results == 0)
            throw new NoMoreResultsException("No more results from cursor [" + getCursor() + "]");
    }
    
    public String getCursor() throws ParseException { return replaceProperties(cursor); }
    public void setCursor(String cursor) { this.cursor = cursor; }

    public String getRecycle() throws ParseException { return replaceProperties(recycle); }
    public boolean isRecycle() throws ParseException { return toBoolean("recycle",getRecycle()); }
    public void setRecycle(String recycle) { this.recycle = recycle; }

    public int getPreFetch() throws ParseException { return toInt("prefetch",prefetch); }
    public void setPreFetch(String prefetch) { this.prefetch = prefetch; }
}
