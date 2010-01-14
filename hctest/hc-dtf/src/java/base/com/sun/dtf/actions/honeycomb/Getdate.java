package com.sun.dtf.actions.honeycomb;

import java.util.Date;

import com.sun.dtf.actions.honeycomb.util.NVOAPool;
import com.sun.dtf.actions.properties.Property;
import com.sun.dtf.comm.rpc.ActionResult;
import com.sun.dtf.comm.rpc.Node;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.honeycomb.client.NameValueObjectArchive;

/**
 * @dtf.tag getdate
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The tag will get the current date from the cluster and will 
 *               return it in the property attribute as being a Unix time stamp. 
 *               
 * @dtf.tag.example 
 * <component id="CLIENT1">
 *     <getdate property="cluster.timestamp"/>
 * </component>
 */
public class Getdate extends HCBasicOperation {

    /**
     * @dtf.attr property
     * @dtf.attr.desc the property attribute will hold the value of the time on 
     *                the cluster side expressed as a Unix time stamp.
     */
    private String property = null;
   
    public Getdate() { }
    
    public void execute() throws DTFException {
        connectionSetup();
        
        NameValueObjectArchive archive =
                                       NVOAPool.getNVOA(getDatavip(),getPort());
        
        Date date = GetDate.getDate(archive);
        /*
         * Return a property action to the DTFX that has executed this action 
         * so that it can record this property in the runtime environment of 
         * the test being executed on the DTFX side.
         */
        Property property = new Property();
        property.setName(getProperty());
        property.setValue(""+(date.getTime()));
   
        ActionResult ar = (ActionResult)getContext(Node.ACTION_RESULT_CONTEXT);
        ar.addAction(property);
    }
    
    public String getProperty() throws ParseException { return replaceProperties(property); }
    public void setProperty(String property) { this.property = property; }
}
