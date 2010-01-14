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
import java.text.MessageFormat;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.LinkedList;
import java.util.GregorianCalendar;
import java.util.Observer;
import java.util.Observable;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.regex.Pattern;

import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.mail.SMTPClient;
import com.sun.honeycomb.common.mail.MailMessage;
import com.sun.honeycomb.common.mail.EmailAddress;
import com.sun.honeycomb.common.mail.MailException;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertApiImpl;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.common.AdminResourcesConstants;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.time.Time;
import com.sun.honeycomb.time.TimeManagedService;
import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.SysLog;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService.Proxy;


public class AlertMail extends AlertDefaultClient
{
    private static transient final Logger logger = 
        Logger.getLogger(AlertMail.class.getName());

    private final static String PATTERN_PROP_SPLIT =
        "\\s*,\\s*";

    //
    // Alert mail messages
    //
    private static final String ALERT_MAIL_CMM_CUR_MEMBERSHIP =
        "alertMail.cmm.curmembership";
    private static final String ALERT_MAIL_CMM_MEMBERSHIP =
        "alertMail.cmm.membership";
    private static final String ALERT_MAIL_DISK_CAPACITY =
        "alertMail.diskMonitor.disk.capacity";
    
    private static final String ALERT_MAIL_STORES_BLOCKED =
        "alertMail.Layout.storesBlocked";
    
    private static final String ALERT_MAIL_STORES_UNBLOCKED =
        "alertMail.Layout.storesUnblocked";
    
    //
    // Too verbose
    //
    //     private  static final String ALERT_MAIL_CURRENT_MAP =
    //         "alert.layout.currentMap";
    private  static final String ALERT_MAIL_ADMIN_VIP =
        "alertMail.adminVip";
    private  static final String ALERT_MAIL_DATA_VIP =
        "alertMail.dataVip";
    private  static final String ALERT_MAIL_SP_IP =
        "alertMail.spIp";
    private  static final String ALERT_MAIL_GATEWAY_IP =
        "alertMail.gatewayIp";
    private  static final String ALERT_MAIL_SUBNET =
        "alertMail.subnet";
    private  static final String ALERT_MAIL_SMTP_SERVER =
        "alertMail.smtpIp";
    private  static final String ALERT_MAIL_SMTP_PORT =
        "alertMail.smtpPort";
    private  static final String ALERT_MAIL_SWITCH_ID =
        "alertMail.switch.switchID";
    private  static final String ALERT_MAIL_BACKUP_SWITCH_STATUS =
        "alertMail.switch.backupSwitchStatus";
    private  static final String ALERT_MAIL_SP_STATUS =
        "alertMail.spStatus";
    private  static final String ALERT_MAIL_DISK_STATUS =
        "alertMail.diskMonitor.disk.status";
    private static final String ALERT_MAIL_NTP_SERVER_REMOTE =
        "alertMail.ntpServer.remoteStatus";
    private static final String ALERT_MAIL_NTP_SERVER_SYNCED =
        "alertMail.ntpServer.syncedStatus";
    private static final String ALERT_MAIL_NTP_SERVER_RUNNING =
        "alertMail.ntpServer.runningStatus";
    private static final String ALERT_MAIL_NTP_SERVER_TRUSTED =
        "alertMail.ntpServer.trustedStatus";
    private static final String ALERT_MAIL_MASTER_NODE_COMPLIANT = 
        "alertMail.masterNode.complianceStatus";
   
    static private ResourceBundle mailBundle;
    static private boolean saveQuorum = false;
    static private boolean hadQuorum = false;
    
    static {
        try {
	    mailBundle = ResourceBundle.getBundle("AdminResources",
                                                  Locale.getDefault());
        } catch (MissingResourceException ex){
            throw new InternalException("cannot retrieve property" +
                                        " bundle AlertMail");
        }
    }

    // Config stuff
    private String       smtpHost;
    private int          smtpPort;
    private EmailAddress addrSender;
    private ArrayList    addrTo;
    private ArrayList    addrCC;

    private EmailAddress phoneHomeTo;
    private long         phoneHomeFreq;
    
