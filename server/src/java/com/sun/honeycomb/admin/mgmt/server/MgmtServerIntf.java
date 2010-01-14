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

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;

import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertException;


import java.util.logging.Logger;
import java.util.logging.Level;

public interface MgmtServerIntf
    extends ManagedService.RemoteInvocation, ManagedService {
       public int     getNumTasks () throws ManagedServiceException;
       public int     getTaskId (String name) throws ManagedServiceException;
       public String  getTaskName (int task) throws ManagedServiceException;
       public long    getTaskCompletionTime (int task);
       public long    getTaskNumFaults(int task) throws ManagedServiceException;
       public long    getPreviousCompletedCycleTime (int task) throws ManagedServiceException;
       public void    resetSysCache() throws ManagedServiceException;
       public long    getTaskSuperRunStart(int task) throws ManagedServiceException;
       public long    getTaskSuperCycleStart(int task) throws ManagedServiceException;
       public void    setSyscacheInsertFailureTime(long timestamp) throws ManagedServiceException;
       
    public class Proxy extends ManagedService.ProxyObject {
        private static transient final Logger logger = 
            Logger.getLogger(MgmtServerIntf.Proxy.class.getName());
        
        private long queryIntegrityTime;

        public Proxy (long queryIntegrityTime) {
            super();
            this.queryIntegrityTime = queryIntegrityTime;
        }

        public long getQueryIntegrityTime() {
            return queryIntegrityTime;
        }

        static public Proxy getProxy() {
            Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            if (! (obj instanceof NodeMgrService.Proxy)) {
                logger.severe("Can't get node manager sercice from mgmtServerInff utility class.");
                return null;
            }
            NodeMgrService.Proxy nodeMgr = (NodeMgrService.Proxy) obj;
            Node master = ((NodeMgrService.Proxy) obj).getMasterNode();


            if (master == null) {
                logger.severe("Can't get master node pointer.");
                return null;
            }
            ManagedService.ProxyObject proxy =
                ServiceManager.proxyFor(master.nodeId(), "MgmtServer");
            if(null==proxy)
                logger.severe("Proxy is null... that's baaaad.");
            if (! (proxy instanceof MgmtServerIntf.Proxy)) {
                logger.severe("Can't get MgmtServerIntf from master node.");
                return null;
            }
            return (MgmtServerIntf.Proxy) proxy;
        }

        static public MgmtServerIntf getMgmtServerAPI() {
            Proxy proxy = getProxy();
            if (proxy == null) {
                logger.severe("Null proxy returned.");
                return null;
            }
            Object api = proxy.getAPI();
            if (! (api instanceof MgmtServerIntf)) {
                logger.severe("Bad intf proxy returned.");
                return null;
            }
            return  (MgmtServerIntf) api;
        }        


        private static final AlertProperty[] alertProps = {
            new AlertProperty("cliAlerts", AlertType.STRING)
        };
        
        public AlertProperty getPropertyChild(int index)
            throws AlertException {
            try {
                return alertProps[index];
            }
            catch (ArrayIndexOutOfBoundsException e) {
                throw new AlertException("index " + index + "does not exist");
            }
        }
        public String getPropertyValueString(String property)
            throws AlertException {
            if (property.equals("cliAlerts")) {
                return "Dummy alert";
            } else {
                throw new AlertException("property " +
                                         property + " does not exit");
            }
        }


        
        public int getNbChildren() {
            return alertProps.length;
        }



   }
}
