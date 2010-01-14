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

import java.text.MessageFormat;
import java.util.Properties;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.AdmException;
import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.admin.mgmt.client.HCCell;


public class CommandRebootCluster extends ShellCommand
implements ExitCodes {

    


    private final boolean VERBOSE = false;

    private final long DELAY = 1000; // 1s

    private final Option _optAll; // Reboot nodes and switches


    public CommandRebootCluster (String name,
                                 String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
	addForceOption();
	addCellIdOption(true);
        _optAll = addOption (OPTION_BOOLEAN, 'A', "all");
	addNodeOption(true);				    // Hidden
	// addAllCellsOption();    // NOTE: possible --all option conflict HERE
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        boolean rebootAll = false;
	
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
	
	rebootAll = getOptionValueBoolean (_optAll);

        if(isForceEnabled() == false) {
            if (! promptForConfirm ("Reboot? [y/N]: ", 'N')) {
                return EX_USERABORT;
            }
        }
        if (isAllCellsEnabled()) {
            HCCell[] cells = getApi().getCells(false);
            for(int i=0;i<cells.length;i++) {
                int curCell=cells[i].getCellId();

                retCode=EX_OK;
                cellId=cells[i].getCellId();     
		int nodeid = getNodeId();
                if (nodeid != -1) {
		    // Sun Service Only Options
		    if (isHiddenOptionProceed() == false) {
			return EX_USERABORT;
		    }
                    getApi().rebootNode(cellId, nodeid);
                } else {                    
                    retCode=rebootCell(cells[i],rebootAll,rebootAll);
                }
                if(retCode != EX_OK) 
                    return retCode;
            }
            System.out.println("Exiting; hive is rebooting.");
            System.exit(0);
        } else {
            int retcode=rebootCell(getApi().getCell(cellId),rebootAll,rebootAll);
            if(retcode == EX_OK) {
                System.out.println("Exiting; cell ["+cellId+"] is rebooting.");
                System.exit(0);
            } else if (retcode != EX_USERABORT) {
                System.out.println("Reboot failed.");
            }
        }

        return EX_OK;

    }


    // Reboot the cell
    public int rebootCell(HCCell cell,boolean switches, boolean sp)
        throws MgmtException ,ConnectException,PermissionException {

        AdminClient api = getApi();
        if(isForceEnabled() == false) {
            if(!getApi().isClusterSane(cell.getCellId())) {
               MessageFormat mf =
                   new MessageFormat("It is not safe to reboot cell " +
                   cell.getCellId()+".\n" +
                   (getLocalString("cli.reboot.reasonPrompt")) );
                Object [] args = { "reboot" };
                if (! promptForConfirm(mf.format(args), 'N')) {
                    return EX_USERABORT;
                }
            }
        }
        try {
            getApi().rebootCell(cell, switches,sp);                
            return EX_OK;

        } catch (MgmtException me) {
            throw me;
        } catch (Exception re) {
            printError("Error during cell reboot, canceling command:" , re);
            return EX_TEMPFAIL;
        }


    }
}
