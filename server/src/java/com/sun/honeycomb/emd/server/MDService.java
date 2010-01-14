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



package com.sun.honeycomb.emd.server;

import com.sun.honeycomb.cm.*;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.*;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.oa.*;

import java.util.logging.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import com.sun.honeycomb.emd.MDManagedService;
import com.sun.honeycomb.emd.server.ProcessingCenter;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.cache.CacheInterface;

/**
 * The <code>MDService</code> class is the management interface for
 * the metadata cache on a given node.
 */

public class MDService implements MDManagedService {
    private static final Logger LOG = Logger.getLogger("MDService");
    
    private MDServer server;
    private static final int NB_LOAD_RECORDED = 10;
    private double loadAverage;
    private double[] loadRecord;
    private int curLoadIndex;
    private volatile boolean running;

    private static MDManagedService.Proxy proxy = new MDManagedService.Proxy(0,false) ;
    
    /*
     * Cluster Management entry point
     */
    public MDService() throws EMDException {
        server = new MDServer();
        loadAverage = 0;
        loadRecord = new double[NB_LOAD_RECORDED];
        curLoadIndex = 0;
        running = false;
    }
    
    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {

        // Register the processing Units
        
        CacheManager cacheManager = CacheManager.getInstance();
        CacheInterface[] caches = cacheManager.getServerCaches();

        for (int i=0; i<caches.length; i++) {
            try {
                ProcessingCenter.registerProcessingUnit(caches[i]);
            } catch (EMDException e) {
                LOG.log(Level.SEVERE,
                        "Failed to register the "+caches[i].getCacheId()+" cache ["+e.getMessage()+"]",
                        e);
            }
        }
    }


    public void run() {
        try {
            ProcessingCenter.startUnits();
            server.startServer();
            SysCache.getInstance().start();
            running = true;
        } catch (EMDException e) {
            LOG.log(Level.SEVERE,
                    "Failed to start the local MD cache ["+e.getMessage()+"]",
                    e);
            running = false;
            throw new InternalException(e);
        }
        LOG.info("MD server is running");
    }

    public void shutdown() {
        try {
            SysCache.getInstance().stop();
            server.stopServer();
            ProcessingCenter.shutdownUnits();
        } catch (EMDException e) {
            LOG.severe("Failed to stop the local MD cache" + e);
            throw new InternalException(e);
        } finally {
            running = false;
        }
        LOG.info("MD server is shutdown");
    }

    
    public ManagedService.ProxyObject getProxy() {
        update();
       
        proxy.setMDLoad(loadAverage /(double)loadRecord.length);
        proxy.setRunning(running);
        
        return proxy;
	}

   /**
     * This method can only be used within the IO-SERVERS JVM where the MDService
     * is running otherwise the changes made by this call will not be reflected
     * in the proxy at all.
     * 
     * @param disk
     * @param isRunning
     */
    public static void setCacheRunning(Disk disk, boolean isRunning) { 
        proxy.setCacheState(disk, isRunning);
    }
    

    /**
     * This method can only be used within the IO-SERVERS JVM where the MDService
     * is running otherwise the changes made by this call will not be reflected
     * in the proxy at all.
     * 
     * @param disk
     * @param isRunning
     */
    public static void setCacheCorrupted(Disk disk, boolean isCorrupted) { 
        proxy.setCacheCorrupted(disk, isCorrupted);
    }

    public static void setCacheComplete(Disk disk, boolean isCorrupted) { 
        proxy.setCacheComplete(disk, isCorrupted);
    }

    /****************************************
     *
     * This runs only heartbeats and checks if
     * disks need to be refreshed
     *
     * Also publishes the mailbox content
     *
     ****************************************/

    private void update() {
        // Get the new load and update the average load.
        loadAverage -= loadRecord[curLoadIndex];
        loadRecord[curLoadIndex] = server.getLoad();
        loadAverage += loadRecord[curLoadIndex];
        curLoadIndex++;
        if (curLoadIndex == loadRecord.length) {
            curLoadIndex = 0;
        }
    }
  
