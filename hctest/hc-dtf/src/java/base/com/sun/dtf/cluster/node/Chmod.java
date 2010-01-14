package com.sun.dtf.cluster.node;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;

public class Chmod extends NodeAction {
   
    private String options = null;
    private String location = null;
    
    public void execute(NodeInterface node) throws DTFException {
        node.chmod(getOptions(), getLocation());
    }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
