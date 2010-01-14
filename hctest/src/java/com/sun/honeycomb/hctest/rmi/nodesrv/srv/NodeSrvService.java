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



package com.sun.honeycomb.hctest.rmi.nodesrv.srv;

import com.sun.honeycomb.hctest.rmi.nodesrv.common.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.err_inj.ErrInjManagedService;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.layout.LayoutClient;

//
// These needs to be ported to the new CLI code structure.
//
//import com.sun.honeycomb.admin.ClusterManager;
//import com.sun.honeycomb.admin.DiskFRU;
//import com.sun.honeycomb.admin.MoboFRU;
//import com.sun.honeycomb.admin.FRU;
//import com.sun.honeycomb.admin.UnknownFRUException;
//

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.io.*;
import java.util.*;

import java.util.logging.Logger;

public class NodeSrvService extends UnicastRemoteObject implements NodeSrvIF {

    private final String MOUNT_POINT = "/netdisks/"; // where all drives mntd

    // use to read properties XXX get from dev stack
    private static final String PREFIX = "honeycomb.layout.";
    private static final String DATA_FRAGS = PREFIX + "datafrags";
    private static final String PARITY_FRAGS = PREFIX + "parityfrags";
    private static final String DISKS_PER_NODE=PREFIX + "diskspernode";

    private RunCommand shell = new RunCommand();

    // config info
    private HClusterConfig hconfig = new HClusterConfig();
    private ArrayList diskPaths = new ArrayList();

    private OAClient oaClient = OAClient.getInstance();

    private static final Logger LOG =
                            Logger.getLogger(NodeSrvService.class.getName());

    public NodeSrvService() throws RemoteException {

        super();
        LOG.info("NodeSrvService is initializing");

        getClusterConfig();
        getKnownDisks();

        LOG.info("NodeSrvService initialized");
    }

    /** 
     *  Read cluster config from Cell Manager.
     */
    private void getClusterConfig() {

        LOG.info("getClusterConfig() - XXX needs update");

        // Get the IPs of all nodes running in the cluster
        try {
            hconfig.activeNodeIPs = getActiveNodes();
        } catch (Exception e) {
            LOG.severe("Can't get active nodes: " + e);
            System.exit(1);
        }

        if (hconfig.activeNodeIPs == null  ||  hconfig.activeNodeIPs.length==0){
            LOG.warning("no active nodes");
        } else {
            for (int i=0; i<hconfig.activeNodeIPs.length; i++) {
                LOG.info("IP " + hconfig.activeNodeIPs[i]);
            }
        }

        // get the cluster config, read from cluster_config.properties
        // per honeycomb/layout//LayoutConfig.java
        ClusterProperties config = ClusterProperties.getInstance();

        if (config == null) {
            LOG.warning("Unable to get cluster config");
            return;
        }

        // set nDataFrags
        if (config.isDefined (DATA_FRAGS))
            hconfig.nDataFrags = config.getPropertyAsInt(DATA_FRAGS);
        else
            LOG.warning(DATA_FRAGS + " not set");

        // set nParityFrags
        if (config.isDefined(PARITY_FRAGS))
            hconfig.nParityFrags = config.getPropertyAsInt(PARITY_FRAGS);
        else
            LOG.warning(PARITY_FRAGS +" not set");

        // get number of disks per node
        if (config.isDefined(DISKS_PER_NODE))
            hconfig.disksPerNode = config.getPropertyAsInt(DISKS_PER_NODE);
        else
            LOG.warning(DISKS_PER_NODE+" not set");

        LOG.info("disksPerNode=" + hconfig.disksPerNode + 
                 " nDataFrags=" + hconfig.nDataFrags +
                 " nParityFrags=" + hconfig.nParityFrags);
    }

    //
    //  Get the disks on all of the known active nodes
    //
    private void getKnownDisks() {
        diskPaths.clear();
        for (int i=0; i<hconfig.activeNodeIPs.length; i++)  {
            String diskPath = MOUNT_POINT + hconfig.activeNodeIPs[i] + "/data";
            for (int j=0; j<hconfig.disksPerNode; j++)
                diskPaths.add(new File(diskPath + "/" + j));
        }
    }

