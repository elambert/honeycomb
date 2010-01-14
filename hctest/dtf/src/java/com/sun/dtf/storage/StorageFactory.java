package com.sun.dtf.storage;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;

import com.sun.dtf.exception.StorageException;


public class StorageFactory {

    private HashMap _storages = null;
   
    /**
     * There will always be the DEFAULT storage which actually refers to the 
     * directory where DTF resides. ie where the dtf.properties is.
     */
    public StorageFactory() {
        _storages = new HashMap();
    }
   
    /**
     * 
     * @param id
     * @return
     * @throws StorageException
     */
    public Storage retrieveStorage(String id) 
           throws StorageException {
        if (_storages.containsKey(id))
            return (Storage)_storages.get(id);
        else 
            throw new StorageException("Unable to find storage: " + id);
    }
   
    /**
     * 
     * @param id
     * @return
     */
    public boolean checkStorage(String id) {
        return _storages.containsKey(id);
    }
   
    /**
     * 
     * @param id
     * @param path
     * @throws StorageException
     */
    public void createStorage(String id, String path) 
           throws StorageException {
        Storage storage = new Storage(id,path);
        
        if (_storages.containsKey(id))
            throw new StorageException("Storage already exists with id: " + id);
        
        _storages.put(id, storage);
    }

    /**
     * 
     * @param uri
     * @return
     * @throws StorageException
     */
    public String getPath(URI uri) throws StorageException {
        Storage storage = retrieveStorage(uri.getHost());
        return storage.getPath().getAbsolutePath() + 
               File.separatorChar + uri.getPath();
    }
   
    /**
     * 
     * @param uri
     * @return
     * @throws StorageException
     */
    public InputStream getInputStream(URI uri) throws StorageException {
        Storage storage = retrieveStorage(uri.getHost());
        return storage.getInputStream(uri.getPath()); 
    }
  
    /**
     * 
     * @param uri
     * @return
     * @throws StorageException
     */
    public OutputStream getOutputStream(URI uri) throws StorageException {
        return getOutputStream(uri,false);
    }

    /**
     * 
     * @param uri
     * @return
     * @throws StorageException
     */
    public OutputStream getOutputStream(URI uri,boolean append) throws StorageException {
        Storage storage = retrieveStorage(uri.getHost());
        return storage.getOutputStream(uri.getPath(),append); 
    }
   
    /**
     * 
     */
    public Object clone() throws CloneNotSupportedException {
        StorageFactory storageFactory = new StorageFactory();
        storageFactory._storages = (HashMap)this._storages.clone(); 
        return storageFactory;
    }
}
