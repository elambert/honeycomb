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


package com.sun.honeycomb.adm.cli;


import java.util.regex.Matcher;

import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.admin.mgmt.client.HCFru;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import com.sun.honeycomb.admin.mgmt.client.HCSwitch;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.admin.mgmt.client.HCSP;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.FruNamePatterns;
import com.sun.honeycomb.mgmt.common.MgmtException;


/**
 * Abstract class for FruCommands.  Provides basic methods for examining and
 * quering about fru properties
 *
 * TODO: Consider reworking into a stand alone class so that fru states
 * are accessible and available in a single class.
 */
public abstract class FruCommand extends ShellCommand 
{
    private static final String UNAVAILABLE = "unavailable";
    /**
     * Alternate form of Fru ID form seen during testing.  This is suppose
     * to be a transient id form
     */
    private static final String UNKNOWN_FRU_ID_ALT_FORM = "???";
    
    /**
     * Creates a new instance of FruCommand
     */
    public FruCommand(String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
    }
    
    /**
     * Search for the specified id on the currently defined cell
     *
     * @param cell the cell
     * @param id the name or fru id of the fru to find
     * @return HCFru the fru id for the specified <code>id</code>
     */
    protected HCFru getFru(HCCell cell, String id)
    throws ConnectException, MgmtException {
	HCFru fru = getFruByName(cell, id);
	if (fru == null)
	    fru = getFruById(cell, id);
	return fru;
    }
    
    /**
     * Search for the fru with the specified id on the currently defined cell
     * <P>
     * <B>NOTE:</B>This method uses the AdminClient.getFRUs() and loops
     * through the frus to find the specified fru id specified.  The
     * resulting HCFru object returned by this method.  The resulting
     * object is find for displaying information but should not
     * have operations invoked upon it via this object.  It should be
     * refetched via HCFru fru = getFruByName(cell, id) before actions
     * are invoked upon it.
     *
     * @param cell the cell
     * @param id the name or fru id of the fru to find
     * @return HCFru the matching fru, null if no fru found
     * @see com.sun.honeycomb.adm.client#getFRUs(HCCell)
     */
    protected HCFru getFruById(HCCell cell, String id)
    throws ConnectException, MgmtException {
	if (id == null || id.length() == 0)
	    return null;
	
	Object[] frus = getApi().getFRUs(cell.getCellId()).getFrusList().toArray();
	for (int i=0; i < frus.length; i++) {
	    
            HCFru fru = (HCFru) frus[i];
	    if (id.equals(fru.getFruId().trim())) {
		return fru;
	    }
	}
	return null;
    }
    
    /**
     * Search for the fru that has a name of <code>name</code> on the currently 
     * defined cell
     *
     * @param cell the cell
     * @param name the fru name to find
     * @return HCFru the matching fru, null if no fru found
     */
    protected HCFru getFruByName(HCCell cell, String name)
    throws ConnectException, MgmtException {
	if (name == null || name.length() == 0)
	    return null;

	if (name.startsWith("NODE")) {
	    return getApi().getNode(cell.getCellId(), name.toLowerCase());
	} else if (name.startsWith("DISK")) {
	    return getApi().getDisk(cell.getCellId(), name.toLowerCase());
	} else if (name.startsWith("SWITCH")) {
            return getApi().getSwitch(cell.getCellId(), name);
        } else if (name.equals("SN"))
            return getApi().getSp(cell.getCellId());
	return null;
    }

    /**
     * Returns the name of the fru.  
     * @return String the name of the fru
     */
    public final String getFruName(HCFru fru)
    {
	if (isNode(fru))
            return new StringBuffer("NODE-").append(fru.getFruName()).toString();
        return fru.getFruName();
    } 

    /**
     * Return the status of the fru
     * @return String the fru status.  
     */
    public final String getFruStatus(HCFru fru)
    {
	try {
	    if (isNode(fru) || (fru instanceof HCSP)) {
		return CliConstants.HCNODE_STATUS_STR[fru.getStatus().intValue()];
	    }
            if (isSwitch(fru)) {
                return CliConstants.HCSWITCH_STATUS_STR[fru.getStatus().intValue()];
            }
	    return CliConstants.HCFRU_STATUS_STR[fru.getStatus().intValue()];
	}
	catch (ArrayIndexOutOfBoundsException ae)
	{
	    return CliConstants.HCFRU_UNKNOWN_STR;
	}
    }
    
    /**
     * @return String the fru type.
     */
    public final String getFruType(HCFru fru)
    {
	try {
	    return CliConstants.HCFRU_TYPE_STR[fru.getFruType().intValue()];
	}
	catch (ArrayIndexOutOfBoundsException ae)
	{
	    return CliConstants.HCFRU_UNKNOWN_STR;
	}
        catch (NullPointerException ne) {
            return CliConstants.HCFRU_UNKNOWN_STR;
        }
    }
    
    
    /**
     * @return true if specified fru is a disk fru, false otherwise
     */
    public boolean isDisk(HCFru fru) {
	return fru instanceof HCDisk;
    }
    
    
    /**
     * @return true if specified fru is a node fru, false otherwise
     */
    public boolean isNode(HCFru fru)
    {
	return fru instanceof HCNode;
    }
    
    
    /**
     * @return true if specified fru is a switch fru, false otherwise
     */
    public boolean isSwitch(HCFru fru)
    {
	return fru instanceof HCSwitch;
    }
    
