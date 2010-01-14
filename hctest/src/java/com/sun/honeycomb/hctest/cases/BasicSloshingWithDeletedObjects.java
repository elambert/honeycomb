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



package com.sun.honeycomb.hctest.cases;

import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.hctest.cli.DataDoctorState;
import com.sun.honeycomb.hctest.util.CheatNode;
import com.sun.honeycomb.hctest.util.ClusterMembership;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.SnapshotTool;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import java.util.Iterator;


public class BasicSloshingWithDeletedObjects extends BasicSloshing {

    public BasicSloshingWithDeletedObjects() {
        super();
    }
 
    public void doLoad() throws HoneycombTestException {
        /*
         * Default Stores are done with current client and using 10 processes
         * with 100% Stores with max data size of 1M and for 5m
         * 
         */
        TestRunner.setProperty("factory","ContinuousMixFactory");
        
        if (TestRunner.getProperty("clients") == null) {
            String hostname = com.sun.honeycomb.test.util.Util.localHostName();
            TestRunner.setProperty("clients",hostname);
        }
        
        if (TestRunner.getProperty("processes") == null)
            TestRunner.setProperty("processes","5");
        
        if (TestRunner.getProperty("operations") == null)
            TestRunner.setProperty("operations",
                "95%StoreOpGenerator,5%DeleteOpGenerator");
        
        if (TestRunner.getProperty("maxsize") == null)
            TestRunner.setProperty("maxsize","1M");
        
        TestRunner.setProperty("nodes", new Integer(cm.getNumNodes()).toString());
        
        if (TestRunner.getProperty("time") == null)
            TestRunner.setProperty("time","1m");
        
        // TestBed must be re-initiazlied in order to make sure that the new
        // settings above take effect.
        TestBed.getInstance().init();
        
        Suite suite;
        try {
            suite = Suite.newInstance("com.sun.honeycomb.hctest.cases.storepatterns.ContinuousStore");
        } catch (Throwable e) {
            throw new HoneycombTestException(e);
        }
        
        try {
            suite.setUp();
            suite.run();
            suite.tearDown();        
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }
    }
} 
