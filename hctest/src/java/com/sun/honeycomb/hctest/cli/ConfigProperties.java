/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.hctest.cli;

import java.util.HashMap;

import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.util.*;

/**
 *
 * @author jk142663
 */
public class ConfigProperties {
    
    // config file
    protected static final String CONFIG_PROPERTIES_FILE = 
            "/config/config.properties";
    
    // hivecfg properties
    protected static final String CONFIG_PROPERTIES_SMTP_SERVER = 
            "honeycomb.cell.smtp.server";
    protected static final String CONFIG_PROPERTIES_SMTP_PORT = 
            "honeycomb.cell.smtp.port";
    protected static final String CONFIG_PROPERTIES_NTP_SERVER = 
            "honeycomb.cell.ntp";
    protected static final String CONFIG_PROPERTIES_LOGGER = 
            "honeycomb.cell.external_logger";
    protected static final String CONFIG_PROPERTIES_AUTH_CLIENT = 
            "honeycomb.security.authorized_clients";
    protected static final String CONFIG_PROPERTIES_DNS = 
            "honeycomb.cell.dns";
    protected static final String CONFIG_PROPERTIES_DOMAIN_NAME = 
            "honeycomb.cell.domain_name";
    protected static final String CONFIG_PROPERTIES_DNS_SEARCH = 
            "honeycomb.cell.dns_search";
    protected static final String CONFIG_PROPERTIES_PRIMARY_DNS_SERVER = 
            "honeycomb.cell.primary_dns_server";
    protected static final String CONFIG_PROPERTIES_SECONDARY_DNS_SERVER = 
            "honeycomb.cell.secondary_dns_server";
    
    // logdump properties
    protected static final String CONFIG_PROPERTIES_LOGDUMP_GEO = 
            "honeycomb.logdump.exp.contact.geo";
    protected static final String CONFIG_PROPERTIES_LOGDUMP_SERVER = 
            "honeycomb.logdump.exp.proxy.server";
    protected static final String CONFIG_PROPERTIES_LOGDUMP_PORT = 
            "honeycomb.logdump.exp.proxy.port";
    protected static final String CONFIG_PROPERTIES_LOGDUMP_CONTACT = 
            "honeycomb.logdump.exp.contact.name";
    protected static final String CONFIG_PROPERTIES_LOGDUMP_PHONE = 
            "honeycomb.logdump.exp.contact.phone";
    protected static final String CONFIG_PROPERTIES_LOGDUMP_EMAIL = 
            "honeycomb.logdump.exp.contact.email";
    
    private static String [] lsConfigPropName = null;
    
    protected static String [] lsHivecfgConfigPropName = {
            CONFIG_PROPERTIES_SMTP_SERVER, 
            CONFIG_PROPERTIES_SMTP_PORT,
            CONFIG_PROPERTIES_NTP_SERVER, 
            CONFIG_PROPERTIES_LOGGER,
            CONFIG_PROPERTIES_AUTH_CLIENT, 
            CONFIG_PROPERTIES_DNS,
            CONFIG_PROPERTIES_DOMAIN_NAME, 
            CONFIG_PROPERTIES_DNS_SEARCH, 
            CONFIG_PROPERTIES_PRIMARY_DNS_SERVER, 
            CONFIG_PROPERTIES_SECONDARY_DNS_SERVER
    };
    
    protected static String [] lsLogdumpConfigPropName = {
            CONFIG_PROPERTIES_LOGDUMP_GEO,
            CONFIG_PROPERTIES_LOGDUMP_SERVER,
            CONFIG_PROPERTIES_LOGDUMP_PORT,
            CONFIG_PROPERTIES_LOGDUMP_CONTACT,
            CONFIG_PROPERTIES_LOGDUMP_PHONE,
            CONFIG_PROPERTIES_LOGDUMP_EMAIL
    };
    
    private HashMap configPropMap = null;
    
