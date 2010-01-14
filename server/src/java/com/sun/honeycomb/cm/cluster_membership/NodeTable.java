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

import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.logging.Logger;
import com.sun.honeycomb.config.ClusterProperties;


/**
 * The NodeTable class provides an ordered view
 * of all the nodes in the ring.
 */
public final class NodeTable {

    private static final Logger logger = Logger.getLogger(NodeTable.class.getName());

    public static boolean testMode = false;
    private static Node[] nodes;
    private static int localId = -1;

    /**
     * Build the nodes table from the cell configuration.
     * The table is statically defined and must be indentical
     * for every nodes.
     */
    private static void parseNodes(final String config) {

        if (config == null) {
            throw new CMMError("config is missing");
        }
        String snodes[] = config.split(",", CMM.MAX_NODES);
        int idx = 0;
        nodes = new Node[snodes.length];

        for (int i = 0; i < snodes.length; i++) {
            String cnode[] = snodes[i].trim().split("\\s++");
            if (cnode.length != 3) {
                String error = "bad node entry " + i + " line " + snodes[i];
                throw new CMMError(error);
            }
            try {
                nodes[idx++] = new Node(
                                        i,
                                        Integer.parseInt(cnode[0]),
                                        cnode[1],
                                        CMM.RING_PORT,
                                        Boolean.valueOf(cnode[2]).booleanValue()
                                        );
            } catch (Exception e) {
                String error = "bad format entry " + e.getMessage();
                throw new CMMError(error);
            }
        }

        // verify that this node is part of the list
        boolean found = false;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId() == localId) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new CMMError("Local node \"" + localId +
                               "\" not found in Nodes Table " + nodes);
        }
        // sort the nodes table by clockwise distance from local node.
        for (int i = 0; i < nodes.length - 1; i++) {
            for (int j = nodes.length - 1; j > i; j--) {
                if (compareNodes(nodes[j - 1], nodes[j]) > 0) {
                    Node tmp = nodes[j];
                    nodes[j] = nodes[j - 1];
                    nodes[j - 1] = tmp;
                }
            }
        }
    }

    /**
     * Return the relative order of the two given nodes in terms
     * of distance from the local node.
     */
    private static int compareNodes(final Node a, final Node b) {

        int nodeA = a.nodeId();
        int nodeB = b.nodeId();

        if (nodeA == nodeB) {
            return 0;
        } else if (nodeA < localId && nodeB >= localId) {
            return 1;
        } else if (nodeA >= localId && nodeB < localId) {
            return -1;
        } else if (nodeA < nodeB) {
            return -1;
        } else {
            return 1;
        }
    }

    /**
     * @return the relative order of the two given nodes
     */
    static public int compare(final Node a, final Node b) {

        if (isLocalNode(a) || isLocalNode(b)) {
            return 0;
        }
        return compareNodes(a, b);
    }

    /**
     * @return the local node
     */
    static public Node getLocalNode() {
        return nodes[0];
    }

    /**
     * @return the node corresponding to the given node id
     */
    static public Node getNode(int nodeid) {
        if(null==nodes)
            return null;
        for (int i = 0; i < nodes.length; i++) {
            if(null==nodes[i]) {
                logger.severe("Null entry in nodes - this shoudln't be.");
            }else{
                if (nodes[i].nodeId() == nodeid) {
                    return nodes[i];
                }
            }
        }
        return null;
    }


    /**
     * @return the node corresponding to the given hostname
     */
    static public Node getNode(InetSocketAddress addr) {
        String hostname = addr.getHostName();
        int port = -1;

        if (testMode) {
            port = addr.getPort();
            if ((port < CMM.RING_PORT) || (port > CMM.RING_PORT+32)) {
                // In test mode, we cannot decide who is the source
                // (all CMMs run on the same node)
                return(null);
            }
        }

        for (int i = 0; i < nodes.length; i++) {
            if ( ((port == -1) && (nodes[i].getHost().equals(hostname)))
                 || ((port != -1) && (nodes[i].getInetAddr().equals(addr))) ) {
                return nodes[i];
            }
        }

        return null;
    }

    /**
     * @return the node corresponding to the given socket
     * channel
     */
    static public Node getNode(SocketChannel sc) {

        InetSocketAddress addr = (InetSocketAddress)
            sc.socket().getRemoteSocketAddress();
        if (addr != null) {
            return getNode(addr);
        }
        return null;
    }

    /**
     * @return the local node id
     */
    static public int getLocalNodeId() {
        return localId;
    }

    /**
     * * @return the current master node
     */
    static public Node getMasterNode() {

        for(int i = 0; i < nodes.length; i++) {
            if (nodes[i].isMaster()) {
                return nodes[i];
            }
        }
        return null;
    }

    /**
     * @return the current vice master node
     */
    static public Node getViceMasterNode() {

        for(int i = 0; i < nodes.length; i++) {
            if (nodes[i].isViceMaster()) {
                return nodes[i];
            }
        }
        return null;
    }

    /**
     * @return an iterator for the nodes
     */
    static public Iterator iterator() {
        return Arrays.asList(nodes).iterator();
    }

    /**
     * @return the number of nodes in the table
     */
    static public int getCount() {
        return nodes.length;
    }

    /**
     * @return the number of alive nodes in the table.
     */
    static public int getActiveCount() {
        int count = 0;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isAlive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * calculate the number of active disks in the system
     */
    static public int getActiveDiskCount() {
        int count = 0;
        int numNodes = CMM.getNumNodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].isAlive() && nodes[i].nodeIndex < numNodes) {
                count += nodes[i].getActiveDiskCount();
            }
        }
        return count;
    }

    /**
     * @return true if the give node is the local one
     */
    static public boolean isLocalNode(Node node) {
        return node.nodeId() == localId;
    }

    /**
     * reinitialize the nodes table.
     */
    static void reset() {
        for (int i = 0; i < nodes.length; i++) {
            nodes[i].setAlive(false);
        }
    }

    static String toLogString() {
        StringBuffer sb = new StringBuffer("[ ");
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];

            if (node == null || !node.isAlive()) {
                continue;
            }

            if (node.isEligible()) {
                sb.append("*");
            }
            sb.append(node.nodeId());
            sb.append (":").append(nodes[i].getActiveDiskCount());
            if (node.isMaster()) {
                sb.append(" MASTER");
            }
            if (node.isViceMaster()) {
                sb.append(" VICEMASTER");
            }
            sb.append (" ");
        }
        sb.append(" ] Disks: " + getActiveDiskCount());
        return sb.toString();
    }

    /**
     * Initialize the nodes table
     */
    public static void init(int nodeid, String config) {
        localId = nodeid;

        if (config != null) {
            NodeTable.parseNodes(config);
        } else {
            // CMM Test Mode.
            String cmmtype = ClusterProperties.getInstance().getProperty("cmm.test.type");
            if (cmmtype == null) {
                throw new RuntimeException("Config is null, but cmm.test.type missing for test run.");
            }

            if (ClusterProperties.getInstance().getProperty("cmm.test.type").equals("0")) {
                buildTestNodesForSingleMode();
            } else if (ClusterProperties.getInstance().getProperty("cmm.test.type").equals("1")) {
                buildTestNodesForDistMode();
            }

            getLocalNode().setAlive(true);
        }
    }

    /***************************************************************************
     *
     * The following code is for a test purpose ONLY
     *
     **************************************************************************/

    private static boolean[] sniffTable(int NB_NODES) {
        boolean[] res = new boolean[NB_NODES];
        for (int i=0; i<res.length; i++) {
            res[i] = false;
        }

        ClusterProperties props = ClusterProperties.getInstance();
        String sniffConfig = props.getProperty("cmm.test.sniffed");
        if (sniffConfig == null) {
            return(res);
        }

        String[] nodes = sniffConfig.split(",");
        for (int i=0; i<nodes.length; i++) {
            try {
                int pos = Integer.parseInt(nodes[i])-101;
                res[pos] = true;
            } catch (NumberFormatException ignored) {
                ignored.printStackTrace();
            }
        }

        return(res);
    }

    private static void buildTestNodesForSingleMode() {

        logger.warning("The node table has been built "
                       + "in a test mode environment [local id "+localId+"]"
                       );
        testMode = true;

        int NB_NODES = 16;
        nodes = new Node[NB_NODES];
        int idx = NB_NODES+101-localId;
        boolean[] sniffed = sniffTable(NB_NODES);

        for (int i=0; i<nodes.length; i++) {
            if (idx == NB_NODES) {
                idx = 0;
            }
            nodes[idx] = new Node(i, 101+i, "127.0.0.1", CMM.RING_PORT+2*i,
                                  true);
            if (sniffed[i]) {
                nodes[idx].setSniffedFlag();
                logger.info("Node ["+nodes[idx].nodeId()+
                            "] is put is sniff mode");
            }

            idx++;
        }
    }

    private static void buildTestNodesForDistMode() {

        logger.warning("The node table has been built "
                       + "in a test mode environment [local id "+localId+"]"
                       );
        int NB_NODES = 16;
        nodes = new Node[NB_NODES];
        int idx = NB_NODES+101-localId;
        boolean[] sniffed = sniffTable(NB_NODES);

        for (int i=0; i<nodes.length; i++) {
            if (idx == NB_NODES) {
                idx = 0;
            }
            nodes[idx] = new Node(i, 101+i, "10.123.45."+(101+i), CMM.RING_PORT+2*i,
                                  true);
            if (sniffed[i]) {
                nodes[idx].setSniffedFlag();
                logger.info("Node ["+nodes[idx].nodeId()+
                            "] is put is sniff mode");
            }

            idx++;
        }
    }
}
