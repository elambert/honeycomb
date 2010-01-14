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



package com.sun.honeycomb.adm.client;

import com.sun.honeycomb.adm.cli.AdmException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import com.sun.honeycomb.admin.mgmt.client.HCNodes;
import com.sun.honeycomb.admin.mgmt.client.HCFru;
import com.sun.honeycomb.admin.mgmt.client.HCFrus;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCDDTasks;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.admin.mgmt.client.HCDisks;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import com.sun.honeycomb.admin.mgmt.client.HCSiloProps;
import com.sun.honeycomb.admin.mgmt.client.HCExpProps;
import com.sun.honeycomb.admin.mgmt.client.HCSensor;
import com.sun.honeycomb.admin.mgmt.client.HCSetupCell;
import com.sun.honeycomb.admin.mgmt.client.HCVersions;
import com.sun.honeycomb.admin.mgmt.client.HCPerfStats;
import com.sun.honeycomb.admin.mgmt.client.HCSP;
import com.sun.honeycomb.admin.mgmt.client.HCServiceTagCellData;
import com.sun.honeycomb.admin.mgmt.client.HCSwitch;
import com.sun.honeycomb.mgmt.common.MgmtException;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.logging.Level;

import java.math.BigInteger;
/**
 * This interface defines the set of operations that any administrative client
 * can perform on a @HoneycombProductName@ hive.
 */
public interface AdminClient {

    public static final int KBYTE = 1024;
    
    public static final long NOT_LOGGED_IN_SESSION_ID = -1;
    
    /*
     * Retreieve Mgmt Software entities-- HCSilo, HCCell, ...
     */
    /**
     * GetCells "ignoreDeadCells" option will return a fully populated
     * array of HCCells, even if the actual cells referenced aren't 
     * reachable. If the cell in question isn't reachable,
     * the "isAlive" property will be false, and the only populated
     * values in the object will be the cellid. 
     */
    public HCCell[] getCells(boolean ignoreDeadCells)
        throws MgmtException, ConnectException;
    public HCCell getCell(byte cellId)
        throws MgmtException, ConnectException;
    public HCNodes getNodes (byte cellId)
        throws MgmtException, ConnectException;
    //
    // Note - inefficent; searches all nodes to find the FRU in question
    //
    /*
    public HCNode getNode (byte cell,String fru)
        throws  MgmtException, ConnectException;
    
    public HCNode getNode (HCCell cell,String fru)
        throws  MgmtException, ConnectException;
    */
    public int  getNumNodes (byte cellId)
        throws MgmtException, ConnectException;
    public int  getNumNodes (HCCell cell)
        throws MgmtException, ConnectException;
    public HCDisk getDisk (byte cell,String fru)
        throws MgmtException, ConnectException;
    
    /**
     * Get the specified Switch fru from the specified cell
     * @param cellId the id of the cell to communciate with to retrieve
     * the switch info
     * @param fruId the id of the switch
     * @return HCSwitch the switch fru object
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     */
    public HCSwitch getSwitch(byte cell, String fruId)
        throws MgmtException, ConnectException;

    /**
     * Get the switches for a specified cell
     * @param cellId the id of the cell to communciate with to retrieve
     * the switches
     * @return HCSwitch[] the switch fru objects for the cell
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     */
    public HCSwitch[] getSwitches(byte cellId)
        throws MgmtException, ConnectException;

    
    /**
     * Get the fru information for the service processor
     * @param cellId the id of the cell to communciate with to retrieve
     * the service processor fru object
     * @return HCSP the service processor fru object
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     */
    public HCSP getSp(byte cellid)
        throws MgmtException, ConnectException;
    

