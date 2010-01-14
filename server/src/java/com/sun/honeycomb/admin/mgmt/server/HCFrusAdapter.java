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



package com.sun.honeycomb.admin.mgmt.server;

import org.w3c.dom.Document;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.math.BigInteger;
import java.util.List;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.admin.mgmt.Utils;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.honeycomb.util.ServiceProcessor;
import com.sun.honeycomb.admin.mgmt.AdminException;
/**
 * HCFrus returns ALL the frus in the system
 * including the switches and the cheat.
 * This is very slow, because querying cheat & switches
 * implicitly causes the platform layer to ssh to each
 * item and pull it's status. Unless there's a really
 * good reason, use the HCNodes and HCDisks adaptors instead.
 */
public class HCFrusAdapter implements HCFrusAdapterInterface {
    private static transient final Logger logger = 
        Logger.getLogger(HCFrusAdapter.class.getName());


    public void loadHCFrus()
        throws InstantiationException {
    }


    public void populateFrusList(List<HCFru>  array) throws MgmtException {

        try {           
            Node []  nodes = Utils.getNodes();
            for (int i = 0; i < Utils.getNumNodes(); i++) {
                Node cur = nodes[i];
                if(nodes[i] != null) {                    
                    HCNode node = FruCreators.createHCNode(nodes[i]);
                    array.add(node);

                    int numDisks=Utils.getDisksPerNodes();
                    Disk [] disks = Utils.getDisksOnNode(Utils.NODE_BASE + i);
                    for (int j = 0; j < Utils.getDisksPerNodes(); j++) {
                        Disk curDisk=null;
                        if(null != disks) {
                            curDisk=disks[j];
                        }
                        HCDisk disk = FruCreators.createHCDisk(curDisk,Utils.NODE_BASE+i,j);
                        array.add(disk);
                    }
                } else {
                    array.add(FruCreators.createDeadNode(i+101));
                }
            }
            
            // Add the switch fru's
            HCSwitch[] switches = HCSwitchAdapter.getSwitchFrus();
            for (int i=0; i < switches.length; i++) {
                array.add(switches[i]);
            }
            
            // Add the service processor fru information
            array.add(ServiceProcessor.getSPFru());
        } catch (AdminException e) {
            logger.severe("Cannot populate frus."+e);
            //
            // Internationalize here
            //
            throw new MgmtException ("Unable to fetch fru information.");
        }
    }
    //
    // Placeholder for mgmt
    //
    public void setFrusList(List<HCFru> ignore) throws MgmtException {}

}
