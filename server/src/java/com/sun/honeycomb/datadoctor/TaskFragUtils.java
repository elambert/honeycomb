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



package com.sun.honeycomb.datadoctor;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.common.IncompleteObjectException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.FooterExtension;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.DeletedFragmentException;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAException;


/**
 * Contains methods used by multiple tasks, mostly related to iterating
 * through directories and dealing with fragment files.
 */
public class TaskFragUtils {

    private static Logger LOG = Logger.getLogger(TaskFragUtils.class.getName());

    /*
     * This method returns the list of temporary fragments on the given *LOCAL*
     * disk as File objects
     * The disk must be local.
     */
    static File[] listTmpFrags(DiskId diskId) {

        if (diskId.nodeId() != DataDocConfig.getInstance().localNodeId()) {
            throw new IllegalArgumentException("disk " + diskId + " not local");
        }
        File tmpDir = new File(Common.makeTmpDirName(DiskProxy.getDisk(diskId)));
        return tmpDir.listFiles(new FragmentFilter());
    }

    /*
     * This method returns the list of filenames contained on the given map id
     * for the given disk.
     * Returns null if the map is not accessible or in case of error
     * The array will be empty if the directory is empty.
     */
    static String[] readMap(DiskId diskId, int mapId) {

        String [] res = null;

        if (diskId.nodeId() == DataDocConfig.getInstance().localNodeId()) {
            // Disk is on this node
            String path = Common.mapIdToDir(mapId, diskId);
            if (!verifyDirPath(path)) {
                LOG.warning("Directory " + path + " not found");
            } else {
                res = new File(path).list(new FragNameFilter());
            }
        }  else {
            // Remote call - call the corresponding DD
            DataDocIntf api = DataDocProxy.getServiceAPI(diskId.nodeId());
            if (api == null) {
                if (nodeIsAlive(diskId.nodeId())) {
                    // DD is not available for this node -
                    // should be a transient situation.
                    LOG.warning("failed to join DD on node " + diskId.nodeId());
                }
            } else {
                try {
                    res = api.readMap(diskId, mapId);
                } catch (ManagedServiceException me) {
                    // node/jvm failed ?
                    LOG.warning("DD rmi call failed " + me);
                }
            }
        }
        return res;
    }

    /*
     * This method returns the list of fragments committed on persistent
     * storage for the given OID on the given disk.
     * Returns null in case of error
     * The array will be empty if the directory is empty.
     */
    static String[] getDataFrags(NewObjectIdentifier oid, DiskId diskId) {

        String[] res = null;

        if (diskId.nodeId() == DataDocConfig.getInstance().localNodeId()) {
            // Disk on this node.
            String path = Common.mapIdToDir(oid.getLayoutMapId(), diskId);
            if (!verifyDirPath(path)) {
                LOG.warning("Directory " + path + " not found");
            } else {
                res = new File(path).list(new OidFilter(oid));
            }
        }  else {
            // remote call - call the corresponding DD
            DataDocIntf api = DataDocProxy.getServiceAPI(diskId.nodeId());
            if (api == null) {
                if (nodeIsAlive(diskId.nodeId())) {
                    // DD is not available for this node -
                    // should be a transient situation.
                    LOG.warning("failed to join DD on node " + diskId.nodeId());
                }
            } else {
                try {
                    res = api.getDataFrags(oid, diskId);
                } catch (ManagedServiceException me) {
                    // node/jvm failed ?
                    LOG.warning("DD rmi call failed " + me);
                }
            }
        }
        return res;
    }

    /**
     * Return all of the tmp frags on the specified node and do not fail if 
     * there are missing disks.
     * 
     * @param nodeId
     * @return return null in case of error
     */
    static String[] getTmpFrags() {
        String[] res = new String[0];
        String[] aux = null;
        Disk[] allDisks = DiskProxy.getDisks(ServiceManager.LOCAL_NODE);
      
        if (allDisks == null) { 
            LOG.warning("Unable to getDisks from DiskProxy.");
        }
        
        for (int i = 0; i < allDisks.length; i++) { 
           
            /*
             * If the disk is not mounted then skip because we want to be able 
             * to return all available tmp frags on this node and handle the 
             * cases of missing disks on a node.
             */
            if (!allDisks[i].isEnabled())
                continue;
            
            aux = getTmpFrags(null, allDisks[i].getId());
            
            if (aux == null) 
                return null;

            String[] c = new String[res.length + aux.length];
            System.arraycopy(res, 0, c, 0, res.length);
            System.arraycopy(aux, 0, c, res.length, aux.length);
            res = c;
        }
        
        return res;
    }
 
