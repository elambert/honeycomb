package com.sun.dtf.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.dtf.exception.StorageException;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.util.SystemUtil;


public class Storage {
  
    private static DTFLogger _logger = DTFLogger.getLogger(Storage.class);

    private String _id = null;
    private File _path = null;
    
    public Storage(String id, String path) throws StorageException {
        _id = id;
        _path = new File(path);
      
        if (!_path.exists()) {
            _logger.warn("Storage path does not exist [" + path + "] will create.");
            
            if (!_path.mkdirs())
                throw new StorageException("Unable to create storage path [" + path + "]");
        }
    }
   
    public OutputStream getOutputStream(String filename,boolean append) 
           throws StorageException {
        try {
            File file = new File(_path.getAbsolutePath() + File.separatorChar + filename);
            
            if (!append && file.isDirectory()) {
                _logger.info("Wiping " + file);
                SystemUtil.deleteDirectory(file);
            }
                
            return new FileOutputStream(file, append);
        } catch (FileNotFoundException e) {
            throw new StorageException("Unable to retrieve OutputStream for storage: " + _id,e);
        }
    }
    
    public InputStream getInputStream(String filename) 
           throws StorageException {
        try {
            return new FileInputStream(_path.getAbsolutePath() + 
                                       File.separatorChar + filename);
        } catch (FileNotFoundException e) {
            throw new StorageException("Unable to retrieve InputStream for storage: " + _id,e);
        }
    }
    
    public String getId() { return _id; }
    public void setId(String id) { _id = id; }

    public File getPath() { return _path; }
}
