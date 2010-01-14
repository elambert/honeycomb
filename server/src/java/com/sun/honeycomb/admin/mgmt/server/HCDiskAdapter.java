
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import java.io.UnsupportedEncodingException;

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskLabel;
import com.sun.honeycomb.diskmonitor.DiskControl;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.admin.mgmt.AdminException;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;

import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.Reassure;

import com.sun.honeycomb.common.CliConstants;


public class HCDiskAdapter
    extends HCFruAdapter implements HCDiskAdapterInterface {


    DiskProxy diskProxy = null;
    Disk disk = null;
    String diskId = null;

    private static final Logger logger =
        Logger.getLogger(Utils.class.getName());

    public HCDiskAdapter() {
        super();
    }


    // Some strings break XML unless they're properly encoded;
    // this method provides for that encoding.
    private static QuotedPrintableCodec codec = new QuotedPrintableCodec();
    private static String encode (String s){
        try {
            return codec.encode(s);
        }
        catch (EncoderException e){throw new RuntimeException(e);}
    }

    public void loadHCFru()
        throws InstantiationException {

    }

    public void loadHCDisk(String diskid)        
        throws InstantiationException {
        //
        // Form of 103:4
        //

        StringTokenizer st = new StringTokenizer(diskid, DiskId.DELIM);
        if (st.countTokens() != 2) {
            logger.severe("disk " + diskid + " is invalid");
            throw new InstantiationException("Internal cluster error : " +
                                             " disk " + diskid + " is invalid.");
        }
        int nodeId = Integer.parseInt(st.nextToken());
        int index = Integer.parseInt(st.nextToken());
        
        diskId = "DISK-" + diskid;
        try {
            diskProxy = Utils.getDiskMonitorProxy(nodeId);
        } catch (AdminException ae) {
            logger.severe("failed to get disk monitor on node" + nodeId);
            throw new InstantiationException("Internal cluster error : " +
              "failed to instanciate the adapater HCDisk");
        }

        disk = diskProxy.getDisk(nodeId, index);
        if (disk == null) {
            logger.severe("failed to retrieve disk " + index +
              " on node " + nodeId);
            throw new InstantiationException("Internal cluster error : " +
                "failed to instanciate the adapater HCDisk");
        }
    }


    /*
    * This is the list of accessors to the object
    */




    public String getFruId() throws MgmtException {
        String fruId = disk.getSerialNo();

        fruId = fruId.trim();
        fruId = encode(fruId);

        return fruId;
    }

    public String getFruName() throws MgmtException {
        return diskId;
    }
    
    public String getDiskId() throws MgmtException {
        return diskId;
    }

    public BigInteger getNodeId() throws MgmtException {
        return BigInteger.valueOf(disk.nodeId());
    }

    public BigInteger getFruType() throws MgmtException {
        return BigInteger.valueOf(0);
    }

    public String getDevice() throws MgmtException {
        return disk.getDevice();
    }

    public String getPath() throws MgmtException {
        return disk.getPath();
    }

    public String getMode() throws MgmtException {
        return disk.getModeString();
    }

    public BigInteger getStatus() throws MgmtException {
        switch (disk.getStatus()) {
        case Disk.DISABLED:
            // Disk has been disabled (either by admin or because of errors)
            return BigInteger.valueOf(CliConstants.HCFRU_STATUS_DISABLED);
        case Disk.ENABLED:
            // Disk is in use
            return BigInteger.valueOf(CliConstants.HCFRU_STATUS_ENABLED);
        case Disk.MOUNTED:
            // Disk is mounted, but Honeycomb is not using it
            return BigInteger.valueOf(CliConstants.HCFRU_STATUS_AVAILABLE);
        case Disk.OFFLINE:
            // Disk is present but not mounted
            return BigInteger.valueOf(CliConstants.HCFRU_STATUS_OFFLINE);
        case Disk.FOREIGN:
            // Label indicates disk belongs somewhere else
            return BigInteger.valueOf(CliConstants.HCFRU_STATUS_MISPLACED);
        case Disk.ABSENT:
            // No disk in slot
            return BigInteger.valueOf(CliConstants.HCFRU_STATUS_ABSENT);
        }

        throw new MgmtException("Unknown Disk.status: " + disk.getStatus());
    }

    public Long getTotalCapacity() throws MgmtException {
        return new Long(disk.getDiskSize());
    }

    public Long getUsedCapacity() throws MgmtException {
        return new Long(disk.getUsedSize());
    }

    /*
     * This is the list of custom actions
     */

    public BigInteger enable(EventSender evt, BigInteger dummy)
        throws MgmtException {

        DiskControl ctrl = getDiskControl();
        if (ctrl == null) {
            return BigInteger.valueOf(-1);     
        }
        Reassure reassureThread = null;
        try {           
            reassureThread = new Reassure(evt);
            reassureThread.start();
            // Pay attention to the return code from DiskControl.enable()
            if (!ctrl.enable(disk.getId()))
                return BigInteger.valueOf(-1);
        } catch (Exception exc) {
            return BigInteger.valueOf(-1);           
        } finally {
            reassureThread.safeStop();
        }
        return BigInteger.valueOf(0);
    }

    public BigInteger disable(EventSender evt, BigInteger dummy)
        throws MgmtException {

        DiskControl ctrl = getDiskControl();
        Reassure reassureThread = null;
        try {
            reassureThread = new Reassure(evt);
            reassureThread.start();
            ctrl.disable(disk.getId());
        } catch (Exception exc) {
            return BigInteger.valueOf(-1);            
        } finally {
            reassureThread.safeStop();
        }
        return BigInteger.valueOf(0);
    }

    /**
     * Applies the specified label <code>label</code> to this disk object
     * applyDiskLabel() is the update function, which calls 
     * setDiskLabel() which is just a setter and calls 
     * the set method.
     * Since we are adding a DiskLabel string object to the ws framework,
     * we get free getLabel()/setLabel() methods, but since we want more
     * intelligent settor we call it applyDiskLabel. So applyDiskLabel()
     * is the only interface to update our DiskLabel objects.
     * @param label to set on the disk.
     * @return BigInteger 0 for success, -1 for failure.
     */

    public BigInteger applyDiskLabel(String label) 
        throws MgmtException {
        try {
            setDiskLabel(label);
        } catch (Exception exp) {
            logger.severe("Failed to set label:" + label + 
                          " for disk" + disk.getId());
            return BigInteger.valueOf(-1);
        }
        return BigInteger.valueOf(0);
    }

    /**
     * Interface to set a given disk label <code>label</code>. 
     * Intended to be used for disk service regression testing purposes only.
     * @param label to set on the disk.
     */
    public void setDiskLabel(String label)
        throws MgmtException {
        try {
            DiskControl ctrl = getDiskControl();
            ctrl.setDiskLabel(disk.getId(), label);
        } catch (Exception exp) {
            throw new MgmtException(exp);
        }
    }

    /**
     * Interface to get a disk label. Intended to be used for
     * testing and diagnostic purpose only.
     * @param none
     * @return DiskLabel requested for a disk.
     */

    public String getDiskLabel() {
        DiskControl ctrl = getDiskControl();
        try {
            return ctrl.getDiskLabel(disk.getId()).diskLabelCliString();
        } catch (Exception exp) {
            logger.severe("Failed to get label for disk" + disk.getId());
            return null;
        }
    }

    public BigInteger online(EventSender evt, BigInteger dummy) {
        DiskControl ctrl = getDiskControl();
        if (ctrl == null) {
            return BigInteger.valueOf(-1);      
        }

        Reassure reassureThread = null;
        try {           
            reassureThread = new Reassure(evt);
            reassureThread.start();
            if (!ctrl.onlineDisk(disk.getId())) {
                return BigInteger.valueOf(-1);
	    }
        } catch (Exception exc) {
            return BigInteger.valueOf(-1);            
        } finally {
            reassureThread.safeStop();
        }

        return BigInteger.valueOf(0);
    }

    public BigInteger offline(BigInteger dummy) {
        DiskControl ctrl = getDiskControl();
        if (ctrl == null) {
            return BigInteger.valueOf(-1);      
        }
        try {
            ctrl.offlineDisk(disk.getId());
        } catch (Exception exc) {
            return BigInteger.valueOf(-1);            
        }
        return BigInteger.valueOf(0);
    }

    public BigInteger wipe(EventSender evt, BigInteger dummy) {
        DiskControl ctrl = getDiskControl();
        if (ctrl == null) {
            return BigInteger.valueOf(-1);      
        }
        
        Reassure reassureThread = null;
        try {           
            reassureThread = new Reassure(evt);
            reassureThread.start();
            ctrl.fullWipe(disk.getId());
        } catch (Exception exc) {
            return BigInteger.valueOf(-1);            
        } finally {
            reassureThread.safeStop();
        }
        return BigInteger.valueOf(0);
    }

    private DiskControl getDiskControl() {
        if (! (diskProxy.getAPI() instanceof DiskControl)) {
            return null;
        }
        DiskControl res = (DiskControl) diskProxy.getAPI();
        return res;
    }
}
