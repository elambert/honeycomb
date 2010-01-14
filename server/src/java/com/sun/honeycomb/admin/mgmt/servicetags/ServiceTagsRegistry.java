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


package com.sun.honeycomb.admin.mgmt.servicetags;

import com.sun.honeycomb.common.AdminResourcesConstants;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.common.MgmtException;

import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.Exec;
import com.sun.servicetag.Registry;
import com.sun.servicetag.ServiceTag;
import com.sun.servicetag.UnauthorizedAccessException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class contains the method that interact with the Service Tag Registry
 */
public class ServiceTagsRegistry {
    
    private static final Logger logger = 
            Logger.getLogger(ServiceTagsRegistry.class.getName());
    
    /**
     * The service tag registry file /var/sadm/servicetag/registry
     */
    private static final String REGISTRY_FILE =
            new StringBuffer(File.separator)
                .append("var")
                .append(File.separator)
                .append("sadm")
                .append(File.separator)
                .append("servicetag")
                .append(File.separator)
                .append("registry")
                .append(File.separator)
                .append("servicetag.xml").toString();
    
    public static final String REGISTRY_IS_NOT_AVAILABLE_MSG =
            "Service Tag Registry file can not be updated."
            + "\nService Tag packages are not installed. ";
    
    
    /**
     * Update the service tag registry with <code>tagEntries</code>.
     * <P>
     * The callee is assumed to have cleared the old entries out of the
     * service tag registry before calling this function.
     * 
     * @throws MgmtException on error.   Errors logged prior to throw
     */
    public static void update(ServiceTag[] tagEntries) 
    throws MgmtException {
        if (Registry.isSupported() == false) {
            throw new MgmtException(REGISTRY_IS_NOT_AVAILABLE_MSG);
        }
        
        try {
            Registry registry = Registry.getSystemRegistry();
            for (int i=0; i < tagEntries.length; i++) {
                try {
                    registry.addServiceTag(tagEntries[i]);
                }
                catch (IllegalArgumentException iae) {
                    // If we got this it means we've got a bug somewhere
                    logger.log(Level.WARNING, 
                            "Trying to add a service tag that already exists");
                    // Recover by removing the old one and then adding the new
                    registry.removeServiceTag(tagEntries[i].getInstanceURN());
                    registry.addServiceTag(tagEntries[i]);
                }
            }
        }
        catch (IOException io) {
            String msg = "Failed to create service tag registry entries.";
            logger.log(Level.WARNING, msg, io);
            throw new MgmtException(msg);
        }
        catch (UnauthorizedAccessException uae) {
            logger.log(Level.WARNING, uae.getMessage());
            throw new MgmtException(uae.getMessage());
        }
    }
    
    /**
     * Determine whether the Service Tag Registry can be supported
     * on this system. 
     * @return boolean, true when service tag packages necessary are installed,
     * false otherwise
     */
    public static boolean isSupported() {
        return Registry.isSupported();
    }
    
    /**
     * Remove all ST5800 service tag entries from the registry
     * This routine is mainly intended for the emulator.  One a live
     * system we just blow away registry file as we are the only
     * consumers on a emulated system we don't have permissions to
     * remove the file.
     * @param instanceURN the service tag registry entries to remove
     */
    public static void clear(String[] instanceURNs)
    throws MgmtException {
        if (instanceURNs.length == 0) {
            // Nothing to do
            return;
        }
        if (Registry.isSupported() == false) {
            throw new MgmtException(REGISTRY_IS_NOT_AVAILABLE_MSG);
        }
        try {
            Registry registry = Registry.getSystemRegistry();
            for (int i=0; i < instanceURNs.length; i++) {
                if (instanceURNs[i] != null && instanceURNs[i].length() != 0) {
                    // Make sure that the tag is there.   If it's not
                    // and we attempt to remove it we'll get an IO Exception
                    ServiceTag tag = registry.getServiceTag(instanceURNs[i]);
                    if (tag != null)
                        registry.removeServiceTag(instanceURNs[i]);
                }
            }
        }
        catch (IOException io) {
            StringBuffer buf = new StringBuffer();
            buf.append("Failed to clear service tag registry entries.");
            logger.log(Level.WARNING, buf.toString(), io);
            buf.append("Ensure that the servicetag\n");
            buf.append("entries are all valid by invoking the command, 'servicetags --refresh'.");
            throw new MgmtException(buf.toString());
        }
        catch (UnauthorizedAccessException uae) {
            logger.log(Level.WARNING, uae.getMessage());
            throw new MgmtException(uae.getMessage());
        }
    }
    
