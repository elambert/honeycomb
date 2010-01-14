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
import java.text.MessageFormat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.client.*;
import com.sun.honeycomb.common.md5.MD5Crypt;
import com.sun.honeycomb.common.md5.MD5Check;
import com.sun.honeycomb.adm.cli.editline.Editline;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;

public class CommandPassword extends ShellCommand 
implements ExitCodes {


    private final Option _optPubkey;
    private final Option _optTestPasswd;

    public CommandPassword (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
        _optPubkey = addOption (OPTION_BOOLEAN, 'K', "pubkey");
        _optTestPasswd = addOption (OPTION_BOOLEAN, 't', "test");
	addForceOption();
    }

    public int main (Properties env, String[] argv) 
        throws MgmtException, PermissionException, ConnectException {
        String[] unparsed = null;
        boolean isPubkey  = false;
        boolean testMode  = false;
        String  secret    = null;
	
	if(!getApi().loggedIn())
            throw new PermissionException();

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

	isPubkey = getOptionValueBoolean (_optPubkey);
	testMode = getOptionValueBoolean (_optTestPasswd);
	unparsed = getRemainingArgs();
	
	if (testMode) {
	    // Test only option.  Treat as Sun Service Only options
	    if (isHiddenOptionProceed() == false) {
		return EX_USERABORT;
	    }
	}
	
        if (isInteractive() || testMode) {
	    if (isPubkey) {
		System.out.println (
		    "Use noninteractive mode to enter your public key.");
                return EX_DATAERR;
            }

            secret = getPasswd();
        }
        else {
	    if (!isPubkey) {
		// We don't allow changing the password in non interactive
		// mode since the password is being echoed out.  If
		// we fix this then this requirement can be removed.
		System.out.println (
		    "Use interactive mode to change the password.");
                return EX_DATAERR;
            }

            secret = getPubkey();
        }


        
        if ( secret == null) {
	    // no need to generate an error message getPasswd()
	    // has already generated one for us
            return EX_DATAERR;
        }

        AdminClient api = getApi();

        if (isPubkey) { 
            api.setPublicKey (secret);
        } else {
            api.setPasswd(secret);
        }




        String str = getLocalString("cli.alert.passwd");
        String msg = null;
        if (isPubkey) {
            Object [] arglog =  {"public key"};
            msg = MessageFormat.format(str, arglog);
        } else {
            Object [] arglog = {"admin password"};
            msg = MessageFormat.format(str, arglog);
        }

        System.out.println (msg);
        return EX_OK;
    }

    /** reads apubkey from stdin */
    private String getPubkey () {
        String pubkey = null;
        BufferedReader in = null;

        try {
            in = new BufferedReader (new InputStreamReader (System.in));
            pubkey = in.readLine();
        }
        catch (Exception e) {
            System.out.println ("Error: " + e.getMessage());
        }

        return pubkey;
    }



    private String getPasswd () {
        String password = null;
        BufferedReader in = null;
        AdminClient api = getApi();
        try {
            in = new BufferedReader (new InputStreamReader (System.in));

            String p1, p2, curr;
            Editline.disableEcho();
            System.out.print("Enter current password: " );
            curr=in.readLine();
            System.out.println();
            String systemPasswd=api.getCryptedPasswd();            


            if(!MD5Check.check(systemPasswd,curr)) {
                System.out.println("Password entered is incorrect.");
                return null;
            }



            while (true) {
                System.out.print ("Enter new password: ");
                p1 = in.readLine();
                System.out.println();
                System.out.print ("Re-enter new password: ");
                p2 = in.readLine();
                System.out.println();

                if (! (p1.equals (p2))) {
                    System.out.println ("Passwords did not match.");
                    continue;
                }
                else if (p1 == null || p1.length() == 0) {
                    System.out.println ("You must enter a password.");
                    continue;
                }
                else if (p1.length() < 5) {
                    System.out.println (
                        "Passwords must contain at least 5 characters.");
                    continue;
                }
                else if (p1.length() > 32) {
                    System.out.println (
                        "Passwords may contain no more than 32 characters.");
                    continue;
                }

                Editline.enableEcho();
                password = MD5Crypt.crypt (p1);

                break;
            }
        } 
        catch (Exception e) {
            System.out.println ("Error: " + e.getMessage());
        }

        return password;
    }

}