    public HCNode getMaster (byte cellId)
        throws MgmtException, ConnectException; 
    public HCNode getMaster (HCNodes nodes)  //WARNING - do not hit with stale cell
        throws MgmtException, ConnectException;
    
       
    /** 
     * Fetch the disk frus for the specified <code>cell</code>
     * <P>
     * <B>NOTE:</B> There is underlying problem with the HCDisk objects
     * returned by this method if you attempt invoke methods
     * like online() via the HCDisk object they will
     * fail with a NullPointerException from ClientData.invoke.
     * There is a apprently some yet undetermined difference between
     * how HCellAdapter creates these objects compared to 
     * how JAXP Fetcher.fetch method fetches single instance of the object.
     * Therefore if you need to perform actions on an object fetched
     * by the method you'll either need to refetch the object
     * via one of the helper methods like getDisk(cellid, fruid)
     * which retrieves the method via Fetcher.fetch
     * or invoke the action method onlineDisk(cellid, diskid) directly
     *
     * @param cellId the id of the cell to fetch the fru data from. 
     * @return HCDisk[] the array of disk fru's retrieved from the cell
     */
    public HCDisks getDisks(byte cellId)
        throws MgmtException, ConnectException;
    public HCNode getNode (byte cellId, String fru)
        throws MgmtException, ConnectException;
    public HCNode getNode (byte cellId, int nodeId)
        throws MgmtException, ConnectException;

    /**
     * @return int the max number of disks on a given node
     */
    public int getNumDisksPerNode(HCCell cell)
       throws MgmtException, ConnectException;
    public HCDisk[] getDisksOnNode (HCDisks disks, int nodeId)
        throws MgmtException, ConnectException;
    public HCDisk[] getDisksOnNode (byte cellId, int nodeId)
        throws MgmtException, ConnectException;

    public HCCellProps getCellProps(byte cellId)
        throws MgmtException, ConnectException;
    public HCCellProps getCellProps(HCCell cell)
        throws MgmtException, ConnectException;
    public void setCellProps(byte cellId,HCCellProps newProps) 
        throws MgmtException, ConnectException, PermissionException;
    public void setCellProps(HCCell cell,HCCellProps newProps)
        throws MgmtException, ConnectException, PermissionException;


    public HCSetupCell getSetupCell()
        throws MgmtException, ConnectException;
    public void setSetupCell(HCSetupCell newProps) 
        throws MgmtException, ConnectException, PermissionException,
        AdmException;
    public boolean getSwitchesState(byte cellId) 
        throws MgmtException,ConnectException;


    public HCSiloProps getSiloProps()
        throws MgmtException, ConnectException;
    public int setSiloProps(HCSiloProps newProps) 
        throws MgmtException, ConnectException, PermissionException;

    public int getNumCells()
        throws MgmtException, ConnectException;
    public String getAdminVip(byte cellId)
        throws MgmtException,ConnectException;


    public int getNumAliveDisks (byte cellId)
        throws MgmtException, ConnectException;
    public int getNumAliveNodes(byte cellId)
        throws MgmtException, ConnectException;
    public boolean getPossibleDataLoss(byte cellId)
        throws MgmtException, ConnectException;
    public long getQueryIntegrityTime(byte cellId)
        throws MgmtException, ConnectException;
    //
    // These three methods can and will throw MgmtExceptions
    // if data services are unavailable. 
    //
    public int  getUnhealedFailures(byte cellId)
        throws MgmtException, ConnectException;
    public int  getUnhealedFailuresUnique(byte cellId)
        throws MgmtException, ConnectException;
    public long getEndTimeLastRecoveryCycle(byte cellId)
        throws MgmtException, ConnectException;

    
    /**
     * Get the available free disk space for the cell.
     * This routine returns takes into account the high watermark for hard 
     * drives and makes a basic guess about the 5+2 fragments that the
     * 5800 system uses, it won't be totally accurate but it will give a 
     * good indication of the amount of space available. This adjustment
     * has been requested by marketing.
     * @param cell the cell to retreive the available free space from
     * @return long MBybtes of available space adjusted for system use.
     * This routine should not be used for reporting physical space.
     */
    public double getFreeDiskSpace (byte cellId)
        throws MgmtException, ConnectException;