    // Get the uid from the oid
    private String getUid(String oid) {
        return oid.substring(0, oid.indexOf('.'));
    }

    private NodeMgrService.Proxy getNodeMgrProxy() throws RemoteException {
        NodeMgrService.Proxy proxy = ServiceManager.proxyFor(
                                                ServiceManager.LOCAL_NODE);
        if (proxy == null) {
            throw new RemoteException("Can't get proxy");
        }
        return proxy;
    }

    ////////////////////////////////////////////////////////////////////////
    //  remote interface
    //
    public String uptime() throws RemoteException {
        try {
            return shell.uptime();
        } catch (Exception e) {
            throw new RemoteException("shell", e);
        }
    }

    public void shutdown() throws RemoteException {
        LOG.info("shutdown rcvd");
        System.exit(0);
    }

    public void logMsg(String msg) throws RemoteException {
        LOG.info("REMOTE: " + msg);
    }

    public String[] getNodes() throws RemoteException {
        LOG.info("getNodes");
        NodeMgrService.Proxy proxy = getNodeMgrProxy();
        Node[] nodes = proxy.getNodes();
        String[] ret = new String[nodes.length];
        for (int i=0; i<ret.length; i++)
            ret[i] = nodes[i].toString();
                                                                                
        return ret;
    }

    public String[] getActiveNodes() throws RemoteException {
        LOG.info("getActiveNodes");

        NodeMgrService.Proxy proxy = getNodeMgrProxy();
        Node[] nodes = proxy.getNodes();
        if (nodes == null) {
            throw new RemoteException("Can't get nodes from proxy");
        }
        ArrayList list = new ArrayList();
        for (int i=0; i<nodes.length; i++) {
            if (nodes[i].isAlive())
                list.add(nodes[i].getName());
        }
        String[] ret = new String[list.size()];
        for (int i=0; i<ret.length; i++) {
            ret[i] = (String) list.get(i);
        }                                                                       
        return ret;
    }

    public HClusterConfig getConfig() throws RemoteException {
        return hconfig;
    }

    private String dotForm(String oid) {
        if (oid.indexOf('.') != -1)
            return oid;
        NewObjectIdentifier noid = NewObjectIdentifier.fromHexString(oid);
        return noid.toString();
    }

    private void setObjectType(NewObjectIdentifier noid, HOidInfo info) {
        switch (noid.getObjectType()) {
            case NewObjectIdentifier.METADATA_TYPE:
                info.type = HOidInfo.METADATA_TYPE;
                break;
            case NewObjectIdentifier.DATA_TYPE:
                info.type = HOidInfo.DATA_TYPE;
                break;
            case NewObjectIdentifier.NULL_TYPE:
                info.type = HOidInfo.NULL_TYPE;
                break;
            default:
                info.type = HOidInfo.UNDEFINED;
                break;
        }
    }

    /**
     * Get info about the oid.  If thisOnly is true, we don't dereference an MD
     * object to get its data oid.  This is needed because this doesn't work for
     * deleted fragments.
     */
    public HOidInfo getOidInfo(String oid, boolean thisOnly)
                                                       throws RemoteException {

        // Make sure we have an oid
        if (oid == null) {
            throw new RemoteException("null oid");
        }

        //
        //  convert oid to 'dot' form if needed
        //
        boolean hex = (oid.indexOf('.') == -1);
        NewObjectIdentifier noid;
        if (hex) {
            noid = NewObjectIdentifier.fromHexString(oid);
            String s = oid;
            oid = noid.toString();
            LOG.info("oid=" + s + " / " + oid);
        } else {
            noid = new NewObjectIdentifier(oid);
            LOG.info("oid=" + oid);
        }

        HOidInfo info = new HOidInfo();

        info.oid = noid.toHexString();
        info.int_oid = noid.toString();
        setObjectType(noid, info);
        info.chunk = noid.getChunkNumber();

        boolean isMetadata = false;

        if (info.type == HOidInfo.METADATA_TYPE  &&  !thisOnly) {

            isMetadata = true;

            //
            //  get data oid
            //
            NewObjectIdentifier noid2 = getDataNOid(info.oid);

            info.other = new HOidInfo();

            info.other.oid = noid2.toHexString();
            info.other.int_oid = noid2.toString();
            setObjectType(noid2, info.other);
            info.other.chunk = noid2.getChunkNumber();
            getChunks(noid2, info.other, false);
        }

        getChunks(noid, info, isMetadata);

        // System.out.println("done:\n" + info.toString());

        return info;
    }

