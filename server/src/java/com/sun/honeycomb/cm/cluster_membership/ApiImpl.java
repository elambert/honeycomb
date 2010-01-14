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



package com.sun.honeycomb.cm.cluster_membership;

import java.nio.channels.*;
import java.net.*;
import java.util.*;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Update;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Commit;
import com.sun.honeycomb.cm.cluster_membership.messages.api.Register;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Disconnect;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeInfo;
import com.sun.honeycomb.cm.cluster_membership.messages.api.C_NodeInfo;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.DiskChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ClusterInfo;
import com.sun.honeycomb.config.ClusterProperties;

/**
 * CMM API implementation.
 */
class ApiImpl implements CMMApi {
    
    public static final String protocol_error = 
        "ClusterMgmt - CMM API protocol error";

    protected static final Logger logger = 
        Logger.getLogger(ApiImpl.class.getName());

    private final String host;
    private final int port;
    private final int nodeId;
    private volatile NodeInfo nodeInfo;
    
    private ApiClientMultiplex multiplex;
    private Boolean lock = Boolean.FALSE;
        
    ApiImpl(String hostname, Integer nPort) throws CMMException 
    {
        try {
            if (hostname == null) {
                host = InetAddress.getLocalHost().getHostName();
            } else {
                host = hostname;
            }
        } catch (Exception e) {
            throw new CMMException("Can't retrieve localhost " + e);
        }
        
        try {
            port = nPort.intValue();
            SocketChannel api = SocketChannel.open();
            
            api.connect(new InetSocketAddress(host, port));
            api.configureBlocking(false);

            multiplex = new ApiClientMultiplex(api);
            try {
                multiplex.performRequest(new Register(Register.API),
                                         Register.class);
            } catch (CMMException e) {
                close();
                throw e;
            }

            nodeInfo = new NodeInfo(NodeInfo.MEMBER_ME);
            nodeInfo = (NodeInfo)multiplex.performRequest(nodeInfo, NodeInfo.class);
            
            CMMApi.Node[] nodes = nodeInfo.getNodes();
            if (nodes == null) {
                throw new CMMException(protocol_error);
            }

            nodeId = nodes[0].nodeId;
            nodeInfo = new NodeInfo();
            
        } catch (IOException e) {
            close();
            throw new CMMException(e);
        }
    }
    
    
    /**********************************
     * CMM client API implementation
     **********************************/
    
    synchronized public SocketChannel register() throws CMMException {
        SocketChannel event = null;
        ApiClientMultiplex m = null;
        boolean success = false;

        try {
            event = SocketChannel.open();
            event.connect(new InetSocketAddress(host, port));
            event.socket().setKeepAlive(true);
            event.configureBlocking(false);

            m = new ApiClientMultiplex(event);
            m.performRequest(new Register(Register.EVENT), Register.class);
            success = true;
        } catch (IOException e) {
            throw new CMMException(e);
        } finally {
            if (success) {
                m.closeSelector();
                try { event.configureBlocking(true); } catch (IOException e) {}
            } else {
                m.close();
            }
            m = null;
        }

        return event;
    }
    
    synchronized public Message getNotification(SocketChannel event) 
        throws CMMException 
    {
        if (event == null) {
            throw new CMMException("ClusterMgmt - CMM API event = null");
        }
        try {
            boolean omode = event.isBlocking();
            event.configureBlocking(false);
            Message msg = Message.receive(event);
            event.configureBlocking(omode);
            return msg;
        } catch (IOException ioe) {
            throw new CMMException(ioe);
        }
    }
    
    public void setEligibility(boolean eligible) throws CMMException {
        if (multiplex == null) {
            throw new CMMException("ClusterMgmt - CMM API not connected");
        }
        boolean success = false;
        try {
            NodeChange msg = null;
            if (eligible) {
                msg = new NodeChange(NodeChange.NODE_ELIGIBLE);
            } else {
                msg = new NodeChange(NodeChange.NODE_INELIGIBLE);
            }            
            msg = (NodeChange)multiplex.performRequest(msg, NodeChange.class);
            success = true;
            if (msg.getCause() != NodeChange.CMD_OK) {
                throw new CMMException("ClusterMgmt - operation failed");
            }
        } finally {
            if (!success) {
                close();
            }
        }
    }

    public int nodeId() throws CMMException {
        return nodeId;
    }

    public CMMApi.Node[] getNodes() throws CMMException {
        synchronized (lock) {
            if (lock.booleanValue()) {
                return nodeInfo.getNodes();
            }
            lock = Boolean.TRUE;
        }
        NodeInfo msg = null;
        boolean success = false;
        try {
            msg = new NodeInfo(NodeInfo.MEMBER_ALL);
            msg = (NodeInfo)multiplex.performRequest(msg, NodeInfo.class);
            success = true;
        } finally {
            synchronized (lock) {
                lock = Boolean.FALSE;
                nodeInfo = msg;
                if (!success) {
                    close();
                }
            }
        }
        return(nodeInfo.getNodes());
    }

