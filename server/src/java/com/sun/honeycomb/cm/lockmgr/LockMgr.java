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



package com.sun.honeycomb.cm.lockmgr;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.config.ClusterProperties;

public class LockMgr implements LockMgrIntf, PropertyChangeListener
{
    private Map              lockTable = null;
    private Thread           thr = null;
    private int              lockTimeoutPeriod;
    private int              runLoopExpiredLocks;

    private volatile boolean keepRunning = false;
    
    private static final Logger logger = 
      Logger.getLogger(LockMgr.class.getName());

    private static final String PROP_LOCK_TIMEOUT = 
      "honeycomb.cm.lockmgr.locktimeout";

    private static final String PROP_RUN_LOOP_EXPIRED_LOCKS = 
      "honeycomb.cm.lockmgr.expiredlocks";

    private static final int RUN_SLEEP = 
      (5 * 1000); // 5 sec

    private static final int DEFAULT_LOCK_TIMEOUT_PERIOD = 
      (5 * 1000 * 1000); // 5 min
    private static final int DEFAULT_RUN_LOOP_CHECK_EXPIRED_LOCKS = 
      12; // 5*12=1 min 

    public static final String LOCK_MGR_PREFIX = "LockMgr: ";

    public LockMgr() {
        lockTable = new HashMap();
        keepRunning = true;
        thr = null;
        readProperties();
    }


    public void shutdown() {

        keepRunning = false;
        if (thr != null) {
            thr.interrupt();
        }

        boolean stopped = false;
        while (!stopped) {
            try {
                if (thr != null) {
                    thr.join();
                }
                stopped = true;
            } catch (InterruptedException ignored) {
                
            }
        }
        logger.info(LOCK_MGR_PREFIX + "LockMgr now STOPPED");
    }

    public void syncRun() {
    }

    public void run() {

        int loop = 0;

        logger.info(LOCK_MGR_PREFIX + "LockMgr now STARTED with " +
          "LockTimeoutPeriod = " + lockTimeoutPeriod +
          ", RunLoopExpiredLock " + runLoopExpiredLocks);

        thr = Thread.currentThread();
        while (keepRunning) {
            loop++;
            ServiceManager.publish(this);
            try {
                Thread.sleep(RUN_SLEEP);
            } catch (InterruptedException ignored) {
            }
            if (loop % runLoopExpiredLocks == 0) {
                cleanupExpiredLocks();
            }
        }
    }
    
    public ManagedService.ProxyObject getProxy () {
        return new Proxy();
    }


    public void propertyChange(PropertyChangeEvent event) {

        String prop = event.getPropertyName();

        if (prop.equals(PROP_LOCK_TIMEOUT) || 
          (prop.equals(PROP_RUN_LOOP_EXPIRED_LOCKS))) {
            readProperties();
        }            
    }

