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



package com.sun.honeycomb.cm.node_mgr;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.jvm_agent.JVMService;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.common.SoftwareVersion;
import com.sun.honeycomb.util.Exec;
import java.util.logging.Logger;
import java.util.logging.Level;

public interface NodeMgrService extends JVMService {

    public static final String mboxTag = "NODE-SERVERS";

    /*
     * argument for startServices
     * This can be extended to run various type of services
     * like diagnostics/recovery or repairs.
     */
    public static final int INIT_SERVICES       = 0;
    public static final int PLATFORM_SERVICES   = 1;
    public static final int IO_SERVICES         = 2;
    public static final int MASTER_SERVICES     = 3;
    public static final int ALL_MASTER_SERVICES = 4;
    public static final int DIAGNOSE_SERVICES   = 5;
    public static final int API_SERVICES        = 6;
    public static final int ALL_SERVICES        = -1;

    /**
     * start the node
     */
    void start() throws ManagedServiceException;

    /**
     * stop the node
     */
    boolean stopAllServices() throws ManagedServiceException;

    /**
     * reboot the node gracefully
     */
    void reboot() throws ManagedServiceException;

    /**
     * turn the node off, either gracefully or using impi 
     * to force a powerdown
     */
    void powerOff(boolean useIpmi) 
        throws MgrException, ManagedServiceException;

    /**
     * set the log level for this JVM
     */
    void setLogLevel(String level)
        throws ManagedServiceException;

    /**
     * force the node into maintenance mode. Calling this function with a
     * true value will cause the node to enter maintenance mode and remain in
     * this state, regardless of quorum. The node will stay in this state
     * until either (1) it is removed from this mode by calling this function
     * again with false, or a reboot.
     * <br><br>
     * Note that calling this function with false will not always trigger
     * restoration of data services if the node is not ready to start them.
     */
    public boolean forceMaintenanceMode (boolean force)
        throws ManagedServiceException;

    /**
     * reboot the cluster - can be forced if the impi flag is high.
     * switches will be rebooted if the reboot switches flag is high.
     */
    public void rebootCell(boolean rebootSwitches) 
        throws MgrException, ManagedServiceException;


    /**
     * shut down the cluster - can be forced if the impi flag is high.
     * switches will be rebooted if the reboot switches flag is high.
     */
    public void shutdownCell(boolean useIpmi, boolean rebootSwitches) 
        throws MgrException, ManagedServiceException;


    /**
     * Node manager proxy object.
     */
    public class Proxy extends ManagedService.ProxyObject {

        int        nodeid;
        Node[]     nodes;
        Service[]  services;
        boolean    isMaint;
        boolean    hasQuorum;
        boolean    isRebooting;
        int        minDiskQuorum;

        Proxy(int nodeid) {
            this.nodeid = nodeid;
            nodes    = null;
            services = null;
            minDiskQuorum = CMM.getMinDiskNum();
        }
        
        public boolean isMaintenance() {
            return isMaint;
        }

        public boolean hasQuorum() {
            return hasQuorum;
        }

        public boolean isRebooting() {
            return isRebooting;
        }

        public int nodeId() {
            return nodeid;
        }

        public Node[] getNodes() {
            return nodes;
        }

        public int getNumNodes() {
            return getNodes().length;
        }

        public int getMinDiskQuorum() {
            return minDiskQuorum;
        }

        /**
         * @return The node that is hosting this proxy's service
         * or null of the given nodeid is not found
         */
        public Node getNode() {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].nodeId() == nodeid) {
                    return nodes[i];
                }
            }
            return null;
        }


        /**
         * @return The node with the specified hostname
         * or null of the given nodeid is not found
         */
        public Node getNode(String hostname) {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].getName().equals(hostname)) {
                    return nodes[i];
                }
            }
            return null;
        }

        /**
         * @return the number of alive nodes in the cluster.
         */
        public int nodesAliveCount() {
            int count = 0;
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].isAlive()) {
                    count++;
                }
            }
            return count;
        }

        /**
         * @return the master node
         */
        public Node getMasterNode() {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].isMaster()) {
                    return nodes[i];
                }
            }
            return null;
        }

        /**
         * @return the vice master node
         */
        public Node getViceMasterNode() {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].isViceMaster()) {
                    return nodes[i];
                }
            }
            return null;
        }


        public Service[] getServices() {
            return services;
        }

        /**
         * return the service corresponding to the given tag.
         */
        public Service getService(String tag) {
            for (int i = 0; i < services.length; i++) {
                if (services[i].getTag().equals(tag)) {
                    return services[i];
                }
            }
            return null;
        }

        /**
         * return the service tag for the given class.
         */
        public String getTagByClass(Class cls) {
            String name = cls.getName();
            for (int i = 0; i < services.length; i++) {
                if (services[i].getClassName().equals(name)) {
                    return services[i].getTag();
                }
            }
            return null;
        }

        public boolean isRunning() {
            for (int i = 0; i < services.length; i++) {
                if (services[i].isManaged() && !services[i].isRunning()) {
                    return false;
                }
            }
            return true;
        }

        public String getHostname() {
            assert (nodes != null);
            assert (nodes.length > 0);
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].nodeId() == nodeid) {
                    return nodes[i].getAddress();
                }
            }
            assert (false);
            return null;
        }

        public void remoteExec(String tst, Logger log) {
            try {
                Exec.exec(tst, log);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Problems execing: " + tst, e);
            }
        }
        
        /**
         * Get the proxy for NodeMgrService for the specified node
         * @param nodeId the id of the node to return the proxy for
         * @return NodeMgrService.Proxy the proxy for the specified node,
         * returns null if not available
         */
        public static NodeMgrService.Proxy getProxy(int nodeId) {
            Object obj = ServiceManager.proxyFor(nodeId);
            if (obj instanceof NodeMgrService.Proxy) 
                return (NodeMgrService.Proxy) obj;
            return null;
        }

        /**
         * Fetch a handle to the api interface for the specified node
         * @param nodeId the id of the node to return the handle to the api for
         * @return NodeMgrService the api handle, returns null if not available
         */
        public static NodeMgrService getAPI(int nodeId) {
            NodeMgrService.Proxy proxy = getProxy(nodeId);
	    if (proxy == null)
		return null;
	    ManagedService.RemoteInvocation api = proxy.getAPI();
            if (api instanceof NodeMgrService)
		return (NodeMgrService)api;
            return null;	
        }

        // private
        private static final long serialVersionUID =
            SoftwareVersion.serializeUID;

        /*
         * Alert API - exports the following properties
         *
         * nodeid
         * diskcount
         * hostname
         * isRunning
         * isEligible
         * isMaster
         * isVicemaster
         * isOff
         * fru
         * hostname
        public int getNbChildren() {
            return 10;
        }
         */
    }
}

