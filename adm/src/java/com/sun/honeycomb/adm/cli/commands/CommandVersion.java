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
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCVersions;
import java.util.Iterator;
import java.util.Properties;

/**
 * Command-line tool that reports system status information
 */
public class CommandVersion extends ShellCommand 
implements ExitCodes {

    private final Option _optVerbose;

    public CommandVersion (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);

        _optVerbose = addOption (OPTION_BOOLEAN, 'v', "verbose");
	 addCellIdOption(true);
	 // addAllOption();
    }

    public int main (Properties env, String[] argv) 
        throws MgmtException, PermissionException, ConnectException{
        boolean verbose = false;
	
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

	verbose = getOptionValueBoolean (_optVerbose);

        if(isAllCellsEnabled()) {
	    HCCell[] cells = getApi().getCells(false);
            for(byte curCell=0;curCell<cells.length;curCell++) {
                printVersions(getApi().getVersions(curCell),verbose);
          
            }
        } else {
            printVersions(getApi().getVersions(cellId),verbose);
        }

        return EX_OK;
    }

    private void printVersions(HCVersions versions,boolean verbose) {
        
        System.out.println (versions.getVersion());
        // bvb todo:  later will want to get the build number and display it when
        // the user specifies a new option of some sort.  
        //System.out.println(getLocalString("common.productname"));

        if (verbose) {                
            System.out.println("Service Node:");
            System.out.println("\tBIOS Version: "
                               + versions.getSpBios());
            System.out.println("\tSMDC Version: "
                               + versions.getSpSmdc());
            System.out.println("Switch:");
            System.out.println("\tOverlay Version (sw#1): "
                               + versions.getSwitchOneOverlay());
            System.out.print("\tOverlay Version (sw#2): ");
            String swTwo = versions.getSwitchTwoOverlay(); 
            if(null==swTwo) {
                System.out.println ("not available");
            } else {
                System.out.println (swTwo);
            }

            Iterator biosIter = versions.getBios().iterator();
            Iterator smdcIter = versions.getSmdc().iterator();
            int i=101;
            while (biosIter.hasNext()) {
                System.out.println(getNodeName(i) + ":");
                i++;
                String biosVersion=null;
                try {
                     biosVersion = (String) (biosIter.next());
                }  catch (Exception e) {
                     biosVersion = "Unavailable";
                }
                if ((null == biosVersion) || (0 == biosVersion.length())) {
                     biosVersion = "Unavailable";
                }
                System.out.println("\tBIOS version: " + biosVersion);
                String smdcVersion = null;
                try {
                     smdcVersion = (String) (smdcIter.next());

                }  catch (Exception e) {
                     smdcVersion = "Unavailable";
                }
                if ((null == smdcVersion) || (0 == smdcVersion.length())) {
                     smdcVersion = "Unavailable";
                }

                System.out.println("\tSMDC version: " + smdcVersion);
            }


        }
    }
}

