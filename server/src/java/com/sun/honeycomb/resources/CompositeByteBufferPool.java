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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class CompositeByteBufferPool extends ByteBufferPool {

    private FlatByteBufferPool[] pools;

    // sizes is assumed to be sorted
    CompositeByteBufferPool(int[] sizes) {
        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("must specify at least one size");
        }

        pools = new FlatByteBufferPool[sizes.length];

        for (int i = 0; i < sizes.length; i++) {
            pools[i] = new FlatByteBufferPool(sizes[i]);
        }
    }

    public ByteBuffer checkOutBuffer(int capacity) {
        return poolForCapacity(capacity).checkOutBuffer(capacity);
    }

    public ByteBuffer checkOutDuplicate(ByteBuffer buffer) {
        return poolForCapacity(buffer.capacity()).checkOutDuplicate(buffer);
    }

    public ByteBuffer checkOutReadOnlyBuffer(ByteBuffer buffer) {
        return poolForCapacity(buffer.capacity()).checkOutReadOnlyBuffer(buffer);
    }

    public ByteBuffer checkOutSlice(ByteBuffer buffer) {
        return poolForCapacity(buffer.capacity()).checkOutSlice(buffer);
    }

    public void checkInBuffer(ByteBuffer buffer) {
        FlatByteBufferPool.sharedCheckInBuffer(buffer);
    }

    private FlatByteBufferPool poolForCapacity(int capacity) {
        for (int i = 0; i < pools.length; i++) {
            if (pools[i].getBufferSize() >= capacity) {
                return pools[i];
            }
        }

        throw new IllegalArgumentException("requested capacity " +
                                           capacity +
                                           " exceeds maximum " +
                                           pools[pools.length - 1].getBufferSize());
    }
}
