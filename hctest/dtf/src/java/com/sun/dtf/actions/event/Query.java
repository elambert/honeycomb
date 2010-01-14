package com.sun.dtf.actions.event;

import java.net.URI;
import java.util.ArrayList;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.conditionals.Condition;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.query.Cursor;
import com.sun.dtf.query.QueryFactory;
import com.sun.dtf.query.QueryIntf;

/**
 * @dtf.tag query
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is used to query for results that have been previously 
 *               reordered with the record tag. This tag will create a result 
 *               set and then to iterate over that result set you must call the
 *               nextresult tag to make it progress. Not all recorders are 
 *               query-able though, so you should look at the following list for
 *               query-able recorders:
 *               
 * @dtf.tag.example 
 * <query type="database" 
 *        event="hc.*"  
 *        uri="storage://OUTPUT/perf.db" 
 *        property="obj1">
 *     <select>
 *         <field name="oid"/>
 *     </select>
 *     <where>
 *         <neq field="deleted" value="true"/>
 *     </where>
 * </query>
 * 
 * @dtf.tag.example 
 * <query type="txt" 
 *        event="hc.*"  
 *        uri="storage://OUTPUT/perf.db" 
 *        property="obj1">
 *     <where>
 *         <eq field="myfield" value="myvalue"/>
 *     </where>
 * </query>
 * 
 * @dtf.tag.example 
 * <query type="database" 
 *        event="hc.*"  
 *        uri="storage://OUTPUT/perf.db" 
 *        property="obj1">
 *     <where>
 *         <and>
 *             <eq field="field1" value="somevalue"/>
 *             <neq field="field2" value="othervalue"/>
 *         </and>
 *     </where>
 * </query>
 * 
 */
public class Query extends Action {

    /**
     * @dtf.attr uri
     * @dtf.attr.desc Input URI of the previous recorded events.
     * 
     */
    private String uri = null;
    
    /**
     * @dtf.attr type
     * @dtf.attr.desc Identifies the type of query engine that will be used to 
     *                open the specified recorded events at the place specified 
     *                byt the <code>uri</code>.
     *                
     *           <b>Query Types</b>
     *           <table border="1">
     *               <tr>
     *                   <th>Name</th> 
     *                   <th>Description</th> 
     *               </tr>
     *               <tr>
     *                   <td>txt</td> 
     *                   <td>
     *                       Text query engine, able to query the results 
     *                       recorded by the txt recorder.
     *                   </td> 
     *               </tr>
     *               <tr>
     *                   <td>database</td> 
     *                   <td>
     *                       Database query engine, able to query the results 
     *                       recorded by the database recorder.
     *                   </td>
     *               </tr>
     *          </table>
     */
    private String type = null;
    
    /**
     * @dtf.attr event 
     * @dtf.attr.desc The name of the event to query the underlying cursor on.
     */
    private String event = null;

    /**
     * @dtf.attr cursor
     * @dtf.attr.desc The name of the cursor that will give us the results to 
     *                apply the query constraints on.
     */
    private String cursor = null;

    public Query() { }
    
    public void execute() throws DTFException {
        getLogger().info("Starting query: " + this);
        QueryIntf query = QueryFactory.getQuery(getType());
      
        Select select = (Select) findFirstAction(Select.class);
        ArrayList children = findActions(Where.class);
        Where where =  null;
       
        if (children.size() != 0)
            where = (Where) children.get(0);
        
        query.open(getUri(),
                   (select == null ? null : select.findActions(Field.class)), 
                   (where == null ? null : (Condition)where.findFirstAction(Condition.class)),
                   getEvent(),
                   getCursor());
        
        addCursor(getCursor(),new Cursor(query));
    }

    public String getEvent() throws ParseException { return replaceProperties(event); }
    public void setEvent(String event) { this.event = event; }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }

    public URI getUri() throws ActionException, ParseException { return parseURI(uri); }
    public void setUri(String uri) { this.uri = uri; }

    public String getCursor() throws ParseException { return replaceProperties(cursor); }
    public void setCursor(String cursor) { this.cursor = cursor; }
}
