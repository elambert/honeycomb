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
import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.nio.channels.Pipe;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChangeNotif;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.DiskChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ClusterInfo;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Election;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Discovery;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Notification;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Commit;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Update;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.ConnectResponse;


/**
 * The lobby task is responsible for updating the view
 * of the nodes table and handling the API aspect of CMM.
 * It receives messages from the receiver task, process
 * them locally and forward them to the senderTask.
 * It is the only thread manipulating the nodes table.
 * it implements the election protocol and maintains
 * the ring configuration.
 */
public class LobbyTask extends CMMTask {

    private final SenderTask senderTask;
    private Pipe msgQueue;
    private Pipe apiQueue;
    private final ApiServer api;
    private final CfgUpdUtil cfgUpdUtil;
    private volatile boolean keepRunning;
    private long lastMasterApplication;
    private long lastVicemasterApplication;
    private long lastDiscoveryReceived;
    private boolean hasQuorum;
    

    LobbyTask(SenderTask sender) {
        senderTask = sender;
        api = new ApiServer(this);
        cfgUpdUtil = CfgUpdUtil.getInstance();
        lastMasterApplication = 0;
        lastVicemasterApplication = 0;
        lastDiscoveryReceived = 0;

        msgQueue = null;
        apiQueue = null;

        try {
            msgQueue = Pipe.open();
            msgQueue.source().configureBlocking(false);
        } catch (IOException e) {
            throw new CMMError("Failed to initialize the msgQueue ["+
                               e.getMessage()+"]");
        }
        try {
            apiQueue = Pipe.open();
            apiQueue.source().configureBlocking(false);
        } catch (IOException e) {
            throw new CMMError("Failed to initialize the apiQueue ["+
                               e.getMessage()+"]");
        }

        // the api module is created only once and 
        // need to stay intact during a CMM restart.
        api.init();

        // default to false
        hasQuorum = false;
    }

    public Pipe.SourceChannel getApiSourceChannel() {
        return(apiQueue.source());
    }

    public Pipe.SourceChannel getMsgSourceChannel() {
        return(msgQueue.source());
    }

    public void init() {
        keepRunning = true;
    }

    public void reset() {
        keepRunning = false;
    }

    public boolean isRunning() { 
        return keepRunning; 
    }

    public void run() {

        init();
        try {
            work();
        } finally {
            reset();
        }
    }

    /**
     * Main task loop
     */
    private void work() {

        long logTimeout = System.currentTimeMillis() + CMM.LOGGING_INTERVAL;
        long purgeTimeout = System.currentTimeMillis() + CMM.PURGE_INTERVAL;
        long lastHeartbeat = 0;
        
        cfgUpdUtil.purgeAllConfigs();
        
        while (keepRunning) {
            /*
             * Process msg received - api + ring
             */
            api.processClients(CMM.latencyTimeout());
            
            /*
             * Transmit a discovery message periodically from the
             * master node. This will take care of recovering inconsistent
             * state in the ring and provide an internal heartbeat mechanism.
             */
            if (NodeTable.getLocalNode().isMaster()) {
                Discovery discovery = new Discovery();
                discovery.updateAllNodesStatus();
                senderTask.dispatch (discovery);
            }
            
            /*
             * Heartbeat locally to CMM as long as we receive
             * discovery messages.
             */
            if (lastHeartbeat != lastDiscoveryReceived) {
                CMM.heartbeat();
                lastHeartbeat = lastDiscoveryReceived;
            }
                
            /*
             * Purge periodically config update files.
             */
            if (purgeTimeout - System.currentTimeMillis() <= 0) {
                cfgUpdUtil.purgeAllConfigs();
                purgeTimeout = System.currentTimeMillis() + CMM.PURGE_INTERVAL;
            }
            
            /*
             * Log periodically CMM state
             */
            if (logTimeout - System.currentTimeMillis() <= 0) {
                info(NodeTable.toLogString() + " - Quorum: " + hasQuorum);
                logTimeout = System.currentTimeMillis() + CMM.LOGGING_INTERVAL;
            }
            
        }
    }

