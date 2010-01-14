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



package com.sun.honeycomb.hctest.cases.api.client;

import com.sun.honeycomb.client.ObjectArchive;
import com.sun.honeycomb.client.MetadataRecord;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.common.ArchiveException;

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.HoneycombSuite;
import com.sun.honeycomb.hctest.rmi.auditsrv.clnt.AuditSrvClnt;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HoneycombTestClient;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.MultiThreadTestDriver;
import com.sun.honeycomb.hctest.util.LoadTesterThread;
import com.sun.honeycomb.hctest.util.OAStoreFileTask;
import com.sun.honeycomb.hctest.util.SimpleTask;

import com.sun.honeycomb.test.Metric;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.matrix.Domain;
import com.sun.honeycomb.test.matrix.Matrix;
import com.sun.honeycomb.test.matrix.SimpleDomain;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.Util;
import java.io.File;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

public class ObjectArchiveTest extends HoneycombSuite {
    private String dataVIP;
    
    public void setUp() throws Throwable {
        this.dataVIP = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_VIP);
    }
    
    public void runTests() {
        try {
            testMultiThreadLoad();
        } catch (Throwable t) {
            Log.ERROR("unexpected exception thrown within NameValueObjectArchiveTest::runTests()");
            Log.ERROR(Log.stackTrace(t));
        }
    }

    /**
     * This test is designed to stress the ObjectArchive.storeObject() method. It creates an 
     * instance of ObjectArchive that is shared across several threads. Each threads will continually 
     * ask the ObjectArchive to store objects of various sizes for a predetermined amount of time. The test
     * will then ensure that a System Record and has been returned from the store operation and that the 
     * data size and data hash with in the System Record meet 
     */
    public void testMultiThreadLoad() 
        throws Throwable
    {
        TestCase c = new TestCase(this,"ObjectArchive.storeObject():loadtest","Timed");
        c.addTag(new String[] {Tag.DAYLONG, Tag.EXPERIMENTAL});
	boolean result = false;
	if (!c.excludeCase()) {
	    Random myRandom = new Random(seed);
	    long time = Util.parseTime("8h");
	    int testMode = LoadTesterThread.TIMER_MODE;
	    int numThreads = 10;
	    String fileSizeCase = "SMALL"; //can be XXSMALL, XSMALL, SMALL, MEDIUM, LARGE, XLARGE. XXLARGE, XXXLARGE, MIXED

	    SimpleTask myTask = new OAStoreFileTask(new ObjectArchive(dataVIP),myRandom.nextLong(),fileSizeCase,c);
	    MultiThreadTestDriver mtd = new MultiThreadTestDriver(myTask,testMode,time,numThreads, c.toString());
	    CmdResult res = mtd.run();
	    result = res.pass;
	    res.logExceptions(c.toString());
	    Log.INFO ("Files Written: " + res.count + " Bytes: " + res.filesize);
	    c.postResult(result);
	}
    }

}
