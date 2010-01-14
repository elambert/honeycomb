package com.sun.dtf.actions.event;

import java.net.URI;

import com.sun.dtf.actions.reference.Referencable;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.recorder.RecorderBase;
import com.sun.dtf.recorder.RecorderFactory;


/**
 * @dtf.tag record
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is used to record DTF events thrown within the code 
 *               and also thrown from the test case using the event tag. These 
 *               events can be recorded to the following output formats: XML, 
 *               Text file or even a Database. Then the query tag can be used to 
 *               get data back out of the database for later use by other test 
 *               cases or the same one.
 * 
 * @dtf.tag.example 
 * <record uri="storage://OUTPUT/dtf.internals.txt" 
 *         type="txt" 
 *         event="dtf.perf.*">
 *     <local> 
 *         <testscript uri="storage://INPUT/parallel.xml"/> 
 *     </local>
 * </record>
 * 
 * @dtf.tag.example
 * <record type="txt" uri="storage://OUTPUT/recorder2.txt">
 *     <for property="index" range="1,2,3,4,5,6,7,8,9,10">
 *         <event name="dtf.echo.outter">
 *             <local>
 *                 <echo>Testing...</echo>
 *             </local>
 *         </event>
 *      </for>
 *      <record type="txt" uri="storage://OUTPUT/recorder3.txt">
 *          <for property="index" range="1,2,3,4,5,6,7,8,9,10">
 *              <event name="dtf.echo.inner">
 *                  <local>
 *                      <echo>Testing...</echo>
 *                  </local>
 *              </event>
 *          </for>
 *      </record>
 * </record>
 */
public class Record extends Referencable {

    /**
     * @dtf.attr uri
     * @dtf.attr.desc output URI used by all the record types except the 
     *                console recorder.
     */
    private String uri = null;
    
    /**
     * @dtf.attr type
     * @dtf.attr.desc Identifies the type of recorder to instantiate to record
     *                events that are thrown child tags of this recorder tag.
     *                
     *           <b>Recorder Types</b>
     *           <table border="1">
     *               <tr>
     *                   <th>Name</th> 
     *                   <th>Description</th> 
     *               </tr>
     *               <tr>
     *                   <td>txt</td> 
     *                   <td>
     *                       Text recorder will record events to a file in a human readable format like so: 
     *                       <pre>
     *                       dtf.perf.action.component.start=1166719862514 
     *                       dtf.perf.action.component.stop=1166719862690
     *                       </pre>
     *                   </td> 
     *               </tr>
     *               <tr>
     *                   <td>console</td> 
     *                   <td>
     *                       Outputs in the same format as the txt type but 
     *                       instead of to a file it will output the 
     *                       results to the STDOUT of the process from 
     *                       where you execute your test case.
     *                            
     *                       Output on screen would look like this: 
     *                       <pre>
     *                       INFO  27/12/2006 00:12:31 Echo - Testing...
     *                       INFO  27/12/2006 00:12:31 ConsoleRecorder - dtf.echo.start=1167208531453 
     *                       INFO  27/12/2006 00:12:31 ConsoleRecorder - dtf.echo.stop=1167208531453
     *                       </pre>
     *                   </td>
     *               </tr>
     *               <tr>
     *                   <td>object</td> 
     *                   <td>
     *                       Will output the results of an event to an in memory
     *                       property that can be accessed after the event to 
     *                       get important information about the event.
     *                   </td>
     *               </tr>
     *               <tr>
     *                   <td>database</td> 
     *                   <td>
     *                       Stores all of the event results in a database and 
     *                       this database can be used to query for results 
     *                       based on specific information. More information on 
     *                       this database and the current implementation to 
     *                       come soon.
     *                   </td>
     *               </tr>
     *          </table>
     */
    private String type = null;
    
    /**
     * @dtf.attr event
     * @dtf.attr.desc prefix of the events you intend to recorder.
     */
    private String event = null;
    
    /**
     * @dtf.attr append
     * @dtf.attr.desc This value indicates if the events should be appended to
     *                the existing output file or start the recording from 
     *                scratch eliminating any previous results. Accepted values
     *                are "true" and "false".
     */
    private String append = null;
    
    public Record() { }
    
    public void execute() throws DTFException {
        getLogger().info("Starting recorder: " + this);
       
        RecorderBase recorder = RecorderFactory.getRecorder(getType(), getUri(), getAppend());
        pushRecorder(recorder, getEvent());
        try { 
            executeChildren();
        } finally { 
            // If there's an exception we still need to close up the recorder
            popRecorder();
        }
        
        getLogger().info("Stopping recorder: " + this);
    }

    public String getEvent() throws ParseException { return replaceProperties(event); }
    public void setEvent(String event) { this.event = event; }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }

    public URI getUri() throws ActionException, ParseException { return parseURI(uri); }
    public void setUri(String uri) { this.uri = uri; }

    public boolean getAppend() throws ParseException { 
        if (append == null)  
            return true;
        return new Boolean(replaceProperties(append)).booleanValue();
    }
    
    public void setAppend(String append) { this.append = append; }
}
