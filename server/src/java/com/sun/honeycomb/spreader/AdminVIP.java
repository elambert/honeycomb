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



package com.sun.honeycomb.spreader;

import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.util.Exec;
import java.io.IOException;

import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * Configure adminVIP on the master node
 *
 * @author Shamim Mohamed
 * @version $Id: AdminVIP.java 10855 2007-05-19 02:54:08Z bberndt $
 */

class AdminVIP {

    private static final String PNAME_INSTALL_DIR =
        "honeycomb.prefixPath";
    private static final String PNAME_VIP_INTERNAL_ADMIN =
        "honeycomb.cell.vip_internal_admin";

    private static final Logger logger =
        Logger.getLogger(SpreaderService.class.getName());

    /**
     * Configure admin VIPs
     * There is an external VIP that is used for cell administration
     * An internal VIP that moves around with the master node is used
     * to simply configuration of services that run on the master node
     * such as ntp. 
     */
    public static synchronized void configureAdminVIPs(String ifName,
                                                       int switchType,
                                                       boolean start) {
        ClusterProperties conf = ClusterProperties.getInstance();
        logger.fine("Setting up admin VIPs...");

        Object obj = null;
        int retry = 0; 
        while (obj == null && !(obj instanceof PlatformService.Proxy)) {
            if (retry != 0) { 
                try {
                    logger.info ("sleeping and waiting for platform");
                    Thread.sleep (2000);
                }
                catch (Exception e) {
                    if (logger.isLoggable (Level.INFO)) {
                        logger.log (Level.INFO,
                                    "thread interrupted while waiting for" 
                                    + "PlatformService proxy");
                    }
                }
            }

            if (retry < 3) {
                obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE,
                                              "PlatformService");
                retry++;
            }
            else {
                throw new RuntimeException ("unable to acquire PlatformService proxy for Admin VIP");
            }
        }

        String adminVIP   = null;
        String network    = null;
        String scriptPath = conf.getProperty (PNAME_INSTALL_DIR);
        String subnet     = MultiCellLib.getInstance().getSubnet();
        String gateway    = MultiCellLib.getInstance().getGateway();
        String cmd = (start) ? "start" : "stop";

        if (logger.isLoggable (Level.FINE)) {
            logger.log(Level.FINE, "Read admin VIP " + adminVIP);
        }

        for (int i = 0; i < 2; i++) {
            String ifconfig_command="";

            if (i == 0) {

                /* Only configure the external admin VIP if we're not on
                   a Znyx switch. Otherwise the switch owns the admin VIP. */
                if (switchType == SpreaderService.SWITCH_ZNYX)
                    continue;

                adminVIP = MultiCellLib.getInstance().getAdminVIP();
                if (adminVIP == null) {
                    // This should never be null, since SpreaderService
                    // init checks and disabled itself if it is.
                    assert false;
                    continue;
                }

                // TODO: (rjw: not sure this is needed anymore?)
                // Check to see that no one else is using this VIP. If another
                // node is using the VIP, kill that node. This can happen if
                // an old master node is out of the cluster but didn't die 
                // cleanly so the admin VIP remains plumbed on the old master 
                // node.

                // Plumb/unplumb the admin VIP on the external network 
                // interface
                ifconfig_command = scriptPath +
                    "/bin/ifconfig_script.sh " +
                    ifName + " " +
                    adminVIP + " " +
                    subnet + " " + gateway + " " + 1 + " " + cmd
                    + " master";
            }
            else {
                adminVIP =
                    conf.getProperty(PNAME_VIP_INTERNAL_ADMIN);
                if (adminVIP == null) {
                    logger.info("Could not get internal_admin VIP from" +
                                " cluster config file. Using default " +
                                "10.123.45.200");
                    adminVIP = "10.123.45.200";
                }

                //Plumb/unplumb the internal admin VIP address
                // We assume the internal admin VIP is on the 10.123.45.X 
                // network which is private to the cluster.
                network = "10.123.45.0";
                subnet = "255.255.255.0";
                gateway = "10.123.45.1";
                ifconfig_command = scriptPath +
                    "/bin/ifconfig_internal.sh " + ifName +
                    " " + adminVIP + " " + subnet + " " + cmd;
            }

            try {
                Exec.exec (ifconfig_command);
            }
            catch (IOException e) {
                if (logger.isLoggable (Level.SEVERE)) {
                    logger.log(Level.SEVERE,
                               "Failed to configure admin VIP["
                               + i + "]"  + e.getMessage(), e);
                }
            }
        }
    }
}
