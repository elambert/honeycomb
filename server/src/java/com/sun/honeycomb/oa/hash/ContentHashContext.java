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



package com.sun.honeycomb.oa.hash;

import java.nio.ByteBuffer;

import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;

import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.coordinator.Disposable;

public class ContentHashContext implements Codable, Disposable {
    public int hashAlgorithm;
    public ByteBuffer hashContext = null;
    private boolean disposed = false;
   
    public ContentHashContext() {
    }

    public ContentHashContext(int hashAlgorithm, ByteBuffer hashContext) {
        disposed = false;
        
        this.hashAlgorithm = hashAlgorithm;
        
        // We make a copy of what we get in case caller checks in theirs
        this.hashContext = 
            ByteBufferPool.getInstance().checkOutDuplicate(hashContext);
    }

    public void encode(Encoder encoder) {
        if(disposed) {
            return;
        }
        encoder.encodeInt(hashAlgorithm);
        encoder.encodeBuffer(hashContext);
    }
    
    public void decode(Decoder decoder) {
        hashAlgorithm = decoder.decodeInt();
        hashContext = decoder.decodeBuffer(true);
    }
    
    public void dispose() {
        ByteBufferPool.getInstance().checkInBuffer(hashContext);
        hashContext = null;
        disposed = true;
    }
}
