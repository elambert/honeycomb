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

import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.oa.OAException;

public abstract class ContentBlockParser {
    
    protected String _contentType = null;
    protected ContentSerializer _serializer = null;
    protected Session _session = null;
    
    /**
     * By registering these constants here any new block parsers created will make
     * the author come here and put them in the same place as the previous 
     * constants and understand how they are used below to get the right 
     * ContentBlockParser class
     * 
     */
    public static String OBJECT_BLOCK_PARSER   = "Object";
    public static String CLCONF_BLOCK_PARSER   = "ClusterConfig";
    public static String SILOCONF_BLOCK_PARSER = "SiloConfig";
    public static String SCHEMA_BLOCK_PARSER   = "SchemaConfig";
    public static String SYSCACHE_BLOCK_PARSER = "SysCache";
    public static String END_OF_STREAM_BLOCK   = "EndOfStream";

    /**
     * 
     * @param contentType identifies the contentType to use for this 
     *                    ContentBlockParser.
     * @param session return the Session for streaming data.
     */
    protected ContentBlockParser(String contentType, Session session) {
        _contentType = contentType;
        _session = session;
    }
    
    /**
     *
     * Used to write default headers to the StreamWriter specified
     * 
     * @param writer - StreamWriter used to write out the default headers to 
     * @param contentLength - by passing -1 you will set the Encoding-Type to chunked 
     * @throws SerializationException
     */
    protected final void writeDefaultHeaders(StreamWriter writer,
                                             long contentLength) 
                    throws SerializationException {
        writer.writeHeader(Constants.CONTENT_TYPE_HEADER, _contentType);
        writer.writeHeader(Constants.CONTENT_LENGTH_HEADER, contentLength);
    }

    /**
     * 
     * @return
     */
    public ContentSerializer getContentSerializer() {
        return _serializer;
    }
   
    /**
     * Default behaviour for any BlockFormatter is to serialize the object onto 
     * the StreamWriter and make sure to call the callback, in some instances
     * these may not be the case and can be overriden by sub-classes
     * 
     */
    protected void serialize(long offset, StreamWriter writer) 
              throws SerializationException, ArchiveException, OAException, IOException {
        CallbackObject obj = _serializer.serialize(writer);
        obj.setStreamOffset(offset);
        _session.callback(obj);
    }
    
    /**
     * Default behaviour for any BlockFormatter is to deserialize the object 
     * onto the StreamWriter and make sure to call the callback, in some
     * instances these may not be the case and can be overriden by sub-classes
     * 
     */
    protected void deserialize(long offset, StreamReader reader, Map headers) 
              throws SerializationException, ArchiveException, OAException, IOException {
        CallbackObject obj = _serializer.deserialize(reader, headers);
        obj.setStreamOffset(offset);
        _session.callback(obj);
    }
   
    /**
     * this method returns the correct block parser for the block name specified
     * and instantiates that block parser with the session so it can use it 
     * during parsing activities.
     * 
     * @param blockName
     * @param session
     * @return
     * @throws SerializationException
     */
    public static ContentBlockParser getBlockParser(String blockName, Session session) 
                  throws SerializationException { 
    
        if (blockName.equals(OBJECT_BLOCK_PARSER))
            return new ObjectParser(session);

        if (blockName.equals(SYSCACHE_BLOCK_PARSER))
            return new SysCacheParser(session);
       
        if (blockName.equals(CLCONF_BLOCK_PARSER))
            return new ConfigParser(session, CLCONF_BLOCK_PARSER);
        
        if (blockName.equals(SCHEMA_BLOCK_PARSER))
            return new ConfigParser(session, SCHEMA_BLOCK_PARSER);

        if (blockName.equals(SILOCONF_BLOCK_PARSER))
            return new ConfigParser(session, SILOCONF_BLOCK_PARSER);

        throw new SerializationException("Unknown content block type: '" +
                                         blockName + "'");
    }
    
    /**
     * new content block parsers must define how to write extended headers for
     * this new block parser or just define an empty method if this block parser
     * doesn't need any extended headers
     *  
     * @param writer
     * @throws SerializationException
     */
    protected abstract void writeExtendedHeaders(StreamWriter writer) 
              throws SerializationException;    

}
