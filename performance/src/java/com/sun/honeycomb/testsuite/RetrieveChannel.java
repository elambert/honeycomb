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



import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;

public class RetrieveChannel implements WritableByteChannel
{
    private long sizeBytes;
    private long bytesWritten;
    private boolean isClosed;

    private byte [] buf;

    public RetrieveChannel() {
        this(0);
    }

    public RetrieveChannel(long sizeBytes) {
        buf = new byte[ChannelBytes.bytes.length];
        reset(sizeBytes);
    }

    public void reset(long sizeBytes) {
        this.sizeBytes=sizeBytes;
        bytesWritten=0;
        isClosed=false;
    }

    public int write(ByteBuffer src) throws IOException 
    {
        int bytesToWrite = Math.min(src.remaining(), buf.length);
        
        src.get(buf, 0, bytesToWrite);

	/* Comment out read verification 
	   for (int i = 0; i < bytesToWrite; i++) {
	   int bytes_i = (int) ((bytesWritten + i) % ((long) ChannelBytes.bytes.length));
	   if (buf[i] != ChannelBytes.bytes[bytes_i]) {
	   throw new IOException("Read verification failed.  byte(" + Long.toString(bytesWritten + i) + ").  '" + buf[i] + "' should be '" + ChannelBytes.bytes[bytes_i] + "'");
	   }
	   }
	*/
        
        bytesWritten += bytesToWrite;

        if (bytesWritten > sizeBytes) {
            throw new IOException("Too many bytes retrieved.  sizeBytes(" + Long.toString(sizeBytes) + ") bytesRetrieved(" + Long.toString(bytesWritten) + ")");
        }

        return bytesToWrite;
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