    /**
     * @return true if specified fru is disabled, false otherwise
     */
    public boolean isFruDisabled(HCFru fru) {
	int status = fru.getStatus().intValue();
	if (isNode(fru)) {
	    return status == 0 || status == 3;
	}
	return status == 0;
    }
    
    /**
     * @return true if specified fru is enabled, false otherwise
     */
    public boolean isFruEnabled(HCFru fru) {
	return fru.getStatus().intValue() == 1;
    }
    
    /**
     * @return true if specified fru is offline, false otherwise
     */
    public boolean isFruOffline(HCFru fru) {
	int status = fru.getStatus().intValue();
	if (isNode(fru)) {
	    return status == 0 || status == 3;
	}
	return status == 3;
    }
    
    /**
     * @return true if specified fru is online, false otherwise
     */
    public boolean isFruOnline(HCFru fru) {
	
	int status = fru.getStatus().intValue();
	return status == 2;
    }
    
    /**
     * @return true if specified fru is available, false otherwise
     * Currently only disks can have a status of missing
     */
    public boolean isFruAvailable(HCFru fru) {
	
	if (isNode(fru))
	    return false;
	int status = fru.getStatus().intValue();
	return status == 4;
    }
    

    
    /**
     * Validate the passed in fru name or id.  
     * @param key the name or id to validate.  Generates
     * error messages to standard out if name is invalid.
     * No validation of the id currently occurs
     * @return int exit status. callee should exit with
     * returned exit status if not EX_CONTINUE
     */
    protected int validateFruKey(String key) 
    throws MgmtException {
	String keyUpper = key.toUpperCase();
	if (keyUpper.startsWith("DISK"))
	    return validateDiskName(key);
	if (keyUpper.startsWith("NODE"))
	    return validateNodeName(key);
	if (keyUpper.startsWith("SWITCH"))
            return validateSwitchName(key);
        if (keyUpper.equals("SP"))
            return ExitCodes.EX_CONTINUE;
	return validateId(key);
    }
    
    private final static String MSG_HWSTAT_FOR_DISKS =
	"Use hwstat for a list of valid disk names.";
    
    private final static String MSG_HWSTAT_FOR_SWITCHS =
	"Use hwstat for a list of valid switchs.";
    
    /**
     * Validate the passed in fru id string  
     * @param id the fru id string to validate.  Generates
     * error messages to standard out if id is invalid.
     * @return int exit status. callee should exit with
     * returned exit status if not EX_CONTINUE
     */
    protected int validateId(String id) {
	// We don't have a way to validate id's but there are
	// 2 forms of id's that represent unknown id's.  These
	// id's are not unique so don't allow the user to
	// specify them.  ??? is suppose to be a transient state
	if (id.toUpperCase().equals(CliConstants.HCFRU_UNKNOWN_STR) 
	    || id.equals(UNKNOWN_FRU_ID_ALT_FORM)
	    || id.toLowerCase().equals(UNAVAILABLE)) {
	    System.out.println(
		"Invalid id specified.  " + id.toUpperCase()
		+ " is used by the system to represent the id for\n"
		+ "a fru it can't retrieve id information. However, since this id is not unique\n"
		+ "no actions can be performed using this identifier.");
	    return ExitCodes.EX_DATAERR;
	}
	return ExitCodes.EX_CONTINUE;
    }
    
