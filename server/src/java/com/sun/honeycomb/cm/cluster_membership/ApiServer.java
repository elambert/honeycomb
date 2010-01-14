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
import java.util.logging.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.nio.channels.ClosedChannelException;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.api.Register;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChangeNotif;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Disconnect;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeInfo;
import com.sun.honeycomb.cm.cluster_membership.messages.api.C_NodeInfo;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.DiskChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ClusterInfo;


class ApiServer {

    private static final Integer LOBBY_API_CHANNEL_ATTACHEMENT  = new Integer(67);
    private static final Integer LOBBY_MSG_CHANNEL_ATTACHEMENT  = new Integer(76);

    private ServerSocketChannel serverChannel;
    private Selector clients;
    private final LobbyTask lobby;
    private Selector notifications;
    private volatile ConfigChange configChangeRequest;

    ApiServer(LobbyTask lobbyTask) {
        lobby = lobbyTask;
        clients = null;
        notifications = null;
        serverChannel = null;
        configChangeRequest=null;
    }

    void init() {
        String host = "";
        reset();
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);

            Node local = NodeTable.getLocalNode();
            ServerSocket sock = serverChannel.socket();

            InetSocketAddress addr;
            host = local.getInetAddr().getAddress().getHostAddress();

            String testNodeId = System.getProperty("cmm.test.nodeid");
            if (testNodeId == null) {
                addr = new InetSocketAddress(host, CMM.API_PORT);
            } else {
                int port = CMM.API_PORT + 2*(Integer.parseInt(testNodeId)-101);
                lobby.warning("The ApiServer has been started "
                              +"in a test environment [port "+port+"]"
                              );
                addr = new InetSocketAddress(host, port);
            }

            sock.setReuseAddress(true);
            sock.bind(addr);
            if (addr.isUnresolved()) {
                String error = "cannot resolve local host address";
                throw new CMMError("ClusterMgmt - " + error);
            }
            lobby.info("CMM API connected to " + addr);
            clients = Selector.open();
            
            try {
                // API connection
                lobby.getApiSourceChannel().register(clients, 
                                                     SelectionKey.OP_READ,
                                                     LOBBY_API_CHANNEL_ATTACHEMENT
                                                     );
                // Lobby queue
                lobby.getMsgSourceChannel().register(clients, 
                                                     SelectionKey.OP_READ,
                                                     LOBBY_MSG_CHANNEL_ATTACHEMENT
                                                     );
            } catch (ClosedChannelException e) {
                throw new CMMError("Failed to register the communication channels"
                                   + "in the selector ["+e.getMessage()+"]");
            }

