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
import java.io.FileInputStream;
import java.io.IOException;
import com.sun.honeycomb.adm.cli.parser.Option;

import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.PermissionException;

public class CommandProfiler extends ShellCommand
implements ExitCodes {


        
    private final Option _optModule;
    private final Option _optHowLong;

    public CommandProfiler (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
        _optModule = addOption (OPTION_STRING, 'm', "module");
        _optHowLong = addOption (OPTION_INTEGER, 't', "howlong");
	addCellIdOption(true);
	addNodeOption(false);
    }
    
    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        String module  = null;
        int    howlong = 0;
        String args[]  = null;
	
	int retCode = handleStandardOptions(argv, false);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}

	module = getOptionValueString (_optModule);
	howlong = getOptionValueInteger (_optHowLong);
	
	args = getRemainingArgs();
	if (args.length == 0) {
	    usage ();
	    return EX_USAGE;
	}
	
	if (args.length > 1) {
	    System.out.println("Unknown argument: " + args[1]);
	    usage ();
	    return EX_USAGE;
	}
        
        if (module == null) {
            module = "all";
        }            
        
        try {
            if (args[0].equals(CMD_START)) {            

                getInternalApi().profilerStart(module, getCellId(), getNodeId(), howlong);
                
            } else if (args[0].equals(CMD_STOP)) {
                
                getInternalApi().profilerStop(getCellId());
                
            } else if (args[0].equals(CMD_GET)) {
                String tarFile = getInternalApi().profilerTarResult(getCellId());
                if (tarFile != null) {
                    FileInputStream in = null;
                    try {
                        in = new FileInputStream(tarFile);
                        byte [] buf = new byte[2048];
                        int res;
                        while ((res = in.read(buf, 0, buf.length)) > 0) {
                            System.out.write(buf, 0, res);
                        }
                    } catch (IOException ioe) {
                        System.out.println("Failed to create tar: " + ioe.getMessage());
                        return EX_SOFTWARE;

                    } finally {
                        if (in != null) {
                            try { 
                                in.close(); 
                            } catch (IOException ignore) {}                            
                        }
                    }
                }
            } else {
                usage();
                return EX_USAGE;
            }
            return EX_OK;
            
        } catch (MgmtException ce) {
            System.out.println("Command failed: " + ce.getMessage());
            return EX_SOFTWARE;
        }
    }
    
    private String listAvailableModules()  throws MgmtException, PermissionException, ConnectException {
    
        return getInternalApi().listAvailableModules(cellId);
    }
    
    // profiler commands
    private static final String CMD_START = "start";
    private static final String CMD_STOP  = "stop";
    private static final String CMD_GET   = "getTar";
}
