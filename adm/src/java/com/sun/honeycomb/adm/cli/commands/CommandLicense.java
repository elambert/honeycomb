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
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;



import java.util.Properties;

public class CommandLicense extends ShellCommand 
implements ExitCodes {


    /**********************************************************************/
    public CommandLicense(String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
	addCellIdOption(true);
	// addAllCellsOption();
    }

    /**********************************************************************/
    public int main(Properties env, String[] argv) throws MgmtException, ConnectException, PermissionException {
	
	int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}

	String[] args = getRemainingArgs();
	if (args.length > 1) {
	    System.out.println("Unknown argument: " + args[1]);
	    usage ();
	    return EX_USAGE;
	}

        boolean multiCell = isMultiCell();
        if (isAllCellsEnabled()) {                

	    HCCell[] cells = getApi().getCells(false);
            for(int curCell=0;curCell<cells.length;curCell++) {
                byte curCellId=cells[curCell].getCellId();            
                if (args == null || args.length == 0) {
                    // display
                    showLicense(cells[curCellId],curCellId,multiCell);
                } else if (args.length == 1) {
                    
                    System.out.println("Setting license to: " + args[0] + " on cell " +curCellId);
                    getApi().setLicense(curCellId,args[0]);                
                }
            }
        } else {          
	    if (args == null || args.length == 0) {
                showLicense(getCell(),getCellId(),multiCell);           
            }
	    else
	    {
                System.out.println("Setting license to: " + args[0]);
                getApi().setLicense(getCellId(),args[0]);     
	    }
        }


        return EX_OK;
    }


    private void showLicense(HCCell cell,byte curCellId,boolean multiCell) throws MgmtException, ConnectException {

        String license = cell.getLicense();
        if (license == null || license.length() < 1) {
            license = getLocalString("cli.license.not_set");
        }
        if(multiCell) {
            System.out.println("Cell " + curCellId+":");                
        }
        System.out.println("    License: "+license);

    }
}


