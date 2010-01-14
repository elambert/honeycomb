package com.sun.dtf.util;

import java.io.File;

public class SystemUtil {

    public static void deleteDirectory(File path) { 
        String[] files = path.list();

        if (files != null) { 
            for (int i = 0; i < files.length; i++) { 
                File file = new File(path.getAbsolutePath() + File.separatorChar +  files[i]);
               
                if (file.isFile()) 
                    file.delete();
                else 
                    deleteDirectory(file);
            }
        }
        
        path.delete();
    }
    
    
}