    /**
     * Dispatch the message in the lobby message queue.
     * Note: this method in invoked in the context of the calling thread
     * (not the lobby thread).
     */
    public void dispatch(Message message) {
        Node source = message.getNodeSource();
        int frameId = message.getFrameId();

        if (SenderTask.USE_RETRANSMIT_QUEUE) {
            /*
             * In case of retransmission,
             * we don't want to process multiple times the same
             * message.
             */            
            if (frameId - source.getLastFrameId() > 0) {
                source.setLastFrameId(frameId);
                putQueue(msgQueue, message);
            } else {
                info("message discarded " + message);
                senderTask.dispatch(message);
            }
            
        } else {
            /*
             * In case of non-retransmission, we do drop message
             * so we want to process all messages.
             * In particular, we may have missed Node info join/leave msgs.
             */
            if ((frameId - source.getLastFrameId()) <= 0) {
                warning("received out-of-order message frameId " + 
                        source.getLastFrameId() + " > msg " + message
                        );
            }
            source.setLastFrameId(frameId);
            putQueue(msgQueue, message);
        }                        
    }


    /**
     * The following method is called by the ApiServer when there an
     * available message in the msg queue.
     * This is therefore invoked in the context of the lobby thread.
     * NOT TO BE CONFUSED WITH dispatch()
     */
    public void dealWithMessage(Message msg) {

        fine("lobby process: " + msg.toString());
        
        if (msg instanceof Discovery) {
            // ring configuration message
            processDiscovery((Discovery) msg);
        } else if (msg instanceof Election) {
            // election message
            processElection((Election) msg);
        } else if (msg instanceof Notification) {
            // node notification
            processNotification((Notification) msg);
        } else if (msg instanceof Update) {
            // config update
            processUpdate((Update) msg);
        } else if (msg instanceof Commit) {
            // config commit
            processCommit((Commit) msg);
        }
        else {
            severe("unknown message " + msg);
        }
    }

    /**
     * Process API Node Change request -.
     */
    public void processNodeChange(NodeChange msg) {

        Node local = NodeTable.getLocalNode();
        Notification notif;

        switch (msg.getCause()) {

            case NodeChange.NODE_ELIGIBLE:
                if (!local.isEligible()) {
                    local.setEligible(true);
                    notif = new Notification(Notification.NODE_ELIGIBLE);
                    senderTask.dispatch(notif);

                    if (NodeTable.getMasterNode() == null) {
                        applyForOffice(Election.MASTER);
                    }
                }
                break;

            case NodeChange.NODE_INELIGIBLE:
                if (local.isEligible()) {
                    Node master = NodeTable.getMasterNode();
                    Node vmaster = NodeTable.getViceMasterNode();
                    local.setEligible(false);
                    notif = new Notification(Notification.NODE_INELIGIBLE);
                    senderTask.dispatch(notif);

                    if (master == local) {
                        setOffice(Election.MASTER, local, false, true);
                        applyForOffice(Election.MASTER);
                    }
                    if (vmaster == local) {
                        setOffice(Election.VICEMASTER, local, false, true);
                        applyForOffice(Election.VICEMASTER);
                    }
                }
                break;

            default:
                warning("received " + msg);
                msg.setReply(NodeChange.CMD_ERROR);
                return;
        }
        msg.setReply(NodeChange.CMD_OK);
    }

    /**
     * Check that the node is a valid candidate and trigger
     * an election for the given office.
     */
    private void applyForOffice(int office) {

        boolean accepted = true;
        switch (office) {
        case Election.MASTER:
            if ((lastMasterApplication>0) && 
                (System.currentTimeMillis()-lastMasterApplication<1000)) 
            {
                accepted = false;
            } else {
                lastMasterApplication = System.currentTimeMillis();
            }
            break;

        case Election.VICEMASTER:
            if ((lastVicemasterApplication>0) && 
                (System.currentTimeMillis()-lastVicemasterApplication<1000)) 
            {
                accepted = false;
            } else {
                lastVicemasterApplication = System.currentTimeMillis();
            }
            break;
        }
        
        if (!accepted) {
            return;
        }
        senderTask.dispatch(new Election(office));
    }
    
