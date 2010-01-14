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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;

public class FooterExtensionFileSet {
 
    public static short EMPTY_SLOT = 0;
    public static short LEGAL_HOLD = 1;

    int minFiles;
    int numFiles;
    int numNodes;

    FooterExtension fe;
    FooterExtensionFile fefSet[];

    NewObjectIdentifier oid;
    Layout layout;
    ObjectReliability reliability;
    short checksumAlgorithm;

    int nodeId;

    // Logger
    private static final Logger LOG =
        Logger.getLogger(FooterExtensionFileSet.class.getName());

    // Constructor takes the oid and then creates a fileset
    public FooterExtensionFileSet(NewObjectIdentifier o, Layout l,
                                  ObjectReliability r) {
        oid = o;
        layout = l;
        minFiles = r.getDataFragCount();
        numFiles = r.getTotalFragCount();

        // Create the array of footer extension files
        fefSet = new FooterExtensionFile[numFiles];

        // Get the node id and shift it by -101 to start at 0.
	NodeMgrService.Proxy proxy = 
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);        
        nodeId = proxy.nodeId() - 101;

        // Get the number of nodes in the cluster
        numNodes = proxy.getNumNodes();

        // Create each file object
        for (int fragNum=0; fragNum<fefSet.length; fragNum++) {
	    Disk disk = layout.getDisk(fragNum);

            // Make sure we have a valid disk
            if (disk != null && !disk.isNullDisk()) {
                
                // Pass the oid, fragment number, disk object, checksum alg
                fefSet[fragNum] = new FooterExtensionFile(oid, disk, fragNum);
            }
        }
    }

    // Make sure that the files are consistent. This will also make
    // sure that the files are internally consistent. If a file is not
    // internall consistent, it will be written over, instead of
    // merged, like the other files are.
    public void verify() throws OAException {

        // Status records
        int status[];
        long checksums[] = new long[numFiles];

        // File counters
        int badFiles = 0;
        boolean mergeRequired = false;

        // Get the info from each file
        for (int i=0; i<fefSet.length; i++) {
            try {
                checksums[i] = fefSet[i].checksum();
            } catch (OAException oae) {
                badFiles++;
                checksums[i] = -1;
                continue;
            }

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Verifying file " + i + " checksum is " + checksums[i]);
            }            

            // Merge and rewrite all files if any are missing or incorrect
            if (i > 0 && checksums[i] != checksums[i-1]) {
                mergeRequired = true;
            }
        }

        // If there are no extension files, return without error since
        // no properties have been added to this OID. If there are
        // some but not enough files, throw an exception since we've
        // lost the minimum number of required copies.
        if (badFiles == numFiles) {
            return;
        } else if (badFiles > (numFiles - minFiles)) {
            throw new OAException("Too many bad files");
        }

        // Return if we don't have to merge
        if (!mergeRequired) {
            return;
        }

        LOG.info("Merge required for OID " + oid + " footer extension files");

        // Merge the different files
        fe = new FooterExtension();

        // Get the info from each file
        for (int i=0; i<fefSet.length; i++) {
            if (checksums[i] == -1) {
                continue;
            }

            // Make sure we're not null
            if (fefSet[i] == null) {
                throw new OAException("fefSet[" + i + "] is null!");
            }

            // Get the tree from the file
            FooterExtension newFe = fefSet[i].read();

            // Merge it if it's not null
            if (newFe != null) {
                fe.merge(newFe);
            }
        }

        // Write out each file with the new tree
        for (int i=0; i<fefSet.length; i++) {
            fefSet[i].write(fe);
        }
    }

    // Add data
    public void add(short type, byte[] data) throws OAException {
        long mtime = System.currentTimeMillis();
        for (int i=0; i<fefSet.length; i++) {
            fefSet[i].add(nodeId, type, data, mtime);
        }
    }

    // Remove data
    public void remove(short type, byte[] data) throws OAException {
        long mtime = System.currentTimeMillis();
        for (int i=0; i<fefSet.length; i++) {
            fefSet[i].remove(type, data, mtime);
        }
    }

    // Set the last modified time
    public void setLastModified(long mtime) throws OAException {
        for (int i=0; i<fefSet.length; i++) {
            fefSet[i].setLastModified(mtime);
        }
    }

    // Get the last modified time
    public long getLastModified() throws OAException {
        verify();
        return fefSet[fefSet.length-1].getLastModified();
    }

    // Print the last file of the set. Make sure the files are
    // consistent first.
    public String toString() {
        try {
            verify();
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
        return fefSet[fefSet.length-1].toString();
    }

    // Check to see if a particular type exists. Make sure the files
    // are consistent first.
    public boolean hasType(short type) throws OAException {
        verify();
        return fefSet[fefSet.length-1].hasType(type);
    }

    // Get all instances of a particular type. Make sure the files
    // are consistent first.
    public ArrayList getType(short type) throws OAException {
        verify();
        return fefSet[fefSet.length-1].getType(type);
    }

    // Return the FooterExtension for this object. Make sure the files
    // are consistent first.
    public FooterExtension getFooterExtension() throws OAException {
        verify();
        return fefSet[fefSet.length-1].read();
    }

    // Write the given FooterExtension to this object.
    public void putFooterExtension(FooterExtension fe)
        throws OAException {
        for (int i=0; i<fefSet.length; i++) {
            fefSet[i].write(fe);
        }
    }
}
