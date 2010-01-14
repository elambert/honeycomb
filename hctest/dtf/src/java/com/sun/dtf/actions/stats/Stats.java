package com.sun.dtf.actions.stats;

import java.util.Iterator;
import java.util.LinkedHashMap;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.query.Cursor;
import com.sun.dtf.recorder.Event;
import com.sun.dtf.stats.GenCalcStats;


/**
 * @dtf.tag stats
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The stats tag is capable of calculating some statistical 
 *               analysis over the events identified in the result set of the 
 *               attribute <code>cursor</code>. The calculated statistics are 
 *               thrown as events to be used by the testcase as it best sees fit.
 *
 * @dtf.event cursor.fieldname
 * @dtf.event.attr avg_val
 * @dtf.event.attr.desc Each field of the specified event that is of a number 
 *                      type will have its average value calculated and recorded
 *                      in this attribute.
 *
 * @dtf.event cursor.fieldname
 * @dtf.event.attr tot_val
 * @dtf.event.attr.desc Each field of the specified event that is of a number 
 *                      type will have its total value calculated and recorded
 *                      in this attribute.
 *
 * @dtf.event cursor.fieldname
 * @dtf.event.attr min_val
 * @dtf.event.attr.desc Each field of the specified event that is of a number 
 *                      type will have its minimum value calculated and recorded
 *                      in this attribute.
 *
 * @dtf.event cursor.fieldname
 * @dtf.event.attr max_val
 * @dtf.event.attr.desc Each field of the specified event that is of a number 
 *                      type will have its maximum value calculated and recorded
 *                      in this attribute.
 * 
 * @dtf.event cursor
 * @dtf.event.attr min_dur
 * @dtf.event.attr.desc The minimum duration of any event recorded will be saved
 *                      in this attribute.
 * 
 * @dtf.event cursor
 * @dtf.event.attr max_dur
 * @dtf.event.attr.desc The maximum duration of any event recorded will be saved
 *                      in this attribute.
 * 
 * @dtf.event cursor
 * @dtf.event.attr avg_dur
 * @dtf.event.attr.desc The average duration of any event recorded will be saved
 *                      in this attribute.
 * 
 * @dtf.event cursor
 * @dtf.event.attr tot_dur
 * @dtf.event.attr.desc The average duration of any event recorded will be saved
 *                      in this attribute.
 * 
 * @dtf.event cursor
 * @dtf.event.attr tot_occ
 * @dtf.event.attr.desc The total occurrences of this event will be saved in 
 *                      this attribute.
 * 
 * @dtf.event cursor
 * @dtf.event.attr avg_occ
 * @dtf.event.attr.desc The number of occurrences of this event over the 
 *                      duration of all events is recorded in this attribute.
 * 
 * @dtf.tag.example
 * <script>
 *     <query uri="storage://OUTPUT/${recorder.filename}" 
 *            type="${recorder.type}" 
 *            event="dtf.event"
 *            cursor="cursor1">
 *         <where>
 *             <eq op1="runid" op2="${runid}"/>
 *         </where>
 *     </query>
 *                 
 *     <record type="object" uri="property://txtrecorder"> 
 *         <stats cursor="cursor1" event="stats"/>
 *     </record>
 * </script>
 * 
 * @dtf.tag.example
 * <script>
 *     <query uri="storage://OUTPUT/${recorder.filename}" 
 *            type="${recorder.type}" 
 *            event="dtf.event"
 *            cursor="cursor1"/>
 *                 
 *     <record type="object" uri="property://txtrecorder"> 
 *         <stats cursor="cursor1" event="stats"/>
 *     </record>
 * </script>
 */
public class Stats extends Action {

    /**
     * @dtf.attr cursor
     * @dtf.attr.desc Identifies the cursor to use when calculating statistics.
     */
    private String cursor = null;

    /**
     * @dtf.attr event
     * @dtf.attr.desc Specifies the event name to attach the statistics that 
     *                are calculated.
     */
    private String event = null;

    public Stats() { }
    
    public void execute() throws DTFException {
        getLogger().info("Starting to analyze " + this);
        
        GenCalcStats calcStats = new GenCalcStats();
        Cursor cursor = retCursor(getCursor());
        LinkedHashMap props = calcStats.calcStats(cursor);
        Event event = new Event(getEvent());
        Iterator iter = props.keySet().iterator();
       
        while (iter.hasNext()) {
            String key = (String)iter.next();
            event.addAttribute(key, (String)props.get(key));
        }
        
        getRecorder().record(event);
        getLogger().info("Finished analyzing " + this);
    }

    public String getCursor() throws ParseException { return replaceProperties(cursor); }
    public void setCursor(String cursor) { this.cursor = cursor; }
    
    public String getEvent() throws ActionException, ParseException { return replaceProperties(event); }
    public void setEvent(String event) { this.event = event; }
}
