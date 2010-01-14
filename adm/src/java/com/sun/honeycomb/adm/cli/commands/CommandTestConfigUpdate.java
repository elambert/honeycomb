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

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.client.AdminClientInternal;


public class CommandTestConfigUpdate extends ShellCommand 
implements ExitCodes {

    private final Option optAdd;
    private final Option optDelete;
    private final Option optExecutorId;
    private final Option optLatency;
    private final Option optCreateInterface;
    private final Option optNodeFailure;
    private final Option optRateFailure;
    
    
    public CommandTestConfigUpdate (String name, 
      String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
	addCellIdOption(true);
        optAdd = addOption (OPTION_BOOLEAN, 'n', "new");
        optDelete = addOption (OPTION_BOOLEAN, 'r', "remove");
        optExecutorId = addOption (OPTION_INTEGER, 'i', "executor_id");
        optLatency = addOption (OPTION_INTEGER, 'l', "latency");
        optNodeFailure = addOption (OPTION_BOOLEAN, 'f', "failure");
        optRateFailure = addOption (OPTION_INTEGER, 'p', "percent_failure");
        optCreateInterface = 
          addOption (OPTION_BOOLEAN, 'k', "create_interface");
    }

    public int main (Properties env, String[] argv) 
        throws MgmtException, PermissionException, ConnectException {

        boolean cmdAdd = false;
        boolean cmdDelete = false;
        int execId = 0;
        long latency = 0;
        boolean createInterface = false;
        boolean nodeFailure = false;
        int rateFailure = 0;

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

	cmdAdd = getOptionValueBoolean(optAdd);
	cmdDelete = getOptionValueBoolean(optDelete);
	execId = getOptionValueInteger(optExecutorId);
	latency = getOptionValueInteger(optLatency);
	System.err.println("(1) createInterface = " + createInterface);
	createInterface = getOptionValueBoolean(optCreateInterface);
	System.err.println("(2) createInterface = " + createInterface);
	System.err.println("(1) nodeFailure = " + nodeFailure);
	nodeFailure = getOptionValueBoolean(optNodeFailure);
	System.err.println("(2) nodeFailure = " + nodeFailure);
	rateFailure = getOptionValueInteger(optRateFailure);
	
	
        boolean s = 
          sanityCheck(cmdAdd, cmdDelete, execId, latency, createInterface,
            nodeFailure, rateFailure);
        if (!s) {
            return EX_USAGE;
        }
            

        if (cmdAdd) {
            newConfigUpdateExecutor(latency, createInterface, 
              nodeFailure, rateFailure);
        }
        
        if (cmdDelete) {
            removeExistingConfigUpdateExecutor(execId);
        }
        return EX_OK;
    }

    private boolean sanityCheck(boolean cmdAdd,
      boolean cmdDelete, int execId, long latency,
      boolean createInterface, boolean nodeFailure, int rateFailure) {
        return true;
    }

    private void newConfigUpdateExecutor(long latency,
      boolean createInterface, boolean nodeFailure, int rateFailure)
        throws MgmtException, ConnectException, PermissionException {

        AdminClientInternal internalApi = getInternalApi();
        int res = internalApi.startNewExecutor(cellId, latency,
          createInterface, nodeFailure, rateFailure);
        if (res > 0) {
            System.out.println("Started successfully executor, id = " + res);
        } else {
            System.out.println("Failed to start new executor.");
        }
        System.out.flush();
    }

    private void  removeExistingConfigUpdateExecutor(int execId)
        throws MgmtException, ConnectException, PermissionException {
        AdminClientInternal internalApi = getInternalApi();
        String status = internalApi.stopExistingExecutor(cellId, execId);
        System.out.println(status);
        System.out.flush();
    }
}
