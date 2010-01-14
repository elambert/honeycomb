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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.sun.honeycomb.common.HttpPassword;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.client.*;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.md5.MD5Crypt;
import com.sun.honeycomb.adm.cli.editline.Editline;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;

public class CommandProtocolPassword extends ShellCommand 
implements ExitCodes {


    private Option _optRealm;
    private Option _optUser;
    public CommandProtocolPassword (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
        _optRealm  = addOption (OPTION_STRING,'r', "realm");
        _optUser  = addOption (OPTION_STRING,'u', "user");
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        String[] unparsed = null;
        String realm=null;
        String user=null;
        String  secret    = null;
        
       if(!getApi().loggedIn())
            throw new PermissionException();

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
	
	if (isInteractive() == false) {
	    System.out.println (
		"Use interactive mode to change the protocolpassword.");
                return EX_DATAERR;
	}
	
	realm = getOptionValueString (_optRealm);
	user = getOptionValueString (_optUser);
	
	if (realm == null && user == null) {
	    usage();
	    return EX_TEMPFAIL;
	}
        if(null==realm) {
            System.out.println("Specify a realm with -r.");
            usage();
            return EX_TEMPFAIL;
        }
	
        if(null==user) {
            System.out.println("Specify a user with -u.");
            usage();
            return EX_TEMPFAIL;
        }
	
	String prompt = null;
	secret = getPasswd(user); 
	if (secret == null)
	    return EX_DATAERR;
        
        AdminClient api = getApi();
        
        byte[] pass = HttpPassword.makeHash(realm, user, secret);
	
	try {
	    int retcode = api.setProtocolPassword(realm,user,HttpPassword.makeHash(realm, user, secret));

	    if (retcode != CliConstants.SUCCESS) {
		System.out.println("Failed to set the password for " + user + ".");
		return EX_TEMPFAIL;
	    }

	    System.out.println ("The password for user "+user+" has been changed successfully.");
	    return EX_OK;
	}
	catch (MgmtException me) {
		System.out.println("Failed to set the password for " + user + ".");
		System.out.println(me.getMessage());
		return EX_TEMPFAIL;
	}
    }



    private String getPasswd (String user) {
        String password = null;
        BufferedReader in = null;
        AdminClient api = getApi();
        try {
            in = new BufferedReader (new InputStreamReader (System.in));

            String p1, p2, curr;
            Editline.disableEcho();

            while (true) {
                System.out.print ("New password for " + user + ": ");
                p1 = in.readLine(); // TODO: we should really read into a byte[]
                System.out.println();
                System.out.print ("Re-enter new password for " + user + ": ");
                p2 = in.readLine(); // TODO: we should really read into a byte[]
                System.out.println();

                if (! (p1.equals (p2))) {
                    System.out.println ("Passwords did not match.");
                    continue;
                }
                else if (p1 == null || p1.length() == 0) {
                    System.out.println ("You must provide a password.");
                    continue;
                }
                else if (p1.length() < 6) {
                    System.out.println (
                        "Passwords must contain at least 6 characters.");
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

	if (password == null) {
            System.out.println ("Invalid password: null");
	}
        return password;
    }

}
