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


import java.util.Properties;
import java.io.IOException;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.StringTokenizer;
import java.util.HashMap;

import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.adm.client.*;
import com.sun.honeycomb.util.AuditLevel;
import com.sun.honeycomb.spreader.SwitchStatusManager;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.CliException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.common.CliConstants;

/*
 * This entire class is going to be revised/removed in 1.1 to 
 * support healing based sloshing.
 */

public class CommandCellAdmin extends ShellCommand 
implements ExitCodes {

    // cell admin commands
    private static final String CMD_EXPAND = "expand";


    public CommandCellAdmin (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
    }

    public int main (Properties env, String[] argv) 
    throws MgmtException, PermissionException, ConnectException {
	
	int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE)
	    return retCode;	

	String[] args = getRemainingArgs();

	try {
	    SiloInfo silo = SiloInfo.getInstance();
	    if (isMultiCell()) {
		System.out.println("A cell may not be expanded in a multicell configuration.");
		return EX_USAGE;
	    }
	    cellId = SiloInfo.getInstance().getUniqueCellId();
	    cell = getApi().getCell(cellId);    
	}
	catch (ConnectException e) {
	    exitConnectError(e);
	}
	catch (PermissionException pe) {
	    exitPermissionException(pe);
	}
	
	if (args == null || args.length == 0) { 
	    showCellInfo(cell);
	    return EX_OK;
	} 

	if (args.length == 1 && args[0].equals(CMD_EXPAND)) { 

	    // perform the requested command
	    if (isForceEnabled() == false) {
		if (! promptForConfirm ("Expand cell? [y/N]: ", 'N')) {
		    return EX_USERABORT;
		}
	    }
	    return expandCell(cell);
	}
	else {
	    usage();
	    return EX_USAGE;
	}
    }
    

    /**
     * Show info about the cell.
     */
    private void showCellInfo (HCCell cell) throws MgmtException, PermissionException, ConnectException {
        int status = -2;


        status = getApi().getExpansionStatus(cell);


        
        String statusString = null;
        switch (status) {
            case CliConstants.EXPAN_UNKNOWN:
                statusString = "unknown";
                break;
            case CliConstants.EXPAN_NT_RDY:
                statusString = "not ready";
                break;
            case CliConstants.EXPAN_READY:
                statusString = "ready";
                break;
            case CliConstants.EXPAN_EXPAND:
                statusString = "expanding";
                break;
            case CliConstants.EXPAN_DONE:
                statusString = "complete";
                break;
        }

        System.out.println ("Expansion status is: "+statusString);
    }

    /**
     * Start hive expansion.
     */
    private int expandCell (HCCell cell) throws MgmtException,PermissionException, ConnectException {


        try {
            getApi().startExpansion(cell);
        } catch (MgmtException ce) {
            System.out.println ("Expansion failed: " + ce.getMessage());
            return EX_BADCMD;
        }

        String str = getLocalString ("cli.alert.expand");
        System.out.println(str);
        return EX_OK;
    }

    /** check if expansion is in progress */
    //
    // not 
    //
    /*
    private boolean isExpanding(HCCell cell) throws MgmtException,PermissionException, ConnectException {
        return (getApi().getExpansionStatus(cell) == 1);
    }
    */
    
}


