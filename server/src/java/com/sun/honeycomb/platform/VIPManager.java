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



package com.sun.honeycomb.platform;

import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.spreader.SpreaderManagedService;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.Commands;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.multicell.lib.MultiCellLib;

import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.Inet4Address;
import java.net.InetAddress;

/*
 * This class network interfaces and configuires them with the
 * appropriate data VIP.
 *
 * A slightly smaller hack than it used to be, but it's
 * STILL A BIG HACK - 
 */
public class VIPManager {

    private static final Logger logger =
        Logger.getLogger(VIPManager.class.getName());

    private static final String PNAME_SWITCH_TYPE = 
        "honeycomb.cell.switch_type";

    private static HardwareProfile profile;

    private String activeInterface = null;
    private String dataVipInterface = null;
    private String internalMasterVipInterface = null;
    private String masterMulticellVipInterface = null;
    private String dataVip = null;
    private String macAddress = null;
    private int nodeId;

    VIPManager(HardwareProfile profile) {
        this.profile = profile;
        nodeId = VIPManager.nodeId();

        queryInterfaces();
        configDataVip();
    }

    public void reInit() {
        try {
            queryInterfaces();
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't re-init.", e);
        }
    }

    public void resetNetwork() {
        configDataVip();
        reInit();
    }

    public String getDataVip() {
        return dataVip;
    }

    public String getMACAddress() {
        return macAddress;
    }

    public String getNetworkInterface() {
        return activeInterface;
    }

    public String getDataVipInterface() {
        return dataVipInterface;
    }

    public String getInternalMasterVipInterface() {
        return internalMasterVipInterface;
    }

    public String getMasterMulticellVipInterface() {
        return masterMulticellVipInterface;
    }

    private void configDataVip() {
        ClusterProperties config = ClusterProperties.getInstance();
        
        String switchType = config.getProperty(PNAME_SWITCH_TYPE);
        if (switchType == null)
            switchType = "other";

        String vip = null;
        
        if (switchType.equals("znyx")) {
            vip = MultiCellLib.getInstance().getDataVIP();
            configureSharedDataVip(config, vip, switchType);
        }
        else {
            vip = configureDataVip(nodeId, config);
        }
        if (vip == null)
            throw new RuntimeException(
                    "Couldn't figure out data VIP (switch=" + switchType + ")");

        dataVip = vip;
    }

    private void configureSharedDataVip(ClusterProperties config, 
                                        String vip,
                                        String switchType) {

        String subnet  = MultiCellLib.getInstance().getSubnet();
        String gateway = MultiCellLib.getInstance().getGateway();
        String hcPrefix = config.getProperty("honeycomb.prefixPath");


        String ifcfg_script = hcPrefix + "/bin/ifconfig_script.sh ";
        String cmd = ifcfg_script + activeInterface + " " +
            vip + " " + subnet + " " + gateway + " " + 1 + " start shared";

        int exitCode = -1;
        try {
            exitCode = Exec.exec(cmd, logger);
        }
        catch(IOException e) {
            logger.warning("Couldn't run interface configuration script " +
                           cmd);
        }

        if (exitCode != 0) {
            logger.severe("ifconfig script returned " + exitCode);
            throw new RuntimeException("Couldn't configure data VIP");
        }
        else {
            if (logger.isLoggable(Level.INFO))
                logger.info("Node " + nodeId + "(" + activeInterface +
                            ") config: shared address " + vip + 
                            " using switch type " + switchType);
        }
    }

    /**
     * For switch_type = other -- each node needs to be configured
     * with its externally-visible personal address.
     * This is a temporary hack 
     */
    private String configureDataVip(int nodeid, ClusterProperties conf) {

        logger.warning("*** VIP CONFIGURATION HACK FOR SWITCH=other - NEED TO BE FIXED - ***");
        String vip = null;
        int index = nodeid - 101;

        try {
            String s = conf.getProperty("honeycomb.cell.vips");
            if (s == null) {
                logger.log(Level.WARNING, "Couldn't find honeycomb.cell.vips");
                return null;
            }
            String[] vips = s.trim().split(" ");
            if (vips.length <= index) {
                logger.log(Level.WARNING, "Couldn't find self in honeycomb.cell.vips");
                return null;
            }
            vip = vips[index];
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't find my public address", e);
            return null;
        }

        String prefix  = conf.getProperty("honeycomb.prefixPath");
        String subnet  = MultiCellLib.getInstance().getSubnet();
        String gateway = MultiCellLib.getInstance().getGateway();

        String cmd = prefix + "/bin/ifconfig_script.sh " + activeInterface +
            " " + vip + " " + subnet + " " + gateway + " " + 1 + " start";

        int exitCode = -1;
        try {
            exitCode = Exec.exec(cmd, logger);
        }
        catch(IOException e) {
            logger.warning("Couldn't run interface configuration script "
                           + cmd);
        }

        if (exitCode == 0) {
            if (logger.isLoggable(Level.INFO))
                logger.info("Local node " + nodeid + " configured with VIP " +
                            vip);
        }
        else
            throw new RuntimeException("Failed to configure the VIP: \"" + 
                                       cmd + "\" exit status is " + exitCode);

        return vip;
    }

