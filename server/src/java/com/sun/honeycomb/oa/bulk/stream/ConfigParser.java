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

import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;

public class ConfigParser extends ContentBlockParser {

    /**
     * config parser is used for all of the configs: cluster_config and metadata
     * config all you need to do is update the constructor here in order to 
     * handle the new config file.
     * 
     * @param session
     * @throws SerializationException 
     */
    public ConfigParser(Session session, String contentType) 
           throws SerializationException {
        super(contentType, session);
       
        if (contentType.equals(CLCONF_BLOCK_PARSER))
            _serializer = new ConfigSerializer(session, CMMApi.UPDATE_DEFAULT_FILE);
        
        if (contentType.equals(SCHEMA_BLOCK_PARSER))
            _serializer = new ConfigSerializer(session, CMMApi.UPDATE_METADATA_FILE);
      
        if (contentType.equals(SILOCONF_BLOCK_PARSER))
            _serializer = new ConfigSerializer(session, CMMApi.UPDATE_SILO_FILE);

        if (_serializer == null)
            throw new SerializationException("Content type not supported: " + contentType);
    }
   
    protected void writeExtendedHeaders(StreamWriter writer) 
              throws SerializationException {
        // No extended headers
    }

    protected void readExtendedHeaders(StreamReader reader) 
              throws SerializationException {
        // No extended headers 
    }
}
