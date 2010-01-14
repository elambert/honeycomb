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
import java.util.logging.Logger;
import java.nio.channels.ReadableByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.sun.honeycomb.common.TestRequestParameters;

public class HCTestReadableByteChannel extends HCTestByteChannel
    implements ReadableByteChannel {

    protected long seed;

    public HCTestReadableByteChannel(long sizeBytes)
        throws NoSuchAlgorithmException, HoneycombTestException {
        super(true);
        setup(sizeBytes,System.currentTimeMillis(),null,0,null);
    }
    public HCTestReadableByteChannel(long sizeBytes, boolean calcHash)
        throws NoSuchAlgorithmException, HoneycombTestException {
        super(calcHash);
        setup(sizeBytes,System.currentTimeMillis(),null,0,null);
    }

    public HCTestReadableByteChannel(byte[] bytes, int repeats)
        throws NoSuchAlgorithmException, HoneycombTestException {
        super(true);
        setup(0,0,bytes,repeats,null);
    }
    public HCTestReadableByteChannel(byte[] bytes, int repeats, boolean calcHash)
        throws NoSuchAlgorithmException, HoneycombTestException {
        super(calcHash);
        setup(0,0,bytes,repeats,null);
    }

    public HCTestReadableByteChannel(long sizearg, long seedarg, TestCase tc) 
        throws NoSuchAlgorithmException, HoneycombTestException {
        super(true);
        setup(sizearg,seedarg,null,0,tc);
    }
    public HCTestReadableByteChannel(long sizearg, long seedarg, TestCase tc, boolean calcHash) 
        throws NoSuchAlgorithmException, HoneycombTestException {
        super(calcHash);
        setup(sizearg,seedarg,null,0,tc);
    }


    private void setup(long sizearg, long seedarg, byte[] bytes,int repeats,
        TestCase tc) throws HoneycombTestException {
        _functionName = "read";
        _action = "write";  // for a read channel, we write to it        
        _tc = tc;

        if (bytes == null) {
            _size = sizearg;
            seed = seedarg;
            initializeBuffer();
        } else {
            _bufsize=bytes.length;
            if(repeats==0) {
                _size=bytes.length;
            } else {
                _size=bytes.length*repeats;
            }
            _bytes=bytes;
            _repeats=repeats;
        }

    }

    private void initializeBuffer() throws HoneycombTestException {
        RandomUtil.setSeed(seed); 
        if (_tc != null) {
           _tc.postMetric(new Metric("Seed", "seed", seed)); 
        }

        _bytes = RandomUtil.getRandomBytes(_bufsize);
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
        printPeriodicStatusMsg(dst);
        // we are done
        if (_bytesMoved >= _size) {
            printLastMsg();
            _open = false;
            return (-1);
        }

        // grab the minimum value so we know how many bytes are left to move
        if (dst.remaining() > _bytes.length)
            _bytesToMove=_bytes.length;
        else
            _bytesToMove=dst.remaining();

        if ((_bytesMoved + _bytesToMove) >= _size) {
            _bytesToMove = (int)(_size - _bytesMoved);
        }

        dst.put(_bytes, 0, _bytesToMove);
        // calculate the hash as we go
        if (calcHash  &&  TestRequestParameters.getCalcHash())
            _md.update(_bytes, 0, _bytesToMove);
        _bytesMoved += _bytesToMove;

        if (_bytesMoved == _size) {
            printFinalDataMoveMsg(_bytesToMove);
        }
        
        printEndLoopDebugStats(_bytesToMove, dst);
        return (_bytesToMove);
    }
    
    public String toString() {
        return ( "[size=" + _size + "; bytesMoved=" + _bytesMoved +
            "; bytesLength=" + _bytes.length + "; totalcalls=" + _totalcalls + 
            "; seed=" + seed +  "; repeats=" + _repeats +
            "; " + statString() + "]");
    }

	public String getFilename() {
		return "HCTestReadableBytechannel have no filename. specs: " + toString();
	}
}
