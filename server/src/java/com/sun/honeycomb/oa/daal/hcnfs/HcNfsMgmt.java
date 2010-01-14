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



package com.sun.honeycomb.oa.daal.hcnfs;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;
import java.io.IOException;

import com.sun.honeycomb.oa.daal.DAALMgmt;
import com.sun.honeycomb.oa.OAServerIntf;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.hwprofiles.HardwareProfile;

/**
 * DAAL Management Interface over our client nfs lib
 * Avoid overloading the namei cache of the system by keeping a cache
 * of nfs file handle for all the layout map ids.
 * Theses caches are exchanged int the cluster when disks are opened/closed.
 */
public class HcNfsMgmt implements DAALMgmt {

    private static final Logger LOG = Logger.getLogger(HcNfsMgmt.class.getName());
    private static final String RCP = "/usr/bin/rcp -p";
    private static final String DBDIR = "/nfsdisks";
    
    private final int localNodeId;
    private final long[] dbLastModified;
        
    public HcNfsMgmt() {
        // make sure our "mountpoint" exists.
        File f = new File(DBDIR);
        if (!f.exists()) {
            f.mkdir();
        } else if (!f.isDirectory()) {
            f.delete();
            f.mkdir();
        }
        
        //NOTE: we don't clean up all previous local db
        //since the server can restart upon failure.
        
        // get the node id this service is running on
        NodeMgrService.Proxy proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        assert(proxy != null);
        localNodeId = proxy.nodeId();
        
        // build the mgmt proxy
        HardwareProfile profile = HardwareProfile.getProfile();
        assert(profile != null);
        dbLastModified = new long[profile.getMaxDisks()];
    }
        
    /*
     * Open the given disk.
     */
    public boolean openDisk(DiskId id) throws ManagedServiceException
    {
        if (id.nodeId() == localNodeId) {
            // the disk is on this node -
            // build the layout file handle database for it if needed.
            return buildLayoutFileHandles(id);
            
        } else {
            // this is a foreign disk -
            // forward the request to the corresponding OA server and
            // get a local copy of its layout map file handles.
            OAServerIntf oa = OAServerIntf.Proxy.getAPI(id.nodeId());
            if (oa == null) {
                LOG.warning("OAServer not available on node " + id.nodeId());
                return false;
            }
            if (oa.openDisk(id) == false) {
                LOG.warning("Failed to open disk " + id);
                return false;
            }
            return importLayoutFileHandles(id);
        }
    }        
    
    /*
     * Close the given disk
     */
    public void closeDisk(DiskId id) throws ManagedServiceException
    {
        File db = new File(getDbName(id));
        if (db.exists()) {
            // delete the database from the namespace.
            db.delete();
        }
    }

    /*
     * Close all disks
     */
    public boolean closeAllDisks() throws ManagedServiceException
    {
        File[] fl = new File(DBDIR).listFiles();
        if (fl == null) {
            return true;
        }
        for (int i = 0; i < fl.length; i++) {
            fl[i].delete();
        }
        return true;
    }

    /*
     * Get the mgmt proxy
     * the proxy contains the timestamp of the last modification time
     * for each db of the local disks.
     */
    public Object getProxy()
    {        
        for (int i = 0; i < dbLastModified.length; i++) {
            dbLastModified[i] = 0;
        }
        Disk[] disks = DiskProxy.getLocalDisks();
        if (disks != null) {
            for (int i = 0; i < disks.length; i++) {
                if (disks[i] == null) {
                    continue;
                }
                if (!disks[i].isEnabled()) {
                    continue;
                }
                DiskId id = disks[i].getId();
                assert(id.diskIndex() >= 0 && id.diskIndex() < dbLastModified.length);
                
                File db = new File(getDbName(id));
                if (db.exists()) {
                    dbLastModified[id.diskIndex()] = db.lastModified();
                }
            }
        }
        return dbLastModified;
    }
    
    // STATIC PACKAGE ACCESS
    
    protected static boolean dbStillGood(DiskId id) throws ManagedServiceException
    {
        File db = new File(getDbName(id));
        if (db.exists()) {
            Object obj = OAServerIntf.Proxy.getMgmtProxy(id.nodeId());
            if (!(obj instanceof long[])) {
                throw new ManagedServiceException("failed to get mgmt proxy " + obj);
            }
            long[] dbProxy = (long[]) obj;
            if (id.diskIndex() < 0 || id.diskIndex() >= dbProxy.length) {
                throw new ManagedServiceException("wrong disk index for " + id);
            }
            if (dbProxy[id.diskIndex()] == db.lastModified()) {
                return true;
            }
            LOG.warning("database " + db + " is outdated");
        } else {
            LOG.severe("database " + db + " is missing");            
        }
        return false;
    }        
        
    protected static synchronized void dbRepair(DiskId id) throws ManagedServiceException
    {
        if (dbStillGood(id)) {
            return;
        }        
        OAServerIntf oa = OAServerIntf.Proxy.getLocalAPI();
        if (oa == null) {
            LOG.warning("OAServer not available on local node");
            return;
        }
        if (oa.openDisk(id) == false) {
            LOG.warning("Failed to open disk " + id);
            return;
        }
        LOG.info("db " + getDbName(id) + " recovered");
    }
    
    // PRIVATE
    
    /*
     * return db cache filename corresponding to this disk id.
     */
    private static String getDbName(DiskId id) 
    {
        return DBDIR + "/" + id.nodeId() + "_" + id.diskIndex() + ".db";
    }
    
    
    /*
     * build the layout map id nfs file handle cache if it does not exists.
     * Note: synchronized guarantees that the cache is consistent across the 
     * nodes in the cluster.
     */
    private synchronized boolean buildLayoutFileHandles(DiskId id)
    {
        File db = new File(getDbName(id));
        if (db.exists()) {
            return true;
        }
        
        assert(id.diskIndex() >= 0 && id.diskIndex() < dbLastModified.length);
        
        if (_buildDB(id.nodeId() - NfsAccess.NODE_BASE_ID, id.diskIndex()) != 0) {
            db.delete();
            dbLastModified[id.diskIndex()] = 0;
            return false;
        }
        dbLastModified[id.diskIndex()] = db.lastModified();
        return true;
    }
    
    /*
     * Remote copy the layout cache db for this remote disk
     */
    private boolean importLayoutFileHandles(DiskId id)
    {
        Node[] allNodes = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).getNodes();
        assert (allNodes != null);
        
        String nodeAddr = null;
        for (int i = 0; i < allNodes.length; i++) {
            if (allNodes[i].nodeId() == id.nodeId()) {
                nodeAddr = allNodes[i].getAddress();
                break;
            }
        }
        if (nodeAddr == null) {
            throw new RuntimeException("failed to get node addr for disk " + id);
        }
        
        StringBuffer cmd = new StringBuffer(RCP);
        cmd.append(" " + nodeAddr + ":" + getDbName(id));
        cmd.append(" " + DBDIR + "/");

        boolean succeed = false;
        try {
            File db = new File(getDbName(id));
            if (db.exists()) {
                db.delete();
            }
            if (Exec.exec(cmd.toString(), LOG) != 0) {
                LOG.severe("failed to import handles for " + id + 
                           " cmd " + cmd.toString());
            } else {
                succeed = true;
            }
        } catch (IOException ioe) {
            LOG.severe("exception importing handles for " + id + " " + ioe);
        }
        return succeed;
    }
    
    // NFS CLIENT NATIVE LIB

    private native int _buildDB(int nodeid, int diskid);
    
    static {
        System.loadLibrary("hcnfs");
    }    
}
