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

import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCServiceTagCellData;
import com.sun.honeycomb.admin.mgmt.client.HCServiceTags;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.servicetag.ServiceTag;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ServiceTagsUpdater is a helper class that contains method
 * related to the updating the serivce tag entries on the remote cells
 */
public class ServiceTagsUpdater {
 
    public static Logger logger = 
            Logger.getLogger(ServiceTagsUpdater.class.getName());
    
    /**
     * Reset the instanceURNs for the service tag entries associated
     * with this system.  
     * @param serialNumMap HashMap of service tag entries where all
     * entries have been verified for correctness and the map is
     * assumed to have been created via a call to createSerialNumGroupings();
     * 
     * @see ServiceTagsUtils#createSerialNumGroupings()
     */
    public static void resetInstanceURNs(HashMap serialNumMap) {
        assert(serialNumMap != null);
        Set keySet = serialNumMap.keySet();
        String keys[] = (String[])keySet.toArray(new String[keySet.size()]);
        for (int i=0; i < keys.length; i++) {
            List<HCServiceTagCellData> grouping = 
                    (List<HCServiceTagCellData>)serialNumMap.get(keys[i]);
            HCServiceTagCellData[] entries = (HCServiceTagCellData[])
                    grouping.toArray(new HCServiceTagCellData[grouping.size()]);
            String instanceURN = ServiceTag.generateInstanceURN();
            for (int j=0; j < entries.length; j++) {
                entries[j].setInstanceURN(instanceURN);
            }
        }
    }
    
    /**
     * Clear all instanceURNs.  This is done when a validation error
     * has been encountered.  If the entries are cleared
     * @param serialNumMap HashMap of service tag entries where all
     * entries have been verified for correctness and the map is
     * assumed to have been created via a call to createSerialNumGroupings();
     */
    private static void clearInstanceURNs(HCServiceTagCellData[] entries) {
        for (int i=1; i < entries.length; i++) {
            entries[i].setInstanceURN(null);
        }
    }
    
    /**
     * Scan the service tag entries to determine whether the instanceURNs
     * are already cleared or whether they need to be
     * @param entries the service tag data entries
     * @return boolean true if the instanceURNs in the passed in entries need
     * to be cleared, false otherwise
     */
    private static boolean needsToBeCleared(HCServiceTagCellData[] entries) {
        for (int i=1; i < entries.length; i++) {
            String value = entries[i].getInstanceURN();
            if (value == null || value.length() == 0)
                return true;
        }
        return false;
    }
    
    
    /**
     * Update the all the service tag data.   All messages related to
     * the failure of the command are sent via the evt 
     * handle passed when the remote command is executed.  Callee
     * should output the appropriate sucess message when the return code
     * is 0.
     * 
     * @param api handle to the AdminClient object
     * @param data The service tag data to update
     * @param updateRegistry boolean flag, true if service tag registry
     * file should be update, false otherwise.  When the callee knows
     * the service tag data is not valid updateRegistry will be false.
     * @return int the return status code, where 0 is success, any other
     * value represents a failure
     */
    public static int updateAllServiceTagData(
            AdminClient api, HCServiceTags data, boolean updateRegistry) 
        throws MgmtException, ConnectException, PermissionException {

        if(api.loggedIn() == false) 
            throw new PermissionException();
        HCCell[] cells = api.getCells(false);
        System.out.print("Updating service tag data");
        if (cells.length > 1)
            System.out.print(" on all cells");
        System.out.println(".");
        MultiCellOp [] tagUpdater = 
            MultiCellUtils.allocateMultiCellOp(
                MultiCellServiceTagsUpdater.class, cells);
        MultiCellRunner runner = new MultiCellRunner(tagUpdater);
        ServiceTagsCookie cookie = 
                new ServiceTagsCookie(data, updateRegistry);
        runner.setCookie(cookie);
        runner.start();
        return runner.waitForResult();
    }
    
    /**
     * Validate and update the service tag entries for the hive.  This call
     * should only be made when there is a change to the hive that would
     * required the service tag registry to be rebuilt.  Valid changes include:
     * <ul>
     * <li>A new cell is added to the hive.</li>
     * <li>A cell is removed from the hive.</li>
     * <li>The software running on the hive is updated.</li>
     * <li>The service tag data is updated (this case is handled via
     * the code in servicetags cli command)</li>
     * </ul>
     * <P>
     * If a validation error is detected a caution error will be outputed
     * to System.out with details of the problem.  The service tag
     * registry will be cleared of the existing entries since there
     * no longer valid. 
     * <P>
     * If no validation error is detected the instanceURNs are updated.
     * The updated service tag data is propogated to all cells and the
     * service tag registry is rebuilt.
     * @param api
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     * @throws com.sun.honeycomb.adm.cli.PermissionException
     */
    public static void validateAndUpdateServiceTags(AdminClient api)
        throws MgmtException, ConnectException, PermissionException {
        // Force a reload of SiloInfo we don't want cells list ever
        // to be stale.
        SiloInfo.forceReload();
        HCServiceTags tags = ServiceTagsUtils.getServiceTags(api);
        HCServiceTagCellData[] entries = ServiceTagsUtils.toArray(tags.getData());
        ServiceTagsValidator validator = new ServiceTagsValidator(entries);
        int retCode = ExitCodes.EX_OK;
        if (validator.isValid() == false) {
            // The service tag data is not valid
            StringBuffer buf = new StringBuffer();
            buf.append("\nCAUTION: Service Tag Data validation error encountered:\n\n");
            buf.append(validator.getValidationErorMsg());
            buf.append("\n\n");
            buf.append("A change to the system has been made that requires a rebuild of the service\n");
            buf.append("tag registry.   This can not be done due to the validation error encountered.\n");
            buf.append("Use the cli 'servicetags' command to correct this problem.\n");
            System.out.println(buf.toString());
	    logger.log(Level.WARNING, buf.toString());
            // If the instanceURNs are not all empty then clear the 
            // service tag registry
            clearServiceTagsRegistry(api, tags);
        } else {
            HashMap<String,List>map = validator.getSerialNumGroupings();
            resetInstanceURNs(map);
            retCode = updateAllServiceTagData(api, tags, true);
            if (retCode == ExitCodes.EX_OK) {
                System.out.println("Successfully update service tag registry.");
            }
        }
    }
    
    /**
     * Clear the service tag registry and reset the instanceURNs associated
     * with the service tag data for all cells.   If the instanceURNs
     * are all empty, ie having been previously cleared, no action is taken
     * @param api Handle the the AdminClient object
     * @param tags handle the HCServiceTags object.  This value may be null.
     * If null the data will be refetched from the master cell.
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     * @throws com.sun.honeycomb.adm.cli.PermissionException
     */
    public static void clearServiceTagsRegistry(AdminClient api, HCServiceTags tags)
        throws MgmtException, ConnectException, PermissionException {
        
        if (tags == null)
            tags = ServiceTagsUtils.getServiceTags(api);
        HCServiceTagCellData[] entries = ServiceTagsUtils.toArray(tags.getData());
        // Determine whether we need to clear the registry
        if (needsToBeCleared(entries)) {
            clearInstanceURNs(entries);
            System.out.println("Clearing service tag registry.");
            int retCode = updateAllServiceTagData(api, tags, false);
            if (retCode == ExitCodes.EX_OK) {
                System.out.println("Successfully cleared service tag registry.");
            }
        }
    }
    
    
}
