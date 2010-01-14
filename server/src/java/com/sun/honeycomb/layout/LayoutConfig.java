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



package com.sun.honeycomb.layout;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.common.InternalException;
import java.util.logging.Logger;
import java.util.Properties;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.hwprofiles.HardwareProfile;


/** Encapsulates all external info needed by Layout subsystem. */
public class LayoutConfig {

    /** Get Node Manager proxy for local node, cannot return null. */
    static NodeMgrService.Proxy getNodeMgrProxy() {

        // get node manager proxy
        NodeMgrService.Proxy proxy = null;
        proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        // if local NodeMgr unavailable, throw exception
        if (proxy == null) {
            throw new InternalException("local NodeMgr proxy is null");
        }
        return proxy;
    }

    /** Get local node id from NodeMgr */
    static int getLocalNodeId() {

        return getNodeMgrProxy().nodeId();
    }

    /** Determine if the local node is Master. */
    static boolean localNodeIsMaster() {

        NodeMgrService.Proxy proxy = getNodeMgrProxy();
        Node[] nodes = proxy.getNodes();
        for (int i=0; i < nodes.length; i++) {
            if (nodes[i].isMaster()) {
                return nodes[i].nodeId() == proxy.nodeId(); 
            }
        } 
        return false;   // master not found
    }

    /** Accessors */
    static long getGracePeriod() { return gracePeriod; }

    /** Print values of private attributes read from cluster config. */
    static public String configToString() {

        StringBuffer sb = new StringBuffer();
        sb.append(" nodesPerCell=" + NODES_PER_CELL + ", ");
        sb.append(" disksPerNode=" + DISKS_PER_NODE + ", ");
        sb.append(" fragsPerObj=" + FRAGS_PER_OBJ + ", ");
        sb.append(" gracePeriod=" + gracePeriod);

        return sb.toString();
    }


    /** Read cluster config from Cell Manager, do sanity checks. */
    static void getClusterConfig() {

        // read max nodes from node manager, be sure they look sane
        verifyMaxNodes();

        // read cluster_config.properties to get read of config info
        ClusterProperties config = ClusterProperties.getInstance();

        // get number of fragments per object
        if (!config.isDefined (HC_DATA_FRAGS)) {
            throw new InternalException(HC_DATA_FRAGS+" not set");
        }
        int nDataFrags = config.getPropertyAsInt(HC_DATA_FRAGS);

        if (!config.isDefined(HC_PARITY_FRAGS)) {
            throw new InternalException(HC_PARITY_FRAGS+" not set");
        }
        int nParityFrags = config.getPropertyAsInt(HC_PARITY_FRAGS);
        int frags = nDataFrags + nParityFrags;
        if (frags != FRAGS_PER_OBJ) {
            throw new InternalException("expected "+FRAGS_PER_OBJ+
                " fragments per object but read "+frags+
                " from config ("+HC_DATA_FRAGS+"="+nDataFrags+
                " and "+HC_PARITY_FRAGS+"="+nParityFrags+")");
        }

        // get number of disks per node from hardware profile
        HardwareProfile profile = HardwareProfile.getProfile();
        int disks = profile.getNumDisks();
        if (disks != DISKS_PER_NODE) {
            throw new InternalException("expected "+DISKS_PER_NODE+
                " disks per node, but "+HC_DISKS_PER_NODE+"="+disks);
        }

        // get diskmask grace period 
        if (!config.isDefined(HC_GRACE_PERIOD)) {
            throw new InternalException(HC_GRACE_PERIOD+" not set");
        }
        gracePeriod = config.getPropertyAsLong(HC_GRACE_PERIOD);

    }

    /** verify that CMM table matches what layout expects */
    static private void verifyMaxNodes() {

        // verify node table contains correct number of nodes
        NodeMgrService.Proxy nmProxy = getNodeMgrProxy();
        Node[] nodes = nmProxy.getNodes();
        int maxNodes = nodes.length;
        if (maxNodes != NODES_PER_CELL) {
            throw new InternalException("expected "+NODES_PER_CELL+
                    " nodes, but nodeMgr sees "+maxNodes);
        }

        // verify nodeIds are what we expect
        int firstNodeId = nodes[0].nodeId();
        if (firstNodeId != BASE_NODE_ID) {
            throw new InternalException("expected first NodeId to be "
                    +BASE_NODE_ID+", but nodeMgr sees "+firstNodeId);
        }
        for (int i=1; i < NODES_PER_CELL; i++) {
            if (nodes[i].nodeId() != BASE_NODE_ID + i) {
                throw new InternalException("expected ordered NodeIds "+
                    " but nodes["+i+"] NodeId is "+nodes[i].nodeId()+
                    " and not "+(BASE_NODE_ID + i));
            }
        }

    }

    // timeout (in millis) for disk mask, from cluster config
    static private long gracePeriod =  0;   

    /* CONSTANTS */

    // use to read properties
    private static final String HC_DATA_FRAGS = 
        "honeycomb.layout.datafrags";
    private static final String HC_PARITY_FRAGS = 
        "honeycomb.layout.parityfrags";
    private static final String HC_DISKS_PER_NODE =
        "honeycomb.layout.diskspernode";
    private static final String HC_GRACE_PERIOD =
        "honeycomb.layout.diskmask.graceperiod";
                                                 
    // these values cannot be change in the config file, although we do
    // check that the config file does not contradict the values below
    public static final int BASE_NODE_ID = 101;
    public static final int NODES_PER_CELL = 16;
    public static final int DISKS_PER_NODE = 4;
    public static final int FRAGS_PER_OBJ = 7;
    public static final int MAX_NODE_ID = BASE_NODE_ID + NODES_PER_CELL - 1;

    // logger has same name as this class
    private static final Logger LOG =
        Logger.getLogger(LayoutConfig.class.getName());

}



