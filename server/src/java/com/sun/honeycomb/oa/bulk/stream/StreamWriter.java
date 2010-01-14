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
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;


import com.sun.honeycomb.oa.bulk.SerializationException;

public class StreamWriter implements WritableByteChannel {  

    protected static Logger LOG = Logger.getLogger(StreamReader.class.getName());
    
    private WritableByteChannel _channel = null;
    private long _offset = 0;
   
    public StreamWriter(WritableByteChannel channel) {
        _channel = channel;
    }

    /**
     * 
     * @param headerName
     * @param value
     * @throws SerializationException
     */
    public void writeHeader(String headerName, String value ) throws SerializationException {
        writeLine(headerName + ": " + value);
    }
   
    /**
     * 
     * @param headerName
     * @param value
     * @throws SerializationException
     */
    public void writeHeader(String headerName, Object value ) throws SerializationException {
        writeLine(headerName + ": " + value.toString());
    }
   
    /**
     * 
     * @param headerName
     * @param value
     * @throws SerializationException
     */
    public void writeHeader(String headerName, long value ) throws SerializationException {
        writeLine(headerName + ": " + value);
    }
   
    /**
     * 
     * @param line
     * @throws SerializationException
     */
    public void writeLine(String line) throws SerializationException {
        try {
            byte[] bytes = (line + "\n").getBytes();
            ByteBuffer buff = ByteBuffer.wrap(bytes);

            int written =  _channel.write(buff);

            while (written < bytes.length) {
                written += _channel.write(buff);
            }
            _offset += bytes.length;
        } catch (IOException e) {
            throw new SerializationException("Error serializing line '" + 
                                             line + "'.", e);
        }
    }
   
    /**
     * 
     * @param value
     * @throws SerializationException
     */
    public void writeLine(long value) throws SerializationException {
        writeLine("" + value);
    }

    /**
     * 
     * @param value
     * @throws SerializationException
     */
    public void writeLongAsHex(long value) throws SerializationException {
        writeLine(Long.toHexString(value));
    }
    
    public void writeIntAsHex(int value) throws SerializationException {
        writeLine(Integer.toHexString(value));
    }
   
    public int write(ByteBuffer src) throws IOException {
        int n = _channel.write(src);
        _offset += n;
        return n;
    }

    // Convenience method to write all expected bytes
    void write(ByteBuffer dst, int len) throws IOException {
        int n = 0;
        while (n < len){
            int w = _channel.write(dst);
            if (w == 0)
                throw new IOException ("Expected blocking stream. Wrote " + n + 
                                       ", expected " + len +".");
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("wrote " + w + " bytes at offset " + n);
            n += w;
        }
        if (n > len)
            throw new IOException ("Failed to read expected block length. Read " + n + 
                                   ", expected " + len +".");
        _offset += n;
    }

   
    /**
     * 
     * @throws SerializationException
     */
    public void writeSeparator() throws SerializationException {
        writeLine(Constants.HEADER_TERMINATOR);
    }
    
    public void close() throws IOException {
        _channel.close();
    }

    public boolean isOpen() {
        return _channel.isOpen();
    }
   
    /**
     * 
     * @return
     */
    public long getOffset() {
        return _offset;
    }
}
