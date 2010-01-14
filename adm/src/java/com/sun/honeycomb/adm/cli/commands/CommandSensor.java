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
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCSensor;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.adm.cli.CliException;
import com.sun.honeycomb.adm.cli.PrintfFormat;
import java.util.Properties;

/**
 * Command-line tool that reports system status information
 */
public class CommandSensor extends ShellCommand 
implements ExitCodes {
    
    PrintfFormat formatter 
            = new PrintfFormat("\t%-20s  %-15s");

    public CommandSensor (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
	addCellIdOption(true);
	// addAllCellsOption();
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

        try {
            if(isAllCellsEnabled()) {
                HCCell[] cells = getApi().getCells(false);
                for(int curCell=0;curCell<cells.length;curCell++) {
                    cellId=cells[curCell].getCellId();            
                    printAllSensors(cellId);
                }
            } else {
                printAllSensors(getCellId());
            }
        } catch (CliException e ){
            return EX_UNAVAILABLE;
        }

        return EX_OK;

    }

    private void printAllSensors(byte cellId) throws CliException,MgmtException,ConnectException{

        HCSensor sensor = null;
        for (int i=101; i < getApi().getNumNodes(cellId)+101; i++) {
            try {
                sensor = getApi().getSensors(cellId,i);
            } catch (Exception e) {
                System.out.println ("Failure getting node data for node: " 
			+ getNodeName(i));
                throw new CliException("");

            }

            System.out.println("\n" + getNodeName(i) + ":");
            printSensors(sensor);
        }
    }
    private void printSensors(HCSensor sensor) {
        System.out.println(formatter.sprintf(
	    new String[] {
		"DDR Voltage",
		getValue(sensor.getDdrVoltage())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"CPU Voltage",
		getValue(sensor.getCpuVoltage())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"VCC 3.3V",
		getValue(sensor.getThreeVCC())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"VCC 5V",
		getValue(sensor.getFiveVCC())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"VCC 12V",
		getValue(sensor.getTwelveVCC())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"Battery Voltage",
		getValue(sensor.getBatteryVoltage())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"CPU Temperature",
		getValue(sensor.getCpuTemperature())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"System Temperature",
		getValue(sensor.getSystemTemperature())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"System Fan 1 speed",
		getValue(sensor.getSystemFan1Speed())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"System Fan 2 speed",
		getValue(sensor.getSystemFan2Speed())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"System Fan 3 speed",
		getValue(sensor.getSystemFan3Speed())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"System Fan 4 speed",
		getValue(sensor.getSystemFan4Speed())
	}));
        System.out.println(formatter.sprintf(
	    new String[] {
		"System Fan 5 speed",
		getValue(sensor.getSystemFan5Speed())
	}));

    }

    /**
     * Check to ensure the value being outputed is not null
     * @param value the value to output
     * @return String a non null value
     */
    private String getValue(String value)
    {
	if (value == null || value.length() == 0
	    || value.equals("unavailable")) {
	    return "Unavailable";
	} else
	    return value;
    }
}
