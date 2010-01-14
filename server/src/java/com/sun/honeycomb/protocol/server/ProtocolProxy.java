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



package com.sun.honeycomb.protocol.server;

import java.io.IOException;
import java.util.Map;
import java.util.Iterator;
import java.util.Properties;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.oa.OAStats;


/**
 * The proxy of the Protocol service. It allows delete to clear all
 * caches and will later publish statistics.
 */
public class ProtocolProxy extends ManagedService.ProxyObject {

    private transient static final Logger logger = 
      Logger.getLogger(ProtocolProxy.class.getName());
    
    static public final String STORE_BW = "storeBandwidth";
    static public final String STORE_BOTH_BW = "storeBothBandwidth";
    static public final String STORE_MD_BW = "storeMDBandwidth";
    static public final String STORE_MD_SIDE_TIME = "storeMDSideTime";
    static public final String RETRIEVE_BW = "retrieveBandwidth";
    static public final String RETRIEVE_MD_BW = "retrieveMDBandwidth";
    static public final String QUERY_TIME = "queryTime";
    static public final String DELETE_TIME = "deleteTime";
    static public final String GET_SCHEMA_TIME ="getschemaTime";
    static public final String SEL_UNIQ_TIME = "seluniqTime";
    static public final String OA_STATS = "OAStats";
    static public final String WEBDAV_PUT_BW = "WebDAVPutBandwidth";
    static public final String WEBDAV_GET_BW = "WebDAVGetBandwidth";

    private OAStats oaStats = null;
    private String storePerf = null;
    private String storeBothPerf = null;
    private String storeMDPerf = null;
    private String storeMDSidePerf = null;
    private String retrievePerf = null;
    private String retrieveMDPerf = null;
    private String queryPerf = null;
    private String deletePerf = null;
    private String getschemaPerf = null;
    private String seluniqPerf = null;
    private String storeOpsStr = null;
    private String storeBothOpsStr = null;
    private String storeMDOpsStr = null;
    private String retrieveOpsStr = null;
    private String retrieveMDOpsStr = null;

    private boolean acceptRequests;


    /** Get current ProtocolProxy for given node, null if failed. */
    static public ProtocolProxy getProxy(int nodeId) {
	
        ManagedService.ProxyObject proxy = 
          ServiceManager.proxyFor(nodeId, ProtocolService.class);

        if (proxy == null || ! (proxy instanceof ProtocolProxy)) {
            logger.warning("Bad proxy for node " + nodeId +
			   ", proxy = " + StringUtil.image(proxy));
            return null;
        }
        return (ProtocolProxy) proxy;
    }

    /** Get current DiskProxy for local node, null if failed. */
    static public ProtocolProxy getProxy() {

        NodeMgrService.Proxy proxy = getNodeMgrProxy();
        if (proxy == null) {
            return null;
        }
        return getProxy(proxy.nodeId());
    }

    /** Get all ProtocolProxies in the cluster, null if failed. */
    static public ProtocolProxy[] getProxies() {

        NodeMgrService.Proxy proxy = getNodeMgrProxy();
        if (proxy == null) {
            return null;
        }

        Node[] nodes = proxy.getNodes();
        ProtocolProxy[] proxies = new ProtocolProxy[nodes.length];

        int j = 0;
        for (int i = 0; i < nodes.length; i++) {
            ProtocolProxy protocolProxy = null;

            if (nodes[i].isAlive && 
              (protocolProxy = getProxy(nodes[i].nodeId())) != null) {
                proxies[j++] = protocolProxy;
            }
        }
        if (j == 0) {
            return null;
        }

        ProtocolProxy[] newArray = new ProtocolProxy[j];
        System.arraycopy(proxies, 0, newArray, 0, j);
        return newArray;
    }


    static private NodeMgrService.Proxy getNodeMgrProxy() {

        NodeMgrService.Proxy proxy =
          ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        if (proxy == null) {
            logger.severe("couldn't get node manager proxy");
        }
        return proxy;
    }

     
    ProtocolProxy() {
            super();
            initStatistics();
            initAcceptRequestState();
    }
    
