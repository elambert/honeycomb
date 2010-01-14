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



package com.sun.honeycomb.emd.remote;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.common.MDDiskAbstraction;
import com.sun.honeycomb.emd.server.MDServer;

/**
 * This class creates Socket objects to reach MD servers
 */

public class ConnectionFactory {
    private static final Logger LOG = Logger.getLogger("ConnectionFactory");

    private static final int INITIAL_CONNECT_TIMEOUT = 5000; // in ms
    private static final int NUMBER_CONNECT_TRIES = 5;

    public static final int SOCKET_BUFFER_SIZE = 0x20000;
    
    /**********************************************************************
     *
     * DiskConnection class
     *
     **********************************************************************/

    public static class DiskConnection {
        private static final String NODE_PREFIX = "hcb";
        private InetSocketAddress nodeAddress;
        private ArrayList disks;

        private DiskConnection(InetSocketAddress nNodeAddress) {
            nodeAddress = nNodeAddress;
            disks = null;
        }

        private void addDisk(Disk disk) {
            if (disks == null) {
                disks = new ArrayList();
            }
            disks.add(disk);
        }
        
        public SocketAddress getNodeAddress() { return(nodeAddress); }
        public ArrayList getDisks() { return(disks); }

        public void toString(StringBuffer buffer) {

            buffer.append(nodeAddress.getHostName().substring(
                                                    NODE_PREFIX.length()) +
                          " <>");
            if (disks != null) {
                for (int i=0; i<disks.size(); i++) {
                    buffer.append(" "+((Disk)disks.get(i)).getPath());
                }
            } else {
                buffer.append(" All disks on that node");
            }
        }
        
        public String toString() {
            StringBuffer result = new StringBuffer();
            toString(result);
            return(result.toString());
        }
    }

    /**********************************************************************
     * 
     * ConnectionFactory class
     *
     **********************************************************************/
    
    public static DiskConnection getConnection(Disk disk) {
        DiskConnection result = new DiskConnection(new InetSocketAddress(disk.getNodeIpAddr(),
                                                                         MDServer.MD_SERVER_PORT));
        result.addDisk(disk);
        return(result);
    }

    public static DiskConnection[] getConnections(ArrayList disks) {
        HashMap connections = new HashMap();

        for (int i=0; i<disks.size(); i++) {
            Disk disk = (Disk)disks.get(i);
            if (disk == null) {
                continue;
            }
            String host = disk.getNodeIpAddr();
            DiskConnection conn = (DiskConnection)connections.get(host);
            if (conn == null) {
                conn = new DiskConnection(new InetSocketAddress(disk.getNodeIpAddr(),
                                                                MDServer.MD_SERVER_PORT));
                connections.put(host, conn);
            }
            conn.addDisk(disk);
        }

        Collection values = connections.values();
        DiskConnection[] result = new DiskConnection[values.size()];
        values.toArray(result);
        return(result);
    }

    public static DiskConnection[] getConnections(int layoutMapId,
            String cacheId, boolean useAllPossible) {
        if (layoutMapId == -1) {
            return(getConnections());
        }

        ArrayList disks = MDDiskAbstraction.getInstance((byte)0)
            .getUsedDisksFromMapId(layoutMapId, cacheId, useAllPossible);
        
        return(getConnections(disks));
    }
    
    public static DiskConnection[] getConnections(int layoutMapId,
                                                  String cacheId) {
        return getConnections (layoutMapId, cacheId, false);
    }
    
    public static DiskConnection[] getConnections(int[] layoutMapIds,
                                                  String cacheId) {
        if (layoutMapIds == null) {
            return(getConnections());
        }
	
        MDDiskAbstraction diskAbstraction = MDDiskAbstraction.getInstance((byte)0);
        HashSet disks = new HashSet();
        for (int i=0; i<layoutMapIds.length; i++) {
            ArrayList newDisks = diskAbstraction
                .getUsedDisksFromMapId(layoutMapIds[i], cacheId);
            disks.addAll(newDisks);
        }
        
        return(getConnections(new ArrayList(disks)));
    }
   
    public static DiskConnection[] getConnections() {
        String[] ips = MDDiskAbstraction.getInstance((byte)0).getAllIPs();
        DiskConnection[] result = new DiskConnection[ips.length];
        for (int i=0; i<ips.length; i++) {
            result[i] = new DiskConnection(new InetSocketAddress(ips[i], MDServer.MD_SERVER_PORT));
        }
        return(result);
    }

    /*
     * Connection method
     */
    
    public static Socket connect(DiskConnection address) 
        throws IOException {
        int timeout = INITIAL_CONNECT_TIMEOUT;
        int nbTries = 0;
        Socket result = null;

        for (nbTries = 0; nbTries < NUMBER_CONNECT_TRIES; nbTries++) {
            try {
                result = new Socket();
                result.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
                result.setTcpNoDelay(true);
                result.setKeepAlive(true);
                result.connect(address.getNodeAddress(), timeout);
                break;
            } catch (SocketTimeoutException e) {
                if ((result != null)
                    && (!result.isClosed())) {
                    try {
                        result.close();
                    } catch (IOException ignored) {
                    }
                }
                result = null;
                LOG.warning("Failed to connect to MD server on node ["+
                            ((InetSocketAddress)address.getNodeAddress()).getHostName()+"]. Try "+
                            (nbTries+1)+" out of "+NUMBER_CONNECT_TRIES);
                timeout *= 2;
            }
        }
        
        if (nbTries == NUMBER_CONNECT_TRIES) {
            throw new IOException("Failed to connect to node ["+
                                  ((InetSocketAddress)address.getNodeAddress()).getHostName()+"]. Timeout");
        }
        
        return(result);
    }
}
