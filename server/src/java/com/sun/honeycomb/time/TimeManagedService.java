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



package com.sun.honeycomb.time;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.ArrayIndexOutOfBoundsException;
import java.lang.Math;

import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.time.NTPServer;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.Switch;

/**
 * Public definition of Time managed service.
 * It is an interface that extends ManagedService and
 * exports its own public API and proxy object.
 */
public interface TimeManagedService 
    extends ManagedService.RemoteInvocation, ManagedService {
    /*
     * RMI or API exports - Currently none 
     */

    /*
     * Time managed service exports the following proxy object.
     */
    public class Proxy extends ManagedService.ProxyObject {

        private transient static final Logger logger = 
            Logger.getLogger(Proxy.class.getName());

        /** 
         * Alert Properties for Time Compliance 
         * Commenting TIME COMPLIANCE for 1.1 
        protected static final int ALERT_PROPS_PER_NTP_SERVER = 4;
        public static final String ALERT_PROP_NTP_SERVER_REMOTE = "_RemoteSynced";
        public static final String ALERT_PROP_NTP_SERVER_SYNCED = "_Synced";
        public static final String ALERT_PROP_NTP_SERVER_RUNNING = "_Running";
        public static final String ALERT_PROP_NTP_SERVER_TRUSTED = "_Trusted";
        public static final String ALERT_PROP_MASTER_NODE_DATE_COMPLIANT = 
            "MasterNodeDateCompliant";
        private boolean isMasterNodeCompliant;
        private static final int MASTER_NODE_DATE_COMPLIANCE_PROP = 1;
        private AlertProperty[] alertProps = null;
         */ 
        protected NTPServer[] ntpServers = null;
        private boolean initNTP = false;
     

        /** Register the following events for each ntp server
          * - ntpserver remote/local 
          * - ntpserver synced/not-synced
          * - ntpserver ntp-running/ntp-not-running
          * - ntpserver offset > 5 seconds?     
          * hence ALERT_PROPS_PER_NTP_SERVER=4 
	  */
        Proxy() {
            logger.info("proxy init");
        }

        /**
           called from time managed service run() method
         */ 
        protected void initAlertProperties(String[] servers) {
            ntpServers = new NTPServer[servers.length];
            /**
             * commenting TIME COMPLIANCE for 1.1
             *    
            alertProps = new AlertProperty[ALERT_PROPS_PER_NTP_SERVER *
                                           servers.length + 
                                           MASTER_NODE_DATE_COMPLIANCE_PROP];
            int p = 0;                    
            for (int i=0; i<servers.length; i++) {
                ntpServers[i] = new NTPServer(servers[i]);
                alertProps[p++] =  
                    new AlertProperty("ntpServer" +i 
                                      +ALERT_PROP_NTP_SERVER_REMOTE,
                                      AlertType.BOOLEAN);

                alertProps[p++] =  
                    new AlertProperty("ntpServer" +i 
                                      +ALERT_PROP_NTP_SERVER_SYNCED,
                                      AlertType.BOOLEAN);
                
                alertProps[p++] =  
                    new AlertProperty("ntpServer" +i 
                                      +ALERT_PROP_NTP_SERVER_RUNNING,
                                      AlertType.BOOLEAN);
 
                alertProps[p++] =  
                    new AlertProperty("ntpServer" +i 
                                      +ALERT_PROP_NTP_SERVER_TRUSTED,
                                      AlertType.BOOLEAN);
            }
            alertProps[p] = new AlertProperty(
                                ALERT_PROP_MASTER_NODE_DATE_COMPLIANT,
                                AlertType.BOOLEAN);
            *
            */
            logger.info("done with initialization");
        }
     
        /**
         *  Api that returns hostname or IP address of a NTP server
         *  based on index.
         */
        public String getNtpServerFromIndex(int index) {
            if(index < 0 || index >= ntpServers.length) {
                return null;     
            }
            return ntpServers[index].getHost(); 
        }

        /**
         * returns number of ntp servers
         */
        public int numNtpServers() {
            return ntpServers.length;
        }

        /**
         * return hostnames or IP addresses of ntp servers 
         */
        public String[] getNtpServers() {
            String[] servers = new String[ntpServers.length];
            for(int i=0; i<servers.length; i++) {
                servers[i] = new String(ntpServers[i].getHost()); 
            }
            return servers;
        }

        /**
         * return hostname or IP address of a ntp server to which 
         * master node is synced to
         */
        /** 
         * commenting TIME COMPLIANCE for 1.1 
        public String getMasterSyncedToNtpServer() {
            String ntpCmd = Time.NTPQ + "-p "; 
            String line = null;
            BufferedReader in = null;
            Pattern ntpSyncPattern = Pattern.compile("^\\*(\\S+)\\s+.*");
            Matcher m;

            try {
                in = Exec.execRead(ntpCmd, logger);
                while((line = in.readLine()) != null) {
                    line.trim();
                    m = ntpSyncPattern.matcher(line);
                    if(m.matches()) {
                        logger.info("synced to external ntpserver: " 
                                    + m.group(1));
                        if(m.group(1).contains("LOCAL")) {
                            return new String("localhost");    
                        } else {
                            return new String(m.group(1));
                        }
                    } 
                }
            } catch(IOException e) {
                logger.info("");
            } finally {
                try {
                    if(in != null) {
                        in.close();
                    } 
                } catch(IOException e) {}
            }
            logger.info("master node is not synced");
            return null;
        }
        *
        */
    
        /**
         * return hostname or IP address of a ntp server to which 
         * switch is synced to
         */
        /** 
         * commenting TIME COMPLIANCE for 1.1 
        public String getSwitchSyncedToNtpServer() {
            String ntpCmd = Time.NTPQ + "-p " + Switch.SWITCH_FAILOVER_IP;
            String line = null;
            BufferedReader in = null;
            Pattern ntpSyncPattern = Pattern.compile("^\\*(\\S+)\\s+.*");
            Matcher m;

            try {
                in = Exec.execRead(ntpCmd, logger);
                while((line = in.readLine()) != null) {
                    line.trim();
                    m = ntpSyncPattern.matcher(line);
                    if(m.matches()) {
                        logger.info("external ntpserver: " +m.group(1));
                        return new String(m.group(1));
                    } 
                }
            } catch(IOException e) {
                logger.info("");
            } finally {
                try {
                    if(in != null) {
                        in.close();
                    } 
                } catch(IOException e) {}
            }
            logger.info("switch is not synced");
            return null;
        }
        *
        */

        /**
         * is switch time synced?
         */
        /** 
         * commenting TIME COMPLIANCE for 1.1 
        public boolean isSwitchSynced() {
            return getSwitchSyncedToNtpServer() != null ? true : false;
        }
        *
        */

        /**
         * is master node time synced?
         */
        /** 
         * commenting TIME COMPLIANCE for 1.1 
        public boolean isMasterNodeSynced() {
            return getMasterSyncedToNtpServer() != null ? true : false; 
        }

        public boolean isMasterNodeCompliant() {
            String masterSyncedToNtpServer = getMasterSyncedToNtpServer();

            String ntpserver = null;
            if(masterSyncedToNtpServer != null) {
                ntpserver = masterSyncedToNtpServer;     
            } else {
                return false;
            }

            String ntpCmd = Time.NTPDATE + "-u -d " + ntpserver;
            BufferedReader in = null;
            String line = null;
            float timeOffset = Time.MAX_COMPLIANT_TIME_OFFSET;
            try {
                ntpCmd = Time.NTPDATE + "-u -d " + ntpserver;
                in = Exec.execRead(ntpCmd, logger);
                Pattern p = Pattern.compile("offset(.*)");
                Matcher m;
                while((line = in.readLine()) != null) {
                    line.trim();
                    m = p.matcher(line);
                    if(m.matches()) { 
                        logger.info("time offset is " +m.group(1));
                        timeOffset = Float.valueOf(m.group(1)).floatValue();
                    }
                }
            } catch(IOException e) {
                logger.info("");     
            } finally {
                try {
                    if(in != null) {
                        in.close();
                    } 
                } catch(IOException e) {}
            }
            return (Math.abs(timeOffset) < Time.MAX_COMPLIANT_TIME_OFFSET) ? 
                   true : false;
        }
        *
        */

        public static TimeManagedService.Proxy getProxy(int node) {
            ManagedService.ProxyObject obj;
            obj = ServiceManager.proxyFor(node, "TimeManagedService");
            if (obj != null) {
                if (!obj.isReady()) {
                    return null;
                } else if (obj instanceof TimeManagedService.Proxy) {
                    return (TimeManagedService.Proxy) obj;
                }
            }
            return null;
        }

        public static TimeManagedService getApi(int nodeid) {
            ManagedService.ProxyObject obj = getProxy(nodeid);
            if (obj == null) {
                return null;
            }
            if (obj.getAPI() instanceof TimeManagedService) {
                return (TimeManagedService) obj.getAPI();
            }
            return null;
        }

        public static TimeManagedService.Proxy getProxy() {
            return getProxy(ServiceManager.LOCAL_NODE);
        }

        public static TimeManagedService getApi() {
            return getApi(ServiceManager.LOCAL_NODE);
        }

        /** 
         * Public Alert API's
         */ 
 
        /** 
         * commenting TIME COMPLIANCE for 1.1 
        public int getNbChildren() {
            if (alertProps != null) {
                return alertProps.length;
            } 
            return 0; 
        }
        
        public AlertProperty getPropertyChild(int index) 
            throws AlertException {
            try {
                return alertProps[index];  
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new AlertException("Index " +index +" Out of Bounds");
            }
        }

        public boolean getPropertyValueBoolean(String property) 
            throws AlertException {

            // Alert Property regarding ntp server status 
            // Pattern of the form 'ntpServer0_Running'
            Pattern p = Pattern.compile("(\\w+)(\\d+)(\\w+)");
            Matcher m = p.matcher(property);
            if(m.matches()) {
                if(m.group(1).equals("ntpServer")) {
                    int index = Integer.parseInt(m.group(2));
                    String status = m.group(3);
                    if(status.equals(ALERT_PROP_NTP_SERVER_REMOTE)) {
                        return ntpServers[index].getRemoteSyncStatus(); 
                    } else if(status.equals(ALERT_PROP_NTP_SERVER_SYNCED)) {
                        return ntpServers[index].getSyncStatus();
                    } else if(status.equals(ALERT_PROP_NTP_SERVER_RUNNING)) {
                        return ntpServers[index].getRunningStatus();
                    } else if(status.equals(ALERT_PROP_NTP_SERVER_TRUSTED)) {
                        return ntpServers[index].getTrustedStatus();
                    }
                }
            }

            // Alert Property regarding master node date compliance 
            if(property.equals(ALERT_PROP_MASTER_NODE_DATE_COMPLIANT)) {
                return getMasterNodeComplianceStatus();    
            }

            // No Alert Property Match 
            throw new AlertException("property " + property +
                                     " does not exist");
        }
        *
        */

        /** 
         * Proxy Private API's
         */

        /** 
         * commenting TIME COMPLIANCE feature for 1.1
        protected boolean getMasterNodeComplianceStatus() {
            return isMasterNodeCompliant;    
        }

        protected void setMasterNodeComplianceStatus(boolean s) {
            isMasterNodeCompliant = s;
        }
        *
        */   
    }
}
