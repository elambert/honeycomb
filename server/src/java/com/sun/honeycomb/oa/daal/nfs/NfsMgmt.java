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



package com.sun.honeycomb.oa.daal.nfs;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.oa.daal.DAALMgmt;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.platform.PlatformService;
import com.sun.honeycomb.cm.ManagedServiceException;

/**
 * DAAL Management Interface over NFS
 * Uses the Platform api to mount/unmount disk over nfs.
 */
public class NfsMgmt implements DAALMgmt {

    private static final Logger LOG = Logger.getLogger(NfsMgmt.class.getName());
        
    /*
     * mount the given disk over NFS
     */
    public boolean openDisk(DiskId id) throws ManagedServiceException
    {
        return getPlatform().nfsOpen(id);
    }        
    
    /*
     * NFS unmount the given disk
     */
    public void closeDisk(DiskId id) throws ManagedServiceException
    {
        getPlatform().nfsClose(id);
    }

    /*
     * close all nfs mounted filesystems.
     */
    public boolean closeAllDisks() throws ManagedServiceException
    {
        return getPlatform().nfsCloseAll();
    }
    
    /*
     * get the mgmt proxy
     */
    public Object getProxy() {
        return null;
    }
    
    private PlatformService getPlatform() {
        PlatformService platform = PlatformService.Proxy.getApi();
        if (platform == null) {
            String error = "ERROR - cannot access local Platorm proxy/api";
            LOG.severe(error);
            throw new RuntimeException(error);
        }
        return platform;
    }
}
