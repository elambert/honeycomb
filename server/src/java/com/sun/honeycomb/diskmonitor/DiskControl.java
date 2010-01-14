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



package com.sun.honeycomb.diskmonitor;

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.DiskLabel;
import com.sun.honeycomb.disks.DiskHealth;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;

import java.io.IOException;

public interface DiskControl
    extends ManagedService.RemoteInvocation, ManagedService {

    // Remote API for DiskMonitor

    DiskHealth getHealth(DiskId disk) throws IOException,
        ManagedServiceException;
    
    void disable(DiskId disk) throws IOException, ManagedServiceException;
    void dismount(DiskId disk) throws IOException, ManagedServiceException;
    boolean enable(DiskId disk) throws IOException, ManagedServiceException;
    
    void wipe(DiskId disk) throws IOException, ManagedServiceException;
    void fullWipe(DiskId disk) throws IOException, ManagedServiceException;
    void wipeAll() throws IOException, ManagedServiceException;

    boolean onlineDisk(DiskId diskId) 
        throws IOException,	ManagedServiceException;
    void offlineDisk(DiskId diskId) 
        throws IOException, ManagedServiceException;

    // Report an error reading or writing a disk. Return value
    // indicates whether or not the disk was disabled as a result.
    boolean reportError(DiskId diskId) throws ManagedServiceException;

    // debug
    void pullDisk(DiskId diskId) throws IOException, ManagedServiceException;
    void pushDisk(DiskId diskId) throws IOException, ManagedServiceException;


    // Evacuation will not be implemented in 1.0, so these will just
    // throw an "unimplemented" exception
    void evacuate(DiskId diskId) throws IOException, ManagedServiceException;
    void stopEvacuate(DiskId diskId) 
        throws IOException, ManagedServiceException;

    // When a disk is added to the running system, this method is used
    // to tell the DiskMonitor about it
    public void addDisk(DiskId disk, String device) 
        throws ManagedServiceException;

    // Interface to get the disk label for a disk. For
    // Disk service testing only.
    public DiskLabel getDiskLabel(DiskId disk)
        throws ManagedServiceException;

    // Interface to set the disk label for a disk. For
    // Disk service testing only.
    public int setDiskLabel(DiskId disk, String labelString)
        throws ManagedServiceException;

    /**
     * Determine if the disk is properly configured so I/O can be
     * done on it.
     */
    public boolean isDiskConfigured(DiskId diskId)
        throws ManagedServiceException;

}