    /**
     * Talk to the spreader and figure out which is active, and what
     * its MAC address is
     */
    private void queryInterfaces() {
        String[] interfaces = profile.getNetworkInterfaces();
        String[] dataVipInterfaces = profile.getDataVipInterfaces();
        String[] internalMasterVipInterfaces = profile.getInternalMasterVipInterfaces();
        String[] masterMulticellVipInterfaces = profile.getMasterMulticellVipInterfaces(); 

        int activeInterfaceIndex = -1;

        try {
            activeInterfaceIndex = profile.getActiveInterface();
            if (activeInterfaceIndex != -1) {
                activeInterface = interfaces[activeInterfaceIndex];
                dataVipInterface = dataVipInterfaces[activeInterfaceIndex];
                internalMasterVipInterface = 
                    internalMasterVipInterfaces[activeInterfaceIndex];
                masterMulticellVipInterface =
                    masterMulticellVipInterfaces[activeInterfaceIndex];
                macAddress = getMACAddress(activeInterface);
            }
        } catch (Exception e) {
            // If there's no active interface yet, getActiveInterface()
            // will return -1 and cause an exception.  This is harmless,
            // as this method will be retried later when we  have determined
            // the active interface.
        }
    }

    /**
     * Get the node number by extracting it from the hostname.
     */
    private int getHostNum() {

        String hostName = null;

        try {
            // hostnames should be of the form "hcb<nodenum>"

            hostName = getHostName();
            String nodeNumString = hostName.replace("hcb", "");

            return Integer.parseInt(nodeNumString);

        } catch (Exception e) {
            throw new InternalException("Ill-formed hostname " + hostName);
        }
    }

    /**
     * Get the hostname.  It seems like there should be an easier way to
     * do this, but I don't see one in the Java api.  Existing interfaces
     * seem to want to use the internet address and do name resolution;
     * this won't work if we're calling this when trying to find our address.
     */
    private String getHostName() {
        BufferedReader f = null;

        try {
            String cmd = Commands.getCommands().hostname();
            
            f = Exec.execRead(cmd, logger);
            String line;

            if ((line = f.readLine()) != null) {
                logger.warning("getHostName: hostname " + line);
                return line;
            }

            throw new InternalException("Couldn't find hostname");

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally { 
            if ( f != null )
                try {
                    f.close();
                } catch (IOException e) {}
        }
    }

    /**
     * Read the output of ifconfig to find a word that looks like a
     * MAC address (6 bytes separated by ":").
     *
     * There is just no easy way to find the MAC address of an interface.
     */
    private String getMACAddress(String ifName) {
    	BufferedReader f = null;

        if (logger.isLoggable(Level.INFO))
            logger.info("Trying to find MAC address of " + ifName);

        try {
            String cmd = Commands.getCommands().ifconfig() + ifName;

            f = Exec.execRead(cmd, logger);
            String line;

            while ((line = f.readLine()) != null) {
                StringTokenizer s = new StringTokenizer(line);
                while (s.hasMoreTokens()) {
                    String word = s.nextToken();
                    if (isMAC(word)) {
                        if (logger.isLoggable(Level.INFO))
                            logger.info(ifName + " is " + word);
                        return padMACAddress(word.toLowerCase());
                    }
                }
            }

            throw new InternalException("Couldn't find MAC address in " + cmd);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally { 
            if ( f != null )
                try {
                    f.close();
                } catch (IOException e) {}
        }
    }

    /** Checks to see if the string looks like a MAC address */
    private static boolean isMAC(String word) {
        // See if there are 6 octets separated by ":"

        String[] comps = word.split(":");
        if (comps.length != 6)
            return false;

        // Check that each component can be turned into a byte
        for (int i = 0; i < comps.length; i++)
            try {
                // Goddamn java has no unsigned
                if (Short.parseShort(comps[i], 16) >= 256)
                    return false;
            }
            catch (NumberFormatException e) {
                return false;
            }

        return true;
    }

    /* 
     * Pad the Solaris mac address to make sure that each octet has
     * two hex digits to match the traditional mac representation.
     */
    public static String padMACAddress(String mac) {
        String[] macparts = mac.split(":");
        for (int i = 0; i < macparts.length; i++) {
            if (macparts[i].length() == 1) {
                macparts[i] = "0" + macparts[i];
            }
        }
        String padmac = macparts[0] + ":" + macparts [1] +
            ":" + macparts[2] + ":" + macparts[3] +
            ":" + macparts[4] + ":" + macparts[5];
        return padmac;
    }      

    /** Get the node ID from the local node manager */
    private static int nodeId () {
        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor (ServiceManager.LOCAL_NODE);
        if (proxy == null) {
            throw new RuntimeException("Cannot access local node proxy");
        }
        return proxy.nodeId();
    }
}

