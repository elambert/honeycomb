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



package com.sun.honeycomb.admin.mgmt.server;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.Map;
import java.util.HashMap;

import org.mortbay.jetty.Server;
import org.mortbay.util.InetAddrPort;
import org.mortbay.jetty.servlet.ServletHttpContext;
import java.io.IOException;
import org.mortbay.util.MultiException;

import com.sun.honeycomb.admin.mgmt.AdminException;
import com.sun.honeycomb.multicell.MultiCellConfig;
import com.sun.honeycomb.config.ClusterProperties;


public abstract class MgmtServerBase  {
    
    public static transient final Logger logger = 
        Logger.getLogger(MgmtServer.class.getName());

    static protected final String WS_MANAGEMENT_SERVLET =
        "com.sun.ws.management.server.WSManServlet";

    static protected final int    MGMT_PORT = 9000;

    protected volatile boolean    keepRunning;
    protected Thread              thr;
    protected ClusterProperties   config;
    protected Server              server;    
    protected int                 mgmtPort;


    public MgmtServerBase() {
        keepRunning = false;
        thr = Thread.currentThread();
        config = ClusterProperties.getInstance();
        try {
            mgmtPort = 
                Integer.parseInt(config.getProperty(MultiCellConfig.PROP_MGMT_PORT));
        } catch (Exception exc) {
            throw new RuntimeException("MgmtServer cannot start because " +
              "the port number is invalid");
        }
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
        destroyHttpServer();

        if (logger.isLoggable(Level.INFO)) {
            logger.info("MgmtServer now STOPPED");
        }        
    }

    public void syncRun() {
    }


    protected abstract void executeInRunLoop();

    public void run() {

        int delay = 5000;

        createHttpServer();
        keepRunning = true;

        while (keepRunning) {

            executeInRunLoop();
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
            }

        }
    }

    private void createHttpServer() {

        try {
            server = new Server();
            server.addListener(new InetAddrPort(mgmtPort));

            ServletHttpContext context = new ServletHttpContext();
            context.addServlet("/", WS_MANAGEMENT_SERVLET);
            context.setContextPath("/");
            server.addContext(context);

            server.start();

            if (logger.isLoggable(Level.INFO)) {
                logger.info("The HTTP mgmt server has been started on port " +
                    mgmtPort);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (MultiException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void destroyHttpServer() {
        if (server != null) {
            try {
                server.stop();
            } catch (InterruptedException ignored) {
            }
            server.destroy();
            server = null;
        }
    }

}    