    /**
     * Update current view of master/vicemaster and queue
     * API notification.
     * can trigger Election -
     */
    private void setOffice(int office, Node node, boolean elected,
                           boolean launchElection) {
        
        StringBuffer sb = new StringBuffer("node " + node.nodeId());
        int state = -1;
        
        switch (office) {
            case Election.MASTER:
                if (elected) {
                    state = NodeChange.MASTER_ELECTED;
                    if (node.isViceMaster()) {
                        node.setViceMaster(false);
                    }
                    sb.append(" is new master");
                } else {
                    state = NodeChange.MASTER_DEMOTED;
                    sb.append(" lost master office");
                }
                node.setMaster(elected);
                break;
                
            case Election.VICEMASTER:
                if (elected) {
                    state = NodeChange.VICEMASTER_ELECTED;
                    sb.append(" is new vicemaster");
                } else {
                    state = NodeChange.VICEMASTER_DEMOTED;
                    sb.append(" lost vicemaster office");
                }
                node.setViceMaster(elected);
                break;
        }
        
        putQueue(apiQueue, new NodeChange(node, state));
        info(sb.toString());
        
        if (launchElection) {
            Node local = NodeTable.getLocalNode();
            if (NodeTable.getMasterNode() == null) {
                applyForOffice(Election.MASTER);
            } else if (NodeTable.getViceMasterNode() == null) {
                if (!local.isMaster()) {
                    applyForOffice(Election.VICEMASTER);
                }
            }
        }
    }
    
    /**
     * Discover who is master and vicemaster in the ring
     * and trigger the appropriate election or recover action.
     */
    private void checkOffice(int office, Discovery message) {
        Node curr = null;
        Node node = null;
        String label = null;
        
        switch (office) {
            case Election.MASTER:
                curr = NodeTable.getMasterNode();
                node = message.getMasterNode();
                label = "master";
                break;
                
            case Election.VICEMASTER:
                curr = NodeTable.getViceMasterNode();
                node = message.getViceMasterNode();
                label = "vicemaster";
                break;
                
            default:
                severe("Unknown office [" + office +
                       "]. Cannot properly process DISCOVERY message"
                       );
                return;
        }
        
        if (node == null) {
            if (NodeTable.isLocalNode(message.getNodeSource()) && 
                NodeTable.getLocalNode().isEligible()) 
            {
                // No one took office. I am candidate !
                applyForOffice(office);
            }
        } else {
            if (curr == null) {
                // Welcome the newly appointed node
                setOffice(office, node, true, true);
                
            } else if (node.nodeId() != curr.nodeId()) {
                /*
                 * What happened ???
                 * There is multiple master/vicemaster nodes -
                 * Resolve the conflict as follow:
                 * - if the local node is not in conflict, follow
                 * what the discovery msg says.
                 * - otherwise give up the office if the elected node
                 * from the message is a better candidate.
                 */
                severe("RECOVERY: I learnt that there is a different " +
                       label + " from a discovery message. [" +
                       curr.nodeId() + " -> " + node.nodeId()+"]"
                       );                
                if (NodeTable.isLocalNode(curr) == false ||
                    node.nodeId() < curr.nodeId())
                {
                    setOffice(office, curr, false, false);
                    setOffice(office, node, true, true);
                }
            }
        }
    }
        
