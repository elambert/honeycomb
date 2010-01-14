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

import com.sun.honeycomb.oa.bulk.stream.StreamReader;
import com.sun.honeycomb.oa.bulk.stream.Constants;
import com.sun.honeycomb.emd.common.SysCacheException;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Logger;
import java.util.Map;

abstract class BaseRestoreSession extends Session {
        
    protected static Logger LOG = Logger.getLogger(RestoreSession.class.getName());
    private StreamReader _reader = null;

    BaseRestoreSession(ReadableByteChannel channel, 
                       String streamFormat, 
                       Callback callback,
                       int options) throws SessionException {
        super(streamFormat, callback, options);
        _reader = new StreamReader(channel);
    }
   
    public void startSession() throws Exception{
        // read Stream Headers
        setHeaders(_reader.readHeaders());
        String streamFormat = getHeader(Constants.CONTENT_DESCRIPTION_HEADER);
        if (streamFormat == null)
            throw new SerializationException(Constants.CONTENT_DESCRIPTION_HEADER + " header not found in backup stream.");
        if (!streamFormat.equals(getStreamFormat()))
            throw new SerializationException("Unexpected stream format, expected '" + 
                                             getStreamFormat() + 
                                             "' got: '" +  streamFormat + "'");
        String backupDate = getHeader(Constants.CREATION_TIME);
        // Backward compatibility
        if (backupDate != null)
            setCreationDate(Constants.DATE_FORMAT.parse(backupDate).getTime());
        if (!confirmStateBeforeRestore()){
            return;
        }

        // continue reading until no blocks are available
        while (!_reader.isEndOfStream()) {
            _streamParser.readBlock(_reader);
            checkAbort();
        }
            
        confirmStateAfterRestore();
        CallbackObject cb = new CallbackObject(null, CallbackObject.SESSION_COMPLETED);
        cb.setStreamOffset(_reader.getOffset());
        callback(cb);
    }

    public long getBytesProcessed(){
        return _reader.getOffset();
    }
        
    // Put these in subclass so that emulator can fake it
    abstract boolean confirmStateBeforeRestore() throws SysCacheException;
    abstract boolean confirmStateAfterRestore() throws SysCacheException;
    abstract void setCorruptedState();
}
