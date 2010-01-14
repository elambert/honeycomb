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
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.client.TestNVOA;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.hctest.util.HoneycombTestClient;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.HoneycombTag;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.Util;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.io.IOException;

public class NameValueRecordTest extends Suite {

    public void setUp() throws Throwable {
        this.m_dataVIP = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_VIP);
    }
    public void runTests() {
	try {
	    testEquals();
	    testCompareTo();
	    testHashCode();
	    testPutAll();
	    testToString();
	    testGetAsString();
	}
	catch (Throwable t) {
            Log.ERROR("unexpected exception thrown within NameValueRecordTest::runTests()");
            Log.ERROR(Log.stackTrace(t));
	}
    }

    public void testEquals() throws Throwable {
	TestCase c = new TestCase(this,"NameValueRecord::equals()", "equalitySuite");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueRecord nvr1 = nvoa.createRecord();
	    NameValueRecord nvr2 = nvoa.createRecord();
	    NameValueRecord nvr3 = nvoa.createRecord();
	    NameValueRecord nvrA = nvoa.createRecord();
	    HoneycombTestClient.fillNVR(nvrA, nvoa, 1);
	    c.postResult(HoneycombTestClient.equalityTest(nvr1,nvr2,nvr3,nvrA));
	}
    }

    public void testCompareTo() throws Throwable {
	TestCase c = new TestCase(this,"NameValueRecord::compareTo()", "basicCompareTests");
	//don't run this due to bug 6293781
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK, Tag.NORUN});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueRecord thisOne = nvoa.createRecord();
	    NameValueRecord thatOne = nvoa.createRecord();
	    NameValueRecord differentOne = nvoa.createRecord();
	    HoneycombTestClient.fillNVR(differentOne, nvoa, 0);
	    boolean result = HoneycombTestClient.basicCompareToTest(thisOne, thatOne, differentOne);
	    c.postResult(result, "");
	}
    }

    public void testHashCode() throws Throwable {
	TestCase c = new TestCase(this,"NameValueRecord::HashCode()", "basicHashCodeTest");
	//don't run this due to bug 6292560
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK, Tag.NORUN});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueRecord thisOne = nvoa.createRecord();
	    NameValueRecord thatOne = nvoa.createRecord();
	    c.postResult(HoneycombTestClient.basicHashCodeTest(thisOne,thatOne));
	}
    }

    public void testPutAll() throws Throwable {
	// test null condition 
	TestCase c = new TestCase(this,"NameValueRecord::putAll()", "null map");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueRecord thisOne = nvoa.createRecord();
	    Throwable thrown = null;
	    boolean result = false;
	    String note = "";
	    Map myMap = null;
	    try {
		thisOne.putAll(myMap);
	    } catch (Throwable t) {
		thrown = t;
	    }
	    if (thrown == null) {
		note = "anticipated throwable was not thrown";
	    }
	    else {
		if (thrown instanceof NullPointerException ) {
		    result = true;
		} else {
		    note = "unanticipated throwable was caught";
		    Log.stackTrace(thrown);
		}
	    }
	    c.postResult(result, note);
	}

	// test emptyMap
	c = new TestCase(this,"NameValueRecord::putAll()", "empty map");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueRecord nvr = nvoa.createRecord();
	    boolean result = putall(nvr,new HashMap());
	    c.postResult(result, result ? "" : "failed to put an empty map");
	}

	// test non-emptyMap
	// Bug 6291821 fixed to make this work
	c = new TestCase(this,"NameValueRecord::putAll()", "non-empty map with non-string values");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueRecord nvr = nvoa.createRecord();
	    NameValueRecord nvrWork = nvoa.createRecord();
	    HoneycombTestClient.fillNVR(nvrWork, nvoa, 0);
	    boolean result = putall(nvr,HoneycombTestClient.nvr2Hash(nvrWork));
	    c.postResult(result, result ? "" : "failed to put an non-empty map");
	}

	//
	c = new TestCase(this,"NameValueRecord::putAll()", "non-empty map with string values");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueRecord nvr = nvoa.createRecord();
	    NameValueRecord nvr1= nvoa.createRecord();
	    HoneycombTestClient.fillNVR(nvr, nvoa, 0);
	    HashMap hm = HoneycombTestClient.nvr2Hash(nvr);
	    Iterator iter = hm.keySet().iterator();
	    while (iter.hasNext()) {
            String key = (String) iter.next();
            Object value = (Object) hm.get(key);
            if (value instanceof byte[]) {
                hm.put(key, ByteArrays.toHexString((byte[])value));
            } else if (value instanceof Timestamp) {
                SimpleDateFormat formatter = new SimpleDateFormat(
                        CanonicalEncoding.CANONICAL_TIMESTAMP_FORMAT);
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                hm.put(key, formatter.format(value));
            } else {
                hm.put(key, value.toString());
            }
	    }
	    boolean result = putall(nvr1,hm);
	    c.postResult(result, result ? "" : "failed to put an non-empty map with string values");
	}

    } 

    public void testToString () throws Throwable {
	// Don't know what specified formate is for toString(), so just gonna
	// Make sure that we don't return a null or worse.
	TestCase c = new TestCase(this,"NameValueRecord::toString()", "null");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
	    NameValueRecord thisOne = nvoa.createRecord();
	    String note = "";
	    boolean result = false;
	    try {
		String myString = thisOne.toString();
		if (myString == null) {
		    result = false;
		    note= "toString() returned a null object.";
		} else {
		    result = true;
		}
	    }
	    catch (Throwable t) {
		result = false;
		Log.stackTrace(t);
		note= "toString() resulted in an unanticipated exception being thrown.";
	    }
	    c.postResult(result,note);
	}

    }

    public void testGetAsString() throws Throwable {
        TestCase c = new TestCase(this,"NameValueRecord::getAsString()", "null");
        c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                                HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
        if (!c.excludeCase()) {
            boolean result = true;
            String note = "";
            NameValueObjectArchive nvoa = new TestNVOA(m_dataVIP);
            NameValueRecord nvr = nvoa.createRecord();
            NameValueSchema.ValueType [] types = NameValueSchema.TYPES;
            NameValueSchema mySchema = nvoa.getSchema();
            NameValueSchema.Attribute [] myAttrs = mySchema.getAttributes();
            ArrayList testList = new ArrayList();
            for (int i = 0; i < types.length; i++) {
                NameValueSchema.ValueType currentType = types[i];
                if (currentType == NameValueSchema.OBJECTID_TYPE)
                    continue;
                ArrayList testListEntry = null;
                for (int j = 0; j < myAttrs.length; j++) {
                    NameValueSchema.Attribute currentAttr = myAttrs[j];
                    if (currentAttr.getType().equals(currentType)) {
                        String attrName = currentAttr.getName();
                        putAttrValue(nvr,attrName, currentType); 
                        testListEntry = new ArrayList();
                        testListEntry.add(attrName);
                        testListEntry.add(currentType);
                        testList.add(testListEntry);
                        break;
                    }
                }
                if (testListEntry == null) {
                    Log.WARN("Unable to find an attribute of type " + attrTypeToString(currentType));
                }
            }
            Iterator iter = testList.iterator();
            while (iter.hasNext()) {
                String valueAsString;
                ArrayList currentTestListEntry = (ArrayList) iter.next();
                String attrName = (String) currentTestListEntry.get(0);
                NameValueSchema.ValueType attrType = (NameValueSchema.ValueType) currentTestListEntry.get(1);
                try {
                    valueAsString = nvr.getAsString(attrName);
                } catch (Throwable t) {
                    if (t instanceof IllegalArgumentException) {
                        // 			if (!attrType.equals(NameValueSchema.BINARY_TYPE)) {
                        Log.ERROR("an unanticipated exception was thrown");
                        Log.ERROR(Log.stackTrace(t));
                        result = false;
                        // 			}
                    } else {
                        result = false;
                    }
                    continue;
                }
                String expectedValueAsString = (attrType == NameValueSchema.BINARY_TYPE ?
                                                ByteArrays.toHexString((byte[])getAttrValue(attrType)) :
                                                getAttrValue(attrType).toString());
                if (!valueAsString.equals(expectedValueAsString)) {
                    result = false;
                    Log.ERROR("getAsString() failed for type " + attrTypeToString(attrType));
                    Log.ERROR("Expected getAsString() to return: " + expectedValueAsString);
                    Log.ERROR("getAsString() actually  returned: " + valueAsString);
                }
            }
            //check for an unkown key (assumes that no one would use an empty string as a key
            Throwable thrown = null;
            try {
                nvr.getAsString("");
            }
            catch (Throwable t) {
                thrown = t;
            }
            if (thrown == null) {
                result = false;
                Log.ERROR("getAsString() with an unkown key did not result in an IllegalArgumentException");
            } else {
                if (! (thrown instanceof IllegalArgumentException)) {
                    result = false;
                    Log.ERROR("getAsString() with an unkown key resulted in an unanticipated throwable being caught.");
                    Log.ERROR(Log.stackTrace(thrown));
                }
            }

            //check for an unkown key (assumes that no one would use an empty string as a key
            thrown = null;
            try {
                nvr.getAsString(null);
            }
            catch (Throwable t) {
                thrown = t;
            }
            if (thrown == null) {
                result = false;
                Log.ERROR("getAsString() with an unkown key did not result in an IllegalArgumentException");
            } else {
                if (! (thrown instanceof IllegalArgumentException)) {
                    result = false;
                    Log.ERROR("getAsString() with an unkown key resulted in an unanticipated throwable being caught.");
                    Log.ERROR(Log.stackTrace(thrown));
                }
            }

            c.postResult(result);
        }
    }

    private void putAttrValue (NameValueRecord nvr, String name, NameValueSchema.ValueType valueType) {
        nvr.put(name,getAttrValue(valueType));
    }

    private Object getAttrValue (NameValueSchema.ValueType valueType) {
        if (valueType.equals(NameValueSchema.DOUBLE_TYPE)) {
            return new Double(Double.MAX_VALUE);
        } 
        else if (valueType.equals(NameValueSchema.LONG_TYPE)) {
            return new Long(Long.MAX_VALUE);
        } 
        else if (valueType.equals(NameValueSchema.STRING_TYPE)) {
            return "Hownowbrowncow-String";
        } 
        else if (valueType.equals(NameValueSchema.CHAR_TYPE)) {
            return "Hownowbrowncow-Char";
        } 
        else if (valueType.equals(NameValueSchema.BINARY_TYPE)) {
            byte[] bytes = new byte[10];
            Arrays.fill(bytes,(byte)0x5a);
            return bytes;
        } 
        else if (valueType.equals(NameValueSchema.DATE_TYPE)) {
            return new Date(0L);
        } 
        else if (valueType.equals(NameValueSchema.TIME_TYPE)) {
            return new Time(0L);
        } 
        else if (valueType.equals(NameValueSchema.TIMESTAMP_TYPE)) {
            return new Timestamp(0L);
        } 
        else {
            Log.WARN("Unexpected NameValueSchemaType encountered: " + valueType);
            return null;
        }
    }

    private String attrTypeToString (NameValueSchema.ValueType valueType) {
        return valueType.toString();
    }

    private boolean putall (NameValueRecord nvr, Map myMap) {
        boolean result = false;
        try {
            nvr.putAll(myMap);
            String [] keys = nvr.getKeys();
            if (keys.length == myMap.size()) {
                result = true;
            }
            else {
                Log.ERROR("number of entries retrieved does not equal number put");
                result = false;        
            }
        }
        catch (Throwable t) {
                Log.ERROR("an unanticipated throwable was caught");
                Log.ERROR(Log.stackTrace(t));
                result = false;
        }
        return result;
    }

    String m_dataVIP;

}

