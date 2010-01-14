package com.sun.dtf.util;

public class HCProperties {

    /**
     * This constant is used to figure out which version of honeycomb I am 
     * running and use the honeycomb API in the correct manner since we are 
     * linking with the specific build id!
     */
    public static final String HC_CLIENT_BUILD      = "hc.client.build";
    public static final String HC_CLIENT_VERSION    = "hc.client.version";
    public static final String HC_CLIENT_TYPE       = "hc.client.type";

    public static final String HC_CLUSTER_TYPE      = "hc.cluster.type";
    public static final String HC_CLUSTER_NUMNODES  = "hc.cluster.numnodes";
    
    public static final String HC_CLUSTER_DATAVIP   = "hc.cluster.datavip";
    public static final String HC_CLUSTER_ADMINVIP  = "hc.cluster.adminvip";
    public static final String HC_CLUSTER_SPVIP     = "hc.cluster.spvip";
    
    public static final String HC_SSH_USER          = "hc.ssh.user";
    public static final String HC_SSH_PASS          = "hc.ssh.pass";
    
    public static final String HC_SSH_USER_DEF      = "root";
    public static final String HC_SSH_PASS_DEF      = "honeycomb";

    public static final String HC_ADMIN_USER        = "hc.admin.user";
    public static final String HC_ADMIN_PASS        = "hc.admin.pass";
    
    public static final String HC_ADMIN_USER_DEF    = "admin";
    public static final String HC_ADMIN_PASS_DEF    = "honeycomb";
  
    /*
     * Cluster properties
     */
    public static final String HC_CLUSTER_CONFIG       = "hc.cluster.config";
    public static final String HC_CLUSTER_CONFIG_DEF   = "/config/config.properties";

    public static final String HC_SILO_CONFIG          = "hc.silo.config";
    public static final String HC_SILO_CONFIG_DEF      = "/config/silo_info.xml";
}
