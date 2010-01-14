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

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.logging.Level;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;

import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.emd.ObjectInfo;
import com.sun.honeycomb.emd.common.MDDiskAbstraction;
import com.sun.honeycomb.emd.remote.ObjectBroker;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.common.EMDException;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * The <code>ProcessingCenter</code> class is the entry point to perform
 * queries against the metadata repositories available on the local node.
 */

public class ProcessingCenter {

    static private final long SYNC_DISK_TIMEOUT = 5000; 
    static private final long PERIODIC_WORK_TIMEOUT = 1800000; // 30 minutes
 
    private static final Random random = new Random(System.currentTimeMillis());

    /**
     * The <code>DiskMonitorThread</code> class monitors the list of
     * available local disks and registers / unregisters them if needed
     */
    
    private static class DiskMonitorThread
        extends Thread {

        private static Logger LOG = Logger.getLogger("DiskMonitorThread");
        private static long checkFrequency = 2000; /* in ms. */

        private boolean running;

        DiskMonitorThread() {
            super();
        }

        public void safeStop() {
            running = false;
            interrupt();

            boolean stopped = false;

            while (!stopped) {
                try {
                    join();
                    stopped = true;
                } catch (InterruptedException ignored) {
                }
            }

            LOG.info("The DiskMonitorThread has been stopped");
        }

        public void run() {
            LOG.info("The DiskMonitorThread has been started");

            long lastone = System.currentTimeMillis();
            running = true;
            while (running) {
                try {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("Checking the set of available disks on the local node");
                    }

                    Disk[] newDisks = MDDiskAbstraction.getInstance(MDDiskAbstraction.FORCE_REFRESH_LOCAL).getLocalDisks();

                    // Remove the disks that are not in anymore
                    int i;
                    int diskIndex = 0;

                    while (diskIndex < registeredDisks.size()) {
                        Disk disk = (Disk)registeredDisks.get(diskIndex);
                        for (i=0; i<newDisks.length; i++) {
                            if (newDisks[i].equals(disk)) {
                                break;
                            }
                        }

                        if (i == newDisks.length) {
                            // The current disk has disappeared
                            unregisterDisk(disk);
                            // Useful to go back to the same index (the disk has been
                            // removed, so the same index is the next one)
                            diskIndex--;
                        }
                        diskIndex++;
                    }

                    int nbDiskFound = 0;

                    int profile = -1;
                    ArrayList threads = new ArrayList();

                    // Check that all the new disks are already registered
                    for (i=0; i<newDisks.length; i++) {
                        if (!registeredDisks.contains(newDisks[i])) {
                            if (profile == -1) {
                                profile = random.nextInt();
                            }
                            Thread t = new Thread(new DiskRegistrationThread(newDisks[i], profile));
                            threads.add(t);
                            t.setName("DiskRegThread-" + i);
                            LOG.info("Registration profile "+profile+" spawned thread "+t.getName());
                            t.start();
                            nbDiskFound++;
                        }
                    }

                    if (nbDiskFound > 0) {
                        for (int tid=0; tid<threads.size(); tid++) {
                            boolean stopped = false;
                            Thread t = (Thread)threads.get(tid);
                            while (!stopped) {
                                try {
                                    t.join();
                                    stopped = true;
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }
                        
                        LOG.info(nbDiskFound+" new disks have been registered by profile ["+
                                 profile+"]");
                    }
                    
                    if (LOG.isLoggable(Level.FINE)) {
                        if (registeredDisks.size() == 0) {
                            LOG.fine("The local MDService still didn't find any local disk");
                        }
                    }
                    
                    // cycle through units and call periodWork method
                    if (System.currentTimeMillis() - lastone > PERIODIC_WORK_TIMEOUT) { 
                        lastone = System.currentTimeMillis();
	                    for(int u = 0; u < units.length; u++) {
	                        diskIndex = 0;
	                        while (diskIndex < registeredDisks.size()) {
	                            Disk disk = (Disk)registeredDisks.get(diskIndex);
                                if (LOG.isLoggable(Level.FINE))
                                    LOG.fine("doPeriodicWork on " + 
                                             disk.getId().toStringShort());
	                            units[u].doPeriodicWork(disk);
	                            diskIndex++;
	                        }
	                    }
                    }
                    
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE,
                            "Got an exception in the DiskMonitorThread",
                            t);
                } finally {
                    try { 
                        Thread.sleep(checkFrequency);
                    } catch (InterruptedException ignored) {
                    }
                    
                }
            }
        }
    }

