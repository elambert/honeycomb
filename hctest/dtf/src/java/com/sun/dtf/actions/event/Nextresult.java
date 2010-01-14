package com.sun.dtf.actions.event;

import java.util.HashMap;
import java.util.Iterator;

import com.sun.dtf.actions.Action;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.NoMoreResultsException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.query.Cursor;

/**
 * @dtf.tag nextresult
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is used to move the cursor of a previously executed 
 *               query tag. By moving the cursor we will get new results from 
 *               the collection of previously recorded events that obey the 
 *               previously defined query.
 *               
 * @dtf.tag.example 
 * <nextresult property="element" cursor="cursor2"/>
 *
 * @dtf.tag.example 
 * <nextresult property="element" cursor="cursor1" recycle="true"/>
 */
public class Nextresult extends Action {

    /**
     * @dtf.attr cursor
     * @dtf.attr.desc Identifies the cursor name that will be used to fetch the
     *                next result.
     */
    private String cursor = null;
    
    /**
     * @dtf.attr recycle
     * @dtf.attr.desc If this value is true then when the result set identified 
     *                by the cursor hits the end of its results the cursor is 
     *                reopened. If this value is false once there are no more 
     *                results this tag will throw a NoMoreResultsException.
     */
    private String recycle = null;
    
    public Nextresult() { }

    public void execute() throws DTFException {
        Cursor cursor = retCursor(getCursor());
        Config config = getConfig();
        HashMap map = null;
        if ((map = cursor.next(isRecycle())) == null) {
            throw new NoMoreResultsException("No more results in db for this query.");
        }
        
        Iterator keys = map.keySet().iterator();
        while (keys.hasNext()) { 
            String key = (String) keys.next();
            config.setProperty(key, (String)map.get(key));
        }
    }

    public String getCursor() throws ParseException { return replaceProperties(cursor); }
    public void setCursor(String cursor) { this.cursor = cursor; }

    public boolean isRecycle() throws ParseException { return toBoolean("recycle",recycle); }
    public void setRecycle(String recycle) { this.recycle = recycle; }
}