    /**
     * Boolean flag used to indicate that we got a
     * stores block true alert fromt the alert tree. 
     */
    private boolean	 storesBlockTrueAlertReceived = false;

    // Registered properties
    private HashMap registerProperties;

    // commenting TIME COMPLIANCE for 1.1
    // private Map complianceProperties;

    /*
     * Managed Service interface.
     */ 
    public AlertMail() {

        super("AlertMail");

        smtpHost        = null;
        smtpPort        = -1;
        addrTo          = null;
        addrCC          = null;
        addrSender      = null;
        phoneHomeTo     = null;
        phoneHomeFreq   = -1;
        registerProperties=new HashMap();
        getNodePropertiesToRegister(registerProperties);
        getDiskPropertiesToRegister(registerProperties);
    }


    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
    }


    public void run() {
        try {
            api = AlertApiImpl.getAlertApi();
        } catch (AlertException ae) {
            logger.severe("AlertMail exit..." + ae);
            keepRunning = false;
        }

        if (keepRunning) {
            readConfig();
          
            // 
            // Registration for all the events we are interested in.
            //
            registerForCMMNotifications();
        
            /**
             * commenting TIME COMPLIANCE code for 1.1 
            //
            // get Properties for ntp server events
            //  
            complianceProperties = getCompliancePropertiesToRegister();
             */

            Object[] args = {getCurrentMembership()};
            String bundleStr = getPropertyString(ALERT_MAIL_CMM_CUR_MEMBERSHIP);
            String msg = MessageFormat.format(bundleStr, args);

            MailNotification firstMembership = new MailNotification(msg);


            try {
                // Send first mail to notify about new membership
                email(firstMembership);
            } catch (MailException ml) {
                logger.severe("Cannot send the mail" + 
                              ml + 
                              "\nWould have sent:" + 
                              firstMembership.getMessage());
            }
        }
        
        long checkQuorumTime = System.currentTimeMillis();

        while (keepRunning) {
            purgeQueue(false);
            // Keep trying for unregistered properties
            synchronized(AlertDefaultClient.class) {
                HashMap allProps=new HashMap();

                registerProperties(registerProperties);
                if (!registeredDiskCapacity) {
                    registerForDiskCapacity();
                } 
                /**
                 * commenting TIME COMPLIANCE for 1.1
                registerProperties(complianceProperties);
                */ 
            }

            // phone home if we need to
            try {
                phoneHome();
            } catch (ServerConfigException e) {


                logger.log(Level.SEVERE, "Unhandled server update failure in alertMail", e); 
            }

            // check quorum if time
            try {
                long currentTime = System.currentTimeMillis();
                if (checkQuorumTime - currentTime <= 0) {
                    checkQuorumTime = currentTime + 
                            AlertDefaultClient.QUORUM_CHECK_INTERVAL;
                    checkQuorum();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, 
                        "Unhandled exception checking quorum in alertMail", e); 
            }

            try {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Sleeping for " + 
                      AlertDefaultClient.CHECK_NOTIF_PERIOD
                      + " seconds");
                }
                Thread.sleep(AlertDefaultClient.CHECK_NOTIF_PERIOD * 1000);
            } catch (InterruptedException ie) {
                logger.severe("Thread for AlertMail got interrupted...");
                keepRunning = false;
            }
        } 

    }


    // Empty the queue of notifications.
    protected void purgeQueue(boolean shutdown) {
        int nbMessages = 0;
        int nMessageFail=0;
        MailNotification curNotification = 
          (MailNotification) getNextNotification();
        while (curNotification != null) {
            try {
                email(curNotification);
            } catch (MailException me) {
                logger.severe("Cannot send the mail - misconfigured mail server?" + 
                  me + 
                  "\nWould have sent:" + 
                  curNotification.getMessage());
                //
                // If we are in shutdown, we should dump the queue manually,
                // otherwise we'll keep retrying, and failing.
                //
                if(shutdown==true || !keepRunning) {
                    logger.severe ("Dumping mail queue, because we're in shutdown.");

                    while (!notifications.isEmpty()) {
                        curNotification = 
                            (MailNotification) getNextNotification();
                        logger.severe("Purging email, Would have sent:" + 
                                      curNotification.getMessage());
                        nMessageFail++;
                    }                        
                        
                }
            }
            nbMessages++;
            curNotification =  (MailNotification) getNextNotification();
        }

        if (shutdown) {
            logger.info("AlertMail : logged last " + 
                        nbMessages + 
                        " and purged " +
                        nMessageFail +
                        "before shutting down...");

        }
    }

    /**
     * Parse the notification from the alert framework and create
     * the corresponding message
     * @return Notification the notification or null if no alert should be
     * generated.
     */
    protected Notification getNotification(AlertApi.AlertObject obj) {
        
        Matcher m = null;
        String bundleStr = null;
        String msg = null;
	String propertyName = obj.getPropertyName();
        try {
            //
            // Check for indicator that stores are blocked/unblocked.  
            //
            m = blockStorePattern.matcher(propertyName);
            if (m.matches()) {
		boolean storesBlocked = obj.getPropertyValueBoolean();
		if (storesBlocked) {
		    bundleStr = getPropertyString(ALERT_MAIL_STORES_BLOCKED);
		    storesBlockTrueAlertReceived = true;
		} else {
		    // If storesBlockTrueAlertReceived is set to true
		    // and we got a storesBlock false value we have gone
		    // from blocked stores to unblocked stores and we
		    // want to send an alert indicating this.  Otherwise
		    // we just got a notification that stores are not blocked
		    // when the cluster came up.  Ignore it.
		    if (storesBlockTrueAlertReceived == false)
			return null;
		    else
			bundleStr = getPropertyString(ALERT_MAIL_STORES_UNBLOCKED);
		    storesBlockTrueAlertReceived = false;
		    
		}
		Object[] args = new Object[] {
		    Integer.toString(Utils.getCellid())
		};
                msg = MessageFormat.format(bundleStr, args);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(msg);
                }
		
                return (new MailNotification(msg));
            }

	    
            //
            // Change in current Map
            //
            /*
              //
              // Commented out because it's way too verbose (100+ messages on bringup)              
              // and because the messages are broken
              // eg: The current disk map has been changed to com.sun.honeycomb.layout.DiskMask@ca9ed4
              //
             m = currentMapPattern.matcher(propertyName);
             if (m.matches()) {
                 bundleStr = getPropertyString(ALERT_MAIL_CURRENT_MAP);
            
                 Object[] args = {obj.getPropertyValueString()};
                 msg = MessageFormat.format(bundleStr, args);
                 if (logger.isLoggable(Level.FINE)) {
                     logger.fine(msg);      
                 }
                 return (new MailNotification(msg));
             }

	    */

            //
            // Change in activeSwitch
            //
            m = activeSwitchPattern.matcher(propertyName);
            if (m.matches()) {
                bundleStr = getPropertyString(ALERT_MAIL_SWITCH_ID);
                int switchIdx = obj.getPropertyValueInt();
                if(switchIdx >= 0) {

                    Object[] args = {switchIdx+1};

                    msg = MessageFormat.format(bundleStr, args);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine(msg);
                    }
                    return (new MailNotification(msg));
                }

            }


            //
            // Change in backup Switch Status for eg if backup switch died 
            //
            m = backupSwitchAlivePattern.matcher(propertyName);
            if (m.matches()) {
                String statusStr;
                statusStr = obj.getPropertyValueBoolean() == true ? 
                                new String("online") : 
                                new String("offline");
                Object[] args = { statusStr };
                bundleStr = getPropertyString(ALERT_MAIL_BACKUP_SWITCH_STATUS);
                msg = MessageFormat.format(bundleStr, args);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(msg);
                }
                return (new MailNotification(msg));
            }

            //
            // Change in service processor Status 
            //
            m = spAlivePattern.matcher(propertyName);
            if (m.matches()) {
                String statusStr;
                statusStr = obj.getPropertyValueBoolean() == true ? 
                                new String("online") : 
                                new String("offline");
                Object[] args = { statusStr };
                bundleStr = getPropertyString(ALERT_MAIL_SP_STATUS);
                msg = MessageFormat.format(bundleStr, args);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(msg);
                }
                return (new MailNotification(msg));
            }
           
            //
            // Change in disk status
            //
            m = diskStatusPattern.matcher(propertyName);
            if (m.matches()) {
                bundleStr = getPropertyString(ALERT_MAIL_DISK_STATUS);
                String statusString=null;
                if(obj.getPropertyValueInt() == Disk.ENABLED) {
                    statusString="enabled";
                } else if(obj.getPropertyValueInt() == Disk.DISABLED) {
                    statusString="disabled";
                }

                if(null==statusString) {
                    return null;
                }

                Object[] args =  {new Integer(m.group(2)),
                                  new Integer(m.group(1)),
                                  statusString};
                msg = MessageFormat.format(bundleStr, args);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(msg);      
                }
                return (new MailNotification(msg));
            } 
            
            /**
             * Commenting TIME COMPLIANCE for 1.1  - Sameer Mehta 
             * // 
             * // Change in NTP Server status
             * // 
            m = ntpServerPattern.matcher(propertyName));
            String statusStr = new String();
            if(m.matches()) {
                logger.info("ntpServerPattern matched "+m.group(3));
                if(m.group(3).equals(TimeManagedService.Proxy.
                                     ALERT_PROP_NTP_SERVER_REMOTE)) {
                    bundleStr = getPropertyString(ALERT_MAIL_NTP_SERVER_REMOTE);
                    statusStr = obj.getPropertyValueBoolean() ? 
                                "synced remotely" : 
                                "synced to its own hardware clock, Compliance is compromised";
                } else if(m.group(3).equals(TimeManagedService.Proxy.
                                            ALERT_PROP_NTP_SERVER_SYNCED)) {
                    bundleStr = getPropertyString(ALERT_MAIL_NTP_SERVER_SYNCED);
                    statusStr = obj.getPropertyValueBoolean() ? 
                                "synced" : 
                                "not synced, Compliance is compromised";
                } else if(m.group(3).equals(TimeManagedService.Proxy.
                                            ALERT_PROP_NTP_SERVER_RUNNING)) {
                    bundleStr = getPropertyString(ALERT_MAIL_NTP_SERVER_RUNNING);
                    statusStr = obj.getPropertyValueBoolean() ? 
                                "running" : 
                                "not running, Compliance is compromised";
                } else if(m.group(3).equals(TimeManagedService.Proxy.
                                            ALERT_PROP_NTP_SERVER_TRUSTED)) {
                    bundleStr = getPropertyString(ALERT_MAIL_NTP_SERVER_TRUSTED);
                    statusStr = obj.getPropertyValueBoolean() ? 
                                "trusted" : 
                                "not trusted, Compliance is compromised";
                }
                
                String ntpServer = getNtpServerFromIndex(Integer.parseInt(m.group(2)));
                if(ntpServer == null) {
                    logger.severe("cannot retrieve ntp server from index");
                    return null;
                }
                logger.info("alert email sent for ntp server: " +ntpServer);
                Object[] args =  { ntpServer, 
                                   statusStr,
                                 }; 

                msg = MessageFormat.format(bundleStr, args);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(msg);      
                }
                return (new MailNotification(msg));
            }

 
            //
            // CMM master node date compliance alert 
            // 
            m = masterNodeCompliancePattern.matcher(propertyName); 
            if(m.matches()) { 
                logger.info("master node compliance property");
                if(m.group(2).equals(TimeManagedService.Proxy.
                                     ALERT_PROP_MASTER_NODE_DATE_COMPLIANT)) {
                    bundleStr = getPropertyString(
                                     ALERT_MAIL_MASTER_NODE_COMPLIANT);
                    statusStr = obj.getPropertyValueBoolean() ? 
                                    "Time Compliant" : 
                                    "not Time Compliant";
                 
                    logger.info("alert email sent for master node compliance");
                    Object[] args =  { 
                                       statusStr,
                                     };
                    msg = MessageFormat.format(bundleStr, args);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine(msg);      
                    }
                    return (new MailNotification(msg));
                }
            }
            * END OF COMMENTING TIME COMPLIANCE ALERTS
            */
        } catch (AlertException ae) {
            logger.log(Level.SEVERE,
                       "cannot format mail message...",
                       ae);
        }
        return null;
    }

    //
    // create message from the CMM notification.
    //
    protected Notification getNotification(NodeChange notif) {
        String bundleStr = null;
        String msg = null;
        String cause = null;

        Integer nodeid = new Integer(notif.nodeId());
        switch(notif.getCause()) {
        case NodeChange.MEMBER_JOINED:
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("AlertMail: NODE " + notif.nodeId() +
                            " joined...");
            }
            cause = "joined";
            break;
            
        case NodeChange.MEMBER_LEFT:
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("AlertMail: NODE " + notif.nodeId() +
                            " left...");
            }
            cause = "left";
            break;
            
        default:
            // ignore
            break;
        }
        if (cause != null) {
            Object[] args = {nodeid, cause};
            bundleStr = getPropertyString(ALERT_MAIL_CMM_MEMBERSHIP);
            msg = MessageFormat.format(bundleStr, args);

            return (new MailNotification(msg));
        }
        return null;
    }

    protected Notification getNotification(String str) {
        return (new MailNotification(str));
    }

    //
    // Email the current message
    //

    private void email(MailNotification notif)
        throws MailException {

                
        logger.log(ExtLevel.EXT_INFO, notif.getMessage());
        if (addrTo == null)
            return;
            
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("AlertMail: email msg = " +
              notif.getMessage());
        }
	
	MultiCellLib cellLib = MultiCellLib.getInstance();
        String adminIp = cellLib.getAdminVIP();
        byte localCellId= Utils.getCellid();

        MailMessage msg  = new MailMessage(addrSender,
          (EmailAddress)
          addrTo.get(0));
	StringBuffer subjectLine = new StringBuffer("ST5800 Alert [");
	subjectLine.append(adminIp);
	
	if (cellLib.isCellStandalone() == false) {
	    // If there's more than one cell add cell id
	    subjectLine.append(":").append(localCellId);
	}
	subjectLine.append("]:").append(notif.getMessage());
        msg.setSubject(subjectLine.toString());
            
        for (int i = 1; i < addrTo.size(); i++) {
            msg.addRecipient(MailMessage.RECIP_TO,
              (EmailAddress)addrTo.get(i));
        }
            
        if (addrCC != null && !(addrCC.isEmpty())) {
            for (int i = 0; i < addrCC.size(); i++) {
                msg.addRecipient (MailMessage.RECIP_CC, 
                  (EmailAddress)addrCC.get(i));
            }
        }
            
        StringBuffer buffer = new StringBuffer();
        buffer.append("Incident occurred at: ");
        buffer.append(notif.getTime());
        buffer.append("\r\n");
        buffer.append(notif.getMessage());
        

        
	
        buffer.append("\n\n\nSystem identification:\nAdmin IP: " + adminIp);
        buffer.append("\r\n");
        buffer.append("Cell ID: " + localCellId);
        buffer.append("\r\n\r\n");
            
        msg.setBody(buffer.toString());
            
        SMTPClient smtp = new SMTPClient (smtpHost, smtpPort);
        smtp.send (msg);
        smtp.close();
    }


    //
    // Config/Update
    //
    protected void readConfig () {
        super.readConfig();
        readPropSmtpServer();
        readPropSmtpPort();
        readPropSmtpFrom();
        readPropAddrTo();
        readPropAddrCc();
        readPropPhoneHomeTo();
        readPropPhoneHomeFrequency();
    }

    private void readPropSmtpServer() {
        synchronized(AlertDefaultClient.class) {
            smtpHost = config.getProperty(ConfigPropertyNames.PROP_SMTP_SERVER);
        }
    }

    private void readPropSmtpPort() {

        synchronized(AlertDefaultClient.class) {
            String sPort = config.getProperty(ConfigPropertyNames.PROP_SMTP_PORT);
            if (sPort == null) {
                smtpPort = 25;
            } else {
                try {
                    smtpPort = Integer.parseInt (sPort);
                } 
                catch (NumberFormatException nfe) {
                    throw new InternalException("invalid smtp port in config: " +
                                                sPort);
                }
            }
        }
    }

    private void readPropSmtpFrom() {
        synchronized(AlertDefaultClient.class) {
            String fromUser = config.getProperty(ConfigPropertyNames.PROP_SMTP_FROM);
            if (fromUser == null) {
                logger.warning (ConfigPropertyNames.PROP_SMTP_FROM 
                                + " is not set in config.. using default");
                fromUser = "ST5800-noreply@sun.com";
            }
            
            try {
                addrSender = new EmailAddress(fromUser);
            } catch (MailException me) {
                addrSender = null;
                logger.warning("bad sender in config");
            }
        }
    }

    private void readPropPhoneHomeTo() {
        synchronized(AlertDefaultClient.class) {
            String to = config.getProperty(
		ConfigPropertyNames.PROP_PHONEHOME_SMTP_TO);
            if (to == null) {
                logger.warning (
		    ConfigPropertyNames.PROP_PHONEHOME_SMTP_TO 
                                + " is not set in config.. no default");
                to = null;
            }
            
            try {
                phoneHomeTo = new EmailAddress(to);
            } catch (MailException me) {
                phoneHomeTo = null;
                logger.warning("bad phone home address in config");
            }
        }
    }

    private void readPropPhoneHomeFrequency() {
        synchronized(AlertDefaultClient.class) {
            String foo = config.getProperty(
		ConfigPropertyNames.PROP_PHONEHOME_FREQUENCY);
            if (foo == null) {
                logger.warning (
		    ConfigPropertyNames.PROP_PHONEHOME_FREQUENCY
                                + " is not set in config.. using default");
                phoneHomeFreq = 86400000; // 1 day in millis
            }

            try {
                phoneHomeFreq = Long.parseLong (foo);
            } catch (NumberFormatException e) {
                phoneHomeFreq = -1;
                logger.warning ("bad phone home frequency in config");
            }
        }
    }

    private void readPropAddrTo() {
        synchronized(AlertDefaultClient.class) {
            addrTo = parseAddresses(config.getProperty(
		ConfigPropertyNames.PROP_SMTP_TO));
        }
    }

    private void readPropAddrCc() {
        synchronized(AlertDefaultClient.class) {
            addrCC = parseAddresses(config.getProperty(
		ConfigPropertyNames.PROP_SMTP_CC));
        }
    }

    public void propertyChange(PropertyChangeEvent event) {
        super.propertyChange(event);
        String prop = event.getPropertyName();

        if (prop.equals(ConfigPropertyNames.PROP_SMTP_SERVER)) {
            readPropSmtpServer();
            propertyChangeMail(event);
        } else if (prop.equals(ConfigPropertyNames.PROP_SMTP_PORT)) {
            readPropSmtpPort();
            propertyChangeMail(event);
        } else if (prop.equals(ConfigPropertyNames.PROP_SMTP_FROM)) {
            readPropSmtpFrom();
        } else if (prop.equals(ConfigPropertyNames.PROP_SMTP_TO)) {
            readPropAddrTo();
        } else if (prop.equals(ConfigPropertyNames.PROP_SMTP_CC)) {
            readPropAddrCc();
        } else if (prop.equals(ConfigPropertyNames.PROP_PHONEHOME_SMTP_TO)) {
            readPropPhoneHomeTo();
        } else if (prop.equals(ConfigPropertyNames.PROP_PHONEHOME_FREQUENCY)) {
            readPropPhoneHomeFrequency();
        } else if (prop.equals(MultiCellLib.PROP_DATA_VIP) ||            
                   prop.equals(MultiCellLib.PROP_SP_VIP) ||
                   prop.equals(MultiCellLib.PROP_SUBNET) ||
                   prop.equals(MultiCellLib.PROP_ADMIN_VIP) ||
                   prop.equals(MultiCellLib.PROP_GATEWAY)) {
            propertyChangeMail(event);
        }
    }
     
    private void propertyChangeMail(PropertyChangeEvent event) {
        Notification notif =null;

        Object [] args= {event.getNewValue()};
        String str=null;
        String prop=event.getPropertyName();

        if (prop.equals(MultiCellLib.PROP_DATA_VIP)) {
            str = getPropertyString(ALERT_MAIL_DATA_VIP);
        } else if (prop.equals(MultiCellLib.PROP_ADMIN_VIP)) {
            str = getPropertyString(ALERT_MAIL_ADMIN_VIP);
        } else if (prop.equals(MultiCellLib.PROP_SP_VIP)) {
            str = getPropertyString(ALERT_MAIL_SP_IP);
        } else if (prop.equals(MultiCellLib.PROP_SUBNET)) {
            str = getPropertyString(ALERT_MAIL_SUBNET);           
        } else if (prop.equals(MultiCellLib.PROP_GATEWAY)) {
            str = getPropertyString(ALERT_MAIL_GATEWAY_IP);           
        } else if (prop.equals(ConfigPropertyNames.PROP_SMTP_SERVER)) {
            str = getPropertyString(ALERT_MAIL_SMTP_SERVER);           
        } else if (prop.equals(ConfigPropertyNames.PROP_SMTP_PORT)) {
            str = getPropertyString(ALERT_MAIL_SMTP_PORT);           
        }
        if (null == str) {
            logger.severe("Missing message that corresponds to " + 
                          prop +
                          ". no email sent for this change.");
            return;
        }
        String msg = MessageFormat.format(str, args);

        notif=new MailNotification(msg);
        synchronized(notifications) {
            notifications.add(notif);
        }

    }




    //
    // Turns a properties value into an array of EmailAddress objects.
    //
    private ArrayList parseAddresses (String line) {
        if (line == null || line.length() == 0) {
            return null;
        }

        ArrayList addrs = new ArrayList();

        String[] x = line.split (PATTERN_PROP_SPLIT);
        for (int i = 0; i < x.length; i++) {
            try {
                if (x[i].indexOf ("@") > 0) {
                    addrs.add (new EmailAddress (x[i]));
                } else {
                    addrs.add (new EmailAddress (x[i] + "@" + smtpHost));
                }
            } catch (MailException me) {
                logger.warning ("invalid email address in config: " + x[i]);
            }
        }
        return addrs;
    }

    private String getPropertyString(String prop) {
        String res = null;
        try {
            res = mailBundle.getString(prop);
        } catch (MissingResourceException mr) {
            logger.severe("missing resource " + prop + "in configuration file");
        }
        return res;
    }


    private void phoneHome() throws ServerConfigException {
        if (phoneHomeFreq <= 0) {
            logger.finest ("bypassing phone home, no frequency set");
            return;
        }

        if (phoneHomeTo == null) {
            logger.finest ("bypassing phone home, no phone home address");
            return;
        }

        // check to see if we need to phone home
        long last = getLastPhoneHomeTime();
        long now  = System.currentTimeMillis();
        long delta = now - last;

        if (delta > phoneHomeFreq) {
            doPhoneHome(now);
        }
    }


    private long getLastPhoneHomeTime() {
        String foo = config.getProperty (
	    ConfigPropertyNames.PROP_PHONEHOME_LAST_TIMESTAMP);
        long last = -1;

        if (foo == null) {
            // we've never phoned home before...
            return System.currentTimeMillis();
        }

        try {
            last = Long.parseLong (foo);
        } catch (NumberFormatException e) {
            logger.warning ("unable to parse last phone home time");
        }

        return last;
    }

    private StringBuffer getAlertTreeString() throws ServerConfigException {

        AlertApi.AlertViewProperty view = null;

        try {
            view = api.getViewProperty();
        } catch (Exception e) {
            logger.log (Level.SEVERE, "Alert exception getting view", e);
        }
        
        if (view == null) {
            logger.log (Level.WARNING, "Unable to read alert tree");
            return null; // don't update the last updated prop, so we keep retrying
        }

        StringBuffer buf = new StringBuffer();
        AlertApi.AlertObject o = view.getFirstAlertProperty();
        while (o != null) {
            buf.append (o.getPropertyName());
            buf.append (": ");

            try {
                switch (o.getPropertyType()) {
                case AlertType.INT:
                    buf.append (o.getPropertyValueInt());
                    break;

                case AlertType.LONG:
                    buf.append (o.getPropertyValueLong());
                    break;

                case AlertType.DOUBLE:
                    buf.append (o.getPropertyValueDouble());
                    break;

                case AlertType.FLOAT:
                    buf.append (o.getPropertyValueFloat());
                    break;

                case AlertType.STRING:
                    buf.append (o.getPropertyValueString());
                    break;

                case AlertType.BOOLEAN:
                    buf.append (o.getPropertyValueBoolean());
                    break;

                default:
                    buf.append ("??? unknown type ???");
                }
            }
            catch (AlertException ae) {
                buf.append ("???");
            }

            buf.append ("\n");
            o = view.getNextAlertProperty();
        }
        return buf;
    }

    private void doPhoneHome(long timestamp) throws ServerConfigException {
    
        StringBuffer buf = getAlertTreeString();

        try {
            MailMessage msg = new MailMessage (addrSender, phoneHomeTo);
            msg.setSubject ("ST5800 Heartbeat: " + timestamp);
            
            msg.setBody(buf.toString());
        
            SMTPClient smtp = new SMTPClient (smtpHost, smtpPort);
            smtp.send (msg);
            smtp.close();
        } catch (MailException me) {
            logger.severe("unable to send heartbeat mail: " +
                          me + 
                          "\nWould have sent:" + 
                          buf.toString());
        }

        // update config with the new timestamp
        config.put(ConfigPropertyNames.PROP_PHONEHOME_LAST_TIMESTAMP, 
              Long.toString(timestamp));
    }

    /*
     *  Checks to see if quorum status has changed
     */
    static long count = 0;
    private void checkQuorum() throws ServerConfigException {
        
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (obj instanceof NodeMgrService.Proxy) {
            NodeMgrService.Proxy nodeMgr = (NodeMgrService.Proxy) obj;
            boolean hasQuorum = nodeMgr.hasQuorum();
            // Don't send alert if system booting up and is getting
            // quorum for the first time'
            if (hasQuorum != saveQuorum && hadQuorum) {
                Object[] args = new Object[0];
                String msgKey = hasQuorum ?
                    AdminResourcesConstants.MSG_KEY_ALERT_GAINED_QUORUM :
                    AdminResourcesConstants.MSG_KEY_ALERT_LOST_QUORUM;
                saveQuorum = hasQuorum;
                
                String msg = this.getPropertyString(msgKey);
                
                if (null != msg) {                    
                    msg = MessageFormat.format(msg, args);
                    Notification notif=new MailNotification(msg);
                    synchronized(notifications) {
                        notifications.add(notif);
                    }
                }
            }
            if (hasQuorum) {
                hadQuorum = true;
            }
        } else {
            //diane todo put limit on how often this prints
            logger.warning("Can't get NodeMgrProxy object, unable " +
                    "to determine quorum status");
        }
    }


    private class MailNotification extends AlertDefaultClient.Notification {
        private String time;

        public MailNotification(String msg) {
            super(msg);
            time = (new GregorianCalendar()).getTime().toString();
        }
        
        public String getTime() {
            return time;
        }
    }
  
    private String getNtpServerFromIndex(int index) {
        TimeManagedService.Proxy TimeProxy;

        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE,
                                             "TimeKeeper");
        if (! (obj instanceof TimeManagedService.Proxy)) {
            logger.warning ("TimeManagedService.Proxy not available");
            throw new RuntimeException (
                "unable to acquire to time proxy");
        }
        TimeProxy = (TimeManagedService.Proxy) obj;
        return TimeProxy.getNtpServerFromIndex(index);
    }
}
