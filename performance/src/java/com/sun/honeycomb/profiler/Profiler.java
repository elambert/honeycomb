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



package com.sun.honeycomb.profiler;

import java.util.logging.Logger;
import java.util.*;
import java.io.*;

import com.sun.honeycomb.admin.ProfilerIntf;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.SolarisRuntime;
import com.sun.honeycomb.util.sysdep.DiskOps;


/**
 * This is the implementation of the profiler service
 */
public class Profiler implements ProfilerIntf {

    private static final Logger logger = 
        Logger.getLogger(Profiler.class.getName());
    
    static final String GZIP = "/usr/bin/gzip";
    static final String PKILL = "/usr/bin/pkill";
    
    static final int DELAY = (20 * 1000); // 20s
    static final int SAMPLING_PERIOD = 5; // 5s
 
    private String resultDataFile;
    private String curModule;
    private boolean profileInProgress;
    boolean isRunning;
    private Thread svcThr;
    private SolarisRuntime sr;  

    // default constructor called by CM
    public Profiler() 
    {
        logger.info("Profiler init");
        isRunning = true;
        profileInProgress = false;
        
        // Runtime object for future execs
        cleanup();
        sr = new SolarisRuntime();
    }

    public void shutdown() 
    {
        logger.info("Profiler shutdowns");
        isRunning = false;
        synchronized (this) {
            if (svcThr != null) {
                while (svcThr.isAlive()) {
                    svcThr.interrupt();
                    try {
                        wait(DELAY);
                    } catch (InterruptedException ignore) {}
                }
            }
        }
    }
    
    public void syncRun() {
    }
    
    // return the current proxy for this service
    public ProxyObject getProxy() 
    {
        return new ProfilerIntf.Proxy(curModule);
    }

    // service entry point
    public void run() 
    {        
        logger.info("Profiler runs");
        svcThr = Thread.currentThread();
 
        while (isRunning) {
            try {
                Thread.sleep(DELAY);
            } catch (InterruptedException e) {
            }
        }
        cleanup();
        synchronized (this) {
            notifyAll();
        }        
    }

    /*
     * remote API exported by the managed service
     */

    public void startProfiling(String module, int howlong) 
        throws ManagedServiceException
    {
        if (profileInProgress) {
            throw new ManagedServiceException("profiling is running, stop it first");
        }
        logger.info("start profiling");
        cleanup();
      
        // Calculate the no. of profiling samples, sampling period in secs
        int count = 0; 
        if (howlong > 0) {
            count = (int) (howlong * 60) / SAMPLING_PERIOD; 
        }
        
        // Get Disks for local node and return the first online disk 
        Disk[] nodeDisks = DiskProxy.getLocalDisks(); 
        if (nodeDisks == null) {
            throw new ManagedServiceException("null disks online");
        }
        String profileDir = null;
        for (int d=0; d < nodeDisks.length; d++) {
           Disk disk = nodeDisks[d];
           if (disk.isEnabled()) {
               // FIXME - this is broken with new DAAL implementation
               //profileDir = Common.getNFSMountPoint(disk);
               profileDir += ProfilerIntf.PROFILE_RESULT_DIR;
               break;
           }
        }
        if (profileDir == null) {
            throw new ManagedServiceException("no disk available");
        }
       
        // Create Profile Directory, if needed 
        File f = new File(profileDir);
        if (!f.exists()) {
           f.mkdir(); 
        }
 
        // remember where we put the results
        resultDataFile = profileDir + ProfilerIntf.MODULE_PREFIX + module +".dat";
        
        // Start Dtrace Capture
        // Assume that dtrace script name = module_ABC.sh where ABC = module
        // and redirects output to file module_ABC.dat
        String dtraceCmd;   
        if (count > 0) { 
            dtraceCmd = ProfilerIntf.PROFILER_DTRACE_PATH 
                + ProfilerIntf.MODULE_PREFIX + module + ".sh " 
                + resultDataFile + " 5 " + count;
        } else {
            dtraceCmd = ProfilerIntf.PROFILER_DTRACE_PATH 
                + ProfilerIntf.MODULE_PREFIX + module +".sh "
                + resultDataFile;
        }
        
        try {
            sr = Exec.execBg(dtraceCmd, logger);
            if (sr == null) {
                throw new ManagedServiceException(dtraceCmd + " failed");
            }
            curModule = module;
            profileInProgress = true;
            
        } catch (IOException ioe) {
            logger.severe("Unable to execute dtrace script " +ioe.getMessage());
            throw new ManagedServiceException(dtraceCmd + " : " + ioe.getMessage());
        } 
    }

    public String stopProfiling() throws ManagedServiceException 
    {
        if (profileInProgress) {
            // Kill dtrace profiling script
            logger.info("Stopping dtrace profiling script"); 
            sr.destroy();
            try {
                String gzipCmd = GZIP + " " + resultDataFile;
                Exec.exec (gzipCmd, logger); 
            } catch (IOException ioe) {
                logger.severe("Unable to gzip raw data files " +ioe.getMessage());
            }
            profileInProgress = false;
        } else {
            logger.info("Stop - profiler is not running");
        }
        if (resultDataFile != null) {
            File gzipFile = new File(resultDataFile + ".gz");
            if (gzipFile.exists()) {
                return gzipFile.getPath();
            }
        }
        return null;
    }
    
    private void cleanup() 
    {
        try {
            String pkillCmd = PKILL + " -9 -f " + ".*dtrace.*/" + ProfilerIntf.MODULE_PREFIX;
            logger.info(pkillCmd);
            Exec.exec(pkillCmd);
        } catch (IOException ioe) {
            logger.warning("fail to destroy dtrace module " + ioe.getMessage());
        }
        Disk[] nodeDisks = DiskProxy.getLocalDisks(); 
        if (nodeDisks != null) {
            String target = null;
            for (int d=0; d < nodeDisks.length; d++) {
                Disk disk = nodeDisks[d];
                if (disk.isEnabled()) {
                    target = disk.getPath();
                    target += ProfilerIntf.PROFILE_RESULT_DIR;
                    DiskOps.getDiskOps().rmdirP(target);
                    break;
                }
            }
        }
    }
}
