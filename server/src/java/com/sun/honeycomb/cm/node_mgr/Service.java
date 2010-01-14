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

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;


import com.sun.honeycomb.cm.jvm_agent.CMAgent;

/**
 * Definition of a service
 */
public class Service implements java.io.Serializable {

    final String  cls;
    final String  tag;
    final boolean isJVM;
    final int     id;
    int     state;
    boolean masterOnly;
    int shutdownTimeout;
    boolean isManaged;
    int     runlevel;
    int     maxRss;
    long incarnationNumber;

    public ManagedService.ProxyObject getProxy(int nodeid) {
        Object obj = ServiceManager.proxyFor(nodeid, getName());
        return (ManagedService.ProxyObject) obj;
    }

    public String getClassName() {
        return cls;
    }

    public String getTag() {
        return tag;
    }

    public int getMaxRss() {
        return maxRss;
    }

    public int getState() {
        return state;
    }

    public int getId() {
        return id;
    }

    public boolean isManaged() {
        return isManaged;
    }

    public boolean isJVM() {
        return isJVM;
    }

    public String getName() {
        String[] parts = tag.split("/");
        if (parts.length > 1) {
            return parts[parts.length - 1];
        }
        return tag;
    }
        
    public boolean isRunning() {
        return state == ManagedService.ProxyObject.RUNNING;
    }

    public boolean isDisabled() {
        return state == ManagedService.ProxyObject.DISABLED;
    }

    public boolean isMasterOnly() {
        return masterOnly;
    }

    public int getRunLevel() {
        return runlevel;
    }

    /** This method serves only testing purposes - see unit_tests.
        Do not setStatus() on actual running services.
     */
    public boolean setStatus(String status) {
        if (status.equals("RUNNING"))
            state = ManagedService.ProxyObject.RUNNING;
        else if (status.equals("INIT"))
            state = ManagedService.ProxyObject.INIT;
        else if (status.equals("READY"))
            state = ManagedService.ProxyObject.READY;
        else if (status.equals("DISABLED"))
            state = ManagedService.ProxyObject.DISABLED;
        else // unknown state => no change
            return false;
        return true;
    }
        
    public String toStatus() {
        String status;
        switch (state) {
        case ManagedService.ProxyObject.RUNNING:
            status = "RUNNING";
            break;
        case ManagedService.ProxyObject.INIT:
            status = "INIT";
            break;
        case ManagedService.ProxyObject.READY:
            status = "READY";
            break;
        case ManagedService.ProxyObject.DISABLED:
            status = "DISABLED";
            break;
        default:
            status = "UNKNOWN";
            break;
        }
        return status;
    }
        
    public String toString() {
        // eg: MyMasterService [RUNNING] [3M]
        // JustAnotherService [INIT] [4]
        return 
            getName() + " [" + toStatus() + "] [" +
            getRunLevel() + (isMasterOnly() ? "M]" : "]" +
            " <" + incarnationNumber + ">");
    }

    public Service(Service svc) {
        tag = svc.tag;
        cls = svc.cls;
        runlevel = svc.runlevel;
        masterOnly = svc.masterOnly;
        shutdownTimeout = svc.shutdownTimeout;
        isJVM = svc.isJVM;
        state = svc.state;
        id = svc.id;
        isManaged = svc.isManaged;
        maxRss = svc.maxRss;
        incarnationNumber = svc.incarnationNumber;
    }

    public Service(String tag, 
                   String cls, 
                   int id,
                   int runlevel, 
                   boolean masterOnly,
                   int shutdownTimeout,
                   boolean isJVM) {
        this.tag = tag;
        this.cls = cls;
        this.runlevel = runlevel;
        this.masterOnly = masterOnly;
        this.shutdownTimeout = shutdownTimeout;
        this.isManaged = false;
        this.isJVM = isJVM;
        this.state = ManagedService.ProxyObject.DISABLED;
        this.id = id;
        this.maxRss = 0;
        this.incarnationNumber = 0;
    }

    public int getShutdownTimeout() {
        return(shutdownTimeout);
    }

    public long getIncarnationNumber() {
        return incarnationNumber;
    }

    void incIncarnationNumber() {
        incarnationNumber++;
    }

    void update(Service svc) {
        runlevel = svc.runlevel;
        masterOnly = svc.masterOnly;
        state = svc.state;
        isManaged = svc.isManaged;
        maxRss = svc.maxRss;
        incarnationNumber = svc.incarnationNumber;
    }
}
