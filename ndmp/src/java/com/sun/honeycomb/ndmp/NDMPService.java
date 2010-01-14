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



package com.sun.honeycomb.ndmp;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.emd.server.SysCache;
import com.sun.honeycomb.admin.mgmt.server.MgmtServerIntf;

import java.text.MessageFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;



/** Managed Service which controls the NDMP Data Server for Distaster Recovery backups and restores
 */
public class NDMPService extends BaseNDMPService 
    implements ManagedService, NDMPProxyIF {

    public NDMPService(){
        super();
        if (logger.isLoggable(Level.INFO))
            logger.info("NDMP instantiated " + this + " on node " + 
                        ServiceManager.proxyFor(ServiceManager.LOCAL_NODE));
    }

    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
        String name = "unknown";
        try {
            name = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException unhe) {}
        if (logger.isLoggable(Level.INFO)){
            logger.info(name + " NDMP Server on port " + controlPort + ", IP " + getDataVIP());
            logger.info("NDMP ServiceManager.publish");
        }
        ServiceManager.publish(this);
    }


    /**
     * Gets the IP address the Protocol service should bind to from the
     * PlatformService proxy. Copied from NFSService.java.
     *
     * @return IP address
     */
    private static String getDataVIP() {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE, "PlatformService");
        if (obj == null || !(obj instanceof PlatformService.Proxy))
            return null;

        PlatformService.Proxy proxy = (PlatformService.Proxy) obj;
        return proxy.getDataVIPaddress();
    }


    public ManagedService.ProxyObject getProxy() {
        return new ManagedService.ProxyObject();
    }

    void alert(String message){
        com.sun.honeycomb.cm.node_mgr.NodeMgrService.Proxy nodeMgr =  
            (com.sun.honeycomb.cm.node_mgr.NodeMgrService.Proxy) 
            com.sun.honeycomb.cm.ServiceManager.proxyFor(com.sun.honeycomb.cm.ServiceManager.LOCAL_NODE);
        int masterNodeId = nodeMgr.nodeId();
        String prop = "root." + masterNodeId + ".MgmtServer.cliAlerts";
        try {
            com.sun.honeycomb.alert.AlerterServerIntf alertApi = com.sun.honeycomb.alert.AlerterServerIntf.Proxy.getServiceAPI();
            if (alertApi == null) {
                logger.severe("can't retrieve alert API");
                return;
            }
            alertApi.notifyClients(prop, message);
        } catch (Exception ae) {
            logger.warning("failed to notify AlertClients about CLI change -"+
                           " property not yet registered? " + ae);
        }
    }

    public String getStatus(){
            String activityString = getLocalizedString(activity);
            if (activity.equals(DataServer.INACTIVE)){

                ClusterProperties props = ClusterProperties.getInstance();
                String state = props.getProperty(SysCache.SYSTEM_CACHE_STATE);
                if (state == null)
                    return "unknown";
                if (state.equals(SysCache.RESTORING) || state.equals(SysCache.RESTOR_FT)){
                    String restoringToDate = props.getProperty(SysCache.RESTOR_FT_DATE);
                    return "Restore started to " + props.getProperty(SysCache.RESTOR_FT_DATE) +
                        ". Ready for next tape.";
                }
                else if (state.equals(SysCache.RUNNING)){
                        return "Backup ready.";
                }
                else if (state.equals(SysCache.WAITFORREPOP)) {
                    long t = props.getPropertyAsLong(SysCache.FIRST_ERROR_TIME, Long.MIN_VALUE);
                    if (t>0) {
                        return "Safe to backup to " + new Date (t) + ".";
                    } else {
                        return "Backup unavailable.";
                    }
                }
                else {
                    return "Backup unavailable.";
                }
            }
            else {
                Object[] args = {activityString, Long.valueOf(objectsProcessed), Long.valueOf(bytesProcessed)};
                String formatString = getLocalizedString(DataServer.N_OBJECTS_N_BYTES_PROCESSED);
                return MessageFormat.format(formatString, args);
            }
}


    String getLocalizedString(String descriptor){
        ResourceBundle bundle = BundleAccess.getInstance().getBundle();
        if (bundle == null){
            logger.severe("NDMP localization resource bundle null");
            return descriptor;
        }
        else{
            try{
                return bundle.getString(descriptor);
            }
            catch (Exception e){
                logger.log(Level.SEVERE, "NDMP localization failed for " + descriptor, e);
                return descriptor;
            }
        }
    }
}
