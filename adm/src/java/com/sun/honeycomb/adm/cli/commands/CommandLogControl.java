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

import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.client.*;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;



public class CommandLogControl extends ShellCommand 
implements ExitCodes {

    private final Option _optJVM;
    private final Option _optLevel;
    public static final String[] LOG_LEVELS = {
        "OFF", "FINEST", "FINE", "INFO", "WARNING", "SEVERE", "FATAL" };


    public CommandLogControl (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
        _optJVM   = addOption (OPTION_STRING, 's', "service");
        _optLevel = addOption (OPTION_STRING, 'l', "level");
	addCellIdOption(true);
	addNodeOption(false);
    }

    public int main (Properties env, String[] argv) 
        throws MgmtException, PermissionException, ConnectException{
        
        String jvm    = null;
        String level  = null;

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

	jvm    = getOptionValueString (_optJVM);
	level  = getOptionValueString (_optLevel);

        if (level == null) {
            // display the logger states
            return displayLogLevel (getNodeId(), jvm);
        }

        return setLogLevel (cellId, getNodeId(), jvm, level);
    }

    /**
     * Currently displays the known JVMs that exist in the hive
     */
    public int displayLogLevel (int nodeid, String jvm) throws MgmtException, ConnectException{
        String  levels[]=getInternalApi().getLogLevels(cellId);
        String thisNode=levels[nodeid];
        System.out.println("Available JVMs: " + thisNode);
        //        List<String> getListFromCsv
        return EX_OK;
    }

    public int setLogLevel (byte cellId, int nodeid, String jvm, String level) 
        throws MgmtException, ConnectException, PermissionException {
        AdminClientInternal internalApi = getInternalApi();
        
        int iLvl = -1;
        for (int i = 0; i < LOG_LEVELS.length; i++) {
            if (LOG_LEVELS[i].equalsIgnoreCase (level)) {
                iLvl = i;
            }
        }

        if (iLvl < 0) {
            System.out.println ("Invalid log level: " + level);
            return EX_DATAERR;
        }


        internalApi.setLogLevel (cellId, nodeid, jvm, iLvl);


        return EX_OK;
    }
}
