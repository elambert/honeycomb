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

import com.sun.honeycomb.oa.bulk.BackupRestore;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;

public class ObjectParser extends ContentBlockParser {

    public static final String CONTENT_TYPE = "Object";

    public ObjectParser(Session session) throws SerializationException {
        super(CONTENT_TYPE,session);
    
        if (session.getStreamFormat().equals(BackupRestore.STREAM_OA_OBJ))
            _serializer = new OAObjectSerializer(session);
        else if (session.getStreamFormat().equals(BackupRestore.STREAM_7_FRAGS))
            _serializer = new Frag7Serializer(session);
        else 
            throw new SerializationException("Unkown stream format: " + 
                                             session.getStreamFormat());
    }
    
    protected void writeExtendedHeaders(StreamWriter writer) 
              throws SerializationException {
        writer.writeHeader(Constants.OID_HEADER, ((ObjectSerializer)_serializer).getOID());
        writer.writeHeader(Constants.METADATA_LENGTH_HEADER, 
                           ((ObjectSerializer)_serializer).getMetadataLength());
        writer.writeHeader(Constants.N_DATA_BLOCKS_HEADER, 
                              ((ObjectSerializer)_serializer).getNDataBlocks());
    }
}
