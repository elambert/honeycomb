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



package com.sun.honeycomb.admin.mgmt;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.MissingResourceException;

import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.datadoctor.DataDocProxy;
import com.sun.honeycomb.datadoctor.DataDoctor;
import com.sun.honeycomb.protocol.server.ProtocolProxy;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.alert.AlerterServerIntf;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.common.CliConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class Utils {
    
    static final private String hadb_cmd = 
        "/opt/SUNWhadb/4/bin/hadbm status honeycomb";


    private static final Logger logger =
        Logger.getLogger(Utils.class.getName());

    static final public int    NODE_BASE        = 101;


    static public byte getCellid() {
        ClusterProperties config = ClusterProperties.getInstance();
	try {
	    return Byte.parseByte(config.getProperty(ConfigPropertyNames.PROP_CELLID));
	}
	catch (Exception e) {
	    logger.log(Level.SEVERE, "Can't get cell id ", e);
	    return 0;
	}
    }

    static public AlerterServerIntf getAlertServerAPI() throws AdminException {
        AlerterServerIntf alertApi;
        alertApi = AlerterServerIntf.Proxy.getServiceAPI();
        if (alertApi == null) {
            logger.severe("can't retrieve alert API");
            throw new AdminException("can't retrieve alert API");
        }
        return alertApi;
    }
    
    static public NodeMgrService.Proxy getNodeMgrProxy() throws AdminException {
        return getNodeMgrProxy(ServiceManager.LOCAL_NODE);
    }

    static public NodeMgrService.Proxy getNodeMgrProxy(int nodeid) 
        throws AdminException {
        Object obj = ServiceManager.proxyFor (nodeid);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            logger.warning ("NodeMgrService.Proxy not available");
            throw new AdminException(
                "unable to acquire to node manager proxy");
        }
        return ((NodeMgrService.Proxy) obj);
    }
    

    static public DataDocProxy getDataDocProxy()
        throws AdminException {
        DataDocProxy ddProxy = DataDocProxy.getDataDocProxy(
                                ServiceManager.LOCAL_NODE);
        if (ddProxy == null) {
            logger.warning ("DataDoc proxy not available");
            throw new AdminException(
                 "unable to acquire to datadoc proxy");
        }
        return ddProxy;
    }

    static public ProtocolProxy getProtocolProxy(int nodeid) 
        throws AdminException {
        ProtocolProxy proxy = ProtocolProxy.getProxy(nodeid);
        if (proxy == null) { 
            logger.warning ("DataDoc proxy not available"); 
            throw new AdminException(
                 "unable to acquire to protocol proxy");
        }
        return proxy;
    }


    // This is true because MgmtServer is running on master node.
    static public int getMasterNodeId() {
        return ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).nodeId();
    }

    static public Node[] getNodes() throws AdminException {
        Node[] nodes = null;
        nodes = getNodeMgrProxy(ServiceManager.LOCAL_NODE).getNodes();
        return nodes;
    }

    static public int getNumNodes() {
        return ClusterProperties.getInstance().
            getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
    }

    static public DiskMask getCurrentDiskMask() {
        return LayoutProxy.getCurrentDiskMask();
    }

    static public Disk [] getDisks() throws AdminException {
        int numNodes = getNumNodes();
        Disk [] res = new Disk[getDisksPerNodes() * numNodes];
        for (int i = 0; i < numNodes; i++) {
                Disk [] disks = getDiskMonitorProxy().getDisks(NODE_BASE + i);
            if(null==disks) {
                for (int j = 0; j < getDisksPerNodes(); j++) {
                    res[(i * getDisksPerNodes()) + j] = null;
                }
            } else {
                for (int j = 0; j < disks.length; j++) {
                    res[(i * getDisksPerNodes()) + j] = disks[j];
                }
            }
        }
        return res;
    }

    static public Disk [] getDisksOnNode(int nodeId) throws AdminException {

        Disk [] res = new Disk[getDisksPerNodes()];
        Disk [] disks = getDiskMonitorProxy().getDisks( nodeId);
        if(null==disks) {

            for (int j = 0; j < getDisksPerNodes(); j++) {
                res[j] = null;
            }
        } else {

            for (int j = 0; j < disks.length; j++) {
                res[j] = disks[j];
            }
        }
        return res;
    }

    static public int getDisksPerNodes() {
        return HardwareProfile.getProfile().getNumDisks();
    }


    static public DiskProxy getDiskMonitorProxy() throws AdminException {
        return getDiskMonitorProxy(ServiceManager.LOCAL_NODE);
    }

    static public DiskProxy getDiskMonitorProxy(int nodeid) 
        throws AdminException {
        Object obj = ServiceManager.proxyFor(nodeid, "DiskMonitor");
        if (! (obj instanceof DiskProxy)) {
            logger.warning ("DiskProxy not available");
            throw new AdminException (
                "unable to acquire to disk monitor proxy");
        }
        return ((DiskProxy) obj);
    }

    static public String getCSVFromList(List<String> myList) {
        String output=null;
        Iterator<String> iter = myList.iterator();
        while (iter.hasNext()) {

            String curVal=(String) iter.next();
            if(null==output) {
                output=curVal;
            } else {
                output=output+","+curVal;
            }
            
        }
        if (null==output) {
            return "";
        }
        return output;
    }


    static public List<String> getListFromCSV(String myCsv) {
        List<String> output = new ArrayList();
        if(null==myCsv) 
            return output;
        String[] fields = myCsv.split(",");
        if (null==fields) 
            return output;
        for(int i=0;i<fields.length;i++) {
            output.add(fields[i]);
        }
        return output;
    }

    static public void notifyChangeCli(String msg) {
        int masterNodeId = getMasterNodeId();
        String prop = "root." + masterNodeId + ".MgmtServer.cliAlerts";

        try {
            getAlertServerAPI().notifyClients(prop, msg);
        } catch (Exception ae) {
            logger.warning("failed to notify AlertClients about CLI change -"+
                           " property not yet registered? " + ae);
        }
    }
    static public String getLocalString (String key) {
        String res = null;
        try {
            res = BundleAccess.getInstance().getBundle().getString(key);
        } catch (MissingResourceException mr) {
            logger.severe("missing resource " + key + "in configuration file");
        }
        return res;
    }
    /**
     * used to send the contents of an arbitrarily large buffer through the mgnt
     * interface.
     */
    
    static public int sendBuffer(StringBuffer buffer, EventSender evt){
   
        int nbEvents = buffer.length() / 
          CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
        nbEvents = 
          (buffer.length() % CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE == 0) ?
          nbEvents : (nbEvents + 1);
  
        int start = 0;
        int end = CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
        for (int i = 0; i < nbEvents; i++) {
            String bufferPiece = null;
            if (i < (nbEvents - 1)) {
                bufferPiece = buffer.substring(start, end);
              } else {
                  bufferPiece = buffer.substring(start);
            }
            try {
                evt.sendSynchronousEvent(bufferPiece);
            } catch (Exception ignore) {
                logger.severe("failed to send the async " + 
                  "event ");
                return -1;
            }
            start = end;
            end += CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
        }
        return 0;
    }


}
