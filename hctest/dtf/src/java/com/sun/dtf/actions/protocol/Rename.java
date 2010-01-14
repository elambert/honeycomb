package com.sun.dtf.actions.protocol;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.actions.Action;

public class Rename extends Action {
    
    public String name = null;
    
    public Rename() {}

    public void execute() {
        getLogger().info("Node name set to: " + getName());
        getConfig().setProperty(DTFProperties.DTF_NODE_NAME,getName());
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; } 
}
