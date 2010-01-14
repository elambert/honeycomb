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

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.TestNVOA;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HoneycombTestClient;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.HoneycombTag;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.Util;

import java.io.IOException;

public class SystemRecordTest extends Suite {

    public void setUp() throws Throwable {
        this.m_dataVIP = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_VIP);
    }

    public void runTests() {
	try {
	    testEquals();
	    testCompareTo();
	    testHashCode();
	}
	catch (Throwable t) {
            Log.ERROR("unexpected exception thrown within SystemRecordTest::runTests()");
            Log.ERROR(Log.stackTrace(t));
	}
    }

    public void testEquals() throws Throwable {
	TestCase c = new TestCase(this,"SystemRecord::equals()", "equalitySuite");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
            HCTestReadableByteChannel data  = new HCTestReadableByteChannel(1024);
            HCTestReadableByteChannel data1 = new HCTestReadableByteChannel(1024);
	    SystemRecord sysRec1 = HoneycombTestClient.storeObject(nvoa,data,c);
	    ObjectIdentifier myOID = sysRec1.getObjectIdentifier();
	    NameValueRecord myNVR = nvoa.retrieveMetadata(myOID);
	    SystemRecord sysRec2 = myNVR.getSystemRecord();
	    myNVR = nvoa.retrieveMetadata(myOID);
	    SystemRecord sysRec3 = myNVR.getSystemRecord();
	    SystemRecord sysRecA = HoneycombTestClient.storeObject(nvoa,data1,c);
	    c.postResult(HoneycombTestClient.equalityTest(sysRec1,sysRec2,sysRec3,sysRecA));
	}
    }

    public void testHashCode() throws Throwable {
	TestCase c = new TestCase(this,"SystemRecord::hashCode()", "basicHashCode");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
            HCTestReadableByteChannel data  = new HCTestReadableByteChannel(1024);
            HCTestReadableByteChannel data1 = new HCTestReadableByteChannel(1024);
	    SystemRecord sysRec1 = HoneycombTestClient.storeObject(nvoa,data,c);
            System.out.println("sys 1");
            System.out.println(sysRec1);
	    ObjectIdentifier myOID = sysRec1.getObjectIdentifier();
	    NameValueRecord myNVR = nvoa.retrieveMetadata(myOID);
	    SystemRecord sysRec2 = myNVR.getSystemRecord();
            System.out.println("sys 2");
            System.out.println(sysRec1);
	    c.postResult(HoneycombTestClient.basicHashCodeTest(sysRec1,sysRec2));
	}
    }
    public void testCompareTo() throws Throwable {
	TestCase c = new TestCase(this,"SystemRecord::hashCode()", "basicHashCode");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
            HCTestReadableByteChannel data  = new HCTestReadableByteChannel(1024);
            HCTestReadableByteChannel data1 = new HCTestReadableByteChannel(1024);
            HCTestReadableByteChannel data2 = new HCTestReadableByteChannel(1024);
	    SystemRecord sysRec1 = HoneycombTestClient.storeObject(nvoa,data,c);
	    ObjectIdentifier myOID = sysRec1.getObjectIdentifier();
	    NameValueRecord myNVR = nvoa.retrieveMetadata(myOID);
	    SystemRecord sysRec2 = myNVR.getSystemRecord();
	    SystemRecord sysRecA = HoneycombTestClient.storeObject(nvoa,data2,c);
	    c.postResult(HoneycombTestClient.basicCompareToTest(sysRec1,sysRec2,sysRecA));
	}
    }


    private String m_dataVIP;
}