    //
    // Warning - if you call these with a stale cell, you'll get stale information!
    //

    public boolean getPossibleDataLoss(HCCell cell)
        throws MgmtException, ConnectException;
    public int  getUnhealedFailures(HCCell cell)
        throws MgmtException, ConnectException;
    public int  getUnhealedFailuresUnique(HCCell cell)
        throws MgmtException, ConnectException;
    public long getEndTimeLastRecoveryCycle(HCCell cell)
        throws MgmtException, ConnectException;
    public long getQueryIntegrityTime(HCCell cell)
        throws MgmtException, ConnectException;
    //
    // end warning
    //
    public int getNumAliveNodes(HCNodes nodes)
        throws MgmtException, ConnectException;
    public int getNumAliveDisks (HCNodes nodes)
        throws MgmtException, ConnectException;
    /**
     * Get the available free disk space for the cell.
     * This routine returns takes into account the high watermark for hard 
     * drives and makes a basic guess about the 5+2 fragments that the
     * 5800 system uses, it won't be totally accurate but it will give a 
     * good indication of the amount of space available. This adjustment
     * has been requested by marketing.
     * @param cell the cell to retreive the available free space from
     * @return long MBybtes of available space adjusted for system use.
     * This routine should not be used for reporting physical space.
     */
    public double getFreeDiskSpace(HCDisks disks)
        throws MgmtException, ConnectException;
    
    /** 
     * Fetch the fru for the specified <code>cellid</code>
     * <P>
     * <B>NOTE:</B> There is underlying problem with the HCFru objects
     * returned by this method if you attempt invoke methods
     * like online() from classes that subclass HCFru like HCDisk they will
     * fail with a NullPointerException from ClientData.invoke.
     * There is a apprently some yet undetermined difference between
     * how HCellAdapter creates these objects compared to 
     * how JAXP Fetcher.fetch method fetches single instance of the object.
     * Therefore if you need to perform actions on an object fetched
     * by the method you'll either need to refetch the object
     * via one of the helper methods like getDisk(cellid, fruid)
     * which retrieves the method via Fetcher.fetch
     * or invoke the action method onlineDisk(cellid, diskid) directly
     *
     * @param cellid the id of the cell to fetch the frus from
     * @return HCFru[] the array of fru's retrieved from the cell
     */
    public HCFrus  getFRUs(byte cellId)
        throws MgmtException, ConnectException;   


    
    public int wipeDisks()
        throws MgmtException, ConnectException, PermissionException; 
    
    /**
     * Wipe disk for the specified cell
     * @param cellid the cell to wipe the disks on
     * @return int 0 on success, -1 on failure
     */
    public int wipeDisks (byte cellId)        
        throws MgmtException, ConnectException, PermissionException; 

    /**
     * Wipe disk for the specified cell
     * @param cell the cell to wipe the disks on
     * @return int 0 on success, -1 on failure
     */
    public int wipeDisks (HCCell cell)
        throws MgmtException, ConnectException, PermissionException;


    /**
     * Disk Management
     */
    
    /**
     * Enable disk.  Disk state may be any state except ENABLED and FOREIGN
     * @param cellid the cell id
     * @param diskId the disk Id
     * @return int 0 on success, -1 on failure
     */
    public int enableDisk (byte cellId,String diskId) 
        throws MgmtException, ConnectException, PermissionException; 


    
    /**
     * Enable disk.  Disk state may be any state except ENABLED and FOREIGN
     * @param disk the disk fru to offline
     * @return int 0 on success, -1 on failure
     */
    public int enableDisk (HCDisk disk) 
        throws MgmtException, ConnectException, PermissionException; 
    
    /**
     * Disable disk.  Disk state must be ENABLED before calling
     * @param cellid the cell id
     * @param diskId the disk Id
     * @return int 0 on success, -1 on failure
     */
    public int disableDisk (byte cellId,String diskId) 
        throws MgmtException, ConnectException, PermissionException;  
    
