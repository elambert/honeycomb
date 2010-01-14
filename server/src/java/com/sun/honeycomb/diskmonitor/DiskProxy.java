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



package com.sun.honeycomb.diskmonitor;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertException;

import java.io.IOException;

import java.util.Map;
import java.util.Iterator;
import java.util.Properties;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The proxy of the DiskMonitor service. It contains data about
 * the disks on this node.
 */
public class DiskProxy extends ManagedService.ProxyObject {

    private Disk[] disks;

    private transient static final Logger logger = 
        Logger.getLogger(DiskProxy.class.getName());

    //////////////////////////////////////////////////////////////////////
    // Static methods

    /*
     * Preferred way to get disks.
     */

    /** Return the requested disk, or null if failed. */
    static public Disk getDisk(DiskId id) {

        DiskProxy proxy = getProxy(id.nodeId());
        if (proxy == null)
            return null;

        return proxy.getDisk(id.diskIndex());
    }

    /** Get all disks on the given node, null if failed. */
    static public Disk[] getDisks(int nodeId) {

        DiskProxy proxy = getProxy(nodeId);
        if (proxy == null)
            return null;

        return proxy.getDisks();
    }

    /** This method used by OA */
    static public Disk getDisk(int nodeId, int diskIndex) {
        return getDisk(new DiskId(nodeId, diskIndex));
    }

    /** Get all disks on the local node, null if failed.  */
    static public Disk[] getLocalDisks() {
        DiskProxy proxy = getProxy();
        if (proxy == null)
            return null;

        return proxy.getDisks();
    }

    /** Get all disks in the cluster, null if failed. */
    static public Disk[] getClusterDisks() {

        DiskProxy[] proxies = getProxies();
        if (proxies == null)
            return null;

        ArrayList diskList = new ArrayList();

        for (int i = 0; i < proxies.length; i++) {
            Disk[] nodeDisks = proxies[i].getDisks();

            if (nodeDisks == null)
                continue;

            for (int j = 0; j < nodeDisks.length; j++) {
                diskList.add(nodeDisks[j]);
            }

        }
        if (diskList.isEmpty())
            return null;

        Disk[] allDisks = new Disk[diskList.size()];
        return (Disk[]) diskList.toArray(allDisks);
    }

    /** Return the disk with given path, or null if none. */
    static public Disk getDisk(int nodeId, String path) {

        Disk[] nodeDisks = DiskProxy.getDisks(nodeId);
        if (nodeDisks == null)
            return null;

        for (int i = 0; i < nodeDisks.length; i++)
            if (nodeDisks[i] != null && 
                    nodeDisks[i].getPath().equals(path))
                return nodeDisks[i];

        return null;
    }

    /*
     * Use calls below to get proxy or remote API.
     * If all you need are disks, use above methods.
     */

    /** Get current DiskProxy for given node, null if failed. */
    static public DiskProxy getProxy(int nodeId) {
        ManagedService.ProxyObject proxy = 
            ServiceManager.proxyFor(nodeId, DiskMonitor.class);

        if (proxy == null || ! (proxy instanceof DiskProxy)) {
            // okay for proxy to be null, node might be down or booting 
            return null;
        }

        return (DiskProxy) proxy;

    }
    
    /** get DiskMonitor API for given node, null if failed */
    public static DiskControl getAPI(int nodeId) {
        DiskProxy proxy = getProxy(nodeId);
        if (proxy == null) {
            return null;
        }
        ManagedService.RemoteInvocation api = proxy.getAPI();
        if (!(api instanceof DiskControl)) {
            return null;
        }
        return (DiskControl) api;
    }
        
    /** Get current DiskProxy for local node, null if failed. */
    static public DiskProxy getProxy() {

        NodeMgrService.Proxy proxy = getNodeMgrProxy();
        if (proxy == null)
            return null;

        return getProxy(proxy.nodeId());
    }

    /** Get all DiskProxies in the cluster, null if failed. */
    static public DiskProxy[] getProxies() {

        NodeMgrService.Proxy proxy = getNodeMgrProxy();
        if (proxy == null)
            return null;

        Node[] nodes = proxy.getNodes();
        DiskProxy[] proxies = new DiskProxy[nodes.length];

        int j = 0;
        for (int i = 0; i < nodes.length; i++) {
            DiskProxy diskProxy = null;

            if (nodes[i].isAlive && 
                   (diskProxy = getProxy(nodes[i].nodeId())) != null)
                proxies[j++] = (DiskProxy) diskProxy;
        }
        if (j == 0)
            return null;

        DiskProxy[] newArray = new DiskProxy[j];
        System.arraycopy(proxies, 0, newArray, 0, j);
        return newArray;
    }

    // That's all the static methods
    //////////////////////////////////////////////////////////////////////

    public String toString() {
        String s = "";
        for (int i = 0; i < disks.length; i++)
            s += disks[i].toString() + " ";
        return s;
    }

    /** 
     * IMPORTANT: disks[] must follow these conventions:
     * 1. is the same size as the maxDisks defined in the hardware profile
     * 2. if disk with index i is missing, disk[i]=null
     * 3. if disk[i] not null, it has diskIndex=i
     */
    DiskProxy(Disk[] diskArray, int nodeId) {

        // init the disk array to max number of disks, all null
        disks = new Disk[diskArray.length];
        for (int i = 0; i < disks.length; i++) {
            disks[i] = diskArray[i];
        } 
    }

    // Callers outside this class should get disks via static methods
    private Disk getDisk(int index) {
        if (index < 0 || index >= disks.length)
            return null;
        return disks[index];
    }
    
    private Disk[] getDisks() {
        return disks;
    }

    /** Get node manager proxy, null if failed. */
    static private NodeMgrService.Proxy getNodeMgrProxy() {
        NodeMgrService.Proxy proxy = 
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        if (proxy == null)
            logger.severe("couldn't get node manager proxy");

        return proxy;
    }

    /**
     * Alert private API.
     *
     * Exports the diks objects
     */
    public int getNbChildren() {
        return disks.length;
    }

    public AlertProperty getPropertyChild(int index)  
            throws AlertException {
         AlertProperty prop = null;
         if (index < disks.length) {
             String diskName = "disk" +  index;
             prop = new AlertProperty(diskName, AlertType.COMPOSITE);
         } else {
             throw new AlertException("index " + index + "does not exist");
         }
        return prop;
    }
    public AlertComponent getPropertyValueComponent(String property)  
            throws AlertException {
        Pattern p = Pattern.compile("^(\\w+)(\\d+)");
        Matcher m = p.matcher(property);
        if (m.matches()) {
            if (m.group(1).equals("disk")) {
                int index = Integer.parseInt(m.group(2));
                return (AlertComponent) getDisk(index);
            }
        }
        throw new AlertException("property " + property +
                                 " does not exist");
    }
}

