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
import com.sun.honeycomb.adm.cli.parser.OptionException;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.CliException;
import java.util.Properties;


public class CommandHadb
    extends ShellCommand 
    implements ExitCodes {

    public CommandHadb(String name, String[] aliases, Boolean isHidden) {
        super(name, aliases, isHidden);
	addCellIdOption(true);
    }
    
    public int main (Properties env, String[] argv)
        throws MgmtException, PermissionException, ConnectException {
        
        boolean interactive = false;

        int retCode = handleStandardOptions(argv, false);
        if (retCode != EX_CONTINUE) {
            return retCode;
        }

        String[] unparsedArgs = getRemainingArgs();
        if (unparsedArgs.length > 0) {
            if (unparsedArgs[0].equals("status")) {
                System.out.println(getApi().getHadbStatus(cellId));
                return EX_OK;
            } else if (unparsedArgs[0].equals("clear")) {
                //
                // multicell
                //
                getInternalApi().clearHADBFailure(cellId);

                return EX_OK;
            } else if (unparsedArgs[0].equals("details")) {
                System.out.println("HADB Status: "+
                                   getApi().getHadbStatus(cellId));
                int state = (int)(getApi().getCacheStatus(cellId).longValue());
                long lastCreateTime = getApi().getLastHADBCreateTime(cellId);
                String stateString;
                String stateStrings[] = {
                    "Unknown",
                    "Failed",
                    "Connecting",
                    "Initializing",
                    "Upgrading",
                    "Setting Up",
                    "Running" };
                if (state < 0 || state > stateStrings.length) {
                    stateString = "Unknown State ("+state+")";
                } else {
                    stateString = stateStrings[state];
                }
                System.out.println("State Machine Status: "+ stateString);
                
                //Print last HADB Create Time
                //FIXME: use a MessageFormat here
                if (lastCreateTime <= 0) {
                    System.out.println("HADB currently wiped");
                } else {
                    System.out.println("HADB Database last created at "+
                                       new java.util.Date(lastCreateTime));
                }
                return EX_OK;
            } // if/else/elseif 
        } // if

        if (unparsedArgs.length > 0)
            System.out.println("Unknown argument: " + unparsedArgs[0]);
        usage();
        return(EX_USAGE);
    }
                                                      

}
