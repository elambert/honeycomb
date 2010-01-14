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



package com.sun.honeycomb.stressconfig;

import java.io.IOException;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ManagedService;

public interface ConfigStresserIntf 
    extends ManagedService.RemoteInvocation, ManagedService {

    // Call by Adapter for the CLI.
    public int startConfigUpdate(long delay, 
      boolean createConfig, boolean nodeFailure, int rateFailure)
        throws IOException;
    public String stopConfigUpdate(int executorId) throws IOException;

    // Call by CMM
    public void resetFailure(long version) throws IOException;

    public class Proxy extends ManagedService.ProxyObject {

        static public ConfigStresserIntf getConfigStresserAPI() {
            Proxy proxy = getProxy();
            if (proxy == null) {
                return null;
            }
            Object api = proxy.getAPI();
            if (! (api instanceof ConfigStresserIntf)) {
                return null;
            }
            return (ConfigStresserIntf) api;
        }

        static public Proxy getProxy() {
            Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            if (! (obj instanceof NodeMgrService.Proxy)) {
                return null;
            }
            NodeMgrService.Proxy nodeMgr = (NodeMgrService.Proxy) obj;
            Node master = ((NodeMgrService.Proxy) obj).getMasterNode();
            if (master == null) {
                return null;
            }
            ManagedService.ProxyObject proxy =
              ServiceManager.proxyFor(master.nodeId(), "ConfigStresser");
            if (! (proxy instanceof ConfigStresserIntf.Proxy)) {
                return null;
            }
            return (ConfigStresserIntf.Proxy) proxy;
        }

        public Proxy() {
            super();
        }
    }

}