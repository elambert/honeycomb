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



package com.sun.honeycomb.emd.common;

import java.util.Iterator;
import java.util.HashMap;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.DataInputStream;

public class CodableMap
    extends HashMap
    implements Codable {

    public CodableMap() {
        super();
    }

    /**********************************************************************
     *
     * Codable API
     *
     **********************************************************************/

    public void encode(Encoder encoder) {
        int s = size();
        encoder.encodeInt(s);

        Iterator iterator = keySet().iterator();
        while (iterator.hasNext()) {
            String key = (String)iterator.next();
            encoder.encodeString(key);
            encoder.encodeString((String)get(key));
        }
    }
    
    public void decode(Decoder decoder) {
        int s = decoder.decodeInt();
        for (int i=0; i<s; i++) {
            String key = decoder.decodeString();
            String value = decoder.decodeString();
            put(key, value);
        }
    }

    public void serialize(DataOutputStream output) 
	 throws IOException {
        int s = size();
	output.writeInt(s);

        Iterator iterator = keySet().iterator();
        while (iterator.hasNext()) {
            String key = (String)iterator.next();
	    output.writeUTF(key);
	    output.writeUTF((String)get(key));
        }
    }

    public static CodableMap deserialize(DataInputStream input)
	throws IOException {
        int s = input.readInt();
	CodableMap result = new CodableMap();
        for (int i=0; i<s; i++) {
            String key = input.readUTF();
            String value = input.readUTF();
            result.put(key, value);
        }
	
	return(result);
    }
}
