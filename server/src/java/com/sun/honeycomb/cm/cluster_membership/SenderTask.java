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

import java.util.*;
import java.nio.channels.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketAddress;

import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.Commands;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.SoftwareVersion;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Connect;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.ConnectResponse;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Disconnect;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Discovery;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Heartbeat;
import com.sun.honeycomb.cm.cluster_membership.messages.FrameType;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Election;

/**
 * The SenderTask is responsible for connecting the node to the ring 
 * and forwards outgoing traffic. It monitors the health of the node 
 * it is connected to and breaks the current connection if the connected
 * node is not heartbeating. The SenderTask provides a reliable transport 
 * of messages in the ring.
 * The cluster config files are updated when the node connects to the ring.
 * Based on the version (timestamp) number, the cluster always has the most
 * up-to-date config version.
 * The node powers off if it runs the wrong version of the ramdisk.
 */
class SenderTask extends CMMTask {

    static final boolean USE_RETRANSMIT_QUEUE = false;

    private SocketChannel    senderChannel;
    private LinkedList       senderQueue;
    private LinkedList       retransmitQueue;
    private Selector         selector;
    private volatile boolean keepRunning;
    private boolean          receiverConnected;
    private boolean          connectedToBestCandidate;

    SenderTask() {
        
        senderChannel = null;
        senderQueue = new LinkedList();
        selector = null;
        receiverConnected = false;
        keepRunning = false;
        connectedToBestCandidate = false;
        
        if (USE_RETRANSMIT_QUEUE) {
            retransmitQueue = new LinkedList();
        } else {
            retransmitQueue = null;
        }        
    }

