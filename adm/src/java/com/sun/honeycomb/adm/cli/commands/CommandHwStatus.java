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
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.FruCommand;
import com.sun.honeycomb.adm.cli.PrintfFormat;
import com.sun.honeycomb.admin.mgmt.client.HCFru;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.adm.cli.ConnectException; 
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.mgmt.common.MgmtException;

/**
 * The execution class for the hwstat command.
 */
public class CommandHwStatus extends FruCommand 
implements ExitCodes {


    private final Option _optFruname;
    private final MessageFormat _hw_formatFru;
    private final MessageFormat _hw_formatDisk;
    private final PrintfFormat  _hw_formatLong;
   


    public CommandHwStatus (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);       

        _hw_formatLong 
            = new PrintfFormat("%-12s  %-6s  %-46s  %-8s");
        _hw_formatFru 
            = new MessageFormat ("Component: {0}  Type: {1}  Status: [{2}]\n"
		+ "FRU ID: {3}\n");
        _hw_formatDisk 
            = new MessageFormat ("Disk: {0} (disk number {1} Device Path: {2}) Disk Status: [{3}]\n"
				+ "FRU ID: {9}\n"
                                + "Size: {5}; Avail: {6}; Used: {7}; Use%: {8,number,percent}\n");
	addCellIdOption(true);
        _optFruname = addOption (OPTION_STRING,'f',"FRUID");
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        String  fru_name = null;
        cellId=-1;
	
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
	
	fru_name  = getOptionValueString  (_optFruname);
	if (fru_name == null || fru_name.length() == 0)
	    System.out.println(getOutput(null));
	else {
	    retCode = validateFruKey(fru_name);
	    if (retCode != EX_CONTINUE)
		return retCode;
	    HCFru fru = getFru(getCell(), fru_name); 
	    if (fru == null) {
		System.out.println("Unable to find a FRU for the specified FRUID, "
		    + fru_name + ".\n"
		    + "Use hwstat for a list of valid FRU IDs."); 
		return EX_DATAERR;
	    } else {
		System.out.println(getFruInfo(fru));
	    }
	}
	
	return EX_OK;
    }

    /**
     * Output information for all know fru's or for the fru belonging to
     * the specified nodeId for the current set cell
     * @param nodeId the nodeId to fetch the fru's outputed for.  If null all fru
     * information fetched and outputed.
     * @return String the fru output
     */
    private String getOutput(String nodeId)
    throws MgmtException , ConnectException {
	
	StringBuffer output = new StringBuffer();
	output.append("Component     Type    FRU ID                                          Status\n");
	output.append("------------  ------  ----------------------------------------------  --------\n");
	Object[] frus = getApi().getFRUs(getCellId()).getFrusList().toArray();
	boolean isNodeOnline = true;
        for (int i = 0; i < frus.length; i++) {
            String msg = null;

            HCFru fru = (HCFru) frus[i];
	    StringBuffer name = new StringBuffer();
	    if (nodeId != null
		&& fru.getFruName().contains(nodeId) == false)
		continue;
	    if (isNode(fru)) {
		name.append("NODE-");
		isNodeOnline = "ONLINE".equals(getFruStatus(fru));
	    }
	    name.append(fru.getFruName());
	    if (isNodeOnline == false && isDisk(fru)) {
		// If node is offline don't output disk data
		continue;
	    }
            String[] args = new String[] {
                name.toString(),
                getFruType(fru),
                fru.getFruId().trim(),
                getFruStatus(fru)                
            };
	    msg = _hw_formatLong.sprintf(args);
	    output.append(msg);
	    output.append("\n");
	}
	return output.toString();
    }
    
    
    /**
     * Returns the disk information for the passed in disk <code>fru</code>.
     * @param String the fru data of the disk to output fru information for.
     * @return String the disk fru information
     */
    private String getDiskFruInfo(HCFru fru) 
    throws ConnectException, MgmtException {
	
	String fruName = fru.getFruName();
	HCDisk disk = getApi().getDisk(cellId, fruName);
	String id = "";
	String subId = "";
	if (fruName.length() >= 8) {
	    id = fruName.substring (5,8);
	    if (fruName.length() > 8)
		subId = fruName.substring (9);
	}
	Object[] args = new Object[] {
	    id,
	    subId,
	    disk.getDevice(),
	    getFruStatus(disk),
	    disk.getPath(),
	    Long.toString(disk.getTotalCapacity()*1024),
	    Long.toString((disk.getTotalCapacity()-disk.getUsedCapacity())*1024),
	    Long.toString(disk.getUsedCapacity()*1024),
	    new Double((disk.getUsedCapacity()*1.00)/(disk.getTotalCapacity()*1.00)),
	    disk.getFruId()
	};
	return _hw_formatDisk.format (args);
    }
    
    /**
     * @param fru the fru to generate informational string for
     * @return String the fru information for the specified fru
     */
    private String getFruInfo(HCFru fru)
    throws ConnectException, MgmtException {
	String msg;
	String typestring = getFruType(fru);
	if (typestring.equals("DISK")) {
	    msg = getDiskFruInfo(fru);
	} else if (typestring.equals("NODE")) {
	    msg = getNodeInfo(fru);
	} else {
	    Object[] args = new Object[] {
		fru.getFruName(),
		getFruType(fru),
		getFruStatus(fru),
		fru.getFruId()
	    };
	    msg = _hw_formatFru.format (args);
	}
	return msg;
    }

        
    /**
     * Returns the node information for the passed in node <code>fru</code>.
     * @param String the fru data of the node to output fru information for.
     * @return String the node information
     */
    private String getNodeInfo(HCFru fru)
    throws ConnectException, MgmtException {
        return getOutput(fru.getFruName());
    }
}
