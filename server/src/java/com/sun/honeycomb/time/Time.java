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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.time.TimeManagedService.Proxy;
import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.spreader.SwitchStatusManager;
import com.sun.honeycomb.spreader.SpreaderManagedService;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.platform.PlatformService;

/**
 * 
 * 
 */
public class Time implements TimeManagedService {

    private static final Logger logger = 
        Logger.getLogger(Time.class.getName());

    /** Path to unix commands */
    private static final String PKILL = "/usr/bin/pkill";
    private static final String PGREP = "/usr/bin/pgrep";
    private static final String PS = "/usr/bin/ps";

    /** Path to ntp commands */
    public static final String NTPDATE = 
        "/opt/honeycomb/sbin/ntpdate ";
    public static final String NTPQ =
        "/opt/honeycomb/sbin/ntpq ";
    private static final String NTPD = 
        "/opt/honeycomb/sbin/ntpd ";
   
    /** ntp options */ 
    private static final String NTP_CONF_FILE =
        "/etc/inet/external_ntp.conf";
    private static final String NTP_LOG_FILE =
        "/var/adm/external_ntp.log";
    private static final String NTP_PID_FILE =
        "/var/run/external_ntpd.pid";
    private static final String NTPD_OPTIONS = 
        "-c "  + NTP_CONF_FILE +
        " -l " + NTP_LOG_FILE +
        " -p " + NTP_PID_FILE + 
        " -x ";
  
    /** svcs commands */ 
    private static final String svcsStatusCmd = "/usr/bin/svcs ntpd";
    private static final String svcsEnableCmd = "/usr/sbin/svcadm enable ntpd";
    private static final String svcsDisableCmd = "/usr/sbin/svcadm disable ntpd";

    /** Honeycomb NTP sevice 'svcs ntpd' boolean */
    private boolean isNtpServiceRunning;
 
    /** Max time offset tolerated before honeycomb compliance is compromised */
    public static final float MAX_COMPLIANT_TIME_OFFSET = 60; // 1 Minute 

    /** For honeycomb compliance we need 3 ntp servers for compliance */
    public static final int MIN_NTP_SERVERS_REQD_FOR_COMPLIANCE = 3; 
        
    /** monitoring ntp server status codes and script name */ 
    public static final String SCRIPT_NAME = 
        "/opt/honeycomb/bin/monitor_ntpserver.sh "; 
    public static final int SERVER_OK = 0;
    public static final int SCRIPT_USAGE = 101;
    public static final int SERVER_LOCALLY_SYNCED = 102;
    public static final int SERVER_NOT_RUNNING = 103;
    public static final int SERVER_NOT_SYNCED = 104;
    public static final int SERVER_NOT_TRUSTED = 105;
    public static final int SWITCH_NOT_PINGABLE = 106;
    public static final int SWITCH_UPLINK_NOT_CONNECTED = 107;
    public static final int SERVER_NOT_VERIFIED = 108;
    public static final int MIN_SERVERS_NOT_MET = 109;
  
    /** A ntp server with offset more than 5 seconds, mark it unreliable */ 
    private static final int MAX_SERVER_TIME_OFFSET = 5000; // 5 Seconds  
 
    private static final int DELAY = 60000 * 5; // 5 Minutes

    /** NTP Source Port */
    public static final int NTP_PORT = 123;
 
    volatile boolean keeprunning;
    private ClusterProperties _props = null;
    private String[] servers; 
    private Proxy proxy;
    private NodeMgrService.Proxy nodeMgrProxy;
    private boolean switchRulesOK;

    private static final String PROPERTY_CELL_NUM_NODES = 
        "honeycomb.cell.num_nodes";
    private static final String PROPERTY_CELL_NTP =
        "honeycomb.cell.ntp";

    /**
     * monitors ntpd and starts it, if the daemon got 
     * killed 
     */
    private void monitorNtpd() {
        // ntp is not running
        if(!isNtpRunning()) {
            /** Delete Pid File, as it contains the old NTP Pid */
            deleteNtpPidFile();
            startNtpd();
        }
    }

