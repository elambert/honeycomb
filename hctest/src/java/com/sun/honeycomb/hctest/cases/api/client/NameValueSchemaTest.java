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
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.client.MyNameValueSchema;
import com.sun.honeycomb.client.TestNVOA;
import com.sun.honeycomb.client.QAClient;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.HoneycombTestClient;
import com.sun.honeycomb.hctest.HoneycombTag;
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

import java.io.IOException;
import java.util.ArrayList;

public class NameValueSchemaTest extends Suite{
    
    public void setUp() throws Throwable {
        this.m_dataVIP = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_VIP);
    }

    public void runTests() {
	try {
	    testHashCode();
	    testEquals();
	    testCompare();
	    testAttributeEquals();
	    testAttributeCompare();
	}
	catch (Throwable t) {
            Log.ERROR("unexpected exception thrown within NameValueSchemaTest::runTests()");
            Log.ERROR(Log.stackTrace(t));
	}
    }

    /** Test hashcodes.
     * hashcode for the equal objects must be the same.
     */
    public void testHashCode() throws ArchiveException, IOException {
	TestCase c = new TestCase(this,"NameValueSchema::hashcode()", "basicHashCode");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueSchema thisOne = nvoa.getSchema();
	    NameValueSchema thatOne = nvoa.getSchema();
	    boolean result = (HoneycombTestClient.basicHashCodeTest(thisOne, thatOne));
	    c.postResult(result, "");
	}
    }

    public void testEquals() throws ArchiveException, IOException {
	TestCase c = new TestCase(this,"NameValueRecord::equals()", "equalitySuite");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueSchema nvs1 = nvoa.getSchema();
	    NameValueSchema nvs2 = nvoa.getSchema();
	    NameValueSchema nvs3 = nvoa.getSchema();
	    NameValueSchema.Attribute[] attributes = {QAClient.makeAttribute("attr1",1,1)};
	    MyNameValueSchema nvsA = new MyNameValueSchema(attributes);
	    c.postResult(HoneycombTestClient.equalityTest(nvs1,nvs2,nvs3,nvsA));
	}
    }

    public void testCompare() throws ArchiveException, IOException{

	TestCase c = new TestCase(this,"NameValueSchema::compareTo()", "basicCompare");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueSchema.Attribute[] list1 = {QAClient.makeAttribute("attr1",1,1)};
	    NameValueSchema.Attribute[] list2 = {QAClient.makeAttribute("attr2",2,2)};
	    MyNameValueSchema schema1 = new MyNameValueSchema(list1);
	    MyNameValueSchema schema2 = new MyNameValueSchema(list1);
	    MyNameValueSchema schemaA = new MyNameValueSchema(list2);
	    boolean result = HoneycombTestClient.basicCompareToTest(schema1, schema2, schemaA);
	    c.postResult(result,"");
	}
    }

    public void testAttributeEquals() throws ArchiveException, IOException {
	TestCase c = new TestCase(this,"NameValueRecord.Attribute::equals()", "equalitySuite");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueSchema.Attribute nvsa1 = QAClient.makeAttribute("name",1,1);
	    NameValueSchema.Attribute nvsa2 = QAClient.makeAttribute("name",1,1);
	    NameValueSchema.Attribute nvsa3 = QAClient.makeAttribute("name",1,1);
	    NameValueSchema.Attribute nvsaA = QAClient.makeAttribute("anotherName",1,1);
	    c.postResult(HoneycombTestClient.equalityTest(nvsa1,nvsa2,nvsa3,nvsaA));
	}
    }

    public void testAttributeCompare() throws ArchiveException, IOException{

	TestCase c = new TestCase(this,"NameValueSchema.Attribute::compareTo()", "basicCompare");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueSchema.Attribute nvsa1 = QAClient.makeAttribute("name",1,1);
	    NameValueSchema.Attribute nvsa2 = QAClient.makeAttribute("name",1,1);
	    NameValueSchema.Attribute nvsaA = QAClient.makeAttribute("anotherName",1,1);
	    c.postResult(HoneycombTestClient.basicCompareToTest(nvsa1,nvsa2,nvsaA));
	}
    }    

    String m_dataVIP;

}