    /**
     * API - initialize server
     */
    public synchronized void init() {
        
        reset();
        try {
            selector = Selector.open();
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
        disconnectFromRing(null);
        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                warning("sender I/O error");
            }
            selector = null;
        }
    }

    /**
     * API - return true if this task is running
     */
    public boolean isRunning() {
        return keepRunning;
    }

    /**
     * API - disconnect server
     */
    public void disconnect() {
        /*
         * Put a disconnect message into the message queue as the 1st thing
         * to process. Disconnects will never appear in this queue in any
         * other manner.
         */
        synchronized (senderQueue) {
            senderQueue.addFirst(new Disconnect());
        }
    }

    /**
     * API - tell sender that receiver is connected
     */
    public void receiverIsConnected(boolean value) {
        if (value) {
            info("CMM receiver is connected. Start to emit messages");
        } else {
            info("CMM receiver is disconnected. Stop to emit messages");
        }
        receiverConnected = value;
    }
    
    /**
     * API - queue the message for the senderTask
     */
    public void dispatch(Message msg) {
        
        // ADD CHECK THRESHOLD AND THROW CMMERROR
        // DETECT THREAD BLOCKED ON RESOURCES
        
        Node source = msg.getNodeSource();
        synchronized (senderQueue) {
            if (NodeTable.isLocalNode(source)) {
                if (msg.isFromNetwork()) {
                    /*
                     * cancel message retransmission.
                     * be careful to close all windows.
                     */
                    if ((USE_RETRANSMIT_QUEUE) && 
                        (!retransmitQueue.remove(msg))) 
                    {
                        if (!senderQueue.remove(msg)) {
                            warning("sender dropping " + msg);
                        }
                    }
                    return;
                }
                
                msg.nextFrameId();
            }
            senderQueue.addLast(msg);
            senderQueue.notifyAll();
        }
    }
    
    /*
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
        
        Heartbeat heartbeat = new Heartbeat(CMM.HEARTBEAT_TIMEOUT);
        Heartbeat splitbrain = new Heartbeat(CMM.CONNECT_TIMEOUT);
        boolean failureDetected = false;
        
        while (keepRunning) 
        {
            try {
                if (senderChannel == null) {
                    /*
                     * We are *not* connected to the ring -
                     */
                    if (connectToRing() == false) {
                        severe("sender cannot join the ring");
                        try {
                            Thread.sleep(CMM.CONNECT_TIMEOUT);
                        } catch (InterruptedException ie) {
                            throw new CMMError(ie);
                        }
                        continue;
                    }
                    heartbeat.arm(CMM.HEARTBEAT_TIMEOUT);
                    splitbrain.arm(CMM.CONNECT_TIMEOUT);
                    failureDetected = false;
                    
                } else if (heartbeat.remainingTime() == 0) {
                    /*
                     * Heartbeat timed out - disconnect from the ring.
                     * The connected node failed to send any heartbeat
                     * messages.
                     */
                    if (failureDetected) {
                        Node node = NodeTable.getNode(senderChannel);
                        if (node != null) {
                            logExternal("warn.cm.heartbeat", node.nodeId());
                        }
                        warning("failed to receive heartbeat - restarting");
                        disconnectFromRing(null);
                    } else {
                        failureDetected = true;
                    }

                } else {
                    /*
                     * Happy path -
                     * the node is connected to the ring.
                     */
                    if ((NodeTable.getActiveCount() > (CMM.getNumNodes() / 2)) 
                        || connectedToBestCandidate) {
                        /*
                         * There is a majority of nodes in the ring or
                         * we are connected to the best possible candidate -
                         * re-arm the splitbrain timeout.
                         */
                        splitbrain.arm(CMM.CONNECT_TIMEOUT);
                        
                    } else if (splitbrain.remainingTime() == 0) {
                        /*
                         * Split brain timed out - disconnect from the ring.
                         * The ring does not contain a majority of nodes
                         * and this node is not connected to its best candidate.
                         */
                        warning("split brain timeout - restarting");
                        disconnectFromRing(null);
                        continue;
                    }
                    
                    if (failureDetected) {
                        /*
                         * A wrong failure was detected (hiccup) -
                         * warn and reset the pending failure.
                         */
                        warning("transient failure detected - check load avg");
                        failureDetected = false;
                    }
                    
                    /*
                     * forward as many messages as possible for the 
                     * duration of the given timeout and as long as there
                     * is nothing to read from the connected node.
                     */
                    sendMessages(CMM.HEARTBEAT_INTERVAL);
                }
                
                /*
                 * Process all messages from the connected node -
                 * Note: only limited message types are expected
                 * (heartbeat and disconnect)
                 */
                Message message;
                while ((senderChannel != null) && 
                       ((message = receiveMessage()) != null)) 
                {
                    if (message instanceof Heartbeat) {
                        // heartbeat msg
                        heartbeat.arm(CMM.HEARTBEAT_TIMEOUT);
                    } else if (message instanceof Disconnect) {
                        // Accept the disconnection
                        disconnectFromRing((Disconnect)message);
                    } else {
                        severe("sender unexpected " + message);
                    }
                }
                
            } catch (VersionMismatchException ve) {
                /*
                 * A version mismatch has been detected -
                 * This node does not run the correct software version.
                 * power off or reboot if that fails.
                 */
                info("BAD VERSION - ATTEMPTING POWEROFF.");
                // TODO: Add email sending
                logExternal("err.cm.version.mismatch", NodeTable.getLocalNodeId());
                try {
                    Commands cmds = Commands.getCommands();
                    Exec.exec(cmds.poweroff(), LOG);
                } catch (Exception e) {
                    severe("failed to power off node " + e);
                }
                warning("CANNOT POWEROFF. REBOOTING");
                System.exit(1);
                
            } catch (CMMException e) {
                /*
                 * internal CMM exception - restart the sender task.
                 */
                info("sender restarts ["+e.getMessage()+"]");
                disconnectFromRing(null);

            } catch (IOException e) {
                /*
                 * IO exception - reinit the sender task (including nio).
                 */
                info("sender resets " + e);
                init();
            }
        }
    }

    /**
     * fetch the first pending message to send (from the sender
     * or the retransmit queue) waiting for the duration of the timeout.
     */
    private Message fetchQueue(long timeout) throws CMMException {

        long endTime = System.currentTimeMillis() + timeout;
        try {
            synchronized (senderQueue) {
                if (USE_RETRANSMIT_QUEUE) {
                    Iterator it = retransmitQueue.iterator();
                    while (it.hasNext()) {
                        Message msg = (Message) it.next();
                        if (msg.hasExpired()) {
                            info("CMM retransmits " + msg);
                            return msg;
                        }
                    }
                }
                long remaining = endTime - System.currentTimeMillis();
                while (senderQueue.size() == 0 && remaining > 0L) {
                    senderQueue.wait(remaining);
                    remaining = endTime - System.currentTimeMillis();
                }
                if (senderQueue.size() != 0) {
                    return (Message) senderQueue.getFirst();
                }
            }
        } catch (InterruptedException e) {
            warning("sender thread interrupted");
            throw new CMMException(e);
        }
        return null;
    }

    /**
     * remove the given message from the sender queue and
     * put it on the retransmit queue if this node is the
     * sender.
     */
    private void removeQueue(Message msg) {

        Node node = msg.getNodeSource();
        synchronized (senderQueue) {
            if (senderQueue.remove(msg) == true) {
                if (USE_RETRANSMIT_QUEUE) {
                    if (NodeTable.isLocalNode(node)) {
                        retransmitQueue.addLast(msg); 
                    }
                }
            }
        }
    }

    /**
     * route and forward as many messages as possible 
     * for the duration of the timeout and as long as there is
     * nothing to read from the sender channel
     */
    private void sendMessages(long timeout) throws CMMException {

        long remainingTime = timeout;
        long endTime = System.currentTimeMillis() + timeout;

        SelectionKey key = senderChannel.keyFor(selector);
        Node connected = (Node) key.attachment();

        Message message;
        while ((message = fetchQueue(remainingTime)) != null) {
            /*
             * if a disconnect is pending, handle it and break out of the
             * loop, allowing a reconnect to happen
             */
            if (message instanceof Disconnect) {
                disconnectFromRing(null);
                synchronized (senderQueue) {
                    senderQueue.remove(message);
                    if (USE_RETRANSMIT_QUEUE)
                        retransmitQueue.clear();
                }
                break;
            }
            try {
                /*
                 * check and abort if there is something 
                 * to read from the channel
                 */
                selector.selectedKeys().clear(); 
                int n = selector.selectNow();
                if (n > 0 && key.isReadable()) {
                    break;
                }
            } catch (IOException e) {
                throw new CMMException(e);
            }
            /*
             * forward the message in the ring
             */
            if (message instanceof Discovery) {
                // update notification information on the fly
                ((Discovery) message).updateNodesInfo(connected);
            }
            
            Node source = message.getNodeSource();
            if (NodeTable.compare(source, connected) < 0 ||
                (NodeTable.isLocalNode(connected) && source != connected)) {
                /*
                 * the sender node is not connected anymore -
                 * drop the message otherwise it will be retransmitted
                 * in the ring until the sender node comes back.
                 */
                warning("dropping msg - source not connected " + source.nodeId());
                removeQueue(message);

            } else if ((receiverConnected) && (message.send(senderChannel))) {
                /*
                 * message has been sent - remove it from the queue
                 */
                removeQueue(message);
            }
            remainingTime = endTime - System.currentTimeMillis();
        }
    }

    /**
     * @return the current pending messages from the sender
     * channel.
     */
    private Message receiveMessage() throws CMMException {
        
        SelectionKey key = senderChannel.keyFor(selector);
        try {
            selector.selectedKeys().clear(); 
            int n = selector.selectNow();
            if (n > 0 && key.isReadable()) {
                return Message.receive(senderChannel);
            }
        } catch (IOException e) {
            throw new CMMException(e);
        }
        return null;
    }

    /**
     * Connect to the ring with retry if necessary.
     * This method tries as much as possible to connect to the best
     * valid candidate node in the ring including ourself.
     * @return the socket channel for the connection or 'null' if
     * no connection can be made.
     * @exception VersionMismatchException - the node cannot join the ring
     * due to a software version mismatch
     */
    private SocketChannel connectWithRetry() 	
        throws VersionMismatchException 
    {
        SocketChannel result = null;
        
        /*
         * Build an ordered list of best candidates
         * excluding ourself. The best candidate is the closest 
         * node from us following the ring order.
         */
        ArrayList nodes = new ArrayList();
        Iterator it = NodeTable.iterator();
        while (it.hasNext()) { 
            Node node = (Node) it.next();
            if (node.isLocalNode()) {
                // skip ourselves 
                continue;
            }
            nodes.add(node);
        }

        /*
         * first phase - 
         * try to connect to the best candidate in the list
         * (our closest neighbor)
         */
        boolean failed = false;
        do {
            try {
                result = connect(nodes);
                if (result == null) {
                    failed = true;
                }
            } catch (ConnectionRetry re) {
                // we have to retry
                result = null;
            } catch (ConnectionFailed fe) {
                // give up
                failed = true;
            }
        } while ((result == null) && (!failed));
        
        if (result != null) {
            return result;
        }
        
        /*
         * this fails, second phase -
         * connect to the ring no matter what, 
         * adding ourself at the end of the list and 
         * skipping potential bad nodes
         */
        failed = false;
        nodes.add(NodeTable.getLocalNode());
        do {
            try {
                result = connect(nodes);
                if (result == null) {
                    failed = true;
                }
            } catch (ConnectionRetry re) {
                // we have to retry
                result = null;
            } catch (ConnectionFailed fe) {
                // remove failed node
                nodes.remove(fe.node);
            }
        } while ((result == null) && (!nodes.isEmpty()) && (!failed));

        return result;
    }
    
    /**
     * Try to connect to the best candidate node in the given list.
     * The best candidate is the closest node from us following the
     * ring order. The input list is sorted by that order.
     * @return the socket channel or 'null' if no connection can be made.
     * @exception VersionMismatchException - the node cannot join the ring
     * due to a software version mismatch
     * @exception ConnectionRetry - the attempt should be retry
     * @exception ConnectionFailed - the connection failed to complete
     * with the selected node.
     */
    private SocketChannel connect(List list) 
        throws ConnectionRetry, ConnectionFailed, VersionMismatchException {
            
        ConnectionData[] connections = new ConnectionData[list.size()];
        int bestCandidate = -1;
        Selector connectSelector = null;
        int nbActive = 0;

        try {
            connectSelector = Selector.open();
        } catch (IOException e) {
            severe("Failed to instantiate a selector ["+e.getMessage()+"]");
            return(null);
        }

        /*
         * Phase 1 -  initiate in parallel a connection request 
         * for every node in the list.
         */
        for (int i = 0; i < list.size(); i++) {
            Node node = (Node)list.get(i);
            if (node.isLocalNode()) {
                connections[i] = new ConnectionData(i, node.getInetAddr(), true);
                if (connections[i].sc != null) {
                    if (bestCandidate == -1) {
                        bestCandidate = i;
                    } else {
                        connections[i].disconnect();
                    }
                }
            } else {
                connections[i] = new ConnectionData(i, node.getInetAddr(), false);
                if (connections[i].sc != null)
                    try {
                        connections[i].sc.register(connectSelector,
                                                   SelectionKey.OP_CONNECT,
                                                    connections[i]);
                        nbActive++;
                    } catch (ClosedChannelException e) {
                        warning("Failed to register the connection to ["+
                                connections[i].addr+"] for selection"
                                );
                        connections[i].disconnect();
                    }
            }
        }

        /*
         * Phase 2. Wait for the connections to complete or the best candidate
         * to connect (the first entry in the list) and keep only the best one. 
         */
        long startTime = System.currentTimeMillis();

        while ((nbActive>0) && (bestCandidate != 0) &&
               (System.currentTimeMillis()-startTime<CMM.CONNECT_TIMEOUT)) 
        {
            connectSelector.selectedKeys().clear();
            int nbSelected = 0;
            try {
                long timeout = CMM.CONNECT_TIMEOUT-System.currentTimeMillis()+startTime;
                if (timeout > 0) {
                    nbSelected = connectSelector.select(timeout);
                } else {
                    nbSelected = -1;
                }
            } catch (IOException e) {
                severe("Select failed ["+ e.getMessage()+"]");
                nbSelected = -1;
            }

            if (nbSelected > 0) {
                // Some connects completed
                Iterator keys = connectSelector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = (SelectionKey)keys.next();
                    if (key.isValid() && key.isConnectable()) {
                        ConnectionData conn = (ConnectionData)key.attachment();
                        boolean connected = false;
                        try {
                            connected = conn.sc.finishConnect();
                            if (connected) {
                                if ((bestCandidate == -1) || 
                                    (conn.index < bestCandidate)) 
                                {
                                    // We have a better candidate
                                    if (bestCandidate >=0)
                                        connections[bestCandidate].disconnect();
                                    bestCandidate = conn.index;
                                } else {
                                    conn.disconnect();
                                }
                            } else {
                                warning("Failed to connect to ["+conn.addr+"]");
                                conn.disconnect();
                            }                            
                        } catch (IOException e) {
                            warning("Failed to connect to ["+conn.addr+"]");
                            conn.disconnect();
                        } finally {
                            nbActive--;
                            conn.completed = true;
                            key.cancel();
                        }
                    }
                }
            }
        }

        try { connectSelector.close(); } catch (IOException e) {}

        /*
         * Phase 3. Check and close the unanswering connections
         */
        for (int i=0; i<connections.length; i++) {
            if (!connections[i].completed) {
                if (bestCandidate != 0) {
                    warning("Didn't get any answer from ["+connections[i].addr+"]");
                }
                connections[i].disconnect();
            }
        }
                
        if (bestCandidate == -1) {
            // no connection possible with any node in the list.
            return(null);
        }

        /*
         * Phase 4. Finish the connection with the best candidate
         * Throw the appropriate Connection exception (retry or failed)
         * if the connection cannot be completed (cannot return null at
         * this point).
         */
        ConnectionData conn = connections[bestCandidate];
        SocketChannel result = null;
        SelectionKey key = null;
	        
        try {
            assert(conn != null);
            conn.connect();
            key = conn.sc.register(selector, SelectionKey.OP_READ); 
            selector.selectedKeys().clear();
            int n = selector.select(CMM.CONNECT_TIMEOUT);
            if (n > 0 && key.isReadable()) {
                if (!handleConnectResponse(conn.sc)) {
                    /*
                     * the connection should be retry
                     */
                    throw new ConnectionRetry(bestCandidate, "retry");
                }
            } else {
                /*
                 * timeout waiting for a reply from the selected candidate
                 */
                throw new ConnectionFailed(bestCandidate, "timeout");
            }
            result = conn.sc;
            
        } catch (IOException ioe) {
            String msg = "i/o error during connection " + ioe;
            warning(msg);
            throw new ConnectionFailed(bestCandidate, msg);
            
        } catch (CMMException cme) {
            String msg = "connection failed due to " + cme;
            warning(msg);
            throw new ConnectionFailed(bestCandidate, msg);
            
        } finally {
            if (result == null) {
                /*
                 * this is an exception path -
                 * the connection failed or must be retry
                 */
                if (key != null) {
                    key.cancel();
                }
                conn.disconnect();
                warning("Failed to complete the connection to ["+conn.addr+"]");

            } else {
                /*
                 * We succeeded to connect to the selected candidate.
                 */
                connectedToBestCandidate = (bestCandidate == 0);
                info("Managed to connect to my "+bestCandidate+" ["+conn.addr+"]");
            }
        }
        assert (result != null);
        return(result);
    }
    
    /**
     * Validate the connection with the given node.
     * @return true if the connection is validated, throw an exception
     * if the connection cannot be completed.
     * @exception CMMException - an error ocurred during the connection
     * @exception VersionMismatchException - the node cannot join the ring
     * due to a software version mismatch
     */
    private boolean handleConnectResponse (SocketChannel sc) 
        throws VersionMismatchException, CMMException {

        Message message;
        while ((message = Message.receive (sc, 10)) != null) {
            if (message instanceof ConnectResponse) {
                /*
                 * We get a connect response -
                 * First check and fetch the config files. The node
                 * must have completed the config update phase
                 * before attempting to check for the software release.
                 * 
                 */
                ConnectResponse response = (ConnectResponse) message;                
                if ((response.getResponse() & ConnectResponse.CFG_MISMATCH) != 0) {
                    /* 
                     * If there's a config mismatch, fetch the right 
                     * config files before returning.
                     */
                    LobbyTask lobby = CMM.lobbyTask();
                    if (lobby != null) {
                        if (lobby.fetchConfigFiles(response)) {
                            return false;
                        }
                    }
                    throw new CMMException("failed to fetch " + response);
                    
                } else if ((response.getResponse() == ConnectResponse.CONNECT_OK) || 
                           ((response.getResponse() & ConnectResponse.SW_MISMATCH) != 0)) {
                    /*
                     * Config versions are up to date.
                     * Always check local ramdisk version against 
                     * config version to make sure we're running the right
                     * version.  If not, throw VersionMismatchException so
                     * node will be powered off by work loop.
                     */                    
                    if (!SoftwareVersion.checkVersionMatch()) {
                        String cver = SoftwareVersion.getConfigVersion();
                        String rver = SoftwareVersion.getConfigVersion();
                        throw new VersionMismatchException(cver, rver);
                    }
                    
                    /* If version check passes, proceed */
                    return true;
                    
                } else {
                    /*
                     * A node is trying to connect with a different 
                     * protocol - should never happen
                     */
                    severe("sender unexpected "+ message);
                }
            }
        }
        /*
         * We failed to receive any reply from our open request.
         */
        throw new CMMException("failed to receive a reply");
    }
    
    /**
     * Connect to the ring.
     * Go through the ordered list of nodes as given by the NodeTable 
     * until we find one node we can connect to.
     * @return true if the node is connected, false otherwise.
     */
    private boolean connectToRing() 
        throws IOException, CMMException, VersionMismatchException {
            
        disconnectFromRing(null);

        // Try to connect to the ring
        senderChannel = connectWithRetry();
        if (senderChannel == null) {
            // Give up ...
            return false;
        }

        Node node = NodeTable.getNode(senderChannel);
        if (node == null) {
            throw new CMMError("internal error unknown node");
        }
        int nodeid = node.nodeId();
        info("sender connected to " + nodeid);
        int linger = CMM.HEARTBEAT_TIMEOUT / 1000; // in seconds
        senderChannel.socket().setSoLinger(true, linger);
        //senderChannel.socket().setKeepAlive(true);
        senderChannel.socket().setTcpNoDelay(true);
        senderChannel.register(selector, 
                                SelectionKey.OP_READ | SelectionKey.OP_WRITE, 
                                node);
        Discovery discovery = new Discovery();
        discovery.updateLocalNodeStatus();
        dispatch(discovery);

        //senderQueue.addLast (new Discovery());
        return true;
    }

    /**
     * Disconnect from the ring.
     */
    private void disconnectFromRing(Disconnect reply) {
        
        if (senderChannel != null) {
            Node node = null;
            SelectionKey key = senderChannel.keyFor(selector);
            if (key != null) {
                node = (Node) key.attachment();
                if (node != null) {
                    info("sender disconnecting from " +node.nodeId());
                } else {
                    info("sender disconnecting from [UNKNOWN NODE]");
                }
                key.cancel();
            } else {
                throw new CMMError("sender connected to unknown host");
            }

            // Send the message
            Disconnect msg = (reply==null) ? new Disconnect() : reply;
            try {
                msg.send(senderChannel);
            } catch (CMMException e) {}

//             if (USE_RETRANSMIT_QUEUE)
//                 retransmitQueue.clear();

            if (reply == null) {
                // NYI
                // We expect a reply
//                 try {
//                     Message m = Message.receive(senderChannel);
//                     if (!(m instanceof Disconnect)) {
//                         severe("Protocol error. Didn't get a disconnect message");
//                     }
//                 } catch (CMMException e) {
//                     severe("Protocol error. ["+e.getMessage()+"]");
//                 }
            }                    
            
            try {
                senderChannel.close();
            } catch (IOException ignored) {}
            senderChannel = null;
            connectedToBestCandidate = false;
        }
    }
    
    /**
     * wrapper class around a network connection with a node in the ring.
     */
    private static class ConnectionData {
        public SocketAddress addr;
        public int index;
        public SocketChannel sc;
        public boolean completed;
        
        public ConnectionData(int _index, SocketAddress dest, boolean isLocal) {
            addr = dest;
            index = _index;
            try {
                sc = SocketChannel.open();
                if (!isLocal) {
                    sc.configureBlocking(false);
                    sc.connect(addr);
                    completed = false;
                } else {
                    sc.connect(addr);
                    completed = true;
                    sc.configureBlocking(false);
                }
            } catch (IOException e) {
                if (sc != null)
                    try { sc.close(); } catch (IOException ignored) {}
                sc = null;
                completed = true;
            }
        }
        
        public void connect() throws CMMException {
            if (sc != null) {
                new Connect().send(sc);
            } else {
                throw new CMMException("not connected");
            }
        }
        
        public void disconnect() {
            if (sc != null) {
                if (completed)
                    try { new Disconnect().send(sc); } catch (CMMException e) {}
                try { sc.close(); } catch (IOException e) {}
            }
            sc = null;
            completed = true;
        }
        
        public boolean isClosed() {
            return((sc==null) || (!sc.isConnectionPending() && !sc.isConnected()));
        }
    }
    
    /**
     * indicate a connection should be retry
     */
    private static class ConnectionRetry extends Exception {
        int node;
        public ConnectionRetry(int node, String msg) {
            super(msg);
            this.node = node;
        }
    }
    
    /**
     * indicate a connection failed to complete
     */
    private static class ConnectionFailed extends Exception {
        int node;
        public ConnectionFailed(int node, String msg) {
            super(msg);
            this.node = node;
        }
    }
}
