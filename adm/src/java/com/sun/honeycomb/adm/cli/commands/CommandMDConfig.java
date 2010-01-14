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
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.common.CliConstants;

import java.util.Properties;
import java.io.IOException;


public class CommandMDConfig extends ShellCommand 
implements ExitCodes {
    

    private final Option _optCommit;
    private final Option _optDump;
    private final Option _optTemplate;
    private final Option _optWipe;
    private final Option _optRetry;
    private final Option _optValidate;

    public CommandMDConfig(String name, String[] aliases, Boolean isHidden) {
        super(name, aliases, isHidden);

        _optCommit  = addOption (OPTION_BOOLEAN, 
                                 getLocalString("cli.mdconfig.commit_char").charAt(0), 
                                 getLocalString("cli.mdconfig.commit_name"));
        _optDump  = addOption (OPTION_BOOLEAN, 
                                 getLocalString("cli.mdconfig.dump_char").charAt(0), 
                                 getLocalString("cli.mdconfig.dump_name"));
        _optTemplate  = addOption (OPTION_BOOLEAN,
                                 getLocalString("cli.mdconfig.template_char").charAt(0), 
                                 getLocalString("cli.mdconfig.template_name"));
        _optWipe  = addOption (OPTION_BOOLEAN, 
                                 getLocalString("cli.mdconfig.wipe_char").charAt(0), 
                                 getLocalString("cli.mdconfig.wipe_name"));
        _optRetry  = addOption (OPTION_BOOLEAN, 
                                 getLocalString("cli.mdconfig.retry_char").charAt(0), 
                                 getLocalString("cli.mdconfig.retry_name"));
        _optValidate  = addOption (OPTION_BOOLEAN, 
                                 getLocalString("cli.mdconfig.validate_char").charAt(0), 
                                 getLocalString("cli.mdconfig.validate_name"));
	addForceOption();
    }

    public int main (Properties env, String[] argv)
        throws MgmtException, PermissionException, ConnectException {

        boolean commit = false;
        boolean dump = false;
        boolean template = false;
        boolean wipe = false;
        boolean retry = false;
        boolean validate = false;

        int retCode = handleStandardOptions(argv, false);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}

	String[] unparsedArgs = getRemainingArgs();
	if (unparsedArgs.length > 0) {
	    System.out.println("Unknown argument: " + unparsedArgs[0]);
	    usage ();
	    return EX_USAGE;
	}

	commit = getOptionValueBoolean(_optCommit);
	dump = getOptionValueBoolean(_optDump);
	template = getOptionValueBoolean(_optTemplate);
	wipe = getOptionValueBoolean(_optWipe);
	retry = getOptionValueBoolean(_optRetry);
	validate = getOptionValueBoolean(_optValidate);

        if ((commit&&dump) || (commit&&template) || (dump&&template) || 
            retry && (commit || dump || template || wipe)) {
            usage();
            return(EX_USAGE);
        }
        
        int res;
        if (wipe) {
	    // Sun Service Only Option
	    if (isHiddenOptionProceed() == false) {
		return EX_USERABORT;
	    }
            res = getApi().clearSchema();
        } else if (retry) {
            res = getApi().retrySchema();
        } else {
            try {
                if (dump) {
                    res = getApi().getSchema(System.out, false);
                } else if (template) {
                    res = getApi().getSchema(System.out, true);
                } else if (!commit) {
                    res = getApi().pushSchema(System.in, true);
                } else {
                    res = getApi().pushSchema(System.in, false);
                }
            } catch (IOException ioe) {
                System.out.println("IO exception while reading the " +
                                   " schema, retry the operation...");
                return(EX_IOERR);
            }
        }
        switch (res) {
        case CliConstants.MGMT_OK:
            if (wipe) {
                System.out.println("The schema has been wiped.");
                System.out.flush();
            } else if (retry) {
                System.out.println("The schema has been updated.");
                System.out.flush();
            } else if (commit) {
                System.out.println("The schema has been committed.");
                System.out.flush();                    
            } else if (!commit && !template && !dump) {
                System.out.println("The schema has been validated.");
                System.out.flush();                    
            }
            return (EX_OK);

        case CliConstants.MGMT_SCHEMA_UPLOAD_FAILED:
            System.out.println("Failed to upload the schema.");
            System.out.flush();
            return (EX_UNAVAILABLE);

        case CliConstants.MGMT_SCHEMA_VERIFICATION_FAILED:
            System.out.println("Failed to validate the schema.");
            System.out.flush();
            return (EX_CONFIG);
            
        case CliConstants.MGMT_CMM_CONFIG_UPDATE_FAILED:
            System.out.println("Failed to propagate the config on all " +
              "cells.");
            System.out.flush();
            return (EX_UNAVAILABLE);

        case CliConstants.MGMT_HADB_LOAD_SCHEMA_FAILED:
            System.out.println("CAUTION: New schema changes have not been " +
                "activated.\nWait for Query Engine to become " +
                "(HA)FaultTolerant,\nthen try 'mdconfig -r' to activate " +
                "schema changes");
            return (EX_UNAVAILABLE);

        case  CliConstants.MGMT_CANT_RETRIEVE_SCHEMA:
            System.out.println("Internal error while retrieving schema.");
            System.out.flush();
            return (EX_UNAVAILABLE);

        case  CliConstants.MGMT_CLEAR_SCHEMA_FAILED:
            System.out.println("Failed to wipe the schema.");
            System.out.flush();
            return (EX_UNAVAILABLE);

        case  CliConstants.MGMT_RETRY_SCHEMA_FAILED:
            System.out.println("CAUTION: Timed out while trying to activate " +
                "new schema.\nWait for Query Engine to become " +
                "(HA)FaultTolerant,\nthen try 'mdconfig -r' to activate " +
                "schema changes.");
            System.out.flush();
            return (EX_UNAVAILABLE);

        case  CliConstants.MGMT_FAILED_TO_CONNECT:
            System.out.println("");
            System.out.flush();
            return (EX_UNAVAILABLE);

        default:
            return (EX_SOFTWARE);
        }
    }
}
