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



/*
 * The purpose of this class is to provide an abstraction of the disk
 * mailboxes that makes sense for the Metadata module.
 */

package com.sun.honeycomb.emd.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.oa.OAClient;

public class MDDiskAbstraction {

    /**********************************************************************
     *
     * Configuration parameters
     *
     **********************************************************************/

    public static long refreshPeriod = 60000; /* in ms. */
    private static final String MD_REDUNDANCY_PROPERTY = "honeycomb.md.redundancy";

    /**********************************************************************
     *
     * Implementation of the class
     *
     **********************************************************************/

    private static MDDiskAbstraction instance = null;
    private static Logger LOG = Logger.getLogger("MDDiskAbstraction");

    /*
     * The getInstance class is THE one that allows clients to have an
     * handle to a MDDiskAbstraction object.
     */

    public static final byte FORCE_REFRESH_LOCAL       = 0x1;
    public static final byte FORCE_REFRESH_REMOTE      = 0x2;

    public synchronized static MDDiskAbstraction getInstance(byte forceFlag) {
        if (instance == null) {
            instance = new MDDiskAbstraction();
        }

        instance.refresh(forceFlag);

        return(instance);
    }

    private HashSet localDisks;
    private HashSet remoteDisks;
    private long lastRefreshTime;

    private ArrayList nodes;
    private int localNodeId;
    private int redundancy;

    private MDDiskAbstraction() {
        localDisks = new HashSet();
        remoteDisks = new HashSet();
        lastRefreshTime = 0;
        nodes = new ArrayList();
        localNodeId = -1;
        redundancy = -1;
    }

    private void checkRedundancy() {
        if (redundancy != -1) {
            return;
        }
        
        try {
            ClusterProperties config = ClusterProperties.getInstance();
            String value = config.getProperty(MD_REDUNDANCY_PROPERTY);
            if (value == null) {
                throw new EMDException("The "+MD_REDUNDANCY_PROPERTY+
                        " property is not defined in the config");
            }
        
            redundancy = Integer.parseInt(value);
        } catch (EMDException e) {
            LOG.severe("Failed to retrieve the redundancy value ["
                       +e.getMessage()+"]");
        }
    }

    /*
     * Get the disks in the system.
     * Remote disks are computed from the layout proxy
     * Local disks are computed from the local disk manager because we
     * don't want to wait for the layout grace period to unmount local
     * databases.
     */
    
    private void refresh(byte forceFlag) {
        boolean expired = (System.currentTimeMillis()-lastRefreshTime >= refreshPeriod);

        if ((expired) || ((forceFlag & FORCE_REFRESH_REMOTE) != 0)) {
            /* I have to get the local node id */
            NodeMgrService.Proxy proxy;
            proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            if (proxy == null) {
                throw new InternalException("Fail to get node proxy");
            }

            if (localNodeId == -1) {
                localNodeId = proxy.nodeId();
            }
            nodes.clear();
            Node[] newNodes = proxy.getNodes();
            for (int i=0; newNodes != null && i<newNodes.length; i++) {
                if (newNodes[i].isAlive()) {
                    nodes.add(newNodes[i]);
                }
            }

            DiskMask diskMask = LayoutProxy.getCurrentDiskMask();
            if (diskMask != null && diskMask.onlineDisks() != null) {
                remoteDisks.clear();
                Disk[] disks = diskMask.onlineDisks();
                for (int i=0; disks != null && i<disks.length; i++) {
                    if (disks[i] == null) {
                        continue;
                    }
                    if (disks[i].nodeId() != localNodeId) {
                        remoteDisks.add(disks[i]);
                    }
                }
            }
        }

        if ((expired) || ((forceFlag & FORCE_REFRESH_LOCAL) != 0)) {
            localDisks.clear();
            Disk[] lDisks = DiskProxy.getLocalDisks();
            for (int i=0; lDisks !=null && i<lDisks.length; i++) {
                if (lDisks[i].isEnabled()) {
                    localDisks.add(lDisks[i]);
                }
            }
        }

        if (expired) {
            lastRefreshTime = System.currentTimeMillis();
        }
    }