    public CMMApi.Node getMaster() throws CMMException {
        CMMApi.Node[] nodes = getNodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isMaster()) {
                return nodes[i];
            }
        }
        throw new CMMException(protocol_error);
    }
  
    public int getActiveDiskCount() throws CMMException {
        int count = 0;
        CMMApi.Node[] nodes = getNodes();
        for (int i = 0; i < nodes.length; i++) {
            count += nodes[i].getActiveDiskCount();
        }
        return count;
    }
    
    public void setActiveDiskCount (int numDisks) throws CMMException {
        if (multiplex == null) {
            throw new CMMException ("ClusterMgmt - CMM API not connected");
        }
        boolean success = false;
        try {
            DiskChange change = new DiskChange (this.nodeId, numDisks);
            change = (DiskChange)multiplex.performRequest(change, DiskChange.class);
            success = true;
        } finally {
            if (!success) {
                close();
            }
        }
    }
    
    public boolean hasQuorum() throws CMMException {
        if (multiplex == null) {
            throw new CMMException ("ClusterMgmt - CMM API not connected");
        }
        boolean hasQuorum;
        boolean success = false;
        try {
            ClusterInfo info = new ClusterInfo();
            info = (ClusterInfo)multiplex.performRequest(info, ClusterInfo.class);
            hasQuorum = info.getQuorum();
            success = true;
        } finally {
            if (!success) {
                close();
            }
        }
        return hasQuorum;
    }
  
    public CMMApi.Node getViceMaster() throws CMMException {
        CMMApi.Node[] nodes = getNodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isViceMaster()) {
                return nodes[i];
            }
        }
        throw new CMMException(protocol_error);
    }

    public void wipeConfig(CMMApi.ConfigFile fileToUpdate, long version) 
        throws CMMException, ServerConfigException {
        configChange(null, fileToUpdate, true, version, "0000000000000000");
    }

    public void updateConfig(CMMApi.ConfigFile fileToUpdate, Map newProps)
        throws CMMException, ServerConfigException {
        configChange(newProps, fileToUpdate, false, -1, "0000000000000000");
    }

    public void storeConfig(CMMApi.ConfigFile fileToUpdate, long version, String md5sum)
        throws CMMException, ServerConfigException {
        configChange(null, fileToUpdate, false, version, md5sum);
    }

    /********************
     * Package access
     ********************/
    
    synchronized void configChange(Map newProps, 
                                   CMMApi.ConfigFile fileToUpdate,
                                   boolean clearMode, 
                                   long vers, 
                                   String md5sum)
        throws CMMException, ServerConfigException 
    {
        int tryCount = CMM.CONFIG_UPDATE_RETRY_COUNT;
        
        boolean success = false;        
        try {
            long version = vers;
            if (newProps != null) {
                version = CfgUpdUtil.getInstance().createFile(fileToUpdate, 
                                                              newProps);
            }

            ConfigChange change = configChangeProlog(fileToUpdate, 
                                                     clearMode, 
                                                     version, 
                                                     md5sum);
            while ((tryCount > 0) && (!success)) {
                tryCount--;
                success = configChangeRequest(change);
                
                if ((!success) && (tryCount > 0)) {
                    logger.info(CMMApi.LOG_PREFIX + 
                                " Config/update failed, retry the request ..."
                                );
                    try {
                        Thread.sleep(CMM.CONFIG_UPDATE_RETRY_INTERVAL);
                    } catch (InterruptedException e) {
                    }
                }
            }
        } finally {
            if (!success) {
                close();
                throw new ServerConfigException(CMMApi.LOG_PREFIX + 
                    "Failed to update config after internal retry.");
            }
        }
    }

    /*
     * Config update has been cut into pieces to allow Stress test
     * to reuse pieces
     */
    ConfigChange configChangeProlog(CMMApi.ConfigFile fileToUpdate, 
                                    boolean clearMode, 
                                    long version, 
                                    String md5sum) 
        throws CMMException 
    {
        if (multiplex == null) {
            throw new CMMException("ClusterMgmt - CMM API not connected");
        }

        Node master = getMaster();
        if (master == null) {
            throw new MasterNodeOperationException
                    ("ClusterMgmt -No master node for config update");
        }
        
        if (master.nodeId() != this.nodeId) {
            throw new MasterNodeOperationException
                    ("ClusterMgmt -Not master node for config update");
        }
        
        ConfigChange change = new ConfigChange(this.nodeId, 
                                               fileToUpdate.val(), 
                                               clearMode, 
                                               version, 
                                               md5sum);
        return change;
    }

    boolean configChangeRequest(ConfigChange change) {
        boolean success = false;
        try {
            ConfigChange msg;
            msg = (ConfigChange)multiplex.performRequest(change, 
                                                         ConfigChange.class, 
                                                         30000
                                                         );
            if ((msg != null) && (msg.getStatus() == ConfigChange.SUCCESS)) {
                success = true;
            }
        } catch (CMMException cmme) {
            logger.log(Level.SEVERE, 
                       CMMApi.LOG_PREFIX + "config/update failed for file " +
                       change.getFileToUpdate() + ", retry... ", 
                       cmme);
        }
        return success;
    }
    
    boolean isClosed() {
        return (multiplex == null);
    }
    
    synchronized void close() {
        if (multiplex != null) {
            try {
                multiplex.close();
            } finally {
                multiplex = null;
            }
        }
    }
    
}