            notifications = Selector.open();
            serverChannel.register(clients, SelectionKey.OP_ACCEPT);
            lobby.info("CMM API registered for clients");
        } catch (IOException e) {
            throw new CMMError(e + " " + host);
        }
    }

    void reset() {
        boolean error = false;
        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (IOException e) {
                error = true;
            }
            serverChannel = null;
        }
        if (clients != null) {
            try {
                clients.close();
            } catch (IOException e) {
                error = true;
            }
            clients = null;
        }
        if (notifications != null) {
            try {
                notifications.close();
            } catch (IOException e) {
                error = true;
            }
            notifications = null;
        }
        if (error) {
            lobby.severe("CMM API I/O error");
        }
        configChangeRequest = null;
    }

    private void acceptClient() throws IOException {
        SocketChannel sock = serverChannel.accept();
        if (sock == null) {
            return;
        }

        sock.configureBlocking(false);
        sock.socket().setSoLinger(true, CMM.HEARTBEAT_TIMEOUT / 1000);
        sock.socket().setKeepAlive(true);
        sock.socket().setTcpNoDelay(true);
        try {
            Message msg = Message.receive(sock, 10);
            if (!(msg instanceof Register)) {
                lobby.fine("not registration message, closing!");                
                sock.close();
                return;
            }
            int type = ((Register)msg).getRegistrationType();
            switch (type) {
            case Register.API:
                sock.register(clients, SelectionKey.OP_READ);
                break;
            case Register.EVENT:
                sock.register(notifications, SelectionKey.OP_WRITE);
                break;
            default:
                sock.close();
                return;
            }
            msg.send(sock);
            
        } catch (CMMException e) {
            lobby.info("socket closed " + e);
            sock.close();
        }
    }

    private void closeClient(SelectionKey key) {
        SocketChannel sock = (SocketChannel) key.channel();
        key.cancel();
        try {
            synchronized (this) {
                ConfigChange msg = configChangeRequest;
                if (msg != null && msg.getSocket() == sock) {
                    configChangeRequest = null;
                }                    
            }
            sock.close();
            
        } catch (IOException e) {
            lobby.warning("CMM Api I/O error");
        }
    }
    
    /**
     * Dispatch an event notification to all clients registered
     * for notifications. Update locally CMM if this is a notification
     * for the default config file.
     */
    private void dispatchClients(Message msg) {
        
        if (msg instanceof ConfigChangeNotif) {
            ConfigChangeNotif notif = (ConfigChangeNotif)msg;
            if (notif.getFileUpdated() == CMMApi.UPDATE_DEFAULT_FILE) {
                CMM.updateConfig();
                lobby.info("Configuration updated");
            }
        }
        
        Iterator it = notifications.keys().iterator();
        while (it.hasNext()) {
            SelectionKey key = (SelectionKey) it.next();
            if (key.isValid()) {
                SocketChannel sock = (SocketChannel) key.channel();
                try {
                    msg.send(sock);
                } catch (CMMException e) {
                    lobby.info("exception " + e);
                    closeClient(key);
                }                
            }
        }
    }
    
    /**
     * Dispatch a message either to the lobby or to the clients.
     */                                                              
    private void dispatchMessage(SelectionKey key) throws IOException {
        Integer attachment = (Integer)key.attachment();
        Message msg = null;
        try {
            ReadableByteChannel input = (ReadableByteChannel)key.channel();
            msg = Message.receive(input);
            if (attachment.equals(LOBBY_API_CHANNEL_ATTACHEMENT)) {
                dispatchClients(msg);
                
            } else if (attachment.equals(LOBBY_MSG_CHANNEL_ATTACHEMENT)) {
                lobby.dealWithMessage(msg);
                
            } else {
                lobby.severe("Got a message from an unknown channel. "+
                             "Dropping it ["+ msg+"]"
                             );
            }
        } catch (CMMException e) {
            lobby.warning("CMM communication error ["+e.getMessage()+"]");
        }
    }        
    
    /**
     * Process a client request.
     */
    private void processClient(SelectionKey key) {
        SocketChannel sock = (SocketChannel) key.channel();

        try {
            Message msg = Message.receive(sock);
            if (msg == null) {
                closeClient(key);
                return;
            }

            lobby.fine("CMM Api client " + sock + " request " + msg);
            
            if (msg instanceof Disconnect) {
                //
                // The client wants to disconnect from CMM.
                //
                closeClient(key);

            } else if (msg instanceof NodeInfo) {
                //
                // The client wants nodes table info
                //
                ((NodeInfo) msg).updateInfo();
                msg.send(sock);

            } else if (msg instanceof NodeChange) {
                //
                // The client wants to change local node state
                //
                lobby.processNodeChange((NodeChange)msg);
                msg.send(sock);

            } else if (msg instanceof ConfigChange) {
                //
                // Config update - We do not reply here.
                // In order to make the config update synchronous from the
                // client's point of view, the reply will be sent only once
                // the Update message will have gone through the ring and
                // back.
                // The reply will be sent back in ackConfigChange
                //
                lobby.info("Got configChange message in processClient.");
                ConfigChange req = (ConfigChange) msg;
                processConfigChange(sock, req);

            } else if (msg instanceof DiskChange) {
                //
                // The client wants to change the number of disks
                // as seen by this CMM
                //
                lobby.processDiskChange ((DiskChange)msg);
                msg.send(sock);

            } else if (msg instanceof ClusterInfo) {
                //
                // Get CMM view
                //
                lobby.updateClusterInfo ((ClusterInfo)msg);
                msg.send(sock);
            }
        } catch (CMMException e) {
            lobby.info("exception " + e);
            closeClient(key);
        }
    }

    /**
     * Process a client config update request.
     */
    private void processConfigChange(SocketChannel sock, ConfigChange req)
        throws CMMException
    {
        synchronized (this) {
            if (null != configChangeRequest) {
                
                if (req.getFileToUpdate() == 
                    configChangeRequest.getFileToUpdate() &&
                    req.getVersion() == 
                    configChangeRequest.getVersion()) {
                    //
                    // There is already a pending config/update.
                    // but this is for the same file and same
                    // version, this is a retry
                    //
                    lobby.warning("Retry on a config/update which is "
                                  + "already in progress... proceed"
                                  );

                } else if (configChangeRequest.hasExpired()) {
                    //
                    // We should never hit that case since we allow
                    // only one config/update but we may have lost 
                    // the config update message in the ring.
                    //
                    lobby.severe("Current config update expired " 
                                 + configChangeRequest + " ...proceed"
                                 );
                } else {
                    //
                    // A config update is already in progress and
                    // didn't expired yet.
                    // the new request will abort.
                    //
                    lobby.severe("There is already a config/update "
                                 + "in progress... abort"
                                 );                          
                    req.setStatus(ConfigChange.FAILURE);
                    req.setErrorCode(ConfigChange.BUSY);
                    req.send(sock);
                    return;
                }
            }   
            configChangeRequest = req;
            configChangeRequest.setSocket(sock);
            configChangeRequest.arm(CMM.CONFIG_UPDATE_TIMEOUT);
        }
        
        lobby.processConfigChange (req);
        lobby.info("Waiting for config change to happen");
    }
    
    /**
     * Ack the current config change.
     * Unblocks CLI and returns proper error
     */
    void ackConfigChange(int errorCode, int requestId) {

        //
        // Copy static variables in local variables
        // There may be a race condition where the request is retried
        // and as we are acknowlegding the first request, the second
        // one comes in, and overwrites those variables.
        //
        ConfigChange msg = configChangeRequest;        
        if (msg == null) {
            lobby.severe("Acking a non-existent server request, requestId = "
                         + requestId + ", aborting...");
            return;
        }

        //
        // If the request does not match, we know the retry has already been
        // started, so we can discard right here.
        //
        if (msg.getRequestId() != requestId) {
            lobby.warning("Discard acknowledgment for request id = " +
                          requestId
                          );
            return;
        }

        //
        // At this point we don't know if there is a retry or not, but
        // we are replying with the old request-- that we just copied,
        // so if this is a retry, there will be no match in the Multiplexor
        // and the request will be discarded at that level.
        //
        if (0 != errorCode) {
            msg.setStatus(ConfigChange.FAILURE);
            msg.setErrorCode(errorCode);
        } else {
            msg.setStatus(ConfigChange.SUCCESS);
        }
        
        lobby.info("Acking config/change, version =  " + msg.getVersion() +
                   ", requestId = " + requestId);
                
        try {
            //
            // Now we need to reset the static config/change request 
            //
            synchronized (this) {
                // We got raced by a 'retry', drop it.
                if (msg != configChangeRequest) {
                    lobby.warning("Discard acknowledgement request " +
                                  " no more valid " + msg);
                    return;
                }
                // Reset static variables and reply
                configChangeRequest = null;
            }
            msg.reply();
            
        } catch (CMMException e) {
            lobby.severe("ApiServer can't send the unblock for config update: " 
                         + msg.getVersion() + " reason " + e
                         );
        }
    }

    /**
     * Main loop of the API/Lobby task.
     * Process all messages from the ring and the clients until
     * the timeout expired.
     */
    void processClients(long timeout) {

        long remaining = timeout;
        long endTime = System.currentTimeMillis() + timeout;

        clients.selectedKeys().clear();
        try {
            while (clients.select(remaining) > 0) {
                Iterator it = clients.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            acceptClient();

                        } else if (key.isReadable()) {                            
                            if (key.attachment() != null) {
                                //
                                // The request comes from the lobby
                                // communication channel, not a client library
                                //
                                dispatchMessage(key);
                            } else {
                                //
                                // the request comes from the client library
                                //
                                processClient(key);
                            }
                        }
                    }
                    it.remove(); 
                }
                remaining = endTime - System.currentTimeMillis();
                if (remaining <= 0L) {
                    break;
                }
            }
        } catch (IOException e) {
            lobby.severe("CMM Api I/O error " + e);
        }
    }
}
