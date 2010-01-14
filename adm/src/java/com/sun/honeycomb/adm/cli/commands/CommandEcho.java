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


package com.sun.honeycomb.adm.cli.commands; import com.sun.honeycomb.adm.cli.PermissionException;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.client.*;
import java.util.Properties;

public class CommandEcho extends ShellCommand 
implements ExitCodes {
    private static final String SPACE = " ";
    private static final String WS_TOKEN = "\\s";
    private static final char   ENVVAR_TOKEN = '$';

    public CommandEcho (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
    
        try {
            Thread.currentThread().sleep(1000*60*10); // 10 minutes
        } catch (Exception e) {
            System.out.println (e.getMessage());
            e.printStackTrace();
        }

        // Echo supports arguments that may be quoted. These arguments
        // must be broken down into constituent parts and checked for
        // substitution variables
        if (argv == null || argv.length == 0) {
            System.out.println ();
            return EX_OK;
        }

        for (int i = 0; i < argv.length; i++) {

            String[] argParts = argv[i].split (WS_TOKEN);

            if (argParts == null || argParts.length == 0) {
                continue;
            }

            if (i > 0) {
                System.out.print (SPACE); 
            }

            for (int j = 0; j < argParts.length; j++) {
                if (j > 0) { 
                    System.out.print (SPACE); 
                }

                if (argParts[j].charAt(0) == ENVVAR_TOKEN) {
                    String value = env.getProperty (argParts[i].substring (1));
                    if (value != null) {
                        System.out.print (value);
                    }
                }
                else {
                    System.out.print (argParts[j]);
                }
            }

            if (i == argv.length - 1) {
                System.out.println ();
            }
        }

        return EX_OK;
    }
}
