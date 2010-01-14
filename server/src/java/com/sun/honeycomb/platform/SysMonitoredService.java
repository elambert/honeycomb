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



package com.sun.honeycomb.platform;

import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.Commands;

import com.sun.honeycomb.platform.MonitoredService;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;

/**
 * <p> An java wrapper around the system scripts implementing the
 * MonitorService interface.  The assumption is that all services are
 * started from a initrd-like or svcadm script defined by {@name}
 * which takes start, stop, and restart arguments.  This class can be
 * extended to provide various type of MonitoredService.  </p>
 */
public class SysMonitoredService implements MonitoredService {
    private static final Logger logger 
        = Logger.getLogger(SysMonitoredService.class.getName());

    private String  name;
    private String  checkProc;
    private boolean doShutdownOnExit;

    static Commands commands = Commands.getCommands();

    /** <p>
     * Creates a new MonitoredService instance.
     * </p>
     * Note that there is no dependecy checking between these services.
     * It is assumed that every MonitoredService can run independently from
     * every other or that the underlying OS takes care of the service
     * dependencies. For example, creating a MonitoredService for
     * /etc/init.d/network and issuing a stop command will not stop
     * any Monitored services which, at the operating system level (like
     * SSH), may depend on network.</p>
     *
     * @param name the name of the system service
     * @param checkProc The command for checking if the service is running.
     * @param shutdownOnExit Specifies whether this service should be 
     *                       terminated when honeycomb is shutdown
     */
    public SysMonitoredService(String name, String checkProc,
                                boolean shutdownOnExit) {
        this.name     = name;
        this.checkProc = checkProc;
        this.doShutdownOnExit = shutdownOnExit;

        logger.info("created new MonitoredService: " + name);
    }

    /** Create an empty MonitoredService - useful for extended classes. */
    public SysMonitoredService() {
        this(null, null, false);
    }

    public String getPath() {
        return name;
    }

    public boolean isRunning() {
        boolean isRunning = false;

        int retval = -1;
        try {
            retval = Exec.exec(checkProc);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't exec " + checkProc, e);
        }

        return retval == 0;
    }

    /** 
     * Start a monitored service. Returns if service is already started
     */
    public void start() {
        if (isRunning()) {
            if (logger.isLoggable(Level.INFO))
                logger.info("monitored service already running " + name);
            return;
        }

        int retval;
        try {
            retval = Exec.exec(commands.svcStart(name), logger);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE,
                       "Couldn't exec " + commands.svcStart(name), e);
            return;
        }

        if (retval != 0)
            logger.warning("Couldn't start service \"" + name +
                           "\" (exit status = " + retval + ")");
        else
            if (logger.isLoggable(Level.INFO))
                logger.info(name + " started: " + retval);
    }

    /** 
     * Stop a monitored service
     */
    public void stop() {
        if (!isRunning()) {
            if (logger.isLoggable(Level.INFO))
                logger.info("monitored service is already stopped " + name);
            return;
        }

        int retval;
        try {
            retval = Exec.exec(commands.svcStop(name), logger);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE,
                       "Couldn't exec " + commands.svcStop(name), e);
            return;
        }

        if (retval != 0)
            logger.warning("Couldn't stop service \"" + name +
                           "\" (exit status = " + retval + ")");
        else
            if (logger.isLoggable(Level.INFO))
                logger.info(name + " stopped: " + retval);
    }

    /**
     * Restarts a monitored service 
     */
    public void restart() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info ("restarting monitored service" + name);
            if (!isRunning())
                logger.info("monitored service not running " + name);
        }

        int retval;
        try {
            retval = Exec.exec(commands.svcRestart(name), logger);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE,
                       "Couldn't exec " + commands.svcRestart(name), e);
            return;
        }

        if (logger.isLoggable(Level.INFO))
            logger.info(name + " restarted: " + retval);
    }

    public boolean doShutdownOnExit() {
        return doShutdownOnExit;
    }

    public String toString() {
        return getPath();
    }
}
