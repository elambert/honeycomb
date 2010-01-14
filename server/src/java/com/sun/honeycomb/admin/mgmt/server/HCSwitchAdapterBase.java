/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.honeycomb.admin.mgmt.server;

import com.sun.honeycomb.common.FruNamePatterns;
import com.sun.honeycomb.util.Switch;
import java.util.regex.Matcher;

/**
 * The switch adapter class is responsible for loading and retrieving a single
 * switch fru instance.  It is called via Fetch.fetchSwitch()
 */
public class HCSwitchAdapterBase 
extends HCSwitch
implements HCSwitchAdapterInterface
{

    public void loadHCFru()
        throws InstantiationException {
        // Do nothing.  Load is handled via loadHCSwitch
    }
    
    /**
     * Load the fru object associated with this object
     * @param fruName the name for the fru object we want to load,
     * SWITCH-1 or SWITCH-2
     * @throws java.lang.InstantiationException if an invalid fru id
     * is specified.
     */
    public void loadHCSwitch(String fruName) 
        throws InstantiationException {
        
        Matcher switchMatcher = FruNamePatterns.SWITCH_FRU_NAME.matcher(fruName);
	if (switchMatcher.matches() == false) { 
            throw new InstantiationException("Invalid switch name, '"
                    + fruName + "' specified. "
                    + "Must be in the form SWITCH-x");
	}
        
	// Since the pattern matched we know the reference to the specified
        // switch is valid.   Create the switch object
	int switchId = Integer.parseInt(switchMatcher.group(1));      
        
        try {
            HCSwitch fru = Switch.getSwitchFru(switchId);
            setFruId(fru.getFruId());
            setFruName(fru.getFruName());
            setFruType(fru.getFruType());
            setStatus(fru.getStatus());
            setSwitchName(fru.getSwitchName());
            setVersion(fru.getVersion());
        }
        catch (NumberFormatException nfe) {
            throw new InstantiationException(nfe.getMessage());
        }
        
    }
    
     /**
     * @return HCSwitch[] array of HCSwitch object that describes 
     * the switches for the ST5800 system
     */
    static HCSwitch[] getSwitchFrus() {
        int[] ids = Switch.getIds();
        HCSwitch[] fru = new HCSwitch[ids.length];
        for (int i=0; i < ids.length; i++) {
            fru[i] = Switch.getSwitchFru(ids[i]);
        }
        return fru;
    }
}
