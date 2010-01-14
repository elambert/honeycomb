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
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.FruCommand;
import com.sun.honeycomb.adm.cli.Shell;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.admin.mgmt.client.HCFru;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.common.CliConstants;



public class CommandHwConfig extends FruCommand 
implements ExitCodes {
    
    private final Option _optEnable;
    private final Option _optDisable;
    private final Option _optIpmi;
    private final Option _optDiskWipe;	// Wipe the disk
    private final Option _optOffline;	// Take disk offline prior to pull
    private final Option _optOnline;	// Put disk online after push

    public CommandHwConfig (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
	addForceOption();
	addCellIdOption(true);
        _optEnable  = addOption (OPTION_STRING, 
                                 getLocalString("cli.hwcfg.enable_char").charAt(0), 
                                 getLocalString("cli.hwcfg.enable_name"));
        _optDisable = addOption (OPTION_STRING, 
                                 getLocalString("cli.hwcfg.disable_char").charAt(0), 
                               getLocalString("cli.hwcfg.disable_name"));
        _optIpmi = addOption (OPTION_BOOLEAN, 'i', "ipmi");        
        _optOnline = addOption (OPTION_STRING, 'O', "online");
        _optOffline = addOption (OPTION_STRING, 'X', "offline");
        _optDiskWipe = addOption (OPTION_STRING, 'W', "wipe");
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        String enableFruId  = null;
        String disableFruId = null;
	String diskWipeFruId = null;
	String onlineFruId = null;
	String offlineFruId = null;
	boolean enable = false;
	boolean disable = false;
        boolean ipmi = false;
        boolean isNode=false;
        boolean isDisk=false;
        boolean diskWipe = false;
        boolean online = false;
        boolean offline = false;

        HCFru fru = null;
	String fruString = null;

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

	enableFruId  = getOptionValueString(_optEnable);
	disableFruId = getOptionValueString(_optDisable);
	ipmi = getOptionValueBoolean(_optIpmi);
	diskWipeFruId = getOptionValueString(_optDiskWipe);
	onlineFruId  = getOptionValueString(_optOnline);
	offlineFruId = getOptionValueString(_optOffline);

	
	// If the parser was smarter we could execute the commands in order
	// but that doesn't appear possible so we have to make sure only
	// one fru command has been requested.
	int actions = 0;
	if (enableFruId != null) {
	    actions++;
	    enable = true;
	    fruString = enableFruId;
	}
	if (disableFruId != null) {
	    actions++;
	    disable = true;
	    fruString = disableFruId;
	}
	if (diskWipeFruId != null) {
	    actions++;
	    diskWipe = true;
	    fruString = diskWipeFruId;
	}
	if (onlineFruId != null) {
	    actions++;
	    online = true;
	    fruString = onlineFruId;
	}
	if (offlineFruId != null) {
	    actions++;
	    offline = true;
	    fruString = offlineFruId;
	}
	if (actions > 1) {
	    System.out.println("Only 1 action allowed per invocation.");
	    return EX_USAGE;
	}
	if (actions == 0) {
	    System.out.println("Invalid command sequence specified.");
	    usage();
	    return EX_USAGE;
	}

	try {
	    retCode = validateFruKey(fruString);
	    if (retCode != EX_CONTINUE)
		return retCode;
	    fru = getFru(getCell(), fruString);
	} catch (MgmtException e) {
	    System.out.println(e.toString());
	    return EX_UNAVAILABLE;
	}

	if (fru == null) {
	    System.out.println("Unable to find a FRU for the specified FRUID,\n'"
		+ fruString + "' . To see the list of available FRUIDs use hwstat.\n");
	    return EX_DATAERR;
	}
	
	if (isFruAvailable(fru)) {
	    System.out.println("Requested component " + getFruName(fru)
		+ " is not available.\n"
		+ "Current status of " + fru.getFruName()
		+ " is " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_UNAVAILABLE;
	}
	    
        if (enable) {                
            return enable(fru);
        }
	if (disable) {
            return disable(fru, ipmi);
        }
	if (diskWipe) {
	    return wipe(fru);
	}
	if (online) {
	    return online(fru);
	}
	if (offline) {
	    return offline(fru);
	}
	usage();
	return EX_USAGE;
    }
    
     /**
     * Search for the fru with the specified id on the currently defined cell.
     * Overide subclass.  This method refetches the fru object once found.
     * The resulting fru object can safely have methods invoked upon it.
     *
     * @param cell the cell
     * @param id the name or fru id of the fru to find
     * @return HCFru the matching fru, null if no fru found
     * @see com.sun.honeycomb.adm.client#getFRUs(HCCell)
     */
    protected HCFru getFruById(HCCell cell, String id)
    throws ConnectException, MgmtException {
	HCFru fru = super.getFruById(cell, id);
	if (fru == null)
	    return null;
	
	// Refetch the object.  The resulting object can safetly have
	// method invoked upon it.
	return getFruByName(cell, getFruName(fru));
    }
    
    
    /**
     * Enable the specified fru on the specified cell.
     *
     * @param fru the fru to perform the enable on
     * @param userFruId the identifier specified by the user.  Used
     * in outputing error messages.
     * @return int command completion status 
     * @see ExitCodes
     */
    public int enable(HCFru fru)
    throws ConnectException, PermissionException {
	
	if (isFruEnabled(fru)) {
	    System.out.println(getFruName(fru) 
		+ " is already " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_OK;
	}
	int result = EX_OK;
	if(fru instanceof HCDisk) {
	    return enableDisk((HCDisk) fru);
	} else if(fru instanceof HCNode) {
	    return powerOnNode((HCNode) fru);
	} else {
	    System.out.println ("Invalid fru specified, not a node or a disk.");
	    return EX_USAGE;
	}
    }
    
    /**
     * Enable the specified disk on the specified cell.  
     *
     * @param disk the disk to perform the enable on
     * @return int command completion status 
     * @see ExitCodes
     */
    private int enableDisk(HCDisk disk)
    throws ConnectException, PermissionException {
	int result = EX_OK;
	try {
	    try {
		System.out.print("Enabling disk " + disk.getFruName() + "...");
		result = getApi().enableDisk(disk);
	    } finally {
		System.out.println();
	    }
	} catch (MgmtException e) {
	    printError("Unable to enable disk: "+ disk.getFruName(), e);
	    return EX_TEMPFAIL;
	}
	if (result == EX_OK) {
	    System.out.println("Successfully enabled " + disk.getFruName() + ".");
	} else {
	    System.out.println("Enabled of " + disk.getFruName() + " failed.");
	}
	return result;
    }
     
    /**
     * Power on the specified node on the specified cell
     *
     * @param node the node to perform the power on on
     * @return int command completion status 
     * @see ExitCodes
     */
    private int powerOnNode(HCNode node)
    throws ConnectException, PermissionException {
	int result = EX_OK;
	try {
	    try {
		System.out.print("Powering on node " + getFruName(node) + "...");
		result = getApi().powerNodeOn (getCell(),
		    node.getNodeId().intValue());
	    } finally {
		System.out.println();
	    }
	} catch (MgmtException e) {
	    printError("Unable to power on node: "+ getFruName(node), e);
	    return EX_TEMPFAIL;
	}
	switch (result) {
	    case CliConstants.SUCCESS:
		System.out.println("Successfully powered on " + getFruName(node) + ".");
		return EX_OK;
	    case CliConstants.POWER_ON_HONEYCOMB_SERVICES_DOWN:
		System.out.println("Power on of " + getFruName(node) 
		    + " succeeded but honeycomb services failed to start.");
		break;
	    default:
		System.out.println("Power on of " + getFruName(node) + " failed.");
		break;
	}
	return EX_TEMPFAIL;
    }
    
    
    /**
     * Disable the specified fru on the specified cell
     *
     * @param fru the fru to perform the disable on
     * @param ipmi boolean flag, true if ipmi should be used for the disable
     * @return int command completion status 
     * @see ExitCodes
     */
    public int disable(HCFru fru, boolean ipmi)
    throws ConnectException, PermissionException, MgmtException {
	
	if (isFruDisabled(fru)) {
	    System.out.println(getFruName(fru) 
		+ " is already " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_OK;
	}
	if(fru instanceof HCDisk) {
	    return disableDisk((HCDisk)fru);
	} else if (fru instanceof HCNode) {
	    return powerOffNode((HCNode)fru, ipmi);
	} else {
	    System.out.println ("Invalid fru specified, not a node or a disk.");
	    return EX_DATAERR;
	}
    }
    
    /**
     * Disable the specified disk on the specified cell
     *
     * @param disk the disk fru to perform the disable on
     * @return int command completion status 
     * @see ExitCodes
     */
    public int disableDisk(HCDisk disk)
    throws ConnectException, PermissionException {
	
	if (isFruEnabled(disk) == false) {
	    System.out.println(disk.getFruName() + " can only be disabled disk if it has"
		+ " a status of enabled.\nCurrent status of " + disk.getFruName()
		+ " is " + getFruStatus(disk).toLowerCase() + ".");
	    return EX_TEMPFAIL;
	}
	int result = EX_OK;
	try {
	    try {
		System.out.print("Disabling disk " + disk.getFruName() + "...");
		result = getApi().disableDisk(disk);
	    } 
	    finally {
		System.out.println();
	    }
	} catch (MgmtException e) {
	    printError("Disable of " + disk.getFruName() + " failed.", e);
	    return EX_TEMPFAIL;
	}
	
	if (result == EX_OK) {
	    System.out.println("Successfully disabled " + disk.getFruName() + ".");
	} else {
	    System.out.println("Disable of " + disk.getFruName() + " failed.");
	}
	return result;
    }
    
    /**
     * Power Off the specified node on the specified cell
     *
     * @param node the node fru to perform the power off on
     * @param ipmi boolean flag, true if ipmi should be used for the power off
     * @return int command completion status 
     * @see ExitCodes
     */
    public int powerOffNode(HCNode node, boolean ipmi)
    throws ConnectException, PermissionException, MgmtException {
	
	boolean isMasterNode = node.isIsMaster() && (getCellId() == getApi().getMasterCellId());
	if (isForceEnabled() == false) {
	    if (isMasterNode) {       
		// We are on the master cell and are getting ready
		// to power down the master node
		System.out.println(getFruName(node) + " is the master node for cell " 
		    + cell.getCellId() + ". Powering off\n"
		    +"the node will terminate the current cli session.");
	    }
	    if (!promptForConfirm("Power off " + getFruName(node) + "? [y/N]: ",'N')) {
		return EX_USERABORT;
	    }
	}
	int result = EX_OK;
	try {
	    try {
		System.out.print("Powering off node " + getFruName(node) + "..." );
		result = getApi().powerNodeOff(getCell(),
		    node.getNodeId().intValue(), ipmi);
	    }
	    finally {
		System.out.println();
	    }
	} catch (MgmtException me) {
	    printError("Power off of " + getFruName(node) + " failed.", me);
	    return EX_TEMPFAIL;
	} 
	    
	if (result == EX_OK) {
	    if (isMasterNode) {
		System.out.println("Successfully issued command to power off the master node, " 
		    + getFruName(node) + ".");
		System.out.println("Master failover is in progress...");
                doMasterLogoutWait();
	    }
	    System.out.println("Successfully powered off " + getFruName(node) + ".");
	} else {
	    System.out.println("Power off of " + getFruName(node) + " failed.");
	}
	return result;
    }
    
    /**
     * Wipe Disk
     *
     * @param fru the fru to perform the wipe on
     * @return int command completion status 
     * @see ExitCodes
     */
    public int wipe(HCFru fru)
    throws ConnectException, PermissionException, MgmtException {
	if (!(fru instanceof HCDisk)) {
	    System.out.println("Wipe operation is only allowed on a disk.");
	    return EX_USAGE;
	}
	if (isFruEnabled(fru)) {
	    System.out.println("The disk " + fru.getFruName() 
		+ " must be disabled before performing\n"
		+ "a wipe operation. Current status of " + fru.getFruName()
		+ " is " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_TEMPFAIL;
	}
	if (isFruDisabled(fru) == false) {
	    System.out.println("The disk " + fru.getFruName() 
		+ " must have a status of disabled before a wipe\n"
		+ "operation can be performed. Current status of " + fru.getFruName()
		+ " is " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_OK;
	}
	if (isForceEnabled() == false) {
	    StringBuffer buf = 
		new StringBuffer("Destroy all data on ");
	    buf.append(fru.getFruName()).append("? [y/N]: ");
	    if (!promptForConfirm(buf.toString(),'N')) {
		return EX_USERABORT; 
	    }
	}

	HCDisk disk = (HCDisk) fru;
	int result = EX_OK;
	try {
	    try {
		System.out.print("Wiping " + disk.getFruName() + "..." );
		result = getApi().wipeDisk(disk);
	    }
	    finally {
		System.out.println();
	    }
	} catch (MgmtException e) {
	    printError("Unable to wipe disk: " + disk.getFruName(), e);
	    return EX_TEMPFAIL;
	}
	if (result == EX_OK) {
	    System.out.println("Successfully wiped " + disk.getFruName() + ".");
	    // Currently the disk commands are returning before the states
	    // are changed.  Uncomment the following when CR6573089
	    // is resolved.
	    /*
	    HCFru fru1 = refetchFru(fru);
	    System.out.println("Current status of " + fru.getFruName()
		+ " is " + getFruStatus(fru1).toLowerCase() + ".");
	     */
	} else {
	    System.out.println("Wipe of " + disk.getFruName() + " failed.");
	}
	return result;
    }
    
    /**
     * Online Disk 
     * <P>
     * Can be run on any disk, no matter the state.  If the disk is offline, 
     * it will be put online. 
     *
     * @param fru the fru to perform the online on
     * @return int command completion status 
     * @see ExitCodes
     */
    public int online(HCFru fru)
    throws ConnectException, PermissionException, MgmtException {

	if (!(fru instanceof HCDisk)) {
	    System.out.println("Only disks can be onlined.");
	    return EX_USAGE;
	}
	
	if (isFruOnline(fru)) {
	    System.out.println(getFruName(fru) 
		+ " is already " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_OK;
	}
	
	
	if (isFruOffline(fru) == false) {
	    System.out.println(getFruName(fru) 
		+ " must be offline in order to online drive.\n"
		+ "Current status of " + fru.getFruName()
		+ " is " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_TEMPFAIL;
	}
	
	// Currently there is no status that tells us the disk is online
	HCDisk disk = (HCDisk) fru;
	int result;
	try {
	    try {
		System.out.print("Onlining " + disk.getFruName() + "..." );
		result = getApi().onlineDisk(disk);
	    }
	    finally {
		System.out.println();
	    }
	} catch (MgmtException e) {
	    printError("Unable to online disk: " + disk.getFruName(), e);
	    return EX_TEMPFAIL;
	}
	if (result == EX_OK) {
	    System.out.println("Successfully onlined " + disk.getFruName() + ".");
	    
	    // onlining a disk results in a major fru state change, offline to enabled
	    // display current state to user
	    
	    // Currently the disk commands are returning before the states
	    // are changed.  Uncomment the following when CR6573089
	    // is resolved.
	    /*
	    HCFru fru1 = refetchFru(fru);
	    System.out.println("Current status of " + fru.getFruName()
		+ " is " + getFruStatus(fru1).toLowerCase() + ".");
	     */
	} else {
	    System.out.println("Online of " + disk.getFruName() + " failed.");
	}
	return result;
    }
    
    /**
     * Offline Disk so that it can be pulled.
     * <P>
     * Fails if the disk is enabled.
     *
     * @param fru the fru to perform the offline on
     * @return int command completion status 
     * @see ExitCodes
     */
    public int offline(HCFru fru)
    throws ConnectException, PermissionException {

	if (!(fru instanceof HCDisk)) {
	    System.out.println("May only offline a disk.");
	    return EX_USAGE;
	}
	
	if (isFruOffline(fru)) {
	    System.out.println(getFruName(fru) 
		+ " is already " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_OK;
	}
	
	
	if (isFruEnabled(fru)) {
	    System.out.println("The disk " + fru.getFruName() 
		+ " must be disabled before it can be\n"
		+ "taken offline. Current status of " + fru.getFruName()
		+ " is " + getFruStatus(fru).toLowerCase() + ".");
	    return EX_TEMPFAIL;
	}

	// Currently there is no status that tells us the disk is offline
	HCDisk disk = (HCDisk) fru;
	int result;
	try {
	    try {
		System.out.print("Offlining " + disk.getFruName() + "..." );
		result = getApi().offlineDisk(disk);
	    }
	    finally {
		System.out.println();
	    }
	} catch (MgmtException e) {
	    printError("Unable to offline disk: " + disk.getFruName(), e);
	    return EX_TEMPFAIL;
	}
	if (result == EX_OK) {
	    System.out.println("Successfully offlined " + disk.getFruName() + ".");
	} else {
	    System.out.println("Offline of " + disk.getFruName() + " failed.");
	} 
	return result;
    }
    
    /**
     * Refetch the fru
     * @param HCFru the fru to refetch
     * @return HCFru the refetched fru
     */
    private HCFru refetchFru(HCFru fru)
    throws ConnectException, PermissionException, MgmtException {
	HCCell cell = getApi().getCell(getCell().getCellId());
	return getFru(cell, fru.getFruId());  
    }
}
