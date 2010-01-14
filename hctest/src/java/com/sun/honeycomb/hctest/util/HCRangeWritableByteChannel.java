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

import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class HCRangeWritableByteChannel extends HCRangeByteChannel
    implements WritableByteChannel {

    private Random _random;
    private int _bytesStoredPosition;
    private long _offset;
    private byte[] _bytesStored;
    private boolean pass = true;
    private byte[] storedBytes;
    private byte[] retrievedBytes;
    private boolean _verbose;

    public HCRangeWritableByteChannel(long firstByte, long lastByte,
                                      boolean verbose) {
        super();
        _functionName = "write";
        _action = "read"; // for a write channel, we read from it
        _size = lastByte - firstByte + 1;
        _offset = firstByte;
        _verbose = verbose;

        // fill byte array with the same random data that was stored
        _random = new Random(2006);
        _bytesStored = new byte[_bufsize];
        _random.nextBytes(_bytesStored);
        if (_verbose) {
            System.out.println("_bytesStored[0]  = " + _bytesStored[0]);
        }
    }

    public int write(ByteBuffer src) throws IOException {
        if (_totalcalls++ == 0) {
            _timestart = System.currentTimeMillis();
        }
        // we are done
        if (_bytesMoved >= _size) {
            if (_verbose) {
                System.out.println(
                "HCRangeWritableByteChannel.write called after all bytes written");
            }
            _open = false;
            return (-1);
        }

        // grab the minimum value so we know how many bytes are left to move

        if (_verbose) {
            System.out.println("write: src.capacity() = " + src.capacity());
            System.out.println("write: src.limit() = " + src.limit());
            System.out.println("write: src.position() = " + src.position());
            System.out.println("write: src.remaining() = " + src.remaining());
        }

        if(src.remaining() > _bytes.length)
            _bytesToMove=_bytes.length;
        else
            _bytesToMove=src.remaining();

        if ((_bytesMoved + _bytesToMove) >= _size) {
            _bytesToMove = (int)(_size - _bytesMoved);
        }

        src.get(_bytes, 0, _bytesToMove);
        if (_verbose) {
            System.out.println("After src.get _bytes[0]  = " + _bytes[0]);
        }

        // compare what was previously stored with what was retrieved now

        // set up retrievedBytes to the desired size
        if (_bytesToMove == _bytes.length) {
            retrievedBytes = _bytes;
        } else {
            retrievedBytes = new byte[_bytesToMove];
            System.arraycopy(_bytes, 0, retrievedBytes, 0, _bytesToMove);
        }

        // set up storedBytes in correct sequence based on offset
        storedBytes = new byte[_bytesToMove];
        _bytesStoredPosition = (int)((_offset + _bytesMoved) % _bufsize);
        if ((_bytesStoredPosition + _bytesToMove) > _bytes.length) {
            // Copy original stored bytes in two sections
            // 1) From _bytesStoredPosition to end of _bytesStored into start
            //    of storedBytes
            // 2) From start of _bytesStored to after the first section
            int endLength = _bytes.length - _bytesStoredPosition;
            System.arraycopy(_bytesStored, _bytesStoredPosition, storedBytes,
                             0, endLength);
            System.arraycopy(_bytesStored, 0, storedBytes, endLength,
                             _bytesToMove-endLength);
        } else { 
            System.arraycopy(_bytesStored, _bytesStoredPosition, storedBytes, 0,
                             _bytesToMove);
        }

        // Ready for compare
        boolean same = Arrays.equals(retrievedBytes, storedBytes);
        if ((pass == true) && (same == false)) {
            pass = false;
            // Print where the incorrect data begins
            System.out.println("The offset after firstByte at which data differ:");
            for (int i = 0 ; i < retrievedBytes.length ; i++) {
                if (retrievedBytes[i] != storedBytes[i]) {
                    System.out.println("  retrievedBytes[" + i + "] = " +
                        retrievedBytes[i] + ",  storedBytes[" + i + "] = " +
                        storedBytes[i]);
                    break;
                }
            }
        }

        _bytesMoved += _bytesToMove;

        if (_verbose && _bytesMoved == _size) {
            System.out.println("HCRangeWritableByteChannel.write completed getting " + _size + " bytes.");
        }

        return (_bytesToMove);
    }

    public long getBytesMoved() {
        return _bytesMoved;
    }

    public boolean getPass() {
        return pass;
    }
}
