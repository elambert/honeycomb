package com.sun.dtf;

import java.util.ArrayList;

import com.sun.dtf.actions.component.Attrib;
import com.sun.dtf.actions.protocol.Connect;
import com.sun.dtf.actions.protocol.Lock;
import com.sun.dtf.comm.CommClient;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.StringUtil;


public class NodeInfo extends Connect {
    
    private boolean locked = false;
    private CommClient _client = null;
  
    // Safety mutex for generating the unique names for components.
    private static Object genObject = new Object();
    private static long componentCount = 0;
    
    public NodeInfo(Connect conn, CommClient client) throws DTFException {
        _client = client;
        
        setId(conn.getId());
        addAttribs(conn.findActions(Attrib.class));
        
        // generate unique name for this component
        if (getId() == null) { 
            synchronized(genObject) {
                String type = findAttrib("type");
                setId((type == null ? "dtfa" : type) + "-" + componentCount++);
            }
        }
       
        setAddress(conn.getAddress());
        setPort(conn.getPort());
    }

    public NodeInfo(Lock lock) throws DTFException {
        setId(lock.getId());
        addAttribs(lock.findActions(Attrib.class));
    }
    
    public boolean matches(NodeInfo ni) throws ParseException {
        ArrayList attribs = ni.findActions(Attrib.class);
        ArrayList myAttribs = findActions(Attrib.class);
     
        for(int i = 0; i < myAttribs.size(); i++) { 
            Attrib attrib = (Attrib)myAttribs.get(i);
            boolean found = false;
            
            for(int j = 0; j < attribs.size(); j++) { 
                if (attrib.matches((Attrib)attribs.get(j))) {
                    found = true;
                    break;
                }
            }
           
            if (!found) return false;
        }
        
        if (StringUtil.equalsIgnoreCase(getId(), ni.getId())) 
            return true;
           
        return false;
    }
    
  
    public void lock() { assert locked == false; locked = true;  }
    public void unlock() { assert locked == true; locked = false; }
    
    public boolean isLocked() { return locked; }
    
    public CommClient getClient() { return _client; } 
}