    public boolean tryLock(LockOwner newOwner, String lockName)
        throws ManagedServiceException {
        
        boolean res = true;
        boolean addEntry = true;

        LockOwner curOwner = null;

        synchronized(lockTable) {
            boolean exist = lockTable.containsKey(lockName);
            if (exist) {
                curOwner = (LockOwner) lockTable.get(lockName);
                if (curOwner != null) {
                    if (!curOwner.isValid()) {
                        logger.warning(LOCK_MGR_PREFIX + 
                          "Detected invalid owner " +
                          curOwner.getSource() + 
                          ", update lock entry for " + lockName);
                        res = true;
                        lockTable.remove(lockName);
                        addEntry = true;
                    } else if (curOwner.isSameOwner(newOwner)) {
                        logger.warning(LOCK_MGR_PREFIX + "Owner " + 
                          curOwner + 
                          " already hold the lock for " +
                          lockName);
                        curOwner.setTimestamp();
                        res = true;
                        addEntry = false;
                    } else {
                        res = false;
                        addEntry = false;
                    }
                } else {
                    // paranoia
                    lockTable.remove(lockName);
                    addEntry = true;
                    res = true;
                    logger.severe(LOCK_MGR_PREFIX +
                      "Existing owner is null for lock " +
                        lockName + " !!!");
                }
            }
            
            // STEPH
            logger.info(LOCK_MGR_PREFIX + "tryLock " + 
              " curOwner = " + ((curOwner == null) ? "null" : curOwner) +
              ", newOwner = " + newOwner +
              ", addEntry = " + addEntry +
              ", res = " + res + ", lock = " + lockName);

            if (addEntry) {
                newOwner.setTimestamp();
                lockTable.put(lockName, newOwner);
                // STEPH
                logger.info(LOCK_MGR_PREFIX + 
                  "Added lock " + lockName + 
                  " for owner " + newOwner);
                
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(LOCK_MGR_PREFIX + 
                      "Added lock " + lockName + 
                      " for owner " + newOwner);
                }
            }
            return res;
        }
    }

    public void unlock(LockOwner expectedOwner, String lockName, boolean force) 
        throws ManagedServiceException {
        

        // STEPH
        logger.info(LOCK_MGR_PREFIX + "unlock expctOwner = " + 
          expectedOwner + ", lock = " + lockName);


        synchronized(lockTable) {
            LockOwner curOwner = (LockOwner) lockTable.get(lockName);
            if (curOwner == null) {
                logger.warning(LOCK_MGR_PREFIX +
                  "Nothing to unlock for " + lockName);
                return;
            }

            logger.info(LOCK_MGR_PREFIX + "curOwner = " + curOwner);

            if (force) {
                if (!curOwner.isSameSource(expectedOwner)) {
                    logger.warning(LOCK_MGR_PREFIX +
                      "Can't unlock (force) " + lockName +
                      " curOwner is " + curOwner.getSource() +
                      ", caller is " + expectedOwner.getSource());
                    return;
                }
            } else {
                if (!curOwner.isSameOwner(expectedOwner)) {
                    logger.warning(LOCK_MGR_PREFIX +
                      "Can't unlock " + lockName +
                      " curOwner is " + curOwner +
                      ", caller is " + expectedOwner);
                    return;
                }
            }
            lockTable.remove(lockName);

            // STEPH
            logger.info(LOCK_MGR_PREFIX +
              "Removed lock " + lockName +
              " for owner " + expectedOwner);

            if (logger.isLoggable(Level.FINE)) {
                logger.fine(LOCK_MGR_PREFIX +
                  "Removed lock " + lockName +
                  " for owner " + expectedOwner);
            }
        }
    }

    private void readProperties() {

        ClusterProperties config = ClusterProperties.getInstance();

        runLoopExpiredLocks = config.getPropertyAsInt(
            PROP_RUN_LOOP_EXPIRED_LOCKS, DEFAULT_RUN_LOOP_CHECK_EXPIRED_LOCKS);
        lockTimeoutPeriod = config.getPropertyAsInt(
            PROP_LOCK_TIMEOUT, DEFAULT_LOCK_TIMEOUT_PERIOD);
    }

    private void cleanupExpiredLocks() {

        int oldLocks = 0;
        long now = System.currentTimeMillis();

        logger.info(LOCK_MGR_PREFIX + "CleanupExpiredLocks starts...");

        //
        // Browse through the table of locks without any locks
        // Concurrent tryLock may be missed and concurrent unlock
        // may result in an IllegalStateException.
        //
        Iterator it = lockTable.values().iterator();
        while (it.hasNext()) {
            LockOwner entry  = (LockOwner) it.next();
            if (entry != null && entry.hasExpired(now, lockTimeoutPeriod)) {
                try {
                    it.remove();
                    oldLocks++;
                } catch (IllegalStateException ignore) {
                    // The element has been removed already
                }
            }
        }
        logger.info(LOCK_MGR_PREFIX +
          "CleanupExpiredLocks ends, cleaned " + oldLocks + " entries");
    }


    private static class LockOwnerError extends Error {
        public LockOwnerError(String msg) {
            super(msg);
        }
    }

    public static class LockOwner implements java.io.Serializable {

        private int node;
        private String tag;
        private long threadId;
        private long incarnation;
        transient private long stamp;
        
        public LockOwner() {
            computeLocalNodeid();
            computeTagName();
            computeIncarnationNumber();
            threadId = Thread.currentThread().getId();
        }

        private void setTimestamp() {
            stamp = System.currentTimeMillis();
        }

        private boolean hasExpired(long now, int timeout) {
            if ((now - stamp) > timeout) {
                return true;
            } else {
                return false;
            }
        }

        private String getSource() {
            StringBuffer buf = new StringBuffer();
            buf.append(Integer.toString(node));
            buf.append(":");
            buf.append(tag);
            return buf.toString();
        }

        public int getNode() {
            return node;
        }

        private String getTagName() {
            return tag;
        }
        
        private long getThreadId() {
            return threadId;
        }

        public long getIncarnation() {
            return incarnation;
        }

        public boolean isSameSource(LockOwner newOwner) {
            if (node != newOwner.getNode()) {
                return false;
            }
            if (!tag.equals(newOwner.getTagName())) {
                return false;
            }
            return true;
        }

        public boolean isSameOwner(LockOwner newOwner) {
            if (!isSameSource(newOwner)) {
                return false;
            }
            if (threadId != newOwner.getThreadId()) {
                return false;
            }
            if (incarnation != newOwner.getIncarnation()) {
                return false;
            }
            return true;            
        }
        

        //
        // Returns the incarnation number for the service as returned
        // by the local mailbox of the peer service. This may be slightly
        // out of date if the service has just restarted and the new mailbox
        // has not been propagated yet.
        //
        public boolean isValid() {
            if (incarnation == getIncarnationNumber()) {
                return true;
            } else {
                return false;
            }
        }
        
        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append(Integer.toString(node));
            buf.append(":");
            buf.append(tag);
            buf.append(":");
            buf.append(Long.toString(threadId));
            buf.append(":");
            buf.append(Long.toString(incarnation));
            return buf.toString();
        }

        private void computeLocalNodeid() {
            node = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).nodeId();
        }

        private void computeTagName() {
            Class cls = ServiceManager.currentManagedService();
            if (cls == null) {
                throw new LockOwnerError("can't the class of the current " +
                  "service");
            }
            NodeMgrService.Proxy proxy = 
              ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            tag = proxy.getTagByClass(cls);
        }

        private void computeIncarnationNumber() {
            incarnation = getIncarnationNumber();
        }

        private long getIncarnationNumber() {
            NodeMgrService.Proxy proxy = 
              ServiceManager.proxyFor(node);
            Service svc = proxy.getService(tag);
            if (svc == null) {
                throw new LockOwnerError("can't get the current service ");
            }
            return svc.getIncarnationNumber();
        }
    }
}