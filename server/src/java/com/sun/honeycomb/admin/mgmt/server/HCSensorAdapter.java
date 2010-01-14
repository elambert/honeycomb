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
import java.util.logging.Logger;

import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.platform.IPMIInfo;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCSensorAdapter implements HCSensorAdapterInterface {

    private int _nodeId;

    
    private static transient final Logger logger = 
      Logger.getLogger(HCSensorAdapter.class.getName());


    public void loadHCSensor(String node) 
        throws InstantiationException {
        _nodeId = Integer.parseInt(node);
    }
     

    /*
    * This is the list of accessors to the object
    */
    public String getNodeid() throws MgmtException {
        return String.valueOf(_nodeId);
    }

    public String getDdrVoltage() throws MgmtException {
        return getIPMIInfo(IPMIInfo.vDDR);
    }

    public String getCpuVoltage() throws MgmtException {
        return getIPMIInfo(IPMIInfo.vCPU);
    }

    public String getThreeVCC() throws MgmtException {
        return getIPMIInfo(IPMIInfo.v3_3);
    }

    public String getFiveVCC() throws MgmtException {
        return getIPMIInfo(IPMIInfo.v5);
    }

    public String getTwelveVCC() throws MgmtException {
        return getIPMIInfo(IPMIInfo.v12);
    }

    public String getBatteryVoltage() throws MgmtException {
        return getIPMIInfo(IPMIInfo.vBattery);
    }

    public String getCpuTemperature() throws MgmtException {
        return getIPMIInfo(IPMIInfo.tempCPU);
    }


    public String getSystemTemperature() throws MgmtException {
        return getIPMIInfo(IPMIInfo.tempSys);
    }

    public String getCpuFanSpeed() throws MgmtException {
        // does not seem to be implemented by IPMIInfo
        return "unavailable";
    }

    public String getSystemFan1Speed() throws MgmtException {
        return getIPMIInfo(IPMIInfo.sysFan1);
    }

    public String getSystemFan2Speed() throws MgmtException {
        return getIPMIInfo(IPMIInfo.sysFan2);
    }

    public String getSystemFan3Speed() throws MgmtException {
        return getIPMIInfo(IPMIInfo.sysFan3);
    }
    public String getSystemFan4Speed() throws MgmtException {
        return getIPMIInfo(IPMIInfo.sysFan4);
    }
    
    public String getSystemFan5Speed() throws MgmtException {
        return getIPMIInfo(IPMIInfo.sysFan5);
    }

    private String getIPMIInfo(int val) {
        IPMIInfo info = null;
        try {
            PlatformService platform = PlatformService.Proxy.getApi(_nodeId);
            if (platform != null) {
                info = platform.getIPMIInfo();
            }
            if (info == null) {
                logger.warning("Failed to get IPMInfo on node " + _nodeId);
            }
        } catch (ManagedServiceException me) {
            logger.severe("getIPMIInfo(" + _nodeId + ") got: " + me);
        }
        if (info != null) {
            return info.getValue(val);
        } else {
            return "unavailable";
        }
    }
}