    public void restart(String cacheID, Disk disk) throws EMDException  {
        CacheInterface ci = CacheManager.getInstance().getServerCache(cacheID);
        ci.unregisterDisk(disk);
        ci.registerDisk(ProcessingCenter.getMDPath(disk), disk);
    }
    
    public void sync(String cacheID, Disk disk) throws EMDException  {
        CacheInterface ci = CacheManager.getInstance().getServerCache(cacheID);
        ci.sync(disk);
    }
    
    public boolean isCacheUp(String cacheID, Disk disk)
            throws ManagedServiceException, EMDException {
        CacheInterface ci = CacheManager.getInstance().getServerCache(cacheID);
        return ci.isRegistered(disk);
    }
    
    public boolean isSystemCacheInInit() throws ManagedServiceException {
        return SysCache.getInstance().isInInit();
    }
    
    public void wipeCache(String cacheID, Disk disk)
            throws ManagedServiceException, EMDException {
        CacheInterface ci = CacheManager.getInstance().getServerCache(cacheID);
       
        /* 
         * Best effort if we can't close we'll still wipe and try to bring up 
         * the cache. If that then fails it will escalate restarting the JVM.
         */
        try {
            ci.unregisterDisk(disk); 
        } catch (EMDException e) { 
            LOG.log(Level.WARNING,
                    "Unable to close disk correctly proceeding to wipe anyways.",
                    e);
        }
        
        /* 
         * lets disable it incase the close didn't work well, which it oftenly
         * doesn't when the cache is corrupted.
         */
        MDService.setCacheRunning(disk, false);
      
        ci.wipe(disk);
        ci.registerDisk(ProcessingCenter.getMDPath(disk),disk); 
    }
    
    /****************************************
     *
     * Private attributes
     *
     ****************************************/

    /****************************************
     *
     * Class to garbage collect the database
     *
     ****************************************/

//     private static final long GC_SLEEP_PERIOD = 60000; // in ms. = 1 minute

//     private MySqlGC gcThread;

//     private class MySqlGC
//         implements Runnable {

//         private boolean running;

//         private MySqlGC() {
//             running = true;
//         }
        
//         public void run() {
//             LOG.log(Level.INFO,
//                     "The MySQL garbage collection thread has been started");
            
//             while (running) {

//                 try {
//                     Thread.sleep(GC_SLEEP_PERIOD);
//                 } catch (InterruptedException ignored) {
//                 }
                
//                 Connection connection = null;
//                 Statement statement = null;
//                 Statement killStatement = null;

//                 try {
//                     connection = getDBConnection(false);
//                     statement = connection.createStatement();
//                     killStatement = connection.createStatement();

//                     ResultSet processes = statement.executeQuery("show processlist");
//                     if (processes.first()) {
//                         do {
//                             int time = processes.getInt("Time");
//                             if (time > EMDClient.DEFAULT_TIMEOUT) {
//                                 int id = processes.getInt("Id");
//                                 String cmd = processes.getString("Info");

//                                 LOG.log(Level.INFO,
//                                         "The process "+id+" ["+cmd+"] has been running for "+time+" seconds. It will be killed");

//                                 killStatement.execute("kill "+id);
//                             }
//                         } while (processes.next());
//                     }
//                 } catch (SQLException e) {
//                     LOG.log(Level.SEVERE,
//                             "The Mysql garbage collection failed",
//                             e);
//                 } catch (IOException e) {
//                     LOG.log(Level.SEVERE,
//                             "The Mysql garbage collection failed",
//                             e);
//                 } finally {
//                     try {
//                         if (killStatement != null) {
//                             killStatement.close();
//                         }
//                         if (statement != null) {
//                             statement.close();
//                         }
//                         if (connection != null) {
//                             connection.close();
//                         }
//                     } catch (SQLException e) {
//                         LOG.log(Level.SEVERE,
//                                 "Failed to close the handles to the database",
//                                 e);
//                     }
//                 }
//             }
//         }
//     }
}
