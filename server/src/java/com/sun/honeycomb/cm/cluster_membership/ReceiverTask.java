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

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.util.Iterator;
import java.io.IOException;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Heartbeat;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Connect;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.ConnectResponse;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Disconnect;


/**
 * The ReceiverTask is responsible for accepting new nodes
 * in the ring and for handling incoming message. It forwards
 * all messages to the LobbyTask for local processing and 
 * periodically send an heartbeat to its connected client to
 * maintain its membership in the ring.
 */
class ReceiverTask extends CMMTask { 
    
    private LobbyTask lobbyClient;
    private SenderTask sender;
    private ServerSocketChannel serverChannel;
    private SocketChannel nodeSocket;
    private Selector selector;
    private volatile boolean keepRunning;
    private volatile boolean rqstToClose;

    ReceiverTask(LobbyTask lobby, SenderTask _sender) {
        lobbyClient = lobby;
        sender = _sender;
        selector = null;
        nodeSocket = null;
        serverChannel = null;
        keepRunning = false;
        rqstToClose = false;
    }

    /**
     * API - initialize server.
     */
    public synchronized void init() {
        reset();
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            ServerSocket sock = serverChannel.socket();
            sock.setReuseAddress(true);
            sock.bind(NodeTable.getLocalNode().getAddrToBind(), CMM.MAX_NODES);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
        } catch (IOException e) {
            throw new CMMError(e);
        }
        keepRunning = true;
    }

    /**
     * API - reset server internal state
     */
    public synchronized void reset() {

        keepRunning = false;
        boolean error = false;

        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (IOException x) {
                error = true;
            }
            serverChannel = null;
        }
        if (nodeSocket != null) {
            try {
                nodeSocket.close();
            } catch (IOException x) {
                error = true;
            }
            nodeSocket = null;
        }
        if (selector != null) {
            try {
                Iterator it = selector.keys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();
                    try {
                        if (key.isValid()) {
                            key.channel().close();
                            key.cancel();
                        }
                    } catch (Exception e) {
                        warning("receiver got " + e);
                    }
                }
                selector.close();
            } catch (IOException x) {
                error = true;
            }
            selector = null;
        }
        if (error) {
            severe("receiver I/O error");
        }
    }

    /**
     * API - close the receiver socket
     */
    public void close() {
        rqstToClose = true;
    }
    
    /**
     * API - return true if this task is running
     */
    public boolean isRunning() {
        return keepRunning;
    }

    /**
     * API - dispatch message
     */
    public void dispatch(Message msg) {
        if (msg instanceof Disconnect) {
            closeClient(false);
        } else {
            lobbyClient.dispatch(msg);
        }
    }

    /**
     * Task entry point 
     */
    public void run() { 

        init();
        try {
            work();
        } finally {
            reset();
        }
    }

    /**
     * Main server loop
     */
    private void work() {

        Heartbeat heartbeat = new Heartbeat(CMM.HEARTBEAT_INTERVAL);
        while (keepRunning) {
            try {
                if (rqstToClose) {
                    closeClient(true);
                    rqstToClose = false;
                }
                long remaining = heartbeat.retransmitIfExpired(nodeSocket);
                selector.selectedKeys().clear();
                if (selector.select(remaining) > 0) {
                    processSelector();
                }
                
            } catch (CMMException e) {
                warning("receiver resets - " + e);
                closeClient(false);

            } catch (IOException e) {
                severe("receiver restarts - " + e);
                init();
            }
        }
    }

    /**
     * disconnect from the ring
     */
    private synchronized void closeClient(boolean flush) {

        if (nodeSocket != null) {
            Node node = null;
            SelectionKey key = nodeSocket.keyFor(selector);
            if (key != null) {
                node = (Node) key.attachment();
                if (node != null) {
                    info("disconnecting from node " + node.nodeId());
                } else {
                    info("disconnecting from an unidentified node");
                }
                key.cancel();
            }

            if (flush) {
                /*
                 * We need to play the synchronous disconnect 
                 * protocol not to loose message
                 */
                Selector closeSelector = null;
                try {
                    new Disconnect().send(nodeSocket);
                    Message msg = null;
                    boolean done = false;
                    int err;

                    closeSelector = Selector.open();
                    nodeSocket.register(closeSelector, SelectionKey.OP_READ);
                    long startTime = System.currentTimeMillis();

                    while (!done) {
                        long remaining = startTime + 2 * CMM.HEARTBEAT_INTERVAL
                            - System.currentTimeMillis();

                        if (remaining > 0) {
                            err = closeSelector.select(remaining);
                        } else {
                            err = -1;
                        }

                        if (err == -1) {
                            done = true;
                        } else if (err == 0) {
                            closeSelector.close();
                            closeSelector = null;
                            closeSelector = Selector.open();
                            nodeSocket.register(closeSelector, SelectionKey.OP_READ);
                        } else {
                            msg = Message.receive(nodeSocket);
                            if (msg instanceof Disconnect) {
                                done = true;
                            } else {
                                dispatch(msg);
                            }
                        }
                    }
                } catch (CMMException ignored) {
                    
                } catch (IOException e) {
                    severe("Failed to flush socket ["+e.getMessage()+"]");
                    
                } finally {
                    if (closeSelector != null)
                        try {closeSelector.close();} catch (IOException e) {}
                }
            }
            try {
                Disconnect msg = new Disconnect();
                msg.send(nodeSocket);
                nodeSocket.close();
            } catch (IOException e) {
                severe("receiver I/O error");
            } catch (CMMException e) {}
        
            nodeSocket = null;
            sender.receiverIsConnected(false);
        }
    }

    /**
     * A node wants to connect to this node.
     */
    private void acceptClient() throws IOException, CMMException {

        SocketChannel sock = serverChannel.accept();
        if (sock == null) {
            return;
        }
        Node node = NodeTable.getNode(sock);
        if ((node == null) && (!NodeTable.testMode)) {
            severe("unknown host " + sock + " tries to connect to the ring");
            sock.close();
            return;
        }
        int linger = CMM.HEARTBEAT_TIMEOUT / 1000; // in seconds
        sock.socket().setSoLinger(true, linger);
        // sock.socket().setKeepAlive(true);
        sock.socket().setTcpNoDelay(true);
        sock.configureBlocking(false);
        sock.register(selector, SelectionKey.OP_READ, node);
    }

    /**
     * return the current connected Node
     */
    private Node connectedNode() {
        Node connected = null;
        if (nodeSocket != null) {
            SelectionKey key = nodeSocket.keyFor(selector);
            if (key != null) {
                connected = (Node) key.attachment();
            }
        }
        return connected;
    }
       
    /**
     * Handle a connection request.
     * The current protocol always accepts a valid connection request
     * regardless who is connecting (even if the current connected 
     * node is a better candidate). This guarantees that we don't 
     * stay connected to a "dead" node. The algorithm must eventually 
     * finish to establish the ring taking into account all active 
     * nodes.
     * @return true if the connection is accepted.
     */
    private boolean handleConnectRequest(Connect connect, 
                                         SocketChannel sc, 
                                         Node node)
        throws CMMException
    {        
        ConnectResponse response = new ConnectResponse(connect);
        response.send(sc);

        if (response.getResponse() == ConnectResponse.CONNECT_OK) {
            /*
             * the connection request is valid (same config/vers) - 
             * accept the connection.
             */
            closeClient(true);
            nodeSocket = sc;
            new Heartbeat(0).send(nodeSocket);
            if (node != null) {
                int nodeid = node.nodeId();
                info("Config match - receiver connected to " + nodeid);
            } else {
                warning("receiver connected to an unidentified node");
            }                        
            sender.receiverIsConnected(true);            
            return true;
            
        } else if ((response.getResponse() & response.CFG_MISMATCH) != 0) {
            /*
             * config mismatch -
             * Disconnect the current connected node 
             */
            info("receiver refused connection to " 
                 + ((node == null)? "unknown node":node.nodeId())
                 + " due to config mismatch " + response.toString()
                 );
            closeClient(true);
            
        } else {
            /*
             * we don't agree with the connect request -
             * close the connection.
             */
            info("receiver refused connection to " 
                 + ((node == null)? "unknown node":node.nodeId())
                 + " due to version mismatch" + response.toString()
                 );
        }
        return false;
    }
        
    /**
     * process pending message on the given channel.
     */
    private void processMessage(SelectionKey key) throws CMMException {

        SocketChannel sc = (SocketChannel) key.channel();
        Node node = (Node) key.attachment();

        if (sc != nodeSocket) {
            /*
             * this is a message from an unknown node.
             * we allow only connect or disconnect messages.
             */
            boolean closeSocket = true;
            try {
                Message msg = Message.receive(sc);
                if (msg instanceof Disconnect) {
                    /*
                     * End of disconnect protocol -
                     * close the socket.
                     */
                    if (node != null) {
                        int nodeid = node.nodeId();
                        fine("close " + nodeid);
                    } else {
                        fine("close - Unidentified node" );
                    }
                } else if (msg instanceof Connect) {
                    /*
                     * This is a connection message -
                     * build and send the response back and accept
                     * the connection if it is valid.
                     */
                    Connect connect = (Connect) msg;
                    if (handleConnectRequest(connect, sc, node)) {
                        closeSocket = false;
                    }
                }
            } catch (CMMException e) {
                fine("failed to complete connection protocol " + e);

            } finally {
                if (closeSocket) {
                    key.cancel();
                    try { 
                        sc.socket().shutdownOutput();
                        sc.close(); 
                    } catch (IOException x) {
                        severe("I/O error" + x);
                    }
                }
            }        
        } else {
            /*
             * this is a message from the current connected node.
             * Close the connection if the node wants to disconnect,
             * otherwise forward the message to the lobby task.
             */
            Message msg = Message.receive(nodeSocket);
            if (msg instanceof Disconnect) {
                throw new CMMException("Disconnecting");
            }
            msg.isFromNetwork (true);
            dispatch(msg);
        }
    }

    /**
     * process pending events
     */
    private void processSelector() throws CMMException, IOException {

        Iterator it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = (SelectionKey) it.next();
            if (key.isValid()) {
                if (key.isReadable()) {
                    // a message is pending -
                    processMessage(key);
                } else if (key.isAcceptable()) {
                    // a connection request is pending -
                    acceptClient();
                }
            }
            it.remove();
        }
    }
}
