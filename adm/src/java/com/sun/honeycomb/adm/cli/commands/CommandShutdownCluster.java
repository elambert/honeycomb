
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


import java.text.MessageFormat;
import java.util.Properties;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.AdmException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCFru;
import com.sun.honeycomb.admin.mgmt.client.HCNode;

public class CommandShutdownCluster extends CommandHwConfig
implements ExitCodes {

    private final Option _optCheat;
    private final Option _optAll;
    private final Option _optIpmi;

    public CommandShutdownCluster (String name,
                                   String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
	addForceOption();
	addCellIdOption(true);
        _optCheat = addOption (OPTION_BOOLEAN, 's', "sp");  // Hidden
        _optAll = addOption (OPTION_BOOLEAN, 'A', "all");
        _optIpmi = addOption (OPTION_BOOLEAN, 'i', "ipmi");
	addNodeOption(true);				    // Hidden
	// addAllCellsOption();  // NOTE: Possible --all conflict
    }

    public int main (Properties env, String[] argv) 
        throws MgmtException, PermissionException, ConnectException {
        boolean doCheat = false;
        boolean doAll = false;
        boolean doIpmi = false;
        if(!getApi().loggedIn())
            throw new PermissionException();

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

	doAll = getOptionValueBoolean(_optAll);
	doIpmi = getOptionValueBoolean(_optIpmi);
	doCheat = getOptionValueBoolean(_optCheat);
	if (doAll)
	    doCheat = true;
	else if (doCheat == true && getNodeName() == null) {
	    if (isHiddenOptionProceed() == false)
		return EX_USERABORT;
	}
    
	if (getNodeName() != null) {
	    HCFru fru = getFruByName(getCell(), getNodeName());
	    
	    if (fru == null || !(fru instanceof HCNode)) {
		System.out.println("Unable to find a FRU for the specified FRUID,\n'"
		    + getNodeName() + "' . To see the list of available FRUIDs use hwstat.\n");
		return EX_DATAERR;
	    }
	    return powerOffNode((HCNode)fru, doIpmi);
	}

        
	if(isAllCellsEnabled()) {
	    // Unused code currently
	    if (isForceEnabled() == false) {
		if (! promptForConfirm ("Shutdown all cells? [y/N]: ", 'N')) {
		    return EX_USERABORT;
		}
	    }
	    HCCell[] cells = getApi().getCells(false);
	    for(int curCell=0;curCell<cells.length;curCell++) {
		byte curCellId=cells[curCell].getCellId();            
		retCode=shutDown(cells[curCell], doCheat, doAll, doIpmi);
		if(retCode != EX_OK) 
		    return retCode;
	    }
	} else {

	    if (isForceEnabled() == false) {
		StringBuffer buf = new StringBuffer("Shutdown ");
                buf.append("cell ").append(getCellId());
		buf.append("? [y/N]: ");
		if (!promptForConfirm(buf.toString(), 'N')) {
		    return EX_USERABORT;
		}
	    }
	    return shutDown(getCell(), doCheat, doAll, doIpmi);
	}
        return EX_OK;
    }

    public int shutDown(HCCell cell,
                         boolean doCheat,
                         boolean doAll,
                         boolean ipmi) 
        throws PermissionException,ConnectException,MgmtException {

        //
        // General cluster shutdown
        //
        
        if (isForceEnabled() == false 
	    && (!getApi().isClusterSane(cell.getCellId()))) {  
            MessageFormat mf =
               new MessageFormat("It is not safe to shut down cell " +
               cell.getCellId()+".\n" +
               (getLocalString("cli.reboot.reasonPrompt")) );
            Object [] args = { "shut down" };
            if (! promptForConfirm(mf.format(args), 'N')) {
                return EX_USERABORT;
            }
        }
        getApi().powerOff(cell,doAll,ipmi);
        System.out.println("Exiting; cell is shut down.");
        System.exit(0);
        return EX_OK;
            

        
    }
}
