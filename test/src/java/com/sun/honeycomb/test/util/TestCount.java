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



package com.sun.honeycomb.test.util;

import com.sun.honeycomb.test.Result;
import java.util.ArrayList;

/**
 *  Counter of passed/failed/skipped tests
 */
public class TestCount {

    public int numTotal;
    public int numPass;
    public int numFail;
    public int numSkipped;

    public ArrayList failures; // list of Result objects for summary
    
    public TestCount() {
        numTotal = numPass = numFail = numSkipped = 0;
        failures = new ArrayList();
    }

    public void add(TestCount c) {
        numTotal += c.numTotal;
	numPass += c.numPass;
	numFail += c.numFail;
	numSkipped += c.numSkipped;
        failures.addAll(c.failures);
    }

    public void addFail(Result r) {
        // we need the failed object for summary, so append it
        failures.add(r);
        numFail++;
    }
 
    public int gotFailure() { return numFail; }
   
    public String toString() {
	return 
            "RESULTS=" + numTotal +
	    " PASS=" + numPass + 
	    " SKIPPED=" + numSkipped +
            " FAIL=" + numFail + " ";
    }
}

