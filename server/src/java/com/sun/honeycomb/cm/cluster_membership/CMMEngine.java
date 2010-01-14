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



package com.sun.honeycomb.cm.cluster_membership;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import java.util.logging.*;
import java.util.logging.Level;


/**
 * Threads Management - handle HA aspect of CMM and
 * is responsible to spawn and restart CMM threads.
 * This class implements a watchdog. If the watchdog has not been
 * refreshed within a period of time, the JVM exits.
 */
final class CMMEngine extends ThreadGroup implements Runnable {

    public static final Logger logger =
        Logger.getLogger(CMMEngine.class.getName());

    private static final int nbThreads = 4;
    private static final int WAIT_DELAY = (10 * 1000); // 10s
    
    private SenderTask sender;
    private ReceiverTask receiver;
    private LobbyTask lobby;
    private Thread threads[];
    private int nbrestarts;
    private long lastfaultTime;
    private boolean isRunning;
    private volatile long lastHbtTime;
    private volatile boolean lock;

    CMMEngine() {
        super("CMMEngine");
        sender = new SenderTask();
        lobby = CMM.getLobbyImpl(sender);
        receiver = new ReceiverTask(lobby, sender);
        threads = new Thread[nbThreads];
        lastHbtTime = 0;
        lastfaultTime = 0;
        nbrestarts = 0;
        isRunning = false;
        lock = false;
    }


    /**
     * Start the CMM engine.
     */
    CMMEngine start() {
        //
        // Log the registered configuration files.
        //
        for (int i = 0; i < CMMApi.CFGFILES.size(); i++) {
            CMMApi.ConfigFile cfg = (CMMApi.ConfigFile) CMMApi.CFGFILES.get(i);
            logger.info("registered configuration file: " + cfg.name());
        }
        //
        // Initialize and start the CMM threads.
        //
        isRunning = true;
        lastHbtTime = System.currentTimeMillis();
        threads[0] = new Thread(this, receiver, "ReceiverTask");
        threads[1] = new Thread(this, lobby, "LobbyTask");
        threads[2] = new Thread(this, sender, "SenderTask");
        threads[3] = new Thread(this, this, "WatchdogTask");
        for (int i = 0; i < nbThreads; i++) {
            threads[i].start();
            logger.info("ClusterMgmt - Started " + threads[i].getName());
        }
        return this;
    }

    /**
     * Reset the CMM engine.
     */
    private void reset() {
        sender.reset();
        lobby.reset();
        receiver.reset();
        isRunning = false;
    }

    /**
     * Get the lobby task.
     */
    LobbyTask lobbyTask() {
        return lobby;
    }
    
    /**
     * Get the receiver task.
     */
    ReceiverTask receiverTask() {
        return receiver;
    }
    
    /**
     * Get the sender task.
     */
    SenderTask senderTask() {
        return sender;
    }
    
    /**
     * CMM internal heartbeat
     */
    void heartbeat() {
        lastHbtTime = System.currentTimeMillis();
    }
    
    /**
     * CMM Watchdog task
     * If an heartbeat is not received within WATCHDOG_TIMEOUT, the JVM exits.
     */
    public void run() {
        
        while (isRunning) 
        {
            long last = System.currentTimeMillis() - lastHbtTime;
            if (last > CMM.WATCHDOG_TIMEOUT) {
                // this code is disabled for now
                //logger.severe("ClusterMgmt - WATCHDOG TIMEOUT - EXITING");
                //System.exit(1);
                logger.severe("ClusterMgmt - WATCHDOG TIMEOUT");
            }
            try {
                Thread.sleep(CMM.WATCHDOG_TIMEOUT);
            } catch (InterruptedException e) {
                logger.warning("ClusterMgmt - WatchdogTask interrupted");
            }
        }
    }
    
    /**
     * Catch all unchecked exceptions from all CMM threads.
     */
    public void uncaughtException(Thread t, Throwable e) {

        if (e instanceof CMMError) {
            logger.severe("ClusterMgmt - CMM ERROR - EXITING " + e);
            System.exit(1);
        }

        // restart mechanism - synchronized on the first dying thread
        synchronized (this) {
            logger.info("ClusterMgmt - " + lock + " name " + t.getName());
            if (lock) {
                return;
            }
            lock = true;
        }

        logger.log(Level.SEVERE,
				   "ClusterMgmt - Thread " + t + " got exception " + e,
				   e);

        e.printStackTrace();
        
        long now = System.currentTimeMillis();
        if (now - lastfaultTime > CMM.RESTART_WINDOW) {
            nbrestarts = 0;
        }
        lastfaultTime = now;

        if (++nbrestarts > CMM.MAX_RESTARTS) {
            logger.severe("ClusterMgmt - CMM TOO MANY ERRORS - EXITING");
            System.exit(1);
        }

        reset();
        for (int i = 0; i < nbThreads; i++) {
            if (threads[i] != Thread.currentThread()) {
                try {
                    threads[i].interrupt();
                    threads[i].join(WAIT_DELAY);
                    if (threads[i].isAlive()) {
                        logger.severe("ClusterMgmt - failed to wait for " +
                                      " thread " + threads[i]
                                      );
                        System.exit(1);
                    }
                } catch (InterruptedException x) {
                    logger.severe("ClusterMgmt - CMM ERROR - EXITING " + x);
                    System.exit(1);
                }
            }
        }
        logger.severe("ClusterMgmt - CMM - RESTARTS");
        lock = false;
        start();
    }
}