    /* 
     * Hack to call a private, non-static constructor from static context 
     * 10/24/06 - FIXME - This hack does not work anymore.
     */
    public static class FragmentFileSubclass extends FragmentFile {
        public FragmentFileSubclass(File f) {
            super();
            // namef = f;
        }

        public FragmentFooter getFragmentFooter() {
            return (fragmentFooter);
        }
    }

    public String getRefCount(File frag) {
        FragmentFileSubclass ffs = null;
        SystemMetadata sm = null;
        String refcnt = null;
        try {
            ffs = new FragmentFileSubclass(frag);
            sm = ffs.readSystemMetadata();
            FragmentFooter footer = ffs.getFragmentFooter();
            if (footer != null) {
                refcnt = "[" + footer.refCount +"/"+ footer.maxRefCount + "]";
            } else {
                refcnt = "[footer null]"; 
            }
        } catch (com.sun.honeycomb.oa.DeletedFragmentException dfe) {
            refcnt = "[deleted]";
            // don't care; fragment should be populated anyhow
            sm = ffs.getSystemMetadata();
            FragmentFooter footer = ffs.getFragmentFooter();
            if (footer != null) {
                refcnt += "[" + footer.refCount +"/"+ footer.maxRefCount + "]";
            }
        } catch (Throwable t) {
            refcnt = "[unknown]";
        }
        return (refcnt);
    }

    private void getChunks(NewObjectIdentifier noid, HOidInfo info, 
                                                        boolean isMetadata) 
                                                       throws RemoteException {

//if (info != null) {
//System.out.println("skipping chunks");
//return;
//}
        if (hconfig.activeNodeIPs.length == 0) {
            // XXX add field to record how many nodes were gone
            LOG.warning("No active IPs, skipping chunks");
            return;
        }

        int chunk = info.chunk;
        if (chunk != 0)
            LOG.warning("Getting chunks >= " + chunk);

        //
        //  get chunks
        //
        info.chunks = new ArrayList();
        HChunkInfo ci = getChunk(noid, chunk, isMetadata);
        int mapId = noid.getLayoutMapId();
        while (ci != null) {

            info.chunks.add(ci);

            if (ci.extraFrags)
                info.extraFrags = true;
            if (ci.missingFrags)
                info.missingFrags = true;

            // define next chunk
            chunk++;
            noid.setChunkNumber(chunk);
            mapId = LayoutClient.getConsecutiveLayoutMapId(mapId);
            noid.setLayoutMapId(mapId);
            ci = getChunk(noid, chunk, isMetadata);
        }
    }