    /**
     * 
    private void monitorCompliance() {
        // Is date on the master node within tolerable limits of 
        //  honeycomb compliance?
        // 
        if(proxy.isMasterNodeCompliant()) {
            proxy.setMasterNodeComplianceStatus(true);
            logger.info("Master node is compliant"); 
        } else {
            proxy.setMasterNodeComplianceStatus(false);
            logger.info("Master node is not compliant"); 
        }
    }
    *
    */

    /** 
     * commenting TIME COMPLIANCE for 1.1
    private void monitorNtpServers() {
        String ntpCmd;
        int retval = SERVER_NOT_VERIFIED;

        // Want to Make sure that we have a Proxy Object
        if (proxy == null) {
            getProxy();
        }
 
        for(int i=0; i<servers.length; i++) {
            String ntpServer = proxy.ntpServers[i].getHost();
            ntpCmd = SCRIPT_NAME +ntpServer +" " +MAX_SERVER_TIME_OFFSET;
            try {
                retval = Exec.exec(ntpCmd, logger);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Unable to execute " +SCRIPT_NAME +retval, e);    
            }

            switch(retval) {
            case SERVER_OK:
            logger.info("ntp server " +ntpServer +" Healthy");
            proxy.ntpServers[i].setRemoteSyncStatus(true); 
            proxy.ntpServers[i].setRunningStatus(true);
            proxy.ntpServers[i].setSyncStatus(true); 
            proxy.ntpServers[i].setTrustedStatus(true); 
            break;

            case SWITCH_NOT_PINGABLE:
            logger.severe("switch is not pingable");
            // throw new something something here
            break;

            case SWITCH_UPLINK_NOT_CONNECTED:
            logger.warning("switch uplink not connected");
            break;

            case SERVER_NOT_RUNNING:
            logger.info("host " + ntpServer + " is not running NTP");
            proxy.ntpServers[i].setRunningStatus(false);
            proxy.ntpServers[i].setSyncStatus(false); 
            proxy.ntpServers[i].setTrustedStatus(false); 
            break;

            case SERVER_NOT_SYNCED:
            logger.info("host " + ntpServer + " is not time synced");
            proxy.ntpServers[i].setRunningStatus(true);
            proxy.ntpServers[i].setSyncStatus(false); 
            proxy.ntpServers[i].setTrustedStatus(false); 
            break;

            case SERVER_LOCALLY_SYNCED: 
            logger.info("host " + ntpServer + 
                        " is time synced to its own hardware clock");
            proxy.ntpServers[i].setSyncStatus(true); 
            proxy.ntpServers[i].setRunningStatus(true);
            proxy.ntpServers[i].setRemoteSyncStatus(false); 
            proxy.ntpServers[i].setTrustedStatus(false); 
            break;
     
            case SERVER_NOT_TRUSTED:
            logger.info("host " + ntpServer + 
                        " is not trusted as time offset is greater than 5 seconds");
            proxy.ntpServers[i].setRemoteSyncStatus(true); 
            proxy.ntpServers[i].setRunningStatus(true);
            proxy.ntpServers[i].setSyncStatus(true); 
            proxy.ntpServers[i].setTrustedStatus(false); 
            break;

            case SERVER_NOT_VERIFIED:
            logger.info("host " + ntpServer + " is not verified");
            break;
 
            default: 
            logger.log(Level.SEVERE, "Unable to execute " +SCRIPT_NAME +retval);    
            break; 
            }
        }
    }
    *    
    */