    /**
     * Disable disk.  Disk state must be ENABLED before calling
     * @param d the disk fru to disable
     * @return int 0 on success, -1 on failure
     */
    public int disableDisk (HCDisk d) 
        throws MgmtException, ConnectException, PermissionException; 
    
    /**
     * Online disk.  Disk state must be OFFLINE before calling
     * @param cellid the cell id
     * @param diskId the disk Id
     * @return int 0 on success, -1 on failure
     */
    public int onlineDisk (byte cellId,String diskId) 
        throws MgmtException, ConnectException,PermissionException;
    
    /**
     * Online disk.  Disk state must be OFFLINE before calling
     * @param d the disk fru to online
     * @return int 0 on success, -1 on failure
     */
    public int onlineDisk (HCDisk d) 
        throws MgmtException, ConnectException, PermissionException;

    /**
     * Offline disk. Disk state must be DISABLED before calling.  After successfull
     * completion state of disk will be OFFLINE
     * @param cellid the cell id
     * @param diskId the disk Id
     * @return int 0 on success, -1 on failure
     */
    public int offlineDisk (byte cellId,String diskId) 
        throws MgmtException, ConnectException,PermissionException;
    
    /**
     * Offline disk. Disk state must be DISABLED before calling.  After successfull
     * completion state of disk will be OFFLINE
     * @param d the disk fru to offline
     * @return int 0 on success, -1 on failure
     */
    public int offlineDisk (HCDisk d) 
        throws MgmtException, ConnectException, PermissionException;
    
    /**
     * Wipe disk, totaly cleans and reforts the disk (not just the data
     * partition but the entire disk). Disk state must be DISABLED before calling.
     * After successful execution the disk is put in the ENABLED state.
     * @param cellid the cell id
     * @param diskId the disk Id
     * @return int 0 on success, -1 on failure
     */
    public int wipeDisk (byte cellId,String diskId) 
        throws MgmtException, ConnectException,PermissionException;
    
    /**
     * Wipe disk, totaly cleans and reforts the disk (not just the data
     * partition but the entire disk). Disk state must be DISABLED before calling.
     * After successful execution the disk is put in the ENABLED state.
     * @param d the Disk fru to wipe
     * @return int 0 on success, -1 on failure
     */
    public int wipeDisk (HCDisk d) 
        throws MgmtException, ConnectException, PermissionException;

    /**
     * passwd
     */
    public String getCryptedPasswd() 
        throws MgmtException, ConnectException;
    public boolean setPasswd(String cryptedPass) 
        throws MgmtException, ConnectException, PermissionException; 
    public void setPublicKey(String key) 
        throws  MgmtException, ConnectException, PermissionException; 
    
    /**
     * Set the protocol password used for webdev
     * @param realmName the realm name
     * @param userName the user name
     * @param hash the hashed password
     * @return BigInteger 
     * CliConstants.SUCCESS for success, 
     * CliConstants.FAILURE for failure
     * @throws MgmtException for Illegal realm or user name
     */
    public int setProtocolPassword(String realmName,String userName, byte[] hash) 
        throws MgmtException, ConnectException,PermissionException;
    
    
    /**
     * Login
     */
    public boolean loggedIn()
        throws MgmtException, ConnectException, PermissionException;
    public void    logout()
        throws MgmtException, ConnectException;


    /**
     *  Get session id of logged in user
     */
    public long getLoggedInSessionId();
    
    
   /**
    * Set alertAddresses To: list for the alert notifications
    * @param alertAddressesthe list of email addresses to use for the To:
     * @return int the return code,
     * CliConstants.SUCCESS for success
     * CliConstants.FAILURE for general failure
    */ 
    public int setAlertTo(String[] alertAddresses)
        throws MgmtException, ConnectException, PermissionException;
    
