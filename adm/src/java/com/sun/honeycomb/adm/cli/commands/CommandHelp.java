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
import java.util.Properties;

import com.sun.honeycomb.adm.cli.ShellCommandManager;
import com.sun.honeycomb.adm.cli.IShellCommand;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.CommandNotFoundException;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;

public class CommandHelp extends ShellCommand 
implements ExitCodes {

    public CommandHelp (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
	
	int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}

	String[] unparsedArgs = getRemainingArgs();
	if (unparsedArgs == null || unparsedArgs.length == 0) {
	    help();
	    return EX_OK;
	}
	if (unparsedArgs.length > 1) {
	    System.out.println("Unknown argument: " + unparsedArgs[1]);
	    usage ();
	    return EX_USAGE;
	}

	ShellCommandManager commands = getManager();
	IShellCommand cmd;
	try {
	    cmd = (IShellCommand) commands.get (unparsedArgs[0]);
	}
	catch (CommandNotFoundException cnfe) {
	    System.out.println (unparsedArgs[0] + ": "
		    + getLocalString ("cli.command_not_found")); 
	    help();
	    return EX_DATAERR;
	}

	cmd.usage ();
        return EX_OK;
    }

    public void help () {
        new ShowHelp(getManager());
    }
}