    //
    //  return null if no frags
    //
    private HChunkInfo getChunk(NewObjectIdentifier noid, int chunk, boolean md)
                                                        throws RemoteException {

        //
        //  Get the uid from the oid and figure out the hashed path
        //
        String oid = noid.toString();
        int mapId = noid.getLayoutMapId();

        String layoutMapId = Integer.toString(mapId);
        while (layoutMapId.length() < 4) {
            layoutMapId = '0' + layoutMapId;
        }
        String dirA = layoutMapId.substring(0, 2);
        String dirB = layoutMapId.substring(2, 4);
        String hashPath = "/" + dirA + "/" + dirB + "/" + oid + "_";

        HChunkInfo ci = new HChunkInfo(chunk, mapId, hashPath);

        //
        //  Look for the fragments on all of the disks
        //
        int tot_frags = 0;
        int dup_frags = 0;
        int n_frags = hconfig.nDataFrags + hconfig.nParityFrags;
        ArrayList missing = new ArrayList();
        for (int fg=0; fg<n_frags; fg++) {

            HFragmentInfo fragInfo = new HFragmentInfo(fg);

            boolean found = false;
            String path = hashPath + fg;
            for (int i=0; i<diskPaths.size(); i++) {

                String disk = diskPaths.get(i).toString();
                String fragment = disk + path;
                File file = new File(fragment);

                if (file.exists()) {
                    fragInfo.addDisk(disk, file.length(), getRefCount(file));
                    tot_frags++;
                    if (found) {
                        dup_frags++;
                        LOG.warning("Found DUPLICATE fragment (chunk " + 
                                                chunk + "): " + fg + ": " +
                                                fragment);
                    } else {
                        LOG.fine("Found fragment " + fg + ": " + fragment);
                        found = true;
                    }
                } else {
                    LOG.fine("(no fragment " + fg +
                                 " (chunk " + chunk + 
                                 ") at path " + fragment + ")");
                }
            }
            ci.addFrag(fragInfo);
            if (found) {
                tot_frags++;
            } else {
                missing.add(new String("Failed to find fragment " + fg + 
                            " (" + path + " chunk " + chunk + ") md=" + md));
            }
        }
        if (tot_frags == 0)
            return null;
        if (missing.size() > 0) {
            LOG.info("MISSING: " + missing.toString());
            ci.missingFrags = true;
        }
        if (dup_frags > 0)
            ci.extraFrags = true;

        return ci;

    }

    public String getDataOID(String mdOid) throws RemoteException {
        NewObjectIdentifier noid = getDataNOid(mdOid);
        return noid.toHexString();
    }
    private NewObjectIdentifier getDataNOid(String mdOid) 
                                                      throws RemoteException {
        Context tmpctx = null;
        try {
            NewObjectIdentifier noid = NewObjectIdentifier.fromHexString(mdOid);
            if (noid.getObjectType() == NewObjectIdentifier.DATA_TYPE) {
                throw new RemoteException("OID is already data type");
            }
            tmpctx = new Context();
            SystemMetadata systemMetadata = oaClient.open(noid, tmpctx);
            noid = (NewObjectIdentifier)
                    systemMetadata.get(SystemMetadata.FIELD_LINK);
            return noid;
        } catch (Throwable t) {
            throw new RemoteException("getDataNOid()", t);
        } finally {
            if (tmpctx != null)
                tmpctx.dispose();
        }
    }

    /**
     *  Delete the given fragments (rename them actually).
     */
    public void deleteFragments(List fragments) throws RemoteException {
        LOG.info("(delete " + fragments.size() + " frags)");
        for (int i=0; i<fragments.size(); i++) {
            String oldFileName = fragments.get(i).toString();
            String newFileName = oldFileName + ".del";
            File oldFile = new File(oldFileName);       
            File newFile = new File(newFileName);
            if (!oldFile.renameTo(newFile)) {
                throw new RemoteException("Failed to delete " +
                                                 "fragment: " + i + " " +
                                                 oldFileName);
            } else {
                LOG.info("Renamed file [" + i + "] " + oldFileName +
                    " to " + newFileName);
            }
        }

        // Wait for the filesystem change to sync.
        // We do this in a separate loop so that we can wait all
        // at once, instead of waiting after each delete
        for (int i=0; i<fragments.size(); i++) {
            String oldFileName = fragments.get(i).toString();
            String newFileName = oldFileName + ".del";
            if (!waitForFileToBeGone(oldFileName) ||
                !waitForFileToAppear(newFileName)) {
                throw new RemoteException("Verify failed in delete " +
                                                 "fragment: " + i + " " +
                                                 oldFileName);
            } else {
                LOG.fine("Verified rename file [" + i + "] " + oldFileName +
                    " to " + newFileName);
            }
        }
    }