    /**
     * Set alertAddresses Cc: list for the alert notifications
     * @param alertAddressesthe list of email addresses to use for the Cc: 
     * @return int the return code,
     * CliConstants.SUCCESS for success
     * CliConstants.FAILURE for general failure
     */
    public int setAlertCc(String[] alertAddresses)
        throws MgmtException, ConnectException, PermissionException; 
    
    /**
     * Add the specified email address to the To: list for the alert notifications
     * @param alertEmail the email address to add to the To: list
     * @return int the return code,
     * CliConstants.SUCCESS for success
     * CliConstants.FAILURE for general failure
     */ 
    public int addAlertTo(String alertEmail)
        throws MgmtException, ConnectException, PermissionException;
    
    /**
     * Add the specified email address to the Cc: list for the alert notifications
     * @param alertEmail the email address to add to the Cc: list
     * @return int the return code,
     * CliConstants.SUCCESS for success
     * CliConstants.FAILURE for general failure
     */ 
    public int addAlertCc(String alertEmail)
        throws MgmtException, ConnectException, PermissionException; 
    
    /**
     * Delete the specified To: email address from the alert notification
     * @param alertEmail the email address to delete from the to list
     * @return int the return code,
     * CliConstants.SUCCESS for success
     * CliConstants.FAILURE for general failure,
     * CliConstants.NOT_FOUND for alertEmail address specified
     * not found in current email list
     */ 
    public int delAlertTo(String alertEmail)
        throws MgmtException, ConnectException, PermissionException;
    
    
    /**
     * Delete the specified Cc: email address from the alert notification
     * @param alertEmail the email address to delete from the to list
     * @return int the return code,
     * CliConstants.SUCCESS for success
     * CliConstants.FAILURE for general failure,
     * CliConstants.NOT_FOUND for alertEmail address specified
     * not found in current email list
     */ 
    public int delAlertCc(String alertEmail)
        throws MgmtException, ConnectException, PermissionException; 
    
    /**
     * Get the To: email addresses that will be used to send alert notifications to
     * @return String[] the To: email addresses that will be used to send alert notifications to
     */ 
    public String[] getAlertTo()
        throws MgmtException, ConnectException;
    
    /**
     * Get the Cc: email addresses that will be used to send alert notifications to
     * @return String[] the Cc: email addresses that will be used to send alert notifications to
     */ 
    public String[] getAlertCc()
        throws MgmtException, ConnectException;

    /**
     * License
     */

    public void setLicense(byte cellId,String license)
        throws MgmtException, ConnectException, PermissionException;  
    public String getLicense(byte cellId)
        throws MgmtException, ConnectException;

    /**
     * Hive Management -- reboot, powerOff...
     */
    public HCVersions getVersions (byte cellId)
        throws MgmtException, ConnectException;

    /**
     * Power node on
     * @param cellId the cell id
     * @param nodeId the node id to power on
     * @return int
     * CliConstants.FAILURE for general failure, 
     * CliConstants.POWER_ON_HONEYCOMB_SERVICES_DOWN to indicate that
     * the node powered up but the @HoneycombProductName@ services failed to start
     */
    public int powerNodeOn (byte cellId, int nodeid) 
        throws MgmtException, ConnectException, PermissionException;    

    /**
     * Power node on
     * @param cell the cell
     * @param nodeId the node id to power on
     * @return int
     * CliConstants.SUCCESS for success,
     * CliConstants.FAILURE for general failure, 
     * CliConstants.POWER_ON_HONEYCOMB_SERVICES_DOWN to indicate that
     * the node powered up but the @HoneycombProductName@ services failed to start
     */
    public int powerNodeOn (HCCell cell, int nodeid)
        throws MgmtException, ConnectException, PermissionException;

    
    /**
     * Power node off.   
     * <P>
     * If the node being powered off is the master node on the master cell
     * this command will return immediately after issuing the power off
     * command.  The callee should force a logout of the user.  For
     * all other nodes this call blocks until the system status for
     * the node is offline or the reassure thread has timed out.
     *
     * @param cellId the cell id
     * @param nodeId the node id to power of
     * @return int
     * CliConstants.SUCCESS for success,
     * CliConstants.FAILURE for general failure, 
     */
    public int powerNodeOff (byte cellId, int nodeid,boolean useIpmi) 
        throws MgmtException, ConnectException, PermissionException;   

