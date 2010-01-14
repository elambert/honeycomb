package com.sun.dtf.actions.component;

import java.util.ArrayList;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.protocol.Lock;
import com.sun.dtf.comm.Comm;
import com.sun.dtf.comm.CommClient;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.TimeUtil;


/**
 * @dtf.tag lockcomponent
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is used to identify and lock the components required 
 *               for the execution of the current test case. When you lock a 
 *               {@dtf.link Component} you can then define a global alias 
 *               by which it will be known for use by other tags such as the 
 *               {@dtf.link Component} tag.
 * 
 * @dtf.tag.example 
 * <local>
 *     <echo>Remote counter retrieval</echo>
 *     <lockcomponent id="DTFA1">
 *         <attrib name="type" value="DTFA"/>
 *     </lockcomponent>
 * </local>
 */
public class Lockcomponent extends Action {

    /**
     * @dtf.attr id
     * @dtf.attr.desc Specifies the internal ID that will be used to identify 
     *                this component in the test case. 
     */
    private String id = null;
    
    /**
     * @dtf.attr name
     * @dtf.attr.desc Specifies the exact name of the component we want to lock,
     *                this name will identify one and only one component that
     *                has been registered with the DTFC. 
     */
    private String name = null;
    
   
    /**
     * @dtf.attr timeout
     * @dtf.attr.desc The timeout in seconds to wait for the component to 
     *                become available to the DTFC. This timeout is useful when
     *                automating testing and wanting to start up the framework 
     *                along with the test driver (DTFX) and not knowing when 
     *                the necessary components are available. Using the timeout
     *                you can estimate the component will be come available with
     *                in X amount of time. 
     */
    private String timeout = null;
   
    public Lockcomponent() { }
  
    private static Object _lock = new Object();
    
    // tricky code not for anyone to change.. :) 
    public void execute() throws DTFException { 
       
        /*
         * Whenever we haven't been named lets make sure to register first.
         */
        synchronized (_lock) {
            if (Action.getLocalID() == null) { 
                getComm().getCommClient("dtfc").register();
            } 
        }
        
        Lock lock = new Lock(getName(),getId(),getTimeout());
      
        /*
         * Add all Attribs that we're looking for in a component
         */
        ArrayList attribs = findActions(Attrib.class);
        lock.addActions(attribs);
        
        Action result = getComm().sendAction("dtfc", lock);
        
        // TODO: why not execute the result ? 
        if (result != null) { 
            Lock returnedLock = (Lock)result.findActions(Lock.class).get(0);

            CommClient client = new CommClient(returnedLock.getAddress(),
                                               returnedLock.getPort());
           
            if (client.heartbeat().booleanValue()) { 
                if (getLogger().isDebugEnabled())
                    getLogger().debug("Direction connection to component being used.");

                Comm.addClient(returnedLock.getId(), client);
            }
            
            /*
             * Record  the right attributes as test properties in the record 
             * and set the right test properties for the others.
             */
            attribs = returnedLock.findActions(Attrib.class);
            Config config = getConfig();
            for(int i = 0; i < attribs.size(); i++) { 
                Attrib attrib = (Attrib)attribs.get(i);
                
                if (attrib.isTestProp())
                    config.setProperty(attrib.getName(),attrib.getValue());
                
                getResults().recordProperty(getId() + "." + attrib.getName(), 
                                            attrib.getValue());
            }

            getComponents().registerComponent(getId(), returnedLock);
            getLogger().info("Component locked " + returnedLock + " as " + getId());
        } else { 
            throw new DTFException("Unable to register component :(.");
        }
    }

    public String getId() throws ParseException { return replaceProperties(id); }
    public void setId(String id) { this.id = id; }

    public String getName() throws ParseException { return replaceProperties(name); }
    public void setName(String name) { this.name = name; }

    public long getTimeout() throws ParseException { 
        return TimeUtil.parseTime("timeout",replaceProperties(timeout)); 
    }
    public void setTimeout(String timeout) { this.timeout = timeout; }
}
