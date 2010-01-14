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
import com.sun.honeycomb.common.SoftwareVersion;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.multicell.lib.MultiCellLibError;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagCellData;

import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
import com.sun.servicetag.ServiceTag;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is responsible for converting the service tag data stored
 * into silo_info.xml for each cell and converting it into the service tag
 * data that will be registered and stored in the service tag repository.
 */
public class ServiceTagsGenerator {

    private static final Logger logger = 
            Logger.getLogger(ServiceTagsGenerator.class.getName());
    private ServiceTag[] serviceTags;
    boolean isValid = false;
    
    private static final String SERVICE_TAGS_ERROR_KEY = "ServiceTags: ";
    private static final String VENDOR = "Sun";
    private static final String PLATFORM_ARCH = "i386"; 
    private static final String NONE="None";
    
    /**
     * Fetch all the service tag data entries for the hive and then
     * process and validate them.
     * If all the entries are valid <code>isValid()</code> will return true.
     * If true then <code>getServiceTags()</code> can be used to retrieve
     * the service tag data that can be registred with 
     * service tag infrastructure.
     * @param entries the service tag data entries for all the cells in
     * the hive.
     */
    public ServiceTagsGenerator() {
        ServiceTagCellData[] entries = 
                MultiCellLib.getInstance().getServiceTagDataForAllCells();
        process(entries);
    }
    
    /**
     * Process and validate the service tag data entries for the hive.
     * If all the entries are valid <code>isValid()</code> will return true.
     * If true then <code>getServiceTags()</code> can be used to retrieve
     * the service tag data that can be registred with 
     * service tag infrastructure.
     * @param entries the service tag data entries for all the cells in
     * the hive.
     */
    public ServiceTagsGenerator(ServiceTagCellData[] entries) {
        process(entries);
    }
    
    
    /**
     * Group all the serivce tag data based on the serial numbers
     * @param entries the array of service tag cell entries to group
     * @return HashMap the service tag groups.  Key is serial number,
     * Object is List<ServiceTagCellData> where all objects have
     * the same serial numbers.
     */
    private HashMap<String,List> createSerialNumGroupings(ServiceTagCellData[] cellEntry) {
        // Create groupings based on the serial number.
        HashMap serialNumMap = new HashMap();
        for (int i=0; i < cellEntry.length; i++) {
            String key = cellEntry[i].getServiceTagData().getProductSerialNumber();
            if (serialNumMap.containsKey(key)) {
                List<ServiceTagCellData> same = 
                    (List<ServiceTagCellData>)serialNumMap.get(key);
                same.add(cellEntry[i]);
            } else {
                // We've never seen this service tag entry.  Add it to our map
                List<ServiceTagCellData> same = new ArrayList<ServiceTagCellData>();
                same.add(cellEntry[i]);
                serialNumMap.put(key, same);
            }
        }
        return serialNumMap;
    }
    

    /**
     * Check the service tag data associated with each cell to
     * make sure it's not null.   Failures are logged.
     * @param entries the entries to check for null fields
     * @return boolean true if passes check, false otherwise
     */
    private boolean passesNullFieldCheck(ServiceTagCellData[] entries) {
        boolean passes = true;
        for (int i=0; i < entries.length; i++) {
            ServiceTagData data = entries[i].getServiceTagData();
            if (isEmpty(data.getMarketingNumber()) 
                    || isEmpty(data.getProductNumber())
                    || isEmpty(data.getProductSerialNumber())) {
                logValidationError("Service tag information is missing for cell " 
                        + entries[i].getCellId());
                passes = false;
            }
        }
        return passes;
    }
    
    /**
     * Check to make sure all the fields have instanceURN  values
     * Check the service tag data associated with each cell to
     * make sure it's not null.   Failures are logged.
     * @param entries the entries to check for null fields
     * @return boolean true if passes check, false otherwise
     */
    private boolean passesInstanceURNCheck(ServiceTagCellData[] entries) {
        boolean passes = true;
        for (int i=0; i < entries.length; i++) {
            if (isEmpty(entries[i].getServiceTagData().getInstanceURN())) {
                logValidationError("Service tag information is missing for cell " 
                        + entries[i].getCellId());
                passes = false;
            }
        }
        return passes;
    }
    
    private boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
    
    
    
