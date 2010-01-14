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

import com.sun.honeycomb.cm.ServiceManager;

import java.util.Arrays;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.util.zip.Adler32;
import java.util.logging.Logger;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;

/**
 * Footer Extension File (FEF) layout. A valid file has at least
 * the version, last modified time (also used to track retention
 * time upates), and a checksum. It will have zero or more slots
 * used to store legal hold and other data about an object which
 * cannot fit into the OA footer. 
 *
 * -----------------------------------------------------------
 * version (2)
 * last modified time (8)
 * slot id (4) | type (2) | data length (4) | data (arbitrary)
 * checksum (4)
 */
public class FooterExtension {

    public static final short EMPTY_SLOT = 0;
    public static final short LEGAL_HOLD = 1;
    public static final String TYPE_STRINGS[] = { "EMPTY_SLOT", "LEGAL_HOLD" };

    public static final String SUFFIX = ".fef";

    public static final int SLOT_ID_SIZE = 4;
    public static final int TYPE_SIZE = 2;
    public static final int LENGTH_SIZE = 4;
    public static final int MODIFIED_SIZE = 8;

    public static final short VERSION = 1;

    public static final int VERSION_SIZE = 2;
    public static final int CHECKSUM_SIZE = 4;
    public static final int MIN_FILE_SIZE = VERSION_SIZE + CHECKSUM_SIZE;

    private ArrayList slots[];
    private long lastModified = 0;

    // Logger
    protected static final Logger LOG = 
        Logger.getLogger(FooterExtension.class.getName());

    // Constructor takes the number of nodes
    public FooterExtension() {

        // Get the number of nodes in the cluster
        int numNodes = (ServiceManager.proxyFor(ServiceManager.LOCAL_NODE)).
            getNumNodes();

        // Create the slot array
        slots = new ArrayList[numNodes];
    }

    // Add a new item
    public void add(int node, short type, byte data[], long mtime) {
        add(node, -1, type, data, mtime);
    }

    // Add an item with or without an index
    public void add(int node, int index, short type, byte data[], long mtime) {
        FooterExtensionSlot slot = new FooterExtensionSlot(type, data);

        // Create an ArrayList if this node has no entries
        if (slots[node] == null) {
            slots[node] = new ArrayList();
        }

        // Just add to the end with a new element & set last modified time
        if (index == -1) {
            slots[node].add(slot);
            lastModified = mtime;
        } else {

            // If the array is not yet that big, add the slot to that
            // index. Otherwise, set it instead to replace the
            // existing one in the case of remove.
            if ((slots[node].size() - 1) < index) {
                slots[node].add(index, slot);
            } else {
                slots[node].set(index, slot);
            }
        }
    }

    // Merge a tree into this one
    public void merge(FooterExtension fe) {
        for (int node = 0; node < fe.slots.length; node++) {
            ArrayList slotArray = fe.slots[node];
            if (slotArray == null) {
                continue;
            }

            for (int index = 0; index < slotArray.size(); index++) {
                FooterExtensionSlot slot =
                    (FooterExtensionSlot)slotArray.get(index);
                add(node, index, slot.getType(), slot.getData(), -1);
            }
        }

        // Set the last modified time to be the greatest of the two
        if (fe.getLastModified() > lastModified) {
            lastModified = fe.getLastModified();
        }
    }

    // Return just the data in string format
    public String dataToString(short type, byte data[]) {
        String output = "[EMPTY]";

        if (type == LEGAL_HOLD) {
            try {
                output = new String(data, "UTF8");
            } catch(UnsupportedEncodingException uee) {
                uee.printStackTrace();
                output = "[UNKNOWN]";
            }
        }

        return output;
    }

    // Return this object as a string
    public String toString() {
        String output = "Last Modified: " + lastModified + "\n\n"; 
        output = output + "(N,S) Slot Type Len Data\n";
        output = output + "----- ---- ---- --- ----\n";

        for (int node = 0; node < slots.length; node++) {
            ArrayList slotArray = slots[node];
            if (slotArray == null) {
                continue;
            }

            for (int index = 0; index < slotArray.size(); index++) {
                FooterExtensionSlot slot =
                    (FooterExtensionSlot)slotArray.get(index);

                // Calculate the slot id
                int slotId = (node << 16) + index;

                output = output + "(" + node + "," + index + ") " +
                    slotId + " | " +
                    TYPE_STRINGS[slot.getType()] + " | " +
                    slot.getLength() + " | " +
                    dataToString(slot.getType(), slot.getData()) + "\n";
            }
        }

        return output;
    }

    // Read in a tree from a byte stream
    public void read(ByteBuffer fileBuffer) {

        // Set the last modified date
        lastModified = fileBuffer.getLong();

        // Put the file contents into the slot structure
        while (fileBuffer.hasRemaining()) {

            // Read the slot id
            int slot = fileBuffer.getInt();
            short type = fileBuffer.getShort();
            int size = fileBuffer.getInt();

            // Put the data into a byte array
            byte[] data = null;
            if (size > 0) {
                data = new byte[size];
                fileBuffer.get(data);
            }

            // Get the node id and slot id
            int node = slot >> 16;
            int index = slot & 0x0000ffff;

            // Add to the tree
            add(node, index, type, data, -1);
        }
    }

