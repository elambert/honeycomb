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

import java.nio.channels.Channel;
import java.nio.ByteBuffer;
import java.io.IOException;

public class HCRangeByteChannel  {
    protected int _bufsize = 10*1024; //HoneycombTestConstants.MAX_ALLOCATE;

    protected long _size = 0;
    public long getSize() {return _size;}

    protected long _bytesMoved = 0; // bytes read or written so far
    protected int _bytesToMove = 0; // bytes to read or write in this call
    protected boolean _open = true;
    protected byte[] _bytes;
    protected long _timestart = 0; //for stats
    protected long _totalcalls=0;

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
	protected  HCRangeByteChannel() {
		_bytes = new byte[_bufsize];
	}

    public boolean isOpen() {
        return (_open);
    }

    public void close() {
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

        return ("elapsedtime=" + timetotal);
    }

}
