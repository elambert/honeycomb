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



package com.sun.honeycomb.hctest.util;

import java.util.LinkedList;
import java.util.Iterator;

import com.sun.honeycomb.test.util.*;

/**
    Monitor read/write channels and log performance to dbase.
    Should be just 1 per JVM.
*/

public class ChannelMonitor extends Thread {

    private static long interval = 5000;  // 5 sec
    private static ChannelMonitor instance = new ChannelMonitor();
    private static boolean running = false;
    private static boolean finished = false;
    private static boolean disabled = false;

    private ChannelMonitor() {
    }

    public static void init() {
        synchronized(instance) {

            if (disabled)
                return;

            if (finished) {
                Log.ERROR("attempt to init ChannelMonitor after done() called");
                return;
            }
            if (!running) {
                Log.INFO("ChannelMonitor starting thread");
                instance.start();
                running = true;
            }
        }
    }

    public static void done() {
        if (disabled) {
            Log.INFO("ChannelMonitor disabled, no bandwidth snapshots were logged");
            return;
        }
        running = false;
        Log.INFO("ChannelMonitor.done() called");
    }

    public void run() {

        AuditDBMetricsClient db = null;
        try {
            db = new AuditDBMetricsClient();
        } catch (Throwable t) {
            Log.ERROR("===================== ChannelMonitor can't get db, so no logging: " + t.getMessage());
            disabled = true;
            running = false;
            finished = true;
        }
        if (db == null  &&  running) {
            Log.ERROR("===================== ChannelMonitor db null, so no logging");
            running = false;
            finished = true;
        }

        while(running) {
            try {
                long totalBytes = 0;

                LinkedList ll = DigestableReadChannel.getList();
                synchronized (ll) {
                    Iterator it = ll.iterator();
                    while (it.hasNext()) {
                        DigestableReadChannel ch = (DigestableReadChannel) 
                                               it.next();
                        long bytes = ch.getNumCheckBytes();
                        if (bytes > 0)
                            totalBytes += bytes;
                        if (!ch.isOpen())
                            it.remove();
                    }
                }
                if (totalBytes > 0) {
                    // stored
                    Log.DEBUG("ChannelMonitor store rate " + 
                           BandwidthStatistic.toMBPerSec(totalBytes, interval));
                    db.logPerfBW(AuditDBMetricsClient.STORE, 
                                 interval, totalBytes);
                }

                totalBytes = 0;
                ll = DigestableWriteChannel.getList();
                synchronized (ll) {
                    Iterator it = ll.iterator();
                    while (it.hasNext()) {
                        DigestableWriteChannel ch = (DigestableWriteChannel) 
                                                it.next();
                        long bytes = ch.getNumCheckBytes();
                        if (bytes > 0)
                            totalBytes += bytes;
                        if (!ch.isOpen())
                            it.remove();
                    }
                }
                if (totalBytes > 0) {
                    // retrieved
                    Log.DEBUG("ChannelMonitor rtrv rate " + 
                           BandwidthStatistic.toMBPerSec(totalBytes, interval));
                    db.logPerfBW(AuditDBMetricsClient.RETRIEVE, 
                                 interval, totalBytes);
                }
            } catch (Throwable t) {
                Log.ERROR("ChannelMonitor caught: " + t + "\n" + 
                          Log.stackTrace(t));
                //errors++;
            }
            try {
                Thread.sleep(interval);
            } catch (Exception e) {}
        }
        Log.INFO("ChannelMonitor thread finished");
        finished = true;
    }
}
