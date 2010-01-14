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



package com.sun.honeycomb.resources;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.io.IOException;

public class ByteBufferListInputStream
    extends InputStream {

    private ByteBufferList buffers;
    private ByteBuffer[] bufferArray;
    private int lastValidBuffer;

    private ByteBufferListInputStream() {
    }

    public ByteBufferListInputStream(ByteBufferList nBuffers) {
        buffers = nBuffers;
        bufferArray = buffers.getBuffers();
        lastValidBuffer = 0;
    }

    private void jumpOverEmptyBuffers() {
        for (; lastValidBuffer<bufferArray.length; lastValidBuffer++) {
            if (bufferArray[lastValidBuffer].remaining() > 0) {
                break;
            }
        }
    }

    public int read()
        throws IOException {
        if (lastValidBuffer == bufferArray.length) {
            return(-1);
        }
        
        int result = bufferArray[lastValidBuffer].get();
        if (bufferArray[lastValidBuffer].remaining() == 0) {
            jumpOverEmptyBuffers();
        }

        return(result);
    }

    public int available()
        throws IOException {
        int result = 0;
            
        for (int i = 0; i < bufferArray.length; i++) {
            result += bufferArray[i].remaining();
        }

        return(result);
    }
    
    public boolean markSupported() {
        return(false);
    }
}
