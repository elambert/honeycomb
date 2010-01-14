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

import java.util.BitSet;
import java.util.Arrays;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.ObjectMetadata;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ExtendedMetadata;
import com.sun.honeycomb.common.ObjectMetadataInterface;

/** This is the object metadata. It defines information about the
 *  object that is needed for search, retrieval and reconstruction.
 *  It is made up of OA, System, and Extended metadata
 */

public class ObjectInfo 
    implements ObjectMetadataInterface {
    private static final short SCHEMA_VERSION = 0; // is this used?
    private static final short TYPE = 2; // is this used?
   
    /* system-specified metadata set when an object gets stored */
    private SystemMetadata smd;
    
    /* user-specified metadata set when object gets stored */
    private ExtendedMetadata emd;
    
    public ObjectInfo(SystemMetadata smd,
                      ExtendedMetadata emd) {
        this.smd = smd;
        this.emd = emd;
    }
    
    /* copy constructor */ 
    public ObjectInfo(ObjectInfo oi) {
        this.smd = new SystemMetadata(oi.getSystemMetadata());
        this.emd = new ExtendedMetadata(oi.getExtendedMetadata());
    }
    
    private ObjectInfo() { // Is this necessary?
    }
    
    public SystemMetadata getSystemMetadata() {
        return smd;
    }
    
    public ExtendedMetadata getExtendedMetadata() {
        return emd;
    }
    
    public String toString() {
        return( smd.toString() + emd.toString() );
    }
    
    /** comparisons of two object infos.**/
    // TODO - rename to equals, ctime and atime count now
    public boolean isSimilar(ObjectInfo other) {
        if(other == null) { return false; }
        else if(other.getClass() != this.getClass()) {
            return false;
        } else {
            return (this.smd.equals(other.smd) &&
                    this.emd.equals(other.emd));
        }
    }
    
    public ObjectMetadata makeObjectMetadata() {
        return new ObjectMetadata(smd, emd);
    }

    /* the right way of doing this is to use the XdrSerializable interface
     * and use the methods readObject and writeObject. */
    
    public void serialize(DataOutput output) throws IOException {
        output.writeShort(SCHEMA_VERSION);
        output.writeShort(TYPE);
        smd.serialize((DataOutput)output);
        emd.serialize((DataOutput)output);
    }
    
    public static ObjectInfo deserialize(DataInput input) throws IOException {
        int schema = input.readShort();
        if (schema != SCHEMA_VERSION) {
            throw new IOException("Schema version mismatch: expected " +
                                  SCHEMA_VERSION + ", got " + schema);
        }
        
        int type = input.readShort();
        if (type != TYPE) {
            throw new IOException("Type mismatch: expected " +
                                  TYPE + ", got " + type);
        }
        ObjectInfo oi = new ObjectInfo();
        oi.smd = SystemMetadata.deserialize((DataInput)input);
        oi.emd = ExtendedMetadata.deserialize((DataInput)input);
        
        return oi;
    }
}
