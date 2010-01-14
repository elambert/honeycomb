package com.sun.dtf.cluster;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import com.sun.dtf.actions.Action;
import com.sun.dtf.cluster.node.Chmod;
import com.sun.dtf.cluster.node.Copy;
import com.sun.dtf.cluster.node.Mkdir;
import com.sun.dtf.cluster.node.Rmdir;
import com.sun.dtf.cluster.config.MulticellConfig;
import com.sun.dtf.comm.CommClient;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.util.HCProperties;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;

public class HCCluster extends Cluster {

    public static final String HC_CONFIG_LOCATION  = "lib/hc/config";
    public static final String HC_SCRIPT_LOCATION  = "lib/hc/scripts";

    public static final String HC_JAR              = "lib/hc-dtfhook.jar";
   
    public static final String HC_NODE_BASEDIR     = "/var/adm/test/";
    public static final String HC_NODE_SCRIPTDIR   = "/var/adm/test/scripts";
    public static final String HC_NODE_LIBDIR      = "/var/adm/test/lib";
    public static final String HC_NODE_LOGDIR      = "/var/adm/test/logs";
    
    public static final int SSH_ADMIN_BASE_PORT = 2000; 
    private static int MAX_NODES = 16;
   
    /*
     * Hijack the System Properties so honeycomb libraries will look in that 
     * new config directory for config files.
     */
    static { 
        System.setProperty("honeycomb.config.dir", HC_CONFIG_LOCATION); 
    }
    
    private Config _config = null;
    private ArrayList _nodes = null;
   
    private String _cellID = null;
    private ClusterProperties _clusterConfig = null;
    private MulticellConfig _multiCellConfig = null;
    
    protected HCCluster() throws DTFException { 
        _config = Action.getConfig();
        _nodes = new ArrayList();
        
        /*
         * Admin vip is the way we can get into the cluster and then try to do 
         * the best approach of figuring out the configuration of this cluster.
         */
        String adminvip = _config.getProperty(HCProperties.HC_CLUSTER_ADMINVIP);
        checkProperty(adminvip, HCProperties.HC_CLUSTER_ADMINVIP);
       
        String hcuser = _config.getProperty(HCProperties.HC_SSH_USER,
                                            HCProperties.HC_SSH_USER_DEF);
        String hcpass = _config.getProperty(HCProperties.HC_SSH_PASS,
                                            HCProperties.HC_SSH_PASS_DEF);

        String clusterconf = _config.getProperty(HCProperties.HC_CLUSTER_CONFIG,
                                            HCProperties.HC_CLUSTER_CONFIG_DEF);
        
        String siloconf = _config.getProperty(HCProperties.HC_SILO_CONFIG,
                                              HCProperties.HC_SILO_CONFIG_DEF);
       
        char sep = File.separatorChar;
        String local_clusterconf = HC_CONFIG_LOCATION + sep +  "config.properties";
        String local_siloconf = HC_CONFIG_LOCATION + sep +  "silo_info.xml";
        NodeInterface node = null;
       
        // Find a node that is accessible and retrieve the config file.
        try {
            node = new ClusterNode(adminvip, 
                                   hcuser,
                                   hcpass,
                                   SSH_ADMIN_BASE_PORT + 1);
            FileOutputStream fos = new FileOutputStream(local_clusterconf);
            node.scpFrom(clusterconf, fos);
            _nodes.add(0,node);
        } catch (IOException e) { 
            throw new DTFException("Unable to reated node 1",e);
        } 
      
        // Now with the config we can figure out how many nodes are suppose to
        // be online
        int numnodes;
        try { 
            FileInputStream fis = new FileInputStream(local_clusterconf);
            Properties props = new Properties();
            props.load(fis);

            _clusterConfig = ClusterProperties.getInstance();
            ClusterProperties.load(props);

            numnodes = _clusterConfig.
                            getPropertyAsInt(ConfigPropertyNames.PROP_NUMNODES);

            _cellID = _clusterConfig.getProperty(ConfigPropertyNames.PROP_CELLID);
            
            Action.getLogger().info("Parsed honeycomb cluster config.");
            Action.getLogger().info("Cluster should have " + numnodes 
                                    + " nodes and cellID is: " + _cellID);
        } catch (IOException e) { 
            throw new DTFException("Unable to parse cluster config.",e);
        }
        
        /*
         * Parse multicell config
         */
        try { 
            FileOutputStream fos = new FileOutputStream(local_siloconf);
            node.scpFrom(siloconf, fos);
            
            FileInputStream fis = new FileInputStream(local_siloconf);
            _multiCellConfig = new MulticellConfig(fis);

            Action.getLogger().info("Parsed honeycomb silo config.");
            Action.getLogger().info("Multicell config has found " + 
                                    _multiCellConfig.getNumCells() + " cell(s).");
        } catch (IOException e) { 
            throw new DTFException("Unable to parse silo config.",e);
        }
        
        // Find a node that is accessible and retrieve the config file.
        for (int i = 2; i <= numnodes; i++) { 
            node = new ClusterNode(adminvip, 
                                   hcuser, 
                                   hcpass, 
                                   SSH_ADMIN_BASE_PORT + i);
                    
            _nodes.add(i-1, node);
        }
        
        if (_multiCellConfig.getNumCells() == 0) 
            throw new DTFException("No cells found in silo config.");
        
        Action.getLogger().info("Cluster has datavip [" + 
                                _multiCellConfig.getDataVIP(_cellID) + 
                                "] adminvip [" + 
                                _multiCellConfig.getAdminVIP(_cellID) +
                                "] spVIP [" + 
                                _multiCellConfig.getSpVIP(_cellID) + "].");
       
        // Register DTFA attributes
        CommClient.addAgentAttribute(HCProperties.HC_CLUSTER_TYPE, 
                                     Cluster.HC_CLUSTER);
        
        CommClient.addAgentAttribute(HCProperties.HC_CLUSTER_ADMINVIP,
                                     _multiCellConfig.getAdminVIP(_cellID),
                                     true);
        CommClient.addAgentAttribute(HCProperties.HC_CLUSTER_DATAVIP,
                                     _multiCellConfig.getDataVIP(_cellID),
                                     true);
        CommClient.addAgentAttribute(HCProperties.HC_CLUSTER_SPVIP,
                                     _multiCellConfig.getSpVIP(_cellID),
                                     true);
        
        Action.getLogger().info("Started HCCluster monitor.");
    }
    
