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

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.DeletedObjectException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;

public abstract class ObjectSerializer extends ContentSerializer {
    
    protected OAClient _oaclient = null;
     
    /**
     * Basic ObjectSerializer constructor
     * 
     * @param session
     */
    public ObjectSerializer(Session session) {
        super(session);
        _oaclient = OAClient.getInstance();
    }

    /**
     * @return returns a String representation of the current OID of the current object. 
     */
    public abstract String getOID();
 
    /**
     * @return length of the metadata block
     */
    public abstract long getMetadataLength();
   
    /**
     * @return number of data blocks that are present.
     */
    public abstract long getNDataBlocks();
  
    // Utility functions for subclasses
    
    /**
     * read system metadata from the StreamReader specified
     *  
     * @param reader
     * @return
     * @throws SerializationException
     */
    public SystemMetadata readSystemMetadata(StreamReader reader)
           throws SerializationException {
        HashMapSerializer map = new HashMapSerializer();
        map.deserialize(reader);
        SystemMetadata sm = new SystemMetadata();
        sm.populateFrom(map, true);
        return sm;
    } 
  
    /**
     * read the system metadata for the specified oid from OA
     *  
     * @param oid NewObjectIdentifier of the object to read from OA 
     * @return system record for the specified object. If the object is 
     *         incomplete then null will be returned.
     *         
     * @throws SerializationException
     */
    public SystemMetadata readSystemMetadata(NewObjectIdentifier oid) {
        SystemMetadata sm = null;
        try {
            sm = _oaclient.getSystemMetadata(oid, true, false);
        } catch (NoSuchObjectException e) {
            return null;
        } catch (OAException e) {
            return null;
        }
        return sm;
    }
   
    /** 
     * stream the SystemMetadata object to the StreamWriter
     *  
     * @param sm
     * @param writer
     * @throws SerializationException
     */
    public void writeSystemMetadata(SystemMetadata sm, StreamWriter writer) throws SerializationException, EMDException {
        writer.writeLine(smToString(sm));
    }
    
    /**
     * convert a SystemMetadata object to a string that consists of each key 
     * value separated by a new line
     *  
     * @param sm 
     * @return
     * @throws SerializationException
     */ 
    public String smToString(SystemMetadata sm) throws EMDException {
        HashMapSerializer map = new HashMapSerializer();
        sm.populateStrings(map, true);
        return map.toString();
    }
}
