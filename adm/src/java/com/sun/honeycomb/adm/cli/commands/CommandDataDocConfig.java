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

import java.util.Hashtable;

import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;

import com.sun.honeycomb.datadoctor.DataDocConfig;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.parser.Option;
import java.util.Iterator;

public class CommandDataDocConfig extends ShellCommand 
implements ExitCodes {




    // set all cycle targets to their default values (config.properties)
    private static final String CMD_DEFAULT = "default";

    // set all cycle targets to 0, which stops all tasks
    private static final String CMD_OFF= "off";

    // set all cycle targets to 1, which runs as fast as possible
    private static final String CMD_FULLSPEED = "fullspeed";

    private static final String DEFAULT_CONF_FILE = 
        "/config/config_defaults.properties";

    private final Option _optList;

    private Hashtable _ddCycleTypes;

    /*
     * end variable definitions
     */


    public CommandDataDocConfig (String name, String[] aliases, 
                                 Boolean isHidden) {
        super (name, aliases, isHidden);
	addCellIdOption(true);
        _optList = addOption (OPTION_BOOLEAN, 'l', "list");

        _ddCycleTypes = new Hashtable();

        _ddCycleTypes.put("REMOVE_DUP_FRAGS_CYCLE",new Integer(0));
        _ddCycleTypes.put("REMOVE_TEMP_FRAGS_CYCLE",new Integer(1));
        _ddCycleTypes.put("POPULATE_SYS_CACHE_CYCLE",new Integer(2));
        _ddCycleTypes.put("POPULATE_EXT_CACHE_CYCLE",new Integer(3));
        _ddCycleTypes.put("RECOVER_LOST_FRAGS_CYCLE",new Integer(4));
        _ddCycleTypes.put("SLOSH_FRAGS_CYCLE",new Integer(5));
        _ddCycleTypes.put("SCAN_FRAGS_CYCLE",new Integer(6));
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        String[] args   = null;
        int retcode = EX_OK;
        boolean list = false;
	String command = null;

        int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}
	list = getOptionValueBoolean (_optList);

	args = getRemainingArgs();
	if (args != null && args.length != 0)
	    command = args[0];
        
        if (null != command && command.equals(CMD_OFF)) { 
            retcode = turnOff(cellId);
        } else if (null != command && command.equals(CMD_FULLSPEED)) {
            retcode = goFullspeed(cellId);
        } else if (null != command && command.equals(CMD_DEFAULT)) {
            retcode = restoreDefaults(cellId);
        } else if (list==true || args == null || args.length == 0) { // display all
            displayCycles(cellId);
        } 

        else if (args.length == 1) { // display specific property or properties
            displayCycle (cellId,args[0].toLowerCase());
        }
        else if (args.length == 2) { // set
            return setCycle (cellId,args[0], Integer.valueOf(args[1]).intValue());
        }
        else {
            usage();
	    return EX_USAGE;
        }


        return retcode;
    }

    /** turn off all data doctor tasks */
    private int turnOff (byte cellId) throws MgmtException,ConnectException,PermissionException {
        setAllProps(cellId,(long)DataDocConfig.CG_DONT_RUN);
        return EX_OK;
    }

    /** set all data doctor tasks to run as fast as possible */
    private int goFullspeed (byte cellId) throws MgmtException,ConnectException,PermissionException {

        setAllProps(cellId, (long)DataDocConfig.CG_FULL_SPEED);
        return EX_OK;
    }

    /** revert to the values in the original config file */
    private int restoreDefaults (byte cellId) throws MgmtException,ConnectException,PermissionException {
        getInternalApi().restoreDdDefaults(cellId);
        return EX_OK;
    }

    /** set all properties to the given value */
    private void setAllProps(byte cellId, long cycleGoal) 
        throws MgmtException,ConnectException,PermissionException {
        getInternalApi().setAllDdProps(cellId, cycleGoal);
    }

    private void displayCycles(byte cellId) 
        throws MgmtException,ConnectException {
        Iterator cycleNames=_ddCycleTypes.keySet().iterator();
        while(cycleNames.hasNext()) {
            String cycleName=(String)cycleNames.next();
            displayCycle(cellId,cycleName);
        }
    }

    private int displayCycle(byte cellId,String cycleName) 
        throws MgmtException,ConnectException {
        cycleName=cycleName.toUpperCase();
        if(null==_ddCycleTypes.get(cycleName)) {
            System.out.println("No such cycle name: " + cycleName);
            return EX_USAGE;
        }
        int value=((Integer)_ddCycleTypes.get(cycleName)).intValue();
        int ddCycle=getInternalApi().getDDCycle(cellId,value);
        System.out.println(cycleName.toLowerCase()+" = " +  ddCycle);
        return EX_OK;
    }

    private int setCycle(byte cellId,String cycleName,int newVal) 
        throws MgmtException,ConnectException,PermissionException {
        cycleName=cycleName.toUpperCase();


        if(null==_ddCycleTypes.get(cycleName)) {
            System.out.println("No such cycle name: " + cycleName);
            return EX_USAGE;
        }
        int keyValue=((Integer)_ddCycleTypes.get(cycleName)).intValue();
        getInternalApi().setDDCycle(cellId,keyValue,newVal);
        return EX_OK;
    }
}