    /**
     * process Election message -.
     * The current design relies on a global order between nodes
     * to elect the correct candidate.
     * CAUTION: the protocol can only rely on a partial order 
     * between messages in the ring. In particular there is
     * no order between Election, Notification and Discovery 
     * messages.
     */
    private void processElection(Election msg) {
        
        boolean resendMessage = true;
        try {
            if ( msg.isRequested() && 
                 ( (msg.getNodeSource().nodeId() == NodeTable.getLocalNodeId())
                   || (msg.getCandidate() == null) ) ) {
                if (msg.isMasterOffice()) {
                    lastMasterApplication = 0;
                } else {
                    lastVicemasterApplication = 0;
                }
            }
            if (msg.isCanceled()) {
                /*
                 * This is a useless message. Forward it to the sender queue to
                 * potentially remove it from the retransmit queue.
                 */
                return;
            }

            Node candidate = msg.getCandidate();
            Node local = NodeTable.getLocalNode();
            Node curr;
            String label = null;
            int office = -1;
            
            if (msg.isMasterOffice()) {
                curr = NodeTable.getMasterNode();
                label = "master";
                office = Election.MASTER;
            } else {
                curr = NodeTable.getViceMasterNode();
                label = "vicemaster";
                office = Election.VICEMASTER;
            }

            if (msg.isNotification()) {
                /*
                 * This is the notification of an election -
                 * update local view of master/vice master.
                 */
                if ((curr == null) || (curr.nodeId() != candidate.nodeId())) {
                    if (curr != null) {
                        // We already had a node ...
                        setOffice(office, curr, false, false);
                    }
                    setOffice(office, candidate, true, true);
                }
                return;
            }

            /*
             * From now on, this is an election request.
             * We have to play the protocol
             */
            if ((candidate == null) && (!msg.getNodeSource().equals(local))) {
                // Someone failed to take office. I become candidate
                info("Got an election request for " + label
                     + " with no candidate. I am now running for "+ label
                     );
                applyForOffice(office);
                return;
            }            
            
            if (msg.getNodeSource().nodeId() == local.nodeId()) {
                resendMessage = false;
                if (candidate == null) {
                    return;
                }
                if (candidate.nodeId() != local.nodeId()) {
                    severe("Inconsistent election message !!! Source is "
                           + msg.getNodeSource().nodeId()+" and candidate is "
                           +candidate.nodeId()
                           );
                    return;
                }

                // I am the sender !! I got elected
                if ((curr == null) || (curr.nodeId() != local.nodeId())) {
                    // I didn't already know it

                    if ((office == Election.VICEMASTER) && (local.isMaster())) {
                        // I have been elected vicemaster but I am already master ...
                        info("I am already master. "
                             + "Cannot be vicemaster also. Give up the office ..."
                             );
                        Election newElection = new Election(office);
                        newElection.removeCandidate();
                        senderTask.dispatch(newElection);
                        return;
                    }

                    if ((office == Election.MASTER) && (local.isViceMaster())) {
                        info("I just got elected master. "
                             + "I give up my vicemaster office"
                             );
                        Election newElection = new Election(Election.VICEMASTER);
                        newElection.removeCandidate();
                        senderTask.dispatch(newElection);
                    }
                    
                    if (curr != null) {
                        setOffice(office, curr, false, false);
                    }
                    setOffice(office, local, true, true);

                    // Send the notification
                    senderTask.dispatch(new Election(local, office));
                }
                return;
            }

            if ((curr != null) && (curr.nodeId() == local.nodeId())) {
                // I already hold the office. Cancel the election
                info("I am already "+ label + 
                     ". Cancelling the request from "+ candidate.nodeId()
                     );
                msg.setCancel(true);
                return;
            }
            
            /*
             * This is the election protocol -
             * Check if I am a better candidate and take the appropriate
             * action if so.
             */
            if (!local.isEligible()) {
                // I cannot participate
                return;
            }

            if (local.nodeId() < candidate.nodeId()) {
                boolean iambetter = true;
                if ((office == Election.MASTER) && (candidate.isViceMaster())) {
                    iambetter = false;
                }
                if (iambetter && (office == Election.VICEMASTER) && (local.isMaster())) {
                    // I am already master ...
                    iambetter = false;
                }
                if (iambetter) {
                    // I am better
                    info("I am a better candidate than "+
                         candidate.nodeId()+" for "+label+". I am now running"
                         );
                    msg.setCancel(true);
                    applyForOffice(office);
                    return;
                }
            }

            if ((office == Election.MASTER) && (local.isViceMaster())) {
                info("I am vicemaster. " + 
                     "I am running for master and cancel the request from " +
                     candidate.nodeId()
                     );
                msg.setCancel(true);
                applyForOffice(office);
                return;
            }

        } finally {
            if (resendMessage) {
                /*
                 * Forward the updated current election.
                 */
                senderTask.dispatch(msg);
            }
        }
    }
    
