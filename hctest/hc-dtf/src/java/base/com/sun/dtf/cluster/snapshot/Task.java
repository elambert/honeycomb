package com.sun.dtf.cluster.snapshot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.sun.dtf.exceptions.SnapshotException;

public abstract class Task extends Thread {

    protected File _base = null;
    protected File _dst = null;
    private TaskGenerator _generator = null;
    
    public Task(File base, File destination, TaskGenerator generator) { 
        _generator = generator;
        _base = base;
        _dst = destination;
    }
    
    public static ArrayList getTasks(String type, 
                                     File source,
                                     File destination,
                                     TaskGenerator generator,
                                     int workers) 
                  throws SnapshotException { 
        ArrayList tasks = new ArrayList();
        
        if (type.equalsIgnoreCase("copy")) { 
            for (int i = 0; i < workers; i++) 
                tasks.add(new CopyTask(source, destination, generator));
        } else if (type.equalsIgnoreCase("move")) { 
            tasks.add(new MoveTask(source, destination, generator));
        } else if (type.equalsIgnoreCase("delete")) { 
            for (int i = 0; i < workers; i++)  {
                tasks.add(new DeleteTask(source, destination, generator));
            }
        } else
            throw new SnapshotException("Unkown type [" + type + "]");
        
        return tasks;
    }
    
    protected abstract void operate(File source, File destination) throws IOException;
    
    public void run() {
        File work = null;
        while ((work = _generator.nextFile()) != null) { 
            try {
                String filename = work.getPath();
                String basename = _base.getPath();
                filename = filename.replaceFirst(basename, "");
                operate(work, new File(_dst,filename));
            } catch (IOException e) {
                throw new RuntimeException("Error Handling file [" + work + 
                                           "]." ,e);
            }
        }
    }
}
