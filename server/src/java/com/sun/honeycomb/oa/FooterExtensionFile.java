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
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.config.ClusterProperties;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.channels.FileLock;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Object Archive Footer Extension File
 *
 * This is used to store variable size data about objects that would
 * otherwise go into fragment footers. Arbitrary datatypes can be
 * stored here and there is no limit on how much data is
 * stored. However, there is a limit on the number of entries which is
 * determined by an integer used to specify slot IDs. 
 *
 */
public class FooterExtensionFile {

    // Logger
    protected static final Logger LOG = 
        Logger.getLogger(FooterExtensionFile.class.getName());
    
    private final FragmentFile ff;

    // Slot storage
    private FooterExtension fe;

    // Constructor takes the filename
    FooterExtensionFile(NewObjectIdentifier o, Disk d, int f) {
        ff = new FragmentFile(o, f, d);
    }

    // Read the footer extension
    FooterExtension read() throws OAException {
        fe = ff.readFooterExtension();
        return fe;
    }

    // Write the local footer extension
    void write() throws OAException {
        if (fe == null) {
            throw new OAException("footer not initialized");
        }
        ff.writeFooterExtension(fe);
    }

    // Write the given footer extension
    void write(FooterExtension extension) throws OAException {
        ff.writeFooterExtension(extension);
    }

    // Print the footer extension. Since we're overriding
    // Object.toString() we can't throw an exception if something
    // fails.
    public String toString()  {
        String contents = "";
        try {
            read();
            contents = fe.toString();
        } catch (Exception e) {
            e.printStackTrace();
            contents = "ERROR";
        }
        return contents;
    }

    // Set the last modified time but not anything else
    void setLastModified(long mtime) throws OAException {
        read();
        fe.setLastModified(mtime);
        write();
    }

    // Get the last modified time
    public long getLastModified() throws OAException {
        read();
        return fe.getLastModified();
    }

    // Add an entry
    void add(int node, short type, byte[] data, long mtime)
        throws OAException {
        
        // Get the number of nodes in the cluster
        int numNodes = (ServiceManager.proxyFor(ServiceManager.LOCAL_NODE)).
            getNumNodes();

        // Check that the node id ranges from [0-15]
        if (node < 0 || node >= numNodes) {    
            throw new OAException("Node id is out of range: " + node);
        }
        
        // Check the type
        if (type != FooterExtension.LEGAL_HOLD) {    
            throw new OAException("Invalid data type: " + type);
        }

        // Write, add, write
        read();
        fe.add(node, type, data, mtime);
        write();
    }

    // Remove an entry
    void remove(short type, byte[] data, long mtime) 
        throws OAException {
        read();
        fe.remove(type, data, mtime);
        write();
    }

    // Get the checksum
    int checksum() throws OAException {
        read();
        return fe.checksum();
    }

    // Check to see if a particular type exists
    boolean hasType(short type) throws OAException {
        read();
        return fe.hasType(type);
    }

    // Get all instances of a particular type
    ArrayList getType(short type) throws OAException {
        ArrayList data = null;
        read();
        data = fe.getType(type);
        return data;
    }
}