    /**
     * Retrieve all the unique instanceURNs from the stored service tag
     * data associated with each cell.  On a multi-cell hive two cells
     * will share the same instanceURN since they share the same top
     * level assemebly part # 
     * @param entries the service tag data associated with each cell in the hive
     * @return String[] array of unique instanceURNs
     */
    public static String[] getInstanceURNs(ServiceTagCellData[] entries) {
        List<String> list = new ArrayList<String>();
        for (int i=0; i < entries.length; i++) {
            String instanceURN = entries[i].getServiceTagData().getInstanceURN();
            if (instanceURN != null && list.contains(instanceURN) == false)
                list.add(instanceURN);
        }
        return (String[])list.toArray(new String[list.size()]);
    }
    
    /**
     * Remove the service tag registration file
     * @return boolean, true if file has been successfully removed, false
     * if the remove failed.
     */
    public static boolean remove() {
        File file = new File(REGISTRY_FILE);
        if (file.exists())
            return file.delete();
        return true;
    }
    
    /**
     * Get the status of the service tag registration file.
     * @return int status of the service tag registry file.
     * Possible values are:
     * <ul>
     * <li>0 - Registry file contains no ST5800 records
     * <li>1 - Registry file contains 1 or more ST5800 records.  This
     * should mean the registry is up to date.</li>
     * <li>CliConstants.SERVICE_TAG_REGISTRY_STATUS_UNKNOWN - Status is unknown</li>
     * </ul>
     * <P>
     * <B>Note:</B> This command is only valid if it's
     * executed on the master cell.
     */
    public static int getRegistryStatus() {
        if (Registry.isSupported() == false)
            return CliConstants.SERVICE_TAG_REGISTRY_STATUS_UNKNOWN;
        
        // There's no way for us to do this check via the ServiceTag Registry
        // Java API.  
        //
        // Submitted RFE 6661826 asking for new api.
        // until then do a simple but nasty grep looking for 5800
        // This is only going to work on Solars based Systems.  This is
        // considered a temporary workaround.
        String searchKey = ServiceTagsGenerator.getMessage(
                AdminResourcesConstants.KEY_SERVICE_TAG_SEARCH_STR, 
                new Object[0], "Sun StorageTek 5800");
        BufferedReader reader = null;
        try {
            reader = Exec.execRead("/bin/stclient -x");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(searchKey))
                    return CliConstants.SERVICE_TAG_REGISTRY_ENTRIES_FOUND;
            }
            return CliConstants.SERVICE_TAG_REGISTRY_NO_ENTRIES_FOUND;
        }
        catch (Error e) {
            logger.log(Level.WARNING, 
                    "Failed to retrieve service tag registry status", e);
            // Handle Unsatisfied Linke Error
            // ClassNotFoundException that may occur in emulator
            // if libjava-exec.so isn't present or isn't
            // built for the system we are running on
            return CliConstants.SERVICE_TAG_REGISTRY_STATUS_UNKNOWN;
        }
        catch (IOException ignore) {
            logger.log(Level.WARNING, 
                    "Failed to retrieve service tag registry status", ignore);
            return CliConstants.SERVICE_TAG_REGISTRY_STATUS_UNKNOWN;
        }
        finally {
            if (reader != null)
                try {
                    reader.close();
                }
                catch (IOException ignore) {}
        }
    }
}
