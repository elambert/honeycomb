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



package com.sun.honeycomb.adm.client;

import com.sun.honeycomb.adm.cli.Shell;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCSiloProps;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.common.MgmtException;

import java.util.logging.Level;

public class MultiCellSetSiloProps extends MultiCellOpBase
{

    public MultiCellSetSiloProps(HCCell cell) {
        super(cell);
    }

    public void run() {

        HCSiloProps newProps = (HCSiloProps) cookie;
        HCSiloProps oldProps= cell.getSiloProps();
        
        if (newProps.getNtpServer() != null) {       
            oldProps.setNtpServer(newProps.getNtpServer());        
        }
        if (newProps.getSmtpServer() != null) {       
            oldProps.setSmtpServer(newProps.getSmtpServer());
        }
        if (newProps.getSmtpPort() != null) {       
            oldProps.setSmtpPort(newProps.getSmtpPort());
        }
        if (newProps.getAuthorizedClients() != null) {       
            oldProps.setAuthorizedClients(newProps.getAuthorizedClients());

        }
        if (newProps.getExtLogger() != null) {   
            oldProps.setExtLogger(newProps.getExtLogger());        
        }
        if (newProps.getDns() != null) {       
            oldProps.setDns(newProps.getDns());  
        }
        if (newProps.getDomainName() != null) {       
            oldProps.setDomainName(newProps.getDomainName());
        }
        if (newProps.getDnsSearch() != null) {
            oldProps.setDnsSearch(newProps.getDnsSearch());
        }
        if (newProps.getPrimaryDnsServer() != null) {
            oldProps.setPrimaryDnsServer(newProps.getPrimaryDnsServer());
        }
        if (newProps.getSecondaryDnsServer() != null ) {
            oldProps.setSecondaryDnsServer(newProps.getSecondaryDnsServer());
        }
	int cellId = cell.getCellId();
        try {
            cell.push();
	    Shell.logger.log(Level.FINE, 
		"CLI: Successfully updated silo props configuration on cell  " 
		+ cellId + ".");
	    StringBuffer buf = new StringBuffer("You must reboot ");
	    if (SiloInfo.getInstance().getCellCount() != 1) {
		// This is a multicellsystem.  
		buf.append("cell ").append(cellId).append(" with ");
		buf.append("'reboot --cellid ").append(cellId).append(" --all'\n");
	    } else {
		buf.append("the hive with 'reboot --all' ");
	    }
	    buf.append("for all changes to take effect.");
	    System.out.println(buf.toString());
        }
	catch (Exception e) {
	    Shell.logger.log(Level.SEVERE, 
		"CLI: Failed to save configuration on cell " + cellId + ".", e);
            result = CliConstants.MGMT_CMM_CONFIG_UPDATE_FAILED;
	    return;
        }
        result = CliConstants.MGMT_OK;
    }
}