    /**
     * Perform a validation of all the entries in the list.  All the data fields
     * in the list need to match.  Failures are logged.   
     * 
     * @param list the serial number list to validate.  All entries in the list
     * already share the same serial #s
     * @return boolean true if validation check passes, false otherwise
     */
    private boolean validateSerialNumberGrouping(List<ServiceTagCellData> list) {
       
        // DO WE REALLY NEED THIS CHECK NOW.  
        // We'll only generate service tags when the instanceURNs fields
        // are present and on a multi-cell the only way for them to
        // get generated is via the servicetags --refresh operation.
        if (list.size() == 1) {
            // The list passed in is based on serial numbers.  All the
            // items in the list will have the same serial number.
            // If there is only 1 item in the list then no validation
            // is required.
            return true;
        }
        boolean productNumberMismatch = false;
        boolean marketingNumberMismatch = false;
        boolean instanceURNMismatch = true;
        ServiceTagCellData[] entries = 
            (ServiceTagCellData[])list.toArray(
                new ServiceTagCellData[list.size()]);
        
        // Each list has a minimum of at least 1 entry
        ServiceTagData baseEntry = entries[0].getServiceTagData();
        for (int i=1; i < entries.length; i++) {
            // A grouping is considered valid if the product number and
            // marketing numbers of all fields is the same.
            ServiceTagData data = entries[i].getServiceTagData();
            if (baseEntry.getProductNumber().equals(
                    data.getProductNumber()) == false) {
                productNumberMismatch = true;
            }
            if (baseEntry.getMarketingNumber().equals(
                    data.getMarketingNumber()) == false) {
                marketingNumberMismatch = true;
            }
            if (baseEntry.getInstanceURN().equals(
                    data.getInstanceURN()) == false) {
                logValidationError("Service tag instanceURN mismatch detected, "
                        + "servicetags --refresh required.");
                return false;
            }
        }
        if (marketingNumberMismatch || productNumberMismatch) {
            StringBuffer buf = new StringBuffer();
            if (marketingNumberMismatch && productNumberMismatch) {
                buf.append(
                    "Marketing number and product number mismatch detected.");
            } else {
                if (marketingNumberMismatch) {
                    buf.append("Marketing number mismatch detected.");
                } else {
                    buf.append("Product number mismatch detected.");
                }
            }
            buf.append(
                "All product numbers, marketing numbers, and instanceURNs must ");
            buf.append(
                "agree for all cells that share the product serial number, '");
            buf.append(baseEntry.getProductSerialNumber());
            buf.append("'.");
            logValidationError(buf.toString());
            return false;
        }
        if (entries.length > 2) {
            // Should we try this as a caution instead of an error to allow
            // for a hardware change in the future?
            StringBuffer buf = new StringBuffer();
            buf.append(
                "More than two cells detected with the same product serial number, '");
            buf.append(baseEntry.getProductSerialNumber());
            buf.append("'.");
            logValidationError(buf.toString());
            return false;
        }
        return true;
    }

    /**
     * Validate service tag data based on the serial # groupings.  A common
     * product serial number indicates the cells that are located within
     * the same top level assembly component # (rack).   Process all the list
     * groupings within the HashMap to make sure there all valid
     * @param serialNumMap Hashmap of data to check. 
     * @return boolean, true if all valid, false if validation check fails
     */
    private boolean passesSerialNumGroupCheck(
            HashMap<String, List> serialNumMap) {
        
        Set keySet = serialNumMap.keySet();
        String keys[] = (String[])keySet.toArray(new String[keySet.size()]);
        for (int i=0; i < keys.length; i++) {
            List<ServiceTagCellData> grouping = 
                    (List<ServiceTagCellData>)serialNumMap.get(keys[i]);
            boolean isValid = validateSerialNumberGrouping(grouping);
            if (isValid == false)
                return false;
        }
        return true;
    }
      
