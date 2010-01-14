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



/* Things to do:
   Add stillMaster check many places (if not still the master, exit gracefully).
   Maybe move where the retry of the wipe is done so that it is done here.
   Add an escalation all the way out to killing the Master JVM.
*/


package com.sun.honeycomb.hadb;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.IOException;
import java.io.File;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

import com.sun.hadb.adminapi.HADBException;
import com.sun.hadb.adminapi.MemberNotInThisDomainException;

import com.sun.honeycomb.common.StringUtil;

import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.EMDConfigException;

import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;

public class MasterService implements HADBMasterInterface {

    private static final Logger LOG =
        Logger.getLogger(MasterService.class.getName());    
    private static final int SLEEP_DELAY = (15 * 1000); // 15s
    private static final int POLL_DELAY  = ( 5 * 1000); // 5s
    
    /*
     * Global failure escalation timeout.
     * If Hadbm reports that it is not running for more than this time period, 
     * we decide to escalate and restart from scratch.
     */
    private static final long DOOMSDAY_TIMEOUT = (2 * 60 * 60 * 1000); // 2h

    private int numStateTries;
    private volatile boolean isRunning; // if svc thread should keep running
    private volatile boolean isForcedShutdown; // kill state machine and exit
    private Thread svcThr;

    private static HADBMasterInterface theMasterService;

    public MasterService() {

        numStateTries = 0;
        isRunning = true;
        svcThr = null;
        theMasterService = null;
        LOG.info("The HADB MasterService has been instantiated.");
    }

    public void clearFailure() {
        LOG.severe("Forcing state machine to exit.");

        isForcedShutdown = true;
        svcThr.interrupt();
    }
    
    public int getCacheStatus() {
        return Hadbm.getInstance().getCacheStatus();
    }

    public String getEMDCacheStatus() {
        return Hadbm.getInstance().getEMDCacheStatus();
    }

    public void updateSchema() throws HADBServiceException {
        try {
            Hadbm.getInstance().updateSchema();
        }
        catch (StateMachineException e) {
            LOG.log(Level.SEVERE, "Operation failed", e);
            throw new HADBServiceException(e);
        }
    }

    public void wipeAndRestartAll() throws HADBServiceException {
        try {
            Hadbm.getInstance().wipeAndRestartAll();
        }
        catch (StateMachineException e) {
            LOG.log(Level.SEVERE, "Operation failed", e);
            throw new HADBServiceException(e);
        }
    }

    public void recoverHost(int nodeId) throws HADBServiceException, 
    MemberNotInThisDomainException {
        try {
            Hadbm.getInstance().recoverHost(nodeId);
        }
        catch (StateMachineException e) {
            LOG.log(Level.SEVERE, "Operation failed", e);
            throw new HADBServiceException(e);
        }
    }
    
    public void disableOneHost(int nodeId) throws HADBServiceException {
        try {
            Hadbm.getInstance().disableHost(nodeId);
        }
        catch (StateMachineException e) {
            LOG.log(Level.SEVERE, "Operation failed", e);
            throw new HADBServiceException(e);
        }
    }

    public void recoverHostForMove(int nodeId, int newDrive)
        throws HADBServiceException, MemberNotInThisDomainException {

        try {
            Hadbm.getInstance().recoverHostForMove(nodeId, newDrive);
        }
        catch (StateMachineException e) {
            LOG.log(Level.SEVERE, "Operation failed", e);
            throw new HADBServiceException(e);
        }
    }
        
    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state.
     */
    public void syncRun() {
        theMasterService = this;
        svcThr = Thread.currentThread();
        isRunning = true;

        // Make sure no old instance of the state machine is hanging around
        Hadbm.resetInstance();

        LOG.info("The HADB Service is ready to start.");
    }