    /**********************************************************************
     *
     * The DiskRegistration class
     *
     **********************************************************************/

    private static class DiskRegistrationThread 
        implements Runnable {
        private static Logger LOG = Logger.getLogger("DiskRegistrationThread");
        private Disk disk;
        private int profile;

        DiskRegistrationThread(Disk newDisk,
                               int nProfile) {
            disk = newDisk;
            profile = nProfile;
        }

        public void run() {
            LOG.info("A new thread ["+Thread.currentThread().getName()+
                     "] is registering a new disk ["+disk.getId()+"] - [Registration profile "+
                     profile+"]");

            

            try {
                ProcessingCenter.registerDisk(disk);

                LOG.info("The thread "+Thread.currentThread().getName()+
                         " is done with the registration of disk "+
                         disk.getId()+" - [Registration profile "+
                         profile+"]");
                
            } catch (EMDException e) {
                LOG.log(Level.SEVERE,
                        "Failed to register disk "+disk.getId()+
                        " [thread "+Thread.currentThread().getName()+"]",
                        e);
            }
        }
    }
    
    /**********************************************************************
     *
     * ProcessingCenter implementation
     *
     **********************************************************************/
    
    /**
     * The <code>LOG</code> variable is used to log messages to the logger
     *
     */

    private static Logger LOG = Logger.getLogger("ProcessingCenter");

    /**
     * The <code>temporaryUnits</code> variable is used to store the
     * temporary list of processing units while it is being built at
     * startup time.
     *
     * This object is also used for synchronization in the ProcessingCenter
     * class and should therefore never be changed or deleted.
     */

    private static ArrayList temporaryUnits = new ArrayList();

    private static CacheInterface[] units = null;
    private static boolean allUnitsRegistered = false;
    private static int scheduledHighLevelRequests = 0;
    private static boolean running = false;
    private static int ongoingRequests = 0;
    private static ArrayList registeredDisks = new ArrayList();
    private static DiskMonitorThread diskThread = new DiskMonitorThread();
    
    /**
     * <code>registerProcessingUnit</code> registers a new processing unit.
     *
     * @param unit a <code>CacheProcessingUnit</code> value
     * @exception EMDException if an error occurs
     */
    
    public static void registerProcessingUnit(CacheInterface unit) 
        throws EMDException {
        synchronized (temporaryUnits) {
            if (allUnitsRegistered) {
                throw new EMDException("A unit registration came too late");
            }
            
            temporaryUnits.add(unit);
        }
    }

    /*
     * Start / stop the registered processing units
     */

    /* The temporaryUnits lock has to be hold when calling that routine
     * (see startUnits) */

    private static void lockUnitList() {
        units = new CacheInterface[temporaryUnits.size()];
        temporaryUnits.toArray(units);
        temporaryUnits.clear();
        allUnitsRegistered = true;
    }

    /* The temporaryUnits lock has to be hold when calling that routine
     * (see stopUnits) */
    
    private static void unlockUnitList() {
        for (int i=0; i<units.length; i++) {
            temporaryUnits.add(units[i]);
        }
        units = null;
        allUnitsRegistered = false;
    }

    /* The temporaryUnits lock has to be hold when calling that routine
     * (see shutdownUnits) */

    private static void destroyUnitList() {
        temporaryUnits.clear();
        allUnitsRegistered = false;
        ongoingRequests = 0;
        scheduledHighLevelRequests = 0;
        running = false;
        registeredDisks = new ArrayList();
        diskThread = new DiskMonitorThread();
    }

