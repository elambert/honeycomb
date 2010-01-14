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



package com.sun.honeycomb.oa;

import java.io.File;
import java.io.FileFilter;
import java.util.logging.Logger;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.oa.daal.DAALMgmt;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.NewObjectIdentifier;

// Default mgmt implementation if the daal property is not set.
import com.sun.honeycomb.oa.daal.nfs.NfsMgmt;


/**
 * This is the implementation of the OA server.
 */
public class OAServer implements OAServerIntf {

    private static final Logger LOG = Logger.getLogger(OAServer.class.getName());
    private static final int SLEEP_DELAY = 5000; // 5s
    private static final int POLL_DELAY  = (1 * 1000); // 1s

    private volatile boolean terminate;
    private Thread svcThr;
    private final DAALMgmt daalMgmt;

    // default constructor called by CM
    public OAServer() {

        // get DAAL management implementaton
        ClusterProperties config = ClusterProperties.getInstance();
        String className = config.getProperty(DAALMgmt.DAAL_MGMT_PROPERTY);
        try {
            if (className == null) {
                LOG.severe("Cannot find property " + DAALMgmt.DAAL_MGMT_PROPERTY +
                           " - using NFS protocol");
                daalMgmt = new NfsMgmt();
            } else {
                Class cl = Class.forName(className);
                daalMgmt = (DAALMgmt) cl.newInstance();
            }
        } catch (ClassNotFoundException ce) {
            LOG.severe("FATAL failed to find DAAL class " + className);
            throw new RuntimeException(ce);

        } catch (InstantiationException ie) {
            LOG.severe("FATAL failed to initialize DAAL class " + className);
            throw new RuntimeException(ie);

        } catch (IllegalAccessException ae) {
            LOG.severe("FATAL cannot access DAAL class " + className);
            throw new RuntimeException(ae);
        }

        terminate = false;
        svcThr = null;
        LOG.info("The OAServer has been instanciated");
    }

    // CM - shutdown this service.
    public void shutdown() {
        LOG.info("The OAServer is being told to shut down.");
        terminate = true;
        synchronized (this) {
            if (svcThr != null) {
                while (svcThr.isAlive()) {
                    svcThr.interrupt();
                    try {
                        wait(POLL_DELAY);
                    } catch (InterruptedException ignore) {}
                }
            }
        }
    }

    // CM - return the current proxy for this service
    public ManagedService.ProxyObject getProxy() {
        return new OAServerIntf.Proxy(daalMgmt);
    }

    // CM - sync run
    public void syncRun() {
    }

    // service entry point
    public void run() {

        svcThr = Thread.currentThread();
        while (!terminate) {
            try {
                Thread.sleep(SLEEP_DELAY);
            } catch (InterruptedException e) {
                LOG.info("OAServer interrupted");
            }
        }
    }

    //
    // remote API exported by the managed service
    //

    // RMI - open the given disk
    public boolean openDisk(DiskId id)
    {
        boolean succeed = false;
        try {
            succeed = daalMgmt.openDisk(id);
        } catch (ManagedServiceException me) {
            LOG.warning("failed to open disk " + id + " " + me);
        }
        return succeed;
    }


    // RMI - close the given disk
    public void closeDisk(DiskId id)
    {
        try {
            daalMgmt.closeDisk(id);
        } catch (ManagedServiceException me) {
            LOG.warning("failed to close disk " + id + " " + me);
        }
    }

    // RMI - close all opened disks on this node.
    public boolean closeAllDisks()
    {
        boolean succeed = false;
        try {
            succeed =  daalMgmt.closeAllDisks();
        } catch (ManagedServiceException me) {
            LOG.warning("failed to close all disks " + me);
        }
        return succeed;
    }

    // RMI - return the fragment numbers found for the given OID on the
    // given disk.
    public int[] listFragNumbers(NewObjectIdentifier oid, DiskId diskId)
        throws ManagedServiceException
    {
        Disk disk = DiskProxy.getDisk(diskId);
        if (disk == null) {
            throw new IllegalArgumentException("invalid disk " + diskId);
        }

        NodeMgrService.Proxy proxy = null;
        proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (diskId.nodeId() != proxy.nodeId()) {
            throw new IllegalArgumentException("disk " + diskId + " not local");
        }

        FragNumberFilter fragsFilter = new FragNumberFilter(oid);
        File[] ls = new File(Common.makeDir(oid, disk)).listFiles(fragsFilter);
        if (ls == null) {
            return null;
        }
        int[] res = new int[ls.length];
        for (int i = 0; i < ls.length; i++) {
            res[i] = Common.extractFragNumFromFilename(ls[i].getName());
            assert(res[i] > 0);
        }
        return res;
    }

    //
    // PRIVATE
    //

    // filter fragments that match the oid and have a valid frag number
    private static class FragNumberFilter implements FileFilter {
        String oidStr;

        public FragNumberFilter(NewObjectIdentifier oid) {
            oidStr = oid.toString();
        }
        public boolean accept(File f) {
            return (f.isFile() &&
                    !f.isHidden() &&
                    f.getName().startsWith(oidStr) &&
                    Common.extractFragNumFromFilename(f.getName()) >= 0);
        }
    }
}
