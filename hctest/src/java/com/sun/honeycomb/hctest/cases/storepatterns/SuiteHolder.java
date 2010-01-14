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

     
package com.sun.honeycomb.hctest.cases.storepatterns;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.rmi.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;


/**
   Suite holder - we're goig to be using localsuite or remote suite.
   This is the only way I can think of to seperate the test invocation
   from the utility functions (store, fetch, and the like)
 
*/

public class SuiteHolder {

    private static SuiteHolder _instance;

    private HoneycombSuite _suite=null;        

    synchronized public static void createHolder(HoneycombSuite suite) 
        throws HoneycombTestException
    {
        if (_instance != null) {
            throw new HoneycombTestException("Suiteholder already has a suite, don't set up another one.");
        }
        _instance = new SuiteHolder(suite);        
    }

    public static Suite getSuite() 
        throws HoneycombTestException {
        if (_instance == null) {
            throw new HoneycombTestException("Can't access the suite object before setting it.");
            
        }
        return _instance._suite;
    }
    

    /**

     */
    private SuiteHolder(HoneycombSuite suite) {
        _suite=suite;
        
    }
   
}
