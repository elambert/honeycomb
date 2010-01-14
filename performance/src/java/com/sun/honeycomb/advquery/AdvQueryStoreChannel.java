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
import java.nio.channels.ReadableByteChannel;
import java.nio.ByteBuffer;

import com.sun.honeycomb.test.util.RandomUtil;

public class AdvQueryStoreChannel extends StoreChannel {
    public static final int MAX_ALLOCATED_BYTES = 4*1024;
    
    private long sizeBytes = 1;
    private int patternSize = 1;
    private long bytesRead = 0;
    private boolean isClosed;
    
    private byte[] randomBytesPattern = null;
    private AdvQueryRandomUtil prng = null;

    
    public AdvQueryStoreChannel(AdvQueryRandomUtil prng, long sizeBytes, int patternSize) {
        if (sizeBytes == 0) {
            sizeBytes = 1;
        }
        this.prng = prng;
        patternSize = (patternSize <= MAX_ALLOCATED_BYTES) ? patternSize : MAX_ALLOCATED_BYTES;
        reset(sizeBytes, patternSize);
    }
    
    public void reset(long sizeBytes, int patternSize) {
        this.sizeBytes=sizeBytes;
        if (patternSize > sizeBytes) {
            patternSize = (int) sizeBytes;
        }
        this.patternSize = patternSize;
        bytesRead=0;
        isClosed=false;
        
        
        randomBytesPattern = AdvQueryChannelBytes.getRandomBytes(patternSize, prng);
    }
    
    public int read(ByteBuffer dst) throws IOException {
        if (bytesRead >= sizeBytes) {
            close();
            return -1;
        }
        
        int nextByte = (int) (bytesRead % ((long) randomBytesPattern.length));
        
        int bytesToRead = (int) Math.min(sizeBytes - bytesRead, (long) (randomBytesPattern.length - nextByte));
        bytesToRead = Math.min(bytesToRead, dst.remaining());
        
        dst.put(randomBytesPattern, nextByte, bytesToRead);
        
        bytesRead += bytesToRead;
        
        return bytesToRead;
    }
    
    public void close() {
        isClosed = true;
    }
    
    public boolean isOpen() {
        return !isClosed;
    }
}