    /**
     * Ring configuration message -.
     * Update the nodes table and queue node change notifications
     * for API callbacks.  This function can trigger new elections.
     */
    private void processDiscovery(Discovery message) {

        lastDiscoveryReceived = System.currentTimeMillis();        
        /*
         * process list of active nodes.
         */
        NodeChange notif;
        Iterator it = message.getAliveNodes();
        while (it.hasNext()) {
            Node node = (Node) it.next();
            if (!node.isAlive()) {
                node.setAlive(true);
                notif = new NodeChange(node, NodeChange.MEMBER_JOINED);
                putQueue(apiQueue, notif);
            }
            node.setActiveDiskCount(message.getDisksCount(node));
        }

        /*
         * process list of dead nodes -
         * update master/vicemaster offices and queue
         * API notifications.
         */
        it = message.getDeadNodes();
        while (it.hasNext()) {
            Node node = (Node) it.next();
            if (node.isLocalNode()) {
                severe("trying to disable local node");
            } else if (node.isAlive()) {
                if (node.isMaster()) {
                    setOffice(Election.MASTER, node, false, true);
                } else if (node.isViceMaster()) {
                    setOffice(Election.VICEMASTER, node, false, true);
                }
                node.setAlive(false);
                notif = new NodeChange(node, NodeChange.MEMBER_LEFT);
                putQueue(apiQueue, notif);
            }
        }

        /*
         * if CMM is configured for single mode, allow the
         * node to form a ring only with itself. Otherwise,
         * a ring is at least 2 nodes.
         */
        if (message.getNodeSource().isLocalNode() && 
            NodeTable.getActiveCount() == 1 && 
            !CMM.isSingleMode()) 
        {
            warning("CMM node is alone -");
            Node node = NodeTable.getLocalNode();
            if (node.isMaster()) {
                setOffice(Election.MASTER, node, false, true);
            }
            if (node.isViceMaster()) {
                setOffice(Election.VICEMASTER, node, false, true);
            }
            senderTask.disconnect();
            Thread.yield();
            return;
        }
        
        // Check the consistency of the master node
        checkOffice(Election.MASTER, message);
                
        // Check the consistency of the vicemaster node
        checkOffice(Election.VICEMASTER, message);
        
        /*
         * Lastly, we need to determine if we have disk quorum and take the
         * appropriate action (via a notification) 
         */        
        Node local = NodeTable.getLocalNode();
        NodeChange nodechange;
        if (CMM.doQuorumCheck()) {
            int activeDisks = NodeTable.getActiveDiskCount();
            if (hasQuorum && activeDisks < CMM.getMinDiskNum()) {
                fine("Lost Quorum! " + activeDisks);
                hasQuorum = false;
                nodechange = new NodeChange (local, NodeChange.LOST_QUORUM);
                putQueue (apiQueue, nodechange);

            } else if (!hasQuorum && activeDisks >= CMM.getMinDiskNum()) {
                fine("Gained Quorum! " + activeDisks);
                hasQuorum = true;
                nodechange = new NodeChange (local, NodeChange.GAINED_QUORUM);
                putQueue (apiQueue, nodechange);
            }
        } else {
            /* 
             * If we're doing quorum checking, generate an initial NodeChange
             * and pretend that we have quorum 
             */
            if (hasQuorum == false) {
                hasQuorum = true;
                nodechange = new NodeChange (local, NodeChange.GAINED_QUORUM);
                putQueue (apiQueue, nodechange);
            }
        }

        /*
         * forward the current discovery
         */
        message.updateLocalNodeStatus();
        senderTask.dispatch(message);
    }

    /**
     * Node Notification message -.
     * the state of a node in the ring changed. Update its
     * current view.
     */
    private void processNotification(Notification msg) {

        Node source = msg.getNodeSource();
        switch (msg.getCause()) {

            case Notification.NODE_ELIGIBLE:
                source.setEligible(true);
                break;

            case Notification.NODE_INELIGIBLE:
                if (source == NodeTable.getMasterNode()) {
                    setOffice(Election.MASTER, source, false, true);
                } 
                if (source == NodeTable.getViceMasterNode()) {
                    setOffice(Election.VICEMASTER, source, false, true);
                }
                source.setEligible(false);
                break;

            default:
                warning("unknown msg " + msg);
                break;
        }
        senderTask.dispatch(msg);
    }

