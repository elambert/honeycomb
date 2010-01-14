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



package com.sun.honeycomb.util;

import com.sun.honeycomb.admin.mgmt.server.HCSwitch;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;

import java.math.BigInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Static switch class for switch updates, reboot, and switch fru generation
 */
public class Switch extends AncillaryServer {

    private static final Logger logger =
        Logger.getLogger(Switch.class.getName());

    public static final String SWITCH_FAILOVER_IP = "10.123.45.1";
    public static final int SWITCH_SSH_PORT = 2222;
    public static final String SWITCH_SSH_USER = "nopasswd";

    public static final String ZRULE_CMD = "/usr/sbin/zrule2 ";
    public static final String ZRULE_OPTS = " -i -timeout 5000 -retries 5 -b 127.0.0.1 ";
    private static final int PRIMARY_SWITCH_ID = 1;
    private static final int BACKUP_SWITCH_ID = 2;
    private static final int[] SWITCH_IDS = { PRIMARY_SWITCH_ID, BACKUP_SWITCH_ID }; 
    private static final String RUN_CMD_OTHER = "/usr/bin/run_cmd_other.sh";

    // Changing the settings: for security, ssh to swcfg/swadm on the switch
    private static final String SWADM_CMD = "/usr/sbin/swadm";
    private static final String SWCFG_CMD = "/usr/sbin/swcfg -o -b";
    private static final int SWCFG_RETRIES = 3;

    // These are the URLs that we get use to read settings from the switch
    private static final String SWINFO_URI_PREFIX = "/http/cgi-bin/swinfo?";
    private static final String SWINFO_VERSION = "vers";
    private static final String SWINFO_ACTIVESWITCH = "active";
    private static final String SWINFO_BACKUPSWOK = "backupOK";
    private static final String SWINFO_FRUIDACTIVE = "fruid_active";
    private static final String SWINFO_FRUIDBACKUP = "fruid_backup";

    // This is a singleton class, because Java does not allow you to
    // override static methods in any sane way.
    private static Switch theObj = null;
    private static Switch getInstance() {
        if (theObj == null)
            theObj = new Switch();
        return theObj;
    }

    protected Switch() {
    }

    String getAddress() {
        return SWITCH_FAILOVER_IP;
    }
    int getSshPort() {
        return SWITCH_SSH_PORT;
    }

    int getHttpPort() {
        return 80;
    }
    
    String getSshUser() {
        return SWITCH_SSH_USER;
    }
    
    /**
     * Returns the list of switch id's
     * @return int[] array of switch id
     */
    public static int[] getIds() {
        return SWITCH_IDS;
    }
    
 
    /**
     * @param switchId the id that identifies the switch that should be 
     * retrieved.
     * @return HCSwitch the fru switch object for the specified switch id
     */
    public static HCSwitch getSwitchFru(int switchId) {
        //
        // HC* objects are automatically generated as part of MGMT
        // and shouldn't be allowed out of the adapters
        //
        HCSwitch fru = new HCSwitch();
	fru.setFruName(Switch.getName(switchId));
	fru.setSwitchName(fru.getFruName());	// Remove as part of CR6643979
        int status =Switch.getStatus(switchId);
        if (status == (CliConstants.HCSWITCH_STATUS_OFFLINE)) {
            fru.setVersion(CliConstants.HCFRU_UNKNOWN_STR);
            fru.setFruId(CliConstants.HCFRU_UNKNOWN_STR);       
            fru.setStatus(BigInteger.valueOf(CliConstants.HCSWITCH_STATUS_OFFLINE));
        } else {
            String version = Switch.getVersion(switchId);
            if(null==version) {
                fru.setVersion(CliConstants.HCFRU_UNKNOWN_STR);
            } else {
                fru.setVersion(Switch.getVersion(switchId));
            }
            String fruId = Switch.getFruId(switchId);
            if(null==fruId) {
                fru.setFruId(CliConstants.HCFRU_UNKNOWN_STR);
            } else {
                fru.setFruId(Switch.getFruId(switchId));
            }
            fru.setStatus(BigInteger.valueOf(status));
        }      
        fru.setFruType(BigInteger.valueOf(CliConstants.HCFRU_TYPE_SWITCH));
        return fru;
    }


    
    public static void updateAdmin(String adminVip) {
        updateSwitches(getAdminOptions(adminVip));
    }