    /**
     * Power node off
     * <P>
     * If the node being powered off is the master node on the master cell
     * this command will return immediately after issuing the power off
     * command.  The callee should force a logout of the user.  For
     * all other nodes this call blocks until the system status for
     * the node is offline or the reassure thread has timed out.
     *
     * @param cell the cell
     * @param nodeId the node id to power off
     * @return int
     * CliConstants.SUCCESS for success,
     * CliConstants.FAILURE for general failure, 
     */
    public int powerNodeOff (HCCell cell, int nodeid,boolean useIpmi)
        throws MgmtException, ConnectException, PermissionException;


    public void rebootNode (byte cellId, int nodeid) 
        throws MgmtException, ConnectException, PermissionException;    

    public void rebootCell(byte cellId, boolean switches, boolean sp)  
        throws MgmtException, ConnectException, AdmException,
        PermissionException;
   
    public void rebootCell(HCCell cell, boolean switches, boolean sp)
        throws MgmtException, ConnectException, AdmException,
        PermissionException;
 
    /**
     * Power off cell and service node if <code>sp</code> is true.
     * <P>
     * Callee should check isClusterSane and hasQuroum first.
     * If both aren't true, it's not safe to shutdown
     *
     * @param cellId the id of the cell to power down
     * @param sp boolean flag, true indicates that service node should be
     * powered down, false indicates don't power down service node
     * @param useIpmi boolean flag, true if ipmi should be used to power off
     * node, false otherwise
     * @return 0 on success, -1 on failure
     */
    public int powerOff (byte cellId,boolean sp,boolean useIpmi)  
        throws MgmtException, ConnectException, PermissionException;

    /**
     * Power off cell and service node if <code>sp</code> is true.
     * <P>
     * Callee should check isClusterSane and hasQuroum first.
     * If both aren't true, it's not safe to shutdown
     *
     * @param cell the cell to power down
     * @param sp boolean flag, true indicates that service node should be
     * powered down, false indicates don't power down service node
     * @param useIpmi boolean flag, true if ipmi should be used to power off
     * cell, false otherwise
     * @return 0 on success, -1 on failure
     */
    public int powerOff (HCCell cell,boolean sp,boolean useIpmi)
        throws MgmtException, ConnectException, PermissionException;

    //
    // Versions that take HCCell instead of cellid, to allow for optimizing
    //


    /**
     * Hive configuration
     */
    public byte getMasterCellId() 
        throws MgmtException, ConnectException, PermissionException; 
    public int addCell(String adminVIP, String dataVIP)  
        throws MgmtException, ConnectException, PermissionException;
    public int delCell(byte cellId)  
        throws MgmtException, ConnectException, PermissionException;

    public boolean getProtocolStatus(byte cellId)
        throws MgmtException, ConnectException; 
    public boolean hasQuorum(byte cellId)
        throws MgmtException, ConnectException;
    public boolean isClusterSane(byte cellId)
        throws MgmtException, ConnectException;
    public BigInteger getCacheStatus(byte cellId)
        throws MgmtException,ConnectException;
    public long getLastHADBCreateTime(byte cellId)
        throws MgmtException,ConnectException;
    public String getHadbStatus(byte cellId)
        throws MgmtException,ConnectException;

    public boolean getProtocolStatus(HCNodes nodes)
        throws MgmtException, ConnectException;
    public boolean hasQuorum(HCCell cell)
        throws MgmtException, ConnectException;


    /**
     * NTP related
     */   
    public int verifyNtpServers(String ntpServers)
        throws MgmtException, ConnectException;
    public String getDate(byte cellId)
        throws MgmtException, ConnectException;

