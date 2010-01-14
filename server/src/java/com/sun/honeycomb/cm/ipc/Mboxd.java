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




package com.sun.honeycomb.cm.ipc;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.Service;
import java.util.logging.Logger;
import java.nio.channels.DatagramChannel;
import java.net.InetSocketAddress;
import java.util.List;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.nio.ByteBuffer;
import java.net.MulticastSocket;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.Observer;
import java.util.Observable;


public class Mboxd implements MboxdService, Observer {

    private static final Logger logger = 
        Logger.getLogger(Mboxd.class.getName());

    // tunables
    public static final int PUBLISH_INTERVAL = 5000; // 5s
    public static final int PUBLISH_POLL = 1000;     // 1s
    public static final int MONITOR_PERIOD = 10;     // 10x1s
    public static final int MAX_ERRORS = 10; // give up after 10 consecutive errors

    static private volatile boolean isRunning;

    private final int localnode;
    private final MboxReceiver receiver;
    private Thread thr = null;

    /**
     * default constructor called by cluster management
     */
    public Mboxd() throws ManagedServiceException, IOException {
        NodeMgrService.Proxy nodemgr;
        nodemgr = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        localnode = nodemgr.nodeId();
        receiver = new MboxReceiver();
        /*
         * Register for CMM notifications
         */
        try {
            ServiceManager.register(ServiceManager.CMM_EVENT, this);
        } catch (Exception e) {
            throw new ManagedServiceException(e);
        }
        logger.info("ClusterMgmt - service has been started");
        isRunning = true;
    }

    /**
     * entry point called by cluster management.
     */

    public void syncRun() {
    }

    public void run() {
        logger.info("ClusterMgmt - service is running ");
        thr = Thread.currentThread();
        receiver.start();
        int monitor = 0;
        int errors = 0;
        while (isRunning) {
            try {
                int delay;
                if (monitor > 0) {
                    monitor--;
                    delay = PUBLISH_POLL;
                } else {
                    delay = PUBLISH_INTERVAL;
                }
                Thread.currentThread().sleep(delay);
                syncMailboxes();
                errors = 0;

            } catch (InterruptedException ie) {
                // Mboxd is interrupted every time it needs
                // to publish at a higher rate
                monitor = MONITOR_PERIOD;

            } catch (IOException ioe) {
                // Failed to publish the mailboxes - 
                // this can be a transient lack of resources condition,
                // so we don't want to give up right away
                logger.severe("ClusterMgmt - failed to publish mailboxes " + 
                              errors + " times " + ioe
                              );
                if (++errors > MAX_ERRORS) {
                    logger.severe("ClusterMgmt - too many errors, giving up");
                    break;
                }
            }
        }
        logger.info("ClusterMgmt - service exits");
    }

    /**
     * Thread to receive multicast mailboxes
     */
    static private class MboxReceiver extends Thread {

        private MulticastSocket sock;
        private DatagramPacket packet;
        
        private MboxReceiver() throws IOException {
            InetAddress group = InetAddress.getByName(Mailbox.MBOXD_GROUP);
            sock = new MulticastSocket(Mailbox.MBOXD_PORT);
            sock.joinGroup(group);
            byte[] buf = new byte[Mailbox.MBOX_MAXSIZE];
            packet = new DatagramPacket(buf, buf.length);
            new Thread(this, "ClusterMgmt-MULTICAST");
        }

        public void run() {
            logger.info("ClusterMgmt - Multicast receiver running");
            while (isRunning) {
                try {
                    sock.receive(packet);
                    Mailbox.copyout(packet.getData(), packet.getLength());
                } catch (IOException ioe) {
                    logger.severe("ClusterMgmt - Multicast IO error " + ioe);
                    throw new RuntimeException(ioe);
                }
            }
        }
    }

    /**
     * shutdown this service - called by cluster management.
     */
    synchronized public void shutdown() {
        if (isRunning) {
            isRunning = false;
            receiver.interrupt();
        }
    }

    /**
     * this service has no proxy (yet).
     */
    public ManagedService.ProxyObject getProxy() {
        return null;
    }

    /**
     * Process CMM notification.
     */
    public void update(Observable obj, Object arg) {
        if (thr != null) {
            thr.interrupt();
        }
    }

    /*
     * Remote API
     */
    
    public byte[] getMailbox(String tag) throws ManagedServiceException {
        return Mailbox.copyin(tag);
    }


    /*
     * internal
     */

    private void syncMailboxes() throws IOException {
        NodeMgrService.Proxy nodemgr;
        nodemgr = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        Node[] nodes = nodemgr.getNodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() == localnode) {
                continue;
            }
            if (!nodes[i].isAlive()) {
                Mailbox.disable_node(nodes[i].nodeId());
            } else if (!syncNode(nodes[i].nodeId())) {
                ServiceManager.publish(this);
            }
        }
        Mailbox.publish();
    }

    private boolean syncNode(int nodeid) {
        ManagedService.ProxyObject obj;
        obj = ServiceManager.proxyFor(nodeid, Mboxd.class);
        if (obj == null) {
            logger.warning("ClusterMgmt - node "  + nodeid +
                           " is up but service Mboxd not running yet");
            return false;
        }
        MboxdService api;
        if (!(obj.getAPI() instanceof MboxdService)) {
            logger.warning("ClusterMgmt - node "  + nodeid +
                           " is up but service Mbox not accessible yet");
            return false;
        }
        api = (MboxdService) obj.getAPI();

        NodeMgrService.Proxy nodeproxy;
        nodeproxy = ServiceManager.proxyFor(nodeid);
        if (nodeproxy == null) {
            logger.info("ClusterMgmt - node manager mailbox not yet " +
                        "available for node " + nodeid + " - fetching it");
            try {
                Mailbox.initIPC (nodeid, 0);
                byte[] buf = api.getMailbox(NodeMgrService.mboxTag);
                Mailbox.copyout(buf, buf.length);
                nodeproxy = ServiceManager.proxyFor(nodeid);
            } catch (Exception e) {
                nodeproxy = null;
            }
        }
        if (nodeproxy == null) {
            logger.warning("ClusterMgmt - cannot sync up node manager " 
                           + " mailbox on " + nodeid);
            return false;
        }
        Service[] services = nodeproxy.getServices();
        for (int i = 0; i < services.length; i++) {
            if (!services[i].isManaged()) {
                continue;
            }
            if (!services[i].isRunning()) {
                continue;
            }
            if (!Mailbox.exists(services[i].getTag())) {
                logger.info("ClusterMgmt - syncing mailbox " 
                            + services[i].getName()
                            + " from node " + nodeproxy.nodeId());
                try {
                    byte[] buf = api.getMailbox(services[i].getTag());
                    Mailbox.copyout(buf, buf.length);
                } catch (Exception e) {
                    logger.warning("ClusterMgmt - cannot sync mailbox " +
                                   services[i].getTag());
                }
            }
        }
        return true;
    }
}
