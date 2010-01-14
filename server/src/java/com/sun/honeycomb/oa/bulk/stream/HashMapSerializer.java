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



package com.sun.honeycomb.oa.bulk.stream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;

import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.bulk.SerializationException;

public class HashMapSerializer extends HashMap {

    /**
     * 
     * @param writer
     * @throws SerializationException
     */
    public void serialize(StreamWriter writer)
            throws SerializationException {
        try {
            writer.writeLine(toString());
        } catch (SerializationException e) {
            throw new SerializationException("Error streaming hashmap", e);
        }
    }

    static final String DELETED_REFS_HEADER = 
        SystemMetadata.FIELD_NAMESPACE + "." + 
        SystemMetadata.FIELD_DELETED_REFS;

    static final int DELETED_REFS_LENGTH = FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH/8;
    private ByteBuffer _deletedRefsBB =
        ByteBuffer.allocate(DELETED_REFS_LENGTH);

    private byte[] _deletedRefsBytes = new byte[FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH/8];


    /**
     * 
     * @param reader
     * @throws SerializationException
     */
    public void deserialize(StreamReader reader) 
                throws SerializationException{
        String key;
        String value;
        this.clear();
        
        while (true) {
            try {
                key = reader.readLine();
                 
                if (key == null || key.equals(Constants.HEADER_TERMINATOR))
                    break;

                /*
                 * TODO: once we start handling versioning of the stream we 
                 *       should handle this field that way and not do this 
                 *       special reading for one of the entries in the this 
                 *       block.
                 */
                if (key.equals(DELETED_REFS_HEADER)) {
                    int r = reader.read(_deletedRefsBB);
                    int c = r;
                    while (r != 0 && c < DELETED_REFS_LENGTH){
                        r = reader.read(_deletedRefsBB);
                        c += r;
                    }
                    reader.readLine();
                    _deletedRefsBB.flip();
                    _deletedRefsBB.get(_deletedRefsBytes);
                    value = new String(_deletedRefsBytes);
                } else { 
                    value = reader.readLine();
                }
                
                put(key, value);
            } catch (IOException e) {
                throw new SerializationException("Error reading name,value pairs from System Metadata block.",e);
            }
        }
    }
   
    /**
     * 
     */
    public String toString() {
        Iterator iterator = keySet().iterator();
        StringBuffer str = new StringBuffer();
       
        while (iterator.hasNext()) {
           String key = (String) iterator.next(); 
           str.append(key + "\n");
           str.append(get(key) + "\n");
        }
        
        return str.toString();
    }
}