    public static void startUnits() throws EMDException {
        synchronized (temporaryUnits) {
            if (allUnitsRegistered) {
                return;
            }

            lockUnitList();
            running = true;

            for (int i=0; i<units.length; i++) {
                units[i].start();
            }

            diskThread.start();
        }
    }

    public static void stopUnits() throws EMDException {
        synchronized (temporaryUnits) {
            if (!allUnitsRegistered) {
                return;
            }

            /* Prevent new requests from being processed */
            running = false;
            
            while (ongoingRequests > 0) {
                try {
                    temporaryUnits.wait();
                } catch (InterruptedException ignored) {
                }
            }

            diskThread.safeStop();
            
            for (int i=0; i<units.length; i++) {
                units[i].stop();
            }

            unlockUnitList();
        }
    }

    public static void shutdownUnits() throws EMDException {
        synchronized (temporaryUnits) {
            if (!allUnitsRegistered) {
                return;
            }

            /* Prevent new requests from being processed */
            running = false;
            
            while (ongoingRequests > 0) {
                try {
                    temporaryUnits.wait();
                } catch (InterruptedException ignored) {
                }
            }

            diskThread.safeStop();
            
            for (int i=0; i<units.length; i++) {
                units[i].stop();
            }

            /* reinitialize the class - don't release the lock */
            destroyUnitList();
        }
    }
               
            

    /*
     * General - any request - calls
     */
               

    /*
     * Get latest list of disks if inputDisks are not
     * part of registeredDisks.
     * Retry until all disks are there or we reached 
     * the timeout.
     *
     */
    private static void syncDisks(ArrayList inputDisks) {
        long timeout = SYNC_DISK_TIMEOUT;
        long waitTime = 0;
        boolean retry = true;
        boolean resynced = false;
        int j;


        while (retry) {
            ArrayList currentRegisteredDisks = (ArrayList) 
                registeredDisks.clone();

            for (int i = 0; i < inputDisks.size(); i++) {
                Disk inputDisk = (Disk) inputDisks.get(i);
                for (j = 0; j < currentRegisteredDisks.size(); j++) {
                    Disk registeredDisk = (Disk) currentRegisteredDisks.get(j);
                    if (registeredDisk.equals(inputDisk)) {
                        // We found all of them, return
                        if (i == (inputDisks.size() - 1)) {
                            return;
                        }
                        break;
                    }
                }
                //
                // Input disk is missing, force a resync if necessary
                // and wait for DiskRegistrationThread to complete.
                //
                if (j == currentRegisteredDisks.size()) {
                    // awake diskThread to resync the list of disks
                    if (!resynced) {
                        diskThread.interrupt();
                        resynced = true;
                    }

                    try {
                        waitTime = System.currentTimeMillis();
                        synchronized(registeredDisks) {
                            registeredDisks.wait(timeout);
                        }
                    } catch (InterruptedException ignored) {
                    }
                    waitTime = System.currentTimeMillis() - waitTime;
                    timeout = timeout - waitTime;
                }
                // timeout expired, return
                if (timeout <= 0) {
                    StringBuffer input = new StringBuffer();
                    input.append(", input disks = ");
                    for (i = 0; i < inputDisks.size(); i++) {
                        input.append(" ");
                        input.append(((Disk)inputDisks.get(i)).diskIndex());
                    }
                    LOG.info("failed to match input disks " + input.toString() +
                             " for query... timed-out.");
                    return;
                }
            }
        }
    }

     
    private static void preRequest(ArrayList disks) throws EMDException {
        synchronized (temporaryUnits) {
            if (!allUnitsRegistered) {
                throw new EMDException("Not all processing units have been registered. Cannot execute request");
            }

            if (!running) {
                throw new EMDException("The ProcessingCenter is not running. Cannot handle requests");
            }

            while (scheduledHighLevelRequests > 0) {
                try {
                    temporaryUnits.wait();
                } catch (InterruptedException ignored) {
                }
            }

            // We increment the ongoingRequests anyway since it will be decremented in the finally
            // clauses
            ongoingRequests++;
        }
        if (disks != null) {
            syncDisks(disks);
        }
    }

