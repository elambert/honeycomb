package com.sun.dtf.cluster.snapshot;

import java.io.File;
import java.io.IOException;

public class MoveTask extends Task {
    
    public MoveTask(File source, File destination, TaskGenerator generator) { 
        super(source, destination,generator);
    }
    
    protected void operate(File source, File destination) throws IOException { 
        // nothing to do everything is done in one shot in the run 
    }
    
    public void run() {
        File[] files = _base.listFiles();
       
        for(int i = 0; i < files.length; i++) { 
           
            if (files[i].isHidden())
                continue;
            
            File dest = new File(_dst,files[i].getName());
            if (!files[i].renameTo(dest))
                throw new RuntimeException("Failed to move [" + files[i] + 
                                           "] to [" + dest + "]");
        }
    }
}
