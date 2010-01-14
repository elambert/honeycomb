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



package com.sun.honeycomb.emd;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.server.MDService;
import com.sun.honeycomb.layout.LayoutConfig;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertException;

/**
 * Public definition of the Meta Data service.
 */
public interface MDManagedService extends ManagedService.RemoteInvocation, ManagedService {

    /**
     * Restart the specified metadata cache in order for it to pickup changes
     * on the filesystem that have been made by the bulk oa api on restore.
     * 
     * @param cacheID
     * @param disk
     * @throws ManagedServiceException
     * @throws EMDException
     */
    public void restart(String cacheID, Disk disk) 
           throws ManagedServiceException, EMDException;

    /**
     * Sync a given cache to disk, mainly used by back in order to guarantee
     * all data is on disk before proceeding to do a live backup.
     *  
     * @param cacheID
     * @param disk
     * @throws ManagedServiceException
     * @throws EMDException
     */
    public void sync(String cacheID, Disk disk) 
           throws ManagedServiceException, EMDException;
    
    public void wipeCache(String cacheID, Disk disk) 
           throws ManagedServiceException, EMDException;
    
   
    /**
     * 
     * @return
     * @throws ManagedServiceException
     */
    public boolean isSystemCacheInInit() throws ManagedServiceException;
    
   
    /*
     * MD Proxy object
     */
    public class Proxy extends ManagedService.ProxyObject {
        boolean running;
        double mdLoad;
       
        boolean cache_running[];
        boolean cache_corrupted[];
        boolean cache_complete[];
        
        public Proxy(double newLoad, boolean isRunning) {
            mdLoad = newLoad;
            running = isRunning;
          
            cache_running = new boolean[LayoutConfig.DISKS_PER_NODE];
            cache_corrupted = new boolean[LayoutConfig.DISKS_PER_NODE];
            cache_complete = new boolean[LayoutConfig.DISKS_PER_NODE];
            
            for(int d = 0 ; d < LayoutConfig.DISKS_PER_NODE; d++) {
                cache_running[d] = false;
                cache_corrupted[d] = false;
                cache_complete[d] = true;
            }
        }
 
        // override from ProxyObject
        public boolean isEnabled() {
            return running;
        }

        public double getMDLoad() {
            return(mdLoad);
        }
        
        public void setMDLoad(double newLoad) { 
            mdLoad = newLoad;
        }
        
        public void setRunning(boolean isRunning) { 
            running = isRunning;
        }

        private Proxy() {
        }

        /*
         * Alert API
         */
        public int getNbChildren() {
            return 1;
        }

        public AlertProperty getPropertyChild(int index) 
            throws AlertException {
            AlertProperty prop = null;
        
            switch(index) {
            case 0:
                prop = new AlertProperty("mdLoad", AlertType.DOUBLE);
                break;
            default:
                throw new AlertException("index " + index + " out of bound");
            }
            return prop;
        }


        public double getPropertyValueDouble(String property)  
            throws AlertException {
            if (property.equals("mdLoad")) {
                return mdLoad;
            } else {
                throw new AlertException("property " + property +
                                         " does not exist");                
            }
        }
        
        /**
         * Return the MDService Proxy
         */
        public static MDManagedService.Proxy getProxy(int nodeId) {
            ManagedService.ProxyObject proxy;
            proxy = ServiceManager.proxyFor(nodeId, MDService.class);
            if (!(proxy instanceof MDManagedService.Proxy)) {
                return null;
            }
            return ((MDManagedService.Proxy) proxy);
        }
       
        /**
         * 
         * @param nodeId
         * @return
         */
        public static MDManagedService getServiceAPI(int nodeId) {
            ManagedService.ProxyObject proxy;
            proxy = ServiceManager.proxyFor(nodeId, MDService.class);
            if (!(proxy instanceof MDManagedService.Proxy)) {
                return null;
            }
            
            ManagedService.RemoteInvocation api = proxy.getAPI();
            if (!(api instanceof MDManagedService)) {
                return null;
            }
            return (MDManagedService) api;
        } 
        
        public void setCacheState(Disk disk, boolean isRunning) {
            if (!diskIsValid(disk)) {
                throw new RuntimeException("setCacheState - "
                                           + "disk not valid " + disk
                                           );
            }
            cache_running[disk.diskIndex()] = isRunning;
        }
       
        public void setCacheCorrupted(Disk disk, boolean isCorrupted) {
            if (!diskIsValid(disk)) {
                throw new RuntimeException("setCacheCorrupted - "
                                           + "disk not valid " + disk
                                           );
            }
            cache_corrupted[disk.diskIndex()] = isCorrupted;
        }
        
        public void setCacheComplete(Disk disk, boolean isComplete) {
            if (!diskIsValid(disk)) {
                throw new RuntimeException("setCacheComplete - "
                                           + "disk not valid " + disk
                                           );
            }            
            cache_complete[disk.diskIndex()] = isComplete;
        }
        
        public boolean isCacheRunning(Disk disk) {
            return diskIsValid(disk) && cache_running[disk.diskIndex()];
        }
        
        public boolean isCacheCorrupted(Disk disk) { 
            return diskIsValid(disk) && cache_corrupted[disk.diskIndex()];
        }
        
        public boolean isCacheComplete(Disk disk) { 
            return diskIsValid(disk) && cache_complete[disk.diskIndex()];
        }
        
        private boolean diskIsValid(Disk disk) {
            if (disk == null) {
                return false;
            }
            int idx = disk.diskIndex();
            if (idx < 0 || idx > LayoutConfig.DISKS_PER_NODE) {
                return false;
            }
            return true;
        }
    }
}
