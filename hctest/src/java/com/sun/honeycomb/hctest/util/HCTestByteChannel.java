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
import java.nio.channels.Channel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HCTestByteChannel  {
    protected static final Logger LOG =
                Logger.getLogger(HCTestByteChannel.class.getName());

    protected int _bufsize = 10*1024; //HoneycombTestConstants.MAX_ALLOCATE;

    protected long _size = 0;
    public long getSize() {return _size;}

    protected long _bytesMoved = 0; // bytes read or written so far
    protected int _bytesToMove = 0; // bytes to read or write in this call
    protected boolean _open = true;
    protected byte[] _bytes;
    protected TestCase _tc = null; // for metrics
    protected long _timestart = 0; //for stats
    protected long _totalcalls=0;
    protected MessageDigest _md = null; // for hash
    protected boolean calcHash = true;
    protected String _hash = null;
    protected int _repeats;

    //
    // Diagnostics
    //
    static final int LOG_INCREMENT=50000; // Log every X calls.
    static final int METRIC_INCREMENT=1000000; // Log every X calls.
    static final boolean REALLY_CHATTY=true;
    protected String _functionName = ""; // name of the function that is called
    protected String _action = ""; // action that the function does to the data

    //
    // Functions
    //
    protected  HCTestByteChannel(boolean calcHash) 
        throws NoSuchAlgorithmException {
        this.calcHash = calcHash;
        _bytes = new byte[_bufsize];
        if (calcHash  &&  TestRequestParameters.getCalcHash()) 
            _md = MessageDigest.getInstance(HoneycombTestConstants.CURRENT_HASH_ALG);
    }
    
    public boolean isOpen() {
        return (_open);
    }

    public void close() {
    }
    

    /**
     * Return the hash.  Note that this can only be called once.
     * Subsequent calls reset the hash.  MessageDigest doc says:
     * Completes the hash computation by performing final operations 
     * such as padding.
     */
    public String computeHash() {
System.out.println("XXXXXXXXXXXXXX calcHash=" + calcHash
+ "TestRequestParameters.getCalcHash()=" + TestRequestParameters.getCalcHash());
        if (calcHash  &&  TestRequestParameters.getCalcHash()) 
            _hash = HCUtil.convertHashBytesToString(_md.digest());
        else
            _hash = "hash disabled";
        return _hash;
    }

    /**
     * Returns the last computed hash, or null if it hasn't been computed.
     */
    public String getHash() {
        return _hash;
    }


    /**
      Diagnostics
    */
    public void printPeriodicStatusMsg(ByteBuffer b) {
        if (_totalcalls % METRIC_INCREMENT == 0 && _tc != null) {
            _tc.postMetricGroup(getCurrentMetrics());
        }

        if (_totalcalls % LOG_INCREMENT == 0) {
            LOG.info(_functionName + " called on " + toString() +
                ", buf is " + b);

            // Check if buffer is too small
            if (b.limit() > _bufsize) {
                LOG.info("Maybe we should increase bufsize; remaining is " +
                    b.remaining() + "; bufsize is " + _bufsize);
            }
        } else if(true==REALLY_CHATTY){
            LOG.fine(_functionName + " called on " + toString() +
                ", buf is " + b);
        }
    }

    public void printFinalDataMoveMsg(long b) {
        LOG.fine("last " + _action + ": " + _action + " only " + b +
            "; " + toString());
        if (_tc != null) {
            _tc.postMetricGroup(getCurrentMetrics());
        }
    }

    public void printLastMsg() {
        LOG.fine("no more bytes to " + _action + " -- total calls " + 
                 _totalcalls + " total bytes " + _bytesMoved);
    }
    
    public void printEndLoopDebugStats(long b, ByteBuffer bb) {
        LOG.fine("end of loop: size of buf is " + bb.limit() +
            "; remaining is " + bb.remaining() + "; bytesMoved this call is " +
            b + "; " + _action + " bytes is " + _bytesMoved);
    }

    public Metric[] getCurrentMetrics() {
        long timetotal = 0;
        if (_timestart != 0) {
            timetotal = System.currentTimeMillis() - _timestart;
        }

        Metric metrics[] = new Metric[6];
        metrics[0] = new Metric("TotalCalls", "calls", _totalcalls);
        metrics[1] = new Metric("BytesMoved", "bytes", _bytesMoved);
        metrics[2] = new Metric("BytesRemaining", "bytes", _size - _bytesMoved);
        metrics[3] = new Metric("RateMegabytesPerSecond", "MB/sec",
            HCUtil.megabytesPerSecond(timetotal, _bytesMoved));
        metrics[4] = new Metric("RateGigabytesPerSecond", "GB/sec",
            HCUtil.gigsPerDay(timetotal, _bytesMoved));
        metrics[5] = new Metric("ElapsedTime", "milliseconds", timetotal);

        return (metrics);
    }

    public String toString() {
        return ( "[size=" + _size + "; bytesMoved=" + _bytesMoved +
            "; bytesLength=" + _bytes.length + "; totalcalls=" + _totalcalls +
            "; " + statString() + "]");
    }


    public String statString() {
        long timetotal = 0;
        
        if (_timestart != 0) {
            timetotal = System.currentTimeMillis() - _timestart;
        }

        return ("elapsedtime=" + timetotal + "; rate=" +
            HCUtil.megabytesPerSecond(timetotal, _bytesMoved) +
            " " + HCUtil.gigsPerDay(timetotal, _bytesMoved));
    }

}