    /**
     * Handle the config update from the client
     */
    public void processConfigChange(ConfigChange msg) {
        
        Update update = new Update (msg);

        if (msg.clearMode()) {
            CMMApi.ConfigFile cfg;
            cfg = CMMApi.ConfigFile.lookup(msg.getFileToUpdate());
            cfgUpdUtil.wipeConfig(cfg, update.getVersion());
        }
        // LUDO CHECK MASTER NODE
        info("Start config/update");
        senderTask.dispatch (update);
        msg.setStatus (ConfigChange.SUCCESS);
    }
    
    /**
     * Handle disk changes from the client
     */
    public void processDiskChange (DiskChange msg) {
        
        // update the local state...
        Node n = NodeTable.getNode (msg.nodeId());
        n.setActiveDiskCount(msg.getActiveDisks());
        fine("Disk change trigged change " + NodeTable.toLogString());
        // Send a new Discovery message so that everyone gets the new disk
        // state
        Discovery discovery = new Discovery();
        discovery.updateLocalNodeStatus();
        senderTask.dispatch (discovery);
    }
    
    /**
     * Update the cluster info message from the client.
     */
    public void updateClusterInfo (ClusterInfo msg) {
        msg.setQuorum((CMM.doQuorumCheck()? hasQuorum:true));
    }
    
    /**
     * Process the update message. this results in a new config file 
     * being copied and the message being reforwarded with an ack from this
     * node
     *
     * (This has been broken into parts to allow intrusive CMM stress tests
     *  to fake failures) 
     */
    public void processUpdate(Update msg) {
        
        Node local = NodeTable.getLocalNode();
        Node source = msg.getNodeSource();

        if (msg.isFromNetwork() && (source.nodeId() == local.nodeId())) {
            processUpdateOnMasterOnly(msg);
            return;
        }
        if (msg.isFromNetwork()) {
            processUpdateNonMaster(msg);
            return;
        }
        throw new CMMError("Invalid path in processUpdate");
    }


    protected void processUpdateOnMasterOnly(Update msg) {

        Node local = NodeTable.getLocalNode();
        /*
         * We've gone all the way around the ring, if everything is okay
         * we need to commit the request
         */
        if (msg.isValid()) {
            if (msg.clearMode()) {
                // This is a request to clear the config - No commit phase
                api.ackConfigChange(0, msg.getRequestId());
                putQueue (apiQueue, 
                  new ConfigChangeNotif (local,
                    ConfigChangeNotif.CONFIG_UPDATED,
                    msg.getFileToUpdate()));
            } else {
                info("update successful, send commit");
                Commit commit = new Commit (msg);
                senderTask.dispatch (commit);
            }
        } else {
            /*
             * Failure during fetchProperty
             */
            warning("update failed, message = " + msg);
            api.ackConfigChange(ConfigChange.DIST_FAILURE, msg.getRequestId());
            putQueue (apiQueue, new ConfigChangeNotif (local,
                ConfigChangeNotif.CONFIG_FAILED,
                msg.getFileToUpdate()));
        }
    }

    protected void processUpdateNonMaster(Update msg) {
        boolean success =  doActionOnUpdateNonMaster(msg);
        setStatusOnUpdateAndSendNext(msg, success);
    }


    protected boolean doActionOnUpdateNonMaster(Update msg) {
        
        long version = msg.getVersion();
        boolean success = false;
        CMMApi.ConfigFile cfg = CMMApi.ConfigFile.lookup(msg.getFileToUpdate());
        
        if (msg.clearMode()) {
            success = cfgUpdUtil.wipeConfig(cfg, version);
        } else {
            byte[] content = msg.getContent();
            if (content != null) {
                success = cfgUpdUtil.writeConfigFile(cfg, version, content);
            }
            if (!success) {
                Node src = NodeTable.getNode(msg.getMaster());
                success = cfgUpdUtil.fetchConfigFile(src, cfg, version);
            }
        }
        return success;
    }

