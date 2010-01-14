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
import java.util.Iterator;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCDiskAdapter
    extends HCFruAdapter
    implements HCDiskAdapterInterface {

    private MgmtServer  mgmtServer;
    private HCDisk      thisDisk = null;

    public HCDiskAdapter() {
        super();
    }

    public void loadHCDisk(String diskid)
        throws InstantiationException {
        diskid="DISK-"+diskid;
        List<HCDisk> disks =ValuesRepository.getInstance().getDisks().getDisksList();
        Iterator iter=disks.iterator();
        while(iter.hasNext()){
            HCDisk curDisk=(HCDisk)iter.next();
            mgmtServer.logger.info("Checking:" +curDisk.getDiskId() +
                                   " and "+curDisk.getFruId());            
            if (curDisk.getFruId().equals(diskid) ||
                curDisk.getFruName().equals(diskid) ||
                curDisk.getDiskId().equals(diskid)) {
                thisDisk=curDisk;
            }
        }
        if(null == thisDisk)
            throw new InstantiationException ("Emulator cannot find disk named: " +diskid);
        mgmtServer = MgmtServer.getInstance();
    }

    
    public String getFruId() throws MgmtException {
        return thisDisk.getFruId();
    }

    public String getFruName() throws MgmtException {
        return thisDisk.getFruName();
    }

    /*
    * This is the list of accessors to the object
    */
    public String getDiskId() throws MgmtException {
        return thisDisk.getDiskId();

    }

    public BigInteger getNodeId() throws MgmtException {
        return thisDisk.getNodeId();
    }

    public BigInteger getFruType() throws MgmtException {
        return BigInteger.valueOf(CliConstants.HCFRU_TYPE_DISK);
    }
    
    public String getDevice() throws MgmtException {
        return thisDisk.getDevice();
    }

    public String getPath() throws MgmtException {
        return thisDisk.getPath();
    }

    public String getMode() throws MgmtException {
        return thisDisk.getMode();
    }

    public BigInteger getStatus() throws MgmtException {
        return thisDisk.getStatus();
    }

    public Long getTotalCapacity() throws MgmtException {
        return thisDisk.getTotalCapacity();
    }

    public Long getUsedCapacity() throws MgmtException {
        return thisDisk.getUsedCapacity();
    }

    /*
     * This is the list of custom actions
     */

    public BigInteger enable(EventSender evt, BigInteger dummy) throws MgmtException {

        mgmtServer.logger.info("enable disk:" + thisDisk.getDiskId());
        thisDisk.status=BigInteger.valueOf(CliConstants.HCFRU_STATUS_ENABLED);
        return BigInteger.ZERO;
    }


    public BigInteger disable(EventSender evt,BigInteger dummy) throws MgmtException {

        mgmtServer.logger.info("disable disk:" + thisDisk.getDiskId());
        thisDisk.status=
                BigInteger.valueOf(CliConstants.HCFRU_STATUS_DISABLED);
        return BigInteger.ZERO;
    }

    public BigInteger applyDiskLabel(String labelstring) {
        return BigInteger.valueOf(0);
    }
    public void setDiskLabel(String labelstring) {
        return;
    }

    public String getDiskLabel() {
        return null;
    }

    public BigInteger online(EventSender evt, BigInteger dummy) {
        mgmtServer.logger.info("online disk:" + thisDisk.getDiskId());
        thisDisk.status=
                BigInteger.valueOf(CliConstants.HCFRU_STATUS_AVAILABLE);
        return BigInteger.ZERO;
    }

    public BigInteger offline(BigInteger dummy) {
        mgmtServer.logger.info("offline disk:" + thisDisk.getDiskId());
        thisDisk.status=
                BigInteger.valueOf(CliConstants.HCFRU_STATUS_OFFLINE);
        return BigInteger.ZERO;
    }

    public BigInteger wipe(EventSender evt, BigInteger dummy) {
        mgmtServer.logger.info("wipe disk:" + thisDisk.getDiskId());
        return BigInteger.ZERO;
    }
}
