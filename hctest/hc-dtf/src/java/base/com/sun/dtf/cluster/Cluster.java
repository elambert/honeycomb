package com.sun.dtf.cluster;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.dtf.actions.Action;
import com.sun.dtf.cluster.ssh.SSHCommand;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exceptions.NodeCommException;
import com.sun.dtf.util.HCProperties;

public abstract class Cluster {

    public static final String HC_CLUSTER  = "hccluster";
    public static final String HC_EMULATOR = "hcemulator";
    
    private static Cluster _instance = null;
    
    public static synchronized Cluster getInstance() throws DTFException { 
        return _instance;
    }
    
    public static void init() throws DTFException { 
        String type = Action.getConfig().getProperty(HCProperties.HC_CLUSTER_TYPE);
        
        if (_instance == null) { 
            if (type.equalsIgnoreCase(Cluster.HC_CLUSTER)) {
                _instance = new HCCluster();
            } else  if (type.equalsIgnoreCase(Cluster.HC_EMULATOR))  {
                _instance = new HCEmulator(); 
            } else 
                throw new DTFException("Cluster type unrecognized [" + type + "].");
        }
        
        _instance.initCluster();
    }
    
    public abstract void initCluster() throws DTFException;
        
    public abstract String getAdminVIP(String cellID);
    public abstract String getDataVIP(String cellID);
    public abstract String getSpVIP(String cellID);
    
    public abstract int getCellCount();
    public abstract String getCellId(int index);
    
    public abstract NodeInterface getNode(int nodeID) throws DTFException;
    public abstract int getNumNodes();

    protected void info(InputStream in) throws NodeCommException {
        try {
            byte[] tmp = new byte[16 * 1024];
            int i = in.read(tmp, 0, tmp.length);
            while (i != -1) {
                Action.getLogger().info(new String(tmp,0,tmp.length));
                i = in.read(tmp, 0, tmp.length);
            }
        } catch (IOException e) {
            throw new NodeCommException("Failed to communicate to node 101", e);
        }
    }
    
    protected void error(InputStream in) throws NodeCommException {
        try {
            byte[] tmp = new byte[16 * 1024];
            int i = in.read(tmp, 0, tmp.length);
            while (i != -1) {
                Action.getLogger().error(new String(tmp,0,tmp.length));
                i = in.read(tmp, 0, tmp.length);
            }
        } catch (IOException e) {
            throw new NodeCommException("Failed to communicate to node 101", e);
        }
    }
    
    protected void debug(InputStream in) throws NodeCommException {
        try {
            byte[] tmp = new byte[16 * 1024];
            int i = in.read(tmp, 0, tmp.length);
            while (i != -1) {
                Action.getLogger().debug(new String(tmp,0,tmp.length));
                i = in.read(tmp, 0, tmp.length);
            }
        } catch (IOException e) {
            throw new NodeCommException("Failed to communicate to node 101", e);
        }
    }
    
    protected void checkProperty(String property, 
                                      String propertyName) 
                   throws DTFException { 
        if (property == null) 
            throw new DTFException("Please set the [" + propertyName + 
                                   "] property.");
    }
}
