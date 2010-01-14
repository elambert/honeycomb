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



package com.sun.honeycomb.protocol.server;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.net.ServerSocket;
import java.util.Iterator;
import java.util.logging.Level;

import org.mortbay.util.MultiException;

import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.InternalException;

public class ProtocolService extends ProtocolBase 
implements ProtocolManagedService {

    private static final long PROXY_UPDATE_PERIOD = (5 * 1000); // 5s
    
    private volatile boolean isReady = false;
    private volatile boolean isRunning = false;
    private volatile ProtocolProxy proxy;

    private static ProtocolService protocolService = null;
    
    /**
     * Public interface to get the current API service
     */
    static public ProtocolService getProtocolService() {
        return protocolService;
    }

    /**
     * Return true if the API server can handle a client request.
     * Called in the data-path to check if a request can be accepted.
     */
    static public boolean isReady() {
        if (protocolService == null) {
            return false;
        }
        return (protocolService.isRunning && protocolService.isReady);
    }
    
    /**
     * Return true if the API server is running.
     * Called in the error-path to check if we can return an error
     * to the client.
     */
    static public boolean isRunning() {
        if (protocolService == null) {
            return false;
        }
        return protocolService.isRunning;
    }
    
    /**
     * Instantiate the API service.
     * Called only once during the life cycle of the JVM
     */
    public ProtocolService() throws IOException {
        super();
        if (protocolService != null) {
            logger.warning("ProtocolService instance already exists, ignore...");
        }
        protocolService = this;
        proxy = new ProtocolProxy();
    }
    
    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
        try {
            // Get the data VIP from the PlatformService
            String dataVIP = getDataVIP();
            init(dataVIP);
        } catch (IOException io) {
            throw new InternalException("syncRun() failed " + io);
        }
        isRunning = true;
        isReady = false;
    }

    /**
     * Starts the HTTP server.
     */
    public void run() {
        logger.info("Going to running state");

        try {
            server.start();
        } catch (MultiException e) {
            logger.severe("Failed to run: " + e.getMessage());
            throw new InternalException(e);
        }

        while (isRunning) 
        {
            proxy = new ProtocolProxy();
            boolean newIsReady = proxy.isAPIReady();
            
            if (newIsReady != isReady) {
                logger.info("isReady changing to " + newIsReady);
                isReady = newIsReady;
                ServiceManager.publish(this);
            }
            try {
                Thread.currentThread().sleep(PROXY_UPDATE_PERIOD);
            } catch (InterruptedException ignore) {
                logger.warning("API service thread interrupted");
            }
        }
        isReady = false;
    }

    /**
     * Gracefully stops the HTTP server. This means that the server
     * doesn't stop until all currently running requests have been
     * handled.
     */
    public void shutdown() {
        logger.info("Shutdown");
        isRunning = false;
        try {
            ServerSocket sockets[] = httpListener.getMultiPortSocketServer();
            for (int i = 0; i < sockets.length; i++) {
                try {
                    sockets[i].close();
                    logger.info("closes socket " + i + ", isClosed = "  +
                                sockets[i].isClosed() + 
                                ", bound = " + sockets[i].isBound());
                } catch (IOException io) {
                    
                }
            }
            server.stop(true);
            server.join();
            server.destroy();
        } catch (InterruptedException e) {
            logger.severe("Failed to shutdown: " + e.getMessage());
        }
    }

    /**
     * Return the current API proxy
     */
    public ManagedService.ProxyObject getProxy() {
        return proxy;
    }

    /**
     * Gets the IP address the Protocol service should bind to from the
     * PlatformService proxy. Copied from NFSService.java.
     *
     * @return IP address
     */
    private static String getDataVIP() {
        Object obj = ServiceManager.
            proxyFor(ServiceManager.LOCAL_NODE, "PlatformService");
        if (obj == null || !(obj instanceof PlatformService.Proxy))
            return null;

        PlatformService.Proxy proxy = (PlatformService.Proxy) obj;
        return proxy.getDataVIPaddress();
    }

}
