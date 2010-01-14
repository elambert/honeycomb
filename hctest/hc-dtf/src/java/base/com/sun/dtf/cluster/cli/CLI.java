package com.sun.dtf.cluster.cli;

import java.io.InputStream;
import java.util.HashMap;

import com.sun.dtf.actions.Action;
import com.sun.dtf.cluster.Cluster;
import com.sun.dtf.config.Config;
import com.sun.dtf.util.HCProperties;
import com.sun.dtf.exceptions.CLIException;

public abstract class CLI {

    public static final String EXEC_CHANNEL = "exec";
    
    private static HashMap _instances = new HashMap();
    public CLI() { }
    
    public synchronized static CLI getInstance(String adminvip,
                                               String user,
                                               String pass)
                        throws CLIException { 
        String key = user + ":" + pass + "@" + adminvip;
        Config config = Action.getConfig();
        
        String type = config.getProperty(HCProperties.HC_CLUSTER_TYPE);
        
        if (!_instances.containsKey(key)) { 
            if (type.equalsIgnoreCase(Cluster.HC_CLUSTER)) {
                _instances.put(key, new HCCLI(adminvip,user,pass));
            } else  if (type.equalsIgnoreCase(Cluster.HC_EMULATOR))  {
                _instances.put(key, new EmulatorCLI(adminvip,user,pass));
            } else 
                throw new CLIException("Cluster type unrecognized [" + 
                                       Cluster.HC_CLUSTER + "].");
        }
        
        return (CLI)_instances.get(key);
    }
   
    public abstract String[] df() throws CLIException;
    
    public abstract void reboot(String[] options) throws CLIException;
    
    public abstract String[] sysstat(String[] options) throws CLIException;
    
    public abstract String[] mdconfig(String[] options, 
                                      InputStream is) 
                    throws CLIException;
    
    public abstract String[] setprops(String name, 
                                      String value)
                    throws CLIException;
}