    public void initCluster() throws DTFException { 
        /*
         * Rmdir logs
         */
        Rmdir rmdir = new Rmdir();
        rmdir.setDir(HC_NODE_BASEDIR);
        rmdir.execute();

        /*
         * Mkdir logs
         */
        Mkdir mkdir = new Mkdir();
        mkdir.setDir(HC_NODE_BASEDIR);
        mkdir.execute();

        mkdir.setDir(HC_NODE_LOGDIR);
        mkdir.execute();

        mkdir.setDir(HC_NODE_SCRIPTDIR);
        mkdir.execute();

        mkdir.setDir(HC_NODE_LIBDIR);
        mkdir.execute();
        
        /*
         * Copy scripts
         */
        Copy copy = new Copy();
        copy.setFrom(HC_SCRIPT_LOCATION);
        copy.setTo(HC_NODE_SCRIPTDIR);
        copy.execute();

        /*
         * Copy hc-dtf.jar
         */
        copy = new Copy();
        copy.setFrom(HC_JAR);
        copy.setTo(HC_NODE_LIBDIR);
        copy.execute();
        
        /*
         * Chmod scripts to executable
         */
        Chmod chmod = new Chmod();
        chmod.setLocation(HC_NODE_SCRIPTDIR + File.separatorChar + "*");
        chmod.setOptions("+x");
        chmod.execute();
    }
    
    public String getAdminVIP(String cellID) {
        return _multiCellConfig.getAdminVIP(cellID);
    }
    
    public String getDataVIP(String cellID) {
        return _multiCellConfig.getDataVIP(cellID);
    }
    
    public String getSpVIP(String cellID) {
        return _multiCellConfig.getSpVIP(cellID);
    }
    
    public int getCellCount() { 
        return _multiCellConfig.getNumCells(); 
    }
    
    public String getCellId(int index) {
        return _multiCellConfig.getCellId(index);
    }

    public int getNumNodes() { return _nodes.size(); }
    
    public NodeInterface getNode(int nodeID) throws DTFException { 
        if (nodeID >=1 && nodeID <= MAX_NODES) 
            return (NodeInterface) _nodes.get(nodeID-1);
        else
            throw new DTFException("Illegal node id, only accepting ids between 1 and " + MAX_NODES);
    }
}