    // Get the size of the slots
    public int size() {

        // Set the counter to size of the last modified time
        int counter = MODIFIED_SIZE;

        // Loop to get the size of the data slots
        for (int node = 0; node < slots.length; node++) {
            ArrayList slotArray = slots[node];

            if (slotArray == null) {
                continue;
            }

            for (int index=0; index<slotArray.size(); index++) {
                FooterExtensionSlot slot =
                    (FooterExtensionSlot)slotArray.get(index);
                counter = counter + SLOT_ID_SIZE + TYPE_SIZE +
                    LENGTH_SIZE + slot.getLength();
            }
        }

        return counter;
    }

    // Return this object as a ByteBuffer
    public ByteBuffer asByteBuffer() {

        // Get the size
        int bufferSize = size();

        // Create the ByteBuffer
        ByteBuffer buf = ByteBuffer.allocateDirect(bufferSize);

        // Add the last modified time
        buf.putLong(lastModified);

        // Second loop to add the contents
        for (int node = 0; node < slots.length; node++) {
            ArrayList slotArray = slots[node];

            if (slotArray == null) {
                continue;
            }

            for (int index=0; index<slotArray.size(); index++) {
                FooterExtensionSlot slot =
                    (FooterExtensionSlot)slotArray.get(index);

                // Write out the data
                int slotId = (node << 16) + index;
                buf.putInt(slotId);
                buf.putShort(slot.getType());
                buf.putInt(slot.getLength());
                if (slot.getLength() > 0)
                    buf.put(slot.getData());
            }
        }

        buf.flip();
        return buf;
    }

    // Remove all instances of matching data with the given type
    public void remove(short type, byte data[], long mtime) {
        for (int node = 0; node < slots.length; node++) {
            ArrayList slotArray = slots[node];

            if (slotArray == null) {
                continue;
            }

            for (int index = 0; index < slotArray.size(); index++) {
                FooterExtensionSlot slot =
                    (FooterExtensionSlot)slotArray.get(index);

                if (slot.getType() == type &&
                    Arrays.equals(data, slot.getData())) {
                    FooterExtensionSlot emptySlot =
                        new FooterExtensionSlot(EMPTY_SLOT, null);
                    slotArray.set(index, emptySlot);
                    lastModified = mtime;
                }
            }
        }
    }

    // Find out if we have any entries of the given type
    public boolean hasType(short type) {
        for (int node = 0; node < slots.length; node++) {
            ArrayList slotArray = slots[node];

            if (slotArray == null) {
                continue;
            }

            for (int index = 0; index < slotArray.size(); index++) {
                FooterExtensionSlot slot =
                    (FooterExtensionSlot)slotArray.get(index);

                if (slot.getType() == type) {
                    return true;
                }
            }
        }

        return false;
    }

    // Return all instances of a given type
    public ArrayList getType(short type) {
        ArrayList results = new ArrayList(slots.length);

        for (int node = 0; node < slots.length; node++) {
            ArrayList slotArray = slots[node];

            if (slotArray == null) {
                continue;
            }

            for (int index = 0; index < slotArray.size(); index++) {
                FooterExtensionSlot slot =
                    (FooterExtensionSlot)slotArray.get(index);

                if (slot.getType() == type) {
                    results.add(slot.getData());
                }
            }
        }

        if (results.size() > 0) {
            return results;
        }
         
        return null;
    }

    // Get the checksum
    public int checksum() {
        //ByteBuffer buffer = ByteBuffer.wrap(data);
        //ChecksumAlgorithm algorithm =
        //    ChecksumAlgorithm.getInstance(checksumAlgorithm);
        //AlgorithmState internalState = algorithm.createAlgorithmState();
        //algorithm.update(buffer, buffer.remaining(), internalState);
        //return algorithm.getIntValue(internalState);

        // Calculate the checksum of the byte array
        Adler32 checksumAlgorithm = new Adler32();
        ByteBuffer checksumBuffer = asByteBuffer();
        byte[] checksumBytes = new byte[checksumBuffer.capacity()];
        checksumBuffer.get(checksumBytes);
        checksumAlgorithm.update(checksumBytes);
        long checksumValue = checksumAlgorithm.getValue();
        return (int)checksumValue;
    }

    // Set the last modified time
    public void setLastModified(long mtime) {
        lastModified = mtime;
    }

    // Get the last modified time
    public long getLastModified() {
        return lastModified;
    }
    
    class FooterExtensionSlot {
        private short type;
        byte[] data;
        
        public FooterExtensionSlot(short t, byte[] d) {
            type = t;
            data = d;
        }
        
        public short getType() {
            return type;
        }
        
        public int getLength() {
            if (data == null) {
                return 0;
            }
            return data.length;
        }
        
        public byte[] getData() {
            return data;
        }
    }    
}
