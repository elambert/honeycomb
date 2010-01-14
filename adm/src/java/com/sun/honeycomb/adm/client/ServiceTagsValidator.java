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


import com.sun.honeycomb.admin.mgmt.client.HCServiceTagCellData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This class is responsible for performing basic validation of
 * the service tag data after there has been a change in the cluster
 * due to a new cell being added or remove or a software upgrade.
 * This is basically a simplier version of the code contained
 * within CommandServiceTags.   Only a basic validation error
 * is outputed by this code where CommandServiceTags, 'servicetags --validate'
 * generate a detail description of the problem encountered.
 * If isValid() return false the validation error encountered can be
 * retrieved and outputed by the caller via getValidationError()
 */
public class ServiceTagsValidator {

    private static final Logger logger = 
            Logger.getLogger(ServiceTagsValidator.class.getName());
    private boolean isValid = false;
    private String message = null;
    private HashMap<String,List>serialNumGroupings = null;
    
    /**
     * Process and validate the service tag data entries for the hive.
     * If all the entries are valid <code>isValid()</code> will return true.
     * If true then <code>getServiceTags()</code> can be used to retrieve
     * the service tag data that can be registred with 
     * service tag infrastructure.
     * @param entries the service tag data entries for all the cells in
     * the hive.
     */
    public ServiceTagsValidator(HCServiceTagCellData[] entries) {
        process(entries);
    }
    
    /**
     * Check the service tag data associated with each cell to
     * make sure it's not null.  
     * @param entries the entries to check for null fields
     * @return boolean true if passes check, false otherwise
     */
    private boolean passesNullFieldCheck(HCServiceTagCellData[] entries) {
        boolean passes = true;
        for (int i=0; i < entries.length; i++) {
            HCServiceTagCellData data = entries[i];
            if (isEmpty(data.getMarketingNumber()) 
                    || isEmpty(data.getProductNumber())
                    || isEmpty(data.getProductSerialNumber())) {
                StringBuffer buf = new StringBuffer();
                buf.append("Service tag information is missing for cell "); 
                buf.append(entries[i].getCellId());
                buf.append(".");
                setValidationErrorMsg(buf.toString());
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
     * in the list need to match.    
     * 
     * @param list the serial number list to validate.  All entries in the list
     * already share the same serial #s
     * @return boolean true if validation check passes, false otherwise
     */
    private boolean validateSerialNumberGrouping(List<HCServiceTagCellData> list) {
       
        if (list.size() == 1) {
            // The list passed in is based on serial numbers.  All the
            // items in the list will have the same serial number.
            // If there is only 1 item in the list then no validation
            // is required.
            return true;
        }
        boolean productNumberMismatch = false;
        boolean marketingNumberMismatch = false;
        HCServiceTagCellData[] entries = 
            (HCServiceTagCellData[])list.toArray(
                new HCServiceTagCellData[list.size()]);
        
        // Each list has a minimum of at least 1 entry
        HCServiceTagCellData baseEntry = entries[0];
        for (int i=1; i < entries.length; i++) {
            // A grouping is considered valid if the product number and
            // marketing numbers of all fields is the same.
            HCServiceTagCellData data = entries[i];
            if (baseEntry.getProductNumber().equals(
                    data.getProductNumber()) == false) {
                productNumberMismatch = true;
            }
            if (baseEntry.getMarketingNumber().equals(
                    data.getMarketingNumber()) == false) {
                marketingNumberMismatch = true;
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
            buf.append(buf.toString());
            setValidationErrorMsg(buf.toString());
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
            setValidationErrorMsg(buf.toString());
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
            List<HCServiceTagCellData> grouping = 
                    (List<HCServiceTagCellData>)serialNumMap.get(keys[i]);
            boolean isValid = validateSerialNumberGrouping(grouping);
            if (isValid == false)
                return false;
        }
        return true;
    }
      
    /**
     * Process the service tag data for all the cells.  Verify that all
     * the stored data is valid.  If the data is not valid the isValid() method 
     * will return false. 
     */
    private void process(HCServiceTagCellData[] entries) {
        
        // #1 - Verify that there are no null fields in any of the
        // service tag info fields.  If there are null fields
        // then no action is possible
        isValid = passesNullFieldCheck(entries);
        if (isValid == false)
            return;
        serialNumGroupings = 
                ServiceTagsUtils.createSerialNumGroupings(entries);
        isValid = passesSerialNumGroupCheck(serialNumGroupings);
    }
    
    /**
     * Get the HCServiceTagCellData grouped by serial numbers
     * @return HashMap map of serial number groupings.  May be null if
     * null field check failed.  Should only be used if isValid()
     * returns true.
     * <P>
     * Map Key is the shared serial number.  List is of type
     * HCServiceTagCellData
     */
    public HashMap<String,List> getSerialNumGroupings() {
        return serialNumGroupings;
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
     * If isValid() return false then this method will return
     * the validation error that caused the validation check to fail.
     * @return String the validation error message if any
     */
    public String getValidationErorMsg() {
        return message;
    }
    
    /**
     * Set the validation error message that caused the isValid() check to
     * fail.
     * @param message the validation error message
     */
    protected void setValidationErrorMsg(String message) {
        this.message = message;
    }
}
