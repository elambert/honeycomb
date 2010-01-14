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



package com.sun.honeycomb.cm;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Logger;
import java.io.File;
import java.io.UnsupportedEncodingException;

import com.sun.honeycomb.protocol.server.ProtocolService;
import com.sun.honeycomb.webdav.EmulatedWebdav;
import com.sun.honeycomb.common.LogEscapeFormatter;
import com.sun.honeycomb.multicell.MultiCell;
import com.sun.honeycomb.admin.mgmt.server.MgmtServer;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.emd.MetadataService;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.ndmp.NDMPService;

/**********************************************************************
 *
 * This is a fake NodeMgr - Not to be confused with the real one ;-)
 *
 **********************************************************************/

public class NodeMgr 
    implements Runnable {

    private static Logger LOG = null;
    private static final int SLEEP_TIME = 1000;
    private static final int SHUTDOWN_SLEEP = 2000;
    private static final int SHUTDOWN_RETRIES = 10;

    private static String cellid = null;

    private ThreadGroup rootThreadGroup;
    private ThreadGroup[] threads;
    private EmulatedService[] services;
    private int nbRunning;
    private Thread shutdownThread = null;

    private static NodeMgr instance = null;

    public static NodeMgr getInstance() {
        return(instance);
    }

    private static String emulatorRoot = null;

    public static synchronized String getEmulatorRoot() {
        if (emulatorRoot != null) {
            return(emulatorRoot);
        }
        emulatorRoot = System.getProperty("emulator.root");
        emulatorRoot = ClusterProperties.getInstance().getProperty("honeycomb.emulator.root", emulatorRoot);
        cellid = ClusterProperties.getInstance().getProperty("honeycomb.silo.cellid");
        File file = new File(emulatorRoot);
        if (!file.exists()) {
            file.mkdir();
        }


	file = new File(emulatorRoot +File.separatorChar + "var");
	if (!file.exists()) {
	    file.mkdir();
	}

	file = new File(emulatorRoot + File.separatorChar + "logs");
	if (!file.exists()) {
	    file.mkdir();
	}
        return(emulatorRoot);
    }


    private NodeMgr(EmulatedService[] nServices) {
        services = nServices;
    }

    public void start() {
        // Some initializations
        OAClient.getInstance();

        rootThreadGroup = new ThreadGroup("EmulatedMain");
        threads = new ThreadGroup[services.length];
        nbRunning = 0;
        
        for (int i=0; i<threads.length; i++) {
            String svcName = services[i].getName();
            threads[i] = new ThreadGroup(rootThreadGroup, svcName);
            new Thread(threads[i], services[i], svcName+"-Main").start();
            LOG.info("Starting service ["+
                     services[i].getName()+"]");
            nbRunning++;
        }

        while (nbRunning > 0) {
            for (int i=0; i<threads.length; i++) {
                if ( (threads[i] != null) && (threads[i].activeCount()==0) ) {
                    nbRunning--;
                    LOG.info("All the threads of service ["+
                             services[i].getName()+"] exited. "+
                             nbRunning+" services are still running");
                    threads[i] = null;
                }
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.finer("NodeMgr sees "+nbRunning+" active threads.");
                }
                if (shutdownThread != null && !shutdownThread.isAlive()) {
                    LOG.warning("SHUTDOWN thread exited prematurely !!!"+
                                " Forcing exit");
                    System.exit(0);
                }
            }
            synchronized (this) {
                try {
                    wait(SLEEP_TIME);
                } catch (InterruptedException ignored) {
                }
            }
        }
        
        LOG.info("The emulator is exiting");
    }

    public void run() {
        // Runs the shutdown
        if (threads == null) {
            return;
        }

        LOG.info("Shutting down the emulator");

        int nbRetries = SHUTDOWN_RETRIES;

        while ((nbRunning > 0) && (nbRetries > 0)) {
            for (int i=threads.length-1; i>=0; i--) {
                if ((threads[i] != null) && (threads[i].activeCount()>0)) {
                    if (nbRetries == SHUTDOWN_RETRIES) {
                        LOG.info("Calling shutdown in ["+
                                 services[i].getName()+"] service.");
                        services[i].shutdown();
                        LOG.info("Shutdown of ["+
                                 services[i].getName()+"] returned.");
                    }
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("Service ["+
                                 services[i].getName()+"] still has "+
                                 threads[i].activeCount()+" threads running");
                    }
                }
            }

            if (nbRetries == SHUTDOWN_RETRIES) {
                synchronized (this) {
                    notify();
                }
            }

            if (nbRunning > 0) {
                try {
                    Thread.sleep(SHUTDOWN_SLEEP);
                } catch (InterruptedException ignored) {
                }
            }
	    
            nbRetries--;
        }
	
        if (nbRunning > 0) {
            LOG.warning("SHUTDOWN TIMEOUT !!! Forcing exit");
        }

        System.exit(0);
    }
    
    public void shutdown() {
        OAClient.getInstance().shutdown();
        shutdownThread = new Thread(rootThreadGroup,this);
        shutdownThread.start();
    }

    public ServiceStatus[] status() {
        ServiceStatus[] result = new ServiceStatus[services.length];
        for (int i=0; i<services.length; i++) {
            result[i] = new ServiceStatus( services[i].getName(),
                                           threads[i] != null ? threads[i].activeCount()
                                           : 0 );
        }
        return(result);
    }

    private boolean isConfiguredWithMulticell() {
        String isMulticell = 
            ClusterProperties.getInstance().getProperty("honeycomb.config.multicell");
        if ((isMulticell != null) && (isMulticell.equals("true"))) {
            return true;
        } else {
            return false;
        }
    }


    public static void main(String[] arg) {

        /***** Set up the log file first *****/
        Logger rootLogger = Logger.getLogger("");
        
        Handler[] hdlers = rootLogger.getHandlers();
        for (int i=0; i<hdlers.length; i++) {
            rootLogger.removeHandler(hdlers[i]);
        }

        String logFile = getEmulatorRoot() + "/logs/emulator.log";

        FileHandler fileHandler = null;
        try {
            fileHandler = new FileHandler(logFile, false);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        fileHandler.setFormatter(new LogEscapeFormatter());
        rootLogger.addHandler(fileHandler);
        
        LOG = Logger.getLogger(NodeMgr.class.getName());
        
        /***** The log file is setup *****/
        
        LOG.info("\n\n**********************************************************************\n\n"+
                 "Honeycomb emulator starting - Welcome !\n\n"+
                 "Copyright 2007 Sun Microsystems, Inc.  All rights reserved.\n"+
                 "Use is subject to license terms.\n\n"+
                 "**********************************************************************\n\n");

        LOG.info("The emulator root is at ["+
                 emulatorRoot+"]");

        LOG.info("The log file is at ["+
                 logFile+"]");

        EmulatedService[] services = null;
        //
        // cellid is only defined when we run in multicell mode; if not,
        // we dont' run multicell and mgmtServer and all the WS management
        // stuff
        try {
            if (cellid == null) {
                services = new EmulatedService[4];
            } else {
                LOG.info("start emulator in multicell mode...");
                services = new EmulatedService[6];
                services[4] = new MultiCell();
                services[5] = new MgmtServer();
            }
            services[0] = new MetadataService();
            services[1] = new ProtocolService();
            services[2] = new EmulatedWebdav();
            services[3] = new NDMPService();
            instance = new NodeMgr(services);
            instance.start();
        } catch (Exception e) {
            LOG.log(Level.SEVERE,
                    "Failed to start ["+e.getMessage()+"]",
                    e);
        }
    }

    public static class ServiceStatus {
        public String name;
        public int nbThreads;

        private ServiceStatus(String nName,
                              int nNbThreads) {
            name = nName;
            nbThreads = nNbThreads;
        }
    }
}
