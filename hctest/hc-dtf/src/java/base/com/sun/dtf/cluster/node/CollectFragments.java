package com.sun.dtf.cluster.node;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;

public class CollectFragments extends NodeAction {
    
    private String where = null;
    private String drive = null;
    
    public void execute(NodeInterface node) throws DTFException {
        node.collectFrags(getDrive(),getWhere() + node.getId() + "." + getDrive() + ".gz");
        getRemoteLogger().info("Checkfrags",
                               "Collected fragments from node " + node.getId() 
                               + " drive " + getDrive());
    }
   
    public String getWhere() { return where; }
    public void setWhere(String where) { 
        this.where = where;
    }
    
    public String getDrive() { return drive; } 
    public void setDrive(String drive) { this.drive = drive; } 
}