    /*
     * This method returns the list of transient fragments for the given OID
     * on the given disk.
     * Returns null in case of error
     * The array will be empty if the directory is empty.
     *
     * @param oid when a null oid is specified this function will return all of
     *            the tmp fragments on the specified disk.
     * @param diskId
     * @return
     */
     static String[] getTmpFrags(NewObjectIdentifier oid, DiskId diskId) {

        String[] res = null;

        if (diskId.nodeId() == DataDocConfig.getInstance().localNodeId()) {
            // Disk is on this node
            String path = Common.mapIdToTmpDir(diskId);
            if (!verifyDirPath(path)) {
                LOG.warning("Directory " + path + " not found");
            } else {
                if (oid == null) 
                    res = new File(path).list();
                else
                    res = new File(path).list(new OidFilter(oid));
            }
        }  else {
            // Remote call - call the corresponding DD
            DataDocIntf api = DataDocProxy.getServiceAPI(diskId.nodeId());
            if (api == null) {
                if (nodeIsAlive(diskId.nodeId())) {
                    // DD is not available for this node -
                    // should be a transient situation.
                    LOG.warning("failed to join DD on node " + diskId.nodeId());
                }
            } else {
                try {
                    res = api.getTmpFrags(oid, diskId);
                } catch (ManagedServiceException me) {
                    // node/jvm failed ?
                    LOG.warning("DD rmi call failed " + me);
                }
            }
        }
        return res;
    }

    /*
     * convert filename to oid, throws if filename is bad
     */
    public static NewObjectIdentifier fileNameToOid(String f)
        throws NotFragmentFileException
    {
        NewObjectIdentifier oid = Common.extractOIDFromFilename(f);
        if (oid == NewObjectIdentifier.NULL) {
            throw new NotFragmentFileException("filename does not resolve to OID", f);
        }
        return oid;
    }

    /*
     * extract fragment id from filename, throws on bad filename
     */
    public static int extractFragId(String f)
        throws NotFragmentFileException
    {
        int fragNum = Common.extractFragNumFromFilename(f);
        if (fragNum == -1) {
            throw new NotFragmentFileException("cannot extract fragId from file", f);
        }
        return fragNum;
    }

    /*
     * This method will remove the FragmentFile and also will make sure to
     * remove the entry from the system cache on this disk if it is the case
     * to be remove
     */
    static boolean removeFragment(FragmentFile frag) {
        boolean result = frag.remove();
        return result;
    }

    /*
     * This method will move the FragmentFile from to the FragmentFile to and
     * will do this in a correct manner by moving to a tmp directory and only
     * then committing it to disk with the correct FragmentFile commmit method.
     *
     */
    static boolean moveFragment(FragmentFile from, Disk disk) {
        return moveFragment (from, disk, true);
    }

    static boolean moveFragment(FragmentFile from, Disk disk, 
                                                   boolean failOnDeleted) {

        FragmentFile tmpFrag = null;
        try {
            // copy contents to tmp frag
            from.open();
            try {
                tmpFrag = new FragmentFile(from, from.getFragNum(), disk);
                tmpFrag.createFrom(from);
                // commit tmp frag
                tmpFrag.completeCreate();
            } finally {                
                // close and remove old fragment
                from.close();
            }
            from.remove();

        } catch (ObjectCorruptedException e) {
            LOG.warning("got " + e);
            return false;
        } catch (OAException e) {
            if (e instanceof DeletedFragmentException
                && !failOnDeleted) {
                // TODO: Fix this hack. Bug 6656099
                // For 1.1.1 we're going to leave deleted fragments in their
                // pre-expansion location, to minizmize churn and side-effects.
                // However, the real fix, for post 1.1.1, is to fix OA to allow
                // deleted objects to be moved during expansion to their 16-node
                // layouts.
                LOG.info ("Deleted fragment " + from.getOID().toString()
                    + " fragnum " + from.getFragNum() + " found and skipped");
            } else {
                LOG.warning("got " + e);
                return false;
            }
        } catch (FragmentNotFoundException fnfe) {
            LOG.warning("got " + fnfe);
            return false;

        } finally {
            if (tmpFrag != null) {
                tmpFrag.close();
            }
        }
        return true;
    }

