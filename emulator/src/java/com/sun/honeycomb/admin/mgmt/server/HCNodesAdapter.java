/**
 * $Id: HCCellAdapter.java 9554 2006-10-31 21:45:09Z jr152025 $
 *
 * Copyright 200 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

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


public class HCNodesAdapter implements HCNodesAdapterInterface{
    private static transient final Logger logger = 
      Logger.getLogger(HCNodesAdapter.class.getName());


    public void loadHCNodes()
        throws InstantiationException{

    }
    /*
    * This is the list of accessors to the object
    */
    public void populateNodesList(List<HCNode> array) throws MgmtException {
        array.clear();
        array.addAll( ValuesRepository.getInstance().getNodes().getNodesList());
    }
    //
    // placeholder for mgmt
    //
    public void setNodesList(List<HCNode> ignore) throws MgmtException {}

    public void populateNodeIds(List<BigInteger> array) throws MgmtException {
        array.clear();
        array = ValuesRepository.getInstance().getNodes().getNodeIds();

    }

    public BigInteger getNumAliveNodes() throws MgmtException {
        return ValuesRepository.getInstance().getNodes().getNumAliveNodes();
    }

    public BigInteger getNumAliveDisks() throws MgmtException {
        return ValuesRepository.getInstance().getNodes().getNumAliveDisks();
    }


    public BigInteger getMasterNode() throws MgmtException {
        return ValuesRepository.getInstance().getNodes().getMasterNode();
    }

    public Boolean getProtocolRunning() throws MgmtException {
        return new Boolean(true);
    }






}


