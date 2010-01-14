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

 

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.contentpanels.PnlDNS;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JOptionPane;

/**
 *
 * @author jb127219
 */
public class DisableDNSHelper {
    
    private PnlDNS panel = null;
    
    private boolean disable = false;
    private int numHostNames = 0;
    
    // keeps track of host names successfully converted to IPs
    private StringBuffer msgBuffer = new StringBuffer();

    // keeps track of those host names that can't be resolved
    private StringBuffer errMsgBuffer = new StringBuffer();
    
    /** Creates a new instance of DisableDNSHelper */
    public DisableDNSHelper(PnlDNS pDNS) {
        panel = pDNS;
    }

    public void disableDNSCheck() {
        disable = false;
        try {
            if (!panel.isDNSSelected()) {
                // *************    disabling DNS   **************************
                // call into panel to resolve the host ids -- panel keeps
                // track of its own host ids
                panel.resolveHostIds();
                if (isServiceConfigured()) {
                    // there are some services which are configured using
                    // host names -- need to notify user that these host names
                    // must be resolved to IP addresses in order for DNS to
                    // be disabled
                    int confirmValue = JOptionPane.showConfirmDialog(
                                    MainFrame.getMainFrame(),
                                    GuiResources.getGuiString(
                                        "config.network.dns.disable.warning", 
                                        panel.getHostnameConfiguredServices()),
                                    GuiResources.getGuiString("app.name"),
                                    JOptionPane.OK_CANCEL_OPTION);
                    if (confirmValue == JOptionPane.OK_OPTION) {
                        try {
                            if (!areAllResolved()) {
                                // at least one host name couldn't be resolved
                                Log.logAndDisplay(Level.SEVERE, GuiResources.
                                        getGuiString(
                            "config.network.dns.hostName.resolution.notSaved",
                                        new String[] {msgBuffer.toString(), 
                                            errMsgBuffer.toString()}), null);
                                panel.setDNSSelected(true);
                            } else {
                                disable = true;
                            }
                        } catch (Exception ex) {
                            // if an exception is caught, then an error
                            // occured during loading or saving data -- if
                            // the host name can't be resolved, an exception
                            // is NOT thrown and it is handled above
                            Throwable t = null;
                            while (ex.getCause() != null) {
                                t = ex.getCause();
                            }
                            Log.logAndDisplayInfoMessage(
                                 GuiResources.getGuiString(
                                 "config.network.dns.hostName.resolution.error",
                                                        t.getMessage()));
                            panel.setDNSSelected(true);
                        }
                    } else {
                        // JOptionPane.CANCEL_OPTION -- reset value and do nada
                        panel.setDNSSelected(true);
                    }
                } else {
                    // there are no services which are configured using host
                    // names and thus, no resolution needs to be performed --
                    // simply disable DNS
                    disable = true;
                }
            } // dns disabled check
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }
    /**
     * This method returns the list of successfully converted host names
     * to IPs.
     *
     * @return List of successfully resolved host names.
     */
    public String getResolvedList() {
        return msgBuffer.toString();
    }
    /**
     * This method indicates whether or not DNS may be disabled after the
     * host ids are resolved and the conditional checks are made.
     *
     * @return Flag indicating if disabling DNS is allowed.
     */
    public boolean okToDisableDNS() {
        return disable;
    }
    /**
     * This method indicates whether or not there was at least one
     * service configured using host name(s)
     *
     * @return Flag indicating if there was at least one 
     *         service configured using host name(s)
     */
    public boolean isServiceConfigured() {
        // if at least one service is configured
        String hnConfigured = panel.getHostnameConfiguredServices();
        if (hnConfigured.length() > 0) {
            return true;
        }
        return false;
    }
    /**
     * This method indicates whether or not there was at least one host name
     * that COULD be resolved to an IP address.
     *
     * @return Flag indicating if there was at least one host name resolved
     *         to an IP address.
     */
    public boolean atLeastOneResolved() {
        // if at least one hostname was found and converted to an IP,
        // then the message buffer will have text in it
        return (msgBuffer.length() > 0) ? true : false;
    }
    /**
     * This method indicates whether or not there was at least one host name
     * that COULD NOT be resolved to an IP address.  If this is true, then DNS
     * cannot be disabled.
     *
     * @return Flag indicating if all host names could be resolved to IPs.
     */    
    public boolean areAllResolved() { 
        return errMsgBuffer.length() == 0;
    }
    
    /**
     * This method returns the number of host names in the set of host ids
     *
     * @return Number of host names for a given set of host ids.
     */
    public int getNumHostnames() {
        return numHostNames;
    }

    /**
     * This method is called to convert the host names to IPs in the set of host ids.
     * The array of host ids may consist solely of IP addresses, in which case
     * they are simply added to the new array and passed back to the caller.  In the case
     * where one or more host names are part of the hostIds array passed in, then the
     * return array may consist of a mix of IPs and host names depending on if all
     * host names could be resolved.
     *
     * @param hostIds Array of host id strings which may include host names and/or IP
     *                addresses.
     * @return Array of strings which will consist of either:
     *         (1)  original IP address
     *         (2)  new IP address (host name resolved to an IP)
     *         (3)  original host name (host name could not be resolved to IP)
     */
    public String[] getHostIPs(String[] hostIds) {
        // reinitialize -- counter keeps track for each conversion call
        numHostNames = 0;
        
        String[] hostsToCommit = new String[hostIds.length];
        String ipAddress = null;
        for (int i = 0; i < hostIds.length; i++) {
            String host = hostIds[i];
            ipAddress = convertHostname(host);
            if (ipAddress != null) {
                // was a hostname, which is converted now to IP address
                hostsToCommit[i] = ipAddress;
            } else {
                // wasn't a hostname
                hostsToCommit[i] = host;
            }
        } // end iterating through host Ids
        return hostsToCommit;
    }

    private String convertHostname(String value) {  
        String ip = null;
        if (!Validate.isValidIpAddress(value) && 
                                            Validate.isValidHostname(value) ) {
            numHostNames++;
            try {
                // hostname needs to be resolved
                InetAddress addr = InetAddress.getByName(value);
                ip = addr.getHostAddress(); 
                if (msgBuffer.length() > 0) {
                    msgBuffer.append("\n");
                }
                msgBuffer.append(GuiResources.getGuiString(
                        "config.network.dns.hostName.resolution.yes",
                            new String[] {value, ip}));
            } catch (UnknownHostException uhe) {
                if (errMsgBuffer.length() > 0) {
                    errMsgBuffer.append("\n");
                }
                errMsgBuffer.append(GuiResources.getGuiString(
                     "config.network.dns.hostName.resolution.no",
                                                value));
            }
        } 
        return ip;
    }
}