    /**
     * Localization
     */
    public String[] getLanguages()
        throws MgmtException, ConnectException;
    public void   setLocale (String language)   
        throws MgmtException, ConnectException, PermissionException;
    public String getLocale()
        throws MgmtException, ConnectException;

    
    /**
     * mdconfig support
     */
    /**
     * @deprecated. use either getSchema or pushSchema instead. Otherwise 
     * you can't inspect the schema when not logged in.
     */
    public int updateSchema(InputStream input,
                             PrintStream output,
                             boolean commit,
                             boolean dump,
                             boolean template)  
        throws MgmtException, ConnectException, PermissionException, 
        IOException;
    public int getSchema(PrintStream out, boolean template)
        throws MgmtException, ConnectException, IOException;
    public int pushSchema(InputStream input, boolean validateOnly)
        throws MgmtException, ConnectException, IOException, PermissionException;

    public int clearSchema()  
        throws MgmtException, ConnectException, PermissionException;
    public int retrySchema()  
        throws MgmtException, ConnectException, PermissionException;

    /**
     * perf statistics gathering
     */
    public HCPerfStats getClusterPerfStats(int interval, byte cellId) 
        throws MgmtException, ConnectException;
    public HCPerfStats getNodePerfStats(int nodeId, int interval, byte cellId) 
        throws MgmtException, ConnectException;


    /** 
     * Sensors
     */
    public HCSensor getSensors (byte cellId,int nodeId)
        throws MgmtException, ConnectException;


    /**
     * DataDoctor Information
     */
    public HCDDTasks getDdTasks(byte cellId)
        throws MgmtException, ConnectException;

    /**
     * Cell expansion functions
     */
    public void startExpansion(byte cellId)
        throws MgmtException, ConnectException, PermissionException; 
    public void cancelExpansion(byte cellId)
        throws MgmtException, ConnectException, PermissionException; 
    public int  getExpansionStatus(byte cellId)
        throws MgmtException, ConnectException, PermissionException; 

    public void startExpansion(HCCell cell)
        throws MgmtException, ConnectException, PermissionException;
    public void cancelExpansion(HCCell cell)
        throws MgmtException, ConnectException, PermissionException;
    public int  getExpansionStatus(HCCell cell)
        throws MgmtException, ConnectException, PermissionException;
    /**
     * Upgrade support
     */

