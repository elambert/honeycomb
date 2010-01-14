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
import java.text.MessageFormat;
import java.util.Date;
import java.math.BigInteger;

import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.AdmException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.admin.mgmt.client.HCDDTasks;


/**
 * Command-line tool that reports NDMP status information 
 * (tape-based disaster recovery).
 */
public class CommandNDMPStatus extends ShellCommand 
    implements ExitCodes {

    final static MessageFormat backupMsgFormat = 
        new MessageFormat ("NDMP status: {0}");
//     final static MessageFormat controlPortFormat = 
//         new MessageFormat ("NDMP control port: {0,number,integer}");
//     final static MessageFormat outboundDataPortFormat = 
//         new MessageFormat ("NDMP outbound data port: {0,number,integer}");
//     final static MessageFormat inboundDataPortFormat = 
//         new MessageFormat ("NDMP inbound data port: {0,number,integer}");
      

    private final Option _optIntvl;
    private final Option _optVerbose;

    public CommandNDMPStatus (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
        _optIntvl    = addOption (OPTION_INTEGER, 'i', "interval");
        _optVerbose  = addOption (OPTION_BOOLEAN, 'v', "verbose");
        addCellIdOption(true);
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {

        int intvl = getOptionValueInteger (_optIntvl);
        boolean verbose = getOptionValueBoolean (_optVerbose);

        int retCode = handleStandardOptions(argv, true);
        if (retCode != EX_CONTINUE) {
            return retCode;
        }
	
        if(intvl < 0) {
            System.out.println("Negative interval " + intvl + " is invalid");
            usage();
            return EX_USAGE;
        }	
        if(cellId < 0) {
            System.out.println("Cell ID " + cellId + " is invalid");
            usage();
            return EX_USAGE;
        }

        String[] unparsedArgs = getRemainingArgs();
        if (unparsedArgs.length > 0) {
            System.out.println("Unknown argument: " + unparsedArgs[0]);
            usage ();
            return EX_USAGE;
        }	

        try {
            return ndmpStatus(getApi().getCell(cellId), intvl, verbose);
        } catch (AdmException e) {
            printError("Internal adm error - failing.",e);
        }

        return EX_OK;

    }

    private int ndmpStatus(HCCell cell,
                           int intvl,
                           boolean verbose) 
        throws AdmException, ConnectException, MgmtException {
        AdminClient api = getApi();
        while (true) {
            try{
                if (verbose) {
                    // NYI
                } 
                 Object[] status = {api.getBackupStatus(cell.getCellId())};
                 System.out.println (backupMsgFormat.format (status));

//                 Object[] controlPort = {api.getBackupControlPort(cell.getCellId())};
//                 System.out.println (backupMsgFormat.format (controlPort));

//                 Object[] inboundDataPort = {api.getBackupInboundDataPort(cell.getCellId())};
//                 System.out.println (inboundDataPortFormat.format (inboundDataPort));

//                 Object[] outboundDataPort = {api.getBackupOutboundDataPort(cell.getCellId())};
//                 System.out.println (outboundDataPortFormat.format (outboundDataPort));

            }
            catch (MgmtException ce) {
                System.out.println (ce.getMessage());
                return EX_TEMPFAIL;
            }


            if (intvl == 0) {
                return EX_OK;
            }

            try {
                Thread.sleep (intvl * 1000);
            } catch (InterruptedException ie) {
                return EX_OSERR;
            }
        }
    }

//     private int setProceedAfterError(byte cellId, boolean newVal) 
//         throws AdmException, ConnectException, MgmtException {
//         getApi().setProceedAfterError(cellId, newVal);
//         return EX_OK;
//     }


}