    /** Creates a new instance of ConfigProperties */
    public ConfigProperties(String adminIp, String [] lsPropName) 
            throws HoneycombTestException {
        ClusterMembership cm = new ClusterMembership(-1, adminIp);
        
        cm.setQuorum(true);
        cm.initClusterState(); 
        
        ClusterNode node = cm.getMaster();
        
        String configfileStdout = node.runCmd("cat " + CONFIG_PROPERTIES_FILE); 
        parseConfigProperties(configfileStdout, lsPropName);
    }
    
    public ConfigProperties(String adminIp, int nodeId, String [] lsPropName) 
            throws HoneycombTestException {
        ClusterMembership cm = new ClusterMembership(-1, adminIp);
        
        cm.setQuorum(true);
        cm.initClusterState(); 
        
        ClusterNode node = cm.getNode(nodeId);
        
        String configfileStdout = node.runCmd("cat " + CONFIG_PROPERTIES_FILE); 
        parseConfigProperties(configfileStdout, lsPropName);
    }
    
    public ConfigProperties(ClusterMembership cm, int nodeId, String [] lsPropName) 
                                    throws HoneycombTestException {
        ClusterNode node = cm.getNode(nodeId);
        
        String configfileStdout = node.runCmd("cat " + CONFIG_PROPERTIES_FILE); 
        parseConfigProperties(configfileStdout, lsPropName);
    }
    
    protected void parseConfigProperties(String fileContent, String [] lsPropName) {
        lsConfigPropName = lsPropName;
        configPropMap = new HashMap();
        String [] lsPropNameValue = null;
        
        try {
            lsPropNameValue = HoneycombCLISuite.tokenizeIt(
                    fileContent, "\n");
        } catch (Exception e) {
            Log.ERROR("Error to tokenize the config properties file: " + e);
        }
        
        for (int i=0; i<lsConfigPropName.length; i++) {
            String propLine = null;            
            String propName = lsConfigPropName[i];
            String propValue = null;
            
            for (int totalProp = 0; totalProp<lsPropNameValue.length; totalProp++) {
                propLine = lsPropNameValue[totalProp];
                
                if (propLine.startsWith("#"))
                    continue;
                
                String [] propNameValue = null;                    
                try {
                    propNameValue = HoneycombCLISuite.tokenizeIt(propLine, "=");
                } catch (Exception e) {
                    Log.ERROR("Error to parse the config properties: " + e);
                }
                
                if (propNameValue[0].equals(propName)) {
                    if (propNameValue.length > 1)
                        propValue = propNameValue[1].trim();
                    else
                        propValue = "";
                    
                    configPropMap.put(propName, propValue);
                    
                    break;
                }
            }
        }
    }
    
    protected boolean compareConfigProperties(HashMap expConfigFile) {
        int error = 0;
        
        for (int i=0; i<lsConfigPropName.length; i++) {
            String propName = lsConfigPropName[i];
            
            if ((!expConfigFile.containsKey(propName)) ||
                    (!configPropMap.containsKey(propName))){
                Log.ERROR("Can't verify property " + propName + 
                        " due to unable to find the name/value pair " +
                        "in config properties file");
                error++;
                continue;
            }
            
            String expPropValue = ((String) expConfigFile.get(propName));
            String actPropValue = ((String) configPropMap.get(propName));
            
            if ((expPropValue == null) && (actPropValue == null)) {
                Log.ERROR("Value for property " + propName + " is null");
                error++;
                continue;
            }
            
            if (!expPropValue.trim().equals(actPropValue.trim())) {
                error++;
                Log.ERROR("Config properties - " + propName + ": ");
                Log.ERROR("Expected: " + expPropValue + 
                            "; Actual: " + actPropValue);
            }
        }
        
        if (error == 0)
            return true;
        else
            return false;
    }
    
    public String [] getAllConfigPropName() {
        return lsConfigPropName;
    }
    
    public HashMap getAllConfigPropNameValue() {
        return configPropMap;
    } 
    
}