    private static void postRequest() {
        synchronized (temporaryUnits) {
            ongoingRequests--;
            temporaryUnits.notifyAll();
        }
    }

    /**
     * The next call waits until all the high level requests have been
     * performed (high level requests include disk register and disk
     * unregister.
     */

    private static void waitForHighLevelRequestsCompletion() {
        synchronized (temporaryUnits) {
            while (scheduledHighLevelRequests > 0) {
                try {
                    temporaryUnits.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    public static String getMDPath(Disk disk) 
        throws EMDException {
        // Should be put in the disk server

        String result = disk.getPath()+"/MD_cache";

        File MDdir = new File(result);
        if (!MDdir.exists()) {
            LOG.info("Creating the MD directory ["+result+"]");
            MDdir.mkdir();
        }
        
        return(result);
    }
    
    private static void registerDisk(Disk disk) 
        throws EMDException {
        
        synchronized (temporaryUnits) {
            scheduledHighLevelRequests++;

            // Make sure that no request is going on
            while (ongoingRequests > 0) {
                try {
                    temporaryUnits.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }

        LOG.info("Detected a new disk ["+disk.getPath()+"]");

        EMDException outputException = null;
            
        try {
            for (int i=0; i<units.length; i++) {
                LOG.info("Sending the registration of disk "+disk.getPath()+
                         " to cache "+units[i].getCacheId());

                if (!units[i].isRegistered(disk)) {
                    try {
                        units[i].registerDisk(getMDPath(disk), disk);
                    } catch (EMDException e) {
                        LOG.log(Level.SEVERE,
                                "Failed to register disk ["+
                                disk.getPath()+"] to cache "+
                                units[i].getCacheId(),
                                e);
                        outputException = e;
                    }
                } else {
                    LOG.info("Disk ["+
                             disk.getPath()+"] is already registered with cache "+
                             units[i].getCacheId());
                }
            }
            
            if (outputException == null) {
                synchronized (temporaryUnits) {
                    registeredDisks.add(disk);
                    synchronized(registeredDisks) {
                        registeredDisks.notifyAll();
                    }
                }
            } else {
                throw outputException;
            }
        } finally {
            synchronized (temporaryUnits) {
                scheduledHighLevelRequests--;
                temporaryUnits.notifyAll();
            }
        }
    }
    
    private static void unregisterDisk(Disk disk) {
        
        synchronized (temporaryUnits) {
            scheduledHighLevelRequests++;
            // Make sure that no request is going on
            while (ongoingRequests > 0) {
                try {
                    temporaryUnits.wait();
                } catch (InterruptedException ignored) {
                }
            }
            
            LOG.info("Detected the leave of a disk ["+disk.getId()+"]");
            
	    try {
		for (int i=0; i<units.length; i++) {
		    try {
			units[i].unregisterDisk(disk);
		    } catch (EMDException e) {
			LOG.log(Level.SEVERE,
				"Failed to unregister disk ["+
				disk.getPath()+"] from cache "+
				units[i].getCacheId()+" ["+e.getMessage()+"]");
		    }
		}
	    } finally {
		registeredDisks.remove(disk);
                synchronized(registeredDisks) {
                    registeredDisks.notifyAll();
                }
		scheduledHighLevelRequests--;
		temporaryUnits.notifyAll();
	    }
        }
    }

    /*
     * get/set/delete metadata APIs
     */

    public static void setMetadata(String cacheId,
                                   NewObjectIdentifier oid,
                                   Object argument,
                                   ArrayList disks)
        throws EMDException {
        preRequest(disks);
        
        EMDException exception = null;
        

        try {
            for (int diskNbr=0; diskNbr<disks.size(); diskNbr++) {
                Disk disk = (Disk)disks.get(diskNbr);
                for (int i=0; i<units.length; i++) {
                    if (units[i].getCacheId().equals(cacheId)) {
                        try {
                            units[i].setMetadata(oid, argument, disk);
                        } catch (EMDException e) {
                            exception = e;
                        }
                        break;
                    }
                }
            }
        } finally {
            postRequest();
        }

        if (exception != null) {
            throw exception;
        }
    }
    
    public static void removeMetadata(String cacheId,
                                      NewObjectIdentifier oid,
                                      ArrayList disks)
        throws EMDException {
        preRequest(disks);
        
        if (disks == null) {
            disks = registeredDisks;
        }
        
        try {
            for (int i=0; i<units.length; i++) {
                if (units[i].getCacheId().equals(cacheId)) {
                    for (int j=0; j<disks.size(); j++) {
                        units[i].removeMetadata(oid, (Disk)disks.get(j));
                    }
                    break;
                }
            }
        } finally {
            postRequest();
        }
    }

    public static void addLegalHold(String cacheId,
                                    NewObjectIdentifier oid,
                                    String legalHold,
                                    ArrayList disks)
        throws EMDException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Adding (" + oid + ", " + legalHold +
                     ") into cache " + cacheId);
        }

        preRequest(disks);
        EMDException exception = null;

        try {
            for (int diskNbr=0; diskNbr<disks.size(); diskNbr++) {
                Disk disk = (Disk)disks.get(diskNbr);
                for (int i=0; i<units.length; i++) {
                    if (units[i].getCacheId().equals(cacheId)) {
                        try {
                            units[i].addLegalHold(oid, legalHold, disk);
                        } catch (EMDException e) {
                            exception = e;
                        }
                        break;
                    }
                }
            }
        } finally {
            postRequest();
        }

        if (exception != null) {
            throw exception;
        }
    }

    public static void removeLegalHold(String cacheId,
                                       NewObjectIdentifier oid,
                                       String legalHold,
                                       ArrayList disks)
        throws EMDException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Removing (" + oid + ", " + legalHold +
                     ") from cache " + cacheId);
        }        

        preRequest(disks);

        if (disks == null) {
            disks = registeredDisks;
        }
        
        try {
            for (int i=0; i<units.length; i++) {
                if (units[i].getCacheId().equals(cacheId)) {
                    for (int j=0; j<disks.size(); j++) {
                        units[i].removeLegalHold(oid, legalHold,
                                                 (Disk)disks.get(j));
                    }
                    break;
                }
            }
        } finally {
            postRequest();
        }
    }
    
    /*
     * Query API
     */

    public static void queryPlus(ObjectBroker broker,
                                 String cacheId,
                                 ArrayList disks,
                                 String query,
                                 ArrayList attributes,
                                 EMDCookie cookie,
                                 int maxResults, 
                                 int timeout,
                                 boolean forceResults,
                                 Object[] boundParameters) 
        throws EMDException {
        preRequest(disks);

        try {
            for (int i=0; i<units.length; i++) {
                if (units[i].getCacheId().equals(cacheId)) {
                    units[i].queryPlus(broker, disks, query, attributes,
                                       cookie, maxResults, timeout, 
                                       forceResults,boundParameters);
                    break;
                }
            }
            
        } finally {
            postRequest();
        }
    }
    
    /*
     * Select Unique API
     */

    public static void selectUnique(ObjectBroker broker,
                                    String cacheId,
                                    String query,
                                    String attribute,
                                    String lastAttribute,
                                    int maxResults, 
                                    int timeout,
                                    boolean forceResults,
                                    Object[] boundParameters) 
        throws EMDException {
        preRequest(null);

        try {
            // There is only one procesing unit for now, so don't merge
            for (int i=0; i<units.length; i++) {
                if (units[i].getCacheId().equals(cacheId)) {
                    units[i].selectUnique(broker, query, attribute, lastAttribute, 
                                          maxResults, timeout, forceResults,
                                          boundParameters);
                    break;
                }
            }
            
        } finally {
            postRequest();
        }
    }
}