    public static void updateData(String dataVip) {
        updateSwitches(getDataOptions(dataVip));
    }

    public static void updateSp(String spIp) {
        updateSwitches(getSpOptions(spIp));
    }

    public static void updateSmtp(String smtp) {
        updateSwitches(getSmtpOptions(smtp));
    }

    public static void updateSmtpPort(String smtpPort) {
        updateSwitches(getSmtpPortOptions(smtpPort));
    }

    public static void updateNtp(String ntp) {
        updateSwitches(getNtpOptions(ntp));
    }

    public static void updateGateway(String gateway, String subnet) {
        updateSwitches(getGatewayOptions(gateway) + getSubnetOptions(subnet));
    }

    public static void updateLogger(String extLogger) {
        updateSwitches(getLoggerOptions(extLogger));
    }

    /**
     * Update the switches with all relevant network information
     */
    public static void updateAll(String dataVip,
                                 String adminVip,
                                 String spIp,
                                 String smtp,
                                 String smtp_port,
                                 String ntp,
                                 String subnet,
                                 String gateway,
                                 String extlogger,
                                 String authClients,
                                 String dns,
                                 String domain_name,
                                 String dns_search,
                                 String dns_primary_server,
                                 String dns_secondary_server) {

        // Construct the command

        String cmd = new String();

        if (null != dataVip)
            cmd += getDataOptions(dataVip);

        if (null != adminVip)
            cmd += getAdminOptions(adminVip);

        if (null != spIp)
            cmd += getSpOptions(spIp);

        if (null != gateway)
            cmd += getGatewayOptions(gateway);
        
        if (null != extlogger)
            cmd += getLoggerOptions(extlogger);

        if (null != subnet)
            cmd += getSubnetOptions(subnet);
        
        if (null != smtp)
            cmd += getSmtpOptions(smtp);

        if (null != smtp_port)
            cmd += getSmtpPortOptions(smtp_port);

        if (null != ntp) 
            cmd += getNtpOptions(ntp);

        cmd += getDnsOptions(dns);
        cmd += getDomainOptions(domain_name);
        cmd += getDnsSearchOptions(dns_search);
        cmd += getDnsPrimaryServerOptions(dns_primary_server);
        cmd += getDnsSecondaryServerOptions(dns_secondary_server);
        cmd += getAuthClientOptions(authClients);

        // Now we can run the command
        updateSwitches(cmd);
    }

    /**
     * Update the switches with all relevant network information
     */
    private static void updateSwitches(String args) {
        String cmd = SWCFG_CMD + args;

        for (int i = 1; i <= SWCFG_RETRIES; i++) {
            try {
                logger.info("Switch update try #" + i + ": " + cmd);
                if (getInstance().runSshCommand(cmd))
                    return;
            }
            catch (Exception e) {
                logger.log(Level.WARNING,
                           "Switch update try #" + i + " failed", e);
            }
        }

        logger.severe("Command failed on switch " + StringUtil.image(cmd));
    }

