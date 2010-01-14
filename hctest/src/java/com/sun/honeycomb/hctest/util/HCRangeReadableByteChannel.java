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



package com.sun.honeycomb.hctest.util;

import java.nio.channels.ReadableByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Random;

public class HCRangeReadableByteChannel extends HCRangeByteChannel
    implements ReadableByteChannel {

    protected Random _random;
    private boolean _verbose;

    public HCRangeReadableByteChannel(long sizeBytes, boolean verbose) {
        super();
        _functionName = "read";
        _action = "write";  // for a read channel, we write to it        
        _size = sizeBytes;
        _verbose = verbose;
        // fill byte array with reproducible random data
        _random = new Random(2006);
        _random.nextBytes(_bytes);
    }

    /**
       Chunks out the same bytes array again and again.
       won't return more than the size of the array itself
       each call.
     */
    public int read(ByteBuffer dst) throws IOException {
        if (_totalcalls++ == 0) {
            _timestart = System.currentTimeMillis();
        }
        // we are done
        if (_verbose) {
            System.out.println("_size = " + _size + "   _bytesMoved = " +
                                _bytesMoved);
            System.out.println("_bytesMoved >= _size = " +
                                (_bytesMoved >= _size));
        }
        if (_bytesMoved >= _size) {
            if (_verbose) {
                System.out.println("HCRangeReadableByteChannel.read called after all bytes moved");
            }
            _open = false;
            return (-1);
        }

        // Calculate how many bytes to put for this call to read(dst)
        long _yetToMove = _size - _bytesMoved;
        int _offset = (int)(_bytesMoved  % _bytes.length);
        if ((_offset + _yetToMove) >= _bytes.length) {
            _bytesToMove = _bytes.length - _offset;
        } else {
            _bytesToMove = (int)_yetToMove;
        }
        if (dst.remaining() < _bytesToMove) {
            _bytesToMove = dst.remaining();
        }

        if (_verbose) {
			System.out.println("read: dst.capacity() = " + dst.capacity());
			System.out.println("read: dst.limit() = " + dst.limit());
			System.out.println("read: dst.position() = " + dst.position());
			System.out.println("read: dst.remaining() = " + dst.remaining());
			System.out.println("_bytesMoved = " + _bytesMoved);
			System.out.println("_bytesToMove = " + _bytesToMove);
        }

        dst.put(_bytes, _offset, _bytesToMove);
        _bytesMoved += _bytesToMove;

        if (_bytesMoved == _size) {
            if (_verbose) {
                System.out.println(
                    "HCRangeReadableByteChannel.read completed putting " + _size
                     + " bytes");
            }
        }
        
        return (_bytesToMove);
    }
    
    public String toString() {
        return ( "[size=" + _size + "; bytesMoved=" + _bytesMoved +
            "; bytesLength=" + _bytes.length + "; totalcalls=" + _totalcalls + 
            "; " + statString() + "]");
    }

    public String getFilename() {
        return "HCRangeReadableBytechannel have no filename. specs: " + toString();
    }
}
