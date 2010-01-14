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
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import com.sun.honeycomb.admin.mgmt.client.HCServiceTagCellData;

import com.sun.honeycomb.admin.mgmt.client.HCServiceTags;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Helper class
 */
public class ServiceTagsUtils {


    /**
     * Group all the serivce tag data based on the serial numbers
     * @param entries The service tag entries to group
     * @return HashMap the service tag groups.  Key is serial number,
     * Object is List<HCServiceTagCellData> where all objects have
     * the same serial numbers.
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public static HashMap<String,List> createSerialNumGroupings(
            HCServiceTagCellData[] entries) {
        // Create groupings based on the serial number.
        HashMap serialNumMap = new HashMap();
        for (int i=0; i < entries.length; i++) {
            String key = entries[i].getProductSerialNumber();
            if (serialNumMap.containsKey(key)) {
                List<HCServiceTagCellData> same = 
                    (List<HCServiceTagCellData>)serialNumMap.get(key);
                same.add(entries[i]);
            } else {
                // We've never seen this service tag entry.  Add it to our map
                List<HCServiceTagCellData> same = 
                        new ArrayList<HCServiceTagCellData>();
                same.add(entries[i]);
                serialNumMap.put(key, same);
            }
        }
        return serialNumMap;
    }
    
    /**
     * Validate the all instanceURNs in the list are not empty and if
     * the instanceURN is set that all the entries in the list refer
     * to the same instanceURN 
     * @param list a grouping of service tag entries that share the
     * same product serial number
     * @return boolean if all instanceURNs in the list are equal, false
     * otherwise
     */
    private static boolean validateInstanceURNs(List<HCServiceTagCellData> list) {
        HCServiceTagCellData[] entries = 
            (HCServiceTagCellData[])list.toArray(new HCServiceTagCellData[list.size()]);
        
        // Each list has a minimum of at least 1 entry
        String baseInstanceURN = entries[0].getInstanceURN();
        
        if (baseInstanceURN == null || baseInstanceURN.length() == 0)
            return false;
        for (int i=1; i < entries.length; i++) {
            String instanceURN = entries[i].getInstanceURN();
            if (baseInstanceURN.equals(instanceURN) == false)
                return false;
        }
        return true;
    }


    /**
     * This routine determins whether all instanceURNs set for the
     * service tag records are correct.  To be correct all entries
     * must have a value and all entries that share the same product serial
     * number refer must refer to the same instanceURN.
     * @param serialNumMap a HashMap created via createSerialNumGroupings()
     * @return boolean, true if all entries are valid, false otherwise.
     * If false the instanceURNs need to be regenerated and the service
     * tag registry will need to repopulated or deleted if there is
     * another validation type issues.
     */
    public static boolean validateInstanceURNs(HashMap serialNumMap) { 
        Set keySet = serialNumMap.keySet();
        String keys[] = (String[])keySet.toArray(new String[keySet.size()]);
        for (int i=0; i < keys.length; i++) {
            List<HCServiceTagCellData> grouping = 
                    (List<HCServiceTagCellData>)serialNumMap.get(keys[i]);
            if (validateInstanceURNs(grouping) == false)
                return false;
        }
        return true;
    }
    
    /**
     * Fetch the service tag object from the server
     * @param api handle to the AdminClient api
     * @return HCServiceTags the service tag object
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.PermissionException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     */
    public static HCServiceTags getServiceTags(AdminClient api)
    throws MgmtException, PermissionException, ConnectException {
        byte masterCellId = api.getMasterCellId();
        HCServiceTags tags =
                Fetcher.fetchHCServiceTags(
                    SiloInfo.getInstance().getServerUrl(masterCellId));
        return tags;
    }
    
    /**
     * Helper method to convert list to array
     * @param list the list to convert to an array
     * @return HCServiceTagCellData[] array of HCServiceTagCellData elements
     */
    public static HCServiceTagCellData[] toArray(List<HCServiceTagCellData> list) {
        HCServiceTagCellData[] entries = 
            (HCServiceTagCellData[])list.toArray(
                new HCServiceTagCellData[list.size()]);
        return entries;
    }
}