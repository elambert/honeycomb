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



package com.sun.honeycomb.common;

import com.sun.honeycomb.common.SafeMessageDigest;

import org.doomdark.uuid.*;
import java.io.File;
import java.io.DataOutput;
import java.io.DataInput;
import java.io.IOException;
import java.io.Serializable;
import java.util.Random;
import java.util.logging.Logger;

/**
 * The UID class encapsulates a Honeycomb UID.
 */

public class UID implements Comparable, Serializable {

    private static final String DEFAULT_LIB_PATH = "/opt/honeycomb/lib";
    private static final String LIB_PATH_PROPERTY = "uid.lib.path";

    public UID() {
        uuid = UUIDGenerator.getInstance().generateTimeBasedUUID(eaddr);
    }
    
    // Note - doesn't copy!
    public UID(byte[] data) {
        uuid = new UUID(data,0);
    }

    public UID(byte[] data, int start) {
        uuid = new UUID(data, start);
    }

    public UID(String canonicalString) {
        uuid = new UUID(canonicalString);
    }
    
    public byte[] getBytes() {
        return uuid.toByteArray();
    }

    public void getBytes(byte[] dst, int start) {
        uuid.toByteArray(dst, start);
    }

    public String toString() {
        return uuid.toString();
    }
    
    public int compareTo(Object other) {
        return uuid.compareTo(((UID)other).uuid);
    }

    public int hashCode() {
        return uuid.hashCode();
    }
    
    public boolean equals(Object other) {
        /*
         * Use equals because the compareTo does additional work that is not 
         * necessary for figuring out equality between UIDs
         */
        return uuid.equals(((UID)other).uuid);
    }

    /** Calculate something guarenteed to be evenly distributed for
     * picking fragment file directires */
    public synchronized String calculateHash() {
        if(hash != null) {
            return hash;
        }

        hash = ByteArrays.toHexString(SafeMessageDigest.digest(getBytes(),"SHA"));

        return hash;
    }

    public void serialize(DataOutput dout) 
        throws IOException {
        byte[] data = uuid.asByteArray();
        dout.writeInt(data.length);
        dout.write(data);
    }
    
    public static UID deserialize(DataInput din) 
        throws IOException {
        int size = din.readInt();
        byte[] data = new byte[size];
        din.readFully(data);
        return( new UID(data) );
    }


    
    protected static final Logger LOG = 
        Logger.getLogger(UID.class.getName());
    private static EthernetAddress eaddr = null;
    private UUID uuid = null;
    private byte[] data = null;
    public static final int NUMBYTES = 16;
    private static final int HEX_RADIX = 16;
    private static final int BITS_PER_NIBBLE = 4;
    private static final int MAX_DECIMAL = 10;
    String hash = null; // Used to pick a directory for fragment files
    
    static {
        String path = System.getProperty(LIB_PATH_PROPERTY);
        if (path == null) {
            path = DEFAULT_LIB_PATH;
        } else if (path.equals("emulator")) {
            path = null;
        } else {
            LOG.warning("Loading UID libraries from ["+path+"]");
        }

        if (path != null) {
            NativeInterfaces.
                setLibDir(new File(path));
            eaddr = NativeInterfaces.getPrimaryInterface();
        } else {
            byte[] addr = { (byte)0xca, (byte)0xfe, 
                            (byte)0xca, (byte)0xfe, 
                            (byte)0xca, (byte)0xfe };
            eaddr = new EthernetAddress(addr);
        }

        if(eaddr == null) {
            LOG.info("ethernet address is null - UUIDs will be suboptimal");
        } else {
            LOG.fine("Using ethernet address " + eaddr + 
                     " for UUID generation");
        }
        
        UUIDGenerator.getInstance().setRandomNumberGenerator(new Random());
    }
}
