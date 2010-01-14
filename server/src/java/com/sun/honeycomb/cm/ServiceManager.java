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



package com.sun.honeycomb.cm;

import com.sun.honeycomb.cm.jvm_agent.CMAgent;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import java.util.Observer;

public class ServiceManager {

    // FIXME - this is not the right place for this
    static {
        System.loadLibrary("jcontract");
    }

    public static final int LOCAL_NODE = 0;
    /*
     * event notification channels
     */
    public static final int CMM_EVENT = 0x01;
    public static final int CONFIG_EVENT = 0x02;
    
    /**
     * return the node manager proxy object for the given node.
     * The node manager proxy object allows an application to 
     * access all services running in the cluster.
     * @param node is the node identifier
     * @return the proxy object for or <code>null</code>
     */
    static public NodeMgrService.Proxy proxyFor(int node) {
        return CMAgent.proxyFor(node);
    }

    /**
     * return the proxy object of the managed service running
     * on the given node.
     * @param node is the node identifier
     * @param tag the tag that uniquely identifies the service on the node.
     * @return the proxy object for this service or <code>null</code>
     * if the service does not exist.
     */ 
    static public ManagedService.ProxyObject proxyFor(int node, String tag) {
        return CMAgent.proxyFor(node, tag);
    }
    
    /**
     * return the proxy object of the managed service running
     * on the given node. The unique tag is deduced from the 
     * name of the managed service class.
     * @param node is the node identifier
     * @param managedService is the name of the class of the managed
     * service.
     * @return the proxy object for this service or <code>null</code>
     * if the service does not exist.
     */ 
    static public ManagedService.ProxyObject proxyFor(int node, Class cls) {
        return CMAgent.proxyFor(node, cls);
    }

    /**
     * return all the proxy objects of the given managed service class.
     * @param managedService is the name of the class of the managed
     * service.
     * @return all the proxy objects for this service.
     */
    static public ManagedService.ProxyObject[] 
        proxyFor(Class managedService) 
    {
        Node[] nodes = ServiceManager.proxyFor(
                            ServiceManager.LOCAL_NODE).getNodes();

        ManagedService.ProxyObject[] proxies = new ManagedService.
                                                ProxyObject[nodes.length];
        int numProxies = 0;
        for (int i=0; i < nodes.length; i++) {
            if (nodes[i].isAlive()) {
                proxies[numProxies++] = proxyFor(nodes[i].nodeId(), 
                                                 managedService);
            }
        } 

        // trim to contain only proxies for Alive nodes
        ManagedService.ProxyObject[] trim = new ManagedService.
                                                ProxyObject[numProxies];
        System.arraycopy(proxies, 0, trim, 0, numProxies);

        return trim;
    }

    /**
     * A managed service can explicitly request to publish a new proxy 
     * object by calling the <code>publish</code> method.
     * @param service is the managed service that requests to publish
     * a new proxy object.
     */
    static public void publish(ManagedService service) {
        CMAgent.publish(service);
    }

    /**
     * @return the ManagedService class into which the current thread
     * is executing or null if the service cannot be found.
     */
    static public Class currentManagedService() {
        return CMAgent.currentManagedService();
    }
    
    /**
     * Register for notification event.
     * CMM_EVENT for cluster membership notification
     */
    static public void register(int event, Observer observer) 
        throws CMAException {
        CMAgent.register(event, observer);
    }

    /*
     * This should be in the NodeMgr but because of the bad directory
     * naming (node_mgr containing underscore), the JNI can't be built
     * correctly, so here it is...
     */
    static public boolean initNodeMgr() {
        return openTemplateContract();
    }

    public static native boolean openTemplateContract();
    public static native boolean setResourcesLimit();
}