    // default constructor called by CM
    public Time() {
        keeprunning = true;
        synchronized(this) { 
            if (_props == null) {
                _props = ClusterProperties.getInstance();
            }
            // read ntp config 
            String ntp = _props.getProperty(PROPERTY_CELL_NTP);
            logger.info("ntp config " +ntp);
            StringTokenizer st = new StringTokenizer(ntp,",");  
            if(st.countTokens() <= 1) {
                servers = new String[1];
                servers[0] = new String(st.nextToken());
                logger.info("servers: " +servers[0]);
            } else {
                servers = new String[st.countTokens()];
                for(int i=0; i<servers.length; i++) {
                    servers[i] = new String(st.nextToken());
                    logger.info("servers: " +servers[i]);
                }
            }
        }
       
        // Get Node Mgr Proxy
        getNodeMgrProxy();

        // 
        switchRulesOK = false;

        // Honeycomb NTP 4.2.0 ntpd service is running
        // when TimeKeeper starts
        isNtpServiceRunning = true;

        logger.info("going to ready state");
    }

    public void shutdown() {
        keeprunning = false;

        // stop external ntp daemon
        stopNtpd();
        if(isNtpRunning()) {
            logger.log(Level.SEVERE, "unable to kill external ntp daemon");
        }

        // Enable Honeycomb NTP 4.2.0 ntpd
        enableNtpService();

        logger.info("Time shutdowns");
    }

    // return the current proxy for this service
    public TimeManagedService.ProxyObject getProxy() {
        try {
            if (proxy == null) {
                proxy = new TimeManagedService.Proxy();
            }
        } catch (Exception e) { }
        return proxy; 
    }

    public void syncRun() {
        logger.info("going to running state");
    }

    // service entry point
    public void run() {

        if (proxy != null) {
            proxy.initAlertProperties(servers);
        }

        // 
        // in case MASTER-SERVERS jvm restarts, external NTP daemon is not killed
        // and hence explicitly kill NTP daemon before starting a new one.
        //
        stopNtpd();
        if(isNtpRunning()) {
            logger.log(Level.SEVERE, "unable to kill external ntp daemon");
            throw new RuntimeException("unable to kill external ntp daemon");
        }

        // 
        // Start NTP daemon 
        // 
        startNtpd();
        // Main Loop
        while (keeprunning) {
            try {
                getProxy();
                ServiceManager.publish(this);
                monitorNtpd();
                /**
                 * comment TIME COMPLIANCE for 1.1
                monitorNtpServers();
                monitorCompliance();
                * 
                */  
                Thread.sleep(DELAY);
            } catch (InterruptedException e) { }
        }
    }

