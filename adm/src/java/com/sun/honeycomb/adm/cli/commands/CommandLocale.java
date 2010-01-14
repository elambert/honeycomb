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
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;

import com.sun.honeycomb.adm.client.*;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.PermissionException;

public class CommandLocale extends ShellCommand 
implements ExitCodes {



    private Option _optSetLanguage;
    private Option _optListLanguages;

    public CommandLocale (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);

        _optSetLanguage  = addOption (OPTION_STRING,'s', "set");
        _optListLanguages= addOption (OPTION_BOOLEAN,'l',"list");

    }

    public int main (Properties env, String[] argv) throws 
        MgmtException, ConnectException, PermissionException {
        AdminClient api = getApi();
        boolean doSet=false;
        boolean listLanguages=false;
        String  locale    = null;

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

	if(getOptionValueBoolean (_optListLanguages)) {
	    System.out.println("Supported languages: " + getLanguages());
	}
	locale = getOptionValueString (_optSetLanguage);
	if(null!=locale) {
	    doSet=true;
	}
        
        if(!doSet) {
            System.out.println("Language is currently set to: " +
                               api.getLocale());

            return EX_OK;
        }


        String languages[] = api.getLanguages();
        boolean found=false;
        for (int i=0;i<languages.length && false==found;i++) {            
            if(locale.equals(languages[i])) 
                found=true;                
        }
        if(!found) {
            System.out.println("Language " + locale + " not installed on this system.");
            System.out.println("Supported languages: "+getLanguages());
            return EX_TEMPFAIL;
        }

        try {
            api.setLocale(locale);

        } catch (MgmtException e) {
            System.out.println("Invalid language code entered, or language not supported.");
            return EX_CONFIG;
            
        } catch (PermissionException e) {
            System.out.println("Not currently logged in as administrator, or timed out.");
        } 
        
        System.out.println("Hive reboot is required for command to take effect.");

        return EX_OK;
    }
    String getLanguages()  throws       MgmtException, ConnectException {
        String retString= new String();
        String languages[] = getApi().getLanguages();
        if(languages != null) {
            for (int i=0;i<languages.length ;i++) {            
                retString+=languages[i];
                if(i+1 < languages.length) 
                    retString+=",";
                
            }
        } else {
            retString="No languages installed.";
        }
        return retString;
    }


}
