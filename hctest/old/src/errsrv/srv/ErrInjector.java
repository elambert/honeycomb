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



package com.sun.honeycomb.errsrv.srv;

import com.sun.honeycomb.errsrv.common.*;
import com.sun.honeycomb.errsrv.util.*;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.err_inj.ErrInjManagedService;
import com.sun.honeycomb.config.ClusterConfig;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.admin.ClusterManager;
import com.sun.honeycomb.admin.DiskFRU;
import com.sun.honeycomb.admin.MoboFRU;
import com.sun.honeycomb.admin.FRU;
import com.sun.honeycomb.admin.UnknownFRUException;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.io.*;
import java.util.*;

import java.util.logging.Logger;

public class ErrInjector extends UnicastRemoteObject 
				implements ErrInjectorIF {

    // config info
    private EClusterConfig econfig = new EClusterConfig();

    private OAClient oaClient = OAClient.getInstance();

    // for now set up disk config at startup
    private ArrayList diskPaths = new ArrayList();

    private static final Logger LOG =
				Logger.getLogger(ErrInjector.class.getName());

    public ErrInjector() throws RemoteException {

        super();
        LOG.info("ErrInjector is initializing");

        getClusterConfig();

        // Get the disks on all of the nodes
        for (int i=0; i<econfig.activeNodeIPs.length; i++)  {
            String diskPath = "/nfs/" + econfig.activeNodeIPs[i] + "/data";
            for (int j=0; j<econfig.disksPerNode; j++)
                diskPaths.add(new File(diskPath + "/" + j));
        }
                                                                                
    }

    // use to read properties
    private static final String PREFIX = "honeycomb.layout.";
    private static final String DATA_FRAGS = PREFIX + "datafrags";
    private static final String PARITY_FRAGS = PREFIX + "parityfrags";
    private static final String DISKS_PER_NODE=PREFIX + "diskspernode";

    /** Read cluster config from Cell Manager. */
    private void getClusterConfig() {

        LOG.info("getClusterConfig()");

        // Get the IPs of all nodes running in the cluster
        try {
            econfig.activeNodeIPs = getActiveNodes();
        } catch (Exception e) {
            LOG.severe("Can't get active nodes: " + e);
            System.exit(1);
        }

        for (int i=0; i<econfig.activeNodeIPs.length; i++) {
            LOG.info("IP " + econfig.activeNodeIPs[i]);
        }
        // get the cluster config, read from cluster_config.properties
        Properties cellConfig = ClusterConfig.getConfiguration();
        if (cellConfig == null) {
            LOG.warning("Unable to get cluster config");
            return;
        }

        // set nDataFrags
        String s = cellConfig.getProperty(DATA_FRAGS);
        if (s == null) {
            LOG.warning(DATA_FRAGS+" not set");
        }
        econfig.nDataFrags = (new Integer(s)).intValue();

        // set nParityFrags
        s = cellConfig.getProperty(PARITY_FRAGS);
        if (s == null) {
            LOG.warning(PARITY_FRAGS+" not set");
        }
        econfig.nParityFrags = (new Integer(s)).intValue();

        // get number of disks per node from config file
        s = cellConfig.getProperty(DISKS_PER_NODE);
        if (s == null) {
            LOG.warning(DISKS_PER_NODE+" not set");
        }
        econfig.disksPerNode = (new Integer(s)).intValue();

        LOG.info("disksPerNode=" + econfig.disksPerNode + 
		" nDataFrags=" + econfig.nDataFrags +
		" nParityFrags=" + econfig.nParityFrags);
    }

    public String[] getNodes() throws RemoteException {
        LOG.info("getNodes");
                                                                                
        NodeMgrService.Proxy proxy = ServiceManager.proxyFor(
                                                ServiceManager.LOCAL_NODE);
        if (proxy == null) {
            throw new RemoteException("Can't get proxy");
        }
        Node[] nodes = proxy.getNodes();
        String[] ret = new String[nodes.length];
        for (int i=0; i<ret.length; i++)
            ret[i] = nodes[i].toString();
                                                                                
        return ret;
    }

    public String[] getActiveNodes() throws RemoteException {
        LOG.info("getActiveNodes");
                                                                                
        NodeMgrService.Proxy proxy = ServiceManager.proxyFor(
                                                ServiceManager.LOCAL_NODE);
        if (proxy == null) {
            throw new RemoteException("Can't get proxy");
        }
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

    public EClusterConfig getConfig() throws RemoteException {
        return econfig;
    }

    private String dotForm(String oid) {
        int i = oid.indexOf('.');
        if (i != -1)
            return oid;
        NewObjectIdentifier noid = new NewObjectIdentifier(oid, true);
        return noid.toString();
    }

    public List getFragmentPaths(String oid, boolean allChunks) 
						throws RemoteException {
                                                                                
        // Make sure we have an oid
        if (oid == null) {
            throw new RemoteException("null oid");
        }
        if (econfig.activeNodeIPs.length == 0) {
            throw new RemoteException("NO IP's TO CHECK");
        }

        // convert to 'dot' form if needed
        int i = oid.indexOf('.');
        boolean hex = (i == -1);
        NewObjectIdentifier noid = new NewObjectIdentifier(oid, hex);
        if (hex) {
           String s = oid;
           oid = dotForm(oid);
           LOG.info("oid=" + s + " / " + oid);
        } else 
           LOG.info("oid=" + oid);

        int chunk = noid.getChunkNumber();

        // handle simple case
        if (!allChunks)
            return getFrags(oid, chunk);

        if (chunk != 0)
            LOG.warning("Getting chunks >= " + chunk);

        // get List of Lists
        ArrayList chunkList = new ArrayList();
        List frags = getFrags(oid, chunk);
        int mapId = noid.getLayoutMapId();
        while (frags.size() > 0) {
            chunkList.add(frags);
            // define next chunk
            chunk++;
            noid.setChunkNumber(chunk);
            mapId = LayoutClient.getConsecutiveLayoutMapId(mapId);
            noid.setLayoutMapId(mapId);
            oid = noid.toString();
            frags = getFrags(oid, chunk);
        }
        return chunkList;
    }

    private List getFrags(String oid, int chunk) throws RemoteException {

        // Get the uid from the oid
        String uid = TestUtil.getUid(oid);
        String hashPath;
        try {
            String uidHash = TestUtil.getUidSHA1(uid);
            String dirA = uidHash.substring(0, 2);
            String dirB = uidHash.substring(2, 4);
            hashPath = "/" + dirA + "/" + dirB + "/" + oid + "_";
        } catch (Exception e) {
            throw new RemoteException("Getting/splitting uid sha1, chunk " + 
								chunk, e);
        }
                                                                                
        // Look for the fragments on all of the disks
        ArrayList fragmentPaths = new ArrayList();
        int n_frags = econfig.nDataFrags + econfig.nParityFrags;
        for (int fg=0; fg<n_frags; fg++) {
            boolean found = false;
            for (int i=0; i<diskPaths.size(); i++) {
                String fragment;
                                                                                
                fragment = diskPaths.get(i).toString() + hashPath + fg;
                File file = new File(fragment);
                if (file.exists()) {
                    if (found) {
                        LOG.warning("Found DUPLICATE fragment (chunk " + 
					chunk + "): " + fg + ": " +
					fragment);
                    } else {
                        LOG.info("Found fragment " + fg + ": " + fragment);
                        fragmentPaths.add(file);
                        found = true;
                    }
                } else {
                    LOG.fine("Failed to find fragment " + fg +
                                 " (chunk " + chunk + ") at path " + fragment);
                }
            }
            if (!found)
                LOG.info("Failed to find fragment " + fg + " (" +
				hashPath + fg + " chunk " + chunk + ")");
        }
                                                                                
        //for (int i=0; i<fragmentPaths.size(); i++)
            //LOG.info("FRAG " + fragmentPaths.get(i).toString());
                                                                                
        return fragmentPaths;

    }

    public String getDataOID(String mdOid) throws RemoteException {
        NewObjectIdentifier noid;
        try {
            noid = new NewObjectIdentifier(mdOid, true);
            if (noid.getObjectType() == NewObjectIdentifier.DATA_TYPE) {
                throw new RemoteException("OID is already data type");
            }
            Context tmpctx = new Context();
            SystemMetadata systemMetadata = oaClient.open(noid, tmpctx);
            tmpctx.dispose();
            noid = (NewObjectIdentifier)
			systemMetadata.get(SystemMetadata.FIELD_LINK);
        } catch (Exception e) {
            throw new RemoteException("getDataOID()", e);
        }
        return noid.toHexString();
    }

    // Delete the given number of fragments
    public void deleteFragments(List fragments, int numToDelete)
        				throws RemoteException {
        LOG.info("(delete " + numToDelete + " frags out of " +
                 fragments.size() + ")");
        for (int i=0; i<numToDelete; i++) {
            String oldFileName = fragments.get(i).toString();
            String newFileName = oldFileName + ".del";
            File oldFile = new File(oldFileName);       
            File newFile = new File(newFileName);
            if (!oldFile.renameTo(newFile)) {
                throw new RemoteException("Failed to delete " +
                                                 "fragment: " + i + " " +
                                                 oldFileName);
            } else {
                LOG.fine("Renamed file [" + i + "] " + oldFileName +
                    " to " + newFileName);
            }
        }

        // Wait for the filesystem change to sync.
        // We do this in a separate loop so that we can wait all
        // at once, instead of waiting after each delete
        for(int i = 0; i < numToDelete; i++) {
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

    // Delete the specific fragment
    public void deleteOneFragment(List fragments, int fragToDelete)
					throws RemoteException {
        LOG.info("(frag " + fragToDelete + " out of " + fragments.size() + ")");
        String oldFileName = fragments.get(fragToDelete).toString();
        String newFileName = oldFileName + ".del";
        File oldFile = new File(oldFileName);       
        File newFile = new File(newFileName);
        if (!oldFile.renameTo(newFile)) {
            throw new RemoteException("Failed to delete " +
                "fragment " + fragToDelete + ": " +
                oldFileName);
        } else {
            LOG.fine("Rename file [" + fragToDelete + "] " + oldFileName +
                " to " + newFileName);
        }

        // Wait for the filesystem change to sync.
        // We do this in a separate loop so that we can wait all
        // at once, instead of waiting after each delete
        if (!waitForFileToBeGone(oldFileName) ||
            !waitForFileToAppear(newFileName)) {
            throw new RemoteException("Verify failed in delete " +
                "fragment: " + fragToDelete + " " +
                oldFileName);
        } else {
            LOG.fine("Verified rename file [" + fragToDelete + "] " +
                oldFileName + " to " + newFileName);
        }
    }

    // Restore moved fragments, given a list of originals
    public void restoreFragments(List fragments, int numToRestore)
						throws RemoteException {
        LOG.info("restore " + numToRestore + " out of " + fragments.size());
        for (int i = 0; i < numToRestore; i++) {
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
        for(int i = 0; i < numToRestore; i++) {
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

    // Delete the given list of fragments
    public void deleteFragments(List fragments, int[] list)
						throws RemoteException {
        LOG.info("delete list len=" + list.length + " out of " + 
                 fragments.size());
        for(int i=0; i<list.length; i++) {
            String oldFileName = fragments.get(list[i]).toString();
            String newFileName = oldFileName + ".del";
            File oldFile = new File(oldFileName);       
            File newFile = new File(newFileName);
            if (!oldFile.renameTo(newFile)) {
                throw new RemoteException("Failed to delete " +
                                                 "fragment: " + list[i] + " " +
                                                 oldFileName);
            } else {
                LOG.fine("Renamed file [" + list[i] + "] " + oldFileName +
                    " to " + newFileName);
            }
        }

        // Wait for the filesystem change to sync.
        // We do this in a separate loop so that we can wait all
        // at once, instead of waiting after each delete
        for(int i=0; i<list.length; i++) {
            String oldFileName = fragments.get(list[i]).toString();
            String newFileName = oldFileName + ".del";
            if (!waitForFileToBeGone(oldFileName) ||
                !waitForFileToAppear(newFileName)) {
                throw new RemoteException("Verify failed in delete " +
                                                 "fragment: " + list[i] + " " +
                                                 oldFileName);
            } else {
                LOG.fine("Verified rename file [" + list[i] + "] " + 
						oldFileName +
						" to " + newFileName);
            }
        }
    }

    public void restoreFragments(List fragments, int[] list)
        				throws RemoteException {
        LOG.info("restore list len=" + list.length + " out of " + 
                 fragments.size());
        for (int i = 0; i < list.length; i++) {
            String newFileName = fragments.get(list[i]).toString();
            String oldFileName = newFileName + ".del";
            File newFile = new File(newFileName);
            File oldFile = new File(oldFileName);
            if (!oldFile.renameTo(newFile)) {
                throw new RemoteException("Failed to restore " +
                                                 "fragment " + list[i] + ": " +
                                                 newFileName);
            } else {
                LOG.fine("Restored file [" + list[i] + "] " + oldFileName +
                    " to " + newFileName);
            }
        }

        // Wait for the filesystem change to sync.
        // We do this in a separate loop so that we can wait all
        // at once, instead of waiting after each restore
        for(int i = 0; i < list.length; i++) {
            String newFileName = fragments.get(list[i]).toString();
            String oldFileName = newFileName + ".del";
            if (!waitForFileToBeGone(oldFileName) ||
                !waitForFileToAppear(newFileName)) {
                throw new RemoteException("Verify failed in restore " +
                                                 "fragment: " + list[i] + " " +
                                                 oldFileName);
            } else {
                LOG.fine("Verified rename file [" + list[i] + "] " + 
                             oldFileName + " to " + newFileName);
            }
        }
    }

    public void corruptFragments(List fragments, int[] list, int nbytes, 
								byte mask)
        				throws RemoteException {
        if (nbytes < 1)
            throw new RemoteException(
				"corruptFragments nbytes must be >0");

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
        int retries = 10;
        int sleep = 1000; // msecs
        
        while (retries-- > 0) {
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

            sleep(sleep);
        }

        return (false);
    }

    // Wait until the give file is gone
    private boolean waitForFileToBeGone(String filename) {
        boolean b = waitForFile(filename, false);

        if (!b) {
            LOG.info("File " + filename + " is still not gone");
        }
        return (b);
    }

    // Wait until the give file appears
    private boolean waitForFileToAppear(String filename) {
        boolean b = waitForFile(filename, true);

        if (!b) {
            LOG.info("File " + filename + " is still not there");
        }
        return (b);
     }

    public void disableNode (int nodeid) throws RemoteException {
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

    public void disableDisk (int nodeid, String disk) throws RemoteException {
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

    public void disableFRU (FRU fru) throws RemoteException {
        try {
            ClusterManager cm = ClusterManager.getInstance();
            cm.disableFRU (fru);
        } catch (UnknownFRUException ufe) {
            LOG.info ("disable of FRU " + fru + " failed");
        } catch (Exception e) {
            LOG.info ("disable of FRU " + fru + " failed: " + e);
        }
    }

    public void injectServiceProblem(int nodeid, String svc_name,
								String action)
						throws RemoteException {
        if (nodeid == -1)
            nodeid = ServiceManager.LOCAL_NODE;

        if (!svc_name.startsWith("ErrInj_"))
            svc_name = "ErrInj_" + svc_name;

        boolean server = true;

        ManagedService.ProxyObject obj;
        obj = ServiceManager.proxyFor(nodeid, svc_name);
        if (obj == null)
            throw new RemoteException("Got null proxy, node=" + nodeid +
						"  svc=" + svc_name);
        if (obj instanceof ErrInjManagedService.Proxy) {
            ErrInjManagedService.Proxy proxy = 
				(ErrInjManagedService.Proxy) obj;

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
                        api.throwException("errsrvr");
                    } else if (action.equals("kill")) {
                        api.killJVM(server, "errsrvr");
                    } else if (action.equals("fd")) {
                        api.eatFDs(server, "errsrvr");
                    } else if (action.equals("mem")) {
                        api.eatMemory(server, "errsrvr");
                    } else if (action.equals("proc")) {
                        api.eatThreads(server, "errsrvr");
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
            throw new RemoteException("Proxy is not ErrInjManagedService.Proxy: " + obj.toString());
        }
    }
}