    /** pass null to any options you don't want to set. */
    public static void reconfigSwitches(String adminIp,
                                        String dataIp,
                                        String subnet,
                                        String gateway,
                                        String ntp) {

       // FIXME: Just plain not working that well right now. Using old method.
       //        Dynamic updating of switches is currently taking 
       //        so long that it's actually faster to reboot, anyhow.
       // FIXME: Should support all the options that Switch.updateAll supports.

        String cmd = SWADM_CMD;

        if (null != adminIp)
            cmd += getAdminOptions(adminIp);

        if (null != dataIp)
            cmd += getDataOptions(dataIp);

        if (null != subnet)
            cmd += getSubnetOptions(subnet);

        if (null != gateway)
            cmd += getGatewayOptions(gateway);

        if (null != ntp)
            cmd += getNtpOptions(ntp);


        /*
         * exec the switch reconfig command
         */
        try {
            logger.info("Reconfiguring switches: \"" + cmd + "\"");
            if (!getInstance().runSshCommand(cmd))
                logger.severe("Switch cmd \"" + cmd + "\" failed");
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Switch cmd \"" + cmd + "\" failed", e);
        }
    }

    /**
     * Tell the switches to reboot
     */
    public static void rebootSwitches() {
        String cmd = SWADM_CMD + getRebootOptions();
        ResourceBundle rs = BundleAccess.getInstance().getBundle();


        try {
            String str1 = rs.getString("info.util.Switch.reboot");
            Object [] args1 = {new String(cmd)};
            logger.log(ExtLevel.EXT_INFO, MessageFormat.format(str1, args1));
            logger.info("Rebooting switches: \"" + cmd + "\"");

            if (!getInstance().runSshCommand(cmd)) {
                String str2 = rs.getString("err.util.Switch.rebootfailed");
                Object [] args2 = {new String(cmd)};
                logger.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str2, args2));
                logger.severe("Switch reboot failed.");
            }
        }
        catch (Exception e) {
            String str3 = rs.getString("err.util.Switch.rebootfailed");
            Object [] args3 = {new String(e.getMessage())};
            logger.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str3, args3));
            logger.log(Level.SEVERE, "Switch reboot failed", e);
        }
    }

    public static boolean inbound(String ip, int port) {

        return sw_fnat_base(true, true, true, ip, port, -1);
    }

    public static boolean delete_inbound(String ip, int port) {

        return sw_fnat_base(false, true, true, ip, port, -1);
    }

    public static boolean delete_inbound(String ip, int port, int swport) {

        return sw_fnat_base(false, true, true, ip, port, swport);
    }

    public static boolean outbound(int port) {

        return sw_fnat_base(true, true, false, null, port, -1);
    }

    public static boolean outbound_udp(String ip, int port) {

        return sw_fnat_base(true, false, false, ip, port, -1);
    }

    public static boolean outbound_udp(int port) {

        return sw_fnat_base(true, false, false, null, port, -1);
    }

    public static boolean delete_outbound(int port) {

        return sw_fnat_base(false, true, false, null, port, -1);
    }

    public static boolean delete_outbound(int port, int swport) {

        return sw_fnat_base(false, true, false, null, port, swport);
    }

    private static boolean sw_fnat_base(boolean add, boolean tcp, boolean in,
                                        String ip, int port, int swport) {
        String cmd = ZRULE_CMD;

        if (add) {
            cmd += " add";
        } else {
            cmd += " delete";
        }

        cmd += ZRULE_OPTS;

        if (ip != null) {
            cmd += "-dest " + ip + " ";
        }

        if (tcp) {
            cmd += "-t tcp ";
        } else {
            cmd += "-t udp ";
        }

        if (in) {
            cmd += "-destport ";
        } else {
            cmd += "-srcport ";
        }

        if (swport == -1) {
            NodeMgrService.Proxy proxy = getProxy();

            if (proxy == null) {
                return false;
            }

            Node master = proxy.getMasterNode();
            swport = master.nodeId() - 100; // UGH!!!
        }

        cmd += port + " -s " + swport;

        try {
            logger.info("Operating on Switch rules");

            getInstance().runSshCommand("echo " + cmd + ">>/etc/honeycomb/rules");
            return getInstance().runSshCommand(cmd);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Modifying the Switch rules failed", e);
        }

        return false;
    }

    ////////////////////////////////////////////////////////////////////////
    // Read-only access to switch parameters is done via HTTP, not SSH

    /**
     * Never return null unless you put a big freakin' "THIS RETURNS NULL"
     * warning. Thank you. Even better, throw a proper exception, 
     * and declare a throws.
     * @returns String the serial number of the switch
     */
    private static String getSwitchValue(String tag) {
        String errMsg = "Couldn't get swinfo value " + StringUtil.image(tag) +
            " from switch -- invalid overlay?";

        try {
            String url = SWINFO_URI_PREFIX + tag;
            String reply = getInstance().getHttpFirstLine(url);
            if (reply != null)
                return reply.trim();

            logger.warning(errMsg);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, errMsg, e);
        }
        return null;
    }

    public static String getActiveSwitchId() {
        String switchID = getSwitchValue(SWINFO_ACTIVESWITCH);
        if (switchID == null)
            return Integer.toString(PRIMARY_SWITCH_ID);
        return switchID;
    }

    public static String switchVersion() {
        return getVersion(Integer.parseInt(getActiveSwitchId()));
    }

    public static String getVersion(int switchid) {
        String version = getSwitchValue(SWINFO_VERSION + switchid);
        if (logger.isLoggable(Level.INFO))
            logger.info("Switch " + switchid + " version: " +
                        StringUtil.image(version));
        return version;
    }

    /**
     * Get the state of the specified switch
     * @param switchid the id of the switch to retrieve the state for
     * @return int the state of the switch, where statis is
     * CliConstants.HCSWITCH_STATUS_ACTIVE,
     * CliConstants.HCSWITCH_STATUS_STANDBY, or
     * CliConstants.HCSWITCH_STATUS_OFFLINE
     */
    public static int getStatus(int switchid) {
         int active_id = Integer.parseInt(getActiveSwitchId());
         if (switchid == active_id 
                 && active_id == PRIMARY_SWITCH_ID)
             return CliConstants.HCSWITCH_STATUS_ACTIVE;
         else if (isBackupSwitchAlive())
             return CliConstants.HCSWITCH_STATUS_STANDBY;
         else
             return CliConstants.HCSWITCH_STATUS_OFFLINE;
    }
    
    /**
     * Get the name of the switch
     * @param switchid the id of the switch to retrieve the fru name for
     * @return String the id for the specified switch
     */
    public static String getName(int switchid) {
        return new StringBuffer("SWITCH-").append(switchid).toString();
    }

    /**
     * Get the fru id of the specifed switch
     * @param switchid the id of the switch to retrieve the fru id for
     * @return String the id for the specified switch
     */
    public static String getFruId(int switchid) {
        if (switchid == PRIMARY_SWITCH_ID)
           return getFruidPrimary();
        else
           return getFruidBackup();
    }  
    
    public static boolean isBackupSwitchAlive() {
        String backupAlive = getSwitchValue(SWINFO_BACKUPSWOK);
        if (backupAlive == null)
            return false;
        return backupAlive.equalsIgnoreCase("true");
    }
    
    public static String getFruidPrimary() {
        return getSwitchValue(SWINFO_FRUIDACTIVE);
    }
    public static String getFruidBackup() {
        return getSwitchValue(SWINFO_FRUIDBACKUP);
    }
    
    

    ////////////////////////////////////////////////////////////////////////

    private static void usage() {
        System.err.println("Switch <\"in\" | \"out\"> " +
                           "<\"add\" | \"del\"> <\"tcp\" | \"udp\"> " +
                           " <dataVIP> <portNumber>");
    }

    public static void main(String [] args) {
        boolean add = true;
        boolean tcp = true;

        if (args[0].equals("fake")) {
            System.out.println("Active switch: " +
                               StringUtil.image(getActiveSwitchId()));
            System.out.println("Switch version: " +
                               StringUtil.image(switchVersion()));
            System.out.println("Switch 1 version: " +
                               StringUtil.image(getVersion(1)));
            System.out.println("Switch 2 version: " +
                               StringUtil.image(getVersion(2)));
            System.out.println("Backup switch " + 
                               (isBackupSwitchAlive()? "":"not ") + "OK");

            AncillaryServer.setFakeit(true);

            updateAdmin("10.123.45.500");
            updateData("10.123.45.501");
            updateSp("10.123.45.502");
            updateSmtp("10.123.45.503");
            updateSmtpPort("2525");
            updateNtp("10.123.45.504");
            updateGateway("10.123.45.1", "255.255.255.0");
            updateLogger("10.123.45.505");

            updateAll("10.123.45.501", "10.123.45.500", "10.123.45.502",
                      "10.123.45.503", "2525", "10.123.45.504",
                      "255.255.255.0", "10.123.45.1", "10.123.45.505",
                      null, "Y", "honeycomb.example.com",
                      "example.com", "10.7.224.10", null);

            System.exit(0);
        }

        if (args.length != 5) {
            usage();
            System.exit(1);
        }

        String vip = args[3];
        int port = -1;
        try {
            port = Integer.parseInt(args[4]);
        } catch (Exception e) {
            usage();
        }

        if (!args[1].equals("add")) {
            add = false;
        }
		
        if (!args[2].equals("tcp")) {
            tcp = false;
        }
		
        if (args[0].equals("in")) {
            System.out.println("configure inbound traffic for vip = " +
                               vip + ", port = " + port);
            Switch.sw_fnat_base(add, tcp, true, vip, port, -1);
        } else if (args[0].equals("out")) {
            System.out.println("configure outbound traffic for vip = " +
                               vip + ", port =" + port);
            Switch.sw_fnat_base(add, tcp, false, vip, port, -1);
        } else {
            usage();
        }
    }
			
}
