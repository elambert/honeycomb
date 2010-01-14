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


package com.sun.honeycomb.adm.cli.commands; 

import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.admin.mgmt.client.HCSetupCell;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import com.sun.honeycomb.admin.mgmt.client.HCSiloProps;
import com.sun.honeycomb.adm.cli.AdmException;

import com.sun.honeycomb.adm.client.SiloInfo;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import java.util.Properties;

public class CommandSetupCell extends ValueSetter {

    private static final String[][] SETUP_CELL_OPTIONS = {

        //
        // Cell ID
        //
        {"Cell ID", CELLID_KEY, "CellId", "c",""},        
        //
        // cell props
        //
        {"Admin IP Address", ADMIN_IP_KEY, "AdminVIP", "a"},
        {"Data IP Address", DATA_IP_KEY, "DataVIP", "d"},
        {"Service Node IP Address", SP_IP_KEY, "SpVIP", "S"},
        {"Subnet", SUBNET_KEY, "Subnet", "u"},
        {"Gateway", GATEWAY_KEY, "Gateway", "g"},

        //
        // silo props
        {"NTP Server", NTP_SERVER_KEY, "NtpServer", "n"},
        {"SMTP Server",SMTP_SERVER_KEY, "SmtpServer", "s"},
        {"SMTP Port",SMTP_SERVER_PORT_KEY, "SmtpPort", "p"},
        {"Authorized Clients",AUTH_CLIENT_KEY, "AuthorizedClients", "h"},
        {"External Logger",EXT_LOGGER_KEY, "ExtLogger", "x"},
        {"DNS", DNS_ENABLE_KEY, "Dns", "D"},
        {"Domain Name", DOMAIN_NAME_KEY, "DomainName","m"},
        {"DNS Search", DNS_SEARCH_KEY, "DnsSearch","e"},
        {"Primary DNS Server", PRIMARY_DNS_SERVER_KEY, "PrimaryDnsServer","1"},
        {"Secondary DNS Server", SECONDARY_DNS_SERVER_KEY, "SecondaryDnsServer","2"}
    };

    public CommandSetupCell (String name, String[] aliases, Boolean isHidden) {

        super (name, aliases,  isHidden, SETUP_CELL_OPTIONS);         
	addForceOption();
    }

    public int main (Properties env, String[] argv)
        throws MgmtException, PermissionException, ConnectException {       

        if(!getApi().loggedIn())
            throw new PermissionException();

        int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}
	
	String[] unparsedArgs = getRemainingArgs();
	if (unparsedArgs.length > 0) {
	    System.out.println("Unknown argument: " + unparsedArgs[0]);
	    usage();
	    return EX_USAGE;
	}
	
        System.out.println("\n\tThis command should only be used to set up \n" +
          "\tan empty cell after it is received from the factory.");
        System.out.println("\tYou should use hivecfg or cellcfg to set the \n" +
          "\tproperties.\n");

        System.out.println("\tIf cellid was changed, the hive MUST BE WIPED.\n"+
                           "\tThis operation should be performed manually by the operator\n"+
                           "\twhen the hive comes back online.\n\n");

        System.out.println("\tSYSTEM WILL AUTOMATICALLY REBOOT IF ANY VALUES ARE CHANGED.\n\n");
	
	if (isForceEnabled() == false) {
	    if (continueOperation("Continue") == false)
		return EX_OK;
	}
	
	HCSetupCell setupCellProps = null;
        try {            
            setupCellProps=getApi().getSetupCell();
        } catch (MgmtException e) {
            System.out.println("Error fetching cell properties.");
            return EX_UNAVAILABLE;
        }      
	
