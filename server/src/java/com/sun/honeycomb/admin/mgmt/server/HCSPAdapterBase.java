/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.honeycomb.admin.mgmt.server;

import com.sun.honeycomb.util.ServiceProcessor;

/**
 * The service processor base adapter class is responsible for loading 
 * the service processor fru instance.  This is a shared class and is
 * also used by the emulator
 */
public class HCSPAdapterBase 
extends HCSP
implements HCSPAdapterInterface
{

    public void loadHCFru()
        throws InstantiationException {
        // Do nothing.  Load is handled via loadHCSP
    }
    
    /**
     * Load the service processor fru.
     * @throws java.lang.InstantiationException if an invalid fru id
     * is specified.
     */
    public void loadHCSP() 
        throws InstantiationException {
        
        HCSP fru = ServiceProcessor.getSPFru();
        setFruId(fru.getFruId());
        setFruName(fru.getFruName());
        setFruType(fru.getFruType());
        setStatus(fru.getStatus());
    }
}
