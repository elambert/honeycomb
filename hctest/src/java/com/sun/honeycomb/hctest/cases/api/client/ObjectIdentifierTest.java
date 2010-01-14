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
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HoneycombTestClient;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class ObjectIdentifierTest extends HoneycombLocalSuite {

    private String m_dataVIP;
    private TestCase m_currentTestCase = null;
  
    public void setUp() throws Throwable {
	super.setUp();
	this.m_dataVIP = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_VIP);
    }

    public void runTests() {
	try {
	    testConstructor();
	    testClone();
	    testCompareTo();
	    testEquals();
	    testGetBytes();
	    testHashCode();
	}
	catch (Throwable t) {
            Log.ERROR("unexpected exception thrown within ObjectIdentifier::runTests()");
            Log.ERROR(Log.stackTrace(t));
            if (m_currentTestCase != null) {
        	m_currentTestCase.postResult(false, "Unexpected throwable encountered! " + t);
            }
	} 
    }

    public void testConstructor() throws Throwable {
	// try null
	m_currentTestCase = new TestCase(this,"ObjectIdentifier::constructor","null byteArray");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    boolean result = false;
	    Throwable thrown = null;
	    byte [] nullByteArray = null;
	    String note = "";
	    try {
		ObjectIdentifier myoid = new ObjectIdentifier(nullByteArray);
	    }
	    catch (Throwable t) {
		thrown = t;
	    }
	    if (thrown == null ) {
		result = false;
		note = "Attempt to create an ObjectIdentifier with a null byte array did not result in a throwable";
	    }
	    else {
		if (thrown instanceof IllegalArgumentException ) {
		    result = true;
		}
		else {
		    result = false;
		    note = "Attempt to create an ObjectIdentifier with a null byte array resulted in an unanticipated throwable";
		    Log.ERROR(Log.stackTrace(thrown));
		}
	    }
	    m_currentTestCase.postResult(result,note);
	}

	m_currentTestCase = new TestCase(this,"ObjectIdentifier::constructor","null String");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    boolean result = false;
	    Throwable thrown = null;
	    String nullHexArray = null;
	    String note = "";
	    try {
		ObjectIdentifier myoid = new ObjectIdentifier(nullHexArray);
	    }
	    catch (Throwable t) {
		thrown = t;
	    }
	    if (thrown == null ) {
		result = false;
		note = "Attempt to create an ObjectIdentifier with a null hex string did not result in a throwable";
	    }
	    else {
		if (thrown instanceof IllegalArgumentException ) {
		    result = true;
		}
		else {
		    result = false;
		    note = "Attempt to create an ObjectIdentifier with a null hex string resulted in an unanticipated throwable";
		    Log.ERROR(Log.stackTrace(thrown));
		}
	    }
	    m_currentTestCase.postResult(result,note);
	}

	m_currentTestCase = new TestCase(this,"ObjectIdentifier::constructor","byte array");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    boolean result = false;
	    byte [] myArray = generateNewOidBytes();
	    ObjectIdentifier myOid = new ObjectIdentifier(myArray);
	    result = java.util.Arrays.equals(myArray, myOid.getBytes());
	    m_currentTestCase.postResult(result, result ? "" : "The byte array used to create the OID does not match the " + 
					 "array returned by getBytes()");
	} 

	
	m_currentTestCase = new TestCase(this,"ObjectIdentifier::constructor","hex string");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    boolean result = false;
	    String myString = generateNewHexOid();
	    ObjectIdentifier myOid = new ObjectIdentifier(myString);
	    String resultString = myOid.toString();
	    result = myString.equals(resultString);
	    m_currentTestCase.postResult(result, result ? "" : " The hexStringID returned by toString(): " + resultString +
					" does not match the hex string passed to the constructor: " + myString);
	}

    }

    public void testEquals() throws Throwable {

	m_currentTestCase = new TestCase(this,"NameValueRecord::equals()", "equalitySuite");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    byte [] myArray = generateNewOidBytes();
	    byte [] myOtherArray = generateNewOidBytes();
	    ObjectIdentifier oid1 = new ObjectIdentifier(myArray);
	    ObjectIdentifier oid2 = new ObjectIdentifier(myArray);
	    ObjectIdentifier oid3 = new ObjectIdentifier(myArray);
	    ObjectIdentifier oidA = new ObjectIdentifier(myOtherArray);
	    m_currentTestCase.postResult(HoneycombTestClient.equalityTest(oid1,oid2,oid3,oidA));
	}
    }

    // JavaDoc for clone does not really specify behavior of clone. 
    // assume that instance returned by clone should not be an the instance as that 
    // which was cloned and that clone should be equal to it's master
    public void testClone() throws Throwable {
	m_currentTestCase = new TestCase(this,"ObjectIdentifier::clone()", "clone");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    byte [] myArray = generateNewOidBytes();
	    ObjectIdentifier me = new ObjectIdentifier(myArray);
	    ObjectIdentifier myShadow = (ObjectIdentifier) me.clone();
	    boolean result = (myShadow != null && me != myShadow && me.equals(myShadow));
	    String note = "";
	    if (! result ) {
		if (myShadow == null) {
		    note = "ObjectIdentifier.clone() returned null";
		} else if (me == myShadow) {
		    note = "ObjectIdentifier.clone() returned a reference to the object it was cloning";
		} else if (!me.equals(myShadow)) {
		    note = "ObjectIdentifier.clone() returned an object that does not equal() the object it cloned";
		}
	    }
	    m_currentTestCase.postResult(result,note);
	}
    }

    public void testCompareTo() throws Throwable {

	m_currentTestCase = new TestCase(this,"ObjectIdentifier::compareTo()", "basicCompare");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    byte [] myArray = generateNewOidBytes();
	    byte [] otherArray = generateNewOidBytes();
	    ObjectIdentifier thisOne = new ObjectIdentifier(myArray);
	    ObjectIdentifier thatOne = new ObjectIdentifier(myArray);
	    ObjectIdentifier diffOne = new ObjectIdentifier(otherArray);
	    boolean result = HoneycombTestClient.basicCompareToTest(thisOne, thatOne, diffOne);
	    m_currentTestCase.postResult(result,"");
	}

    }

    public void testGetBytes() throws Throwable {
	m_currentTestCase = new TestCase(this,"ObjectIdentifier::getBytes()", "null");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    byte [] myArray = generateNewOidBytes();
	    ObjectIdentifier me = new ObjectIdentifier(myArray);
	    boolean result = java.util.Arrays.equals(myArray, me.getBytes());
	    m_currentTestCase.postResult(result, result ? "" : 
					"byteArray returned by getBytes() doesnt equal the byteArray used to create the OID");
	}
    }

    public void testHashCode() throws Throwable {
	//make sure that equal objects return equal hashcodes
	m_currentTestCase = new TestCase(this,"ObjectIdentifier::hashCode()", "equal");
	m_currentTestCase.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK});
	if (!m_currentTestCase.excludeCase()) {
	    byte [] myArray = generateNewOidBytes();
	    ObjectIdentifier thisOne = new ObjectIdentifier(myArray);
	    ObjectIdentifier thatOne = new ObjectIdentifier(myArray);
	    boolean result = (thisOne.hashCode() == thatOne.hashCode());
	    m_currentTestCase.postResult(result, result ? "" : 
					"equal instances incorrectly returned different hashcode values");
	}
    }

    /*
     * The only way I know how to generate an OID client side is by doing store.
     * NewObjectIdentifier class looks promising but it needs some native libraries
     * which do not currently appear on the clients. So this will have to do for the 
     * time being.
     */
    private byte [] generateNewOidBytes() throws HoneycombTestException {
	try {
	  NameValueObjectArchive nvoa = new NameValueObjectArchive(m_dataVIP); 
	  return nvoa.storeObject(new HCTestReadableByteChannel(new byte[] {'d','e', 'a','d','b','e','e','f'}, 10)).getObjectIdentifier().getBytes();
	} catch (Throwable t) {
	    throw new HoneycombTestException("Unable to execute store needed to generate oid." + t.getMessage());
	}
    }
   
    private String generateNewHexOid() throws HoneycombTestException {
	try {
	    NameValueObjectArchive nvoa = new NameValueObjectArchive(m_dataVIP); 
	    return nvoa.storeObject(new HCTestReadableByteChannel(new byte[] {'d','e', 'a','d','b','e','e','f'}, 10)).getObjectIdentifier().toString();
	} catch (Throwable t) {
	    throw new HoneycombTestException("Unable to execute store needed to generate oid." + t.getMessage());
	}	
    }
}
