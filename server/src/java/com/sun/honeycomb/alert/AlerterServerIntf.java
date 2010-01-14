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


package com.sun.honeycomb.alert;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import java.io.IOException;
import java.util.Hashtable;

//
// Interface for Alerter Server Service.
//
public interface AlerterServerIntf
    extends ManagedService.RemoteInvocation, ManagedService {

    // RMI calls
        
    public AlertApi.AlertViewProperty getViewProperty() 
        throws ManagedServiceException;

    /**
     * Retrieve the specified list of <code>props</code> from the alert tree
     * for the given <code>nodes</code>.
     * @param nodeId the id of the node to retrieve properties from
     * @param props the array of properties to return.  
     * @return Hashtable of AlertApi.AlertObject of the properties retrieved.
     * If a property is not found in the alert tree it will not be added to
     * the Hashtable.
     * @throws AlertException
     * @throws ManagedServiceException
     */
    public Hashtable[] getClusterAlertProperties(Node[] nodes, String[] props)
        throws AlertException, ManagedServiceException;
    
    /**
     * Retrieve the specified list of <code>props</code> from the alert tree
     * for the given node.
     * @param nodeId the id of the node to retrieve properties from
     * @param props the array of properties to return
     * @return Hashtable of AlertApi.AlertObject of the properties retrieved.
     * If a property is not found in the alert tree it will not be added to
     * the Hashtable.
     * @throws AlertException
     * @throws ManagedServiceException
     */
    public Hashtable getNodeAlertProperties(int nodeId, String[] props)
        throws AlertException, ManagedServiceException;
    
    /**
     * Retrieve the specified property from the alert tree
     * @return AlertApi.AlertObject the alert object for the specified property
     * @throws AlertException if the requested property is not found
     * @throws ManagedServiceException
     */
    public AlertApi.AlertObject getProperty(String property)
        throws AlertException, ManagedServiceException; 
    
    public void notifyClients(String prop, String msg)
        throws AlertException, ManagedServiceException;
        
    
    // Proxy definition
    
    public class Proxy extends ManagedService.ProxyObject {

        public Proxy () {
            super();
        }
        
        static public AlerterServerIntf getServiceAPI() {
            ManagedService.ProxyObject proxy;
            proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE, 
                                            AlerterServer.class
                                            );
            if (!(proxy instanceof Proxy)) {
                return null;
            }
            ManagedService.RemoteInvocation api = proxy.getAPI();
            if (!(api instanceof AlerterServerIntf)) {
                return null;
            }
            return (AlerterServerIntf) api;
        }
    }
}
