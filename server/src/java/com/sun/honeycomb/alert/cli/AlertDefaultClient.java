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


package com.sun.honeycomb.alert.cli;

import java.util.logging.Level;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.LinkedList;
import java.util.Observer;
import java.util.Observable;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.StringTokenizer;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.NoSuchElementException;

import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertApiImpl;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.time.TimeManagedService;

abstract public class AlertDefaultClient
    implements AlertDefaultClientService, Observer, PropertyChangeListener
{
    private static transient final Logger logger = 
        Logger.getLogger(AlertDefaultClient.class.getName());

    // Seconds between client's check for new notifications
    protected final static int CHECK_NOTIF_PERIOD = 1;
    
    /*
     * Interval for checking quorum state change
     */
    protected static final long QUORUM_CHECK_INTERVAL = 60000;    // 1mn

    // Runtime
    protected boolean    keepRunning;
    protected Thread     thr;

    // Alert API
    protected AlertApi api;

    // Current list of mails that need to be sent out.
    protected LinkedList notifications;

    // Registration ID
    protected Object[] disksNotif;

    // Pattern matching for the various registration
    protected Pattern blockStorePattern;
    protected Pattern activeSwitchPattern;
    protected Pattern backupSwitchAlivePattern;
    protected Pattern spAlivePattern;
    protected Pattern diskStatusPattern;
    /** 
     * commenting TIME COMPLIANCE code for 1.1 - sameer mehta
    protected Pattern ntpServerPattern;
    protected Pattern masterNodeCompliancePattern;
     */
    protected Pattern currentMapPattern;

    // Config
    protected ClusterProperties config;
    protected boolean           registeredDiskCapacity;
    protected boolean           registeredForSpreaderAlerts;
    protected boolean           registeredCliAlerts;
    protected boolean           registeredNtpServers;
    private String              maxDiskCapacity;
    private int                 disksPerNode;
    // commenting TIME COMPLIANCE code for 1.1
    // private String[]            ntpServers;

    private boolean             registeredCmmNotification;
    private int                 numNodes;
    protected int               masterNodeId;
    protected String            svcName;

    protected HardwareProfile     profile = null;

    //
    // Managed Service interface.
    // 
    public AlertDefaultClient(String name) {
        notifications   = new LinkedList();
        svcName         = name;
        keepRunning     = true;
        thr             = Thread.currentThread();

        disksNotif      = null;


        blockStorePattern = Pattern.compile("root\\.(\\d+)\\.Layout\\." +
                                       "blockStores");
	activeSwitchPattern = Pattern.compile("root\\.(\\d+)\\." +
					      "SwitchSpreader\\." +
					      "activeSwitch");
        backupSwitchAlivePattern = Pattern.compile("root\\.(\\d+)\\." +
					      "SwitchSpreader\\." +
					      "backupSwitchStatus");
        spAlivePattern = Pattern.compile("root\\.(\\d+)\\." +
					      "SwitchSpreader\\." +
					      "spStatus");
	diskStatusPattern = Pattern.compile("root\\.(\\d+)\\." +
					    "DiskMonitor\\." +
					    "disk(\\d+)\\.status");
        //
        // Generates too much junk; see AlertMail.java
        //
        //        currentMapPattern = Pattern.compile("root\\.\\d+\\." +
        //                                            "Layout\\.diskmap\\." +
        //                                            "currentMap");

        /**
         * commenting TIME COMPLIANCE code for 1.1 
        ntpServerPattern = Pattern.compile("root\\.(\\d+)\\." +
                                            "TimeKeeper\\." +
                                            "ntpServer(\\d+)" +
                                            "(\\w+)");
                                             
        masterNodeCompliancePattern = Pattern.compile("root\\.(\\d+)\\." +
                                                      "TimeKeeper\\." +
                                                      "(\\w+)");
         * 
         */

        config = ClusterProperties.getInstance();
        config.addPropertyListener(this);
        MultiCellLib.getInstance().addPropertyListener(this);
        profile = HardwareProfile.getProfile();


        registeredCmmNotification = false;

        maxDiskCapacity = null;
        registeredForSpreaderAlerts=false;
        registeredDiskCapacity = false;
        registeredCliAlerts = false;
        registeredNtpServers = false;
        disksPerNode    = 0;

        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            throw new InternalException("cannot fetch NodeManager Proxy" +
                                        " for local node");
        }
        NodeMgrService.Proxy nodeMgr =  (NodeMgrService.Proxy) obj;

        masterNodeId = nodeMgr.nodeId();
    }

    public void shutdown () {
        keepRunning = false;
        thr.interrupt();
        boolean stopped = false;
        while (!stopped) {
            try {
                thr.join();
                stopped = true;
            } catch (InterruptedException ignored) {
                
            }
        }
        // make sure we log/e-mail whatever is left before we shutdown.
        purgeQueue(true);
        if (logger.isLoggable(Level.INFO)) {
            logger.info(svcName + " now STOPPED");
        }        
    }

    public ManagedService.ProxyObject getProxy () {
        return new Proxy ();
    }


    //
    // Returns the interval of time we are sleeping
    // before checking the queue in ms
    //
    static public int getAlertNotificationPeriod() {
        return (CHECK_NOTIF_PERIOD * 1000);
    }

    abstract public void run();


    //
    // Received notification from Correlation Engine.
    //
    public void update(Observable obj, Object arg) {

        Notification notif =null;
        // CMM notification
        if (arg instanceof NodeChange) {

            notif  = getNotification((NodeChange) arg);

        // Alert Framework notification    
        } else if (arg instanceof AlertApi.AlertObject) {
            notif = getNotification((AlertApi.AlertObject) arg);
            if (logger.isLoggable(Level.FINE)) {  
                try {
                    AlertApi.AlertObject al = (AlertApi.AlertObject) arg;
                    String value = (al.getPropertyValue() == null) ?
                        "undef" : al.getPropertyValue().toString();
                    logger.log(Level.FINE, "received notification for " +
                                al.getPropertyName() + ", value = " +
                                value);
                } catch (Exception e) {
                    logger.severe("exception " + e);
                }
            }

        // error
        } else if (arg instanceof String) {
            notif = getNotification((String)arg);
        } else {
            logger.severe("Invalid notification:" + obj.toString());
        }
        if (notif != null) {
            synchronized(notifications) {

                notifications.add(notif);
            }
        }
    }


    /**********************************************************************
     * Return a list of node properties to register for.
     **/
    protected void getNodePropertiesToRegister(HashMap properties) {
        properties.put("root.*.Layout.diskmap.currentMap", Boolean.FALSE);
        properties.put("root." + masterNodeId +".SwitchSpreader.activeSwitch", Boolean.FALSE);       
        properties.put("root." + masterNodeId +".SwitchSpreader.backupSwitchStatus", Boolean.FALSE);
        properties.put("root." + masterNodeId +".SwitchSpreader.spStatus", Boolean.FALSE);       
        properties.put("root." + masterNodeId +".MgmtServer.cliAlerts", Boolean.FALSE);
	properties.put("root." + masterNodeId +".Layout.blockStores", Boolean.FALSE);
    }

    /**********************************************************************
     * Return a list of disk properties to register for.
     **/
    protected void getDiskPropertiesToRegister(HashMap properties) {

        int disksPerNode = profile.getNumDisks();
        for (int i = 0; i < disksPerNode; i++) {
            properties.put("root.*.DiskMonitor.disk" + i + ".status", Boolean.FALSE);
        }


    }


   /**
    * commenting TIME COMPLIANCE for 1.1
    protected Map getCompliancePropertiesToRegister() {
        HashMap properties = new HashMap();
        String prop;
      
        // 
        // Compliance Properties relating to ntp servers 
        // Read ntp config
        //
        readNtpServers();
 
        for(int i=0; i<ntpServers.length; i++) {
            prop = "root." + masterNodeId + ".TimeKeeper.ntpServer" +i 
                   +TimeManagedService.Proxy.ALERT_PROP_NTP_SERVER_REMOTE;
            properties.put(prop, Boolean.FALSE);
            prop = "root." + masterNodeId + ".TimeKeeper.ntpServer" +i 
                   +TimeManagedService.Proxy.ALERT_PROP_NTP_SERVER_SYNCED;
            properties.put(prop, Boolean.FALSE);
            prop = "root." + masterNodeId + ".TimeKeeper.ntpServer" +i 
                   +TimeManagedService.Proxy.ALERT_PROP_NTP_SERVER_RUNNING;
            properties.put(prop, Boolean.FALSE);
            prop = "root." + masterNodeId + ".TimeKeeper.ntpServer" +i
                   +TimeManagedService.Proxy.ALERT_PROP_NTP_SERVER_TRUSTED;
            properties.put(prop, Boolean.FALSE);
        }

        //
        // Compliance Property relating to Master node
        // 
        prop = "root." + masterNodeId + ".TimeKeeper." +
                TimeManagedService.Proxy.
                    ALERT_PROP_MASTER_NODE_DATE_COMPLIANT;  
        properties.put(prop,Boolean.FALSE);
 
        return properties; 
    }
    * 
    */
     
    /**********************************************************************
     * Register for properties we are interested in. Clears out the
     * properties map if all properties are registered.
     **/
    protected void registerProperties(Map properties) {

        if (properties.isEmpty()) {
            return;
        }
        synchronized(AlertDefaultClient.class) {
            AlertApi.AlertEvent event
                = new AlertApi.AlertEvent(AlertApi.AlertEvent.EVT_CHANGE);

            for (Iterator it = properties.keySet().iterator(); it.hasNext();) {
                String property = (String) it.next();
                if (((Boolean) properties.get(property)) == Boolean.FALSE) {
                    try {
                        if (logger.isLoggable(Level.FINE)) {  
                            logger.fine("registered property " + property);
                        }
                        api.register(svcName,
                                     (String) property, this, event);

                        properties.put(property,Boolean.TRUE);
                    } catch (AlertException ae) {
                        //
                        // Logged as fine because otherwise cluster bringup
                        // hits thousands of these..
                        //
                        logger.fine("register() " + property +
                                    " (service not started?): "
                                    + ae);
                    }
                }
            }

            // Clear out the map if all properties are registered
            boolean allRegistered = true;
            for (Iterator it = properties.keySet().iterator(); it.hasNext(); ) {
                String property = (String) it.next();
                if (((Boolean) properties.get(property)) == Boolean.FALSE) {
                    allRegistered = false;
                    break;
                }
            }
            if (allRegistered) {

                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Clearing out properties");
                }
                properties.clear();
            }
        }
    }

    //
    // CMM notification-- directly rely on Managed Service API.
    //
    protected void registerForCMMNotifications() {
        AlertApi.AlertEvent ev =
            new AlertApi.AlertEvent(AlertApi.AlertEvent.EVT_CMM);
        try {
            Object cmmNotif = api.register(svcName, null, this, ev);
            registeredCmmNotification = true;
        } catch (AlertException ae) {
            logger.severe("cannot register to CMM notifications");
        }
    }


    //
    // Register for max disk capacity
    //
    // Note that diskMonitor may not be initialized when the AlertClient/
    // AlertMail make this registration; If this is the case, the clients
    // should retry. Alert service make the registration idempotent so even
    // if we fail for half of the objects, it should not matter...
    //
    protected void registerForDiskCapacity() {
        synchronized(AlertDefaultClient.class) {

            Double threshold = new Double(maxDiskCapacity);
            AlertApi.AlertEvent ev =
                new AlertApi.AlertEvent(AlertApi.AlertEvent.EVT_THRESHOLD_MAX,
                                        threshold);
            
            disksNotif = new Object[disksPerNode];
            for (int i = 0; i < disksPerNode; i++) {
                String rule = "root.*.DiskMonitor.disk" + i + ".percentUsed";
                try {
                    disksNotif[i] = api.register(svcName, rule, this, ev);
                } catch (AlertException ae) {
                    return;
                }
            }
            if (logger.isLoggable(Level.INFO)) {
                logger.info("successfully registered for disk capacity");
            }
            registeredDiskCapacity = true;
        }
    }

    protected String getCurrentMembership() {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            throw new InternalException("cannot fetch NodeManager Proxy" +
                                        " for local node");
        }
        NodeMgrService.Proxy nodeMgr =  (NodeMgrService.Proxy) obj;

        StringBuffer buf = new StringBuffer();
        Node[] nodes = nodeMgr.getNodes();
        boolean first = true;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isAlive()) {
                if (!first) {
                    buf.append(", ");  
                }
                buf.append(nodes[i].nodeId());
                first = false;
            }
        }
        return buf.toString();
    }

    protected Notification getNextNotification() {
        synchronized(notifications) {
            while (!notifications.isEmpty()) {
                try {
                    return (Notification) notifications.removeFirst();
                } catch (NoSuchElementException el) {
                    return null;
                }
            }
            return null;
        }
    }

    private void unregisterForDiskCapacity() {
        synchronized(AlertDefaultClient.class) {
            for (int i = 0; i < disksNotif.length; i++) {
                try {
                    api.unregister(this, disksNotif[i]);
                } catch (AlertException ae) {
                    logger.warning("unregistration for disk capacity:" +
                                   ae);
                }
            }
            registeredDiskCapacity = false;
        }
    }

    //
    // Config/Update
    //
    protected void readConfig() {
        readPropMaxDisk();
        readNumNodes();
        disksPerNode = profile.getNumDisks();
    }

    private void readPropMaxDisk() {
        synchronized(AlertDefaultClient.class) {
            maxDiskCapacity = config.getProperty(
		ConfigPropertyNames.PROP_DISK_USAGE_CAP);
        }
    }

    /** 
     * commenting TIME COMPLIANCE feature for 1.1
    private void readNtpServers() {
        synchronized(AlertDefaultClient.class) {
            String ntp = config.getProperty(ConfigPropertyNames.PROP_NTP_SERVER);
            logger.info("ntp config " +ntp);
            StringTokenizer st = new StringTokenizer(ntp,",");  
            if(st.countTokens() <= 1) {
                ntpServers = new String[1];
                ntpServers[0] = new String(st.nextToken());
                logger.info("ntpServers: " +ntpServers[0]);
            } else {
                ntpServers = new String[st.countTokens()];
                for(int i=0; i<ntpServers.length; i++) {
                    ntpServers[i] = new String(st.nextToken());
                    logger.info("ntpServers: " +ntpServers[i]);
                }
            }
        }
    }
    *
    */

    private void readNumNodes() {
        synchronized(AlertDefaultClient.class) {
            String sNumNodes = config.getProperty(
		ConfigPropertyNames.PROP_NUM_NODES);
            if (sNumNodes == null) {
                throw new InternalException("cannot retrieve value for " +
		    "property " + 
                    ConfigPropertyNames.PROP_NUM_NODES);
            }
            try {
                numNodes = Integer.parseInt(sNumNodes);
            } catch (NumberFormatException  nfe) {
                throw new InternalException("invalid value for property " +
		    ConfigPropertyNames.PROP_NUM_NODES);
            }
        }
    }
    
    public void propertyChange(PropertyChangeEvent event) {
        String prop = event.getPropertyName();

        /**
	 * Comment out since we aren't using the Disk Capacity Stuff currently.
	 * Capacity full notification is currently being done via the blockStores
	 * alert.
	 *
        if (prop.equals(ConfigPropertyNames.PROP_DISK_USAGE_CAP)) {
            readPropMaxDisk();
            if (registeredDiskCapacity) {
                unregisterForDiskCapacity();
                registerForDiskCapacity();
            }
        } else if (prop.equals(ConfigPropertyNames.PROP_NUM_NODES)) {
            readNumNodes();
            if (registeredDiskCapacity) {
                unregisterForDiskCapacity();
                registerForDiskCapacity();
            }

        } else {
            // Ignore the rest...
            return;
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.info("config change for prop" + prop);
        }
	*
        */
    }


    //
    // Notifications (CMM, Alert Framework)
    //
    abstract protected Notification getNotification(AlertApi.AlertObject obj);
    abstract protected Notification getNotification(NodeChange notif);
    abstract protected Notification getNotification(String str);
    abstract protected void purgeQueue(boolean shutdown);

    protected class Notification {
        private String msg;
        public Notification(String s) {
            msg = s;
        }
        String getMessage() {
            return msg;
        }
    }
}
