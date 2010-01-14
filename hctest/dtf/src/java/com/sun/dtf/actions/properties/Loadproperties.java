package com.sun.dtf.actions.properties;

import java.net.URI;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;


/**
 * @dtf.tag loadproperties
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Loads all of the properties from a regular  
 *               <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/util/Properties.html#load(java.io.InputStream)}">Java Properties file</a>
 *               <br/> 
 *               <br/> 
 *               <b>DTF Internal Properties</b>
 *               <table border="1">
 *                   <tr>
 *                       <th>Name</th> 
 *                       <th>Description</th> 
 *                   </tr>
 *                   <tr>
 *                       <td>dtf.xml.path</td>
 *                       <td>Assigned at the beginning of any DTF 
 *                           run and is always assigned to the location of the 
 *                           XML that defines the currently executed test case.</td>
 *                   </tr>
 *                   <tr>
 *                       <td>dtf.timestamp</td>
 *                       <td>Retrieves the System.currentMilliseconds() value 
 *                           dynamically at execution time of the test case.
 *                       </td>
 *                   </tr>
 *                   <tr>
 *                       <td>dtf.datestamp</td>
 *                       <td>creates a date stamp.</td>
 *                   </tr>
 *               </table>
 *               
 * @dtf.tag.example 
 * <local>
 *     <createstorage id="INPUT" path="${dtf.path}/tests/ut/input"/>
 *     <loadproperties uri="storage://INPUT/ut.properties"/>
 * </local>
 */
public class Loadproperties extends Action {

    /**
     * @dtf.attr uri
     * @dtf.attr.desc Specifies the location where the properties file can be 
     *                found.  
     */
    private String uri = null;
    
    /**
     * @dtf.attr overwrite
     * @dtf.attr.desc Defaults to false, and this defines if the properties 
     *                being loaded from this external file are to overwrite 
     *                existing values of any property or not. 
     */
    private boolean overwrite = false;

    public Loadproperties() { }
    
    public void execute() throws DTFException {
        getConfig().loadProperties(getStorageFactory().getInputStream(getUri()),getOverwrite());
    }
    
    public void setUri(String uri) { this.uri = uri; }
    public URI getUri() throws ActionException, ParseException { return parseURI(uri); }
  
    public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; } 
    public boolean getOverwrite() { return overwrite; } 
}
