package com.sun.dtf.cluster.node;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;

public class Copy extends NodeAction {
   
    private String from = null;
    private String to = null;
    
    public void execute(NodeInterface node) throws DTFException {
        File from = new File(getFrom());
        File[] files = null;
        
        if (from.isDirectory()) { 
            files = from.listFiles();
        } else {
            files = new File[] { from };
        } 
            
        for(int f = 0; f < files.length; f++) { 
            FileInputStream fis;
            try {
                fis = new FileInputStream(files[f]);
            } catch (FileNotFoundException e) {
                throw new DTFException("Error opening [" + files[f] + "]",e);
            }
            node.scpTo(fis, getTo() + File.separatorChar + files[f].getName());
        }
    }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
}
