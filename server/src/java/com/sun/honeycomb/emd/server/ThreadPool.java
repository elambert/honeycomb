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
import java.net.Socket;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.remote.ObjectBroker;
import java.io.IOException;
import java.util.LinkedList;



public class ThreadPool {

    private static final Logger LOG = Logger.getLogger("ThreadPool");

    /**********************************************************************
     *
     * Definition of the inner class ServerThread
     *
     **********************************************************************/

    private class ServerThread
        implements Runnable {
        
        private boolean running = true;
        private Socket requestPending;
        private Thread svcThread;
        private String name;


        ServerThread(String threadName) {
            running = false;
            requestPending = null;
            name = threadName;
        }

        void startThread()
            throws IllegalThreadStateException {
            svcThread = new Thread(this);
            svcThread.setName(name);
            running = true;
            svcThread.start();
        }

        void stopThread() {
            running = false;
            svcThread.interrupt();

            boolean stopped = false;
            while (!stopped) {
                try {
                    svcThread.join();
                    stopped = true;
                } catch (InterruptedException ignored) {
                }
            }
            svcThread = null;
        }

        String getName() {
            if (svcThread != null) {
                return svcThread.getName();
            } else {
                return "null";
            }
        }

        void giveNewRequest(Socket newRequest) 
            throws EMDException {
            synchronized (this) {
                if (requestPending != null) {
                    throw new EMDException("Thread "+ svcThread.getName() + 
                                           " is already busy");
                }

                requestPending = newRequest;
                notify();
            }
        }
                
        public void run() {
            LOG.fine("The thread "+ svcThread.getName() +" has been started");

            while (running) {
                synchronized (this) {
                    while ((running)
                           && (requestPending == null)) {
                        try {
                            wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                if (running) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("The thread "+ svcThread.getName() + 
                                 " is servicing a request");
                    }
                    
                    try {

                        ObjectBroker broker = new ObjectBroker(requestPending);
                        broker.serverDispatch();

                    } catch (EMDException e) {
                        LOG.log(Level.SEVERE,
                                "Failed to perform the request",
                                e);
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE,
                                "Failed to perform the request",
                                e);
                    } catch (Throwable e) {
                        LOG.log(Level.SEVERE,
                                "***** UNEXPECTED EXCEPTION *****",
                                e);
                    } finally {
                        if (requestPending != null) {
                            if (!requestPending.isClosed()) {
                                try {
                                    requestPending.close();
                                } catch (IOException ignored) {
                                }
                            }
                            requestPending = null;
                        }

                        synchronized (freeThreads) {
                            freeThreads.add(this);
                            freeThreads.notify();
                        }
                    }

                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("The thread "+ svcThread.getName() + 
                                 " is back without any request");
                    }
                    // We are exiting, go back to the pool...
                }
            }
            LOG.info("The thread " + svcThread.getName() + " is exiting");
        }
    }

    /**********************************************************************
     *
     * Implementation of the ThreadPool class
     *
     **********************************************************************/

    private ServerThread[] threads;
    private LinkedList freeThreads;
    private final int nbThreads;

    public ThreadPool(int newNbThreads) {
        nbThreads = newNbThreads;
        freeThreads = new LinkedList();
        threads = new ServerThread[nbThreads];
        for (int i=0; i<nbThreads; i++) {
            threads[i] = new ServerThread("MDServerThread-" + i);
            freeThreads.add(threads[i]);
        }
    }

    public void start() {
        LOG.info("Starting all the server servicing threads");
        
        for (int i=0; i<threads.length; i++) {
            try {
                threads[i].startThread();
            } catch (IllegalThreadStateException ilState) {

                for (int j = i; j >=0; j--) {
                    threads[j].stopThread();
                }
                throw ilState;
            }
        }

        LOG.info("All the server servicing threads have been started");
    }

    public void stop() {
        LOG.info("Stopping all the server servicing threads");

        for (int i=0; i<threads.length; i++) {
            threads[i].stopThread();
        }

        LOG.info("All the server servicing threads have been stopped");
    }

    public void serveNewRequest(Socket request) {
        ServerThread thread = null;

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Received a new request to serve");
        }

        while (thread == null) {
            synchronized (freeThreads) {
                while (freeThreads.size() == 0) {
                    try {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("Waiting for one free MD thread");
                        }
                        freeThreads.wait();
                    } catch (InterruptedException ignored) {
                    }
                }

                thread = (ServerThread)freeThreads.removeFirst();
            }

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Giving the request to thread "+ thread.getName());
            }
            
            try {
                thread.giveNewRequest(request);
            } catch (EMDException e) {
                LOG.log(Level.SEVERE,
                        "Serious exception in the thread attribution algorithm",
                        e);
                thread = null;
            }
        }
    }

    /**
     * Returns the current load of that thread pool.
     *
     * The returned result is a double between 0 an 1 (included).
     * @return the load
     */

    public double getLoad() {
        int nbFreeThreads = 0;
        
        synchronized (freeThreads) {
            nbFreeThreads = freeThreads.size();
        }

        return( (double)1 - ((double)nbFreeThreads / (double)nbThreads) );
    }
}
        
