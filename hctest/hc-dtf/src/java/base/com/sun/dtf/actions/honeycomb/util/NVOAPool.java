package com.sun.dtf.actions.honeycomb.util;

import java.io.IOException;
import java.util.Hashtable;


import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.TestNVOA;
import com.sun.honeycomb.common.ArchiveException;

public class NVOAPool {

    private static Hashtable _nvoas = new Hashtable();
    
    public synchronized static NameValueObjectArchive getNVOA(String host, 
                                                              int port) 
                        throws DTFException {
        NameValueObjectArchive nvoa = 
                (NameValueObjectArchive)_nvoas.get(host + ":" + port);
        
        if (nvoa == null)  {
            try {
                Action.getLogger().info("New NVOA connection created for: " + 
                                        host + ":" + port);
                nvoa = new TestNVOA(host, port);
            } catch (ArchiveException e) {
                throw new DTFException("Error instantiationg honeycomb NVOA.",e);
            } catch (IOException e) {
                throw new DTFException("Error instantiationg honeycomb NVOA.",e);
            }
            
            _nvoas.put(host + ":" + port, nvoa);
        }
        
        return nvoa;
    }
    
    public synchronized static void removeNVOA(String host, int port) { 
        _nvoas.remove(host + ":" + port);
    }
}
