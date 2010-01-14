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

import com.sun.honeycomb.mgmt.common.MgmtException;


public class HCNodeAdapter
    extends HCFruAdapter
    implements HCNodeAdapterInterface {

    protected int _nodeId;
    private MgmtServer mgmtServer;
    private HCNode node;

    public HCNodeAdapter() {
        super();
    }

    public void loadHCNode(BigInteger nodeid)
        throws InstantiationException {
        _nodeId = nodeid.intValue();
        mgmtServer = MgmtServer.getInstance();
    }

    /*
    * This is the list of accessors to the object
    */
    public BigInteger getNodeId() throws MgmtException {
        return BigInteger.valueOf(_nodeId);
    }

    public String getHostname() throws MgmtException {
        return "NODE-"+_nodeId;
    }

    public Boolean getIsAlive() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).isAlive;
    }
    public Boolean getIsEligible() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).isEligible;
    }
    public Boolean getIsMaster() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).isMaster;
    }
    
    public Boolean getIsViceMaster() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).isViceMaster;
    }
    
    public BigInteger getDiskCount() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).diskCount;
    }
    public BigInteger getStatus() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).status;
    }


    public BigInteger reboot(BigInteger dummy) throws MgmtException {
        mgmtServer.logger.info("reboot: ");
        return BigInteger.valueOf(0);
    }
    public String getFruId() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).getFruId();

    }
    public String getFruName() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).getFruName();
    }
    public BigInteger getFruType() throws MgmtException {
        return ValuesRepository.getInstance().getNode(_nodeId).getFruType();
    }

}