    /**
     * Mount the iso image
     * @param cellid - the cell id of the cell being upgraded
     * @param spDvd - boolean indicating if image is on DVD
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int mountIso(byte cellid, boolean spDvd)  
        throws MgmtException, ConnectException,PermissionException; // write mode 
    /**
     * Unmount the iso image
     * @param cellid - the cell id of the cell being upgraded
     * @param spDvd - boolean indicating if image is on DVD
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int uMountIso(byte cellid, boolean spDvd) 
        throws MgmtException, ConnectException,PermissionException; // write mode    

    /**
     * Run preliminary status check on cluster state
     * @param cellid - the cell id of the cell being upgraded
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int statusCheck(byte cellid)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Initialize or setup the upgrader class
     * @param cellid - the cell id of the cell being upgraded
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int initializeUpgrader(byte cellid)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Get the next question to ask the user for interactive options
     * @param cellid - the cell id of the cell being upgraded
     * @return string the next question to display on the CLI
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public String getNextQuestion(byte cellid)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Get the confirmation question to ask the user 
     * @param cellid - the cell id of the cell being upgraded
     * @return String the confirmation question
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public String getConfirmQuestion(byte cellid)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Get and invoke the next method to run for interactive options
     * @param cellid - the cell id of the cell being upgraded
     * @param answer - user response to question asked
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int invokeNextMethod(byte cellid, boolean answer)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Set state and run methods if force option used.
     * @param cellid - the cell id of the cell being upgraded
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int setForceOptions(byte cellid)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Download the upgrade jar for an http upgrade
     * @param cellid - the cell id of the cell being upgraded
     * @param src - the url of the iso image
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int downloadJar(byte cellid, String src)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Copy the upgrade jar from mounted image to the expected location
     * @param cellid - the cell id of the cell being upgraded
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int copyJar(byte cellid)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Download the iso image via http.
     * @param cellid - the cell id of the cell being upgraded
     * @param url - the url of the iso image to be downloaded
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int httpFetch(byte cellid, String url)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Start the upgrade - main upgrade method.
     * @param cellid - the cell id of the cell being upgraded
     * @param spdvd - boolean indicating if image is on dvd
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int startUpgrade(byte cellid, boolean spDvdb)
	throws MgmtException, ConnectException, PermissionException;

    /**
     * Finish the upgrade - post-upgrade tasks
     * @param cellid - the cell id of the cell being upgraded
     * @param spdvd - boolean indicating if this is a DVD upgrade
     * @param success - boolean indicating if upgrade was a success
     * @return int the return status code
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int finishUpgrade(byte cellid, int type, boolean success)
	throws MgmtException, ConnectException, PermissionException;

    // Get the max authorized clients allowed on cli.
    public int getMaxNumAuthClients(HCSiloProps siloProps)
        throws MgmtException, ConnectException;


    // NDMP backup/restore

    public String getBackupStatus (byte cellid)
        throws MgmtException, ConnectException, AdmException;
    
    /**
     * Collects the service node, switches and node logs for all cells and then
     * posts the results via https
     *
     * @return int 0 on success, -1 on failure
     * @throws MgmtException
     * @throws ConnectException
     * @throws PermissionException
     */
    public int dumpLog()
        throws MgmtException, ConnectException, PermissionException;
    /**
     * Retrieve the saved explorer configuration properties
     * @return HCExpProps the current explorer configuration settings
     */
    public HCExpProps getExpProps()
        throws MgmtException, ConnectException;
    
    /**
     * Save the explorer default configurations to the cluster 
     * configuration properties file.
     * @param newProps the properties to save to the cluster configuration
     * property file. The contact name, contact email, contact phone, proxy
     * server, proxy port and geo settings will be user settable with the only
     * mandatory one being geo.  The explorer script won't run without
     * the geographic location being set in the cluster config file.
     */
    public void setExpProps(HCExpProps newProps)
        throws MgmtException, ConnectException, PermissionException; 


//     public int getBackupControlPort (byte cellId)
//         throws MgmtException,ConnectException;
//     public void setBackupControlPort (byte cellId, int port)
//         throws MgmtException,ConnectException;

//     public int getBackupOutboundDataPort (byte cellId)
//         throws MgmtException,ConnectException;
//     public void setBackupOutboundDataPort (byte cellId, int port)
//         throws MgmtException,ConnectException;

//     public int getBackupInboundDataPort (byte cellId)
//         throws MgmtException,ConnectException;
//     public void setBackupInboundDataPort (byte cellId, int port)
//         throws MgmtException,ConnectException;

//     public void setProceedAfterError (byte cellId, boolean proceed)
//         throws MgmtException, ConnectException, AdmException;

//     public boolean getProceedAfterError (byte cellId)
//         throws MgmtException, ConnectException, AdmException;

    
    /** 
     * Testing interfaces - used to allow emulator specific commands and input.
     * @return boolean true if in emulated mode, false otherwise
     * @throws MgmtException
     * @throws ConnectException
     */     
    public boolean isEmulated()
        throws MgmtException, ConnectException;

    /*
     *  Log audit message to external log file. This method should is not
     *  meant to be called by external clients. Curretnly only AdminClientImpl
     *  and AdminClientInternalImpl should call this to log audit messages.
     *  
     *  called
     *  @param  level   the external log level, Should be obtained from ExtLevel
     *  @param  msgKey  the message key in the properties file
     *  @param  params  the message parameters (all strings)
     *  @param  methodName  the method name that called extLog or the method
     *                      that should be displayed in the log message
     */
    public void extLog(Level level, String msgKey, Object [] params,
            String methodName);
 
}
