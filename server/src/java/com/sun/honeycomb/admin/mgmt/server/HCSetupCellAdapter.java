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


import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.common.ConfigPropertyNames;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HCSetupCellAdapter implements HCSetupCellAdapterInterface {

    static private final int SILO_PROPS_LENGTH = 11;
    static private final int CELL_PROPS_LENGTH = MultiCellLib.NB_PROPS_CELL;

    private HashMap              siloProps = null;
    private HashMap              cellProps = null;
    private ClusterProperties    config;
    private MgmtServer           mgmtServer;
    private byte                 cellid;

    public void loadHCSiloProps()
        throws InstantiationException {
        // Do nothing.  All work done via loadHCSetupCell
    }

    public void loadHCSetupCell()
        throws InstantiationException {
        config = ClusterProperties.getInstance();
        mgmtServer = MgmtServer.getInstance();
        siloProps = new HashMap();
        cellProps = new HashMap();
        try {
            cellid = 
              Byte.parseByte(config.getProperty(MultiCellLib.PROP_SILO_CELLID));
        } catch (Exception ignored) {
            throw new InstantiationException("Internal error while " +
                "retrieving the cellid");
        }
    }

    private void commitProps() {
	// Technically the callee should make the change to commit this value.
	// This code should be rewritten
	// In it's current form the callee does a push of this object.   After the push
	// there's no way to interact with the object that we pushed.  As part of the push
	// the values need to be comitted.  Instead of writting each object out the code
	// waits until all values have been set.  It assumes all values are set when the map
	// reaches the correct size that we are waiting for.
	//
	// It would be safer to rewrite this code and invoke an argument that sets all the
	// values at once.  This would require changing the callee to invoke a method with
	// all arguments 
	//
	// Any changes to the HC_SiloProps object could potentially breakt his code
	// The +1 is for the cellid value
	if ((cellProps.size() == (CELL_PROPS_LENGTH + 1)) &&
            (siloProps.size() == (SILO_PROPS_LENGTH + 1))) {
            try {
                config.putAll(siloProps);
	        MultiCellLib.getInstance().updateProperties(cellid, cellProps);
                mgmtServer.logger.info("setupCell : successfully updated silo " +
                    "and cellProps properties...");
            } 
            catch (Exception e) {
                mgmtServer.logger.log(Level.SEVERE, "Failed to updated properties...", e);
            }
        }
    }


    public String getCellId() throws MgmtException {
        return "dummy";
    }


    public void setCellId(String value) throws MgmtException {
        cellProps.put(MultiCellLib.PROP_SILO_CELLID, value);
        siloProps.put(MultiCellLib.PROP_SILO_CELLID, value);
	commitProps();
    }

    //
    // Cell Properties
    //
    public String getAdminVIP() throws MgmtException {
        return "dummy";
    }

    public void setAdminVIP(String value) throws MgmtException {
        cellProps.put(MultiCellLib.PROP_ADMIN_VIP, value);
        commitProps();
    }

    public String getDataVIP() throws MgmtException {
        return "dummy";
    }
    public void setDataVIP(String value) throws MgmtException {
        cellProps.put(MultiCellLib.PROP_DATA_VIP, value);
        commitProps();
    }

    public String getSpVIP() throws MgmtException {
        return "dummy";
    }
    public void setSpVIP(String value) throws MgmtException {
        cellProps.put(MultiCellLib.PROP_SP_VIP, value);
        commitProps();
    }

    public String getSubnet() throws MgmtException {
        return "dummy";
    }

    public void setSubnet(String value) throws MgmtException {
        cellProps.put(MultiCellLib.PROP_SUBNET, value);
        commitProps();
    }

    public String getGateway() throws MgmtException {
        return "dummy";
    }

    public void setGateway(String value) throws MgmtException {
        cellProps.put(MultiCellLib.PROP_GATEWAY, value);
        commitProps();
    }


    //
    // Silo Properties
    //

    public String getNtpServer() throws MgmtException {
        return "dummy";
    }

    public void setNtpServer(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_NTP_SERVER, value);
        commitProps();
    }

    public String getSmtpServer() throws MgmtException {
        return "dummy";
    }

    public void setSmtpServer(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_SMTP_SERVER, value);
        commitProps();
    }

    public String getSmtpPort() throws MgmtException {
        return "dummy";
    }

    public void setSmtpPort(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_SMTP_PORT, value);
        commitProps();
    }

    public String getAuthorizedClients() throws MgmtException {
        return "dummy";
    }

    public String getNumAuthRules() throws MgmtException {
        return "dummy";
    }

    public void setNumAuthRules(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_AUTH_NUM_RULES, value);
        commitProps();
    }

    public void setAuthorizedClients(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_AUTH_CLI, value);
        commitProps();
    }

    public String getExtLogger() throws MgmtException {
        return "dummy";
    }

    public void setExtLogger(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_EXT_LOGGER, value);
        commitProps();
    }

    public String getDns() throws MgmtException {
        return "dummy";
    }

    public void setDns(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_DNS, value);
        commitProps();
    }

    public String getDomainName() throws MgmtException {
        return "dummy";
    }

    public void setDomainName(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_DOMAIN_NAME, value);
        commitProps();
    }

    public String getDnsSearch() throws MgmtException {
        return "dummy";
    }

    public void setDnsSearch(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_DNS_SEARCH, value);
        commitProps();
    }

    public String getPrimaryDnsServer() throws MgmtException {
        return "dummy";
    }

    public void setPrimaryDnsServer(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER, value);
        commitProps();
    }

    public String getSecondaryDnsServer() throws MgmtException {
        return "dummy";
    }
    public void setSecondaryDnsServer(String value) throws MgmtException {
        siloProps.put(ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER, value);
        commitProps();
    }
}