    private void initAcceptRequestState() {

        acceptRequests = false;

        NodeMgrService.Proxy nodeMgrProxy =
          ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (nodeMgrProxy == null) {
            return ;
        }

        //
        // Retrieve the view from the layout
        //
        LayoutProxy layoutProxy = LayoutProxy.getLayoutProxy();
        if (layoutProxy == null) {
            return;
        }
        if (!layoutProxy.isReady()) {
            logger.info("Layout is not RUNNING, can't accept API requests");
            return;
        }


        DiskMask diskMask = layoutProxy.currentDiskMask();
        if (diskMask == null) {
            logger.warning("Layout proxy returns a null diskMask");
            return;
        }
        int layoutView = diskMask.enabledDisks();

        if (layoutView >= nodeMgrProxy.getMinDiskQuorum()) {
            acceptRequests = true;
        } else { 
            acceptRequests = false;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ProtocolProxy layoutView = " + layoutView + 
              ", minimum required = " + nodeMgrProxy.getMinDiskQuorum() + 
              ", acceptAPIRequests = " + acceptRequests);
        }
    }


    private void initStatistics() {

        storePerf = StoreHandler.storeStats.getStatsStr();	    // BandwidthStatsAccumulator
        storeBothPerf = StoreHandler.storeBothStats.getStatsStr();  // BandwidthStatsAccumulator
        storeMDPerf = StoreHandler.storeMDStats.getStatsStr();	    // BandwidthStatsAccumulator
        storeMDSidePerf = StoreHandler.storeMDSideStats.getStatsStr(); // StatsAccumulator
        retrievePerf = RetrieveHandler.retrieveStats.getStatsStr();     // BandwidthStatsAccumulator
        retrieveMDPerf = RetrieveHandler.retrieveMDStats.getStatsStr(); // BandwidthStatsAccumulator
        queryPerf = QueryHandler.queryStats.getStatsStr();	    // StatsAccumulator
        deletePerf = DeleteHandler.deleteStats.getStatsStr();	    // StatsAccumulator
        getschemaPerf = GetConfigurationHandler.getschemaStats.getStatsStr();  // StatsAccumulator
        seluniqPerf = SelectUniqueHandler.selUniqStats.getStatsStr();  // StatsAccumulator
        oaStats = new OAStats();

        /*
        logger.info(
	    new StringBuffer("Store Only: ").append(storePerf) 
		.append(", Store MD: ").append(storeMDPerf)
		.append(", Store Both: ").append(storeBothPerf)
		.append(", Retrieve: ").append(retrievePerf)
		.append(", Retrieve MD: ").append(retrieveMDPerf)
		.append(", Query: ").append(queryPerf)
		.append(", Delete: ").append(deletePerf)
		.append(", Get Schema: ").append(getschemaPerf)
		.append(", Select Unique: ").append(seluniqPerf).toString());
	 */
    }

        
    
    public boolean isAPIReady() {
        return acceptRequests;
    }

    public boolean apiCallback(int event,
      NewObjectIdentifier oid,
      NewObjectIdentifier dataOid) {

        ProtocolManagedService api = null;
	
        if (!(getAPI() instanceof ProtocolManagedService)) {
            logger.
              warning("API is not of type ProtocolManagedService deleting" + 
                oid);
            return false;
        }
        api = (ProtocolManagedService) getAPI();	
        if (api == null) {
            logger.warning("ProtocolManagedService API is null deleting " + 
              oid);
            return false;
        }
	
        try {
            return api.apiCallback(event, oid, dataOid);
        } catch (ManagedServiceException mse) {
            logger.warning("proxy failed to rpc: " + mse);
            return false;
        }
    }

    /*
     * Alert API
     */
    public int getNbChildren() {
        return 11;
    }

    public AlertProperty getPropertyChild(int index) 
        throws AlertException {
        AlertProperty prop = null;
        
    switch(index) {
        case 0:
            prop = new AlertProperty(STORE_BW, AlertType.STRING);
            break;
        case 1:
            prop = new AlertProperty(STORE_BOTH_BW, AlertType.STRING);
            break;
        case 2:
            prop = new AlertProperty(STORE_MD_BW, AlertType.STRING);
            break;
	case 3:    
	    prop = new AlertProperty(STORE_MD_SIDE_TIME, AlertType.STRING);
	    break;
        case 4:
            prop = new AlertProperty(RETRIEVE_BW, AlertType.STRING);
            break;
        case 5:
            prop = new AlertProperty(RETRIEVE_MD_BW, AlertType.STRING);
            break;
        case 6:
            prop = new AlertProperty(QUERY_TIME, AlertType.STRING);
            break;
        case 7:
            prop = new AlertProperty(DELETE_TIME, AlertType.STRING);
            break;
        case 8:
            prop = new AlertProperty(GET_SCHEMA_TIME, AlertType.STRING);
            break;
        case 9:
            prop = new AlertProperty(SEL_UNIQ_TIME, AlertType.STRING);
            break;
        case 10:
            prop = new AlertProperty(OA_STATS, AlertType.COMPOSITE);
            break;
        default:
            throw new AlertException("index " + index + " out of bound");
        }
        return prop;
    }

    public String getPropertyValueString(String property)  
        throws AlertException {
         if (property.equals(STORE_BW)) {
            return storePerf;
        } else if (property.equals(STORE_BOTH_BW)) {
            return storeBothPerf;
        } else if (property.equals(STORE_MD_SIDE_TIME)) {
            return storeMDSidePerf;
        } else if (property.equals(RETRIEVE_BW)) {
            return retrievePerf;
        } else if (property.equals(QUERY_TIME)) {
            return queryPerf;
        } else if (property.equals(DELETE_TIME)) {
            return deletePerf;
        } else if (property.equals(GET_SCHEMA_TIME)) {
            return getschemaPerf;
        } else if (property.equals(SEL_UNIQ_TIME)) {
            return seluniqPerf;
        } else if (property.equals(RETRIEVE_MD_BW)) {
            return retrieveMDPerf;
        } else if (property.equals(STORE_MD_BW)) {
            return storeMDPerf;
        } else {
            throw new AlertException("property " + property +
              " does not exist");                
        }
    }

    public AlertComponent getPropertyValueComponent(String property)
        throws AlertException {
        if (property.equals(OA_STATS)) {
            return oaStats;
        } else {
            throw new AlertException("property " + property +
              " does not exist");
        }
    }
}

