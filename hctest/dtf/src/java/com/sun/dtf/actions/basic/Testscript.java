package com.sun.dtf.actions.basic;

import java.net.URI;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.properties.Testproperty;
import com.sun.dtf.actions.util.ScriptUtil;
import com.sun.dtf.components.Components;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.state.DTFState;
import com.sun.dtf.storage.StorageFactory;


/**
 * @dtf.tag testscript
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The testscript tag allows you to execute an external XML script
 *               by identifying where it is with the <code>uri</code> attribute.
 *               The properties,references are inherited from the parent script, 
 *               but all components and storages are not inherited from the 
 *               parent script. 
 *
 * @dtf.tag.desc This tag generates test result events that can be recorded with
 *               the results tag.
 *               
 * @dtf.tag.example 
 * <testscript uri="storage://INPUT/storage.xml"/>
 * 
 */
public class Testscript extends Action {

    /**
     * @dtf.attr uri
     * @dtf.attr.desc The uri identifies the location of the script to be 
     *                executed.
     */
    private String uri = null;

    public Testscript() { }
  
    public void execute() throws DTFException {
        getLogger().info("Executing testscript " + uri);
        
        DTFState state = (DTFState) getState().duplicate();
        state.setComponents(new Components());
        state.setStorage(new StorageFactory());
        state.getConfig().setProperty(DTFProperties.DTF_XML_FILENAME,
                                      getStorageFactory().getPath(getUri()));
        // Set the node name to null to register this new testscript 
        state.getConfig().remove(DTFProperties.DTF_NODE_NAME);
        
        // Execute testproperty tags that will automatically record their value
        // for the next test result generated.
        executeChildren(Testproperty.class);
        
        ScriptUtil.executeScript(getStorageFactory().getInputStream(getUri()), 
                                 state);
    }

    public URI getUri() throws ActionException, ParseException { return parseURI(uri); }
    public void setUri(String uri) throws ActionException { this.uri = uri; }
}
