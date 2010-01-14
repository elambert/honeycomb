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



/**
 *  Base class for dummy service for testing purposes.
 *
 *  Use TestFaultyService if you want fault injection, TestSimpleService otherwise.
 *
 *  Running multiple instances of TestService with different tags works fine,
 *  because the service gets its name from tag in node_config.xml so 
 *  instances can be distinguished from each other by their log messages.
 * 
 */

package com.sun.honeycomb.cm;

import java.util.logging.Logger;

public class TestService implements ManagedService {
    
    protected String serviceName; // name of this instance of TestService
    protected boolean keeprunning;
    protected Logger logger;

    protected int loginterval;    // how often to log "i'm alive" message
    protected int runloop_pause;  // sleep time, in seconds, in run loop
    protected int shutdown_pause; // sleep time, in seconds, in shutdown method
    protected int startup_pause;  // sleep time, in seconds, in constructor
    protected boolean dothrow;    // throw an exception from run loop?
    protected boolean doexit;     // prematurely return from run loop?

    /** Preferred constructor sets service name by its tag, known to the caller.
     *  Service name is used in logging to distinguish between multiple TestService instances.
     */
    protected TestService(String tag) {
        serviceName = "TestService." + tag;
        
        logger = Logger.getLogger(serviceName);
        keeprunning = true;
        loginterval = 60;  // log from runloop every minute

        // default property values (no failure injection)
        runloop_pause = 5;
        shutdown_pause = 0;
        startup_pause = 0;
        dothrow = false;
        doexit = false;
    }

    /** Run routine of the service.
     */
    public void run() {
        logger.info(serviceName + " RUNNING");
        int count = 0;
        boolean quiet = true; // don't log frequently

        doSleep(runloop_pause, quiet);

        if (dothrow) {
            throw new RuntimeException(serviceName + " INTENTIONAL FAILURE");
        }

        if (doexit) {
            logger.warning(serviceName + " INTENTIONAL EXIT FROM run() METHOD");
            return;
        }

        while (keeprunning) {
            if (count % loginterval == 0)
                logger.info(serviceName + " OK at time: " + count);
            doSleep(runloop_pause, quiet);
            count += runloop_pause;
        }
        logger.info(serviceName + " RUNLOOP EXITING");
    }

    public void syncRun() {
        logger.info(serviceName + " SYNC RUNNING");
    }

    /** No-op: test service does not export a proxy
     */
    public ManagedService.ProxyObject getProxy() {
        return null;
    }
        
    /** Log service exit
     */
    public void shutdown() {
        logger.info(serviceName + " SHUTDOWN CALLED");
        doSleep(shutdown_pause); // blocks the caller!
        keeprunning = false;
        logger.info(serviceName + " SHUTDOWN SET");
    }

    /** Guarantees sleep for given number of seconds;
     *  if interrupted, goes right back to sleep.
     */
    protected void doSleep(int seconds) {
        doSleep(seconds, false); // not quiet, do logging
    }

    protected void doSleep(int seconds, boolean quiet) {
        if (seconds == 0)
            return; // shortcut
        if (!quiet)
            logger.info(serviceName + " Sleeping for " + seconds + " seconds");
        long wakeup = System.currentTimeMillis() + seconds * 1000;

        while (System.currentTimeMillis() < wakeup) { 
            try {
                Thread.sleep(seconds * 1000);
            } catch (InterruptedException e) {
                long rem = (wakeup - System.currentTimeMillis()) / 1000;
                logger.info(serviceName + " Sleep interrupted - " + rem + " seconds remaining");
            }
        }
    }
}
