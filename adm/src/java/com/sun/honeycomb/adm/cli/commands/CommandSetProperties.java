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
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;
import java.io.EOFException;


import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.editline.Editline;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.client.AdminClientInternal;

public class CommandSetProperties extends ShellCommand 
implements ExitCodes {

    
    public CommandSetProperties(String name,
      String[] aliases, Boolean isHidden) {
        super (name, aliases,  isHidden);
	addCellIdOption(true);
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

        Map props = loadNameValues();
        validateNameValues(props, cellId);

        return EX_OK;
    }

    private void validateNameValues(Map map, byte cellId)
        throws MgmtException, ConnectException,PermissionException {

        if (map == null || map.size() == 0) {
            System.err.println("no properties set");
            return;
        }

        System.out.println("You entered the following properties:");
        Iterator it = map.keySet().iterator();
        while (it.hasNext()) {
            String name = (String) it.next();
            String value = (String) map.get(name);
            System.out.println("name = " + name + ", value = " + value);
        }
        System.out.flush();
	String prompt = "Do you want to commit those? [y/N]: ";
	if (promptForConfirm(prompt, 'N')) {
            commitProperties(map, cellId);
        } else {
            System.err.println("Properties have not been committed");
        }
    }

    private void commitProperties(Map props, byte cellId)
        throws MgmtException, ConnectException, PermissionException {
        AdminClientInternal internalApi = getInternalApi();
        int res = internalApi.setProperties(cellId, props);
        if (res == 0) {
            System.out.println("Committed properties successfully");
        } else {
            System.out.println("Failed to commit properties");
        }
        System.out.flush();
    }

    private Map loadNameValues() {

        System.out.println("Enter each property using : <name> = <value>, " +
          ", end with return");
        System.out.flush();

        Editline editline = Editline.create("hcsh");
        String line = null;
        HashMap props = new HashMap();
        do {
            try {
                line = editline.readline("- new prop (or <enter> to finish): ",
		    isInteractive());
                if (line == null) {
                    return props;
                }
                String [] NV = line.split("=");
                if (NV == null || NV.length != 2) {
                    System.err.println("Invalid name value pair : " +
                        line);
                    continue;
                }
                props.put(NV[0].trim(), NV[1].trim());
            } catch (EOFException e) {
                line = null;
            } catch (IOException e) {
                line = null;
            }
        } while (line != null);
        return props;
    }
}
