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
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.admin.mgmt.client.HCSiloProps;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;

import java.util.Iterator;
import java.util.Properties;

public class CommandHiveConfig extends ValueSetter {
    

    private static final String[][] HIVE_CONFIG_OPTIONS= {
        {"NTP Server", NTP_SERVER_KEY, "NtpServer", "n"},
        {"SMTP Server",SMTP_SERVER_KEY, "SmtpServer", "s"},
        {"SMTP Port",SMTP_SERVER_PORT_KEY, "SmtpPort", "p"},
        {"Authorized Clients",AUTH_CLIENT_KEY, "AuthorizedClients", "h"},
        {"External Logger",EXT_LOGGER_KEY, "ExtLogger", "x"},
        {"DNS", DNS_ENABLE_KEY, "Dns", "D"},
        {"Domain Name", DOMAIN_NAME_KEY, "DomainName","m"},
        {"DNS Search", DNS_SEARCH_KEY, "DnsSearch","e"},
        {"Primary DNS Server", PRIMARY_DNS_SERVER_KEY, "PrimaryDnsServer","1"},
        {"Secondary DNS Server", SECONDARY_DNS_SERVER_KEY, "SecondaryDnsServer","2"}
    };
    
    private final Option _optSetInteractive;
    private final Option _optNoNTPValidate;
    boolean noNtpValidation = false;

    public CommandHiveConfig (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden,  HIVE_CONFIG_OPTIONS);       
        _optSetInteractive = addOption (OPTION_BOOLEAN,'z', "set");
	_optNoNTPValidate = addOption(OPTION_BOOLEAN, 'N', "no_validation");
    }    

    public int main (Properties env, String[] argv) 
        throws MgmtException, PermissionException, ConnectException {

	
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

	setValidateWithSystem(getOptionValueBoolean(_optNoNTPValidate) == false);
	
	HCSiloProps siloProps=null;
        try {            
            siloProps=getApi().getSiloProps();
        } catch (MgmtException e) {
            System.out.println("Error fetching hive properties.");
            return EX_UNAVAILABLE;
        }           
        int res = CliConstants.MGMT_OK;
        if (getOptionValueBoolean(_optSetInteractive)) {

            boolean changed = setValuesInteractively((Object)siloProps,
              HIVE_CONFIG_OPTIONS, true);
            if (false == changed) {
                System.out.println("No values changed.");
            } else {
                try {
                    if (!verifyInteractiveValues(
			HIVE_CONFIG_OPTIONS, siloProps)) {
                        return EX_TEMPFAIL;
                    }
		    if (verifyDNSSettings(siloProps) == false) {
			return EX_TEMPFAIL;
		    }
		    // Since this takes 2+ minutes give the user some information
		    System.out.println("Updating hive configuration settings...");
		    HCSiloProps blankProps=new HCSiloProps();
                    HCCell[] cells = getApi().getCells(false);                    
                    for(int i=0;i<cells.length;i++) {
                        if(!validateSwitches(cells[i].getCellId()))
                            return EX_TEMPFAIL;
                    }
		    commitChanges((Object)blankProps, HIVE_CONFIG_OPTIONS);

		    res = getApi().setSiloProps(blankProps); 
                } catch (MgmtException e) {
                    System.out.println("Invalid data value: " +e.getMessage());
                }
            }
        } else if (areKeyValues(HIVE_CONFIG_OPTIONS)) {

	    String[] keys = (String[])_values.keySet().toArray(new String[_values.size()]);
	    
	    // The Hashmap needs to get cleared each invocation
	    // otherwise we attempt to use the values from the
	    // last invocation
            _pendingChanges.clear();
	    
	    // DNS enabled is a special case since the cli doesn't
	    // use a getops style parser the order of the arguments is
	    // not guaranteed.  We therefore need to walk through all
	    // the option passed by the user to see if they are changing
	    // the DNS setting.  If they are we need to
	    // add it to the pending change list at the very beginning
	    // so that we can do smart things like hostname to IP
	    // conversion.
	    for (int i = 0; i < keys.length; i++) {
		if (DNS_ENABLE_KEY.equals(keys[i])) {
		    if (verifyDns() == false)
			return EX_TEMPFAIL;
		    _pendingChanges.put(DNS_ENABLE_KEY,(String)_values.get(DNS_ENABLE_KEY));
		}
	    }
	    
	    // Verify all the passed in keys
	    // We don't use '
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                    
                if(isKeyOption(key,HIVE_CONFIG_OPTIONS)) {
                    _pendingChanges.put(key,(String)_values.get(key));
		    if (verifyValue(key, siloProps) == false)
			return EX_TEMPFAIL;
                }                        
            }
	    if (verifyDNSSettings(siloProps) == false) { 
		return EX_TEMPFAIL;
	    }
	    // Since this takes 2+ minutes give the user some information

            HCCell[] cells = getApi().getCells(false);                    
            for(int i=0;i<cells.length;i++) {
                if(!validateSwitches(cells[i].getCellId()))
                    return EX_TEMPFAIL;
            }
	    System.out.println("Updating hive configuration settings...");
	    HCSiloProps blankProps=new HCSiloProps();
	    commitChanges((Object)blankProps,HIVE_CONFIG_OPTIONS);
	    res = getApi().setSiloProps(blankProps); 
        } else {
            super.printNetInfo((Object)siloProps,HIVE_CONFIG_OPTIONS);
        }
        switch (res) {
        case CliConstants.MGMT_OK:
            return EX_OK;
        case CliConstants.MGMT_CMM_CONFIG_UPDATE_FAILED:
            return EX_UNAVAILABLE;
        default:
            return EX_SOFTWARE;
        }
    }
    
    
    /**
     * Validate the values entered by the user.   This method is called
     * when the "--set" option is called.
     * @param options the options list
     * @param siloProps the current setttings
     * @return true if all values have been successfully updated,
     * false otherwise.
     */
    protected boolean verifyInteractiveValues(
	String[][] options, 
	HCSiloProps siloProps)
    throws ConnectException, MgmtException {
	
	for(int i = 0; i < options.length; i++) {
	    if (verifyValue(options[i][1], siloProps) == false)
		return false;
	}
        return true;
    }
    
}
