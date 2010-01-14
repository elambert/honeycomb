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

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.io.IOException;
import java.io.DataInput;
import java.io.DataOutput;

// This is what comes back from getMetadata - it is publicly visible
// It is simply a composite object that we can easily return from things

public class ObjectMetadata 
    implements ObjectMetadataInterface {
    
    public ObjectMetadata(SystemMetadata smd, 
                          ExtendedMetadata emd) {
        this.smd = smd;
        this.emd = emd;
    }
    
    // Accessor Methods
    public SystemMetadata getSystemMetadata() {return smd;}
    public ExtendedMetadata getExtendedMetadata() {return emd;}
    
    // Equality
    public boolean equals(Object anObject) {
        return ((anObject != null) &&
                (anObject.getClass() == this.getClass()) &&
                (((ObjectMetadata)anObject).smd.equals(smd)) &&
                (((ObjectMetadata)anObject).emd.equals(emd)));
    }
    
    // Binary Serialize
    public void serialize(DataOutput output) throws IOException {
        smd.serialize(output);
        emd.serialize(output);
    }
    
    
    // Text Serialize
    public String toString() {
        return emd.toString();
    }
    
    // Binary Deserialize
    public static ObjectMetadata deserialize(DataInput din) 
        throws IOException {
        return new ObjectMetadata(SystemMetadata.deserialize(din),
                                  ExtendedMetadata.deserialize(din));
    }
    
    // Private Members
    private SystemMetadata smd = null;
    private ExtendedMetadata emd = null;
}