    protected void setStatusOnUpdateAndSendNext(Update msg, boolean success) {

        Node local = NodeTable.getLocalNode();

        if (true == success) {
            msg.ack ((byte) local.nodeId());
            if (msg.clearMode()) {
                putQueue(apiQueue, 
                  new ConfigChangeNotif (local,
                    ConfigChangeNotif.CONFIG_UPDATED,
                    msg.getFileToUpdate()));
            }
        } else {
            msg.nack ((byte) local.nodeId());
            putQueue (apiQueue, 
              new ConfigChangeNotif (local,
                ConfigChangeNotif.CONFIG_FAILED,
                msg.getFileToUpdate()));
        }   
        senderTask.dispatch (msg);
    }

    /**
     * Process the commit message.
     * (This virtual method calls the real method so we can
     *  modify the behavior with intrusive test that overload
     *  this method)
     */
    protected void processCommit(Commit msg) {

        CMMApi.ConfigFile cfg = CMMApi.ConfigFile.lookup(msg.getFileToUpdate());
        boolean success = cfgUpdUtil.activate(cfg, msg.getVersion());
        
        fine("Making config. " + msg.getVersion()
             + " for "+cfg+" active"
             );
        setStatusOnCommitAndSendNext(msg, success);
    }

    protected void setStatusOnCommitAndSendNext(Commit msg, boolean success) {

        Node local = NodeTable.getLocalNode();
        Node source = msg.getNodeSource();

        if (success) {
            msg.ack ((byte) local.nodeId());
            putQueue (apiQueue, 
              new ConfigChangeNotif (local,
                ConfigChangeNotif.CONFIG_UPDATED,
                msg.getFileToUpdate()));
        } else {
            msg.nack ((byte) local.nodeId());
            putQueue (apiQueue, 
              new ConfigChangeNotif (local,
                ConfigChangeNotif.CONFIG_FAILED,
                msg.getFileToUpdate()));
        }
 
        if (msg.isFromNetwork() && (source.nodeId() == local.nodeId())) {
            /*
             * we've gone all the way around the ring, so drop the message
             *
             *  Add the sending of the config change message to the
             *      API queue. 
             *  This will unblock the CLI.
             *  Add error codes, if required.
             */
            info("config/update went through, " +
                 " message is " + (msg.isValid() ? "valid" : "not valid" )
                 );
            if (msg.isValid()) {
                api.ackConfigChange(0, msg.getRequestId());

            } else {
                api.ackConfigChange(ConfigChange.NO_LINK, msg.getRequestId());
            }
            return;
        }
        senderTask.dispatch (msg);
    }

    /**
     * Fetch the appropriate config files from the response of
     * a connection request.
     * Note - call from the sender thread.
     * @return true if some config files have been fetched.
     */
    protected boolean fetchConfigFiles(ConnectResponse msg) {
        
        if ((msg.getResponse() & ConnectResponse.CFG_MISMATCH) == 0) {
            warning("config mismatch not set " + msg);
            return false;
        }        
        List updatedFiles = cfgUpdUtil.fetchConfigFiles(msg);
        if (updatedFiles.size() == 0) {
            return false;
        }
        
        for (int i = 0; i < updatedFiles.size(); i++) {
            CMMApi.ConfigFile cfg = (CMMApi.ConfigFile) updatedFiles.get(i);
            
            // send the config notif.
            ConfigChangeNotif notif = new ConfigChangeNotif
                    (NodeTable.getLocalNode(), 
                     ConfigChangeNotif.CONFIG_UPDATED,
                     cfg.val()
                     );
            putQueue(apiQueue, notif);
            info("configuration file " + cfg + " updated");
        }
        
        ReceiverTask receiver = CMM.receiverTask();
        if (receiver != null) {
            receiver.close();
        }        
        return true;
    }            
        
    /*
     * Queue helpers
     */
    private void putQueue(Pipe queue, Message message) {
        synchronized (queue) {
            try {
                message.send(queue.sink());
            } catch (CMMException e) {
                warning("Failed to push a message in a queue ["+message+"]");
            }
        }
    }
}
