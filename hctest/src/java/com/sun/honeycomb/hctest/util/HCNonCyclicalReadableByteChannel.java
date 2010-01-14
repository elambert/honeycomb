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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import com.sun.honeycomb.test.Metric;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RandomUtil;

public class HCNonCyclicalReadableByteChannel extends HCTestReadableByteChannel {

    private long _seedInc = 0;
    private boolean _cycleSeed = false;
    
	public HCNonCyclicalReadableByteChannel(long sizeBytes)
			throws NoSuchAlgorithmException, HoneycombTestException {
		super(sizeBytes);
	}

	public HCNonCyclicalReadableByteChannel(byte[] bytes, int repeats)
			throws NoSuchAlgorithmException, HoneycombTestException {
		super(bytes, repeats);
	}

	public HCNonCyclicalReadableByteChannel(long sizearg, long seedarg,
			TestCase tc) throws NoSuchAlgorithmException,
			HoneycombTestException {
		super(sizearg, seedarg, tc);
	}


    public HCNonCyclicalReadableByteChannel(long sizearg, long seedarg, TestCase tc, boolean cycleSeed) 
    throws NoSuchAlgorithmException, HoneycombTestException {
    	super(sizearg, seedarg, tc);
	    this._cycleSeed = cycleSeed;
    }
    
    private void initializeBuffer() throws HoneycombTestException {
        RandomUtil.setSeed(seed + _seedInc); 
        if (_tc != null) {
           _tc.postMetric(new Metric("Seed", "seed", seed + _seedInc)); 
        }

        _bytes = RandomUtil.getRandomBytes(_bufsize);
    }
    
    public int read(ByteBuffer dst) throws IOException {
    	int res = super.read(dst);
    	
    	if (_cycleSeed && _bytesMoved >= _bytes.length){
        	// Moved a full pattern block :)
        	// Time to renew these random bytes! 
        	_seedInc++;
        	try {
    			initializeBuffer();
    		} catch (HoneycombTestException e) {
    			Log.WARN("Initializing buffer failed: " + e.getMessage());
    		}
        }
    	
    	return res;
    }

    
    public String toString() {
        return ( "[size=" + _size + "; seed=" + seed +
            "; repeats=" + _repeats + "; cycleSeed=" + _cycleSeed + "; " + statString() + "]");
    }
}
