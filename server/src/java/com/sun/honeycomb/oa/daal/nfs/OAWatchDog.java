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



package com.sun.honeycomb.oa.daal.nfs;

import java.util.logging.Logger;
import java.util.logging.Level;



public class OAWatchDog implements Runnable {
    
    private static final long IDLE_TIMEOUT = 300000; // 5 min.

    private static final Logger LOG = Logger.getLogger(OAWatchDog.class.getName());
    private static OAWatchDog instance = null;

    public static synchronized OAWatchDog getInstance() {
        if (instance == null) {
            instance = new OAWatchDog();
            Thread t = new Thread(instance, "OAWatchDog");
            t.setDaemon(true);
            instance.setThread(t);
            t.start();
            Thread.yield();
        }
        return(instance);
    }

    private WatchDogTask tasks;
    private Thread wdThread;

    private OAWatchDog() {
        tasks = null;
        wdThread = null;
    }

    private void setThread(Thread t) {
        wdThread = t;
    }

    private void interruptWdThread() {
        // We have a new first element
        wdThread.interrupt();
    }

    private synchronized WatchDogTask getTask() {
        long time = System.currentTimeMillis();
        while (tasks != null) {
            if (tasks.cancelled) {
            } else if (tasks.expireTime<=time) {
                tasks.interruptThread();
            } else {
                return(tasks);
            }
            tasks = tasks.next;
        }
        return(null);
    }

    public void run() {
        while (true) {
            WatchDogTask currentTask = getTask();
            long timeToWait = (currentTask == null) ? IDLE_TIMEOUT
                : (currentTask.expireTime-System.currentTimeMillis());
            try {
                if (timeToWait > 0) {
                    Thread.currentThread().sleep(timeToWait);
                }
            } catch (InterruptedException e) {
            }
            if ((currentTask != null) && (!currentTask.cancelled) && (currentTask.expireTime<=System.currentTimeMillis())) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("An OA thread will be interrupted by the watchdog");
                }
                currentTask.interruptThread();
            }
        }
    }

    public WatchDogTask register(long timeout) {
        WatchDogTask task = new WatchDogTask(Thread.currentThread(),
                                             timeout);
        insert(task);
        //dump();
        return(task);
    }

    /*
     * For debug purpose
     */
    private synchronized void dump() {
        WatchDogTask task = tasks;
        int count = 1;
        StringBuffer sb = new StringBuffer("\n"+System.currentTimeMillis()+"\n");
        while (task != null) {
            sb.append(count+": "+task+"\n");
            count++;
            task = task.next;
        }
        LOG.info(sb.toString());
    }

    private synchronized void insert(WatchDogTask t) {
        if (tasks == null) {
            tasks = t;
            interruptWdThread();
            return;
        }

        if (t.compareTasks(tasks) < 0) {
            t.next = tasks;
            tasks = t;
            interruptWdThread();
            return;
        }

        WatchDogTask current = tasks;
        while ((current.next != null) && (current.next.compareTasks(t) < 0)) {
            current = current.next;
        }
        t.next = current.next;
        current.next = t;
    }

    public static class WatchDogTask {
        private WatchDogTask next;

        private long expireTime;
        private Thread thread;
        private boolean cancelled;

        private WatchDogTask(Thread t,
                             long timeout) {
            next = null;
            expireTime = System.currentTimeMillis()+timeout;
            thread = t;
            cancelled = false;
        }

        public void cancel() {
            cancelled = true;
        }

        private void interruptThread() {
            thread.interrupt();
            cancelled = true;
        }

        public int compareTasks(WatchDogTask task) {
            if (task.expireTime == expireTime) {
                return(0);
            }
            return(expireTime<task.expireTime ? -1 : 1);
        }

        public String toString() {
            return(cancelled+" "+expireTime);
        }
    }
}
