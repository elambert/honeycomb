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
import java.util.Map;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.emd.common.EMDException;

/**
 * ContentSerializer class is an interface used to have common serialization mechanism for 
 * all objects in the bulk data stream
 */
public abstract class ContentSerializer {
    protected Session _session = null;
    
    public static long UNKOWN_LENGTH = -1;
    public static long SKIP_BLOCK    = -2;
  
    /**
     * Basic Constructor for a ContentSerializer
     * @param session
     */
    public ContentSerializer(Session session) {
        _session = session;
    }
    
    /**
     * The init method can be used by the ContentSerializer to initialize any necessary 
     * mechanisms that later allow the getContentLength to return the correct content 
     * length. 
     * @param obj
     * @throws SerializationException in case of any errors
     */
    public abstract void init(Object obj) throws SerializationException;

    /**
     * Serialize the current Content to the StreamWriter specified.
     *  
     * @param writer StreamWriter to use to serialize the Content 
     * @return returns a CallbackObject for the Callback class to process 
     * @throws SerializationException in case of any errors
     */
    public abstract CallbackObject serialize(StreamWriter writer)
                    throws SerializationException, ArchiveException, OAException, IOException;

    /**
     * 
     * Deserialize the current Content from the StreamReader specified.
     * 
     * @param reader
     * @return
     * @throws SerializationException in case of any errors
     */
    public abstract CallbackObject deserialize(StreamReader reader, Map headers) 
                    throws SerializationException, ArchiveException, OAException, IOException;
  
    /**
     * return the content length of this content being handled.
     *  
     * @return length in bytes of the content being serialized by this class
     */
    public abstract long getContentLength() throws SerializationException, EMDException;
}