    private boolean checkExistence(Disk disk,
                                   boolean localCheck) 
        throws NoSuchObjectException {
        boolean local = localDisks.contains(disk);
        boolean remote = remoteDisks.contains(disk);

        if ((!local) && (!remote)) {
            /* We are given an unknown disk ??? Refresh the list of disks */
            refresh((byte)(FORCE_REFRESH_LOCAL & FORCE_REFRESH_REMOTE));
            local = localDisks.contains(disk);
            remote = remoteDisks.contains(disk);
            
            if ((!local) && (!remote)) {
                throw new NoSuchObjectException("The Disk "+disk+" cannot be found");
            }
        }
        
        if (localCheck) {
            return(local);
        } else {
            return(remote);
        }
    }

    /**********************************************************************
     *
     * Implementation of the public methods of the class
     *
     **********************************************************************/

    public Disk[] getLocalDisks() {
        Disk[] result = new Disk[localDisks.size()];
        localDisks.toArray(result);

        return(result);
    }

    public Disk[] getRemoteDisks() {
        Disk[] result = new Disk[remoteDisks.size()];
        remoteDisks.toArray(result);

        return(result);
    }

    public boolean isLocal(Disk disk) 
        throws NoSuchObjectException {
        return(checkExistence(disk, true));
    }

    public boolean isRemote(Disk disk) 
        throws NoSuchObjectException {
        return(checkExistence(disk, false));
    }

    public String[] getAllIPs() {
        String[] result = new String[nodes.size()];

        for (int i=0; i<nodes.size(); i++) {
            result[i] = ((Node) nodes.get(i)).getAddress();
        }

        return(result);
    }

    /**
     * The <code>getDisksFromMapId</code> method get disks for a given map
     * id.
     * <br><br>
     * The Disks are sorted in regard to the node id to which they
     * belong. That way, we have a ring of disks and if each MD client asks
     * the next available entry, eventually all the results will propagate
     * to all nodes.
     *
     * @param The placementMapId parameter (an <code>int</code>) specifies
     * the layoutMapId to consider
     * @param The number parameter (an <code>int</code>) gives the number
     * of disks to return (-1 for all)
     * @return an <code>ArrayList</code> value
     */

    private ArrayList getDisksFromMapId(int placementMapId,
                                        int number) {

        StringBuffer trace = null;

        // get the layout
        Layout disksInMap =
            LayoutClient.getInstance().getLayoutForRetrieve(placementMapId);

        // possibly truncate list to have desired number of disks
        if (number != -1 && number < disksInMap.size()) {
            while (number != disksInMap.size()) {
                disksInMap.remove(disksInMap.size()-1);
            }
        } 

        if (LOG.isLoggable(Level.FINE)) {
            trace = new StringBuffer();
            trace.append("Map "+placementMapId+" contains disks "+
                disksInMap+ " using these for Metadata: ");
        }

        // sort disks, find index of first disk with node greater than us
        Collections.sort(disksInMap);
        int i;
        for (i=0; i < disksInMap.size(); i++) {
            if (((DiskId)disksInMap.get(i)).nodeId() > localNodeId) { 
                break;
            }
        } 

        // make the "ring" starting at index we left off above
        ArrayList disks = new ArrayList(); 
        while (disks.size() < disksInMap.size()) {
            if (i == disksInMap.size()) {
                i = 0;  // wrap to beginning
            }
            Disk d = disksInMap.getDisk(i);
            disks.add(d);
            if (LOG.isLoggable(Level.FINE)) {
            	// during grace period disks are null
            	if (d == null)
            		trace.append("null ");
            	else
            		trace.append(d.getId().toStringShort()+" ");
            }
            i++;
        } 

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(trace.toString());
        }

        return disks;
    }

    public ArrayList getDisksFromMapId(int placementMapId) {
        return(getDisksFromMapId(placementMapId, -1));
    }

    public ArrayList getUsedDisksFromMapId(int placementMapId,
            String cacheId) {
        return getUsedDisksFromMapId (placementMapId, cacheId, false);
    }
    
    public ArrayList getUsedDisksFromMapId(int placementMapId,
                                           String cacheId, boolean useAll) {
        checkRedundancy();
        if (redundancy == -1) {
            return(getDisksFromMapId(placementMapId, -1));
        }

        int nbCaches = -1;

        if (cacheId.equals(CacheInterface.SYSTEM_CACHE)) {
            ObjectReliability rel = OAClient.getInstance().getReliability();
            if (useAll)
                nbCaches = rel.getDataFragCount() + rel.getRedundantFragCount();
            else
                nbCaches = rel.getRedundantFragCount() + 1;
        } else {
            nbCaches = redundancy;
        }
        
        return(getDisksFromMapId(placementMapId, nbCaches));
    }
}
