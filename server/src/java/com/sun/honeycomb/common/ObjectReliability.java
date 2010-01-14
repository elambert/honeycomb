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

import java.io.IOException;
import java.io.DataInput;
import java.io.DataOutput;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;

/**
 * This class represents the reliability of an object stored in the
 * cluster.  It encapsulates the N (data) and M(checksum) fragments
 * each block should be split into.  It will eventually provide more
 * interesting ways of specifing reliability (percentages, etc.) and
 * probably should _not_  be erasure-coding specific!
 *
 * TODO - JavaDoc
 */

public class ObjectReliability implements Codable {

    /* specify N and M */
    public ObjectReliability(int nDataFrags, int mRedundantFrags) {
        dataFrags = nDataFrags;
        redundantFrags = mRedundantFrags;
    }

    /* specify M, default N */
    public ObjectReliability(int mRedundantFrags) {
        dataFrags = DEFAULT_DATA_FRAGS;
        redundantFrags = mRedundantFrags;
    }

    /* default M, default N */
    public ObjectReliability() {
        dataFrags = DEFAULT_DATA_FRAGS;
        redundantFrags = DEFAULT_REDUNDANT_FRAGS;
    }

    /* copy constructor */
    public ObjectReliability(ObjectReliability or) {
        dataFrags = or.dataFrags;
        redundantFrags = or.redundantFrags;
    }
    
    public int getDataFragCount() {
        return dataFrags;
    }

    public int getRedundantFragCount() {
        return redundantFrags;
    }

    public int getTotalFragCount() {
        return dataFrags + redundantFrags;
    }

    public boolean equals(Object obj) {
        ObjectReliability compareTo = (ObjectReliability)obj;
        if (dataFrags != compareTo.getDataFragCount()) {
            return false;
        }
        if (redundantFrags != compareTo.getRedundantFragCount()) {
            return false;
        }
        return true;
    }
    
    public String toString() {
        return("data(n): " + Integer.toString(dataFrags) + 
               "\n" +
               "checksum(m): " + Integer.toString(redundantFrags) + 
               "\n");
    }
    
    public void encode(Encoder encoder) {
        encoder.encodeInt(dataFrags);           // 4
        encoder.encodeInt(redundantFrags);      // 4
    }
    
    public void decode(Decoder decoder) {
        dataFrags = decoder.decodeInt();        // 4
        redundantFrags = decoder.decodeInt();   // 4
    }

    // TODO: serialize and deserialize replaced by encode/decode

    public void serialize(DataOutput dout) throws IOException {
        if(dout == null) throw new IOException("No DataOutput!");
        dout.writeInt(dataFrags);
        dout.writeInt(redundantFrags);
    }
    
    public static ObjectReliability deserialize(DataInput din) 
        throws IOException {
        if(din == null) throw new IOException ("No DataInput!");
        
        int n = din.readInt();
        int m = din.readInt();
        
        return new ObjectReliability(n, m);
    }
    
    private int dataFrags;
    private int redundantFrags;

    private static final int DEFAULT_DATA_FRAGS = 6;
    private static final int DEFAULT_REDUNDANT_FRAGS = 4;
}
