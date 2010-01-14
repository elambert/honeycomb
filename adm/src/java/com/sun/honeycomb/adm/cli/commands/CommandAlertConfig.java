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
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.common.MgmtException;

import java.util.Properties;

/**
 * 
 */
public class CommandAlertConfig extends ShellCommand 
implements ExitCodes {

     


    private static final String ACTION_ADD = "add";
    private static final String ACTION_DEL = "del";
    private static final String TYPE_TO    = "to";
    private static final String TYPE_CC    = "cc";
    

    public CommandAlertConfig (String name,String[] aliases,Boolean isHidden) {
        super (name, aliases, isHidden);

    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        if (null != argv) {
            int retCode = handleStandardOptions(argv, true);
	    if (retCode != EX_CONTINUE)
		return retCode;

            String[] extraArgs = getRemainingArgs();

            if (extraArgs.length != 3) {
                usage();
                return EX_USAGE;
            }

            String action  = extraArgs[0];
            String type    = extraArgs[1];
            String address = extraArgs[2];
	    
	    assert(action != null);
	    assert(type != null);
	    assert(address != null);
	    
	    action = action.trim();
	    type = type.trim();
	    address = address.trim();

	    // TODO: Use enums for type
            if ((!action.equals(ACTION_DEL) && !action.equals(ACTION_ADD)) ||
                (!type.equals(TYPE_TO) && !type.equals(TYPE_CC))) {
                usage();
                return EX_USAGE;
            }  



            try {
                if (action.equals (ACTION_ADD)) {
                    return addAlertEmail (type, address);
                } else {
                    return delAlertEmail (type, address);
                }
            } catch (MgmtException e) {
                System.out.println(e.getMessage());
            }
	    // Let other unexpected exceptions go to ShellCommand for
            // proper handling/logging


        } else {
            try {
                printAddressLists();
            } catch (MgmtException e) {
		e.printStackTrace();
                System.out.println(e.getMessage());
            }
	    // Let other unexpected exceptions go to ShellCommand for
            // proper handling/logging
        }

        return EX_OK;
    }

    /**
     * Add the specified alert email address to the to: or cc: list as indicated
     * by the specified type
     * @param type the to or cc list
     * @param addr the email address to add
     * @return int the cli exit status code
     */
    private int addAlertEmail (String type,String addr) 
            throws MgmtException, PermissionException, ConnectException {
        String[] addrs = getAddressList (type);
        if (addrs != null && addrs.length != 0) {
            for (int i = 0; i < addrs.length; i++) {
                if (addrs[i].equals (addr)) {
		    System.out.println(addr + " is already in the specified list.");
                    return EX_OK;
                }
            }
        }

	int retCode = CliConstants.FAILURE;
	String typeStr = null;
        if(TYPE_TO.equals(type)) {
	    retCode = getApi().addAlertTo(addr);
	    typeStr = "To";
	} else if(TYPE_CC.equals(type)) {
            retCode =  getApi().addAlertCc(addr);
            typeStr = "Cc";
	} else {
            System.out.println("Fatal error in ComandAlertConfig - bad type:" +type);
            System.exit(1);
        }
	switch (retCode) {
	    case CliConstants.SUCCESS:
		break;
	    case CliConstants.FAILURE:
	    default:
                System.out.println("Failed to add " + addr + " to the "
		    + typeStr + ": list.");
		return EX_TEMPFAIL;
	}
        return EX_OK;
    }

    /**
     * Add the specified alert email address to the to: or cc: list as indicated
     * by the specified type
     * @param type the to or cc list
     * @param addr the email address to remove
     * @return int the cli exit status code
     */
    private int delAlertEmail (String type,String addr) 
            throws MgmtException, PermissionException, ConnectException {

	
	int retCode = CliConstants.FAILURE;
	String typeStr = null;
        if(TYPE_TO.equals(type)) {
	    retCode = getApi().delAlertTo(addr);
	    typeStr = "To";
	} else if(TYPE_CC.equals(type)) {
            retCode =  getApi().delAlertCc(addr);
            typeStr = "Cc";
	} else {
            System.out.println("Fatal error in ComandAlertConfig - bad type:" +type);
            System.exit(1);
        }
	switch (retCode) {
	    case CliConstants.SUCCESS:
		break;
	    case CliConstants.NOT_FOUND:
                System.out.println("Unable to delete " + addr + " from the "
		    + typeStr + ": list.\nThe specified email address is not a member of the list.");
		return EX_TEMPFAIL;
	    case CliConstants.FAILURE:
	    default:
                System.out.println("Failed to delete " + addr + " from the "
		    + typeStr + ": list.");
		return EX_TEMPFAIL;
	}
        return EX_OK;
    }

    private String[] getAddressList (String type) throws MgmtException, ConnectException {
        AdminClient api = getApi();
        if(TYPE_TO.equals(type)) 
            return api.getAlertTo();
        else if(TYPE_CC.equals(type)) 
            return api.getAlertCc();
        else {
            System.out.println("Fatal error in ComandAlertConfig - bad type:" +type);
            System.exit(1);
        }
	return null;  // Make the compier happy
    }

    private void printAddressLists() throws MgmtException, ConnectException {
        System.out.print (getLocalString("cli.alertcfg.listto"));
        String[] addrs = getAddressList(TYPE_TO);
        if (addrs != null && addrs.length > 0) {
            for (int i = 0; i < addrs.length; i++) {
                System.out.print ("\t");
                System.out.println (addrs[i]);
            }
        } else {
            System.out.println();
        }
        System.out.print (getLocalString("cli.alertcfg.listcc"));
        try {
            addrs = getAddressList(TYPE_CC);
        } catch (MgmtException e){
            System.out.println(e.getMessage());
	    return;
        }

        if (addrs != null && addrs.length > 0) {
            for (int i = 0; i < addrs.length; i++) {
                System.out.print ("\t");
                System.out.println (addrs[i]);
            }
        } else {
            System.out.println();
        }
    }
}
