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


package com.sun.honeycomb.admin.mgmt.server;

import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagCellData;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagsRegistry;
import com.sun.servicetag.Registry;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The service tag adapter allows callee to retrieve all the defined
 * service tag entries for all cells, the status of the service tag
 * service, and the status of the registry file.  
 */
public class HCServiceTagsAdapterBase 
extends HCServiceTags 
implements HCServiceTagsAdapterInterface {
    
    private static final Logger logger = 
            Logger.getLogger(HCServiceTagsAdapterBase.class.getName());
    
    /**
     * Populate the various fields associated with the adapter so
     * they can be returned to the caller
     * @throws java.lang.InstantiationException
     */
    public void loadHCServiceTags()
    throws InstantiationException {
        ClusterProperties props = ClusterProperties.getInstance();
        boolean disabled = props.getPropertyAsBoolean(
                ConfigPropertyNames.PROP_SERVICE_TAG_SERVICE_DISABLED);
        setStatus(BigInteger.valueOf(
                disabled ? CliConstants.SERVICE_TAG_SERVICE_STATUS_DISABLED 
                    : CliConstants.SERVICE_TAG_SERVICE_STATUS_ENABLED));
        if (disabled == true) {
            // The service is disabled according to the setting
            // make sure this is a single cell system.
            if (MultiCellLib.getInstance().isCellStandalone() == false) {
                // It's not.  Technically this should never happen
                logger.log(Level.WARNING, 
                        "Invalid state of disabled for Service Tag Service found. "
                        + "Service can't be disabled on a multi-cell hive. "
                        + "Re-enabled service.");
                try {
                    enable(BigInteger.ZERO);
                }
                catch (MgmtException me) {
                    logger.log(Level.WARNING, 
                            "Re-enable of service tag service failed.");
                    setStatus(BigInteger.valueOf(
                        CliConstants.SERVICE_TAG_SERVICE_STATUS_UNKNOWN));
                }
            }
        }
    }
    
    
    /**
     * Fetch all the service tags entries from silo_info.xml 
     * and populate the list associated with this object
     * @param list the list to add the entries to
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public void populateData(List<HCServiceTagCellData> list) 
    throws MgmtException {
        list.clear();
        
        ServiceTagCellData[] cellEntries =
                MultiCellLib.getInstance().getServiceTagDataForAllCells();
        for (int i=0; i < cellEntries.length; i++) {
            HCServiceTagCellData entry = new HCServiceTagCellData();
            ServiceTagData data = cellEntries[i].getServiceTagData(); 
            entry.setCellId(cellEntries[i].getCellId());
            entry.setMarketingNumber(data.getMarketingNumber());
            entry.setProductNumber(data.getProductNumber());
            entry.setProductSerialNumber(data.getProductSerialNumber());
            list.add(entry);
        }
    }
    
    /**
     * Clear the registry file. On a hive we do this by simply removing
     * the registration file.  If this file has to be shared in the
     * future the entries should be removed one by one instead
     * @return int status of removal, 0 for sucess,
     * CliConstants.SERVICE_TAG_REGISTRY_DELETE_FAILURE for failure
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public BigInteger clearRegistry()
    throws MgmtException {
        return ServiceTagsRegistry.remove() 
            ? BigInteger.ZERO 
            : BigInteger.valueOf(CliConstants.SERVICE_TAG_REGISTRY_DELETE_FAILURE);
    }
        
    /**
     * Disable service tags.  This operation is only valid on a single cell
     * system.  This operation should only be performed when two
     * cells share the same product serial number but are not joined into a 
     * hive.  If both cells will be running as independent cells, service tags 
     * on one cell must be disabled to prevent duplicate service tag 
     * registration entries from being created.
     * @param dummy
     * @return BigInteger, 0 for SUCCESS, 
     * CliConstants.FAILURE (-1) for general failure,
     * CliConstants.SERVICE_TAG_REGISTRY_DELETE_FAILURE
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public BigInteger disable(BigInteger dummy) 
    throws MgmtException {
        if (MultiCellLib.getInstance().isCellStandalone() == false) {
            throw new MgmtException(
                    "Service tags service can only be disabled on a single cell system.");
        }
        ClusterProperties props = ClusterProperties.getInstance();
        HashMap map = new HashMap();
        map.put(ConfigPropertyNames.PROP_SERVICE_TAG_SERVICE_DISABLED,
                Boolean.TRUE.toString());
        try {
            props.putAll(map);
        }
        catch (ServerConfigException e) {
            return BigInteger.valueOf(CliConstants.FAILURE);
        }
        setStatus(BigInteger.valueOf(
                CliConstants.SERVICE_TAG_SERVICE_STATUS_DISABLED));
        try {
            // Now that the servie is disabled clear the registry
            return clearRegistry();
        }
        catch (SecurityException se) {
            throw new MgmtException(
                "Insufficient priviledges to remove service tag registry file from system.");
        }
    }
    
    /**
     * Enable the service tag service.   This only enables it
     * It does not cause a rebuild of the service tag data or cause
     * a validation of it.
     * @param dummy 
     * @return BigInteger, 0 for SUCCESS, -1 for failure
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public BigInteger enable(BigInteger dummy) 
    throws MgmtException {
        try {
        ClusterProperties props = ClusterProperties.getInstance();
        HashMap map = new HashMap();
        map.put(ConfigPropertyNames.PROP_SERVICE_TAG_SERVICE_DISABLED,
                Boolean.FALSE.toString());
        try {
            props.putAll(map);
        }
        catch (ServerConfigException e) {
            throw new MgmtException("Failed to enable service tag service.");
        }
        }
        catch (Throwable t) {
            logger.log(Level.SEVERE, "enable", t);
        }
        setStatus(BigInteger.valueOf(
                CliConstants.SERVICE_TAG_SERVICE_STATUS_ENABLED));
        return BigInteger.ZERO;
    }
    
    /**
     * Dummy operation required by MOF.  Not used.
     * @param list
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public void setData(List<HCServiceTagCellData> list)
    throws MgmtException {
    }
    
    /**
     * Get the status of the service tag registry file
     * @return BigInteger status of registry file. Possible values
     * are: CliConstants.SERVICE_TAG_REGISTRY_ENTRIES_FOUND,
     * CliConstants.SERVICE_TAG_REGISTRY_NO_ENTRIES_FOUND,
     * CliConstants.SERVICE_TAG_REGISTRY_STATUS_UNKNOWN
     */
    public BigInteger getRegistryStatus() {
        if (ServiceTagsRegistry.isSupported() == false) {
            // Required packages aren't installed so yes the registry
            // file needs to be updated.  An update can only
            // happen if the ServiceTags package are installed
            return BigInteger.valueOf(
                    CliConstants.SERVICE_TAG_REGISTRY_STATUS_UNKNOWN);
        } else {
            return BigInteger.valueOf(ServiceTagsRegistry.getRegistryStatus());
        }
    }
}
