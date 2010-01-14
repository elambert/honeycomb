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



package com.sun.honeycomb.admin.mgmt.server;

import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.diskmonitor.DiskControl;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.datadoctor.DataDocProxy;
import com.sun.honeycomb.datadoctor.DataDoctor;
import com.sun.honeycomb.protocol.server.ProtocolProxy;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.admin.mgmt.ClusterManagement;
import com.sun.honeycomb.multicell.MultiCellIntf;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.util.ServiceProcessor;
import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.mgmt.common.MgmtException;

import java.math.BigInteger;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.*;
import java.io.*;


public class HCVersionsAdapter
    implements HCVersionsAdapterInterface {
    private int                  numNodes;
    private MgmtServer           mgmtServer;
     private static transient final Logger logger = 
         Logger.getLogger(HCVersionsAdapter.class.getName());

    static final private String HC_VERSION_FILE  = "/opt/honeycomb/version";


    public void loadHCVersions() throws InstantiationException {
        mgmtServer = MgmtServer.getInstance();
        numNodes= Utils.getNumNodes();
    }

    /*
    * This is the list of accessors to the object
    */

    public String getSpBios() throws MgmtException {
        if (ServiceProcessor.isSPAlive()) {
            return ServiceProcessor.getSpBIOS();
        } else {
            return "unavailable";
        }
    }

    public String getSpSmdc() throws MgmtException {
        if (ServiceProcessor.isSPAlive()) {
            return ServiceProcessor.getSpSMDC();
        } else {
            return "unavailable";
        }
    }

    public String getSwitchOneOverlay() throws MgmtException {
        return Switch.getVersion(1);
    }

    public String getSwitchTwoOverlay() throws MgmtException {
        if (Switch.isBackupSwitchAlive()) {
            return Switch.getVersion(2);
        } else {
            return "unavailable";
        }
    }

    public void populateBios(List<String> array) throws MgmtException {
        for (int i = 0; i < numNodes; i++) {
            int node = 101 + i;
            String prop = "root." + node + ".PlatformService.";
            try {
                AlertApi.AlertViewProperty alertView = mgmtServer.getAlertView();
                if (alertView != null) {
                    AlertApi.AlertObject o = alertView.getAlertProperty(prop +
                                                    "biosInfo.BIOSVersion");
                    array.add(o.getPropertyValueString());

                }
            } catch (AlertException e) {
                array.add("");
                mgmtServer.logger.log(Level.SEVERE,
                           "Error while retrieving the firmware versions" +
                           e.getMessage(), e);
            }
        }
    }

    public void populateSmdc(List<String> array) throws MgmtException {
        for (int i = 0; i < numNodes; i++) {
            int node = 101 + i;
            String prop = "root." + node + ".PlatformService.";

            try {
                AlertApi.AlertViewProperty alertView = mgmtServer.getAlertView();
                if (alertView != null) {
                    AlertApi.AlertObject o = alertView.getAlertProperty(prop +
                      "ipmiFwVersion");
                    array.add(o.getPropertyValueString());

                }
            } catch (AlertException e) {
                array.add("");
                mgmtServer.logger.log(Level.SEVERE,
                           "Error while retrieving the firmware versions" +
                           e.getMessage(), e);
            }
        }
    }

    public String getVersion() throws MgmtException {
    
        StringBuffer version = new StringBuffer("");
        File version_file = new File (HC_VERSION_FILE);
        if (! version_file.exists()) {
            mgmtServer.logger.warning (HC_VERSION_FILE
              + " does not exist. Unable to determine version");
            version.append (" Development Build");
        } else {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(version_file));
                // the 1st line of the version file looks like:
                // Honeycomb release [A.V-XYZ]
                String line = in.readLine();
                version.append(line);
            } catch (Exception e) {
                mgmtServer.logger.log (Level.SEVERE,
                  "Error reading " + HC_VERSION_FILE + ": "
                  + e.getMessage(), e);
            }
            finally {
                if (in != null) {
                    try { in.close(); } catch (Exception e) { ; }
                }
            }
        }
        logger.info("Got version.");
        return version.toString();
    }
}

