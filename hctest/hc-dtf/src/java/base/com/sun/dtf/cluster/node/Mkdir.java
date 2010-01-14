package com.sun.dtf.cluster.node;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;

public class Mkdir extends NodeAction {
   
    private String dir = null;
    
    public void execute(NodeInterface node) throws DTFException {
        node.mkdir(getDir());
    }

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
}