    static void recoverFragment(NewObjectIdentifier oid,
                                int fragId,
                                Disk disk) throws
        IncompleteObjectException,
        OAException
    {
        OAClient oaClient = OAClient.getInstance();
        oaClient.recover(oid, fragId, disk);
    }

    /*
     * check if the given string points to a valid directory
     */
    private static boolean verifyDirPath(String path) {
        // TODO: should we generate a DataDoctor exception here, and
        // force the caller to catch it?
        File dir = new File(path);
        return dir.exists() && dir.isDirectory();
    }


    public static class OidFilter implements FilenameFilter {
        private String oidStr;
        OidFilter(NewObjectIdentifier oid) {
            oidStr = oid.toString();
        }
        public boolean accept(File dir, String name) {
            if (name.startsWith(oidStr) &&
                !name.endsWith(FooterExtension.SUFFIX)) {
                return true;
            }
            return false;
        }
    }

    private static class FragNameFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return !name.endsWith(FooterExtension.SUFFIX);
        }
    }
    
    private static class FragmentFilter implements FileFilter {
        public boolean accept(File f) {
            return(f.isFile() && 
                   !f.isHidden() && 
                   !f.getName().endsWith(FooterExtension.SUFFIX)
                   );
        }     
    }
    
    private static boolean nodeIsAlive(int nodeid) {
        Node[] nodes = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).getNodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() == nodeid) {
                return nodes[i].isAlive();
            }
        }
        return false;
    }
    
    /**
     * Return all of the tmp frags on the cluster that are available at this 
     * moment. This method will return the fragments from all available disks
     * and will not fail if any node or disk is unavaiable.
     * 
     * @param nodeId
     * @return return null in case of error
     */
    public static ArrayList getClusterTmpFrags() {
        Node[] nodes = null;
        ArrayList result = new ArrayList();
        
        NodeMgrService.Proxy proxy = 
                         ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
                
        if (proxy == null) {
            LOG.warning("NodeMgrService.Proxy is null.");
            return null;
        }
            
        nodes = proxy.getNodes();

        /*
         * Make sure to only count the nodes upto num_nodes
         */
        long num_nodes = ClusterProperties.getInstance().
                          getPropertyAsLong(ConfigPropertyNames.PROP_NUM_NODES);
        
        for (int n = 0; n < num_nodes; n++) { 
            Node node = nodes[n];    
          
            /*
             * Both cases where the node is not alive or not available we will 
             * continue because we stilll want t oget the available tmp frags.
             */
            if (node == null) 
                continue;
            
            if (!node.isAlive)
                continue;
            
            DataDocIntf api = DataDocProxy.getServiceAPI(node.nodeId());
          
            /*
             * if API is null return null because this is certainly an error 
             * and we dont' want to just skip on.
             */
            if (api == null)  {
                LOG.warning("Unable to retrieve DataDocProxy API for node " + node.nodeId());
                return null;
            }
           
            String[] aux;
            
            try {
                aux = api.getAllTmpFrags();
            } catch (ManagedServiceException e) {
                LOG.log(Level.WARNING,
                        "Unable to execute DataDocProxy.getAllTmpFrags on node "
                        + node.nodeId(), e);
                return null;
            }
           
            if (aux == null)  {
                LOG.warning("Unable to retrieve all tmp fragments from the cluster.");
                return null;
            }
           
            for (int i = 0; i < aux.length; i++)
                result.add(aux[i]);
        }
      
        return result;
    }
}
