package com.sun.dtf.cluster.snapshot;

import java.io.File;
import java.util.ArrayList;

public class TaskGenerator {

    private File _root = null;
    private ArrayList _files = null;
    private ArrayList _directories = null;
    
    public TaskGenerator(File root) { 
        _root = root;
        _directories = new ArrayList();
        _files = new ArrayList();
        _directories.add(_root);
    }
    
    public synchronized File nextFile() { 
        if (_files.size() == 0) {
           
            if (_directories.size() == 0)
                return null;
            
            File dir = (File)_directories.remove(0);
            File[] files = dir.listFiles();

            if (files != null) {
                for (int i = 0; i < files.length; i++) { 
                    if (!files[i].isHidden()) {
                        if (files[i].isDirectory()) 
                            _directories.add(files[i]);
                        else 
                            _files.add(files[i]);
                    }
                }
            }

            // add directories to be handled as well 
            if (!dir.isHidden())
                _files.add(dir);
        }

        if (_files.size() == 0)
            return null;
            
        return (File)_files.remove(0);
    }
    
    public static void main(String[] args) {
        TaskGenerator tg = new TaskGenerator(new File("/tmp"));
       
        File aux = null;
        while ((aux = tg.nextFile()) != null) { 
            System.out.println("File: " + aux.getPath());
        }
    }
}
