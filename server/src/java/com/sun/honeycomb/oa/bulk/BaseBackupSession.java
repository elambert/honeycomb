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



package com.sun.honeycomb.oa.bulk;

import java.nio.channels.WritableByteChannel;
import java.util.logging.Logger;
import java.util.Date;
import java.io.IOException;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.oa.bulk.stream.Constants;
import com.sun.honeycomb.oa.bulk.stream.StreamWriter;
import com.sun.honeycomb.oa.bulk.stream.ContentBlockParser;
 
public abstract class BaseBackupSession extends Session {
    
    protected static Logger LOG = Logger.getLogger(BackupSession.class.getName());
   
    StreamWriter _writer = null;
    private OIDIterator _oids = null;
    public final long _start;
    public final long _end;
    
    /**
     * 
     * @param start
     * @param end
     * @param channel
     * @param streamFormat
     * @param callback
     * @param sessionOptions
     */
    public BaseBackupSession(long start,
                             long end,
                             WritableByteChannel channel, 
                             String streamFormat, 
                             Callback callback,
                             int options) throws SessionException{
        super(streamFormat, callback, options);
        // perform check, instantiate Iterator
        boolean force = ((options & Session.FORCE_BACKUP) != 0);
        try {
            _oids = new SysCacheOIDIterator (start, end, force);
        } catch(ArchiveException ae){
            throw new ReportableException("Unable to query from " + new Date(start) + " to " + new Date(end), ae);
        }
        _start = start;
        _end = end;
        _writer = new StreamWriter(channel);
    }


    public void startSession() throws Exception{
        // write stream headers 
        _streamParser.writeHeaders(_writer);
           
        // backup cluster config
        if (optionChosen(CLUSTER_CONFIG_BACKUP_OPTION)) {
            _streamParser.writeBlock(ContentBlockParser.CLCONF_BLOCK_PARSER,
                                     _writer, null);
        }

        // backup cluster schema
        if (optionChosen(SCHEMA_BACKUP_OPTION)) {
            _streamParser.writeBlock(ContentBlockParser.SCHEMA_BLOCK_PARSER, 
                                     _writer,null);
        }

        // backup silo config 
        if (optionChosen(SILO_CONFIG_BACKUP_OPTION)) {
            _streamParser.writeBlock(ContentBlockParser.SILOCONF_BLOCK_PARSER, 
                                     _writer,null);
        }

        // DO NOT change the order of this ever! The reason we 
        // backup config first is so that we can then setup the 
        // system cache state machine correctly when we put the 
        // system cache in place and not have any of the above 
        // config updates change to the wrong state.
            
        // backup system cache
        if (optionChosen(SYSCACHE_BACKUP_OPTION)) {
            _streamParser.writeBlock(ContentBlockParser.SYSCACHE_BLOCK_PARSER,
                                     _writer, null);
        }
           
        // backup cluster objects
        if (optionChosen(OBJECT_BACKUP_OPTION)) {
            long start = System.currentTimeMillis();
            long offset = _writer.getOffset();

            while (_oids.hasNext()) {
                NewObjectIdentifier oid = _oids.next();
                _streamParser.writeBlock(ContentBlockParser.OBJECT_BLOCK_PARSER,
                                         _writer, oid);
                checkAbort();
                checkPendingChanges();
            }

            float delta = (float) ((System.currentTimeMillis() - start) / 1000.0);
            long bytes = _writer.getOffset() - offset;
            LOG.info("Backed up " + bytes + " in " + delta + " seconds, " + (bytes / 1000000.0) / delta + " MBps");

        }
        _writer.writeHeader(Constants.CONTENT_TYPE_HEADER,
                            ContentBlockParser.END_OF_STREAM_BLOCK);
        _writer.writeHeader(Constants.CONTENT_LENGTH_HEADER, "0");
        _writer.writeSeparator();

        CallbackObject cb = new CallbackObject(null, CallbackObject.SESSION_COMPLETED);
        cb.setStreamOffset(_writer.getOffset());
        callback(cb);
    }

    public long getBytesProcessed(){
        return _writer.getOffset();
    }

    abstract void checkPendingChanges()  throws SerializationException, OAException, ArchiveException, IOException;
    abstract boolean checkSysCacheState(long timestamp)  throws SerializationException;
}