        boolean changed = setValuesInteractively((Object)setupCellProps,
                                                 SETUP_CELL_OPTIONS, 
                                                 true);
        if (false == changed) {
            System.out.println("No values changed.");
        } else {
            try {
		if (!verifyInteractiveValues(SETUP_CELL_OPTIONS, setupCellProps)) {
                        return EX_TEMPFAIL;
		}
		if (verifyDNSSettings(setupCellProps) == false) {
		    return EX_TEMPFAIL;
		}
		if (isAddressConflict(setupCellProps)) {
		    return EX_TEMPFAIL;
		}
                if (!promptForConfirm ("Committing the changes " 
			+ "will reboot cell " 
			+ setupCellProps.getCellId()
                        + ". Commit? [y/N]: ", 'N')) {
                    System.out.println("Nothing has been committed, command cancelled.");
                    return EX_USERABORT;
                }
                if(!validateSwitches(SiloInfo.getInstance().getUniqueCellId())) 
                    return EX_TEMPFAIL;
		System.out.print("Updating configuration settings...");
		commitChanges((Object)setupCellProps, SETUP_CELL_OPTIONS);
		getApi().setSetupCell(setupCellProps);
		System.exit(0);
            } catch (MgmtException e) {
                String errorMessage= "Invalid data value: " + e.getMessage();
                printError(errorMessage,e);
                return EX_USAGE;
            } catch (AdmException ae) {
                String errorMessage= "Not enough permission to run that " +
                  " command ";
                printError(errorMessage, ae);
                return EX_NOPERM;                
            }
	    finally {
		System.out.flush();
	    }
        }
        return EX_OK;
    }
    
    /**
     * Validate the values entered by the user.   This method is called
     * when the "--set" option is called.
     * @param options the options list
     * @param siloProps the current setttings
     * @return true if all values have been successfully updated,
     * false otherwise.
     */
    protected boolean verifyInteractiveValues(
	String[][] options, HCSiloProps siloProps)
    throws ConnectException, MgmtException {
	
	for(int i = 0; i < options.length; i++) {
	    if (verifyValue(options[i][1], siloProps) == false)
		return false;
	}
        return true;
    }
    
    /**
     * @see ValueSetter#verifyValue(String, HCSiloProps)
     */
    protected boolean verifyValue(String key, HCSiloProps siloProps)
    throws ConnectException, MgmtException {

	if (CELLID_KEY.equals(key)) {
	    try {
		String value = (String) _pendingChanges.get(CELLID_KEY);
		if (value != null)
		    Byte.parseByte(value);
	    } catch (NumberFormatException exc) {
		System.out.println("Invalid cell ID specified.");
		return false;
	    }
	    return true;
	}
	return super.verifyValue(key, siloProps);
    }
    
  
    /**
     * Do a check of the Address settings looking for conflicts.  Outputs 
     * appropriate error message
     * <P>
     * The same basic routine also exists in CommandCellConfig but
     * since HCCellProps and HCSetupCell don't have a common base
     * class we have to create this routine twice.
     *
     * @param props the current settings of the box
     * @return boolean true if conflict exists, false otherwise 
     */
    protected boolean isAddressConflict(HCSetupCell props) {
	String adminVIP = (String)_pendingChanges.get(ADMIN_IP_KEY);
	String dataVIP = (String)_pendingChanges.get(DATA_IP_KEY);
	String spVIP = (String)_pendingChanges.get(SP_IP_KEY);
	String subnet = (String)_pendingChanges.get(SUBNET_KEY);
	String gateway = (String)_pendingChanges.get(GATEWAY_KEY);
	
	// All this checking is getting absurd but the regression test
	// do all sorts of things like this.  
	// TODO: Simplify. 
	if (adminVIP != null) {
	    if (adminVIP.equals(dataVIP != null ? dataVIP : props.getDataVIP())) {
		System.out.println(
		    "Configuration error.  Admin IP address and data IP address can not be the same.");
		return true;
	    }
	    
	    if (adminVIP.equals(spVIP != null ? spVIP : props.getSpVIP())) {
		System.out.println(
		    "Configuration error.  Admin IP address and service processor IP address can\nnot be the same.");
		return true;
	    }
	    
	    if (adminVIP.equals(subnet != null ? subnet : props.getSubnet())) {
		System.out.println(
		    "Configuration error.  Admin IP address and subnet address can\nnot be the same.");
		return true;
	    }
	    
	    if (adminVIP.equals(gateway != null ? gateway : props.getGateway())) {
		System.out.println(
		    "Configuration error.  Admin IP address and gateway address can\nnot be the same.");
		return true;
	    }
	}
	if (dataVIP != null) {
	    if (dataVIP.equals(adminVIP != null ? adminVIP : props.getAdminVIP())) {
		System.out.println(
		    "Configuration error.  Admin IP address and data IP address can not be the same.");
		return true;
	    }
	    
	    if (dataVIP.equals(spVIP != null ? spVIP : props.getSpVIP())) {
		System.out.println(
		    "Configuration error.  Data IP address and service processor IP address can\nnot be the same.");
		return true;
	    }
	    if (dataVIP.equals(spVIP != null ? spVIP : props.getSpVIP())) {
		System.out.println(
		    "Configuration error.  Data IP address and service processor IP address can\nnot be the same.");
		return true;
	    }
	    if (dataVIP.equals(subnet != null ? subnet : props.getSubnet())) {
		System.out.println(
		    "Configuration error.  Data IP address and subnet address can\nnot be the same.");
		return true;
	    }
	    if (dataVIP.equals(gateway != null ? subnet : props.getGateway())) {
		System.out.println(
		    "Configuration error.  Data IP address and gateway address can\nnot be the same.");
		return true;
	    }
	}
	
	if (spVIP != null) {
	    if (spVIP.equals(adminVIP != null ? adminVIP : props.getAdminVIP())) {
		System.out.println(
		    "Configuration error.  Admin IP address and service processor IP address can\nnot be the same.");
		return true;
	    }
	    
	    if (spVIP.equals(dataVIP != null ? dataVIP : props.getDataVIP())) {
		System.out.println(
		    "Configuration error.  Data IP address and service processor IP address can\nnot be the same.");
		return true;
	    }
	    if (spVIP.equals(subnet != null ? subnet : props.getSubnet())) {
		System.out.println(
		    "Configuration error.  Data IP address and subnet address can\nnot be the same.");
		return true;
	    }
	    if (spVIP.equals(gateway != null ? gateway : props.getGateway())) {
		System.out.println(
		    "Configuration error.  Data IP address and subnet address can\nnot be the same.");
		return true;
	    }
	}
	if (subnet != null) {
	    if (subnet.equals(adminVIP != null ? adminVIP : props.getAdminVIP())) {
		System.out.println(
		    "Configuration error.  Admin IP address and subnet address can\nnot be the same.");
		return true;
	    }
	    
	    if (subnet.equals(dataVIP != null ? dataVIP : props.getDataVIP())) {
		System.out.println(
		    "Configuration error.  Data IP address and subnet address can\nnot be the same.");
		return true;
	    }
	    if (subnet.equals(spVIP != null ? spVIP : props.getSpVIP())) {
		System.out.println(
		    "Configuration error.  Service processor IP address and subnet address can\nnot be the same.");
		return true;
	    }
	    if (subnet.equals(gateway != null ? gateway : props.getGateway())) {
		System.out.println(
		    "Configuration error.  Subnet address and gateway address can\nnot be the same.");
		return true;
	    }
	}
	if (gateway != null) {
	    if (gateway.equals(adminVIP != null ? adminVIP : props.getAdminVIP())) {
		System.out.println(
		    "Configuration error.  Admin IP address and gateway address can\nnot be the same.");
		return true;
	    }
	    
	    if (gateway.equals(dataVIP != null ? dataVIP : props.getDataVIP())) {
		System.out.println(
		    "Configuration error.  Data IP address and gateway address can\nnot be the same.");
		return true;
	    }
	    if (gateway.equals(spVIP != null ? spVIP : props.getSpVIP())) {
		System.out.println(
		    "Configuration error.  Service processor IP address and gateway address can\nnot be the same.");
		return true;
	    }
	    if (gateway.equals(subnet != null ? subnet : props.getGateway())) {
		System.out.println(
		    "Configuration error.  Subnet address and gateway address can\nnot be the same.");
		return true;
	    }
	}
	return false;
    }
    
    
}
