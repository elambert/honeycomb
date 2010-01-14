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



package com.sun.honeycomb.webdav;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.connectors.HCGlue;
import com.sun.honeycomb.protocol.server.ProtocolProxy;

import java.io.IOException;

import java.util.Map;
import java.util.Iterator;
import java.util.Properties;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

public interface WebdavServiceAPI
    extends ManagedService {
    // Nothing to export

    public class Proxy 
        extends ManagedService.ProxyObject {

        private String putPerf = "null";
        private String putOpsStr = "null";

        private String getPerf = "null";
        private String getOpsStr = "null";
 
        private transient static final Logger logger = 
            Logger.getLogger(Proxy.class.getName());


        //////////////////////////////////////////////////////////////////////
        // Static methods

        /** Get current Proxy for given node, null if failed. */
        static public Proxy getProxy(int nodeId) {
	
            ManagedService.ProxyObject proxy = 
                ServiceManager.proxyFor(nodeId, HCDAV.class);

            if (proxy == null || ! (proxy instanceof Proxy)) {
                // okay for proxy to be null, node might be down or booting 
	        logger.warning("proxy for node " + nodeId + " is of the wrong kind or is null");
                return null;
            }

            return (Proxy) proxy;
        }

	/** Get current Proxy for local node, null if failed. */
	static public Proxy getProxy() {

	    NodeMgrService.Proxy proxy = getNodeMgrProxy();
	    return getProxy(proxy.nodeId());
	}

	/** Get all WebdavProxies in the cluster, null if failed. */
	static public Proxy[] getProxies() {

	    NodeMgrService.Proxy nodeMgrProxy = getNodeMgrProxy();

	    Node[] nodes = nodeMgrProxy.getNodes();
	    Proxy[] proxies = new Proxy[nodes.length];

	    int j = 0;
	    for (int i = 0; i < nodes.length; i++) {
		Proxy proxy = null;

		if (nodes[i].isAlive && 
		    (proxy = getProxy(nodes[i].nodeId())) != null) {
		    proxies[j++] = proxy;
		}
	    }
	    if (j == 0)
		return null;

	    Proxy[] newArray = new Proxy[j];
	    System.arraycopy(proxies, 0, newArray, 0, j);
	    return newArray;
	}


	static private NodeMgrService.Proxy getNodeMgrProxy() {
	    NodeMgrService.Proxy proxy =
		ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
	    return proxy;
	}
        
        public Proxy() {
            /*
             * Webdav Put Stats
             */ 
            putPerf = HCGlue.putStats.getStatsStr(); 
 
            /*
             * Webdav Get Stats
             */  
            getPerf = HCGlue.getStats.getStatsStr(); 

            // This is only for Debugging Purposes
            /*
            logger.info("Put B/s: " +putPerf +" Put Ops: " +putOpsStr); 
            logger.info("Get B/s: " +getPerf +" Get Ops: " +getOpsStr);
            */ 
        }
        
        /*
	 * Alert API
	 */
	public int getNbChildren() {
	    return 2;
	}

	public AlertProperty getPropertyChild(int index) 
	    throws AlertException {
	    AlertProperty prop = null;
	    
	    switch(index) {
	    case 0:
		prop = new AlertProperty(ProtocolProxy.WEBDAV_PUT_BW, AlertType.STRING);
		break;
	    case 1:
		prop = new AlertProperty(ProtocolProxy.WEBDAV_GET_BW, AlertType.STRING);
		break;
	    default:
		throw new AlertException("index " + index + " out of bound");
	    }
	    return prop;
	}

	public String getPropertyValueString(String property)  
	    throws AlertException {
	    if (property.equals(ProtocolProxy.WEBDAV_PUT_BW)) {
		return putPerf;
	    } else if (property.equals(ProtocolProxy.WEBDAV_GET_BW)) {
		return getPerf;
	    } else {
		throw new AlertException("property " + property +
					     " does not exist");                
	    }
	}
    } 
}