    /**
     *  Restore moved fragments, given a list of originals.
     */
    public void restoreFragments(List fragments) throws RemoteException {
        LOG.info("restore " + fragments.size());
        for (int i=0; i<fragments.size(); i++) {
            String newFileName = fragments.get(i).toString();
            String oldFileName = newFileName + ".del";
            File newFile = new File(newFileName);
            File oldFile = new File(oldFileName);
            if (!oldFile.renameTo(newFile)) {
                throw new RemoteException("Failed to restore " +
                                                 "fragment " + i + ": " +
                                                 newFileName);
            } else {
                LOG.fine("Restored file [" + i + "] " + oldFileName +
                    " to " + newFileName);
            }
        }

        // Wait for the filesystem change to sync.
        // We do this in a separate loop so that we can wait all
        // at once, instead of waiting after each restore
        for (int i=0; i<fragments.size(); i++) {
            String newFileName = fragments.get(i).toString();
            String oldFileName = newFileName + ".del";
            if (!waitForFileToBeGone(oldFileName) ||
                !waitForFileToAppear(newFileName)) {
                throw new RemoteException("Verify failed in restore " +
                                                 "fragment: " + i + " " +
                                                 oldFileName);
            } else {
                LOG.fine("Verified rename file [" + i + "] " + oldFileName +
                    " to " + newFileName);
            }
        }
    }

    public void waitForFragments(List fragments, boolean shouldExist)
                                                        throws RemoteException {
        for (int i = 0; i < fragments.size(); i++) {
            if (!waitForFile((String)fragments.get(i), shouldExist)) {
                throw new RemoteException("File " + fragments.get(i) +
                    " has expected shouldExist status of " + shouldExist +
                    " but that wasn't achieved.");
            }
            LOG.info("File " + fragments.get(i) +
                " had expected shouldExist status of " + shouldExist);
        }
    }

/*
    public void corruptFragments(List fragments, int[] list, int nbytes, 
                                                                    byte mask)
                                                        throws RemoteException {
        if (nbytes < 1)
            throw new RemoteException("corruptFragments nbytes must be >0");

        LOG.info("corruptFragments()");
        try {
            byte[] b = new byte[1];
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < list.length; i++) {
                File f = (File)fragments.get(list[i]);
                long size = f.length();
                if (size < 1) {
                    LOG.info("FRAG " + f + " size=" + size);
                    continue;
                }
                long mod = size / nbytes - 1;
                RandomAccessFile raf = new RandomAccessFile(f, "rw");
                sb.setLength(0);
                sb.append("[");
                for (int j=0; j<nbytes; j++) {
                    long loc = i * mod;
                    sb.append(loc).append(" ");
                    raf.seek(loc);
                    raf.read(b);
                    b[0] ^= mask;
                    raf.seek(loc);
                    raf.write(b);
                }
                raf.close();
                sb.append("]");
                LOG.info("Corrupted " + f + " at " + sb);
            }
        } catch (Exception e) {
            throw new RemoteException("corruptFragments", e);
        }
    }
*/

    // Since filesystem operations can take a while to sync to disk,
    // the test code will try to verify that the changes it makes
    // are visible before proceding.  Otherwise, tests are inconsistent
    // depending on what changes show up in the FS.
    // Sleep the given number of milliseconds
    public void sleep(long msecs) {
        try {
            if (msecs > 0) {
                LOG.fine("Sleeping for " + msecs + " msecs");
            }
            Thread.sleep(msecs);
        } catch (InterruptedException e) {
            LOG.info("Sleep " + msecs + " Interrupted: " + e.getMessage());
        }
    }
    private boolean waitForFile(String filename, boolean shouldExist) {
        int retries = 60;
        int numIterationsBeforeWarning = 20;
        int warningIteration = retries - numIterationsBeforeWarning;
        int sleep = 1000; // msecs
        Runtime rt = Runtime.getRuntime();

        while (retries-- > 0) {
            try {
                // This might help get the filesystem attrs refreshed
                rt.exec("sync");
            } catch (Throwable t) {
                LOG.info("Failed to exec sync: " + t);
            }

            // XXX do we need to create an object each time
            // for the check to really be re-done?
            File f = new File(filename);
            boolean exists = f.exists();
            if (exists && shouldExist) {
                LOG.fine(filename + " exists");
                return (true);
            } else if (!exists && !shouldExist) {
                LOG.fine(filename + " does not exist");
                return (true);
            }

            LOG.fine("Sleeping " + sleep + " msecs waiting for file " +
                filename + " to " + (shouldExist ? "exist" : "not exist"));

            // after a while, log a message that we are waiting longer than we
            // think we should
            if (retries == warningIteration) {
                LOG.warning("Still waiting on file " + filename + " to have " +
                    "exist status " + shouldExist);
            }

            sleep(sleep);
        }

        return (false);
    }