    private NodeMgrService.Proxy getNodeMgrProxy() {
        if(nodeMgrProxy == null) {
            nodeMgrProxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);  
        }
        return nodeMgrProxy;
    }

    private String getDataVIP(){
       Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE,
                                            "PlatformService");
        if (obj == null || !(obj instanceof PlatformService.Proxy))
            return null;

        PlatformService.Proxy proxy = (PlatformService.Proxy) obj;
        return proxy.getDataVIPaddress();
    }

    private String getMyAddress() {
        return nodeMgrProxy.getHostname(); 
    }

    private int getMasterPort() {
        return nodeMgrProxy.nodeId() - 100;  
    }

    private int getNumNodes() {
        String sNumNodes = _props.getProperty(PROPERTY_CELL_NUM_NODES);
        if (sNumNodes == null) {
                throw new InternalException("cannot retrieve value for " +
                                            "property " + 
                                            PROPERTY_CELL_NUM_NODES);
        }
        try {
            return Integer.parseInt(sNumNodes);
        } catch (NumberFormatException  nfe) {
            throw new InternalException("invalid value for property " +
                                        PROPERTY_CELL_NUM_NODES);
        }
    }

    /**
     * Enable 'Honeycomb NTP 4.2.0 ntpd' Service 
     */
    private void enableNtpService() {
        BufferedReader in = null;

        String line = null; 
        int retcode = 1; 
        try {
            retcode = Exec.exec(svcsEnableCmd, logger);
            if(retcode != 0) {
                logger.warning("return code for " +svcsEnableCmd);
            }
            in = Exec.execRead(svcsStatusCmd, logger);
            while((line = in.readLine()) != null) {
                if((line.indexOf("enable") > 0)) {
                    isNtpServiceRunning = true; 
                    break;
                } 
            }
        } catch(IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        } finally {   
            try {
                if(in != null) {
                    in.close();
                }
            } catch(IOException e) {}
        }
    }

    /**
     * Disable 'Honeycomb NTP 4.2.0 ntpd' Service 
     */
    private void disableNtpService() {
        BufferedReader in = null;

        String line = null; 
        int retcode = 1; 
        try {
            retcode = Exec.exec(svcsDisableCmd, logger);
            if(retcode != 0) {
                logger.warning("return code for " +svcsDisableCmd);
            }
            in = Exec.execRead(svcsStatusCmd, logger);
            while((line = in.readLine()) != null) {
                if((line.indexOf("disable") > 0)) {
                    isNtpServiceRunning = false; 
                    break;
                } 
            }
        } catch(IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        } finally {   
            try {
                if(in != null) {
                    in.close();
                }
            } catch(IOException e) {}
        }
    }

    /**
     * Starts ntp daemon, which syncs to the external ntp server
     */ 
    private void startNtpd() {
        // 
        // Before starting ntp daemons, disable
        // Honeycomb ntp service manifest
        // why?? 
        // When 2 ntp daemons are running at the same time
        // ntpq -p reports status of the daemon started first
        //
        if(isNtpServiceRunning) {
            disableNtpService();
        } 
 
        String ntpCmd = NTPD + NTPD_OPTIONS;
        String line = null;
        int retcode = 1;
        try {
            retcode = Exec.exec(ntpCmd, logger);
            if(retcode != 0) {
                logger.warning("retcode  " + retcode + " for " +ntpCmd);
            }

        } catch(IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e); 
            throw new RuntimeException(e.getMessage());
        } 
    }

    /**
     * Checks to see if external ntp daemon is running or not 
     * if running, return true or return false 
     */
    private boolean isNtpRunning() {
        String pgrepCmd = PGREP + " -f external_ntp.conf";
        BufferedReader grepIn = null;
        String line = null;

        try {
            grepIn = Exec.execRead(pgrepCmd);
            if ((line = grepIn.readLine()) != null) {
                return true; 
            } else {
                logger.log(Level.INFO, "external ntp daemon is not running");
            }
        } catch(IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e); 
            throw new RuntimeException(e.getMessage());
        } finally {
            if (grepIn != null) {
                try {
                    grepIn.close();
                } catch(IOException e) { }
            }
        }
        return false; 
    }

    /**
     * stop ntp daemon, first with SIGTERM and finally with SIGKILL
     */ 
    private void stopNtpd() {
        if (isNtpRunning()) {
            sendKillSignalToNtp("TERM");

            // Is ntp still running?
            if (isNtpRunning()) {
                // this time kill ntp with SIGKILL
                sendKillSignalToNtp("KILL");

                if (!isNtpRunning()) {
                    deleteNtpPidFile();
                }
            } else {
                deleteNtpPidFile();
            }
        }
    }

    /**
     * delete ntp pid file
     */ 
    private void deleteNtpPidFile() {
        // 
        // Delete external pid file
        //
        File pidFile = new File(NTP_PID_FILE);
        if(pidFile.exists()) {
            logger.info("deleting file " +NTP_PID_FILE);
            pidFile.delete();
        }
    }

    /** 
     * kill external ntp daemon with the specified signal
     */
    private void sendKillSignalToNtp(String signal) {
        String pkillCmd = PKILL + " -" + signal + " -f external_ntp.conf";
        int retcode = 1;
        String line = null;
        BufferedReader grepIn = null;

        try {

            //
            // kill ntp daemon
            //
            logger.log(Level.INFO, "sending kill signal " + signal + " to external ntp daemon");   
            retcode = Exec.exec(pkillCmd, logger);
            if(retcode != 0) {
                logger.log(Level.INFO, "external ntp daemon is not running");
            } 
            
        } catch(IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e); 
            throw new RuntimeException(e.getMessage());
        }
    }

    /*
     * remote API exported by Time managed service
     */
}
