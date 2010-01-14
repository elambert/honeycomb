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
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.cm.node_mgr.Node;
import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertException;


//
// codecs - encode/decode support
//
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;


public class FruCreators {
    private static final Logger logger =
        Logger.getLogger(FruCreators.class.getName());


    public static HCDisk createHCDisk(Disk input, int node, int diskNum) {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        HCDisk hcDisk = new HCDisk();
        if(null== input) {

            hcDisk.setNodeId(BigInteger.valueOf(node));
            hcDisk.setDevice("");
            hcDisk.setPath("");
            hcDisk.setMode("ABNORMAL");
            hcDisk.setTotalCapacity(-1);
            hcDisk.setUsedCapacity(-1);
                
            hcDisk.setStatus(BigInteger.valueOf(0));                                              
            hcDisk.setFruType(BigInteger.valueOf(0));               
            try {
                hcDisk.setFruId(codec.encode(""));
            } catch(EncoderException e) {
                logger.log(Level.INFO,
                           "failed to encode null string " , e);
                hcDisk.setFruId("unavailable");
            }
            String diskId="DISK-"+node+":"+diskNum;
            hcDisk.setFruName(diskId);
            hcDisk.setDiskId(diskId);
        } else {
            hcDisk.setNodeId(BigInteger.valueOf(input.nodeId()));
            hcDisk.setDevice(input.getDevice());
            hcDisk.setPath(input.getPath());
            hcDisk.setMode(input.getModeString());
            hcDisk.setTotalCapacity(input.getDiskSize());
            hcDisk.setUsedCapacity(input.getUsedSize());

            hcDisk.setStatus(BigInteger.valueOf(input.getStatus()));


            //            String[] type= {"DISK","MOBO","PWRSPLY","FAN","SWITCH"};
            // defined in MOF file
            // FIXME - move to a better place

            hcDisk.setFruType(BigInteger.valueOf(0));
            //
            // fruIDs are carrying trailing spaces
            //

            String fruId=input.getSerialNo();
            fruId=fruId.trim();           

            try {
                hcDisk.setFruId(codec.encode(fruId));
            } catch(EncoderException e) {
                logger.log(Level.INFO,
                           "failed to encode string " + fruId, e);
                hcDisk.setFruId("unavailable");
            }

            String diskId="DISK-"+input.nodeId()+":"+input.diskIndex();
            hcDisk.setFruName(diskId);
            hcDisk.setDiskId(diskId);
        }
        return hcDisk;
    }

 
    public static HCNode createHCNode(Node input) {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        HCNode hcNode = new HCNode();        
        hcNode.setHostname(input.getName());
        hcNode.setIsAlive(input.isAlive());
        hcNode.setIsEligible(input.isEligible());
        hcNode.setIsMaster(input.isMaster());
        hcNode.setIsViceMaster(input.isViceMaster());
        hcNode.setDiskCount(BigInteger.valueOf(input.getActiveDiskCount()));
        if(input.isOff()) {
            hcNode.setStatus(BigInteger.valueOf(CliConstants.HCFRU_STATUS_OFFLINE));
        }else if(input.isAlive()) {
            hcNode.setStatus(BigInteger.valueOf(CliConstants.HCFRU_STATUS_ENABLED));
        } else if (!input.isAlive()) {
            hcNode.setStatus(BigInteger.valueOf(CliConstants.HCFRU_STATUS_DISABLED));
        }
                             

        hcNode.setFruType(BigInteger.valueOf(CliConstants.HCFRU_TYPE_NODE));
        int nodeId=input.nodeId();
        hcNode.setFruName(nodeId+"");
        hcNode.setNodeId(BigInteger.valueOf(nodeId));
        String curVer="";
        try {

            AlertApi.AlertViewProperty alertView = 
                MgmtServer.getInstance().getAlertView();
            if (alertView != null) {
                String prop = "root." + input.nodeId() + 
                  ".PlatformService.biosInfo.UUID";
                curVer=prop;
                AlertApi.AlertObject o = alertView.getAlertProperty(prop);
                hcNode.fruId = o.getPropertyValueString().trim() + 
                  "              ";
            } else {
                hcNode.fruId="unavailable";
            }
            try {
                hcNode.fruId = codec.encode(hcNode.fruId);
            } catch (EncoderException e) {
                logger.log(Level.INFO, "failed to encode string " +
                  hcNode.fruId, e);
                hcNode.fruId = "unavailable";
            }



        } catch (AlertException e) {
            hcNode.fruId="unavailable";
            logger.log(Level.WARNING,
              "Error while retrieving the firmware version:" + curVer);
        }
        return hcNode;
    }

    public static HCNode createDeadNode(int nodeId) {
        HCNode deadNode=new HCNode();
        deadNode.setIsAlive(false);
        deadNode.setIsMaster(false);
        deadNode.setIsViceMaster(false);
        deadNode.setDiskCount(BigInteger.valueOf(0));
        deadNode.setIsEligible(false);
        deadNode.setNodeId(BigInteger.valueOf(nodeId));
        return deadNode;
        
    }

}
