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
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.oa.bulk.SerializationException;

public class StreamReader implements ReadableByteChannel {

    protected static Logger LOG = Logger.getLogger(StreamReader.class.getName());
    private ReadableByteChannel _channel = null;
    
    // internal variable used to store the buffering of reading and test for 
    // channel availalibity
    private ByteBuffer _buff = null;
    private long _offset = 0;
    
    private boolean _endOfStream = false;
 
    public StreamReader(ReadableByteChannel channel) {
        _channel = channel;
        
        // we are only reading byte by byte on readlines ... the 
        // read(ByteBuffer dst) still reads big buffers for good 
        // performance
        _buff = ByteBuffer.allocate(1);
    }

    public int read(ByteBuffer dst) throws IOException {
        int n = _channel.read(dst);
        _offset += n;
        return n;
    }

    // Convenience method to read all expected bytes
    void read(ByteBuffer dst, int len) throws IOException {
        int n = 0;
        dst.limit(len);
        while (n < len){
            int r = _channel.read(dst);
            if (r == 0)
                throw new IOException ("Expected blocking stream. Read " + n + 
                                       ". Expected " + len +". " + _channel);
            else if (r < 0)
                throw new IOException ("Failed to read expected block length. Read " + n + 
                                       ", expected " + len +".");
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("read " + r + " bytes at offset " + n);
            n += r;
        }
        if (n > len)
            throw new IOException ("Failed to read expected block length. Read " + n + ". Expected " + len +".");
        _offset += n;
    }
    
    /**
     * 
     * @throws SerializationException
     */
    public void readSeparator() throws SerializationException {
        String line;

        try {
            line = readLine();
        } catch (IOException e) {
            throw new SerializationException(
                    "Unable to read header terminator", e);
        }

        if (!line.equals(""))
            throw new SerializationException("Header not found at offset " + _offset + ". Found '" + line
                    + "' instead.");
    }

    /**
     * 
     * @return Map of name/value pairs
     * @throws SerializationException
     */
    public Map readHeaders() throws SerializationException {
        HashMap headers = new HashMap();
        //System.out.println("Reading headers");
        try{
            String line = readLine();
            while (!line.equals("")){
                int i = line.indexOf(":");
                if (i == -1 || i == 0)
                    throw new SerializationException("Malformed header " + line);
                headers.put(line.substring(0, i), line.substring(i + 2));
                //System.out.println("HEADER: " + line.substring(0, i) + " \"" + line.substring(i + 2) + "\"");
                line = readLine();
            }
            return headers;
        } catch (IOException e) {
            throw new SerializationException("Unabled to read headers at offset " + _offset, e);
        }
    }
  
    /**
     * 
     * @return
     * @throws IOException
     */
    private int read() throws IOException {
        int n = _channel.read(_buff);
        _offset += n;
        return _buff.position();
    }

    /**
     * 
     * @return
     * @throws IOException
     */
    public String readLine() throws IOException {         
        StringBuffer result = new StringBuffer();
        int read = -1;
        char readChar = 0;
     
        // try reading from buffer 
        read = read();
        readChar = (char)_buff.get(0);
        _offset++;
        
        while ((read > 0) && (readChar != '\n')) {
            result.append(readChar);
            _buff.clear();
            read = _channel.read(_buff);
            readChar = (char)_buff.get(0);
            _offset++;
        }
        
        _buff.clear();
      
        if (read == -1 )
           return null;
            
        return result.toString();
    }
  
    /**
     * 
     * @return
     * @throws IOException
     */
    public long readLineAsLongHex() throws IOException {
        return Long.parseLong(readLine(),16);
    }
    
    public int readLineAsIntHex() throws IOException {
        return Integer.parseInt(readLine(),16);
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
     * @throws IOException
     */
    public boolean isEndOfStream() throws IOException {
        return _endOfStream;
    }
    
    public void setEndOfStream() {
        _endOfStream = true;
    }
    
    public long getOffset() {
        return _offset;
    }
}
