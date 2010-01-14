
package com.sun.honeycomb.admin.mgmt.server;


import com.sun.honeycomb.admin.mgmt.AdminException;
import org.w3c.dom.Document;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.math.BigInteger;
import java.util.List;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.cm.node_mgr.Node;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.honeycomb.protocol.server.ProtocolProxy;
/**
 * HCNodes handles only the node objects for the cell. 
 * It also incorporates a few calls that pertain to/handle 
 * nodes, such as the number of alive nodes & the master.
 */
public class HCNodesAdapter implements HCNodesAdapterInterface{
    private static transient final Logger logger = 
      Logger.getLogger(HCNodesAdapter.class.getName());

    
    private Node []              nodes; // HC nodes representation can contain nulls
    private int                  numNodes;

    public void loadHCNodes()
        throws InstantiationException{

        try {
            nodes = Utils.getNodes();
        } catch (AdminException ae) {
            logger.log(Level.SEVERE,"Internal cluster errror setting up hccell.", ae);
            throw new InstantiationException("Internal cluster error : " +
              "failed to instantiate the adapater HCCell");
        }
        numNodes = Utils.getNumNodes();
    }
    /*
    * This is the list of accessors to the object
    */
    public void populateNodesList(List<HCNode> array) throws MgmtException {
        for (int i = 0; i < Utils.getNumNodes(); i++) {
            if(nodes[i]!= null) {
                HCNode node = FruCreators.createHCNode(nodes[i]);
                array.add(node);
            } else {
                array.add(FruCreators.createDeadNode(i+101));
            }
        }
    }

    //
    // placeholder for mgmt
    //
    public void setNodesList(List<HCNode> ignore) throws MgmtException {}
    

    public void populateNodeIds(List<BigInteger> array) throws MgmtException {

        for (int i = 0; i < numNodes; i++) {
            array.add(BigInteger.valueOf(Utils.NODE_BASE + i));
        }
    }

    /**
     * Returns the number of nodes running honeycomb in the
     * cell.
     * @return BigInteger the number of nodes running honeycomb in the cell
     * @throws MgmtException
     */
    public BigInteger getNumAliveNodes() throws MgmtException {
        int nbAlive = 0;
        for (int i = 0; i < nodes.length; i++) {
            Node cur = nodes[i];
            if (cur.isAlive()) {
                nbAlive++;
            }
        }
        return BigInteger.valueOf(nbAlive);
    }
    /**
     * Returns the toal number of available disks, across all nodes in a 
     * cell.
     * @return BigInteger number of availbale disks
     * @throws MgmtException
     */
    public BigInteger getNumAliveDisks() throws MgmtException {
        int nbActiveDisks = 0;
        for (int i = 0; i < Utils.getNumNodes(); i++) {
            Node cur = nodes[i];
	    if (cur.isAlive())
		nbActiveDisks += cur.getActiveDiskCount();
        }
        return BigInteger.valueOf(nbActiveDisks);
    }

    /**
     * returns the nodeid of the master node. 
     * will be between 101 and 116.
     * @return BigInteger id of the master node
     * @throws MgmtException
     */
    public BigInteger getMasterNode() throws MgmtException {
        for (int i = 0; i < nodes.length; i++) {
            Node cur = nodes[i];
            if (cur.isMaster()) {
                return BigInteger.valueOf(Utils.NODE_BASE + i);                
            }
        }
        return BigInteger.valueOf(-1);
    }

    /**
     * True if the cell is ready to store and fetch data
     * ie: not in maintenance mode and has quorum.
     * @return String MBybtes of available space adjusted for system use.
     * @throws MgmtException
     */

    public Boolean getProtocolRunning() throws MgmtException {
        boolean res= false;
        try {
            res = Utils.getNodeMgrProxy().hasQuorum();
        } catch (AdminException ae) {
            logger.log(Level.SEVERE,"Internal cluster error getting quorum", ae);
            throw new MgmtException("Internal cluster error : " +
              "failed to get quorum status");
        }



        if (!res) {
            return res;
        }

        boolean running = true;
        for (int i = 0; i < nodes.length && running == true; i++) {
            if (nodes[i].isAlive()) {
                try {
                    ProtocolProxy proxy = 
                      Utils.getProtocolProxy(nodes[i].nodeId());
                    running &= proxy.isAPIReady();
                } catch (Exception e) {
                    logger.info("Protocol not running - exception thrown: " + e);
                    running=false;
                }
            }
        }
        return new Boolean(running);
    }





}