    // Wait until the file is gone
    private boolean waitForFileToBeGone(String filename) {
        boolean b = waitForFile(filename, false);

        if (!b) {
            LOG.info("File " + filename + " is still not gone");
        }
        return (b);
    }

    // Wait until the file appears
    private boolean waitForFileToAppear(String filename) {
        boolean b = waitForFile(filename, true);

        if (!b) {
            LOG.info("File " + filename + " is still not there");
        }
        return (b);
    }

/*
    public void disableNode(int nodeid) throws RemoteException {
        MoboFRU fru = null;

        try {
            fru = new MoboFRU (String.valueOf (nodeid));
        } catch (UnknownFRUException ufe) {
            LOG.info ("disable of unknown node " + nodeid + " failed");
            return;
        }

        // assert fru != null;

        disableFRU (fru);
    }

    public void disableDisk(int nodeid, String disk) throws RemoteException {
        DiskFRU fru = null;

        try {
            fru = new DiskFRU (nodeid, disk);
        } catch (UnknownFRUException ufe) {
            LOG.info ("disable of unknown disk " + nodeid + ":" 
                        + disk  + " failed");
            return;
        }

        // assert fru != null;

        disableFRU (fru);
    }

    public void disableFRU(FRU fru) throws RemoteException {
        try {
            ClusterManager cm = ClusterManager.getInstance();
            cm.disableFRU (fru);
        } catch (UnknownFRUException ufe) {
            LOG.info ("disable of FRU " + fru + " failed");
        } catch (Exception e) {
            LOG.info ("disable of FRU " + fru + " failed: " + e);
        }
    }
*/

    public void injectServiceProblem(String svc_name, String action)
                                                        throws RemoteException {
        int nodeid = ServiceManager.LOCAL_NODE;

        if (!svc_name.startsWith("ErrInj_"))
            svc_name = "ErrInj_" + svc_name;

        boolean server = true;

        ManagedService.ProxyObject obj;
        obj = ServiceManager.proxyFor(nodeid, svc_name);
        if (obj == null)
            throw new RemoteException("Got null proxy, node=" + nodeid +
                                                    "  svc=" + svc_name);
        if (obj instanceof ErrInjManagedService.Proxy) {
            ErrInjManagedService.Proxy proxy = (ErrInjManagedService.Proxy) obj;

            // access embedded information.
            int value = proxy.getValue();
            //System.out.println("Client - got proxy " + proxy);

            if (proxy.getAPI() instanceof ErrInjManagedService) {
                ErrInjManagedService api;
                api = (ErrInjManagedService) obj.getAPI();
                if (action == null)
                    action = "excep";
                try {
                    if (action.equals("excep")) {
                        api.throwException("nodesrv");
                    } else if (action.equals("kill")) {
                        api.killJVM(server, "nodesrv");
                    } else if (action.equals("fd")) {
                        api.eatFDs(server, "nodesrv");
                    } else if (action.equals("mem")) {
                        api.eatMemory(server, "nodesrv");
                    } else if (action.equals("proc")) {
                        api.eatThreads(server, "nodesrv");
                    } else {
                        LOG.severe("injectServiceProblem: Unknown cmd: " + 
                                                                        action);
                        throw new RemoteException("Unknown cmd: " + action);
                    }
                } catch (ManagedServiceException e) {
                    LOG.info("Remote Invocation got: " + e);
                    throw new RemoteException("got exception: " + e);
                }
            }
        } else {
            LOG.severe("Proxy is not ErrInjManagedService.Proxy");
            throw new RemoteException(
                "Proxy is not ErrInjManagedService.Proxy: " + obj.toString());
        }
    }

    public void reboot(boolean fast) throws RemoteException {
        LOG.warning("REBOOTING fast=" + fast);
        try {
            shell.reboot(fast);
        } catch (Exception e) {
            LOG.severe("reboot failed: " + e);
            throw new RemoteException("", e);
        }
    }
}
