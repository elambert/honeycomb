package com.sun.dtf.actions.protocol;

import java.util.ArrayList;
import java.util.Hashtable;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.NodeInfo;
import com.sun.dtf.NodeState;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.component.Attrib;
import com.sun.dtf.comm.CommClient;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.CollectionsUtil;


/**
 * This action is used internally for connecting to the DTFC
 * 
 * @author Rodney Gomes
 */
public class Connect extends Action {
   
    private String id = null;
   
    /**
     * Sending some settings to the dtfc
     */
    private String address = null;
    private int port = -1;

    public Connect() {}
    
    public Connect(String id) throws DTFException {
        this.id = id;
        setAddress(getConfig().getProperty(DTFProperties.DTF_LISTEN_ADDR));
        setPort(getConfig().getPropertyAsInt(DTFProperties.DTF_LISTEN_PORT));
    }
    
    public void copy(NodeInfo info) throws DTFException {
        setId(info.getId());
        setAddress(info.getAddress());
        setPort(info.getPort());
        addAttribs(info.findActions(Attrib.class));
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public void execute() throws DTFException {
        // Register myself on the component that just accepted my 
        // ConnectAction object
        NodeState ns = NodeState.getInstance();
        CommClient client = new CommClient(getAddress(), getPort());
        copy(ns.addNode(this,client));
        getLogger().info("Agent " + getId() + " connected, from " + 
                         getAddress() + ":" + getPort());
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public boolean equals(Object obj) {
        if (obj instanceof Connect) { 
            return ((Connect)obj).getId().equals(getId());
        }
        return false;
    }
    
    public void addAttrib(Attrib attrib) { 
       ArrayList current_attribs = findActions(Attrib.class);
       
       if (!CollectionsUtil.isIn(attrib, current_attribs))
           addAction(attrib);
    }
   
    public void addAttribs(ArrayList attribs) { 
       ArrayList current_attribs = findActions(Attrib.class);
      
       for (int i = 0; i < attribs.size(); i++) { 
           if (!CollectionsUtil.isIn((Attrib)attribs.get(i), current_attribs))
               addAction((Action)attribs.get(i));
       }
    }
    
    public String findAttrib(String name) throws ParseException { 
       ArrayList attribs = findActions(Attrib.class);
      
       for (int i = 0; i < attribs.size(); i++) { 
           Attrib attrib = (Attrib) attribs.get(i);
           if (attrib.getName().equals(name))
               return attrib.getValue();
       }
       
       return null;
    }
    
    protected Hashtable getAttribs(Class actionClass) {
        Hashtable result =  super.getAttribs(actionClass);
        ArrayList attribs = findActions(Attrib.class);
        
        for (int i = 0; i < attribs.size(); i++) {
            Attrib attrib = (Attrib)attribs.get(i);
            try {
                result.put(attrib.getName(),attrib.getValue());
            } catch (ParseException e) {
                getLogger().warn("Unable to get property names.",e);
            }
        }
       
        return result;
    }
}
