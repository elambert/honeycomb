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

import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.client.ServiceTagsUpdater;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;


/**
 * Command line to add/remove cells.
 */
public class CommandHiveAdm extends ShellCommand 
implements ExitCodes {

    private final Option optAdd;
    private final Option optDelete;
    private final Option optCellId;
    private final Option optAdminVIP;
    private final Option optDataVIP;
    private final Option optStatus;

    
    
    public CommandHiveAdm (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);

        optAdd = addOption (OPTION_BOOLEAN, 'n', "new");	// Hidden Option
        optDelete = addOption (OPTION_BOOLEAN, 'r', "remove");  // Hidden Option
        optStatus = addOption (OPTION_BOOLEAN, 's', "status");
	
	// cellId is different here.  Don't use standard version
        optCellId = addOption (OPTION_BYTE, 'c', "cellid");
        optAdminVIP = addOption (OPTION_STRING, 'a', 
	    ValueSetter.ADMIN_IP_KEY); // Hidden Option
        optDataVIP = addOption (OPTION_STRING, 'd', 
	    ValueSetter.DATA_IP_KEY);   // Hidden Option
	addForceOption();

    }

    public int main (Properties env, String[] argv) 
        throws MgmtException, PermissionException, ConnectException {

        boolean cellAdd = false;
        boolean cellDelete = false;
        boolean cellStatus = false;
        String cellAdminVIP = null;
        String cellDataVIP = null;
        byte    cellId = 0;

        int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}
	
	String[] unparsedArgs = getRemainingArgs();
	if (unparsedArgs.length > 0) {
	    System.out.println("Unknown argument: " + unparsedArgs[0]);
	    usage ();
	    return EX_USAGE;
	}
	
	cellAdd = getOptionValueBoolean(optAdd);
	cellDelete = getOptionValueBoolean(optDelete);
	cellStatus = getOptionValueBoolean(optStatus);
	cellAdminVIP = getOptionValueString(optAdminVIP);
	cellDataVIP = getOptionValueString(optDataVIP);
	cellId = getOptionValueByte(optCellId);

        if(false == cellAdd && false==cellDelete && false==cellStatus){
            cellStatus=true;
        }
        if(!cellStatus) {
	    if(!getApi().loggedIn())
                throw new PermissionException();
	    
	    if (isHiddenOptionProceed() == false) {
		return EX_USERABORT;
	    }
        }
        boolean s = sanityCheck(cellAdd, cellDelete, cellStatus,
          cellAdminVIP, cellDataVIP,cellId);
        if (!s) {
            return EX_USAGE;            
        }
        
        if (cellAdd) {
            addCell(cellAdminVIP, cellDataVIP);
        } else if (cellDelete) {
            delCell(cellId);
        } else if (cellStatus) {
            siloStatus();
        }
        return EX_OK;
    }

    private boolean sanityCheck(boolean cellAdd,
                                boolean cellDelete,
                                boolean cellStatus,
                                String cellAdminVIP,
                                String cellDataVIP,
                                byte cellId) 
        throws MgmtException, ConnectException {


        int commands = 0;
        if (cellAdd) {
            commands++;
        }
        if (cellDelete) {
            commands++;
        }
        if (cellStatus) {
            commands++;
        }
        if (commands != 1) {
            System.out.println("Specify a single command to run.");
            return false;
        }
            
        if (cellAdd) {
            if (cellAdminVIP == null) {
                System.out.println("Admin IP address is null.");
                return false;
            }

            if (cellDataVIP == null) {
                System.out.println("Data IP address is null.");
                return false;
            }


            Pattern pVIP = null;
            if(!getApi().isEmulated()) 
                pVIP=Validate.IP_ADDRESS_PATTERN;
            else 
                pVIP=Validate.IP_ADDRESS_PATTERN_EMULATED;

            Matcher m = pVIP.matcher(cellAdminVIP);

            if (!m.matches()) {
                System.out.println("Invalid admin IP address entered.");
                return false;
            } 




            m = pVIP.matcher(cellDataVIP);
            if (!m.matches()) {
                System.out.println("Invalid data IP address entered.");
                return false;
            }
        }
        if (cellDelete) {
            if (cellId <= 0) {
                System.out.println("You must specify the ID of the cell " +
                    "to remove.");
                return false;
            }
        }
        if (cellStatus) {
            if ((cellAdminVIP != null)  ||
              (cellDataVIP != null)) {
                System.out.println("You must specify admin and data IP addresses for status.");
                return false;
            }
        }
        return true;
    }


    private void addCell(String adminVIP, String dataVIP)
        throws MgmtException, PermissionException, ConnectException {
        
        // When we add a cell to hive it's not necessary to clear
        // the service tag registry on the existing cell since that
        // cell will lose it's status as master.  That will
        // cause the ServiceTag service on that cell to execute, which
        // in turn will delete the service tag registry on cell.
        int newCellid = getApi().addCell(adminVIP, dataVIP);
        if (newCellid > 0) {
            System.out.println("New cell was added successfully, cellid = " +
              newCellid);
            ServiceTagsUpdater.validateAndUpdateServiceTags(getApi());
        } else {
            System.out.println("Failed to add cell in the hive.");
        }
        System.out.flush();
    }

    private void delCell(byte cellId)
        throws MgmtException, PermissionException, ConnectException {
        if (cellId == getApi().getMasterCellId()) {
            System.out.println("You cannot remove the master cell from the hive.");
            System.out.flush();
            return;
        }
        int res = getApi().delCell(cellId);
        if (res == -1) {
            System.out.println("Failed to remove cell " + cellId + 
                ". Verify the cell ID. If it is correct, contact Sun Service.");
        } else {
            System.out.println("Removed cell " + cellId + " successfully.");
            ServiceTagsUpdater.validateAndUpdateServiceTags(getApi());
        }
        System.out.flush();
    }

    private void siloStatus() 
        throws MgmtException, ConnectException {

        HCCell []cells = getApi().getCells(true);
        System.out.println("There is/are " + cells.length + 
          " cell(s) in the hive:");
        for (int i = 0; i < cells.length; i++) {
            if(cells[i].isIsAlive()) {
                HCCellProps props= cells[i].getCellProps();
                System.out.println("- Cell " + cells[i].getCellId() + 
                                   ": adminVIP = " + props.getAdminVIP() +
                                   ", dataVIP = " + props.getDataVIP());
            } else {
                System.out.println("- Cell " + cells[i].getCellId() +
                                   " UNREACHABLE");
            }
        }
    }
}
