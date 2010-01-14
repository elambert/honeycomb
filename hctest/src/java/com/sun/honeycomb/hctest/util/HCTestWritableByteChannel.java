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

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.common.TestRequestParameters;

import java.util.logging.Logger;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HCTestWritableByteChannel extends HCTestByteChannel
    implements WritableByteChannel {

    public HCTestWritableByteChannel(long sizearg, TestCase tc)
        throws NoSuchAlgorithmException {
        super(true);
        _functionName = "write";
        _action = "read"; // for a write channel, we read from it
        _size = sizearg;
        _bytes = new byte[_bufsize];
        _tc = tc;
    }
    public HCTestWritableByteChannel(long sizearg, TestCase tc, boolean calcHash)
        throws NoSuchAlgorithmException {
        super(calcHash);
        _functionName = "write";
        _action = "read"; // for a write channel, we read from it
        _size = sizearg;
        _bytes = new byte[_bufsize];
        _tc = tc;
    }

    public int write(ByteBuffer src) throws IOException {
        if (_totalcalls++ == 0) {
            _timestart = System.currentTimeMillis();
        }
        printPeriodicStatusMsg(src);
        // we are done
        if (_bytesMoved >= _size) {
            int remaining = src.remaining();
            printLastMsg();
            Log.ERROR("remaining in buffer: " + remaining);
            System.out.println("PREV BYTES:\n" + new String(_bytes));
            if (remaining > 2048)
                remaining = 2048;
            _bytes = new byte[remaining];
            src.get(_bytes, 0, _bytes.length);
            System.out.println("BYTES" + 
                        (src.remaining() > 2048 ? " (truncated to 2048)" : "") +
                        ":\n" + new String(_bytes));
            _open = false;
            return (-1);
        }

        // grab the minimum value so we know how many bytes are left to move
        if(src.remaining() > _bytes.length)
            _bytesToMove=_bytes.length;
        else
            _bytesToMove=src.remaining();


        if ((_bytesMoved + _bytesToMove) >= _size) {
            _bytesToMove = (int)(_size - _bytesMoved);
        }

        src.get(_bytes, 0, _bytesToMove);
        // calculate the hash as we go
        if (calcHash  &&  TestRequestParameters.getCalcHash())
            _md.update(_bytes, 0, _bytesToMove);
        _bytesMoved += _bytesToMove;

        if (_bytesMoved == _size) {
            printFinalDataMoveMsg(_bytesToMove);
        }

        printEndLoopDebugStats(_bytesToMove, src);
        return (_bytesToMove);
    }

    public long getBytesMoved() {
        return _bytesMoved;
    }
}
