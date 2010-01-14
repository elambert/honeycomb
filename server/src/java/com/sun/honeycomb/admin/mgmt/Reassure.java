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



package com.sun.honeycomb.admin.mgmt;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;

//
// Used by mgmt adapters to send asynchronous events to the CLI
// while the main thread is blocked on RMI calls or waiting
// for threads to join.
//
// This is more tricky than it looks because the Event is an 
// instance of Runnable whose thread executes the main handler
// on behalf of the ws-mgmt thread handling the request. 
//
public class Reassure extends Thread {

    private static final long SLEEP_INTERVAL = (20 * 1000); // 20 sec
    private static final String DEFAULT_MSG = ".";
 
    private static String LOG_PREFIX = "";

    private volatile boolean pause;
    private volatile boolean running;
    private String message;
    private EventSender evt;
    private int incarnation;
    private long threadId;

    private static transient final Logger logger = 
        Logger.getLogger(Reassure.class.getName());

    public Reassure(EventSender evt) {            
        super();
        this.incarnation = evt.getIncarnation();
        this.evt = evt;
        this.pause = false;
        this.message = DEFAULT_MSG;
        this.threadId = -1;
        this.running = false;
    }

    private void processMessage() {

        String curMessage = null;

        synchronized (this) {
            curMessage = message;
            if (!message.equals(DEFAULT_MSG)) {
                message = DEFAULT_MSG;
            }
        }

        try {
            evt.sendAsynchronousEvent(curMessage);
        } catch (MgmtException e) {
            logger.log(Level.SEVERE,
              LOG_PREFIX + " Failed to send the event: ", e);
        } finally {
            synchronized (this) {
                if (incarnation != evt.getIncarnation()) {
                    logger.warning(LOG_PREFIX +
                      " detected wrong incarnation for event, exiting..");
                    running = false;
                    return;
                }
            }
        }
    }

    public void run() {

        threadId = Thread.currentThread().getId();
        LOG_PREFIX = "ReassureThread " + threadId + " ";

        logger.info(LOG_PREFIX + "running");

        running = true;
        while (running) {

            synchronized (this) {
                try {
                    wait(SLEEP_INTERVAL);
                } catch(InterruptedException ignored) {
                    logger.info(LOG_PREFIX + "got interrupted");
                    running = false;
                    break;
                }
            }
            if (!pause) {
                processMessage();
            }
        }
        logger.info(LOG_PREFIX + "stopped");
    }

    //
    // All those methods are called from a different context
    //
    public void setMessage(String newMessage) {
        synchronized (this) {
            message = newMessage;
            notify();
        }           
    }


    public void pause() {
        pause = true;
    }

    public void unpause() {
        pause = false;
    }

    public void safeStop() {

        synchronized (this) {
            if (running == false) {
                logger.info("safeStop already called onReassure Thread " + 
                  threadId + ", return");
            }
            running = false;
        }

        interrupt();
        boolean stopped = false;
        while (!stopped) {
            try {
                join();
                stopped = true;
            } catch (InterruptedException ignored) {
            }
        }
    }
}
