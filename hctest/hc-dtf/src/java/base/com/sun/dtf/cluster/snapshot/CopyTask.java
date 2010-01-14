package com.sun.dtf.cluster.snapshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class CopyTask extends Task {

    public CopyTask(File source, File destination, TaskGenerator generator) { 
        super(source, destination, generator);
    }
    
    protected void operate(File source, File destination) throws IOException { 
        if (source.isDirectory())  {
            destination.mkdirs();
            return;
        }

        FileInputStream in = new FileInputStream(source);

        if (destination.isDirectory()) {
            destination = new File(destination, source.getName());
        }

        // Nasty I know but sometimes there is collisions in try to create 
        // the exact same directory
        while (!destination.getParentFile().exists())
            destination.getParentFile().mkdirs();

        FileOutputStream out = new FileOutputStream(destination);
        
        FileChannel fin  = in.getChannel();
        FileChannel fout = out.getChannel();
           
        long transfered = 0;
        while (transfered < fin.size()) { 
            transfered += fin.transferTo(0, fin.size(), fout);
        }
    }
}