    /**
     * Create the unique list of service tag data that we use to generate
     * the service tag registry entries from.  We pull 1 entry from the
     * service tag serial number grouping map.  At this point in time
     * the <code>serialNumMap</code> has been validated to ensure all
     * the entries in a grouping match.
     * @param serialNumMap The serial number grouping hashMap
     * @return ServiceTag[] the servic tag entries for the hive
     */
    private ServiceTag[] createServiceTagEntries(
            HashMap<String, List>serialNumMap) {
        List<ServiceTag> list = new ArrayList<ServiceTag>();
        Set keySet = serialNumMap.keySet();
        String keys[] = (String[])keySet.toArray(new String[keySet.size()]);
        String productVersion = SoftwareVersion.getConfigVersion();
        if (productVersion == null)
            productVersion = "Unknown";
        String softwareName = getMessage(
                AdminResourcesConstants.KEY_SERVICE_TAG_PRODUCT_SOFTWARE_NAME, 
                new Object[] { productVersion }, null);
        String defaultProductName = getMessage(
                AdminResourcesConstants.KEY_SERVICE_TAG_PRODUCT_NAME, 
                    new Object[0], null);
        for (int i=0; i < keys.length; i++) {
            List<ServiceTagCellData> grouping = 
                    (List<ServiceTagCellData>)serialNumMap.get(keys[i]);
            
            // At this point in time we know all the elements within
            // serial number grouping all match.  Since there
            // all the same we just grab the 1st one and generate
            // a tag for that element.
            ServiceTagCellData entry = (ServiceTagCellData)grouping.get(0);
            ServiceTagData data = entry.getServiceTagData();
            
            
            Object[] params = new Object[] {
                data.getProductSerialNumber(),
                data.getMarketingNumber()
            };
            String productInstanceID = getMessage(
                    AdminResourcesConstants.KEY_SERVICE_TAG_INSTANCE_ID, 
                    params, null);
            
            String lookupKey = new StringBuffer()
                    .append(AdminResourcesConstants.PREFIX_KEY_SERVICE_TAG_PART_NUM_TO_PRODUCT_NAME)
                    .append(data.getProductNumber()).toString();
            String productName = getMessage(lookupKey, new Object[0], defaultProductName);
            ServiceTag tag = ServiceTag.newInstance(
                    data.getInstanceURN(), 
                    productName, 
                    productVersion, 
                    new StringBuffer("urn:st:").append(data.getProductNumber())
                        .toString(), 
                    NONE, 
                    NONE, 
                    productInstanceID, 
                    VENDOR, 
                    PLATFORM_ARCH, 
                    NONE, 
                    softwareName);
            list.add(tag);
        }
        return (ServiceTag[])list.toArray(new ServiceTag[list.size()]);
    }
    
      
    /**
     * Process the service tag data for all the cells.  Verify that all
     * the stored data is valid.  If the data is not valid the isValid() method 
     * will return false.  Any validation failures will be logged.
     * <P>
     * If isValid() is true then getServiceTag() can be called to retrieve
     * the service tag entries that describe this hive.
     */
    private void process(ServiceTagCellData[] entries) {
        
        // #1 - Verify that there are no null fields in any of the
        // service tag info fields.  If there are null fields
        // then no action is possible
        isValid = passesNullFieldCheck(entries);
        if (isValid == false)
            return;
        
        isValid = passesInstanceURNCheck(entries);
        if (isValid == false) {
            // If this is a multi-cell hive we are done
            // The operator must run servicetags --refresh to 
            // cause the instanceURN to get generated.  This is
            // due to the fact there there is no mechansim that
            // can be used to update the silo_info.xml file on all
            // the cells.  The file must be in sync on all cells 
            //
            // If however we are running in a single cell
            // then we can create the instanceURN and update
            try {
                if (entries.length != 1)
                    return;
                    
                MultiCellLib multiCell = MultiCellLib.getInstance();
                if (multiCell.isCellStandalone() == false)
                    return;
                
                ServiceTagCellData cellData = entries[0];
                ServiceTagData data = cellData.getServiceTagData();
                data.setInstanceURN(ServiceTag.generateInstanceURN());
                multiCell.updateServiceTagData(
                    cellData.getCellId(), data);
            } 
            catch (MultiCellLibError mcle) { 
                logger.log(Level.WARNING, mcle.getMessage(), mcle);
                return;
            }
        }
        HashMap<String,List> serialNumGroupings = 
                createSerialNumGroupings(entries);
        isValid = passesSerialNumGroupCheck(serialNumGroupings);
        if (isValid) {
            serviceTags = createServiceTagEntries(serialNumGroupings);
        }
    }
    
    
    /**
     * Check to see whether the service tag data associated with this object
     * is valid.
     * @return boolean, true if valid, false otherwise
     */
    public boolean isValid() {
        return isValid;
    }
   
    /**
     * Get the service tags that represent that system derived from the
     * <code>entries</code> passed in to the contructor of this object
     * @return ServiceTag[] the service tag entries for the hive
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public ServiceTag[] getServiceTags()
    throws MgmtException {
        if (isValid() == false)
            throw new MgmtException("Unable to fetch service tags, data is not valid.");
        return serviceTags;
    }
    
    private void logValidationError(String msg) {
        logger.log(Level.WARNING, 
                new StringBuffer(SERVICE_TAGS_ERROR_KEY)
                .append("Validation check failed. ")
                .append(msg).toString());
    }
    
    
    /**
     * Fetch a value from the english version of the resource bundle
     * <P>
     * This code should really into move in BundleAccess so there's
     * a common routine but unfortunetly the path used for lookup
     * is based on NodeMgr and won't work in the emulator.  Can't fix
     * everything is this checkin so leave this here.
     * 
     * @param msgKey the key
     * @param params the parameters if any for the pattern retrieved via the
     * msgKey.  Value may be null if no parameters are required.
     * @param fallback the fallback message to use if the lookup of the msgKey
     * fails.
     * @return String the resulting message.  If a failure occurs the
     * msgKey and the passed in parameters values will be returned.
     */
    public static String getMessage(String msgKey, Object[] params, String fallback) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(
                    AdminResourcesConstants.RESOURCE_BUNDLE_NAME, 
                    Locale.ENGLISH);
            String pattern = bundle.getString(msgKey);
            if (params == null || params.length == 0)
                return pattern;
            return MessageFormat.format(pattern, params);
        } 
        catch (Exception ex) {
            if (fallback != null || fallback.length() > 0)
                return fallback;
            StringBuffer buf = new StringBuffer(msgKey);
            buf = new StringBuffer(msgKey);
            if (params != null) {
                for (int i=0; i < params.length; i++)
                    if (params[i] != null)
                        buf.append(" ").append(params[i]);
            }
            return buf.toString();
        }
    }
}
