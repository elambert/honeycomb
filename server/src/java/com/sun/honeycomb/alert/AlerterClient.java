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
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ipc.Mboxd;
import com.sun.honeycomb.cm.ManagedServiceException;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;


/** *  Alerter 'client' on each node
 */
public class AlerterClient implements AlerterClientIntf {

    private static transient final Logger logger = 
        Logger.getLogger(AlerterClient.class.getName());

    private volatile boolean              keepRunning;
    private Thread                        thr;
    private AlertLocalEngine              engine;

    public AlerterClient() {
        keepRunning = true;
        thr = Thread.currentThread();
    }

    public void shutdown () {
        keepRunning = false;
        thr.interrupt();
        boolean stopped = false;
        while (!stopped) {
            try {
                thr.join();
                stopped = true;
            } catch (InterruptedException ignored) {
                
            }
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.info("AlerterClient now STOPPED");
        }        
    }

    public ManagedService.ProxyObject getProxy () {
        return new Proxy();
    }

    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
    }


    public void run() {

        // We choose the same period 
        int delay = Mboxd.PUBLISH_INTERVAL;

        // Create engine.
        engine = new AlertLocalEngine();

        while (keepRunning) {

            ServiceManager.publish (this);
            
            try {
                engine.update();
                engine.waitForMaster();

            } catch (InterruptedException ie) {
                logger.info("AlerterClient has been interrupted");
                break;
            }
        }
    }

    // RMI calls from AlerterServer
    public AlertTreeNode getBranch(int whichBranch)
        throws IOException, ManagedServiceException {
        return engine.getBranch(whichBranch);
    }
}




