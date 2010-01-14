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



package com.sun.honeycomb.test.stress;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;

public class RetrieveChannel implements WritableByteChannel
{
    public long sizeBytes;
    public int pattern;
    public boolean doContentVerification;
    public long seed;

    private byte [] bytes = null;

    private byte [] buf = null;
    private static int BUFSIZE = 4*1024;

    private long totalBytesWritten;
    private boolean isClosed;
    
    public RetrieveChannel() {
        this.sizeBytes = 0;
        this.pattern = ChannelPatterns.BINARY;
        this.seed = 0;
        this.buf = new byte[BUFSIZE];
        reset();
    }

    public void reset() {
        totalBytesWritten=0;
        isClosed=false;
        switch (pattern) {
          case ChannelPatterns.BINARY:
            bytes = BinaryBytes.bytes;
            break;
          case ChannelPatterns.DEADBEEF:
            bytes = DeadBeefBytes.bytes;
            break;
        }
    }

    public int write(ByteBuffer src) throws IOException 
    {
        int bytesWritten = 0;

        int errcnt = 0;
        StringBuffer errstring = new StringBuffer();

        while (src.remaining() > 0) {
            int remaining = src.remaining();
            int bytesToWrite = Math.min(remaining, BUFSIZE);
            
            src.get(buf, 0, bytesToWrite);
            
            if (doContentVerification) {
                for (int i = 0; i < bytesToWrite; i++) {
                    int bytes_i = (int) ((seed + totalBytesWritten + bytesWritten + i) % ((long) bytes.length));

                    // Detect corruption.  We used to throw immediately,
                    // but this meant it was difficult to see the pattern
                    // and size of the corruption.  Now we note down the
                    // corruption we see, in hopes 
                    // that this info will enable us to understand the
                    // corruption better and then we throw the exception.
                    // Three types of corruption are detected:
                    //    i. too much data returned
                    //   ii. incorrect data returned
                    //  iii. too little data returned
                    if (totalBytesWritten+i > sizeBytes) {
                        // too much data
                        errcnt++;
                        String errline = 
                            errcnt + ":"
                            + " extra byte [" 
                            + "totalBytesWritten(" + totalBytesWritten + i + ")"
                            + " extra byte(" + buf[i] + ")"
                            + " extra char(" + ((char) buf[i]) + ")"
                            + "\n";
                        System.err.print(errline);
                        errstring.append(errline);
                    } else if (buf[i] != bytes[bytes_i]) {
                        errcnt++;
                        String errline = errcnt + ": byte [" + (bytesWritten + i) +
                            " (bytesWritten=" + bytesWritten + " + i=" + i + ")" +
                            "] was '" + buf[i] + "', not '" + 
                            bytes[bytes_i] + "', bytes_i=" + bytes_i +
                            ", got char '" + ((char) buf[i]) +
                            "' not char '" + ((char) bytes[bytes_i]) +
                            "'\n";
                        System.err.print(errline);
                        errstring.append(errline);
                    }
                }
            }
                
            bytesWritten += bytesToWrite;
        }

        totalBytesWritten += bytesWritten;
        
        if (bytesWritten == 0 && totalBytesWritten < sizeBytes) {
            errcnt++;
            String errline = 
                errcnt + ":"
                + " not enough data ["
                + "totalBytesWritten(" + totalBytesWritten + ")"
                + ", sizeBytes(" + sizeBytes + ")"
                + "]"
                + "\n";
            System.err.print(errline);
            errstring.append(errline);
        }
        
        if (errcnt > 0) {
            throw new IOException("Read verification failed." +
                                  " bytesWritten=" + bytesWritten +
                                  ", totalBytesWritten=" + totalBytesWritten +
                                  ", sizeBytes=" + sizeBytes +
                                  ", isClosed=" + isClosed +
                                  ", bytes.length=" + bytes.length +
                                  ", errcnt=" + errcnt +
                                  ", errstring=" + errstring);
        }
        
        return bytesWritten;
    }
    
    public void close()
    {
        isClosed = true;
    }

    public boolean isOpen()
    {
        return !isClosed;
    }
}