    public void run() {
        LOG.info("The HADB Service is running.");

        // Start the state machine thread
        Hadbm.getInstance().startup();

        long doomsdayEndTime = 0;
        boolean isOK = false;

        if (!isRunning)
            LOG.severe("Already told to shutdown before startup!");

        while (isRunning) {
            Hadbm hadbm = Hadbm.getInstance();
            long delay;
            
            if (!hadbm.healthCheck() || isForcedShutdown) {
                /*
                 * HADB is not happy. If it has been trying for more
                 * than the doomsday timeout, we wipe and restart from
                 * scratch.
                 */

                if (! hadbm.isAlive()) {
                    String msg = "Hadbm thread died unexpectedly!";
                    LOG.severe(msg + " Triggering master failover....");
                    throw new RuntimeException(msg);
                }

                LOG.info("HADBM State: " + hadbm);

                isOK = false;
                long now = System.currentTimeMillis();
                
                if (doomsdayEndTime == 0 && !isForcedShutdown) {
                    LOG.warning("Starting doomsday timer.");
                    doomsdayEndTime = now + DOOMSDAY_TIMEOUT;
                }

                else if (now > doomsdayEndTime || isForcedShutdown) {
                    if (isForcedShutdown)
                        LOG.severe("Forced shutdown: wipe and re-start.");
                    else
                        LOG.severe("Doomsday timeout! Wipe and restart.");

                    isForcedShutdown = false;

                    try {
                        LOG.severe("Forcing shutdown of Hadbm.");
                        hadbm.forceShutdown();

                        LOG.severe("Resetting Hadbm and starting its thread.");
                        Hadbm.resetInstance();
                        Hadbm.getInstance().wipeAndRestartAll();
                        Hadbm.getInstance().startup();
                    }
                    catch (Throwable e) {
                        LOG.log(Level.SEVERE, "Wipe and restart failed", e);
                        // Cause a restart of this JVM
                        throw new RuntimeException(e);
                    }
                }

                delay = POLL_DELAY;
            }
            else {
                // HADB is running.

                if (!isOK) {
                    // We're transitioning from bad (or init) to
                    // good. Log the current state ("RUNNING").
                    LOG.info("HADBM State: " + hadbm);
                    isOK = true;
                }
                else {
                    if (LOG.isLoggable(Level.FINE))
                        LOG.fine("HADBM State: " + hadbm);
                }

                // Reset timeout and go back to normal health-check polling
                doomsdayEndTime = 0;
                delay = SLEEP_DELAY;
            }
            try {
                Thread.currentThread().sleep(delay);
            } catch (InterruptedException ignore) {}
        }

        LOG.info("Telling HADB state machine to stop...");
        try {
            Hadbm.getInstance().shutdown();
            LOG.fine("Hadbm exited normally.");
        }
        catch (InterruptedException ie) {
            LOG.warning("Interrupted: didn't properly shutdown HADB state machine!");
        }
    }
    
    public void shutdown() { 
        isRunning = false;

        if (svcThr == null) {
            LOG.info("The HADB Master Service has already terminated.");
            return;
        }   
    
        LOG.info("The HADB Master Service is being told to shut down.");

        // Wait for the thread to exit
        try {
            svcThr.interrupt();
            svcThr.join();
            LOG.info("HADB Master Service shutdown complete.");
        }
        catch (InterruptedException e) {
            LOG.warning("Interrupted: couldn't wait for MasterService exit!");
        }
    }

    public static void resetProxy() {
        ServiceManager.publish(theMasterService);
    }
    
    public ManagedService.ProxyObject getProxy() {
        Hadbm hadbm = Hadbm.getInstance();
        return new HADBMasterInterface.Proxy(hadbm.getJdbcUrl(),
                                             hadbm.isInitializing(),
                                             hadbm.isRunning(),
                                             hadbm.getEMDCacheStatus(),
                                             hadbm.isDomainCleared(),
                                             hadbm.getLastCreateTime());
    }

    /** Get the proxy for the HADB MasterService */
    public static HADBMasterInterface.Proxy getMasterProxy() {

        NodeMgrService.Proxy nodemgrProxy = 
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        if (nodemgrProxy == null) {
            LOG.warning("Failed to get the local node manager.");
            return null;
        }

        Node masterNode = nodemgrProxy.getMasterNode();
        if (masterNode == null) {
            LOG.warning("Failed to get the master node proxy.");
            return null;
        }

        ManagedService.ProxyObject proxy =
            ServiceManager.proxyFor(masterNode.nodeId(), MasterService.class);

        if (proxy == null) {
            LOG.warning("Couldn't get HADBMasterInterface proxy object.");
            return null;
        }

        if (!(proxy instanceof HADBMasterInterface.Proxy)) {
            LOG.warning("Bad MasterService proxy: " + StringUtil.image(proxy));
            return null;
        }

        return (HADBMasterInterface.Proxy) proxy;
    }

}
