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

import com.sun.honeycomb.admin.mgmt.server.HCSP;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.util.Exec;
import java.util.logging.*;
import java.io.*;

import com.sun.honeycomb.common.StringUtil;
import java.math.BigInteger;

/**
 * Static service processor class for service processor updates and reboot
 * ACCESSED BY SERVICE PLATFORM AT STARTUP
 * THE SERVICE PROCESSOR IS NOT PART OF HONEYCOMB AND SHOULD
 * BE ONLY USED FOR SPECIFIC OPERATIONS (upgrade, ping alive)
 */
public class ServiceProcessor extends AncillaryServer {

    private static final String spInternalIp = "10.123.45.100";

    private static final String PING_CMD = "/usr/sbin/ping -c 1 ";
    private static final String SPCFG_CMD = "/opt/honeycomb/bin/spcfg";
    private static final int MAX_PING_RETRY = 3;

    private static final String spVIP = 
        MultiCellLib.getInstance().getSPVIP();
 
    private static final Logger logger =
        Logger.getLogger(ServiceProcessor.class.getName());

    // This is a singleton class, because Java does not allow you to
    // override static methods in any sane way.
    private static ServiceProcessor theObj = null;
    private static ServiceProcessor getInstance() {
        if (theObj == null)
            theObj = new ServiceProcessor();
        return theObj;
    }

    protected ServiceProcessor() {
    }

    String getAddress() {
        return spInternalIp;
    }

    int getSshPort() {
        return 22;
    }

    int getHttpPort() {
        return 80;
    }

    String getSshUser() {
        return null;            // Default.
    }

    // Check if a IP addr is pingable
    private static boolean isIpPingable(String Ip) {
        int num_retries = MAX_PING_RETRY;
        int retry = 1;
        try {
            // is IP  pingable?
            String cmd = PING_CMD + Ip;
            while (retry++ <= num_retries) {
                int rc = Exec.exec(cmd, logger);
                if (rc != 0) {
                    logger.warning("IP " + Ip + " is not pingable, retrying!");
                } else {
                    return true;
                }
            }
        }
        catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't ping the IP ", e);
            return false;
        }
        if (retry > num_retries) {
            logger.warning("IP " + Ip + " is not pingable after "
                           + num_retries + " tries. Marking dead");
             return false;
        } else {
             return true;
        }
    }

    public static boolean isSPAlive() {

        // Ping the internal Ip address.
        if (!isIpPingable(spInternalIp)) {
            logger.warning("Service processor internal ip " + spInternalIp + " is not pingable");
            return false;
        }
        // Ping the external Ip address.
        if (!isIpPingable(spVIP)) {
            logger.warning("Service processor external ip " + spVIP + " is not pingable");
            return false;
        }
        logger.info("Service processor is pingable.");
        return true;
    }

    public static void updateSp(String spIp) {
        updateServiceProcessor(getSpOptions(spIp));
    }

    public static void updateGateway(String gateway, String subnet) {
        updateServiceProcessor(getGatewayOptions(gateway) +
                               getSubnetOptions(subnet));
    }

    /**
     * Update the service processor with all relevant network information
     */
    public static void updateAll(String spIp, String subnet, String gateway) {
        updateServiceProcessor(getSpOptions(spIp) +
                               getGatewayOptions(gateway) +
                               getSubnetOptions(subnet));
    }

    /**
     * Run the spcfg program on the SP
     */
    private static void updateServiceProcessor(String cmd) {
        logger.info("Updating the service processor: \"" + cmd + "\"");
        if (!getInstance().runSshCommandAsRoot(SPCFG_CMD + cmd))
            logger.severe("Service processor update \"" + cmd + "\" failed");
    }

    /**
     * Tell the service processor to reboot
     */
    public static void rebootServiceProcessor() {
        logger.info("Rebooting the service processor");
        if (!getInstance().runSshCommandAsRoot("reboot"))
            logger.severe("Service processor reboot failed");
    }

    /**
     *  Tell the service processor to shutdown
     */
    public static void shutdownServiceProcessor() {
        logger.info("Shutting down the service processor");
        if (!getInstance().runSshCommandAsRoot("poweroff"))
            logger.severe("Service processor poweroff failed");
    }

    /**
     * Get the UUID of the service processor
     */
    public static String getSpUUID() {
        String cmd = "dmidecode | sed -n /UUID/s/^.*://p";
        String uuid = getInstance().getSshFirstLine(cmd).trim();
        if (logger.isLoggable(Level.INFO))
            logger.info("Service Processor UUID " + StringUtil.image(uuid));
        return uuid;
    }

    /**
     * Get the SMDC Firware version for the service processor
     */
    public static String getSpSMDC() {
        String cmd = "ipmitool mc info | sed -n /^Firmware.Revision/s/^.*://p";
        String smdc = getInstance().getSshFirstLine(cmd).trim();
        if (logger.isLoggable(Level.INFO))
            logger.info("Service Processor SMDC " + StringUtil.image(smdc));
        return smdc;
    }

    /**
     * Get the BIOS version of the service processor
     */
    public static String getSpBIOS() {
        String cmd = "dmidecode | grep Version\\ String | cut -f2 -d:";
        String bios = getInstance().getSshFirstLine(cmd).trim();
        if (logger.isLoggable(Level.INFO))
            logger.info("Service Processor BIOS " + StringUtil.image(bios));
        return bios;
    }
 

    /**
     * @return HCSP the fru information for the Service Processor
     */
    public static HCSP getSPFru() {
        HCSP fru = new HCSP();
        fru.setFruName("SN");
        fru.setFruType(BigInteger.valueOf(CliConstants.HCFRU_TYPE_SP));
        boolean isAlive = isSPAlive();
        fru.setStatus(BigInteger.valueOf(
                isSPAlive() ? CliConstants.HCNODE_STATUS_ONLINE : CliConstants.HCNODE_STATUS_OFFLINE));
        if (isAlive)
            fru.setFruId(getSpUUID());
        else
            fru.setFruId(CliConstants.HCFRU_UNKNOWN_STR);
        return fru;
    }

    public static void main(String[] args) {
        System.out.println("SP is " + (isSPAlive()? "":"not ") + "alive.");
        System.out.println("SP UUID: " + StringUtil.image(getSpUUID()));
        System.out.println("SP SMDC: " + StringUtil.image(getSpSMDC()));
        System.out.println("SP BIOS: " + StringUtil.image(getSpBIOS()));

        AncillaryServer.setFakeit(true);
        updateSp("123.45.67.89");
        updateGateway("123.45.67.1", "255.255.255.0");
        updateAll("123.45.67.89", "123.45.67.1", "255.255.255.0");
    }
}

