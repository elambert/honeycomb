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



package com.sun.honeycomb.layout;

import java.util.logging.Logger;
import java.util.ArrayList;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.ConfigPropertyNames;

/** 
 * Contains the disk mask for a node.  
 * Published by the LayoutService.  No remote API (RMI) for now.
 */
public class LayoutProxy extends ManagedService.ProxyObject {

    /* PULIC METHODS */

    /** Current disk mask from local LayoutProxy, or null if failed. */
    static public DiskMask getCurrentDiskMask() {

        LayoutProxy proxy = getLayoutProxy();
        if (proxy == null) {
            return null;
        }
        return proxy.mask;
    }

    /**
     * Current block stores flag from local LayoutProxy.  If true, we
     * are disallowing STORES because we've reached our capacity limit.
     */
    static public boolean getBlockStores() {


        LayoutProxy proxy = getLayoutProxy();
        if (proxy == null) {
            return false;
        }
        return proxy.blockStores;
    }

    /**
     * For healing-based expansion, we need a way to remember the disk mask
     * at the time of expansion. We store it in config, and use this method
     * to return the DiskMask
     */

    /** Get current Layout proxy for given node, null if failed. */
    static public LayoutProxy getLayoutProxy(int nodeId) {

        ManagedService.ProxyObject proxy = null;
        proxy = ServiceManager.proxyFor(nodeId, LayoutService.class);

        if (! (proxy instanceof LayoutProxy)) {
            LOG.severe("failed to get layout proxy - wrong type");
            return null;
        }
        return (LayoutProxy) proxy;
    }

    /** Get current Layout proxy for local node, null if failed. */
    static public LayoutProxy getLayoutProxy() {
        return getLayoutProxy(ServiceManager.LOCAL_NODE);
    }

    /** 
     * Get pre-sloshing mask. Only use during cluster expansion
     * by RecoverLostFrag to find objects in old layout.
    */
    static DiskMask getPreSloshDiskMask () {
        
        String enabledMask = ClusterProperties.getInstance().
            getProperty(ConfigPropertyNames.PROP_EXP_ENABLED_MASK);
        String availMask   = ClusterProperties.getInstance().
            getProperty(ConfigPropertyNames.PROP_EXP_AVAILABLE_MASK);

        if (enabledMask == null) {
            return null;
        }

        DiskMask preSloshMask = new DiskMask();
        preSloshMask.setEnabledMask(enabledMask);
        preSloshMask.setAvailableMask(availMask);

        return preSloshMask;
    }

    public String toString() {
        return "Current Disk Mask - " + mask.toString();
    }

    public DiskMask currentDiskMask() { return mask; }

    /* PACKAGE METHODS */

    /** Constructor, create "empty" mask. */
    LayoutProxy() {
        mask = new DiskMask();
    }

    /** Make given mask the current disk mask */
    void currentDiskMask(DiskMask newMask) {
        mask = newMask;
    }

    /** accesors for Layout Service */
    boolean blockStores() { return blockStores; }
    void blockStores(boolean b) { blockStores=b; };

    /*
     * Alerts that this component will be generating.  The
     * alerts will have the following form when populated
     * in the alert tree: root.nodeXXX.Layout.{alertName}
     * <P>
     * Defined alerts for Layout are:
     * <ul>
     * <li>diskMap</li>
     * <li>blockStores: indicates whether store operations to the system are 
     * blocked or not.  true indicates blocked, false indicates stores
     * are allowed.</li>
     * </ul>
     */
    private static final AlertProperty[] alertProps = {
	new AlertProperty("diskmap", AlertType.COMPOSITE),
	new AlertProperty("blockStores", AlertType.BOOLEAN),
    };

    /**
     * @see AlertComponent#getNbChildren()
     */
    public int getNbChildren() {
	return alertProps.length;
    }

    /**
     * @see AlertComponent#getPropertyChild(int)
     */
    public AlertProperty getPropertyChild(int index)
	    throws AlertException {

	try {
	    return alertProps[index]; 
	}
	catch (ArrayIndexOutOfBoundsException e) {
	    throw new AlertException("index " + index + "does not exist");
	}
    }
    
    /**
     * @see AlertComponent#getPropertyValueBoolean(String)
     */
    public boolean getPropertyValueBoolean(String property) 
    throws AlertException {
	if (property.equals("blockStores")) { 
            return getBlockStores();
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
    }
    
    /**
     * @see AlertComponent#getPropertyValueComponent(String)
     */
    public AlertComponent getPropertyValueComponent(String property)
            throws AlertException {
        if (property.equals("diskmap")) {
            return (AlertComponent) mask;
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
    }

    /* PRIVATE ATTRIBUTES */

    private DiskMask mask;

    private boolean blockStores = false;

    private transient static final Logger LOG =
        Logger.getLogger(LayoutProxy.class.getName());

}


