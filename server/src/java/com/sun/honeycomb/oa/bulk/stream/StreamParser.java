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
import java.util.Date;
import java.util.Map;

import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.oa.bulk.BackupSession;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.common.ArchiveException;

public class StreamParser {
    protected Session _session = null;
   
    /**
     * Constructor for the StreamParser
     * 
     * @param session identifies the Session being delt with.
     */
    public StreamParser(Session session) {
        _session = session;
    }

    /**
     * write the stream headers to the StreamWriter specified
     * 
     * @param writer is the StreamWriter used during streaming. 
     * @throws SerializationException
     */
    public void writeHeaders(StreamWriter writer) throws SerializationException {
        // write stream headers
        writer.writeHeader(Constants.VERSION_HEADER, 
                           _session.getBackupVersion());
        writer.writeHeader(Constants.CONTENT_DESCRIPTION_HEADER, 
                           _session.getStreamFormat());

        String date = Constants.DATE_FORMAT.format(new Date());
        writer.writeHeader(Constants.CREATION_TIME, date);

        date = Constants.DATE_FORMAT.format(((BackupSession)_session)._start);
        writer.writeHeader(Constants.START_TIME, date);

        date = Constants.DATE_FORMAT.format(((BackupSession)_session)._end);
        writer.writeHeader(Constants.END_TIME, date);
       
        // write termination line
        writer.writeSeparator();
    }

  
    /**
     * write a block to the specified stream
     * 
     * @param parserName name of the content block parser to use, constants 
     *                   define as static variables of ContentBlockParser.
     * @param writer StreamWriter used to stream the current content block out
     * @param obj Possible object used by the ContentSerializer, could be null.
     * @throws SerializationException
     */
    public final void writeBlock(String parserName, StreamWriter writer,
                                 Object obj) 
                 throws SerializationException, ArchiveException, OAException, IOException{
        // save the current offset for the beginning of this block
        long offset = writer.getOffset();
        
        ContentBlockParser formatter = ContentBlockParser.getBlockParser(parserName, _session);
        formatter.getContentSerializer().init(obj);

        long contentLength = formatter.getContentSerializer().getContentLength();
       
        /*
         * If contentLength is SKIP_BLOCK then there's nothing to do. This is 
         * used mainly by object block formatters but can be used in other 
         * instances where we just want to skip a block and the formatter is 
         * responsible for sending out alerts or logging issues.
         */
        if (contentLength != ContentSerializer.SKIP_BLOCK) {
	        formatter.writeDefaultHeaders(writer, contentLength);
	        formatter.writeExtendedHeaders(writer);
	        
	        // separator between headers and contents
	        writer.writeSeparator();
	
	        formatter.serialize(offset, writer);
	      
	        // block separator
	        writer.writeSeparator();
        }
    }

    /**
     * read a block from the specified stream
     * 
     * @param reader StreamReader to read a block from.
     * @throws SerializationException
     */
    public final void readBlock(StreamReader reader)
            throws SerializationException, ArchiveException, OAException, IOException {
        // save the current offset for the beginning of this block
        long offset = reader.getOffset();
       
        // from the content type we can instantiate directly the correct 
        // content block parser
        Map headers = reader.readHeaders();
        String contentType = (String) headers.get(Constants.CONTENT_TYPE_HEADER);

        // Detect end of stream marker and set the reader to end of 
        // stream and return back to the restore loop.
        if (contentType.equals(ContentBlockParser.END_OF_STREAM_BLOCK)) {
            reader.setEndOfStream();
            return;
        }

        ContentBlockParser formatter = ContentBlockParser.getBlockParser(contentType, _session);
        formatter.deserialize(offset, reader, headers);
        
        // block separator
        reader.readSeparator();
    }
}
