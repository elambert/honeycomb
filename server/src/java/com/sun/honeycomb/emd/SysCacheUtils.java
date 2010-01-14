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

import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.oa.OAClient;

/*
 * Utility class to be used by anyone who wants to insert,remove,update records
 * in the system cache. Created initially to avoid having one piece of code to 
 * handle these operations in the Coordinator and another copy and paste of that
 * code in the Data Doctor tasks.
 *  
 */
public class SysCacheUtils {

    public static MetadataClient _mdClient = MetadataClient.getInstance();
    public static LayoutClient _layoutClient = LayoutClient.getInstance();

    // Logger
    protected static final Logger LOG = 
        Logger.getLogger(SysCacheUtils.class.getName());
 
    /* 
     * Insert a single metadta record into the appropriate system caches.
     */
    public static void insertRecord(SystemMetadata sm){
        SystemMetadata[] sms = {sm};
        insertRecord(sms); 
    }
    
    /*
     * Inserts all system metadata records that are suppose to be inserted into 
     * all of the correct system caches. The "suppose to be" comes from the fact
     * that multi chunk objects should only have one system cache record not one
     * per chunk
     */
    public static void insertRecord(SystemMetadata[] sms){
        for (int s = 0; s < sms.length; s++) {
            /*
             * Only store metadata for the last chunk of multichunk object and 
             * fix it up by making it have the layout of the first chunk (the
             * actual object OID) and the right chunk number 0, then we're set
             * and we can insert it into the syscache
             */
            if (sms[s].getSize() != OAClient.MORE_CHUNKS) {
                SystemMetadata sm = new SystemMetadata(sms[s]);
                
                if (sm.getOID().getChunkNumber() != 0) {
	                NewObjectIdentifier oid = sms[s].getOID();
	
	                int layout = oid.getLayoutMapId();
	                int i = 0;
	                while (i++ < oid.getChunkNumber())
	                    layout = _layoutClient.getPreviousLayoutMapId(layout);
	
	                oid.setLayoutMapId(layout);
	                oid.setChunkNumber(0);
	                
	                sm.setOID(oid);
	                sm.setLayoutMapId(layout);
                }
                
                _mdClient.setMetadata(CacheClientInterface.SYSTEM_CACHE, sm.getOID(), sm);
            }
        }
    }
    
    /*
     * Remove all system metadta records from all of the system caches for the
     * specified oid.
     */
    public static void  removeRecord(NewObjectIdentifier oid) 
                  throws EMDException {
        _mdClient.removeMetadata(oid, CacheClientInterface.SYSTEM_CACHE);
    }
    
    /*
     * Remove the system metadta record  on the specified disk.
     */
    public static void removeRecord(NewObjectIdentifier oid, 
                                    Disk disk) 
                  throws EMDException {
       _mdClient.removeMetadata(oid,CacheClientInterface.SYSTEM_CACHE,disk);
    }
    
    public static void updateRecord(NewObjectIdentifier oid,
                                    SystemMetadata sm) {
        
        _mdClient.setMetadata(CacheClientInterface.SYSTEM_CACHE,
                              oid,
                              sm); 
        
    }
    
    public static SystemMetadata retrieveRecord(NewObjectIdentifier oid) throws EMDException {
        return _mdClient.retrieveMetadata(CacheClientInterface.SYSTEM_CACHE, oid); 
    }

    public static void addLegalHold(NewObjectIdentifier oid,
                                    String legalHold) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Adding (" + oid + ", " + legalHold + ")");
        }
        _mdClient.addLegalHold(oid, legalHold); 
    }

    public static void removeLegalHold(NewObjectIdentifier oid,
                                       String legalHold) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Removing (" + oid + ", " + legalHold + ")");
        }
        _mdClient.removeLegalHold(oid, legalHold);
    }
}
