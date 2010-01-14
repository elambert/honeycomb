package com.sun.dtf.comm.rpc;

import com.sun.dtf.NodeInfo;
import com.sun.dtf.NodeState;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.protocol.Connect;
import com.sun.dtf.actions.protocol.Lock;
import com.sun.dtf.actions.protocol.Rename;
import com.sun.dtf.exception.DTFException;

public class ControllerNode implements NodeInterface, ControllerInterface {
    
    public ActionResult execute(String id, Action action) throws DTFException {
        ActionResult result = new ActionResult();

        if (id.equals("dtfc")) { 
            action.execute();
            if (action instanceof Lock) 
                result.addAction(action);
        } else { 
           NodeInfo node = NodeState.getInstance().getNodeInfo(id);
           return node.getClient().sendAction(id, action);
        }
        
        return result;
    }
    
    public Boolean heartbeat() {
        return Boolean.TRUE;
    }
    
    public ActionResult register(Connect connect) throws DTFException {
        ActionResult result = new ActionResult();
        connect.execute();
        Rename rename = new Rename();
        rename.setName(connect.getId());
        result.addAction(rename);
        return result;
    }
    
    public ActionResult unregister(Connect connect) throws DTFException {
        ActionResult result = new ActionResult();
       
        NodeState ns = NodeState.getInstance();
        ns.removeNode(connect);
        
        return result;
    }
}
