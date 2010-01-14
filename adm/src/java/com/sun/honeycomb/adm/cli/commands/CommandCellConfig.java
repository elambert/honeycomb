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
import com.sun.honeycomb.adm.cli.CliException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.adm.cli.Shell;


import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import java.util.HashMap;

import java.util.Iterator;
import java.util.Properties;


public class CommandCellConfig extends ValueSetter {

    private static final String[][] CELL_CONFIG_OPTIONS= {
        {"Admin IP Address", ADMIN_IP_KEY, "AdminVIP","a"},
        {"Data IP Address", DATA_IP_KEY, "DataVIP","d"},
        {"Service Node IP Address", SP_IP_KEY, "SpVIP","n"},
        {"Subnet", SUBNET_KEY, "Subnet", "u"},
        {"Gateway", GATEWAY_KEY, "Gateway", "g"},
    };

    private final Option _optSetInteractive;

    public CommandCellConfig (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden,  CELL_CONFIG_OPTIONS);
	addCellIdOption(true);
	addForceOption();
        _optSetInteractive = addOption (OPTION_BOOLEAN,'z', "set");
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {

        boolean doForce = false;
    
        int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) 
	    return retCode;
	
	String[] unparsedArgs = getRemainingArgs();
	if (unparsedArgs.length > 0) {
	    System.out.println("Unknown argument: " + unparsedArgs[0]);
	    usage ();
	    return EX_USAGE;
	}
        HCCell cell = getCell();
        HCCellProps cellProps=null;
        try {            
            cellProps=getApi().getCellProps(cell);
        } catch (MgmtException e) {
            printError("Error fetching cell properties.",e);
            return EX_UNAVAILABLE;
        }      
       
        if(getOptionValueBoolean(_optSetInteractive)) {
            boolean changed=setValuesInteractively((Object)cellProps,CELL_CONFIG_OPTIONS, false);

            if(false==changed) {
                System.out.println("No values changed.");
            } else {
                try {
		    if (verifyInteractiveValues(CELL_CONFIG_OPTIONS) == false) {
                        return EX_TEMPFAIL;
		    }
		    if (isAddressConflict(cellProps)) {
			return EX_TEMPFAIL;
		    }
                    HCCellProps blankProps=new HCCellProps();

                    if (isForceEnabled() == false) {
                        if (! promptForConfirm ("Committing the changes " +
                                                "will reboot the cell " + cellId + 
                                                ". Commit ? [y/N]: ", 'N')) {
                            return EX_USERABORT;
                        }
                    }
                    if(!validateSwitches(cellId)) 
                        return EX_TEMPFAIL;

                    commitChanges((Object)blankProps, CELL_CONFIG_OPTIONS);
                    getApi().setCellProps(cell,blankProps);
		    // TODO: setSetupCell() and setCellProps() - the underlying 
		    // messages outputed by the two are different.  setCellProps()
		    // doesn't tell us about the master reboot.  We should add
		    // it for consistency so we don't have to do it here.
		    doMasterLogoutCheck();
                }catch (MgmtException e) {
                    String errorMessage= "Invalid data value: " +e.getMessage();
                    printError(errorMessage,e);
                }
            }

        } else if(areKeyValues(CELL_CONFIG_OPTIONS)) {
            
            Iterator iter = _values.keySet().iterator();
	    // The Hashmap needs to get cleared each invocation
	    // otherwise we attempt to use the values from the
	    // last invocation
            _pendingChanges.clear();
            while (iter.hasNext()) {
                String key = (String) (iter.next());
                    
                if(isKeyOption(key,CELL_CONFIG_OPTIONS)) {
                    _pendingChanges.put(key,(String)_values.get(key));
		    if (verifySimpleValue(key) == false) {
                        return EX_TEMPFAIL;
		    }
                }                        
            }
	    if (isAddressConflict(cellProps)) {
		return EX_TEMPFAIL;
	    }
            HCCellProps blankProps=new HCCellProps();
            if(!validateSwitches(cellId)) 
		return EX_TEMPFAIL;


            commitChanges((Object)blankProps, CELL_CONFIG_OPTIONS);

            if (isForceEnabled() == false) {
                if (! promptForConfirm ("Committing the changes " +
                                        "will reboot the cell " + cellId + 
                                        ". Commit ? [y/N]: ", 'N')) {
                    return EX_USERABORT;
                }
            }
	    // TODO: setSetupCell() and setCellProps() - the underlying 
	    // messages outputed by the two are different.  setCellProps()
	    // doesn't tell us about the master reboot.  We should add
	    // it for consistency so we don't have to do it here.
            getApi().setCellProps(cell,blankProps);
	    doMasterLogoutCheck();

        } else {
            super.printNetInfo((Object)cellProps,CELL_CONFIG_OPTIONS);
        }
        return EX_OK;
    }
    
    /**
     * Check to see if we are running on the master cell.  If we are indicate
     * to the user that the master cell is rebooting.  Wait a period of time
     * as defined by doMasterLogoutWait() and if our process isn't killed
     * force a logout.
     * <P>
     * If we are not running on the master cell output a warning that cell x
     * is rebooting and won't be available until it comes back online.
     */
    public void doMasterLogoutCheck()
    throws MgmtException, ConnectException, PermissionException{
	
	if(cellId == getApi().getMasterCellId()) { 
	    System.out.println("Master cell is rebooting...");
	    doMasterLogoutWait();
	} else {
	    System.out.println("Cell " + cellId 
		+ " is rebooting. It will be unavailable until it comes back online.");
	}
    }
    
    
    /**
     * Validate the values entered by the user.   This method is called
     * when the "--set" option is called.
     * @param options the options list
     * @return true if all values have been successfully updated,
     * false otherwise.
     */
    protected boolean verifyInteractiveValues(String[][] options)
    throws MgmtException,  ConnectException {
	
	for(int i = 0; i < options.length; i++) {
	    if (verifySimpleValue(options[i][1]) == false)
		return false;
	}
        return true;
    }
    
    
    /**
     * Do a check of the address settings looking for conflicts.  Outputs 
     * appropriate error message
     * <P>
     * The same basic routine also exists in CommandCellConfig but
     * since HCCellProps and HCSetupCell don't have a common base
     * class we have to create this routine twice.
     *
     * @param props the current settings of the box
     * @return boolean true if conflict exists, false otherwise 
     */
    protected boolean isAddressConflict(HCCellProps props) {
	String adminVIP = (String)_pendingChanges.get(ADMIN_IP_KEY);
	String dataVIP = (String)_pendingChanges.get(DATA_IP_KEY);
	String spVIP = (String)_pendingChanges.get(SP_IP_KEY);
	String subnet = (String)_pendingChanges.get(SUBNET_KEY);
	String gateway = (String)_pendingChanges.get(GATEWAY_KEY);
	
	// All this checking is getting absurd but the regression test
	// do all sorts of things like this.  
	// TODO: Simplify
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

