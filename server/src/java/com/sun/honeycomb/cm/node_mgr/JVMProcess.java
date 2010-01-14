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



package com.sun.honeycomb.cm.node_mgr;

import java.lang.Process;
import java.lang.Runtime;
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.util.Comparator;
import java.lang.Comparable;
import java.io.OutputStream;
import java.io.InputStream;
import java.lang.IllegalThreadStateException;
import java.util.logging.Logger;



/**
 *  A JVMProcess manages several groups of services
 */
class JVMProcess implements Comparable {

    private static final Logger logger = 
        Logger.getLogger(NodeMgr.class.getName());

    static final int DELAY_SHUTDOWN = 5000; // 5s
    static final int POLL_DELAY= 500; // 500ms

    private Process process = null;
    private ServiceMailbox agent = null;
    private boolean masterOnly = false;
    private List services = new Vector();
    private String cmd;
    private String[] envp;
    private int exitCode;
    private final int maxRss;
    private final int jvmId;
    private final String tag;
    private final String cls;

    
    JVMProcess(int id, String name, String agent, int rss) {
        services = new Vector();
        jvmId = id;
        tag = name;
        cls = agent;
        cmd = null;
        envp = null;
        exitCode = 0;
        maxRss = rss;
    }

    JVMProcess(int id,
               String name, 
               String cmd0,
               String params,
               String agent,
               String libdir,
               String logconf,
               String classpath,
               String[] nEnvp,
               int    rss) {
        jvmId = id;
        tag = name;
        cls = agent;
        exitCode = 0;
        maxRss = rss;
        cmd = cmd0;

        // name looks like "/000/JVMName where 000 is the node id.
        String vmname = name.substring (name.lastIndexOf ("/") +1);
        cmd += " -D" + vmname;
        cmd += " -Dhc.mailbox=" + tag;
        cmd += " -classpath " + classpath;
        cmd += " -Djava.library.path=" + libdir;
        cmd += " -Djava.util.logging.config.file=" + logconf + " " + params + " " + agent;
        envp = nEnvp;
    }

    int getId() {
        return jvmId;
    }
    
    int getMaxRss() {
        return maxRss;
    }

    void attach(ServiceMailbox svc) {
        services.add(svc);
    }

    List getServices() {
        return services;
    }

    boolean masterOnly() {
        return masterOnly;
    }

    boolean safeToShutdown() {
        for (int i = 0; i < services.size(); i++) {
            ServiceMailbox svc = (ServiceMailbox) services.get(i);
            if (svc.isManaged()) {
                return false;
            }
        }
        return true;
    }            
    
    synchronized void init() throws IOException {
        for (int i = 0; i < services.size(); i++) {
            ServiceMailbox svc = (ServiceMailbox) services.get(i);
            if (svc.masterOnly()) {
                masterOnly = true;
                break;
            }
        }
        int level = (cmd == null)? 0: 1;
        agent = new ServiceMailbox(jvmId, cls, tag, level, masterOnly, true);
        services.add(agent);
    }

    synchronized void start() throws IOException {
        boolean exec = false;

        for (int i = 0; i < services.size(); i++) {
            ServiceMailbox svc = (ServiceMailbox) services.get(i);
            if (svc.isManaged()) {
                exec = true;
                break;
            }
        }
        if (exec) {
            if (process == null && cmd != null) {
                for (int i = 0; i < services.size(); i++) {
                    ServiceMailbox svc = (ServiceMailbox) services.get(i);
                    svc.reset();
                }
                
                logger.info("ClusterMgmt - starting JVM " + this +
							"["+cmd+"]");
				
                process = Runtime.getRuntime().exec(cmd, envp);

            } else if (cmd == null) {
                for (int i = 0; i < services.size(); i++) {
                    ServiceMailbox svc = (ServiceMailbox) services.get(i);
                    if (svc.isDisable()) {
                        svc.reset();
                    }
                }
            }
        }
    }

    synchronized void shutdown() throws IOException {
        boolean kill = true;
        for (int i = 0; i < services.size(); i++) {
            ServiceMailbox svc = (ServiceMailbox) services.get(i);
            if (svc.getLevel() != 0) {
                svc.disable();
            } else {
                kill = false;
            }
        }
        if (kill && process != null) {
            process.destroy();
            Timeout timeout = new Timeout(DELAY_SHUTDOWN);
            logger.info("ClusterMgmt - destroy process " + this);
            while (true) {
                Thread.currentThread().yield();
                try {
                    exitCode = process.exitValue();
                    break;
                } catch (IllegalThreadStateException e) {}
                if (timeout.hasExpired()) {
                    logger.severe("ClusterMgmt - cannot destroy " + this);
                    break;
                }
                try {
                    logger.warning("ClusterMgmt - waiting for " 
                                   + this + " to die");
                    Thread.currentThread().sleep(POLL_DELAY);
                } catch (InterruptedException e) {
                    logger.severe("ClusterMgmt - interrupted");
                }
            }
            process = null;
        }         
    }

    synchronized void restart() {
        try {
            shutdown();
            start();
        } catch (Exception e) {
            process = null;
        }
    }

    synchronized boolean healthCheck() {
        if (cmd == null) {
            return true;
        }
        if (process == null) {
            return false;
        }
        try {
            exitCode = process.exitValue();     
        } catch (IllegalThreadStateException e) {
        	// No exit code if jvm has not terminated.
            return true;
        }
        
        logger.info("Monitored JVM has exited with return code: " + exitCode);
        try {
            shutdown();
        } catch (Exception e) {
            logger.severe("Failed to shutdown JVM properly " + e);
            process.destroy();
            process = null;
        }
        return false;
    }

    boolean isStarted() {
        return (cmd == null || process != null);
    }

    public int compareTo(Object obj) {
        return jvmId  - ((JVMProcess) obj).jvmId;
    }

    public String toString() {
        String[] parts = tag.split("/");
        if (parts.length > 1) {
            return parts[parts.length -1];
        }
        return tag;
    }

    public void flushOutput() throws IOException {
        InputStream stream;
        if (process == null) {
            stream = System.in;
        } else {
            stream = process.getInputStream();
        }
        int count;
        while ((count = stream.available()) > 0) {
            byte[] buf = new byte[count];
            stream.read(buf);
            logger.info(new String(buf));
        }
        if (process != null) {
            stream = process.getErrorStream();
            while ((count = stream.available()) > 0) {
                byte[] buf = new byte[count];
                stream.read(buf);
                logger.info(new String(buf));
            }
        }
    }
}
