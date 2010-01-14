package com.sun.dtf.cluster;

import com.sun.dtf.actions.Action;
import com.sun.dtf.comm.CommClient;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.util.HCProperties;

public class HCEmulator extends Cluster {

    public HCEmulator() {
        CommClient.addAgentAttribute(HCProperties.HC_CLUSTER_TYPE, 
                                     Cluster.HC_EMULATOR);
       
        CommClient.addAgentAttribute(HCProperties.HC_CLUSTER_ADMINVIP,
                                    "localhost", true);

        CommClient.addAgentAttribute(HCProperties.HC_CLUSTER_DATAVIP,
                                     "localhost", true);

        CommClient.addAgentAttribute(HCProperties.HC_CLUSTER_SPVIP,
                                     "localhost", true);
        
        
        Action.getLogger().info("Started HCEmulator monitor.");
    }
    
    public void initCluster() throws DTFException {
        
    }
    
    public NodeInterface getNode(int nodeID) throws DTFException {
        return new EmulatorNode();
    }

    public int getNumNodes() {
        return 1;
    }

    public String getAdminVIP(String cellID) { return null; }
    public String getDataVIP(String cellID) { return null; }
    public String getSpVIP(String cellID) { return null; }
    
    public int getCellCount() { return 0; }
    public String getCellId(int index) { return null; }
}
