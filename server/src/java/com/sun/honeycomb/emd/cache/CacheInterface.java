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



package com.sun.honeycomb.emd.cache;

import java.util.ArrayList;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.remote.MDOutputStream;

/**
 * This interface has to be implemented by classes that implement a logic to
 * execute metadata operations.
 *
 * Some processing examples may include :
 *  - a relational model ;
 *  - a full text index model (e.g. lucene) ;
 *  - any third party pluggable module.
 */

// For now the CacheInterface extends the CacheClientInterface.
// At some point, these 2 interfaces will be separated into 2 different
// objects / jar packages.

public interface CacheInterface 
    extends CacheClientInterface {

    /*
     * Methods to start / stop the processing unit
     */

    void start() throws EMDException;
    void stop() throws EMDException;

    /*
     * Method to register a new disk in the cache
     *
     * This call may be performed with a disk that has already been
     * registered. Its implementation has to be idempotent.
     */
    
    void registerDisk(String MDPath,
                      Disk disk) throws EMDException;
    
    /*
     * Method to unregister a disk (e.g. the disk has been detected bad).
     */
    
    void unregisterDisk(Disk disk) throws EMDException;

    /*
     * Method to check if a given fisk is already in that cache
     */

    boolean isRegistered(Disk disk);
   
    /*
     * Wipe the cache on the specified disk;
     */
    public void wipe(Disk disk) throws EMDException;
    
    public void doPeriodicWork(Disk disk) throws EMDException;

    /*
     * set/delete metadata APIs
     */
    
    void setMetadata(NewObjectIdentifier oid,
                     Object argument,
                     Disk disk)
        throws EMDException;

    void removeMetadata(NewObjectIdentifier oid,
                        Disk disk)
        throws EMDException;
    
    /*
     * Query API
     */

    void queryPlus(MDOutputStream output,
                   ArrayList disks,
                   String query,
                   ArrayList attributes,
                   EMDCookie cookie,
                   int maxResults, int timeout,
                   boolean forceResults,
                   Object[] boundParameters)
        throws EMDException ;
    
    /*
     * Select Unique API
     */
    
    void selectUnique(MDOutputStream output,
                      String query,
                      String attribute,
                      String cookie,
                      int maxResults, 
                      int timeout,
                      boolean forceResults,
                      Object[] boundParameters)
        throws EMDException ;

    /*
     * Compliance API
     */
    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold,
                             Disk disk)
	throws EMDException;

    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold,
                                Disk disk)
	throws EMDException;
    
    void sync(Disk disk) throws EMDException;
}
