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

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.ConfigPropertyNames;

import java.net.ServerSocket;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.Socket;
import java.io.IOException;
import com.sun.honeycomb.emd.common.EMDException;
import java.net.SocketTimeoutException;
import com.sun.honeycomb.emd.remote.ConnectionFactory;
import java.io.InterruptedIOException;



/**
 * This class implements the metadata server .
 *
 * It is reponsible for :
 * - getting connections ;
 * - managing a thread pool and associating a connection to a free thread.
 */

public class MDServer 
    implements Runnable {

    private static final Logger LOG = Logger.getLogger("MDServer");

    /**********************************************************************
     *
     * Global constants
     *
     **********************************************************************/

    public  static final int MD_SERVER_PORT             = 53264;
    private static final int MD_SERVER_DEFAULT_THREADS  = 10;
    public  static final int MD_SO_TIMEOUT              = 10000;

    /**********************************************************************
     *
     * Class fields
     *
     **********************************************************************/

    private Thread thServer;
    private ServerSocket socket;
    private ThreadPool threadPool;
    private boolean running;

    /**********************************************************************
     *
     * Class methods
     *
     **********************************************************************/

    public MDServer() {
        socket = null;

	ClusterProperties props = ClusterProperties.getInstance();
	int numThreads =
            props.getPropertyAsInt(ConfigPropertyNames.PROP_MDSERVER_MAXTHREADS,
                                   MD_SERVER_DEFAULT_THREADS);
        LOG.info("MDServer initialized with " + numThreads + " threads.");
        threadPool = new ThreadPool(numThreads);
        running = false;
    }

    public void startServer()
        throws EMDException {

        try {
            thServer = new Thread(this);
            socket = new ServerSocket(MD_SERVER_PORT);
            socket.setSoTimeout(MD_SO_TIMEOUT);
            socket.setReuseAddress(true);
        } catch (IOException e) {
            EMDException newe = new EMDException("Couldn't create the server socket");
            newe.initCause(e);
            throw newe;
        }

        try {
            threadPool.start();
            thServer.start();
        } catch (IllegalThreadStateException ilState) {
            EMDException newe = new EMDException("Couldn't start the server threads");
            newe.initCause(ilState);
            throw newe;
        }
    }
    
    public void stopServer() 
        throws EMDException {
        
        LOG.info("Received a request to shutdown the MD server");
        if (!running) {
            return;
        }

        // Stopping the server thread
        running = false;
        thServer.interrupt();

        boolean stopped = false;
        while (!stopped) {
            try {
                thServer.join();
                stopped = true;
            } catch (InterruptedException ignored) {
            }
        }

        // Stop the servicing threads
        threadPool.stop();

        // Close the socket
        try {
            socket.close();
            socket = null;
        } catch (IOException e) {
            EMDException newe = new EMDException("Couldn't close the server socket");
            newe.initCause(e);
            throw newe;
        }
    }

    public void run() {
        running = true;
        
        LOG.info("The MD server thread has been started");

        while (running) {
            try {
                Socket request = socket.accept();
                request.setSendBufferSize(ConnectionFactory.SOCKET_BUFFER_SIZE);
                request.setTcpNoDelay(true);
                request.setKeepAlive(true);
                threadPool.serveNewRequest(request);
                
            } catch (SocketTimeoutException ignored) {
            } catch (InterruptedIOException e) {
                // When the server is asked to stop and the node manager interrupts us, we'll get that
                // exception. Log only if running ...
                if (running) {
                    LOG.log(Level.SEVERE,
                            "Got an InterruptedIOException in the MD server thread",
                            e);
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE,
                        "Got an IOException in the MD server thread",
                        e);
            }
        }
        
        LOG.info("The MD server thread is exiting");
    }

    public double getLoad() {
        return(threadPool.getLoad());
    }
}