    /**
     * Validate the passed in disk name.  
     * @param diskName the disk name to validate.  Generates
     * error messages to standard out if name is invalid.
     * @return int exit status. callee should exit with
     * returned exit status if not EX_CONTINUE
     */
    protected int validateDiskName(String sDiskName)
    throws MgmtException {
	Matcher diskMatcher = FruNamePatterns.DISK_FRU_NAME.matcher(sDiskName);
	if (diskMatcher.matches() == false) { 
	    System.out.println("Invalid disk name, " + sDiskName 
		+ " specified. Must be in the form DISK-1xx:y.\n"
		+ MSG_HWSTAT_FOR_DISKS); 
	    return ExitCodes.EX_DATAERR;
	}
	// We know the pattern is valid.  But is disk number
	// within what is allowed
	int diskNum = Integer.parseInt(diskMatcher.group(2));
	int nodeNum = Integer.parseInt(diskMatcher.group(1));
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15. In this case, we're 1-16. Need to fix the pattern
        // matcher
        //

	if (nodeNum == 0) {
	    System.out.println("Invalid disk name of " + sDiskName 
		+ " specified.\nValid disk names start at DISK-101:0.\n"
		+ MSG_HWSTAT_FOR_DISKS);
	    return ExitCodes.EX_DATAERR;
	}
	int diskCount = 0;
	int nodeCount = 0;
	try {
	    nodeCount = getApi().getNumNodes(getCellId());
	}
	catch (ConnectException ce) {
	    exitConnectError(ce);
	}
	if (nodeNum > nodeCount) {
	    System.out.println("Invalid disk name, " + sDiskName 
		+ " specified. Cell " + getCellId()
		+ " only has " + nodeCount + " nodes.\n"
		+ MSG_HWSTAT_FOR_DISKS);
	    return ExitCodes.EX_DATAERR;
	}
	try {
	    diskCount = getApi().getNumDisksPerNode(getCell());
	}
	catch (ConnectException ce) {
	    exitConnectError(ce);
	}
	if (diskNum > diskCount) {
	    System.out.println("Invalid disk name, " + sDiskName 
		+ " specified. "
		+ getNodeName(nodeNum)
		+ " only has " + diskCount + " disks.\n"
		+ MSG_HWSTAT_FOR_DISKS);
	    return ExitCodes.EX_DATAERR;
	}
	return ExitCodes.EX_CONTINUE;
    }
    
    /**
     * Validate the disk name
     * @return boolean true if specified name is a valid disk name, false
     * otherwise
     */
    public boolean isValidDiskName(String name,int nodeNum) 
    throws ConnectException, MgmtException {
	Matcher diskMatcher = FruNamePatterns.DISK_FRU_NAME.matcher(name);
	if (diskMatcher.matches()) {
	    // We know the pattern is valid.  But is disk number
	    // within what is allowed
	    int diskNum = Integer.parseInt(diskMatcher.group(2));
	    int diskCount = getApi().getNumDisksPerNode(getCell());
	    return (diskNum < diskCount);
	} 
	return false;
    }

    
    /**
     * Validate the passed in disk name.  
     * @param switchName the switch name to validate.  Generates
     * error messages to standard out if name is invalid.
     * @return int exit status. callee should exit with
     * returned exit status if not EX_CONTINUE
     */
    protected int validateSwitchName(String switchName) {
        Matcher switchMatcher = FruNamePatterns.SWITCH_FRU_NAME.matcher(switchName);
        if (switchMatcher.matches() == false) { 
	    System.out.println("Invalid switch name, " + switchName 
		+ " specified. Must be in the form SWITCH-x.\n"
		+ MSG_HWSTAT_FOR_SWITCHS); 
	    return ExitCodes.EX_DATAERR;
	}
	return ExitCodes.EX_CONTINUE;
    }
    
    
    /**
     * Debug method to output the contents of HCFru since no toString() method
     * is provided by those classes.
     * @param fru the HCFru to output contents of 
     * @return String contents dump of fru
     */
    public String toString(HCFru fru) {
	StringBuffer buf = new StringBuffer();
	buf.append("Id: " + fru.getFruId());
	buf.append("\nName: " + fru.getFruName());
	buf.append("\nType: " + fru.getFruType() 
	    + "("+ getFruType(fru) + ")");
	buf.append("\nStatus: " + fru.getStatus().toString()
	    + "(" + getFruStatus(fru) + ")");
	return buf.toString();
    }
    
    /**
     * Debug method to output the contents of HCDisk since no toString() method
     * is provided by those classes.
     * @param fru the HCDisk to output contents of 
     * @return String contents dump of fru
     */
    public String toString(HCDisk fru) {
	StringBuffer buf = new StringBuffer(toString(((HCFru)fru)));
	buf.append("\nDevice: " + fru.getDevice());
	buf.append("\nDisk ID: " + fru.getDiskId());
	buf.append("\nMode: " + fru.getMode());
	buf.append("\nNode ID: " + fru.getNodeId());
	buf.append("\nPath: " + fru.getPath());
	buf.append("\nTotal Capacity: " + fru.getTotalCapacity());
	buf.append("\nUsed Capacity: " + fru.getUsedCapacity());
	return buf.toString();
    }
    
    /**
     * Debug method to output the contents of HCNode since no toString() method
     * is provided by those classes.
     * @param fru the HCNode to output contents of 
     * @return String contents dump of fru
     */
    public String toString(HCNode fru) {
	
	StringBuffer buf = new StringBuffer(toString(((HCFru)fru)));
	buf.append("\nDisk Count: " + fru.getDiskCount());
	buf.append("\nHostname: " + fru.getHostname());
	buf.append("\nNode ID: " + fru.getNodeId());
	buf.append("\nIs Alive: " + fru.isIsAlive());
	buf.append("\nIs Eligible: " + fru.isIsEligible());
	buf.append("\nIs Master: " + fru.isIsMaster());
	buf.append("\nIs Vice Master: " + fru.isIsViceMaster());
	return buf.toString();
    }
    
}
